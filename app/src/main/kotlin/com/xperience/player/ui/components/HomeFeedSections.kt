package com.xperience.player.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xperience.player.domain.model.AlbumItem
import com.xperience.player.domain.model.MoreLikeAlbumSectionData
import com.xperience.player.domain.model.NewReleaseItem
import com.xperience.player.domain.model.RadioStationItem
import com.xperience.player.domain.model.RecommendedPlaylistItem
import com.xperience.player.domain.model.UserPlaylist
import com.xperience.player.ui.playlists.PlaylistCoverImage
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkAdded
import com.xperience.player.ui.theme.AppThemeColors
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.PlusJakartaSans
import kotlin.math.abs

private val FeedCardShape = RoundedCornerShape(22.dp)
private val AlbumMoreCardShape = RoundedCornerShape(20.dp)
private val BannerShape = RoundedCornerShape(24.dp)

@Stable
internal class JellyPressState(
    val interactionSource: MutableInteractionSource,
    val scaleXState: State<Float>,
    val scaleYState: State<Float>
)

@Composable
internal fun rememberJellyPressState(
    pressedScaleX: Float = 0.955f,
    pressedScaleY: Float = 0.92f
): JellyPressState {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val jellySpring = remember {
        spring<Float>(
            dampingRatio = 0.42f,
            stiffness = 420f
        )
    }
    val scaleX = animateFloatAsState(
        targetValue = if (isPressed) pressedScaleX else 1.0f,
        animationSpec = jellySpring,
        label = "jellyScaleX"
    )
    val scaleY = animateFloatAsState(
        targetValue = if (isPressed) pressedScaleY else 1.0f,
        animationSpec = jellySpring,
        label = "jellyScaleY"
    )
    return remember(interactionSource, scaleX, scaleY) {
        JellyPressState(
            interactionSource = interactionSource,
            scaleXState = scaleX,
            scaleYState = scaleY
        )
    }
}

/**
 * Applies BentoGrid-style scroll exit/enter scale & pivot transform inside the draw/layer phase
 * without triggering recomposition on scroll ticks.
 */
internal fun Modifier.bentoScrollTransform(
    lazyListState: LazyListState,
    itemIndex: Int,
    leftScaleDrop: Float = 0.22f,
    rightScaleDrop: Float = 0.14f,
    leftAlphaDrop: Float = 0.22f
): Modifier = graphicsLayer {
    @Suppress("UNUSED_VARIABLE")
    val scrollOffset = lazyListState.firstVisibleItemScrollOffset
    @Suppress("UNUSED_VARIABLE")
    val firstIndex = lazyListState.firstVisibleItemIndex

    val layoutInfo = lazyListState.layoutInfo
    val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == itemIndex }
    if (itemInfo != null && itemInfo.size > 0) {
        val itemStart = itemInfo.offset.toFloat()
        val itemSize = itemInfo.size.toFloat()
        val itemEnd = itemStart + itemSize
        val viewportEnd = layoutInfo.viewportEndOffset.toFloat()

        val leftScale = if (itemStart < 0f) {
            val exitProgress = (-itemStart / itemSize).coerceIn(0f, 1f)
            1.0f - (leftScaleDrop * exitProgress)
        } else {
            1.0f
        }

        val rightScale = if (itemEnd > viewportEnd) {
            val rightExitProgress = ((itemEnd - viewportEnd) / itemSize).coerceIn(0f, 1f)
            1.0f - (rightScaleDrop * rightExitProgress)
        } else {
            1.0f
        }

        val finalScale = (leftScale * rightScale).coerceIn(0.76f, 1.0f)
        scaleX = finalScale
        scaleY = finalScale

        val pivotX = when {
            itemStart < 0f -> 0.88f
            itemEnd > viewportEnd -> 0.12f
            else -> 0.5f
        }
        transformOrigin = TransformOrigin(pivotX, 0.5f)

        alpha = if (itemStart < 0f) {
            val exitProgress = (-itemStart / itemSize).coerceIn(0f, 1f)
            1.0f - (leftAlphaDrop * exitProgress)
        } else {
            1.0f
        }
    }
}

internal fun Modifier.jellyTapScale(jellyState: JellyPressState): Modifier = graphicsLayer {
    scaleX = jellyState.scaleXState.value
    scaleY = jellyState.scaleYState.value
}

private val ExpressiveGradientPalettes = listOf(
    listOf(Color(0xFF3A1C71), Color(0xFFD76D77), Color(0xFFFFAF7B)),
    listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C7744)),
    listOf(Color(0xFF1D2671), Color(0xFFC33764), Color(0xFF7B2FF7)),
    listOf(Color(0xFF0B486B), Color(0xFF3B8686), Color(0xFF79BD9A)),
    listOf(Color(0xFF2B1055), Color(0xFF7597DE), Color(0xFF00C9FF)),
    listOf(Color(0xFF42275A), Color(0xFF734B6D), Color(0xFFE96443))
)

internal fun expressiveGradientColors(seedKey: String): List<Color> {
    val idx = abs(seedKey.hashCode()) % ExpressiveGradientPalettes.size
    return ExpressiveGradientPalettes[idx]
}

internal fun readableAccentColor(accentColor: Color, isDark: Boolean): Color =
    if (!isDark && accentColor.luminance() > 0.55f) {
        lerp(accentColor, Color(0xFF0A101C), 0.45f)
    } else {
        accentColor
    }

internal fun onAccentColor(accentColor: Color): Color =
    if (accentColor.luminance() > 0.62f) Color(0xFF0A101C) else Color.White

@Composable
internal fun FeedSectionHeader(
    title: String,
    appColors: AppThemeColors,
    modifier: Modifier = Modifier,
    leadingBadgeIcon: ImageVector? = null,
    accentColor: Color = LocalAccentColor.current,
    trailingBadgeText: String? = null
) {
    val displayAccent = readableAccentColor(accentColor, appColors.isDark)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        if (leadingBadgeIcon != null) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(displayAccent.copy(alpha = if (appColors.isDark) 0.20f else 0.14f))
                    .border(
                        width = 0.8.dp,
                        color = displayAccent.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(9.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = leadingBadgeIcon,
                    contentDescription = null,
                    tint = displayAccent,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
        }

        Text(
            text = title,
            color = appColors.textPrimary,
            fontFamily = GoogleSans,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        if (!trailingBadgeText.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(displayAccent.copy(alpha = if (appColors.isDark) 0.16f else 0.12f))
                    .border(
                        width = 0.8.dp,
                        color = displayAccent.copy(alpha = 0.42f),
                        shape = CircleShape
                    )
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            ) {
                Text(
                    text = trailingBadgeText,
                    color = displayAccent,
                    fontFamily = GoogleSans,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
internal fun FrostedCardBadge(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    dotColor: Color? = null,
    containerColor: Color = Color.Black.copy(alpha = 0.46f),
    contentColor: Color = Color.White,
    borderColor: Color = Color.White.copy(alpha = 0.26f)
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(CircleShape)
            .background(containerColor)
            .border(width = 0.7.dp, color = borderColor, shape = CircleShape)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        if (dotColor != null) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(12.dp)
            )
        }
        Text(
            text = text,
            color = contentColor,
            fontFamily = GoogleSans,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
internal fun GlossyCardShell(
    shape: Shape,
    jellyState: JellyPressState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    elevation: Dp = 8.dp,
    containerColor: Color = Color(0xFF1A1D28),
    content: @Composable BoxScope.() -> Unit
) {
    val rimBrush = remember {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.34f),
                Color.White.copy(alpha = 0.08f),
                Color.White.copy(alpha = 0.16f)
            )
        )
    }

    Box(
        modifier = modifier
            .jellyTapScale(jellyState)
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.38f),
                spotColor = Color.Black.copy(alpha = 0.48f)
            )
            .clip(shape)
            .background(containerColor)
            .border(width = 0.9.dp, brush = rimBrush, shape = shape)
            .clickable(
                interactionSource = jellyState.interactionSource,
                indication = null,
                onClick = onClick
            ),
        content = content
    )
}

@Composable
fun MoreLikeAlbumSectionRow(
    section: MoreLikeAlbumSectionData,
    onAlbumClick: (AlbumItem) -> Unit,
    appColors: AppThemeColors = LocalAppColors.current,
    modifier: Modifier = Modifier,
    accentColor: Color = LocalAccentColor.current
) {
    val visibleAlbums = remember(section.albums) { section.albums.take(15) }
    if (visibleAlbums.isEmpty()) return

    val listState = rememberLazyListState()
    val headerTitle = section.sectionTitle.ifBlank { "More like: ${section.seedAlbumTitle}" }

    Column(modifier = modifier.fillMaxWidth()) {
        FeedSectionHeader(
            title = headerTitle,
            appColors = appColors,
            leadingBadgeIcon = Icons.Rounded.Album,
            accentColor = accentColor,
            trailingBadgeText = "Similar"
        )

        Spacer(modifier = Modifier.height(14.dp))

        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            itemsIndexed(
                items = visibleAlbums,
                key = { index, album -> "${section.id}_${album.id}_$index" }
            ) { index, album ->
                MoreLikeAlbumCard(
                    album = album,
                    accentColor = accentColor,
                    onClick = { onAlbumClick(album) },
                    modifier = Modifier
                        .width(150.dp)
                        .height(178.dp)
                        .bentoScrollTransform(listState, index)
                )
            }
        }
    }
}

@Composable
private fun MoreLikeAlbumCard(
    album: AlbumItem,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val jellyState = rememberJellyPressState()
    val artworkModel: Any? = album.coverUri?.takeIf { it.isNotBlank() } ?: album.coverResId

    GlossyCardShell(
        shape = AlbumMoreCardShape,
        jellyState = jellyState,
        onClick = onClick,
        modifier = modifier
    ) {
        when {
            album.coverResId != null -> {
                Image(
                    painter = painterResource(id = album.coverResId),
                    contentDescription = "${album.title} - ${album.artist}",
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    modifier = Modifier.matchParentSize()
                )
            }
            !album.coverUri.isNullOrBlank() -> {
                AsyncImage(
                    model = album.coverUri,
                    contentDescription = "${album.title} - ${album.artist}",
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    modifier = Modifier.matchParentSize()
                )
            }
            else -> {
                val gradient = expressiveGradientColors(album.id)
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Brush.linearGradient(gradient)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Album,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.45f),
                        modifier = Modifier.size(46.dp)
                    )
                }
            }
        }

        ProgressiveArtworkBlurScrim(
            artworkModel = artworkModel,
            modifier = Modifier.matchParentSize(),
            blurRadius = 20.dp,
            tintColor = Color.Black,
            blurStartFraction = 0.46f,
            scrimTopAlpha = 0.0f,
            scrimBottomAlpha = 0.80f
        )

        if (album.year > 1900) {
            FrostedCardBadge(
                text = album.year.toString(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
                dotColor = accentColor
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp)
        ) {
            Text(
                text = album.title,
                color = Color.White,
                fontFamily = GoogleSans,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = album.artist,
                color = Color.White.copy(alpha = 0.80f),
                fontFamily = PlusJakartaSans,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun PlaylistsMadeForYouSection(
    playlists: List<RecommendedPlaylistItem>,
    onPlaylistClick: (RecommendedPlaylistItem) -> Unit,
    appColors: AppThemeColors = LocalAppColors.current,
    modifier: Modifier = Modifier,
    accentColor: Color = LocalAccentColor.current
) {
    if (playlists.isEmpty()) return
    val listState = rememberLazyListState()

    Column(modifier = modifier.fillMaxWidth()) {
        FeedSectionHeader(
            title = "Playlists made for you",
            appColors = appColors,
            leadingBadgeIcon = Icons.AutoMirrored.Rounded.PlaylistPlay,
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
                items = playlists,
                key = { index, item -> "playlist_mfy_${item.id}_$index" }
            ) { index, playlist ->
                PlaylistMadeForYouCard(
                    playlist = playlist,
                    accentColor = accentColor,
                    onClick = { onPlaylistClick(playlist) },
                    modifier = Modifier
                        .width(168.dp)
                        .height(202.dp)
                        .bentoScrollTransform(listState, index)
                )
            }
        }
    }
}

@Composable
private fun PlaylistMadeForYouCard(
    playlist: RecommendedPlaylistItem,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val jellyState = rememberJellyPressState()
    val artworkUri = playlist.artworkUri?.takeIf { it.isNotBlank() }

    GlossyCardShell(
        shape = FeedCardShape,
        jellyState = jellyState,
        onClick = onClick,
        modifier = modifier
    ) {
        if (artworkUri != null) {
            AsyncImage(
                model = artworkUri,
                contentDescription = playlist.title,
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                modifier = Modifier.matchParentSize()
            )
        } else {
            val palette = expressiveGradientColors(playlist.id)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Brush.linearGradient(palette)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(54.dp)
                )
            }
        }

        ProgressiveArtworkBlurScrim(
            artworkModel = artworkUri,
            modifier = Modifier.matchParentSize(),
            blurRadius = 22.dp,
            tintColor = Color.Black,
            blurStartFraction = 0.45f,
            scrimTopAlpha = 0.0f,
            scrimBottomAlpha = 0.84f
        )

        FrostedCardBadge(
            text = "Made for you",
            icon = Icons.Rounded.AutoAwesome,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(11.dp)
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(11.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.42f))
                .border(0.7.dp, Color.White.copy(alpha = 0.28f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 13.dp, vertical = 12.dp)
        ) {
            Text(
                text = playlist.title,
                color = Color.White,
                fontFamily = GoogleSans,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                lineHeight = 17.5.sp,
                overflow = TextOverflow.Ellipsis
            )
            val secondaryLine = playlist.subtitle.ifBlank { playlist.author.orEmpty() }
            if (secondaryLine.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = secondaryLine,
                    color = Color.White.copy(alpha = 0.78f),
                    fontFamily = PlusJakartaSans,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun RadioStationsSection(
    stations: List<RadioStationItem>,
    onStationClick: (RadioStationItem) -> Unit,
    onSeeMoreClick: () -> Unit,
    appColors: AppThemeColors = LocalAppColors.current,
    modifier: Modifier = Modifier,
    accentColor: Color = LocalAccentColor.current,
    title: String = "Radio Stations"
) {
    val cappedStations = remember(stations) { stations.take(8) }
    if (cappedStations.isEmpty()) return
    val listState = rememberLazyListState()

    Column(modifier = modifier.fillMaxWidth()) {
        FeedSectionHeader(
            title = title,
            appColors = appColors,
            leadingBadgeIcon = Icons.Rounded.Radio,
            accentColor = accentColor,
            trailingBadgeText = "Live Mix"
        )

        Spacer(modifier = Modifier.height(14.dp))

        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(
                items = cappedStations,
                key = { index, station -> "radio_home_${station.id}_$index" }
            ) { index, station ->
                RadioStationCard(
                    station = station,
                    accentColor = accentColor,
                    onClick = { onStationClick(station) },
                    modifier = Modifier
                        .width(164.dp)
                        .height(194.dp)
                        .bentoScrollTransform(listState, index)
                )
            }

            item(key = "radio_see_more_end") {
                SeeMoreEndCard(
                    label = "See more",
                    subtitle = "Explore stations",
                    icon = Icons.Rounded.Radio,
                    accentColor = accentColor,
                    appColors = appColors,
                    onClick = onSeeMoreClick,
                    modifier = Modifier
                        .width(138.dp)
                        .height(194.dp)
                        .bentoScrollTransform(listState, cappedStations.size)
                )
            }
        }
    }
}

@Composable
internal fun RadioStationCard(
    station: RadioStationItem,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showPlayBadge: Boolean = true
) {
    val jellyState = rememberJellyPressState()
    val artworkUri = station.artworkUri?.takeIf { it.isNotBlank() }
    val palette = remember(station.id) { expressiveGradientColors(station.id) }

    GlossyCardShell(
        shape = FeedCardShape,
        jellyState = jellyState,
        onClick = onClick,
        modifier = modifier
    ) {
        if (artworkUri != null) {
            AsyncImage(
                model = artworkUri,
                contentDescription = station.title,
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

        ProgressiveArtworkBlurScrim(
            artworkModel = artworkUri,
            modifier = Modifier.matchParentSize(),
            blurRadius = 22.dp,
            tintColor = Color(0xFF090B12),
            blurStartFraction = 0.46f,
            scrimTopAlpha = 0.0f,
            scrimBottomAlpha = 0.84f
        )

        if (artworkUri != null) {
            // Subtle stylized radio color tint wash layered seamlessly across sharp & blurred zones
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                palette.first().copy(alpha = 0.28f),
                                Color.Transparent,
                                palette.last().copy(alpha = 0.18f)
                            )
                        )
                    )
            )
        }

        // Stylized broadcast arcs drawn softly at upper-right
        Canvas(modifier = Modifier.matchParentSize()) {
            val center = Offset(size.width * 0.86f, size.height * 0.16f)
            listOf(34.dp.toPx(), 54.dp.toPx(), 74.dp.toPx()).forEachIndexed { idx, radius ->
                drawCircle(
                    color = Color.White.copy(alpha = 0.16f - idx * 0.04f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 1.4.dp.toPx())
                )
            }
        }

        FrostedCardBadge(
            text = "RADIO",
            icon = Icons.Rounded.GraphicEq,
            dotColor = accentColor,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(11.dp)
        )

        if (showPlayBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(11.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .border(0.7.dp, Color.White.copy(alpha = 0.28f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Play radio",
                    tint = Color.White,
                    modifier = Modifier.size(17.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 13.dp, vertical = 12.dp)
        ) {
            Text(
                text = station.title,
                color = Color.White,
                fontFamily = GoogleSans,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                lineHeight = 17.5.sp,
                overflow = TextOverflow.Ellipsis
            )
            if (station.subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = station.subtitle,
                    color = Color.White.copy(alpha = 0.78f),
                    fontFamily = PlusJakartaSans,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun NovedadesSection(
    releases: List<NewReleaseItem>,
    onReleaseClick: (NewReleaseItem) -> Unit,
    onSeeMoreClick: () -> Unit,
    appColors: AppThemeColors = LocalAppColors.current,
    modifier: Modifier = Modifier,
    accentColor: Color = LocalAccentColor.current,
    title: String = "New Releases"
) {
    val cappedReleases = remember(releases) { releases.take(8) }
    if (cappedReleases.isEmpty()) return
    val listState = rememberLazyListState()

    Column(modifier = modifier.fillMaxWidth()) {
        FeedSectionHeader(
            title = title,
            appColors = appColors,
            leadingBadgeIcon = Icons.Rounded.AutoAwesome,
            accentColor = accentColor,
            trailingBadgeText = "NEW"
        )

        Spacer(modifier = Modifier.height(14.dp))

        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(
                items = cappedReleases,
                key = { index, release -> "novedades_home_${release.id}_$index" }
            ) { index, release ->
                NewReleaseCard(
                    release = release,
                    accentColor = accentColor,
                    onClick = { onReleaseClick(release) },
                    modifier = Modifier
                        .width(164.dp)
                        .height(198.dp)
                        .bentoScrollTransform(listState, index)
                )
            }

            item(key = "novedades_see_more_end") {
                SeeMoreEndCard(
                    label = "See more",
                    subtitle = "All new releases",
                    icon = Icons.Rounded.AutoAwesome,
                    accentColor = accentColor,
                    appColors = appColors,
                    onClick = onSeeMoreClick,
                    modifier = Modifier
                        .width(138.dp)
                        .height(198.dp)
                        .bentoScrollTransform(listState, cappedReleases.size)
                )
            }
        }
    }
}

@Composable
internal fun NewReleaseCard(
    release: NewReleaseItem,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showPlayBadge: Boolean = false
) {
    val jellyState = rememberJellyPressState()
    val artworkUri = release.artworkUri?.takeIf { it.isNotBlank() }

    GlossyCardShell(
        shape = FeedCardShape,
        jellyState = jellyState,
        onClick = onClick,
        modifier = modifier
    ) {
        if (artworkUri != null) {
            AsyncImage(
                model = artworkUri,
                contentDescription = "${release.title} - ${release.artist}",
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                modifier = Modifier.matchParentSize()
            )
        } else {
            val palette = remember(release.id) { expressiveGradientColors(release.id) }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Brush.linearGradient(palette)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (release.isAlbum) Icons.Rounded.Album else Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(50.dp)
                )
            }
        }

        ProgressiveArtworkBlurScrim(
            artworkModel = artworkUri,
            modifier = Modifier.matchParentSize(),
            blurRadius = 22.dp,
            tintColor = Color.Black,
            blurStartFraction = 0.46f,
            scrimTopAlpha = 0.0f,
            scrimBottomAlpha = 0.84f
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(11.dp)
        ) {
            FrostedCardBadge(
                text = "NEW",
                dotColor = accentColor,
                containerColor = accentColor.copy(alpha = 0.24f),
                borderColor = accentColor.copy(alpha = 0.65f)
            )
            FrostedCardBadge(
                text = if (release.isAlbum) "Album" else "Single"
            )
        }

        if (showPlayBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(11.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.46f))
                    .border(0.7.dp, Color.White.copy(alpha = 0.28f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Play release",
                    tint = Color.White,
                    modifier = Modifier.size(17.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 13.dp, vertical = 12.dp)
        ) {
            Text(
                text = release.title,
                color = Color.White,
                fontFamily = GoogleSans,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                lineHeight = 17.5.sp,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            val artistLine = if (!release.year.isNullOrBlank()) {
                "${release.artist} • ${release.year}"
            } else {
                release.artist.ifBlank { release.subtitle }
            }
            Text(
                text = artistLine,
                color = Color.White.copy(alpha = 0.80f),
                fontFamily = PlusJakartaSans,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SeeMoreEndCard(
    label: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    appColors: AppThemeColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val jellyState = rememberJellyPressState()
    val rimBrush = remember(appColors.isDark, accentColor) {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = if (appColors.isDark) 0.40f else 0.85f),
                accentColor.copy(alpha = 0.45f),
                Color.White.copy(alpha = if (appColors.isDark) 0.14f else 0.35f)
            )
        )
    }

    val surfaceTint = if (appColors.isDark) {
        Color(0xB8181B26)
    } else {
        Color(0xEBFFFFFF)
    }

    Box(
        modifier = modifier
            .jellyTapScale(jellyState)
            .shadow(
                elevation = 8.dp,
                shape = FeedCardShape,
                ambientColor = accentColor.copy(alpha = 0.25f),
                spotColor = Color.Black.copy(alpha = if (appColors.isDark) 0.45f else 0.18f)
            )
            .clip(FeedCardShape)
            .background(surfaceTint)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = if (appColors.isDark) 0.24f else 0.16f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.36f),
                        radius = size.maxDimension * 0.72f
                    )
                )
            }
            .border(width = 1.dp, brush = rimBrush, shape = FeedCardShape)
            .clickable(
                interactionSource = jellyState.interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .shadow(
                        elevation = 6.dp,
                        shape = CircleShape,
                        spotColor = accentColor.copy(alpha = 0.45f)
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                accentColor,
                                accentColor.copy(alpha = 0.72f)
                            )
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.42f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = label,
                    tint = onAccentColor(accentColor),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = label,
                color = appColors.textPrimary,
                fontFamily = GoogleSans,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(4.dp))

            val displayAccent = readableAccentColor(accentColor, appColors.isDark)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = displayAccent,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = subtitle,
                    color = appColors.textSecondary,
                    fontFamily = PlusJakartaSans,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun YtmLoginPromptBanner(
    onSignInClick: () -> Unit,
    onLaterClick: () -> Unit,
    onDismissClick: () -> Unit,
    appColors: AppThemeColors = LocalAppColors.current,
    accentColor: Color = LocalAccentColor.current,
    modifier: Modifier = Modifier
) {
    val rimBrush = remember(appColors.isDark, accentColor) {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = if (appColors.isDark) 0.44f else 0.90f),
                accentColor.copy(alpha = 0.48f),
                Color.White.copy(alpha = if (appColors.isDark) 0.14f else 0.35f)
            )
        )
    }

    val glassFill = if (appColors.isDark) {
        Color(0xC8151924)
    } else {
        Color(0xEDFFFFFF)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxWidth()
                .shadow(
                    elevation = 10.dp,
                    shape = BannerShape,
                    ambientColor = accentColor.copy(alpha = 0.22f),
                    spotColor = Color.Black.copy(alpha = if (appColors.isDark) 0.48f else 0.16f)
                )
                .clip(BannerShape)
                .background(glassFill)
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = if (appColors.isDark) 0.22f else 0.14f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.12f, size.height * 0.15f),
                            radius = size.width * 0.78f
                        )
                    )
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (appColors.isDark) 0.12f else 0.45f),
                                Color.Transparent
                            )
                        )
                    )
                }
                .border(width = 1.1.dp, brush = rimBrush, shape = BannerShape)
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .shadow(
                                elevation = 6.dp,
                                shape = RoundedCornerShape(14.dp),
                                spotColor = accentColor.copy(alpha = 0.45f)
                            )
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        accentColor,
                                        accentColor.copy(alpha = 0.72f)
                                    )
                                )
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = onAccentColor(accentColor),
                            modifier = Modifier.size(23.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Connect your YouTube Music account",
                            color = appColors.textPrimary,
                            fontFamily = GoogleSans,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Sign in to unlock personalized recommendations, mixes, and new releases from your feed.",
                            color = appColors.textSecondary,
                            fontFamily = PlusJakartaSans,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Normal,
                            lineHeight = 17.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                BannerActionPill(
                    label = "Sign in",
                    isPrimary = true,
                    accentColor = accentColor,
                    appColors = appColors,
                    onClick = onSignInClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BannerActionPill(
                        label = "Later",
                        isPrimary = false,
                        accentColor = accentColor,
                        appColors = appColors,
                        onClick = onLaterClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                    )

                    BannerActionPill(
                        label = "Dismiss",
                        isPrimary = false,
                        accentColor = accentColor,
                        appColors = appColors,
                        isMuted = true,
                        onClick = onDismissClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BannerActionPill(
    label: String,
    isPrimary: Boolean,
    accentColor: Color,
    appColors: AppThemeColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isMuted: Boolean = false
) {
    val jellyState = rememberJellyPressState(pressedScaleX = 0.96f, pressedScaleY = 0.91f)
    val pillShape = CircleShape

    val backgroundModifier = if (isPrimary) {
        Modifier.background(
            Brush.linearGradient(
                colors = listOf(
                    accentColor,
                    accentColor.copy(alpha = 0.80f)
                )
            )
        )
    } else {
        Modifier.background(
            if (appColors.isDark) {
                Color.White.copy(alpha = if (isMuted) 0.05f else 0.10f)
            } else {
                Color.Black.copy(alpha = if (isMuted) 0.04f else 0.07f)
            }
        )
    }

    val borderColor = if (isPrimary) {
        Color.White.copy(alpha = 0.35f)
    } else if (appColors.isDark) {
        Color.White.copy(alpha = if (isMuted) 0.10f else 0.18f)
    } else {
        Color.Black.copy(alpha = if (isMuted) 0.07f else 0.12f)
    }

    val textColor = when {
        isPrimary -> onAccentColor(accentColor)
        isMuted -> appColors.textSecondary
        else -> appColors.textPrimary
    }

    Box(
        modifier = modifier
            .jellyTapScale(jellyState)
            .clip(pillShape)
            .then(backgroundModifier)
            .border(width = 0.9.dp, color = borderColor, shape = pillShape)
            .clickable(
                interactionSource = jellyState.interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontFamily = GoogleSans,
            fontSize = if (isPrimary) 13.5.sp else 12.5.sp,
            fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Auto-Generated Smart Playlists ("Sugerencias para ti") section displayed on the Home feed.
 * These smart playlists (Top 20 de la última semana, Mix de Géneros Escuchados [25], En Repetición,
 * Descubrimiento según tus gustos) are NEVER auto-saved to the user's library unless the user explicitly
 * taps the "Guardar" button.
 */
@Composable
fun SmartSuggestedPlaylistsSection(
    playlists: List<UserPlaylist>,
    savedPlaylistTitles: Set<String>,
    onPlaylistClick: (UserPlaylist) -> Unit,
    onPlayPlaylistClick: (UserPlaylist) -> Unit,
    onSavePlaylistClick: (UserPlaylist) -> Unit,
    appColors: AppThemeColors = LocalAppColors.current,
    modifier: Modifier = Modifier,
    accentColor: Color = LocalAccentColor.current
) {
    if (playlists.isEmpty()) return
    val listState = rememberLazyListState()

    Column(modifier = modifier.fillMaxWidth()) {
        FeedSectionHeader(
            title = "Suggested for You",
            appColors = appColors,
            leadingBadgeIcon = Icons.Rounded.AutoAwesome,
            accentColor = accentColor,
            trailingBadgeText = "Smart Mix"
        )

        Spacer(modifier = Modifier.height(14.dp))

        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(
                items = playlists,
                key = { index, item -> "smart_home_${item.id}_$index" }
            ) { index, playlist ->
                val isSaved = savedPlaylistTitles.contains(playlist.title.trim().lowercase())
                SmartSuggestedPlaylistHomeCard(
                    playlist = playlist,
                    isSaved = isSaved,
                    accentColor = accentColor,
                    onClick = { onPlaylistClick(playlist) },
                    onPlayClick = { onPlayPlaylistClick(playlist) },
                    onSaveClick = { onSavePlaylistClick(playlist) },
                    modifier = Modifier
                        .width(196.dp)
                        .height(228.dp)
                        .bentoScrollTransform(listState, index)
                )
            }
        }
    }
}

@Composable
private fun SmartSuggestedPlaylistHomeCard(
    playlist: UserPlaylist,
    isSaved: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val jellyState = rememberJellyPressState()
    val badgeLabel = playlist.suggestionBadge.ifBlank {
        if (playlist.tracks.size == 1) "1 song" else "${playlist.tracks.size} songs"
    }

    GlossyCardShell(
        shape = FeedCardShape,
        jellyState = jellyState,
        onClick = onClick,
        modifier = modifier
    ) {
        PlaylistCoverImage(
            playlist = playlist,
            shape = FeedCardShape,
            fallbackIconSize = 48.dp,
            modifier = Modifier.matchParentSize()
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.20f),
                        0.38f to Color.Transparent,
                        0.68f to Color.Black.copy(alpha = 0.65f),
                        1.0f to Color.Black.copy(alpha = 0.90f)
                    )
                )
        )

        FrostedCardBadge(
            text = badgeLabel,
            icon = Icons.Rounded.AutoAwesome,
            dotColor = accentColor,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
        )

        // Explicit Save to Library pill button (top right)
        val saveBg = if (isSaved) accentColor.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.56f)
        val saveFg = if (isSaved) onAccentColor(accentColor) else Color.White
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .clip(CircleShape)
                .background(saveBg)
                .border(0.8.dp, if (isSaved) Color.White.copy(alpha = 0.5f) else accentColor.copy(alpha = 0.65f), CircleShape)
                .clickable { onSaveClick() }
                .padding(horizontal = 9.dp, vertical = 5.dp)
        ) {
            Icon(
                imageVector = if (isSaved) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                contentDescription = if (isSaved) "Saved" else "Save to Library",
                tint = saveFg,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = if (isSaved) "Saved" else "Save",
                color = saveFg,
                fontFamily = GoogleSans,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = playlist.title,
                        color = Color.White,
                        fontFamily = GoogleSans,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        lineHeight = 17.sp,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = playlist.description.ifBlank {
                            if (playlist.tracks.size == 1) "1 song" else "${playlist.tracks.size} songs"
                        },
                        color = Color.White.copy(alpha = 0.78f),
                        fontFamily = PlusJakartaSans,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                        .border(0.8.dp, Color.White.copy(alpha = 0.45f), CircleShape)
                        .clickable { onPlayClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play ${playlist.title}",
                        tint = onAccentColor(accentColor),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
