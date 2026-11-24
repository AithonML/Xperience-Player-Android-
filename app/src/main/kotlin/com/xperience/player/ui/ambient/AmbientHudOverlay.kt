package com.xperience.player.ui.ambient

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xperience.player.R
import com.xperience.player.domain.model.PlaybackRepeatMode
import com.xperience.player.domain.model.PlaybackState
import com.xperience.player.ui.component.GlassCircleButton
import com.xperience.player.ui.component.GlassComponent
import com.xperience.player.ui.component.LocalGlassEffectConfig
import com.xperience.player.ui.component.isGlassAllowed
import com.xperience.player.ui.component.liquidGlass
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.player.MorphingPlayPauseButton
import com.xperience.player.ui.player.StrokedSkipNextIcon
import com.xperience.player.ui.player.StrokedSkipPreviousIcon
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.utils.pressWobble
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private val HudCapsuleShape = ContinuousRoundedRectangle(percent = 50)
private val HudDockShape = ContinuousRoundedRectangle(size = 28.dp)
private const val AMBIENT_TWO_PI = (2.0 * PI).toFloat()

/**
 * Auto-hiding Liquid Glass controls overlay for Ambient Mode:
 * - Top bar: Exit Ambient button, inline clock pill, panel mode switcher (Lyrics / Clock / Cinema),
 *   Visualizer toggle, Canvas toggle (when available), and Cast slot.
 * - Bottom dock: Interactive Ambient waveform seek bar (pauses auto-hide while scrubbing) with
 *   elapsed & tappable remaining/total timestamps, Favorite, Shuffle, Previous, Morphing Play/Pause,
 *   Next, and Repeat controls.
 * - Whisper-thin ambient progress filament along the bottom edge while HUD is hidden.
 */
@Composable
internal fun AmbientHudOverlay(
    state: AmbientModeUiState,
    hudState: AmbientHudState,
    actions: AmbientModeActions,
    accentColor: Color,
    isCompactHeight: Boolean,
    castButtonContent: (@Composable () -> Unit)?,
    onInteraction: () -> Unit,
    onScrubbingChanged: (Boolean) -> Unit,
    onSelectPanelMode: (AmbientPanelMode) -> Unit,
    onToggleVisualizer: () -> Unit,
    onToggleCanvasVideo: () -> Unit,
    onToggleRemainingTime: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = state.playbackState.currentTrack
    val hasCanvasVideo = !state.canvasArtwork?.videoUri.isNullOrBlank()

    Box(modifier = modifier.fillMaxSize()) {
        // Protective top/bottom gradient scrims visible only while HUD is shown
        AnimatedVisibility(
            visible = hudState.isHudVisible,
            enter = fadeIn(tween(240)),
            exit = fadeOut(tween(380)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(104.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.54f), Color.Transparent)
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(168.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.68f))
                            )
                        )
                )
            }
        }

        // TOP BAR
        AnimatedVisibility(
            visible = hudState.isHudVisible,
            enter = fadeIn(tween(240)) + slideInVertically(tween(280)) { -it / 2 },
            exit = fadeOut(tween(360)) + slideOutVertically(tween(340)) { -it / 2 },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .displayCutoutPadding()
                .padding(
                    horizontal = if (isCompactHeight) 18.dp else 28.dp,
                    vertical = if (isCompactHeight) 12.dp else 20.dp
                )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Exit Ambient Mode + subtle live clock chip
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassCircleButton(
                        onClick = actions.onExit,
                        size = if (isCompactHeight) 40.dp else 44.dp
                    ) {
                        Icon(
                            imageVector = Icons.Default.FullscreenExit,
                            contentDescription = "Exit Ambient Mode",
                            tint = Color.White,
                            modifier = Modifier.size(if (isCompactHeight) 20.dp else 22.dp)
                        )
                    }

                    AmbientGlassPill(
                        modifier = Modifier
                            .height(if (isCompactHeight) 36.dp else 40.dp)
                            .pointerInput(Unit) {
                                detectTapGestures { onInteraction() }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(accentColor)
                            )
                            Text(
                                text = "Ambient",
                                fontFamily = GoogleSans,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.90f)
                            )
                            if (hudState.panelMode != AmbientPanelMode.CLOCK_METADATA) {
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(12.dp)
                                        .background(Color.White.copy(alpha = 0.22f))
                                )
                                AmbientDigitalClock(
                                    isCompactHeight = isCompactHeight,
                                    accentColor = accentColor,
                                    showDate = false,
                                    compactInline = true
                                )
                            }
                        }
                    }
                }

                // Right: Mode switcher + Visualizer + Canvas + Cast slot
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AmbientGlassPill(
                        modifier = Modifier.height(if (isCompactHeight) 40.dp else 44.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            AmbientModeTabChip(
                                icon = Icons.Default.Lyrics,
                                label = "Lyrics",
                                selected = hudState.panelMode == AmbientPanelMode.LYRICS,
                                accentColor = accentColor,
                                showLabel = !isCompactHeight,
                                onClick = {
                                    onInteraction()
                                    onSelectPanelMode(AmbientPanelMode.LYRICS)
                                }
                            )
                            AmbientModeTabChip(
                                icon = Icons.Default.Schedule,
                                label = "Clock",
                                selected = hudState.panelMode == AmbientPanelMode.CLOCK_METADATA,
                                accentColor = accentColor,
                                showLabel = !isCompactHeight,
                                onClick = {
                                    onInteraction()
                                    onSelectPanelMode(AmbientPanelMode.CLOCK_METADATA)
                                }
                            )
                            AmbientModeTabChip(
                                icon = Icons.Default.Album,
                                label = "Cinema",
                                selected = hudState.panelMode == AmbientPanelMode.CINEMA_ART,
                                accentColor = accentColor,
                                showLabel = !isCompactHeight,
                                onClick = {
                                    onInteraction()
                                    onSelectPanelMode(AmbientPanelMode.CINEMA_ART)
                                }
                            )

                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .width(1.dp)
                                    .height(18.dp)
                                    .background(Color.White.copy(alpha = 0.20f))
                            )

                            AmbientToolbarIconButton(
                                icon = Icons.Default.GraphicEq,
                                contentDescription = if (hudState.isVisualizerEnabled) {
                                    "Disable ambient visualizer"
                                } else {
                                    "Enable ambient visualizer"
                                },
                                active = hudState.isVisualizerEnabled,
                                accentColor = accentColor,
                                onClick = {
                                    onInteraction()
                                    onToggleVisualizer()
                                }
                            )

                            if (hasCanvasVideo) {
                                AmbientToolbarIconButton(
                                    icon = Icons.Default.SlowMotionVideo,
                                    contentDescription = if (hudState.isCanvasVideoEnabled) {
                                        "Disable canvas artwork video"
                                    } else {
                                        "Enable canvas artwork video"
                                    },
                                    active = hudState.isCanvasVideoEnabled,
                                    accentColor = accentColor,
                                    onClick = {
                                        onInteraction()
                                        onToggleCanvasVideo()
                                    }
                                )
                            }

                            if (castButtonContent != null) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    castButtonContent()
                                }
                            } else {
                                AmbientToolbarIconButton(
                                    icon = if (state.isCastConnected) {
                                        Icons.Default.CastConnected
                                    } else {
                                        Icons.Default.Cast
                                    },
                                    contentDescription = if (state.isCastConnected) {
                                        "Casting to ${state.castDeviceName ?: "device"}"
                                    } else {
                                        "Cast to device"
                                    },
                                    active = state.isCastConnected,
                                    accentColor = accentColor,
                                    onClick = {
                                        onInteraction()
                                        actions.onOpenCastPicker()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // BOTTOM TRANSPORT DOCK (when track is active)
        if (track != null) {
            AnimatedVisibility(
                visible = hudState.isHudVisible,
                enter = fadeIn(tween(240)) + slideInVertically(tween(280)) { it / 2 },
                exit = fadeOut(tween(360)) + slideOutVertically(tween(340)) { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .displayCutoutPadding()
                    .padding(
                        horizontal = if (isCompactHeight) 18.dp else 32.dp,
                        vertical = if (isCompactHeight) 12.dp else 22.dp
                    )
            ) {
                AmbientTransportDock(
                    playbackState = state.playbackState,
                    isLiked = state.isLiked,
                    showRemainingTime = hudState.showRemainingTime,
                    accentColor = accentColor,
                    isCompactHeight = isCompactHeight,
                    onInteraction = onInteraction,
                    onScrubbingChanged = onScrubbingChanged,
                    onToggleRemainingTime = {
                        onInteraction()
                        onToggleRemainingTime()
                    },
                    onSeekToFraction = { frac ->
                        onInteraction()
                        actions.onSeekToFraction(frac)
                    },
                    onToggleFavorite = {
                        onInteraction()
                        actions.onToggleFavorite(track.id)
                    },
                    onToggleShuffle = {
                        onInteraction()
                        actions.onToggleShuffle()
                    },
                    onSkipPrevious = {
                        onInteraction()
                        actions.onSkipPrevious()
                    },
                    onTogglePlayPause = {
                        onInteraction()
                        actions.onTogglePlayPause()
                    },
                    onSkipNext = {
                        onInteraction()
                        actions.onSkipNext()
                    },
                    onCycleRepeatMode = {
                        onInteraction()
                        actions.onCycleRepeatMode()
                    }
                )
            }

            // Whisper-thin ambient hairline progress bar along the bottom edge when HUD is hidden
            AnimatedVisibility(
                visible = !hudState.isHudVisible,
                enter = fadeIn(tween(360)),
                exit = fadeOut(tween(200)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                AmbientBottomHairlineProgress(
                    progressFraction = state.playbackState.progressFraction,
                    accentColor = accentColor
                )
            }
        }
    }
}

@Composable
private fun AmbientTransportDock(
    playbackState: PlaybackState,
    isLiked: Boolean,
    showRemainingTime: Boolean,
    accentColor: Color,
    isCompactHeight: Boolean,
    onInteraction: () -> Unit,
    onScrubbingChanged: (Boolean) -> Unit,
    onToggleRemainingTime: () -> Unit,
    onSeekToFraction: (Float) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleShuffle: () -> Unit,
    onSkipPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onCycleRepeatMode: () -> Unit
) {
    val glassConfig = LocalGlassEffectConfig.current
    val useGlass = glassConfig.isEnabledFor(GlassComponent.PLAYER) && isGlassAllowed()

    val dockBackgroundModifier = if (useGlass) {
        Modifier.liquidGlass(
            config = glassConfig,
            shape = HudDockShape,
            highlightAlpha = 0.36f
        )
    } else {
        Modifier
            .background(Color(0x9912151E))
            .border(1.dp, Color.White.copy(alpha = 0.20f), HudDockShape)
    }

    Column(
        modifier = Modifier
            .widthIn(max = 740.dp)
            .fillMaxWidth()
            .clip(HudDockShape)
            .then(dockBackgroundModifier)
            // Consume taps & stray horizontal drags on the dock frame itself so interacting near
            // transport controls refreshes the HUD instead of hiding it or skipping tracks.
            .pointerInput(Unit) {
                detectTapGestures { onInteraction() }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ -> change.consume() }
            }
            .padding(
                horizontal = if (isCompactHeight) 16.dp else 22.dp,
                vertical = if (isCompactHeight) 8.dp else 14.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Interactive Ambient Seek Bar with elapsed & tappable remaining/total time
        AmbientSeekBar(
            currentPositionMs = playbackState.currentPositionMs,
            durationMs = playbackState.durationMs,
            isPlaying = playbackState.isPlaying,
            showRemainingTime = showRemainingTime,
            accentColor = accentColor,
            onSeek = onSeekToFraction,
            onScrubbingChanged = onScrubbingChanged,
            onToggleRemainingTime = onToggleRemainingTime
        )

        Spacer(modifier = Modifier.height(if (isCompactHeight) 4.dp else 8.dp))

        // 2. Transport Controls Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val favSource = remember { MutableInteractionSource() }
            IconButton(
                onClick = onToggleFavorite,
                interactionSource = favSource,
                modifier = Modifier
                    .pressWobble(favSource)
                    .size(if (isCompactHeight) 38.dp else 44.dp)
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isLiked) "Remove from favorites" else "Add to favorites",
                    tint = if (isLiked) accentColor else Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.size(if (isCompactHeight) 20.dp else 22.dp)
                )
            }

            val shuffleSource = remember { MutableInteractionSource() }
            IconButton(
                onClick = onToggleShuffle,
                interactionSource = shuffleSource,
                modifier = Modifier
                    .pressWobble(shuffleSource)
                    .size(if (isCompactHeight) 38.dp else 44.dp)
            ) {
                Icon(
                    painter = painterResource(
                        if (playbackState.isShuffleEnabled) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle_off
                    ),
                    contentDescription = "Shuffle",
                    tint = if (playbackState.isShuffleEnabled) accentColor else Color.White.copy(alpha = 0.62f),
                    modifier = Modifier.size(if (isCompactHeight) 20.dp else 22.dp)
                )
            }

            // Previous
            val prevSource = remember { MutableInteractionSource() }
            val skipBtnSize = if (isCompactHeight) 42.dp else 48.dp
            Box(
                modifier = Modifier
                    .pressWobble(prevSource)
                    .size(skipBtnSize)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f))
                    .border(0.8.dp, Color.White.copy(alpha = 0.24f), CircleShape)
                    .clickable(
                        interactionSource = prevSource,
                        indication = null,
                        onClick = onSkipPrevious
                    ),
                contentAlignment = Alignment.Center
            ) {
                StrokedSkipPreviousIcon(
                    tint = Color.White,
                    modifier = Modifier.size(if (isCompactHeight) 20.dp else 22.dp)
                )
            }

            // Hero Morphing Play/Pause
            MorphingPlayPauseButton(
                isPlaying = playbackState.isPlaying,
                onClick = onTogglePlayPause,
                buttonSize = if (isCompactHeight) 52.dp else 62.dp,
                iconSize = if (isCompactHeight) 24.dp else 28.dp,
                tint = Color.White
            )

            // Next
            val nextSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .pressWobble(nextSource)
                    .size(skipBtnSize)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f))
                    .border(0.8.dp, Color.White.copy(alpha = 0.24f), CircleShape)
                    .clickable(
                        interactionSource = nextSource,
                        indication = null,
                        onClick = onSkipNext
                    ),
                contentAlignment = Alignment.Center
            ) {
                StrokedSkipNextIcon(
                    tint = Color.White,
                    modifier = Modifier.size(if (isCompactHeight) 20.dp else 22.dp)
                )
            }

            // Repeat
            val repeatSource = remember { MutableInteractionSource() }
            val isRepeatActive = playbackState.repeatMode != PlaybackRepeatMode.Off
            IconButton(
                onClick = onCycleRepeatMode,
                interactionSource = repeatSource,
                modifier = Modifier
                    .pressWobble(repeatSource)
                    .size(if (isCompactHeight) 38.dp else 44.dp)
            ) {
                val repeatIconRes = when (playbackState.repeatMode) {
                    PlaybackRepeatMode.RepeatOne -> R.drawable.ic_repeat_one
                    PlaybackRepeatMode.RepeatAll -> R.drawable.ic_repeat_all
                    PlaybackRepeatMode.Off -> R.drawable.ic_repeat_off
                }
                Icon(
                    painter = painterResource(repeatIconRes),
                    contentDescription = "Repeat mode",
                    tint = if (isRepeatActive) accentColor else Color.White.copy(alpha = 0.62f),
                    modifier = Modifier.size(if (isCompactHeight) 20.dp else 22.dp)
                )
            }
        }
    }
}

/**
 * Interactive Ambient waveform scrubber supporting both tap-to-seek and drag scrubbing,
 * reporting active scrubbing state back to [AmbientHudController] so HUD auto-hide pauses mid-drag.
 */
@Composable
private fun AmbientSeekBar(
    currentPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    showRemainingTime: Boolean,
    accentColor: Color,
    onSeek: (Float) -> Unit,
    onScrubbingChanged: (Boolean) -> Unit,
    onToggleRemainingTime: () -> Unit,
    modifier: Modifier = Modifier
) {
    val safeDurationMs = durationMs.coerceAtLeast(0L)
    val safePositionMs = currentPositionMs.coerceAtLeast(0L)
    val playbackFraction = if (safeDurationMs > 0L) {
        (safePositionMs.toFloat() / safeDurationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(playbackFraction) }
    var pendingSeekFraction by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(playbackFraction, pendingSeekFraction) {
        val pending = pendingSeekFraction ?: return@LaunchedEffect
        if (abs(playbackFraction - pending) < 0.025f) {
            pendingSeekFraction = null
        } else {
            delay(400L)
            pendingSeekFraction = null
        }
    }

    val displayFraction = when {
        isDragging -> dragFraction
        pendingSeekFraction != null -> pendingSeekFraction!!
        else -> playbackFraction
    }.coerceIn(0f, 1f)

    val displayedPositionMs = when {
        isDragging -> (dragFraction * safeDurationMs).toLong()
        pendingSeekFraction != null -> (pendingSeekFraction!! * safeDurationMs).toLong()
        else -> safePositionMs
    }

    val shouldAnimateWave = isPlaying && !isDragging && safeDurationMs > 0L
    val waveAmplitudeFraction by animateFloatAsState(
        targetValue = if (shouldAnimateWave) 1f else 0f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "ambientSeekWaveAmp"
    )
    val thumbScale by animateFloatAsState(
        targetValue = if (isDragging) 1.35f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "ambientSeekThumbScale"
    )

    var wavePhase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(shouldAnimateWave) {
        if (shouldAnimateWave) {
            var lastNanos = withFrameNanos { it }
            while (isActive) {
                withFrameNanos { now ->
                    val dtSec = ((now - lastNanos).coerceIn(0L, 100_000_000L)) / 1_000_000_000f
                    lastNanos = now
                    wavePhase = (wavePhase + dtSec * AMBIENT_TWO_PI * 1.1f) % AMBIENT_TWO_PI
                }
            }
        }
    }

    val density = LocalDensity.current
    val insetPx = with(density) { 8.dp.toPx() }
    val wavePath = remember { Path() }

    val latestOnSeek by rememberUpdatedState(onSeek)
    val latestOnScrubbingChanged by rememberUpdatedState(onScrubbingChanged)
    val latestHasDuration by rememberUpdatedState(safeDurationMs > 0L)
    val latestInsetPx by rememberUpdatedState(insetPx)

    DisposableEffect(Unit) {
        onDispose {
            if (isDragging) {
                latestOnScrubbingChanged(false)
            }
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formatAmbientTime(displayedPositionMs),
            style = TextStyle(
                fontFamily = GoogleSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.5.sp,
                fontFeatureSettings = "tnum"
            ),
            color = Color.White.copy(alpha = 0.85f),
            maxLines = 1,
            modifier = Modifier.widthIn(min = 40.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .height(30.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        if (!latestHasDuration) return@detectTapGestures
                        val fraction = ambientXToFraction(offset.x, size.width.toFloat(), latestInsetPx)
                        pendingSeekFraction = fraction
                        latestOnSeek(fraction)
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            if (!latestHasDuration) return@detectHorizontalDragGestures
                            isDragging = true
                            latestOnScrubbingChanged(true)
                            dragFraction = ambientXToFraction(offset.x, size.width.toFloat(), latestInsetPx)
                        },
                        onDragEnd = {
                            if (!isDragging) return@detectHorizontalDragGestures
                            val target = dragFraction
                            isDragging = false
                            latestOnScrubbingChanged(false)
                            pendingSeekFraction = target
                            latestOnSeek(target)
                        },
                        onDragCancel = {
                            if (isDragging) {
                                isDragging = false
                                latestOnScrubbingChanged(false)
                            }
                        },
                        onHorizontalDrag = { change, _ ->
                            if (!isDragging) return@detectHorizontalDragGestures
                            change.consume()
                            dragFraction = ambientXToFraction(change.position.x, size.width.toFloat(), latestInsetPx)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
            ) {
                val trackY = size.height / 2f
                val startX = insetPx
                val endX = (size.width - insetPx).coerceAtLeast(startX + 1f)
                val trackWidth = (endX - startX).coerceAtLeast(1f)
                val activeX = startX + trackWidth * displayFraction

                val strokeWidthPx = 3.dp.toPx()
                val waveLengthPx = 24.dp.toPx()
                val maxAmpPx = 3.4.dp.toPx()
                val taperLenPx = 12.dp.toPx()
                val currentAmpPx = maxAmpPx * waveAmplitudeFraction

                if (endX > activeX) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.24f),
                        start = Offset(activeX, trackY),
                        end = Offset(endX, trackY),
                        strokeWidth = strokeWidthPx,
                        cap = StrokeCap.Round
                    )
                }

                if (activeX > startX + 0.5f) {
                    if (currentAmpPx < 0.08f) {
                        drawLine(
                            color = accentColor,
                            start = Offset(startX, trackY),
                            end = Offset(activeX, trackY),
                            strokeWidth = strokeWidthPx,
                            cap = StrokeCap.Round
                        )
                    } else {
                        wavePath.reset()
                        val stepPx = 1.5.dp.toPx().coerceAtLeast(2f)
                        wavePath.moveTo(startX, trackY)
                        var x = startX + stepPx
                        while (x < activeX) {
                            val startTaper = ((x - startX) / (taperLenPx * 0.5f)).coerceIn(0f, 1f)
                            val endTaper = ((activeX - x) / taperLenPx).coerceIn(0f, 1f)
                            val t = minOf(startTaper, endTaper)
                            val taper = t * t * (3f - 2f * t)
                            val angle = AMBIENT_TWO_PI * ((x - startX) / waveLengthPx) - wavePhase
                            wavePath.lineTo(x, trackY + currentAmpPx * taper * sin(angle))
                            x += stepPx
                        }
                        wavePath.lineTo(activeX, trackY)

                        drawPath(
                            path = wavePath,
                            color = accentColor.copy(alpha = 0.24f * waveAmplitudeFraction),
                            style = Stroke(
                                width = strokeWidthPx * 2.1f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                        drawPath(
                            path = wavePath,
                            color = accentColor,
                            style = Stroke(
                                width = strokeWidthPx,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }

                val thumbCenter = Offset(activeX, trackY)
                val glowRadiusPx = 12.dp.toPx() * thumbScale
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = if (isDragging) 0.62f else 0.42f),
                            Color.Transparent
                        ),
                        center = thumbCenter,
                        radius = glowRadiusPx
                    ),
                    radius = glowRadiusPx,
                    center = thumbCenter
                )
                drawCircle(
                    color = accentColor,
                    radius = 5.5.dp.toPx() * thumbScale,
                    center = thumbCenter
                )
                drawCircle(
                    color = Color.White,
                    radius = 2.2.dp.toPx() * thumbScale,
                    center = thumbCenter
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        val rightTimestamp = if (showRemainingTime) {
            formatRemainingTime(displayedPositionMs, safeDurationMs)
        } else {
            formatAmbientTime(safeDurationMs)
        }

        Text(
            text = rightTimestamp,
            style = TextStyle(
                fontFamily = GoogleSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.5.sp,
                fontFeatureSettings = "tnum"
            ),
            color = Color.White.copy(alpha = 0.78f),
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier
                .widthIn(min = 44.dp)
                .clip(HudCapsuleShape)
                .clickable(onClick = onToggleRemainingTime)
                .padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

private fun ambientXToFraction(x: Float, widthPx: Float, insetPx: Float): Float {
    val usable = (widthPx - insetPx * 2f).coerceAtLeast(1f)
    return ((x - insetPx) / usable).coerceIn(0f, 1f)
}

@Composable
private fun AmbientBottomHairlineProgress(
    progressFraction: Float,
    accentColor: Color
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "ambientHairlineProgress"
    )
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
    ) {
        drawRect(
            color = Color.White.copy(alpha = 0.12f),
            topLeft = Offset(0f, size.height - 2.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(size.width, 2.dp.toPx())
        )
        val activeWidth = size.width * animatedProgress
        if (activeWidth > 0.5f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(accentColor.copy(alpha = 0.65f), accentColor),
                    startX = 0f,
                    endX = activeWidth
                ),
                topLeft = Offset(0f, size.height - 2.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(activeWidth, 2.dp.toPx())
            )
        }
    }
}

@Composable
private fun AmbientGlassPill(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val glassConfig = LocalGlassEffectConfig.current
    val useGlass = glassConfig.isEnabledFor(GlassComponent.NAV_BAR) && isGlassAllowed()
    val bgModifier = if (useGlass) {
        Modifier.liquidGlass(
            config = glassConfig,
            shape = HudCapsuleShape,
            highlightAlpha = 0.34f
        )
    } else {
        Modifier
            .background(Color(0x88141822))
            .border(0.8.dp, Color.White.copy(alpha = 0.22f), HudCapsuleShape)
    }

    Box(
        modifier = modifier
            .clip(HudCapsuleShape)
            .then(bgModifier),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun AmbientModeTabChip(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    accentColor: Color,
    showLabel: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .pressWobble(interactionSource, pressedScale = 0.92f)
            .height(32.dp)
            .clip(HudCapsuleShape)
            .background(if (selected) accentColor.copy(alpha = 0.28f) else Color.Transparent)
            .then(
                if (selected) {
                    Modifier.border(0.8.dp, accentColor.copy(alpha = 0.65f), HudCapsuleShape)
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = if (showLabel) 11.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) accentColor else Color.White.copy(alpha = 0.78f),
            modifier = Modifier.size(16.dp)
        )
        if (showLabel) {
            Text(
                text = label,
                fontFamily = GoogleSans,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 12.sp,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.78f)
            )
        }
    }
}

@Composable
private fun AmbientToolbarIconButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .pressWobble(interactionSource, pressedScale = 0.90f)
            .size(34.dp)
            .clip(CircleShape)
            .background(if (active) accentColor.copy(alpha = 0.24f) else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) accentColor else Color.White.copy(alpha = 0.80f),
            modifier = Modifier.size(18.dp)
        )
    }
}
