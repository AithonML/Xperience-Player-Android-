package com.xperience.player.ui.playlists

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xperience.player.domain.model.UserPlaylist
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.PlusJakartaSans
import com.xperience.player.ui.utils.pressWobble

private val ConfirmDialogShape = ContinuousRoundedRectangle(percent = 18)
private val ConfirmPillShape = ContinuousRoundedRectangle(percent = 50)

/**
 * Confirmation dialog for removing a playlist locally from Xperience Player.
 * Clearly reassures the user that removing an imported YouTube Music playlist
 * only deletes it from the app and never modifies their YouTube Music library.
 */
@Composable
fun RemovePlaylistDialog(
    playlist: UserPlaylist?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    if (playlist == null) return

    val appColors = LocalAppColors.current
    val isDark = appColors.isDark
    val destructiveColor = if (isDark) Color(0xFFFF5A6E) else Color(0xFFD92D20)
    val ytmRed = if (isDark) YouTubeMusicBrandRedDark else YouTubeMusicBrandRedLight

    val titleText = if (playlist.isFromYouTubeMusic) "Remove from App?" else "Delete Playlist?"
    val bodyText = if (playlist.isFromYouTubeMusic) {
        "\"${playlist.title}\" will be removed from Xperience Player. This won't affect your YouTube Music library."
    } else {
        "Delete \"${playlist.title}\"? Your music files won't be affected."
    }
    val confirmLabel = if (playlist.isFromYouTubeMusic) "Remove" else "Delete"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .shadow(
                    elevation = 24.dp,
                    shape = ConfirmDialogShape,
                    ambientColor = Color.Black.copy(alpha = if (isDark) 0.5f else 0.15f),
                    spotColor = Color.Black.copy(alpha = if (isDark) 0.7f else 0.25f)
                )
                .clip(ConfirmDialogShape)
                .background(if (isDark) Color(0xFF16181F) else Color(0xFFFFFFFF))
                .border(
                    width = 1.2.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (isDark) 0.28f else 0.80f),
                            Color.White.copy(alpha = if (isDark) 0.08f else 0.25f)
                        )
                    ),
                    shape = ConfirmDialogShape
                )
                .padding(22.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(destructiveColor.copy(alpha = if (isDark) 0.18f else 0.11f))
                            .border(1.dp, destructiveColor.copy(alpha = 0.40f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            tint = destructiveColor,
                            modifier = Modifier.size(21.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = titleText,
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            color = appColors.textPrimary
                        )
                        if (playlist.isFromYouTubeMusic) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayCircle,
                                    contentDescription = null,
                                    tint = ytmRed,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "YouTube Music import • Local only",
                                    fontFamily = PlusJakartaSans,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.5.sp,
                                    color = ytmRed
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = bodyText,
                    fontFamily = PlusJakartaSans,
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp,
                    color = appColors.textSecondary
                )

                Spacer(modifier = Modifier.height(22.dp))

                val cancelInteraction = remember { MutableInteractionSource() }
                val confirmInteraction = remember { MutableInteractionSource() }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .pressWobble(cancelInteraction, pressedScale = 0.94f)
                            .clip(ConfirmPillShape)
                            .background(if (isDark) Color(0x18FFFFFF) else Color(0x0E000000))
                            .border(
                                0.85.dp,
                                if (isDark) Color(0x22FFFFFF) else Color(0x16000000),
                                ConfirmPillShape
                            )
                            .clickable(
                                interactionSource = cancelInteraction,
                                indication = null,
                                onClick = onDismiss
                            )
                    ) {
                        Text(
                            text = "Cancel",
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = appColors.textPrimary
                        )
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .pressWobble(confirmInteraction, pressedScale = 0.94f)
                            .clip(ConfirmPillShape)
                            .background(destructiveColor)
                            .clickable(
                                interactionSource = confirmInteraction,
                                indication = null,
                                onClick = onConfirm
                            )
                    ) {
                        Text(
                            text = confirmLabel,
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
