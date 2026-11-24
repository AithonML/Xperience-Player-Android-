package com.xperience.player.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as lazyGridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.xperience.player.domain.model.AlbumItem
import com.xperience.player.domain.model.ArtistItem
import com.xperience.player.domain.model.GenreCatalog
import com.xperience.player.domain.model.GenreCategory
import com.xperience.player.domain.model.RecommendedPlaylistItem
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.domain.model.YtmMoodCategory
import com.xperience.player.domain.model.YtmMoodCategoryDetail
import com.xperience.player.domain.model.YtmMoodSection
import com.xperience.player.domain.model.YtmMoodsAndGenresFeed
import com.xperience.player.service.MediaNetworkClients
import com.xperience.player.service.SearchFilter
import com.xperience.player.service.SearchResultData
import com.xperience.player.service.YouTubeMusicService
import com.xperience.player.ui.adaptive.AdaptivePreviewFixtures
import com.xperience.player.ui.adaptive.AdaptivePreviewSurface
import com.xperience.player.ui.adaptive.LocalAppWindowSizeClass
import com.xperience.player.ui.adaptive.LocalBottomChromePadding
import com.xperience.player.ui.adaptive.LocalTopChromePadding
import com.xperience.player.ui.adaptive.adaptiveGridMinSize
import com.xperience.player.ui.adaptive.rememberAdaptivePaneScaffoldDirective
import com.xperience.player.ui.adaptive.supportsTwoPaneLayout
import com.xperience.player.ui.component.ExplicitBadge
import com.xperience.player.ui.component.GlassCircleButton
import com.xperience.player.ui.component.GlassComponent
import com.xperience.player.ui.component.GlassEffectConfig
import com.xperience.player.ui.component.LiquidGlassSegmentedPill
import com.xperience.player.ui.component.LocalAppBackdrop
import com.xperience.player.ui.component.LocalGlassEffectConfig
import com.xperience.player.ui.component.ShimmerBox
import com.xperience.player.ui.component.ShimmerLine
import com.xperience.player.ui.component.ShimmerScope
import com.xperience.player.ui.component.backdrop.backdrops.layerBackdrop
import com.xperience.player.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.xperience.player.ui.component.isGlassAllowed
import com.xperience.player.ui.component.liquidGlass
import com.xperience.player.ui.components.HomeAuroraBackground
import com.xperience.player.ui.components.expressiveGradientColors
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

private val SearchAlbumCardShape = RoundedCornerShape(18.dp)
private val SearchThumbShape = RoundedCornerShape(10.dp)
private val MoodCardShape = RoundedCornerShape(16.dp)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun SearchScreen(
    searchQuery: String,
    searchFilter: SearchFilter,
    searchResults: SearchResultData,
    isSearching: Boolean,
    recentSearches: List<String>,
    onSearchQueryChange: (String) -> Unit,
    onSearchFilterChange: (SearchFilter) -> Unit,
    onRecentSearchClick: (String) -> Unit,
    onRemoveRecentSearch: (String) -> Unit,
    onClearRecentSearches: () -> Unit,
    onGenreClick: (GenreCategory) -> Unit,
    onTrackClick: (TrackItem) -> Unit,
    onAlbumClick: (AlbumItem) -> Unit,
    onArtistClick: (ArtistItem) -> Unit,
    onDismissSearch: () -> Unit,
    likedTrackIds: Set<String> = emptySet(),
    onToggleFavorite: ((TrackItem) -> Unit)? = null,
    auroraColors: AuroraColors = DEFAULT_AURORA_COLORS,
    currentPlayingTrackId: String? = null,
    isPlaybackPlaying: Boolean = false,
    onTrackMoreClick: ((TrackItem) -> Unit)? = null,
    onPlayTrackFromList: ((List<TrackItem>, Int) -> Unit)? = null,
    moodsAndGenresFeed: YtmMoodsAndGenresFeed? = null,
    selectedMoodCategory: YtmMoodCategory? = null,
    moodDetailResult: YtmMoodCategoryDetail? = null,
    isLoadingMoods: Boolean = false,
    isLoadingMoodDetail: Boolean = false,
    onMoodCategoryClick: (YtmMoodCategory) -> Unit = {},
    onClearMoodCategory: () -> Unit = {},
    onMoodPlaylistClick: (RecommendedPlaylistItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    @Suppress("UNUSED_VARIABLE")
    val unusedQueryCallback = onSearchQueryChange

    val appColors = LocalAppColors.current
    val accentColor = LocalAccentColor.current
    val readableAccent = readableAccentColor(accentColor, appColors.isDark)
    val glassConfig = LocalGlassEffectConfig.current
    val useGlass = glassConfig.isEnabledFor(GlassComponent.NAV_BAR) && isGlassAllowed()
    val density = LocalDensity.current
    val windowSizeClass = LocalAppWindowSizeClass.current
    val topChromePadding = LocalTopChromePadding.current
    val isTwoPaneBrowse = windowSizeClass.supportsTwoPaneLayout

    val isSearchingActive = searchQuery.isNotBlank()
    val isViewingMoodDetail = !isSearchingActive && selectedMoodCategory != null

    // Intercept system Back while browsing a Mood/Genre detail page so it returns to Mood sections.
    BackHandler(enabled = isViewingMoodDetail) {
        onClearMoodCategory()
    }

    val exploreGridState = rememberLazyGridState()
    val moodDetailGridState = rememberLazyGridState()
    val resultsGridState = rememberLazyGridState()

    LaunchedEffect(selectedMoodCategory?.id) {
        if (selectedMoodCategory != null) {
            moodDetailGridState.scrollToItem(0)
        }
    }

    LaunchedEffect(searchQuery, searchFilter) {
        if (searchQuery.isNotBlank()) {
            resultsGridState.scrollToItem(0)
        }
    }

    val activeGridState = when {
        isSearchingActive -> resultsGridState
        isViewingMoodDetail -> moodDetailGridState
        else -> exploreGridState
    }

    val scrollOffsetPx by remember(activeGridState) {
        derivedStateOf {
            activeGridState.firstVisibleItemIndex * 220 + activeGridState.firstVisibleItemScrollOffset
        }
    }

    var floatingHeaderHeightPx by remember { mutableIntStateOf(0) }
    val statusBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val fallbackTopPaddingDp = statusBarTopPadding + topChromePadding +
        if (isSearchingActive || isViewingMoodDetail) 144.dp else 88.dp
    val topContentPadding by remember(floatingHeaderHeightPx, fallbackTopPaddingDp) {
        derivedStateOf {
            if (floatingHeaderHeightPx > 0) {
                with(density) { floatingHeaderHeightPx.toDp() } + 8.dp
            } else {
                fallbackTopPaddingDp
            }
        }
    }

    // Isolated LayerBackdrop capturing the Aurora background and scrolled cards so the pinned
    // Close GlassCircleButton and LiquidGlassSegmentedPill refract cleanly without recursion.
    val searchBackdrop = rememberLayerBackdrop {
        drawRect(appColors.background)
        drawContent()
    }

    val subtitleText = when {
        isSearchingActive -> "Results for \"$searchQuery\""
        selectedMoodCategory != null -> "Moods & Genres · ${selectedMoodCategory.title}"
        else -> "Explore music, moods & genres"
    }

    Box(modifier = modifier.fillMaxSize()) {
        // LAYER 1: Ambient Aurora backdrop + radial accent glow + scrollable Search content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(searchBackdrop)
        ) {
            HomeAuroraBackground(
                auroraColors = auroraColors,
                isDark = appColors.isDark,
                scrollOffsetPxProvider = { scrollOffsetPx },
                modifier = Modifier.fillMaxSize()
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = if (appColors.isDark) 0.14f else 0.08f),
                            auroraColors.color2.copy(alpha = if (appColors.isDark) 0.06f else 0.03f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.84f, size.height * 0.09f),
                        radius = size.width * 0.85f
                    ),
                    center = Offset(size.width * 0.84f, size.height * 0.09f),
                    radius = size.width * 0.85f
                )
            }

            when {
                isSearchingActive -> {
                    SearchResultsListContent(
                        gridState = resultsGridState,
                        topContentPadding = topContentPadding,
                        searchQuery = searchQuery,
                        searchFilter = searchFilter,
                        searchResults = searchResults,
                        isSearching = isSearching,
                        likedTrackIds = likedTrackIds,
                        currentPlayingTrackId = currentPlayingTrackId,
                        isPlaybackPlaying = isPlaybackPlaying,
                        onSearchFilterChange = onSearchFilterChange,
                        onTrackClick = onTrackClick,
                        onAlbumClick = onAlbumClick,
                        onArtistClick = onArtistClick,
                        onToggleFavorite = onToggleFavorite,
                        onTrackMoreClick = onTrackMoreClick
                    )
                }

                isTwoPaneBrowse -> {
                    val listDetailNavigator = rememberListDetailPaneScaffoldNavigator<Nothing>(
                        scaffoldDirective = rememberAdaptivePaneScaffoldDirective()
                    )
                    ListDetailPaneScaffold(
                        directive = listDetailNavigator.scaffoldDirective,
                        value = listDetailNavigator.scaffoldValue,
                        modifier = Modifier.fillMaxSize(),
                        listPane = {
                            AnimatedPane(
                                modifier = Modifier
                                    .preferredWidth(0.42f)
                                    .fillMaxHeight()
                            ) {
                                ExploreSearchContent(
                                    gridState = exploreGridState,
                                    topContentPadding = topContentPadding,
                                    recentSearches = recentSearches,
                                    moodsAndGenresFeed = moodsAndGenresFeed,
                                    isLoadingMoods = isLoadingMoods,
                                    onRecentSearchClick = onRecentSearchClick,
                                    onRemoveRecentSearch = onRemoveRecentSearch,
                                    onClearRecentSearches = onClearRecentSearches,
                                    onMoodCategoryClick = onMoodCategoryClick,
                                    onFallbackGenreClick = onGenreClick
                                )
                            }
                        },
                        detailPane = {
                            AnimatedPane(
                                modifier = Modifier
                                    .preferredWidth(0.58f)
                                    .fillMaxHeight()
                            ) {
                                if (selectedMoodCategory != null) {
                                    MoodCategoryDetailContent(
                                        gridState = moodDetailGridState,
                                        topContentPadding = topContentPadding,
                                        category = selectedMoodCategory,
                                        moodDetailResult = moodDetailResult,
                                        isLoading = isLoadingMoodDetail,
                                        likedTrackIds = likedTrackIds,
                                        currentPlayingTrackId = currentPlayingTrackId,
                                        isPlaybackPlaying = isPlaybackPlaying,
                                        onMoodPlaylistClick = onMoodPlaylistClick,
                                        onAlbumClick = onAlbumClick,
                                        onTrackClick = onTrackClick,
                                        onPlayTrackFromList = onPlayTrackFromList,
                                        onToggleFavorite = onToggleFavorite,
                                        onTrackMoreClick = onTrackMoreClick,
                                        onSearchFallbackClick = { onRecentSearchClick(selectedMoodCategory.title) },
                                        onClearMoodCategory = onClearMoodCategory
                                    )
                                } else {
                                    SearchMoodEmptySelectionPane(
                                        topContentPadding = topContentPadding,
                                        accentColor = readableAccent
                                    )
                                }
                            }
                        }
                    )
                }

                selectedMoodCategory != null -> {
                    MoodCategoryDetailContent(
                        gridState = moodDetailGridState,
                        topContentPadding = topContentPadding,
                        category = selectedMoodCategory,
                        moodDetailResult = moodDetailResult,
                        isLoading = isLoadingMoodDetail,
                        likedTrackIds = likedTrackIds,
                        currentPlayingTrackId = currentPlayingTrackId,
                        isPlaybackPlaying = isPlaybackPlaying,
                        onMoodPlaylistClick = onMoodPlaylistClick,
                        onAlbumClick = onAlbumClick,
                        onTrackClick = onTrackClick,
                        onPlayTrackFromList = onPlayTrackFromList,
                        onToggleFavorite = onToggleFavorite,
                        onTrackMoreClick = onTrackMoreClick,
                        onSearchFallbackClick = { onRecentSearchClick(selectedMoodCategory.title) },
                        onClearMoodCategory = onClearMoodCategory
                    )
                }

                else -> {
                    ExploreSearchContent(
                        gridState = exploreGridState,
                        topContentPadding = topContentPadding,
                        recentSearches = recentSearches,
                        moodsAndGenresFeed = moodsAndGenresFeed,
                        isLoadingMoods = isLoadingMoods,
                        onRecentSearchClick = onRecentSearchClick,
                        onRemoveRecentSearch = onRemoveRecentSearch,
                        onClearRecentSearches = onClearRecentSearches,
                        onMoodCategoryClick = onMoodCategoryClick,
                        onFallbackGenreClick = onGenreClick
                    )
                }
            }
        }

        // LAYER 2a: Top vertical background gradient fade so sticky text and LiquidGlass stay readable
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(topContentPadding + 18.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to appColors.background.copy(alpha = if (appColors.isDark) 0.90f else 0.92f),
                            0.58f to appColors.background.copy(alpha = if (appColors.isDark) 0.56f else 0.62f),
                            1.0f to Color.Transparent
                        )
                    )
                )
        )

        // LAYER 2b: Pinned top header, top-right LiquidGlass Close button, and LiquidGlass filter pill
        CompositionLocalProvider(LocalAppBackdrop provides searchBackdrop) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .onSizeChanged { floatingHeaderHeightPx = it.height }
                    .statusBarsPadding()
                    .padding(top = topChromePadding)
                    .padding(top = 10.dp, start = 20.dp, end = 18.dp, bottom = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    ) {
                        Text(
                            text = "Search",
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 30.sp,
                            color = appColors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitleText,
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.5.sp,
                            color = if (isSearchingActive || isViewingMoodDetail) {
                                readableAccent
                            } else {
                                appColors.textSecondary
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    GlassCircleButton(
                        onClick = onDismissSearch,
                        size = 42.dp
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Search",
                            tint = if (useGlass) appColors.glassTextPrimary else appColors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (isSearchingActive) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LiquidGlassSegmentedPill(
                        options = SearchFilter.entries,
                        selectedOption = searchFilter,
                        labelProvider = { it.label },
                        onSelectOption = onSearchFilterChange,
                        modifier = Modifier.fillMaxWidth(),
                        appColors = appColors,
                        accentColor = accentColor,
                        equalWidthSegments = true,
                        segmentHeight = 38.dp
                    )
                } else if (selectedMoodCategory != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    MoodBackBreadcrumbBar(
                        category = selectedMoodCategory,
                        onBack = onClearMoodCategory,
                        useGlass = useGlass,
                        glassConfig = glassConfig
                    )
                }
            }
        }
    }
}

// =============================================================================
// PINNED HEADER CONTROLS (MOOD BACK BAR)
// =============================================================================

@Composable
private fun MoodBackBreadcrumbBar(
    category: YtmMoodCategory,
    onBack: () -> Unit,
    useGlass: Boolean,
    glassConfig: GlassEffectConfig
) {
    val appColors = LocalAppColors.current
    val haptic = LocalHapticFeedback.current
    val jelly = rememberJellyPressState(pressedScaleX = 0.96f, pressedScaleY = 0.94f)
    val pillShape = RoundedCornerShape(50)

    val containerModifier = if (useGlass) {
        Modifier.liquidGlass(
            config = glassConfig,
            shape = pillShape,
            highlightAlpha = 0.28f
        )
    } else {
        Modifier
            .clip(pillShape)
            .background(appColors.surface.copy(alpha = if (appColors.isDark) 0.88f else 0.94f))
            .border(1.dp, appColors.cardBorder, pillShape)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .jellyTapScale(jelly)
            .then(containerModifier)
            .clickable(
                interactionSource = jelly.interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onBack()
                }
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back to Moods & Genres",
            tint = if (useGlass) appColors.glassTextPrimary else appColors.textPrimary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "All Genres",
            fontFamily = GoogleSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = if (useGlass) appColors.glassTextSecondary else appColors.textSecondary
        )
        Text(
            text = " · ",
            fontFamily = PlusJakartaSans,
            fontSize = 13.sp,
            color = appColors.textSecondary.copy(alpha = 0.7f)
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(category.stripeColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = category.title,
            fontFamily = GoogleSans,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = if (useGlass) appColors.glassTextPrimary else appColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// =============================================================================
// EXPLORE MODE: RECENT SEARCHES + YOUTUBE MUSIC MOODS & GENRES
// =============================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExploreSearchContent(
    gridState: LazyGridState,
    topContentPadding: Dp,
    recentSearches: List<String>,
    moodsAndGenresFeed: YtmMoodsAndGenresFeed?,
    isLoadingMoods: Boolean,
    onRecentSearchClick: (String) -> Unit,
    onRemoveRecentSearch: (String) -> Unit,
    onClearRecentSearches: () -> Unit,
    onMoodCategoryClick: (YtmMoodCategory) -> Unit,
    onFallbackGenreClick: (GenreCategory) -> Unit
) {
    val appColors = LocalAppColors.current
    val accentColor = readableAccentColor(LocalAccentColor.current, appColors.isDark)
    val windowSizeClass = LocalAppWindowSizeClass.current
    val bottomChromePadding = LocalBottomChromePadding.current
    val sections = moodsAndGenresFeed?.sections.orEmpty()
    val fallbackGenres = remember { GenreCatalog.allGenres }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(
            minSize = windowSizeClass.adaptiveGridMinSize(152.dp, 174.dp, 188.dp)
        ),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = topContentPadding,
            bottom = bottomChromePadding
        ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (recentSearches.isNotEmpty()) {
            item(
                key = "recent_searches_block",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SearchSectionHeader(
                        title = "Recent Searches",
                        actionLabel = "Clear",
                        onActionClick = onClearRecentSearches
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        recentSearches.forEach { query ->
                            RecentSearchChip(
                                query = query,
                                onClick = { onRecentSearchClick(query) },
                                onRemove = { onRemoveRecentSearch(query) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        when {
            isLoadingMoods && sections.isEmpty() -> {
                item(
                    key = "moods_shimmer_skeleton",
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    MoodsShimmerSkeleton(
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            sections.isNotEmpty() -> {
                sections.forEachIndexed { sectionIndex, section ->
                    val sectionTitle = section.title.ifBlank {
                        if (sectionIndex == 0) "Moods & Genres" else "More Genres"
                    }
                    item(
                        key = "mood_section_header_${sectionIndex}_$sectionTitle",
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (sectionIndex > 0) {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            SearchSectionHeader(
                                title = sectionTitle,
                                countBadge = "${section.items.size}",
                                accentColor = accentColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }

                    lazyGridItemsIndexed(
                        items = section.items,
                        key = { itemIdx, category ->
                            "mood_card_${sectionIndex}_${itemIdx}_${category.id}"
                        },
                        contentType = { _, _ -> "mood_category_card" }
                    ) { _, category ->
                        YtmMoodCategoryCard(
                            category = category,
                            onClick = { onMoodCategoryClick(category) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item(
                        key = "mood_section_footer_$sectionIndex",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = "spacer"
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            else -> {
                item(
                    key = "fallback_genre_header",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "section_header"
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SearchSectionHeader(
                            title = "Explore by Genre",
                            countBadge = "${fallbackGenres.size}",
                            accentColor = accentColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                lazyGridItemsIndexed(
                    items = fallbackGenres,
                    key = { idx, genre -> "fallback_genre_${idx}_${genre.id}" },
                    contentType = { _, _ -> "fallback_genre_card" }
                ) { _, genre ->
                    FallbackGenreCard(
                        genre = genre,
                        onClick = { onFallbackGenreClick(genre) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchMoodEmptySelectionPane(
    topContentPadding: Dp,
    accentColor: Color
) {
    val appColors = LocalAppColors.current
    val bottomChromePadding = LocalBottomChromePadding.current
    val cardShape = RoundedCornerShape(24.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = topContentPadding,
                bottom = bottomChromePadding,
                start = 24.dp,
                end = 24.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .clip(cardShape)
                .background(appColors.surface.copy(alpha = if (appColors.isDark) 0.64f else 0.88f))
                .border(1.dp, appColors.cardBorder, cardShape)
                .padding(horizontal = 28.dp, vertical = 34.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.14f))
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Select a Mood or Genre",
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = appColors.textPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Pick any category on the left to browse curated playlists, featured albums, and songs right here.",
                fontFamily = PlusJakartaSans,
                fontSize = 13.5.sp,
                color = appColors.textSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun RecentSearchChip(
    query: String,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val appColors = LocalAppColors.current
    val jelly = rememberJellyPressState(pressedScaleX = 0.94f, pressedScaleY = 0.92f)
    val closeInteraction = remember { MutableInteractionSource() }

    Surface(
        shape = RoundedCornerShape(50),
        color = appColors.surface.copy(alpha = if (appColors.isDark) 0.72f else 0.90f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = appColors.cardBorder
        ),
        modifier = Modifier
            .jellyTapScale(jelly)
            .clickable(
                interactionSource = jelly.interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = null,
                tint = appColors.textSecondary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = query,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = appColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .pressWobble(closeInteraction)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = closeInteraction,
                        indication = null,
                        onClick = onRemove
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove $query",
                    tint = appColors.textSecondary,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
private fun YtmMoodCategoryCard(
    category: YtmMoodCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    CategoryStripeCard(
        title = category.title,
        stripePrimary = category.stripeColor,
        stripeSecondary = category.stripeColor,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
private fun FallbackGenreCard(
    genre: GenreCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    CategoryStripeCard(
        title = genre.name,
        stripePrimary = genre.primaryColor,
        stripeSecondary = genre.secondaryColor,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
private fun CategoryStripeCard(
    title: String,
    stripePrimary: Color,
    stripeSecondary: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val haptic = LocalHapticFeedback.current
    val jelly = rememberJellyPressState(pressedScaleX = 0.945f, pressedScaleY = 0.925f)
    val surfaceBg = appColors.surface.copy(alpha = if (appColors.isDark) 0.72f else 0.90f)

    Box(
        modifier = modifier
            .height(66.dp)
            .jellyTapScale(jelly)
            .shadow(
                elevation = if (appColors.isDark) 4.dp else 2.dp,
                shape = MoodCardShape,
                ambientColor = stripePrimary.copy(alpha = 0.25f),
                spotColor = Color.Black.copy(alpha = 0.28f)
            )
            .clip(MoodCardShape)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        stripePrimary.copy(alpha = if (appColors.isDark) 0.25f else 0.15f),
                        surfaceBg
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        stripePrimary.copy(alpha = if (appColors.isDark) 0.58f else 0.45f),
                        Color.White.copy(alpha = if (appColors.isDark) 0.11f else 0.55f)
                    )
                ),
                shape = MoodCardShape
            )
            .clickable(
                interactionSource = jelly.interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
    ) {
        // Soft ambient glow anchored near the category color stripe
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        stripePrimary.copy(alpha = if (appColors.isDark) 0.22f else 0.12f),
                        Color.Transparent
                    ),
                    center = Offset(12f, size.height * 0.5f),
                    radius = size.height * 1.1f
                ),
                center = Offset(12f, size.height * 0.5f),
                radius = size.height * 1.1f
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.5.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.verticalGradient(listOf(stripePrimary, stripeSecondary))
                    )
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                fontFamily = GoogleSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                color = appColors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = appColors.textSecondary.copy(alpha = 0.55f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

// =============================================================================
// MOOD / GENRE CATEGORY DETAIL VIEW
// =============================================================================

@Composable
private fun MoodCategoryDetailContent(
    gridState: LazyGridState,
    topContentPadding: Dp,
    category: YtmMoodCategory,
    moodDetailResult: YtmMoodCategoryDetail?,
    isLoading: Boolean,
    likedTrackIds: Set<String>,
    currentPlayingTrackId: String?,
    isPlaybackPlaying: Boolean,
    onMoodPlaylistClick: (RecommendedPlaylistItem) -> Unit,
    onAlbumClick: (AlbumItem) -> Unit,
    onTrackClick: (TrackItem) -> Unit,
    onPlayTrackFromList: ((List<TrackItem>, Int) -> Unit)?,
    onToggleFavorite: ((TrackItem) -> Unit)?,
    onTrackMoreClick: ((TrackItem) -> Unit)?,
    onSearchFallbackClick: () -> Unit,
    onClearMoodCategory: () -> Unit
) {
    val appColors = LocalAppColors.current
    val accentColor = readableAccentColor(LocalAccentColor.current, appColors.isDark)
    val windowSizeClass = LocalAppWindowSizeClass.current
    val bottomChromePadding = LocalBottomChromePadding.current
    val matchingDetail = moodDetailResult?.takeIf {
        it.browseId == category.browseId || it.categoryTitle == category.title
    }
    val showSkeleton = isLoading || (moodDetailResult != null && matchingDetail == null)

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(
            minSize = windowSizeClass.adaptiveGridMinSize(156.dp, 176.dp, 192.dp)
        ),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = topContentPadding,
            bottom = bottomChromePadding
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(
            key = "mood_hero_${category.id}",
            span = { GridItemSpan(maxLineSpan) }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                MoodHeroBanner(
                    category = category,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        if (showSkeleton) {
            item(
                key = "mood_detail_skeleton",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                MoodDetailShimmerSkeleton()
            }
            return@LazyVerticalGrid
        }

        val playlists = matchingDetail?.playlists.orEmpty()
        val albums = matchingDetail?.albums.orEmpty()
        val songs = matchingDetail?.songs.orEmpty()

        if (playlists.isEmpty() && albums.isEmpty() && songs.isEmpty()) {
            item(
                key = "mood_empty_state",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                MoodEmptyStateCard(
                    categoryTitle = category.title,
                    accentColor = accentColor,
                    onSearchInstead = onSearchFallbackClick,
                    onBackToGenres = onClearMoodCategory
                )
            }
            return@LazyVerticalGrid
        }

        if (playlists.isNotEmpty()) {
            item(
                key = "mood_playlists_header",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                SearchSectionHeader(
                    title = "Playlists",
                    countBadge = "${playlists.size}",
                    accentColor = accentColor
                )
            }

            if (albums.isEmpty() && songs.isEmpty()) {
                lazyGridItemsIndexed(
                    items = playlists,
                    key = { idx, playlist -> "mood_pl_grid_${idx}_${playlist.id}" }
                ) { _, playlist ->
                    MoodPlaylistCard(
                        playlist = playlist,
                        onClick = { onMoodPlaylistClick(playlist) },
                        modifier = Modifier.fillMaxWidth(),
                        isGridCard = true
                    )
                }
            } else {
                item(
                    key = "mood_playlists_row",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "horizontal_row"
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(
                                items = playlists,
                                key = { idx, item -> "mood_pl_${idx}_${item.id}" },
                                contentType = { _, _ -> "mood_playlist_card" }
                            ) { _, playlist ->
                                MoodPlaylistCard(
                                    playlist = playlist,
                                    onClick = { onMoodPlaylistClick(playlist) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }

        if (albums.isNotEmpty()) {
            item(
                key = "mood_albums_header",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "section_header"
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (playlists.isNotEmpty() && songs.isEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    SearchSectionHeader(
                        title = "Albums",
                        countBadge = "${albums.size}",
                        accentColor = accentColor
                    )
                }
            }

            if (playlists.isEmpty() && songs.isEmpty()) {
                lazyGridItemsIndexed(
                    items = albums,
                    key = { idx, album -> "mood_album_grid_${idx}_${album.id}" },
                    contentType = { _, _ -> "search_album_grid_card" }
                ) { _, album ->
                    SearchAlbumCard(
                        album = album,
                        onClick = { onAlbumClick(album) },
                        modifier = Modifier.fillMaxWidth(),
                        isGridCard = true
                    )
                }
            } else {
                item(
                    key = "mood_albums_row",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "horizontal_row"
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(
                                items = albums,
                                key = { idx, album -> "mood_album_${idx}_${album.id}" },
                                contentType = { _, _ -> "search_album_card" }
                            ) { _, album ->
                                SearchAlbumCard(
                                    album = album,
                                    onClick = { onAlbumClick(album) },
                                    isGridCard = false
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }

        if (songs.isNotEmpty()) {
            item(
                key = "mood_songs_header",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                SearchSectionHeader(
                    title = "Songs",
                    countBadge = "${songs.size}",
                    accentColor = accentColor
                )
            }

            lazyGridItemsIndexed(
                items = songs,
                key = { idx, track -> "mood_song_${idx}_${track.id}" },
                span = { _, _ -> GridItemSpan(maxLineSpan) }
            ) { index, track ->
                val isLiked = track.id in likedTrackIds || track.isLiked
                val isCurrentTrack = currentPlayingTrackId != null && track.id == currentPlayingTrackId
                SearchTrackItemRow(
                    track = track,
                    isCurrentTrack = isCurrentTrack,
                    isPlaybackPlaying = isPlaybackPlaying,
                    isLiked = isLiked,
                    onClick = {
                        onPlayTrackFromList?.invoke(songs, index) ?: onTrackClick(track)
                    },
                    onToggleFavorite = onToggleFavorite?.let { callback -> { callback(track) } },
                    onMoreClick = onTrackMoreClick?.let { callback -> { callback(track) } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun MoodHeroBanner(
    category: YtmMoodCategory,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val stripe = category.stripeColor
    val surfaceBg = appColors.surface.copy(alpha = if (appColors.isDark) 0.78f else 0.92f)
    val bannerShape = RoundedCornerShape(22.dp)

    Box(
        modifier = modifier
            .clip(bannerShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        stripe.copy(alpha = if (appColors.isDark) 0.34f else 0.22f),
                        surfaceBg
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        stripe.copy(alpha = 0.60f),
                        Color.White.copy(alpha = if (appColors.isDark) 0.12f else 0.55f)
                    )
                ),
                shape = bannerShape
            )
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(50))
                    .background(stripe)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.title,
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = appColors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Playlists, albums & tracks curated on YouTube Music",
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.5.sp,
                    color = appColors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MoodEmptyStateCard(
    categoryTitle: String,
    accentColor: Color,
    onSearchInstead: () -> Unit,
    onBackToGenres: () -> Unit
) {
    val appColors = LocalAppColors.current
    val cardShape = RoundedCornerShape(22.dp)

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(cardShape)
            .background(appColors.surface.copy(alpha = if (appColors.isDark) 0.72f else 0.90f))
            .border(1.dp, appColors.cardBorder, cardShape)
            .padding(horizontal = 20.dp, vertical = 28.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Nothing loaded for $categoryTitle",
            fontFamily = GoogleSans,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = appColors.textPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "YouTube Music didn't return items for this category right now. Try searching directly or return to genres.",
            fontFamily = PlusJakartaSans,
            fontSize = 13.sp,
            color = appColors.textSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionPillButton(
                label = "Search \"$categoryTitle\"",
                containerColor = accentColor,
                contentColor = onAccentColor(accentColor),
                onClick = onSearchInstead
            )
            ActionPillButton(
                label = "Back to Genres",
                containerColor = appColors.surfaceVariant.copy(alpha = 0.85f),
                contentColor = appColors.textPrimary,
                onClick = onBackToGenres
            )
        }
    }
    }
}

@Composable
private fun ActionPillButton(
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    val jelly = rememberJellyPressState(pressedScaleX = 0.95f, pressedScaleY = 0.93f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .jellyTapScale(jelly)
            .clip(RoundedCornerShape(50))
            .background(containerColor)
            .clickable(
                interactionSource = jelly.interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            fontFamily = GoogleSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// =============================================================================
// SEARCH RESULTS VIEWS (ALL / SONGS / ARTISTS / ALBUMS)
// =============================================================================

@Composable
private fun SearchResultsListContent(
    gridState: LazyGridState,
    topContentPadding: Dp,
    searchQuery: String,
    searchFilter: SearchFilter,
    searchResults: SearchResultData,
    isSearching: Boolean,
    likedTrackIds: Set<String>,
    currentPlayingTrackId: String?,
    isPlaybackPlaying: Boolean,
    onSearchFilterChange: (SearchFilter) -> Unit,
    onTrackClick: (TrackItem) -> Unit,
    onAlbumClick: (AlbumItem) -> Unit,
    onArtistClick: (ArtistItem) -> Unit,
    onToggleFavorite: ((TrackItem) -> Unit)?,
    onTrackMoreClick: ((TrackItem) -> Unit)?
) {
    val appColors = LocalAppColors.current
    val accentColor = readableAccentColor(LocalAccentColor.current, appColors.isDark)
    val windowSizeClass = LocalAppWindowSizeClass.current
    val bottomChromePadding = LocalBottomChromePadding.current

    val hasAnyResults = searchResults.songs.isNotEmpty() ||
        searchResults.albums.isNotEmpty() ||
        searchResults.artists.isNotEmpty()

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(
            minSize = windowSizeClass.adaptiveGridMinSize(150.dp, 170.dp, 188.dp)
        ),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = topContentPadding,
            bottom = bottomChromePadding
        ),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isSearching && !hasAnyResults) {
            item(
                key = "search_results_shimmer",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                SearchResultsShimmerSkeleton()
            }
            return@LazyVerticalGrid
        }

        if (!hasAnyResults && !isSearching) {
            item(
                key = "search_empty_results",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                EmptySearchResultsState(query = searchQuery)
            }
            return@LazyVerticalGrid
        }

        if (isSearching && hasAnyResults) {
            item(
                key = "search_updating_bar",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                ShimmerScope {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        ShimmerLine(fraction = 1f, height = 3.dp)
                    }
                }
            }
        }

        when (searchFilter) {
            SearchFilter.ALL -> {
                if (searchResults.songs.isNotEmpty()) {
                    val previewSongs = searchResults.songs.take(5)
                    item(
                        key = "all_songs_header",
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        SearchSectionHeader(
                            title = "Songs",
                            countBadge = "${searchResults.songs.size}",
                            actionLabel = if (searchResults.songs.size > 5) "See all" else null,
                            onActionClick = { onSearchFilterChange(SearchFilter.SONGS) },
                            accentColor = accentColor
                        )
                    }

                    searchTrackRows(
                        tracks = previewSongs,
                        keyPrefix = "all_song",
                        likedTrackIds = likedTrackIds,
                        currentPlayingTrackId = currentPlayingTrackId,
                        isPlaybackPlaying = isPlaybackPlaying,
                        onTrackClick = onTrackClick,
                        onToggleFavorite = onToggleFavorite,
                        onTrackMoreClick = onTrackMoreClick
                    )

                    item(
                        key = "all_songs_spacer",
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }

                if (searchResults.artists.isNotEmpty()) {
                    item(
                        key = "all_artists_header",
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        SearchSectionHeader(
                            title = "Artists",
                            countBadge = "${searchResults.artists.size}",
                            actionLabel = if (searchResults.artists.size > 4) "See all" else null,
                            onActionClick = { onSearchFilterChange(SearchFilter.ARTISTS) },
                            accentColor = accentColor
                        )
                    }

                    item(
                        key = "all_artists_row",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = "horizontal_row"
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                itemsIndexed(
                                    items = searchResults.artists,
                                    key = { idx, artist -> "all_artist_${idx}_${artist.id}" },
                                    contentType = { _, _ -> "search_artist_card" }
                                ) { _, artist ->
                                    SearchArtistCard(
                                        artist = artist,
                                        onClick = { onArtistClick(artist) },
                                        avatarSize = 135.dp,
                                        isGridCard = false
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }

                if (searchResults.albums.isNotEmpty()) {
                    item(
                        key = "all_albums_header",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = "section_header"
                    ) {
                        SearchSectionHeader(
                            title = "Albums",
                            countBadge = "${searchResults.albums.size}",
                            actionLabel = if (searchResults.albums.size > 4) "See all" else null,
                            onActionClick = { onSearchFilterChange(SearchFilter.ALBUMS) },
                            accentColor = accentColor
                        )
                    }

                    item(
                        key = "all_albums_row",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = "horizontal_row"
                    ) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(
                                items = searchResults.albums,
                                key = { idx, album -> "all_album_${idx}_${album.id}" },
                                contentType = { _, _ -> "search_album_card" }
                            ) { _, album ->
                                SearchAlbumCard(
                                    album = album,
                                    onClick = { onAlbumClick(album) },
                                    isGridCard = false
                                )
                            }
                        }
                    }
                }
            }

            SearchFilter.SONGS -> {
                if (searchResults.songs.isEmpty()) {
                    item(
                        key = "songs_empty",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = "empty_state"
                    ) {
                        EmptySearchResultsState(query = searchQuery)
                    }
                } else {
                    searchTrackRows(
                        tracks = searchResults.songs,
                        keyPrefix = "filter_song",
                        likedTrackIds = likedTrackIds,
                        currentPlayingTrackId = currentPlayingTrackId,
                        isPlaybackPlaying = isPlaybackPlaying,
                        onTrackClick = onTrackClick,
                        onToggleFavorite = onToggleFavorite,
                        onTrackMoreClick = onTrackMoreClick
                    )
                }
            }

            SearchFilter.ARTISTS -> {
                if (searchResults.artists.isEmpty()) {
                    item(
                        key = "artists_empty",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = "empty_state"
                    ) {
                        EmptySearchResultsState(query = searchQuery)
                    }
                } else {
                    lazyGridItemsIndexed(
                        items = searchResults.artists,
                        key = { idx, artist -> "artist_grid_${idx}_${artist.id}" },
                        contentType = { _, _ -> "search_artist_grid_card" }
                    ) { _, artist ->
                        SearchArtistCard(
                            artist = artist,
                            onClick = { onArtistClick(artist) },
                            avatarSize = 132.dp,
                            modifier = Modifier.fillMaxWidth(),
                            isGridCard = true
                        )
                    }
                }
            }

            SearchFilter.ALBUMS -> {
                if (searchResults.albums.isEmpty()) {
                    item(
                        key = "albums_empty",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = "empty_state"
                    ) {
                        EmptySearchResultsState(query = searchQuery)
                    }
                } else {
                    lazyGridItemsIndexed(
                        items = searchResults.albums,
                        key = { idx, album -> "album_grid_${idx}_${album.id}" },
                        contentType = { _, _ -> "search_album_grid_card" }
                    ) { _, album ->
                        SearchAlbumCard(
                            album = album,
                            onClick = { onAlbumClick(album) },
                            modifier = Modifier.fillMaxWidth(),
                            isGridCard = true
                        )
                    }
                }
            }
        }
    }
}

private fun LazyGridScope.searchTrackRows(
    tracks: List<TrackItem>,
    keyPrefix: String,
    likedTrackIds: Set<String>,
    currentPlayingTrackId: String?,
    isPlaybackPlaying: Boolean,
    onTrackClick: (TrackItem) -> Unit,
    onToggleFavorite: ((TrackItem) -> Unit)?,
    onTrackMoreClick: ((TrackItem) -> Unit)?
) {
    lazyGridItemsIndexed(
        items = tracks,
        key = { idx, track -> "${keyPrefix}_${idx}_${track.id}" },
        span = { _, _ -> GridItemSpan(maxLineSpan) },
        contentType = { _, _ -> "search_track_row" }
    ) { _, track ->
        val isLiked = track.id in likedTrackIds || track.isLiked
        val isCurrentTrack = currentPlayingTrackId != null && track.id == currentPlayingTrackId
        SearchTrackItemRow(
            track = track,
            isCurrentTrack = isCurrentTrack,
            isPlaybackPlaying = isPlaybackPlaying,
            isLiked = isLiked,
            onClick = { onTrackClick(track) },
            onToggleFavorite = onToggleFavorite?.let { callback -> { callback(track) } },
            onMoreClick = onTrackMoreClick?.let { callback -> { callback(track) } },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun EmptySearchResultsState(query: String) {
    val appColors = LocalAppColors.current
    val accentColor = readableAccentColor(LocalAccentColor.current, appColors.isDark)
    val cardShape = RoundedCornerShape(22.dp)

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .padding(vertical = 24.dp)
                .clip(cardShape)
                .background(appColors.surface.copy(alpha = if (appColors.isDark) 0.68f else 0.88f))
                .border(1.dp, appColors.cardBorder, cardShape)
                .padding(horizontal = 24.dp, vertical = 38.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.14f))
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "No results for \"$query\"",
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = appColors.textPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Try searching another song, artist, album, or explore by genre",
                fontFamily = PlusJakartaSans,
                fontSize = 13.5.sp,
                color = appColors.textSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

// =============================================================================
// CARDS & TRACK ROW (MATCHING ALBUMSCREEN & HOME CARDS)
// =============================================================================

@Composable
private fun SearchTrackItemRow(
    track: TrackItem,
    isCurrentTrack: Boolean,
    isPlaybackPlaying: Boolean,
    isLiked: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: (() -> Unit)?,
    onMoreClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val accentColor = LocalAccentColor.current
    val readableAccent = readableAccentColor(accentColor, appColors.isDark)
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "searchTrackScale"
    )

    val rowBackgroundColor = when {
        isCurrentTrack -> readableAccent.copy(alpha = if (appColors.isDark) 0.14f else 0.10f)
        isPressed -> appColors.surfaceVariant.copy(alpha = if (appColors.isDark) 0.50f else 0.75f)
        else -> Color.Transparent
    }

    val subtitle = remember(track.artist, track.album, track.title) {
        buildString {
            if (track.artist.isNotBlank()) append(track.artist)
            if (track.album.isNotBlank() && track.album != track.title) {
                if (isNotEmpty()) append(" · ")
                append(track.album)
            }
        }.ifBlank { "Unknown Artist" }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(14.dp))
            .background(rowBackgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        // Cover thumbnail (with active equalizer overlay when currently playing)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(SearchThumbShape)
                .background(if (appColors.isDark) Color(0xFF222632) else appColors.surfaceVariant)
        ) {
            SearchAsyncArtwork(
                imageUrl = track.artworkUri,
                fallbackResId = track.artworkResId,
                contentDescription = track.title,
                modifier = Modifier.fillMaxSize(),
                placeholder = {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(appColors.surfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = appColors.textSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            )
            if (isCurrentTrack) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.50f))
                ) {
                    SearchEqualizerBars(
                        color = accentColor,
                        isPlaying = isPlaybackPlaying
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title + ExplicitBadge ("E") + Artist/Album subtitle
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = track.title,
                    fontFamily = GoogleSans,
                    fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = if (isCurrentTrack) readableAccent else appColors.textPrimary,
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
                text = subtitle,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Normal,
                fontSize = 12.5.sp,
                color = appColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        if (track.durationMs > 0L) {
            Text(
                text = track.formattedDuration,
                fontFamily = PlusJakartaSans,
                fontSize = 12.sp,
                color = appColors.textSecondary,
                modifier = Modifier.padding(end = 4.dp)
            )
        }

        // Like Button (matching AlbumTrackRow heart pop animation + #FF4081 tint)
        if (onToggleFavorite != null) {
            var justLiked by remember { mutableStateOf(false) }
            val heartPopScale by animateFloatAsState(
                targetValue = if (justLiked) 1.28f else 1f,
                animationSpec = spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium),
                finishedListener = { justLiked = false },
                label = "searchHeartPop"
            )
            val favInteraction = remember { MutableInteractionSource() }
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (!isLiked) justLiked = true
                    onToggleFavorite()
                },
                interactionSource = favInteraction,
                modifier = Modifier
                    .size(32.dp)
                    .pressWobble(favInteraction)
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isLiked) "Unlike" else "Like",
                    tint = if (isLiked) Color(0xFFFF4081) else appColors.textSecondary.copy(alpha = 0.75f),
                    modifier = Modifier
                        .size(17.dp)
                        .graphicsLayer {
                            scaleX = heartPopScale
                            scaleY = heartPopScale
                        }
                )
            }
        }

        // "..." More Menu Button (NO Play button)
        if (onMoreClick != null) {
            val moreInteraction = remember { MutableInteractionSource() }
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onMoreClick()
                },
                interactionSource = moreInteraction,
                modifier = Modifier
                    .size(32.dp)
                    .pressWobble(moreInteraction)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = appColors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun SearchAlbumCard(
    album: AlbumItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isGridCard: Boolean = false
) {
    val subtitle = remember(album.artist, album.year) {
        if (album.year > 0) "${album.artist} · ${album.year}" else album.artist
    }
    SearchSquareMediaCard(
        title = album.title,
        subtitle = subtitle,
        imageUrl = album.coverUri,
        fallbackResId = album.coverResId,
        cardWidth = 132.dp,
        isGridCard = isGridCard,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
private fun SearchArtistCard(
    artist: ArtistItem,
    onClick: () -> Unit,
    avatarSize: Dp,
    modifier: Modifier = Modifier,
    isGridCard: Boolean = false
) {
    val appColors = LocalAppColors.current
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val haptic = LocalHapticFeedback.current
    val jelly = rememberJellyPressState(pressedScaleX = 0.92f, pressedScaleY = 0.89f)

    var avatarUriState by remember(artist.name, artist.avatarUri) {
        mutableStateOf(
            artist.avatarUri?.takeIf { it.isNotBlank() }
                ?: YouTubeMusicService.getInstance().getArtistAvatarFromCache(artist.name, appContext)
        )
    }

    LaunchedEffect(artist.name, artist.avatarUri) {
        if (avatarUriState.isNullOrBlank()) {
            val ytm = YouTubeMusicService.getInstance()
            val resolved = ytm.getArtistAvatarFromCache(artist.name, appContext)
                ?: ytm.getArtistAvatarUri(artist.name, appContext)
            if (!resolved.isNullOrBlank()) {
                avatarUriState = resolved
            }
        }
    }

    val cardModifier = if (isGridCard) {
        modifier
    } else {
        modifier.width(avatarSize)
    }

    val ringColors = if (appColors.isDark) {
        listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.06f))
    } else {
        listOf(Color.White.copy(alpha = 0.72f), appColors.cardBorder)
    }

    val secondaryLabel = remember(artist.subscribers, artist.genre) {
        artist.subscribers.takeIf { it.isNotBlank() && it != "3,14 M" }
            ?: artist.genre.takeIf { it.isNotBlank() && it != "Pop" }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = cardModifier
            .jellyTapScale(jelly)
            .clickable(
                interactionSource = jelly.interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(avatarSize)
                .shadow(
                    elevation = 6.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.40f),
                    spotColor = Color.Black.copy(alpha = 0.40f)
                )
                .clip(CircleShape)
                .background(if (appColors.isDark) Color(0xFF222632) else appColors.surfaceVariant)
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(ringColors),
                    shape = CircleShape
                )
        ) {
            SearchAsyncArtwork(
                imageUrl = avatarUriState,
                fallbackResId = artist.avatarResId,
                contentDescription = artist.name,
                modifier = Modifier.fillMaxSize(),
                placeholder = {
                    ArtistInitialsPlaceholder(name = artist.name)
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = artist.name,
            fontFamily = GoogleSans,
            fontWeight = FontWeight.Medium,
            fontSize = 13.5.sp,
            lineHeight = 16.sp,
            color = appColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        if (!secondaryLabel.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = secondaryLabel,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                color = appColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun MoodPlaylistCard(
    playlist: RecommendedPlaylistItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isGridCard: Boolean = false
) {
    val subtitleText = playlist.author?.takeIf { it.isNotBlank() }
        ?: playlist.subtitle.takeIf { it.isNotBlank() }
        ?: "Playlist"
    SearchSquareMediaCard(
        title = playlist.title,
        subtitle = subtitleText,
        imageUrl = playlist.artworkUri,
        fallbackResId = 0,
        cardWidth = 136.dp,
        badgeText = playlist.trackCountText?.takeIf { it.isNotBlank() },
        isGridCard = isGridCard,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
private fun SearchSquareMediaCard(
    title: String,
    subtitle: String,
    imageUrl: String?,
    fallbackResId: Int?,
    cardWidth: Dp,
    badgeText: String? = null,
    isGridCard: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val haptic = LocalHapticFeedback.current
    val jelly = rememberJellyPressState(pressedScaleX = 0.92f, pressedScaleY = 0.89f)

    val containerModifier = if (isGridCard) modifier else modifier.width(cardWidth)
    val artworkModifier = if (isGridCard) {
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    } else {
        Modifier.size(cardWidth)
    }

    val borderColors = if (appColors.isDark) {
        listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.05f))
    } else {
        listOf(Color.White.copy(alpha = 0.65f), appColors.cardBorder)
    }

    Column(
        modifier = containerModifier
            .jellyTapScale(jelly)
            .clickable(
                interactionSource = jelly.interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
    ) {
        Box(
            modifier = artworkModifier
                .shadow(
                    elevation = 6.dp,
                    shape = SearchAlbumCardShape,
                    ambientColor = Color.Black.copy(alpha = 0.45f),
                    spotColor = Color.Black.copy(alpha = 0.45f)
                )
                .clip(SearchAlbumCardShape)
                .background(if (appColors.isDark) Color(0xFF222632) else appColors.surfaceVariant)
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(borderColors),
                    shape = SearchAlbumCardShape
                )
        ) {
            SearchAsyncArtwork(
                imageUrl = imageUrl,
                fallbackResId = fallbackResId,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                placeholder = {
                    AlbumGradientPlaceholder(title = title)
                }
            )

            if (!badgeText.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.62f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        color = Color.White,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = title,
            fontFamily = GoogleSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = appColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = subtitle,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.Medium,
            fontSize = 11.5.sp,
            color = appColors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// =============================================================================
// SHARED ARTWORK, EQUALIZER, SECTION HEADER & SHIMMER PLACEHOLDERS
// =============================================================================

@Composable
private fun SearchAsyncArtwork(
    imageUrl: String?,
    fallbackResId: Int?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    placeholder: @Composable () -> Unit
) {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val imageLoader = remember(appContext) { MediaNetworkClients.getImageLoader(appContext) }
    val cleanUrl = imageUrl?.takeIf { it.isNotBlank() }
    var loadFailed by remember(cleanUrl) { mutableStateOf(false) }

    Box(modifier = modifier) {
        // Keep clean placeholder/initials visible underneath while Coil fades in so artists & albums
        // never flash blank/broken frames during network loading.
        when {
            cleanUrl != null && !loadFailed -> {
                placeholder()
                val request = remember(cleanUrl, appContext) {
                    ImageRequest.Builder(appContext)
                        .data(cleanUrl)
                        .crossfade(220)
                        .build()
                }
                AsyncImage(
                    model = request,
                    imageLoader = imageLoader,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    onError = { loadFailed = true },
                    onSuccess = { loadFailed = false },
                    modifier = Modifier.fillMaxSize()
                )
            }

            fallbackResId != null && fallbackResId != 0 -> {
                Image(
                    painter = painterResource(id = fallbackResId),
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            else -> {
                placeholder()
            }
        }
    }
}

@Composable
private fun ArtistInitialsPlaceholder(name: String) {
    val palette = remember(name) { expressiveGradientColors(name) }
    val initials = remember(name) { computeArtistInitials(name) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(palette))
    ) {
        Text(
            text = initials,
            fontFamily = GoogleSans,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            color = Color.White.copy(alpha = 0.94f),
            maxLines = 1
        )
    }
}

@Composable
private fun AlbumGradientPlaceholder(title: String) {
    val palette = remember(title) { expressiveGradientColors(title) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(palette))
    ) {
        Icon(
            imageVector = Icons.Rounded.Album,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(36.dp)
        )
    }
}

private val WhitespaceRegex = Regex("\\s+")

private fun computeArtistInitials(name: String): String {
    val parts = name.trim().split(WhitespaceRegex).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> "${parts.first().first().uppercaseChar()}${parts.last().first().uppercaseChar()}"
    }
}

@Composable
private fun SearchSectionHeader(
    title: String,
    countBadge: String? = null,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    accentColor: Color = readableAccentColor(LocalAccentColor.current, LocalAppColors.current.isDark)
) {
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Text(
                text = title,
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = appColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!countBadge.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(accentColor.copy(alpha = if (appColors.isDark) 0.16f else 0.12f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = countBadge,
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp,
                        color = accentColor
                    )
                }
            }
        }

        if (!actionLabel.isNullOrBlank() && onActionClick != null) {
            val actionInteraction = remember { MutableInteractionSource() }
            Text(
                text = actionLabel,
                fontFamily = GoogleSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = accentColor,
                modifier = Modifier
                    .pressWobble(actionInteraction)
                    .clip(RoundedCornerShape(50))
                    .clickable(
                        interactionSource = actionInteraction,
                        indication = null,
                        onClick = onActionClick
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun SearchEqualizerBars(
    color: Color,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "searchEq")
    val bar1 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(320, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(380, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq3"
    )

    Row(
        modifier = modifier.height(14.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val h1 = if (isPlaying) bar1 else 0.4f
        val h2 = if (isPlaying) bar2 else 0.7f
        val h3 = if (isPlaying) bar3 else 0.5f
        Box(
            modifier = Modifier
                .width(2.5.dp)
                .fillMaxHeight(h1)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
        Box(
            modifier = Modifier
                .width(2.5.dp)
                .fillMaxHeight(h2)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
        Box(
            modifier = Modifier
                .width(2.5.dp)
                .fillMaxHeight(h3)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
    }
}

@Composable
private fun MoodsShimmerSkeleton(modifier: Modifier = Modifier) {
    ShimmerScope {
        Column(modifier = modifier) {
            repeat(2) { sectionIdx ->
                if (sectionIdx > 0) Spacer(modifier = Modifier.height(22.dp))
                ShimmerLine(fraction = if (sectionIdx == 0) 0.44f else 0.32f, height = 20.dp)
                Spacer(modifier = Modifier.height(14.dp))
                repeat(3) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ShimmerBox(
                            modifier = Modifier
                                .weight(1f)
                                .height(66.dp),
                            shape = MoodCardShape
                        )
                        ShimmerBox(
                            modifier = Modifier
                                .weight(1f)
                                .height(66.dp),
                            shape = MoodCardShape
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodDetailShimmerSkeleton() {
    ShimmerScope {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                ShimmerLine(fraction = 0.34f, height = 18.dp)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repeat(3) {
                    Column(modifier = Modifier.width(132.dp)) {
                        ShimmerBox(
                            modifier = Modifier.size(132.dp),
                            shape = SearchAlbumCardShape
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ShimmerLine(fraction = 0.85f, height = 13.dp)
                        Spacer(modifier = Modifier.height(4.dp))
                        ShimmerLine(fraction = 0.55f, height = 11.dp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                ShimmerLine(fraction = 0.28f, height = 18.dp)
            }
            Spacer(modifier = Modifier.height(10.dp))
            TrackRowsShimmer(count = 5)
        }
    }
}

@Composable
private fun SearchResultsShimmerSkeleton() {
    ShimmerScope {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                ShimmerLine(fraction = 0.24f, height = 18.dp)
            }
            Spacer(modifier = Modifier.height(10.dp))
            TrackRowsShimmer(count = 4)
            Spacer(modifier = Modifier.height(22.dp))
            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                ShimmerLine(fraction = 0.26f, height = 18.dp)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                repeat(3) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(120.dp)
                    ) {
                        ShimmerBox(
                            modifier = Modifier.size(120.dp),
                            shape = CircleShape
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ShimmerLine(fraction = 0.72f, height = 13.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRowsShimmer(count: Int) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        repeat(count) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 7.dp)
            ) {
                ShimmerBox(
                    modifier = Modifier.size(48.dp),
                    shape = SearchThumbShape
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    ShimmerLine(fraction = 0.70f, height = 14.dp)
                    Spacer(modifier = Modifier.height(6.dp))
                    ShimmerLine(fraction = 0.45f, height = 11.dp)
                }
            }
        }
    }
}

@Composable
private fun SearchPreviewContent(widthDp: Int, heightDp: Int) {
    AdaptivePreviewSurface(widthDp = widthDp, heightDp = heightDp) {
        SearchScreen(
            searchQuery = "",
            searchFilter = SearchFilter.ALL,
            searchResults = AdaptivePreviewFixtures.searchResults,
            isSearching = false,
            recentSearches = listOf("Lady Gaga", "Electropop", "Mae Muller"),
            onSearchQueryChange = {},
            onSearchFilterChange = {},
            onRecentSearchClick = {},
            onRemoveRecentSearch = {},
            onClearRecentSearches = {},
            onGenreClick = {},
            onTrackClick = {},
            onAlbumClick = {},
            onArtistClick = {},
            onDismissSearch = {},
            moodsAndGenresFeed = AdaptivePreviewFixtures.moodsFeed,
            selectedMoodCategory = if (widthDp >= 840) {
                AdaptivePreviewFixtures.moodsFeed.sections.first().items.first()
            } else {
                null
            },
            moodDetailResult = AdaptivePreviewFixtures.moodDetail
        )
    }
}

@Preview(name = "Phone (360x800)", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
private fun SearchPhonePreview() = SearchPreviewContent(360, 800)

@Preview(name = "Tablet Portrait (800x1280)", widthDp = 800, heightDp = 1280, showBackground = true)
@Composable
private fun SearchTabletPortraitPreview() = SearchPreviewContent(800, 1280)

@Preview(name = "Tablet Landscape (1280x800)", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
private fun SearchTabletLandscapePreview() = SearchPreviewContent(1280, 800)
