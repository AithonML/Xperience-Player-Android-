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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xperience.player.R
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.ui.adaptive.AdaptiveModalContainer
import com.xperience.player.ui.adaptive.LocalAppWindowSizeClass
import com.xperience.player.ui.adaptive.shouldUseModalDialogs
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.PlusJakartaSans
import com.xperience.player.ui.utils.pressWobble
import kotlinx.coroutines.launch

private val OptionRowShape = ContinuousRoundedRectangle(percent = 22)

/**
 * Universal Track Options Bottom Sheet.
 * Displays track metadata and essential actions:
 * 1. Add to Playlist
 * 2. Download Offline
 * 3. Toggle Favorite
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
    onSetAsRingtone: ((TrackItem) -> Unit)? = null
) {
    if (!isOpen || track == null) return

    val appColors = LocalAppColors.current
    val accentColor = LocalAccentColor.current
    val useModalDialog = LocalAppWindowSizeClass.current.shouldUseModalDialogs
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
        containerColor = if (appColors.isDark) Color(0xFF13151C) else Color(0xFFFFFFFF),
        dialogMaxWidth = 500.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = if (useModalDialog) 12.dp else 36.dp)
        ) {
            // Track Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (appColors.isDark) Color(0x18FFFFFF) else Color(0x0A000000))
                    .border(0.8.dp, if (appColors.isDark) Color(0x15FFFFFF) else Color(0x10000000), RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
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
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

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
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${track.artist} • ${track.album}",
                        fontFamily = PlusJakartaSans,
                        fontSize = 11.5.sp,
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
                        .clickable { dismissThen {} },
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

            Spacer(modifier = Modifier.height(18.dp))

            // Action 1: Add to Playlist
            TrackOptionActionRow(
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                iconTint = accentColor,
                title = "Add to Playlist",
                subtitle = "Save to an existing playlist or create a new one",
                onClick = { dismissThen { onAddToPlaylist(track) } }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Action 2: Download Offline
            TrackOptionActionRow(
                icon = Icons.Default.Download,
                iconTint = Color(0xFF34C759),
                title = "Download Song",
                subtitle = "Save audio file locally with custom tags & cover",
                onClick = { dismissThen { onDownload(track) } }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Action 3: Set as Ringtone
            if (onSetAsRingtone != null) {
                TrackOptionActionRow(
                    icon = Icons.Default.NotificationsActive,
                    iconTint = Color(0xFFFF9F0A),
                    title = stringResource(R.string.set_as_ringtone),
                    subtitle = stringResource(R.string.set_as_ringtone_subtitle),
                    onClick = { dismissThen { onSetAsRingtone(track) } }
                )

                Spacer(modifier = Modifier.height(10.dp))
            }

            // Action 4: Like / Favorite
            TrackOptionActionRow(
                icon = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                iconTint = if (isLiked) Color(0xFFFF2D55) else appColors.textSecondary,
                title = if (isLiked) "Remove from Liked Songs" else "Add to Liked Songs",
                subtitle = if (isLiked) "Song is currently saved in favorites" else "Save this track to your Liked collection",
                onClick = {
                    onToggleFavorite(track)
                }
            )
        }
    }
}

@Composable
private fun TrackOptionActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val appColors = LocalAppColors.current
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressWobble(interactionSource)
            .clip(OptionRowShape)
            .background(if (appColors.isDark) Color(0x18FFFFFF) else Color(0x06000000))
            .border(0.8.dp, if (appColors.isDark) Color(0x12FFFFFF) else Color(0x0E000000), OptionRowShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconTint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = appColors.textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontFamily = PlusJakartaSans,
                fontSize = 11.5.sp,
                color = appColors.textSecondary
            )
        }
    }
}
