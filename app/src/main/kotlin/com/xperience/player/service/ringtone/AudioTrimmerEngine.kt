package com.xperience.player.service.ringtone

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.service.MediaNetworkClients
import com.xperience.player.service.YouTubeMusicService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Result returned by an optional custom stream resolver injected by the host app.
 */
sealed interface RingtoneResolvedStream {
    data class LocalUri(val uriString: String) : RingtoneResolvedStream
    data class LocalFile(val file: File) : RingtoneResolvedStream
    data class RemoteHttpUrl(
        val url: String,
        val headers: Map<String, String> = emptyMap()
    ) : RingtoneResolvedStream
}

/**
 * Clean callback interface allowing the app to inject custom stream resolution logic
 * for online/streamed tracks (e.g. YouTube Music or catalog streams).
 */
fun interface RingtoneAudioStreamResolver {
    suspend fun resolve(track: TrackItem): RingtoneResolvedStream?
}

/**
 * Production Android audio trimmer and transcoding engine for Set as Ringtone.
 *
 * Capabilities:
 * 1. Resolves BOTH local files (`content://`, SAF, `file://`, downloads) AND online/streamed
 *    tracks (YouTube Music cached audio or HTTP audio downloaded via OkHttp to app cache).
 * 2. Fast lossless container remuxing (`MediaExtractor` + `MediaMuxer`) for AAC/M4A tracks
 *    when fade-in / fade-out DSP is disabled.
 * 3. Automatic universal transcoding fallback (`MediaCodec` decode -> 16-bit PCM with sample-
 *    accurate slicing, hi-res resampling to AAC-safe rates, and smooth raised-cosine fade-in /
 *    fade-out via [PcmFadeProcessor] -> AAC-LC `MediaCodec` encode -> M4A `MediaMuxer`) for
 *    MP3, FLAC, OGG/Vorbis, WebM/Opus, WAV, or whenever fades are enabled.
 * 4. Strict `try / finally` teardown for every `MediaExtractor`, `MediaCodec`, `MediaMuxer`,
 *    and temp file to prevent native resource leaks.
 */
class AudioTrimmerEngine(
    private val context: Context,
    private val httpClient: OkHttpClient = MediaNetworkClients.sharedOkHttpClient,
    private val youTubeMusicService: YouTubeMusicService = YouTubeMusicService.getInstance()
) {
    private val appContext = context.applicationContext

    private val tempDirectory: File
        get() = File(appContext.cacheDir, TEMP_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }

    /**
     * Resolves any [TrackItem] into a local readable URI ready for waveform extraction,
     * live preview playback, and trimming.
     */
    suspend fun resolveTrackAudio(
        track: TrackItem,
        customResolver: RingtoneAudioStreamResolver? = null,
        onStatus: (String) -> Unit = {}
    ): Result<ResolvedLocalAudio> = withContext(Dispatchers.IO) {
        try {
            currentCoroutineContext().ensureActive()

            // 1. Direct local URI / existing file path on TrackItem
            val rawUrl = track.audioUrl?.trim().orEmpty()
            if (isDirectLocalSource(rawUrl)) {
                val normalizedUri = normalizeLocalUriString(rawUrl)
                val durationMs = resolveDurationMs(normalizedUri, track.durationMs)
                return@withContext Result.success(
                    ResolvedLocalAudio(
                        uriString = normalizedUri,
                        durationMs = durationMs,
                        sourceKind = ResolvedSourceKind.LOCAL_FILE,
                        isTemporaryFile = false
                    )
                )
            }

            // 2. Host-injected stream resolver (if provided)
            if (customResolver != null) {
                onStatus("Resolviendo fuente de audio…")
                val resolved = customResolver.resolve(track)
                if (resolved != null) {
                    val mapped = materializeResolvedStream(
                        stream = resolved,
                        track = track,
                        onStatus = onStatus
                    )
                    if (mapped != null) {
                        return@withContext Result.success(mapped)
                    }
                }
            }

            // 3. Direct remote HTTP(S) audio URL (non-YouTube HTML watch page)
            if (rawUrl.startsWith("http://", ignoreCase = true) ||
                rawUrl.startsWith("https://", ignoreCase = true)
            ) {
                if (!isYouTubeWatchUrl(rawUrl)) {
                    onStatus("Descargando audio remoto en caché…")
                    val downloaded = downloadRemoteAudioToCache(
                        url = rawUrl,
                        headers = emptyMap(),
                        cacheKey = "${track.id}_${rawUrl.hashCode()}"
                    )
                    if (downloaded != null && downloaded.exists() && downloaded.length() > MIN_AUDIO_BYTES) {
                        val uriStr = Uri.fromFile(downloaded).toString()
                        val durationMs = resolveDurationMs(uriStr, track.durationMs)
                        return@withContext Result.success(
                            ResolvedLocalAudio(
                                uriString = uriStr,
                                durationMs = durationMs,
                                sourceKind = ResolvedSourceKind.DOWNLOADED_TEMP,
                                isTemporaryFile = true
                            )
                        )
                    }
                }
            }

            // 4. Online YouTube Music track: reuse existing disk cache or fetch via YouTubeMusicService
            onStatus("Obteniendo audio en línea para recorte…")
            var videoId = extractYouTubeVideoId(track)
            if (videoId.isNullOrBlank()) {
                val query = listOf(track.artist, track.title)
                    .filter { it.isNotBlank() }
                    .joinToString(" - ")
                if (query.isNotBlank()) {
                    val searchResults = runCatching { youTubeMusicService.searchSongs(query) }.getOrNull()
                    videoId = searchResults?.firstOrNull()?.id?.removePrefix("ytm_")?.takeIf { it.isNotBlank() }
                }
            }

            if (!videoId.isNullOrBlank()) {
                onStatus("Descargando pista en caché local…")
                val cachedFile = youTubeMusicService.getOrDownloadAudioFile(videoId, appContext)
                if (cachedFile != null && cachedFile.exists() && cachedFile.length() > MIN_AUDIO_BYTES) {
                    val uriStr = Uri.fromFile(cachedFile).toString()
                    val durationMs = resolveDurationMs(uriStr, track.durationMs)
                    return@withContext Result.success(
                        ResolvedLocalAudio(
                            uriString = uriStr,
                            durationMs = durationMs,
                            sourceKind = ResolvedSourceKind.CACHED_ONLINE,
                            isTemporaryFile = false
                        )
                    )
                }

                // Secondary fallback: direct stream URL -> OkHttp cache file
                val streamUrl = youTubeMusicService.resolveAudioStreamUrl(videoId)
                if (!streamUrl.isNullOrBlank()) {
                    onStatus("Guardando flujo temporal para recorte…")
                    val downloaded = downloadRemoteAudioToCache(
                        url = streamUrl,
                        headers = emptyMap(),
                        cacheKey = "ytm_$videoId"
                    )
                    if (downloaded != null && downloaded.exists() && downloaded.length() > MIN_AUDIO_BYTES) {
                        val uriStr = Uri.fromFile(downloaded).toString()
                        val durationMs = resolveDurationMs(uriStr, track.durationMs)
                        return@withContext Result.success(
                            ResolvedLocalAudio(
                                uriString = uriStr,
                                durationMs = durationMs,
                                sourceKind = ResolvedSourceKind.DOWNLOADED_TEMP,
                                isTemporaryFile = true
                            )
                        )
                    }
                }
            }

            Result.failure(
                IllegalStateException(
                    "No se pudo acceder al archivo de audio de esta canción. Verifica tu conexión o descárgala primero."
                )
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to resolve audio for '${track.title}': ${t.message}", t)
            Result.failure(t)
        }
    }

    /**
     * Trims [request.sourceUri] to `[request.range.startMs, request.range.endMs]` and writes a
     * standard AAC-LC `.m4a` file into app cache. Caller should delete the file after persisting
     * to MediaStore.
     */
    suspend fun trimToM4a(
        request: RingtoneTrimRequest,
        onProgress: (RingtoneExportProgress) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        if (request.range.durationMs <= 0L) {
            return@withContext Result.failure(
                IllegalArgumentException("El rango de recorte no es válido.")
            )
        }

        val safeBase = RingtoneMetadataSanitizer.sanitizeFileNameBase(request.clipName)
        val outputFile = File(tempDirectory, "clip_${System.currentTimeMillis()}_$safeBase.m4a")
        val uri = parsePlayableUri(request.sourceUri)

        try {
            currentCoroutineContext().ensureActive()

            val probe = inspectAudioTrack(uri, request.sourceUri)
                ?: return@withContext Result.failure(
                    IllegalStateException("No se encontró ninguna pista de audio compatible en el archivo.")
                )

            val wantsFade = request.fadeInEnabled || request.fadeOutEnabled
            val canAttemptLosslessRemux = !wantsFade &&
                probe.mime.equals(MediaFormat.MIMETYPE_AUDIO_AAC, ignoreCase = true) &&
                probe.hasCodecSpecificData

            if (canAttemptLosslessRemux) {
                onProgress(
                    RingtoneExportProgress(
                        stage = RingtoneExportStage.TRIMMING,
                        fraction = 0.15f,
                        message = "Recortando audio sin pérdida…"
                    )
                )
                val remuxOk = try {
                    remuxAacToM4a(
                        uri = uri,
                        rawUriString = request.sourceUri,
                        range = request.range,
                        outputFile = outputFile,
                        onProgress = { frac ->
                            onProgress(
                                RingtoneExportProgress(
                                    stage = RingtoneExportStage.TRIMMING,
                                    fraction = (0.15f + frac * 0.70f).coerceIn(0.15f, 0.85f),
                                    message = "Recortando audio sin pérdida…"
                                )
                            )
                        }
                    )
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    Log.w(TAG, "Lossless AAC remux failed, falling back to PCM transcode: ${t.message}")
                    false
                }

                if (remuxOk && outputFile.exists() && outputFile.length() > MIN_OUTPUT_M4A_BYTES) {
                    return@withContext Result.success(outputFile)
                }
                runCatching { outputFile.delete() }
            }

            // Universal fallback (MP3, FLAC, OGG, WebM/Opus, WAV, or fade-in/fade-out enabled)
            transcodeToM4aWithFade(
                uri = uri,
                rawUriString = request.sourceUri,
                request = request,
                outputFile = outputFile,
                onProgress = onProgress
            )

            if (!outputFile.exists() || outputFile.length() <= MIN_OUTPUT_M4A_BYTES) {
                runCatching { outputFile.delete() }
                return@withContext Result.failure(
                    IllegalStateException("El archivo recortado quedó vacío. Intenta ajustar el rango.")
                )
            }

            Result.success(outputFile)
        } catch (ce: CancellationException) {
            runCatching { outputFile.delete() }
            throw ce
        } catch (t: Throwable) {
            runCatching { outputFile.delete() }
            Log.e(TAG, "Trimming failed: ${t.message}", t)
            Result.failure(t)
        }
    }

    /**
     * Deletes stale temporary files inside `cacheDir/ringtone_temp/`.
     */
    fun cleanupStaleTempFiles(maxAgeMs: Long = STALE_TEMP_MAX_AGE_MS) {
        val dir = tempDirectory
        val cutoff = System.currentTimeMillis() - maxAgeMs
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) {
                runCatching { file.delete() }
            }
        }
    }

    private suspend fun materializeResolvedStream(
        stream: RingtoneResolvedStream,
        track: TrackItem,
        onStatus: (String) -> Unit
    ): ResolvedLocalAudio? {
        return when (stream) {
            is RingtoneResolvedStream.LocalUri -> {
                val normalized = normalizeLocalUriString(stream.uriString)
                val duration = resolveDurationMs(normalized, track.durationMs)
                ResolvedLocalAudio(
                    uriString = normalized,
                    durationMs = duration,
                    sourceKind = ResolvedSourceKind.LOCAL_FILE,
                    isTemporaryFile = false
                )
            }
            is RingtoneResolvedStream.LocalFile -> {
                if (!stream.file.exists() || stream.file.length() <= 0L) return null
                val uriStr = Uri.fromFile(stream.file).toString()
                val duration = resolveDurationMs(uriStr, track.durationMs)
                ResolvedLocalAudio(
                    uriString = uriStr,
                    durationMs = duration,
                    sourceKind = ResolvedSourceKind.CACHED_ONLINE,
                    isTemporaryFile = false
                )
            }
            is RingtoneResolvedStream.RemoteHttpUrl -> {
                onStatus("Descargando flujo de audio…")
                val downloaded = downloadRemoteAudioToCache(
                    url = stream.url,
                    headers = stream.headers,
                    cacheKey = "${track.id}_${stream.url.hashCode()}"
                ) ?: return null
                val uriStr = Uri.fromFile(downloaded).toString()
                val duration = resolveDurationMs(uriStr, track.durationMs)
                ResolvedLocalAudio(
                    uriString = uriStr,
                    durationMs = duration,
                    sourceKind = ResolvedSourceKind.DOWNLOADED_TEMP,
                    isTemporaryFile = true
                )
            }
        }
    }

    private suspend fun downloadRemoteAudioToCache(
        url: String,
        headers: Map<String, String>,
        cacheKey: String
    ): File? {
        val safeKey = RingtoneMetadataSanitizer.sanitizeFileNameBase(cacheKey, fallback = "remote_audio")
        val finalFile = File(tempDirectory, "src_$safeKey.audio")
        if (finalFile.exists() && finalFile.length() > MIN_AUDIO_BYTES) {
            return finalFile
        }

        val partFile = File(tempDirectory, "src_${safeKey}_${System.currentTimeMillis()}.part")
        try {
            val requestBuilder = Request.Builder().url(url).get()
            headers.forEach { (k, v) -> requestBuilder.header(k, v) }
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Remote audio download failed with HTTP ${response.code}")
                    return null
                }
                val body = response.body ?: return null
                body.byteStream().use { input ->
                    BufferedOutputStream(FileOutputStream(partFile), IO_BUFFER_SIZE).use { out ->
                        val buf = ByteArray(IO_BUFFER_SIZE)
                        var totalRead = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buf)
                            if (read < 0) break
                            out.write(buf, 0, read)
                            totalRead += read
                            if (totalRead > MAX_REMOTE_DOWNLOAD_BYTES) {
                                throw IllegalStateException("El archivo de audio excede el tamaño máximo permitido.")
                            }
                        }
                        out.flush()
                    }
                }
            }
            if (partFile.length() <= MIN_AUDIO_BYTES) {
                runCatching { partFile.delete() }
                return null
            }
            if (finalFile.exists()) {
                runCatching { finalFile.delete() }
            }
            if (!partFile.renameTo(finalFile)) {
                partFile.copyTo(finalFile, overwrite = true)
                runCatching { partFile.delete() }
            }
            return finalFile
        } catch (ce: CancellationException) {
            runCatching { partFile.delete() }
            throw ce
        } catch (t: Throwable) {
            runCatching { partFile.delete() }
            Log.w(TAG, "Failed downloading remote audio: ${t.message}")
            return null
        }
    }

    private data class AudioTrackProbe(
        val mime: String,
        val sampleRate: Int,
        val channelCount: Int,
        val durationUs: Long,
        val hasCodecSpecificData: Boolean
    )

    private fun inspectAudioTrack(uri: Uri, rawUriString: String): AudioTrackProbe? {
        val extractor = MediaExtractor()
        return try {
            setExtractorDataSource(extractor, uri, rawUriString)
            val idx = findFirstAudioTrack(extractor)
            if (idx < 0) return null
            val fmt = extractor.getTrackFormat(idx)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: return null
            val sampleRate = if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                runCatching { fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) }.getOrDefault(44_100)
            } else {
                44_100
            }
            val channels = if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                runCatching { fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }.getOrDefault(2)
            } else {
                2
            }
            val durationUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) {
                runCatching { fmt.getLong(MediaFormat.KEY_DURATION) }.getOrDefault(0L)
            } else {
                0L
            }
            val hasCsd0 = runCatching { fmt.getByteBuffer("csd-0") != null }.getOrDefault(false)
            AudioTrackProbe(
                mime = mime,
                sampleRate = sampleRate,
                channelCount = channels,
                durationUs = durationUs,
                hasCodecSpecificData = hasCsd0
            )
        } finally {
            runCatching { extractor.release() }
        }
    }

    /**
     * Fast lossless container remuxing path for AAC/M4A files without fade DSP.
     */
    private suspend fun remuxAacToM4a(
        uri: Uri,
        rawUriString: String,
        range: TrimRange,
        outputFile: File,
        onProgress: (Float) -> Unit
    ): Boolean {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var writtenSamples = 0
        try {
            setExtractorDataSource(extractor, uri, rawUriString)
            val trackIndex = findFirstAudioTrack(extractor)
            if (trackIndex < 0) return false
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)

            val maxInputSize = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                runCatching { format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) }.getOrDefault(REMUX_BUFFER_SIZE)
            } else {
                REMUX_BUFFER_SIZE
            }.coerceAtLeast(REMUX_BUFFER_SIZE)

            val buffer = ByteBuffer.allocateDirect(maxInputSize)
            val bufferInfo = MediaCodec.BufferInfo()
            val startUs = range.startMs * 1_000L
            val endUs = range.endMs * 1_000L
            val spanUs = (endUs - startUs).coerceAtLeast(1L)

            val m = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = m
            val outTrackIndex = m.addTrack(format)
            m.start()
            muxerStarted = true

            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            var firstKeptPtsUs = -1L
            var lastWrittenPtsUs = -1L

            while (true) {
                currentCoroutineContext().ensureActive()
                buffer.clear()
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs < 0L) {
                    extractor.advance()
                    continue
                }
                if (sampleTimeUs > endUs) break

                if (sampleTimeUs >= startUs) {
                    if (firstKeptPtsUs < 0L) {
                        firstKeptPtsUs = sampleTimeUs
                    }
                    var rebasedPts = (sampleTimeUs - firstKeptPtsUs).coerceAtLeast(0L)
                    if (lastWrittenPtsUs >= 0L && rebasedPts < lastWrittenPtsUs) {
                        rebasedPts = lastWrittenPtsUs
                    }
                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.presentationTimeUs = rebasedPts
                    bufferInfo.flags = mapExtractorFlagsToCodecFlags(extractor.sampleFlags)

                    buffer.position(0)
                    buffer.limit(sampleSize)
                    m.writeSampleData(outTrackIndex, buffer, bufferInfo)
                    lastWrittenPtsUs = rebasedPts
                    writtenSamples++

                    if (writtenSamples % 16 == 0) {
                        val frac = ((sampleTimeUs - startUs).toFloat() / spanUs.toFloat()).coerceIn(0f, 1f)
                        onProgress(frac)
                    }
                }
                extractor.advance()
            }

            onProgress(1f)
            return writtenSamples > 0
        } finally {
            muxer?.let { m ->
                if (muxerStarted && writtenSamples > 0) {
                    runCatching { m.stop() }
                }
                runCatching { m.release() }
            }
            runCatching { extractor.release() }
        }
    }

    private data class DecodedPcmArtifact(
        val file: File,
        val sampleRate: Int,
        val channelCount: Int,
        val totalFrames: Long
    )

    /**
     * Universal two-stage transcode pipeline:
     * Stage 1: Decode trimmed `[startMs, endMs]` into normalized 16-bit LE stereo/mono PCM temp file
     *          (resampling hi-res >48kHz FLAC to AAC-safe 48kHz/44.1kHz on the fly).
     * Stage 2: Stream PCM through [PcmFadeProcessor] into AAC-LC [MediaCodec] encoder -> [MediaMuxer] M4A.
     */
    private suspend fun transcodeToM4aWithFade(
        uri: Uri,
        rawUriString: String,
        request: RingtoneTrimRequest,
        outputFile: File,
        onProgress: (RingtoneExportProgress) -> Unit
    ) {
        val tempPcmFile = File(tempDirectory, "pcm_${System.currentTimeMillis()}.raw")
        try {
            onProgress(
                RingtoneExportProgress(
                    stage = RingtoneExportStage.TRIMMING,
                    fraction = 0.05f,
                    message = "Decodificando y recortando audio…"
                )
            )

            val pcm = decodeTrimmedSegmentToPcm16(
                uri = uri,
                rawUriString = rawUriString,
                range = request.range,
                pcmOutputFile = tempPcmFile,
                onDecodeProgress = { frac ->
                    onProgress(
                        RingtoneExportProgress(
                            stage = RingtoneExportStage.TRIMMING,
                            fraction = (0.05f + frac * 0.45f).coerceIn(0.05f, 0.50f),
                            message = "Decodificando y recortando audio…"
                        )
                    )
                }
            )

            if (pcm.totalFrames <= 0L) {
                throw IllegalStateException("No se pudieron decodificar muestras de audio en el rango seleccionado.")
            }

            onProgress(
                RingtoneExportProgress(
                    stage = RingtoneExportStage.ENCODING,
                    fraction = 0.52f,
                    message = if (request.fadeInEnabled || request.fadeOutEnabled) {
                        "Aplicando fundido y codificando M4A…"
                    } else {
                        "Codificando tono en formato M4A…"
                    }
                )
            )

            encodePcm16ToM4a(
                pcm = pcm,
                fadeInEnabled = request.fadeInEnabled,
                fadeOutEnabled = request.fadeOutEnabled,
                fadeDurationMs = request.fadeDurationMs,
                outputFile = outputFile,
                onEncodeProgress = { frac ->
                    onProgress(
                        RingtoneExportProgress(
                            stage = RingtoneExportStage.ENCODING,
                            fraction = (0.52f + frac * 0.40f).coerceIn(0.52f, 0.92f),
                            message = "Codificando tono en formato M4A…"
                        )
                    )
                }
            )
        } finally {
            runCatching { tempPcmFile.delete() }
        }
    }

    private suspend fun decodeTrimmedSegmentToPcm16(
        uri: Uri,
        rawUriString: String,
        range: TrimRange,
        pcmOutputFile: File,
        onDecodeProgress: (Float) -> Unit
    ): DecodedPcmArtifact {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            setExtractorDataSource(extractor, uri, rawUriString)
            val trackIndex = findFirstAudioTrack(extractor)
            if (trackIndex < 0) {
                throw IllegalStateException("No se encontró pista de audio en el origen.")
            }
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalStateException("Formato de audio desconocido.")

            val codec = MediaCodec.createDecoderByType(mime)
            decoder = codec
            codec.configure(format, null, null, 0)
            codec.start()

            var srcSampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                runCatching { format.getInteger(MediaFormat.KEY_SAMPLE_RATE) }.getOrDefault(44_100)
            } else {
                44_100
            }
            var srcChannels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                runCatching { format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }.getOrDefault(2)
            } else {
                2
            }.coerceAtLeast(1)
            var srcPcmEncoding = format.safePcmEncoding()

            var dstSampleRate = chooseAacSafeSampleRate(srcSampleRate)
            var dstChannels = srcChannels.coerceIn(1, 2)

            val startUs = range.startMs * 1_000L
            val endUs = range.endMs * 1_000L
            val spanUs = (endUs - startUs).coerceAtLeast(1L)

            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var postEosEmptyPolls = 0
            var totalDstFramesWritten = 0L
            var fallbackNextPtsUs = -1L

            BufferedOutputStream(FileOutputStream(pcmOutputFile), IO_BUFFER_SIZE).use { pcmOut ->
                while (!outputDone) {
                    currentCoroutineContext().ensureActive()

                    if (!inputDone) {
                        val inIdx = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                        if (inIdx >= 0) {
                            val inBuf = codec.getInputBuffer(inIdx)
                            if (inBuf != null) {
                                inBuf.clear()
                                val sampleSize = extractor.readSampleData(inBuf, 0)
                                val sampleTimeUs = extractor.sampleTime
                                if (sampleSize < 0 || sampleTimeUs > endUs + DECODE_TAIL_PADDING_US) {
                                    codec.queueInputBuffer(
                                        inIdx,
                                        0,
                                        0,
                                        0L,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                    )
                                    inputDone = true
                                } else {
                                    codec.queueInputBuffer(
                                        inIdx,
                                        0,
                                        sampleSize,
                                        sampleTimeUs.coerceAtLeast(0L),
                                        0
                                    )
                                    extractor.advance()
                                }
                            }
                        }
                    }

                    val outIdx = codec.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
                    when {
                        outIdx >= 0 -> {
                            try {
                                val isEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                                if (bufferInfo.size > 0 && srcSampleRate > 0) {
                                    val outBuf = codec.getOutputBuffer(outIdx)
                                    if (outBuf != null) {
                                        val bytesPerSample = bytesPerSampleForEncoding(srcPcmEncoding)
                                        val bytesPerFrame = (srcChannels * bytesPerSample).coerceAtLeast(2)
                                        val totalBufFrames = bufferInfo.size / bytesPerFrame
                                        if (totalBufFrames > 0) {
                                            val bufDurationUs = (totalBufFrames.toLong() * 1_000_000L) / srcSampleRate.toLong()
                                            val rawPtsUs = bufferInfo.presentationTimeUs
                                            val bufStartUs = if (rawPtsUs >= 0L && (fallbackNextPtsUs < 0L || rawPtsUs >= fallbackNextPtsUs - 50_000L)) {
                                                rawPtsUs
                                            } else if (fallbackNextPtsUs >= 0L) {
                                                fallbackNextPtsUs
                                            } else {
                                                rawPtsUs.coerceAtLeast(0L)
                                            }
                                            val bufEndUs = bufStartUs + bufDurationUs
                                            fallbackNextPtsUs = bufEndUs

                                            if (bufEndUs > startUs && bufStartUs < endUs) {
                                                val skipUs = (startUs - bufStartUs).coerceAtLeast(0L)
                                                val firstFrame = ((skipUs * srcSampleRate) / 1_000_000L)
                                                    .toInt()
                                                    .coerceIn(0, totalBufFrames)
                                                val keepUntilUs = (endUs - bufStartUs).coerceAtMost(bufDurationUs)
                                                val endFrame = ((keepUntilUs * srcSampleRate) / 1_000_000L)
                                                    .toInt()
                                                    .coerceIn(firstFrame, totalBufFrames)

                                                val keptSrcFrames = endFrame - firstFrame
                                                if (keptSrcFrames > 0) {
                                                    val maxAllowedDstFrames =
                                                        (((range.durationMs + 250L) * dstSampleRate) / 1_000L)
                                                            .coerceAtLeast(dstSampleRate.toLong() / 10L)
                                                    val remainingBudget = (maxAllowedDstFrames - totalDstFramesWritten)
                                                        .coerceAtLeast(0L)
                                                    if (remainingBudget > 0L) {
                                                        val pcm16StereoOrMono = extractNormalizedPcm16(
                                                            buffer = outBuf,
                                                            baseOffset = bufferInfo.offset,
                                                            firstFrame = firstFrame,
                                                            frameCount = keptSrcFrames,
                                                            srcChannels = srcChannels,
                                                            dstChannels = dstChannels,
                                                            pcmEncoding = srcPcmEncoding
                                                        )
                                                        val finalSamples = if (srcSampleRate != dstSampleRate) {
                                                            resampleLinearInterleaved(
                                                                input = pcm16StereoOrMono,
                                                                channels = dstChannels,
                                                                srcRate = srcSampleRate,
                                                                dstRate = dstSampleRate
                                                            )
                                                        } else {
                                                            pcm16StereoOrMono
                                                        }
                                                        val framesInChunk = (finalSamples.size / dstChannels)
                                                            .toLong()
                                                            .coerceAtMost(remainingBudget)
                                                        if (framesInChunk > 0L) {
                                                            writeShortsLittleEndian(
                                                                out = pcmOut,
                                                                samples = finalSamples,
                                                                sampleCount = (framesInChunk * dstChannels).toInt()
                                                            )
                                                            totalDstFramesWritten += framesInChunk
                                                        }
                                                    }
                                                }
                                            }

                                            val progressFrac = ((bufEndUs - startUs).toFloat() / spanUs.toFloat())
                                                .coerceIn(0f, 1f)
                                            onDecodeProgress(progressFrac)

                                            if (bufEndUs >= endUs) {
                                                outputDone = true
                                            }
                                        }
                                    }
                                }
                                postEosEmptyPolls = 0
                                if (isEos) {
                                    outputDone = true
                                }
                            } finally {
                                codec.releaseOutputBuffer(outIdx, false)
                            }
                        }
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            postEosEmptyPolls = 0
                            val newFmt = codec.outputFormat
                            if (newFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                                val nextRate = runCatching { newFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) }
                                    .getOrDefault(srcSampleRate)
                                if (nextRate > 0) {
                                    srcSampleRate = nextRate
                                    // Lock output rate only before any PCM has been written
                                    if (totalDstFramesWritten == 0L) {
                                        dstSampleRate = chooseAacSafeSampleRate(srcSampleRate)
                                    }
                                }
                            }
                            if (newFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                val nextCh = runCatching { newFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }
                                    .getOrDefault(srcChannels)
                                if (nextCh > 0) {
                                    srcChannels = nextCh
                                    if (totalDstFramesWritten == 0L) {
                                        dstChannels = srcChannels.coerceIn(1, 2)
                                    }
                                }
                            }
                            srcPcmEncoding = newFmt.safePcmEncoding()
                        }
                        else -> {
                            if (inputDone && ++postEosEmptyPolls > MAX_POST_EOS_EMPTY_POLLS) {
                                outputDone = true
                            }
                        }
                    }
                }
                pcmOut.flush()
            }

            return DecodedPcmArtifact(
                file = pcmOutputFile,
                sampleRate = dstSampleRate,
                channelCount = dstChannels,
                totalFrames = totalDstFramesWritten
            )
        } finally {
            decoder?.let { codec ->
                runCatching { codec.stop() }
                runCatching { codec.release() }
            }
            runCatching { extractor.release() }
        }
    }

    private suspend fun encodePcm16ToM4a(
        pcm: DecodedPcmArtifact,
        fadeInEnabled: Boolean,
        fadeOutEnabled: Boolean,
        fadeDurationMs: Long,
        outputFile: File,
        onEncodeProgress: (Float) -> Unit
    ) {
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var writtenMuxedSamples = 0

        try {
            val (fadeInFrames, fadeOutFrames) = PcmFadeProcessor.computeEffectiveFadeFrames(
                totalFrames = pcm.totalFrames,
                sampleRate = pcm.sampleRate,
                fadeInEnabled = fadeInEnabled,
                fadeOutEnabled = fadeOutEnabled,
                fadeDurationMs = fadeDurationMs
            )

            val outFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                pcm.sampleRate,
                pcm.channelCount
            ).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, if (pcm.channelCount == 1) 128_000 else 192_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, ENCODER_CHUNK_BYTES * 2)
            }

            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder = codec
            codec.configure(outFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            val m = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = m
            var muxerTrackIndex = -1

            val bytesPerFrame = pcm.channelCount * 2
            val chunkSizeBytes = (ENCODER_CHUNK_BYTES / bytesPerFrame) * bytesPerFrame
            val chunkBuf = ByteArray(chunkSizeBytes)
            val bufferInfo = MediaCodec.BufferInfo()

            var inputDone = false
            var outputDone = false
            var postEosEmptyPolls = 0
            var currentFrameIndex = 0L
            var lastMuxedPtsUs = -1L

            BufferedInputStream(FileInputStream(pcm.file), IO_BUFFER_SIZE).use { pcmIn ->
                while (!outputDone) {
                    currentCoroutineContext().ensureActive()

                    if (!inputDone) {
                        val inIdx = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                        if (inIdx >= 0) {
                            val inBuf = codec.getInputBuffer(inIdx)
                            if (inBuf != null) {
                                val maxToRead = minOf(inBuf.capacity(), chunkBuf.size)
                                    .let { (it / bytesPerFrame) * bytesPerFrame }
                                    .coerceAtLeast(bytesPerFrame)
                                val bytesRead = readAlignedBytes(pcmIn, chunkBuf, maxToRead, bytesPerFrame)
                                if (bytesRead <= 0) {
                                    val eosPtsUs = (currentFrameIndex * 1_000_000L) / pcm.sampleRate.toLong()
                                    codec.queueInputBuffer(
                                        inIdx,
                                        0,
                                        0,
                                        eosPtsUs,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                    )
                                    inputDone = true
                                } else {
                                    PcmFadeProcessor.applyFadeToPcm16LeBytes(
                                        bytes = chunkBuf,
                                        lengthBytes = bytesRead,
                                        channelCount = pcm.channelCount,
                                        startFrameIndex = currentFrameIndex,
                                        totalFrames = pcm.totalFrames,
                                        fadeInFrames = fadeInFrames,
                                        fadeOutFrames = fadeOutFrames
                                    )
                                    val ptsUs = (currentFrameIndex * 1_000_000L) / pcm.sampleRate.toLong()
                                    inBuf.clear()
                                    inBuf.put(chunkBuf, 0, bytesRead)
                                    codec.queueInputBuffer(inIdx, 0, bytesRead, ptsUs, 0)
                                    currentFrameIndex += (bytesRead / bytesPerFrame).toLong()

                                    val frac = (currentFrameIndex.toFloat() / pcm.totalFrames.coerceAtLeast(1L).toFloat())
                                        .coerceIn(0f, 1f)
                                    onEncodeProgress(frac)
                                }
                            }
                        }
                    }

                    var drainMore = true
                    while (drainMore) {
                        val outIdx = codec.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
                        when {
                            outIdx >= 0 -> {
                                try {
                                    val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                                    val isEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                                    if (!isConfig && bufferInfo.size > 0 && muxerStarted && muxerTrackIndex >= 0) {
                                        val outBuf = codec.getOutputBuffer(outIdx)
                                        if (outBuf != null) {
                                            outBuf.position(bufferInfo.offset)
                                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                                            var pts = bufferInfo.presentationTimeUs.coerceAtLeast(0L)
                                            if (lastMuxedPtsUs >= 0L && pts < lastMuxedPtsUs) {
                                                pts = lastMuxedPtsUs
                                            }
                                            bufferInfo.presentationTimeUs = pts
                                            m.writeSampleData(muxerTrackIndex, outBuf, bufferInfo)
                                            lastMuxedPtsUs = pts
                                            writtenMuxedSamples++
                                        }
                                    }
                                    postEosEmptyPolls = 0
                                    if (isEos) {
                                        outputDone = true
                                        drainMore = false
                                    }
                                } finally {
                                    codec.releaseOutputBuffer(outIdx, false)
                                }
                            }
                            outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                postEosEmptyPolls = 0
                                if (!muxerStarted) {
                                    muxerTrackIndex = m.addTrack(codec.outputFormat)
                                    m.start()
                                    muxerStarted = true
                                }
                            }
                            else -> {
                                if (inputDone && ++postEosEmptyPolls > MAX_POST_EOS_EMPTY_POLLS) {
                                    outputDone = true
                                }
                                drainMore = false
                            }
                        }
                    }
                }
            }
            onEncodeProgress(1f)
        } finally {
            encoder?.let { codec ->
                runCatching { codec.stop() }
                runCatching { codec.release() }
            }
            muxer?.let { m ->
                if (muxerStarted && writtenMuxedSamples > 0) {
                    runCatching { m.stop() }
                }
                runCatching { m.release() }
            }
        }
    }

    private fun extractNormalizedPcm16(
        buffer: ByteBuffer,
        baseOffset: Int,
        firstFrame: Int,
        frameCount: Int,
        srcChannels: Int,
        dstChannels: Int,
        pcmEncoding: Int
    ): ShortArray {
        val dup = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val out = ShortArray(frameCount * dstChannels)
        val bytesPerSample = bytesPerSampleForEncoding(pcmEncoding)
        val srcBytesPerFrame = srcChannels * bytesPerSample
        val startByte = baseOffset + firstFrame * srcBytesPerFrame

        for (f in 0 until frameCount) {
            val frameOffset = startByte + f * srcBytesPerFrame
            if (dstChannels == 1) {
                var sum = 0f
                for (ch in 0 until srcChannels) {
                    sum += readSampleNormalizedFloat(dup, frameOffset + ch * bytesPerSample, pcmEncoding)
                }
                val avg = (sum / srcChannels.toFloat()).coerceIn(-1f, 1f)
                out[f] = floatToPcm16(avg)
            } else {
                // Stereo output: map 2ch directly or fold multichannel (>2ch) cleanly into L/R
                val left: Float
                val right: Float
                if (srcChannels == 1) {
                    val mono = readSampleNormalizedFloat(dup, frameOffset, pcmEncoding)
                    left = mono
                    right = mono
                } else if (srcChannels == 2) {
                    left = readSampleNormalizedFloat(dup, frameOffset, pcmEncoding)
                    right = readSampleNormalizedFloat(dup, frameOffset + bytesPerSample, pcmEncoding)
                } else {
                    var lSum = 0f
                    var rSum = 0f
                    var lCount = 0
                    var rCount = 0
                    for (ch in 0 until srcChannels) {
                        val s = readSampleNormalizedFloat(dup, frameOffset + ch * bytesPerSample, pcmEncoding)
                        if (ch % 2 == 0) {
                            lSum += s
                            lCount++
                        } else {
                            rSum += s
                            rCount++
                        }
                    }
                    left = (lSum / lCount.coerceAtLeast(1)).coerceIn(-1f, 1f)
                    right = (rSum / rCount.coerceAtLeast(1)).coerceIn(-1f, 1f)
                }
                out[f * 2] = floatToPcm16(left)
                out[f * 2 + 1] = floatToPcm16(right)
            }
        }
        return out
    }

    private fun readSampleNormalizedFloat(buf: ByteBuffer, byteOffset: Int, encoding: Int): Float {
        if (byteOffset < 0 || byteOffset >= buf.capacity()) return 0f
        return when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> {
                if (byteOffset + 4 > buf.capacity()) return 0f
                val f = buf.getFloat(byteOffset)
                if (f.isFinite()) f.coerceIn(-1f, 1f) else 0f
            }
            AudioFormat.ENCODING_PCM_8BIT -> {
                val u = buf.get(byteOffset).toInt() and 0xFF
                ((u - 128) / 128f).coerceIn(-1f, 1f)
            }
            else -> {
                if (byteOffset + 2 > buf.capacity()) return 0f
                (buf.getShort(byteOffset).toFloat() / 32768f).coerceIn(-1f, 1f)
            }
        }
    }

    private fun floatToPcm16(v: Float): Short {
        return (v.coerceIn(-1f, 1f) * 32767f).roundToInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()
    }

    private fun writeShortsLittleEndian(out: BufferedOutputStream, samples: ShortArray, sampleCount: Int) {
        val byteLen = sampleCount * 2
        val bytes = ByteArray(byteLen)
        for (i in 0 until sampleCount) {
            val s = samples[i].toInt()
            bytes[i * 2] = (s and 0xFF).toByte()
            bytes[i * 2 + 1] = ((s ushr 8) and 0xFF).toByte()
        }
        out.write(bytes)
    }

    private fun readAlignedBytes(
        input: BufferedInputStream,
        dest: ByteArray,
        maxBytes: Int,
        frameAlignBytes: Int
    ): Int {
        val target = (maxBytes / frameAlignBytes) * frameAlignBytes
        var total = 0
        while (total < target) {
            val read = input.read(dest, total, target - total)
            if (read < 0) break
            total += read
        }
        return (total / frameAlignBytes) * frameAlignBytes
    }

    private fun resolveDurationMs(uriString: String, fallbackDurationMs: Long): Long {
        val uri = parsePlayableUri(uriString)
        val retriever = MediaMetadataRetriever()
        val probed = try {
            when (uri.scheme?.lowercase()) {
                "content", "file", "android.resource" -> retriever.setDataSource(appContext, uri)
                else -> retriever.setDataSource(uriString)
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
        return if (probed > 0L) probed else fallbackDurationMs.coerceAtLeast(0L)
    }

    private fun setExtractorDataSource(extractor: MediaExtractor, uri: Uri, rawUriString: String) {
        when (uri.scheme?.lowercase()) {
            "content", "file", "android.resource" -> extractor.setDataSource(appContext, uri, null)
            else -> extractor.setDataSource(rawUriString)
        }
    }

    private fun findFirstAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
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

    private fun bytesPerSampleForEncoding(encoding: Int): Int = when (encoding) {
        AudioFormat.ENCODING_PCM_FLOAT -> 4
        AudioFormat.ENCODING_PCM_8BIT -> 1
        else -> 2
    }

    private fun mapExtractorFlagsToCodecFlags(sampleFlags: Int): Int {
        var flags = 0
        if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
            flags = flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
        }
        return flags
    }

    private fun parsePlayableUri(uriString: String): Uri {
        val trimmed = uriString.trim()
        return if (trimmed.startsWith("/")) Uri.fromFile(File(trimmed)) else Uri.parse(trimmed)
    }

    private fun normalizeLocalUriString(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("/")) Uri.fromFile(File(trimmed)).toString() else trimmed
    }

    private fun isDirectLocalSource(rawUrl: String): Boolean {
        if (rawUrl.isBlank()) return false
        if (rawUrl.startsWith("content://", ignoreCase = true)) return true
        if (rawUrl.startsWith("file://", ignoreCase = true)) {
            val path = runCatching { Uri.parse(rawUrl).path }.getOrNull()
            return !path.isNullOrBlank() && File(path).exists()
        }
        if (rawUrl.startsWith("/") && File(rawUrl).exists()) return true
        return false
    }

    private fun isYouTubeWatchUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("youtube.com/watch") ||
            lower.contains("music.youtube.com/") ||
            lower.contains("youtu.be/")
    }

    private fun extractYouTubeVideoId(track: TrackItem): String? {
        if (track.id.startsWith("ytm_")) {
            val candidate = track.id.removePrefix("ytm_").removePrefix("video_").trim()
            if (candidate.isNotBlank()) return candidate
        }
        val url = track.audioUrl.orEmpty()
        if (url.contains("v=")) {
            val candidate = url.substringAfter("v=").substringBefore("&").trim()
            if (candidate.isNotBlank()) return candidate
        }
        if (url.contains("youtu.be/")) {
            val candidate = url.substringAfter("youtu.be/").substringBefore("?").trim()
            if (candidate.isNotBlank()) return candidate
        }
        return null
    }

    companion object {
        private const val TAG = "AudioTrimmerEngine"
        private const val TEMP_DIR_NAME = "ringtone_temp"
        private const val IO_BUFFER_SIZE = 32 * 1024
        private const val REMUX_BUFFER_SIZE = 256 * 1024
        private const val ENCODER_CHUNK_BYTES = 8 * 1024
        private const val CODEC_TIMEOUT_US = 15_000L
        private const val MAX_POST_EOS_EMPTY_POLLS = 200
        private const val DECODE_TAIL_PADDING_US = 300_000L
        private const val MIN_AUDIO_BYTES = 8_192L
        private const val MIN_OUTPUT_M4A_BYTES = 1_024L
        private const val MAX_REMOTE_DOWNLOAD_BYTES = 120L * 1024L * 1024L // 120 MB safety guard
        private const val STALE_TEMP_MAX_AGE_MS = 6L * 60L * 60L * 1000L // 6 hours

        private val AAC_SUPPORTED_SAMPLE_RATES = intArrayOf(
            8_000, 11_025, 12_000, 16_000, 22_050, 24_000, 32_000, 44_100, 48_000
        )

        /**
         * Maps any source sample rate (including 88.2kHz / 96kHz / 192kHz Hi-Res FLAC)
         * to an AAC-LC compliant sample rate cleanly supported by Android MediaCodec encoders.
         */
        internal fun chooseAacSafeSampleRate(srcRate: Int): Int {
            if (srcRate in AAC_SUPPORTED_SAMPLE_RATES) return srcRate
            return when {
                srcRate <= 0 -> 44_100
                srcRate > 48_000 && srcRate % 44_100 == 0 -> 44_100
                srcRate > 48_000 -> 48_000
                srcRate < 8_000 -> 16_000
                else -> 44_100
            }
        }

        /**
         * Pure-Kotlin linear interpolation resampler for interleaved 16-bit PCM.
         */
        internal fun resampleLinearInterleaved(
            input: ShortArray,
            channels: Int,
            srcRate: Int,
            dstRate: Int
        ): ShortArray {
            if (input.isEmpty() || channels <= 0 || srcRate <= 0 || dstRate <= 0 || srcRate == dstRate) {
                return input
            }
            val srcFrames = input.size / channels
            if (srcFrames <= 1) return input

            val dstFrames = ((srcFrames.toLong() * dstRate.toLong()) / srcRate.toLong())
                .toInt()
                .coerceAtLeast(1)
            val output = ShortArray(dstFrames * channels)
            val ratio = srcRate.toDouble() / dstRate.toDouble()

            for (df in 0 until dstFrames) {
                val srcPos = df * ratio
                val idx0 = srcPos.toInt().coerceIn(0, srcFrames - 1)
                val idx1 = (idx0 + 1).coerceAtMost(srcFrames - 1)
                val frac = (srcPos - idx0).toFloat()

                for (ch in 0 until channels) {
                    val s0 = input[idx0 * channels + ch].toFloat()
                    val s1 = input[idx1 * channels + ch].toFloat()
                    val interpolated = s0 + (s1 - s0) * frac
                    output[df * channels + ch] = interpolated.roundToInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        .toShort()
                }
            }
            return output
        }
    }
}
