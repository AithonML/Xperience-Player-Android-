package com.xperience.player.ui.library

import android.net.Uri
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xperience.player.domain.model.AlbumItem
import com.xperience.player.domain.model.ArtistItem
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.ui.adaptive.AdaptivePreviewFixtures
import com.xperience.player.ui.adaptive.AdaptivePreviewSurface
import com.xperience.player.ui.adaptive.LocalAppWindowSizeClass
import com.xperience.player.ui.adaptive.LocalBottomChromePadding
import com.xperience.player.ui.adaptive.LocalTopChromePadding
import com.xperience.player.ui.adaptive.isMediumOrExpandedWidth
import com.xperience.player.ui.adaptive.usesTopDesktopChrome
import com.xperience.player.ui.component.GlassComponent
import com.xperience.player.ui.component.LiquidGlassSegmentedPill
import com.xperience.player.ui.component.LocalAppBackdrop
import com.xperience.player.ui.component.LocalGlassEffectConfig
import com.xperience.player.ui.component.backdrop.backdrops.layerBackdrop
import com.xperience.player.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.xperience.player.ui.component.isGlassAllowed
import com.xperience.player.ui.component.liquidGlass
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.components.HomeAuroraBackground
import com.xperience.player.ui.components.bentoScrollTransform
import com.xperience.player.ui.components.jellyTapScale
import com.xperience.player.ui.components.onAccentColor
import com.xperience.player.ui.components.readableAccentColor
import com.xperience.player.ui.components.rememberJellyPressState
import com.xperience.player.ui.home.MediaSourceType
import com.xperience.player.ui.home.SearchCategoryFilter
import com.xperience.player.ui.theme.AppThemeColors
import com.xperience.player.ui.theme.AuroraColors
import com.xperience.player.ui.theme.DEFAULT_AURORA_COLORS
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.PlusJakartaSans
import kotlin.coroutines.cancellation.CancellationException

private val SegmentPillShape = ContinuousRoundedRectangle(percent = 50)
private val UtilityCardShape = ContinuousRoundedRectangle(size = 22.dp)

@Composable
fun LibraryScreen(
    headerTitle: String,
    headerSubtitle: String,
    selectedCategory: SearchCategoryFilter,
    selectedSourceType: MediaSourceType,
    onSelectCategory: (SearchCategoryFilter) -> Unit,
    onSelectSourceType: (MediaSourceType) -> Unit,
    artists: List<ArtistItem>,
    albums: List<AlbumItem>,
    songs: List<TrackItem>,
    isScanning: Boolean,
    onTrackClick: (TrackItem) -> Unit,
    onAlbumClick: (AlbumItem) -> Unit,
    onArtistClick: (ArtistItem) -> Unit,
    onScanMediaStore: () -> Unit,
    onAddFolder: (Uri) -> Unit,
    onSongMoreClick: (TrackItem) -> Unit,
    likedTrackIds: Set<String> = emptySet(),
    onToggleFavorite: ((TrackItem) -> Unit)? = null,
    modifier: Modifier = Modifier,
    recentlyAddedTracks: List<TrackItem> = emptyList(),
    currentPlayingTrackId: String? = null,
    isPlaybackPlaying: Boolean = false,
    auroraColors: AuroraColors = DEFAULT_AURORA_COLORS,
    activeLibrarySubView: LibrarySubView? = null,
    onOpenSubView: ((LibrarySubView?) -> Unit)? = null,
    applyStatusBarsPadding: Boolean = false,
    onPlayTrackFromList: ((List<TrackItem>, Int) -> Unit)? = null
) {
    val appColors = LocalAppColors.current
    val accentColor = LocalAccentColor.current
    val density = LocalDensity.current

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { onAddFolder(it) }
    }

    // Fallback recently-added list if caller hasn't supplied a pre-merged stream yet
    val effectiveRecentlyAdded = remember(recentlyAddedTracks, songs) {
        if (recentlyAddedTracks.isNotEmpty()) {
            recentlyAddedTracks
        } else {
            val dated = songs.filter { it.dateAddedMs > 0L }.sortedByDescending { it.dateAddedMs }
            if (dated.isNotEmpty()) dated else songs
        }
    }

    // Sub-view state management (externalized for HomeScreen PredictiveBack or local fallback)
    var localSubView by remember { mutableStateOf<LibrarySubView?>(null) }
    var previousExternalSubView by remember { mutableStateOf(activeLibrarySubView) }

    LaunchedEffect(activeLibrarySubView) {
        if (onOpenSubView != null) {
            if (previousExternalSubView != null && activeLibrarySubView == null && selectedCategory != SearchCategoryFilter.ALL) {
                onSelectCategory(SearchCategoryFilter.ALL)
            }
            previousExternalSubView = activeLibrarySubView
        }
    }

    val currentSubView: LibrarySubView? = if (onOpenSubView != null) {
        activeLibrarySubView
    } else {
        localSubView
    }

    fun navigateToSubView(target: LibrarySubView?) {
        when (target) {
            LibrarySubView.ARTISTS -> if (selectedCategory != SearchCategoryFilter.ARTISTS) {
                onSelectCategory(SearchCategoryFilter.ARTISTS)
            }
            LibrarySubView.ALBUMS -> if (selectedCategory != SearchCategoryFilter.ALBUMS) {
                onSelectCategory(SearchCategoryFilter.ALBUMS)
            }
            LibrarySubView.SONGS -> if (selectedCategory != SearchCategoryFilter.SONGS) {
                onSelectCategory(SearchCategoryFilter.SONGS)
            }
            null -> if (selectedCategory != SearchCategoryFilter.ALL) {
                onSelectCategory(SearchCategoryFilter.ALL)
            }
            LibrarySubView.RECENTLY_ADDED -> Unit
        }
        if (onOpenSubView != null) {
            onOpenSubView(target)
        } else {
            localSubView = target
        }
    }

    val isRecentlyAddedOpen = currentSubView == LibrarySubView.RECENTLY_ADDED

    // Effective directory driven seamlessly by either active sub-view or CategoryFilterPill
    val activeDirectoryView: LibrarySubView? = when {
        currentSubView == LibrarySubView.ARTISTS -> LibrarySubView.ARTISTS
        currentSubView == LibrarySubView.ALBUMS -> LibrarySubView.ALBUMS
        currentSubView == LibrarySubView.SONGS -> LibrarySubView.SONGS
        else -> when (selectedCategory) {
            SearchCategoryFilter.ALL -> null
            SearchCategoryFilter.ARTISTS -> LibrarySubView.ARTISTS
            SearchCategoryFilter.ALBUMS -> LibrarySubView.ALBUMS
            SearchCategoryFilter.SONGS -> LibrarySubView.SONGS
        }
    }

    // Handle system Predictive Back locally when parent hasn't provided onOpenSubView
    if (onOpenSubView == null) {
        PredictiveBackHandler(
            enabled = localSubView != null || selectedCategory != SearchCategoryFilter.ALL
        ) { progressFlow ->
            try {
                progressFlow.collect { }
                when {
                    localSubView == LibrarySubView.RECENTLY_ADDED -> localSubView = null
                    localSubView != null || selectedCategory != SearchCategoryFilter.ALL -> navigateToSubView(null)
                }
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    // Isolated backdrop recorded around Aurora + scrollable content so floating LiquidGlass
    // slicers sample only the background/content sibling without backdrop recursion cycles.
    val libraryBackdrop = rememberLayerBackdrop {
        drawRect(appColors.background)
        drawContent()
    }

    var overviewScrollOffsetPx by remember { mutableIntStateOf(0) }
    var directoryScrollOffsetPx by remember { mutableIntStateOf(0) }
    val activeAuroraScrollOffsetPx = if (activeDirectoryView == null) {
        overviewScrollOffsetPx
    } else {
        directoryScrollOffsetPx
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(appColors.background)
    ) {
        // =====================================================================
        // LAYER 1: AURORA + OVERVIEW / ALPHABETICAL DIRECTORIES (Backdropped)
        // =====================================================================
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(libraryBackdrop)
        ) {
            HomeAuroraBackground(
                auroraColors = auroraColors,
                isDark = appColors.isDark,
                scrollOffsetPxProvider = { activeAuroraScrollOffsetPx },
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = if (appColors.isDark) 0.14f else 0.08f),
                                    Color.Transparent
                                ),
                                center = Offset(size.width * 0.85f, size.height * 0.10f),
                                radius = size.width * 0.70f
                            )
                        )
                    }
            )

            AnimatedContent(
                targetState = activeDirectoryView,
                transitionSpec = {
                    (fadeIn(tween(240)) + scaleIn(
                        initialScale = 0.97f,
                        animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f)
                    )) togetherWith (fadeOut(tween(180)) + scaleOut(targetScale = 0.98f))
                },
                label = "libraryDirectoryTransition",
                modifier = Modifier.fillMaxSize()
            ) { directoryView ->
                if (directoryView == null) {
                    LibraryOverviewContent(
                        selectedSourceType = selectedSourceType,
                        recentlyAddedTracks = effectiveRecentlyAdded,
                        artists = artists,
                        albums = albums,
                        songs = songs,
                        currentPlayingTrackId = currentPlayingTrackId,
                        isPlaybackPlaying = isPlaybackPlaying,
                        likedTrackIds = likedTrackIds,
                        onToggleFavorite = onToggleFavorite,
                        onTrackClick = onTrackClick,
                        onAlbumClick = onAlbumClick,
                        onArtistClick = onArtistClick,
                        onSongMoreClick = onSongMoreClick,
                        onOpenRecentlyAdded = { navigateToSubView(LibrarySubView.RECENTLY_ADDED) },
                        onOpenArtistsDirectory = { navigateToSubView(LibrarySubView.ARTISTS) },
                        onOpenAlbumsDirectory = { navigateToSubView(LibrarySubView.ALBUMS) },
                        onOpenSongsDirectory = { navigateToSubView(LibrarySubView.SONGS) },
                        onScanMediaStore = onScanMediaStore,
                        onAddFolderClick = { folderPickerLauncher.launch(null) },
                        topContentPadding = topContentPadding,
                        appColors = appColors,
                        accentColor = accentColor,
                        onScrollOffsetChanged = { overviewScrollOffsetPx = it },
                        onPlayTrackFromList = onPlayTrackFromList
                    )
                } else {
                    LibraryAlphabeticalDirectory(
                        subView = directoryView,
                        artists = artists,
                        albums = albums,
                        songs = songs,
                        currentPlayingTrackId = currentPlayingTrackId,
                        isPlaybackPlaying = isPlaybackPlaying,
                        likedTrackIds = likedTrackIds,
                        onToggleFavorite = onToggleFavorite,
                        onArtistClick = onArtistClick,
                        onAlbumClick = onAlbumClick,
                        onTrackClick = onTrackClick,
                        onSongMoreClick = onSongMoreClick,
                        topContentPadding = topContentPadding,
                        appColors = appColors,
                        accentColor = accentColor,
                        onScrollOffsetChanged = { directoryScrollOffsetPx = it },
                        onPlayTrackFromList = onPlayTrackFromList
                    )
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

        CompositionLocalProvider(LocalAppBackdrop provides libraryBackdrop) {
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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = headerTitle,
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
                            text = headerSubtitle,
                            color = appColors.textSecondary,
                            fontFamily = PlusJakartaSans,
                            fontSize = 12.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (isScanning) {
                        Spacer(modifier = Modifier.width(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = readableAccentColor(accentColor, appColors.isDark),
                            strokeWidth = 2.5.dp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Horizontal scrollable LiquidGlass Segmented Slicers (Category + Source)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CategoryFilterPill(
                        selectedCategory = selectedCategory,
                        onSelectCategory = { cat ->
                            val targetSubView = when (cat) {
                                SearchCategoryFilter.ALL -> null
                                SearchCategoryFilter.ARTISTS -> LibrarySubView.ARTISTS
                                SearchCategoryFilter.ALBUMS -> LibrarySubView.ALBUMS
                                SearchCategoryFilter.SONGS -> LibrarySubView.SONGS
                            }
                            onSelectCategory(cat)
                            if (onOpenSubView != null) {
                                onOpenSubView(targetSubView)
                            } else {
                                localSubView = targetSubView
                            }
                        },
                        appColors = appColors,
                        accentColor = accentColor
                    )

                    SourceFilterPill(
                        selectedSourceType = selectedSourceType,
                        onSelectSourceType = onSelectSourceType,
                        appColors = appColors,
                        accentColor = accentColor
                    )
                }
            }
        }

        // =====================================================================
        // LAYER 3: FULLSCREEN "AÑADIDOS RECIENTEMENTE" CHRONOLOGICAL OVERLAY
        // =====================================================================
        AnimatedVisibility(
            visible = isRecentlyAddedOpen,
            enter = fadeIn(tween(240)) + scaleIn(
                initialScale = 0.94f,
                animationSpec = spring(dampingRatio = 0.78f, stiffness = 360f)
            ),
            exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.96f),
            modifier = Modifier.fillMaxSize()
        ) {
            RecentlyAddedExpandedScreen(
                tracks = effectiveRecentlyAdded,
                currentPlayingTrackId = currentPlayingTrackId,
                isPlaybackPlaying = isPlaybackPlaying,
                auroraColors = auroraColors,
                onTrackClick = onTrackClick,
                onTrackMoreClick = onSongMoreClick,
                likedTrackIds = likedTrackIds,
                onToggleFavorite = onToggleFavorite,
                onBack = { navigateToSubView(null) },
                applyStatusBarsPadding = applyStatusBarsPadding,
                onPlayTrackFromList = onPlayTrackFromList,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// =============================================================================
// LIBRARY "ALL" OVERVIEW CONTENT
// =============================================================================

@Composable
private fun LibraryOverviewContent(
    selectedSourceType: MediaSourceType,
    recentlyAddedTracks: List<TrackItem>,
    artists: List<ArtistItem>,
    albums: List<AlbumItem>,
    songs: List<TrackItem>,
    currentPlayingTrackId: String?,
    isPlaybackPlaying: Boolean,
    likedTrackIds: Set<String>,
    onToggleFavorite: ((TrackItem) -> Unit)?,
    onTrackClick: (TrackItem) -> Unit,
    onAlbumClick: (AlbumItem) -> Unit,
    onArtistClick: (ArtistItem) -> Unit,
    onSongMoreClick: (TrackItem) -> Unit,
    onOpenRecentlyAdded: () -> Unit,
    onOpenArtistsDirectory: () -> Unit,
    onOpenAlbumsDirectory: () -> Unit,
    onOpenSongsDirectory: () -> Unit,
    onScanMediaStore: () -> Unit,
    onAddFolderClick: () -> Unit,
    topContentPadding: Dp,
    appColors: AppThemeColors,
    accentColor: Color,
    onScrollOffsetChanged: (Int) -> Unit,
    onPlayTrackFromList: ((List<TrackItem>, Int) -> Unit)? = null
) {
    val listState = rememberLazyListState()
    val scrollPx by remember {
        derivedStateOf { listState.firstVisibleItemIndex * 220 + listState.firstVisibleItemScrollOffset }
    }
    LaunchedEffect(scrollPx) { onScrollOffsetChanged(scrollPx) }

    val hasAnyContent = recentlyAddedTracks.isNotEmpty() ||
        artists.isNotEmpty() ||
        albums.isNotEmpty() ||
        songs.isNotEmpty()

    val cappedRecentlyAdded = remember(recentlyAddedTracks) { recentlyAddedTracks.take(10) }
    val cappedArtists = remember(artists) { artists.take(10) }
    val cappedAlbums = remember(albums) { albums.take(10) }
    val overviewSongs = remember(songs) { songs.take(15) }

    val windowSizeClass = LocalAppWindowSizeClass.current
    val bottomChromePadding = LocalBottomChromePadding.current
    val songColumns = if (windowSizeClass.isMediumOrExpandedWidth) 2 else 1

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = topContentPadding, bottom = bottomChromePadding),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (!hasAnyContent) {
            item(key = "library_empty_card") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LibraryLocalEmptyState(
                        selectedSourceType = selectedSourceType,
                        onScanMediaStore = onScanMediaStore,
                        onAddFolderClick = onAddFolderClick,
                        appColors = appColors,
                        accentColor = accentColor,
                        modifier = Modifier
                            .widthIn(max = 560.dp)
                            .fillMaxWidth()
                    )
                }
            }
            return@LazyColumn
        }

        // 1. FIRST SECTION: "Añadidos recientemente" (capped at 10 + "Ver más")
        if (cappedRecentlyAdded.isNotEmpty()) {
            item(key = "section_recently_added") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LibraryOverviewSectionHeader(
                        title = "Recently Added",
                        icon = Icons.Rounded.History,
                        trailingActionText = "See more",
                        onTrailingActionClick = onOpenRecentlyAdded,
                        appColors = appColors,
                        accentColor = accentColor
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val recRowState = rememberLazyListState()
                    LazyRow(
                        state = recRowState,
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(
                            items = cappedRecentlyAdded,
                            key = { idx, track -> "rec_recent_${track.id}_$idx" }
                        ) { idx, track ->
                            val isCurrent = track.id == currentPlayingTrackId
                            LibraryRecentlyAddedCard(
                                track = track,
                                isCurrent = isCurrent,
                                isPlaying = isPlaybackPlaying && isCurrent,
                                appColors = appColors,
                                accentColor = accentColor,
                                onClick = {
                                    if (onPlayTrackFromList != null) {
                                        onPlayTrackFromList(recentlyAddedTracks, idx)
                                    } else {
                                        onTrackClick(track)
                                    }
                                },
                                modifier = Modifier
                                    .width(152.dp)
                                    .height(198.dp)
                                    .bentoScrollTransform(recRowState, itemIndex = idx)
                            )
                        }

                        item(key = "rec_see_more") {
                            SeeMoreDirectoryCard(
                                title = "See more",
                                subtitle = if (recentlyAddedTracks.size > 10) {
                                    "+${recentlyAddedTracks.size - 10} more"
                                } else {
                                    "Timeline"
                                },
                                appColors = appColors,
                                accentColor = accentColor,
                                onClick = onOpenRecentlyAdded,
                                modifier = Modifier
                                    .width(134.dp)
                                    .height(198.dp)
                                    .bentoScrollTransform(recRowState, itemIndex = cappedRecentlyAdded.size)
                            )
                        }
                    }
                }
            }
        }

        // 2. ARTISTS ROW (capped at 10 + "More artists" when > 10)
        if (cappedArtists.isNotEmpty()) {
            item(key = "section_artists") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LibraryOverviewSectionHeader(
                        title = "Artists",
                        icon = Icons.Rounded.Person,
                        trailingActionText = if (artists.size > 10) "More (${artists.size})" else "${artists.size}",
                        onTrailingActionClick = onOpenArtistsDirectory,
                        appColors = appColors,
                        accentColor = accentColor
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val artistRowState = rememberLazyListState()
                    LazyRow(
                        state = artistRowState,
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(
                            items = cappedArtists,
                            key = { idx, artist -> "overview_artist_${artist.id}_$idx" }
                        ) { idx, artist ->
                            LibraryArtistCircleCard(
                                artist = artist,
                                appColors = appColors,
                                accentColor = accentColor,
                                onClick = { onArtistClick(artist) },
                                avatarSize = 86.dp,
                                modifier = Modifier
                                    .width(94.dp)
                                    .bentoScrollTransform(artistRowState, itemIndex = idx)
                            )
                        }

                        if (artists.size > 10) {
                            item(key = "artists_see_more") {
                                SeeMoreDirectoryCard(
                                    title = "More artists",
                                    subtitle = "+${artists.size - 10}",
                                    appColors = appColors,
                                    accentColor = accentColor,
                                    onClick = onOpenArtistsDirectory,
                                    modifier = Modifier
                                        .width(114.dp)
                                        .height(124.dp)
                                        .bentoScrollTransform(artistRowState, itemIndex = cappedArtists.size)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. ALBUMS ROW (capped at 10 + "See more albums" when > 10)
        if (cappedAlbums.isNotEmpty()) {
            item(key = "section_albums") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LibraryOverviewSectionHeader(
                        title = "Albums",
                        icon = Icons.Rounded.Album,
                        trailingActionText = if (albums.size > 10) "See more (${albums.size})" else "${albums.size}",
                        onTrailingActionClick = onOpenAlbumsDirectory,
                        appColors = appColors,
                        accentColor = accentColor
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val albumRowState = rememberLazyListState()
                    LazyRow(
                        state = albumRowState,
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(
                            items = cappedAlbums,
                            key = { idx, album -> "overview_album_${album.id}_$idx" }
                        ) { idx, album ->
                            LibraryAlbumCard(
                                album = album,
                                appColors = appColors,
                                accentColor = accentColor,
                                onClick = { onAlbumClick(album) },
                                modifier = Modifier
                                    .width(156.dp)
                                    .height(198.dp)
                                    .bentoScrollTransform(albumRowState, itemIndex = idx)
                            )
                        }

                        if (albums.size > 10) {
                            item(key = "albums_see_more") {
                                SeeMoreDirectoryCard(
                                    title = "See more albums",
                                    subtitle = "+${albums.size - 10}",
                                    appColors = appColors,
                                    accentColor = accentColor,
                                    onClick = onOpenAlbumsDirectory,
                                    modifier = Modifier
                                        .width(136.dp)
                                        .height(198.dp)
                                        .bentoScrollTransform(albumRowState, itemIndex = cappedAlbums.size)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. SONGS SECTION (Playing-Queue-style frosted translucent cards)
        if (overviewSongs.isNotEmpty()) {
            item(key = "section_songs_header") {
                LibraryOverviewSectionHeader(
                    title = "Songs",
                    icon = Icons.Rounded.MusicNote,
                    trailingActionText = "View all (${songs.size})",
                    onTrailingActionClick = onOpenSongsDirectory,
                    appColors = appColors,
                    accentColor = accentColor
                )
            }

            if (songColumns == 1) {
                itemsIndexed(
                    items = overviewSongs,
                    key = { idx, track -> "overview_song_${track.id}_$idx" },
                    contentType = { _, _ -> "QueueSongRow" }
                ) { idx, track ->
                    val isCurrent = track.id == currentPlayingTrackId
                    LibraryQueueSongRow(
                        track = track,
                        isCurrent = isCurrent,
                        isPlaying = isPlaybackPlaying && isCurrent,
                        isFavorite = track.id in likedTrackIds || track.isLiked,
                        onToggleFavorite = onToggleFavorite?.let { callback -> { callback(track) } },
                        onClick = {
                            if (onPlayTrackFromList != null) {
                                onPlayTrackFromList(songs, idx)
                            } else {
                                onTrackClick(track)
                            }
                        },
                        onMoreClick = { onSongMoreClick(track) },
                        appColors = appColors,
                        accentColor = accentColor,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            } else {
                val pairedOverviewSongs = overviewSongs.chunked(songColumns)
                itemsIndexed(
                    items = pairedOverviewSongs,
                    key = { rowIdx, _ -> "overview_song_row_$rowIdx" },
                    contentType = { _, _ -> "QueueSongGridRow" }
                ) { rowIdx, rowTracks ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowTracks.forEachIndexed { colIdx, track ->
                            val actualIdx = rowIdx * songColumns + colIdx
                            val isCurrent = track.id == currentPlayingTrackId
                            LibraryQueueSongRow(
                                track = track,
                                isCurrent = isCurrent,
                                isPlaying = isPlaybackPlaying && isCurrent,
                                isFavorite = track.id in likedTrackIds || track.isLiked,
                                onToggleFavorite = onToggleFavorite?.let { callback -> { callback(track) } },
                                onClick = {
                                    if (onPlayTrackFromList != null) {
                                        onPlayTrackFromList(songs, actualIdx)
                                    } else {
                                        onTrackClick(track)
                                    }
                                },
                                onMoreClick = { onSongMoreClick(track) },
                                appColors = appColors,
                                accentColor = accentColor,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(songColumns - rowTracks.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            if (songs.size > overviewSongs.size) {
                item(key = "songs_view_all_pill") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LibraryFullWidthActionPill(
                            label = "View all songs (${songs.size})",
                            icon = Icons.AutoMirrored.Rounded.ArrowForward,
                            appColors = appColors,
                            accentColor = accentColor,
                            onClick = onOpenSongsDirectory,
                            modifier = Modifier
                                .widthIn(max = 520.dp)
                                .fillMaxWidth()
                        )
                    }
                }
            }
        }

        // 5. LOCAL LIBRARY SCAN / ADD FOLDER BAR (when Local or All is selected)
        if (selectedSourceType != MediaSourceType.ONLINE) {
            item(key = "local_tools_bar") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LibraryLocalToolsBar(
                        onScanMediaStore = onScanMediaStore,
                        onAddFolderClick = onAddFolderClick,
                        appColors = appColors,
                        accentColor = accentColor,
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxWidth()
                    )
                }
            }
        }
    }
}

// =============================================================================
// NAVBAR-STYLE LIQUIDGLASS FILTER SLICERS (CATEGORY & SOURCE)
// =============================================================================

@Composable
private fun CategoryFilterPill(
    selectedCategory: SearchCategoryFilter,
    onSelectCategory: (SearchCategoryFilter) -> Unit,
    appColors: AppThemeColors,
    accentColor: Color
) {
    LiquidGlassSegmentedPill(
        options = SearchCategoryFilter.entries,
        selectedOption = selectedCategory,
        labelProvider = { it.label },
        onSelectOption = onSelectCategory,
        appColors = appColors,
        accentColor = accentColor
    )
}

@Composable
private fun SourceFilterPill(
    selectedSourceType: MediaSourceType,
    onSelectSourceType: (MediaSourceType) -> Unit,
    appColors: AppThemeColors,
    accentColor: Color
) {
    LiquidGlassSegmentedPill(
        options = MediaSourceType.entries,
        selectedOption = selectedSourceType,
        labelProvider = { it.label },
        onSelectOption = onSelectSourceType,
        appColors = appColors,
        accentColor = accentColor
    )
}

// =============================================================================
// SECTION HEADERS, ACTION PILLS & LOCAL TOOLS BAR
// =============================================================================

@Composable
private fun LibraryOverviewSectionHeader(
    title: String,
    icon: ImageVector,
    trailingActionText: String?,
    onTrailingActionClick: (() -> Unit)?,
    appColors: AppThemeColors,
    accentColor: Color
) {
    val displayAccent = readableAccentColor(accentColor, appColors.isDark)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(ContinuousRoundedRectangle(size = 9.dp))
                .background(displayAccent.copy(alpha = if (appColors.isDark) 0.22f else 0.14f))
                .border(0.8.dp, displayAccent.copy(alpha = 0.45f), ContinuousRoundedRectangle(size = 9.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = displayAccent,
                modifier = Modifier.size(15.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = title,
            color = appColors.textPrimary,
            fontFamily = GoogleSans,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        if (!trailingActionText.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(10.dp))
            val jellyState = rememberJellyPressState(pressedScaleX = 0.93f, pressedScaleY = 0.89f)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .jellyTapScale(jellyState)
                    .clip(CircleShape)
                    .background(displayAccent.copy(alpha = if (appColors.isDark) 0.18f else 0.12f))
                    .border(0.8.dp, displayAccent.copy(alpha = 0.45f), CircleShape)
                    .then(
                        if (onTrailingActionClick != null) {
                            Modifier.clickable(
                                interactionSource = jellyState.interactionSource,
                                indication = null,
                                onClick = onTrailingActionClick
                            )
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = trailingActionText,
                    color = displayAccent,
                    fontFamily = GoogleSans,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                if (onTrailingActionClick != null) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = displayAccent,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryFullWidthActionPill(
    label: String,
    icon: ImageVector,
    appColors: AppThemeColors,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val jellyState = rememberJellyPressState(pressedScaleX = 0.96f, pressedScaleY = 0.92f)
    val displayAccent = readableAccentColor(accentColor, appColors.isDark)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .jellyTapScale(jellyState)
            .clip(SegmentPillShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        displayAccent.copy(alpha = if (appColors.isDark) 0.24f else 0.15f),
                        displayAccent.copy(alpha = if (appColors.isDark) 0.12f else 0.08f)
                    )
                )
            )
            .border(0.9.dp, displayAccent.copy(alpha = 0.45f), SegmentPillShape)
            .clickable(
                interactionSource = jellyState.interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            color = displayAccent,
            fontFamily = GoogleSans,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = displayAccent,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun LibraryLocalToolsBar(
    onScanMediaStore: () -> Unit,
    onAddFolderClick: () -> Unit,
    appColors: AppThemeColors,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val displayAccent = readableAccentColor(accentColor, appColors.isDark)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(UtilityCardShape)
            .background(if (appColors.isDark) Color(0x4D171C28) else Color(0xDFFFFFFF))
            .border(
                width = 0.9.dp,
                color = if (appColors.isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.08f),
                shape = UtilityCardShape
            )
            .padding(14.dp)
    ) {
        Text(
            text = "Music on Device",
            color = appColors.textPrimary,
            fontFamily = GoogleSans,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = "Scan MediaStore or add a custom folder.",
            color = appColors.textSecondary,
            fontFamily = PlusJakartaSans,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            LibraryActionButtonPill(
                label = "Scan",
                icon = Icons.Rounded.Refresh,
                isPrimary = true,
                appColors = appColors,
                accentColor = displayAccent,
                onClick = onScanMediaStore,
                modifier = Modifier.weight(1f)
            )
            LibraryActionButtonPill(
                label = "Add Folder",
                icon = Icons.Rounded.CreateNewFolder,
                isPrimary = false,
                appColors = appColors,
                accentColor = displayAccent,
                onClick = onAddFolderClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LibraryLocalEmptyState(
    selectedSourceType: MediaSourceType,
    onScanMediaStore: () -> Unit,
    onAddFolderClick: () -> Unit,
    appColors: AppThemeColors,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val displayAccent = readableAccentColor(accentColor, appColors.isDark)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .clip(UtilityCardShape)
            .background(if (appColors.isDark) Color(0x66171C28) else Color(0xEBFFFFFF))
            .border(
                width = 0.9.dp,
                color = if (appColors.isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.08f),
                shape = UtilityCardShape
            )
            .padding(horizontal = 22.dp, vertical = 28.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(displayAccent.copy(alpha = if (appColors.isDark) 0.22f else 0.14f))
                .border(1.dp, displayAccent.copy(alpha = 0.48f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.LibraryMusic,
                contentDescription = null,
                tint = displayAccent,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = when (selectedSourceType) {
                MediaSourceType.LOCAL -> "Your local library is empty"
                MediaSourceType.ONLINE -> "No online songs yet"
                MediaSourceType.ALL -> "Your library is empty"
            },
            color = appColors.textPrimary,
            fontFamily = GoogleSans,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Scan your device or add folders to automatically organize artists, albums, and recent additions.",
            color = appColors.textSecondary,
            fontFamily = PlusJakartaSans,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
        if (selectedSourceType != MediaSourceType.ONLINE) {
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                LibraryActionButtonPill(
                    label = "Scan",
                    icon = Icons.Rounded.Refresh,
                    isPrimary = true,
                    appColors = appColors,
                    accentColor = displayAccent,
                    onClick = onScanMediaStore,
                    modifier = Modifier.weight(1f)
                )
                LibraryActionButtonPill(
                    label = "Folder",
                    icon = Icons.Rounded.CreateNewFolder,
                    isPrimary = false,
                    appColors = appColors,
                    accentColor = displayAccent,
                    onClick = onAddFolderClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun LibraryActionButtonPill(
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
            .height(40.dp)
            .jellyTapScale(jellyState)
            .clip(SegmentPillShape)
            .background(bgBrush)
            .border(
                width = 0.85.dp,
                color = if (isPrimary) Color.White.copy(alpha = 0.35f) else if (appColors.isDark) Color.White.copy(alpha = 0.20f) else Color.Black.copy(alpha = 0.10f),
                shape = SegmentPillShape
            )
            .clickable(
                interactionSource = jellyState.interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentTint,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = contentTint,
            fontFamily = GoogleSans,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LibraryPreviewContent(widthDp: Int, heightDp: Int) {
    AdaptivePreviewSurface(widthDp = widthDp, heightDp = heightDp) {
        LibraryScreen(
            headerTitle = "Library",
            headerSubtitle = "6 songs · 4 albums",
            selectedCategory = SearchCategoryFilter.ALL,
            selectedSourceType = MediaSourceType.ALL,
            onSelectCategory = {},
            onSelectSourceType = {},
            artists = AdaptivePreviewFixtures.artists,
            albums = AdaptivePreviewFixtures.albums,
            songs = AdaptivePreviewFixtures.tracks,
            recentlyAddedTracks = AdaptivePreviewFixtures.tracks,
            isScanning = false,
            onTrackClick = {},
            onAlbumClick = {},
            onArtistClick = {},
            onScanMediaStore = {},
            onAddFolder = {},
            onSongMoreClick = {},
            likedTrackIds = setOf("ytm_paparazzi"),
            currentPlayingTrackId = "ytm_paparazzi",
            isPlaybackPlaying = true,
            applyStatusBarsPadding = true
        )
    }
}

@Preview(name = "Phone (360x800)", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
private fun LibraryPhonePreview() = LibraryPreviewContent(360, 800)

@Preview(name = "Tablet Portrait (800x1280)", widthDp = 800, heightDp = 1280, showBackground = true)
@Composable
private fun LibraryTabletPortraitPreview() = LibraryPreviewContent(800, 1280)

@Preview(name = "Tablet Landscape (1280x800)", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
private fun LibraryTabletLandscapePreview() = LibraryPreviewContent(1280, 800)
