package com.xperience.player.ui.cast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.xperience.player.service.cast.CastConnectionState
import com.xperience.player.service.cast.CastPlaybackState
import com.xperience.player.ui.adaptive.AdaptivePreviewFixtures
import com.xperience.player.ui.adaptive.AdaptivePreviewSurface
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.theme.AppThemeColors
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.PlusJakartaSans
import com.xperience.player.ui.utils.pressWobble

private val MiniBannerShape = ContinuousRoundedRectangle(size = 22.dp)
private val MiniArtworkShape = ContinuousRoundedRectangle(size = 12.dp)

/**
 * Sleek floating Cast mini banner displaying active Chromecast status ("Transmitiendo en <DeviceName>"),
 * current track summary, subtle progress indicator, and quick play/pause control.
 *
 * Tapping the banner opens [CastExpandedControlsSheet].
 */
@Composable
fun CastMiniBanner(
    castState: CastPlaybackState,
    onOpenControls: () -> Unit,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = LocalAccentColor.current,
    appColors: AppThemeColors = LocalAppColors.current
) {
    AnimatedVisibility(
        visible = castState.isActiveSession,
        enter = fadeIn(tween(220)) + expandVertically(expandFrom = Alignment.Bottom),
        exit = fadeOut(tween(180)) + shrinkVertically(shrinkTowards = Alignment.Bottom),
        modifier = modifier
    ) {
        val context = LocalContext.current
        val isConnecting = castState.isConnectingOrReconnecting
        val deviceLabel = castState.deviceName?.takeIf { it.isNotBlank() } ?: "Chromecast"

        val pulseTransition = rememberInfiniteTransition(label = "castMiniPulse")
        val dotAlpha by pulseTransition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "castMiniDotAlpha"
        )

        val surfaceBrush = if (appColors.isDark) {
            Brush.horizontalGradient(
                colors = listOf(
                    Color(0xF0171B24),
                    Color(0xF01D2330)
                )
            )
        } else {
            Brush.horizontalGradient(
                colors = listOf(
                    Color(0xF5FFFFFF),
                    Color(0xF5F1F6FC)
                )
            )
        }

        val bannerInteraction = remember { MutableInteractionSource() }
        val playInteraction = remember { MutableInteractionSource() }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 14.dp,
                    shape = MiniBannerShape,
                    ambientColor = accentColor.copy(alpha = 0.22f),
                    spotColor = Color.Black.copy(alpha = if (appColors.isDark) 0.55f else 0.18f)
                )
                .clip(MiniBannerShape)
                .background(surfaceBrush)
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        listOf(
                            accentColor.copy(alpha = 0.65f),
                            accentColor.copy(alpha = 0.25f)
                        )
                    ),
                    shape = MiniBannerShape
                )
                .clickable(
                    interactionSource = bannerInteraction,
                    indication = ripple(bounded = true),
                    role = Role.Button,
                    onClick = onOpenControls
                )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Artwork thumbnail or Cast glyph
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(MiniArtworkShape)
                            .background(accentColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        val artworkUri = castState.currentTrack?.artworkUri
                        if (!artworkUri.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(artworkUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = castState.currentTrack.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize()
                            )
                        } else {
                            Icon(
                                imageVector = if (castState.currentTrack != null) {
                                    Icons.Rounded.MusicNote
                                } else {
                                    Icons.Rounded.CastConnected
                                },
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Status + Track info
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CastConnected,
                                contentDescription = null,
                                tint = accentColor.copy(alpha = if (isConnecting) dotAlpha else 1f),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = when (castState.connectionState) {
                                    CastConnectionState.CONNECTING -> "Conectando con $deviceLabel…"
                                    CastConnectionState.RECONNECTING -> "Reconectando con $deviceLabel…"
                                    else -> "Transmitiendo en $deviceLabel"
                                },
                                fontFamily = GoogleSans,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = accentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        val trackSubtitle = castState.currentTrack?.let { track ->
                            if (track.artist.isNotBlank()) {
                                "${track.title} • ${track.artist}"
                            } else {
                                track.title
                            }
                        } ?: "Toca para abrir controles de Chromecast"

                        Text(
                            text = trackSubtitle,
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = appColors.textPrimary.copy(alpha = 0.88f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Quick Play/Pause or Buffering indicator
                    Box(
                        modifier = Modifier
                            .pressWobble(playInteraction)
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = if (castState.isPlaying) 0.22f else 0.14f))
                            .border(1.dp, accentColor.copy(alpha = 0.50f), CircleShape)
                            .clickable(
                                interactionSource = playInteraction,
                                enabled = castState.isConnected && !castState.isBuffering,
                                indication = ripple(bounded = false, radius = 19.dp),
                                role = Role.Button,
                                onClick = onTogglePlayPause
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (castState.isBuffering || isConnecting) {
                            CircularProgressIndicator(
                                color = accentColor,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                imageVector = if (castState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (castState.isPlaying) "Pausar en Chromecast" else "Reproducir en Chromecast",
                                tint = accentColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // Sleek thin progress rail along the bottom edge
                if (castState.durationMs > 0L) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .background(appColors.dividerColor)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(castState.progressFraction.coerceIn(0f, 1f))
                                .height(2.5.dp)
                                .background(accentColor)
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "CastMiniBanner Playing - Dark", showBackground = true, backgroundColor = 0xFF0F1012)
@Composable
private fun CastMiniBannerDarkPreview() {
    AdaptivePreviewSurface(widthDp = 380, heightDp = 140, darkTheme = true) {
        Box(modifier = Modifier.padding(16.dp)) {
            CastMiniBanner(
                castState = CastPlaybackState(
                    connectionState = CastConnectionState.CONNECTED,
                    deviceName = "Sala Living TV",
                    currentTrack = AdaptivePreviewFixtures.tracks.first(),
                    isPlaying = true,
                    positionMs = 84_000L,
                    durationMs = 208_000L
                ),
                onOpenControls = {},
                onTogglePlayPause = {}
            )
        }
    }
}

@Preview(name = "CastMiniBanner Connecting - Light", showBackground = true, backgroundColor = 0xFFF6F8FB)
@Composable
private fun CastMiniBannerLightPreview() {
    AdaptivePreviewSurface(widthDp = 380, heightDp = 140, darkTheme = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            CastMiniBanner(
                castState = CastPlaybackState(
                    connectionState = CastConnectionState.CONNECTING,
                    deviceName = "Nest Audio Estudio",
                    currentTrack = AdaptivePreviewFixtures.tracks[1],
                    isBuffering = true
                ),
                onOpenControls = {},
                onTogglePlayPause = {}
            )
        }
    }
}
