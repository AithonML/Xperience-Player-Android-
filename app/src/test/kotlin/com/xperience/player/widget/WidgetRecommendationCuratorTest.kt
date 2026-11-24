package com.xperience.player.widget

import com.xperience.player.domain.model.RadioStationItem
import com.xperience.player.domain.model.RecommendedPlaylistItem
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.domain.model.UserPlaylist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetRecommendationCuratorTest {

    private fun sampleTrack(id: String, artworkUri: String? = "https://img/$id.jpg"): TrackItem {
        return TrackItem(
            id = id,
            title = "Track $id",
            artist = "Artist $id",
            album = "Album $id",
            durationMs = 200_000L,
            artworkUri = artworkUri
        )
    }

    @Test
    fun curateInterleavesDiverseSourcesAndInjectsLikedSongs() {
        val userPlaylists = listOf(
            UserPlaylist(
                id = "u_small",
                title = "Small List",
                tracks = listOf(sampleTrack("1"))
            ),
            UserPlaylist(
                id = "u_big",
                title = "Workout Hits",
                customCoverImagePath = "/storage/covers/workout.jpg",
                tracks = listOf(sampleTrack("2"), sampleTrack("3"), sampleTrack("4"))
            )
        )
        val smartPlaylists = listOf(
            UserPlaylist(
                id = "smart_1",
                title = "Late Night Chill",
                isSmartSuggestion = true,
                suggestionBadge = "Smart Mix",
                tracks = listOf(sampleTrack("5"), sampleTrack("6"))
            )
        )
        val madeForYou = listOf(
            RecommendedPlaylistItem(
                id = "m1",
                title = "Supermix",
                subtitle = "Personalized",
                artworkUri = "https://img/supermix.jpg",
                playlistId = "RDTMAK5uy_supermix",
                browseId = "VLRDTMAK5uy_supermix",
                trackCountText = "50 songs"
            )
        )
        val radios = listOf(
            RadioStationItem(
                id = "r1",
                title = "Synthwave Radio",
                subtitle = "Quick Mix",
                artworkUri = "https://img/radio.jpg",
                playlistId = "RDAMVM123",
                seedVideoId = "123"
            )
        )

        val curated = WidgetRecommendationCurator.curate(
            userPlaylists = userPlaylists,
            smartPlaylists = smartPlaylists,
            madeForYou = madeForYou,
            radioStations = radios,
            likedCount = 42,
            likedArtworkUri = "https://img/liked.jpg"
        )

        // First round picks one from each source: Made For You, Smart, top populated User, Radio, then Liked Songs.
        assertEquals(WidgetPlaylistSourceType.MADE_FOR_YOU, curated[0].sourceType)
        assertEquals(50, curated[0].trackCount)
        assertEquals(WidgetPlaylistSourceType.SMART_PLAYLIST, curated[1].sourceType)
        assertEquals(WidgetPlaylistSourceType.USER_PLAYLIST, curated[2].sourceType)
        assertEquals("Workout Hits", curated[2].title) // richer playlist prioritized over Small List
        assertEquals("/storage/covers/workout.jpg", curated[2].artworkUri)
        assertEquals(WidgetPlaylistSourceType.RADIO_STATION, curated[3].sourceType)
        assertEquals(WidgetPlaylistSourceType.LIKED_SONGS, curated[4].sourceType)
        assertEquals(42, curated[4].trackCount)
    }

    @Test
    fun curateDeduplicatesTitlesAcrossSourcesAndUsesFallbackWhenOffline() {
        val duplicateTitleMadeForYou = listOf(
            RecommendedPlaylistItem(
                id = "m1",
                title = "Focus Mix",
                subtitle = "YTM",
                artworkUri = "https://img/m1.jpg",
                playlistId = "PL_FOCUS"
            )
        )
        val duplicateTitleUser = listOf(
            UserPlaylist(
                id = "u1",
                title = "  focus mix  ",
                tracks = listOf(sampleTrack("1"))
            )
        )

        val deduped = WidgetRecommendationCurator.curate(
            userPlaylists = duplicateTitleUser,
            smartPlaylists = emptyList(),
            madeForYou = duplicateTitleMadeForYou,
            radioStations = emptyList(),
            likedCount = 0
        )
        assertEquals(1, deduped.size)
        assertEquals(WidgetPlaylistSourceType.MADE_FOR_YOU, deduped[0].sourceType)

        // Offline fallback with zero playlists but liked + recently played tracks
        val offlineFallback = WidgetRecommendationCurator.curate(
            userPlaylists = emptyList(),
            smartPlaylists = emptyList(),
            madeForYou = emptyList(),
            radioStations = emptyList(),
            likedCount = 5,
            likedArtworkUri = "https://img/liked.jpg",
            recentlyPlayedTracks = listOf(sampleTrack("r1", "https://img/recent.jpg"))
        )
        assertEquals(2, offlineFallback.size)
        assertEquals(WidgetPlaylistSourceType.LIKED_SONGS, offlineFallback[0].sourceType)
        assertEquals(WidgetPlaylistSourceType.RECENTLY_PLAYED, offlineFallback[1].sourceType)
        assertEquals("https://img/recent.jpg", offlineFallback[1].artworkUri)
    }

    @Test
    fun curateSurfacesEmptyUserPlaylistsOnlyAsLastResort() {
        val emptyUserPlaylist = UserPlaylist(id = "u_empty", title = "New Ideas", tracks = emptyList())
        val populatedUserPlaylist = UserPlaylist(id = "u_full", title = "Road Trip", tracks = listOf(sampleTrack("1")))

        val curated = WidgetRecommendationCurator.curate(
            userPlaylists = listOf(emptyUserPlaylist, populatedUserPlaylist),
            smartPlaylists = emptyList(),
            madeForYou = emptyList(),
            radioStations = emptyList(),
            likedCount = 0
        )
        assertEquals(2, curated.size)
        assertEquals("Road Trip", curated[0].title)
        assertEquals("New Ideas", curated[1].title)
    }

    @Test
    fun selectWindowRotatesCyclicallyWithoutDuplicates() {
        val items = List(5) { idx ->
            WidgetPlaylistItem(id = "id_$idx", title = "Mix $idx", subtitle = "")
        }

        val page0 = WidgetRecommendationCurator.selectWindow(items, offset = 0, count = 4)
        assertEquals(listOf("id_0", "id_1", "id_2", "id_3"), page0.map { it.id })

        val pageWrapped = WidgetRecommendationCurator.selectWindow(items, offset = 3, count = 4)
        assertEquals(listOf("id_3", "id_4", "id_0", "id_1"), pageWrapped.map { it.id })

        val smallList = items.take(2)
        val smallWindow = WidgetRecommendationCurator.selectWindow(smallList, offset = 1, count = 4)
        assertEquals(listOf("id_1", "id_0"), smallWindow.map { it.id })

        assertTrue(WidgetRecommendationCurator.selectWindow(emptyList(), offset = 0, count = 4).isEmpty())
    }

    @Test
    fun buildSignatureDetectsChangesAndRemainsStable() {
        val a = listOf(WidgetPlaylistItem(id = "1", title = "Mix A", subtitle = "5 songs", trackCount = 5))
        val b = listOf(WidgetPlaylistItem(id = "1", title = "Mix A", subtitle = "5 songs", trackCount = 5))
        val c = listOf(WidgetPlaylistItem(id = "1", title = "Mix A", subtitle = "6 songs", trackCount = 6))

        assertEquals(WidgetRecommendationCurator.buildSignature(a), WidgetRecommendationCurator.buildSignature(b))
        assertNotEquals(WidgetRecommendationCurator.buildSignature(a), WidgetRecommendationCurator.buildSignature(c))
    }
}
