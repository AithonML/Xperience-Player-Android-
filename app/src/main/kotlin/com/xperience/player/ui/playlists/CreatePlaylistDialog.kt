package com.xperience.player.ui.playlists

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.xperience.player.domain.model.UserPlaylist
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.PlusJakartaSans
import com.xperience.player.ui.utils.pressWobble

private val DialogShape = ContinuousRoundedRectangle(percent = 18)
private val PreviewCoverShape = ContinuousRoundedRectangle(percent = 22)
private val ButtonPillShape = ContinuousRoundedRectangle(percent = 50)

/**
 * Dialog for creating a new playlist or editing an existing playlist.
 * Lets the user customize Title, Description, and a custom Cover photo from the device gallery.
 */
@Composable
fun CreatePlaylistDialog(
    isOpen: Boolean,
    playlistToEdit: UserPlaylist? = null,
    onDismiss: () -> Unit,
    onConfirm: (title: String, description: String, customCoverPath: String) -> Unit
) {
    if (!isOpen) return

    val appColors = LocalAppColors.current
    val accentColor = LocalAccentColor.current

    var title by remember(playlistToEdit) { mutableStateOf(playlistToEdit?.title ?: "") }
    var description by remember(playlistToEdit) { mutableStateOf(playlistToEdit?.description ?: "") }
    var customCoverPath by remember(playlistToEdit) { mutableStateOf(playlistToEdit?.customCoverImagePath ?: "") }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            customCoverPath = uri.toString()
        }
    }

    val isEditing = playlistToEdit != null
    val isConfirmEnabled = title.isNotBlank()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 500.dp)
                .fillMaxWidth()
                .shadow(
                    elevation = 24.dp,
                    shape = DialogShape,
                    ambientColor = Color.Black.copy(alpha = if (appColors.isDark) 0.5f else 0.15f),
                    spotColor = Color.Black.copy(alpha = if (appColors.isDark) 0.7f else 0.25f)
                )
                .clip(DialogShape)
                .background(if (appColors.isDark) Color(0xFF16181F) else Color(0xFFFFFFFF))
                .border(
                    width = 1.2.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (appColors.isDark) 0.3f else 0.8f),
                            Color.White.copy(alpha = if (appColors.isDark) 0.08f else 0.25f)
                        )
                    ),
                    shape = DialogShape
                )
                .padding(22.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Row: Title + Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEditing) "Edit Playlist" else "New Playlist",
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = appColors.textPrimary
                    )

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (appColors.isDark) Color(0x20FFFFFF) else Color(0x10000000))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = appColors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Cover preview & photo picker
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(PreviewCoverShape)
                            .background(if (appColors.isDark) Color(0xFF222632) else Color(0xFFE9EDF4))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), PreviewCoverShape)
                            .clickable { galleryLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (customCoverPath.isNotBlank()) {
                            AsyncImage(
                                model = customCoverPath,
                                contentDescription = "Playlist Cover",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (playlistToEdit != null && playlistToEdit.tracks.isNotEmpty()) {
                            PlaylistCoverImage(
                                playlist = playlistToEdit,
                                modifier = Modifier.fillMaxSize(),
                                shape = PreviewCoverShape,
                                fallbackIconSize = 28.dp
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(26.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "2×2 Grid",
                                    fontFamily = PlusJakartaSans,
                                    fontSize = 10.sp,
                                    color = appColors.textSecondary
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (customCoverPath.isNotBlank()) "Custom cover photo active" else "Automatic 2×2 cover collage",
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = appColors.textPrimary
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = if (customCoverPath.isNotBlank()) "Tap to change photo" else "Leave empty to collage song covers, or pick a custom photo.",
                            fontFamily = PlusJakartaSans,
                            fontSize = 11.5.sp,
                            color = appColors.textSecondary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Pick button
                            Box(
                                modifier = Modifier
                                    .clip(ButtonPillShape)
                                    .background(accentColor.copy(alpha = 0.15f))
                                    .border(1.dp, accentColor.copy(alpha = 0.4f), ButtonPillShape)
                                    .clickable { galleryLauncher.launch("image/*") }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (customCoverPath.isNotBlank()) "Change" else "Pick Photo",
                                    fontFamily = GoogleSans,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = accentColor
                                )
                            }

                            // Remove custom cover button (restores 2x2 grid)
                            if (customCoverPath.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .clip(ButtonPillShape)
                                        .background(if (appColors.isDark) Color(0x20FFFFFF) else Color(0x10000000))
                                        .clickable { customCoverPath = "" }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "Reset 2×2",
                                        fontFamily = GoogleSans,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = appColors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Title Input Field
                Text(
                    text = "TITLE",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.5.sp,
                    color = appColors.textSecondary,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (appColors.isDark) Color(0x25FFFFFF) else Color(0x0C000000))
                        .border(1.dp, if (appColors.isDark) Color(0x20FFFFFF) else Color(0x15000000), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    if (title.isEmpty()) {
                        Text(
                            text = "Playlist Title (e.g. Late Night Synthwave)",
                            fontFamily = PlusJakartaSans,
                            fontSize = 13.5.sp,
                            color = appColors.textSecondary.copy(alpha = 0.6f)
                        )
                    }
                    BasicTextField(
                        value = title,
                        onValueChange = { title = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontFamily = PlusJakartaSans,
                            fontSize = 13.5.sp,
                            color = appColors.textPrimary
                        ),
                        cursorBrush = SolidColor(accentColor),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Description Input Field
                Text(
                    text = "DESCRIPTION (OPTIONAL)",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.5.sp,
                    color = appColors.textSecondary,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (appColors.isDark) Color(0x25FFFFFF) else Color(0x0C000000))
                        .border(1.dp, if (appColors.isDark) Color(0x20FFFFFF) else Color(0x15000000), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    if (description.isEmpty()) {
                        Text(
                            text = "Add an optional description...",
                            fontFamily = PlusJakartaSans,
                            fontSize = 13.sp,
                            color = appColors.textSecondary.copy(alpha = 0.6f)
                        )
                    }
                    BasicTextField(
                        value = description,
                        onValueChange = { description = it },
                        singleLine = false,
                        maxLines = 3,
                        textStyle = TextStyle(
                            fontFamily = PlusJakartaSans,
                            fontSize = 13.sp,
                            color = appColors.textPrimary
                        ),
                        cursorBrush = SolidColor(accentColor),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons: Cancel & Confirm
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(ButtonPillShape)
                            .background(if (appColors.isDark) Color(0x20FFFFFF) else Color(0x10000000))
                            .clickable { onDismiss() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cancel",
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp,
                            color = appColors.textSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1.5f)
                            .clip(ButtonPillShape)
                            .background(
                                if (isConfirmEnabled) accentColor else accentColor.copy(alpha = 0.35f)
                            )
                            .clickable(enabled = isConfirmEnabled) {
                                onConfirm(title.trim(), description.trim(), customCoverPath.trim())
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isEditing) "Save Changes" else "Create Playlist",
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
