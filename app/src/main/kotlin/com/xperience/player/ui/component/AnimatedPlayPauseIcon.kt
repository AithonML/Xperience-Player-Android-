/**
 * Convx / Xperience Music Player
 * Licensed under GPL-3.0
 */
package com.xperience.player.ui.component

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AnimatedPlayPauseIcon(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    size: Dp = 28.dp,
) {
    val blurAnim = remember { Animatable(0f) }

    LaunchedEffect(isPlaying) {
        blurAnim.snapTo(10f)
        blurAnim.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        )
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = isPlaying,
            transitionSpec = {
                (fadeIn(tween(200)) togetherWith fadeOut(tween(150)))
                    .using(SizeTransform(clip = false))
            },
            contentKey = { it },
            label = "animatedPlayPause",
        ) { playing ->
            val icon = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow
            val blur = blurAnim.value
            val iconSize = if (playing) size * 0.92f else size

            Icon(
                imageVector = icon,
                contentDescription = if (playing) "Pause" else "Play",
                tint = tint,
                modifier = Modifier
                    .size(iconSize)
                    .graphicsLayer {
                        scaleX = 1f - blur * 0.008f
                        scaleY = 1f - blur * 0.008f
                        alpha = 1f - blur * 0.04f
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blur > 0.1f) {
                            renderEffect = android.graphics.RenderEffect
                                .createBlurEffect(blur, blur, android.graphics.Shader.TileMode.CLAMP)
                                .asComposeRenderEffect()
                        } else if (blur <= 0.1f) {
                            renderEffect = null
                        }
                    },
            )
        }
    }
}
