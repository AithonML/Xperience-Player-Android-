package com.xperience.player.ui.cast

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
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.xperience.player.domain.model.PlaybackRepeatMode
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.service.cast.CastConnectionState
import com.xperience.player.service.cast.CastPlaybackState
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

private val ExpandedArtworkShape = ContinuousRoundedRectangle(size = 24.dp)
private val ExpandedCardShape = ContinuousRoundedRectangle(size = 20.dp)
private val ExpandedPillShape = ContinuousRoundedRectangle(percent = 50)
private val QueueThumbShape = ContinuousRoundedRectangle(size = 10.dp)

/**
 * Custom full-featured Chromecast remote control bottom sheet matching Xperience Music Player's
 * design system: ambient cover artwork, "Transmitiendo a <Device>" chip, live seek scrubber,
 * transport controls, dedicated Cast hardware volume slider + mute toggle, interactive queue
 * preview with tap-to-jump, and "Detener transmisión" button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastExpandedControlsSheet(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    castState: CastPlaybackState,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSetVolume: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onJumpToQueueIndex: (Int) -> Unit,
    onDisconnect: () -> Unit,
    onOpenDevicePicker: () -> Unit,
    accentColor: Color = LocalAccentColor.current,
    appColors: AppThemeColors = LocalAppColors.current
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetBg = if (appColors.isDark) Color(0xFF11141C) else Color(0xFFFDFEFF)

    AdaptiveModalContainer(
        isOpen = isOpen,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        dialogMaxWidth = 540.dp,
        dialogMaxHeight = 820.dp
    ) {
        CastExpandedControlsContent(
            castState = castState,
            onTogglePlayPause = onTogglePlayPause,
            onSkipNext = onSkipNext,
            onSkipPrevious = onSkipPrevious,
            onSeekTo = onSeekTo,
            onSetVolume = onSetVolume,
            onToggleMute = onToggleMute,
            onToggleShuffle = onToggleShuffle,
            onCycleRepeatMode = onCycleRepeatMode,
            onJumpToQueueIndex = onJumpToQueueIndex,
            onDisconnect = {
                onDisconnect()
                onDismiss()
            },
            onOpenDevicePicker = {
                onDismiss()
                onOpenDevicePicker()
            },
            onClose = onDismiss,
            accentColor = accentColor,
            appColors = appColors
        )
    }
}

@Composable
fun CastExpandedControlsContent(
    castState: CastPlaybackState,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSetVolume: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onJumpToQueueIndex: (Int) -> Unit,
    onDisconnect: () -> Unit,
    onOpenDevicePicker: () -> Unit,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    accentColor: Color = LocalAccentColor.current,
    appColors: AppThemeColors = LocalAppColors.current
) {
    val context = LocalContext.current
    val deviceName = castState.deviceName?.takeIf { it.isNotBlank() } ?: "Chromecast"
    val track = castState.currentTrack
    val cardBg = if (appColors.isDark) Color(0xFF1A1F2B) else Color(0xFFF3F6FB)
    val cardBorder = if (appColors.isDark) Color(0x22FFFFFF) else Color(0x16000000)

    // Smooth seek slider dragging without jitter
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(castState.progressFraction) }
    val activeProgressFraction = if (isScrubbing) scrubFraction else castState.progressFraction
    val displayedPositionMs = if (isScrubbing && castState.durationMs > 0L) {
        (scrubFraction * castState.durationMs.toFloat()).toLong()
    } else {
        castState.positionMs
    }

    // Smooth remote Cast volume slider (throttled live updates + jitter-free dragging)
    var isAdjustingVolume by remember { mutableStateOf(false) }
    var dragVolume by remember { mutableFloatStateOf(castState.volume) }
    var lastVolumeEmitMs by remember { mutableLongStateOf(0L) }
    val displayedVolume = when {
        castState.isMuted && !isAdjustingVolume -> 0f
        isAdjustingVolume -> dragVolume
        else -> castState.volume
    }.coerceIn(0f, 1f)

    val ambientBackdrop = Brush.verticalGradient(
        colors = listOf(
            accentColor.copy(alpha = if (appColors.isDark) 0.18f else 0.10f),
            Color.Transparent
        )
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ambientBackdrop)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Top Bar: "Transmitiendo a <Device>" chip + Close button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val chipInteraction = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .pressWobble(chipInteraction)
                    .clip(ExpandedPillShape)
                    .background(accentColor.copy(alpha = 0.16f))
                    .border(1.dp, accentColor.copy(alpha = 0.52f), ExpandedPillShape)
                    .clickable(
                        interactionSource = chipInteraction,
                        indication = ripple(bounded = true),
                        role = Role.Button,
                        onClick = onOpenDevicePicker
                    )
                    .padding(horizontal = 13.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CastConnected,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Transmitiendo a $deviceName",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (onClose != null) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Cerrar controles",
                        tint = appColors.textSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 2. Hero Album Artwork with Ambient Elevation
        Box(
            modifier = Modifier
                .size(196.dp)
                .shadow(
                    elevation = 24.dp,
                    shape = ExpandedArtworkShape,
                    ambientColor = accentColor.copy(alpha = 0.35f),
                    spotColor = Color.Black.copy(alpha = if (appColors.isDark) 0.70f else 0.24f)
                )
                .clip(ExpandedArtworkShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            accentColor.copy(alpha = 0.28f),
                            if (appColors.isDark) Color(0xFF1E222E) else Color(0xFFE7EEF6)
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = if (appColors.isDark) 0.18f else 0.65f), ExpandedArtworkShape),
            contentAlignment = Alignment.Center
        ) {
            val artworkUri = track?.artworkUri
            if (!artworkUri.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(artworkUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(64.dp)
                )
            }

            if (castState.isBuffering || castState.isConnectingOrReconnecting) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.42f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = accentColor,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Track Title, Artist & Album Metadata
        Text(
            text = track?.title ?: "Sin reproducción activa",
            fontFamily = GoogleSans,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = appColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = buildString {
                append(track?.artist?.takeIf { it.isNotBlank() } ?: "Xperience Music Player")
                val album = track?.album?.takeIf { it.isNotBlank() }
                if (album != null) {
                    append(" • ")
                    append(album)
                }
            },
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = appColors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Real-Time Seek Slider & Timestamps
        Column(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = activeProgressFraction.coerceIn(0f, 1f),
                onValueChange = { fraction ->
                    isScrubbing = true
                    scrubFraction = fraction.coerceIn(0f, 1f)
                },
                onValueChangeFinished = {
                    if (castState.durationMs > 0L) {
                        val targetMs = (scrubFraction * castState.durationMs.toFloat()).toLong()
                        onSeekTo(targetMs)
                    }
                    isScrubbing = false
                },
                enabled = castState.isConnected && castState.durationMs > 0L,
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = appColors.dividerColor,
                    disabledThumbColor = appColors.textTertiary,
                    disabledActiveTrackColor = appColors.textTertiary
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatCastDuration(displayedPositionMs),
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp,
                    color = appColors.textSecondary
                )
                Text(
                    text = formatCastDuration(castState.durationMs),
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp,
                    color = appColors.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 5. Primary Remote Transport Controls (Shuffle | Prev | Play/Pause | Next | Repeat)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleShuffle,
                enabled = castState.isConnected
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = "Aleatorio",
                    tint = if (castState.isShuffleEnabled) accentColor else appColors.textSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(
                onClick = onSkipPrevious,
                enabled = castState.isConnected,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Anterior",
                    tint = appColors.textPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }

            val playInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .pressWobble(playInteraction)
                    .size(66.dp)
                    .shadow(
                        elevation = 16.dp,
                        shape = CircleShape,
                        ambientColor = accentColor.copy(alpha = 0.55f),
                        spotColor = accentColor.copy(alpha = 0.55f)
                    )
                    .clip(CircleShape)
                    .background(accentColor)
                    .clickable(
                        interactionSource = playInteraction,
                        enabled = castState.isConnected,
                        indication = ripple(bounded = false, radius = 33.dp),
                        role = Role.Button,
                        onClick = onTogglePlayPause
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (castState.isBuffering) {
                    CircularProgressIndicator(
                        color = Color(0xFF0A0D12),
                        strokeWidth = 2.8.dp,
                        modifier = Modifier.size(26.dp)
                    )
                } else {
                    Icon(
                        imageVector = if (castState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (castState.isPlaying) "Pausar" else "Reproducir",
                        tint = Color(0xFF0A0D12),
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            IconButton(
                onClick = onSkipNext,
                enabled = castState.isConnected,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Siguiente",
                    tint = appColors.textPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(
                onClick = onCycleRepeatMode,
                enabled = castState.isConnected
            ) {
                val repeatIcon = if (castState.repeatMode == PlaybackRepeatMode.RepeatOne) {
                    Icons.Rounded.RepeatOne
                } else {
                    Icons.Rounded.Repeat
                }
                Icon(
                    imageVector = repeatIcon,
                    contentDescription = "Modo de repetición",
                    tint = if (castState.repeatMode != PlaybackRepeatMode.Off) accentColor else appColors.textSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 6. Dedicated Hardware-Style Cast Volume Slider + Mute Toggle Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ExpandedCardShape)
                .background(cardBg)
                .border(1.dp, cardBorder, ExpandedCardShape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Volumen de $deviceName",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    color = appColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(ExpandedPillShape)
                        .background(accentColor.copy(alpha = 0.15f))
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (castState.isMuted) "Silenciado" else "${(displayedVolume * 100f).roundToInt()}%",
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        color = accentColor
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onToggleMute,
                    enabled = castState.isConnected,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = when {
                            castState.isMuted || displayedVolume <= 0.01f -> Icons.AutoMirrored.Rounded.VolumeOff
                            displayedVolume < 0.5f -> Icons.AutoMirrored.Rounded.VolumeDown
                            else -> Icons.AutoMirrored.Rounded.VolumeUp
                        },
                        contentDescription = if (castState.isMuted) "Activar sonido" else "Silenciar Chromecast",
                        tint = if (castState.isMuted) appColors.textTertiary else accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Slider(
                    value = displayedVolume.coerceIn(0f, 1f),
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
                    enabled = castState.isConnected,
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor,
                        inactiveTrackColor = appColors.dividerColor
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 7. Cast Queue Preview with Tap-to-Jump
        if (castState.queue.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            CastQueuePreviewCard(
                queue = castState.queue,
                currentQueueIndex = castState.currentQueueIndex,
                onJumpToQueueIndex = onJumpToQueueIndex,
                cardBg = cardBg,
                cardBorder = cardBorder,
                accentColor = accentColor,
                appColors = appColors
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 8. Prominent "Detener transmisión" Button
        val disconnectInteraction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pressWobble(disconnectInteraction)
                .clip(ExpandedPillShape)
                .background(Color(0x26EF4444))
                .border(1.dp, Color(0x70EF4444), ExpandedPillShape)
                .clickable(
                    interactionSource = disconnectInteraction,
                    indication = ripple(bounded = true),
                    role = Role.Button,
                    onClick = onDisconnect
                )
                .padding(vertical = 13.dp, horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.StopCircle,
                    contentDescription = null,
                    tint = if (appColors.isDark) Color(0xFFFCA5A5) else Color(0xFFDC2626),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Detener transmisión",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (appColors.isDark) Color(0xFFFCA5A5) else Color(0xFFDC2626)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
private fun CastQueuePreviewCard(
    queue: List<TrackItem>,
    currentQueueIndex: Int,
    onJumpToQueueIndex: (Int) -> Unit,
    cardBg: Color,
    cardBorder: Color,
    accentColor: Color,
    appColors: AppThemeColors
) {
    val context = LocalContext.current
    // Show up to 6 tracks centered around currentQueueIndex so the modal stays responsive and readable
    val startWindow = (currentQueueIndex.coerceAtLeast(0) - 1).coerceAtLeast(0)
    val endWindow = minOf(queue.lastIndex, startWindow + 5)
    val visibleIndices = (startWindow..endWindow).toList()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ExpandedCardShape)
            .background(cardBg)
            .border(1.dp, cardBorder, ExpandedCardShape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Cola en Chromecast",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = appColors.textPrimary
                )
            }
            Text(
                text = "${queue.size} canciones",
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Medium,
                fontSize = 11.5.sp,
                color = appColors.textSecondary
            )
        }

        visibleIndices.forEach { index ->
            val item = queue[index]
            val isCurrent = index == currentQueueIndex
            val rowInteraction = remember { MutableInteractionSource() }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ContinuousRoundedRectangle(size = 13.dp))
                    .background(if (isCurrent) accentColor.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable(
                        interactionSource = rowInteraction,
                        indication = ripple(bounded = true),
                        role = Role.Button,
                        onClick = { onJumpToQueueIndex(index) }
                    )
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(QueueThumbShape)
                        .background(
                            if (isCurrent) accentColor.copy(alpha = 0.22f)
                            else appColors.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (!item.artworkUri.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(item.artworkUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                    } else {
                        Icon(
                            imageVector = if (isCurrent) Icons.Rounded.GraphicEq else Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = if (isCurrent) accentColor else appColors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        fontFamily = GoogleSans,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = if (isCurrent) accentColor else appColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.artist,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Normal,
                        fontSize = 11.5.sp,
                        color = appColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isCurrent) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = "Reproduciendo ahora",
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                } else if (item.durationMs > 0L) {
                    Text(
                        text = item.formattedDuration,
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        color = appColors.textTertiary
                    )
                }
            }
        }
    }
}

internal fun formatCastDuration(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

@Preview(name = "CastExpandedControls - Dark", showBackground = true, backgroundColor = 0xFF11141C)
@Composable
private fun CastExpandedControlsDarkPreview() {
    AdaptivePreviewSurface(widthDp = 412, heightDp = 920, darkTheme = true) {
        CastExpandedControlsContent(
            castState = CastPlaybackState(
                connectionState = CastConnectionState.CONNECTED,
                deviceName = "Sala Living TV",
                currentTrack = AdaptivePreviewFixtures.tracks.first(),
                isPlaying = true,
                positionMs = 74_000L,
                durationMs = 208_000L,
                volume = 0.72f,
                isMuted = false,
                queue = AdaptivePreviewFixtures.tracks,
                currentQueueIndex = 0,
                repeatMode = PlaybackRepeatMode.RepeatAll,
                isShuffleEnabled = true
            ),
            onTogglePlayPause = {},
            onSkipNext = {},
            onSkipPrevious = {},
            onSeekTo = {},
            onSetVolume = {},
            onToggleMute = {},
            onToggleShuffle = {},
            onCycleRepeatMode = {},
            onJumpToQueueIndex = {},
            onDisconnect = {},
            onOpenDevicePicker = {}
        )
    }
}

@Preview(name = "CastExpandedControls Muted & Buffering - Light", showBackground = true, backgroundColor = 0xFFFDFEFF)
@Composable
private fun CastExpandedControlsLightPreview() {
    AdaptivePreviewSurface(widthDp = 412, heightDp = 860, darkTheme = false) {
        CastExpandedControlsContent(
            castState = CastPlaybackState(
                connectionState = CastConnectionState.CONNECTED,
                deviceName = "Nest Audio Estudio",
                currentTrack = AdaptivePreviewFixtures.tracks[1],
                isPlaying = false,
                isBuffering = true,
                positionMs = 42_000L,
                durationMs = 165_000L,
                volume = 0.45f,
                isMuted = true,
                queue = AdaptivePreviewFixtures.tracks.take(4),
                currentQueueIndex = 1,
                repeatMode = PlaybackRepeatMode.RepeatOne
            ),
            onTogglePlayPause = {},
            onSkipNext = {},
            onSkipPrevious = {},
            onSeekTo = {},
            onSetVolume = {},
            onToggleMute = {},
            onToggleShuffle = {},
            onCycleRepeatMode = {},
            onJumpToQueueIndex = {},
            onDisconnect = {},
            onOpenDevicePicker = {}
        )
    }
}
