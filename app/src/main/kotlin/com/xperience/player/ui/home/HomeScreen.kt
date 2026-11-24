package com.xperience.player.ui.home

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xperience.player.ui.adaptive.AdaptivePreviewFixtures
import com.xperience.player.ui.adaptive.AdaptivePreviewSurface
import com.xperience.player.ui.adaptive.LocalAppWindowSizeClass
import com.xperience.player.ui.adaptive.LocalBottomChromePadding
import com.xperience.player.ui.adaptive.LocalTopChromePadding
import com.xperience.player.ui.adaptive.rememberAdaptivePaneScaffoldDirective
import com.xperience.player.ui.adaptive.supportsTwoPaneLayout
import com.xperience.player.ui.adaptive.usesTopDesktopChrome
import com.xperience.player.ui.album.AlbumScreen
import com.xperience.player.ui.ambient.AmbientModeActions
import com.xperience.player.ui.ambient.AmbientModeScreen
import com.xperience.player.ui.ambient.AmbientModeUiState
import com.xperience.player.ui.artist.ArtistScreen
import com.xperience.player.ui.cast.CastButton
import com.xperience.player.ui.cast.CastButtonStyle
import com.xperience.player.ui.cast.CastDevicePickerSheet
import com.xperience.player.ui.cast.CastExpandedControlsSheet
import com.xperience.player.ui.cast.CastMiniBanner
import com.xperience.player.ui.ringtone.RingtoneEditorSheet
import com.xperience.player.ui.component.DesktopTopNavBar
import com.xperience.player.ui.component.GlassCircleButton
import com.xperience.player.ui.component.GlassEffectConfig
import com.xperience.player.ui.component.LocalAppBackdrop
import com.xperience.player.ui.component.LocalGlassEffectConfig
import com.xperience.player.ui.component.backdrop.backdrops.layerBackdrop
import com.xperience.player.ui.component.backdrop.backdrops.rememberBackdropFreeze
import com.xperience.player.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.xperience.player.ui.component.rememberFloatingTabBarScrollConnection
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.TextFieldValue
import com.xperience.player.ui.component.AppFloatingNavBar
import com.xperience.player.ui.component.LocalNavSearchState
import com.xperience.player.ui.component.NavSearchState
import com.xperience.player.ui.component.SearchSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.xperience.player.ui.components.BentoGrid
import com.xperience.player.ui.components.BottomNavBar
import com.xperience.player.ui.components.NavigationTab
import com.xperience.player.ui.components.DiscoverByGenreSection
import com.xperience.player.ui.components.HeaderBar
import com.xperience.player.ui.components.HomeAuroraBackground
import com.xperience.player.ui.components.HomeFeedSkeletonScreen
import com.xperience.player.ui.components.MiniPlayerBar
import com.xperience.player.ui.components.MoreLikeAlbumSectionRow
import com.xperience.player.ui.components.NovedadesSection
import com.xperience.player.ui.components.PlaylistsMadeForYouSection
import com.xperience.player.ui.components.RadioStationsSection
import com.xperience.player.ui.components.RecommendedAlbumsSection
import com.xperience.player.ui.components.RecommendedSongsSection
import com.xperience.player.ui.components.RelatedArtistsRow
import com.xperience.player.ui.components.SmartSuggestedPlaylistsSection
import com.xperience.player.ui.components.TopPicksSection
import com.xperience.player.ui.components.YtmLoginPromptBanner
import com.xperience.player.ui.library.LibraryScreen
import com.xperience.player.ui.library.LibrarySubView
import com.xperience.player.ui.liked.LikedScreen
import com.xperience.player.ui.playlists.AddToPlaylistSheet
import com.xperience.player.ui.playlists.CreatePlaylistDialog
import com.xperience.player.ui.playlists.ImportYouTubePlaylistsSheet
import com.xperience.player.ui.playlists.PlaylistDetailScreen
import com.xperience.player.ui.playlists.PlaylistsEmptyDetailPane
import com.xperience.player.ui.playlists.PlaylistsScreen
import com.xperience.player.ui.playlists.RemovePlaylistDialog
import com.xperience.player.ui.components.TrackOptionsMenuSheet
import com.xperience.player.ui.player.DownloadConfirmationModal
import com.xperience.player.ui.player.NowPlayingScreen
import com.xperience.player.ui.player.NowPlayingSidebar
import com.xperience.player.ui.player.NowPlayingSidebarDefaults
import com.xperience.player.ui.player.NowPlayingSidebarTab
import com.xperience.player.service.update.ApkInstaller
import com.xperience.player.ui.search.SearchScreen
import com.xperience.player.ui.settings.SettingsSheet
import com.xperience.player.ui.settings.UpdateDialog
import com.xperience.player.ui.settings.YouTubeLoginSheet
import kotlin.coroutines.cancellation.CancellationException
import com.xperience.player.ui.theme.AccentColorOption
import com.xperience.player.ui.theme.DarkThemeColors
import com.xperience.player.ui.theme.LightThemeColors
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.ThemeMode

enum class MainDisplayState {
    CATALOG,
    SEARCH
}

private fun computeOverlayZIndex(
    stack: List<OverlayLayer>,
    layer: OverlayLayer,
    isLayerActive: Boolean
): Float {
    val idx = stack.indexOf(layer)
    return when {
        idx >= 0 -> (idx + 1).toFloat()
        !isLayerActive -> (stack.size + 10).toFloat()
        else -> 0.5f
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel()
) {
    val feed by viewModel.homeFeed.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val outputDeviceName by viewModel.outputDeviceName.collectAsState()
    val context = LocalContext.current
    val selectedTab by viewModel.selectedTab.collectAsState()
    val isSettingsOpen by viewModel.isSettingsOpen.collectAsState()
    val libraryFolders by viewModel.libraryFolders.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    // Library Tab States
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedSourceType by viewModel.selectedSourceType.collectAsState()
    val libraryArtists by viewModel.libraryArtists.collectAsState()
    val libraryAlbums by viewModel.libraryAlbums.collectAsState()
    val librarySongs by viewModel.librarySongs.collectAsState()
    val recentlyAddedLibraryTracks by viewModel.recentlyAddedLibraryTracks.collectAsState()
    val activeLibrarySubView by viewModel.activeLibrarySubView.collectAsState()
    val libraryHeaderTitle by viewModel.libraryHeaderTitle.collectAsState()
    val libraryHeaderSubtitle by viewModel.libraryHeaderSubtitle.collectAsState()
    val liquidGlassSettings by viewModel.liquidGlassSettings.collectAsState()
    val accentColorOption by viewModel.accentColorOption.collectAsState()
    val dynamicArtworkColor by viewModel.dynamicArtworkColor.collectAsState()
    val auroraColors by viewModel.auroraColors.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()

    // YouTube Music Account & Home Grid States
    val ytmAccountState by viewModel.ytmAccountState.collectAsState()
    val shouldShowYtmLoginPrompt by viewModel.shouldShowYtmLoginPrompt.collectAsState()
    val isYtmLoginSheetOpen by viewModel.isYtmLoginSheetOpen.collectAsState()
    val isSyncingYtmAccount by viewModel.isSyncingYtmAccount.collectAsState()
    val ytmLoginError by viewModel.ytmLoginError.collectAsState()
    val isYtmPlaylistImportOpen by viewModel.isYtmPlaylistImportOpen.collectAsState()
    val isLoadingYtmLibraryPlaylists by viewModel.isLoadingYtmLibraryPlaylists.collectAsState()
    val ytmLibraryPlaylists by viewModel.ytmLibraryPlaylists.collectAsState()
    val selectedYtmPlaylistIds by viewModel.selectedYtmPlaylistIds.collectAsState()
    val isImportingYtmPlaylists by viewModel.isImportingYtmPlaylists.collectAsState()
    val ytmImportProgressText by viewModel.ytmImportProgressText.collectAsState()
    val ytmImportError by viewModel.ytmImportError.collectAsState()
    val ytmImportStatusMessage by viewModel.ytmImportStatusMessage.collectAsState()
    val playlistPendingDelete by viewModel.playlistPendingDelete.collectAsState()
    val isRefreshingImportedPlaylist by viewModel.isRefreshingImportedPlaylist.collectAsState()
    val homeGridCategory by viewModel.homeGridCategory.collectAsState()
    val isHomeGridOpen = homeGridCategory != null
    var lastHomeGridCategory by remember { mutableStateOf(homeGridCategory) }
    LaunchedEffect(homeGridCategory) {
        if (homeGridCategory != null) {
            lastHomeGridCategory = homeGridCategory
        }
    }

    // Determine adaptive Dark vs Light mode
    val isSystemDark = isSystemInDarkTheme()
    val isDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val appColors = if (isDarkTheme) DarkThemeColors else LightThemeColors

    // Search Mode States & Moods
    val isSearchActive by viewModel.isSearchActive.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchFilter by viewModel.searchFilter.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val moodsAndGenresFeed by viewModel.moodsAndGenresFeed.collectAsState()
    val isLoadingMoods by viewModel.isLoadingMoods.collectAsState()
    val selectedMoodCategory by viewModel.selectedMoodCategory.collectAsState()
    val moodDetailResult by viewModel.moodDetailResult.collectAsState()
    val isLoadingMoodDetail by viewModel.isLoadingMoodDetail.collectAsState()
    val overlayStack by viewModel.overlayStack.collectAsState()
    val isPlayerExpanded by viewModel.isPlayerExpanded.collectAsState()
    val isAmbientModeOpen by viewModel.isAmbientModeOpen.collectAsState()
    val castState by viewModel.castState.collectAsState()
    val castRoutes by viewModel.castRoutes.collectAsState()
    val isCastScanning by viewModel.isCastScanning.collectAsState()
    val isCastPickerOpen by viewModel.isCastPickerOpen.collectAsState()
    val isCastControlsOpen by viewModel.isCastControlsOpen.collectAsState()
    val trackForRingtoneEditor by viewModel.trackForRingtoneEditor.collectAsState()
    val likedTrackIds by viewModel.likedTrackIds.collectAsState()
    val likedTracks by viewModel.likedTracks.collectAsState()
    val likedRecommendations by viewModel.likedRecommendations.collectAsState()
    val isLoadingLikedRecommendations by viewModel.isLoadingLikedRecommendations.collectAsState()
    val isLiked = playbackState.currentTrack?.let { likedTrackIds.contains(it.id) } ?: false

    val availableVisualizers by viewModel.availableVisualizers.collectAsState()
    val selectedVisualizer by viewModel.selectedVisualizer.collectAsState()
    val currentLyrics by viewModel.currentLyrics.collectAsState()
    val currentLyricsProviderName by viewModel.currentLyricsProviderName.collectAsState()
    val lyricsProviders by viewModel.lyricsProviders.collectAsState()
    val currentCanvasArtwork by viewModel.currentCanvasArtwork.collectAsState()
    val currentMusicVideo by viewModel.currentMusicVideo.collectAsState()
    val isResolvingMusicVideo by viewModel.isResolvingMusicVideo.collectAsState()
    val playerMediaMode by viewModel.playerMediaMode.collectAsState()

    val downloadOptions by viewModel.downloadOptions.collectAsState()
    val isDownloadModalOpen by viewModel.isDownloadModalOpen.collectAsState()
    val downloadCandidates by viewModel.downloadCandidates.collectAsState()
    val isSearchingCandidates by viewModel.isSearchingCandidates.collectAsState()
    val downloadProgressState by viewModel.downloadProgressState.collectAsState()

    val autoCheckUpdates by viewModel.autoCheckUpdates.collectAsState()
    val updateUiState by viewModel.updateUiState.collectAsState()
    val unknownSourcesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onReturnedFromInstallPermissionSettings()
    }

    // Artist Detail States
    val isArtistDetailOpen by viewModel.isArtistDetailOpen.collectAsState()
    val isArtistDetailLoading by viewModel.isArtistDetailLoading.collectAsState()
    val currentArtistDetail by viewModel.currentArtistDetail.collectAsState()
    val currentArtistVideoUri by viewModel.currentArtistVideoUri.collectAsState()
    val isArtistBioExpanded by viewModel.isArtistBioExpanded.collectAsState()
    val currentArtistAmbientColors by viewModel.currentArtistAmbientColors.collectAsState()
    val currentArtistAmbientColor = currentArtistAmbientColors.colorFor(isDarkTheme)

    // Album Detail States
    val isAlbumDetailOpen by viewModel.isAlbumDetailOpen.collectAsState()
    val isAlbumDetailLoading by viewModel.isAlbumDetailLoading.collectAsState()
    val currentAlbumDetail by viewModel.currentAlbumDetail.collectAsState()
    val currentAlbumAmbientColors by viewModel.currentAlbumAmbientColors.collectAsState()
    val currentAlbumAmbientColor = currentAlbumAmbientColors.colorFor(isDarkTheme)

    // Playlist States
    val playlists by viewModel.playlists.collectAsState()
    val smartSuggestedPlaylists by viewModel.smartSuggestedPlaylists.collectAsState()
    val savedPlaylistTitles = remember(playlists) {
        playlists.map { it.title.trim().lowercase() }.toSet()
    }
    val isPlaylistDetailOpen by viewModel.isPlaylistDetailOpen.collectAsState()
    val currentPlaylistDetail by viewModel.currentPlaylistDetail.collectAsState()
    val isCreatePlaylistDialogOpen by viewModel.isCreatePlaylistDialogOpen.collectAsState()
    val playlistToEdit by viewModel.playlistToEdit.collectAsState()
    val trackForAddToPlaylist by viewModel.trackForAddToPlaylist.collectAsState()
    val trackForOptionsMenu by viewModel.trackForOptionsMenu.collectAsState()

    val homeListState = rememberLazyListState()

    var predictiveBackProgress by remember { mutableFloatStateOf(0f) }

    val topOverlay = overlayStack.lastOrNull()
    val isAlbumTop = topOverlay == OverlayLayer.ALBUM_DETAIL
    val isArtistTop = topOverlay == OverlayLayer.ARTIST_DETAIL
    val isPlaylistTop = topOverlay == OverlayLayer.PLAYLIST_DETAIL
    val isHomeGridTop = topOverlay == OverlayLayer.HOME_GRID
    val isSearchTop = topOverlay == OverlayLayer.SEARCH

    val hasLibraryBackTarget = selectedTab == NavigationTab.Library &&
        (activeLibrarySubView != null || selectedCategory != SearchCategoryFilter.ALL)

    val coroutineScope = rememberCoroutineScope()
    var searchKeyboardActive by remember { mutableStateOf(false) }
    var searchVisualOverride by remember { mutableStateOf<Boolean?>(null) }
    var searchSource by remember { mutableStateOf(SearchSource.ONLINE) }
    val searchFocusRequester = remember { FocusRequester() }

    var queryState by remember { mutableStateOf(TextFieldValue(searchQuery)) }
    LaunchedEffect(searchQuery) {
        if (queryState.text != searchQuery) {
            queryState = TextFieldValue(searchQuery)
        }
    }

    var searchTransitionJob by remember { mutableStateOf<Job?>(null) }

    val enterSearch: () -> Unit = remember(coroutineScope, viewModel) {
        {
            searchTransitionJob?.cancel()
            searchKeyboardActive = false
            searchVisualOverride = null
            viewModel.openSearch()
        }
    }

    val exitSearch: () -> Unit = remember(coroutineScope, viewModel) {
        {
            searchTransitionJob?.cancel()
            searchKeyboardActive = false
            searchVisualOverride = false
            searchTransitionJob = coroutineScope.launch {
                delay(200L)
                viewModel.closeSearch()
                searchVisualOverride = null
            }
        }
    }

    val overlayAboveSearch = isSearchActive && topOverlay != null && topOverlay != OverlayLayer.SEARCH
    val navSearchState = remember(
        searchVisualOverride, isSearchActive, overlayAboveSearch, searchKeyboardActive,
        queryState, searchSource
    ) {
        NavSearchState(
            visualActive = if (overlayAboveSearch) false else (searchVisualOverride ?: isSearchActive),
            keyboardActive = searchKeyboardActive,
            query = queryState,
            onQueryChange = { newTfv ->
                queryState = newTfv
                viewModel.onSearchQueryChange(newTfv.text)
            },
            onSubmit = { q ->
                viewModel.submitSearch(q)
                searchKeyboardActive = false
            },
            searchSource = searchSource,
            onToggleSource = {
                searchSource = if (searchSource == SearchSource.ONLINE) SearchSource.LOCAL else SearchSource.ONLINE
            },
            canToggleSource = true,
            onTapSearchIcon = enterSearch,
            onTapBar = {
                searchKeyboardActive = true
            },
            onExit = exitSearch,
            onCloseKeyboard = { searchKeyboardActive = false },
            focusRequester = searchFocusRequester,
        )
    }

    // Smooth transition for AccentColor (dynamic from album cover or selected static color)
    val currentAccentTarget = if (accentColorOption == AccentColorOption.DYNAMIC) {
        dynamicArtworkColor
    } else {
        accentColorOption.color ?: Color(0xFF00E5FF)
    }

    val animatedAccentColor by animateColorAsState(
        targetValue = currentAccentTarget,
        animationSpec = tween(durationMillis = 600),
        label = "animatedAccentColor"
    )

    // Convx AppBackdrop + BackdropFreeze architecture
    val appBackdrop = rememberLayerBackdrop(
        onDraw = {
            drawRect(appColors.background)
            drawContent()
        }
    )
    val backdropFreeze = rememberBackdropFreeze()

    // Reactive LiquidGlass config driven by Settings sliders and adaptive light/dark mode
    val glassConfig = remember(liquidGlassSettings, isDarkTheme) {
        GlassEffectConfig(
            globalEnabled = true,
            vibrancy = if (isDarkTheme) 1.3f else 1.15f,
            blurRadius = liquidGlassSettings.blurRadiusDp,
            lensHeight = (liquidGlassSettings.refractionStrength * 0.7f).coerceIn(0.1f, 1f),
            lensAmount = liquidGlassSettings.refractionStrength.coerceIn(0.1f, 1f),
            surfaceTintColor = appColors.glassSurfaceTint,
            surfaceOpacity = if (isDarkTheme) liquidGlassSettings.surfaceTintOpacity else (liquidGlassSettings.surfaceTintOpacity * 0.82f).coerceIn(0.35f, 0.75f),
            highlightOpacity = if (isDarkTheme) liquidGlassSettings.specularMeniscus else 0.42f,
            textColor = appColors.glassTextPrimary
        )
    }

    // Scroll connection for Convx-style hide/show on scroll
    val scrollConnection = rememberFloatingTabBarScrollConnection(
        initialIsInline = false,
        scrollThreshold = 50.dp,
        expandThreshold = 8.dp
    )

    val windowSizeClass = LocalAppWindowSizeClass.current
    val usesTopDesktopChrome = windowSizeClass.usesTopDesktopChrome
    val density = LocalDensity.current
    val statusBarTopDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var desktopTopBarHeightPx by remember { mutableIntStateOf(0) }
    val topChromePadding = if (usesTopDesktopChrome) {
        if (desktopTopBarHeightPx > 0) {
            with(density) { desktopTopBarHeightPx.toDp() } + 18.dp
        } else {
            66.dp
        }
    } else {
        0.dp
    }
    val bottomChromePadding = if (usesTopDesktopChrome) 36.dp else 180.dp

    var isNowPlayingSidebarOpen by rememberSaveable { mutableStateOf(false) }
    var sidebarTab by rememberSaveable { mutableStateOf(NowPlayingSidebarTab.QUEUE) }
    val hasActiveTrack = playbackState.currentTrack != null
    LaunchedEffect(hasActiveTrack) {
        if (!hasActiveTrack && isNowPlayingSidebarOpen) {
            isNowPlayingSidebarOpen = false
        }
    }
    val showNowPlayingSidebar = usesTopDesktopChrome && hasActiveTrack && isNowPlayingSidebarOpen

    val navigationTabs = remember {
        listOf(
            NavigationTab.Home,
            NavigationTab.Library,
            NavigationTab.Liked,
            NavigationTab.Playlists
        )
    }

    val selectTabWithReset: (NavigationTab) -> Unit = { tab ->
        if (isSearchActive || searchVisualOverride != null) {
            searchTransitionJob?.cancel()
            searchKeyboardActive = false
            searchVisualOverride = null
        }
        viewModel.onTabSelected(tab)
    }

    CompositionLocalProvider(
        LocalAppBackdrop provides appBackdrop,
        LocalGlassEffectConfig provides glassConfig,
        LocalAccentColor provides animatedAccentColor,
        LocalAppColors provides appColors,
        LocalNavSearchState provides navSearchState,
        LocalTopChromePadding provides topChromePadding,
        LocalBottomChromePadding provides bottomChromePadding
    ) {
        NavigationSuiteScaffold(
            layoutType = NavigationSuiteType.None,
            containerColor = appColors.background,
            navigationSuiteItems = {
                navigationTabs.forEach { tab ->
                    item(
                        selected = tab == selectedTab && !isSearchActive,
                        onClick = { selectTabWithReset(tab) },
                        icon = { Icon(imageVector = tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(appColors.background)
        ) {
            // Full Screen Now Playing Animation Progress
            val playerExpandProgress by animateFloatAsState(
                targetValue = if (isPlayerExpanded) 1f else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "nowPlayingExpandAnim"
            )

            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
            val isSearchVisibleTarget = searchVisualOverride ?: isSearchActive
            val searchExpandAnim by animateFloatAsState(
                targetValue = if (isSearchVisibleTarget) 1f else 0f,
                animationSpec = tween(durationMillis = 220),
                label = "searchExpandAnim"
            )
            val effectiveSearchProgress = (searchExpandAnim * (1f - (if (isSearchTop && selectedMoodCategory == null) predictiveBackProgress else 0f) * 0.35f)).coerceIn(0f, 1f)

            // Album Detail Animation Progress (Spring-driven opening/closing with responsive predictive back)
            val albumExpandAnim by animateFloatAsState(
                targetValue = if (isAlbumDetailOpen) 1f else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "albumExpandAnim"
            )
            val effectiveAlbumProgress = (albumExpandAnim * (1f - (if (isAlbumTop) predictiveBackProgress else 0f) * 0.45f)).coerceIn(0f, 1f)

            // Artist Detail Animation Progress (Spring-driven opening/closing with responsive predictive back)
            val artistExpandAnim by animateFloatAsState(
                targetValue = if (isArtistDetailOpen) 1f else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "artistExpandAnim"
            )
            val effectiveArtistProgress = (artistExpandAnim * (1f - (if (isArtistTop) predictiveBackProgress else 0f) * 0.45f)).coerceIn(0f, 1f)

            // Playlist Detail Animation Progress (Spring-driven opening/closing with responsive predictive back)
            val playlistExpandAnim by animateFloatAsState(
                targetValue = if (isPlaylistDetailOpen) 1f else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "playlistExpandAnim"
            )
            val effectivePlaylistProgress = (playlistExpandAnim * (1f - (if (isPlaylistTop) predictiveBackProgress else 0f) * 0.45f)).coerceIn(0f, 1f)

            // Radio & Novedades Grid Animation Progress
            val homeGridExpandAnim by animateFloatAsState(
                targetValue = if (isHomeGridOpen) 1f else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "homeGridExpandAnim"
            )
            val effectiveGridProgress = (homeGridExpandAnim * (1f - (if (isHomeGridTop) predictiveBackProgress else 0f) * 0.45f)).coerceIn(0f, 1f)

            val overlayFreezeActive = isAppBackdropOverlayFreezeActive(
                isAlbumDetailOpen = isAlbumDetailOpen,
                isArtistDetailOpen = isArtistDetailOpen,
                isPlaylistDetailOpen = isPlaylistDetailOpen,
                isHomeGridOpen = isHomeGridOpen,
                effectiveAlbumProgress = effectiveAlbumProgress,
                effectiveArtistProgress = effectiveArtistProgress,
                effectivePlaylistProgress = effectivePlaylistProgress,
                effectiveGridProgress = effectiveGridProgress
            ) || isPlayerExpanded || playerExpandProgress > 0.001f || isAmbientModeOpen || isSettingsOpen
            val overlayFreezeRef = remember { BooleanArray(1) }
            overlayFreezeRef[0] = overlayFreezeActive
            val isAppBackdropFrozen = remember(backdropFreeze, overlayFreezeRef) {
                { overlayFreezeRef[0] || backdropFreeze.frozen() }
            }

            // Main Content Layer: Home Catalog / Search / Artist Detail Screen
            // Wrapped with layerBackdrop so all screens (including Artist Screen) are captured
            // and blurred/refracted under floating LiquidGlass chrome (MiniPlayerBar & BottomNavBar)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(
                        appBackdrop,
                        frozen = isAppBackdropFrozen
                    )
                    .nestedScroll(backdropFreeze.connection)
            ) {
                Box(modifier = Modifier.fillMaxSize().zIndex(0f)) {
                    when (selectedTab) {
                            NavigationTab.Home -> {
                                val showYtmBanner = shouldDisplayYtmLoginBanner(
                                    accountState = ytmAccountState,
                                    shouldShowPrompt = shouldShowYtmLoginPrompt
                                )
                                val homeSmartPlaylists = smartSuggestedPlaylists.ifEmpty { feed.smartSuggestedPlaylists }
                                val moreLikeSections = remember(feed.moreLikeSections) {
                                    feed.moreLikeSections.filter { it.albums.isNotEmpty() }
                                }
                                val moreLike1 = moreLikeSections.getOrNull(0)
                                val moreLike2 = moreLikeSections.getOrNull(1)
                                val moreLike3 = moreLikeSections.getOrNull(2)

                                Box(modifier = Modifier.fillMaxSize()) {
                                    HomeAuroraBackground(
                                        auroraColors = auroraColors,
                                        isDark = isDarkTheme,
                                        scrollOffsetPxProvider = {
                                            if (homeListState.firstVisibleItemIndex > 0) 2000
                                            else homeListState.firstVisibleItemScrollOffset
                                        },
                                        isVisible = !isPlayerExpanded && !isAmbientModeOpen && overlayStack.isEmpty(),
                                        modifier = Modifier.align(Alignment.TopCenter)
                                    )

                                    // Scrollable Catalog Content Layer (virtualized LazyColumn)
                                    LazyColumn(
                                        state = homeListState,
                                        contentPadding = PaddingValues(
                                            top = topChromePadding,
                                            bottom = bottomChromePadding
                                        ),
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .nestedScroll(scrollConnection)
                                            .statusBarsPadding()
                                    ) {
                                        item(key = "home_header", contentType = "header") {
                                            HeaderBar(
                                                showBrandBadge = !usesTopDesktopChrome,
                                                onRefreshClick = if (usesTopDesktopChrome) {
                                                    { viewModel.refreshHomeFeed() }
                                                } else {
                                                    null
                                                }
                                            )
                                        }

                                        item(key = "ytm_login_banner", contentType = "banner") {
                                            androidx.compose.animation.AnimatedVisibility(
                                                visible = showYtmBanner,
                                                enter = expandVertically(animationSpec = tween(260)) + fadeIn(animationSpec = tween(220)),
                                                exit = shrinkVertically(animationSpec = tween(220)) + fadeOut(animationSpec = tween(180))
                                            ) {
                                                Column(modifier = Modifier.fillMaxWidth()) {
                                                    Spacer(modifier = Modifier.height(12.dp))
                                                    YtmLoginPromptBanner(
                                                        onSignInClick = { viewModel.openYtmLogin() },
                                                        onLaterClick = { viewModel.remindYtmLoginLater() },
                                                        onDismissClick = { viewModel.dismissYtmLoginPrompt() }
                                                    )
                                                }
                                            }
                                        }

                                        if (feed.isFeedLoading) {
                                            item(key = "home_skeleton", contentType = "skeleton") {
                                                Column(modifier = Modifier.fillMaxWidth()) {
                                                    if (showYtmBanner) {
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                    }
                                                    HomeFeedSkeletonScreen(
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                            }
                                        } else {
                                            // 1. Top Picks For You
                                            if (feed.topPicks.isNotEmpty()) {
                                                item(key = "top_picks", contentType = "top_picks") {
                                                    Column(modifier = Modifier.fillMaxWidth()) {
                                                        Spacer(modifier = Modifier.height(if (showYtmBanner) 20.dp else 10.dp))
                                                        TopPicksSection(
                                                            items = feed.topPicks,
                                                            onTopPickClick = { item -> viewModel.onTopPickClick(item) }
                                                        )
                                                        Spacer(modifier = Modifier.height(26.dp))
                                                    }
                                                }
                                            } else {
                                                item(key = "top_picks_spacer", contentType = "spacer") {
                                                    Spacer(modifier = Modifier.height(if (showYtmBanner) 18.dp else 10.dp))
                                                }
                                            }

                                            // 2. Recently Played (Bento Grid)
                                            item(key = "bento_grid", contentType = "bento_grid") {
                                                BentoGrid(
                                                    tracks = feed.recentlyPlayedTracks,
                                                    onAlbumClick = { album -> viewModel.onAlbumClicked(album) },
                                                    onAddFolderClick = { viewModel.openSettings() }
                                                )
                                            }

                                            // 2B. Auto-Generated Smart Playlists ("Sugerencias para ti")
                                            if (homeSmartPlaylists.isNotEmpty()) {
                                                item(key = "smart_playlists", contentType = "smart_playlists") {
                                                    Column(modifier = Modifier.fillMaxWidth()) {
                                                        Spacer(modifier = Modifier.height(26.dp))
                                                        SmartSuggestedPlaylistsSection(
                                                            playlists = homeSmartPlaylists,
                                                            savedPlaylistTitles = savedPlaylistTitles,
                                                            onPlaylistClick = { playlist -> viewModel.openPlaylistDetail(playlist) },
                                                            onPlayPlaylistClick = { playlist -> viewModel.playPlaylistTracks(playlist, 0) },
                                                            onSavePlaylistClick = { playlist -> viewModel.saveSuggestedPlaylistToLibrary(playlist) }
                                                        )
                                                    }
                                                }
                                            }

                                            // 3. More like #1
                                            if (moreLike1 != null) {
                                                item(key = "more_like_0", contentType = "more_like_row") {
                                                    Column(modifier = Modifier.fillMaxWidth()) {
                                                        Spacer(modifier = Modifier.height(26.dp))
                                                        MoreLikeAlbumSectionRow(
                                                            section = moreLike1,
                                                            onAlbumClick = { album -> viewModel.onAlbumClicked(album) }
                                                        )
                                                    }
                                                }
                                            }

                                            // 4. Related Artists For You
                                            if (feed.relatedArtists.isNotEmpty()) {
                                                item(key = "related_artists", contentType = "related_artists") {
                                                    Column(modifier = Modifier.fillMaxWidth()) {
                                                        Spacer(modifier = Modifier.height(26.dp))
                                                        RelatedArtistsRow(
                                                            artists = feed.relatedArtists,
                                                            onArtistClick = { artist -> viewModel.onArtistClicked(artist) }
                                                        )
                                                    }
                                                }
                                            }

                                            // 5. Playlists Made For You
                                            if (feed.playlistsMadeForYou.isNotEmpty()) {
                                                item(key = "playlists_made_for_you", contentType = "playlists_row") {
                                                    Column(modifier = Modifier.fillMaxWidth()) {
                                                        Spacer(modifier = Modifier.height(26.dp))
                                                        PlaylistsMadeForYouSection(
                                                            playlists = feed.playlistsMadeForYou,
                                                            onPlaylistClick = { item -> viewModel.onRecommendedPlaylistClick(item) }
                                                        )
                                                    }
                                                }
                                            }

                                            // 6. More like #2
                                            if (moreLike2 != null) {
                                                item(key = "more_like_1", contentType = "more_like_row") {
                                                    Column(modifier = Modifier.fillMaxWidth()) {
                                                        Spacer(modifier = Modifier.height(26.dp))
                                                        MoreLikeAlbumSectionRow(
                                                            section = moreLike2,
                                                            onAlbumClick = { album -> viewModel.onAlbumClicked(album) }
                                                        )
                                                    }
                                                }
                                            }

                                            // 7. Estaciones de Radio
                                            if (feed.radioStations.isNotEmpty()) {
                                                item(key = "radio_stations", contentType = "radio_stations") {
                                                    Column(modifier = Modifier.fillMaxWidth()) {
                                                        Spacer(modifier = Modifier.height(26.dp))
                                                        RadioStationsSection(
                                                            stations = feed.radioStations,
                                                            onStationClick = { station -> viewModel.onRadioStationClick(station) },
                                                            onSeeMoreClick = { viewModel.openHomeGrid(HomeGridCategory.RADIO_STATIONS) }
                                                        )
                                                    }
                                                }
                                            }

                                            // 8. Recommended Songs (YouTube Music Powered)
                                            if (feed.recommendedTracks.isNotEmpty()) {
                                                item(key = "recommended_songs", contentType = "recommended_songs") {
                                                    Column(modifier = Modifier.fillMaxWidth()) {
                                                        Spacer(modifier = Modifier.height(26.dp))
                                                        RecommendedSongsSection(
                                                            tracks = feed.recommendedTracks,
                                                            likedTrackIds = likedTrackIds,
                                                            onToggleFavorite = { track -> viewModel.toggleFavorite(track) },
                                                            onTrackClick = { track -> viewModel.onSearchTrackClicked(track) },
                                                            onPlayTrackFromList = { tracks, index -> viewModel.playQueue(tracks, index) }
                                                        )
                                                    }
                                                }
                                            }

                                            // 9. Novedades
                                            if (feed.newReleases.isNotEmpty()) {
                                                item(key = "new_releases", contentType = "new_releases") {
                                                    Column(modifier = Modifier.fillMaxWidth()) {
                                                        Spacer(modifier = Modifier.height(26.dp))
                                                        NovedadesSection(
                                                            releases = feed.newReleases,
                                                            onReleaseClick = { release -> viewModel.onNewReleaseClick(release) },
                                                            onSeeMoreClick = { viewModel.openHomeGrid(HomeGridCategory.NOVEDADES) }
                                                        )
                                                    }
                                                }
                                            }

                                            // 10. More like #3
                                            if (moreLike3 != null) {
                                                item(key = "more_like_2", contentType = "more_like_row") {
                                                    Column(modifier = Modifier.fillMaxWidth()) {
                                                        Spacer(modifier = Modifier.height(26.dp))
                                                        MoreLikeAlbumSectionRow(
                                                            section = moreLike3,
                                                            onAlbumClick = { album -> viewModel.onAlbumClicked(album) }
                                                        )
                                                    }
                                                }
                                            }

                                            // 11. Recommended Albums (YouTube Music Powered)
                                            if (feed.recommendedAlbums.isNotEmpty()) {
                                                item(key = "recommended_albums", contentType = "recommended_albums") {
                                                    Column(modifier = Modifier.fillMaxWidth()) {
                                                        Spacer(modifier = Modifier.height(26.dp))
                                                        RecommendedAlbumsSection(
                                                            albums = feed.recommendedAlbums,
                                                            onAlbumClick = { album -> viewModel.onAlbumClicked(album) }
                                                        )
                                                    }
                                                }
                                            }

                                            // 12. Discover by Genre (Dynamic Filters & Tracks from YouTube Music)
                                            if (feed.genreTags.isNotEmpty()) {
                                                item(key = "discover_by_genre", contentType = "discover_by_genre") {
                                                    Column(modifier = Modifier.fillMaxWidth()) {
                                                        Spacer(modifier = Modifier.height(26.dp))
                                                        DiscoverByGenreSection(
                                                            genres = feed.genreTags,
                                                            selectedGenre = feed.selectedGenreTag,
                                                            genreTracks = feed.genreTracks,
                                                            onGenreSelect = { genre -> viewModel.onGenreSelected(genre) },
                                                            onTrackClick = { track -> viewModel.onSearchTrackClicked(track) },
                                                            onPlayTrackFromList = { tracks, index -> viewModel.playQueue(tracks, index) }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            NavigationTab.Library -> {
                                LibraryScreen(
                                    headerTitle = libraryHeaderTitle,
                                    headerSubtitle = libraryHeaderSubtitle,
                                    selectedCategory = selectedCategory,
                                    selectedSourceType = selectedSourceType,
                                    onSelectCategory = { cat -> viewModel.selectCategory(cat) },
                                    onSelectSourceType = { src -> viewModel.selectSourceType(src) },
                                    artists = libraryArtists,
                                    albums = libraryAlbums,
                                    songs = librarySongs,
                                    recentlyAddedTracks = recentlyAddedLibraryTracks,
                                    isScanning = isScanning,
                                    likedTrackIds = likedTrackIds,
                                    currentPlayingTrackId = playbackState.currentTrack?.id,
                                    isPlaybackPlaying = playbackState.isPlaying,
                                    auroraColors = auroraColors,
                                    activeLibrarySubView = activeLibrarySubView,
                                    onOpenSubView = { subView -> viewModel.openLibrarySubView(subView) },
                                    applyStatusBarsPadding = true,
                                    onToggleFavorite = { track -> viewModel.toggleFavorite(track) },
                                    onTrackClick = { track ->
                                        val idx = librarySongs.indexOfFirst { it.id == track.id }
                                        if (idx >= 0) {
                                            viewModel.playQueue(librarySongs, idx)
                                        } else {
                                            viewModel.onSearchTrackClicked(track)
                                        }
                                    },
                                    onPlayTrackFromList = { tracks, index -> viewModel.playQueue(tracks, index) },
                                    onAlbumClick = { album -> viewModel.onAlbumClicked(album) },
                                    onArtistClick = { artist -> viewModel.onArtistClicked(artist) },
                                    onScanMediaStore = { viewModel.scanMediaStore() },
                                    onAddFolder = { uri -> viewModel.addLibraryFolder(uri) },
                                    onSongMoreClick = { track -> viewModel.openTrackOptions(track) },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .nestedScroll(scrollConnection)
                                )
                            }
                            NavigationTab.Liked -> {
                                LikedScreen(
                                    likedTracks = likedTracks,
                                    likedTrackIds = likedTrackIds,
                                    recommendations = likedRecommendations,
                                    isLoadingRecommendations = isLoadingLikedRecommendations,
                                    currentPlayingTrackId = playbackState.currentTrack?.id,
                                    isPlaybackPlaying = playbackState.isPlaying,
                                    onPlayAllClick = { viewModel.playLikedTracks() },
                                    onShuffleClick = { viewModel.shuffleLikedTracks() },
                                    onTrackClick = { track -> viewModel.playLikedTrack(track) },
                                    onToggleFavorite = { track -> viewModel.toggleFavorite(track) },
                                    onTrackMoreClick = { track -> viewModel.openTrackOptions(track) },
                                    onArtistClick = { artistName -> viewModel.openArtistByName(artistName) },
                                    onAlbumClick = { album -> viewModel.onAlbumClicked(album) },
                                    onExploreMusicClick = { viewModel.onTabSelected(NavigationTab.Home) },
                                    auroraColors = auroraColors,
                                    onPlayTrackFromList = { tracks, index -> viewModel.playLikedTracks(tracks, index) },
                                    applyStatusBarsPadding = true,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .nestedScroll(scrollConnection)
                                )
                            }
                            NavigationTab.Playlists -> {
                                if (windowSizeClass.supportsTwoPaneLayout) {
                                    var selectedInlinePlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
                                    val combinedInlinePool = remember(playlists, smartSuggestedPlaylists) {
                                        playlists + smartSuggestedPlaylists
                                    }
                                    val activeInlinePlaylist = combinedInlinePool.firstOrNull { it.id == selectedInlinePlaylistId }
                                        ?: playlists.firstOrNull()
                                        ?: smartSuggestedPlaylists.firstOrNull()
                                    val playlistsNavigator = rememberListDetailPaneScaffoldNavigator<Nothing>(
                                        scaffoldDirective = rememberAdaptivePaneScaffoldDirective()
                                    )

                                    ListDetailPaneScaffold(
                                        directive = playlistsNavigator.scaffoldDirective,
                                        value = playlistsNavigator.scaffoldValue,
                                        modifier = Modifier.fillMaxSize(),
                                        listPane = {
                                            AnimatedPane(
                                                modifier = Modifier
                                                    .preferredWidth(0.40f)
                                                    .fillMaxHeight()
                                            ) {
                                                PlaylistsScreen(
                                                    playlists = playlists,
                                                    suggestedPlaylists = smartSuggestedPlaylists,
                                                    selectedPlaylistId = activeInlinePlaylist?.id,
                                                    onPlaylistClick = { playlist ->
                                                        selectedInlinePlaylistId = playlist.id
                                                    },
                                                    onPlayPlaylistClick = { playlist ->
                                                        selectedInlinePlaylistId = playlist.id
                                                        viewModel.playPlaylistTracks(playlist)
                                                    },
                                                    onSaveSuggestedPlaylist = { playlist ->
                                                        viewModel.saveSuggestedPlaylistToLibrary(playlist)
                                                    },
                                                    onCreatePlaylistClick = { viewModel.openCreatePlaylistDialog() },
                                                    isYtmSignedIn = ytmAccountState.isSignedIn,
                                                    onImportYtmPlaylistsClick = { viewModel.openYtmPlaylistImport() },
                                                    onRequestDeletePlaylist = { playlist -> viewModel.requestDeletePlaylist(playlist) },
                                                    statusMessage = ytmImportStatusMessage,
                                                    onDismissStatusMessage = { viewModel.dismissYtmImportStatusMessage() },
                                                    auroraColors = auroraColors,
                                                    applyStatusBarsPadding = true,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .nestedScroll(scrollConnection)
                                                )
                                            }
                                        },
                                        detailPane = {
                                            AnimatedPane(
                                                modifier = Modifier
                                                    .preferredWidth(0.60f)
                                                    .fillMaxHeight()
                                            ) {
                                                if (activeInlinePlaylist != null) {
                                                    val isInlineSaved = !activeInlinePlaylist.isSmartSuggestion ||
                                                        savedPlaylistTitles.contains(activeInlinePlaylist.title.trim().lowercase())
                                                    PlaylistDetailScreen(
                                                        playlist = activeInlinePlaylist,
                                                        likedTrackIds = likedTrackIds,
                                                        currentPlayingTrackId = playbackState.currentTrack?.id,
                                                        isPlaybackPlaying = playbackState.isPlaying,
                                                        onBack = {},
                                                        showBackButton = false,
                                                        isSavedInLibrary = isInlineSaved,
                                                        onSaveToLibraryClick = {
                                                            viewModel.saveSuggestedPlaylistToLibrary(activeInlinePlaylist)
                                                        },
                                                        onPlayAllClick = { viewModel.playPlaylistTracks(activeInlinePlaylist, 0) },
                                                        onShuffleClick = { viewModel.shufflePlaylistTracks(activeInlinePlaylist) },
                                                        onTrackClick = { track ->
                                                            val idx = activeInlinePlaylist.tracks.indexOfFirst { it.id == track.id }
                                                                .takeIf { it >= 0 } ?: activeInlinePlaylist.tracks.indexOf(track)
                                                            viewModel.playPlaylistTracks(activeInlinePlaylist, if (idx >= 0) idx else 0)
                                                        },
                                                        onToggleFavorite = { track -> viewModel.toggleFavorite(track.id, fallback = track) },
                                                        onRemoveTrack = { track -> viewModel.removeTrackFromPlaylist(activeInlinePlaylist.id, track.id) },
                                                        onEditPlaylistClick = { viewModel.openCreatePlaylistDialog(activeInlinePlaylist) },
                                                        onDeletePlaylistClick = { viewModel.requestDeletePlaylist(activeInlinePlaylist) },
                                                        isYtmSignedIn = ytmAccountState.isSignedIn,
                                                        isRefreshingImportedPlaylist = isRefreshingImportedPlaylist,
                                                        onRefreshImportedPlaylistClick = { viewModel.refreshImportedPlaylist(activeInlinePlaylist) },
                                                        auroraColors = auroraColors,
                                                        onPlayTrackFromList = { tracks, index -> viewModel.playQueue(tracks, index) },
                                                        onTrackMoreClick = { track -> viewModel.openTrackOptions(track) },
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    PlaylistsEmptyDetailPane(
                                                        onCreatePlaylistClick = { viewModel.openCreatePlaylistDialog() }
                                                    )
                                                }
                                            }
                                        }
                                    )
                                } else {
                                    PlaylistsScreen(
                                        playlists = playlists,
                                        suggestedPlaylists = smartSuggestedPlaylists,
                                        onPlaylistClick = { playlist -> viewModel.openPlaylistDetail(playlist) },
                                        onPlayPlaylistClick = { playlist -> viewModel.playPlaylistTracks(playlist) },
                                        onSaveSuggestedPlaylist = { playlist -> viewModel.saveSuggestedPlaylistToLibrary(playlist) },
                                        onCreatePlaylistClick = { viewModel.openCreatePlaylistDialog() },
                                        isYtmSignedIn = ytmAccountState.isSignedIn,
                                        onImportYtmPlaylistsClick = { viewModel.openYtmPlaylistImport() },
                                        onRequestDeletePlaylist = { playlist -> viewModel.requestDeletePlaylist(playlist) },
                                        statusMessage = ytmImportStatusMessage,
                                        onDismissStatusMessage = { viewModel.dismissYtmImportStatusMessage() },
                                        auroraColors = auroraColors,
                                        applyStatusBarsPadding = true,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .nestedScroll(scrollConnection)
                                    )
                                }
                            }
                            else -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .statusBarsPadding()
                                        .padding(bottom = 180.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${selectedTab.label} section port coming up next",
                                        color = appColors.textSecondary,
                                        fontFamily = com.xperience.player.ui.theme.PlusJakartaSans,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                    }
                }

                // Fullscreen Radio Stations / Novedades Grid Layer (Shared-expansion with predictive back)
                if (effectiveGridProgress > 0.001f) {
                    val activeGridCategory = homeGridCategory ?: lastHomeGridCategory
                    if (activeGridCategory != null) {
                        val cornerRadius = ((1f - effectiveGridProgress) * 36).dp
                        val containerShape = if (cornerRadius > 0.dp) {
                            ContinuousRoundedRectangle(size = cornerRadius)
                        } else {
                            RectangleShape
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(computeOverlayZIndex(overlayStack, OverlayLayer.HOME_GRID, isHomeGridOpen))
                                .graphicsLayer {
                                    alpha = effectiveGridProgress.coerceIn(0f, 1f)
                                    translationY = (1f - effectiveGridProgress) * 220.dp.toPx()
                                    scaleX = 0.82f + 0.18f * effectiveGridProgress
                                    scaleY = 0.82f + 0.18f * effectiveGridProgress
                                    clip = cornerRadius > 0.dp
                                    shape = containerShape
                                }
                        ) {
                            RadioAndNovedadesGridScreen(
                                category = activeGridCategory,
                                radioStations = feed.allRadioStations.ifEmpty { feed.radioStations },
                                newReleases = feed.allNewReleases.ifEmpty { feed.newReleases },
                                onBack = { viewModel.closeHomeGrid() },
                                onRadioClick = { station -> viewModel.onRadioStationClick(station) },
                                onReleaseClick = { release -> viewModel.onNewReleaseClick(release) },
                                onCategoryChange = { category -> viewModel.openHomeGrid(category) },
                                appColors = appColors,
                                accentColor = animatedAccentColor,
                                enableInternalBackHandler = false,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                // Full Screen Artist Detail Layer (Shared-expansion from card to full screen with predictive back)
                // Inside layerBackdrop so MiniPlayerBar and BottomNavBar refract ArtistScreen when open!
                if (effectiveArtistProgress > 0.001f) {
                    val cornerRadius = ((1f - effectiveArtistProgress) * 36).dp
                    val containerShape = if (cornerRadius > 0.dp) {
                        ContinuousRoundedRectangle(size = cornerRadius)
                    } else {
                        RectangleShape
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(computeOverlayZIndex(overlayStack, OverlayLayer.ARTIST_DETAIL, isArtistDetailOpen))
                            .graphicsLayer {
                                alpha = effectiveArtistProgress.coerceIn(0f, 1f)
                                translationY = (1f - effectiveArtistProgress) * 220.dp.toPx()
                                scaleX = 0.82f + 0.18f * effectiveArtistProgress
                                scaleY = 0.82f + 0.18f * effectiveArtistProgress
                                clip = cornerRadius > 0.dp
                                shape = containerShape
                            }
                    ) {
                        val artistProfile = currentArtistDetail
                        if (artistProfile != null) {
                            ArtistScreen(
                                profile = artistProfile,
                                videoUri = currentArtistVideoUri,
                                isLoading = isArtistDetailLoading,
                                isBioExpanded = isArtistBioExpanded,
                                likedTrackIds = likedTrackIds,
                                ambientBackgroundColor = currentArtistAmbientColor,
                                currentPlayingTrackId = playbackState.currentTrack?.id,
                                isPlaybackPlaying = playbackState.isPlaying,
                                onBack = { viewModel.closeArtistDetail() },
                                onToggleBio = { viewModel.toggleArtistBioExpanded() },
                                onShuffleClick = { viewModel.shuffleArtistTopTracks() },
                                onMixClick = { viewModel.playArtistMix() },
                                onTrackClick = { track ->
                                    val idx = artistProfile.topTracks.indexOfFirst { it.id == track.id }
                                    if (idx >= 0) {
                                        viewModel.playQueue(artistProfile.topTracks, idx)
                                    } else {
                                        viewModel.onSearchTrackClicked(track)
                                    }
                                },
                                onToggleFavorite = { trackId -> viewModel.toggleFavorite(trackId) },
                                onTrackMoreClick = { track -> viewModel.openTrackOptions(track) },
                                onAlbumClick = { album -> viewModel.onAlbumClicked(album) },
                                onArtistClick = { artist -> viewModel.onArtistClicked(artist) },
                                onPlayTrackFromList = { tracks, index -> viewModel.playQueue(tracks, index) },
                                onOfficialVideoClick = { videoTrack -> viewModel.playArtistOfficialVideo(videoTrack) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                // Full Screen Album Detail Layer (Shared-expansion from card to full screen with predictive back)
                // Inside layerBackdrop so MiniPlayerBar and BottomNavBar refract AlbumScreen when open!
                if (effectiveAlbumProgress > 0.001f) {
                    val cornerRadius = ((1f - effectiveAlbumProgress) * 36).dp
                    val containerShape = if (cornerRadius > 0.dp) {
                        ContinuousRoundedRectangle(size = cornerRadius)
                    } else {
                        RectangleShape
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(computeOverlayZIndex(overlayStack, OverlayLayer.ALBUM_DETAIL, isAlbumDetailOpen))
                            .graphicsLayer {
                                alpha = effectiveAlbumProgress.coerceIn(0f, 1f)
                                translationY = (1f - effectiveAlbumProgress) * 220.dp.toPx()
                                scaleX = 0.82f + 0.18f * effectiveAlbumProgress
                                scaleY = 0.82f + 0.18f * effectiveAlbumProgress
                                clip = cornerRadius > 0.dp
                                shape = containerShape
                            }
                    ) {
                        val albumProfile = currentAlbumDetail
                        if (albumProfile != null) {
                            AlbumScreen(
                                profile = albumProfile,
                                isLoading = isAlbumDetailLoading,
                                likedTrackIds = likedTrackIds,
                                ambientBackgroundColor = currentAlbumAmbientColor,
                                currentPlayingTrackId = playbackState.currentTrack?.id,
                                isPlaybackPlaying = playbackState.isPlaying,
                                onBack = { viewModel.closeAlbumDetail() },
                                onPlayAllClick = { viewModel.playAlbumTracks() },
                                onShuffleClick = { viewModel.shuffleAlbumTracks() },
                                onTrackClick = { track ->
                                    val idx = albumProfile.tracks.indexOfFirst { it.id == track.id }
                                        .takeIf { it >= 0 } ?: albumProfile.tracks.indexOf(track)
                                    viewModel.playAlbumTracks(albumProfile.tracks, if (idx >= 0) idx else 0)
                                },
                                onToggleFavorite = { trackId -> viewModel.toggleFavorite(trackId) },
                                onTrackMoreClick = { track -> viewModel.openTrackOptions(track) },
                                onArtistClick = { artistName -> viewModel.openArtistByName(artistName) },
                                onAlbumClick = { album -> viewModel.onAlbumClicked(album) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                // Full Screen Playlist Detail Layer (Shared-expansion from card to full screen with predictive back)
                // Inside layerBackdrop so MiniPlayerBar and BottomNavBar refract PlaylistDetailScreen when open!
                if (effectivePlaylistProgress > 0.001f) {
                    val cornerRadius = ((1f - effectivePlaylistProgress) * 36).dp
                    val containerShape = if (cornerRadius > 0.dp) {
                        ContinuousRoundedRectangle(size = cornerRadius)
                    } else {
                        RectangleShape
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(computeOverlayZIndex(overlayStack, OverlayLayer.PLAYLIST_DETAIL, isPlaylistDetailOpen))
                            .graphicsLayer {
                                alpha = effectivePlaylistProgress.coerceIn(0f, 1f)
                                translationY = (1f - effectivePlaylistProgress) * 220.dp.toPx()
                                scaleX = 0.82f + 0.18f * effectivePlaylistProgress
                                scaleY = 0.82f + 0.18f * effectivePlaylistProgress
                                clip = cornerRadius > 0.dp
                                shape = containerShape
                            }
                            .background(appColors.background, shape = containerShape)
                    ) {
                        val playlist = currentPlaylistDetail
                        if (playlist != null) {
                            val isSaved = !playlist.isSmartSuggestion ||
                                savedPlaylistTitles.contains(playlist.title.trim().lowercase())
                            PlaylistDetailScreen(
                                playlist = playlist,
                                likedTrackIds = likedTrackIds,
                                currentPlayingTrackId = playbackState.currentTrack?.id,
                                isPlaybackPlaying = playbackState.isPlaying,
                                onBack = { viewModel.closePlaylistDetail() },
                                isSavedInLibrary = isSaved,
                                onSaveToLibraryClick = { viewModel.saveSuggestedPlaylistToLibrary(playlist) },
                                onPlayAllClick = { viewModel.playPlaylistTracks(playlist, 0) },
                                onShuffleClick = { viewModel.shufflePlaylistTracks(playlist) },
                                onTrackClick = { track ->
                                    val idx = playlist.tracks.indexOfFirst { it.id == track.id }
                                        .takeIf { it >= 0 } ?: playlist.tracks.indexOf(track)
                                    viewModel.playPlaylistTracks(playlist, if (idx >= 0) idx else 0)
                                },
                                onToggleFavorite = { track -> viewModel.toggleFavorite(track.id, fallback = track) },
                                onRemoveTrack = { track -> viewModel.removeTrackFromPlaylist(playlist.id, track.id) },
                                onEditPlaylistClick = { viewModel.openCreatePlaylistDialog(playlist) },
                                onDeletePlaylistClick = { viewModel.requestDeletePlaylist(playlist) },
                                isYtmSignedIn = ytmAccountState.isSignedIn,
                                isRefreshingImportedPlaylist = isRefreshingImportedPlaylist,
                                onRefreshImportedPlaylistClick = { viewModel.refreshImportedPlaylist(playlist) },
                                auroraColors = auroraColors,
                                onPlayTrackFromList = { tracks, index -> viewModel.playQueue(tracks, index) },
                                onTrackMoreClick = { track -> viewModel.openTrackOptions(track) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                // Search Overlay Layer (opens immediately above any active tab or detail overlay)
                if (effectiveSearchProgress > 0.001f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(computeOverlayZIndex(overlayStack, OverlayLayer.SEARCH, isSearchVisibleTarget))
                            .graphicsLayer {
                                alpha = effectiveSearchProgress.coerceIn(0f, 1f)
                                val scale = 0.94f + 0.06f * effectiveSearchProgress
                                scaleX = scale
                                scaleY = scale
                            }
                            .background(appColors.background)
                    ) {
                        SearchScreen(
                            searchQuery = searchQuery,
                            searchFilter = searchFilter,
                            searchResults = searchResults,
                            isSearching = isSearching,
                            recentSearches = recentSearches,
                            onSearchQueryChange = { q ->
                                queryState = TextFieldValue(q)
                                viewModel.onSearchQueryChange(q)
                            },
                            onSearchFilterChange = { f -> viewModel.onSearchFilterChange(f) },
                            onRecentSearchClick = { q ->
                                queryState = TextFieldValue(q)
                                viewModel.selectRecentSearch(q)
                            },
                            onRemoveRecentSearch = { q -> viewModel.removeRecentSearch(q) },
                            onClearRecentSearches = { viewModel.clearRecentSearches() },
                            onGenreClick = { genre ->
                                queryState = TextFieldValue(genre.name)
                                viewModel.selectGenreSearch(genre.name)
                            },
                            onTrackClick = { track -> viewModel.onSearchTrackClicked(track) },
                            onAlbumClick = { album -> viewModel.onAlbumClicked(album) },
                            onArtistClick = { artist -> viewModel.onArtistClicked(artist) },
                            onDismissSearch = { exitSearch() },
                            likedTrackIds = likedTrackIds,
                            onToggleFavorite = { track -> viewModel.toggleFavorite(track) },
                            auroraColors = auroraColors,
                            currentPlayingTrackId = playbackState.currentTrack?.id,
                            isPlaybackPlaying = playbackState.isPlaying,
                            onTrackMoreClick = { track -> viewModel.openTrackOptions(track) },
                            onPlayTrackFromList = { tracks, index -> viewModel.playQueue(tracks, index) },
                            moodsAndGenresFeed = moodsAndGenresFeed,
                            selectedMoodCategory = selectedMoodCategory,
                            moodDetailResult = moodDetailResult,
                            isLoadingMoods = isLoadingMoods,
                            isLoadingMoodDetail = isLoadingMoodDetail,
                            onMoodCategoryClick = { category -> viewModel.selectMoodCategory(category) },
                            onClearMoodCategory = { viewModel.clearMoodCategory() },
                            onMoodPlaylistClick = { playlist -> viewModel.onRecommendedPlaylistClick(playlist) },
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(scrollConnection)
                        )
                    }
                }
            }

            if (usesTopDesktopChrome) {
                val isHomeScrolled by remember(selectedTab, homeListState) {
                    derivedStateOf {
                        selectedTab == NavigationTab.Home &&
                            (homeListState.firstVisibleItemIndex > 0 || homeListState.firstVisibleItemScrollOffset > 12)
                    }
                }
                val topScrimAlpha by animateFloatAsState(
                    targetValue = when {
                        overlayStack.isNotEmpty() -> if (isDarkTheme) 0.44f else 0.54f
                        selectedTab != NavigationTab.Home -> if (isDarkTheme) 0.34f else 0.42f
                        isHomeScrolled -> if (isDarkTheme) 0.78f else 0.84f
                        else -> if (isDarkTheme) 0.44f else 0.54f
                    },
                    animationSpec = tween(durationMillis = 220),
                    label = "desktopTopScrimAlpha"
                )
                val midScrimAlpha by animateFloatAsState(
                    targetValue = when {
                        overlayStack.isNotEmpty() -> if (isDarkTheme) 0.18f else 0.24f
                        selectedTab != NavigationTab.Home -> if (isDarkTheme) 0.14f else 0.18f
                        isHomeScrolled -> if (isDarkTheme) 0.36f else 0.42f
                        else -> if (isDarkTheme) 0.18f else 0.24f
                    },
                    animationSpec = tween(durationMillis = 220),
                    label = "desktopTopScrimMidAlpha"
                )

                // Soft top readability gradient from y=0 across the status bar behind DesktopTopNavBar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(statusBarTopDp + topChromePadding + 24.dp)
                        .align(Alignment.TopCenter)
                        .graphicsLayer {
                            alpha = (1f - playerExpandProgress * 2f).coerceIn(0f, 1f)
                        }
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to appColors.background.copy(alpha = topScrimAlpha),
                                    0.56f to appColors.background.copy(alpha = midScrimAlpha),
                                    1.0f to Color.Transparent
                                )
                            )
                        )
                )

                DesktopTopNavBar(
                    navigationItems = navigationTabs,
                    selectedTab = selectedTab,
                    onTabSelected = selectTabWithReset,
                    onBrandClick = {
                        if (isSearchActive || searchVisualOverride != null) {
                            exitSearch()
                        }
                        if (isAlbumDetailOpen) viewModel.closeAlbumDetail()
                        if (isArtistDetailOpen) viewModel.closeArtistDetail()
                        if (isPlaylistDetailOpen) viewModel.closePlaylistDetail()
                        if (isHomeGridOpen) viewModel.closeHomeGrid()
                        viewModel.onTabSelected(NavigationTab.Home)
                    },
                    playbackState = playbackState,
                    onMiniPlayerClick = {
                        if (playbackState.currentTrack != null) {
                            isNowPlayingSidebarOpen = !isNowPlayingSidebarOpen
                        }
                    },
                    onTogglePlayPause = { viewModel.onTogglePlayPause() },
                    onSkipNext = { viewModel.onSkipNext() },
                    onSkipPrevious = { viewModel.onSkipPrevious() },
                    onSeek = { frac -> viewModel.seekTo(frac) },
                    onOpenSettings = { viewModel.openSettings() },
                    isNowPlayingSidebarOpen = showNowPlayingSidebar,
                    onToggleNowPlayingSidebar = {
                        isNowPlayingSidebarOpen = !isNowPlayingSidebarOpen
                    },
                    playbackPositionMsFlow = viewModel.playbackPositionMs,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                        .onSizeChanged { desktopTopBarHeightPx = it.height }
                        .graphicsLayer {
                            val progress = playerExpandProgress.coerceIn(0f, 1f)
                            translationY = -progress * 120.dp.toPx()
                            alpha = (1f - progress * 2f).coerceIn(0f, 1f)
                        }
                )
            } else {
                // Fixed Top-Right Settings Button pinned on screen with Convx LiquidGlass (only in catalog mode)
                if (!isSearchActive &&
                    effectiveSearchProgress < 0.05f &&
                    !shouldHideSettingsButton(selectedTab, activeLibrarySubView) &&
                    effectiveGridProgress < 0.05f &&
                    effectiveArtistProgress < 0.05f &&
                    effectiveAlbumProgress < 0.05f &&
                    effectivePlaylistProgress < 0.05f
                ) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(top = 10.dp, end = 18.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CastButton(
                            castState = castState,
                            onClick = { viewModel.onCastButtonClicked() },
                            availableRouteCount = castRoutes.size,
                            style = CastButtonStyle.GLASS_CIRCLE,
                            iconSize = 20.dp,
                            appColors = appColors
                        )

                        GlassCircleButton(
                            onClick = { viewModel.openSettings() },
                            size = 42.dp
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = appColors.glassTextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Active Chromecast Floating Mini Banner above bottom navigation
                if (castState.isActiveSession && !isPlayerExpanded && !isAmbientModeOpen) {
                    CastMiniBanner(
                        castState = castState,
                        onOpenControls = { viewModel.openCastControls() },
                        onTogglePlayPause = { viewModel.onTogglePlayPause() },
                        accentColor = animatedAccentColor,
                        appColors = appColors,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .widthIn(max = 520.dp)
                            .padding(horizontal = 16.dp)
                            .navigationBarsPadding()
                            .padding(bottom = if (playbackState.currentTrack != null) 144.dp else 84.dp)
                            .graphicsLayer {
                                val progress = playerExpandProgress.coerceIn(0f, 1f)
                                alpha = (1f - progress * 2f).coerceIn(0f, 1f)
                            }
                    )
                }

                // Floating LiquidGlass Navigation & MiniPlayer Layer (Convx Apple Music style)
                AppFloatingNavBar(
                    navigationItems = navigationTabs,
                    selectedTab = selectedTab,
                    onTabSelected = selectTabWithReset,
                    scrollConnection = scrollConnection,
                    playbackState = playbackState,
                    onMiniPlayerClick = { viewModel.openPlayer() },
                    onTogglePlayPause = { viewModel.onTogglePlayPause() },
                    onSkipNext = { viewModel.onSkipNext() },
                    onSkipPrevious = { viewModel.onSkipPrevious() },
                    onSeek = { frac -> viewModel.seekTo(frac) },
                    playbackPositionMsFlow = viewModel.playbackPositionMs,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .widthIn(max = 520.dp)
                        .padding(horizontal = 16.dp)
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp)
                        .graphicsLayer {
                            val progress = playerExpandProgress.coerceIn(0f, 1f)
                            translationY = progress * 300.dp.toPx()
                            alpha = (1f - progress * 2f).coerceIn(0f, 1f)
                        }
                )
            }
                }

                if (usesTopDesktopChrome) {
                    AnimatedVisibility(
                        visible = showNowPlayingSidebar,
                        enter = expandHorizontally(
                            expandFrom = Alignment.Start,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) + fadeIn(animationSpec = tween(durationMillis = 220)),
                        exit = shrinkHorizontally(
                            shrinkTowards = Alignment.Start,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) + fadeOut(animationSpec = tween(durationMillis = 180))
                    ) {
                        NowPlayingSidebar(
                            playbackState = playbackState,
                            isLiked = isLiked,
                            onCollapse = { isNowPlayingSidebarOpen = false },
                            onExpandFullscreen = { viewModel.openPlayer() },
                            onTogglePlayPause = { viewModel.onTogglePlayPause() },
                            onSkipNext = { viewModel.onSkipNext() },
                            onSkipPrevious = { viewModel.onSkipPrevious() },
                            onSeekToFraction = { fraction -> viewModel.seekTo(fraction) },
                            onToggleFavorite = { trackId -> viewModel.toggleFavorite(trackId) },
                            selectedTab = sidebarTab,
                            onSelectTab = { sidebarTab = it },
                            onMoveQueueItem = { fromIndex, toIndex -> viewModel.moveQueueItem(fromIndex, toIndex) },
                            onPlayQueueIndex = { index -> viewModel.playQueueIndex(index) },
                            selectedVisualizer = selectedVisualizer,
                            availableVisualizers = availableVisualizers,
                            onSelectVisualizer = { viz -> viewModel.selectVisualizer(viz) },
                            onImportVisualizer = { content, fileName -> viewModel.importVisualizer(content, fileName) },
                            onDeleteVisualizer = { id -> viewModel.deleteVisualizer(id) },
                            lyrics = currentLyrics,
                            lyricsProviderName = currentLyricsProviderName,
                            lyricsProviders = lyricsProviders,
                            canvasArtwork = currentCanvasArtwork,
                            onImportLrc = { content -> viewModel.importLocalLrc(content) },
                            onMoveLyricsProviderPriority = { id, delta -> viewModel.moveLyricsProviderPriority(id, delta) },
                            onSetLyricsProviderEnabled = { id, enabled -> viewModel.setLyricsProviderEnabled(id, enabled) },
                            onDownloadTrack = { track -> viewModel.openDownloadModal(track) },
                            onAddToPlaylist = { track -> viewModel.openAddToPlaylist(track) },
                            onToggleShuffle = { viewModel.onToggleShuffle() },
                            onCycleRepeatMode = { viewModel.onCycleRepeatMode() },
                            onArtistClick = { artistName -> viewModel.openArtistByName(artistName) },
                            outputDeviceName = outputDeviceName,
                            onOpenOutputSwitcher = {
                                val act = context as? Activity
                                if (act != null) {
                                    viewModel.onOpenOutputSwitcher(act)
                                }
                            },
                            onOpenAmbientMode = { viewModel.openAmbientMode() },
                            onSetAsRingtone = { track -> viewModel.openRingtoneEditor(track) },
                            castState = castState,
                            castRouteCount = castRoutes.size,
                            onOpenCastPicker = { viewModel.onCastButtonClicked() },
                            playbackPositionMsFlow = viewModel.playbackPositionMs,
                            modifier = Modifier
                                .width(NowPlayingSidebarDefaults.Width)
                                .fillMaxHeight()
                        )
                    }
                }
            }

            // Collapse NowPlayingSidebar on Back only when no overlay, search, Library sub-view,
            // fullscreen player, or modal sheet is open so predictive back on overlays wins cleanly.
            BackHandler(
                enabled = showNowPlayingSidebar &&
                    overlayStack.isEmpty() &&
                    !hasLibraryBackTarget &&
                    searchVisualOverride != true &&
                    !isPlayerExpanded &&
                    !isAmbientModeOpen &&
                    !isCastPickerOpen &&
                    !isCastControlsOpen &&
                    !isSettingsOpen &&
                    !isYtmLoginSheetOpen &&
                    !isDownloadModalOpen &&
                    !isCreatePlaylistDialogOpen &&
                    !updateUiState.isDialogOpen &&
                    trackForAddToPlaylist == null &&
                    trackForOptionsMenu == null &&
                    trackForRingtoneEditor == null
            ) {
                isNowPlayingSidebarOpen = false
            }

            // Ordered Back / Predictive Back Handlers placed AFTER AppFloatingNavBar so top overlays,
            // Search mood-detail views, and Search dismissal strictly win LIFO Back dispatch.
            PredictiveBackHandler(
                enabled = hasLibraryBackTarget &&
                    overlayStack.isEmpty() &&
                    searchVisualOverride != true &&
                    !isPlayerExpanded
            ) { progressFlow ->
                try {
                    progressFlow.collect { }
                    viewModel.handleLibraryBackNavigation()
                } catch (e: CancellationException) {
                    throw e
                }
            }

            PredictiveBackHandler(
                enabled = isHomeGridTop && !isPlayerExpanded
            ) { progressFlow ->
                try {
                    progressFlow.collect { backEvent ->
                        predictiveBackProgress = backEvent.progress
                    }
                    predictiveBackProgress = 0f
                    viewModel.closeHomeGrid()
                } catch (_: CancellationException) {
                    predictiveBackProgress = 0f
                }
            }

            PredictiveBackHandler(
                enabled = isPlaylistTop && !isPlayerExpanded
            ) { progressFlow ->
                try {
                    progressFlow.collect { backEvent ->
                        predictiveBackProgress = backEvent.progress
                    }
                    predictiveBackProgress = 0f
                    viewModel.closePlaylistDetail()
                } catch (_: CancellationException) {
                    predictiveBackProgress = 0f
                }
            }

            PredictiveBackHandler(
                enabled = isArtistTop && !isPlayerExpanded
            ) { progressFlow ->
                try {
                    progressFlow.collect { backEvent ->
                        predictiveBackProgress = backEvent.progress
                    }
                    predictiveBackProgress = 0f
                    viewModel.closeArtistDetail()
                } catch (_: CancellationException) {
                    predictiveBackProgress = 0f
                }
            }

            PredictiveBackHandler(
                enabled = isAlbumTop && !isPlayerExpanded
            ) { progressFlow ->
                try {
                    progressFlow.collect { backEvent ->
                        predictiveBackProgress = backEvent.progress
                    }
                    predictiveBackProgress = 0f
                    viewModel.closeAlbumDetail()
                } catch (_: CancellationException) {
                    predictiveBackProgress = 0f
                }
            }

            PredictiveBackHandler(
                enabled = isSearchTop &&
                    selectedMoodCategory == null &&
                    !searchKeyboardActive &&
                    searchVisualOverride != false &&
                    !isPlayerExpanded
            ) { progressFlow ->
                try {
                    progressFlow.collect { backEvent ->
                        predictiveBackProgress = backEvent.progress
                    }
                    predictiveBackProgress = 0f
                    exitSearch()
                } catch (_: CancellationException) {
                    predictiveBackProgress = 0f
                }
            }

            BackHandler(
                enabled = isSearchTop &&
                    selectedMoodCategory != null &&
                    !searchKeyboardActive &&
                    searchVisualOverride != false &&
                    !isPlayerExpanded
            ) {
                viewModel.clearMoodCategory()
            }

            // Full Screen Now Playing Layer (Surges and expands smoothly over catalog)
            if (playerExpandProgress > 0.001f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = playerExpandProgress
                            translationY = (1f - playerExpandProgress) * 450.dp.toPx()
                            scaleX = 0.88f + 0.12f * playerExpandProgress
                            scaleY = 0.88f + 0.12f * playerExpandProgress
                        }
                ) {
                    NowPlayingScreen(
                        playbackState = playbackState,
                        isLiked = isLiked,
                        onClose = { viewModel.closePlayer() },
                        onTogglePlayPause = { viewModel.onTogglePlayPause() },
                        onSkipNext = { viewModel.onSkipNext() },
                        onSkipPrevious = { viewModel.onSkipPrevious() },
                        onSeekToFraction = { fraction -> viewModel.seekTo(fraction) },
                        onToggleFavorite = { trackId -> viewModel.toggleFavorite(trackId) },
                        onTrackSelect = { track -> viewModel.onTrackClicked(track) },
                        onMoveQueueItem = { fromIndex, toIndex -> viewModel.moveQueueItem(fromIndex, toIndex) },
                        onPlayQueueIndex = { index -> viewModel.playQueueIndex(index) },
                        selectedVisualizer = selectedVisualizer,
                        availableVisualizers = availableVisualizers,
                        onSelectVisualizer = { viz -> viewModel.selectVisualizer(viz) },
                        onImportVisualizer = { content, fileName -> viewModel.importVisualizer(content, fileName) },
                        onDeleteVisualizer = { id -> viewModel.deleteVisualizer(id) },
                        lyrics = currentLyrics,
                        lyricsProviderName = currentLyricsProviderName,
                        lyricsProviders = lyricsProviders,
                        canvasArtwork = currentCanvasArtwork,
                        musicVideo = currentMusicVideo,
                        isResolvingMusicVideo = isResolvingMusicVideo,
                        playerMediaMode = playerMediaMode,
                        onSelectPlayerMediaMode = { mode -> viewModel.setPlayerMediaMode(mode) },
                        onMusicVideoPlaybackError = { viewModel.retryMusicVideoStream(automatic = true) },
                        onImportLrc = { content -> viewModel.importLocalLrc(content) },
                        onMoveLyricsProviderPriority = { id, delta -> viewModel.moveLyricsProviderPriority(id, delta) },
                        onSetLyricsProviderEnabled = { id, enabled -> viewModel.setLyricsProviderEnabled(id, enabled) },
                        onDownloadTrack = { track -> viewModel.openDownloadModal(track) },
                        onAddToPlaylist = { track -> viewModel.openAddToPlaylist(track) },
                        onToggleShuffle = { viewModel.onToggleShuffle() },
                        onCycleRepeatMode = { viewModel.onCycleRepeatMode() },
                        outputDeviceName = outputDeviceName,
                        onOpenOutputSwitcher = {
                            val act = context as? Activity
                            if (act != null) {
                                viewModel.onOpenOutputSwitcher(act)
                            }
                        },
                        onOpenAmbientMode = { viewModel.openAmbientMode() },
                        onSetAsRingtone = { track -> viewModel.openRingtoneEditor(track) },
                        castState = castState,
                        castRouteCount = castRoutes.size,
                        onOpenCastPicker = { viewModel.onCastButtonClicked() },
                        playbackPositionMsFlow = viewModel.playbackPositionMs
                    )
                }
            }

            // Full Screen Landscape Ambient Mode Overlay
            AnimatedVisibility(
                visible = isAmbientModeOpen && playbackState.currentTrack != null,
                enter = fadeIn(animationSpec = tween(durationMillis = 260)),
                exit = fadeOut(animationSpec = tween(durationMillis = 220))
            ) {
                AmbientModeScreen(
                    state = AmbientModeUiState(
                        playbackState = playbackState,
                        isLiked = isLiked,
                        lyrics = currentLyrics,
                        lyricsProviderName = currentLyricsProviderName,
                        canvasArtwork = currentCanvasArtwork,
                        selectedVisualizer = selectedVisualizer,
                        availableVisualizers = availableVisualizers,
                        auroraColors = auroraColors,
                        outputDeviceName = outputDeviceName,
                        isCastConnected = castState.isConnected,
                        castDeviceName = castState.deviceName
                    ),
                    actions = AmbientModeActions(
                        onExit = { viewModel.closeAmbientMode() },
                        onTogglePlayPause = { viewModel.onTogglePlayPause() },
                        onSkipNext = { viewModel.onSkipNext() },
                        onSkipPrevious = { viewModel.onSkipPrevious() },
                        onSeekToFraction = { fraction -> viewModel.seekTo(fraction) },
                        onToggleFavorite = { trackId -> viewModel.toggleFavorite(trackId) },
                        onToggleShuffle = { viewModel.onToggleShuffle() },
                        onCycleRepeatMode = { viewModel.onCycleRepeatMode() },
                        onSelectVisualizer = { viz -> viewModel.selectVisualizer(viz) },
                        onOpenCastPicker = { viewModel.onCastButtonClicked() },
                        onPlayQueueIndex = { index -> viewModel.playQueueIndex(index) }
                    ),
                    castButtonContent = {
                        CastButton(
                            castState = castState,
                            onClick = { viewModel.onCastButtonClicked() },
                            availableRouteCount = castRoutes.size,
                            style = CastButtonStyle.ICON,
                            iconSize = 18.dp,
                            accentColor = animatedAccentColor,
                            appColors = DarkThemeColors,
                            modifier = Modifier.size(34.dp)
                        )
                    },
                    playbackPositionMsFlow = viewModel.playbackPositionMs,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Active Chromecast Floating Mini Banner on Tablet / Desktop layouts
            if (usesTopDesktopChrome && castState.isActiveSession && !isPlayerExpanded && !isAmbientModeOpen) {
                CastMiniBanner(
                    castState = castState,
                    onOpenControls = { viewModel.openCastControls() },
                    onTogglePlayPause = { viewModel.onTogglePlayPause() },
                    accentColor = animatedAccentColor,
                    appColors = appColors,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .widthIn(max = 460.dp)
                        .padding(horizontal = 20.dp)
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp)
                )
            }

            // Settings Sheet
            SettingsSheet(
                isOpen = isSettingsOpen,
                onDismiss = { viewModel.closeSettings() },
                selectedThemeMode = themeMode,
                onSelectThemeMode = { mode -> viewModel.selectThemeMode(mode) },
                selectedAccentOption = accentColorOption,
                dynamicArtworkColor = dynamicArtworkColor,
                onSelectAccentOption = { opt -> viewModel.selectAccentColorOption(opt) },
                glassSettings = liquidGlassSettings,
                onUpdateBlurRadius = { r -> viewModel.updateBlurRadius(r) },
                onUpdateRefractionStrength = { s -> viewModel.updateRefractionStrength(s) },
                onUpdateSurfaceTintOpacity = { o -> viewModel.updateSurfaceTintOpacity(o) },
                onUpdateSpecularMeniscus = { m -> viewModel.updateSpecularMeniscus(m) },
                onResetLiquidGlassSettings = { viewModel.resetLiquidGlassSettings() },
                folders = libraryFolders,
                isScanning = isScanning,
                onScanMediaStore = { viewModel.scanMediaStore() },
                onAddFolder = { uri -> viewModel.addLibraryFolder(uri) },
                onRemoveFolder = { folderUri -> viewModel.removeLibraryFolder(folderUri) },
                downloadOptions = downloadOptions,
                onUpdateDownloadOptions = { opts -> viewModel.updateDownloadOptions(opts) },
                ytmAccountState = ytmAccountState,
                isSyncingYtmAccount = isSyncingYtmAccount,
                onSignInYtmClick = {
                    viewModel.closeSettings()
                    viewModel.openYtmLogin()
                },
                onSignOutYtmClick = { viewModel.signOutYtm() },
                onForceSyncYtmClick = { viewModel.forceSyncYtm() },
                onImportYtmPlaylistsClick = {
                    viewModel.closeSettings()
                    viewModel.openYtmPlaylistImport()
                },
                autoCheckUpdates = autoCheckUpdates,
                onToggleAutoCheckUpdates = { enabled -> viewModel.setAutoCheckUpdates(enabled) },
                updateUiState = updateUiState,
                onCheckForUpdatesClick = { viewModel.checkForUpdatesManually() }
            )

            // App Auto-Update Modal Dialog
            UpdateDialog(
                uiState = updateUiState,
                onDismiss = { viewModel.dismissUpdateDialog() },
                onStartUpdate = { viewModel.startUpdateDownloadAndInstall() },
                onCancelDownload = { viewModel.cancelUpdateDownload() },
                onOpenInstallPermissionSettings = {
                    unknownSourcesLauncher.launch(
                        ApkInstaller.createUnknownAppSourcesSettingsIntent(context)
                    )
                },
                onRetryLaunchInstaller = { viewModel.retryLaunchVerifiedInstaller() }
            )

            // Fullscreen YouTube Music Account WebView Login Sheet
            YouTubeLoginSheet(
                isOpen = isYtmLoginSheetOpen,
                onDismiss = { viewModel.closeYtmLogin() },
                onCredentialsCaptured = { cookie, visitorData, dataSyncId ->
                    viewModel.onYtmCredentialsCaptured(cookie, visitorData, dataSyncId)
                },
                isValidating = isSyncingYtmAccount,
                validationError = ytmLoginError
            )

            // Import YouTube Music Playlists Sheet
            ImportYouTubePlaylistsSheet(
                isOpen = isYtmPlaylistImportOpen,
                isLoading = isLoadingYtmLibraryPlaylists,
                libraryPlaylists = ytmLibraryPlaylists,
                selectedIds = selectedYtmPlaylistIds,
                existingLocalPlaylists = playlists,
                isImporting = isImportingYtmPlaylists,
                progressText = ytmImportProgressText,
                errorMessage = ytmImportError,
                onDismiss = { viewModel.closeYtmPlaylistImport() },
                onRetry = { viewModel.loadYtmLibraryPlaylists() },
                onToggleSelection = { id -> viewModel.toggleYtmPlaylistSelection(id) },
                onSelectAll = { viewModel.selectAllYtmPlaylists() },
                onClearSelection = { viewModel.clearYtmPlaylistSelection() },
                onImportClick = { viewModel.importSelectedYtmPlaylists() }
            )

            // Local-Only Remove / Delete Playlist Confirmation Dialog
            RemovePlaylistDialog(
                playlist = playlistPendingDelete,
                onDismiss = { viewModel.cancelDeletePlaylist() },
                onConfirm = { viewModel.confirmDeletePlaylist() }
            )

            // Download Confirmation & Source Resolution Modal
            playbackState.currentTrack?.let { currentTrack ->
                DownloadConfirmationModal(
                    isOpen = isDownloadModalOpen,
                    track = currentTrack,
                    candidates = downloadCandidates,
                    isSearching = isSearchingCandidates,
                    progressState = downloadProgressState,
                    defaultFormat = downloadOptions.defaultFormat,
                    onDismiss = { viewModel.closeDownloadModal() },
                    onConfirmDownload = { candidate, format ->
                        viewModel.startDownload(currentTrack, candidate, format)
                    }
                )
            }

            // Create & Edit Playlist Dialog
            CreatePlaylistDialog(
                isOpen = isCreatePlaylistDialogOpen,
                playlistToEdit = playlistToEdit,
                onDismiss = { viewModel.closeCreatePlaylistDialog() },
                onConfirm = { title, description, customCoverPath ->
                    viewModel.savePlaylist(
                        title = title,
                        description = description,
                        customCoverPath = customCoverPath.ifBlank { null }
                    )
                }
            )

            // Add To Playlist Bottom Sheet
            AddToPlaylistSheet(
                track = trackForAddToPlaylist,
                playlists = playlists,
                isOpen = trackForAddToPlaylist != null,
                onDismiss = { viewModel.closeAddToPlaylist() },
                onSelectPlaylist = { playlist, track ->
                    viewModel.addTrackToPlaylist(playlist, track)
                    viewModel.closeAddToPlaylist()
                },
                onCreateNewPlaylistWithTrack = { track ->
                    viewModel.openCreatePlaylistDialog()
                }
            )

            // Universal Track Options Sheet
            TrackOptionsMenuSheet(
                track = trackForOptionsMenu,
                isLiked = trackForOptionsMenu?.let { likedTrackIds.contains(it.id) } ?: false,
                isOpen = trackForOptionsMenu != null,
                onDismiss = { viewModel.closeTrackOptions() },
                onAddToPlaylist = { track ->
                    viewModel.closeTrackOptions()
                    viewModel.openAddToPlaylist(track)
                },
                onDownload = { track ->
                    viewModel.closeTrackOptions()
                    viewModel.openDownloadModal(track)
                },
                onToggleFavorite = { track ->
                    viewModel.toggleFavorite(track.id, fallback = track)
                },
                onSetAsRingtone = { track ->
                    viewModel.closeTrackOptions()
                    viewModel.openRingtoneEditor(track)
                }
            )

            // Set as Ringtone Audio Trimmer & Clip Manager Sheet
            RingtoneEditorSheet(
                track = trackForRingtoneEditor,
                isOpen = trackForRingtoneEditor != null,
                onDismiss = { viewModel.closeRingtoneEditor() },
                onPauseMainPlayback = { viewModel.pausePlayback() },
                resolveAudioSource = { track -> viewModel.resolveLocalAudioUriForRingtone(track) }
            )

            // Custom Chromecast Device Picker Sheet (with Local Audio Output Switcher bridge)
            CastDevicePickerSheet(
                isOpen = isCastPickerOpen,
                onDismiss = { viewModel.closeCastPicker() },
                castState = castState,
                routes = castRoutes,
                isScanning = isCastScanning,
                onStartDiscovery = { viewModel.castController.startActiveDiscovery() },
                onStopDiscovery = { viewModel.castController.stopActiveDiscovery() },
                onSelectRoute = { deviceId ->
                    viewModel.castController.selectRoute(deviceId)
                    viewModel.closeCastPicker()
                },
                onDisconnect = { viewModel.castController.disconnect() },
                onSetVolume = { volume -> viewModel.castController.setVolume(volume) },
                onToggleMute = { viewModel.castController.toggleMute() },
                onOpenExpandedControls = { viewModel.openCastControls() },
                onClearError = { viewModel.castController.clearError() },
                currentLocalOutputName = outputDeviceName,
                onOpenSystemAudioOutput = {
                    val act = context as? Activity
                    if (act != null) {
                        viewModel.closeCastPicker()
                        viewModel.onOpenOutputSwitcher(act)
                    }
                },
                accentColor = animatedAccentColor,
                appColors = appColors
            )

            // Custom Expanded Chromecast Remote Control Sheet
            CastExpandedControlsSheet(
                isOpen = isCastControlsOpen,
                onDismiss = { viewModel.closeCastControls() },
                castState = castState,
                onTogglePlayPause = { viewModel.castController.togglePlayPause() },
                onSkipNext = { viewModel.castController.skipNext() },
                onSkipPrevious = { viewModel.castController.skipPrevious() },
                onSeekTo = { positionMs -> viewModel.castController.seekTo(positionMs) },
                onSetVolume = { volume -> viewModel.castController.setVolume(volume) },
                onToggleMute = { viewModel.castController.toggleMute() },
                onToggleShuffle = { viewModel.castController.toggleShuffle() },
                onCycleRepeatMode = { viewModel.castController.cycleRepeatMode() },
                onJumpToQueueIndex = { index -> viewModel.castController.jumpToQueueIndex(index) },
                onDisconnect = { viewModel.castController.disconnect() },
                onOpenDevicePicker = { viewModel.openCastPicker() },
                accentColor = animatedAccentColor,
                appColors = appColors
            )
        }
        }
    }
}

@Composable
private fun HomeAdaptivePreviewContent() {
    val windowSizeClass = LocalAppWindowSizeClass.current
    val appColors = LocalAppColors.current
    val useDesktopBar = windowSizeClass.usesTopDesktopChrome
    val scrollConnection = rememberFloatingTabBarScrollConnection()
    val tabs = remember {
        listOf(NavigationTab.Home, NavigationTab.Library, NavigationTab.Liked, NavigationTab.Playlists)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.background)
    ) {
        HomeAuroraBackground(
            auroraColors = com.xperience.player.ui.theme.DEFAULT_AURORA_COLORS,
            isDark = appColors.isDark,
            scrollOffsetPxProvider = { 0 },
            modifier = Modifier.fillMaxSize()
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = LocalTopChromePadding.current)
        ) {
            HeaderBar(
                showBrandBadge = !useDesktopBar,
                onRefreshClick = if (useDesktopBar) ({}) else null
            )
            Spacer(modifier = Modifier.height(16.dp))
            RecommendedSongsSection(
                tracks = AdaptivePreviewFixtures.tracks,
                likedTrackIds = setOf("ytm_paparazzi"),
                onTrackClick = {}
            )
            Spacer(modifier = Modifier.height(24.dp))
            DiscoverByGenreSection(
                genres = listOf("Pop", "Electropop", "Indie", "Dance", "R&B", "Synthpop"),
                selectedGenre = "Pop",
                genreTracks = AdaptivePreviewFixtures.tracks,
                onGenreSelect = {},
                onTrackClick = {}
            )
            Spacer(modifier = Modifier.height(LocalBottomChromePadding.current))
        }

        if (useDesktopBar) {
            DesktopTopNavBar(
                navigationItems = tabs,
                selectedTab = NavigationTab.Home,
                onTabSelected = {},
                onBrandClick = {},
                playbackState = AdaptivePreviewFixtures.playbackState,
                onMiniPlayerClick = {},
                onTogglePlayPause = {},
                onSkipNext = {},
                onSkipPrevious = {},
                onSeek = {},
                onOpenSettings = {},
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            )
        } else {
            AppFloatingNavBar(
                navigationItems = tabs,
                selectedTab = NavigationTab.Home,
                onTabSelected = {},
                scrollConnection = scrollConnection,
                playbackState = AdaptivePreviewFixtures.playbackState,
                onMiniPlayerClick = {},
                onTogglePlayPause = {},
                onSkipNext = {},
                onSkipPrevious = {},
                onSeek = {},
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 520.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}

@Preview(name = "Phone (360x800)", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
private fun HomeScreenPhonePreview() {
    AdaptivePreviewSurface(widthDp = 360, heightDp = 800) {
        HomeAdaptivePreviewContent()
    }
}

@Preview(name = "Tablet Portrait (800x1280)", widthDp = 800, heightDp = 1280, showBackground = true)
@Composable
private fun HomeScreenTabletPortraitPreview() {
    AdaptivePreviewSurface(widthDp = 800, heightDp = 1280) {
        HomeAdaptivePreviewContent()
    }
}

@Preview(name = "Tablet Landscape (1280x800)", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
private fun HomeScreenTabletLandscapePreview() {
    AdaptivePreviewSurface(widthDp = 1280, heightDp = 800) {
        HomeAdaptivePreviewContent()
    }
}
