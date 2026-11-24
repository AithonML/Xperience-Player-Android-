/**
 * Convx / Xperience Music Player
 * Licensed under GPL-3.0
 */
package com.xperience.player.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.floor
import kotlin.random.Random

/**
 * Deterministic pseudo-waveform: [count] bar heights in 0..1, stable for a given
 * [seed] (e.g. a song's mediaId hash) so every play of the same song draws the
 * same shape.
 */
fun waveformBars(seed: Int, count: Int): FloatArray {
    val rnd = Random(seed)
    var prev = 0.5f
    return FloatArray(count) {
        prev = (prev + (rnd.nextFloat() - 0.5f) * 0.6f).coerceIn(0.12f, 1f)
        prev
    }
}

/**
 * A scrolling waveform that stays centered on the current playback position.
 * Shows only a fixed [visibleBars] window — bars scroll as the song plays.
 * Both edges fade out progressively via a horizontal alpha mask.
 * Bars are rounded pill shape.
 */
@Composable
fun ScrollingWaveformSeekBar(
    progress: () -> Float,
    onSeek: (Float) -> Unit,
    playedColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    totalBars: Int = 80,
    visibleBars: Int = 18,
    seed: Int = 0,
    edgeFade: Float = 0.22f,
) {
    val heights = remember(seed, totalBars) { waveformBars(seed, totalBars) }
    var dragOffset by remember { mutableStateOf<Float?>(null) }

    Canvas(
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent,
                        edgeFade to Color.Black,
                        1f - edgeFade to Color.Black,
                        1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    val frac = (o.x / size.width).coerceIn(0f, 1f)
                    val shift = (frac - 0.5f) * visibleBars / totalBars
                    onSeek(((dragOffset ?: progress()) + shift).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        val frac = (o.x / size.width).coerceIn(0f, 1f)
                        dragOffset = ((dragOffset ?: progress()) + (frac - 0.5f) * visibleBars / totalBars).coerceIn(0f, 1f)
                    },
                    onDragEnd = { dragOffset?.let(onSeek); dragOffset = null },
                    onDragCancel = { dragOffset = null },
                ) { change, _ ->
                    val delta = -change.previousPosition.x + change.position.x
                    val shiftPerPx = visibleBars.toFloat() / totalBars / size.width
                    dragOffset = ((dragOffset ?: progress()) + delta * shiftPerPx).coerceIn(0f, 1f)
                }
            }
    ) {
        val n = heights.size
        if (n == 0 || size.width <= 0f || size.height <= 0f) return@Canvas

        val currentProgress = (dragOffset ?: progress()).coerceIn(0f, 1f)
        val midY = size.height / 2f
        val minH = size.height * 0.15f

        val slot = size.width / visibleBars
        val barWidth = slot * 0.55f
        val centerBar = currentProgress * (n - 1)
        val firstBar = centerBar - visibleBars / 2f
        val startIndex = floor(firstBar).toInt()

        for (i in startIndex..(startIndex + visibleBars + 1)) {
            if (i < 0 || i >= n) continue

            val x = (i - firstBar) * slot + slot / 2f
            if (x < -slot || x > size.width + slot) continue

            val h = (heights[i] * size.height * 0.9f).coerceAtLeast(minH)
            val color = if (i <= centerBar) playedColor else trackColor

            drawRoundRect(
                color = color,
                topLeft = Offset(x - barWidth / 2f, midY - h / 2f),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}
