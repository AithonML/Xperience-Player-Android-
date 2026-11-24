package com.xperience.player.ui.ambient

import androidx.compose.runtime.Immutable
import com.xperience.player.data.VisualizerRepository
import com.xperience.player.domain.model.PlaybackState
import com.xperience.player.domain.model.canvas.CanvasArtworkResult
import com.xperience.player.domain.model.lyrics.LyricLine
import com.xperience.player.domain.model.visualizer.VisualizerDefinition
import com.xperience.player.ui.theme.AuroraColors
import com.xperience.player.ui.theme.DEFAULT_AURORA_COLORS

/**
 * Right-pane / stage presentation modes available inside full-screen landscape Ambient Mode.
 */
enum class AmbientPanelMode(val label: String) {
    /** Split widescreen layout: album artwork on left, live synchronized lyrics on right. */
    LYRICS("Lyrics"),

    /** Split widescreen layout: album artwork on left, editorial clock + track metadata + Up Next on right. */
    CLOCK_METADATA("Clock"),

    /** Pure minimalist gallery showcase centered on album artwork / motion canvas and subtle metadata. */
    CINEMA_ART("Cinema")
}

/**
 * Result of classifying a horizontal swipe gesture across the Ambient Mode stage.
 */
enum class AmbientSwipeDirection {
    NONE,
    NEXT,
    PREVIOUS
}

/**
 * Immutable UI state contract consumed by [AmbientModeScreen].
 *
 * Designed so the host (`HomeScreen` / `NowPlayingScreen` / `Navigation`) can map existing
 * `HomeViewModel` flows directly into Ambient Mode in just a few lines.
 */
@Immutable
data class AmbientModeUiState(
    val playbackState: PlaybackState = PlaybackState(),
    val isLiked: Boolean = false,
    val lyrics: List<LyricLine> = emptyList(),
    val lyricsProviderName: String? = null,
    val canvasArtwork: CanvasArtworkResult? = null,
    val selectedVisualizer: VisualizerDefinition = VisualizerRepository.DefaultPreset,
    val availableVisualizers: List<VisualizerDefinition> = VisualizerRepository.BuiltInPresets,
    val auroraColors: AuroraColors = DEFAULT_AURORA_COLORS,
    val outputDeviceName: String = "Device Speaker",
    val audioQualityLabel: String? = null,
    val isCastConnected: Boolean = false,
    val castDeviceName: String? = null
)

/**
 * Immutable action callbacks exposed by [AmbientModeScreen].
 */
@Immutable
data class AmbientModeActions(
    val onExit: () -> Unit,
    val onTogglePlayPause: () -> Unit = {},
    val onSkipNext: () -> Unit = {},
    val onSkipPrevious: () -> Unit = {},
    val onSeekToFraction: (Float) -> Unit = {},
    val onToggleFavorite: (trackId: String) -> Unit = {},
    val onToggleShuffle: () -> Unit = {},
    val onCycleRepeatMode: () -> Unit = {},
    val onSelectVisualizer: (VisualizerDefinition) -> Unit = {},
    val onOpenCastPicker: () -> Unit = {},
    val onOpenLyricsSettings: () -> Unit = {},
    val onPlayQueueIndex: (Int) -> Unit = {}
)
