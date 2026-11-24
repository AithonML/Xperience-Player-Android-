package com.xperience.player.service.cast

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.Cast
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.xperience.player.domain.model.PlaybackRepeatMode
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.service.YouTubeMusicService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Resolved audio/artwork source ready to be mapped into a Chromecast-playable HTTP URL.
 */
sealed interface ResolvedCastSource {
    val mimeType: String
    val artworkUrl: String?

    data class RemoteUrl(
        val url: String,
        override val mimeType: String,
        override val artworkUrl: String? = null
    ) : ResolvedCastSource

    data class LocalFile(
        val file: File,
        override val mimeType: String,
        override val artworkUrl: String? = null
    ) : ResolvedCastSource

    data class LocalContentUri(
        val uri: Uri,
        override val mimeType: String,
        val suggestedFileName: String = "track.m4a",
        override val artworkUrl: String? = null
    ) : ResolvedCastSource
}

/**
 * Pluggable resolver converting an Xperience [TrackItem] into a [ResolvedCastSource].
 */
fun interface CastMediaSourceResolver {
    suspend fun resolve(track: TrackItem): ResolvedCastSource?
}

/**
 * Central Chromecast engine for Xperience Music Player.
 *
 * Responsibilities:
 * - Safely initializes Google Cast SDK (`CastContext`) with graceful fallback when GMS is unavailable.
 * - Discovers live Cast routes via [MediaRouter] without requiring `AppCompatActivity`.
 * - Bridges local & YouTube-cached tracks to Chromecast via embedded [LocalCastMediaServer].
 * - Performs seamless Local <-> Cast playback handoff on connect and disconnect.
 * - Exposes Compose-friendly [StateFlow]s for device picker, mini banner, and expanded remote controls.
 */
class CastSessionController internal constructor(
    private val appContext: Context?,
    private val localServer: LocalCastMediaServer = LocalCastMediaServer(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    mainDispatcher: CoroutineDispatcher? = null,
    private val gmsAvailabilityCheck: (Context) -> Boolean = ::defaultCheckGmsAvailable,
    private val skipAndroidFrameworkInit: Boolean = false
) {
    private val effectiveMainDispatcher: CoroutineDispatcher by lazy {
        mainDispatcher ?: if (skipAndroidFrameworkInit) {
            Dispatchers.Unconfined
        } else {
            Dispatchers.Main.immediate
        }
    }
    private val mainHandler: Handler? = if (skipAndroidFrameworkInit) {
        null
    } else {
        runCatching { Handler(Looper.getMainLooper()) }.getOrNull()
    }
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val castInitExecutor: ExecutorService? = if (skipAndroidFrameworkInit) {
        null
    } else {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "cast-context-init").apply { isDaemon = true }
        }
    }

    private val _routes = MutableStateFlow<List<CastRouteDevice>>(emptyList())
    val routes: StateFlow<List<CastRouteDevice>> = _routes.asStateFlow()

    private val _castState = MutableStateFlow(CastPlaybackState())
    val castState: StateFlow<CastPlaybackState> = _castState.asStateFlow()

    private val _isCastAvailable = MutableStateFlow(false)
    val isCastAvailable: StateFlow<Boolean> = _isCastAvailable.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    /**
     * Supplies the active local ExoPlayer snapshot when a Chromecast session connects.
     */
    @Volatile
    var localPlaybackStateProvider: (() -> CastHandoffSnapshot?)? = null

    /**
     * Invoked as soon as a Chromecast session starts so local ExoPlayer pauses immediately.
     */
    @Volatile
    var onCastSessionStarted: (() -> Unit)? = null

    /**
     * Invoked when a Chromecast session ends with the last known remote track & position
     * so local ExoPlayer resumes seamlessly without losing progress.
     */
    @Volatile
    var onCastSessionEnded: ((CastHandoffSnapshot) -> Unit)? = null

    /**
     * Optional custom media resolver. Defaults to resolving direct HTTP streams,
     * local `content://` / `file://` files, and cached YouTube Music tracks.
     */
    @Volatile
    var mediaSourceResolver: CastMediaSourceResolver = CastMediaSourceResolver { track ->
        defaultResolveTrackSource(track)
    }

    @Volatile
    private var castContext: CastContext? = null

    @Volatile
    private var sessionManager: SessionManager? = null

    @Volatile
    private var currentSession: CastSession? = null

    @Volatile
    private var mediaRouter: MediaRouter? = null

    @Volatile
    private var routeSelector: MediaRouteSelector? = null

    @Volatile
    private var activeDiscoveryRefCount: Int = 0

    @Volatile
    private var loadMediaJob: Job? = null

    @Volatile
    private var prefetchQueueJob: Job? = null

    private val sessionHandoffDispatched = AtomicBoolean(false)
    private val isReleased = AtomicBoolean(false)
    private val trackIdToCastItemId = ConcurrentHashMap<String, Int>()
    private val queuedTrackIds = ConcurrentHashMap.newKeySet<String>()
    private val routeInfoById = ConcurrentHashMap<String, MediaRouter.RouteInfo>()

    private val mediaRouterCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
            refreshRoutesFromRouter(router)
        }

        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) {
            refreshRoutesFromRouter(router)
        }

        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
            refreshRoutesFromRouter(router)
        }

        override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) {
            refreshRoutesFromRouter(router)
        }

        override fun onRouteUnselected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) {
            refreshRoutesFromRouter(router)
        }

        override fun onRouteVolumeChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
            refreshRoutesFromRouter(router)
        }
    }

    private val castVolumeListener = object : Cast.Listener() {
        override fun onVolumeChanged() {
            val session = currentSession ?: return
            val vol = runCatching { session.volume.toFloat() }.getOrDefault(_castState.value.volume)
            val muted = runCatching { session.isMute }.getOrDefault(_castState.value.isMuted)
            _castState.update { CastStateReducer.onRemoteVolumeChanged(it, vol, muted) }
        }
    }

    private val remoteProgressListener = RemoteMediaClient.ProgressListener { progressMs, durationMs ->
        _castState.update { state ->
            CastStateReducer.onRemoteProgress(
                current = state,
                positionMs = progressMs,
                durationMs = durationMs
            )
        }
    }

    private val remoteMediaCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            syncFromRemoteMediaClient()
        }

        override fun onMetadataUpdated() {
            syncFromRemoteMediaClient()
        }

        override fun onQueueStatusUpdated() {
            syncQueueItemIdsFromRemote()
            syncFromRemoteMediaClient()
        }

        override fun onPreloadStatusUpdated() {
            syncFromRemoteMediaClient()
        }
    }

    private val sessionManagerListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {
            val device = session.castDevice
            _castState.update {
                CastStateReducer.onConnecting(
                    current = it,
                    deviceName = device?.friendlyName,
                    deviceId = device?.deviceId
                )
            }
        }

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            sessionHandoffDispatched.set(false)
            attachSession(session)

            val handoff = runCatching { localPlaybackStateProvider?.invoke() }.getOrNull()
            runCatching { onCastSessionStarted?.invoke() }

            val device = session.castDevice
            val volume = runCatching { session.volume.toFloat() }.getOrDefault(1f)
            val isMuted = runCatching { session.isMute }.getOrDefault(false)

            _castState.update {
                CastStateReducer.onSessionConnected(
                    current = it,
                    deviceName = device?.friendlyName,
                    deviceId = device?.deviceId,
                    volume = volume,
                    isMuted = isMuted,
                    handoff = handoff
                )
            }

            if (handoff != null && handoff.hasPlayableContent) {
                val track = handoff.currentTrack ?: handoff.queue.getOrNull(handoff.currentQueueIndex)
                if (track != null) {
                    loadTrackAndQueueOnCast(
                        track = track,
                        queue = handoff.queue,
                        startIndex = handoff.currentQueueIndex.coerceAtLeast(0),
                        startPositionMs = handoff.positionMs,
                        autoplay = handoff.wasPlaying,
                        repeatMode = handoff.repeatMode,
                        isShuffleEnabled = handoff.isShuffleEnabled
                    )
                }
            } else {
                syncFromRemoteMediaClient()
            }
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            detachSession(session)
            localServer.stop()
            _castState.update {
                it.copy(
                    connectionState = CastConnectionState.DISCONNECTED,
                    deviceName = null,
                    deviceId = null,
                    isBuffering = false,
                    isPlaying = false,
                    errorMessage = "No se pudo conectar con Chromecast (código $error)"
                )
            }
        }

        override fun onSessionEnding(session: CastSession) {
            handleSessionEnded(session, errorMessage = null)
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            val message = if (error != 0) "Se desconectó la transmisión (código $error)" else null
            handleSessionEnded(session, errorMessage = message)
        }

        override fun onSessionResuming(session: CastSession, sessionId: String) {
            _castState.update { CastStateReducer.onReconnecting(it) }
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            sessionHandoffDispatched.set(false)
            attachSession(session)
            val device = session.castDevice
            val volume = runCatching { session.volume.toFloat() }.getOrDefault(1f)
            val isMuted = runCatching { session.isMute }.getOrDefault(false)
            _castState.update {
                CastStateReducer.onSessionConnected(
                    current = it,
                    deviceName = device?.friendlyName,
                    deviceId = device?.deviceId,
                    volume = volume,
                    isMuted = isMuted,
                    handoff = null
                )
            }
            syncQueueItemIdsFromRemote()
            syncFromRemoteMediaClient()
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            handleSessionEnded(session, errorMessage = "No se pudo reconectar con Chromecast")
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {
            _castState.update { CastStateReducer.onReconnecting(it) }
        }
    }

    init {
        if (!skipAndroidFrameworkInit) {
            initializeSafely()
        } else {
            val available = appContext?.let { runCatching { gmsAvailabilityCheck(it) }.getOrDefault(false) } ?: false
            if (!available) {
                markFrameworkUnavailable()
            } else {
                _isCastAvailable.value = true
            }
        }
    }

    private fun initializeSafely() {
        val context = appContext ?: run {
            markFrameworkUnavailable()
            return
        }

        val gmsOk = runCatching { gmsAvailabilityCheck(context) }.getOrDefault(false)
        if (!gmsOk) {
            safeLogW("Google Play Services unavailable — Chromecast gracefully disabled")
            markFrameworkUnavailable()
            return
        }

        val executor = castInitExecutor ?: run {
            markFrameworkUnavailable()
            return
        }

        try {
            val task = CastContext.getSharedInstance(context, executor)
            task.addOnSuccessListener { resolvedContext ->
                if (isReleased.get()) return@addOnSuccessListener
                runOnMain {
                    if (isReleased.get()) return@runOnMain
                    onCastContextReady(context, resolvedContext)
                }
            }.addOnFailureListener { error ->
                safeLogW("CastContext initialization unavailable: ${error.message}")
                markFrameworkUnavailable()
            }
        } catch (t: Throwable) {
            // Protects emulators/devices without GMS or Cast dynamically-loaded modules
            safeLogW("CastContext.getSharedInstance threw ${t.javaClass.simpleName}: ${t.message}")
            markFrameworkUnavailable()
        }
    }

    private fun onCastContextReady(context: Context, resolvedContext: CastContext) {
        try {
            castContext = resolvedContext
            val manager = resolvedContext.sessionManager
            sessionManager = manager
            manager.addSessionManagerListener(sessionManagerListener, CastSession::class.java)

            val selector = MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent.categoryForCast(CastConfig.RECEIVER_APP_ID))
                .build()
            routeSelector = selector

            val router = MediaRouter.getInstance(context)
            mediaRouter = router
            router.addCallback(
                selector,
                mediaRouterCallback,
                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY
            )
            refreshRoutesFromRouter(router)

            _isCastAvailable.value = true

            // Re-attach if a saved session was already active when app launched
            val existingSession = manager.currentCastSession
            if (existingSession != null && existingSession.isConnected) {
                sessionManagerListener.onSessionResumed(existingSession, false)
            }
        } catch (t: Throwable) {
            safeLogW("Failed attaching Cast session/router listeners: ${t.message}")
            markFrameworkUnavailable()
        }
    }

    internal fun markFrameworkUnavailable() {
        _isCastAvailable.value = false
        _isScanning.value = false
        _routes.value = emptyList()
        _castState.update { CastStateReducer.markUnavailable(it) }
    }

    /**
     * Enables active Wi-Fi mDNS Cast scanning while the device picker UI is visible.
     */
    fun startActiveDiscovery() {
        if (!_isCastAvailable.value || isReleased.get()) return
        runOnMain {
            activeDiscoveryRefCount++
            _isScanning.value = true
            val router = mediaRouter ?: return@runOnMain
            val selector = routeSelector ?: return@runOnMain
            runCatching {
                router.removeCallback(mediaRouterCallback)
                router.addCallback(
                    selector,
                    mediaRouterCallback,
                    MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN or MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY
                )
                refreshRoutesFromRouter(router)
            }
        }
    }

    /**
     * Steps down from active scanning to low-power passive discovery when picker closes.
     */
    fun stopActiveDiscovery() {
        if (!_isCastAvailable.value || isReleased.get()) return
        runOnMain {
            activeDiscoveryRefCount = (activeDiscoveryRefCount - 1).coerceAtLeast(0)
            if (activeDiscoveryRefCount > 0) return@runOnMain
            _isScanning.value = false
            val router = mediaRouter ?: return@runOnMain
            val selector = routeSelector ?: return@runOnMain
            runCatching {
                router.removeCallback(mediaRouterCallback)
                router.addCallback(
                    selector,
                    mediaRouterCallback,
                    MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY
                )
            }
        }
    }

    /**
     * Connects to a discovered Cast route by [deviceId].
     */
    fun selectRoute(deviceId: String) {
        if (!_isCastAvailable.value) return
        runOnMain {
            val router = mediaRouter ?: return@runOnMain
            val route = routeInfoById[deviceId]
                ?: router.routes.firstOrNull { it.id == deviceId }
                ?: return@runOnMain

            _castState.update {
                CastStateReducer.onConnecting(
                    current = it,
                    deviceName = route.name,
                    deviceId = route.id
                )
            }
            runCatching { router.selectRoute(route) }
                .onFailure { err ->
                    safeLogW("Failed to select route ${route.name}: ${err.message}")
                    _castState.update {
                        it.copy(
                            connectionState = CastConnectionState.DISCONNECTED,
                            isBuffering = false,
                            errorMessage = "No se pudo seleccionar ${route.name}"
                        )
                    }
                }
        }
    }

    /**
     * Adjusts an individual Cast route's hardware volume directly from the device picker.
     */
    fun setRouteVolume(deviceId: String, volume: Int) {
        runOnMain {
            val route = routeInfoById[deviceId] ?: return@runOnMain
            val clamped = volume.coerceIn(0, route.volumeMax.coerceAtLeast(1))
            runCatching { route.requestSetVolume(clamped) }
        }
    }

    /**
     * Disconnects the active Cast session (returning playback to this phone via [onCastSessionEnded]).
     */
    fun disconnect(stopCasting: Boolean = true) {
        runOnMain {
            val manager = sessionManager
            val router = mediaRouter
            if (manager?.currentCastSession != null) {
                runCatching { manager.endCurrentSession(stopCasting) }
            } else if (router != null) {
                runCatching { router.unselect(MediaRouter.UNSELECT_REASON_STOPPED) }
                handleSessionEnded(currentSession, errorMessage = null)
            } else {
                handleSessionEnded(currentSession, errorMessage = null)
            }
        }
    }

    /**
     * Loads and plays [track] (plus its surrounding [queue]) on the active Chromecast session.
     */
    fun playTrack(
        track: TrackItem,
        queue: List<TrackItem> = listOf(track),
        startIndex: Int = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0),
        startPositionMs: Long = 0L,
        autoplay: Boolean = true
    ) {
        if (!_castState.value.isConnected) return
        val current = _castState.value
        val effectiveQueue = queue.ifEmpty { listOf(track) }
        val safeIndex = startIndex.coerceIn(0, effectiveQueue.lastIndex)

        loadTrackAndQueueOnCast(
            track = track,
            queue = effectiveQueue,
            startIndex = safeIndex,
            startPositionMs = startPositionMs.coerceAtLeast(0L),
            autoplay = autoplay,
            repeatMode = current.repeatMode,
            isShuffleEnabled = current.isShuffleEnabled
        )
    }

    fun togglePlayPause() {
        val client = currentSession?.remoteMediaClient ?: return
        runOnMain {
            runCatching { client.togglePlayback() }
        }
    }

    fun play() {
        val client = currentSession?.remoteMediaClient ?: return
        runOnMain {
            runCatching { client.play() }
        }
    }

    fun pause() {
        val client = currentSession?.remoteMediaClient ?: return
        runOnMain {
            runCatching { client.pause() }
        }
    }

    fun seekTo(positionMs: Long) {
        val state = _castState.value
        val maxDuration = state.durationMs.coerceAtLeast(1L)
        val clamped = positionMs.coerceIn(0L, maxDuration)
        _castState.update { it.copy(positionMs = clamped) }

        val client = currentSession?.remoteMediaClient ?: return
        runOnMain {
            val seekOptions = MediaSeekOptions.Builder()
                .setPosition(clamped)
                .build()
            runCatching { client.seek(seekOptions) }
        }
    }

    fun seekToFraction(fraction: Float) {
        val duration = _castState.value.durationMs
        if (duration <= 0L) return
        val targetMs = (fraction.coerceIn(0f, 1f) * duration.toFloat()).toLong()
        seekTo(targetMs)
    }

    fun skipNext() {
        val state = _castState.value
        val nextIndex = CastMediaMapper.computeNextQueueIndex(
            currentIndex = state.currentQueueIndex,
            queueSize = state.queue.size,
            repeatMode = state.repeatMode
        )
        if (nextIndex != null) {
            jumpToQueueIndex(nextIndex)
            return
        }
        val client = currentSession?.remoteMediaClient ?: return
        runOnMain { runCatching { client.queueNext(null) } }
    }

    fun skipPrevious() {
        val state = _castState.value
        if (state.positionMs > 3_000L) {
            seekTo(0L)
            return
        }
        val prevIndex = CastMediaMapper.computePreviousQueueIndex(
            currentIndex = state.currentQueueIndex,
            queueSize = state.queue.size,
            repeatMode = state.repeatMode
        )
        if (prevIndex != null) {
            jumpToQueueIndex(prevIndex)
            return
        }
        val client = currentSession?.remoteMediaClient ?: return
        runOnMain { runCatching { client.queuePrev(null) } }
    }

    fun jumpToQueueIndex(index: Int) {
        val state = _castState.value
        val targetTrack = state.queue.getOrNull(index) ?: return
        val castItemId = trackIdToCastItemId[targetTrack.id]
        val client = currentSession?.remoteMediaClient

        if (client != null && castItemId != null) {
            _castState.update {
                it.copy(
                    currentTrack = targetTrack,
                    currentQueueIndex = index,
                    positionMs = 0L,
                    durationMs = targetTrack.durationMs,
                    isBuffering = true
                )
            }
            runOnMain {
                runCatching { client.queueJumpToItem(castItemId, null) }
            }
            scheduleUpcomingPrefetch(state.queue, index, queuedTrackIds.toSet())
        } else {
            // Track not yet uploaded into remote Cast queue — resolve & load immediately
            loadTrackAndQueueOnCast(
                track = targetTrack,
                queue = state.queue,
                startIndex = index,
                startPositionMs = 0L,
                autoplay = true,
                repeatMode = state.repeatMode,
                isShuffleEnabled = state.isShuffleEnabled
            )
        }
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _castState.update {
            it.copy(
                volume = clamped,
                isMuted = if (clamped > 0f) false else it.isMuted
            )
        }
        val session = currentSession ?: return
        runOnMain {
            runCatching {
                if (clamped > 0f && session.isMute) {
                    session.isMute = false
                }
                session.volume = clamped.toDouble()
            }
        }
    }

    fun toggleMute() {
        val nextMuted = !_castState.value.isMuted
        _castState.update { it.copy(isMuted = nextMuted) }
        val session = currentSession ?: return
        runOnMain {
            runCatching { session.isMute = nextMuted }
        }
    }

    fun setRepeatMode(mode: PlaybackRepeatMode) {
        val isShuffle = _castState.value.isShuffleEnabled
        _castState.update { it.copy(repeatMode = mode) }
        syncRepeatModeToCast(mode, isShuffle)
    }

    fun cycleRepeatMode(): PlaybackRepeatMode {
        val next = when (_castState.value.repeatMode) {
            PlaybackRepeatMode.Off -> PlaybackRepeatMode.RepeatAll
            PlaybackRepeatMode.RepeatAll -> PlaybackRepeatMode.RepeatOne
            PlaybackRepeatMode.RepeatOne -> PlaybackRepeatMode.Off
        }
        setRepeatMode(next)
        return next
    }

    fun toggleShuffle() {
        val nextShuffle = !_castState.value.isShuffleEnabled
        val mode = _castState.value.repeatMode
        _castState.update { it.copy(isShuffleEnabled = nextShuffle) }
        syncRepeatModeToCast(mode, nextShuffle)
    }

    fun clearError() {
        _castState.update { it.copy(errorMessage = null) }
    }

    /**
     * Releases all MediaRouter / Cast SDK listeners, cancels background jobs, and stops the LAN HTTP server.
     */
    fun release() {
        if (!isReleased.compareAndSet(false, true)) return
        loadMediaJob?.cancel()
        prefetchQueueJob?.cancel()
        scope.cancel()

        runOnMain {
            currentSession?.let { detachSession(it) }
            runCatching {
                sessionManager?.removeSessionManagerListener(sessionManagerListener, CastSession::class.java)
            }
            runCatching {
                mediaRouter?.removeCallback(mediaRouterCallback)
            }
        }

        localPlaybackStateProvider = null
        onCastSessionStarted = null
        onCastSessionEnded = null

        localServer.stop()
        castInitExecutor?.shutdownNow()
        trackIdToCastItemId.clear()
        queuedTrackIds.clear()
        routeInfoById.clear()
        synchronized(Companion) {
            if (instance === this) {
                instance = null
            }
        }
    }

    private fun attachSession(session: CastSession) {
        currentSession?.let { previous ->
            if (previous !== session) detachSession(previous)
        }
        currentSession = session
        runCatching { session.addCastListener(castVolumeListener) }
        session.remoteMediaClient?.let { client ->
            runCatching { client.registerCallback(remoteMediaCallback) }
            runCatching {
                client.addProgressListener(remoteProgressListener, CastConfig.PROGRESS_UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun detachSession(session: CastSession?) {
        if (session == null) return
        runCatching { session.removeCastListener(castVolumeListener) }
        session.remoteMediaClient?.let { client ->
            runCatching { client.unregisterCallback(remoteMediaCallback) }
            runCatching { client.removeProgressListener(remoteProgressListener) }
        }
        if (currentSession === session) {
            currentSession = null
        }
    }

    private fun handleSessionEnded(session: CastSession?, errorMessage: String?) {
        loadMediaJob?.cancel()
        prefetchQueueJob?.cancel()

        val liveRemotePos = runCatching {
            session?.remoteMediaClient?.approximateStreamPosition?.takeIf { it > 0L }
        }.getOrNull()

        detachSession(session ?: currentSession)
        trackIdToCastItemId.clear()
        queuedTrackIds.clear()
        localServer.stop()

        val (disconnectedState, handoff) = CastStateReducer.onSessionDisconnected(
            current = _castState.value,
            latestPositionMs = liveRemotePos,
            errorMessage = errorMessage
        )
        _castState.value = disconnectedState

        if (sessionHandoffDispatched.compareAndSet(false, true) && handoff.hasPlayableContent) {
            runOnMain {
                runCatching { onCastSessionEnded?.invoke(handoff) }
            }
        }
    }

    private fun loadTrackAndQueueOnCast(
        track: TrackItem,
        queue: List<TrackItem>,
        startIndex: Int,
        startPositionMs: Long,
        autoplay: Boolean,
        repeatMode: PlaybackRepeatMode,
        isShuffleEnabled: Boolean
    ) {
        loadMediaJob?.cancel()
        prefetchQueueJob?.cancel()
        trackIdToCastItemId.clear()
        queuedTrackIds.clear()

        _castState.update {
            it.copy(
                currentTrack = track,
                queue = queue,
                currentQueueIndex = startIndex,
                positionMs = startPositionMs,
                durationMs = track.durationMs.takeIf { d -> d > 0L } ?: it.durationMs,
                isBuffering = true,
                errorMessage = null
            )
        }

        loadMediaJob = scope.launch {
            try {
                val primaryPayload = prepareCastPayload(track)
                if (primaryPayload == null) {
                    _castState.update {
                        it.copy(
                            isBuffering = false,
                            isPlaying = false,
                            errorMessage = "No se pudo preparar el audio de \"${track.title}\" para Chromecast"
                        )
                    }
                    return@launch
                }

                // Collect any already-available queue items (local files / cached YTM / HTTP) without blocking
                val immediateItems = mutableListOf<MediaQueueItem>()
                val immediateTrackIds = mutableSetOf<String>()
                var loadedStartIndex = 0
                queue.forEachIndexed { idx, candidate ->
                    if (idx == startIndex) {
                        loadedStartIndex = immediateItems.size
                        immediateItems.add(
                            CastMediaMapper.toCastMediaQueueItem(
                                payload = primaryPayload,
                                autoplay = autoplay
                            )
                        )
                        immediateTrackIds.add(primaryPayload.track.id)
                    } else if (idx > startIndex && immediateItems.size < 15) {
                        val fastPayload = tryPrepareImmediatePayload(candidate)
                        if (fastPayload != null) {
                            immediateItems.add(
                                CastMediaMapper.toCastMediaQueueItem(
                                    payload = fastPayload,
                                    autoplay = true
                                )
                            )
                            immediateTrackIds.add(fastPayload.track.id)
                        }
                    }
                }

                if (immediateItems.isEmpty()) {
                    loadedStartIndex = 0
                    immediateItems.add(
                        CastMediaMapper.toCastMediaQueueItem(
                            payload = primaryPayload,
                            autoplay = autoplay
                        )
                    )
                    immediateTrackIds.add(primaryPayload.track.id)
                }

                queuedTrackIds.addAll(immediateTrackIds)
                val castRepeat = CastMediaMapper.toCastRepeatMode(repeatMode, isShuffleEnabled)

                withContext(effectiveMainDispatcher) {
                    val client = currentSession?.remoteMediaClient ?: return@withContext
                    if (queue.size > 1 || immediateItems.size > 1) {
                        client.queueLoad(
                            immediateItems.toTypedArray(),
                            loadedStartIndex,
                            castRepeat,
                            startPositionMs,
                            null
                        )
                    } else {
                        val loadRequest = MediaLoadRequestData.Builder()
                            .setMediaInfo(CastMediaMapper.toCastMediaInfo(primaryPayload))
                            .setAutoplay(autoplay)
                            .setCurrentTime(startPositionMs)
                            .build()
                        client.load(loadRequest)
                    }
                }

                scheduleUpcomingPrefetch(queue, startIndex, immediateTrackIds)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                safeLogE("Failed loading media onto Cast", t)
                _castState.update {
                    it.copy(
                        isBuffering = false,
                        errorMessage = "Error al enviar la canción a Chromecast"
                    )
                }
            }
        }
    }

    private fun scheduleUpcomingPrefetch(
        queue: List<TrackItem>,
        currentIndex: Int,
        alreadyQueuedIds: Set<String>
    ) {
        prefetchQueueJob?.cancel()
        if (queue.size <= currentIndex + 1) return

        prefetchQueueJob = scope.launch {
            val endIndex = minOf(queue.lastIndex, currentIndex + CastConfig.MAX_PREFETCH_AHEAD_TRACKS)
            for (idx in (currentIndex + 1)..endIndex) {
                val track = queue[idx]
                if (track.id in alreadyQueuedIds ||
                    track.id in queuedTrackIds ||
                    trackIdToCastItemId.containsKey(track.id)
                ) {
                    continue
                }
                val payload = runCatching { prepareCastPayload(track) }.getOrNull() ?: continue
                if (!queuedTrackIds.add(track.id)) continue
                val queueItem = CastMediaMapper.toCastMediaQueueItem(payload = payload, autoplay = true)
                withContext(effectiveMainDispatcher) {
                    val client = currentSession?.remoteMediaClient ?: return@withContext
                    if (client.mediaStatus != null) {
                        runCatching {
                            client.queueInsertItems(
                                arrayOf(queueItem),
                                MediaQueueItem.INVALID_ITEM_ID,
                                null
                            )
                        }
                    }
                }
            }
        }
    }

    private suspend fun prepareCastPayload(track: TrackItem): CastTrackPayload? {
        val resolved = mediaSourceResolver.resolve(track) ?: return null
        return materializePayload(track, resolved)
    }

    /**
     * Resolves a queue item ONLY if it requires zero network download (direct HTTP, local file,
     * `content://`, or already-cached YTM audio file).
     */
    private fun tryPrepareImmediatePayload(track: TrackItem): CastTrackPayload? {
        val audioUrl = track.audioUrl?.trim()
        val artworkUrl = resolveCastArtworkUrl(track)
        return when {
            CastMimeResolver.isDirectHttpStreamUrl(audioUrl) -> {
                CastTrackPayload(
                    track = track,
                    streamUrl = audioUrl!!,
                    mimeType = CastMimeResolver.resolveAudioMimeType(audioUrl),
                    artworkUrl = artworkUrl
                )
            }
            audioUrl != null && audioUrl.startsWith("content://", ignoreCase = true) -> {
                val ctx = appContext ?: return null
                val uri = Uri.parse(audioUrl)
                val resolverMime = runCatching { ctx.contentResolver.getType(uri) }.getOrNull()
                val mime = CastMimeResolver.resolveAudioMimeType(audioUrl, resolverMime)
                val ext = CastMimeResolver.extensionForAudioMimeType(mime)
                val served = localServer.registerContentUri(
                    contentResolver = ctx.contentResolver,
                    uri = uri,
                    mimeType = mime,
                    suggestedFileName = "${track.id}.$ext",
                    dedupeKey = "audio:${track.id}"
                ) ?: return null
                CastTrackPayload(track, served, mime, artworkUrl)
            }
            audioUrl != null && (audioUrl.startsWith("file://", ignoreCase = true) || audioUrl.startsWith("/")) -> {
                val file = if (audioUrl.startsWith("file://", ignoreCase = true)) {
                    File(Uri.parse(audioUrl).path ?: return null)
                } else {
                    File(audioUrl)
                }
                if (!file.exists() || !file.isFile) return null
                val mime = CastMimeResolver.resolveAudioMimeType(file.name)
                val served = localServer.registerFile(file, mime, dedupeKey = "audio:${track.id}")
                    ?: return null
                CastTrackPayload(track, served, mime, artworkUrl)
            }
            else -> {
                // Check if YTM cache file already exists on disk
                val ctx = appContext ?: return null
                val videoId = extractVideoId(track) ?: return null
                val cached = File(File(ctx.cacheDir, "audio_cache"), "$videoId.m4a")
                if (!cached.exists() || cached.length() <= 50_000L) return null
                val served = localServer.registerFile(
                    file = cached,
                    mimeType = "audio/mp4",
                    dedupeKey = "audio:${track.id}"
                ) ?: return null
                CastTrackPayload(track, served, "audio/mp4", artworkUrl)
            }
        }
    }

    private fun materializePayload(
        track: TrackItem,
        resolved: ResolvedCastSource
    ): CastTrackPayload? {
        val artworkUrl = resolved.artworkUrl ?: resolveCastArtworkUrl(track)
        return when (resolved) {
            is ResolvedCastSource.RemoteUrl -> {
                CastTrackPayload(
                    track = track,
                    streamUrl = resolved.url,
                    mimeType = resolved.mimeType,
                    artworkUrl = artworkUrl
                )
            }
            is ResolvedCastSource.LocalFile -> {
                val servedUrl = localServer.registerFile(
                    file = resolved.file,
                    mimeType = resolved.mimeType,
                    dedupeKey = "audio:${track.id}"
                ) ?: return null
                CastTrackPayload(
                    track = track,
                    streamUrl = servedUrl,
                    mimeType = resolved.mimeType,
                    artworkUrl = artworkUrl
                )
            }
            is ResolvedCastSource.LocalContentUri -> {
                val ctx = appContext ?: return null
                val servedUrl = localServer.registerContentUri(
                    contentResolver = ctx.contentResolver,
                    uri = resolved.uri,
                    mimeType = resolved.mimeType,
                    suggestedFileName = resolved.suggestedFileName,
                    dedupeKey = "audio:${track.id}"
                ) ?: return null
                CastTrackPayload(
                    track = track,
                    streamUrl = servedUrl,
                    mimeType = resolved.mimeType,
                    artworkUrl = artworkUrl
                )
            }
        }
    }

    private suspend fun defaultResolveTrackSource(track: TrackItem): ResolvedCastSource? {
        val audioUrl = track.audioUrl?.trim()
        val artworkUrl = resolveCastArtworkUrl(track)

        if (CastMimeResolver.isDirectHttpStreamUrl(audioUrl)) {
            return ResolvedCastSource.RemoteUrl(
                url = audioUrl!!,
                mimeType = CastMimeResolver.resolveAudioMimeType(audioUrl),
                artworkUrl = artworkUrl
            )
        }

        if (audioUrl != null && audioUrl.startsWith("content://", ignoreCase = true)) {
            val uri = Uri.parse(audioUrl)
            val resolverMime = appContext?.let { ctx ->
                runCatching { ctx.contentResolver.getType(uri) }.getOrNull()
            }
            val mime = CastMimeResolver.resolveAudioMimeType(audioUrl, resolverMime)
            val ext = CastMimeResolver.extensionForAudioMimeType(mime)
            return ResolvedCastSource.LocalContentUri(
                uri = uri,
                mimeType = mime,
                suggestedFileName = "${track.id}.$ext",
                artworkUrl = artworkUrl
            )
        }

        if (audioUrl != null && (audioUrl.startsWith("file://", ignoreCase = true) || audioUrl.startsWith("/"))) {
            val file = if (audioUrl.startsWith("file://", ignoreCase = true)) {
                File(Uri.parse(audioUrl).path ?: return null)
            } else {
                File(audioUrl)
            }
            if (file.exists() && file.isFile) {
                return ResolvedCastSource.LocalFile(
                    file = file,
                    mimeType = CastMimeResolver.resolveAudioMimeType(file.name),
                    artworkUrl = artworkUrl
                )
            }
        }

        // Resolve YouTube Music audio file (instant cache hit if already playing locally!)
        val ctx = appContext ?: return null
        val ytm = YouTubeMusicService.getInstance()
        var videoId = extractVideoId(track)
        if (videoId.isNullOrBlank()) {
            val results = runCatching { ytm.searchSongs("${track.artist} - ${track.title}") }.getOrNull()
            videoId = results?.firstOrNull()?.id?.removePrefix("ytm_")
        }
        if (videoId.isNullOrBlank()) return null

        val file = ytm.getOrDownloadAudioFile(videoId, ctx) ?: return null
        if (!file.exists() || file.length() <= 50_000L) return null
        return ResolvedCastSource.LocalFile(
            file = file,
            mimeType = CastMimeResolver.resolveAudioMimeType(file.name),
            artworkUrl = artworkUrl
        )
    }

    private fun resolveCastArtworkUrl(track: TrackItem): String? {
        val raw = track.artworkUri?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (CastMimeResolver.isHttpUrl(raw)) return raw

        val ctx = appContext ?: return null
        return when {
            raw.startsWith("content://", ignoreCase = true) -> {
                val uri = Uri.parse(raw)
                val resolverMime = runCatching { ctx.contentResolver.getType(uri) }.getOrNull()
                val mime = CastMimeResolver.resolveImageMimeType(raw, resolverMime)
                val ext = CastMimeResolver.extensionForImageMimeType(mime)
                localServer.registerContentUri(
                    contentResolver = ctx.contentResolver,
                    uri = uri,
                    mimeType = mime,
                    suggestedFileName = "art_${track.id}.$ext",
                    dedupeKey = "art:${track.id}"
                )
            }
            raw.startsWith("file://", ignoreCase = true) || raw.startsWith("/") -> {
                val file = if (raw.startsWith("file://", ignoreCase = true)) {
                    File(Uri.parse(raw).path ?: return null)
                } else {
                    File(raw)
                }
                val mime = CastMimeResolver.resolveImageMimeType(file.name)
                localServer.registerFile(file, mime, dedupeKey = "art:${track.id}")
            }
            else -> null
        }
    }

    private fun extractVideoId(track: TrackItem): String? {
        return when {
            track.id.startsWith("ytm_") -> track.id.removePrefix("ytm_").takeIf { it.isNotBlank() }
            track.audioUrl?.contains("v=") == true -> {
                track.audioUrl.substringAfter("v=").substringBefore("&").takeIf { it.isNotBlank() }
            }
            else -> null
        }
    }

    private fun syncRepeatModeToCast(mode: PlaybackRepeatMode, isShuffle: Boolean) {
        val client = currentSession?.remoteMediaClient ?: return
        val castMode = CastMediaMapper.toCastRepeatMode(mode, isShuffle)
        runOnMain {
            runCatching { client.queueSetRepeatMode(castMode, null) }
        }
    }

    private fun syncQueueItemIdsFromRemote() {
        val status = currentSession?.remoteMediaClient?.mediaStatus ?: return
        val items = status.queueItems
        if (items.isNullOrEmpty()) return
        items.forEach { item ->
            val trackId = item.customData?.optString(CastMediaMapper.CUSTOM_KEY_TRACK_ID)
                ?.takeIf { it.isNotBlank() }
                ?: item.media?.customData?.optString(CastMediaMapper.CUSTOM_KEY_TRACK_ID)
                    ?.takeIf { it.isNotBlank() }
            if (trackId != null) {
                trackIdToCastItemId[trackId] = item.itemId
            }
        }
    }

    private fun syncFromRemoteMediaClient() {
        val session = currentSession ?: return
        val client = session.remoteMediaClient ?: return
        val status = client.mediaStatus ?: return

        val playerState = status.playerState
        val isPlaying = playerState == MediaStatus.PLAYER_STATE_PLAYING
        val isBuffering = playerState == MediaStatus.PLAYER_STATE_BUFFERING ||
            playerState == MediaStatus.PLAYER_STATE_LOADING

        val positionMs = client.approximateStreamPosition.coerceAtLeast(0L)
        val durationMs = client.streamDuration.takeIf { it > 0L }
            ?: status.mediaInfo?.streamDuration?.takeIf { it > 0L }
            ?: _castState.value.durationMs

        val (repeatMode, shuffleFromCast) = CastMediaMapper.fromCastRepeatMode(status.queueRepeatMode)
        val volume = runCatching { session.volume.toFloat() }.getOrDefault(_castState.value.volume)
        val isMuted = runCatching { session.isMute }.getOrDefault(_castState.value.isMuted)

        _castState.update { current ->
            val resolvedTrack = CastMediaMapper.fromCastMediaInfo(status.mediaInfo, current.queue)
                ?: current.currentTrack
            val matchedIndex = if (resolvedTrack != null && current.queue.isNotEmpty()) {
                current.queue.indexOfFirst { it.id == resolvedTrack.id }
                    .takeIf { it >= 0 }
                    ?: current.currentQueueIndex
            } else {
                current.currentQueueIndex
            }

            val updatedQueue = if (current.queue.isEmpty() && resolvedTrack != null) {
                listOf(resolvedTrack)
            } else {
                current.queue
            }

            current.copy(
                connectionState = CastConnectionState.CONNECTED,
                deviceName = session.castDevice?.friendlyName ?: current.deviceName,
                deviceId = session.castDevice?.deviceId ?: current.deviceId,
                currentTrack = resolvedTrack,
                queue = updatedQueue,
                currentQueueIndex = if (updatedQueue.isEmpty()) -1 else matchedIndex.coerceIn(0, updatedQueue.lastIndex),
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                positionMs = positionMs,
                durationMs = durationMs,
                volume = volume.coerceIn(0f, 1f),
                isMuted = isMuted,
                repeatMode = repeatMode,
                isShuffleEnabled = if (status.queueRepeatMode == MediaStatus.REPEAT_MODE_REPEAT_SINGLE) {
                    current.isShuffleEnabled
                } else {
                    shuffleFromCast
                },
                errorMessage = if (playerState == MediaStatus.PLAYER_STATE_IDLE &&
                    status.idleReason == MediaStatus.IDLE_REASON_ERROR
                ) {
                    "Chromecast no pudo reproducir este archivo"
                } else {
                    null
                }
            )
        }
    }

    private fun refreshRoutesFromRouter(router: MediaRouter) {
        val selector = routeSelector ?: return
        val discovered = mutableListOf<CastRouteDevice>()
        val latestMap = HashMap<String, MediaRouter.RouteInfo>()

        router.routes.forEach { route ->
            if (!route.isEnabled || route.isDefault) return@forEach
            if (!route.matchesSelector(selector)) return@forEach

            latestMap[route.id] = route
            discovered.add(
                CastRouteDevice(
                    id = route.id,
                    name = route.name.orEmpty().ifBlank { "Dispositivo Cast" },
                    description = route.description.orEmpty(),
                    isSelected = route.isSelected,
                    volume = route.volume,
                    volumeMax = route.volumeMax.coerceAtLeast(1),
                    deviceType = classifyRouteDeviceType(route),
                    isConnecting = route.isConnecting,
                    supportsVolumeControl = route.volumeHandling == MediaRouter.RouteInfo.PLAYBACK_VOLUME_VARIABLE
                )
            )
        }

        routeInfoById.clear()
        routeInfoById.putAll(latestMap)
        _routes.value = discovered.sortedWith(
            compareByDescending<CastRouteDevice> { it.isSelected }
                .thenByDescending { it.isConnecting }
                .thenBy { it.name.lowercase(Locale.US) }
        )
    }

    private fun classifyRouteDeviceType(route: MediaRouter.RouteInfo): CastDeviceType {
        val descLower = route.description.orEmpty().lowercase(Locale.US)
        val nameLower = route.name.orEmpty().lowercase(Locale.US)
        return when (route.deviceType) {
            MediaRouter.RouteInfo.DEVICE_TYPE_TV -> CastDeviceType.TV
            MediaRouter.RouteInfo.DEVICE_TYPE_GROUP -> CastDeviceType.SPEAKER_GROUP
            MediaRouter.RouteInfo.DEVICE_TYPE_REMOTE_SPEAKER,
            MediaRouter.RouteInfo.DEVICE_TYPE_AUDIO_VIDEO_RECEIVER -> CastDeviceType.SPEAKER
            else -> when {
                descLower.contains("group") || descLower.contains("grupo") -> CastDeviceType.SPEAKER_GROUP
                descLower.contains("tv") || nameLower.contains("tv") -> CastDeviceType.TV
                descLower.contains("speaker") || descLower.contains("nest audio") || descLower.contains("nest mini") -> {
                    CastDeviceType.SPEAKER
                }
                else -> CastDeviceType.CHROMECAST
            }
        }
    }

    private fun runOnMain(block: () -> Unit) {
        val handler = mainHandler
        if (handler == null || Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            handler.post(block)
        }
    }

    companion object {
        private const val TAG = "CastSessionController"

        @Volatile
        private var instance: CastSessionController? = null

        fun getInstance(context: Context): CastSessionController {
            return instance ?: synchronized(this) {
                instance ?: CastSessionController(context.applicationContext).also { instance = it }
            }
        }

        internal fun defaultCheckGmsAvailable(context: Context): Boolean {
            return runCatching {
                val availability = GoogleApiAvailability.getInstance()
                availability.isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
            }.getOrDefault(false)
        }

        private fun safeLogW(msg: String) {
            runCatching { Log.w(TAG, msg) }
        }

        private fun safeLogE(msg: String, t: Throwable) {
            runCatching { Log.e(TAG, msg, t) }
        }
    }
}
