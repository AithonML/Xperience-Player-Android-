package com.xperience.player.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.xperience.player.data.VisualizerRepository
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.domain.model.canvas.CanvasArtworkResult
import com.xperience.player.domain.model.visualizer.VisualizerDefinition
import com.xperience.player.domain.model.visualizer.VisualizerRenderMode
import com.xperience.player.service.MediaNetworkClients
import com.xperience.player.ui.theme.ArtworkColorExtractor
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.visualizer.GpuMusicVisualizer

/**
 * Full-bleed Now Playing visual stage modeled after AlbumScreen's borderless editorial hero:
 * edge-to-edge album artwork + seamless looping Album Canvas + adaptive ambient dissolve gradients.
 *
 * When [isCanvasPaused] is true (e.g. synchronized lyrics are open), [CanvasArtworkPlayer] is
 * paused and unmounted to free the video decoder and GPU while leaving the static cover visible.
 */
@Composable
fun NowPlayingBackground(
    track: TrackItem,
    canvasArtwork: CanvasArtworkResult?,
    isPlaying: Boolean,
    isCanvasPaused: Boolean = false,
    selectedVisualizer: VisualizerDefinition = VisualizerRepository.DefaultPreset,
    positionMsProvider: () -> Long = { 0L },
    durationMs: Long = 0L,
    accentColor: Color = LocalAccentColor.current,
    isOverlayActive: Boolean = false,
    ambientColor: Color? = null,
    isAmbientBlurredStage: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val sharedImageLoader = remember(appContext) {
        MediaNetworkClients.getImageLoader(appContext)
    }
    val appColors = LocalAppColors.current
    val isDark = appColors.isDark
    val fallbackBg = appColors.background
    val resolvedArtworkUri = remember(track.artworkUri, track.artworkResId, appContext) {
        track.artworkUri?.takeIf { it.isNotBlank() }
            ?: track.artworkResId?.let { resId ->
                "android.resource://${appContext.packageName}/$resId"
            }
    }
    val artworkRequest = remember(resolvedArtworkUri, isAmbientBlurredStage, appContext) {
        resolvedArtworkUri?.let { uri ->
            ImageRequest.Builder(appContext)
                .data(uri)
                .crossfade(350)
                .apply {
                    if (isAmbientBlurredStage) {
                        size(256)
                    }
                }
                .build()
        }
    }

    var extractedAmbient by remember(resolvedArtworkUri, isDark) {
        mutableStateOf(
            ArtworkColorExtractor.getCachedArtistAmbientColors(resolvedArtworkUri)
                ?.colorFor(isDark)
                ?: lerp(fallbackBg, accentColor, if (isDark) 0.14f else 0.08f)
        )
    }

    LaunchedEffect(resolvedArtworkUri, isDark, ambientColor) {
        if (ambientColor == null && !resolvedArtworkUri.isNullOrBlank()) {
            extractedAmbient = ArtworkColorExtractor
                .extractArtistAmbientColors(appContext, resolvedArtworkUri)
                .colorFor(isDark)
        }
    }

    val targetBgColor = ambientColor ?: extractedAmbient
    val animatedBgColor by animateColorAsState(
        targetValue = targetBgColor,
        animationSpec = tween(durationMillis = 650),
        label = "nowPlayingAmbientBg"
    )

    val activeVideoUri = canvasArtwork?.videoUri?.takeIf { it.isNotBlank() }
    val showMotionCanvas = !isCanvasPaused && !isAmbientBlurredStage && activeVideoUri != null

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(animatedBgColor)
    ) {
        // Full-bleed upper editorial stage dissolving smoothly into the adaptive ambient canvas below
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(if (isAmbientBlurredStage) 1f else 0.74f)
                .align(Alignment.TopCenter)
        ) {
            if (artworkRequest == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = accentColor.copy(alpha = 0.25f),
                        modifier = Modifier.size(96.dp)
                    )
                }
            } else {
                AsyncImage(
                    model = artworkRequest,
                    imageLoader = sharedImageLoader,
                    contentDescription = track.album.ifBlank { track.title },
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    modifier = if (isAmbientBlurredStage) {
                        Modifier
                            .fillMaxSize()
                            .blur(40.dp)
                    } else {
                        Modifier.fillMaxSize()
                    }
                )
            }

            AnimatedVisibility(
                visible = showMotionCanvas,
                enter = fadeIn(animationSpec = tween(durationMillis = 320)),
                exit = fadeOut(animationSpec = tween(durationMillis = 220)),
                modifier = Modifier.fillMaxSize()
            ) {
                if (activeVideoUri != null) {
                    CanvasArtworkPlayer(
                        videoUri = activeVideoUri,
                        isPlaying = isPlaying && !isCanvasPaused,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Bottom-to-top seamless dissolve gradient fusing hero edge into adaptive ambient color
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.00f to animatedBgColor.copy(alpha = if (isDark) 0.18f else 0.08f),
                                0.16f to Color.Transparent,
                                0.44f to animatedBgColor.copy(alpha = if (isDark) 0.16f else 0.10f),
                                0.68f to animatedBgColor.copy(alpha = if (isDark) 0.56f else 0.48f),
                                0.86f to animatedBgColor.copy(alpha = if (isDark) 0.88f else 0.82f),
                                1.00f to animatedBgColor
                            )
                        )
                    )
            )

            // Soft horizontal reading vignette dissolving edges smoothly into adaptive ambient color
            val vignetteSideAlpha = if (isDark) 0.52f else 0.22f
            val vignetteMidAlpha = if (isDark) 0.18f else 0.07f
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0.00f to animatedBgColor.copy(alpha = vignetteSideAlpha),
                                0.22f to animatedBgColor.copy(alpha = vignetteMidAlpha),
                                0.78f to Color.Transparent,
                                1.00f to animatedBgColor.copy(alpha = vignetteSideAlpha)
                            )
                        )
                    )
            )
        }

        if (selectedVisualizer.renderMode != VisualizerRenderMode.None) {
            GpuMusicVisualizer(
                definition = selectedVisualizer,
                positionMsProvider = positionMsProvider,
                durationMs = durationMs,
                isPlaying = isPlaying,
                currentTrack = track,
                accentColor = accentColor,
                isVisible = !isOverlayActive,
                modifier = Modifier.fillMaxSize()
            )
        }

        val overlayScrimAlpha by animateFloatAsState(
            targetValue = if (isOverlayActive) (if (isDark) 0.58f else 0.44f) else 0f,
            animationSpec = tween(durationMillis = 280),
            label = "nowPlayingOverlayScrim"
        )
        if (overlayScrimAlpha > 0.001f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(animatedBgColor.copy(alpha = overlayScrimAlpha))
            )
        }
    }
}
