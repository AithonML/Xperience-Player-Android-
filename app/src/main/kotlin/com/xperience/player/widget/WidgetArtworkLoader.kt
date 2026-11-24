package com.xperience.player.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import coil.request.ImageRequest
import coil.size.Scale
import com.xperience.player.service.MediaNetworkClients
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pure JVM-testable artwork sizing and Binder IPC payload budget math.
 *
 * Android's Binder transaction buffer is 1 MB process-wide; capping total bitmap bytes per
 * widget update payload to [MAX_TOTAL_PAYLOAD_BYTES] ensures widgets never throw
 * `TransactionTooLargeException`, even when a responsive `RemoteViews(Map<SizeF, RemoteViews>)`
 * bundles multiple layout sizes and multiple playlist thumbnails together.
 */
object WidgetArtworkSizing {

    const val BYTES_PER_PIXEL_ARGB_8888: Int = 4
    const val MAX_TOTAL_PAYLOAD_BYTES: Int = 420_000
    const val MIN_SAFE_SIDE_PX: Int = 48
    const val MAX_SAFE_SIDE_PX: Int = 180

    /**
     * Computes a square pixel dimension that respects both [requestedPx] and the per-slot
     * Binder byte budget for a payload carrying [bitmapSlotsInPayload] bitmaps.
     */
    fun computeSafeTargetPx(
        requestedPx: Int,
        bitmapSlotsInPayload: Int,
        maxTotalPayloadBytes: Int = MAX_TOTAL_PAYLOAD_BYTES
    ): Int {
        val safeSlots = bitmapSlotsInPayload.coerceAtLeast(1)
        val safeMaxBytes = maxTotalPayloadBytes.coerceAtLeast(
            MIN_SAFE_SIDE_PX * MIN_SAFE_SIDE_PX * BYTES_PER_PIXEL_ARGB_8888
        )
        val budgetPerBitmap = safeMaxBytes / safeSlots
        val maxSideForBudget = floor(sqrt(budgetPerBitmap.toDouble() / BYTES_PER_PIXEL_ARGB_8888)).toInt()
        val cappedRequested = requestedPx.coerceIn(MIN_SAFE_SIDE_PX, MAX_SAFE_SIDE_PX)
        return min(cappedRequested, maxSideForBudget).coerceAtLeast(MIN_SAFE_SIDE_PX)
    }

    /**
     * Estimates raw ARGB_8888 byte footprint of a square bitmap of side [sidePx].
     */
    fun estimateBitmapBytes(sidePx: Int): Long {
        val safeSide = sidePx.coerceAtLeast(0).toLong()
        return safeSide * safeSide * BYTES_PER_PIXEL_ARGB_8888.toLong()
    }

    /**
     * Computes a power-of-two `BitmapFactory.Options.inSampleSize` so decoded dimensions stay
     * close to [targetPx] without loading full multi-megapixel images into RAM.
     */
    fun calculateInSampleSize(
        sourceWidth: Int,
        sourceHeight: Int,
        targetPx: Int
    ): Int {
        if (sourceWidth <= 0 || sourceHeight <= 0 || targetPx <= 0) return 1
        var sampleSize = 1
        val halfWidth = sourceWidth / 2
        val halfHeight = sourceHeight / 2
        while ((halfWidth / sampleSize) >= targetPx && (halfHeight / sampleSize) >= targetPx) {
            sampleSize *= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

    /**
     * Computes the centered square source crop rectangle `(left, top, right, bottom)` for
     * center-cropping arbitrary aspect-ratio artwork without distortion.
     */
    fun computeCenterSquareCrop(sourceWidth: Int, sourceHeight: Int): IntArray {
        if (sourceWidth <= 0 || sourceHeight <= 0) return intArrayOf(0, 0, 0, 0)
        val side = min(sourceWidth, sourceHeight)
        val left = (sourceWidth - side) / 2
        val top = (sourceHeight - side) / 2
        return intArrayOf(left, top, left + side, top + side)
    }
}

/**
 * Asynchronous artwork loader, downscaler, rounded-corner clipper, and in-memory cache
 * dedicated to Xperience home-screen widgets.
 */
object WidgetArtworkLoader {

    private const val TAG = "WidgetArtworkLoader"
    private const val CACHE_MAX_BYTES = 2 * 1024 * 1024 // 2 MB bounded LRU cache for rounded widget tiles
    private const val DEFAULT_CORNER_RADIUS_RATIO = 0.18f

    private val bitmapCache = object : LruCache<String, Bitmap>(CACHE_MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    fun getCached(
        artworkUri: String?,
        artworkResId: Int?,
        targetPx: Int,
        cornerRadiusRatio: Float = DEFAULT_CORNER_RADIUS_RATIO
    ): Bitmap? {
        val key = buildCacheKey(artworkUri, artworkResId, targetPx, cornerRadiusRatio) ?: return null
        val cached = bitmapCache.get(key)
        return if (cached != null && !cached.isRecycled) cached else null
    }

    suspend fun loadRoundedArtwork(
        context: Context,
        artworkUri: String?,
        artworkResId: Int? = null,
        targetPx: Int,
        cornerRadiusRatio: Float = DEFAULT_CORNER_RADIUS_RATIO
    ): Bitmap? {
        val safePx = targetPx.coerceIn(
            WidgetArtworkSizing.MIN_SAFE_SIDE_PX,
            WidgetArtworkSizing.MAX_SAFE_SIDE_PX
        )
        val cacheKey = buildCacheKey(artworkUri, artworkResId, safePx, cornerRadiusRatio) ?: return null
        bitmapCache.get(cacheKey)?.takeIf { !it.isRecycled }?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val appContext = context.applicationContext
                val rawBitmapPair: Pair<Bitmap, Boolean>? = when {
                    !artworkUri.isNullOrBlank() -> loadFromUri(appContext, artworkUri.trim(), safePx)
                    artworkResId != null && artworkResId != 0 -> decodeFromResource(appContext, artworkResId, safePx)
                    else -> null
                }
                if (rawBitmapPair == null) return@withContext null

                val (rawBitmap, ownsRaw) = rawBitmapPair
                try {
                    if (rawBitmap.isRecycled || rawBitmap.width <= 0 || rawBitmap.height <= 0) {
                        return@withContext null
                    }
                    val rounded = clipToRoundedSquare(rawBitmap, safePx, cornerRadiusRatio)
                    bitmapCache.put(cacheKey, rounded)
                    rounded
                } finally {
                    if (ownsRaw && !rawBitmap.isRecycled) {
                        rawBitmap.recycle()
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.w(TAG, "Widget artwork load failed for ${artworkUri ?: artworkResId}: ${e.message}")
                null
            } catch (_: OutOfMemoryError) {
                bitmapCache.evictAll()
                null
            }
        }
    }

    private suspend fun loadFromUri(
        appContext: Context,
        cleanUri: String,
        targetPx: Int
    ): Pair<Bitmap, Boolean>? {
        val dataModel: Any = if (!cleanUri.contains("://") && cleanUri.startsWith("/")) {
            val localFile = File(cleanUri)
            if (!localFile.exists()) return null
            localFile
        } else {
            cleanUri
        }

        val imageLoader = MediaNetworkClients.getImageLoader(appContext)
        val request = ImageRequest.Builder(appContext)
            .data(dataModel)
            .size(targetPx, targetPx)
            .scale(Scale.FILL)
            .allowHardware(false)
            .build()

        val drawable = imageLoader.execute(request).drawable ?: return null
        val existingBitmap = (drawable as? BitmapDrawable)?.bitmap
        if (existingBitmap != null && !existingBitmap.isRecycled) {
            if (existingBitmap.config == Bitmap.Config.HARDWARE) {
                val soft = runCatching { existingBitmap.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
                return soft?.let { it to true }
            }
            return existingBitmap to false
        }
        val rendered = runCatching {
            drawable.toBitmap(width = targetPx, height = targetPx, config = Bitmap.Config.ARGB_8888)
        }.getOrNull()
        return rendered?.let { it to true }
    }

    private fun decodeFromResource(
        appContext: Context,
        resId: Int,
        targetPx: Int
    ): Pair<Bitmap, Boolean>? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(appContext.resources, resId, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = WidgetArtworkSizing.calculateInSampleSize(
                sourceWidth = bounds.outWidth,
                sourceHeight = bounds.outHeight,
                targetPx = targetPx
            )
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeResource(appContext.resources, resId, decodeOptions)
        return decoded?.let { it to true }
    }

    private fun clipToRoundedSquare(
        source: Bitmap,
        targetPx: Int,
        cornerRadiusRatio: Float
    ): Bitmap {
        val output = Bitmap.createBitmap(targetPx, targetPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val radius = (targetPx * cornerRadiusRatio.coerceIn(0f, 0.5f))
        val destRectF = RectF(0f, 0f, targetPx.toFloat(), targetPx.toFloat())
        canvas.drawRoundRect(destRectF, radius, radius, paint)

        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val crop = WidgetArtworkSizing.computeCenterSquareCrop(source.width, source.height)
        val srcRect = Rect(crop[0], crop[1], crop[2], crop[3])
        val destRect = Rect(0, 0, targetPx, targetPx)
        canvas.drawBitmap(source, srcRect, destRect, paint)
        return output
    }

    private fun buildCacheKey(
        artworkUri: String?,
        artworkResId: Int?,
        targetPx: Int,
        cornerRadiusRatio: Float
    ): String? {
        val sourceKey = artworkUri?.trim()?.takeIf { it.isNotEmpty() }
            ?: artworkResId?.takeIf { it != 0 }?.let { "res:$it" }
            ?: return null
        val radiusBucket = (cornerRadiusRatio * 100).toInt()
        return "$sourceKey|$targetPx|$radiusBucket"
    }
}
