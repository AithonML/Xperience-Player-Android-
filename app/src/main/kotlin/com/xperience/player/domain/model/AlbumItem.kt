package com.xperience.player.domain.model

data class AlbumItem(
    val id: String,
    val title: String,
    val artist: String,
    val coverResId: Int? = null,
    val coverUri: String? = null,
    val year: Int = 2024,
    val tracksCount: Int = 12,
    val releaseMonth: Int = 0,
    val releaseDateText: String = "",
    val isSingleOrEp: Boolean = false
) {
    val releaseTypeBadgeText: String
        get() = when {
            isSingleOrEp || id.startsWith("ytm_single_") || tracksCount == 1 -> "SINGLE"
            else -> "ALBUM"
        }

    val formattedReleaseDate: String
        get() = when {
            releaseDateText.isNotBlank() -> releaseDateText
            releaseMonth in 1..12 && year > 0 -> "${monthName(releaseMonth)} $year"
            year > 0 -> "$year"
            else -> "Recent"
        }

    companion object {
        fun monthName(month: Int): String = when (month) {
            1 -> "January"
            2 -> "February"
            3 -> "March"
            4 -> "April"
            5 -> "May"
            6 -> "June"
            7 -> "July"
            8 -> "August"
            9 -> "September"
            10 -> "October"
            11 -> "November"
            12 -> "December"
            else -> ""
        }

        fun monthNameEs(month: Int): String = monthName(month)
    }
}
