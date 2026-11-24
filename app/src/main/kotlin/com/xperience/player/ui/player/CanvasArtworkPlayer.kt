package com.xperience.player.ui.player

import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.TextureView
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import com.xperience.player.service.MediaNetworkClients
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

private const val CANVAS_PLAYER_TAG = "CanvasArtworkPlayer"
private val SeamlessCanvasEasing = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)

private class CanvasPlayerHolder {
    var player: ExoPlayer? = null
    var textureView: TextureView? = null
    var disposed: Boolean = false
}

/**
 * Renders a hardware-accelerated looping Apple Music Album/Artist Canvas video over artwork.
 *
 * Uses the shared [MediaNetworkClients] Media3 `SimpleCache` and pooled `OkHttpClient`, reuses the
 * [ExoPlayer] instance across URI changes, pauses decoding when lifecycle-stopped or offscreen,
 * preserves the video's true aspect ratio via [Player.Listener.onVideoSizeChanged] +
 * [AspectRatioFrameLayout.setAspectRatio], and optionally locks to a height-matched [targetAspectRatio]
 * (e.g. `16f / 9f` for full-height artist hero banners centered and clipped horizontally).
 */
@OptIn(UnstableApi::class)
@Composable
fun CanvasArtworkPlayer(
    videoUri: String,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    targetAspectRatio: Float? = null,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
    syncPositionMs: Long? = null,
    playbackSpeed: Float = 1.0f,
    httpHeaders: Map<String, String> = emptyMap(),
    useDiskCache: Boolean = true,
    repeatVideo: Boolean = (syncPositionMs == null),
    constrainTrackQuality: Boolean = (syncPositionMs == null),
    onReady: () -> Unit = {},
    onError: () -> Unit = {}
) {
    if (videoUri.isBlank()) return

    val context = LocalContext.current
    val appContext = context.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current

    var inForeground by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> inForeground = true
                Lifecycle.Event.ON_STOP -> inForeground = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val shouldPlay = isPlaying && inForeground
    val currentShouldPlay by rememberUpdatedState(shouldPlay)
    val currentSyncPositionMs by rememberUpdatedState(syncPositionMs)
    var isVideoReady by remember(videoUri) { mutableStateOf(false) }
    var measuredVideoAspectRatio by remember(videoUri) { mutableFloatStateOf(0f) }
    var didInitialSyncSeek by remember(videoUri) { mutableStateOf(false) }
    var lastSeekWallClockMs by remember(videoUri) { mutableLongStateOf(0L) }

    val currentOnReady by rememberUpdatedState(onReady)
    val currentOnError by rememberUpdatedState(onError)

    val headersRef = remember { AtomicReference<Map<String, String>>(emptyMap()) }
    headersRef.set(httpHeaders)

    val bypassDiskCache = !useDiskCache || httpHeaders.isNotEmpty()
    val playerHolder = remember(appContext, bypassDiskCache, constrainTrackQuality) { CanvasPlayerHolder() }
    var exoPlayer by remember(appContext, bypassDiskCache, constrainTrackQuality) { mutableStateOf<ExoPlayer?>(null) }

    LaunchedEffect(appContext, playerHolder, bypassDiskCache, constrainTrackQuality) {
        val mediaSourceFactory = withContext(Dispatchers.IO) {
            if (bypassDiskCache) {
                val callFactory = Call.Factory { request ->
                    val extraHeaders = headersRef.get()
                    if (extraHeaders.isEmpty()) {
                        MediaNetworkClients.sharedOkHttpClient.newCall(request)
                    } else {
                        val builder = request.newBuilder()
                        extraHeaders.forEach { (key, value) ->
                            if (key.isNotBlank() && value.isNotBlank()) {
                                builder.header(key, value)
                            }
                        }
                        MediaNetworkClients.sharedOkHttpClient.newCall(builder.build())
                    }
                }
                val upstream = OkHttpDataSource.Factory(callFactory)
                val dataSourceFactory = DefaultDataSource.Factory(appContext, upstream)
                DefaultMediaSourceFactory(dataSourceFactory)
            } else {
                MediaNetworkClients.getCanvasMediaSourceFactory(appContext)
            }
        }
        if (playerHolder.disposed) return@LaunchedEffect

        val created = runCatching {
            ExoPlayer.Builder(appContext)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
                .apply {
                    trackSelectionParameters = trackSelectionParameters
                        .buildUpon()
                        .apply {
                            if (constrainTrackQuality) {
                                setMaxVideoFrameRate(30)
                                setMaxVideoBitrate(2_500_000)
                            }
                            setMaxVideoSize(1920, 1080)
                        }
                        .build()
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(C.USAGE_MEDIA)
                            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                            .build(),
                        false
                    )
                    volume = 0f
                    repeatMode = if (repeatVideo) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                    playWhenReady = false
                }
        }.getOrNull() ?: return@LaunchedEffect

        if (playerHolder.disposed) {
            runCatching { created.release() }
            return@LaunchedEffect
        }

        playerHolder.player = created
        playerHolder.textureView?.let { tv ->
            runCatching { created.setVideoTextureView(tv) }
        }
        exoPlayer = created
    }

    LaunchedEffect(repeatVideo, exoPlayer) {
        val player = exoPlayer ?: return@LaunchedEffect
        if (playerHolder.disposed) return@LaunchedEffect
        runCatching {
            val targetRepeatMode = if (repeatVideo) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            if (player.repeatMode != targetRepeatMode) {
                player.repeatMode = targetRepeatMode
            }
        }
    }

    LaunchedEffect(shouldPlay, exoPlayer) {
        val player = exoPlayer ?: return@LaunchedEffect
        if (playerHolder.disposed) return@LaunchedEffect
        runCatching {
            if (player.playWhenReady != shouldPlay) {
                player.playWhenReady = shouldPlay
            }
        }
    }

    LaunchedEffect(playbackSpeed, exoPlayer) {
        val player = exoPlayer ?: return@LaunchedEffect
        if (playerHolder.disposed) return@LaunchedEffect
        runCatching {
            val safeSpeed = playbackSpeed.coerceIn(0.5f, 2.0f)
            if (kotlin.math.abs(player.playbackParameters.speed - safeSpeed) > 0.01f) {
                player.setPlaybackSpeed(safeSpeed)
            }
        }
    }

    LaunchedEffect(syncPositionMs, isVideoReady, exoPlayer) {
        val targetPos = syncPositionMs ?: return@LaunchedEffect
        val player = exoPlayer ?: return@LaunchedEffect
        if (playerHolder.disposed || !isVideoReady) return@LaunchedEffect
        runCatching {
            val state = player.playbackState
            if (state != Player.STATE_READY && state != Player.STATE_BUFFERING) return@runCatching
            val duration = player.duration
            val clampedTarget = if (duration > 0L) {
                targetPos.coerceIn(0L, (duration - 250L).coerceAtLeast(0L))
            } else {
                targetPos.coerceAtLeast(0L)
            }
            val diff = kotlin.math.abs(player.currentPosition - clampedTarget)
            // Never interrupt an active buffer fill for minor drift; only jump on large user scrubs
            if (state == Player.STATE_BUFFERING && diff <= 5_000L) {
                return@runCatching
            }
            val nowMs = SystemClock.elapsedRealtime()
            val shouldSeek = diff > 3_500L || (diff > 1_500L && nowMs - lastSeekWallClockMs >= 2_000L)
            if (shouldSeek) {
                lastSeekWallClockMs = nowMs
                player.seekTo(clampedTarget)
            }
        }
    }

    DisposableEffect(exoPlayer, videoUri) {
        val player = exoPlayer
        if (player == null || playerHolder.disposed) {
            return@DisposableEffect onDispose {}
        }
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.w(
                    CANVAS_PLAYER_TAG,
                    "Playback error (${error.errorCodeName}) for uri=${videoUri.take(120)}",
                    error
                )
                isVideoReady = false
                currentOnError()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && !didInitialSyncSeek) {
                    didInitialSyncSeek = true
                    val targetPos = currentSyncPositionMs
                    if (targetPos != null && targetPos > 0L) {
                        runCatching {
                            val duration = player.duration
                            val clampedTarget = if (duration > 0L) {
                                targetPos.coerceIn(0L, (duration - 250L).coerceAtLeast(0L))
                            } else {
                                targetPos
                            }
                            if (kotlin.math.abs(player.currentPosition - clampedTarget) > 1_500L) {
                                lastSeekWallClockMs = SystemClock.elapsedRealtime()
                                player.seekTo(clampedTarget)
                            }
                        }
                    }
                }
            }

            override fun onRenderedFirstFrame() {
                isVideoReady = true
                currentOnReady()
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0 && videoSize.pixelWidthHeightRatio.isFinite()) {
                    val ratio = (videoSize.width.toFloat() * videoSize.pixelWidthHeightRatio) /
                        videoSize.height.toFloat()
                    if (ratio.isFinite() && ratio > 0f) {
                        measuredVideoAspectRatio = ratio
                    }
                }
            }
        }
        runCatching { player.addListener(listener) }
        onDispose {
            if (!playerHolder.disposed) {
                runCatching { player.removeListener(listener) }
            }
        }
    }

    LaunchedEffect(videoUri, exoPlayer) {
        val player = exoPlayer ?: return@LaunchedEffect
        if (playerHolder.disposed) return@LaunchedEffect

        val trimmed = videoUri.trim()
        if (trimmed.isEmpty()) {
            isVideoReady = false
            measuredVideoAspectRatio = 0f
            didInitialSyncSeek = false
            lastSeekWallClockMs = 0L
            runCatching {
                player.stop()
                player.clearMediaItems()
            }
            return@LaunchedEffect
        }

        val isRemoteOrContentUri = trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("content://", ignoreCase = true) ||
            trimmed.startsWith("file://", ignoreCase = true)

        if (!isRemoteOrContentUri) {
            val fileExists = withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(trimmed)
                    file.exists() && file.length() > 0L
                }.getOrDefault(false)
            }
            if (playerHolder.disposed) return@LaunchedEffect
            if (!fileExists) {
                isVideoReady = false
                measuredVideoAspectRatio = 0f
                runCatching {
                    player.stop()
                    player.clearMediaItems()
                }
                return@LaunchedEffect
            }
        }

        val uri = if (isRemoteOrContentUri) {
            Uri.parse(trimmed)
        } else {
            Uri.fromFile(File(trimmed))
        }
        val lowerUri = trimmed.lowercase(Locale.ROOT)
        // Check YouTube HLS manifests BEFORE generic googlevideo.com MP4 matching because
        // YouTube HLS URLs use manifest.googlevideo.com/api/manifest/hls_variant/... without .m3u8.
        val mimeType = when {
            lowerUri.contains(".m3u8") ||
                lowerUri.contains("/hls_variant/") ||
                lowerUri.contains("/hls_playlist/") ||
                lowerUri.contains("/api/manifest/hls") -> MimeTypes.APPLICATION_M3U8
            lowerUri.contains("video%2fwebm") ||
                lowerUri.contains("video/webm") ||
                lowerUri.contains(".webm") -> MimeTypes.VIDEO_WEBM
            lowerUri.contains("googlevideo.com") ||
                lowerUri.contains("mime=video") ||
                lowerUri.contains(".mp4") -> MimeTypes.VIDEO_MP4
            !isRemoteOrContentUri || lowerUri.startsWith("file://") -> MimeTypes.VIDEO_MP4
            else -> MimeTypes.APPLICATION_M3U8
        }

        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMimeType(mimeType)
            .build()

        headersRef.set(httpHeaders)
        isVideoReady = false
        measuredVideoAspectRatio = 0f
        didInitialSyncSeek = false
        lastSeekWallClockMs = SystemClock.elapsedRealtime()
        val initialStartPosMs = currentSyncPositionMs?.takeIf { it > 1_200L } ?: C.TIME_UNSET
        runCatching {
            player.stop()
            player.clearMediaItems()
            if (initialStartPosMs != C.TIME_UNSET) {
                player.setMediaItem(mediaItem, initialStartPosMs)
            } else {
                player.setMediaItem(mediaItem)
            }
            player.prepare()
            player.playWhenReady = currentShouldPlay
        }.onFailure { err ->
            Log.w(CANVAS_PLAYER_TAG, "Failed to prepare media item", err)
            isVideoReady = false
            currentOnError()
        }
    }

    DisposableEffect(playerHolder) {
        onDispose {
            playerHolder.disposed = true
            val player = playerHolder.player
            playerHolder.player = null
            exoPlayer = null
            val textureView = playerHolder.textureView
            playerHolder.textureView = null
            if (player != null) {
                runCatching {
                    if (textureView != null) {
                        player.clearVideoTextureView(textureView)
                    }
                    player.stop()
                    player.clearMediaItems()
                    player.release()
                }
            }
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVideoReady) 1f else 0f,
        animationSpec = tween(durationMillis = 650, easing = SeamlessCanvasEasing),
        label = "canvasAlpha"
    )

    BoxWithConstraints(
        modifier = modifier.clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        val containerWidth = maxWidth
        val containerHeight = maxHeight
        val validTargetAspectRatio = targetAspectRatio?.takeIf { it.isFinite() && it > 0f }
        val videoModifier = if (
            validTargetAspectRatio != null &&
            containerWidth.value.isFinite() &&
            containerHeight.value.isFinite() &&
            containerWidth > 0.dp &&
            containerHeight > 0.dp
        ) {
            val aspectWidth = containerHeight * validTargetAspectRatio
            val (resolvedWidth, resolvedHeight) = if (aspectWidth >= containerWidth) {
                aspectWidth to containerHeight
            } else {
                containerWidth to (containerWidth / validTargetAspectRatio)
            }
            if (resolvedWidth.value.isFinite() && resolvedHeight.value.isFinite() &&
                resolvedWidth > 0.dp && resolvedHeight > 0.dp
            ) {
                Modifier.requiredSize(width = resolvedWidth, height = resolvedHeight)
            } else {
                Modifier.fillMaxSize()
            }
        } else {
            Modifier.fillMaxSize()
        }

        val effectiveAspectRatio = when {
            measuredVideoAspectRatio.isFinite() && measuredVideoAspectRatio > 0f -> measuredVideoAspectRatio
            validTargetAspectRatio != null -> validTargetAspectRatio
            else -> 0f
        }

        AndroidView(
            factory = { viewContext ->
                FrameLayout(viewContext).apply {
                    clipChildren = true
                    clipToPadding = true
                    setBackgroundColor(Color.TRANSPARENT)

                    val aspectLayout = AspectRatioFrameLayout(viewContext).apply {
                        this.resizeMode = resizeMode
                        if (effectiveAspectRatio > 0f) {
                            setAspectRatio(effectiveAspectRatio)
                        }
                        setBackgroundColor(Color.TRANSPARENT)

                        val textureView = TextureView(viewContext).apply {
                            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                        }
                        addView(textureView)
                        playerHolder.textureView = textureView
                        if (!playerHolder.disposed) {
                            runCatching { playerHolder.player?.setVideoTextureView(textureView) }
                        }
                    }
                    addView(
                        aspectLayout,
                        FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT, Gravity.CENTER)
                    )
                }
            },
            update = { container ->
                val aspectLayout = container.getChildAt(0) as? AspectRatioFrameLayout ?: return@AndroidView
                if (aspectLayout.resizeMode != resizeMode) {
                    aspectLayout.resizeMode = resizeMode
                }
                if (effectiveAspectRatio > 0f) {
                    aspectLayout.setAspectRatio(effectiveAspectRatio)
                }
                val textureView = aspectLayout.getChildAt(0) as? TextureView
                if (textureView != null && playerHolder.textureView !== textureView && !playerHolder.disposed) {
                    playerHolder.textureView = textureView
                    runCatching { playerHolder.player?.setVideoTextureView(textureView) }
                }
            },
            onRelease = { container ->
                val aspectLayout = container.getChildAt(0) as? AspectRatioFrameLayout
                val textureView = aspectLayout?.getChildAt(0) as? TextureView
                if (playerHolder.textureView === textureView) {
                    playerHolder.textureView = null
                }
                if (!playerHolder.disposed && textureView != null) {
                    runCatching { playerHolder.player?.clearVideoTextureView(textureView) }
                }
            },
            modifier = videoModifier.graphicsLayer {
                clip = true
                this.alpha = alpha
            }
        )
    }
}
