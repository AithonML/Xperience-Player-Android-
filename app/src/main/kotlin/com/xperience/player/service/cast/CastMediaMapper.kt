package com.xperience.player.service.cast

import android.net.Uri
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.common.images.WebImage
import com.xperience.player.domain.model.PlaybackRepeatMode
import com.xperience.player.domain.model.TrackItem
import org.json.JSONObject
import java.util.Locale

/**
 * Pure Kotlin descriptor holding resolved HTTP-reachable URLs and metadata
 * before materialization into Google Cast SDK [MediaInfo] / [MediaQueueItem].
 */
data class CastTrackPayload(
    val track: TrackItem,
    val streamUrl: String,
    val mimeType: String,
    val artworkUrl: String? = null
)

/**
 * Pure Kotlin MIME & URL classification utilities for Chromecast media preparation.
 */
object CastMimeResolver {

    fun resolveAudioMimeType(urlOrPath: String?): String {
        val clean = urlOrPath
            ?.substringBefore('?')
            ?.substringBefore('#')
            ?.trim()
            ?.lowercase(Locale.US)
            ?: return CastConfig.DEFAULT_AUDIO_MIME_TYPE

        return when {
            clean.endsWith(".mp3") -> "audio/mpeg"
            clean.endsWith(".m4a") || clean.endsWith(".mp4") || clean.endsWith(".aac") -> "audio/mp4"
            clean.endsWith(".flac") -> "audio/flac"
            clean.endsWith(".wav") -> "audio/wav"
            clean.endsWith(".ogg") || clean.endsWith(".oga") || clean.endsWith(".opus") -> "audio/ogg"
            clean.endsWith(".webm") -> "audio/webm"
            clean.endsWith(".m3u8") -> "application/x-mpegURL"
            else -> CastConfig.DEFAULT_AUDIO_MIME_TYPE
        }
    }

    fun resolveImageMimeType(urlOrPath: String?): String {
        val clean = urlOrPath
            ?.substringBefore('?')
            ?.substringBefore('#')
            ?.trim()
            ?.lowercase(Locale.US)
            ?: return "image/jpeg"

        return when {
            clean.endsWith(".png") -> "image/png"
            clean.endsWith(".webp") -> "image/webp"
            clean.endsWith(".gif") -> "image/gif"
            else -> "image/jpeg"
        }
    }

    /**
     * Prefers an explicit `ContentResolver.getType(uri)` MIME value when it reports a valid
     * audio MIME (prefix `audio/` or `application/ogg`), falling back to URL/path extension parsing.
     */
    fun resolveAudioMimeType(urlOrPath: String?, resolverMimeType: String?): String {
        val candidate = resolverMimeType?.trim()?.lowercase(Locale.US)
        if (!candidate.isNullOrEmpty() &&
            (candidate.startsWith("audio/") || candidate == "application/ogg")
        ) {
            return if (candidate == "application/ogg") "audio/ogg" else candidate
        }
        return resolveAudioMimeType(urlOrPath)
    }

    /**
     * Prefers an explicit `ContentResolver.getType(uri)` MIME value when it reports a valid
     * image MIME (prefix `image/`), falling back to URL/path extension parsing.
     */
    fun resolveImageMimeType(urlOrPath: String?, resolverMimeType: String?): String {
        val candidate = resolverMimeType?.trim()?.lowercase(Locale.US)
        if (!candidate.isNullOrEmpty() && candidate.startsWith("image/")) {
            return candidate
        }
        return resolveImageMimeType(urlOrPath)
    }

    fun extensionForAudioMimeType(mimeType: String): String {
        return when (mimeType.trim().lowercase(Locale.US)) {
            "audio/mpeg", "audio/mp3" -> "mp3"
            "audio/flac", "audio/x-flac" -> "flac"
            "audio/wav", "audio/x-wav", "audio/wave" -> "wav"
            "audio/ogg", "audio/opus", "application/ogg" -> "ogg"
            "audio/webm" -> "webm"
            "application/x-mpegurl" -> "m3u8"
            else -> "m4a"
        }
    }

    fun extensionForImageMimeType(mimeType: String): String {
        return when (mimeType.trim().lowercase(Locale.US)) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> "jpg"
        }
    }

    fun isDirectHttpStreamUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.trim().lowercase(Locale.US)
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
        // Exclude YouTube web watch URLs which require audio extraction rather than direct streaming
        val isYouTubeWatchPage = lower.contains("youtube.com/watch") ||
            lower.contains("music.youtube.com/watch") ||
            lower.contains("youtu.be/")
        return !isYouTubeWatchPage
    }

    fun isHttpUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.trim().lowercase(Locale.US)
        return lower.startsWith("http://") || lower.startsWith("https://")
    }

    fun isLocalUriOrPath(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.trim().lowercase(Locale.US)
        return lower.startsWith("content://") ||
            lower.startsWith("file://") ||
            lower.startsWith("/")
    }
}

/**
 * Bidirectional mapper between Xperience Music Player domain models ([TrackItem], [PlaybackRepeatMode])
 * and Google Cast SDK media/queue objects.
 */
object CastMediaMapper {

    const val CUSTOM_KEY_TRACK_ID = "trackId"
    const val CUSTOM_KEY_TITLE = "title"
    const val CUSTOM_KEY_ARTIST = "artist"
    const val CUSTOM_KEY_ALBUM = "album"
    const val CUSTOM_KEY_DURATION_MS = "durationMs"
    const val CUSTOM_KEY_ARTWORK_URI = "artworkUri"

    /**
     * Serializes essential [TrackItem] identity into Cast `customData` JSON so remote
     * status callbacks and reconnected sessions can reconstruct the exact local track.
     */
    fun buildCustomData(track: TrackItem): JSONObject {
        return JSONObject().apply {
            put(CUSTOM_KEY_TRACK_ID, track.id)
            put(CUSTOM_KEY_TITLE, track.title)
            put(CUSTOM_KEY_ARTIST, track.artist)
            put(CUSTOM_KEY_ALBUM, track.album)
            put(CUSTOM_KEY_DURATION_MS, track.durationMs)
            if (!track.artworkUri.isNullOrBlank()) {
                put(CUSTOM_KEY_ARTWORK_URI, track.artworkUri)
            }
        }
    }

    /**
     * Extracts [TrackItem] identity from Cast `customData` JSON, optionally matching against
     * the known local [knownQueue] first to preserve artwork/audioUrl/liked metadata.
     */
    fun resolveTrackFromCustomData(
        customData: JSONObject?,
        knownQueue: List<TrackItem>,
        fallbackTitle: String? = null,
        fallbackArtist: String? = null,
        fallbackAlbum: String? = null,
        fallbackArtworkUrl: String? = null,
        fallbackDurationMs: Long = 0L
    ): TrackItem? {
        val trackId = customData?.optString(CUSTOM_KEY_TRACK_ID)?.takeIf { it.isNotBlank() }
        if (trackId != null) {
            knownQueue.firstOrNull { it.id == trackId }?.let { return it }
        }

        val title = customData?.optString(CUSTOM_KEY_TITLE)?.takeIf { it.isNotBlank() }
            ?: fallbackTitle?.takeIf { it.isNotBlank() }
            ?: return null

        val artist = customData?.optString(CUSTOM_KEY_ARTIST)?.takeIf { it.isNotBlank() }
            ?: fallbackArtist.orEmpty()
        val album = customData?.optString(CUSTOM_KEY_ALBUM)?.takeIf { it.isNotBlank() }
            ?: fallbackAlbum.orEmpty()
        val durationMs = customData?.optLong(CUSTOM_KEY_DURATION_MS, 0L)
            ?.takeIf { it > 0L }
            ?: fallbackDurationMs.coerceAtLeast(0L)
        val artworkUri = customData?.optString(CUSTOM_KEY_ARTWORK_URI)?.takeIf { it.isNotBlank() }
            ?: fallbackArtworkUrl?.takeIf { it.isNotBlank() }

        return TrackItem(
            id = trackId ?: "cast_${title.hashCode()}_${artist.hashCode()}",
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            artworkUri = artworkUri
        )
    }

    /**
     * Converts Xperience [PlaybackRepeatMode] + shuffle flag to Cast [MediaStatus] repeat mode constant.
     */
    fun toCastRepeatMode(
        repeatMode: PlaybackRepeatMode,
        isShuffleEnabled: Boolean
    ): Int {
        return when {
            repeatMode == PlaybackRepeatMode.RepeatOne -> MediaStatus.REPEAT_MODE_REPEAT_SINGLE
            isShuffleEnabled -> MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE
            repeatMode == PlaybackRepeatMode.RepeatAll -> MediaStatus.REPEAT_MODE_REPEAT_ALL
            else -> MediaStatus.REPEAT_MODE_REPEAT_OFF
        }
    }

    /**
     * Converts Cast [MediaStatus] repeat mode constant back to Xperience ([PlaybackRepeatMode], isShuffle).
     */
    fun fromCastRepeatMode(castRepeatMode: Int): Pair<PlaybackRepeatMode, Boolean> {
        return when (castRepeatMode) {
            MediaStatus.REPEAT_MODE_REPEAT_SINGLE -> PlaybackRepeatMode.RepeatOne to false
            MediaStatus.REPEAT_MODE_REPEAT_ALL -> PlaybackRepeatMode.RepeatAll to false
            MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE -> PlaybackRepeatMode.RepeatAll to true
            else -> PlaybackRepeatMode.Off to false
        }
    }

    /**
     * Maps a [CastTrackPayload] into a Google Cast SDK [MediaInfo].
     */
    fun toCastMediaInfo(payload: CastTrackPayload): MediaInfo {
        val track = payload.track
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
            putString(MediaMetadata.KEY_TITLE, track.title)
            if (track.artist.isNotBlank()) {
                putString(MediaMetadata.KEY_ARTIST, track.artist)
            }
            if (track.album.isNotBlank()) {
                putString(MediaMetadata.KEY_ALBUM_TITLE, track.album)
            }
            val imageUrl = payload.artworkUrl?.takeIf { CastMimeResolver.isHttpUrl(it) }
            if (imageUrl != null) {
                runCatching { addImage(WebImage(Uri.parse(imageUrl))) }
            }
        }

        val builder = MediaInfo.Builder(payload.streamUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(payload.mimeType)
            .setContentUrl(payload.streamUrl)
            .setMetadata(metadata)
            .setCustomData(buildCustomData(track))

        if (track.durationMs > 0L) {
            builder.setStreamDuration(track.durationMs)
        }

        return builder.build()
    }

    /**
     * Wraps a [CastTrackPayload] into a Google Cast SDK [MediaQueueItem].
     */
    fun toCastMediaQueueItem(
        payload: CastTrackPayload,
        autoplay: Boolean = true,
        preloadTimeSeconds: Double = CastConfig.PRELOAD_TIME_SECONDS
    ): MediaQueueItem {
        val mediaInfo = toCastMediaInfo(payload)
        return MediaQueueItem.Builder(mediaInfo)
            .setAutoplay(autoplay)
            .setPreloadTime(preloadTimeSeconds)
            .setCustomData(buildCustomData(payload.track))
            .build()
    }

    /**
     * Reconstructs a [TrackItem] from a Cast SDK [MediaInfo] and optional known queue.
     */
    fun fromCastMediaInfo(
        mediaInfo: MediaInfo?,
        knownQueue: List<TrackItem>
    ): TrackItem? {
        if (mediaInfo == null) return null
        val metadata = mediaInfo.metadata
        val title = metadata?.getString(MediaMetadata.KEY_TITLE)
        val artist = metadata?.getString(MediaMetadata.KEY_ARTIST)
        val album = metadata?.getString(MediaMetadata.KEY_ALBUM_TITLE)
        val artworkUrl = metadata?.images?.firstOrNull()?.url?.toString()
        val durationMs = mediaInfo.streamDuration.takeIf { it > 0L } ?: 0L
        return resolveTrackFromCustomData(
            customData = mediaInfo.customData,
            knownQueue = knownQueue,
            fallbackTitle = title,
            fallbackArtist = artist,
            fallbackAlbum = album,
            fallbackArtworkUrl = artworkUrl,
            fallbackDurationMs = durationMs
        )
    }

    /**
     * Pure helper computing the next queue index when navigating forward/backward on Cast.
     */
    fun computeNextQueueIndex(
        currentIndex: Int,
        queueSize: Int,
        repeatMode: PlaybackRepeatMode
    ): Int? {
        if (queueSize <= 0) return null
        val safeCurrent = currentIndex.coerceIn(0, queueSize - 1)
        return when {
            safeCurrent < queueSize - 1 -> safeCurrent + 1
            repeatMode == PlaybackRepeatMode.RepeatAll -> 0
            else -> null
        }
    }

    fun computePreviousQueueIndex(
        currentIndex: Int,
        queueSize: Int,
        repeatMode: PlaybackRepeatMode
    ): Int? {
        if (queueSize <= 0) return null
        val safeCurrent = currentIndex.coerceIn(0, queueSize - 1)
        return when {
            safeCurrent > 0 -> safeCurrent - 1
            repeatMode == PlaybackRepeatMode.RepeatAll -> queueSize - 1
            else -> 0
        }
    }
}

/**
 * Pure Kotlin state reducer for Chromecast session transitions and local <-> remote handoffs.
 * Enables deterministic JVM unit testing of connection lifecycle, progress updates,
 * volume/mute changes, GMS-unavailable fallback, and disconnect handoff preservation.
 */
object CastStateReducer {

    fun markUnavailable(current: CastPlaybackState): CastPlaybackState {
        return current.copy(
            connectionState = CastConnectionState.UNAVAILABLE,
            deviceName = null,
            deviceId = null,
            isPlaying = false,
            isBuffering = false,
            errorMessage = null
        )
    }

    fun onConnecting(
        current: CastPlaybackState,
        deviceName: String?,
        deviceId: String?
    ): CastPlaybackState {
        return current.copy(
            connectionState = CastConnectionState.CONNECTING,
            deviceName = deviceName ?: current.deviceName,
            deviceId = deviceId ?: current.deviceId,
            isBuffering = true,
            errorMessage = null
        )
    }

    fun onReconnecting(current: CastPlaybackState): CastPlaybackState {
        return current.copy(
            connectionState = CastConnectionState.RECONNECTING,
            isBuffering = true,
            errorMessage = null
        )
    }

    fun onSessionConnected(
        current: CastPlaybackState,
        deviceName: String?,
        deviceId: String?,
        volume: Float,
        isMuted: Boolean,
        handoff: CastHandoffSnapshot?
    ): CastPlaybackState {
        return current.copy(
            connectionState = CastConnectionState.CONNECTED,
            deviceName = deviceName ?: current.deviceName,
            deviceId = deviceId ?: current.deviceId,
            volume = volume.coerceIn(0f, 1f),
            isMuted = isMuted,
            currentTrack = handoff?.currentTrack ?: current.currentTrack,
            queue = handoff?.queue?.takeIf { it.isNotEmpty() } ?: current.queue,
            currentQueueIndex = if (handoff != null && handoff.hasPlayableContent) {
                handoff.currentQueueIndex
            } else {
                current.currentQueueIndex
            },
            positionMs = handoff?.positionMs ?: current.positionMs,
            durationMs = handoff?.currentTrack?.durationMs?.takeIf { it > 0L } ?: current.durationMs,
            isPlaying = handoff?.wasPlaying ?: current.isPlaying,
            isBuffering = handoff?.hasPlayableContent == true,
            repeatMode = handoff?.repeatMode ?: current.repeatMode,
            isShuffleEnabled = handoff?.isShuffleEnabled ?: current.isShuffleEnabled,
            errorMessage = null
        )
    }

    fun onSessionDisconnected(
        current: CastPlaybackState,
        latestPositionMs: Long? = null,
        errorMessage: String? = null
    ): Pair<CastPlaybackState, CastHandoffSnapshot> {
        val snapshotState = if (latestPositionMs != null && latestPositionMs >= 0L) {
            current.copy(positionMs = latestPositionMs)
        } else {
            current
        }
        val handoff = CastHandoffSnapshot.fromCastState(snapshotState)
        val disconnectedState = CastPlaybackState(
            connectionState = if (current.connectionState == CastConnectionState.UNAVAILABLE) {
                CastConnectionState.UNAVAILABLE
            } else {
                CastConnectionState.DISCONNECTED
            },
            errorMessage = errorMessage
        )
        return disconnectedState to handoff
    }

    fun onRemoteProgress(
        current: CastPlaybackState,
        positionMs: Long,
        durationMs: Long
    ): CastPlaybackState {
        if (!current.isConnected) return current
        val safePos = positionMs.coerceAtLeast(0L)
        val safeDur = if (durationMs > 0L) durationMs else current.durationMs
        return current.copy(
            positionMs = safePos,
            durationMs = safeDur
        )
    }

    fun onRemoteVolumeChanged(
        current: CastPlaybackState,
        volume: Float,
        isMuted: Boolean
    ): CastPlaybackState {
        return current.copy(
            volume = volume.coerceIn(0f, 1f),
            isMuted = isMuted
        )
    }
}
