package com.xperience.player.service

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.xperience.player.BuildConfig
import com.xperience.player.domain.model.AlbumDetailProfile
import com.xperience.player.domain.model.AlbumItem
import com.xperience.player.domain.model.ArtistDetailProfile
import com.xperience.player.domain.model.ArtistItem
import com.xperience.player.domain.model.HomeDiscoveryFeed
import com.xperience.player.domain.model.MoreLikeAlbumSectionData
import com.xperience.player.domain.model.NewReleaseItem
import com.xperience.player.domain.model.RadioStationItem
import com.xperience.player.domain.model.RecommendedPlaylistItem
import com.xperience.player.domain.model.TopPickItem
import com.xperience.player.domain.model.TopPickKind
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.domain.model.UserPlaylist
import com.xperience.player.domain.model.YtmAccountState
import com.xperience.player.domain.model.canvas.CanvasArtworkResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class MediaCatalogService(
    private val context: Context,
    private val ytmService: YouTubeMusicService = YouTubeMusicService.getInstance(),
    private val appleMusicCanvasService: AppleMusicCanvasService = AppleMusicCanvasService.getInstance(context),
    val authManager: YouTubeAuthManager = YouTubeAuthManager.getInstance(context)
) {

    private val scanner = LocalAudioScanner(context)
    private val searchPrefs = context.getSharedPreferences("xperience_search_prefs", Context.MODE_PRIVATE)
    private val recentlyPlayedPrefs = context.getSharedPreferences("xperience_recently_played_prefs", Context.MODE_PRIVATE)
    private val playbackHistoryPrefs = context.getSharedPreferences("xperience_playback_history_prefs", Context.MODE_PRIVATE)
    private val trackAddedAtStore = TrackAddedAtStore(context)
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val catalogStateLock = Any()
    private var recFetchJob: Job? = null

    private data class PlaybackHistoryEntry(
        val track: TrackItem,
        val lastPlayedAtMs: Long,
        val playTimestampsMs: List<Long>,
        val totalPlayCount: Int,
        val genreTag: String
    )

    private val _playbackHistoryEntries = LinkedHashMap<String, PlaybackHistoryEntry>()
    private val _smartSuggestedPlaylists = MutableStateFlow<List<UserPlaylist>>(emptyList())
    val smartSuggestedPlaylists: StateFlow<List<UserPlaylist>> = _smartSuggestedPlaylists.asStateFlow()

    private fun isSameAlbumOrItem(a: TrackItem, b: TrackItem): Boolean {
        if (a.id == b.id) return true

        val albumA = a.album.trim()
        val albumB = b.album.trim()
        val isGenericA = albumA.isBlank() ||
            albumA.equals("Unknown Album", ignoreCase = true) ||
            albumA.equals("Single", ignoreCase = true) ||
            albumA.equals("YouTube Music", ignoreCase = true)
        val isGenericB = albumB.isBlank() ||
            albumB.equals("Unknown Album", ignoreCase = true) ||
            albumB.equals("Single", ignoreCase = true) ||
            albumB.equals("YouTube Music", ignoreCase = true)

        if (!isGenericA && !isGenericB) {
            val sameAlbum = albumA.equals(albumB, ignoreCase = true)
            val artistA = a.artist.trim()
            val artistB = b.artist.trim()
            val isGenericArtistA = artistA.isBlank() || artistA.equals("Unknown Artist", ignoreCase = true)
            val isGenericArtistB = artistB.isBlank() || artistB.equals("Unknown Artist", ignoreCase = true)
            val sameArtist = isGenericArtistA || isGenericArtistB || artistA.equals(artistB, ignoreCase = true)
            if (sameAlbum && sameArtist) {
                return true
            }
        }

        // Fallback for singles / tracks without distinct album names
        return a.title.trim().equals(b.title.trim(), ignoreCase = true) &&
               a.artist.trim().equals(b.artist.trim(), ignoreCase = true)
    }

    private fun deduplicateByAlbum(tracks: List<TrackItem>): List<TrackItem> {
        val result = mutableListOf<TrackItem>()
        for (track in tracks) {
            val alreadyPresent = result.any { isSameAlbumOrItem(it, track) }
            if (!alreadyPresent) {
                result.add(track)
            }
        }
        return result
    }

    private fun saveRecentlyPlayed() {
        try {
            val snapshot = synchronized(catalogStateLock) { _recentlyPlayed.take(50) }
            val array = JSONArray()
            for (track in snapshot) {
                val obj = JSONObject().apply {
                    put("id", track.id)
                    put("title", track.title)
                    put("artist", track.artist)
                    put("album", track.album)
                    put("durationMs", track.durationMs)
                    if (track.artworkResId != null) put("artworkResId", track.artworkResId)
                    if (!track.artworkUri.isNullOrBlank()) put("artworkUri", track.artworkUri)
                    if (!track.audioUrl.isNullOrBlank()) put("audioUrl", track.audioUrl)
                    put("isLiked", track.isLiked)
                    put("isExplicit", track.isExplicit)
                    put("playCount", track.playCount)
                    if (track.dateAddedMs > 0L) put("dateAddedMs", track.dateAddedMs)
                }
                array.put(obj)
            }
            recentlyPlayedPrefs.edit().putString("recently_played_json", array.toString()).apply()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) runCatching { Log.w("MediaCatalogService", "Catalog operation failed", e) }
        }
    }

    private fun loadRecentlyPlayed(): Pair<List<TrackItem>, Boolean> {
        val raw = recentlyPlayedPrefs.getString("recently_played_json", null) ?: return emptyList<TrackItem>() to false
        val list = mutableListOf<TrackItem>()
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    TrackItem(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        artist = obj.getString("artist"),
                        album = obj.optString("album", ""),
                        durationMs = obj.optLong("durationMs", 0L),
                        artworkResId = if (obj.has("artworkResId")) obj.getInt("artworkResId") else null,
                        artworkUri = if (obj.has("artworkUri")) obj.getString("artworkUri") else null,
                        audioUrl = if (obj.has("audioUrl")) obj.getString("audioUrl") else null,
                        isLiked = obj.optBoolean("isLiked", false),
                        isExplicit = obj.optBoolean("isExplicit", false),
                        playCount = obj.optLong("playCount", 0L),
                        dateAddedMs = obj.optLong("dateAddedMs", 0L)
                    )
                )
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) runCatching { Log.w("MediaCatalogService", "Catalog operation failed", e) }
        }
        val (sanitized, modified) = YouTubeMusicService.sanitizePersistedTracks(
            list,
            ytmService.getAtvCounterpartSnapshot()
        )
        return deduplicateByAlbum(sanitized).take(50) to modified
    }

    fun getRecentSearches(): List<String> {
        val raw = searchPrefs.getString("recent_searches_list", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("||").filter { it.isNotBlank() }
    }

    fun addRecentSearch(query: String): List<String> {
        val clean = query.trim()
        if (clean.isBlank()) return getRecentSearches()
        val current = getRecentSearches().toMutableList()
        current.remove(clean)
        current.add(0, clean)
        val trimmed = current.take(15)
        searchPrefs.edit().putString("recent_searches_list", trimmed.joinToString("||")).apply()
        return trimmed
    }

    fun removeRecentSearch(query: String): List<String> {
        val current = getRecentSearches().toMutableList()
        current.remove(query.trim())
        searchPrefs.edit().putString("recent_searches_list", current.joinToString("||")).apply()
        return current
    }

    fun clearRecentSearches(): List<String> {
        searchPrefs.edit().remove("recent_searches_list").apply()
        return emptyList()
    }

    suspend fun searchYouTubeMusic(query: String, filter: SearchFilter = SearchFilter.ALL): SearchResultData {
        return ytmService.searchYouTubeMusic(query, filter)
    }

    private val _allTracks = mutableListOf<TrackItem>()
    private val _recentlyPlayed = mutableListOf<TrackItem>()
    private val _recommendedTracks = mutableListOf<TrackItem>()
    private val _recommendedAlbums = mutableListOf<AlbumItem>()
    private val _genreTracks = mutableListOf<TrackItem>()
    private var _selectedGenre: String = "Pop"

    private val _onlineArtists = mutableListOf<ArtistItem>()
    private val _seedArtists = mutableListOf<String>()

    @Volatile private var _topPicks: List<TopPickItem> = emptyList()
    @Volatile private var _moreLikeSections: List<MoreLikeAlbumSectionData> = emptyList()
    @Volatile private var _playlistsMadeForYou: List<RecommendedPlaylistItem> = emptyList()
    @Volatile private var _allRadioStations: List<RadioStationItem> = emptyList()
    @Volatile private var _allNewReleases: List<NewReleaseItem> = emptyList()
    @Volatile private var _isPersonalizedFromAccount: Boolean = false
    @Volatile private var _likedTracks: List<TrackItem> = emptyList()

    private val _localTracks = MutableStateFlow<List<TrackItem>>(emptyList())
    val localTracks: StateFlow<List<TrackItem>> = _localTracks.asStateFlow()

    private val _localArtists = MutableStateFlow<List<ArtistItem>>(emptyList())
    val localArtists: StateFlow<List<ArtistItem>> = _localArtists.asStateFlow()

    private val _localAlbums = MutableStateFlow<List<AlbumItem>>(emptyList())
    val localAlbums: StateFlow<List<AlbumItem>> = _localAlbums.asStateFlow()

    private val _onlineTracks = MutableStateFlow<List<TrackItem>>(emptyList())
    val onlineTracks: StateFlow<List<TrackItem>> = _onlineTracks.asStateFlow()

    private val _onlineArtistsState = MutableStateFlow<List<ArtistItem>>(emptyList())
    val onlineArtists: StateFlow<List<ArtistItem>> = _onlineArtistsState.asStateFlow()

    private val _onlineAlbumsState = MutableStateFlow<List<AlbumItem>>(emptyList())
    val onlineAlbums: StateFlow<List<AlbumItem>> = _onlineAlbumsState.asStateFlow()

    private val defaultGenres = listOf("Pop", "Synthwave", "Alternative", "Electronic", "Indie Pop", "R&B", "Rock")

    @Volatile private var _isFeedLoading: Boolean = true
    @Volatile private var _hasInitialFeedSettled: Boolean = false
    private val activeFeedLoadingCount = AtomicInteger(0)

    private val _homeFeed = MutableStateFlow(
        HomeDiscoveryFeed(
            personalizedSubtitle = "Powered by YouTube Music & Local Library",
            recentlyPlayedTracks = emptyList(),
            relatedArtists = emptyList(),
            recommendedTracks = emptyList(),
            recommendedAlbums = emptyList(),
            genreTags = defaultGenres,
            selectedGenreTag = "Pop",
            genreTracks = emptyList(),
            isFeedLoading = true
        )
    )
    val homeFeed: StateFlow<HomeDiscoveryFeed> = _homeFeed.asStateFlow()

    init {
        ytmService.attachAuthManager(authManager)
        ytmService.attachCacheContext(context)
        serviceScope.launch {
            val (saved, modified) = loadRecentlyPlayed()
            if (saved.isNotEmpty()) {
                synchronized(catalogStateLock) {
                    _recentlyPlayed.addAll(saved)
                }
                if (modified) {
                    saveRecentlyPlayed()
                }
            }
            loadPlaybackHistory(seedTracks = saved)
            updateFeedState()
        }
    }

    fun release() {
        enrichJob?.cancel()
        recFetchJob?.cancel()
        serviceScope.cancel()
    }

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    fun getLibraryFolders(): List<String> = scanner.getSavedLibraryFolders().toList()

    private fun beginFeedLoadingState() {
        activeFeedLoadingCount.incrementAndGet()
        _isFeedLoading = true
        _homeFeed.update { it.copy(isFeedLoading = true) }
    }

    private fun endFeedLoadingState() {
        _hasInitialFeedSettled = true
        val remaining = activeFeedLoadingCount.updateAndGet { (it - 1).coerceAtLeast(0) }
        _isFeedLoading = remaining > 0
        updateFeedState()
    }

    suspend fun initializeCatalog() = withContext(Dispatchers.IO) {
        beginFeedLoadingState()
        try {
            // 1. Scan device MediaStore tracks (standard on Android)
            scanDeviceMediaStoreInternal()

            // 2. Scan custom saved library folders if any
            val savedFolders = scanner.getSavedLibraryFolders()
            for (folderUriStr in savedFolders) {
                try {
                    val uri = Uri.parse(folderUriStr)
                    val tracks = scanner.scanDocumentTree(uri)
                    if (tracks.isNotEmpty()) {
                        addTracksInternal(tracks)
                    }
                } catch (e: Exception) {
                    // Revoked or invalid SAF permission; continue with MediaStore tracks
                }
            }

            // 3. Fetch online recommendations & genre while holding skeleton state
            val likedSnapshot = synchronized(catalogStateLock) { _likedTracks }
            fetchOnlineRecommendationsInternal(
                likedTracks = likedSnapshot,
                showLoadingState = true
            )
        } finally {
            endFeedLoadingState()
        }
    }

    suspend fun scanDeviceMediaStore() = withContext(Dispatchers.IO) {
        _isScanning.value = true
        try {
            scanDeviceMediaStoreInternal()
            val likedSnapshot = synchronized(catalogStateLock) { _likedTracks }
            fetchOnlineRecommendationsInternal(
                likedTracks = likedSnapshot,
                showLoadingState = !_hasInitialFeedSettled
            )
        } finally {
            _isScanning.value = false
        }
    }

    private suspend fun scanDeviceMediaStoreInternal() {
        try {
            val tracks = scanner.scanMediaStoreTracks()
            addTracksInternal(tracks)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) runCatching { Log.w("MediaCatalogService", "Catalog operation failed", e) }
        }
    }

    suspend fun addCustomLibraryFolder(treeUri: Uri) = withContext(Dispatchers.IO) {
        _isScanning.value = true
        try {
            scanner.saveLibraryFolder(treeUri.toString())
            val tracks = scanner.scanDocumentTree(treeUri)
            addTracksInternal(tracks)
            val likedSnapshot = synchronized(catalogStateLock) { _likedTracks }
            fetchOnlineRecommendationsInternal(
                likedTracks = likedSnapshot,
                showLoadingState = !_hasInitialFeedSettled
            )
        } finally {
            _isScanning.value = false
        }
    }

    fun removeLibraryFolder(uriStr: String) {
        scanner.removeLibraryFolder(uriStr)
    }

    fun recordTrackPlayed(track: TrackItem) {
        val cleanTrack = YouTubeMusicService.sanitizePersistedOnlineTrack(
            track,
            ytmService.getAtvCounterpartSnapshot()
        ).first
        val nowMs = System.currentTimeMillis()
        synchronized(catalogStateLock) {
            _recentlyPlayed.removeAll { isSameAlbumOrItem(it, cleanTrack) }
            _recentlyPlayed.add(0, cleanTrack)
            while (_recentlyPlayed.size > 50) {
                _recentlyPlayed.removeAt(_recentlyPlayed.lastIndex)
            }
            recordPlaybackHistoryEventLocked(cleanTrack, nowMs)
        }
        updateFeedState()

        // Offload SharedPreferences / JSON serialization to Dispatchers.IO so track taps never block Main
        serviceScope.launch(Dispatchers.IO) {
            val stampedTrack = trackAddedAtStore.stampTrack(cleanTrack)
            if (stampedTrack.dateAddedMs != cleanTrack.dateAddedMs) {
                synchronized(catalogStateLock) {
                    val idx = _recentlyPlayed.indexOfFirst { it.id == cleanTrack.id }
                    if (idx >= 0) {
                        _recentlyPlayed[idx] = stampedTrack
                    }
                }
            }
            saveRecentlyPlayed()
            savePlaybackHistory()
        }

        // Debounce & asynchronously refresh recommendations based on the updated user listening history
        recFetchJob?.cancel()
        recFetchJob = serviceScope.launch {
            delay(400L)
            val likedSnapshot = synchronized(catalogStateLock) { _likedTracks }
            fetchOnlineRecommendationsInternal(
                likedTracks = likedSnapshot,
                showLoadingState = false
            )
        }
    }

    private fun inferTrackGenreTag(track: TrackItem, fallbackGenre: String): String {
        val combined = "${track.title} ${track.artist} ${track.album}".lowercase(Locale.ROOT)
        return when {
            combined.contains("synth") || combined.contains("retro") || combined.contains("outrun") -> "Synthwave"
            combined.contains("rock") || combined.contains("metal") || combined.contains("punk") -> "Rock"
            combined.contains("indie") || combined.contains("alternative") || combined.contains("alt") -> "Indie Pop"
            combined.contains("electro") || combined.contains("edm") || combined.contains("house") || combined.contains("dance") -> "Electronic"
            combined.contains("r&b") || combined.contains("soul") || combined.contains("weeknd") -> "R&B"
            fallbackGenre.isNotBlank() -> fallbackGenre
            else -> "Pop"
        }
    }

    private fun recordPlaybackHistoryEventLocked(track: TrackItem, timestampMs: Long) {
        val key = track.id.trim().ifBlank { "${track.title.trim().lowercase(Locale.ROOT)}|${track.artist.trim().lowercase(Locale.ROOT)}" }
        val existing = _playbackHistoryEntries[key]
        val genre = existing?.genreTag?.takeIf { it.isNotBlank() } ?: inferTrackGenreTag(track, _selectedGenre)
        val updatedTimestamps = ((existing?.playTimestampsMs ?: emptyList()) + timestampMs).takeLast(50)
        val updatedCount = (existing?.totalPlayCount ?: 0) + 1
        _playbackHistoryEntries[key] = PlaybackHistoryEntry(
            track = track,
            lastPlayedAtMs = timestampMs,
            playTimestampsMs = updatedTimestamps,
            totalPlayCount = updatedCount,
            genreTag = genre
        )
        if (_playbackHistoryEntries.size > 200) {
            val overflow = _playbackHistoryEntries.size - 200
            val oldestKeys = _playbackHistoryEntries.entries
                .sortedBy { it.value.lastPlayedAtMs }
                .take(overflow)
                .map { it.key }
            oldestKeys.forEach { _playbackHistoryEntries.remove(it) }
        }
    }

    private fun loadPlaybackHistory(seedTracks: List<TrackItem>) {
        val raw = playbackHistoryPrefs.getString("playback_history_events_json", null)
        synchronized(catalogStateLock) {
            _playbackHistoryEntries.clear()
            if (!raw.isNullOrBlank()) {
                try {
                    val array = JSONArray(raw)
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        val trackObj = obj.optJSONObject("track") ?: continue
                        val id = trackObj.optString("id")
                        if (id.isBlank()) continue
                        val track = TrackItem(
                            id = id,
                            title = trackObj.optString("title", "Unknown Title"),
                            artist = trackObj.optString("artist", "Unknown Artist"),
                            album = trackObj.optString("album", ""),
                            durationMs = trackObj.optLong("durationMs", 0L),
                            artworkResId = if (trackObj.has("artworkResId")) trackObj.getInt("artworkResId") else null,
                            artworkUri = trackObj.optString("artworkUri").takeIf { it.isNotBlank() },
                            audioUrl = trackObj.optString("audioUrl").takeIf { it.isNotBlank() },
                            isLiked = trackObj.optBoolean("isLiked", false),
                            isExplicit = trackObj.optBoolean("isExplicit", false),
                            playCount = trackObj.optLong("playCount", 0L),
                            dateAddedMs = trackObj.optLong("dateAddedMs", 0L)
                        )
                        val tsArray = obj.optJSONArray("timestamps") ?: JSONArray()
                        val timestamps = mutableListOf<Long>()
                        for (j in 0 until tsArray.length()) {
                            val ts = tsArray.optLong(j, 0L)
                            if (ts > 0L) timestamps.add(ts)
                        }
                        val lastPlayed = obj.optLong("lastPlayedAtMs", timestamps.lastOrNull() ?: System.currentTimeMillis())
                        val totalPlays = obj.optInt("totalPlayCount", timestamps.size.coerceAtLeast(1))
                        val genre = obj.optString("genreTag", inferTrackGenreTag(track, _selectedGenre))
                        _playbackHistoryEntries[id] = PlaybackHistoryEntry(
                            track = track,
                            lastPlayedAtMs = lastPlayed,
                            playTimestampsMs = if (timestamps.isEmpty()) listOf(lastPlayed) else timestamps,
                            totalPlayCount = totalPlays,
                            genreTag = genre
                        )
                    }
                } catch (_: Exception) {
                }
            }
            if (_playbackHistoryEntries.isEmpty() && seedTracks.isNotEmpty()) {
                val now = System.currentTimeMillis()
                seedTracks.forEachIndexed { idx, track ->
                    val ts = now - idx * 3_600_000L
                    _playbackHistoryEntries[track.id] = PlaybackHistoryEntry(
                        track = track,
                        lastPlayedAtMs = ts,
                        playTimestampsMs = listOf(ts),
                        totalPlayCount = (seedTracks.size - idx).coerceIn(1, 5),
                        genreTag = inferTrackGenreTag(track, defaultGenres[idx % defaultGenres.size])
                    )
                }
            }
        }
    }

    private fun savePlaybackHistory() {
        try {
            val entries = synchronized(catalogStateLock) {
                _playbackHistoryEntries.values
                    .sortedByDescending { it.lastPlayedAtMs }
                    .take(150)
            }
            val array = JSONArray()
            for (entry in entries) {
                val track = entry.track
                val trackObj = JSONObject().apply {
                    put("id", track.id)
                    put("title", track.title)
                    put("artist", track.artist)
                    put("album", track.album)
                    put("durationMs", track.durationMs)
                    if (track.artworkResId != null) put("artworkResId", track.artworkResId)
                    if (!track.artworkUri.isNullOrBlank()) put("artworkUri", track.artworkUri)
                    if (!track.audioUrl.isNullOrBlank()) put("audioUrl", track.audioUrl)
                    put("isLiked", track.isLiked)
                    put("isExplicit", track.isExplicit)
                    put("playCount", track.playCount)
                    if (track.dateAddedMs > 0L) put("dateAddedMs", track.dateAddedMs)
                }
                val tsArray = JSONArray()
                entry.playTimestampsMs.forEach { tsArray.put(it) }
                val obj = JSONObject().apply {
                    put("track", trackObj)
                    put("lastPlayedAtMs", entry.lastPlayedAtMs)
                    put("totalPlayCount", entry.totalPlayCount)
                    put("genreTag", entry.genreTag)
                    put("timestamps", tsArray)
                }
                array.put(obj)
            }
            playbackHistoryPrefs.edit().putString("playback_history_events_json", array.toString()).apply()
        } catch (_: Exception) {
        }
    }

    private fun buildSmartSuggestedPlaylists(
        historyEntries: List<PlaybackHistoryEntry>,
        recentlyPlayed: List<TrackItem>,
        likedTracks: List<TrackItem>,
        recommendedTracks: List<TrackItem>,
        genreTracks: List<TrackItem>,
        allTracks: List<TrackItem>
    ): List<UserPlaylist> {
        val nowMs = System.currentTimeMillis()
        val sevenDaysAgoMs = nowMs - 7L * 24L * 3600L * 1000L

        val fallbackPool = (recentlyPlayed + likedTracks + recommendedTracks + genreTracks + allTracks)
            .distinctBy { it.id }

        if (historyEntries.isEmpty() && fallbackPool.isEmpty()) {
            return emptyList()
        }

        // 1. "Top 20 de la última semana" (Top 20 songs played in the last 7 days)
        val weeklyScored = historyEntries
            .map { entry ->
                val playsLast7d = entry.playTimestampsMs.count { it >= sevenDaysAgoMs }
                    .let { if (it == 0 && entry.lastPlayedAtMs >= sevenDaysAgoMs) 1 else it }
                Triple(entry.track, playsLast7d, entry.lastPlayedAtMs)
            }
            .sortedWith(
                compareByDescending<Triple<TrackItem, Int, Long>> { it.second }
                    .thenByDescending { it.third }
            )
            .map { it.first }

        val top20WeekTracks = LinkedHashMap<String, TrackItem>()
        for (t in weeklyScored) {
            if (top20WeekTracks.size >= 20) break
            top20WeekTracks.putIfAbsent(t.id, t)
        }
        for (t in (recentlyPlayed + likedTracks + recommendedTracks + allTracks)) {
            if (top20WeekTracks.size >= 20) break
            top20WeekTracks.putIfAbsent(t.id, t)
        }

        // 2. "Mix de Géneros Escuchados" (Selection of up to 25 tracks across the genres listened to by the user)
        val listenedGenres = historyEntries
            .groupingBy { it.genreTag.ifBlank { "Pop" } }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
            .ifEmpty { defaultGenres.take(3) }

        val genreMixTracks = LinkedHashMap<String, TrackItem>()
        // Interleave tracks from history matching the user's top genres + genreTracks + recommendedTracks
        val byGenre = historyEntries.groupBy { it.genreTag.ifBlank { "Pop" } }
        for (genre in listenedGenres) {
            byGenre[genre].orEmpty()
                .sortedByDescending { it.lastPlayedAtMs }
                .forEach { entry ->
                    if (genreMixTracks.size < 25) {
                        genreMixTracks.putIfAbsent(entry.track.id, entry.track)
                    }
                }
        }
        for (t in (genreTracks + recommendedTracks + likedTracks + recentlyPlayed + allTracks)) {
            if (genreMixTracks.size >= 25) break
            genreMixTracks.putIfAbsent(t.id, t)
        }
        val genreSubtitle = listenedGenres.take(3).joinToString(", ")

        // 3. "En Repetición" (Most replayed / heavy rotation songs)
        val onRepeatScored = historyEntries
            .sortedWith(
                compareByDescending<PlaybackHistoryEntry> { it.totalPlayCount }
                    .thenByDescending { it.lastPlayedAtMs }
            )
            .map { it.track }
        val onRepeatTracks = LinkedHashMap<String, TrackItem>()
        for (t in (onRepeatScored + likedTracks + recentlyPlayed + recommendedTracks)) {
            if (onRepeatTracks.size >= 20) break
            onRepeatTracks.putIfAbsent(t.id, t)
        }

        // 4. "Descubrimiento según tus gustos" (Personalized discovery based on top artists & listening profile)
        val topPlayedIds = historyEntries.take(5).map { it.track.id }.toSet()
        val discoveryCandidates = (recommendedTracks + genreTracks + likedTracks + allTracks + recentlyPlayed)
            .sortedBy { if (it.id in topPlayedIds) 1 else 0 }
        val discoveryTracks = LinkedHashMap<String, TrackItem>()
        for (t in discoveryCandidates) {
            if (discoveryTracks.size >= 20) break
            discoveryTracks.putIfAbsent(t.id, t)
        }

        val suggestions = mutableListOf<UserPlaylist>()
        if (top20WeekTracks.isNotEmpty()) {
            suggestions.add(
                UserPlaylist(
                    id = "smart_top20_week",
                    title = "Top 20 This Week",
                    description = "Your 20 most played tracks from the last 7 days",
                    tracks = top20WeekTracks.values.toList(),
                    isSmartSuggestion = true,
                    suggestionBadge = "Top 20 • 7 Days",
                    categoryTag = "Weekly History"
                )
            )
        }
        if (genreMixTracks.isNotEmpty()) {
            suggestions.add(
                UserPlaylist(
                    id = "smart_genre_mix_25",
                    title = "Genre Mix",
                    description = "${genreMixTracks.size}-track selection • $genreSubtitle",
                    tracks = genreMixTracks.values.toList(),
                    isSmartSuggestion = true,
                    suggestionBadge = "25-Track Selection",
                    categoryTag = "Genre Mix"
                )
            )
        }
        if (onRepeatTracks.isNotEmpty()) {
            suggestions.add(
                UserPlaylist(
                    id = "smart_on_repeat",
                    title = "On Repeat",
                    description = "The songs you've been replaying the most recently",
                    tracks = onRepeatTracks.values.toList(),
                    isSmartSuggestion = true,
                    suggestionBadge = "On Repeat",
                    categoryTag = "Favorites"
                )
            )
        }
        if (discoveryTracks.isNotEmpty()) {
            suggestions.add(
                UserPlaylist(
                    id = "smart_taste_discovery",
                    title = "Discovery Mix",
                    description = "Personalized picks inspired by your favorite artists and genres",
                    tracks = discoveryTracks.values.toList(),
                    isSmartSuggestion = true,
                    suggestionBadge = "For You",
                    categoryTag = "Discovery"
                )
            )
        }
        return suggestions
    }

    suspend fun selectGenre(genre: String) = withContext(Dispatchers.IO) {
        synchronized(catalogStateLock) {
            _selectedGenre = genre
            _genreTracks.clear()
        }
        _homeFeed.update { it.copy(selectedGenreTag = genre, genreTracks = emptyList()) }
        try {
            val genreResult = ytmService.searchCatalog("$genre hits songs")
            synchronized(catalogStateLock) {
                _genreTracks.clear()
                _genreTracks.addAll(genreResult.songs)
            }
            updateFeedState()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) runCatching { Log.w("MediaCatalogService", "Catalog operation failed", e) }
            updateFeedState()
        }
    }

    fun updateLikedSeeds(likedTracks: List<TrackItem>) {
        val stamped = trackAddedAtStore.stampTracks(likedTracks)
        synchronized(catalogStateLock) {
            _likedTracks = stamped
        }
    }

    fun recordTrackAddedTimestamp(
        track: TrackItem,
        timestampMs: Long = System.currentTimeMillis()
    ): TrackItem = trackAddedAtStore.stampTrack(track, fallbackNowMs = timestampMs)

    /**
     * Returns unified local + online library tracks sorted newest-first by [TrackItem.dateAddedMs].
     * Real local timestamps (MediaStore `DATE_ADDED` / `DATE_MODIFIED` / file `lastModified` / SAF
     * `DocumentFile.lastModified`) are preserved, while online tracks receive persisted first-seen
     * timestamps in `"xperience_added_timestamps_prefs"`.
     */
    fun getRecentlyAddedLibraryTracks(allLibraryTracks: List<TrackItem>): List<TrackItem> {
        val stamped = trackAddedAtStore.stampTracks(allLibraryTracks)
        return sortRecentlyAddedLibraryTracks(stamped)
    }

    /**
     * Convenience wrapper combining local, online, and liked tracks currently known to the catalog,
     * stamped with persistent [TrackItem.dateAddedMs] and sorted descending.
     */
    fun getAllRecentlyAddedTracks(extraTracks: List<TrackItem> = emptyList()): List<TrackItem> {
        val combined = synchronized(catalogStateLock) {
            _allTracks.toList() + _onlineTracks.value + _likedTracks + extraTracks
        }
        return getRecentlyAddedLibraryTracks(combined)
    }

    suspend fun refreshHomeFeed() = withContext(Dispatchers.IO) {
        val likedSnapshot = synchronized(catalogStateLock) { _likedTracks }
        fetchOnlineRecommendationsInternal(
            likedTracks = likedSnapshot,
            showLoadingState = true
        )
    }

    suspend fun syncYouTubeAccountAndFeed(likedTracks: List<TrackItem> = _likedTracks): YtmAccountState = withContext(Dispatchers.IO) {
        if (likedTracks.isNotEmpty()) {
            updateLikedSeeds(likedTracks)
        }
        beginFeedLoadingState()
        try {
            val state = if (authManager.isSignedIn()) {
                ytmService.fetchAccountInfo()
            } else {
                YtmAccountState(isSignedIn = false)
            }
            val accountValidated = !authManager.isSignedIn() ||
                (state.isSignedIn && (!state.accountName.isNullOrBlank() || !state.accountHandle.isNullOrBlank()))
            if (accountValidated) {
                val likedSnapshot = synchronized(catalogStateLock) { _likedTracks }
                fetchOnlineRecommendationsInternal(
                    likedTracks = likedSnapshot,
                    showLoadingState = true,
                    prefetchedAccountState = state
                )
            }
            state
        } finally {
            endFeedLoadingState()
        }
    }

    suspend fun signOutYouTubeAccount() = withContext(Dispatchers.IO) {
        authManager.clearSession()
        ytmService.clearAuthCaches()
        WebViewSessionCleaner.clearAll()
        _isPersonalizedFromAccount = false
        val likedSnapshot = synchronized(catalogStateLock) { _likedTracks }
        fetchOnlineRecommendationsInternal(
            likedTracks = likedSnapshot,
            showLoadingState = true
        )
    }

    suspend fun resolveRadioStationTracks(station: RadioStationItem): List<TrackItem> =
        ytmService.resolveRadioStationTracks(station)

    suspend fun resolvePlaylistTracks(item: RecommendedPlaylistItem): List<TrackItem> =
        ytmService.resolvePlaylistTracks(item)

    suspend fun fetchOnlineRecommendations() {
        val likedSnapshot = synchronized(catalogStateLock) { _likedTracks }
        fetchOnlineRecommendations(likedSnapshot)
    }

    suspend fun fetchOnlineRecommendations(likedTracks: List<TrackItem>) {
        fetchOnlineRecommendationsInternal(
            likedTracks = likedTracks,
            showLoadingState = !_hasInitialFeedSettled
        )
    }

    private suspend fun fetchOnlineRecommendationsInternal(
        likedTracks: List<TrackItem>,
        showLoadingState: Boolean,
        prefetchedAccountState: YtmAccountState? = null
    ) = withContext(Dispatchers.IO) {
        if (likedTracks.isNotEmpty()) {
            updateLikedSeeds(likedTracks)
        }

        val shouldTrackLoading = showLoadingState || !_hasInitialFeedSettled
        if (shouldTrackLoading) {
            beginFeedLoadingState()
        }

        val (recentSnapshot, likedSnapshot, currentGenre) = synchronized(catalogStateLock) {
            Triple(_recentlyPlayed.toList(), _likedTracks, _selectedGenre)
        }

        val combinedArtists = (recentSnapshot.map { it.artist } + likedSnapshot.map { it.artist })
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals("Unknown Artist", ignoreCase = true) && !it.equals("YouTube Artist", ignoreCase = true) }
            .distinct()

        try {
            withTimeoutOrNull(18_000L) {
                coroutineScope {
                    val accountDeferred = async {
                        prefetchedAccountState ?: if (authManager.isSignedIn()) {
                            ytmService.fetchAccountInfo()
                        } else {
                            YtmAccountState(isSignedIn = false)
                        }
                    }
                    val homeFeedDeferred = async { ytmService.fetchPersonalizedHomeFeed() }
                    val exploreDeferred = async { ytmService.fetchExploreAndNewReleases() }

                    if (combinedArtists.isNotEmpty()) {
                        synchronized(catalogStateLock) {
                            _seedArtists.clear()
                            _seedArtists.addAll(combinedArtists.take(3))
                        }

                        val primaryArtist = combinedArtists.first()
                        val recQuery = if (combinedArtists.size >= 2) {
                            "${combinedArtists[0]} ${combinedArtists[1]} mix songs"
                        } else {
                            "$primaryArtist similar songs"
                        }

                        val recCatalogDeferred = async { ytmService.searchCatalog(recQuery) }
                        val userProfilesDeferred = combinedArtists.take(3).map { artistName ->
                            async { withTimeoutOrNull(8_000L) { ytmService.getArtistProfile(artistName) } }
                        }
                        val userAlbumsDeferred = combinedArtists.take(2).map { artistName ->
                            async {
                                withTimeoutOrNull(8_000L) {
                                    ytmService.executeInnerTubeAlbumSearch(artistName, strictlyAlbumsOnly = true)
                                }.orEmpty()
                            }
                        }
                        val genreCatalogDeferred = async { ytmService.searchCatalog("$currentGenre hits songs") }

                        val accountState = accountDeferred.await()
                        val ytmHome = homeFeedDeferred.await()
                        val (exploreReleases, exploreRadios) = exploreDeferred.await()
                        val recCatalog = recCatalogDeferred.await()

                        val mergedTracks = trackAddedAtStore.stampTracks(
                            (ytmHome.personalizedTracks + recCatalog.songs).distinctBy { it.id }
                        )
                        synchronized(catalogStateLock) {
                            _recommendedTracks.clear()
                            _recommendedTracks.addAll(mergedTracks)
                        }
                        _onlineTracks.value = mergedTracks

                        // Related artists from YouTube Music
                        val relatedArtistsList = mutableListOf<ArtistItem>()
                        for (profile in userProfilesDeferred.awaitAll()) {
                            if (profile != null && profile.similarArtists.isNotEmpty()) {
                                relatedArtistsList.addAll(profile.similarArtists)
                            }
                        }
                        if (relatedArtistsList.isEmpty()) {
                            relatedArtistsList.addAll(recCatalog.artists.filterNot { it.name.equals(primaryArtist, ignoreCase = true) })
                        }
                        val distinctRelated = relatedArtistsList.distinctBy { it.name.lowercase(Locale.ROOT) }
                        val resolvedArtists = distinctRelated.take(12).map { artist ->
                            async(Dispatchers.IO) {
                                val uri = artist.avatarUri
                                val isOfficialAvatar = !uri.isNullOrBlank() &&
                                    (uri.contains("ggpht.com") || uri.contains("googleusercontent.com") || uri.contains("=s540") || uri.contains("=w540"))
                                if (isOfficialAvatar) {
                                    artist
                                } else {
                                    val officialAvatar = ytmService.getArtistAvatarFromCache(artist.name, context)
                                        ?: withTimeoutOrNull(5_000L) { ytmService.getArtistAvatarUri(artist.name, context) }
                                    if (!officialAvatar.isNullOrBlank()) {
                                        artist.copy(avatarUri = officialAvatar)
                                    } else {
                                        artist
                                    }
                                }
                            }
                        }.awaitAll()

                        synchronized(catalogStateLock) {
                            _onlineArtists.clear()
                            _onlineArtists.addAll(resolvedArtists)
                        }
                        _onlineArtistsState.value = resolvedArtists

                        val artistAlbums = userAlbumsDeferred.awaitAll().flatten()
                        val allAlbums = (ytmHome.personalizedAlbums + artistAlbums + recCatalog.albums)
                            .distinctBy { "${it.title.lowercase(Locale.ROOT)}::${it.artist.lowercase(Locale.ROOT)}" }
                        synchronized(catalogStateLock) {
                            _recommendedAlbums.clear()
                            _recommendedAlbums.addAll(allAlbums)
                        }
                        _onlineAlbumsState.value = allAlbums

                        val genreCatalog = genreCatalogDeferred.await()
                        synchronized(catalogStateLock) {
                            _genreTracks.clear()
                            _genreTracks.addAll(genreCatalog.songs)
                        }

                        populateRichDiscoverySections(
                            accountState = accountState,
                            ytmHome = ytmHome,
                            exploreReleases = exploreReleases,
                            exploreRadios = exploreRadios,
                            seedArtists = combinedArtists,
                            candidateTracks = mergedTracks,
                            candidateAlbums = allAlbums,
                            relatedArtists = resolvedArtists
                        )
                    } else {
                        val defDeferred = async { ytmService.fetchDefaultRecommendations() }
                        val genreDeferred = async { ytmService.searchCatalog("$currentGenre hits songs") }

                        val accountState = accountDeferred.await()
                        val ytmHome = homeFeedDeferred.await()
                        val (exploreReleases, exploreRadios) = exploreDeferred.await()
                        val defaultCatalog = defDeferred.await()
                        val genreCatalog = genreDeferred.await()

                        val homeArtists = ytmHome.personalizedTracks
                            .map { it.artist.trim() }
                            .filter { it.isNotBlank() && !it.equals("YouTube Artist", ignoreCase = true) }
                            .distinct()

                        synchronized(catalogStateLock) {
                            _seedArtists.clear()
                            if (homeArtists.isNotEmpty() && authManager.isSignedIn()) {
                                _seedArtists.addAll(homeArtists.take(3))
                            }
                        }

                        val mergedTracks = trackAddedAtStore.stampTracks(
                            (ytmHome.personalizedTracks + defaultCatalog.songs).distinctBy { it.id }
                        )
                        synchronized(catalogStateLock) {
                            _recommendedTracks.clear()
                            _recommendedTracks.addAll(mergedTracks)
                        }
                        _onlineTracks.value = mergedTracks

                        val albums = (ytmHome.personalizedAlbums + defaultCatalog.albums + genreCatalog.albums)
                            .distinctBy { "${it.title.lowercase(Locale.ROOT)}::${it.artist.lowercase(Locale.ROOT)}" }
                        synchronized(catalogStateLock) {
                            _recommendedAlbums.clear()
                            _recommendedAlbums.addAll(albums)
                        }
                        _onlineAlbumsState.value = albums

                        synchronized(catalogStateLock) {
                            _genreTracks.clear()
                            _genreTracks.addAll(genreCatalog.songs)
                        }

                        val onlineArts = (defaultCatalog.artists + genreCatalog.artists)
                            .distinctBy { it.name.lowercase(Locale.ROOT) }
                        synchronized(catalogStateLock) {
                            _onlineArtists.clear()
                            _onlineArtists.addAll(onlineArts)
                        }
                        _onlineArtistsState.value = onlineArts

                        populateRichDiscoverySections(
                            accountState = accountState,
                            ytmHome = ytmHome,
                            exploreReleases = exploreReleases,
                            exploreRadios = exploreRadios,
                            seedArtists = homeArtists.take(3),
                            candidateTracks = mergedTracks,
                            candidateAlbums = albums,
                            relatedArtists = onlineArts
                        )
                    }
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) runCatching { Log.w("MediaCatalogService", "Catalog operation failed", e) }
        } finally {
            if (shouldTrackLoading) {
                endFeedLoadingState()
            } else {
                _hasInitialFeedSettled = true
                updateFeedState()
            }
        }
    }

    private suspend fun populateRichDiscoverySections(
        accountState: YtmAccountState,
        ytmHome: YtmHomeFeedResult,
        exploreReleases: List<NewReleaseItem>,
        exploreRadios: List<RadioStationItem>,
        seedArtists: List<String>,
        candidateTracks: List<TrackItem>,
        candidateAlbums: List<AlbumItem>,
        relatedArtists: List<ArtistItem>
    ) = coroutineScope {
        _isPersonalizedFromAccount = authManager.isSignedIn() && (accountState.isSignedIn || ytmHome.isPersonalized)

        val (likedSnapshot, recentSnapshot) = synchronized(catalogStateLock) {
            _likedTracks to _recentlyPlayed.toList()
        }

        // 1. Playlists Made For You (FEmusic_home playlists + curated fallback search)
        val playlistQueryDeferred = if (ytmHome.playlistsMadeForYou.size < 8) {
            val query = seedArtists.firstOrNull()?.let { "$it mix playlist" } ?: "top hits chill mix playlist"
            async { withTimeoutOrNull(8_000L) { ytmService.searchPlaylists(query, limit = 12) }.orEmpty() }
        } else {
            null
        }

        // 2. Choose 3 distinct seed albums & fetch up to 15 similar albums per seed in parallel
        val seedAlbums = selectSeedAlbumsForMoreLike(
            likedTracks = likedSnapshot,
            recentlyPlayed = recentSnapshot,
            localAlbums = _localAlbums.value,
            ytmAlbums = (ytmHome.personalizedAlbums + candidateAlbums),
            targetCount = 3
        )
        val similarAlbumsDeferreds = seedAlbums.associateWith { seed ->
            async {
                val cleanBrowseId = seed.id.removePrefix("ytm_alb_").removePrefix("ytm_single_")
                    .takeIf { it.startsWith("MPRE") || it.startsWith("OLAK") }
                withTimeoutOrNull(8_000L) {
                    ytmService.getSimilarAlbumsForSeed(
                        seedAlbumTitle = seed.title,
                        seedArtist = seed.artist,
                        seedBrowseId = cleanBrowseId,
                        limit = 15
                    )
                }.orEmpty()
            }
        }

        // Collect Playlists Made For You
        val playlistMap = LinkedHashMap<String, RecommendedPlaylistItem>()
        ytmHome.playlistsMadeForYou.forEach { playlistMap.putIfAbsent(it.playlistId, it) }
        playlistQueryDeferred?.await()?.forEach { playlistMap.putIfAbsent(it.playlistId, it) }
        _playlistsMadeForYou = playlistMap.values.take(15)

        // Collect 16-24 Radio Stations
        val radioMap = LinkedHashMap<String, RadioStationItem>()
        (ytmHome.radioStations + exploreRadios).forEach { radioMap.putIfAbsent(it.playlistId, it) }
        if (radioMap.size < 20) {
            val seedTrackPool = (likedSnapshot + recentSnapshot + candidateTracks)
                .distinctBy { it.id }
            for (track in seedTrackPool) {
                if (radioMap.size >= 24) break
                val videoId = track.id.removePrefix("ytm_")
                if (videoId.isBlank() || videoId.startsWith("local_") || !track.audioUrl.orEmpty().contains("youtube.com")) continue
                val plId = "RDAMVM$videoId"
                radioMap.putIfAbsent(
                    plId,
                    RadioStationItem(
                        id = "radio_$plId",
                        title = "${track.artist} • ${track.title}",
                        subtitle = "Radio • ${track.artist}",
                        artworkUri = track.artworkUri,
                        playlistId = plId,
                        seedVideoId = videoId
                    )
                )
            }
        }
        // Supplement remaining radios with curated playlist mixes if user has local-only tracks
        if (radioMap.size < 16) {
            _playlistsMadeForYou.forEach { pl ->
                if (radioMap.size >= 20) return@forEach
                radioMap.putIfAbsent(
                    pl.playlistId,
                    RadioStationItem(
                        id = "radio_${pl.playlistId}",
                        title = pl.title,
                        subtitle = pl.subtitle,
                        artworkUri = pl.artworkUri,
                        playlistId = pl.playlistId
                    )
                )
            }
        }
        _allRadioStations = radioMap.values.take(24)

        // Collect 16-24 New Releases
        val releaseMap = LinkedHashMap<String, NewReleaseItem>()
        (ytmHome.newReleases + exploreReleases).forEach { item ->
            val key = "${item.title.lowercase(Locale.ROOT)}::${item.artist.lowercase(Locale.ROOT)}"
            releaseMap.putIfAbsent(key, item)
        }
        if (releaseMap.size < 16) {
            candidateAlbums.forEach { alb ->
                if (releaseMap.size >= 24) return@forEach
                val key = "${alb.title.lowercase(Locale.ROOT)}::${alb.artist.lowercase(Locale.ROOT)}"
                val cleanBrowse = alb.id.removePrefix("ytm_alb_").removePrefix("ytm_single_")
                    .takeIf { it.startsWith("MPRE") || it.startsWith("OLAK") }
                releaseMap.putIfAbsent(
                    key,
                    NewReleaseItem(
                        id = "nr_${alb.id}",
                        title = alb.title,
                        artist = alb.artist,
                        subtitle = "Album • ${alb.year}",
                        artworkUri = alb.coverUri,
                        isAlbum = true,
                        browseId = cleanBrowse,
                        year = alb.year.toString()
                    )
                )
            }
        }
        if (releaseMap.size < 16) {
            candidateTracks.forEach { tr ->
                if (releaseMap.size >= 24) return@forEach
                val key = "${tr.title.lowercase(Locale.ROOT)}::${tr.artist.lowercase(Locale.ROOT)}"
                val videoId = tr.id.removePrefix("ytm_").takeIf { tr.id.startsWith("ytm_") }
                releaseMap.putIfAbsent(
                    key,
                    NewReleaseItem(
                        id = "nr_${tr.id}",
                        title = tr.title,
                        artist = tr.artist,
                        subtitle = "Single • ${tr.artist}",
                        artworkUri = tr.artworkUri,
                        isAlbum = false,
                        videoId = videoId
                    )
                )
            }
        }
        _allNewReleases = releaseMap.values.take(24)

        // Collect 3 x 15 More Like Album Sections
        val candidatesBySeedId = LinkedHashMap<String, List<AlbumItem>>()
        similarAlbumsDeferreds.forEach { (seed, deferred) ->
            candidatesBySeedId[seed.id] = deferred.await()
        }
        _moreLikeSections = buildMoreLikeAlbumSections(
            seeds = seedAlbums,
            candidatesBySeedId = candidatesBySeedId,
            maxSections = 3,
            maxAlbumsPerSection = 15
        )

        // Build Top Picks carousel
        _topPicks = buildHomeTopPicks(
            homeCandidates = ytmHome.featuredTopPicks,
            radioStations = _allRadioStations,
            newReleases = _allNewReleases,
            playlists = _playlistsMadeForYou,
            relatedArtists = relatedArtists,
            candidateTracks = candidateTracks,
            isPersonalized = _isPersonalizedFromAccount || recentSnapshot.isNotEmpty() || likedSnapshot.isNotEmpty()
        )
    }

    private fun addTracksInternal(newTracks: List<TrackItem>) {
        val allTracksSnapshot = synchronized(catalogStateLock) {
            val existingIds = _allTracks.map { it.id }.toSet()
            val uniqueNew = newTracks.filter { it.id !in existingIds }
            val stampedNew = trackAddedAtStore.stampTracks(uniqueNew)
            _allTracks.addAll(0, stampedNew)
            _allTracks.toList()
        }

        _localTracks.value = allTracksSnapshot

        // Recompute local albums
        val albums = allTracksSnapshot.groupBy { (it.album.ifBlank { "Unknown Album" }) to (it.artist.ifBlank { "Unknown Artist" }) }
            .map { (key, tracks) ->
                val (albumTitle, artistName) = key
                val firstArt = tracks.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri
                AlbumItem(
                    id = "local_alb_${(albumTitle + "_" + artistName).hashCode()}",
                    title = albumTitle,
                    artist = artistName,
                    coverUri = firstArt,
                    tracksCount = tracks.size
                )
            }
        _localAlbums.value = albums

        // Recompute local artists with cache
        val localArtists = allTracksSnapshot.groupBy { it.artist.ifBlank { "Unknown Artist" } }.map { (artistName, tracks) ->
            val cleanName = if (artistName.contains("<unknown>", ignoreCase = true)) "Unknown Artist" else artistName
            val cachedAvatar = ytmService.getArtistAvatarFromCache(cleanName, context)
                ?: tracks.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri
            ArtistItem(
                id = "artist_${cleanName.hashCode()}",
                name = cleanName,
                avatarUri = cachedAvatar,
                genre = "Local Library",
                monthlyListeners = "${tracks.size} songs"
            )
        }
        _localArtists.value = localArtists

        if (!_isFeedLoading) {
            updateFeedState()
        }
        enrichLocalArtistAvatars()
    }

    private fun updateFeedState() {
        if (_isFeedLoading) {
            _homeFeed.update { it.copy(isFeedLoading = true) }
            return
        }

        val (
            allTracksSnapshot,
            recentlyPlayedSnapshot,
            recommendedTracksSnapshot,
            recommendedAlbumsSnapshot,
            genreTracksSnapshot,
            selectedGenreSnapshot,
            onlineArtistsSnapshot,
            seedArtistsSnapshot
        ) = synchronized(catalogStateLock) {
            CatalogFeedSnapshot(
                allTracks = _allTracks.toList(),
                recentlyPlayed = _recentlyPlayed.toList(),
                recommendedTracks = _recommendedTracks.toList(),
                recommendedAlbums = _recommendedAlbums.toList(),
                genreTracks = _genreTracks.toList(),
                selectedGenre = _selectedGenre,
                onlineArtists = _onlineArtists.toList(),
                seedArtists = _seedArtists.toList()
            )
        }

        val localArtists = _localArtists.value.ifEmpty {
            allTracksSnapshot.groupBy { it.artist }.map { (artistName, tracks) ->
                val firstArt = tracks.firstOrNull { it.artworkUri != null }?.artworkUri
                ArtistItem(
                    id = "artist_${artistName.hashCode()}",
                    name = artistName,
                    avatarUri = firstArt,
                    genre = "Local Library",
                    monthlyListeners = "${tracks.size} songs"
                )
            }
        }

        val allArtists = (onlineArtistsSnapshot + localArtists).distinctBy { it.name.lowercase() }

        val subtitle = when {
            _isPersonalizedFromAccount && !authManager.accountState.value.accountName.isNullOrBlank() ->
                "Personalized for ${authManager.accountState.value.accountName}"
            seedArtistsSnapshot.isNotEmpty() ->
                "Powered by YouTube Music • Inspired by ${seedArtistsSnapshot.joinToString(", ")}"
            else -> "Trending on YouTube Music"
        }

        val (historyEntriesSnapshot, likedTracksSnapshot) = synchronized(catalogStateLock) {
            _playbackHistoryEntries.values.toList() to _likedTracks
        }
        val smartPlaylists = buildSmartSuggestedPlaylists(
            historyEntries = historyEntriesSnapshot,
            recentlyPlayed = recentlyPlayedSnapshot,
            likedTracks = likedTracksSnapshot,
            recommendedTracks = recommendedTracksSnapshot,
            genreTracks = genreTracksSnapshot,
            allTracks = allTracksSnapshot
        )
        _smartSuggestedPlaylists.value = smartPlaylists

        _homeFeed.update {
            it.copy(
                personalizedSubtitle = subtitle,
                recentlyPlayedTracks = recentlyPlayedSnapshot,
                relatedArtists = allArtists.take(12),
                recommendedTracks = recommendedTracksSnapshot.ifEmpty { allTracksSnapshot.take(15) },
                recommendedAlbums = recommendedAlbumsSnapshot,
                genreTags = defaultGenres,
                selectedGenreTag = selectedGenreSnapshot,
                genreTracks = genreTracksSnapshot,
                topPicks = _topPicks,
                moreLikeSections = _moreLikeSections,
                playlistsMadeForYou = _playlistsMadeForYou,
                smartSuggestedPlaylists = smartPlaylists,
                radioStations = _allRadioStations.take(8),
                allRadioStations = _allRadioStations,
                newReleases = _allNewReleases.take(8),
                allNewReleases = _allNewReleases,
                isPersonalizedFromAccount = _isPersonalizedFromAccount,
                isFeedLoading = false
            )
        }
        if (allArtists.isNotEmpty()) {
            appleMusicCanvasService.prefetchArtists(allArtists.take(6).map { it.name }, maxCount = 6)
        }
    }

    private data class CatalogFeedSnapshot(
        val allTracks: List<TrackItem>,
        val recentlyPlayed: List<TrackItem>,
        val recommendedTracks: List<TrackItem>,
        val recommendedAlbums: List<AlbumItem>,
        val genreTracks: List<TrackItem>,
        val selectedGenre: String,
        val onlineArtists: List<ArtistItem>,
        val seedArtists: List<String>
    )

    private var enrichJob: Job? = null

    fun enrichLocalArtistAvatars() {
        enrichJob?.cancel()
        enrichJob = serviceScope.launch {
            val currentArtists = _localArtists.value
            val gate = Semaphore(4)
            for (artist in currentArtists) {
                if (artist.avatarUri != null && artist.avatarUri.startsWith("http")) continue
                launch {
                    gate.withPermit {
                        val avatarUrl = ytmService.getArtistAvatarUri(artist.name, context)
                        if (!avatarUrl.isNullOrBlank()) {
                            _localArtists.update { list ->
                                list.map { item ->
                                    if (item.name.equals(artist.name, ignoreCase = true)) item.copy(avatarUri = avatarUrl) else item
                                }
                            }
                            if (!_isFeedLoading) {
                                updateFeedState()
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Progressively streams an [ArtistDetailProfile]:
     * 1. Emits immediately from any warm Apple Music + YouTube Music caches (and local matches).
     * 2. Runs Apple Music artist assets (wordmark, motion header video, hero banner, palette colors)
     *    and YouTube Music artist catalog in parallel, emitting updates as soon as EACH completes
     *    without blocking fast header media on slow catalog scrapes.
     */
    fun observeArtistDetail(
        artistName: String,
        existingAvatarUri: String? = null
    ): Flow<ArtistDetailProfile> {
        val cleanName = artistName.split(",", "&", ";", "/").firstOrNull()?.trim().orEmpty().ifBlank { artistName.trim() }
        val allTracksSnapshot = synchronized(catalogStateLock) { _allTracks.toList() }
        val localMatches = if (cleanName.isNotBlank()) {
            allTracksSnapshot.filter {
                it.artist.equals(cleanName, ignoreCase = true) || it.artist.contains(cleanName, ignoreCase = true)
            }
        } else {
            emptyList()
        }
        val localAlbumsForArtist = if (cleanName.isNotBlank()) {
            _localAlbums.value.filter {
                it.artist.equals(cleanName, ignoreCase = true) ||
                    it.artist.split(",", "&", ";", "/").any { a -> a.trim().equals(cleanName, ignoreCase = true) }
            }
        } else {
            emptyList()
        }
        val cachedYtmAvatar = if (cleanName.isNotBlank()) {
            ytmService.getArtistAvatarFromCache(cleanName, context)
        } else {
            null
        }
        val fallbackAvatar = existingAvatarUri?.takeIf { it.isNotBlank() }
            ?: cachedYtmAvatar?.takeIf { it.isNotBlank() }
            ?: localMatches.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri
            ?: ""

        return buildProgressiveArtistDetailFlow(
            artistName = cleanName,
            fallbackAvatarUri = fallbackAvatar,
            localMatches = localMatches,
            localAlbumsForArtist = localAlbumsForArtist,
            ytmService = ytmService,
            appleMusicCanvasService = appleMusicCanvasService
        )
    }

    suspend fun getArtistDetail(artistName: String, existingAvatarUri: String? = null): ArtistDetailProfile = withContext(Dispatchers.IO) {
        observeArtistDetail(artistName, existingAvatarUri).last()
    }

    /**
     * Progressively streams an [AlbumDetailProfile], emitting cached/local state at t=0ms
     * and updating independently as Apple Music Canvas and YouTube Music tracklist resolve.
     */
    fun observeAlbumDetail(album: AlbumItem): Flow<AlbumDetailProfile> {
        val albumTitle = album.title.trim()
        val artistName = album.artist.split(",", "&", ";", "/").firstOrNull()?.trim() ?: album.artist.trim()

        val localAlbumTracks = _localTracks.value.filter {
            it.album.equals(albumTitle, ignoreCase = true) &&
                (artistName.isBlank() || it.artist.contains(artistName, ignoreCase = true) || artistName.contains(it.artist, ignoreCase = true))
        }.sortedBy { it.title }

        val localOtherAlbums = _localAlbums.value.filter {
            !it.title.equals(albumTitle, ignoreCase = true) &&
                (artistName.isNotBlank() && (it.artist.contains(artistName, ignoreCase = true) || artistName.contains(it.artist, ignoreCase = true)))
        }

        return buildProgressiveAlbumDetailFlow(
            album = album,
            artistName = artistName,
            localAlbumTracks = localAlbumTracks,
            localOtherAlbums = localOtherAlbums,
            ytmService = ytmService,
            appleMusicCanvasService = appleMusicCanvasService,
            appContext = context.applicationContext
        )
    }

    suspend fun getAlbumDetail(album: AlbumItem): AlbumDetailProfile = withContext(Dispatchers.IO) {
        observeAlbumDetail(album).last()
    }

    suspend fun getRecommendationsForLiked(likedTracks: List<TrackItem>): List<TrackItem> = withContext(Dispatchers.IO) {
        if (likedTracks.isEmpty()) {
            return@withContext try {
                ytmService.searchCatalog("Top hits songs").songs.take(12)
            } catch (e: Exception) {
                emptyList()
            }
        }

        val seedArtists = likedTracks.map { it.artist }
            .filter { it.isNotBlank() && !it.equals("Unknown Artist", ignoreCase = true) }
            .distinct()
            .take(3)

        val query = if (seedArtists.isNotEmpty()) {
            "${seedArtists.joinToString(" ")} mix songs"
        } else {
            val seedTitles = likedTracks.map { it.title }.take(2)
            "${seedTitles.joinToString(" ")} similar songs"
        }

        try {
            val result = ytmService.searchCatalog(query)
            val likedIds = likedTracks.map { it.id }.toSet()
            val likedTitles = likedTracks.map { it.title.trim().lowercase() }.toSet()
            val filtered = result.songs.filter { it.id !in likedIds && it.title.trim().lowercase() !in likedTitles }
            if (filtered.isNotEmpty()) filtered.take(15) else result.songs.take(15)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) runCatching { Log.w("MediaCatalogService", "Catalog operation failed", e) }
            emptyList()
        }
    }
}

/**
 * Pure Kotlin progressive [ArtistDetailProfile] pipeline shared by [MediaCatalogService]
 * and unit tests so fast Apple Music editorial media emits without waiting for slower YTM scrapes.
 */
internal fun buildProgressiveArtistDetailFlow(
    artistName: String,
    fallbackAvatarUri: String = "",
    localMatches: List<TrackItem> = emptyList(),
    localAlbumsForArtist: List<AlbumItem> = emptyList(),
    ytmService: YouTubeMusicService,
    appleMusicCanvasService: AppleMusicCanvasService
): Flow<ArtistDetailProfile> = channelFlow {
    val cleanName = artistName.trim()
    var appleAssets = appleMusicCanvasService.peekArtistAssets(cleanName)
    var ytmProfile = ytmService.peekArtistProfile(cleanName)
    var appleDone = appleAssets != null
    var ytmDone = ytmProfile != null

    fun mergeSnapshot(): ArtistDetailProfile {
        val currentYtm = ytmProfile
        val currentApple = appleAssets
        val finalAvatar = currentYtm?.avatarUri?.ifBlank { null }
            ?: fallbackAvatarUri
        val finalBanner = currentYtm?.bannerImageUri?.ifBlank { null }
            ?: currentApple?.bannerUri?.ifBlank { null }
            ?: finalAvatar
        val topTracks = if (currentYtm != null && currentYtm.topTracks.isNotEmpty()) {
            currentYtm.topTracks
        } else {
            localMatches.take(10)
        }
        val combinedAlbumsList = (localAlbumsForArtist + (currentYtm?.combinedAlbums ?: emptyList()))
            .distinctBy { it.title.trim().lowercase() }
            .ensureUniqueNonBlankIds("alb", { it.id }, { it.title }, { item, id -> item.copy(id = id) })
        val topTracksDeduped = topTracks
            .ensureUniqueNonBlankIds("trk", { it.id }, { it.title }, { item, id -> item.copy(id = id) })
        val singlesDeduped = (currentYtm?.singlesAndEps ?: emptyList())
            .ensureUniqueNonBlankIds("sng", { it.id }, { it.title }, { item, id -> item.copy(id = id) })
        val playlistsDeduped = (currentYtm?.artistPlaylists ?: emptyList())
            .ensureUniqueNonBlankIds("pls", { it.id }, { it.title }, { item, id -> item.copy(id = id) })
        val similarDeduped = (currentYtm?.similarArtists ?: emptyList())
            .ensureUniqueNonBlankIds("sim", { it.id }, { it.name }, { item, id -> item.copy(id = id) })
        val monthlyText = currentYtm?.monthlyListenersText?.ifBlank { null }
            ?: if (ytmDone && localMatches.isNotEmpty()) "${localMatches.size} songs in library" else ""
        val subtitleText = currentYtm?.metadataSubtitle?.ifBlank { null }
            ?: if (ytmDone) "Official Artist" else ""
        val latestRelease = currentYtm?.latestRelease
            ?: ytmService.selectArtistLatestRelease(
                studioAlbums = combinedAlbumsList,
                singlesAndEps = singlesDeduped,
                artistName = currentYtm?.name?.ifBlank { cleanName } ?: cleanName
            )
        val officialVideosDeduped = (currentYtm?.officialVideos ?: emptyList())
            .ensureUniqueNonBlankIds("vid", { it.id }, { it.title }, { item, id -> item.copy(id = id) })

        return ArtistDetailProfile(
            name = currentYtm?.name?.ifBlank { cleanName } ?: cleanName,
            avatarUri = finalAvatar,
            bannerImageUri = finalBanner,
            customTitleLogoUri = currentApple?.wordmarkUri ?: "",
            headerVideoUri = currentApple?.headerVideoUri ?: "",
            subscribersCountText = currentYtm?.subscribersCountText ?: "",
            monthlyListenersText = monthlyText,
            biography = currentYtm?.biography ?: "",
            metadataSubtitle = subtitleText,
            topTracks = topTracksDeduped,
            combinedAlbums = combinedAlbumsList,
            singlesAndEps = singlesDeduped,
            artistPlaylists = playlistsDeduped,
            similarArtists = similarDeduped,
            latestRelease = latestRelease,
            latestReleaseTypeBadge = latestRelease?.releaseTypeBadgeText.orEmpty(),
            latestReleaseDateText = latestRelease?.formattedReleaseDate.orEmpty(),
            officialVideos = officialVideosDeduped,
            isHeaderMediaLoading = !appleDone,
            isCatalogLoading = !ytmDone,
            keyColorRgb = currentApple?.bgColorHex?.ifBlank { null } ?: currentApple?.keyColorHex ?: ""
        )
    }

    send(mergeSnapshot())
    if (appleDone && ytmDone) return@channelFlow

    val mergeMutex = Mutex()
    val jobs = mutableListOf<Job>()

    if (!appleDone) {
        jobs += launch(Dispatchers.IO) {
            val fetchedApple = try {
                appleMusicCanvasService.getArtistAssets(cleanName, priority = true)
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                null
            }
            mergeMutex.withLock {
                appleAssets = fetchedApple
                appleDone = true
                send(mergeSnapshot())
            }
        }
    }

    if (!ytmDone) {
        jobs += launch(Dispatchers.IO) {
            val fetchedYtm = try {
                ytmService.getArtistProfile(cleanName)
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                null
            }
            if (fetchedYtm != null && fetchedYtm.similarArtists.isNotEmpty()) {
                appleMusicCanvasService.prefetchArtists(
                    fetchedYtm.similarArtists.map { it.name },
                    maxCount = 6
                )
            }
            mergeMutex.withLock {
                ytmProfile = fetchedYtm
                ytmDone = true
                send(mergeSnapshot())
            }
        }
    }

    jobs.joinAll()
}.flowOn(Dispatchers.IO)

/**
 * Pure Kotlin progressive [AlbumDetailProfile] pipeline shared by [MediaCatalogService]
 * and unit tests so fast Canvas / cached tracklist states emit without mutual blocking.
 */
internal fun buildProgressiveAlbumDetailFlow(
    album: AlbumItem,
    artistName: String,
    localAlbumTracks: List<TrackItem> = emptyList(),
    localOtherAlbums: List<AlbumItem> = emptyList(),
    ytmService: YouTubeMusicService,
    appleMusicCanvasService: AppleMusicCanvasService,
    appContext: Context? = null
): Flow<AlbumDetailProfile> = channelFlow {
    val albumTitle = album.title.trim()
    val hasValidAlbum = appleMusicCanvasService.isValidAlbumName(albumTitle)
    var ytmDetail = ytmService.peekAlbumDetail(album.id, albumTitle, artistName)
    var canvasResult = if (hasValidAlbum) {
        appleMusicCanvasService.peekAlbumCanvasArtwork(albumTitle, artistName)
    } else {
        null
    }
    var ytmDone = ytmDetail != null
    var canvasDone = !hasValidAlbum || canvasResult != null

    fun warmCanvasIfNeeded(result: CanvasArtworkResult?) {
        val ctx = appContext ?: return
        val uri = result?.videoUri ?: return
        if (!result.isHls && uri.startsWith("http")) {
            launch(Dispatchers.IO) {
                runCatching { MediaNetworkClients.warmCanvasVideoCache(ctx, uri) }
            }
        }
    }

    fun mergeSnapshot(): AlbumDetailProfile {
        val currentYtm = ytmDetail
        val finalTracks = if (currentYtm != null && currentYtm.tracks.isNotEmpty()) {
            currentYtm.tracks.map { ytmTrack ->
                val matchingLocal = localAlbumTracks.firstOrNull {
                    it.title.equals(ytmTrack.title, ignoreCase = true)
                }
                if (matchingLocal != null) {
                    ytmTrack.copy(id = matchingLocal.id, audioUrl = matchingLocal.audioUrl)
                } else {
                    ytmTrack
                }
            }
        } else {
            localAlbumTracks
        }

        val combinedOther = (localOtherAlbums + (currentYtm?.otherAlbums ?: emptyList()))
            .distinctBy { it.title.trim().lowercase() }
            .filterNot { it.title.equals(albumTitle, ignoreCase = true) }
            .ensureUniqueNonBlankIds("oth", { it.id }, { it.title }, { item, id -> item.copy(id = id) })

        val finalCover = currentYtm?.coverArtUri?.ifBlank { null }
            ?: album.coverUri?.ifBlank { null }
            ?: localAlbumTracks.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri
            ?: ""

        return AlbumDetailProfile(
            id = currentYtm?.id ?: album.id,
            title = currentYtm?.title?.ifBlank { albumTitle } ?: albumTitle,
            artist = currentYtm?.artist?.ifBlank { album.artist } ?: album.artist,
            coverArtUri = finalCover,
            releaseYear = if ((currentYtm?.releaseYear ?: 0) > 0) currentYtm!!.releaseYear else album.year,
            albumInformation = currentYtm?.albumInformation?.ifBlank { null }
                ?: if (album.year > 0) "${album.year}" else "",
            description = currentYtm?.description ?: "",
            canvasVideoUri = canvasResult?.videoUri ?: "",
            tracks = finalTracks.ensureUniqueNonBlankIds("trk", { it.id }, { it.title }, { item, id -> item.copy(id = id) }),
            otherVersions = (currentYtm?.otherVersions ?: emptyList())
                .ensureUniqueNonBlankIds("ver", { it.id }, { it.title }, { item, id -> item.copy(id = id) }),
            otherAlbums = combinedOther,
            isTracksLoading = !ytmDone,
            isCanvasLoading = !canvasDone
        )
    }

    warmCanvasIfNeeded(canvasResult)
    send(mergeSnapshot())
    if (ytmDone && canvasDone) return@channelFlow

    val mergeMutex = Mutex()
    val jobs = mutableListOf<Job>()

    if (!canvasDone) {
        jobs += launch(Dispatchers.IO) {
            val fetchedCanvas = try {
                appleMusicCanvasService.getAlbumCanvasArtwork(
                    album = albumTitle,
                    artist = artistName,
                    priority = true
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                null
            }
            warmCanvasIfNeeded(fetchedCanvas)
            mergeMutex.withLock {
                canvasResult = fetchedCanvas
                canvasDone = true
                send(mergeSnapshot())
            }
        }
    }

    if (!ytmDone) {
        jobs += launch(Dispatchers.IO) {
            val fetchedYtm = try {
                ytmService.getAlbumDetail(
                    albumId = album.id,
                    albumTitle = albumTitle,
                    artistName = artistName,
                    fallbackCover = album.coverUri
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                null
            }
            mergeMutex.withLock {
                ytmDetail = fetchedYtm
                ytmDone = true
                send(mergeSnapshot())
            }
        }
    }

    jobs.joinAll()
}.flowOn(Dispatchers.IO)

internal fun selectSeedAlbumsForMoreLike(
    likedTracks: List<TrackItem>,
    recentlyPlayed: List<TrackItem>,
    localAlbums: List<AlbumItem>,
    ytmAlbums: List<AlbumItem>,
    targetCount: Int = 3
): List<AlbumItem> {
    val selected = LinkedHashMap<String, AlbumItem>()

    fun isValidSeedAlbum(title: String, artist: String): Boolean {
        val t = title.trim()
        val a = artist.trim()
        if (t.isBlank() || t.equals("Unknown Album", ignoreCase = true) || t.equals("YouTube Music", ignoreCase = true)) {
            return false
        }
        return a.isNotBlank() && !a.equals("Unknown Artist", ignoreCase = true) && !a.equals("YouTube Artist", ignoreCase = true)
    }

    fun addTrackAlbums(tracks: List<TrackItem>, prefix: String) {
        for (track in tracks) {
            if (selected.size >= targetCount) return
            val albumTitle = track.album.trim()
            val artistName = track.artist.trim()
            if (!isValidSeedAlbum(albumTitle, artistName)) continue
            val key = albumTitle.lowercase(Locale.ROOT)
            if (!selected.containsKey(key)) {
                selected[key] = AlbumItem(
                    id = "${prefix}_${key.hashCode()}",
                    title = albumTitle,
                    artist = artistName,
                    coverUri = track.artworkUri
                )
            }
        }
    }

    fun addAlbums(albums: List<AlbumItem>) {
        for (album in albums) {
            if (selected.size >= targetCount) return
            if (!isValidSeedAlbum(album.title, album.artist)) continue
            val key = album.title.trim().lowercase(Locale.ROOT)
            selected.putIfAbsent(key, album)
        }
    }

    // Priority: Liked -> Recently Played -> Local Library -> YTM Albums -> Curated Fallbacks
    addTrackAlbums(likedTracks, "seed_liked")
    addTrackAlbums(recentlyPlayed, "seed_recent")
    addAlbums(localAlbums)
    addAlbums(ytmAlbums)

    if (selected.size < targetCount) {
        val fallbackSeeds = listOf(
            AlbumItem(id = "seed_fallback_after_hours", title = "After Hours", artist = "The Weeknd", year = 2020),
            AlbumItem(id = "seed_fallback_ram", title = "Random Access Memories", artist = "Daft Punk", year = 2013),
            AlbumItem(id = "seed_fallback_currents", title = "Currents", artist = "Tame Impala", year = 2015)
        )
        addAlbums(fallbackSeeds)
    }

    return selected.values.take(targetCount)
}

internal fun buildMoreLikeAlbumSections(
    seeds: List<AlbumItem>,
    candidatesBySeedId: Map<String, List<AlbumItem>>,
    maxSections: Int = 3,
    maxAlbumsPerSection: Int = 15
): List<MoreLikeAlbumSectionData> {
    val sections = mutableListOf<MoreLikeAlbumSectionData>()
    val seenSeedTitles = mutableSetOf<String>()

    for (seed in seeds) {
        if (sections.size >= maxSections) break
        val normalizedSeedTitle = seed.title.trim().lowercase(Locale.ROOT)
        if (normalizedSeedTitle.isBlank() || !seenSeedTitles.add(normalizedSeedTitle)) continue

        val rawCandidates = candidatesBySeedId[seed.id].orEmpty()
        val distinctAlbums = LinkedHashMap<String, AlbumItem>()
        for (candidate in rawCandidates) {
            if (distinctAlbums.size >= maxAlbumsPerSection) break
            val normCand = candidate.title.trim().lowercase(Locale.ROOT)
            if (normCand.isBlank() || normCand == normalizedSeedTitle) continue
            val key = "$normCand::${candidate.artist.trim().lowercase(Locale.ROOT)}"
            distinctAlbums.putIfAbsent(key, candidate)
        }

        if (distinctAlbums.isNotEmpty()) {
            sections.add(
                MoreLikeAlbumSectionData(
                    id = "more_like_${seed.id}",
                    seedAlbumTitle = seed.title,
                    sectionTitle = "More like: ${seed.title}",
                    albums = distinctAlbums.values.take(maxAlbumsPerSection)
                )
            )
        }
    }
    return sections
}

internal fun buildHomeTopPicks(
    homeCandidates: List<TopPickItem>,
    radioStations: List<RadioStationItem>,
    newReleases: List<NewReleaseItem>,
    playlists: List<RecommendedPlaylistItem>,
    relatedArtists: List<ArtistItem>,
    candidateTracks: List<TrackItem>,
    isPersonalized: Boolean,
    maxItems: Int = 8
): List<TopPickItem> {
    val result = LinkedHashMap<String, TopPickItem>()

    // 1. Personal station hero card
    val firstStationPick = homeCandidates.firstOrNull { it.kind == TopPickKind.PERSONAL_STATION }
        ?: radioStations.firstOrNull()?.let { radio ->
            TopPickItem(
                id = "tp_radio_${radio.playlistId}",
                eyebrow = if (isPersonalized) "PERSONAL STATION" else "FEATURED RADIO",
                title = radio.title,
                subtitle = radio.subtitle,
                artworkUri = radio.artworkUri,
                kind = TopPickKind.PERSONAL_STATION,
                playlistId = radio.playlistId,
                videoId = radio.seedVideoId
            )
        }
        ?: candidateTracks.firstOrNull()?.let { track ->
            val vid = track.id.removePrefix("ytm_")
            TopPickItem(
                id = "tp_track_${track.id}",
                eyebrow = if (isPersonalized) "PICKED FOR YOU" else "TRENDING NOW",
                title = track.title,
                subtitle = "${track.artist} • ${track.album}",
                artworkUri = track.artworkUri,
                kind = TopPickKind.PERSONAL_STATION,
                playlistId = if (track.id.startsWith("ytm_")) "RDAMVM$vid" else null,
                videoId = if (track.id.startsWith("ytm_")) vid else null,
                artistName = track.artist,
                seedTrack = track
            )
        }
    firstStationPick?.let { result.putIfAbsent(it.id, it) }

    // 2. Featured new release card
    val releasePick = homeCandidates.firstOrNull { it.kind == TopPickKind.NEW_RELEASE }
        ?: newReleases.firstOrNull()?.let { nr ->
            TopPickItem(
                id = "tp_nr_${nr.id}",
                eyebrow = "NEW RELEASE",
                title = nr.title,
                subtitle = "${nr.artist} • ${nr.subtitle}",
                artworkUri = nr.artworkUri,
                kind = TopPickKind.NEW_RELEASE,
                browseId = nr.browseId,
                videoId = nr.videoId,
                artistName = nr.artist,
                albumTitle = if (nr.isAlbum) nr.title else null
            )
        }
    releasePick?.let { result.putIfAbsent(it.id, it) }

    // 3. Featured playlist card
    val playlistPick = homeCandidates.firstOrNull { it.kind == TopPickKind.FEATURED_PLAYLIST }
        ?: playlists.firstOrNull()?.let { pl ->
            TopPickItem(
                id = "tp_pl_${pl.playlistId}",
                eyebrow = "PLAYLIST FOR YOU",
                title = pl.title,
                subtitle = pl.subtitle,
                artworkUri = pl.artworkUri,
                kind = TopPickKind.FEATURED_PLAYLIST,
                browseId = pl.browseId,
                playlistId = pl.playlistId
            )
        }
    playlistPick?.let { result.putIfAbsent(it.id, it) }

    // 4. Artist spotlight card
    relatedArtists.firstOrNull()?.let { artist ->
        val spotlight = TopPickItem(
            id = "tp_artist_${artist.name.hashCode()}",
            eyebrow = "ARTIST SPOTLIGHT",
            title = artist.name,
            subtitle = artist.genre.ifBlank { "Explore essentials & top tracks" },
            artworkUri = artist.avatarUri,
            kind = TopPickKind.ARTIST_SPOTLIGHT,
            browseId = artist.id.removePrefix("ytm_sim_").takeIf { it.startsWith("UC") },
            artistName = artist.name
        )
        result.putIfAbsent(spotlight.id, spotlight)
    }

    // 5. Fill remaining slots up to maxItems
    (homeCandidates + playlists.drop(1).map { pl ->
        TopPickItem(
            id = "tp_pl_${pl.playlistId}",
            eyebrow = "MIXED FOR YOU",
            title = pl.title,
            subtitle = pl.subtitle,
            artworkUri = pl.artworkUri,
            kind = TopPickKind.FEATURED_PLAYLIST,
            browseId = pl.browseId,
            playlistId = pl.playlistId
        )
    } + newReleases.drop(1).map { nr ->
        TopPickItem(
            id = "tp_nr_${nr.id}",
            eyebrow = "NEW RELEASE",
            title = nr.title,
            subtitle = "${nr.artist} • ${nr.subtitle}",
            artworkUri = nr.artworkUri,
            kind = TopPickKind.NEW_RELEASE,
            browseId = nr.browseId,
            videoId = nr.videoId,
            artistName = nr.artist,
            albumTitle = if (nr.isAlbum) nr.title else null
        )
    }).forEach { pick ->
        if (result.size >= maxItems) return@forEach
        result.putIfAbsent(pick.id, pick)
    }

    return result.values.take(maxItems)
}

/**
 * Persists first-seen / saved timestamps (`dateAddedMs`) for online tracks in SharedPreferences
 * while always preserving genuine local device MediaStore / SAF timestamps when present.
 */
class TrackAddedAtStore internal constructor(
    private val prefs: android.content.SharedPreferences?,
    private val backingMap: MutableMap<String, Long>?,
    private val clockMs: () -> Long
) {
    constructor(
        context: android.content.Context,
        clockMs: () -> Long = { System.currentTimeMillis() }
    ) : this(
        prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE),
        backingMap = null,
        clockMs = clockMs
    )

    constructor(
        prefs: android.content.SharedPreferences,
        clockMs: () -> Long = { System.currentTimeMillis() }
    ) : this(
        prefs = prefs,
        backingMap = null,
        clockMs = clockMs
    )

    internal constructor(
        backingMap: MutableMap<String, Long>,
        clockMs: () -> Long = { System.currentTimeMillis() }
    ) : this(
        prefs = null,
        backingMap = backingMap,
        clockMs = clockMs
    )

    private val lock = Any()
    private val cache: MutableMap<String, Long> = loadInitialTimestamps()

    private fun loadInitialTimestamps(): MutableMap<String, Long> {
        val loaded = LinkedHashMap<String, Long>()
        prefs?.all?.forEach { (key, value) ->
            val ts = (value as? Number)?.toLong() ?: return@forEach
            if (key.isNotBlank() && ts > 0L) {
                loaded[key] = ts
            }
        }
        backingMap?.forEach { (key, ts) ->
            if (key.isNotBlank() && ts > 0L) {
                loaded[key] = ts
            }
        }
        return loaded
    }

    fun getTimestamp(trackId: String): Long? {
        val key = trackId.trim()
        if (key.isBlank()) return null
        synchronized(lock) {
            return cache[key]?.takeIf { it > 0L }
        }
    }

    fun stampTrack(track: TrackItem, fallbackNowMs: Long = clockMs()): TrackItem {
        if (track.dateAddedMs > 0L) return track
        val key = track.id.trim()
        val effectiveFallback = fallbackNowMs.coerceAtLeast(1L)
        if (key.isBlank()) return track.copy(dateAddedMs = effectiveFallback)

        val resolvedMs = synchronized(lock) {
            val existing = cache[key]
            if (existing != null && existing > 0L) {
                existing
            } else {
                cache[key] = effectiveFallback
                backingMap?.put(key, effectiveFallback)
                prefs?.edit()?.putLong(key, effectiveFallback)?.apply()
                effectiveFallback
            }
        }
        return track.copy(dateAddedMs = resolvedMs)
    }

    fun stampTracks(tracks: List<TrackItem>, batchNowMs: Long = clockMs()): List<TrackItem> {
        if (tracks.isEmpty()) return emptyList()
        val safeNow = batchNowMs.coerceAtLeast(1L)
        val newlyRecorded = mutableListOf<Pair<String, Long>>()

        val stamped = synchronized(lock) {
            tracks.mapIndexed { index, track ->
                if (track.dateAddedMs > 0L) {
                    track
                } else {
                    val key = track.id.trim()
                    if (key.isBlank()) {
                        track.copy(dateAddedMs = (safeNow - index).coerceAtLeast(1L))
                    } else {
                        val existing = cache[key]
                        if (existing != null && existing > 0L) {
                            track.copy(dateAddedMs = existing)
                        } else {
                            val assigned = (safeNow - index).coerceAtLeast(1L)
                            cache[key] = assigned
                            backingMap?.put(key, assigned)
                            newlyRecorded.add(key to assigned)
                            track.copy(dateAddedMs = assigned)
                        }
                    }
                }
            }
        }

        if (newlyRecorded.isNotEmpty() && prefs != null) {
            val editor = prefs.edit()
            newlyRecorded.forEach { (key, value) ->
                editor.putLong(key, value)
            }
            editor.apply()
        }

        return stamped
    }

    companion object {
        const val PREFS_NAME = "xperience_added_timestamps_prefs"
    }
}

/**
 * Deduplicates library tracks by ID (preferring entries with valid `dateAddedMs`)
 * and returns them sorted descending from newest to oldest.
 */
internal fun sortRecentlyAddedLibraryTracks(tracks: List<TrackItem>): List<TrackItem> {
    if (tracks.isEmpty()) return emptyList()
    val deduplicated = LinkedHashMap<String, TrackItem>(tracks.size)
    tracks.forEachIndexed { index, track ->
        val key = track.id.takeIf { it.isNotBlank() } ?: "__anon_$index"
        val existing = deduplicated[key]
        if (existing == null || track.dateAddedMs > existing.dateAddedMs) {
            deduplicated[key] = track
        }
    }
    return deduplicated.values.sortedWith(
        compareByDescending<TrackItem> { it.dateAddedMs }
            .thenBy { it.title.lowercase() }
            .thenBy { it.id }
    )
}

/**
 * Normalizes any blank item IDs to deterministic non-blank fallback IDs and deduplicates by ID
 * so Compose Lazy lists never receive duplicate or empty keys.
 */
internal inline fun <T> List<T>.ensureUniqueNonBlankIds(
    prefix: String,
    crossinline getId: (T) -> String,
    crossinline getLabel: (T) -> String,
    crossinline withId: (T, String) -> T
): List<T> = map { item ->
    val rawId = getId(item)
    if (rawId.isNotBlank()) {
        item
    } else {
        val normalizedLabel = getLabel(item).trim().lowercase()
        withId(item, "gen_${prefix}_${normalizedLabel.hashCode()}")
    }
}.distinctBy { getId(it) }
