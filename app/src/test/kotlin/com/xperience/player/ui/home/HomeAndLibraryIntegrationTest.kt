package com.xperience.player.ui.home

import com.xperience.player.domain.model.AlbumItem
import com.xperience.player.domain.model.HomeDiscoveryFeed
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.ui.components.NavigationTab
import com.xperience.player.ui.library.LibrarySubView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAndLibraryIntegrationTest {

    @Test
    fun homeDiscoveryFeed_defaultsToLoadingTrueUntilResolved() {
        val defaultFeed = HomeDiscoveryFeed(
            personalizedSubtitle = "Curated for you",
            recentlyPlayedTracks = emptyList(),
            relatedArtists = emptyList()
        )
        assertTrue(defaultFeed.isFeedLoading)
    }

    @Test
    fun isLocalLibraryTrack_identifiesLocalPrefixesAndPaths() {
        assertTrue(
            isLocalLibraryTrack(
                TrackItem(id = "local_media_1", title = "A", artist = "B", album = "C")
            )
        )
        assertTrue(
            isLocalLibraryTrack(
                TrackItem(id = "saf_doc_2", title = "A", artist = "B", album = "C")
            )
        )
        assertTrue(
            isLocalLibraryTrack(
                TrackItem(
                    id = "custom_3",
                    title = "A",
                    artist = "B",
                    album = "C",
                    audioUrl = "content://media/external/audio/media/3"
                )
            )
        )
        assertTrue(
            isLocalLibraryTrack(
                TrackItem(
                    id = "custom_4",
                    title = "A",
                    artist = "B",
                    album = "C",
                    audioUrl = "file:///storage/emulated/0/Music/song.flac"
                )
            )
        )
        assertFalse(
            isLocalLibraryTrack(
                TrackItem(
                    id = "ytm_vid123",
                    title = "A",
                    artist = "B",
                    album = "C",
                    audioUrl = "https://music.youtube.com/stream"
                )
            )
        )
    }

    @Test
    fun selectRecentlyAddedSourceCandidates_respectsSelectedSourceFilter() {
        val localTrack = TrackItem(
            id = "local_media_10",
            title = "Local Track",
            artist = "Artist L",
            album = "Album L",
            dateAddedMs = 2000L
        )
        val onlineTrack = TrackItem(
            id = "ytm_99",
            title = "Online Track",
            artist = "Artist O",
            album = "Album O",
            audioUrl = "https://example.com/audio.m4a",
            dateAddedMs = 3000L
        )
        val likedLocalDuplicateMissingTimestamp = localTrack.copy(dateAddedMs = 0L)
        val likedOnlineTrack = TrackItem(
            id = "ytm_liked_only",
            title = "Liked Online",
            artist = "Artist O2",
            album = "Album O2",
            dateAddedMs = 4000L
        )

        val localOnly = selectRecentlyAddedSourceCandidates(
            sourceType = MediaSourceType.LOCAL,
            localTracks = listOf(localTrack),
            onlineTracks = listOf(onlineTrack),
            likedTracks = listOf(likedLocalDuplicateMissingTimestamp, likedOnlineTrack)
        )
        assertEquals(listOf("local_media_10"), localOnly.map { it.id })
        assertEquals(2000L, localOnly.single().dateAddedMs)

        val onlineOnly = selectRecentlyAddedSourceCandidates(
            sourceType = MediaSourceType.ONLINE,
            localTracks = listOf(localTrack),
            onlineTracks = listOf(onlineTrack),
            likedTracks = listOf(likedLocalDuplicateMissingTimestamp, likedOnlineTrack)
        )
        assertEquals(setOf("ytm_99", "ytm_liked_only"), onlineOnly.map { it.id }.toSet())

        val allSources = selectRecentlyAddedSourceCandidates(
            sourceType = MediaSourceType.ALL,
            localTracks = listOf(localTrack),
            onlineTracks = listOf(onlineTrack),
            likedTracks = listOf(likedLocalDuplicateMissingTimestamp, likedOnlineTrack)
        )
        assertEquals(setOf("local_media_10", "ytm_99", "ytm_liked_only"), allSources.map { it.id }.toSet())
        assertEquals(2000L, allSources.first { it.id == "local_media_10" }.dateAddedMs)
    }

    @Test
    fun selectRecentlyAddedSourceCandidates_prefersPositiveDateAddedWhenFirstSeenWasZero() {
        val zeroStamped = TrackItem(
            id = "ytm_abc",
            title = "Track",
            artist = "Artist",
            album = "Album",
            dateAddedMs = 0L
        )
        val dated = zeroStamped.copy(dateAddedMs = 1731540000000L)

        val resolved = selectRecentlyAddedSourceCandidates(
            sourceType = MediaSourceType.ALL,
            localTracks = emptyList(),
            onlineTracks = listOf(zeroStamped),
            likedTracks = listOf(dated)
        )
        assertEquals(1, resolved.size)
        assertEquals(1731540000000L, resolved.single().dateAddedMs)
    }

    @Test
    fun resolveLibraryBackTransition_handlesSubViewsAndCategories() {
        assertEquals(
            null to SearchCategoryFilter.ALL,
            resolveLibraryBackTransition(LibrarySubView.RECENTLY_ADDED, SearchCategoryFilter.ALL)
        )
        assertEquals(
            null to SearchCategoryFilter.ALL,
            resolveLibraryBackTransition(LibrarySubView.ARTISTS, SearchCategoryFilter.ARTISTS)
        )
        assertEquals(
            null to SearchCategoryFilter.ALL,
            resolveLibraryBackTransition(LibrarySubView.ALBUMS, SearchCategoryFilter.ALBUMS)
        )
        assertEquals(
            null to SearchCategoryFilter.ALL,
            resolveLibraryBackTransition(LibrarySubView.SONGS, SearchCategoryFilter.SONGS)
        )
        assertEquals(
            null to SearchCategoryFilter.ALL,
            resolveLibraryBackTransition(null, SearchCategoryFilter.SONGS)
        )
        assertNull(resolveLibraryBackTransition(null, SearchCategoryFilter.ALL))
    }

    @Test
    fun shouldHideSettingsButton_onlyHidesInRecentlyAddedLibrarySubView() {
        assertTrue(shouldHideSettingsButton(NavigationTab.Library, LibrarySubView.RECENTLY_ADDED))
        assertFalse(shouldHideSettingsButton(NavigationTab.Library, null))
        assertFalse(shouldHideSettingsButton(NavigationTab.Library, LibrarySubView.ARTISTS))
        assertFalse(shouldHideSettingsButton(NavigationTab.Library, LibrarySubView.ALBUMS))
        assertFalse(shouldHideSettingsButton(NavigationTab.Library, LibrarySubView.SONGS))
        assertFalse(shouldHideSettingsButton(NavigationTab.Home, LibrarySubView.RECENTLY_ADDED))
    }

    @Test
    fun isAppBackdropOverlayFreezeActive_tracksOpenStateAndAnimationProgress() {
        assertFalse(
            isAppBackdropOverlayFreezeActive(
                isAlbumDetailOpen = false,
                isArtistDetailOpen = false,
                isPlaylistDetailOpen = false,
                isHomeGridOpen = false,
                effectiveAlbumProgress = 0f,
                effectiveArtistProgress = 0.001f,
                effectivePlaylistProgress = 0f,
                effectiveGridProgress = 0f
            )
        )
        assertTrue(
            isAppBackdropOverlayFreezeActive(
                isAlbumDetailOpen = true,
                isArtistDetailOpen = false,
                isPlaylistDetailOpen = false,
                isHomeGridOpen = false,
                effectiveAlbumProgress = 0f,
                effectiveArtistProgress = 0f,
                effectivePlaylistProgress = 0f,
                effectiveGridProgress = 0f
            )
        )
        assertTrue(
            isAppBackdropOverlayFreezeActive(
                isAlbumDetailOpen = false,
                isArtistDetailOpen = false,
                isPlaylistDetailOpen = false,
                isHomeGridOpen = false,
                effectiveAlbumProgress = 0f,
                effectiveArtistProgress = 0.002f,
                effectivePlaylistProgress = 0f,
                effectiveGridProgress = 0f
            )
        )
        assertTrue(
            isAppBackdropOverlayFreezeActive(
                isAlbumDetailOpen = false,
                isArtistDetailOpen = false,
                isPlaylistDetailOpen = false,
                isHomeGridOpen = false,
                effectiveAlbumProgress = 0f,
                effectiveArtistProgress = 0f,
                effectivePlaylistProgress = 0.25f,
                effectiveGridProgress = 0f
            )
        )
        assertTrue(
            isAppBackdropOverlayFreezeActive(
                isAlbumDetailOpen = false,
                isArtistDetailOpen = false,
                isPlaylistDetailOpen = false,
                isHomeGridOpen = true,
                effectiveAlbumProgress = 0f,
                effectiveArtistProgress = 0f,
                effectivePlaylistProgress = 0f,
                effectiveGridProgress = 0.5f
            )
        )
    }

    @Test
    fun sanitizeAlbumOpenRequest_rejectsEmptyAndNormalizesValidAlbums() {
        assertNull(sanitizeAlbumOpenRequest(AlbumItem(id = "   ", title = "   ", artist = "Artist")))

        val synthesized = sanitizeAlbumOpenRequest(
            AlbumItem(id = " ", title = "  Midnight  ", artist = "<unknown>")
        )
        assertNotNull(synthesized)
        assertEquals("Midnight", synthesized!!.title)
        assertEquals("Unknown Artist", synthesized.artist)
        assertTrue(synthesized.id.startsWith("alb_"))

        val withIdOnly = sanitizeAlbumOpenRequest(
            AlbumItem(id = "MPREb_xyz", title = " ", artist = "The Weeknd")
        )
        assertNotNull(withIdOnly)
        assertEquals("MPREb_xyz", withIdOnly!!.id)
        assertEquals("Album", withIdOnly.title)

        assertNotEquals(
            buildAlbumRequestKey(synthesized),
            buildAlbumRequestKey(withIdOnly)
        )
    }

    @Test
    fun normalizeArtistLookupName_rejectsPlaceholdersAndExtractsPrimaryArtist() {
        assertNull(normalizeArtistLookupName(null))
        assertNull(normalizeArtistLookupName("   "))
        assertNull(normalizeArtistLookupName("Unknown Artist"))
        assertNull(normalizeArtistLookupName("<unknown>"))
        assertEquals("Daft Punk", normalizeArtistLookupName("  Daft Punk, Pharrell Williams  "))
        assertEquals("The Weeknd", normalizeArtistLookupName("The Weeknd & Ariana Grande"))
        assertEquals("Rosalia", normalizeArtistLookupName("Rosalia / Travis Scott"))
    }
}
