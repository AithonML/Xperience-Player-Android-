package com.xperience.player.service

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.domain.model.download.DownloadAudioFormat
import com.xperience.player.domain.model.download.DownloadMetadataOptions
import com.xperience.player.domain.model.download.TrackDownloadCandidate
import com.xperience.player.domain.model.download.TrackDownloadResult
import com.xperience.player.domain.model.lyrics.LyricLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Multi-quality track download service inspired by Mornye (Tidal, Deezer, GDStudio FLAC)
 * and YouTube Music (MP3 320k, M4A native). Handles direct FLAC stream downloads,
 * synchronized .lrc sidecars, high-res cover art, and offline Apple Music Canvas motion video.
 */
class MornyeMusicDownloadService(
    private val context: Context,
    private val youTubeMusicService: YouTubeMusicService,
    private val lyricsProviderService: LyricsProviderService,
    private val canvasArtworkService: AppleMusicCanvasService,
    private val mediaCatalogService: MediaCatalogService
) {

    companion object {
        private const val TAG = "MornyeDownloadService"
        private const val PREFS_NAME = "xperience_download_prefs"
        private const val PREF_FLAC_RETAG_MIGRATION_V1_DONE = "flac_retag_migration_v1_done"
        private const val TIDAL_API_BASE_URL = "https://api.tidal.com/v1"
        private const val TIDAL_PUBLIC_TOKEN = "CzET4vdadNUFQ5JU"
        private const val DEEZER_API_BASE_URL = "https://api.deezer.com"
        private const val GDSTUDIO_API_URL = "https://music-api.gdstudio.xyz/api.php"
        private const val MAX_FILE_NAME_LENGTH = 120

        private val TIDAL_ID_REGEX = Regex("^[0-9]+$")
        private val TIDAL_COVER_PATH_REGEX = Regex("^[A-Za-z0-9-]+$")
        private val CONTROL_CHARS_REGEX = Regex("[\\u0000-\\u001F\\u007F]")
        private val INVALID_FILE_CHARS_REGEX = Regex("[\\\\/:*?\"<>|]")

        private val TITLE_CLEAN_REGEX = Pattern.compile(
            "\\s*[\\[\\(][^\\]\\)]*(?:official|audio|video|lyric|lyrics|visualizer|remaster|remastered|deluxe|bonus|hd|hq|4k|explicit|clean)[^\\]\\)]*[\\]\\)]|\\s+-\\s+(?:[^-]*\\b(?:remaster|remastered|mono|stereo|deluxe|bonus|version)\\b.*)$",
            Pattern.CASE_INSENSITIVE
        )

        fun sanitizeFileName(name: String): String {
            var cleaned = name
                .replace(CONTROL_CHARS_REGEX, "")
                .replace(INVALID_FILE_CHARS_REGEX, "_")
            while (cleaned.contains("..")) {
                cleaned = cleaned.replace("..", "")
            }
            cleaned = cleaned.trim().trimStart('.').trim()
            if (cleaned.length > MAX_FILE_NAME_LENGTH) {
                cleaned = cleaned.take(MAX_FILE_NAME_LENGTH).trim().trimStart('.').trim()
            }
            val hasContent = cleaned.any { it != '_' && it != '.' && !it.isWhitespace() }
            return cleaned.takeIf { hasContent && it != "." && it != ".." } ?: "track"
        }

        internal fun isHttpsUrl(url: String?): Boolean {
            if (url.isNullOrBlank()) return false
            return url.trim().toHttpUrlOrNull()?.isHttps == true
        }

        internal fun isValidTidalId(tidalId: String?): Boolean {
            return !tidalId.isNullOrBlank() && TIDAL_ID_REGEX.matches(tidalId)
        }

        internal fun isValidTidalCoverPath(coverPath: String?): Boolean {
            return !coverPath.isNullOrBlank() && TIDAL_COVER_PATH_REGEX.matches(coverPath)
        }

        internal fun resolveSafeLocalFile(targetDir: File, fileName: String): File? {
            val candidate = File(targetDir, fileName)
            val canonicalDir = runCatching { targetDir.canonicalFile.toPath() }.getOrNull() ?: return null
            val canonicalTarget = runCatching { candidate.canonicalFile.toPath() }.getOrNull() ?: return null
            return if (canonicalTarget.startsWith(canonicalDir) && canonicalTarget != canonicalDir) {
                candidate
            } else {
                null
            }
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val httpClient = MediaNetworkClients.sharedOkHttpClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(false)
        .build()

    private val defaultDownloadDir: File by lazy {
        val musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?: File(context.filesDir, "Music")
        File(musicDir, "Xperience Downloads").apply {
            if (!exists()) mkdirs()
        }
    }

    private val _options = MutableStateFlow(loadOptions())
    val options: StateFlow<DownloadMetadataOptions> = _options.asStateFlow()

    sealed class TargetLocation {
        data class Saf(val treeDoc: DocumentFile) : TargetLocation()
        data class Local(val directory: File) : TargetLocation()
    }

    private fun resolveTargetLocation(dirSetting: String): TargetLocation {
        if (dirSetting.startsWith("content://")) {
            try {
                val treeUri = Uri.parse(dirSetting)
                val doc = DocumentFile.fromTreeUri(context, treeUri)
                if (doc != null && doc.canWrite()) {
                    return TargetLocation.Saf(doc)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving SAF tree URI: ${e.message}", e)
            }
        }

        if (dirSetting.isNotBlank() && !dirSetting.startsWith("content://")) {
            val f = File(dirSetting)
            if (f.exists() || f.mkdirs()) {
                if (f.canWrite()) {
                    return TargetLocation.Local(f)
                }
            }
        }

        val fallback = defaultDownloadDir.apply { if (!exists()) mkdirs() }
        return TargetLocation.Local(fallback)
    }

    init {
        serviceScope.launch {
            syncPreviousDownloadsToTarget()
            if (!prefs.getBoolean(PREF_FLAC_RETAG_MIGRATION_V1_DONE, false)) {
                verifyAndTagLocalFiles()
                prefs.edit().putBoolean(PREF_FLAC_RETAG_MIGRATION_V1_DONE, true).apply()
            }
        }
    }

    fun release() {
        serviceScope.cancel()
    }

    suspend fun verifyAndTagLocalFiles() = withContext(Dispatchers.IO) {
        try {
            val publicMusic = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val files = publicMusic.listFiles() ?: return@withContext
            for (file in files) {
                if (file.isFile && file.name.endsWith(".flac", ignoreCase = true) && file.name.contains(" - ")) {
                    val baseName = file.nameWithoutExtension
                    val parts = baseName.split(" - ", limit = 2)
                    val title = parts[0].trim()
                    val artist = parts[1].trim()
                    val coverFile = File(publicMusic, "$baseName.jpg")
                    val coverBytes = if (coverFile.exists() && coverFile.length() > 1024) coverFile.readBytes() else null

                    FlacMetadataTagger.tagFlacFile(
                        file,
                        FlacMetadataTagger.FlacTags(
                            title = title,
                            artist = artist,
                            album = "Downloaded",
                            coverArtBytes = coverBytes
                        )
                    )
                    notifyMediaScanner(file.absolutePath, "audio/flac")
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "verifyAndTagLocalFiles error: ${e.message}")
        }
    }

    suspend fun syncPreviousDownloadsToTarget() = withContext(Dispatchers.IO) {
        val currentDir = options.value.downloadDirectory
        if (!currentDir.startsWith("content://")) return@withContext
        try {
            val treeUri = Uri.parse(currentDir)
            val targetDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext
            if (!targetDoc.canWrite()) return@withContext

            val legacyFiles = defaultDownloadDir.listFiles() ?: return@withContext
            for (file in legacyFiles) {
                if (!file.isFile || file.length() == 0L) continue
                val fileName = file.name
                val existing = targetDoc.findFile(fileName)
                if (existing == null || !existing.exists()) {
                    val mime = when {
                        fileName.endsWith(".flac", true) -> "audio/flac"
                        fileName.endsWith(".mp3", true) -> "audio/mpeg"
                        fileName.endsWith(".m4a", true) -> "audio/mp4"
                        fileName.endsWith(".lrc", true) -> "text/plain"
                        fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true) -> "image/jpeg"
                        fileName.endsWith(".mp4", true) -> "video/mp4"
                        else -> "application/octet-stream"
                    }
                    val newDoc = targetDoc.createFile(mime, fileName) ?: continue
                    context.contentResolver.openOutputStream(newDoc.uri, "wt")?.use { out ->
                        file.inputStream().use { it.copyTo(out) }
                    }
                    notifyMediaScanner(fileName, mime)
                    Log.d(TAG, "Migrated legacy download '$fileName' to user selected folder.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing previous downloads: ${e.message}", e)
        }
    }

    fun findDownloadedTrackUri(title: String, artist: String): String? {
        val base = sanitizeFileName("$title - $artist")
        val currentDir = options.value.downloadDirectory
        val extensions = listOf(".flac", ".mp3", ".m4a")

        if (currentDir.startsWith("content://")) {
            try {
                val treeUri = Uri.parse(currentDir)
                val doc = DocumentFile.fromTreeUri(context, treeUri) ?: return null
                for (ext in extensions) {
                    val targetName = "$base$ext"
                    val match = doc.findFile(targetName)
                    if (match != null && match.isFile && match.length() > 32_768) {
                        return match.uri.toString()
                    }
                }
            } catch (_: Exception) {}
        } else {
            val dir = File(currentDir.ifBlank { defaultDownloadDir.absolutePath })
            if (dir.exists()) {
                for (ext in extensions) {
                    val f = File(dir, "$base$ext")
                    if (f.exists() && f.length() > 32_768) {
                        return f.absolutePath
                    }
                }
            }
        }
        if (defaultDownloadDir.exists()) {
            for (ext in extensions) {
                val f = File(defaultDownloadDir, "$base$ext")
                if (f.exists() && f.length() > 32_768) {
                    return f.absolutePath
                }
            }
        }
        return null
    }

    private fun loadOptions(): DownloadMetadataOptions {
        val formatName = prefs.getString("default_format", DownloadAudioFormat.FLAC_LOSSLESS.name)
            ?: DownloadAudioFormat.FLAC_LOSSLESS.name
        val format = try {
            DownloadAudioFormat.valueOf(formatName)
        } catch (_: Exception) {
            DownloadAudioFormat.FLAC_LOSSLESS
        }

        val dir = prefs.getString("download_directory", "") ?: ""
        val isValidDir = when {
            dir.isBlank() -> false
            dir.startsWith("content://") -> {
                try {
                    val uri = Uri.parse(dir)
                    val doc = DocumentFile.fromTreeUri(context, uri)
                    doc != null && doc.canWrite()
                } catch (_: Exception) {
                    false
                }
            }
            else -> File(dir).exists()
        }
        val effectiveDir = if (isValidDir) dir else defaultDownloadDir.absolutePath

        return DownloadMetadataOptions(
            defaultFormat = format,
            downloadDirectory = effectiveDir,
            includeTitleAndArtist = prefs.getBoolean("include_title_artist", true),
            includeAlbumAndTrackNumber = prefs.getBoolean("include_album_track", true),
            includeHighResCoverArt = prefs.getBoolean("include_high_res_cover", true),
            includeGenreAndReleaseYear = prefs.getBoolean("include_genre_year", true),
            includeIsrcAndCopyright = prefs.getBoolean("include_isrc_copyright", true),
            includeReplayGain = prefs.getBoolean("include_replay_gain", true),
            includeSynchronizedLyrics = prefs.getBoolean("include_synced_lyrics", true),
            includeStaticLyrics = prefs.getBoolean("include_static_lyrics", true),
            includeAppleCanvasOffline = prefs.getBoolean("include_canvas_offline", true)
        )
    }

    fun updateOptions(newOptions: DownloadMetadataOptions) {
        val dirToSave = if (newOptions.downloadDirectory.isNotBlank()) newOptions.downloadDirectory else defaultDownloadDir.absolutePath
        val finalOptions = newOptions.copy(downloadDirectory = dirToSave)

        prefs.edit()
            .putString("default_format", finalOptions.defaultFormat.name)
            .putString("download_directory", finalOptions.downloadDirectory)
            .putBoolean("include_title_artist", finalOptions.includeTitleAndArtist)
            .putBoolean("include_album_track", finalOptions.includeAlbumAndTrackNumber)
            .putBoolean("include_high_res_cover", finalOptions.includeHighResCoverArt)
            .putBoolean("include_genre_year", finalOptions.includeGenreAndReleaseYear)
            .putBoolean("include_isrc_copyright", finalOptions.includeIsrcAndCopyright)
            .putBoolean("include_replay_gain", finalOptions.includeReplayGain)
            .putBoolean("include_synced_lyrics", finalOptions.includeSynchronizedLyrics)
            .putBoolean("include_static_lyrics", finalOptions.includeStaticLyrics)
            .putBoolean("include_canvas_offline", finalOptions.includeAppleCanvasOffline)
            .apply()

        _options.value = finalOptions

        CoroutineScope(Dispatchers.IO).launch {
            syncPreviousDownloadsToTarget()
        }
    }

    /**
     * Searches multi-provider candidates across Mornye (Tidal, Deezer, GDStudio FLAC)
     * and YouTube Music with scoring and version identification.
     */
    suspend fun searchCandidates(track: TrackItem): List<TrackDownloadCandidate> = withContext(Dispatchers.IO) {
        val query = cleanSearchQuery(track.title, track.artist)
        val expectedDurationMs = track.durationMs

        val gdTask = async { searchGdStudio(query, track) }
        val tidalTask = async { searchTidal(query, track) }
        val deezerTask = async { searchDeezer(query, track) }
        val ytTask = async { searchYouTube(query, track) }

        val gdList = gdTask.await()
        val tidalList = tidalTask.await()
        val deezerList = deezerTask.await()
        val ytList = ytTask.await()

        val all = mutableListOf<TrackDownloadCandidate>()
        all.addAll(gdList)
        all.addAll(tidalList)
        all.addAll(deezerList)
        all.addAll(ytList)

        if (all.isEmpty()) {
            // Build fallback candidate directly from track item
            return@withContext listOf(
                TrackDownloadCandidate(
                    id = track.id,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    durationMs = track.durationMs,
                    coverArtUri = track.artworkUri ?: "",
                    providerLabel = "YouTube Music",
                    qualityBadge = options.value.defaultFormat.displayName,
                    versionBadge = "Original Studio",
                    matchScorePercent = 98,
                    isRecommended = true,
                    youTubeVideoId = track.id.removePrefix("ytm_")
                )
            )
        }

        // Rank candidates
        val scored = all.sortedByDescending { it.matchScorePercent }
        val best = scored.firstOrNull()

        // Build recommendation candidate (Mornye enriched)
        val recommended = best?.copy(
            isRecommended = true,
            providerLabel = if (best.gdStudioUrlId != null) "Mornye Lossless (GDStudio FLAC)" else best.providerLabel,
            qualityBadge = if (best.gdStudioUrlId != null) "FLAC Lossless" else best.qualityBadge
        ) ?: scored.first()

        val result = mutableListOf<TrackDownloadCandidate>()
        result.add(recommended)

        // Add remaining distinct versions
        val seenSignatures = mutableSetOf<String>()
        seenSignatures.add(normalizeSignature(recommended.title, recommended.artist, recommended.durationMs))

        for (cand in scored) {
            val sig = normalizeSignature(cand.title, cand.artist, cand.durationMs)
            if (!seenSignatures.contains(sig) && result.size < 6) {
                seenSignatures.add(sig)
                result.add(cand.copy(isRecommended = false))
            }
        }

        result
    }

    private fun searchGdStudio(query: String, track: TrackItem): List<TrackDownloadCandidate> {
        val list = mutableListOf<TrackDownloadCandidate>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "$GDSTUDIO_API_URL?types=search&source=netease&name=$encoded&count=8"
            val req = Request.Builder().url(url).build()

            httpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val arr = JSONArray(body)

                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val urlId = item.optString("url_id", item.optString("id", ""))
                    if (urlId.isBlank()) continue

                    val name = item.optString("name", "")
                    val album = item.optString("album", "")
                    val artist = when (val a = item.opt("artist")) {
                        is JSONArray -> (0 until a.length()).joinToString(", ") { a.optString(it) }
                        is String -> a
                        else -> ""
                    }

                    val score = calculateMatchScore(track.title, track.artist, name, artist, track.durationMs, 0)
                    if (score >= 60) {
                        list.add(
                            TrackDownloadCandidate(
                                id = "gd_$urlId",
                                title = name.ifBlank { track.title },
                                artist = artist.ifBlank { track.artist },
                                album = album.ifBlank { track.album },
                                durationMs = track.durationMs,
                                providerLabel = "GDStudio FLAC",
                                qualityBadge = "FLAC Lossless",
                                versionBadge = detectVersionBadge(name),
                                matchScorePercent = score,
                                gdStudioSource = "netease",
                                gdStudioUrlId = urlId
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "GDStudio search fallback: ${e.message}")
        }
        return list
    }

    private fun searchTidal(query: String, track: TrackItem): List<TrackDownloadCandidate> {
        val list = mutableListOf<TrackDownloadCandidate>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "$TIDAL_API_BASE_URL/search/tracks?query=$encoded&limit=6&countryCode=US"
            val req = Request.Builder()
                .url(url)
                .header("x-tidal-token", TIDAL_PUBLIC_TOKEN)
                .build()

            httpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val root = JSONObject(body)
                val items = root.optJSONArray("items") ?: return emptyList()

                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val tId = item.optString("id", "")
                    val tTitle = item.optString("title", "")
                    val tDurationSec = item.optDouble("duration", 0.0)
                    val tIsrc = item.optString("isrc", "")
                    val tCopyright = item.optString("copyright", "")

                    val artistsArr = item.optJSONArray("artists")
                    val tArtist = if (artistsArr != null && artistsArr.length() > 0) {
                        (0 until artistsArr.length()).joinToString(", ") {
                            artistsArr.optJSONObject(it)?.optString("name", "") ?: ""
                        }
                    } else item.optJSONObject("artist")?.optString("name", "") ?: ""

                    val albObj = item.optJSONObject("album")
                    val tAlbum = albObj?.optString("title", "") ?: ""
                    val coverPath = albObj?.optString("cover", "") ?: ""
                    val coverUri = if (isValidTidalCoverPath(coverPath)) {
                        "https://resources.tidal.com/images/${coverPath.replace("-", "/")}/1280x1280.jpg"
                    } else ""

                    val durMs = (tDurationSec * 1000).toLong()
                    val score = calculateMatchScore(track.title, track.artist, tTitle, tArtist, track.durationMs, durMs)
                    if (score >= 60) {
                        list.add(
                            TrackDownloadCandidate(
                                id = "tidal_$tId",
                                title = tTitle,
                                artist = tArtist,
                                album = tAlbum,
                                durationMs = durMs,
                                coverArtUri = coverUri,
                                isrc = tIsrc,
                                copyright = tCopyright,
                                providerLabel = "Tidal Hi-Fi",
                                qualityBadge = "FLAC Lossless",
                                versionBadge = detectVersionBadge(tTitle),
                                matchScorePercent = score,
                                tidalTrackId = tId
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Tidal search fallback: ${e.message}")
        }
        return list
    }

    private fun searchDeezer(query: String, track: TrackItem): List<TrackDownloadCandidate> {
        val list = mutableListOf<TrackDownloadCandidate>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "$DEEZER_API_BASE_URL/search?q=$encoded&limit=6"
            val req = Request.Builder().url(url).build()

            httpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val root = JSONObject(body)
                val data = root.optJSONArray("data") ?: return emptyList()

                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    val dzId = item.optString("id", "")
                    val dzTitle = item.optString("title", "")
                    val dzDurSec = item.optDouble("duration", 0.0)

                    val aObj = item.optJSONObject("artist")
                    val dzArtist = aObj?.optString("name", "") ?: ""

                    val alObj = item.optJSONObject("album")
                    val dzAlbum = alObj?.optString("title", "") ?: ""
                    val coverUri = alObj?.optString("cover_xl", alObj.optString("cover_big", "")) ?: ""

                    val durMs = (dzDurSec * 1000).toLong()
                    val score = calculateMatchScore(track.title, track.artist, dzTitle, dzArtist, track.durationMs, durMs)
                    if (score >= 60) {
                        list.add(
                            TrackDownloadCandidate(
                                id = "deezer_$dzId",
                                title = dzTitle,
                                artist = dzArtist,
                                album = dzAlbum,
                                durationMs = durMs,
                                coverArtUri = coverUri,
                                providerLabel = "Deezer",
                                qualityBadge = "FLAC Lossless",
                                versionBadge = detectVersionBadge(dzTitle),
                                matchScorePercent = score,
                                deezerTrackId = dzId
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Deezer search fallback: ${e.message}")
        }
        return list
    }

    private suspend fun searchYouTube(query: String, track: TrackItem): List<TrackDownloadCandidate> {
        return try {
            val songs = youTubeMusicService.searchSongs(query)
            songs.take(3).map { song ->
                val score = calculateMatchScore(track.title, track.artist, song.title, song.artist, track.durationMs, song.durationMs)
                TrackDownloadCandidate(
                    id = song.id,
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    durationMs = song.durationMs,
                    coverArtUri = song.artworkUri ?: "",
                    providerLabel = "YouTube Music",
                    qualityBadge = "MP3 320k",
                    versionBadge = detectVersionBadge(song.title),
                    matchScorePercent = score.coerceAtLeast(80),
                    youTubeVideoId = song.id.removePrefix("ytm_")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Executes the download operation according to user preferences and format.
     */
    suspend fun downloadTrack(
        track: TrackItem,
        candidate: TrackDownloadCandidate? = null,
        formatOverride: DownloadAudioFormat? = null,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): TrackDownloadResult = withContext(Dispatchers.IO) {
        val currentOpts = options.value
        val format = formatOverride ?: currentOpts.defaultFormat
        val targetLocation = resolveTargetLocation(currentOpts.downloadDirectory)

        val effectiveTitle = candidate?.title ?: track.title
        val effectiveArtist = candidate?.artist ?: track.artist
        val baseFileName = sanitizeFileName("$effectiveTitle - $effectiveArtist")

        val tempAudioFile = File(context.cacheDir, "temp_dl_${System.currentTimeMillis()}${format.extension}")

        var lrcPath: String? = null
        var canvasPath: String? = null
        var finalAudioPathOrUri: String = ""

        try {
            onProgress(0.08f, "Verifying track identity & ISRC on Mornye...")

            var directFlacDownloaded = false
            var providerSummary = candidate?.providerLabel ?: "YouTube Music"

            // 1. If FLAC Lossless requested, try direct GDStudio / Tidal FLAC stream
            if (format == DownloadAudioFormat.FLAC_LOSSLESS) {
                onProgress(0.22f, "Searching for verified FLAC Lossless stream...")
                directFlacDownloaded = tryDownloadDirectFlac(track, candidate, tempAudioFile) { p ->
                    onProgress(0.22f + (p * 0.45f), "Downloading FLAC Lossless stream (${(p * 100).toInt()}%)...")
                }

                if (directFlacDownloaded) {
                    providerSummary = "Mornye Direct Lossless FLAC"
                    onProgress(0.68f, "FLAC stream verified successfully")
                }
            }

            // 2. If not direct FLAC, download from YouTube Music
            if (!directFlacDownloaded) {
                onProgress(0.32f, "Resolving high-fidelity stream (${format.displayName})...")
                val videoId = candidate?.youTubeVideoId ?: track.id.removePrefix("ytm_")
                val streamUrl = youTubeMusicService.resolveAudioStreamUrl(videoId)

                if (streamUrl.isNullOrBlank()) {
                    tempAudioFile.delete()
                    return@withContext TrackDownloadResult(
                        success = false,
                        audioFilePath = "",
                        errorMessage = "Could not resolve audio stream"
                    )
                }

                onProgress(0.40f, "Downloading master audio stream...")
                val downloadOk = downloadUrlToFile(streamUrl, tempAudioFile) { p ->
                    onProgress(0.40f + (p * 0.28f), "Downloading audio (${(p * 100).toInt()}%)...")
                }

                if (!downloadOk || tempAudioFile.length() < 32_768) {
                    tempAudioFile.delete()
                    return@withContext TrackDownloadResult(
                        success = false,
                        audioFilePath = "",
                        errorMessage = "Audio file download failed or was incomplete"
                    )
                }
            }

            // Fetch cover bytes if available for metadata embedding and sidecar
            var coverBytes: ByteArray? = null
            val coverUrl = candidate?.coverArtUri?.ifBlank { track.artworkUri } ?: track.artworkUri
            if (isHttpsUrl(coverUrl)) {
                try {
                    val req = Request.Builder().url(coverUrl!!).build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            coverBytes = resp.body?.bytes()
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Cover art download warning: ${e.message}")
                }
            }

            // Tag FLAC file with standard Vorbis Comments & Picture block
            if (format == DownloadAudioFormat.FLAC_LOSSLESS) {
                onProgress(0.65f, "Embedding FLAC tags & metadata...")
                try {
                    FlacMetadataTagger.tagFlacFile(
                        tempAudioFile,
                        FlacMetadataTagger.FlacTags(
                            title = effectiveTitle,
                            artist = effectiveArtist,
                            album = candidate?.album ?: track.album,
                            genre = candidate?.genre ?: "Pop",
                            isrc = candidate?.isrc ?: "",
                            coverArtBytes = coverBytes
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "FLAC tagging warning: ${e.message}")
                }
            }

            // Save audio file to target location
            onProgress(0.70f, "Saving audio to your selected folder...")
            val audioFileName = "$baseFileName${format.extension}"
            val savedAudioUri = saveTempFileToTarget(targetLocation, tempAudioFile, audioFileName, format.mimeType)
            tempAudioFile.delete()

            if (savedAudioUri == null) {
                return@withContext TrackDownloadResult(
                    success = false,
                    audioFilePath = "",
                    errorMessage = "Could not save file to the selected folder"
                )
            }
            finalAudioPathOrUri = savedAudioUri

            // 3. Synchronized / Static Lyrics (.lrc sidecar)
            if (currentOpts.includeSynchronizedLyrics) {
                onProgress(0.76f, "Downloading synchronized lyrics (.lrc)...")
                try {
                    val lyricsResult = lyricsProviderService.resolveLyrics(track)
                    if (lyricsResult != null && lyricsResult.lines.isNotEmpty()) {
                        val lrcContent = formatAsLrc(lyricsResult.lines, effectiveTitle, effectiveArtist)
                        val lrcFileName = "$baseFileName.lrc"
                        lrcPath = saveTextToTarget(targetLocation, lrcContent, lrcFileName)
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Lyrics sidecar fallback: ${e.message}")
                }
            }

            // 4. High-Res Cover Art (1280px)
            if (currentOpts.includeHighResCoverArt) {
                onProgress(0.85f, "Downloading high-res cover art...")
                val coverFileName = "$baseFileName.jpg"
                val resolvedCoverBytes = coverBytes
                if (resolvedCoverBytes != null && resolvedCoverBytes.isNotEmpty()) {
                    try {
                        val tempCover = File(context.cacheDir, "temp_cover_${System.currentTimeMillis()}.jpg")
                        tempCover.writeBytes(resolvedCoverBytes)
                        saveTempFileToTarget(targetLocation, tempCover, coverFileName, "image/jpeg")
                        tempCover.delete()
                    } catch (e: Exception) {
                        Log.d(TAG, "Cover art sidecar save warning: ${e.message}")
                    }
                } else if (isHttpsUrl(coverUrl)) {
                    try {
                        val tempCover = File(context.cacheDir, "temp_cover_${System.currentTimeMillis()}.jpg")
                        if (downloadUrlToFile(coverUrl!!, tempCover)) {
                            saveTempFileToTarget(targetLocation, tempCover, coverFileName, "image/jpeg")
                            tempCover.delete()
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Cover art download fallback: ${e.message}")
                    }
                }
            }

            // 5. Offline Apple Music Canvas Motion Video (.canvas.mp4 sidecar)
            if (currentOpts.includeAppleCanvasOffline) {
                onProgress(0.92f, "Checking Apple Music animated Canvas...")
                try {
                    val canvasResult = canvasArtworkService.resolveDownloadableCanvasArtwork(
                        title = effectiveTitle,
                        artist = effectiveArtist,
                        album = candidate?.album ?: track.album
                    )
                    if (canvasResult != null && canvasResult.videoUri.isNotBlank()) {
                        val tempCanvas = File(context.cacheDir, "temp_canvas_${System.currentTimeMillis()}.mp4")
                        var hasCanvas = false
                        if (File(canvasResult.videoUri).exists()) {
                            File(canvasResult.videoUri).copyTo(tempCanvas, overwrite = true)
                            hasCanvas = tempCanvas.length() > 16_384
                        } else if (isHttpsUrl(canvasResult.videoUri)) {
                            hasCanvas = downloadUrlToFile(canvasResult.videoUri, tempCanvas) && tempCanvas.length() > 16_384
                        }

                        if (hasCanvas) {
                            val canvasFileName = "$baseFileName.canvas.mp4"
                            canvasPath = saveTempFileToTarget(targetLocation, tempCanvas, canvasFileName, "video/mp4")
                        }
                        tempCanvas.delete()
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Canvas sidecar download fallback: ${e.message}")
                }
            }

            // 6. Record and register in local catalog
            onProgress(0.98f, "Adding to your local library...")
            val localTrack = track.copy(
                id = "local_${baseFileName.hashCode()}",
                title = effectiveTitle,
                artist = effectiveArtist,
                album = candidate?.album ?: track.album,
                audioUrl = finalAudioPathOrUri
            )
            mediaCatalogService.recordTrackPlayed(localTrack)

            onProgress(1.0f, "Download complete!")

            TrackDownloadResult(
                success = true,
                audioFilePath = finalAudioPathOrUri,
                lrcFilePath = lrcPath,
                canvasVideoFilePath = canvasPath,
                formatBadge = format.badge,
                providerSummary = providerSummary
            )
        } catch (e: Exception) {
            tempAudioFile.delete()
            Log.e(TAG, "Download failed for ${track.title}", e)
            TrackDownloadResult(
                success = false,
                audioFilePath = finalAudioPathOrUri,
                errorMessage = e.message ?: "Unknown error during download"
            )
        }
    }

    private fun saveTempFileToTarget(
        target: TargetLocation,
        tempFile: File,
        fileName: String,
        mimeType: String
    ): String? {
        return when (target) {
            is TargetLocation.Saf -> {
                try {
                    val existing = target.treeDoc.findFile(fileName)
                    if (existing != null && existing.isFile) {
                        existing.delete()
                    }
                    val doc = target.treeDoc.createFile(mimeType, fileName) ?: return null
                    context.contentResolver.openOutputStream(doc.uri, "wt")?.use { out ->
                        tempFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                    notifyMediaScanner(fileName, mimeType)
                    doc.uri.toString()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed saving to SAF: ${e.message}", e)
                    null
                }
            }
            is TargetLocation.Local -> {
                try {
                    val destFile = resolveSafeLocalFile(target.directory, fileName) ?: return null
                    if (destFile.exists()) destFile.delete()
                    tempFile.copyTo(destFile, overwrite = true)
                    notifyMediaScanner(destFile.absolutePath, mimeType)
                    destFile.absolutePath
                } catch (e: Exception) {
                    Log.e(TAG, "Failed saving to Local File: ${e.message}", e)
                    if (mimeType.startsWith("audio/")) {
                        saveAudioToMediaStore(tempFile, fileName, mimeType, fileName.substringBeforeLast(" - "), fileName.substringAfter(" - ").substringBeforeLast("."), "")
                    } else null
                }
            }
        }
    }

    private fun saveAudioToMediaStore(
        tempFile: File,
        fileName: String,
        mimeType: String,
        title: String,
        artist: String,
        album: String
    ): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Audio.Media.TITLE, title)
                put(MediaStore.Audio.Media.ARTIST, artist)
                put(MediaStore.Audio.Media.ALBUM, album)
                put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            context.contentResolver.openOutputStream(uri)?.use { out ->
                tempFile.inputStream().use { it.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            uri.toString()
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore save failed: ${e.message}", e)
            null
        }
    }

    private fun saveTextToTarget(
        target: TargetLocation,
        text: String,
        fileName: String
    ): String? {
        return when (target) {
            is TargetLocation.Saf -> {
                try {
                    val existing = target.treeDoc.findFile(fileName)
                    if (existing != null && existing.isFile) {
                        existing.delete()
                    }
                    val doc = target.treeDoc.createFile("text/plain", fileName) ?: return null
                    context.contentResolver.openOutputStream(doc.uri, "wt")?.use { out ->
                        out.write(text.toByteArray(Charsets.UTF_8))
                    }
                    doc.uri.toString()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed saving text to SAF: ${e.message}", e)
                    null
                }
            }
            is TargetLocation.Local -> {
                try {
                    val file = resolveSafeLocalFile(target.directory, fileName) ?: return null
                    file.writeText(text, Charsets.UTF_8)
                    file.absolutePath
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    private fun notifyMediaScanner(fileNameOrPath: String, mimeType: String) {
        try {
            if (fileNameOrPath.startsWith("/")) {
                MediaScannerConnection.scanFile(context, arrayOf(fileNameOrPath), arrayOf(mimeType), null)
            } else {
                val currentDir = options.value.downloadDirectory
                if (currentDir.startsWith("content://")) {
                    val decoded = Uri.decode(currentDir)
                    if (decoded.contains("primary:")) {
                        val relative = decoded.substringAfter("primary:").trim('/')
                        val physicalDir = if (relative.isNotBlank()) {
                            File(Environment.getExternalStorageDirectory(), relative)
                        } else {
                            Environment.getExternalStorageDirectory()
                        }
                        val physicalFile = File(physicalDir, fileNameOrPath)
                        if (physicalFile.exists()) {
                            MediaScannerConnection.scanFile(
                                context,
                                arrayOf(physicalFile.absolutePath),
                                arrayOf(mimeType),
                                null
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private suspend fun tryDownloadDirectFlac(
        track: TrackItem,
        candidate: TrackDownloadCandidate?,
        targetFile: File,
        onFlacProgress: (Float) -> Unit
    ): Boolean {
        // 1. Try GDStudio NetEase Lossless 999/740
        val gdId = candidate?.gdStudioUrlId
        if (!gdId.isNullOrBlank()) {
            for (br in listOf(999, 740)) {
                try {
                    val urlReq = "$GDSTUDIO_API_URL?types=url&source=netease&id=${URLEncoder.encode(gdId, "UTF-8")}&br=$br"
                    val req = Request.Builder().url(urlReq).build()
                    val json = httpClient.newCall(req).execute().use { it.body?.string() } ?: continue
                    val doc = JSONObject(json)
                    val streamUrl = doc.optString("url", "")
                    if (isHttpsUrl(streamUrl)) {
                        if (downloadFlacVerified(streamUrl, targetFile, onFlacProgress)) {
                            return true
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "GDStudio stream error: ${e.message}")
                }
            }
        }

        // 2. Try Tidal FLAC stream
        val tidalId = candidate?.tidalTrackId
        if (isValidTidalId(tidalId)) {
            try {
                val playbackUrl = "$TIDAL_API_BASE_URL/tracks/$tidalId/playbackinfopostpaywall?audioquality=LOSSLESS&playbackmode=STREAM&assetpresentation=FULL&countryCode=US"
                val req = Request.Builder()
                    .url(playbackUrl)
                    .header("x-tidal-token", TIDAL_PUBLIC_TOKEN)
                    .build()

                val json = httpClient.newCall(req).execute().use { it.body?.string() }
                if (json != null) {
                    val doc = JSONObject(json)
                    val manifestStr = doc.optString("manifest", "")
                    if (manifestStr.isNotBlank()) {
                        val decodedBytes = java.util.Base64.getDecoder().decode(manifestStr)
                        val manifestJson = JSONObject(String(decodedBytes, Charsets.UTF_8))
                        val urls = manifestJson.optJSONArray("urls")
                        if (urls != null && urls.length() > 0) {
                            val streamUrl = urls.optString(0)
                            if (isHttpsUrl(streamUrl) && downloadFlacVerified(streamUrl, targetFile, onFlacProgress)) {
                                return true
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Tidal direct flac error: ${e.message}")
            }
        }

        return false
    }

    private fun downloadFlacVerified(
        streamUrl: String,
        targetFile: File,
        onProgress: (Float) -> Unit
    ): Boolean {
        if (!isHttpsUrl(streamUrl)) return false
        val temp = File(targetFile.parentFile, "${targetFile.name}.tmp")
        try {
            val req = Request.Builder().url(streamUrl).build()
            httpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return false
                val body = response.body ?: return false
                val totalLength = body.contentLength()

                FileOutputStream(temp).use { fos ->
                    val buffer = ByteArray(16384)
                    var read: Int
                    var totalRead = 0L
                    val inputStream = body.byteStream()

                    // Check for FLAC magic header: 'fLaC' = 0x66 0x4C 0x61 0x43
                    val header = ByteArray(4)
                    val headerRead = inputStream.read(header)
                    if (headerRead == 4) {
                        val isFlac = header[0] == 'f'.code.toByte() &&
                                header[1] == 'L'.code.toByte() &&
                                header[2] == 'a'.code.toByte() &&
                                header[3] == 'C'.code.toByte()
                        if (!isFlac) {
                            // Not a genuine FLAC stream
                            return false
                        }
                        fos.write(header, 0, 4)
                        totalRead += 4
                    }

                    while (inputStream.read(buffer).also { read = it } != -1) {
                        fos.write(buffer, 0, read)
                        totalRead += read
                        if (totalLength > 0) {
                            onProgress(totalRead.toFloat() / totalLength)
                        }
                    }
                }
            }

            if (temp.length() > 100_000) {
                if (targetFile.exists()) targetFile.delete()
                return temp.renameTo(targetFile)
            }
            temp.delete()
            return false
        } catch (e: Exception) {
            temp.delete()
            return false
        }
    }

    private fun downloadUrlToFile(
        url: String,
        targetFile: File,
        onProgress: (Float) -> Unit = {}
    ): Boolean {
        if (!isHttpsUrl(url)) return false
        val temp = File(targetFile.parentFile, "${targetFile.name}.tmp")
        try {
            val req = Request.Builder().url(url).build()
            httpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return false
                val body = response.body ?: return false
                val total = body.contentLength()

                FileOutputStream(temp).use { fos ->
                    val buffer = ByteArray(16384)
                    var read: Int
                    var totalRead = 0L
                    val inStream = body.byteStream()
                    while (inStream.read(buffer).also { read = it } != -1) {
                        fos.write(buffer, 0, read)
                        totalRead += read
                        if (total > 0) {
                            onProgress(totalRead.toFloat() / total)
                        }
                    }
                }
            }

            if (temp.length() > 16_384) {
                if (targetFile.exists()) targetFile.delete()
                return temp.renameTo(targetFile)
            }
            temp.delete()
            return false
        } catch (e: Exception) {
            temp.delete()
            return false
        }
    }

    private fun formatAsLrc(lines: List<LyricLine>, title: String, artist: String): String {
        val sb = java.lang.StringBuilder()
        sb.appendLine("[ti:$title]")
        sb.appendLine("[ar:$artist]")
        for (line in lines) {
            val min = line.timestampMs / 60000
            val sec = (line.timestampMs % 60000) / 1000
            val ms = (line.timestampMs % 1000) / 10
            sb.appendLine(String.format(Locale.ROOT, "[%02d:%02d.%02d]%s", min, sec, ms, line.text))
        }
        return sb.toString()
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

    private fun detectVersionBadge(title: String): String {
        val lower = title.lowercase(Locale.ROOT)
        return when {
            lower.contains("remaster") -> "Remastered"
            lower.contains("deluxe") -> "Deluxe Edition"
            lower.contains("live") -> "Live Version"
            lower.contains("acoustic") -> "Acoustic"
            lower.contains("instrumental") -> "Instrumental"
            lower.contains("remix") -> "Remix"
            else -> "Original Studio"
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

        if (reqDurMs > 0 && candDurMs > 0) {
            val diffSec = abs(reqDurMs - candDurMs) / 1000
            if (diffSec <= 3) score += 10
            else if (diffSec <= 10) score += 5
            else if (diffSec > 35) score -= 25
        }

        return score.coerceIn(0, 100)
    }

    private fun normalize(str: String): String {
        return str.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "").trim()
    }

    private fun normalizeSignature(title: String, artist: String, durationMs: Long): String {
        val durChunk = (durationMs / 4000).toInt()
        return "${normalize(title)}|${normalize(artist)}|$durChunk"
    }
}
