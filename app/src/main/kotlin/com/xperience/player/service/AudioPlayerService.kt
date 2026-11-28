package com.xperience.player.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
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
import com.xperience.player.domain.model.DataLimitReachedEvent
import com.xperience.player.domain.model.DataUsageCategory
import com.xperience.player.domain.model.NetworkConnectionType
import com.xperience.player.domain.model.PlaybackRepeatMode
import com.xperience.player.domain.model.PlaybackSourceType
import com.xperience.player.domain.model.PlaybackState
import com.xperience.player.domain.model.StreamingAudioQuality
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.domain.model.TrackPlaybackInfo
import com.xperience.player.service.dsp.AudioDspController
import com.xperience.player.service.dsp.SleepTimerManager
import java.io.File
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
    val playbackSettings: PlaybackSettingsManager = PlaybackSettingsManager.getInstance(appContext)
    val dataUsageTracker: DataUsageTracker = DataUsageTracker.getInstance(appContext)
    private val flacStreamResolver = FlacStreamResolver(appContext)

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

    private val _currentTrackPlaybackInfo = MutableStateFlow<TrackPlaybackInfo?>(null)
    val currentTrackPlaybackInfo: StateFlow<TrackPlaybackInfo?> = _currentTrackPlaybackInfo.asStateFlow()

    var onTrackPlayed: ((TrackItem) -> Unit)? = null
    var onDataLimitReached: ((DataLimitReachedEvent) -> Unit)? = null

    private data class PreloadedNextTrack(
        val track: TrackItem,
        val uri: Uri,
        val playbackInfo: TrackPlaybackInfo
    )
    @Volatile private var preloadedNextTrack: PreloadedNextTrack? = null
    @Volatile private var gaplessQueuedTrackId: String? = null
    @Volatile private var isPlayingVideoAudioVariant: Boolean = false

    private val systemVolumeObserver = SystemVolumeObserver(
        appContext = appContext,
        isFeatureEnabled = { playbackSettings.preferences.value.pauseAtZeroVolumeEnabled },
        isCurrentlyPlaying = { _playbackState.value.isPlaying || exoPlayer.isPlaying },
        onAutoPause = { pauseInternal(isManual = false) },
        onAutoResume = { resumeInternal(isManual = false) }
    )

    /**
     * Optional offline/catalog similar-track resolver injected by [com.xperience.player.ui.home.HomeViewModel]
     * used when [YouTubeMusicService.fetchSimilarTracksForQueue] returns empty or throws offline.
     */
    var fallbackSimilarTracksProvider: ((seedTracks: List<TrackItem>, excludeIds: Set<String>) -> List<TrackItem>)? = null

    private val sessionStore = PlaybackSessionStore(appContext)
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
            scheduleSessionSave()
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
    private var nextTrackPreloadJob: Job? = null
    private var sessionSaveJob: Job? = null
    @Volatile private var lastSessionSaveWallMs: Long = 0L
    @Volatile private var preloadedNextTrackId: String? = null
    @Volatile private var consecutiveAutoSkipFailures: Int = 0
    @Volatile private var isLoadingMedia: Boolean = false
    @Volatile private var preparedForCurrentSession: Boolean = false
    @Volatile private var activeLoadingSessionId: Long = 0L

    init {
        MediaNetworkClients.attachAppContext(appContext)
        systemVolumeObserver.start()
        restorePersistedSessionIfAvailable()

        scope.launch {
            dataUsageTracker.limitReachedEvents.collect { event ->
                val current = _playbackState.value.currentTrack
                val isPlayingOnline = current != null && !isLocalPlayableTrack(current)
                if (isPlayingOnline) {
                    pauseInternal(isManual = false)
                }
                showDataLimitReachedNotification(event)
                onDataLimitReached?.invoke(event)
            }
        }

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
            override fun onTracksChanged(tracks: Tracks) {
                enrichCurrentTrackInfoFromExoPlayer(tracks)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && mediaItem != null) {
                    val preloaded = preloadedNextTrack
                    if (preloaded != null && mediaItem.mediaId == preloaded.track.id) {
                        handleGaplessAutoTransition(preloaded)
                    }
                }
            }
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
        preloadedNextTrack = null
        if (gaplessQueuedTrackId != null) {
            gaplessQueuedTrackId = null
            runCatching {
                while (exoPlayer.mediaItemCount > 1) {
                    exoPlayer.removeMediaItem(exoPlayer.mediaItemCount - 1)
                }
            }
        }
    }

    private fun handleGaplessAutoTransition(preloaded: PreloadedNextTrack) {
        val repeatMode = _playbackState.value.repeatMode
        val advanced = queueController.skipNext(repeatMode)
        val snapshot = queueController.snapshot()
        val activeTrack = advanced ?: preloaded.track
        preloadedNextTrack = null
        preloadedNextTrackId = null
        gaplessQueuedTrackId = null

        // Remove the finished previous item from ExoPlayer timeline so index 0 stays current
        runCatching {
            if (exoPlayer.mediaItemCount > 1 && exoPlayer.currentMediaItemIndex > 0) {
                exoPlayer.removeMediaItem(0)
            }
        }

        val isLocal = isLocalPlayableTrack(activeTrack)
        dataUsageTracker.setActiveTrackContext(activeTrack, isLocal = isLocal)
        _currentTrackPlaybackInfo.value = preloaded.playbackInfo
        if (!isLocal) {
            dataUsageTracker.recordStreamPlay(
                track = activeTrack,
                isLocal = false,
                isDownloaded = false,
                qualityLabel = preloaded.playbackInfo.actualQualityLabel,
                sourceLabel = preloaded.playbackInfo.sourceType.displayName
            )
        }
        _playbackPositionMs.value = 0L
        _playbackState.update {
            it.copy(
                queue = snapshot.queue,
                currentQueueIndex = snapshot.currentIndex,
                currentTrack = activeTrack,
                currentPositionMs = 0L,
                durationMs = exoPlayer.duration.takeIf { d -> d != C.TIME_UNSET && d > 0L } ?: activeTrack.durationMs,
                isLoading = false
            )
        }
        onTrackPlayed?.invoke(activeTrack)
        updateSessionLayout()
        scheduleSessionSave()
        progressiveQueueManager.maybePrefetch(repeatMode)
    }

    private fun enrichCurrentTrackInfoFromExoPlayer(tracks: Tracks) {
        val current = _currentTrackPlaybackInfo.value ?: return
        for (group in tracks.groups) {
            if (group.type != C.TRACK_TYPE_AUDIO || group.length == 0) continue
            val selectedIdx = (0 until group.length).firstOrNull { group.isTrackSelected(it) } ?: 0
            val fmt = group.getTrackFormat(selectedIdx)
            val codec = when {
                fmt.sampleMimeType?.contains("flac", true) == true -> "FLAC"
                fmt.sampleMimeType?.contains("alac", true) == true -> "ALAC"
                fmt.sampleMimeType?.contains("opus", true) == true -> "Opus"
                fmt.sampleMimeType?.contains("mp4a", true) == true ||
                    fmt.codecs?.startsWith("mp4a", true) == true -> "AAC"
                fmt.sampleMimeType?.contains("mpeg", true) == true -> "MP3"
                fmt.sampleMimeType?.contains("vorbis", true) == true -> "Vorbis"
                fmt.sampleMimeType?.contains("raw", true) == true ||
                    fmt.sampleMimeType?.contains("wav", true) == true -> "PCM"
                else -> current.codec
            }
            val effectiveDuration = current.durationMs.takeIf { it > 0L }
                ?: exoPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0L }
                ?: 0L
            val bitrateKbps = when {
                fmt.averageBitrate > 0 -> (fmt.averageBitrate / 1000).coerceAtLeast(1)
                fmt.bitrate > 0 -> (fmt.bitrate / 1000).coerceAtLeast(1)
                current.bitrateKbps > 0 -> current.bitrateKbps
                current.fileSizeBytes > 0L && effectiveDuration > 0L ->
                    FlacStreamResolver.computeBitrateKbps(current.fileSizeBytes, effectiveDuration)
                else -> 0
            }
            val sampleRate = fmt.sampleRate.takeIf { it > 0 } ?: current.sampleRateHz
            val channels = fmt.channelCount.takeIf { it > 0 } ?: current.channelCount
            val isLosslessCodec = codec.equals("FLAC", ignoreCase = true) ||
                codec.equals("ALAC", ignoreCase = true) ||
                codec.equals("PCM", ignoreCase = true) ||
                current.isLossless
            val bitDepth = if (isLosslessCodec) {
                when (fmt.pcmEncoding) {
                    C.ENCODING_PCM_8BIT -> 8
                    C.ENCODING_PCM_16BIT -> 16
                    C.ENCODING_PCM_24BIT -> 24
                    C.ENCODING_PCM_32BIT -> 32
                    else -> current.bitDepth
                }
            } else {
                // Never claim 16-bit/24-bit Hi-Res PCM bit depth for lossy AAC/Opus/MP3 streams
                0
            }
            val effectiveCodec = codec.ifBlank { current.codec }
            val updatedQualityLabel = TrackPlaybackInfo.buildAccurateQualityLabel(
                codec = effectiveCodec,
                bitrateKbps = bitrateKbps,
                sampleRateHz = sampleRate,
                bitDepth = bitDepth,
                isLossless = isLosslessCodec,
                isFlacFallback = current.isFlacFallback
            ).ifBlank { current.actualQualityLabel }

            val updatedProviderLabel = if (
                current.sourceType == PlaybackSourceType.YOUTUBE_MUSIC ||
                current.sourceType == PlaybackSourceType.YOUTUBE_VIDEO
            ) {
                TrackPlaybackInfo.buildAccurateYouTubeProviderLabel(
                    isVideoFallback = isPlayingVideoAudioVariant || current.sourceType == PlaybackSourceType.YOUTUBE_VIDEO,
                    isAuthenticated = current.isAuthenticatedStream,
                    bitrateKbps = bitrateKbps
                )
            } else {
                current.providerLabel
            }

            _currentTrackPlaybackInfo.value = current.copy(
                codec = effectiveCodec,
                bitrateKbps = bitrateKbps,
                sampleRateHz = sampleRate,
                channelCount = channels,
                bitDepth = bitDepth,
                actualQualityLabel = updatedQualityLabel,
                providerLabel = updatedProviderLabel,
                outputDeviceLabel = current.outputDeviceLabel.ifBlank { getActiveAudioOutputDeviceLabel() }
            )
            return
        }
    }

    private fun showDataLimitReachedNotification(event: DataLimitReachedEvent) {
        runCatching {
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val channel = NotificationChannel(
                DATA_LIMIT_CHANNEL_ID,
                appContext.getString(R.string.data_limit_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = appContext.getString(R.string.data_limit_channel_description)
            }
            nm.createNotificationChannel(channel)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            val networkLabel = if (event.networkType == NetworkConnectionType.MOBILE) {
                appContext.getString(R.string.data_usage_mobile)
            } else {
                appContext.getString(R.string.data_usage_wifi)
            }
            val body = appContext.getString(
                R.string.data_limit_notification_body,
                networkLabel,
                DataUsageTracker.formatBytes(event.usedBytes),
                DataUsageTracker.formatBytes(event.limitBytes)
            )
            val notification = NotificationCompat.Builder(appContext, DATA_LIMIT_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(appContext.getString(R.string.data_limit_notification_title))
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(appContext).notify(DATA_LIMIT_NOTIFICATION_ID, notification)
        }.onFailure { e ->
            Log.w(TAG, "Failed to post data limit notification: ${e.message}")
        }
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
        val isShuffleOn = _playbackState.value.isShuffleEnabled
        val snapshot = if (isShuffleOn && tracks.size > 1) {
            queueController.setShuffledQueue(tracks, startIndex = startIndex)
        } else {
            queueController.setQueue(tracks, startIndex = startIndex)
        }
        if (snapshot.queue.isEmpty() || snapshot.currentTrack == null) {
            _playbackState.update { it.copy(queue = emptyList(), currentQueueIndex = -1) }
            scheduleSessionSave()
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

    fun playShuffledQueue(
        tracks: List<TrackItem>,
        startIndex: Int = -1
    ) {
        if (tracks.isEmpty()) return
        consecutiveAutoSkipFailures = 0
        cancelNextTrackPreload()
        progressiveQueueManager.onQueueReplaced()
        val snapshot = queueController.setShuffledQueue(tracks, startIndex = startIndex)
        val activeTrack = snapshot.currentTrack ?: return
        _playbackState.update { it.copy(isShuffleEnabled = true) }
        exoPlayer.shuffleModeEnabled = true
        updateSessionLayout()
        playResolvedTrack(
            track = activeTrack,
            snapshot = snapshot,
            startPositionMs = 0L,
            autoPlay = true
        )
        progressiveQueueManager.maybePrefetch(_playbackState.value.repeatMode)
    }

    fun playNext(track: TrackItem) {
        val wasEmpty = queueController.isEmpty()
        val snapshot = queueController.enqueuePlayNext(track)
        if (wasEmpty && snapshot.currentTrack != null) {
            playResolvedTrack(snapshot.currentTrack, snapshot)
            progressiveQueueManager.maybePrefetch(_playbackState.value.repeatMode)
            return
        }
        cancelNextTrackPreload()
        _playbackState.update {
            it.copy(
                queue = snapshot.queue,
                currentQueueIndex = snapshot.currentIndex
            )
        }
        scheduleSessionSave()
        if (_playbackState.value.durationMs > 0L) {
            maybePreloadNextTrack(_playbackPositionMs.value, _playbackState.value.durationMs)
        }
    }

    fun addToQueue(track: TrackItem) {
        val wasEmpty = queueController.isEmpty()
        val snapshot = queueController.addToQueue(track)
        if (wasEmpty && snapshot.currentTrack != null) {
            playResolvedTrack(snapshot.currentTrack, snapshot)
            progressiveQueueManager.maybePrefetch(_playbackState.value.repeatMode)
            return
        }
        _playbackState.update {
            it.copy(
                queue = snapshot.queue,
                currentQueueIndex = snapshot.currentIndex
            )
        }
        scheduleSessionSave()
        if (_playbackState.value.durationMs > 0L) {
            maybePreloadNextTrack(_playbackPositionMs.value, _playbackState.value.durationMs)
        }
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
        scheduleSessionSave()
        progressiveQueueManager.maybePrefetch(_playbackState.value.repeatMode)
    }

    fun removeQueueItem(index: Int) {
        val wasActive = index == queueController.currentQueueIndex()
        val snapshot = queueController.removeQueueItem(index) ?: return
        cancelNextTrackPreload()
        if (snapshot.queue.isEmpty() || snapshot.currentTrack == null) {
            pauseInternal(isManual = false)
            runCatching { exoPlayer.clearMediaItems() }
            _playbackPositionMs.value = 0L
            _playbackState.update {
                it.copy(
                    currentTrack = null,
                    queue = emptyList(),
                    currentQueueIndex = -1,
                    currentPositionMs = 0L,
                    durationMs = 0L,
                    isPlaying = false,
                    isLoading = false
                )
            }
            scheduleSessionSave()
            return
        }
        if (wasActive) {
            playResolvedTrack(snapshot.currentTrack, snapshot, autoPlay = _playbackState.value.isPlaying)
        } else {
            _playbackState.update {
                it.copy(
                    queue = snapshot.queue,
                    currentQueueIndex = snapshot.currentIndex
                )
            }
            scheduleSessionSave()
        }
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
        isPlayingVideoAudioVariant = track.id.startsWith("ytm_video_")
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
        scheduleSessionSave()
        ensureServiceStarted()

        audioLoadJob?.cancel()

        if (isLocalPlayableTrack(track)) {
            Log.d(TAG, "Playing local audio: ${track.audioUrl}")
            dataUsageTracker.setActiveTrackContext(track, isLocal = true, isDownloaded = false)
            val localUri = Uri.parse(track.audioUrl)
            _currentTrackPlaybackInfo.value = buildInitialLocalPlaybackInfo(track, localUri)
            startPlaybackPrepared(track, localUri, sessionId, initialPos, autoPlay)
            // Extract detailed local audio metadata (bitrate/sample rate/year/genre/FLAC bit depth) off-main
            audioLoadJob = scope.launch(Dispatchers.IO) {
                val detailed = extractDetailedLocalPlaybackInfo(track, localUri)
                withContext(Dispatchers.Main.immediate) {
                    if (activeLoadingSessionId == sessionId) {
                        _currentTrackPlaybackInfo.value = detailed
                    }
                }
            }
        } else {
            val activeNetwork = playbackSettings.getActiveNetworkType()
            val requestedQuality = playbackSettings.getEffectiveStreamingQuality(activeNetwork)
            dataUsageTracker.setActiveTrackContext(track, isLocal = false, isDownloaded = false)

            // Check if data usage limit is already exceeded on the current network
            if (dataUsageTracker.isLimitExceeded(activeNetwork)) {
                Log.w(TAG, "Online playback blocked: data usage limit exceeded on $activeNetwork")
                isLoadingMedia = false
                releaseTransitionWakeLock()
                _playbackState.update { it.copy(isPlaying = false, isLoading = false) }
                dataUsageTracker.emitLimitReachedNow(activeNetwork)
                return
            }

            // Asynchronously resolve & download audio (FLAC Lossless or selected YTM quality tier)
            val rawVideoId = extractOnlineVideoId(track)

            audioLoadJob = scope.launch(Dispatchers.IO) {
                try {
                    val resolved = resolveOnlineTrackAudio(
                        track = track,
                        rawVideoId = rawVideoId,
                        requestedQuality = requestedQuality,
                        activeNetwork = activeNetwork,
                        sessionId = sessionId
                    )

                    withContext(Dispatchers.Main.immediate) {
                        if (activeLoadingSessionId != sessionId) return@withContext
                        if (resolved != null) {
                            _currentTrackPlaybackInfo.value = resolved.playbackInfo
                            dataUsageTracker.recordStreamPlay(
                                track = track,
                                isLocal = false,
                                isDownloaded = false,
                                qualityLabel = resolved.playbackInfo.actualQualityLabel,
                                sourceLabel = resolved.playbackInfo.sourceType.displayName
                            )
                            startPlaybackPrepared(track, resolved.uri, sessionId, initialPos, autoPlay)
                        } else {
                            handleOnlineDownloadFailure(
                                reason = "Could not resolve online audio for ${track.title}",
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

    private suspend fun resolveOnlineTrackAudio(
        track: TrackItem,
        rawVideoId: String?,
        requestedQuality: StreamingAudioQuality,
        activeNetwork: NetworkConnectionType,
        sessionId: Long? = null
    ): PreloadedNextTrack? {
        var videoIdToPlay = rawVideoId
        if (videoIdToPlay.isNullOrBlank()) {
            val searchResult = ytmService.searchSongs("${track.artist} - ${track.title}")
            videoIdToPlay = searchResult.firstOrNull()?.id?.removePrefix("ytm_")
        }

        val cacheKey = videoIdToPlay?.takeIf { it.isNotBlank() } ?: track.id

        // 1. If FLAC Lossless requested, attempt direct verified FLAC streaming first
        var flacFallback = false
        if (requestedQuality == StreamingAudioQuality.FLAC_LOSSLESS) {
            val flacResult = flacStreamResolver.resolveAndCacheFlacStream(track, cacheKey)
            if (flacResult != null) {
                if (flacResult.downloadedBytes > 0L) {
                    dataUsageTracker.recordNetworkBytes(
                        bytes = flacResult.downloadedBytes,
                        category = DataUsageCategory.AUDIO,
                        networkType = activeNetwork,
                        track = track,
                        isLocal = false,
                        qualityLabel = "FLAC Lossless",
                        sourceLabel = flacResult.sourceType.displayName
                    )
                }
                val info = TrackPlaybackInfo(
                    trackId = track.id,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    durationMs = track.durationMs,
                    sourceType = flacResult.sourceType,
                    providerLabel = flacResult.providerLabel,
                    requestedQuality = requestedQuality,
                    actualQualityLabel = "FLAC Lossless (${flacResult.bitDepth}-bit / ${flacResult.sampleRateHz / 1000} kHz)",
                    isFlacFallback = false,
                    codec = "FLAC",
                    bitrateKbps = flacResult.bitrateKbps,
                    sampleRateHz = flacResult.sampleRateHz,
                    channelCount = flacResult.channelCount,
                    bitDepth = flacResult.bitDepth,
                    fileSizeBytes = flacResult.fileSizeBytes,
                    filePathOrUri = flacResult.file.absolutePath,
                    isrc = flacResult.isrc,
                    networkType = activeNetwork,
                    servedFromCache = flacResult.servedFromCache
                )
                return PreloadedNextTrack(track, Uri.fromFile(flacResult.file), info)
            }
            flacFallback = true
        }

        // 2. YouTube Music resolution with selected quality tier (or High fallback if FLAC unavailable)
        if (videoIdToPlay.isNullOrBlank()) return null
        val ytmQuality = if (requestedQuality == StreamingAudioQuality.FLAC_LOSSLESS) {
            StreamingAudioQuality.HIGH_160
        } else {
            requestedQuality
        }

        val useCanonicalAtv = !track.id.startsWith("ytm_video_")
        var ytmResult = ytmService.getOrDownloadAudioFileWithInfo(
            videoIdOrId = videoIdToPlay,
            context = appContext,
            targetQuality = ytmQuality,
            resolveCanonicalAtv = useCanonicalAtv
        )
        if (ytmResult == null || !ytmResult.file.exists() || ytmResult.file.length() <= 50_000) {
            delay(DOWNLOAD_RETRY_DELAY_MS)
            if (sessionId != null && activeLoadingSessionId != sessionId) return null
            ytmResult = ytmService.getOrDownloadAudioFileWithInfo(
                videoIdOrId = videoIdToPlay,
                context = appContext,
                targetQuality = ytmQuality,
                resolveCanonicalAtv = useCanonicalAtv
            )
        }

        val finalYtm = ytmResult?.takeIf { it.file.exists() && it.file.length() > 50_000 } ?: return null
        val sourceType = if (finalYtm.isVideoFallback) PlaybackSourceType.YOUTUBE_VIDEO else PlaybackSourceType.YOUTUBE_MUSIC
        val effectiveCodec = finalYtm.codec.ifBlank { "AAC" }
        val qualityLabel = TrackPlaybackInfo.buildAccurateQualityLabel(
            codec = effectiveCodec,
            bitrateKbps = finalYtm.bitrateKbps,
            sampleRateHz = finalYtm.sampleRateHz,
            bitDepth = 0,
            isLossless = false,
            isFlacFallback = flacFallback
        )
        val providerLabel = TrackPlaybackInfo.buildAccurateYouTubeProviderLabel(
            isVideoFallback = finalYtm.isVideoFallback,
            isAuthenticated = finalYtm.usedAuth,
            bitrateKbps = finalYtm.bitrateKbps
        )

        if (finalYtm.downloadedBytes > 0L) {
            dataUsageTracker.recordNetworkBytes(
                bytes = finalYtm.downloadedBytes,
                category = DataUsageCategory.AUDIO,
                networkType = activeNetwork,
                track = track,
                isLocal = false,
                qualityLabel = qualityLabel,
                sourceLabel = sourceType.displayName
            )
        }

        val info = TrackPlaybackInfo(
            trackId = track.id,
            title = track.title,
            artist = track.artist,
            album = track.album,
            durationMs = track.durationMs,
            sourceType = sourceType,
            providerLabel = providerLabel,
            requestedQuality = requestedQuality,
            actualQualityLabel = qualityLabel,
            isFlacFallback = flacFallback,
            codec = effectiveCodec,
            bitrateKbps = finalYtm.bitrateKbps,
            sampleRateHz = finalYtm.sampleRateHz,
            channelCount = finalYtm.channelCount,
            fileSizeBytes = finalYtm.file.length(),
            filePathOrUri = finalYtm.file.absolutePath,
            networkType = activeNetwork,
            servedFromCache = finalYtm.servedFromCache,
            requestedQualityLabel = requestedQuality.displayName,
            outputDeviceLabel = getActiveAudioOutputDeviceLabel(),
            isAuthenticatedStream = finalYtm.usedAuth
        )
        return PreloadedNextTrack(track, Uri.fromFile(finalYtm.file), info)
    }

    /**
     * Returns real active audio output hardware/route name for Song Information telemetry.
     */
    fun getActiveAudioOutputDeviceLabel(): String {
        return runCatching {
            val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                ?: return@runCatching "Phone Speaker"
            val outputs = am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
            val external = outputs.firstOrNull { dev ->
                dev.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    dev.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    dev.type == android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                    dev.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET ||
                    dev.type == android.media.AudioDeviceInfo.TYPE_USB_DEVICE ||
                    dev.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    dev.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    dev.type == android.media.AudioDeviceInfo.TYPE_HDMI
            }
            if (external != null) {
                val product = external.productName?.toString()?.trim().orEmpty()
                if (product.isNotBlank() && !product.equals(android.os.Build.MODEL, ignoreCase = true)) {
                    product
                } else {
                    when (external.type) {
                        android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                        android.media.AudioDeviceInfo.TYPE_BLE_HEADSET,
                        android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER -> "Bluetooth Audio"
                        android.media.AudioDeviceInfo.TYPE_USB_HEADSET,
                        android.media.AudioDeviceInfo.TYPE_USB_DEVICE -> "USB DAC / Audio"
                        android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
                        android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones"
                        android.media.AudioDeviceInfo.TYPE_HDMI -> "HDMI Audio"
                        else -> "External Audio"
                    }
                }
            } else {
                "Phone Speaker"
            }
        }.getOrDefault("Phone Speaker")
    }

    /**
     * Inspects real local file / disk-cache / configured streaming telemetry for any track
     * (whether currently playing or tapped from a catalog list).
     */
    suspend fun inspectTrackMetadata(track: TrackItem): TrackPlaybackInfo = withContext(Dispatchers.IO) {
        val outputLabel = getActiveAudioOutputDeviceLabel()
        val live = _currentTrackPlaybackInfo.value
        if (live != null && live.trackId == track.id) {
            return@withContext live.copy(
                outputDeviceLabel = live.outputDeviceLabel.ifBlank { outputLabel }
            )
        }

        if (isLocalPlayableTrack(track)) {
            val localUri = Uri.parse(track.audioUrl)
            return@withContext extractDetailedLocalPlaybackInfo(track, localUri).copy(
                outputDeviceLabel = outputLabel
            )
        }

        val activeNetwork = playbackSettings.getActiveNetworkType()
        val requestedQuality = playbackSettings.getEffectiveStreamingQuality(activeNetwork)
        val rawVideoId = extractOnlineVideoId(track)

        if (!rawVideoId.isNullOrBlank()) {
            if (requestedQuality == StreamingAudioQuality.FLAC_LOSSLESS) {
                val cachedFlac = flacStreamResolver.peekCachedFlacStream(track, rawVideoId)
                if (cachedFlac != null) {
                    return@withContext TrackPlaybackInfo(
                        trackId = track.id,
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        durationMs = track.durationMs,
                        sourceType = cachedFlac.sourceType,
                        providerLabel = cachedFlac.providerLabel,
                        requestedQuality = requestedQuality,
                        actualQualityLabel = TrackPlaybackInfo.buildAccurateQualityLabel(
                            codec = "FLAC",
                            bitrateKbps = cachedFlac.bitrateKbps,
                            sampleRateHz = cachedFlac.sampleRateHz,
                            bitDepth = cachedFlac.bitDepth,
                            isLossless = true
                        ),
                        codec = "FLAC",
                        bitrateKbps = cachedFlac.bitrateKbps,
                        sampleRateHz = cachedFlac.sampleRateHz,
                        channelCount = cachedFlac.channelCount,
                        bitDepth = cachedFlac.bitDepth,
                        fileSizeBytes = cachedFlac.fileSizeBytes,
                        filePathOrUri = cachedFlac.file.absolutePath,
                        isrc = cachedFlac.isrc,
                        networkType = activeNetwork,
                        servedFromCache = true,
                        requestedQualityLabel = requestedQuality.displayName,
                        outputDeviceLabel = outputLabel
                    )
                }
            }

            val ytmTier = if (requestedQuality == StreamingAudioQuality.FLAC_LOSSLESS) {
                StreamingAudioQuality.HIGH_160
            } else {
                requestedQuality
            }
            val cachedYtm = ytmService.peekCachedAudioInfo(
                videoIdOrId = rawVideoId,
                context = appContext,
                targetQuality = ytmTier,
                resolveCanonicalAtv = !track.id.startsWith("ytm_video_")
            )
            if (cachedYtm != null) {
                val effectiveCodec = cachedYtm.codec.ifBlank { "AAC" }
                return@withContext TrackPlaybackInfo(
                    trackId = track.id,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    durationMs = track.durationMs,
                    sourceType = if (cachedYtm.isVideoFallback) PlaybackSourceType.YOUTUBE_VIDEO else PlaybackSourceType.YOUTUBE_MUSIC,
                    providerLabel = TrackPlaybackInfo.buildAccurateYouTubeProviderLabel(
                        isVideoFallback = cachedYtm.isVideoFallback,
                        isAuthenticated = cachedYtm.usedAuth,
                        bitrateKbps = cachedYtm.bitrateKbps
                    ),
                    requestedQuality = requestedQuality,
                    actualQualityLabel = TrackPlaybackInfo.buildAccurateQualityLabel(
                        codec = effectiveCodec,
                        bitrateKbps = cachedYtm.bitrateKbps,
                        sampleRateHz = cachedYtm.sampleRateHz,
                        bitDepth = 0,
                        isLossless = false
                    ),
                    codec = effectiveCodec,
                    bitrateKbps = cachedYtm.bitrateKbps,
                    sampleRateHz = cachedYtm.sampleRateHz,
                    channelCount = cachedYtm.channelCount,
                    fileSizeBytes = cachedYtm.file.length(),
                    filePathOrUri = cachedYtm.file.absolutePath,
                    networkType = activeNetwork,
                    servedFromCache = true,
                    requestedQualityLabel = requestedQuality.displayName,
                    outputDeviceLabel = outputLabel,
                    isAuthenticatedStream = cachedYtm.usedAuth
                )
            }
        }

        TrackPlaybackInfo(
            trackId = track.id,
            title = track.title,
            artist = track.artist,
            album = track.album,
            durationMs = track.durationMs,
            sourceType = if (track.id.startsWith("ytm_video_")) PlaybackSourceType.YOUTUBE_VIDEO else PlaybackSourceType.YOUTUBE_MUSIC,
            providerLabel = "YouTube Music",
            requestedQuality = requestedQuality,
            actualQualityLabel = requestedQuality.displayName,
            networkType = activeNetwork,
            servedFromCache = false,
            requestedQualityLabel = requestedQuality.displayName,
            outputDeviceLabel = outputLabel
        )
    }

    /**
     * Switches the active ExoPlayer audio source between Song (ATV / local / FLAC) and Official Music Video (OMV)
     * audio, seeking accurately via the provided [positionMapper] so intros/skits stay aligned.
     */
    suspend fun switchTrackAudioVariant(
        expectedTrackId: String,
        targetVideoId: String,
        useVideoAudio: Boolean,
        positionMapper: (Long) -> Long
    ): Boolean {
        val currentSnapshot = withContext(Dispatchers.Main.immediate) {
            val activeTrack = _playbackState.value.currentTrack ?: return@withContext null
            if (activeTrack.id != expectedTrackId) return@withContext null
            val wasPlaying = _playbackState.value.isPlaying || exoPlayer.playWhenReady
            val currentPos = maxOf(_playbackPositionMs.value, _playbackState.value.currentPositionMs).coerceAtLeast(0L)
            Triple(activeTrack, wasPlaying, currentPos)
        } ?: return false

        val (track, wasPlaying, currentPosMs) = currentSnapshot
        if (!useVideoAudio && !isPlayingVideoAudioVariant && !track.id.startsWith("ytm_video_")) {
            return true
        }
        val mappedTargetPosMs = positionMapper(currentPosMs).coerceAtLeast(0L)

        val resolvedVariant: PreloadedNextTrack = withContext(Dispatchers.IO) {
            if (useVideoAudio) {
                val activeNetwork = playbackSettings.getActiveNetworkType()
                val requestedQuality = playbackSettings.getEffectiveStreamingQuality(activeNetwork)
                val ytmQuality = if (requestedQuality == StreamingAudioQuality.FLAC_LOSSLESS) {
                    StreamingAudioQuality.HIGH_160
                } else {
                    requestedQuality
                }
                val cleanVid = targetVideoId.removePrefix("ytm_video_").removePrefix("ytm_").trim()
                val ytmResult = ytmService.getOrDownloadAudioFileWithInfo(
                    videoIdOrId = cleanVid,
                    context = appContext,
                    targetQuality = ytmQuality,
                    resolveCanonicalAtv = false
                )?.takeIf { it.file.exists() && it.file.length() > 50_000 } ?: return@withContext null

                val qualityLabel = buildString {
                    append(ytmResult.codec.ifBlank { "AAC" })
                    if (ytmResult.bitrateKbps > 0) append(" ${ytmResult.bitrateKbps} kbps")
                }
                if (ytmResult.downloadedBytes > 0L) {
                    dataUsageTracker.recordNetworkBytes(
                        bytes = ytmResult.downloadedBytes,
                        category = DataUsageCategory.AUDIO,
                        networkType = activeNetwork,
                        track = track,
                        isLocal = false,
                        qualityLabel = qualityLabel,
                        sourceLabel = PlaybackSourceType.YOUTUBE_VIDEO.displayName
                    )
                }
                val info = TrackPlaybackInfo(
                    trackId = track.id,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    durationMs = track.durationMs,
                    sourceType = PlaybackSourceType.YOUTUBE_VIDEO,
                    providerLabel = "YouTube Music Video",
                    requestedQuality = requestedQuality,
                    actualQualityLabel = qualityLabel,
                    isFlacFallback = false,
                    codec = ytmResult.codec.ifBlank { "AAC" },
                    bitrateKbps = ytmResult.bitrateKbps,
                    sampleRateHz = ytmResult.sampleRateHz,
                    channelCount = ytmResult.channelCount,
                    fileSizeBytes = ytmResult.file.length(),
                    filePathOrUri = ytmResult.file.absolutePath,
                    networkType = activeNetwork,
                    servedFromCache = ytmResult.servedFromCache
                )
                PreloadedNextTrack(track, Uri.fromFile(ytmResult.file), info)
            } else {
                if (isLocalPlayableTrack(track)) {
                    val localUri = Uri.parse(track.audioUrl)
                    val info = extractDetailedLocalPlaybackInfo(track, localUri)
                    PreloadedNextTrack(track, localUri, info)
                } else {
                    val activeNetwork = playbackSettings.getActiveNetworkType()
                    val requestedQuality = playbackSettings.getEffectiveStreamingQuality(activeNetwork)
                    val canonicalTrackVid = extractOnlineVideoId(track)
                    val songVid = if (!track.id.startsWith("ytm_video_") && !canonicalTrackVid.isNullOrBlank()) {
                        canonicalTrackVid
                    } else {
                        targetVideoId.removePrefix("ytm_video_").removePrefix("ytm_").trim()
                            .takeIf { it.isNotBlank() } ?: canonicalTrackVid
                    }
                    resolveOnlineTrackAudio(
                        track = track,
                        rawVideoId = songVid,
                        requestedQuality = requestedQuality,
                        activeNetwork = activeNetwork,
                        sessionId = activeLoadingSessionId
                    )
                }
            }
        } ?: return false

        return withContext(Dispatchers.Main.immediate) {
            if (_playbackState.value.currentTrack?.id != expectedTrackId) {
                return@withContext false
            }
            val metadata = buildMediaMetadata(track, artworkBytes = null)
            val item = MediaItem.Builder()
                .setMediaId(track.id)
                .setUri(resolvedVariant.uri)
                .setMediaMetadata(metadata)
                .build()
            preparedForCurrentSession = true
            isPlayingVideoAudioVariant = useVideoAudio
            _currentTrackPlaybackInfo.value = resolvedVariant.playbackInfo
            _playbackPositionMs.value = mappedTargetPosMs
            exoPlayer.setMediaItem(item, mappedTargetPosMs)
            exoPlayer.prepare()
            if (wasPlaying) {
                exoPlayer.play()
            } else {
                exoPlayer.pause()
            }
            _playbackState.update {
                it.copy(
                    currentPositionMs = mappedTargetPosMs,
                    isPlaying = wasPlaying,
                    isLoading = false
                )
            }
            updateSessionLayout()
            scheduleSessionSave()
            true
        }
    }

    private fun buildInitialLocalPlaybackInfo(track: TrackItem, uri: Uri): TrackPlaybackInfo {
        val path = uri.toString()
        val isDownloaded = path.contains("Xperience Downloads", ignoreCase = true)
        val ext = path.substringAfterLast('.', "").lowercase()
        val codec = when (ext) {
            "flac" -> "FLAC"
            "mp3" -> "MP3"
            "m4a", "aac" -> "AAC"
            "ogg", "opus" -> "Opus"
            "wav" -> "PCM"
            else -> ""
        }
        return TrackPlaybackInfo(
            trackId = track.id,
            title = track.title,
            artist = track.artist,
            album = track.album,
            durationMs = track.durationMs,
            sourceType = if (isDownloaded) PlaybackSourceType.DOWNLOADED_OFFLINE else PlaybackSourceType.LOCAL,
            providerLabel = if (isDownloaded) "Downloaded Offline" else "Local Device Storage",
            actualQualityLabel = if (codec == "FLAC") "FLAC Lossless (Local)" else codec.ifBlank { "Local Audio" },
            codec = codec,
            filePathOrUri = path
        )
    }

    private fun extractDetailedLocalPlaybackInfo(track: TrackItem, uri: Uri): TrackPlaybackInfo {
        val base = buildInitialLocalPlaybackInfo(track, uri)
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(appContext, uri)
            val bitrateBps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
            val sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 0
            val year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR).orEmpty()
            val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE).orEmpty()
            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE).orEmpty()
            val fileSize = runCatching {
                if (uri.scheme == "file" || uri.scheme == null) {
                    File(uri.path ?: uri.toString()).takeIf { it.exists() }?.length() ?: 0L
                } else {
                    appContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length.coerceAtLeast(0L) } ?: 0L
                }
            }.getOrDefault(0L)

            var bitDepth = 0
            var channels = 2
            var effectiveSampleRate = sampleRate
            if (mime.contains("flac", true) || base.codec == "FLAC") {
                val localFile = (uri.path ?: uri.toString()).let { File(it) }.takeIf { it.exists() }
                localFile?.let { FlacStreamResolver.parseFlacStreamInfo(it) }?.let { flac ->
                    bitDepth = flac.bitDepth
                    channels = flac.channelCount
                    if (effectiveSampleRate <= 0) effectiveSampleRate = flac.sampleRateHz
                }
            }
            val codec = when {
                mime.contains("flac", true) -> "FLAC"
                mime.contains("mpeg", true) || mime.contains("mp3", true) -> "MP3"
                mime.contains("mp4", true) || mime.contains("aac", true) -> "AAC"
                mime.contains("opus", true) -> "Opus"
                mime.contains("ogg", true) || mime.contains("vorbis", true) -> "Vorbis"
                mime.contains("wav", true) -> "PCM"
                else -> base.codec
            }
            val kbps = (bitrateBps / 1000).takeIf { it > 0 }
                ?: if (fileSize > 0L && track.durationMs > 0L) FlacStreamResolver.computeBitrateKbps(fileSize, track.durationMs) else 0
            val qualityLabel = buildString {
                append(codec.ifBlank { "Local Audio" })
                if (codec == "FLAC") {
                    append(" Lossless")
                    if (bitDepth > 0 && effectiveSampleRate > 0) append(" (${bitDepth}-bit / ${effectiveSampleRate / 1000} kHz)")
                } else if (kbps > 0) {
                    append(" $kbps kbps")
                }
            }
            base.copy(
                codec = codec,
                bitrateKbps = kbps,
                sampleRateHz = effectiveSampleRate,
                channelCount = channels,
                bitDepth = bitDepth,
                fileSizeBytes = fileSize,
                releaseYear = year,
                genre = genre,
                actualQualityLabel = qualityLabel
            )
        } catch (_: Exception) {
            base
        } finally {
            runCatching { retriever.release() }
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
    }

    private fun maybePreloadNextTrack(currentPositionMs: Long, durationMs: Long) {
        if (!playbackSettings.preferences.value.gaplessPreloadEnabled) return
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
        if (nextTrack.id == preloadedNextTrackId) return

        // Handle local next track directly for true gapless ExoPlayer transition
        if (isLocalPlayableTrack(nextTrack)) {
            val localUri = Uri.parse(nextTrack.audioUrl)
            preloadedNextTrackId = nextTrack.id
            nextTrackPreloadJob?.cancel()
            nextTrackPreloadJob = scope.launch(Dispatchers.IO) {
                val info = extractDetailedLocalPlaybackInfo(nextTrack, localUri)
                val preloaded = PreloadedNextTrack(nextTrack, localUri, info)
                withContext(Dispatchers.Main.immediate) {
                    enqueueGaplessMediaItem(preloaded)
                }
            }
            return
        }

        val activeNetwork = playbackSettings.getActiveNetworkType()
        if (dataUsageTracker.isLimitExceeded(activeNetwork)) return

        val requestedQuality = playbackSettings.getEffectiveStreamingQuality(activeNetwork)
        val nextVideoId = extractOnlineVideoId(nextTrack) ?: return
        preloadedNextTrackId = nextTrack.id
        nextTrackPreloadJob?.cancel()
        nextTrackPreloadJob = scope.launch(Dispatchers.IO) {
            runCatching {
                Log.d(TAG, "Gapless preloading next online track: ${nextTrack.title} ($nextVideoId, $requestedQuality)")
                val resolved = resolveOnlineTrackAudio(
                    track = nextTrack,
                    rawVideoId = nextVideoId,
                    requestedQuality = requestedQuality,
                    activeNetwork = activeNetwork,
                    sessionId = null
                )
                if (resolved != null) {
                    withContext(Dispatchers.Main.immediate) {
                        enqueueGaplessMediaItem(resolved)
                    }
                }
            }.onFailure { err ->
                if (err is CancellationException) throw err
                Log.w(TAG, "Next track preload skipped for $nextVideoId: ${err.message}")
            }
        }
    }

    private fun enqueueGaplessMediaItem(preloaded: PreloadedNextTrack) {
        if (preloadedNextTrackId != preloaded.track.id) return
        if (!playbackSettings.preferences.value.gaplessPreloadEnabled) return
        if (gaplessQueuedTrackId == preloaded.track.id) return
        if (exoPlayer.mediaItemCount == 0) return

        preloadedNextTrack = preloaded
        gaplessQueuedTrackId = preloaded.track.id
        runCatching {
            while (exoPlayer.mediaItemCount > 1) {
                exoPlayer.removeMediaItem(exoPlayer.mediaItemCount - 1)
            }
            val nextItem = MediaItem.Builder()
                .setMediaId(preloaded.track.id)
                .setUri(preloaded.uri)
                .setMediaMetadata(buildMediaMetadata(preloaded.track, artworkBytes = null))
                .build()
            exoPlayer.addMediaItem(nextItem)
        }.onFailure { e ->
            Log.w(TAG, "Gapless queue append skipped: ${e.message}")
            gaplessQueuedTrackId = null
        }
    }

    private fun buildMediaMetadata(track: TrackItem, artworkBytes: ByteArray?): MediaMetadata {
        val builder = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)

        if (!track.artworkUri.isNullOrBlank()) {
            val normalizedUri = MediaNetworkClients.ArtworkCacheKeyNormalizer.normalizeUrl(track.artworkUri)
            builder.setArtworkUri(Uri.parse(normalizedUri))
        }

        if (artworkBytes != null && artworkBytes.isNotEmpty()) {
            builder.setArtworkData(artworkBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }

        return builder.build()
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

    fun resume() = resumeInternal(isManual = true)

    private fun resumeInternal(isManual: Boolean) {
        if (isManual) systemVolumeObserver.notifyManualPlaybackAction()
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

    fun pause() = pauseInternal(isManual = true)

    private fun pauseInternal(isManual: Boolean) {
        if (isManual) systemVolumeObserver.notifyManualPlaybackAction()
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
        persistSessionAsync()
    }

    fun setPauseAtZeroVolumeEnabled(enabled: Boolean) {
        playbackSettings.setPauseAtZeroVolumeEnabled(enabled)
        systemVolumeObserver.notifyFeatureToggled(enabled)
    }

    fun setGaplessPreloadEnabled(enabled: Boolean) {
        playbackSettings.setGaplessPreloadEnabled(enabled)
        if (!enabled) {
            cancelNextTrackPreload()
        }
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
        scheduleSessionSave()
    }

    fun setRepeatMode(mode: PlaybackRepeatMode) {
        _playbackState.update { it.copy(repeatMode = mode) }
        when (mode) {
            PlaybackRepeatMode.Off -> exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
            PlaybackRepeatMode.RepeatOne -> exoPlayer.repeatMode = Player.REPEAT_MODE_ONE
            PlaybackRepeatMode.RepeatAll -> exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
        }
        updateSessionLayout()
        scheduleSessionSave()
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
        val snapshot = queueController.setShuffleEnabled(next)
        cancelNextTrackPreload()
        _playbackState.update {
            it.copy(
                isShuffleEnabled = next,
                queue = snapshot.queue,
                currentQueueIndex = snapshot.currentIndex
            )
        }
        exoPlayer.shuffleModeEnabled = next
        updateSessionLayout()
        scheduleSessionSave()
        if (_playbackState.value.durationMs > 0L) {
            maybePreloadNextTrack(_playbackPositionMs.value, _playbackState.value.durationMs)
        }
        progressiveQueueManager.maybePrefetch(_playbackState.value.repeatMode)
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
                    val nowWallMs = System.currentTimeMillis()
                    if (nowWallMs - lastSessionSaveWallMs >= PERIODIC_SESSION_SAVE_INTERVAL_MS) {
                        lastSessionSaveWallMs = nowWallMs
                        persistSessionAsync()
                    }
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

    fun persistSessionNow() {
        val snapshot = captureSessionSnapshot()
        if (snapshot.queue.isEmpty()) {
            sessionStore.clear()
        } else {
            sessionStore.save(snapshot)
        }
        lastSessionSaveWallMs = System.currentTimeMillis()
    }

    private fun scheduleSessionSave() {
        sessionSaveJob?.cancel()
        sessionSaveJob = scope.launch {
            delay(350L)
            persistSessionAsync()
        }
    }

    private fun persistSessionAsync() {
        val snapshot = captureSessionSnapshot()
        scope.launch(Dispatchers.IO) {
            if (snapshot.queue.isEmpty()) {
                sessionStore.clear()
            } else {
                sessionStore.save(snapshot)
            }
            lastSessionSaveWallMs = System.currentTimeMillis()
        }
    }

    private fun captureSessionSnapshot(): PersistedPlaybackSession {
        val state = _playbackState.value
        val livePos = if (exoPlayer.mediaItemCount > 0) {
            exoPlayer.currentPosition.coerceAtLeast(0L)
        } else {
            _playbackPositionMs.value.coerceAtLeast(0L)
        }
        val queueSnap = queueController.snapshot()
        return PlaybackSessionStore.buildPersistedSession(
            queue = queueSnap.queue,
            canonicalQueue = queueController.canonicalQueue(),
            currentIndex = queueSnap.currentIndex,
            positionMs = livePos,
            durationMs = state.durationMs,
            isShuffleEnabled = state.isShuffleEnabled,
            repeatMode = state.repeatMode
        )
    }

    private fun restorePersistedSessionIfAvailable() {
        val restored = sessionStore.load() ?: return
        if (restored.queue.isEmpty() || restored.currentQueueIndex !in restored.queue.indices) return
        val snapshot = queueController.restoreSession(
            queueTracks = restored.queue,
            canonicalTracks = restored.canonicalQueue,
            activeIndex = restored.currentQueueIndex,
            shuffleEnabled = restored.isShuffleEnabled
        )
        val activeTrack = snapshot.currentTrack ?: return
        val effectiveDuration = restored.durationMs.takeIf { it > 0L } ?: activeTrack.durationMs
        val effectivePos = if (effectiveDuration > 0L) {
            restored.positionMs.coerceIn(0L, effectiveDuration)
        } else {
            restored.positionMs.coerceAtLeast(0L)
        }
        _playbackPositionMs.value = effectivePos
        exoPlayer.shuffleModeEnabled = restored.isShuffleEnabled
        exoPlayer.repeatMode = when (restored.repeatMode) {
            PlaybackRepeatMode.Off -> Player.REPEAT_MODE_OFF
            PlaybackRepeatMode.RepeatOne -> Player.REPEAT_MODE_ONE
            PlaybackRepeatMode.RepeatAll -> Player.REPEAT_MODE_ALL
        }
        _playbackState.value = PlaybackState(
            currentTrack = activeTrack,
            isPlaying = false,
            isLoading = false,
            currentPositionMs = effectivePos,
            durationMs = effectiveDuration,
            repeatMode = restored.repeatMode,
            isShuffleEnabled = restored.isShuffleEnabled,
            queue = snapshot.queue,
            currentQueueIndex = snapshot.currentIndex
        )
    }

    fun release() {
        persistSessionNow()
        sessionSaveJob?.cancel()
        systemVolumeObserver.stop()
        progressiveQueueManager.onQueueReplaced()
        audioLoadJob?.cancel()
        cancelNextTrackPreload()
        stopProgressPolling()
        releaseTransitionWakeLock()
        onTrackPlayed = null
        onDataLimitReached = null
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
        private const val DATA_LIMIT_CHANNEL_ID = "xperience_data_limit_channel"
        private const val DATA_LIMIT_NOTIFICATION_ID = 4207
        private const val TRANSITION_WAKE_LOCK_TAG = "XperiencePlayer:TrackTransition"
        private const val TRANSITION_WAKE_LOCK_TIMEOUT_MS = 60_000L
        private const val DOWNLOAD_RETRY_DELAY_MS = 500L
        internal const val MAX_CONSECUTIVE_AUTO_SKIP_FAILURES = 3
        private const val NEXT_TRACK_PRELOAD_WINDOW_MS = 25_000L
        private const val SHORT_TRACK_IMMEDIATE_PRELOAD_MS = 30_000L
        private const val ACTIVE_UI_POLL_INTERVAL_MS = 33L
        private const val BACKGROUND_POLL_INTERVAL_MS = 1_000L
        private const val PERIODIC_SESSION_SAVE_INTERVAL_MS = 10_000L
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
 * direct slot playback, skip transitions, drag-and-drop reordering, and
 * Shuffle ON/OFF restoration of the original pre-shuffle queue order—even when
 * duplicate track IDs exist in the queue.
 */
internal class PlaybackQueueController {

    data class Snapshot(
        val queue: List<TrackItem>,
        val currentIndex: Int,
        val currentTrack: TrackItem?
    )

    private data class SlotEntry(
        val slotId: Long,
        val track: TrackItem
    )

    private val activeSlots = mutableListOf<SlotEntry>()
    private val canonicalSlots = mutableListOf<SlotEntry>()
    private val pinnedNextSlotIds = LinkedHashSet<Long>()
    private var nextSlotId: Long = 1L
    private var isShuffled: Boolean = false
    private var currentIndex: Int = -1

    var queueEpoch: Long = 0L
        private set

    val size: Int get() = activeSlots.size

    fun isEmpty(): Boolean = activeSlots.isEmpty()

    fun isNotEmpty(): Boolean = activeSlots.isNotEmpty()

    fun currentQueueIndex(): Int = currentIndex

    fun tailTrackId(): String? = activeSlots.lastOrNull()?.track?.id

    fun canonicalQueue(): List<TrackItem> = canonicalSlots.map { it.track }

    fun isShuffleEnabled(): Boolean = isShuffled

    fun snapshot(): Snapshot = Snapshot(
        queue = activeSlots.map { it.track },
        currentIndex = currentIndex,
        currentTrack = activeSlots.getOrNull(currentIndex)?.track
    )

    private fun normalizeQueuedTrack(track: TrackItem): TrackItem {
        val rawArt = track.artworkUri?.trim().orEmpty()
        if (rawArt.isEmpty()) return track
        val normalized = MediaNetworkClients.ArtworkCacheKeyNormalizer.normalizeUrl(rawArt)
        return if (normalized == rawArt) track else track.copy(artworkUri = normalized)
    }

    private fun allocateSlot(track: TrackItem): SlotEntry = SlotEntry(nextSlotId++, normalizeQueuedTrack(track))

    private fun normalizeAlbumArtworkAcrossList(tracks: List<TrackItem>): List<TrackItem> {
        if (tracks.isEmpty()) return tracks
        val canonicalByAlbum = HashMap<Pair<String, String>, String>()
        tracks.forEach { t ->
            val alb = t.album.trim().lowercase()
            val art = t.artist.trim().lowercase()
            val uri = t.artworkUri?.trim().orEmpty()
            if (alb.isNotEmpty() && alb != "unknown album" && alb != "single" && uri.startsWith("http")) {
                val normalized = MediaNetworkClients.ArtworkCacheKeyNormalizer.normalizeUrl(uri)
                canonicalByAlbum.putIfAbsent(alb to art, normalized)
            }
        }
        return tracks.map { t ->
            val alb = t.album.trim().lowercase()
            val art = t.artist.trim().lowercase()
            val shared = if (alb.isNotEmpty()) canonicalByAlbum[alb to art] else null
            val current = t.artworkUri?.trim().orEmpty()
            val effective = when {
                !shared.isNullOrBlank() && (current.isEmpty() || current.contains("googleusercontent.com")) -> shared
                current.isNotEmpty() -> MediaNetworkClients.ArtworkCacheKeyNormalizer.normalizeUrl(current)
                else -> current
            }
            if (effective.isNotEmpty() && effective != current) t.copy(artworkUri = effective) else t
        }
    }

    fun setQueue(tracks: List<TrackItem>, startIndex: Int = 0): Snapshot {
        queueEpoch++
        activeSlots.clear()
        canonicalSlots.clear()
        pinnedNextSlotIds.clear()
        isShuffled = false
        normalizeAlbumArtworkAcrossList(tracks).forEach { track ->
            val entry = allocateSlot(track)
            activeSlots.add(entry)
            canonicalSlots.add(entry)
        }
        currentIndex = if (activeSlots.isEmpty()) -1 else startIndex.coerceIn(0, activeSlots.lastIndex)
        return snapshot()
    }

    fun setShuffledQueue(
        tracks: List<TrackItem>,
        startIndex: Int = -1,
        random: kotlin.random.Random = kotlin.random.Random.Default
    ): Snapshot {
        queueEpoch++
        activeSlots.clear()
        canonicalSlots.clear()
        pinnedNextSlotIds.clear()
        isShuffled = true
        if (tracks.isEmpty()) {
            currentIndex = -1
            return snapshot()
        }
        tracks.forEach { track ->
            canonicalSlots.add(allocateSlot(track))
        }
        val chosenIdx = if (startIndex in canonicalSlots.indices) {
            startIndex
        } else {
            random.nextInt(canonicalSlots.size)
        }
        val chosenEntry = canonicalSlots[chosenIdx]
        val remainder = canonicalSlots.filterIndexed { index, _ -> index != chosenIdx }.toMutableList()
        remainder.shuffle(random)
        activeSlots.add(chosenEntry)
        activeSlots.addAll(remainder)
        currentIndex = 0
        return snapshot()
    }

    fun setShuffleEnabled(
        enabled: Boolean,
        random: kotlin.random.Random = kotlin.random.Random.Default
    ): Snapshot {
        if (enabled == isShuffled) return snapshot()
        isShuffled = enabled

        if (activeSlots.isEmpty()) {
            currentIndex = -1
            return snapshot()
        }

        if (canonicalSlots.isEmpty()) {
            canonicalSlots.addAll(activeSlots)
        }

        val currentEntry = activeSlots.getOrNull(currentIndex)
        if (enabled) {
            if (activeSlots.size <= 1 || currentEntry == null) {
                return snapshot()
            }
            val pinnedEntries = activeSlots.drop(currentIndex + 1).filter { it.slotId in pinnedNextSlotIds }
            val pinnedIds = pinnedEntries.mapTo(HashSet()) { it.slotId }
            val remainder = canonicalSlots.filter {
                it.slotId != currentEntry.slotId && it.slotId !in pinnedIds
            }.toMutableList()
            remainder.shuffle(random)

            activeSlots.clear()
            activeSlots.add(currentEntry)
            activeSlots.addAll(pinnedEntries)
            activeSlots.addAll(remainder)
            currentIndex = 0
        } else {
            activeSlots.clear()
            activeSlots.addAll(canonicalSlots)
            currentIndex = if (currentEntry != null) {
                val bySlot = activeSlots.indexOfFirst { it.slotId == currentEntry.slotId }
                if (bySlot >= 0) {
                    bySlot
                } else {
                    activeSlots.indexOfFirst { it.track.id == currentEntry.track.id }.takeIf { it >= 0 } ?: 0
                }
            } else {
                0
            }
        }
        return snapshot()
    }

    fun restoreSession(
        queueTracks: List<TrackItem>,
        canonicalTracks: List<TrackItem>,
        activeIndex: Int,
        shuffleEnabled: Boolean
    ): Snapshot {
        queueEpoch++
        activeSlots.clear()
        canonicalSlots.clear()
        pinnedNextSlotIds.clear()
        isShuffled = shuffleEnabled

        if (queueTracks.isEmpty()) {
            currentIndex = -1
            return snapshot()
        }

        val safeCanonicalSource = if (canonicalTracks.isNotEmpty()) canonicalTracks else queueTracks
        val remainingCanonicalPool = ArrayList<SlotEntry>(safeCanonicalSource.size)
        safeCanonicalSource.forEach { track ->
            val entry = allocateSlot(track)
            canonicalSlots.add(entry)
            remainingCanonicalPool.add(entry)
        }

        queueTracks.forEach { track ->
            val matchIdx = remainingCanonicalPool.indexOfFirst { it.track.id == track.id && it.track.title == track.title }
                .takeIf { it >= 0 }
                ?: remainingCanonicalPool.indexOfFirst { it.track.id == track.id }
            if (matchIdx >= 0) {
                val matched = remainingCanonicalPool.removeAt(matchIdx)
                val updated = if (matched.track == track) matched else matched.copy(track = track)
                if (updated != matched) {
                    val canonIdx = canonicalSlots.indexOfFirst { it.slotId == matched.slotId }
                    if (canonIdx >= 0) canonicalSlots[canonIdx] = updated
                }
                activeSlots.add(updated)
            } else {
                val extra = allocateSlot(track)
                activeSlots.add(extra)
                canonicalSlots.add(extra)
            }
        }

        currentIndex = activeIndex.coerceIn(0, activeSlots.lastIndex)
        return snapshot()
    }

    /**
     * Replaces the entire queue with a single seed [track] at index 0 and bumps [queueEpoch].
     */
    fun playSingleTrack(track: TrackItem): Snapshot {
        queueEpoch++
        activeSlots.clear()
        canonicalSlots.clear()
        pinnedNextSlotIds.clear()
        val entry = allocateSlot(track)
        activeSlots.add(entry)
        canonicalSlots.add(entry)
        currentIndex = 0
        return snapshot()
    }

    fun playTrack(track: TrackItem): Snapshot {
        val foundIndex = if (currentIndex in activeSlots.indices && activeSlots[currentIndex].track.id == track.id) {
            currentIndex
        } else {
            activeSlots.indexOfFirst { it.track.id == track.id }
        }
        if (foundIndex != -1) {
            currentIndex = foundIndex
            val oldEntry = activeSlots[foundIndex]
            val updated = oldEntry.copy(track = track)
            activeSlots[foundIndex] = updated
            val canonIdx = canonicalSlots.indexOfFirst { it.slotId == oldEntry.slotId }
            if (canonIdx >= 0) canonicalSlots[canonIdx] = updated
        } else {
            val entry = allocateSlot(track)
            activeSlots.add(entry)
            canonicalSlots.add(entry)
            currentIndex = activeSlots.lastIndex
        }
        return snapshot()
    }

    fun enqueuePlayNext(track: TrackItem): Snapshot {
        if (activeSlots.isEmpty()) {
            return playSingleTrack(track)
        }
        val entry = allocateSlot(track)
        val insertIdx = if (currentIndex in activeSlots.indices) currentIndex + 1 else 0
        activeSlots.add(insertIdx.coerceIn(0, activeSlots.size), entry)
        pinnedNextSlotIds.add(entry.slotId)

        val currentSlotId = activeSlots.getOrNull(currentIndex)?.slotId
        val canonIdx = if (currentSlotId != null) {
            canonicalSlots.indexOfFirst { it.slotId == currentSlotId }
        } else {
            -1
        }
        if (canonIdx >= 0) {
            canonicalSlots.add(canonIdx + 1, entry)
        } else {
            canonicalSlots.add(entry)
        }
        if (currentIndex < 0) currentIndex = 0
        return snapshot()
    }

    fun addToQueue(track: TrackItem): Snapshot {
        if (activeSlots.isEmpty()) {
            return playSingleTrack(track)
        }
        val entry = allocateSlot(track)
        activeSlots.add(entry)
        canonicalSlots.add(entry)
        return snapshot()
    }

    fun removeQueueItem(index: Int): Snapshot? {
        if (index !in activeSlots.indices) return null
        val removed = activeSlots.removeAt(index)
        pinnedNextSlotIds.remove(removed.slotId)
        canonicalSlots.removeAll { it.slotId == removed.slotId }
        if (activeSlots.isEmpty()) {
            currentIndex = -1
        } else if (index < currentIndex) {
            currentIndex--
        } else if (index == currentIndex) {
            currentIndex = currentIndex.coerceIn(0, activeSlots.lastIndex)
        }
        return snapshot()
    }

    fun playQueueIndex(index: Int): TrackItem? {
        if (index !in activeSlots.indices) return null
        currentIndex = index
        pinnedNextSlotIds.remove(activeSlots[index].slotId)
        return activeSlots[index].track
    }

    fun updateCurrentTrackDuration(naturalDurationMs: Long): Snapshot? {
        if (naturalDurationMs <= 0L || currentIndex !in activeSlots.indices) return null
        val entry = activeSlots[currentIndex]
        val current = entry.track
        if (kotlin.math.abs(current.durationMs - naturalDurationMs) <= 1000L) return null
        val updated = entry.copy(track = current.copy(durationMs = naturalDurationMs))
        activeSlots[currentIndex] = updated
        val canonIdx = canonicalSlots.indexOfFirst { it.slotId == entry.slotId }
        if (canonIdx >= 0) {
            canonicalSlots[canonIdx] = updated
        }
        return snapshot()
    }

    fun skipNext(repeatMode: PlaybackRepeatMode): TrackItem? {
        if (activeSlots.isEmpty()) return null
        if (currentIndex >= activeSlots.size - 1 && repeatMode == PlaybackRepeatMode.Off) {
            return null
        }
        currentIndex = if (currentIndex < 0) 0 else (currentIndex + 1) % activeSlots.size
        pinnedNextSlotIds.remove(activeSlots[currentIndex].slotId)
        return activeSlots[currentIndex].track
    }

    fun skipPrevious(): TrackItem? {
        if (activeSlots.isEmpty()) return null
        currentIndex = if (currentIndex <= 0) activeSlots.size - 1 else currentIndex - 1
        return activeSlots[currentIndex].track
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int): Boolean {
        if (fromIndex !in activeSlots.indices || toIndex !in activeSlots.indices || fromIndex == toIndex) {
            return false
        }
        val moved = activeSlots.removeAt(fromIndex)
        activeSlots.add(toIndex, moved)

        if (!isShuffled) {
            val canonFrom = canonicalSlots.indexOfFirst { it.slotId == moved.slotId }
            if (canonFrom in canonicalSlots.indices) {
                val targetNeighborId = activeSlots.getOrNull(toIndex + 1)?.slotId
                canonicalSlots.removeAt(canonFrom)
                val canonTarget = if (targetNeighborId != null) {
                    canonicalSlots.indexOfFirst { it.slotId == targetNeighborId }
                        .takeIf { it >= 0 } ?: canonicalSlots.size
                } else {
                    canonicalSlots.size
                }
                canonicalSlots.add(canonTarget.coerceIn(0, canonicalSlots.size), moved)
            }
        }

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
        if (repeatMode != PlaybackRepeatMode.Off || activeSlots.isEmpty() || currentIndex < 0) return false
        return currentIndex >= (activeSlots.lastIndex - thresholdFromEnd).coerceAtLeast(0)
    }

    fun seedTracksForSimilar(maxSeeds: Int = 5): List<TrackItem> {
        if (activeSlots.isEmpty() || maxSeeds <= 0) return emptyList()
        val activeIdx = currentIndex.coerceIn(0, activeSlots.lastIndex)
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

        addIfUnique(activeSlots[activeIdx].track)
        for (idx in (activeIdx - 1) downTo 0) {
            if (seeds.size >= maxSeeds) break
            addIfUnique(activeSlots[idx].track)
        }
        for (idx in activeSlots.lastIndex downTo (activeIdx + 1)) {
            if (seeds.size >= maxSeeds) break
            addIfUnique(activeSlots[idx].track)
        }
        return seeds
    }

    fun buildExcludeTrackIds(): Set<String> {
        if (activeSlots.isEmpty()) return emptySet()
        val ids = LinkedHashSet<String>(activeSlots.size * 2)
        activeSlots.forEach { entry ->
            val trimmed = entry.track.id.trim()
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
        val existingIds = HashSet<String>(activeSlots.size * 2)
        val existingTitleArtists = HashSet<String>(activeSlots.size)
        activeSlots.forEach { entry ->
            normalizeTrackIdKey(entry.track.id)?.let { existingIds.add(it) }
            normalizeTitleArtistKey(entry.track)?.let { existingTitleArtists.add(it) }
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
        if (expectedEpoch != queueEpoch || activeSlots.isEmpty()) return null
        val unique = filterUniqueCandidates(candidates)
        if (unique.isEmpty()) return null
        unique.forEach { track ->
            val entry = allocateSlot(track)
            activeSlots.add(entry)
            canonicalSlots.add(entry)
        }
        if (activeSlots.size > MAX_QUEUE_SIZE && currentIndex > PLAYED_HISTORY_KEEP_COUNT) {
            val trimCount = (currentIndex - PLAYED_HISTORY_KEEP_COUNT).coerceAtMost(activeSlots.size - 1)
            if (trimCount > 0) {
                val removedIds = activeSlots.subList(0, trimCount).mapTo(HashSet()) { it.slotId }
                activeSlots.subList(0, trimCount).clear()
                canonicalSlots.removeAll { it.slotId in removedIds }
                pinnedNextSlotIds.removeAll(removedIds)
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
