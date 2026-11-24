package com.xperience.player.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.service.MediaNetworkClients
import com.xperience.player.ui.component.ExplicitBadge
import com.xperience.player.ui.component.LocalGlassEffectConfig
import com.xperience.player.ui.component.liquidGlass
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.theme.AppThemeColors
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.PlusJakartaSans
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

private val QueueRowShape = ContinuousRoundedRectangle(size = 16.dp)
private val QueueArtworkShape = ContinuousRoundedRectangle(percent = 24)
private val QueueHeaderPillShape = ContinuousRoundedRectangle(percent = 50)

private data class QueueSlot(
    val uid: Long,
    val track: TrackItem
)

/**
 * Inline Playing Queue view embedded inside [NowPlayingScreen]'s center area.
 *
 * Features:
 * - Left-side album cover artwork via [MediaNetworkClients.getImageLoader] with rounded corners & fallback icon
 * - Right-side dedicated drag handle with edge auto-scroll so normal vertical list scrolling stays responsive
 * - Micro-interactive currently-playing row (dancing equalizer bars synced to [isPlaying], glowing LiquidGlass highlight, spring pulse)
 * - Jelly / elastic ("efecto gelatinoso") lift, velocity tilt, bouncy slot reordering, and squash-and-stretch landing impulses
 */
@Composable
fun InlineQueueView(
    queue: List<TrackItem>,
    currentQueueIndex: Int,
    currentTrack: TrackItem?,
    isPlaying: Boolean,
    onPlayQueueIndex: (Int) -> Unit,
    onMoveQueueItem: (fromIndex: Int, toIndex: Int) -> Unit,
    accentColor: Color = LocalAccentColor.current,
    appColors: AppThemeColors = LocalAppColors.current,
    modifier: Modifier = Modifier
) {
    var nextUid by remember { mutableLongStateOf(1L) }
    val displaySlots = remember { mutableStateListOf<QueueSlot>() }

    LaunchedEffect(queue) {
        if (queue.isEmpty()) {
            displaySlots.clear()
            return@LaunchedEffect
        }
        val matchesCurrentOrder = displaySlots.size == queue.size &&
            displaySlots.indices.all { idx -> displaySlots[idx].track == queue[idx] }
        if (!matchesCurrentOrder) {
            val pool = displaySlots.toMutableList()
            val reconciled = queue.map { track ->
                val matchIdx = pool.indexOfFirst { it.track == track }
                if (matchIdx >= 0) {
                    pool.removeAt(matchIdx)
                } else {
                    QueueSlot(uid = nextUid++, track = track)
                }
            }
            displaySlots.clear()
            displaySlots.addAll(reconciled)
        }
    }

    if (queue.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = null,
                    tint = appColors.glassTextPrimary.copy(alpha = 0.5f),
                    modifier = Modifier.size(52.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Playing queue is empty",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = appColors.textPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Pick a song or album to start building your queue",
                    fontFamily = PlusJakartaSans,
                    fontSize = 13.sp,
                    color = appColors.textSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    val context = LocalContext.current
    val appContext = context.applicationContext
    val sharedImageLoader = remember(appContext) {
        MediaNetworkClients.getImageLoader(appContext)
    }
    val glassConfig = LocalGlassEffectConfig.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    val currentOnPlayQueueIndex by rememberUpdatedState(onPlayQueueIndex)
    val currentOnMoveQueueItem by rememberUpdatedState(onMoveQueueItem)

    // Immediate seed on first composition before LaunchedEffect runs so first frame renders with keys.
    if (displaySlots.isEmpty() && queue.isNotEmpty()) {
        queue.forEach { track ->
            displaySlots.add(QueueSlot(uid = nextUid++, track = track))
        }
    }

    val activeIndex = when {
        currentQueueIndex in queue.indices -> currentQueueIndex
        currentTrack != null -> queue.indexOfFirst { it.id == currentTrack.id }
        else -> -1
    }

    val listState = rememberLazyListState()
    var draggedUid by remember { mutableStateOf<Long?>(null) }
    var draggedOffsetY by remember { mutableFloatStateOf(0f) }
    var dragVelocityY by remember { mutableFloatStateOf(0f) }

    fun performSwap(fromIdx: Int, toIdx: Int, offsetDelta: Int) {
        if (fromIdx !in displaySlots.indices || toIdx !in displaySlots.indices || fromIdx == toIdx) return
        val firstVisibleIndex = listState.firstVisibleItemIndex
        val firstVisibleOffset = listState.firstVisibleItemScrollOffset

        val moved = displaySlots.removeAt(fromIdx)
        displaySlots.add(toIdx, moved)

        // Prevent LazyColumn from scrolling the viewport when swapping at the top visible anchor item.
        if (fromIdx == firstVisibleIndex || toIdx == firstVisibleIndex) {
            listState.requestScrollToItem(firstVisibleIndex, firstVisibleOffset)
        }

        draggedOffsetY += offsetDelta.toFloat()
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        currentOnMoveQueueItem(fromIdx, toIdx)
    }

    fun trySwapDraggedItem() {
        val uid = draggedUid ?: return
        val currentIndex = displaySlots.indexOfFirst { it.uid == uid }
        if (currentIndex == -1) return

        val visibleItems = listState.layoutInfo.visibleItemsInfo
        val currentItemInfo = visibleItems.firstOrNull { it.key == uid } ?: return
        val currentTop = currentItemInfo.offset + draggedOffsetY
        val currentBottom = currentTop + currentItemInfo.size

        if (draggedOffsetY < 0f && currentIndex > 0) {
            val prevUid = displaySlots[currentIndex - 1].uid
            val prevInfo = visibleItems.firstOrNull { it.key == prevUid }
            if (prevInfo != null) {
                val prevMidpoint = prevInfo.offset + prevInfo.size * 0.52f
                if (currentTop < prevMidpoint) {
                    performSwap(currentIndex, currentIndex - 1, currentItemInfo.offset - prevInfo.offset)
                    return
                }
            }
        }

        if (draggedOffsetY > 0f && currentIndex < displaySlots.lastIndex) {
            val nextUid = displaySlots[currentIndex + 1].uid
            val nextInfo = visibleItems.firstOrNull { it.key == nextUid }
            if (nextInfo != null) {
                val nextMidpoint = nextInfo.offset + nextInfo.size * 0.48f
                if (currentBottom > nextMidpoint) {
                    performSwap(currentIndex, currentIndex + 1, currentItemInfo.offset - nextInfo.offset)
                    return
                }
            }
        }
    }

    // Smooth edge auto-scroll while dragging a queue row near top/bottom viewport bounds.
    val edgeThresholdPx = with(density) { 58.dp.toPx() }
    val maxScrollStepPx = with(density) { 14.dp.toPx() }

    LaunchedEffect(draggedUid) {
        val uid = draggedUid ?: return@LaunchedEffect
        while (isActive && draggedUid == uid) {
            withFrameNanos { }
            val layoutInfo = listState.layoutInfo
            val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.key == uid }
            if (itemInfo != null) {
                val visualTop = itemInfo.offset + draggedOffsetY
                val visualBottom = visualTop + itemInfo.size
                val viewportStart = layoutInfo.viewportStartOffset.toFloat()
                val viewportEnd = layoutInfo.viewportEndOffset.toFloat()

                val targetScroll = when {
                    visualTop < viewportStart + edgeThresholdPx && listState.canScrollBackward -> {
                        val ratio = ((viewportStart + edgeThresholdPx - visualTop) / edgeThresholdPx).coerceIn(0f, 1f)
                        -maxScrollStepPx * (0.25f + 0.75f * ratio)
                    }
                    visualBottom > viewportEnd - edgeThresholdPx && listState.canScrollForward -> {
                        val ratio = ((visualBottom - (viewportEnd - edgeThresholdPx)) / edgeThresholdPx).coerceIn(0f, 1f)
                        maxScrollStepPx * (0.25f + 0.75f * ratio)
                    }
                    else -> 0f
                }

                if (targetScroll != 0f) {
                    val consumed = listState.scrollBy(targetScroll)
                    if (consumed != 0f) {
                        draggedOffsetY += consumed
                        trySwapDraggedItem()
                    }
                }
            }
        }
    }

    // Scroll gently to the currently playing track when first opening the inline queue.
    LaunchedEffect(Unit) {
        if (activeIndex > 1 && activeIndex < displaySlots.size) {
            listState.scrollToItem(maxOf(0, activeIndex - 1))
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Compact LiquidGlass Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(QueueHeaderPillShape)
                    .liquidGlass(
                        config = glassConfig,
                        shape = QueueHeaderPillShape,
                        highlightAlpha = 0.34f
                    )
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = "Playing Queue",
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = appColors.glassTextPrimary
                    )
                }
            }

            val countText = if (activeIndex >= 0) {
                "${activeIndex + 1} / ${displaySlots.size} songs"
            } else {
                "${displaySlots.size} songs"
            }
            Box(
                modifier = Modifier
                    .clip(QueueHeaderPillShape)
                    .background(if (appColors.isDark) Color(0x28FFFFFF) else Color(0x40FFFFFF))
                    .border(
                        width = 0.8.dp,
                        color = Color.White.copy(alpha = if (appColors.isDark) 0.18f else 0.48f),
                        shape = QueueHeaderPillShape
                    )
                    .padding(horizontal = 11.dp, vertical = 6.dp)
            ) {
                Text(
                    text = countText,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp,
                    color = appColors.textSecondary
                )
            }
        }

        LazyColumn(
            state = listState,
            userScrollEnabled = draggedUid == null,
            contentPadding = PaddingValues(top = 2.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            itemsIndexed(
                items = displaySlots,
                key = { _, slot -> slot.uid }
            ) { index, slot ->
                val isCurrent = index == activeIndex
                val isDragged = draggedUid == slot.uid

                val placementModifier = if (isDragged) {
                    Modifier
                } else {
                    Modifier.animateItem(
                        fadeInSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        fadeOutSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        placementSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }

                QueueTrackRow(
                    slot = slot,
                    index = index,
                    isCurrent = isCurrent,
                    isPlaying = isPlaying,
                    isDragged = isDragged,
                    dragOffsetY = if (isDragged) draggedOffsetY else 0f,
                    dragVelocityY = if (isDragged) dragVelocityY else 0f,
                    accentColor = accentColor,
                    appColors = appColors,
                    sharedImageLoader = sharedImageLoader,
                    onClick = {
                        if (draggedUid == null) {
                            currentOnPlayQueueIndex(index)
                        }
                    },
                    dragHandleModifier = Modifier.pointerInput(slot.uid) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            draggedUid = slot.uid
                            draggedOffsetY = 0f
                            dragVelocityY = 0f

                            val slop = awaitVerticalTouchSlopOrCancellation(down.id) { change, over ->
                                change.consume()
                                draggedOffsetY += over
                                dragVelocityY = over
                                trySwapDraggedItem()
                            }

                            if (slop != null) {
                                verticalDrag(slop.id) { change ->
                                    val dy = change.positionChange().y
                                    change.consume()
                                    draggedOffsetY += dy
                                    dragVelocityY = dragVelocityY * 0.65f + dy * 0.35f
                                    trySwapDraggedItem()
                                }
                            }

                            draggedUid = null
                            draggedOffsetY = 0f
                            dragVelocityY = 0f
                        }
                    },
                    modifier = placementModifier
                        .zIndex(if (isDragged) 10f else if (isCurrent) 1f else 0f)
                )
            }
        }
    }
}

@Composable
private fun QueueTrackRow(
    slot: QueueSlot,
    index: Int,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isDragged: Boolean,
    dragOffsetY: Float,
    dragVelocityY: Float,
    accentColor: Color,
    appColors: AppThemeColors,
    sharedImageLoader: coil.ImageLoader,
    onClick: () -> Unit,
    dragHandleModifier: Modifier,
    modifier: Modifier = Modifier
) {
    val track = slot.track
    val isDark = appColors.isDark
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Jelly squash-and-stretch + wobble animatables for grab, swap displacement, and landing.
    val jellyScaleX = remember(slot.uid) { Animatable(1f) }
    val jellyScaleY = remember(slot.uid) { Animatable(1f) }
    val jellyRotationZ = remember(slot.uid) { Animatable(0f) }
    val settlingOffsetY = remember(slot.uid) { Animatable(0f) }

    var prevIndex by remember(slot.uid) { mutableIntStateOf(index) }
    var wasDragged by remember(slot.uid) { mutableStateOf(false) }
    var lastDragOffset by remember(slot.uid) { mutableFloatStateOf(0f) }

    if (isDragged) {
        lastDragOffset = dragOffsetY
    }

    // Grab & landing jelly impulse + smooth spring settle back to slot alignment.
    LaunchedEffect(isDragged) {
        if (isDragged) {
            wasDragged = true
            settlingOffsetY.snapTo(0f)
            launch {
                jellyScaleX.snapTo(1.06f)
                jellyScaleX.animateTo(1f, spring(dampingRatio = 0.36f, stiffness = 420f))
            }
            launch {
                jellyScaleY.snapTo(0.94f)
                jellyScaleY.animateTo(1f, spring(dampingRatio = 0.36f, stiffness = 420f))
            }
        } else if (wasDragged) {
            val settleStart = lastDragOffset
            lastDragOffset = 0f
            if (abs(settleStart) > 0.5f) {
                settlingOffsetY.snapTo(settleStart)
                wasDragged = false
                launch {
                    settlingOffsetY.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }
            } else {
                wasDragged = false
            }
            launch {
                jellyScaleX.snapTo(1.07f)
                jellyScaleX.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = 340f))
            }
            launch {
                jellyScaleY.snapTo(0.92f)
                jellyScaleY.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = 340f))
            }
            launch {
                jellyRotationZ.snapTo(if (settleStart >= 0f) 1.5f else -1.5f)
                jellyRotationZ.animateTo(0f, spring(dampingRatio = 0.34f, stiffness = 360f))
            }
        }
    }

    // Transient jelly displacement impulse whenever surrounding items reorder across slots.
    LaunchedEffect(index) {
        if (index != prevIndex) {
            val direction = if (index > prevIndex) 1f else -1f
            prevIndex = index
            if (!isDragged) {
                launch {
                    jellyScaleX.snapTo(1.055f)
                    jellyScaleX.animateTo(1f, spring(dampingRatio = 0.33f, stiffness = 350f))
                }
                launch {
                    jellyScaleY.snapTo(0.935f)
                    jellyScaleY.animateTo(1f, spring(dampingRatio = 0.33f, stiffness = 350f))
                }
                launch {
                    jellyRotationZ.snapTo(direction * 1.6f)
                    jellyRotationZ.animateTo(0f, spring(dampingRatio = 0.34f, stiffness = 360f))
                }
            }
        }
    }

    // Micro-bounce highlight pulse when row transitions into becoming the active track.
    val activePopScale = remember(slot.uid) { Animatable(1f) }
    LaunchedEffect(isCurrent) {
        if (isCurrent) {
            activePopScale.snapTo(0.96f)
            activePopScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && !isDragged) 0.972f else 1f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium),
        label = "queuePressScale"
    )
    val liftScale by animateFloatAsState(
        targetValue = if (isDragged) 1.045f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "queueLiftScale"
    )
    val dragTilt by animateFloatAsState(
        targetValue = if (isDragged) (dragVelocityY * 0.09f).coerceIn(-2.4f, 2.4f) else 0f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow),
        label = "queueDragTilt"
    )
    val velocityStretch by animateFloatAsState(
        targetValue = if (isDragged) (abs(dragVelocityY) * 0.0018f).coerceIn(0f, 0.045f) else 0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
        label = "queueVelocityStretch"
    )
    val rowElevation by animateDpAsState(
        targetValue = when {
            isDragged -> 18.dp
            isCurrent -> 8.dp
            else -> 0.dp
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "queueElevation"
    )

    // Subtle breathing glow for currently playing row synced to playback state.
    val glowPulse by if (isCurrent && isPlaying) {
        val transition = rememberInfiniteTransition(label = "queueActiveGlow")
        transition.animateFloat(
            initialValue = 0.55f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1050, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "queueGlowPulse"
        )
    } else {
        remember(isCurrent) { mutableFloatStateOf(if (isCurrent) 0.65f else 0f) }
    }

    val rowBackground by animateColorAsState(
        targetValue = when {
            isDragged -> if (isDark) Color(0xCC222836) else Color(0xEBFFFFFF)
            isCurrent -> accentColor.copy(alpha = if (isDark) 0.22f else 0.16f)
            isDark -> Color(0x20FFFFFF)
            else -> Color(0x70FFFFFF)
        },
        animationSpec = tween(durationMillis = 220),
        label = "queueRowBg"
    )

    val borderBrush = when {
        isDragged -> Brush.linearGradient(
            listOf(accentColor.copy(alpha = 0.90f), Color.White.copy(alpha = 0.55f))
        )
        isCurrent -> Brush.linearGradient(
            listOf(
                accentColor.copy(alpha = 0.72f * glowPulse),
                Color.White.copy(alpha = (if (isDark) 0.35f else 0.70f) * glowPulse),
                accentColor.copy(alpha = 0.55f * glowPulse)
            )
        )
        else -> Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = if (isDark) 0.16f else 0.55f),
                Color.White.copy(alpha = if (isDark) 0.06f else 0.25f)
            )
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = when {
                    isDragged -> dragOffsetY
                    wasDragged -> lastDragOffset
                    else -> settlingOffsetY.value
                }
                scaleX = pressScale * liftScale * activePopScale.value * jellyScaleX.value * (1f - velocityStretch * 0.45f)
                scaleY = pressScale * liftScale * activePopScale.value * jellyScaleY.value * (1f + velocityStretch)
                rotationZ = dragTilt + jellyRotationZ.value
            }
            .shadow(
                elevation = rowElevation,
                shape = QueueRowShape,
                ambientColor = if (isCurrent || isDragged) accentColor.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.25f),
                spotColor = if (isCurrent || isDragged) accentColor.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.35f)
            )
            .clip(QueueRowShape)
            .background(rowBackground)
            .border(
                width = if (isDragged || isCurrent) 1.25.dp else 0.8.dp,
                brush = borderBrush,
                shape = QueueRowShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Album Cover Artwork with fallback MusicNote icon
        val coverScale by animateFloatAsState(
            targetValue = if (isCurrent && isPlaying) 1.04f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "queueCoverScale"
        )
        QueueTrackCover(
            track = track,
            isCurrent = isCurrent,
            accentColor = accentColor,
            isDark = isDark,
            sharedImageLoader = sharedImageLoader,
            modifier = Modifier.graphicsLayer {
                scaleX = coverScale
                scaleY = coverScale
            }
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Middle: Title & Artist
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = track.title,
                    fontFamily = GoogleSans,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = if (isCurrent) accentColor else appColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (track.isExplicit) {
                    ExplicitBadge()
                }
            }
            Text(
                text = track.artist,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = if (isCurrent) appColors.textPrimary.copy(alpha = 0.80f) else appColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Currently playing dancing equalizer indicator OR duration label
        if (isCurrent) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = (0.16f + 0.12f * glowPulse)))
                    .border(0.75.dp, accentColor.copy(alpha = 0.45f * glowPulse), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                QueueEqualizerBars(
                    color = accentColor,
                    isPlaying = isPlaying
                )
            }
        } else if (track.durationMs > 0L) {
            Text(
                text = track.formattedDuration,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Medium,
                fontSize = 11.5.sp,
                color = appColors.textSecondary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Right-side dedicated Drag Handle button
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 44.dp)
                .clip(CircleShape)
                .background(
                    if (isDragged) accentColor.copy(alpha = 0.22f) else Color.Transparent
                )
                .then(dragHandleModifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Drag to reorder",
                tint = if (isDragged) accentColor else appColors.textSecondary.copy(alpha = 0.80f),
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

@Composable
private fun QueueTrackCover(
    track: TrackItem,
    isCurrent: Boolean,
    accentColor: Color,
    isDark: Boolean,
    sharedImageLoader: coil.ImageLoader,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var uriLoadFailed by remember(track.artworkUri) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(48.dp)
            .clip(QueueArtworkShape)
            .background(if (isDark) Color(0xFF1E222B) else Color(0xFFE2E8F0))
            .then(
                if (isCurrent) {
                    Modifier.border(1.2.dp, accentColor.copy(alpha = 0.75f), QueueArtworkShape)
                } else {
                    Modifier.border(0.6.dp, Color.White.copy(alpha = if (isDark) 0.14f else 0.35f), QueueArtworkShape)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (track.artworkResId != null) {
            Image(
                painter = painterResource(id = track.artworkResId),
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (!track.artworkUri.isNullOrBlank() && !uriLoadFailed) {
            AsyncImage(
                model = ImageRequest.Builder(context.applicationContext)
                    .data(track.artworkUri)
                    .crossfade(200)
                    .build(),
                imageLoader = sharedImageLoader,
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                onError = { uriLoadFailed = true },
                onSuccess = { uriLoadFailed = false },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = if (isCurrent) accentColor else Color(0xFF94A3B8),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * Animated 4-bar equalizer synced smoothly to [isPlaying].
 */
@Composable
private fun QueueEqualizerBars(
    color: Color,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "queueEqBars")

    val wave1 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(360, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "qBar1"
    )
    val wave2 by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 0.30f,
        animationSpec = infiniteRepeatable(
            animation = tween(290, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "qBar2"
    )
    val wave3 by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(430, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "qBar3"
    )
    val wave4 by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(330, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "qBar4"
    )

    val ampScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "qEqAmp"
    )

    val maxBarHeightDp = 15f
    val minBarHeightDp = 3.5f

    fun barHeight(wave: Float, restFraction: Float) =
        (minBarHeightDp + (maxBarHeightDp - minBarHeightDp) * (restFraction + (wave - restFraction) * ampScale)).dp

    Row(
        modifier = modifier.height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val heights = listOf(
            barHeight(wave1, 0.22f),
            barHeight(wave2, 0.52f),
            barHeight(wave3, 0.34f),
            barHeight(wave4, 0.26f)
        )
        heights.forEach { barH ->
            Box(
                modifier = Modifier
                    .width(2.6.dp)
                    .height(barH)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(color)
            )
        }
    }
}
