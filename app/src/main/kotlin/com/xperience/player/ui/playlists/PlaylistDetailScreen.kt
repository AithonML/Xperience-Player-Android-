package com.xperience.player.ui.playlists

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.domain.model.UserPlaylist
import com.xperience.player.ui.adaptive.AdaptivePreviewFixtures
import com.xperience.player.ui.adaptive.AdaptivePreviewSurface
import com.xperience.player.ui.adaptive.LocalBottomChromePadding
import com.xperience.player.ui.adaptive.LocalTopChromePadding
import com.xperience.player.ui.component.ExplicitBadge
import com.xperience.player.ui.component.GlassCircleButton
import com.xperience.player.ui.component.GlassComponent
import com.xperience.player.ui.component.LocalAppBackdrop
import com.xperience.player.ui.component.LocalGlassEffectConfig
import com.xperience.player.ui.component.backdrop.backdrops.layerBackdrop
import com.xperience.player.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.xperience.player.ui.component.isGlassAllowed
import com.xperience.player.ui.component.liquidGlass
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.components.HomeAuroraBackground
import com.xperience.player.ui.components.jellyTapScale
import com.xperience.player.ui.components.onAccentColor
import com.xperience.player.ui.components.readableAccentColor
import com.xperience.player.ui.components.rememberJellyPressState
import com.xperience.player.ui.theme.AuroraColors
import com.xperience.player.ui.theme.DEFAULT_AURORA_COLORS
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.PlusJakartaSans
import com.xperience.player.ui.utils.pressWobble

private val DetailHeroCoverShape = ContinuousRoundedRectangle(size = 24.dp)
private val DetailPillShape = ContinuousRoundedRectangle(percent = 50)
private val DetailTrackThumbShape = ContinuousRoundedRectangle(size = 11.dp)
private val DetailTrackRowShape = ContinuousRoundedRectangle(size = 16.dp)
private val DetailLocalBadgeShape = ContinuousRoundedRectangle(percent = 50)
private val DetailEmptyCardShape = ContinuousRoundedRectangle(size = 22.dp)

/**
 * Full-screen detail view for a user-created playlist with dynamic Aurora backdrop,
 * floating LiquidGlass top navigation & actions, explicit badges, and ordered-queue
 * track playback support.
 */
@Composable
fun PlaylistDetailScreen(
    playlist: UserPlaylist,
    likedTrackIds: Set<String>,
    currentPlayingTrackId: String?,
    isPlaybackPlaying: Boolean,
    onBack: () -> Unit,
    onPlayAllClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onTrackClick: (TrackItem) -> Unit,
    onToggleFavorite: (TrackItem) -> Unit,
    onRemoveTrack: (TrackItem) -> Unit,
    onEditPlaylistClick: () -> Unit,
    onDeletePlaylistClick: () -> Unit,
    modifier: Modifier = Modifier,
    auroraColors: AuroraColors = DEFAULT_AURORA_COLORS,
    onPlayTrackFromList: ((List<TrackItem>, Int) -> Unit)? = null,
    onTrackMoreClick: ((TrackItem) -> Unit)? = null,
    showBackButton: Boolean = true,
    isSavedInLibrary: Boolean = !playlist.isSmartSuggestion,
    onSaveToLibraryClick: (() -> Unit)? = null,
    isYtmSignedIn: Boolean = false,
    isRefreshingImportedPlaylist: Boolean = false,
    onRefreshImportedPlaylistClick: (() -> Unit)? = null
) {
    val appColors = LocalAppColors.current
    val isDark = appColors.isDark
    val accentColor = readableAccentColor(LocalAccentColor.current, isDark)
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val topChromePadding = LocalTopChromePadding.current
    val bottomChromePadding = LocalBottomChromePadding.current

    val detailBackdrop = rememberLayerBackdrop()
    val listState = rememberLazyListState()
    val isScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 140
        }
    }

    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerHeightDp = if (headerHeightPx > 0) {
        with(density) { headerHeightPx.toDp() }
    } else {
        92.dp
    }

    val deleteTint = if (isDark) Color(0xFFFF6B7A) else Color(0xFFD92D20)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(appColors.background)
    ) {
        // Layer 1: Aurora backdrop + scrollable hero & tracks recorded for LiquidGlass top chrome
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(detailBackdrop)
        ) {
            HomeAuroraBackground(
                auroraColors = auroraColors,
                isDark = isDark,
                scrollOffsetPxProvider = {
                    if (listState.firstVisibleItemIndex == 0) {
                        listState.firstVisibleItemScrollOffset
                    } else {
                        1200 + listState.firstVisibleItemIndex * 180
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Ambient radial hero bloom centered behind cover art
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .drawBehind {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = if (isDark) 0.16f else 0.10f),
                                    Color.Transparent
                                ),
                                center = Offset(size.width * 0.5f, size.height * 0.24f),
                                radius = size.width * 0.85f
                            )
                        )
                    }
            )

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    top = headerHeightDp + 10.dp,
                    bottom = bottomChromePadding
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize()
            ) {
                item(key = "playlist_hero") {
                    PlaylistDetailHeroSection(
                        playlist = playlist,
                        accentColor = accentColor,
                        isSavedInLibrary = isSavedInLibrary,
                        onSaveToLibraryClick = onSaveToLibraryClick,
                        onPlayAllClick = onPlayAllClick,
                        onShuffleClick = onShuffleClick
                    )
                }

                if (playlist.tracks.isEmpty()) {
                    item(key = "playlist_empty_tracks") {
                        PlaylistTracksEmptyCard(
                            accentColor = accentColor,
                            modifier = Modifier
                                .widthIn(max = 560.dp)
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        )
                    }
                } else {
                    item(key = "playlist_tracks_header") {
                        PlaylistTracksSectionHeader(
                            trackCount = playlist.tracks.size,
                            durationLabel = playlist.formattedDuration,
                            accentColor = accentColor,
                            modifier = Modifier.widthIn(max = 720.dp)
                        )
                    }

                    itemsIndexed(
                        items = playlist.tracks,
                        key = { index, track -> "pl_track_${index}_${track.id}" }
                    ) { index, track ->
                        val isPlayingThis = track.id == currentPlayingTrackId
                        val isLiked = likedTrackIds.contains(track.id)

                        PlaylistTrackRow(
                            index = index + 1,
                            track = track,
                            isPlaying = isPlayingThis && isPlaybackPlaying,
                            isCurrentTrack = isPlayingThis,
                            isLiked = isLiked,
                            accentColor = accentColor,
                            onTrackClick = {
                                if (onPlayTrackFromList != null) {
                                    onPlayTrackFromList(playlist.tracks, index)
                                } else {
                                    onTrackClick(track)
                                }
                            },
                            onToggleFavorite = { onToggleFavorite(track) },
                            onRemoveTrack = { onRemoveTrack(track) },
                            onMoreClick = onTrackMoreClick?.let { callback -> { callback(track) } },
                            modifier = Modifier
                                .widthIn(max = 720.dp)
                                .padding(horizontal = 12.dp, vertical = 2.dp)
                                .animateItem(
                                    fadeInSpec = tween(220),
                                    fadeOutSpec = tween(160),
                                    placementSpec = spring(
                                        dampingRatio = 0.84f,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                        )
                    }
                }
            }
        }

        // Layer 2: Soft top readability gradient + floating LiquidGlass header buttons
        CompositionLocalProvider(LocalAppBackdrop provides detailBackdrop) {
            Box(modifier = Modifier.fillMaxSize()) {
                val topAlpha = if (isScrolled) {
                    if (isDark) 0.84f else 0.90f
                } else {
                    if (isDark) 0.40f else 0.56f
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(headerHeightDp + 28.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to appColors.background.copy(alpha = topAlpha),
                                    0.6f to appColors.background.copy(alpha = topAlpha * 0.45f),
                                    1.0f to Color.Transparent
                                )
                            )
                        )
                )

                val glassConfig = LocalGlassEffectConfig.current
                val useGlass = glassConfig.isEnabledFor(GlassComponent.NAV_BAR) && isGlassAllowed()

                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = topChromePadding)
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                        .onSizeChanged { headerHeightPx = it.height },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showBackButton) {
                        GlassCircleButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onBack()
                            },
                            size = 44.dp
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = if (isDark) Color.White else appColors.glassTextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(44.dp))
                    }

                    AnimatedVisibility(
                        visible = isScrolled,
                        enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.93f, animationSpec = tween(220)),
                        exit = fadeOut(tween(150)),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp)
                    ) {
                        val titleChipModifier = Modifier
                            .height(40.dp)
                            .then(
                                if (useGlass) {
                                    Modifier.liquidGlass(
                                        config = glassConfig,
                                        shape = DetailPillShape,
                                        highlightAlpha = 0.18f
                                    )
                                } else {
                                    Modifier
                                        .clip(DetailPillShape)
                                        .background(
                                            if (isDark) Color(0xFF1A1E29).copy(alpha = 0.90f)
                                            else Color.White.copy(alpha = 0.90f)
                                        )
                                }
                            )
                            .border(
                                0.75.dp,
                                if (isDark) Color.White.copy(alpha = 0.14f)
                                else appColors.textPrimary.copy(alpha = 0.10f),
                                DetailPillShape
                            )
                            .padding(horizontal = 14.dp)

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = titleChipModifier
                        ) {
                            Text(
                                text = playlist.title,
                                fontFamily = GoogleSans,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = if (useGlass) appColors.glassTextPrimary else appColors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (playlist.isSmartSuggestion && onSaveToLibraryClick != null) {
                        GlassCircleButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSaveToLibraryClick()
                            },
                            size = 44.dp
                        ) {
                            Icon(
                                imageVector = if (isSavedInLibrary) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                                contentDescription = if (isSavedInLibrary) "Saved" else "Save to Library",
                                tint = if (isSavedInLibrary) accentColor else (if (isDark) Color.White else appColors.glassTextPrimary),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (playlist.isFromYouTubeMusic && isYtmSignedIn && onRefreshImportedPlaylistClick != null) {
                                GlassCircleButton(
                                    onClick = {
                                        if (!isRefreshingImportedPlaylist) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onRefreshImportedPlaylistClick()
                                        }
                                    },
                                    size = 44.dp
                                ) {
                                    if (isRefreshingImportedPlaylist) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = if (isDark) Color.White else appColors.glassTextPrimary
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Refresh,
                                            contentDescription = "Refresh from YouTube Music",
                                            tint = if (isDark) Color.White else appColors.glassTextPrimary,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }
                            }

                            GlassCircleButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onEditPlaylistClick()
                                },
                                size = 44.dp
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = "Edit playlist",
                                    tint = if (isDark) Color.White else appColors.glassTextPrimary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            GlassCircleButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onDeletePlaylistClick()
                                },
                                size = 44.dp
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = if (playlist.isFromYouTubeMusic) "Remove from app" else "Delete playlist",
                                    tint = deleteTint,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistDetailHeroSection(
    playlist: UserPlaylist,
    accentColor: Color,
    isSavedInLibrary: Boolean,
    onSaveToLibraryClick: (() -> Unit)?,
    onPlayAllClick: () -> Unit,
    onShuffleClick: () -> Unit
) {
    val appColors = LocalAppColors.current
    val isDark = appColors.isDark
    val haptic = LocalHapticFeedback.current
    val hasTracks = playlist.tracks.isNotEmpty()
    val ytmRed = if (isDark) YouTubeMusicBrandRedDark else YouTubeMusicBrandRedLight

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp)
    ) {
        // Glossy square artwork frame matching Home's album card finish
        val rimBrush = Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = if (isDark) 0.28f else 0.70f),
                Color.White.copy(alpha = if (isDark) 0.08f else 0.20f)
            )
        )
        Box(
            modifier = Modifier
                .size(214.dp)
                .shadow(
                    elevation = if (isDark) 24.dp else 16.dp,
                    shape = DetailHeroCoverShape,
                    ambientColor = accentColor.copy(alpha = 0.35f),
                    spotColor = accentColor.copy(alpha = 0.45f)
                )
                .clip(DetailHeroCoverShape)
                .border(0.95.dp, rimBrush, DetailHeroCoverShape)
        ) {
            PlaylistCoverImage(
                playlist = playlist,
                shape = DetailHeroCoverShape,
                fallbackIconSize = 68.dp,
                modifier = Modifier.fillMaxSize()
            )
            // Subtle top specular sheen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            brush = Brush.verticalGradient(
                                0.0f to Color.White.copy(alpha = if (isDark) 0.10f else 0.18f),
                                0.3f to Color.Transparent,
                                1.0f to Color.Black.copy(alpha = 0.16f)
                            )
                        )
                    }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = playlist.title,
            fontFamily = GoogleSans,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            letterSpacing = (-0.4).sp,
            color = appColors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        if (playlist.description.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = playlist.description,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Normal,
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
                color = appColors.textSecondary,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Metadata & YouTube Music source badges
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (playlist.isFromYouTubeMusic) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier
                        .clip(DetailPillShape)
                        .background(ytmRed.copy(alpha = if (isDark) 0.16f else 0.11f))
                        .border(0.75.dp, ytmRed.copy(alpha = 0.45f), DetailPillShape)
                        .padding(horizontal = 11.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayCircle,
                        contentDescription = null,
                        tint = ytmRed,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "YouTube Music",
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = ytmRed
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(DetailPillShape)
                    .background(
                        if (isDark) Color.White.copy(alpha = 0.07f)
                        else appColors.textPrimary.copy(alpha = 0.06f)
                    )
                    .border(
                        0.65.dp,
                        if (isDark) Color.White.copy(alpha = 0.12f)
                        else appColors.textPrimary.copy(alpha = 0.09f),
                        DetailPillShape
                    )
                    .padding(horizontal = 13.dp, vertical = 5.dp)
            ) {
                Text(
                    text = playlist.summarySubtitle,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = appColors.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Tactile Play & Shuffle action strip
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (hasTracks) 1f else 0.45f)
        ) {
            val playInteraction = remember { MutableInteractionSource() }
            val playInk = onAccentColor(accentColor)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .pressWobble(playInteraction, pressedScale = 0.94f)
                    .shadow(
                        elevation = if (hasTracks) 10.dp else 0.dp,
                        shape = DetailPillShape,
                        ambientColor = accentColor.copy(alpha = 0.40f),
                        spotColor = accentColor.copy(alpha = 0.48f)
                    )
                    .clip(DetailPillShape)
                    .background(
                        Brush.linearGradient(
                            listOf(accentColor, accentColor.copy(alpha = 0.85f))
                        )
                    )
                    .border(
                        0.8.dp,
                        Color.White.copy(alpha = if (isDark) 0.28f else 0.42f),
                        DetailPillShape
                    )
                    .clickable(
                        interactionSource = playInteraction,
                        indication = ripple(bounded = true),
                        enabled = hasTracks
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPlayAllClick()
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = playInk,
                        modifier = Modifier.size(21.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Play",
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = playInk
                    )
                }
            }

            val shuffleInteraction = remember { MutableInteractionSource() }
            val shuffleInk = appColors.textPrimary
            // Rendered inside detailBackdrop/appBackdrop, so avoid sampling LocalAppBackdrop here.
            val shuffleModifier = Modifier
                .weight(1f)
                .height(48.dp)
                .pressWobble(shuffleInteraction, pressedScale = 0.94f)
                .clip(DetailPillShape)
                .background(
                    if (isDark) Color.White.copy(alpha = 0.12f)
                    else Color.White.copy(alpha = 0.82f)
                )
                .border(
                    0.8.dp,
                    if (isDark) Color.White.copy(alpha = 0.18f)
                    else appColors.textPrimary.copy(alpha = 0.11f),
                    DetailPillShape
                )
                .clickable(
                    interactionSource = shuffleInteraction,
                    indication = ripple(bounded = true),
                    enabled = hasTracks
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onShuffleClick()
                }

            Box(
                contentAlignment = Alignment.Center,
                modifier = shuffleModifier
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Shuffle",
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = shuffleInk
                    )
                }
            }
        }

        if (playlist.isSmartSuggestion && onSaveToLibraryClick != null) {
            Spacer(modifier = Modifier.height(12.dp))
            val saveInteraction = remember { MutableInteractionSource() }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .pressWobble(saveInteraction, pressedScale = 0.96f)
                    .clip(DetailPillShape)
                    .background(
                        if (isSavedInLibrary) {
                            accentColor.copy(alpha = if (isDark) 0.22f else 0.14f)
                        } else {
                            if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.85f)
                        }
                    )
                    .border(
                        1.dp,
                        accentColor.copy(alpha = if (isSavedInLibrary) 0.75f else 0.45f),
                        DetailPillShape
                    )
                    .clickable(
                        interactionSource = saveInteraction,
                        indication = ripple(bounded = true)
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSaveToLibraryClick()
                    }
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isSavedInLibrary) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isSavedInLibrary) "Saved" else "Save to Library",
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = if (isSavedInLibrary) accentColor else appColors.textPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistTracksSectionHeader(
    trackCount: Int,
    durationLabel: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val isDark = appColors.isDark

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Tracks",
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = 16.5.sp,
                color = appColors.textPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(DetailPillShape)
                    .background(accentColor.copy(alpha = if (isDark) 0.16f else 0.12f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = trackCount.toString(),
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = accentColor
                )
            }
        }

        if (durationLabel.isNotBlank()) {
            Text(
                text = durationLabel,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = appColors.textSecondary
            )
        }
    }
}

@Composable
private fun PlaylistTracksEmptyCard(
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val isDark = appColors.isDark

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .clip(DetailEmptyCardShape)
            .background(
                if (isDark) Color.White.copy(alpha = 0.05f)
                else Color.White.copy(alpha = 0.72f)
            )
            .border(
                0.8.dp,
                if (isDark) Color.White.copy(alpha = 0.12f)
                else appColors.textPrimary.copy(alpha = 0.08f),
                DetailEmptyCardShape
            )
            .padding(horizontal = 24.dp, vertical = 32.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = accentColor.copy(alpha = 0.75f),
            modifier = Modifier.size(38.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "No tracks in this playlist yet",
            fontFamily = GoogleSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.5.sp,
            color = appColors.textPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Open any song's menu and choose Add to Playlist to start curating.",
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            color = appColors.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PlaylistTrackRow(
    index: Int,
    track: TrackItem,
    isPlaying: Boolean,
    isCurrentTrack: Boolean,
    isLiked: Boolean,
    accentColor: Color,
    onTrackClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRemoveTrack: () -> Unit,
    onMoreClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val isDark = appColors.isDark
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val rowJelly = rememberJellyPressState(pressedScaleX = 0.97f, pressedScaleY = 0.955f)

    val isLocal = track.audioUrl?.startsWith("content://") == true ||
        track.audioUrl?.startsWith("file://") == true ||
        track.audioUrl?.startsWith("/") == true ||
        track.id.startsWith("local_")

    val rowBackground = if (isCurrentTrack) {
        accentColor.copy(alpha = if (isDark) 0.14f else 0.10f)
    } else {
        Color.Transparent
    }
    val rowBorder = if (isCurrentTrack) {
        accentColor.copy(alpha = if (isDark) 0.34f else 0.28f)
    } else {
        Color.Transparent
    }

    val likeInteraction = remember { MutableInteractionSource() }
    val removeInteraction = remember { MutableInteractionSource() }
    val moreInteraction = remember { MutableInteractionSource() }
    val likeScale by animateFloatAsState(
        targetValue = if (isLiked) 1.08f else 1f,
        animationSpec = spring(
            dampingRatio = 0.45f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "playlistLikeScale"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .jellyTapScale(rowJelly)
            .clip(DetailTrackRowShape)
            .background(rowBackground)
            .then(
                if (isCurrentTrack) {
                    Modifier.border(0.75.dp, rowBorder, DetailTrackRowShape)
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = rowJelly.interactionSource,
                indication = ripple(bounded = true)
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onTrackClick()
            }
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        // Index or animated playing equalizer
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.width(26.dp)
        ) {
            if (isCurrentTrack && isPlaying) {
                PlaylistEqualizerBars(color = accentColor)
            } else {
                Text(
                    text = index.toString(),
                    fontFamily = PlusJakartaSans,
                    fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 12.5.sp,
                    color = if (isCurrentTrack) accentColor else appColors.textTertiary
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Rounded artwork thumbnail with hairline border
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(DetailTrackThumbShape)
                .background(
                    if (isDark) Color(0xFF1E2230) else Color(0xFFE7EBF2)
                )
                .border(
                    0.65.dp,
                    if (isDark) Color.White.copy(alpha = 0.12f)
                    else appColors.textPrimary.copy(alpha = 0.08f),
                    DetailTrackThumbShape
                )
        ) {
            if (!track.artworkUri.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(track.artworkUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = accentColor.copy(alpha = 0.72f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title + ExplicitBadge + LOCAL pill + artist/duration metadata
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.title,
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.5.sp,
                    color = if (isCurrentTrack) accentColor else appColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (track.isExplicit) {
                    Spacer(modifier = Modifier.width(6.dp))
                    ExplicitBadge(compact = true)
                }

                if (isLocal) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(DetailLocalBadgeShape)
                            .background(accentColor.copy(alpha = 0.14f))
                            .border(
                                0.6.dp,
                                accentColor.copy(alpha = 0.36f),
                                DetailLocalBadgeShape
                            )
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "LOCAL",
                            fontFamily = GoogleSans,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            val durationText = if (track.durationMs > 0L) track.formattedDuration else null
            val subtitleText = if (durationText != null) {
                "${track.artist} · $durationText"
            } else {
                track.artist
            }
            Text(
                text = subtitleText,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                color = appColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Favorite heart toggle
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .pressWobble(likeInteraction, pressedScale = 0.80f)
                .size(34.dp)
                .clip(DetailPillShape)
                .clickable(
                    interactionSource = likeInteraction,
                    indication = ripple(bounded = true, radius = 17.dp)
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleFavorite()
                }
        ) {
            Icon(
                imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (isLiked) "Unlike" else "Like",
                tint = if (isLiked) Color(0xFFFF4D6D) else appColors.textSecondary,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer {
                        scaleX = likeScale
                        scaleY = likeScale
                    }
            )
        }

        if (onMoreClick != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .pressWobble(moreInteraction, pressedScale = 0.82f)
                    .size(34.dp)
                    .clip(DetailPillShape)
                    .clickable(
                        interactionSource = moreInteraction,
                        indication = ripple(bounded = true, radius = 17.dp)
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onMoreClick()
                    }
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Track options",
                    tint = appColors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Remove from playlist
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .pressWobble(removeInteraction, pressedScale = 0.82f)
                .size(34.dp)
                .clip(DetailPillShape)
                .clickable(
                    interactionSource = removeInteraction,
                    indication = ripple(bounded = true, radius = 17.dp)
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onRemoveTrack()
                }
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Remove from playlist",
                tint = appColors.textTertiary,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
private fun PlaylistEqualizerBars(color: Color) {
    val transition = rememberInfiniteTransition(label = "playlistEq")
    val bar1 by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(380, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eqBar1"
    )
    val bar2 by transition.animateFloat(
        initialValue = 0.95f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(460, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eqBar2"
    )
    val bar3 by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.90f,
        animationSpec = infiniteRepeatable(
            animation = tween(340, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eqBar3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(14.dp)
    ) {
        listOf(bar1, bar2, bar3).forEach { fraction ->
            Box(
                modifier = Modifier
                    .width(2.6.dp)
                    .height(14.dp * fraction)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun PlaylistDetailPreviewContent(widthDp: Int, heightDp: Int) {
    AdaptivePreviewSurface(widthDp = widthDp, heightDp = heightDp) {
        PlaylistDetailScreen(
            playlist = AdaptivePreviewFixtures.playlists.first(),
            likedTrackIds = setOf("ytm_paparazzi"),
            currentPlayingTrackId = "ytm_paparazzi",
            isPlaybackPlaying = true,
            onBack = {},
            onPlayAllClick = {},
            onShuffleClick = {},
            onTrackClick = {},
            onToggleFavorite = {},
            onRemoveTrack = {},
            onEditPlaylistClick = {},
            onDeletePlaylistClick = {}
        )
    }
}

@Preview(name = "Phone (360x800)", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
private fun PlaylistDetailPhonePreview() = PlaylistDetailPreviewContent(360, 800)

@Preview(name = "Tablet Portrait (800x1280)", widthDp = 800, heightDp = 1280, showBackground = true)
@Composable
private fun PlaylistDetailTabletPortraitPreview() = PlaylistDetailPreviewContent(800, 1280)

@Preview(name = "Tablet Landscape (1280x800)", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
private fun PlaylistDetailTabletLandscapePreview() = PlaylistDetailPreviewContent(1280, 800)
