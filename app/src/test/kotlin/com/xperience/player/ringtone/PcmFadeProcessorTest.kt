package com.xperience.player.ringtone

import com.xperience.player.service.ringtone.AudioTrimmerEngine
import com.xperience.player.service.ringtone.PcmFadeProcessor
import com.xperience.player.service.ringtone.TrimRange
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PcmFadeProcessorTest {

    @Test
    fun `computeEffectiveFadeFrames caps fades at half duration for short clips`() {
        // Normal 10s clip @ 44.1kHz with 1.5s fades -> 66150 frames each
        val (inNormal, outNormal) = PcmFadeProcessor.computeEffectiveFadeFrames(
            totalFrames = 441_000L,
            sampleRate = 44_100,
            fadeInEnabled = true,
            fadeOutEnabled = true,
            fadeDurationMs = 1_500L
        )
        assertEquals(66_150L, inNormal)
        assertEquals(66_150L, outNormal)

        // Short 1s clip @ 44.1kHz with 1.5s fades on both ends -> capped to 22050 frames each (never overlaps)
        val (inShort, outShort) = PcmFadeProcessor.computeEffectiveFadeFrames(
            totalFrames = 44_100L,
            sampleRate = 44_100,
            fadeInEnabled = true,
            fadeOutEnabled = true,
            fadeDurationMs = 1_500L
        )
        assertEquals(22_050L, inShort)
        assertEquals(22_050L, outShort)
        assertTrue(inShort + outShort <= 44_100L)

        // Disabled fades produce zero
        val (inOff, outOff) = PcmFadeProcessor.computeEffectiveFadeFrames(
            totalFrames = 441_000L,
            sampleRate = 44_100,
            fadeInEnabled = false,
            fadeOutEnabled = false,
            fadeDurationMs = 1_500L
        )
        assertEquals(0L, inOff)
        assertEquals(0L, outOff)
    }

    @Test
    fun `raised cosine gain curve starts at zero reaches unity in plateau and decays smoothly to zero`() {
        val total = 1000L
        val fadeIn = 200L
        val fadeOut = 200L

        assertEquals(0.0f, PcmFadeProcessor.gainAtFrame(0L, total, fadeIn, fadeOut), 0.001f)
        assertEquals(0.5f, PcmFadeProcessor.gainAtFrame(100L, total, fadeIn, fadeOut), 0.01f)
        assertEquals(1.0f, PcmFadeProcessor.gainAtFrame(200L, total, fadeIn, fadeOut), 0.001f)
        assertEquals(1.0f, PcmFadeProcessor.gainAtFrame(500L, total, fadeIn, fadeOut), 0.001f)
        assertEquals(1.0f, PcmFadeProcessor.gainAtFrame(799L, total, fadeIn, fadeOut), 0.001f)
        assertTrue(PcmFadeProcessor.gainAtFrame(900L, total, fadeIn, fadeOut) in 0.45f..0.55f)
        assertEquals(0.0f, PcmFadeProcessor.gainAtFrame(999L, total, fadeIn, fadeOut), 0.001f)

        // Monotonic increase during fade-in
        var prev = -1f
        for (f in 0L until fadeIn) {
            val g = PcmFadeProcessor.gainAtFrame(f, total, fadeIn, fadeOut)
            assertTrue("Expected monotonic rise at frame $f", g >= prev)
            prev = g
        }
        // Monotonic decrease during fade-out
        prev = 2f
        for (f in (total - fadeOut) until total) {
            val g = PcmFadeProcessor.gainAtFrame(f, total, fadeIn, fadeOut)
            assertTrue("Expected monotonic decay at frame $f", g <= prev)
            prev = g
        }
    }

    @Test
    fun `chunked PCM16 fade processing matches single-pass and byte-buffer processing bit-for-bit`() {
        val channels = 2
        val totalFrames = 600
        val fadeInFrames = 150L
        val fadeOutFrames = 150L

        // Alternating extreme values to verify zero overflow/clipping
        val source = ShortArray(totalFrames * channels) { idx ->
            if (idx % 2 == 0) 30_000.toShort() else (-30_000).toShort()
        }

        // 1. Single-pass ShortArray
        val singlePass = source.copyOf()
        PcmFadeProcessor.applyFadeInPlace(
            samples = singlePass,
            sampleCount = singlePass.size,
            channelCount = channels,
            startFrameIndex = 0L,
            totalFrames = totalFrames.toLong(),
            fadeInFrames = fadeInFrames,
            fadeOutFrames = fadeOutFrames
        )

        // Verify start & end are silenced and middle plateau is untouched
        assertEquals(0.toShort(), singlePass[0])
        assertEquals(0.toShort(), singlePass[1])
        assertEquals(30_000.toShort(), singlePass[300 * channels])
        assertEquals((-30_000).toShort(), singlePass[300 * channels + 1])
        assertEquals(0.toShort(), singlePass[(totalFrames - 1) * channels])
        assertEquals(0.toShort(), singlePass[(totalFrames - 1) * channels + 1])

        // 2. Chunked ShortArray across uneven chunk sizes (e.g. 73 frames)
        val chunked = ShortArray(source.size)
        var frameOffset = 0
        while (frameOffset < totalFrames) {
            val chunkFrames = minOf(73, totalFrames - frameOffset)
            val slice = source.copyOfRange(frameOffset * channels, (frameOffset + chunkFrames) * channels)
            PcmFadeProcessor.applyFadeInPlace(
                samples = slice,
                sampleCount = slice.size,
                channelCount = channels,
                startFrameIndex = frameOffset.toLong(),
                totalFrames = totalFrames.toLong(),
                fadeInFrames = fadeInFrames,
                fadeOutFrames = fadeOutFrames
            )
            slice.copyInto(chunked, destinationOffset = frameOffset * channels)
            frameOffset += chunkFrames
        }
        assertArrayEquals(singlePass, chunked)

        // 3. Little-endian PCM byte array processing matches ShortArray identically
        val pcmBytes = ByteArray(source.size * 2)
        for (i in source.indices) {
            val s = source[i].toInt()
            pcmBytes[i * 2] = (s and 0xFF).toByte()
            pcmBytes[i * 2 + 1] = ((s ushr 8) and 0xFF).toByte()
        }
        PcmFadeProcessor.applyFadeToPcm16LeBytes(
            bytes = pcmBytes,
            lengthBytes = pcmBytes.size,
            channelCount = channels,
            startFrameIndex = 0L,
            totalFrames = totalFrames.toLong(),
            fadeInFrames = fadeInFrames,
            fadeOutFrames = fadeOutFrames
        )
        val decodedShorts = ShortArray(source.size) { i ->
            val lo = pcmBytes[i * 2].toInt() and 0xFF
            val hi = pcmBytes[i * 2 + 1].toInt() shl 8
            (hi or lo).toShort()
        }
        assertArrayEquals(singlePass, decodedShorts)
    }

    @Test
    fun `previewGainAtPositionMs computes smooth envelope for live preview playback`() {
        val range = TrimRange(startMs = 10_000L, endMs = 20_000L)
        val startGain = PcmFadeProcessor.previewGainAtPositionMs(
            positionMs = 10_000L,
            range = range,
            fadeInEnabled = true,
            fadeOutEnabled = true,
            fadeDurationMs = 1_500L
        )
        val midGain = PcmFadeProcessor.previewGainAtPositionMs(
            positionMs = 15_000L,
            range = range,
            fadeInEnabled = true,
            fadeOutEnabled = true,
            fadeDurationMs = 1_500L
        )
        val endGain = PcmFadeProcessor.previewGainAtPositionMs(
            positionMs = 20_000L,
            range = range,
            fadeInEnabled = true,
            fadeOutEnabled = true,
            fadeDurationMs = 1_500L
        )
        assertTrue(startGain < 0.05f)
        assertEquals(1.0f, midGain, 0.001f)
        assertTrue(endGain < 0.05f)
    }

    @Test
    fun `AAC safe sample rate mapping and hi-res PCM resampling work cleanly`() {
        assertEquals(44_100, AudioTrimmerEngine.chooseAacSafeSampleRate(44_100))
        assertEquals(48_000, AudioTrimmerEngine.chooseAacSafeSampleRate(48_000))
        assertEquals(48_000, AudioTrimmerEngine.chooseAacSafeSampleRate(96_000))
        assertEquals(44_100, AudioTrimmerEngine.chooseAacSafeSampleRate(88_200))
        assertEquals(48_000, AudioTrimmerEngine.chooseAacSafeSampleRate(192_000))
        assertEquals(16_000, AudioTrimmerEngine.chooseAacSafeSampleRate(4_000))
        assertEquals(44_100, AudioTrimmerEngine.chooseAacSafeSampleRate(0))

        // Resample 96kHz stereo constant ramp -> 48kHz stereo halves frame count accurately
        val srcFrames = 200
        val input = ShortArray(srcFrames * 2) { i ->
            val frame = i / 2
            (frame * 100).toShort()
        }
        val resampled = AudioTrimmerEngine.resampleLinearInterleaved(
            input = input,
            channels = 2,
            srcRate = 96_000,
            dstRate = 48_000
        )
        assertEquals(100 * 2, resampled.size)
        assertEquals(0.toShort(), resampled[0])
        assertTrue(abs(resampled[50 * 2].toInt() - 10_000) <= 100)
    }
}
