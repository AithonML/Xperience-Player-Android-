package com.xperience.player.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.xperience.player.data.FavoritesRepository
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.service.AudioPlayerService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Handles widget interactive button actions (`ACTION_PLAY_PAUSE`, `ACTION_NEXT`, `ACTION_PREV`,
 * `ACTION_TOGGLE_FAVORITE`, `ACTION_REFRESH_RECOMMENDATIONS`) via explicit package-scoped
 * immutable `PendingIntent`s.
 *
 * Registered with `android:exported="false"` in `AndroidManifest.xml` so only PendingIntents
 * issued by Xperience Player can trigger playback or library mutations. Never calls
 * `startActivity(...)` from background receiver context (Android 14-16 BAL compliant); idle
 * launch buttons bind directly to `PendingIntent.getActivity` in [WidgetUpdater].
 */
class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val appContext = context.applicationContext
        val pendingResult = goAsync()

        receiverScope.launch {
            try {
                withTimeoutOrNull(RECEIVER_TIMEOUT_MS) {
                    when (action) {
                        WidgetActions.ACTION_PLAY_PAUSE -> handleTransportAction(appContext, TransportCommand.PLAY_PAUSE)
                        WidgetActions.ACTION_NEXT -> handleTransportAction(appContext, TransportCommand.NEXT)
                        WidgetActions.ACTION_PREV -> handleTransportAction(appContext, TransportCommand.PREVIOUS)
                        WidgetActions.ACTION_TOGGLE_FAVORITE -> handleToggleFavorite(appContext)
                        WidgetActions.ACTION_REFRESH_RECOMMENDATIONS -> handleRefreshRecommendations(appContext)
                        else -> Log.d(TAG, "Ignoring unrecognized widget action: $action")
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e(TAG, "Error handling widget action=$action: ${e.message}", e)
            } finally {
                runCatching { pendingResult.finish() }
            }
        }
    }

    private enum class TransportCommand {
        PLAY_PAUSE,
        NEXT,
        PREVIOUS
    }

    private suspend fun handleTransportAction(appContext: Context, command: TransportCommand) {
        val persisted = withContext(Dispatchers.IO) {
            WidgetStateStore(appContext).readPlayback()
        }

        // ExoPlayer requires Main-thread access for all player commands.
        val updatedState = withContext(Dispatchers.Main.immediate) {
            val audioPlayer = AudioPlayerService.getInstance(appContext)
            val before = audioPlayer.playbackState.value
            val hasLoadedPlayback = before.currentTrack != null || before.queue.isNotEmpty()

            if (!hasLoadedPlayback) {
                // Process restarted with a persisted track: restore & play directly without BAL startActivity.
                val trackId = persisted.trackId
                if (persisted.hasTrack && !trackId.isNullOrBlank()) {
                    val restoredTrack = TrackItem(
                        id = trackId,
                        title = persisted.title,
                        artist = persisted.artist.ifBlank { "Unknown Artist" },
                        album = persisted.album,
                        durationMs = persisted.safeDurationMs,
                        artworkResId = persisted.artworkResId,
                        artworkUri = persisted.artworkUri,
                        audioUrl = persisted.audioUrl,
                        isLiked = persisted.isLiked
                    )
                    audioPlayer.playSingleTrack(restoredTrack)
                    return@withContext audioPlayer.playbackState.value.copy(isPlaying = true)
                }
                return@withContext null
            }

            when (command) {
                TransportCommand.PLAY_PAUSE -> audioPlayer.togglePlayPause()
                TransportCommand.NEXT -> audioPlayer.skipNext()
                TransportCommand.PREVIOUS -> audioPlayer.skipPrevious()
            }
            val after = audioPlayer.playbackState.value
            if (command == TransportCommand.PLAY_PAUSE && after.currentTrack != null) {
                val expectedPlaying = if (audioPlayer.exoPlayer.mediaItemCount == 0 && !before.isPlaying) {
                    true
                } else {
                    !before.isPlaying
                }
                after.copy(isPlaying = expectedPlaying)
            } else {
                after
            }
        }

        if (updatedState != null) {
            val trackId = updatedState.currentTrack?.id
            val isLiked = withContext(Dispatchers.IO) {
                if (trackId.isNullOrBlank()) {
                    false
                } else {
                    FavoritesRepository(appContext).likedTrackIds.value.contains(trackId)
                }
            }
            // Persist synchronously without spawning a competing async render; we await update*Now below.
            WidgetSyncBridge.syncPlaybackInternal(
                context = appContext,
                playbackState = updatedState,
                isLiked = isLiked,
                accentColorArgb = persisted.accentColorArgb,
                triggerAsyncRender = false
            )
        }

        WidgetUpdater.updateMiniPlayerWidgetsNow(appContext)
        WidgetUpdater.updateMusicHubWidgetsNow(appContext)
    }

    private suspend fun handleToggleFavorite(appContext: Context) {
        val store = WidgetStateStore(appContext)
        val snapshot = withContext(Dispatchers.IO) { store.readPlayback() }
        val trackId = snapshot.trackId?.takeIf { it.isNotBlank() } ?: return

        val fallbackTrack = TrackItem(
            id = trackId,
            title = snapshot.title.ifBlank { "Favorite Song" },
            artist = snapshot.artist.ifBlank { "Unknown Artist" },
            album = snapshot.album,
            durationMs = snapshot.safeDurationMs,
            artworkResId = snapshot.artworkResId,
            artworkUri = snapshot.artworkUri,
            audioUrl = snapshot.audioUrl,
            isLiked = snapshot.isLiked
        )

        val handledByLiveApp = withContext(Dispatchers.Main.immediate) {
            WidgetSyncBridge.onFavoriteToggleRequested?.invoke(trackId, fallbackTrack)
        }

        val newLiked = handledByLiveApp ?: withContext(Dispatchers.IO) {
            FavoritesRepository(appContext).toggleFavorite(fallbackTrack)
        }

        WidgetSyncBridge.notifyFavoriteChanged(
            context = appContext,
            trackId = trackId,
            isLiked = newLiked,
            triggerAsyncRender = false
        )
        WidgetUpdater.updateMiniPlayerWidgetsNow(appContext)
        WidgetUpdater.updateMusicHubWidgetsNow(appContext)
    }

    private suspend fun handleRefreshRecommendations(appContext: Context) {
        withContext(Dispatchers.Main.immediate) {
            WidgetSyncBridge.onRefreshRecommendationsRequested?.invoke()
        }

        val store = WidgetStateStore(appContext)
        val existing = withContext(Dispatchers.IO) { store.readRecommendations() }
        if (existing.isEmpty()) {
            WidgetSyncBridge.hydrateRecommendationsFromLocalDiskIfEmpty(appContext)
        } else {
            withContext(Dispatchers.IO) {
                store.advanceRecommendationOffset(totalItems = existing.size, step = 2)
            }
        }

        WidgetUpdater.updatePlaylistWidgetsNow(appContext)
        WidgetUpdater.updateMusicHubWidgetsNow(appContext)
    }

    companion object {
        private const val TAG = "WidgetActionReceiver"
        private const val RECEIVER_TIMEOUT_MS = 8_000L
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
