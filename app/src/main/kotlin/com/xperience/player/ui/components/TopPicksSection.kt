package com.xperience.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xperience.player.domain.model.TopPickItem
import com.xperience.player.domain.model.TopPickKind
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.PlusJakartaSans

private val TopPickCardShape = RoundedCornerShape(22.dp)

@Composable
fun TopPicksSection(
    items: List<TopPickItem>,
    onTopPickClick: (TopPickItem) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Top Picks for You",
    onTopPickLongPress: ((TopPickItem) -> Unit)? = null
) {
    if (items.isEmpty()) return
    val appColors = LocalAppColors.current
    val accentColor = LocalAccentColor.current
    val listState = rememberLazyListState()

    Column(modifier = modifier.fillMaxWidth()) {
        FeedSectionHeader(
            title = title,
            appColors = appColors,
            leadingBadgeIcon = Icons.Rounded.AutoAwesome,
            accentColor = accentColor
        )

        Spacer(modifier = Modifier.height(14.dp))

        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(
                items = items,
                key = { index, item -> "top_pick_${item.kind.name}_${item.id}_$index" },
                contentType = { _, item -> item.kind.name }
            ) { index, item ->
                TopPickEditorialCard(
                    item = item,
                    accentColor = accentColor,
                    onClick = { onTopPickClick(item) },
                    onLongClick = onTopPickLongPress?.let { cb -> { cb(item) } },
                    modifier = Modifier
                        .width(196.dp)
                        .height(264.dp)
                        .bentoScrollTransform(
                            lazyListState = listState,
                            itemIndex = index,
                            leftScaleDrop = 0.20f,
                            rightScaleDrop = 0.13f,
                            leftAlphaDrop = 0.22f
                        )
                )
            }
        }
    }
}

@Composable
private fun TopPickEditorialCard(
    item: TopPickItem,
    accentColor: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val jellyState = rememberJellyPressState()
    val artworkUri = item.artworkUri?.takeIf { it.isNotBlank() }
    val palette = remember(item.id, item.kind) { expressiveGradientColors("${item.kind.name}_${item.id}") }
    val deepTint = remember(palette) { lerp(palette.first(), Color(0xFF080A10), 0.72f) }

    val collageUris = remember(artworkUri, item.secondaryArtworkUris) {
        (listOfNotNull(artworkUri) + item.secondaryArtworkUris.filter { it.isNotBlank() })
            .distinct()
            .take(4)
    }

    GlossyCardShell(
        shape = TopPickCardShape,
        jellyState = jellyState,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        elevation = 10.dp,
        containerColor = Color(0xFF131620)
    ) {
        when (item.kind) {
            TopPickKind.PERSONAL_STATION -> {
                if (artworkUri != null) {
                    AsyncImage(
                        model = artworkUri,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center,
                        modifier = Modifier.matchParentSize()
                    )
                }

                ProgressiveArtworkBlurScrim(
                    artworkModel = artworkUri,
                    modifier = Modifier.matchParentSize(),
                    blurRadius = 24.dp,
                    tintColor = deepTint,
                    blurStartFraction = 0.50f,
                    scrimTopAlpha = 0.0f,
                    scrimBottomAlpha = 0.45f
                )

                // Expressive multi-stop gradient canvas wash unified across sharp & blurred zones
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to palette.first().copy(alpha = if (artworkUri != null) 0.54f else 1.0f),
                                0.45f to palette[1 % palette.size].copy(alpha = if (artworkUri != null) 0.42f else 0.96f),
                                1.0f to deepTint.copy(alpha = 0.86f)
                            )
                        )
                )

                // Subtle editorial concentric acoustic waves
                Canvas(modifier = Modifier.matchParentSize()) {
                    val waveCenter = Offset(size.width * 0.28f, size.height * 0.28f)
                    listOf(32.dp.toPx(), 56.dp.toPx(), 84.dp.toPx(), 114.dp.toPx()).forEachIndexed { idx, radius ->
                        drawCircle(
                            color = Color.White.copy(alpha = 0.16f - idx * 0.03f),
                            radius = radius,
                            center = waveCenter,
                            style = Stroke(width = 1.3.dp.toPx())
                        )
                    }
                }

                val badgeLabel = if (
                    item.title.contains("mix", ignoreCase = true) ||
                    item.eyebrow.contains("mix", ignoreCase = true)
                ) {
                    "Mix"
                } else {
                    "Music"
                }
                FrostedCardBadge(
                    text = badgeLabel,
                    icon = Icons.Rounded.GraphicEq,
                    dotColor = accentColor,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                )
            }

            TopPickKind.NEW_RELEASE -> {
                if (artworkUri != null) {
                    AsyncImage(
                        model = artworkUri,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center,
                        modifier = Modifier.matchParentSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Brush.linearGradient(palette)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Album,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.45f),
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }

                ProgressiveArtworkBlurScrim(
                    artworkModel = artworkUri,
                    modifier = Modifier.matchParentSize(),
                    blurRadius = 24.dp,
                    tintColor = Color.Black,
                    blurStartFraction = 0.54f,
                    scrimTopAlpha = 0.0f,
                    scrimBottomAlpha = 0.80f
                )
            }

            TopPickKind.FEATURED_PLAYLIST -> {
                val useCollage = collageUris.size >= 2
                if (useCollage) {
                    EditorialCollageBackground(
                        uris = collageUris,
                        fallbackPalette = palette,
                        modifier = Modifier.matchParentSize()
                    )
                } else if (artworkUri != null) {
                    AsyncImage(
                        model = artworkUri,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center,
                        modifier = Modifier.matchParentSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Brush.linearGradient(palette))
                    )
                }

                // Vibrant diagonal sheen
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = 0.22f),
                                    Color.Transparent,
                                    palette.last().copy(alpha = 0.18f)
                                )
                            )
                        )
                )

                ProgressiveArtworkBlurScrim(
                    artworkModel = if (useCollage) null else artworkUri,
                    modifier = Modifier.matchParentSize(),
                    blurRadius = 24.dp,
                    tintColor = Color(0xFF090B12),
                    blurStartFraction = 0.52f,
                    scrimTopAlpha = 0.0f,
                    scrimBottomAlpha = 0.86f
                )

                FrostedCardBadge(
                    text = "Playlist",
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                )
            }

            TopPickKind.ARTIST_SPOTLIGHT -> {
                if (artworkUri != null) {
                    AsyncImage(
                        model = artworkUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center,
                        alpha = 0.18f,
                        modifier = Modifier.matchParentSize()
                    )
                }

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    lerp(palette.first(), accentColor, 0.28f).copy(alpha = 0.84f),
                                    Color(0xFF10131E).copy(alpha = 0.94f),
                                    Color(0xFF090B12)
                                )
                            )
                        )
                )

                // Overlapping circular artist portraits in upper area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(154.dp)
                        .align(Alignment.TopCenter)
                        .padding(top = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val sideArtists = item.secondaryArtworkUris.filter { it.isNotBlank() }.take(2)
                    if (sideArtists.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(124.dp)
                                .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(102.dp)
                                .border(1.dp, accentColor.copy(alpha = 0.32f), CircleShape)
                        )
                    } else {
                        sideArtists.getOrNull(0)?.let { leftUri ->
                            SpotlightAvatarCircle(
                                uri = leftUri,
                                sizeDp = 58,
                                borderColor = Color.White.copy(alpha = 0.42f),
                                modifier = Modifier.offset(x = (-38).dp, y = 10.dp)
                            )
                        }
                        sideArtists.getOrNull(1)?.let { rightUri ->
                            SpotlightAvatarCircle(
                                uri = rightUri,
                                sizeDp = 58,
                                borderColor = Color.White.copy(alpha = 0.42f),
                                modifier = Modifier.offset(x = 38.dp, y = 10.dp)
                            )
                        }
                    }

                    SpotlightAvatarCircle(
                        uri = artworkUri,
                        sizeDp = 86,
                        borderColor = Color.White.copy(alpha = 0.85f),
                        accentGlow = accentColor
                    )
                }

                ProgressiveArtworkBlurScrim(
                    artworkModel = null,
                    modifier = Modifier.matchParentSize(),
                    blurRadius = 22.dp,
                    tintColor = Color(0xFF07090F),
                    blurStartFraction = 0.50f,
                    scrimTopAlpha = 0.0f,
                    scrimBottomAlpha = 0.90f
                )

                FrostedCardBadge(
                    text = "Spotlight",
                    icon = Icons.Rounded.AutoAwesome,
                    dotColor = accentColor,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                )
            }
        }

        // Clean white left-aligned editorial typography in the lower 38-42%
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            val eyebrow = item.eyebrow.ifBlank { defaultTopPickEyebrow(item.kind) }
            Text(
                text = eyebrow,
                color = Color.White.copy(alpha = 0.85f),
                fontFamily = PlusJakartaSans,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = item.title,
                color = Color.White,
                fontFamily = GoogleSans,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 19.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (item.subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = item.subtitle,
                    color = Color.White.copy(alpha = 0.78f),
                    fontFamily = PlusJakartaSans,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EditorialCollageBackground(
    uris: List<String>,
    fallbackPalette: List<Color>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            CollageTile(
                uri = uris.getOrNull(0),
                fallbackColor = fallbackPalette.first(),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            Spacer(modifier = Modifier.width(1.5.dp))
            CollageTile(
                uri = uris.getOrNull(1) ?: uris.getOrNull(0),
                fallbackColor = fallbackPalette[1 % fallbackPalette.size],
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
        Spacer(modifier = Modifier.height(1.5.dp))
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            CollageTile(
                uri = uris.getOrNull(2) ?: uris.getOrNull(1) ?: uris.getOrNull(0),
                fallbackColor = fallbackPalette[2 % fallbackPalette.size],
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            Spacer(modifier = Modifier.width(1.5.dp))
            CollageTile(
                uri = uris.getOrNull(3) ?: uris.getOrNull(0),
                fallbackColor = fallbackPalette.last(),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun CollageTile(
    uri: String?,
    fallbackColor: Color,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(fallbackColor)) {
        if (!uri.isNullOrBlank()) {
            AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun SpotlightAvatarCircle(
    uri: String?,
    sizeDp: Int,
    borderColor: Color,
    modifier: Modifier = Modifier,
    accentGlow: Color? = null
) {
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .shadow(
                elevation = if (accentGlow != null) 12.dp else 6.dp,
                shape = CircleShape,
                ambientColor = accentGlow?.copy(alpha = 0.45f) ?: Color.Black.copy(alpha = 0.45f),
                spotColor = Color.Black.copy(alpha = 0.6f)
            )
            .clip(CircleShape)
            .background(Color(0xFF202433))
            .border(width = if (accentGlow != null) 2.dp else 1.5.dp, color = borderColor, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (!uri.isNullOrBlank()) {
            AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.65f),
                modifier = Modifier.size((sizeDp * 0.5f).dp)
            )
        }
    }
}

private fun defaultTopPickEyebrow(kind: TopPickKind): String = when (kind) {
    TopPickKind.PERSONAL_STATION -> "Your Personal Station"
    TopPickKind.NEW_RELEASE -> "New Release"
    TopPickKind.FEATURED_PLAYLIST -> "Featured Playlist"
    TopPickKind.ARTIST_SPOTLIGHT -> "Featured Artist"
}
