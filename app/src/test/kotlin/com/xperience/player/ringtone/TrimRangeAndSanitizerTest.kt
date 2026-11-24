package com.xperience.player.ringtone

import com.xperience.player.service.ringtone.RingtoneMetadataSanitizer
import com.xperience.player.service.ringtone.RingtoneStorageManager
import com.xperience.player.service.ringtone.RingtoneTarget
import com.xperience.player.service.ringtone.TrimPreset
import com.xperience.player.service.ringtone.TrimRange
import com.xperience.player.service.ringtone.WaveformNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrimRangeAndSanitizerTest {

    @Test
    fun `defaultForTrack selects 30s on long track and full duration on short track`() {
        val longRange = TrimRange.defaultForTrack(trackDurationMs = 210_000L)
        assertEquals(0L, longRange.startMs)
        assertEquals(30_000L, longRange.endMs)
        assertTrue(longRange.isValidFor(210_000L))

        val shortRange = TrimRange.defaultForTrack(trackDurationMs = 12_000L)
        assertEquals(0L, shortRange.startMs)
        assertEquals(12_000L, shortRange.endMs)
        assertTrue(shortRange.isValidFor(12_000L))

        val tinyRange = TrimRange.defaultForTrack(trackDurationMs = 600L)
        assertEquals(0L, tinyRange.startMs)
        assertEquals(600L, tinyRange.endMs)
        assertTrue(tinyRange.isValidFor(600L))

        val zeroRange = TrimRange.defaultForTrack(trackDurationMs = 0L)
        assertEquals(TrimRange.ZERO, zeroRange)
        assertFalse(zeroRange.isValidFor(0L))
    }

    @Test
    fun `createClamped fixes inverted bounds negative values and min max duration constraints`() {
        val duration = 180_000L

        // Inverted start/end
        val swapped = TrimRange.createClamped(startMs = 45_000L, endMs = 15_000L, trackDurationMs = duration)
        assertEquals(15_000L, swapped.startMs)
        assertEquals(45_000L, swapped.endMs)

        // Negative start and out-of-bounds end capped at MAX_DURATION_MS (60s)
        val capped = TrimRange.createClamped(startMs = -5_000L, endMs = 250_000L, trackDurationMs = duration)
        assertEquals(0L, capped.startMs)
        assertEquals(TrimRange.MAX_DURATION_MS, capped.endMs)

        // Too-short span expanded to MIN_DURATION_MS (1s)
        val tooShort = TrimRange.createClamped(startMs = 50_000L, endMs = 50_200L, trackDurationMs = duration)
        assertEquals(50_000L, tooShort.startMs)
        assertEquals(51_000L, tooShort.endMs)

        // Too-short span right at track end pulls start backwards
        val nearEnd = TrimRange.createClamped(startMs = 179_800L, endMs = 180_000L, trackDurationMs = duration)
        assertEquals(179_000L, nearEnd.startMs)
        assertEquals(180_000L, nearEnd.endMs)
    }

    @Test
    fun `withStart withEnd nudge and shiftBy preserve bounds and window size`() {
        val duration = 120_000L
        val initial = TrimRange(20_000L, 40_000L)

        // Nudge start -0.5s / +0.5s
        assertEquals(TrimRange(19_500L, 40_000L), initial.nudgeStart(-500L, duration))
        assertEquals(TrimRange(20_500L, 40_000L), initial.nudgeStart(500L, duration))

        // Nudge end -0.5s / +0.5s
        assertEquals(TrimRange(20_000L, 39_500L), initial.nudgeEnd(-500L, duration))
        assertEquals(TrimRange(20_000L, 40_500L), initial.nudgeEnd(500L, duration))

        // Moving start past end stops at end - MIN_DURATION_MS
        val pushedStart = initial.withStart(50_000L, duration)
        assertEquals(39_000L, pushedStart.startMs)
        assertEquals(40_000L, pushedStart.endMs)

        // Moving end far beyond MAX_DURATION_MS clamps to start + MAX_DURATION_MS
        val stretchedEnd = initial.withEnd(115_000L, duration)
        assertEquals(20_000L, stretchedEnd.startMs)
        assertEquals(80_000L, stretchedEnd.endMs)

        // Shifting window preserves 20s length even when clamping against track edges
        val shiftedRight = initial.shiftBy(95_000L, duration)
        assertEquals(100_000L, shiftedRight.startMs)
        assertEquals(120_000L, shiftedRight.endMs)
        assertEquals(20_000L, shiftedRight.durationMs)

        val shiftedLeft = initial.shiftBy(-50_000L, duration)
        assertEquals(0L, shiftedLeft.startMs)
        assertEquals(20_000L, shiftedLeft.endMs)
        assertEquals(20_000L, shiftedLeft.durationMs)
    }

    @Test
    fun `applyPreset produces expected intervals for chorus intro and short notifications`() {
        val duration = 200_000L

        val notif = TrimRange.applyPreset(TrimPreset.NOTIFICATION_5S, duration, anchorStartMs = 40_000L)
        assertEquals(40_000L, notif.startMs)
        assertEquals(45_000L, notif.endMs)

        val clip15 = TrimRange.applyPreset(TrimPreset.CLIP_15S, duration, anchorStartMs = 190_000L)
        assertEquals(185_000L, clip15.startMs)
        assertEquals(200_000L, clip15.endMs)

        val chorus = TrimRange.applyPreset(TrimPreset.CHORUS, duration)
        assertEquals(60_000L, chorus.startMs)
        assertEquals(90_000L, chorus.endMs)

        val intro = TrimRange.applyPreset(TrimPreset.INTRO_15S, duration)
        assertEquals(0L, intro.startMs)
        assertEquals(15_000L, intro.endMs)

        // Chorus on a short 10s track covers full 10s
        val shortChorus = TrimRange.applyPreset(TrimPreset.CHORUS, trackDurationMs = 10_000L)
        assertEquals(0L, shortChorus.startMs)
        assertEquals(10_000L, shortChorus.endMs)
    }

    @Test
    fun `metadata sanitizer strips illegal chars handles empty fallbacks and formats timestamps`() {
        assertEquals(
            "Daft Punk_ Harder_Better_Faster.m4a",
            RingtoneMetadataSanitizer.buildM4aFileName("  Daft Punk: Harder/Better*Faster?  ")
        )
        assertEquals(
            "xperience_ringtone.m4a",
            RingtoneMetadataSanitizer.buildM4aFileName("...///???")
        )
        assertEquals(
            "xperience_ringtone.m4a",
            RingtoneMetadataSanitizer.buildM4aFileName("..")
        )
        assertEquals(
            "Recorte Xperience",
            RingtoneMetadataSanitizer.sanitizeDisplayTitle("   \u0000\t\n  ")
        )
        assertEquals(
            "Xperience Player",
            RingtoneMetadataSanitizer.sanitizeArtist("<unknown>")
        )
        assertTrue(RingtoneMetadataSanitizer.sanitizeFileNameBase("A".repeat(120)).length <= 64)
        assertEquals("Midnight City (Recorte)", RingtoneMetadataSanitizer.defaultClipName("Midnight City"))

        assertEquals("0:00.0", RingtoneMetadataSanitizer.formatTrimTimeMs(0L))
        assertEquals("0:30.5", RingtoneMetadataSanitizer.formatTrimTimeMs(30_500L))
        assertEquals("1:05.3", RingtoneMetadataSanitizer.formatTrimTimeMs(65_260L))
        assertEquals("15.0 s", RingtoneMetadataSanitizer.formatDurationLabel(15_000L))
        assertEquals("1:00.0", RingtoneMetadataSanitizer.formatDurationLabel(60_000L))
    }

    @Test
    fun `waveform normalizer handles silence NaN negative values and deterministic fallback`() {
        assertEquals(emptyList<Float>(), WaveformNormalizer.normalizePeaks(floatArrayOf()))

        // Silence produces uniform floor
        val silence = WaveformNormalizer.normalizePeaks(floatArrayOf(0f, 0f, 0f), minFloor = 0.12f)
        assertEquals(listOf(0.12f, 0.12f, 0.12f), silence)

        // NaN, Infinity, and negative values are handled cleanly and peak reaches 1f
        val mixed = WaveformNormalizer.normalizePeaks(
            floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, -0.5f, 1.0f, 0.0f),
            minFloor = 0.10f
        )
        assertEquals(5, mixed.size)
        assertEquals(0.10f, mixed[0], 0.001f)
        assertEquals(0.10f, mixed[1], 0.001f)
        assertTrue(mixed[2] in 0.5f..0.8f)
        assertEquals(1.0f, mixed[3], 0.001f)
        assertEquals(0.10f, mixed[4], 0.001f)

        // Fallback waveform is deterministic for identical seed and distinct for different seeds
        val fbA1 = WaveformNormalizer.generateFallbackWaveform(seed = 1234, barCount = 96)
        val fbA2 = WaveformNormalizer.generateFallbackWaveform(seed = 1234, barCount = 96)
        val fbB = WaveformNormalizer.generateFallbackWaveform(seed = 5678, barCount = 96)
        assertEquals(96, fbA1.size)
        assertEquals(fbA1, fbA2)
        assertNotEquals(fbA1, fbB)
        assertTrue(fbA1.all { it in 0.10f..1.0f })
    }

    @Test
    fun `inferTargetFromMediaStoreRow distinguishes ringtones notifications and alarms`() {
        assertEquals(
            RingtoneTarget.NOTIFICATION,
            RingtoneStorageManager.inferTargetFromMediaStoreRow(
                relativePath = "Notifications/Xperience/",
                isRingtone = false,
                isNotification = true,
                isAlarm = false
            )
        )
        assertEquals(
            RingtoneTarget.ALARM,
            RingtoneStorageManager.inferTargetFromMediaStoreRow(
                relativePath = "Alarms/Xperience/",
                isRingtone = false,
                isNotification = false,
                isAlarm = true
            )
        )
        assertEquals(
            RingtoneTarget.RINGTONE,
            RingtoneStorageManager.inferTargetFromMediaStoreRow(
                relativePath = "Ringtones/Xperience/",
                isRingtone = true,
                isNotification = false,
                isAlarm = false
            )
        )
    }
}
