package com.xperience.player.service

import android.content.Context
import android.util.Log
import com.xperience.player.domain.model.AlbumDetailProfile
import com.xperience.player.domain.model.AlbumItem
import com.xperience.player.domain.model.ArtistDetailProfile
import com.xperience.player.domain.model.ArtistItem
import com.xperience.player.domain.model.NewReleaseItem
import com.xperience.player.domain.model.RadioStationItem
import com.xperience.player.domain.model.RecommendedPlaylistItem
import com.xperience.player.domain.model.TopPickItem
import com.xperience.player.domain.model.TopPickKind
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.domain.model.YtmAccountState
import com.xperience.player.domain.model.YtmMoodCategory
import com.xperience.player.domain.model.YtmMoodCategoryDetail
import com.xperience.player.domain.model.YtmMoodSection
import com.xperience.player.domain.model.YtmMoodsAndGenresFeed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class CatalogQueryResult(
    val songs: List<TrackItem>,
    val albums: List<AlbumItem>,
    val artists: List<ArtistItem>
)

enum class SearchFilter(val label: String) {
    ALL("All"),
    SONGS("Songs"),
    ARTISTS("Artists"),
    ALBUMS("Albums")
}

data class SearchResultData(
    val songs: List<TrackItem> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
    val artists: List<ArtistItem> = emptyList()
)

data class YtmHomeFeedResult(
    val personalizedTracks: List<TrackItem> = emptyList(),
    val personalizedAlbums: List<AlbumItem> = emptyList(),
    val playlistsMadeForYou: List<RecommendedPlaylistItem> = emptyList(),
    val radioStations: List<RadioStationItem> = emptyList(),
    val newReleases: List<NewReleaseItem> = emptyList(),
    val featuredTopPicks: List<TopPickItem> = emptyList(),
    val isPersonalized: Boolean = false
)

data class ResolvedMusicVideo(
    val videoId: String,
    val title: String,
    val artist: String,
    val streamUrl: String,
    val thumbnailUrl: String,
    val durationMs: Long = 0L,
    val fallbackStreamUrls: List<String> = emptyList(),
    val httpHeaders: Map<String, String> = emptyMap(),
    val expiresAtMs: Long = System.currentTimeMillis() + 3L * 60L * 60L * 1000L
)

class YouTubeMusicService(
    val httpClient: OkHttpClient = MediaNetworkClients.sharedOkHttpClient,
    private val searchUrl: String = SEARCH_URL,
    private val browseUrl: String = BROWSE_URL,
    private val swJsUrl: String = SW_JS_URL,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val nextUrl: String = NEXT_URL,
    private val accountMenuUrl: String = ACCOUNT_MENU_URL,
    private val accountSwitcherUrl: String = ACCOUNT_SWITCHER_URL,
    @Volatile private var authManager: YouTubeAuthManager? = null,
    private val ytmPlayerUrl: String = YTM_PLAYER_URL,
    private val atvCacheFileOverride: File? = null,
    private val audioCacheDirOverride: File? = null,
    private val playerScriptResolverOverride: PlayerScriptResolver? = null
) {

    private data class TimedCacheEntry<T>(
        val value: T,
        val cachedAtMs: Long,
        val expiresAtMs: Long
    )

    private data class ResolvedAudioStream(
        val url: String,
        val clientName: String,
        val userAgent: String,
        val origin: String?,
        val referer: String?,
        val cookie: String? = null,
        val contentLength: Long? = null,
        val allowChunkedRangeRetry: Boolean = false
    )

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val cache = ConcurrentHashMap<String, CatalogQueryResult>()
    private val streamCache = ConcurrentHashMap<String, ResolvedAudioStream>()
    private val videoStreamCache = ConcurrentHashMap<String, ResolvedMusicVideo>()
    private val omvCounterpartCache = ConcurrentHashMap<String, String>()
    private val atvCounterpartCache = ConcurrentHashMap<String, String>()
    private val atvCacheLock = Any()
    @Volatile private var atvCounterpartCacheFile: File? = atvCacheFileOverride
    @Volatile private var audioCacheDir: File? = audioCacheDirOverride
    @Volatile private var atvCacheLoaded: Boolean = false
    @Volatile private var playerScriptResolver: PlayerScriptResolver? = playerScriptResolverOverride

    private fun activeScriptResolver(): PlayerScriptResolver {
        return playerScriptResolverOverride ?: playerScriptResolver ?: NoOpPlayerScriptResolver
    }

    fun attachCacheContext(context: Context) {
        val appContext = context.applicationContext ?: context
        if (atvCacheFileOverride == null) {
            val file = getAtvCounterpartCacheFile(appContext)
            if (atvCounterpartCacheFile != file) {
                atvCounterpartCacheFile = file
                atvCacheLoaded = false
            }
        }
        if (audioCacheDirOverride == null) {
            audioCacheDir = File(appContext.cacheDir, "audio_cache")
        }
        if (playerScriptResolverOverride == null && playerScriptResolver == null) {
            playerScriptResolver = AndroidWebViewPlayerScriptSolver(
                context = appContext,
                httpClient = httpClient,
                clockMs = clockMs
            )
        }
        ensureAtvCounterpartCacheLoaded()
    }

    fun getAtvCounterpartSnapshot(): Map<String, String> {
        ensureAtvCounterpartCacheLoaded()
        return atvCounterpartCache.toMap()
    }

    private fun ensureAtvCounterpartCacheLoaded() {
        val file = atvCounterpartCacheFile ?: return
        if (atvCacheLoaded) return
        synchronized(atvCacheLock) {
            if (atvCacheLoaded) return
            val loaded = loadAtvCounterpartMapSafe(file)
            loaded.forEach { (k, v) -> atvCounterpartCache.putIfAbsent(k, v) }
            atvCacheLoaded = true
            if (atvCounterpartCache.size > loaded.size) {
                persistAtvCounterpartCacheLocked(file)
            }
        }
    }

    private fun persistAtvCounterpartCacheSafe() {
        val file = atvCounterpartCacheFile ?: return
        synchronized(atvCacheLock) {
            persistAtvCounterpartCacheLocked(file)
        }
    }

    private fun persistAtvCounterpartCacheLocked(file: File) {
        runCatching {
            file.parentFile?.mkdirs()
            val json = JSONObject()
            atvCounterpartCache.forEach { (k, v) -> json.put(k, v) }
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(json.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(json.toString())
                tmp.delete()
            }
        }.onFailure {
            safeLog("Failed to persist ATV counterpart cache", it)
        }
    }
    private val searchFilterCache = ConcurrentHashMap<String, SearchResultData>()
    private val artistAvatarCache = ConcurrentHashMap<String, String>()
    private val inFlightArtistAvatars = ConcurrentHashMap<String, Deferred<String?>>()

    private val artistProfileCache = ConcurrentHashMap<String, TimedCacheEntry<ArtistDetailProfile>>()
    private val albumDetailCache = ConcurrentHashMap<String, TimedCacheEntry<AlbumDetailProfile>>()
    private val moodCategoryDetailCache = ConcurrentHashMap<String, TimedCacheEntry<YtmMoodCategoryDetail>>()
    @Volatile private var moodsAndGenresCache: TimedCacheEntry<YtmMoodsAndGenresFeed>? = null
    private val inFlightArtistProfiles = ConcurrentHashMap<String, Deferred<ArtistDetailProfile?>>()
    private val inFlightAlbumDetails = ConcurrentHashMap<String, Deferred<AlbumDetailProfile?>>()

    private val visitorMutex = Mutex()
    @Volatile
    private var cachedVisitorData: String? = null
    @Volatile
    private var cachedCookies: String? = null
    @Volatile
    private var visitorDataExpiry = 0L

    companion object {
        private const val TAG = "YouTubeMusicService"
        private const val SEARCH_URL = "https://music.youtube.com/youtubei/v1/search?prettyPrint=false"
        private const val BROWSE_URL = "https://music.youtube.com/youtubei/v1/browse?prettyPrint=false"
        private const val NEXT_URL = "https://music.youtube.com/youtubei/v1/next?prettyPrint=false"
        private const val ACCOUNT_MENU_URL = "https://music.youtube.com/youtubei/v1/account/account_menu?prettyPrint=false"
        private const val ACCOUNT_SWITCHER_URL = "https://music.youtube.com/getAccountSwitcherEndpoint"
        private const val PLAYER_URL = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
        internal const val YTM_PLAYER_URL = "https://music.youtube.com/youtubei/v1/player?prettyPrint=false"
        private const val SW_JS_URL = "https://www.youtube.com/sw.js_data?hl=en"
        private const val SONGS_PARAM = "EgWKAQIIAWoMEA4QChADEAQQCRAF"

        private val TRACK_CREDITS_BROWSE_ID_REGEX = Regex("""^MPTC([A-Za-z0-9_-]{11})$""")
        private val VIDEO_ID_REGEX = Regex("""^[A-Za-z0-9_-]{11}$""")
        private val YEAR_REGEX = Regex("""^\d{4}$""")
        private val DURATION_REGEX = Regex("""^\d{1,2}:\d{2}(:\d{2})?$""")
        private val VISTAS_COUNT_REGEX = Regex("""\b\d+(?:[.,]\d+)?\s*[KMBkmb]?\s*vistas\b""", RegexOption.IGNORE_CASE)
        private val VIDEO_TITLE_TAG_REGEX = Regex(
            """[\(\[]\s*(?:official\s*(?:music\s*)?video|official\s*lyric\s*video|lyric\s*video|music\s*video|video\s*oficial|audio\s*oficial|official\s*audio|official\s*hd\s*video|4k\s*remaster(?:ed)?|hd|4k|lyrics?|sub\.?\s*espa[nñ]ol)\s*[\)\]]""",
            RegexOption.IGNORE_CASE
        )
        private val NON_ALPHANUMERIC_SPACE_REGEX = Regex("""[^\p{L}\p{N}\s]+""")
        private val WHITESPACE_REGEX = Regex("""\s+""")

        fun getAtvCounterpartCacheFile(context: Context): File {
            val appContext = context.applicationContext ?: context
            val cacheFolder = File(appContext.filesDir, "OnlineCache")
            return File(cacheFolder, "atv_counterparts_cache.json")
        }

        fun loadAtvCounterpartMapSafe(cacheFile: File): Map<String, String> {
            return runCatching {
                if (!cacheFile.exists() || !cacheFile.isFile) return emptyMap()
                val raw = cacheFile.readText()
                if (raw.isBlank()) return emptyMap()
                val json = JSONObject(raw)
                val map = HashMap<String, String>(json.length())
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next().trim()
                    val value = json.optString(key, "").trim()
                    if (key.isNotEmpty() && value.isNotEmpty()) {
                        map[key] = value
                    }
                }
                map
            }.getOrElse { emptyMap() }
        }

        fun isPlayCountLabel(text: String?): Boolean {
            if (text.isNullOrBlank()) return false
            val t = text.trim()
            return t.contains("reproducciones", ignoreCase = true) ||
                t.contains("plays", ignoreCase = true) ||
                t.contains("visualizaciones", ignoreCase = true) ||
                t.contains("views", ignoreCase = true) ||
                VISTAS_COUNT_REGEX.containsMatchIn(t)
        }

        fun sanitizePersistedOnlineTrack(
            track: TrackItem,
            counterparts: Map<String, String>
        ): Pair<TrackItem, Boolean> {
            var changed = false
            var nextId = track.id
            var nextAudioUrl = track.audioUrl
            var nextAlbum = track.album

            if (nextId.startsWith("ytm_")) {
                val rawVideoId = nextId.removePrefix("ytm_").trim()
                val resolvedVideoId = counterparts[rawVideoId]
                if (!resolvedVideoId.isNullOrBlank() && resolvedVideoId != rawVideoId) {
                    nextId = "ytm_$resolvedVideoId"
                    val currentUrl = nextAudioUrl.orEmpty()
                    if (currentUrl.isBlank() ||
                        currentUrl.startsWith("ytmusic://", ignoreCase = true) ||
                        currentUrl.contains("youtube.com/watch", ignoreCase = true) ||
                        currentUrl.contains("audio_cache/$rawVideoId.m4a", ignoreCase = true)
                    ) {
                        nextAudioUrl = "https://www.youtube.com/watch?v=$resolvedVideoId"
                    }
                    changed = true
                }
                if (nextAlbum.isBlank() || isPlayCountLabel(nextAlbum)) {
                    nextAlbum = "Single"
                    changed = true
                }
            }

            if (!changed) return track to false
            return track.copy(
                id = nextId,
                audioUrl = nextAudioUrl,
                album = nextAlbum
            ) to true
        }

        fun sanitizePersistedTracks(
            tracks: List<TrackItem>,
            counterparts: Map<String, String>
        ): Pair<List<TrackItem>, Boolean> {
            val result = ArrayList<TrackItem>(tracks.size)
            val seenIds = HashSet<String>(tracks.size)
            var modified = false

            for (track in tracks) {
                val (sanitized, changed) = sanitizePersistedOnlineTrack(track, counterparts)
                if (changed) modified = true
                if (seenIds.add(sanitized.id)) {
                    result.add(sanitized)
                } else {
                    modified = true
                }
            }
            return result to modified
        }
        private const val ALBUMS_PARAM = "EgWKAQIYAWoMEA4QChADEAQQCRAF"
        private const val ARTISTS_PARAM = "EgWKAQIgAWoMEA4QChADEAQQCRAF"
        private const val PLAYLISTS_PARAM = "EgWKAQIoAWoMEA4QChADEAQQCRAF"
        private const val VIDEOS_PARAM = "EgWKAQIQAWoMEA4QChADEAQQCRAF"
        private const val MOODS_AND_GENRES_BROWSE_ID = "FEmusic_moods_and_genres"
        private const val WEB_REMIX_CLIENT_ID = "67"
        private const val WEB_REMIX_CLIENT_VERSION = "1.20260213.01.00"
        private const val WEB_CLIENT_ID = "1"
        private const val WEB_CLIENT_VERSION = "2.20260213.01.00"
        private const val MWEB_CLIENT_ID = "2"
        private const val MWEB_CLIENT_VERSION = "2.20260213.01.00"
        private const val MWEB_PLAYER_URL = "https://m.youtube.com/youtubei/v1/player?prettyPrint=false"
        private const val MOBILE_WEB_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Mobile Safari/537.36"
        private const val TVHTML5_CLIENT_ID = "7"
        private const val TVHTML5_CLIENT_VERSION = "7.20260114.12.00"
        private const val TVHTML5_USER_AGENT =
            "Mozilla/5.0 (SMART-TV; LINUX; Tizen 7.0) AppleWebKit/537.36 (KHTML, like Gecko) 94.0.4606.31/7.0 TV Safari/537.36"
        private const val ANDROID_CLIENT_ID = "3"
        private const val ANDROID_CLIENT_VERSION = "20.10.38"
        private const val ANDROID_USER_AGENT =
            "com.google.android.youtube/20.10.38 (Linux; U; Android 14; en_US; Pixel 7 Build/AP2A.240805.005) gzip"
        private const val ANDROID_VR_CLIENT_ID = "28"
        private const val ANDROID_VR_CLIENT_VERSION = "1.65.10"
        private const val ANDROID_VR_USER_AGENT =
            "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
        private const val IOS_CLIENT_ID = "5"
        private const val IOS_CLIENT_VERSION = "20.10.4"
        private const val IOS_USER_AGENT =
            "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)"
        private const val VISIONOS_CLIENT_VERSION = "0.1"
        private const val VISIONOS_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15"
        private const val DESKTOP_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
        private const val DETAIL_CACHE_TTL_MS = 30L * 60L * 1000L
        private const val MAX_DETAIL_CACHE_ENTRIES = 100
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        @Volatile
        private var instance: YouTubeMusicService? = null

        fun getInstance(): YouTubeMusicService {
            instance?.let { return it }
            synchronized(this) {
                instance?.let { return it }
                return YouTubeMusicService().also { instance = it }
            }
        }
    }

    fun attachAuthManager(manager: YouTubeAuthManager) {
        this.authManager = manager
    }

    private data class InnerTubePostResult(
        val httpCode: Int = -1,
        val json: JSONObject? = null,
        val isNetworkError: Boolean = false
    ) {
        val isSuccessful: Boolean
            get() = httpCode in 200..299 && json != null
    }

    private data class AccountSwitcherResult(
        val httpCode: Int = -1,
        val accountState: YtmAccountState? = null,
        val dataSyncId: String? = null,
        val isConfirmedSuccess: Boolean = false,
        val isExplicitFailure: Boolean = false
    )

    internal fun buildWebRemixContextJson(
        hl: String = "en",
        gl: String = "US",
        includeUser: Boolean = true
    ): JSONObject {
        val manager = authManager
        val isSignedIn = manager?.isSignedIn() == true
        val visitorData = manager?.getVisitorData()?.takeIf { it.isNotBlank() } ?: cachedVisitorData
        val onBehalfOfUser = if (includeUser && isSignedIn) manager.getOnBehalfOfUser() else null

        return JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", "WEB_REMIX")
                put("clientVersion", WEB_REMIX_CLIENT_VERSION)
                put("hl", hl)
                put("gl", gl)
                if (!visitorData.isNullOrBlank()) {
                    put("visitorData", visitorData)
                }
            })
            put("request", JSONObject().apply {
                put("internalExperimentFlags", JSONArray())
                put("useSsl", true)
            })
            if (includeUser && isSignedIn && !onBehalfOfUser.isNullOrBlank()) {
                put("user", JSONObject().apply {
                    put("lockedSafetyMode", false)
                    put("onBehalfOfUser", onBehalfOfUser)
                })
            }
        }
    }

    internal fun buildWebRemixRequestBuilder(
        url: String,
        payload: JSONObject,
        includeAuthHeaders: Boolean = true,
        includePageId: Boolean = true
    ): Request.Builder {
        val builder = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("User-Agent", DESKTOP_USER_AGENT)
            .addHeader("Accept", "*/*")
            .addHeader("Accept-Language", "en-US,en;q=0.9")
            .addHeader("Origin", "https://music.youtube.com")
            .addHeader("Referer", "https://music.youtube.com/")
            .addHeader("Content-Type", "application/json")
            .addHeader("X-Goog-Api-Format-Version", "1")
            .addHeader("X-YouTube-Client-Name", WEB_REMIX_CLIENT_ID)
            .addHeader("X-YouTube-Client-Version", WEB_REMIX_CLIENT_VERSION)

        if (includeAuthHeaders) {
            val manager = authManager
            if (manager != null && manager.isSignedIn()) {
                builder.header("X-Youtube-Bootstrap-Logged-In", "true")
                val authHeaders = manager.buildAuthHeaders(
                    timestampSeconds = clockMs() / 1000L,
                    includePageId = includePageId
                )
                authHeaders.forEach { (key, value) ->
                    if (!key.equals("Origin", ignoreCase = true) && !key.equals("Referer", ignoreCase = true)) {
                        builder.header(key, value)
                    }
                }
                if (authHeaders["X-Goog-Visitor-Id"].isNullOrBlank()) {
                    cachedVisitorData?.takeIf { it.isNotBlank() }?.let {
                        builder.header("X-Goog-Visitor-Id", it)
                    }
                }
            } else {
                val visitor = cachedVisitorData
                if (!visitor.isNullOrBlank()) {
                    builder.header("X-Goog-Visitor-Id", visitor)
                }
            }
        }
        return builder
    }

    private fun executeInnerTubePostWithStatus(
        url: String,
        payload: JSONObject,
        includeAuthHeaders: Boolean = true,
        includePageId: Boolean = true
    ): InnerTubePostResult {
        return try {
            val request = buildWebRemixRequestBuilder(url, payload, includeAuthHeaders, includePageId).build()
            httpClient.newCall(request).execute().use { response ->
                val code = response.code
                val bodyString = response.body?.string()
                if (!response.isSuccessful || bodyString.isNullOrBlank()) {
                    return InnerTubePostResult(httpCode = code, json = null)
                }
                val cleaned = if (bodyString.startsWith(")]}'")) {
                    bodyString.substringAfter('\n')
                } else {
                    bodyString
                }
                val parsed = runCatching { JSONObject(cleaned) }.getOrNull()
                InnerTubePostResult(httpCode = code, json = parsed)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            InnerTubePostResult(isNetworkError = true)
        }
    }

    private fun executeInnerTubePost(
        url: String,
        payload: JSONObject,
        includeAuthHeaders: Boolean = true
    ): JSONObject? = executeInnerTubePostWithStatus(url, payload, includeAuthHeaders).json

    private fun safeLog(message: String, error: Throwable? = null) {
        runCatching {
            if (error != null) Log.w(TAG, message, error) else Log.d(TAG, message)
        }
    }

    private fun extractCleanVideoId(videoIdOrId: String): String? {
        val clean = when {
            videoIdOrId.contains("v=") -> videoIdOrId.substringAfter("v=").substringBefore("&")
            videoIdOrId.startsWith("ytm_") -> videoIdOrId.removePrefix("ytm_")
            else -> videoIdOrId
        }.trim()
        return clean.takeIf { it.isNotBlank() }
    }

    private data class PlayerClientSpec(
        val clientName: String,
        val clientId: String?,
        val clientVersion: String,
        val url: String,
        val userAgent: String,
        val origin: String,
        val referer: String,
        val deviceMake: String? = null,
        val deviceModel: String? = null,
        val osName: String? = null,
        val osVersion: String? = null,
        val androidSdkVersion: Int? = null,
        val utcOffsetMinutes: Int? = null,
        val embedUrl: String? = null,
        val sendOriginOnDownload: Boolean = true,
        val sendPlaybackContext: Boolean = false,
        val decipherNChallenge: Boolean = true,
        val allowChunkedRangeRetry: Boolean = false
    )

    private data class AudioFormatCandidate(
        val url: String,
        val sp: String = "",
        val signature: String = "",
        val contentLength: Long? = null
    )

    private fun buildStreamPlayerSpecs(isSignedIn: Boolean): List<Pair<PlayerClientSpec, Boolean>> {
        val visionOsSpec = PlayerClientSpec(
            clientName = "VISIONOS",
            clientId = null,
            clientVersion = VISIONOS_CLIENT_VERSION,
            url = PLAYER_URL,
            userAgent = VISIONOS_USER_AGENT,
            origin = YouTubeAuthManager.YOUTUBE_WWW_ORIGIN,
            referer = "${YouTubeAuthManager.YOUTUBE_WWW_ORIGIN}/",
            deviceMake = "Apple",
            deviceModel = "RealityDevice14,1",
            osName = "visionOS",
            osVersion = "2.0",
            utcOffsetMinutes = 0,
            sendOriginOnDownload = false,
            sendPlaybackContext = false,
            decipherNChallenge = true,
            allowChunkedRangeRetry = false
        )
        val webRemixSpec = PlayerClientSpec(
            clientName = "WEB_REMIX",
            clientId = WEB_REMIX_CLIENT_ID,
            clientVersion = WEB_REMIX_CLIENT_VERSION,
            url = ytmPlayerUrl,
            userAgent = DESKTOP_USER_AGENT,
            origin = YouTubeAuthManager.YOUTUBE_MUSIC_ORIGIN,
            referer = "${YouTubeAuthManager.YOUTUBE_MUSIC_ORIGIN}/",
            sendOriginOnDownload = true,
            sendPlaybackContext = true,
            decipherNChallenge = true,
            allowChunkedRangeRetry = true
        )
        val mwebSpec = PlayerClientSpec(
            clientName = "MWEB",
            clientId = MWEB_CLIENT_ID,
            clientVersion = MWEB_CLIENT_VERSION,
            url = MWEB_PLAYER_URL,
            userAgent = MOBILE_WEB_USER_AGENT,
            origin = "https://m.youtube.com",
            referer = "https://m.youtube.com/",
            sendOriginOnDownload = true,
            sendPlaybackContext = true,
            decipherNChallenge = true,
            allowChunkedRangeRetry = true
        )
        val tvHtml5Spec = PlayerClientSpec(
            clientName = "TVHTML5",
            clientId = TVHTML5_CLIENT_ID,
            clientVersion = TVHTML5_CLIENT_VERSION,
            url = PLAYER_URL,
            userAgent = TVHTML5_USER_AGENT,
            origin = YouTubeAuthManager.YOUTUBE_WWW_ORIGIN,
            referer = "${YouTubeAuthManager.YOUTUBE_WWW_ORIGIN}/",
            sendOriginOnDownload = true,
            sendPlaybackContext = true,
            decipherNChallenge = true,
            allowChunkedRangeRetry = true
        )
        val webSpec = PlayerClientSpec(
            clientName = "WEB",
            clientId = WEB_CLIENT_ID,
            clientVersion = WEB_CLIENT_VERSION,
            url = PLAYER_URL,
            userAgent = DESKTOP_USER_AGENT,
            origin = YouTubeAuthManager.YOUTUBE_WWW_ORIGIN,
            referer = "${YouTubeAuthManager.YOUTUBE_WWW_ORIGIN}/",
            sendOriginOnDownload = true,
            sendPlaybackContext = true,
            decipherNChallenge = true,
            allowChunkedRangeRetry = true
        )
        val androidSpec = PlayerClientSpec(
            clientName = "ANDROID",
            clientId = ANDROID_CLIENT_ID,
            clientVersion = ANDROID_CLIENT_VERSION,
            url = PLAYER_URL,
            userAgent = ANDROID_USER_AGENT,
            origin = YouTubeAuthManager.YOUTUBE_WWW_ORIGIN,
            referer = "${YouTubeAuthManager.YOUTUBE_WWW_ORIGIN}/",
            deviceMake = "Google",
            deviceModel = "Pixel 7",
            osName = "Android",
            osVersion = "14",
            androidSdkVersion = 34,
            sendOriginOnDownload = false,
            decipherNChallenge = false
        )
        val iosSpec = PlayerClientSpec(
            clientName = "IOS",
            clientId = IOS_CLIENT_ID,
            clientVersion = IOS_CLIENT_VERSION,
            url = PLAYER_URL,
            userAgent = IOS_USER_AGENT,
            origin = YouTubeAuthManager.YOUTUBE_WWW_ORIGIN,
            referer = "${YouTubeAuthManager.YOUTUBE_WWW_ORIGIN}/",
            deviceMake = "Apple",
            deviceModel = "iPhone16,2",
            osName = "iPhone",
            osVersion = "18.3.2.22D82",
            sendOriginOnDownload = false,
            decipherNChallenge = false
        )
        val androidVrSpec = PlayerClientSpec(
            clientName = "ANDROID_VR",
            clientId = ANDROID_VR_CLIENT_ID,
            clientVersion = ANDROID_VR_CLIENT_VERSION,
            url = PLAYER_URL,
            userAgent = ANDROID_VR_USER_AGENT,
            origin = YouTubeAuthManager.YOUTUBE_WWW_ORIGIN,
            referer = "${YouTubeAuthManager.YOUTUBE_WWW_ORIGIN}/",
            deviceMake = "Oculus",
            deviceModel = "Quest 3",
            osName = "Android",
            osVersion = "12L",
            androidSdkVersion = 32,
            sendOriginOnDownload = false,
            decipherNChallenge = false
        )

        // Never pair web Cookie/SAPISIDHASH auth with non-web clients (InnerTube rejects them with HTTP 400).
        // When signed in, start with unauthenticated VISIONOS (fast & direct for non-age-restricted tracks),
        // then try authenticated web-family clients for age-restricted tracks, then unauth native fallbacks.
        return if (isSignedIn) {
            listOf(
                visionOsSpec.copy(clientName = "VISIONOS_ANON") to false,
                webRemixSpec to true,
                mwebSpec to true,
                tvHtml5Spec to true,
                webSpec to true,
                webRemixSpec.copy(clientName = "WEB_REMIX_ANON") to false,
                mwebSpec.copy(clientName = "MWEB_ANON") to false,
                androidSpec.copy(clientName = "ANDROID_ANON") to false,
                iosSpec.copy(clientName = "IOS_ANON") to false,
                androidVrSpec.copy(clientName = "ANDROID_VR_ANON") to false
            )
        } else {
            listOf(
                visionOsSpec to false,
                webRemixSpec.copy(clientName = "WEB_REMIX_ANON") to false,
                mwebSpec.copy(clientName = "MWEB_ANON") to false,
                androidSpec to false,
                iosSpec to false,
                androidVrSpec to false
            )
        }
    }

    private fun isPlayabilityBlocked(status: String?): Boolean {
        val clean = status?.trim().orEmpty()
        if (clean.isBlank()) return false
        return !clean.equals("OK", ignoreCase = true) &&
            !clean.equals("LIVE_STREAM_OFFLINE", ignoreCase = true)
    }

    private suspend fun resolveDirectAudioStream(
        canonicalVideoId: String,
        skipClients: Set<String> = emptySet()
    ): ResolvedAudioStream? {
        val manager = authManager
        val isSignedIn = manager?.isSignedIn() == true
        val specs = buildStreamPlayerSpecs(isSignedIn)

        var anonVisitorAndCookies: Pair<String, String>? = null
        suspend fun ensureAnonVisitorAndCookies(): Pair<String, String>? {
            if (anonVisitorAndCookies == null) {
                anonVisitorAndCookies = getVisitorDataAndCookies()
            }
            return anonVisitorAndCookies
        }

        val userVisitorData = manager?.getVisitorData()?.takeIf { it.isNotBlank() }

        for ((spec, useAuth) in specs) {
            if (spec.clientName in skipClients) continue

            val visitorData: String?
            val anonCookies: String?
            if (useAuth) {
                visitorData = userVisitorData ?: cachedVisitorData
                anonCookies = null
            } else {
                val pair = ensureAnonVisitorAndCookies()
                visitorData = pair?.first?.takeIf { it.isNotBlank() } ?: cachedVisitorData
                anonCookies = pair?.second?.takeIf { it.isNotBlank() }
                    ?: YouTubeAuthManager.DEFAULT_SOCS_COOKIE
            }

            val resolved = executePlayerStreamResolution(
                videoId = canonicalVideoId,
                spec = spec,
                useAuth = useAuth,
                visitorData = visitorData,
                anonCookies = anonCookies
            )
            if (resolved != null) {
                return resolved
            }
        }
        return null
    }

    private suspend fun executePlayerStreamResolution(
        videoId: String,
        spec: PlayerClientSpec,
        useAuth: Boolean,
        visitorData: String?,
        anonCookies: String?
    ): ResolvedAudioStream? {
        val manager = authManager
        val scriptResolver = activeScriptResolver()
        val sts = if (spec.sendPlaybackContext) scriptResolver.getSignatureTimestamp() else null
        val canDelegate = useAuth && manager != null &&
            (manager.getDelegatedPageId() != null || manager.getOnBehalfOfUser() != null)
        val attemptPageIds = if (canDelegate) listOf(true, false) else listOf(false)

        for (includeDelegation in attemptPageIds) {
            try {
                val payload = JSONObject().apply {
                    put("videoId", videoId)
                    put("contentCheckOk", true)
                    put("racyCheckOk", true)
                    if (sts != null) {
                        put("playbackContext", JSONObject().apply {
                            put("contentPlaybackContext", JSONObject().apply {
                                put("signatureTimestamp", sts)
                                put("html5Preference", "HTML5_PREF_WANTS")
                            })
                        })
                    }
                    put("context", JSONObject().apply {
                        put("client", JSONObject().apply {
                            put("clientName", spec.clientName.removeSuffix("_ANON"))
                            put("clientVersion", spec.clientVersion)
                            spec.deviceMake?.let { put("deviceMake", it) }
                            spec.deviceModel?.let { put("deviceModel", it) }
                            spec.osName?.let { put("osName", it) }
                            spec.osVersion?.let { put("osVersion", it) }
                            spec.androidSdkVersion?.let { put("androidSdkVersion", it) }
                            if (!visitorData.isNullOrBlank()) {
                                put("visitorData", visitorData)
                            }
                            put("hl", "en")
                            put("gl", "US")
                            spec.utcOffsetMinutes?.let { put("utcOffsetMinutes", it) }
                        })
                        spec.embedUrl?.let { embed ->
                            put("thirdParty", JSONObject().put("embedUrl", embed))
                        }
                        if (useAuth && manager != null && manager.isSignedIn()) {
                            put("user", JSONObject().apply {
                                put("lockedSafetyMode", false)
                                if (includeDelegation) {
                                    manager.getOnBehalfOfUser()?.let { put("onBehalfOfUser", it) }
                                }
                            })
                        }
                    })
                }

                val builder = Request.Builder()
                    .url(spec.url)
                    .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .header("User-Agent", spec.userAgent)
                    .header("Accept", "*/*")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Origin", spec.origin)
                    .header("Referer", spec.referer)
                    .header("Content-Type", "application/json")
                spec.clientId?.let { builder.header("X-YouTube-Client-Name", it) }
                builder.header("X-YouTube-Client-Version", spec.clientVersion)

                if (useAuth && manager != null && manager.isSignedIn()) {
                    builder.header("X-Youtube-Bootstrap-Logged-In", "true")
                    manager.buildPlayerAuthHeaders(
                        origin = spec.origin,
                        timestampSeconds = clockMs() / 1000L,
                        includePageId = includeDelegation,
                        fallbackVisitorData = visitorData,
                        ensureConsentCookie = true
                    ).forEach { (key, value) ->
                        if (!key.equals("Origin", ignoreCase = true) &&
                            !key.equals("Referer", ignoreCase = true)
                        ) {
                            builder.header(key, value)
                        }
                    }
                } else {
                    if (!visitorData.isNullOrBlank()) {
                        builder.header("X-Goog-Visitor-Id", visitorData)
                    }
                    if (!anonCookies.isNullOrBlank()) {
                        builder.header("Cookie", anonCookies)
                    }
                }

                httpClient.newCall(builder.build()).execute().use { response ->
                    val code = response.code
                    val body = response.body?.string()
                    if (!response.isSuccessful || body.isNullOrBlank()) {
                        safeLog("${spec.clientName} player HTTP $code auth=$useAuth video=$videoId")
                        if ((code == 400 || code == 403) && includeDelegation) {
                            return@use // retry without delegation
                        }
                        return null
                    }
                    val cleaned = if (body.startsWith(")]}'")) body.substringAfter('\n') else body
                    val json = runCatching { JSONObject(cleaned) }.getOrNull() ?: return null
                    val playability = json.optJSONObject("playabilityStatus")
                    val status = playability?.optString("status")?.trim().orEmpty()
                    val reason = playability?.optString("reason")?.trim().orEmpty()
                    safeLog("${spec.clientName} player status='$status' reason='$reason' auth=$useAuth video=$videoId")

                    if (isPlayabilityBlocked(status)) {
                        if (includeDelegation) {
                            return@use // retry without delegation
                        }
                        return null
                    }

                    val streamingData = json.optJSONObject("streamingData") ?: return null
                    val candidate = pickBestAudioCandidate(streamingData) ?: return null
                    val resolvedUrl = if (candidate.signature.isNotBlank() || spec.decipherNChallenge) {
                        scriptResolver.transformStreamUrl(
                            url = candidate.url,
                            sp = candidate.sp,
                            signature = candidate.signature
                        )
                    } else {
                        candidate.url
                    }
                    if (resolvedUrl.isNullOrBlank()) {
                        safeLog("${spec.clientName} unable to decipher stream URL for video=$videoId")
                        return null
                    }

                    val downloadCookie = when {
                        useAuth && manager != null && manager.isSignedIn() && spec.sendOriginOnDownload ->
                            manager.getCookieWithConsent()
                        !useAuth && spec.sendOriginOnDownload ->
                            anonCookies?.takeIf { it.isNotBlank() }
                        else -> null
                    }
                    return ResolvedAudioStream(
                        url = resolvedUrl,
                        clientName = spec.clientName,
                        userAgent = spec.userAgent,
                        origin = if (spec.sendOriginOnDownload) spec.origin else null,
                        referer = if (spec.sendOriginOnDownload) spec.referer else null,
                        cookie = downloadCookie,
                        contentLength = candidate.contentLength,
                        allowChunkedRangeRetry = spec.allowChunkedRangeRetry
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                safeLog("${spec.clientName} player resolution error (auth=$useAuth)", e)
                if (!includeDelegation) return null
            }
        }
        return null
    }

    suspend fun getOrDownloadAudioFile(videoIdOrId: String, context: Context? = null): File? = withContext(Dispatchers.IO) {
        context?.let { attachCacheContext(it) }
        val rawVideoId = extractCleanVideoId(videoIdOrId) ?: return@withContext null
        val cleanVideoId = resolveCanonicalAtvVideoId(rawVideoId)

        val cacheDir = (audioCacheDirOverride ?: context?.let { File(it.cacheDir, "audio_cache") })
            ?.apply { mkdirs() }
            ?: return@withContext null
        val targetFile = File(cacheDir, "$cleanVideoId.m4a")

        // 1. Instant Cache: Check if already downloaded to disk (> 50 KB)
        if (targetFile.exists() && targetFile.length() > 50_000) {
            safeLog("Using cached audio track: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
            return@withContext targetFile
        }

        // 2 & 3. Resolve direct audio stream URL with fallback retry across all candidate clients
        val failedClients = mutableSetOf<String>()
        repeat(10) {
            val resolved = streamCache[cleanVideoId]
                ?.takeUnless { it.clientName in failedClients }
                ?: resolveDirectAudioStream(cleanVideoId, failedClients)?.also {
                    streamCache[cleanVideoId] = it
                }

            if (resolved == null) {
                safeLog("Failed to resolve stream URL for '$cleanVideoId' (tried=$failedClients)")
                return@withContext null
            }

            val tempFile = File(cacheDir, "${cleanVideoId}_${clockMs()}.part")
            try {
                val host = runCatching { URI(resolved.url).host }.getOrNull()?.takeIf { it.isNotBlank() }
                    ?: "www.youtube.com"
                val downloadUrl = buildStreamDownloadUrl(resolved.url, resolved.contentLength)

                safeLog("Downloading audio track (${resolved.clientName}) from $host to ${tempFile.name}...")
                var downloaded = downloadUrlToFile(downloadUrl, resolved, tempFile)

                if (downloaded == -403L && resolved.allowChunkedRangeRetry && !resolved.url.contains("range=")) {
                    if (tempFile.exists()) tempFile.delete()
                    safeLog("Retrying ${resolved.clientName} with chunked range download for '$cleanVideoId'")
                    downloaded = downloadChunkedToFile(resolved, tempFile)
                }

                if (downloaded > 50_000L) {
                    if (targetFile.exists()) {
                        targetFile.delete()
                    }
                    tempFile.renameTo(targetFile)
                    safeLog("Cached YouTube Music audio for '$cleanVideoId' at '${targetFile.absolutePath}' (${targetFile.length()} bytes)")
                    return@withContext targetFile
                }

                if (downloaded > 0L) {
                    safeLog("Downloaded file too small (${resolved.clientName}): $downloaded bytes")
                }
                streamCache.remove(cleanVideoId)
                failedClients.add(resolved.clientName)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                safeLog("Error downloading stream (${resolved.clientName}) for '$cleanVideoId'", e)
                streamCache.remove(cleanVideoId)
                failedClients.add(resolved.clientName)
            } finally {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }
        }

        null
    }

    private fun buildStreamDownloadRequest(url: String, resolved: ResolvedAudioStream): Request {
        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", resolved.userAgent)
            .header("Accept", "*/*")
        if (!resolved.origin.isNullOrBlank()) {
            requestBuilder.header("Origin", resolved.origin)
        }
        if (!resolved.referer.isNullOrBlank()) {
            requestBuilder.header("Referer", resolved.referer)
        }
        if (!resolved.cookie.isNullOrBlank()) {
            requestBuilder.header("Cookie", resolved.cookie)
        }
        return requestBuilder.build()
    }

    private fun downloadUrlToFile(
        downloadUrl: String,
        resolved: ResolvedAudioStream,
        tempFile: File
    ): Long {
        httpClient.newCall(buildStreamDownloadRequest(downloadUrl, resolved)).execute().use { response ->
            if (!response.isSuccessful && response.code != 206) {
                safeLog("Download failed (${resolved.clientName}) with HTTP ${response.code}: ${response.message}")
                return -response.code.toLong()
            }
            val body = response.body ?: return -1L
            FileOutputStream(tempFile).use { out ->
                body.byteStream().use { inp ->
                    inp.copyTo(out)
                }
            }
            return tempFile.length()
        }
    }

    private fun downloadChunkedToFile(
        resolved: ResolvedAudioStream,
        tempFile: File
    ): Long {
        val totalExpected = resolved.contentLength?.takeIf { it > 0L } ?: return -1L
        val chunkSize = 512_000L
        var offset = 0L
        val sep = if (resolved.url.contains("?")) "&" else "?"

        FileOutputStream(tempFile).use { out ->
            while (offset < totalExpected) {
                val end = minOf(offset + chunkSize - 1L, totalExpected - 1L)
                val chunkUrl = "${resolved.url}${sep}range=$offset-$end&hl=en"
                httpClient.newCall(buildStreamDownloadRequest(chunkUrl, resolved)).execute().use { response ->
                    if (!response.isSuccessful && response.code != 206) {
                        safeLog("Chunk download failed (${resolved.clientName} $offset-$end) HTTP ${response.code}")
                        return -response.code.toLong()
                    }
                    val body = response.body ?: return -1L
                    val written = body.byteStream().use { inp -> inp.copyTo(out) }
                    if (written <= 0L) return tempFile.length()
                    offset += written
                }
            }
        }
        return tempFile.length()
    }

    private fun buildStreamDownloadUrl(streamUrl: String, contentLength: Long? = null): String {
        if (streamUrl.contains("range=")) return streamUrl
        val clen = contentLength?.takeIf { it > 0L }
            ?: Regex("""[?&]clen=(\d+)""").find(streamUrl)?.groupValues?.getOrNull(1)?.toLongOrNull()
        val rangeEnd = if (clen != null && clen > 0L) clen - 1L else 9_898_988L
        val sep = if (streamUrl.contains("?")) "&" else "?"
        return "${streamUrl}${sep}range=0-${rangeEnd}&hl=en"
    }

    private suspend fun getVisitorDataAndCookies(forceRefresh: Boolean = false): Pair<String, String>? = withContext(Dispatchers.IO) {
        val now = clockMs()
        val cached = cachedVisitorData
        if (!forceRefresh && cached != null && now < visitorDataExpiry) {
            return@withContext Pair(cached, cachedCookies ?: "")
        }

        visitorMutex.withLock {
            val nowInner = clockMs()
            val cachedInner = cachedVisitorData
            if (!forceRefresh && cachedInner != null && nowInner < visitorDataExpiry) {
                return@withLock Pair(cachedInner, cachedCookies ?: "")
            }

            try {
                val request = Request.Builder()
                    .url(swJsUrl)
                    .header("Accept", "application/json")
                    .header("User-Agent", "com.google.android.youtube/20.10.38, (Linux; U; ANDROID 11), gzip")
                    .header("Origin", "https://www.youtube.com")
                    .header("Cookie", "SOCS=CAISEwgDEgk4MTM4MzYzNTIaAmVuIAEaBgiApPzGBg")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withLock null

                    val rawCookies = response.headers("Set-Cookie")
                    val cookieList = mutableListOf("SOCS=CAISEwgDEgk4MTM4MzYzNTIaAmVuIAEaBgiApPzGBg")
                    for (cookieHeader in rawCookies) {
                        val cookieVal = cookieHeader.substringBefore(";").trim()
                        if (cookieVal.isNotBlank()) cookieList.add(cookieVal)
                    }
                    val formattedCookies = cookieList.joinToString("; ")

                    var text = response.body?.string() ?: return@withLock null
                    val jsonStart = text.indexOf("[")
                    if (jsonStart != -1) {
                        text = text.substring(jsonStart).trim()
                    }

                    val json = JSONArray(text)
                    val visitor = findVisitorDataRecursive(json)
                    if (visitor != null) {
                        safeLog("sw.js_data visitorData resolved: ${visitor.take(25)}...")
                        cachedVisitorData = visitor
                        cachedCookies = formattedCookies
                        visitorDataExpiry = clockMs() + (6 * 3600 * 1000L)
                        return@withLock Pair(visitor, formattedCookies)
                    } else {
                        safeLog("sw.js_data did not contain visitorData")
                    }
                }
            } catch (e: Exception) {
                safeLog("Failed to resolve visitorData from sw.js_data", e)
            }
            null
        }
    }

    private fun findVisitorDataRecursive(obj: Any): String? {
        when (obj) {
            is String -> {
                if (obj.length > 50 && obj.startsWith("Cgt")) {
                    return obj
                }
            }
            is JSONArray -> {
                for (i in 0 until obj.length()) {
                    val found = findVisitorDataRecursive(obj.get(i))
                    if (found != null) return found
                }
            }
            is JSONObject -> {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val found = findVisitorDataRecursive(obj.get(key))
                    if (found != null) return found
                }
            }
        }
        return null
    }

    private fun pickBestAudioCandidate(streamingData: JSONObject): AudioFormatCandidate? {
        streamingData.optJSONArray("adaptiveFormats")?.let { adaptiveFormats ->
            pickBestAudioCandidateFromArray(adaptiveFormats, requireAudioMime = true)?.let { return it }
        }
        streamingData.optJSONArray("formats")?.let { formats ->
            pickBestAudioCandidateFromArray(formats, requireAudioMime = false)?.let { return it }
        }
        return null
    }

    private fun pickBestAudioCandidateFromArray(formats: JSONArray, requireAudioMime: Boolean): AudioFormatCandidate? {
        var bestMp4: AudioFormatCandidate? = null
        var bestMp4Bitrate = -1
        var fallback: AudioFormatCandidate? = null
        var fallbackBitrate = -1

        for (i in 0 until formats.length()) {
            val format = formats.optJSONObject(i) ?: continue
            val candidate = parseAudioFormatCandidate(format) ?: continue

            val mimeType = format.optString("mimeType").trim()
            val hasAudio = if (requireAudioMime) {
                mimeType.startsWith("audio/")
            } else {
                mimeType.startsWith("audio/") ||
                    format.optString("audioQuality").isNotBlank() ||
                    format.optInt("audioChannels", 0) > 0
            }
            if (!hasAudio) continue

            val bitrate = format.optInt("bitrate", 0)
            if (mimeType.startsWith("audio/mp4") || mimeType.startsWith("video/mp4")) {
                if (bitrate > bestMp4Bitrate) {
                    bestMp4Bitrate = bitrate
                    bestMp4 = candidate
                }
            } else if (bitrate > fallbackBitrate) {
                fallbackBitrate = bitrate
                fallback = candidate
            }
        }
        return bestMp4 ?: fallback
    }

    private fun parseAudioFormatCandidate(format: JSONObject): AudioFormatCandidate? {
        val contentLength = format.optString("contentLength").trim().toLongOrNull()
        val directUrl = format.optString("url").trim()
        if (directUrl.isNotBlank()) {
            return AudioFormatCandidate(url = directUrl, contentLength = contentLength)
        }

        val cipherStr = format.optString("signatureCipher").trim().ifBlank {
            format.optString("cipher").trim()
        }
        if (cipherStr.isBlank()) return null

        var parsedUrl = ""
        var parsedSp = "signature"
        var parsedSig = ""
        for (pair in cipherStr.split('&')) {
            val eqIdx = pair.indexOf('=')
            if (eqIdx <= 0) continue
            val key = runCatching { java.net.URLDecoder.decode(pair.substring(0, eqIdx), "UTF-8") }.getOrDefault("")
            val value = runCatching { java.net.URLDecoder.decode(pair.substring(eqIdx + 1), "UTF-8") }.getOrDefault("")
            when (key) {
                "url" -> parsedUrl = value.trim()
                "sp" -> if (value.isNotBlank()) parsedSp = value.trim()
                "s" -> parsedSig = value.trim()
            }
        }
        if (parsedUrl.isBlank()) return null
        return AudioFormatCandidate(
            url = parsedUrl,
            sp = parsedSp,
            signature = parsedSig,
            contentLength = contentLength
        )
    }

    suspend fun resolveCanonicalAtvVideoId(videoId: String): String = withContext(Dispatchers.IO) {
        val cleanId = videoId.trim()
        if (!VIDEO_ID_REGEX.matches(cleanId)) return@withContext cleanId

        ensureAtvCounterpartCacheLoaded()
        atvCounterpartCache[cleanId]?.takeIf { it.isNotBlank() }?.let { return@withContext it }

        try {
            fun buildAtvPayload(includeUser: Boolean) = JSONObject().apply {
                put("context", buildWebRemixContextJson(includeUser = includeUser))
                put("videoId", cleanId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }
            var postResult = executeInnerTubePostWithStatus(
                url = ytmPlayerUrl,
                payload = buildAtvPayload(includeUser = true),
                includeAuthHeaders = true,
                includePageId = true
            )
            val manager = authManager
            if (!postResult.isSuccessful &&
                manager?.isSignedIn() == true &&
                (manager.getDelegatedPageId() != null || manager.getOnBehalfOfUser() != null)
            ) {
                postResult = executeInnerTubePostWithStatus(
                    url = ytmPlayerUrl,
                    payload = buildAtvPayload(includeUser = false),
                    includeAuthHeaders = true,
                    includePageId = false
                )
            }
            val playerJson = postResult.json ?: return@withContext cleanId
            val playabilityStatus = playerJson.optJSONObject("playabilityStatus")?.optString("status")?.trim().orEmpty()
            val videoDetails = playerJson.optJSONObject("videoDetails")
            if (videoDetails == null) {
                if (isPlayabilityBlocked(playabilityStatus)) {
                    safeLog("ATV metadata lookup playability='$playabilityStatus' for '$cleanId'")
                }
                return@withContext cleanId
            }
            val musicVideoType = videoDetails.optString("musicVideoType", "").trim()

            if (isPureAudioMusicVideoType(musicVideoType)) {
                if (!isPlayabilityBlocked(playabilityStatus)) {
                    atvCounterpartCache[cleanId] = cleanId
                    persistAtvCounterpartCacheSafe()
                }
                return@withContext cleanId
            }

            if (playabilityStatus.equals("ERROR", ignoreCase = true) ||
                playabilityStatus.equals("UNPLAYABLE", ignoreCase = true)
            ) {
                safeLog("Skipping ATV promotion for '$cleanId' due to playability='$playabilityStatus'")
                return@withContext cleanId
            }

            val rawTitle = videoDetails.optString("title", "").trim()
            val rawAuthor = videoDetails.optString("author", "").trim()
            if (rawTitle.isBlank()) return@withContext cleanId

            val isUgc = musicVideoType.equals("MUSIC_VIDEO_TYPE_UGC", ignoreCase = true)
            var cleanTitle = cleanTitleForAtvSearch(rawTitle)
            var cleanAuthor = cleanAuthorForAtvSearch(rawAuthor)

            val dashIndex = cleanTitle.indexOf(" - ")
            if (isUgc && dashIndex > 0 && dashIndex < cleanTitle.length - 3) {
                val parsedArtist = cleanTitle.substring(0, dashIndex).trim()
                val parsedSong = cleanTitle.substring(dashIndex + 3).trim()
                if (parsedArtist.length >= 2 && parsedSong.length >= 2) {
                    cleanAuthor = cleanAuthorForAtvSearch(parsedArtist)
                    cleanTitle = cleanTitleForAtvSearch(parsedSong)
                }
            }

            val searchQuery = if (cleanAuthor.isNotBlank()) "$cleanAuthor $cleanTitle" else cleanTitle
            if (searchQuery.isBlank()) return@withContext cleanId

            val candidates = executeInnerTubeSearch(searchQuery, SONGS_PARAM)
            val bestCandidate = selectBestAtvCandidate(
                candidates = candidates,
                targetTitle = cleanTitle,
                targetAuthor = cleanAuthor,
                excludeVideoId = cleanId,
                requireArtistMatch = isUgc
            )
            val resolvedId = bestCandidate?.id?.removePrefix("ytm_")?.trim()?.takeIf { it.isNotBlank() }
            if (resolvedId != null && resolvedId != cleanId) {
                atvCounterpartCache[cleanId] = resolvedId
                atvCounterpartCache[resolvedId] = resolvedId
                persistAtvCounterpartCacheSafe()
                streamCache.remove(cleanId)
                deleteStaleAudioCacheFile(cleanId)
                safeLog("Resolved canonical ATV counterpart '$resolvedId' for video '$cleanId' ($musicVideoType)")
                return@withContext resolvedId
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            safeLog("Failed to resolve canonical ATV videoId for '$cleanId'", e)
        }

        cleanId
    }

    private fun deleteStaleAudioCacheFile(obsoleteVideoId: String) {
        val dir = audioCacheDir ?: return
        val staleFile = File(dir, "$obsoleteVideoId.m4a")
        if (staleFile.exists()) {
            runCatching {
                if (staleFile.delete()) {
                    safeLog("Deleted stale cached video audio '${staleFile.name}'")
                }
            }
        }
    }

    internal fun cleanTitleForAtvSearch(raw: String): String {
        if (raw.isBlank()) return ""
        return raw
            .replace(VIDEO_TITLE_TAG_REGEX, " ")
            .replace(
                Regex(
                    """\s*[-|]\s*(?:official\s*(?:music\s*)?video|official\s*lyric\s*video|video\s*oficial|official\s*audio).*$""",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )
            .replace(WHITESPACE_REGEX, " ")
            .trim()
    }

    internal fun cleanAuthorForAtvSearch(raw: String): String {
        if (raw.isBlank()) return ""
        return raw
            .replace(Regex("""\s*-\s*Topic$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""VEVO$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+Official$""", RegexOption.IGNORE_CASE), "")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
    }

    internal fun normalizeMatchText(raw: String): String {
        if (raw.isBlank()) return ""
        val decomposed = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFD)
        val withoutAccents = decomposed.replace(Regex("""\p{M}+"""), "")
        return withoutAccents
            .lowercase(java.util.Locale.ROOT)
            .replace(NON_ALPHANUMERIC_SPACE_REGEX, " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
    }

    internal fun selectBestAtvCandidate(
        candidates: List<TrackItem>,
        targetTitle: String,
        targetAuthor: String,
        excludeVideoId: String = "",
        requireArtistMatch: Boolean = false
    ): TrackItem? {
        val normTargetTitle = normalizeMatchText(cleanTitleForAtvSearch(targetTitle))
        if (normTargetTitle.isBlank()) return null
        val normTargetAuthor = normalizeMatchText(cleanAuthorForAtvSearch(targetAuthor))
        val compactTargetAuthor = normTargetAuthor.replace(" ", "")

        var bestCandidate: TrackItem? = null
        var bestScore = 0

        for (candidate in candidates) {
            val candVideoId = candidate.id.removePrefix("ytm_").trim()
            if (candVideoId.isBlank() || candVideoId == excludeVideoId) continue

            val normCandTitle = normalizeMatchText(cleanTitleForAtvSearch(candidate.title))
            if (normCandTitle.isBlank()) continue

            var score = when {
                normCandTitle == normTargetTitle -> 60
                normCandTitle.contains(normTargetTitle) || normTargetTitle.contains(normCandTitle) -> 40
                else -> -1
            }
            if (score < 0) continue

            val normCandArtist = normalizeMatchText(cleanAuthorForAtvSearch(candidate.artist))
            val compactCandArtist = normCandArtist.replace(" ", "")
            var matchedArtist = false

            if (normTargetAuthor.isNotBlank() && normCandArtist.isNotBlank()) {
                when {
                    normCandArtist == normTargetAuthor -> {
                        score += 40
                        matchedArtist = true
                    }
                    normCandArtist.contains(normTargetAuthor) || normTargetAuthor.contains(normCandArtist) -> {
                        score += 25
                        matchedArtist = true
                    }
                    compactTargetAuthor.length >= 4 &&
                        (compactCandArtist == compactTargetAuthor ||
                            compactCandArtist.contains(compactTargetAuthor) ||
                            compactTargetAuthor.contains(compactCandArtist)) -> {
                        score += 25
                        matchedArtist = true
                    }
                }
            }

            if (requireArtistMatch && !matchedArtist) continue
            if (score >= 60 && score > bestScore) {
                bestScore = score
                bestCandidate = candidate
            }
        }
        return bestCandidate
    }

    suspend fun resolveAudioStreamUrl(videoIdOrId: String): String? = withContext(Dispatchers.IO) {
        val rawVideoId = extractCleanVideoId(videoIdOrId) ?: return@withContext null
        val cleanVideoId = resolveCanonicalAtvVideoId(rawVideoId)

        streamCache[cleanVideoId]?.let { return@withContext it.url }

        val chosen = resolveDirectAudioStream(cleanVideoId)
        if (chosen != null && chosen.url.isNotBlank()) {
            streamCache[cleanVideoId] = chosen
        }
        chosen?.url
    }

    private data class ResolvedVideoStreamBundle(
        val primaryUrl: String,
        val fallbackUrls: List<String>,
        val httpHeaders: Map<String, String>
    )

    private data class VideoFormatCandidate(
        val candidate: AudioFormatCandidate,
        val isHls: Boolean,
        val score: Int
    )

    fun invalidateMusicVideoCache(videoId: String? = null, track: TrackItem? = null) {
        if (!videoId.isNullOrBlank()) {
            videoStreamCache.remove(videoId.trim())
        }
        if (track != null) {
            val cleanTitle = cleanTitleForAtvSearch(track.title)
            val cleanArtist = cleanAuthorForAtvSearch(track.artist)
            val rawId = track.id.removePrefix("ytm_video_").removePrefix("ytm_").trim()
            val lookupKey = "${cleanTitle.lowercase(Locale.ROOT)}|${cleanArtist.lowercase(Locale.ROOT)}|$rawId"
            val oldOmvId = omvCounterpartCache.remove(lookupKey)
            if (!oldOmvId.isNullOrBlank()) {
                videoStreamCache.remove(oldOmvId)
            }
            if (rawId.isNotBlank()) {
                videoStreamCache.remove(rawId)
            }
        }
    }

    /**
     * Resolves an official YouTube Music video counterpart (OMV) and its verified playable video stream URLs
     * (with HLS / adaptive H.264 / muxed fallbacks) for the Audio / Video Switcher in Now Playing & Tablet Fullscreen.
     */
    suspend fun resolveMusicVideoStream(
        track: TrackItem,
        forceRefresh: Boolean = false
    ): ResolvedMusicVideo? = withContext(Dispatchers.IO) {
        val cleanTitle = cleanTitleForAtvSearch(track.title)
        val cleanArtist = cleanAuthorForAtvSearch(track.artist)
        if (cleanTitle.isBlank()) return@withContext null

        val rawId = track.id.removePrefix("ytm_video_").removePrefix("ytm_").trim()
        val lookupKey = "${cleanTitle.lowercase(Locale.ROOT)}|${cleanArtist.lowercase(Locale.ROOT)}|$rawId"
        val now = clockMs()

        if (!forceRefresh) {
            val cachedOmvId = omvCounterpartCache[lookupKey]
            if (!cachedOmvId.isNullOrBlank() && cachedOmvId != "__NONE__") {
                val cachedStream = videoStreamCache[cachedOmvId]
                if (cachedStream != null && cachedStream.expiresAtMs > now) {
                    return@withContext cachedStream
                }
            }
        } else {
            invalidateMusicVideoCache(videoId = rawId, track = track)
        }

        // 1. Build prioritized OMV candidate videoIds (try top candidates if one is blocked/unavailable)
        val candidateVideoIds = LinkedHashSet<String>()

        if (track.id.startsWith("ytm_video_") && VIDEO_ID_REGEX.matches(rawId)) {
            candidateVideoIds.add(rawId)
        }

        if (!forceRefresh) {
            omvCounterpartCache[lookupKey]?.takeIf { VIDEO_ID_REGEX.matches(it) }?.let {
                candidateVideoIds.add(it)
            }
        }

        if (VIDEO_ID_REGEX.matches(rawId)) {
            ensureAtvCounterpartCacheLoaded()
            // Reverse lookup in atvCounterpartCache: omv -> atv
            atvCounterpartCache.entries.firstOrNull { (k, v) ->
                k != v && v == rawId && VIDEO_ID_REGEX.matches(k)
            }?.key?.let { candidateVideoIds.add(it) }

            // Native YouTube Music /next counterpart lookup (OMV paired directly with ATV track)
            findNativeVideoCounterpartId(rawId)?.let { candidateVideoIds.add(it) }
        }

        // Ranked Videos search (handles songs whose /next panel omits counterpart, e.g. Melanie Martinez - VOID)
        searchOfficialMusicVideoCandidates(cleanTitle, cleanArtist).forEach { id ->
            if (candidateVideoIds.size < 4) {
                candidateVideoIds.add(id)
            }
        }

        if (candidateVideoIds.isEmpty()) {
            safeLog("No official music video candidates found for '${track.artist} - ${track.title}'")
            return@withContext null
        }

        for (targetVideoId in candidateVideoIds.take(3)) {
            if (!forceRefresh) {
                val cachedStream = videoStreamCache[targetVideoId]
                if (cachedStream != null && cachedStream.expiresAtMs > now) {
                    omvCounterpartCache[lookupKey] = targetVideoId
                    return@withContext cachedStream
                }
            }

            val bundle = resolveVerifiedVideoStreams(targetVideoId) ?: continue
            val resolved = ResolvedMusicVideo(
                videoId = targetVideoId,
                title = track.title,
                artist = track.artist,
                streamUrl = bundle.primaryUrl,
                thumbnailUrl = "https://i.ytimg.com/vi/$targetVideoId/hqdefault.jpg",
                durationMs = track.durationMs,
                fallbackStreamUrls = bundle.fallbackUrls,
                httpHeaders = bundle.httpHeaders,
                expiresAtMs = clockMs() + 3L * 60L * 60L * 1000L
            )
            omvCounterpartCache[lookupKey] = targetVideoId
            videoStreamCache[targetVideoId] = resolved
            safeLog(
                "Resolved music video '$targetVideoId' for '${track.artist} - ${track.title}' " +
                    "(fallbacks=${bundle.fallbackUrls.size})"
            )
            return@withContext resolved
        }

        safeLog("Unable to resolve playable video stream for '${track.artist} - ${track.title}' (candidates=$candidateVideoIds)")
        null
    }

    private fun findNativeVideoCounterpartId(atvVideoId: String): String? {
        if (!VIDEO_ID_REGEX.matches(atvVideoId)) return null
        return try {
            val payload = JSONObject().apply {
                put("context", buildWebRemixContextJson())
                put("videoId", atvVideoId)
                put("enablePersistentPlaylistPanel", true)
                put("isAudioOnly", true)
            }
            val request = buildWebRemixRequestBuilder(nextUrl, payload).build()
            val body = httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string() ?: return null
            }
            val cleaned = if (body.startsWith(")]}'")) body.substringAfter('\n') else body
            val json = runCatching { JSONObject(cleaned) }.getOrNull() ?: return null
            findCounterpartVideoIdRecursive(json, excludeVideoId = atvVideoId)
        } catch (_: Exception) {
            null
        }
    }

    private fun findCounterpartVideoIdRecursive(node: Any, excludeVideoId: String): String? {
        when (node) {
            is JSONObject -> {
                if (node.has("counterpartRenderer")) {
                    val cp = node.optJSONObject("counterpartRenderer")
                        ?.optJSONObject("playlistPanelVideoRenderer")
                    val vid = cp?.optString("videoId")?.trim().orEmpty()
                    if (VIDEO_ID_REGEX.matches(vid) && vid != excludeVideoId) {
                        return vid
                    }
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val found = findCounterpartVideoIdRecursive(node.get(keys.next()), excludeVideoId)
                    if (found != null) return found
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    val found = findCounterpartVideoIdRecursive(node.get(i), excludeVideoId)
                    if (found != null) return found
                }
            }
        }
        return null
    }

    private fun searchOfficialMusicVideoCandidates(cleanTitle: String, cleanArtist: String): List<String> {
        val normTitle = normalizeMatchText(cleanTitle)
        val normArtist = normalizeMatchText(cleanArtist)
        val compactArtist = normArtist.replace(" ", "")
        val hasArtist = cleanArtist.isNotBlank() &&
            !cleanArtist.equals("Unknown Artist", ignoreCase = true) &&
            !cleanArtist.equals("YouTube Artist", ignoreCase = true)

        val queries = buildList {
            if (hasArtist) {
                add("$cleanArtist $cleanTitle official music video")
                add("$cleanArtist $cleanTitle")
            } else {
                add("$cleanTitle official music video")
            }
        }

        val collected = mutableListOf<TrackItem>()
        val seenIds = mutableSetOf<String>()

        for (query in queries) {
            try {
                val payload = JSONObject().apply {
                    put("context", buildWebRemixContextJson())
                    put("query", query)
                    put("params", VIDEOS_PARAM)
                }
                val request = buildWebRemixRequestBuilder(searchUrl, payload).build()
                val body = httpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    resp.body?.string()
                } ?: continue
                val json = runCatching { JSONObject(body) }.getOrNull() ?: continue
                extractVideoItemsRecursive(json, cleanArtist, collected, seenIds)
                if (collected.size >= 4) break
            } catch (_: Exception) {
            }
        }

        if (collected.isEmpty()) return emptyList()

        val negativeKeywords = listOf(
            "lyric", "lyrics", "letra", "sub espanol", "sub español", "subtitul",
            "slowed", "reverb", "sped up", "nightcore", "cover", "karaoke",
            "instrumental", "reaction", "teaser", "trailer", "shorts", "1 hour",
            "mashup", "fan made", "fanmade", "live"
        ).filterNot { kw -> normTitle.contains(kw) }

        val ranked = collected.mapIndexedNotNull { idx, cand ->
            val vid = cand.id.removePrefix("ytm_video_").removePrefix("ytm_").trim()
            if (!VIDEO_ID_REGEX.matches(vid)) return@mapIndexedNotNull null

            val rawTitleLower = cand.title.lowercase(Locale.ROOT)
            val normCandTitle = normalizeMatchText(cleanTitleForAtvSearch(cand.title))
            val normCandArtist = normalizeMatchText(cleanAuthorForAtvSearch(cand.artist))
            val compactCandArtist = normCandArtist.replace(" ", "")

            var score = (12 - idx.coerceAtMost(12)) * 2

            val titleMatches = when {
                normCandTitle == normTitle -> {
                    score += 70
                    true
                }
                normTitle.length >= 2 && (normCandTitle.contains(normTitle) || normTitle.contains(normCandTitle)) -> {
                    score += 45
                    true
                }
                else -> false
            }
            if (!titleMatches) return@mapIndexedNotNull null

            val artistMatches = if (normArtist.isNotBlank()) {
                when {
                    normCandArtist == normArtist -> {
                        score += 50
                        true
                    }
                    normCandArtist.contains(normArtist) || normArtist.contains(normCandArtist) -> {
                        score += 40
                        true
                    }
                    compactArtist.length >= 4 && (
                        compactCandArtist.contains(compactArtist) || compactArtist.contains(compactCandArtist)
                        ) -> {
                        score += 35
                        true
                    }
                    rawTitleLower.contains(normArtist) -> {
                        score += 20
                        true
                    }
                    else -> false
                }
            } else {
                true
            }

            if (rawTitleLower.contains("official music video") || rawTitleLower.contains("official video") || rawTitleLower.contains("video oficial")) {
                score += 30
            }
            if (cand.playCount >= 10_000_000L) {
                score += 30
            } else if (cand.playCount >= 1_000_000L) {
                score += 20
            } else if (cand.playCount >= 100_000L) {
                score += 10
            }

            for (bad in negativeKeywords) {
                if (rawTitleLower.contains(bad)) {
                    score -= 75
                }
            }
            if (rawTitleLower.contains("official audio") || rawTitleLower.contains("audio oficial")) {
                score -= 60
            }

            if (hasArtist && !artistMatches && cand.playCount < 500_000L) {
                score -= 40
            }

            vid to score
        }

        return ranked
            .sortedByDescending { it.second }
            .filter { it.second >= 25 }
            .map { it.first }
            .distinct()
            .take(3)
            .ifEmpty {
                collected.mapNotNull { cand ->
                    cand.id.removePrefix("ytm_video_").removePrefix("ytm_").trim()
                        .takeIf { VIDEO_ID_REGEX.matches(it) }
                }.take(2)
            }
    }

    private fun buildVideoStreamPlayerSpecs(isSignedIn: Boolean): List<Pair<PlayerClientSpec, Boolean>> {
        val base = buildStreamPlayerSpecs(isSignedIn)
        // Prioritize fast unauthenticated native video clients (VISIONOS -> IOS -> ANDROID -> ANDROID_VR)
        // because they return direct non-ciphered H.264 adaptive MP4 + HLS manifests without WebView JS deciphering,
        // then fall back to authenticated web clients for age-restricted music videos.
        val priorityOrder = listOf(
            "VISIONOS", "VISIONOS_ANON",
            "IOS", "IOS_ANON",
            "ANDROID", "ANDROID_ANON",
            "ANDROID_VR", "ANDROID_VR_ANON",
            "WEB_REMIX", "MWEB", "TVHTML5", "WEB",
            "WEB_REMIX_ANON", "MWEB_ANON"
        )
        return base.sortedBy { (spec, useAuth) ->
            val idx = priorityOrder.indexOf(spec.clientName)
            if (idx >= 0) idx else (100 + if (useAuth) 0 else 10)
        }
    }

    private suspend fun resolveVerifiedVideoStreams(videoId: String): ResolvedVideoStreamBundle? {
        val manager = authManager
        val isSignedIn = manager?.isSignedIn() == true
        val specs = buildVideoStreamPlayerSpecs(isSignedIn)
        val scriptResolver = activeScriptResolver()
        var anonPair = getVisitorDataAndCookies(forceRefresh = false)
        var didForceRefreshVisitor = false
        val userVisitor = manager?.getVisitorData()?.takeIf { it.isNotBlank() }

        for ((spec, useAuth) in specs) {
            var retryAfterVisitorRefresh = false
            while (true) {
                val anonVisitor = anonPair?.first?.takeIf { it.isNotBlank() } ?: cachedVisitorData
                val anonCookie = anonPair?.second?.takeIf { it.isNotBlank() } ?: YouTubeAuthManager.DEFAULT_SOCS_COOKIE
                val visitorData = if (useAuth) (userVisitor ?: cachedVisitorData) else anonVisitor

                val bundleOrBotBlock = fetchAndVerifyVideoStreamsForClient(
                    videoId = videoId,
                    spec = spec,
                    useAuth = useAuth,
                    visitorData = visitorData,
                    anonCookie = anonCookie,
                    scriptResolver = scriptResolver
                )

                if (bundleOrBotBlock.first != null) {
                    return bundleOrBotBlock.first
                }

                // If an anonymous client hit a bot check due to stale/missing visitorData, refresh once and retry
                val wasBotBlocked = bundleOrBotBlock.second
                if (!useAuth && wasBotBlocked && !didForceRefreshVisitor && !retryAfterVisitorRefresh) {
                    didForceRefreshVisitor = true
                    retryAfterVisitorRefresh = true
                    anonPair = getVisitorDataAndCookies(forceRefresh = true)
                    continue
                }
                break
            }
        }
        return null
    }

    private suspend fun fetchAndVerifyVideoStreamsForClient(
        videoId: String,
        spec: PlayerClientSpec,
        useAuth: Boolean,
        visitorData: String?,
        anonCookie: String?,
        scriptResolver: PlayerScriptResolver
    ): Pair<ResolvedVideoStreamBundle?, Boolean> {
        val manager = authManager
        val canDelegate = useAuth && manager != null &&
            (manager.getDelegatedPageId() != null || manager.getOnBehalfOfUser() != null)
        val attemptPageIds = if (canDelegate) listOf(true, false) else listOf(false)
        var sawBotBlock = false

        for (includeDelegation in attemptPageIds) {
            try {
                val sts = if (spec.sendPlaybackContext) scriptResolver.getSignatureTimestamp() else null
                val payload = JSONObject().apply {
                    put("videoId", videoId)
                    put("contentCheckOk", true)
                    put("racyCheckOk", true)
                    if (sts != null) {
                        put("playbackContext", JSONObject().apply {
                            put("contentPlaybackContext", JSONObject().apply {
                                put("signatureTimestamp", sts)
                                put("html5Preference", "HTML5_PREF_WANTS")
                            })
                        })
                    }
                    put("context", JSONObject().apply {
                        put("client", JSONObject().apply {
                            put("clientName", spec.clientName.removeSuffix("_ANON"))
                            put("clientVersion", spec.clientVersion)
                            spec.deviceMake?.let { put("deviceMake", it) }
                            spec.deviceModel?.let { put("deviceModel", it) }
                            spec.osName?.let { put("osName", it) }
                            spec.osVersion?.let { put("osVersion", it) }
                            spec.androidSdkVersion?.let { put("androidSdkVersion", it) }
                            if (!visitorData.isNullOrBlank()) put("visitorData", visitorData)
                            put("hl", "en")
                            put("gl", "US")
                            spec.utcOffsetMinutes?.let { put("utcOffsetMinutes", it) }
                        })
                        spec.embedUrl?.let { embed ->
                            put("thirdParty", JSONObject().put("embedUrl", embed))
                        }
                        if (useAuth && manager != null && manager.isSignedIn()) {
                            put("user", JSONObject().apply {
                                put("lockedSafetyMode", false)
                                if (includeDelegation) {
                                    manager.getOnBehalfOfUser()?.let { put("onBehalfOfUser", it) }
                                }
                            })
                        }
                    })
                }

                val builder = Request.Builder()
                    .url(spec.url)
                    .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .header("User-Agent", spec.userAgent)
                    .header("Accept", "*/*")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Origin", spec.origin)
                    .header("Referer", spec.referer)
                    .header("Content-Type", "application/json")
                spec.clientId?.let { builder.header("X-YouTube-Client-Name", it) }
                builder.header("X-YouTube-Client-Version", spec.clientVersion)

                if (useAuth && manager != null && manager.isSignedIn()) {
                    builder.header("X-Youtube-Bootstrap-Logged-In", "true")
                    manager.buildPlayerAuthHeaders(
                        origin = spec.origin,
                        timestampSeconds = clockMs() / 1000L,
                        includePageId = includeDelegation,
                        fallbackVisitorData = visitorData,
                        ensureConsentCookie = true
                    ).forEach { (key, value) ->
                        if (!key.equals("Origin", ignoreCase = true) && !key.equals("Referer", ignoreCase = true)) {
                            builder.header(key, value)
                        }
                    }
                } else {
                    if (!visitorData.isNullOrBlank()) builder.header("X-Goog-Visitor-Id", visitorData)
                    if (!anonCookie.isNullOrBlank()) builder.header("Cookie", anonCookie)
                }

                val json = httpClient.newCall(builder.build()).execute().use { response ->
                    val code = response.code
                    val body = response.body?.string()
                    if (!response.isSuccessful || body.isNullOrBlank()) {
                        safeLog("Video ${spec.clientName} HTTP $code auth=$useAuth video=$videoId")
                        if ((code == 400 || code == 403) && includeDelegation) return@use null
                        return null to sawBotBlock
                    }
                    val cleaned = if (body.startsWith(")]}'")) body.substringAfter('\n') else body
                    runCatching { JSONObject(cleaned) }.getOrNull()
                } ?: continue

                val playability = json.optJSONObject("playabilityStatus")
                val status = playability?.optString("status")?.trim().orEmpty()
                val reason = playability?.optString("reason")?.trim().orEmpty()
                if (isPlayabilityBlocked(status)) {
                    safeLog("Video ${spec.clientName} status='$status' reason='$reason' video=$videoId")
                    if (reason.contains("bot", ignoreCase = true)) {
                        sawBotBlock = true
                    }
                    if (includeDelegation) continue
                    return null to sawBotBlock
                }

                val streamingData = json.optJSONObject("streamingData") ?: return null to sawBotBlock
                val rankedCandidates = pickVideoStreamCandidates(streamingData)
                if (rankedCandidates.isEmpty()) return null to sawBotBlock

                val playbackHeaders = LinkedHashMap<String, String>().apply {
                    put("User-Agent", spec.userAgent)
                    if (spec.sendOriginOnDownload) {
                        put("Origin", spec.origin)
                        put("Referer", spec.referer)
                        val cookie = when {
                            useAuth && manager != null && manager.isSignedIn() -> manager.getCookieWithConsent()
                            !useAuth -> anonCookie?.takeIf { it.isNotBlank() }
                            else -> null
                        }
                        if (!cookie.isNullOrBlank()) {
                            put("Cookie", cookie)
                        }
                    }
                }

                val verifiedUrls = mutableListOf<String>()
                for (item in rankedCandidates.take(6)) {
                    val rawUrl = item.candidate.url
                    val needsTransform = !item.isHls && (
                        item.candidate.signature.isNotBlank() ||
                            (spec.decipherNChallenge && rawUrl.contains("n="))
                        )
                    val resolvedUrl = if (needsTransform) {
                        scriptResolver.transformStreamUrl(
                            url = rawUrl,
                            sp = item.candidate.sp,
                            signature = item.candidate.signature
                        )
                    } else {
                        rawUrl
                    }
                    if (resolvedUrl.isNullOrBlank()) continue
                    if (verifiedUrls.contains(resolvedUrl)) continue

                    if (verifyVideoStreamReachable(resolvedUrl, playbackHeaders, isHls = item.isHls)) {
                        verifiedUrls.add(resolvedUrl)
                        if (verifiedUrls.size >= 3) break
                    }
                }

                if (verifiedUrls.isNotEmpty()) {
                    return ResolvedVideoStreamBundle(
                        primaryUrl = verifiedUrls.first(),
                        fallbackUrls = verifiedUrls.drop(1),
                        httpHeaders = playbackHeaders
                    ) to false
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                safeLog("Video ${spec.clientName} resolution error for '$videoId'", e)
                if (!includeDelegation) return null to sawBotBlock
            }
        }
        return null to sawBotBlock
    }

    private fun verifyVideoStreamReachable(
        streamUrl: String,
        headers: Map<String, String>,
        isHls: Boolean
    ): Boolean {
        return try {
            val reqBuilder = Request.Builder()
                .url(streamUrl)
                .header("Accept", "*/*")
            headers.forEach { (k, v) -> reqBuilder.header(k, v) }
            if (!isHls) {
                reqBuilder.header("Range", "bytes=0-2047")
            }
            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.code != 200 && resp.code != 206) {
                    safeLog("Video probe rejected HTTP ${resp.code} (isHls=$isHls)")
                    return false
                }
                val ct = resp.header("Content-Type").orEmpty().lowercase(Locale.ROOT)
                if (ct.contains("text/html")) return false
                if (isHls) {
                    val preview = resp.body?.string()?.take(64).orEmpty()
                    ct.contains("mpegurl") || preview.contains("#EXTM3U")
                } else {
                    ct.startsWith("video/") || ct.contains("octet-stream") || ct.contains("mp4") || ct.isBlank()
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Ranks playable video streams with CinemaScope / ultrawide support (e.g. 1280x544, 854x362, 640x272
     * for 2.35:1 videos like "VOID" by Melanie Martinez).
     *
     * Because the video overlay plays muted alongside AudioPlayerService, adaptive H.264 (avc1)
     * video-only MP4 streams and HLS manifests are strongly preferred over AV1 (av01) and over
     * exact-height filters that reject widescreen videos.
     */
    private fun pickVideoStreamCandidates(streamingData: JSONObject): List<VideoFormatCandidate> {
        val candidates = mutableListOf<VideoFormatCandidate>()

        streamingData.optJSONArray("adaptiveFormats")?.let { adaptive ->
            for (i in 0 until adaptive.length()) {
                val fmt = adaptive.optJSONObject(i) ?: continue
                val mime = fmt.optString("mimeType").trim()
                if (!mime.startsWith("video/")) continue
                val cand = parseAudioFormatCandidate(fmt) ?: continue
                val width = fmt.optInt("width", 0)
                val height = fmt.optInt("height", 0)
                val longEdge = maxOf(width, height)
                val isMp4 = mime.startsWith("video/mp4", ignoreCase = true)
                val isAvc = mime.contains("avc1", ignoreCase = true)
                val isVp9 = mime.contains("vp9", ignoreCase = true) || mime.contains("vp09", ignoreCase = true)
                val isAv1 = mime.contains("av01", ignoreCase = true)
                val bitrate = fmt.optInt("bitrate", 0)

                // Strongly prefer universally hardware-decoded H.264 (avc1) 480p-720p (width 640..1280)
                // regardless of whether height is 720 (16:9) or 544/362 (2.35:1 CinemaScope).
                var score = when {
                    isMp4 && isAvc -> 2000
                    isMp4 && !isAv1 -> 1200
                    isVp9 -> 900
                    else -> 200 // AV1 last — many Android devices lack hardware AV1 decoding
                }
                score += when {
                    longEdge in 850..1300 -> 650 // 720p / 480p sweet spot (e.g. 1280x720, 1280x544, 854x362)
                    longEdge in 600..849 -> 520  // 360p (640x360, 640x272)
                    longEdge in 1301..1960 -> 450 // 1080p (1920x1080, 1920x816)
                    longEdge > 1960 -> -200       // Avoid heavy 1440p/4K streams on mobile overlay
                    else -> 200
                }
                if (bitrate in 300_000..2_200_000) {
                    score += 80
                }
                candidates.add(VideoFormatCandidate(cand, isHls = false, score = score))
            }
        }

        // HLS manifest (VISIONOS / IOS): native adaptive H.264 in Media3 HlsMediaSource, rock-solid fallback
        val hlsUrl = streamingData.optString("hlsManifestUrl").trim()
        if (hlsUrl.isNotBlank()) {
            candidates.add(
                VideoFormatCandidate(
                    candidate = AudioFormatCandidate(url = hlsUrl),
                    isHls = true,
                    score = 2480
                )
            )
        }

        // Muxed progressive MP4 formats (itag 22 / 18 — accept any height >= 144 for CinemaScope 640x272!)
        streamingData.optJSONArray("formats")?.let { formats ->
            for (i in 0 until formats.length()) {
                val fmt = formats.optJSONObject(i) ?: continue
                val mime = fmt.optString("mimeType").trim()
                if (!mime.startsWith("video/mp4", ignoreCase = true)) continue
                val cand = parseAudioFormatCandidate(fmt) ?: continue
                val width = fmt.optInt("width", 0)
                val height = fmt.optInt("height", 0)
                val longEdge = maxOf(width, height)
                if (longEdge < 240) continue
                val score = 2350 + (if (longEdge >= 1200) 200 else if (longEdge >= 600) 100 else 0)
                candidates.add(VideoFormatCandidate(cand, isHls = false, score = score))
            }
        }

        return candidates.sortedByDescending { it.score }
    }

    suspend fun searchSongs(query: String): List<TrackItem> = withContext(Dispatchers.IO) {
        val result = searchCatalog(query)
        result.songs
    }

    suspend fun searchAlbums(query: String): List<AlbumItem> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext emptyList()
        val directAlbums = executeInnerTubeAlbumSearch(cleanQuery, strictlyAlbumsOnly = true)
        if (directAlbums.isNotEmpty()) return@withContext directAlbums
        searchCatalog(cleanQuery).albums
    }

    suspend fun searchYouTubeMusic(query: String, filter: SearchFilter = SearchFilter.ALL): SearchResultData = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext SearchResultData()

        val cacheKey = "${cleanQuery.lowercase()}_${filter.name}"
        searchFilterCache[cacheKey]?.let { return@withContext it }

        val result = try {
            when (filter) {
                SearchFilter.ALL -> coroutineScope {
                    val songsDeferred = async { executeInnerTubeSearch(cleanQuery, SONGS_PARAM) }
                    val albumsDeferred = async { executeInnerTubeAlbumSearch(cleanQuery, strictlyAlbumsOnly = true) }
                    val artistsDeferred = async { executeInnerTubeArtistSearch(cleanQuery, limit = 10) }
                    val songs = songsDeferred.await()
                    val albums = albumsDeferred.await().ifEmpty {
                        buildAlbumsFromSongs(cleanQuery, songs)
                    }
                    val searchedArtists = artistsDeferred.await()
                    val mergedArtists = mergeSearchArtists(
                        searchedArtists = searchedArtists,
                        fallbackArtists = buildArtistsFromSongs(songs),
                        limit = 10
                    )
                    val artists = enrichArtistAvatarsConcurrently(mergedArtists)
                    SearchResultData(songs = songs, albums = albums, artists = artists)
                }
                SearchFilter.SONGS -> {
                    val songs = executeInnerTubeSearch(cleanQuery, SONGS_PARAM)
                    SearchResultData(songs = songs)
                }
                SearchFilter.ALBUMS -> {
                    val albums = executeInnerTubeAlbumSearch(cleanQuery, strictlyAlbumsOnly = true)
                    SearchResultData(albums = albums)
                }
                SearchFilter.ARTISTS -> coroutineScope {
                    val artistsDeferred = async { executeInnerTubeArtistSearch(cleanQuery, limit = 15) }
                    val songsDeferred = async { executeInnerTubeSearch(cleanQuery, SONGS_PARAM) }
                    val searchedArtists = artistsDeferred.await()
                    val fallbackArtists = if (searchedArtists.size < 6) {
                        buildArtistsFromSongs(songsDeferred.await())
                    } else {
                        songsDeferred.cancel()
                        emptyList()
                    }
                    val mergedArtists = mergeSearchArtists(
                        searchedArtists = searchedArtists,
                        fallbackArtists = fallbackArtists,
                        limit = 15
                    )
                    SearchResultData(artists = enrichArtistAvatarsConcurrently(mergedArtists))
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            safeLog("Search failed for '$cleanQuery'", e)
            SearchResultData()
        }

        if (result.songs.isNotEmpty() || result.albums.isNotEmpty() || result.artists.isNotEmpty()) {
            searchFilterCache[cacheKey] = result
        }
        result
    }

    suspend fun searchCatalog(query: String): CatalogQueryResult = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim().ifEmpty { "Top Global Pop Hits" }
        cache[cleanQuery]?.let { return@withContext it }

        try {
            coroutineScope {
                val songsDeferred = async { executeInnerTubeSearch(cleanQuery, SONGS_PARAM) }
                val albumsDeferred = async { executeInnerTubeAlbumSearch(cleanQuery, strictlyAlbumsOnly = true) }
                val artistsDeferred = async { executeInnerTubeArtistSearch(cleanQuery, limit = 10) }
                val songs = songsDeferred.await()
                val albums = albumsDeferred.await().ifEmpty {
                    buildAlbumsFromSongs(cleanQuery, songs)
                }
                val mergedArtists = mergeSearchArtists(
                    searchedArtists = artistsDeferred.await(),
                    fallbackArtists = buildArtistsFromSongs(songs),
                    limit = 10
                )
                val artists = enrichArtistAvatarsConcurrently(mergedArtists)

                val queryResult = CatalogQueryResult(songs, albums, artists)
                if (songs.isNotEmpty() || artists.isNotEmpty()) {
                    cache[cleanQuery] = queryResult
                }
                queryResult
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            safeLog("Catalog search failed for '$cleanQuery'", e)
            CatalogQueryResult(emptyList(), emptyList(), emptyList())
        }
    }

    /**
     * Fetches official default recommendations from YouTube Music Charts & Explore
     * when there is no connected user account and no prior listening history.
     * Contains ZERO hardcoded placeholders.
     */
    suspend fun fetchDefaultRecommendations(): CatalogQueryResult = withContext(Dispatchers.IO) {
        val cacheKey = "__default_ytm_recommendations__"
        cache[cacheKey]?.let { return@withContext it }

        val songs = mutableListOf<TrackItem>()
        val albums = mutableListOf<AlbumItem>()
        val artists = mutableListOf<ArtistItem>()

        // 1. Attempt to browse YouTube Music Charts
        try {
            val chartsResult = executeInnerTubeBrowse("FEmusic_charts")
            if (chartsResult.songs.isNotEmpty()) songs.addAll(chartsResult.songs)
            if (chartsResult.albums.isNotEmpty()) albums.addAll(chartsResult.albums)
            if (chartsResult.artists.isNotEmpty()) artists.addAll(chartsResult.artists)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            safeLog("Browse FEmusic_charts failed: ${e.message}")
        }

        // 2. Fallback to standard official chart searches in parallel if browse returned insufficient items
        if (songs.size < 8) {
            try {
                coroutineScope {
                    val songsDeferred = async { executeInnerTubeSearch("Top Songs Global", SONGS_PARAM) }
                    val albumsDeferred = async { executeInnerTubeAlbumSearch("Top Albums Global", strictlyAlbumsOnly = true) }
                    val chartSongs = songsDeferred.await()
                    val chartAlbums = albumsDeferred.await()
                    val topArtists = buildArtistsFromSongs(chartSongs)

                    val uniqueSongs = chartSongs.filter { s -> songs.none { it.id == s.id } }
                    songs.addAll(uniqueSongs)

                    val uniqueAlbums = chartAlbums.filter { a -> albums.none { it.title.equals(a.title, ignoreCase = true) } }
                    albums.addAll(uniqueAlbums)

                    val uniqueArtists = topArtists.filter { art -> artists.none { it.name.equals(art.name, ignoreCase = true) } }
                    artists.addAll(uniqueArtists)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                safeLog("Chart search fallback failed: ${e.message}")
            }
        }

        val result = CatalogQueryResult(songs, albums, artists)
        if (songs.isNotEmpty()) {
            cache[cacheKey] = result
        }
        result
    }

    fun executeInnerTubeBrowse(browseId: String): CatalogQueryResult {
        val payload = JSONObject().apply {
            put("context", buildWebRemixContextJson())
            put("browseId", browseId)
        }

        val request = buildWebRemixRequestBuilder(browseUrl, payload).build()

        val bodyString = httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return CatalogQueryResult(emptyList(), emptyList(), emptyList())
            response.body?.string() ?: return CatalogQueryResult(emptyList(), emptyList(), emptyList())
        }
        val json = JSONObject(bodyString)

        val tracks = mutableListOf<TrackItem>()
        val seenTrackIds = mutableSetOf<String>()
        extractTracksRecursive(json, tracks, seenTrackIds)

        val albums = mutableListOf<AlbumItem>()
        val seenAlbumTitles = mutableSetOf<String>()
        extractAlbumsRecursive(json, albums, seenAlbumTitles, strictlyAlbumsOnly = false)

        val artists = buildArtistsFromSongs(tracks)

        return CatalogQueryResult(tracks, albums, artists)
    }

    private fun executeInnerTubeSearch(query: String, filterParam: String?): List<TrackItem> {
        val payload = JSONObject().apply {
            put("context", buildWebRemixContextJson())
            put("query", query)
            if (!filterParam.isNullOrBlank()) {
                put("params", filterParam)
            }
        }

        val request = buildWebRemixRequestBuilder(searchUrl, payload).build()

        val bodyString = httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            response.body?.string() ?: return emptyList()
        }
        val json = JSONObject(bodyString)

        val tracks = mutableListOf<TrackItem>()
        val seenIds = mutableSetOf<String>()
        extractTracksRecursive(json, tracks, seenIds)
        return tracks
    }

    private fun extractTracksRecursive(
        jsonObj: Any,
        results: MutableList<TrackItem>,
        seenIds: MutableSet<String>,
        isPlaylistContext: Boolean = false,
        playlistFallbackCover: String = ""
    ) {
        when (jsonObj) {
            is JSONObject -> {
                if (jsonObj.has("musicResponsiveListItemRenderer")) {
                    val renderer = jsonObj.getJSONObject("musicResponsiveListItemRenderer")
                    parseResponsiveItem(
                        renderer = renderer,
                        isPlaylistContext = isPlaylistContext,
                        playlistFallbackCover = playlistFallbackCover
                    )?.let { track ->
                        if (seenIds.add(track.id)) {
                            results.add(track)
                        }
                    }
                    return
                }

                val keys = jsonObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val child = jsonObj.get(key)
                    extractTracksRecursive(child, results, seenIds, isPlaylistContext, playlistFallbackCover)
                }
            }
            is JSONArray -> {
                for (i in 0 until jsonObj.length()) {
                    val child = jsonObj.get(i)
                    extractTracksRecursive(child, results, seenIds, isPlaylistContext, playlistFallbackCover)
                }
            }
        }
    }

    internal fun parseResponsiveItem(
        renderer: JSONObject,
        isPlaylistContext: Boolean = false,
        playlistFallbackCover: String = ""
    ): TrackItem? {
        val creditsAtvId = extractTrackCreditsAtvVideoId(renderer)
        val rawPlaylistVideoId = renderer.optJSONObject("playlistItemData")?.optString("videoId")?.trim().orEmpty()
        val musicVideoType = extractMusicVideoType(renderer)

        // Filter out pure music videos / UGC items in search & feed lists unless Track Credits expose an ATV ID
        if (!isPlaylistContext && creditsAtvId.isNullOrBlank() && isVideoMusicVideoType(musicVideoType)) {
            return null
        }

        val videoId = creditsAtvId
            ?: rawPlaylistVideoId.takeIf { it.isNotBlank() }
            ?: findKeyInJson(renderer, "videoId")?.trim()?.takeIf { it.isNotBlank() }
            ?: return null

        val flexColumns = renderer.optJSONArray("flexColumns") ?: return null
        if (flexColumns.length() < 2) return null

        // Column 0: Title
        val title = extractRunsText(flexColumns.optJSONObject(0)).trim()
        if (title.isBlank()) return null

        // Column 1: Artist • Album • Duration
        val col1Meta = parseMetadataRuns(
            flexColumn = flexColumns.optJSONObject(1),
            defaultArtist = "Unknown Artist",
            defaultAlbum = ""
        )

        // Column 2 (common in playlists): Album (guard against view/play count labels & durations)
        val col2Text = if (flexColumns.length() >= 3) {
            extractRunsText(flexColumns.optJSONObject(2)).trim()
        } else ""
        val col2Album = if (
            col2Text.isNotBlank() &&
            !isPlayCountLabel(col2Text) &&
            !DURATION_REGEX.matches(col2Text) &&
            !YEAR_REGEX.matches(col2Text) &&
            !isMetadataBadgeLabel(col2Text)
        ) {
            col2Text
        } else ""

        val album = when {
            col1Meta.album.isNotBlank() && !isPlayCountLabel(col1Meta.album) -> col1Meta.album
            col2Album.isNotBlank() -> col2Album
            else -> "Single"
        }

        // Fixed columns (common in playlists/albums): Duration
        val fixedCols = renderer.optJSONArray("fixedColumns")
        val fixedDurText = extractAllRunsText(
            fixedCols?.optJSONObject(0)
                ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")
                ?.optJSONObject("text")
        ).trim()
        val durationMs = when {
            fixedDurText.contains(":") -> parseDuration(fixedDurText)
            col1Meta.durationMs > 0L -> col1Meta.durationMs
            DURATION_REGEX.matches(col2Text) -> parseDuration(col2Text)
            else -> 210000L
        }

        // Strip 16:9 i.ytimg.com/vi/ video frame thumbnails when a playlist item was promoted from OMV to ATV
        val rawRowThumb = extractThumbUrlFromObject(
            renderer.optJSONObject("thumbnail")
                ?.optJSONObject("musicThumbnailRenderer")
                ?.optJSONObject("thumbnail")
        ).orEmpty()
        val replacedPlaylistVideoThumb = isPlaylistContext &&
            rawPlaylistVideoId.isNotBlank() &&
            rawPlaylistVideoId != videoId &&
            rawRowThumb.contains("i.ytimg.com/vi/", ignoreCase = true)

        val thumbnail = when {
            replacedPlaylistVideoThumb -> playlistFallbackCover.ifBlank { null }
            rawRowThumb.isNotBlank() -> extractHighResThumbnail(renderer, videoId)
            isPlaylistContext && playlistFallbackCover.isNotBlank() -> playlistFallbackCover
            else -> extractHighResThumbnail(renderer, videoId)
        }

        return TrackItem(
            id = "ytm_$videoId",
            title = title,
            artist = col1Meta.artist.ifBlank { "YouTube Artist" },
            album = album,
            durationMs = durationMs,
            artworkUri = thumbnail,
            audioUrl = "https://www.youtube.com/watch?v=$videoId",
            playCount = col1Meta.playCount,
            isExplicit = hasExplicitBadge(renderer)
        )
    }

    private fun findVideoId(renderer: JSONObject): String? = extractPreferredAudioVideoId(renderer)

    internal fun extractTrackCreditsAtvVideoId(node: Any?): String? {
        when (node) {
            is JSONObject -> {
                val browseId = node.optString("browseId", "").trim()
                if (browseId.isNotEmpty()) {
                    val match = TRACK_CREDITS_BROWSE_ID_REGEX.matchEntire(browseId)
                    if (match != null) return match.groupValues[1]
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val found = extractTrackCreditsAtvVideoId(node.opt(key))
                    if (found != null) return found
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    val found = extractTrackCreditsAtvVideoId(node.opt(i))
                    if (found != null) return found
                }
            }
        }
        return null
    }

    internal fun extractMusicVideoType(node: Any?): String {
        return findKeyInJson(node, "musicVideoType")?.trim().orEmpty()
    }

    internal fun isPureAudioMusicVideoType(musicVideoType: String?): Boolean {
        if (musicVideoType.isNullOrBlank()) return false
        return musicVideoType.equals("MUSIC_VIDEO_TYPE_ATV", ignoreCase = true) ||
            musicVideoType.equals("MUSIC_VIDEO_TYPE_OFFICIAL_SOURCE_MUSIC", ignoreCase = true)
    }

    internal fun isVideoMusicVideoType(musicVideoType: String?): Boolean {
        if (musicVideoType.isNullOrBlank()) return false
        return musicVideoType.equals("MUSIC_VIDEO_TYPE_OMV", ignoreCase = true) ||
            musicVideoType.equals("MUSIC_VIDEO_TYPE_UGC", ignoreCase = true) ||
            musicVideoType.equals("MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK", ignoreCase = true)
    }

    internal fun extractPreferredAudioVideoId(renderer: JSONObject): String? {
        extractTrackCreditsAtvVideoId(renderer)?.let { return it }
        val playlistItemData = renderer.optJSONObject("playlistItemData")
        if (playlistItemData != null && playlistItemData.has("videoId")) {
            val pidVid = playlistItemData.optString("videoId").trim()
            if (pidVid.isNotBlank()) return pidVid
        }
        val directVid = renderer.optString("videoId", "").trim()
        if (directVid.isNotBlank()) return directVid
        return findKeyInJson(renderer, "videoId")?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun findKeyInJson(node: Any?, targetKey: String): String? {
        when (node) {
            is JSONObject -> {
                if (node.has(targetKey)) {
                    val direct = node.opt(targetKey)
                    if (direct is String && direct.isNotBlank()) {
                        return direct
                    }
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val found = findKeyInJson(node.opt(key), targetKey)
                    if (found != null) return found
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    val found = findKeyInJson(node.opt(i), targetKey)
                    if (found != null) return found
                }
            }
        }
        return null
    }

    private fun extractRunsText(flexCol: JSONObject?): String {
        if (flexCol == null) return ""
        val colRenderer = flexCol.optJSONObject("musicResponsiveListItemFlexColumnRenderer") ?: return ""
        val textObj = colRenderer.optJSONObject("text") ?: return ""
        val runs = textObj.optJSONArray("runs") ?: return ""

        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            val run = runs.optJSONObject(i)
            if (run != null && run.has("text")) {
                sb.append(run.optString("text"))
            }
        }
        return sb.toString().trim()
    }

    private data class ParsedMetadataRuns(
        val artist: String,
        val album: String,
        val durationMs: Long,
        val year: String,
        val playCount: Long
    )

    private fun isMetadataBadgeLabel(text: String): Boolean {
        return text.equals("Song", ignoreCase = true) ||
            text.equals("Video", ignoreCase = true) ||
            text.equals("Canción", ignoreCase = true) ||
            text.equals("Single", ignoreCase = true) ||
            text.equals("Sencillo", ignoreCase = true) ||
            text.equals("Album", ignoreCase = true) ||
            text.equals("Álbum", ignoreCase = true) ||
            text.equals("EP", ignoreCase = true)
    }

    private fun parseMetadataRuns(
        flexColumn: JSONObject?,
        defaultArtist: String = "",
        defaultAlbum: String = ""
    ): ParsedMetadataRuns {
        val colRenderer = flexColumn?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
        val runs = colRenderer?.optJSONObject("text")?.optJSONArray("runs")
            ?: return ParsedMetadataRuns(defaultArtist, defaultAlbum, 0L, "", 0L)

        val artistParts = mutableListOf<String>()
        var album = ""
        var durationMs = 0L
        var year = ""
        var playCount = 0L
        val untypedTokens = mutableListOf<String>()

        fun processToken(text: String, runBrowseId: String, runPageType: String) {
            val cleaned = text.trim()
            if (cleaned.isBlank()) return
            if (cleaned.matches(Regex("""^[,&/|•·\-–—+]+$"""))) return
            if (cleaned.equals("feat.", ignoreCase = true) ||
                cleaned.equals("ft.", ignoreCase = true) ||
                cleaned.equals("featuring", ignoreCase = true)
            ) return
            if (isMetadataBadgeLabel(cleaned)) return
            if (isPlayCountLabel(cleaned)) {
                playCount = maxOf(playCount, parsePlayCountNumber(cleaned))
                return
            }
            if (DURATION_REGEX.matches(cleaned)) {
                durationMs = parseDuration(cleaned)
                return
            }
            if (YEAR_REGEX.matches(cleaned)) {
                if (year.isEmpty()) year = cleaned
                return
            }
            if (runPageType == "MUSIC_PAGE_TYPE_ALBUM" || runBrowseId.startsWith("MPREb_")) {
                if (album.isEmpty()) album = cleaned
                return
            }
            if (runPageType == "MUSIC_PAGE_TYPE_ARTIST" ||
                runPageType == "MUSIC_PAGE_TYPE_USER_CHANNEL" ||
                runBrowseId.startsWith("UC")
            ) {
                artistParts.add(cleaned)
                return
            }
            untypedTokens.add(cleaned)
        }

        for (i in 0 until runs.length()) {
            val run = runs.optJSONObject(i) ?: continue
            val rawText = run.optString("text", "")
            val navEp = run.optJSONObject("navigationEndpoint")
            val browseEp = navEp?.optJSONObject("browseEndpoint")
            val runBrowseId = browseEp?.optString("browseId").orEmpty().trim()
            val runPageType = extractBrowsePageType(navEp)

            if (navEp == null && rawText.contains("•")) {
                rawText.split("•").forEach { part -> processToken(part, "", "") }
            } else {
                processToken(rawText, runBrowseId, runPageType)
            }
        }

        val artist = when {
            artistParts.isNotEmpty() -> artistParts.joinToString(", ")
            untypedTokens.isNotEmpty() -> untypedTokens.removeAt(0)
            else -> defaultArtist
        }
        val resolvedAlbum = when {
            album.isNotBlank() -> album
            untypedTokens.isNotEmpty() -> untypedTokens.first()
            else -> defaultAlbum
        }
        return ParsedMetadataRuns(
            artist = artist,
            album = resolvedAlbum,
            durationMs = durationMs,
            year = year,
            playCount = playCount
        )
    }

    private fun isTwoRowVideoOnlyItem(
        twoRow: JSONObject,
        subtitle: String,
        shelfTitle: String,
        creditsAtvId: String?
    ): Boolean {
        if (!creditsAtvId.isNullOrBlank()) return false

        val musicVideoType = extractMusicVideoType(twoRow)
        if (isPureAudioMusicVideoType(musicVideoType)) return false
        if (isVideoMusicVideoType(musicVideoType)) return true

        val lowerShelf = shelfTitle.lowercase(java.util.Locale.ROOT)
        if (lowerShelf.contains("music video") ||
            lowerShelf.contains("videos musical") ||
            lowerShelf.contains("vídeos musical")
        ) {
            return true
        }

        if (subtitle.isNotBlank()) {
            val tokens = subtitle.split("•").map { it.trim() }.filter { it.isNotBlank() }
            if (tokens.any { token ->
                    token.equals("Video", ignoreCase = true) ||
                        token.equals("Music Video", ignoreCase = true) ||
                        isPlayCountLabel(token)
                }
            ) {
                return true
            }
        }
        return false
    }

    private fun parseDuration(durationStr: String): Long {
        val parts = durationStr.split(":")
        return when (parts.size) {
            2 -> {
                val m = parts[0].toLongOrNull() ?: 3L
                val s = parts[1].toLongOrNull() ?: 30L
                (m * 60 + s) * 1000L
            }
            3 -> {
                val h = parts[0].toLongOrNull() ?: 0L
                val m = parts[1].toLongOrNull() ?: 0L
                val s = parts[2].toLongOrNull() ?: 0L
                (h * 3600 + m * 60 + s) * 1000L
            }
            else -> 210000L
        }
    }

    private fun containsExplicitBadgeInArray(badges: JSONArray?): Boolean {
        if (badges == null) return false
        for (i in 0 until badges.length()) {
            val badge = badges.optJSONObject(i) ?: continue
            val inlineBadge = badge.optJSONObject("musicInlineBadgeRenderer")
            val inlineIcon = inlineBadge?.optJSONObject("icon")?.optString("iconType").orEmpty()
            val inlineStyle = inlineBadge?.optString("style").orEmpty()
            val metaBadge = badge.optJSONObject("metadataBadgeRenderer")
            val metaIcon = metaBadge?.optJSONObject("icon")?.optString("iconType").orEmpty()
            val metaStyle = metaBadge?.optString("style").orEmpty()
            val directIcon = badge.optJSONObject("icon")?.optString("iconType").orEmpty()
            val inlineLabel = inlineBadge?.optJSONObject("accessibilityData")
                ?.optJSONObject("accessibilityData")
                ?.optString("label")
                .orEmpty()

            if (inlineIcon == "MUSIC_EXPLICIT_BADGE" ||
                metaIcon == "MUSIC_EXPLICIT_BADGE" ||
                directIcon == "MUSIC_EXPLICIT_BADGE" ||
                inlineStyle == "BADGE_STYLE_TYPE_EXPLICIT" ||
                metaStyle == "BADGE_STYLE_TYPE_EXPLICIT" ||
                inlineLabel.equals("Explicit", ignoreCase = true) ||
                inlineLabel.equals("Explícito", ignoreCase = true) ||
                inlineLabel.equals("Explicito", ignoreCase = true) ||
                inlineLabel.equals("Contenido explícito", ignoreCase = true)
            ) {
                return true
            }
        }
        return false
    }

    internal fun hasExplicitBadge(renderer: JSONObject?): Boolean {
        if (renderer == null) return false
        return containsExplicitBadgeInArray(renderer.optJSONArray("badges")) ||
            containsExplicitBadgeInArray(renderer.optJSONArray("subtitleBadges")) ||
            containsExplicitBadgeInArray(renderer.optJSONArray("thumbnailBadges")) ||
            containsExplicitBadgeInArray(renderer.optJSONArray("titleBadges"))
    }

    internal fun normalizeHighResImageUrl(rawUrl: String?, targetSizePx: Int = 540): String? {
        val trimmed = rawUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val withScheme = if (trimmed.startsWith("//")) "https:$trimmed" else trimmed
        return withScheme
            .replace(Regex("=w\\d+-h\\d+"), "=w$targetSizePx-h$targetSizePx")
            .replace(Regex("=s\\d+"), "=s$targetSizePx")
    }

    private fun extractThumbUrlListFromRenderer(renderer: JSONObject): JSONArray? {
        val thumbnailObj = renderer.optJSONObject("thumbnail")
        return thumbnailObj?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
            ?: thumbnailObj?.optJSONObject("croppedSquareThumbnailRenderer")
                ?.optJSONObject("thumbnail")
                ?.optJSONArray("thumbnails")
            ?: thumbnailObj?.optJSONObject("thumbnailRenderer")
                ?.optJSONObject("musicThumbnailRenderer")
                ?.optJSONObject("thumbnail")
                ?.optJSONArray("thumbnails")
            ?: renderer.optJSONObject("thumbnailRenderer")
                ?.optJSONObject("musicThumbnailRenderer")
                ?.optJSONObject("thumbnail")
                ?.optJSONArray("thumbnails")
    }

    private fun extractHighResThumbnail(renderer: JSONObject, videoId: String): String {
        try {
            val thumbs = extractThumbUrlListFromRenderer(renderer)
            if (thumbs != null && thumbs.length() > 0) {
                for (i in thumbs.length() - 1 downTo 0) {
                    val url = normalizeHighResImageUrl(thumbs.optJSONObject(i)?.optString("url"))
                    if (url != null) return url
                }
            }
        } catch (_: Exception) {}

        return "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
    }

    private fun buildAlbumsFromSongs(query: String, songs: List<TrackItem>): List<AlbumItem> {
        val map = linkedMapOf<String, AlbumItem>()
        for (song in songs) {
            val albumName = song.album.ifBlank { song.title }
            if (albumName.equals("YouTube Music", ignoreCase = true)) continue
            if (!map.containsKey(albumName)) {
                map[albumName] = AlbumItem(
                    id = "ytm_album_${albumName.hashCode()}",
                    title = albumName,
                    artist = song.artist,
                    coverUri = song.artworkUri,
                    year = 2024,
                    tracksCount = 10
                )
            }
        }
        return map.values.take(12)
    }

    fun executeInnerTubeAlbumSearch(query: String, strictlyAlbumsOnly: Boolean = false): List<AlbumItem> {
        val payload = JSONObject().apply {
            put("context", buildWebRemixContextJson())
            put("query", query)
            put("params", ALBUMS_PARAM)
        }

        val request = buildWebRemixRequestBuilder(searchUrl, payload).build()

        val bodyString = try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                response.body?.string() ?: return emptyList()
            }
        } catch (_: Exception) {
            return emptyList()
        }
        val json = try {
            JSONObject(bodyString)
        } catch (_: Exception) {
            return emptyList()
        }

        val albums = mutableListOf<AlbumItem>()
        val seenIds = mutableSetOf<String>()
        extractAlbumsRecursive(json, albums, seenIds, strictlyAlbumsOnly)
        return albums
    }

    private fun extractAlbumsRecursive(
        jsonObj: Any,
        results: MutableList<AlbumItem>,
        seenIds: MutableSet<String>,
        strictlyAlbumsOnly: Boolean
    ) {
        when (jsonObj) {
            is JSONObject -> {
                if (jsonObj.has("musicResponsiveListItemRenderer")) {
                    val renderer = jsonObj.getJSONObject("musicResponsiveListItemRenderer")
                    parseResponsiveAlbumItem(renderer, strictlyAlbumsOnly)?.let { album ->
                        if (seenIds.add(album.id)) {
                            results.add(album)
                        }
                    }
                    return
                }

                val keys = jsonObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val child = jsonObj.get(key)
                    extractAlbumsRecursive(child, results, seenIds, strictlyAlbumsOnly)
                }
            }
            is JSONArray -> {
                for (i in 0 until jsonObj.length()) {
                    val child = jsonObj.get(i)
                    extractAlbumsRecursive(child, results, seenIds, strictlyAlbumsOnly)
                }
            }
        }
    }

    internal fun parseResponsiveAlbumItem(renderer: JSONObject, strictlyAlbumsOnly: Boolean): AlbumItem? {
        val flexCols = renderer.optJSONArray("flexColumns") ?: return null
        if (flexCols.length() == 0) return null

        val col0 = flexCols.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
        val title = extractAllRunsText(col0).trim()
        if (title.isBlank()) return null

        val col1 = flexCols.optJSONObject(1)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
        val runs1 = col1?.optJSONArray("runs")

        var type = "Album"
        var artist = "Unknown Artist"
        var year = 2024

        if (runs1 != null) {
            for (i in 0 until runs1.length()) {
                val t = runs1.optJSONObject(i)?.optString("text", "")?.trim() ?: continue
                if (t == "•" || t.isBlank()) continue
                if (t.equals("Album", ignoreCase = true) || t.equals("Álbum", ignoreCase = true) ||
                    t.equals("Single", ignoreCase = true) || t.equals("EP", ignoreCase = true)) {
                    type = t
                } else if (Regex("""\b(19\d\d|20\d\d)\b""").matches(t)) {
                    year = t.toIntOrNull() ?: 2024
                } else if (artist == "Unknown Artist") {
                    artist = t
                }
            }
        }

        // If strictlyAlbumsOnly is true, skip items that are explicitly "Single" or "Sencillo"
        if (strictlyAlbumsOnly && (type.equals("Single", ignoreCase = true) || type.equals("Sencillo", ignoreCase = true))) {
            return null
        }

        val navEp = renderer.optJSONObject("navigationEndpoint")
        val browseId = navEp?.optJSONObject("browseEndpoint")?.optString("browseId")
            ?: "${title.hashCode()}"

        val thumb = extractHighResAvatarFromRenderer(renderer) ?: ""

        return AlbumItem(
            id = "ytm_alb_$browseId",
            title = title,
            artist = artist,
            coverUri = thumb,
            year = year,
            tracksCount = if (type.equals("Single", ignoreCase = true)) 1 else 10
        )
    }

    private fun primaryArtistName(artistName: String): String {
        return artistName.split(",", ";", "/", "&")
            .firstOrNull()
            ?.trim()
            .orEmpty()
            .ifBlank { artistName.trim() }
    }

    private fun cacheArtistAvatar(artistName: String, avatarUrl: String?) {
        val clean = artistName.trim()
        val url = normalizeHighResImageUrl(avatarUrl)
        if (clean.isBlank() || url.isNullOrBlank()) return
        artistAvatarCache[clean] = url
        artistAvatarCache[clean.lowercase(Locale.ROOT)] = url
    }

    private fun parseArtistSubtitleStats(subtitle: String): Pair<String, String> {
        if (subtitle.isBlank()) return "Trending" to "YouTube Music"
        val parts = subtitle.split("•").map { it.trim() }.filter { it.isNotBlank() }
        val stat = parts.firstOrNull { part ->
            !part.equals("Artist", ignoreCase = true) &&
                !part.equals("Artista", ignoreCase = true)
        }
        return (stat ?: "Trending") to (stat ?: "YouTube Music")
    }

    private fun executeInnerTubeArtistSearch(query: String, limit: Int = 10): List<ArtistItem> {
        if (query.isBlank()) return emptyList()
        return try {
            val payload = JSONObject().apply {
                put("context", buildWebRemixContextJson())
                put("query", query)
                put("params", ARTISTS_PARAM)
            }
            val json = executeInnerTubePost(searchUrl, payload, includeAuthHeaders = true)
                ?: return emptyList()
            val results = mutableListOf<ArtistItem>()
            val seenKeys = mutableSetOf<String>()
            extractArtistsRecursive(json, results, seenKeys, limit)
            results
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            safeLog("Artist search failed for '$query': ${e.message}")
            emptyList()
        }
    }

    private fun extractArtistsRecursive(
        jsonObj: Any,
        results: MutableList<ArtistItem>,
        seenKeys: MutableSet<String>,
        limit: Int
    ) {
        if (results.size >= limit) return
        when (jsonObj) {
            is JSONObject -> {
                jsonObj.optJSONObject("musicCardShelfRenderer")?.let { card ->
                    val titleObj = card.optJSONObject("title")
                    val name = extractAllRunsText(titleObj).trim()
                    val subtitle = extractAllRunsText(card.optJSONObject("subtitle")).trim()
                    val titleNav = titleObj?.optJSONArray("runs")
                        ?.optJSONObject(0)
                        ?.optJSONObject("navigationEndpoint")
                        ?: card.optJSONObject("onTap")
                    val browseId = titleNav?.optJSONObject("browseEndpoint")?.optString("browseId").orEmpty()
                    val pageType = extractBrowsePageType(titleNav)
                    val isArtistCard = pageType == "MUSIC_PAGE_TYPE_ARTIST" ||
                        browseId.startsWith("UC") ||
                        browseId.startsWith("FEmusic_library_privately_owned_artist_detail") ||
                        subtitle.contains("Artist", ignoreCase = true) ||
                        subtitle.contains("Artista", ignoreCase = true) ||
                        subtitle.contains("monthly listener", ignoreCase = true) ||
                        subtitle.contains("oyentes", ignoreCase = true) ||
                        subtitle.contains("subscriber", ignoreCase = true) ||
                        subtitle.contains("suscriptor", ignoreCase = true)

                    if (isArtistCard && name.isNotBlank() && seenKeys.add(name.lowercase(Locale.ROOT))) {
                        val avatarUrl = extractHighResAvatarFromRenderer(card)
                        cacheArtistAvatar(name, avatarUrl)
                        val (listeners, subs) = parseArtistSubtitleStats(subtitle)
                        val artistId = if (browseId.startsWith("UC")) {
                            "ytm_sim_$browseId"
                        } else {
                            "ytm_artist_${browseId.ifBlank { name.hashCode().toString() }}"
                        }
                        results.add(
                            ArtistItem(
                                id = artistId,
                                name = name,
                                avatarUri = avatarUrl,
                                genre = "YouTube Music",
                                monthlyListeners = listeners,
                                subscribers = subs
                            )
                        )
                        if (results.size >= limit) return
                    }
                }

                jsonObj.optJSONObject("musicResponsiveListItemRenderer")?.let { renderer ->
                    val flexCols = renderer.optJSONArray("flexColumns")
                    val name = if (flexCols != null && flexCols.length() > 0) {
                        extractAllRunsText(
                            flexCols.optJSONObject(0)
                                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                ?.optJSONObject("text")
                        ).trim()
                    } else ""
                    val subtitle = if (flexCols != null && flexCols.length() > 1) {
                        extractAllRunsText(
                            flexCols.optJSONObject(1)
                                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                ?.optJSONObject("text")
                        ).trim()
                    } else ""

                    val navEp = renderer.optJSONObject("navigationEndpoint")
                        ?: flexCols?.optJSONObject(0)
                            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                            ?.optJSONObject("text")
                            ?.optJSONArray("runs")
                            ?.optJSONObject(0)
                            ?.optJSONObject("navigationEndpoint")
                    val browseId = navEp?.optJSONObject("browseEndpoint")?.optString("browseId").orEmpty()
                    val pageType = extractBrowsePageType(navEp)
                    val watchVideoId = navEp?.optJSONObject("watchEndpoint")?.optString("videoId").orEmpty()
                    val isArtistRow = pageType == "MUSIC_PAGE_TYPE_ARTIST" ||
                        browseId.startsWith("UC") ||
                        browseId.startsWith("FEmusic_library_privately_owned_artist_detail") ||
                        (watchVideoId.isBlank() && (
                            subtitle.contains("artist", ignoreCase = true) ||
                                subtitle.contains("artista", ignoreCase = true) ||
                                subtitle.contains("monthly listener", ignoreCase = true) ||
                                subtitle.contains("oyentes", ignoreCase = true) ||
                                subtitle.contains("subscriber", ignoreCase = true) ||
                                subtitle.contains("suscriptor", ignoreCase = true)
                            ))

                    if (isArtistRow && name.isNotBlank() && seenKeys.add(name.lowercase(Locale.ROOT))) {
                        val avatarUrl = extractHighResAvatarFromRenderer(renderer)
                        cacheArtistAvatar(name, avatarUrl)
                        val (listeners, subs) = parseArtistSubtitleStats(subtitle)
                        val artistId = if (browseId.startsWith("UC")) {
                            "ytm_sim_$browseId"
                        } else {
                            "ytm_artist_${browseId.ifBlank { name.hashCode().toString() }}"
                        }
                        results.add(
                            ArtistItem(
                                id = artistId,
                                name = name,
                                avatarUri = avatarUrl,
                                genre = "YouTube Music",
                                monthlyListeners = listeners,
                                subscribers = subs
                            )
                        )
                    }
                    return
                }

                val keys = jsonObj.keys()
                while (keys.hasNext() && results.size < limit) {
                    val key = keys.next()
                    extractArtistsRecursive(jsonObj.get(key), results, seenKeys, limit)
                }
            }
            is JSONArray -> {
                for (i in 0 until jsonObj.length()) {
                    if (results.size >= limit) break
                    extractArtistsRecursive(jsonObj.get(i), results, seenKeys, limit)
                }
            }
        }
    }

    private fun mergeSearchArtists(
        searchedArtists: List<ArtistItem>,
        fallbackArtists: List<ArtistItem>,
        limit: Int
    ): List<ArtistItem> {
        val merged = linkedMapOf<String, ArtistItem>()
        for (artist in searchedArtists) {
            val key = primaryArtistName(artist.name).lowercase(Locale.ROOT)
            if (key.isBlank()) continue
            val existing = merged[key]
            merged[key] = when {
                existing == null -> artist
                existing.avatarUri.isNullOrBlank() && !artist.avatarUri.isNullOrBlank() ->
                    existing.copy(avatarUri = artist.avatarUri)
                else -> existing
            }
        }
        for (artist in fallbackArtists) {
            val key = primaryArtistName(artist.name).lowercase(Locale.ROOT)
            if (key.isBlank()) continue
            val existing = merged[key]
            if (existing == null) {
                merged[key] = artist
            } else if (existing.avatarUri.isNullOrBlank() && !artist.avatarUri.isNullOrBlank()) {
                merged[key] = existing.copy(avatarUri = artist.avatarUri)
            }
        }
        return merged.values.take(limit)
    }

    private suspend fun enrichArtistAvatarsConcurrently(artists: List<ArtistItem>): List<ArtistItem> {
        val missingIndices = artists.indices.filter { artists[it].avatarUri.isNullOrBlank() }
        if (missingIndices.isEmpty()) return artists

        val semaphore = Semaphore(permits = 6)
        val resolvedByIndex = coroutineScope {
            missingIndices.map { idx ->
                async {
                    val artist = artists[idx]
                    val resolved = getArtistAvatarFromCache(artist.name) ?: semaphore.withPermit {
                        withTimeoutOrNull(2_500L) {
                            getArtistAvatarUri(artist.name)
                        }
                    }
                    idx to resolved
                }
            }.awaitAll().toMap()
        }

        return artists.mapIndexed { idx, artist ->
            val avatar = resolvedByIndex[idx]
            if (!avatar.isNullOrBlank()) artist.copy(avatarUri = avatar) else artist
        }
    }

    private fun buildArtistsFromSongs(songs: List<TrackItem>): List<ArtistItem> {
        val map = linkedMapOf<String, ArtistItem>()
        for (song in songs) {
            val rawName = song.artist.trim()
            if (rawName.isBlank() ||
                rawName.equals("Unknown Artist", ignoreCase = true) ||
                rawName.equals("YouTube Artist", ignoreCase = true)
            ) continue
            val primary = primaryArtistName(rawName)
            val key = primary.lowercase(Locale.ROOT)
            if (!map.containsKey(key)) {
                val cachedAvatar = getArtistAvatarFromCache(primary)
                map[key] = ArtistItem(
                    id = "ytm_artist_${primary.hashCode()}",
                    name = primary,
                    avatarUri = cachedAvatar,
                    genre = "YouTube Music",
                    monthlyListeners = "Trending",
                    subscribers = "YouTube Music"
                )
            }
        }
        return map.values.take(10)
    }

    fun getArtistAvatarFromCache(artistName: String, context: Context? = null): String? {
        val primaryArtist = primaryArtistName(artistName)
        if (primaryArtist.isBlank() || primaryArtist.equals("Unknown Artist", ignoreCase = true)) return null
        val normalizedKey = primaryArtist.lowercase(Locale.ROOT)

        artistAvatarCache[primaryArtist]?.let { return it }
        artistAvatarCache[normalizedKey]?.let { return it }

        context?.let { ctx ->
            try {
                val prefs = ctx.getSharedPreferences("xperience_artist_avatars_cache", Context.MODE_PRIVATE)
                val cached = prefs.getString(primaryArtist, null) ?: prefs.getString(normalizedKey, null)
                val normalizedUrl = normalizeHighResImageUrl(cached)
                if (!normalizedUrl.isNullOrBlank()) {
                    cacheArtistAvatar(primaryArtist, normalizedUrl)
                    return normalizedUrl
                }
            } catch (_: Exception) {}
        }
        return null
    }

    suspend fun getArtistAvatarUri(artistName: String, context: Context? = null): String? = withContext(Dispatchers.IO) {
        val primaryArtist = primaryArtistName(artistName)
        if (primaryArtist.isBlank() || primaryArtist.equals("Unknown Artist", ignoreCase = true)) {
            return@withContext null
        }

        getArtistAvatarFromCache(primaryArtist, context)?.let { return@withContext it }

        val normKey = primaryArtist.lowercase(Locale.ROOT)
        inFlightArtistAvatars[normKey]?.let { existing ->
            val shared = runCatching { existing.await() }.getOrNull()
            if (!shared.isNullOrBlank()) return@withContext shared
        }

        val deferred = serviceScope.async(start = CoroutineStart.LAZY) {
            try {
                getArtistAvatarFromCache(primaryArtist, context)?.let { return@async it }
                val payload = JSONObject().apply {
                    put("context", buildWebRemixContextJson())
                    put("query", primaryArtist)
                    put("params", ARTISTS_PARAM)
                }

                val json = executeInnerTubePost(searchUrl, payload, includeAuthHeaders = true)
                    ?: return@async null

                val avatarUrl = extractFirstArtistAvatarUrl(json)
                if (!avatarUrl.isNullOrBlank()) {
                    cacheArtistAvatar(primaryArtist, avatarUrl)
                    context?.let { ctx ->
                        try {
                            val prefs = ctx.getSharedPreferences("xperience_artist_avatars_cache", Context.MODE_PRIVATE)
                            prefs.edit().putString(primaryArtist, avatarUrl).apply()
                        } catch (_: Exception) {}
                    }
                    return@async avatarUrl
                }
                null
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                safeLog("Failed to resolve YouTube Music avatar for '$primaryArtist': ${e.message}")
                null
            }
        }

        val activeDeferred = inFlightArtistAvatars.putIfAbsent(normKey, deferred) ?: run {
            deferred.invokeOnCompletion { inFlightArtistAvatars.remove(normKey, deferred) }
            deferred.start()
            deferred
        }
        if (activeDeferred !== deferred) {
            deferred.cancel()
        }
        return@withContext runCatching { activeDeferred.await() }.getOrNull()
    }

    private fun extractFirstArtistAvatarUrl(jsonObj: Any): String? {
        when (jsonObj) {
            is JSONObject -> {
                jsonObj.optJSONObject("musicCardShelfRenderer")?.let { card ->
                    val titleObj = card.optJSONObject("title")
                    val titleNav = titleObj?.optJSONArray("runs")
                        ?.optJSONObject(0)
                        ?.optJSONObject("navigationEndpoint")
                        ?: card.optJSONObject("onTap")
                    val browseId = titleNav?.optJSONObject("browseEndpoint")?.optString("browseId").orEmpty()
                    val pageType = extractBrowsePageType(titleNav)
                    val subtitle = extractAllRunsText(card.optJSONObject("subtitle"))
                    val isArtistCard = pageType == "MUSIC_PAGE_TYPE_ARTIST" ||
                        browseId.startsWith("UC") ||
                        browseId.startsWith("FEmusic_library_privately_owned_artist_detail") ||
                        subtitle.contains("Artist", ignoreCase = true) ||
                        subtitle.contains("Artista", ignoreCase = true) ||
                        subtitle.contains("listener", ignoreCase = true) ||
                        subtitle.contains("subscriber", ignoreCase = true)
                    if (isArtistCard) {
                        extractHighResAvatarFromRenderer(card)?.let { return it }
                    }
                }
                if (jsonObj.has("musicResponsiveListItemRenderer")) {
                    val renderer = jsonObj.getJSONObject("musicResponsiveListItemRenderer")
                    val watchVideoId = renderer.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("watchEndpoint")
                        ?.optString("videoId")
                        .orEmpty()
                    if (watchVideoId.isBlank()) {
                        val thumb = extractHighResAvatarFromRenderer(renderer)
                        if (!thumb.isNullOrBlank()) return thumb
                    }
                }
                val keys = jsonObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val child = jsonObj.get(key)
                    val found = extractFirstArtistAvatarUrl(child)
                    if (!found.isNullOrBlank()) return found
                }
            }
            is JSONArray -> {
                for (i in 0 until jsonObj.length()) {
                    val child = jsonObj.get(i)
                    val found = extractFirstArtistAvatarUrl(child)
                    if (!found.isNullOrBlank()) return found
                }
            }
        }
        return null
    }

    private fun extractHighResAvatarFromRenderer(renderer: JSONObject): String? {
        try {
            val thumbs = extractThumbUrlListFromRenderer(renderer)
            if (thumbs != null && thumbs.length() > 0) {
                for (i in thumbs.length() - 1 downTo 0) {
                    val url = normalizeHighResImageUrl(thumbs.optJSONObject(i)?.optString("url"))
                    if (url != null) return url
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun stripAlbumIdPrefix(rawId: String): String = when {
        rawId.startsWith("ytm_alb_") -> rawId.removePrefix("ytm_alb_")
        rawId.startsWith("ytm_single_") -> rawId.removePrefix("ytm_single_")
        rawId.startsWith("ytm_pl_") -> rawId.removePrefix("ytm_pl_")
        else -> rawId
    }

    private fun buildAlbumCacheKey(albumId: String, albumTitle: String, artistName: String): String {
        val cleanId = stripAlbumIdPrefix(albumId).trim()
        if (cleanId.startsWith("MPREb_") || cleanId.startsWith("VL") || cleanId.startsWith("OLAK5uy_")) {
            return "id:$cleanId"
        }
        return "q:${albumTitle.lowercase(Locale.ROOT).trim()}|${artistName.lowercase(Locale.ROOT).trim()}"
    }

    fun peekAlbumDetail(
        albumId: String,
        albumTitle: String,
        artistName: String
    ): AlbumDetailProfile? {
        if (albumTitle.isBlank() && albumId.isBlank()) return null
        val now = clockMs()
        val key = buildAlbumCacheKey(albumId, albumTitle, artistName)
        albumDetailCache[key]?.takeIf { now < it.expiresAtMs }?.let { return it.value }
        val queryKey = "q:${albumTitle.lowercase(Locale.ROOT).trim()}|${artistName.lowercase(Locale.ROOT).trim()}"
        return albumDetailCache[queryKey]?.takeIf { now < it.expiresAtMs }?.value
    }

    suspend fun getAlbumDetail(
        albumId: String,
        albumTitle: String,
        artistName: String,
        fallbackCover: String? = null
    ): AlbumDetailProfile? = withContext(Dispatchers.IO) {
        if (albumTitle.isBlank() && albumId.isBlank()) return@withContext null

        peekAlbumDetail(albumId, albumTitle, artistName)?.let { return@withContext it }

        val cacheKey = buildAlbumCacheKey(albumId, albumTitle, artistName)
        val deferred = inFlightAlbumDetails.computeIfAbsent(cacheKey) {
            serviceScope.async(start = CoroutineStart.LAZY) {
                try {
                    fetchAlbumDetailUncached(albumId, albumTitle, artistName, fallbackCover)?.also { profile ->
                        cacheAlbumDetail(cacheKey, albumTitle, artistName, profile)
                    }
                } finally {
                    inFlightAlbumDetails.remove(cacheKey)
                }
            }
        }
        deferred.await()
    }

    private fun cacheAlbumDetail(
        primaryKey: String,
        inputTitle: String,
        inputArtist: String,
        profile: AlbumDetailProfile
    ) {
        val now = clockMs()
        val entry = TimedCacheEntry(profile, now, now + DETAIL_CACHE_TTL_MS)
        if (albumDetailCache.size > MAX_DETAIL_CACHE_ENTRIES) {
            albumDetailCache.entries
                .sortedBy { it.value.cachedAtMs }
                .take(albumDetailCache.size - MAX_DETAIL_CACHE_ENTRIES)
                .forEach { albumDetailCache.remove(it.key) }
        }
        albumDetailCache[primaryKey] = entry
        val resolvedIdKey = buildAlbumCacheKey(profile.id, profile.title, profile.artist)
        albumDetailCache[resolvedIdKey] = entry
        val inputQueryKey = "q:${inputTitle.lowercase(Locale.ROOT).trim()}|${inputArtist.lowercase(Locale.ROOT).trim()}"
        albumDetailCache[inputQueryKey] = entry
        val resolvedQueryKey = "q:${profile.title.lowercase(Locale.ROOT).trim()}|${profile.artist.lowercase(Locale.ROOT).trim()}"
        albumDetailCache[resolvedQueryKey] = entry
    }

    private fun fetchAlbumDetailUncached(
        albumId: String,
        albumTitle: String,
        artistName: String,
        fallbackCover: String?
    ): AlbumDetailProfile? {
        try {
            var browseId = stripAlbumIdPrefix(albumId)

            if (!browseId.startsWith("MPREb_") && !browseId.startsWith("VL") && !browseId.startsWith("OLAK5uy_")) {
                val searchQuery = "$artistName $albumTitle".trim()
                val searchAlbums = executeInnerTubeAlbumSearch(searchQuery, strictlyAlbumsOnly = false)
                val match = searchAlbums.firstOrNull {
                    it.title.equals(albumTitle, ignoreCase = true)
                } ?: searchAlbums.firstOrNull()
                if (match != null) {
                    browseId = stripAlbumIdPrefix(match.id)
                }
            }

            if (browseId.isBlank() || browseId.startsWith("local_")) {
                return null
            }

            if (browseId.startsWith("OLAK5uy_") || browseId.startsWith("RDCLAK")) {
                browseId = "VL$browseId"
            }

            val payload = JSONObject().apply {
                put("context", buildWebRemixContextJson())
                put("browseId", browseId)
            }

            val request = buildWebRemixRequestBuilder(browseUrl, payload).build()

            val body = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string() ?: return null
            }
            val json = JSONObject(body)

            var resolvedTitle = albumTitle
            var resolvedArtist = artistName
            var resolvedYear = 0
            var description = ""
            var coverUrl = fallbackCover ?: ""
            var secondSubtitle = ""

            val headerRenderer = findHeaderRendererRecursive(json)
            if (headerRenderer != null) {
                val titleObj = headerRenderer.optJSONObject("title")
                val t = extractAllRunsText(titleObj)
                if (t.isNotBlank()) resolvedTitle = t

                val straplineObj = headerRenderer.optJSONObject("straplineTextOne")
                val a = extractAllRunsText(straplineObj)
                if (a.isNotBlank()) resolvedArtist = a

                val subObj = headerRenderer.optJSONObject("subtitle")
                val sub = extractAllRunsText(subObj)
                val y = Regex("""\b(19\d\d|20\d\d)\b""").find(sub)?.value?.toIntOrNull()
                if (y != null) resolvedYear = y

                val secSubObj = headerRenderer.optJSONObject("secondSubtitle")
                secondSubtitle = extractAllRunsText(secSubObj)

                val descObj = headerRenderer.optJSONObject("description")
                    ?.optJSONObject("musicDescriptionShelfRenderer")
                    ?.optJSONObject("description")
                val d = extractAllRunsText(descObj)
                if (d.isNotBlank()) description = cleanArtistBio(d)

                val thumb = extractHighResAvatarFromRenderer(headerRenderer)
                if (!thumb.isNullOrBlank()) coverUrl = thumb
            }

            val cleanBrowseId = browseId.removePrefix("VL")
            val isPlaylistBrowse = !cleanBrowseId.startsWith("MPREb_") && !cleanBrowseId.startsWith("OLAK5uy_")
            val tracks = mutableListOf<TrackItem>()
            val seenVideoIds = mutableSetOf<String>()
            extractAlbumTracks(
                jsonObj = json,
                albumTitle = resolvedTitle,
                defaultArtist = resolvedArtist,
                albumCover = coverUrl,
                results = tracks,
                seenVideoIds = seenVideoIds,
                isPlaylistBrowse = isPlaylistBrowse
            )

            val otherVersions = mutableListOf<AlbumItem>()
            val otherAlbums = mutableListOf<AlbumItem>()
            extractAlbumOtherReleases(json, resolvedArtist, otherVersions, otherAlbums)

            if (otherAlbums.isEmpty() && resolvedArtist.isNotBlank()) {
                val artistAlbums = executeInnerTubeAlbumSearch(resolvedArtist, strictlyAlbumsOnly = true)
                otherAlbums.addAll(
                    artistAlbums.filterNot { it.title.equals(resolvedTitle, ignoreCase = true) }.take(10)
                )
            }

            return AlbumDetailProfile(
                id = "ytm_alb_$browseId",
                title = resolvedTitle,
                artist = resolvedArtist,
                coverArtUri = coverUrl,
                releaseYear = resolvedYear,
                albumInformation = secondSubtitle.ifBlank { if (resolvedYear > 0) "$resolvedYear" else "" },
                description = description,
                tracks = tracks,
                otherVersions = otherVersions,
                otherAlbums = otherAlbums
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            safeLog("Error fetching album detail for '$albumTitle'", e)
            return null
        }
    }

    private fun findHeaderRendererRecursive(jsonObj: Any): JSONObject? {
        when (jsonObj) {
            is JSONObject -> {
                if (jsonObj.has("musicResponsiveHeaderRenderer")) {
                    return jsonObj.getJSONObject("musicResponsiveHeaderRenderer")
                }
                if (jsonObj.has("musicDetailHeaderRenderer")) {
                    return jsonObj.getJSONObject("musicDetailHeaderRenderer")
                }
                val keys = jsonObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val child = jsonObj.get(key)
                    val found = findHeaderRendererRecursive(child)
                    if (found != null) return found
                }
            }
            is JSONArray -> {
                for (i in 0 until jsonObj.length()) {
                    val found = findHeaderRendererRecursive(jsonObj.get(i))
                    if (found != null) return found
                }
            }
        }
        return null
    }

    private fun extractAlbumTracks(
        jsonObj: Any,
        albumTitle: String,
        defaultArtist: String,
        albumCover: String,
        results: MutableList<TrackItem>,
        seenVideoIds: MutableSet<String>,
        isPlaylistBrowse: Boolean = false
    ) {
        when (jsonObj) {
            is JSONObject -> {
                if (jsonObj.has("musicResponsiveListItemRenderer")) {
                    val renderer = jsonObj.getJSONObject("musicResponsiveListItemRenderer")
                    val rawPlaylistVideoId = renderer.optJSONObject("playlistItemData")?.optString("videoId")?.trim().orEmpty()
                    val videoId = extractPreferredAudioVideoId(renderer)
                    if (!videoId.isNullOrBlank() && seenVideoIds.add(videoId)) {
                        val flexCols = renderer.optJSONArray("flexColumns")
                        val titleCol = flexCols?.optJSONObject(0)
                            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                            ?.optJSONObject("text")
                        val title = extractAllRunsText(titleCol).trim()

                        val col1Meta = if (flexCols != null && flexCols.length() > 1) {
                            parseMetadataRuns(
                                flexColumn = flexCols.optJSONObject(1),
                                defaultArtist = defaultArtist,
                                defaultAlbum = if (isPlaylistBrowse) "" else albumTitle
                            )
                        } else {
                            ParsedMetadataRuns(defaultArtist, if (isPlaylistBrowse) "" else albumTitle, 0L, "", 0L)
                        }

                        val col2Text = if (flexCols != null && flexCols.length() > 2) {
                            extractRunsText(flexCols.optJSONObject(2)).trim()
                        } else ""

                        val resolvedAlbum = if (isPlaylistBrowse) {
                            when {
                                col1Meta.album.isNotBlank() && !isPlayCountLabel(col1Meta.album) -> col1Meta.album
                                col2Text.isNotBlank() &&
                                    !isPlayCountLabel(col2Text) &&
                                    !DURATION_REGEX.matches(col2Text) &&
                                    !YEAR_REGEX.matches(col2Text) &&
                                    !isMetadataBadgeLabel(col2Text) -> col2Text
                                else -> "Single"
                            }
                        } else {
                            albumTitle
                        }

                        var durationMs = 210000L
                        val fixedCols = renderer.optJSONArray("fixedColumns")
                        val durCol = fixedCols?.optJSONObject(0)
                            ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")
                            ?.optJSONObject("text")
                        val durText = extractAllRunsText(durCol).trim()
                        if (durText.contains(":")) {
                            durationMs = parseDuration(durText)
                        } else if (col1Meta.durationMs > 0L) {
                            durationMs = col1Meta.durationMs
                        } else if (DURATION_REGEX.matches(col2Text)) {
                            durationMs = parseDuration(col2Text)
                        }

                        var rowCover = extractThumbUrlFromObject(
                            renderer.optJSONObject("thumbnail")
                                ?.optJSONObject("musicThumbnailRenderer")
                                ?.optJSONObject("thumbnail")
                        ).orEmpty()
                        if (isPlaylistBrowse &&
                            rawPlaylistVideoId.isNotBlank() &&
                            rawPlaylistVideoId != videoId &&
                            rowCover.contains("i.ytimg.com/vi/", ignoreCase = true)
                        ) {
                            rowCover = ""
                        }
                        val finalCover = if (isPlaylistBrowse) {
                            rowCover.ifBlank { albumCover }.ifBlank { null }
                        } else {
                            albumCover.ifBlank { rowCover }.ifBlank { null }
                        }

                        if (title.isNotBlank()) {
                            results.add(
                                TrackItem(
                                    id = "ytm_$videoId",
                                    title = title,
                                    artist = col1Meta.artist.ifBlank { defaultArtist },
                                    album = resolvedAlbum,
                                    durationMs = durationMs,
                                    artworkUri = finalCover,
                                    audioUrl = "https://www.youtube.com/watch?v=$videoId",
                                    playCount = col1Meta.playCount,
                                    isExplicit = hasExplicitBadge(renderer)
                                )
                            )
                        }
                    }
                    return
                }

                // If this is a secondary carousel (Other versions / Releases for you), don't recurse into it for tracks
                if (jsonObj.has("musicCarouselShelfRenderer")) {
                    return
                }

                val keys = jsonObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val child = jsonObj.get(key)
                    extractAlbumTracks(child, albumTitle, defaultArtist, albumCover, results, seenVideoIds, isPlaylistBrowse)
                }
            }
            is JSONArray -> {
                for (i in 0 until jsonObj.length()) {
                    extractAlbumTracks(jsonObj.get(i), albumTitle, defaultArtist, albumCover, results, seenVideoIds, isPlaylistBrowse)
                }
            }
        }
    }

    private fun extractAlbumOtherReleases(
        jsonObj: Any,
        artistName: String,
        otherVersions: MutableList<AlbumItem>,
        otherAlbums: MutableList<AlbumItem>
    ) {
        when (jsonObj) {
            is JSONObject -> {
                if (jsonObj.has("musicCarouselShelfRenderer")) {
                    val carousel = jsonObj.getJSONObject("musicCarouselShelfRenderer")
                    val headerObj = carousel.optJSONObject("header")
                        ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                    val shelfTitle = extractAllRunsText(headerObj?.optJSONObject("title")).lowercase()

                    val isOtherVersions = shelfTitle.contains("version") || 
                        shelfTitle.contains("versión") || 
                        shelfTitle.contains("versiones")

                    val isOtherAlbums = shelfTitle.contains("release") || 
                        shelfTitle.contains("other") ||
                        shelfTitle.contains("more") || 
                        shelfTitle.contains("para ti") || 
                        shelfTitle.contains("lanzamiento") ||
                        shelfTitle.contains("más de")

                    val targetList = if (isOtherVersions) otherVersions else if (isOtherAlbums) otherAlbums else null
                    if (targetList != null) {
                        val contents = carousel.optJSONArray("contents")
                        if (contents != null) {
                            for (i in 0 until contents.length()) {
                                val item = contents.optJSONObject(i) ?: continue
                                val twoRow = item.optJSONObject("musicTwoRowItemRenderer") ?: continue

                                val title = extractAllRunsText(twoRow.optJSONObject("title")).trim()
                                if (title.isBlank()) continue

                                val subtitle = extractAllRunsText(twoRow.optJSONObject("subtitle")).trim()
                                val navEp = twoRow.optJSONObject("navigationEndpoint")
                                val browseId = navEp?.optJSONObject("browseEndpoint")?.optString("browseId") ?: ""
                                if (browseId.isBlank() || browseId.startsWith("UC")) continue

                                if (twoRow.optString("aspectRatio").contains("RECTANGLE", ignoreCase = true) ||
                                    subtitle.contains("views", ignoreCase = true) ||
                                    subtitle.contains("reproducc", ignoreCase = true)) {
                                    continue
                                }

                                val thumbObj = twoRow.optJSONObject("thumbnailRenderer")
                                    ?.optJSONObject("musicThumbnailRenderer")
                                    ?.optJSONObject("thumbnail")
                                    ?.optJSONArray("thumbnails")
                                var cover = ""
                                if (thumbObj != null && thumbObj.length() > 0) {
                                    cover = thumbObj.getJSONObject(thumbObj.length() - 1).optString("url")
                                    if (cover.isNotBlank()) {
                                        cover = cover.replace(Regex("=w\\d+-h\\d+.*"), "=w540-h540-p-l90-rj")
                                            .replace(Regex("=s\\d+.*"), "=s540-c-k-c0x00ffffff-no-rj-mo")
                                    }
                                }

                                val year = Regex("""\b(19\d\d|20\d\d)\b""").find(subtitle)?.value?.toIntOrNull() ?: 2024
                                val albArtist = if (subtitle.isNotBlank() && !subtitle.matches(Regex("""\b(19\d\d|20\d\d)\b"""))) {
                                    subtitle.replace(Regex("""•\s*(19\d\d|20\d\d).*"""), "").trim().ifBlank { artistName }
                                } else {
                                    artistName
                                }

                                targetList.addUniqueAlbum(
                                    AlbumItem(
                                        id = "ytm_alb_$browseId",
                                        title = title,
                                        artist = albArtist,
                                        coverUri = cover,
                                        year = year,
                                        tracksCount = 10
                                    )
                                )
                            }
                        }
                    }
                } else {
                    val keys = jsonObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val child = jsonObj.get(key)
                        extractAlbumOtherReleases(child, artistName, otherVersions, otherAlbums)
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until jsonObj.length()) {
                    extractAlbumOtherReleases(jsonObj.get(i), artistName, otherVersions, otherAlbums)
                }
            }
        }
    }

    private fun normalizeArtistCacheKey(artistName: String): String {
        val primary = artistName.split(",", ";", "/").firstOrNull()?.trim().orEmpty()
        return primary.lowercase(Locale.ROOT)
    }

    fun peekArtistProfile(artistName: String): ArtistDetailProfile? {
        val key = normalizeArtistCacheKey(artistName)
        if (key.isBlank() || key == "unknown artist") return null
        return artistProfileCache[key]?.takeIf { clockMs() < it.expiresAtMs }?.value
    }

    fun prefetchArtistProfile(artistName: String) {
        val key = normalizeArtistCacheKey(artistName)
        if (key.isBlank() || key == "unknown artist") return
        if (peekArtistProfile(artistName) != null) return
        serviceScope.launch {
            runCatching { getArtistProfile(artistName) }
        }
    }

    suspend fun getArtistProfile(artistName: String): ArtistDetailProfile? = withContext(Dispatchers.IO) {
        val primaryArtist = artistName.split(",", ";", "/").firstOrNull()?.trim() ?: return@withContext null
        if (primaryArtist.isBlank() || primaryArtist.equals("Unknown Artist", ignoreCase = true)) {
            return@withContext null
        }

        val cacheKey = primaryArtist.lowercase(Locale.ROOT)
        artistProfileCache[cacheKey]?.takeIf { clockMs() < it.expiresAtMs }?.let { return@withContext it.value }

        val deferred = inFlightArtistProfiles.computeIfAbsent(cacheKey) {
            serviceScope.async(start = CoroutineStart.LAZY) {
                try {
                    fetchArtistProfileUncached(primaryArtist)?.also { profile ->
                        val now = clockMs()
                        val entry = TimedCacheEntry(profile, now, now + DETAIL_CACHE_TTL_MS)
                        if (artistProfileCache.size > MAX_DETAIL_CACHE_ENTRIES) {
                            artistProfileCache.entries
                                .sortedBy { it.value.cachedAtMs }
                                .take(artistProfileCache.size - MAX_DETAIL_CACHE_ENTRIES)
                                .forEach { artistProfileCache.remove(it.key) }
                        }
                        artistProfileCache[cacheKey] = entry
                        val officialKey = profile.name.trim().lowercase(Locale.ROOT)
                        if (officialKey.isNotBlank() && officialKey != cacheKey) {
                            artistProfileCache[officialKey] = entry
                        }
                    }
                } finally {
                    inFlightArtistProfiles.remove(cacheKey)
                }
            }
        }
        deferred.await()
    }

    private suspend fun fetchArtistProfileUncached(primaryArtist: String): ArtistDetailProfile? {
        try {
            val (browseId, searchAvatar, officialName) = findArtistBrowseIdAndAvatar(primaryArtist)

            if (browseId.isNullOrBlank()) {
                return coroutineScope {
                    val catalogDeferred = async { searchCatalog(primaryArtist) }
                    val albumsDeferred = async { executeInnerTubeAlbumSearch(primaryArtist, strictlyAlbumsOnly = true) }
                    val videosDeferred = async { searchArtistOfficialVideos(primaryArtist, limit = 8) }
                    val fallbackCatalog = catalogDeferred.await()
                    val directAlbums = albumsDeferred.await()
                    val fallbackVideos = videosDeferred.await()
                    val albumsList = directAlbums.ifEmpty { fallbackCatalog.albums }
                    val latest = selectArtistLatestRelease(albumsList, emptyList(), primaryArtist)
                    ArtistDetailProfile(
                        name = primaryArtist,
                        avatarUri = fallbackCatalog.artists.firstOrNull()?.avatarUri ?: "",
                        bannerImageUri = fallbackCatalog.artists.firstOrNull()?.avatarUri ?: "",
                        metadataSubtitle = "YouTube Music",
                        topTracks = fallbackCatalog.songs.take(10),
                        combinedAlbums = albumsList,
                        similarArtists = fallbackCatalog.artists.filterNot { it.name.equals(primaryArtist, ignoreCase = true) },
                        latestRelease = latest,
                        latestReleaseTypeBadge = latest?.releaseTypeBadgeText.orEmpty(),
                        latestReleaseDateText = latest?.formattedReleaseDate.orEmpty(),
                        officialVideos = fallbackVideos
                    )
                }
            }

            val payload = JSONObject().apply {
                put("context", buildWebRemixContextJson())
                put("browseId", browseId)
            }

            val request = buildWebRemixRequestBuilder(browseUrl, payload).build()

            val body = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string() ?: return null
            }
            val json = JSONObject(body)

            val resolvedName = officialName ?: primaryArtist
            var bannerUri = ""
            var subscribersText = ""
            var monthlyListenersText = ""
            var biography = ""

            val header = json.optJSONObject("header")
            val immHeader = header?.optJSONObject("musicImmersiveHeaderRenderer")
                ?: header?.optJSONObject("musicVisualHeaderRenderer")

            if (immHeader != null) {
                bannerUri = extractBannerUrlFromHeader(immHeader)

                val mlObj = immHeader.optJSONObject("monthlyListenerCount")
                monthlyListenersText = extractAllRunsText(mlObj)

                val subBtn = immHeader.optJSONObject("subscriptionButton")
                    ?.optJSONObject("subscribeButtonRenderer")
                val scObj = subBtn?.optJSONObject("subscriberCountText")
                    ?: subBtn?.optJSONObject("shortSubscriberCountText")
                subscribersText = extractAllRunsText(scObj)

                val descObj = immHeader.optJSONObject("description")
                biography = cleanArtistBio(extractAllRunsText(descObj))
            }

            val topTracks = mutableListOf<TrackItem>()
            val studioAlbums = mutableListOf<AlbumItem>()
            val singlesAndEps = mutableListOf<AlbumItem>()
            val artistPlaylists = mutableListOf<AlbumItem>()
            val similarArtists = mutableListOf<ArtistItem>()
            val officialVideos = mutableListOf<TrackItem>()

            val contents = json.optJSONObject("contents")
                ?.optJSONObject("singleColumnBrowseResultsRenderer")
                ?.optJSONArray("tabs")
                ?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")

            if (contents != null) {
                for (i in 0 until contents.length()) {
                    val section = contents.optJSONObject(i) ?: continue

                    // A. Top Songs
                    if (section.has("musicShelfRenderer")) {
                        val shelf = section.getJSONObject("musicShelfRenderer")
                        val items = shelf.optJSONArray("contents")
                        if (items != null) {
                            for (j in 0 until items.length()) {
                                val itemObj = items.optJSONObject(j) ?: continue
                                val renderer = itemObj.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                                parseArtistTopTrack(renderer, resolvedName)?.let { topTracks.add(it) }
                            }
                        }
                    }

                    // B. Carousels (Albums, Singles, Playlists, Videos, Fans might also like)
                    if (section.has("musicCarouselShelfRenderer")) {
                        val carousel = section.getJSONObject("musicCarouselShelfRenderer")
                        val headerObj = carousel.optJSONObject("header")
                            ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                        val shelfTitle = extractAllRunsText(headerObj?.optJSONObject("title")).lowercase()

                        parseArtistCarouselShelf(
                            carousel = carousel,
                            shelfTitle = shelfTitle,
                            artistName = resolvedName,
                            studioAlbums = studioAlbums,
                            singlesAndEps = singlesAndEps,
                            artistPlaylists = artistPlaylists,
                            similarArtists = similarArtists,
                            officialVideos = officialVideos
                        )
                    }

                    // C. Biography fallback
                    if (section.has("musicDescriptionShelfRenderer")) {
                        val descShelf = section.getJSONObject("musicDescriptionShelfRenderer")
                        if (biography.isBlank()) {
                            biography = cleanArtistBio(extractAllRunsText(descShelf.optJSONObject("description")))
                        }
                        if (monthlyListenersText.isBlank()) {
                            monthlyListenersText = extractAllRunsText(descShelf.optJSONObject("subheader"))
                        }
                    }
                }
            }

            if (officialVideos.isEmpty() && contents != null) {
                officialVideos.addAll(searchArtistOfficialVideos(resolvedName, limit = 8))
            }

            val latestRelease = selectArtistLatestRelease(
                studioAlbums = studioAlbums,
                singlesAndEps = singlesAndEps,
                artistName = resolvedName
            )

            val finalAvatar = searchAvatar ?: bannerUri
            val finalBanner = if (bannerUri.isNotBlank()) bannerUri else finalAvatar

            return ArtistDetailProfile(
                name = resolvedName,
                avatarUri = finalAvatar,
                bannerImageUri = finalBanner,
                subscribersCountText = subscribersText,
                monthlyListenersText = monthlyListenersText,
                biography = biography,
                metadataSubtitle = if (monthlyListenersText.isNotBlank()) monthlyListenersText else "YouTube Music Official",
                topTracks = topTracks.take(15),
                combinedAlbums = studioAlbums,
                singlesAndEps = singlesAndEps,
                artistPlaylists = artistPlaylists,
                similarArtists = similarArtists,
                latestRelease = latestRelease,
                latestReleaseTypeBadge = latestRelease?.releaseTypeBadgeText.orEmpty(),
                latestReleaseDateText = latestRelease?.formattedReleaseDate.orEmpty(),
                officialVideos = officialVideos.take(12)
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            safeLog("Error fetching artist profile for '$primaryArtist'", e)
            return null
        }
    }

    private fun findArtistBrowseIdAndAvatar(artistName: String): Triple<String?, String?, String?> {
        val payload = JSONObject().apply {
            put("context", buildWebRemixContextJson())
            put("query", artistName)
            put("params", ARTISTS_PARAM)
        }

        val request = buildWebRemixRequestBuilder(searchUrl, payload).build()

        val body = httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return Triple(null, null, null)
            response.body?.string() ?: return Triple(null, null, null)
        }
        val json = JSONObject(body)

        return findFirstArtistBrowseIdRecursive(json)
    }

    private fun findFirstArtistBrowseIdRecursive(jsonObj: Any): Triple<String?, String?, String?> {
        when (jsonObj) {
            is JSONObject -> {
                if (jsonObj.has("musicCardShelfRenderer")) {
                    val card = jsonObj.getJSONObject("musicCardShelfRenderer")
                    val titleObj = card.optJSONObject("title")
                    val runs = titleObj?.optJSONArray("runs")
                    val run0 = runs?.optJSONObject(0)
                    val browseId = run0?.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("browseEndpoint")
                        ?.optString("browseId")
                    if (!browseId.isNullOrBlank() && (browseId.startsWith("UC") || browseId.startsWith("FEmusic_library_privately_owned_artist_detail"))) {
                        val name = run0.optString("text")
                        val avatarUrl = extractHighResAvatarFromRenderer(card)
                        return Triple(browseId, avatarUrl, if (name.isNotBlank()) name else null)
                    }
                }
                if (jsonObj.has("musicResponsiveListItemRenderer")) {
                    val renderer = jsonObj.getJSONObject("musicResponsiveListItemRenderer")
                    val navEp = renderer.optJSONObject("navigationEndpoint")
                    val browseEp = navEp?.optJSONObject("browseEndpoint")
                    val browseId = browseEp?.optString("browseId")

                    if (!browseId.isNullOrBlank() && (browseId.startsWith("UC") || browseId.startsWith("FEmusic_library_privately_owned_artist_detail"))) {
                        val avatarUrl = extractHighResAvatarFromRenderer(renderer)
                        val flexCols = renderer.optJSONArray("flexColumns")
                        val col0 = flexCols?.optJSONObject(0)
                            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                            ?.optJSONObject("text")
                        val name = extractAllRunsText(col0)
                        return Triple(browseId, avatarUrl, if (name.isNotBlank()) name else null)
                    }
                }
                val keys = jsonObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val child = jsonObj.get(key)
                    val result = findFirstArtistBrowseIdRecursive(child)
                    if (result.first != null) return result
                }
            }
            is JSONArray -> {
                for (i in 0 until jsonObj.length()) {
                    val result = findFirstArtistBrowseIdRecursive(jsonObj.get(i))
                    if (result.first != null) return result
                }
            }
        }
        return Triple(null, null, null)
    }

    private fun extractBannerUrlFromHeader(header: JSONObject): String {
        try {
            val thumbObj = header.optJSONObject("thumbnail")
            val musicThumb = thumbObj?.optJSONObject("musicThumbnailRenderer")
            val thumb = musicThumb?.optJSONObject("thumbnail")
            val thumbs = thumb?.optJSONArray("thumbnails")
            if (thumbs != null && thumbs.length() > 0) {
                var bestUrl = ""
                var bestWidth = 0
                for (i in 0 until thumbs.length()) {
                    val item = thumbs.getJSONObject(i)
                    val w = item.optInt("width", 0)
                    val u = item.optString("url")
                    if (w >= bestWidth && u.isNotBlank()) {
                        bestWidth = w
                        bestUrl = u
                    }
                }
                if (bestUrl.isNotBlank()) {
                    return bestUrl.replace(Regex("=w\\d+-h\\d+.*"), "=w1920-h800-p-l90-rj")
                }
            }
        } catch (_: Exception) {}
        return ""
    }

    private fun extractAllRunsText(obj: JSONObject?): String {
        if (obj == null) return ""
        val runs = obj.optJSONArray("runs")
        if (runs != null) {
            val sb = StringBuilder()
            for (i in 0 until runs.length()) {
                val run = runs.optJSONObject(i)
                sb.append(run?.optString("text") ?: "")
            }
            val joined = sb.toString().trim()
            if (joined.isNotEmpty()) return joined
        }
        val simpleText = obj.optString("simpleText", "").trim()
        if (simpleText.isNotEmpty()) return simpleText
        return obj.optString("text", "").trim()
    }

    private fun cleanArtistBio(raw: String): String {
        if (raw.isBlank()) return ""
        return raw.replace(Regex("\\s*From Wikipedia.*", RegexOption.IGNORE_CASE), "").trim()
    }

    internal fun parseArtistTopTrack(renderer: JSONObject, artistName: String): TrackItem? {
        val videoId = extractPreferredAudioVideoId(renderer) ?: return null
        val flexCols = renderer.optJSONArray("flexColumns") ?: return null
        if (flexCols.length() == 0) return null

        val col0 = flexCols.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
        val title = extractAllRunsText(col0).trim().ifBlank { "Unknown Title" }

        val col1Meta = if (flexCols.length() > 1) {
            parseMetadataRuns(
                flexColumn = flexCols.optJSONObject(1),
                defaultArtist = artistName,
                defaultAlbum = ""
            )
        } else {
            ParsedMetadataRuns(artistName, "", 0L, "", 0L)
        }

        val col2Text = if (flexCols.length() > 2) extractRunsText(flexCols.optJSONObject(2)).trim() else ""
        val col2Meta = if (flexCols.length() > 2) {
            parseMetadataRuns(
                flexColumn = flexCols.optJSONObject(2),
                defaultArtist = "",
                defaultAlbum = ""
            )
        } else {
            ParsedMetadataRuns("", "", 0L, "", 0L)
        }

        val col2AlbumCandidate = when {
            col2Meta.album.isNotBlank() -> col2Meta.album
            col2Text.isNotBlank() &&
                !isPlayCountLabel(col2Text) &&
                !DURATION_REGEX.matches(col2Text) &&
                !YEAR_REGEX.matches(col2Text) &&
                !isMetadataBadgeLabel(col2Text) -> col2Text
            else -> ""
        }

        val album = when {
            col2AlbumCandidate.isNotBlank() -> col2AlbumCandidate
            col1Meta.album.isNotBlank() && !isPlayCountLabel(col1Meta.album) -> col1Meta.album
            else -> "Single"
        }

        val fixedCols = renderer.optJSONArray("fixedColumns")
        val fixedDurText = extractAllRunsText(
            fixedCols?.optJSONObject(0)
                ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")
                ?.optJSONObject("text")
        ).trim()
        val col3Text = if (flexCols.length() > 3) extractRunsText(flexCols.optJSONObject(3)).trim() else ""

        val durationMs = when {
            fixedDurText.contains(":") -> parseDuration(fixedDurText)
            DURATION_REGEX.matches(col3Text) -> parseDuration(col3Text)
            col2Meta.durationMs > 0L -> col2Meta.durationMs
            DURATION_REGEX.matches(col2Text) -> parseDuration(col2Text)
            col1Meta.durationMs > 0L -> col1Meta.durationMs
            else -> 210000L
        }

        val playCount = maxOf(col1Meta.playCount, col2Meta.playCount)
        val thumb = extractHighResThumbnail(renderer, videoId)

        return TrackItem(
            id = "ytm_$videoId",
            title = title,
            artist = col1Meta.artist.ifBlank { artistName },
            album = album,
            durationMs = durationMs,
            artworkUri = thumb,
            audioUrl = "https://www.youtube.com/watch?v=$videoId",
            playCount = playCount,
            isExplicit = hasExplicitBadge(renderer)
        )
    }

    private fun parsePlayCountNumber(text: String): Long {
        return try {
            val clean = text.replace(Regex("[^0-9.KkMmBb]"), "").trim()
            when {
                clean.endsWith("B", ignoreCase = true) -> (clean.dropLast(1).toDouble() * 1_000_000_000).toLong()
                clean.endsWith("M", ignoreCase = true) -> (clean.dropLast(1).toDouble() * 1_000_000).toLong()
                clean.endsWith("K", ignoreCase = true) -> (clean.dropLast(1).toDouble() * 1_000).toLong()
                else -> clean.replace(".", "").replace(",", "").toLongOrNull() ?: 0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    internal fun parseArtistCarouselShelf(
        carousel: JSONObject,
        shelfTitle: String,
        artistName: String,
        studioAlbums: MutableList<AlbumItem>,
        singlesAndEps: MutableList<AlbumItem>,
        artistPlaylists: MutableList<AlbumItem>,
        similarArtists: MutableList<ArtistItem>,
        officialVideos: MutableList<TrackItem> = mutableListOf()
    ) {
        val cleanShelfTitle = shelfTitle.lowercase().trim()

        val isVideoShelf = (cleanShelfTitle.contains("video") || cleanShelfTitle.contains("vídeo")) &&
            !cleanShelfTitle.contains("live") && !cleanShelfTitle.contains("vivo")

        val isAlbumShelf = (cleanShelfTitle.contains("album") || cleanShelfTitle.contains("álbum") || cleanShelfTitle.contains("discograph") || cleanShelfTitle.contains("discograf")) &&
            !cleanShelfTitle.contains("video") && !cleanShelfTitle.contains("vídeo") && !cleanShelfTitle.contains("single") && !cleanShelfTitle.contains("sencillo") && !cleanShelfTitle.contains("ep")

        val isSingleOrEpShelf = (cleanShelfTitle.contains("single") || cleanShelfTitle.contains("sencillo") || cleanShelfTitle.contains("ep")) &&
            !cleanShelfTitle.contains("album") && !cleanShelfTitle.contains("álbum") && !cleanShelfTitle.contains("video") && !cleanShelfTitle.contains("vídeo")

        val isPlaylistShelf = (cleanShelfTitle.contains("playlist") || cleanShelfTitle.contains("lista") || cleanShelfTitle.contains("featured") || cleanShelfTitle.contains("aparece")) &&
            !cleanShelfTitle.contains("video") && !cleanShelfTitle.contains("vídeo") && !cleanShelfTitle.contains("fan")

        val isSimilarShelf = cleanShelfTitle.contains("fan") || cleanShelfTitle.contains("similar") || cleanShelfTitle.contains("like") || cleanShelfTitle.contains("guste")

        if (!isAlbumShelf && !isSingleOrEpShelf && !isPlaylistShelf && !isSimilarShelf && !isVideoShelf) {
            return
        }

        val contents = carousel.optJSONArray("contents") ?: return
        for (i in 0 until contents.length()) {
            val item = contents.optJSONObject(i) ?: continue
            val twoRow = item.optJSONObject("musicTwoRowItemRenderer") ?: continue

            val titleObj = twoRow.optJSONObject("title")
            val title = extractAllRunsText(titleObj).trim()
            if (title.isBlank()) continue

            val subObj = twoRow.optJSONObject("subtitle")
            val subtitle = extractAllRunsText(subObj).trim()
            val navEp = twoRow.optJSONObject("navigationEndpoint")
            val browseEp = navEp?.optJSONObject("browseEndpoint")
            val browseId = browseEp?.optString("browseId") ?: ""
            val watchEp = navEp?.optJSONObject("watchEndpoint")

            val isVideo = watchEp != null ||
                twoRow.optString("aspectRatio").contains("RECTANGLE", ignoreCase = true) ||
                subtitle.contains("views", ignoreCase = true) ||
                subtitle.contains("reproducc", ignoreCase = true) ||
                subtitle.contains("visualizac", ignoreCase = true) ||
                subtitle.contains("vistas", ignoreCase = true)

            if (isVideoShelf) {
                val videoId = watchEp?.optString("videoId")?.trim().orEmpty()
                    .ifBlank { extractPreferredAudioVideoId(twoRow).orEmpty() }
                if (videoId.isBlank() || !VIDEO_ID_REGEX.matches(videoId)) continue

                val thumbObj = twoRow.optJSONObject("thumbnailRenderer")
                    ?.optJSONObject("musicThumbnailRenderer")
                    ?.optJSONObject("thumbnail")
                    ?.optJSONArray("thumbnails")
                var videoThumb = ""
                if (thumbObj != null && thumbObj.length() > 0) {
                    videoThumb = thumbObj.getJSONObject(thumbObj.length() - 1).optString("url")
                    if (videoThumb.isNotBlank()) {
                        videoThumb = videoThumb.replace(Regex("=w\\d+-h\\d+.*"), "=w640-h360-p-l90-rj")
                    }
                }
                if (videoThumb.isBlank()) {
                    videoThumb = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                }

                val views = parsePlayCountNumber(subtitle)
                val trackId = "ytm_video_$videoId"
                if (officialVideos.none { it.id == trackId }) {
                    officialVideos.add(
                        TrackItem(
                            id = trackId,
                            title = title,
                            artist = artistName,
                            album = "Official Video",
                            durationMs = 210_000L,
                            artworkUri = videoThumb,
                            audioUrl = "https://www.youtube.com/watch?v=$videoId",
                            playCount = views,
                            isExplicit = hasExplicitBadge(twoRow)
                        )
                    )
                }
                continue
            }

            if (isVideo) continue

            // Release browseId check: Albums / Singles / Playlists must have valid browseIds and cannot be artist channels ('UC')
            if (isAlbumShelf || isSingleOrEpShelf || isPlaylistShelf) {
                if (browseId.isBlank() || browseId.startsWith("UC")) {
                    continue
                }
            }

            val thumbObj = twoRow.optJSONObject("thumbnailRenderer")
                ?.optJSONObject("musicThumbnailRenderer")
                ?.optJSONObject("thumbnail")
                ?.optJSONArray("thumbnails")
            var coverUrl = ""
            if (thumbObj != null && thumbObj.length() > 0) {
                coverUrl = thumbObj.getJSONObject(thumbObj.length() - 1).optString("url")
                if (coverUrl.isNotBlank()) {
                    coverUrl = coverUrl.replace(Regex("=w\\d+-h\\d+.*"), "=w540-h540-p-l90-rj")
                        .replace(Regex("=s\\d+.*"), "=s540-c-k-c0x00ffffff-no-rj-mo")
                }
            }

            val (year, releaseMonth, releaseDateText) = parseReleaseYearAndMonth(subtitle)

            when {
                isSimilarShelf -> {
                    similarArtists.addUniqueArtist(
                        ArtistItem(
                            id = "ytm_sim_${browseId.ifBlank { "${i}_${title.trim().lowercase().hashCode()}" }}",
                            name = title,
                            avatarUri = coverUrl,
                            genre = subtitle.ifBlank { "Similar Artist" },
                            monthlyListeners = "Trending"
                        )
                    )
                }
                isSingleOrEpShelf -> {
                    singlesAndEps.addUniqueAlbum(
                        AlbumItem(
                            id = "ytm_single_$browseId",
                            title = title,
                            artist = if (subtitle.isNotBlank() && !subtitle.contains("Single", ignoreCase = true) && !subtitle.contains("EP", ignoreCase = true)) subtitle else artistName,
                            coverUri = coverUrl,
                            year = year,
                            tracksCount = 1,
                            releaseMonth = releaseMonth,
                            releaseDateText = releaseDateText,
                            isSingleOrEp = true
                        )
                    )
                }
                isPlaylistShelf -> {
                    artistPlaylists.addUniqueAlbum(
                        AlbumItem(
                            id = "ytm_pl_$browseId",
                            title = title,
                            artist = if (subtitle.isNotBlank()) subtitle else artistName,
                            coverUri = coverUrl,
                            year = year,
                            tracksCount = 10
                        )
                    )
                }
                isAlbumShelf -> {
                    // Double check: if subtitle explicitly denotes Single / Sencillo, move to singles
                    if (subtitle.contains("Single", ignoreCase = true) || subtitle.contains("Sencillo", ignoreCase = true)) {
                        singlesAndEps.addUniqueAlbum(
                            AlbumItem(
                                id = "ytm_single_$browseId",
                                title = title,
                                artist = if (subtitle.isNotBlank() && !subtitle.contains("Single", ignoreCase = true)) subtitle else artistName,
                                coverUri = coverUrl,
                                year = year,
                                tracksCount = 1,
                                releaseMonth = releaseMonth,
                                releaseDateText = releaseDateText,
                                isSingleOrEp = true
                            )
                        )
                    } else {
                        studioAlbums.addUniqueAlbum(
                            AlbumItem(
                                id = "ytm_alb_$browseId",
                                title = title,
                                artist = if (subtitle.isNotBlank() && !subtitle.matches(Regex("""\b(19\d\d|20\d\d)\b"""))) {
                                    subtitle.replace(Regex("""•\s*(19\d\d|20\d\d).*"""), "").trim().ifBlank { artistName }
                                } else {
                                    artistName
                                },
                                coverUri = coverUrl,
                                year = year,
                                tracksCount = 10,
                                releaseMonth = releaseMonth,
                                releaseDateText = releaseDateText,
                                isSingleOrEp = false
                            )
                        )
                    }
                }
            }
        }
    }

    internal fun parseReleaseYearAndMonth(subtitle: String, fallbackYear: Int = 2024): Triple<Int, Int, String> {
        val year = Regex("""\b(19\d\d|20\d\d)\b""").find(subtitle)?.value?.toIntOrNull() ?: fallbackYear
        val lower = subtitle.lowercase(Locale.ROOT)
        val monthTokens = listOf(
            1 to listOf("jan", "ene", "enero", "january"),
            2 to listOf("feb", "febrero", "february"),
            3 to listOf("mar", "marzo", "march"),
            4 to listOf("abr", "apr", "abril", "april"),
            5 to listOf("may", "mayo"),
            6 to listOf("jun", "junio", "june"),
            7 to listOf("jul", "julio", "july"),
            8 to listOf("ago", "aug", "agosto", "august"),
            9 to listOf("sep", "sept", "septiembre", "september"),
            10 to listOf("oct", "octubre", "october"),
            11 to listOf("nov", "noviembre", "november"),
            12 to listOf("dic", "dec", "diciembre", "december")
        )
        var detectedMonth = 0
        for ((m, tokens) in monthTokens) {
            if (tokens.any { token -> Regex("""\b$token\b""").containsMatchIn(lower) }) {
                detectedMonth = m
                break
            }
        }
        if (detectedMonth == 0) {
            val isoMatch = Regex("""\b(?:19\d\d|20\d\d)[-/](0?[1-9]|1[0-2])\b""").find(lower)
            if (isoMatch != null) {
                detectedMonth = isoMatch.groupValues[1].toIntOrNull() ?: 0
            }
        }
        val dateText = if (detectedMonth in 1..12) {
            "${AlbumItem.monthNameEs(detectedMonth)} $year"
        } else {
            "$year"
        }
        return Triple(year, detectedMonth, dateText)
    }

    internal fun selectArtistLatestRelease(
        studioAlbums: List<AlbumItem>,
        singlesAndEps: List<AlbumItem>,
        artistName: String
    ): AlbumItem? {
        val allCandidates = mutableListOf<Pair<AlbumItem, Int>>()
        // YouTube Music lists newest releases first within each shelf; preserve shelf order as tie-breaker
        singlesAndEps.forEachIndexed { idx, single ->
            allCandidates.add(single.copy(isSingleOrEp = true) to (100 - idx.coerceAtMost(90)))
        }
        studioAlbums.forEachIndexed { idx, album ->
            allCandidates.add(album.copy(isSingleOrEp = false) to (95 - idx.coerceAtMost(90)))
        }
        if (allCandidates.isEmpty()) return null

        val best = allCandidates.maxWithOrNull(
            compareBy<Pair<AlbumItem, Int>> { it.first.year }
                .thenBy { it.first.releaseMonth }
                .thenBy { it.second }
        )?.first ?: return null

        // Derive a deterministic display month when YouTube Music only provides the year in the shelf subtitle
        val effectiveMonth = if (best.releaseMonth in 1..12) {
            best.releaseMonth
        } else {
            ((kotlin.math.abs(best.title.hashCode() + best.year) % 12) + 1)
        }
        val effectiveDateText = if (best.releaseDateText.isNotBlank() && best.releaseDateText.length > 4) {
            best.releaseDateText
        } else {
            "${AlbumItem.monthNameEs(effectiveMonth)} ${best.year}"
        }
        return best.copy(
            artist = best.artist.ifBlank { artistName },
            releaseMonth = effectiveMonth,
            releaseDateText = effectiveDateText
        )
    }

    internal fun searchArtistOfficialVideos(artistName: String, limit: Int = 8): List<TrackItem> {
        val cleanArtist = artistName.trim()
        if (cleanArtist.isBlank()) return emptyList()
        return try {
            val payload = JSONObject().apply {
                put("context", buildWebRemixContextJson())
                put("query", "$cleanArtist official music video")
                put("params", VIDEOS_PARAM)
            }
            val request = buildWebRemixRequestBuilder(searchUrl, payload).build()
            val body = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                response.body?.string() ?: return emptyList()
            }
            val json = JSONObject(body)
            val videos = mutableListOf<TrackItem>()
            val seenIds = mutableSetOf<String>()
            extractVideoItemsRecursive(json, cleanArtist, videos, seenIds)
            videos.take(limit)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun extractVideoItemsRecursive(
        node: Any,
        defaultArtist: String,
        results: MutableList<TrackItem>,
        seenVideoIds: MutableSet<String>
    ) {
        when (node) {
            is JSONObject -> {
                if (node.has("musicResponsiveListItemRenderer")) {
                    val renderer = node.getJSONObject("musicResponsiveListItemRenderer")
                    val videoId = renderer.optJSONObject("overlay")
                        ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                        ?.optJSONObject("content")
                        ?.optJSONObject("musicPlayButtonRenderer")
                        ?.optJSONObject("playNavigationEndpoint")
                        ?.optJSONObject("watchEndpoint")
                        ?.optString("videoId")
                        ?.takeIf { VIDEO_ID_REGEX.matches(it) }
                        ?: renderer.optJSONObject("navigationEndpoint")
                            ?.optJSONObject("watchEndpoint")
                            ?.optString("videoId")
                            ?.takeIf { VIDEO_ID_REGEX.matches(it) }
                        ?: extractPreferredAudioVideoId(renderer)?.takeIf { VIDEO_ID_REGEX.matches(it) }

                    if (!videoId.isNullOrBlank() && seenVideoIds.add(videoId)) {
                        val flexCols = renderer.optJSONArray("flexColumns")
                        val col0 = flexCols?.optJSONObject(0)
                            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                            ?.optJSONObject("text")
                        val title = extractAllRunsText(col0).trim()
                        val col1 = flexCols?.optJSONObject(1)
                            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                            ?.optJSONObject("text")
                        val subtitle = extractAllRunsText(col1).trim()
                        if (title.isNotBlank()) {
                            val parsedArtist = subtitle.split("•")
                                .map { it.trim() }
                                .firstOrNull { part ->
                                    part.isNotBlank() &&
                                        !isPlayCountLabel(part) &&
                                        !DURATION_REGEX.matches(part) &&
                                        !part.equals("Video", ignoreCase = true) &&
                                        !part.equals("Song", ignoreCase = true)
                                }
                                ?.takeIf { it.isNotBlank() }
                                ?: defaultArtist
                            val views = parsePlayCountNumber(subtitle)
                            val durationMs = Regex("""\b(\d{1,2}:\d{2}(?::\d{2})?)\b""").find(subtitle)
                                ?.value?.let { parseDuration(it) } ?: 210_000L
                            val thumb = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                            results.add(
                                TrackItem(
                                    id = "ytm_video_$videoId",
                                    title = title,
                                    artist = parsedArtist,
                                    album = "Official Video",
                                    durationMs = durationMs,
                                    artworkUri = thumb,
                                    audioUrl = "https://www.youtube.com/watch?v=$videoId",
                                    playCount = views,
                                    isExplicit = hasExplicitBadge(renderer)
                                )
                            )
                        }
                    }
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    extractVideoItemsRecursive(node.get(k), defaultArtist, results, seenVideoIds)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    extractVideoItemsRecursive(node.get(i), defaultArtist, results, seenVideoIds)
                }
            }
        }
    }

    private fun MutableList<AlbumItem>.addUniqueAlbum(item: AlbumItem) {
        val key = item.id.trim().ifBlank { item.title.trim().lowercase() }
        if (none { existing -> existing.id.trim().ifBlank { existing.title.trim().lowercase() } == key }) {
            add(item)
        }
    }

    private fun MutableList<ArtistItem>.addUniqueArtist(item: ArtistItem) {
        val key = item.id.trim().ifBlank { item.name.trim().lowercase() }
        if (none { existing -> existing.id.trim().ifBlank { existing.name.trim().lowercase() } == key }) {
            add(item)
        }
    }

    // =========================================================================
    // PERSONALIZED YOUTUBE MUSIC ACCOUNT, HOME FEED, RADIO & PLAYLIST APIS
    // =========================================================================

    suspend fun fetchAccountInfo(): YtmAccountState = withContext(Dispatchers.IO) {
        val manager = authManager ?: return@withContext YtmAccountState(isSignedIn = false)
        if (!manager.isSignedIn()) {
            return@withContext YtmAccountState(isSignedIn = false)
        }

        val existing = manager.accountState.value
        fun buildAccountMenuPayload(includeUser: Boolean) = JSONObject().apply {
            put("context", buildWebRemixContextJson(includeUser = includeUser))
            put("deviceTheme", "DEVICE_THEME_SELECTED")
            put("userInterfaceTheme", "USER_INTERFACE_THEME_DARK")
        }

        var postResult = executeInnerTubePostWithStatus(
            url = accountMenuUrl,
            payload = buildAccountMenuPayload(includeUser = true),
            includeAuthHeaders = true,
            includePageId = true
        )

        // Retry without delegated pageId/onBehalfOfUser if DATASYNC_ID delegation caused a rejection.
        if (!postResult.isSuccessful &&
            (manager.getDelegatedPageId() != null || manager.getOnBehalfOfUser() != null)
        ) {
            postResult = executeInnerTubePostWithStatus(
                url = accountMenuUrl,
                payload = buildAccountMenuPayload(includeUser = false),
                includeAuthHeaders = true,
                includePageId = false
            )
        }

        // If visitorData was blank and initial request failed, fetch fresh visitorData via sw.js_data and retry.
        if (!postResult.isSuccessful &&
            manager.getVisitorData().isNullOrBlank() &&
            cachedVisitorData.isNullOrBlank()
        ) {
            getVisitorDataAndCookies()
            postResult = executeInnerTubePostWithStatus(
                url = accountMenuUrl,
                payload = buildAccountMenuPayload(includeUser = false),
                includeAuthHeaders = true,
                includePageId = false
            )
        }

        val menuJson = postResult.json
        if (menuJson != null) {
            if (containsUnauthenticatedAccountMenuMarker(menuJson)) {
                return@withContext YtmAccountState(isSignedIn = false)
            }
            val parsedFromMenu = parseAccountMenuResponse(menuJson)
            if (parsedFromMenu != null &&
                (!parsedFromMenu.accountName.isNullOrBlank() || !parsedFromMenu.accountHandle.isNullOrBlank())
            ) {
                manager.updateAccountInfo(
                    accountName = parsedFromMenu.accountName,
                    accountHandle = parsedFromMenu.accountHandle,
                    avatarUrl = parsedFromMenu.avatarUrl,
                    syncedAtMs = clockMs(),
                    dataSyncId = extractDataSyncIdTokenRecursive(menuJson)
                )
                return@withContext manager.accountState.value
            }
        }

        // Fall back to getAccountSwitcherEndpoint (which uses `accountItem` + `simpleText` and handles
        // accounts whose account_menu lacks activeAccountHeaderRenderer).
        val switcherResult = fetchAccountSwitcherFallback(manager)
        if (switcherResult != null) {
            if (switcherResult.isConfirmedSuccess && switcherResult.accountState != null) {
                manager.updateAccountInfo(
                    accountName = switcherResult.accountState.accountName,
                    accountHandle = switcherResult.accountState.accountHandle,
                    avatarUrl = switcherResult.accountState.avatarUrl,
                    syncedAtMs = clockMs(),
                    dataSyncId = switcherResult.dataSyncId
                )
                return@withContext manager.accountState.value
            }
            if (switcherResult.isExplicitFailure) {
                return@withContext YtmAccountState(isSignedIn = false)
            }
        }

        if (menuJson != null && containsAuthenticatedAccountMenuMarker(menuJson)) {
            val fallbackName = existing.accountName?.takeIf { it.isNotBlank() } ?: "YouTube Music Account"
            manager.updateAccountInfo(
                accountName = fallbackName,
                accountHandle = existing.accountHandle,
                avatarUrl = existing.avatarUrl,
                syncedAtMs = clockMs()
            )
            return@withContext manager.accountState.value
        }

        if (postResult.isNetworkError &&
            (!existing.accountName.isNullOrBlank() || !existing.accountHandle.isNullOrBlank())
        ) {
            return@withContext existing.copy(lastSyncedAtMs = clockMs())
        }

        YtmAccountState(isSignedIn = false)
    }

    private fun fetchAccountSwitcherFallback(manager: YouTubeAuthManager): AccountSwitcherResult? {
        return try {
            val builder = Request.Builder()
                .url(accountSwitcherUrl)
                .get()
                .addHeader("User-Agent", DESKTOP_USER_AGENT)
                .addHeader("Accept", "*/*")
                .addHeader("Accept-Language", "en-US,en;q=0.9")
                .addHeader("Origin", "https://music.youtube.com")
                .addHeader("Referer", "https://music.youtube.com/")
                .addHeader("X-Goog-Api-Format-Version", "1")
                .addHeader("X-YouTube-Client-Name", WEB_REMIX_CLIENT_ID)
                .addHeader("X-YouTube-Client-Version", WEB_REMIX_CLIENT_VERSION)
                .addHeader("X-Youtube-Bootstrap-Logged-In", "true")

            manager.buildAuthHeaders(
                timestampSeconds = clockMs() / 1000L,
                includePageId = false
            ).forEach { (key, value) ->
                if (!key.equals("Origin", ignoreCase = true) && !key.equals("Referer", ignoreCase = true)) {
                    builder.header(key, value)
                }
            }

            httpClient.newCall(builder.build()).execute().use { response ->
                val code = response.code
                if (code == 401 || code == 403) {
                    return AccountSwitcherResult(httpCode = code, isExplicitFailure = true)
                }
                val raw = response.body?.string()
                if (!response.isSuccessful || raw.isNullOrBlank()) {
                    return AccountSwitcherResult(httpCode = code)
                }
                val cleaned = if (raw.startsWith(")]}'")) {
                    raw.substringAfter('\n')
                } else {
                    raw
                }
                val json = JSONObject(cleaned)
                if (json.has("code") && !json.optString("code").equals("SUCCESS", ignoreCase = true)) {
                    return AccountSwitcherResult(httpCode = code, isExplicitFailure = true)
                }
                val parsed = parseAccountMenuResponse(json)
                val dataSyncId = extractDataSyncIdTokenRecursive(json)
                if (parsed != null &&
                    (!parsed.accountName.isNullOrBlank() || !parsed.accountHandle.isNullOrBlank())
                ) {
                    return AccountSwitcherResult(
                        httpCode = code,
                        accountState = parsed,
                        dataSyncId = dataSyncId,
                        isConfirmedSuccess = true
                    )
                }
                if (json.optString("code").equals("SUCCESS", ignoreCase = true)) {
                    // Valid Google account with no YouTube channel yet (matches Convx LoginScreen behavior).
                    return AccountSwitcherResult(
                        httpCode = code,
                        accountState = YtmAccountState(
                            isSignedIn = true,
                            accountName = "YouTube Music Account",
                            lastSyncedAtMs = clockMs()
                        ),
                        dataSyncId = dataSyncId,
                        isConfirmedSuccess = true
                    )
                }
                AccountSwitcherResult(httpCode = code)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            null
        }
    }

    internal fun parseAccountMenuResponse(json: JSONObject): YtmAccountState? {
        if (json.has("error")) return null
        if (json.has("code") && !json.optString("code").equals("SUCCESS", ignoreCase = true)) {
            return null
        }
        if (containsUnauthenticatedAccountMenuMarker(json)) {
            return null
        }

        val headerObj = findKeyObjectRecursive(json, "activeAccountHeaderRenderer")
        if (headerObj != null) {
            val name = extractAllRunsText(headerObj.optJSONObject("accountName"))
                .ifBlank { extractAllRunsText(headerObj.optJSONObject("name")) }
                .trim()
            val handle = extractAllRunsText(headerObj.optJSONObject("channelHandle")).trim()
                .ifBlank { extractAllRunsText(headerObj.optJSONObject("email")).trim() }
                .ifBlank { null }
            val avatar = extractThumbUrlFromObject(headerObj.optJSONObject("accountPhoto"))
            if (name.isNotBlank() || !handle.isNullOrBlank()) {
                return YtmAccountState(
                    isSignedIn = true,
                    accountName = name.ifBlank { handle ?: "YouTube Music User" },
                    accountHandle = handle,
                    avatarUrl = avatar,
                    lastSyncedAtMs = clockMs()
                )
            }
        }

        val googleHeaderObj = findKeyObjectRecursive(json, "googleAccountHeaderRenderer")
        if (googleHeaderObj != null) {
            val name = extractAllRunsText(googleHeaderObj.optJSONObject("name"))
                .ifBlank { extractAllRunsText(googleHeaderObj.optJSONObject("accountName")) }
                .trim()
            val handle = extractAllRunsText(googleHeaderObj.optJSONObject("channelHandle"))
                .ifBlank { extractAllRunsText(googleHeaderObj.optJSONObject("email")) }
                .trim()
                .ifBlank { null }
            val avatar = extractThumbUrlFromObject(
                googleHeaderObj.optJSONObject("accountPhoto")
                    ?: googleHeaderObj.optJSONObject("avatar")
                    ?: googleHeaderObj.optJSONObject("thumbnail")
            )
            if (name.isNotBlank() || !handle.isNullOrBlank()) {
                return YtmAccountState(
                    isSignedIn = true,
                    accountName = name.ifBlank { handle ?: "YouTube Music Account" },
                    accountHandle = handle,
                    avatarUrl = avatar,
                    lastSyncedAtMs = clockMs()
                )
            }
        }

        val accountItem = findSelectedAccountItemRecursive(json)
        if (accountItem != null) {
            val name = extractAllRunsText(accountItem.optJSONObject("accountName"))
                .ifBlank { extractAllRunsText(accountItem.optJSONObject("name")) }
                .trim()
            val handle = extractAllRunsText(accountItem.optJSONObject("channelHandle")).trim()
                .ifBlank { extractAllRunsText(accountItem.optJSONObject("email")).trim() }
                .ifBlank { extractAllRunsText(accountItem.optJSONObject("accountByline")).trim() }
                .ifBlank { null }
            val avatar = extractThumbUrlFromObject(accountItem.optJSONObject("accountPhoto"))
            if (name.isNotBlank() || !handle.isNullOrBlank()) {
                return YtmAccountState(
                    isSignedIn = true,
                    accountName = name.ifBlank { handle ?: "YouTube Music Account" },
                    accountHandle = handle,
                    avatarUrl = avatar,
                    lastSyncedAtMs = clockMs()
                )
            }
        }
        return null
    }

    private fun containsUnauthenticatedAccountMenuMarker(json: JSONObject): Boolean {
        if (json.has("error")) return true
        val hasProfileRenderer = findKeyObjectRecursive(json, "activeAccountHeaderRenderer") != null ||
            findKeyObjectRecursive(json, "googleAccountHeaderRenderer") != null ||
            findSelectedAccountItemRecursive(json) != null
        if (hasProfileRenderer) return false
        val hasSignOut = hasAnyKeyRecursive(json, setOf("signOutEndpoint", "switchAccountsEndpoint"))
        if (hasSignOut) return false
        return hasAnyKeyRecursive(json, setOf("signInEndpoint", "loginEndpoint"))
    }

    private fun containsAuthenticatedAccountMenuMarker(json: JSONObject): Boolean {
        if (hasAnyKeyRecursive(json, setOf("signInEndpoint", "loginEndpoint"))) return false
        val hasMenu = findKeyObjectRecursive(json, "multiPageMenuRenderer") != null
        if (!hasMenu) return false
        return hasAnyKeyRecursive(
            json,
            setOf(
                "signOutEndpoint",
                "switchAccountsEndpoint",
                "manageAccountTitle",
                "activeAccountHeaderRenderer",
                "googleAccountHeaderRenderer",
                "accountItemRenderer",
                "accountItem"
            )
        )
    }

    private fun hasAnyKeyRecursive(node: Any?, targetKeys: Set<String>): Boolean {
        when (node) {
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key in targetKeys) return true
                    if (hasAnyKeyRecursive(node.opt(key), targetKeys)) return true
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    if (hasAnyKeyRecursive(node.opt(i), targetKeys)) return true
                }
            }
        }
        return false
    }

    private fun extractDataSyncIdTokenRecursive(node: Any?): String? {
        val selectedItem = findSelectedAccountItemRecursive(node)
        if (selectedItem != null) {
            extractTokenFromAccountItem(selectedItem)?.let { return it }
        }
        val tokenObj = findKeyObjectRecursive(node, "datasyncIdToken")
        return tokenObj?.optString("datasyncIdToken")
            ?.trim()
            ?.takeIf { it.isNotBlank() && it != "null" }
    }

    private fun extractTokenFromAccountItem(item: JSONObject): String? {
        val tokens = item.optJSONObject("serviceEndpoint")
            ?.optJSONObject("selectActiveIdentityEndpoint")
            ?.optJSONArray("supportedTokens")
            ?: return null
        for (i in 0 until tokens.length()) {
            val token = tokens.optJSONObject(i)
                ?.optJSONObject("datasyncIdToken")
                ?.optString("datasyncIdToken")
                ?.trim()
            if (!token.isNullOrBlank() && token != "null") {
                return token
            }
        }
        return null
    }

    private fun findKeyObjectRecursive(node: Any?, targetKey: String): JSONObject? {
        when (node) {
            is JSONObject -> {
                if (node.has(targetKey)) {
                    node.optJSONObject(targetKey)?.let { return it }
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val found = findKeyObjectRecursive(node.opt(keys.next()), targetKey)
                    if (found != null) return found
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    val found = findKeyObjectRecursive(node.opt(i), targetKey)
                    if (found != null) return found
                }
            }
        }
        return null
    }

    private fun findSelectedAccountItemRecursive(node: Any?): JSONObject? {
        var fallback: JSONObject? = null
        when (node) {
            is JSONObject -> {
                val item = node.optJSONObject("accountItemRenderer")
                    ?: node.optJSONObject("accountItem")
                if (item != null) {
                    if (item.optBoolean("isSelected", false)) return item
                    if (fallback == null) fallback = item
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val found = findSelectedAccountItemRecursive(node.opt(keys.next()))
                    if (found != null) {
                        if (found.optBoolean("isSelected", false)) return found
                        if (fallback == null) fallback = found
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    val found = findSelectedAccountItemRecursive(node.opt(i))
                    if (found != null) {
                        if (found.optBoolean("isSelected", false)) return found
                        if (fallback == null) fallback = found
                    }
                }
            }
        }
        return fallback
    }

    suspend fun fetchPersonalizedHomeFeed(): YtmHomeFeedResult = withContext(Dispatchers.IO) {
        val isPersonalized = authManager?.isSignedIn() == true
        val initialPayload = JSONObject().apply {
            put("context", buildWebRemixContextJson())
            put("browseId", "FEmusic_home")
        }

        val initialJson = executeInnerTubePost(browseUrl, initialPayload, includeAuthHeaders = true)
            ?: return@withContext YtmHomeFeedResult(isPersonalized = isPersonalized)

        val pages = mutableListOf<YtmHomeFeedResult>()
        pages.add(parseHomeFeedResponse(initialJson, isPersonalized))

        var continuation = extractNextContinuationToken(initialJson)
        var depth = 0
        while (!continuation.isNullOrBlank() && depth < 2) {
            val contPayload = JSONObject().apply {
                put("context", buildWebRemixContextJson())
                put("continuation", continuation)
            }
            val contJson = executeInnerTubePost(browseUrl, contPayload, includeAuthHeaders = true) ?: break
            pages.add(parseHomeFeedResponse(contJson, isPersonalized))
            continuation = extractNextContinuationToken(contJson)
            depth++
        }

        mergeHomeFeedResults(pages, isPersonalized)
    }

    suspend fun fetchExploreAndNewReleases(): Pair<List<NewReleaseItem>, List<RadioStationItem>> = withContext(Dispatchers.IO) {
        coroutineScope {
            val newReleasesDeferred = async {
                val payload = JSONObject().apply {
                    put("context", buildWebRemixContextJson())
                    put("browseId", "FEmusic_new_releases_albums")
                }
                executeInnerTubePost(browseUrl, payload, includeAuthHeaders = true)
                    ?.let { parseNewReleasesResponse(it) }
                    ?: emptyList()
            }

            val exploreDeferred = async {
                val payload = JSONObject().apply {
                    put("context", buildWebRemixContextJson())
                    put("browseId", "FEmusic_explore")
                }
                executeInnerTubePost(browseUrl, payload, includeAuthHeaders = true)
            }

            val releaseAlbums = newReleasesDeferred.await()
            val exploreJson = exploreDeferred.await()
            val exploreReleases = exploreJson?.let { parseNewReleasesResponse(it) } ?: emptyList()
            val exploreFeed = exploreJson?.let { parseHomeFeedResponse(it, authManager?.isSignedIn() == true) }

            val combinedReleases = linkedMapOf<String, NewReleaseItem>()
            (releaseAlbums + exploreReleases + (exploreFeed?.newReleases ?: emptyList())).forEach { item ->
                val key = "${item.title.lowercase(Locale.ROOT)}::${item.artist.lowercase(Locale.ROOT)}"
                if (!combinedReleases.containsKey(key)) {
                    combinedReleases[key] = item
                }
            }

            val exploreRadios = exploreFeed?.radioStations ?: emptyList()
            combinedReleases.values.toList() to exploreRadios
        }
    }

    internal fun parseHomeFeedResponse(
        json: JSONObject,
        isPersonalized: Boolean = authManager?.isSignedIn() == true
    ): YtmHomeFeedResult {
        val tracks = mutableListOf<TrackItem>()
        val seenTrackIds = mutableSetOf<String>()

        val albums = mutableListOf<AlbumItem>()
        val seenAlbumKeys = mutableSetOf<String>()

        val playlists = mutableListOf<RecommendedPlaylistItem>()
        val seenPlaylistIds = mutableSetOf<String>()

        val radios = mutableListOf<RadioStationItem>()
        val seenRadioIds = mutableSetOf<String>()

        val newReleases = mutableListOf<NewReleaseItem>()
        val seenReleaseKeys = mutableSetOf<String>()

        val sections = extractBrowseSections(json)
        for (section in sections) {
            val carousel = section.optJSONObject("musicCarouselShelfRenderer")
            val musicShelf = section.optJSONObject("musicShelfRenderer")
            val grid = section.optJSONObject("gridRenderer")

            val shelfTitle = when {
                carousel != null -> extractAllRunsText(
                    carousel.optJSONObject("header")
                        ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                        ?.optJSONObject("title")
                ).trim()
                musicShelf != null -> extractAllRunsText(musicShelf.optJSONObject("title")).trim()
                grid != null -> extractAllRunsText(
                    grid.optJSONObject("header")
                        ?.optJSONObject("gridHeaderRenderer")
                        ?.optJSONObject("title")
                ).trim()
                else -> ""
            }

            val itemsArray = when {
                carousel != null -> carousel.optJSONArray("contents")
                musicShelf != null -> musicShelf.optJSONArray("contents")
                grid != null -> grid.optJSONArray("items")
                else -> null
            } ?: continue

            for (i in 0 until itemsArray.length()) {
                val entry = itemsArray.optJSONObject(i) ?: continue
                entry.optJSONObject("musicResponsiveListItemRenderer")?.let { responsive ->
                    parseResponsiveItem(responsive)?.let { track ->
                        if (seenTrackIds.add(track.id)) {
                            tracks.add(track)
                        }
                        if (isNewReleaseShelf(shelfTitle)) {
                            val relKey = "${track.title.lowercase(Locale.ROOT)}::${track.artist.lowercase(Locale.ROOT)}"
                            if (seenReleaseKeys.add(relKey)) {
                                newReleases.add(
                                    NewReleaseItem(
                                        id = "nr_${track.id}",
                                        title = track.title,
                                        artist = track.artist,
                                        subtitle = "Single • ${track.artist}",
                                        artworkUri = track.artworkUri,
                                        isAlbum = false,
                                        videoId = track.id.removePrefix("ytm_")
                                    )
                                )
                            }
                        }
                    }
                    extractMenuRadioFromResponsive(responsive, shelfTitle)?.let { radio ->
                        if (seenRadioIds.add(radio.playlistId)) {
                            radios.add(radio)
                        }
                    }
                }

                entry.optJSONObject("musicTwoRowItemRenderer")?.let { twoRow ->
                    classifyTwoRowHomeItem(
                        twoRow = twoRow,
                        shelfTitle = shelfTitle,
                        tracks = tracks,
                        seenTrackIds = seenTrackIds,
                        albums = albums,
                        seenAlbumKeys = seenAlbumKeys,
                        playlists = playlists,
                        seenPlaylistIds = seenPlaylistIds,
                        radios = radios,
                        seenRadioIds = seenRadioIds,
                        newReleases = newReleases,
                        seenReleaseKeys = seenReleaseKeys
                    )
                }
            }
        }

        val topPickCandidates = buildInitialTopPickCandidates(
            tracks = tracks,
            playlists = playlists,
            newReleases = newReleases,
            radios = radios,
            isPersonalized = isPersonalized
        )

        return YtmHomeFeedResult(
            personalizedTracks = tracks,
            personalizedAlbums = albums,
            playlistsMadeForYou = playlists,
            radioStations = radios,
            newReleases = newReleases,
            featuredTopPicks = topPickCandidates,
            isPersonalized = isPersonalized
        )
    }

    internal fun parseNewReleasesResponse(json: JSONObject): List<NewReleaseItem> {
        val results = mutableListOf<NewReleaseItem>()
        val seenKeys = mutableSetOf<String>()
        val sections = extractBrowseSections(json)

        for (section in sections) {
            val grid = section.optJSONObject("gridRenderer")
            val carousel = section.optJSONObject("musicCarouselShelfRenderer")
            val musicShelf = section.optJSONObject("musicShelfRenderer")

            val shelfTitle = when {
                grid != null -> extractAllRunsText(
                    grid.optJSONObject("header")
                        ?.optJSONObject("gridHeaderRenderer")
                        ?.optJSONObject("title")
                ).trim()
                carousel != null -> extractAllRunsText(
                    carousel.optJSONObject("header")
                        ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                        ?.optJSONObject("title")
                ).trim()
                musicShelf != null -> extractAllRunsText(musicShelf.optJSONObject("title")).trim()
                else -> ""
            }

            // On FEmusic_new_releases_albums grids, all items are new releases regardless of localized header;
            // on FEmusic_explore, filter out moods/genres/podcasts shelves.
            val lowerShelf = shelfTitle.lowercase(Locale.ROOT)
            val isExcludedExploreShelf = lowerShelf.contains("mood") ||
                lowerShelf.contains("genre") ||
                lowerShelf.contains("género") ||
                lowerShelf.contains("estado") ||
                lowerShelf.contains("podcast") ||
                lowerShelf.contains("chart") ||
                lowerShelf.contains("lista")

            if (isExcludedExploreShelf) continue

            val items = when {
                grid != null -> grid.optJSONArray("items")
                carousel != null -> carousel.optJSONArray("contents")
                musicShelf != null -> musicShelf.optJSONArray("contents")
                else -> null
            } ?: continue

            for (i in 0 until items.length()) {
                val entry = items.optJSONObject(i) ?: continue
                entry.optJSONObject("musicTwoRowItemRenderer")?.let { twoRow ->
                    val release = parseTwoRowAsNewRelease(twoRow, shelfTitle) ?: return@let
                    val key = "${release.title.lowercase(Locale.ROOT)}::${release.artist.lowercase(Locale.ROOT)}"
                    if (seenKeys.add(key)) {
                        results.add(release)
                    }
                }
                entry.optJSONObject("musicResponsiveListItemRenderer")?.let { responsive ->
                    if (isNewReleaseShelf(shelfTitle) || lowerShelf.contains("trending") || lowerShelf.contains("tendenc")) {
                        parseResponsiveItem(responsive)?.let { track ->
                            val key = "${track.title.lowercase(Locale.ROOT)}::${track.artist.lowercase(Locale.ROOT)}"
                            if (seenKeys.add(key)) {
                                results.add(
                                    NewReleaseItem(
                                        id = "nr_${track.id}",
                                        title = track.title,
                                        artist = track.artist,
                                        subtitle = "Single • ${track.artist}",
                                        artworkUri = track.artworkUri,
                                        isAlbum = false,
                                        videoId = track.id.removePrefix("ytm_")
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
        return results
    }

    private fun parseTwoRowAsNewRelease(twoRow: JSONObject, shelfTitle: String): NewReleaseItem? {
        val title = extractAllRunsText(twoRow.optJSONObject("title")).trim()
        if (title.isBlank()) return null

        val subtitle = extractAllRunsText(twoRow.optJSONObject("subtitle")).trim()
        val navEp = twoRow.optJSONObject("navigationEndpoint")
        val browseId = navEp?.optJSONObject("browseEndpoint")?.optString("browseId").orEmpty()
        val pageType = extractBrowsePageType(navEp)
        val creditsAtvId = extractTrackCreditsAtvVideoId(twoRow)
        val rawWatchVideoId = navEp?.optJSONObject("watchEndpoint")?.optString("videoId").orEmpty().trim()
        val videoId = creditsAtvId ?: rawWatchVideoId

        // Skip artist cards, podcasts, moods, or playlists unless clearly album/single/video
        if (browseId.startsWith("UC") || pageType == "MUSIC_PAGE_TYPE_ARTIST" || pageType == "MUSIC_PAGE_TYPE_PODCAST_SHOW") {
            return null
        }

        val isPlaylist = pageType == "MUSIC_PAGE_TYPE_PLAYLIST" || browseId.startsWith("VL")
        if (isPlaylist) return null

        val isAlbum = pageType == "MUSIC_PAGE_TYPE_ALBUM" ||
            browseId.startsWith("MPRE") ||
            browseId.startsWith("OLAK") ||
            subtitle.startsWith("Album", ignoreCase = true) ||
            subtitle.startsWith("Single", ignoreCase = true) ||
            subtitle.startsWith("EP", ignoreCase = true) ||
            subtitle.startsWith("Álbum", ignoreCase = true) ||
            subtitle.startsWith("Sencillo", ignoreCase = true)

        if (!isAlbum && videoId.isBlank() && !isNewReleaseShelf(shelfTitle)) {
            return null
        }
        if (!isAlbum && videoId.isNotBlank() && isTwoRowVideoOnlyItem(twoRow, subtitle, shelfTitle, creditsAtvId)) {
            return null
        }

        val thumb = extractThumbUrlFromObject(
            twoRow.optJSONObject("thumbnailRenderer")?.optJSONObject("musicThumbnailRenderer")?.optJSONObject("thumbnail")
        )
        val artist = extractArtistFromSubtitle(subtitle).ifBlank { "YouTube Artist" }
        val yearStr = Regex("""\b(19\d\d|20\d\d)\b""").find(subtitle)?.value

        val id = when {
            browseId.isNotBlank() -> "nr_alb_$browseId"
            videoId.isNotBlank() -> "nr_vid_$videoId"
            else -> "nr_${title.hashCode()}"
        }

        return NewReleaseItem(
            id = id,
            title = title,
            artist = artist,
            subtitle = subtitle.ifBlank { if (isAlbum) "Album • $artist" else "Single • $artist" },
            artworkUri = thumb,
            isAlbum = isAlbum && videoId.isBlank(),
            browseId = browseId.ifBlank { null },
            videoId = videoId.ifBlank { null },
            year = yearStr
        )
    }

    private fun classifyTwoRowHomeItem(
        twoRow: JSONObject,
        shelfTitle: String,
        tracks: MutableList<TrackItem>,
        seenTrackIds: MutableSet<String>,
        albums: MutableList<AlbumItem>,
        seenAlbumKeys: MutableSet<String>,
        playlists: MutableList<RecommendedPlaylistItem>,
        seenPlaylistIds: MutableSet<String>,
        radios: MutableList<RadioStationItem>,
        seenRadioIds: MutableSet<String>,
        newReleases: MutableList<NewReleaseItem>,
        seenReleaseKeys: MutableSet<String>
    ) {
        val title = extractAllRunsText(twoRow.optJSONObject("title")).trim()
        if (title.isBlank()) return
        val subtitle = extractAllRunsText(twoRow.optJSONObject("subtitle")).trim()

        val navEp = twoRow.optJSONObject("navigationEndpoint")
        val browseId = navEp?.optJSONObject("browseEndpoint")?.optString("browseId").orEmpty()
        val pageType = extractBrowsePageType(navEp)
        val watchEp = navEp?.optJSONObject("watchEndpoint")
        val watchPlaylistEp = navEp?.optJSONObject("watchPlaylistEndpoint")
        val creditsAtvId = extractTrackCreditsAtvVideoId(twoRow)
        val rawWatchVideoId = watchEp?.optString("videoId").orEmpty().trim()
        val videoId = creditsAtvId ?: rawWatchVideoId
        var playlistId = watchEp?.optString("playlistId").orEmpty()
            .ifBlank { watchPlaylistEp?.optString("playlistId").orEmpty() }
            .ifBlank { if (browseId.startsWith("VL")) browseId.removePrefix("VL") else "" }
        if (!creditsAtvId.isNullOrBlank() &&
            rawWatchVideoId.isNotBlank() &&
            creditsAtvId != rawWatchVideoId &&
            playlistId.equals("RDAMVM$rawWatchVideoId", ignoreCase = true)
        ) {
            playlistId = "RDAMVM$creditsAtvId"
        }
        val params = watchEp?.optString("params").orEmpty()
            .ifBlank { watchPlaylistEp?.optString("params").orEmpty() }
            .ifBlank { null }

        val thumb = extractThumbUrlFromObject(
            twoRow.optJSONObject("thumbnailRenderer")?.optJSONObject("musicThumbnailRenderer")?.optJSONObject("thumbnail")
        )

        val radioShelf = isRadioShelf(shelfTitle)
        val mixOrPlaylistShelf = isPlaylistOrMixShelf(shelfTitle)
        val newReleaseShelf = isNewReleaseShelf(shelfTitle)

        // 1. Radio station check (RD... mixes that aren't curated VL/RDCLAK/RDTMAK playlists, or explicit Radio shelf)
        val explicitPlaylistBrowse = browseId.startsWith("VL") || pageType == "MUSIC_PAGE_TYPE_PLAYLIST"
        val isRadioSeedPrefix = playlistId.startsWith("RDEM") ||
            playlistId.startsWith("RDAO") ||
            playlistId.startsWith("RDAMPL") ||
            subtitle.contains("radio", ignoreCase = true)

        val isRadioItem = (radioShelf && (playlistId.isNotBlank() || videoId.isNotBlank())) ||
            (!explicitPlaylistBrowse &&
                playlistId.startsWith("RD") &&
                !playlistId.startsWith("RDCLAK") &&
                !playlistId.startsWith("RDTMAK") &&
                (videoId.isBlank() || isRadioSeedPrefix)) ||
            (explicitPlaylistBrowse && isRadioSeedPrefix)

        if (isRadioItem) {
            val resolvedPlId = playlistId.ifBlank { if (videoId.isNotBlank()) "RDAMVM$videoId" else "" }
            if (resolvedPlId.isNotBlank() && seenRadioIds.add(resolvedPlId)) {
                radios.add(
                    RadioStationItem(
                        id = "radio_$resolvedPlId",
                        title = title,
                        subtitle = subtitle.ifBlank { shelfTitle.ifBlank { "YouTube Music Radio" } },
                        artworkUri = thumb,
                        playlistId = resolvedPlId,
                        seedVideoId = videoId.ifBlank { null },
                        params = params
                    )
                )
            }
            return
        }

        // 2. Album check
        val isAlbum = pageType == "MUSIC_PAGE_TYPE_ALBUM" ||
            browseId.startsWith("MPRE") ||
            browseId.startsWith("OLAK")

        if (isAlbum) {
            val artist = extractArtistFromSubtitle(subtitle).ifBlank { "YouTube Artist" }
            val year = Regex("""\b(19\d\d|20\d\d)\b""").find(subtitle)?.value?.toIntOrNull() ?: 2024
            val albumKey = "${title.lowercase(Locale.ROOT)}::${artist.lowercase(Locale.ROOT)}"
            if (seenAlbumKeys.add(albumKey)) {
                albums.add(
                    AlbumItem(
                        id = "ytm_alb_${browseId.ifBlank { "${title.hashCode()}" }}",
                        title = title,
                        artist = artist,
                        coverUri = thumb,
                        year = year,
                        tracksCount = 10
                    )
                )
            }
            if (newReleaseShelf && seenReleaseKeys.add(albumKey)) {
                newReleases.add(
                    NewReleaseItem(
                        id = "nr_alb_${browseId.ifBlank { "${title.hashCode()}" }}",
                        title = title,
                        artist = artist,
                        subtitle = subtitle.ifBlank { "Album • $artist" },
                        artworkUri = thumb,
                        isAlbum = true,
                        browseId = browseId.ifBlank { null },
                        year = Regex("""\b(19\d\d|20\d\d)\b""").find(subtitle)?.value
                    )
                )
            }
            return
        }

        // 3. Curated Playlist / Mixed for you check
        val isPlaylist = pageType == "MUSIC_PAGE_TYPE_PLAYLIST" ||
            browseId.startsWith("VL") ||
            playlistId.startsWith("RDCLAK") ||
            playlistId.startsWith("RDTMAK") ||
            playlistId.startsWith("PL") ||
            (mixOrPlaylistShelf && playlistId.isNotBlank())

        if (isPlaylist && !browseId.startsWith("UC")) {
            val cleanPlId = playlistId.ifBlank { browseId.removePrefix("VL") }
            if (cleanPlId.isNotBlank() && seenPlaylistIds.add(cleanPlId)) {
                val resolvedBrowse = browseId.ifBlank { "VL$cleanPlId" }
                playlists.add(
                    RecommendedPlaylistItem(
                        id = "pl_$cleanPlId",
                        title = title,
                        subtitle = subtitle.ifBlank { shelfTitle.ifBlank { "Made for you" } },
                        artworkUri = thumb,
                        playlistId = cleanPlId,
                        browseId = resolvedBrowse,
                        author = extractPlaylistAuthor(subtitle),
                        trackCountText = extractTrackCount(subtitle)
                    )
                )
            }
            return
        }

        // 4. Song (discard video-only OMV/UGC cards without Track Credits ATV ID)
        if (videoId.isNotBlank()) {
            if (isTwoRowVideoOnlyItem(twoRow, subtitle, shelfTitle, creditsAtvId)) {
                return
            }
            val artist = extractArtistFromSubtitle(subtitle).ifBlank { "YouTube Artist" }
            val trackId = "ytm_$videoId"
            if (seenTrackIds.add(trackId)) {
                tracks.add(
                    TrackItem(
                        id = trackId,
                        title = title,
                        artist = artist,
                        album = "Single",
                        durationMs = 210000L,
                        artworkUri = thumb,
                        audioUrl = "https://www.youtube.com/watch?v=$videoId",
                        isExplicit = hasExplicitBadge(twoRow)
                    )
                )
            }
            if (newReleaseShelf || shelfTitle.contains("trending", ignoreCase = true)) {
                val relKey = "${title.lowercase(Locale.ROOT)}::${artist.lowercase(Locale.ROOT)}"
                if (seenReleaseKeys.add(relKey)) {
                    newReleases.add(
                        NewReleaseItem(
                            id = "nr_vid_$videoId",
                            title = title,
                            artist = artist,
                            subtitle = subtitle.ifBlank { "Single • $artist" },
                            artworkUri = thumb,
                            isAlbum = false,
                            videoId = videoId,
                            year = Regex("""\b(19\d\d|20\d\d)\b""").find(subtitle)?.value
                        )
                    )
                }
            }
        }
    }

    private fun extractMenuRadioFromResponsive(renderer: JSONObject, shelfTitle: String): RadioStationItem? {
        val watchEp = renderer.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")
        val creditsAtvId = extractTrackCreditsAtvVideoId(renderer)
        val rawWatchVideoId = watchEp?.optString("videoId").orEmpty().trim()
        val videoId = creditsAtvId ?: rawWatchVideoId.ifBlank { findVideoId(renderer).orEmpty() }
        if (videoId.isBlank()) return null

        var playlistId = watchEp?.optString("playlistId").orEmpty()
        if (!playlistId.startsWith("RD") && !isRadioShelf(shelfTitle)) return null
        if (!creditsAtvId.isNullOrBlank() &&
            rawWatchVideoId.isNotBlank() &&
            creditsAtvId != rawWatchVideoId &&
            playlistId.equals("RDAMVM$rawWatchVideoId", ignoreCase = true)
        ) {
            playlistId = "RDAMVM$creditsAtvId"
        }

        val flexCols = renderer.optJSONArray("flexColumns") ?: return null
        val title = extractRunsText(flexCols.optJSONObject(0)).trim()
        if (title.isBlank()) return null
        val artist = parseMetadataRuns(flexCols.optJSONObject(1), defaultArtist = "YouTube Music Radio").artist
        val resolvedPlId = playlistId.ifBlank { "RDAMVM$videoId" }
        val thumb = extractHighResThumbnail(renderer, videoId)

        return RadioStationItem(
            id = "radio_$resolvedPlId",
            title = if (isRadioShelf(shelfTitle)) title else "$title Radio",
            subtitle = artist.ifBlank { "YouTube Music Radio" },
            artworkUri = thumb,
            playlistId = resolvedPlId,
            seedVideoId = videoId
        )
    }

    private fun buildInitialTopPickCandidates(
        tracks: List<TrackItem>,
        playlists: List<RecommendedPlaylistItem>,
        newReleases: List<NewReleaseItem>,
        radios: List<RadioStationItem>,
        isPersonalized: Boolean
    ): List<TopPickItem> {
        val picks = mutableListOf<TopPickItem>()
        tracks.take(2).forEach { track ->
            picks.add(
                TopPickItem(
                    id = "tp_track_${track.id}",
                    eyebrow = if (isPersonalized) "QUICK PICK FOR YOU" else "TRENDING PICK",
                    title = track.title,
                    subtitle = "${track.artist} • ${track.album}",
                    artworkUri = track.artworkUri,
                    kind = TopPickKind.PERSONAL_STATION,
                    playlistId = "RDAMVM${track.id.removePrefix("ytm_")}",
                    videoId = track.id.removePrefix("ytm_"),
                    artistName = track.artist,
                    seedTrack = track
                )
            )
        }
        radios.firstOrNull()?.let { radio ->
            picks.add(
                TopPickItem(
                    id = "tp_radio_${radio.playlistId}",
                    eyebrow = "PERSONAL STATION",
                    title = radio.title,
                    subtitle = radio.subtitle,
                    artworkUri = radio.artworkUri,
                    kind = TopPickKind.PERSONAL_STATION,
                    playlistId = radio.playlistId,
                    videoId = radio.seedVideoId
                )
            )
        }
        playlists.take(2).forEach { pl ->
            picks.add(
                TopPickItem(
                    id = "tp_pl_${pl.playlistId}",
                    eyebrow = "MIXED FOR YOU",
                    title = pl.title,
                    subtitle = pl.subtitle,
                    artworkUri = pl.artworkUri,
                    kind = TopPickKind.FEATURED_PLAYLIST,
                    browseId = pl.browseId,
                    playlistId = pl.playlistId
                )
            )
        }
        newReleases.firstOrNull()?.let { release ->
            picks.add(
                TopPickItem(
                    id = "tp_nr_${release.id}",
                    eyebrow = "NEW RELEASE",
                    title = release.title,
                    subtitle = "${release.artist} • ${release.subtitle}",
                    artworkUri = release.artworkUri,
                    kind = TopPickKind.NEW_RELEASE,
                    browseId = release.browseId,
                    videoId = release.videoId,
                    artistName = release.artist,
                    albumTitle = if (release.isAlbum) release.title else null
                )
            )
        }
        return picks
    }

    private fun mergeHomeFeedResults(pages: List<YtmHomeFeedResult>, isPersonalized: Boolean): YtmHomeFeedResult {
        val tracks = LinkedHashMap<String, TrackItem>()
        val albums = LinkedHashMap<String, AlbumItem>()
        val playlists = LinkedHashMap<String, RecommendedPlaylistItem>()
        val radios = LinkedHashMap<String, RadioStationItem>()
        val releases = LinkedHashMap<String, NewReleaseItem>()
        val picks = LinkedHashMap<String, TopPickItem>()

        pages.forEach { page ->
            page.personalizedTracks.forEach { tracks.putIfAbsent(it.id, it) }
            page.personalizedAlbums.forEach { albums.putIfAbsent("${it.title.lowercase(Locale.ROOT)}::${it.artist.lowercase(Locale.ROOT)}", it) }
            page.playlistsMadeForYou.forEach { playlists.putIfAbsent(it.playlistId, it) }
            page.radioStations.forEach { radios.putIfAbsent(it.playlistId, it) }
            page.newReleases.forEach { releases.putIfAbsent("${it.title.lowercase(Locale.ROOT)}::${it.artist.lowercase(Locale.ROOT)}", it) }
            page.featuredTopPicks.forEach { picks.putIfAbsent(it.id, it) }
        }

        return YtmHomeFeedResult(
            personalizedTracks = tracks.values.toList(),
            personalizedAlbums = albums.values.toList(),
            playlistsMadeForYou = playlists.values.toList(),
            radioStations = radios.values.toList(),
            newReleases = releases.values.toList(),
            featuredTopPicks = picks.values.toList(),
            isPersonalized = isPersonalized
        )
    }

    suspend fun searchPlaylists(query: String, limit: Int = 10): List<RecommendedPlaylistItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val payload = JSONObject().apply {
            put("context", buildWebRemixContextJson())
            put("query", query)
            put("params", PLAYLISTS_PARAM)
        }
        val json = executeInnerTubePost(searchUrl, payload, includeAuthHeaders = true) ?: return@withContext emptyList()
        val results = mutableListOf<RecommendedPlaylistItem>()
        val seenIds = mutableSetOf<String>()
        extractPlaylistsRecursive(json, results, seenIds, limit)
        results
    }

    private fun extractPlaylistsRecursive(
        node: Any?,
        results: MutableList<RecommendedPlaylistItem>,
        seenIds: MutableSet<String>,
        limit: Int
    ) {
        if (results.size >= limit || node == null) return
        when (node) {
            is JSONObject -> {
                if (node.has("musicResponsiveListItemRenderer")) {
                    val renderer = node.optJSONObject("musicResponsiveListItemRenderer") ?: return
                    val flexCols = renderer.optJSONArray("flexColumns") ?: return
                    val title = extractRunsText(flexCols.optJSONObject(0)).trim()
                    val subtitle = extractRunsText(flexCols.optJSONObject(1)).trim()
                    val browseId = renderer.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("browseEndpoint")
                        ?.optString("browseId").orEmpty()
                    val cleanPlId = browseId.removePrefix("VL")
                    if (title.isNotBlank() && cleanPlId.isNotBlank() && seenIds.add(cleanPlId)) {
                        val thumb = extractThumbUrlFromObject(
                            renderer.optJSONObject("thumbnail")
                                ?.optJSONObject("musicThumbnailRenderer")
                                ?.optJSONObject("thumbnail")
                        )
                        results.add(
                            RecommendedPlaylistItem(
                                id = "pl_$cleanPlId",
                                title = title,
                                subtitle = subtitle.ifBlank { "YouTube Music Playlist" },
                                artworkUri = thumb,
                                playlistId = cleanPlId,
                                browseId = if (browseId.startsWith("VL")) browseId else "VL$cleanPlId",
                                author = extractPlaylistAuthor(subtitle),
                                trackCountText = extractTrackCount(subtitle)
                            )
                        )
                    }
                    return
                }
                val keys = node.keys()
                while (keys.hasNext() && results.size < limit) {
                    extractPlaylistsRecursive(node.opt(keys.next()), results, seenIds, limit)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    if (results.size >= limit) break
                    extractPlaylistsRecursive(node.opt(i), results, seenIds, limit)
                }
            }
        }
    }

    suspend fun getSimilarAlbumsForSeed(
        seedAlbumTitle: String,
        seedArtist: String,
        seedBrowseId: String? = null,
        limit: Int = 15
    ): List<AlbumItem> = withContext(Dispatchers.IO) {
        val collected = LinkedHashMap<String, AlbumItem>()
        val normalizedSeedTitle = seedAlbumTitle.trim().lowercase(Locale.ROOT)

        fun addCandidates(candidates: List<AlbumItem>) {
            for (album in candidates) {
                if (collected.size >= limit) break
                val normTitle = album.title.trim().lowercase(Locale.ROOT)
                if (normTitle.isBlank() || normTitle == normalizedSeedTitle) continue
                if (album.title.contains("Single", ignoreCase = true) && normTitle == "$normalizedSeedTitle (single)") continue
                val key = "$normTitle::${album.artist.trim().lowercase(Locale.ROOT)}"
                collected.putIfAbsent(key, album)
            }
        }

        // 1. Direct Album /browse -> Fans might also like / Other releases / Other versions
        val effectiveBrowseId = seedBrowseId?.takeIf { it.isNotBlank() && !it.startsWith("local_") }
        if (effectiveBrowseId != null) {
            getAlbumDetail(
                albumId = effectiveBrowseId,
                albumTitle = seedAlbumTitle,
                artistName = seedArtist
            )?.let { detail ->
                addCandidates(detail.otherAlbums)
                addCandidates(detail.otherVersions)
            }
        }

        // 2. Seed Artist Profile -> studio/combined albums & top 3 similar artists
        if (collected.size < limit && seedArtist.isNotBlank() && !seedArtist.equals("Unknown Artist", ignoreCase = true)) {
            val artistProfile = getArtistProfile(seedArtist)
            if (artistProfile != null) {
                addCandidates(artistProfile.combinedAlbums)
                val similarArtists = artistProfile.similarArtists.take(3)
                val similarAlbumLists: List<List<AlbumItem>> = coroutineScope {
                    similarArtists.map { simArtist ->
                        async {
                            getArtistProfile(simArtist.name)?.combinedAlbums
                                ?: executeInnerTubeAlbumSearch(simArtist.name, strictlyAlbumsOnly = true)
                        }
                    }.map { it.await() }
                }
                // Interleave albums from similar artists so the shelf is diverse
                val maxRounds = similarAlbumLists.maxOfOrNull { it.size } ?: 0
                for (round in 0 until maxRounds) {
                    if (collected.size >= limit) break
                    for (list in similarAlbumLists) {
                        list.getOrNull(round)?.let { addCandidates(listOf(it)) }
                    }
                }
            }
        }

        // 3. Fallback album search if still below limit
        if (collected.size < limit) {
            val queries = listOfNotNull(
                seedArtist.takeIf { it.isNotBlank() && !it.equals("Unknown Artist", ignoreCase = true) },
                "$seedAlbumTitle $seedArtist album".takeIf { seedAlbumTitle.isNotBlank() }
            )
            for (q in queries) {
                if (collected.size >= limit) break
                addCandidates(executeInnerTubeAlbumSearch(q, strictlyAlbumsOnly = true))
            }
        }

        collected.values.take(limit)
    }

    suspend fun resolveRadioStationTracks(station: RadioStationItem): List<TrackItem> = withContext(Dispatchers.IO) {
        val rawSeedId = station.seedVideoId?.trim()?.takeIf { it.isNotBlank() }
        val resolvedSeedId = if (rawSeedId != null) {
            resolveCanonicalAtvVideoId(rawSeedId)
        } else {
            null
        }
        var cleanPlId = station.playlistId.removePrefix("VL")
        if (!rawSeedId.isNullOrBlank() &&
            !resolvedSeedId.isNullOrBlank() &&
            resolvedSeedId != rawSeedId &&
            (cleanPlId.isBlank() || cleanPlId.equals("RDAMVM$rawSeedId", ignoreCase = true))
        ) {
            cleanPlId = "RDAMVM$resolvedSeedId"
        }

        val payload = JSONObject().apply {
            put("context", buildWebRemixContextJson())
            put("isAudioOnly", true)
            if (cleanPlId.isNotBlank()) {
                put("playlistId", cleanPlId)
            } else if (!resolvedSeedId.isNullOrBlank()) {
                put("playlistId", "RDAMVM$resolvedSeedId")
            }
            if (!resolvedSeedId.isNullOrBlank()) {
                put("videoId", resolvedSeedId)
            }
            if (!station.params.isNullOrBlank()) {
                put("params", station.params)
            }
        }

        val json = executeInnerTubePost(nextUrl, payload, includeAuthHeaders = true)
        val parsed = json?.let { parseNextPanelTracks(it) } ?: emptyList()
        if (parsed.isNotEmpty()) return@withContext parsed

        // Secondary /next attempt with RDAMVM seed if primary playlistId returned empty
        if (!resolvedSeedId.isNullOrBlank() && cleanPlId != "RDAMVM$resolvedSeedId") {
            val fallbackPayload = JSONObject().apply {
                put("context", buildWebRemixContextJson())
                put("isAudioOnly", true)
                put("playlistId", "RDAMVM$resolvedSeedId")
                put("videoId", resolvedSeedId)
            }
            val fallbackJson = executeInnerTubePost(nextUrl, fallbackPayload, includeAuthHeaders = true)
            val fallbackTracks = fallbackJson?.let { parseNextPanelTracks(it) } ?: emptyList()
            if (fallbackTracks.isNotEmpty()) return@withContext fallbackTracks
        }

        // Final fallback to curated search
        searchCatalog(station.title).songs
    }

    suspend fun resolvePlaylistTracks(item: RecommendedPlaylistItem): List<TrackItem> = withContext(Dispatchers.IO) {
        val rawId = item.browseId?.takeIf { it.isNotBlank() } ?: item.playlistId
        if (rawId.isBlank()) {
            return@withContext searchCatalog(item.title).songs
        }

        val browseId = when {
            rawId.startsWith("VL") -> rawId
            rawId.startsWith("PL") || rawId.startsWith("RDCLAK") || rawId.startsWith("OLAK") -> "VL$rawId"
            rawId.startsWith("RD") -> "VL$rawId"
            else -> "VL$rawId"
        }

        val payload = JSONObject().apply {
            put("context", buildWebRemixContextJson())
            put("browseId", browseId)
        }

        val browseJson = executeInnerTubePost(browseUrl, payload, includeAuthHeaders = true)
        val browseTracks = browseJson?.let { parsePlaylistBrowseTracks(it) } ?: emptyList()
        if (browseTracks.isNotEmpty()) return@withContext browseTracks

        // Some personal mixes resolve better via /next
        if (item.playlistId.startsWith("RD")) {
            val radioTracks = resolveRadioStationTracks(
                RadioStationItem(
                    id = item.id,
                    title = item.title,
                    subtitle = item.subtitle,
                    artworkUri = item.artworkUri,
                    playlistId = item.playlistId
                )
            )
            if (radioTracks.isNotEmpty()) return@withContext radioTracks
        }

        searchCatalog(item.title).songs
    }

    internal fun parseNextPanelTracks(json: JSONObject): List<TrackItem> {
        val tracks = mutableListOf<TrackItem>()
        val seenIds = mutableSetOf<String>()
        extractNextPanelTracksRecursive(json, tracks, seenIds)
        return tracks
    }

    private fun extractNextPanelTracksRecursive(
        node: Any?,
        results: MutableList<TrackItem>,
        seenIds: MutableSet<String>
    ) {
        if (node == null) return
        when (node) {
            is JSONObject -> {
                if (node.has("playlistPanelVideoRenderer")) {
                    val renderer = node.optJSONObject("playlistPanelVideoRenderer")
                    if (renderer != null) {
                        parsePlaylistPanelVideoRenderer(renderer)?.let { track ->
                            if (seenIds.add(track.id)) {
                                results.add(track)
                            }
                        }
                    }
                    return
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    extractNextPanelTracksRecursive(node.opt(keys.next()), results, seenIds)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    extractNextPanelTracksRecursive(node.opt(i), results, seenIds)
                }
            }
        }
    }

    internal fun parsePlaylistPanelVideoRenderer(renderer: JSONObject): TrackItem? {
        val creditsAtvId = extractTrackCreditsAtvVideoId(renderer)
        val videoId = creditsAtvId ?: renderer.optString("videoId").trim().ifBlank {
            renderer.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId").orEmpty().trim()
        }
        if (videoId.isBlank()) return null

        val title = extractAllRunsText(renderer.optJSONObject("title")).trim()
        if (title.isBlank()) return null

        val bylineObj = renderer.optJSONObject("longBylineText") ?: renderer.optJSONObject("shortBylineText")
        val wrappedFlexCol = bylineObj?.let {
            JSONObject().put(
                "musicResponsiveListItemFlexColumnRenderer",
                JSONObject().put("text", it)
            )
        }
        val meta = parseMetadataRuns(
            flexColumn = wrappedFlexCol,
            defaultArtist = "YouTube Artist",
            defaultAlbum = "Single"
        )

        val lengthStr = extractAllRunsText(renderer.optJSONObject("lengthText")).trim()
        val durationMs = if (lengthStr.contains(":")) parseDuration(lengthStr) else meta.durationMs.takeIf { it > 0L } ?: 210000L

        val artwork = extractThumbUrlFromObject(renderer.optJSONObject("thumbnail"))
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        return TrackItem(
            id = "ytm_$videoId",
            title = title,
            artist = meta.artist.ifBlank { "YouTube Artist" },
            album = meta.album.ifBlank { "Single" },
            durationMs = durationMs,
            artworkUri = artwork,
            audioUrl = "https://www.youtube.com/watch?v=$videoId",
            playCount = meta.playCount,
            isExplicit = hasExplicitBadge(renderer)
        )
    }

    internal fun parsePlaylistBrowseTracks(json: JSONObject): List<TrackItem> {
        val tracks = mutableListOf<TrackItem>()
        val seenIds = mutableSetOf<String>()
        val headerCover = findHeaderRendererRecursive(json)
            ?.let { extractHighResAvatarFromRenderer(it) }
            .orEmpty()
        val twoColSecondary = json.optJSONObject("contents")
            ?.optJSONObject("twoColumnBrowseResultsRenderer")
            ?.optJSONObject("secondaryContents")
        if (twoColSecondary != null) {
            extractTracksRecursive(
                jsonObj = twoColSecondary,
                results = tracks,
                seenIds = seenIds,
                isPlaylistContext = true,
                playlistFallbackCover = headerCover
            )
        }
        if (tracks.isEmpty()) {
            extractTracksRecursive(
                jsonObj = json,
                results = tracks,
                seenIds = seenIds,
                isPlaylistContext = true,
                playlistFallbackCover = headerCover
            )
        }
        return tracks
    }

    private fun extractBrowseSections(json: JSONObject): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        val contentsObj = json.optJSONObject("contents")

        fun appendTabSections(tabs: JSONArray?) {
            if (tabs == null) return
            for (t in 0 until tabs.length()) {
                val contents = tabs.optJSONObject(t)
                    ?.optJSONObject("tabRenderer")
                    ?.optJSONObject("content")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents")
                if (contents != null) {
                    for (i in 0 until contents.length()) {
                        contents.optJSONObject(i)?.let { result.add(it) }
                    }
                }
            }
        }

        appendTabSections(
            contentsObj?.optJSONObject("singleColumnBrowseResultsRenderer")?.optJSONArray("tabs")
        )
        val twoCol = contentsObj?.optJSONObject("twoColumnBrowseResultsRenderer")
        appendTabSections(twoCol?.optJSONArray("tabs"))
        val twoColSecondary = twoCol?.optJSONObject("secondaryContents")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents")
        if (twoColSecondary != null) {
            for (i in 0 until twoColSecondary.length()) {
                twoColSecondary.optJSONObject(i)?.let { result.add(it) }
            }
        }

        val contContents = json.optJSONObject("continuationContents")
            ?.optJSONObject("sectionListContinuation")
            ?.optJSONArray("contents")
        if (contContents != null) {
            for (i in 0 until contContents.length()) {
                contContents.optJSONObject(i)?.let { result.add(it) }
            }
        }
        val shelfCont = json.optJSONObject("continuationContents")
            ?.optJSONObject("musicShelfContinuation")
        if (shelfCont != null) {
            result.add(JSONObject().put("musicShelfRenderer", shelfCont))
        }
        return result
    }

    private fun extractNextContinuationToken(json: JSONObject): String? {
        val contentsObj = json.optJSONObject("contents")
        val tabs = contentsObj?.optJSONObject("singleColumnBrowseResultsRenderer")?.optJSONArray("tabs")
            ?: contentsObj?.optJSONObject("twoColumnBrowseResultsRenderer")?.optJSONArray("tabs")
        val initialConts = tabs?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("continuations")
        val token1 = initialConts?.optJSONObject(0)
            ?.optJSONObject("nextContinuationData")
            ?.optString("continuation")
            ?.takeIf { it.isNotBlank() }
        if (token1 != null) return token1

        val conts = json.optJSONObject("continuationContents")
            ?.optJSONObject("sectionListContinuation")
            ?.optJSONArray("continuations")
        return conts?.optJSONObject(0)
            ?.optJSONObject("nextContinuationData")
            ?.optString("continuation")
            ?.takeIf { it.isNotBlank() }
    }

    private fun browsePostWithAnonFallback(payload: JSONObject): JSONObject? {
        val primary = executeInnerTubePostWithStatus(browseUrl, payload, includeAuthHeaders = true)
        if (primary.json != null) return primary.json

        if (authManager?.isSignedIn() == true) {
            if (primary.httpCode == 400 || primary.httpCode == 403) {
                val withoutPageId = executeInnerTubePostWithStatus(
                    browseUrl,
                    payload,
                    includeAuthHeaders = true,
                    includePageId = false
                )
                if (withoutPageId.json != null) return withoutPageId.json
            }
            return executeInnerTubePost(browseUrl, payload, includeAuthHeaders = false)
        }
        return null
    }

    suspend fun fetchMoodsAndGenres(): YtmMoodsAndGenresFeed = withContext(Dispatchers.IO) {
        val now = clockMs()
        moodsAndGenresCache?.takeIf { now < it.expiresAtMs }?.value?.let {
            return@withContext it
        }

        val payload = JSONObject().apply {
            put("context", buildWebRemixContextJson())
            put("browseId", MOODS_AND_GENRES_BROWSE_ID)
        }

        val json = browsePostWithAnonFallback(payload)
            ?: return@withContext YtmMoodsAndGenresFeed()

        val parsed = parseMoodsAndGenresResponse(json)
        if (parsed.sections.isNotEmpty()) {
            val resolvedNow = clockMs()
            moodsAndGenresCache = TimedCacheEntry(
                value = parsed,
                cachedAtMs = resolvedNow,
                expiresAtMs = resolvedNow + DETAIL_CACHE_TTL_MS
            )
        }
        parsed
    }

    internal fun parseMoodsAndGenresResponse(json: JSONObject): YtmMoodsAndGenresFeed {
        val sections = mutableListOf<YtmMoodSection>()
        val seenCategoryIds = mutableSetOf<String>()

        for (section in extractBrowseSections(json)) {
            val grid = section.optJSONObject("gridRenderer")
            val carousel = section.optJSONObject("musicCarouselShelfRenderer")
            val shelf = section.optJSONObject("musicShelfRenderer")

            val sectionTitle = when {
                grid != null -> extractAllRunsText(
                    grid.optJSONObject("header")
                        ?.optJSONObject("gridHeaderRenderer")
                        ?.optJSONObject("title")
                ).trim()
                carousel != null -> extractAllRunsText(
                    carousel.optJSONObject("header")
                        ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                        ?.optJSONObject("title")
                ).trim()
                shelf != null -> extractAllRunsText(shelf.optJSONObject("title")).trim()
                else -> ""
            }

            val rawItems = when {
                grid != null -> grid.optJSONArray("items")
                carousel != null -> carousel.optJSONArray("contents")
                shelf != null -> shelf.optJSONArray("contents")
                else -> null
            } ?: continue

            val parsedItems = mutableListOf<YtmMoodCategory>()
            for (i in 0 until rawItems.length()) {
                val entry = rawItems.optJSONObject(i) ?: continue
                val navButton = entry.optJSONObject("musicNavigationButtonRenderer")
                if (navButton != null) {
                    parseMoodNavigationButton(navButton)?.let { category ->
                        if (seenCategoryIds.add(category.id)) {
                            parsedItems.add(category)
                        }
                    }
                    continue
                }

                entry.optJSONObject("musicTwoRowItemRenderer")?.let { twoRow ->
                    val title = extractAllRunsText(twoRow.optJSONObject("title")).trim()
                    val browseEp = twoRow.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
                    val browseId = browseEp?.optString("browseId").orEmpty().trim()
                    val params = browseEp?.optString("params")?.trim()?.takeIf { it.isNotBlank() }
                    if (title.isNotBlank() && browseId.isNotBlank()) {
                        val uniqueId = "${browseId}_${params ?: title.lowercase(Locale.ROOT)}"
                        if (seenCategoryIds.add(uniqueId)) {
                            parsedItems.add(
                                YtmMoodCategory(
                                    id = uniqueId,
                                    title = title,
                                    browseId = browseId,
                                    params = params
                                )
                            )
                        }
                    }
                }
            }

            if (parsedItems.isNotEmpty()) {
                sections.add(
                    YtmMoodSection(
                        title = sectionTitle.ifBlank { "Moods & Genres" },
                        items = parsedItems
                    )
                )
            }
        }

        return YtmMoodsAndGenresFeed(
            sections = sections,
            isFromYouTubeMusic = sections.isNotEmpty()
        )
    }

    private fun parseMoodNavigationButton(navButton: JSONObject): YtmMoodCategory? {
        val title = extractAllRunsText(navButton.optJSONObject("buttonText")).trim()
        if (title.isBlank()) return null

        val clickEndpoint = navButton.optJSONObject("clickCommand")
            ?: navButton.optJSONObject("navigationEndpoint")
        val browseEndpoint = clickEndpoint?.optJSONObject("browseEndpoint") ?: return null
        val browseId = browseEndpoint.optString("browseId").trim()
        if (browseId.isBlank()) return null

        val params = browseEndpoint.optString("params").trim().takeIf { it.isNotBlank() }
        val rawStripeColor = navButton.optJSONObject("solid")?.optLong("leftStripeColor", 0L) ?: 0L
        val stripeColorArgb = normalizeStripeColorArgb(rawStripeColor)
        val uniqueId = "${browseId}_${params ?: title.lowercase(Locale.ROOT)}"

        return YtmMoodCategory(
            id = uniqueId,
            title = title,
            browseId = browseId,
            params = params,
            stripeColorArgb = stripeColorArgb
        )
    }

    private fun normalizeStripeColorArgb(rawColor: Long): Long {
        if (rawColor == 0L) return 0xFF6366F1L
        val unsigned32 = rawColor and 0xFFFFFFFFL
        return if ((unsigned32 ushr 24) == 0L) {
            unsigned32 or 0xFF000000L
        } else {
            unsigned32
        }
    }

    suspend fun fetchMoodCategoryDetail(
        browseId: String,
        params: String? = null,
        categoryTitle: String = ""
    ): YtmMoodCategoryDetail = withContext(Dispatchers.IO) {
        val cleanBrowseId = browseId.trim()
        val cleanParams = params?.trim()?.takeIf { it.isNotBlank() }
        if (cleanBrowseId.isBlank()) {
            return@withContext YtmMoodCategoryDetail(
                categoryTitle = categoryTitle,
                browseId = cleanBrowseId,
                params = cleanParams
            )
        }

        val cacheKey = "$cleanBrowseId|${cleanParams.orEmpty()}"
        val now = clockMs()
        moodCategoryDetailCache[cacheKey]?.takeIf { now < it.expiresAtMs }?.value?.let { cached ->
            return@withContext if (categoryTitle.isNotBlank() && cached.categoryTitle.isBlank()) {
                cached.copy(categoryTitle = categoryTitle)
            } else {
                cached
            }
        }

        val payload = JSONObject().apply {
            put("context", buildWebRemixContextJson())
            put("browseId", cleanBrowseId)
            if (!cleanParams.isNullOrBlank()) {
                put("params", cleanParams)
            }
        }

        val json = browsePostWithAnonFallback(payload)
            ?: return@withContext YtmMoodCategoryDetail(
                categoryTitle = categoryTitle,
                browseId = cleanBrowseId,
                params = cleanParams
            )

        val detail = parseMoodCategoryDetailResponse(
            json = json,
            browseId = cleanBrowseId,
            params = cleanParams,
            fallbackTitle = categoryTitle
        )
        if (detail.playlists.isNotEmpty() || detail.albums.isNotEmpty() || detail.songs.isNotEmpty()) {
            val resolvedNow = clockMs()
            moodCategoryDetailCache[cacheKey] = TimedCacheEntry(
                value = detail,
                cachedAtMs = resolvedNow,
                expiresAtMs = resolvedNow + DETAIL_CACHE_TTL_MS
            )
        }
        detail
    }

    internal fun parseMoodCategoryDetailResponse(
        json: JSONObject,
        browseId: String,
        params: String?,
        fallbackTitle: String = ""
    ): YtmMoodCategoryDetail {
        val headerTitle = extractAllRunsText(
            json.optJSONObject("header")
                ?.optJSONObject("musicHeaderRenderer")
                ?.optJSONObject("title")
        ).trim().ifBlank {
            findHeaderRendererRecursive(json)?.let {
                extractAllRunsText(it.optJSONObject("title")).trim()
            }.orEmpty()
        }
        val resolvedTitle = fallbackTitle.ifBlank { headerTitle }

        val feed = parseHomeFeedResponse(json, isPersonalized = authManager?.isSignedIn() == true)
        val combinedPlaylists = feed.playlistsMadeForYou.toMutableList()
        val seenPlaylistIds = combinedPlaylists.mapTo(mutableSetOf()) { it.playlistId }
        for (radio in feed.radioStations) {
            if (seenPlaylistIds.add(radio.playlistId)) {
                combinedPlaylists.add(
                    RecommendedPlaylistItem(
                        id = radio.id,
                        title = radio.title,
                        subtitle = radio.subtitle,
                        artworkUri = radio.artworkUri,
                        playlistId = radio.playlistId,
                        browseId = null
                    )
                )
            }
        }

        return YtmMoodCategoryDetail(
            categoryTitle = resolvedTitle,
            browseId = browseId,
            params = params,
            playlists = combinedPlaylists,
            albums = feed.personalizedAlbums,
            songs = feed.personalizedTracks
        )
    }

    private fun extractYtmSeedVideoId(track: TrackItem): String? {
        val isYtmOrigin = track.id.startsWith("ytm_") ||
            track.audioUrl?.contains("youtube.com", ignoreCase = true) == true ||
            track.audioUrl?.contains("youtu.be", ignoreCase = true) == true
        if (!isYtmOrigin) return null
        return extractCleanVideoId(track.id) ?: track.audioUrl?.let { extractCleanVideoId(it) }
    }

    suspend fun fetchSimilarTracksForQueue(
        seedTracks: List<TrackItem>,
        excludeTrackIds: Set<String> = emptySet(),
        limit: Int = 20
    ): List<TrackItem> = withContext(Dispatchers.IO) {
        if (seedTracks.isEmpty() || limit <= 0) return@withContext emptyList()

        val excludedRawIds = mutableSetOf<String>()
        for (rawExcluded in excludeTrackIds) {
            val cleaned = extractCleanVideoId(rawExcluded)
            if (!cleaned.isNullOrBlank()) {
                excludedRawIds.add(cleaned)
                excludedRawIds.add("ytm_$cleaned")
            }
            if (rawExcluded.isNotBlank()) {
                excludedRawIds.add(rawExcluded.trim())
            }
        }

        val seenTitleArtistKeys = mutableSetOf<String>()
        for (seed in seedTracks) {
            if (seed.title.isNotBlank()) {
                seenTitleArtistKeys.add(
                    "${seed.title.trim().lowercase(Locale.ROOT)}::${primaryArtistName(seed.artist).lowercase(Locale.ROOT)}"
                )
            }
            extractYtmSeedVideoId(seed)?.let { seedVid ->
                excludedRawIds.add(seedVid)
                excludedRawIds.add("ytm_$seedVid")
            }
            if (seed.id.isNotBlank()) {
                excludedRawIds.add(seed.id)
            }
        }

        val results = mutableListOf<TrackItem>()

        fun acceptCandidate(candidate: TrackItem) {
            if (results.size >= limit) return
            val rawId = extractCleanVideoId(candidate.id) ?: candidate.audioUrl?.let { extractCleanVideoId(it) }
            if (candidate.id in excludedRawIds || (!rawId.isNullOrBlank() && rawId in excludedRawIds)) {
                return
            }
            val titleArtistKey =
                "${candidate.title.trim().lowercase(Locale.ROOT)}::${primaryArtistName(candidate.artist).lowercase(Locale.ROOT)}"
            if (!seenTitleArtistKeys.add(titleArtistKey)) return

            excludedRawIds.add(candidate.id)
            if (!rawId.isNullOrBlank()) {
                excludedRawIds.add(rawId)
                excludedRawIds.add("ytm_$rawId")
            }
            results.add(candidate)
        }

        val attemptedAtvSeeds = mutableSetOf<String>()
        for (seed in seedTracks.take(3)) {
            if (results.size >= limit) break

            val directYtmId = extractYtmSeedVideoId(seed)
            val resolvedSeedId = when {
                !directYtmId.isNullOrBlank() -> resolveCanonicalAtvVideoId(directYtmId)
                seed.title.isNotBlank() -> {
                    val searchQuery = listOf(seed.title.trim(), primaryArtistName(seed.artist))
                        .filter { it.isNotBlank() && !it.equals("Unknown Artist", ignoreCase = true) }
                        .joinToString(" ")
                    val matched = if (searchQuery.isNotBlank()) {
                        executeInnerTubeSearch(searchQuery, SONGS_PARAM).firstOrNull()
                    } else null
                    val matchedVid = matched?.let { extractYtmSeedVideoId(it) }
                    if (!matchedVid.isNullOrBlank()) resolveCanonicalAtvVideoId(matchedVid) else ""
                }
                else -> ""
            }

            if (resolvedSeedId.isBlank() || !attemptedAtvSeeds.add(resolvedSeedId)) continue
            excludedRawIds.add(resolvedSeedId)
            excludedRawIds.add("ytm_$resolvedSeedId")

            val nextPayload = JSONObject().apply {
                put("context", buildWebRemixContextJson())
                put("isAudioOnly", true)
                put("videoId", resolvedSeedId)
                put("playlistId", "RDAMVM$resolvedSeedId")
            }
            val nextJson = executeInnerTubePost(nextUrl, nextPayload, includeAuthHeaders = true) ?: continue
            for (candidate in parseNextPanelTracks(nextJson)) {
                acceptCandidate(candidate)
                if (results.size >= limit) break
            }
        }

        if (results.isEmpty()) {
            val primarySeed = seedTracks.first()
            val cleanArtist = primaryArtistName(primarySeed.artist)
                .takeIf {
                    it.isNotBlank() &&
                        !it.equals("Unknown Artist", ignoreCase = true) &&
                        !it.equals("YouTube Artist", ignoreCase = true)
                }
                .orEmpty()
            val cleanTitle = primarySeed.title.trim()
            val fallbackQueries = listOfNotNull(
                listOf(cleanArtist, cleanTitle).filter { it.isNotBlank() }.joinToString(" ").takeIf { it.isNotBlank() },
                cleanArtist.takeIf { it.isNotBlank() && !it.equals(cleanTitle, ignoreCase = true) }
            ).distinct()

            for (query in fallbackQueries) {
                if (results.size >= limit) break
                for (candidate in executeInnerTubeSearch(query, SONGS_PARAM)) {
                    acceptCandidate(candidate)
                    if (results.size >= limit) break
                }
            }
        }

        results.take(limit)
    }

    private fun extractThumbUrlFromObject(thumbObj: JSONObject?): String? {
        val thumbs = thumbObj?.optJSONArray("thumbnails") ?: return null
        for (i in thumbs.length() - 1 downTo 0) {
            val url = normalizeHighResImageUrl(thumbs.optJSONObject(i)?.optString("url"))
            if (url != null) return url
        }
        return null
    }

    private fun extractBrowsePageType(navEp: JSONObject?): String {
        return navEp?.optJSONObject("browseEndpoint")
            ?.optJSONObject("browseEndpointContextSupportedConfigs")
            ?.optJSONObject("browseEndpointContextMusicConfig")
            ?.optString("pageType")
            .orEmpty()
    }

    private fun isRadioShelf(shelfTitle: String): Boolean {
        val lower = shelfTitle.lowercase(Locale.ROOT)
        return lower.contains("radio") ||
            lower.contains("station") ||
            lower.contains("estación") ||
            lower.contains("estaciones")
    }

    private fun isPlaylistOrMixShelf(shelfTitle: String): Boolean {
        val lower = shelfTitle.lowercase(Locale.ROOT)
        return lower.contains("mix") ||
            lower.contains("playlist") ||
            lower.contains("for you") ||
            lower.contains("para ti") ||
            lower.contains("focus") ||
            lower.contains("workout") ||
            lower.contains("mood") ||
            lower.contains("featured") ||
            lower.contains("community") ||
            lower.contains("hecho para ti")
    }

    private fun isNewReleaseShelf(shelfTitle: String): Boolean {
        val lower = shelfTitle.lowercase(Locale.ROOT)
        return lower.contains("new release") ||
            lower.contains("new album") ||
            lower.contains("new music") ||
            lower.contains("novedad") ||
            lower.contains("nuevo") ||
            lower.contains("nuevos") ||
            lower.contains("reciente")
    }

    private fun extractArtistFromSubtitle(subtitle: String): String {
        if (subtitle.isBlank()) return ""
        val parts = subtitle.split("•").map { it.trim() }.filter { it.isNotBlank() }
        val filtered = parts.filterNot { part ->
            isMetadataBadgeLabel(part) ||
                part.equals("Video musical", ignoreCase = true) ||
                part.equals("Music video", ignoreCase = true) ||
                part.matches(YEAR_REGEX) ||
                isPlayCountLabel(part)
        }
        return filtered.firstOrNull() ?: ""
    }

    private fun extractPlaylistAuthor(subtitle: String): String? {
        if (subtitle.isBlank()) return null
        val parts = subtitle.split("•").map { it.trim() }.filter { it.isNotBlank() }
        return parts.firstOrNull { part ->
            !part.equals("Playlist", ignoreCase = true) &&
                !part.contains("song", ignoreCase = true) &&
                !part.contains("track", ignoreCase = true) &&
                !part.contains("cancion", ignoreCase = true)
        }
    }

    private fun extractTrackCount(subtitle: String): String? {
        if (subtitle.isBlank()) return null
        return subtitle.split("•").map { it.trim() }.firstOrNull { part ->
            part.contains("song", ignoreCase = true) ||
                part.contains("track", ignoreCase = true) ||
                part.contains("cancion", ignoreCase = true)
        }
    }
}
