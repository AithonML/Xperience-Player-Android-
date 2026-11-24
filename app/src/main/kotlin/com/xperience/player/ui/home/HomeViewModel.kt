package com.xperience.player.ui.home

import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xperience.player.data.FavoritesRepository
import com.xperience.player.data.PlaylistRepository
import com.xperience.player.data.VisualizerRepository
import com.xperience.player.domain.model.AlbumDetailProfile
import com.xperience.player.domain.model.AlbumItem
import com.xperience.player.domain.model.ArtistDetailProfile
import com.xperience.player.domain.model.ArtistItem
import com.xperience.player.domain.model.GenreCatalog
import com.xperience.player.domain.model.HomeDiscoveryFeed
import com.xperience.player.domain.model.NewReleaseItem
import com.xperience.player.domain.model.PlaybackState
import com.xperience.player.domain.model.RadioStationItem
import com.xperience.player.domain.model.RecommendedPlaylistItem
import com.xperience.player.domain.model.TopPickItem
import com.xperience.player.domain.model.TopPickKind
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.domain.model.UserPlaylist
import com.xperience.player.domain.model.YouTubeMusicLibraryPlaylist
import com.xperience.player.domain.model.YtmAccountState
import com.xperience.player.domain.model.YtmMoodCategory
import com.xperience.player.domain.model.YtmMoodCategoryDetail
import com.xperience.player.domain.model.YtmMoodSection
import com.xperience.player.domain.model.YtmMoodsAndGenresFeed
import com.xperience.player.domain.model.canvas.CanvasArtworkResult
import com.xperience.player.domain.model.download.DownloadAudioFormat
import com.xperience.player.domain.model.download.DownloadMetadataOptions
import com.xperience.player.domain.model.download.DownloadProgressState
import com.xperience.player.domain.model.download.TrackDownloadCandidate
import com.xperience.player.domain.model.download.TrackDownloadResult
import com.xperience.player.domain.model.lyrics.LyricLine
import com.xperience.player.domain.model.lyrics.LyricsProviderConfig
import com.xperience.player.domain.model.lyrics.LyricsProviderId
import com.xperience.player.domain.model.update.ManualCheckFeedback
import com.xperience.player.domain.model.update.UpdateCheckResult
import com.xperience.player.domain.model.update.UpdateDownloadResult
import com.xperience.player.domain.model.update.UpdateErrorKind
import com.xperience.player.domain.model.update.UpdateInstallStage
import com.xperience.player.domain.model.update.UpdateUiState
import com.xperience.player.domain.model.visualizer.VisualizerDefinition
import com.xperience.player.service.AppleMusicCanvasService
import com.xperience.player.service.AudioOutputHelper
import com.xperience.player.service.AudioPlayerService
import com.xperience.player.service.LyricsProviderService
import com.xperience.player.service.MediaCatalogService
import com.xperience.player.service.MediaNetworkClients
import com.xperience.player.service.MornyeMusicDownloadService
import com.xperience.player.service.ResolvedMusicVideo
import com.xperience.player.service.SearchFilter
import com.xperience.player.service.SearchResultData
import com.xperience.player.service.YouTubeMusicService
import com.xperience.player.service.cast.CastHandoffSnapshot
import com.xperience.player.service.cast.CastPlaybackState
import com.xperience.player.service.cast.CastRouteDevice
import com.xperience.player.service.cast.CastSessionController
import com.xperience.player.service.update.ApkInstaller
import com.xperience.player.service.update.AppUpdateService
import com.xperience.player.service.update.SemVerParser
import com.xperience.player.service.update.StreamingSha256Verifier
import java.io.File
import kotlinx.coroutines.withContext
import com.xperience.player.ui.components.NavigationTab
import com.xperience.player.ui.library.LibrarySubView
import com.xperience.player.ui.theme.AccentColorOption
import com.xperience.player.ui.theme.ArtistAmbientColors
import com.xperience.player.ui.theme.ArtworkColorExtractor
import com.xperience.player.ui.theme.AuroraColors
import com.xperience.player.ui.theme.DEFAULT_AURORA_COLORS
import com.xperience.player.ui.theme.ThemeMode
import com.xperience.player.widget.WidgetDeepLink
import com.xperience.player.widget.WidgetPlaylistSourceType
import com.xperience.player.widget.WidgetSyncBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

enum class SearchCategoryFilter(val label: String) {
    ALL("All"),
    ARTISTS("Artists"),
    ALBUMS("Albums"),
    SONGS("Songs")
}

enum class MediaSourceType(val label: String) {
    LOCAL("Local"),
    ONLINE("Online"),
    ALL("All")
}

enum class PlayerMediaMode(val label: String) {
    SONG("Song"),
    VIDEO("Video")
}

internal enum class OverlayLayer {
    HOME_GRID,
    PLAYLIST_DETAIL,
    ARTIST_DETAIL,
    ALBUM_DETAIL,
    SEARCH
}

data class LiquidGlassSettings(
    val blurRadiusDp: Float = 2.5f,
    val refractionStrength: Float = 0.65f,
    val surfaceTintOpacity: Float = 0.55f,
    val specularMeniscus: Float = 0.65f
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    val appleMusicCanvasService = AppleMusicCanvasService.getInstance(application)
    val youTubeMusicService = YouTubeMusicService.getInstance()
    private val catalogService = MediaCatalogService(
        context = application,
        ytmService = youTubeMusicService,
        appleMusicCanvasService = appleMusicCanvasService
    )
    val audioPlayerService = AudioPlayerService.getInstance(application)
    val castController: CastSessionController = CastSessionController.getInstance(application)
    val audioOutputHelper = AudioOutputHelper(application)
    val outputDeviceName: StateFlow<String> = audioOutputHelper.currentOutputName
    val castState: StateFlow<CastPlaybackState> = castController.castState
    val castRoutes: StateFlow<List<CastRouteDevice>> = castController.routes
    val isCastScanning: StateFlow<Boolean> = castController.isScanning

    private val _isCastPickerOpen = MutableStateFlow(false)
    val isCastPickerOpen: StateFlow<Boolean> = _isCastPickerOpen.asStateFlow()

    private val _isCastControlsOpen = MutableStateFlow(false)
    val isCastControlsOpen: StateFlow<Boolean> = _isCastControlsOpen.asStateFlow()

    private val _trackForRingtoneEditor = MutableStateFlow<TrackItem?>(null)
    val trackForRingtoneEditor: StateFlow<TrackItem?> = _trackForRingtoneEditor.asStateFlow()

    private var widgetDeepLinkJob: Job? = null
    val favoritesRepository = FavoritesRepository(application)
    val likedTracks: StateFlow<List<TrackItem>> = favoritesRepository.likedTracks
    val likedTrackIds: StateFlow<Set<String>> = favoritesRepository.likedTrackIds

    private val _likedRecommendations = MutableStateFlow<List<TrackItem>>(emptyList())
    val likedRecommendations: StateFlow<List<TrackItem>> = _likedRecommendations.asStateFlow()

    private val _isLoadingLikedRecommendations = MutableStateFlow(false)
    val isLoadingLikedRecommendations: StateFlow<Boolean> = _isLoadingLikedRecommendations.asStateFlow()

    private var likedRecommendationsJob: Job? = null

    // Full Screen Now Playing & Landscape Ambient Mode States
    private val _isPlayerExpanded = MutableStateFlow(false)
    val isPlayerExpanded: StateFlow<Boolean> = _isPlayerExpanded.asStateFlow()

    private val _isAmbientModeOpen = MutableStateFlow(false)
    val isAmbientModeOpen: StateFlow<Boolean> = _isAmbientModeOpen.asStateFlow()

    val playlistRepository = PlaylistRepository(application)
    private val visualizerRepository = VisualizerRepository(application)
    val lyricsProviderService = LyricsProviderService(application)
    val musicDownloadService = MornyeMusicDownloadService(
        context = application,
        youTubeMusicService = youTubeMusicService,
        lyricsProviderService = lyricsProviderService,
        canvasArtworkService = appleMusicCanvasService,
        mediaCatalogService = catalogService
    )
    private val settingsPrefs = application.getSharedPreferences("xperience_app_prefs", Context.MODE_PRIVATE)
    val appUpdateService = AppUpdateService(application)

    private val _autoCheckUpdates = MutableStateFlow(
        settingsPrefs.getBoolean("auto_check_updates", true)
    )
    val autoCheckUpdates: StateFlow<Boolean> = _autoCheckUpdates.asStateFlow()

    private val _updateUiState = MutableStateFlow(
        UpdateUiState(currentVersionDisplay = appUpdateService.getCurrentVersionDisplayTag())
    )
    val updateUiState: StateFlow<UpdateUiState> = _updateUiState.asStateFlow()

    private var hasTriggeredStartupUpdateCheck: Boolean = false
    private var updateCheckJob: Job? = null
    private var updateDownloadJob: Job? = null
    private var lastVerifiedApkSha256: String? = null

    val homeFeed: StateFlow<HomeDiscoveryFeed> = catalogService.homeFeed
    val isScanning: StateFlow<Boolean> = catalogService.isScanning
    val playbackState: StateFlow<PlaybackState> = combine(
        audioPlayerService.playbackState,
        castController.castState
    ) { local, cast ->
        if (cast.isConnected) {
            val effectiveTrack = cast.currentTrack ?: local.currentTrack
            val effectiveQueue = cast.queue.ifEmpty { local.queue }
            PlaybackState(
                currentTrack = effectiveTrack,
                isPlaying = cast.isPlaying,
                isLoading = cast.isBuffering,
                currentPositionMs = cast.positionMs,
                durationMs = cast.durationMs.takeIf { it > 0L }
                    ?: effectiveTrack?.durationMs
                    ?: local.durationMs,
                repeatMode = cast.repeatMode,
                isShuffleEnabled = cast.isShuffleEnabled,
                queue = effectiveQueue,
                volume = cast.volume,
                currentQueueIndex = cast.currentQueueIndex
            )
        } else {
            local
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, audioPlayerService.playbackState.value)

    private val _isInitialHomeReady = MutableStateFlow(false)
    val isInitialHomeReady: StateFlow<Boolean> = _isInitialHomeReady.asStateFlow()

    // Aurora Borealis home ambient colors driven by current playing artwork
    private val _auroraColors = MutableStateFlow(DEFAULT_AURORA_COLORS)
    val auroraColors: StateFlow<AuroraColors> = _auroraColors.asStateFlow()

    // YouTube Music Account & Personalized Sync State
    val ytmAccountState: StateFlow<YtmAccountState> = catalogService.authManager.accountState
    val shouldShowYtmLoginPrompt: StateFlow<Boolean> = catalogService.authManager.shouldShowLoginPrompt

    private val _isYtmLoginSheetOpen = MutableStateFlow(false)
    val isYtmLoginSheetOpen: StateFlow<Boolean> = _isYtmLoginSheetOpen.asStateFlow()

    private val _isSyncingYtmAccount = MutableStateFlow(false)
    val isSyncingYtmAccount: StateFlow<Boolean> = _isSyncingYtmAccount.asStateFlow()

    private val _ytmLoginError = MutableStateFlow<String?>(null)
    val ytmLoginError: StateFlow<String?> = _ytmLoginError.asStateFlow()

    // YouTube Music Playlist Import & Local Removal Confirmation State
    private val _isYtmPlaylistImportOpen = MutableStateFlow(false)
    val isYtmPlaylistImportOpen: StateFlow<Boolean> = _isYtmPlaylistImportOpen.asStateFlow()

    private val _isLoadingYtmLibraryPlaylists = MutableStateFlow(false)
    val isLoadingYtmLibraryPlaylists: StateFlow<Boolean> = _isLoadingYtmLibraryPlaylists.asStateFlow()

    private val _ytmLibraryPlaylists = MutableStateFlow<List<YouTubeMusicLibraryPlaylist>>(emptyList())
    val ytmLibraryPlaylists: StateFlow<List<YouTubeMusicLibraryPlaylist>> = _ytmLibraryPlaylists.asStateFlow()

    private val _selectedYtmPlaylistIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedYtmPlaylistIds: StateFlow<Set<String>> = _selectedYtmPlaylistIds.asStateFlow()

    private val _isImportingYtmPlaylists = MutableStateFlow(false)
    val isImportingYtmPlaylists: StateFlow<Boolean> = _isImportingYtmPlaylists.asStateFlow()

    private val _ytmImportProgressText = MutableStateFlow<String?>(null)
    val ytmImportProgressText: StateFlow<String?> = _ytmImportProgressText.asStateFlow()

    private val _ytmImportError = MutableStateFlow<String?>(null)
    val ytmImportError: StateFlow<String?> = _ytmImportError.asStateFlow()

    private val _ytmImportStatusMessage = MutableStateFlow<String?>(null)
    val ytmImportStatusMessage: StateFlow<String?> = _ytmImportStatusMessage.asStateFlow()

    private val _playlistPendingDelete = MutableStateFlow<UserPlaylist?>(null)
    val playlistPendingDelete: StateFlow<UserPlaylist?> = _playlistPendingDelete.asStateFlow()

    private val _isRefreshingImportedPlaylist = MutableStateFlow(false)
    val isRefreshingImportedPlaylist: StateFlow<Boolean> = _isRefreshingImportedPlaylist.asStateFlow()

    private var ytmLibraryPlaylistsLoadJob: Job? = null
    private var ytmPlaylistImportJob: Job? = null
    private var refreshImportedPlaylistJob: Job? = null

    // Ordered LIFO overlay stack so Search can open over any detail screen
    // and return cleanly to the exact prior destination on Back/Close.
    private val _overlayStack = MutableStateFlow<List<OverlayLayer>>(emptyList())
    internal val overlayStack: StateFlow<List<OverlayLayer>> = _overlayStack.asStateFlow()

    // Home Fullscreen Grid Overlay State (Radio Stations / Novedades)
    private val _homeGridCategory = MutableStateFlow<HomeGridCategory?>(null)
    val homeGridCategory: StateFlow<HomeGridCategory?> = _homeGridCategory.asStateFlow()

    val availableVisualizers: StateFlow<List<VisualizerDefinition>> = visualizerRepository.availableVisualizers
    val selectedVisualizer: StateFlow<VisualizerDefinition> = visualizerRepository.selectedVisualizer

    private val _currentLyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val currentLyrics: StateFlow<List<LyricLine>> = _currentLyrics.asStateFlow()

    private val _currentLyricsProviderName = MutableStateFlow<String?>("Synchronized")
    val currentLyricsProviderName: StateFlow<String?> = _currentLyricsProviderName.asStateFlow()

    private val _lyricsProviders = MutableStateFlow<List<LyricsProviderConfig>>(lyricsProviderService.getProviders())
    val lyricsProviders: StateFlow<List<LyricsProviderConfig>> = _lyricsProviders.asStateFlow()

    private val _currentCanvasArtwork = MutableStateFlow<CanvasArtworkResult?>(null)
    val currentCanvasArtwork: StateFlow<CanvasArtworkResult?> = _currentCanvasArtwork.asStateFlow()

    // Audio / Video Switcher State for Now Playing & Tablet Fullscreen
    private val _currentMusicVideo = MutableStateFlow<ResolvedMusicVideo?>(null)
    val currentMusicVideo: StateFlow<ResolvedMusicVideo?> = _currentMusicVideo.asStateFlow()

    private val _isResolvingMusicVideo = MutableStateFlow(false)
    val isResolvingMusicVideo: StateFlow<Boolean> = _isResolvingMusicVideo.asStateFlow()

    private val _playerMediaMode = MutableStateFlow(PlayerMediaMode.SONG)
    val playerMediaMode: StateFlow<PlayerMediaMode> = _playerMediaMode.asStateFlow()

    private var musicVideoResolveJob: Job? = null
    private var lastMusicVideoTrackId: String? = null
    private var musicVideoRetryCountForTrack: Int = 0
    private var musicVideoNeedsRefresh: Boolean = false

    // Download & Offline State
    val downloadOptions: StateFlow<DownloadMetadataOptions> = musicDownloadService.options

    private val _isDownloadModalOpen = MutableStateFlow(false)
    val isDownloadModalOpen: StateFlow<Boolean> = _isDownloadModalOpen.asStateFlow()

    private val _downloadCandidates = MutableStateFlow<List<TrackDownloadCandidate>>(emptyList())
    val downloadCandidates: StateFlow<List<TrackDownloadCandidate>> = _downloadCandidates.asStateFlow()

    private val _isSearchingCandidates = MutableStateFlow(false)
    val isSearchingCandidates: StateFlow<Boolean> = _isSearchingCandidates.asStateFlow()

    private val _downloadProgressState = MutableStateFlow<DownloadProgressState?>(null)
    val downloadProgressState: StateFlow<DownloadProgressState?> = _downloadProgressState.asStateFlow()

    private var lyricsJob: Job? = null
    private var lastLyricsTrackId: String? = null
    private val trackCanvasCoordinator by lazy {
        TrackCanvasReuseCoordinator(
            scope = viewModelScope,
            canvasService = appleMusicCanvasService,
            targetFlow = _currentCanvasArtwork,
            warmCache = { result ->
                if (!result.isHls && result.videoUri.startsWith("http")) {
                    viewModelScope.launch {
                        MediaNetworkClients.warmCanvasVideoCache(getApplication(), result.videoUri)
                    }
                }
            }
        )
    }
    private var lastCanvasTrackId: String? = null
    private var lastPrefetchedNextCanvasTrackId: String? = null
    private var lastPrefetchedArtistKey: String? = null
    private var lastArtworkTrackId: String? = null
    private var lastAuroraArtworkUri: String? = null
    private var auroraExtractionJob: Job? = null
    private var ytmSyncJob: Job? = null
    private var refreshFeedJob: Job? = null
    private var feedResolveJob: Job? = null
    private var searchCandidatesJob: Job? = null
    private var downloadJob: Job? = null
    private var artistDetailJob: Job? = null
    private var artistAmbientJob: Job? = null
    private var artistVideoJob: Job? = null
    private var artistRequestGeneration: Long = 0L
    private var activeArtistRequestKey: String? = null

    // Artist Detail View States
    private val _isArtistDetailOpen = MutableStateFlow(false)
    val isArtistDetailOpen: StateFlow<Boolean> = _isArtistDetailOpen.asStateFlow()

    private val _isArtistDetailLoading = MutableStateFlow(false)
    val isArtistDetailLoading: StateFlow<Boolean> = _isArtistDetailLoading.asStateFlow()

    private val _currentArtistDetail = MutableStateFlow<ArtistDetailProfile?>(null)
    val currentArtistDetail: StateFlow<ArtistDetailProfile?> = _currentArtistDetail.asStateFlow()

    private val _currentArtistVideoUri = MutableStateFlow<String?>(null)
    val currentArtistVideoUri: StateFlow<String?> = _currentArtistVideoUri.asStateFlow()

    private val _isArtistBioExpanded = MutableStateFlow(false)
    val isArtistBioExpanded: StateFlow<Boolean> = _isArtistBioExpanded.asStateFlow()

    private val _currentArtistAmbientColors = MutableStateFlow(
        ArtistAmbientColors(
            dark = Color(0xFF0F1012),
            light = Color(0xFFF4F6F9)
        )
    )
    val currentArtistAmbientColors: StateFlow<ArtistAmbientColors> = _currentArtistAmbientColors.asStateFlow()

    private val _currentArtistAmbientColor = MutableStateFlow(Color(0xFF0F1012))
    val currentArtistAmbientColor: StateFlow<Color> = _currentArtistAmbientColor.asStateFlow()

    // Dedicated Album Detail Screen State
    private val _isAlbumDetailOpen = MutableStateFlow(false)
    val isAlbumDetailOpen: StateFlow<Boolean> = _isAlbumDetailOpen.asStateFlow()

    private val _isAlbumDetailLoading = MutableStateFlow(false)
    val isAlbumDetailLoading: StateFlow<Boolean> = _isAlbumDetailLoading.asStateFlow()

    private val _currentAlbumDetail = MutableStateFlow<AlbumDetailProfile?>(null)
    val currentAlbumDetail: StateFlow<AlbumDetailProfile?> = _currentAlbumDetail.asStateFlow()

    private val _currentAlbumAmbientColors = MutableStateFlow(
        ArtistAmbientColors(
            dark = Color(0xFF0F1012),
            light = Color(0xFFF4F6F9)
        )
    )
    val currentAlbumAmbientColors: StateFlow<ArtistAmbientColors> = _currentAlbumAmbientColors.asStateFlow()

    private var albumDetailJob: Job? = null
    private var albumAmbientJob: Job? = null
    private var albumRequestGeneration: Long = 0L
    private var activeAlbumRequestKey: String? = null

    // Dedicated Playlist Detail Screen State
    val playlists: StateFlow<List<UserPlaylist>> = playlistRepository.playlists
    val smartSuggestedPlaylists: StateFlow<List<UserPlaylist>> = catalogService.smartSuggestedPlaylists

    private val _isPlaylistDetailOpen = MutableStateFlow(false)
    val isPlaylistDetailOpen: StateFlow<Boolean> = _isPlaylistDetailOpen.asStateFlow()

    private val _currentPlaylistDetail = MutableStateFlow<UserPlaylist?>(null)
    val currentPlaylistDetail: StateFlow<UserPlaylist?> = _currentPlaylistDetail.asStateFlow()

    // Playlist Creation & Editing Modal State
    private val _isCreatePlaylistDialogOpen = MutableStateFlow(false)
    val isCreatePlaylistDialogOpen: StateFlow<Boolean> = _isCreatePlaylistDialogOpen.asStateFlow()

    private val _playlistToEdit = MutableStateFlow<UserPlaylist?>(null)
    val playlistToEdit: StateFlow<UserPlaylist?> = _playlistToEdit.asStateFlow()

    // Add To Playlist & Track Options State
    private val _trackForAddToPlaylist = MutableStateFlow<TrackItem?>(null)
    val trackForAddToPlaylist: StateFlow<TrackItem?> = _trackForAddToPlaylist.asStateFlow()

    private val _trackForOptionsMenu = MutableStateFlow<TrackItem?>(null)
    val trackForOptionsMenu: StateFlow<TrackItem?> = _trackForOptionsMenu.asStateFlow()

    // Theme Mode (SYSTEM default, DARK, LIGHT)
    private val _themeMode = MutableStateFlow(
        try {
            val saved = settingsPrefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
            ThemeMode.valueOf(saved)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun selectThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        settingsPrefs.edit().putString("theme_mode", mode.name).apply()
    }

    private val _selectedTab = MutableStateFlow(NavigationTab.Home)
    val selectedTab: StateFlow<NavigationTab> = _selectedTab.asStateFlow()

    private val _isSettingsOpen = MutableStateFlow(false)
    val isSettingsOpen: StateFlow<Boolean> = _isSettingsOpen.asStateFlow()

    private val _libraryFolders = MutableStateFlow<List<String>>(emptyList())
    val libraryFolders: StateFlow<List<String>> = _libraryFolders.asStateFlow()

    // Library State & Media Sources
    val localTracks: StateFlow<List<TrackItem>> = catalogService.localTracks
    val localArtists: StateFlow<List<ArtistItem>> = catalogService.localArtists
    val localAlbums: StateFlow<List<AlbumItem>> = catalogService.localAlbums
    val onlineTracks: StateFlow<List<TrackItem>> = catalogService.onlineTracks
    val onlineArtists: StateFlow<List<ArtistItem>> = catalogService.onlineArtists
    val onlineAlbums: StateFlow<List<AlbumItem>> = catalogService.onlineAlbums

    private val _selectedCategory = MutableStateFlow(SearchCategoryFilter.ALL)
    val selectedCategory: StateFlow<SearchCategoryFilter> = _selectedCategory.asStateFlow()

    private val _selectedSourceType = MutableStateFlow(MediaSourceType.LOCAL)
    val selectedSourceType: StateFlow<MediaSourceType> = _selectedSourceType.asStateFlow()

    private val _activeLibrarySubView = MutableStateFlow<LibrarySubView?>(null)
    val activeLibrarySubView: StateFlow<LibrarySubView?> = _activeLibrarySubView.asStateFlow()

    fun selectCategory(category: SearchCategoryFilter) {
        _selectedCategory.value = category
        _activeLibrarySubView.value = when (category) {
            SearchCategoryFilter.ALL -> null
            SearchCategoryFilter.ARTISTS -> LibrarySubView.ARTISTS
            SearchCategoryFilter.ALBUMS -> LibrarySubView.ALBUMS
            SearchCategoryFilter.SONGS -> LibrarySubView.SONGS
        }
    }

    fun selectSourceType(sourceType: MediaSourceType) {
        _selectedSourceType.value = sourceType
    }

    fun openLibrarySubView(subView: LibrarySubView?) {
        _activeLibrarySubView.value = subView
        when (subView) {
            LibrarySubView.ARTISTS -> _selectedCategory.value = SearchCategoryFilter.ARTISTS
            LibrarySubView.ALBUMS -> _selectedCategory.value = SearchCategoryFilter.ALBUMS
            LibrarySubView.SONGS -> _selectedCategory.value = SearchCategoryFilter.SONGS
            null -> _selectedCategory.value = SearchCategoryFilter.ALL
            LibrarySubView.RECENTLY_ADDED -> Unit
        }
    }

    fun handleLibraryBackNavigation(): Boolean {
        val transition = resolveLibraryBackTransition(
            currentSubView = _activeLibrarySubView.value,
            currentCategory = _selectedCategory.value
        ) ?: return false
        _activeLibrarySubView.value = transition.first
        _selectedCategory.value = transition.second
        return true
    }

    val libraryArtists: StateFlow<List<ArtistItem>> = combine(
        _selectedSourceType,
        catalogService.localArtists,
        catalogService.onlineArtists
    ) { source, local, online ->
        when (source) {
            MediaSourceType.LOCAL -> local
            MediaSourceType.ONLINE -> online
            MediaSourceType.ALL -> (local + online).distinctBy { it.name.trim().lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val libraryAlbums: StateFlow<List<AlbumItem>> = combine(
        _selectedSourceType,
        catalogService.localAlbums,
        catalogService.onlineAlbums
    ) { source, local, online ->
        when (source) {
            MediaSourceType.LOCAL -> local
            MediaSourceType.ONLINE -> online
            MediaSourceType.ALL -> (local + online).distinctBy { it.title.trim().lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val librarySongs: StateFlow<List<TrackItem>> = combine(
        _selectedSourceType,
        catalogService.localTracks,
        catalogService.onlineTracks
    ) { source, local, online ->
        when (source) {
            MediaSourceType.LOCAL -> local
            MediaSourceType.ONLINE -> online
            MediaSourceType.ALL -> (local + online).distinctBy { it.id }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val recentlyAddedLibraryTracks: StateFlow<List<TrackItem>> = combine(
        _selectedSourceType,
        catalogService.localTracks,
        catalogService.onlineTracks,
        favoritesRepository.likedTracks
    ) { source, local, online, liked ->
        val candidates = selectRecentlyAddedSourceCandidates(
            sourceType = source,
            localTracks = local,
            onlineTracks = online,
            likedTracks = liked
        )
        catalogService.getRecentlyAddedLibraryTracks(candidates)
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val libraryHeaderTitle: StateFlow<String> = _selectedSourceType.map { source ->
        when (source) {
            MediaSourceType.LOCAL -> "Library"
            MediaSourceType.ONLINE -> "Online Music"
            MediaSourceType.ALL -> "All Music"
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, "Library")

    val libraryHeaderSubtitle: StateFlow<String> = combine(
        _selectedSourceType,
        libraryArtists,
        libraryAlbums,
        librarySongs
    ) { source, artists, albums, songs ->
        when (source) {
            MediaSourceType.LOCAL -> "${artists.size} Artists • ${albums.size} Albums • ${songs.size} Songs from your local music library"
            MediaSourceType.ONLINE -> "Explore trending songs and artists on YouTube Music • ${songs.size} Songs"
            MediaSourceType.ALL -> "Combined Local Library & YouTube Music • ${songs.size} Songs"
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, "Your local music library")

    // LiquidGlass configurable parameters
    private val _liquidGlassSettings = MutableStateFlow(LiquidGlassSettings())
    val liquidGlassSettings: StateFlow<LiquidGlassSettings> = _liquidGlassSettings.asStateFlow()

    // Dynamic and configurable AccentColor
    private val _accentColorOption = MutableStateFlow(AccentColorOption.DYNAMIC)
    val accentColorOption: StateFlow<AccentColorOption> = _accentColorOption.asStateFlow()

    private val _dynamicArtworkColor = MutableStateFlow(Color(0xFF00E5FF))
    val dynamicArtworkColor: StateFlow<Color> = _dynamicArtworkColor.asStateFlow()

    // Search Mode States
    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchFilter = MutableStateFlow(SearchFilter.ALL)
    val searchFilter: StateFlow<SearchFilter> = _searchFilter.asStateFlow()

    private val _searchResults = MutableStateFlow(SearchResultData())
    val searchResults: StateFlow<SearchResultData> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    // Search Moods & Genres State
    private val _moodsAndGenresFeed = MutableStateFlow<YtmMoodsAndGenresFeed?>(null)
    val moodsAndGenresFeed: StateFlow<YtmMoodsAndGenresFeed?> = _moodsAndGenresFeed.asStateFlow()

    private val _isLoadingMoods = MutableStateFlow(false)
    val isLoadingMoods: StateFlow<Boolean> = _isLoadingMoods.asStateFlow()

    private val _selectedMoodCategory = MutableStateFlow<YtmMoodCategory?>(null)
    val selectedMoodCategory: StateFlow<YtmMoodCategory?> = _selectedMoodCategory.asStateFlow()

    private val _moodDetailResult = MutableStateFlow<YtmMoodCategoryDetail?>(null)
    val moodDetailResult: StateFlow<YtmMoodCategoryDetail?> = _moodDetailResult.asStateFlow()

    private val _isLoadingMoodDetail = MutableStateFlow(false)
    val isLoadingMoodDetail: StateFlow<Boolean> = _isLoadingMoodDetail.asStateFlow()

    private var searchDebounceJob: Job? = null
    private var moodsFetchJob: Job? = null
    private var moodDetailJob: Job? = null

    init {
        MediaNetworkClients.getImageLoader(application)
        refreshFolders()
        _recentSearches.value = catalogService.getRecentSearches()

        audioPlayerService.onTrackPlayed = { track ->
            catalogService.recordTrackPlayed(track)
        }
        audioPlayerService.fallbackSimilarTracksProvider = ::buildOfflineSimilarTracksFallback

        viewModelScope.launch {
            try {
                catalogService.updateLikedSeeds(favoritesRepository.likedTracks.value)
                catalogService.initializeCatalog()
            } finally {
                _isInitialHomeReady.value = true
            }
            refreshLikedRecommendations()
            loadMoodsAndGenres()
        }

        // Observe track changes to dynamically extract prominent album artwork colors & Aurora palette
        viewModelScope.launch {
            playbackState.collect { state ->
                val track = state.currentTrack
                if (track != null) {
                    if (track.id != lastArtworkTrackId) {
                        lastArtworkTrackId = track.id
                        val color = ArtworkColorExtractor.extractAccentColor(getApplication(), track)
                        _dynamicArtworkColor.value = color
                    }

                    val artworkUri = normalizeAuroraArtworkUri(track)
                    if (artworkUri == null) {
                        auroraExtractionJob?.cancel()
                        auroraExtractionJob = null
                        lastAuroraArtworkUri = null
                        _auroraColors.value = DEFAULT_AURORA_COLORS
                    } else if (artworkUri != lastAuroraArtworkUri) {
                        lastAuroraArtworkUri = artworkUri
                        auroraExtractionJob?.cancel()
                        val cachedAurora = ArtworkColorExtractor.getCachedAuroraColors(artworkUri)
                        if (cachedAurora != null) {
                            _auroraColors.value = cachedAurora
                        } else {
                            // Keep last extracted Aurora colors while the new artwork finishes extracting
                            auroraExtractionJob = viewModelScope.launch {
                                try {
                                    val extracted = ArtworkColorExtractor.extractAuroraColors(
                                        getApplication(),
                                        artworkUri
                                    )
                                    if (lastAuroraArtworkUri == artworkUri) {
                                        _auroraColors.value = extracted
                                    }
                                } catch (ce: CancellationException) {
                                    throw ce
                                } catch (_: Exception) {
                                    // Preserve last valid Aurora colors on transient failure
                                }
                            }
                        }
                    }
                } else {
                    lastArtworkTrackId = null
                    auroraExtractionJob?.cancel()
                    auroraExtractionJob = null
                    lastAuroraArtworkUri = null
                    _auroraColors.value = DEFAULT_AURORA_COLORS
                }
            }
        }

        // Observe track changes to automatically resolve synchronized lyrics and motion canvas
        viewModelScope.launch {
            playbackState.collect { state ->
                val track = state.currentTrack
                if (track != null) {
                    if (track.id != lastLyricsTrackId) {
                        lastLyricsTrackId = track.id
                        fetchLyricsForTrack(track)
                    }
                    if (track.id != lastCanvasTrackId) {
                        lastCanvasTrackId = track.id
                        fetchCanvasForTrack(track)
                    }
                    if (track.id != lastMusicVideoTrackId) {
                        lastMusicVideoTrackId = track.id
                        fetchMusicVideoForTrack(track)
                    }

                    // Prefetch the current track's artist assets & queue-next track's Canvas in background
                    val primaryArtistKey = track.artist.trim().lowercase()
                    if (primaryArtistKey.isNotBlank() && primaryArtistKey != lastPrefetchedArtistKey) {
                        lastPrefetchedArtistKey = primaryArtistKey
                        appleMusicCanvasService.prefetchArtists(listOf(track.artist), maxCount = 1)
                    }
                    val idx = if (state.currentQueueIndex in state.queue.indices) {
                        state.currentQueueIndex
                    } else {
                        state.queue.indexOfFirst { it.id == track.id }
                    }
                    val nextTrack = if (idx >= 0 && idx + 1 < state.queue.size) state.queue[idx + 1] else null
                    if (nextTrack != null && nextTrack.id != lastPrefetchedNextCanvasTrackId) {
                        lastPrefetchedNextCanvasTrackId = nextTrack.id
                        appleMusicCanvasService.prefetchCanvasArtwork(
                            title = nextTrack.title,
                            artist = nextTrack.artist,
                            album = nextTrack.album
                        )
                    }
                } else {
                    lastLyricsTrackId = null
                    _currentLyrics.value = emptyList()
                    _currentLyricsProviderName.value = null
                    lastCanvasTrackId = null
                    trackCanvasCoordinator.clear()
                    lastMusicVideoTrackId = null
                    musicVideoRetryCountForTrack = 0
                    musicVideoNeedsRefresh = false
                    musicVideoResolveJob?.cancel()
                    _isResolvingMusicVideo.value = false
                    _currentMusicVideo.value = null
                    _playerMediaMode.value = PlayerMediaMode.SONG
                }
            }
        }

        // Keep currentPlaylistDetail updated if the playlist is modified in the repository
        viewModelScope.launch {
            playlistRepository.playlists.collect { list ->
                val current = _currentPlaylistDetail.value ?: return@collect
                val updated = list.firstOrNull { it.id == current.id }
                if (updated != null) {
                    _currentPlaylistDetail.value = updated
                }
            }
        }

        // Bidirectional Local <-> Chromecast handoff (captures singleton audioPlayerService only, avoiding ViewModel leaks)
        val localPlayer = audioPlayerService
        castController.localPlaybackStateProvider = {
            val ps = localPlayer.playbackState.value
            CastHandoffSnapshot.fromLocalPlayback(
                currentTrack = ps.currentTrack,
                queue = ps.queue,
                currentQueueIndex = ps.currentQueueIndex,
                positionMs = ps.currentPositionMs,
                isPlaying = ps.isPlaying,
                repeatMode = ps.repeatMode,
                isShuffleEnabled = ps.isShuffleEnabled
            )
        }
        castController.onCastSessionStarted = {
            localPlayer.pause()
        }
        castController.onCastSessionEnded = { handoff ->
            handoff.currentTrack?.let { track ->
                val queue = handoff.queue.ifEmpty { listOf(track) }
                val index = handoff.currentQueueIndex.coerceIn(0, queue.lastIndex)
                localPlayer.setRepeatMode(handoff.repeatMode)
                if (localPlayer.playbackState.value.isShuffleEnabled != handoff.isShuffleEnabled) {
                    localPlayer.toggleShuffle()
                }
                localPlayer.setQueue(
                    tracks = queue,
                    startIndex = index,
                    startPositionMs = handoff.positionMs,
                    autoPlay = handoff.wasPlaying
                )
            }
        }

        // Auto-close expanded Cast controls sheet if remote session ends
        viewModelScope.launch {
            castController.castState.collect { state ->
                if (!state.isActiveSession && _isCastControlsOpen.value) {
                    _isCastControlsOpen.value = false
                }
            }
        }

        // Live Home Screen Widget synchronization (playback + curated playlist recommendations)
        WidgetSyncBridge.onFavoriteToggleRequested = { trackId, fallback ->
            val wasLiked = favoritesRepository.likedTrackIds.value.contains(trackId)
            toggleFavorite(trackId, fallback)
            !wasLiked
        }
        WidgetSyncBridge.onRefreshRecommendationsRequested = {
            refreshHomeFeed()
        }

        viewModelScope.launch(Dispatchers.IO) {
            combine(
                playbackState,
                likedTrackIds,
                dynamicArtworkColor
            ) { state, likedIds, accent ->
                Triple(state, likedIds, accent)
            }.collect { (state, likedIds, accent) ->
                val isCurrentLiked = state.currentTrack?.let { likedIds.contains(it.id) } ?: false
                runCatching {
                    WidgetSyncBridge.syncPlayback(
                        context = getApplication(),
                        playbackState = state,
                        isLiked = isCurrentLiked,
                        accentColorArgb = accent.toArgb()
                    )
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            combine(
                playlists,
                smartSuggestedPlaylists,
                homeFeed,
                likedTracks
            ) { userPls, smartPls, feed, liked ->
                runCatching {
                    WidgetSyncBridge.syncRecommendations(
                        context = getApplication(),
                        userPlaylists = userPls,
                        smartPlaylists = smartPls.ifEmpty { feed.smartSuggestedPlaylists },
                        madeForYou = feed.playlistsMadeForYou,
                        radioStations = feed.radioStations,
                        likedCount = liked.size,
                        likedArtworkUri = liked.firstNotNullOfOrNull { it.artworkUri },
                        recentlyPlayedTracks = feed.recentlyPlayedTracks
                    )
                }
            }.collect { }
        }
    }

    fun openSettings() {
        refreshFolders()
        _isSettingsOpen.value = true
    }

    fun closeSettings() {
        _isSettingsOpen.value = false
        _updateUiState.update { it.copy(manualCheckFeedback = null) }
    }

    fun setAutoCheckUpdates(enabled: Boolean) {
        _autoCheckUpdates.value = enabled
        settingsPrefs.edit { putBoolean("auto_check_updates", enabled) }
    }

    /**
     * Silent, non-blocking background update check executed once on app startup when
     * `autoCheckUpdates` is enabled. Network/API failures remain completely silent.
     */
    fun performStartupUpdateCheckIfEnabled() {
        if (hasTriggeredStartupUpdateCheck) return
        hasTriggeredStartupUpdateCheck = true
        if (!_autoCheckUpdates.value) return

        viewModelScope.launch {
            val result = try {
                appUpdateService.checkForUpdate()
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                return@launch
            }

            if (result is UpdateCheckResult.UpdateAvailable) {
                _updateUiState.update { current ->
                    if (current.isDialogOpen ||
                        current.isBusyDownloadingOrVerifying ||
                        current.installStage is UpdateInstallStage.AwaitingUnknownSourcesPermission ||
                        current.installStage is UpdateInstallStage.Installing
                    ) {
                        current
                    } else {
                        current.copy(
                            availableUpdate = result.candidate,
                            isDialogOpen = true,
                            installStage = UpdateInstallStage.Ready
                        )
                    }
                }
            }
        }
    }

    /**
     * Manual update check triggered from Settings ("Buscar actualizaciones ahora").
     */
    fun checkForUpdatesManually() {
        val snapshot = _updateUiState.value
        if (snapshot.isChecking) return
        if (snapshot.isBusyDownloadingOrVerifying && snapshot.availableUpdate != null) {
            _isSettingsOpen.value = false
            _updateUiState.update { it.copy(isDialogOpen = true) }
            return
        }

        updateCheckJob?.cancel()
        updateCheckJob = viewModelScope.launch {
            _updateUiState.update {
                it.copy(
                    isChecking = true,
                    manualCheckFeedback = null
                )
            }

            val result = try {
                appUpdateService.checkForUpdate()
            } catch (ce: CancellationException) {
                _updateUiState.update { it.copy(isChecking = false) }
                throw ce
            } catch (e: Exception) {
                UpdateCheckResult.Error(UpdateErrorKind.UNKNOWN, e.message)
            }

            when (result) {
                is UpdateCheckResult.UpdateAvailable -> {
                    _isSettingsOpen.value = false
                    _updateUiState.update {
                        it.copy(
                            isChecking = false,
                            manualCheckFeedback = null,
                            availableUpdate = result.candidate,
                            isDialogOpen = true,
                            installStage = UpdateInstallStage.Ready
                        )
                    }
                }
                is UpdateCheckResult.UpToDate -> {
                    _updateUiState.update {
                        it.copy(
                            isChecking = false,
                            manualCheckFeedback = ManualCheckFeedback.UpToDate(
                                SemVerParser.formatDisplayTag(result.currentVersion)
                            )
                        )
                    }
                }
                is UpdateCheckResult.NoReleasesPublished -> {
                    _updateUiState.update {
                        it.copy(
                            isChecking = false,
                            manualCheckFeedback = ManualCheckFeedback.NoReleasesPublished
                        )
                    }
                }
                is UpdateCheckResult.Error -> {
                    _updateUiState.update {
                        it.copy(
                            isChecking = false,
                            manualCheckFeedback = ManualCheckFeedback.Error(
                                errorKind = result.errorKind,
                                detail = result.detail
                            )
                        )
                    }
                }
            }
        }
    }

    fun dismissUpdateDialog() {
        if (_updateUiState.value.isBusyDownloadingOrVerifying) return
        _updateUiState.update { it.copy(isDialogOpen = false) }
    }

    fun cancelUpdateDownload() {
        updateDownloadJob?.cancel()
        updateDownloadJob = null
        lastVerifiedApkSha256 = null
        _updateUiState.update { it.copy(installStage = UpdateInstallStage.Ready) }
    }

    fun startUpdateDownloadAndInstall() {
        val candidate = _updateUiState.value.availableUpdate ?: return
        if (_updateUiState.value.isBusyDownloadingOrVerifying) return

        val previousJob = updateDownloadJob
        lastVerifiedApkSha256 = null

        updateDownloadJob = viewModelScope.launch {
            previousJob?.cancelAndJoin()
            val initialTotal = candidate.apkAsset.size.takeIf { it > 0L } ?: -1L
            _updateUiState.update {
                it.copy(
                    installStage = UpdateInstallStage.Downloading(
                        percent = if (initialTotal > 0L) 0 else null,
                        bytesDownloaded = 0L,
                        totalBytes = initialTotal
                    )
                )
            }

            val result = try {
                appUpdateService.downloadAndVerifyUpdate(
                    candidate = candidate,
                    onProgress = { progress ->
                        _updateUiState.update {
                            it.copy(
                                installStage = UpdateInstallStage.Downloading(
                                    percent = progress.percent,
                                    bytesDownloaded = progress.bytesDownloaded,
                                    totalBytes = progress.totalBytes
                                )
                            )
                        }
                    },
                    onVerifying = {
                        _updateUiState.update {
                            it.copy(installStage = UpdateInstallStage.Verifying)
                        }
                    }
                )
            } catch (ce: CancellationException) {
                _updateUiState.update { it.copy(installStage = UpdateInstallStage.Ready) }
                throw ce
            }

            when (result) {
                is UpdateDownloadResult.Success -> {
                    lastVerifiedApkSha256 = result.sha256Hex
                    proceedWithVerifiedApkInstall(result.apkFile)
                }
                is UpdateDownloadResult.Error -> {
                    _updateUiState.update {
                        it.copy(
                            installStage = UpdateInstallStage.Error(
                                errorKind = result.errorKind,
                                detail = result.detail
                            )
                        )
                    }
                }
            }
        }
    }

    private fun proceedWithVerifiedApkInstall(apkFile: File) {
        val appContext = getApplication<Application>()
        if (!ApkInstaller.canRequestPackageInstalls(appContext)) {
            _updateUiState.update {
                it.copy(installStage = UpdateInstallStage.AwaitingUnknownSourcesPermission(apkFile))
            }
            return
        }

        _updateUiState.update {
            it.copy(installStage = UpdateInstallStage.Installing(apkFile))
        }

        val launched = ApkInstaller.launchInstall(appContext, apkFile)
        if (!launched) {
            _updateUiState.update {
                it.copy(
                    installStage = UpdateInstallStage.Error(
                        UpdateErrorKind.INSTALLER_LAUNCH_FAILED
                    )
                )
            }
        }
    }

    /**
     * Called when returning from Android's `ACTION_MANAGE_UNKNOWN_APP_SOURCES` Settings screen.
     * Re-verifies the cached APK SHA-256 before seamlessly resuming installation.
     */
    fun onReturnedFromInstallPermissionSettings() {
        val stage = _updateUiState.value.installStage
        val apkFile = (stage as? UpdateInstallStage.AwaitingUnknownSourcesPermission)?.apkFile
            ?: return
        val appContext = getApplication<Application>()
        if (!ApkInstaller.canRequestPackageInstalls(appContext)) {
            return
        }

        val expectedSha256 = lastVerifiedApkSha256
        viewModelScope.launch {
            _updateUiState.update { it.copy(installStage = UpdateInstallStage.Verifying) }
            val stillValid = withContext(Dispatchers.IO) {
                StreamingSha256Verifier.verifyFileOrDelete(apkFile, expectedSha256)
            }
            if (!stillValid) {
                _updateUiState.update {
                    it.copy(
                        installStage = UpdateInstallStage.Error(
                            UpdateErrorKind.INTEGRITY_MISMATCH
                        )
                    )
                }
                return@launch
            }
            proceedWithVerifiedApkInstall(apkFile)
        }
    }

    fun retryLaunchVerifiedInstaller() {
        val stage = _updateUiState.value.installStage
        val apkFile = (stage as? UpdateInstallStage.Installing)?.apkFile ?: return
        val expectedSha256 = lastVerifiedApkSha256
        viewModelScope.launch {
            val stillValid = withContext(Dispatchers.IO) {
                StreamingSha256Verifier.verifyFileOrDelete(apkFile, expectedSha256)
            }
            if (!stillValid) {
                _updateUiState.update {
                    it.copy(
                        installStage = UpdateInstallStage.Error(
                            UpdateErrorKind.INTEGRITY_MISMATCH
                        )
                    )
                }
                return@launch
            }
            proceedWithVerifiedApkInstall(apkFile)
        }
    }

    fun updateBlurRadius(value: Float) {
        _liquidGlassSettings.value = _liquidGlassSettings.value.copy(blurRadiusDp = value)
    }

    fun updateRefractionStrength(value: Float) {
        _liquidGlassSettings.value = _liquidGlassSettings.value.copy(refractionStrength = value)
    }

    fun updateSurfaceTintOpacity(value: Float) {
        _liquidGlassSettings.value = _liquidGlassSettings.value.copy(surfaceTintOpacity = value)
    }

    fun updateSpecularMeniscus(value: Float) {
        _liquidGlassSettings.value = _liquidGlassSettings.value.copy(specularMeniscus = value)
    }

    fun resetLiquidGlassSettings() {
        _liquidGlassSettings.value = LiquidGlassSettings()
    }

    fun selectAccentColorOption(option: AccentColorOption) {
        _accentColorOption.value = option
    }

    fun scanMediaStore() {
        viewModelScope.launch {
            catalogService.scanDeviceMediaStore()
        }
    }

    fun addLibraryFolder(uri: Uri) {
        viewModelScope.launch {
            catalogService.addCustomLibraryFolder(uri)
            refreshFolders()
        }
    }

    fun removeLibraryFolder(uriStr: String) {
        catalogService.removeLibraryFolder(uriStr)
        refreshFolders()
    }

    private fun refreshFolders() {
        _libraryFolders.value = catalogService.getLibraryFolders()
    }

    private fun resolveEffectiveTrack(track: TrackItem): TrackItem {
        val offlineUri = musicDownloadService.findDownloadedTrackUri(track.title, track.artist)
        var effectiveTrack = if (offlineUri != null) {
            track.copy(audioUrl = offlineUri)
        } else track

        if (effectiveTrack.artworkUri.isNullOrBlank()) {
            val publicMusic = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
            val candidateImages = listOf(
                java.io.File(publicMusic, "${effectiveTrack.title} - ${effectiveTrack.artist}.jpg"),
                java.io.File(publicMusic, "Xperience Downloads/${effectiveTrack.title} - ${effectiveTrack.artist}.jpg")
            )
            val foundImg = candidateImages.firstOrNull { it.exists() && it.length() > 1024 }
            if (foundImg != null) {
                effectiveTrack = effectiveTrack.copy(artworkUri = foundImg.toURI().toString())
            }
        }
        return effectiveTrack
    }

    /**
     * Plays a full ordered [tracks] list starting at [startIndex], then transitions
     * into progressive similar recommendations when the list finishes.
     */
    fun playQueue(tracks: List<TrackItem>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val safeIndex = startIndex.coerceIn(0, tracks.lastIndex)
        val effectiveStart = resolveEffectiveTrack(tracks[safeIndex])
        val queueTracks = if (effectiveStart == tracks[safeIndex]) {
            tracks
        } else {
            tracks.toMutableList().also { it[safeIndex] = effectiveStart }
        }
        if (castController.castState.value.isConnected) {
            castController.playTrack(effectiveStart, queueTracks, safeIndex)
        } else {
            audioPlayerService.setQueue(queueTracks, safeIndex)
        }
        catalogService.recordTrackPlayed(effectiveStart)
    }

    /**
     * Plays a clicked Search track as a clean 1-track seed so finishing or skipping
     * transitions immediately into similar recommendations for that track.
     */
    fun onSearchTrackClicked(track: TrackItem) {
        val effectiveTrack = resolveEffectiveTrack(track)
        if (castController.castState.value.isConnected) {
            castController.playTrack(effectiveTrack, listOf(effectiveTrack), 0)
        } else {
            audioPlayerService.playSingleTrack(effectiveTrack)
        }
        catalogService.recordTrackPlayed(effectiveTrack)
    }

    fun onTrackClicked(track: TrackItem) {
        val effectiveTrack = resolveEffectiveTrack(track)
        if (castController.castState.value.isConnected) {
            val currentCastQueue = castController.castState.value.queue
            val existingIdx = currentCastQueue.indexOfFirst { it.id == effectiveTrack.id }
            if (existingIdx >= 0) {
                castController.jumpToQueueIndex(existingIdx)
            } else {
                val updatedQueue = currentCastQueue + effectiveTrack
                castController.playTrack(effectiveTrack, updatedQueue, updatedQueue.lastIndex)
            }
        } else {
            audioPlayerService.playTrack(effectiveTrack)
        }
        catalogService.recordTrackPlayed(effectiveTrack)
    }

    private fun buildOfflineSimilarTracksFallback(
        seedTracks: List<TrackItem>,
        excludeIds: Set<String>
    ): List<TrackItem> {
        val seedArtists = seedTracks.mapNotNull { normalizeArtistLookupName(it.artist)?.lowercase(Locale.ROOT) }.toSet()
        val seedAlbums = seedTracks.mapNotNull { it.album.trim().lowercase(Locale.ROOT).takeIf(String::isNotEmpty) }.toSet()
        val normalizedExcludes = excludeIds.map { it.trim().removePrefix("ytm_").lowercase(Locale.ROOT) }.toSet()

        val rawPool = homeFeed.value.recommendedTracks +
            favoritesRepository.likedTracks.value +
            librarySongs.value +
            catalogService.onlineTracks.value +
            catalogService.localTracks.value

        val seenKeys = HashSet<String>(rawPool.size)
        val candidates = rawPool.filter { candidate ->
            if (candidate.title.isBlank()) return@filter false
            val normId = candidate.id.trim().removePrefix("ytm_").lowercase(Locale.ROOT)
            if (candidate.id in excludeIds || normId in normalizedExcludes) return@filter false
            val key = "${normId}|${candidate.title.trim().lowercase(Locale.ROOT)}|${candidate.artist.trim().lowercase(Locale.ROOT)}"
            seenKeys.add(key)
        }

        val matchingArtistOrAlbum = mutableListOf<TrackItem>()
        val otherTracks = mutableListOf<TrackItem>()
        candidates.forEach { candidate ->
            val artistKey = normalizeArtistLookupName(candidate.artist)?.lowercase(Locale.ROOT)
            val albumKey = candidate.album.trim().lowercase(Locale.ROOT)
            if ((artistKey != null && artistKey in seedArtists) || (albumKey.isNotEmpty() && albumKey in seedAlbums)) {
                matchingArtistOrAlbum.add(candidate)
            } else {
                otherTracks.add(candidate)
            }
        }
        return (matchingArtistOrAlbum + otherTracks).take(20)
    }

    fun onAlbumClicked(album: AlbumItem) {
        openAlbumDetail(album)
    }

    fun openAlbumById(
        albumId: String,
        title: String = "",
        artist: String = "",
        coverUri: String? = null,
        year: Int = 0
    ) {
        val cleanId = albumId.trim()
        val cleanTitle = title.trim()
        if (cleanId.isBlank() && cleanTitle.isBlank()) return

        val matchedAlbum = if (cleanId.isNotBlank()) {
            val strippedTarget = cleanId.removePrefix("ytm_alb_").removePrefix("ytm_single_")
            val candidatePool = libraryAlbums.value +
                catalogService.localAlbums.value +
                catalogService.onlineAlbums.value +
                homeFeed.value.recommendedAlbums +
                homeFeed.value.moreLikeSections.flatMap { it.albums }
            candidatePool.firstOrNull { candidate ->
                candidate.id == cleanId ||
                    candidate.id.removePrefix("ytm_alb_").removePrefix("ytm_single_") == strippedTarget
            }
        } else {
            null
        }

        val resolvedAlbum = matchedAlbum ?: AlbumItem(
            id = cleanId,
            title = cleanTitle,
            artist = artist.trim(),
            coverUri = coverUri,
            year = year
        )
        openAlbumDetail(resolvedAlbum)
    }

    fun openAlbumDetail(album: AlbumItem) {
        val safeAlbum = sanitizeAlbumOpenRequest(album) ?: return
        val requestKey = buildAlbumRequestKey(safeAlbum)
        if (_isAlbumDetailOpen.value &&
            activeAlbumRequestKey == requestKey &&
            (albumDetailJob?.isActive == true || _currentAlbumDetail.value != null)
        ) {
            _overlayStack.update { pushOverlayLayer(it, OverlayLayer.ALBUM_DETAIL) }
            return
        }

        val generation = ++albumRequestGeneration
        activeAlbumRequestKey = requestKey
        albumDetailJob?.cancel()
        albumAmbientJob?.cancel()
        _isAlbumDetailOpen.value = true
        _overlayStack.update { pushOverlayLayer(it, OverlayLayer.ALBUM_DETAIL) }

        val artistName = normalizeArtistLookupName(safeAlbum.artist).orEmpty()
        val cachedYtmAlbum = youTubeMusicService.peekAlbumDetail(safeAlbum.id, safeAlbum.title, artistName)
        val cachedCanvas = appleMusicCanvasService.peekAlbumCanvasArtwork(safeAlbum.title, artistName)

        _isAlbumDetailLoading.value = cachedYtmAlbum == null
        _currentAlbumDetail.value = cachedYtmAlbum?.copy(
            canvasVideoUri = cachedCanvas?.videoUri.orEmpty(),
            isTracksLoading = false,
            isCanvasLoading = cachedCanvas == null
        ) ?: AlbumDetailProfile(
            id = safeAlbum.id,
            title = safeAlbum.title,
            artist = safeAlbum.artist,
            coverArtUri = safeAlbum.coverUri.orEmpty(),
            releaseYear = safeAlbum.year,
            albumInformation = if (safeAlbum.year > 0) "${safeAlbum.year}" else "",
            canvasVideoUri = cachedCanvas?.videoUri.orEmpty(),
            isTracksLoading = true,
            isCanvasLoading = cachedCanvas == null
        )

        var lastAmbientCoverUri: String? = null
        val initialCover = _currentAlbumDetail.value?.coverArtUri
        if (!initialCover.isNullOrBlank()) {
            lastAmbientCoverUri = initialCover
            val cachedAmbient = ArtworkColorExtractor.getCachedArtistAmbientColors(initialCover)
            if (cachedAmbient != null) {
                _currentAlbumAmbientColors.value = cachedAmbient
            } else {
                _currentAlbumAmbientColors.value = DEFAULT_DETAIL_AMBIENT_COLORS
                albumAmbientJob = viewModelScope.launch {
                    val ambient = ArtworkColorExtractor.extractArtistAmbientColors(getApplication(), initialCover)
                    if (generation == albumRequestGeneration) {
                        _currentAlbumAmbientColors.value = ambient
                    }
                }
            }
        } else {
            _currentAlbumAmbientColors.value = DEFAULT_DETAIL_AMBIENT_COLORS
        }

        albumDetailJob = viewModelScope.launch {
            try {
                catalogService.observeAlbumDetail(safeAlbum)
                    .catch { _ -> /* Keep preview state */ }
                    .collect { detail ->
                        if (generation != albumRequestGeneration) return@collect
                        _currentAlbumDetail.value = detail
                        _isAlbumDetailLoading.value = detail.isTracksLoading

                        if (detail.hasCanvasVideo && !detail.canvasVideoUri.contains(".m3u8", ignoreCase = true)) {
                            launch {
                                MediaNetworkClients.warmCanvasVideoCache(getApplication(), detail.canvasVideoUri)
                            }
                        }

                        val cover = detail.coverArtUri
                        if (cover.isNotBlank() && cover != lastAmbientCoverUri) {
                            lastAmbientCoverUri = cover
                            val cached = ArtworkColorExtractor.getCachedArtistAmbientColors(cover)
                            if (cached != null) {
                                _currentAlbumAmbientColors.value = cached
                            } else {
                                albumAmbientJob?.cancel()
                                albumAmbientJob = viewModelScope.launch {
                                    val ambient = ArtworkColorExtractor.extractArtistAmbientColors(
                                        getApplication(),
                                        cover
                                    )
                                    if (generation == albumRequestGeneration) {
                                        _currentAlbumAmbientColors.value = ambient
                                    }
                                }
                            }
                        }
                    }
            } finally {
                if (generation == albumRequestGeneration) {
                    _isAlbumDetailLoading.value = false
                }
            }
        }
    }

    fun closeAlbumDetail() {
        albumRequestGeneration++
        activeAlbumRequestKey = null
        albumDetailJob?.cancel()
        albumAmbientJob?.cancel()
        _isAlbumDetailOpen.value = false
        _isAlbumDetailLoading.value = false
        _currentAlbumDetail.value = null
        _overlayStack.update { removeOverlayLayer(it, OverlayLayer.ALBUM_DETAIL) }
    }

    fun playAlbumTracks(tracks: List<TrackItem>? = null, startIndex: Int = 0) {
        val targetTracks = tracks ?: _currentAlbumDetail.value?.tracks.orEmpty()
        playQueue(targetTracks, startIndex)
    }

    fun shuffleAlbumTracks(tracks: List<TrackItem>? = null) {
        val targetTracks = tracks ?: _currentAlbumDetail.value?.tracks.orEmpty()
        playQueue(targetTracks.shuffled(), 0)
    }

    fun onGenreSelected(genre: String) {
        viewModelScope.launch {
            catalogService.selectGenre(genre)
        }
    }

    fun onArtistClicked(artist: ArtistItem) {
        openArtistDetail(artist)
    }

    fun openArtistDetail(artist: ArtistItem) {
        openArtistByName(artist.name, artist.avatarUri)
    }

    fun prefetchArtist(artistName: String) {
        val normalized = normalizeArtistLookupName(artistName) ?: return
        appleMusicCanvasService.prefetchArtists(listOf(normalized), maxCount = 1)
        youTubeMusicService.prefetchArtistProfile(normalized)
    }

    fun openArtistByName(artistName: String, existingAvatarUri: String? = null) {
        val primaryArtist = normalizeArtistLookupName(artistName) ?: return
        val requestKey = primaryArtist.lowercase(Locale.ROOT)
        if (_isArtistDetailOpen.value &&
            activeArtistRequestKey == requestKey &&
            (artistDetailJob?.isActive == true || _currentArtistDetail.value != null)
        ) {
            _overlayStack.update { pushOverlayLayer(it, OverlayLayer.ARTIST_DETAIL) }
            return
        }

        val generation = ++artistRequestGeneration
        activeArtistRequestKey = requestKey
        artistDetailJob?.cancel()
        artistAmbientJob?.cancel()
        artistVideoJob?.cancel()

        _isArtistBioExpanded.value = false
        _isArtistDetailOpen.value = true
        _overlayStack.update { pushOverlayLayer(it, OverlayLayer.ARTIST_DETAIL) }

        // Synchronously seed any already-cached Apple assets & YTM profile to avoid UI flicker
        val cachedApple = appleMusicCanvasService.peekArtistAssets(primaryArtist)
        val cachedYtm = youTubeMusicService.peekArtistProfile(primaryArtist)

        val seedAvatar = cachedYtm?.avatarUri?.ifBlank { null } ?: existingAvatarUri.orEmpty()
        val seedBanner = cachedYtm?.bannerImageUri?.ifBlank { null }
            ?: cachedApple?.bannerUri?.ifBlank { null }
            ?: seedAvatar
        val seedVideo = cachedApple?.headerVideoUri.orEmpty()
        val seedHexColor = cachedApple?.bgColorHex?.ifBlank { null } ?: cachedApple?.keyColorHex ?: ""

        _isArtistDetailLoading.value = cachedYtm == null
        _currentArtistVideoUri.value = seedVideo.ifBlank { null }

        _currentArtistDetail.value = ArtistDetailProfile(
            name = cachedYtm?.name?.ifBlank { primaryArtist } ?: primaryArtist,
            avatarUri = seedAvatar,
            bannerImageUri = seedBanner,
            customTitleLogoUri = cachedApple?.wordmarkUri.orEmpty(),
            headerVideoUri = seedVideo,
            subscribersCountText = cachedYtm?.subscribersCountText.orEmpty(),
            monthlyListenersText = cachedYtm?.monthlyListenersText.orEmpty(),
            biography = cachedYtm?.biography.orEmpty(),
            metadataSubtitle = cachedYtm?.metadataSubtitle.orEmpty(),
            topTracks = cachedYtm?.topTracks ?: emptyList(),
            combinedAlbums = cachedYtm?.combinedAlbums ?: emptyList(),
            singlesAndEps = cachedYtm?.singlesAndEps ?: emptyList(),
            artistPlaylists = cachedYtm?.artistPlaylists ?: emptyList(),
            similarArtists = cachedYtm?.similarArtists ?: emptyList(),
            latestRelease = cachedYtm?.latestRelease,
            latestReleaseTypeBadge = cachedYtm?.latestReleaseTypeBadge.orEmpty(),
            latestReleaseDateText = cachedYtm?.latestReleaseDateText.orEmpty(),
            officialVideos = cachedYtm?.officialVideos ?: emptyList(),
            isHeaderMediaLoading = cachedApple == null,
            isCatalogLoading = cachedYtm == null,
            keyColorRgb = seedHexColor
        )

        var ambientPaletteLoadedFromImage = false
        var lastAmbientImageUri: String? = null
        val seedAmbientImg = seedBanner.ifBlank { seedAvatar }

        ArtworkColorExtractor.getCachedArtistAmbientColors(seedAmbientImg)?.let {
            ambientPaletteLoadedFromImage = true
            lastAmbientImageUri = seedAmbientImg
            _currentArtistAmbientColors.value = it
            _currentArtistAmbientColor.value = it.dark
        } ?: ArtworkColorExtractor.artistAmbientColorsFromHex(
            cachedApple?.bgColorHex,
            cachedApple?.keyColorHex
        )?.let {
            _currentArtistAmbientColors.value = it
            _currentArtistAmbientColor.value = it.dark
        } ?: if (seedAmbientImg.isNotBlank()) {
            lastAmbientImageUri = seedAmbientImg
            _currentArtistAmbientColors.value = DEFAULT_DETAIL_AMBIENT_COLORS
            _currentArtistAmbientColor.value = DEFAULT_DETAIL_AMBIENT_COLORS.dark
            artistAmbientJob = viewModelScope.launch {
                val ambient = ArtworkColorExtractor.extractArtistAmbientColors(getApplication(), seedAmbientImg)
                if (generation == artistRequestGeneration) {
                    ambientPaletteLoadedFromImage = true
                    _currentArtistAmbientColors.value = ambient
                    _currentArtistAmbientColor.value = ambient.dark
                }
            }
        } else {
            _currentArtistAmbientColors.value = DEFAULT_DETAIL_AMBIENT_COLORS
            _currentArtistAmbientColor.value = DEFAULT_DETAIL_AMBIENT_COLORS.dark
        }

        artistDetailJob = viewModelScope.launch {
            try {
                catalogService.observeArtistDetail(primaryArtist, existingAvatarUri)
                    .catch { e -> e.printStackTrace() }
                    .collect { detail ->
                        if (generation != artistRequestGeneration) return@collect
                        _currentArtistDetail.value = detail
                        _isArtistDetailLoading.value = detail.isCatalogLoading

                        // Emit video URI immediately with zero artificial delay
                        if (detail.hasVideo) {
                            if (_currentArtistVideoUri.value != detail.headerVideoUri) {
                                _currentArtistVideoUri.value = detail.headerVideoUri
                            }
                            if (!detail.headerVideoUri.contains(".m3u8", ignoreCase = true)) {
                                launch {
                                    MediaNetworkClients.warmCanvasVideoCache(
                                        getApplication(),
                                        detail.headerVideoUri
                                    )
                                }
                            }
                        }

                        if (detail.hasCustomWordmark) {
                            MediaNetworkClients.prefetchImage(getApplication(), detail.customTitleLogoUri)
                        }

                        if (!ambientPaletteLoadedFromImage && detail.keyColorRgb.isNotBlank()) {
                            ArtworkColorExtractor.artistAmbientColorsFromHex(detail.keyColorRgb)?.let { hexColors ->
                                _currentArtistAmbientColors.value = hexColors
                                _currentArtistAmbientColor.value = hexColors.dark
                            }
                        }

                        val effectiveImg = detail.bannerImageUri.ifBlank { detail.avatarUri }
                        if (effectiveImg.isNotBlank() && effectiveImg != lastAmbientImageUri) {
                            lastAmbientImageUri = effectiveImg
                            val cached = ArtworkColorExtractor.getCachedArtistAmbientColors(effectiveImg)
                            if (cached != null) {
                                ambientPaletteLoadedFromImage = true
                                _currentArtistAmbientColors.value = cached
                                _currentArtistAmbientColor.value = cached.dark
                            } else {
                                artistAmbientJob?.cancel()
                                artistAmbientJob = viewModelScope.launch {
                                    val ambient = ArtworkColorExtractor.extractArtistAmbientColors(
                                        getApplication(),
                                        effectiveImg
                                    )
                                    if (generation == artistRequestGeneration) {
                                        ambientPaletteLoadedFromImage = true
                                        _currentArtistAmbientColors.value = ambient
                                        _currentArtistAmbientColor.value = ambient.dark
                                    }
                                }
                            }
                        }
                    }
            } finally {
                if (generation == artistRequestGeneration) {
                    _isArtistDetailLoading.value = false
                }
            }
        }
    }

    fun closeArtistDetail() {
        artistRequestGeneration++
        activeArtistRequestKey = null
        artistDetailJob?.cancel()
        artistAmbientJob?.cancel()
        artistVideoJob?.cancel()
        _isArtistDetailOpen.value = false
        _isArtistDetailLoading.value = false
        _currentArtistVideoUri.value = null
        _overlayStack.update { removeOverlayLayer(it, OverlayLayer.ARTIST_DETAIL) }
    }

    fun toggleArtistBioExpanded() {
        _isArtistBioExpanded.update { !it }
    }

    fun playArtistTopTracks() {
        playQueue(_currentArtistDetail.value?.topTracks.orEmpty(), 0)
    }

    fun shuffleArtistTopTracks() {
        val tracks = _currentArtistDetail.value?.topTracks.orEmpty()
        playQueue(tracks.shuffled(), 0)
    }

    fun playArtistMix() {
        playArtistTopTracks()
    }

    fun onTogglePlayPause() {
        if (castController.castState.value.isConnected) {
            castController.togglePlayPause()
        } else {
            audioPlayerService.togglePlayPause()
        }
    }

    fun pausePlayback() {
        audioPlayerService.pause()
        if (castController.castState.value.isConnected) {
            castController.pause()
        }
    }

    fun onSkipNext() {
        if (castController.castState.value.isConnected) {
            castController.skipNext()
        } else {
            audioPlayerService.skipNext()
        }
    }

    fun onSkipPrevious() {
        if (castController.castState.value.isConnected) {
            castController.skipPrevious()
        } else {
            audioPlayerService.skipPrevious()
        }
    }

    fun onToggleShuffle() {
        if (castController.castState.value.isConnected) {
            castController.toggleShuffle()
        } else {
            audioPlayerService.toggleShuffle()
        }
    }

    fun onCycleRepeatMode() {
        if (castController.castState.value.isConnected) {
            castController.cycleRepeatMode()
        } else {
            audioPlayerService.cycleRepeatMode()
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        audioPlayerService.moveQueueItem(fromIndex, toIndex)
    }

    fun playQueueIndex(index: Int) {
        if (castController.castState.value.isConnected) {
            castController.jumpToQueueIndex(index)
        } else {
            audioPlayerService.playQueueIndex(index)
        }
    }

    fun onOpenOutputSwitcher(activity: Activity) {
        audioOutputHelper.showOutputSwitcher(activity)
    }

    // Chromecast UI Sheet State
    fun openCastPicker() {
        _isCastControlsOpen.value = false
        _isCastPickerOpen.value = true
    }

    fun closeCastPicker() {
        _isCastPickerOpen.value = false
    }

    fun openCastControls() {
        _isCastPickerOpen.value = false
        _isCastControlsOpen.value = true
    }

    fun closeCastControls() {
        _isCastControlsOpen.value = false
    }

    fun onCastButtonClicked() {
        if (castController.castState.value.isConnected) {
            openCastControls()
        } else {
            openCastPicker()
        }
    }

    // Set as Ringtone Editor State & Audio Source Resolver
    fun openRingtoneEditor(track: TrackItem) {
        _trackForRingtoneEditor.value = track
    }

    fun closeRingtoneEditor() {
        _trackForRingtoneEditor.value = null
    }

    suspend fun resolveLocalAudioUriForRingtone(track: TrackItem): Uri? = withContext(Dispatchers.IO) {
        val effective = resolveEffectiveTrack(track)
        val rawUrl = effective.audioUrl?.trim()
        if (!rawUrl.isNullOrBlank()) {
            when {
                rawUrl.startsWith("content://") || rawUrl.startsWith("file://") -> {
                    return@withContext Uri.parse(rawUrl)
                }
                rawUrl.startsWith("/") -> {
                    val f = File(rawUrl)
                    if (f.exists() && f.length() > 0L) {
                        return@withContext Uri.fromFile(f)
                    }
                }
            }
        }
        val rawVideoId = when {
            effective.id.startsWith("ytm_") -> effective.id.removePrefix("ytm_")
            rawUrl?.contains("v=") == true -> rawUrl.substringAfter("v=").substringBefore("&")
            else -> null
        } ?: youTubeMusicService.searchSongs("${effective.artist} - ${effective.title}")
            .firstOrNull()?.id?.removePrefix("ytm_")

        if (!rawVideoId.isNullOrBlank()) {
            val cached = youTubeMusicService.getOrDownloadAudioFile(rawVideoId, getApplication())
            if (cached != null && cached.exists() && cached.length() > 0L) {
                return@withContext Uri.fromFile(cached)
            }
        }
        null
    }

    // Home Screen Widget Deep-Link Handler
    fun handleWidgetDeepLink(deepLink: WidgetDeepLink) {
        widgetDeepLinkJob?.cancel()
        when (deepLink) {
            WidgetDeepLink.OpenPlayer -> {
                if (playbackState.value.currentTrack != null) {
                    openPlayer()
                } else {
                    widgetDeepLinkJob = viewModelScope.launch {
                        if (!isInitialHomeReady.value) {
                            isInitialHomeReady.first { it }
                        }
                        if (playbackState.value.currentTrack != null) {
                            openPlayer()
                        } else {
                            val fallback = homeFeed.value.recentlyPlayedTracks
                                .ifEmpty { favoritesRepository.likedTracks.value }
                                .ifEmpty { librarySongs.value }
                            if (fallback.isNotEmpty()) {
                                playQueue(fallback, 0)
                                openPlayer()
                            } else {
                                onTabSelected(NavigationTab.Home)
                            }
                        }
                    }
                }
            }
            WidgetDeepLink.OpenAmbient -> {
                if (playbackState.value.currentTrack != null) {
                    openAmbientMode()
                } else {
                    widgetDeepLinkJob = viewModelScope.launch {
                        if (!isInitialHomeReady.value) {
                            isInitialHomeReady.first { it }
                        }
                        if (playbackState.value.currentTrack == null) {
                            val fallback = homeFeed.value.recentlyPlayedTracks
                                .ifEmpty { favoritesRepository.likedTracks.value }
                                .ifEmpty { librarySongs.value }
                            if (fallback.isNotEmpty()) {
                                playQueue(fallback, 0)
                            }
                        }
                        if (playbackState.value.currentTrack != null) {
                            openAmbientMode()
                        }
                    }
                }
            }
            WidgetDeepLink.OpenHome -> {
                closeAmbientMode()
                closePlayer()
                onTabSelected(NavigationTab.Home)
            }
            WidgetDeepLink.RefreshRecommendations -> {
                refreshHomeFeed()
            }
            is WidgetDeepLink.OpenPlaylist -> {
                widgetDeepLinkJob = viewModelScope.launch {
                    if (!isInitialHomeReady.value) {
                        isInitialHomeReady.first { it }
                    }
                    when (deepLink.sourceType) {
                        WidgetPlaylistSourceType.USER_PLAYLIST -> {
                            val pl = playlistRepository.getPlaylistById(deepLink.id)
                                ?: playlists.value.firstOrNull { it.title.equals(deepLink.title, ignoreCase = true) }
                            if (pl != null) {
                                closeAmbientMode()
                                closePlayer()
                                openPlaylistDetail(pl)
                                if (deepLink.autoPlay && pl.tracks.isNotEmpty()) {
                                    playPlaylistTracks(pl, 0)
                                }
                            } else {
                                onTabSelected(NavigationTab.Playlists)
                            }
                        }
                        WidgetPlaylistSourceType.SMART_PLAYLIST -> {
                            val pool = smartSuggestedPlaylists.value.ifEmpty { homeFeed.value.smartSuggestedPlaylists }
                            val pl = pool.firstOrNull { it.id == deepLink.id }
                                ?: pool.firstOrNull { it.title.equals(deepLink.title, ignoreCase = true) }
                            if (pl != null) {
                                closeAmbientMode()
                                closePlayer()
                                openPlaylistDetail(pl)
                                if (deepLink.autoPlay && pl.tracks.isNotEmpty()) {
                                    playPlaylistTracks(pl, 0)
                                }
                            } else {
                                onTabSelected(NavigationTab.Playlists)
                            }
                        }
                        WidgetPlaylistSourceType.MADE_FOR_YOU -> {
                            val matched = homeFeed.value.playlistsMadeForYou.firstOrNull {
                                it.id == deepLink.id || it.playlistId == deepLink.ytmPlaylistId
                            } ?: RecommendedPlaylistItem(
                                id = deepLink.id,
                                title = deepLink.title.ifBlank { "Playlist" },
                                subtitle = "",
                                artworkUri = null,
                                playlistId = deepLink.ytmPlaylistId ?: deepLink.id,
                                browseId = deepLink.browseId
                            )
                            onRecommendedPlaylistClick(matched)
                            if (deepLink.autoPlay) {
                                openPlayer()
                            }
                        }
                        WidgetPlaylistSourceType.RADIO_STATION -> {
                            val matched = (homeFeed.value.allRadioStations.ifEmpty { homeFeed.value.radioStations })
                                .firstOrNull {
                                    it.id == deepLink.id || it.playlistId == deepLink.ytmPlaylistId
                                } ?: RadioStationItem(
                                id = deepLink.id,
                                title = deepLink.title.ifBlank { "Radio" },
                                subtitle = "",
                                artworkUri = null,
                                playlistId = deepLink.ytmPlaylistId ?: deepLink.id,
                                seedVideoId = deepLink.seedVideoId,
                                params = deepLink.params
                            )
                            onRadioStationClick(matched)
                            if (deepLink.autoPlay) {
                                openPlayer()
                            }
                        }
                        WidgetPlaylistSourceType.LIKED_SONGS -> {
                            closeAmbientMode()
                            closePlayer()
                            onTabSelected(NavigationTab.Liked)
                            if (deepLink.autoPlay && favoritesRepository.likedTracks.value.isNotEmpty()) {
                                playLikedTracks()
                            }
                        }
                        WidgetPlaylistSourceType.RECENTLY_PLAYED -> {
                            val recent = homeFeed.value.recentlyPlayedTracks
                            if (deepLink.autoPlay && recent.isNotEmpty()) {
                                playQueue(recent, 0)
                                openPlayer()
                            } else {
                                onTabSelected(NavigationTab.Home)
                            }
                        }
                    }
                }
            }
        }
    }

    fun onTabSelected(tab: NavigationTab) {
        if (tab != NavigationTab.Library &&
            (_activeLibrarySubView.value != null || _selectedCategory.value != SearchCategoryFilter.ALL)
        ) {
            openLibrarySubView(null)
        }
        if (_overlayStack.value.isNotEmpty()) {
            if (_isAlbumDetailOpen.value) closeAlbumDetail()
            if (_isArtistDetailOpen.value) closeArtistDetail()
            if (_isPlaylistDetailOpen.value) closePlaylistDetail()
            if (_homeGridCategory.value != null) closeHomeGrid()
            if (_isSearchActive.value) closeSearch()
        }
        _selectedTab.value = tab
    }

    fun openSearch() {
        _recentSearches.value = catalogService.getRecentSearches()
        _isSearchActive.value = true
        _overlayStack.update { pushOverlayLayer(it, OverlayLayer.SEARCH) }
        loadMoodsAndGenres()
    }

    fun closeSearch() {
        _isSearchActive.value = false
        _overlayStack.update { removeOverlayLayer(it, OverlayLayer.SEARCH) }
        _searchQuery.value = ""
        _searchResults.value = SearchResultData()
        clearMoodCategory()
        searchDebounceJob?.cancel()
    }

    fun loadMoodsAndGenres(forceRefresh: Boolean = false) {
        val existing = _moodsAndGenresFeed.value
        if (!forceRefresh && (moodsFetchJob?.isActive == true || existing?.isFromYouTubeMusic == true)) {
            return
        }
        moodsFetchJob?.cancel()
        moodsFetchJob = viewModelScope.launch {
            _isLoadingMoods.value = existing?.sections.isNullOrEmpty()
            try {
                val feed = youTubeMusicService.fetchMoodsAndGenres()
                if (feed.sections.isNotEmpty()) {
                    _moodsAndGenresFeed.value = feed
                } else if (_moodsAndGenresFeed.value == null) {
                    _moodsAndGenresFeed.value = buildFallbackMoodsAndGenresFeed()
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                if (_moodsAndGenresFeed.value == null) {
                    _moodsAndGenresFeed.value = buildFallbackMoodsAndGenresFeed()
                }
            } finally {
                _isLoadingMoods.value = false
            }
        }
    }

    fun selectMoodCategory(category: YtmMoodCategory) {
        moodDetailJob?.cancel()
        _selectedMoodCategory.value = category
        _moodDetailResult.value = null
        _isLoadingMoodDetail.value = true
        moodDetailJob = viewModelScope.launch {
            try {
                val detail = youTubeMusicService.fetchMoodCategoryDetail(
                    browseId = category.browseId,
                    params = category.params,
                    categoryTitle = category.title
                )
                if (_selectedMoodCategory.value?.id == category.id) {
                    _moodDetailResult.value = detail
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                if (_selectedMoodCategory.value?.id == category.id) {
                    _moodDetailResult.value = YtmMoodCategoryDetail(
                        categoryTitle = category.title,
                        browseId = category.browseId,
                        params = category.params
                    )
                }
            } finally {
                if (_selectedMoodCategory.value?.id == category.id) {
                    _isLoadingMoodDetail.value = false
                }
            }
        }
    }

    fun clearMoodCategory() {
        moodDetailJob?.cancel()
        _selectedMoodCategory.value = null
        _moodDetailResult.value = null
        _isLoadingMoodDetail.value = false
    }

    private fun buildFallbackMoodsAndGenresFeed(): YtmMoodsAndGenresFeed {
        val genreItems = GenreCatalog.allGenres.map { genre ->
            YtmMoodCategory(
                id = "genre_${genre.id}",
                title = genre.name,
                browseId = "FEmusic_moods_and_genres_category",
                params = genre.id,
                stripeColorArgb = genre.primaryColor.toArgb().toLong() and 0xFFFFFFFFL
            )
        }
        return YtmMoodsAndGenresFeed(
            sections = if (genreItems.isNotEmpty()) {
                listOf(YtmMoodSection(title = "Genres", items = genreItems))
            } else {
                emptyList()
            },
            isFromYouTubeMusic = false
        )
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        searchDebounceJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = SearchResultData()
            _isSearching.value = false
            return
        }

        searchDebounceJob = viewModelScope.launch {
            delay(350)
            executeSearchInternal(query, _searchFilter.value)
        }
    }

    fun onSearchFilterChange(filter: SearchFilter) {
        _searchFilter.value = filter
        val query = _searchQuery.value
        if (query.isNotBlank()) {
            searchDebounceJob?.cancel()
            viewModelScope.launch {
                executeSearchInternal(query, filter)
            }
        }
    }

    fun submitSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        _searchQuery.value = trimmed
        _recentSearches.value = catalogService.addRecentSearch(trimmed)
        searchDebounceJob?.cancel()
        viewModelScope.launch {
            executeSearchInternal(trimmed, _searchFilter.value)
        }
    }

    private suspend fun executeSearchInternal(query: String, filter: SearchFilter) {
        _isSearching.value = true
        try {
            val results = catalogService.searchYouTubeMusic(query, filter)
            _searchResults.value = results
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            _isSearching.value = false
        }
    }

    fun selectRecentSearch(query: String) {
        submitSearch(query)
    }

    fun removeRecentSearch(query: String) {
        _recentSearches.value = catalogService.removeRecentSearch(query)
    }

    fun clearRecentSearches() {
        _recentSearches.value = catalogService.clearRecentSearches()
    }

    fun selectGenreSearch(genreName: String) {
        submitSearch(genreName)
    }

    // Full Screen Now Playing & Ambient Mode Actions
    fun openPlayer() {
        if (playbackState.value.currentTrack != null) {
            _isPlayerExpanded.value = true
        }
    }

    fun closePlayer() {
        _isPlayerExpanded.value = false
    }

    fun openAmbientMode() {
        if (playbackState.value.currentTrack != null) {
            _isAmbientModeOpen.value = true
        }
    }

    fun closeAmbientMode() {
        _isAmbientModeOpen.value = false
    }

    fun seekTo(fraction: Float) {
        val duration = playbackState.value.durationMs
        if (duration > 0) {
            val targetMs = (fraction * duration).toLong()
            if (castController.castState.value.isConnected) {
                castController.seekTo(targetMs)
            } else {
                audioPlayerService.seekTo(targetMs)
            }
        }
    }

    // Favorites & Liked tracks management
    fun refreshLikedRecommendations() {
        likedRecommendationsJob?.cancel()
        likedRecommendationsJob = viewModelScope.launch {
            _isLoadingLikedRecommendations.value = true
            try {
                val recs = catalogService.getRecommendationsForLiked(favoritesRepository.likedTracks.value)
                _likedRecommendations.value = recs
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoadingLikedRecommendations.value = false
            }
        }
    }

    fun toggleFavorite(track: TrackItem) {
        viewModelScope.launch {
            favoritesRepository.toggleFavorite(track)
            catalogService.updateLikedSeeds(favoritesRepository.likedTracks.value)
            refreshLikedRecommendations()
        }
    }

    fun toggleFavorite(trackId: String, fallback: TrackItem? = null) {
        viewModelScope.launch {
            val resolvedTrack = fallback
                ?: playbackState.value.currentTrack?.takeIf { it.id == trackId }
                ?: librarySongs.value.firstOrNull { it.id == trackId }
                ?: homeFeed.value.recommendedTracks.firstOrNull { it.id == trackId }
                ?: homeFeed.value.recentlyPlayedTracks.firstOrNull { it.id == trackId }
                ?: homeFeed.value.genreTracks.firstOrNull { it.id == trackId }
                ?: currentAlbumDetail.value?.tracks?.firstOrNull { it.id == trackId }
                ?: currentArtistDetail.value?.topTracks?.firstOrNull { it.id == trackId }
                ?: searchResults.value.songs.firstOrNull { it.id == trackId }
                ?: moodDetailResult.value?.songs?.firstOrNull { it.id == trackId }
                ?: favoritesRepository.likedTracks.value.firstOrNull { it.id == trackId }

            if (resolvedTrack != null) {
                favoritesRepository.toggleFavorite(resolvedTrack)
            } else {
                favoritesRepository.toggleFavoriteById(trackId, null)
            }
            catalogService.updateLikedSeeds(favoritesRepository.likedTracks.value)
            refreshLikedRecommendations()
        }
    }

    fun playLikedTracks(tracks: List<TrackItem>? = null, startIndex: Int = 0) {
        val targetTracks = tracks ?: favoritesRepository.likedTracks.value
        playQueue(targetTracks, startIndex)
    }

    fun shuffleLikedTracks(tracks: List<TrackItem>? = null) {
        val targetTracks = tracks ?: favoritesRepository.likedTracks.value
        playQueue(targetTracks.shuffled(), 0)
    }

    fun playLikedTrack(track: TrackItem) {
        val tracks = favoritesRepository.likedTracks.value
        val idx = tracks.indexOfFirst { it.id == track.id }
        if (idx >= 0) {
            playQueue(tracks, idx)
        } else {
            onTrackClicked(track)
        }
    }

    // Playlist Management Actions
    fun openPlaylistDetail(playlist: UserPlaylist) {
        _currentPlaylistDetail.value = playlistRepository.getPlaylistById(playlist.id) ?: playlist
        _isPlaylistDetailOpen.value = true
        _overlayStack.update { pushOverlayLayer(it, OverlayLayer.PLAYLIST_DETAIL) }
    }

    fun closePlaylistDetail() {
        _isPlaylistDetailOpen.value = false
        _currentPlaylistDetail.value = null
        _overlayStack.update { removeOverlayLayer(it, OverlayLayer.PLAYLIST_DETAIL) }
    }

    fun openCreatePlaylistDialog(playlist: UserPlaylist? = null) {
        _playlistToEdit.value = playlist
        _isCreatePlaylistDialogOpen.value = true
    }

    fun closeCreatePlaylistDialog() {
        _isCreatePlaylistDialogOpen.value = false
        _playlistToEdit.value = null
    }

    fun savePlaylist(
        title: String,
        description: String,
        customCoverPath: String?,
        playlistIdToEdit: String? = null
    ) {
        viewModelScope.launch {
            val targetId = playlistIdToEdit ?: _playlistToEdit.value?.id
            if (targetId != null) {
                playlistRepository.updatePlaylist(targetId, title, description, customCoverPath.orEmpty())
            } else {
                val initialTrack = _trackForAddToPlaylist.value
                playlistRepository.createPlaylist(
                    title = title,
                    description = description,
                    customCoverPath = customCoverPath.orEmpty(),
                    initialTrack = initialTrack
                )
                if (initialTrack != null) {
                    _trackForAddToPlaylist.value = null
                }
            }
            closeCreatePlaylistDialog()
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlistId)
            if (_currentPlaylistDetail.value?.id == playlistId) {
                closePlaylistDetail()
            }
        }
    }

    fun requestDeletePlaylist(playlist: UserPlaylist) {
        _playlistPendingDelete.value = playlist
    }

    fun cancelDeletePlaylist() {
        _playlistPendingDelete.value = null
    }

    /**
     * Confirms removal of a playlist from Xperience Player.
     * Strictly local: deletes only from PlaylistRepository and never calls any YouTube Music API.
     */
    fun confirmDeletePlaylist() {
        val target = _playlistPendingDelete.value ?: return
        _playlistPendingDelete.value = null
        deletePlaylist(target.id)
    }

    // YouTube Music Playlist Import & Refresh Actions
    fun openYtmPlaylistImport() {
        _ytmImportError.value = null
        _ytmImportProgressText.value = null
        _isYtmPlaylistImportOpen.value = true
        loadYtmLibraryPlaylists()
    }

    fun loadYtmLibraryPlaylists() {
        ytmLibraryPlaylistsLoadJob?.cancel()
        if (!catalogService.authManager.isSignedIn()) {
            _ytmLibraryPlaylists.value = emptyList()
            _selectedYtmPlaylistIds.value = emptySet()
            _ytmImportError.value = "Sign in to YouTube Music to load your playlists."
            _isLoadingYtmLibraryPlaylists.value = false
            return
        }

        ytmLibraryPlaylistsLoadJob = viewModelScope.launch {
            _isLoadingYtmLibraryPlaylists.value = true
            _ytmImportError.value = null
            try {
                val fetched = youTubeMusicService.fetchUserLibraryPlaylists()
                _ytmLibraryPlaylists.value = fetched

                val existingExternalIds = playlistRepository.playlists.value
                    .mapNotNull { pl ->
                        pl.externalPlaylistId
                            .takeIf { it.isNotBlank() }
                            ?.let { PlaylistRepository.normalizeYouTubePlaylistId(it).lowercase() }
                    }
                    .toSet()

                val notYetImportedIds = fetched
                    .map { it.playlistId }
                    .filter { id ->
                        PlaylistRepository.normalizeYouTubePlaylistId(id).lowercase() !in existingExternalIds
                    }
                    .toSet()

                _selectedYtmPlaylistIds.value = notYetImportedIds
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                _ytmImportError.value = e.message?.takeIf { it.isNotBlank() }
                    ?: "Could not load your YouTube Music playlists. Please try again."
            } finally {
                _isLoadingYtmLibraryPlaylists.value = false
            }
        }
    }

    fun closeYtmPlaylistImport() {
        ytmLibraryPlaylistsLoadJob?.cancel()
        ytmLibraryPlaylistsLoadJob = null
        ytmPlaylistImportJob?.cancel()
        ytmPlaylistImportJob = null
        _isLoadingYtmLibraryPlaylists.value = false
        _isImportingYtmPlaylists.value = false
        _ytmImportProgressText.value = null
        _ytmImportError.value = null
        _isYtmPlaylistImportOpen.value = false
    }

    fun dismissYtmImportStatusMessage() {
        _ytmImportStatusMessage.value = null
    }

    fun toggleYtmPlaylistSelection(playlistId: String) {
        if (_isImportingYtmPlaylists.value) return
        _selectedYtmPlaylistIds.update { current ->
            if (playlistId in current) current - playlistId else current + playlistId
        }
    }

    fun selectAllYtmPlaylists() {
        if (_isImportingYtmPlaylists.value) return
        _selectedYtmPlaylistIds.value = _ytmLibraryPlaylists.value.map { it.playlistId }.toSet()
    }

    fun clearYtmPlaylistSelection() {
        if (_isImportingYtmPlaylists.value) return
        _selectedYtmPlaylistIds.value = emptySet()
    }

    fun importSelectedYtmPlaylists() {
        if (_isImportingYtmPlaylists.value) return
        if (!catalogService.authManager.isSignedIn()) {
            _ytmImportError.value = "Sign in to YouTube Music to import playlists."
            return
        }
        val selectedIds = _selectedYtmPlaylistIds.value
        val toImport = _ytmLibraryPlaylists.value.filter { it.playlistId in selectedIds }
        if (toImport.isEmpty()) return

        ytmPlaylistImportJob?.cancel()
        ytmPlaylistImportJob = viewModelScope.launch {
            _isImportingYtmPlaylists.value = true
            _ytmImportError.value = null
            _ytmImportStatusMessage.value = null

            val account = ytmAccountState.value
            val accountLabel = account.accountHandle?.takeIf { it.isNotBlank() }
                ?: account.accountName?.takeIf { it.isNotBlank() }
                ?: ""

            var importedCount = 0
            try {
                toImport.forEachIndexed { index, ytmPlaylist ->
                    _ytmImportProgressText.value = "Importing ${index + 1} of ${toImport.size}…"
                    val tracks = youTubeMusicService.fetchUserPlaylistTracks(ytmPlaylist)
                    playlistRepository.importYouTubeMusicPlaylist(
                        externalPlaylistId = ytmPlaylist.playlistId,
                        externalBrowseId = ytmPlaylist.browseId,
                        title = ytmPlaylist.title,
                        description = ytmPlaylist.subtitle.ifBlank { ytmPlaylist.author },
                        coverUrl = ytmPlaylist.artworkUri,
                        tracks = tracks,
                        accountLabel = accountLabel
                    )
                    importedCount++
                }

                if (importedCount > 0) {
                    _ytmImportStatusMessage.value = if (importedCount == 1) {
                        "Imported 1 playlist from YouTube Music"
                    } else {
                        "Imported $importedCount playlists from YouTube Music"
                    }
                    _selectedYtmPlaylistIds.value = emptySet()
                    _isYtmPlaylistImportOpen.value = false
                } else {
                    _ytmImportError.value = "Could not import selected playlists. Please try again."
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                _ytmImportError.value = e.message?.takeIf { it.isNotBlank() }
                    ?: "Failed to import playlists from YouTube Music."
            } finally {
                _isImportingYtmPlaylists.value = false
                _ytmImportProgressText.value = null
            }
        }
    }

    fun refreshImportedPlaylist(playlist: UserPlaylist) {
        if (!playlist.isFromYouTubeMusic || !catalogService.authManager.isSignedIn()) return
        if (_isRefreshingImportedPlaylist.value) return

        refreshImportedPlaylistJob?.cancel()
        refreshImportedPlaylistJob = viewModelScope.launch {
            _isRefreshingImportedPlaylist.value = true
            try {
                val cleanId = PlaylistRepository.normalizeYouTubePlaylistId(
                    playlist.externalPlaylistId.ifBlank { playlist.externalBrowseId }
                )
                val browseId = playlist.externalBrowseId.ifBlank {
                    if (cleanId.isNotBlank()) "VL$cleanId" else ""
                }
                if (browseId.isBlank()) return@launch

                val libraryItem = YouTubeMusicLibraryPlaylist(
                    playlistId = cleanId,
                    browseId = browseId,
                    title = playlist.title,
                    subtitle = playlist.description,
                    artworkUri = if (PlaylistRepository.isLocalCustomCoverPath(playlist.customCoverImagePath)) {
                        ""
                    } else {
                        playlist.customCoverImagePath
                    }
                )
                val tracks = youTubeMusicService.fetchUserPlaylistTracks(libraryItem)
                val account = ytmAccountState.value
                val accountLabel = account.accountHandle?.takeIf { it.isNotBlank() }
                    ?: account.accountName?.takeIf { it.isNotBlank() }
                    ?: playlist.sourceAccountLabel

                playlistRepository.importYouTubeMusicPlaylist(
                    externalPlaylistId = cleanId,
                    externalBrowseId = browseId,
                    title = playlist.title,
                    description = playlist.description,
                    coverUrl = libraryItem.artworkUri,
                    tracks = tracks,
                    accountLabel = accountLabel
                )
                _ytmImportStatusMessage.value = "Refreshed \"${playlist.title}\" from YouTube Music"
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isRefreshingImportedPlaylist.value = false
            }
        }
    }

    fun openAddToPlaylist(track: TrackItem) {
        _trackForAddToPlaylist.value = track
    }

    fun closeAddToPlaylist() {
        _trackForAddToPlaylist.value = null
    }

    fun addTrackToPlaylist(playlist: UserPlaylist, track: TrackItem) {
        viewModelScope.launch {
            playlistRepository.addTrackToPlaylist(playlist.id, track)
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        viewModelScope.launch {
            playlistRepository.removeTrackFromPlaylist(playlistId, trackId)
        }
    }

    fun playPlaylistTracks(playlist: UserPlaylist, startIndex: Int = 0) {
        playQueue(playlist.tracks, startIndex)
    }

    fun shufflePlaylistTracks(playlist: UserPlaylist) {
        playQueue(playlist.tracks.shuffled(), 0)
    }

    /**
     * Explicitly saves a suggested smart playlist into the user's persistent Playlists library.
     * Smart playlists are never saved automatically until the user invokes this action.
     */
    fun saveSuggestedPlaylistToLibrary(playlist: UserPlaylist) {
        viewModelScope.launch {
            val saved = playlistRepository.saveSmartPlaylistToLibrary(playlist)
            if (_currentPlaylistDetail.value?.id == playlist.id) {
                _currentPlaylistDetail.value = playlist.copy(isSmartSuggestion = true)
            }
        }
    }

    fun setPlayerMediaMode(mode: PlayerMediaMode) {
        if (mode == PlayerMediaMode.VIDEO) {
            val currentVideo = _currentMusicVideo.value ?: return
            _playerMediaMode.value = PlayerMediaMode.VIDEO
            val isExpiringSoon = currentVideo.expiresAtMs <= System.currentTimeMillis() + 60_000L
            if (musicVideoNeedsRefresh || isExpiringSoon) {
                musicVideoNeedsRefresh = false
                retryMusicVideoStream(automatic = false)
            }
            return
        }
        _playerMediaMode.value = mode
    }

    fun playArtistOfficialVideo(videoTrack: TrackItem) {
        val rawVideoId = videoTrack.id.removePrefix("ytm_video_").removePrefix("ytm_").trim()
        val playableTrack = videoTrack.copy(
            id = if (rawVideoId.isNotBlank()) "ytm_$rawVideoId" else videoTrack.id
        )
        onSearchTrackClicked(playableTrack)
        openPlayer()
        musicVideoResolveJob?.cancel()
        lastMusicVideoTrackId = playableTrack.id
        musicVideoRetryCountForTrack = 0
        musicVideoNeedsRefresh = false
        _isResolvingMusicVideo.value = true
        musicVideoResolveJob = viewModelScope.launch {
            try {
                val resolved = youTubeMusicService.resolveMusicVideoStream(videoTrack)
                if (playbackState.value.currentTrack?.id == playableTrack.id) {
                    _currentMusicVideo.value = resolved
                    if (resolved != null) {
                        _playerMediaMode.value = PlayerMediaMode.VIDEO
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
            } finally {
                if (playbackState.value.currentTrack?.id == playableTrack.id) {
                    _isResolvingMusicVideo.value = false
                }
            }
        }
    }

    fun retryMusicVideoStream(automatic: Boolean = true) {
        val track = playbackState.value.currentTrack ?: return
        if (automatic && musicVideoRetryCountForTrack >= 1) {
            // All candidate URLs and one full cache-busted re-resolution failed: return cleanly to Song mode
            // and refresh on the next explicit Video tap.
            musicVideoNeedsRefresh = true
            _playerMediaMode.value = PlayerMediaMode.SONG
            return
        }
        if (automatic) {
            musicVideoRetryCountForTrack++
        } else {
            musicVideoRetryCountForTrack = 0
        }
        youTubeMusicService.invalidateMusicVideoCache(
            videoId = _currentMusicVideo.value?.videoId,
            track = track
        )
        fetchMusicVideoForTrack(track = track, forceRefresh = true)
    }

    private fun fetchMusicVideoForTrack(track: TrackItem, forceRefresh: Boolean = false) {
        val autoVideo = track.id.startsWith("ytm_video_")
        if (!forceRefresh) {
            musicVideoRetryCountForTrack = 0
            musicVideoNeedsRefresh = false
            if (!autoVideo) {
                _playerMediaMode.value = PlayerMediaMode.SONG
            }
            _currentMusicVideo.value = null
        }
        musicVideoResolveJob?.cancel()
        _isResolvingMusicVideo.value = true
        musicVideoResolveJob = viewModelScope.launch {
            try {
                val resolved = youTubeMusicService.resolveMusicVideoStream(
                    track = track,
                    forceRefresh = forceRefresh
                )
                if (playbackState.value.currentTrack?.id == track.id) {
                    _currentMusicVideo.value = resolved
                    if (resolved != null) {
                        musicVideoNeedsRefresh = false
                        if (autoVideo) {
                            _playerMediaMode.value = PlayerMediaMode.VIDEO
                        }
                    } else if (forceRefresh) {
                        _playerMediaMode.value = PlayerMediaMode.SONG
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                if (forceRefresh && playbackState.value.currentTrack?.id == track.id) {
                    _playerMediaMode.value = PlayerMediaMode.SONG
                }
            } finally {
                if (playbackState.value.currentTrack?.id == track.id) {
                    _isResolvingMusicVideo.value = false
                }
            }
        }
    }

    fun openTrackOptions(track: TrackItem) {
        _trackForOptionsMenu.value = track
    }

    fun closeTrackOptions() {
        _trackForOptionsMenu.value = null
    }

    // Visualizer management
    fun selectVisualizer(visualizer: VisualizerDefinition) {
        visualizerRepository.selectVisualizer(visualizer)
    }

    fun importVisualizer(jsonContent: String, fileName: String? = null): Result<VisualizerDefinition> {
        return visualizerRepository.importVisualizer(jsonContent, fileName)
    }

    fun deleteVisualizer(id: String): Boolean {
        return visualizerRepository.deleteCustomVisualizer(id)
    }

    // Lyrics management
    fun fetchLyricsForTrack(track: TrackItem) {
        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch {
            try {
                val result = lyricsProviderService.resolveLyrics(track)
                if (result != null) {
                    _currentLyrics.value = result.lines
                    _currentLyricsProviderName.value = result.providerName
                } else {
                    _currentLyrics.value = emptyList()
                    _currentLyricsProviderName.value = null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _currentLyrics.value = emptyList()
                _currentLyricsProviderName.value = null
            }
        }
    }

    fun importLocalLrc(lrcContent: String) {
        val currentTrack = playbackState.value.currentTrack ?: return
        viewModelScope.launch {
            val result = lyricsProviderService.importLocalLrc(currentTrack, lrcContent)
            if (result != null) {
                _currentLyrics.value = result.lines
                _currentLyricsProviderName.value = result.providerName
            }
        }
    }

    fun moveLyricsProviderPriority(id: LyricsProviderId, delta: Int) {
        lyricsProviderService.moveProviderPriority(id, delta)
        _lyricsProviders.value = lyricsProviderService.getProviders()
        playbackState.value.currentTrack?.let { fetchLyricsForTrack(it) }
    }

    fun setLyricsProviderEnabled(id: LyricsProviderId, enabled: Boolean) {
        lyricsProviderService.setProviderEnabled(id, enabled)
        _lyricsProviders.value = lyricsProviderService.getProviders()
        playbackState.value.currentTrack?.let { fetchLyricsForTrack(it) }
    }

    // Motion Canvas management
    fun fetchCanvasForTrack(track: TrackItem) {
        trackCanvasCoordinator.fetchForTrack(track)
    }

    // Download & Offline Management
    fun openDownloadModal(track: TrackItem) {
        _isDownloadModalOpen.value = true
        _downloadCandidates.value = emptyList()
        _isSearchingCandidates.value = true
        _downloadProgressState.value = null

        searchCandidatesJob?.cancel()
        searchCandidatesJob = viewModelScope.launch {
            try {
                val candidates = musicDownloadService.searchCandidates(track)
                _downloadCandidates.value = candidates
            } catch (e: Exception) {
                _downloadCandidates.value = emptyList()
            } finally {
                _isSearchingCandidates.value = false
            }
        }
    }

    fun closeDownloadModal() {
        _isDownloadModalOpen.value = false
    }

    fun startDownload(track: TrackItem, candidate: TrackDownloadCandidate?, format: DownloadAudioFormat) {
        downloadJob?.cancel()
        _downloadProgressState.value = DownloadProgressState(
            trackId = track.id,
            percent = 0.05f,
            stage = "Starting download...",
            isDownloading = true
        )

        downloadJob = viewModelScope.launch {
            val result = musicDownloadService.downloadTrack(
                track = track,
                candidate = candidate,
                formatOverride = format
            ) { progress, stage ->
                _downloadProgressState.value = DownloadProgressState(
                    trackId = track.id,
                    percent = progress,
                    stage = stage,
                    isDownloading = progress < 1.0f,
                    isSuccess = progress >= 1.0f
                )
            }

            if (result.success) {
                _downloadProgressState.value = DownloadProgressState(
                    trackId = track.id,
                    percent = 1.0f,
                    stage = "Downloaded successfully!",
                    isDownloading = false,
                    isSuccess = true
                )
            } else {
                _downloadProgressState.value = DownloadProgressState(
                    trackId = track.id,
                    percent = 0f,
                    stage = "Error: ${result.errorMessage ?: "Download failed"}",
                    isDownloading = false,
                    isSuccess = false,
                    errorMessage = result.errorMessage
                )
            }
        }
    }

    fun updateDownloadOptions(options: DownloadMetadataOptions) {
        musicDownloadService.updateOptions(options)
    }

    fun changeDownloadFolder(dirPath: String) {
        val current = downloadOptions.value
        musicDownloadService.updateOptions(current.copy(downloadDirectory = dirPath))
    }

    // YouTube Music Account & Login Actions
    fun openYtmLogin() {
        _ytmLoginError.value = null
        _isYtmLoginSheetOpen.value = true
    }

    fun closeYtmLogin() {
        ytmSyncJob?.cancel()
        ytmSyncJob = null
        _isSyncingYtmAccount.value = false
        _ytmLoginError.value = null
        _isYtmLoginSheetOpen.value = false
    }

    fun onYtmCredentialsCaptured(cookie: String, visitorData: String, dataSyncId: String?) {
        ytmSyncJob?.cancel()
        ytmSyncJob = viewModelScope.launch {
            _isSyncingYtmAccount.value = true
            _ytmLoginError.value = null
            try {
                catalogService.authManager.saveSession(
                    cookie = cookie,
                    visitorData = visitorData,
                    dataSyncId = dataSyncId
                )
                val syncedState = catalogService.syncYouTubeAccountAndFeed(
                    favoritesRepository.likedTracks.value
                )
                if (isYtmAccountValidated(syncedState)) {
                    _isYtmLoginSheetOpen.value = false
                    _ytmLoginError.value = null
                    openYtmPlaylistImport()
                } else {
                    catalogService.signOutYouTubeAccount()
                    _ytmLoginError.value =
                        "Could not verify your YouTube Music account. Please sign in again."
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                runCatching { catalogService.signOutYouTubeAccount() }
                _ytmLoginError.value = e.message?.takeIf { it.isNotBlank() }
                    ?: "Could not verify your YouTube Music session. Please try again."
            } finally {
                _isSyncingYtmAccount.value = false
            }
        }
    }

    fun signOutYtm() {
        closeYtmPlaylistImport()
        _ytmLibraryPlaylists.value = emptyList()
        _selectedYtmPlaylistIds.value = emptySet()
        ytmSyncJob?.cancel()
        ytmSyncJob = viewModelScope.launch {
            _isSyncingYtmAccount.value = true
            try {
                catalogService.signOutYouTubeAccount()
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isSyncingYtmAccount.value = false
            }
        }
    }

    fun forceSyncYtm() {
        if (!catalogService.authManager.isSignedIn()) return
        ytmSyncJob?.cancel()
        ytmSyncJob = viewModelScope.launch {
            _isSyncingYtmAccount.value = true
            try {
                catalogService.syncYouTubeAccountAndFeed(favoritesRepository.likedTracks.value)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isSyncingYtmAccount.value = false
            }
        }
    }

    fun remindYtmLoginLater() {
        catalogService.authManager.dismissPromptTemporarily()
    }

    fun dismissYtmLoginPrompt() {
        catalogService.authManager.dismissPromptPermanently()
    }

    fun refreshHomeFeed() {
        refreshFeedJob?.cancel()
        refreshFeedJob = viewModelScope.launch {
            try {
                catalogService.refreshHomeFeed()
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Home Grid Navigation
    fun openHomeGrid(category: HomeGridCategory) {
        _homeGridCategory.value = category
        _overlayStack.update { pushOverlayLayer(it, OverlayLayer.HOME_GRID) }
    }

    fun closeHomeGrid() {
        _homeGridCategory.value = null
        _overlayStack.update { removeOverlayLayer(it, OverlayLayer.HOME_GRID) }
    }

    // Home Discovery Feed Click Handlers
    fun onTopPickClick(item: TopPickItem) {
        feedResolveJob?.cancel()
        when (val action = resolveTopPickClickAction(item)) {
            is HomeFeedClickAction.OpenAlbum -> openAlbumDetail(action.album)
            is HomeFeedClickAction.OpenArtist -> openArtistByName(action.artistName, action.avatarUri)
            is HomeFeedClickAction.PlayTrack -> onSearchTrackClicked(action.track)
            is HomeFeedClickAction.ResolveRadio -> resolveAndPlayRadio(action.station, fallbackTrack = item.seedTrack)
            is HomeFeedClickAction.ResolvePlaylist -> resolveAndPlayPlaylist(action.playlist, fallbackTrack = item.seedTrack)
            HomeFeedClickAction.None -> Unit
        }
    }

    fun onRadioStationClick(station: RadioStationItem) {
        feedResolveJob?.cancel()
        resolveAndPlayRadio(station)
    }

    fun onRecommendedPlaylistClick(item: RecommendedPlaylistItem) {
        feedResolveJob?.cancel()
        resolveAndPlayPlaylist(item)
    }

    fun onNewReleaseClick(release: NewReleaseItem) {
        feedResolveJob?.cancel()
        when (val action = resolveNewReleaseClickAction(release)) {
            is HomeFeedClickAction.OpenAlbum -> openAlbumDetail(action.album)
            is HomeFeedClickAction.PlayTrack -> onSearchTrackClicked(action.track)
            else -> Unit
        }
    }

    private fun resolveAndPlayRadio(station: RadioStationItem, fallbackTrack: TrackItem? = null) {
        feedResolveJob = viewModelScope.launch {
            try {
                val tracks = catalogService.resolveRadioStationTracks(station)
                if (tracks.isNotEmpty()) {
                    playQueue(tracks, 0)
                } else if (fallbackTrack != null) {
                    onSearchTrackClicked(fallbackTrack)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                e.printStackTrace()
                if (fallbackTrack != null) {
                    onSearchTrackClicked(fallbackTrack)
                }
            }
        }
    }

    private fun resolveAndPlayPlaylist(item: RecommendedPlaylistItem, fallbackTrack: TrackItem? = null) {
        feedResolveJob = viewModelScope.launch {
            try {
                val tracks = catalogService.resolvePlaylistTracks(item)
                if (tracks.isNotEmpty()) {
                    playQueue(tracks, 0)
                } else if (fallbackTrack != null) {
                    onSearchTrackClicked(fallbackTrack)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                e.printStackTrace()
                if (fallbackTrack != null) {
                    onSearchTrackClicked(fallbackTrack)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        auroraExtractionJob?.cancel()
        ytmSyncJob?.cancel()
        refreshFeedJob?.cancel()
        feedResolveJob?.cancel()
        moodsFetchJob?.cancel()
        moodDetailJob?.cancel()
        albumDetailJob?.cancel()
        albumAmbientJob?.cancel()
        artistDetailJob?.cancel()
        artistAmbientJob?.cancel()
        artistVideoJob?.cancel()
        widgetDeepLinkJob?.cancel()
        WidgetSyncBridge.onFavoriteToggleRequested = null
        WidgetSyncBridge.onRefreshRecommendationsRequested = null
        audioOutputHelper.release()
    }
}

/**
 * Coordinates Canvas artwork resolution for consecutive Now Playing tracks, preserving the
 * active [CanvasArtworkResult] across tracks from the same valid album without clearing to null
 * or issuing duplicate network fetches.
 */
internal class TrackCanvasReuseCoordinator(
    private val scope: CoroutineScope,
    private val canvasService: AppleMusicCanvasService,
    private val targetFlow: MutableStateFlow<CanvasArtworkResult?>,
    private val warmCache: (CanvasArtworkResult) -> Unit = {}
) {
    private var canvasJob: Job? = null
    private var lastCanvasAlbumKey: String? = null
    private var lastCanvasWasAlbumScoped: Boolean = false
    private var inFlightAlbumKey: String? = null
    private var fetchGeneration: Long = 0L

    val activeAlbumKey: String?
        get() = lastCanvasAlbumKey

    fun fetchForTrack(track: TrackItem) {
        val albumKey = canvasService.buildAlbumCacheKey(track.album, track.artist)
        if (albumKey == null) {
            fetchGeneration++
            canvasJob?.cancel()
            canvasJob = null
            inFlightAlbumKey = null
            lastCanvasAlbumKey = null
            lastCanvasWasAlbumScoped = false
            targetFlow.value = null
            return
        }

        val currentCanvas = targetFlow.value
        if (albumKey == lastCanvasAlbumKey &&
            lastCanvasWasAlbumScoped &&
            currentCanvas != null
        ) {
            return
        }

        if (albumKey == inFlightAlbumKey && canvasJob?.isActive == true) {
            return
        }

        canvasJob?.cancel()
        val generation = ++fetchGeneration
        inFlightAlbumKey = null

        val albumPeek = canvasService.peekAlbumCanvasArtwork(track.album, track.artist)
        if (albumPeek != null) {
            lastCanvasAlbumKey = albumKey
            lastCanvasWasAlbumScoped = true
            targetFlow.value = albumPeek
            warmCache(albumPeek)
            return
        }

        lastCanvasAlbumKey = null
        lastCanvasWasAlbumScoped = false
        inFlightAlbumKey = albumKey
        targetFlow.value = null

        canvasJob = scope.launch {
            try {
                val resolved = canvasService.getAlbumCanvasArtwork(
                    album = track.album,
                    artist = track.artist,
                    priority = true
                )
                if (generation != fetchGeneration) return@launch
                lastCanvasAlbumKey = albumKey
                lastCanvasWasAlbumScoped = resolved != null
                targetFlow.value = resolved
                if (resolved != null) {
                    warmCache(resolved)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                if (generation == fetchGeneration) {
                    lastCanvasAlbumKey = null
                    lastCanvasWasAlbumScoped = false
                    targetFlow.value = null
                }
            } finally {
                if (generation == fetchGeneration) {
                    inFlightAlbumKey = null
                }
            }
        }
    }

    fun clear() {
        fetchGeneration++
        canvasJob?.cancel()
        canvasJob = null
        inFlightAlbumKey = null
        lastCanvasAlbumKey = null
        lastCanvasWasAlbumScoped = false
        targetFlow.value = null
    }
}

internal val DEFAULT_DETAIL_AMBIENT_COLORS = ArtistAmbientColors(
    dark = Color(0xFF0F1012),
    light = Color(0xFFF4F6F9)
)

internal fun isLocalLibraryTrack(track: TrackItem): Boolean {
    val id = track.id.trim()
    if (id.startsWith("local_") || id.startsWith("saf_")) return true
    val url = track.audioUrl?.trim().orEmpty()
    return url.startsWith("content://") || url.startsWith("file://") || url.startsWith("/")
}

internal fun selectRecentlyAddedSourceCandidates(
    sourceType: MediaSourceType,
    localTracks: List<TrackItem>,
    onlineTracks: List<TrackItem>,
    likedTracks: List<TrackItem>
): List<TrackItem> {
    val preferredById = LinkedHashMap<String, TrackItem>()
    fun addPreferred(tracks: List<TrackItem>) {
        for (track in tracks) {
            val key = track.id.trim()
            if (key.isBlank()) continue
            val existing = preferredById[key]
            if (existing == null || (existing.dateAddedMs <= 0L && track.dateAddedMs > 0L)) {
                preferredById[key] = track
            }
        }
    }

    when (sourceType) {
        MediaSourceType.LOCAL -> {
            addPreferred(localTracks)
            addPreferred(likedTracks.filter(::isLocalLibraryTrack))
        }
        MediaSourceType.ONLINE -> {
            addPreferred(onlineTracks)
            addPreferred(likedTracks.filterNot(::isLocalLibraryTrack))
        }
        MediaSourceType.ALL -> {
            addPreferred(localTracks)
            addPreferred(onlineTracks)
            addPreferred(likedTracks)
        }
    }
    return preferredById.values.toList()
}

internal fun pushOverlayLayer(stack: List<OverlayLayer>, layer: OverlayLayer): List<OverlayLayer> =
    (stack.filterNot { it == layer }) + layer

internal fun removeOverlayLayer(stack: List<OverlayLayer>, layer: OverlayLayer): List<OverlayLayer> =
    stack.filterNot { it == layer }

internal fun popTopOverlayLayer(stack: List<OverlayLayer>): Pair<List<OverlayLayer>, OverlayLayer?> =
    if (stack.isEmpty()) stack to null else stack.dropLast(1) to stack.last()

internal fun resolveLibraryBackTransition(
    currentSubView: LibrarySubView?,
    currentCategory: SearchCategoryFilter
): Pair<LibrarySubView?, SearchCategoryFilter>? = when {
    currentSubView == LibrarySubView.RECENTLY_ADDED -> null to currentCategory
    currentSubView != null || currentCategory != SearchCategoryFilter.ALL -> null to SearchCategoryFilter.ALL
    else -> null
}

internal fun shouldHideSettingsButton(
    selectedTab: NavigationTab,
    activeLibrarySubView: LibrarySubView?
): Boolean = selectedTab == NavigationTab.Library && activeLibrarySubView == LibrarySubView.RECENTLY_ADDED

internal fun isAppBackdropOverlayFreezeActive(
    isAlbumDetailOpen: Boolean,
    isArtistDetailOpen: Boolean,
    isPlaylistDetailOpen: Boolean,
    isHomeGridOpen: Boolean,
    effectiveAlbumProgress: Float,
    effectiveArtistProgress: Float,
    effectivePlaylistProgress: Float,
    effectiveGridProgress: Float
): Boolean = isAlbumDetailOpen ||
    isArtistDetailOpen ||
    isPlaylistDetailOpen ||
    isHomeGridOpen ||
    effectiveAlbumProgress > 0.001f ||
    effectiveArtistProgress > 0.001f ||
    effectivePlaylistProgress > 0.001f ||
    effectiveGridProgress > 0.001f

internal fun sanitizeAlbumOpenRequest(album: AlbumItem): AlbumItem? {
    val cleanId = album.id.trim()
    val cleanTitle = album.title.trim()
    if (cleanId.isBlank() && cleanTitle.isBlank()) return null

    val cleanArtist = album.artist.trim().let { raw ->
        if (raw.contains("<unknown>", ignoreCase = true)) "Unknown Artist" else raw
    }
    val effectiveTitle = cleanTitle.ifBlank { "Album" }
    val effectiveId = cleanId.ifBlank {
        "alb_${effectiveTitle.lowercase(Locale.ROOT).hashCode()}_${cleanArtist.lowercase(Locale.ROOT).hashCode()}"
    }
    return album.copy(
        id = effectiveId,
        title = effectiveTitle,
        artist = cleanArtist
    )
}

internal fun buildAlbumRequestKey(album: AlbumItem): String =
    "${album.id.trim()}|${album.title.trim().lowercase(Locale.ROOT)}|${album.artist.trim().lowercase(Locale.ROOT)}"

internal fun normalizeArtistLookupName(rawName: String?): String? {
    val trimmed = rawName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val primary = trimmed.split(",", "&", ";", "/").firstOrNull()?.trim().orEmpty().ifBlank { trimmed }
    if (primary.isBlank() ||
        primary.equals("Unknown Artist", ignoreCase = true) ||
        primary.equals("<unknown>", ignoreCase = true)
    ) {
        return null
    }
    return primary
}

internal sealed interface HomeFeedClickAction {
    data class OpenAlbum(val album: AlbumItem) : HomeFeedClickAction
    data class OpenArtist(val artistName: String, val avatarUri: String?) : HomeFeedClickAction
    data class PlayTrack(val track: TrackItem) : HomeFeedClickAction
    data class ResolveRadio(val station: RadioStationItem) : HomeFeedClickAction
    data class ResolvePlaylist(val playlist: RecommendedPlaylistItem) : HomeFeedClickAction
    data object None : HomeFeedClickAction
}

internal fun normalizeAuroraArtworkUri(track: TrackItem?): String? =
    track?.artworkUri?.trim()?.takeIf { it.isNotEmpty() }

internal fun isYtmAccountValidated(state: YtmAccountState): Boolean =
    state.isSignedIn && (!state.accountName.isNullOrBlank() || !state.accountHandle.isNullOrBlank())

internal fun shouldDisplayYtmLoginBanner(
    accountState: YtmAccountState,
    shouldShowPrompt: Boolean
): Boolean = !accountState.isSignedIn && shouldShowPrompt

internal fun resolveTopPickClickAction(item: TopPickItem): HomeFeedClickAction {
    return when (item.kind) {
        TopPickKind.ARTIST_SPOTLIGHT -> {
            val artist = item.artistName?.trim()?.takeIf { it.isNotEmpty() }
                ?: item.title.trim().takeIf { it.isNotEmpty() }
            if (artist != null) {
                HomeFeedClickAction.OpenArtist(artist, item.artworkUri)
            } else {
                HomeFeedClickAction.None
            }
        }

        TopPickKind.NEW_RELEASE -> {
            val browseId = item.browseId?.trim()?.takeIf { it.isNotEmpty() }
            val albumTitle = item.albumTitle?.trim()?.takeIf { it.isNotEmpty() }
            when {
                browseId != null || albumTitle != null -> {
                    HomeFeedClickAction.OpenAlbum(buildAlbumFromTopPick(item))
                }
                item.seedTrack != null -> HomeFeedClickAction.PlayTrack(item.seedTrack)
                !item.videoId.isNullOrBlank() -> HomeFeedClickAction.PlayTrack(buildTrackFromTopPick(item))
                !item.playlistId.isNullOrBlank() -> {
                    HomeFeedClickAction.ResolvePlaylist(
                        RecommendedPlaylistItem(
                            id = item.id,
                            title = item.title,
                            subtitle = item.subtitle,
                            artworkUri = item.artworkUri,
                            playlistId = item.playlistId.trim(),
                            browseId = item.browseId
                        )
                    )
                }
                else -> HomeFeedClickAction.None
            }
        }

        TopPickKind.PERSONAL_STATION -> {
            val playlistId = item.playlistId?.trim()?.takeIf { it.isNotEmpty() }
            val browseId = item.browseId?.trim()?.takeIf { it.isNotEmpty() }
            when {
                playlistId != null -> {
                    HomeFeedClickAction.ResolveRadio(
                        RadioStationItem(
                            id = item.id,
                            title = item.title,
                            subtitle = item.subtitle,
                            artworkUri = item.artworkUri,
                            playlistId = playlistId,
                            seedVideoId = item.videoId?.trim()?.takeIf { it.isNotEmpty() }
                        )
                    )
                }
                item.seedTrack != null -> HomeFeedClickAction.PlayTrack(item.seedTrack)
                !item.videoId.isNullOrBlank() -> HomeFeedClickAction.PlayTrack(buildTrackFromTopPick(item))
                browseId != null -> {
                    if (browseId.startsWith("UC")) {
                        val artist = item.artistName?.trim()?.takeIf { it.isNotEmpty() } ?: item.title
                        HomeFeedClickAction.OpenArtist(artist, item.artworkUri)
                    } else {
                        HomeFeedClickAction.OpenAlbum(buildAlbumFromTopPick(item))
                    }
                }
                else -> HomeFeedClickAction.None
            }
        }

        TopPickKind.FEATURED_PLAYLIST -> {
            val playlistId = item.playlistId?.trim()?.takeIf { it.isNotEmpty() }
            val browseId = item.browseId?.trim()?.takeIf { it.isNotEmpty() }
            when {
                playlistId != null || browseId != null -> {
                    HomeFeedClickAction.ResolvePlaylist(
                        RecommendedPlaylistItem(
                            id = item.id,
                            title = item.title,
                            subtitle = item.subtitle,
                            artworkUri = item.artworkUri,
                            playlistId = playlistId ?: browseId.orEmpty(),
                            browseId = browseId
                        )
                    )
                }
                item.seedTrack != null -> HomeFeedClickAction.PlayTrack(item.seedTrack)
                !item.videoId.isNullOrBlank() -> HomeFeedClickAction.PlayTrack(buildTrackFromTopPick(item))
                else -> HomeFeedClickAction.None
            }
        }
    }
}

internal fun resolveNewReleaseClickAction(release: NewReleaseItem): HomeFeedClickAction {
    if (release.isAlbum) {
        return HomeFeedClickAction.OpenAlbum(buildAlbumFromNewRelease(release))
    }
    if (!release.videoId.isNullOrBlank()) {
        return HomeFeedClickAction.PlayTrack(buildTrackFromNewRelease(release))
    }
    if (!release.browseId.isNullOrBlank()) {
        return HomeFeedClickAction.OpenAlbum(buildAlbumFromNewRelease(release))
    }
    return HomeFeedClickAction.None
}

internal fun buildAlbumFromNewRelease(release: NewReleaseItem): AlbumItem {
    val parsedYear = release.year?.trim()?.toIntOrNull()
        ?: Regex("""\b(19\d{2}|20\d{2})\b""").find(release.subtitle)?.value?.toIntOrNull()
        ?: 2024
    return AlbumItem(
        id = release.browseId?.trim()?.takeIf { it.isNotEmpty() } ?: release.id,
        title = release.title,
        artist = release.artist.trim().ifEmpty { "YouTube Music" },
        coverUri = release.artworkUri,
        year = parsedYear
    )
}

internal fun buildTrackFromNewRelease(release: NewReleaseItem): TrackItem {
    val rawVid = release.videoId?.trim().orEmpty()
    val trackId = when {
        rawVid.startsWith("ytm_") -> rawVid
        rawVid.isNotEmpty() -> "ytm_$rawVid"
        else -> release.id
    }
    return TrackItem(
        id = trackId,
        title = release.title,
        artist = release.artist.trim().ifEmpty { "YouTube Music" },
        album = release.title,
        artworkUri = release.artworkUri
    )
}

internal fun buildAlbumFromTopPick(item: TopPickItem): AlbumItem {
    val artist = item.artistName?.trim()?.takeIf { it.isNotEmpty() }
        ?: item.subtitle.substringBefore("•").trim().ifEmpty { "YouTube Music" }
    val year = Regex("""\b(19\d{2}|20\d{2})\b""").find(item.subtitle)?.value?.toIntOrNull() ?: 2024
    return AlbumItem(
        id = item.browseId?.trim()?.takeIf { it.isNotEmpty() } ?: item.id,
        title = item.albumTitle?.trim()?.takeIf { it.isNotEmpty() } ?: item.title,
        artist = artist,
        coverUri = item.artworkUri,
        year = year
    )
}

internal fun buildTrackFromTopPick(item: TopPickItem): TrackItem {
    val rawVid = item.videoId?.trim().orEmpty()
    val trackId = when {
        rawVid.startsWith("ytm_") -> rawVid
        rawVid.isNotEmpty() -> "ytm_$rawVid"
        else -> item.id
    }
    val artist = item.artistName?.trim()?.takeIf { it.isNotEmpty() }
        ?: item.subtitle.substringBefore("•").trim().ifEmpty { "YouTube Music" }
    return TrackItem(
        id = trackId,
        title = item.title,
        artist = artist,
        album = item.albumTitle?.trim()?.takeIf { it.isNotEmpty() } ?: item.title,
        artworkUri = item.artworkUri
    )
}
