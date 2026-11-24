package com.xperience.player.domain.model.lyrics

/**
 * Represents a single line of fixed or synchronized lyrics.
 */
data class LyricLine(
    val timestampMs: Long,
    val text: String,
    val isSynchronized: Boolean = true,
    val endTimestampMs: Long? = null,
    val syllables: List<LyricSyllableSegment>? = null
) {
    /**
     * Formatted timestamp badge string (m:ss).
     */
    val formattedTimestamp: String
        get() {
            val totalSeconds = timestampMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }

    /**
     * Indicates whether this line contains sub-line word/character timing segments.
     */
    val hasProgressiveSegments: Boolean
        get() = !syllables.isNullOrEmpty()
}
