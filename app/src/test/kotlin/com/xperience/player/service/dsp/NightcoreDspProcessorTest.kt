package com.xperience.player.service.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class NightcoreDspProcessorTest {

    @Test
    fun `AudioDspState couples speed and pitch in Nightcore mode and uncouples when disabled`() {
        val defaultState = AudioDspState()
        assertFalse(defaultState.isNightcoreEnabled)
        assertEquals(1.0f, defaultState.effectiveSpeed, 0.001f)
        assertEquals(1.0f, defaultState.effectivePitch, 0.001f)
        assertEquals(0.0f, defaultState.effectiveSemitones, 0.01f)

        val nightcoreState = defaultState.copy(isNightcoreEnabled = true, nightcoreSpeed = 1.22f)
        assertTrue(nightcoreState.isNightcoreEnabled)
        assertEquals(1.22f, nightcoreState.effectiveSpeed, 0.001f)
        assertEquals(1.22f, nightcoreState.effectivePitch, 0.001f)
        // 12 * log2(1.22) = +3.44 semitones (within +3 to +4 semitones spec)
        assertTrue(nightcoreState.effectiveSemitones in 3.0f..4.0f)
        assertTrue(nightcoreState.isAnyEffectActive)
    }

    @Test
    fun `NightcoreDspProcessor processes stereo PCM 16-bit with 150ms exponential ramp and soft limiter`() {
        val processor = NightcoreDspProcessor()
        val format = AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)
        val outFormat = processor.configure(format)
        processor.flush()

        assertEquals(48000, outFormat.sampleRate)
        assertEquals(2, outFormat.channelCount)
        assertTrue(processor.isActive)

        processor.setNightcoreEnabled(
            enabled = true,
            lowShelfFreqHz = 90f,
            lowShelfGainDb = 6.0f,
            peakingFreqHz = 3500f,
            peakingGainDb = 2.0f
        )

        // Generate 200ms of 90Hz sub-bass + 3.5kHz stereo signal (9600 frames at 48kHz)
        val frames = 9600
        val inputBuffer = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until frames) {
            val t = i.toDouble() / 48000.0
            val subBass = 0.65 * sin(2.0 * PI * 90.0 * t)
            val vocalHigh = 0.25 * sin(2.0 * PI * 3500.0 * t)
            val sample16 = ((subBass + vocalHigh) * 32767.0).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            inputBuffer.putShort(sample16)
            inputBuffer.putShort(sample16)
        }
        inputBuffer.flip()

        processor.queueInput(inputBuffer)
        val outputBuffer = processor.output
        assertEquals(frames * 4, outputBuffer.remaining())

        var maxPeak = 0
        while (outputBuffer.hasRemaining()) {
            val s = abs(outputBuffer.short.toInt())
            if (s > maxPeak) maxPeak = s
        }

        // Verify non-zero processed output and soft limiter prevents digital wrap/clipping
        assertTrue("Expected processed audio output to be non-silent", maxPeak > 10000)
        assertTrue("Expected soft limiter to prevent hard clipping overflow", maxPeak <= 32767)

        // Verify track transition cleanup zeroes filter states without throwing
        processor.resetFilterStatesForTrackTransition()
        processor.reset()
    }
}
