package com.xperience.player.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xperience.player.domain.model.AlbumItem
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.PlusJakartaSans

@Composable
fun RecommendedAlbumsSection(
    albums: List<AlbumItem>,
    onAlbumClick: (AlbumItem) -> Unit,
    modifier: Modifier = Modifier,
    onAlbumLongPress: ((AlbumItem) -> Unit)? = null
) {
    if (albums.isEmpty()) return
    val appColors = LocalAppColors.current

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = "Recommended Albums",
            color = appColors.textPrimary,
            fontFamily = GoogleSans,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            albums.take(12).forEach { album ->
                AlbumCard(
                    album = album,
                    onClick = { onAlbumClick(album) },
                    onLongClick = onAlbumLongPress?.let { cb -> { cb(album) } }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumCard(
    album: AlbumItem,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val jelly = rememberAuroraJellyPressState(
        interactionSource = interactionSource,
        pressedScaleX = 0.945f,
        pressedScaleY = 0.92f,
        releaseVelocityX = 1.80f,
        releaseVelocityY = 0.90f,
        dampingRatio = 0.42f,
        stiffness = 420f
    )

    val shape = RoundedCornerShape(18.dp)

    Column(
        modifier = Modifier
            .width(132.dp)
            .graphicsLayer {
                scaleX = jelly.scaleX.value
                scaleY = jelly.scaleY.value
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick?.let { cb ->
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        cb()
                    }
                }
            )
    ) {
        Box(
            modifier = Modifier
                .size(132.dp)
                .shadow(
                    elevation = 6.dp,
                    shape = shape,
                    ambientColor = Color.Black.copy(alpha = 0.4f),
                    spotColor = Color.Black.copy(alpha = 0.5f)
                )
                .clip(shape)
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.22f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    ),
                    shape = shape
                )
                .background(Color(0xFF222632)),
            contentAlignment = Alignment.Center
        ) {
            if (album.coverUri != null) {
                AsyncImage(
                    model = album.coverUri,
                    contentDescription = album.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Album,
                    contentDescription = null,
                    tint = Color(0xFF6B7280),
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        val appColors = LocalAppColors.current

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = album.title,
            color = appColors.textPrimary,
            fontFamily = GoogleSans,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = album.artist,
            color = appColors.textSecondary,
            fontFamily = PlusJakartaSans,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
