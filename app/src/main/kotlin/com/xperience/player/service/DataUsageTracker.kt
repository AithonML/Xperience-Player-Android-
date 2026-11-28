package com.xperience.player.service

import android.content.Context
import android.util.Log
import com.xperience.player.domain.model.DataLimitPeriod
import com.xperience.player.domain.model.DataLimitReachedEvent
import com.xperience.player.domain.model.DataUsageCategory
import com.xperience.player.domain.model.DataUsageChartGranularity
import com.xperience.player.domain.model.DataUsageChartPoint
import com.xperience.player.domain.model.DataUsageDailyBucket
import com.xperience.player.domain.model.DataUsageDailyTrackRecord
import com.xperience.player.domain.model.DataUsageLimitConfig
import com.xperience.player.domain.model.DataUsageSortOrder
import com.xperience.player.domain.model.DataUsageSummary
import com.xperience.player.domain.model.DataUsageTimeFilter
import com.xperience.player.domain.model.NetworkConnectionType
import com.xperience.player.domain.model.TrackDataUsageEntry
import com.xperience.player.domain.model.TrackItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Calendar
import java.util.Locale

/**
 * Lightweight, permanent network data usage tracker and limit enforcer.
 *
 * Persists compact normalized per-track IDs + integer byte counters to a small JSON file
 * (`data_usage_v1.json`) without duplicating heavy metadata or blobs, and enforces
 * separate WiFi and Mobile Data limits with automatic period rollover.
 */
class DataUsageTracker private constructor(
    private val appContext: Context,
    private val storageFile: File,
    private val clockMs: () -> Long = System::currentTimeMillis
) {
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var config: DataUsageLimitConfig = loadConfig()
    private var periodWifiBytes: Long = 0L
    private var periodMobileBytes: Long = 0L
    private val entries = LinkedHashMap<String, TrackDataUsageEntry>()
    private val dailyBuckets = LinkedHashMap<Long, DataUsageDailyBucket>()

    @Volatile
    private var activeAttributionTrack: TrackAttributionContext? = null

    private var lastWifiLimitNotifiedPeriodStart: Long = -1L
    private var lastMobileLimitNotifiedPeriodStart: Long = -1L

    private val _summary = MutableStateFlow(DataUsageSummary(config = config))
    val summary: StateFlow<DataUsageSummary> = _summary.asStateFlow()

    private val _limitReachedEvents = MutableSharedFlow<DataLimitReachedEvent>(extraBufferCapacity = 4)
    val limitReachedEvents: SharedFlow<DataLimitReachedEvent> = _limitReachedEvents.asSharedFlow()

    data class TrackAttributionContext(
        val trackKey: String,
        val trackId: String,
        val title: String,
        val artist: String,
        val isLocal: Boolean,
        val isDownloaded: Boolean,
        val normalizedArtworkKey: String = ""
    )

    init {
        loadFromDisk()
        synchronized(lock) {
            rolloverPeriodIfNeededLocked(clockMs())
            publishSummaryLocked()
        }
    }

    fun setActiveTrackContext(track: TrackItem?, isLocal: Boolean, isDownloaded: Boolean = false) {
        activeAttributionTrack = if (track == null) {
            null
        } else {
            val artKey = track.artworkUri
                ?.takeIf { it.isNotBlank() }
                ?.let { MediaNetworkClients.ArtworkCacheKeyNormalizer.cacheKey(it) }
                .orEmpty()
            TrackAttributionContext(
                trackKey = normalizeTrackKey(track, isLocal),
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                isLocal = isLocal,
                isDownloaded = isDownloaded,
                normalizedArtworkKey = artKey
            )
        }
    }

    fun getActiveTrackContext(): TrackAttributionContext? = activeAttributionTrack

    fun updateLimitConfig(newConfig: DataUsageLimitConfig) {
        synchronized(lock) {
            val now = clockMs()
            val periodChanged = newConfig.period != config.period
            val effectiveStart = if (newConfig.periodStartMs > 0L && !periodChanged) {
                newConfig.periodStartMs
            } else {
                computePeriodStartMs(newConfig.period, now)
            }
            config = newConfig.copy(periodStartMs = effectiveStart)
            if (periodChanged) {
                lastWifiLimitNotifiedPeriodStart = -1L
                lastMobileLimitNotifiedPeriodStart = -1L
            }
            saveConfigLocked(config)
            persistToDiskAsync()
            publishSummaryLocked()
        }
    }

    fun resetPeriodUsage() {
        synchronized(lock) {
            val now = clockMs()
            periodWifiBytes = 0L
            periodMobileBytes = 0L
            config = config.copy(periodStartMs = now)
            lastWifiLimitNotifiedPeriodStart = -1L
            lastMobileLimitNotifiedPeriodStart = -1L
            saveConfigLocked(config)
            persistToDiskAsync()
            publishSummaryLocked()
        }
    }

    fun clearAllHistory() {
        synchronized(lock) {
            entries.clear()
            dailyBuckets.clear()
            periodWifiBytes = 0L
            periodMobileBytes = 0L
            config = config.copy(periodStartMs = computePeriodStartMs(config.period, clockMs()))
            lastWifiLimitNotifiedPeriodStart = -1L
            lastMobileLimitNotifiedPeriodStart = -1L
            saveConfigLocked(config)
            persistToDiskAsync()
            publishSummaryLocked()
        }
    }

    fun markTrackDownloaded(track: TrackItem) {
        val key = normalizeTrackKey(track, isLocal = false)
        synchronized(lock) {
            val existing = entries[key] ?: return
            if (!existing.isDownloaded) {
                entries[key] = existing.copy(isDownloaded = true)
                persistToDiskAsync()
                publishSummaryLocked()
            }
        }
    }

    fun isLimitExceeded(networkType: NetworkConnectionType): Boolean {
        synchronized(lock) {
            rolloverPeriodIfNeededLocked(clockMs())
            return when (networkType) {
                NetworkConnectionType.MOBILE ->
                    config.mobileLimitEnabled && config.mobileLimitBytes > 0L && periodMobileBytes >= config.mobileLimitBytes
                NetworkConnectionType.WIFI,
                NetworkConnectionType.OTHER ->
                    config.wifiLimitEnabled && config.wifiLimitBytes > 0L && periodWifiBytes >= config.wifiLimitBytes
            }
        }
    }

    fun getLimitBytesFor(networkType: NetworkConnectionType): Long {
        synchronized(lock) {
            return when (networkType) {
                NetworkConnectionType.MOBILE -> config.mobileLimitBytes
                NetworkConnectionType.WIFI,
                NetworkConnectionType.OTHER -> config.wifiLimitBytes
            }
        }
    }

    fun getPeriodUsedBytesFor(networkType: NetworkConnectionType): Long {
        synchronized(lock) {
            rolloverPeriodIfNeededLocked(clockMs())
            return when (networkType) {
                NetworkConnectionType.MOBILE -> periodMobileBytes
                NetworkConnectionType.WIFI,
                NetworkConnectionType.OTHER -> periodWifiBytes
            }
        }
    }

    /**
     * Records a streamed playback event (increments `streamPlayCount` for smart download suggestions).
     */
    fun recordStreamPlay(
        track: TrackItem,
        isLocal: Boolean,
        isDownloaded: Boolean,
        qualityLabel: String,
        sourceLabel: String
    ) {
        if (isLocal || isDownloaded) return
        val key = normalizeTrackKey(track, isLocal = false)
        val now = clockMs()
        synchronized(lock) {
            val existing = entries[key] ?: TrackDataUsageEntry(
                trackKey = key,
                trackId = track.id,
                title = track.title.take(120),
                artist = track.artist.take(120),
                isLocal = false,
                isDownloaded = false
            )
            entries[key] = existing.copy(
                title = track.title.ifBlank { existing.title }.take(120),
                artist = track.artist.ifBlank { existing.artist }.take(120),
                streamPlayCount = existing.streamPlayCount + 1,
                lastPlayedAtMs = now,
                lastQualityLabel = qualityLabel.ifBlank { existing.lastQualityLabel },
                lastSourceLabel = sourceLabel.ifBlank { existing.lastSourceLabel }
            )
            recordDailyStreamPlayLocked(epochDay = epochDayFromMillis(now), trackKey = key, nowMs = now)
            trimEntriesLocked()
            trimDailyBucketsLocked()
            persistToDiskAsync()
            publishSummaryLocked()
        }
    }

    /**
     * Records external network bytes consumed for a track (or currently active track if [track] is null).
     * Emits [DataLimitReachedEvent] if a configured WiFi or Mobile Data limit is crossed.
     */
    fun recordNetworkBytes(
        bytes: Long,
        category: DataUsageCategory,
        networkType: NetworkConnectionType,
        track: TrackItem? = null,
        isLocal: Boolean? = null,
        isDownloaded: Boolean = false,
        qualityLabel: String = "",
        sourceLabel: String = ""
    ) {
        if (bytes <= 0L) return
        val active = activeAttributionTrack
        val effectiveTrackId = track?.id ?: active?.trackId ?: return
        val effectiveTitle = track?.title ?: active?.title ?: "Unknown Track"
        val effectiveArtist = track?.artist ?: active?.artist ?: "Unknown Artist"
        val effectiveIsLocal = isLocal ?: active?.isLocal ?: false
        val effectiveIsDownloaded = isDownloaded || (active?.isDownloaded == true)
        val key = if (track != null) {
            normalizeTrackKey(track, effectiveIsLocal)
        } else {
            active?.trackKey ?: normalizeTrackKey(effectiveTrackId, effectiveTitle, effectiveArtist, effectiveIsLocal)
        }

        val now = clockMs()
        var triggeredEvent: DataLimitReachedEvent? = null

        synchronized(lock) {
            rolloverPeriodIfNeededLocked(now)

            when (networkType) {
                NetworkConnectionType.MOBILE -> periodMobileBytes += bytes
                NetworkConnectionType.WIFI,
                NetworkConnectionType.OTHER -> periodWifiBytes += bytes
            }

            val existing = entries[key] ?: TrackDataUsageEntry(
                trackKey = key,
                trackId = effectiveTrackId,
                title = effectiveTitle.take(120),
                artist = effectiveArtist.take(120),
                isLocal = effectiveIsLocal,
                isDownloaded = effectiveIsDownloaded
            )

            val updated = when (networkType) {
                NetworkConnectionType.MOBILE -> when (category) {
                    DataUsageCategory.AUDIO -> existing.copy(mobileAudioBytes = existing.mobileAudioBytes + bytes)
                    DataUsageCategory.ARTWORK -> existing.copy(mobileArtworkBytes = existing.mobileArtworkBytes + bytes)
                    DataUsageCategory.LYRICS -> existing.copy(mobileLyricsBytes = existing.mobileLyricsBytes + bytes)
                    DataUsageCategory.CANVAS -> existing.copy(mobileCanvasBytes = existing.mobileCanvasBytes + bytes)
                    DataUsageCategory.OTHER -> existing.copy(mobileOtherBytes = existing.mobileOtherBytes + bytes)
                }
                NetworkConnectionType.WIFI,
                NetworkConnectionType.OTHER -> when (category) {
                    DataUsageCategory.AUDIO -> existing.copy(wifiAudioBytes = existing.wifiAudioBytes + bytes)
                    DataUsageCategory.ARTWORK -> existing.copy(wifiArtworkBytes = existing.wifiArtworkBytes + bytes)
                    DataUsageCategory.LYRICS -> existing.copy(wifiLyricsBytes = existing.wifiLyricsBytes + bytes)
                    DataUsageCategory.CANVAS -> existing.copy(wifiCanvasBytes = existing.wifiCanvasBytes + bytes)
                    DataUsageCategory.OTHER -> existing.copy(wifiOtherBytes = existing.wifiOtherBytes + bytes)
                }
            }.copy(
                title = effectiveTitle.ifBlank { existing.title }.take(120),
                artist = effectiveArtist.ifBlank { existing.artist }.take(120),
                lastPlayedAtMs = maxOf(existing.lastPlayedAtMs, now),
                lastQualityLabel = qualityLabel.ifBlank { existing.lastQualityLabel },
                lastSourceLabel = sourceLabel.ifBlank { existing.lastSourceLabel }
            )

            entries[key] = updated
            recordDailyNetworkBytesLocked(
                epochDay = epochDayFromMillis(now),
                trackKey = key,
                bytes = bytes,
                category = category,
                networkType = networkType,
                nowMs = now
            )
            trimEntriesLocked()
            trimDailyBucketsLocked()

            // Check limit threshold crossing
            if (networkType == NetworkConnectionType.MOBILE &&
                config.mobileLimitEnabled &&
                config.mobileLimitBytes > 0L &&
                periodMobileBytes >= config.mobileLimitBytes &&
                lastMobileLimitNotifiedPeriodStart != config.periodStartMs
            ) {
                lastMobileLimitNotifiedPeriodStart = config.periodStartMs
                triggeredEvent = DataLimitReachedEvent(
                    networkType = NetworkConnectionType.MOBILE,
                    usedBytes = periodMobileBytes,
                    limitBytes = config.mobileLimitBytes,
                    period = config.period,
                    timestampMs = now
                )
            } else if (networkType != NetworkConnectionType.MOBILE &&
                config.wifiLimitEnabled &&
                config.wifiLimitBytes > 0L &&
                periodWifiBytes >= config.wifiLimitBytes &&
                lastWifiLimitNotifiedPeriodStart != config.periodStartMs
            ) {
                lastWifiLimitNotifiedPeriodStart = config.periodStartMs
                triggeredEvent = DataLimitReachedEvent(
                    networkType = NetworkConnectionType.WIFI,
                    usedBytes = periodWifiBytes,
                    limitBytes = config.wifiLimitBytes,
                    period = config.period,
                    timestampMs = now
                )
            }

            persistToDiskAsync()
            publishSummaryLocked()
        }

        triggeredEvent?.let { _limitReachedEvents.tryEmit(it) }
    }

    fun emitLimitReachedNow(networkType: NetworkConnectionType) {
        val event = synchronized(lock) {
            rolloverPeriodIfNeededLocked(clockMs())
            val used = if (networkType == NetworkConnectionType.MOBILE) periodMobileBytes else periodWifiBytes
            val limit = if (networkType == NetworkConnectionType.MOBILE) config.mobileLimitBytes else config.wifiLimitBytes
            DataLimitReachedEvent(
                networkType = networkType,
                usedBytes = used,
                limitBytes = limit,
                period = config.period,
                timestampMs = clockMs()
            )
        }
        _limitReachedEvents.tryEmit(event)
    }

    private fun rolloverPeriodIfNeededLocked(nowMs: Long) {
        if (config.period == DataLimitPeriod.TOTAL) return
        val expectedStart = computePeriodStartMs(config.period, nowMs)
        if (config.periodStartMs <= 0L) {
            config = config.copy(periodStartMs = expectedStart)
            saveConfigLocked(config)
            return
        }
        if (expectedStart > config.periodStartMs) {
            periodWifiBytes = 0L
            periodMobileBytes = 0L
            config = config.copy(periodStartMs = expectedStart)
            lastWifiLimitNotifiedPeriodStart = -1L
            lastMobileLimitNotifiedPeriodStart = -1L
            saveConfigLocked(config)
        }
    }

    private fun trimEntriesLocked() {
        if (entries.size <= MAX_PERSISTED_TRACKS) return
        val sortedKeys = entries.values
            .sortedBy { it.lastPlayedAtMs }
            .take(entries.size - MAX_PERSISTED_TRACKS)
            .map { it.trackKey }
        for (k in sortedKeys) {
            entries.remove(k)
        }
    }

    private fun trimDailyBucketsLocked() {
        if (dailyBuckets.size <= MAX_PERSISTED_DAYS) return
        val oldestDays = dailyBuckets.keys.sorted().take(dailyBuckets.size - MAX_PERSISTED_DAYS)
        oldestDays.forEach { dailyBuckets.remove(it) }
    }

    private fun recordDailyStreamPlayLocked(epochDay: Long, trackKey: String, nowMs: Long) {
        val bucket = dailyBuckets[epochDay] ?: DataUsageDailyBucket(epochDay = epochDay)
        val existingRecord = bucket.trackRecords[trackKey] ?: DataUsageDailyTrackRecord(trackKey = trackKey)
        val updatedRecord = existingRecord.copy(
            streamPlayCount = existingRecord.streamPlayCount + 1,
            lastPlayedAtMs = maxOf(existingRecord.lastPlayedAtMs, nowMs)
        )
        val updatedRecords = HashMap(bucket.trackRecords).apply { put(trackKey, updatedRecord) }
        dailyBuckets[epochDay] = bucket.copy(trackRecords = updatedRecords)
    }

    private fun recordDailyNetworkBytesLocked(
        epochDay: Long,
        trackKey: String,
        bytes: Long,
        category: DataUsageCategory,
        networkType: NetworkConnectionType,
        nowMs: Long
    ) {
        val bucket = dailyBuckets[epochDay] ?: DataUsageDailyBucket(epochDay = epochDay)
        val existingRecord = bucket.trackRecords[trackKey] ?: DataUsageDailyTrackRecord(trackKey = trackKey)
        val updatedRecord = when (networkType) {
            NetworkConnectionType.MOBILE -> when (category) {
                DataUsageCategory.AUDIO -> existingRecord.copy(mobileAudioBytes = existingRecord.mobileAudioBytes + bytes)
                DataUsageCategory.ARTWORK -> existingRecord.copy(mobileArtworkBytes = existingRecord.mobileArtworkBytes + bytes)
                DataUsageCategory.LYRICS -> existingRecord.copy(mobileLyricsBytes = existingRecord.mobileLyricsBytes + bytes)
                DataUsageCategory.CANVAS -> existingRecord.copy(mobileCanvasBytes = existingRecord.mobileCanvasBytes + bytes)
                DataUsageCategory.OTHER -> existingRecord.copy(mobileOtherBytes = existingRecord.mobileOtherBytes + bytes)
            }
            NetworkConnectionType.WIFI,
            NetworkConnectionType.OTHER -> when (category) {
                DataUsageCategory.AUDIO -> existingRecord.copy(wifiAudioBytes = existingRecord.wifiAudioBytes + bytes)
                DataUsageCategory.ARTWORK -> existingRecord.copy(wifiArtworkBytes = existingRecord.wifiArtworkBytes + bytes)
                DataUsageCategory.LYRICS -> existingRecord.copy(wifiLyricsBytes = existingRecord.wifiLyricsBytes + bytes)
                DataUsageCategory.CANVAS -> existingRecord.copy(wifiCanvasBytes = existingRecord.wifiCanvasBytes + bytes)
                DataUsageCategory.OTHER -> existingRecord.copy(wifiOtherBytes = existingRecord.wifiOtherBytes + bytes)
            }
        }.copy(lastPlayedAtMs = maxOf(existingRecord.lastPlayedAtMs, nowMs))

        val updatedRecords = HashMap(bucket.trackRecords).apply { put(trackKey, updatedRecord) }
        dailyBuckets[epochDay] = when (networkType) {
            NetworkConnectionType.MOBILE -> bucket.copy(
                mobileBytes = bucket.mobileBytes + bytes,
                trackRecords = updatedRecords
            )
            NetworkConnectionType.WIFI,
            NetworkConnectionType.OTHER -> bucket.copy(
                wifiBytes = bucket.wifiBytes + bytes,
                trackRecords = updatedRecords
            )
        }
    }

    private fun publishSummaryLocked() {
        val allEntries = entries.values.toList()
        val analysisEntries = allEntries
            .filter { it.shouldAppearInAnalysis() }
            .sortedByDescending { it.totalNetworkBytes }

        val suggestions = allEntries
            .filter { it.isDownloadSuggestionCandidate() }
            .sortedWith(
                compareByDescending<TrackDataUsageEntry> { it.streamPlayCount }
                    .thenByDescending { it.totalNetworkBytes }
            )
            .take(25)

        var totalWifi = 0L
        var totalMobile = 0L
        var totalAudio = 0L
        var totalArt = 0L
        var totalLyrics = 0L
        var totalCanvas = 0L
        var totalOther = 0L

        for (e in analysisEntries) {
            totalWifi += e.totalWifiBytes
            totalMobile += e.totalMobileBytes
            totalAudio += e.totalAudioBytes
            totalArt += e.totalArtworkBytes
            totalLyrics += e.totalLyricsBytes
            totalCanvas += e.totalCanvasBytes
            totalOther += e.totalOtherBytes
        }

        _summary.value = DataUsageSummary(
            config = config,
            periodWifiBytes = periodWifiBytes,
            periodMobileBytes = periodMobileBytes,
            totalWifiBytes = totalWifi,
            totalMobileBytes = totalMobile,
            totalAudioBytes = totalAudio,
            totalArtworkBytes = totalArt,
            totalLyricsBytes = totalLyrics,
            totalCanvasBytes = totalCanvas,
            totalOtherBytes = totalOther,
            trackEntries = analysisEntries,
            downloadSuggestions = suggestions,
            dailyBuckets = dailyBuckets.values.sortedBy { it.epochDay }
        )
    }

    private fun loadConfig(): DataUsageLimitConfig {
        return DataUsageLimitConfig(
            wifiLimitEnabled = prefs.getBoolean(KEY_WIFI_LIMIT_ENABLED, false),
            wifiLimitBytes = prefs.getLong(KEY_WIFI_LIMIT_BYTES, DataUsageLimitConfig.DEFAULT_WIFI_LIMIT_BYTES),
            mobileLimitEnabled = prefs.getBoolean(KEY_MOBILE_LIMIT_ENABLED, false),
            mobileLimitBytes = prefs.getLong(KEY_MOBILE_LIMIT_BYTES, DataUsageLimitConfig.DEFAULT_MOBILE_LIMIT_BYTES),
            period = DataLimitPeriod.fromId(prefs.getString(KEY_LIMIT_PERIOD, DataLimitPeriod.MONTHLY.id)),
            periodStartMs = prefs.getLong(KEY_PERIOD_START_MS, 0L)
        )
    }

    private fun saveConfigLocked(cfg: DataUsageLimitConfig) {
        prefs.edit()
            .putBoolean(KEY_WIFI_LIMIT_ENABLED, cfg.wifiLimitEnabled)
            .putLong(KEY_WIFI_LIMIT_BYTES, cfg.wifiLimitBytes)
            .putBoolean(KEY_MOBILE_LIMIT_ENABLED, cfg.mobileLimitEnabled)
            .putLong(KEY_MOBILE_LIMIT_BYTES, cfg.mobileLimitBytes)
            .putString(KEY_LIMIT_PERIOD, cfg.period.id)
            .putLong(KEY_PERIOD_START_MS, cfg.periodStartMs)
            .apply()
    }

    private fun loadFromDisk() {
        if (!storageFile.exists()) return
        runCatching {
            val raw = storageFile.readText(Charsets.UTF_8)
            val parsed = parsePersistedJson(raw)
            synchronized(lock) {
                periodWifiBytes = parsed.periodWifiBytes
                periodMobileBytes = parsed.periodMobileBytes
                entries.clear()
                for (entry in parsed.entries) {
                    entries[entry.trackKey] = entry
                }
                dailyBuckets.clear()
                for (bucket in parsed.dailyBuckets) {
                    dailyBuckets[bucket.epochDay] = bucket
                }
                trimDailyBucketsLocked()
            }
        }.onFailure { e ->
            Log.w(TAG, "Failed to load data usage history: ${e.message}")
        }
    }

    private fun persistToDiskAsync() {
        val snapshotJson = synchronized(lock) {
            serializeToJson(
                periodWifiBytes = periodWifiBytes,
                periodMobileBytes = periodMobileBytes,
                entries = entries.values.toList(),
                dailyBuckets = dailyBuckets.values.sortedBy { it.epochDay }
            )
        }
        ioScope.launch {
            runCatching {
                val tmp = File(storageFile.parentFile, "${storageFile.name}.tmp")
                tmp.writeText(snapshotJson, Charsets.UTF_8)
                if (storageFile.exists()) storageFile.delete()
                tmp.renameTo(storageFile)
            }.onFailure { e ->
                Log.w(TAG, "Failed to persist data usage history: ${e.message}")
            }
        }
    }

    internal data class PersistedState(
        val periodWifiBytes: Long,
        val periodMobileBytes: Long,
        val entries: List<TrackDataUsageEntry>,
        val dailyBuckets: List<DataUsageDailyBucket> = emptyList()
    )

    companion object {
        private const val TAG = "DataUsageTracker"
        private const val PREFS_NAME = "xperience_data_usage_prefs"
        private const val STORAGE_FILE_NAME = "data_usage_v1.json"
        private const val MAX_PERSISTED_TRACKS = 1500
        private const val MAX_PERSISTED_DAYS = 400

        private const val KEY_WIFI_LIMIT_ENABLED = "wifi_limit_enabled"
        private const val KEY_WIFI_LIMIT_BYTES = "wifi_limit_bytes"
        private const val KEY_MOBILE_LIMIT_ENABLED = "mobile_limit_enabled"
        private const val KEY_MOBILE_LIMIT_BYTES = "mobile_limit_bytes"
        private const val KEY_LIMIT_PERIOD = "limit_period"
        private const val KEY_PERIOD_START_MS = "period_start_ms"

        @Volatile
        private var instance: DataUsageTracker? = null

        fun getInstance(context: Context): DataUsageTracker {
            return instance ?: synchronized(this) {
                val appCtx = context.applicationContext
                instance ?: DataUsageTracker(
                    appContext = appCtx,
                    storageFile = File(appCtx.filesDir, STORAGE_FILE_NAME)
                ).also { instance = it }
            }
        }

        fun normalizeTrackKey(track: TrackItem, isLocal: Boolean): String {
            return normalizeTrackKey(track.id, track.title, track.artist, isLocal)
        }

        fun normalizeTrackKey(
            trackId: String,
            title: String,
            artist: String,
            isLocal: Boolean
        ): String {
            if (!isLocal) {
                val cleanYtm = trackId.removePrefix("ytm_video_").removePrefix("ytm_").trim()
                if (cleanYtm.isNotBlank()) return "ytm:$cleanYtm"
            }
            val sig = "${title.lowercase(Locale.ROOT).trim()}|${artist.lowercase(Locale.ROOT).trim()}|$trackId"
            return "local:${sig.hashCode().toUInt().toString(16)}"
        }

        fun computePeriodStartMs(period: DataLimitPeriod, nowMs: Long): Long {
            if (period == DataLimitPeriod.TOTAL) return 0L
            val cal = Calendar.getInstance().apply {
                timeInMillis = nowMs
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            when (period) {
                DataLimitPeriod.DAILY -> Unit
                DataLimitPeriod.WEEKLY -> cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                DataLimitPeriod.MONTHLY -> cal.set(Calendar.DAY_OF_MONTH, 1)
                DataLimitPeriod.TOTAL -> return 0L
            }
            return cal.timeInMillis
        }

        fun epochDayFromMillis(
            timeMs: Long,
            zoneId: ZoneId = ZoneId.systemDefault()
        ): Long {
            return Instant.ofEpochMilli(timeMs).atZone(zoneId).toLocalDate().toEpochDay()
        }

        fun buildChartPoints(
            summary: DataUsageSummary,
            granularity: DataUsageChartGranularity,
            nowMs: Long = System.currentTimeMillis(),
            zoneId: ZoneId = ZoneId.systemDefault(),
            locale: Locale = Locale.getDefault()
        ): List<DataUsageChartPoint> {
            val today = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
            val byDay = summary.dailyBuckets.associateBy { it.epochDay }

            fun sumRange(startDay: Long, endDay: Long): Pair<Long, Long> {
                var wifi = 0L
                var mobile = 0L
                for (day in startDay..endDay) {
                    val bucket = byDay[day] ?: continue
                    wifi += bucket.wifiBytes
                    mobile += bucket.mobileBytes
                }
                return wifi to mobile
            }

            return when (granularity) {
                DataUsageChartGranularity.DAY -> {
                    val dayFmt = DateTimeFormatter.ofPattern("d MMM", locale)
                    (-6L..0L).map { offset ->
                        val date = today.plusDays(offset)
                        val day = date.toEpochDay()
                        val bucket = byDay[day]
                        val shortDay = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
                        DataUsageChartPoint(
                            label = shortDay.take(3),
                            subLabel = date.format(dayFmt),
                            startEpochDay = day,
                            endEpochDay = day,
                            wifiBytes = bucket?.wifiBytes ?: 0L,
                            mobileBytes = bucket?.mobileBytes ?: 0L
                        )
                    }
                }
                DataUsageChartGranularity.WEEK -> {
                    val dateFmt = DateTimeFormatter.ofPattern("d/MM", locale)
                    val firstDayOfWeek = Calendar.getInstance(locale).firstDayOfWeek
                    val targetDow = if (firstDayOfWeek == Calendar.SUNDAY) java.time.DayOfWeek.SUNDAY else java.time.DayOfWeek.MONDAY
                    val currentWeekStart = today.with(TemporalAdjusters.previousOrSame(targetDow))
                    (-7L..0L).map { weekOffset ->
                        val start = currentWeekStart.plusWeeks(weekOffset)
                        val end = minOf(start.plusDays(6), today)
                        val (wifi, mobile) = sumRange(start.toEpochDay(), end.toEpochDay())
                        DataUsageChartPoint(
                            label = start.format(dateFmt),
                            subLabel = "${start.format(dateFmt)} – ${end.format(dateFmt)}",
                            startEpochDay = start.toEpochDay(),
                            endEpochDay = end.toEpochDay(),
                            wifiBytes = wifi,
                            mobileBytes = mobile
                        )
                    }
                }
                DataUsageChartGranularity.MONTH -> {
                    val thisMonth = YearMonth.from(today)
                    val fullFmt = DateTimeFormatter.ofPattern("MMM yyyy", locale)
                    (-5L..0L).map { monthOffset ->
                        val ym = thisMonth.plusMonths(monthOffset)
                        val start = ym.atDay(1)
                        val end = minOf(ym.atEndOfMonth(), today)
                        val (wifi, mobile) = sumRange(start.toEpochDay(), end.toEpochDay())
                        val shortMonth = ym.month.getDisplayName(TextStyle.SHORT, locale)
                        DataUsageChartPoint(
                            label = shortMonth.take(3),
                            subLabel = ym.format(fullFmt),
                            startEpochDay = start.toEpochDay(),
                            endEpochDay = end.toEpochDay(),
                            wifiBytes = wifi,
                            mobileBytes = mobile
                        )
                    }
                }
            }
        }

        fun filterAndSortTracks(
            summary: DataUsageSummary,
            timeFilter: DataUsageTimeFilter,
            customStartUtcMs: Long? = null,
            customEndUtcMs: Long? = null,
            sortOrder: DataUsageSortOrder = DataUsageSortOrder.HIGHEST_USAGE,
            networkFilter: String = "ALL",
            nowMs: Long = System.currentTimeMillis(),
            zoneId: ZoneId = ZoneId.systemDefault()
        ): List<TrackDataUsageEntry> {
            val baseList: List<TrackDataUsageEntry> = if (timeFilter == DataUsageTimeFilter.ALL_TIME) {
                summary.trackEntries
            } else {
                val today = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
                val (startDay, endDay) = when (timeFilter) {
                    DataUsageTimeFilter.TODAY -> today.toEpochDay() to today.toEpochDay()
                    DataUsageTimeFilter.THIS_WEEK -> {
                        val firstDow = Calendar.getInstance().firstDayOfWeek
                        val targetDow = if (firstDow == Calendar.SUNDAY) java.time.DayOfWeek.SUNDAY else java.time.DayOfWeek.MONDAY
                        val weekStart = today.with(TemporalAdjusters.previousOrSame(targetDow))
                        weekStart.toEpochDay() to today.toEpochDay()
                    }
                    DataUsageTimeFilter.THIS_MONTH -> {
                        today.withDayOfMonth(1).toEpochDay() to today.toEpochDay()
                    }
                    DataUsageTimeFilter.CUSTOM_RANGE -> {
                        val sDay = customStartUtcMs?.let {
                            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                        } ?: today.minusDays(6).toEpochDay()
                        val eDay = customEndUtcMs?.let {
                            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                        } ?: today.toEpochDay()
                        minOf(sDay, eDay) to maxOf(sDay, eDay)
                    }
                    DataUsageTimeFilter.ALL_TIME -> Long.MIN_VALUE to Long.MAX_VALUE
                }

                val metaByKey = summary.trackEntries.associateBy { it.trackKey }
                val aggregated = LinkedHashMap<String, DataUsageDailyTrackRecord>()
                for (bucket in summary.dailyBuckets) {
                    if (bucket.epochDay !in startDay..endDay) continue
                    for ((k, rec) in bucket.trackRecords) {
                        val prev = aggregated[k]
                        aggregated[k] = if (prev == null) {
                            rec
                        } else {
                            prev.copy(
                                wifiAudioBytes = prev.wifiAudioBytes + rec.wifiAudioBytes,
                                wifiArtworkBytes = prev.wifiArtworkBytes + rec.wifiArtworkBytes,
                                wifiLyricsBytes = prev.wifiLyricsBytes + rec.wifiLyricsBytes,
                                wifiCanvasBytes = prev.wifiCanvasBytes + rec.wifiCanvasBytes,
                                wifiOtherBytes = prev.wifiOtherBytes + rec.wifiOtherBytes,
                                mobileAudioBytes = prev.mobileAudioBytes + rec.mobileAudioBytes,
                                mobileArtworkBytes = prev.mobileArtworkBytes + rec.mobileArtworkBytes,
                                mobileLyricsBytes = prev.mobileLyricsBytes + rec.mobileLyricsBytes,
                                mobileCanvasBytes = prev.mobileCanvasBytes + rec.mobileCanvasBytes,
                                mobileOtherBytes = prev.mobileOtherBytes + rec.mobileOtherBytes,
                                streamPlayCount = prev.streamPlayCount + rec.streamPlayCount,
                                lastPlayedAtMs = maxOf(prev.lastPlayedAtMs, rec.lastPlayedAtMs)
                            )
                        }
                    }
                }

                aggregated.values.mapNotNull { rec ->
                    val meta = metaByKey[rec.trackKey]
                    val entry = TrackDataUsageEntry(
                        trackKey = rec.trackKey,
                        trackId = meta?.trackId ?: rec.trackKey,
                        title = meta?.title ?: rec.trackKey,
                        artist = meta?.artist ?: "Unknown Artist",
                        isLocal = meta?.isLocal ?: rec.trackKey.startsWith("local:"),
                        isDownloaded = meta?.isDownloaded ?: false,
                        streamPlayCount = rec.streamPlayCount,
                        lastPlayedAtMs = maxOf(rec.lastPlayedAtMs, meta?.lastPlayedAtMs?.takeIf { timeFilter == DataUsageTimeFilter.ALL_TIME } ?: rec.lastPlayedAtMs),
                        lastQualityLabel = meta?.lastQualityLabel.orEmpty(),
                        lastSourceLabel = meta?.lastSourceLabel.orEmpty(),
                        wifiAudioBytes = rec.wifiAudioBytes,
                        wifiArtworkBytes = rec.wifiArtworkBytes,
                        wifiLyricsBytes = rec.wifiLyricsBytes,
                        wifiCanvasBytes = rec.wifiCanvasBytes,
                        wifiOtherBytes = rec.wifiOtherBytes,
                        mobileAudioBytes = rec.mobileAudioBytes,
                        mobileArtworkBytes = rec.mobileArtworkBytes,
                        mobileLyricsBytes = rec.mobileLyricsBytes,
                        mobileCanvasBytes = rec.mobileCanvasBytes,
                        mobileOtherBytes = rec.mobileOtherBytes
                    )
                    entry.takeIf { it.shouldAppearInAnalysis() }
                }
            }

            val networkFiltered = when (networkFilter.uppercase(Locale.ROOT)) {
                "WIFI" -> baseList.filter { it.totalWifiBytes > 0L }
                "MOBILE" -> baseList.filter { it.totalMobileBytes > 0L }
                else -> baseList
            }

            return when (sortOrder) {
                DataUsageSortOrder.HIGHEST_USAGE -> networkFiltered.sortedWith(
                    compareByDescending<TrackDataUsageEntry> {
                        when (networkFilter.uppercase(Locale.ROOT)) {
                            "WIFI" -> it.totalWifiBytes
                            "MOBILE" -> it.totalMobileBytes
                            else -> it.totalNetworkBytes
                        }
                    }.thenByDescending { it.lastPlayedAtMs }
                )
                DataUsageSortOrder.RECENT_DATE -> networkFiltered.sortedWith(
                    compareByDescending<TrackDataUsageEntry> { it.lastPlayedAtMs }
                        .thenByDescending { it.totalNetworkBytes }
                )
                DataUsageSortOrder.ALPHABETICAL -> networkFiltered.sortedWith(
                    compareBy<TrackDataUsageEntry> { it.title.lowercase(Locale.ROOT) }
                        .thenBy { it.artist.lowercase(Locale.ROOT) }
                )
            }
        }

        internal fun serializeToJson(
            periodWifiBytes: Long,
            periodMobileBytes: Long,
            entries: List<TrackDataUsageEntry>,
            dailyBuckets: List<DataUsageDailyBucket> = emptyList()
        ): String {
            val root = JSONObject().apply {
                put("v", 2)
                put("pw", periodWifiBytes)
                put("pm", periodMobileBytes)
                val arr = JSONArray()
                for (e in entries) {
                    arr.put(
                        JSONObject().apply {
                            put("k", e.trackKey)
                            put("id", e.trackId)
                            put("t", e.title)
                            put("a", e.artist)
                            if (e.isLocal) put("loc", 1)
                            if (e.isDownloaded) put("dl", 1)
                            if (e.streamPlayCount > 0) put("pc", e.streamPlayCount)
                            if (e.lastPlayedAtMs > 0L) put("lp", e.lastPlayedAtMs)
                            if (e.lastQualityLabel.isNotBlank()) put("lq", e.lastQualityLabel)
                            if (e.lastSourceLabel.isNotBlank()) put("ls", e.lastSourceLabel)
                            if (e.wifiAudioBytes > 0L) put("wa", e.wifiAudioBytes)
                            if (e.wifiArtworkBytes > 0L) put("wi", e.wifiArtworkBytes)
                            if (e.wifiLyricsBytes > 0L) put("wl", e.wifiLyricsBytes)
                            if (e.wifiCanvasBytes > 0L) put("wc", e.wifiCanvasBytes)
                            if (e.wifiOtherBytes > 0L) put("wo", e.wifiOtherBytes)
                            if (e.mobileAudioBytes > 0L) put("ma", e.mobileAudioBytes)
                            if (e.mobileArtworkBytes > 0L) put("mi", e.mobileArtworkBytes)
                            if (e.mobileLyricsBytes > 0L) put("ml", e.mobileLyricsBytes)
                            if (e.mobileCanvasBytes > 0L) put("mc", e.mobileCanvasBytes)
                            if (e.mobileOtherBytes > 0L) put("mo", e.mobileOtherBytes)
                        }
                    )
                }
                put("e", arr)
                if (dailyBuckets.isNotEmpty()) {
                    val dArr = JSONArray()
                    for (b in dailyBuckets) {
                        val trArr = JSONArray()
                        for (rec in b.trackRecords.values) {
                            trArr.put(
                                JSONObject().apply {
                                    put("k", rec.trackKey)
                                    if (rec.wifiAudioBytes > 0L) put("wa", rec.wifiAudioBytes)
                                    if (rec.wifiArtworkBytes > 0L) put("wi", rec.wifiArtworkBytes)
                                    if (rec.wifiLyricsBytes > 0L) put("wl", rec.wifiLyricsBytes)
                                    if (rec.wifiCanvasBytes > 0L) put("wc", rec.wifiCanvasBytes)
                                    if (rec.wifiOtherBytes > 0L) put("wo", rec.wifiOtherBytes)
                                    if (rec.mobileAudioBytes > 0L) put("ma", rec.mobileAudioBytes)
                                    if (rec.mobileArtworkBytes > 0L) put("mi", rec.mobileArtworkBytes)
                                    if (rec.mobileLyricsBytes > 0L) put("ml", rec.mobileLyricsBytes)
                                    if (rec.mobileCanvasBytes > 0L) put("mc", rec.mobileCanvasBytes)
                                    if (rec.mobileOtherBytes > 0L) put("mo", rec.mobileOtherBytes)
                                    if (rec.streamPlayCount > 0) put("pc", rec.streamPlayCount)
                                    if (rec.lastPlayedAtMs > 0L) put("lp", rec.lastPlayedAtMs)
                                }
                            )
                        }
                        dArr.put(
                            JSONObject().apply {
                                put("day", b.epochDay)
                                if (b.wifiBytes > 0L) put("w", b.wifiBytes)
                                if (b.mobileBytes > 0L) put("m", b.mobileBytes)
                                if (trArr.length() > 0) put("tr", trArr)
                            }
                        )
                    }
                    put("d", dArr)
                }
            }
            return root.toString()
        }

        internal fun parsePersistedJson(raw: String): PersistedState {
            val root = JSONObject(raw)
            val pw = root.optLong("pw", 0L).coerceAtLeast(0L)
            val pm = root.optLong("pm", 0L).coerceAtLeast(0L)
            val arr = root.optJSONArray("e") ?: JSONArray()
            val list = ArrayList<TrackDataUsageEntry>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val key = obj.optString("k", "").trim()
                if (key.isBlank()) continue
                list.add(
                    TrackDataUsageEntry(
                        trackKey = key,
                        trackId = obj.optString("id", key),
                        title = obj.optString("t", "Unknown Track"),
                        artist = obj.optString("a", "Unknown Artist"),
                        isLocal = obj.optInt("loc", 0) == 1,
                        isDownloaded = obj.optInt("dl", 0) == 1,
                        streamPlayCount = obj.optInt("pc", 0).coerceAtLeast(0),
                        lastPlayedAtMs = obj.optLong("lp", 0L).coerceAtLeast(0L),
                        lastQualityLabel = obj.optString("lq", ""),
                        lastSourceLabel = obj.optString("ls", ""),
                        wifiAudioBytes = obj.optLong("wa", 0L).coerceAtLeast(0L),
                        wifiArtworkBytes = obj.optLong("wi", 0L).coerceAtLeast(0L),
                        wifiLyricsBytes = obj.optLong("wl", 0L).coerceAtLeast(0L),
                        wifiCanvasBytes = obj.optLong("wc", 0L).coerceAtLeast(0L),
                        wifiOtherBytes = obj.optLong("wo", 0L).coerceAtLeast(0L),
                        mobileAudioBytes = obj.optLong("ma", 0L).coerceAtLeast(0L),
                        mobileArtworkBytes = obj.optLong("mi", 0L).coerceAtLeast(0L),
                        mobileLyricsBytes = obj.optLong("ml", 0L).coerceAtLeast(0L),
                        mobileCanvasBytes = obj.optLong("mc", 0L).coerceAtLeast(0L),
                        mobileOtherBytes = obj.optLong("mo", 0L).coerceAtLeast(0L)
                    )
                )
            }

            val buckets = mutableListOf<DataUsageDailyBucket>()
            val dArr = root.optJSONArray("d")
            if (dArr != null) {
                for (i in 0 until dArr.length()) {
                    val bObj = dArr.optJSONObject(i) ?: continue
                    val day = bObj.optLong("day", Long.MIN_VALUE)
                    if (day == Long.MIN_VALUE) continue
                    val trArr = bObj.optJSONArray("tr") ?: JSONArray()
                    val recMap = LinkedHashMap<String, DataUsageDailyTrackRecord>(trArr.length())
                    for (j in 0 until trArr.length()) {
                        val rObj = trArr.optJSONObject(j) ?: continue
                        val k = rObj.optString("k", "").trim()
                        if (k.isBlank()) continue
                        recMap[k] = DataUsageDailyTrackRecord(
                            trackKey = k,
                            wifiAudioBytes = rObj.optLong("wa", 0L).coerceAtLeast(0L),
                            wifiArtworkBytes = rObj.optLong("wi", 0L).coerceAtLeast(0L),
                            wifiLyricsBytes = rObj.optLong("wl", 0L).coerceAtLeast(0L),
                            wifiCanvasBytes = rObj.optLong("wc", 0L).coerceAtLeast(0L),
                            wifiOtherBytes = rObj.optLong("wo", 0L).coerceAtLeast(0L),
                            mobileAudioBytes = rObj.optLong("ma", 0L).coerceAtLeast(0L),
                            mobileArtworkBytes = rObj.optLong("mi", 0L).coerceAtLeast(0L),
                            mobileLyricsBytes = rObj.optLong("ml", 0L).coerceAtLeast(0L),
                            mobileCanvasBytes = rObj.optLong("mc", 0L).coerceAtLeast(0L),
                            mobileOtherBytes = rObj.optLong("mo", 0L).coerceAtLeast(0L),
                            streamPlayCount = rObj.optInt("pc", 0).coerceAtLeast(0),
                            lastPlayedAtMs = rObj.optLong("lp", 0L).coerceAtLeast(0L)
                        )
                    }
                    buckets.add(
                        DataUsageDailyBucket(
                            epochDay = day,
                            wifiBytes = bObj.optLong("w", 0L).coerceAtLeast(0L),
                            mobileBytes = bObj.optLong("m", 0L).coerceAtLeast(0L),
                            trackRecords = recMap
                        )
                    )
                }
            } else if (list.isNotEmpty()) {
                // Backward compatibility: synthesize initial daily buckets from v1 history entries
                val fallbackDay = epochDayFromMillis(System.currentTimeMillis())
                val grouped = LinkedHashMap<Long, MutableList<TrackDataUsageEntry>>()
                for (entry in list) {
                    if (!entry.shouldAppearInAnalysis()) continue
                    val day = if (entry.lastPlayedAtMs > 0L) epochDayFromMillis(entry.lastPlayedAtMs) else fallbackDay
                    grouped.getOrPut(day) { mutableListOf() }.add(entry)
                }
                for ((day, dayEntries) in grouped) {
                    val recMap = LinkedHashMap<String, DataUsageDailyTrackRecord>(dayEntries.size)
                    var dayWifi = 0L
                    var dayMobile = 0L
                    for (e in dayEntries) {
                        dayWifi += e.totalWifiBytes
                        dayMobile += e.totalMobileBytes
                        recMap[e.trackKey] = DataUsageDailyTrackRecord(
                            trackKey = e.trackKey,
                            wifiAudioBytes = e.wifiAudioBytes,
                            wifiArtworkBytes = e.wifiArtworkBytes,
                            wifiLyricsBytes = e.wifiLyricsBytes,
                            wifiCanvasBytes = e.wifiCanvasBytes,
                            wifiOtherBytes = e.wifiOtherBytes,
                            mobileAudioBytes = e.mobileAudioBytes,
                            mobileArtworkBytes = e.mobileArtworkBytes,
                            mobileLyricsBytes = e.mobileLyricsBytes,
                            mobileCanvasBytes = e.mobileCanvasBytes,
                            mobileOtherBytes = e.mobileOtherBytes,
                            streamPlayCount = e.streamPlayCount,
                            lastPlayedAtMs = e.lastPlayedAtMs
                        )
                    }
                    buckets.add(
                        DataUsageDailyBucket(
                            epochDay = day,
                            wifiBytes = dayWifi,
                            mobileBytes = dayMobile,
                            trackRecords = recMap
                        )
                    )
                }
            }
            return PersistedState(pw, pm, list, buckets.sortedBy { it.epochDay })
        }

        fun formatBytes(bytes: Long): String {
            if (bytes <= 0L) return "0 B"
            val kb = 1024.0
            val mb = kb * 1024.0
            val gb = mb * 1024.0
            return when {
                bytes >= gb -> String.format(Locale.ROOT, "%.2f GB", bytes / gb)
                bytes >= mb -> String.format(Locale.ROOT, "%.1f MB", bytes / mb)
                bytes >= kb -> String.format(Locale.ROOT, "%.0f KB", bytes / kb)
                else -> "$bytes B"
            }
        }
    }
}
