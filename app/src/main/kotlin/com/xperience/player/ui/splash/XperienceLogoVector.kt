package com.xperience.player.ui.splash

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathMeasure

/**
 * Viewport dimension matching `ic_launcher_foreground.xml` and `ic_splash_logo.xml` (108x108).
 * Sharing the exact coordinate system guarantees zero visual shift during the
 * `core-splashscreen` -> Compose transition.
 */
internal const val LOGO_VIEWPORT_SIZE = 108f

@Immutable
data class ScaledXperienceLogo(
    val xPath: Path,
    val notePath: Path,
    val wavePath: Path,
    val xMeasure: PathMeasure,
    val noteMeasure: PathMeasure,
    val waveMeasure: PathMeasure,
    val xLength: Float,
    val noteLength: Float,
    val waveLength: Float
)

/**
 * Builds the 3 independent vector paths of the Xperience brand mark in [LOGO_VIEWPORT_SIZE] space
 * and scales them uniformly to fit [widthPx] x [heightPx], pre-computing [PathMeasure] instances
 * so per-frame progressive stroke rendering allocates zero objects.
 */
fun buildScaledXperienceLogo(widthPx: Float, heightPx: Float): ScaledXperienceLogo {
    val safeW = widthPx.coerceAtLeast(1f)
    val safeH = heightPx.coerceAtLeast(1f)
    val scale = minOf(safeW, safeH) / LOGO_VIEWPORT_SIZE
    val dx = (safeW - LOGO_VIEWPORT_SIZE * scale) * 0.5f
    val dy = (safeH - LOGO_VIEWPORT_SIZE * scale) * 0.5f

    val matrix = Matrix().apply {
        translate(x = dx, y = dy)
        scale(x = scale, y = scale)
    }

    val xPath = createLogoXPath().apply {
        fillType = PathFillType.NonZero
        transform(matrix)
    }
    val notePath = createLogoNotePath().apply {
        fillType = PathFillType.NonZero
        transform(matrix)
    }
    val wavePath = createLogoWavePath().apply {
        fillType = PathFillType.NonZero
        transform(matrix)
    }

    val xMeasure = PathMeasure().apply { setPath(xPath, forceClosed = false) }
    val noteMeasure = PathMeasure().apply { setPath(notePath, forceClosed = false) }
    val waveMeasure = PathMeasure().apply { setPath(wavePath, forceClosed = false) }

    return ScaledXperienceLogo(
        xPath = xPath,
        notePath = notePath,
        wavePath = wavePath,
        xMeasure = xMeasure,
        noteMeasure = noteMeasure,
        waveMeasure = waveMeasure,
        xLength = xMeasure.length.coerceAtLeast(1f),
        noteLength = noteMeasure.length.coerceAtLeast(1f),
        waveLength = waveMeasure.length.coerceAtLeast(1f)
    )
}

fun createLogoXPath(): Path = Path().apply {
    moveTo(31.58f, 33.19f)
    lineTo(40.25f, 33.19f)
    lineTo(50.49f, 48.64f)
    cubicTo(51.58f, 46.88f, 52.70f, 45.11f, 53.95f, 43.45f)
    lineTo(55.87f, 41.11f)
    cubicTo(56.91f, 39.85f, 58.22f, 38.86f, 59.50f, 37.88f)
    cubicTo(61.48f, 36.35f, 64.15f, 35.42f, 66.67f, 35.57f)
    cubicTo(67.76f, 35.64f, 68.79f, 35.96f, 69.80f, 36.33f)
    cubicTo(69.06f, 36.47f, 68.24f, 36.55f, 67.56f, 36.93f)
    cubicTo(65.48f, 38.08f, 64.15f, 40.22f, 62.85f, 42.13f)
    lineTo(54.91f, 54.28f)
    lineTo(58.37f, 59.31f)
    lineTo(59.98f, 61.63f)
    cubicTo(60.47f, 62.28f, 60.93f, 62.94f, 61.59f, 63.44f)
    cubicTo(62.08f, 63.82f, 62.64f, 64.10f, 63.19f, 64.35f)
    cubicTo(62.19f, 64.48f, 61.20f, 64.66f, 60.23f, 64.95f)
    cubicTo(58.81f, 65.38f, 57.51f, 66.14f, 56.30f, 66.97f)
    lineTo(54.50f, 68.60f)
    cubicTo(54.51f, 67.87f, 54.56f, 67.07f, 54.33f, 66.36f)
    cubicTo(53.98f, 65.30f, 53.36f, 64.47f, 52.78f, 63.54f)
    lineTo(50.48f, 60.06f)
    lineTo(40.78f, 75.41f)
    lineTo(31.57f, 75.40f)
    lineTo(45.95f, 54.37f)
    lineTo(31.58f, 33.19f)
    close()
}

fun createLogoNotePath(): Path = Path().apply {
    moveTo(66.32f, 40.09f)
    lineTo(68.77f, 40.09f)
    cubicTo(69.13f, 41.41f, 69.48f, 42.73f, 70.36f, 43.82f)
    cubicTo(70.95f, 44.56f, 71.59f, 45.24f, 72.28f, 45.89f)
    cubicTo(74.09f, 47.60f, 76.36f, 49.28f, 76.82f, 51.91f)
    cubicTo(76.99f, 52.88f, 76.99f, 53.76f, 76.87f, 54.73f)
    cubicTo(76.77f, 55.49f, 76.59f, 56.15f, 76.27f, 56.85f)
    cubicTo(75.87f, 57.73f, 75.35f, 58.46f, 74.66f, 59.14f)
    lineTo(74.66f, 58.88f)
    cubicTo(75.17f, 57.54f, 75.28f, 55.90f, 75.10f, 54.48f)
    cubicTo(75.04f, 53.96f, 74.82f, 53.42f, 74.61f, 52.95f)
    cubicTo(73.87f, 51.31f, 72.39f, 49.98f, 70.72f, 49.32f)
    lineTo(69.22f, 48.75f)
    lineTo(68.88f, 48.61f)
    lineTo(68.89f, 65.50f)
    lineTo(68.87f, 69.77f)
    lineTo(68.85f, 71.80f)
    lineTo(68.39f, 73.14f)
    cubicTo(67.02f, 75.99f, 64.06f, 77.97f, 60.95f, 78.42f)
    cubicTo(59.10f, 78.69f, 57.07f, 78.42f, 55.65f, 77.07f)
    cubicTo(54.61f, 76.08f, 54.23f, 74.54f, 54.60f, 73.17f)
    lineTo(54.80f, 72.38f)
    cubicTo(55.15f, 71.64f, 55.54f, 70.98f, 56.05f, 70.32f)
    cubicTo(57.71f, 68.20f, 60.42f, 66.98f, 63.05f, 66.67f)
    cubicTo(64.16f, 66.54f, 65.26f, 66.89f, 66.32f, 67.12f)
    lineTo(66.32f, 40.09f)
    close()
}

fun createLogoWavePath(): Path = Path().apply {
    moveTo(74.94f, 43.31f)
    cubicTo(74.97f, 42.01f, 74.98f, 40.66f, 74.49f, 39.44f)
    cubicTo(74.12f, 38.50f, 73.58f, 37.62f, 72.90f, 36.88f)
    cubicTo(71.75f, 35.63f, 70.08f, 34.59f, 68.40f, 34.28f)
    cubicTo(66.85f, 33.99f, 65.31f, 34.02f, 63.76f, 34.27f)
    lineTo(62.98f, 34.51f)
    cubicTo(62.33f, 34.71f, 61.70f, 34.91f, 61.07f, 35.15f)
    lineTo(59.65f, 35.93f)
    cubicTo(58.37f, 36.66f, 57.22f, 37.64f, 56.13f, 38.59f)
    lineTo(56.24f, 38.23f)
    cubicTo(57.03f, 37.04f, 57.76f, 35.85f, 58.72f, 34.77f)
    cubicTo(61.38f, 31.79f, 65.23f, 29.83f, 69.28f, 30.08f)
    lineTo(70.69f, 30.30f)
    cubicTo(74.01f, 30.84f, 77.41f, 33.23f, 77.73f, 36.81f)
    cubicTo(77.80f, 37.56f, 77.83f, 38.49f, 77.55f, 39.22f)
    cubicTo(77.32f, 39.84f, 77.10f, 40.48f, 76.75f, 41.07f)
    cubicTo(76.26f, 41.89f, 75.59f, 42.60f, 74.94f, 43.31f)
    close()
}
