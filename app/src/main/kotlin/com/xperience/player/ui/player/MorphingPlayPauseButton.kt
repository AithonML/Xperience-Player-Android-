package com.xperience.player.ui.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xperience.player.ui.component.LocalGlassEffectConfig
import com.xperience.player.ui.component.liquidGlass
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.utils.pressWobble

/**
 * Morphing Play/Pause button with continuous vector path interpolation.
 * Morphs seamlessly between hollow stroked Play triangle (progress = 0)
 * and Pause double bars (progress = 1).
 * Aligned with 0 degrees rotation so the Play triangle points razor-straight to the right.
 */
@Composable
fun MorphingPlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 74.dp,
    iconSize: Dp = 32.dp,
    tint: Color = Color.White,
    enabled: Boolean = true
) {
    val glassConfig = LocalGlassEffectConfig.current
    val appColors = LocalAppColors.current
    val interactionSource = remember { MutableInteractionSource() }

    val morphProgress by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "playPauseMorph"
    )

    val bounceScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.05f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "playPauseBounce"
    )

    Box(
        modifier = modifier
            .pressWobble(interactionSource)
            .size(buttonSize)
            .graphicsLayer {
                scaleX = bounceScale
                scaleY = bounceScale
            }
            .shadow(
                elevation = 8.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.25f),
                spotColor = Color.Black.copy(alpha = 0.35f)
            )
            .clip(CircleShape)
            .background(if (appColors.isDark) Color(0x38FFFFFF) else Color(0x60FFFFFF))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = if (appColors.isDark) 0.35f else 0.70f),
                shape = CircleShape
            )
            .liquidGlass(
                config = glassConfig,
                shape = CircleShape,
                highlightAlpha = 0.45f
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        MorphingPlayPauseIcon(
            progress = morphProgress,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Canvas that draws the morphing stroked vector paths between hollow Play and Pause.
 *
 * Geometry (24x24 reference box):
 * Play (t = 0):
 *   Stroke 1: (19.5, 12.0) -> (7.5, 5.0)  -> (7.5, 12.0)
 *   Stroke 2: (19.5, 12.0) -> (7.5, 19.0) -> (7.5, 12.0)
 *   Together they form a hollow stroked triangle ▷ with vertices at (7.5, 5.0), (19.5, 12.0), (7.5, 19.0).
 *
 * Pause (t = 1):
 *   Stroke 1: (8.0, 5.0)  -> (8.0, 12.0)  -> (8.0, 19.0)  [Left bar]
 *   Stroke 2: (16.0, 5.0) -> (16.0, 12.0) -> (16.0, 19.0) [Right bar]
 */
@Composable
fun MorphingPlayPauseIcon(
    progress: Float,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sx = w / 24f
        val sy = h / 24f

        val t = progress.coerceIn(0f, 1f)
        fun lerp(a: Float, b: Float): Float = a + (b - a) * t

        // --- STROKE 1 (Top slope in Play -> Left vertical bar in Pause) ---
        val p1x = lerp(19.5f, 8.0f) * sx
        val p1y = lerp(12.0f, 5.0f) * sy

        val p2x = lerp(7.5f, 8.0f) * sx
        val p2y = lerp(5.0f, 12.0f) * sy

        val p3x = lerp(7.5f, 8.0f) * sx
        val p3y = lerp(12.0f, 19.0f) * sy

        val path1 = Path().apply {
            moveTo(p1x, p1y)
            lineTo(p2x, p2y)
            lineTo(p3x, p3y)
        }

        // --- STROKE 2 (Bottom slope in Play -> Right vertical bar in Pause) ---
        val p4x = lerp(19.5f, 16.0f) * sx
        val p4y = lerp(12.0f, 5.0f) * sy

        val p5x = lerp(7.5f, 16.0f) * sx
        val p5y = lerp(19.0f, 12.0f) * sy

        val p6x = lerp(7.5f, 16.0f) * sx
        val p6y = lerp(12.0f, 19.0f) * sy

        val path2 = Path().apply {
            moveTo(p4x, p4y)
            lineTo(p5x, p5y)
            lineTo(p6x, p6y)
        }

        val strokeWidth = lerp(2.2.dp.toPx(), 3.2.dp.toPx())
        val stroke = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        drawPath(path1, tint, style = stroke)
        drawPath(path2, tint, style = stroke)
    }
}

/**
 * Sleek stroked Previous icon (|◁) matching the hollow outline design language.
 */
@Composable
fun StrokedSkipPreviousIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sx = w / 24f
        val sy = h / 24f

        val stroke = Stroke(
            width = 2.2.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        // Vertical bar on the left
        val barPath = Path().apply {
            moveTo(6.0f * sx, 5.5f * sy)
            lineTo(6.0f * sx, 18.5f * sy)
        }
        drawPath(barPath, tint, style = stroke)

        // Left-pointing triangle
        val trianglePath = Path().apply {
            moveTo(8.0f * sx, 12.0f * sy)
            lineTo(18.0f * sx, 5.5f * sy)
            lineTo(18.0f * sx, 18.5f * sy)
            close()
        }
        drawPath(trianglePath, tint, style = stroke)
    }
}

/**
 * Sleek stroked Next icon (▷|) matching the hollow outline design language.
 */
@Composable
fun StrokedSkipNextIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sx = w / 24f
        val sy = h / 24f

        val stroke = Stroke(
            width = 2.2.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        // Right-pointing triangle
        val trianglePath = Path().apply {
            moveTo(6.0f * sx, 5.5f * sy)
            lineTo(16.0f * sx, 12.0f * sy)
            lineTo(6.0f * sx, 18.5f * sy)
            close()
        }
        drawPath(trianglePath, tint, style = stroke)

        // Vertical bar on the right
        val barPath = Path().apply {
            moveTo(18.0f * sx, 5.5f * sy)
            lineTo(18.0f * sx, 18.5f * sy)
        }
        drawPath(barPath, tint, style = stroke)
    }
}
