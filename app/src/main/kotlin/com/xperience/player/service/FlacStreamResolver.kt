package com.xperience.player.service

import android.content.Context
import android.util.Log
import com.xperience.player.domain.model.PlaybackSourceType
import com.xperience.player.domain.model.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.Base64
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.math.abs

/**
 * Direct online FLAC lossless stream resolver for streaming playback.
 *
 * Mirrors the verified Mornye provider chain (GDStudio NetEase 999/740 -> Tidal Hi-Fi LOSSLESS),
 * certifies every stream with `'fLaC'` magic bytes (`0x66 0x4C 0x61 0x43`), parses the FLAC
 * `STREAMINFO` block for exact sample rate, bit depth, channels, and bitrate, and caches to
 * `cacheDir/audio_cache/<key>_flac.flac`.
 */
class FlacStreamResolver(
    private val appContext: Context
) {
    data class FlacStreamInfo(
        val sampleRateHz: Int,
        val channelCount: Int,
        val bitDepth: Int,
        val totalSamples: Long,
        val durationMs: Long
    )

    data class ResolvedFlacStream(
        val file: File,
        val sourceType: PlaybackSourceType,
        val providerLabel: String,
        val sampleRateHz: Int,
        val channelCount: Int,
        val bitDepth: Int,
        val bitrateKbps: Int,
        val fileSizeBytes: Long,
        val downloadedBytes: Long,
        val servedFromCache: Boolean,
        val isrc: String = ""
    )

    private val httpClient = MediaNetworkClients.sharedOkHttpClient.newBuilder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val cacheDir: File by lazy {
        File(appContext.cacheDir, "audio_cache").apply { mkdirs() }
    }

    suspend fun resolveAndCacheFlacStream(
        track: TrackItem,
        cacheKey: String
    ): ResolvedFlacStream? = withContext(Dispatchers.IO) {
        val safeKey = cacheKey.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(64)
        val cachedFile = File(cacheDir, "${safeKey}_flac.flac")

        // 1. Check disk cache first
        if (cachedFile.exists() && cachedFile.length() > MIN_FLAC_BYTES && isValidFlacFile(cachedFile)) {
            runCatching { cachedFile.setLastModified(System.currentTimeMillis()) }
            val info = parseFlacStreamInfo(cachedFile)
            val bitrateKbps = computeBitrateKbps(cachedFile.length(), info?.durationMs ?: track.durationMs)
            val meta = readFlacSidecarMeta(cachedFile)
            return@withContext ResolvedFlacStream(
                file = cachedFile,
                sourceType = meta?.sourceType ?: PlaybackSourceType.FLAC_GDSTUDIO,
                providerLabel = meta?.providerLabel ?: "FLAC Lossless (Cached)",
                sampleRateHz = info?.sampleRateHz ?: 44100,
                channelCount = info?.channelCount ?: 2,
                bitDepth = info?.bitDepth ?: 16,
                bitrateKbps = bitrateKbps,
                fileSizeBytes = cachedFile.length(),
                downloadedBytes = 0L,
                servedFromCache = true,
                isrc = meta?.isrc.orEmpty()
            )
        }

        val query = cleanSearchQuery(track.title, track.artist)
        if (query.isBlank()) return@withContext null

        // 2. Try GDStudio NetEase Lossless (br=999, then br=740)
        val gdCandidate = searchBestGdStudio(query, track)
        if (gdCandidate != null) {
            for (br in listOf(999, 740)) {
                val streamUrl = fetchGdStudioUrl(gdCandidate.urlId, br) ?: continue
                val downloaded = downloadVerifiedFlac(streamUrl, cachedFile)
                if (downloaded > MIN_FLAC_BYTES) {
                    val info = parseFlacStreamInfo(cachedFile)
                    val bitrateKbps = computeBitrateKbps(cachedFile.length(), info?.durationMs ?: track.durationMs)
                    writeFlacSidecarMeta(
                        cachedFile,
                        FlacSidecarMeta(PlaybackSourceType.FLAC_GDSTUDIO, "GDStudio FLAC", "")
                    )
                    return@withContext ResolvedFlacStream(
                        file = cachedFile,
                        sourceType = PlaybackSourceType.FLAC_GDSTUDIO,
                        providerLabel = "GDStudio FLAC",
                        sampleRateHz = info?.sampleRateHz ?: 44100,
                        channelCount = info?.channelCount ?: 2,
                        bitDepth = info?.bitDepth ?: 16,
                        bitrateKbps = bitrateKbps,
                        fileSizeBytes = cachedFile.length(),
                        downloadedBytes = downloaded,
                        servedFromCache = false
                    )
                }
            }
        }

        // 3. Try Tidal Hi-Fi LOSSLESS
        val tidalCandidate = searchBestTidal(query, track)
        if (tidalCandidate != null) {
            val streamUrl = fetchTidalLosslessUrl(tidalCandidate.trackId)
            if (!streamUrl.isNullOrBlank()) {
                val downloaded = downloadVerifiedFlac(streamUrl, cachedFile)
                if (downloaded > MIN_FLAC_BYTES) {
                    val info = parseFlacStreamInfo(cachedFile)
                    val bitrateKbps = computeBitrateKbps(cachedFile.length(), info?.durationMs ?: track.durationMs)
                    writeFlacSidecarMeta(
                        cachedFile,
                        FlacSidecarMeta(PlaybackSourceType.FLAC_TIDAL, "Tidal Hi-Fi FLAC", tidalCandidate.isrc)
                    )
                    return@withContext ResolvedFlacStream(
                        file = cachedFile,
                        sourceType = PlaybackSourceType.FLAC_TIDAL,
                        providerLabel = "Tidal Hi-Fi FLAC",
                        sampleRateHz = info?.sampleRateHz ?: 44100,
                        channelCount = info?.channelCount ?: 2,
                        bitDepth = info?.bitDepth ?: 16,
                        bitrateKbps = bitrateKbps,
                        fileSizeBytes = cachedFile.length(),
                        downloadedBytes = downloaded,
                        servedFromCache = false,
                        isrc = tidalCandidate.isrc
                    )
                }
            }
        }

        null
    }

    fun peekCachedFlacStream(track: TrackItem, cacheKey: String): ResolvedFlacStream? {
        val safeKey = cacheKey.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(64)
        val cachedFile = File(cacheDir, "${safeKey}_flac.flac")
        if (!cachedFile.exists() || cachedFile.length() <= MIN_FLAC_BYTES || !isValidFlacFile(cachedFile)) {
            return null
        }
        val info = parseFlacStreamInfo(cachedFile)
        val bitrateKbps = computeBitrateKbps(cachedFile.length(), info?.durationMs ?: track.durationMs)
        val meta = readFlacSidecarMeta(cachedFile)
        return ResolvedFlacStream(
            file = cachedFile,
            sourceType = meta?.sourceType ?: PlaybackSourceType.FLAC_GDSTUDIO,
            providerLabel = meta?.providerLabel ?: "FLAC Lossless (Cached)",
            sampleRateHz = info?.sampleRateHz ?: 44100,
            channelCount = info?.channelCount ?: 2,
            bitDepth = info?.bitDepth ?: 16,
            bitrateKbps = bitrateKbps,
            fileSizeBytes = cachedFile.length(),
            downloadedBytes = 0L,
            servedFromCache = true,
            isrc = meta?.isrc.orEmpty()
        )
    }

    private data class FlacSidecarMeta(
        val sourceType: PlaybackSourceType,
        val providerLabel: String,
        val isrc: String
    )

    private fun writeFlacSidecarMeta(flacFile: File, meta: FlacSidecarMeta) {
        runCatching {
            val sidecar = File(flacFile.parentFile, "${flacFile.name}.meta.json")
            val json = JSONObject().apply {
                put("sourceType", meta.sourceType.name)
                put("providerLabel", meta.providerLabel)
                if (meta.isrc.isNotBlank()) put("isrc", meta.isrc)
            }
            sidecar.writeText(json.toString(), Charsets.UTF_8)
        }
    }

    private fun readFlacSidecarMeta(flacFile: File): FlacSidecarMeta? {
        return runCatching {
            val sidecar = File(flacFile.parentFile, "${flacFile.name}.meta.json")
            if (!sidecar.exists()) return@runCatching null
            val obj = JSONObject(sidecar.readText(Charsets.UTF_8))
            val srcName = obj.optString("sourceType", PlaybackSourceType.FLAC_GDSTUDIO.name)
            val src = PlaybackSourceType.entries.firstOrNull { it.name == srcName }
                ?: PlaybackSourceType.FLAC_GDSTUDIO
            FlacSidecarMeta(
                sourceType = src,
                providerLabel = obj.optString("providerLabel", src.displayName),
                isrc = obj.optString("isrc", "")
            )
        }.getOrNull()
    }

    private data class GdMatch(val urlId: String, val score: Int)
    private data class TidalMatch(val trackId: String, val isrc: String, val score: Int)

    private fun searchBestGdStudio(query: String, track: TrackItem): GdMatch? {
        return runCatching {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val req = Request.Builder()
                .url("$GDSTUDIO_API_URL?types=search&source=netease&name=$encoded&count=6")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val arr = JSONArray(body)
                var best: GdMatch? = null
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val urlId = item.optString("url_id", item.optString("id", ""))
                    if (urlId.isBlank()) continue
                    val name = item.optString("name", "")
                    val artist = when (val a = item.opt("artist")) {
                        is JSONArray -> (0 until a.length()).joinToString(", ") { a.optString(it) }
                        is String -> a
                        else -> ""
                    }
                    val score = calculateMatchScore(track.title, track.artist, name, artist, track.durationMs, 0L)
                    if (score >= MIN_MATCH_SCORE && (best == null || score > best.score)) {
                        best = GdMatch(urlId, score)
                    }
                }
                best
            }
        }.onFailure {
            Log.d(TAG, "GDStudio FLAC search skipped: ${it.message}")
        }.getOrNull()
    }

    private fun fetchGdStudioUrl(urlId: String, bitrate: Int): String? {
        return runCatching {
            val req = Request.Builder()
                .url("$GDSTUDIO_API_URL?types=url&source=netease&id=${URLEncoder.encode(urlId, "UTF-8")}&br=$bitrate")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                JSONObject(body).optString("url", "").takeIf { it.startsWith("http") }
            }
        }.getOrNull()
    }

    private fun searchBestTidal(query: String, track: TrackItem): TidalMatch? {
        return runCatching {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val req = Request.Builder()
                .url("$TIDAL_API_BASE_URL/search/tracks?query=$encoded&limit=6&countryCode=US")
                .header("x-tidal-token", TIDAL_PUBLIC_TOKEN)
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val items = JSONObject(body).optJSONArray("items") ?: return null
                var best: TidalMatch? = null
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val tId = item.optString("id", "")
                    if (tId.isBlank()) continue
                    val tTitle = item.optString("title", "")
                    val tDurMs = (item.optDouble("duration", 0.0) * 1000.0).toLong()
                    val tIsrc = item.optString("isrc", "")
                    val artistsArr = item.optJSONArray("artists")
                    val tArtist = if (artistsArr != null && artistsArr.length() > 0) {
                        (0 until artistsArr.length()).joinToString(", ") {
                            artistsArr.optJSONObject(it)?.optString("name", "") ?: ""
                        }
                    } else {
                        item.optJSONObject("artist")?.optString("name", "") ?: ""
                    }
                    val score = calculateMatchScore(track.title, track.artist, tTitle, tArtist, track.durationMs, tDurMs)
                    if (score >= MIN_MATCH_SCORE && (best == null || score > best.score)) {
                        best = TidalMatch(tId, tIsrc, score)
                    }
                }
                best
            }
        }.onFailure {
            Log.d(TAG, "Tidal FLAC search skipped: ${it.message}")
        }.getOrNull()
    }

    private fun fetchTidalLosslessUrl(tidalTrackId: String): String? {
        return runCatching {
            val req = Request.Builder()
                .url("$TIDAL_API_BASE_URL/tracks/$tidalTrackId/playbackinfopostpaywall?audioquality=LOSSLESS&playbackmode=STREAM&assetpresentation=FULL&countryCode=US")
                .header("x-tidal-token", TIDAL_PUBLIC_TOKEN)
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val manifestStr = JSONObject(body).optString("manifest", "")
                if (manifestStr.isBlank()) return null
                val decoded = String(Base64.getDecoder().decode(manifestStr), Charsets.UTF_8)
                val urls = JSONObject(decoded).optJSONArray("urls") ?: return null
                if (urls.length() > 0) urls.optString(0).takeIf { it.startsWith("http") } else null
            }
        }.getOrNull()
    }

    private fun downloadVerifiedFlac(streamUrl: String, targetFile: File): Long {
        val temp = File(targetFile.parentFile, "${targetFile.name}.${System.currentTimeMillis()}.tmp")
        try {
            val req = Request.Builder().url(streamUrl).build()
            var totalRead = 0L
            httpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return -1L
                val body = response.body ?: return -1L
                val input = body.byteStream()
                val header = ByteArray(4)
                val headerRead = input.read(header)
                if (headerRead != 4 || !hasFlacMagicHeader(header)) {
                    return -1L
                }
                FileOutputStream(temp).use { fos ->
                    fos.write(header, 0, 4)
                    totalRead = 4L
                    val buf = ByteArray(16_384)
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        fos.write(buf, 0, n)
                        totalRead += n
                    }
                }
            }
            if (totalRead > MIN_FLAC_BYTES) {
                if (targetFile.exists()) targetFile.delete()
                if (temp.renameTo(targetFile)) {
                    return totalRead
                }
            }
            temp.delete()
            return -1L
        } catch (e: Exception) {
            temp.delete()
            Log.d(TAG, "FLAC stream download error: ${e.message}")
            return -1L
        }
    }

    private fun cleanSearchQuery(title: String, artist: String): String {
        val cleanTitle = TITLE_CLEAN_REGEX.matcher(title).replaceAll("").trim()
        val cleanArtist = artist.replace(Regex("\\s*-\\s*Topic$", RegexOption.IGNORE_CASE), "").trim()
        return if (cleanArtist.isNotBlank() && !cleanTitle.contains(cleanArtist, ignoreCase = true)) {
            "$cleanArtist $cleanTitle"
        } else {
            cleanTitle
        }
    }

    private fun calculateMatchScore(
        reqTitle: String,
        reqArtist: String,
        candTitle: String,
        candArtist: String,
        reqDurMs: Long,
        candDurMs: Long
    ): Int {
        var score = 50
        val nReqT = normalize(reqTitle)
        val nCandT = normalize(candTitle)
        if (nReqT == nCandT) score += 30
        else if (nCandT.contains(nReqT) || nReqT.contains(nCandT)) score += 18

        val nReqA = normalize(reqArtist)
        val nCandA = normalize(candArtist)
        if (nReqA.isNotBlank()) {
            if (nReqA == nCandA) score += 20
            else if (nCandA.contains(nReqA) || nReqA.contains(nCandA)) score += 12
        }

        if (reqDurMs > 0L && candDurMs > 0L) {
            val diffSec = abs(reqDurMs - candDurMs) / 1000L
            when {
                diffSec <= 3L -> score += 10
                diffSec <= 10L -> score += 5
                diffSec > 35L -> score -= 25
            }
        }
        return score.coerceIn(0, 100)
    }

    private fun normalize(str: String): String {
        return str.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "").trim()
    }

    companion object {
        private const val TAG = "FlacStreamResolver"
        private const val MIN_FLAC_BYTES = 100_000L
        private const val MIN_MATCH_SCORE = 72
        private const val TIDAL_API_BASE_URL = "https://api.tidal.com/v1"
        private const val TIDAL_PUBLIC_TOKEN = "CzET4vdadNUFQ5JU"
        private const val GDSTUDIO_API_URL = "https://music-api.gdstudio.xyz/api.php"

        private val TITLE_CLEAN_REGEX = Pattern.compile(
            "\\s*[\\[\\(][^\\]\\)]*(?:official|audio|video|lyric|lyrics|visualizer|remaster|remastered|deluxe|bonus|hd|hq|4k|explicit|clean)[^\\]\\)]*[\\]\\)]|\\s+-\\s+(?:[^-]*\\b(?:remaster|remastered|mono|stereo|deluxe|bonus|version)\\b.*)$",
            Pattern.CASE_INSENSITIVE
        )

        fun hasFlacMagicHeader(bytes: ByteArray): Boolean {
            return bytes.size >= 4 &&
                bytes[0] == 'f'.code.toByte() &&
                bytes[1] == 'L'.code.toByte() &&
                bytes[2] == 'a'.code.toByte() &&
                bytes[3] == 'C'.code.toByte()
        }

        fun isValidFlacFile(file: File): Boolean {
            if (!file.exists() || file.length() < 42) return false
            return runCatching {
                FileInputStream(file).use { fis ->
                    val header = ByteArray(4)
                    fis.read(header) == 4 && hasFlacMagicHeader(header)
                }
            }.getOrDefault(false)
        }

        /**
         * Parses a FLAC file's mandatory STREAMINFO metadata block (bytes 4..41)
         * to extract exact sample rate, channel count, bits per sample, and total samples.
         */
        fun parseFlacStreamInfo(bytes: ByteArray): FlacStreamInfo? {
            if (bytes.size < 42 || !hasFlacMagicHeader(bytes)) return null
            val blockType = bytes[4].toInt() and 0x7F
            if (blockType != 0) return null // 0 = STREAMINFO
            val blockLength = ((bytes[5].toInt() and 0xFF) shl 16) or
                ((bytes[6].toInt() and 0xFF) shl 8) or
                (bytes[7].toInt() and 0xFF)
            if (blockLength < 34) return null

            // STREAMINFO starts at byte 8; sampleRate(20b) + channels-1(3b) + bps-1(5b) + totalSamples(36b) starts at 8+10 = 18
            val b18 = bytes[18].toInt() and 0xFF
            val b19 = bytes[19].toInt() and 0xFF
            val b20 = bytes[20].toInt() and 0xFF
            val b21 = bytes[21].toInt() and 0xFF
            val b22 = bytes[22].toInt() and 0xFF
            val b23 = bytes[23].toInt() and 0xFF
            val b24 = bytes[24].toInt() and 0xFF
            val b25 = bytes[25].toInt() and 0xFF

            val sampleRateHz = (b18 shl 12) or (b19 shl 4) or (b20 ushr 4)
            val channelCount = ((b20 ushr 1) and 0x07) + 1
            val bitDepth = (((b20 and 0x01) shl 4) or (b21 ushr 4)) + 1
            val totalSamples = ((b21.toLong() and 0x0F) shl 32) or
                (b22.toLong() shl 24) or
                (b23.toLong() shl 16) or
                (b24.toLong() shl 8) or
                b25.toLong()

            val durationMs = if (sampleRateHz > 0 && totalSamples > 0L) {
                (totalSamples * 1000L) / sampleRateHz
            } else 0L

            return FlacStreamInfo(
                sampleRateHz = sampleRateHz,
                channelCount = channelCount,
                bitDepth = bitDepth,
                totalSamples = totalSamples,
                durationMs = durationMs
            )
        }

        fun parseFlacStreamInfo(file: File): FlacStreamInfo? {
            if (!file.exists() || file.length() < 42) return null
            return runCatching {
                FileInputStream(file).use { fis ->
                    val header = ByteArray(42)
                    if (fis.read(header) == 42) parseFlacStreamInfo(header) else null
                }
            }.getOrNull()
        }

        fun computeBitrateKbps(fileSizeBytes: Long, durationMs: Long): Int {
            if (fileSizeBytes <= 0L || durationMs <= 0L) return 0
            return ((fileSizeBytes * 8L) / durationMs).toInt().coerceAtLeast(1)
        }
    }
}
