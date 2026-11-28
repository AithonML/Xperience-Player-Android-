package com.xperience.player.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log

/**
 * Pure state machine for "Pause at 0 Volume" behavior so transitions are deterministic and unit-testable.
 *
 * Rules:
 * - When enabled and volume drops to 0 while playing -> emit [Action.PAUSE] and set `autoPausedByZeroVolume = true`.
 * - When enabled and volume rises > 0 while `autoPausedByZeroVolume == true` -> emit [Action.RESUME] and clear flag.
 * - Any manual play/pause or disabling the feature clears `autoPausedByZeroVolume` so playback never unexpectedly resumes.
 */
class ZeroVolumePauseStateMachine {
    enum class Action { NONE, PAUSE, RESUME }

    var autoPausedByZeroVolume: Boolean = false
        private set

    fun onVolumeChanged(
        volumeLevel: Int,
        isPlaying: Boolean,
        featureEnabled: Boolean
    ): Action {
        if (!featureEnabled) {
            autoPausedByZeroVolume = false
            return Action.NONE
        }
        return when {
            volumeLevel <= 0 && isPlaying -> {
                autoPausedByZeroVolume = true
                Action.PAUSE
            }
            volumeLevel > 0 && autoPausedByZeroVolume && !isPlaying -> {
                autoPausedByZeroVolume = false
                Action.RESUME
            }
            volumeLevel > 0 && autoPausedByZeroVolume && isPlaying -> {
                autoPausedByZeroVolume = false
                Action.NONE
            }
            else -> Action.NONE
        }
    }

    fun onManualPlaybackAction() {
        autoPausedByZeroVolume = false
    }

    fun onFeatureDisabled() {
        autoPausedByZeroVolume = false
    }
}

/**
 * Android system media volume observer (`AudioManager.STREAM_MUSIC`) driving [ZeroVolumePauseStateMachine].
 */
class SystemVolumeObserver(
    private val appContext: Context,
    private val isFeatureEnabled: () -> Boolean,
    private val isCurrentlyPlaying: () -> Boolean,
    private val onAutoPause: () -> Unit,
    private val onAutoResume: () -> Unit
) {
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val stateMachine = ZeroVolumePauseStateMachine()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var registered = false

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == VOLUME_CHANGED_ACTION) {
                val streamType = intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, AudioManager.STREAM_MUSIC)
                if (streamType == AudioManager.STREAM_MUSIC) {
                    handleCurrentSystemVolume()
                }
            }
        }
    }

    private val settingsObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            handleCurrentSystemVolume()
        }
    }

    fun start() {
        if (registered) return
        registered = true
        runCatching {
            val filter = IntentFilter(VOLUME_CHANGED_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(volumeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                appContext.registerReceiver(volumeReceiver, filter)
            }
        }.onFailure { e ->
            Log.w(TAG, "Volume broadcast receiver registration warning: ${e.message}")
        }
        runCatching {
            appContext.contentResolver.registerContentObserver(
                Settings.System.CONTENT_URI,
                true,
                settingsObserver
            )
        }.onFailure { e ->
            Log.w(TAG, "Volume ContentObserver registration warning: ${e.message}")
        }
    }

    fun stop() {
        if (!registered) return
        registered = false
        runCatching { appContext.unregisterReceiver(volumeReceiver) }
        runCatching { appContext.contentResolver.unregisterContentObserver(settingsObserver) }
        stateMachine.onFeatureDisabled()
    }

    fun notifyManualPlaybackAction() {
        stateMachine.onManualPlaybackAction()
    }

    fun notifyFeatureToggled(enabled: Boolean) {
        if (!enabled) {
            stateMachine.onFeatureDisabled()
        } else {
            handleCurrentSystemVolume()
        }
    }

    private fun handleCurrentSystemVolume() {
        val currentVol = runCatching {
            audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 1
        }.getOrDefault(1)

        when (stateMachine.onVolumeChanged(currentVol, isCurrentlyPlaying(), isFeatureEnabled())) {
            ZeroVolumePauseStateMachine.Action.PAUSE -> {
                Log.d(TAG, "System media volume dropped to 0 -> auto-pausing playback")
                onAutoPause()
            }
            ZeroVolumePauseStateMachine.Action.RESUME -> {
                Log.d(TAG, "System media volume rose above 0 -> auto-resuming playback")
                onAutoResume()
            }
            ZeroVolumePauseStateMachine.Action.NONE -> Unit
        }
    }

    companion object {
        private const val TAG = "SystemVolumeObserver"
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        private const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
    }
}
