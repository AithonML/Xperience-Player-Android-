package com.xperience.player.service

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.intercept.Interceptor as CoilInterceptor
import coil.memory.MemoryCache
import coil.request.ImageRequest
import coil.request.ImageResult
import com.xperience.player.domain.model.DataUsageCategory
import com.xperience.player.domain.model.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import java.io.File
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Process-wide network, image, and Media3 video cache singletons.
 *
 * Sharing a single [OkHttpClient] connection pool/dispatcher across catalog, canvas,
 * artwork color extraction, Coil, and ExoPlayer eliminates redundant TLS handshakes,
 * socket exhaustion, and cold cache misses.
 */
object MediaNetworkClients {

    private const val TAG = "MediaNetworkClients"
    private const val MAX_CANVAS_CACHE_BYTES = 256L * 1024L * 1024L // 256 MB
    private const val MAX_IMAGE_DISK_CACHE_BYTES = 150L * 1024L * 1024L // 150 MB
    private const val WARM_CANVAS_PREFETCH_BYTES = 2L * 1024L * 1024L // 2 MB head warm
    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private val sharedConnectionPool = ConnectionPool(8, 5, TimeUnit.MINUTES)
    private val sharedDispatcher = Dispatcher().apply {
        maxRequests = 64
        maxRequestsPerHost = 12
    }

    val sharedOkHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(sharedConnectionPool)
        .dispatcher(sharedDispatcher)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Volatile
    private var appContextRef: Context? = null

    fun attachAppContext(context: Context) {
        if (appContextRef == null) {
            appContextRef = context.applicationContext
        }
    }

    data class DataUsageTrackTag(
        val track: TrackItem,
        val isLocal: Boolean = false,
        val isDownloaded: Boolean = false
    )

    internal object ArtworkCacheKeyNormalizer {
        private val GOOGLE_CDN_HOSTS = setOf(
            "lh3.googleusercontent.com",
            "lh4.googleusercontent.com",
            "lh5.googleusercontent.com",
            "yt3.ggpht.com",
            "yt3.googleusercontent.com"
        )
        private val WH_SUFFIX_REGEX = Regex("""=w(\d+)-h(\d+)([^/?#]*)$""")
        private val S_SUFFIX_REGEX = Regex("""=s(\d+)([^/?#]*)$""")

        fun normalizeUrl(rawUrl: String): String {
            val trimmed = rawUrl.trim()
            if (!trimmed.startsWith("http", ignoreCase = true)) return trimmed
            val host = runCatching { URI(trimmed).host?.lowercase().orEmpty() }.getOrDefault("")
            if (host !in GOOGLE_CDN_HOSTS) return trimmed

            WH_SUFFIX_REGEX.find(trimmed)?.let { m ->
                val w = m.groupValues[1].toIntOrNull() ?: return trimmed
                val h = m.groupValues[2].toIntOrNull() ?: return trimmed
                if (w != h) return trimmed
                val bucketSuffix = when {
                    w < 120 -> "=w180-h180-l90-rj"
                    w <= 650 -> "=w544-h544-l90-rj"
                    else -> "=w1080-h1080-l90-rj"
                }
                return WH_SUFFIX_REGEX.replace(trimmed, bucketSuffix)
            }
            S_SUFFIX_REGEX.find(trimmed)?.let { m ->
                val size = m.groupValues[1].toIntOrNull() ?: return trimmed
                val bucketSuffix = when {
                    size < 120 -> "=w180-h180-l90-rj"
                    size <= 650 -> "=w544-h544-l90-rj"
                    else -> "=w1080-h1080-l90-rj"
                }
                return S_SUFFIX_REGEX.replace(trimmed, bucketSuffix)
            }
            return trimmed
        }

        fun cacheKey(rawUrl: String): String {
            return normalizeUrl(rawUrl)
        }
    }

    private val artworkInFlightLocks = ConcurrentHashMap<String, Mutex>()

    private fun acquireArtworkMutex(key: String): Mutex {
        if (artworkInFlightLocks.size > 512) {
            artworkInFlightLocks.entries.removeIf { !it.value.isLocked }
        }
        return artworkInFlightLocks.computeIfAbsent(key) { Mutex() }
    }

    /**
     * Lightweight OkHttp network interceptor that counts exact bytes read from the network
     * response body and attributes them to [DataUsageTracker] under [category].
     */
    fun createDataUsageInterceptor(
        category: DataUsageCategory,
        appContextProvider: () -> Context? = { appContextRef }
    ): Interceptor = Interceptor { chain ->
        val req = chain.request()
        val response = chain.proceed(req)
        val body = response.body ?: return@Interceptor response
        val explicitTag = req.tag(DataUsageTrackTag::class.java)
        val reqUrl = req.url.toString()
        val countedBody = object : ResponseBody() {
            private val bytesRead = AtomicLong(0L)
            private val reported = AtomicBoolean(false)

            private fun reportOnce() {
                if (reported.compareAndSet(false, true)) {
                    val total = bytesRead.get()
                    if (total > 0L) {
                        val ctx = appContextProvider() ?: return
                        runCatching {
                            val tracker = DataUsageTracker.getInstance(ctx)
                            if (category == DataUsageCategory.ARTWORK && explicitTag == null) {
                                val activeArtKey = tracker.getActiveTrackContext()?.normalizedArtworkKey.orEmpty()
                                val reqArtKey = ArtworkCacheKeyNormalizer.cacheKey(reqUrl)
                                if (activeArtKey.isBlank() || reqArtKey != activeArtKey) {
                                    // Avoid charging unrelated Home/Search/Artist grid thumbnails to active song
                                    return
                                }
                            }
                            val networkType = PlaybackSettingsManager.getInstance(ctx).getActiveNetworkType()
                            tracker.recordNetworkBytes(
                                bytes = total,
                                category = category,
                                networkType = networkType,
                                track = explicitTag?.track,
                                isLocal = explicitTag?.isLocal,
                                isDownloaded = explicitTag?.isDownloaded == true
                            )
                        }
                    }
                }
            }

            override fun contentType(): MediaType? = body.contentType()
            override fun contentLength(): Long = body.contentLength()
            override fun source(): BufferedSource {
                val forwarding = object : ForwardingSource(body.source()) {
                    override fun read(sink: Buffer, byteCount: Long): Long {
                        val n = super.read(sink, byteCount)
                        if (n > 0L) {
                            bytesRead.addAndGet(n)
                        } else if (n == -1L) {
                            reportOnce()
                        }
                        return n
                    }

                    override fun close() {
                        reportOnce()
                        super.close()
                    }
                }
                return forwarding.buffer()
            }

            override fun close() {
                reportOnce()
                super.close()
            }
        }
        response.newBuilder().body(countedBody).build()
    }

    val appleMusicOkHttpClient: OkHttpClient = sharedOkHttpClient.newBuilder()
        .addInterceptor { chain ->
            val original = chain.request()
            val host = original.url.host.lowercase()
            val builder = original.newBuilder()
            if (original.header("User-Agent") == null) {
                builder.header("User-Agent", BROWSER_USER_AGENT)
            }
            if (original.header("Accept-Language") == null) {
                builder.header("Accept-Language", "en-US,en;q=0.9")
            }
            if (host.contains("apple.com")) {
                if (original.header("Origin") == null) {
                    builder.header("Origin", "https://music.apple.com")
                }
                if (original.header("Referer") == null) {
                    builder.header("Referer", "https://music.apple.com/")
                }
            }
            chain.proceed(builder.build())
        }
        .addNetworkInterceptor(createDataUsageInterceptor(DataUsageCategory.CANVAS))
        .build()

    @Volatile
    private var canvasCache: SimpleCache? = null
    private val canvasCacheLock = Any()

    @Volatile
    private var imageLoader: ImageLoader? = null
    private val imageLoaderLock = Any()

    private val warmedVideoUris = ConcurrentHashMap.newKeySet<String>()
    private val inFlightVideoWarms = ConcurrentHashMap.newKeySet<String>()

    @OptIn(UnstableApi::class)
    fun getCanvasCache(context: Context): SimpleCache {
        canvasCache?.let { return it }
        synchronized(canvasCacheLock) {
            canvasCache?.let { return it }
            val appContext = context.applicationContext
            val cacheDir = File(appContext.cacheDir, "media3_canvas_cache").apply { mkdirs() }
            val evictor = LeastRecentlyUsedCacheEvictor(MAX_CANVAS_CACHE_BYTES)
            val dbProvider = StandaloneDatabaseProvider(appContext)
            return SimpleCache(cacheDir, evictor, dbProvider).also { canvasCache = it }
        }
    }

    @OptIn(UnstableApi::class)
    fun getCanvasCacheDataSourceFactory(context: Context): CacheDataSource.Factory {
        val appContext = context.applicationContext
        val upstream = OkHttpDataSource.Factory(appleMusicOkHttpClient)
        return CacheDataSource.Factory()
            .setCache(getCanvasCache(appContext))
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    @OptIn(UnstableApi::class)
    fun getCanvasMediaSourceFactory(context: Context): MediaSource.Factory {
        val appContext = context.applicationContext
        val dataSourceFactory = DefaultDataSource.Factory(
            appContext,
            getCanvasCacheDataSourceFactory(appContext)
        )
        return DefaultMediaSourceFactory(dataSourceFactory)
    }

    fun getImageLoader(context: Context): ImageLoader {
        imageLoader?.let { return it }
        synchronized(imageLoaderLock) {
            imageLoader?.let { return it }
            val appContext = context.applicationContext
            attachAppContext(appContext)
            val coilHttpClient = sharedOkHttpClient.newBuilder()
                .addNetworkInterceptor(createDataUsageInterceptor(DataUsageCategory.ARTWORK) { appContext })
                .build()
            val singleFlightArtworkInterceptor = object : CoilInterceptor {
                override suspend fun intercept(chain: CoilInterceptor.Chain): ImageResult {
                    val req = chain.request
                    val rawStr = (req.data as? String)?.trim()
                    if (rawStr == null || !rawStr.startsWith("http", ignoreCase = true)) {
                        return chain.proceed(req)
                    }
                    val normalizedUrl = ArtworkCacheKeyNormalizer.normalizeUrl(rawStr)
                    val normalizedReq = req.newBuilder()
                        .data(normalizedUrl)
                        .diskCacheKey(normalizedUrl)
                        .build()
                    val mutex = acquireArtworkMutex(normalizedUrl)
                    return mutex.withLock {
                        chain.proceed(normalizedReq)
                    }
                }
            }
            val loader = ImageLoader.Builder(appContext)
                .okHttpClient(coilHttpClient)
                .components {
                    add(singleFlightArtworkInterceptor)
                }
                .respectCacheHeaders(false)
                .allowRgb565(true)
                .precision(coil.size.Precision.INEXACT)
                .memoryCache {
                    MemoryCache.Builder(appContext)
                        .maxSizePercent(0.20)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(File(appContext.cacheDir, "coil_image_cache"))
                        .maxSizeBytes(MAX_IMAGE_DISK_CACHE_BYTES)
                        .build()
                }
                .build()
            imageLoader = loader
            return loader
        }
    }

    /**
     * Enqueues a background image decode/disk-cache fill on the shared [ImageLoader].
     */
    fun prefetchImage(
        context: Context,
        imageUri: String?,
        targetSizePx: Int = 512,
        allowRgb565: Boolean = true
    ) {
        if (imageUri.isNullOrBlank()) return
        val appContext = context.applicationContext
        val effectiveRgb565 = allowRgb565 && !imageUri.contains("800x266bb.png", ignoreCase = true)
        val request = ImageRequest.Builder(appContext)
            .data(imageUri)
            .size(targetSizePx)
            .allowRgb565(effectiveRgb565)
            .build()
        getImageLoader(appContext).enqueue(request)
    }

    /**
     * Pre-buffers up to [maxBytes] of a direct `.mp4` canvas/header video into Media3's [SimpleCache]
     * so playback begins near-instantly when rendered. HLS `.m3u8` streams are skipped here because
     * exoplayer handles progressive segment buffering directly.
     */
    @OptIn(UnstableApi::class)
    suspend fun warmCanvasVideoCache(
        context: Context,
        videoUri: String,
        maxBytes: Long = WARM_CANVAS_PREFETCH_BYTES
    ) = withContext(Dispatchers.IO) {
        val clean = videoUri.trim()
        if (!clean.startsWith("http", ignoreCase = true)) return@withContext
        if (clean.contains(".m3u8", ignoreCase = true)) return@withContext
        if (warmedVideoUris.contains(clean)) return@withContext
        if (!inFlightVideoWarms.add(clean)) return@withContext

        try {
            val appContext = context.applicationContext
            val dataSource = getCanvasCacheDataSourceFactory(appContext).createDataSource()
            val dataSpec = DataSpec.Builder()
                .setUri(Uri.parse(clean))
                .setPosition(0L)
                .setLength(maxBytes)
                .build()
            CacheWriter(dataSource, dataSpec, null, null).cache()
            if (warmedVideoUris.size > 256) {
                warmedVideoUris.clear()
            }
            warmedVideoUris.add(clean)
        } catch (e: Exception) {
            Log.d(TAG, "Canvas cache warm skipped for ${clean.take(64)}: ${e.message}")
        } finally {
            inFlightVideoWarms.remove(clean)
        }
    }
}
