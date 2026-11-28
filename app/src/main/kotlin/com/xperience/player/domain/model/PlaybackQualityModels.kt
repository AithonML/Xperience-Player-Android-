package com.xperience.player.domain.model

/**
 * Online streaming audio quality tiers shared across Android and Windows.
 */
enum class StreamingAudioQuality(
    val id: String,
    val displayName: String,
    val shortBadge: String,
    val targetBitrateKbps: Int,
    val cacheTag: String
) {
    LOW_64(
        id = "low_64",
        displayName = "Low (64 kbps)",
        shortBadge = "64k",
        targetBitrateKbps = 64,
        cacheTag = "q64"
    ),
    REGULAR_128(
        id = "regular_128",
        displayName = "Regular (128 kbps)",
        shortBadge = "128k",
        targetBitrateKbps = 128,
        cacheTag = "q128"
    ),
    HIGH_160(
        id = "high_160",
        displayName = "High (160 kbps / Max YT Music)",
        shortBadge = "160k+",
        targetBitrateKbps = 160,
        cacheTag = "q160"
    ),
    FLAC_LOSSLESS(
        id = "flac_lossless",
        displayName = "FLAC Lossless",
        shortBadge = "FLAC",
        targetBitrateKbps = 1411,
        cacheTag = "flac"
    );

    companion object {
        fun fromId(id: String?): StreamingAudioQuality {
            return entries.firstOrNull {
                it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true)
            } ?: HIGH_160
        }
    }
}

/**
 * Active network transport classification for quality selection and data usage attribution.
 */
enum class NetworkConnectionType(val id: String) {
    WIFI("wifi"),
    MOBILE("mobile"),
    OTHER("other");

    companion object {
        fun fromId(id: String?): NetworkConnectionType {
            return entries.firstOrNull {
                it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true)
            } ?: WIFI
        }
    }
}

/**
 * Certified actual playback source for Song Information and Data Usage attribution.
 */
enum class PlaybackSourceType(val displayName: String) {
    LOCAL("Local Device"),
    DOWNLOADED_OFFLINE("Downloaded Offline"),
    YOUTUBE_MUSIC("YouTube Music"),
    YOUTUBE_VIDEO("YouTube Video"),
    FLAC_GDSTUDIO("FLAC Lossless (GDStudio)"),
    FLAC_TIDAL("FLAC Lossless (Tidal)"),
    UNKNOWN("Unknown")
}

/**
 * Certified real-time metadata & audio telemetry for the currently playing track.
 */
data class TrackPlaybackInfo(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long = 0L,
    val sourceType: PlaybackSourceType = PlaybackSourceType.UNKNOWN,
    val providerLabel: String = "",
    val requestedQuality: StreamingAudioQuality? = null,
    val actualQualityLabel: String = "",
    val isFlacFallback: Boolean = false,
    val codec: String = "",
    val bitrateKbps: Int = 0,
    val sampleRateHz: Int = 0,
    val channelCount: Int = 0,
    val bitDepth: Int = 0,
    val fileSizeBytes: Long = 0L,
    val filePathOrUri: String = "",
    val isrc: String = "",
    val releaseYear: String = "",
    val genre: String = "",
    val networkType: NetworkConnectionType? = null,
    val servedFromCache: Boolean = false,
    val requestedQualityLabel: String = requestedQuality?.displayName.orEmpty(),
    val outputDeviceLabel: String = "",
    val isAuthenticatedStream: Boolean = false
) {
    val isLossless: Boolean
        get() = codec.equals("FLAC", ignoreCase = true) ||
            codec.equals("ALAC", ignoreCase = true) ||
            codec.equals("PCM", ignoreCase = true) ||
            sourceType == PlaybackSourceType.FLAC_GDSTUDIO ||
            sourceType == PlaybackSourceType.FLAC_TIDAL

    val formattedTechnicalSummary: String
        get() {
            val parts = mutableListOf<String>()
            if (codec.isNotBlank()) parts.add(codec.uppercase())
            if (bitrateKbps > 0) parts.add("$bitrateKbps kbps")
            if (isLossless && bitDepth > 0) parts.add("${bitDepth}-bit")
            if (sampleRateHz > 0) {
                val khz = sampleRateHz / 1000.0
                val formattedKhz = if (sampleRateHz % 1000 == 0) {
                    "${sampleRateHz / 1000} kHz"
                } else {
                    String.format(java.util.Locale.ROOT, "%.1f kHz", khz)
                }
                parts.add(formattedKhz)
            }
            if (channelCount > 0) {
                parts.add(if (channelCount == 1) "Mono" else if (channelCount == 2) "Stereo" else "${channelCount}ch")
            }
            return parts.joinToString(" • ").ifBlank { actualQualityLabel }
        }

    companion object {
        fun buildAccurateYouTubeProviderLabel(
            isVideoFallback: Boolean,
            isAuthenticated: Boolean,
            bitrateKbps: Int
        ): String {
            return when {
                isVideoFallback -> "YouTube Music Video"
                isAuthenticated && bitrateKbps >= 192 -> "YouTube Music Premium"
                isAuthenticated -> "YouTube Music (Signed-In)"
                else -> "YouTube Music"
            }
        }

        fun buildAccurateQualityLabel(
            codec: String,
            bitrateKbps: Int,
            sampleRateHz: Int,
            bitDepth: Int,
            isLossless: Boolean,
            isFlacFallback: Boolean = false
        ): String {
            val parts = mutableListOf<String>()
            val cleanCodec = codec.trim()
            if (cleanCodec.isNotBlank()) {
                parts.add(if (isLossless && cleanCodec.equals("FLAC", ignoreCase = true)) "FLAC Lossless" else cleanCodec)
            }
            if (isLossless && bitDepth > 0 && sampleRateHz > 0) {
                val khz = if (sampleRateHz % 1000 == 0) {
                    "${sampleRateHz / 1000} kHz"
                } else {
                    String.format(java.util.Locale.ROOT, "%.1f kHz", sampleRateHz / 1000.0)
                }
                parts.add("${bitDepth}-bit / $khz")
            } else if (sampleRateHz > 0) {
                val khz = if (sampleRateHz % 1000 == 0) {
                    "${sampleRateHz / 1000} kHz"
                } else {
                    String.format(java.util.Locale.ROOT, "%.1f kHz", sampleRateHz / 1000.0)
                }
                parts.add(khz)
            }
            if (bitrateKbps > 0) {
                parts.add("$bitrateKbps kbps")
            }
            val joined = parts.joinToString(" • ")
            return if (isFlacFallback && joined.isNotBlank()) "$joined (YT Music Fallback)" else joined
        }
    }
}
