package com.xperience.player.service.dsp

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class SleepTimerMode {
    OFF,
    DURATION,
    END_OF_TRACK
}

enum class SleepTimerPreset(
    val label: String,
    val shortBadgeLabel: String,
    val minutes: Int,
    val isEndOfTrack: Boolean = false
) {
    MIN_5("5 min", "5m", 5),
    MIN_15("15 min", "15m", 15),
    MIN_30("30 min", "30m", 30),
    MIN_45("45 min", "45m", 45),
    MIN_60("60 min", "60m", 60),
    MIN_90("90 min", "90m", 90),
    END_OF_TRACK("End of Track", "End of Track", 0, isEndOfTrack = true)
}

data class SleepTimerState(
    val isActive: Boolean = false,
    val mode: SleepTimerMode = SleepTimerMode.OFF,
    val remainingMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val activePreset: SleepTimerPreset? = null
) {
    val progressFraction: Float
        get() = when {
            !isActive -> 0f
            mode == SleepTimerMode.END_OF_TRACK -> 1f
            totalDurationMs <= 0L -> 0f
            else -> (remainingMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
        }

    val formattedRemaining: String
        get() = when {
            !isActive -> ""
            mode == SleepTimerMode.END_OF_TRACK -> "End of Track"
            else -> formatCountdownMs(remainingMs)
        }

    companion object {
        fun formatCountdownMs(ms: Long): String {
            val totalSeconds = ((ms + 999L) / 1000L).coerceAtLeast(0L)
            val hours = totalSeconds / 3600L
            val minutes = (totalSeconds % 3600L) / 60L
            val seconds = totalSeconds % 60L
            return if (hours > 0L) {
                "%d:%02d:%02d".format(hours, minutes, seconds)
            } else {
                "%02d:%02d".format(minutes, seconds)
            }
        }
    }
}

/**
 * Modular Music Sleep Timer ("Temporizador de música") service.
 *
 * Provides live countdown state for visual player indicators ([com.xperience.player.ui.player.SleepTimerBadge])
 * and smoothly fades out & pauses playback when the countdown expires or when the current track ends.
 */
class SleepTimerManager private constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _timerState = MutableStateFlow(SleepTimerState())
    val timerState: StateFlow<SleepTimerState> = _timerState.asStateFlow()

    private var countdownJob: Job? = null
    private var targetDeadlineElapsedMs: Long = 0L

    var onPausePlaybackRequested: (() -> Unit)? = null
    var onVolumeMultiplierChanged: ((Float) -> Unit)? = null

    fun startPreset(preset: SleepTimerPreset) {
        if (preset.isEndOfTrack) {
            startEndOfTrackTimer()
        } else {
            startDurationMinutes(preset.minutes, preset)
        }
    }

    fun startDurationMinutes(minutes: Int, preset: SleepTimerPreset? = null) {
        val clampedMinutes = minutes.coerceIn(1, 360)
        val durationMs = clampedMinutes * 60_000L
        countdownJob?.cancel()
        onVolumeMultiplierChanged?.invoke(1.0f)

        targetDeadlineElapsedMs = SystemClock.elapsedRealtime() + durationMs
        _timerState.value = SleepTimerState(
            isActive = true,
            mode = SleepTimerMode.DURATION,
            remainingMs = durationMs,
            totalDurationMs = durationMs,
            activePreset = preset
        )
        launchCountdownLoop()
    }

    fun startEndOfTrackTimer() {
        countdownJob?.cancel()
        onVolumeMultiplierChanged?.invoke(1.0f)
        _timerState.value = SleepTimerState(
            isActive = true,
            mode = SleepTimerMode.END_OF_TRACK,
            remainingMs = 0L,
            totalDurationMs = 0L,
            activePreset = SleepTimerPreset.END_OF_TRACK
        )
    }

    fun addMinutes(extraMinutes: Int = 5) {
        val current = _timerState.value
        val extraMs = extraMinutes.coerceIn(1, 120) * 60_000L
        if (!current.isActive || current.mode == SleepTimerMode.END_OF_TRACK) {
            startDurationMinutes(extraMinutes)
            return
        }
        targetDeadlineElapsedMs += extraMs
        val newRemaining = (targetDeadlineElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(1000L)
        val newTotal = (current.totalDurationMs + extraMs).coerceAtLeast(newRemaining)
        onVolumeMultiplierChanged?.invoke(1.0f)
        _timerState.update {
            it.copy(
                remainingMs = newRemaining,
                totalDurationMs = newTotal,
                activePreset = null
            )
        }
    }

    fun cancelTimer() {
        countdownJob?.cancel()
        countdownJob = null
        onVolumeMultiplierChanged?.invoke(1.0f)
        _timerState.value = SleepTimerState()
    }

    /**
     * Called by [com.xperience.player.service.AudioPlayerService] when a track finishes naturally.
     * Returns `true` if the Sleep Timer is set to [SleepTimerMode.END_OF_TRACK] and playback
     * should pause instead of advancing to the next song.
     */
    fun consumeEndOfTrackShouldPause(): Boolean {
        val state = _timerState.value
        if (state.isActive && state.mode == SleepTimerMode.END_OF_TRACK) {
            cancelTimer()
            onPausePlaybackRequested?.invoke()
            return true
        }
        return false
    }

    private fun launchCountdownLoop() {
        countdownJob?.cancel()
        countdownJob = scope.launch {
            while (isActive) {
                val now = SystemClock.elapsedRealtime()
                val remaining = (targetDeadlineElapsedMs - now).coerceAtLeast(0L)

                if (remaining <= 0L) {
                    onVolumeMultiplierChanged?.invoke(1.0f)
                    _timerState.value = SleepTimerState()
                    onPausePlaybackRequested?.invoke()
                    break
                }

                // Smooth 3-second audio fade-out before pausing
                if (remaining <= FADE_OUT_WINDOW_MS) {
                    val fadeMultiplier = (remaining.toFloat() / FADE_OUT_WINDOW_MS.toFloat()).coerceIn(0.05f, 1.0f)
                    onVolumeMultiplierChanged?.invoke(fadeMultiplier)
                } else {
                    onVolumeMultiplierChanged?.invoke(1.0f)
                }

                _timerState.update { state ->
                    if (!state.isActive || state.mode != SleepTimerMode.DURATION) state
                    else state.copy(remainingMs = remaining)
                }

                delay(250L)
            }
        }
    }

    companion object {
        private const val FADE_OUT_WINDOW_MS = 3_000L

        @Volatile
        private var instance: SleepTimerManager? = null

        @Suppress("UNUSED_PARAMETER")
        fun getInstance(context: Context? = null): SleepTimerManager {
            return instance ?: synchronized(this) {
                instance ?: SleepTimerManager().also { instance = it }
            }
        }
    }
}
