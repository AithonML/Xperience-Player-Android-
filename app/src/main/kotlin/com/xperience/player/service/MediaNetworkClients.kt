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
import coil.memory.MemoryCache
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

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
            val diskCacheDir = File(appContext.cacheDir, "coil_image_cache").apply { mkdirs() }
            val loader = ImageLoader.Builder(appContext)
                .okHttpClient(sharedOkHttpClient)
                .respectCacheHeaders(false)
                .memoryCache {
                    MemoryCache.Builder(appContext)
                        .maxSizePercent(0.20)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(diskCacheDir)
                        .maxSizeBytes(MAX_IMAGE_DISK_CACHE_BYTES)
                        .build()
                }
                .build()
            imageLoader = loader
            runCatching { Coil.setImageLoader(loader) }
            return loader
        }
    }

    /**
     * Enqueues a background image decode/disk-cache fill on the shared [ImageLoader].
     */
    fun prefetchImage(context: Context, imageUri: String?) {
        if (imageUri.isNullOrBlank()) return
        val appContext = context.applicationContext
        val request = ImageRequest.Builder(appContext)
            .data(imageUri)
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
