package com.xperience.player.ui.splash

import android.graphics.Matrix
import android.graphics.RuntimeShader
import android.graphics.SweepGradient
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.xperience.player.ui.component.isLowRamDevice
import org.intellij.lang.annotations.Language
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

object SplashPalette {
    val DarkBackground = Color(0xFF0A0B10)
    val LightBackground = Color(0xFFF8F9FA)

    val DarkLogoTint = Color(0xFFFFFFFF)
    val LightLogoTint = Color(0xFF0E111A)

    // Electric blues & purples/violets for Aurora border glow
    val ElectricCyan = Color(0xFF00D4FF)
    val ElectricBlue = Color(0xFF2563EB)
    val CobaltBlue = Color(0xFF3B82F6)
    val DeepIndigo = Color(0xFF4F46E5)
    val ElectricViolet = Color(0xFF8B5CF6)
    val NeonPurple = Color(0xFFA855F7)

    // Soft translucent equivalents for Light Mode legibility & elegance
    val LightSkyElectric = Color(0xFF38BDF8)
    val LightRoyalBlue = Color(0xFF3B82F6)
    val LightIndigo = Color(0xFF6366F1)
    val LightViolet = Color(0xFF8B5CF6)
    val LightOrchid = Color(0xFFC084FC)
}

@Language("AGSL")
private const val AURORA_BORDER_AGSL = """
    uniform float2 uResolution;
    uniform float uTime;
    uniform float uIntensity;
    uniform float uMaxAlpha;
    layout(color) uniform half4 uColor1;
    layout(color) uniform half4 uColor2;
    layout(color) uniform half4 uColor3;
    layout(color) uniform half4 uColor4;

    half4 sampleCyclicPalette(float t) {
        float x = fract(t) * 4.0;
        if (x < 1.0) return mix(uColor1, uColor2, half(x));
        if (x < 2.0) return mix(uColor2, uColor3, half(x - 1.0));
        if (x < 3.0) return mix(uColor3, uColor4, half(x - 2.0));
        return mix(uColor4, uColor1, half(x - 3.0));
    }

    half4 main(float2 fragCoord) {
        float2 res = max(uResolution, float2(1.0, 1.0));
        float2 p = fragCoord - 0.5 * res;
        float2 halfSize = 0.5 * res;
        float minDim = min(res.x, res.y);
        float cornerR = minDim * 0.085;
        float2 q = abs(p) - (halfSize - float2(cornerR, cornerR));
        float distFromEdge = -(length(max(q, float2(0.0, 0.0))) + min(max(q.x, q.y), 0.0) - cornerR);
        distFromEdge = max(distFromEdge, 0.0);

        float ang = atan(p.y, p.x);
        float wave1 = sin(ang * 3.0 + uTime * 1.45) * cos(ang * 2.0 - uTime * 0.90);
        float wave2 = sin(ang * 5.0 - uTime * 1.85 + 1.3);
        float bandWidth = minDim * 0.24;
        float modulatedDist = max(distFromEdge - (wave1 * 0.16 + wave2 * 0.07) * bandWidth, 0.0);

        float bodyGlow = exp(-modulatedDist / max(bandWidth * 0.42, 1.0));
        float edgeRim = exp(-distFromEdge / max(bandWidth * 0.08, 1.0)) * 0.65;
        half alpha = half(clamp((bodyGlow * 0.72 + edgeRim * 0.36) * uMaxAlpha * uIntensity, 0.0, 1.0));

        float paletteT = (ang / 6.2831853) + uTime * 0.14 + wave1 * 0.07;
        half4 tint = sampleCyclicPalette(paletteT);
        return half4(tint.rgb * alpha, alpha);
    }
"""

/**
 * Dynamic electric-blue and violet Aurora illumination hugging the edges of the viewport while
 * preserving a clean, high-contrast center for the animated logo.
 *
 * Uses axis-aligned perimeter strokes with rotating `SweepGradient` shader matrices, drifting
 * radial pools, `Modifier.blur` (API 31+), and an API 33+ AGSL `RuntimeShader` curtain with
 * a pure `Canvas` fallback.
 */
@Composable
fun AuroraBorderEffect(
    isDark: Boolean,
    intensityState: State<Float>,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "splashAuroraBorder")
    val twoPi = (2.0 * PI).toFloat()

    val sweepRotation = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5_200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "auroraSweepRotation"
    )
    val counterRotation = infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8_400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "auroraCounterRotation"
    )
    val driftPhase = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = twoPi,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7_600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "auroraDriftPhase"
    )
    val shaderTime = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 62.83185f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 36_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "auroraShaderTime"
    )

    val isPreview = LocalInspectionMode.current
    val lowRam = isLowRamDevice()
    val useBlur = !isPreview && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !lowRam

    val runtimeShader = remember(isPreview, lowRam) {
        if (!isPreview && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !lowRam) {
            runCatching { RuntimeShader(AURORA_BORDER_AGSL) }.getOrNull()
        } else {
            null
        }
    }

    val palette = remember(isDark) {
        if (isDark) {
            listOf(
                SplashPalette.ElectricCyan,
                SplashPalette.ElectricBlue,
                SplashPalette.DeepIndigo,
                SplashPalette.ElectricViolet,
                SplashPalette.NeonPurple,
                SplashPalette.CobaltBlue,
                SplashPalette.ElectricCyan
            )
        } else {
            listOf(
                SplashPalette.LightSkyElectric,
                SplashPalette.LightRoyalBlue,
                SplashPalette.LightIndigo,
                SplashPalette.LightViolet,
                SplashPalette.LightOrchid,
                SplashPalette.LightRoyalBlue,
                SplashPalette.LightSkyElectric
            )
        }
    }
    val paletteArgbs = remember(palette) {
        IntArray(palette.size) { i -> palette[i].toArgb() }
    }
    val sweepMatrix = remember { Matrix() }

    val blurModifier = if (useBlur) {
        Modifier.blur(
            radius = if (isDark) 42.dp else 34.dp,
            edgeTreatment = BlurredEdgeTreatment.Rectangle
        )
    } else {
        Modifier
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Layer 1: Diffused axis-aligned perimeter sweep + drifting radial Aurora lobes
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(blurModifier)
        ) {
            val intensity = intensityState.value.coerceIn(0f, 1f)
            if (intensity <= 0.005f) return@Canvas

            val w = size.width
            val h = size.height
            val minDim = min(w, h)
            val phase = driftPhase.value
            val sweep = sweepRotation.value
            val counter = counterRotation.value
            val blendMode = if (isDark) BlendMode.Screen else BlendMode.SrcOver
            val baseEdgeAlpha = (if (isDark) 0.56f else 0.28f) * intensity
            val poolAlpha = (if (isDark) 0.40f else 0.20f) * intensity

            // Axis-aligned rounded border stroke with rotated sweep gradient shader
            val strokeThickness = minDim * (if (useBlur) 0.14f else 0.09f)
            val cornerPx = minDim * 0.07f
            val sweepBrush = createRotatedSweepBrush(
                colors = paletteArgbs,
                center = center,
                rotationDeg = sweep,
                matrix = sweepMatrix
            )
            drawRoundRect(
                brush = sweepBrush,
                topLeft = Offset.Zero,
                size = Size(w, h),
                cornerRadius = CornerRadius(cornerPx, cornerPx),
                style = Stroke(width = strokeThickness),
                alpha = baseEdgeAlpha,
                blendMode = blendMode
            )

            // Soft radial Aurora lobes drifting strictly along screen perimeter edges
            val poolRadius = minDim * 0.54f
            val sinP = sin(phase)
            val cosP = cos(phase)
            val counterRad = counter * (PI.toFloat() / 180f)

            drawEdgeAuroraPool(
                center = Offset(
                    x = w * (0.14f + 0.10f * sinP),
                    y = h * (0.02f + 0.04f * cos(counterRad))
                ),
                radius = poolRadius,
                color = palette[0],
                peakAlpha = poolAlpha,
                blendMode = blendMode
            )
            drawEdgeAuroraPool(
                center = Offset(
                    x = w * (0.86f - 0.10f * cosP),
                    y = h * (0.08f + 0.06f * sinP)
                ),
                radius = poolRadius * 0.94f,
                color = palette[3],
                peakAlpha = poolAlpha * 0.95f,
                blendMode = blendMode
            )
            drawEdgeAuroraPool(
                center = Offset(
                    x = w * (0.16f + 0.12f * cosP),
                    y = h * (0.98f - 0.04f * sin(counterRad))
                ),
                radius = poolRadius * 0.96f,
                color = palette[2],
                peakAlpha = poolAlpha * 0.90f,
                blendMode = blendMode
            )
            drawEdgeAuroraPool(
                center = Offset(
                    x = w * (0.84f - 0.11f * sinP),
                    y = h * (0.92f - 0.06f * cosP)
                ),
                radius = poolRadius,
                color = palette[4],
                peakAlpha = poolAlpha,
                blendMode = blendMode
            )
        }

        // Layer 2: API 33+ AGSL undulating Aurora ribbon around display edges + crisp edge rim
        Canvas(modifier = Modifier.fillMaxSize()) {
            val intensity = intensityState.value.coerceIn(0f, 1f)
            if (intensity <= 0.005f) return@Canvas

            val w = size.width
            val h = size.height
            val minDim = min(w, h)
            val blendMode = if (isDark) BlendMode.Screen else BlendMode.SrcOver

            if (runtimeShader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                runtimeShader.setFloatUniform("uResolution", w, h)
                runtimeShader.setFloatUniform("uTime", shaderTime.value)
                runtimeShader.setFloatUniform("uIntensity", intensity)
                runtimeShader.setFloatUniform("uMaxAlpha", if (isDark) 0.42f else 0.20f)
                runtimeShader.setColorUniform("uColor1", palette[0].toArgb())
                runtimeShader.setColorUniform("uColor2", palette[1].toArgb())
                runtimeShader.setColorUniform("uColor3", palette[3].toArgb())
                runtimeShader.setColorUniform("uColor4", palette[4].toArgb())

                drawRect(
                    brush = ShaderBrush(runtimeShader),
                    blendMode = blendMode
                )
            }

            // Crisp axis-aligned luminous bezel hairline hugging the viewport perimeter
            val rimWidth = 2.dp.toPx()
            val inset = rimWidth * 0.5f
            val cornerPx = minDim * 0.055f
            val rimBrush = createRotatedSweepBrush(
                colors = paletteArgbs,
                center = center,
                rotationDeg = sweepRotation.value,
                matrix = sweepMatrix
            )
            drawRoundRect(
                brush = rimBrush,
                topLeft = Offset(inset, inset),
                size = Size((w - rimWidth).coerceAtLeast(1f), (h - rimWidth).coerceAtLeast(1f)),
                cornerRadius = CornerRadius(cornerPx, cornerPx),
                style = Stroke(width = rimWidth),
                alpha = (if (isDark) 0.45f else 0.24f) * intensity,
                blendMode = blendMode
            )
        }
    }
}

private fun createRotatedSweepBrush(
    colors: IntArray,
    center: Offset,
    rotationDeg: Float,
    matrix: Matrix
): ShaderBrush {
    val shader = SweepGradient(center.x, center.y, colors, null)
    matrix.reset()
    matrix.setRotate(rotationDeg, center.x, center.y)
    shader.setLocalMatrix(matrix)
    return ShaderBrush(shader)
}

private fun DrawScope.drawEdgeAuroraPool(
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
            0.35f to color.copy(alpha = peakAlpha * 0.48f),
            0.70f to color.copy(alpha = peakAlpha * 0.12f),
            1.00f to Color.Transparent,
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center,
        blendMode = blendMode
    )
}
