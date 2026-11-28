package com.xperience.player.service

import com.xperience.player.domain.model.DataLimitPeriod
import com.xperience.player.domain.model.DataUsageChartGranularity
import com.xperience.player.domain.model.DataUsageDailyBucket
import com.xperience.player.domain.model.DataUsageDailyTrackRecord
import com.xperience.player.domain.model.DataUsageLimitConfig
import com.xperience.player.domain.model.DataUsageSortOrder
import com.xperience.player.domain.model.DataUsageSummary
import com.xperience.player.domain.model.DataUsageTimeFilter
import com.xperience.player.domain.model.TrackDataUsageEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class DataUsageTrackerTest {

    @Test
    fun localTrackIncludedInAnalysisOnlyWhenExternalDataConsumed() {
        val pureLocal = TrackDataUsageEntry(
            trackKey = "local:abc123",
            trackId = "local_media_1",
            title = "Offline Song",
            artist = "Local Artist",
            isLocal = true
        )
        assertFalse(pureLocal.shouldAppearInAnalysis())
        assertFalse(pureLocal.isDownloadSuggestionCandidate())

        val localWithExternalLyricsAndCanvas = pureLocal.copy(
            wifiLyricsBytes = 4_200L,
            mobileCanvasBytes = 1_500_000L
        )
        assertTrue(localWithExternalLyricsAndCanvas.shouldAppearInAnalysis())
        assertEquals(1_504_200L, localWithExternalLyricsAndCanvas.totalNetworkBytes)
        assertFalse(localWithExternalLyricsAndCanvas.isDownloadSuggestionCandidate())
    }

    @Test
    fun smartDownloadSuggestionRequiresOnlineNotDownloadedAndAtLeastTwoPlays() {
        val singlePlay = TrackDataUsageEntry(
            trackKey = "ytm:vid1",
            trackId = "ytm_vid1",
            title = "Streamed Once",
            artist = "Artist",
            isLocal = false,
            isDownloaded = false,
            streamPlayCount = 1,
            wifiAudioBytes = 4_000_000L
        )
        assertFalse(singlePlay.isDownloadSuggestionCandidate())

        val repeatStreamed = singlePlay.copy(streamPlayCount = 3)
        assertTrue(repeatStreamed.isDownloadSuggestionCandidate())

        val alreadyDownloaded = repeatStreamed.copy(isDownloaded = true)
        assertFalse(alreadyDownloaded.isDownloadSuggestionCandidate())
    }

    @Test
    fun compactJsonSerializationRoundTripPreservesAllCounters() {
        val entries = listOf(
            TrackDataUsageEntry(
                trackKey = "ytm:dQw4w9WgXcQ",
                trackId = "ytm_dQw4w9WgXcQ",
                title = "Never Gonna Give You Up",
                artist = "Rick Astley",
                isLocal = false,
                isDownloaded = false,
                streamPlayCount = 4,
                lastPlayedAtMs = 1_700_000_000_000L,
                lastQualityLabel = "FLAC Lossless",
                lastSourceLabel = "FLAC Lossless (GDStudio)",
                wifiAudioBytes = 28_000_000L,
                wifiArtworkBytes = 120_000L,
                wifiLyricsBytes = 3_500L,
                wifiCanvasBytes = 2_400_000L,
                mobileAudioBytes = 3_200_000L,
                mobileArtworkBytes = 45_000L
            )
        )

        val json = DataUsageTracker.serializeToJson(
            periodWifiBytes = 30_523_500L,
            periodMobileBytes = 3_245_000L,
            entries = entries
        )
        val parsed = DataUsageTracker.parsePersistedJson(json)
        assertEquals(30_523_500L, parsed.periodWifiBytes)
        assertEquals(3_245_000L, parsed.periodMobileBytes)
        assertEquals(1, parsed.entries.size)
        assertEquals(entries[0], parsed.entries[0])
    }

    @Test
    fun dataUsageSummaryLimitThresholdsWorkIndependentlyForWifiAndMobile() {
        val cfg = DataUsageLimitConfig(
            wifiLimitEnabled = true,
            wifiLimitBytes = 1_000_000L,
            mobileLimitEnabled = true,
            mobileLimitBytes = 500_000L,
            period = DataLimitPeriod.MONTHLY
        )
        val summary = DataUsageSummary(
            config = cfg,
            periodWifiBytes = 400_000L,
            periodMobileBytes = 550_000L
        )
        assertFalse(summary.isWifiLimitExceeded)
        assertTrue(summary.isMobileLimitExceeded)
        assertEquals(0.4f, summary.wifiUsageFraction, 0.001f)
        assertEquals(1.0f, summary.mobileUsageFraction, 0.001f)
    }

    @Test
    fun formatBytesProducesHumanReadableUnits() {
        assertEquals("0 B", DataUsageTracker.formatBytes(0L))
        assertEquals("512 B", DataUsageTracker.formatBytes(512L))
        assertEquals("2 KB", DataUsageTracker.formatBytes(2048L))
        assertEquals("1.5 MB", DataUsageTracker.formatBytes(1_572_864L))
        assertEquals("2.00 GB", DataUsageTracker.formatBytes(2L * 1024L * 1024L * 1024L))
    }

    @Test
    fun dailyBucketsRoundTripAndChartAndSortAnalyticsWorkAccurately() {
        val today = LocalDate.of(2026, 11, 27)
        val todayMs = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() + 3_600_000L
        val yesterday = today.minusDays(1)
        val yesterdayMs = yesterday.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() + 3_600_000L

        val entryA = TrackDataUsageEntry(
            trackKey = "ytm:aaa",
            trackId = "ytm_aaa",
            title = "Zebra Song",
            artist = "Artist A",
            lastPlayedAtMs = yesterdayMs,
            wifiAudioBytes = 8_000_000L
        )
        val entryB = TrackDataUsageEntry(
            trackKey = "ytm:bbb",
            trackId = "ytm_bbb",
            title = "Alpha Song",
            artist = "Artist B",
            lastPlayedAtMs = todayMs,
            mobileAudioBytes = 2_000_000L
        )
        val bucketYesterday = DataUsageDailyBucket(
            epochDay = yesterday.toEpochDay(),
            wifiBytes = 8_000_000L,
            mobileBytes = 0L,
            trackRecords = mapOf(
                entryA.trackKey to DataUsageDailyTrackRecord(
                    trackKey = entryA.trackKey,
                    wifiAudioBytes = 8_000_000L,
                    lastPlayedAtMs = yesterdayMs
                )
            )
        )
        val bucketToday = DataUsageDailyBucket(
            epochDay = today.toEpochDay(),
            wifiBytes = 0L,
            mobileBytes = 2_000_000L,
            trackRecords = mapOf(
                entryB.trackKey to DataUsageDailyTrackRecord(
                    trackKey = entryB.trackKey,
                    mobileAudioBytes = 2_000_000L,
                    lastPlayedAtMs = todayMs
                )
            )
        )

        val json = DataUsageTracker.serializeToJson(
            periodWifiBytes = 8_000_000L,
            periodMobileBytes = 2_000_000L,
            entries = listOf(entryA, entryB),
            dailyBuckets = listOf(bucketYesterday, bucketToday)
        )
        val parsed = DataUsageTracker.parsePersistedJson(json)
        assertEquals(2, parsed.dailyBuckets.size)
        assertEquals(8_000_000L, parsed.dailyBuckets[0].wifiBytes)
        assertEquals(2_000_000L, parsed.dailyBuckets[1].mobileBytes)

        val summary = DataUsageSummary(
            trackEntries = listOf(entryA, entryB),
            dailyBuckets = parsed.dailyBuckets
        )

        val dayPoints = DataUsageTracker.buildChartPoints(
            summary = summary,
            granularity = DataUsageChartGranularity.DAY,
            nowMs = todayMs,
            zoneId = ZoneOffset.UTC
        )
        assertEquals(7, dayPoints.size)
        assertEquals(8_000_000L, dayPoints[5].wifiBytes)
        assertEquals(2_000_000L, dayPoints[6].mobileBytes)

        val monthPoints = DataUsageTracker.buildChartPoints(
            summary = summary,
            granularity = DataUsageChartGranularity.MONTH,
            nowMs = todayMs,
            zoneId = ZoneOffset.UTC
        )
        assertEquals(6, monthPoints.size)
        assertEquals(10_000_000L, monthPoints.last().totalBytes)

        // Filter TODAY -> only Alpha Song
        val todayTracks = DataUsageTracker.filterAndSortTracks(
            summary = summary,
            timeFilter = DataUsageTimeFilter.TODAY,
            nowMs = todayMs,
            zoneId = ZoneOffset.UTC
        )
        assertEquals(listOf("Alpha Song"), todayTracks.map { it.title })

        // Sort ALPHABETICAL -> Alpha Song before Zebra Song
        val alphaAll = DataUsageTracker.filterAndSortTracks(
            summary = summary,
            timeFilter = DataUsageTimeFilter.ALL_TIME,
            sortOrder = DataUsageSortOrder.ALPHABETICAL,
            nowMs = todayMs,
            zoneId = ZoneOffset.UTC
        )
        assertEquals(listOf("Alpha Song", "Zebra Song"), alphaAll.map { it.title })

        // Sort HIGHEST_USAGE -> Zebra Song (8MB) before Alpha Song (2MB)
        val highestAll = DataUsageTracker.filterAndSortTracks(
            summary = summary,
            timeFilter = DataUsageTimeFilter.ALL_TIME,
            sortOrder = DataUsageSortOrder.HIGHEST_USAGE,
            nowMs = todayMs,
            zoneId = ZoneOffset.UTC
        )
        assertEquals(listOf("Zebra Song", "Alpha Song"), highestAll.map { it.title })
    }
}
