package com.xperience.player.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.ui.adaptive.LocalAppWindowSizeClass
import com.xperience.player.ui.adaptive.isExpandedWidth
import com.xperience.player.ui.adaptive.isMediumOrExpandedWidth
import com.xperience.player.ui.component.ExplicitBadge
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.PlusJakartaSans

@Composable
fun RecommendedSongsSection(
    tracks: List<TrackItem>,
    onTrackClick: (TrackItem) -> Unit,
    likedTrackIds: Set<String> = emptySet(),
    onToggleFavorite: ((TrackItem) -> Unit)? = null,
    modifier: Modifier = Modifier,
    onPlayTrackFromList: ((List<TrackItem>, Int) -> Unit)? = null
) {
    if (tracks.isEmpty()) return
    val appColors = LocalAppColors.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            text = "Recommended Songs",
            color = appColors.textPrimary,
            fontFamily = GoogleSans,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(14.dp))

        AdaptiveTrackRowsGrid(
            tracks = tracks,
            limit = 6,
            likedTrackIds = likedTrackIds,
            onTrackClick = onTrackClick,
            onToggleFavorite = onToggleFavorite,
            onPlayTrackFromList = onPlayTrackFromList
        )
    }
}

/**
 * Shared adaptive grid helper for short track lists inside vertically scrolling feeds:
 * 1 column on Compact phones, 2 columns on Medium portrait tablets, and 3 columns on Expanded screens.
 */
@Composable
internal fun AdaptiveTrackRowsGrid(
    tracks: List<TrackItem>,
    limit: Int = 6,
    likedTrackIds: Set<String> = emptySet(),
    onTrackClick: (TrackItem) -> Unit,
    onToggleFavorite: ((TrackItem) -> Unit)? = null,
    onPlayTrackFromList: ((List<TrackItem>, Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val windowSizeClass = LocalAppWindowSizeClass.current
    val columns = when {
        windowSizeClass.isExpandedWidth -> 3
        windowSizeClass.isMediumOrExpandedWidth -> 2
        else -> 1
    }
    val visibleTracks = tracks.take(limit)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (columns == 1) {
            visibleTracks.forEachIndexed { index, track ->
                RecommendedSongRow(
                    track = track,
                    isLiked = likedTrackIds.contains(track.id),
                    onClick = {
                        if (onPlayTrackFromList != null) {
                            onPlayTrackFromList(tracks, index)
                        } else {
                            onTrackClick(track)
                        }
                    },
                    onToggleFavorite = { onToggleFavorite?.invoke(track) }
                )
            }
        } else {
            visibleTracks.chunked(columns).forEachIndexed { chunkIdx, rowTracks ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowTracks.forEachIndexed { colIdx, track ->
                        val actualIndex = chunkIdx * columns + colIdx
                        RecommendedSongRow(
                            track = track,
                            isLiked = likedTrackIds.contains(track.id),
                            onClick = {
                                if (onPlayTrackFromList != null) {
                                    onPlayTrackFromList(tracks, actualIndex)
                                } else {
                                    onTrackClick(track)
                                }
                            },
                            onToggleFavorite = { onToggleFavorite?.invoke(track) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(columns - rowTracks.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun RecommendedSongRow(
    track: TrackItem,
    isLiked: Boolean = false,
    onClick: () -> Unit,
    onToggleFavorite: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val accentColor = LocalAccentColor.current
    val appColors = LocalAppColors.current
    val rowJelly = rememberAuroraJellyPressState(
        interactionSource = interactionSource,
        pressedScaleX = 0.978f,
        pressedScaleY = 0.958f,
        releaseVelocityX = 0.95f,
        releaseVelocityY = 0.55f,
        dampingRatio = 0.45f,
        stiffness = 440f
    )
    val playButtonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1.0f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = 460f),
        label = "songPlayButtonScale"
    )

    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = rowJelly.scaleX.value
                scaleY = rowJelly.scaleY.value
            }
            .clip(shape)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = if (appColors.isDark) {
                        listOf(
                            Color.White.copy(alpha = 0.22f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    } else {
                        listOf(
                            Color.Black.copy(alpha = 0.12f),
                            Color.Black.copy(alpha = 0.04f)
                        )
                    }
                ),
                shape = shape
            )
            .background(if (appColors.isDark) Color(0xFF16181F).copy(alpha = 0.72f) else Color(0xFFFFFFFF))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (appColors.isDark) Color(0xFF2A2E3D) else Color(0xFFE2E8F0)),
                contentAlignment = Alignment.Center
            ) {
                if (track.artworkUri != null) {
                    AsyncImage(
                        model = track.artworkUri,
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title & Artist
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = track.title,
                        color = appColors.textPrimary,
                        fontFamily = GoogleSans,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (track.isExplicit) {
                        ExplicitBadge()
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = track.artist,
                    color = appColors.textSecondary,
                    fontFamily = PlusJakartaSans,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Duration
            Text(
                text = formatDuration(track.durationMs),
                color = appColors.textTertiary,
                fontFamily = PlusJakartaSans,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Favorite Button
            if (onToggleFavorite != null) {
                val favInteraction = remember { MutableInteractionSource() }
                val favJelly = rememberAuroraJellyPressState(
                    interactionSource = favInteraction,
                    pressedScaleX = 0.86f,
                    pressedScaleY = 0.84f,
                    releaseVelocityX = 2.2f,
                    releaseVelocityY = 1.6f,
                    dampingRatio = 0.42f,
                    stiffness = 460f
                )
                val likePop = remember(track.id) { Animatable(1f) }
                var previousLiked by remember(track.id) { mutableStateOf(isLiked) }
                LaunchedEffect(isLiked) {
                    if (isLiked && !previousLiked) {
                        likePop.snapTo(1.28f)
                        likePop.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(dampingRatio = 0.42f, stiffness = 450f)
                        )
                    }
                    previousLiked = isLiked
                }

                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .graphicsLayer {
                            val pop = likePop.value
                            scaleX = favJelly.scaleX.value * pop
                            scaleY = favJelly.scaleY.value * pop
                        }
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = favInteraction,
                            indication = null,
                            onClick = { onToggleFavorite() }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isLiked) "Unlike" else "Like",
                        tint = if (isLiked) Color(0xFFFF2D55) else appColors.textTertiary,
                        modifier = Modifier.size(17.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))
            }

            // Play Circle Button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .graphicsLayer {
                        scaleX = playButtonScale
                        scaleY = playButtonScale
                    }
                    .shadow(elevation = 4.dp, shape = CircleShape, spotColor = accentColor.copy(alpha = 0.35f))
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                accentColor,
                                accentColor.copy(alpha = 0.75f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
