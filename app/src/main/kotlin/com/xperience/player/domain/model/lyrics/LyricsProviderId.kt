package com.xperience.player.domain.model.lyrics

/**
 * Identifies the supported online synchronized lyrics providers.
 * Matches the PC port architecture.
 */
enum class LyricsProviderId {
    /**
     * YouLyPlus (LyricsPlus v2 API) supporting word/syllable-by-syllable synchronization.
     */
    YouLyPlus,

    /**
     * Paxsenix Lyrically API supporting Apple Music syllable/word and line synchronization.
     */
    Paxsenix,

    /**
     * Better Lyrics TTML word/syllable-synchronized API.
     */
    BetterLyrics,

    /**
     * LRCLIB community synchronized LRC database.
     */
    LrcLib,

    /**
     * KuGou Music KRC/LRC synchronized lyrics service.
     */
    KuGou,

    /**
     * SimpMusic community synchronized lyrics service (keyed by YouTube videoId or search).
     */
    SimpMusic
}
