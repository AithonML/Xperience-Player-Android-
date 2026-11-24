package com.xperience.player.ui.component

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.media3.common.Player
import com.xperience.player.domain.model.PlaybackState
import com.xperience.player.service.AudioPlayerService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PillPerimeterProgressTest {

    @Test
    fun formatMiniPlayerTime_matchesPromptExampleAndEdgeCases() {
        // Prompt example: 3:07 (187s) / 4:14 (254s)
        assertEquals("3:07 / 4:14", formatMiniPlayerTime(positionMs = 187_000L, durationMs = 254_000L))
        assertEquals("0:00 / 4:14", formatMiniPlayerTime(positionMs = -500L, durationMs = 254_000L))
        assertEquals("4:14 / 4:14", formatMiniPlayerTime(positionMs = 300_000L, durationMs = 254_000L))
        assertEquals("0:12 / --:--", formatMiniPlayerTime(positionMs = 12_000L, durationMs = 0L))
        assertEquals("1:02:03 / 1:10:00", formatMiniPlayerTime(positionMs = 3_723_000L, durationMs = 4_200_000L))
    }

    @Test
    fun wrapFraction_keepsValuesInHalfOpenUnitInterval() {
        assertEquals(0f, wrapFraction(0f), 1e-5f)
        assertEquals(0f, wrapFraction(1f), 1e-5f)
        assertEquals(0f, wrapFraction(-1f), 1e-5f)
        assertEquals(0.25f, wrapFraction(1.25f), 1e-5f)
        assertEquals(0.75f, wrapFraction(-0.25f), 1e-5f)
        assertTrue(wrapFraction(-0.0001f) in 0f..<1f)
    }

    @Test
    fun shortestCircularDelta_bridgesTopCenterSeamInBothDirections() {
        assertEquals(0.10f, shortestCircularDelta(fromFraction = 0.95f, toFraction = 0.05f), 1e-4f)
        assertEquals(-0.10f, shortestCircularDelta(fromFraction = 0.05f, toFraction = 0.95f), 1e-4f)
        assertEquals(0.25f, shortestCircularDelta(fromFraction = 0.20f, toFraction = 0.45f), 1e-4f)
        assertEquals(-0.30f, shortestCircularDelta(fromFraction = 0.65f, toFraction = 0.35f), 1e-4f)
    }

    @Test
    fun computeLoadingArc_remainsContinuousAndBoundedAcrossCycles() {
        var prev = computeLoadingArc(0f)
        assertEquals(0f, prev.startFraction, 1e-5f)
        assertEquals(0.08f, prev.sweepFraction, 1e-5f)

        val stepSeconds = 0.005f
        for (i in 1..1200) { // covers 6.0 seconds across multiple 1.05s cycles
            val t = i * stepSeconds
            val current = computeLoadingArc(t)
            assertTrue("startFraction out of [0, 1): ${current.startFraction}", current.startFraction in 0f..<1f)
            assertTrue("sweepFraction out of [0.08, 0.52]: ${current.sweepFraction}", current.sweepFraction in 0.079f..0.521f)

            val startStep = shortestCircularDelta(prev.startFraction, current.startFraction)
            val sweepStep = abs(current.sweepFraction - prev.sweepFraction)
            assertTrue("Loading start jumped discontinuity ($startStep) at t=$t", abs(startStep) < 0.03f)
            assertTrue("Loading sweep jumped discontinuity ($sweepStep) at t=$t", sweepStep < 0.03f)
            prev = current
        }

        // Negative elapsed time clamps cleanly
        val negative = computeLoadingArc(-2f)
        assertEquals(0f, negative.startFraction, 1e-5f)
        assertEquals(0.08f, negative.sweepFraction, 1e-5f)
    }

    @Test
    fun audioLoadingState_combinesLoadingFlagAndExoPlayerBuffering() {
        assertFalse(PlaybackState().isLoading)
        assertTrue(AudioPlayerService.resolveLoadingState(isLoadingMedia = true, playerPlaybackState = Player.STATE_IDLE))
        assertTrue(AudioPlayerService.resolveLoadingState(isLoadingMedia = true, playerPlaybackState = Player.STATE_READY))
        assertTrue(AudioPlayerService.resolveLoadingState(isLoadingMedia = false, playerPlaybackState = Player.STATE_BUFFERING))
        assertFalse(AudioPlayerService.resolveLoadingState(isLoadingMedia = false, playerPlaybackState = Player.STATE_READY))
        assertFalse(AudioPlayerService.resolveLoadingState(isLoadingMedia = false, playerPlaybackState = Player.STATE_ENDED))
    }

    @Test
    fun perimeterState_playbackUpdatesAreSnapshotObservableForDrawAndTimeText() {
        val noOpHaptics = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
        }
        val state = PillPerimeterState(
            initialAmbientColor = Color.Cyan,
            initialPlaybackState = PlaybackState(
                isPlaying = true,
                isLoading = false,
                currentPositionMs = 0L,
                durationMs = 254_000L,
            ),
            hapticFeedback = noOpHaptics,
            borderGrabBandPx = 44f,
        )

        var invalidatedByPlayback = false
        val handle = Snapshot.registerApplyObserver { changed, _ ->
            if (changed.isNotEmpty()) {
                invalidatedByPlayback = true
            }
        }
        try {
            val readObjects = mutableSetOf<Any>()
            Snapshot.observe(readObserver = { readObjects.add(it) }) {
                assertEquals(0f, state.displayFraction, 1e-5f)
                assertEquals(0L, state.displayedPositionMs)
            }
            assertTrue("Expected snapshot state reads when reading displayFraction/displayedPositionMs", readObjects.size >= 2)

            Snapshot.withMutableSnapshot {
                state.latestPositionMs = 187_000L
                state.latestPlaybackFraction = 187_000f / 254_000f
            }

            assertTrue("Updating playback progress must emit Compose snapshot invalidation", invalidatedByPlayback)
            assertEquals("3:07 / 4:14", formatMiniPlayerTime(state.displayedPositionMs, state.latestDurationMs))
            assertEquals(187_000f / 254_000f, state.displayFraction, 1e-4f)
        } finally {
            handle.dispose()
        }
    }
}
