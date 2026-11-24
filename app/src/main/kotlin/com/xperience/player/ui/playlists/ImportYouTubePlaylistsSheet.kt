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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xperience.player.data.PlaylistRepository
import com.xperience.player.domain.model.UserPlaylist
import com.xperience.player.domain.model.YouTubeMusicLibraryPlaylist
import com.xperience.player.ui.adaptive.AdaptiveModalContainer
import com.xperience.player.ui.adaptive.LocalAppWindowSizeClass
import com.xperience.player.ui.adaptive.shouldUseModalDialogs
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.components.onAccentColor
import com.xperience.player.ui.components.readableAccentColor
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.PlusJakartaSans
import com.xperience.player.ui.utils.pressWobble

private val ImportRowShape = ContinuousRoundedRectangle(size = 15.dp)
private val ImportPillShape = ContinuousRoundedRectangle(percent = 50)
private val ImportThumbShape = ContinuousRoundedRectangle(size = 11.dp)

internal val YouTubeMusicBrandRedDark = Color(0xFFFF2D55)
internal val YouTubeMusicBrandRedLight = Color(0xFFD90429)

/**
 * Adaptive bottom-sheet / modal dialog for choosing and importing playlists from the signed-in
 * user's YouTube Music library into Xperience Player's local Playlists tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportYouTubePlaylistsSheet(
    isOpen: Boolean,
    isLoading: Boolean,
    libraryPlaylists: List<YouTubeMusicLibraryPlaylist>,
    selectedIds: Set<String>,
    existingLocalPlaylists: List<UserPlaylist>,
    isImporting: Boolean,
    progressText: String?,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onImportClick: () -> Unit
) {
    if (!isOpen) return

    val appColors = LocalAppColors.current
    val isDark = appColors.isDark
    val accentColor = readableAccentColor(LocalAccentColor.current, isDark)
    val ytmRed = if (isDark) YouTubeMusicBrandRedDark else YouTubeMusicBrandRedLight
    val useModalDialog = LocalAppWindowSizeClass.current.shouldUseModalDialogs
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val importedExternalIds = remember(existingLocalPlaylists) {
        existingLocalPlaylists
            .mapNotNull { playlist ->
                playlist.externalPlaylistId
                    .takeIf { it.isNotBlank() }
                    ?.let { PlaylistRepository.normalizeYouTubePlaylistId(it).lowercase() }
            }
            .toSet()
    }

    AdaptiveModalContainer(
        isOpen = true,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (isDark) Color(0xFF13151C) else Color(0xFFFFFFFF),
        dialogMaxWidth = 560.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = if (useModalDialog) 14.dp else 30.dp)
        ) {
            // Header row with YouTube Music badge, title, and close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(ytmRed.copy(alpha = if (isDark) 0.20f else 0.12f))
                            .border(1.dp, ytmRed.copy(alpha = 0.45f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayCircle,
                            contentDescription = null,
                            tint = ytmRed,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Import YouTube Music Playlists",
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = appColors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color(0x20FFFFFF) else Color(0x10000000))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = appColors.textSecondary,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Selected playlists are copied into your Playlists tab. Removing them later only deletes them from Xperience Player and never modifies YouTube Music.",
                fontFamily = PlusJakartaSans,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = appColors.textSecondary
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Main body content: Loading / Error / Empty / Selection list
            when {
                isLoading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = ytmRed,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Loading your YouTube Music playlists…",
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.5.sp,
                            color = appColors.textSecondary
                        )
                    }
                }

                errorMessage != null && libraryPlaylists.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ImportRowShape)
                            .background(if (isDark) Color(0x18FF5252) else Color(0x12D32F2F))
                            .border(
                                1.dp,
                                if (isDark) Color(0x45FF5252) else Color(0x35D32F2F),
                                ImportRowShape
                            )
                            .padding(horizontal = 18.dp, vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = ytmRed,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = errorMessage,
                            fontFamily = PlusJakartaSans,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = appColors.textPrimary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(ImportPillShape)
                                .background(ytmRed.copy(alpha = if (isDark) 0.22f else 0.12f))
                                .border(1.dp, ytmRed.copy(alpha = 0.45f), ImportPillShape)
                                .clickable(onClick = onRetry)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                tint = ytmRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Retry",
                                fontFamily = GoogleSans,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = ytmRed
                            )
                        }
                    }
                }

                libraryPlaylists.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ImportRowShape)
                            .background(if (isDark) Color(0x12FFFFFF) else Color(0x08000000))
                            .border(
                                1.dp,
                                if (isDark) Color(0x18FFFFFF) else Color(0x12000000),
                                ImportRowShape
                            )
                            .padding(horizontal = 18.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                            contentDescription = null,
                            tint = appColors.textSecondary,
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No YouTube Music playlists found",
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = appColors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Playlists you create or save on YouTube Music will appear here.",
                            fontFamily = PlusJakartaSans,
                            fontSize = 12.5.sp,
                            color = appColors.textSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(ImportPillShape)
                                .background(accentColor.copy(alpha = 0.14f))
                                .border(1.dp, accentColor.copy(alpha = 0.35f), ImportPillShape)
                                .clickable(onClick = onRetry)
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Refresh",
                                fontFamily = GoogleSans,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.5.sp,
                                color = accentColor
                            )
                        }
                    }
                }

                else -> {
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage,
                            fontFamily = PlusJakartaSans,
                            fontSize = 12.sp,
                            color = ytmRed,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        )
                    }

                    // Selection toolbar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${selectedIds.size} of ${libraryPlaylists.size} selected",
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp,
                            color = appColors.textSecondary
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Select all",
                                fontFamily = GoogleSans,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.5.sp,
                                color = accentColor,
                                modifier = Modifier
                                    .alpha(if (isImporting) 0.4f else 1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(enabled = !isImporting, onClick = onSelectAll)
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                            Text(
                                text = "Clear",
                                fontFamily = GoogleSans,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.5.sp,
                                color = appColors.textSecondary,
                                modifier = Modifier
                                    .alpha(if (isImporting || selectedIds.isEmpty()) 0.4f else 1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(
                                        enabled = !isImporting && selectedIds.isNotEmpty(),
                                        onClick = onClearSelection
                                    )
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Scrollable playlist picker list
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 360.dp)
                    ) {
                        items(
                            items = libraryPlaylists,
                            key = { it.playlistId }
                        ) { playlist ->
                            val isSelected = playlist.playlistId in selectedIds
                            val isAlreadyImported = PlaylistRepository.normalizeYouTubePlaylistId(
                                playlist.playlistId
                            ).lowercase() in importedExternalIds

                            YtmImportPlaylistRow(
                                playlist = playlist,
                                isSelected = isSelected,
                                isAlreadyImported = isAlreadyImported,
                                isEnabled = !isImporting,
                                accentColor = accentColor,
                                ytmRed = ytmRed,
                                onClick = { onToggleSelection(playlist.playlistId) }
                            )
                        }
                    }
                }
            }

            // Progress indicator while importing
            AnimatedVisibility(
                visible = isImporting,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                ) {
                    LinearProgressIndicator(
                        color = ytmRed,
                        trackColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = progressText ?: "Importing playlists…",
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = appColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Footer action buttons
            val importEnabled = selectedIds.isNotEmpty() && !isImporting && !isLoading
            val cancelInteraction = remember { MutableInteractionSource() }
            val importInteraction = remember { MutableInteractionSource() }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .pressWobble(cancelInteraction, pressedScale = 0.94f)
                        .clip(ImportPillShape)
                        .background(if (isDark) Color(0x18FFFFFF) else Color(0x0E000000))
                        .border(
                            0.85.dp,
                            if (isDark) Color(0x22FFFFFF) else Color(0x16000000),
                            ImportPillShape
                        )
                        .clickable(
                            interactionSource = cancelInteraction,
                            indication = null,
                            onClick = onDismiss
                        )
                ) {
                    Text(
                        text = if (isImporting) "Cancel" else "Not Now",
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = appColors.textPrimary
                    )
                }

                val primaryInk = onAccentColor(accentColor)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1.35f)
                        .height(44.dp)
                        .alpha(if (importEnabled || isImporting) 1f else 0.42f)
                        .pressWobble(importInteraction, pressedScale = 0.94f)
                        .clip(ImportPillShape)
                        .background(
                            Brush.linearGradient(
                                listOf(accentColor, accentColor.copy(alpha = 0.85f))
                            )
                        )
                        .border(
                            0.85.dp,
                            Color.White.copy(alpha = if (isDark) 0.25f else 0.40f),
                            ImportPillShape
                        )
                        .clickable(
                            interactionSource = importInteraction,
                            indication = null,
                            enabled = importEnabled,
                            onClick = onImportClick
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(
                                color = primaryInk,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Importing…",
                                fontFamily = GoogleSans,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = primaryInk
                            )
                        } else {
                            val label = if (selectedIds.isNotEmpty()) {
                                "Import (${selectedIds.size})"
                            } else {
                                "Import"
                            }
                            Text(
                                text = label,
                                fontFamily = GoogleSans,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = primaryInk
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YtmImportPlaylistRow(
    playlist: YouTubeMusicLibraryPlaylist,
    isSelected: Boolean,
    isAlreadyImported: Boolean,
    isEnabled: Boolean,
    accentColor: Color,
    ytmRed: Color,
    onClick: () -> Unit
) {
    val appColors = LocalAppColors.current
    val isDark = appColors.isDark

    val rowBackground = when {
        isSelected -> accentColor.copy(alpha = if (isDark) 0.16f else 0.10f)
        isDark -> Color.White.copy(alpha = 0.05f)
        else -> Color.Black.copy(alpha = 0.035f)
    }
    val rowBorder = when {
        isSelected -> accentColor.copy(alpha = 0.55f)
        isDark -> Color.White.copy(alpha = 0.10f)
        else -> Color.Black.copy(alpha = 0.08f)
    }

    val subtitleText = remember(playlist) {
        val parts = buildList {
            if (playlist.author.isNotBlank()) add(playlist.author)
            if (playlist.trackCountText.isNotBlank()) add(playlist.trackCountText)
        }
        when {
            parts.isNotEmpty() -> parts.joinToString(" • ")
            playlist.subtitle.isNotBlank() -> playlist.subtitle
            else -> "YouTube Music playlist"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ImportRowShape)
            .background(rowBackground)
            .border(0.9.dp, rowBorder, ImportRowShape)
            .clickable(enabled = isEnabled, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Artwork thumbnail
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(ImportThumbShape)
                .background(if (isDark) Color(0xFF222634) else Color(0xFFE7EBF3))
        ) {
            if (playlist.artworkUri.isNotBlank()) {
                AsyncImage(
                    model = playlist.artworkUri,
                    contentDescription = playlist.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    contentDescription = null,
                    tint = ytmRed,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title, Imported chip, and subtitle
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = playlist.title,
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = appColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (isAlreadyImported) {
                    Box(
                        modifier = Modifier
                            .clip(ImportPillShape)
                            .background(ytmRed.copy(alpha = if (isDark) 0.22f else 0.12f))
                            .border(0.7.dp, ytmRed.copy(alpha = 0.45f), ImportPillShape)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Imported",
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = ytmRed
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitleText,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = appColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Selection indicator circle
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (isSelected) accentColor else Color.Transparent)
                .border(
                    width = if (isSelected) 0.dp else 1.5.dp,
                    color = if (isSelected) Color.Transparent else appColors.textSecondary.copy(alpha = 0.45f),
                    shape = CircleShape
                )
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Selected",
                    tint = onAccentColor(accentColor),
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}
