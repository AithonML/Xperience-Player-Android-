package com.xperience.player.service

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.domain.model.lyrics.LyricLine
import com.xperience.player.domain.model.lyrics.LyricSyllableSegment
import com.xperience.player.domain.model.lyrics.LyricsProviderConfig
import com.xperience.player.domain.model.lyrics.LyricsProviderId
import com.xperience.player.domain.model.lyrics.LyricsResolutionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Multi-provider online and local synchronized lyrics engine.
 * Supports:
 * - Local .LRC companion files and cached user imports
 * - YouLyPlus (LyricsPlus v2 API)
 * - Paxsenix (Apple Music syllable/line endpoint)
 * - Better Lyrics (TTML word/syllable synchronized API)
 * - LRCLIB (Community synchronized LRC database)
 * - KuGou (KRC/LRC synchronized lyrics service)
 * - SimpMusic (Community synchronized lyrics service)
 */
class LyricsProviderService(private val context: Context) {

    private val prefs = context.getSharedPreferences("xperience_lyrics_prefs", Context.MODE_PRIVATE)
    private val lyricsCacheDir = File(context.filesDir, "lyrics").apply {
        if (!exists()) mkdirs()
    }

    private val httpClient = MediaNetworkClients.sharedOkHttpClient.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
                )
                .build()
            chain.proceed(request)
        }
        .build()

    private val providers = mutableListOf<LyricsProviderConfig>()
    private val memoryCache = BoundedLruMap<String, LyricsResolutionResult>(100)

    init {
        loadProvidersConfig()
    }

    fun getProviders(): List<LyricsProviderConfig> = providers.toList()

    private fun loadProvidersConfig() {
        val defaultList = listOf(
            LyricsProviderConfig(LyricsProviderId.YouLyPlus, "YouLyPlus", "Syllable / Letter Sync", 1, true),
            LyricsProviderConfig(LyricsProviderId.Paxsenix, "Paxsenix", "Apple Syllable / Line Sync", 2, true),
            LyricsProviderConfig(LyricsProviderId.BetterLyrics, "Better Lyrics", "TTML Word / Letter Sync", 3, true),
            LyricsProviderConfig(LyricsProviderId.LrcLib, "LRCLIB", "Synced LRC + Progressive", 4, true),
            LyricsProviderConfig(LyricsProviderId.KuGou, "KuGou", "KRC / LRC Synced", 5, true),
            LyricsProviderConfig(LyricsProviderId.SimpMusic, "SimpMusic", "YouTube VideoId Sync", 6, true)
        )

        val savedOrder = prefs.getString("lyrics_providers_order", null)
        if (savedOrder.isNullOrBlank()) {
            providers.clear()
            providers.addAll(defaultList)
            return
        }

        try {
            val jsonArr = JSONArray(savedOrder)
            val merged = mutableListOf<LyricsProviderConfig>()
            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.getJSONObject(i)
                val idStr = obj.optString("id")
                val isEnabled = obj.optBoolean("isEnabled", true)
                val def = defaultList.firstOrNull { it.id.name.equals(idStr, ignoreCase = true) }
                if (def != null && merged.none { it.id == def.id }) {
                    merged.add(def.copy(isEnabled = isEnabled, priorityOrder = merged.size + 1))
                }
            }
            for (def in defaultList) {
                if (merged.none { it.id == def.id }) {
                    merged.add(def.copy(priorityOrder = merged.size + 1))
                }
            }
            providers.clear()
            providers.addAll(merged)
        } catch (e: Exception) {
            providers.clear()
            providers.addAll(defaultList)
        }
    }

    fun setProviderEnabled(id: LyricsProviderId, enabled: Boolean) {
        val idx = providers.indexOfFirst { it.id == id }
        if (idx >= 0) {
            providers[idx] = providers[idx].copy(isEnabled = enabled)
            saveProvidersConfig()
            memoryCache.clear()
        }
    }

    fun moveProviderPriority(id: LyricsProviderId, delta: Int) {
        val idx = providers.indexOfFirst { it.id == id }
        if (idx < 0) return
        val targetIdx = (idx + delta).coerceIn(0, providers.size - 1)
        if (targetIdx == idx) return

        val item = providers.removeAt(idx)
        providers.add(targetIdx, item)

        for (i in providers.indices) {
            providers[i] = providers[i].copy(priorityOrder = i + 1)
        }
        saveProvidersConfig()
        memoryCache.clear()
    }

    private fun saveProvidersConfig() {
        val jsonArr = JSONArray()
        for (p in providers) {
            val obj = JSONObject()
            obj.put("id", p.id.name)
            obj.put("isEnabled", p.isEnabled)
            obj.put("priorityOrder", p.priorityOrder)
            jsonArr.put(obj)
        }
        prefs.edit().putString("lyrics_providers_order", jsonArr.toString()).apply()
    }

    /**
     * Resolves synchronized or fixed lyrics for the specified track.
     * Prioritizes local companion and cached .LRC files, then executes the ordered online pipeline.
     */
    suspend fun resolveLyrics(track: TrackItem): LyricsResolutionResult? = withContext(Dispatchers.IO) {
        val cleanTitle = cleanTrackTitle(track.title)
        val cleanArtist = cleanArtistName(track.artist)
        if (cleanTitle.isBlank()) return@withContext null

        val cacheKey = "${cleanArtist.lowercase()}|${cleanTitle.lowercase()}"
        memoryCache[cacheKey]?.let { return@withContext it }

        // 1. Check local companion .lrc or cached user-imported .lrc
        val localLrc = findLocalOrCachedLrc(track)
        if (localLrc != null) {
            try {
                val parsed = LrcParser.parse(localLrc, track.durationMs)
                if (parsed.isNotEmpty()) {
                    val result = LyricsResolutionResult("Local .LRC", true, parsed)
                    memoryCache[cacheKey] = result
                    return@withContext result
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing local LRC", e)
            }
        }

        // 2. Query enabled online providers in user-configured priority order
        val ordered = providers.filter { it.isEnabled }.sortedBy { it.priorityOrder }
        for (provider in ordered) {
            try {
                val onlineResult: LyricsResolutionResult? = when (provider.id) {
                    LyricsProviderId.YouLyPlus -> fetchYouLyPlus(cleanTitle, cleanArtist, track.album, track.durationMs)
                    LyricsProviderId.Paxsenix -> fetchPaxsenix(cleanTitle, cleanArtist, track.durationMs)
                    LyricsProviderId.BetterLyrics -> fetchBetterLyrics(cleanTitle, cleanArtist, track.album, track.durationMs, track.id)
                    LyricsProviderId.LrcLib -> fetchLrcLib(cleanTitle, cleanArtist, track.album, track.durationMs)
                    LyricsProviderId.KuGou -> fetchKuGou(cleanTitle, cleanArtist, track.durationMs)
                    LyricsProviderId.SimpMusic -> fetchSimpMusic(cleanTitle, cleanArtist, track.id, track.durationMs)
                }

                if (onlineResult != null && onlineResult.lines.isNotEmpty()) {
                    val enriched = LrcParser.ensureProgressiveSegmentsForAllLines(onlineResult.lines, track.durationMs)
                    val finalResult = onlineResult.copy(lines = enriched)
                    memoryCache[cacheKey] = finalResult
                    return@withContext finalResult
                }
            } catch (e: Exception) {
                Log.d(TAG, "Provider ${provider.displayName} did not match '$cleanTitle': ${e.message}")
            }
        }

        null
    }

    /**
     * Imports a user-selected .LRC file, caches it permanently, and returns the parsed result.
     */
    fun importLocalLrc(track: TrackItem, lrcContent: String): LyricsResolutionResult? {
        if (lrcContent.isBlank()) return null
        return try {
            val parsed = LrcParser.parse(lrcContent, track.durationMs)
            if (parsed.isNotEmpty()) {
                val safeFile = File(lyricsCacheDir, "${track.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.lrc")
                safeFile.writeText(lrcContent, StandardCharsets.UTF_8)

                val result = LyricsResolutionResult("Local .LRC", true, parsed)
                val cleanTitle = cleanTrackTitle(track.title)
                val cleanArtist = cleanArtistName(track.artist)
                memoryCache["${cleanArtist.lowercase()}|${cleanTitle.lowercase()}"] = result
                result
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import local LRC", e)
            null
        }
    }

    private fun findLocalOrCachedLrc(track: TrackItem): String? {
        // A. App internal cache by track.id
        val cachedFile = File(lyricsCacheDir, "${track.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.lrc")
        if (cachedFile.exists() && cachedFile.length() > 10) {
            return cachedFile.readText(StandardCharsets.UTF_8)
        }

        // B. Companion .lrc next to audio file
        val audioUrl = track.audioUrl
        var audioPath: String? = when {
            audioUrl == null -> null
            audioUrl.startsWith("file://") -> audioUrl.removePrefix("file://")
            audioUrl.startsWith("/") -> audioUrl
            audioUrl.startsWith("content://media/") -> {
                try {
                    context.contentResolver.query(
                        android.net.Uri.parse(audioUrl),
                        arrayOf(android.provider.MediaStore.Audio.Media.DATA),
                        null,
                        null,
                        null
                    )?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    }
                } catch (_: Exception) {
                    null
                }
            }
            audioUrl.startsWith("content://") -> {
                try {
                    val decoded = android.net.Uri.decode(audioUrl)
                    if (decoded.contains("primary:")) {
                        val rel = decoded.substringAfter("primary:").trim('/')
                        val f = File(android.os.Environment.getExternalStorageDirectory(), rel)
                        if (f.exists()) f.absolutePath else null
                    } else null
                } catch (_: Exception) {
                    null
                }
            }
            else -> null
        }

        if (!audioPath.isNullOrBlank()) {
            val audioFile = File(audioPath)
            val parentDir = if (audioFile.exists()) audioFile.parentFile else null
            if (parentDir != null && parentDir.exists()) {
                val candidateNames = listOf(
                    "${audioFile.nameWithoutExtension}.lrc",
                    "${track.title} - ${track.artist}.lrc",
                    "${track.artist} - ${track.title}.lrc",
                    "${track.title}.lrc"
                )
                for (name in candidateNames) {
                    val lrcFile = File(parentDir, name)
                    if (lrcFile.exists() && lrcFile.length() > 10) {
                        return lrcFile.readText(StandardCharsets.UTF_8)
                    }
                }

                // Subfolders "Lyrics" or "lyrics"
                val subDirs = listOf(File(parentDir, "Lyrics"), File(parentDir, "lyrics"))
                for (sub in subDirs) {
                    if (sub.exists() && sub.isDirectory) {
                        for (name in candidateNames) {
                            val lrcFile = File(sub, name)
                            if (lrcFile.exists() && lrcFile.length() > 10) {
                                return lrcFile.readText(StandardCharsets.UTF_8)
                            }
                        }
                    }
                }
            }
        }

        // C. Check public Music directory directly for downloaded sidecars
        try {
            val musicDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
            val searchDirs = listOf(musicDir, File(musicDir, "Xperience Downloads"))
            val candidateNames = listOf(
                "${track.title} - ${track.artist}.lrc",
                "${track.artist} - ${track.title}.lrc",
                "${track.title}.lrc"
            )
            for (dir in searchDirs) {
                if (dir.exists()) {
                    for (name in candidateNames) {
                        val lrcFile = File(dir, name)
                        if (lrcFile.exists() && lrcFile.length() > 10) {
                            return lrcFile.readText(StandardCharsets.UTF_8)
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return null
    }

    // =========================================================================
    // 1. YouLyPlus (LyricsPlus v2 API)
    // =========================================================================
    private fun fetchYouLyPlus(title: String, artist: String, album: String, durationMs: Long): LyricsResolutionResult? {
        val durationSec = maxOf(30, (durationMs / 1000).toInt())
        val url = "https://lyricsplus.binimum.org/v2/lyrics/get?title=${encode(title)}&artist=${encode(artist)}&album=${encode(album)}&duration=$durationSec"

        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) return null

        val body = response.body?.string() ?: return null
        val doc = JSONObject(body)
        val lyricsArr = doc.optJSONArray("lyrics") ?: return null
        if (lyricsArr.length() == 0) return null

        val lines = mutableListOf<LyricLine>()
        var hasSyllables = false

        for (i in 0 until lyricsArr.length()) {
            val item = lyricsArr.getJSONObject(i)
            val timeMs = item.optDouble("time", 0.0).toLong()
            val durMs = item.optDouble("duration", 2500.0).toLong()
            var lineText = item.optString("text", "").trim()

            val syllables = mutableListOf<LyricSyllableSegment>()
            val sylArr = item.optJSONArray("syllabus")
            if (sylArr != null && sylArr.length() > 0) {
                val sb = StringBuilder()
                for (s in 0 until sylArr.length()) {
                    val syl = sylArr.getJSONObject(s)
                    val sylText = syl.optString("text", "")
                    val sylTime = syl.optDouble("time", timeMs.toDouble()).toLong()
                    val sylDur = maxOf(40L, syl.optDouble("duration", 200.0).toLong())

                    if (sylText.isNotEmpty()) {
                        sb.Append(sylText)
                        syllables.add(LyricSyllableSegment(sylTime, sylTime + sylDur, sylText))
                    }
                }
                if (lineText.isBlank()) {
                    lineText = sb.toString().trim()
                }
                if (syllables.isNotEmpty()) {
                    hasSyllables = true
                }
            }

            if (lineText.isBlank()) {
                lineText = "♪ • • •"
            }

            lines.add(
                LyricLine(
                    timestampMs = timeMs,
                    text = lineText,
                    isSynchronized = true,
                    endTimestampMs = timeMs + maxOf(300L, durMs),
                    syllables = if (syllables.isNotEmpty()) LrcParser.expandSyllablesToCharacters(syllables) else null
                )
            )
        }

        return if (lines.isNotEmpty()) LyricsResolutionResult("YouLyPlus", hasSyllables, lines) else null
    }

    // =========================================================================
    // 2. Paxsenix (Apple Music Syllables via iTunes Lookup)
    // =========================================================================
    private fun fetchPaxsenix(title: String, artist: String, durationMs: Long): LyricsResolutionResult? {
        val searchUrl = "https://itunes.apple.com/search?term=${encode("$artist $title")}&entity=song&limit=5"
        val searchReq = Request.Builder().url(searchUrl).build()
        val searchResp = httpClient.newCall(searchReq).execute()
        if (!searchResp.isSuccessful) return null

        val searchBody = searchResp.body?.string() ?: return null
        val searchJson = JSONObject(searchBody)
        val resultsArr = searchJson.optJSONArray("results") ?: return null
        if (resultsArr.length() == 0) return null

        var chosenTrackId: Long? = null
        for (i in 0 until resultsArr.length()) {
            val candidate = resultsArr.getJSONObject(i)
            val tid = candidate.optLong("trackId", -1L)
            if (tid > 0) {
                val candName = candidate.optString("trackName", "")
                if (chosenTrackId == null || candName.equals(title, ignoreCase = true)) {
                    chosenTrackId = tid
                    if (candName.equals(title, ignoreCase = true)) break
                }
            }
        }

        val trackId = chosenTrackId ?: return null
        val paxUrl = "https://lyrics.paxsenix.org/apple-music/lyrics?id=$trackId"
        val paxReq = Request.Builder().url(paxUrl).build()
        val paxResp = httpClient.newCall(paxReq).execute()
        if (!paxResp.isSuccessful) return null

        val paxBody = paxResp.body?.string() ?: return null
        val paxJson = JSONObject(paxBody)
        val contentArr = paxJson.optJSONArray("content") ?: return null
        if (contentArr.length() == 0) return null

        val lines = mutableListOf<LyricLine>()
        var hasSyllables = false

        for (i in 0 until contentArr.length()) {
            val item = contentArr.getJSONObject(i)
            val startMs = item.optDouble("timestamp", 0.0).toLong()
            val endMs = item.optDouble("endtime", (startMs + 2500).toDouble()).toLong()

            val syllables = mutableListOf<LyricSyllableSegment>()
            val lineBuilder = StringBuilder()

            val textArr = item.optJSONArray("text")
            if (textArr != null) {
                val count = textArr.length()
                for (w in 0 until count) {
                    val wordObj = textArr.getJSONObject(w)
                    val word = wordObj.optString("text", "")
                    val isPart = wordObj.optBoolean("part", false)
                    val wStart = wordObj.optDouble("timestamp", startMs.toDouble()).toLong()
                    val wEnd = wordObj.optDouble("endtime", (wStart + 220).toDouble()).toLong()

                    val tokenWithSpace = if (!isPart && w < count - 1 && !word.endsWith(' ')) "$word " else word
                    lineBuilder.append(tokenWithSpace)

                    if (tokenWithSpace.isNotEmpty()) {
                        syllables.add(LyricSyllableSegment(wStart, maxOf(wStart + 40L, wEnd), tokenWithSpace))
                    }
                }
            }

            val finalLineText = lineBuilder.toString().trim()
            if (finalLineText.isBlank()) continue

            if (syllables.size > 1) hasSyllables = true

            lines.add(
                LyricLine(
                    timestampMs = startMs,
                    text = finalLineText,
                    isSynchronized = true,
                    endTimestampMs = maxOf(startMs + 300L, endMs),
                    syllables = if (syllables.isNotEmpty()) LrcParser.expandSyllablesToCharacters(syllables) else null
                )
            )
        }

        return if (lines.isNotEmpty()) LyricsResolutionResult("Paxsenix", hasSyllables, lines) else null
    }

    // =========================================================================
    // 3. Better Lyrics (Apple Music TTML API)
    // =========================================================================
    private fun fetchBetterLyrics(title: String, artist: String, album: String, durationMs: Long, trackId: String): LyricsResolutionResult? {
        val durationSec = maxOf(30, (durationMs / 1000).toInt())
        val url = "https://lyrics-api.boidu.dev/getLyrics?s=${encode(title)}&a=${encode(artist)}&d=$durationSec&al=${encode(album)}"

        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) return null

        val body = response.body?.string() ?: return null
        val doc = JSONObject(body)
        val ttmlXml = doc.optString("ttml", "")
        if (ttmlXml.isBlank()) return null

        val lines = parseBetterLyricsTtml(ttmlXml)
        return if (lines.isNotEmpty()) LyricsResolutionResult("Better Lyrics", true, lines) else null
    }

    private fun parseBetterLyricsTtml(ttmlXml: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val pPattern = Pattern.compile("<p\\s+[^>]*?begin=\"([^\"]+)\"[^>]*?(?:end=\"([^\"]+)\")?[^>]*?>(.*?)</p>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
        val spanPattern = Pattern.compile("<span\\s+[^>]*?begin=\"([^\"]+)\"[^>]*?(?:end=\"([^\"]+)\")?[^>]*?>(.*?)</span>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)

        val pMatcher = pPattern.matcher(ttmlXml)
        while (pMatcher.find()) {
            val beginStr = pMatcher.group(1)
            val endStr = pMatcher.group(2)
            val innerContent = pMatcher.group(3) ?: ""

            val lineStartMs = parseTtmlTimeToMs(beginStr)
            val lineEndMs = if (!endStr.isNullOrBlank()) parseTtmlTimeToMs(endStr) else lineStartMs + 2500L

            val syllables = mutableListOf<LyricSyllableSegment>()
            val fullTextBuilder = StringBuilder()

            val spanMatcher = spanPattern.matcher(innerContent)
            var hasSpans = false
            while (spanMatcher.find()) {
                hasSpans = true
                val sBegin = spanMatcher.group(1)
                val sEnd = spanMatcher.group(2)
                val wordText = spanMatcher.group(3)?.replace(Regex("<[^>]+>"), "") ?: ""

                if (wordText.isNotEmpty()) {
                    val wStart = parseTtmlTimeToMs(sBegin)
                    val wEnd = if (!sEnd.isNullOrBlank()) parseTtmlTimeToMs(sEnd) else wStart + 220L
                    fullTextBuilder.append(wordText)
                    syllables.add(LyricSyllableSegment(wStart, wEnd, wordText))
                }
            }

            val lineText = if (hasSpans) {
                fullTextBuilder.toString().replace(WHITESPACE_REGEX, " ").trim()
            } else {
                innerContent.replace(XML_TAG_REGEX, " ").replace(WHITESPACE_REGEX, " ").trim()
            }

            if (lineText.isNotBlank()) {
                lines.add(
                    LyricLine(
                        timestampMs = lineStartMs,
                        text = lineText,
                        isSynchronized = true,
                        endTimestampMs = lineEndMs,
                        syllables = if (syllables.isNotEmpty()) LrcParser.expandSyllablesToCharacters(syllables) else null
                    )
                )
            }
        }

        return lines.sortedBy { it.timestampMs }
    }

    private fun parseTtmlTimeToMs(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        val v = value.trim()
        if (v.endsWith("ms", ignoreCase = true)) {
            return v.removeSuffix("ms").toDoubleOrNull()?.toLong() ?: 0L
        }
        if (v.endsWith("s", ignoreCase = true)) {
            return ((v.removeSuffix("s").toDoubleOrNull() ?: 0.0) * 1000).toLong()
        }
        val parts = v.split(":")
        if (parts.size == 2) {
            val mins = parts[0].toLongOrNull() ?: 0L
            val secs = parts[1].toDoubleOrNull() ?: 0.0
            return (mins * 60_000L) + (secs * 1000L).toLong()
        }
        if (parts.size == 3) {
            val hrs = parts[0].toLongOrNull() ?: 0L
            val mins = parts[1].toLongOrNull() ?: 0L
            val secs = parts[2].toDoubleOrNull() ?: 0.0
            return (hrs * 3600_000L) + (mins * 60_000L) + (secs * 1000L).toLong()
        }
        return (v.toDoubleOrNull() ?: 0.0).toLong() * 1000L
    }

    // =========================================================================
    // 4. LRCLIB (Community Synchronized Database)
    // =========================================================================
    private fun fetchLrcLib(title: String, artist: String, album: String, durationMs: Long): LyricsResolutionResult? {
        val durationSec = maxOf(30, (durationMs / 1000).toInt())
        val getUrl = "https://lrclib.net/api/get?artist_name=${encode(artist)}&track_name=${encode(title)}&album_name=${encode(album)}&duration=$durationSec"

        var syncedLrc: String? = null
        var plainLyrics: String? = null

        val getReq = Request.Builder().url(getUrl).build()
        try {
            val getResp = httpClient.newCall(getReq).execute()
            if (getResp.isSuccessful) {
                val body = getResp.body?.string() ?: ""
                val json = JSONObject(body)
                syncedLrc = json.optString("syncedLyrics", "").takeIf { it.isNotBlank() }
                plainLyrics = json.optString("plainLyrics", "").takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {}

        if (syncedLrc.isNullOrBlank()) {
            val searchUrl = "https://lrclib.net/api/search?q=${encode("$artist $title")}"
            val searchReq = Request.Builder().url(searchUrl).build()
            try {
                val searchResp = httpClient.newCall(searchReq).execute()
                if (searchResp.isSuccessful) {
                    val body = searchResp.body?.string() ?: ""
                    val arr = JSONArray(body)
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        val sl = item.optString("syncedLyrics", "").trim()
                        if (sl.isNotEmpty()) {
                            syncedLrc = sl
                            break
                        }
                        if (plainLyrics == null) {
                            plainLyrics = item.optString("plainLyrics", "").takeIf { it.isNotBlank() }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        val raw = syncedLrc ?: plainLyrics ?: return null
        val lines = LrcParser.parse(raw, durationMs)
        return if (lines.isNotEmpty()) LyricsResolutionResult("LRCLIB", true, lines) else null
    }

    // =========================================================================
    // 5. KuGou Music
    // =========================================================================
    private fun fetchKuGou(title: String, artist: String, durationMs: Long): LyricsResolutionResult? {
        val keyword = "$artist - $title"
        val searchCandidateUrl = "https://krcs.kugou.com/search?ver=1&man=yes&client=mobi&keyword=${encode(keyword)}&duration=$durationMs&hash="

        val searchReq = Request.Builder().url(searchCandidateUrl).build()
        val searchResp = httpClient.newCall(searchReq).execute()
        if (!searchResp.isSuccessful) return null

        val searchBody = searchResp.body?.string() ?: return null
        val searchJson = JSONObject(searchBody)
        val candidates = searchJson.optJSONArray("candidates") ?: return null
        if (candidates.length() == 0) return null

        val firstCandidate = candidates.getJSONObject(0)
        val id = firstCandidate.optString("id", "")
        val accessKey = firstCandidate.optString("accesskey", "")
        if (id.isBlank() || accessKey.isBlank()) return null

        val dlUrl = "https://krcs.kugou.com/download?ver=1&client=pc&id=${encode(id)}&accesskey=${encode(accessKey)}&fmt=lrc&charset=utf8"
        val dlReq = Request.Builder().url(dlUrl).build()
        val dlResp = httpClient.newCall(dlReq).execute()
        if (!dlResp.isSuccessful) return null

        val dlBody = dlResp.body?.string() ?: return null
        val dlJson = JSONObject(dlBody)
        val base64Content = dlJson.optString("content", "")
        if (base64Content.isBlank()) return null

        val decodedBytes = Base64.decode(base64Content, Base64.DEFAULT)
        val lrcText = String(decodedBytes, StandardCharsets.UTF_8)
        val lines = LrcParser.parse(lrcText, durationMs)

        return if (lines.isNotEmpty()) LyricsResolutionResult("KuGou", true, lines) else null
    }

    // =========================================================================
    // 6. SimpMusic
    // =========================================================================
    private fun fetchSimpMusic(title: String, artist: String, videoId: String?, durationMs: Long): LyricsResolutionResult? {
        if (!videoId.isNullOrBlank() && !videoId.startsWith("alb_") && !videoId.startsWith("test_")) {
            val directUrl = "https://api-lyrics.simpmusic.org/v1/${encode(videoId)}"
            try {
                val req = Request.Builder().url(directUrl).build()
                val resp = httpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val parsed = parseSimpMusicResponse(resp.body?.string() ?: "", durationMs)
                    if (parsed != null) return parsed
                }
            } catch (_: Exception) {}
        }

        val searchUrl = "https://api-lyrics.simpmusic.org/v1/search?q=${encode("$title $artist")}&limit=3"
        try {
            val searchReq = Request.Builder().url(searchUrl).build()
            val searchResp = httpClient.newCall(searchReq).execute()
            if (searchResp.isSuccessful) {
                val parsed = parseSimpMusicResponse(searchResp.body?.string() ?: "", durationMs)
                if (parsed != null) return parsed
            }
        } catch (_: Exception) {}

        // Fallback mirror via LRCLIB
        val fallback = fetchLrcLib(title, artist, "", durationMs)
        return fallback?.copy(providerName = "SimpMusic")
    }

    private fun parseSimpMusicResponse(jsonStr: String, durationMs: Long): LyricsResolutionResult? {
        if (jsonStr.isBlank()) return null
        val doc = JSONObject(jsonStr)
        val dataElem = doc.opt("data") ?: return null

        val targetItem = if (dataElem is JSONArray && dataElem.length() > 0) {
            dataElem.getJSONObject(0)
        } else if (dataElem is JSONObject) {
            dataElem
        } else return null

        val richSync = targetItem.optString("richSyncLyrics", "").takeIf { it.isNotBlank() }
        val synced = targetItem.optString("syncedLyrics", "").takeIf { it.isNotBlank() }
        val plain = targetItem.optString("plainLyrics", "").takeIf { it.isNotBlank() }

        val raw = richSync ?: synced ?: plain ?: return null
        val lines = LrcParser.parse(raw, durationMs)
        return if (lines.isNotEmpty()) LyricsResolutionResult("SimpMusic", true, lines) else null
    }

    // =========================================================================
    // Helpers
    // =========================================================================
    private fun cleanTrackTitle(rawTitle: String): String {
        if (rawTitle.isBlank()) return ""
        val cleaned = NOISE_TITLE_PATTERN.matcher(rawTitle).replaceAll("").trim()
        return if (cleaned.isBlank()) rawTitle.trim() else cleaned
    }

    private fun cleanArtistName(rawArtist: String): String {
        if (rawArtist.isBlank()) return ""
        val cleaned = rawArtist.replace(" - Topic", "", ignoreCase = true).trim()
        val split = cleaned.split(ARTIST_SPLIT_REGEX, limit = 2)
        return if (split[0].isBlank()) cleaned else split[0].trim()
    }

    private fun encode(value: String): String {
        return try {
            URLEncoder.encode(value, "UTF-8")
        } catch (_: Exception) {
            value
        }
    }

    private fun StringBuilder.Append(text: String) {
        append(text)
    }

    companion object {
        private const val TAG = "LyricsProviderService"
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val XML_TAG_REGEX = Regex("<[^>]+>")
        private val ARTIST_SPLIT_REGEX = Regex("[,;&]")
        private val NOISE_TITLE_PATTERN = Pattern.compile(
            "\\s*[(\\[]\\s*(official\\s+(music\\s+)?video|official\\s+audio|official\\s+lyric\\s+video|lyrics?|visualizer|audio|hd|4k|remastered[^)\\]]*)\\s*[)\\]]",
            Pattern.CASE_INSENSITIVE
        )
    }
}
