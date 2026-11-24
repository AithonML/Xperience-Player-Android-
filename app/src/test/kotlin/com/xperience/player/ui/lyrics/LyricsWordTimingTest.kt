package com.xperience.player.ui.lyrics

import com.xperience.player.domain.model.lyrics.LyricLine
import com.xperience.player.domain.model.lyrics.LyricSyllableSegment
import com.xperience.player.service.LrcParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsWordTimingTest {

    @Test
    fun `tokenizeLine preserves non-linear syllable glyph timings`() {
        // "Fo" is fast (100ms), "rever" is held (900ms)
        val rawSegments = listOf(
            LyricSyllableSegment(startMs = 1000L, endMs = 1100L, text = "Fo"),
            LyricSyllableSegment(startMs = 1100L, endMs = 2000L, text = "rever "),
            LyricSyllableSegment(startMs = 2000L, endMs = 2500L, text = "young")
        )
        val expanded = LrcParser.expandSyllablesToCharacters(rawSegments)
        val line = LyricLine(
            timestampMs = 1000L,
            text = "Forever young",
            isSynchronized = true,
            endTimestampMs = 2500L,
            syllables = expanded
        )

        val tokens = LyricsTimingEngine.tokenizeLine(line)

        assertEquals(2, tokens.size)
        assertEquals("Forever", tokens[0].text)
        assertTrue(tokens[0].hasTrailingSpace)
        assertEquals("young", tokens[1].text)
        assertFalse(tokens[1].hasTrailingSpace)

        // Ensure trailing space didn't stretch "Forever" into "young"
        assertTrue(tokens[0].endMs <= 2000L)

        // At 1100ms ("Fo" done = 2/7 chars), progress should follow glyph timing (~0.286f), NOT linear 100/1000 (0.10f)
        val progressAtSyllableBoundary = LyricsTimingEngine.computeWordSweepProgress(tokens[0], 1100L)
        assertEquals(2f / 7f, progressAtSyllableBoundary, 0.04f)
    }

    @Test
    fun `tokenizeLine handles leading and trailing spaces without stretching prior word`() {
        val line = LyricLine(
            timestampMs = 5000L,
            text = "Hold on",
            isSynchronized = true,
            endTimestampMs = 6500L,
            syllables = listOf(
                LyricSyllableSegment(5000L, 5400L, "Hold"),
                LyricSyllableSegment(5400L, 5800L, " "),
                LyricSyllableSegment(5800L, 6500L, " on")
            )
        )

        val tokens = LyricsTimingEngine.tokenizeLine(line)

        assertEquals(2, tokens.size)
        assertEquals("Hold", tokens[0].text)
        assertTrue(tokens[0].hasTrailingSpace)
        assertEquals(5000L, tokens[0].startMs)
        assertEquals(5400L, tokens[0].endMs)

        assertEquals("on", tokens[1].text)
        assertFalse(tokens[1].hasTrailingSpace)
        assertEquals(6033L, tokens[1].startMs, 5L)
        assertEquals(6500L, tokens[1].endMs)
    }

    @Test
    fun `tokenizeLine clamps overlapping word end timestamps so two words never sweep together`() {
        val line = LyricLine(
            timestampMs = 1000L,
            text = "Shine bright",
            isSynchronized = true,
            endTimestampMs = 2200L,
            syllables = listOf(
                LyricSyllableSegment(1000L, 1800L, "Shine "),
                LyricSyllableSegment(1500L, 2200L, "bright")
            )
        )

        val tokens = LyricsTimingEngine.tokenizeLine(line)

        assertEquals(2, tokens.size)
        assertTrue(tokens[0].endMs <= tokens[1].startMs)
    }

    @Test
    fun `tokenizeLine splits CJK characters individually and keeps punctuation attached`() {
        val line = LyricLine(
            timestampMs = 2000L,
            text = "夢なら。",
            isSynchronized = true,
            endTimestampMs = 3200L,
            syllables = null
        )

        val tokens = LyricsTimingEngine.tokenizeLine(line)

        assertEquals(3, tokens.size)
        assertEquals("夢", tokens[0].text)
        assertEquals("な", tokens[1].text)
        assertEquals("ら。", tokens[2].text)
        assertTrue(tokens.all { it.isCjkToken })
        assertTrue(tokens[0].endMs <= tokens[1].startMs)
        assertTrue(tokens[1].endMs <= tokens[2].startMs)
    }

    @Test
    fun `computeWordVisualState grows active word individually while upcoming words stay smaller and dimmed`() {
        val line = LyricLine(
            timestampMs = 1000L,
            text = "Light up the night",
            isSynchronized = true,
            endTimestampMs = 3400L,
            syllables = LrcParser.expandSyllablesToCharacters(
                listOf(
                    LyricSyllableSegment(1000L, 1400L, "Light "),
                    LyricSyllableSegment(1500L, 2100L, "up "),
                    LyricSyllableSegment(2100L, 2700L, "the "),
                    LyricSyllableSegment(2700L, 3400L, "night")
                )
            )
        )
        val tokens = LyricsTimingEngine.tokenizeLine(line)
        assertEquals(4, tokens.size)

        // Probe mid-way through word 1 ("up"), well after word 0 ("Light") release tail finished
        val probeMs = 1750L
        val w0 = LyricsTimingEngine.computeWordVisualState(tokens[0], probeMs, isLineActive = true, isLinePast = false, isSynchronized = true)
        val w1 = LyricsTimingEngine.computeWordVisualState(tokens[1], probeMs, isLineActive = true, isLinePast = false, isSynchronized = true)
        val w2 = LyricsTimingEngine.computeWordVisualState(tokens[2], probeMs, isLineActive = true, isLinePast = false, isSynchronized = true)
        val w3 = LyricsTimingEngine.computeWordVisualState(tokens[3], probeMs, isLineActive = true, isLinePast = false, isSynchronized = true)

        // Word 0 already completed and settled back to 1.0x with low residual glow
        assertEquals(1.0f, w0.sweepProgress, 0.001f)
        assertEquals(1.0f, w0.scale, 0.02f)
        assertTrue(w0.glowIntensity <= 0.15f)

        // Word 1 is actively singing: partial sweep, popped scale in 1.12x..1.18x, lifted, bright glow
        assertTrue(w1.sweepProgress in 0.2f..0.95f)
        assertTrue("Expected active scale in 1.12..1.18, got ${w1.scale}", w1.scale in 1.12f..1.18f)
        assertTrue(w1.liftDp < -2.0f)
        assertTrue(w1.glowIntensity > 0.8f)

        // Upcoming words 2 & 3 remain unlit and smaller (0.94f)
        assertEquals(0f, w2.sweepProgress, 0.001f)
        assertEquals(0.94f, w2.scale, 0.001f)
        assertEquals(0f, w2.glowIntensity, 0.001f)
        assertEquals(0f, w3.sweepProgress, 0.001f)
        assertEquals(0.94f, w3.scale, 0.001f)
    }

    @Test
    fun `computeWordVisualState smoothly decays scale and glow across release tail and line transition`() {
        val word = LyricWordToken(
            text = "Forever",
            startMs = 1000L,
            endMs = 1600L,
            glyphs = emptyList(),
            hasTrailingSpace = false
        )

        val peak = LyricsTimingEngine.computeWordVisualState(word, 1300L, isLineActive = true, isLinePast = false, isSynchronized = true)
        val atEnd = LyricsTimingEngine.computeWordVisualState(word, 1600L, isLineActive = true, isLinePast = false, isSynchronized = true)
        val midTailPastLine = LyricsTimingEngine.computeWordVisualState(word, 1700L, isLineActive = false, isLinePast = true, isSynchronized = true)
        val afterTailPastLine = LyricsTimingEngine.computeWordVisualState(word, 2100L, isLineActive = false, isLinePast = true, isSynchronized = true)

        assertTrue(peak.scale >= 1.12f)
        assertTrue(atEnd.scale in 1.05f..1.15f)
        assertTrue("Expected smooth tail release instead of instant drop, got ${midTailPastLine.scale}", midTailPastLine.scale > 1.01f && midTailPastLine.scale < atEnd.scale)
        assertTrue(midTailPastLine.glowIntensity > 0.2f)
        assertEquals(1.0f, afterTailPastLine.scale, 0.001f)
        assertEquals(0f, afterTailPastLine.glowIntensity, 0.001f)
    }

    @Test
    fun `computeWordVisualState keeps long words within target peak scale range`() {
        val longWord = LyricWordToken(
            text = "Extraordinary",
            startMs = 1000L,
            endMs = 2000L,
            glyphs = emptyList()
        )
        val peak = LyricsTimingEngine.computeWordVisualState(longWord, 1500L, isLineActive = true, isLinePast = false, isSynchronized = true)
        assertTrue("Long word peak scale ${peak.scale} should stay in 1.12..1.18", peak.scale in 1.12f..1.18f)
    }

    @Test
    fun `PlaybackPositionInterpolator smoothly interpolates 120fps sub-ticks without backward jitter`() {
        val interpolator = PlaybackPositionInterpolator()

        // Frame 0: tick reports 1000ms
        assertEquals(1000L, interpolator.update(reportedPositionMs = 1000L, isPlaying = true, frameTimeMs = 10_000L))
        // Sub-frames at 120Hz (~8ms, ~16ms, ~25ms) before next 33ms ExoPlayer tick
        assertEquals(1008L, interpolator.update(reportedPositionMs = 1000L, isPlaying = true, frameTimeMs = 10_008L))
        assertEquals(1016L, interpolator.update(reportedPositionMs = 1000L, isPlaying = true, frameTimeMs = 10_016L))
        assertEquals(1025L, interpolator.update(reportedPositionMs = 1000L, isPlaying = true, frameTimeMs = 10_025L))

        // Frame extrapolates to 1036ms at t=36ms, then late tick arrives reporting 1033ms
        assertEquals(1036L, interpolator.update(reportedPositionMs = 1000L, isPlaying = true, frameTimeMs = 10_036L))
        val afterLateTick = interpolator.update(reportedPositionMs = 1033L, isPlaying = true, frameTimeMs = 10_038L)
        assertTrue("Expected monotonic position >= 1036, got $afterLateTick", afterLateTick >= 1036L)

        // Stall bound: if reported position freezes for 1 second while isPlaying=true, cap extrapolation
        val stalled = interpolator.update(reportedPositionMs = 1033L, isPlaying = true, frameTimeMs = 11_000L)
        assertEquals(1033L + LyricsTimingEngine.MAX_EXTRAPOLATION_MS, stalled)

        // Pausing immediately freezes at reported position
        val paused = interpolator.update(reportedPositionMs = 1033L, isPlaying = false, frameTimeMs = 11_050L)
        assertEquals(1033L, paused)

        // Seeking backward or far forward snaps immediately
        assertEquals(42_000L, interpolator.update(reportedPositionMs = 42_000L, isPlaying = true, frameTimeMs = 11_100L))
        assertEquals(12_000L, interpolator.update(reportedPositionMs = 12_000L, isPlaying = true, frameTimeMs = 11_116L))
    }

    @Test
    fun `findActiveLineIndex handles intro, active lines, and duplicate timestamps`() {
        val lines = listOf(
            LyricLine(5000L, "Line one"),
            LyricLine(10000L, "Line two"),
            LyricLine(10000L, "Line two alt"),
            LyricLine(15000L, "Line three")
        )

        assertEquals(-1, LyricsTimingEngine.findActiveLineIndex(lines, 1200L))
        assertEquals(0, LyricsTimingEngine.findActiveLineIndex(lines, 5000L))
        assertEquals(0, LyricsTimingEngine.findActiveLineIndex(lines, 9999L))
        assertEquals(2, LyricsTimingEngine.findActiveLineIndex(lines, 10000L))
        assertEquals(3, LyricsTimingEngine.findActiveLineIndex(lines, 25000L))
    }

    private fun assertEquals(expected: Long, actual: Long, tolerance: Long) {
        assertTrue(
            "Expected $expected ± $tolerance, but was $actual",
            kotlin.math.abs(expected - actual) <= tolerance
        )
    }
}
