package com.xperience.player.ui.album

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
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
import com.xperience.player.domain.model.AlbumDetailProfile
import com.xperience.player.domain.model.AlbumItem
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.service.MediaNetworkClients
import com.xperience.player.ui.adaptive.AdaptivePreviewFixtures
import com.xperience.player.ui.adaptive.AdaptivePreviewSurface
import com.xperience.player.ui.adaptive.LocalAppWindowSizeClass
import com.xperience.player.ui.adaptive.LocalBottomChromePadding
import com.xperience.player.ui.adaptive.LocalTopChromePadding
import com.xperience.player.ui.adaptive.supportsTwoPaneLayout
import com.xperience.player.ui.component.AlbumTracksSkeleton
import com.xperience.player.ui.component.ExplicitBadge
import com.xperience.player.ui.component.GlassCircleButton
import com.xperience.player.ui.component.HeroSubtitleSkeleton
import com.xperience.player.ui.component.LocalAppBackdrop
import com.xperience.player.ui.component.MediaShelfSkeleton
import com.xperience.player.ui.component.ShimmerBox
import com.xperience.player.ui.component.ShimmerScope
import com.xperience.player.ui.component.backdrop.backdrops.layerBackdrop
import com.xperience.player.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.player.CanvasArtworkPlayer
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.PlusJakartaSans
import com.xperience.player.ui.utils.pressWobble

private val AlbumRecCardShape = ContinuousRoundedRectangle(size = 18.dp)

private enum class AlbumSectionState { LOADING, CONTENT, EMPTY }

@Composable
fun AlbumScreen(
    profile: AlbumDetailProfile,
    isLoading: Boolean,
    likedTrackIds: Set<String>,
    ambientBackgroundColor: Color,
    currentPlayingTrackId: String?,
    isPlaybackPlaying: Boolean,
    onBack: () -> Unit,
    onPlayAllClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onTrackClick: (TrackItem) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onTrackMoreClick: (TrackItem) -> Unit,
    onArtistClick: (String) -> Unit,
    onAlbumClick: (AlbumItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val sharedImageLoader = remember(appContext) {
        MediaNetworkClients.getImageLoader(appContext)
    }
    val appColors = LocalAppColors.current
    val accentColor = LocalAccentColor.current
    val isDark = appColors.isDark
    val scrollState = rememberScrollState()
    val windowSizeClass = LocalAppWindowSizeClass.current
    val isTwoPane = windowSizeClass.supportsTwoPaneLayout
    val topChromePadding = LocalTopChromePadding.current
    val bottomChromePadding = LocalBottomChromePadding.current

    val tracksLoading = isLoading || profile.isTracksLoading

    // Smoothly animate the adaptive ambient background color extracted from the album cover
    val animatedBgColorState: State<Color> = animateColorAsState(
        targetValue = ambientBackgroundColor,
        animationSpec = tween(durationMillis = 650),
        label = "albumAmbientBg"
    )

    // Dedicated backdrop for the Album Hero Header:
    // Captures the cover artwork and ambient background so LiquidGlass buttons
    // refract the artwork without recreating the LayerBackdrop instance on animation ticks.
    val albumBackdrop = rememberLayerBackdrop(
        onDraw = remember(animatedBgColorState) {
            {
                drawRect(animatedBgColorState.value)
                drawContent()
            }
        }
    )

    var isDescExpanded by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .drawBehind { drawRect(animatedBgColorState.value) }
    ) {
        val headerHeight = (maxHeight * 0.50f).coerceIn(420.dp, 520.dp)
        val headerHeightPx = with(LocalDensity.current) { headerHeight.toPx() }
        val isHeroVisible by remember(headerHeightPx) {
            derivedStateOf { isTwoPane || scrollState.value < headerHeightPx }
        }

        ShimmerScope {
            if (isTwoPane) {
                // Expanded 2-pane album view: left fixed cover/metadata + right scrollable tracklist & shelves
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(0.40f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .statusBarsPadding()
                            .padding(top = topChromePadding + 14.dp, bottom = bottomChromePadding)
                    ) {
                        CompositionLocalProvider(LocalAppBackdrop provides albumBackdrop) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                GlassCircleButton(
                                    onClick = onBack,
                                    size = 44.dp
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = if (isDark) Color.White else appColors.glassTextPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            val coverShape = remember { ContinuousRoundedRectangle(size = 24.dp) }
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 310.dp)
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .shadow(
                                        elevation = 24.dp,
                                        shape = coverShape,
                                        ambientColor = accentColor.copy(alpha = 0.25f),
                                        spotColor = Color.Black.copy(alpha = 0.40f)
                                    )
                                    .clip(coverShape)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .layerBackdrop(albumBackdrop),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Album,
                                            contentDescription = null,
                                            tint = accentColor.copy(alpha = 0.25f),
                                            modifier = Modifier.size(84.dp)
                                        )
                                    }
                                    if (profile.coverArtUri.isNotBlank()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(appContext)
                                                .data(profile.coverArtUri)
                                                .crossfade(350)
                                                .build(),
                                            imageLoader = sharedImageLoader,
                                            contentDescription = profile.title,
                                            contentScale = ContentScale.Crop,
                                            alignment = Alignment.Center,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                                if (profile.hasCanvasVideo) {
                                    CanvasArtworkPlayer(
                                        videoUri = profile.canvasVideoUri,
                                        isPlaying = isHeroVisible,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            AlbumHeaderMetadata(
                                profile = profile,
                                tracksLoading = tracksLoading,
                                isOverHeroBackdrop = false,
                                isDescExpanded = isDescExpanded,
                                onToggleDescExpanded = { isDescExpanded = !isDescExpanded },
                                onArtistClick = onArtistClick,
                                onPlayAllClick = onPlayAllClick,
                                onShuffleClick = onShuffleClick,
                                modifier = Modifier.widthIn(max = 400.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(0.60f)
                            .fillMaxHeight()
                            .verticalScroll(scrollState)
                            .statusBarsPadding()
                            .padding(top = topChromePadding + 14.dp, bottom = bottomChromePadding)
                    ) {
                        AlbumTracklistAndMore(
                            profile = profile,
                            tracksLoading = tracksLoading,
                            likedTrackIds = likedTrackIds,
                            currentPlayingTrackId = currentPlayingTrackId,
                            isPlaybackPlaying = isPlaybackPlaying,
                            onTrackClick = onTrackClick,
                            onToggleFavorite = onToggleFavorite,
                            onTrackMoreClick = onTrackMoreClick,
                            onAlbumClick = onAlbumClick
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // =========================================================================
                    // HERO HEADER (Full Width Cover + Apple Music Canvas Video + Dissolve Gradient)
                    // =========================================================================
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(headerHeight)
                            .clipToBounds()
                    ) {
                        // 1. BACKDROP SOURCE LAYER (Static cover recorded into albumBackdrop for LiquidGlass)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clipToBounds()
                                .layerBackdrop(albumBackdrop)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Album,
                                    contentDescription = null,
                                    tint = accentColor.copy(alpha = 0.25f),
                                    modifier = Modifier.size(96.dp)
                                )
                            }

                            if (profile.coverArtUri.isNotBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(appContext)
                                        .data(profile.coverArtUri)
                                        .crossfade(350)
                                        .build(),
                                    imageLoader = sharedImageLoader,
                                    contentDescription = profile.title,
                                    contentScale = ContentScale.Crop,
                                    alignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        // LAYER 2: Apple Music Looping Canvas Video rendered directly over cover (outside LayerBackdrop)
                        if (profile.hasCanvasVideo) {
                            CanvasArtworkPlayer(
                                videoUri = profile.canvasVideoUri,
                                isPlaying = isHeroVisible,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // LAYER 3A: Bottom-to-Top Seamless Dissolve Gradient fusing into adaptive ambient color
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .drawBehind {
                                    val bg = animatedBgColorState.value
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colorStops = arrayOf(
                                                0.00f to bg.copy(alpha = if (isDark) 0.05f else 0.02f),
                                                0.25f to bg.copy(alpha = if (isDark) 0.18f else 0.12f),
                                                0.50f to bg.copy(alpha = if (isDark) 0.52f else 0.45f),
                                                0.72f to bg.copy(alpha = if (isDark) 0.85f else 0.80f),
                                                1.00f to bg
                                            )
                                        )
                                    )
                                }
                        )

                        // LAYER 3B: Soft Reading Vignette Dissolving into adaptive ambient color
                        val vignetteSideAlpha = if (isDark) 0.70f else 0.30f
                        val vignetteMidAlpha = if (isDark) 0.30f else 0.12f
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .drawBehind {
                                    val bg = animatedBgColorState.value
                                    drawRect(
                                        brush = Brush.horizontalGradient(
                                            colorStops = arrayOf(
                                                0.00f to bg.copy(alpha = vignetteSideAlpha),
                                                0.25f to bg.copy(alpha = vignetteMidAlpha),
                                                0.75f to Color.Transparent,
                                                1.00f to bg.copy(alpha = vignetteSideAlpha)
                                            )
                                        )
                                    )
                                }
                        )

                        // 2. FLOATING LIQUID GLASS CONTROLS & ALBUM METADATA
                        CompositionLocalProvider(LocalAppBackdrop provides albumBackdrop) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .statusBarsPadding()
                                        .padding(top = topChromePadding)
                                        .padding(horizontal = 20.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.Start,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    GlassCircleButton(
                                        onClick = onBack,
                                        size = 44.dp
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back",
                                            tint = if (isDark) Color.White else appColors.glassTextPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                AlbumHeaderMetadata(
                                    profile = profile,
                                    tracksLoading = tracksLoading,
                                    isOverHeroBackdrop = true,
                                    isDescExpanded = isDescExpanded,
                                    onToggleDescExpanded = { isDescExpanded = !isDescExpanded },
                                    onArtistClick = onArtistClick,
                                    onPlayAllClick = onPlayAllClick,
                                    onShuffleClick = onShuffleClick,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .widthIn(max = 580.dp)
                                        .padding(horizontal = 24.dp, vertical = 14.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    AlbumTracklistAndMore(
                        profile = profile,
                        tracksLoading = tracksLoading,
                        likedTrackIds = likedTrackIds,
                        currentPlayingTrackId = currentPlayingTrackId,
                        isPlaybackPlaying = isPlaybackPlaying,
                        onTrackClick = onTrackClick,
                        onToggleFavorite = onToggleFavorite,
                        onTrackMoreClick = onTrackMoreClick,
                        onAlbumClick = onAlbumClick,
                        trackListMaxWidth = 680.dp
                    )

                    // Bottom breathing space for floating miniplayer and navbar
                    Spacer(modifier = Modifier.height(bottomChromePadding))
                }
            }
        }
    }
}

@Composable
private fun AlbumHeaderMetadata(
    profile: AlbumDetailProfile,
    tracksLoading: Boolean,
    isOverHeroBackdrop: Boolean,
    isDescExpanded: Boolean,
    onToggleDescExpanded: () -> Unit,
    onArtistClick: (String) -> Unit,
    onPlayAllClick: () -> Unit,
    onShuffleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val accentColor = LocalAccentColor.current
    val isDark = appColors.isDark

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = profile.title.ifBlank { "Album" },
            fontFamily = GoogleSans,
            fontSize = if (isOverHeroBackdrop) 28.sp else 24.sp,
            fontWeight = FontWeight.Bold,
            color = if (isDark) Color.White else appColors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        if (profile.artist.isNotBlank()) {
            val artistInteraction = remember { MutableInteractionSource() }
            val haptic = LocalHapticFeedback.current
            Box(
                modifier = Modifier
                    .pressWobble(artistInteraction, pressedScale = 0.93f)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(
                        interactionSource = artistInteraction,
                        indication = ripple(bounded = true)
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onArtistClick(profile.artist)
                    }
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                Text(
                    text = profile.artist,
                    fontFamily = GoogleSans,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor,
                    textAlign = TextAlign.Center
                )
            }
        }

        val subtitleState = when {
            profile.displaySubtitle.isNotBlank() -> AlbumSectionState.CONTENT
            tracksLoading -> AlbumSectionState.LOADING
            else -> AlbumSectionState.EMPTY
        }
        AnimatedContent(
            targetState = subtitleState,
            transitionSpec = { fadeIn(tween(280)) togetherWith fadeOut(tween(180)) },
            label = "albumSubtitle"
        ) { state ->
            when (state) {
                AlbumSectionState.LOADING -> HeroSubtitleSkeleton(width = 164.dp)
                AlbumSectionState.CONTENT -> Text(
                    text = profile.displaySubtitle,
                    fontFamily = PlusJakartaSans,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isDark) Color.White.copy(alpha = 0.85f) else appColors.textSecondary,
                    textAlign = TextAlign.Center
                )
                AlbumSectionState.EMPTY -> Spacer(modifier = Modifier.height(0.dp))
            }
        }

        val actionsState = if (profile.tracks.isNotEmpty() || !tracksLoading) {
            AlbumSectionState.CONTENT
        } else {
            AlbumSectionState.LOADING
        }
        AnimatedContent(
            targetState = actionsState,
            transitionSpec = { fadeIn(tween(280)) togetherWith fadeOut(tween(180)) },
            label = "albumActions"
        ) { state ->
            when (state) {
                AlbumSectionState.LOADING -> Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    ShimmerBox(modifier = Modifier.size(54.dp), shape = CircleShape)
                    ShimmerBox(modifier = Modifier.size(54.dp), shape = CircleShape)
                }
                else -> Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    GlassCircleButton(
                        onClick = onPlayAllClick,
                        size = 54.dp
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = if (isDark) Color.White else appColors.glassTextPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    GlassCircleButton(
                        onClick = onShuffleClick,
                        size = 54.dp
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (isDark) Color.White else appColors.glassTextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        if (profile.description.isNotBlank()) {
            val bioInteraction = remember { MutableInteractionSource() }
            val bioHaptic = LocalHapticFeedback.current
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .pressWobble(bioInteraction, pressedScale = 0.97f)
                    .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                    .clickable(
                        interactionSource = bioInteraction,
                        indication = null
                    ) {
                        bioHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleDescExpanded()
                    }
                    .padding(horizontal = 14.dp, vertical = 2.dp)
            ) {
                Text(
                    text = profile.description,
                    fontFamily = PlusJakartaSans,
                    fontSize = 12.sp,
                    color = if (isDark) Color.White.copy(alpha = 0.82f) else appColors.textSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp,
                    maxLines = if (isDescExpanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (profile.description.length > 100) {
                    Text(
                        text = if (isDescExpanded) "LESS" else "MORE",
                        fontFamily = GoogleSans,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumTracklistAndMore(
    profile: AlbumDetailProfile,
    tracksLoading: Boolean,
    likedTrackIds: Set<String>,
    currentPlayingTrackId: String?,
    isPlaybackPlaying: Boolean,
    onTrackClick: (TrackItem) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onTrackMoreClick: (TrackItem) -> Unit,
    onAlbumClick: (AlbumItem) -> Unit,
    trackListMaxWidth: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp.Unspecified
) {
    val appColors = LocalAppColors.current

    val tracksState = when {
        profile.tracks.isNotEmpty() -> AlbumSectionState.CONTENT
        tracksLoading -> AlbumSectionState.LOADING
        else -> AlbumSectionState.EMPTY
    }
    AnimatedContent(
        targetState = tracksState,
        transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(200)) },
        label = "albumTracks"
    ) { state ->
        when (state) {
            AlbumSectionState.LOADING -> Column {
                AlbumTracksSkeleton(rowCount = 7)
                Spacer(modifier = Modifier.height(28.dp))
            }
            AlbumSectionState.CONTENT -> Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .then(if (trackListMaxWidth != androidx.compose.ui.unit.Dp.Unspecified) Modifier.widthIn(max = trackListMaxWidth) else Modifier)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Tracks",
                        fontFamily = GoogleSans,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = appColors.textPrimary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    profile.tracks.forEachIndexed { index, track ->
                        val isCurrentTrack = currentPlayingTrackId == track.id
                        val isLiked = likedTrackIds.contains(track.id)

                        AlbumTrackRow(
                            track = track,
                            trackIndex = index + 1,
                            isCurrentTrack = isCurrentTrack,
                            isPlaying = isPlaybackPlaying,
                            isLiked = isLiked,
                            onClick = { onTrackClick(track) },
                            onToggleFavorite = { onToggleFavorite(track.id) },
                            onMoreClick = { onTrackMoreClick(track) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }
            AlbumSectionState.EMPTY -> Spacer(modifier = Modifier.height(0.dp))
        }
    }

    if (profile.otherVersions.isNotEmpty()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Other Versions",
                    fontFamily = GoogleSans,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = appColors.textPrimary
                )
                Text(
                    text = "Deluxe, Remastered & Alternate Editions",
                    fontFamily = PlusJakartaSans,
                    fontSize = 12.sp,
                    color = appColors.textSecondary
                )
            }

            val safeOtherVersions = remember(profile.otherVersions) {
                profile.otherVersions.distinctBy { album ->
                    album.id.ifBlank { "${album.title.trim().lowercase()}_${album.artist.trim().lowercase()}" }
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                itemsIndexed(
                    items = safeOtherVersions,
                    key = { index, album -> "ver_${index}_${album.id}" }
                ) { _, album ->
                    AlbumRecommendationCard(
                        album = album,
                        onClick = { onAlbumClick(album) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(26.dp))
    }

    val otherAlbumsState = when {
        profile.otherAlbums.isNotEmpty() -> AlbumSectionState.CONTENT
        tracksLoading -> AlbumSectionState.LOADING
        else -> AlbumSectionState.EMPTY
    }
    AnimatedContent(
        targetState = otherAlbumsState,
        transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(200)) },
        label = "albumOtherAlbums"
    ) { state ->
        when (state) {
            AlbumSectionState.LOADING -> Column {
                MediaShelfSkeleton(titleWidth = 160.dp, cardCount = 4)
                Spacer(modifier = Modifier.height(32.dp))
            }
            AlbumSectionState.CONTENT -> Column {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val sectionTitle = if (profile.artist.isNotBlank()) "More by ${profile.artist}" else "Other Albums & Recommendations"
                    Text(
                        text = sectionTitle,
                        fontFamily = GoogleSans,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = appColors.textPrimary,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )

                    val safeOtherAlbums = remember(profile.otherAlbums) {
                        profile.otherAlbums.distinctBy { album ->
                            album.id.ifBlank { "${album.title.trim().lowercase()}_${album.artist.trim().lowercase()}" }
                        }
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        itemsIndexed(
                            items = safeOtherAlbums,
                            key = { index, album -> "other_${index}_${album.id}" }
                        ) { _, album ->
                            AlbumRecommendationCard(
                                album = album,
                                onClick = { onAlbumClick(album) }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
            AlbumSectionState.EMPTY -> Spacer(modifier = Modifier.height(0.dp))
        }
    }
}

/**
 * Clean track row for the album tracklist, featuring track number or animated equalizer,
 * title, artist/playcount, duration, favorite button, and options.
 */
@Composable
private fun AlbumTrackRow(
    track: TrackItem,
    trackIndex: Int,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    isLiked: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val accentColor = LocalAccentColor.current
    val isDark = appColors.isDark

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val rowScale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "albumRowScale"
    )

    val rowBg = when {
        isCurrentTrack -> accentColor.copy(alpha = if (isDark) 0.18f else 0.12f)
        isPressed -> (if (isDark) Color.White else Color.Black).copy(alpha = 0.05f)
        else -> Color.Transparent
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = rowScale
                scaleY = rowScale
            }
            .clip(RoundedCornerShape(14.dp))
            .background(rowBg)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Track Number or Animated Equalizer
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isCurrentTrack && isPlaying) {
                PlayingEqualizerBars(
                    color = accentColor,
                    isPlaying = true
                )
            } else {
                Text(
                    text = "$trackIndex",
                    fontFamily = GoogleSans,
                    fontSize = 13.5.sp,
                    fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Medium,
                    color = if (isCurrentTrack) accentColor else appColors.textSecondary.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Title and Subtitle
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = track.title,
                    fontFamily = GoogleSans,
                    fontSize = 14.sp,
                    fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isCurrentTrack) accentColor else appColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (track.isExplicit) {
                    ExplicitBadge()
                }
            }

            val subtitle = if (track.artist.isNotBlank()) track.artist else track.formattedDuration
            Text(
                text = subtitle,
                fontFamily = PlusJakartaSans,
                fontSize = 11.5.sp,
                color = appColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Duration
        Text(
            text = track.formattedDuration,
            fontFamily = PlusJakartaSans,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = appColors.textSecondary
        )

        val rowHaptic = LocalHapticFeedback.current
        val favInteraction = remember { MutableInteractionSource() }
        val heartPopScale by animateFloatAsState(
            targetValue = if (isLiked) 1.12f else 1.0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "albumTrackHeartPop"
        )

        // Like Button
        Box(
            modifier = Modifier
                .size(32.dp)
                .pressWobble(favInteraction, pressedScale = 0.78f)
                .clip(CircleShape)
                .clickable(
                    interactionSource = favInteraction,
                    indication = ripple(bounded = false, radius = 16.dp)
                ) {
                    rowHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleFavorite()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (isLiked) Color(0xFFFF4081) else appColors.textSecondary.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer {
                        scaleX = heartPopScale
                        scaleY = heartPopScale
                    }
            )
        }

        // More Button
        val moreInteraction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .size(32.dp)
                .pressWobble(moreInteraction, pressedScale = 0.80f)
                .clip(CircleShape)
                .clickable(
                    interactionSource = moreInteraction,
                    indication = ripple(bounded = false, radius = 16.dp)
                ) {
                    rowHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onMoreClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Options",
                tint = appColors.textSecondary,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

/**
 * Recommendation card for recommended/other albums at the bottom of AlbumScreen.
 */
@Composable
private fun AlbumRecommendationCard(
    album: AlbumItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val accentColor = LocalAccentColor.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "recAlbumScale"
    )

    Column(
        modifier = modifier
            .width(136.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(136.dp)
                .shadow(
                    elevation = 6.dp,
                    shape = AlbumRecCardShape,
                    ambientColor = Color.Black.copy(alpha = 0.35f),
                    spotColor = Color.Black.copy(alpha = 0.45f)
                )
                .clip(AlbumRecCardShape)
                .background(if (appColors.isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0))
        ) {
            val appContext = LocalContext.current.applicationContext
            val sharedImageLoader = remember(appContext) {
                MediaNetworkClients.getImageLoader(appContext)
            }
            AsyncImage(
                model = ImageRequest.Builder(appContext)
                    .data(album.coverUri?.ifBlank { null })
                    .crossfade(300)
                    .build(),
                imageLoader = sharedImageLoader,
                contentDescription = album.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Text(
            text = album.title,
            fontFamily = GoogleSans,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = appColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        val sub = if (album.year > 0) "${album.year} • ${album.artist}" else album.artist
        Text(
            text = sub,
            fontFamily = PlusJakartaSans,
            fontSize = 11.5.sp,
            color = appColors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Animated equalizer visualizer for the currently playing track.
 */
@Composable
private fun PlayingEqualizerBars(
    color: Color,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "audioBars")

    val bar1Height by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = 16f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )

    val bar2Height by infiniteTransition.animateFloat(
        initialValue = 16f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(330, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )

    val bar3Height by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(
            animation = tween(490, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )

    Row(
        modifier = modifier.size(18.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val h1 = if (isPlaying) bar1Height.dp else 4.dp
        val h2 = if (isPlaying) bar2Height.dp else 12.dp
        val h3 = if (isPlaying) bar3Height.dp else 6.dp

        Box(
            modifier = Modifier
                .width(3.dp)
                .height(h1)
                .clip(RoundedCornerShape(1.5.dp))
                .background(color)
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(h2)
                .clip(RoundedCornerShape(1.5.dp))
                .background(color)
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(h3)
                .clip(RoundedCornerShape(1.5.dp))
                .background(color)
        )
    }
}

@Composable
private fun AlbumPreviewContent(widthDp: Int, heightDp: Int) {
    AdaptivePreviewSurface(widthDp = widthDp, heightDp = heightDp) {
        AlbumScreen(
            profile = AdaptivePreviewFixtures.albumProfile,
            isLoading = false,
            likedTrackIds = setOf("ytm_paparazzi"),
            ambientBackgroundColor = Color(0xFF1A1E2B),
            currentPlayingTrackId = "ytm_paparazzi",
            isPlaybackPlaying = true,
            onBack = {},
            onPlayAllClick = {},
            onShuffleClick = {},
            onTrackClick = {},
            onToggleFavorite = {},
            onTrackMoreClick = {},
            onArtistClick = {},
            onAlbumClick = {}
        )
    }
}

@Preview(name = "Phone (360x800)", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
private fun AlbumPhonePreview() = AlbumPreviewContent(360, 800)

@Preview(name = "Tablet Portrait (800x1280)", widthDp = 800, heightDp = 1280, showBackground = true)
@Composable
private fun AlbumTabletPortraitPreview() = AlbumPreviewContent(800, 1280)

@Preview(name = "Tablet Landscape (1280x800)", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
private fun AlbumTabletLandscapePreview() = AlbumPreviewContent(1280, 800)
