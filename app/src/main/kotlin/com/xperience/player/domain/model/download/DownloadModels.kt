package com.xperience.player.domain.model.download

/**
 * Supported target audio formats and quality tiers for offline track downloads.
 */
enum class DownloadAudioFormat(
    val displayName: String,
    val badge: String,
    val extension: String,
    val mimeType: String
) {
    FLAC_LOSSLESS("FLAC Lossless (Mornye)", "FLAC", ".flac", "audio/flac"),
    MP3_320K("MP3 320kbps (YT Music)", "MP3 320k", ".mp3", "audio/mpeg"),
    M4A("M4A", "M4A", ".m4a", "audio/mp4")
}

/**
 * User-configurable preferences controlling download quality, metadata tagging,
 * synchronized lyrics (.lrc), and offline Apple Music Canvas motion video sidecars.
 */
data class DownloadMetadataOptions(
    val defaultFormat: DownloadAudioFormat = DownloadAudioFormat.FLAC_LOSSLESS,
    val downloadDirectory: String = "",
    val includeTitleAndArtist: Boolean = true,
    val includeAlbumAndTrackNumber: Boolean = true,
    val includeHighResCoverArt: Boolean = true,
    val includeGenreAndReleaseYear: Boolean = true,
    val includeIsrcAndCopyright: Boolean = true,
    val includeReplayGain: Boolean = true,
    val includeSynchronizedLyrics: Boolean = true,
    val includeStaticLyrics: Boolean = true,
    val includeAppleCanvasOffline: Boolean = true
)

/**
 * Represents a verified track match or alternative recording version discovered across
 * Mornye's lossless/metadata providers (Tidal, Deezer, GDStudio Lossless, YouTube Music).
 */
data class TrackDownloadCandidate(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val coverArtUri: String = "",
    val isrc: String = "",
    val copyright: String = "",
    val replayGain: String = "",
    val genre: String = "Pop",
    val providerLabel: String = "Mornye Lossless",
    val qualityBadge: String = "FLAC Lossless",
    val versionBadge: String = "Original Studio",
    val matchScorePercent: Int = 98,
    val isRecommended: Boolean = false,
    val youTubeVideoId: String? = null,
    val tidalTrackId: String? = null,
    val deezerTrackId: String? = null,
    val gdStudioSource: String? = null,
    val gdStudioUrlId: String? = null,
    val releaseYear: Int = 2024,
    val trackNumber: Int = 1
)

/**
 * Result of a completed or failed track download operation.
 */
data class TrackDownloadResult(
    val success: Boolean,
    val audioFilePath: String,
    val lrcFilePath: String? = null,
    val canvasVideoFilePath: String? = null,
    val formatBadge: String = "",
    val providerSummary: String = "",
    val errorMessage: String? = null
)

/**
 * Progress report during a download task.
 */
data class DownloadProgressState(
    val trackId: String,
    val percent: Float = 0f,
    val stage: String = "",
    val isDownloading: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null
)
