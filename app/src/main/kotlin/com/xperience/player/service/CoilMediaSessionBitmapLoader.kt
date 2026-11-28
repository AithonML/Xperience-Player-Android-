package com.xperience.player.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import coil.request.ImageRequest
import coil.size.Scale
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Media3 [BitmapLoader] backed by the shared Coil [coil.ImageLoader] in [MediaNetworkClients].
 *
 * Ensures MediaSession notifications and lockscreen artwork read directly from the unified
 * in-memory and disk cover-art cache instead of opening a separate `HttpURLConnection`.
 */
@OptIn(UnstableApi::class)
class CoilMediaSessionBitmapLoader(
    context: Context
) : BitmapLoader {

    private val appContext = context.applicationContext
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun supportsMimeType(mimeType: String): Boolean {
        return mimeType.startsWith("image/", ignoreCase = true)
    }

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        ioScope.launch {
            try {
                val decoded = BitmapFactory.decodeByteArray(data, 0, data.size)
                if (decoded != null) {
                    future.set(decoded)
                } else {
                    future.setException(IllegalArgumentException("Could not decode image byte array"))
                }
            } catch (t: Throwable) {
                future.setException(t)
            }
        }
        return future
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        ioScope.launch {
            try {
                val normalized = MediaNetworkClients.ArtworkCacheKeyNormalizer.normalizeUrl(uri.toString())
                val request = ImageRequest.Builder(appContext)
                    .data(normalized)
                    .size(544, 544)
                    .scale(Scale.FILL)
                    .allowHardware(false)
                    .build()
                val drawable = MediaNetworkClients.getImageLoader(appContext).execute(request).drawable
                if (drawable == null) {
                    future.setException(IllegalStateException("Null drawable for artwork uri: $normalized"))
                    return@launch
                }
                val existing = (drawable as? BitmapDrawable)?.bitmap
                val bitmap = when {
                    existing != null && !existing.isRecycled && existing.config != Bitmap.Config.HARDWARE -> existing
                    else -> drawable.toBitmap(width = 544, height = 544, config = Bitmap.Config.ARGB_8888)
                }
                future.set(bitmap)
            } catch (t: Throwable) {
                future.setException(t)
            }
        }
        return future
    }
}
