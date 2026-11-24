package com.xperience.player.ui.ambient

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.xperience.player.domain.model.PlaybackState
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.domain.model.canvas.CanvasArtworkResult
import com.xperience.player.domain.model.lyrics.LyricLine
import com.xperience.player.service.MediaNetworkClients
import com.xperience.player.ui.component.ExplicitBadge
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.lyrics.LyricWordToken
import com.xperience.player.ui.lyrics.LyricsTimingEngine
import com.xperience.player.ui.lyrics.PlaybackPositionInterpolator
import com.xperience.player.ui.player.CanvasArtworkPlayer
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.PlusJakartaSans
import com.xperience.player.ui.utils.pressWobble
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private const val AMBIENT_LYRICS_USER_SCROLL_PAUSE_MS = 4_000L
private val AmbientArtworkShape = ContinuousRoundedRectangle(size = 28.dp)
private val AmbientMiniArtworkShape = ContinuousRoundedRectangle(size = 10.dp)
private val AmbientCardShape = ContinuousRoundedRectangle(size = 20.dp)
private val AmbientChipShape = ContinuousRoundedRectangle(percent = 50)

/**
 * Framed hero album artwork card with Palette-tinted luminous shadow, crisp specular hairline rim,
 * smooth play/pause breathing scale, and seamless Apple Music motion Canvas video overlay.
 */
@Composable
internal fun AmbientArtworkShowcase(
    track: TrackItem,
    canvasArtwork: CanvasArtworkResult?,
    isPlaying: Boolean,
    isCanvasVideoEnabled: Boolean,
    accentColor: Color,
    artworkSize: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val sharedImageLoader = remember(appContext) {
        MediaNetworkClients.getImageLoader(appContext)
    }

    val playbackScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.94f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "ambientArtworkPlayScale"
    )

    val activeCanvasUri = canvasArtwork?.videoUri?.takeIf { it.isNotBlank() && isCanvasVideoEnabled }

    Box(
        modifier = modifier
            .size(artworkSize)
            .graphicsLayer {
                scaleX = playbackScale
                scaleY = playbackScale
            }
            .shadow(
                elevation = 32.dp,
                shape = AmbientArtworkShape,
                ambientColor = accentColor.copy(alpha = 0.42f),
                spotColor = Color.Black.copy(alpha = 0.72f)
            )
            .clip(AmbientArtworkShape)
            .background(Color(0xFF141822))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.34f),
                        Color.White.copy(alpha = 0.08f)
                    )
                ),
                shape = AmbientArtworkShape
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            track.artworkResId != null -> {
                Image(
                    painter = painterResource(id = track.artworkResId),
                    contentDescription = track.album.ifBlank { track.title },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            !track.artworkUri.isNullOrBlank() -> {
                AsyncImage(
                    model = ImageRequest.Builder(appContext)
                        .data(track.artworkUri)
                        .crossfade(400)
                        .build(),
                    imageLoader = sharedImageLoader,
                    contentDescription = track.album.ifBlank { track.title },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    accentColor.copy(alpha = 0.24f),
                                    Color(0xFF141822)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = accentColor.copy(alpha = 0.85f),
                        modifier = Modifier.size((artworkSize * 0.32f).coerceIn(44.dp, 88.dp))
                    )
                }
            }
        }

        if (activeCanvasUri != null) {
            CanvasArtworkPlayer(
                videoUri = activeCanvasUri,
                isPlaying = isPlaying,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Compact track caption anchored beneath the album artwork in split Lyrics mode.
 */
@Composable
internal fun AmbientCompactTrackCaption(
    track: TrackItem,
    isCompactHeight: Boolean,
    modifier: Modifier = Modifier
) {
    val subtitle = remember(track.artist, track.album, track.title) {
        formatAmbientSubtitle(track)
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = track.title,
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = if (isCompactHeight) 17.sp else 23.sp,
                lineHeight = if (isCompactHeight) 22.sp else 28.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (track.isExplicit) {
                Spacer(modifier = Modifier.width(6.dp))
                ExplicitBadge(tint = Color.White, compact = isCompactHeight)
            }
        }

        if (subtitle.isNotEmpty()) {
            Spacer(modifier = Modifier.height(if (isCompactHeight) 2.dp else 4.dp))
            Text(
                text = subtitle,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Medium,
                fontSize = if (isCompactHeight) 12.5.sp else 14.5.sp,
                color = Color.White.copy(alpha = 0.72f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Distraction-free borderless widescreen synchronized lyrics panel for Ambient Mode.
 *
 * Reuses [LyricsTimingEngine] and [PlaybackPositionInterpolator] for 60/120fps word-by-word
 * luminous progression, optical depth defocus blur, top/bottom `DstIn` edge fades, and tap-to-seek.
 * Automatically falls back to [AmbientClockAndMetadataPanel] when no lyrics are available so the
 * right stage remains rich and balanced.
 */
@Composable
internal fun AmbientLyricsPanel(
    lines: List<LyricLine>,
    playbackState: PlaybackState,
    track: TrackItem,
    accentColor: Color,
    lyricsProviderName: String?,
    audioQualityLabel: String,
    outputLabel: String,
    isCastConnected: Boolean,
    isCompactHeight: Boolean,
    isHudVisible: Boolean,
    onSeekToPosition: (Long) -> Unit,
    onPlayQueueIndex: (Int) -> Unit,
    onOpenLyricsSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (lines.isEmpty()) {
        AmbientClockAndMetadataPanel(
            track = track,
            playbackState = playbackState,
            accentColor = accentColor,
            audioQualityLabel = audioQualityLabel,
            outputLabel = outputLabel,
            isCastConnected = isCastConnected,
            isCompactHeight = isCompactHeight,
            noLyricsHint = true,
            onPlayQueueIndex = onPlayQueueIndex,
            modifier = modifier
        )
        return
    }

    val isSynchronized = remember(lines) { lines.any { it.isSynchronized } }
    val tokenizedLines = remember(lines) { LyricsTimingEngine.tokenizeLines(lines) }
    val listState = rememberLazyListState()

    val latestPositionMs by rememberUpdatedState(playbackState.currentPositionMs)
    val latestIsPlaying by rememberUpdatedState(playbackState.isPlaying)

    val interpolator = remember { PlaybackPositionInterpolator() }
    val smoothPositionState = remember { mutableLongStateOf(playbackState.currentPositionMs.coerceAtLeast(0L)) }
    var hasAlignedInitially by remember(lines) { mutableStateOf(false) }

    LaunchedEffect(lines) {
        interpolator.snap(latestPositionMs)
        smoothPositionState.longValue = latestPositionMs.coerceAtLeast(0L)
        if (!isSynchronized) {
            listState.scrollToItem(0)
        }
    }

    LaunchedEffect(playbackState.currentPositionMs, playbackState.isPlaying) {
        if (!playbackState.isPlaying) {
            interpolator.snap(playbackState.currentPositionMs)
            smoothPositionState.longValue = playbackState.currentPositionMs.coerceAtLeast(0L)
        } else if (abs(playbackState.currentPositionMs - smoothPositionState.longValue) > LyricsTimingEngine.SEEK_SNAP_THRESHOLD_MS) {
            interpolator.snap(playbackState.currentPositionMs)
            smoothPositionState.longValue = playbackState.currentPositionMs.coerceAtLeast(0L)
        }
    }

    LaunchedEffect(playbackState.isPlaying, isSynchronized, lines) {
        if (playbackState.isPlaying && isSynchronized && lines.isNotEmpty()) {
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

    val activeLineIndex by remember(lines, isSynchronized) {
        derivedStateOf {
            if (!isSynchronized) 0
            else LyricsTimingEngine.findActiveLineIndex(lines, smoothPositionState.longValue)
        }
    }
    val centerTargetIndex = activeLineIndex.coerceAtLeast(0)

    var userInteractingUntilMs by remember { mutableLongStateOf(0L) }
    var isUserScrolling by remember { mutableStateOf(false) }
    val isListDragged by listState.interactionSource.collectIsDraggedAsState()

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && abs(available.y) > 0.5f) {
                    userInteractingUntilMs = System.currentTimeMillis() + AMBIENT_LYRICS_USER_SCROLL_PAUSE_MS
                    isUserScrolling = true
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(isListDragged) {
        if (isListDragged) {
            userInteractingUntilMs = System.currentTimeMillis() + AMBIENT_LYRICS_USER_SCROLL_PAUSE_MS
            isUserScrolling = true
        }
    }

    LaunchedEffect(isUserScrolling, userInteractingUntilMs, isListDragged) {
        if (isUserScrolling) {
            while (isActive) {
                val now = System.currentTimeMillis()
                val remaining = userInteractingUntilMs - now
                if (isListDragged) {
                    userInteractingUntilMs = now + AMBIENT_LYRICS_USER_SCROLL_PAUSE_MS
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

    LaunchedEffect(centerTargetIndex, isSynchronized, isUserScrolling, lines) {
        if (isSynchronized && !isUserScrolling && centerTargetIndex in lines.indices) {
            if (!hasAlignedInitially) {
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
            val targetViewportCenter = viewportStart + (viewportHeight * 0.44f)

            val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == centerTargetIndex }
            if (itemInfo != null) {
                val itemCenter = itemInfo.offset + (itemInfo.size / 2f)
                val delta = itemCenter - targetViewportCenter
                if (abs(delta) > 6f) {
                    listState.animateScrollBy(
                        value = delta,
                        animationSpec = tween(durationMillis = 680, easing = FastOutSlowInEasing)
                    )
                }
            } else {
                listState.animateScrollToItem(index = centerTargetIndex, scrollOffset = 0)
            }
        }
    }

    val isActiveLineOffscreen by remember(centerTargetIndex, isUserScrolling) {
        derivedStateOf {
            if (!isUserScrolling) false
            else listState.layoutInfo.visibleItemsInfo.none { it.index == centerTargetIndex }
        }
    }

    val wordFontSizeSp = if (isCompactHeight) 21.5f else 28f
    val wordLineHeightSp = if (isCompactHeight) 30f else 39f

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val topPadding = (maxHeight * 0.34f).coerceIn(48.dp, 220.dp)
        val bottomPadding = (maxHeight * 0.44f).coerceIn(88.dp, 240.dp)

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                top = topPadding,
                bottom = bottomPadding,
                start = 8.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 16.dp else 24.dp),
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val topFadePx = 38.dp.toPx().coerceAtMost(size.height * 0.28f)
                    val bottomFadePx = 52.dp.toPx().coerceAtMost(size.height * 0.28f)
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
                val distance = when {
                    !isSynchronized -> 0
                    activeLineIndex < 0 -> index + 1
                    else -> abs(index - activeLineIndex)
                }
                val wordTokens = tokenizedLines.getOrElse(index) { emptyList() }

                AmbientLyricLineRow(
                    line = line,
                    wordTokens = wordTokens,
                    isSynchronized = isSynchronized,
                    isLineActive = isActive,
                    isLinePast = isPast,
                    distance = distance,
                    isUserScrolling = isUserScrolling,
                    accentColor = accentColor,
                    fontSizeSp = wordFontSizeSp,
                    lineHeightSp = wordLineHeightSp,
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

        // Subtle lyrics provider badge visible only when HUD is open
        AnimatedVisibility(
            visible = isHudVisible && !lyricsProviderName.isNullOrBlank(),
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(240)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(AmbientChipShape)
                    .background(Color.Black.copy(alpha = 0.34f))
                    .border(0.8.dp, Color.White.copy(alpha = 0.16f), AmbientChipShape)
                    .clickable(onClick = onOpenLyricsSettings)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lyrics,
                    contentDescription = "Lyrics provider",
                    tint = accentColor,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = lyricsProviderName.orEmpty(),
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.78f),
                    maxLines = 1
                )
            }
        }

        // Floating Sync re-center pill when user manually scrolled away from active line
        AnimatedVisibility(
            visible = isSynchronized && isActiveLineOffscreen,
            enter = fadeIn(tween(200)) + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut(tween(180)) + slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        ) {
            val syncInteraction = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .pressWobble(syncInteraction, pressedScale = 0.92f)
                    .clip(CircleShape)
                    .background(Color(0xCC121620))
                    .border(1.dp, accentColor.copy(alpha = 0.50f), CircleShape)
                    .clickable(
                        interactionSource = syncInteraction,
                        indication = null
                    ) {
                        userInteractingUntilMs = 0L
                        isUserScrolling = false
                    }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "Sync to active lyric line",
                    tint = accentColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Sync Lyrics",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = Color.White
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AmbientLyricLineRow(
    line: LyricLine,
    wordTokens: List<LyricWordToken>,
    isSynchronized: Boolean,
    isLineActive: Boolean,
    isLinePast: Boolean,
    distance: Int,
    isUserScrolling: Boolean,
    accentColor: Color,
    fontSizeSp: Float,
    lineHeightSp: Float,
    positionMsProvider: () -> Long,
    onTapLine: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.965f else 1.0f,
        animationSpec = spring(dampingRatio = 0.44f, stiffness = Spring.StiffnessMedium),
        label = "ambientLyricPressScale"
    )
    val pressHighlightAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.14f else 0f,
        animationSpec = tween(durationMillis = if (isPressed) 80 else 200),
        label = "ambientLyricPressBg"
    )

    val distanceAlpha = when {
        !isSynchronized -> 0.88f
        isLineActive -> 1.0f
        distance == 1 -> 0.52f
        distance == 2 -> 0.30f
        distance == 3 -> 0.18f
        else -> 0.10f
    }
    val targetAlpha = if (isSynchronized && isUserScrolling && !isLineActive) {
        maxOf(distanceAlpha, 0.62f)
    } else {
        distanceAlpha
    }
    val lineAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 260),
        label = "ambientLyricAlpha"
    )

    val targetBlur = if (!isSynchronized || isLineActive || distance <= 1 || isUserScrolling) {
        0f
    } else {
        when (distance) {
            2 -> 1.2f
            3 -> 2.6f
            else -> 4.0f
        }
    }
    val blurRadius by animateFloatAsState(
        targetValue = targetBlur,
        animationSpec = tween(durationMillis = 300),
        label = "ambientLyricBlur"
    )

    val isAnimatingWords = isSynchronized && distance <= 1

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
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
                interactionSource = interactionSource,
                indication = null,
                onClick = onTapLine
            )
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        if (isSynchronized && wordTokens.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.Start,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                wordTokens.forEachIndexed { wordIndex, word ->
                    val trailingPad = when {
                        wordIndex >= wordTokens.lastIndex -> 0.dp
                        word.hasTrailingSpace -> 7.dp
                        word.isCjkToken -> 1.5.dp
                        else -> 0.dp
                    }
                    Box(modifier = Modifier.padding(end = trailingPad)) {
                        AmbientAnimatedLyricWord(
                            word = word,
                            isLineActive = isLineActive,
                            isLinePast = isLinePast,
                            isAnimatingWords = isAnimatingWords,
                            accentColor = accentColor,
                            fontSizeSp = fontSizeSp,
                            lineHeightSp = lineHeightSp,
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
                fontSize = fontSizeSp.sp,
                lineHeight = lineHeightSp.sp,
                color = if (isLinePast) Color.White.copy(alpha = 0.65f) else Color.White,
                textAlign = TextAlign.Start
            )
        }
    }
}

@Composable
private fun AmbientAnimatedLyricWord(
    word: LyricWordToken,
    isLineActive: Boolean,
    isLinePast: Boolean,
    isAnimatingWords: Boolean,
    accentColor: Color,
    fontSizeSp: Float,
    lineHeightSp: Float,
    positionMsProvider: () -> Long
) {
    val unreachedAlpha by animateFloatAsState(
        targetValue = if (isLineActive) 0.34f else 1.0f,
        animationSpec = tween(durationMillis = 220),
        label = "ambientWordUnreachedAlpha"
    )
    val hotCoreColor = remember(accentColor) {
        lerp(accentColor, Color.White, 0.52f)
    }
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
                    val outerRadius = (maxOf(size.height * 1.16f, 24.dp.toPx()) *
                        (0.72f + 0.36f * state.activeEnvelope)).coerceAtLeast(1f)
                    val outerAlpha = (0.14f + 0.32f * state.activeEnvelope) * state.glowIntensity
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = outerAlpha),
                                accentColor.copy(alpha = outerAlpha * 0.32f),
                                Color.Transparent
                            ),
                            center = Offset(wavefrontX, centerY),
                            radius = outerRadius
                        ),
                        radius = outerRadius,
                        center = Offset(wavefrontX, centerY)
                    )
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
        Text(
            text = word.text,
            fontFamily = GoogleSans,
            fontSize = fontSizeSp.sp,
            fontWeight = wordFontWeight,
            lineHeight = lineHeightSp.sp,
            color = Color.White.copy(alpha = unreachedAlpha)
        )

        if (isAnimatingWords) {
            Text(
                text = word.text,
                fontFamily = GoogleSans,
                fontSize = fontSizeSp.sp,
                fontWeight = wordFontWeight,
                lineHeight = lineHeightSp.sp,
                color = Color.White,
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
                            val bandHalf = maxOf(10.dp.toPx(), size.width * 0.24f).coerceAtLeast(1f)
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        hotCoreColor.copy(alpha = 0.60f * state.glowIntensity),
                                        Color.Transparent
                                    ),
                                    startX = center - bandHalf,
                                    endX = center + bandHalf
                                ),
                                blendMode = BlendMode.SrcAtop
                            )
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color.Black, Color.Transparent),
                                    startX = center - feather,
                                    endX = center + feather
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        } else if (state.activeEnvelope > 0.02f) {
                            drawRect(
                                color = hotCoreColor.copy(alpha = 0.36f * state.glowIntensity * state.activeEnvelope),
                                blendMode = BlendMode.SrcAtop
                            )
                        }
                    }
            )
        }
    }
}

/**
 * Editorial right-side panel combining a minimalist ambient clock, live Now Playing status,
 * track metadata, audio quality & output badges, and a tappable "Up Next" queue preview.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AmbientClockAndMetadataPanel(
    track: TrackItem,
    playbackState: PlaybackState,
    accentColor: Color,
    audioQualityLabel: String,
    outputLabel: String,
    isCastConnected: Boolean,
    isCompactHeight: Boolean,
    noLyricsHint: Boolean = false,
    onPlayQueueIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val upNextTrack = remember(playbackState) { resolveUpNextTrack(playbackState) }
    val upNextIndex = remember(playbackState) { resolveUpNextQueueIndex(playbackState) }
    val subtitle = remember(track.artist, track.album, track.title) { formatAmbientSubtitle(track) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = if (isCompactHeight) 8.dp else 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        // 1. Minimal Ambient Editorial Clock
        AmbientDigitalClock(
            isCompactHeight = isCompactHeight,
            accentColor = accentColor
        )

        Spacer(modifier = Modifier.height(if (isCompactHeight) 14.dp else 26.dp))

        // 2. Live "NOW PLAYING" eyebrow with animated micro-equalizer bars
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AmbientLiveEqIndicator(
                isPlaying = playbackState.isPlaying,
                accentColor = accentColor
            )
            Text(
                text = if (playbackState.isPlaying) "NOW PLAYING" else "PAUSED",
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.6.sp,
                color = accentColor.copy(alpha = 0.92f)
            )
            if (noLyricsHint) {
                Text(
                    text = "· Lyrics unavailable",
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.48f)
                )
            }
        }

        Spacer(modifier = Modifier.height(if (isCompactHeight) 6.dp else 10.dp))

        // 3. Large Editorial Track Title + Explicit Badge + Artist / Album
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = track.title,
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = if (isCompactHeight) 24.sp else 34.sp,
                lineHeight = if (isCompactHeight) 29.sp else 40.sp,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (track.isExplicit) {
                Spacer(modifier = Modifier.width(8.dp))
                ExplicitBadge(tint = Color.White, compact = isCompactHeight)
            }
        }

        if (subtitle.isNotEmpty()) {
            Spacer(modifier = Modifier.height(if (isCompactHeight) 3.dp else 6.dp))
            Text(
                text = subtitle,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Medium,
                fontSize = if (isCompactHeight) 14.sp else 17.sp,
                color = Color.White.copy(alpha = 0.74f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(if (isCompactHeight) 10.dp else 18.dp))

        // 4. Audio Quality & Output / Cast Badges
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AmbientMetadataPill(
                icon = Icons.Default.HighQuality,
                text = audioQualityLabel,
                tint = accentColor
            )
            AmbientMetadataPill(
                icon = if (isCastConnected) Icons.Default.CastConnected else Icons.Default.Speaker,
                text = outputLabel,
                tint = if (isCastConnected) accentColor else Color.White.copy(alpha = 0.80f)
            )
        }

        // 5. "Up Next" Preview Card (when queue has a following track)
        if (upNextTrack != null && upNextTrack.id != track.id && upNextIndex != null) {
            Spacer(modifier = Modifier.height(if (isCompactHeight) 12.dp else 22.dp))
            AmbientUpNextCard(
                track = upNextTrack,
                accentColor = accentColor,
                isCompactHeight = isCompactHeight,
                onClick = { onPlayQueueIndex(upNextIndex) }
            )
        }
    }
}

/**
 * Live ambient digital clock using tabular numerals and locale-aware 12h/24h formatting.
 */
@Composable
internal fun AmbientDigitalClock(
    isCompactHeight: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    showDate: Boolean = true,
    compactInline: Boolean = false
) {
    val context = LocalContext.current
    val is24Hour = remember(context) {
        runCatching { DateFormat.is24HourFormat(context) }.getOrDefault(false)
    }

    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    var currentDate by remember { mutableStateOf(LocalDate.now()) }

    LaunchedEffect(Unit) {
        while (isActive) {
            val now = LocalTime.now()
            currentTime = now
            currentDate = LocalDate.now()
            val millisUntilNextSecond = (1_000L - (now.nano / 1_000_000L)).coerceIn(100L, 1_000L)
            delay(millisUntilNextSecond)
        }
    }

    val timeFormatter = remember(is24Hour) {
        DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm", Locale.getDefault())
    }
    val amPmFormatter = remember {
        DateTimeFormatter.ofPattern("a", Locale.getDefault())
    }
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
    }

    val formattedTime = timeFormatter.format(currentTime)
    val amPm = if (is24Hour) "" else amPmFormatter.format(currentTime).uppercase(Locale.getDefault())
    val formattedDate = dateFormatter.format(currentDate).uppercase(Locale.getDefault())

    if (compactInline) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = formattedTime,
                style = TextStyle(
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    fontFeatureSettings = "tnum"
                ),
                color = Color.White.copy(alpha = 0.90f)
            )
            if (amPm.isNotEmpty()) {
                Text(
                    text = amPm,
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.62f)
                )
            }
        }
        return
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = formattedTime,
                style = TextStyle(
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isCompactHeight) 44.sp else 68.sp,
                    lineHeight = if (isCompactHeight) 46.sp else 70.sp,
                    letterSpacing = (-1.5).sp,
                    fontFeatureSettings = "tnum"
                ),
                color = Color.White.copy(alpha = 0.96f)
            )
            if (amPm.isNotEmpty()) {
                Text(
                    text = amPm,
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = if (isCompactHeight) 14.sp else 19.sp,
                    color = accentColor.copy(alpha = 0.85f),
                    modifier = Modifier.padding(bottom = if (isCompactHeight) 6.dp else 10.dp)
                )
            }
        }
        if (showDate) {
            Text(
                text = formattedDate,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (isCompactHeight) 11.sp else 13.sp,
                letterSpacing = 1.8.sp,
                color = Color.White.copy(alpha = 0.56f)
            )
        }
    }
}

@Composable
private fun AmbientLiveEqIndicator(
    isPlaying: Boolean,
    accentColor: Color
) {
    if (!isPlaying) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.height(12.dp)
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .width(2.5.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accentColor.copy(alpha = 0.65f))
                )
            }
        }
        return
    }

    val transition = rememberInfiniteTransition(label = "ambientEqBars")
    val bar1 by transition.animateFloat(
        initialValue = 0.30f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(520, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eqBar1"
    )
    val bar2 by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(680, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eqBar2"
    )
    val bar3 by transition.animateFloat(
        initialValue = 0.40f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(440, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eqBar3"
    )

    val heights = floatArrayOf(bar1, bar2, bar3)

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(12.dp)
    ) {
        heights.forEach { frac ->
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height((12f * frac).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )
        }
    }
}

@Composable
private fun AmbientMetadataPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color
) {
    Row(
        modifier = Modifier
            .clip(AmbientChipShape)
            .background(Color.White.copy(alpha = 0.10f))
            .border(0.8.dp, Color.White.copy(alpha = 0.18f), AmbientChipShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = text,
            fontFamily = GoogleSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.5.sp,
            color = Color.White.copy(alpha = 0.88f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AmbientUpNextCard(
    track: TrackItem,
    accentColor: Color,
    isCompactHeight: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val sharedImageLoader = remember(appContext) {
        MediaNetworkClients.getImageLoader(appContext)
    }
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .pressWobble(interactionSource, pressedScale = 0.96f)
            .widthIn(max = 380.dp)
            .fillMaxWidth()
            .clip(AmbientCardShape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(0.8.dp, Color.White.copy(alpha = 0.16f), AmbientCardShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = if (isCompactHeight) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(if (isCompactHeight) 36.dp else 44.dp)
                .clip(AmbientMiniArtworkShape)
                .background(Color(0xFF1A1F2C)),
            contentAlignment = Alignment.Center
        ) {
            when {
                track.artworkResId != null -> {
                    Image(
                        painter = painterResource(id = track.artworkResId),
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                !track.artworkUri.isNullOrBlank() -> {
                    AsyncImage(
                        model = ImageRequest.Builder(appContext)
                            .data(track.artworkUri)
                            .crossfade(240)
                            .build(),
                        imageLoader = sharedImageLoader,
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = accentColor.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "UP NEXT",
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = 9.5.sp,
                letterSpacing = 1.2.sp,
                color = accentColor.copy(alpha = 0.88f)
            )
            Text(
                text = track.title,
                fontFamily = GoogleSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (isCompactHeight) 13.sp else 14.5.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Normal,
                fontSize = if (isCompactHeight) 11.sp else 12.5.sp,
                color = Color.White.copy(alpha = 0.66f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = Icons.Default.SkipNext,
            contentDescription = "Play next track",
            tint = Color.White.copy(alpha = 0.62f),
            modifier = Modifier.size(20.dp)
        )
    }
}
