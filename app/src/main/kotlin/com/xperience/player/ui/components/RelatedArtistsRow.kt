package com.xperience.player.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xperience.player.domain.model.ArtistItem
import com.xperience.player.service.YouTubeMusicService
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAppColors

@Composable
fun RelatedArtistsRow(
    artists: List<ArtistItem>,
    onArtistClick: (ArtistItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (artists.isEmpty()) return
    val appColors = LocalAppColors.current

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = "Related Artists For You",
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
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            artists.forEach { artist ->
                ArtistCircleItem(
                    artist = artist,
                    onClick = { onArtistClick(artist) }
                )
            }
        }
    }
}

@Composable
private fun ArtistCircleItem(
    artist: ArtistItem,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var avatarUriState by remember(artist.id, artist.name, artist.avatarUri) {
        mutableStateOf(artist.avatarUri)
    }

    LaunchedEffect(artist.name) {
        if (avatarUriState.isNullOrBlank()) {
            val ytm = YouTubeMusicService.getInstance()
            val resolved = ytm.getArtistAvatarFromCache(artist.name, context)
                ?: ytm.getArtistAvatarUri(artist.name, context)
            if (!resolved.isNullOrBlank()) {
                avatarUriState = resolved
            }
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val jelly = rememberAuroraJellyPressState(
        interactionSource = interactionSource,
        pressedScaleX = 0.945f,
        pressedScaleY = 0.92f,
        releaseVelocityX = 1.85f,
        releaseVelocityY = 0.95f,
        dampingRatio = 0.42f,
        stiffness = 420f
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    ) {
        Box(
            modifier = Modifier
                .size(135.dp)
                .graphicsLayer {
                    scaleX = jelly.scaleX.value
                    scaleY = jelly.scaleY.value
                }
                .shadow(
                    elevation = 6.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.4f),
                    spotColor = Color.Black.copy(alpha = 0.5f)
                )
                .clip(CircleShape)
                .background(Color(0xFF222632)),
            contentAlignment = Alignment.Center
        ) {
            if (artist.avatarResId != null) {
                Image(
                    painter = painterResource(id = artist.avatarResId),
                    contentDescription = artist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            } else if (!avatarUriState.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUriState,
                    contentDescription = artist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color(0xFF6B7280),
                    modifier = Modifier.size(54.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val appColors = LocalAppColors.current

        Text(
            text = artist.name,
            color = appColors.textPrimary,
            fontFamily = GoogleSans,
            fontWeight = FontWeight.Medium,
            fontSize = 13.5.sp,
            maxLines = 1,
            modifier = Modifier.graphicsLayer {
                val followScale = 1f + (jelly.scaleY.value - 1f) * 0.4f
                scaleX = followScale
                scaleY = followScale
                translationY = (1f - jelly.scaleY.value) * 4.dp.toPx()
            }
        )
    }
}
