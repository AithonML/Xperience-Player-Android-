package com.xperience.player.ui.ambient

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xperience.player.domain.model.PlaybackRepeatMode
import com.xperience.player.domain.model.PlaybackState
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.domain.model.canvas.CanvasArtworkResult
import com.xperience.player.domain.model.visualizer.VisualizerRenderMode
import com.xperience.player.ui.adaptive.AdaptivePreviewFixtures
import com.xperience.player.ui.adaptive.AdaptivePreviewSurface
import com.xperience.player.ui.component.LocalAppBackdrop
import com.xperience.player.ui.component.backdrop.backdrops.layerBackdrop
import com.xperience.player.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.theme.ArtworkColorExtractor
import com.xperience.player.ui.theme.DarkThemeColors
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.PlusJakartaSans
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val GestureFeedbackPillShape = ContinuousRoundedRectangle(percent = 50)

private data class AmbientGestureFeedback(
    val id: Long,
    val icon: ImageVector,
    val label: String
)

/**
 * Full-screen landscape **Ambient Mode** player for Xperience Music Player.
 *
 * Features:
 * - Automatic sensor-landscape lock, immersive hidden system bars, and `FLAG_KEEP_SCREEN_ON`,
 *   all restored cleanly on exit via [AmbientImmersiveSystemEffect].
 * - Full-bleed blurred album art + Palette-derived floating Aurora gradient mesh + optional
 *   subtle GPU visualizer + optional Apple Music motion Canvas artwork.
 * - Split widescreen layout supporting live word-by-word synchronized lyrics, an editorial
 *   ambient clock & track metadata panel with "Up Next" card, or minimalist Cinema gallery mode.
 * - Auto-hiding Liquid Glass controls overlay with tap-to-toggle, double-tap play/pause,
 *   horizontal swipe track navigation, gentle OLED burn-in drift, and [BackHandler] exit.
 */
@Composable
fun AmbientModeScreen(
    state: AmbientModeUiState,
    actions: AmbientModeActions,
    castButtonContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    initialPanelMode: AmbientPanelMode? = null,
    initialHudVisible: Boolean = true,
    hudAutoHideDelayMs: Long = DEFAULT_AMBIENT_HUD_AUTO_HIDE_MS,
    enableSystemUiControl: Boolean = true,
    enableBurnInProtection: Boolean = true
) {
    // System immersive landscape lock + keep-screen-on with safe teardown
    AmbientImmersiveSystemEffect(enabled = enableSystemUiControl)

    // Clean hardware/gesture back exit
    BackHandler(onBack = actions.onExit)

    val context = LocalContext.current
    val appContext = context.applicationContext
    val fallbackAccent = LocalAccentColor.current
    val track = state.playbackState.currentTrack

    val resolvedInitialPanel = initialPanelMode ?: resolveDefaultPanelMode(state.lyrics.isNotEmpty())
    val initialVizOn = state.selectedVisualizer.renderMode != VisualizerRenderMode.None

    val hudController = remember(hudAutoHideDelayMs) {
        AmbientHudController(
            initialPanelMode = resolvedInitialPanel,
            initialHudVisible = initialHudVisible,
            initialVisualizerEnabled = initialVizOn,
            initialCanvasVideoEnabled = true,
            hudAutoHideDelayMs = hudAutoHideDelayMs
        )
    }
    val hudState by hudController.state.collectAsState()

    DisposableEffect(hudController) {
        onDispose {
            hudController.cancelAutoHideTimer()
        }
    }

    // Ensure scrubbing never remains stuck if playback clears mid-drag
    LaunchedEffect(track == null) {
        if (track == null && hudState.isScrubbing) {
            hudController.setScrubbing(false)
        }
    }

    // Leak-free auto-hide timer driven strictly by state keys; suspends while scrubbing or hidden
    LaunchedEffect(
        hudState.isHudVisible,
        hudState.isScrubbing,
        hudState.interactionGeneration,
        hudState.hudAutoHideDelayMs
    ) {
        val timeoutMs = hudState.resolveAutoHideDelayMs() ?: return@LaunchedEffect
        delay(timeoutMs)
        hudController.hideHud()
    }

    // Dynamic Palette-derived accent color for glow, highlights, and lyrics sheen
    var dynamicAccent by remember(track?.id, track?.artworkUri, track?.artworkResId) {
        mutableStateOf(ArtworkColorExtractor.getCachedAccentColor(track) ?: fallbackAccent)
    }
    LaunchedEffect(track?.id, track?.artworkUri, track?.artworkResId) {
        if (track != null) {
            dynamicAccent = ArtworkColorExtractor.extractAccentColor(appContext, track)
        } else {
            dynamicAccent = fallbackAccent
        }
    }
    val animatedAccentColor by animateColorAsState(
        targetValue = dynamicAccent,
        animationSpec = tween(durationMillis = 700),
        label = "ambientDynamicAccent"
    )

    // Subtle OLED burn-in protection pixel shift (bounded within ±6.dp, animated over 3s every 45s)
    var burnInTick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(enableBurnInProtection) {
        if (enableBurnInProtection) {
            while (isActive) {
                delay(DEFAULT_BURN_IN_SHIFT_INTERVAL_MS)
                burnInTick += 1L
            }
        } else {
            burnInTick = 0L
        }
    }
    val (targetShiftXDp, targetShiftYDp) = remember(burnInTick, enableBurnInProtection) {
        if (enableBurnInProtection) computeBurnInShiftDp(burnInTick) else (0f to 0f)
    }
    val animatedShiftXDp by animateFloatAsState(
        targetValue = targetShiftXDp,
        animationSpec = tween(durationMillis = 3_000),
        label = "ambientBurnInX"
    )
    val animatedShiftYDp by animateFloatAsState(
        targetValue = targetShiftYDp,
        animationSpec = tween(durationMillis = 3_000),
        label = "ambientBurnInY"
    )

    // Horizontal swipe track skip state & ephemeral center gesture feedback
    val coroutineScope = rememberCoroutineScope()
    val swipeDragOffset = remember { Animatable(0f) }
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 84.dp.toPx() }
    var gestureFeedback by remember { mutableStateOf<AmbientGestureFeedback?>(null) }

    LaunchedEffect(gestureFeedback?.id) {
        if (gestureFeedback != null) {
            delay(900L)
            gestureFeedback = null
        }
    }

    val latestActions by rememberUpdatedState(actions)
    val latestIsPlaying by rememberUpdatedState(state.playbackState.isPlaying)
    val latestHasTrack by rememberUpdatedState(track != null)

    val activeVisualizer = remember(
        state.selectedVisualizer,
        state.availableVisualizers,
        hudState.isVisualizerEnabled
    ) {
        resolveActiveAmbientVisualizer(
            selectedVisualizer = state.selectedVisualizer,
            availableVisualizers = state.availableVisualizers,
            isVisualizerEnabled = hudState.isVisualizerEnabled
        )
    }

    val audioQualityLabel = remember(track?.id, track?.audioUrl, state.audioQualityLabel) {
        deriveAudioQualityLabel(track, state.audioQualityLabel)
    }
    val outputLabel = remember(state.isCastConnected, state.castDeviceName, state.outputDeviceName) {
        if (state.isCastConnected && !state.castDeviceName.isNullOrBlank()) {
            state.castDeviceName
        } else {
            state.outputDeviceName.ifBlank { "Device Speaker" }
        }
    }

    val onSeekToPositionMs: (Long) -> Unit = remember(state.playbackState.durationMs, actions.onSeekToFraction) {
        { posMs ->
            val duration = state.playbackState.durationMs
            if (duration > 0L) {
                actions.onSeekToFraction((posMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f))
            }
        }
    }

    // Dedicated LayerBackdrop capturing the live Ambient visual stage so all glass HUD controls refract it
    val ambientBackdrop = rememberLayerBackdrop(
        onDraw = {
            drawRect(Color(0xFF0A0C12))
            drawContent()
        }
    )

    CompositionLocalProvider(
        LocalAppBackdrop provides ambientBackdrop,
        LocalAppColors provides DarkThemeColors,
        LocalAccentColor provides animatedAccentColor
    ) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF0A0C12))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { hudController.toggleHud() },
                        onDoubleTap = {
                            if (latestHasTrack) {
                                val willPlay = !latestIsPlaying
                                latestActions.onTogglePlayPause()
                                gestureFeedback = AmbientGestureFeedback(
                                    id = System.nanoTime(),
                                    icon = if (willPlay) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    label = if (willPlay) "Playing" else "Paused"
                                )
                            } else {
                                hudController.toggleHud()
                            }
                        }
                    )
                }
                .pointerInput(swipeThresholdPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val totalDrag = swipeDragOffset.value
                            val swipeAction = resolveHorizontalSwipeAction(totalDrag, swipeThresholdPx)
                            if (latestHasTrack) {
                                when (swipeAction) {
                                    AmbientSwipeDirection.NEXT -> {
                                        latestActions.onSkipNext()
                                        gestureFeedback = AmbientGestureFeedback(
                                            id = System.nanoTime(),
                                            icon = Icons.Default.SkipNext,
                                            label = "Next Track"
                                        )
                                    }
                                    AmbientSwipeDirection.PREVIOUS -> {
                                        latestActions.onSkipPrevious()
                                        gestureFeedback = AmbientGestureFeedback(
                                            id = System.nanoTime(),
                                            icon = Icons.Default.SkipPrevious,
                                            label = "Previous Track"
                                        )
                                    }
                                    AmbientSwipeDirection.NONE -> Unit
                                }
                            }
                            coroutineScope.launch {
                                swipeDragOffset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                swipeDragOffset.animateTo(0f)
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            if (latestHasTrack) {
                                coroutineScope.launch {
                                    val next = (swipeDragOffset.value + dragAmount)
                                        .coerceIn(-swipeThresholdPx * 1.45f, swipeThresholdPx * 1.45f)
                                    swipeDragOffset.snapTo(next)
                                }
                            }
                        }
                    )
                }
        ) {
            val stageHeight = maxHeight
            val isCompactHeight = stageHeight < 460.dp

            // 1. RICH ATMOSPHERIC BACKDROP LAYER (captured for Liquid Glass HUD refraction)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer()
                    .layerBackdrop(ambientBackdrop)
            ) {
                AmbientBackdropStage(
                    track = track,
                    auroraColors = state.auroraColors,
                    accentColor = animatedAccentColor,
                    isPlaying = state.playbackState.isPlaying,
                    currentPositionMs = state.playbackState.currentPositionMs,
                    durationMs = state.playbackState.durationMs,
                    activeVisualizer = activeVisualizer,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 2. FOREGROUND IMMERSIVE HORIZONTAL CONTENT STAGE (with gentle burn-in drift & swipe follow)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .displayCutoutPadding()
                    .graphicsLayer {
                        translationX = animatedShiftXDp.dp.toPx() + (swipeDragOffset.value * 0.16f)
                        translationY = animatedShiftYDp.dp.toPx()
                    }
                    .padding(
                        horizontal = if (isCompactHeight) 24.dp else 44.dp,
                        vertical = if (isCompactHeight) 18.dp else 32.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (track == null) {
                    AmbientStandbyStage(
                        isCompactHeight = isCompactHeight,
                        accentColor = animatedAccentColor
                    )
                } else {
                    val artworkSize = (stageHeight * if (hudState.panelMode == AmbientPanelMode.CINEMA_ART) {
                        if (isCompactHeight) 0.62f else 0.56f
                    } else {
                        if (isCompactHeight) 0.56f else 0.50f
                    }).coerceIn(152.dp, 380.dp)

                    AnimatedContent(
                        targetState = hudState.panelMode,
                        transitionSpec = {
                            fadeIn(tween(300)) togetherWith fadeOut(tween(220))
                        },
                        label = "ambientPanelModeStage"
                    ) { panelMode ->
                        when (panelMode) {
                            AmbientPanelMode.CINEMA_ART -> {
                                AmbientCinemaStage(
                                    track = track,
                                    canvasArtwork = state.canvasArtwork,
                                    isPlaying = state.playbackState.isPlaying,
                                    isCanvasVideoEnabled = hudState.isCanvasVideoEnabled,
                                    accentColor = animatedAccentColor,
                                    artworkSize = artworkSize,
                                    isCompactHeight = isCompactHeight,
                                    isHudVisible = hudState.isHudVisible
                                )
                            }
                            AmbientPanelMode.LYRICS,
                            AmbientPanelMode.CLOCK_METADATA -> {
                                Row(
                                    modifier = Modifier
                                        .widthIn(max = 1280.dp)
                                        .fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(if (isCompactHeight) 24.dp else 44.dp)
                                ) {
                                    // LEFT COLUMN: Large Crisp Rounded Album Art / Motion Canvas
                                    Column(
                                        modifier = Modifier
                                            .weight(0.42f)
                                            .fillMaxHeight(),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        AmbientArtworkShowcase(
                                            track = track,
                                            canvasArtwork = state.canvasArtwork,
                                            isPlaying = state.playbackState.isPlaying,
                                            isCanvasVideoEnabled = hudState.isCanvasVideoEnabled,
                                            accentColor = animatedAccentColor,
                                            artworkSize = artworkSize
                                        )

                                        if (panelMode == AmbientPanelMode.LYRICS && state.lyrics.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(if (isCompactHeight) 10.dp else 18.dp))
                                            AmbientCompactTrackCaption(
                                                track = track,
                                                isCompactHeight = isCompactHeight,
                                                modifier = Modifier.widthIn(max = artworkSize + 36.dp)
                                            )
                                        }
                                    }

                                    // RIGHT COLUMN: Live Synchronized Lyrics OR Editorial Clock & Metadata
                                    Box(
                                        modifier = Modifier
                                            .weight(0.58f)
                                            .fillMaxHeight(),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (panelMode == AmbientPanelMode.LYRICS) {
                                            AmbientLyricsPanel(
                                                lines = state.lyrics,
                                                playbackState = state.playbackState,
                                                track = track,
                                                accentColor = animatedAccentColor,
                                                lyricsProviderName = state.lyricsProviderName,
                                                audioQualityLabel = audioQualityLabel,
                                                outputLabel = outputLabel,
                                                isCastConnected = state.isCastConnected,
                                                isCompactHeight = isCompactHeight,
                                                isHudVisible = hudState.isHudVisible,
                                                onSeekToPosition = { posMs ->
                                                    hudController.refreshTimerIfVisible()
                                                    onSeekToPositionMs(posMs)
                                                },
                                                onPlayQueueIndex = { idx ->
                                                    hudController.refreshTimerIfVisible()
                                                    actions.onPlayQueueIndex(idx)
                                                },
                                                onOpenLyricsSettings = {
                                                    hudController.registerInteraction()
                                                    actions.onOpenLyricsSettings()
                                                },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            AmbientClockAndMetadataPanel(
                                                track = track,
                                                playbackState = state.playbackState,
                                                accentColor = animatedAccentColor,
                                                audioQualityLabel = audioQualityLabel,
                                                outputLabel = outputLabel,
                                                isCastConnected = state.isCastConnected,
                                                isCompactHeight = isCompactHeight,
                                                onPlayQueueIndex = { idx ->
                                                    hudController.refreshTimerIfVisible()
                                                    actions.onPlayQueueIndex(idx)
                                                },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. LIVE SWIPE PREVIEW INDICATOR (while dragging horizontally past 35% threshold)
            val dragPreviewDirection = when {
                swipeDragOffset.value <= -swipeThresholdPx * 0.35f -> AmbientSwipeDirection.NEXT
                swipeDragOffset.value >= swipeThresholdPx * 0.35f -> AmbientSwipeDirection.PREVIOUS
                else -> AmbientSwipeDirection.NONE
            }
            AnimatedVisibility(
                visible = dragPreviewDirection != AmbientSwipeDirection.NONE && track != null,
                enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.92f),
                exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.92f),
                modifier = Modifier.align(Alignment.Center)
            ) {
                val isNext = dragPreviewDirection == AmbientSwipeDirection.NEXT
                AmbientGestureToast(
                    icon = if (isNext) Icons.Default.SkipNext else Icons.Default.SkipPrevious,
                    label = if (isNext) "Release for Next" else "Release for Previous",
                    accentColor = animatedAccentColor
                )
            }

            // 4. EPHEMERAL DOUBLE-TAP / SKIP CONFIRMATION PULSE
            AnimatedVisibility(
                visible = gestureFeedback != null && dragPreviewDirection == AmbientSwipeDirection.NONE,
                enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.88f),
                exit = fadeOut(tween(260)) + scaleOut(targetScale = 0.94f),
                modifier = Modifier.align(Alignment.Center)
            ) {
                gestureFeedback?.let { feedback ->
                    AmbientGestureToast(
                        icon = feedback.icon,
                        label = feedback.label,
                        accentColor = animatedAccentColor
                    )
                }
            }

            // 5. AUTO-HIDING LIQUID GLASS HUD CONTROLS OVERLAY
            AmbientHudOverlay(
                state = state,
                hudState = hudState,
                actions = actions,
                accentColor = animatedAccentColor,
                isCompactHeight = isCompactHeight,
                castButtonContent = castButtonContent,
                onInteraction = { hudController.registerInteraction() },
                onScrubbingChanged = { scrubbing -> hudController.setScrubbing(scrubbing) },
                onSelectPanelMode = { mode -> hudController.setPanelMode(mode) },
                onToggleVisualizer = { hudController.toggleVisualizer() },
                onToggleCanvasVideo = { hudController.toggleCanvasVideo() },
                onToggleRemainingTime = { hudController.toggleRemainingTimeDisplay() },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Pure gallery-focused Cinema layout: centered hero artwork / motion canvas + subtle typography
 * and an unobtrusive corner clock when the HUD is hidden.
 */
@Composable
private fun AmbientCinemaStage(
    track: TrackItem,
    canvasArtwork: CanvasArtworkResult?,
    isPlaying: Boolean,
    isCanvasVideoEnabled: Boolean,
    accentColor: Color,
    artworkSize: Dp,
    isCompactHeight: Boolean,
    isHudVisible: Boolean
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Discreet corner clock when HUD is hidden in Cinema mode
        AnimatedVisibility(
            visible = !isHudVisible,
            enter = fadeIn(tween(380)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 4.dp, end = 4.dp)
        ) {
            AmbientDigitalClock(
                isCompactHeight = true,
                accentColor = accentColor,
                showDate = false,
                compactInline = true
            )
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AmbientArtworkShowcase(
                track = track,
                canvasArtwork = canvasArtwork,
                isPlaying = isPlaying,
                isCanvasVideoEnabled = isCanvasVideoEnabled,
                accentColor = accentColor,
                artworkSize = artworkSize
            )
            Spacer(modifier = Modifier.height(if (isCompactHeight) 12.dp else 20.dp))
            AmbientCompactTrackCaption(
                track = track,
                isCompactHeight = isCompactHeight,
                modifier = Modifier.widthIn(max = 480.dp)
            )
        }
    }
}

@Composable
private fun AmbientStandbyStage(
    isCompactHeight: Boolean,
    accentColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(horizontal = 32.dp)
    ) {
        AmbientDigitalClock(
            isCompactHeight = isCompactHeight,
            accentColor = accentColor
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "AMBIENT MODE",
            fontFamily = GoogleSans,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 2.2.sp,
            color = accentColor.copy(alpha = 0.88f)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Start playing music to illuminate the widescreen stage",
            fontFamily = PlusJakartaSans,
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.65f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AmbientGestureToast(
    icon: ImageVector,
    label: String,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .clip(GestureFeedbackPillShape)
            .background(Color(0xCC11141D))
            .border(1.dp, accentColor.copy(alpha = 0.55f), GestureFeedbackPillShape)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = label,
            fontFamily = GoogleSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = Color.White
        )
    }
}

// =============================================================================
// COMPOSE PREVIEWS (Landscape Phone & Landscape Tablet)
// =============================================================================

private fun previewAmbientState(): AmbientModeUiState {
    val tracks = AdaptivePreviewFixtures.tracks
    return AmbientModeUiState(
        playbackState = PlaybackState(
            currentTrack = tracks.first(),
            isPlaying = true,
            currentPositionMs = 74_000L,
            durationMs = 208_000L,
            repeatMode = PlaybackRepeatMode.RepeatAll,
            isShuffleEnabled = false,
            queue = tracks,
            currentQueueIndex = 0
        ),
        isLiked = true,
        lyrics = AdaptivePreviewFixtures.lyrics,
        lyricsProviderName = "LRCLIB Sync",
        outputDeviceName = "Living Room Speaker",
        audioQualityLabel = "FLAC · Lossless"
    )
}

@Preview(
    name = "Ambient Landscape Phone — Lyrics + HUD",
    widthDp = 844,
    heightDp = 390,
    showBackground = true
)
@Composable
private fun AmbientModePhoneLyricsHudPreview() {
    AdaptivePreviewSurface(widthDp = 844, heightDp = 390, darkTheme = true) {
        AmbientModeScreen(
            state = previewAmbientState(),
            actions = AmbientModeActions(onExit = {}),
            initialPanelMode = AmbientPanelMode.LYRICS,
            initialHudVisible = true,
            enableSystemUiControl = false,
            enableBurnInProtection = false
        )
    }
}

@Preview(
    name = "Ambient Landscape Phone — Clock Mode (Pure Ambient)",
    widthDp = 844,
    heightDp = 390,
    showBackground = true
)
@Composable
private fun AmbientModePhoneClockPurePreview() {
    AdaptivePreviewSurface(widthDp = 844, heightDp = 390, darkTheme = true) {
        AmbientModeScreen(
            state = previewAmbientState(),
            actions = AmbientModeActions(onExit = {}),
            initialPanelMode = AmbientPanelMode.CLOCK_METADATA,
            initialHudVisible = false,
            enableSystemUiControl = false,
            enableBurnInProtection = false
        )
    }
}

@Preview(
    name = "Ambient Landscape Tablet — Lyrics + HUD",
    widthDp = 1280,
    heightDp = 800,
    showBackground = true
)
@Composable
private fun AmbientModeTabletLyricsHudPreview() {
    AdaptivePreviewSurface(widthDp = 1280, heightDp = 800, darkTheme = true) {
        AmbientModeScreen(
            state = previewAmbientState(),
            actions = AmbientModeActions(onExit = {}),
            initialPanelMode = AmbientPanelMode.LYRICS,
            initialHudVisible = true,
            enableSystemUiControl = false,
            enableBurnInProtection = false
        )
    }
}

@Preview(
    name = "Ambient Landscape Tablet — Cinema Art Mode",
    widthDp = 1280,
    heightDp = 800,
    showBackground = true
)
@Composable
private fun AmbientModeTabletCinemaPreview() {
    AdaptivePreviewSurface(widthDp = 1280, heightDp = 800, darkTheme = true) {
        AmbientModeScreen(
            state = previewAmbientState().copy(
                playbackState = previewAmbientState().playbackState.copy(
                    currentTrack = TrackItem(
                        id = "demo_cinema",
                        title = "I Wrote A Song",
                        artist = "Mae Muller",
                        album = "Sorry I'm Late",
                        durationMs = 165_000L,
                        isExplicit = true
                    )
                )
            ),
            actions = AmbientModeActions(onExit = {}),
            initialPanelMode = AmbientPanelMode.CINEMA_ART,
            initialHudVisible = false,
            enableSystemUiControl = false,
            enableBurnInProtection = false
        )
    }
}
