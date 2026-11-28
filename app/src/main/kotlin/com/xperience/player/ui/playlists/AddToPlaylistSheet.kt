package com.xperience.player.ui.playlists

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.domain.model.UserPlaylist
import com.xperience.player.ui.adaptive.AdaptiveModalContainer
import com.xperience.player.ui.adaptive.LocalAppWindowSizeClass
import com.xperience.player.ui.adaptive.shouldUseModalDialogs
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.PlusJakartaSans
import com.xperience.player.ui.utils.pressWobble
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SheetItemShape = ContinuousRoundedRectangle(percent = 22)

/**
 * Bottom Sheet modal to add any song in the app to an existing playlist,
 * or immediately create a new playlist with that song.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    track: TrackItem?,
    playlists: List<UserPlaylist>,
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onSelectPlaylist: (playlist: UserPlaylist, track: TrackItem) -> Unit,
    onCreateNewPlaylistWithTrack: (track: TrackItem) -> Unit
) {
    if (!isOpen || track == null) return

    val appColors = LocalAppColors.current
    val accentColor = LocalAccentColor.current
    val useModalDialog = LocalAppWindowSizeClass.current.shouldUseModalDialogs
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    var addedFeedbackMessage by remember { mutableStateOf<String?>(null) }

    val dismissSheet: (delayMs: Long) -> Unit = { delayMs ->
        coroutineScope.launch {
            if (delayMs > 0L) delay(delayMs)
            if (!useModalDialog) sheetState.hide()
            onDismiss()
        }
    }

    AdaptiveModalContainer(
        isOpen = true,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (appColors.isDark) Color(0xFF13151C) else Color(0xFFFFFFFF),
        dialogMaxWidth = 520.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = if (useModalDialog) 12.dp else 36.dp)
        ) {
            // Header Row: Title & Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Add to Playlist",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    color = appColors.textPrimary
                )

                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(if (appColors.isDark) Color(0x20FFFFFF) else Color(0x10000000))
                        .clickable { dismissSheet(0L) },
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

            Spacer(modifier = Modifier.height(14.dp))

            // Track summary card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (appColors.isDark) Color(0x1EFFFFFF) else Color(0x0A000000))
                    .border(0.8.dp, if (appColors.isDark) Color(0x15FFFFFF) else Color(0x10000000), RoundedCornerShape(14.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
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
                        fontSize = 13.5.sp,
                        color = appColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = track.artist,
                        fontFamily = PlusJakartaSans,
                        fontSize = 11.5.sp,
                        color = appColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Feedback banner
            AnimatedVisibility(
                visible = addedFeedbackMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.15f))
                        .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = addedFeedbackMessage.orEmpty(),
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                        color = accentColor
                    )
                }
            }

            // Option 1: Create New Playlist
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SheetItemShape)
                    .background(if (appColors.isDark) Color(0x18FFFFFF) else Color(0x08000000))
                    .border(1.dp, accentColor.copy(alpha = 0.35f), SheetItemShape)
                    .clickable {
                        onCreateNewPlaylistWithTrack(track)
                        onDismiss()
                    }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Playlist",
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = "New Playlist",
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = appColors.textPrimary
                    )
                    Text(
                        text = "Create a new playlist with this track",
                        fontFamily = PlusJakartaSans,
                        fontSize = 11.5.sp,
                        color = appColors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // List of Existing Playlists
            if (playlists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No playlists created yet.",
                        fontFamily = PlusJakartaSans,
                        fontSize = 13.sp,
                        color = appColors.textSecondary
                    )
                }
            } else {
                Text(
                    text = "YOUR PLAYLISTS",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = appColors.textSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(playlists, key = { it.id }, contentType = { "playlist_option" }) { playlist ->
                        val isAlreadyInPlaylist = playlist.tracks.any { it.id == track.id }
                        val interactionSource = remember { MutableInteractionSource() }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pressWobble(interactionSource)
                                .clip(SheetItemShape)
                                .background(if (appColors.isDark) Color(0x18FFFFFF) else Color(0x06000000))
                                .border(
                                    0.8.dp,
                                    if (isAlreadyInPlaylist) accentColor.copy(alpha = 0.5f)
                                    else if (appColors.isDark) Color(0x12FFFFFF) else Color(0x0E000000),
                                    SheetItemShape
                                )
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = {
                                        onSelectPlaylist(playlist, track)
                                        addedFeedbackMessage = if (isAlreadyInPlaylist) {
                                            "Already in ${playlist.title}"
                                        } else {
                                            "Added to ${playlist.title}!"
                                        }
                                        dismissSheet(700L)
                                    }
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlaylistCoverImage(
                                playlist = playlist,
                                modifier = Modifier.size(46.dp),
                                shape = RoundedCornerShape(10.dp),
                                fallbackIconSize = 22.dp
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = playlist.title,
                                    fontFamily = GoogleSans,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = appColors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = playlist.summarySubtitle,
                                    fontFamily = PlusJakartaSans,
                                    fontSize = 11.5.sp,
                                    color = appColors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (isAlreadyInPlaylist) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(accentColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "In Playlist",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
