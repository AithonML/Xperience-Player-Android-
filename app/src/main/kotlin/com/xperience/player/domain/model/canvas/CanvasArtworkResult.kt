package com.xperience.player.domain.model.canvas

/**
 * Represents a live motion artwork (canvas video) resolved for an album or track.
 */
data class CanvasArtworkResult(
    val videoUri: String,
    val providerName: String = "Apple Music Canvas",
    val previewFrameUri: String? = null,
    val albumId: String? = null,
    val isHls: Boolean = videoUri.contains(".m3u8", ignoreCase = true)
)
