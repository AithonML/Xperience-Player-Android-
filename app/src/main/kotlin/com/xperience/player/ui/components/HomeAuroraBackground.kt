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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
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
 *
 * When [isVisible] is false (e.g. player expanded, ambient mode, or overlay open), infinite
 * transitions are unmounted so zero animation frames are scheduled.
 */
@Composable
fun HomeAuroraBackground(
    auroraColors: AuroraColors,
    isDark: Boolean,
    scrollOffsetPxProvider: () -> Int = { 0 },
    isVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    val colorAnimSpec = tween<Color>(durationMillis = 900)
    val c1State = animateColorAsState(auroraColors.color1, colorAnimSpec, label = "auroraColor1")
    val c2State = animateColorAsState(auroraColors.color2, colorAnimSpec, label = "auroraColor2")
    val c3State = animateColorAsState(auroraColors.color3, colorAnimSpec, label = "auroraColor3")
    val c4State = animateColorAsState(auroraColors.color4, colorAnimSpec, label = "auroraColor4")

    val twoPi = (2.0 * PI).toFloat()
    val steadyZero = remember { mutableFloatStateOf(0f) }

    val phase1State: androidx.compose.runtime.State<Float>
    val phase2State: androidx.compose.runtime.State<Float>
    val breatheState: androidx.compose.runtime.State<Float>
    if (isVisible) {
        val infiniteTransition = rememberInfiniteTransition(label = "homeAuroraMotion")
        phase1State = infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = twoPi,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 16_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "auroraDrift1"
        )
        phase2State = infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = twoPi,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 23_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "auroraDrift2"
        )
        breatheState = infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = twoPi,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 11_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "auroraBreathe"
        )
    } else {
        phase1State = steadyZero
        phase2State = steadyZero
        breatheState = steadyZero
    }

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

                    val c1 = c1State.value
                    val c2 = c2State.value
                    val c3 = c3State.value
                    val c4 = c4State.value

                    val basePrimaryAlpha = if (isDark) 0.40f else 0.28f
                    val baseSecondaryAlpha = if (isDark) 0.35f else 0.24f
                    val baseCurtainAlpha = if (isDark) 0.30f else 0.21f
                    val baseMistAlpha = if (isDark) 0.24f else 0.17f

                    // Pre-created unit-radius radial gradients (zero per-frame Brush allocations)
                    val glow1Brush = unitGlowBrush(c1, basePrimaryAlpha)
                    val glow2Brush = unitGlowBrush(c2, baseSecondaryAlpha)
                    val ribbon1Brush = unitRibbonBrush(lerp(c1, c3, 0.45f), c3, baseCurtainAlpha)
                    val ribbon2Brush = unitRibbonBrush(lerp(c3, c2, 0.50f), c4, baseCurtainAlpha * 0.92f)
                    val veilBrush = unitRibbonBrush(c4, lerp(c4, c1, 0.35f), baseMistAlpha)
                    val shimmerBrush = unitGlowBrush(lerp(c1, c2, 0.5f), baseSecondaryAlpha * 0.55f)
                    val ribbonBlend = if (isDark) BlendMode.Screen else BlendMode.SrcOver

                    onDrawBehind {
                        val scrollPx = scrollOffsetPxProvider().coerceAtLeast(0).toFloat()
                        val scrollFade = (scrollPx / (size.height * 1.12f)).coerceIn(0f, 1f)
                        val intensity = 1f - scrollFade * 0.85f
                        if (intensity <= 0.005f) return@onDrawBehind

                        val w = size.width
                        val h = size.height
                        val maxDim = max(w, h)

                        val p1 = phase1State.value
                        val p2 = phase2State.value
                        val b = breatheState.value

                        val sinP1 = sin(p1)
                        val cosP1 = cos(p1)
                        val sinP2 = sin(p2)
                        val cosP2 = cos(p2)
                        val sinB = sin(b)
                        val breatheScale = 1f + 0.055f * sinB

                        val parallaxY = -scrollPx * 0.34f

                        translate(top = parallaxY) {
                            // 1. Primary Aurora Canopy Pool (upper-left drift)
                            drawCachedAuroraGlow(
                                brush = glow1Brush,
                                centerX = w * (0.20f + 0.11f * sinP1),
                                centerY = h * (0.09f + 0.03f * cosP2),
                                radius = maxDim * 0.72f * breatheScale,
                                alpha = intensity,
                                blendMode = BlendMode.SrcOver
                            )

                            // 2. Secondary Aurora Arch Pool (upper-right drift)
                            drawCachedAuroraGlow(
                                brush = glow2Brush,
                                centerX = w * (0.82f - 0.11f * cosP1),
                                centerY = h * (0.14f + 0.04f * sinB),
                                radius = maxDim * 0.68f * (1f - 0.03f * sinB),
                                alpha = intensity,
                                blendMode = ribbonBlend
                            )

                            // 3. Sweeping Borealis Curtain 1 (diagonal ribbon blending c1 -> c3)
                            drawCachedAuroraRibbon(
                                brush = ribbon1Brush,
                                centerX = w * (0.44f + 0.14f * sinP2),
                                centerY = h * (0.23f + 0.04f * cosP1),
                                radius = maxDim * 0.48f,
                                scaleX = 1.75f,
                                scaleY = 0.56f,
                                rotationDeg = -14f + 5f * sinP2,
                                alpha = intensity,
                                blendMode = ribbonBlend
                            )

                            // 4. Sweeping Borealis Curtain 2 (counter-arch ribbon c2 -> c4)
                            drawCachedAuroraRibbon(
                                brush = ribbon2Brush,
                                centerX = w * (0.58f - 0.15f * cosP2),
                                centerY = h * (0.35f + 0.045f * sinP1),
                                radius = maxDim * 0.46f,
                                scaleX = 1.85f,
                                scaleY = 0.52f,
                                rotationDeg = 13f + 4.5f * cosP1,
                                alpha = intensity,
                                blendMode = ribbonBlend
                            )

                            // 5. Lower Atmospheric Veil extending gracefully toward Recently Played
                            drawCachedAuroraRibbon(
                                brush = veilBrush,
                                centerX = w * (0.48f + 0.12f * cosP1),
                                centerY = h * (0.48f + 0.035f * sinP2),
                                radius = maxDim * 0.50f,
                                scaleX = 1.60f,
                                scaleY = 0.64f,
                                rotationDeg = -5f + 3f * sinB,
                                alpha = intensity,
                                blendMode = ribbonBlend
                            )

                            // 6. Subtle top-crest iridescent shimmer behind HeaderBar
                            drawCachedAuroraGlow(
                                brush = shimmerBrush,
                                centerX = w * (0.52f + 0.16f * sinP1),
                                centerY = h * 0.03f,
                                radius = maxDim * 0.46f * breatheScale,
                                alpha = intensity,
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

private fun unitGlowBrush(color: Color, peakAlpha: Float): Brush =
    Brush.radialGradient(
        0.00f to color.copy(alpha = peakAlpha),
        0.38f to color.copy(alpha = peakAlpha * 0.56f),
        0.72f to color.copy(alpha = peakAlpha * 0.16f),
        1.00f to Color.Transparent,
        center = Offset.Zero,
        radius = 1f
    )

private fun unitRibbonBrush(coreColor: Color, edgeColor: Color, peakAlpha: Float): Brush =
    Brush.radialGradient(
        0.00f to coreColor.copy(alpha = peakAlpha),
        0.35f to edgeColor.copy(alpha = peakAlpha * 0.58f),
        0.70f to edgeColor.copy(alpha = peakAlpha * 0.16f),
        1.00f to Color.Transparent,
        center = Offset.Zero,
        radius = 1f
    )

private fun DrawScope.drawCachedAuroraGlow(
    brush: Brush,
    centerX: Float,
    centerY: Float,
    radius: Float,
    alpha: Float,
    blendMode: BlendMode
) {
    if (radius <= 0f || alpha <= 0f) return
    withTransform({
        translate(left = centerX, top = centerY)
        scale(scaleX = radius, scaleY = radius, pivot = Offset.Zero)
    }) {
        drawCircle(
            brush = brush,
            radius = 1f,
            center = Offset.Zero,
            alpha = alpha,
            blendMode = blendMode
        )
    }
}

private fun DrawScope.drawCachedAuroraRibbon(
    brush: Brush,
    centerX: Float,
    centerY: Float,
    radius: Float,
    scaleX: Float,
    scaleY: Float,
    rotationDeg: Float,
    alpha: Float,
    blendMode: BlendMode
) {
    if (radius <= 0f || alpha <= 0f) return
    withTransform({
        translate(left = centerX, top = centerY)
        rotate(degrees = rotationDeg, pivot = Offset.Zero)
        scale(scaleX = radius * scaleX, scaleY = radius * scaleY, pivot = Offset.Zero)
    }) {
        drawCircle(
            brush = brush,
            radius = 1f,
            center = Offset.Zero,
            alpha = alpha,
            blendMode = blendMode
        )
    }
}
