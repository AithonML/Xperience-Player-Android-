package com.xperience.player.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.xperience.player.ui.theme.AuroraColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Ethereal 4-color Aurora Borealis ambient lighting wash across the upper portion of Home
 * (~640.dp tall, extending past `HeaderBar` and `TopPicks` down toward `Recently Played`).
 *
 * Uses draw-phase state reads inside `drawWithCache` + an offscreen `BlendMode.DstIn` bottom
 * feather mask so track changes crossfade smoothly, drift animation stays zero-recomposition,
 * scroll-linked parallax remains lightweight, and the bottom edge dissolves cleanly with zero cutoff.
 */
@Composable
fun HomeAuroraBackground(
    auroraColors: AuroraColors,
    isDark: Boolean,
    scrollOffsetPxProvider: () -> Int = { 0 },
    modifier: Modifier = Modifier
) {
    val colorAnimSpec = tween<Color>(durationMillis = 900)
    val c1State = animateColorAsState(auroraColors.color1, colorAnimSpec, label = "auroraColor1")
    val c2State = animateColorAsState(auroraColors.color2, colorAnimSpec, label = "auroraColor2")
    val c3State = animateColorAsState(auroraColors.color3, colorAnimSpec, label = "auroraColor3")
    val c4State = animateColorAsState(auroraColors.color4, colorAnimSpec, label = "auroraColor4")

    val infiniteTransition = rememberInfiniteTransition(label = "homeAuroraMotion")
    val twoPi = (2.0 * PI).toFloat()

    val phase1State = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = twoPi,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "auroraDrift1"
    )
    val phase2State = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = twoPi,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 23_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "auroraDrift2"
    )
    val breatheState = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = twoPi,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 11_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "auroraBreathe"
    )

    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(640.dp)
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithCache {
                    val bottomFeatherMask = Brush.verticalGradient(
                        0.00f to Color.Black,
                        0.46f to Color.Black,
                        0.68f to Color.Black.copy(alpha = 0.62f),
                        0.84f to Color.Black.copy(alpha = 0.18f),
                        0.94f to Color.Black.copy(alpha = 0.03f),
                        1.00f to Color.Transparent
                    )

                    onDrawBehind {
                        val scrollPx = scrollOffsetPxProvider().coerceAtLeast(0).toFloat()
                        val scrollFade = (scrollPx / (size.height * 1.12f)).coerceIn(0f, 1f)
                        val intensity = 1f - scrollFade * 0.85f
                        if (intensity <= 0.005f) return@onDrawBehind

                        val w = size.width
                        val h = size.height
                        val maxDim = max(w, h)

                        val c1 = c1State.value
                        val c2 = c2State.value
                        val c3 = c3State.value
                        val c4 = c4State.value

                        val p1 = phase1State.value
                        val p2 = phase2State.value
                        val b = breatheState.value

                        val sinP1 = sin(p1)
                        val cosP1 = cos(p1)
                        val sinP2 = sin(p2)
                        val cosP2 = cos(p2)
                        val sinB = sin(b)
                        val breatheScale = 1f + 0.055f * sinB

                        val ribbonBlend = if (isDark) BlendMode.Screen else BlendMode.SrcOver
                        val primaryAlpha = (if (isDark) 0.40f else 0.28f) * intensity
                        val secondaryAlpha = (if (isDark) 0.35f else 0.24f) * intensity
                        val curtainAlpha = (if (isDark) 0.30f else 0.21f) * intensity
                        val mistAlpha = (if (isDark) 0.24f else 0.17f) * intensity

                        val parallaxY = -scrollPx * 0.34f

                        translate(top = parallaxY) {
                            // 1. Primary Aurora Canopy Pool (upper-left drift)
                            drawAuroraGlow(
                                center = Offset(w * (0.20f + 0.11f * sinP1), h * (0.09f + 0.03f * cosP2)),
                                radius = maxDim * 0.72f * breatheScale,
                                color = c1,
                                peakAlpha = primaryAlpha,
                                blendMode = BlendMode.SrcOver
                            )

                            // 2. Secondary Aurora Arch Pool (upper-right drift)
                            drawAuroraGlow(
                                center = Offset(w * (0.82f - 0.11f * cosP1), h * (0.14f + 0.04f * sinB)),
                                radius = maxDim * 0.68f * (1f - 0.03f * sinB),
                                color = c2,
                                peakAlpha = secondaryAlpha,
                                blendMode = ribbonBlend
                            )

                            // 3. Sweeping Borealis Curtain 1 (diagonal ribbon blending c1 -> c3)
                            drawAuroraRibbon(
                                center = Offset(w * (0.44f + 0.14f * sinP2), h * (0.23f + 0.04f * cosP1)),
                                radius = maxDim * 0.48f,
                                scaleX = 1.75f,
                                scaleY = 0.56f,
                                rotationDeg = -14f + 5f * sinP2,
                                coreColor = lerp(c1, c3, 0.45f),
                                edgeColor = c3,
                                peakAlpha = curtainAlpha,
                                blendMode = ribbonBlend
                            )

                            // 4. Sweeping Borealis Curtain 2 (counter-arch ribbon c2 -> c4)
                            drawAuroraRibbon(
                                center = Offset(w * (0.58f - 0.15f * cosP2), h * (0.35f + 0.045f * sinP1)),
                                radius = maxDim * 0.46f,
                                scaleX = 1.85f,
                                scaleY = 0.52f,
                                rotationDeg = 13f + 4.5f * cosP1,
                                coreColor = lerp(c3, c2, 0.50f),
                                edgeColor = c4,
                                peakAlpha = curtainAlpha * 0.92f,
                                blendMode = ribbonBlend
                            )

                            // 5. Lower Atmospheric Veil extending gracefully toward Recently Played
                            drawAuroraRibbon(
                                center = Offset(w * (0.48f + 0.12f * cosP1), h * (0.48f + 0.035f * sinP2)),
                                radius = maxDim * 0.50f,
                                scaleX = 1.60f,
                                scaleY = 0.64f,
                                rotationDeg = -5f + 3f * sinB,
                                coreColor = c4,
                                edgeColor = lerp(c4, c1, 0.35f),
                                peakAlpha = mistAlpha,
                                blendMode = ribbonBlend
                            )

                            // 6. Subtle top-crest iridescent shimmer behind HeaderBar
                            drawAuroraGlow(
                                center = Offset(w * (0.52f + 0.16f * sinP1), h * 0.03f),
                                radius = maxDim * 0.46f * breatheScale,
                                color = lerp(c1, c2, 0.5f),
                                peakAlpha = secondaryAlpha * 0.55f,
                                blendMode = ribbonBlend
                            )
                        }

                        // Feather bottom edge inside the offscreen layer to exact transparency
                        drawRect(
                            brush = bottomFeatherMask,
                            blendMode = BlendMode.DstIn
                        )
                    }
                }
        )
    }
}

private fun DrawScope.drawAuroraGlow(
    center: Offset,
    radius: Float,
    color: Color,
    peakAlpha: Float,
    blendMode: BlendMode
) {
    if (radius <= 0f || peakAlpha <= 0f) return
    drawCircle(
        brush = Brush.radialGradient(
            0.00f to color.copy(alpha = peakAlpha),
            0.38f to color.copy(alpha = peakAlpha * 0.56f),
            0.72f to color.copy(alpha = peakAlpha * 0.16f),
            1.00f to Color.Transparent,
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center,
        blendMode = blendMode
    )
}

private fun DrawScope.drawAuroraRibbon(
    center: Offset,
    radius: Float,
    scaleX: Float,
    scaleY: Float,
    rotationDeg: Float,
    coreColor: Color,
    edgeColor: Color,
    peakAlpha: Float,
    blendMode: BlendMode
) {
    if (radius <= 0f || peakAlpha <= 0f) return
    withTransform({
        translate(left = center.x, top = center.y)
        rotate(degrees = rotationDeg, pivot = Offset.Zero)
        scale(scaleX = scaleX, scaleY = scaleY, pivot = Offset.Zero)
    }) {
        drawCircle(
            brush = Brush.radialGradient(
                0.00f to coreColor.copy(alpha = peakAlpha),
                0.35f to edgeColor.copy(alpha = peakAlpha * 0.58f),
                0.70f to edgeColor.copy(alpha = peakAlpha * 0.16f),
                1.00f to Color.Transparent,
                center = Offset.Zero,
                radius = radius
            ),
            radius = radius,
            center = Offset.Zero,
            blendMode = blendMode
        )
    }
}
