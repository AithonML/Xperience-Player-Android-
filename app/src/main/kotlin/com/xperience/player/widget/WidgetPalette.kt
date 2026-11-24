package com.xperience.player.widget

import android.content.Context
import android.content.res.ColorStateList
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

/**
 * Day/Night color pair applied via API 31+ `RemoteViews.setColorInt` / `setColorStateList`
 * so widgets automatically track system light & dark themes without requiring an app wake-up.
 */
data class DayNightColor(
    val light: Int,
    val dark: Int
)

/**
 * Complete Material You tonal palette for Xperience home-screen widgets.
 */
data class WidgetColorTokens(
    val surfaceContainer: DayNightColor,
    val surfaceCard: DayNightColor,
    val artworkPlaceholder: DayNightColor,
    val primary: DayNightColor,
    val onPrimary: DayNightColor,
    val secondaryContainer: DayNightColor,
    val onSecondaryContainer: DayNightColor,
    val iconButtonSurface: DayNightColor,
    val onSurface: DayNightColor,
    val onSurfaceVariant: DayNightColor,
    val accentText: DayNightColor,
    val progressTrack: DayNightColor,
    val divider: DayNightColor,
    val favoriteActive: DayNightColor
)

/**
 * Pure JVM-testable ARGB color blending and subtle artwork harmonization utilities.
 */
object WidgetColorMath {

    /**
     * Linearly blends [overlayArgb] onto [baseArgb] by [ratio] in `[0f, 1f]`, preserving full opacity.
     */
    fun blendOpaque(baseArgb: Int, overlayArgb: Int, ratio: Float): Int {
        val t = ratio.coerceIn(0f, 1f)
        val inv = 1f - t
        val r = (((baseArgb ushr 16) and 0xFF) * inv + ((overlayArgb ushr 16) and 0xFF) * t).roundToInt()
        val g = (((baseArgb ushr 8) and 0xFF) * inv + ((overlayArgb ushr 8) and 0xFF) * t).roundToInt()
        val b = ((baseArgb and 0xFF) * inv + (overlayArgb and 0xFF) * t).roundToInt()
        return (0xFF shl 24) or
            ((r.coerceIn(0, 255)) shl 16) or
            ((g.coerceIn(0, 255)) shl 8) or
            (b.coerceIn(0, 255))
    }

    /**
     * Gently infuses an extracted album artwork [accentArgb] into the Material You widget tokens
     * while preserving crisp legibility in both Light and Dark themes.
     */
    fun harmonizeWithArtworkAccent(
        base: WidgetColorTokens,
        accentArgb: Int?
    ): WidgetColorTokens {
        if (accentArgb == null) return base
        val opaqueAccent = accentArgb or (0xFF shl 24)
        return base.copy(
            surfaceContainer = DayNightColor(
                light = blendOpaque(base.surfaceContainer.light, opaqueAccent, 0.06f),
                dark = blendOpaque(base.surfaceContainer.dark, opaqueAccent, 0.14f)
            ),
            surfaceCard = DayNightColor(
                light = blendOpaque(base.surfaceCard.light, opaqueAccent, 0.08f),
                dark = blendOpaque(base.surfaceCard.dark, opaqueAccent, 0.16f)
            ),
            artworkPlaceholder = DayNightColor(
                light = blendOpaque(base.artworkPlaceholder.light, opaqueAccent, 0.18f),
                dark = blendOpaque(base.artworkPlaceholder.dark, opaqueAccent, 0.24f)
            ),
            primary = DayNightColor(
                light = blendOpaque(base.primary.light, opaqueAccent, 0.35f),
                dark = blendOpaque(base.primary.dark, opaqueAccent, 0.30f)
            ),
            accentText = DayNightColor(
                light = blendOpaque(base.accentText.light, opaqueAccent, 0.35f),
                dark = blendOpaque(base.accentText.dark, opaqueAccent, 0.30f)
            )
        )
    }
}

/**
 * Resolves live Android 13+ Material You system dynamic colors (`android.R.color.system_*`)
 * into light/dark pairs and applies them cleanly to [RemoteViews].
 */
object WidgetPalette {

    private const val FAVORITE_ACTIVE_LIGHT = 0xFFE11D48.toInt()
    private const val FAVORITE_ACTIVE_DARK = 0xFFFF6B8A.toInt()

    fun resolve(context: Context, artworkAccentArgb: Int? = null): WidgetColorTokens {
        fun color(resId: Int): Int = ContextCompat.getColor(context, resId)

        val base = WidgetColorTokens(
            surfaceContainer = DayNightColor(
                light = color(android.R.color.system_neutral1_50),
                dark = color(android.R.color.system_neutral1_900)
            ),
            surfaceCard = DayNightColor(
                light = color(android.R.color.system_neutral2_100),
                dark = color(android.R.color.system_neutral2_800)
            ),
            artworkPlaceholder = DayNightColor(
                light = color(android.R.color.system_accent1_100),
                dark = color(android.R.color.system_accent1_800)
            ),
            primary = DayNightColor(
                light = color(android.R.color.system_accent1_600),
                dark = color(android.R.color.system_accent1_200)
            ),
            onPrimary = DayNightColor(
                light = color(android.R.color.system_neutral1_0),
                dark = color(android.R.color.system_accent1_900)
            ),
            secondaryContainer = DayNightColor(
                light = color(android.R.color.system_accent2_100),
                dark = color(android.R.color.system_accent2_800)
            ),
            onSecondaryContainer = DayNightColor(
                light = color(android.R.color.system_accent2_900),
                dark = color(android.R.color.system_accent2_100)
            ),
            iconButtonSurface = DayNightColor(
                light = color(android.R.color.system_neutral2_100),
                dark = color(android.R.color.system_neutral2_800)
            ),
            onSurface = DayNightColor(
                light = color(android.R.color.system_neutral1_900),
                dark = color(android.R.color.system_neutral1_50)
            ),
            onSurfaceVariant = DayNightColor(
                light = color(android.R.color.system_neutral2_700),
                dark = color(android.R.color.system_neutral2_200)
            ),
            accentText = DayNightColor(
                light = color(android.R.color.system_accent1_700),
                dark = color(android.R.color.system_accent1_200)
            ),
            progressTrack = DayNightColor(
                light = color(android.R.color.system_neutral2_200),
                dark = color(android.R.color.system_neutral2_700)
            ),
            divider = DayNightColor(
                light = color(android.R.color.system_neutral2_200),
                dark = color(android.R.color.system_neutral2_700)
            ),
            favoriteActive = DayNightColor(
                light = FAVORITE_ACTIVE_LIGHT,
                dark = FAVORITE_ACTIVE_DARK
            )
        )

        return WidgetColorMath.harmonizeWithArtworkAccent(base, artworkAccentArgb)
    }

    fun applyBackgroundTint(rv: RemoteViews, viewId: Int, color: DayNightColor) {
        rv.setColorStateList(
            viewId,
            "setBackgroundTintList",
            ColorStateList.valueOf(color.light),
            ColorStateList.valueOf(color.dark)
        )
    }

    fun applyTextColor(rv: RemoteViews, viewId: Int, color: DayNightColor) {
        rv.setColorInt(viewId, "setTextColor", color.light, color.dark)
    }

    fun applyImageTint(rv: RemoteViews, viewId: Int, color: DayNightColor) {
        rv.setColorStateList(
            viewId,
            "setImageTintList",
            ColorStateList.valueOf(color.light),
            ColorStateList.valueOf(color.dark)
        )
    }

    fun applyProgressTint(
        rv: RemoteViews,
        viewId: Int,
        progressColor: DayNightColor,
        trackColor: DayNightColor
    ) {
        rv.setColorStateList(
            viewId,
            "setProgressTintList",
            ColorStateList.valueOf(progressColor.light),
            ColorStateList.valueOf(progressColor.dark)
        )
        rv.setColorStateList(
            viewId,
            "setProgressBackgroundTintList",
            ColorStateList.valueOf(trackColor.light),
            ColorStateList.valueOf(trackColor.dark)
        )
    }
}
