package com.xperience.player.service.cast

import androidx.compose.runtime.Immutable
import com.xperience.player.domain.model.PlaybackRepeatMode
import com.xperience.player.domain.model.TrackItem

/**
 * Connection lifecycle state for Chromecast sessions and framework availability.
 */
enum class CastConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    UNAVAILABLE
}

/**
 * Semantic device category for discovered MediaRouter Cast routes so custom UI
 * can render tailored icons (Chromecast dongle, Smart TV, Smart Speaker, Speaker Group).
 */
enum class CastDeviceType {
    CHROMECAST,
    TV,
    SPEAKER,
    SPEAKER_GROUP,
    UNKNOWN
}

/**
 * Immutable Compose-friendly representation of a discovered Chromecast / Google Cast route.
 */
@Immutable
data class CastRouteDevice(
    val id: String,
    val name: String,
    val description: String = "",
    val isSelected: Boolean = false,
    val volume: Int = 0,
    val volumeMax: Int = 100,
    val deviceType: CastDeviceType = CastDeviceType.CHROMECAST,
    val isConnecting: Boolean = false,
    val supportsVolumeControl: Boolean = true
) {
    val volumeFraction: Float
        get() = if (volumeMax > 0) (volume.toFloat() / volumeMax.toFloat()).coerceIn(0f, 1f) else 0f
}

/**
 * Immutable Compose-friendly snapshot of active Chromecast playback, remote queue,
 * remote device volume, and connection health.
 */
@Immutable
data class CastPlaybackState(
    val connectionState: CastConnectionState = CastConnectionState.DISCONNECTED,
    val deviceName: String? = null,
    val deviceId: String? = null,
    val currentTrack: TrackItem? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 1.0f,
    val isMuted: Boolean = false,
    val queue: List<TrackItem> = emptyList(),
    val currentQueueIndex: Int = -1,
    val repeatMode: PlaybackRepeatMode = PlaybackRepeatMode.Off,
    val isShuffleEnabled: Boolean = false,
    val errorMessage: String? = null
) {
    val isConnected: Boolean
        get() = connectionState == CastConnectionState.CONNECTED

    val isConnectingOrReconnecting: Boolean
        get() = connectionState == CastConnectionState.CONNECTING ||
            connectionState == CastConnectionState.RECONNECTING

    val isActiveSession: Boolean
        get() = isConnected || isConnectingOrReconnecting

    val isAvailable: Boolean
        get() = connectionState != CastConnectionState.UNAVAILABLE

    val progressFraction: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val hasNext: Boolean
        get() = queue.isNotEmpty() && (
            repeatMode == PlaybackRepeatMode.RepeatAll ||
                currentQueueIndex in 0 until queue.lastIndex
            )

    val hasPrevious: Boolean
        get() = queue.isNotEmpty() && (
            positionMs > 3_000L ||
                repeatMode == PlaybackRepeatMode.RepeatAll ||
                currentQueueIndex > 0
            )
}

/**
 * Bidirectional handoff state transferred between local ExoPlayer playback and a Cast session.
 *
 * - Local -> Cast: supplied when a Cast session starts so playback resumes at the exact track & timestamp.
 * - Cast -> Local: emitted when a Cast session ends so local ExoPlayer resumes seamlessly without losing progress.
 */
@Immutable
data class CastHandoffSnapshot(
    val currentTrack: TrackItem? = null,
    val queue: List<TrackItem> = emptyList(),
    val currentQueueIndex: Int = -1,
    val positionMs: Long = 0L,
    val wasPlaying: Boolean = false,
    val repeatMode: PlaybackRepeatMode = PlaybackRepeatMode.Off,
    val isShuffleEnabled: Boolean = false
) {
    val hasPlayableContent: Boolean
        get() = currentTrack != null || queue.isNotEmpty()

    companion object {
        fun fromLocalPlayback(
            currentTrack: TrackItem?,
            queue: List<TrackItem>,
            currentQueueIndex: Int,
            positionMs: Long,
            isPlaying: Boolean,
            repeatMode: PlaybackRepeatMode,
            isShuffleEnabled: Boolean
        ): CastHandoffSnapshot {
            val effectiveQueue = when {
                queue.isNotEmpty() -> queue
                currentTrack != null -> listOf(currentTrack)
                else -> emptyList()
            }
            val effectiveIndex = when {
                effectiveQueue.isEmpty() -> -1
                currentQueueIndex in effectiveQueue.indices -> currentQueueIndex
                currentTrack != null -> effectiveQueue.indexOfFirst { it.id == currentTrack.id }.coerceAtLeast(0)
                else -> 0
            }
            val effectiveTrack = currentTrack ?: effectiveQueue.getOrNull(effectiveIndex)
            return CastHandoffSnapshot(
                currentTrack = effectiveTrack,
                queue = effectiveQueue,
                currentQueueIndex = effectiveIndex,
                positionMs = positionMs.coerceAtLeast(0L),
                wasPlaying = isPlaying,
                repeatMode = repeatMode,
                isShuffleEnabled = isShuffleEnabled
            )
        }

        fun fromCastState(state: CastPlaybackState): CastHandoffSnapshot {
            val effectiveQueue = when {
                state.queue.isNotEmpty() -> state.queue
                state.currentTrack != null -> listOf(state.currentTrack)
                else -> emptyList()
            }
            val effectiveIndex = when {
                effectiveQueue.isEmpty() -> -1
                state.currentQueueIndex in effectiveQueue.indices -> state.currentQueueIndex
                state.currentTrack != null -> effectiveQueue.indexOfFirst { it.id == state.currentTrack.id }.coerceAtLeast(0)
                else -> 0
            }
            val effectiveTrack = state.currentTrack ?: effectiveQueue.getOrNull(effectiveIndex)
            return CastHandoffSnapshot(
                currentTrack = effectiveTrack,
                queue = effectiveQueue,
                currentQueueIndex = effectiveIndex,
                positionMs = state.positionMs.coerceAtLeast(0L),
                wasPlaying = state.isPlaying,
                repeatMode = state.repeatMode,
                isShuffleEnabled = state.isShuffleEnabled
            )
        }
    }
}
