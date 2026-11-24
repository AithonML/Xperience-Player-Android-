package com.xperience.player.service

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import coil.request.ImageRequest
import coil.size.Scale
import com.xperience.player.R
import com.xperience.player.domain.model.PlaybackRepeatMode
import com.xperience.player.domain.model.PlaybackState
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.service.dsp.AudioDspController
import com.xperience.player.service.dsp.SleepTimerManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@OptIn(UnstableApi::class)
class AudioPlayerService private constructor(
    private val appContext: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ytmService = YouTubeMusicService.getInstance()

    val dspController: AudioDspController = AudioDspController.getInstance(appContext)
    val sleepTimerManager: SleepTimerManager = SleepTimerManager.getInstance(appContext)

    private val wakeLockLock = Any()
    private val transitionWakeLock: PowerManager.WakeLock? by lazy {
        runCatching {
            val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TRANSITION_WAKE_LOCK_TAG)?.apply {
                setReferenceCounted(false)
            }
        }.getOrNull()
    }

    private val audioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            15_000, // minBufferMs
            30_000, // maxBufferMs
            1_000,  // bufferForPlaybackMs
            2_000   // bufferForPlaybackAfterRebufferMs
        )
        .build()

    private val renderersFactory = object : DefaultRenderersFactory(appContext) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioTrackPlaybackParams: Boolean
        ): AudioSink? {
            return DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(arrayOf(dspController.dspProcessor))
                .build()
        }
    }

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(appContext, renderersFactory)
        .setAudioAttributes(audioAttributes, true)
        .setLoadControl(loadControl)
        .setWakeMode(C.WAKE_MODE_LOCAL)
        .setHandleAudioBecomingNoisy(true)
        .build().apply {
            volume = 1.0f
            repeatMode = Player.REPEAT_MODE_OFF
        }

    val forwardingPlayer: Player = object : ForwardingPlayer(exoPlayer) {
        override fun getPlaybackState(): Int {
            val state = super.getPlaybackState()
            // Keep MediaSessionService in foreground while transitioning/downloading next track
            return if (isLoadingMedia && (state == Player.STATE_ENDED || state == Player.STATE_IDLE)) {
                Player.STATE_BUFFERING
            } else {
                state
            }
        }

        override fun getAvailableCommands(): Player.Commands {
            val builder = super.getAvailableCommands().buildUpon()
                .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                .add(Player.COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_SET_SHUFFLE_MODE)
                .add(Player.COMMAND_SET_REPEAT_MODE)
            if (canSkipNext()) {
                builder.add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            } else {
                builder.remove(Player.COMMAND_SEEK_TO_NEXT)
                    .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            }
            if (canSkipPrevious()) {
                builder.add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            } else {
                builder.remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            }
            return builder.build()
        }

        override fun isCommandAvailable(command: Int): Boolean {
            return when (command) {
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> canSkipNext()
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> canSkipPrevious()
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_SET_SHUFFLE_MODE,
                Player.COMMAND_SET_REPEAT_MODE -> true
                else -> super.isCommandAvailable(command)
            }
        }

        override fun seekToNext() {
            skipNext()
        }

        override fun seekToNextMediaItem() {
            skipNext()
        }

        override fun seekToPrevious() {
            skipPrevious()
        }

        override fun seekToPreviousMediaItem() {
            skipPrevious()
        }

        override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
            if (_playbackState.value.isShuffleEnabled != shuffleModeEnabled) {
                toggleShuffle()
            }
        }

        override fun setRepeatMode(repeatMode: Int) {
            val mode = when (repeatMode) {
                Player.REPEAT_MODE_ONE -> PlaybackRepeatMode.RepeatOne
                Player.REPEAT_MODE_ALL -> PlaybackRepeatMode.RepeatAll
                else -> PlaybackRepeatMode.Off
            }
            setRepeatMode(mode)
        }
    }

    private var attachedMediaSession: MediaSession? = null

    private val _playbackState = MutableStateFlow(PlaybackState(repeatMode = PlaybackRepeatMode.Off))
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _playbackPositionMs = MutableStateFlow(0L)
    val playbackPositionMs: StateFlow<Long> = _playbackPositionMs.asStateFlow()

    var onTrackPlayed: ((TrackItem) -> Unit)? = null

    /**
     * Optional offline/catalog similar-track resolver injected by [com.xperience.player.ui.home.HomeViewModel]
     * used when [YouTubeMusicService.fetchSimilarTracksForQueue] returns empty or throws offline.
     */
    var fallbackSimilarTracksProvider: ((seedTracks: List<TrackItem>, excludeIds: Set<String>) -> List<TrackItem>)? = null

    private val queueController = PlaybackQueueController()
    private val progressiveQueueManager = ProgressiveQueueManager(
        scope = scope,
        ioDispatcher = Dispatchers.IO,
        queueController = queueController,
        fetchRemoteSimilar = { seeds, excludeIds ->
            ytmService.fetchSimilarTracksForQueue(
                seedTracks = seeds,
                excludeTrackIds = excludeIds,
                limit = SIMILAR_QUEUE_BATCH_SIZE
            )
        },
        fetchFallbackSimilar = { seeds, excludeIds ->
            fallbackSimilarTracksProvider?.invoke(seeds, excludeIds).orEmpty()
        },
        onQueueUpdated = { snapshot ->
            _playbackState.update { state ->
                state.copy(
                    queue = snapshot.queue,
                    currentQueueIndex = snapshot.currentIndex
                )
            }
            updateSessionLayout()
        },
        onAutoAdvance = ::onProgressiveAutoAdvance,
        onQueueEndedExhausted = {
            isLoadingMedia = false
            releaseTransitionWakeLock()
            _playbackState.update { it.copy(isLoading = false, isPlaying = false) }
            seekTo(0L)
            pause()
        },
        log = { msg -> runCatching { Log.d(TAG, msg) } }
    )
    private var progressPollJob: Job? = null
    private var audioLoadJob: Job? = null
    private var artworkLoadJob: Job? = null
    private var nextTrackPreloadJob: Job? = null
    @Volatile private var preloadedNextTrackId: String? = null
    @Volatile private var consecutiveAutoSkipFailures: Int = 0
    @Volatile private var isLoadingMedia: Boolean = false
    @Volatile private var preparedForCurrentSession: Boolean = false
    @Volatile private var activeLoadingSessionId: Long = 0L

    init {
        dspController.bindPlaybackParametersSink { params ->
            runCatching { exoPlayer.playbackParameters = params }
        }
        sleepTimerManager.onPausePlaybackRequested = {
            pause()
        }
        sleepTimerManager.onVolumeMultiplierChanged = { multiplier ->
            runCatching {
                exoPlayer.volume = (_playbackState.value.volume * multiplier).coerceIn(0f, 1f)
            }
        }

        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d(TAG, "ExoPlayer onIsPlayingChanged: $isPlaying")
                val currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
                _playbackPositionMs.value = currentPos
                _playbackState.update {
                    it.copy(
                        isPlaying = isPlaying,
                        currentPositionMs = currentPos,
                        isLoading = resolveLoadingState(isLoadingMedia, exoPlayer.playbackState)
                    )
                }
                if (isPlaying) {
                    startProgressPolling()
                    ensureServiceStarted()
                } else {
                    stopProgressPolling()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                Log.d(TAG, "ExoPlayer onPlaybackStateChanged: $playbackState")
                if (playbackState == Player.STATE_READY) {
                    if (preparedForCurrentSession) {
                        isLoadingMedia = false
                        consecutiveAutoSkipFailures = 0
                        releaseTransitionWakeLock()
                    }
                    _playbackState.update {
                        it.copy(isLoading = resolveLoadingState(isLoadingMedia, playbackState))
                    }
                    val rawDuration = exoPlayer.duration
                    if (!isLoadingMedia && rawDuration != C.TIME_UNSET && rawDuration > 0L) {
                        if (rawDuration != _playbackState.value.durationMs) {
                            updateNaturalDuration(rawDuration)
                        }
                        if (rawDuration <= SHORT_TRACK_IMMEDIATE_PRELOAD_MS) {
                            maybePreloadNextTrack(exoPlayer.currentPosition.coerceAtLeast(0L), rawDuration)
                        }
                    }
                } else if (playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE) {
                    _playbackState.update {
                        it.copy(isLoading = resolveLoadingState(isLoadingMedia, playbackState))
                    }
                } else if (playbackState == Player.STATE_ENDED) {
                    if (sleepTimerManager.consumeEndOfTrackShouldPause()) {
                        seekTo(0L)
                        return
                    }
                    if (_playbackState.value.repeatMode == PlaybackRepeatMode.RepeatOne) {
                        seekTo(0L)
                        resume()
                    } else {
                        advanceToNextTrack()
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isLoadingMedia = false
                releaseTransitionWakeLock()
                _playbackState.update { it.copy(isLoading = false, isPlaying = false) }
                Log.e(TAG, "ExoPlayer playback error: ${error.errorCodeName} - ${error.message}", error)
            }
        })
    }

    private fun acquireTransitionWakeLock() {
        synchronized(wakeLockLock) {
            runCatching {
                transitionWakeLock?.acquire(TRANSITION_WAKE_LOCK_TIMEOUT_MS)
            }.onFailure { e ->
                Log.w(TAG, "Failed to acquire transition wake lock: ${e.message}")
            }
        }
    }

    private fun releaseTransitionWakeLock() {
        synchronized(wakeLockLock) {
            runCatching {
                val wl = transitionWakeLock
                if (wl != null && wl.isHeld) {
                    wl.release()
                }
            }.onFailure { e ->
                Log.w(TAG, "Failed to release transition wake lock: ${e.message}")
            }
        }
    }

    private fun cancelNextTrackPreload() {
        nextTrackPreloadJob?.cancel()
        nextTrackPreloadJob = null
        preloadedNextTrackId = null
    }

    fun attachMediaSession(session: MediaSession?) {
        attachedMediaSession = session
        updateSessionLayout()
    }

    fun detachMediaSession() {
        attachedMediaSession = null
    }

    fun ensureServiceStarted() {
        val intent = Intent(appContext, PlaybackService::class.java)
        val shouldStartForeground = attachedMediaSession == null &&
            (isLoadingMedia || _playbackState.value.isPlaying || exoPlayer.isPlaying)
        if (shouldStartForeground) {
            try {
                ContextCompat.startForegroundService(appContext, intent)
                return
            } catch (e: Exception) {
                Log.w(TAG, "startForegroundService failed, falling back to startService: ${e.message}")
            }
        }
        try {
            appContext.startService(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not start PlaybackService", e)
        }
    }

    fun setQueue(
        tracks: List<TrackItem>,
        startIndex: Int = 0,
        startPositionMs: Long = 0L,
        autoPlay: Boolean = true
    ) {
        consecutiveAutoSkipFailures = 0
        cancelNextTrackPreload()
        progressiveQueueManager.onQueueReplaced()
        val snapshot = queueController.setQueue(tracks, startIndex)
        if (snapshot.queue.isEmpty() || snapshot.currentTrack == null) {
            _playbackState.update { it.copy(queue = emptyList(), currentQueueIndex = -1) }
            return
        }
        playResolvedTrack(
            track = snapshot.currentTrack,
            snapshot = snapshot,
            startPositionMs = startPositionMs.coerceAtLeast(0L),
            autoPlay = autoPlay
        )
        progressiveQueueManager.maybePrefetch(_playbackState.value.repeatMode)
    }

    /**
     * Starts a fresh single-track queue seeded with [track] (e.g. from Search results)
     * so finishing or skipping it transitions seamlessly into progressive similar tracks.
     */
    fun playSingleTrack(track: TrackItem) {
        Log.d(TAG, "playSingleTrack invoked: ${track.id} - ${track.title}")
        consecutiveAutoSkipFailures = 0
        cancelNextTrackPreload()
        progressiveQueueManager.onQueueReplaced()
        val snapshot = queueController.playSingleTrack(track)
        playResolvedTrack(track, snapshot)
        progressiveQueueManager.maybePrefetch(_playbackState.value.repeatMode)
    }

    fun playTrack(track: TrackItem) {
        Log.d(TAG, "playTrack invoked: ${track.id} - ${track.title}")
        consecutiveAutoSkipFailures = 0
        cancelNextTrackPreload()
        progressiveQueueManager.cancelEndAdvance()
        val snapshot = queueController.playTrack(track)
        playResolvedTrack(track, snapshot)
        progressiveQueueManager.maybePrefetch(_playbackState.value.repeatMode)
    }

    fun playQueueIndex(index: Int) {
        consecutiveAutoSkipFailures = 0
        cancelNextTrackPreload()
        progressiveQueueManager.cancelEndAdvance()
        val track = queueController.playQueueIndex(index) ?: return
        Log.d(TAG, "playQueueIndex invoked: [$index] ${track.id} - ${track.title}")
        playResolvedTrack(track, queueController.snapshot())
        progressiveQueueManager.maybePrefetch(_playbackState.value.repeatMode)
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        if (!queueController.moveQueueItem(fromIndex, toIndex)) return
        cancelNextTrackPreload()
        val snapshot = queueController.snapshot()
        _playbackState.update {
            it.copy(
                queue = snapshot.queue,
                currentQueueIndex = snapshot.currentIndex
            )
        }
        progressiveQueueManager.maybePrefetch(_playbackState.value.repeatMode)
    }

    private fun playResolvedTrack(
        track: TrackItem,
        snapshot: PlaybackQueueController.Snapshot,
        startPositionMs: Long = 0L,
        autoPlay: Boolean = true
    ) {
        val sessionId = ++activeLoadingSessionId
        isLoadingMedia = true
        preparedForCurrentSession = false
        cancelNextTrackPreload()
        acquireTransitionWakeLock()
        dspController.onTrackTransition()

        val initialPos = startPositionMs.coerceAtLeast(0L)
        _playbackPositionMs.value = initialPos
        _playbackState.update {
            it.copy(
                queue = snapshot.queue,
                currentQueueIndex = snapshot.currentIndex,
                currentTrack = track,
                currentPositionMs = initialPos,
                durationMs = track.durationMs,
                isLoading = true
            )
        }

        onTrackPlayed?.invoke(track)
        ensureServiceStarted()

        audioLoadJob?.cancel()
        artworkLoadJob?.cancel()

        if (isLocalPlayableTrack(track)) {
            Log.d(TAG, "Playing local audio: ${track.audioUrl}")
            startPlaybackPrepared(track, Uri.parse(track.audioUrl), sessionId, initialPos, autoPlay)
        } else {
            // Asynchronously resolve & download YouTube Music audio into local cache
            val rawVideoId = extractOnlineVideoId(track)

            audioLoadJob = scope.launch(Dispatchers.IO) {
                try {
                    var videoIdToPlay = rawVideoId
                    if (videoIdToPlay.isNullOrBlank()) {
                        Log.d(TAG, "Searching YouTube Music for '${track.artist} - ${track.title}'...")
                        val searchResult = ytmService.searchSongs("${track.artist} - ${track.title}")
                        videoIdToPlay = searchResult.firstOrNull()?.id?.removePrefix("ytm_")
                    }

                    if (!videoIdToPlay.isNullOrBlank()) {
                        Log.d(TAG, "Downloading/loading cached audio for videoId: $videoIdToPlay")
                        var audioFile = ytmService.getOrDownloadAudioFile(videoIdToPlay, appContext)
                        if (audioFile == null || !audioFile.exists() || audioFile.length() <= 50_000) {
                            Log.w(TAG, "First audio download attempt failed for $videoIdToPlay, retrying in ${DOWNLOAD_RETRY_DELAY_MS}ms...")
                            delay(DOWNLOAD_RETRY_DELAY_MS)
                            if (activeLoadingSessionId != sessionId) return@launch
                            audioFile = ytmService.getOrDownloadAudioFile(videoIdToPlay, appContext)
                        }

                        withContext(Dispatchers.Main.immediate) {
                            if (activeLoadingSessionId != sessionId) return@withContext
                            if (audioFile != null && audioFile.exists() && audioFile.length() > 50_000) {
                                Log.d(TAG, "Playing cached audio file: ${audioFile.absolutePath} (${audioFile.length()} bytes)")
                                startPlaybackPrepared(track, Uri.fromFile(audioFile), sessionId, initialPos, autoPlay)
                            } else {
                                handleOnlineDownloadFailure(
                                    reason = "Audio file download returned null/empty after retry for $videoIdToPlay",
                                    autoPlay = autoPlay
                                )
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main.immediate) {
                            if (activeLoadingSessionId != sessionId) return@withContext
                            handleOnlineDownloadFailure(
                                reason = "Could not resolve videoId for ${track.title}",
                                autoPlay = autoPlay
                            )
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    Log.e(TAG, "Unexpected error loading audio for ${track.title}: ${t.message}", t)
                    withContext(Dispatchers.Main.immediate) {
                        if (activeLoadingSessionId != sessionId) return@withContext
                        handleOnlineDownloadFailure(
                            reason = "Unexpected error loading audio: ${t.message}",
                            autoPlay = autoPlay
                        )
                    }
                }
            }
        }
    }

    private fun handleOnlineDownloadFailure(reason: String, autoPlay: Boolean) {
        Log.w(TAG, "Online track load failed ($reason)")
        isLoadingMedia = false
        stopProgressPolling()
        _playbackState.update { it.copy(isPlaying = false, isLoading = false) }

        val snapshot = queueController.snapshot()
        val hasRemaining = snapshot.currentIndex >= 0 && snapshot.currentIndex < snapshot.queue.lastIndex
        val action = resolveDownloadFailureAction(
            autoPlay = autoPlay,
            hasRemainingQueueTracks = hasRemaining,
            consecutiveFailures = consecutiveAutoSkipFailures
        )
        if (action == DownloadFailureAction.AUTO_SKIP_NEXT) {
            consecutiveAutoSkipFailures++
            Log.w(TAG, "Auto-skipping failed track (attempt $consecutiveAutoSkipFailures/$MAX_CONSECUTIVE_AUTO_SKIP_FAILURES)")
            advanceToNextTrack()
        } else {
            releaseTransitionWakeLock()
        }
    }

    private fun startPlaybackPrepared(
        track: TrackItem,
        uri: Uri,
        sessionId: Long,
        startPositionMs: Long = 0L,
        autoPlay: Boolean = true
    ) {
        if (activeLoadingSessionId != sessionId) return

        // Start ExoPlayer immediately with artworkUri metadata (never block audio playback on artwork download)
        val initialMetadata = buildMediaMetadata(track, artworkBytes = null)
        val initialMediaItem = MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(uri)
            .setMediaMetadata(initialMetadata)
            .build()

        preparedForCurrentSession = true
        exoPlayer.setMediaItem(initialMediaItem)
        if (startPositionMs > 0L) {
            exoPlayer.seekTo(startPositionMs)
        }
        exoPlayer.prepare()
        if (autoPlay) {
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
        updateSessionLayout()

        // Asynchronously enrich SystemUI MediaSession notification with compact embedded artwork bytes
        artworkLoadJob?.cancel()
        if (!track.artworkUri.isNullOrBlank()) {
            artworkLoadJob = scope.launch(Dispatchers.IO) {
                try {
                    val artworkBytes = loadArtworkBytes(track.artworkUri)
                    if (artworkBytes == null || artworkBytes.isEmpty()) return@launch
                    withContext(Dispatchers.Main.immediate) {
                        if (activeLoadingSessionId != sessionId) return@withContext
                        runCatching {
                            val current = exoPlayer.currentMediaItem
                            if (current != null && current.mediaId == track.id && exoPlayer.mediaItemCount > 0) {
                                val updatedMetadata = buildMediaMetadata(track, artworkBytes)
                                val updatedItem = current.buildUpon()
                                    .setMediaMetadata(updatedMetadata)
                                    .build()
                                exoPlayer.replaceMediaItem(0, updatedItem)
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    Log.w(TAG, "Async artwork metadata update skipped for ${track.title}: ${t.message}")
                }
            }
        }
    }

    private fun maybePreloadNextTrack(currentPositionMs: Long, durationMs: Long) {
        if (durationMs <= 0L) return
        val remainingMs = durationMs - currentPositionMs
        if (remainingMs > NEXT_TRACK_PRELOAD_WINDOW_MS && durationMs > SHORT_TRACK_IMMEDIATE_PRELOAD_MS) {
            return
        }

        val repeatMode = _playbackState.value.repeatMode
        if (repeatMode == PlaybackRepeatMode.RepeatOne) return

        val snapshot = queueController.snapshot()
        if (snapshot.queue.isEmpty() || snapshot.currentIndex !in snapshot.queue.indices) return

        val nextIndex = when {
            snapshot.currentIndex + 1 < snapshot.queue.size -> snapshot.currentIndex + 1
            repeatMode == PlaybackRepeatMode.RepeatAll && snapshot.queue.size > 1 -> 0
            else -> return
        }
        val nextTrack = snapshot.queue[nextIndex]
        if (nextTrack.id == preloadedNextTrackId || isLocalPlayableTrack(nextTrack)) return

        val nextVideoId = extractOnlineVideoId(nextTrack) ?: return
        preloadedNextTrackId = nextTrack.id
        nextTrackPreloadJob?.cancel()
        nextTrackPreloadJob = scope.launch(Dispatchers.IO) {
            runCatching {
                Log.d(TAG, "Pre-warming next online track cache: ${nextTrack.title} ($nextVideoId)")
                ytmService.getOrDownloadAudioFile(nextVideoId, appContext)
            }.onFailure { err ->
                if (err is CancellationException) throw err
                Log.w(TAG, "Next track pre-warm skipped for $nextVideoId: ${err.message}")
            }
        }
    }

    private fun buildMediaMetadata(track: TrackItem, artworkBytes: ByteArray?): MediaMetadata {
        val builder = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)

        if (!track.artworkUri.isNullOrBlank()) {
            builder.setArtworkUri(Uri.parse(track.artworkUri))
        }

        if (artworkBytes != null && artworkBytes.isNotEmpty()) {
            builder.setArtworkData(artworkBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }

        return builder.build()
    }

    private suspend fun loadArtworkBytes(artworkUri: String?): ByteArray? = withContext(Dispatchers.IO) {
        if (artworkUri.isNullOrBlank()) return@withContext null
        try {
            val imageLoader = MediaNetworkClients.getImageLoader(appContext)
            val request = ImageRequest.Builder(appContext)
                .data(artworkUri)
                .size(256, 256)
                .scale(Scale.FILL)
                .allowHardware(false) // Software bitmap required for compression
                .build()

            val result = imageLoader.execute(request)
            val drawable = result.drawable
            val bitmap = (drawable as? BitmapDrawable)?.bitmap
            if (bitmap != null) {
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 75, stream)
                return@withContext stream.toByteArray()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load artwork bytes for $artworkUri", e)
        }
        null
    }

    fun togglePlayPause() {
        val state = _playbackState.value
        if (state.currentTrack == null && queueController.isNotEmpty()) {
            val targetIdx = queueController.currentQueueIndex().takeIf { it in 0 until queueController.size } ?: 0
            playQueueIndex(targetIdx)
            return
        }

        if (state.isPlaying) {
            pause()
        } else {
            resume()
        }
    }

    fun resume() {
        ensureServiceStarted()
        if (exoPlayer.mediaItemCount > 0) {
            exoPlayer.play()
        } else {
            val current = _playbackState.value.currentTrack
            if (current != null && !isLoadingMedia) {
                playResolvedTrack(
                    track = current,
                    snapshot = queueController.snapshot(),
                    startPositionMs = _playbackPositionMs.value,
                    autoPlay = true
                )
            }
        }
    }

    fun pause() {
        progressiveQueueManager.cancelEndAdvance()
        releaseTransitionWakeLock()
        if (exoPlayer.mediaItemCount > 0) {
            exoPlayer.pause()
        }
        val currentPos = if (exoPlayer.mediaItemCount > 0) {
            exoPlayer.currentPosition.coerceAtLeast(0L)
        } else {
            _playbackPositionMs.value
        }
        _playbackPositionMs.value = currentPos
        _playbackState.update { it.copy(isPlaying = false, currentPositionMs = currentPos) }
        stopProgressPolling()
    }

    fun canSkipNext(): Boolean {
        return queueController.isNotEmpty()
    }

    fun canSkipPrevious(): Boolean {
        return queueController.isNotEmpty()
    }

    fun skipNext() {
        consecutiveAutoSkipFailures = 0
        advanceToNextTrack()
    }

    private fun onProgressiveAutoAdvance(track: TrackItem, snapshot: PlaybackQueueController.Snapshot) {
        playResolvedTrack(track, snapshot)
        progressiveQueueManager.maybePrefetch(_playbackState.value.repeatMode)
    }

    private fun advanceToNextTrack() {
        if (queueController.isEmpty()) return
        val repeatMode = _playbackState.value.repeatMode
        val nextTrack = queueController.skipNext(repeatMode)
        if (nextTrack != null) {
            progressiveQueueManager.cancelEndAdvance()
            playResolvedTrack(nextTrack, queueController.snapshot())
            progressiveQueueManager.maybePrefetch(repeatMode)
            return
        }
        if (repeatMode == PlaybackRepeatMode.Off) {
            stopProgressPolling()
            acquireTransitionWakeLock()
            progressiveQueueManager.resolveAndAdvanceFromEnd(repeatMode)
        } else {
            seekTo(0L)
            pause()
        }
    }

    fun skipPrevious() {
        if (queueController.isEmpty()) return
        val currentPos = maxOf(_playbackPositionMs.value, _playbackState.value.currentPositionMs)
        if (currentPos > 3000L) {
            seekTo(0L)
            return
        }
        consecutiveAutoSkipFailures = 0
        progressiveQueueManager.cancelEndAdvance()
        val prevTrack = queueController.skipPrevious() ?: return
        playResolvedTrack(prevTrack, queueController.snapshot())
        progressiveQueueManager.maybePrefetch(_playbackState.value.repeatMode)
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceIn(0L, _playbackState.value.durationMs.coerceAtLeast(1L))
        if (exoPlayer.mediaItemCount > 0) {
            exoPlayer.seekTo(clamped)
        }
        _playbackPositionMs.value = clamped
        _playbackState.update { it.copy(currentPositionMs = clamped) }
    }

    fun setRepeatMode(mode: PlaybackRepeatMode) {
        _playbackState.update { it.copy(repeatMode = mode) }
        when (mode) {
            PlaybackRepeatMode.Off -> exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
            PlaybackRepeatMode.RepeatOne -> exoPlayer.repeatMode = Player.REPEAT_MODE_ONE
            PlaybackRepeatMode.RepeatAll -> exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
        }
        updateSessionLayout()
        progressiveQueueManager.maybePrefetch(mode)
    }

    fun cycleRepeatMode(): PlaybackRepeatMode {
        val next = when (_playbackState.value.repeatMode) {
            PlaybackRepeatMode.Off -> PlaybackRepeatMode.RepeatAll
            PlaybackRepeatMode.RepeatAll -> PlaybackRepeatMode.RepeatOne
            PlaybackRepeatMode.RepeatOne -> PlaybackRepeatMode.Off
        }
        setRepeatMode(next)
        return next
    }

    fun toggleShuffle() {
        val next = !_playbackState.value.isShuffleEnabled
        _playbackState.update { it.copy(isShuffleEnabled = next) }
        exoPlayer.shuffleModeEnabled = next
        updateSessionLayout()
    }

    fun buildCustomButtons(): List<CommandButton> {
        val isShuffle = _playbackState.value.isShuffleEnabled
        val repeatMode = _playbackState.value.repeatMode

        val shuffleButton = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName(appContext.getString(R.string.custom_action_shuffle))
            .setSessionCommand(SessionCommand(PlaybackService.ACTION_SHUFFLE, Bundle.EMPTY))
            .setIconResId(if (isShuffle) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle_off)
            .setSlots(CommandButton.SLOT_BACK_SECONDARY)
            .setEnabled(true)
            .build()

        val repeatIcon = when (repeatMode) {
            PlaybackRepeatMode.RepeatOne -> R.drawable.ic_repeat_one
            PlaybackRepeatMode.RepeatAll -> R.drawable.ic_repeat_all
            PlaybackRepeatMode.Off -> R.drawable.ic_repeat_off
        }

        val repeatButton = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName(appContext.getString(R.string.custom_action_repeat))
            .setSessionCommand(SessionCommand(PlaybackService.ACTION_REPEAT, Bundle.EMPTY))
            .setIconResId(repeatIcon)
            .setSlots(CommandButton.SLOT_FORWARD_SECONDARY)
            .setEnabled(true)
            .build()

        return listOf(shuffleButton, repeatButton)
    }

    fun updateSessionLayout() {
        val session = attachedMediaSession ?: return
        session.setMediaButtonPreferences(buildCustomButtons())
    }

    private fun updateNaturalDuration(naturalDurationMs: Long) {
        if (isLoadingMedia || naturalDurationMs <= 0L) return
        val queueSnapshot = queueController.updateCurrentTrackDuration(naturalDurationMs)
        _playbackState.update { state ->
            computeUpdatedPlaybackState(
                currentState = state,
                naturalDurationMs = naturalDurationMs,
                isLoadingMedia = isLoadingMedia,
                updatedQueue = queueSnapshot?.queue,
                updatedCurrentIndex = queueSnapshot?.currentIndex
            )
        }
    }

    private fun startProgressPolling() {
        progressPollJob?.cancel()
        progressPollJob = scope.launch {
            while (isActive) {
                if (!isLoadingMedia && exoPlayer.isPlaying) {
                    val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                    val rawDuration = exoPlayer.duration
                    _playbackPositionMs.value = pos
                    val effectiveDuration = if (rawDuration != C.TIME_UNSET && rawDuration > 0L) {
                        if (rawDuration != _playbackState.value.durationMs) {
                            updateNaturalDuration(rawDuration)
                        }
                        rawDuration
                    } else {
                        _playbackState.value.durationMs
                    }
                    maybePreloadNextTrack(pos, effectiveDuration)
                }
                val intervalMs = if (_playbackPositionMs.subscriptionCount.value > 0) {
                    ACTIVE_UI_POLL_INTERVAL_MS
                } else {
                    BACKGROUND_POLL_INTERVAL_MS
                }
                delay(intervalMs)
            }
        }
    }

    private fun stopProgressPolling() {
        progressPollJob?.cancel()
        progressPollJob = null
    }

    fun release() {
        progressiveQueueManager.onQueueReplaced()
        audioLoadJob?.cancel()
        artworkLoadJob?.cancel()
        cancelNextTrackPreload()
        stopProgressPolling()
        releaseTransitionWakeLock()
        onTrackPlayed = null
        fallbackSimilarTracksProvider = null
        attachedMediaSession = null
        sleepTimerManager.cancelTimer()
        dspController.release()
        exoPlayer.release()
        scope.cancel()
        synchronized(Companion) {
            if (instance === this) {
                instance = null
            }
        }
    }

    internal enum class DownloadFailureAction {
        AUTO_SKIP_NEXT,
        STOP_CLEANLY
    }

    companion object {
        private const val TAG = "AudioPlayer"
        private const val TRANSITION_WAKE_LOCK_TAG = "XperiencePlayer:TrackTransition"
        private const val TRANSITION_WAKE_LOCK_TIMEOUT_MS = 60_000L
        private const val DOWNLOAD_RETRY_DELAY_MS = 500L
        internal const val MAX_CONSECUTIVE_AUTO_SKIP_FAILURES = 3
        private const val NEXT_TRACK_PRELOAD_WINDOW_MS = 25_000L
        private const val SHORT_TRACK_IMMEDIATE_PRELOAD_MS = 30_000L
        private const val ACTIVE_UI_POLL_INTERVAL_MS = 33L
        private const val BACKGROUND_POLL_INTERVAL_MS = 1_000L
        private const val SIMILAR_QUEUE_BATCH_SIZE = 20

        @Volatile
        private var instance: AudioPlayerService? = null

        fun getInstance(context: Context): AudioPlayerService {
            return instance ?: synchronized(this) {
                instance ?: AudioPlayerService(context.applicationContext).also { instance = it }
            }
        }

        internal fun isLocalPlayableTrack(track: TrackItem): Boolean {
            val url = track.audioUrl ?: return false
            return url.startsWith("content://") ||
                url.startsWith("file://") ||
                url.endsWith(".mp3", ignoreCase = true) ||
                url.endsWith(".m4a", ignoreCase = true) ||
                url.endsWith(".flac", ignoreCase = true) ||
                url.endsWith(".wav", ignoreCase = true) ||
                url.endsWith(".ogg", ignoreCase = true)
        }

        internal fun extractOnlineVideoId(track: TrackItem): String? = when {
            track.id.startsWith("ytm_") -> track.id.removePrefix("ytm_").takeIf { it.isNotBlank() }
            track.audioUrl?.contains("v=") == true -> track.audioUrl.substringAfter("v=").substringBefore("&").takeIf { it.isNotBlank() }
            else -> null
        }

        /**
         * Pure decision helper for online download failure: never starts a silent fake
         * simulation ticker; either auto-skips to the next queued track (up to
         * [MAX_CONSECUTIVE_AUTO_SKIP_FAILURES] consecutive times) or stops cleanly.
         */
        internal fun resolveDownloadFailureAction(
            autoPlay: Boolean,
            hasRemainingQueueTracks: Boolean,
            consecutiveFailures: Int
        ): DownloadFailureAction {
            return if (autoPlay && hasRemainingQueueTracks && consecutiveFailures < MAX_CONSECUTIVE_AUTO_SKIP_FAILURES) {
                DownloadFailureAction.AUTO_SKIP_NEXT
            } else {
                DownloadFailureAction.STOP_CLEANLY
            }
        }

        internal fun resolveLoadingState(
            isLoadingMedia: Boolean,
            playerPlaybackState: Int
        ): Boolean = isLoadingMedia || playerPlaybackState == Player.STATE_BUFFERING

        internal fun computeUpdatedPlaybackState(
            currentState: PlaybackState,
            naturalDurationMs: Long,
            isLoadingMedia: Boolean,
            updatedQueue: List<TrackItem>? = null,
            updatedCurrentIndex: Int? = null
        ): PlaybackState {
            if (isLoadingMedia || naturalDurationMs <= 0L) return currentState
            val current = currentState.currentTrack
            val updatedTrack = if (current != null && kotlin.math.abs(current.durationMs - naturalDurationMs) > 1000L) {
                current.copy(durationMs = naturalDurationMs)
            } else {
                current
            }
            val finalQueue = updatedQueue ?: if (updatedTrack != null && currentState.currentQueueIndex in currentState.queue.indices) {
                currentState.queue.toMutableList().also { it[currentState.currentQueueIndex] = updatedTrack }
            } else {
                currentState.queue
            }
            return currentState.copy(
                durationMs = naturalDurationMs,
                currentTrack = updatedTrack,
                queue = finalQueue,
                currentQueueIndex = updatedCurrentIndex ?: currentState.currentQueueIndex
            )
        }
    }
}

/**
 * Pure Kotlin queue & active-slot index controller backing [AudioPlayerService].
 * Keeps track order and [currentIndex] consistent across queue initialization,
 * direct slot playback, skip transitions, and drag-and-drop reordering—even when
 * duplicate track IDs exist in the queue.
 */
internal class PlaybackQueueController {

    data class Snapshot(
        val queue: List<TrackItem>,
        val currentIndex: Int,
        val currentTrack: TrackItem?
    )

    private val queue = mutableListOf<TrackItem>()
    private var currentIndex: Int = -1

    var queueEpoch: Long = 0L
        private set

    val size: Int get() = queue.size

    fun isEmpty(): Boolean = queue.isEmpty()

    fun isNotEmpty(): Boolean = queue.isNotEmpty()

    fun currentQueueIndex(): Int = currentIndex

    fun tailTrackId(): String? = queue.lastOrNull()?.id

    fun snapshot(): Snapshot = Snapshot(
        queue = queue.toList(),
        currentIndex = currentIndex,
        currentTrack = queue.getOrNull(currentIndex)
    )

    fun setQueue(tracks: List<TrackItem>, startIndex: Int = 0): Snapshot {
        queueEpoch++
        queue.clear()
        queue.addAll(tracks)
        currentIndex = if (queue.isEmpty()) -1 else startIndex.coerceIn(0, queue.lastIndex)
        return snapshot()
    }

    /**
     * Replaces the entire queue with a single seed [track] at index 0 and bumps [queueEpoch].
     */
    fun playSingleTrack(track: TrackItem): Snapshot {
        queueEpoch++
        queue.clear()
        queue.add(track)
        currentIndex = 0
        return snapshot()
    }

    fun playTrack(track: TrackItem): Snapshot {
        val foundIndex = if (currentIndex in queue.indices && queue[currentIndex].id == track.id) {
            currentIndex
        } else {
            queue.indexOfFirst { it.id == track.id }
        }
        if (foundIndex != -1) {
            currentIndex = foundIndex
            queue[foundIndex] = track
        } else {
            queue.add(track)
            currentIndex = queue.lastIndex
        }
        return snapshot()
    }

    fun playQueueIndex(index: Int): TrackItem? {
        if (index !in queue.indices) return null
        currentIndex = index
        return queue[index]
    }

    fun updateCurrentTrackDuration(naturalDurationMs: Long): Snapshot? {
        if (naturalDurationMs <= 0L || currentIndex !in queue.indices) return null
        val current = queue[currentIndex]
        if (kotlin.math.abs(current.durationMs - naturalDurationMs) <= 1000L) return null
        queue[currentIndex] = current.copy(durationMs = naturalDurationMs)
        return snapshot()
    }

    fun skipNext(repeatMode: PlaybackRepeatMode): TrackItem? {
        if (queue.isEmpty()) return null
        if (currentIndex >= queue.size - 1 && repeatMode == PlaybackRepeatMode.Off) {
            return null
        }
        currentIndex = if (currentIndex < 0) 0 else (currentIndex + 1) % queue.size
        return queue[currentIndex]
    }

    fun skipPrevious(): TrackItem? {
        if (queue.isEmpty()) return null
        currentIndex = if (currentIndex <= 0) queue.size - 1 else currentIndex - 1
        return queue[currentIndex]
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int): Boolean {
        if (fromIndex !in queue.indices || toIndex !in queue.indices || fromIndex == toIndex) {
            return false
        }
        val moved = queue.removeAt(fromIndex)
        queue.add(toIndex, moved)

        if (currentIndex >= 0) {
            currentIndex = when {
                currentIndex == fromIndex -> toIndex
                fromIndex < currentIndex && toIndex >= currentIndex -> currentIndex - 1
                fromIndex > currentIndex && toIndex <= currentIndex -> currentIndex + 1
                else -> currentIndex
            }
        }
        return true
    }

    fun shouldPrefetchNext(repeatMode: PlaybackRepeatMode, thresholdFromEnd: Int = 1): Boolean {
        if (repeatMode != PlaybackRepeatMode.Off || queue.isEmpty() || currentIndex < 0) return false
        return currentIndex >= (queue.lastIndex - thresholdFromEnd).coerceAtLeast(0)
    }

    fun seedTracksForSimilar(maxSeeds: Int = 5): List<TrackItem> {
        if (queue.isEmpty() || maxSeeds <= 0) return emptyList()
        val activeIdx = currentIndex.coerceIn(0, queue.lastIndex)
        val seeds = mutableListOf<TrackItem>()
        val seenKeys = hashSetOf<String>()

        fun addIfUnique(track: TrackItem) {
            if (seeds.size >= maxSeeds) return
            val key = normalizeTrackIdKey(track.id)
                ?: "${track.title.trim().lowercase()}||${track.artist.trim().lowercase()}"
            if (seenKeys.add(key)) {
                seeds.add(track)
            }
        }

        addIfUnique(queue[activeIdx])
        for (idx in (activeIdx - 1) downTo 0) {
            if (seeds.size >= maxSeeds) break
            addIfUnique(queue[idx])
        }
        for (idx in queue.lastIndex downTo (activeIdx + 1)) {
            if (seeds.size >= maxSeeds) break
            addIfUnique(queue[idx])
        }
        return seeds
    }

    fun buildExcludeTrackIds(): Set<String> {
        if (queue.isEmpty()) return emptySet()
        val ids = LinkedHashSet<String>(queue.size * 2)
        queue.forEach { track ->
            val trimmed = track.id.trim()
            if (trimmed.isNotEmpty()) {
                ids.add(trimmed)
                val stripped = trimmed.removePrefix("ytm_")
                if (stripped.isNotEmpty()) ids.add(stripped)
            }
        }
        return ids
    }

    fun filterUniqueCandidates(candidates: List<TrackItem>): List<TrackItem> {
        if (candidates.isEmpty()) return emptyList()
        val existingIds = HashSet<String>(queue.size * 2)
        val existingTitleArtists = HashSet<String>(queue.size)
        queue.forEach { track ->
            normalizeTrackIdKey(track.id)?.let { existingIds.add(it) }
            normalizeTitleArtistKey(track)?.let { existingTitleArtists.add(it) }
        }

        val accepted = mutableListOf<TrackItem>()
        candidates.forEach { candidate ->
            if (candidate.title.isBlank()) return@forEach
            val idKey = normalizeTrackIdKey(candidate.id)
            val titleArtistKey = normalizeTitleArtistKey(candidate)
            if (idKey != null && idKey in existingIds) return@forEach
            if (titleArtistKey != null && titleArtistKey in existingTitleArtists) return@forEach
            if (idKey != null) existingIds.add(idKey)
            if (titleArtistKey != null) existingTitleArtists.add(titleArtistKey)
            accepted.add(candidate)
        }
        return accepted
    }

    fun appendSimilarTracks(expectedEpoch: Long, candidates: List<TrackItem>): Snapshot? {
        if (expectedEpoch != queueEpoch || queue.isEmpty()) return null
        val unique = filterUniqueCandidates(candidates)
        if (unique.isEmpty()) return null
        queue.addAll(unique)
        if (queue.size > MAX_QUEUE_SIZE && currentIndex > PLAYED_HISTORY_KEEP_COUNT) {
            val trimCount = (currentIndex - PLAYED_HISTORY_KEEP_COUNT).coerceAtMost(queue.size - 1)
            if (trimCount > 0) {
                queue.subList(0, trimCount).clear()
                currentIndex -= trimCount
            }
        }
        return snapshot()
    }

    companion object {
        internal const val MAX_QUEUE_SIZE = 250
        internal const val PLAYED_HISTORY_KEEP_COUNT = 50
    }

    private fun normalizeTrackIdKey(id: String): String? {
        val normalized = id.trim().removePrefix("ytm_").lowercase()
        return normalized.takeIf { it.isNotEmpty() }
    }

    private fun normalizeTitleArtistKey(track: TrackItem): String? {
        val title = track.title.trim().lowercase()
        val artist = track.artist.trim().lowercase()
        return if (title.isNotEmpty() && artist.isNotEmpty()) "$title||$artist" else null
    }
}

/**
 * Pure Kotlin progressive-queue coordinator backing [AudioPlayerService].
 * Handles speculative prefetch near queue end, epoch invalidation on queue replacement,
 * single in-flight deduplication, offline fallback, and seamless end-of-queue advancement.
 */
internal class ProgressiveQueueManager(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val queueController: PlaybackQueueController,
    private val fetchRemoteSimilar: suspend (seedTracks: List<TrackItem>, excludeIds: Set<String>) -> List<TrackItem>,
    private val fetchFallbackSimilar: (seedTracks: List<TrackItem>, excludeIds: Set<String>) -> List<TrackItem> = { _, _ -> emptyList() },
    private val onQueueUpdated: (PlaybackQueueController.Snapshot) -> Unit = {},
    private val onAutoAdvance: (TrackItem, PlaybackQueueController.Snapshot) -> Unit = { _, _ -> },
    private val onQueueEndedExhausted: () -> Unit = {},
    private val log: (String) -> Unit = {}
) {
    private data class InFlightAppend(
        val epoch: Long,
        val deferred: Deferred<Boolean>
    )

    private var inFlightAppend: InFlightAppend? = null
    private var endAdvanceJob: Job? = null
    private var lastPrefetchedTailKey: Pair<Long, String?>? = null

    fun onQueueReplaced() {
        inFlightAppend?.deferred?.cancel()
        inFlightAppend = null
        endAdvanceJob?.cancel()
        endAdvanceJob = null
        lastPrefetchedTailKey = null
    }

    fun cancelEndAdvance() {
        endAdvanceJob?.cancel()
        endAdvanceJob = null
    }

    fun maybePrefetch(repeatMode: PlaybackRepeatMode) {
        if (!queueController.shouldPrefetchNext(repeatMode)) return
        val epoch = queueController.queueEpoch
        val tailKey = epoch to queueController.tailTrackId()
        if (lastPrefetchedTailKey == tailKey) return
        if (inFlightAppend?.let { it.epoch == epoch && it.deferred.isActive } == true) return
        lastPrefetchedTailKey = tailKey

        scope.launch {
            val deferred = ensureAppendSimilarAsync(epoch)
            val appended = runCatching { deferred.await() }.getOrDefault(false)
            if (appended && epoch == queueController.queueEpoch && endAdvanceJob?.isActive != true) {
                onQueueUpdated(queueController.snapshot())
            }
        }
    }

    fun resolveAndAdvanceFromEnd(repeatMode: PlaybackRepeatMode) {
        if (repeatMode != PlaybackRepeatMode.Off || queueController.isEmpty()) return
        if (endAdvanceJob?.isActive == true) return

        val expectedEpoch = queueController.queueEpoch
        endAdvanceJob = scope.launch {
            try {
                queueController.skipNext(repeatMode)?.let { readyNext ->
                    onAutoAdvance(readyNext, queueController.snapshot())
                    return@launch
                }

                val deferred = ensureAppendSimilarAsync(expectedEpoch)
                runCatching { deferred.await() }

                if (expectedEpoch != queueController.queueEpoch) return@launch
                val nextTrack = queueController.skipNext(repeatMode)
                if (nextTrack != null) {
                    onAutoAdvance(nextTrack, queueController.snapshot())
                } else {
                    onQueueEndedExhausted()
                }
            } finally {
                if (endAdvanceJob == coroutineContext[Job]) {
                    endAdvanceJob = null
                }
            }
        }
    }

    private fun ensureAppendSimilarAsync(expectedEpoch: Long): Deferred<Boolean> {
        val current = inFlightAppend
        if (current != null && current.epoch == expectedEpoch && current.deferred.isActive) {
            return current.deferred
        }

        val deferred = scope.async(start = CoroutineStart.LAZY) {
            try {
                if (expectedEpoch != queueController.queueEpoch || queueController.isEmpty()) {
                    return@async false
                }
                val seeds = queueController.seedTracksForSimilar()
                val excludeIds = queueController.buildExcludeTrackIds()

                val remoteCandidates = try {
                    withContext(ioDispatcher) { fetchRemoteSimilar(seeds, excludeIds) }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    log("Remote similar tracks failed: ${e.message}")
                    emptyList()
                }

                if (expectedEpoch != queueController.queueEpoch || queueController.isEmpty()) {
                    return@async false
                }

                val uniqueRemote = queueController.filterUniqueCandidates(remoteCandidates)
                val finalCandidates = if (uniqueRemote.isNotEmpty()) {
                    uniqueRemote
                } else {
                    val refreshedExcludeIds = queueController.buildExcludeTrackIds()
                    val fallbackCandidates = try {
                        withContext(ioDispatcher) { fetchFallbackSimilar(seeds, refreshedExcludeIds) }
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (e: Exception) {
                        log("Fallback similar tracks failed: ${e.message}")
                        emptyList()
                    }
                    if (expectedEpoch != queueController.queueEpoch || queueController.isEmpty()) {
                        return@async false
                    }
                    queueController.filterUniqueCandidates(fallbackCandidates)
                }

                val snapshot = queueController.appendSimilarTracks(expectedEpoch, finalCandidates)
                if (snapshot != null) {
                    log("ProgressiveQueue appended ${finalCandidates.size} tracks (total=${snapshot.queue.size})")
                    true
                } else {
                    false
                }
            } finally {
                if (inFlightAppend?.deferred === coroutineContext[Job]) {
                    inFlightAppend = null
                }
            }
        }
        inFlightAppend = InFlightAppend(expectedEpoch, deferred)
        deferred.start()
        return deferred
    }
}
