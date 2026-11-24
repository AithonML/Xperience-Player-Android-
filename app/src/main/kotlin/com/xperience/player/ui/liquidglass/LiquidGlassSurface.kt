package com.xperience.player.ui.liquidglass

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Native LiquidGlass surface implementation for Android 13+ (API 33+).
 * Recreates the WinUI 3 Win2D LiquidGlass optical refraction, frosted diffusion,
 * 3D Fresnel specular outer rim, and inner meniscus bevel.
 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    cornerRadiusDp: Dp = 24.dp,
    frostBlurRadius: Float = 28f,
    tintColor: Color = Color(0x66181A22),
    specularIntensity: Float = 0.95f,
    strokeWidth: Dp = 1.2.dp,
    clickable: Boolean = false,
    onClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScaleState = animateFloatAsState(
        targetValue = if (isPressed) 0.965f else 1.0f,
        animationSpec = tween(durationMillis = 150),
        label = "glassPressScale"
    )

    val blurRenderEffect = remember(frostBlurRadius) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && frostBlurRadius > 0f) {
            RenderEffect
                .createBlurEffect(
                    frostBlurRadius,
                    frostBlurRadius,
                    Shader.TileMode.CLAMP
                )
                .asComposeRenderEffect()
        } else {
            null
        }
    }

    // Fresnel Specular Rim Gradient (High specular highlight at top-left, subtle reflection at bottom-right)
    val specularRimBrush = remember {
        Brush.linearGradient(
            colors = listOf(
                Color(0xCCFFFFFF), // Top-left crisp white reflection
                Color(0x33A0C8FF), // Upper tangent subtle refraction
                Color(0x1A80A0C0), // Mid tangent deep dissipation
                Color(0x24A0C8FF), // Lower tangent subtle bounce
                Color(0x88FFFFFF)  // Bottom-right rim return reflection
            ),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
    }

    // Inner Meniscus Bevel Gradient for organic 3D glass look
    val innerBevelBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color(0x28FFFFFF),
                Color(0x00FFFFFF),
                Color(0x18000000)
            )
        )
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                val s = pressScaleState.value
                scaleX = s
                scaleY = s
                if (blurRenderEffect != null) {
                    renderEffect = blurRenderEffect
                }
            }
            .clip(shape)
            .background(tintColor)
            .drawBehind {
                // Inner bevel highlight
                drawRect(
                    brush = innerBevelBrush,
                    size = size
                )
            }
            .border(
                width = strokeWidth,
                brush = specularRimBrush,
                shape = shape
            )
            .then(
                if (clickable && onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            ),
        contentAlignment = contentAlignment,
        content = content
    )
}
