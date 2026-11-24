package com.xperience.player.domain.model

enum class TopPickKind { PERSONAL_STATION, NEW_RELEASE, FEATURED_PLAYLIST, ARTIST_SPOTLIGHT }

data class TopPickItem(
    val id: String,
    val eyebrow: String,
    val title: String,
    val subtitle: String,
    val artworkUri: String?,
    val secondaryArtworkUris: List<String> = emptyList(),
    val kind: TopPickKind,
    val browseId: String? = null,
    val playlistId: String? = null,
    val videoId: String? = null,
    val artistName: String? = null,
    val albumTitle: String? = null,
    val seedTrack: TrackItem? = null
)

data class MoreLikeAlbumSectionData(
    val id: String,
    val seedAlbumTitle: String,
    val sectionTitle: String = "More like: $seedAlbumTitle",
    val albums: List<AlbumItem>
)

data class RecommendedPlaylistItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val artworkUri: String?,
    val playlistId: String,
    val browseId: String? = null,
    val author: String? = null,
    val trackCountText: String? = null
)

data class YouTubeMusicLibraryPlaylist(
    val playlistId: String,
    val browseId: String,
    val title: String,
    val subtitle: String = "",
    val artworkUri: String = "",
    val author: String = "",
    val trackCountText: String = ""
)

data class RadioStationItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val artworkUri: String?,
    val playlistId: String,
    val seedVideoId: String? = null,
    val params: String? = null
)

data class NewReleaseItem(
    val id: String,
    val title: String,
    val artist: String,
    val subtitle: String,
    val artworkUri: String?,
    val isAlbum: Boolean,
    val browseId: String? = null,
    val videoId: String? = null,
    val year: String? = null
)

data class YtmAccountState(
    val isSignedIn: Boolean = false,
    val accountName: String? = null,
    val accountHandle: String? = null,
    val avatarUrl: String? = null,
    val lastSyncedAtMs: Long = 0L
)

data class HomeDiscoveryFeed(
    val personalizedSubtitle: String,
    val recentlyPlayedTracks: List<TrackItem>,
    val relatedArtists: List<ArtistItem>,
    val recommendedTracks: List<TrackItem> = emptyList(),
    val recommendedAlbums: List<AlbumItem> = emptyList(),
    val genreTags: List<String> = emptyList(),
    val selectedGenreTag: String = "Pop",
    val genreTracks: List<TrackItem> = emptyList(),
    val topPicks: List<TopPickItem> = emptyList(),
    val moreLikeSections: List<MoreLikeAlbumSectionData> = emptyList(),
    val playlistsMadeForYou: List<RecommendedPlaylistItem> = emptyList(),
    val smartSuggestedPlaylists: List<UserPlaylist> = emptyList(),
    val radioStations: List<RadioStationItem> = emptyList(),
    val allRadioStations: List<RadioStationItem> = emptyList(),
    val newReleases: List<NewReleaseItem> = emptyList(),
    val allNewReleases: List<NewReleaseItem> = emptyList(),
    val isPersonalizedFromAccount: Boolean = false,
    val isFeedLoading: Boolean = true
)
