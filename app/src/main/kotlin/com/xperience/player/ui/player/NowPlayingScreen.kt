package com.xperience.player.ui.player

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.media3.ui.AspectRatioFrameLayout
import com.xperience.player.service.ResolvedMusicVideo
import com.xperience.player.ui.home.PlayerMediaMode
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberSupportingPaneScaffoldNavigator
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.xperience.player.R
import com.xperience.player.data.VisualizerRepository
import com.xperience.player.domain.model.PlaybackRepeatMode
import com.xperience.player.domain.model.PlaybackState
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.domain.model.canvas.CanvasArtworkResult
import com.xperience.player.domain.model.lyrics.LyricLine
import com.xperience.player.domain.model.lyrics.LyricsProviderConfig
import com.xperience.player.domain.model.lyrics.LyricsProviderId
import com.xperience.player.domain.model.visualizer.VisualizerDefinition
import com.xperience.player.domain.model.visualizer.VisualizerRenderMode
import com.xperience.player.service.MediaNetworkClients
import com.xperience.player.service.cast.CastPlaybackState
import com.xperience.player.service.dsp.AudioDspController
import com.xperience.player.service.dsp.AudioDspState
import com.xperience.player.service.dsp.SleepTimerManager
import com.xperience.player.service.dsp.SleepTimerState
import com.xperience.player.ui.cast.CastButton
import com.xperience.player.ui.cast.CastButtonStyle
import com.xperience.player.ui.adaptive.AdaptiveModalContainer
import com.xperience.player.ui.adaptive.AdaptivePreviewFixtures
import com.xperience.player.ui.adaptive.AdaptivePreviewSurface
import com.xperience.player.ui.adaptive.LocalAppWindowSizeClass
import com.xperience.player.ui.adaptive.rememberAdaptivePaneScaffoldDirective
import com.xperience.player.ui.adaptive.supportsTwoPaneLayout
import com.xperience.player.ui.component.ExplicitBadge
import com.xperience.player.ui.component.GlassCircleButton
import com.xperience.player.ui.component.GlassComponent
import com.xperience.player.ui.component.LocalAppBackdrop
import com.xperience.player.ui.component.LocalGlassEffectConfig
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.xperience.player.ui.component.backdrop.backdrops.layerBackdrop
import com.xperience.player.ui.component.backdrop.backdrops.rememberBackdropFreeze
import com.xperience.player.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.xperience.player.ui.component.isGlassAllowed
import com.xperience.player.ui.component.liquidGlass
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.lyrics.LyricsProviderSettingsSheetContent
import com.xperience.player.ui.lyrics.SynchronizedLyricsView
import com.xperience.player.ui.theme.AppThemeColors
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.PlusJakartaSans
import com.xperience.player.ui.utils.pressWobble
import com.xperience.player.ui.visualizer.VisualizerSelectorSheetContent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

private val CapsuleShape = ContinuousRoundedRectangle(percent = 50)
private val BottomPillShape = ContinuousRoundedRectangle(percent = 50)
private val CompactCoverShape = ContinuousRoundedRectangle(percent = 24)

private enum class NowPlayingCenterMode {
    ARTWORK,
    LYRICS,
    QUEUE
}

private enum class NowPlayingSupportingTab {
    LYRICS,
    QUEUE
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun NowPlayingScreen(
    playbackState: PlaybackState,
    isLiked: Boolean,
    onClose: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekToFraction: (Float) -> Unit,
    onToggleFavorite: (String) -> Unit,
    @Suppress("UNUSED_PARAMETER") onTrackSelect: (TrackItem) -> Unit = {},
    modifier: Modifier = Modifier,
    onMoveQueueItem: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    onPlayQueueIndex: (Int) -> Unit = {},
    selectedVisualizer: VisualizerDefinition = VisualizerRepository.DefaultPreset,
    availableVisualizers: List<VisualizerDefinition> = emptyList(),
    onSelectVisualizer: (VisualizerDefinition) -> Unit = {},
    onImportVisualizer: (String, String?) -> Unit = { _, _ -> },
    onDeleteVisualizer: (String) -> Unit = {},
    lyrics: List<LyricLine> = emptyList(),
    lyricsProviderName: String? = null,
    lyricsProviders: List<LyricsProviderConfig> = emptyList(),
    canvasArtwork: CanvasArtworkResult? = null,
    musicVideo: ResolvedMusicVideo? = null,
    isOfficialVideoAvailable: Boolean = false,
    isResolvingMusicVideo: Boolean = false,
    playerMediaMode: PlayerMediaMode = PlayerMediaMode.SONG,
    onSelectPlayerMediaMode: (PlayerMediaMode) -> Unit = {},
    onMusicVideoPlaybackError: () -> Unit = {},
    onImportLrc: (String) -> Unit = {},
    onMoveLyricsProviderPriority: (LyricsProviderId, Int) -> Unit = { _, _ -> },
    onSetLyricsProviderEnabled: (LyricsProviderId, Boolean) -> Unit = { _, _ -> },
    onDownloadTrack: (TrackItem) -> Unit = {},
    onAddToPlaylist: (TrackItem) -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    onCycleRepeatMode: () -> Unit = {},
    outputDeviceName: String = "Device Speaker",
    onOpenOutputSwitcher: () -> Unit = {},
    onOpenAmbientMode: () -> Unit = {},
    onSetAsRingtone: (TrackItem) -> Unit = {},
    onPlayNext: (TrackItem) -> Unit = {},
    onAddToQueue: (TrackItem) -> Unit = {},
    onGoToArtist: (String) -> Unit = {},
    onGoToAlbum: (TrackItem) -> Unit = {},
    onOpenSongInfo: (TrackItem) -> Unit = {},
    onDirectDownloadFormat: (TrackItem, com.xperience.player.domain.model.download.DownloadAudioFormat) -> Unit = { _, _ -> },
    castState: CastPlaybackState = CastPlaybackState(),
    castRouteCount: Int = 0,
    onOpenCastPicker: () -> Unit = onOpenOutputSwitcher,
    playbackPositionMsFlow: StateFlow<Long> = remember { MutableStateFlow(playbackState.currentPositionMs) },
    lyricsPlaybackPositionMsFlow: StateFlow<Long> = playbackPositionMsFlow,
    onSeekToLyricPosition: ((Long) -> Unit)? = null
) {
    val track = playbackState.currentTrack ?: return
    val context = LocalContext.current
    val appColors = LocalAppColors.current
    val accentColor = LocalAccentColor.current
    val windowSizeClass = LocalAppWindowSizeClass.current
    val isTwoPane = windowSizeClass.supportsTwoPaneLayout
    val coroutineScope = rememberCoroutineScope()

    // Smooth drag-down to dismiss gesture
    val dragOffsetY = remember { Animatable(0f) }

    // Mutually exclusive inline overlays for single-pane widths (Compact / Medium)
    var isLyricsOverlayActive by rememberSaveable { mutableStateOf(false) }
    var isQueueOverlayActive by rememberSaveable { mutableStateOf(false) }
    var activeSupportingTab by rememberSaveable { mutableStateOf(NowPlayingSupportingTab.LYRICS) }
    val isCenterOverlayActive = isLyricsOverlayActive || isQueueOverlayActive

    // Modal Sheets
    var showLyricsSettingsSheet by remember { mutableStateOf(false) }
    var showDetailsSheet by remember { mutableStateOf(false) }
    var showVisualizerSheet by remember { mutableStateOf(false) }
    var showAudioEffectsSheet by remember { mutableStateOf(false) }
    var audioEffectsInitialTab by remember { mutableStateOf(AudioEffectsSectionTab.ALL) }

    val dspController = remember(context) { AudioDspController.getInstance(context) }
    val sleepTimerManager = remember(context) { SleepTimerManager.getInstance(context) }
    val dspState by dspController.dspState.collectAsState()
    val sleepTimerState by sleepTimerManager.timerState.collectAsState()
    val isVideoAvailable = isOfficialVideoAvailable || (musicVideo != null && musicVideo.streamUrl.isNotBlank())
    val isVideoModeActive = isVideoAvailable && playerMediaMode == PlayerMediaMode.VIDEO

    val onLyricSeekPosition: (Long) -> Unit = { posMs ->
        if (onSeekToLyricPosition != null) {
            onSeekToLyricPosition(posMs)
        } else {
            val total = playbackState.durationMs
            if (total > 0) {
                onSeekToFraction((posMs.toFloat() / total.toFloat()).coerceIn(0f, 1f))
            }
        }
    }

    // Intercept hardware/gesture Back key: collapse inline overlay first on single-pane, then close player.
    BackHandler(onBack = onClose)
    BackHandler(enabled = !isTwoPane && isCenterOverlayActive) {
        isLyricsOverlayActive = false
        isQueueOverlayActive = false
    }

    val lrcPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val content = stream.bufferedReader(StandardCharsets.UTF_8).readText()
                    onImportLrc(content)
                    Toast.makeText(context, "Lyrics imported successfully", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Error reading LRC file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val onImportLrcClick: () -> Unit = {
        try {
            lrcPickerLauncher.launch(arrayOf("*/*"))
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open file picker", Toast.LENGTH_SHORT).show()
        }
    }

    val onSeekToPosition: (Long) -> Unit = { posMs ->
        val total = playbackState.durationMs
        if (total > 0) {
            onSeekToFraction((posMs.toFloat() / total.toFloat()).coerceIn(0f, 1f))
        }
    }

    // Dedicated backdrop for NowPlayingScreen: captures the full-bleed album cover, canvas,
    // gradients, and GPU visualizer so all LiquidGlass controls refract the current track's art.
    val nowPlayingBackdrop = rememberLayerBackdrop(
        onDraw = {
            drawRect(if (appColors.isDark) Color(0xFF0E1015) else Color(0xFFF8FAFC))
            drawContent()
        }
    )

    val backdropFreeze = rememberBackdropFreeze()

    CompositionLocalProvider(
        LocalAppBackdrop provides nowPlayingBackdrop
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .nestedScroll(backdropFreeze.connection)
                .graphicsLayer {
                    translationY = dragOffsetY.value.coerceAtLeast(0f)
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dragAmount ->
                            if (dragAmount > 0 || dragOffsetY.value > 0) {
                                coroutineScope.launch {
                                    dragOffsetY.snapTo((dragOffsetY.value + dragAmount).coerceAtLeast(0f))
                                }
                            }
                        },
                        onDragEnd = {
                            coroutineScope.launch {
                                if (dragOffsetY.value > 260f) {
                                    onClose()
                                } else {
                                    dragOffsetY.animateTo(
                                        0f,
                                        spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                dragOffsetY.animateTo(0f)
                            }
                        }
                    )
                }
        ) {
            // =========================================================================
            // 1. FULL-BLEED ALBUM COVER, CANVAS VIDEO, GRADIENT DISSOLVE & VISUALIZER
            //    Captured into nowPlayingBackdrop so all LiquidGlass controls refract it!
            // =========================================================================
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(
                        nowPlayingBackdrop,
                        frozen = { backdropFreeze.frozen() || dragOffsetY.value > 0.5f }
                    )
            ) {
                NowPlayingBackground(
                    track = track,
                    canvasArtwork = canvasArtwork,
                    isPlaying = playbackState.isPlaying,
                    isCanvasPaused = (!isTwoPane && isLyricsOverlayActive) || isVideoModeActive,
                    selectedVisualizer = selectedVisualizer,
                    positionMsProvider = { playbackPositionMsFlow.value },
                    durationMs = playbackState.durationMs,
                    accentColor = accentColor,
                    isOverlayActive = (!isTwoPane && isCenterOverlayActive) || (!isTwoPane && isVideoModeActive),
                    isAmbientBlurredStage = isTwoPane || isVideoModeActive,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // =========================================================================
            // 2. MAIN CONTENT STACK (Floating glass controls sampling nowPlayingBackdrop)
            // =========================================================================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = if (isTwoPane) 28.dp else 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // TOP BAR: [Back] --- [Borderless Mini Cover + Title/Artist in 1-pane overlay] --- [Cast | Menu]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassCircleButton(
                        onClick = onClose,
                        size = 46.dp
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Collapse player",
                            tint = appColors.glassTextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!isTwoPane && isCenterOverlayActive) {
                            TopBarCompactTrackInfo(
                                track = track,
                                accentColor = accentColor,
                                appColors = appColors,
                                onClick = {
                                    isLyricsOverlayActive = false
                                    isQueueOverlayActive = false
                                }
                            )
                        } else if (isVideoAvailable) {
                            AudioVideoModeSwitcherPill(
                                selectedMode = playerMediaMode,
                                onSelectMode = { mode ->
                                    if (!isTwoPane && isCenterOverlayActive) {
                                        isLyricsOverlayActive = false
                                        isQueueOverlayActive = false
                                    }
                                    onSelectPlayerMediaMode(mode)
                                },
                                accentColor = accentColor,
                                appColors = appColors
                            )
                        }
                    }

                    TopRightCastMenuPill(
                        castState = castState,
                        castRouteCount = castRouteCount,
                        onAmbientClick = onOpenAmbientMode,
                        onCastClick = onOpenCastPicker,
                        onMenuClick = { showDetailsSheet = true },
                        appColors = appColors
                    )
                }

                // Shared swipe-up to open Song Menu & swipe-down to close player gesture modifier
                var bottomSwipeUpDelta by remember { mutableFloatStateOf(0f) }
                var isClosingDownFromBottom by remember { mutableStateOf(false) }
                val swipeUpThresholdPx = with(LocalDensity.current) { 42.dp.toPx() }
                val bottomControlsLiftY by animateFloatAsState(
                    targetValue = (bottomSwipeUpDelta * 0.25f).coerceIn(-24f, 0f),
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "bottomControlsSwipeUpLift"
                )
                val bottomSwipeModifier = Modifier
                    .graphicsLayer {
                        translationY = bottomControlsLiftY
                    }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                isClosingDownFromBottom = false
                                bottomSwipeUpDelta = 0f
                            },
                            onVerticalDrag = { _, dragAmount ->
                                if (dragOffsetY.value > 0f || isClosingDownFromBottom ||
                                    (dragAmount > 0f && bottomSwipeUpDelta >= 0f)
                                ) {
                                    isClosingDownFromBottom = true
                                    bottomSwipeUpDelta = 0f
                                    coroutineScope.launch {
                                        dragOffsetY.snapTo((dragOffsetY.value + dragAmount).coerceAtLeast(0f))
                                    }
                                } else {
                                    bottomSwipeUpDelta = (bottomSwipeUpDelta + dragAmount).coerceAtMost(0f)
                                }
                            },
                            onDragEnd = {
                                if (isClosingDownFromBottom || dragOffsetY.value > 0f) {
                                    coroutineScope.launch {
                                        if (dragOffsetY.value > 260f) {
                                            onClose()
                                        } else {
                                            dragOffsetY.animateTo(
                                                0f,
                                                spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                                            )
                                        }
                                    }
                                } else if (bottomSwipeUpDelta <= -swipeUpThresholdPx) {
                                    showDetailsSheet = true
                                }
                                bottomSwipeUpDelta = 0f
                                isClosingDownFromBottom = false
                            },
                            onDragCancel = {
                                bottomSwipeUpDelta = 0f
                                isClosingDownFromBottom = false
                                if (dragOffsetY.value > 0f) {
                                    coroutineScope.launch {
                                        dragOffsetY.animateTo(0f)
                                    }
                                }
                            }
                        )
                    }

                val onLyricsAction = {
                    if (isTwoPane) {
                        activeSupportingTab = NowPlayingSupportingTab.LYRICS
                    } else {
                        val next = !isLyricsOverlayActive
                        isLyricsOverlayActive = next
                        if (next) {
                            isQueueOverlayActive = false
                            activeSupportingTab = NowPlayingSupportingTab.LYRICS
                        }
                    }
                }
                val onQueueAction = {
                    if (isTwoPane) {
                        activeSupportingTab = NowPlayingSupportingTab.QUEUE
                    } else {
                        val next = !isQueueOverlayActive
                        isQueueOverlayActive = next
                        if (next) {
                            isLyricsOverlayActive = false
                            activeSupportingTab = NowPlayingSupportingTab.QUEUE
                        }
                    }
                }

                if (isTwoPane) {
                    val supportingNavigator = rememberSupportingPaneScaffoldNavigator(
                        scaffoldDirective = rememberAdaptivePaneScaffoldDirective()
                    )

                    SupportingPaneScaffold(
                        directive = supportingNavigator.scaffoldDirective,
                        value = supportingNavigator.scaffoldValue,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        mainPane = {
                            AnimatedPane(
                                modifier = Modifier
                                    .preferredWidth(0.48f)
                                    .fillMaxHeight()
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .widthIn(max = 480.dp)
                                            .fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth()
                                                .padding(vertical = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            NowPlayingArtworkShowcase(
                                                track = track,
                                                canvasArtwork = canvasArtwork,
                                                musicVideo = musicVideo,
                                                isResolvingMusicVideo = isResolvingMusicVideo,
                                                isVideoModeActive = isVideoModeActive,
                                                isPlaying = playbackState.isPlaying,
                                                playbackPositionMsFlow = playbackPositionMsFlow,
                                                playbackSpeed = dspState.effectiveSpeed,
                                                onMusicVideoPlaybackError = onMusicVideoPlaybackError,
                                                accentColor = accentColor,
                                                appColors = appColors
                                            )
                                        }

                                        NowPlayingPrimaryControls(
                                            track = track,
                                            playbackState = playbackState,
                                            playbackPositionMsFlow = playbackPositionMsFlow,
                                            isLiked = isLiked,
                                            isLyricsHighlighted = activeSupportingTab == NowPlayingSupportingTab.LYRICS,
                                            isQueueHighlighted = activeSupportingTab == NowPlayingSupportingTab.QUEUE,
                                            showTitleHeader = true,
                                            accentColor = accentColor,
                                            appColors = appColors,
                                            controlsModifier = bottomSwipeModifier,
                                            onTogglePlayPause = onTogglePlayPause,
                                            onSkipNext = onSkipNext,
                                            onSkipPrevious = onSkipPrevious,
                                            onSeekToFraction = onSeekToFraction,
                                            onToggleShuffle = onToggleShuffle,
                                            onCycleRepeatMode = onCycleRepeatMode,
                                            onToggleFavorite = { onToggleFavorite(track.id) },
                                            onLyricsClick = onLyricsAction,
                                            onQueueClick = onQueueAction,
                                            dspState = dspState,
                                            sleepTimerState = sleepTimerState,
                                            onToggleNightcore = { dspController.toggleNightcore() },
                                            onOpenAudioEffects = { tab ->
                                                audioEffectsInitialTab = tab
                                                showAudioEffectsSheet = true
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        supportingPane = {
                            AnimatedPane(
                                modifier = Modifier
                                    .preferredWidth(0.52f)
                                    .fillMaxHeight()
                            ) {
                                NowPlayingSupportingPanelCard(
                                    activeTab = activeSupportingTab,
                                    onSelectTab = { activeSupportingTab = it },
                                    track = track,
                                    playbackState = playbackState,
                                    playbackPositionMsFlow = lyricsPlaybackPositionMsFlow,
                                    lyrics = lyrics,
                                    lyricsProviderName = lyricsProviderName,
                                    accentColor = accentColor,
                                    appColors = appColors,
                                    onSeekToPosition = onLyricSeekPosition,
                                    onOpenLyricsSettings = { showLyricsSettingsSheet = true },
                                    onImportLrcClick = onImportLrcClick,
                                    onPlayQueueIndex = onPlayQueueIndex,
                                    onMoveQueueItem = onMoveQueueItem,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(bottom = 14.dp)
                                )
                            }
                        }
                    )
                } else {
                    // CENTER AREA: Open Full-Bleed Canvas OR Inline Synchronized Lyrics OR Inline Playing Queue
                    val centerMode = when {
                        isLyricsOverlayActive -> NowPlayingCenterMode.LYRICS
                        isQueueOverlayActive -> NowPlayingCenterMode.QUEUE
                        else -> NowPlayingCenterMode.ARTWORK
                    }

                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(max = 620.dp)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = centerMode,
                            transitionSpec = {
                                if (targetState == NowPlayingCenterMode.ARTWORK) {
                                    fadeIn(tween(220)) togetherWith
                                        (fadeOut(tween(180)) + slideOutVertically(tween(200)) { it / 10 })
                                } else {
                                    (fadeIn(tween(260)) + slideInVertically(
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        ),
                                        initialOffsetY = { it / 8 }
                                    )) togetherWith
                                        (fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it / 12 })
                                }
                            },
                            label = "nowPlayingCenterMode"
                        ) { mode ->
                            when (mode) {
                                NowPlayingCenterMode.ARTWORK -> {
                                    if (isVideoModeActive && musicVideo != null) {
                                        NowPlayingOfficialVideoStage(
                                            musicVideo = musicVideo,
                                            fallbackThumbnailUri = track.artworkUri,
                                            isPlaying = playbackState.isPlaying,
                                            isResolvingMusicVideo = isResolvingMusicVideo,
                                            playbackPositionMsFlow = playbackPositionMsFlow,
                                            playbackSpeed = dspState.effectiveSpeed,
                                            onMusicVideoPlaybackError = onMusicVideoPlaybackError,
                                            accentColor = accentColor,
                                            appColors = appColors,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(vertical = 12.dp)
                                        )
                                    } else if (isVideoModeActive && isResolvingMusicVideo) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(vertical = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                color = accentColor,
                                                strokeWidth = 2.5.dp,
                                                modifier = Modifier.size(34.dp)
                                            )
                                        }
                                    } else {
                                        Box(modifier = Modifier.fillMaxSize())
                                    }
                                }
                                NowPlayingCenterMode.LYRICS -> {
                                    SynchronizedLyricsView(
                                        lines = lyrics,
                                        playbackPositionMsFlow = lyricsPlaybackPositionMsFlow,
                                        providerName = lyricsProviderName,
                                        accentColor = accentColor,
                                        appColors = appColors,
                                        onSeekToPosition = onLyricSeekPosition,
                                        onOpenLyricsSettings = { showLyricsSettingsSheet = true },
                                        onImportLrcClick = onImportLrcClick,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(vertical = 8.dp),
                                        isPlaying = playbackState.isPlaying
                                    )
                                }
                                NowPlayingCenterMode.QUEUE -> {
                                    InlineQueueView(
                                        queue = playbackState.queue,
                                        currentQueueIndex = playbackState.currentQueueIndex,
                                        currentTrack = track,
                                        isPlaying = playbackState.isPlaying,
                                        onPlayQueueIndex = onPlayQueueIndex,
                                        onMoveQueueItem = onMoveQueueItem,
                                        accentColor = accentColor,
                                        appColors = appColors,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }

                    NowPlayingPrimaryControls(
                        track = track,
                        playbackState = playbackState,
                        playbackPositionMsFlow = playbackPositionMsFlow,
                        isLiked = isLiked,
                        isLyricsHighlighted = isLyricsOverlayActive,
                        isQueueHighlighted = isQueueOverlayActive,
                        showTitleHeader = !isCenterOverlayActive,
                        accentColor = accentColor,
                        appColors = appColors,
                        controlsModifier = bottomSwipeModifier,
                        onTogglePlayPause = onTogglePlayPause,
                        onSkipNext = onSkipNext,
                        onSkipPrevious = onSkipPrevious,
                        onSeekToFraction = onSeekToFraction,
                        onToggleShuffle = onToggleShuffle,
                        onCycleRepeatMode = onCycleRepeatMode,
                        onToggleFavorite = { onToggleFavorite(track.id) },
                        onLyricsClick = onLyricsAction,
                        onQueueClick = onQueueAction,
                        dspState = dspState,
                        sleepTimerState = sleepTimerState,
                        onToggleNightcore = { dspController.toggleNightcore() },
                        onOpenAudioEffects = { tab ->
                            audioEffectsInitialTab = tab
                            showAudioEffectsSheet = true
                        },
                        modifier = Modifier
                            .widthIn(max = 520.dp)
                            .fillMaxWidth()
                    )
                }
            }
        }
    }

    // =========================================================================
    // 3. MODAL BOTTOM SHEETS / ADAPTIVE DIALOGS (Lyrics Providers, Song Menu, Visualizer Selector)
    // =========================================================================
    val lyricsSettingsSheetState = rememberModalBottomSheetState()
    AdaptiveModalContainer(
        isOpen = showLyricsSettingsSheet,
        onDismissRequest = { showLyricsSettingsSheet = false },
        sheetState = lyricsSettingsSheetState,
        containerColor = if (appColors.isDark) Color(0xFF141720) else Color(0xFFFFFFFF),
        dialogMaxWidth = 560.dp,
        showDragHandle = false
    ) {
        LyricsProviderSettingsSheetContent(
            providers = lyricsProviders,
            accentColor = accentColor,
            appColors = appColors,
            onMovePriority = onMoveLyricsProviderPriority,
            onToggleEnabled = onSetLyricsProviderEnabled,
            onImportLrcClick = {
                try {
                    lrcPickerLauncher.launch(arrayOf("*/*"))
                } catch (e: Exception) {
                    Toast.makeText(context, "Could not open file picker", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    val detailsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    AdaptiveModalContainer(
        isOpen = showDetailsSheet,
        onDismissRequest = { showDetailsSheet = false },
        sheetState = detailsSheetState,
        containerColor = if (appColors.isDark) Color(0xFF14161D) else Color(0xFFFFFFFF),
        dialogMaxWidth = 500.dp,
        showDragHandle = false
    ) {
        com.xperience.player.ui.components.TrackOptionsMenuContent(
            track = track,
            isLiked = isLiked,
            onDismiss = { showDetailsSheet = false },
            onAddToPlaylist = {
                showDetailsSheet = false
                onAddToPlaylist(track)
            },
            onDownload = {
                showDetailsSheet = false
                onDownloadTrack(track)
            },
            onToggleFavorite = { onToggleFavorite(track.id) },
            onSetAsRingtone = {
                showDetailsSheet = false
                onSetAsRingtone(track)
            },
            onSongInfo = {
                showDetailsSheet = false
                onOpenSongInfo(track)
            },
            onPlayNext = {
                showDetailsSheet = false
                onPlayNext(track)
            },
            onAddToQueue = {
                showDetailsSheet = false
                onAddToQueue(track)
            },
            onGoToArtist = if (track.artist.isNotBlank()) {
                {
                    showDetailsSheet = false
                    onGoToArtist(track.artist)
                }
            } else {
                null
            },
            onGoToAlbum = {
                showDetailsSheet = false
                onGoToAlbum(track)
            },
            onDirectDownloadFormat = { format ->
                showDetailsSheet = false
                onDirectDownloadFormat(track, format)
            },
            isNowPlayingContext = true,
            dspState = dspState,
            sleepTimerState = sleepTimerState,
            selectedVisualizerName = selectedVisualizer.name,
            isVisualizerActive = selectedVisualizer.renderMode != VisualizerRenderMode.None,
            onToggleNightcore = { dspController.toggleNightcore() },
            onOpenAudioEffectsTab = { tab ->
                showDetailsSheet = false
                audioEffectsInitialTab = tab
                showAudioEffectsSheet = true
            },
            onOpenAmbientMode = {
                showDetailsSheet = false
                onOpenAmbientMode()
            },
            onOpenVisualizer = {
                showDetailsSheet = false
                showVisualizerSheet = true
            }
        )
    }

    val audioEffectsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    AdaptiveModalContainer(
        isOpen = showAudioEffectsSheet,
        onDismissRequest = { showAudioEffectsSheet = false },
        sheetState = audioEffectsSheetState,
        containerColor = if (appColors.isDark) Color(0xFF141720) else Color(0xFFFFFFFF),
        dialogMaxWidth = 580.dp,
        showDragHandle = false
    ) {
        AudioEffectsSheetContent(
            dspState = dspState,
            sleepTimerState = sleepTimerState,
            dspController = dspController,
            sleepTimerManager = sleepTimerManager,
            initialTab = audioEffectsInitialTab,
            accentColor = accentColor,
            appColors = appColors
        )
    }

    val visualizerSheetState = rememberModalBottomSheetState()
    AdaptiveModalContainer(
        isOpen = showVisualizerSheet,
        onDismissRequest = { showVisualizerSheet = false },
        sheetState = visualizerSheetState,
        containerColor = if (appColors.isDark) Color(0xFF141720) else Color(0xFFFFFFFF),
        dialogMaxWidth = 580.dp,
        showDragHandle = false
    ) {
        VisualizerSelectorSheetContent(
            availableVisualizers = availableVisualizers,
            selectedVisualizer = selectedVisualizer,
            accentColor = accentColor,
            appColors = appColors,
            onSelectVisualizer = { viz ->
                onSelectVisualizer(viz)
            },
            onImportVisualizer = onImportVisualizer,
            onDeleteVisualizer = onDeleteVisualizer
        )
    }
}

/**
 * Crisp framed square artwork / looping Canvas card displayed in the primary pane
 * when Now Playing expands into its 2-pane tablet/desktop SupportingPaneScaffold layout.
 */
@Composable
private fun NowPlayingArtworkShowcase(
    track: TrackItem,
    canvasArtwork: CanvasArtworkResult?,
    isPlaying: Boolean,
    accentColor: Color,
    appColors: AppThemeColors,
    modifier: Modifier = Modifier,
    musicVideo: ResolvedMusicVideo? = null,
    isResolvingMusicVideo: Boolean = false,
    isVideoModeActive: Boolean = false,
    playbackPositionMsFlow: StateFlow<Long> = remember { MutableStateFlow(0L) },
    playbackSpeed: Float = 1.0f,
    onMusicVideoPlaybackError: () -> Unit = {}
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val sharedImageLoader = remember(appContext) {
        MediaNetworkClients.getImageLoader(appContext)
    }
    val showcaseShape = remember { ContinuousRoundedRectangle(size = 26.dp) }
    val activeVideoUri = canvasArtwork?.videoUri?.takeIf { it.isNotBlank() }
    val targetRatio = if (isVideoModeActive && musicVideo != null) 16f / 9f else 1f

    Box(
        modifier = modifier
            .widthIn(max = if (isVideoModeActive) 460.dp else 360.dp)
            .heightIn(max = 360.dp)
            .aspectRatio(targetRatio)
            .shadow(
                elevation = 28.dp,
                shape = showcaseShape,
                ambientColor = accentColor.copy(alpha = 0.28f),
                spotColor = Color.Black.copy(alpha = 0.45f)
            )
            .clip(showcaseShape)
            .background(if (appColors.isDark) Color(0xFF191D26) else Color(0xFFE6ECF5))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (appColors.isDark) 0.28f else 0.85f),
                        Color.White.copy(alpha = if (appColors.isDark) 0.08f else 0.25f)
                    )
                ),
                shape = showcaseShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isVideoModeActive && musicVideo != null) {
            OfficialMusicVideoPlayerSurface(
                musicVideo = musicVideo,
                fallbackThumbnailUri = track.artworkUri,
                isPlaying = isPlaying,
                isResolvingMusicVideo = isResolvingMusicVideo,
                playbackPositionMsFlow = playbackPositionMsFlow,
                playbackSpeed = playbackSpeed,
                accentColor = accentColor,
                onPlaybackExhausted = onMusicVideoPlaybackError,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            if (track.artworkResId != null) {
                Image(
                    painter = painterResource(id = track.artworkResId),
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (!track.artworkUri.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(appContext)
                        .data(track.artworkUri)
                        .crossfade(300)
                        .build(),
                    imageLoader = sharedImageLoader,
                    contentDescription = track.album.ifBlank { track.title },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(64.dp)
                )
            }

            if (activeVideoUri != null) {
                CanvasArtworkPlayer(
                    videoUri = activeVideoUri,
                    isPlaying = isPlaying,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * YouTube Music-style "Song | Video" LiquidGlass switcher pill displayed in the Now Playing
 * & Tablet Fullscreen header when an official video counterpart is available.
 */
@Composable
private fun AudioVideoModeSwitcherPill(
    selectedMode: PlayerMediaMode,
    onSelectMode: (PlayerMediaMode) -> Unit,
    accentColor: Color,
    appColors: AppThemeColors,
    modifier: Modifier = Modifier
) {
    val glassConfig = LocalGlassEffectConfig.current
    val useGlass = glassConfig.isEnabledFor(GlassComponent.NAV_BAR) && isGlassAllowed()
    val backgroundModifier = if (useGlass) {
        Modifier.liquidGlass(
            config = glassConfig,
            shape = CapsuleShape,
            highlightAlpha = 0.34f
        )
    } else {
        Modifier.background(if (appColors.isDark) Color(0x66161922) else Color(0xCCFFFFFF))
    }

    Row(
        modifier = modifier
            .height(38.dp)
            .clip(CapsuleShape)
            .then(backgroundModifier)
            .border(
                0.8.dp,
                Color.White.copy(alpha = if (appColors.isDark) 0.22f else 0.65f),
                CapsuleShape
            )
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        PlayerMediaMode.entries.forEach { mode ->
            val isSelected = selectedMode == mode
            val interactionSource = remember { MutableInteractionSource() }
            val icon = if (mode == PlayerMediaMode.SONG) Icons.Default.MusicNote else Icons.Default.OndemandVideo
            Row(
                modifier = Modifier
                    .pressWobble(interactionSource, pressedScale = 0.94f)
                    .height(32.dp)
                    .clip(CapsuleShape)
                    .background(
                        if (isSelected) {
                            accentColor.copy(alpha = if (appColors.isDark) 0.30f else 0.20f)
                        } else {
                            Color.Transparent
                        }
                    )
                    .then(
                        if (isSelected) {
                            Modifier.border(0.8.dp, accentColor.copy(alpha = 0.65f), CapsuleShape)
                        } else {
                            Modifier
                        }
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onSelectMode(mode) }
                    )
                    .padding(horizontal = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = mode.label,
                    tint = if (isSelected) accentColor else appColors.glassTextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = mode.label,
                    fontFamily = GoogleSans,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    color = if (isSelected) accentColor else appColors.glassTextPrimary
                )
            }
        }
    }
}

/**
 * Framed 16:9 Official Music Video player stage for 1-pane NowPlayingScreen when "Video" mode is selected.
 */
@Composable
private fun NowPlayingOfficialVideoStage(
    musicVideo: ResolvedMusicVideo,
    fallbackThumbnailUri: String?,
    isPlaying: Boolean,
    isResolvingMusicVideo: Boolean,
    playbackPositionMsFlow: StateFlow<Long>,
    playbackSpeed: Float,
    onMusicVideoPlaybackError: () -> Unit,
    accentColor: Color,
    appColors: AppThemeColors,
    modifier: Modifier = Modifier
) {
    val stageShape = remember { ContinuousRoundedRectangle(size = 24.dp) }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .shadow(
                    elevation = 24.dp,
                    shape = stageShape,
                    ambientColor = accentColor.copy(alpha = 0.30f),
                    spotColor = Color.Black.copy(alpha = 0.55f)
                )
                .clip(stageShape)
                .background(Color.Black)
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            accentColor.copy(alpha = 0.55f),
                            Color.White.copy(alpha = if (appColors.isDark) 0.16f else 0.45f)
                        )
                    ),
                    shape = stageShape
                ),
            contentAlignment = Alignment.Center
        ) {
            OfficialMusicVideoPlayerSurface(
                musicVideo = musicVideo,
                fallbackThumbnailUri = fallbackThumbnailUri,
                isPlaying = isPlaying,
                isResolvingMusicVideo = isResolvingMusicVideo,
                playbackPositionMsFlow = playbackPositionMsFlow,
                playbackSpeed = playbackSpeed,
                accentColor = accentColor,
                onPlaybackExhausted = onMusicVideoPlaybackError,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Shared resilient official music video player surface used by both Phone 1-pane and Tablet 2-pane
 * Now Playing screens. Automatically steps through verified fallback stream URLs (HLS / H.264 / muxed),
 * attaches the client-specific YouTube HTTP headers, bypasses the Apple Music disk cache, and notifies
 * the ViewModel to cache-bust and re-resolve if all stream candidates fail.
 */
@Composable
private fun OfficialMusicVideoPlayerSurface(
    musicVideo: ResolvedMusicVideo,
    fallbackThumbnailUri: String?,
    isPlaying: Boolean,
    isResolvingMusicVideo: Boolean,
    playbackPositionMsFlow: StateFlow<Long>,
    playbackSpeed: Float,
    accentColor: Color,
    onPlaybackExhausted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentPositionMs by playbackPositionMsFlow.collectAsState()
    val context = LocalContext.current
    val appContext = context.applicationContext
    val sharedImageLoader = remember(appContext) {
        MediaNetworkClients.getImageLoader(appContext)
    }
    val streamCandidates = remember(
        musicVideo.videoId,
        musicVideo.streamUrl,
        musicVideo.fallbackStreamUrls
    ) {
        (listOf(musicVideo.streamUrl) + musicVideo.fallbackStreamUrls)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }
    var candidateIndex by remember(streamCandidates) { mutableIntStateOf(0) }
    var isVideoReady by remember(streamCandidates, candidateIndex) { mutableStateOf(false) }
    val activeStreamUrl = streamCandidates.getOrNull(candidateIndex).orEmpty()

    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(appContext)
                .data(musicVideo.thumbnailUrl.ifBlank { fallbackThumbnailUri })
                .crossfade(220)
                .build(),
            imageLoader = sharedImageLoader,
            contentDescription = musicVideo.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        if (activeStreamUrl.isNotBlank()) {
            CanvasArtworkPlayer(
                videoUri = activeStreamUrl,
                isPlaying = isPlaying,
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
                syncPositionMs = currentPositionMs,
                playbackSpeed = playbackSpeed,
                httpHeaders = musicVideo.httpHeaders,
                useDiskCache = false,
                repeatVideo = false,
                constrainTrackQuality = false,
                tightAudioSync = true,
                onReady = { isVideoReady = true },
                onError = {
                    isVideoReady = false
                    if (candidateIndex + 1 < streamCandidates.size) {
                        candidateIndex += 1
                    } else {
                        onPlaybackExhausted()
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        AnimatedVisibility(
            visible = !isVideoReady || isResolvingMusicVideo,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(240))
        ) {
            Row(
                modifier = Modifier
                    .clip(CapsuleShape)
                    .background(Color.Black.copy(alpha = 0.62f))
                    .border(0.8.dp, Color.White.copy(alpha = 0.22f), CapsuleShape)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    color = accentColor,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Loading video...",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Shared primary player stack: optional title/artist header, squiggly progress slider,
 * transport buttons, and bottom floating quick actions pill.
 */
@Composable
private fun NowPlayingPrimaryControls(
    track: TrackItem,
    playbackState: PlaybackState,
    playbackPositionMsFlow: StateFlow<Long>,
    isLiked: Boolean,
    isLyricsHighlighted: Boolean,
    isQueueHighlighted: Boolean,
    showTitleHeader: Boolean,
    accentColor: Color,
    appColors: AppThemeColors,
    controlsModifier: Modifier = Modifier,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekToFraction: (Float) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleFavorite: () -> Unit,
    onLyricsClick: () -> Unit,
    onQueueClick: () -> Unit,
    dspState: AudioDspState = AudioDspState(),
    sleepTimerState: SleepTimerState = SleepTimerState(),
    onToggleNightcore: () -> Unit = {},
    onOpenAudioEffects: (AudioEffectsSectionTab) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val glassConfig = LocalGlassEffectConfig.current

    Column(
        modifier = modifier.then(controlsModifier),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // SONG TITLE & ARTIST / ALBUM (Hidden with smooth collapse when 1-pane Lyrics or Queue overlay is active)
        AnimatedVisibility(
            visible = showTitleHeader,
            enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
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
                        fontSize = 24.sp,
                        color = appColors.textPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (track.isExplicit) {
                        Spacer(modifier = Modifier.width(8.dp))
                        ExplicitBadge()
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                val displaySubtitle = if (hasValidAlbumName(track)) {
                    "${track.artist} - ${track.album}"
                } else {
                    track.artist
                }

                Text(
                    text = displaySubtitle,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.5.sp,
                    color = appColors.textSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))
            }
        }

        // 1-CLICK NIGHTCORE PILL BUTTON + VISUAL SLEEP TIMER COUNTDOWN BADGE + DSP/EQ QUICK ACCESS
        NowPlayingDspQuickBar(
            dspState = dspState,
            sleepTimerState = sleepTimerState,
            onToggleNightcore = onToggleNightcore,
            onOpenAudioEffects = onOpenAudioEffects,
            accentColor = accentColor,
            appColors = appColors,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        )

        // NATIVE ANDROID 13+ SQUIGGLY PROGRESS BAR INSIDE TALLER LIQUIDGLASS CAPSULE
        SquigglyProgressBar(
            playbackPositionMsFlow = playbackPositionMsFlow,
            durationMs = playbackState.durationMs,
            isPlaying = playbackState.isPlaying,
            accentColor = accentColor,
            appColors = appColors,
            onSeek = onSeekToFraction
        )

        Spacer(modifier = Modifier.height(20.dp))

        // PLAYBACK CONTROLS (Shuffle, Previous, Morphing Play/Pause, Next, Repeat)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val shuffleSource = remember { MutableInteractionSource() }
            IconButton(
                onClick = onToggleShuffle,
                interactionSource = shuffleSource,
                modifier = Modifier
                    .pressWobble(shuffleSource)
                    .size(46.dp)
            ) {
                Icon(
                    painter = painterResource(
                        if (playbackState.isShuffleEnabled) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle_off
                    ),
                    contentDescription = "Shuffle",
                    tint = if (playbackState.isShuffleEnabled) accentColor else appColors.glassTextPrimary.copy(alpha = 0.55f),
                    modifier = Modifier.size(23.dp)
                )
            }

            val prevSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .pressWobble(prevSource)
                    .size(56.dp)
                    .shadow(
                        elevation = 6.dp,
                        shape = CircleShape,
                        ambientColor = Color.Black.copy(alpha = 0.25f),
                        spotColor = Color.Black.copy(alpha = 0.35f)
                    )
                    .clip(CircleShape)
                    .background(if (appColors.isDark) Color(0x38FFFFFF) else Color(0x60FFFFFF))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = if (appColors.isDark) 0.35f else 0.70f),
                        shape = CircleShape
                    )
                    .liquidGlass(
                        config = glassConfig,
                        shape = CircleShape,
                        highlightAlpha = 0.40f
                    )
                    .clickable(
                        interactionSource = prevSource,
                        indication = null,
                        onClick = onSkipPrevious
                    ),
                contentAlignment = Alignment.Center
            ) {
                StrokedSkipPreviousIcon(
                    tint = appColors.glassTextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            MorphingPlayPauseButton(
                isPlaying = playbackState.isPlaying,
                onClick = onTogglePlayPause,
                buttonSize = 74.dp,
                iconSize = 32.dp,
                tint = appColors.glassTextPrimary
            )

            val nextSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .pressWobble(nextSource)
                    .size(56.dp)
                    .shadow(
                        elevation = 6.dp,
                        shape = CircleShape,
                        ambientColor = Color.Black.copy(alpha = 0.25f),
                        spotColor = Color.Black.copy(alpha = 0.35f)
                    )
                    .clip(CircleShape)
                    .background(if (appColors.isDark) Color(0x38FFFFFF) else Color(0x60FFFFFF))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = if (appColors.isDark) 0.35f else 0.70f),
                        shape = CircleShape
                    )
                    .liquidGlass(
                        config = glassConfig,
                        shape = CircleShape,
                        highlightAlpha = 0.40f
                    )
                    .clickable(
                        interactionSource = nextSource,
                        indication = null,
                        onClick = onSkipNext
                    ),
                contentAlignment = Alignment.Center
            ) {
                StrokedSkipNextIcon(
                    tint = appColors.glassTextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            val repeatSource = remember { MutableInteractionSource() }
            val isRepeatActive = playbackState.repeatMode != PlaybackRepeatMode.Off
            IconButton(
                onClick = onCycleRepeatMode,
                interactionSource = repeatSource,
                modifier = Modifier
                    .pressWobble(repeatSource)
                    .size(46.dp)
            ) {
                val repeatIconRes = when (playbackState.repeatMode) {
                    PlaybackRepeatMode.RepeatOne -> R.drawable.ic_repeat_one
                    PlaybackRepeatMode.RepeatAll -> R.drawable.ic_repeat_all
                    PlaybackRepeatMode.Off -> R.drawable.ic_repeat_off
                }
                Icon(
                    painter = painterResource(repeatIconRes),
                    contentDescription = "Repeat",
                    tint = if (isRepeatActive) accentColor else appColors.glassTextPrimary.copy(alpha = 0.55f),
                    modifier = Modifier.size(23.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        BottomFloatingActionPill(
            isLiked = isLiked,
            isLyricsActive = isLyricsHighlighted,
            isQueueActive = isQueueHighlighted,
            onToggleLike = onToggleFavorite,
            onLyricsClick = onLyricsClick,
            onQueueClick = onQueueClick
        )

        Spacer(modifier = Modifier.height(14.dp))
    }
}

/**
 * Right-hand supporting panel card hosting Synchronized Lyrics or Playing Queue side-by-side
 * on Expanded screens.
 */
@Composable
private fun NowPlayingSupportingPanelCard(
    activeTab: NowPlayingSupportingTab,
    onSelectTab: (NowPlayingSupportingTab) -> Unit,
    track: TrackItem,
    playbackState: PlaybackState,
    playbackPositionMsFlow: StateFlow<Long>,
    lyrics: List<LyricLine>,
    lyricsProviderName: String?,
    accentColor: Color,
    appColors: AppThemeColors,
    onSeekToPosition: (Long) -> Unit,
    onOpenLyricsSettings: () -> Unit,
    onImportLrcClick: () -> Unit,
    onPlayQueueIndex: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val panelShape = remember { ContinuousRoundedRectangle(size = 26.dp) }
    val rimBrush = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (appColors.isDark) 0.22f else 0.78f),
            Color.White.copy(alpha = if (appColors.isDark) 0.06f else 0.22f)
        )
    )

    Column(
        modifier = modifier
            .clip(panelShape)
            .background(if (appColors.isDark) Color(0x5212151E) else Color(0xAAFFFFFF))
            .border(width = 1.dp, brush = rimBrush, shape = panelShape)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Segmented Lyrics / Queue header switcher
        Row(
            modifier = Modifier
                .clip(CapsuleShape)
                .background(if (appColors.isDark) Color(0x28FFFFFF) else Color(0x12000000))
                .border(0.8.dp, Color.White.copy(alpha = if (appColors.isDark) 0.16f else 0.55f), CapsuleShape)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                Triple(NowPlayingSupportingTab.LYRICS, Icons.Default.Lyrics, "Lyrics"),
                Triple(NowPlayingSupportingTab.QUEUE, Icons.AutoMirrored.Filled.QueueMusic, "Queue")
            ).forEach { (tab, icon, label) ->
                val isSelected = activeTab == tab
                val tabInteraction = remember { MutableInteractionSource() }
                Row(
                    modifier = Modifier
                        .pressWobble(tabInteraction, pressedScale = 0.94f)
                        .clip(CapsuleShape)
                        .background(
                            if (isSelected) {
                                accentColor.copy(alpha = if (appColors.isDark) 0.24f else 0.16f)
                            } else {
                                Color.Transparent
                            }
                        )
                        .clickable(
                            interactionSource = tabInteraction,
                            indication = null,
                            onClick = { onSelectTab(tab) }
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) accentColor else appColors.glassTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = label,
                        fontFamily = GoogleSans,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp,
                        color = if (isSelected) accentColor else appColors.glassTextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AnimatedContent(
                targetState = activeTab,
                transitionSpec = {
                    fadeIn(tween(200)) togetherWith fadeOut(tween(160))
                },
                label = "nowPlayingSupportingTab"
            ) { tab ->
                when (tab) {
                    NowPlayingSupportingTab.LYRICS -> {
                        SynchronizedLyricsView(
                            lines = lyrics,
                            playbackPositionMsFlow = playbackPositionMsFlow,
                            providerName = lyricsProviderName,
                            accentColor = accentColor,
                            appColors = appColors,
                            onSeekToPosition = onSeekToPosition,
                            onOpenLyricsSettings = onOpenLyricsSettings,
                            onImportLrcClick = onImportLrcClick,
                            modifier = Modifier.fillMaxSize(),
                            isPlaying = playbackState.isPlaying
                        )
                    }
                    NowPlayingSupportingTab.QUEUE -> {
                        InlineQueueView(
                            queue = playbackState.queue,
                            currentQueueIndex = playbackState.currentQueueIndex,
                            currentTrack = track,
                            isPlaying = playbackState.isPlaying,
                            onPlayQueueIndex = onPlayQueueIndex,
                            onMoveQueueItem = onMoveQueueItem,
                            accentColor = accentColor,
                            appColors = appColors,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact borderless mini album cover + Song Title + Artist/Album shown in the middle
 * of the top bar whenever Lyrics or Queue inline overlay is active.
 */
@Composable
private fun TopBarCompactTrackInfo(
    track: TrackItem,
    accentColor: Color,
    appColors: AppThemeColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val sharedImageLoader = remember(appContext) {
        MediaNetworkClients.getImageLoader(appContext)
    }
    val interactionSource = remember { MutableInteractionSource() }

    val compactSubtitle = remember(track.id, track.artist, track.album, track.title) {
        if (hasValidAlbumName(track)) {
            "${track.artist} • ${track.album}"
        } else {
            track.artist
        }
    }

    Row(
        modifier = modifier
            .pressWobble(interactionSource, pressedScale = 0.96f)
            .clip(ContinuousRoundedRectangle(percent = 28))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Borderless mini album cover
        Box(
            modifier = Modifier
                .size(38.dp)
                .shadow(
                    elevation = 6.dp,
                    shape = CompactCoverShape,
                    ambientColor = Color.Black.copy(alpha = 0.35f),
                    spotColor = Color.Black.copy(alpha = 0.45f)
                )
                .clip(CompactCoverShape)
                .background(if (appColors.isDark) Color(0xFF1E222B) else Color(0xFFE2E8F0)),
            contentAlignment = Alignment.Center
        ) {
            if (track.artworkResId != null) {
                Image(
                    painter = painterResource(id = track.artworkResId),
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (!track.artworkUri.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(appContext)
                        .data(track.artworkUri)
                        .crossfade(250)
                        .build(),
                    imageLoader = sharedImageLoader,
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f, fill = false)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = track.title,
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = appColors.glassTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (track.isExplicit) {
                    ExplicitBadge(
                        tint = appColors.glassTextPrimary,
                        compact = true
                    )
                }
            }
            Text(
                text = compactSubtitle,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                color = appColors.glassTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Unified top-right LiquidGlass pill housing [Ambient Mode | Cast (Output Switcher) | Menu (MoreHoriz)].
 */
@Composable
private fun TopRightCastMenuPill(
    castState: CastPlaybackState,
    castRouteCount: Int,
    onAmbientClick: () -> Unit,
    onCastClick: () -> Unit,
    onMenuClick: () -> Unit,
    appColors: AppThemeColors,
    modifier: Modifier = Modifier
) {
    val glassConfig = LocalGlassEffectConfig.current
    val useGlass = glassConfig.isEnabledFor(GlassComponent.NAV_BAR) && isGlassAllowed()

    val backgroundModifier = if (useGlass) {
        Modifier.liquidGlass(
            config = glassConfig,
            shape = CapsuleShape,
            highlightAlpha = 0.34f
        )
    } else {
        Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
    }

    val ambientInteraction = remember { MutableInteractionSource() }
    val menuInteraction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .height(46.dp)
            .clip(CapsuleShape)
            .then(backgroundModifier),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ambient Mode (Full-screen horizontal cinema player)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .pressWobble(ambientInteraction)
                    .size(width = 38.dp, height = 42.dp)
                    .clip(CapsuleShape)
                    .clickable(
                        interactionSource = ambientInteraction,
                        indication = ripple(bounded = false, radius = 20.dp),
                        role = Role.Button,
                        onClick = onAmbientClick
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = androidx.compose.ui.res.stringResource(R.string.ambient_mode),
                    tint = appColors.glassTextPrimary,
                    modifier = Modifier.size(21.dp)
                )
            }

            // Subtle glass divider between Ambient and Cast
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(18.dp)
                    .background(appColors.glassTextPrimary.copy(alpha = 0.16f))
            )

            // Chromecast / Audio Output Switcher (LEFT of Menu)
            CastButton(
                castState = castState,
                onClick = onCastClick,
                availableRouteCount = castRouteCount,
                style = CastButtonStyle.ICON,
                iconSize = 20.dp,
                appColors = appColors,
                modifier = Modifier.size(width = 40.dp, height = 42.dp)
            )

            // Subtle glass divider between Cast and Menu
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(18.dp)
                    .background(appColors.glassTextPrimary.copy(alpha = 0.16f))
            )

            // Song Menu (MoreHoriz)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .pressWobble(menuInteraction)
                    .size(width = 40.dp, height = 42.dp)
                    .clip(CapsuleShape)
                    .clickable(
                        interactionSource = menuInteraction,
                        indication = ripple(bounded = false, radius = 20.dp),
                        role = Role.Button,
                        onClick = onMenuClick
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = "Song options",
                    tint = appColors.glassTextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Compact, taller Bottom Floating LiquidGlass Action Pill containing ONLY 3 actions:
 * Favorite (Like), Lyrics, and Queue.
 */
@Composable
private fun BottomFloatingActionPill(
    isLiked: Boolean,
    isLyricsActive: Boolean,
    isQueueActive: Boolean,
    onToggleLike: () -> Unit,
    onLyricsClick: () -> Unit,
    onQueueClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glassConfig = LocalGlassEffectConfig.current
    val useGlass = glassConfig.isEnabledFor(GlassComponent.PLAYER) && isGlassAllowed()
    val accentColor = LocalAccentColor.current
    val appColors = LocalAppColors.current

    val heartScale by animateFloatAsState(
        targetValue = if (isLiked) 1.2f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "heartScale"
    )

    val lyricsScale by animateFloatAsState(
        targetValue = if (isLyricsActive) 1.16f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "lyricsScale"
    )

    val queueScale by animateFloatAsState(
        targetValue = if (isQueueActive) 1.16f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "queueScale"
    )

    val backgroundModifier = if (useGlass) {
        Modifier.liquidGlass(
            config = glassConfig,
            shape = BottomPillShape,
            highlightAlpha = 0.42f
        )
    } else {
        Modifier
            .background(if (appColors.isDark) Color(0x8816181F) else Color(0xCCFFFFFF))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = if (appColors.isDark) 0.25f else 0.70f),
                shape = BottomPillShape
            )
    }

    Box(
        modifier = modifier
            .width(224.dp)
            .height(64.dp)
            .clip(BottomPillShape)
            .then(backgroundModifier)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Favorite / Like
            val likeSource = remember { MutableInteractionSource() }
            IconButton(
                onClick = onToggleLike,
                interactionSource = likeSource,
                modifier = Modifier
                    .pressWobble(likeSource)
                    .graphicsLayer {
                        scaleX = heartScale
                        scaleY = heartScale
                    }
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (isLiked) Color(0xFFFF3366) else appColors.glassTextPrimary,
                    modifier = Modifier.size(25.dp)
                )
            }

            // 2. Lyrics
            val lyricsSource = remember { MutableInteractionSource() }
            IconButton(
                onClick = onLyricsClick,
                interactionSource = lyricsSource,
                modifier = Modifier
                    .pressWobble(lyricsSource)
                    .graphicsLayer {
                        scaleX = lyricsScale
                        scaleY = lyricsScale
                    }
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isLyricsActive) accentColor.copy(alpha = 0.16f) else Color.Transparent)
            ) {
                Icon(
                    imageVector = Icons.Default.Lyrics,
                    contentDescription = "Lyrics",
                    tint = if (isLyricsActive) accentColor else appColors.glassTextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            // 3. Queue
            val queueSource = remember { MutableInteractionSource() }
            IconButton(
                onClick = onQueueClick,
                interactionSource = queueSource,
                modifier = Modifier
                    .pressWobble(queueSource)
                    .graphicsLayer {
                        scaleX = queueScale
                        scaleY = queueScale
                    }
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isQueueActive) accentColor.copy(alpha = 0.16f) else Color.Transparent)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = "Queue",
                    tint = if (isQueueActive) accentColor else appColors.glassTextPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

internal fun hasValidAlbumName(track: TrackItem): Boolean {
    return track.album.isNotBlank() &&
        !track.album.equals("Unknown Album", ignoreCase = true) &&
        !track.album.equals("YouTube Music", ignoreCase = true) &&
        !track.album.equals(track.title, ignoreCase = true)
}

@Composable
private fun NowPlayingPreviewContent(widthDp: Int, heightDp: Int) {
    AdaptivePreviewSurface(widthDp = widthDp, heightDp = heightDp) {
        NowPlayingScreen(
            playbackState = AdaptivePreviewFixtures.playbackState,
            isLiked = true,
            onClose = {},
            onTogglePlayPause = {},
            onSkipNext = {},
            onSkipPrevious = {},
            onSeekToFraction = {},
            onToggleFavorite = {},
            lyrics = AdaptivePreviewFixtures.lyrics,
            lyricsProviderName = "Xperience Sync"
        )
    }
}

@Preview(name = "Phone (360x800)", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
private fun NowPlayingPhonePreview() = NowPlayingPreviewContent(360, 800)

@Preview(name = "Tablet Portrait (800x1280)", widthDp = 800, heightDp = 1280, showBackground = true)
@Composable
private fun NowPlayingTabletPortraitPreview() = NowPlayingPreviewContent(800, 1280)

@Preview(name = "Tablet Landscape (1280x800)", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
private fun NowPlayingTabletLandscapePreview() = NowPlayingPreviewContent(1280, 800)
