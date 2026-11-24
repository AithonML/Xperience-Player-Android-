package com.xperience.player.ui.lyrics

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xperience.player.domain.model.lyrics.LyricLine
import com.xperience.player.ui.theme.AppThemeColors
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.PlusJakartaSans
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs

private const val USER_SCROLL_PAUSE_MS = 3800L

/**
 * High-performance Apple Music / Convx inspired synchronized lyrics renderer.
 *
 * Features:
 * - True word / glyph-group dynamic scale pop (`~1.12x-1.16x`) and gentle vertical lift
 *   driven strictly in the draw/layer phase (zero whole-line uniform active scaling, zero per-frame recompositions).
 * - Multi-layered progressive glow sweep: wide ambient radial bloom + tight core halo following the
 *   live horizontal wavefront + luminous `SrcAtop` leading edge + feathered `DstIn` progressive reveal.
 * - Frame-clock 60/120fps time interpolation via [PlaybackPositionInterpolator] while [isPlaying] is true.
 * - Tactile spring tap-to-seek feedback, optical defocus blur & depth opacity for distant lines,
 *   and smooth auto-centering that pauses cleanly on user gestures.
 */
@Composable
fun SynchronizedLyricsView(
    lines: List<LyricLine>,
    currentPositionMs: Long,
    providerName: String?,
    accentColor: Color,
    appColors: AppThemeColors,
    onSeekToPosition: (Long) -> Unit,
    onOpenLyricsSettings: () -> Unit,
    onImportLrcClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true
) {
    if (lines.isEmpty()) {
        EmptyLyricsState(
            accentColor = accentColor,
            appColors = appColors,
            onOpenLyricsSettings = onOpenLyricsSettings,
            onImportLrcClick = onImportLrcClick,
            modifier = modifier
        )
        return
    }

    val isSynchronized = remember(lines) { lines.any { it.isSynchronized } }
    val tokenizedLines = remember(lines) { LyricsTimingEngine.tokenizeLines(lines) }
    val listState = rememberLazyListState()

    val latestPositionMs by rememberUpdatedState(currentPositionMs)
    val latestIsPlaying by rememberUpdatedState(isPlaying)

    val interpolator = remember { PlaybackPositionInterpolator() }
    val smoothPositionState = remember { mutableLongStateOf(currentPositionMs.coerceAtLeast(0L)) }

    var hasAlignedInitially by remember(lines) { mutableStateOf(false) }

    // Reset timeline state cleanly when switching tracks or lyric sources
    LaunchedEffect(lines) {
        interpolator.snap(latestPositionMs)
        smoothPositionState.longValue = latestPositionMs.coerceAtLeast(0L)
        if (!isSynchronized) {
            listState.scrollToItem(0)
        }
    }

    // Immediate sync when paused or when user scrubs / seeks far
    LaunchedEffect(currentPositionMs, isPlaying) {
        if (!isPlaying) {
            interpolator.snap(currentPositionMs)
            smoothPositionState.longValue = currentPositionMs.coerceAtLeast(0L)
        } else if (abs(currentPositionMs - smoothPositionState.longValue) > LyricsTimingEngine.SEEK_SNAP_THRESHOLD_MS) {
            interpolator.snap(currentPositionMs)
            smoothPositionState.longValue = currentPositionMs.coerceAtLeast(0L)
        }
    }

    // 60/120fps display vsync interpolation loop while playing
    LaunchedEffect(isPlaying, isSynchronized, lines) {
        if (isPlaying && isSynchronized && lines.isNotEmpty()) {
            while (isActive) {
                withFrameNanos { frameNanos ->
                    val frameMs = frameNanos / 1_000_000L
                    smoothPositionState.longValue = interpolator.update(
                        reportedPositionMs = latestPositionMs,
                        isPlaying = latestIsPlaying,
                        frameTimeMs = frameMs
                    )
                }
            }
        }
    }

    // Derived active line index: recomposes LazyColumn ONLY when active line index changes
    val activeLineIndex by remember(lines, isSynchronized) {
        derivedStateOf {
            if (!isSynchronized) 0
            else LyricsTimingEngine.findActiveLineIndex(lines, smoothPositionState.longValue)
        }
    }
    val centerTargetIndex = activeLineIndex.coerceAtLeast(0)

    // Detect real user scroll gestures (distinguishing them from programmatic animateScrollBy)
    var userInteractingUntilMs by remember { mutableLongStateOf(0L) }
    var isUserScrolling by remember { mutableStateOf(false) }
    val isListDragged by listState.interactionSource.collectIsDraggedAsState()

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && abs(available.y) > 0.5f) {
                    userInteractingUntilMs = System.currentTimeMillis() + USER_SCROLL_PAUSE_MS
                    isUserScrolling = true
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(isListDragged) {
        if (isListDragged) {
            userInteractingUntilMs = System.currentTimeMillis() + USER_SCROLL_PAUSE_MS
            isUserScrolling = true
        }
    }

    LaunchedEffect(isUserScrolling, userInteractingUntilMs, isListDragged) {
        if (isUserScrolling) {
            while (isActive) {
                val now = System.currentTimeMillis()
                val remaining = userInteractingUntilMs - now
                if (isListDragged) {
                    userInteractingUntilMs = now + USER_SCROLL_PAUSE_MS
                    delay(250L)
                } else if (remaining > 0L) {
                    delay(remaining)
                } else {
                    isUserScrolling = false
                    break
                }
            }
        }
    }

    // Smooth geometric viewport centering when auto-scroll is active
    LaunchedEffect(centerTargetIndex, isSynchronized, isUserScrolling, lines) {
        if (isSynchronized && !isUserScrolling && centerTargetIndex in lines.indices) {
            if (!hasAlignedInitially) {
                // Wait a frame if LazyColumn hasn't measured its viewport yet so initial centering is exact
                if (listState.layoutInfo.viewportEndOffset <= listState.layoutInfo.viewportStartOffset) {
                    withFrameNanos { }
                }
                listState.scrollToItem(index = centerTargetIndex, scrollOffset = 0)
                hasAlignedInitially = true
            }

            val layoutInfo = listState.layoutInfo
            val viewportStart = layoutInfo.viewportStartOffset
            val viewportEnd = layoutInfo.viewportEndOffset
            val viewportHeight = (viewportEnd - viewportStart).coerceAtLeast(1)
            val targetViewportCenter = viewportStart + (viewportHeight * 0.43f)

            val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == centerTargetIndex }
            if (itemInfo != null) {
                val itemCenter = itemInfo.offset + (itemInfo.size / 2f)
                val delta = itemCenter - targetViewportCenter
                if (abs(delta) > 6f) {
                    listState.animateScrollBy(
                        value = delta,
                        animationSpec = tween(durationMillis = 720, easing = FastOutSlowInEasing)
                    )
                }
            } else {
                listState.animateScrollToItem(index = centerTargetIndex, scrollOffset = 0)
                val updatedInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == centerTargetIndex }
                if (updatedInfo != null) {
                    val vHeight = (listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset).coerceAtLeast(1)
                    val tCenter = listState.layoutInfo.viewportStartOffset + (vHeight * 0.43f)
                    val delta = (updatedInfo.offset + updatedInfo.size / 2f) - tCenter
                    if (abs(delta) > 6f) {
                        listState.animateScrollBy(
                            value = delta,
                            animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
                        )
                    }
                }
            }
        }
    }

    val isActiveLineOffscreen by remember(centerTargetIndex, isUserScrolling) {
        derivedStateOf {
            if (!isUserScrolling) false
            else listState.layoutInfo.visibleItemsInfo.none { it.index == centerTargetIndex }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Top Provider & Import .LRC Pill Header
        LyricsHeaderBar(
            providerName = providerName,
            isSynchronized = isSynchronized,
            accentColor = accentColor,
            appColors = appColors,
            onOpenLyricsSettings = onOpenLyricsSettings,
            onImportLrcClick = onImportLrcClick
        )

        // Lyrics Cascade Viewport with top/bottom optical edge fade
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val topPadding = (maxHeight * 0.34f).coerceIn(48.dp, 210.dp)
            val bottomPadding = (maxHeight * 0.46f).coerceIn(110.dp, 260.dp)

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    top = topPadding,
                    bottom = bottomPadding,
                    start = 20.dp,
                    end = 20.dp
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val topFadePx = 26.dp.toPx().coerceAtMost(size.height * 0.25f)
                        val bottomFadePx = 42.dp.toPx().coerceAtMost(size.height * 0.25f)
                        if (topFadePx > 0f) {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black),
                                    startY = 0f,
                                    endY = topFadePx
                                ),
                                size = Size(size.width, topFadePx),
                                blendMode = BlendMode.DstIn
                            )
                        }
                        if (bottomFadePx > 0f) {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Black, Color.Transparent),
                                    startY = size.height - bottomFadePx,
                                    endY = size.height
                                ),
                                topLeft = Offset(0f, size.height - bottomFadePx),
                                size = Size(size.width, bottomFadePx),
                                blendMode = BlendMode.DstIn
                            )
                        }
                    }
            ) {
                itemsIndexed(
                    items = lines,
                    key = { idx, item -> "${item.timestampMs}_$idx" }
                ) { index, line ->
                    val isActive = isSynchronized && index == activeLineIndex
                    val isPast = isSynchronized && activeLineIndex >= 0 && index < activeLineIndex
                    val distance = if (!isSynchronized) {
                        0
                    } else if (activeLineIndex < 0) {
                        index + 1
                    } else {
                        abs(index - activeLineIndex)
                    }

                    val wordTokens = tokenizedLines.getOrElse(index) { emptyList() }

                    LyricLineRow(
                        line = line,
                        wordTokens = wordTokens,
                        isSynchronized = isSynchronized,
                        isLineActive = isActive,
                        isLinePast = isPast,
                        distance = distance,
                        isUserScrolling = isUserScrolling,
                        accentColor = accentColor,
                        appColors = appColors,
                        positionMsProvider = { smoothPositionState.longValue },
                        onTapLine = {
                            if (isSynchronized) {
                                userInteractingUntilMs = 0L
                                isUserScrolling = false
                                onSeekToPosition(line.timestampMs)
                            }
                        }
                    )
                }
            }

            // Subtle re-center pill when the user scrolled the active line off-screen
            androidx.compose.animation.AnimatedVisibility(
                visible = isSynchronized && isActiveLineOffscreen,
                enter = fadeIn(tween(200)) + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut(tween(180)) + slideOutVertically(targetOffsetY = { it / 2 }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp)
            ) {
                val syncInteraction = remember { MutableInteractionSource() }
                val syncPressed by syncInteraction.collectIsPressedAsState()
                val syncScale by animateFloatAsState(
                    targetValue = if (syncPressed) 0.90f else 1f,
                    animationSpec = spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium),
                    label = "recenterPillScale"
                )
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = syncScale
                            scaleY = syncScale
                        }
                        .clip(CircleShape)
                        .background(
                            if (appColors.isDark) Color(0xCC181B24) else Color(0xE6FFFFFF)
                        )
                        .border(1.dp, accentColor.copy(alpha = 0.45f), CircleShape)
                        .clickable(
                            interactionSource = syncInteraction,
                            indication = null
                        ) {
                            userInteractingUntilMs = 0L
                            isUserScrolling = false
                        }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Back to active line",
                            tint = accentColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Sync",
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = appColors.textPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLyricsState(
    accentColor: Color,
    appColors: AppThemeColors,
    onOpenLyricsSettings: () -> Unit,
    onImportLrcClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                imageVector = Icons.Default.Lyrics,
                contentDescription = null,
                tint = appColors.glassTextPrimary.copy(alpha = 0.55f),
                modifier = Modifier.size(54.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "No synchronized lyrics found",
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = appColors.textPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Import a local .LRC file or adjust online lyrics providers",
                fontFamily = PlusJakartaSans,
                fontSize = 13.sp,
                color = appColors.textSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ElevatedButton(
                    onClick = onImportLrcClick,
                    shape = CircleShape,
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = accentColor,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.FileOpen,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Import .LRC",
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                OutlinedButton(
                    onClick = onOpenLyricsSettings,
                    shape = CircleShape,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (appColors.isDark) Color(0x45FFFFFF) else Color(0x30000000)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = appColors.textPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Providers",
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = appColors.textPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsHeaderBar(
    providerName: String?,
    isSynchronized: Boolean,
    accentColor: Color,
    appColors: AppThemeColors,
    onOpenLyricsSettings: () -> Unit,
    onImportLrcClick: () -> Unit
) {
    val providerInteraction = remember { MutableInteractionSource() }
    val providerPressed by providerInteraction.collectIsPressedAsState()
    val providerScale by animateFloatAsState(
        targetValue = if (providerPressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium),
        label = "providerPillScale"
    )

    val importInteraction = remember { MutableInteractionSource() }
    val importPressed by importInteraction.collectIsPressedAsState()
    val importScale by animateFloatAsState(
        targetValue = if (importPressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium),
        label = "importPillScale"
    )

    val pillFill = if (appColors.isDark) Color(0x42181B24) else Color(0x52FFFFFF)
    val pillBorder = if (appColors.isDark) Color(0x28FFFFFF) else Color(0x22000000)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = providerScale
                    scaleY = providerScale
                }
                .clip(CircleShape)
                .background(pillFill)
                .border(0.8.dp, pillBorder, CircleShape)
                .clickable(
                    interactionSource = providerInteraction,
                    indication = null,
                    onClick = onOpenLyricsSettings
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isSynchronized) Icons.Default.Sync else Icons.Default.Lyrics,
                    contentDescription = "Lyrics provider settings",
                    tint = accentColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = providerName ?: if (isSynchronized) "Synchronized" else "Plain Text",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp,
                    color = appColors.textPrimary
                )
            }
        }

        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = importScale
                    scaleY = importScale
                }
                .clip(CircleShape)
                .background(pillFill)
                .border(0.8.dp, pillBorder, CircleShape)
                .clickable(
                    interactionSource = importInteraction,
                    indication = null,
                    onClick = onImportLrcClick
                )
                .padding(horizontal = 11.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.FileOpen,
                    contentDescription = "Import LRC",
                    tint = appColors.textPrimary.copy(alpha = 0.85f),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "Import .LRC",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = appColors.textSecondary
                )
            }
        }
    }
}

/**
 * Renders one lyric line without uniform active-line box scaling.
 * Optical defocus blur, depth opacity, and tactile tap-to-seek press spring feedback live on the row,
 * while growth and progressive illumination happen word-by-word / glyph-by-glyph inside [FlowRow].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LyricLineRow(
    line: LyricLine,
    wordTokens: List<LyricWordToken>,
    isSynchronized: Boolean,
    isLineActive: Boolean,
    isLinePast: Boolean,
    distance: Int,
    isUserScrolling: Boolean,
    accentColor: Color,
    appColors: AppThemeColors,
    positionMsProvider: () -> Long,
    onTapLine: () -> Unit
) {
    val lineInteractionSource = remember { MutableInteractionSource() }
    val isPressed by lineInteractionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.962f else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.42f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "lyricLinePressWobble"
    )
    val pressHighlightAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.14f else 0f,
        animationSpec = tween(durationMillis = if (isPressed) 80 else 220),
        label = "lyricLinePressHighlight"
    )

    val distanceAlpha = when {
        !isSynchronized -> 0.90f
        isLineActive -> 1.0f
        distance == 1 -> 0.56f
        distance == 2 -> 0.34f
        distance == 3 -> 0.20f
        else -> 0.12f
    }
    val targetAlpha = if (isSynchronized && isUserScrolling && !isLineActive) {
        maxOf(distanceAlpha, 0.62f)
    } else {
        distanceAlpha
    }
    val lineAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 260),
        label = "lyricLineAlpha"
    )

    val targetBlur = if (!isSynchronized || isLineActive || distance <= 1 || isUserScrolling) {
        0f
    } else {
        when (distance) {
            2 -> 1.2f
            3 -> 2.8f
            else -> 4.2f
        }
    }
    val blurRadius by animateFloatAsState(
        targetValue = targetBlur,
        animationSpec = tween(durationMillis = 320),
        label = "lyricLineBlur"
    )

    val baseInk = if (appColors.isDark) Color.White else appColors.textPrimary
    val isAnimatingWords = isSynchronized && distance <= 1

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                // Tactile press dip only — never uniformly scale the active lyric line
                scaleX = pressScale
                scaleY = pressScale
                alpha = lineAlpha
            }
            .then(
                if (blurRadius > 0.1f) {
                    Modifier.blur(blurRadius.dp, BlurredEdgeTreatment.Unbounded)
                } else {
                    Modifier
                }
            )
            .drawBehind {
                if (pressHighlightAlpha > 0.005f) {
                    drawRoundRect(
                        color = accentColor.copy(alpha = pressHighlightAlpha),
                        cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                    )
                }
            }
            .clickable(
                interactionSource = lineInteractionSource,
                indication = null,
                onClick = onTapLine
            )
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        if (isSynchronized && wordTokens.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.Start,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                wordTokens.forEachIndexed { wordIndex, word ->
                    val trailingPad = when {
                        wordIndex >= wordTokens.lastIndex -> 0.dp
                        word.hasTrailingSpace -> 8.dp
                        word.isCjkToken -> 1.5.dp
                        else -> 0.dp
                    }
                    Box(modifier = Modifier.padding(end = trailingPad)) {
                        AnimatedLyricWord(
                            word = word,
                            isLineActive = isLineActive,
                            isLinePast = isLinePast,
                            isAnimatingWords = isAnimatingWords,
                            baseInk = baseInk,
                            accentColor = accentColor,
                            isDarkTheme = appColors.isDark,
                            positionMsProvider = positionMsProvider
                        )
                    }
                }
            }
        } else {
            Text(
                text = line.text,
                fontFamily = GoogleSans,
                fontWeight = if (isLinePast) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = 22.sp,
                lineHeight = 31.sp,
                color = if (isLinePast) appColors.textSecondary else appColors.textPrimary,
                textAlign = TextAlign.Start
            )
        }
    }
}

/**
 * Individual word / CJK-glyph renderer featuring:
 * - Individual dynamic scale pop (`~0.94f -> 1.12x-1.16x -> 1.00f`) and vertical float (`-4.5.dp`)
 * - Multi-layered progressive glow sweep (unbounded ambient radial bloom + tight inner wavefront core +
 *   luminous `SrcAtop` leading edge + feathered `DstIn` progressive glyph illumination)
 * - Zero per-frame recompositions: reads [positionMsProvider] exclusively inside `graphicsLayer` & draw lambdas.
 */
@Composable
private fun AnimatedLyricWord(
    word: LyricWordToken,
    isLineActive: Boolean,
    isLinePast: Boolean,
    isAnimatingWords: Boolean,
    baseInk: Color,
    accentColor: Color,
    isDarkTheme: Boolean,
    positionMsProvider: () -> Long
) {
    val unreachedAlpha by animateFloatAsState(
        targetValue = if (isLineActive) 0.34f else 1.0f,
        animationSpec = tween(durationMillis = 240),
        label = "wordUnreachedAlpha"
    )

    val hotCoreColor = remember(accentColor, isDarkTheme) {
        if (isDarkTheme) lerp(accentColor, Color.White, 0.50f) else accentColor
    }

    val fontSizeSp = 23.5f
    val lineHeightSp = 32f
    val wordFontWeight = if (isLineActive) FontWeight.ExtraBold else FontWeight.Bold

    val transformModifier = if (isAnimatingWords) {
        Modifier
            .graphicsLayer {
                val state = LyricsTimingEngine.computeWordVisualState(
                    word = word,
                    positionMs = positionMsProvider(),
                    isLineActive = isLineActive,
                    isLinePast = isLinePast,
                    isSynchronized = true
                )
                transformOrigin = TransformOrigin(0.5f, 0.65f)
                scaleX = state.scale
                scaleY = state.scale
                translationY = state.liftDp.dp.toPx()
            }
            .drawBehind {
                val state = LyricsTimingEngine.computeWordVisualState(
                    word = word,
                    positionMs = positionMsProvider(),
                    isLineActive = isLineActive,
                    isLinePast = isLinePast,
                    isSynchronized = true
                )
                if (state.glowIntensity > 0.01f) {
                    val wavefrontX = size.width * state.sweepProgress.coerceIn(0.06f, 0.94f)
                    val centerY = size.height * 0.54f

                    // Layer A: Wide radial ambient halo tracking the active letter wavefront
                    val outerRadius = (maxOf(size.height * 1.18f, 26.dp.toPx()) *
                        (0.72f + 0.38f * state.activeEnvelope)).coerceAtLeast(1f)
                    val outerAlpha = (0.14f + 0.34f * state.activeEnvelope) * state.glowIntensity
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = outerAlpha),
                                accentColor.copy(alpha = outerAlpha * 0.35f),
                                Color.Transparent
                            ),
                            center = Offset(wavefrontX, centerY),
                            radius = outerRadius
                        ),
                        radius = outerRadius,
                        center = Offset(wavefrontX, centerY)
                    )

                    // Layer B: Tight bright core bloom directly beneath the currently sung glyph(s)
                    if (state.activeEnvelope > 0.02f) {
                        val innerRadius = maxOf(size.height * 0.68f, 15.dp.toPx()).coerceAtLeast(1f)
                        val innerAlpha = 0.50f * state.glowIntensity * state.activeEnvelope
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    hotCoreColor.copy(alpha = innerAlpha),
                                    Color.Transparent
                                ),
                                center = Offset(wavefrontX, centerY),
                                radius = innerRadius
                            ),
                            radius = innerRadius,
                            center = Offset(wavefrontX, centerY)
                        )
                    }
                }
            }
    } else {
        val staticScale = if (isLinePast) 1.0f else 0.94f
        Modifier.graphicsLayer {
            transformOrigin = TransformOrigin(0.5f, 0.65f)
            scaleX = staticScale
            scaleY = staticScale
        }
    }

    Box(modifier = transformModifier) {
        // Base dimmed / unlit text layer (also serves as single lightweight layer for distant past/upcoming lines)
        Text(
            text = word.text,
            fontFamily = GoogleSans,
            fontSize = fontSizeSp.sp,
            fontWeight = wordFontWeight,
            lineHeight = lineHeightSp.sp,
            color = baseInk.copy(alpha = unreachedAlpha)
        )

        // Progressive lit foreground layer for active & transitioning adjacent lines
        if (isAnimatingWords) {
            Text(
                text = word.text,
                fontFamily = GoogleSans,
                fontSize = fontSizeSp.sp,
                fontWeight = wordFontWeight,
                lineHeight = lineHeightSp.sp,
                color = baseInk,
                modifier = Modifier
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        val state = LyricsTimingEngine.computeWordVisualState(
                            word = word,
                            positionMs = positionMsProvider(),
                            isLineActive = isLineActive,
                            isLinePast = isLinePast,
                            isSynchronized = true
                        )
                        val sweep = state.sweepProgress
                        if (sweep <= 0.001f) return@drawWithContent

                        drawContent()

                        if (sweep < 0.999f) {
                            val feather = 11.dp.toPx().coerceAtLeast(1f)
                            val center = (size.width + feather * 2f) * sweep - feather

                            // Luminous hotspot sheen across the active letter wavefront
                            val bandHalf = maxOf(10.dp.toPx(), size.width * 0.24f).coerceAtLeast(1f)
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        hotCoreColor.copy(alpha = 0.62f * state.glowIntensity),
                                        Color.Transparent
                                    ),
                                    startX = center - bandHalf,
                                    endX = center + bandHalf
                                ),
                                blendMode = BlendMode.SrcAtop
                            )

                            // Feathered progressive horizontal sweep mask
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color.Black, Color.Transparent),
                                    startX = center - feather,
                                    endX = center + feather
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        } else if (state.activeEnvelope > 0.02f) {
                            // Softly fade completed word's luminous sheen back to crisp baseInk
                            drawRect(
                                color = hotCoreColor.copy(alpha = 0.40f * state.glowIntensity * state.activeEnvelope),
                                blendMode = BlendMode.SrcAtop
                            )
                        }
                    }
            )
        }
    }
}
