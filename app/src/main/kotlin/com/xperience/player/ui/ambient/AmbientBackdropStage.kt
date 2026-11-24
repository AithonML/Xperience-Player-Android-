package com.xperience.player.ui.ambient

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.domain.model.visualizer.VisualizerDefinition
import com.xperience.player.domain.model.visualizer.VisualizerRenderMode
import com.xperience.player.service.MediaNetworkClients
import com.xperience.player.ui.theme.ArtworkColorExtractor
import com.xperience.player.ui.theme.AuroraColors
import com.xperience.player.ui.visualizer.GpuMusicVisualizer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private val AmbientBaseFallback = Color(0xFF0A0C12)

/**
 * Full-bleed widescreen atmospheric backdrop for Ambient Mode:
 * 1. Deep Palette-derived dark ambient base color (smoothly crossfaded on track change).
 * 2. Heavily blurred album artwork with slow breathing Ken Burns motion (draw-phase only).
 * 3. Flowing 4-color Palette Aurora mesh glow driven in `drawWithCache` (zero per-frame recomposition).
 * 4. Optional subtle GPU Music Visualizer layer.
 * 5. Cinema-grade radial & edge vignettes ensuring crisp foreground contrast across bright covers.
 */
@Composable
internal fun AmbientBackdropStage(
    track: TrackItem?,
    auroraColors: AuroraColors,
    accentColor: Color,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    activeVisualizer: VisualizerDefinition,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val sharedImageLoader = remember(appContext) {
        MediaNetworkClients.getImageLoader(appContext)
    }

    val resolvedArtworkUri = remember(track?.artworkUri, track?.artworkResId, appContext) {
        track?.artworkUri?.takeIf { it.isNotBlank() }
            ?: track?.artworkResId?.let { resId ->
                "android.resource://${appContext.packageName}/$resId"
            }
    }

    var extractedDarkBase by remember(resolvedArtworkUri) {
        mutableStateOf(
            ArtworkColorExtractor.getCachedArtistAmbientColors(resolvedArtworkUri)?.dark
                ?: lerp(AmbientBaseFallback, accentColor, 0.14f)
        )
    }

    var extractedAurora by remember(resolvedArtworkUri, auroraColors) {
        mutableStateOf(
            ArtworkColorExtractor.getCachedAuroraColors(resolvedArtworkUri) ?: auroraColors
        )
    }

    LaunchedEffect(resolvedArtworkUri) {
        if (!resolvedArtworkUri.isNullOrBlank()) {
            extractedDarkBase = ArtworkColorExtractor
                .extractArtistAmbientColors(appContext, resolvedArtworkUri)
                .dark
            extractedAurora = ArtworkColorExtractor
                .extractAuroraColors(appContext, resolvedArtworkUri)
        }
    }

    val colorAnimSpec = tween<Color>(durationMillis = 850)
    val animatedBaseColor by animateColorAsState(
        targetValue = extractedDarkBase,
        animationSpec = colorAnimSpec,
        label = "ambientBackdropBase"
    )
    val c1State = animateColorAsState(extractedAurora.color1, colorAnimSpec, label = "ambientMesh1")
    val c2State = animateColorAsState(extractedAurora.color2, colorAnimSpec, label = "ambientMesh2")
    val c3State = animateColorAsState(extractedAurora.color3, colorAnimSpec, label = "ambientMesh3")
    val c4State = animateColorAsState(extractedAurora.color4, colorAnimSpec, label = "ambientMesh4")

    val infiniteTransition = rememberInfiniteTransition(label = "ambientBackdropMotion")
    val twoPi = (2.0 * PI).toFloat()

    val driftPhase1 = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = twoPi,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ambientDrift1"
    )
    val driftPhase2 = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = twoPi,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 34_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ambientDrift2"
    )
    val breathePhase = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = twoPi,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ambientBreathe"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(animatedBaseColor)
    ) {
        // 1. Heavily blurred full-bleed artwork layer with gentle zero-recomposition Ken Burns drift
        if (!resolvedArtworkUri.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(appContext)
                    .data(resolvedArtworkUri)
                    .crossfade(500)
                    .build(),
                imageLoader = sharedImageLoader,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val b = sin(breathePhase.value)
                        val scale = 1.16f + 0.045f * b
                        scaleX = scale
                        scaleY = scale
                        translationX = sin(driftPhase1.value) * 18.dp.toPx()
                        translationY = cos(driftPhase2.value) * 12.dp.toPx()
                        alpha = 0.62f
                    }
                    .blur(54.dp)
            )
        }

        // 2. Palette-derived 4-color atmospheric gradient mesh (strictly draw-phase)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    onDrawBehind {
                        val w = size.width
                        val h = size.height
                        if (w <= 1f || h <= 1f) return@onDrawBehind

                        val maxDim = max(w, h)
                        val p1 = driftPhase1.value
                        val p2 = driftPhase2.value
                        val b = breathePhase.value

                        val sinP1 = sin(p1)
                        val cosP1 = cos(p1)
                        val sinP2 = sin(p2)
                        val cosP2 = cos(p2)
                        val sinB = sin(b)
                        val breathe = 1f + 0.06f * sinB
                        val motionIntensity = if (isPlaying) 1.0f else 0.72f

                        val c1 = c1State.value
                        val c2 = c2State.value
                        val c3 = c3State.value
                        val c4 = c4State.value

                        // Left-side warm/primary aura behind album artwork
                        drawAmbientOrb(
                            center = Offset(w * (0.24f + 0.08f * sinP1), h * (0.46f + 0.12f * cosP2)),
                            radius = maxDim * 0.62f * breathe,
                            color = c1,
                            peakAlpha = 0.34f * motionIntensity,
                            blendMode = BlendMode.Screen
                        )

                        // Right-side expansive glow framing lyrics / ambient clock
                        drawAmbientOrb(
                            center = Offset(w * (0.78f - 0.09f * cosP1), h * (0.38f + 0.14f * sinB)),
                            radius = maxDim * 0.58f * (1f - 0.04f * sinB),
                            color = c2,
                            peakAlpha = 0.28f * motionIntensity,
                            blendMode = BlendMode.Screen
                        )

                        // Sweeping diagonal atmospheric silk curtain
                        drawAmbientRibbon(
                            center = Offset(w * (0.50f + 0.12f * sinP2), h * (0.62f + 0.10f * cosP1)),
                            radius = maxDim * 0.48f,
                            scaleX = 1.80f,
                            scaleY = 0.55f,
                            rotationDeg = -12f + 5f * sinP2,
                            coreColor = lerp(c1, c3, 0.45f),
                            edgeColor = c3,
                            peakAlpha = 0.24f * motionIntensity
                        )

                        // Subtle bottom-left counter-glow
                        drawAmbientRibbon(
                            center = Offset(w * (0.34f - 0.10f * cosP2), h * (0.86f + 0.05f * sinP1)),
                            radius = maxDim * 0.44f,
                            scaleX = 1.65f,
                            scaleY = 0.48f,
                            rotationDeg = 10f + 4f * cosP1,
                            coreColor = c4,
                            edgeColor = lerp(c4, c2, 0.40f),
                            peakAlpha = 0.22f * motionIntensity
                        )
                    }
                }
        )

        // 3. Optional subtle GPU Music Visualizer layer
        AnimatedVisibility(
            visible = activeVisualizer.renderMode != VisualizerRenderMode.None && track != null,
            enter = fadeIn(tween(durationMillis = 420)),
            exit = fadeOut(tween(durationMillis = 320)),
            modifier = Modifier.fillMaxSize()
        ) {
            GpuMusicVisualizer(
                definition = activeVisualizer,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                isPlaying = isPlaying,
                currentTrack = track,
                accentColor = accentColor,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.68f }
            )
        }

        // 4. Vertical cinematic depth scrim preserving crisp readability of white typography
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to animatedBaseColor.copy(alpha = 0.46f),
                        0.28f to animatedBaseColor.copy(alpha = 0.26f),
                        0.68f to animatedBaseColor.copy(alpha = 0.42f),
                        1.00f to animatedBaseColor.copy(alpha = 0.78f)
                    )
                )
        )

        // 5. Horizontal theatre vignette gently anchoring left & right edges
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0.00f to Color.Black.copy(alpha = 0.42f),
                        0.20f to Color.Transparent,
                        0.82f to Color.Transparent,
                        1.00f to Color.Black.copy(alpha = 0.44f)
                    )
                )
        )
    }
}

private fun DrawScope.drawAmbientOrb(
    center: Offset,
    radius: Float,
    color: Color,
    peakAlpha: Float,
    blendMode: BlendMode
) {
    if (radius <= 0f || peakAlpha <= 0.001f) return
    drawCircle(
        brush = Brush.radialGradient(
            0.00f to color.copy(alpha = peakAlpha),
            0.38f to color.copy(alpha = peakAlpha * 0.54f),
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

private fun DrawScope.drawAmbientRibbon(
    center: Offset,
    radius: Float,
    scaleX: Float,
    scaleY: Float,
    rotationDeg: Float,
    coreColor: Color,
    edgeColor: Color,
    peakAlpha: Float
) {
    if (radius <= 0f || peakAlpha <= 0.001f) return
    withTransform({
        translate(left = center.x, top = center.y)
        rotate(degrees = rotationDeg, pivot = Offset.Zero)
        scale(scaleX = scaleX, scaleY = scaleY, pivot = Offset.Zero)
    }) {
        drawCircle(
            brush = Brush.radialGradient(
                0.00f to coreColor.copy(alpha = peakAlpha),
                0.36f to edgeColor.copy(alpha = peakAlpha * 0.52f),
                0.70f to edgeColor.copy(alpha = peakAlpha * 0.14f),
                1.00f to Color.Transparent,
                center = Offset.Zero,
                radius = radius
            ),
            radius = radius,
            center = Offset.Zero,
            blendMode = BlendMode.Screen
        )
    }
}
