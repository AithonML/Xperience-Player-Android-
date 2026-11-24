package com.xperience.player.domain.model.visualizer

import com.xperience.player.domain.model.TrackItem
import com.xperience.player.service.AudioVisualizerSampler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioVisualizerSamplerTest {

    @Test
    fun sample_whenPlaying_returnsActiveEnergyAndBands() {
        val sampler = AudioVisualizerSampler()
        val track = TrackItem(id = "test_song_1", title = "Midnight City", artist = "M83", album = "Hurry Up")
        sampler.setTrack(track)

        val definition = VisualizerDefinition(
            sensitivity = 1.35,
            bassDeformation = 1.45,
            midDeformation = 1.15,
            trebleDeformation = 1.30
        )

        // Sample a few frames to let smoothing ramp up
        var lastFrame: AudioVisualizerSampler.SpectrumFrame? = null
        for (i in 0 until 5) {
            lastFrame = sampler.sample(
                currentPositionMs = 15000L + (i * 16L),
                durationMs = 240000L,
                isPlaying = true,
                definition = definition
            )
        }

        assertNotNull(lastFrame)
        val frame = lastFrame!!
        assertEquals(64, frame.bands.size)
        assertEquals(64, frame.peakHold.size)
        assertTrue("Bass energy should be > 0 when playing", frame.bassEnergy > 0f)
        assertTrue("Mid energy should be > 0 when playing", frame.midEnergy > 0f)
        assertTrue("Treble energy should be > 0 when playing", frame.trebleEnergy > 0f)

        // Verify bands are within valid ranges
        for (b in frame.bands) {
            assertTrue("Band energy must be >= 0", b >= 0f)
            assertTrue("Band energy must be <= 1.5", b <= 1.5f)
        }
    }

    @Test
    fun sample_whenPaused_decaysTowardsZero() {
        val sampler = AudioVisualizerSampler()
        val definition = VisualizerDefinition()

        val frame = sampler.sample(
            currentPositionMs = 0L,
            durationMs = 180000L,
            isPlaying = false,
            definition = definition
        )

        assertEquals(0f, frame.bassEnergy, 0.001f)
        assertEquals(0f, frame.midEnergy, 0.001f)
        assertEquals(0f, frame.trebleEnergy, 0.001f)
    }
}
