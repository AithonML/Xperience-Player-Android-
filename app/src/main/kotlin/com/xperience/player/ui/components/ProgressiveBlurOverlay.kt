package com.xperience.player.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.xperience.player.service.MediaNetworkClients

/**
 * Reusable GPU-accelerated progressive backdrop-blur scrim for API 33+ (`minSdk = 33`).
 *
 * Renders a downsampled (96x96) copy of [artworkModel] scoped to only the bottom blur band
 * `(1f - blurStartFraction)` inside an offscreen graphics layer blurred with
 * [BlurredEdgeTreatment.Rectangle], masks it smoothly from 0f to 1f with [BlendMode.DstIn],
 * and layers a [tintColor] vertical gradient veil on top for crisp bottom typography.
 */
@Composable
fun ProgressiveArtworkBlurScrim(
    artworkModel: Any?,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 22.dp,
    tintColor: Color = Color.Black,
    blurStartFraction: Float = 0.42f,
    scrimTopAlpha: Float = 0.0f,
    scrimBottomAlpha: Float = 0.72f
) {
    val start = blurStartFraction.coerceIn(0.0f, 0.94f)
    val blurBandFraction = (1f - start).coerceIn(0.15f, 1f)

    val topAlpha = scrimTopAlpha.coerceIn(0f, 1f)
    val bottomAlpha = scrimBottomAlpha.coerceIn(0f, 1f)
    val tintStart = (start * 0.85f).coerceIn(0f, 0.90f)
    val tintMid = (tintStart + (1f - tintStart) * 0.52f).coerceIn(tintStart, 1f)
    val midAlpha = topAlpha + (bottomAlpha - topAlpha) * 0.50f

    val hasValidArtwork = when (artworkModel) {
        null -> false
        is String -> artworkModel.isNotBlank()
        is Int -> artworkModel != 0
        else -> true
    }
    val safeBlurRadius = blurRadius.coerceAtLeast(0.dp)
    val appContext = LocalContext.current.applicationContext
    val sharedImageLoader = remember(appContext) {
        MediaNetworkClients.getImageLoader(appContext)
    }

    Box(modifier = modifier) {
        if (hasValidArtwork && safeBlurRadius > 0.dp) {
            val downsampledRequest = remember(artworkModel, appContext) {
                if (artworkModel is Int) {
                    null
                } else {
                    ImageRequest.Builder(appContext)
                        .data(artworkModel)
                        .size(96, 96)
                        .build()
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(blurBandFraction)
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithCache {
                        val maskBrush = Brush.verticalGradient(
                            0.00f to Color.Transparent,
                            0.32f to Color.Black.copy(alpha = 0.24f),
                            0.68f to Color.Black.copy(alpha = 0.75f),
                            1.00f to Color.Black
                        )
                        onDrawWithContent {
                            drawContent()
                            drawRect(
                                brush = maskBrush,
                                blendMode = BlendMode.DstIn
                            )
                        }
                    }
            ) {
                val blurModifier = Modifier
                    .matchParentSize()
                    .blur(safeBlurRadius, BlurredEdgeTreatment.Rectangle)

                if (artworkModel is Int) {
                    Image(
                        painter = painterResource(id = artworkModel),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.BottomCenter,
                        modifier = blurModifier
                    )
                } else {
                    AsyncImage(
                        model = downsampledRequest,
                        imageLoader = sharedImageLoader,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.BottomCenter,
                        modifier = blurModifier
                    )
                }
            }
        }

        if (topAlpha > 0f || bottomAlpha > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawWithCache {
                        val tintBrush = Brush.verticalGradient(
                            0f to tintColor.copy(alpha = topAlpha),
                            tintStart to tintColor.copy(alpha = topAlpha),
                            tintMid to tintColor.copy(alpha = midAlpha),
                            1f to tintColor.copy(alpha = bottomAlpha)
                        )
                        onDrawBehind {
                            drawRect(brush = tintBrush)
                        }
                    }
            )
        }
    }
}
