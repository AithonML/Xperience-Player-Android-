package com.xperience.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.domain.model.download.DownloadAudioFormat
import com.xperience.player.service.dsp.AudioDspState
import com.xperience.player.service.dsp.SleepTimerState
import com.xperience.player.ui.adaptive.AdaptiveModalContainer
import com.xperience.player.ui.adaptive.LocalAppWindowSizeClass
import com.xperience.player.ui.adaptive.shouldUseModalDialogs
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.player.AudioEffectsSectionTab
import com.xperience.player.ui.theme.AppThemeColors
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.PlusJakartaSans
import kotlinx.coroutines.launch
import kotlin.math.abs

private val QuickTileShape = ContinuousRoundedRectangle(percent = 28)
private val ActionWarmAccent = Color(0xFFE07B39)

/**
 * Unified Track Context Menu Sheet used by 3-dot buttons, Home/Catalog long-press,
 * Queue items, and Now Playing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackOptionsMenuSheet(
    track: TrackItem?,
    isLiked: Boolean,
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onAddToPlaylist: (TrackItem) -> Unit,
    onDownload: (TrackItem) -> Unit,
    onToggleFavorite: (TrackItem) -> Unit,
    onSetAsRingtone: ((TrackItem) -> Unit)? = null,
    onSongInfo: ((TrackItem) -> Unit)? = null,
    onPlay: ((TrackItem) -> Unit)? = null,
    onPlayNext: ((TrackItem) -> Unit)? = null,
    onAddToQueue: ((TrackItem) -> Unit)? = null,
    onGoToArtist: ((String) -> Unit)? = null,
    onGoToAlbum: ((TrackItem) -> Unit)? = null,
    onDirectDownloadFormat: ((TrackItem, DownloadAudioFormat) -> Unit)? = null,
    contextualRemoveLabel: String? = null,
    onContextualRemove: (() -> Unit)? = null,
    isNowPlayingContext: Boolean = false,
    dspState: AudioDspState = AudioDspState(),
    sleepTimerState: SleepTimerState = SleepTimerState(),
    selectedVisualizerName: String = "",
    isVisualizerActive: Boolean = false,
    onToggleNightcore: () -> Unit = {},
    onOpenAudioEffectsTab: (AudioEffectsSectionTab) -> Unit = {},
    onOpenAmbientMode: () -> Unit = {},
    onOpenVisualizer: () -> Unit = {}
) {
    if (!isOpen || track == null) return

    val useModalDialog = LocalAppWindowSizeClass.current.shouldUseModalDialogs
    val appColors = LocalAppColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    val dismissThen: (() -> Unit) -> Unit = { after ->
        coroutineScope.launch {
            if (!useModalDialog) sheetState.hide()
            onDismiss()
            after()
        }
    }

    AdaptiveModalContainer(
        isOpen = true,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (appColors.isDark) Color(0xFF14161D) else Color(0xFFFFFFFF),
        dialogMaxWidth = 500.dp,
        showDragHandle = false
    ) {
        TrackOptionsMenuContent(
            track = track,
            isLiked = isLiked,
            onDismiss = { dismissThen {} },
            onAddToPlaylist = { dismissThen { onAddToPlaylist(track) } },
            onDownload = { dismissThen { onDownload(track) } },
            onToggleFavorite = { onToggleFavorite(track) },
            onSetAsRingtone = onSetAsRingtone?.let { cb -> { dismissThen { cb(track) } } },
            onSongInfo = onSongInfo?.let { cb -> { dismissThen { cb(track) } } },
            onPlay = onPlay?.let { cb -> { dismissThen { cb(track) } } },
            onPlayNext = onPlayNext?.let { cb -> { dismissThen { cb(track) } } },
            onAddToQueue = onAddToQueue?.let { cb -> { dismissThen { cb(track) } } },
            onGoToArtist = if (track.artist.isNotBlank() && onGoToArtist != null) {
                { dismissThen { onGoToArtist(track.artist) } }
            } else {
                null
            },
            onGoToAlbum = if (resolveGoToAlbumLabel(track) != null && onGoToAlbum != null) {
                { dismissThen { onGoToAlbum(track) } }
            } else {
                null
            },
            onDirectDownloadFormat = onDirectDownloadFormat?.let { cb ->
                { format -> dismissThen { cb(track, format) } }
            },
            contextualRemoveLabel = contextualRemoveLabel,
            onContextualRemove = onContextualRemove?.let { cb -> { dismissThen { cb() } } },
            isNowPlayingContext = isNowPlayingContext,
            dspState = dspState,
            sleepTimerState = sleepTimerState,
            selectedVisualizerName = selectedVisualizerName,
            isVisualizerActive = isVisualizerActive,
            onToggleNightcore = onToggleNightcore,
            onOpenAudioEffectsTab = { tab -> dismissThen { onOpenAudioEffectsTab(tab) } },
            onOpenAmbientMode = { dismissThen { onOpenAmbientMode() } },
            onOpenVisualizer = { dismissThen { onOpenVisualizer() } },
            bottomPaddingDp = if (useModalDialog) 16 else 32
        )
    }
}

/**
 * Embeddable content for [TrackOptionsMenuSheet], also reused directly inside Now Playing's menu container.
 */
@Composable
fun TrackOptionsMenuContent(
    track: TrackItem,
    isLiked: Boolean,
    onDismiss: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownload: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSetAsRingtone: (() -> Unit)? = null,
    onSongInfo: (() -> Unit)? = null,
    onPlay: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onGoToArtist: (() -> Unit)? = null,
    onGoToAlbum: (() -> Unit)? = null,
    onDirectDownloadFormat: ((DownloadAudioFormat) -> Unit)? = null,
    contextualRemoveLabel: String? = null,
    onContextualRemove: (() -> Unit)? = null,
    isNowPlayingContext: Boolean = false,
    dspState: AudioDspState = AudioDspState(),
    sleepTimerState: SleepTimerState = SleepTimerState(),
    selectedVisualizerName: String = "",
    isVisualizerActive: Boolean = false,
    onToggleNightcore: () -> Unit = {},
    onOpenAudioEffectsTab: (AudioEffectsSectionTab) -> Unit = {},
    onOpenAmbientMode: () -> Unit = {},
    onOpenVisualizer: () -> Unit = {},
    bottomPaddingDp: Int = 28
) {
    val appColors = LocalAppColors.current
    val accentColor = LocalAccentColor.current
    val warmActionColor = if (appColors.isDark) ActionWarmAccent else accentColor
    val dividerColor = if (appColors.isDark) Color(0x1EFFFFFF) else Color(0x16000000)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = bottomPaddingDp.dp)
    ) {
        // Compact header strip showing track art, title & artist + close button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF222632)),
                contentAlignment = Alignment.Center
            ) {
                if (!track.artworkUri.isNullOrBlank()) {
                    AsyncImage(
                        model = track.artworkUri,
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp,
                    color = appColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    fontFamily = PlusJakartaSans,
                    fontSize = 12.sp,
                    color = appColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (appColors.isDark) Color(0x20FFFFFF) else Color(0x10000000))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = appColors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Top Dual Tiles: [ Like ]  [ Download ]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TopQuickTile(
                icon = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                label = if (isLiked) "Liked" else "Like",
                iconTint = if (isLiked) Color(0xFFFF375F) else appColors.textPrimary,
                isHighlighted = isLiked,
                highlightColor = Color(0xFFFF375F),
                appColors = appColors,
                onClick = onToggleFavorite,
                modifier = Modifier.weight(1f)
            )
            TopQuickTile(
                icon = Icons.Default.Download,
                label = "Download",
                iconTint = appColors.textPrimary,
                isHighlighted = false,
                highlightColor = accentColor,
                appColors = appColors,
                onClick = onDownload,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = dividerColor, thickness = 0.8.dp)
        Spacer(modifier = Modifier.height(6.dp))

        // Primary Track & Queue Actions
        if (!isNowPlayingContext && onPlay != null) {
            MenuListRow(
                icon = Icons.Default.PlayArrow,
                label = "Play",
                textColor = appColors.textPrimary,
                iconTint = appColors.textPrimary,
                onClick = onPlay
            )
        }
        if (onPlayNext != null) {
            MenuListRow(
                icon = Icons.Default.PlaylistPlay,
                label = "Play Next",
                textColor = appColors.textPrimary,
                iconTint = appColors.textPrimary,
                onClick = onPlayNext
            )
        }
        if (onAddToQueue != null) {
            MenuListRow(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                label = "Add to Queue",
                textColor = appColors.textPrimary,
                iconTint = appColors.textPrimary,
                onClick = onAddToQueue
            )
        }
        MenuListRow(
            icon = Icons.AutoMirrored.Filled.PlaylistAdd,
            label = "Add to Playlist",
            textColor = appColors.textPrimary,
            iconTint = appColors.textPrimary,
            onClick = onAddToPlaylist
        )
        if (onGoToArtist != null && track.artist.isNotBlank()) {
            MenuListRow(
                icon = Icons.Default.Person,
                label = "Go to ${track.artist}",
                textColor = appColors.textPrimary,
                iconTint = appColors.textPrimary,
                onClick = onGoToArtist
            )
        }
        val goToAlbumLabel = resolveGoToAlbumLabel(track)
        if (onGoToAlbum != null && goToAlbumLabel != null) {
            MenuListRow(
                icon = Icons.Default.MusicNote,
                label = goToAlbumLabel,
                textColor = appColors.textPrimary,
                iconTint = appColors.textPrimary,
                onClick = onGoToAlbum
            )
        }
        if (onSongInfo != null) {
            MenuListRow(
                icon = Icons.Default.Info,
                label = "More Information",
                textColor = appColors.textPrimary,
                iconTint = appColors.textPrimary,
                onClick = onSongInfo
            )
        }
        if (!contextualRemoveLabel.isNullOrBlank() && onContextualRemove != null) {
            MenuListRow(
                icon = Icons.Default.DeleteOutline,
                label = contextualRemoveLabel,
                textColor = Color(0xFFFF453A),
                iconTint = Color(0xFFFF453A),
                onClick = onContextualRemove
            )
        }

        // Quality & Direct Format Downloads Section
        Spacer(modifier = Modifier.height(6.dp))
        HorizontalDivider(color = dividerColor, thickness = 0.8.dp)
        Spacer(modifier = Modifier.height(6.dp))

        MenuListRow(
            icon = Icons.Default.Settings,
            label = "Choose Version & Quality…",
            textColor = warmActionColor,
            iconTint = warmActionColor,
            onClick = onDownload
        )
        if (onDirectDownloadFormat != null) {
            MenuListRow(
                icon = Icons.Default.Download,
                label = "Download FLAC Lossless",
                textColor = warmActionColor,
                iconTint = warmActionColor,
                onClick = { onDirectDownloadFormat(DownloadAudioFormat.FLAC_LOSSLESS) }
            )
            MenuListRow(
                icon = Icons.Default.Download,
                label = "Download MP3 (320kbps)",
                textColor = appColors.textPrimary,
                iconTint = appColors.textPrimary,
                onClick = { onDirectDownloadFormat(DownloadAudioFormat.MP3_320K) }
            )
        }

        // Now Playing Exclusive Audio Effects & Utilities
        if (isNowPlayingContext) {
            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = dividerColor, thickness = 0.8.dp)
            Spacer(modifier = Modifier.height(6.dp))

            val isEqModified = dspState.isEqualizerEnabled && (
                dspState.selectedPreset != com.xperience.player.service.dsp.EqPreset.FLAT ||
                    abs(dspState.bassBoostDb) > 0.1f ||
                    abs(dspState.trebleBoostDb) > 0.1f ||
                    dspState.bandGainsDb.any { abs(it) > 0.1f }
                )
            val isSpeedModified = !dspState.isNightcoreEnabled && (
                abs(dspState.effectiveSpeed - 1.0f) > 0.01f ||
                    abs(dspState.effectiveSemitones) > 0.05f
                )
            val st = dspState.effectiveSemitones
            val sign = if (st > 0.05f) "+" else ""

            MenuListRow(
                icon = Icons.Default.Bolt,
                label = "Nightcore Mode",
                badgeText = if (dspState.isNightcoreEnabled) "ON" else "Off",
                textColor = if (dspState.isNightcoreEnabled) accentColor else appColors.textPrimary,
                iconTint = if (dspState.isNightcoreEnabled) accentColor else appColors.textPrimary,
                badgeColor = if (dspState.isNightcoreEnabled) accentColor else appColors.textSecondary,
                onClick = onToggleNightcore
            )
            MenuListRow(
                icon = Icons.Default.Equalizer,
                label = "Equalizer",
                badgeText = if (isEqModified) dspState.selectedPreset.displayName else "Flat",
                textColor = if (isEqModified) accentColor else appColors.textPrimary,
                iconTint = if (isEqModified) accentColor else appColors.textPrimary,
                badgeColor = if (isEqModified) accentColor else appColors.textSecondary,
                onClick = { onOpenAudioEffectsTab(AudioEffectsSectionTab.EQUALIZER) }
            )
            MenuListRow(
                icon = Icons.Default.Speed,
                label = "Speed & Pitch",
                badgeText = "%.2fx • %s%.1f st".format(dspState.effectiveSpeed, sign, st),
                textColor = if (isSpeedModified) accentColor else appColors.textPrimary,
                iconTint = if (isSpeedModified) accentColor else appColors.textPrimary,
                badgeColor = if (isSpeedModified) accentColor else appColors.textSecondary,
                onClick = { onOpenAudioEffectsTab(AudioEffectsSectionTab.SPEED_PITCH) }
            )
            MenuListRow(
                icon = Icons.Default.Bedtime,
                label = "Sleep Timer",
                badgeText = if (sleepTimerState.isActive) sleepTimerState.formattedRemaining else "Off",
                textColor = if (sleepTimerState.isActive) Color(0xFFFFB74D) else appColors.textPrimary,
                iconTint = if (sleepTimerState.isActive) Color(0xFFFFB74D) else appColors.textPrimary,
                badgeColor = if (sleepTimerState.isActive) Color(0xFFFFB74D) else appColors.textSecondary,
                onClick = { onOpenAudioEffectsTab(AudioEffectsSectionTab.SLEEP_TIMER) }
            )
            MenuListRow(
                icon = Icons.Default.Fullscreen,
                label = "Ambient Mode",
                textColor = appColors.textPrimary,
                iconTint = appColors.textPrimary,
                onClick = onOpenAmbientMode
            )
            if (onSetAsRingtone != null) {
                MenuListRow(
                    icon = Icons.Default.NotificationsActive,
                    label = "Set as Ringtone",
                    textColor = appColors.textPrimary,
                    iconTint = appColors.textPrimary,
                    onClick = onSetAsRingtone
                )
            }
            MenuListRow(
                icon = Icons.Default.GraphicEq,
                label = "Visualizer",
                badgeText = selectedVisualizerName.ifBlank { null },
                textColor = if (isVisualizerActive) accentColor else appColors.textPrimary,
                iconTint = if (isVisualizerActive) accentColor else appColors.textPrimary,
                badgeColor = if (isVisualizerActive) accentColor else appColors.textSecondary,
                onClick = onOpenVisualizer
            )
        }
    }
}

internal fun resolveGoToAlbumLabel(track: TrackItem): String? {
    val clean = track.album.trim()
    val hasExplicitAlbum = clean.isNotBlank() &&
        !clean.equals("Unknown Album", ignoreCase = true) &&
        !clean.equals("Single", ignoreCase = true) &&
        !clean.equals("YouTube Music", ignoreCase = true) &&
        !clean.equals("Official Video", ignoreCase = true)
    if (hasExplicitAlbum) {
        return "Go to $clean"
    }
    val canResolveAlbum = track.title.isNotBlank() && (
        track.id.startsWith("ytm_") ||
            track.audioUrl?.startsWith("http", ignoreCase = true) == true ||
            track.artist.isNotBlank()
        )
    return if (canResolveAlbum) "Go to Album" else null
}

@Composable
private fun TopQuickTile(
    icon: ImageVector,
    label: String,
    iconTint: Color,
    isHighlighted: Boolean,
    highlightColor: Color,
    appColors: AppThemeColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = when {
        isHighlighted -> highlightColor.copy(alpha = if (appColors.isDark) 0.16f else 0.10f)
        appColors.isDark -> Color(0x16FFFFFF)
        else -> Color(0x09000000)
    }
    val borderColor = when {
        isHighlighted -> highlightColor.copy(alpha = 0.55f)
        appColors.isDark -> Color(0x28FFFFFF)
        else -> Color(0x1C000000)
    }

    Column(
        modifier = modifier
            .clip(QuickTileShape)
            .background(bgColor)
            .border(1.dp, borderColor, QuickTileShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 13.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier.size(21.dp)
        )
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = label,
            fontFamily = GoogleSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = if (isHighlighted) highlightColor else appColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MenuListRow(
    icon: ImageVector,
    label: String,
    textColor: Color,
    iconTint: Color,
    badgeText: String? = null,
    badgeColor: Color = textColor,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(15.dp))
        Text(
            text = label,
            fontFamily = GoogleSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.5.sp,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (!badgeText.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = badgeText,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = badgeColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
