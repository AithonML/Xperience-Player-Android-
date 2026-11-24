package com.xperience.player.service.ringtone

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Pure-Kotlin PCM 16-bit signed little-endian fade-in / fade-out DSP processor.
 *
 * Uses a smooth raised-cosine (Hann half-window) gain curve:
 *   gain(t) = 0.5 * (1 - cos(pi * t))   for t in [0, 1]
 *
 * Benefits over linear ramps:
 * - Zero derivative discontinuity at t=0 and t=1 (eliminates audible clicks/pops at clip edges).
 * - Smooth perceptual volume rise & decay for ringtones, notifications, and alarms.
 * - Automatically caps fade-in and fade-out durations to at most half the total clip length
 *   so short clips never invert or clip.
 */
object PcmFadeProcessor {

    /**
     * Computes effective (fadeInFrames, fadeOutFrames) safely clamped so their sum never exceeds [totalFrames].
     */
    fun computeEffectiveFadeFrames(
        totalFrames: Long,
        sampleRate: Int,
        fadeInEnabled: Boolean,
        fadeOutEnabled: Boolean,
        fadeDurationMs: Long
    ): Pair<Long, Long> {
        if (totalFrames <= 0L || sampleRate <= 0 || fadeDurationMs <= 0L) {
            return 0L to 0L
        }
        val requestedFrames = ((fadeDurationMs.coerceAtLeast(0L) * sampleRate.toLong()) / 1_000L)
            .coerceAtLeast(0L)
        if (requestedFrames == 0L) return 0L to 0L

        val maxSingleFade = if (fadeInEnabled && fadeOutEnabled) {
            totalFrames / 2L
        } else {
            totalFrames
        }

        val fadeInFrames = if (fadeInEnabled) minOf(requestedFrames, maxSingleFade) else 0L
        val fadeOutFrames = if (fadeOutEnabled) minOf(requestedFrames, maxSingleFade) else 0L
        return fadeInFrames to fadeOutFrames
    }

    /**
     * Computes the raised-cosine gain multiplier in [0f, 1f] for a specific frame index.
     */
    fun gainAtFrame(
        frameIndex: Long,
        totalFrames: Long,
        fadeInFrames: Long,
        fadeOutFrames: Long
    ): Float {
        if (totalFrames <= 0L) return 1f
        if (frameIndex < 0L || frameIndex >= totalFrames) return 0f

        var gain = 1.0
        if (fadeInFrames > 0L && frameIndex < fadeInFrames) {
            val t = frameIndex.toDouble() / fadeInFrames.toDouble()
            gain *= 0.5 * (1.0 - cos(PI * t))
        }
        if (fadeOutFrames > 0L) {
            val fadeOutStart = totalFrames - fadeOutFrames
            if (frameIndex >= fadeOutStart) {
                val remaining = (totalFrames - 1L - frameIndex).coerceAtLeast(0L)
                val t = if (fadeOutFrames <= 1L) 0.0 else remaining.toDouble() / (fadeOutFrames - 1L).toDouble()
                gain *= 0.5 * (1.0 - cos(PI * t))
            }
        }
        return gain.toFloat().coerceIn(0f, 1f)
    }

    /**
     * Applies smooth fade-in and/or fade-out in-place to an interleaved 16-bit PCM [ShortArray].
     */
    fun applyFadeInPlace(
        samples: ShortArray,
        sampleCount: Int = samples.size,
        channelCount: Int,
        startFrameIndex: Long,
        totalFrames: Long,
        fadeInFrames: Long,
        fadeOutFrames: Long
    ) {
        if (channelCount <= 0 || sampleCount <= 0 || totalFrames <= 0L) return
        if (fadeInFrames <= 0L && fadeOutFrames <= 0L) return

        val clampedSamples = sampleCount.coerceAtMost(samples.size)
        val frameCount = clampedSamples / channelCount
        if (frameCount <= 0) return

        // Fast path: check if entire chunk is strictly inside the flat 1.0 gain plateau
        val endFrameExclusive = startFrameIndex + frameCount
        val fadeOutStart = totalFrames - fadeOutFrames
        val touchesFadeIn = fadeInFrames > 0L && startFrameIndex < fadeInFrames
        val touchesFadeOut = fadeOutFrames > 0L && endFrameExclusive > fadeOutStart
        if (!touchesFadeIn && !touchesFadeOut) return

        for (f in 0 until frameCount) {
            val globalFrame = startFrameIndex + f
            val gain = gainAtFrame(globalFrame, totalFrames, fadeInFrames, fadeOutFrames)
            if (gain >= 0.9999f) continue

            val baseIdx = f * channelCount
            for (ch in 0 until channelCount) {
                val idx = baseIdx + ch
                val scaled = (samples[idx].toFloat() * gain).roundToInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                samples[idx] = scaled.toShort()
            }
        }
    }

    /**
     * Applies smooth fade-in and/or fade-out in-place to an interleaved 16-bit signed little-endian
     * PCM byte buffer.
     */
    fun applyFadeToPcm16LeBytes(
        bytes: ByteArray,
        lengthBytes: Int = bytes.size,
        channelCount: Int,
        startFrameIndex: Long,
        totalFrames: Long,
        fadeInFrames: Long,
        fadeOutFrames: Long
    ) {
        if (channelCount <= 0 || lengthBytes < 2 || totalFrames <= 0L) return
        if (fadeInFrames <= 0L && fadeOutFrames <= 0L) return

        val bytesPerFrame = channelCount * 2
        val clampedBytes = lengthBytes.coerceAtMost(bytes.size)
        val frameCount = clampedBytes / bytesPerFrame
        if (frameCount <= 0) return

        val endFrameExclusive = startFrameIndex + frameCount
        val fadeOutStart = totalFrames - fadeOutFrames
        val touchesFadeIn = fadeInFrames > 0L && startFrameIndex < fadeInFrames
        val touchesFadeOut = fadeOutFrames > 0L && endFrameExclusive > fadeOutStart
        if (!touchesFadeIn && !touchesFadeOut) return

        for (f in 0 until frameCount) {
            val globalFrame = startFrameIndex + f
            val gain = gainAtFrame(globalFrame, totalFrames, fadeInFrames, fadeOutFrames)
            if (gain >= 0.9999f) continue

            val frameByteOffset = f * bytesPerFrame
            for (ch in 0 until channelCount) {
                val off = frameByteOffset + ch * 2
                val lo = bytes[off].toInt() and 0xFF
                val hi = bytes[off + 1].toInt() shl 8
                val sample = (hi or lo).toShort().toInt()
                val scaled = (sample.toFloat() * gain).roundToInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                bytes[off] = (scaled and 0xFF).toByte()
                bytes[off + 1] = ((scaled ushr 8) and 0xFF).toByte()
            }
        }
    }

    /**
     * Preview volume envelope helper: computes linear volume multiplier [0f, 1f] for a live
     * preview playhead position inside [range] when fade-in / fade-out toggles are enabled.
     */
    fun previewGainAtPositionMs(
        positionMs: Long,
        range: TrimRange,
        fadeInEnabled: Boolean,
        fadeOutEnabled: Boolean,
        fadeDurationMs: Long
    ): Float {
        val total = range.durationMs
        if (total <= 0L || (!fadeInEnabled && !fadeOutEnabled) || fadeDurationMs <= 0L) return 1f
        val relMs = (positionMs - range.startMs).coerceIn(0L, total)
        val maxFade = if (fadeInEnabled && fadeOutEnabled) total / 2L else total
        val fadeInMs = if (fadeInEnabled) minOf(fadeDurationMs, maxFade) else 0L
        val fadeOutMs = if (fadeOutEnabled) minOf(fadeDurationMs, maxFade) else 0L

        var gain = 1.0
        if (fadeInMs > 0L && relMs < fadeInMs) {
            val t = relMs.toDouble() / fadeInMs.toDouble()
            gain *= 0.5 * (1.0 - cos(PI * t))
        }
        if (fadeOutMs > 0L && relMs > total - fadeOutMs) {
            val rem = (total - relMs).coerceAtLeast(0L)
            val t = rem.toDouble() / fadeOutMs.toDouble()
            gain *= 0.5 * (1.0 - cos(PI * t))
        }
        return gain.toFloat().coerceIn(0.02f, 1f)
    }
}
