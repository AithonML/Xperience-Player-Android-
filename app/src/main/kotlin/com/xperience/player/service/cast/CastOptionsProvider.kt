package com.xperience.player.service.cast

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.LaunchOptions
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.MediaIntentReceiver
import com.google.android.gms.cast.framework.media.NotificationOptions
import com.xperience.player.MainActivity
import com.xperience.player.R

/**
 * Centralized Chromecast configuration for Xperience Music Player.
 *
 * Swap [RECEIVER_APP_ID] to a registered Google Cast Custom Web Receiver App ID
 * whenever custom TV branding or receiver-side visualizer features are published.
 */
object CastConfig {
    const val RECEIVER_APP_ID: String = CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
    const val PROGRESS_UPDATE_INTERVAL_MS: Long = 500L
    const val PRELOAD_TIME_SECONDS: Double = 15.0
    const val MAX_PREFETCH_AHEAD_TRACKS: Int = 2
    const val DEFAULT_AUDIO_MIME_TYPE: String = "audio/mp4"
}

/**
 * Google Cast SDK [OptionsProvider] registered in `AndroidManifest.xml` via:
 * `com.google.android.gms.cast.framework.OPTIONS_PROVIDER_CLASS_NAME`.
 */
class CastOptionsProvider : OptionsProvider {

    override fun getCastOptions(context: Context): CastOptions {
        val notificationActions = listOf(
            MediaIntentReceiver.ACTION_SKIP_PREV,
            MediaIntentReceiver.ACTION_TOGGLE_PLAYBACK,
            MediaIntentReceiver.ACTION_SKIP_NEXT,
            MediaIntentReceiver.ACTION_STOP_CASTING
        )
        // Compact notification shows Play/Pause (index 1) and Stop Casting (index 3)
        val compactActionIndices = intArrayOf(1, 3)

        val notificationOptions = NotificationOptions.Builder()
            .setActions(notificationActions, compactActionIndices)
            .setSmallIconDrawableResId(R.drawable.ic_cast_notification)
            .setTargetActivityClassName(MainActivity::class.java.name)
            .build()

        val mediaOptions = CastMediaOptions.Builder()
            .setNotificationOptions(notificationOptions)
            .setMediaSessionEnabled(true)
            .build()

        val launchOptions = LaunchOptions.Builder()
            .setAndroidReceiverCompatible(true)
            .build()

        return CastOptions.Builder()
            .setReceiverApplicationId(CastConfig.RECEIVER_APP_ID)
            .setCastMediaOptions(mediaOptions)
            .setLaunchOptions(launchOptions)
            .setEnableReconnectionService(true)
            .setResumeSavedSession(true)
            .setRemoteToLocalEnabled(true)
            .setSessionTransferEnabled(true)
            .setStopReceiverApplicationWhenEndingSession(true)
            .build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
