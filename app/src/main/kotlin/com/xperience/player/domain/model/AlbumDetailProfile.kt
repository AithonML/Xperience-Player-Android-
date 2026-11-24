package com.xperience.player.domain.model

data class AlbumDetailProfile(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val coverArtUri: String = "",
    val releaseYear: Int = 0,
    val albumInformation: String = "",
    val description: String = "",
    val canvasVideoUri: String = "",
    val tracks: List<TrackItem> = emptyList(),
    val otherVersions: List<AlbumItem> = emptyList(),
    val otherAlbums: List<AlbumItem> = emptyList(),
    val isTracksLoading: Boolean = false,
    val isCanvasLoading: Boolean = false
) {
    val hasCoverArt: Boolean
        get() = coverArtUri.isNotBlank()

    val hasCanvasVideo: Boolean
        get() = canvasVideoUri.isNotBlank()

    val totalDurationMs: Long
        get() = tracks.sumOf { it.durationMs }

    val formattedTotalDuration: String
        get() {
            val totalSeconds = totalDurationMs / 1000
            val minutes = totalSeconds / 60
            val hours = minutes / 60
            val remMinutes = minutes % 60
            return if (hours > 0) {
                "${hours}h ${remMinutes}m"
            } else {
                "${minutes} min"
            }
        }

    val displaySubtitle: String
        get() {
            val parts = mutableListOf<String>()
            if (releaseYear > 0) parts.add(releaseYear.toString())
            if (tracks.isNotEmpty()) parts.add("${tracks.size} ${if (tracks.size == 1) "song" else "songs"}")
            if (totalDurationMs > 0) parts.add(formattedTotalDuration)
            return if (parts.isNotEmpty()) parts.joinToString(" • ") else albumInformation
        }
}
