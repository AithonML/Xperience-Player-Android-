package com.xperience.player.domain.model

enum class PlaybackRepeatMode {
    Off,
    RepeatOne,
    RepeatAll
}

data class PlaybackState(
    val currentTrack: TrackItem? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val repeatMode: PlaybackRepeatMode = PlaybackRepeatMode.RepeatAll,
    val isShuffleEnabled: Boolean = false,
    val queue: List<TrackItem> = emptyList(),
    val volume: Float = 1.0f,
    val currentQueueIndex: Int = -1
) {
    val progressFraction: Float
        get() = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}
