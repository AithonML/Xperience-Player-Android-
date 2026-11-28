package com.xperience.player.ui.artist

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
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
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
import com.xperience.player.domain.model.AlbumItem
import com.xperience.player.domain.model.ArtistDetailProfile
import com.xperience.player.domain.model.ArtistItem
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.service.MediaNetworkClients
import com.xperience.player.ui.adaptive.AdaptivePreviewFixtures
import com.xperience.player.ui.adaptive.AdaptivePreviewSurface
import com.xperience.player.ui.adaptive.LocalAppWindowSizeClass
import com.xperience.player.ui.adaptive.LocalBottomChromePadding
import com.xperience.player.ui.adaptive.LocalTopChromePadding
import com.xperience.player.ui.adaptive.isMediumOrExpandedWidth
import com.xperience.player.ui.component.ArtistFansSkeleton
import com.xperience.player.ui.component.ArtistTopSongsSkeleton
import com.xperience.player.ui.component.ArtistWordmarkSkeleton
import com.xperience.player.ui.component.CenteredTextBlockSkeleton
import com.xperience.player.ui.component.ExplicitBadge
import com.xperience.player.ui.component.GlassCircleButton
import com.xperience.player.ui.component.GlassComponent
import com.xperience.player.ui.component.HeroSubtitleSkeleton
import com.xperience.player.ui.component.LocalAppBackdrop
import com.xperience.player.ui.component.LocalGlassEffectConfig
import com.xperience.player.ui.component.MediaShelfSkeleton
import com.xperience.player.ui.component.ShimmerBox
import com.xperience.player.ui.component.ShimmerScope
import com.xperience.player.ui.component.SubscribersPillSkeleton
import com.xperience.player.ui.component.backdrop.backdrops.layerBackdrop
import com.xperience.player.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.xperience.player.ui.component.isGlassAllowed
import com.xperience.player.ui.component.liquidGlass
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.player.CanvasArtworkPlayer
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.PlusJakartaSans
import com.xperience.player.ui.utils.pressWobble

private val TopSongRowShape = RoundedCornerShape(14.dp)
private val AlbumCardShape = RoundedCornerShape(16.dp)
private const val HERO_ASPECT_RATIO = 16f / 9f

private enum class SectionLoadState { LOADING, CONTENT, EMPTY }
private enum class WordmarkSlotState { LOADING, WORDMARK, TITLE }

@Composable
fun ArtistScreen(
    profile: ArtistDetailProfile,
    videoUri: String?,
    isLoading: Boolean,
    isBioExpanded: Boolean,
    likedTrackIds: Set<String>,
    ambientBackgroundColor: Color,
    currentPlayingTrackId: String?,
    isPlaybackPlaying: Boolean,
    onBack: () -> Unit,
    onToggleBio: () -> Unit,
    onShuffleClick: () -> Unit,
    onMixClick: () -> Unit,
    onTrackClick: (TrackItem) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onTrackMoreClick: (TrackItem) -> Unit,
    onAlbumClick: (AlbumItem) -> Unit,
    onArtistClick: (ArtistItem) -> Unit,
    modifier: Modifier = Modifier,
    onPlayTrackFromList: ((List<TrackItem>, Int) -> Unit)? = null,
    onOfficialVideoClick: ((TrackItem) -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    val catalogLoading = isLoading || profile.isCatalogLoading
    val bottomChromePadding = LocalBottomChromePadding.current

    // Smoothly animate the adaptive ambient background color extracted from the artist photo
    // Read via State<Color> inside draw scopes so neither LayerBackdrop nor full-screen tree churns per frame.
    val animatedBgColorState: State<Color> = animateColorAsState(
        targetValue = ambientBackgroundColor,
        animationSpec = tween(durationMillis = 650),
        label = "artistAmbientBg"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .drawBehind { drawRect(animatedBgColorState.value) }
    ) {
        val headerHeight = (maxHeight * 0.52f).coerceIn(420.dp, 540.dp)
        val headerHeightPx = with(LocalDensity.current) { headerHeight.toPx() }
        val isHeroVisible by remember(headerHeightPx) {
            derivedStateOf { scrollState.value < headerHeightPx }
        }

        ShimmerScope {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // =========================================================================
                // HERO HEADER (Photo + Apple Music 16:9 Looping Video + Dissolve Gradient)
                // =========================================================================
                ArtistHeroHeader(
                    profile = profile,
                    videoUri = videoUri,
                    isLoading = isLoading,
                    isHeroVisible = isHeroVisible,
                    isBioExpanded = isBioExpanded,
                    headerHeight = headerHeight,
                    ambientBgColor = ambientBackgroundColor,
                    ambientBgColorState = animatedBgColorState,
                    onBack = onBack,
                    onToggleBio = onToggleBio,
                    onShuffleClick = onShuffleClick,
                    onMixClick = onMixClick
                )

                Spacer(modifier = Modifier.height(18.dp))

                // =========================================================================
                // FEATURED SECTION: Último lanzamiento (Single or Album by Month/Year)
                // =========================================================================
                val resolvedLatestRelease = profile.latestRelease
                    ?: profile.singlesAndEps.maxByOrNull { it.year * 100 + it.releaseMonth }
                    ?: profile.combinedAlbums.maxByOrNull { it.year * 100 + it.releaseMonth }

                if (resolvedLatestRelease != null) {
                    ArtistFeaturedLatestReleaseSection(
                        release = resolvedLatestRelease,
                        releaseTypeBadge = profile.latestReleaseTypeBadge.ifBlank { resolvedLatestRelease.releaseTypeBadgeText },
                        releaseDateText = profile.latestReleaseDateText.ifBlank { resolvedLatestRelease.formattedReleaseDate },
                        ambientBgColor = ambientBackgroundColor,
                        onReleaseClick = { onAlbumClick(resolvedLatestRelease) }
                    )
                    Spacer(modifier = Modifier.height(26.dp))
                }

                // =========================================================================
                // SECTION 1: Top Songs by YouTube Music Play Count
                // =========================================================================
                val topSongsState = when {
                    profile.topTracks.isNotEmpty() -> SectionLoadState.CONTENT
                    catalogLoading -> SectionLoadState.LOADING
                    else -> SectionLoadState.EMPTY
                }
                AnimatedContent(
                    targetState = topSongsState,
                    transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(200)) },
                    label = "artistTopSongs"
                ) { state ->
                    when (state) {
                        SectionLoadState.LOADING -> Column {
                            ArtistTopSongsSkeleton(
                                ambientBgColor = ambientBackgroundColor,
                                rowCount = 5
                            )
                            Spacer(modifier = Modifier.height(28.dp))
                        }
                        SectionLoadState.CONTENT -> Column {
                            ArtistTopSongsSection(
                                tracks = profile.topTracks,
                                likedTrackIds = likedTrackIds,
                                currentPlayingTrackId = currentPlayingTrackId,
                                isPlaybackPlaying = isPlaybackPlaying,
                                ambientBgColor = ambientBackgroundColor,
                                onTrackClick = onTrackClick,
                                onToggleFavorite = onToggleFavorite,
                                onTrackMoreClick = onTrackMoreClick,
                                onPlayTrackFromList = onPlayTrackFromList
                            )
                            Spacer(modifier = Modifier.height(28.dp))
                        }
                        SectionLoadState.EMPTY -> Spacer(modifier = Modifier.height(0.dp))
                    }
                }

                // =========================================================================
                // SECTION 2: Albums (YouTube Music + Local Library)
                // =========================================================================
                val albumsState = when {
                    profile.combinedAlbums.isNotEmpty() -> SectionLoadState.CONTENT
                    catalogLoading -> SectionLoadState.LOADING
                    else -> SectionLoadState.EMPTY
                }
                AnimatedContent(
                    targetState = albumsState,
                    transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(200)) },
                    label = "artistAlbums"
                ) { state ->
                    when (state) {
                        SectionLoadState.LOADING -> Column {
                            MediaShelfSkeleton(titleWidth = 92.dp, cardCount = 4)
                            Spacer(modifier = Modifier.height(28.dp))
                        }
                        SectionLoadState.CONTENT -> Column {
                            ArtistMediaShelf(
                                sectionTitle = "Albums",
                                items = profile.combinedAlbums,
                                onAlbumClick = onAlbumClick
                            )
                            Spacer(modifier = Modifier.height(28.dp))
                        }
                        SectionLoadState.EMPTY -> Spacer(modifier = Modifier.height(0.dp))
                    }
                }

                // =========================================================================
                // SECTION 3: Singles & EPs
                // =========================================================================
                val singlesState = when {
                    profile.singlesAndEps.isNotEmpty() -> SectionLoadState.CONTENT
                    catalogLoading -> SectionLoadState.LOADING
                    else -> SectionLoadState.EMPTY
                }
                AnimatedContent(
                    targetState = singlesState,
                    transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(200)) },
                    label = "artistSingles"
                ) { state ->
                    when (state) {
                        SectionLoadState.LOADING -> Column {
                            MediaShelfSkeleton(titleWidth = 130.dp, cardCount = 4)
                            Spacer(modifier = Modifier.height(28.dp))
                        }
                        SectionLoadState.CONTENT -> Column {
                            ArtistMediaShelf(
                                sectionTitle = "Singles & EPs",
                                items = profile.singlesAndEps,
                                onAlbumClick = onAlbumClick
                            )
                            Spacer(modifier = Modifier.height(28.dp))
                        }
                        SectionLoadState.EMPTY -> Spacer(modifier = Modifier.height(0.dp))
                    }
                }

                // =========================================================================
                // SECTION 3B: Videos Oficiales (Official Music Videos)
                // =========================================================================
                if (profile.officialVideos.isNotEmpty()) {
                    ArtistOfficialVideosSection(
                        videos = profile.officialVideos,
                        ambientBgColor = ambientBackgroundColor,
                        onVideoClick = { videoTrack ->
                            if (onOfficialVideoClick != null) {
                                onOfficialVideoClick(videoTrack)
                            } else {
                                onTrackClick(videoTrack)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                }

                // =========================================================================
                // SECTION 4: Artist Playlists & Featured On
                // =========================================================================
                if (profile.artistPlaylists.isNotEmpty()) {
                    ArtistMediaShelf(
                        sectionTitle = "Artist Playlists & Featured On",
                        items = profile.artistPlaylists,
                        onAlbumClick = onAlbumClick
                    )

                    Spacer(modifier = Modifier.height(28.dp))
                }

                // =========================================================================
                // SECTION 5: Fans Might Also Like (Related Artists)
                // =========================================================================
                val fansState = when {
                    profile.similarArtists.isNotEmpty() -> SectionLoadState.CONTENT
                    catalogLoading -> SectionLoadState.LOADING
                    else -> SectionLoadState.EMPTY
                }
                AnimatedContent(
                    targetState = fansState,
                    transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(200)) },
                    label = "artistFans"
                ) { state ->
                    when (state) {
                        SectionLoadState.LOADING -> Column {
                            ArtistFansSkeleton(cardCount = 4)
                            Spacer(modifier = Modifier.height(28.dp))
                        }
                        SectionLoadState.CONTENT -> Column {
                            ArtistFansSection(
                                artists = profile.similarArtists,
                                onArtistClick = onArtistClick
                            )
                            Spacer(modifier = Modifier.height(28.dp))
                        }
                        SectionLoadState.EMPTY -> Spacer(modifier = Modifier.height(0.dp))
                    }
                }

                // Bottom clearance so content scrolls completely clear of floating capsules
                Spacer(modifier = Modifier.height(bottomChromePadding))
            }
        }
    }
}

/**
 * Hero Header with wide banner photo, Apple Music 16:9 looping video overlay,
 * multi-stop dissolve gradient fusing into the adaptive ambient background color,
 * centered title/wordmark, bio, and LiquidGlass action pills.
 */
@Composable
private fun ArtistHeroHeader(
    profile: ArtistDetailProfile,
    videoUri: String?,
    isLoading: Boolean,
    isHeroVisible: Boolean,
    isBioExpanded: Boolean,
    headerHeight: androidx.compose.ui.unit.Dp,
    ambientBgColor: Color,
    ambientBgColorState: State<Color>,
    onBack: () -> Unit,
    onToggleBio: () -> Unit,
    onShuffleClick: () -> Unit,
    onMixClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val sharedImageLoader = remember(appContext) {
        MediaNetworkClients.getImageLoader(appContext)
    }
    val accentColor = LocalAccentColor.current
    val appColors = LocalAppColors.current
    val isDark = appColors.isDark
    val bannerUrl = profile.bannerImageUri.ifBlank { profile.avatarUri }
    val catalogLoading = isLoading || profile.isCatalogLoading
    val topChromePadding = LocalTopChromePadding.current

    // Dedicated backdrop for the Artist Hero Header:
    // Captures the artist banner photo and ambient color into artistBackdrop cleanly.
    // Capturing State<Color> inside a remembered onDraw lambda ensures artistBackdrop
    // never recreates on every frame, and keeping CanvasArtworkPlayer OUTSIDE layerBackdrop
    // avoids HWUI stalling/crashing on TextureView inside nested RenderEffect GraphicsLayers.
    val artistBackdrop = rememberLayerBackdrop(
        onDraw = remember(ambientBgColorState) {
            {
                drawRect(ambientBgColorState.value)
                drawContent()
            }
        }
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(headerHeight)
            .clipToBounds()
            .drawBehind { drawRect(ambientBgColorState.value) }
    ) {
        // =========================================================================
        // 1. BACKDROP SOURCE LAYER (Banner Photo recorded into artistBackdrop)
        // =========================================================================
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .layerBackdrop(artistBackdrop)
        ) {
            val heroWidth = maxWidth
            val heroHeight = maxHeight
            val hasValidHeroSize = heroWidth.value.isFinite() && heroHeight.value.isFinite() &&
                heroWidth > 0.dp && heroHeight > 0.dp
            val (mediaWidth, mediaHeight) = remember(heroWidth, heroHeight) {
                if (!hasValidHeroSize) {
                    0.dp to 0.dp
                } else {
                    val aspectWidth = heroHeight * HERO_ASPECT_RATIO
                    if (aspectWidth >= heroWidth) {
                        aspectWidth to heroHeight
                    } else {
                        heroWidth to (heroWidth / HERO_ASPECT_RATIO)
                    }
                }
            }

            // LAYER 0: Subtle shimmer while resolving initial media, or fallback icon if none exists
            if (bannerUrl.isBlank()) {
                if (profile.isHeaderMediaLoading || catalogLoading) {
                    ShimmerBox(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(0.dp),
                        baseColor = ambientBgColor,
                        highlightColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = accentColor.copy(alpha = 0.25f),
                            modifier = Modifier.size(96.dp)
                        )
                    }
                }
            }

            // LAYER 1: Height-matched strict 16:9 Photo centered & clipped horizontally
            if (bannerUrl.isNotBlank() && mediaWidth > 0.dp && mediaHeight > 0.dp) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .requiredSize(width = mediaWidth, height = mediaHeight)
                            .clipToBounds(),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(appContext)
                                .data(bannerUrl)
                                .crossfade(300)
                                .build(),
                            imageLoader = sharedImageLoader,
                            contentDescription = profile.name,
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // LAYER 2: Height-matched 16:9 Apple Music Looping Video rendered directly (outside LayerBackdrop)
        if (!videoUri.isNullOrBlank()) {
            CanvasArtworkPlayer(
                videoUri = videoUri,
                isPlaying = isHeroVisible,
                targetAspectRatio = HERO_ASPECT_RATIO,
                modifier = Modifier.fillMaxSize()
            )
        }

        // LAYER 3A: Bottom-to-Top Seamless Dissolve Gradient fusing into adaptive ambient color
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val bg = ambientBgColorState.value
                    drawRect(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.00f to bg.copy(alpha = if (isDark) 0.05f else 0.02f),
                                0.25f to bg.copy(alpha = if (isDark) 0.16f else 0.12f),
                                0.50f to bg.copy(alpha = if (isDark) 0.50f else 0.45f),
                                0.72f to bg.copy(alpha = if (isDark) 0.85f else 0.80f),
                                1.00f to bg
                            )
                        )
                    )
                }
        )

        // LAYER 3B: Soft Reading Vignette Dissolving into adaptive ambient color
        val vignetteSideAlpha = if (isDark) 0.75f else 0.35f
        val vignetteMidAlpha = if (isDark) 0.35f else 0.15f
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val bg = ambientBgColorState.value
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0.00f to bg.copy(alpha = vignetteSideAlpha),
                                0.30f to bg.copy(alpha = vignetteMidAlpha),
                                0.70f to Color.Transparent,
                                1.00f to bg.copy(alpha = vignetteSideAlpha)
                            )
                        )
                    )
                }
        )

        // =========================================================================
        // 2. FLOATING LIQUID GLASS CONTROLS (Back Button, Shuffle, Mix, Subscribers)
        //    All controls sample artistBackdrop, reflecting the real artist photo/video!
        // =========================================================================
        CompositionLocalProvider(
            LocalAppBackdrop provides artistBackdrop
        ) {
            // Top Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = topChromePadding)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Global LiquidGlass Circular Back Button (Icon only, no text)
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

            // Center Artist Identity, Custom Apple Music Wordmark, Bio & Action Pills
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Artist Title: Shimmer while wordmark settles -> Custom Wordmark (with error fallback) OR Bold Text
                var wordmarkLoadFailed by remember(profile.customTitleLogoUri) { mutableStateOf(false) }
                var wordmarkImageReady by remember(profile.customTitleLogoUri) { mutableStateOf(false) }
                val wordmarkSkeletonAlpha by animateFloatAsState(
                    targetValue = if (wordmarkImageReady) 0f else 1f,
                    animationSpec = tween(220),
                    label = "wordmarkSkeletonAlpha"
                )
                val wordmarkState = when {
                    profile.hasCustomWordmark && !wordmarkLoadFailed -> WordmarkSlotState.WORDMARK
                    profile.isHeaderMediaLoading || (isLoading && profile.name.isBlank()) -> WordmarkSlotState.LOADING
                    else -> WordmarkSlotState.TITLE
                }

                Box(
                    contentAlignment = Alignment.BottomCenter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp, max = 84.dp)
                ) {
                    AnimatedContent(
                        targetState = wordmarkState,
                        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                        contentAlignment = Alignment.BottomCenter,
                        label = "artistWordmark"
                    ) { state ->
                        when (state) {
                            WordmarkSlotState.LOADING -> ArtistWordmarkSkeleton(
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            WordmarkSlotState.WORDMARK -> Box(
                                contentAlignment = Alignment.BottomCenter,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (wordmarkSkeletonAlpha > 0f) {
                                    ArtistWordmarkSkeleton(
                                        modifier = Modifier
                                            .padding(bottom = 4.dp)
                                            .graphicsLayer { alpha = wordmarkSkeletonAlpha }
                                    )
                                }
                                AsyncImage(
                                    model = ImageRequest.Builder(appContext)
                                        .data(profile.customTitleLogoUri)
                                        .allowRgb565(false)
                                        .crossfade(300)
                                        .build(),
                                    imageLoader = sharedImageLoader,
                                    contentDescription = profile.name,
                                    contentScale = ContentScale.Fit,
                                    alignment = Alignment.BottomCenter,
                                    onSuccess = { wordmarkImageReady = true },
                                    onError = { wordmarkLoadFailed = true },
                                    modifier = Modifier
                                        .widthIn(max = 286.dp)
                                        .heightIn(max = 78.dp)
                                        .padding(bottom = 2.dp)
                                )
                            }
                            WordmarkSlotState.TITLE -> Text(
                                text = profile.name,
                                fontFamily = GoogleSans,
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White else appColors.textPrimary,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Monthly Listeners / Audience Subtitle from YouTube Music
                val rawSubtitle = profile.monthlyListenersText.ifBlank { profile.metadataSubtitle }
                val subtitleText = if (catalogLoading && rawSubtitle.equals("YouTube Music Official", ignoreCase = true)) {
                    ""
                } else {
                    rawSubtitle
                }
                val subtitleState = when {
                    subtitleText.isNotBlank() -> SectionLoadState.CONTENT
                    catalogLoading -> SectionLoadState.LOADING
                    else -> SectionLoadState.EMPTY
                }
                AnimatedContent(
                    targetState = subtitleState,
                    transitionSpec = { fadeIn(tween(280)) togetherWith fadeOut(tween(180)) },
                    label = "artistSubtitle"
                ) { state ->
                    when (state) {
                        SectionLoadState.LOADING -> HeroSubtitleSkeleton()
                        SectionLoadState.CONTENT -> Text(
                            text = subtitleText,
                            fontFamily = PlusJakartaSans,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDark) Color(0xCCFFFFFF) else appColors.textSecondary,
                            textAlign = TextAlign.Center
                        )
                        SectionLoadState.EMPTY -> Spacer(modifier = Modifier.height(0.dp))
                    }
                }

                // Action Buttons Row: Circular Shuffle and Mix (Icon only, size 52.dp)
                val actionsState = if (profile.topTracks.isNotEmpty() || !catalogLoading) {
                    SectionLoadState.CONTENT
                } else {
                    SectionLoadState.LOADING
                }
                AnimatedContent(
                    targetState = actionsState,
                    transitionSpec = { fadeIn(tween(280)) togetherWith fadeOut(tween(180)) },
                    label = "artistActions"
                ) { state ->
                    when (state) {
                        SectionLoadState.LOADING -> Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ShimmerBox(modifier = Modifier.size(52.dp), shape = CircleShape)
                            ShimmerBox(modifier = Modifier.size(52.dp), shape = CircleShape)
                        }
                        else -> Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlassCircleButton(
                                onClick = onShuffleClick,
                                size = 52.dp
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = "Shuffle",
                                    tint = if (isDark) Color.White else appColors.glassTextPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            GlassCircleButton(
                                onClick = onMixClick,
                                size = 52.dp
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Mix",
                                    tint = if (isDark) Color.White else appColors.glassTextPrimary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }
                }

                // Subscribers Pill below Shuffle & Mix with the exact same LiquidGlass style
                val subscribersState = when {
                    profile.subscribersCountText.isNotBlank() -> SectionLoadState.CONTENT
                    catalogLoading -> SectionLoadState.LOADING
                    else -> SectionLoadState.EMPTY
                }
                AnimatedContent(
                    targetState = subscribersState,
                    transitionSpec = { fadeIn(tween(280)) togetherWith fadeOut(tween(180)) },
                    label = "artistSubscribers"
                ) { state ->
                    when (state) {
                        SectionLoadState.LOADING -> SubscribersPillSkeleton()
                        SectionLoadState.CONTENT -> LiquidSubscribersGlassPill(
                            text = profile.displaySubscribersPillText
                        )
                        SectionLoadState.EMPTY -> Spacer(modifier = Modifier.height(0.dp))
                    }
                }

                // Expandable Artist Biography with smooth animateContentSize and reversible MORE / LESS toggle
                val cleanBiography = if (profile.biography.startsWith("Loading official artist profile", ignoreCase = true)) {
                    ""
                } else {
                    profile.biography
                }
                val bioState = when {
                    cleanBiography.isNotBlank() -> SectionLoadState.CONTENT
                    catalogLoading -> SectionLoadState.LOADING
                    else -> SectionLoadState.EMPTY
                }
                AnimatedContent(
                    targetState = bioState,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(180)) },
                    label = "artistBiography"
                ) { state ->
                    when (state) {
                        SectionLoadState.LOADING -> CenteredTextBlockSkeleton(
                            lines = 2,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
                        )
                        SectionLoadState.CONTENT -> {
                            val bioInteraction = remember { MutableInteractionSource() }
                            val bioHaptic = LocalHapticFeedback.current
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .widthIn(max = 580.dp)
                                    .fillMaxWidth()
                                    .pressWobble(bioInteraction, pressedScale = 0.97f)
                                    .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                                    .clickable(
                                        interactionSource = bioInteraction,
                                        indication = null
                                    ) {
                                        bioHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onToggleBio()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 2.dp)
                            ) {
                            Text(
                                text = cleanBiography,
                                fontFamily = PlusJakartaSans,
                                fontSize = 12.5.sp,
                                color = if (isDark) Color(0xEEFFFFFF) else appColors.textPrimary.copy(alpha = 0.88f),
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp,
                                maxLines = if (isBioExpanded) Int.MAX_VALUE else 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (cleanBiography.length > 100) {
                                Text(
                                    text = if (isBioExpanded) "LESS" else "MORE",
                                    fontFamily = GoogleSans,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            }
                        }
                        SectionLoadState.EMPTY -> Spacer(modifier = Modifier.height(0.dp))
                    }
                }
            }
        }
    }
}

/**
 * LiquidGlass pill for Subscribers displaying the subscriber count,
 * styled with the exact same LiquidGlass engine, refractive lens, specular rim,
 * and ContinuousRoundedRectangle shape as GlassCircleButton.
 */
@Composable
private fun LiquidSubscribersGlassPill(
    text: String,
    modifier: Modifier = Modifier
) {
    val glassConfig = LocalGlassEffectConfig.current
    val useGlass = glassConfig.isEnabledFor(GlassComponent.NAV_BAR) && isGlassAllowed()
    val shape = remember { ContinuousRoundedRectangle(percent = 50) }

    val backgroundModifier = if (useGlass) {
        Modifier.liquidGlass(
            config = glassConfig,
            shape = shape,
            highlightAlpha = 0.35f,
        )
    } else {
        Modifier
            .clip(shape)
            .background(Color(0x35FF3B5C))
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(shape)
            .then(backgroundModifier)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF3B5C))
            )
            Text(
                text = text,
                fontFamily = GoogleSans,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (LocalAppColors.current.isDark) Color.White else LocalAppColors.current.glassTextPrimary
            )
        }
    }
}

/**
 * Top Songs section with specular rows displaying thumbnail,
 * title, playing equalizer wave, play count, album, duration, like, and more options.
 */
@Composable
private fun ArtistTopSongsSection(
    tracks: List<TrackItem>,
    likedTrackIds: Set<String>,
    currentPlayingTrackId: String?,
    isPlaybackPlaying: Boolean,
    ambientBgColor: Color,
    onTrackClick: (TrackItem) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onTrackMoreClick: (TrackItem) -> Unit,
    onPlayTrackFromList: ((List<TrackItem>, Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val windowSizeClass = LocalAppWindowSizeClass.current
    val columns = if (windowSizeClass.isMediumOrExpandedWidth) 2 else 1
    val visibleTracks = tracks.take(10)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Top Songs",
            fontFamily = GoogleSans,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = appColors.textPrimary
        )

        if (columns == 1) {
            visibleTracks.forEachIndexed { index, track ->
                val isLiked = likedTrackIds.contains(track.id)
                val isCurrentTrack = currentPlayingTrackId != null && (
                    track.id == currentPlayingTrackId ||
                    currentPlayingTrackId.contains(track.id.removePrefix("ytm_"))
                )

                ArtistSongRow(
                    track = track,
                    isLiked = isLiked,
                    isCurrentTrack = isCurrentTrack,
                    isPlaybackPlaying = isPlaybackPlaying,
                    ambientBgColor = ambientBgColor,
                    onTrackClick = {
                        if (onPlayTrackFromList != null) {
                            onPlayTrackFromList(tracks, index)
                        } else {
                            onTrackClick(track)
                        }
                    },
                    onToggleFavorite = { onToggleFavorite(track.id) },
                    onMoreClick = { onTrackMoreClick(track) }
                )
            }
        } else {
            visibleTracks.chunked(columns).forEachIndexed { chunkIdx, pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    pair.forEachIndexed { colIdx, track ->
                        val index = chunkIdx * columns + colIdx
                        val isLiked = likedTrackIds.contains(track.id)
                        val isCurrentTrack = currentPlayingTrackId != null && (
                            track.id == currentPlayingTrackId ||
                            currentPlayingTrackId.contains(track.id.removePrefix("ytm_"))
                        )
                        ArtistSongRow(
                            track = track,
                            isLiked = isLiked,
                            isCurrentTrack = isCurrentTrack,
                            isPlaybackPlaying = isPlaybackPlaying,
                            ambientBgColor = ambientBgColor,
                            onTrackClick = {
                                if (onPlayTrackFromList != null) {
                                    onPlayTrackFromList(tracks, index)
                                } else {
                                    onTrackClick(track)
                                }
                            },
                            onToggleFavorite = { onToggleFavorite(track.id) },
                            onMoreClick = { onTrackMoreClick(track) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(columns - pair.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistSongRow(
    track: TrackItem,
    isLiked: Boolean,
    isCurrentTrack: Boolean,
    isPlaybackPlaying: Boolean,
    ambientBgColor: Color,
    onTrackClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val accentColor = LocalAccentColor.current
    val songHaptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "songRowScale"
    )

    // Blend card background with the adaptive ambient background
    val rowBackground = if (isCurrentTrack) {
        accentColor.copy(alpha = if (appColors.isDark) 0.16f else 0.22f)
    } else if (appColors.isDark) {
        ambientBgColor.copy(alpha = 0.45f)
    } else {
        Color.White.copy(alpha = 0.75f)
    }

    val rowBorderColor = if (isCurrentTrack) {
        accentColor.copy(alpha = 0.45f)
    } else if (appColors.isDark) {
        Color.White.copy(alpha = 0.10f)
    } else {
        Color.Black.copy(alpha = 0.08f)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(TopSongRowShape)
            .background(rowBackground)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        rowBorderColor,
                        rowBorderColor.copy(alpha = 0.04f)
                    )
                ),
                shape = TopSongRowShape
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onTrackClick,
                onLongClick = {
                    songHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onMoreClick()
                }
            )
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        val isWide = maxWidth >= 580.dp

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Track Thumbnail with overlay equalizer if currently playing
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E212B)),
                contentAlignment = Alignment.Center
            ) {
                if (!track.artworkUri.isNullOrBlank()) {
                    val appContext = LocalContext.current.applicationContext
                    val sharedImageLoader = remember(appContext) {
                        MediaNetworkClients.getImageLoader(appContext)
                    }
                    AsyncImage(
                        model = ImageRequest.Builder(appContext)
                            .data(track.artworkUri)
                            .crossfade(200)
                            .build(),
                        imageLoader = sharedImageLoader,
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (isCurrentTrack) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        PlayingEqualizerBars(
                            color = accentColor,
                            isPlaying = isPlaybackPlaying
                        )
                    }
                }
            }

            if (isWide) {
                // Wide / Tablet layout: Separate columns for title, play count, album, duration
                Row(
                    modifier = Modifier.weight(1.5f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = track.title,
                        fontFamily = GoogleSans,
                        fontSize = 13.5.sp,
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

                Text(
                    text = track.formattedPlayCount.ifBlank { track.artist },
                    fontFamily = PlusJakartaSans,
                    fontSize = 12.5.sp,
                    color = appColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1.1f)
                )

                Text(
                    text = track.album,
                    fontFamily = PlusJakartaSans,
                    fontSize = 12.5.sp,
                    color = appColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1.1f)
                )

                Text(
                    text = track.formattedDuration,
                    fontFamily = PlusJakartaSans,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = appColors.textPrimary,
                    modifier = Modifier.weight(0.55f)
                )
            } else {
                // Mobile layout: Title and combined subtitle row
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
                            fontSize = 13.5.sp,
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

                    val subParts = listOfNotNull(
                        track.formattedPlayCount.ifBlank { null },
                        track.album.ifBlank { null },
                        track.formattedDuration
                    )
                    Text(
                        text = subParts.joinToString(" • "),
                        fontFamily = PlusJakartaSans,
                        fontSize = 11.5.sp,
                        color = appColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            val favInteraction = remember { MutableInteractionSource() }
            val heartPopScale by animateFloatAsState(
                targetValue = if (isLiked) 1.12f else 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "artistSongHeartPop"
            )

            // Like / Favorite Button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .pressWobble(favInteraction, pressedScale = 0.78f)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = favInteraction,
                        indication = ripple(bounded = false, radius = 16.dp)
                    ) {
                        songHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleFavorite()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isLiked) Color(0xFFFF4081) else appColors.textSecondary.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(17.dp)
                        .graphicsLayer {
                            scaleX = heartPopScale
                            scaleY = heartPopScale
                        }
                )
            }

            // More Options Button (...)
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
                        songHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onMoreClick()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = appColors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
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
        initialValue = 15f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(340, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )

    val bar3Height by infiniteTransition.animateFloat(
        initialValue = 6f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(
            animation = tween(480, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )

    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.height(18.dp)
    ) {
        val h1 = if (isPlaying) bar1Height else 6f
        val h2 = if (isPlaying) bar2Height else 12f
        val h3 = if (isPlaying) bar3Height else 8f

        Box(
            modifier = Modifier
                .width(3.dp)
                .height(h1.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(color)
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(h2.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(color)
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(h3.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(color)
        )
    }
}

/**
 * Reusable horizontal media shelf for Albums, Singles & EPs, and Playlists.
 */
@Composable
private fun ArtistMediaShelf(
    sectionTitle: String,
    items: List<AlbumItem>,
    onAlbumClick: (AlbumItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = sectionTitle,
            fontFamily = GoogleSans,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = appColors.textPrimary,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        val safeItems = remember(items) {
            items.distinctBy { album ->
                album.id.ifBlank { "${album.title.trim().lowercase()}_${album.artist.trim().lowercase()}" }
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(
                items = safeItems,
                key = { index, album -> "shelf_${sectionTitle}_${index}_${album.id}" }
            ) { _, album ->
                ArtistAlbumCard(
                    album = album,
                    onClick = { onAlbumClick(album) }
                )
            }
        }
    }
}

@Composable
private fun ArtistAlbumCard(
    album: AlbumItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val accentColor = LocalAccentColor.current
    val songHaptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "albumCardScale"
    )

    val isLocal = album.id.startsWith("local_")

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
                    shape = AlbumCardShape,
                    ambientColor = Color.Black.copy(alpha = 0.35f),
                    spotColor = Color.Black.copy(alpha = 0.45f)
                )
                .clip(AlbumCardShape)
                .background(if (appColors.isDark) Color(0xFF1E212B) else Color(0xFFE2E8F0))
                .border(
                    width = 1.dp,
                    color = appColors.cardBorder,
                    shape = AlbumCardShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!album.coverUri.isNullOrBlank()) {
                val appContext = LocalContext.current.applicationContext
                val sharedImageLoader = remember(appContext) {
                    MediaNetworkClients.getImageLoader(appContext)
                }
                AsyncImage(
                    model = ImageRequest.Builder(appContext)
                        .data(album.coverUri)
                        .crossfade(200)
                        .build(),
                    imageLoader = sharedImageLoader,
                    contentDescription = album.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = accentColor.copy(alpha = 0.4f),
                    modifier = Modifier.size(32.dp)
                )
            }

            if (isLocal) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xCC00E5FF))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "LOCAL",
                        fontFamily = GoogleSans,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }
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

        val subText = if (album.releaseDateText.isNotBlank()) {
            album.releaseDateText
        } else if (album.year > 0) {
            "${album.year}"
        } else if (album.tracksCount > 0) {
            "${album.tracksCount} tracks"
        } else {
            album.artist
        }
        Text(
            text = subText,
            fontFamily = PlusJakartaSans,
            fontSize = 11.5.sp,
            color = appColors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Featured "Último lanzamiento" section on the Artist screen showing the artist's newest
 * Single or Album determined by release month/year.
 */
@Composable
private fun ArtistFeaturedLatestReleaseSection(
    release: AlbumItem,
    releaseTypeBadge: String,
    releaseDateText: String,
    ambientBgColor: Color,
    onReleaseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val accentColor = LocalAccentColor.current
    val songHaptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.975f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "latestReleaseScale"
    )
    val cardShape = RoundedCornerShape(22.dp)
    val cardBg = if (appColors.isDark) {
        Brush.linearGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.20f),
                ambientBgColor.copy(alpha = 0.72f),
                Color(0xFF151822).copy(alpha = 0.88f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.14f),
                Color.White.copy(alpha = 0.92f)
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Latest Release",
                fontFamily = GoogleSans,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = appColors.textPrimary
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(accentColor.copy(alpha = if (appColors.isDark) 0.22f else 0.14f))
                    .border(0.8.dp, accentColor.copy(alpha = 0.50f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "$releaseTypeBadge • $releaseDateText",
                    fontFamily = GoogleSans,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(
                    elevation = 10.dp,
                    shape = cardShape,
                    ambientColor = accentColor.copy(alpha = 0.25f),
                    spotColor = Color.Black.copy(alpha = 0.40f)
                )
                .clip(cardShape)
                .background(cardBg)
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            accentColor.copy(alpha = 0.55f),
                            Color.White.copy(alpha = if (appColors.isDark) 0.16f else 0.55f)
                        )
                    ),
                    shape = cardShape
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onReleaseClick
                )
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .shadow(8.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (appColors.isDark) Color(0xFF1E212B) else Color(0xFFE2E8F0)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!release.coverUri.isNullOrBlank()) {
                        val appContext = LocalContext.current.applicationContext
                        val sharedImageLoader = remember(appContext) {
                            MediaNetworkClients.getImageLoader(appContext)
                        }
                        AsyncImage(
                            model = ImageRequest.Builder(appContext)
                                .data(release.coverUri)
                                .crossfade(220)
                                .build(),
                            imageLoader = sharedImageLoader,
                            contentDescription = release.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.72f))
                            .border(0.6.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = releaseTypeBadge,
                            fontFamily = GoogleSans,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "FEATURED RELEASE • ${releaseDateText.uppercase()}",
                        fontFamily = GoogleSans,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = release.title,
                        fontFamily = GoogleSans,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = appColors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    val tracksLabel = if (releaseTypeBadge.equals("SINGLE", ignoreCase = true) || release.tracksCount <= 1) {
                        "Official Single"
                    } else {
                        "Full Album"
                    }
                    Text(
                        text = "$tracksLabel • ${release.artist}",
                        fontFamily = PlusJakartaSans,
                        fontSize = 12.5.sp,
                        color = appColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(accentColor)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Play",
                            fontFamily = GoogleSans,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }
        }
    }
}

/**
 * "Videos oficiales" 16:9 horizontal carousel on the Artist screen.
 */
@Composable
private fun ArtistOfficialVideosSection(
    videos: List<TrackItem>,
    ambientBgColor: Color,
    onVideoClick: (TrackItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val accentColor = LocalAccentColor.current
    val songHaptic = LocalHapticFeedback.current
    val safeVideos = remember(videos) {
        videos.distinctBy { it.id.ifBlank { it.title.trim().lowercase() } }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Official Videos",
                fontFamily = GoogleSans,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = appColors.textPrimary
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(accentColor.copy(alpha = if (appColors.isDark) 0.18f else 0.12f))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (safeVideos.size == 1) "1 video" else "${safeVideos.size} videos",
                    fontFamily = GoogleSans,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(
                items = safeVideos,
                key = { index, video -> "artist_video_${index}_${video.id}" }
            ) { _, video ->
                ArtistOfficialVideoCard(
                    video = video,
                    ambientBgColor = ambientBgColor,
                    onClick = { onVideoClick(video) }
                )
            }
        }
    }
}

@Composable
private fun ArtistOfficialVideoCard(
    video: TrackItem,
    ambientBgColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val accentColor = LocalAccentColor.current
    val songHaptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "videoCardScale"
    )
    val thumbShape = RoundedCornerShape(16.dp)

    Column(
        modifier = modifier
            .width(232.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = thumbShape,
                    ambientColor = Color.Black.copy(alpha = 0.35f),
                    spotColor = Color.Black.copy(alpha = 0.45f)
                )
                .clip(thumbShape)
                .background(if (appColors.isDark) ambientBgColor.copy(alpha = 0.6f) else Color(0xFFE2E8F0))
                .border(1.dp, appColors.cardBorder, thumbShape),
            contentAlignment = Alignment.Center
        ) {
            if (!video.artworkUri.isNullOrBlank()) {
                val appContext = LocalContext.current.applicationContext
                val sharedImageLoader = remember(appContext) {
                    MediaNetworkClients.getImageLoader(appContext)
                }
                AsyncImage(
                    model = ImageRequest.Builder(appContext)
                        .data(video.artworkUri)
                        .crossfade(220)
                        .build(),
                    imageLoader = sharedImageLoader,
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.15f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.65f)
                            )
                        )
                    )
            )

            // Center Play button badge
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.58f))
                    .border(1.2.dp, accentColor.copy(alpha = 0.85f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play video",
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Top-left VIDEO badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.68f))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "OFFICIAL VIDEO",
                    fontFamily = GoogleSans,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }

            if (video.durationMs > 0L) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = video.formattedDuration,
                        fontFamily = PlusJakartaSans,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }

        Text(
            text = video.title,
            fontFamily = GoogleSans,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = appColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        val subtitle = buildList {
            add(video.artist)
            if (video.formattedPlayCount.isNotBlank()) {
                add(video.formattedPlayCount.replace("plays", "views"))
            } else {
                add("Music Video")
            }
        }.joinToString(" • ")

        Text(
            text = subtitle,
            fontFamily = PlusJakartaSans,
            fontSize = 11.5.sp,
            color = appColors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Fans Might Also Like section with circular artist avatars.
 */
@Composable
private fun ArtistFansSection(
    artists: List<ArtistItem>,
    onArtistClick: (ArtistItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Fans Might Also Like",
            fontFamily = GoogleSans,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = appColors.textPrimary,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        val safeArtists = remember(artists) {
            artists.distinctBy { artist ->
                artist.id.ifBlank { artist.name.trim().lowercase() }
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(
                items = safeArtists,
                key = { index, artist -> "fan_${index}_${artist.id}" }
            ) { _, artist ->
                ArtistCircleCardItem(
                    artist = artist,
                    onClick = { onArtistClick(artist) }
                )
            }
        }
    }
}

@Composable
private fun ArtistCircleCardItem(
    artist: ArtistItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val accentColor = LocalAccentColor.current
    val songHaptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "artistCardScale"
    )

    Column(
        modifier = modifier
            .width(118.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(118.dp)
                .shadow(
                    elevation = 6.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.35f),
                    spotColor = Color.Black.copy(alpha = 0.45f)
                )
                .clip(CircleShape)
                .background(if (appColors.isDark) Color(0xFF1E212B) else Color(0xFFE2E8F0))
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.75f),
                            Color.White.copy(alpha = 0.3f),
                            accentColor.copy(alpha = 0.15f)
                        )
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!artist.avatarUri.isNullOrBlank()) {
                val appContext = LocalContext.current.applicationContext
                val sharedImageLoader = remember(appContext) {
                    MediaNetworkClients.getImageLoader(appContext)
                }
                AsyncImage(
                    model = ImageRequest.Builder(appContext)
                        .data(artist.avatarUri)
                        .crossfade(200)
                        .build(),
                    imageLoader = sharedImageLoader,
                    contentDescription = artist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = accentColor.copy(alpha = 0.5f),
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Text(
            text = artist.name,
            fontFamily = GoogleSans,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = appColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = artist.genre.ifBlank { "Artist" },
            fontFamily = PlusJakartaSans,
            fontSize = 11.sp,
            color = appColors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ArtistPreviewContent(widthDp: Int, heightDp: Int) {
    AdaptivePreviewSurface(widthDp = widthDp, heightDp = heightDp) {
        ArtistScreen(
            profile = AdaptivePreviewFixtures.artistProfile,
            videoUri = null,
            isLoading = false,
            isBioExpanded = false,
            likedTrackIds = setOf("ytm_paparazzi"),
            ambientBackgroundColor = Color(0xFF1A1E2B),
            currentPlayingTrackId = "ytm_paparazzi",
            isPlaybackPlaying = true,
            onBack = {},
            onToggleBio = {},
            onShuffleClick = {},
            onMixClick = {},
            onTrackClick = {},
            onToggleFavorite = {},
            onTrackMoreClick = {},
            onAlbumClick = {},
            onArtistClick = {}
        )
    }
}

@Preview(name = "Phone (360x800)", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
private fun ArtistPhonePreview() = ArtistPreviewContent(360, 800)

@Preview(name = "Tablet Portrait (800x1280)", widthDp = 800, heightDp = 1280, showBackground = true)
@Composable
private fun ArtistTabletPortraitPreview() = ArtistPreviewContent(800, 1280)

@Preview(name = "Tablet Landscape (1280x800)", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
private fun ArtistTabletLandscapePreview() = ArtistPreviewContent(1280, 800)
