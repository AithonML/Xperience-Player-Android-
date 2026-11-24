package com.xperience.player.ui.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xperience.player.ui.component.GlassComponent
import com.xperience.player.ui.component.LocalGlassEffectConfig
import com.xperience.player.ui.component.isGlassAllowed
import com.xperience.player.ui.component.liquidGlass
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.theme.AppThemeColors
import com.xperience.player.ui.theme.GoogleSans
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private val SquigglyCapsuleShape = ContinuousRoundedRectangle(percent = 50)
private const val TWO_PI = (2.0 * PI).toFloat()

/**
 * Native Android 13+ / Media3 style animated Squiggly waveform progress bar
 * housed inside a taller LiquidGlass capsule for rich optical refraction.
 *
 * - Animates the sinusoidal wave phase smoothly at display refresh rate while [isPlaying] is true.
 * - Smoothly flattens into a straight line when paused or actively scrubbing.
 * - Features a glowing interactive thumb and elapsed / total duration timestamps.
 */
@Composable
fun SquigglyProgressBar(
    currentPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    accentColor: Color,
    appColors: AppThemeColors,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeDurationMs = durationMs.coerceAtLeast(0L)
    val safePositionMs = currentPositionMs.coerceAtLeast(0L)
    val playbackFraction = if (safeDurationMs > 0L) {
        (safePositionMs.toFloat() / safeDurationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(playbackFraction) }
    var pendingSeekFraction by remember { mutableStateOf<Float?>(null) }

    // Clear the optimistic seek anchor once playback catches up or after a short grace period.
    LaunchedEffect(playbackFraction, pendingSeekFraction) {
        val pending = pendingSeekFraction ?: return@LaunchedEffect
        if (abs(playbackFraction - pending) < 0.025f) {
            pendingSeekFraction = null
        } else {
            delay(400L)
            pendingSeekFraction = null
        }
    }

    val displayFraction = when {
        isDragging -> dragFraction
        pendingSeekFraction != null -> pendingSeekFraction!!
        else -> playbackFraction
    }.coerceIn(0f, 1f)

    val displayedPositionMs = when {
        isDragging -> (dragFraction * safeDurationMs).toLong()
        pendingSeekFraction != null -> (pendingSeekFraction!! * safeDurationMs).toLong()
        else -> safePositionMs
    }

    val shouldAnimateWave = isPlaying && !isDragging && safeDurationMs > 0L
    val waveAmplitudeFraction by animateFloatAsState(
        targetValue = if (shouldAnimateWave) 1f else 0f,
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "squigglyWaveAmplitude"
    )

    val thumbScale by animateFloatAsState(
        targetValue = if (isDragging) 1.32f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "squigglyThumbScale"
    )

    // Frame-clock driven phase offset, read exclusively in the Draw phase (zero recompositions).
    var wavePhase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(shouldAnimateWave) {
        if (shouldAnimateWave) {
            var lastNanos = withFrameNanos { it }
            while (isActive) {
                withFrameNanos { frameNanos ->
                    val dtSeconds = ((frameNanos - lastNanos).coerceIn(0L, 100_000_000L)) / 1_000_000_000f
                    lastNanos = frameNanos
                    wavePhase = (wavePhase + dtSeconds * TWO_PI * 1.15f) % TWO_PI
                }
            }
        }
    }

    val glassConfig = LocalGlassEffectConfig.current
    val useGlass = glassConfig.isEnabledFor(GlassComponent.PLAYER) && isGlassAllowed()
    val density = LocalDensity.current
    val trackInsetPx = with(density) { 8.dp.toPx() }
    val wavePath = remember { Path() }

    val latestOnSeek by rememberUpdatedState(onSeek)
    val latestHasDuration by rememberUpdatedState(safeDurationMs > 0L)
    val latestTrackInsetPx by rememberUpdatedState(trackInsetPx)

    val capsuleModifier = if (useGlass) {
        Modifier.liquidGlass(
            config = glassConfig,
            shape = SquigglyCapsuleShape,
            highlightAlpha = 0.44f
        )
    } else {
        Modifier
            .background(if (appColors.isDark) Color(0x8816181F) else Color(0xCCFFFFFF))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = if (appColors.isDark) 0.25f else 0.70f),
                shape = SquigglyCapsuleShape
            )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(SquigglyCapsuleShape)
            .then(capsuleModifier)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Current position timestamp
            Text(
                text = formatTrackDuration(displayedPositionMs),
                fontFamily = GoogleSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = appColors.glassTextPrimary.copy(alpha = 0.85f),
                maxLines = 1,
                modifier = Modifier.widthIn(min = 38.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Interactive Squiggly Waveform Track
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            if (!latestHasDuration) return@detectTapGestures
                            val newFraction = xToTrackFraction(
                                x = offset.x,
                                widthPx = size.width.toFloat(),
                                insetPx = latestTrackInsetPx
                            )
                            pendingSeekFraction = newFraction
                            latestOnSeek(newFraction)
                        }
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                if (!latestHasDuration) return@detectHorizontalDragGestures
                                isDragging = true
                                dragFraction = xToTrackFraction(
                                    x = offset.x,
                                    widthPx = size.width.toFloat(),
                                    insetPx = latestTrackInsetPx
                                )
                            },
                            onDragEnd = {
                                if (!isDragging) return@detectHorizontalDragGestures
                                val target = dragFraction
                                isDragging = false
                                pendingSeekFraction = target
                                latestOnSeek(target)
                            },
                            onDragCancel = {
                                isDragging = false
                            },
                            onHorizontalDrag = { change, _ ->
                                if (!isDragging) return@detectHorizontalDragGestures
                                change.consume()
                                dragFraction = xToTrackFraction(
                                    x = change.position.x,
                                    widthPx = size.width.toFloat(),
                                    insetPx = latestTrackInsetPx
                                )
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                ) {
                    val trackY = size.height / 2f
                    val startX = trackInsetPx
                    val endX = (size.width - trackInsetPx).coerceAtLeast(startX + 1f)
                    val trackWidth = (endX - startX).coerceAtLeast(1f)
                    val activeX = startX + trackWidth * displayFraction

                    val strokeWidthPx = 3.5.dp.toPx()
                    val waveLengthPx = 26.dp.toPx()
                    val maxAmplitudePx = 4.0.dp.toPx()
                    val taperLengthPx = 14.dp.toPx()
                    val currentAmplitudePx = maxAmplitudePx * waveAmplitudeFraction

                    // 1. Inactive unplayed track segment
                    if (endX > activeX) {
                        drawLine(
                            color = if (appColors.isDark) {
                                Color.White.copy(alpha = 0.24f)
                            } else {
                                Color.Black.copy(alpha = 0.16f)
                            },
                            start = Offset(activeX, trackY),
                            end = Offset(endX, trackY),
                            strokeWidth = strokeWidthPx,
                            cap = StrokeCap.Round
                        )
                    }

                    // 2. Active squiggly waveform (or flattened line when paused / scrubbing)
                    if (activeX > startX + 0.5f) {
                        if (currentAmplitudePx < 0.08f) {
                            drawLine(
                                color = accentColor,
                                start = Offset(startX, trackY),
                                end = Offset(activeX, trackY),
                                strokeWidth = strokeWidthPx,
                                cap = StrokeCap.Round
                            )
                        } else {
                            wavePath.reset()
                            val stepPx = 1.5.dp.toPx().coerceAtLeast(2f)
                            wavePath.moveTo(startX, trackY)

                            var x = startX + stepPx
                            while (x < activeX) {
                                val startTaper = ((x - startX) / (taperLengthPx * 0.5f)).coerceIn(0f, 1f)
                                val endTaper = ((activeX - x) / taperLengthPx).coerceIn(0f, 1f)
                                val taper = smoothStep(minOf(startTaper, endTaper))
                                val angle = TWO_PI * ((x - startX) / waveLengthPx) - wavePhase
                                val y = trackY + currentAmplitudePx * taper * sin(angle)
                                wavePath.lineTo(x, y)
                                x += stepPx
                            }
                            wavePath.lineTo(activeX, trackY)

                            // Soft luminous under-glow
                            drawPath(
                                path = wavePath,
                                color = accentColor.copy(alpha = 0.26f * waveAmplitudeFraction),
                                style = Stroke(
                                    width = strokeWidthPx * 2.15f,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )

                            // Crisp primary squiggly wave
                            drawPath(
                                path = wavePath,
                                color = accentColor,
                                style = Stroke(
                                    width = strokeWidthPx,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }

                    // 3. Glowing interactive thumb
                    val thumbCenter = Offset(activeX, trackY)
                    val glowRadiusPx = 14.5.dp.toPx() * thumbScale
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = if (isDragging) 0.65f else 0.48f),
                                accentColor.copy(alpha = 0.16f),
                                Color.Transparent
                            ),
                            center = thumbCenter,
                            radius = glowRadiusPx
                        ),
                        radius = glowRadiusPx,
                        center = thumbCenter
                    )

                    drawCircle(
                        color = accentColor,
                        radius = 6.5.dp.toPx() * thumbScale,
                        center = thumbCenter
                    )

                    drawCircle(
                        color = Color.White,
                        radius = 2.6.dp.toPx() * thumbScale,
                        center = thumbCenter
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Total duration timestamp
            Text(
                text = formatTrackDuration(safeDurationMs),
                fontFamily = GoogleSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = appColors.glassTextPrimary.copy(alpha = 0.85f),
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.widthIn(min = 38.dp)
            )
        }
    }
}

internal fun xToTrackFraction(x: Float, widthPx: Float, insetPx: Float): Float {
    val usableWidth = (widthPx - insetPx * 2f).coerceAtLeast(1f)
    return ((x - insetPx) / usableWidth).coerceIn(0f, 1f)
}

internal fun smoothStep(t: Float): Float {
    val clamped = t.coerceIn(0f, 1f)
    return clamped * clamped * (3f - 2f * clamped)
}

internal fun formatTrackDuration(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
