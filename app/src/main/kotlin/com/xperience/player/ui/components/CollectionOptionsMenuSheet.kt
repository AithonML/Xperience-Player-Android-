package com.xperience.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.xperience.player.domain.model.AlbumItem
import com.xperience.player.domain.model.ArtistItem
import com.xperience.player.ui.adaptive.AdaptiveModalContainer
import com.xperience.player.ui.adaptive.LocalAppWindowSizeClass
import com.xperience.player.ui.adaptive.shouldUseModalDialogs
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.PlusJakartaSans
import kotlinx.coroutines.launch

/**
 * Long-press / 3-dot context menu for [AlbumItem] cards across Home, Search, Artist & Library screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumOptionsMenuSheet(
    album: AlbumItem?,
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onOpenAlbum: (AlbumItem) -> Unit,
    onPlayAlbum: (AlbumItem) -> Unit,
    onShuffleAlbum: (AlbumItem) -> Unit,
    onPlayAlbumNext: (AlbumItem) -> Unit,
    onAddAlbumToQueue: (AlbumItem) -> Unit,
    onGoToArtist: ((String) -> Unit)? = null
) {
    if (!isOpen || album == null) return

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
        containerColor = if (appColors.isDark) Color(0xFF14161D) else Color(0xFFFFFFFF),
        dialogMaxWidth = 480.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = if (useModalDialog) 16.dp else 32.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF222632)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!album.coverUri.isNullOrBlank()) {
                        AsyncImage(
                            model = album.coverUri,
                            contentDescription = album.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Album,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = album.title,
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = appColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (album.artist.isNotBlank()) {
                        Text(
                            text = album.artist,
                            fontFamily = PlusJakartaSans,
                            fontSize = 12.sp,
                            color = appColors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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

            HorizontalDivider(color = appColors.cardBorder.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(6.dp))

            CollectionActionRow(
                icon = Icons.Default.PlayArrow,
                label = "Play Album",
                tint = appColors.textPrimary,
                onClick = { dismissThen { onPlayAlbum(album) } }
            )
            CollectionActionRow(
                icon = Icons.Default.Shuffle,
                label = "Shuffle Album",
                tint = appColors.textPrimary,
                onClick = { dismissThen { onShuffleAlbum(album) } }
            )
            CollectionActionRow(
                icon = Icons.Default.PlaylistPlay,
                label = "Play Next",
                tint = appColors.textPrimary,
                onClick = { dismissThen { onPlayAlbumNext(album) } }
            )
            CollectionActionRow(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                label = "Add to Queue",
                tint = appColors.textPrimary,
                onClick = { dismissThen { onAddAlbumToQueue(album) } }
            )
            CollectionActionRow(
                icon = Icons.Default.Album,
                label = "Go to Album",
                tint = accentColor,
                onClick = { dismissThen { onOpenAlbum(album) } }
            )
            if (album.artist.isNotBlank() && onGoToArtist != null) {
                CollectionActionRow(
                    icon = Icons.Default.Person,
                    label = "Go to ${album.artist}",
                    tint = appColors.textPrimary,
                    onClick = { dismissThen { onGoToArtist(album.artist) } }
                )
            }
        }
    }
}

/**
 * Long-press / 3-dot context menu for [ArtistItem] avatars across Home, Search, Artist & Library screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistOptionsMenuSheet(
    artist: ArtistItem?,
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onGoToArtist: (ArtistItem) -> Unit,
    onPlayTopSongs: (ArtistItem) -> Unit,
    onShuffleArtist: (ArtistItem) -> Unit
) {
    if (!isOpen || artist == null) return

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
        containerColor = if (appColors.isDark) Color(0xFF14161D) else Color(0xFFFFFFFF),
        dialogMaxWidth = 480.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = if (useModalDialog) 16.dp else 32.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF222632)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!artist.avatarUri.isNullOrBlank()) {
                        AsyncImage(
                            model = artist.avatarUri,
                            contentDescription = artist.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = artist.name,
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.5.sp,
                        color = appColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (artist.genre.isNotBlank()) {
                        Text(
                            text = artist.genre,
                            fontFamily = PlusJakartaSans,
                            fontSize = 12.sp,
                            color = appColors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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

            HorizontalDivider(color = appColors.cardBorder.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(6.dp))

            CollectionActionRow(
                icon = Icons.Default.Person,
                label = "Go to ${artist.name}",
                tint = accentColor,
                onClick = { dismissThen { onGoToArtist(artist) } }
            )
            CollectionActionRow(
                icon = Icons.Default.PlayArrow,
                label = "Play Top Songs",
                tint = appColors.textPrimary,
                onClick = { dismissThen { onPlayTopSongs(artist) } }
            )
            CollectionActionRow(
                icon = Icons.Default.Shuffle,
                label = "Shuffle ${artist.name}",
                tint = appColors.textPrimary,
                onClick = { dismissThen { onShuffleArtist(artist) } }
            )
        }
    }
}

@Composable
private fun CollectionActionRow(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(21.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            fontFamily = GoogleSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.5.sp,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
