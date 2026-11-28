package com.xperience.player.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xperience.player.domain.model.AlbumItem
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.ui.liquidglass.LiquidGlassPill
import com.xperience.player.ui.theme.ArtworkColorExtractor
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.PlusJakartaSans

private sealed class BentoColumn {
    data class TwoCards(val top: TrackItem, val bottom: TrackItem, val reverseHeights: Boolean = false) : BentoColumn()
    data class ThreeCards(val top: TrackItem, val middle: TrackItem, val bottom: TrackItem, val reverseHeights: Boolean = false) : BentoColumn()
    data class SingleCard(val track: TrackItem) : BentoColumn()
}

private fun buildBentoColumns(tracks: List<TrackItem>): List<BentoColumn> {
    if (tracks.isEmpty()) return emptyList()
    val columns = mutableListOf<BentoColumn>()
    var index = 0
    var wantTwo = true

    while (index < tracks.size) {
        val remaining = tracks.size - index
        if (wantTwo) {
            if (remaining >= 2) {
                val reverse = (columns.size / 2) % 2 == 1
                columns.add(BentoColumn.TwoCards(tracks[index], tracks[index + 1], reverseHeights = reverse))
                index += 2
                wantTwo = false
            } else {
                columns.add(BentoColumn.SingleCard(tracks[index]))
                index += 1
            }
        } else {
            if (remaining >= 3) {
                val reverse = (columns.size / 2) % 2 == 1
                columns.add(BentoColumn.ThreeCards(tracks[index], tracks[index + 1], tracks[index + 2], reverseHeights = reverse))
                index += 3
                wantTwo = true
            } else if (remaining == 2) {
                columns.add(BentoColumn.TwoCards(tracks[index], tracks[index + 1]))
                index += 2
                wantTwo = false
            } else {
                columns.add(BentoColumn.SingleCard(tracks[index]))
                index += 1
            }
        }
    }
    return columns
}

fun trackToAlbumItem(track: TrackItem): AlbumItem {
    val albumTitle = if (track.album.isNotBlank() &&
        !track.album.equals("Unknown Album", ignoreCase = true) &&
        !track.album.equals("YouTube Music", ignoreCase = true)) {
        track.album
    } else {
        track.title
    }
    val albumArtist = if (track.artist.isNotBlank() &&
        !track.artist.equals("Unknown Artist", ignoreCase = true)) {
        track.artist
    } else {
        "Unknown Artist"
    }
    return AlbumItem(
        id = "album_${albumTitle.lowercase().replace(" ", "_")}_${albumArtist.lowercase().replace(" ", "_")}",
        title = albumTitle,
        artist = albumArtist,
        coverResId = track.artworkResId,
        coverUri = track.artworkUri,
        year = 2024,
        tracksCount = 12
    )
}

@Composable
fun BentoGrid(
    tracks: List<TrackItem>,
    onAlbumClick: (AlbumItem) -> Unit,
    onAddFolderClick: () -> Unit,
    modifier: Modifier = Modifier,
    onTrackClick: ((TrackItem) -> Unit)? = null,
    onTrackLongPress: ((TrackItem) -> Unit)? = null
) {
    val accentColor = LocalAccentColor.current
    val appColors = LocalAppColors.current

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = "Recently Played",
            color = appColors.textPrimary,
            fontFamily = GoogleSans,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (tracks.isEmpty()) {
            // Elegant LiquidGlass Empty State Card
            LiquidGlassPill(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(210.dp),
                shape = RoundedCornerShape(26.dp),
                tintColor = appColors.glassPillTint,
                specularAlpha = if (appColors.isDark) 0.95f else 0.45f,
                elevation = 8.dp,
                onClick = onAddFolderClick
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(accentColor.copy(alpha = 0.20f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "No recently played songs",
                        color = appColors.textPrimary,
                        fontFamily = GoogleSans,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Play songs from recommendations, search, or local library to build your history.",
                        color = appColors.textSecondary,
                        fontFamily = PlusJakartaSans,
                        fontSize = 12.5.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp
                    )
                }
            }
        } else if (tracks.size == 1) {
            // Single hero card with padding
            Box(
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                BentoCard(
                    track = tracks[0],
                    modifier = Modifier
                        .width(220.dp)
                        .height(220.dp),
                    alignment = Alignment.Center,
                    blurStartFraction = 0.56f,
                    onClick = { onAlbumClick(trackToAlbumItem(tracks[0])) },
                    onLongClick = onTrackLongPress?.let { cb -> { cb(tracks[0]) } }
                )
            }
        } else {
            // Horizontal scrollable bento layout for 2 to 50 albums/tracks
            val bentoColumns = remember(tracks) { buildBentoColumns(tracks) }
            val lazyListState = rememberLazyListState()

            LazyRow(
                state = lazyListState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(
                    items = bentoColumns,
                    key = { index, col ->
                        when (col) {
                            is BentoColumn.TwoCards -> "two_${col.top.id}_${col.bottom.id}_$index"
                            is BentoColumn.ThreeCards -> "three_${col.top.id}_${col.middle.id}_${col.bottom.id}_$index"
                            is BentoColumn.SingleCard -> "single_${col.track.id}_$index"
                        }
                    },
                    contentType = { _, col ->
                        when (col) {
                            is BentoColumn.TwoCards -> "bento_two"
                            is BentoColumn.ThreeCards -> "bento_three"
                            is BentoColumn.SingleCard -> "bento_single"
                        }
                    }
                ) { columnIndex, column ->
                    Column(
                        modifier = Modifier
                            .width(185.dp)
                            .height(412.dp)
                            .graphicsLayer {
                                // Observing scroll offset registers redraw dependency during draw phase
                                @Suppress("UNUSED_VARIABLE")
                                val scrollOffset = lazyListState.firstVisibleItemScrollOffset
                                @Suppress("UNUSED_VARIABLE")
                                val firstIndex = lazyListState.firstVisibleItemIndex

                                val layoutInfo = lazyListState.layoutInfo
                                val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == columnIndex }
                                if (itemInfo != null) {
                                    val itemStart = itemInfo.offset.toFloat()
                                    val itemSize = itemInfo.size.toFloat()
                                    val itemEnd = itemStart + itemSize
                                    val viewportEnd = layoutInfo.viewportEndOffset.toFloat()

                                    // Scale smoothly decreases as item scrolls out to the left
                                    val leftScale = if (itemStart < 0f) {
                                        val exitProgress = (-itemStart / itemSize).coerceIn(0f, 1f)
                                        1.0f - (0.24f * exitProgress)
                                    } else {
                                        1.0f
                                    }

                                    // Gentle scale as item enters or exits right edge
                                    val rightScale = if (itemEnd > viewportEnd) {
                                        val rightExitProgress = ((itemEnd - viewportEnd) / itemSize).coerceIn(0f, 1f)
                                        1.0f - (0.16f * rightExitProgress)
                                    } else {
                                        1.0f
                                    }

                                    val finalScale = (leftScale * rightScale).coerceIn(0.74f, 1.0f)
                                    scaleX = finalScale
                                    scaleY = finalScale

                                    // Pivot dynamically to pull away naturally on exit
                                    val pivotX = when {
                                        itemStart < 0f -> 0.90f
                                        itemEnd > viewportEnd -> 0.10f
                                        else -> 0.5f
                                    }
                                    transformOrigin = TransformOrigin(pivotX, 0.5f)

                                    // Subtle graceful alpha fade as it exits to the left
                                    alpha = if (itemStart < 0f) {
                                        val exitProgress = (-itemStart / itemSize).coerceIn(0f, 1f)
                                        1.0f - (0.25f * exitProgress)
                                    } else {
                                        1.0f
                                    }
                                }
                            },
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when (column) {
                            is BentoColumn.TwoCards -> {
                                val topHeight = if (column.reverseHeights) 180.dp else 220.dp
                                val bottomHeight = if (column.reverseHeights) 220.dp else 180.dp
                                BentoCard(
                                    track = column.top,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(topHeight),
                                    alignment = Alignment.Center,
                                    blurStartFraction = if (topHeight >= 200.dp) 0.56f else 0.48f,
                                    onClick = { onAlbumClick(trackToAlbumItem(column.top)) },
                                    onLongClick = onTrackLongPress?.let { cb -> { cb(column.top) } }
                                )
                                BentoCard(
                                    track = column.bottom,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(bottomHeight),
                                    alignment = Alignment.Center,
                                    blurStartFraction = if (bottomHeight >= 200.dp) 0.56f else 0.48f,
                                    onClick = { onAlbumClick(trackToAlbumItem(column.bottom)) },
                                    onLongClick = onTrackLongPress?.let { cb -> { cb(column.bottom) } }
                                )
                            }
                            is BentoColumn.ThreeCards -> {
                                val topHeight = if (column.reverseHeights) 138.dp else 125.dp
                                val middleHeight = 125.dp
                                val bottomHeight = if (column.reverseHeights) 125.dp else 138.dp
                                BentoCard(
                                    track = column.top,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(topHeight),
                                    alignment = Alignment.Center,
                                    blurStartFraction = 0.42f,
                                    onClick = { onAlbumClick(trackToAlbumItem(column.top)) },
                                    onLongClick = onTrackLongPress?.let { cb -> { cb(column.top) } }
                                )
                                BentoCard(
                                    track = column.middle,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(middleHeight),
                                    alignment = Alignment.Center,
                                    blurStartFraction = 0.42f,
                                    onClick = { onAlbumClick(trackToAlbumItem(column.middle)) },
                                    onLongClick = onTrackLongPress?.let { cb -> { cb(column.middle) } }
                                )
                                BentoCard(
                                    track = column.bottom,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(bottomHeight),
                                    alignment = Alignment.Center,
                                    blurStartFraction = 0.42f,
                                    onClick = { onAlbumClick(trackToAlbumItem(column.bottom)) },
                                    onLongClick = onTrackLongPress?.let { cb -> { cb(column.bottom) } }
                                )
                            }
                            is BentoColumn.SingleCard -> {
                                BentoCard(
                                    track = column.track,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(412.dp),
                                    alignment = Alignment.Center,
                                    blurStartFraction = 0.72f,
                                    onClick = { onAlbumClick(trackToAlbumItem(column.track)) },
                                    onLongClick = onTrackLongPress?.let { cb -> { cb(column.track) } }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BentoCard(
    track: TrackItem,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    blurStartFraction: Float = 0.48f,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val fallbackAccent = LocalAccentColor.current

    // Check memory cache synchronously first, then fetch asynchronously if not yet cached
    val cachedColor = remember(track.id, track.artworkUri, track.artworkResId) {
        ArtworkColorExtractor.getCachedAccentColor(track)
    }
    var ambientColorState by remember(track.id, track.artworkUri, track.artworkResId) {
        mutableStateOf(cachedColor)
    }

    LaunchedEffect(track.id, track.artworkUri, track.artworkResId) {
        if (ambientColorState == null) {
            val extracted = ArtworkColorExtractor.extractAccentColor(context, track)
            ambientColorState = extracted
        }
    }

    val ambientColor = ambientColorState ?: fallbackAccent
    val animatedAmbientColor by animateColorAsState(
        targetValue = ambientColor,
        animationSpec = tween(durationMillis = 350),
        label = "bentoAmbientColor"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val jelly = rememberAuroraJellyPressState(
        interactionSource = interactionSource,
        pressedScaleX = 0.955f,
        pressedScaleY = 0.935f,
        releaseVelocityX = 1.65f,
        releaseVelocityY = 0.85f,
        dampingRatio = 0.42f,
        stiffness = 420f
    )
    val cornerShape = RoundedCornerShape(26.dp)

    val displayAlbum = if (track.album.isNotBlank() &&
        !track.album.equals("Unknown Album", ignoreCase = true) &&
        !track.album.equals("YouTube Music", ignoreCase = true)) {
        track.album
    } else {
        track.title
    }
    val displayArtist = if (track.artist.isNotBlank() &&
        !track.artist.equals("Unknown Artist", ignoreCase = true)) {
        track.artist
    } else {
        "Unknown Artist"
    }

    val artworkModel: Any? = track.artworkResId?.takeIf { it != 0 }
        ?: track.artworkUri?.takeIf { it.isNotBlank() }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = jelly.scaleX.value
                scaleY = jelly.scaleY.value
            }
            .shadow(
                elevation = 6.dp,
                shape = cornerShape,
                ambientColor = Color.Black.copy(alpha = 0.4f),
                spotColor = Color.Black.copy(alpha = 0.5f)
            )
            .clip(cornerShape)
            .background(Color(0xFF1E212A))
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
            ),
        contentAlignment = Alignment.Center
    ) {
        if (track.artworkResId != null) {
            Image(
                painter = painterResource(id = track.artworkResId),
                contentDescription = "$displayAlbum - $displayArtist",
                contentScale = ContentScale.Crop,
                alignment = alignment,
                modifier = Modifier.matchParentSize()
            )
        } else if (track.artworkUri != null) {
            AsyncImage(
                model = track.artworkUri,
                contentDescription = "$displayAlbum - $displayArtist",
                contentScale = ContentScale.Crop,
                alignment = alignment,
                modifier = Modifier.matchParentSize()
            )
        } else {
            // Fallback minimalist art with music note
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color(0xFF6B7280),
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // True GPU progressive backdrop-blur + smooth ambient-tinted gradient over the lower region
        ProgressiveArtworkBlurScrim(
            artworkModel = artworkModel,
            modifier = Modifier.matchParentSize(),
            blurRadius = 22.dp,
            tintColor = lerp(Color(0xFF07090E), animatedAmbientColor, 0.22f),
            blurStartFraction = blurStartFraction,
            scrimTopAlpha = 0.0f,
            scrimBottomAlpha = 0.78f
        )

        // Edge-to-edge full-width bottom label flush inside the card's 26.dp rounded clip
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 13.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = displayAlbum,
                color = Color.White,
                fontFamily = GoogleSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = displayArtist,
                color = Color(0xFFD6DCE8),
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Normal,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
