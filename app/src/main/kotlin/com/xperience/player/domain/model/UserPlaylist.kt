package com.xperience.player.domain.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Represents a user-created playlist in Xperience Music Player.
 * Matches the PC port architecture (UserPlaylist) for full cross-platform compatibility:
 * supports custom cover image, automatic 2x2 collage of distinct track covers,
 * title, description, creation date, and ordered track items.
 */
data class UserPlaylist(
    val id: String = "pl_" + UUID.randomUUID().toString().replace("-", ""),
    val title: String,
    val description: String = "",
    val customCoverImagePath: String = "",
    val createdAt: String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(Date()),
    val tracks: List<TrackItem> = emptyList(),
    val isSmartSuggestion: Boolean = false,
    val suggestionBadge: String = "",
    val categoryTag: String = ""
) {
    /**
     * Whether a custom cover photo was selected by the user.
     */
    val hasCustomCover: Boolean
        get() = customCoverImagePath.isNotBlank()

    /**
     * Returns up to 4 distinct cover art URIs from the tracks in this playlist,
     * skipping duplicate album covers (matching PC port's GetDistinctCollageCovers).
     */
    fun getDistinctCollageCovers(): List<String> {
        val distinct = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        val seenAlbums = mutableSetOf<String>()

        for (track in tracks) {
            val uri = track.artworkUri ?: continue
            if (uri.isBlank()) continue

            val albumKey = if (track.album.isBlank()) uri else "${track.artist.trim()}|${track.album.trim()}"
            if (seen.add(uri.lowercase(Locale.ROOT)) && seenAlbums.add(albumKey.lowercase(Locale.ROOT))) {
                distinct.add(uri)
                if (distinct.size >= 4) break
            }
        }
        return distinct
    }

    /**
     * Resolves the cover URI for a specific collage slot (0 to 3).
     * If fewer than 4 distinct covers exist, cycles through available covers
     * (matching PC port's GetCollageSlotUri).
     */
    fun getCollageSlotUri(slotIndex: Int): String? {
        val covers = getDistinctCollageCovers()
        if (covers.isEmpty()) return null
        if (slotIndex < covers.size) return covers[slotIndex]
        return covers[slotIndex % covers.size]
    }

    val collageCover1: String? get() = getCollageSlotUri(0)
    val collageCover2: String? get() = getCollageSlotUri(1)
    val collageCover3: String? get() = getCollageSlotUri(2)
    val collageCover4: String? get() = getCollageSlotUri(3)

    /**
     * Formatted track count subtitle (e.g. "1 song" or "4 songs").
     */
    val trackCountSubtitle: String
        get() = if (tracks.size == 1) "1 song" else "${tracks.size} songs"

    /**
     * Summary line combining track count and optional description.
     */
    val summarySubtitle: String
        get() = if (description.isBlank()) trackCountSubtitle else "$trackCountSubtitle • $description"

    /**
     * Total duration of all tracks in milliseconds.
     */
    val totalDurationMs: Long
        get() = tracks.sumOf { it.durationMs }

    /**
     * Human readable total duration (e.g. "1 hr 12 min" or "28 min").
     */
    val formattedDuration: String
        get() {
            val totalSeconds = totalDurationMs / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            return when {
                hours > 0 -> "${hours} hr ${minutes} min"
                minutes > 0 -> "${minutes} min"
                else -> "${totalSeconds} sec"
            }
        }
}
