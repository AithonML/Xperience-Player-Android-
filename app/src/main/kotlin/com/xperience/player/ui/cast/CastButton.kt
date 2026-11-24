package com.xperience.player.ui.cast

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xperience.player.service.cast.CastConnectionState
import com.xperience.player.service.cast.CastPlaybackState
import com.xperience.player.ui.adaptive.AdaptivePreviewSurface
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.theme.AppThemeColors
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.utils.pressWobble

/**
 * Visual presentation styles for [CastButton] across Now Playing, Sidebar, Ambient Mode, and Home.
 */
enum class CastButtonStyle {
    /** Compact borderless icon button (fits inside TopRightCastMenuPill or toolbars). */
    ICON,

    /** Frosted circular pill button suitable for Ambient Mode or floating headers. */
    GLASS_CIRCLE,

    /** Expanded capsule chip showing connected device name when active. */
    CHIP
}

private val CastChipShape = ContinuousRoundedRectangle(percent = 50)

/**
 * Custom Material 3 Jetpack Compose Chromecast button replacing legacy AppCompat MediaRouteButton.
 *
 * Visual states:
 * - [CastConnectionState.DISCONNECTED]: clean idle Cast icon (with subtle availability dot if devices are found).
 * - [CastConnectionState.CONNECTING] / [CastConnectionState.RECONNECTING]: animated pulsing rings & wave glow.
 * - [CastConnectionState.CONNECTED]: glowing accent-tinted [Icons.Rounded.CastConnected] + active badge.
 * - [CastConnectionState.UNAVAILABLE]: hides cleanly if [hideWhenUnavailable] is `true`, otherwise renders a
 *   muted icon so tapping opens the picker with an informative message + local audio output switcher.
 */
@Composable
fun CastButton(
    castState: CastPlaybackState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    availableRouteCount: Int = 0,
    style: CastButtonStyle = CastButtonStyle.ICON,
    hideWhenUnavailable: Boolean = false,
    iconSize: Dp = 20.dp,
    tint: Color? = null,
    accentColor: Color = LocalAccentColor.current,
    appColors: AppThemeColors = LocalAppColors.current
) {
    if (hideWhenUnavailable && castState.connectionState == CastConnectionState.UNAVAILABLE) {
        return
    }

    val isConnected = castState.isConnected
    val isConnecting = castState.isConnectingOrReconnecting
    val isUnavailable = castState.connectionState == CastConnectionState.UNAVAILABLE

    val infiniteTransition = rememberInfiniteTransition(label = "castButtonAnim")
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "castPulsePhase"
    )
    val connectingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.42f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 680, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "castConnectingAlpha"
    )

    val idleTint = tint ?: appColors.glassTextPrimary
    val resolvedTint by animateColorAsState(
        targetValue = when {
            isConnected -> accentColor
            isConnecting -> accentColor
            isUnavailable -> idleTint.copy(alpha = 0.45f)
            else -> idleTint
        },
        animationSpec = tween(220),
        label = "castIconTint"
    )

    val accessibilityLabel = when (castState.connectionState) {
        CastConnectionState.CONNECTED ->
            "Transmitiendo en ${castState.deviceName ?: "Chromecast"}. Toca para abrir controles"
        CastConnectionState.CONNECTING ->
            "Conectando a ${castState.deviceName ?: "Chromecast"}"
        CastConnectionState.RECONNECTING ->
            "Reconectando con ${castState.deviceName ?: "Chromecast"}"
        CastConnectionState.UNAVAILABLE ->
            "Salida de audio y Chromecast no disponible"
        CastConnectionState.DISCONNECTED ->
            if (availableRouteCount > 0) {
                "Transmitir a Chromecast ($availableRouteCount dispositivos disponibles)"
            } else {
                "Transmitir a Chromecast o cambiar salida de audio"
            }
    }

    val interactionSource = remember { MutableInteractionSource() }

    when (style) {
        CastButtonStyle.CHIP -> {
            val containerBg = when {
                isConnected -> accentColor.copy(alpha = 0.18f)
                isConnecting -> accentColor.copy(alpha = 0.14f)
                appColors.isDark -> Color(0x661C202B)
                else -> Color(0xCCFFFFFF)
            }
            val borderColor = when {
                isConnected -> accentColor.copy(alpha = 0.65f)
                isConnecting -> accentColor.copy(alpha = 0.45f)
                else -> appColors.cardBorder
            }
            Row(
                modifier = modifier
                    .pressWobble(interactionSource)
                    .clip(CastChipShape)
                    .background(containerBg)
                    .border(1.dp, borderColor, CastChipShape)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = ripple(bounded = true),
                        role = Role.Button,
                        onClick = onClick
                    )
                    .semantics { contentDescription = accessibilityLabel }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CastIconCore(
                    isConnected = isConnected,
                    isConnecting = isConnecting,
                    showRouteDot = !isConnected && !isConnecting && !isUnavailable && availableRouteCount > 0,
                    pulsePhase = pulsePhase,
                    connectingAlpha = connectingAlpha,
                    tint = resolvedTint,
                    accentColor = accentColor,
                    iconSize = iconSize
                )
                val chipText = when {
                    isConnected -> castState.deviceName ?: "Chromecast"
                    isConnecting -> "Conectando…"
                    else -> "Transmitir"
                }
                Text(
                    text = chipText,
                    fontFamily = GoogleSans,
                    fontWeight = if (isConnected) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    color = if (isConnected || isConnecting) accentColor else appColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        CastButtonStyle.GLASS_CIRCLE -> {
            val circleBg = when {
                isConnected -> accentColor.copy(alpha = 0.22f)
                isConnecting -> accentColor.copy(alpha = 0.16f)
                appColors.isDark -> Color(0x70161922)
                else -> Color(0xD8FFFFFF)
            }
            val circleBorder = when {
                isConnected -> accentColor.copy(alpha = 0.70f)
                isConnecting -> accentColor.copy(alpha = 0.48f)
                else -> Color.White.copy(alpha = if (appColors.isDark) 0.20f else 0.65f)
            }
            Box(
                modifier = modifier
                    .pressWobble(interactionSource)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(circleBg)
                    .border(1.dp, circleBorder, CircleShape)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = ripple(bounded = false, radius = 22.dp),
                        role = Role.Button,
                        onClick = onClick
                    )
                    .semantics { contentDescription = accessibilityLabel },
                contentAlignment = Alignment.Center
            ) {
                CastIconCore(
                    isConnected = isConnected,
                    isConnecting = isConnecting,
                    showRouteDot = !isConnected && !isConnecting && !isUnavailable && availableRouteCount > 0,
                    pulsePhase = pulsePhase,
                    connectingAlpha = connectingAlpha,
                    tint = resolvedTint,
                    accentColor = accentColor,
                    iconSize = iconSize
                )
            }
        }

        CastButtonStyle.ICON -> {
            Box(
                modifier = modifier
                    .pressWobble(interactionSource)
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = ripple(bounded = false, radius = 20.dp),
                        role = Role.Button,
                        onClick = onClick
                    )
                    .semantics { contentDescription = accessibilityLabel },
                contentAlignment = Alignment.Center
            ) {
                CastIconCore(
                    isConnected = isConnected,
                    isConnecting = isConnecting,
                    showRouteDot = !isConnected && !isConnecting && !isUnavailable && availableRouteCount > 0,
                    pulsePhase = pulsePhase,
                    connectingAlpha = connectingAlpha,
                    tint = resolvedTint,
                    accentColor = accentColor,
                    iconSize = iconSize
                )
            }
        }
    }
}

@Composable
private fun CastIconCore(
    isConnected: Boolean,
    isConnecting: Boolean,
    showRouteDot: Boolean,
    pulsePhase: Float,
    connectingAlpha: Float,
    tint: Color,
    accentColor: Color,
    iconSize: Dp
) {
    Box(
        modifier = Modifier.size(iconSize + 14.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isConnected) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.34f),
                            accentColor.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
            }
        } else if (isConnecting) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val radius = size.minDimension * (0.28f + 0.24f * pulsePhase)
                drawCircle(
                    color = accentColor.copy(alpha = (1f - pulsePhase) * 0.55f),
                    radius = radius
                )
            }
        }

        Icon(
            imageVector = if (isConnected) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer {
                    alpha = if (isConnecting) connectingAlpha else 1f
                }
        )

        // Subtle live indicator dot for active session or discovered nearby Chromecast
        if (isConnected || showRouteDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 3.dp, end = 3.dp)
                    .size(if (isConnected) 7.dp else 6.dp)
                    .clip(CircleShape)
                    .background(if (isConnected) accentColor else accentColor.copy(alpha = 0.85f))
            )
        }
    }
}

@Preview(name = "CastButton States - Dark", showBackground = true, backgroundColor = 0xFF0F1012)
@Composable
private fun CastButtonStatesDarkPreview() {
    AdaptivePreviewSurface(widthDp = 360, heightDp = 160, darkTheme = true) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CastButton(
                castState = CastPlaybackState(connectionState = CastConnectionState.DISCONNECTED),
                availableRouteCount = 2,
                style = CastButtonStyle.GLASS_CIRCLE,
                onClick = {}
            )
            CastButton(
                castState = CastPlaybackState(
                    connectionState = CastConnectionState.CONNECTING,
                    deviceName = "Sala TV"
                ),
                style = CastButtonStyle.GLASS_CIRCLE,
                onClick = {}
            )
            CastButton(
                castState = CastPlaybackState(
                    connectionState = CastConnectionState.CONNECTED,
                    deviceName = "Sala TV"
                ),
                style = CastButtonStyle.CHIP,
                onClick = {}
            )
            CastButton(
                castState = CastPlaybackState(connectionState = CastConnectionState.UNAVAILABLE),
                style = CastButtonStyle.ICON,
                onClick = {}
            )
        }
    }
}

@Preview(name = "CastButton States - Light", showBackground = true, backgroundColor = 0xFFF6F8FB)
@Composable
private fun CastButtonStatesLightPreview() {
    AdaptivePreviewSurface(widthDp = 360, heightDp = 120, darkTheme = false) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CastButton(
                castState = CastPlaybackState(connectionState = CastConnectionState.DISCONNECTED),
                style = CastButtonStyle.GLASS_CIRCLE,
                onClick = {}
            )
            CastButton(
                castState = CastPlaybackState(
                    connectionState = CastConnectionState.CONNECTED,
                    deviceName = "Nest Audio"
                ),
                style = CastButtonStyle.CHIP,
                onClick = {}
            )
        }
    }
}
