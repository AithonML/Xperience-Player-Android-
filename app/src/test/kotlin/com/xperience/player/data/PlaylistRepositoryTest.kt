package com.xperience.player.data

import com.xperience.player.domain.model.TrackItem
import com.xperience.player.domain.model.UserPlaylist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistRepositoryTest {

    @Test
    fun testCollageCoversDistinctAlbums() {
        // Tracks with distinct album covers
        val track1 = TrackItem(id = "1", title = "T1", artist = "Artist A", album = "Album 1", artworkUri = "https://cover1.jpg")
        val track2 = TrackItem(id = "2", title = "T2", artist = "Artist A", album = "Album 1", artworkUri = "https://cover1.jpg") // Duplicate album
        val track3 = TrackItem(id = "3", title = "T3", artist = "Artist B", album = "Album 2", artworkUri = "https://cover2.jpg")
        val track4 = TrackItem(id = "4", title = "T4", artist = "Artist C", album = "Album 3", artworkUri = "https://cover3.jpg")
        val track5 = TrackItem(id = "5", title = "T5", artist = "Artist D", album = "Album 4", artworkUri = "https://cover4.jpg")

        val playlist = UserPlaylist(
            title = "My Party Mix",
            tracks = listOf(track1, track2, track3, track4, track5)
        )

        val distinct = playlist.getDistinctCollageCovers()
        assertEquals(4, distinct.size)
        assertEquals("https://cover1.jpg", distinct[0])
        assertEquals("https://cover2.jpg", distinct[1])
        assertEquals("https://cover3.jpg", distinct[2])
        assertEquals("https://cover4.jpg", distinct[3])

        assertEquals("https://cover1.jpg", playlist.collageCover1)
        assertEquals("https://cover2.jpg", playlist.collageCover2)
        assertEquals("https://cover3.jpg", playlist.collageCover3)
        assertEquals("https://cover4.jpg", playlist.collageCover4)
    }

    @Test
    fun testCollageSlotCycleWhenFewTracks() {
        val track1 = TrackItem(id = "1", title = "T1", artist = "Artist A", album = "Album 1", artworkUri = "https://cover1.jpg")
        val track2 = TrackItem(id = "2", title = "T2", artist = "Artist B", album = "Album 2", artworkUri = "https://cover2.jpg")

        val playlist = UserPlaylist(
            title = "Duo Mix",
            tracks = listOf(track1, track2)
        )

        val distinct = playlist.getDistinctCollageCovers()
        assertEquals(2, distinct.size)
        assertEquals("https://cover1.jpg", playlist.collageCover1)
        assertEquals("https://cover2.jpg", playlist.collageCover2)
        assertEquals("https://cover1.jpg", playlist.collageCover3) // cycles slot 0
        assertEquals("https://cover2.jpg", playlist.collageCover4) // cycles slot 1
    }

    @Test
    fun testEmptyPlaylistCovers() {
        val emptyPlaylist = UserPlaylist(title = "Empty")
        assertTrue(emptyPlaylist.getDistinctCollageCovers().isEmpty())
        assertNull(emptyPlaylist.collageCover1)
        assertFalse(emptyPlaylist.hasCustomCover)
    }

    @Test
    fun testCustomCoverPriority() {
        val playlist = UserPlaylist(
            title = "Custom Artwork Playlist",
            customCoverImagePath = "content://media/external/images/media/999",
            tracks = listOf(
                TrackItem(id = "1", title = "Song", artist = "Artist", album = "Album", artworkUri = "https://cover.jpg")
            )
        )
        assertTrue(playlist.hasCustomCover)
        assertEquals("content://media/external/images/media/999", playlist.customCoverImagePath)
    }

    @Test
    fun testSerializeAndParseYouTubeMusicMetadataRoundTrip() {
        val track = TrackItem(
            id = "ytm_dQw4w9WgXcQ",
            title = "Never Gonna Give You Up",
            artist = "Rick Astley",
            album = "Whenever You Need Somebody",
            durationMs = 213_000L,
            artworkUri = "https://lh3.googleusercontent.com/cover=w544-h544",
            audioUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        )
        val imported = UserPlaylist(
            id = "pl_ytm_1",
            title = "Road Trip",
            description = "My YTM Mix",
            customCoverImagePath = "https://lh3.googleusercontent.com/pl=w544-h544",
            tracks = listOf(track),
            source = UserPlaylist.SOURCE_YOUTUBE_MUSIC,
            externalPlaylistId = "PL1234567890",
            externalBrowseId = "VLPL1234567890",
            sourceAccountLabel = "@testuser",
            importedAt = "2026-11-24T08:00:00.000Z"
        )

        val json = PlaylistRepository.serializePlaylistsToJson(listOf(imported))
        val parsed = PlaylistRepository.parsePlaylistsJson(json)

        assertEquals(1, parsed.size)
        val roundTripped = parsed[0]
        assertTrue(roundTripped.isFromYouTubeMusic)
        assertEquals(UserPlaylist.SOURCE_YOUTUBE_MUSIC, roundTripped.source)
        assertEquals("PL1234567890", roundTripped.externalPlaylistId)
        assertEquals("VLPL1234567890", roundTripped.externalBrowseId)
        assertEquals("@testuser", roundTripped.sourceAccountLabel)
        assertEquals("2026-11-24T08:00:00.000Z", roundTripped.importedAt)
        assertEquals(1, roundTripped.tracks.size)
        assertEquals("ytm_dQw4w9WgXcQ", roundTripped.tracks[0].id)
    }

    @Test
    fun testLegacyPlaylistJsonWithoutSourceFieldsRemainsLocal() {
        val legacyJson = """
            [
              {
                "id": "pl_legacy_1",
                "title": "Local Favorites",
                "description": "Created locally",
                "customCoverImagePath": "",
                "createdAt": "2026-01-01T00:00:00.000Z",
                "tracks": []
              }
            ]
        """.trimIndent()

        val parsed = PlaylistRepository.parsePlaylistsJson(legacyJson)
        assertEquals(1, parsed.size)
        assertFalse(parsed[0].isFromYouTubeMusic)
        assertEquals("", parsed[0].source)
        assertEquals("", parsed[0].externalPlaylistId)
    }

    @Test
    fun testUpsertImportedYouTubePlaylistCreatesUpdatesAndPreservesLocalCover() {
        val localPlaylist = UserPlaylist(id = "pl_local", title = "Local Mix")
        val track1 = TrackItem(id = "ytm_aaa11111111", title = "Track 1", artist = "A1", album = "Al1")
        val track2 = TrackItem(id = "ytm_bbb22222222", title = "Track 2", artist = "A2", album = "Al2")

        // First import prepends new YTM playlist
        val (afterInsert, created) = PlaylistRepository.upsertImportedYouTubePlaylist(
            existing = listOf(localPlaylist),
            externalPlaylistId = "VLPLabc123",
            externalBrowseId = "VLPLabc123",
            title = "YTM Chill",
            description = "First import",
            coverUrl = "https://lh3.googleusercontent.com/cover1",
            tracks = listOf(track1),
            accountLabel = "@alice",
            importedAt = "2026-11-24T08:00:00.000Z"
        )

        assertEquals(2, afterInsert.size)
        assertEquals(created.id, afterInsert[0].id)
        assertTrue(created.isFromYouTubeMusic)
        assertEquals("PLabc123", created.externalPlaylistId)
        assertEquals("VLPLabc123", created.externalBrowseId)

        // Simulate user setting a local custom gallery cover on the imported playlist
        val withLocalCover = afterInsert.map {
            if (it.id == created.id) it.copy(customCoverImagePath = "content://media/external/images/42") else it
        }

        // Re-import with same ID (case-insensitive) updates in-place without duplicating and keeps local cover
        val (afterUpdate, updated) = PlaylistRepository.upsertImportedYouTubePlaylist(
            existing = withLocalCover,
            externalPlaylistId = "plabc123",
            externalBrowseId = "VLplabc123",
            title = "YTM Chill Updated",
            description = "Synced",
            coverUrl = "https://lh3.googleusercontent.com/cover2",
            tracks = listOf(track1, track2),
            accountLabel = "@alice",
            importedAt = "2026-11-24T09:00:00.000Z"
        )

        assertEquals(2, afterUpdate.size)
        assertEquals(created.id, updated.id)
        assertEquals("YTM Chill Updated", updated.title)
        assertEquals(2, updated.tracks.size)
        assertEquals("content://media/external/images/42", updated.customCoverImagePath)
    }
}
