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
}
