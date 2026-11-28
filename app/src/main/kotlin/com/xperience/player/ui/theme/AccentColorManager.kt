package com.xperience.player.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.request.ImageRequest
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.service.MediaNetworkClients
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.min

val LocalAccentColor = staticCompositionLocalOf { Color(0xFF00E5FF) }

enum class AccentColorOption(val label: String, val color: Color?) {
    DYNAMIC("Dynamic", null),
    TURQUOISE("Turquoise", Color(0xFF00E5FF)),
    PURPLE("Purple", Color(0xFFA855F7)),
    RED("Red", Color(0xFFEF4444)),
    ORANGE("Orange", Color(0xFFF97316)),
    BLUE("Blue", Color(0xFF3B82F6)),
    GREEN("Green", Color(0xFF10B981))
}

data class ArtistAmbientColors(
    val dark: Color,
    val light: Color
) {
    fun colorFor(isDark: Boolean): Color = if (isDark) dark else light
}

@Immutable
data class AuroraColors(
    val color1: Color,
    val color2: Color,
    val color3: Color,
    val color4: Color
)

val DEFAULT_AURORA_COLORS = AuroraColors(
    color1 = Color(0xFF00E5FF),
    color2 = Color(0xFF8B5CF6),
    color3 = Color(0xFF10B981),
    color4 = Color(0xFF3B82F6)
)

object ArtworkColorExtractor {
    val DefaultAccent = Color(0xFF00E5FF)
    val DEFAULT_AURORA_COLORS: AuroraColors = com.xperience.player.ui.theme.DEFAULT_AURORA_COLORS
    private val FallbackDark = Color(0xFF0F1012)
    private val FallbackLight = Color(0xFFF4F6F9)
    private val colorCache = ConcurrentHashMap<String, Color>()
    private val inFlightAccent = ConcurrentHashMap<String, Deferred<Color>>()
    private val ambientColorCache = ConcurrentHashMap<String, ArtistAmbientColors>()
    private val inFlightAmbient = ConcurrentHashMap<String, Deferred<ArtistAmbientColors>>()
    private val auroraColorCache = ConcurrentHashMap<String, AuroraColors>()
    private val inFlightAurora = ConcurrentHashMap<String, Deferred<AuroraColors>>()
    private val extractionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun getCachedAuroraColors(artworkUri: String?): AuroraColors? {
        val clean = artworkUri?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { MediaNetworkClients.ArtworkCacheKeyNormalizer.normalizeUrl(it) }
            ?: return null
        return auroraColorCache[clean]
    }

    fun getCachedAccentColor(track: TrackItem?): Color? {
        if (track == null) return null
        val cacheKey = track.artworkUri
            ?.let { MediaNetworkClients.ArtworkCacheKeyNormalizer.normalizeUrl(it) }
            ?: track.artworkResId?.toString()
            ?: track.id
        return colorCache[cacheKey]
    }

    fun getCachedArtistAmbientColors(imageUri: String?): ArtistAmbientColors? {
        val clean = imageUri?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { MediaNetworkClients.ArtworkCacheKeyNormalizer.normalizeUrl(it) }
            ?: return null
        return ambientColorCache[clean]
    }

    fun cacheArtistAmbientColors(imageUri: String?, colors: ArtistAmbientColors) {
        val clean = imageUri?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { MediaNetworkClients.ArtworkCacheKeyNormalizer.normalizeUrl(it) }
            ?: return
        if (ambientColorCache.size > 512) {
            ambientColorCache.clear()
        }
        ambientColorCache[clean] = colors
    }

    /**
     * Derives dark/light ambient background surfaces directly from Apple Music hex metadata
     * (`bgColor` / `textColor1`) with zero image fetch or Palette overhead.
     */
    fun artistAmbientColorsFromHex(
        bgColorHex: String?,
        keyColorHex: String? = null
    ): ArtistAmbientColors? {
        val primaryRgb = parseHexRgb(bgColorHex) ?: parseHexRgb(keyColorHex) ?: return null
        return ambientColorsFromRgbInt(primaryRgb)
    }

    private fun parseHexRgb(raw: String?): Int? {
        val clean = raw?.trim()?.removePrefix("#") ?: return null
        if (clean.length != 6) return null
        val rgb = clean.toIntOrNull(16) ?: return null
        return (0xFF shl 24) or rgb
    }

    private fun ambientColorsFromRgbInt(rgb: Int): ArtistAmbientColors {
        return ArtistAmbientColors(
            dark = swatchToAmbientColor(rgb, minSat = 0.35f, maxSat = 0.65f, minVal = 0.08f, maxVal = 0.16f),
            light = swatchToAmbientColor(rgb, minSat = 0.06f, maxSat = 0.16f, minVal = 0.93f, maxVal = 0.98f)
        )
    }

    private fun swatchToAmbientColor(
        rgb: Int,
        minSat: Float,
        maxSat: Float,
        minVal: Float,
        maxVal: Float
    ): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(rgb, hsv)
        hsv[1] = hsv[1].coerceIn(minSat, maxSat)
        hsv[2] = hsv[2].coerceIn(minVal, maxVal)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    private fun decodeSampledResourceBitmap(context: Context, resId: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(context.resources, resId, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while ((bounds.outWidth / (sampleSize * 2)) >= 128 &&
            (bounds.outHeight / (sampleSize * 2)) >= 128
        ) {
            sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeResource(context.resources, resId, decodeOpts)
    }

    private fun toPaletteSafeBitmap(source: Bitmap?, ownsSource: Boolean): Pair<Bitmap, Boolean>? {
        if (source == null || source.isRecycled || source.width <= 0 || source.height <= 0) {
            return null
        }
        val isHardware = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            source.config == Bitmap.Config.HARDWARE
        if (isHardware) {
            val softCopy = runCatching { source.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
            if (ownsSource && !source.isRecycled) {
                source.recycle()
            }
            if (softCopy == null || softCopy.isRecycled || softCopy.width <= 0 || softCopy.height <= 0) {
                return null
            }
            return softCopy to true
        }
        return source to ownsSource
    }

    private fun drawableToPaletteSafeBitmap(drawable: Drawable?): Pair<Bitmap, Boolean>? {
        if (drawable == null) return null
        val rawBitmap = (drawable as? BitmapDrawable)?.bitmap
        if (rawBitmap != null) {
            return toPaletteSafeBitmap(rawBitmap, ownsSource = false)
        }
        val converted = runCatching {
            drawable.toBitmap(width = 128, height = 128, config = Bitmap.Config.ARGB_8888)
        }.getOrNull()
        return toPaletteSafeBitmap(converted, ownsSource = true)
    }

    private inline fun <T> withPaletteFromBitmap(
        pair: Pair<Bitmap, Boolean>?,
        maxColors: Int = 16,
        block: (Palette) -> T
    ): T? {
        if (pair == null) return null
        val (bitmap, ownsBitmap) = pair
        return try {
            if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return null
            val palette = Palette.from(bitmap)
                .maximumColorCount(maxColors)
                .generate()
            block(palette)
        } finally {
            if (ownsBitmap && !bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    suspend fun extractAccentColor(context: Context, track: TrackItem?): Color {
        if (track == null) return DefaultAccent
        val normalizedUri = track.artworkUri?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { MediaNetworkClients.ArtworkCacheKeyNormalizer.normalizeUrl(it) }
        val cacheKey = normalizedUri ?: track.artworkResId?.toString() ?: track.id
        colorCache[cacheKey]?.let { return it }

        val appContext = context.applicationContext
        val normalizedTrack = if (normalizedUri != null && normalizedUri != track.artworkUri) {
            track.copy(artworkUri = normalizedUri)
        } else {
            track
        }
        val deferred = inFlightAccent.computeIfAbsent(cacheKey) {
            extractionScope.async(start = CoroutineStart.LAZY) {
                try {
                    computeAccentColor(appContext, normalizedTrack, cacheKey)
                } finally {
                    inFlightAccent.remove(cacheKey)
                }
            }
        }
        return deferred.await()
    }

    private suspend fun computeAccentColor(
        context: Context,
        track: TrackItem,
        cacheKey: String
    ): Color {
        try {
            val bitmapPair: Pair<Bitmap, Boolean>? = when {
                track.artworkResId != null -> {
                    toPaletteSafeBitmap(
                        decodeSampledResourceBitmap(context, track.artworkResId),
                        ownsSource = true
                    )
                }
                track.artworkUri != null -> {
                    val loader = MediaNetworkClients.getImageLoader(context)
                    val req = ImageRequest.Builder(context)
                        .data(track.artworkUri)
                        .allowHardware(false)
                        .size(128, 128)
                        .build()
                    val res = loader.execute(req)
                    drawableToPaletteSafeBitmap(res.drawable)
                }
                else -> null
            }

            val extractedColor = withPaletteFromBitmap(bitmapPair) { palette ->
                val targetSwatch = palette.vibrantSwatch
                    ?: palette.dominantSwatch
                    ?: palette.lightVibrantSwatch
                    ?: palette.darkVibrantSwatch
                    ?: palette.mutedSwatch

                targetSwatch?.let { swatch ->
                    val hsv = FloatArray(3)
                    android.graphics.Color.colorToHSV(swatch.rgb, hsv)
                    // Harmonize and boost saturation & brightness so it pops vibrantly on dark obsidian background
                    hsv[1] = hsv[1].coerceIn(0.55f, 1.0f) // Saturation
                    hsv[2] = hsv[2].coerceIn(0.70f, 1.0f) // Brightness / Value
                    Color(android.graphics.Color.HSVToColor(hsv))
                }
            }

            if (extractedColor != null) {
                if (colorCache.size > 512) {
                    colorCache.clear()
                }
                colorCache[cacheKey] = extractedColor
                return extractedColor
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            // Fallback safely to default on any decode or fetch error
        } catch (_: OutOfMemoryError) {
            // Fallback safely on extreme bitmap decode pressure
        }

        return DefaultAccent
    }

    suspend fun extractArtistAmbientColors(context: Context, imageUri: String?): ArtistAmbientColors {
        val cleanUri = imageUri?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { MediaNetworkClients.ArtworkCacheKeyNormalizer.normalizeUrl(it) }
            ?: return ArtistAmbientColors(FallbackDark, FallbackLight)

        ambientColorCache[cleanUri]?.let { return it }

        val appContext = context.applicationContext
        val deferred = inFlightAmbient.computeIfAbsent(cleanUri) {
            extractionScope.async(start = CoroutineStart.LAZY) {
                try {
                    computeArtistAmbientColors(appContext, cleanUri)
                } finally {
                    inFlightAmbient.remove(cleanUri)
                }
            }
        }
        return deferred.await()
    }

    private suspend fun computeArtistAmbientColors(context: Context, imageUri: String): ArtistAmbientColors {
        try {
            val loader = MediaNetworkClients.getImageLoader(context)
            val req = ImageRequest.Builder(context)
                .data(imageUri)
                .allowHardware(false)
                .size(128, 128)
                .build()
            val res = loader.execute(req)
            val bitmapPair = drawableToPaletteSafeBitmap(res.drawable)

            val computed = withPaletteFromBitmap(bitmapPair) { palette ->
                val darkSwatch = palette.darkVibrantSwatch
                    ?: palette.darkMutedSwatch
                    ?: palette.dominantSwatch
                    ?: palette.mutedSwatch

                val darkColor = darkSwatch?.let {
                    swatchToAmbientColor(it.rgb, minSat = 0.35f, maxSat = 0.65f, minVal = 0.08f, maxVal = 0.16f)
                } ?: FallbackDark

                val lightSwatch = palette.lightVibrantSwatch
                    ?: palette.lightMutedSwatch
                    ?: palette.dominantSwatch
                    ?: palette.mutedSwatch
                    ?: palette.vibrantSwatch

                val lightColor = lightSwatch?.let {
                    swatchToAmbientColor(it.rgb, minSat = 0.06f, maxSat = 0.16f, minVal = 0.93f, maxVal = 0.98f)
                } ?: FallbackLight

                ArtistAmbientColors(dark = darkColor, light = lightColor)
            }
            if (computed != null) {
                cacheArtistAmbientColors(imageUri, computed)
                return computed
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            // Fallback on error
        } catch (_: OutOfMemoryError) {
            // Fallback on extreme decode pressure
        }

        return ArtistAmbientColors(FallbackDark, FallbackLight)
    }

    suspend fun extractAmbientBackgroundColor(context: Context, imageUri: String?, isDark: Boolean = true): Color {
        return extractArtistAmbientColors(context, imageUri).colorFor(isDark)
    }

    /**
     * Extracts 4 distinct, luminous ambient colors from [artworkUri] using Android [Palette]
     * with memory caching and single-flight deduplication so rapid track switches smoothly drive
     * the Home Aurora Borealis background in both dark and light modes.
     */
    suspend fun extractAuroraColors(context: Context, artworkUri: String?): AuroraColors {
        val cleanUri = artworkUri?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { MediaNetworkClients.ArtworkCacheKeyNormalizer.normalizeUrl(it) }
            ?: return DEFAULT_AURORA_COLORS

        auroraColorCache[cleanUri]?.let { return it }

        val appContext = context.applicationContext
        val deferred = inFlightAurora.computeIfAbsent(cleanUri) {
            extractionScope.async(start = CoroutineStart.LAZY) {
                try {
                    computeAuroraColors(appContext, cleanUri)
                } finally {
                    inFlightAurora.remove(cleanUri)
                }
            }
        }
        return deferred.await()
    }

    private suspend fun computeAuroraColors(context: Context, artworkUri: String): AuroraColors {
        try {
            val loader = MediaNetworkClients.getImageLoader(context)
            val req = ImageRequest.Builder(context)
                .data(artworkUri)
                .allowHardware(false)
                .size(128, 128)
                .build()
            val res = loader.execute(req)
            val bitmapPair = drawableToPaletteSafeBitmap(res.drawable)

            val aurora = withPaletteFromBitmap(bitmapPair, maxColors = 16) { palette ->
                buildAuroraColorsFromPalette(palette)
            }
            if (aurora != null) {
                if (auroraColorCache.size > 512) {
                    auroraColorCache.clear()
                }
                auroraColorCache[artworkUri] = aurora
                return aurora
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            // Fall back safely on decode or network failure
        } catch (_: OutOfMemoryError) {
            // Fall back safely on extreme decode pressure
        }

        return DEFAULT_AURORA_COLORS
    }

    private fun buildAuroraColorsFromPalette(palette: Palette): AuroraColors {
        val candidates = buildList {
            palette.vibrantSwatch?.rgb?.let(::add)
            palette.lightVibrantSwatch?.rgb?.let(::add)
            palette.darkVibrantSwatch?.rgb?.let(::add)
            palette.dominantSwatch?.rgb?.let(::add)
            palette.mutedSwatch?.rgb?.let(::add)
            palette.lightMutedSwatch?.rgb?.let(::add)
            palette.darkMutedSwatch?.rgb?.let(::add)
            palette.swatches
                .sortedByDescending { it.population }
                .forEach { add(it.rgb) }
        }.distinct()

        if (candidates.isEmpty()) return DEFAULT_AURORA_COLORS

        val pickedHsv = mutableListOf<FloatArray>()

        // Pass 1: select perceptually distinct swatches (different hue or significant luminance/saturation shift)
        for (rgb in candidates) {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(rgb, hsv)
            if (pickedHsv.isEmpty() || pickedHsv.none { isTooClose(it, hsv, minHueDeg = 26f, minDelta = 0.24f) }) {
                pickedHsv.add(hsv)
                if (pickedHsv.size == 4) break
            }
        }

        // Pass 2: relax distinctness threshold slightly if cover has tighter palette
        if (pickedHsv.size < 4) {
            for (rgb in candidates) {
                val hsv = FloatArray(3)
                android.graphics.Color.colorToHSV(rgb, hsv)
                if (pickedHsv.none { isTooClose(it, hsv, minHueDeg = 14f, minDelta = 0.14f) }) {
                    pickedHsv.add(hsv)
                    if (pickedHsv.size == 4) break
                }
            }
        }

        // Pass 3: synthesize harmonious aurora companions if cover is strictly monochrome/duotone
        val seed = pickedHsv.first()
        val isAchromatic = seed[1] < 0.08f
        val achromaticHues = floatArrayOf(196f, 226f, 260f, 172f)
        val chromaticHueOffsets = floatArrayOf(0f, 34f, -38f, 68f)

        while (pickedHsv.size < 4) {
            val slot = pickedHsv.size
            val synthetic = if (isAchromatic) {
                floatArrayOf(achromaticHues[slot], 0.24f + slot * 0.04f, 0.72f)
            } else {
                floatArrayOf(
                    ((seed[0] + chromaticHueOffsets[slot]) % 360f + 360f) % 360f,
                    (seed[1] * (0.88f + slot * 0.05f)).coerceIn(0.45f, 0.92f),
                    (seed[2] + if (slot == 2) -0.15f else 0.10f).coerceIn(0.45f, 0.94f)
                )
            }
            pickedHsv.add(synthetic)
        }

        return AuroraColors(
            color1 = harmonizeAuroraSlot(pickedHsv[0], slot = 0),
            color2 = harmonizeAuroraSlot(pickedHsv[1], slot = 1),
            color3 = harmonizeAuroraSlot(pickedHsv[2], slot = 2),
            color4 = harmonizeAuroraSlot(pickedHsv[3], slot = 3)
        )
    }

    private fun isTooClose(a: FloatArray, b: FloatArray, minHueDeg: Float, minDelta: Float): Boolean {
        val rawHueDiff = abs(a[0] - b[0])
        val hueDiff = min(rawHueDiff, 360f - rawHueDiff)
        val satDiff = abs(a[1] - b[1])
        val valDiff = abs(a[2] - b[2])
        val bothChromatic = a[1] >= 0.12f && b[1] >= 0.12f
        return if (bothChromatic) {
            hueDiff < minHueDeg && satDiff < minDelta && valDiff < minDelta
        } else {
            satDiff < minDelta && valDiff < minDelta
        }
    }

    private fun harmonizeAuroraSlot(sourceHsv: FloatArray, slot: Int): Color {
        val hsv = sourceHsv.copyOf()
        if (hsv[1] < 0.08f) {
            // Keep grayscale / B&W covers subtle arctic-mist rather than shifting Hue=0 to neon red
            val mistHues = floatArrayOf(198f, 224f, 252f, 176f)
            hsv[0] = mistHues[slot.coerceIn(0, 3)]
            hsv[1] = 0.22f
            hsv[2] = when (slot) {
                0 -> 0.80f
                1 -> 0.86f
                2 -> 0.56f
                else -> 0.74f
            }
        } else {
            when (slot) {
                0 -> {
                    hsv[1] = hsv[1].coerceIn(0.56f, 0.94f)
                    hsv[2] = hsv[2].coerceIn(0.68f, 0.96f)
                }
                1 -> {
                    hsv[1] = hsv[1].coerceIn(0.48f, 0.90f)
                    hsv[2] = hsv[2].coerceIn(0.72f, 0.98f)
                }
                2 -> {
                    hsv[1] = hsv[1].coerceIn(0.52f, 0.92f)
                    hsv[2] = hsv[2].coerceIn(0.44f, 0.78f)
                }
                else -> {
                    hsv[1] = hsv[1].coerceIn(0.44f, 0.88f)
                    hsv[2] = hsv[2].coerceIn(0.60f, 0.94f)
                }
            }
        }
        return Color(android.graphics.Color.HSVToColor(hsv))
    }
}
