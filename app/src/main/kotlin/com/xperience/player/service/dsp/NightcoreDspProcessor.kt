package com.xperience.player.service.dsp

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Modular Real-Time PCM Audio DSP Processor for Media3 / ExoPlayer.
 *
 * Implements the 1-Click "Nightcore" Equalizer & Dynamics pipeline in series,
 * plus a 5-band Graphic Equalizer, Bass/Treble tone shelves, and a 150ms
 * exponential anti-click parameter smoothing ramp:
 *
 * 1. **Low-Shelf Filter (Sub-bass punch)**:
 *    - Frequency: `90 Hz` (configurable within `85 Hz - 100 Hz`)
 *    - Gain: `+6.0 dB` (configurable `+5 dB to +7 dB`) when Nightcore is active
 *    - Q factor: `0.7`
 * 2. **Peaking Filter (Vocal clarity / High brightness "Brillo")**:
 *    - Frequency: `3500 Hz` (`3.5 kHz`)
 *    - Gain: `+2.0 dB` when Nightcore is active
 *    - Q factor: `1.0`
 * 3. **5-Band User Equalizer + Tone Stack (Graves / Agudos)**:
 *    - Bands at `60 Hz`, `230 Hz`, `910 Hz`, `3.6 kHz`, `14 kHz`
 *    - Independent Bass (`95 Hz`) and Treble (`8.5 kHz`) tone adjustments
 * 4. **Dynamics Compressor (Soft Limiter)**:
 *    - Threshold: `-6.0 dB`
 *    - Ratio: `4:1`
 *    - Attack: `0.003s` (`3 ms`)
 *    - Release: `0.15s` (`150 ms`)
 *    - Prevents clipping or harmonic distortion from the sub-bass boost.
 * 5. **150ms Exponential Anti-Click Smoothing Ramp**:
 *    - Smoothly ramps all filter gains and compressor mix over `150ms`
 *      so toggling Nightcore or EQ produces zero digital clicks or pops.
 */
@OptIn(UnstableApi::class)
class NightcoreDspProcessor : BaseAudioProcessor() {

    companion object {
        // Nightcore DSP specification constants
        const val DEFAULT_NIGHTCORE_SPEED = 1.22f
        const val MIN_NIGHTCORE_SPEED = 1.20f
        const val MAX_NIGHTCORE_SPEED = 1.25f

        const val NIGHTCORE_LOW_SHELF_FREQ_HZ = 90.0f // 85 Hz - 100 Hz range
        const val NIGHTCORE_LOW_SHELF_GAIN_DB = 6.0f  // +5 dB to +7 dB range
        const val NIGHTCORE_LOW_SHELF_Q = 0.707f      // Q = 0.7

        const val NIGHTCORE_PEAKING_FREQ_HZ = 3500.0f // 3.5 kHz
        const val NIGHTCORE_PEAKING_GAIN_DB = 2.0f    // +2 dB
        const val NIGHTCORE_PEAKING_Q = 1.0f

        const val COMPRESSOR_THRESHOLD_DB = -6.0f     // -6 dB
        const val COMPRESSOR_RATIO = 4.0f             // 4:1
        const val COMPRESSOR_ATTACK_SEC = 0.003f      // 3 ms
        const val COMPRESSOR_RELEASE_SEC = 0.150f     // 150 ms
        const val COMPRESSOR_KNEE_DB = 3.0f

        // 150ms exponential ramp duration (tau = 150ms / 4.60517 so 99% target is reached at 150ms)
        const val RAMP_DURATION_SEC = 0.150f
        private const val RAMP_TAU_SEC = RAMP_DURATION_SEC / 4.60517f

        val EQ_BAND_FREQUENCIES_HZ = floatArrayOf(60f, 230f, 910f, 3600f, 14000f)
        private const val USER_BASS_FREQ_HZ = 95f
        private const val USER_TREBLE_FREQ_HZ = 8500f
        private const val MAX_CHANNELS = 8
    }

    // Volatile target parameters updated from UI / AudioDspController thread
    @Volatile private var targetNightcoreActive: Boolean = false
    @Volatile private var targetNightcoreLowShelfFreqHz: Float = NIGHTCORE_LOW_SHELF_FREQ_HZ
    @Volatile private var targetNightcoreLowShelfGainDb: Float = NIGHTCORE_LOW_SHELF_GAIN_DB
    @Volatile private var targetNightcorePeakingFreqHz: Float = NIGHTCORE_PEAKING_FREQ_HZ
    @Volatile private var targetNightcorePeakingGainDb: Float = NIGHTCORE_PEAKING_GAIN_DB

    @Volatile private var targetEqEnabled: Boolean = true
    @Volatile private var targetUserBassDb: Float = 0f
    @Volatile private var targetUserTrebleDb: Float = 0f
    @Volatile private var targetCompressorEnabled: Boolean = true
    private val targetBandGainsDb = FloatArray(EQ_BAND_FREQUENCIES_HZ.size)
    private val paramLock = Any()

    // Exponentially smoothed active DSP parameters (audio thread)
    private var currentNightcoreMix: Float = 0f
    private var currentLowShelfGainDb: Float = 0f
    private var currentPeakingGainDb: Float = 0f
    private var currentUserBassDb: Float = 0f
    private var currentUserTrebleDb: Float = 0f
    private val currentBandGainsDb = FloatArray(EQ_BAND_FREQUENCIES_HZ.size)
    private var currentCompressorMix: Float = 0f

    // Last-computed gain snapshots to avoid unnecessary trigonometric recalculations at steady state
    private var computedLowShelfGainDb: Float = Float.NaN
    private var computedPeakingGainDb: Float = Float.NaN
    private var computedUserBassDb: Float = Float.NaN
    private var computedUserTrebleDb: Float = Float.NaN
    private val computedBandGainsDb = FloatArray(EQ_BAND_FREQUENCIES_HZ.size) { Float.NaN }

    // Biquad filter stages in series
    private val nightcoreLowShelfFilter = BiquadStage(MAX_CHANNELS)
    private val nightcorePeakingFilter = BiquadStage(MAX_CHANNELS)
    private val userBassShelfFilter = BiquadStage(MAX_CHANNELS)
    private val userTrebleShelfFilter = BiquadStage(MAX_CHANNELS)
    private val userEqBandFilters = Array(EQ_BAND_FREQUENCIES_HZ.size) { BiquadStage(MAX_CHANNELS) }

    // Dynamics compressor state
    private var compressorEnvDb: Float = 0f
    private var attackCoeff: Float = 0f
    private var releaseCoeff: Float = 0f
    private var rampAlphaPerSample: Float = 0.001f

    // Reusable per-frame channel scratch buffer (zero allocations on audio thread)
    private val frameSamples = FloatArray(MAX_CHANNELS)

    /**
     * Enables or disables 1-Click Nightcore DSP filter & dynamics chain.
     * Filter gains ramp exponentially over 150ms to prevent clicks or pops.
     */
    fun setNightcoreEnabled(
        enabled: Boolean,
        lowShelfFreqHz: Float = NIGHTCORE_LOW_SHELF_FREQ_HZ,
        lowShelfGainDb: Float = NIGHTCORE_LOW_SHELF_GAIN_DB,
        peakingFreqHz: Float = NIGHTCORE_PEAKING_FREQ_HZ,
        peakingGainDb: Float = NIGHTCORE_PEAKING_GAIN_DB
    ) {
        synchronized(paramLock) {
            targetNightcoreActive = enabled
            targetNightcoreLowShelfFreqHz = lowShelfFreqHz.coerceIn(85f, 100f)
            targetNightcoreLowShelfGainDb = lowShelfGainDb.coerceIn(5f, 7f)
            targetNightcorePeakingFreqHz = peakingFreqHz.coerceIn(2500f, 5000f)
            targetNightcorePeakingGainDb = peakingGainDb.coerceIn(0f, 6f)
        }
    }

    fun isNightcoreEnabled(): Boolean = targetNightcoreActive

    fun setEqualizerEnabled(enabled: Boolean) {
        synchronized(paramLock) {
            targetEqEnabled = enabled
        }
    }

    fun setBandGainDb(bandIndex: Int, gainDb: Float) {
        if (bandIndex !in targetBandGainsDb.indices) return
        synchronized(paramLock) {
            targetBandGainsDb[bandIndex] = gainDb.coerceIn(-12f, 12f)
        }
    }

    fun setAllBandGainsDb(gainsDb: FloatArray) {
        synchronized(paramLock) {
            for (i in targetBandGainsDb.indices) {
                targetBandGainsDb[i] = gainsDb.getOrElse(i) { 0f }.coerceIn(-12f, 12f)
            }
        }
    }

    fun setBassAndTrebleDb(bassDb: Float, trebleDb: Float) {
        synchronized(paramLock) {
            targetUserBassDb = bassDb.coerceIn(-12f, 12f)
            targetUserTrebleDb = trebleDb.coerceIn(-12f, 12f)
        }
    }

    fun setCompressorEnabled(enabled: Boolean) {
        synchronized(paramLock) {
            targetCompressorEnabled = enabled
        }
    }

    /**
     * Resets all filter delay lines and dynamics envelopes on track transition
     * so no residual audio energy leaks between songs.
     */
    fun resetFilterStatesForTrackTransition() {
        synchronized(paramLock) {
            clearDelayLinesAndEnvelopes()
        }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val encoding = inputAudioFormat.encoding
        if (encoding != C.ENCODING_PCM_16BIT && encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        if (inputAudioFormat.channelCount < 1 || inputAudioFormat.channelCount > MAX_CHANNELS || inputAudioFormat.sampleRate <= 0) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }

        val sr = inputAudioFormat.sampleRate.toFloat()
        updateTimeConstants(sr)
        forceRecalculateAllCoefficients(sr)
        return inputAudioFormat
    }

    private fun updateTimeConstants(sampleRate: Float) {
        val safeSr = sampleRate.coerceAtLeast(8000f)
        attackCoeff = exp(-1.0f / (COMPRESSOR_ATTACK_SEC * safeSr))
        releaseCoeff = exp(-1.0f / (COMPRESSOR_RELEASE_SEC * safeSr))
        rampAlphaPerSample = (1.0f - exp(-1.0f / (RAMP_TAU_SEC * safeSr))).coerceIn(0.00005f, 0.05f)
    }

    private fun forceRecalculateAllCoefficients(sampleRate: Float) {
        val safeSr = sampleRate.coerceAtLeast(8000f)
        computedLowShelfGainDb = Float.NaN
        computedPeakingGainDb = Float.NaN
        computedUserBassDb = Float.NaN
        computedUserTrebleDb = Float.NaN
        for (i in computedBandGainsDb.indices) {
            computedBandGainsDb[i] = Float.NaN
        }
        maybeUpdateFilterCoefficients(safeSr, force = true)
    }

    private fun maybeUpdateFilterCoefficients(sampleRate: Float, force: Boolean = false) {
        if (force || abs(currentLowShelfGainDb - computedLowShelfGainDb) > 0.025f) {
            computedLowShelfGainDb = currentLowShelfGainDb
            nightcoreLowShelfFilter.configureLowShelf(
                sampleRate = sampleRate,
                freqHz = targetNightcoreLowShelfFreqHz,
                gainDb = currentLowShelfGainDb,
                q = NIGHTCORE_LOW_SHELF_Q
            )
        }

        if (force || abs(currentPeakingGainDb - computedPeakingGainDb) > 0.025f) {
            computedPeakingGainDb = currentPeakingGainDb
            nightcorePeakingFilter.configurePeaking(
                sampleRate = sampleRate,
                freqHz = targetNightcorePeakingFreqHz,
                gainDb = currentPeakingGainDb,
                q = NIGHTCORE_PEAKING_Q
            )
        }

        if (force || abs(currentUserBassDb - computedUserBassDb) > 0.025f) {
            computedUserBassDb = currentUserBassDb
            userBassShelfFilter.configureLowShelf(
                sampleRate = sampleRate,
                freqHz = USER_BASS_FREQ_HZ,
                gainDb = currentUserBassDb,
                q = 0.707f
            )
        }

        if (force || abs(currentUserTrebleDb - computedUserTrebleDb) > 0.025f) {
            computedUserTrebleDb = currentUserTrebleDb
            userTrebleShelfFilter.configureHighShelf(
                sampleRate = sampleRate,
                freqHz = USER_TREBLE_FREQ_HZ,
                gainDb = currentUserTrebleDb,
                q = 0.707f
            )
        }

        for (i in EQ_BAND_FREQUENCIES_HZ.indices) {
            val bandGain = currentBandGainsDb[i]
            if (force || abs(bandGain - computedBandGainsDb[i]) > 0.025f) {
                computedBandGainsDb[i] = bandGain
                val q = if (i == 0 || i == EQ_BAND_FREQUENCIES_HZ.lastIndex) 0.8f else 1.0f
                userEqBandFilters[i].configurePeaking(
                    sampleRate = sampleRate,
                    freqHz = EQ_BAND_FREQUENCIES_HZ[i],
                    gainDb = bandGain,
                    q = q
                )
            }
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val format = inputAudioFormat
        val channelCount = format.channelCount.coerceIn(1, MAX_CHANNELS)
        val sampleRate = format.sampleRate.toFloat().coerceAtLeast(8000f)
        val isFloatPcm = format.encoding == C.ENCODING_PCM_FLOAT
        val bytesPerSample = if (isFloatPcm) 4 else 2
        val bytesPerFrame = bytesPerSample * channelCount
        val frameCount = remaining / bytesPerFrame

        val outputBuffer = replaceOutputBuffer(frameCount * bytesPerFrame)

        // Snapshot target parameters once per buffer
        val nightcoreOn: Boolean
        val targetLowShelfDb: Float
        val targetPeakDb: Float
        val eqOn: Boolean
        val targetBassDb: Float
        val targetTrebleDb: Float
        val compOn: Boolean
        val snapshotBands0: Float
        val snapshotBands1: Float
        val snapshotBands2: Float
        val snapshotBands3: Float
        val snapshotBands4: Float

        synchronized(paramLock) {
            nightcoreOn = targetNightcoreActive
            targetLowShelfDb = if (nightcoreOn) targetNightcoreLowShelfGainDb else 0f
            targetPeakDb = if (nightcoreOn) targetNightcorePeakingGainDb else 0f
            eqOn = targetEqEnabled
            targetBassDb = if (eqOn) targetUserBassDb else 0f
            targetTrebleDb = if (eqOn) targetUserTrebleDb else 0f
            compOn = nightcoreOn || (eqOn && targetCompressorEnabled)
            snapshotBands0 = if (eqOn) targetBandGainsDb[0] else 0f
            snapshotBands1 = if (eqOn) targetBandGainsDb[1] else 0f
            snapshotBands2 = if (eqOn) targetBandGainsDb[2] else 0f
            snapshotBands3 = if (eqOn) targetBandGainsDb[3] else 0f
            snapshotBands4 = if (eqOn) targetBandGainsDb[4] else 0f
        }

        val targetNcMix = if (nightcoreOn) 1f else 0f
        val targetCompMix = if (compOn) 1f else 0f
        val alpha = rampAlphaPerSample

        for (frameIdx in 0 until frameCount) {
            // 1. Exponential 150ms anti-click smoothing per sample frame
            currentNightcoreMix += (targetNcMix - currentNightcoreMix) * alpha
            currentLowShelfGainDb += (targetLowShelfDb - currentLowShelfGainDb) * alpha
            currentPeakingGainDb += (targetPeakDb - currentPeakingGainDb) * alpha
            currentUserBassDb += (targetBassDb - currentUserBassDb) * alpha
            currentUserTrebleDb += (targetTrebleDb - currentUserTrebleDb) * alpha
            currentBandGainsDb[0] += (snapshotBands0 - currentBandGainsDb[0]) * alpha
            currentBandGainsDb[1] += (snapshotBands1 - currentBandGainsDb[1]) * alpha
            currentBandGainsDb[2] += (snapshotBands2 - currentBandGainsDb[2]) * alpha
            currentBandGainsDb[3] += (snapshotBands3 - currentBandGainsDb[3]) * alpha
            currentBandGainsDb[4] += (snapshotBands4 - currentBandGainsDb[4]) * alpha
            currentCompressorMix += (targetCompMix - currentCompressorMix) * alpha

            // Refresh biquad coefficients every 16 frames (~0.33ms at 48kHz) during active ramp
            if ((frameIdx and 15) == 0) {
                maybeUpdateFilterCoefficients(sampleRate, force = false)
            }

            // 2. Decode PCM samples for all channels in this frame into normalized [-1.0, 1.0]
            for (ch in 0 until channelCount) {
                frameSamples[ch] = if (isFloatPcm) {
                    inputBuffer.float
                } else {
                    inputBuffer.short.toFloat() / 32768.0f
                }
            }

            // 3. Process through series Biquad filter chain
            val runNightcoreStages = abs(currentLowShelfGainDb) > 0.01f || abs(currentPeakingGainDb) > 0.01f
            val runBassStage = abs(currentUserBassDb) > 0.01f
            val runTrebleStage = abs(currentUserTrebleDb) > 0.01f

            var peakAbs = 0f
            for (ch in 0 until channelCount) {
                var s = frameSamples[ch]

                // Nightcore Stage 1: Low-Shelf Filter (Sub-bass punch: 90Hz, +6dB, Q=0.7)
                // Nightcore Stage 2: Peaking Filter (Vocal clarity/brillo: 3.5kHz, +2dB, Q=1.0)
                if (runNightcoreStages) {
                    s = nightcoreLowShelfFilter.processSample(s, ch)
                    s = nightcorePeakingFilter.processSample(s, ch)
                }

                // User Tone & 5-Band Graphic Equalizer stages
                if (runBassStage) {
                    s = userBassShelfFilter.processSample(s, ch)
                }
                if (runTrebleStage) {
                    s = userTrebleShelfFilter.processSample(s, ch)
                }
                for (b in userEqBandFilters.indices) {
                    if (abs(currentBandGainsDb[b]) > 0.01f) {
                        s = userEqBandFilters[b].processSample(s, ch)
                    }
                }

                frameSamples[ch] = s
                val absVal = abs(s)
                if (absVal > peakAbs) {
                    peakAbs = absVal
                }
            }

            // 4. Stage 3: Dynamics Compressor (Soft Limiter: Threshold -6dB, Ratio 4:1, Attack 3ms, Release 150ms)
            if (currentCompressorMix > 0.005f) {
                val compGain = computeCompressorGain(peakAbs)
                val effectiveGain = 1.0f + (compGain - 1.0f) * currentCompressorMix
                for (ch in 0 until channelCount) {
                    frameSamples[ch] *= effectiveGain
                }
            }

            // 5. Transparent soft-saturation ceiling guard & encode back to output buffer
            for (ch in 0 until channelCount) {
                val processed = softClipGuard(frameSamples[ch])
                if (isFloatPcm) {
                    outputBuffer.putFloat(processed)
                } else {
                    val pcm16 = (processed * 32767.0f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    outputBuffer.putShort(pcm16)
                }
            }
        }

        outputBuffer.flip()
    }

    /**
     * Dynamics Compressor gain computer with soft knee:
     * - Threshold: -6.0 dB
     * - Ratio: 4:1
     * - Attack: 0.003s (3ms)
     * - Release: 0.15s (150ms)
     */
    private fun computeCompressorGain(peakLinear: Float): Float {
        val safePeak = max(peakLinear, 1e-6f)
        val inputDb = 20.0f * log10(safePeak)

        val slope = 1.0f - (1.0f / COMPRESSOR_RATIO) // 0.75 for 4:1 ratio
        val halfKnee = COMPRESSOR_KNEE_DB * 0.5f
        val overshootDb = inputDb - COMPRESSOR_THRESHOLD_DB

        val targetGainReductionDb = when {
            overshootDb <= -halfKnee -> 0.0f
            overshootDb >= halfKnee -> overshootDb * slope
            else -> {
                val kneeVal = overshootDb + halfKnee
                slope * (kneeVal * kneeVal) / (2.0f * COMPRESSOR_KNEE_DB)
            }
        }

        compressorEnvDb = if (targetGainReductionDb > compressorEnvDb) {
            attackCoeff * compressorEnvDb + (1.0f - attackCoeff) * targetGainReductionDb
        } else {
            releaseCoeff * compressorEnvDb + (1.0f - releaseCoeff) * targetGainReductionDb
        }

        if (compressorEnvDb <= 0.001f) return 1.0f
        return 10.0f.pow(-compressorEnvDb / 20.0f)
    }

    /**
     * Transparent soft-limiter guard above 0.92 (-0.7 dBFS) asymptotically approaching 0.998
     * so extreme sub-bass peaks never wrap or hard-clip.
     */
    private fun softClipGuard(sample: Float): Float {
        if (sample.isNaN() || sample.isInfinite()) return 0f
        val absSample = abs(sample)
        if (absSample <= 0.92f) return sample
        val sign = if (sample >= 0f) 1f else -1f
        val excess = (absSample - 0.92f) / 0.08f
        return sign * (0.92f + 0.078f * tanh(excess))
    }

    override fun onFlush() {
        clearDelayLinesAndEnvelopes()
    }

    override fun onReset() {
        clearDelayLinesAndEnvelopes()
        currentNightcoreMix = 0f
        currentLowShelfGainDb = 0f
        currentPeakingGainDb = 0f
        currentUserBassDb = 0f
        currentUserTrebleDb = 0f
        for (i in currentBandGainsDb.indices) {
            currentBandGainsDb[i] = 0f
        }
    }

    private fun clearDelayLinesAndEnvelopes() {
        nightcoreLowShelfFilter.resetState()
        nightcorePeakingFilter.resetState()
        userBassShelfFilter.resetState()
        userTrebleShelfFilter.resetState()
        for (filter in userEqBandFilters) {
            filter.resetState()
        }
        compressorEnvDb = 0f
        for (i in frameSamples.indices) {
            frameSamples[i] = 0f
        }
    }

    /**
     * Direct Form II Transposed Biquad Filter stage supporting up to [maxChannels] channels.
     */
    private class BiquadStage(maxChannels: Int) {
        private var b0: Float = 1.0f
        private var b1: Float = 0.0f
        private var b2: Float = 0.0f
        private var a1: Float = 0.0f
        private var a2: Float = 0.0f

        private val z1 = FloatArray(maxChannels)
        private val z2 = FloatArray(maxChannels)

        fun resetState() {
            for (i in z1.indices) {
                z1[i] = 0f
                z2[i] = 0f
            }
        }

        fun setIdentity() {
            b0 = 1.0f
            b1 = 0.0f
            b2 = 0.0f
            a1 = 0.0f
            a2 = 0.0f
        }

        fun configureLowShelf(sampleRate: Float, freqHz: Float, gainDb: Float, q: Float) {
            if (abs(gainDb) < 0.005f) {
                setIdentity()
                return
            }
            val nyquist = sampleRate * 0.48f
            val clampedFreq = freqHz.coerceIn(15f, nyquist)
            val a = 10.0.pow(gainDb.toDouble() / 40.0)
            val w0 = 2.0 * PI * clampedFreq / sampleRate
            val cosW0 = cos(w0)
            val sinW0 = sin(w0)
            val alpha = sinW0 / (2.0 * q.coerceAtLeast(0.1f))
            val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha

            val a0 = (a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha
            val rawB0 = a * ((a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha)
            val rawB1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosW0)
            val rawB2 = a * ((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha)
            val rawA1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosW0)
            val rawA2 = (a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha

            b0 = (rawB0 / a0).toFloat()
            b1 = (rawB1 / a0).toFloat()
            b2 = (rawB2 / a0).toFloat()
            a1 = (rawA1 / a0).toFloat()
            a2 = (rawA2 / a0).toFloat()
        }

        fun configureHighShelf(sampleRate: Float, freqHz: Float, gainDb: Float, q: Float) {
            if (abs(gainDb) < 0.005f) {
                setIdentity()
                return
            }
            val nyquist = sampleRate * 0.48f
            val clampedFreq = freqHz.coerceIn(100f, nyquist)
            val a = 10.0.pow(gainDb.toDouble() / 40.0)
            val w0 = 2.0 * PI * clampedFreq / sampleRate
            val cosW0 = cos(w0)
            val sinW0 = sin(w0)
            val alpha = sinW0 / (2.0 * q.coerceAtLeast(0.1f))
            val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha

            val a0 = (a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha
            val rawB0 = a * ((a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha)
            val rawB1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosW0)
            val rawB2 = a * ((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha)
            val rawA1 = 2.0 * ((a - 1.0) - (a + 1.0) * cosW0)
            val rawA2 = (a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha

            b0 = (rawB0 / a0).toFloat()
            b1 = (rawB1 / a0).toFloat()
            b2 = (rawB2 / a0).toFloat()
            a1 = (rawA1 / a0).toFloat()
            a2 = (rawA2 / a0).toFloat()
        }

        fun configurePeaking(sampleRate: Float, freqHz: Float, gainDb: Float, q: Float) {
            if (abs(gainDb) < 0.005f) {
                setIdentity()
                return
            }
            val nyquist = sampleRate * 0.48f
            val clampedFreq = freqHz.coerceIn(20f, nyquist)
            val a = 10.0.pow(gainDb.toDouble() / 40.0)
            val w0 = 2.0 * PI * clampedFreq / sampleRate
            val cosW0 = cos(w0)
            val sinW0 = sin(w0)
            val alpha = sinW0 / (2.0 * q.coerceAtLeast(0.1f))

            val a0 = 1.0 + alpha / a
            val rawB0 = 1.0 + alpha * a
            val rawB1 = -2.0 * cosW0
            val rawB2 = 1.0 - alpha * a
            val rawA1 = -2.0 * cosW0
            val rawA2 = 1.0 - alpha / a

            b0 = (rawB0 / a0).toFloat()
            b1 = (rawB1 / a0).toFloat()
            b2 = (rawB2 / a0).toFloat()
            a1 = (rawA1 / a0).toFloat()
            a2 = (rawA2 / a0).toFloat()
        }

        fun processSample(input: Float, channel: Int): Float {
            val out = b0 * input + z1[channel]
            z1[channel] = b1 * input - a1 * out + z2[channel]
            z2[channel] = b2 * input - a2 * out
            return out
        }
    }
}
