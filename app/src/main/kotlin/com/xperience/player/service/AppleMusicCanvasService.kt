package com.xperience.player.service

import android.content.Context
import android.net.Uri
import android.util.Log
import com.xperience.player.domain.model.canvas.CanvasArtworkResult
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
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Base64
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern
import kotlin.math.abs

/**
 * Resolved Apple Music editorial media assets for an artist page or player ambient theme.
 */
data class ArtistAssetsResult(
    val appleArtistId: String = "",
    val artistName: String = "",
    val wordmarkUri: String? = null,
    val headerVideoUri: String? = null,
    val bannerUri: String? = null,
    val bgColorHex: String? = null,
    val keyColorHex: String? = null
)

/**
 * High-speed resolver for Apple Music Album Canvas videos and artist editorial media.
 *
 * Designed for low-latency playback and progressive UI rendering:
 * - Resolves animated Canvas strictly by validated album title + artist (never by song title or
 *   song-only fallback feeds, preventing wrong-release Canvas bleed-through across albums/EPs).
 * - Returns HLS `.m3u8` / direct `.mp4` streams immediately for live playback while delegating
 *   offline `.canvas.mp4` transcoding/download strictly to [resolveDownloadableCanvasArtwork].
 * - Uses single-flight `inFlight` coalescing so concurrent requests for the same artist or album
 *   execute at most one upstream pipeline.
 * - Uses Apple Music AMP JSON API (`/search` + `/albums/{id}` + `/artists/{id}`) backed by TTL
 *   disk caches (hits 7d, confirmed misses 24h, stale grace 30d) so transient errors never
 *   permanently poison caches.
 */
class AppleMusicCanvasService(
    private val context: Context? = null,
    private val httpClient: OkHttpClient = MediaNetworkClients.appleMusicOkHttpClient,
    private val ampBaseUrl: String = APPLE_MUSIC_BASE_URL,
    private val browseUrl: String = APPLE_MUSIC_BROWSE_URL,
    @Suppress("unused")
    private val echoFeedUrl: String = ECHO_MUSIC_FEED_URL,
    @Suppress("unused")
    private val viviFeedUrl: String = VIVI_MUSIC_FEED_URL,
    private val cacheRootDir: File? = context?.cacheDir,
    private val defaultStorefront: String = "us",
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val logSink: (String) -> Unit = { msg -> runCatching { Log.d(TAG, msg) } }
) {

    companion object {
        private const val TAG = "AppleMusicCanvasService"
        private const val APPLE_MUSIC_BASE_URL = "https://amp-api.music.apple.com/"
        private const val APPLE_MUSIC_BROWSE_URL = "https://music.apple.com/us/browse"
        private const val ECHO_MUSIC_FEED_URL = "https://canvas.echomusic.fun/canvas.json"
        private const val VIVI_MUSIC_FEED_URL = "https://vivimusicanvas.mkmdevilmi.workers.dev/canvas.json"

        private const val TOKEN_PREFS_NAME = "xperience_apple_token_prefs_v2"
        private const val PREF_TOKEN_KEY = "amp_token"
        private const val PREF_TOKEN_EXP_KEY = "amp_token_exp"

        private const val HIT_TTL_MS = 7L * 24L * 60L * 60L * 1000L
        private const val MISS_TTL_MS = 24L * 60L * 60L * 1000L
        private const val STALE_GRACE_MS = 30L * 24L * 60L * 60L * 1000L
        private const val ARTIST_ID_TTL_MS = 30L * 24L * 60L * 60L * 1000L
        private const val SCRAPE_FAILURE_BACKOFF_MS = 10L * 60L * 1000L
        private const val MAX_ALBUM_PROBES = 3
        private const val MAX_ALBUM_CANVAS_CACHE_ENTRIES = 300
        private const val MAX_ARTIST_CACHE_ENTRIES = 300
        private const val MAX_ARTIST_ID_CACHE_ENTRIES = 400

        private val INVALID_ALBUM_NAMES = setOf(
            "unknown album",
            "youtube music",
            "single",
            "<unknown>",
            "favorites",
            "downloaded",
            "local"
        )

        private val PLAY_COUNT_LABEL_REGEX = Regex(
            "^\\d+(?:[.,]\\d+)?\\s*[kmb]?\\s*(?:views?|plays?|vistas?|reproducciones?)\\b.*$",
            RegexOption.IGNORE_CASE
        )
        private val DURATION_LABEL_REGEX = Regex("^\\d+(?::\\d{2})+$")

        private val SAFE_PAREN_EDITION_REGEX = Regex(
            "\\s*[\\[\\(]\\s*(?:" +
                "(?:Super\\s+)?Deluxe(?:\\s+(?:Edition|Version))?" +
                "|Expanded(?:\\s+(?:Edition|Version))?" +
                "|Remaster(?:ed)?(?:\\s+\\d{4})?" +
                "|\\d{4}\\s+Remaster(?:ed)?" +
                "|(?:Special|Collector'?s?|Anniversary|Standard|Digital|Complete|Bonus(?:\\s+Track(?:s)?)?)\\s+(?:Edition|Version|Collection)" +
                "|Bonus\\s+Track(?:s)?" +
                "|Explicit|Clean" +
            ")[^\\)\\]]*[\\]\\)]",
            RegexOption.IGNORE_CASE
        )
        private val SAFE_EP_PAREN_REGEX = Regex(
            "(?<=\\S)\\s*[\\[\\(]\\s*EP\\s*[\\]\\)]",
            RegexOption.IGNORE_CASE
        )
        private val SAFE_DASH_EDITION_REGEX = Regex(
            "(?<=\\S)\\s+[-–—]\\s+(?:" +
                "EP" +
                "|(?:(?:Super\\s+)?Deluxe|Expanded|Special|Collector'?s?|Anniversary|Standard|Digital|Bonus(?:\\s+Track(?:s)?)?)(?:\\s+(?:Edition|Version))?" +
                "|(?:Remaster(?:ed)?(?:\\s+\\d{4})?|\\d{4}\\s+Remaster(?:ed)?)" +
            ")\\s*$",
            RegexOption.IGNORE_CASE
        )

        private val STRICT_ALBUM_MODIFIER_REGEXES = listOf(
            Regex("\\bremix(?:es)?\\b", RegexOption.IGNORE_CASE),
            Regex("\\blive\\b", RegexOption.IGNORE_CASE),
            Regex("\\bacoustic\\b", RegexOption.IGNORE_CASE),
            Regex("\\bsped\\s+up\\b", RegexOption.IGNORE_CASE),
            Regex("\\bslowed\\b", RegexOption.IGNORE_CASE),
            Regex("\\bnightcore\\b", RegexOption.IGNORE_CASE),
            Regex("\\binstrumental(?:s)?\\b", RegexOption.IGNORE_CASE),
            Regex("\\bkaraoke\\b", RegexOption.IGNORE_CASE),
            Regex("\\btribute\\b", RegexOption.IGNORE_CASE),
            Regex("\\bdj\\s+mix\\b", RegexOption.IGNORE_CASE),
            Regex("\\bessentials\\b", RegexOption.IGNORE_CASE),
            Regex("\\bgreatest\\s+hits\\b", RegexOption.IGNORE_CASE),
            Regex("\\bbest\\s+of\\b", RegexOption.IGNORE_CASE),
            Regex("\\banthology\\b", RegexOption.IGNORE_CASE),
            Regex("\\bcompilation\\b", RegexOption.IGNORE_CASE),
            Regex("(?:[-–—]\\s*single\\b|\\(\\s*single\\s*\\)|\\[\\s*single\\s*\\])", RegexOption.IGNORE_CASE)
        )

        private val FALLBACK_SEED_TOKEN =
            "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6IldlYlBsYXlLaWQifQ" +
                ".eyJpc3MiOiJBTVBXZWJQbGF5IiwiaWF0IjoxNzc0NDU2MzgyLCJleHAiOjE3ODE3" +
                "MTM5ODIsInJvb3RfaHR0cHNfb3JpZ2luIjpbImFwcGxlLmNvbSJdfQ" +
                ".4n8qYF4qa18sL1E0G9A3qX35cD8wQ-IJcS9Bh8ZT8JV_yLBtVq46B-9-2ZS3EvWHuw3yK9BYFYAhAdTaDm38vQ"

        private val PREFERRED_CANVAS_VIDEO_VARIANTS = listOf(
            "motionDetailSquare",
            "motionSquareVideo1x1",
            "motionDetailRaw",
            "motionDetailTall",
            "motionTallVideo3x4",
            "motionDetailStatic"
        )

        private val PREFERRED_ARTIST_VIDEO_VARIANTS = listOf(
            "motionArtistFullscreen16x9",
            "motionArtistWide16x9",
            "wideVideoArtwork",
            "motionDetailWide",
            "motionDetailRaw",
            "motionArtistSquare1x1",
            "motionDetailSquare",
            "motionSquareVideo1x1",
            "motionDetailTall",
            "motionTallVideo3x4"
        )

        private val WORDMARK_ARTWORK_KEYS = listOf(
            "musicContentWhiteLogoTrimmed",
            "musicContentColorLogoTrimmed",
            "artistLogo",
            "centeredLogo",
            "brandLogo",
            "editorialLogo"
        )

        private val HERO_ARTWORK_KEYS = listOf(
            "bannerUber",
            "artistBackground",
            "centeredBanner",
            "superHeroWide",
            "storeFlowcase",
            "hero",
            "subscriptionHero"
        )

        private val ARTIST_STOP_WORDS = setOf(
            "the", "and", "feat", "featuring", "ft", "with", "los", "las", "el", "la",
            "de", "del", "dj", "mc", "vs", "by"
        )

        private val DIACRITICS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")
        private val TOKEN_SPLIT_REGEX = Regex("[^a-z0-9]+")
        private val FEATURE_DELIM_REGEX = Regex("\\s*(?:\\bfeat\\.?\\b|\\bft\\.?\\b|\\bfeaturing\\b|,|&|;|/|\\+|\\bx\\b)\\s*", RegexOption.IGNORE_CASE)
        private val META_ENV_REGEX = Pattern.compile(
            "<meta[^>]+name=[\"']desktop-music-app/config/environment[\"'][^>]+content=[\"']([^\"']+)[\"']|" +
                "<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+name=[\"']desktop-music-app/config/environment[\"']",
            Pattern.CASE_INSENSITIVE
        )
        private val SCRIPT_SRC_REGEX = Pattern.compile(
            "<script[^>]+src=[\"']([^\"']*?/assets/[^\"']*\\.js)[\"']",
            Pattern.CASE_INSENSITIVE
        )
        private val JWT_REGEX = Pattern.compile("eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}")
        private val RESOLUTION_REGEX = Pattern.compile("RESOLUTION=(\\d+)x(\\d+)", Pattern.CASE_INSENSITIVE)
        private val BANDWIDTH_REGEX = Pattern.compile("BANDWIDTH=(\\d+)", Pattern.CASE_INSENSITIVE)
        private val EXT_X_MAP_URI_REGEX = Pattern.compile("#EXT-X-MAP:URI=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE)

        private val ARTIST_LOGO_REGEX = Pattern.compile(
            "\"(?:musicContentWhiteLogoTrimmed|musicContentColorLogoTrimmed|artistLogo)\"\\s*:\\s*\\{[^}]*?\"url\"\\s*:\\s*\"(https://is\\d-ssl\\.mzstatic\\.com/image/thumb/[^\"]+)\"",
            Pattern.CASE_INSENSITIVE
        )
        private val ARTIST_PICTURE_WORDMARK_REGEX = Pattern.compile(
            "(https://is\\d-ssl\\.mzstatic\\.com/image/thumb/Features[^\"'\\s<>]+?\\.png/(?:800x266|400x133)bb\\.(?:webp|png))",
            Pattern.CASE_INSENSITIVE
        )
        private val ARTIST_WIDE_VIDEO_REGEX = Pattern.compile(
            "\"(?:wideVideoArtwork|motionArtistFullscreen16x9|motionArtistWide16x9|motionDetailRaw|motionArtistSquare1x1)\"[\\s\\S]{1,1500}?\"video\"\\s*:\\s*\"(https://mvod\\.itunes\\.apple\\.com/[^\"]+?\\.m3u8)\"",
            Pattern.CASE_INSENSITIVE
        )

        @Volatile
        private var instance: AppleMusicCanvasService? = null

        fun getInstance(context: Context): AppleMusicCanvasService {
            instance?.let { return it }
            synchronized(this) {
                instance?.let { return it }
                return AppleMusicCanvasService(context = context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private data class CacheEntry<T>(
        val value: T?,
        val updatedAtMs: Long,
        val expiresAtMs: Long,
        val staleUntilMs: Long
    ) {
        fun isFresh(nowMs: Long): Boolean = nowMs < expiresAtMs
        fun isStaleReadable(nowMs: Long): Boolean = value != null && nowMs < staleUntilMs
    }

    private data class CachedArtistId(
        val id: String,
        val name: String,
        val storefront: String,
        val updatedAtMs: Long,
        val expiresAtMs: Long
    )

    private data class EditorialVideoInfo(
        val rawVideoUrl: String,
        val previewUrl: String?,
        val bgColorHex: String? = null
    )

    private data class CandidateSong(
        val score: Int,
        val albumId: String?,
        val directVideo: EditorialVideoInfo?
    )

    private data class CandidateAlbum(
        val score: Int,
        val albumId: String?,
        val albumName: String,
        val artistName: String,
        val directVideo: EditorialVideoInfo?
    )

    private data class AlbumEditorialVideo(
        val video: EditorialVideoInfo,
        val albumId: String,
        val albumName: String,
        val artistName: String
    )

    private data class AmpHttpResponse(
        val code: Int,
        val body: String?,
        val unauthorized: Boolean,
        val error: Boolean
    ) {
        val isSuccess: Boolean get() = !unauthorized && !error && code in 200..299 && body != null
    }

    private data class CanvasLookupOutcome(
        val result: CanvasArtworkResult?,
        val authoritative: Boolean
    )

    private data class ArtistAssetsOutcome(
        val result: ArtistAssetsResult?,
        val authoritative: Boolean
    )

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val diskCacheDir: File? by lazy {
        cacheRootDir?.let { root ->
            File(root, "canvas_cache").apply { if (!exists()) mkdirs() }
        }
    }
    private val metadataCacheDir: File? by lazy {
        cacheRootDir?.let { root ->
            File(root, "apple_metadata_cache_v3").apply { if (!exists()) mkdirs() }
        }
    }

    private val priorityGate = Semaphore(2)
    private val backgroundGate = Semaphore(4)

    private val tokenMutex = Mutex()
    private var cachedBearerToken: String = ""
    private var tokenExpiresAtEpochSec: Long = 0L
    private var lastScrapeFailedAtMs: Long = 0L

    private val albumCanvasCache = ConcurrentHashMap<String, CacheEntry<CanvasArtworkResult>>()
    private val artistAssetsCache = ConcurrentHashMap<String, CacheEntry<ArtistAssetsResult>>()
    private val artistIdCache = ConcurrentHashMap<String, CachedArtistId>()

    private val inFlightAlbumCanvas = ConcurrentHashMap<String, Deferred<CanvasArtworkResult?>>()
    private val inFlightArtistAssets = ConcurrentHashMap<String, Deferred<ArtistAssetsResult?>>()

    private val diskLoaded = AtomicBoolean(false)
    private val diskLoadLock = Any()
    private val persistMutex = Mutex()
    private val persistDirty = AtomicBoolean(false)
    private val persistScheduled = AtomicBoolean(false)

    init {
        restoreInitialToken()
        serviceScope.launch { ensureDiskCachesLoaded() }
    }

    private fun restoreInitialToken() {
        val nowSec = clockMs() / 1000L
        val ctx = context
        if (ctx != null) {
            runCatching {
                val prefs = ctx.getSharedPreferences(TOKEN_PREFS_NAME, Context.MODE_PRIVATE)
                val savedToken = prefs.getString(PREF_TOKEN_KEY, null)
                val savedExp = prefs.getLong(PREF_TOKEN_EXP_KEY, 0L)
                if (!savedToken.isNullOrBlank() && savedExp > nowSec + 60L && isAppleWebPlayJwt(savedToken)) {
                    cachedBearerToken = savedToken
                    tokenExpiresAtEpochSec = savedExp
                    return
                }
            }
        }
        val seedExp = jwtExpEpochSeconds(FALLBACK_SEED_TOKEN) ?: 0L
        if (seedExp > nowSec + 60L && isAppleWebPlayJwt(FALLBACK_SEED_TOKEN)) {
            cachedBearerToken = FALLBACK_SEED_TOKEN
            tokenExpiresAtEpochSec = seedExp
        }
    }

    private fun safeLog(message: String) {
        logSink(message)
    }

    private fun isMainThread(): Boolean {
        return runCatching {
            android.os.Looper.getMainLooper()?.thread === Thread.currentThread()
        }.getOrDefault(false)
    }

    private fun ensureDiskCachesLoaded() {
        if (diskLoaded.get()) return
        synchronized(diskLoadLock) {
            if (diskLoaded.get()) return
            val dir = metadataCacheDir
            if (dir != null) {
                loadCanvasEntriesFile(File(dir, "album_canvas_entries.json"), albumCanvasCache)
                loadArtistAssetsDiskCache(File(dir, "artist_assets.json"))
                loadArtistIdsDiskCache(File(dir, "artist_ids.json"))
            }
            diskLoaded.set(true)
        }
    }

    /**
     * Returns true when [album] names a real album rather than blank or generic placeholder metadata.
     */
    fun isValidAlbumName(album: String?): Boolean {
        val raw = album?.trim().orEmpty()
        if (raw.isBlank()) return false
        if (PLAY_COUNT_LABEL_REGEX.matches(raw) || DURATION_LABEL_REGEX.matches(raw)) return false
        val clean = cleanAlbumName(raw)
        if (clean.isBlank()) return false
        val rawLower = raw.lowercase(Locale.ROOT)
        val cleanLower = clean.lowercase(Locale.ROOT)
        return rawLower !in INVALID_ALBUM_NAMES && cleanLower !in INVALID_ALBUM_NAMES
    }

    /**
     * Canonical normalized cache key for an album + primary artist, or `null` when [album] is invalid.
     */
    fun buildAlbumCacheKey(album: String?, artist: String?): String? {
        if (!isValidAlbumName(album)) return null
        val normAlbum = normalizeKey(cleanAlbumName(album))
        if (normAlbum.isBlank()) return null
        val primaryArtist = cleanPrimaryArtist(artist.orEmpty())
        val normArtist = if (
            primaryArtist.isBlank() ||
            primaryArtist.equals("Unknown Artist", ignoreCase = true) ||
            primaryArtist.equals("<unknown>", ignoreCase = true)
        ) {
            ""
        } else {
            normalizeKey(primaryArtist)
        }
        return "$normAlbum|$normArtist"
    }

    /**
     * Synchronously peeks an album-keyed Canvas entry without performing any network or disk I/O.
     * Returns fresh hits or stale-grace hits while triggering background refresh for stale hits.
     */
    fun peekAlbumCanvasArtwork(
        album: String,
        artist: String
    ): CanvasArtworkResult? {
        if (!isMainThread()) {
            ensureDiskCachesLoaded()
        }
        val albumKey = buildAlbumCacheKey(album, artist) ?: return null
        val now = clockMs()
        val entry = albumCanvasCache[albumKey] ?: return null
        val value = entry.value ?: return null
        if (!isPlayableCanvasValue(value)) return null

        if (entry.isFresh(now)) return value
        if (entry.isStaleReadable(now)) {
            serviceScope.launch {
                runCatching { getAlbumCanvasArtwork(album, artist, priority = false) }
            }
            return value
        }
        return null
    }

    /**
     * Resolves an animated Apple Music Album Canvas keyed by album + primary artist with
     * in-memory TTL cache, disk persistence, and single-flight [inFlightAlbumCanvas] coalescing.
     */
    suspend fun getAlbumCanvasArtwork(
        album: String,
        artist: String,
        priority: Boolean = true
    ): CanvasArtworkResult? = withContext(Dispatchers.IO) {
        ensureDiskCachesLoaded()
        val albumKey = buildAlbumCacheKey(album, artist) ?: return@withContext null
        val cleanAlbum = cleanAlbumName(album)
        val cleanArtist = cleanPrimaryArtist(artist)

        val now = clockMs()
        val existing = albumCanvasCache[albumKey]
        if (existing != null && existing.isFresh(now)) {
            val cached = existing.value
            if (cached == null || isPlayableCanvasValue(cached)) {
                return@withContext cached
            }
        }

        val deferred = inFlightAlbumCanvas.computeIfAbsent(albumKey) {
            serviceScope.async(start = CoroutineStart.LAZY) {
                try {
                    withGate(priority) {
                        val outcome = resolveAlbumCanvasUncached(cleanAlbum, cleanArtist, album, artist)
                        val validResult = outcome.result?.takeIf { isPlayableCanvasValue(it) }
                        if (validResult != null) {
                            storeAlbumCanvasEntry(albumKey, validResult, isMiss = false)
                            validResult
                        } else if (outcome.authoritative) {
                            val stale = existing?.takeIf { it.isStaleReadable(clockMs()) }?.value
                            if (stale == null) {
                                storeAlbumCanvasEntry(albumKey, null, isMiss = true)
                            }
                            stale
                        } else {
                            existing?.takeIf { it.isStaleReadable(clockMs()) }?.value
                        }
                    }
                } finally {
                    inFlightAlbumCanvas.remove(albumKey)
                }
            }
        }

        deferred.await()
    }

    /**
     * Fire-and-forget album Canvas prefetching + Media3 SimpleCache warming.
     */
    fun prefetchAlbumCanvasArtwork(
        album: String,
        artist: String
    ) {
        if (!isValidAlbumName(album)) return
        serviceScope.launch {
            runCatching {
                ensureDiskCachesLoaded()
                val albumKey = buildAlbumCacheKey(album, artist) ?: return@runCatching
                val existing = albumCanvasCache[albumKey]
                if (existing != null && existing.isFresh(clockMs())) {
                    warmCacheIfDirectHttp(existing.value)
                    return@runCatching
                }
                val res = getAlbumCanvasArtwork(album, artist, priority = false)
                warmCacheIfDirectHttp(res)
            }
        }
    }

    /**
     * Synchronously peeks cached Canvas strictly by validated [album] + [artist].
     * Never matches or returns Canvas by track [title].
     */
    fun peekCanvasArtwork(
        @Suppress("UNUSED_PARAMETER") title: String,
        artist: String,
        album: String? = null
    ): CanvasArtworkResult? {
        if (!isValidAlbumName(album)) return null
        return peekAlbumCanvasArtwork(album!!, artist)
    }

    /**
     * Resolves Apple Music Canvas strictly from validated [album] + [artist].
     * Returns `null` when [album] is missing/generic or when that album has no Apple Canvas,
     * never falling back to song-title searches.
     */
    suspend fun getCanvasArtwork(
        @Suppress("UNUSED_PARAMETER") title: String,
        artist: String,
        album: String? = null,
        priority: Boolean = true
    ): CanvasArtworkResult? = withContext(Dispatchers.IO) {
        ensureDiskCachesLoaded()
        if (!isValidAlbumName(album)) return@withContext null
        getAlbumCanvasArtwork(album!!, artist, priority)
            ?.takeIf { isPlayableCanvasValue(it) }
    }

    /**
     * Fire-and-forget Canvas resolution for queue-next / catalog prefetching using background
     * priority, strictly scoped to validated [album] + [artist].
     */
    fun prefetchCanvasArtwork(
        @Suppress("UNUSED_PARAMETER") title: String,
        artist: String,
        album: String? = null
    ) {
        if (!isValidAlbumName(album)) return
        prefetchAlbumCanvasArtwork(album!!, artist)
    }

    private suspend fun warmCacheIfDirectHttp(result: CanvasArtworkResult?) {
        if (context != null && result != null && !result.isHls && result.videoUri.startsWith("http", ignoreCase = true)) {
            MediaNetworkClients.warmCanvasVideoCache(context, result.videoUri)
        }
    }

    /**
     * Resolves an offline-downloadable `.mp4` Canvas file/URL specifically for background downloads
     * (`MornyeMusicDownloadService`), performing HLS-to-AVC MP4 resolution and edit-list cleanup
     * away from live UI playback paths.
     */
    suspend fun resolveDownloadableCanvasArtwork(
        title: String,
        artist: String,
        album: String? = null
    ): CanvasArtworkResult? = withContext(Dispatchers.IO) {
        val resolved = getCanvasArtwork(title, artist, album, priority = false) ?: return@withContext null
        val uri = resolved.videoUri
        if (!uri.startsWith("http", ignoreCase = true)) {
            return@withContext resolved
        }

        var directMp4Url = uri
        if (uri.contains(".m3u8", ignoreCase = true)) {
            directMp4Url = resolveHlsMasterPlaylistToAvcMp4(uri) ?: return@withContext resolved
        }

        val downloadedPath = downloadToCacheIfPossible(directMp4Url)
        val finalUri = downloadedPath ?: directMp4Url
        CanvasArtworkResult(
            videoUri = finalUri,
            providerName = resolved.providerName,
            previewFrameUri = resolved.previewFrameUri,
            isHls = finalUri.contains(".m3u8", ignoreCase = true)
        )
    }

    /**
     * Synchronously peeks cached artist assets (`wordmarkUri`, `headerVideoUri`, `bannerUri`, `bgColorHex`).
     * Returns immediately for fresh or stale-grace hits without blocking on network I/O.
     */
    fun peekArtistAssets(artistName: String): ArtistAssetsResult? {
        val cleanArtist = cleanPrimaryArtist(artistName)
        if (!isValidArtistQuery(cleanArtist)) return null
        val key = normalizeKey(cleanArtist)
        if (key.isBlank()) return null

        val now = clockMs()
        val entry = artistAssetsCache[key] ?: return null
        val value = entry.value ?: return null
        if (entry.isFresh(now)) return value
        if (entry.isStaleReadable(now)) {
            serviceScope.launch {
                runCatching { getArtistAssets(artistName, priority = false) }
            }
            return value
        }
        return null
    }

    /**
     * Resolves Apple Music artist editorial media (wordmark logo, motion header video, hero banner,
     * and palette colors) via AMP JSON API with single-flight coalescing.
     */
    suspend fun getArtistAssets(
        artistName: String,
        priority: Boolean = true
    ): ArtistAssetsResult? = withContext(Dispatchers.IO) {
        ensureDiskCachesLoaded()
        val cleanArtist = cleanPrimaryArtist(artistName)
        if (!isValidArtistQuery(cleanArtist)) return@withContext null

        val key = normalizeKey(cleanArtist)
        if (key.isBlank()) return@withContext null

        val now = clockMs()
        val existing = artistAssetsCache[key]
        if (existing != null && existing.isFresh(now)) {
            return@withContext existing.value
        }

        val deferred = inFlightArtistAssets.computeIfAbsent(key) {
            serviceScope.async(start = CoroutineStart.LAZY) {
                try {
                    withGate(priority) {
                        val outcome = fetchArtistAssetsUncached(cleanArtist)
                        if (outcome.result != null) {
                            storeArtistAssetsEntry(key, outcome.result, isMiss = false)
                            outcome.result
                        } else if (outcome.authoritative) {
                            val stale = existing?.takeIf { it.isStaleReadable(clockMs()) }?.value
                            if (stale == null) {
                                storeArtistAssetsEntry(key, null, isMiss = true)
                            }
                            stale
                        } else {
                            existing?.takeIf { it.isStaleReadable(clockMs()) }?.value
                        }
                    }
                } finally {
                    inFlightArtistAssets.remove(key)
                }
            }
        }

        deferred.await()
    }

    /**
     * Background prefetch for multiple artists (for example, artist detail similar-artists lists),
     * bounded to low-priority concurrency and skipping already-cached entries.
     */
    fun prefetchArtists(artistNames: Collection<String>, maxCount: Int = 6) {
        if (artistNames.isEmpty() || maxCount <= 0) return
        val targets = artistNames
            .asSequence()
            .map { cleanPrimaryArtist(it) }
            .filter { isValidArtistQuery(it) }
            .distinctBy { normalizeKey(it) }
            .take(maxCount)
            .toList()
        if (targets.isEmpty()) return

        serviceScope.launch {
            ensureDiskCachesLoaded()
            val now = clockMs()
            val pending = targets.filter { name ->
                val entry = artistAssetsCache[normalizeKey(name)]
                entry == null || !entry.isFresh(now)
            }
            if (pending.isEmpty()) return@launch

            coroutineScope {
                pending.map { name ->
                    async {
                        runCatching { getArtistAssets(name, priority = false) }
                    }
                }.awaitAll()
            }
        }
    }

    suspend fun getArtistWordmarkLogoUri(artistName: String): String? {
        return getArtistAssets(artistName, priority = true)?.wordmarkUri
    }

    suspend fun getArtistHeaderVideoUri(artistName: String): String? {
        return getArtistAssets(artistName, priority = true)?.headerVideoUri
    }

    private suspend fun <T> withGate(priority: Boolean, block: suspend () -> T): T {
        val gate = if (priority) priorityGate else backgroundGate
        return gate.withPermit { block() }
    }

    private fun isPlayableCanvasValue(result: CanvasArtworkResult): Boolean {
        val uri = result.videoUri.trim()
        if (uri.isEmpty()) return false
        return when {
            uri.startsWith("http://", ignoreCase = true) ||
                uri.startsWith("https://", ignoreCase = true) ||
                uri.startsWith("content://", ignoreCase = true) -> true
            uri.startsWith("file://", ignoreCase = true) -> {
                runCatching { android.net.Uri.parse(uri).path?.let { File(it).exists() } == true }.getOrDefault(false)
            }
            else -> File(uri).exists()
        }
    }

    private suspend fun resolveAlbumCanvasUncached(
        cleanAlbum: String,
        cleanArtist: String,
        rawAlbum: String,
        rawArtist: String
    ): CanvasLookupOutcome {
        var anyAppleError = false
        val storefronts = if (defaultStorefront.equals("gb", ignoreCase = true)) {
            listOf("gb", "us")
        } else {
            listOf(defaultStorefront, "gb")
        }

        for (storefront in storefronts) {
            val searchRes = searchAlbumStorefront(storefront, cleanAlbum, cleanArtist)
            when (searchRes.status) {
                SearchStatus.SUCCESS -> {
                    searchRes.canvas?.let { result ->
                        crossPopulateAlbumCanvasCache(rawAlbum, rawArtist, result)
                    }
                    return CanvasLookupOutcome(searchRes.canvas, authoritative = true)
                }
                SearchStatus.ERROR, SearchStatus.UNAUTHORIZED -> anyAppleError = true
                SearchStatus.NOT_FOUND -> Unit
            }
        }

        return CanvasLookupOutcome(result = null, authoritative = !anyAppleError)
    }

    private suspend fun searchAlbumStorefront(
        storefront: String,
        album: String,
        artist: String
    ): SearchResult {
        val query = if (!isValidArtistQuery(artist) || album.contains(artist, ignoreCase = true)) {
            album
        } else {
            "$artist $album"
        }.trim()

        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val path = "v1/catalog/$storefront/search?term=$encodedQuery&types=albums,songs&limit=10&extend=editorialVideo&include=albums"

        val resp = executeAmpGet(path)
        if (resp.unauthorized) return SearchResult(SearchStatus.UNAUTHORIZED, null)
        if (!resp.isSuccess) return SearchResult(SearchStatus.ERROR, null)

        return try {
            val root = JSONObject(resp.body!!)
            val resultsObj = root.optJSONObject("results") ?: return SearchResult(SearchStatus.NOT_FOUND, null)

            val normArtist = normalizeKey(artist)
            val normTarget = normalizeKey(cleanAlbumName(album))
            if (normTarget.isBlank()) return SearchResult(SearchStatus.NOT_FOUND, null)

            val candidateAlbums = mutableListOf<CandidateAlbum>()
            val albumsData = resultsObj.optJSONObject("albums")?.optJSONArray("data")
            if (albumsData != null) {
                for (i in 0 until albumsData.length()) {
                    val item = albumsData.optJSONObject(i) ?: continue
                    val attr = item.optJSONObject("attributes") ?: continue
                    val itemName = attr.optString("name", "")
                    val itemArtist = attr.optString("artistName", "")
                    if (isValidArtistQuery(artist) && !artistMatches(itemArtist, artist)) continue
                    if (!albumNamesMatch(itemName, album)) continue

                    var score = 100
                    if (itemName.trim().equals(album.trim(), ignoreCase = true)) {
                        score += 15
                    }
                    if (normArtist.isNotBlank() && normalizeKey(cleanPrimaryArtist(itemArtist)) == normArtist) {
                        score += 35
                    }

                    val directVideo = extractEditorialVideoInfo(attr, PREFERRED_CANVAS_VIDEO_VARIANTS)
                    val albumId = item.optString("id").takeIf { it.isNotBlank() }
                    candidateAlbums.add(CandidateAlbum(score, albumId, itemName, itemArtist, directVideo))
                }
            }

            val rankedAlbums = candidateAlbums.sortedByDescending { it.score }
            rankedAlbums.firstOrNull { it.directVideo != null }?.let { match ->
                val result = toCanvasResult(
                    match.directVideo!!.rawVideoUrl,
                    match.directVideo.previewUrl,
                    albumId = match.albumId
                )
                crossPopulateAlbumCanvasCache(match.albumName, match.artistName, result)
                return SearchResult(SearchStatus.SUCCESS, result)
            }

            val matchingSongs = mutableListOf<CandidateSong>()
            val songsData = resultsObj.optJSONObject("songs")?.optJSONArray("data")
            if (songsData != null) {
                for (i in 0 until songsData.length()) {
                    val item = songsData.optJSONObject(i) ?: continue
                    val attr = item.optJSONObject("attributes") ?: continue
                    val itemArtist = attr.optString("artistName", "")
                    val itemAlbum = attr.optString("albumName", "")
                    if (isValidArtistQuery(artist) && !artistMatches(itemArtist, artist)) continue
                    // Strict album matching only: never fall back to song title matching when albumName is blank.
                    if (itemAlbum.isBlank() || !albumNamesMatch(itemAlbum, album)) continue

                    var score = 100
                    if (itemAlbum.trim().equals(album.trim(), ignoreCase = true)) {
                        score += 15
                    }
                    if (normArtist.isNotBlank() && normalizeKey(cleanPrimaryArtist(itemArtist)) == normArtist) {
                        score += 35
                    }

                    val directVideo = extractEditorialVideoInfo(attr, PREFERRED_CANVAS_VIDEO_VARIANTS)
                    val albumId = extractAlbumIdFromSong(item, attr)
                    matchingSongs.add(CandidateSong(score, albumId, directVideo))
                }
            }

            val rankedMatchingSongs = matchingSongs.sortedByDescending { it.score }
            val topAlbumIds = (rankedAlbums.mapNotNull { it.albumId } + rankedMatchingSongs.mapNotNull { it.albumId })
                .distinct()
                .take(MAX_ALBUM_PROBES)

            if (topAlbumIds.isNotEmpty()) {
                probeFirstAlbumEditorialCanvas(
                    storefront = storefront,
                    albumIds = topAlbumIds,
                    fallbackAlbum = album,
                    fallbackArtist = artist
                )?.let { result ->
                    return SearchResult(SearchStatus.SUCCESS, result)
                }
            }

            rankedMatchingSongs.firstOrNull { it.directVideo != null }?.let { songMatch ->
                val result = toCanvasResult(
                    songMatch.directVideo!!.rawVideoUrl,
                    songMatch.directVideo.previewUrl,
                    albumId = songMatch.albumId
                )
                return SearchResult(SearchStatus.SUCCESS, result)
            }

            SearchResult(SearchStatus.NOT_FOUND, null)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            safeLog("Storefront album search parse failed ($storefront): ${e.message}")
            SearchResult(SearchStatus.ERROR, null)
        }
    }

    private enum class SearchStatus {
        SUCCESS,
        NOT_FOUND,
        UNAUTHORIZED,
        ERROR
    }

    private data class SearchResult(val status: SearchStatus, val canvas: CanvasArtworkResult?)

    private suspend fun probeFirstAlbumEditorialCanvas(
        storefront: String,
        albumIds: List<String>,
        fallbackAlbum: String,
        fallbackArtist: String
    ): CanvasArtworkResult? {
        if (albumIds.isEmpty() || !isValidAlbumName(fallbackAlbum)) return null
        val probedVideos = coroutineScope {
            albumIds.map { albumId ->
                async { fetchAlbumEditorialVideo(storefront, albumId) }
            }.awaitAll()
        }
        val albumVideo = probedVideos
            .filterNotNull()
            .firstOrNull { probed ->
                probed.albumName.isNotBlank() &&
                    albumNamesMatch(probed.albumName, fallbackAlbum) &&
                    (!isValidArtistQuery(fallbackArtist) ||
                        (probed.artistName.isNotBlank() && artistMatches(probed.artistName, fallbackArtist)))
            }
            ?: return null
        val result = toCanvasResult(
            albumVideo.video.rawVideoUrl,
            albumVideo.video.previewUrl,
            albumId = albumVideo.albumId
        )
        crossPopulateAlbumCanvasCache(albumVideo.albumName, albumVideo.artistName, result)
        crossPopulateAlbumCanvasCache(fallbackAlbum, fallbackArtist, result)
        return result
    }

    private suspend fun fetchAlbumEditorialVideo(storefront: String, albumId: String): AlbumEditorialVideo? {
        val resp = executeAmpGet("v1/catalog/$storefront/albums/$albumId?extend=editorialVideo")
        if (!resp.isSuccess) return null
        return try {
            val root = JSONObject(resp.body!!)
            val data = root.optJSONArray("data") ?: return null
            val item = data.optJSONObject(0) ?: return null
            val resolvedId = item.optString("id").takeIf { it.isNotBlank() } ?: albumId
            val attr = item.optJSONObject("attributes") ?: return null
            val video = extractEditorialVideoInfo(attr, PREFERRED_CANVAS_VIDEO_VARIANTS) ?: return null
            AlbumEditorialVideo(
                video = video,
                albumId = resolvedId,
                albumName = attr.optString("name", ""),
                artistName = attr.optString("artistName", "")
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun extractAlbumIdFromSong(songItem: JSONObject, attributes: JSONObject): String? {
        val relAlbums = songItem.optJSONObject("relationships")?.optJSONObject("albums")
        val albData = relAlbums?.optJSONArray("data")
        if (albData != null && albData.length() > 0) {
            val id = albData.optJSONObject(0)?.optString("id")
            if (!id.isNullOrBlank()) return id
        }

        val url = attributes.optString("url", "")
        if (url.contains("/album/")) {
            val afterAlbum = url.substringAfter("/album/").substringBefore("?")
            val possibleId = afterAlbum.substringAfterLast("/")
            if (possibleId.isNotEmpty() && possibleId.all { it.isDigit() }) return possibleId
        }

        return attributes.optString("collectionId", "").takeIf { it.isNotBlank() }
    }

    private fun toCanvasResult(
        rawVideoUrl: String,
        previewUrl: String?,
        albumId: String? = null
    ): CanvasArtworkResult {
        val cachedLocal = findCachedMp4IfPresent(rawVideoUrl)
        val playableUri = cachedLocal ?: rawVideoUrl
        return CanvasArtworkResult(
            videoUri = playableUri,
            providerName = "Apple Music Canvas",
            previewFrameUri = previewUrl,
            albumId = albumId,
            isHls = playableUri.contains(".m3u8", ignoreCase = true)
        )
    }

    private fun findCachedMp4IfPresent(videoUrl: String): String? {
        val dir = diskCacheDir ?: return null
        if (!videoUrl.contains(".mp4", ignoreCase = true)) return null
        val finalFile = File(dir, "canvas_${sha256Hex(videoUrl)}.mp4")
        return finalFile.takeIf { it.exists() && it.length() > 16_384 }?.absolutePath
    }

    private fun extractEditorialVideoInfo(
        attributes: JSONObject,
        preferredKeys: List<String>
    ): EditorialVideoInfo? {
        val editorialVideo = attributes.optJSONObject("editorialVideo") ?: return null
        val orderedKeys = LinkedHashSet<String>(preferredKeys.size + 4)
        orderedKeys.addAll(preferredKeys)
        val keysIter = editorialVideo.keys()
        while (keysIter.hasNext()) {
            val k = keysIter.next()
            if (!k.contains("Static", ignoreCase = true)) {
                orderedKeys.add(k)
            }
        }

        for (key in orderedKeys) {
            val variant = editorialVideo.optJSONObject(key) ?: continue
            val videoUrl = variant.optString("video", "")
                .ifBlank { variant.optString("videoUrl", "") }
                .ifBlank { variant.optString("hlsUrl", "") }
                .ifBlank { variant.optString("url", "") }
                .trim()

            if (videoUrl.isNotBlank()) {
                val previewObj = variant.optJSONObject("previewFrame")
                val previewUrl = previewObj?.optString("url", "")
                    ?.takeIf { it.isNotBlank() }
                    ?.replace("{w}", "600")
                    ?.replace("{h}", "600")
                    ?.replace("{c}", "bb")
                    ?.replace("{f}", "jpg")
                val bgHex = previewObj?.optString("bgColor", "")?.takeIf { it.isNotBlank() }
                return EditorialVideoInfo(videoUrl, previewUrl, bgHex)
            }
        }
        return null
    }

    private suspend fun fetchArtistAssetsUncached(cleanArtist: String): ArtistAssetsOutcome {
        val normalizedArtist = normalizeKey(cleanArtist)
        val cachedIdEntry = artistIdCache[normalizedArtist]?.takeIf { clockMs() < it.expiresAtMs }

        var anyTransientError = false

        val resolvedStorefronts = if (cachedIdEntry != null) {
            listOf(cachedIdEntry.storefront to cachedIdEntry.id)
        } else {
            emptyList()
        }

        // Fast path when artist ID is already known
        for ((sf, id) in resolvedStorefronts) {
            val profileOutcome = fetchAmpArtistProfile(sf, id, cleanArtist)
            if (profileOutcome.result != null || profileOutcome.authoritative) {
                return profileOutcome
            }
            anyTransientError = true
        }

        // Search artist ID then fetch full profile JSON (with wordmark + video + banner + colors)
        val storefronts = if (defaultStorefront.equals("gb", ignoreCase = true)) {
            listOf("gb", "us")
        } else {
            listOf(defaultStorefront, "gb")
        }

        for (sf in storefronts) {
            val idSearch = searchAppleArtistId(sf, cleanArtist)
            if (idSearch.unauthorized || idSearch.transientError) {
                anyTransientError = true
                continue
            }
            val foundId = idSearch.artistId ?: continue
            storeArtistIdEntry(normalizedArtist, foundId, idSearch.matchedName ?: cleanArtist, sf)
            val profileOutcome = fetchAmpArtistProfile(sf, foundId, cleanArtist)
            if (profileOutcome.result != null || profileOutcome.authoritative) {
                return profileOutcome
            }
            anyTransientError = true
        }

        // Resilience fallback: if AMP calls all failed transiently (e.g., browse scrape failed),
        // fall back once to iTunes lookup + public artist page regexes
        if (anyTransientError) {
            val htmlFallback = fetchArtistAssetsFromPublicHtmlFallback(cleanArtist)
            if (htmlFallback != null) {
                return ArtistAssetsOutcome(htmlFallback, authoritative = true)
            }
        }

        return ArtistAssetsOutcome(result = null, authoritative = !anyTransientError)
    }

    private data class ArtistIdSearchOutcome(
        val artistId: String?,
        val matchedName: String?,
        val unauthorized: Boolean,
        val transientError: Boolean
    )

    private suspend fun searchAppleArtistId(storefront: String, cleanArtist: String): ArtistIdSearchOutcome {
        val encoded = URLEncoder.encode(cleanArtist, StandardCharsets.UTF_8.name())
        val resp = executeAmpGet("v1/catalog/$storefront/search?term=$encoded&types=artists&limit=5")
        if (resp.unauthorized) return ArtistIdSearchOutcome(null, null, unauthorized = true, transientError = true)
        if (!resp.isSuccess) return ArtistIdSearchOutcome(null, null, unauthorized = false, transientError = true)

        return try {
            val root = JSONObject(resp.body!!)
            val data = root.optJSONObject("results")
                ?.optJSONObject("artists")
                ?.optJSONArray("data")
                ?: return ArtistIdSearchOutcome(null, null, unauthorized = false, transientError = false)

            val normTarget = normalizeKey(cleanArtist)
            var bestId: String? = null
            var bestName: String? = null
            var bestScore = Int.MIN_VALUE

            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val id = item.optString("id", "").takeIf { it.isNotBlank() } ?: continue
                val attr = item.optJSONObject("attributes") ?: continue
                val name = attr.optString("name", "")
                if (!artistMatches(name, cleanArtist)) continue

                val nName = normalizeKey(name)
                var score = 0
                if (nName == normTarget) score += 100
                else if (nName.startsWith(normTarget)) score += 40
                if (attr.has("editorialArtwork") || attr.has("editorialVideo") || attr.has("artwork")) score += 10

                if (score > bestScore) {
                    bestScore = score
                    bestId = id
                    bestName = name
                }
            }

            ArtistIdSearchOutcome(bestId, bestName, unauthorized = false, transientError = false)
        } catch (_: Exception) {
            ArtistIdSearchOutcome(null, null, unauthorized = false, transientError = true)
        }
    }

    private suspend fun fetchAmpArtistProfile(
        storefront: String,
        artistId: String,
        fallbackName: String
    ): ArtistAssetsOutcome {
        val resp = executeAmpGet(
            "v1/catalog/$storefront/artists/$artistId?extend=artwork,editorialArtwork,editorialVideo"
        )
        if (resp.unauthorized || !resp.isSuccess) {
            return ArtistAssetsOutcome(result = null, authoritative = false)
        }

        return try {
            val root = JSONObject(resp.body!!)
            val item = root.optJSONArray("data")?.optJSONObject(0)
                ?: return ArtistAssetsOutcome(result = null, authoritative = true)
            val attr = item.optJSONObject("attributes")
                ?: return ArtistAssetsOutcome(result = null, authoritative = true)

            val resolvedName = attr.optString("name", fallbackName).ifBlank { fallbackName }
            val editorialArtwork = attr.optJSONObject("editorialArtwork")
            val primaryArtwork = attr.optJSONObject("artwork")

            val wordmarkObj = findWordmarkArtworkObject(editorialArtwork)
            val wordmarkUri = wordmarkObj
                ?.optString("url", "")
                ?.takeIf { it.isNotBlank() }
                ?.let { normalizeAppleWordmarkUrl(it) }
                ?.takeIf { it.isNotBlank() }

            val editorialHeroObj = findHeroArtworkObject(editorialArtwork)
            val heroObj = editorialHeroObj ?: primaryArtwork
            val bannerUri = heroObj?.let { obj ->
                val rawUrl = obj.optString("url", "")
                if (rawUrl.isBlank()) {
                    null
                } else {
                    val rawW = obj.optInt("width", 0)
                    val rawH = obj.optInt("height", 0)
                    val (targetW, targetH) = if (rawW > 0 && rawH > 0) {
                        val w = minOf(1600, rawW)
                        val h = (w.toLong() * rawH.toLong() / rawW.toLong()).toInt().coerceAtLeast(1)
                        w to h
                    } else if (editorialHeroObj != null) {
                        1600 to 900
                    } else {
                        1200 to 1200
                    }
                    formatAppleArtworkUrl(rawUrl, width = targetW, height = targetH).takeIf { it.isNotBlank() }
                }
            }

            val videoInfo = extractEditorialVideoInfo(attr, PREFERRED_ARTIST_VIDEO_VARIANTS)
            val bgColorHex = pickHexColor(heroObj, wordmarkObj, videoInfo?.bgColorHex)
            val keyColorHex = pickKeyColor(heroObj, wordmarkObj)

            val result = if (
                wordmarkUri != null ||
                videoInfo?.rawVideoUrl != null ||
                bannerUri != null ||
                bgColorHex != null
            ) {
                ArtistAssetsResult(
                    appleArtistId = artistId,
                    artistName = resolvedName,
                    wordmarkUri = wordmarkUri,
                    headerVideoUri = videoInfo?.rawVideoUrl,
                    bannerUri = bannerUri,
                    bgColorHex = bgColorHex,
                    keyColorHex = keyColorHex
                )
            } else {
                null
            }

            ArtistAssetsOutcome(result = result, authoritative = true)
        } catch (e: Exception) {
            safeLog("Failed parsing AMP artist profile $artistId: ${e.message}")
            ArtistAssetsOutcome(result = null, authoritative = false)
        }
    }

    private fun findWordmarkArtworkObject(editorialArtwork: JSONObject?): JSONObject? {
        if (editorialArtwork == null) return null
        for (key in WORDMARK_ARTWORK_KEYS) {
            val obj = editorialArtwork.optJSONObject(key)
            if (obj != null && obj.optString("url", "").isNotBlank()) {
                return obj
            }
        }
        val keys = editorialArtwork.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (k.contains("logo", ignoreCase = true) || k.contains("wordmark", ignoreCase = true)) {
                val obj = editorialArtwork.optJSONObject(k)
                if (obj != null && obj.optString("url", "").isNotBlank()) {
                    return obj
                }
            }
        }
        return null
    }

    private fun findHeroArtworkObject(editorialArtwork: JSONObject?): JSONObject? {
        if (editorialArtwork == null) return null
        for (key in HERO_ARTWORK_KEYS) {
            val obj = editorialArtwork.optJSONObject(key)
            if (obj != null && obj.optString("url", "").isNotBlank()) {
                return obj
            }
        }
        return null
    }

    private fun pickHexColor(
        heroObj: JSONObject?,
        wordmarkObj: JSONObject?,
        videoBgHex: String?
    ): String? {
        return heroObj?.optString("bgColor", "")?.takeIf { it.isNotBlank() }
            ?: wordmarkObj?.optString("bgColor", "")?.takeIf { it.isNotBlank() }
            ?: videoBgHex?.takeIf { it.isNotBlank() }
    }

    private fun pickKeyColor(heroObj: JSONObject?, wordmarkObj: JSONObject?): String? {
        return heroObj?.optString("textColor1", "")?.takeIf { it.isNotBlank() }
            ?: wordmarkObj?.optString("textColor1", "")?.takeIf { it.isNotBlank() }
    }

    private fun fetchArtistAssetsFromPublicHtmlFallback(cleanArtist: String): ArtistAssetsResult? {
        if (!ampBaseUrl.startsWith("https://amp-api.music.apple.com", ignoreCase = true)) {
            return null
        }
        return try {
            val encoded = URLEncoder.encode(cleanArtist, StandardCharsets.UTF_8.name())
            val searchUrl = "https://itunes.apple.com/search?term=$encoded&entity=musicArtist&limit=5"
            val req = Request.Builder().url(searchUrl).build()
            val bestUrl = httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val results = JSONObject(body).optJSONArray("results") ?: return null
                var fallbackUrl: String? = null
                for (i in 0 until results.length()) {
                    val item = results.optJSONObject(i) ?: continue
                    val candName = item.optString("artistName", "")
                    val candUrl = item.optString("artistLinkUrl", "")
                    if (candUrl.isBlank()) continue
                    if (normalizeKey(candName) == normalizeKey(cleanArtist)) {
                        return@use candUrl
                    }
                    if (fallbackUrl == null && artistMatches(candName, cleanArtist)) {
                        fallbackUrl = candUrl
                    }
                }
                fallbackUrl
            } ?: return null

            val pageReq = Request.Builder()
                .url(bestUrl.substringBefore("?"))
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            val html = httpClient.newCall(pageReq).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string()
            } ?: return null

            var wordmarkUrl: String? = null
            val logoMatcher = ARTIST_LOGO_REGEX.matcher(html)
            if (logoMatcher.find()) {
                wordmarkUrl = normalizeAppleWordmarkUrl(logoMatcher.group(1) ?: "").takeIf { it.isNotBlank() }
            } else {
                val picMatcher = ARTIST_PICTURE_WORDMARK_REGEX.matcher(html)
                if (picMatcher.find()) {
                    wordmarkUrl = normalizeAppleWordmarkUrl(picMatcher.group(1) ?: "").takeIf { it.isNotBlank() }
                }
            }

            var videoUrl: String? = null
            val videoMatcher = ARTIST_WIDE_VIDEO_REGEX.matcher(html)
            if (videoMatcher.find()) {
                videoUrl = (videoMatcher.group(1) ?: "").replace("\\/", "/").trim().takeIf { it.isNotBlank() }
            }

            if (wordmarkUrl == null && videoUrl == null) return null
            ArtistAssetsResult(
                artistName = cleanArtist,
                wordmarkUri = wordmarkUrl,
                headerVideoUri = videoUrl
            )
        } catch (e: Exception) {
            safeLog("HTML fallback failed for '$cleanArtist': ${e.message}")
            null
        }
    }

    private suspend fun executeAmpGet(relativePathAndQuery: String): AmpHttpResponse {
        val base = ampBaseUrl.trimEnd('/')
        val path = relativePathAndQuery.trimStart('/')
        val fullUrl = "$base/$path"

        var token = ensureAppleMusicToken(forceRefresh = false)
        if (token.isBlank()) {
            token = ensureAppleMusicToken(forceRefresh = true)
        }

        val first = runSingleAmpGet(fullUrl, token)
        if (!first.unauthorized) {
            return first
        }

        val refreshed = ensureAppleMusicToken(forceRefresh = true, rejectedToken = token)
        if (refreshed.isBlank() || refreshed == token) {
            return first
        }
        return runSingleAmpGet(fullUrl, refreshed)
    }

    private fun runSingleAmpGet(url: String, token: String): AmpHttpResponse {
        val builder = Request.Builder()
            .url(url)
            .header("Origin", "https://music.apple.com")
            .header("Referer", "https://music.apple.com/")
        if (token.isNotBlank()) {
            builder.header("Authorization", "Bearer $token")
        }

        return try {
            httpClient.newCall(builder.build()).execute().use { response ->
                val code = response.code
                if (code == 401 || code == 403) {
                    AmpHttpResponse(code, null, unauthorized = true, error = true)
                } else if (!response.isSuccessful) {
                    AmpHttpResponse(code, null, unauthorized = false, error = true)
                } else {
                    AmpHttpResponse(code, response.body?.string(), unauthorized = false, error = false)
                }
            }
        } catch (e: Exception) {
            safeLog("AMP GET failed for ${url.take(80)}: ${e.message}")
            AmpHttpResponse(-1, null, unauthorized = false, error = true)
        }
    }

    private suspend fun ensureAppleMusicToken(
        forceRefresh: Boolean,
        rejectedToken: String? = null
    ): String = tokenMutex.withLock {
        val nowMs = clockMs()
        val nowSec = nowMs / 1000L

        // If another waiter already replaced rejectedToken with a valid token, return immediately
        if (cachedBearerToken.isNotBlank() &&
            cachedBearerToken != rejectedToken &&
            tokenExpiresAtEpochSec > nowSec + 60L &&
            (!forceRefresh || rejectedToken != null)
        ) {
            return@withLock cachedBearerToken
        }

        if (rejectedToken != null && cachedBearerToken == rejectedToken) {
            cachedBearerToken = ""
            tokenExpiresAtEpochSec = 0L
        }

        if (lastScrapeFailedAtMs > 0L && nowMs - lastScrapeFailedAtMs < SCRAPE_FAILURE_BACKOFF_MS) {
            return@withLock cachedBearerToken
        }

        val scraped = scrapeTokenFromBrowsePage(nowSec, rejectedToken)
        if (scraped != null) {
            cachedBearerToken = scraped.first
            tokenExpiresAtEpochSec = scraped.second
            lastScrapeFailedAtMs = 0L
            persistTokenToPrefs(scraped.first, scraped.second)
            safeLog("Refreshed Apple Music token successfully, exp=${scraped.second}")
            return@withLock cachedBearerToken
        }

        lastScrapeFailedAtMs = nowMs
        cachedBearerToken
    }

    private fun persistTokenToPrefs(token: String, expSec: Long) {
        val ctx = context ?: return
        runCatching {
            ctx.getSharedPreferences(TOKEN_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_TOKEN_KEY, token)
                .putLong(PREF_TOKEN_EXP_KEY, expSec)
                .apply()
        }
    }

    private fun scrapeTokenFromBrowsePage(
        nowSec: Long,
        rejectedToken: String?
    ): Pair<String, Long>? {
        return try {
            val htmlReq = Request.Builder()
                .url(browseUrl)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            val html = httpClient.newCall(htmlReq).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string()
            } ?: return null

            // 1. Meta config environment tag (no extra JS bundle HTTP fetch required)
            extractTokenFromEnvironmentMeta(html, nowSec, rejectedToken)?.let { return it }

            // 2. Fallback: JS bundles, checking modern non-legacy bundles first
            val candidateBundleUrls = mutableListOf<String>()
            val baseUri = runCatching { URI(browseUrl) }.getOrNull()
            val scriptMatcher = SCRIPT_SRC_REGEX.matcher(html)
            while (scriptMatcher.find()) {
                val src = scriptMatcher.group(1)?.trim() ?: continue
                val resolved = when {
                    src.startsWith("http", ignoreCase = true) -> src
                    baseUri != null -> runCatching { baseUri.resolve(src).toString() }.getOrNull()
                    else -> null
                } ?: continue
                if (resolved !in candidateBundleUrls) {
                    candidateBundleUrls.add(resolved)
                }
            }

            val orderedBundles = candidateBundleUrls
                .sortedBy { if (it.contains("legacy", ignoreCase = true)) 1 else 0 }
                .take(4)

            for (bundleUrl in orderedBundles) {
                val jsContent = runCatching {
                    httpClient.newCall(Request.Builder().url(bundleUrl).build()).execute().use { resp ->
                        if (resp.isSuccessful) resp.body?.string() else null
                    }
                }.getOrNull() ?: continue

                findValidJwtInText(jsContent, nowSec, rejectedToken)?.let { return it }
            }

            // Last check in case JWT is inline in HTML
            findValidJwtInText(html, nowSec, rejectedToken)
        } catch (e: Exception) {
            safeLog("Failed to scrape Apple Music token: ${e.message}")
            null
        }
    }

    private fun extractTokenFromEnvironmentMeta(
        html: String,
        nowSec: Long,
        rejectedToken: String?
    ): Pair<String, Long>? {
        val matcher = META_ENV_REGEX.matcher(html)
        while (matcher.find()) {
            val encoded = matcher.group(1) ?: matcher.group(2) ?: continue
            val decoded = runCatching {
                URLDecoder.decode(encoded, StandardCharsets.UTF_8.name())
            }.getOrDefault(encoded)

            runCatching {
                val json = JSONObject(decoded)
                val token = json.optJSONObject("MEDIA_API")?.optString("token", "")
                    ?.takeIf { it.isNotBlank() }
                    ?: json.optString("token", "").takeIf { it.isNotBlank() }
                if (token != null && token != rejectedToken && isAppleWebPlayJwt(token)) {
                    val exp = jwtExpEpochSeconds(token)
                    if (exp != null && exp > nowSec + 60L) {
                        return token to exp
                    }
                }
            }

            findValidJwtInText(decoded, nowSec, rejectedToken)?.let { return it }
        }
        return null
    }

    private fun findValidJwtInText(
        text: String,
        nowSec: Long,
        rejectedToken: String?
    ): Pair<String, Long>? {
        val jwtMatcher = JWT_REGEX.matcher(text)
        while (jwtMatcher.find()) {
            val candidate = jwtMatcher.group()
            if (candidate == rejectedToken) continue
            if (!isAppleWebPlayJwt(candidate)) continue
            val exp = jwtExpEpochSeconds(candidate) ?: continue
            if (exp > nowSec + 60L) {
                return candidate to exp
            }
        }
        return null
    }

    private fun isAppleWebPlayJwt(jwt: String): Boolean {
        val parts = jwt.split(".")
        if (parts.size != 3) return false
        val header = runCatching { base64UrlDecode(parts[0]) }.getOrNull().orEmpty()
        val payload = runCatching { base64UrlDecode(parts[1]) }.getOrNull().orEmpty()
        if (payload.isBlank()) return false

        val payloadJson = runCatching { JSONObject(payload) }.getOrNull() ?: return false
        val iss = payloadJson.optString("iss", "")
        if (iss.equals("AMPWebPlay", ignoreCase = true) || iss.contains("WebPlay", ignoreCase = true)) {
            return true
        }
        return header.contains("WebPlayKid", ignoreCase = true)
    }

    private fun base64UrlDecode(segment: String): String {
        val padded = segment + "=".repeat((4 - segment.length % 4) % 4)
        return String(Base64.getUrlDecoder().decode(padded), StandardCharsets.UTF_8)
    }

    private fun jwtExpEpochSeconds(token: String): Long? = runCatching {
        val payload = token.split(".").getOrNull(1) ?: return null
        JSONObject(base64UrlDecode(payload)).optLong("exp", 0L).takeIf { it > 0L }
    }.getOrNull()

    private fun resolveHlsMasterPlaylistToAvcMp4(
        masterM3u8Url: String,
        preferredTargetWidth: Int = 768,
        preferredBandwidth: Int = 2_200_000
    ): String? {
        return try {
            val request = Request.Builder().url(masterM3u8Url).build()
            val masterContent = httpClient.newCall(request).execute().use { it.body?.string() } ?: return null
            val lines = masterContent.lines().map { it.trim() }.filter { it.isNotEmpty() }

            data class Variant(val width: Int, val bandwidth: Int, val subPlaylistUrl: String)
            val avcVariants = mutableListOf<Variant>()
            val masterUri = URI(masterM3u8Url)

            for (i in 0 until lines.size - 1) {
                val line = lines[i]
                if (!line.startsWith("#EXT-X-STREAM-INF:", ignoreCase = true)) continue
                if (!line.contains("avc1", ignoreCase = true)) continue

                val nextLine = lines[i + 1]
                if (nextLine.startsWith("#")) continue

                var width = 486
                val resMatcher = RESOLUTION_REGEX.matcher(line)
                if (resMatcher.find()) {
                    width = resMatcher.group(1)?.toIntOrNull() ?: 486
                }

                var bandwidth = 1_000_000
                val bwMatcher = BANDWIDTH_REGEX.matcher(line)
                if (bwMatcher.find()) {
                    bandwidth = bwMatcher.group(1)?.toIntOrNull() ?: 1_000_000
                }

                avcVariants.add(Variant(width, bandwidth, masterUri.resolve(nextLine).toString()))
            }

            if (avcVariants.isEmpty()) return null

            val chosen = avcVariants.minByOrNull {
                abs(it.width - preferredTargetWidth) * 10_000 + abs(it.bandwidth - preferredBandwidth) / 1000
            } ?: avcVariants.first()

            val subM3u8Url = chosen.subPlaylistUrl
            val subContent = httpClient.newCall(Request.Builder().url(subM3u8Url).build())
                .execute().use { it.body?.string() } ?: return null
            val subBaseUri = URI(subM3u8Url)

            val mapMatcher = EXT_X_MAP_URI_REGEX.matcher(subContent)
            if (mapMatcher.find()) {
                val relativeMp4 = mapMatcher.group(1)
                if (!relativeMp4.isNullOrBlank()) {
                    return subBaseUri.resolve(relativeMp4).toString()
                }
            }

            if (subM3u8Url.endsWith(".m3u8", ignoreCase = true)) {
                return subM3u8Url.substring(0, subM3u8Url.length - 5) + "-.mp4"
            }

            null
        } catch (e: Exception) {
            safeLog("HLS master resolution fallback: ${e.message}")
            null
        }
    }

    private fun downloadToCacheIfPossible(videoUrl: String): String? {
        val dir = diskCacheDir ?: return null
        if (!videoUrl.contains(".mp4", ignoreCase = true)) return null

        return try {
            val hashHex = sha256Hex(videoUrl)
            val finalFile = File(dir, "canvas_$hashHex.mp4")

            if (finalFile.exists() && finalFile.length() > 16_384) {
                return finalFile.absolutePath
            }

            val tempFile = File(dir, "canvas_$hashHex.tmp")
            val request = Request.Builder().url(videoUrl).build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                FileOutputStream(tempFile).use { fos ->
                    body.byteStream().copyTo(fos)
                }
            }

            neutralizeMp4EditListsInPlace(tempFile)

            if (tempFile.renameTo(finalFile)) {
                finalFile.absolutePath
            } else if (finalFile.exists()) {
                tempFile.delete()
                finalFile.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            safeLog("Download to cache failed: ${e.message}")
            null
        }
    }

    private fun neutralizeMp4EditListsInPlace(file: File) {
        try {
            val bytes = file.readBytes()
            var modified = false
            val limit = minOf(bytes.size - 8, 524_288)

            for (i in 4 until limit) {
                if (bytes[i] == 'e'.code.toByte() &&
                    bytes[i + 1] == 'd'.code.toByte() &&
                    bytes[i + 2] == 't'.code.toByte() &&
                    bytes[i + 3] == 's'.code.toByte()
                ) {
                    val boxSize = ((bytes[i - 4].toLong() and 0xFF) shl 24) or
                        ((bytes[i - 3].toLong() and 0xFF) shl 16) or
                        ((bytes[i - 2].toLong() and 0xFF) shl 8) or
                        (bytes[i - 1].toLong() and 0xFF)

                    if (boxSize in 8..4096) {
                        bytes[i] = 'f'.code.toByte()
                        bytes[i + 1] = 'r'.code.toByte()
                        bytes[i + 2] = 'e'.code.toByte()
                        bytes[i + 3] = 'e'.code.toByte()
                        modified = true
                    }
                }
            }

            if (modified) {
                file.writeBytes(bytes)
            }
        } catch (_: Exception) {
        }
    }

    private fun storeAlbumCanvasEntry(key: String, value: CanvasArtworkResult?, isMiss: Boolean) {
        storeCanvasMapEntry(albumCanvasCache, MAX_ALBUM_CANVAS_CACHE_ENTRIES, key, value, isMiss)
    }

    private fun storeCanvasMapEntry(
        cache: ConcurrentHashMap<String, CacheEntry<CanvasArtworkResult>>,
        maxEntries: Int,
        key: String,
        value: CanvasArtworkResult?,
        isMiss: Boolean
    ) {
        val now = clockMs()
        val ttl = if (isMiss) MISS_TTL_MS else HIT_TTL_MS
        cache[key] = CacheEntry(
            value = value,
            updatedAtMs = now,
            expiresAtMs = now + ttl,
            staleUntilMs = if (value != null) now + STALE_GRACE_MS else now + ttl
        )
        trimCacheIfNeeded(cache, maxEntries)
        scheduleDiskPersist()
    }

    private fun crossPopulateAlbumCanvasCache(
        albumName: String,
        artistName: String,
        result: CanvasArtworkResult
    ) {
        val key = buildAlbumCacheKey(albumName, artistName) ?: return
        val existing = albumCanvasCache[key]
        val now = clockMs()
        if (existing != null && existing.isFresh(now) && existing.value == result) {
            return
        }
        storeAlbumCanvasEntry(key, result, isMiss = false)
    }

    private fun storeArtistAssetsEntry(key: String, value: ArtistAssetsResult?, isMiss: Boolean) {
        val now = clockMs()
        val ttl = if (isMiss) MISS_TTL_MS else HIT_TTL_MS
        artistAssetsCache[key] = CacheEntry(
            value = value,
            updatedAtMs = now,
            expiresAtMs = now + ttl,
            staleUntilMs = if (value != null) now + STALE_GRACE_MS else now + ttl
        )
        trimCacheIfNeeded(artistAssetsCache, MAX_ARTIST_CACHE_ENTRIES)
        scheduleDiskPersist()
    }

    private fun storeArtistIdEntry(key: String, id: String, name: String, storefront: String) {
        val now = clockMs()
        artistIdCache[key] = CachedArtistId(
            id = id,
            name = name,
            storefront = storefront,
            updatedAtMs = now,
            expiresAtMs = now + ARTIST_ID_TTL_MS
        )
        if (artistIdCache.size > MAX_ARTIST_ID_CACHE_ENTRIES) {
            val overflow = artistIdCache.size - MAX_ARTIST_ID_CACHE_ENTRIES
            artistIdCache.entries
                .sortedBy { it.value.updatedAtMs }
                .take(overflow)
                .forEach { artistIdCache.remove(it.key) }
        }
        scheduleDiskPersist()
    }

    private fun <T> trimCacheIfNeeded(map: ConcurrentHashMap<String, CacheEntry<T>>, maxSize: Int) {
        if (map.size <= maxSize) return
        val overflow = map.size - maxSize
        map.entries
            .sortedBy { it.value.updatedAtMs }
            .take(overflow)
            .forEach { map.remove(it.key) }
    }

    private fun scheduleDiskPersist() {
        if (metadataCacheDir == null) return
        persistDirty.set(true)
        if (!persistScheduled.compareAndSet(false, true)) return
        serviceScope.launch {
            try {
                kotlinx.coroutines.delay(350L)
                persistMutex.withLock {
                    val dir = metadataCacheDir ?: return@withLock
                    while (persistDirty.getAndSet(false)) {
                        saveCanvasEntriesFile(File(dir, "album_canvas_entries.json"), albumCanvasCache)
                        saveArtistAssetsDiskCache(File(dir, "artist_assets.json"))
                        saveArtistIdsDiskCache(File(dir, "artist_ids.json"))
                    }
                }
            } finally {
                persistScheduled.set(false)
                if (persistDirty.get()) {
                    scheduleDiskPersist()
                }
            }
        }
    }

    private fun loadCanvasEntriesFile(
        file: File,
        target: ConcurrentHashMap<String, CacheEntry<CanvasArtworkResult>>
    ) {
        if (!file.exists()) return
        runCatching {
            val arr = JSONArray(file.readText())
            val now = clockMs()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val key = obj.optString("k", "").takeIf { it.isNotBlank() } ?: continue
                val exp = obj.optLong("exp", 0L)
                val stale = obj.optLong("stale", exp)
                val updated = obj.optLong("upd", 0L)
                if (now >= stale && now >= exp) continue
                val videoUri = obj.optString("v", "")
                val result = if (videoUri.isNotBlank()) {
                    CanvasArtworkResult(
                        videoUri = videoUri,
                        providerName = obj.optString("p", "Apple Music Canvas"),
                        previewFrameUri = obj.optString("pre", "").takeIf { it.isNotBlank() },
                        albumId = obj.optString("aid", "").takeIf { it.isNotBlank() },
                        isHls = obj.optBoolean("hls", videoUri.contains(".m3u8", ignoreCase = true))
                    )
                } else {
                    null
                }
                target.putIfAbsent(key, CacheEntry(result, updated, exp, stale))
            }
        }
    }

    private fun saveCanvasEntriesFile(
        file: File,
        source: ConcurrentHashMap<String, CacheEntry<CanvasArtworkResult>>
    ) {
        runCatching {
            val arr = JSONArray()
            val now = clockMs()
            for ((k, e) in source.entries) {
                if (now >= e.staleUntilMs && now >= e.expiresAtMs) continue
                val obj = JSONObject().apply {
                    put("k", k)
                    put("upd", e.updatedAtMs)
                    put("exp", e.expiresAtMs)
                    put("stale", e.staleUntilMs)
                    e.value?.let { v ->
                        put("v", v.videoUri)
                        put("p", v.providerName)
                        put("pre", v.previewFrameUri ?: "")
                        put("aid", v.albumId ?: "")
                        put("hls", v.isHls)
                    }
                }
                arr.put(obj)
            }
            atomicWriteText(file, arr.toString())
        }
    }

    private fun loadArtistAssetsDiskCache(file: File) {
        if (!file.exists()) return
        runCatching {
            val arr = JSONArray(file.readText())
            val now = clockMs()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val key = obj.optString("k", "").takeIf { it.isNotBlank() } ?: continue
                val exp = obj.optLong("exp", 0L)
                val stale = obj.optLong("stale", exp)
                val updated = obj.optLong("upd", 0L)
                if (now >= stale && now >= exp) continue
                val hasValue = obj.optBoolean("hit", false)
                val value = if (hasValue) {
                    ArtistAssetsResult(
                        appleArtistId = obj.optString("id", ""),
                        artistName = obj.optString("name", ""),
                        wordmarkUri = obj.optString("wm", "").takeIf { it.isNotBlank() },
                        headerVideoUri = obj.optString("vid", "").takeIf { it.isNotBlank() },
                        bannerUri = obj.optString("ban", "").takeIf { it.isNotBlank() },
                        bgColorHex = obj.optString("bg", "").takeIf { it.isNotBlank() },
                        keyColorHex = obj.optString("fg", "").takeIf { it.isNotBlank() }
                    )
                } else {
                    null
                }
                artistAssetsCache.putIfAbsent(key, CacheEntry(value, updated, exp, stale))
            }
        }
    }

    private fun saveArtistAssetsDiskCache(file: File) {
        runCatching {
            val arr = JSONArray()
            val now = clockMs()
            for ((k, e) in artistAssetsCache.entries) {
                if (now >= e.staleUntilMs && now >= e.expiresAtMs) continue
                val obj = JSONObject().apply {
                    put("k", k)
                    put("upd", e.updatedAtMs)
                    put("exp", e.expiresAtMs)
                    put("stale", e.staleUntilMs)
                    put("hit", e.value != null)
                    e.value?.let { v ->
                        put("id", v.appleArtistId)
                        put("name", v.artistName)
                        put("wm", v.wordmarkUri ?: "")
                        put("vid", v.headerVideoUri ?: "")
                        put("ban", v.bannerUri ?: "")
                        put("bg", v.bgColorHex ?: "")
                        put("fg", v.keyColorHex ?: "")
                    }
                }
                arr.put(obj)
            }
            atomicWriteText(file, arr.toString())
        }
    }

    private fun loadArtistIdsDiskCache(file: File) {
        if (!file.exists()) return
        runCatching {
            val arr = JSONArray(file.readText())
            val now = clockMs()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val key = obj.optString("k", "").takeIf { it.isNotBlank() } ?: continue
                val exp = obj.optLong("exp", 0L)
                if (now >= exp) continue
                val id = obj.optString("id", "").takeIf { it.isNotBlank() } ?: continue
                artistIdCache.putIfAbsent(
                    key,
                    CachedArtistId(
                        id = id,
                        name = obj.optString("name", ""),
                        storefront = obj.optString("sf", defaultStorefront),
                        updatedAtMs = obj.optLong("upd", now),
                        expiresAtMs = exp
                    )
                )
            }
        }
    }

    private fun saveArtistIdsDiskCache(file: File) {
        runCatching {
            val arr = JSONArray()
            val now = clockMs()
            for ((k, e) in artistIdCache.entries) {
                if (now >= e.expiresAtMs) continue
                val obj = JSONObject().apply {
                    put("k", k)
                    put("id", e.id)
                    put("name", e.name)
                    put("sf", e.storefront)
                    put("upd", e.updatedAtMs)
                    put("exp", e.expiresAtMs)
                }
                arr.put(obj)
            }
            atomicWriteText(file, arr.toString())
        }
    }

    private fun atomicWriteText(target: File, text: String) {
        val parent = target.parentFile ?: return
        if (!parent.exists()) parent.mkdirs()
        val tmp = File(parent, "${target.name}.tmp")
        tmp.writeText(text, StandardCharsets.UTF_8)
        if (!tmp.renameTo(target)) {
            target.writeText(text, StandardCharsets.UTF_8)
            tmp.delete()
        }
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(StandardCharsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }.take(24)
    }

    private fun cleanArtistName(artist: String): String {
        return artist
            .replace(Regex("\\s*-\\s*Topic$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*VEVO$", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private fun cleanPrimaryArtist(artist: String): String {
        return cleanArtistName(artist)
            .split(FEATURE_DELIM_REGEX)
            .firstOrNull()
            ?.trim()
            .orEmpty()
    }

    private fun isValidArtistQuery(cleanArtist: String): Boolean {
        return cleanArtist.isNotBlank() &&
            !cleanArtist.equals("Unknown Artist", ignoreCase = true) &&
            !cleanArtist.equals("Various Artists", ignoreCase = true)
    }

    internal fun cleanAlbumName(album: String?): String {
        var current = (album ?: "").trim()
        if (current.isEmpty()) return ""
        repeat(5) {
            val next = current
                .replace(SAFE_PAREN_EDITION_REGEX, "")
                .replace(SAFE_EP_PAREN_REGEX, "")
                .replace(SAFE_DASH_EDITION_REGEX, "")
                .trim()
            if (next == current) return@repeat
            current = next
        }
        return current
    }

    internal fun albumNamesMatch(candidateAlbum: String, targetAlbum: String): Boolean {
        val cleanCand = cleanAlbumName(candidateAlbum)
        val cleanTarget = cleanAlbumName(targetAlbum)
        if (cleanCand.isBlank() || cleanTarget.isBlank()) return false

        val candLower = candidateAlbum.lowercase(Locale.ROOT)
        val targetLower = targetAlbum.lowercase(Locale.ROOT)
        for (modRegex in STRICT_ALBUM_MODIFIER_REGEXES) {
            if (modRegex.containsMatchIn(candLower) != modRegex.containsMatchIn(targetLower)) {
                return false
            }
        }

        val normCand = normalizeKey(cleanCand)
        val normTarget = normalizeKey(cleanTarget)
        if (normCand.isBlank() || normTarget.isBlank()) return false
        return normCand == normTarget
    }

    internal fun normalizeKey(str: String): String {
        if (str.isBlank()) return ""
        val decomposed = Normalizer.normalize(str, Normalizer.Form.NFD)
            .replace(DIACRITICS_REGEX, "")
        return decomposed
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]"), "")
            .trim()
    }

    private fun normalizeTokens(str: String): List<String> {
        if (str.isBlank()) return emptyList()
        val decomposed = Normalizer.normalize(str, Normalizer.Form.NFD)
            .replace(DIACRITICS_REGEX, "")
            .lowercase(Locale.ROOT)
        return decomposed
            .split(TOKEN_SPLIT_REGEX)
            .filter { it.isNotEmpty() }
    }

    /**
     * Whole-word and collaborator-aware artist matching: prevents single stop-words like `"the"`
     * from falsely matching unrelated artists while still accepting collaborations and diacritic
     * differences (e.g., `"Drake & 21 Savage"` <-> `"Drake"`, `"Beyoncé"` <-> `"Beyonce"`).
     */
    internal fun artistMatches(candidateArtist: String, targetArtist: String): Boolean {
        val cleanCand = cleanArtistName(candidateArtist)
        val cleanTarget = cleanArtistName(targetArtist)
        if (cleanTarget.isBlank()) return true
        if (cleanCand.isBlank()) return false

        val normCand = normalizeKey(cleanCand)
        val normTarget = normalizeKey(cleanTarget)
        if (normCand == normTarget) return true

        val candSegments = cleanCand.split(FEATURE_DELIM_REGEX).map { normalizeKey(it) }.filter { it.isNotEmpty() }
        val targetSegments = cleanTarget.split(FEATURE_DELIM_REGEX).map { normalizeKey(it) }.filter { it.isNotEmpty() }
        if (candSegments.any { seg -> seg in targetSegments }) return true

        val candTokens = normalizeTokens(cleanCand).filter { it.length >= 2 && it !in ARTIST_STOP_WORDS }
        val targetTokens = normalizeTokens(cleanTarget).filter { it.length >= 2 && it !in ARTIST_STOP_WORDS }
        if (candTokens.isEmpty() || targetTokens.isEmpty()) return false

        val smaller = if (candTokens.size <= targetTokens.size) candTokens else targetTokens
        val larger = if (candTokens.size <= targetTokens.size) targetTokens.toSet() else candTokens.toSet()
        return smaller.all { it in larger }
    }

    private fun formatAppleArtworkUrl(rawUrl: String, width: Int, height: Int): String {
        val cleaned = rawUrl.replace("\\/", "/").trim()
        if (cleaned.isBlank()) return ""
        return cleaned
            .replace("{w}", width.toString(), ignoreCase = true)
            .replace("{h}", height.toString(), ignoreCase = true)
            .replace("{c}", "cc", ignoreCase = true)
            .replace("{f}", "jpg", ignoreCase = true)
    }

    private fun normalizeAppleWordmarkUrl(rawUrl: String): String {
        if (rawUrl.isBlank()) return ""
        val cleaned = rawUrl.replace("\\/", "/").trim()
        // Coil does not include coil-svg in this build, so always request raster PNG wordmarks
        if (cleaned.endsWith(".svg", ignoreCase = true)) return ""
        val lastSlash = cleaned.lastIndexOf('/')
        if (lastSlash > 0 && cleaned.contains(".png/", ignoreCase = true)) {
            return "${cleaned.substring(0, lastSlash + 1)}800x266bb.png"
        }
        return cleaned
            .replace("{w}x{h}{c}.{f}", "800x266bb.png", ignoreCase = true)
            .replace("{w}x{h}bb.{f}", "800x266bb.png", ignoreCase = true)
            .replace("{w}", "800", ignoreCase = true)
            .replace("{h}", "266", ignoreCase = true)
            .replace("{c}", "bb", ignoreCase = true)
            .replace("{f}", "png", ignoreCase = true)
            .replace("800x266bb.webp", "800x266bb.png", ignoreCase = true)
            .replace("400x133bb.webp", "800x266bb.png", ignoreCase = true)
    }
}
