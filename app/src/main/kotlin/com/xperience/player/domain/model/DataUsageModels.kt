package com.xperience.player.domain.model

/**
 * Granular network byte categories for the Advanced Data Usage breakdown.
 */
enum class DataUsageCategory(val id: String) {
    AUDIO("audio"),
    ARTWORK("artwork"),
    LYRICS("lyrics"),
    CANVAS("canvas"),
    OTHER("other")
}

enum class DataUsageChartGranularity(val id: String) {
    DAY("day"),
    WEEK("week"),
    MONTH("month")
}

enum class DataUsageTimeFilter(val id: String) {
    TODAY("today"),
    THIS_WEEK("this_week"),
    THIS_MONTH("this_month"),
    CUSTOM_RANGE("custom_range"),
    ALL_TIME("all_time")
}

enum class DataUsageSortOrder(val id: String) {
    HIGHEST_USAGE("highest_usage"),
    RECENT_DATE("recent_date"),
    ALPHABETICAL("alphabetical")
}

/**
 * Per-track network usage accumulated within a single calendar day.
 */
data class DataUsageDailyTrackRecord(
    val trackKey: String,
    val wifiAudioBytes: Long = 0L,
    val wifiArtworkBytes: Long = 0L,
    val wifiLyricsBytes: Long = 0L,
    val wifiCanvasBytes: Long = 0L,
    val wifiOtherBytes: Long = 0L,
    val mobileAudioBytes: Long = 0L,
    val mobileArtworkBytes: Long = 0L,
    val mobileLyricsBytes: Long = 0L,
    val mobileCanvasBytes: Long = 0L,
    val mobileOtherBytes: Long = 0L,
    val streamPlayCount: Int = 0,
    val lastPlayedAtMs: Long = 0L
) {
    val totalWifiBytes: Long
        get() = wifiAudioBytes + wifiArtworkBytes + wifiLyricsBytes + wifiCanvasBytes + wifiOtherBytes

    val totalMobileBytes: Long
        get() = mobileAudioBytes + mobileArtworkBytes + mobileLyricsBytes + mobileCanvasBytes + mobileOtherBytes

    val totalNetworkBytes: Long
        get() = totalWifiBytes + totalMobileBytes
}

/**
 * Daily aggregate consumption bucket keyed by local [epochDay].
 */
data class DataUsageDailyBucket(
    val epochDay: Long,
    val wifiBytes: Long = 0L,
    val mobileBytes: Long = 0L,
    val trackRecords: Map<String, DataUsageDailyTrackRecord> = emptyMap()
) {
    val totalBytes: Long
        get() = wifiBytes + mobileBytes
}

/**
 * Single bar point rendered by the Data Usage stacked bar chart.
 */
data class DataUsageChartPoint(
    val label: String,
    val subLabel: String = "",
    val startEpochDay: Long,
    val endEpochDay: Long,
    val wifiBytes: Long,
    val mobileBytes: Long
) {
    val totalBytes: Long
        get() = wifiBytes + mobileBytes
}

/**
 * Reset period for data usage limits and statistics.
 */
enum class DataLimitPeriod(val id: String, val displayName: String) {
    DAILY("daily", "Daily"),
    WEEKLY("weekly", "Weekly"),
    MONTHLY("monthly", "Monthly"),
    TOTAL("total", "All Time");

    companion object {
        fun fromId(id: String?): DataLimitPeriod {
            return entries.firstOrNull {
                it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true)
            } ?: MONTHLY
        }
    }
}

/**
 * Persisted data usage restriction settings (WiFi & Mobile Data separated).
 */
data class DataUsageLimitConfig(
    val wifiLimitEnabled: Boolean = false,
    val wifiLimitBytes: Long = DEFAULT_WIFI_LIMIT_BYTES,
    val mobileLimitEnabled: Boolean = false,
    val mobileLimitBytes: Long = DEFAULT_MOBILE_LIMIT_BYTES,
    val period: DataLimitPeriod = DataLimitPeriod.MONTHLY,
    val periodStartMs: Long = 0L
) {
    companion object {
        const val DEFAULT_WIFI_LIMIT_BYTES: Long = 2L * 1024L * 1024L * 1024L // 2 GB
        const val DEFAULT_MOBILE_LIMIT_BYTES: Long = 500L * 1024L * 1024L // 500 MB
    }
}

/**
 * Compact normalized per-track data usage record stored permanently without heavy metadata blobs.
 * [trackKey] is normalized as "ytm:<videoId>" for online tracks or "local:<stableHash>" for local files.
 */
data class TrackDataUsageEntry(
    val trackKey: String,
    val trackId: String,
    val title: String,
    val artist: String,
    val isLocal: Boolean = false,
    val isDownloaded: Boolean = false,
    val streamPlayCount: Int = 0,
    val lastPlayedAtMs: Long = 0L,
    val lastQualityLabel: String = "",
    val lastSourceLabel: String = "",
    val wifiAudioBytes: Long = 0L,
    val wifiArtworkBytes: Long = 0L,
    val wifiLyricsBytes: Long = 0L,
    val wifiCanvasBytes: Long = 0L,
    val wifiOtherBytes: Long = 0L,
    val mobileAudioBytes: Long = 0L,
    val mobileArtworkBytes: Long = 0L,
    val mobileLyricsBytes: Long = 0L,
    val mobileCanvasBytes: Long = 0L,
    val mobileOtherBytes: Long = 0L
) {
    val totalWifiBytes: Long
        get() = wifiAudioBytes + wifiArtworkBytes + wifiLyricsBytes + wifiCanvasBytes + wifiOtherBytes

    val totalMobileBytes: Long
        get() = mobileAudioBytes + mobileArtworkBytes + mobileLyricsBytes + mobileCanvasBytes + mobileOtherBytes

    val totalNetworkBytes: Long
        get() = totalWifiBytes + totalMobileBytes

    val totalAudioBytes: Long
        get() = wifiAudioBytes + mobileAudioBytes

    val totalArtworkBytes: Long
        get() = wifiArtworkBytes + mobileArtworkBytes

    val totalLyricsBytes: Long
        get() = wifiLyricsBytes + mobileLyricsBytes

    val totalCanvasBytes: Long
        get() = wifiCanvasBytes + mobileCanvasBytes

    val totalOtherBytes: Long
        get() = wifiOtherBytes + mobileOtherBytes

    /**
     * Local tracks are included in Data Usage analysis ONLY if they consumed external network data
     * (e.g., fetched external LRC lyrics, Apple Canvas, or cover art).
     */
    fun shouldAppearInAnalysis(): Boolean {
        return totalNetworkBytes > 0L
    }

    /**
     * Smart download suggestion eligibility: online streamed track, not local/downloaded, played >= 2 times.
     */
    fun isDownloadSuggestionCandidate(): Boolean {
        return !isLocal && !isDownloaded && streamPlayCount >= DOWNLOAD_SUGGESTION_MIN_PLAYS
    }

    companion object {
        const val DOWNLOAD_SUGGESTION_MIN_PLAYS = 2
    }
}

/**
 * Aggregated UI snapshot for Data Usage statistics, limit status, detailed analysis, and smart suggestions.
 */
data class DataUsageSummary(
    val config: DataUsageLimitConfig = DataUsageLimitConfig(),
    val periodWifiBytes: Long = 0L,
    val periodMobileBytes: Long = 0L,
    val totalWifiBytes: Long = 0L,
    val totalMobileBytes: Long = 0L,
    val totalAudioBytes: Long = 0L,
    val totalArtworkBytes: Long = 0L,
    val totalLyricsBytes: Long = 0L,
    val totalCanvasBytes: Long = 0L,
    val totalOtherBytes: Long = 0L,
    val trackEntries: List<TrackDataUsageEntry> = emptyList(),
    val downloadSuggestions: List<TrackDataUsageEntry> = emptyList(),
    val dailyBuckets: List<DataUsageDailyBucket> = emptyList()
) {
    val totalAllBytes: Long
        get() = totalWifiBytes + totalMobileBytes

    val periodAllBytes: Long
        get() = periodWifiBytes + periodMobileBytes

    val wifiUsageFraction: Float
        get() = if (config.wifiLimitEnabled && config.wifiLimitBytes > 0L) {
            (periodWifiBytes.toFloat() / config.wifiLimitBytes.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val mobileUsageFraction: Float
        get() = if (config.mobileLimitEnabled && config.mobileLimitBytes > 0L) {
            (periodMobileBytes.toFloat() / config.mobileLimitBytes.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val isWifiLimitExceeded: Boolean
        get() = config.wifiLimitEnabled && config.wifiLimitBytes > 0L && periodWifiBytes >= config.wifiLimitBytes

    val isMobileLimitExceeded: Boolean
        get() = config.mobileLimitEnabled && config.mobileLimitBytes > 0L && periodMobileBytes >= config.mobileLimitBytes
}

/**
 * Event emitted when playback is automatically paused due to exceeding a configured data limit.
 */
data class DataLimitReachedEvent(
    val networkType: NetworkConnectionType,
    val usedBytes: Long,
    val limitBytes: Long,
    val period: DataLimitPeriod,
    val timestampMs: Long = System.currentTimeMillis()
)
