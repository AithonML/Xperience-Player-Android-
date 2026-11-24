package com.xperience.player.domain.model

data class ArtistDetailProfile(
    val name: String = "",
    val avatarUri: String = "",
    val bannerImageUri: String = "",
    val customTitleLogoUri: String = "",
    val headerVideoUri: String = "",
    val subscribersCountText: String = "",
    val monthlyListenersText: String = "",
    val biography: String = "",
    val metadataSubtitle: String = "",
    val topTracks: List<TrackItem> = emptyList(),
    val combinedAlbums: List<AlbumItem> = emptyList(),
    val singlesAndEps: List<AlbumItem> = emptyList(),
    val artistPlaylists: List<AlbumItem> = emptyList(),
    val similarArtists: List<ArtistItem> = emptyList(),
    val latestRelease: AlbumItem? = null,
    val latestReleaseTypeBadge: String = latestRelease?.releaseTypeBadgeText ?: "",
    val latestReleaseDateText: String = latestRelease?.formattedReleaseDate ?: "",
    val officialVideos: List<TrackItem> = emptyList(),
    val isHeaderMediaLoading: Boolean = false,
    val isCatalogLoading: Boolean = false,
    val keyColorRgb: String = ""
) {
    val displaySubscribersPillText: String
        get() = if (subscribersCountText.isBlank()) "Subscribers" else "Subscribers $subscribersCountText"

    val hasCustomWordmark: Boolean
        get() = customTitleLogoUri.isNotBlank()

    val hasBanner: Boolean
        get() = bannerImageUri.isNotBlank()

    val hasVideo: Boolean
        get() = headerVideoUri.isNotBlank()
}
