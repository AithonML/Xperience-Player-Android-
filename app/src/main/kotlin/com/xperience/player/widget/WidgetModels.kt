package com.xperience.player.widget

import java.util.Locale

/**
 * Origin of a playlist or quick-mix card displayed inside Xperience home-screen widgets.
 */
enum class WidgetPlaylistSourceType(val wireValue: String) {
    USER_PLAYLIST("user_playlist"),
    SMART_PLAYLIST("smart_playlist"),
    MADE_FOR_YOU("made_for_you"),
    RADIO_STATION("radio_station"),
    LIKED_SONGS("liked_songs"),
    RECENTLY_PLAYED("recently_played");

    companion object {
        fun fromWireValue(raw: String?): WidgetPlaylistSourceType {
            val normalized = raw?.trim()?.lowercase(Locale.ROOT).orEmpty()
            return entries.firstOrNull { it.wireValue == normalized } ?: USER_PLAYLIST
        }
    }
}

/**
 * Lightweight, process-death-safe snapshot of current playback state persisted for widgets.
 */
data class WidgetPlaybackSnapshot(
    val trackId: String? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artworkUri: String? = null,
    val artworkResId: Int? = null,
    val audioUrl: String? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val isLiked: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queueSize: Int = 0,
    val accentColorArgb: Int? = null,
    val updatedAtMs: Long = 0L
) {
    val hasTrack: Boolean
        get() = !trackId.isNullOrBlank() && title.isNotBlank()

    val safePositionMs: Long
        get() = positionMs.coerceAtLeast(0L)

    val safeDurationMs: Long
        get() = durationMs.coerceAtLeast(0L)

    val progressPermille: Int
        get() {
            if (!hasTrack || safeDurationMs <= 0L) return 0
            val clamped = safePositionMs.coerceIn(0L, safeDurationMs)
            return ((clamped * 1000L) / safeDurationMs).toInt().coerceIn(0, 1000)
        }

    val formattedPosition: String
        get() = formatWidgetTimeMs(safePositionMs)

    val formattedDuration: String
        get() = formatWidgetTimeMs(safeDurationMs)
}

/**
 * Lightweight playlist or quick-mix recommendation item rendered by widgets.
 */
data class WidgetPlaylistItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val trackCount: Int = 0,
    val artworkUri: String? = null,
    val sourceType: WidgetPlaylistSourceType = WidgetPlaylistSourceType.USER_PLAYLIST,
    val playlistId: String? = null,
    val browseId: String? = null,
    val seedVideoId: String? = null,
    val params: String? = null
)

/**
 * Full snapshot consumed by all Xperience home-screen widgets.
 */
data class WidgetStateSnapshot(
    val playback: WidgetPlaybackSnapshot = WidgetPlaybackSnapshot(),
    val recommendations: List<WidgetPlaylistItem> = emptyList(),
    val recommendationsUpdatedAtMs: Long = 0L,
    val rotationOffset: Int = 0,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 1
    }
}

/**
 * Formats millisecond durations as `m:ss` (or `h:mm:ss` when >= 1 hour) for compact widget labels.
 */
fun formatWidgetTimeMs(durationMs: Long): String {
    val safeMs = durationMs.coerceAtLeast(0L)
    val totalSeconds = safeMs / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
