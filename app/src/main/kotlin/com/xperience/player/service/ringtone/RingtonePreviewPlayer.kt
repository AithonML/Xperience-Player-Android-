package com.xperience.player.service.ringtone

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Lightweight, self-contained Media3 [ExoPlayer] controller dedicated to previewing:
 * 1. The exact trimmed `[startMs, endMs]` segment with looping and live fade-in/fade-out volume envelope.
 * 2. Previously saved [SavedRingtoneClip]s from the Saved Clips manager tab.
 *
 * Automatically invokes [onPauseMainPlayback] before playing so the main music player never overlaps,
 * and cleanly releases the underlying [ExoPlayer] in [release].
 */
class RingtonePreviewPlayer(
    context: Context,
    private val onPauseMainPlayback: () -> Unit
) {
    private val appContext = context.applicationContext
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var exoPlayer: ExoPlayer? = null
    private var pollJob: Job? = null
    private var released = false

    private var loadedUriString: String? = null
    private var isLoopingSegment: Boolean = false
    private var activeRange: TrimRange = TrimRange.ZERO
    private var activeFadeIn: Boolean = false
    private var activeFadeOut: Boolean = false
    private var activeFadeDurationMs: Long = RingtoneTrimRequest.DEFAULT_FADE_DURATION_MS
    private var lastLoopSeekWallMs: Long = 0L

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _previewingClipId = MutableStateFlow<Long?>(null)
    val previewingClipId: StateFlow<Long?> = _previewingClipId.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            _isPlaying.value = playing
            if (playing) {
                startPolling()
            } else {
                stopPolling()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                val player = exoPlayer ?: return
                if (isLoopingSegment && activeRange.durationMs > 0L) {
                    player.seekTo(activeRange.startMs)
                    player.playWhenReady = true
                } else {
                    stop()
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "Preview playback error: ${error.errorCodeName} - ${error.message}")
            stop()
        }
    }

    private fun ensurePlayer(): ExoPlayer? {
        if (released) return null
        exoPlayer?.let { return it }
        return try {
            val attrs = AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build()
            ExoPlayer.Builder(appContext)
                .setAudioAttributes(attrs, true)
                .setHandleAudioBecomingNoisy(true)
                .build()
                .also { player ->
                    player.repeatMode = Player.REPEAT_MODE_OFF
                    player.volume = 1f
                    player.addListener(playerListener)
                    exoPlayer = player
                }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize preview ExoPlayer: ${t.message}", t)
            null
        }
    }

    /**
     * Starts or pauses looping preview of `[range.startMs, range.endMs]` on [uriString].
     */
    fun toggleSegmentPreview(
        uriString: String,
        range: TrimRange,
        fadeInEnabled: Boolean,
        fadeOutEnabled: Boolean,
        fadeDurationMs: Long
    ) {
        if (released) return
        if (_isPlaying.value && isLoopingSegment && _previewingClipId.value == null && loadedUriString == uriString) {
            stop()
            return
        }
        startSegmentPreview(uriString, range, fadeInEnabled, fadeOutEnabled, fadeDurationMs, seekPosMs = range.startMs)
    }

    /**
     * Starts looping preview from a specific position (defaults to `range.startMs`).
     */
    fun startSegmentPreview(
        uriString: String,
        range: TrimRange,
        fadeInEnabled: Boolean,
        fadeOutEnabled: Boolean,
        fadeDurationMs: Long,
        seekPosMs: Long = range.startMs
    ) {
        if (released || uriString.isBlank() || range.durationMs <= 0L) return
        val player = ensurePlayer() ?: return

        runCatching { onPauseMainPlayback() }

        isLoopingSegment = true
        _previewingClipId.value = null
        activeRange = range
        activeFadeIn = fadeInEnabled
        activeFadeOut = fadeOutEnabled
        activeFadeDurationMs = fadeDurationMs

        val clampedStart = seekPosMs.coerceIn(range.startMs, (range.endMs - 50L).coerceAtLeast(range.startMs))

        if (loadedUriString != uriString) {
            loadedUriString = uriString
            player.setMediaItem(MediaItem.fromUri(parseUri(uriString)))
            player.prepare()
        }
        player.volume = PcmFadeProcessor.previewGainAtPositionMs(
            positionMs = clampedStart,
            range = range,
            fadeInEnabled = fadeInEnabled,
            fadeOutEnabled = fadeOutEnabled,
            fadeDurationMs = fadeDurationMs
        )
        player.seekTo(clampedStart)
        _positionMs.value = clampedStart
        player.playWhenReady = true
        _isPlaying.value = true
        startPolling()
    }

    /**
     * Updates active trim boundaries / fade toggles while previewing without audio glitches.
     */
    fun updateLiveSegmentParameters(
        range: TrimRange,
        fadeInEnabled: Boolean,
        fadeOutEnabled: Boolean,
        fadeDurationMs: Long
    ) {
        activeRange = range
        activeFadeIn = fadeInEnabled
        activeFadeOut = fadeOutEnabled
        activeFadeDurationMs = fadeDurationMs

        val player = exoPlayer ?: return
        if (isLoopingSegment && _isPlaying.value && _previewingClipId.value == null) {
            val pos = player.currentPosition
            if (pos < range.startMs || pos >= range.endMs) {
                player.seekTo(range.startMs)
                _positionMs.value = range.startMs
            }
        }
    }

    /**
     * Seeks the live segment preview playhead directly to [positionMs] within [activeRange].
     */
    fun seekSegmentTo(positionMs: Long) {
        val player = exoPlayer ?: return
        if (!isLoopingSegment || activeRange.durationMs <= 0L) return
        val clamped = positionMs.coerceIn(activeRange.startMs, activeRange.endMs)
        player.seekTo(clamped)
        _positionMs.value = clamped
    }

    /**
     * Plays or stops a previously saved ringtone clip from MediaStore.
     */
    fun toggleSavedClipPreview(clip: SavedRingtoneClip) {
        if (released) return
        if (_isPlaying.value && _previewingClipId.value == clip.id) {
            stop()
            return
        }
        val player = ensurePlayer() ?: return
        runCatching { onPauseMainPlayback() }

        isLoopingSegment = false
        _previewingClipId.value = clip.id
        loadedUriString = clip.contentUri

        player.volume = 1f
        player.setMediaItem(MediaItem.fromUri(Uri.parse(clip.contentUri)))
        player.prepare()
        player.seekTo(0L)
        _positionMs.value = 0L
        player.playWhenReady = true
        _isPlaying.value = true
        startPolling()
    }

    fun stop() {
        stopPolling()
        exoPlayer?.let { player ->
            runCatching {
                player.playWhenReady = false
                player.pause()
            }
        }
        _isPlaying.value = false
        _previewingClipId.value = null
    }

    fun release() {
        if (released) return
        released = true
        stopPolling()
        exoPlayer?.let { player ->
            runCatching { player.removeListener(playerListener) }
            runCatching { player.stop() }
            runCatching { player.release() }
        }
        exoPlayer = null
        loadedUriString = null
        _isPlaying.value = false
        _previewingClipId.value = null
        mainScope.cancel()
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = mainScope.launch {
            while (isActive && !released) {
                val player = exoPlayer ?: break
                if (player.isPlaying) {
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    if (isLoopingSegment && activeRange.durationMs > 0L) {
                        val nowMs = System.currentTimeMillis()
                        if (pos >= activeRange.endMs - LOOP_EDGE_EPSILON_MS &&
                            nowMs - lastLoopSeekWallMs > LOOP_SEEK_COOLDOWN_MS
                        ) {
                            lastLoopSeekWallMs = nowMs
                            player.seekTo(activeRange.startMs)
                            _positionMs.value = activeRange.startMs
                            player.volume = PcmFadeProcessor.previewGainAtPositionMs(
                                positionMs = activeRange.startMs,
                                range = activeRange,
                                fadeInEnabled = activeFadeIn,
                                fadeOutEnabled = activeFadeOut,
                                fadeDurationMs = activeFadeDurationMs
                            )
                        } else {
                            val clampedPos = pos.coerceIn(activeRange.startMs, activeRange.endMs)
                            _positionMs.value = clampedPos
                            player.volume = PcmFadeProcessor.previewGainAtPositionMs(
                                positionMs = clampedPos,
                                range = activeRange,
                                fadeInEnabled = activeFadeIn,
                                fadeOutEnabled = activeFadeOut,
                                fadeDurationMs = activeFadeDurationMs
                            )
                        }
                    } else {
                        _positionMs.value = pos
                    }
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun parseUri(uriString: String): Uri {
        val trimmed = uriString.trim()
        return if (trimmed.startsWith("/")) Uri.fromFile(File(trimmed)) else Uri.parse(trimmed)
    }

    companion object {
        private const val TAG = "RingtonePreviewPlayer"
        private const val POLL_INTERVAL_MS = 33L
        private const val LOOP_EDGE_EPSILON_MS = 35L
        private const val LOOP_SEEK_COOLDOWN_MS = 180L
    }
}
