package com.xperience.player.widget

import android.content.Context
import android.os.SystemClock
import com.xperience.player.data.FavoritesRepository
import com.xperience.player.data.PlaylistRepository
import com.xperience.player.domain.model.PlaybackState
import com.xperience.player.domain.model.RadioStationItem
import com.xperience.player.domain.model.RecommendedPlaylistItem
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.domain.model.UserPlaylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Primary integration bridge between app state (`PlaybackService`, `AudioPlayerService`,
 * `HomeViewModel`) and Xperience home-screen widgets.
 *
 * Safe to call frequently (including from 30 Hz `playbackState` collectors): internal
 * change-detection and throttling automatically skip redundant disk writes and Binder IPC.
 */
object WidgetSyncBridge {

    private val syncLock = Any()

    @Volatile private var lastPlaybackSnapshot: WidgetPlaybackSnapshot? = null
    @Volatile private var lastPlaybackRenderElapsedMs: Long = 0L
    @Volatile private var lastRecommendationsSignature: String? = null

    /**
     * Optional in-process hook invoked when the user toggles Favorite from a home-screen widget
     * while the app UI is alive, keeping `HomeViewModel` in immediate sync.
     * Return the new `isLiked` state if handled, or `null` to fall back to [FavoritesRepository].
     */
    @Volatile
    var onFavoriteToggleRequested: ((trackId: String, fallbackTrack: TrackItem?) -> Boolean?)? = null

    /**
     * Optional in-process hook invoked when the user taps Refresh on a recommendations widget
     * while the app is alive (e.g. `homeViewModel.refreshHomeFeed()`).
     */
    @Volatile
    var onRefreshRecommendationsRequested: (() -> Unit)? = null

    /**
     * Syncs live [PlaybackState] and [isLiked] status to persisted widget storage and active widgets.
     */
    fun syncPlayback(
        context: Context,
        playbackState: PlaybackState,
        isLiked: Boolean,
        accentColorArgb: Int? = null
    ) {
        syncPlaybackInternal(
            context = context,
            playbackState = playbackState,
            isLiked = isLiked,
            accentColorArgb = accentColorArgb,
            triggerAsyncRender = true
        )
    }

    internal fun syncPlaybackInternal(
        context: Context,
        playbackState: PlaybackState,
        isLiked: Boolean,
        accentColorArgb: Int? = null,
        triggerAsyncRender: Boolean = true
    ) {
        val appContext = context.applicationContext
        val track = playbackState.currentTrack
        val nowWallMs = System.currentTimeMillis()
        val nowElapsedMs = SystemClock.elapsedRealtime()

        val candidate = if (track != null) {
            WidgetPlaybackSnapshot(
                trackId = track.id,
                title = track.title.trim(),
                artist = track.artist.trim(),
                album = track.album.trim(),
                artworkUri = track.artworkUri?.trim()?.ifBlank { null },
                artworkResId = track.artworkResId?.takeIf { it != 0 },
                audioUrl = track.audioUrl?.trim()?.ifBlank { null },
                isPlaying = playbackState.isPlaying,
                isLoading = playbackState.isLoading,
                isLiked = isLiked,
                positionMs = playbackState.currentPositionMs.coerceAtLeast(0L),
                durationMs = (playbackState.durationMs.takeIf { it > 0L } ?: track.durationMs).coerceAtLeast(0L),
                queueSize = playbackState.queue.size,
                accentColorArgb = accentColorArgb,
                updatedAtMs = nowWallMs
            )
        } else {
            WidgetPlaybackSnapshot(
                isPlaying = false,
                isLoading = false,
                isLiked = false,
                updatedAtMs = nowWallMs
            )
        }

        val decision: WidgetSyncThrottlePolicy.SyncDecision
        synchronized(syncLock) {
            // Fast-path: evaluate throttle policy before querying AppWidgetManager Binder IPC
            val optimistic = WidgetSyncThrottlePolicy.evaluate(
                previous = lastPlaybackSnapshot,
                current = candidate,
                lastRenderAtMs = lastPlaybackRenderElapsedMs,
                nowMs = nowElapsedMs,
                hasActiveWidgets = true
            )
            if (optimistic == WidgetSyncThrottlePolicy.SyncDecision.SKIP) {
                return
            }
            val hasActiveWidgets = WidgetUpdater.hasAnyActiveWidgets(appContext)
            decision = if (hasActiveWidgets) {
                optimistic
            } else {
                WidgetSyncThrottlePolicy.evaluate(
                    previous = lastPlaybackSnapshot,
                    current = candidate,
                    lastRenderAtMs = lastPlaybackRenderElapsedMs,
                    nowMs = nowElapsedMs,
                    hasActiveWidgets = false
                )
            }
            if (decision != WidgetSyncThrottlePolicy.SyncDecision.SKIP) {
                lastPlaybackSnapshot = candidate
                if (decision == WidgetSyncThrottlePolicy.SyncDecision.PERSIST_AND_RENDER) {
                    lastPlaybackRenderElapsedMs = nowElapsedMs
                }
            }
        }

        if (decision == WidgetSyncThrottlePolicy.SyncDecision.SKIP) return

        WidgetStateStore(appContext).savePlayback(candidate)
        if (triggerAsyncRender && decision == WidgetSyncThrottlePolicy.SyncDecision.PERSIST_AND_RENDER) {
            WidgetUpdater.requestUpdatePlaybackWidgets(appContext)
        }
    }

    /**
     * Curates and syncs recommended playlists, smart mixes, user playlists, radio stations,
     * and Liked Songs into persisted widget storage and active playlist/hub widgets.
     */
    fun syncRecommendations(
        context: Context,
        userPlaylists: List<UserPlaylist>,
        smartPlaylists: List<UserPlaylist>,
        madeForYou: List<RecommendedPlaylistItem>,
        radioStations: List<RadioStationItem>,
        likedCount: Int,
        likedArtworkUri: String? = null,
        recentlyPlayedTracks: List<TrackItem> = emptyList()
    ) {
        val appContext = context.applicationContext
        val curated = WidgetRecommendationCurator.curate(
            userPlaylists = userPlaylists,
            smartPlaylists = smartPlaylists,
            madeForYou = madeForYou,
            radioStations = radioStations,
            likedCount = likedCount,
            likedArtworkUri = likedArtworkUri,
            recentlyPlayedTracks = recentlyPlayedTracks
        )
        val signature = WidgetRecommendationCurator.buildSignature(curated)

        val changed: Boolean
        synchronized(syncLock) {
            changed = signature != lastRecommendationsSignature
            if (changed) {
                lastRecommendationsSignature = signature
            }
        }
        if (!changed) return

        WidgetStateStore(appContext).saveRecommendations(curated)
        if (WidgetUpdater.hasAnyActiveWidgets(appContext)) {
            WidgetUpdater.requestUpdateRecommendationWidgets(appContext)
        }
    }

    /**
     * Updates only the persisted/cached favorite state for [trackId] and optionally refreshes active widgets.
     */
    internal fun notifyFavoriteChanged(
        context: Context,
        trackId: String,
        isLiked: Boolean,
        triggerAsyncRender: Boolean = true
    ) {
        val appContext = context.applicationContext
        val updated = WidgetStateStore(appContext).updateLikedStatus(trackId, isLiked)
        synchronized(syncLock) {
            lastPlaybackSnapshot = updated
            lastPlaybackRenderElapsedMs = SystemClock.elapsedRealtime()
        }
        if (triggerAsyncRender && WidgetUpdater.hasAnyActiveWidgets(appContext)) {
            WidgetUpdater.requestUpdatePlaybackWidgets(appContext)
        }
    }

    /**
     * Cold-start hydration: if no widget recommendations have been synced yet, lazily seeds
     * them from persisted on-disk [PlaylistRepository] and [FavoritesRepository] so newly placed
     * widgets immediately show real user playlists and Liked Songs even before opening the app.
     */
    internal suspend fun hydrateRecommendationsFromLocalDiskIfEmpty(
        context: Context
    ): List<WidgetPlaylistItem> = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val store = WidgetStateStore(appContext)
        val existing = store.readRecommendations()
        if (existing.isNotEmpty()) return@withContext existing

        val userPlaylists = runCatching {
            PlaylistRepository(appContext).apply { loadPlaylistsFromDisk() }.playlists.value
        }.getOrDefault(emptyList())
        val likedTracks = runCatching {
            val repo = FavoritesRepository.getInstance(appContext)
            if (repo.likedTracks.value.isEmpty()) repo.loadFavoritesFromDisk()
            repo.likedTracks.value
        }.getOrDefault(emptyList())

        val curated = WidgetRecommendationCurator.curate(
            userPlaylists = userPlaylists,
            smartPlaylists = emptyList(),
            madeForYou = emptyList(),
            radioStations = emptyList(),
            likedCount = likedTracks.size,
            likedArtworkUri = likedTracks.firstNotNullOfOrNull { it.artworkUri }
        )
        if (curated.isNotEmpty()) {
            store.saveRecommendations(curated)
            synchronized(syncLock) {
                lastRecommendationsSignature = WidgetRecommendationCurator.buildSignature(curated)
            }
        }
        curated
    }

    /**
     * Forces an immediate refresh of all active home-screen widgets from persisted storage.
     */
    fun refreshAllWidgets(context: Context) {
        WidgetUpdater.requestUpdateAllWidgets(context.applicationContext)
    }
}
