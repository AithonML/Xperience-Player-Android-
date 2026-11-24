package com.xperience.player.data

import com.xperience.player.domain.model.TrackItem
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesRepositoryTest {

    @Test
    fun testTrackItemLocalDetection() {
        val onlineTrack = TrackItem(
            id = "ytm_abc123",
            title = "Bubblegum Bitch",
            artist = "MARINA",
            album = "Electra Heart",
            audioUrl = "https://example.com/stream.m4a"
        )
        val isOnlineLocal = onlineTrack.audioUrl?.startsWith("content://") == true ||
            onlineTrack.audioUrl?.startsWith("file://") == true ||
            onlineTrack.id.startsWith("local_") ||
            onlineTrack.id.startsWith("saf_")
        assertFalse(isOnlineLocal)

        val localTrack = TrackItem(
            id = "local_456",
            title = "Soda Pop",
            artist = "Saja Boys",
            album = "K-Pop Demon",
            audioUrl = "content://media/external/audio/media/456"
        )
        val isDeviceLocal = localTrack.audioUrl?.startsWith("content://") == true ||
            localTrack.audioUrl?.startsWith("file://") == true ||
            localTrack.id.startsWith("local_") ||
            localTrack.id.startsWith("saf_")
        assertTrue(isDeviceLocal)
    }

    @Test
    fun testFavoritesJsonSerialization() {
        val track = TrackItem(
            id = "test_track_1",
            title = "Starboy",
            artist = "The Weeknd",
            album = "Starboy",
            durationMs = 230000L,
            artworkUri = "https://example.com/art.jpg",
            audioUrl = "https://example.com/audio.m4a",
            isLiked = true
        )

        val obj = JSONObject()
        obj.put("id", track.id)
        obj.put("title", track.title)
        obj.put("artist", track.artist)
        obj.put("album", track.album)
        obj.put("durationMs", track.durationMs)
        obj.put("artworkUri", track.artworkUri ?: "")
        obj.put("audioUrl", track.audioUrl ?: "")

        val jsonArray = JSONArray()
        jsonArray.put(obj)
        val jsonString = jsonArray.toString()

        val parsedArray = JSONArray(jsonString)
        assertEquals(1, parsedArray.length())
        val parsedObj = parsedArray.getJSONObject(0)
        assertEquals("test_track_1", parsedObj.getString("id"))
        assertEquals("Starboy", parsedObj.getString("title"))
        assertEquals("The Weeknd", parsedObj.getString("artist"))
        assertEquals(230000L, parsedObj.getLong("durationMs"))
    }

    @Test
    fun testArtistAndAlbumGroupingFromFavorites() {
        val tracks = listOf(
            TrackItem(id = "1", title = "Track A", artist = "MARINA", album = "Album 1"),
            TrackItem(id = "2", title = "Track B", artist = "MARINA", album = "Album 1"),
            TrackItem(id = "3", title = "Track C", artist = "MARINA", album = "Album 2"),
            TrackItem(id = "4", title = "Track D", artist = "The Weeknd", album = "After Hours")
        )

        val artistGroups = tracks.groupBy { it.artist }
        assertEquals(2, artistGroups.size)
        assertEquals(3, artistGroups["MARINA"]?.size)
        assertEquals(1, artistGroups["The Weeknd"]?.size)

        val albumGroups = tracks.groupBy { "${it.album}__${it.artist}" }
        assertEquals(3, albumGroups.size)
    }
}
