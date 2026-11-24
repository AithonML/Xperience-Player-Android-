package com.xperience.player.ui.ambient

import com.xperience.player.data.VisualizerRepository
import com.xperience.player.domain.model.PlaybackRepeatMode
import com.xperience.player.domain.model.PlaybackState
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.domain.model.visualizer.VisualizerRenderMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientModeHelpersTest {

    private val sampleQueue = listOf(
        TrackItem(id = "t1", title = "Paparazzi", artist = "Lady Gaga", album = "The Fame", durationMs = 208_000L),
        TrackItem(id = "t2", title = "Starfucker", artist = "Slayyyter", album = "STARFUCKER", durationMs = 192_000L),
        TrackItem(id = "t3", title = "Froot", artist = "MARINA", album = "FROOT", durationMs = 331_000L)
    )

    @Test
    fun `formatAmbientTime formats minutes, hours, and clamps negative timestamps`() {
        assertEquals("0:00", formatAmbientTime(0L))
        assertEquals("0:00", formatAmbientTime(-5_000L))
        assertEquals("0:09", formatAmbientTime(9_000L))
        assertEquals("3:28", formatAmbientTime(208_000L))
        assertEquals("1:05:04", formatAmbientTime(3_904_000L))
    }

    @Test
    fun `formatRemainingTime formats countdown and clamps edge bounds safely`() {
        assertEquals("-2:14", formatRemainingTime(positionMs = 74_000L, durationMs = 208_000L))
        assertEquals("-0:00", formatRemainingTime(positionMs = 208_000L, durationMs = 208_000L))
        assertEquals("-0:00", formatRemainingTime(positionMs = 250_000L, durationMs = 208_000L))
        assertEquals("-0:00", formatRemainingTime(positionMs = 10_000L, durationMs = 0L))
        assertEquals("-3:28", formatRemainingTime(positionMs = -1_000L, durationMs = 208_000L))
        assertEquals("-1:00:00", formatRemainingTime(positionMs = 0L, durationMs = 3_600_000L))
    }

    @Test
    fun `resolveUpNextTrack respects repeat modes and wraps around queue cleanly`() {
        val midQueueRepeatAll = PlaybackState(
            currentTrack = sampleQueue[0],
            queue = sampleQueue,
            currentQueueIndex = 0,
            repeatMode = PlaybackRepeatMode.RepeatAll
        )
        assertEquals("t2", resolveUpNextTrack(midQueueRepeatAll)?.id)
        assertEquals(1, resolveUpNextQueueIndex(midQueueRepeatAll))

        val endQueueRepeatAll = midQueueRepeatAll.copy(currentTrack = sampleQueue[2], currentQueueIndex = 2)
        assertEquals("t1", resolveUpNextTrack(endQueueRepeatAll)?.id)
        assertEquals(0, resolveUpNextQueueIndex(endQueueRepeatAll))

        val endQueueRepeatOff = endQueueRepeatAll.copy(repeatMode = PlaybackRepeatMode.Off)
        assertNull(resolveUpNextTrack(endQueueRepeatOff))
        assertNull(resolveUpNextQueueIndex(endQueueRepeatOff))

        val repeatOneState = midQueueRepeatAll.copy(repeatMode = PlaybackRepeatMode.RepeatOne)
        assertEquals("t1", resolveUpNextTrack(repeatOneState)?.id)
        assertEquals(0, resolveUpNextQueueIndex(repeatOneState))

        // Falls back to matching currentTrack by ID when currentQueueIndex is unset (-1)
        val unsetIndexState = PlaybackState(
            currentTrack = sampleQueue[1],
            queue = sampleQueue,
            currentQueueIndex = -1,
            repeatMode = PlaybackRepeatMode.Off
        )
        assertEquals("t3", resolveUpNextTrack(unsetIndexState)?.id)
        assertEquals(2, resolveUpNextQueueIndex(unsetIndexState))

        assertNull(resolveUpNextTrack(PlaybackState()))
    }

    @Test
    fun `deriveAudioQualityLabel detects formats, streams, local audio, and explicit override`() {
        val base = sampleQueue.first()
        assertEquals("24-bit · 96kHz", deriveAudioQualityLabel(base, explicitLabel = " 24-bit · 96kHz "))
        assertEquals("FLAC · Lossless", deriveAudioQualityLabel(base.copy(audioUrl = "file:///music/song.flac")))
        assertEquals("Hi-Res Lossless", deriveAudioQualityLabel(base.copy(audioUrl = "file:///music/master.wav")))
        assertEquals("Opus · High Quality", deriveAudioQualityLabel(base.copy(audioUrl = "https://cdn.example/audio.opus")))
        assertEquals("AAC · High Quality", deriveAudioQualityLabel(base.copy(audioUrl = "file:///music/track.m4a")))
        assertEquals("MP3 · Local Audio", deriveAudioQualityLabel(base.copy(audioUrl = "/storage/emulated/0/a.mp3")))
        assertEquals("High Quality Stream", deriveAudioQualityLabel(base.copy(audioUrl = "https://music.youtube.com/watch?v=abc")))
        assertEquals("Local Audio", deriveAudioQualityLabel(base.copy(audioUrl = "content://media/external/audio/123")))
        assertEquals("Ambient Audio", deriveAudioQualityLabel(null))
    }

    @Test
    fun `computeBurnInShiftDp stays bounded and produces smooth non-static motion`() {
        assertEquals(0f to 0f, computeBurnInShiftDp(tickIndex = 0L, maxAmplitudeDp = 6f))
        assertEquals(0f to 0f, computeBurnInShiftDp(tickIndex = 5L, maxAmplitudeDp = 0f))

        val seenOffsets = mutableSetOf<Pair<Int, Int>>()
        for (tick in 1L..120L) {
            val (dx, dy) = computeBurnInShiftDp(tickIndex = tick, maxAmplitudeDp = 6f)
            assertTrue("dx=$dx out of [-6, 6] at tick=$tick", dx in -6f..6f)
            assertTrue("dy=$dy out of [-6, 6] at tick=$tick", dy in -6f..6f)
            seenOffsets.add((dx * 10).toInt() to (dy * 10).toInt())
        }
        assertTrue("Expected distinct burn-in shift coordinates over time", seenOffsets.size > 12)
    }

    @Test
    fun `resolveHorizontalSwipeAction classifies left, right, and sub-threshold drags`() {
        assertEquals(AmbientSwipeDirection.NONE, resolveHorizontalSwipeAction(totalDragX = 0f, thresholdPx = 100f))
        assertEquals(AmbientSwipeDirection.NONE, resolveHorizontalSwipeAction(totalDragX = -99.5f, thresholdPx = 100f))
        assertEquals(AmbientSwipeDirection.NONE, resolveHorizontalSwipeAction(totalDragX = 99.5f, thresholdPx = 100f))
        assertEquals(AmbientSwipeDirection.NEXT, resolveHorizontalSwipeAction(totalDragX = -100f, thresholdPx = 100f))
        assertEquals(AmbientSwipeDirection.PREVIOUS, resolveHorizontalSwipeAction(totalDragX = 120f, thresholdPx = 100f))
    }

    @Test
    fun `resolveActiveAmbientVisualizer enables fallback Aurora preset when global visualizer is Off`() {
        val presets = VisualizerRepository.BuiltInPresets
        val offPreset = presets.first { it.renderMode == VisualizerRenderMode.None }
        val terrainPreset = presets.first { it.renderMode == VisualizerRenderMode.NeonTerrainPeaks }

        // Disabled -> always returns None
        val whenOff = resolveActiveAmbientVisualizer(terrainPreset, presets, isVisualizerEnabled = false)
        assertEquals(VisualizerRenderMode.None, whenOff.renderMode)

        // Enabled with active preset -> preserves selected preset
        val whenOnActive = resolveActiveAmbientVisualizer(terrainPreset, presets, isVisualizerEnabled = true)
        assertEquals(VisualizerRenderMode.NeonTerrainPeaks, whenOnActive.renderMode)

        // Enabled while global preset is None -> picks AuroraPrismaticWaves
        val whenOnFallback = resolveActiveAmbientVisualizer(offPreset, presets, isVisualizerEnabled = true)
        assertEquals(VisualizerRenderMode.AuroraPrismaticWaves, whenOnFallback.renderMode)
    }

    @Test
    fun `resolveDefaultPanelMode and formatAmbientSubtitle handle metadata edge cases`() {
        assertEquals(AmbientPanelMode.LYRICS, resolveDefaultPanelMode(hasLyrics = true))
        assertEquals(AmbientPanelMode.CLOCK_METADATA, resolveDefaultPanelMode(hasLyrics = false))

        assertEquals(
            "Lady Gaga · The Fame",
            formatAmbientSubtitle(sampleQueue[0])
        )
        assertEquals(
            "Lady Gaga",
            formatAmbientSubtitle(sampleQueue[0].copy(album = "Unknown Album"))
        )
        assertEquals(
            "Lady Gaga",
            formatAmbientSubtitle(sampleQueue[0].copy(album = "Paparazzi"))
        )
        assertEquals(
            "Lady Gaga",
            formatAmbientSubtitle(sampleQueue[0].copy(album = "Lady Gaga"))
        )
    }
}
