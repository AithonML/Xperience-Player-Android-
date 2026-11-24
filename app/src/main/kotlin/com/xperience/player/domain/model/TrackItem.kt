package com.xperience.player.domain.model

data class TrackItem(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long = 0L,
    val artworkResId: Int? = null,
    val artworkUri: String? = null,
    val audioUrl: String? = null,
    val isLiked: Boolean = false,
    val isExplicit: Boolean = false,
    val playCount: Long = 0L,
    val dateAddedMs: Long = 0L
) {
    val formattedDuration: String
        get() {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }

    val formattedPlayCount: String
        get() {
            if (playCount <= 0L) return ""
            return when {
                playCount >= 1_000_000_000L -> "%.0fB plays".format(playCount / 1_000_000_000.0)
                playCount >= 1_000_000L -> "%.0fM plays".format(playCount / 1_000_000.0)
                playCount >= 1_000L -> "%.0fK plays".format(playCount / 1_000.0)
                else -> "$playCount plays"
            }
        }
}
