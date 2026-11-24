package com.xperience.player.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.xperience.player.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Pure JVM-testable throttling policy preventing 30 Hz playback progress ticks from flooding
 * Binder IPC while pushing structural playback transitions (track change, play/pause, like,
 * seek jumps) with zero latency.
 */
object WidgetSyncThrottlePolicy {

    const val PROGRESS_PUSH_INTERVAL_MS: Long = 5_000L
    const val SEEK_JUMP_THRESHOLD_MS: Long = 3_000L
    const val DURATION_DELTA_THRESHOLD_MS: Long = 1_000L

    enum class SyncDecision {
        SKIP,
        PERSIST_ONLY,
        PERSIST_AND_RENDER
    }

    fun evaluate(
        previous: WidgetPlaybackSnapshot?,
        current: WidgetPlaybackSnapshot,
        lastRenderAtMs: Long,
        nowMs: Long,
        hasActiveWidgets: Boolean
    ): SyncDecision {
        if (previous == null) {
            return if (hasActiveWidgets) SyncDecision.PERSIST_AND_RENDER else SyncDecision.PERSIST_ONLY
        }

        val structuralChange =
            previous.trackId != current.trackId ||
                previous.title != current.title ||
                previous.artist != current.artist ||
                previous.album != current.album ||
                previous.artworkUri != current.artworkUri ||
                previous.artworkResId != current.artworkResId ||
                previous.isPlaying != current.isPlaying ||
                previous.isLiked != current.isLiked ||
                previous.accentColorArgb != current.accentColorArgb ||
                abs(previous.safeDurationMs - current.safeDurationMs) > DURATION_DELTA_THRESHOLD_MS

        if (structuralChange) {
            return if (hasActiveWidgets) SyncDecision.PERSIST_AND_RENDER else SyncDecision.PERSIST_ONLY
        }

        val elapsedSinceRender = (nowMs - lastRenderAtMs).coerceAtLeast(0L)
        val expectedPosition = if (previous.isPlaying) {
            previous.safePositionMs + elapsedSinceRender
        } else {
            previous.safePositionMs
        }
        val seekJumped = abs(current.safePositionMs - expectedPosition) >= SEEK_JUMP_THRESHOLD_MS

        if (seekJumped) {
            return if (hasActiveWidgets) SyncDecision.PERSIST_AND_RENDER else SyncDecision.PERSIST_ONLY
        }

        if (!hasActiveWidgets) {
            return SyncDecision.SKIP
        }

        val periodicTickDue = current.isPlaying &&
            elapsedSinceRender >= PROGRESS_PUSH_INTERVAL_MS &&
            current.progressPermille != previous.progressPermille

        return if (periodicTickDue) SyncDecision.PERSIST_AND_RENDER else SyncDecision.SKIP
    }
}

/**
 * Coordinates responsive API 33+ Material You `RemoteViews(Map<SizeF, RemoteViews>)` rendering
 * across all three Xperience home-screen widgets with coalesced async artwork loading.
 */
object WidgetUpdater {

    private const val TAG = "WidgetUpdater"

    private const val MINI_PLAYER_SLOT_COUNT_IN_PAYLOAD = 3
    private const val PLAYLISTS_SLOT_COUNT_IN_PAYLOAD = 6
    private const val MUSIC_HUB_SLOT_COUNT_IN_PAYLOAD = 8

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var miniPlayerJob: Job? = null
    @Volatile private var playlistsJob: Job? = null
    @Volatile private var musicHubJob: Job? = null

    fun hasAnyActiveWidgets(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context.applicationContext)
        return getWidgetIds(context, manager, MiniPlayerWidgetProvider::class.java).isNotEmpty() ||
            getWidgetIds(context, manager, PlaylistRecommendationsWidgetProvider::class.java).isNotEmpty() ||
            getWidgetIds(context, manager, MusicHubWidgetProvider::class.java).isNotEmpty()
    }

    fun requestUpdatePlaybackWidgets(context: Context) {
        val appContext = context.applicationContext
        launchCoalescedMiniPlayer(appContext)
        launchCoalescedMusicHub(appContext)
    }

    fun requestUpdateRecommendationWidgets(context: Context) {
        val appContext = context.applicationContext
        launchCoalescedPlaylists(appContext)
        launchCoalescedMusicHub(appContext)
    }

    fun requestUpdateAllWidgets(context: Context) {
        val appContext = context.applicationContext
        launchCoalescedMiniPlayer(appContext)
        launchCoalescedPlaylists(appContext)
        launchCoalescedMusicHub(appContext)
    }

    /**
     * Suspends until the Mini Player widgets finish updating (used by `AppWidgetProvider.onUpdate`).
     */
    suspend fun updateMiniPlayerWidgetsNow(
        context: Context,
        specificIds: IntArray? = null
    ) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val ids = specificIds?.takeIf { it.isNotEmpty() }
            ?: getWidgetIds(appContext, manager, MiniPlayerWidgetProvider::class.java)
        if (ids.isEmpty()) return

        val playback = WidgetStateStore(appContext).readPlayback()
        val targetPx = WidgetArtworkSizing.computeSafeTargetPx(
            requestedPx = 160,
            bitmapSlotsInPayload = MINI_PLAYER_SLOT_COUNT_IN_PAYLOAD
        )

        val cachedArt = if (playback.hasTrack) {
            WidgetArtworkLoader.getCached(playback.artworkUri, playback.artworkResId, targetPx)
        } else {
            null
        }

        // Phase 1: push immediately with cached art or crisp placeholder so button taps feel instant.
        val initialViews = buildMiniPlayerResponsiveViews(appContext, playback, cachedArt)
        safePush(manager, ids, initialViews)

        // Phase 2: fetch artwork asynchronously if not yet cached.
        if (playback.hasTrack && cachedArt == null &&
            (!playback.artworkUri.isNullOrBlank() || playback.artworkResId != null)
        ) {
            val loadedArt = WidgetArtworkLoader.loadRoundedArtwork(
                context = appContext,
                artworkUri = playback.artworkUri,
                artworkResId = playback.artworkResId,
                targetPx = targetPx
            )
            if (loadedArt != null) {
                val updatedViews = buildMiniPlayerResponsiveViews(appContext, playback, loadedArt)
                safePush(manager, ids, updatedViews)
            }
        }
    }

    /**
     * Suspends until the Playlist Recommendations widgets finish updating.
     */
    suspend fun updatePlaylistWidgetsNow(
        context: Context,
        specificIds: IntArray? = null
    ) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val ids = specificIds?.takeIf { it.isNotEmpty() }
            ?: getWidgetIds(appContext, manager, PlaylistRecommendationsWidgetProvider::class.java)
        if (ids.isEmpty()) return

        val state = WidgetStateStore(appContext).readState()
        val visibleItems = WidgetRecommendationCurator.selectWindow(
            items = state.recommendations,
            offset = state.rotationOffset,
            count = 4
        )
        val targetPx = WidgetArtworkSizing.computeSafeTargetPx(
            requestedPx = 112,
            bitmapSlotsInPayload = PLAYLISTS_SLOT_COUNT_IN_PAYLOAD
        )

        val cachedArt = visibleItems.map { item ->
            WidgetArtworkLoader.getCached(item.artworkUri, null, targetPx)
        }
        safePush(manager, ids, buildPlaylistsResponsiveViews(appContext, visibleItems, cachedArt))

        val needsFetch = visibleItems.indices.any { idx ->
            cachedArt[idx] == null && !visibleItems[idx].artworkUri.isNullOrBlank()
        }
        if (needsFetch) {
            val loadedArt = loadPlaylistArtworkBatch(appContext, visibleItems, cachedArt, targetPx)
            safePush(manager, ids, buildPlaylistsResponsiveViews(appContext, visibleItems, loadedArt))
        }
    }

    /**
     * Suspends until the unified Music Hub widgets finish updating.
     */
    suspend fun updateMusicHubWidgetsNow(
        context: Context,
        specificIds: IntArray? = null
    ) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val ids = specificIds?.takeIf { it.isNotEmpty() }
            ?: getWidgetIds(appContext, manager, MusicHubWidgetProvider::class.java)
        if (ids.isEmpty()) return

        val state = WidgetStateStore(appContext).readState()
        val playback = state.playback
        val visibleItems = WidgetRecommendationCurator.selectWindow(
            items = state.recommendations,
            offset = state.rotationOffset,
            count = 4
        )
        val targetPx = WidgetArtworkSizing.computeSafeTargetPx(
            requestedPx = 112,
            bitmapSlotsInPayload = MUSIC_HUB_SLOT_COUNT_IN_PAYLOAD
        )

        val cachedTrackArt = if (playback.hasTrack) {
            WidgetArtworkLoader.getCached(playback.artworkUri, playback.artworkResId, targetPx)
        } else {
            null
        }
        val cachedPlaylistArt = visibleItems.map { item ->
            WidgetArtworkLoader.getCached(item.artworkUri, null, targetPx)
        }

        safePush(
            manager,
            ids,
            buildMusicHubResponsiveViews(
                context = appContext,
                playback = playback,
                trackArtwork = cachedTrackArt,
                playlists = visibleItems,
                playlistArtworks = cachedPlaylistArt
            )
        )

        val trackNeedsFetch = playback.hasTrack && cachedTrackArt == null &&
            (!playback.artworkUri.isNullOrBlank() || playback.artworkResId != null)
        val playlistsNeedFetch = visibleItems.indices.any { idx ->
            cachedPlaylistArt[idx] == null && !visibleItems[idx].artworkUri.isNullOrBlank()
        }

        if (trackNeedsFetch || playlistsNeedFetch) {
            val (loadedTrackArt, loadedPlaylistArt) = coroutineScope {
                val trackDeferred = async {
                    if (trackNeedsFetch) {
                        WidgetArtworkLoader.loadRoundedArtwork(
                            context = appContext,
                            artworkUri = playback.artworkUri,
                            artworkResId = playback.artworkResId,
                            targetPx = targetPx
                        )
                    } else {
                        cachedTrackArt
                    }
                }
                val playlistsDeferred = async {
                    if (playlistsNeedFetch) {
                        loadPlaylistArtworkBatch(appContext, visibleItems, cachedPlaylistArt, targetPx)
                    } else {
                        cachedPlaylistArt
                    }
                }
                trackDeferred.await() to playlistsDeferred.await()
            }

            safePush(
                manager,
                ids,
                buildMusicHubResponsiveViews(
                    context = appContext,
                    playback = playback,
                    trackArtwork = loadedTrackArt,
                    playlists = visibleItems,
                    playlistArtworks = loadedPlaylistArt
                )
            )
        }
    }

    private fun launchCoalescedMiniPlayer(appContext: Context) {
        miniPlayerJob?.cancel()
        miniPlayerJob = scope.launch {
            try {
                updateMiniPlayerWidgetsNow(appContext)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.w(TAG, "MiniPlayer widget update error: ${e.message}")
            }
        }
    }

    private fun launchCoalescedPlaylists(appContext: Context) {
        playlistsJob?.cancel()
        playlistsJob = scope.launch {
            try {
                updatePlaylistWidgetsNow(appContext)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.w(TAG, "Playlists widget update error: ${e.message}")
            }
        }
    }

    private fun launchCoalescedMusicHub(appContext: Context) {
        musicHubJob?.cancel()
        musicHubJob = scope.launch {
            try {
                updateMusicHubWidgetsNow(appContext)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.w(TAG, "MusicHub widget update error: ${e.message}")
            }
        }
    }

    private suspend fun loadPlaylistArtworkBatch(
        context: Context,
        items: List<WidgetPlaylistItem>,
        existing: List<Bitmap?>,
        targetPx: Int
    ): List<Bitmap?> = coroutineScope {
        items.mapIndexed { idx, item ->
            async {
                existing.getOrNull(idx) ?: item.artworkUri?.let { uri ->
                    WidgetArtworkLoader.loadRoundedArtwork(
                        context = context,
                        artworkUri = uri,
                        targetPx = targetPx
                    )
                }
            }
        }.awaitAll()
    }

    private fun buildMiniPlayerResponsiveViews(
        context: Context,
        playback: WidgetPlaybackSnapshot,
        artwork: Bitmap?
    ): RemoteViews {
        val tokens = WidgetPalette.resolve(context, playback.accentColorArgb)

        val compact = RemoteViews(context.packageName, R.layout.widget_mini_player_compact).also { rv ->
            bindMiniPlayerSurface(
                context = context,
                rv = rv,
                playback = playback,
                artwork = artwork,
                tokens = tokens,
                hasHeader = false,
                hasFavorite = false,
                hasProgress = false,
                hasTimestamps = false,
                hasAlbum = false,
                hasAmbientShortcut = false,
                usesTonalTransportButtons = false
            )
        }

        val medium = RemoteViews(context.packageName, R.layout.widget_mini_player_medium).also { rv ->
            bindMiniPlayerSurface(
                context = context,
                rv = rv,
                playback = playback,
                artwork = artwork,
                tokens = tokens,
                hasHeader = true,
                hasFavorite = true,
                hasProgress = true,
                hasTimestamps = true,
                hasAlbum = false,
                hasAmbientShortcut = false,
                usesTonalTransportButtons = true
            )
        }

        val expanded = RemoteViews(context.packageName, R.layout.widget_mini_player_expanded).also { rv ->
            bindMiniPlayerSurface(
                context = context,
                rv = rv,
                playback = playback,
                artwork = artwork,
                tokens = tokens,
                hasHeader = true,
                hasFavorite = true,
                hasProgress = true,
                hasTimestamps = true,
                hasAlbum = true,
                hasAmbientShortcut = true,
                usesTonalTransportButtons = true
            )
        }

        val sizeMapping = mapOf(
            SizeF(180f, 48f) to compact,
            SizeF(180f, 108f) to medium,
            SizeF(180f, 180f) to expanded
        )
        return RemoteViews(sizeMapping)
    }

    private fun buildPlaylistsResponsiveViews(
        context: Context,
        playlists: List<WidgetPlaylistItem>,
        artworks: List<Bitmap?>
    ): RemoteViews {
        val tokens = WidgetPalette.resolve(context, artworkAccentArgb = null)

        val compact = RemoteViews(context.packageName, R.layout.widget_playlists_compact).also { rv ->
            WidgetPalette.applyBackgroundTint(rv, android.R.id.background, tokens.surfaceContainer)
            bindPlaylistHeader(context, rv, tokens, hasBrandIcon = true, hasPlaylists = playlists.isNotEmpty())
            bindPlaylistSlots(
                context = context,
                rv = rv,
                playlists = playlists,
                artworks = artworks,
                tokens = tokens,
                maxSlots = 2,
                hasSecondRow = false,
                hasEmptyIcon = true
            )
        }

        val expanded = RemoteViews(context.packageName, R.layout.widget_playlists_expanded).also { rv ->
            WidgetPalette.applyBackgroundTint(rv, android.R.id.background, tokens.surfaceContainer)
            bindPlaylistHeader(context, rv, tokens, hasBrandIcon = true, hasPlaylists = playlists.isNotEmpty())
            bindPlaylistSlots(
                context = context,
                rv = rv,
                playlists = playlists,
                artworks = artworks,
                tokens = tokens,
                maxSlots = 4,
                hasSecondRow = true,
                hasEmptyIcon = true
            )
        }

        val sizeMapping = mapOf(
            SizeF(180f, 110f) to compact,
            SizeF(180f, 180f) to expanded
        )
        return RemoteViews(sizeMapping)
    }

    private fun buildMusicHubResponsiveViews(
        context: Context,
        playback: WidgetPlaybackSnapshot,
        trackArtwork: Bitmap?,
        playlists: List<WidgetPlaylistItem>,
        playlistArtworks: List<Bitmap?>
    ): RemoteViews {
        val tokens = WidgetPalette.resolve(context, playback.accentColorArgb)

        val medium = RemoteViews(context.packageName, R.layout.widget_music_hub_medium).also { rv ->
            bindMiniPlayerSurface(
                context = context,
                rv = rv,
                playback = playback,
                artwork = trackArtwork,
                tokens = tokens,
                hasHeader = false,
                hasFavorite = true,
                hasProgress = true,
                hasTimestamps = false,
                hasAlbum = false,
                hasAmbientShortcut = false,
                usesTonalTransportButtons = false
            )
            WidgetPalette.applyBackgroundTint(rv, R.id.widget_hub_divider, tokens.divider)
            bindPlaylistHeader(context, rv, tokens, hasBrandIcon = false, hasPlaylists = playlists.isNotEmpty())
            bindPlaylistSlots(
                context = context,
                rv = rv,
                playlists = playlists,
                artworks = playlistArtworks,
                tokens = tokens,
                maxSlots = 2,
                hasSecondRow = false,
                hasEmptyIcon = false
            )
        }

        val expanded = RemoteViews(context.packageName, R.layout.widget_music_hub_expanded).also { rv ->
            bindMiniPlayerSurface(
                context = context,
                rv = rv,
                playback = playback,
                artwork = trackArtwork,
                tokens = tokens,
                hasHeader = true,
                hasFavorite = true,
                hasProgress = true,
                hasTimestamps = false,
                hasAlbum = false,
                hasAmbientShortcut = true,
                usesTonalTransportButtons = true
            )
            WidgetPalette.applyBackgroundTint(rv, R.id.widget_hub_divider, tokens.divider)
            bindPlaylistHeader(context, rv, tokens, hasBrandIcon = false, hasPlaylists = playlists.isNotEmpty())
            bindPlaylistSlots(
                context = context,
                rv = rv,
                playlists = playlists,
                artworks = playlistArtworks,
                tokens = tokens,
                maxSlots = 4,
                hasSecondRow = true,
                hasEmptyIcon = true
            )
        }

        val sizeMapping = mapOf(
            SizeF(250f, 180f) to medium,
            SizeF(250f, 250f) to expanded
        )
        return RemoteViews(sizeMapping)
    }

    private fun bindMiniPlayerSurface(
        context: Context,
        rv: RemoteViews,
        playback: WidgetPlaybackSnapshot,
        artwork: Bitmap?,
        tokens: WidgetColorTokens,
        hasHeader: Boolean,
        hasFavorite: Boolean,
        hasProgress: Boolean,
        hasTimestamps: Boolean,
        hasAlbum: Boolean,
        hasAmbientShortcut: Boolean,
        usesTonalTransportButtons: Boolean
    ) {
        WidgetPalette.applyBackgroundTint(rv, android.R.id.background, tokens.surfaceContainer)
        WidgetPalette.applyBackgroundTint(rv, R.id.widget_artwork_frame, tokens.artworkPlaceholder)
        WidgetPalette.applyImageTint(rv, R.id.widget_artwork_placeholder_icon, tokens.accentText)
        WidgetPalette.applyTextColor(rv, R.id.widget_track_title, tokens.onSurface)
        WidgetPalette.applyTextColor(rv, R.id.widget_track_artist, tokens.onSurfaceVariant)

        if (playback.hasTrack) {
            rv.setTextViewText(R.id.widget_track_title, playback.title)
            rv.setTextViewText(
                R.id.widget_track_artist,
                playback.artist.ifBlank { context.getString(R.string.widget_idle_title) }
            )
        } else {
            rv.setTextViewText(R.id.widget_track_title, context.getString(R.string.widget_idle_title))
            rv.setTextViewText(R.id.widget_track_artist, context.getString(R.string.widget_idle_subtitle))
        }

        if (artwork != null && !artwork.isRecycled && playback.hasTrack) {
            rv.setImageViewBitmap(R.id.widget_artwork, artwork)
            rv.setViewVisibility(R.id.widget_artwork, View.VISIBLE)
            rv.setViewVisibility(R.id.widget_artwork_placeholder_icon, View.GONE)
        } else {
            rv.setViewVisibility(R.id.widget_artwork, View.GONE)
            rv.setViewVisibility(R.id.widget_artwork_placeholder_icon, View.VISIBLE)
        }

        if (hasHeader) {
            WidgetPalette.applyImageTint(rv, R.id.widget_brand_icon, tokens.accentText)
            WidgetPalette.applyTextColor(rv, R.id.widget_status_label, tokens.accentText)
            val statusText = when {
                !playback.hasTrack -> context.getString(R.string.widget_idle_title)
                playback.isPlaying -> context.getString(R.string.widget_now_playing_eyebrow)
                else -> context.getString(R.string.widget_paused_eyebrow)
            }
            rv.setTextViewText(R.id.widget_status_label, statusText)
        }

        if (hasAlbum) {
            WidgetPalette.applyTextColor(rv, R.id.widget_track_album, tokens.onSurfaceVariant)
            if (playback.hasTrack && playback.album.isNotBlank()) {
                rv.setTextViewText(R.id.widget_track_album, playback.album)
                rv.setViewVisibility(R.id.widget_track_album, View.VISIBLE)
            } else {
                rv.setViewVisibility(R.id.widget_track_album, View.GONE)
            }
        }

        if (hasAmbientShortcut) {
            WidgetPalette.applyBackgroundTint(rv, R.id.widget_btn_ambient, tokens.secondaryContainer)
            WidgetPalette.applyImageTint(rv, R.id.widget_btn_ambient_icon, tokens.onSecondaryContainer)
            WidgetPalette.applyTextColor(rv, R.id.widget_btn_ambient_label, tokens.onSecondaryContainer)
            rv.setOnClickPendingIntent(
                R.id.widget_btn_ambient,
                WidgetActions.openAmbientPendingIntent(context)
            )
        }

        if (hasFavorite) {
            WidgetPalette.applyBackgroundTint(rv, R.id.widget_btn_favorite, tokens.iconButtonSurface)
            val isFav = playback.hasTrack && playback.isLiked
            rv.setImageViewResource(
                R.id.widget_btn_favorite,
                if (isFav) R.drawable.widget_ic_favorite_filled else R.drawable.widget_ic_favorite_border
            )
            WidgetPalette.applyImageTint(
                rv,
                R.id.widget_btn_favorite,
                if (isFav) tokens.favoriteActive else tokens.onSurfaceVariant
            )
            rv.setOnClickPendingIntent(
                R.id.widget_btn_favorite,
                if (playback.hasTrack) {
                    WidgetActions.toggleFavoritePendingIntent(context)
                } else {
                    WidgetActions.openHomePendingIntent(context)
                }
            )
        }

        if (hasProgress) {
            rv.setProgressBar(R.id.widget_progress_bar, 1000, playback.progressPermille, false)
            WidgetPalette.applyProgressTint(rv, R.id.widget_progress_bar, tokens.primary, tokens.progressTrack)
        }

        if (hasTimestamps) {
            WidgetPalette.applyTextColor(rv, R.id.widget_time_elapsed, tokens.onSurfaceVariant)
            WidgetPalette.applyTextColor(rv, R.id.widget_time_duration, tokens.onSurfaceVariant)
            rv.setTextViewText(
                R.id.widget_time_elapsed,
                if (playback.hasTrack) playback.formattedPosition else context.getString(R.string.widget_time_zero)
            )
            rv.setTextViewText(
                R.id.widget_time_duration,
                if (playback.hasTrack) playback.formattedDuration else context.getString(R.string.widget_time_zero)
            )
        }

        // Transport buttons
        val navSurface = if (usesTonalTransportButtons) tokens.secondaryContainer else tokens.iconButtonSurface
        val navTint = if (usesTonalTransportButtons) tokens.onSecondaryContainer else tokens.onSurface
        WidgetPalette.applyBackgroundTint(rv, R.id.widget_btn_prev, navSurface)
        WidgetPalette.applyImageTint(rv, R.id.widget_btn_prev, navTint)
        WidgetPalette.applyBackgroundTint(rv, R.id.widget_btn_next, navSurface)
        WidgetPalette.applyImageTint(rv, R.id.widget_btn_next, navTint)

        WidgetPalette.applyBackgroundTint(rv, R.id.widget_btn_play_pause, tokens.primary)
        WidgetPalette.applyImageTint(rv, R.id.widget_btn_play_pause, tokens.onPrimary)
        rv.setImageViewResource(
            R.id.widget_btn_play_pause,
            if (playback.isPlaying) R.drawable.widget_ic_pause else R.drawable.widget_ic_play
        )

        // Android 14-16 BAL-safe click routing: when idle (no track), bind transport buttons directly
        // to PendingIntent.getActivity so tapping Play always opens Xperience without BAL blocks.
        val openHomeIntent = WidgetActions.openHomePendingIntent(context)
        rv.setOnClickPendingIntent(
            R.id.widget_player_tap_area,
            if (playback.hasTrack) WidgetActions.openPlayerPendingIntent(context) else openHomeIntent
        )
        rv.setOnClickPendingIntent(
            R.id.widget_btn_prev,
            if (playback.hasTrack) WidgetActions.previousPendingIntent(context) else openHomeIntent
        )
        rv.setOnClickPendingIntent(
            R.id.widget_btn_play_pause,
            if (playback.hasTrack) WidgetActions.playPausePendingIntent(context) else openHomeIntent
        )
        rv.setOnClickPendingIntent(
            R.id.widget_btn_next,
            if (playback.hasTrack) WidgetActions.nextPendingIntent(context) else openHomeIntent
        )
    }

    private fun bindPlaylistHeader(
        context: Context,
        rv: RemoteViews,
        tokens: WidgetColorTokens,
        hasBrandIcon: Boolean,
        hasPlaylists: Boolean
    ) {
        if (hasBrandIcon) {
            WidgetPalette.applyImageTint(rv, R.id.widget_playlists_brand_icon, tokens.accentText)
            WidgetPalette.applyTextColor(rv, R.id.widget_playlists_header_title, tokens.onSurface)
        } else {
            WidgetPalette.applyTextColor(rv, R.id.widget_playlists_header_title, tokens.onSurfaceVariant)
        }
        WidgetPalette.applyBackgroundTint(rv, R.id.widget_btn_refresh, tokens.iconButtonSurface)
        WidgetPalette.applyImageTint(rv, R.id.widget_btn_refresh, tokens.accentText)

        rv.setOnClickPendingIntent(
            R.id.widget_playlists_header_row,
            WidgetActions.openHomePendingIntent(context)
        )
        // When recommendations are empty, launch MainActivity directly via Activity PendingIntent
        // (Android 14-16 BAL-safe) instead of attempting startActivity from a BroadcastReceiver.
        rv.setOnClickPendingIntent(
            R.id.widget_btn_refresh,
            if (hasPlaylists) {
                WidgetActions.refreshRecommendationsPendingIntent(context)
            } else {
                WidgetActions.openRefreshRecommendationsPendingIntent(context)
            }
        )
    }

    private fun bindPlaylistSlots(
        context: Context,
        rv: RemoteViews,
        playlists: List<WidgetPlaylistItem>,
        artworks: List<Bitmap?>,
        tokens: WidgetColorTokens,
        maxSlots: Int,
        hasSecondRow: Boolean,
        hasEmptyIcon: Boolean
    ) {
        if (playlists.isEmpty()) {
            rv.setViewVisibility(R.id.widget_playlists_grid, View.GONE)
            rv.setViewVisibility(R.id.widget_playlists_empty_state, View.VISIBLE)
            WidgetPalette.applyBackgroundTint(rv, R.id.widget_playlists_empty_state, tokens.surfaceCard)
            WidgetPalette.applyTextColor(rv, R.id.widget_playlists_empty_title, tokens.onSurface)
            WidgetPalette.applyTextColor(rv, R.id.widget_playlists_empty_subtitle, tokens.onSurfaceVariant)
            if (hasEmptyIcon) {
                WidgetPalette.applyImageTint(rv, R.id.widget_playlists_empty_icon, tokens.accentText)
            }
            rv.setOnClickPendingIntent(
                R.id.widget_playlists_empty_state,
                WidgetActions.openHomePendingIntent(context)
            )
            return
        }

        rv.setViewVisibility(R.id.widget_playlists_grid, View.VISIBLE)
        rv.setViewVisibility(R.id.widget_playlists_empty_state, View.GONE)

        if (hasSecondRow) {
            rv.setViewVisibility(
                R.id.widget_playlists_row_2,
                if (playlists.size > 2) View.VISIBLE else View.GONE
            )
        }

        val slotIds = PLAYLIST_SLOT_IDS.take(maxSlots)
        slotIds.forEachIndexed { index, ids ->
            val item = playlists.getOrNull(index)
            if (item == null) {
                rv.setViewVisibility(ids.cardId, View.INVISIBLE)
                return@forEachIndexed
            }

            rv.setViewVisibility(ids.cardId, View.VISIBLE)
            WidgetPalette.applyBackgroundTint(rv, ids.cardId, tokens.surfaceCard)
            WidgetPalette.applyBackgroundTint(rv, ids.artFrameId, tokens.artworkPlaceholder)
            WidgetPalette.applyImageTint(rv, ids.artPlaceholderId, tokens.accentText)
            WidgetPalette.applyTextColor(rv, ids.titleId, tokens.onSurface)
            WidgetPalette.applyTextColor(rv, ids.subtitleId, tokens.onSurfaceVariant)

            val resolvedTitle = when (item.sourceType) {
                WidgetPlaylistSourceType.LIKED_SONGS -> context.getString(R.string.widget_liked_songs_title)
                WidgetPlaylistSourceType.RECENTLY_PLAYED -> context.getString(R.string.widget_recent_mix_title)
                else -> item.title
            }
            rv.setTextViewText(ids.titleId, resolvedTitle)
            rv.setTextViewText(ids.subtitleId, formatPlaylistSubtitle(context, item))

            val bitmap = artworks.getOrNull(index)
            if (bitmap != null && !bitmap.isRecycled) {
                rv.setImageViewBitmap(ids.artId, bitmap)
                rv.setViewVisibility(ids.artId, View.VISIBLE)
                rv.setViewVisibility(ids.artPlaceholderId, View.GONE)
            } else {
                rv.setViewVisibility(ids.artId, View.GONE)
                rv.setViewVisibility(ids.artPlaceholderId, View.VISIBLE)
            }

            rv.setOnClickPendingIntent(
                ids.cardId,
                WidgetActions.openPlaylistPendingIntent(
                    context = context,
                    item = item,
                    slotIndex = index,
                    autoPlay = true
                )
            )
        }
    }

    private fun formatPlaylistSubtitle(context: Context, item: WidgetPlaylistItem): String {
        if (item.trackCount > 0) {
            return if (item.trackCount == 1) {
                context.getString(R.string.widget_track_count_singular)
            } else {
                context.getString(R.string.widget_track_count_plural, item.trackCount)
            }
        }
        if (item.subtitle.isNotBlank()) return item.subtitle
        return when (item.sourceType) {
            WidgetPlaylistSourceType.USER_PLAYLIST -> context.getString(R.string.widget_badge_user_playlist)
            WidgetPlaylistSourceType.SMART_PLAYLIST -> context.getString(R.string.widget_badge_smart_mix)
            WidgetPlaylistSourceType.MADE_FOR_YOU -> context.getString(R.string.widget_badge_made_for_you)
            WidgetPlaylistSourceType.RADIO_STATION -> context.getString(R.string.widget_badge_radio)
            WidgetPlaylistSourceType.LIKED_SONGS -> context.getString(R.string.widget_badge_liked)
            WidgetPlaylistSourceType.RECENTLY_PLAYED -> context.getString(R.string.widget_badge_recent)
        }
    }

    private fun getWidgetIds(
        context: Context,
        manager: AppWidgetManager,
        providerClass: Class<*>
    ): IntArray {
        return runCatching {
            manager.getAppWidgetIds(ComponentName(context.applicationContext, providerClass))
        }.getOrDefault(IntArray(0))
    }

    private fun safePush(manager: AppWidgetManager, widgetIds: IntArray, views: RemoteViews) {
        if (widgetIds.isEmpty()) return
        try {
            manager.updateAppWidget(widgetIds, views)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update AppWidget ids=${widgetIds.contentToString()}: ${e.message}", e)
        }
    }

    private data class PlaylistSlotViewIds(
        val cardId: Int,
        val artFrameId: Int,
        val artId: Int,
        val artPlaceholderId: Int,
        val titleId: Int,
        val subtitleId: Int
    )

    private val PLAYLIST_SLOT_IDS = listOf(
        PlaylistSlotViewIds(
            cardId = R.id.widget_pl_card_0,
            artFrameId = R.id.widget_pl_art_frame_0,
            artId = R.id.widget_pl_art_0,
            artPlaceholderId = R.id.widget_pl_art_placeholder_0,
            titleId = R.id.widget_pl_title_0,
            subtitleId = R.id.widget_pl_subtitle_0
        ),
        PlaylistSlotViewIds(
            cardId = R.id.widget_pl_card_1,
            artFrameId = R.id.widget_pl_art_frame_1,
            artId = R.id.widget_pl_art_1,
            artPlaceholderId = R.id.widget_pl_art_placeholder_1,
            titleId = R.id.widget_pl_title_1,
            subtitleId = R.id.widget_pl_subtitle_1
        ),
        PlaylistSlotViewIds(
            cardId = R.id.widget_pl_card_2,
            artFrameId = R.id.widget_pl_art_frame_2,
            artId = R.id.widget_pl_art_2,
            artPlaceholderId = R.id.widget_pl_art_placeholder_2,
            titleId = R.id.widget_pl_title_2,
            subtitleId = R.id.widget_pl_subtitle_2
        ),
        PlaylistSlotViewIds(
            cardId = R.id.widget_pl_card_3,
            artFrameId = R.id.widget_pl_art_frame_3,
            artId = R.id.widget_pl_art_3,
            artPlaceholderId = R.id.widget_pl_art_placeholder_3,
            titleId = R.id.widget_pl_title_3,
            subtitleId = R.id.widget_pl_subtitle_3
        )
    )
}
