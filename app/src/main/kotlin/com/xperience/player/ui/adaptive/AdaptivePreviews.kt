package com.xperience.player.ui.adaptive

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import com.xperience.player.domain.model.AlbumDetailProfile
import com.xperience.player.domain.model.AlbumItem
import com.xperience.player.domain.model.ArtistDetailProfile
import com.xperience.player.domain.model.ArtistItem
import com.xperience.player.domain.model.NewReleaseItem
import com.xperience.player.domain.model.PlaybackRepeatMode
import com.xperience.player.domain.model.PlaybackState
import com.xperience.player.domain.model.RadioStationItem
import com.xperience.player.domain.model.RecommendedPlaylistItem
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.domain.model.UserPlaylist
import com.xperience.player.domain.model.YtmMoodCategory
import com.xperience.player.domain.model.YtmMoodCategoryDetail
import com.xperience.player.domain.model.YtmMoodSection
import com.xperience.player.domain.model.YtmMoodsAndGenresFeed
import com.xperience.player.domain.model.lyrics.LyricLine
import com.xperience.player.service.SearchResultData
import com.xperience.player.ui.theme.DarkThemeColors
import com.xperience.player.ui.theme.LightThemeColors
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.XperiencePlayerTheme

/**
 * Shared Compose Preview wrapper injecting realistic [WindowSizeClass], chrome padding insets,
 * theme colors, and Material 3 theme tokens for Phone (360x800dp), Tablet Portrait (800x1280dp),
 * and Tablet Landscape (1280x800dp) verification.
 */
@Composable
internal fun AdaptivePreviewSurface(
    widthDp: Int,
    heightDp: Int,
    darkTheme: Boolean = true,
    accentColor: Color = Color(0xFF38BDF8),
    content: @Composable () -> Unit
) {
    val windowSizeClass = WindowSizeClass(minWidthDp = widthDp, minHeightDp = heightDp)
    val appColors = if (darkTheme) DarkThemeColors else LightThemeColors
    val topChromePadding = if (windowSizeClass.usesTopDesktopChrome) 82.dp else 0.dp
    val bottomChromePadding = if (windowSizeClass.usesTopDesktopChrome) 36.dp else 180.dp

    XperiencePlayerTheme(darkTheme = darkTheme) {
        ProvideAppWindowSizeClass(windowSizeClass = windowSizeClass) {
            CompositionLocalProvider(
                LocalAppColors provides appColors,
                LocalAccentColor provides accentColor,
                LocalTopChromePadding provides topChromePadding,
                LocalBottomChromePadding provides bottomChromePadding
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = appColors.background
                ) {
                    content()
                }
            }
        }
    }
}

internal object AdaptivePreviewFixtures {
    val tracks: List<TrackItem> = listOf(
        TrackItem(
            id = "ytm_paparazzi",
            title = "Paparazzi",
            artist = "Lady Gaga",
            album = "The Fame",
            durationMs = 208_000L,
            isLiked = true,
            playCount = 910_000_000L
        ),
        TrackItem(
            id = "ytm_wrote_a_song",
            title = "I Wrote A Song",
            artist = "Mae Muller",
            album = "Sorry I'm Late",
            durationMs = 165_000L,
            isLiked = true,
            isExplicit = true,
            playCount = 128_000_000L
        ),
        TrackItem(
            id = "ytm_better_days",
            title = "Better Days",
            artist = "NEIKED, Mae Muller, Polo G",
            album = "Better Days",
            durationMs = 160_000L,
            playCount = 430_000_000L
        ),
        TrackItem(
            id = "ytm_starfucker",
            title = "Starfucker",
            artist = "Slayyyter",
            album = "STARFUCKER",
            durationMs = 192_000L,
            isExplicit = true,
            playCount = 45_000_000L
        ),
        TrackItem(
            id = "ytm_memories",
            title = "Memories Of You",
            artist = "Slayyyter",
            album = "STARFUCKER",
            durationMs = 211_000L,
            playCount = 29_000_000L
        ),
        TrackItem(
            id = "ytm_love_you_so",
            title = "Love You So",
            artist = "The King Khan & BBQ Show",
            album = "Invisible Girl",
            durationMs = 204_000L,
            playCount = 310_000_000L
        )
    )

    val albums: List<AlbumItem> = listOf(
        AlbumItem(id = "alb_1", title = "Sorry I'm Late", artist = "Mae Muller", year = 2023, tracksCount = 17),
        AlbumItem(id = "alb_2", title = "The Fame", artist = "Lady Gaga", year = 2008, tracksCount = 15),
        AlbumItem(id = "alb_3", title = "STARFUCKER", artist = "Slayyyter", year = 2023, tracksCount = 12),
        AlbumItem(id = "alb_4", title = "no one else, not ever", artist = "Mae Muller", year = 2020, tracksCount = 8)
    )

    val artists: List<ArtistItem> = listOf(
        ArtistItem(id = "art_1", name = "Lady Gaga", genre = "Pop", subscribers = "26.4M"),
        ArtistItem(id = "art_2", name = "Mae Muller", genre = "Pop", subscribers = "412K"),
        ArtistItem(id = "art_3", name = "Slayyyter", genre = "Electropop", subscribers = "285K"),
        ArtistItem(id = "art_4", name = "MARINA", genre = "Alt Pop", subscribers = "2.9M")
    )

    val playlists: List<UserPlaylist> = listOf(
        UserPlaylist(
            id = "pl_1",
            title = "Late Night Neon",
            description = "Upbeat synth & electropop favorites for evening drives",
            tracks = tracks
        ),
        UserPlaylist(
            id = "pl_2",
            title = "Pop Essentials",
            description = "Curated high-energy chart toppers",
            tracks = tracks.take(4)
        ),
        UserPlaylist(
            id = "pl_3",
            title = "Chill Acoustic",
            description = "Unplugged sessions & relaxed mornings",
            tracks = tracks.drop(2)
        )
    )

    val playbackState: PlaybackState = PlaybackState(
        currentTrack = tracks.first(),
        isPlaying = true,
        currentPositionMs = 74_000L,
        durationMs = 208_000L,
        repeatMode = PlaybackRepeatMode.RepeatAll,
        isShuffleEnabled = false,
        queue = tracks,
        currentQueueIndex = 0
    )

    val lyrics: List<LyricLine> = listOf(
        LyricLine(0L, "We are the crowd, we're c-coming out"),
        LyricLine(65_000L, "Got my flash on, it's true"),
        LyricLine(72_000L, "Need that picture of you"),
        LyricLine(79_000L, "It's so magical, we'd be so fantastical"),
        LyricLine(88_000L, "Leather and jeans, garage glamorous")
    )

    val albumProfile: AlbumDetailProfile = AlbumDetailProfile(
        id = "alb_1",
        title = "Sorry I'm Late",
        artist = "Mae Muller",
        releaseYear = 2023,
        description = "Debut studio album blending candid songwriting with infectious dance-pop hooks.",
        tracks = tracks,
        otherVersions = albums.take(2),
        otherAlbums = albums
    )

    val artistProfile: ArtistDetailProfile = ArtistDetailProfile(
        name = "Lady Gaga",
        subscribersCountText = "26.4M",
        monthlyListenersText = "58.2M monthly listeners",
        biography = "Renowned singer, songwriter, and actress pushing boundaries across pop, electronic, and jazz.",
        topTracks = tracks,
        combinedAlbums = albums,
        singlesAndEps = albums.take(2),
        similarArtists = artists
    )

    val radioStations: List<RadioStationItem> = listOf(
        RadioStationItem("rad_1", "Mae Muller Radio", "Inspired by I Wrote A Song", null, "RDAMVM"),
        RadioStationItem("rad_2", "Lady Gaga Radio", "Station For You", null, "RDAMVM2"),
        RadioStationItem("rad_3", "Electropop Mix", "Slayyyter, Kim Petras, Charli XCX", null, "RDAMVM3"),
        RadioStationItem("rad_4", "Dance Hits 2026", "Non-stop club energy", null, "RDAMVM4")
    )

    val newReleases: List<NewReleaseItem> = listOf(
        NewReleaseItem("rel_1", "Mayhem", "Lady Gaga", "Album • 2025", null, isAlbum = true, year = "2025"),
        NewReleaseItem("rel_2", "I Wrote A Song", "Mae Muller", "Single • 2024", null, isAlbum = false, year = "2024"),
        NewReleaseItem("rel_3", "STARFUCKER Deluxe", "Slayyyter", "Album • 2024", null, isAlbum = true, year = "2024"),
        NewReleaseItem("rel_4", "Cuntissimo", "MARINA", "Single • 2025", null, isAlbum = false, year = "2025")
    )

    val moodsFeed: YtmMoodsAndGenresFeed = YtmMoodsAndGenresFeed(
        sections = listOf(
            YtmMoodSection(
                title = "Moods & Moments",
                items = listOf(
                    YtmMoodCategory("m1", "Feel Good", "FEmood_1", stripeColorArgb = 0xFF34D399L),
                    YtmMoodCategory("m2", "Workout", "FEmood_2", stripeColorArgb = 0xFFF97316L),
                    YtmMoodCategory("m3", "Late Night", "FEmood_3", stripeColorArgb = 0xFF8B5CF6L),
                    YtmMoodCategory("m4", "Focus", "FEmood_4", stripeColorArgb = 0xFF38BDF8L)
                )
            )
        )
    )

    val moodDetail: YtmMoodCategoryDetail = YtmMoodCategoryDetail(
        categoryTitle = "Late Night",
        browseId = "FEmood_3",
        playlists = listOf(
            RecommendedPlaylistItem("rp1", "Neon Synth Drives", "Midnight pop & dreamwave", null, "PL1"),
            RecommendedPlaylistItem("rp2", "After Hours", "Velvet beats & R&B", null, "PL2")
        ),
        albums = albums,
        songs = tracks
    )

    val searchResults: SearchResultData = SearchResultData(
        songs = tracks,
        albums = albums,
        artists = artists
    )
}
