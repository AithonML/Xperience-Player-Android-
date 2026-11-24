package com.xperience.player.ui.liked

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as lazyGridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed as lazyListItemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberSupportingPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.xperience.player.domain.model.AlbumItem
import com.xperience.player.domain.model.ArtistItem
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.service.MediaNetworkClients
import com.xperience.player.service.YouTubeMusicService
import com.xperience.player.ui.adaptive.AdaptivePreviewFixtures
import com.xperience.player.ui.adaptive.AdaptivePreviewSurface
import com.xperience.player.ui.adaptive.LocalAppWindowSizeClass
import com.xperience.player.ui.adaptive.LocalBottomChromePadding
import com.xperience.player.ui.adaptive.LocalTopChromePadding
import com.xperience.player.ui.adaptive.adaptiveGridMinSize
import com.xperience.player.ui.adaptive.rememberAdaptivePaneScaffoldDirective
import com.xperience.player.ui.adaptive.supportsTwoPaneLayout
import com.xperience.player.ui.adaptive.usesTopDesktopChrome
import com.xperience.player.ui.component.ExplicitBadge
import com.xperience.player.ui.component.LiquidGlassSegmentedPill
import com.xperience.player.ui.component.LocalAppBackdrop
import com.xperience.player.ui.component.ShimmerBox
import com.xperience.player.ui.component.ShimmerLine
import com.xperience.player.ui.component.ShimmerScope
import com.xperience.player.ui.component.backdrop.backdrops.layerBackdrop
import com.xperience.player.ui.component.backdrop.backdrops.rememberBackdropFreeze
import com.xperience.player.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.components.FrostedCardBadge
import com.xperience.player.ui.components.GlossyCardShell
import com.xperience.player.ui.components.HomeAuroraBackground
import com.xperience.player.ui.components.ProgressiveArtworkBlurScrim
import com.xperience.player.ui.components.expressiveGradientColors
import com.xperience.player.ui.components.jellyTapScale
import com.xperience.player.ui.components.onAccentColor
import com.xperience.player.ui.components.readableAccentColor
import com.xperience.player.ui.components.rememberAuroraJellyPressState
import com.xperience.player.ui.components.rememberJellyPressState
import com.xperience.player.ui.library.AlphabetSection
import com.xperience.player.ui.library.WaveAlphabetScrubber
import com.xperience.player.ui.library.groupItemsAlphabetically
import com.xperience.player.ui.theme.AppThemeColors
import com.xperience.player.ui.theme.AuroraColors
import com.xperience.player.ui.theme.DEFAULT_AURORA_COLORS
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.PlusJakartaSans
import com.xperience.player.ui.utils.pressWobble
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class LikedFilter(val label: String) {
    SONGS("Songs"),
    ARTISTS("Artists"),
    ALBUMS("Albums")
}

enum class LikedSortOrder(val label: String) {
    ALPHABETICAL("A–Z"),
    RECENT("Recent")
}

private val HeartRubyTint = Color(0xFFFF2D55)
private val HeaderEmblemShape = ContinuousRoundedRectangle(percent = 36)
private val SegmentPillShape = ContinuousRoundedRectangle(percent = 50)
private val SongRowShape = ContinuousRoundedRectangle(size = 16.dp)
private val SongArtworkShape = ContinuousRoundedRectangle(percent = 24)
private val AlbumCardShape = ContinuousRoundedRectangle(size = 20.dp)
private val EmptyCardShape = ContinuousRoundedRectangle(size = 24.dp)

@Composable
fun LikedScreen(
    likedTracks: List<TrackItem>,
    likedTrackIds: Set<String>,
    recommendations: List<TrackItem>,
    isLoadingRecommendations: Boolean,
    currentPlayingTrackId: String?,
    isPlaybackPlaying: Boolean,
    onPlayAllClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onTrackClick: (TrackItem) -> Unit,
    onToggleFavorite: (TrackItem) -> Unit,
    onTrackMoreClick: (TrackItem) -> Unit,
    onArtistClick: (String) -> Unit,
    onAlbumClick: (AlbumItem) -> Unit,
    onExploreMusicClick: () -> Unit,
    modifier: Modifier = Modifier,
    auroraColors: AuroraColors = DEFAULT_AURORA_COLORS,
    likedArtists: List<ArtistItem> = emptyList(),
    likedAlbums: List<AlbumItem> = emptyList(),
    onPlayTrackFromList: ((List<TrackItem>, Int) -> Unit)? = null,
    applyStatusBarsPadding: Boolean = false
) {
    val appColors = LocalAppColors.current
    val accentColor = LocalAccentColor.current
    val displayAccent = readableAccentColor(accentColor, appColors.isDark)
    val density = LocalDensity.current

    var selectedFilter by remember { mutableStateOf(LikedFilter.SONGS) }
    var sortOrder by remember { mutableStateOf(LikedSortOrder.ALPHABETICAL) }

    val derivedArtists = remember(likedTracks, likedArtists) {
        deriveLikedArtists(likedTracks, likedArtists)
    }
    val derivedAlbums = remember(likedTracks, likedAlbums) {
        deriveLikedAlbums(likedTracks, likedAlbums)
    }

    val totalDurationMs = remember(likedTracks) { likedTracks.sumOf { it.durationMs } }
    val formattedDuration = remember(totalDurationMs) { formatCollectionDuration(totalDurationMs) }
    val localTracksCount = remember(likedTracks) { likedTracks.count(::isLocalLikedTrack) }

    val statsSubtitle = remember(likedTracks.size, formattedDuration, localTracksCount) {
        buildString {
            append("${likedTracks.size} ${if (likedTracks.size == 1) "song" else "songs"}")
            append(" • ")
            append(formattedDuration)
            if (localTracksCount > 0) {
                append(" • $localTracksCount local")
            }
        }
    }

    val likedBackdrop = rememberLayerBackdrop {
        drawRect(appColors.background)
        drawContent()
    }

    var songsScrollOffsetPx by remember { mutableIntStateOf(0) }
    var artistsScrollOffsetPx by remember { mutableIntStateOf(0) }
    var albumsScrollOffsetPx by remember { mutableIntStateOf(0) }
    val activeAuroraScrollOffsetPx = when (selectedFilter) {
        LikedFilter.SONGS -> songsScrollOffsetPx
        LikedFilter.ARTISTS -> artistsScrollOffsetPx
        LikedFilter.ALBUMS -> albumsScrollOffsetPx
    }

    var floatingHeaderHeightPx by remember { mutableIntStateOf(0) }
    val windowSizeClass = LocalAppWindowSizeClass.current
    val topChromePadding = LocalTopChromePadding.current
    val statusBarTopDp = if (applyStatusBarsPadding) {
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    } else {
        0.dp
    }
    val topContentPadding: Dp = if (floatingHeaderHeightPx > 0) {
        with(density) { floatingHeaderHeightPx.toDp() } + 8.dp
    } else {
        statusBarTopDp + topChromePadding + 132.dp
    }

    fun playOrderedTrack(orderedList: List<TrackItem>, index: Int) {
        val safeIndex = index.coerceIn(0, orderedList.lastIndex.coerceAtLeast(0))
        val track = orderedList.getOrNull(safeIndex) ?: return
        if (onPlayTrackFromList != null) {
            onPlayTrackFromList(orderedList, safeIndex)
        } else {
            onTrackClick(track)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(appColors.background)
    ) {
        // =====================================================================
        // LAYER 1: EDGE-TO-EDGE AURORA + FILTERED LISTS / ALPHABETICAL SCRUBBER
        // =====================================================================
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(likedBackdrop)
        ) {
            HomeAuroraBackground(
                auroraColors = auroraColors,
                isDark = appColors.isDark,
                scrollOffsetPxProvider = { activeAuroraScrollOffsetPx },
                modifier = Modifier.fillMaxSize()
            )

            // Subtle ruby/accent ambient bloom complementing Aurora Borealis
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    HeartRubyTint.copy(alpha = if (appColors.isDark) 0.14f else 0.08f),
                                    accentColor.copy(alpha = if (appColors.isDark) 0.08f else 0.04f),
                                    Color.Transparent
                                ),
                                center = Offset(size.width * 0.18f, size.height * 0.12f),
                                radius = size.width * 0.72f
                            )
                        )
                    }
            )

            AnimatedContent(
                targetState = selectedFilter to sortOrder,
                transitionSpec = {
                    (fadeIn(tween(240)) + scaleIn(
                        initialScale = 0.97f,
                        animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f)
                    )) togetherWith (fadeOut(tween(180)) + scaleOut(targetScale = 0.98f))
                },
                label = "likedFilterTransition",
                modifier = Modifier.fillMaxSize()
            ) { (filter, activeSort) ->
                when (filter) {
                    LikedFilter.SONGS -> {
                        LikedSongsContent(
                            songs = likedTracks,
                            sortOrder = activeSort,
                            recommendations = recommendations,
                            isLoadingRecommendations = isLoadingRecommendations,
                            likedTrackIds = likedTrackIds,
                            currentPlayingTrackId = currentPlayingTrackId,
                            isPlaybackPlaying = isPlaybackPlaying,
                            topContentPadding = topContentPadding,
                            appColors = appColors,
                            accentColor = displayAccent,
                            onPlayAllOrdered = { orderedSongs ->
                                if (activeSort == LikedSortOrder.ALPHABETICAL &&
                                    onPlayTrackFromList != null &&
                                    orderedSongs.isNotEmpty()
                                ) {
                                    onPlayTrackFromList(orderedSongs, 0)
                                } else {
                                    onPlayAllClick()
                                }
                            },
                            onShuffleClick = onShuffleClick,
                            onPlayTrackAt = ::playOrderedTrack,
                            onToggleFavorite = onToggleFavorite,
                            onTrackMoreClick = onTrackMoreClick,
                            onExploreMusicClick = onExploreMusicClick,
                            onScrollOffsetChanged = { songsScrollOffsetPx = it }
                        )
                    }
                    LikedFilter.ARTISTS -> {
                        LikedArtistsContent(
                            artists = derivedArtists,
                            sortOrder = activeSort,
                            topContentPadding = topContentPadding,
                            appColors = appColors,
                            accentColor = displayAccent,
                            onArtistClick = { artist -> onArtistClick(artist.name) },
                            onExploreMusicClick = onExploreMusicClick,
                            onScrollOffsetChanged = { artistsScrollOffsetPx = it }
                        )
                    }
                    LikedFilter.ALBUMS -> {
                        LikedAlbumsContent(
                            albums = derivedAlbums,
                            sortOrder = activeSort,
                            topContentPadding = topContentPadding,
                            appColors = appColors,
                            accentColor = displayAccent,
                            onAlbumClick = onAlbumClick,
                            onExploreMusicClick = onExploreMusicClick,
                            onScrollOffsetChanged = { albumsScrollOffsetPx = it }
                        )
                    }
                }
            }
        }

        // =====================================================================
        // LAYER 2: SOFT TOP GRADIENT FADE + FLOATING LIQUIDGLASS HEADER & SLICERS
        // =====================================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(topContentPadding + 14.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            appColors.background.copy(alpha = 0.86f),
                            appColors.background.copy(alpha = 0.48f),
                            Color.Transparent
                        )
                    )
                )
        )

        CompositionLocalProvider(LocalAppBackdrop provides likedBackdrop) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .onSizeChanged { size -> floatingHeaderHeightPx = size.height }
                    .then(if (applyStatusBarsPadding) Modifier.statusBarsPadding() else Modifier)
                    .padding(top = topChromePadding)
                    .padding(top = 10.dp, bottom = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 20.dp,
                            end = if (windowSizeClass.usesTopDesktopChrome) 20.dp else 64.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LikedHeaderHeartBadge(
                        hasLikedSongs = likedTracks.isNotEmpty(),
                        isDark = appColors.isDark
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Liked Songs",
                            color = appColors.textPrimary,
                            fontFamily = GoogleSans,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = statsSubtitle,
                            color = appColors.textSecondary,
                            fontFamily = PlusJakartaSans,
                            fontSize = 12.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Horizontal scrollable LiquidGlass Segmented Slicers (Category + Sort Order)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LiquidGlassSegmentedPill(
                        options = LikedFilter.entries,
                        selectedOption = selectedFilter,
                        labelProvider = { it.label },
                        onSelectOption = { selectedFilter = it },
                        appColors = appColors,
                        accentColor = accentColor
                    )

                    LiquidGlassSegmentedPill(
                        options = LikedSortOrder.entries,
                        selectedOption = sortOrder,
                        labelProvider = { it.label },
                        onSelectOption = { sortOrder = it },
                        appColors = appColors,
                        accentColor = accentColor
                    )
                }
            }
        }
    }
}

// =============================================================================
// HEADER EMBLEM & LIQUIDGLASS SEGMENTED PILL (Matching LibraryScreen)
// =============================================================================

@Composable
private fun LikedHeaderHeartBadge(
    hasLikedSongs: Boolean,
    isDark: Boolean
) {
    val heartScaleState = if (hasLikedSongs) {
        val transition = rememberInfiniteTransition(label = "likedHeaderHeartPulse")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.07f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "likedHeartScale"
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    val bgBrush = remember(isDark) {
        Brush.verticalGradient(
            colors = listOf(
                HeartRubyTint.copy(alpha = if (isDark) 0.34f else 0.20f),
                if (isDark) Color(0x45281420) else Color(0x88FFFFFF)
            )
        )
    }
    val borderBrush = remember(isDark) {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = if (isDark) 0.55f else 0.85f),
                HeartRubyTint.copy(alpha = 0.55f),
                Color.White.copy(alpha = 0.14f)
            )
        )
    }

    Box(
        modifier = Modifier
            .size(44.dp)
            .graphicsLayer {
                val s = heartScaleState.value
                scaleX = s
                scaleY = s
            }
            .shadow(
                elevation = 10.dp,
                shape = HeaderEmblemShape,
                ambientColor = HeartRubyTint.copy(alpha = 0.40f),
                spotColor = HeartRubyTint.copy(alpha = 0.55f)
            )
            .clip(HeaderEmblemShape)
            .background(bgBrush)
            .border(
                width = 1.1.dp,
                brush = borderBrush,
                shape = HeaderEmblemShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Favorite,
            contentDescription = "Favorites",
            tint = HeartRubyTint,
            modifier = Modifier.size(23.dp)
        )
    }
}

// =============================================================================
// TAB 1: LIKED SONGS (Alphabetical A-Z + WaveAlphabetScrubber / Recent + Recs)
// =============================================================================

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun LikedSongsContent(
    songs: List<TrackItem>,
    sortOrder: LikedSortOrder,
    recommendations: List<TrackItem>,
    isLoadingRecommendations: Boolean,
    likedTrackIds: Set<String>,
    currentPlayingTrackId: String?,
    isPlaybackPlaying: Boolean,
    topContentPadding: Dp,
    appColors: AppThemeColors,
    accentColor: Color,
    onPlayAllOrdered: (List<TrackItem>) -> Unit,
    onShuffleClick: () -> Unit,
    onPlayTrackAt: (List<TrackItem>, Int) -> Unit,
    onToggleFavorite: (TrackItem) -> Unit,
    onTrackMoreClick: (TrackItem) -> Unit,
    onExploreMusicClick: () -> Unit,
    onScrollOffsetChanged: (Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    var activeScrubJob by remember { mutableStateOf<Job?>(null) }
    val windowSizeClass = LocalAppWindowSizeClass.current
    val isTwoPane = windowSizeClass.supportsTwoPaneLayout
    val bottomChromePadding = LocalBottomChromePadding.current

    val isAlphabetical = sortOrder == LikedSortOrder.ALPHABETICAL && songs.isNotEmpty()
    val sections = remember(songs, isAlphabetical) {
        if (isAlphabetical) groupItemsAlphabetically(songs) { it.title } else emptyList()
    }
    val orderedAlphabeticalSongs = remember(sections) { sections.flatMap { it.items } }
    val sectionStartOffsets = remember(sections) {
        buildSectionStartOffsets(sections)
    }

    val listState = rememberLazyListState()
    val scrollPx by remember {
        derivedStateOf { listState.firstVisibleItemIndex * 84 + listState.firstVisibleItemScrollOffset }
    }
    LaunchedEffect(scrollPx) { onScrollOffsetChanged(scrollPx) }

    val visibleRecommendations = remember(recommendations) { recommendations.take(10) }

    @Composable
    fun SongsLazyColumn(endPadding: Dp, includeInlineRecs: Boolean) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = endPadding,
                top = topContentPadding,
                bottom = bottomChromePadding
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (songs.isEmpty()) {
                item(key = "liked_songs_empty") {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        LikedEmptyStateCard(
                            icon = Icons.Rounded.FavoriteBorder,
                            title = "No Liked Songs Yet",
                            subtitle = "Tap the heart on any online stream or local track to save it here for quick playback.",
                            appColors = appColors,
                            accentColor = accentColor,
                            onExploreClick = onExploreMusicClick,
                            modifier = Modifier
                                .widthIn(max = 560.dp)
                                .padding(horizontal = 4.dp, vertical = 8.dp)
                        )
                    }
                }
            } else {
                item(key = "liked_songs_actions_bar") {
                    LikedPlaybackActionsBar(
                        onPlayAllClick = {
                            val listToPlay = if (isAlphabetical) orderedAlphabeticalSongs else songs
                            onPlayAllOrdered(listToPlay)
                        },
                        onShuffleClick = onShuffleClick,
                        appColors = appColors,
                        accentColor = accentColor,
                        modifier = Modifier
                            .widthIn(max = 560.dp)
                            .padding(bottom = 4.dp)
                    )
                }

                if (isAlphabetical) {
                    sections.forEachIndexed { sectionIndex, section ->
                        val sectionStart = sectionStartOffsets[sectionIndex]
                        item(
                            key = "songs_header_${section.letter}",
                            contentType = "AlphabetHeader"
                        ) {
                            LikedAlphabetSectionHeader(
                                letter = section.letter,
                                count = section.items.size,
                                appColors = appColors,
                                accentColor = accentColor,
                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                            )
                        }

                        lazyListItemsIndexed(
                            items = section.items,
                            key = { idx, track -> "liked_az_${section.letter}_${track.id}_$idx" },
                            contentType = { _, _ -> "LikedSongRow" }
                        ) { localIdx, track ->
                            val isCurrent = track.id == currentPlayingTrackId
                            LikedSongRow(
                                track = track,
                                isPlaying = isCurrent && isPlaybackPlaying,
                                isCurrentTrack = isCurrent,
                                appColors = appColors,
                                accentColor = accentColor,
                                onTrackClick = {
                                    onPlayTrackAt(orderedAlphabeticalSongs, sectionStart + localIdx)
                                },
                                onToggleFavorite = { onToggleFavorite(track) },
                                onMoreClick = { onTrackMoreClick(track) },
                                modifier = Modifier.animateItem(
                                    fadeInSpec = tween(200),
                                    placementSpec = spring(
                                        dampingRatio = 0.68f,
                                        stiffness = Spring.StiffnessMediumLow
                                    ),
                                    fadeOutSpec = tween(160)
                                )
                            )
                        }
                    }
                } else {
                    lazyListItemsIndexed(
                        items = songs,
                        key = { idx, track -> "liked_recent_${track.id}_$idx" },
                        contentType = { _, _ -> "LikedSongRow" }
                    ) { idx, track ->
                        val isCurrent = track.id == currentPlayingTrackId
                        LikedSongRow(
                            track = track,
                            isPlaying = isCurrent && isPlaybackPlaying,
                            isCurrentTrack = isCurrent,
                            appColors = appColors,
                            accentColor = accentColor,
                            onTrackClick = { onPlayTrackAt(songs, idx) },
                            onToggleFavorite = { onToggleFavorite(track) },
                            onMoreClick = { onTrackMoreClick(track) },
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(200),
                                placementSpec = spring(
                                    dampingRatio = 0.68f,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                fadeOutSpec = tween(160)
                            )
                        )
                    }
                }
            }

            if (includeInlineRecs) {
                // Recommendations Shelf ("Fans Also Like & Recommended")
                item(key = "liked_recs_header") {
                    LikedRecommendationsHeader(
                        appColors = appColors,
                        modifier = Modifier.padding(top = 18.dp, bottom = 4.dp, start = 4.dp, end = 4.dp)
                    )
                }

                if (isLoadingRecommendations && visibleRecommendations.isEmpty()) {
                    item(key = "liked_recs_shimmer") {
                        LikedRecommendationsSkeleton()
                    }
                } else if (visibleRecommendations.isEmpty()) {
                    item(key = "liked_recs_empty") {
                        Text(
                            text = "Add more favorites to get personalized recommendations.",
                            fontFamily = PlusJakartaSans,
                            fontSize = 12.5.sp,
                            color = appColors.textSecondary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    lazyListItemsIndexed(
                        items = visibleRecommendations,
                        key = { idx, track -> "liked_rec_${track.id}_$idx" },
                        contentType = { _, _ -> "LikedRecommendationRow" }
                    ) { idx, track ->
                        val isLiked = likedTrackIds.contains(track.id)
                        val isCurrent = track.id == currentPlayingTrackId
                        LikedRecommendationRow(
                            track = track,
                            isLiked = isLiked,
                            isCurrent = isCurrent,
                            isPlaying = isCurrent && isPlaybackPlaying,
                            appColors = appColors,
                            accentColor = accentColor,
                            onTrackClick = { onPlayTrackAt(visibleRecommendations, idx) },
                            onToggleFavorite = { onToggleFavorite(track) }
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun RenderSongsPane(includeInlineRecs: Boolean) {
        if (isAlphabetical && sections.isNotEmpty()) {
            // Index 0 is the Play All / Shuffle bar above the first A-Z header
            val (letterToFlatIndex, headerPositions) = remember(sections) {
                buildSectionIndexMaps(sections, initialOffset = 1)
            }
            val availableLetters = remember(sections) { sections.map { it.letter }.toSet() }
            val activeLetter by remember(headerPositions) {
                derivedStateOf {
                    val firstIdx = listState.firstVisibleItemIndex
                    headerPositions.lastOrNull { it.first <= firstIdx }?.second
                        ?: headerPositions.firstOrNull()?.second
                }
            }
            val firstSectionLetter = sections.firstOrNull()?.letter

            LikedScrubberDirectoryScaffold(
                availableLetters = availableLetters,
                activeLetter = activeLetter,
                topContentPadding = topContentPadding,
                onLetterSelected = { letter ->
                    val targetIdx = letterToFlatIndex[letter] ?: return@LikedScrubberDirectoryScaffold
                    activeScrubJob?.cancel()
                    activeScrubJob = scope.launch {
                        listState.scrollToItem(if (letter == firstSectionLetter) 0 else targetIdx)
                    }
                }
            ) {
                SongsLazyColumn(endPadding = 42.dp, includeInlineRecs = includeInlineRecs)
            }
        } else {
            SongsLazyColumn(endPadding = 16.dp, includeInlineRecs = includeInlineRecs)
        }
    }

    if (isTwoPane) {
        val navigator = rememberSupportingPaneScaffoldNavigator(
            scaffoldDirective = rememberAdaptivePaneScaffoldDirective()
        )
        SupportingPaneScaffold(
            directive = navigator.scaffoldDirective,
            value = navigator.scaffoldValue,
            modifier = Modifier.fillMaxSize(),
            mainPane = {
                AnimatedPane(
                    modifier = Modifier
                        .preferredWidth(0.58f)
                        .fillMaxHeight()
                ) {
                    RenderSongsPane(includeInlineRecs = false)
                }
            },
            supportingPane = {
                AnimatedPane(
                    modifier = Modifier
                        .preferredWidth(0.42f)
                        .fillMaxHeight()
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            top = topContentPadding,
                            bottom = bottomChromePadding,
                            start = 8.dp,
                            end = 20.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item(key = "liked_side_recs_header") {
                            LikedRecommendationsHeader(
                                appColors = appColors,
                                modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                            )
                        }
                        if (isLoadingRecommendations && visibleRecommendations.isEmpty()) {
                            item(key = "liked_side_recs_shimmer") {
                                LikedRecommendationsSkeleton()
                            }
                        } else if (visibleRecommendations.isEmpty()) {
                            item(key = "liked_side_recs_empty") {
                                Text(
                                    text = "Add more favorites to get personalized recommendations.",
                                    fontFamily = PlusJakartaSans,
                                    fontSize = 12.5.sp,
                                    color = appColors.textSecondary,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        } else {
                            lazyListItemsIndexed(
                                items = visibleRecommendations,
                                key = { idx, track -> "liked_side_rec_${track.id}_$idx" },
                                contentType = { _, _ -> "LikedRecommendationRow" }
                            ) { idx, track ->
                                val isLiked = likedTrackIds.contains(track.id)
                                val isCurrent = track.id == currentPlayingTrackId
                                LikedRecommendationRow(
                                    track = track,
                                    isLiked = isLiked,
                                    isCurrent = isCurrent,
                                    isPlaying = isCurrent && isPlaybackPlaying,
                                    appColors = appColors,
                                    accentColor = accentColor,
                                    onTrackClick = { onPlayTrackAt(visibleRecommendations, idx) },
                                    onToggleFavorite = { onToggleFavorite(track) }
                                )
                            }
                        }
                    }
                }
            }
        )
    } else {
        RenderSongsPane(includeInlineRecs = true)
    }
}

// =============================================================================
// TAB 2: LIKED ARTISTS (Circle / Home Artist Style + Alphabetical Scrubber)
// =============================================================================

@Composable
private fun LikedArtistsContent(
    artists: List<ArtistItem>,
    sortOrder: LikedSortOrder,
    topContentPadding: Dp,
    appColors: AppThemeColors,
    accentColor: Color,
    onArtistClick: (ArtistItem) -> Unit,
    onExploreMusicClick: () -> Unit,
    onScrollOffsetChanged: (Int) -> Unit
) {
    val windowSizeClass = LocalAppWindowSizeClass.current
    val bottomChromePadding = LocalBottomChromePadding.current
    if (artists.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topContentPadding, bottom = bottomChromePadding, start = 20.dp, end = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            LikedEmptyStateCard(
                icon = Icons.Rounded.Person,
                title = "No Favorite Artists",
                subtitle = "Artists whose songs you mark as favorite will appear grouped here.",
                appColors = appColors,
                accentColor = accentColor,
                onExploreClick = onExploreMusicClick,
                modifier = Modifier.widthIn(max = 560.dp)
            )
        }
        return
    }

    val scope = rememberCoroutineScope()
    var activeScrubJob by remember { mutableStateOf<Job?>(null) }
    val isAlphabetical = sortOrder == LikedSortOrder.ALPHABETICAL
    val sections = remember(artists, isAlphabetical) {
        if (isAlphabetical) groupItemsAlphabetically(artists) { it.name } else emptyList()
    }

    val gridState = rememberLazyGridState()
    val scrollPx by remember {
        derivedStateOf { gridState.firstVisibleItemIndex * 180 + gridState.firstVisibleItemScrollOffset }
    }
    LaunchedEffect(scrollPx) { onScrollOffsetChanged(scrollPx) }

    @Composable
    fun ArtistsLazyGrid(endPadding: Dp) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(
                minSize = windowSizeClass.adaptiveGridMinSize(108.dp, 128.dp, 144.dp)
            ),
            contentPadding = PaddingValues(
                start = 18.dp,
                end = endPadding,
                top = topContentPadding,
                bottom = bottomChromePadding
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (isAlphabetical) {
                var runningIndex = 0
                sections.forEach { section ->
                    item(
                        key = "artists_header_${section.letter}",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = "AlphabetHeader"
                    ) {
                        LikedAlphabetSectionHeader(
                            letter = section.letter,
                            count = section.items.size,
                            appColors = appColors,
                            accentColor = accentColor,
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                        )
                    }

                    val startIdx = runningIndex
                    runningIndex += section.items.size

                    lazyGridItemsIndexed(
                        items = section.items,
                        key = { idx, artist -> "liked_az_artist_${section.letter}_${artist.id}_$idx" },
                        contentType = { _, _ -> "LikedArtistCircleCard" }
                    ) { idx, artist ->
                        LikedStaggeredItemFrame(index = startIdx + idx) {
                            LikedArtistCircleCard(
                                artist = artist,
                                appColors = appColors,
                                accentColor = accentColor,
                                onClick = { onArtistClick(artist) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            } else {
                lazyGridItemsIndexed(
                    items = artists,
                    key = { idx, artist -> "liked_recent_artist_${artist.id}_$idx" },
                    contentType = { _, _ -> "LikedArtistCircleCard" }
                ) { idx, artist ->
                    LikedStaggeredItemFrame(index = idx) {
                        LikedArtistCircleCard(
                            artist = artist,
                            appColors = appColors,
                            accentColor = accentColor,
                            onClick = { onArtistClick(artist) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    if (isAlphabetical && sections.isNotEmpty()) {
        val (letterToFlatIndex, headerPositions) = remember(sections) {
            buildSectionIndexMaps(sections)
        }
        val availableLetters = remember(sections) { sections.map { it.letter }.toSet() }
        val activeLetter by remember(headerPositions) {
            derivedStateOf {
                val firstIdx = gridState.firstVisibleItemIndex
                headerPositions.lastOrNull { it.first <= firstIdx }?.second
                    ?: headerPositions.firstOrNull()?.second
            }
        }

        LikedScrubberDirectoryScaffold(
            availableLetters = availableLetters,
            activeLetter = activeLetter,
            topContentPadding = topContentPadding,
            onLetterSelected = { letter ->
                val targetIdx = letterToFlatIndex[letter] ?: return@LikedScrubberDirectoryScaffold
                activeScrubJob?.cancel()
                activeScrubJob = scope.launch {
                    gridState.scrollToItem(targetIdx)
                }
            }
        ) {
            ArtistsLazyGrid(endPadding = 42.dp)
        }
    } else {
        ArtistsLazyGrid(endPadding = 18.dp)
    }
}

// =============================================================================
// TAB 3: LIKED ALBUMS (Home Album Card Style in Grid + Alphabetical Scrubber)
// =============================================================================

@Composable
private fun LikedAlbumsContent(
    albums: List<AlbumItem>,
    sortOrder: LikedSortOrder,
    topContentPadding: Dp,
    appColors: AppThemeColors,
    accentColor: Color,
    onAlbumClick: (AlbumItem) -> Unit,
    onExploreMusicClick: () -> Unit,
    onScrollOffsetChanged: (Int) -> Unit
) {
    val windowSizeClass = LocalAppWindowSizeClass.current
    val bottomChromePadding = LocalBottomChromePadding.current
    if (albums.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topContentPadding, bottom = bottomChromePadding, start = 20.dp, end = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            LikedEmptyStateCard(
                icon = Icons.Rounded.Album,
                title = "No Favorite Albums",
                subtitle = "Albums featuring your liked songs will be organized here.",
                appColors = appColors,
                accentColor = accentColor,
                onExploreClick = onExploreMusicClick,
                modifier = Modifier.widthIn(max = 560.dp)
            )
        }
        return
    }

    val scope = rememberCoroutineScope()
    var activeScrubJob by remember { mutableStateOf<Job?>(null) }
    val isAlphabetical = sortOrder == LikedSortOrder.ALPHABETICAL
    val sections = remember(albums, isAlphabetical) {
        if (isAlphabetical) groupItemsAlphabetically(albums) { it.title } else emptyList()
    }

    val gridState = rememberLazyGridState()
    val scrollPx by remember {
        derivedStateOf { gridState.firstVisibleItemIndex * 220 + gridState.firstVisibleItemScrollOffset }
    }
    LaunchedEffect(scrollPx) { onScrollOffsetChanged(scrollPx) }

    @Composable
    fun AlbumsLazyGrid(endPadding: Dp) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(
                minSize = windowSizeClass.adaptiveGridMinSize(158.dp, 180.dp, 196.dp)
            ),
            contentPadding = PaddingValues(
                start = 18.dp,
                end = endPadding,
                top = topContentPadding,
                bottom = bottomChromePadding
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (isAlphabetical) {
                var runningIndex = 0
                sections.forEach { section ->
                    item(
                        key = "albums_header_${section.letter}",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = "AlphabetHeader"
                    ) {
                        LikedAlphabetSectionHeader(
                            letter = section.letter,
                            count = section.items.size,
                            appColors = appColors,
                            accentColor = accentColor,
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                        )
                    }

                    val startIdx = runningIndex
                    runningIndex += section.items.size

                    lazyGridItemsIndexed(
                        items = section.items,
                        key = { idx, album -> "liked_az_album_${section.letter}_${album.id}_$idx" },
                        contentType = { _, _ -> "LikedAlbumCard" }
                    ) { idx, album ->
                        LikedStaggeredItemFrame(index = startIdx + idx) {
                            LikedAlbumCard(
                                album = album,
                                appColors = appColors,
                                accentColor = accentColor,
                                onClick = { onAlbumClick(album) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.84f)
                            )
                        }
                    }
                }
            } else {
                lazyGridItemsIndexed(
                    items = albums,
                    key = { idx, album -> "liked_recent_album_${album.id}_$idx" },
                    contentType = { _, _ -> "LikedAlbumCard" }
                ) { idx, album ->
                    LikedStaggeredItemFrame(index = idx) {
                        LikedAlbumCard(
                            album = album,
                            appColors = appColors,
                            accentColor = accentColor,
                            onClick = { onAlbumClick(album) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.84f)
                        )
                    }
                }
            }
        }
    }

    if (isAlphabetical && sections.isNotEmpty()) {
        val (letterToFlatIndex, headerPositions) = remember(sections) {
            buildSectionIndexMaps(sections)
        }
        val availableLetters = remember(sections) { sections.map { it.letter }.toSet() }
        val activeLetter by remember(headerPositions) {
            derivedStateOf {
                val firstIdx = gridState.firstVisibleItemIndex
                headerPositions.lastOrNull { it.first <= firstIdx }?.second
                    ?: headerPositions.firstOrNull()?.second
            }
        }

        LikedScrubberDirectoryScaffold(
            availableLetters = availableLetters,
            activeLetter = activeLetter,
            topContentPadding = topContentPadding,
            onLetterSelected = { letter ->
                val targetIdx = letterToFlatIndex[letter] ?: return@LikedScrubberDirectoryScaffold
                activeScrubJob?.cancel()
                activeScrubJob = scope.launch {
                    gridState.scrollToItem(targetIdx)
                }
            }
        ) {
            AlbumsLazyGrid(endPadding = 42.dp)
        }
    } else {
        AlbumsLazyGrid(endPadding = 18.dp)
    }
}

// =============================================================================
// SCRUBBER DIRECTORY SCAFFOLD & ALPHABETICAL SECTION HEADERS
// =============================================================================

@Composable
private fun LikedScrubberDirectoryScaffold(
    availableLetters: Set<String>,
    activeLetter: String?,
    topContentPadding: Dp,
    onLetterSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val directoryBackdrop = rememberLayerBackdrop()
    val backdropFreeze = rememberBackdropFreeze()
    Box(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(backdropFreeze.connection)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(directoryBackdrop, frozen = backdropFreeze.frozen)
        ) {
            content()
        }
        CompositionLocalProvider(LocalAppBackdrop provides directoryBackdrop) {
            WaveAlphabetScrubber(
                availableLetters = availableLetters,
                activeLetter = activeLetter,
                onLetterSelected = onLetterSelected,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(top = topContentPadding + 4.dp, bottom = LocalBottomChromePadding.current, end = 4.dp)
            )
        }
    }
}

@Composable
private fun LikedAlphabetSectionHeader(
    letter: String,
    count: Int,
    appColors: AppThemeColors,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val displayAccent = readableAccentColor(accentColor, appColors.isDark)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(displayAccent.copy(alpha = if (appColors.isDark) 0.24f else 0.16f))
                .border(0.9.dp, displayAccent.copy(alpha = 0.50f), RoundedCornerShape(10.dp))
                .padding(horizontal = 11.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = letter,
                color = displayAccent,
                fontFamily = GoogleSans,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .height(0.8.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            displayAccent.copy(alpha = 0.42f),
                            if (appColors.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                            Color.Transparent
                        )
                    )
                )
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "$count",
            color = appColors.textSecondary,
            fontFamily = PlusJakartaSans,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun LikedStaggeredItemFrame(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shouldAnimateEntrance = remember(index) { index < 10 }
    val entrance = remember { Animatable(if (shouldAnimateEntrance) 0f else 1f) }
    LaunchedEffect(shouldAnimateEntrance) {
        if (shouldAnimateEntrance && entrance.value < 1f) {
            val delayMs = (index % 8) * 28L
            if (delayMs > 0L) delay(delayMs)
            entrance.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.54f,
                    stiffness = 340f
                )
            )
        }
    }

    Box(
        modifier = modifier.graphicsLayer {
            val p = entrance.value
            alpha = p.coerceIn(0f, 1f)
            val s = 0.86f + 0.14f * p
            scaleX = s
            scaleY = s
            translationY = (1f - p) * 28.dp.toPx()
        }
    ) {
        content()
    }
}

// =============================================================================
// PLAY ALL & SHUFFLE FROSTED ACTION BAR
// =============================================================================

@Composable
private fun LikedPlaybackActionsBar(
    onPlayAllClick: () -> Unit,
    onShuffleClick: () -> Unit,
    appColors: AppThemeColors,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LikedActionPillButton(
            label = "Play All",
            icon = Icons.Rounded.PlayArrow,
            isPrimary = true,
            appColors = appColors,
            accentColor = accentColor,
            onClick = onPlayAllClick,
            modifier = Modifier.weight(1f)
        )

        LikedActionPillButton(
            label = "Shuffle",
            icon = Icons.Rounded.Shuffle,
            isPrimary = false,
            appColors = appColors,
            accentColor = accentColor,
            onClick = onShuffleClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun LikedActionPillButton(
    label: String,
    icon: ImageVector,
    isPrimary: Boolean,
    appColors: AppThemeColors,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val jellyState = rememberJellyPressState(pressedScaleX = 0.95f, pressedScaleY = 0.90f)
    val contentTint = if (isPrimary) onAccentColor(accentColor) else appColors.textPrimary
    val bgBrush = if (isPrimary) {
        Brush.linearGradient(listOf(accentColor, accentColor.copy(alpha = 0.80f)))
    } else {
        Brush.linearGradient(
            if (appColors.isDark) {
                listOf(Color(0x30FFFFFF), Color(0x18FFFFFF))
            } else {
                listOf(Color(0xF5FFFFFF), Color(0xE0F1F5F9))
            }
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .height(42.dp)
            .jellyTapScale(jellyState)
            .clip(SegmentPillShape)
            .background(bgBrush)
            .border(
                width = 0.85.dp,
                color = if (isPrimary) {
                    Color.White.copy(alpha = 0.38f)
                } else if (appColors.isDark) {
                    Color.White.copy(alpha = 0.20f)
                } else {
                    Color.Black.copy(alpha = 0.10f)
                },
                shape = SegmentPillShape
            )
            .clickable(
                interactionSource = jellyState.interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentTint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = contentTint,
            fontFamily = GoogleSans,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// =============================================================================
// SONG ROW (Matching LibraryQueueSongRow + ExplicitBadge + Local Badge)
// =============================================================================

@Composable
private fun LikedSongRow(
    track: TrackItem,
    isPlaying: Boolean,
    isCurrentTrack: Boolean,
    appColors: AppThemeColors,
    accentColor: Color,
    onTrackClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sharedImageLoader = remember(context) {
        MediaNetworkClients.getImageLoader(context.applicationContext)
    }
    val displayAccent = readableAccentColor(accentColor, appColors.isDark)
    val isDark = appColors.isDark
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.972f else 1f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium),
        label = "likedSongPressScale"
    )

    val activePopScale = remember(track.id) { Animatable(1f) }
    LaunchedEffect(isCurrentTrack) {
        if (isCurrentTrack) {
            activePopScale.snapTo(0.96f)
            activePopScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    val glowPulse by if (isCurrentTrack && isPlaying) {
        val transition = rememberInfiniteTransition(label = "likedSongActiveGlow")
        transition.animateFloat(
            initialValue = 0.55f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1050, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "likedSongGlowPulse"
        )
    } else {
        remember(isCurrentTrack) { mutableFloatStateOf(if (isCurrentTrack) 0.65f else 0f) }
    }

    val rowElevation by animateDpAsState(
        targetValue = if (isCurrentTrack) 8.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "likedSongElevation"
    )

    val rowBackground by animateColorAsState(
        targetValue = when {
            isCurrentTrack -> displayAccent.copy(alpha = if (isDark) 0.22f else 0.16f)
            isDark -> Color(0x20FFFFFF)
            else -> Color(0x78FFFFFF)
        },
        animationSpec = tween(durationMillis = 220),
        label = "likedSongBg"
    )

    val borderBrush = if (isCurrentTrack) {
        Brush.linearGradient(
            listOf(
                displayAccent.copy(alpha = 0.72f * glowPulse),
                Color.White.copy(alpha = (if (isDark) 0.35f else 0.70f) * glowPulse),
                displayAccent.copy(alpha = 0.55f * glowPulse)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = if (isDark) 0.16f else 0.58f),
                Color.White.copy(alpha = if (isDark) 0.06f else 0.26f)
            )
        )
    }

    val coverScale by animateFloatAsState(
        targetValue = if (isCurrentTrack && isPlaying) 1.04f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "likedSongCoverScale"
    )

    val isLocal = remember(track.id, track.audioUrl) { isLocalLikedTrack(track) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                val combined = pressScale * activePopScale.value
                scaleX = combined
                scaleY = combined
            }
            .shadow(
                elevation = rowElevation,
                shape = SongRowShape,
                ambientColor = if (isCurrentTrack) displayAccent.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.22f),
                spotColor = if (isCurrentTrack) displayAccent.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.30f)
            )
            .clip(SongRowShape)
            .background(rowBackground)
            .border(
                width = if (isCurrentTrack) 1.25.dp else 0.8.dp,
                brush = borderBrush,
                shape = SongRowShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onTrackClick
            )
            .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LikedTrackCover(
            track = track,
            isCurrent = isCurrentTrack,
            accentColor = displayAccent,
            isDark = isDark,
            sharedImageLoader = sharedImageLoader,
            modifier = Modifier.graphicsLayer {
                scaleX = coverScale
                scaleY = coverScale
            }
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = track.title,
                    fontFamily = GoogleSans,
                    fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = if (isCurrentTrack) displayAccent else appColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (track.isExplicit) {
                    Spacer(modifier = Modifier.width(6.dp))
                    ExplicitBadge()
                }

                if (isLocal) {
                    Spacer(modifier = Modifier.width(6.dp))
                    LikedLocalBadge(accentColor = displayAccent)
                }
            }

            Text(
                text = if (track.album.isNotBlank() && !track.album.equals("Unknown Album", ignoreCase = true)) {
                    "${track.artist} • ${track.album}"
                } else {
                    track.artist
                },
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = if (isCurrentTrack) appColors.textPrimary.copy(alpha = 0.80f) else appColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        if (isCurrentTrack) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(displayAccent.copy(alpha = (0.16f + 0.12f * glowPulse)))
                    .border(0.75.dp, displayAccent.copy(alpha = 0.45f * glowPulse), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                LikedEqualizerBars(
                    color = displayAccent,
                    isPlaying = isPlaying
                )
            }
        } else if (track.durationMs > 0L) {
            Text(
                text = track.formattedDuration,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Medium,
                fontSize = 11.5.sp,
                color = appColors.textSecondary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        val favInteraction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .size(34.dp)
                .pressWobble(favInteraction, pressedScale = 0.82f)
                .clip(CircleShape)
                .clickable(
                    interactionSource = favInteraction,
                    indication = null,
                    onClick = onToggleFavorite
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = "Unlike",
                tint = HeartRubyTint,
                modifier = Modifier.size(18.dp)
            )
        }

        val moreInteraction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .size(34.dp)
                .pressWobble(moreInteraction, pressedScale = 0.82f)
                .clip(CircleShape)
                .clickable(
                    interactionSource = moreInteraction,
                    indication = null,
                    onClick = onMoreClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "Options",
                tint = appColors.textSecondary.copy(alpha = 0.86f),
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
private fun LikedLocalBadge(accentColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(accentColor.copy(alpha = 0.16f))
            .border(0.6.dp, accentColor.copy(alpha = 0.38f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = "Local File",
                tint = accentColor,
                modifier = Modifier.size(9.dp)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "LOCAL",
                fontFamily = PlusJakartaSans,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
        }
    }
}

@Composable
private fun LikedTrackCover(
    track: TrackItem,
    isCurrent: Boolean,
    accentColor: Color,
    isDark: Boolean,
    sharedImageLoader: ImageLoader,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var uriLoadFailed by remember(track.artworkUri) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(48.dp)
            .clip(SongArtworkShape)
            .background(if (isDark) Color(0xFF1E222B) else Color(0xFFE2E8F0))
            .then(
                if (isCurrent) {
                    Modifier.border(1.2.dp, accentColor.copy(alpha = 0.75f), SongArtworkShape)
                } else {
                    Modifier.border(0.6.dp, Color.White.copy(alpha = if (isDark) 0.14f else 0.35f), SongArtworkShape)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (track.artworkResId != null) {
            Image(
                painter = painterResource(id = track.artworkResId),
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (!track.artworkUri.isNullOrBlank() && !uriLoadFailed) {
            AsyncImage(
                model = ImageRequest.Builder(context.applicationContext)
                    .data(track.artworkUri)
                    .crossfade(200)
                    .build(),
                imageLoader = sharedImageLoader,
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                onError = { uriLoadFailed = true },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = if (isCurrent) accentColor else if (isDark) Color.White.copy(alpha = 0.38f) else Color.Black.copy(alpha = 0.38f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

// =============================================================================
// ARTIST CIRCLE CARD (Home Artist Style + YTM Avatar Resolution)
// =============================================================================

@Composable
private fun LikedArtistCircleCard(
    artist: ArtistItem,
    appColors: AppThemeColors,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 90.dp
) {
    val context = LocalContext.current
    val sharedImageLoader = remember(context) {
        MediaNetworkClients.getImageLoader(context.applicationContext)
    }
    val gradientColors = remember(artist.id) { expressiveGradientColors(artist.id) }
    val displayAccent = readableAccentColor(accentColor, appColors.isDark)

    var resolvedAvatarUri by remember(artist.id, artist.name, artist.avatarUri) {
        val cached = try {
            YouTubeMusicService.getInstance().getArtistAvatarFromCache(artist.name, context)
        } catch (_: Exception) {
            null
        }
        mutableStateOf(cached?.takeIf { it.isNotBlank() } ?: artist.avatarUri)
    }
    var uriLoadFailed by remember(resolvedAvatarUri) { mutableStateOf(false) }

    LaunchedEffect(artist.name) {
        val ytmAvatar = try {
            val ytm = YouTubeMusicService.getInstance()
            ytm.getArtistAvatarFromCache(artist.name, context)
                ?: ytm.getArtistAvatarUri(artist.name, context)
        } catch (_: Exception) {
            null
        }
        if (!ytmAvatar.isNullOrBlank() && ytmAvatar != resolvedAvatarUri) {
            uriLoadFailed = false
            resolvedAvatarUri = ytmAvatar
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
        modifier = modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    ) {
        Box(
            modifier = Modifier
                .size(avatarSize)
                .graphicsLayer {
                    scaleX = jelly.scaleX.value
                    scaleY = jelly.scaleY.value
                }
                .shadow(
                    elevation = 10.dp,
                    shape = CircleShape,
                    ambientColor = displayAccent.copy(alpha = 0.25f),
                    spotColor = Color.Black.copy(alpha = 0.45f)
                )
                .clip(CircleShape)
                .background(Brush.linearGradient(gradientColors))
                .border(
                    width = 1.6.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (appColors.isDark) 0.48f else 0.85f),
                            displayAccent.copy(alpha = 0.65f),
                            Color.White.copy(alpha = if (appColors.isDark) 0.14f else 0.35f)
                        )
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (artist.avatarResId != null) {
                Image(
                    painter = painterResource(id = artist.avatarResId),
                    contentDescription = artist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (!resolvedAvatarUri.isNullOrBlank() && !uriLoadFailed) {
                AsyncImage(
                    model = ImageRequest.Builder(context.applicationContext)
                        .data(resolvedAvatarUri)
                        .crossfade(200)
                        .build(),
                    imageLoader = sharedImageLoader,
                    contentDescription = artist.name,
                    contentScale = ContentScale.Crop,
                    onError = {
                        if (resolvedAvatarUri != artist.avatarUri && !artist.avatarUri.isNullOrBlank()) {
                            resolvedAvatarUri = artist.avatarUri
                        } else {
                            uriLoadFailed = true
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = artist.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    color = Color.White,
                    fontFamily = GoogleSans,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = artist.name,
            color = appColors.textPrimary,
            fontFamily = GoogleSans,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer {
                val followScale = 1f + (jelly.scaleY.value - 1f) * 0.4f
                scaleX = followScale
                scaleY = followScale
                translationY = (1f - jelly.scaleY.value) * 4.dp.toPx()
            }
        )

        if (artist.genre.isNotBlank()) {
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = artist.genre,
                color = appColors.textSecondary,
                fontFamily = PlusJakartaSans,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

// =============================================================================
// ALBUM GRID CARD (Home Album Card Style with GlossyCardShell + Blur Scrim)
// =============================================================================

@Composable
private fun LikedAlbumCard(
    album: AlbumItem,
    appColors: AppThemeColors,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val jellyState = rememberJellyPressState(pressedScaleX = 0.955f, pressedScaleY = 0.92f)
    val context = LocalContext.current
    val sharedImageLoader = remember(context) {
        MediaNetworkClients.getImageLoader(context.applicationContext)
    }
    val gradientColors = remember(album.id) { expressiveGradientColors(album.id) }
    var uriLoadFailed by remember(album.coverUri) { mutableStateOf(false) }

    val artworkModel: Any? = remember(album.coverResId, album.coverUri, uriLoadFailed) {
        album.coverResId ?: album.coverUri?.takeIf { it.isNotBlank() && !uriLoadFailed }
    }

    GlossyCardShell(
        shape = AlbumCardShape,
        jellyState = jellyState,
        onClick = onClick,
        elevation = 8.dp,
        containerColor = if (appColors.isDark) Color(0xFF151924) else Color(0xFFE7ECF5),
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
            !album.coverUri.isNullOrBlank() && !uriLoadFailed -> {
                AsyncImage(
                    model = ImageRequest.Builder(context.applicationContext)
                        .data(album.coverUri)
                        .crossfade(200)
                        .build(),
                    imageLoader = sharedImageLoader,
                    contentDescription = "${album.title} - ${album.artist}",
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    onError = { uriLoadFailed = true },
                    modifier = Modifier.matchParentSize()
                )
            }
            else -> {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Brush.linearGradient(gradientColors)),
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
            blurRadius = 20.dp,
            tintColor = Color.Black,
            blurStartFraction = 0.46f,
            scrimTopAlpha = 0.0f,
            scrimBottomAlpha = 0.80f,
            modifier = Modifier.matchParentSize()
        )

        if (album.tracksCount > 0) {
            FrostedCardBadge(
                text = "${album.tracksCount} liked",
                dotColor = HeartRubyTint,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
            )
        } else if (album.year > 1900) {
            FrostedCardBadge(
                text = album.year.toString(),
                dotColor = accentColor,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
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

// =============================================================================
// YOUTUBE MUSIC RECOMMENDATIONS SHELF ("FANS ALSO LIKE & RECOMMENDED")
// =============================================================================

@Composable
private fun LikedRecommendationsHeader(
    appColors: AppThemeColors,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Fans Also Like & Recommended",
            fontFamily = GoogleSans,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = appColors.textPrimary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Discoveries from YouTube Music based on your favorites",
            fontFamily = PlusJakartaSans,
            fontSize = 11.5.sp,
            color = appColors.textSecondary
        )
    }
}

@Composable
private fun LikedRecommendationsSkeleton() {
    val appColors = LocalAppColors.current
    ShimmerScope {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            repeat(3) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(SongRowShape)
                        .background(if (appColors.isDark) Color(0x18FFFFFF) else Color(0x88FFFFFF))
                        .border(
                            0.8.dp,
                            Color.White.copy(alpha = if (appColors.isDark) 0.10f else 0.35f),
                            SongRowShape
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ShimmerBox(
                        modifier = Modifier.size(44.dp),
                        shape = SongArtworkShape
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ShimmerLine(fraction = 0.62f, height = 13.dp)
                        ShimmerLine(fraction = 0.38f, height = 10.dp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    ShimmerBox(
                        modifier = Modifier.size(28.dp),
                        shape = CircleShape
                    )
                }
            }
        }
    }
}

@Composable
private fun LikedRecommendationRow(
    track: TrackItem,
    isLiked: Boolean,
    isCurrent: Boolean,
    isPlaying: Boolean,
    appColors: AppThemeColors,
    accentColor: Color,
    onTrackClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val context = LocalContext.current
    val sharedImageLoader = remember(context) {
        MediaNetworkClients.getImageLoader(context.applicationContext)
    }
    val displayAccent = readableAccentColor(accentColor, appColors.isDark)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.975f else 1f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium),
        label = "likedRecRowScale"
    )
    val playScale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium),
        label = "likedRecPlayScale"
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(SongRowShape)
            .background(
                when {
                    isCurrent -> displayAccent.copy(alpha = if (appColors.isDark) 0.18f else 0.12f)
                    appColors.isDark -> Color(0x1CFFFFFF)
                    else -> Color(0x88FFFFFF)
                }
            )
            .border(
                width = if (isCurrent) 1.dp else 0.8.dp,
                color = if (isCurrent) {
                    displayAccent.copy(alpha = 0.55f)
                } else {
                    Color.White.copy(alpha = if (appColors.isDark) 0.12f else 0.45f)
                },
                shape = SongRowShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onTrackClick
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LikedTrackCover(
            track = track,
            isCurrent = isCurrent,
            accentColor = displayAccent,
            isDark = appColors.isDark,
            sharedImageLoader = sharedImageLoader
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.title,
                    fontFamily = GoogleSans,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    color = if (isCurrent) displayAccent else appColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (track.isExplicit) {
                    Spacer(modifier = Modifier.width(6.dp))
                    ExplicitBadge()
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.artist,
                fontFamily = PlusJakartaSans,
                fontSize = 11.5.sp,
                color = appColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        val heartSource = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .pressWobble(heartSource, pressedScale = 0.82f)
                .size(32.dp)
                .graphicsLayer {
                    scaleX = likePop.value
                    scaleY = likePop.value
                }
                .clip(CircleShape)
                .clickable(
                    interactionSource = heartSource,
                    indication = null,
                    onClick = onToggleFavorite
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (isLiked) "Unlike" else "Like",
                tint = if (isLiked) HeartRubyTint else appColors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        if (isCurrent) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(displayAccent.copy(alpha = 0.18f))
                    .border(0.75.dp, displayAccent.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                LikedEqualizerBars(
                    color = displayAccent,
                    isPlaying = isPlaying
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .graphicsLayer {
                        scaleX = playScale
                        scaleY = playScale
                    }
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(displayAccent, displayAccent.copy(alpha = 0.78f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Play",
                    tint = onAccentColor(displayAccent),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// =============================================================================
// EMPTY STATE & EQUALIZER VISUALIZER BARS
// =============================================================================

@Composable
private fun LikedEmptyStateCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    appColors: AppThemeColors,
    accentColor: Color,
    onExploreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val jellyState = rememberJellyPressState(pressedScaleX = 0.94f, pressedScaleY = 0.90f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(EmptyCardShape)
            .background(
                if (appColors.isDark) Color(0x99171B26) else Color(0xE8FFFFFF)
            )
            .border(
                width = 0.9.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (appColors.isDark) 0.22f else 0.75f),
                        Color.White.copy(alpha = if (appColors.isDark) 0.05f else 0.20f)
                    )
                ),
                shape = EmptyCardShape
            )
            .padding(horizontal = 24.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(HeartRubyTint.copy(alpha = if (appColors.isDark) 0.20f else 0.12f))
                    .border(1.dp, HeartRubyTint.copy(alpha = 0.42f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = HeartRubyTint,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = title,
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = appColors.textPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = subtitle,
                fontFamily = PlusJakartaSans,
                fontSize = 13.sp,
                color = appColors.textSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .jellyTapScale(jellyState)
                    .clip(SegmentPillShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(accentColor, accentColor.copy(alpha = 0.80f))
                        )
                    )
                    .clickable(
                        interactionSource = jellyState.interactionSource,
                        indication = null,
                        onClick = onExploreClick
                    )
                    .padding(horizontal = 18.dp, vertical = 9.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Explore,
                    contentDescription = null,
                    tint = onAccentColor(accentColor),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Explore Music",
                    fontFamily = GoogleSans,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = onAccentColor(accentColor)
                )
            }
        }
    }
}

@Composable
private fun LikedEqualizerBars(
    color: Color,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barHeight: Dp = 14.dp
) {
    val barHeights: List<Float> = if (isPlaying) {
        val transition = rememberInfiniteTransition(label = "likedEq")
        listOf(
            transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 0.95f,
                animationSpec = infiniteRepeatable(
                    animation = tween(360, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "likedEq0"
            ).value,
            transition.animateFloat(
                initialValue = 0.90f,
                targetValue = 0.28f,
                animationSpec = infiniteRepeatable(
                    animation = tween(430, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "likedEq1"
            ).value,
            transition.animateFloat(
                initialValue = 0.38f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(310, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "likedEq2"
            ).value,
            transition.animateFloat(
                initialValue = 0.80f,
                targetValue = 0.32f,
                animationSpec = infiniteRepeatable(
                    animation = tween(490, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "likedEq3"
            ).value
        )
    } else {
        listOf(0.32f, 0.56f, 0.42f, 0.35f)
    }

    Row(
        modifier = modifier.height(barHeight),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        barHeights.forEach { frac ->
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .fillMaxHeight(frac.coerceIn(0.22f, 1f))
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(color)
            )
        }
    }
}

// =============================================================================
// PURE DERIVATION & INDEX HELPERS
// =============================================================================

private fun isLocalLikedTrack(track: TrackItem): Boolean =
    track.audioUrl?.startsWith("content://") == true ||
        track.audioUrl?.startsWith("file://") == true ||
        track.id.startsWith("local_") ||
        track.id.startsWith("saf_")

private fun formatCollectionDuration(totalDurationMs: Long): String {
    val totalSec = (totalDurationMs / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val hrs = mins / 60L
    return if (hrs > 0L) {
        "${hrs} hr ${mins % 60L} min"
    } else {
        "$mins min"
    }
}

internal fun deriveLikedArtists(
    likedTracks: List<TrackItem>,
    overrideArtists: List<ArtistItem> = emptyList()
): List<ArtistItem> {
    if (overrideArtists.isNotEmpty()) return overrideArtists
    if (likedTracks.isEmpty()) return emptyList()

    val grouped = linkedMapOf<String, MutableList<TrackItem>>()
    for (track in likedTracks) {
        val artistName = track.artist.trim()
        if (artistName.isBlank() ||
            artistName.equals("Unknown Artist", ignoreCase = true) ||
            artistName.equals("<unknown>", ignoreCase = true)
        ) {
            continue
        }
        grouped.getOrPut(artistName.lowercase(Locale.ROOT)) { mutableListOf() }.add(track)
    }

    return grouped.values.mapNotNull { tracks ->
        val first = tracks.firstOrNull() ?: return@mapNotNull null
        val displayName = first.artist.trim()
        ArtistItem(
            id = "liked_artist_${displayName.lowercase(Locale.ROOT).hashCode()}",
            name = displayName,
            avatarResId = tracks.firstNotNullOfOrNull { it.artworkResId },
            avatarUri = tracks.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri,
            genre = "${tracks.size} ${if (tracks.size == 1) "song" else "songs"}"
        )
    }
}

internal fun deriveLikedAlbums(
    likedTracks: List<TrackItem>,
    overrideAlbums: List<AlbumItem> = emptyList()
): List<AlbumItem> {
    if (overrideAlbums.isNotEmpty()) return overrideAlbums
    if (likedTracks.isEmpty()) return emptyList()

    val grouped = linkedMapOf<String, MutableList<Pair<String, TrackItem>>>()
    for (track in likedTracks) {
        val rawAlbum = track.album.trim()
        if (rawAlbum.isBlank() ||
            rawAlbum.equals("Unknown Album", ignoreCase = true) ||
            rawAlbum.equals("YouTube Music", ignoreCase = true)
        ) {
            continue
        }
        val artistKey = track.artist.trim().lowercase(Locale.ROOT)
        val groupKey = "${rawAlbum.lowercase(Locale.ROOT)}__$artistKey"
        grouped.getOrPut(groupKey) { mutableListOf() }.add(rawAlbum to track)
    }

    return grouped.values.mapNotNull { entries ->
        val albumTitle = entries.firstOrNull()?.first ?: return@mapNotNull null
        val tracks = entries.map { it.second }
        val first = tracks.firstOrNull() ?: return@mapNotNull null
        val artistName = first.artist.trim().ifBlank { "Unknown Artist" }
        AlbumItem(
            id = "liked_album_${albumTitle.lowercase(Locale.ROOT).hashCode()}_${artistName.lowercase(Locale.ROOT).hashCode()}",
            title = albumTitle,
            artist = artistName,
            coverResId = tracks.firstNotNullOfOrNull { it.artworkResId },
            coverUri = tracks.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri,
            tracksCount = tracks.size
        )
    }
}

private fun <T> buildSectionStartOffsets(sections: List<AlphabetSection<T>>): List<Int> {
    val offsets = ArrayList<Int>(sections.size)
    var running = 0
    for (section in sections) {
        offsets.add(running)
        running += section.items.size
    }
    return offsets
}

private fun <T> buildSectionIndexMaps(
    sections: List<AlphabetSection<T>>,
    initialOffset: Int = 0
): Pair<Map<String, Int>, List<Pair<Int, String>>> {
    val map = LinkedHashMap<String, Int>(sections.size)
    val positions = ArrayList<Pair<Int, String>>(sections.size)
    var flatIndex = initialOffset
    for (section in sections) {
        map[section.letter] = flatIndex
        positions.add(flatIndex to section.letter)
        flatIndex += 1 + section.items.size
    }
    return map to positions
}

@Composable
private fun LikedPreviewContent(widthDp: Int, heightDp: Int) {
    AdaptivePreviewSurface(widthDp = widthDp, heightDp = heightDp) {
        LikedScreen(
            likedTracks = AdaptivePreviewFixtures.tracks,
            likedTrackIds = setOf("ytm_paparazzi", "ytm_wrote_a_song"),
            recommendations = AdaptivePreviewFixtures.tracks.drop(2),
            isLoadingRecommendations = false,
            currentPlayingTrackId = "ytm_paparazzi",
            isPlaybackPlaying = true,
            onPlayAllClick = {},
            onShuffleClick = {},
            onTrackClick = {},
            onToggleFavorite = {},
            onTrackMoreClick = {},
            onArtistClick = {},
            onAlbumClick = {},
            onExploreMusicClick = {},
            applyStatusBarsPadding = true
        )
    }
}

@Preview(name = "Phone (360x800)", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
private fun LikedPhonePreview() = LikedPreviewContent(360, 800)

@Preview(name = "Tablet Portrait (800x1280)", widthDp = 800, heightDp = 1280, showBackground = true)
@Composable
private fun LikedTabletPortraitPreview() = LikedPreviewContent(800, 1280)

@Preview(name = "Tablet Landscape (1280x800)", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
private fun LikedTabletLandscapePreview() = LikedPreviewContent(1280, 800)
