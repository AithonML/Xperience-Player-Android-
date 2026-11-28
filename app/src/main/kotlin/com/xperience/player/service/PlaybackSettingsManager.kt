package com.xperience.player.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.xperience.player.domain.model.NetworkConnectionType
import com.xperience.player.domain.model.StreamingAudioQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PlaybackPreferences(
    val wifiQuality: StreamingAudioQuality = StreamingAudioQuality.HIGH_160,
    val mobileDataQuality: StreamingAudioQuality = StreamingAudioQuality.REGULAR_128,
    val gaplessPreloadEnabled: Boolean = true,
    val pauseAtZeroVolumeEnabled: Boolean = false
)

/**
 * Centralized SharedPreferences-backed manager for streaming quality (WiFi vs Mobile Data),
 * gapless next-song preloading, and pause-at-zero-volume playback behavior.
 */
class PlaybackSettingsManager private constructor(
    private val appContext: Context
) {
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _preferences = MutableStateFlow(loadPreferences())
    val preferences: StateFlow<PlaybackPreferences> = _preferences.asStateFlow()

    private fun loadPreferences(): PlaybackPreferences {
        val wifiId = prefs.getString(KEY_WIFI_QUALITY, StreamingAudioQuality.HIGH_160.id)
        val mobileId = prefs.getString(KEY_MOBILE_QUALITY, StreamingAudioQuality.REGULAR_128.id)
        val gapless = prefs.getBoolean(KEY_GAPLESS_PRELOAD, true)
        val pauseZero = prefs.getBoolean(KEY_PAUSE_AT_ZERO_VOLUME, false)
        return PlaybackPreferences(
            wifiQuality = StreamingAudioQuality.fromId(wifiId),
            mobileDataQuality = StreamingAudioQuality.fromId(mobileId),
            gaplessPreloadEnabled = gapless,
            pauseAtZeroVolumeEnabled = pauseZero
        )
    }

    fun setWifiQuality(quality: StreamingAudioQuality) {
        prefs.edit().putString(KEY_WIFI_QUALITY, quality.id).apply()
        _preferences.update { it.copy(wifiQuality = quality) }
    }

    fun setMobileDataQuality(quality: StreamingAudioQuality) {
        prefs.edit().putString(KEY_MOBILE_QUALITY, quality.id).apply()
        _preferences.update { it.copy(mobileDataQuality = quality) }
    }

    fun setGaplessPreloadEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GAPLESS_PRELOAD, enabled).apply()
        _preferences.update { it.copy(gaplessPreloadEnabled = enabled) }
    }

    fun setPauseAtZeroVolumeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PAUSE_AT_ZERO_VOLUME, enabled).apply()
        _preferences.update { it.copy(pauseAtZeroVolumeEnabled = enabled) }
    }

    /**
     * Detects active transport (WiFi vs Mobile Cellular vs Other) using [ConnectivityManager].
     */
    fun getActiveNetworkType(): NetworkConnectionType {
        return runCatching {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return NetworkConnectionType.WIFI
            val network = cm.activeNetwork ?: return NetworkConnectionType.OTHER
            val caps = cm.getNetworkCapabilities(network) ?: return NetworkConnectionType.OTHER
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkConnectionType.WIFI
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkConnectionType.MOBILE
                else -> NetworkConnectionType.OTHER
            }
        }.getOrDefault(NetworkConnectionType.WIFI)
    }

    /**
     * Returns the effective streaming quality tier for the currently active network connection.
     */
    fun getEffectiveStreamingQuality(
        networkType: NetworkConnectionType = getActiveNetworkType()
    ): StreamingAudioQuality {
        val current = _preferences.value
        return when (networkType) {
            NetworkConnectionType.MOBILE -> current.mobileDataQuality
            NetworkConnectionType.WIFI,
            NetworkConnectionType.OTHER -> current.wifiQuality
        }
    }

    companion object {
        private const val PREFS_NAME = "xperience_playback_settings_prefs"
        private const val KEY_WIFI_QUALITY = "wifi_streaming_quality"
        private const val KEY_MOBILE_QUALITY = "mobile_streaming_quality"
        private const val KEY_GAPLESS_PRELOAD = "gapless_preload_enabled"
        private const val KEY_PAUSE_AT_ZERO_VOLUME = "pause_at_zero_volume_enabled"

        @Volatile
        private var instance: PlaybackSettingsManager? = null

        fun getInstance(context: Context): PlaybackSettingsManager {
            return instance ?: synchronized(this) {
                instance ?: PlaybackSettingsManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
