package com.xperience.player.domain.model.lyrics

/**
 * Represents a single word, syllable, or character timing segment within a lyric line
 * for progressive letter-by-letter / syllable-by-syllable karaoke synchronization.
 */
data class LyricSyllableSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String
)
