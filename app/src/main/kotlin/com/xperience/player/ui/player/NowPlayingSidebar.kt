package com.xperience.player.ui.player

import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.xperience.player.BuildConfig
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
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
import com.xperience.player.service.dsp.SleepTimerManager
import com.xperience.player.ui.cast.CastButton
import com.xperience.player.ui.cast.CastButtonStyle
import com.xperience.player.ui.adaptive.AdaptiveModalContainer
import com.xperience.player.ui.adaptive.AdaptivePreviewFixtures
import com.xperience.player.ui.adaptive.AdaptivePreviewSurface
import com.xperience.player.ui.component.ExplicitBadge
import com.xperience.player.ui.component.GlassCircleButton
import com.xperience.player.ui.component.GlassComponent
import com.xperience.player.ui.component.LiquidGlassSegmentedPill
import com.xperience.player.ui.component.LocalAppBackdrop
import com.xperience.player.ui.component.LocalGlassEffectConfig
import com.xperience.player.ui.component.backdrop.backdrops.layerBackdrop
import com.xperience.player.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.xperience.player.ui.component.isGlassAllowed
import com.xperience.player.ui.component.liquidGlass
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.lyrics.LyricsProviderSettingsSheetContent
import com.xperience.player.ui.lyrics.SynchronizedLyricsView
import com.xperience.player.ui.theme.AppThemeColors
import com.xperience.player.ui.theme.ArtworkColorExtractor
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.PlusJakartaSans
import com.xperience.player.ui.utils.pressWobble
import com.xperience.player.ui.visualizer.GpuMusicVisualizer
import com.xperience.player.ui.visualizer.VisualizerSelectorSheetContent
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TRANSPORT_WAVE_STAGGER_MS = 65L
private const val TRANSPORT_AUTO_HIDE_MS = 5_000L

private val SidebarPillShape = ContinuousRoundedRectangle(percent = 50)
private val SidebarArtistChipShape = ContinuousRoundedRectangle(size = 10.dp)

enum class NowPlayingSidebarTab(val label: String) {
    QUEUE("Queue"),
    LYRICS("Lyrics")
}

object NowPlayingSidebarDefaults {
    val Width: Dp = 336.dp
}

/**
 * Docked right-hand Now Playing sidebar for Expanded tablet/desktop layouts, inspired by the
 * PC WinUI3 Now Playing sidebar while reusing Android's existing LiquidGlass player components.
 *
 * Isolates its own [rememberLayerBackdrop] over the artwork/ambient background stage so glass
 * controls refract the current track's cover without introducing cycles into the main app backdrop.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSidebar(
    playbackState: PlaybackState,
    isLiked: Boolean,
    onCollapse: () -> Unit,
    onExpandFullscreen: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekToFraction: (Float) -> Unit,
    onToggleFavorite: (String) -> Unit,
    modifier: Modifier = Modifier,
    selectedTab: NowPlayingSidebarTab? = null,
    onSelectTab: ((NowPlayingSidebarTab) -> Unit)? = null,
    initialTab: NowPlayingSidebarTab = NowPlayingSidebarTab.QUEUE,
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
    onImportLrc: (String) -> Unit = {},
    onMoveLyricsProviderPriority: (LyricsProviderId, Int) -> Unit = { _, _ -> },
    onSetLyricsProviderEnabled: (LyricsProviderId, Boolean) -> Unit = { _, _ -> },
    onDownloadTrack: (TrackItem) -> Unit = {},
    onAddToPlaylist: (TrackItem) -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    onCycleRepeatMode: () -> Unit = {},
    onArtistClick: ((String) -> Unit)? = null,
    outputDeviceName: String = "Device Speaker",
    onOpenOutputSwitcher: () -> Unit = {},
    onOpenAmbientMode: () -> Unit = {},
    onSetAsRingtone: (TrackItem) -> Unit = {},
    onPlayNext: (TrackItem) -> Unit = {},
    onAddToQueue: (TrackItem) -> Unit = {},
    onGoToAlbum: (TrackItem) -> Unit = {},
    onOpenSongInfo: (TrackItem) -> Unit = {},
    onDirectDownloadFormat: (TrackItem, com.xperience.player.domain.model.download.DownloadAudioFormat) -> Unit = { _, _ -> },
    castState: CastPlaybackState = CastPlaybackState(),
    castRouteCount: Int = 0,
    onOpenCastPicker: () -> Unit = onOpenOutputSwitcher,
    playbackPositionMsFlow: kotlinx.coroutines.flow.StateFlow<Long> = remember { kotlinx.coroutines.flow.MutableStateFlow(playbackState.currentPositionMs) },
    lyricsPlaybackPositionMsFlow: kotlinx.coroutines.flow.StateFlow<Long> = playbackPositionMsFlow,
    onSeekToLyricPosition: ((Long) -> Unit)? = null
) {
    val track = playbackState.currentTrack ?: return
    val context = LocalContext.current
    val isInspectionMode = LocalInspectionMode.current
    val appColors = LocalAppColors.current
    val accentColor = LocalAccentColor.current

    var internalTab by rememberSaveable { mutableStateOf(initialTab) }
    val activeTab = selectedTab ?: internalTab
    val handleSelectTab: (NowPlayingSidebarTab) -> Unit = { tab ->
        internalTab = tab
        onSelectTab?.invoke(tab)
    }

    var isTransportVisible by remember { mutableStateOf(isInspectionMode) }
    var transportInteractive by remember { mutableStateOf(isInspectionMode) }
    var transportTimerResetTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(isTransportVisible, transportTimerResetTick, isInspectionMode) {
        if (!isInspectionMode && isTransportVisible) {
            delay(TRANSPORT_AUTO_HIDE_MS)
            transportInteractive = false
            isTransportVisible = false
        }
    }

    val bumpTransportTimer: () -> Unit = {
        transportTimerResetTick++
    }

    var showLyricsSettingsSheet by remember { mutableStateOf(false) }
    var showDetailsSheet by remember { mutableStateOf(false) }
    var showVisualizerSheet by remember { mutableStateOf(false) }
    var showAudioEffectsSheet by remember { mutableStateOf(false) }
    var audioEffectsInitialTab by remember { mutableStateOf(AudioEffectsSectionTab.ALL) }

    val dspController = remember(context) { AudioDspController.getInstance(context) }
    val sleepTimerManager = remember(context) { SleepTimerManager.getInstance(context) }
    val dspState by dspController.dspState.collectAsState()
    val sleepTimerState by sleepTimerManager.timerState.collectAsState()

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
                if (BuildConfig.DEBUG) runCatching { Log.w("NowPlayingSidebar", "Error reading LRC file", e) }
                Toast.makeText(context, "Error reading LRC file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val onImportLrcClick: () -> Unit = {
        try {
            lrcPickerLauncher.launch(arrayOf("*/*"))
        } catch (_: Exception) {
            Toast.makeText(context, "Could not open file picker", Toast.LENGTH_SHORT).show()
        }
    }

    val onSeekToPosition: (Long) -> Unit = { posMs ->
        if (onSeekToLyricPosition != null) {
            onSeekToLyricPosition(posMs)
        } else {
            val total = playbackState.durationMs
            if (total > 0L) {
                onSeekToFraction((posMs.toFloat() / total.toFloat()).coerceIn(0f, 1f))
            }
        }
    }

    val backdropBaseColor = if (appColors.isDark) Color(0xFF0E1015) else Color(0xFFF8FAFC)
    val backdropOnDraw = remember<ContentDrawScope.() -> Unit>(backdropBaseColor) {
        {
            drawRect(backdropBaseColor)
            drawContent()
        }
    }
    val sidebarBackdrop = rememberLayerBackdrop(onDraw = backdropOnDraw)

    CompositionLocalProvider(LocalAppBackdrop provides sidebarBackdrop) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxHeight()
                .clipToBounds()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val wasVisible = isTransportVisible
                        if (!wasVisible) {
                            transportInteractive = false
                            isTransportVisible = true
                        }
                        transportTimerResetTick++
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                        } while (event.changes.any { it.pressed })
                        if (!wasVisible && isTransportVisible) {
                            transportInteractive = true
                        }
                        if (isTransportVisible) {
                            transportTimerResetTick++
                        }
                    }
                }
        ) {
            val statusBarTopDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val heroArtworkHeight = ((maxHeight * 0.37f) + statusBarTopDp * 0.5f)
                .coerceIn(220.dp, 324.dp)
            val heroStageContentHeight = (heroArtworkHeight - statusBarTopDp - 12.dp)
                .coerceAtLeast(156.dp)

            // 1. Dedicated clean backdrop stage (artwork, Canvas, ambient dissolve & optional visualizer)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(sidebarBackdrop)
            ) {
                SidebarArtworkBackdropStage(
                    track = track,
                    canvasArtwork = canvasArtwork,
                    isPlaying = playbackState.isPlaying,
                    isCanvasPaused = activeTab == NowPlayingSidebarTab.LYRICS,
                    heroHeight = heroArtworkHeight,
                    selectedVisualizer = selectedVisualizer,
                    positionMsProvider = { playbackPositionMsFlow.value },
                    durationMs = playbackState.durationMs,
                    accentColor = accentColor,
                    appColors = appColors,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Subtle left edge hairline separating sidebar from main catalog pane
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(
                        if (appColors.isDark) {
                            Color.White.copy(alpha = 0.14f)
                        } else {
                            Color(0xFF0F172A).copy(alpha = 0.12f)
                        }
                    )
            )

            // 2. Interactive foreground stack sampling sidebarBackdrop
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Hero header + overlaid glass transport controls over the cover artwork
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(heroStageContentHeight)
                ) {
                    SidebarHeaderBar(
                        castState = castState,
                        castRouteCount = castRouteCount,
                        onCollapse = onCollapse,
                        onExpandFullscreen = onExpandFullscreen,
                        onOpenCastPicker = onOpenCastPicker,
                        onOpenSongOptions = { showDetailsSheet = true },
                        appColors = appColors,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )

                    SidebarTransportControlsRow(
                        visible = isTransportVisible,
                        interactive = transportInteractive,
                        isPlaying = playbackState.isPlaying,
                        isShuffleEnabled = playbackState.isShuffleEnabled,
                        repeatMode = playbackState.repeatMode,
                        accentColor = accentColor,
                        appColors = appColors,
                        onToggleShuffle = {
                            bumpTransportTimer()
                            onToggleShuffle()
                        },
                        onSkipPrevious = {
                            bumpTransportTimer()
                            onSkipPrevious()
                        },
                        onTogglePlayPause = {
                            bumpTransportTimer()
                            onTogglePlayPause()
                        },
                        onSkipNext = {
                            bumpTransportTimer()
                            onSkipNext()
                        },
                        onCycleRepeatMode = {
                            bumpTransportTimer()
                            onCycleRepeatMode()
                        },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .padding(start = 10.dp, end = 10.dp, top = 26.dp)
                    )
                }

                // Track title + Artist / Album subtitle
                SidebarTrackInfoHeader(
                    track = track,
                    appColors = appColors,
                    onArtistClick = onArtistClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )

                if (dspState.isNightcoreEnabled || dspState.isEqOrSpeedActive || sleepTimerState.isActive) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // Active-only Nightcore / Sleep Timer / DSP badges
                    NowPlayingDspQuickBar(
                        dspState = dspState,
                        sleepTimerState = sleepTimerState,
                        onToggleNightcore = { dspController.toggleNightcore() },
                        onOpenAudioEffects = { tab ->
                            audioEffectsInitialTab = tab
                            showAudioEffectsSheet = true
                        },
                        accentColor = accentColor,
                        appColors = appColors,
                        compact = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Squiggly progress bar + Heart like toggle row
                SidebarSeekAndLikeRow(
                    playbackPositionMsFlow = playbackPositionMsFlow,
                    durationMs = playbackState.durationMs,
                    isPlaying = playbackState.isPlaying,
                    isLiked = isLiked,
                    accentColor = accentColor,
                    appColors = appColors,
                    onSeekToFraction = onSeekToFraction,
                    onToggleFavorite = { onToggleFavorite(track.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Queue / Lyrics LiquidGlass segmented switcher
                LiquidGlassSegmentedPill(
                    options = NowPlayingSidebarTab.entries,
                    selectedOption = activeTab,
                    labelProvider = { it.label },
                    onSelectOption = handleSelectTab,
                    appColors = appColors,
                    accentColor = accentColor,
                    equalWidthSegments = true,
                    segmentHeight = 32.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Tab content: InlineQueueView or SynchronizedLyricsView
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AnimatedContent(
                        targetState = activeTab,
                        transitionSpec = {
                            val forward = targetState.ordinal > initialState.ordinal
                            (fadeIn(tween(220)) + slideInHorizontally(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                initialOffsetX = { width ->
                                    val shift = (width * 0.08f).toInt()
                                    if (forward) shift else -shift
                                }
                            )) togetherWith (fadeOut(tween(170)) + slideOutHorizontally(
                                animationSpec = tween(170),
                                targetOffsetX = { width ->
                                    val shift = (width * 0.08f).toInt()
                                    if (forward) -shift else shift
                                }
                            ))
                        },
                        label = "nowPlayingSidebarTab"
                    ) { tab ->
                        when (tab) {
                            NowPlayingSidebarTab.QUEUE -> {
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
                                        .padding(horizontal = 12.dp)
                                )
                            }

                            NowPlayingSidebarTab.LYRICS -> {
                                SynchronizedLyricsView(
                                    lines = lyrics,
                                    playbackPositionMsFlow = lyricsPlaybackPositionMsFlow,
                                    providerName = lyricsProviderName,
                                    accentColor = accentColor,
                                    appColors = appColors,
                                    onSeekToPosition = onSeekToPosition,
                                    onOpenLyricsSettings = { showLyricsSettingsSheet = true },
                                    onImportLrcClick = onImportLrcClick,
                                    modifier = Modifier.fillMaxSize(),
                                    isPlaying = playbackState.isPlaying
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal dialogs / sheets matching NowPlayingScreen
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
            onImportLrcClick = onImportLrcClick
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
            onGoToArtist = if (track.artist.isNotBlank() && onArtistClick != null) {
                {
                    showDetailsSheet = false
                    onArtistClick.invoke(track.artist)
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
            onSelectVisualizer = onSelectVisualizer,
            onImportVisualizer = onImportVisualizer,
            onDeleteVisualizer = onDeleteVisualizer
        )
    }
}

@Composable
private fun SidebarArtworkBackdropStage(
    track: TrackItem,
    canvasArtwork: CanvasArtworkResult?,
    isPlaying: Boolean,
    isCanvasPaused: Boolean,
    heroHeight: Dp,
    selectedVisualizer: VisualizerDefinition,
    positionMsProvider: () -> Long,
    durationMs: Long,
    accentColor: Color,
    appColors: AppThemeColors,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val sharedImageLoader = remember(appContext) {
        MediaNetworkClients.getImageLoader(appContext)
    }
    val isDark = appColors.isDark
    val fallbackBg = appColors.background

    val resolvedArtworkUri = remember(track.artworkUri, track.artworkResId, appContext) {
        track.artworkUri?.takeIf { it.isNotBlank() }
            ?: track.artworkResId?.let { resId ->
                "android.resource://${appContext.packageName}/$resId"
            }
    }

    var extractedAmbient by remember(resolvedArtworkUri, isDark) {
        mutableStateOf(
            ArtworkColorExtractor.getCachedArtistAmbientColors(resolvedArtworkUri)
                ?.colorFor(isDark)
                ?: lerp(fallbackBg, accentColor, if (isDark) 0.14f else 0.08f)
        )
    }

    LaunchedEffect(resolvedArtworkUri, isDark) {
        if (!resolvedArtworkUri.isNullOrBlank()) {
            extractedAmbient = ArtworkColorExtractor
                .extractArtistAmbientColors(appContext, resolvedArtworkUri)
                .colorFor(isDark)
        }
    }

    val animatedAmbientColor by animateColorAsState(
        targetValue = extractedAmbient,
        animationSpec = tween(durationMillis = 650),
        label = "sidebarAmbientBg"
    )

    val activeVideoUri = canvasArtwork?.videoUri?.takeIf { it.isNotBlank() }
    val showMotionCanvas = !isCanvasPaused && activeVideoUri != null

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(animatedAmbientColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight)
                .align(Alignment.TopCenter)
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
                            .crossfade(350)
                            .build(),
                        imageLoader = sharedImageLoader,
                        contentDescription = track.album.ifBlank { track.title },
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = accentColor.copy(alpha = 0.25f),
                            modifier = Modifier.size(84.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = showMotionCanvas,
                enter = fadeIn(animationSpec = tween(durationMillis = 320)),
                exit = fadeOut(animationSpec = tween(durationMillis = 220)),
                modifier = Modifier.fillMaxSize()
            ) {
                if (activeVideoUri != null) {
                    CanvasArtworkPlayer(
                        videoUri = activeVideoUri,
                        isPlaying = isPlaying && !isCanvasPaused,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // 6-stop vertical dissolve: top header feather + seamless merge into ambient canvas below
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.00f to animatedAmbientColor.copy(alpha = if (isDark) 0.54f else 0.70f),
                                0.20f to Color.Transparent,
                                0.50f to Color.Transparent,
                                0.74f to animatedAmbientColor.copy(alpha = 0.64f),
                                0.89f to animatedAmbientColor.copy(alpha = 0.92f),
                                1.00f to animatedAmbientColor
                            )
                        )
                    )
            )
        }

        if (selectedVisualizer.renderMode != VisualizerRenderMode.None) {
            GpuMusicVisualizer(
                definition = selectedVisualizer,
                positionMsProvider = positionMsProvider,
                durationMs = durationMs,
                isPlaying = isPlaying,
                currentTrack = track,
                accentColor = accentColor,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun SidebarHeaderBar(
    castState: CastPlaybackState,
    castRouteCount: Int,
    onCollapse: () -> Unit,
    onExpandFullscreen: () -> Unit,
    onOpenCastPicker: () -> Unit,
    onOpenSongOptions: () -> Unit,
    appColors: AppThemeColors,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassCircleButton(
            onClick = onCollapse,
            size = 38.dp
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Hide Now Playing",
                tint = appColors.glassTextPrimary,
                modifier = Modifier.size(22.dp)
            )
        }

        Text(
            text = "Now Playing",
            style = TextStyle(
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = 16.5.sp,
                color = appColors.glassTextPrimary,
                shadow = Shadow(
                    color = if (appColors.isDark) {
                        Color.Black.copy(alpha = 0.55f)
                    } else {
                        Color.White.copy(alpha = 0.75f)
                    },
                    offset = Offset(0f, 1.5f),
                    blurRadius = 8f
                )
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        )

        SidebarHeaderActionPill(
            castState = castState,
            castRouteCount = castRouteCount,
            onExpandFullscreen = onExpandFullscreen,
            onOpenCastPicker = onOpenCastPicker,
            onOpenSongOptions = onOpenSongOptions,
            appColors = appColors
        )
    }
}

@Composable
private fun SidebarHeaderActionPill(
    castState: CastPlaybackState,
    castRouteCount: Int,
    onExpandFullscreen: () -> Unit,
    onOpenCastPicker: () -> Unit,
    onOpenSongOptions: () -> Unit,
    appColors: AppThemeColors,
    modifier: Modifier = Modifier
) {
    val glassConfig = LocalGlassEffectConfig.current
    val useGlass = glassConfig.isEnabledFor(GlassComponent.NAV_BAR) && isGlassAllowed()
    val backgroundModifier = if (useGlass) {
        Modifier.liquidGlass(
            config = glassConfig,
            shape = SidebarPillShape,
            highlightAlpha = 0.34f
        )
    } else {
        Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
    }

    Box(
        modifier = modifier
            .height(38.dp)
            .clip(SidebarPillShape)
            .then(backgroundModifier),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SidebarHeaderPillButton(
                icon = Icons.Default.OpenInFull,
                contentDescription = "Expand to fullscreen",
                tint = appColors.glassTextPrimary,
                iconSize = 16.dp,
                onClick = onExpandFullscreen
            )

            SidebarHeaderDivider(appColors = appColors)

            CastButton(
                castState = castState,
                onClick = onOpenCastPicker,
                availableRouteCount = castRouteCount,
                style = CastButtonStyle.ICON,
                iconSize = 16.5.dp,
                appColors = appColors,
                modifier = Modifier.size(width = 32.dp, height = 34.dp)
            )

            SidebarHeaderDivider(appColors = appColors)

            SidebarHeaderPillButton(
                icon = Icons.Default.MoreHoriz,
                contentDescription = "Song options",
                tint = appColors.glassTextPrimary,
                iconSize = 18.dp,
                onClick = onOpenSongOptions
            )
        }
    }
}

@Composable
private fun SidebarHeaderPillButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    iconSize: Dp,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .pressWobble(interaction)
            .size(width = 32.dp, height = 34.dp)
            .clip(SidebarPillShape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = false, radius = 17.dp),
                role = Role.Button,
                onClick = onClick
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun SidebarHeaderDivider(appColors: AppThemeColors) {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(16.dp)
            .background(appColors.glassTextPrimary.copy(alpha = 0.16f))
    )
}

private class SidebarTransportWaveAnimState(initialVisible: Boolean) {
    val alpha = Animatable(if (initialVisible) 1f else 0f)
    val scaleX = Animatable(if (initialVisible) 1f else 0.45f)
    val scaleY = Animatable(if (initialVisible) 1f else 0.45f)
    val offsetYDp = Animatable(if (initialVisible) 0f else 18f)
}

@Composable
private fun SidebarTransportWaveSlot(
    slotSize: Dp,
    waveState: SidebarTransportWaveAnimState,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier.size(slotSize),
        contentAlignment = Alignment.Center
    ) {
        if (waveState.alpha.value > 0.005f) {
            Box(
                modifier = Modifier.graphicsLayer {
                    alpha = waveState.alpha.value.coerceIn(0f, 1f)
                    scaleX = waveState.scaleX.value
                    scaleY = waveState.scaleY.value
                    translationY = waveState.offsetYDp.value.dp.toPx()
                },
                contentAlignment = Alignment.Center
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SidebarTransportControlsRow(
    visible: Boolean,
    interactive: Boolean,
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    repeatMode: PlaybackRepeatMode,
    accentColor: Color,
    appColors: AppThemeColors,
    onToggleShuffle: () -> Unit,
    onSkipPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isInspectionMode = LocalInspectionMode.current
    // Wave 0 = center (Play/Pause), Wave 1 = sides (Prev/Next), Wave 2 = outer (Shuffle/Repeat).
    val waveStates = remember { List(3) { SidebarTransportWaveAnimState(isInspectionMode) } }

    LaunchedEffect(visible, isInspectionMode) {
        if (isInspectionMode) return@LaunchedEffect
        if (visible) {
            // Enter center-out with bouncy jelly spring: Play/Pause -> Prev/Next -> Shuffle/Repeat
            val enterOrder = intArrayOf(0, 1, 2)
            coroutineScope {
                enterOrder.forEachIndexed { stepIndex, waveIdx ->
                    val state = waveStates[waveIdx]
                    launch {
                        delay(stepIndex * TRANSPORT_WAVE_STAGGER_MS)
                        launch {
                            state.alpha.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(durationMillis = 135, easing = FastOutSlowInEasing)
                            )
                        }
                        launch {
                            state.offsetYDp.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = 0.52f,
                                    stiffness = 440f
                                )
                            )
                        }
                        launch {
                            state.scaleX.animateTo(
                                targetValue = 1f,
                                animationSpec = spring(
                                    dampingRatio = 0.42f,
                                    stiffness = 380f
                                )
                            )
                        }
                        launch {
                            state.scaleY.animateTo(
                                targetValue = 1f,
                                animationSpec = spring(
                                    dampingRatio = 0.48f,
                                    stiffness = 450f
                                )
                            )
                        }
                    }
                }
            }
        } else {
            if (waveStates.all { it.alpha.value <= 0.001f }) return@LaunchedEffect
            // Exit in exact reverse order: Shuffle/Repeat -> Prev/Next -> Play/Pause
            val exitOrder = intArrayOf(2, 1, 0)
            coroutineScope {
                exitOrder.forEachIndexed { stepIndex, waveIdx ->
                    val state = waveStates[waveIdx]
                    launch {
                        delay(stepIndex * TRANSPORT_WAVE_STAGGER_MS)
                        launch {
                            state.offsetYDp.animateTo(
                                targetValue = 18f,
                                animationSpec = keyframes {
                                    durationMillis = 220
                                    (-3.5f) at 55 using FastOutSlowInEasing
                                    18f at 220 using FastOutLinearInEasing
                                }
                            )
                        }
                        launch {
                            state.scaleX.animateTo(
                                targetValue = 0.45f,
                                animationSpec = keyframes {
                                    durationMillis = 220
                                    1.08f at 50 using FastOutSlowInEasing
                                    0.45f at 220 using FastOutLinearInEasing
                                }
                            )
                        }
                        launch {
                            state.scaleY.animateTo(
                                targetValue = 0.45f,
                                animationSpec = keyframes {
                                    durationMillis = 220
                                    0.94f at 45 using FastOutSlowInEasing
                                    1.05f at 80 using FastOutSlowInEasing
                                    0.45f at 220 using FastOutLinearInEasing
                                }
                            )
                        }
                        launch {
                            state.alpha.animateTo(
                                targetValue = 0f,
                                animationSpec = keyframes {
                                    durationMillis = 210
                                    1f at 55
                                    0f at 210 using FastOutLinearInEasing
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    val isSlotClickable = visible && interactive

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SidebarTransportWaveSlot(slotSize = 38.dp, waveState = waveStates[2]) {
            SidebarGlassTransportButton(
                size = 38.dp,
                highlightAlpha = 0.36f,
                appColors = appColors,
                enabled = isSlotClickable,
                onClick = onToggleShuffle
            ) {
                Icon(
                    painter = painterResource(
                        if (isShuffleEnabled) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle_off
                    ),
                    contentDescription = "Shuffle",
                    tint = if (isShuffleEnabled) {
                        accentColor
                    } else {
                        appColors.glassTextPrimary.copy(alpha = 0.80f)
                    },
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        SidebarTransportWaveSlot(slotSize = 44.dp, waveState = waveStates[1]) {
            SidebarGlassTransportButton(
                size = 44.dp,
                highlightAlpha = 0.40f,
                appColors = appColors,
                enabled = isSlotClickable,
                onClick = onSkipPrevious
            ) {
                StrokedSkipPreviousIcon(
                    tint = appColors.glassTextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        SidebarTransportWaveSlot(slotSize = 58.dp, waveState = waveStates[0]) {
            MorphingPlayPauseButton(
                isPlaying = isPlaying,
                onClick = onTogglePlayPause,
                buttonSize = 58.dp,
                iconSize = 26.dp,
                tint = appColors.glassTextPrimary,
                enabled = isSlotClickable
            )
        }

        SidebarTransportWaveSlot(slotSize = 44.dp, waveState = waveStates[1]) {
            SidebarGlassTransportButton(
                size = 44.dp,
                highlightAlpha = 0.40f,
                appColors = appColors,
                enabled = isSlotClickable,
                onClick = onSkipNext
            ) {
                StrokedSkipNextIcon(
                    tint = appColors.glassTextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        val isRepeatActive = repeatMode != PlaybackRepeatMode.Off
        val repeatIconRes = when (repeatMode) {
            PlaybackRepeatMode.RepeatOne -> R.drawable.ic_repeat_one
            PlaybackRepeatMode.RepeatAll -> R.drawable.ic_repeat_all
            PlaybackRepeatMode.Off -> R.drawable.ic_repeat_off
        }
        SidebarTransportWaveSlot(slotSize = 38.dp, waveState = waveStates[2]) {
            SidebarGlassTransportButton(
                size = 38.dp,
                highlightAlpha = 0.36f,
                appColors = appColors,
                enabled = isSlotClickable,
                onClick = onCycleRepeatMode
            ) {
                Icon(
                    painter = painterResource(repeatIconRes),
                    contentDescription = "Repeat",
                    tint = if (isRepeatActive) {
                        accentColor
                    } else {
                        appColors.glassTextPrimary.copy(alpha = 0.80f)
                    },
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun SidebarGlassTransportButton(
    size: Dp,
    highlightAlpha: Float,
    appColors: AppThemeColors,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val glassConfig = LocalGlassEffectConfig.current
    val useGlass = glassConfig.isEnabledFor(GlassComponent.PLAYER) && isGlassAllowed()
    val interactionSource = remember { MutableInteractionSource() }

    val glassModifier = if (useGlass) {
        Modifier.liquidGlass(
            config = glassConfig,
            shape = CircleShape,
            highlightAlpha = highlightAlpha
        )
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .pressWobble(interactionSource)
            .size(size)
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
            .then(glassModifier)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun SidebarTrackInfoHeader(
    track: TrackItem,
    appColors: AppThemeColors,
    onArtistClick: ((String) -> Unit)?,
    modifier: Modifier = Modifier
) {
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
                fontSize = 18.5.sp,
                color = appColors.textPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .basicMarquee()
            )
            if (track.isExplicit) {
                Spacer(modifier = Modifier.width(6.dp))
                ExplicitBadge()
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        val subtitle = remember(track.id, track.artist, track.album, track.title) {
            if (hasValidAlbumName(track)) {
                "${track.artist} • ${track.album}"
            } else {
                track.artist
            }
        }

        val subtitleInteraction = remember { MutableInteractionSource() }
        val subtitleModifier = if (onArtistClick != null && track.artist.isNotBlank()) {
            Modifier
                .pressWobble(subtitleInteraction, pressedScale = 0.96f)
                .clip(SidebarArtistChipShape)
                .clickable(
                    interactionSource = subtitleInteraction,
                    indication = null,
                    onClick = { onArtistClick(track.artist) }
                )
                .padding(horizontal = 8.dp, vertical = 2.dp)
        } else {
            Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        }

        Box(
            modifier = subtitleModifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = subtitle,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Medium,
                fontSize = 12.5.sp,
                color = appColors.textSecondary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.basicMarquee()
            )
        }
    }
}

@Composable
private fun SidebarSeekAndLikeRow(
    playbackPositionMsFlow: kotlinx.coroutines.flow.StateFlow<Long>,
    durationMs: Long,
    isPlaying: Boolean,
    isLiked: Boolean,
    accentColor: Color,
    appColors: AppThemeColors,
    onSeekToFraction: (Float) -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val heartScale by animateFloatAsState(
        targetValue = if (isLiked) 1.2f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "sidebarHeartScale"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SquigglyProgressBar(
            playbackPositionMsFlow = playbackPositionMsFlow,
            durationMs = durationMs,
            isPlaying = isPlaying,
            accentColor = accentColor,
            appColors = appColors,
            onSeek = onSeekToFraction,
            modifier = Modifier.weight(1f)
        )

        GlassCircleButton(
            onClick = onToggleFavorite,
            size = 48.dp
        ) {
            Icon(
                imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Like",
                tint = if (isLiked) Color(0xFFFF3366) else appColors.glassTextPrimary,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        scaleX = heartScale
                        scaleY = heartScale
                    }
            )
        }
    }
}

@Composable
private fun NowPlayingSidebarPreviewContent(
    darkTheme: Boolean,
    initialTab: NowPlayingSidebarTab
) {
    AdaptivePreviewSurface(
        widthDp = 360,
        heightDp = 800,
        darkTheme = darkTheme
    ) {
        NowPlayingSidebar(
            playbackState = AdaptivePreviewFixtures.playbackState,
            isLiked = true,
            onCollapse = {},
            onExpandFullscreen = {},
            onTogglePlayPause = {},
            onSkipNext = {},
            onSkipPrevious = {},
            onSeekToFraction = {},
            onToggleFavorite = {},
            initialTab = initialTab,
            lyrics = AdaptivePreviewFixtures.lyrics,
            lyricsProviderName = "Better Lyrics",
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview(name = "Sidebar Dark - Queue", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
private fun NowPlayingSidebarDarkQueuePreview() =
    NowPlayingSidebarPreviewContent(darkTheme = true, initialTab = NowPlayingSidebarTab.QUEUE)

@Preview(name = "Sidebar Light - Lyrics", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
private fun NowPlayingSidebarLightLyricsPreview() =
    NowPlayingSidebarPreviewContent(darkTheme = false, initialTab = NowPlayingSidebarTab.LYRICS)
