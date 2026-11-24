package com.xperience.player.ui.ambient

import androidx.compose.runtime.Immutable
import com.xperience.player.data.VisualizerRepository
import com.xperience.player.domain.model.PlaybackRepeatMode
import com.xperience.player.domain.model.PlaybackState
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.domain.model.visualizer.VisualizerDefinition
import com.xperience.player.domain.model.visualizer.VisualizerRenderMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

const val DEFAULT_AMBIENT_HUD_AUTO_HIDE_MS: Long = 4_500L
const val DEFAULT_BURN_IN_SHIFT_INTERVAL_MS: Long = 45_000L
const val DEFAULT_BURN_IN_MAX_AMPLITUDE_DP: Float = 6f

/**
 * Internal interactive state for Ambient Mode's HUD, panel selection, visual layers, and timer generation.
 */
@Immutable
data class AmbientHudState(
    val isHudVisible: Boolean = true,
    val isScrubbing: Boolean = false,
    val panelMode: AmbientPanelMode = AmbientPanelMode.LYRICS,
    val isVisualizerEnabled: Boolean = false,
    val isCanvasVideoEnabled: Boolean = true,
    val showRemainingTime: Boolean = false,
    val hudAutoHideDelayMs: Long = DEFAULT_AMBIENT_HUD_AUTO_HIDE_MS,
    val interactionGeneration: Long = 0L
) {
    /**
     * Returns the auto-hide timeout in milliseconds when the HUD should automatically dismiss,
     * or `null` when auto-hide should be suspended (HUD already hidden, user actively scrubbing,
     * or non-positive delay).
     */
    fun resolveAutoHideDelayMs(): Long? {
        if (!isHudVisible || isScrubbing || hudAutoHideDelayMs <= 0L) return null
        return hudAutoHideDelayMs
    }
}

/**
 * Pure Kotlin state holder and auto-hide timer controller for Ambient Mode.
 *
 * All transitions are deterministic and directly testable on JVM without an Android runtime.
 */
class AmbientHudController(
    initialPanelMode: AmbientPanelMode = AmbientPanelMode.LYRICS,
    initialHudVisible: Boolean = true,
    initialVisualizerEnabled: Boolean = false,
    initialCanvasVideoEnabled: Boolean = true,
    hudAutoHideDelayMs: Long = DEFAULT_AMBIENT_HUD_AUTO_HIDE_MS
) {
    private val _state = MutableStateFlow(
        AmbientHudState(
            isHudVisible = initialHudVisible,
            panelMode = initialPanelMode,
            isVisualizerEnabled = initialVisualizerEnabled,
            isCanvasVideoEnabled = initialCanvasVideoEnabled,
            hudAutoHideDelayMs = hudAutoHideDelayMs.coerceAtLeast(0L)
        )
    )
    val state: StateFlow<AmbientHudState> = _state.asStateFlow()

    private var autoHideJob: Job? = null

    /**
     * Reveals the HUD and bumps [AmbientHudState.interactionGeneration] so any active auto-hide
     * countdown restarts cleanly from zero.
     */
    fun revealHud() {
        _state.update { current ->
            current.copy(
                isHudVisible = true,
                interactionGeneration = current.interactionGeneration + 1L
            )
        }
    }

    /**
     * Immediately hides the HUD (unless the user is actively scrubbing) and cancels any pending timer.
     */
    fun hideHud(force: Boolean = false) {
        if (!force && _state.value.isScrubbing) return
        cancelAutoHideTimer()
        _state.update { current ->
            current.copy(
                isHudVisible = false,
                isScrubbing = if (force) false else current.isScrubbing
            )
        }
    }

    /**
     * Toggles HUD visibility when the user taps the ambient backdrop/stage.
     */
    fun toggleHud() {
        if (_state.value.isHudVisible) {
            hideHud()
        } else {
            revealHud()
        }
    }

    /**
     * Registers user interaction with an on-screen HUD control, keeping the HUD visible
     * and resetting the inactivity countdown.
     */
    fun registerInteraction() {
        _state.update { current ->
            current.copy(
                isHudVisible = true,
                interactionGeneration = current.interactionGeneration + 1L
            )
        }
    }

    /**
     * Resets the inactivity countdown only if the HUD is already visible, without unexpectedly
     * revealing it when the user interacts with stage content (e.g., tapping a lyric line).
     */
    fun refreshTimerIfVisible() {
        _state.update { current ->
            if (current.isHudVisible) {
                current.copy(interactionGeneration = current.interactionGeneration + 1L)
            } else {
                current
            }
        }
    }

    /**
     * Tracks seek-bar scrubbing so the HUD never fades away while the user drags the thumb.
     * Releasing the thumb restarts the inactivity timer cleanly.
     */
    fun setScrubbing(scrubbing: Boolean) {
        if (scrubbing) {
            cancelAutoHideTimer()
        }
        _state.update { current ->
            current.copy(
                isHudVisible = if (scrubbing) true else current.isHudVisible,
                isScrubbing = scrubbing,
                interactionGeneration = if (!scrubbing) {
                    current.interactionGeneration + 1L
                } else {
                    current.interactionGeneration
                }
            )
        }
    }

    fun setPanelMode(mode: AmbientPanelMode) {
        _state.update { current ->
            current.copy(
                panelMode = mode,
                interactionGeneration = if (current.isHudVisible) {
                    current.interactionGeneration + 1L
                } else {
                    current.interactionGeneration
                }
            )
        }
    }

    /**
     * Cycles across available Ambient panel modes (`LYRICS -> CLOCK_METADATA -> CINEMA_ART`).
     * When [hasLyrics] is false and starting from `CLOCK_METADATA`, cycles cleanly through
     * `CINEMA_ART` and back without stalling.
     */
    fun cyclePanelMode(hasLyrics: Boolean = true) {
        val next = when (_state.value.panelMode) {
            AmbientPanelMode.LYRICS -> AmbientPanelMode.CLOCK_METADATA
            AmbientPanelMode.CLOCK_METADATA -> AmbientPanelMode.CINEMA_ART
            AmbientPanelMode.CINEMA_ART -> if (hasLyrics) AmbientPanelMode.LYRICS else AmbientPanelMode.CLOCK_METADATA
        }
        setPanelMode(next)
    }

    fun toggleVisualizer() {
        _state.update { current ->
            current.copy(
                isVisualizerEnabled = !current.isVisualizerEnabled,
                interactionGeneration = if (current.isHudVisible) {
                    current.interactionGeneration + 1L
                } else {
                    current.interactionGeneration
                }
            )
        }
    }

    fun toggleCanvasVideo() {
        _state.update { current ->
            current.copy(
                isCanvasVideoEnabled = !current.isCanvasVideoEnabled,
                interactionGeneration = if (current.isHudVisible) {
                    current.interactionGeneration + 1L
                } else {
                    current.interactionGeneration
                }
            )
        }
    }

    fun toggleRemainingTimeDisplay() {
        _state.update { current ->
            current.copy(
                showRemainingTime = !current.showRemainingTime,
                interactionGeneration = if (current.isHudVisible) {
                    current.interactionGeneration + 1L
                } else {
                    current.interactionGeneration
                }
            )
        }
    }

    /**
     * Schedules or cancels a single auto-hide countdown job inside [scope] based on the current
     * [AmbientHudState]. Calling this again cleanly cancels the previous job so timers never leak.
     */
    fun scheduleAutoHide(scope: CoroutineScope): Job? {
        cancelAutoHideTimer()
        val snapshot = _state.value
        val delayMs = snapshot.resolveAutoHideDelayMs() ?: return null
        val targetGeneration = snapshot.interactionGeneration
        val job = scope.launch {
            delay(delayMs)
            _state.update { latest ->
                if (latest.isHudVisible &&
                    !latest.isScrubbing &&
                    latest.interactionGeneration == targetGeneration
                ) {
                    latest.copy(isHudVisible = false)
                } else {
                    latest
                }
            }
        }
        autoHideJob = job
        return job
    }

    fun cancelAutoHideTimer() {
        autoHideJob?.cancel()
        autoHideJob = null
    }
}

/**
 * Formats an elapsed or total duration in milliseconds as `m:ss` (or `h:mm:ss` when >= 1 hour).
 */
fun formatAmbientTime(positionMs: Long): String {
    val totalSeconds = (positionMs / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/**
 * Formats remaining track time as `-m:ss` (or `-h:mm:ss`). Clamps safely when [durationMs] <= 0
 * or when [positionMs] overshoots [durationMs].
 */
fun formatRemainingTime(positionMs: Long, durationMs: Long): String {
    val safeDuration = durationMs.coerceAtLeast(0L)
    val safePosition = positionMs.coerceIn(0L, safeDuration)
    val remainingMs = (safeDuration - safePosition).coerceAtLeast(0L)
    return "-${formatAmbientTime(remainingMs)}"
}

/**
 * Resolves the next upcoming track in the queue for the "Up Next" ambient preview card.
 *
 * Respects [PlaybackRepeatMode.RepeatOne], [PlaybackRepeatMode.RepeatAll], and
 * [PlaybackRepeatMode.Off], and falls back to matching [PlaybackState.currentTrack] by ID
 * if [PlaybackState.currentQueueIndex] is unset or stale.
 */
fun resolveUpNextTrack(playbackState: PlaybackState): TrackItem? {
    val queue = playbackState.queue
    val currentTrack = playbackState.currentTrack
    if (queue.isEmpty()) return null

    val currentIndex = when {
        playbackState.currentQueueIndex in queue.indices -> playbackState.currentQueueIndex
        currentTrack != null -> queue.indexOfFirst { it.id == currentTrack.id }
        else -> -1
    }
    if (currentIndex < 0) return queue.firstOrNull()

    return when (playbackState.repeatMode) {
        PlaybackRepeatMode.RepeatOne -> queue[currentIndex]
        PlaybackRepeatMode.RepeatAll -> {
            if (queue.size == 1) queue[0] else queue[(currentIndex + 1) % queue.size]
        }
        PlaybackRepeatMode.Off -> queue.getOrNull(currentIndex + 1)
    }
}

/**
 * Resolves the queue index corresponding to the next track returned by [resolveUpNextTrack].
 */
fun resolveUpNextQueueIndex(playbackState: PlaybackState): Int? {
    val queue = playbackState.queue
    if (queue.isEmpty()) return null
    val currentTrack = playbackState.currentTrack
    val currentIndex = when {
        playbackState.currentQueueIndex in queue.indices -> playbackState.currentQueueIndex
        currentTrack != null -> queue.indexOfFirst { it.id == currentTrack.id }
        else -> -1
    }
    if (currentIndex < 0) return 0

    return when (playbackState.repeatMode) {
        PlaybackRepeatMode.RepeatOne -> currentIndex
        PlaybackRepeatMode.RepeatAll -> if (queue.size == 1) 0 else (currentIndex + 1) % queue.size
        PlaybackRepeatMode.Off -> (currentIndex + 1).takeIf { it in queue.indices }
    }
}

/**
 * Derives a concise audio quality / source badge string for Ambient Mode metadata.
 */
fun deriveAudioQualityLabel(track: TrackItem?, explicitLabel: String? = null): String {
    val cleanedOverride = explicitLabel?.trim()?.takeIf { it.isNotEmpty() }
    if (cleanedOverride != null) return cleanedOverride
    if (track == null) return "Ambient Audio"

    val url = track.audioUrl?.lowercase().orEmpty()
    return when {
        url.endsWith(".flac") || url.contains(".flac?") -> "FLAC · Lossless"
        url.endsWith(".wav") || url.contains(".wav?") ||
            url.endsWith(".alac") || url.endsWith(".aiff") -> "Hi-Res Lossless"
        url.endsWith(".opus") || url.contains("opus") -> "Opus · High Quality"
        url.endsWith(".m4a") || url.endsWith(".aac") || url.contains("mp4a") -> "AAC · High Quality"
        url.endsWith(".mp3") -> "MP3 · Local Audio"
        url.startsWith("http://") || url.startsWith("https://") -> "High Quality Stream"
        url.startsWith("content://") || url.startsWith("file://") || url.startsWith("/") -> "Local Audio"
        else -> "Xperience Audio"
    }
}

/**
 * Computes a gentle, bounded burn-in protection pixel-shift offset (in dp) along a slow
 * Lissajous curve so static ambient UI elements subtly drift over extended OLED sessions.
 */
fun computeBurnInShiftDp(
    tickIndex: Long,
    maxAmplitudeDp: Float = DEFAULT_BURN_IN_MAX_AMPLITUDE_DP
): Pair<Float, Float> {
    val safeMax = maxAmplitudeDp.coerceAtLeast(0f)
    if (safeMax == 0f || tickIndex <= 0L) return 0f to 0f
    val angle = (tickIndex % 360L).toDouble() * (PI / 12.0)
    val offsetX = (sin(angle) * safeMax).toFloat().coerceIn(-safeMax, safeMax)
    val offsetY = (cos(angle * 0.5) * (safeMax * 0.75f)).toFloat().coerceIn(-safeMax, safeMax)
    return offsetX to offsetY
}

/**
 * Classifies a completed horizontal drag gesture across the Ambient stage into a track skip action.
 *
 * Swiping left (`totalDragX < 0`) advances to [AmbientSwipeDirection.NEXT];
 * swiping right (`totalDragX > 0`) returns to [AmbientSwipeDirection.PREVIOUS].
 */
fun resolveHorizontalSwipeAction(
    totalDragX: Float,
    thresholdPx: Float
): AmbientSwipeDirection {
    val safeThreshold = thresholdPx.coerceAtLeast(1f)
    if (abs(totalDragX) < safeThreshold) return AmbientSwipeDirection.NONE
    return if (totalDragX < 0f) AmbientSwipeDirection.NEXT else AmbientSwipeDirection.PREVIOUS
}

/**
 * Resolves which [VisualizerDefinition] should render in Ambient Mode given the user's Ambient
 * visualizer toggle. When enabled while the global visualizer is `BackdropOnly` (`None`), falls
 * back gracefully to `AuroraPrismaticWaves` (or the first active built-in preset).
 */
fun resolveActiveAmbientVisualizer(
    selectedVisualizer: VisualizerDefinition,
    availableVisualizers: List<VisualizerDefinition>,
    isVisualizerEnabled: Boolean
): VisualizerDefinition {
    val offPreset = availableVisualizers.firstOrNull { it.renderMode == VisualizerRenderMode.None }
        ?: VisualizerRepository.BuiltInPresets.last()

    if (!isVisualizerEnabled) return offPreset
    if (selectedVisualizer.renderMode != VisualizerRenderMode.None) return selectedVisualizer

    return availableVisualizers.firstOrNull {
        it.renderMode == VisualizerRenderMode.AuroraPrismaticWaves
    } ?: availableVisualizers.firstOrNull {
        it.renderMode != VisualizerRenderMode.None
    } ?: VisualizerRepository.BuiltInPresets.first {
        it.renderMode == VisualizerRenderMode.AuroraPrismaticWaves
    }
}

/**
 * Chooses the best initial [AmbientPanelMode] for a track: prefers [AmbientPanelMode.LYRICS]
 * when lyrics are present and [AmbientPanelMode.CLOCK_METADATA] otherwise.
 */
fun resolveDefaultPanelMode(hasLyrics: Boolean): AmbientPanelMode {
    return if (hasLyrics) AmbientPanelMode.LYRICS else AmbientPanelMode.CLOCK_METADATA
}

/**
 * Formats track artist and album into a crisp subtitle without duplicating identical values
 * or placeholder album names.
 */
fun formatAmbientSubtitle(track: TrackItem): String {
    val artist = track.artist.trim()
    val album = track.album.trim()
    val hasDistinctAlbum = album.isNotEmpty() &&
        !album.equals("Unknown Album", ignoreCase = true) &&
        !album.equals(track.title.trim(), ignoreCase = true) &&
        !album.equals(artist, ignoreCase = true)

    return when {
        artist.isNotEmpty() && hasDistinctAlbum -> "$artist · $album"
        artist.isNotEmpty() -> artist
        hasDistinctAlbum -> album
        else -> ""
    }
}
