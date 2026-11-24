package com.xperience.player.domain.model.lyrics

/**
 * Encapsulates the resolved synchronized lyrics and provider metadata.
 */
data class LyricsResolutionResult(
    val providerName: String,
    val isWordOrCharSynced: Boolean,
    val lines: List<LyricLine>
)
