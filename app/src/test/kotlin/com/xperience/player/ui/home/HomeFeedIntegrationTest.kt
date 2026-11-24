package com.xperience.player.ui.home

import com.xperience.player.domain.model.NewReleaseItem
import com.xperience.player.domain.model.TopPickItem
import com.xperience.player.domain.model.TopPickKind
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.domain.model.YtmAccountState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFeedIntegrationTest {

    @Test
    fun resolveTopPickClickAction_routesNewReleaseAlbumToOpenAlbum() {
        val pick = TopPickItem(
            id = "tp_nr_1",
            eyebrow = "NEW RELEASE",
            title = "Hurry Up Tomorrow",
            subtitle = "The Weeknd • 2025",
            artworkUri = "https://lh3.googleusercontent.com/hut.jpg",
            kind = TopPickKind.NEW_RELEASE,
            browseId = "MPREb_hut",
            artistName = "The Weeknd",
            albumTitle = "Hurry Up Tomorrow"
        )

        val action = resolveTopPickClickAction(pick)
        assertTrue(action is HomeFeedClickAction.OpenAlbum)
        val openAlbum = action as HomeFeedClickAction.OpenAlbum
        assertEquals("MPREb_hut", openAlbum.album.id)
        assertEquals("Hurry Up Tomorrow", openAlbum.album.title)
        assertEquals("The Weeknd", openAlbum.album.artist)
        assertEquals("https://lh3.googleusercontent.com/hut.jpg", openAlbum.album.coverUri)
        assertEquals(2025, openAlbum.album.year)
    }

    @Test
    fun resolveTopPickClickAction_routesNewReleaseSingleToPlayTrack() {
        val pick = TopPickItem(
            id = "tp_nr_single",
            eyebrow = "NEW RELEASE",
            title = "Timeless",
            subtitle = "The Weeknd • Single",
            artworkUri = "https://lh3.googleusercontent.com/timeless.jpg",
            kind = TopPickKind.NEW_RELEASE,
            videoId = "abc123",
            artistName = "The Weeknd"
        )

        val action = resolveTopPickClickAction(pick)
        assertTrue(action is HomeFeedClickAction.PlayTrack)
        val playTrack = action as HomeFeedClickAction.PlayTrack
        assertEquals("ytm_abc123", playTrack.track.id)
        assertEquals("Timeless", playTrack.track.title)
        assertEquals("The Weeknd", playTrack.track.artist)
    }

    @Test
    fun resolveTopPickClickAction_routesPersonalStationAndFeaturedPlaylistCorrectly() {
        val stationPick = TopPickItem(
            id = "tp_radio_1",
            eyebrow = "PERSONAL STATION",
            title = "Daft Punk Radio",
            subtitle = "Daft Punk, Justice",
            artworkUri = "https://lh3.googleusercontent.com/dp.jpg",
            kind = TopPickKind.PERSONAL_STATION,
            playlistId = "RDEM_daft",
            videoId = "seed123"
        )
        val stationAction = resolveTopPickClickAction(stationPick)
        assertTrue(stationAction is HomeFeedClickAction.ResolveRadio)
        val radio = (stationAction as HomeFeedClickAction.ResolveRadio).station
        assertEquals("RDEM_daft", radio.playlistId)
        assertEquals("seed123", radio.seedVideoId)

        val playlistPick = TopPickItem(
            id = "tp_pl_1",
            eyebrow = "PLAYLIST FOR YOU",
            title = "My Supermix",
            subtitle = "YouTube Music",
            artworkUri = "https://lh3.googleusercontent.com/mix.jpg",
            kind = TopPickKind.FEATURED_PLAYLIST,
            browseId = "VLRDTMAK5uy_mix",
            playlistId = "RDTMAK5uy_mix"
        )
        val playlistAction = resolveTopPickClickAction(playlistPick)
        assertTrue(playlistAction is HomeFeedClickAction.ResolvePlaylist)
        val pl = (playlistAction as HomeFeedClickAction.ResolvePlaylist).playlist
        assertEquals("RDTMAK5uy_mix", pl.playlistId)
        assertEquals("VLRDTMAK5uy_mix", pl.browseId)
    }

    @Test
    fun resolveTopPickClickAction_routesArtistSpotlightAndSeedTrack() {
        val artistPick = TopPickItem(
            id = "tp_artist_1",
            eyebrow = "ARTIST SPOTLIGHT",
            title = "Justice",
            subtitle = "Electronic",
            artworkUri = "https://yt3.ggpht.com/justice.jpg",
            kind = TopPickKind.ARTIST_SPOTLIGHT,
            artistName = "Justice"
        )
        val artistAction = resolveTopPickClickAction(artistPick)
        assertTrue(artistAction is HomeFeedClickAction.OpenArtist)
        assertEquals("Justice", (artistAction as HomeFeedClickAction.OpenArtist).artistName)

        val seed = TrackItem(id = "local_1", title = "Genesis", artist = "Justice", album = "Cross")
        val seedPick = TopPickItem(
            id = "tp_seed_1",
            eyebrow = "PICKED FOR YOU",
            title = "Genesis",
            subtitle = "Justice • Cross",
            artworkUri = null,
            kind = TopPickKind.PERSONAL_STATION,
            seedTrack = seed
        )
        val seedAction = resolveTopPickClickAction(seedPick)
        assertEquals(HomeFeedClickAction.PlayTrack(seed), seedAction)
    }

    @Test
    fun resolveNewReleaseClickAction_handlesAlbumSongAndBrowseSingle() {
        val albumRelease = NewReleaseItem(
            id = "nr_alb",
            title = "Random Access Memories",
            artist = "Daft Punk",
            subtitle = "Album • 2013",
            artworkUri = "https://lh3.googleusercontent.com/ram.jpg",
            isAlbum = true,
            browseId = "MPREb_ram",
            year = "2013"
        )
        val albumAction = resolveNewReleaseClickAction(albumRelease)
        assertTrue(albumAction is HomeFeedClickAction.OpenAlbum)
        val album = (albumAction as HomeFeedClickAction.OpenAlbum).album
        assertEquals("MPREb_ram", album.id)
        assertEquals(2013, album.year)

        val songRelease = NewReleaseItem(
            id = "nr_song",
            title = "Midnight",
            artist = "M83",
            subtitle = "Single • 2025",
            artworkUri = "https://lh3.googleusercontent.com/m83.jpg",
            isAlbum = false,
            videoId = "ytm_m83vid"
        )
        val songAction = resolveNewReleaseClickAction(songRelease)
        assertTrue(songAction is HomeFeedClickAction.PlayTrack)
        assertEquals("ytm_m83vid", (songAction as HomeFeedClickAction.PlayTrack).track.id)

        val emptyRelease = NewReleaseItem(
            id = "nr_empty",
            title = "Unknown",
            artist = "",
            subtitle = "",
            artworkUri = null,
            isAlbum = false
        )
        assertEquals(HomeFeedClickAction.None, resolveNewReleaseClickAction(emptyRelease))
    }

    @Test
    fun ytmValidationAndBannerVisibility_behaveAsExpected() {
        assertFalse(isYtmAccountValidated(YtmAccountState(isSignedIn = false)))
        assertFalse(isYtmAccountValidated(YtmAccountState(isSignedIn = true, accountName = null, accountHandle = null)))
        assertTrue(isYtmAccountValidated(YtmAccountState(isSignedIn = true, accountName = "Alex")))
        assertTrue(isYtmAccountValidated(YtmAccountState(isSignedIn = true, accountHandle = "@alex")))

        assertTrue(shouldDisplayYtmLoginBanner(YtmAccountState(isSignedIn = false), shouldShowPrompt = true))
        assertFalse(shouldDisplayYtmLoginBanner(YtmAccountState(isSignedIn = false), shouldShowPrompt = false))
        assertFalse(shouldDisplayYtmLoginBanner(YtmAccountState(isSignedIn = true, accountName = "Alex"), shouldShowPrompt = true))
    }

    @Test
    fun normalizeAuroraArtworkUri_trimsAndHandlesEmpty() {
        assertNull(normalizeAuroraArtworkUri(null))
        assertNull(
            normalizeAuroraArtworkUri(
                TrackItem(id = "1", title = "T", artist = "A", album = "Al", artworkUri = "   ")
            )
        )
        assertEquals(
            "https://example.com/art.jpg",
            normalizeAuroraArtworkUri(
                TrackItem(id = "1", title = "T", artist = "A", album = "Al", artworkUri = "  https://example.com/art.jpg ")
            )
        )
    }
}
