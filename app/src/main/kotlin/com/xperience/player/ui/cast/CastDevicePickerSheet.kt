package com.xperience.player.ui.cast

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.SpeakerGroup
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xperience.player.service.cast.CastConnectionState
import com.xperience.player.service.cast.CastDeviceType
import com.xperience.player.service.cast.CastPlaybackState
import com.xperience.player.service.cast.CastRouteDevice
import com.xperience.player.ui.adaptive.AdaptiveModalContainer
import com.xperience.player.ui.adaptive.AdaptivePreviewFixtures
import com.xperience.player.ui.adaptive.AdaptivePreviewSurface
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.theme.AppThemeColors
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.PlusJakartaSans
import com.xperience.player.ui.utils.pressWobble
import kotlin.math.roundToInt

private val PickerCardShape = ContinuousRoundedRectangle(size = 20.dp)
private val PickerButtonShape = ContinuousRoundedRectangle(percent = 50)

/**
 * Custom Material 3 + Xperience bottom sheet (upgrading automatically to a centered modal dialog
 * on tablets/foldables via [AdaptiveModalContainer]) replacing the legacy AppCompat
 * MediaRouteChooserDialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastDevicePickerSheet(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    castState: CastPlaybackState,
    routes: List<CastRouteDevice>,
    isScanning: Boolean,
    onStartDiscovery: () -> Unit,
    onStopDiscovery: () -> Unit,
    onSelectRoute: (String) -> Unit,
    onDisconnect: () -> Unit,
    onSetVolume: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onOpenExpandedControls: () -> Unit,
    onClearError: () -> Unit = {},
    currentLocalOutputName: String = "Este teléfono",
    onOpenSystemAudioOutput: (() -> Unit)? = null,
    accentColor: Color = LocalAccentColor.current,
    appColors: AppThemeColors = LocalAppColors.current
) {
    if (isOpen) {
        DisposableEffect(Unit) {
            onStartDiscovery()
            onDispose { onStopDiscovery() }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetBg = if (appColors.isDark) Color(0xFF13161E) else Color(0xFFFDFEFF)

    AdaptiveModalContainer(
        isOpen = isOpen,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        dialogMaxWidth = 520.dp
    ) {
        CastDevicePickerSheetContent(
            castState = castState,
            routes = routes,
            isScanning = isScanning,
            onSelectRoute = { deviceId ->
                onSelectRoute(deviceId)
            },
            onDisconnect = {
                onDisconnect()
            },
            onSetVolume = onSetVolume,
            onToggleMute = onToggleMute,
            onOpenExpandedControls = {
                onDismiss()
                onOpenExpandedControls()
            },
            onClearError = onClearError,
            currentLocalOutputName = currentLocalOutputName,
            onOpenSystemAudioOutput = onOpenSystemAudioOutput?.let { callback ->
                {
                    onDismiss()
                    callback()
                }
            },
            onClose = onDismiss,
            accentColor = accentColor,
            appColors = appColors
        )
    }
}

@Composable
fun CastDevicePickerSheetContent(
    castState: CastPlaybackState,
    routes: List<CastRouteDevice>,
    isScanning: Boolean,
    onSelectRoute: (String) -> Unit,
    onDisconnect: () -> Unit,
    onSetVolume: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onOpenExpandedControls: () -> Unit,
    onClearError: () -> Unit = {},
    currentLocalOutputName: String = "Este teléfono",
    onOpenSystemAudioOutput: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    accentColor: Color = LocalAccentColor.current,
    appColors: AppThemeColors = LocalAppColors.current
) {
    val cardBg = if (appColors.isDark) Color(0xFF1B1F2B) else Color(0xFFF3F6FB)
    val cardBorder = if (appColors.isDark) Color(0x22FFFFFF) else Color(0x18000000)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.16f))
                        .border(1.dp, accentColor.copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (castState.isConnected) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Transmitir y salida de audio",
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = appColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when (castState.connectionState) {
                            CastConnectionState.CONNECTED ->
                                "Transmitiendo en ${castState.deviceName ?: "Chromecast"}"
                            CastConnectionState.CONNECTING ->
                                "Conectando con ${castState.deviceName ?: "Chromecast"}…"
                            CastConnectionState.RECONNECTING ->
                                "Reconectando sesión…"
                            CastConnectionState.UNAVAILABLE ->
                                "Chromecast no disponible • Audio local activo"
                            CastConnectionState.DISCONNECTED ->
                                if (routes.isNotEmpty()) {
                                    "${routes.size} dispositivo(s) Chromecast en tu Wi-Fi"
                                } else {
                                    "Selecciona un dispositivo en tu red Wi-Fi"
                                }
                        },
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = if (castState.isConnected) accentColor else appColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isScanning && castState.isAvailable) {
                Box(
                    modifier = Modifier
                        .clip(PickerButtonShape)
                        .background(accentColor.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CircularProgressIndicator(
                            color = accentColor,
                            strokeWidth = 1.8.dp,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Wi-Fi",
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = accentColor
                        )
                    }
                }
            } else if (onClose != null) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Cerrar",
                        tint = appColors.textSecondary
                    )
                }
            }
        }

        // Error Banner (if any)
        AnimatedVisibility(visible = !castState.errorMessage.isNullOrBlank()) {
            val errorText = castState.errorMessage.orEmpty()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clip(PickerCardShape)
                    .background(Color(0x26EF4444))
                    .border(1.dp, Color(0x66EF4444), PickerCardShape)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = Color(0xFFF87171),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = errorText,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = if (appColors.isDark) Color(0xFFFECACA) else Color(0xFF991B1B),
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onClearError,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Descartar error",
                        tint = Color(0xFFF87171),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Active Connected Device Control Card (Inline Volume + Expanded Controls + Return to Phone)
        if (castState.isActiveSession) {
            ActiveCastSessionCard(
                castState = castState,
                onSetVolume = onSetVolume,
                onToggleMute = onToggleMute,
                onOpenExpandedControls = onOpenExpandedControls,
                onDisconnect = onDisconnect,
                accentColor = accentColor,
                appColors = appColors
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Discovered Chromecast Devices Section
        Text(
            text = "DISPOSITIVOS CHROMECAST",
            fontFamily = GoogleSans,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 0.8.sp,
            color = appColors.textTertiary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        when {
            !castState.isAvailable -> {
                CastUnavailableNoticeCard(
                    cardBg = cardBg,
                    cardBorder = cardBorder,
                    appColors = appColors
                )
            }

            routes.isEmpty() -> {
                CastScanningEmptyStateCard(
                    isScanning = isScanning,
                    cardBg = cardBg,
                    cardBorder = cardBorder,
                    accentColor = accentColor,
                    appColors = appColors
                )
            }

            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    routes.forEach { device ->
                        val isDeviceActive = device.isSelected ||
                            (castState.isConnected && castState.deviceId == device.id)
                        val isDeviceConnecting = device.isConnecting ||
                            (castState.isConnectingOrReconnecting && castState.deviceId == device.id)

                        CastRouteDeviceRow(
                            device = device,
                            isActive = isDeviceActive,
                            isConnecting = isDeviceConnecting,
                            cardBg = cardBg,
                            cardBorder = cardBorder,
                            accentColor = accentColor,
                            appColors = appColors,
                            onClick = {
                                if (isDeviceActive) {
                                    onOpenExpandedControls()
                                } else {
                                    onSelectRoute(device.id)
                                }
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        HorizontalDivider(color = appColors.dividerColor, thickness = 1.dp)
        Spacer(modifier = Modifier.height(14.dp))

        // Local Phone & System Audio Output Section (Bluetooth / Wired / Speaker)
        Text(
            text = "ESTE DISPOSITIVO",
            fontFamily = GoogleSans,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 0.8.sp,
            color = appColors.textTertiary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        LocalPhoneOutputCard(
            currentLocalOutputName = currentLocalOutputName,
            isPlayingLocally = !castState.isActiveSession,
            onPlayOnPhone = onDisconnect,
            onOpenSystemAudioOutput = onOpenSystemAudioOutput,
            cardBg = cardBg,
            cardBorder = cardBorder,
            accentColor = accentColor,
            appColors = appColors
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun ActiveCastSessionCard(
    castState: CastPlaybackState,
    onSetVolume: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onOpenExpandedControls: () -> Unit,
    onDisconnect: () -> Unit,
    accentColor: Color,
    appColors: AppThemeColors
) {
    var isAdjustingVolume by remember { mutableStateOf(false) }
    var dragVolume by remember { mutableFloatStateOf(castState.volume) }
    var lastVolumeEmitMs by remember { mutableLongStateOf(0L) }
    val displayVolume = when {
        castState.isMuted && !isAdjustingVolume -> 0f
        isAdjustingVolume -> dragVolume
        else -> castState.volume
    }.coerceIn(0f, 1f)

    val bgBrush = Brush.verticalGradient(
        colors = listOf(
            accentColor.copy(alpha = if (appColors.isDark) 0.20f else 0.14f),
            if (appColors.isDark) Color(0xFF191D28) else Color(0xFFF3F7FD)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PickerCardShape)
            .background(bgBrush)
            .border(1.dp, accentColor.copy(alpha = 0.55f), PickerCardShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                if (castState.isConnectingOrReconnecting) {
                    CircularProgressIndicator(
                        color = accentColor,
                        strokeWidth = 2.2.dp,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.CastConnected,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = castState.deviceName ?: "Chromecast",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.5.sp,
                    color = appColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = castState.currentTrack?.let { "${it.title} • ${it.artist}" }
                        ?: if (castState.isConnectingOrReconnecting) "Estableciendo conexión…" else "Listo para reproducir",
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = appColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Inline Volume Slider
        if (castState.isConnected) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onToggleMute,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = when {
                            castState.isMuted || displayVolume <= 0.01f -> Icons.AutoMirrored.Rounded.VolumeOff
                            displayVolume < 0.5f -> Icons.AutoMirrored.Rounded.VolumeDown
                            else -> Icons.AutoMirrored.Rounded.VolumeUp
                        },
                        contentDescription = if (castState.isMuted) "Activar sonido" else "Silenciar",
                        tint = if (castState.isMuted) appColors.textTertiary else accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Slider(
                    value = displayVolume,
                    onValueChange = { value ->
                        isAdjustingVolume = true
                        dragVolume = value.coerceIn(0f, 1f)
                        val now = System.currentTimeMillis()
                        if (now - lastVolumeEmitMs >= 80L) {
                            lastVolumeEmitMs = now
                            onSetVolume(dragVolume)
                        }
                    },
                    onValueChangeFinished = {
                        onSetVolume(dragVolume)
                        isAdjustingVolume = false
                    },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor,
                        inactiveTrackColor = appColors.dividerColor
                    ),
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = "${(displayVolume * 100f).roundToInt()}%",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = appColors.textSecondary,
                    modifier = Modifier.width(38.dp),
                    textAlign = TextAlign.End
                )
            }
        }

        // Action Pills: Open Controls + Play on Phone
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (castState.isConnected) {
                val controlsInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .pressWobble(controlsInteraction)
                        .clip(PickerButtonShape)
                        .background(accentColor)
                        .clickable(
                            interactionSource = controlsInteraction,
                            indication = ripple(bounded = true),
                            role = Role.Button,
                            onClick = onOpenExpandedControls
                        )
                        .padding(vertical = 10.dp, horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = Color(0xFF0A0D12),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Controles",
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF0A0D12)
                        )
                    }
                }
            }

            val stopInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .pressWobble(stopInteraction)
                    .clip(PickerButtonShape)
                    .background(Color(0x22EF4444))
                    .border(1.dp, Color(0x66EF4444), PickerButtonShape)
                    .clickable(
                        interactionSource = stopInteraction,
                        indication = ripple(bounded = true),
                        role = Role.Button,
                        onClick = onDisconnect
                    )
                    .padding(vertical = 10.dp, horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Reproducir en teléfono",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    color = if (appColors.isDark) Color(0xFFFCA5A5) else Color(0xFFB91C1C),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CastRouteDeviceRow(
    device: CastRouteDevice,
    isActive: Boolean,
    isConnecting: Boolean,
    cardBg: Color,
    cardBorder: Color,
    accentColor: Color,
    appColors: AppThemeColors,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val rowBg = if (isActive) accentColor.copy(alpha = 0.14f) else cardBg
    val rowBorder = if (isActive) accentColor.copy(alpha = 0.55f) else cardBorder

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressWobble(interaction)
            .clip(PickerCardShape)
            .background(rowBg)
            .border(1.dp, rowBorder, PickerCardShape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = true),
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (isActive) accentColor.copy(alpha = 0.22f)
                    else appColors.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = deviceIconForType(device.deviceType),
                contentDescription = null,
                tint = if (isActive) accentColor else appColors.textPrimary,
                modifier = Modifier.size(21.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.name,
                fontFamily = GoogleSans,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 14.5.sp,
                color = if (isActive) accentColor else appColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = when {
                isConnecting -> "Conectando…"
                isActive -> "Conectado • Toca para controles"
                device.description.isNotBlank() -> device.description
                else -> defaultDeviceTypeLabel(device.deviceType)
            }
            Text(
                text = subtitle,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = appColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        when {
            isConnecting -> {
                CircularProgressIndicator(
                    color = accentColor,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            }
            isActive -> {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Conectado",
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Rounded.Cast,
                    contentDescription = "Conectar",
                    tint = appColors.textTertiary,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

@Composable
private fun CastScanningEmptyStateCard(
    isScanning: Boolean,
    cardBg: Color,
    cardBorder: Color,
    accentColor: Color,
    appColors: AppThemeColors
) {
    val radarTransition = rememberInfiniteTransition(label = "castRadar")
    val ring1 by radarTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarRing1"
    )
    val ring2 by radarTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, delayMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarRing2"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PickerCardShape)
            .background(cardBg)
            .border(1.dp, cardBorder, PickerCardShape)
            .padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier.size(68.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isScanning) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val maxR = size.minDimension / 2f
                    drawCircle(
                        color = accentColor.copy(alpha = (1f - ring1) * 0.48f),
                        radius = maxR * (0.35f + 0.65f * ring1),
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawCircle(
                        color = accentColor.copy(alpha = (1f - ring2) * 0.35f),
                        radius = maxR * (0.35f + 0.65f * ring2),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Wifi,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Text(
            text = "Buscando dispositivos Chromecast en tu red Wi-Fi…",
            fontFamily = GoogleSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = appColors.textPrimary,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Asegúrate de que tu Chromecast, Google TV o altavoz inteligente esté encendido y en la misma red Wi-Fi.",
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            color = appColors.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CastUnavailableNoticeCard(
    cardBg: Color,
    cardBorder: Color,
    appColors: AppThemeColors
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PickerCardShape)
            .background(cardBg)
            .border(1.dp, cardBorder, PickerCardShape)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(appColors.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Cast,
                contentDescription = null,
                tint = appColors.textTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Chromecast no disponible en este dispositivo",
                fontFamily = GoogleSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.5.sp,
                color = appColors.textPrimary
            )
            Text(
                text = "Se requiere Google Play Services para transmitir por Chromecast. Aún puedes cambiar la salida de audio local abajo.",
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Normal,
                fontSize = 11.5.sp,
                color = appColors.textSecondary
            )
        }
    }
}

@Composable
private fun LocalPhoneOutputCard(
    currentLocalOutputName: String,
    isPlayingLocally: Boolean,
    onPlayOnPhone: () -> Unit,
    onOpenSystemAudioOutput: (() -> Unit)?,
    cardBg: Color,
    cardBorder: Color,
    accentColor: Color,
    appColors: AppThemeColors
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PickerCardShape)
            .background(if (isPlayingLocally) accentColor.copy(alpha = 0.10f) else cardBg)
            .border(
                width = 1.dp,
                color = if (isPlayingLocally) accentColor.copy(alpha = 0.42f) else cardBorder,
                shape = PickerCardShape
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isPlayingLocally, onClick = onPlayOnPhone),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isPlayingLocally) accentColor.copy(alpha = 0.20f) else appColors.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Smartphone,
                    contentDescription = null,
                    tint = if (isPlayingLocally) accentColor else appColors.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Reproducir en este teléfono",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = appColors.textPrimary
                )
                Text(
                    text = if (isPlayingLocally) "Activo • $currentLocalOutputName" else currentLocalOutputName,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = if (isPlayingLocally) accentColor else appColors.textSecondary
                )
            }
            if (isPlayingLocally) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Salida activa",
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (onOpenSystemAudioOutput != null) {
            val sysInteraction = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressWobble(sysInteraction)
                    .clip(PickerButtonShape)
                    .background(if (appColors.isDark) Color(0xFF252A38) else Color(0xFFE7EDF6))
                    .clickable(
                        interactionSource = sysInteraction,
                        indication = ripple(bounded = true),
                        role = Role.Button,
                        onClick = onOpenSystemAudioOutput
                    )
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.BluetoothAudio,
                    contentDescription = null,
                    tint = appColors.textPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Bluetooth y salida de audio del sistema",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    color = appColors.textPrimary
                )
            }
        }
    }
}

private fun deviceIconForType(type: CastDeviceType): ImageVector = when (type) {
    CastDeviceType.TV -> Icons.Rounded.Tv
    CastDeviceType.SPEAKER -> Icons.Rounded.Speaker
    CastDeviceType.SPEAKER_GROUP -> Icons.Rounded.SpeakerGroup
    CastDeviceType.CHROMECAST,
    CastDeviceType.UNKNOWN -> Icons.Rounded.Cast
}

private fun defaultDeviceTypeLabel(type: CastDeviceType): String = when (type) {
    CastDeviceType.TV -> "Google TV / Chromecast"
    CastDeviceType.SPEAKER -> "Altavoz con Chromecast"
    CastDeviceType.SPEAKER_GROUP -> "Grupo de altavoces"
    CastDeviceType.CHROMECAST,
    CastDeviceType.UNKNOWN -> "Dispositivo Chromecast"
}

@Preview(name = "CastPicker Discovered & Connected - Dark", showBackground = true, backgroundColor = 0xFF13161E)
@Composable
private fun CastPickerConnectedDarkPreview() {
    AdaptivePreviewSurface(widthDp = 400, heightDp = 760, darkTheme = true) {
        CastDevicePickerSheetContent(
            castState = CastPlaybackState(
                connectionState = CastConnectionState.CONNECTED,
                deviceName = "Sala Living TV",
                deviceId = "route_tv",
                currentTrack = AdaptivePreviewFixtures.tracks.first(),
                isPlaying = true,
                volume = 0.68f
            ),
            routes = listOf(
                CastRouteDevice(
                    id = "route_tv",
                    name = "Sala Living TV",
                    description = "Google TV • 4K",
                    isSelected = true,
                    volume = 68,
                    volumeMax = 100,
                    deviceType = CastDeviceType.TV
                ),
                CastRouteDevice(
                    id = "route_nest",
                    name = "Nest Audio Estudio",
                    description = "Altavoz inteligente",
                    deviceType = CastDeviceType.SPEAKER
                ),
                CastRouteDevice(
                    id = "route_group",
                    name = "Toda la casa",
                    description = "Grupo de 3 altavoces",
                    deviceType = CastDeviceType.SPEAKER_GROUP
                )
            ),
            isScanning = true,
            onSelectRoute = {},
            onDisconnect = {},
            onSetVolume = {},
            onToggleMute = {},
            onOpenExpandedControls = {},
            currentLocalOutputName = "Sony WH-1000XM5",
            onOpenSystemAudioOutput = {}
        )
    }
}

@Preview(name = "CastPicker Scanning Empty - Light", showBackground = true, backgroundColor = 0xFFFDFEFF)
@Composable
private fun CastPickerScanningLightPreview() {
    AdaptivePreviewSurface(widthDp = 400, heightDp = 560, darkTheme = false) {
        CastDevicePickerSheetContent(
            castState = CastPlaybackState(connectionState = CastConnectionState.DISCONNECTED),
            routes = emptyList(),
            isScanning = true,
            onSelectRoute = {},
            onDisconnect = {},
            onSetVolume = {},
            onToggleMute = {},
            onOpenExpandedControls = {},
            currentLocalOutputName = "Altavoz del teléfono",
            onOpenSystemAudioOutput = {}
        )
    }
}

@Preview(name = "CastPicker Unavailable - Dark", showBackground = true, backgroundColor = 0xFF13161E)
@Composable
private fun CastPickerUnavailableDarkPreview() {
    AdaptivePreviewSurface(widthDp = 400, heightDp = 480, darkTheme = true) {
        CastDevicePickerSheetContent(
            castState = CastPlaybackState(connectionState = CastConnectionState.UNAVAILABLE),
            routes = emptyList(),
            isScanning = false,
            onSelectRoute = {},
            onDisconnect = {},
            onSetVolume = {},
            onToggleMute = {},
            onOpenExpandedControls = {},
            currentLocalOutputName = "Phone Speaker",
            onOpenSystemAudioOutput = {}
        )
    }
}
