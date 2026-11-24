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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * LiquidGlass capsule / pill component recreating the WinUI 3 Win2D LiquidGlass optics:
 * - 3D Fresnel Specular Rim (high specular reflection at top-left and subtle return bounce at bottom-right)
 * - Inner White Meniscus Glow (inset soft bevel highlight)
 * - Translucent Dark Crystal Luminance Stabilization
 * - Micro-interaction press feedback
 */
@Composable
fun LiquidGlassPill(
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    tintColor: Color = Color(0x701A1D24),
    specularAlpha: Float = 0.95f,
    strokeWidth: Dp = 1.2.dp,
    elevation: Dp = 12.dp,
    onClick: (() -> Unit)? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = tween(durationMillis = 120),
        label = "pillScale"
    )

    // Exact Win2D Fresnel Specular Rim (pratikone / archisvaze / Xperience LiquidGlass)
    val rimBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = specularAlpha * 0.92f), // Crisp top-left specular highlight
            Color(0x38B5D8FF),                             // Upper tangent dispersion
            Color(0x1588A0B8),                             // Mid tangent deep refraction
            Color(0x35B0D4FF),                             // Lower tangent dispersion
            Color.White.copy(alpha = specularAlpha * 0.58f)  // Bottom-right rim reflection
        ),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    // Inner Meniscus Bevel Gradient (soft white top inner glow)
    val innerMeniscusGlow = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.22f),
            Color.White.copy(alpha = 0.05f),
            Color.Transparent,
            Color.Black.copy(alpha = 0.25f)
        )
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.55f),
                spotColor = Color.Black.copy(alpha = 0.65f)
            )
            .clip(shape)
            .background(tintColor)
            .drawBehind {
                // Paint inner meniscus bevel glow
                drawRect(brush = innerMeniscusGlow, size = size)

                // Soft inner meniscus stroke
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.35f),
                            Color.Transparent
                        )
                    ),
                    style = Stroke(width = 2.5f)
                )
            }
            .border(
                width = strokeWidth,
                brush = rimBrush,
                shape = shape
            )
            .then(
                if (onClick != null) {
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
