package com.xperience.player.service.ringtone

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Asynchronously extracts normalized amplitude peaks from local audio files/URIs using
 * Android [MediaExtractor] + [MediaCodec] with fast frame-skipping sampling and
 * deterministic graceful fallback if an unsupported/corrupt codec is encountered.
 */
class AudioWaveformExtractor(
    private val context: Context
) {
    private val appContext = context.applicationContext

    suspend fun extractWaveform(
        uriString: String,
        barCount: Int = WaveformData.DEFAULT_BAR_COUNT,
        fallbackSeed: Int = uriString.hashCode(),
        knownDurationMs: Long = 0L
    ): WaveformData = withContext(Dispatchers.IO) {
        val safeBars = barCount.coerceIn(MIN_BAR_COUNT, MAX_BAR_COUNT)
        val uri = parsePlayableUri(uriString)

        val extracted = try {
            extractRealPeaks(uri, uriString, safeBars, knownDurationMs)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.w(TAG, "Real waveform extraction failed, using deterministic fallback: ${t.message}")
            null
        }

        if (extracted != null && extracted.validBuckets >= (safeBars / 4).coerceAtLeast(4)) {
            fillMissingBuckets(extracted.rawPeaks, extracted.bucketPopulated)
            val normalized = WaveformNormalizer.normalizePeaks(extracted.rawPeaks)
            WaveformData(
                peaks = normalized,
                durationMs = extracted.durationMs.coerceAtLeast(knownDurationMs),
                isRealWaveform = true
            )
        } else {
            val probedDuration = extracted?.durationMs?.takeIf { it > 0L }
                ?: knownDurationMs.takeIf { it > 0L }
                ?: probeDurationMs(uri, uriString)
            WaveformData(
                peaks = WaveformNormalizer.generateFallbackWaveform(fallbackSeed, safeBars),
                durationMs = probedDuration,
                isRealWaveform = false
            )
        }
    }

    private data class RawExtractionResult(
        val rawPeaks: FloatArray,
        val bucketPopulated: BooleanArray,
        val validBuckets: Int,
        val durationMs: Long
    )

    private suspend fun extractRealPeaks(
        uri: Uri,
        rawUriString: String,
        barCount: Int,
        knownDurationMs: Long
    ): RawExtractionResult? {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            setExtractorDataSource(extractor, uri, rawUriString)
            val trackIndex = findFirstAudioTrack(extractor)
            if (trackIndex < 0) return null

            extractor.selectTrack(trackIndex)
            val trackFormat = extractor.getTrackFormat(trackIndex)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: return null

            val formatDurationUs = if (trackFormat.containsKey(MediaFormat.KEY_DURATION)) {
                runCatching { trackFormat.getLong(MediaFormat.KEY_DURATION) }.getOrDefault(0L)
            } else {
                0L
            }
            val durationUs = when {
                formatDurationUs > 0L -> formatDurationUs
                knownDurationMs > 0L -> knownDurationMs * 1_000L
                else -> probeDurationMs(uri, rawUriString) * 1_000L
            }
            if (durationUs <= 0L) return null

            val codec = MediaCodec.createDecoderByType(mime)
            decoder = codec
            codec.configure(trackFormat, null, null, 0)
            codec.start()

            val peaks = FloatArray(barCount)
            val populated = BooleanArray(barCount)

            val validBuckets = if (durationUs <= SEQUENTIAL_DECODE_THRESHOLD_US) {
                extractSequential(
                    extractor = extractor,
                    codec = codec,
                    initialFormat = trackFormat,
                    durationUs = durationUs,
                    peaks = peaks,
                    populated = populated
                )
            } else {
                extractByWindowSeeking(
                    extractor = extractor,
                    codec = codec,
                    initialFormat = trackFormat,
                    durationUs = durationUs,
                    peaks = peaks,
                    populated = populated
                )
            }

            return RawExtractionResult(
                rawPeaks = peaks,
                bucketPopulated = populated,
                validBuckets = validBuckets,
                durationMs = (durationUs / 1_000L).coerceAtLeast(1L)
            )
        } finally {
            decoder?.let { codec ->
                runCatching { codec.stop() }
                runCatching { codec.release() }
            }
            runCatching { extractor.release() }
        }
    }

    private suspend fun extractByWindowSeeking(
        extractor: MediaExtractor,
        codec: MediaCodec,
        initialFormat: MediaFormat,
        durationUs: Long,
        peaks: FloatArray,
        populated: BooleanArray
    ): Int {
        val barCount = peaks.size
        val bufferInfo = MediaCodec.BufferInfo()
        var pcmEncoding = initialFormat.safePcmEncoding()
        var validBuckets = 0
        val deadlineMs = System.currentTimeMillis() + MAX_EXTRACTION_WALL_MS

        for (bucket in 0 until barCount) {
            currentCoroutineContext().ensureActive()
            if (System.currentTimeMillis() > deadlineMs) break

            val targetUs = ((bucket.toDouble() + 0.5) / barCount.toDouble() * durationUs.toDouble())
                .toLong()
                .coerceIn(0L, (durationUs - 1L).coerceAtLeast(0L))

            extractor.seekTo(targetUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            runCatching { codec.flush() }

            var bucketEnergy = 0f
            var gotSamples = false
            var fedPackets = 0
            var outputAttempts = 0

            while (outputAttempts < MAX_OUTPUT_POLLS_PER_BUCKET && !gotSamples) {
                currentCoroutineContext().ensureActive()

                if (fedPackets < MAX_INPUT_PACKETS_PER_BUCKET) {
                    val inIdx = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            inBuf.clear()
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            if (sampleSize > 0) {
                                val pts = extractor.sampleTime.coerceAtLeast(0L)
                                codec.queueInputBuffer(inIdx, 0, sampleSize, pts, 0)
                                extractor.advance()
                                fedPackets++
                            } else {
                                codec.queueInputBuffer(
                                    inIdx,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                fedPackets = MAX_INPUT_PACKETS_PER_BUCKET
                            }
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
                outputAttempts++
                when {
                    outIdx >= 0 -> {
                        try {
                            if (bufferInfo.size > 0) {
                                val outBuf = codec.getOutputBuffer(outIdx)
                                if (outBuf != null) {
                                    val level = measureBufferAmplitude(
                                        buffer = outBuf,
                                        offset = bufferInfo.offset,
                                        size = bufferInfo.size,
                                        pcmEncoding = pcmEncoding
                                    )
                                    if (level > bucketEnergy) bucketEnergy = level
                                    gotSamples = true
                                }
                            }
                        } finally {
                            codec.releaseOutputBuffer(outIdx, false)
                        }
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            break
                        }
                    }
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        pcmEncoding = codec.outputFormat.safePcmEncoding()
                    }
                }
            }

            if (gotSamples) {
                peaks[bucket] = bucketEnergy
                populated[bucket] = true
                validBuckets++
            }
        }
        return validBuckets
    }

    private suspend fun extractSequential(
        extractor: MediaExtractor,
        codec: MediaCodec,
        initialFormat: MediaFormat,
        durationUs: Long,
        peaks: FloatArray,
        populated: BooleanArray
    ): Int {
        val barCount = peaks.size
        val bufferInfo = MediaCodec.BufferInfo()
        var pcmEncoding = initialFormat.safePcmEncoding()
        var inputDone = false
        var outputDone = false
        val deadlineMs = System.currentTimeMillis() + MAX_EXTRACTION_WALL_MS

        extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

        while (!outputDone) {
            currentCoroutineContext().ensureActive()
            if (System.currentTimeMillis() > deadlineMs) break

            if (!inputDone) {
                val inIdx = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                if (inIdx >= 0) {
                    val inBuf = codec.getInputBuffer(inIdx)
                    if (inBuf != null) {
                        inBuf.clear()
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inIdx,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            val pts = extractor.sampleTime.coerceAtLeast(0L)
                            codec.queueInputBuffer(inIdx, 0, sampleSize, pts, 0)
                            extractor.advance()
                        }
                    }
                }
            }

            val outIdx = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
            when {
                outIdx >= 0 -> {
                    try {
                        if (bufferInfo.size > 0) {
                            val outBuf = codec.getOutputBuffer(outIdx)
                            if (outBuf != null) {
                                val bucket = ((bufferInfo.presentationTimeUs.toDouble() / durationUs.toDouble()) * barCount)
                                    .toInt()
                                    .coerceIn(0, barCount - 1)
                                val level = measureBufferAmplitude(
                                    buffer = outBuf,
                                    offset = bufferInfo.offset,
                                    size = bufferInfo.size,
                                    pcmEncoding = pcmEncoding
                                )
                                if (level > peaks[bucket]) {
                                    peaks[bucket] = level
                                }
                                populated[bucket] = true
                            }
                        }
                    } finally {
                        codec.releaseOutputBuffer(outIdx, false)
                    }
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true
                    }
                }
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    pcmEncoding = codec.outputFormat.safePcmEncoding()
                }
            }
        }

        return populated.count { it }
    }

    private fun measureBufferAmplitude(
        buffer: ByteBuffer,
        offset: Int,
        size: Int,
        pcmEncoding: Int
    ): Float {
        if (size <= 0) return 0f
        val dup = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val safeEnd = (offset + size).coerceAtMost(dup.capacity())
        if (offset < 0 || offset >= safeEnd) return 0f
        dup.position(offset)
        dup.limit(safeEnd)

        return when (pcmEncoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> {
                val floatBuf = dup.asFloatBuffer()
                val total = floatBuf.remaining()
                if (total <= 0) return 0f
                val step = (total / MAX_SAMPLES_PER_BUFFER).coerceAtLeast(1)
                var sumSq = 0.0
                var maxAbs = 0f
                var count = 0
                var i = 0
                while (i < total) {
                    val v = floatBuf.get(i).let { if (it.isFinite()) abs(it).coerceAtMost(1f) else 0f }
                    sumSq += v * v
                    if (v > maxAbs) maxAbs = v
                    count++
                    i += step
                }
                val rms = if (count > 0) sqrt(sumSq / count).toFloat() else 0f
                0.65f * rms + 0.35f * maxAbs
            }
            AudioFormat.ENCODING_PCM_8BIT -> {
                val total = dup.remaining()
                if (total <= 0) return 0f
                val step = (total / MAX_SAMPLES_PER_BUFFER).coerceAtLeast(1)
                var sumSq = 0.0
                var maxAbs = 0f
                var count = 0
                var i = 0
                while (i < total) {
                    val unsigned = dup.get(offset + i).toInt() and 0xFF
                    val v = abs(unsigned - 128) / 128f
                    sumSq += v * v
                    if (v > maxAbs) maxAbs = v
                    count++
                    i += step
                }
                val rms = if (count > 0) sqrt(sumSq / count).toFloat() else 0f
                0.65f * rms + 0.35f * maxAbs
            }
            else -> {
                // Default PCM 16-bit signed little-endian
                val shortBuf = dup.asShortBuffer()
                val total = shortBuf.remaining()
                if (total <= 0) return 0f
                val step = (total / MAX_SAMPLES_PER_BUFFER).coerceAtLeast(1)
                var sumSq = 0.0
                var maxAbs = 0f
                var count = 0
                var i = 0
                while (i < total) {
                    val sample = shortBuf.get(i).toInt()
                    val v = abs(sample) / 32768f
                    sumSq += v * v
                    if (v > maxAbs) maxAbs = v
                    count++
                    i += step
                }
                val rms = if (count > 0) sqrt(sumSq / count).toFloat() else 0f
                0.65f * rms + 0.35f * maxAbs
            }
        }
    }

    private fun fillMissingBuckets(peaks: FloatArray, populated: BooleanArray) {
        for (i in peaks.indices) {
            if (populated[i]) continue
            var left = i - 1
            while (left >= 0 && !populated[left]) left--
            var right = i + 1
            while (right < peaks.size && !populated[right]) right++
            peaks[i] = when {
                left >= 0 && right < peaks.size -> (peaks[left] + peaks[right]) * 0.5f
                left >= 0 -> peaks[left]
                right < peaks.size -> peaks[right]
                else -> 0f
            }
        }
    }

    private fun setExtractorDataSource(extractor: MediaExtractor, uri: Uri, rawUriString: String) {
        when (uri.scheme?.lowercase()) {
            "content", "file", "android.resource" -> extractor.setDataSource(appContext, uri, null)
            else -> extractor.setDataSource(rawUriString)
        }
    }

    private fun probeDurationMs(uri: Uri, rawUriString: String): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            when (uri.scheme?.lowercase()) {
                "content", "file", "android.resource" -> retriever.setDataSource(appContext, uri)
                else -> retriever.setDataSource(rawUriString)
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(0L)
                ?: 0L
        } catch (_: Throwable) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun findFirstAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return i
        }
        return -1
    }

    private fun MediaFormat.safePcmEncoding(): Int {
        return if (containsKey(MediaFormat.KEY_PCM_ENCODING)) {
            runCatching { getInteger(MediaFormat.KEY_PCM_ENCODING) }
                .getOrDefault(AudioFormat.ENCODING_PCM_16BIT)
        } else {
            AudioFormat.ENCODING_PCM_16BIT
        }
    }

    private fun parsePlayableUri(uriString: String): Uri {
        val trimmed = uriString.trim()
        return if (trimmed.startsWith("/")) {
            Uri.fromFile(File(trimmed))
        } else {
            Uri.parse(trimmed)
        }
    }

    companion object {
        private const val TAG = "AudioWaveformExtractor"
        private const val MIN_BAR_COUNT = 24
        private const val MAX_BAR_COUNT = 160
        private const val SEQUENTIAL_DECODE_THRESHOLD_US = 12_000_000L // 12 seconds
        private const val DEQUEUE_TIMEOUT_US = 12_000L
        private const val MAX_INPUT_PACKETS_PER_BUCKET = 4
        private const val MAX_OUTPUT_POLLS_PER_BUCKET = 8
        private const val MAX_SAMPLES_PER_BUFFER = 512
        private const val MAX_EXTRACTION_WALL_MS = 6_000L
    }
}
