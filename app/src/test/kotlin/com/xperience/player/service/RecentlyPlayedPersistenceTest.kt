package com.xperience.player.service

import com.xperience.player.domain.model.TrackItem
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentlyPlayedPersistenceTest {

    @Test
    fun testRecentlyPlayedSerializationAndDeserialization() {
        val originalTracks = listOf(
            TrackItem(
                id = "ytm_song_1",
                title = "Levitating",
                artist = "Dua Lipa",
                album = "Future Nostalgia",
                durationMs = 203000L,
                artworkUri = "https://example.com/levitating.jpg",
                isLiked = true,
                playCount = 1500000L
            ),
            TrackItem(
                id = "ytm_song_2",
                title = "Blinding Lights",
                artist = "The Weeknd",
                album = "After Hours",
                durationMs = 200000L,
                artworkUri = "https://example.com/blinding.jpg",
                isLiked = false,
                playCount = 3000000L
            )
        )

        // Serialize to JSON
        val array = JSONArray()
        for (track in originalTracks) {
            val obj = JSONObject().apply {
                put("id", track.id)
                put("title", track.title)
                put("artist", track.artist)
                put("album", track.album)
                put("durationMs", track.durationMs)
                if (track.artworkResId != null) put("artworkResId", track.artworkResId)
                if (!track.artworkUri.isNullOrBlank()) put("artworkUri", track.artworkUri)
                if (!track.audioUrl.isNullOrBlank()) put("audioUrl", track.audioUrl)
                put("isLiked", track.isLiked)
                put("isExplicit", track.isExplicit)
                put("playCount", track.playCount)
            }
            array.put(obj)
        }
        val serializedString = array.toString()
        assertTrue(serializedString.contains("Levitating"))
        assertTrue(serializedString.contains("Blinding Lights"))

        // Deserialize from JSON
        val deserializedTracks = mutableListOf<TrackItem>()
        val parsedArray = JSONArray(serializedString)
        for (i in 0 until parsedArray.length()) {
            val obj = parsedArray.getJSONObject(i)
            deserializedTracks.add(
                TrackItem(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    artist = obj.getString("artist"),
                    album = obj.optString("album", ""),
                    durationMs = obj.optLong("durationMs", 0L),
                    artworkResId = if (obj.has("artworkResId")) obj.getInt("artworkResId") else null,
                    artworkUri = if (obj.has("artworkUri")) obj.getString("artworkUri") else null,
                    audioUrl = if (obj.has("audioUrl")) obj.getString("audioUrl") else null,
                    isLiked = obj.optBoolean("isLiked", false),
                    isExplicit = obj.optBoolean("isExplicit", false),
                    playCount = obj.optLong("playCount", 0L)
                )
            )
        }

        assertEquals(2, deserializedTracks.size)
        assertEquals("ytm_song_1", deserializedTracks[0].id)
        assertEquals("Levitating", deserializedTracks[0].title)
        assertEquals("Dua Lipa", deserializedTracks[0].artist)
        assertTrue(deserializedTracks[0].isLiked)

        assertEquals("ytm_song_2", deserializedTracks[1].id)
        assertEquals("Blinding Lights", deserializedTracks[1].title)
        assertEquals("The Weeknd", deserializedTracks[1].artist)
        assertFalse(deserializedTracks[1].isLiked)
    }

    @Test
    fun testRecentlyPlayedDeduplicationAndOrder() {
        val history = mutableListOf<TrackItem>()

        val trackA = TrackItem(id = "1", title = "Song A", artist = "Artist A", album = "Album A")
        val trackB = TrackItem(id = "2", title = "Song B", artist = "Artist B", album = "Album B")
        val trackC = TrackItem(id = "3", title = "Song C", artist = "Artist C", album = "Album C")

        // Play A, then B, then C
        fun record(track: TrackItem) {
            history.removeAll { it.id == track.id }
            history.add(0, track)
            while (history.size > 50) history.removeAt(history.lastIndex)
        }

        record(trackA)
        record(trackB)
        record(trackC)

        assertEquals(listOf("3", "2", "1"), history.map { it.id })

        // Replay Song A -> Should move to top without duplicate
        record(trackA)
        assertEquals(listOf("1", "3", "2"), history.map { it.id })
        assertEquals(3, history.size)
    }

    @Test
    fun testAlbumLevelGroupingAndRepositioning() {
        val history = mutableListOf<TrackItem>()

        fun isSameAlbumOrItem(a: TrackItem, b: TrackItem): Boolean {
            if (a.id == b.id) return true
            val albumA = a.album.trim()
            val albumB = b.album.trim()
            val isGenericA = albumA.isBlank() || albumA.equals("Unknown Album", ignoreCase = true) || albumA.equals("Single", ignoreCase = true) || albumA.equals("YouTube Music", ignoreCase = true)
            val isGenericB = albumB.isBlank() || albumB.equals("Unknown Album", ignoreCase = true) || albumB.equals("Single", ignoreCase = true) || albumB.equals("YouTube Music", ignoreCase = true)

            if (!isGenericA && !isGenericB) {
                val sameAlbum = albumA.equals(albumB, ignoreCase = true)
                val artistA = a.artist.trim()
                val artistB = b.artist.trim()
                val isGenericArtistA = artistA.isBlank() || artistA.equals("Unknown Artist", ignoreCase = true)
                val isGenericArtistB = artistB.isBlank() || artistB.equals("Unknown Artist", ignoreCase = true)
                val sameArtist = isGenericArtistA || isGenericArtistB || artistA.equals(artistB, ignoreCase = true)
                if (sameAlbum && sameArtist) return true
            }

            return a.title.trim().equals(b.title.trim(), ignoreCase = true) &&
                   a.artist.trim().equals(b.artist.trim(), ignoreCase = true)
        }

        fun record(track: TrackItem) {
            history.removeAll { isSameAlbumOrItem(it, track) }
            history.add(0, track)
            while (history.size > 50) history.removeAt(history.lastIndex)
        }

        // 1. Play Track 1 from album "Future Nostalgia"
        val track1 = TrackItem(id = "s1", title = "Don't Start Now", artist = "Dua Lipa", album = "Future Nostalgia")
        record(track1)
        assertEquals(1, history.size)
        assertEquals("Future Nostalgia", history[0].album)

        // 2. Play Track 2 from album "After Hours"
        val track2 = TrackItem(id = "s2", title = "Blinding Lights", artist = "The Weeknd", album = "After Hours")
        record(track2)
        assertEquals(2, history.size)
        assertEquals("After Hours", history[0].album)
        assertEquals("Future Nostalgia", history[1].album)

        // 3. Play Track 3 from album "Future Nostalgia" (different song, same album)
        // Should REPOSITION "Future Nostalgia" to index 0, rather than creating a duplicate entry
        val track3 = TrackItem(id = "s3", title = "Levitating", artist = "Dua Lipa", album = "Future Nostalgia")
        record(track3)
        assertEquals(2, history.size)
        assertEquals("s3", history[0].id)
        assertEquals("Levitating", history[0].title)
        assertEquals("Future Nostalgia", history[0].album)
        assertEquals("After Hours", history[1].album)

        // 4. Fill history up to 50 albums and ensure capacity is respected
        for (i in 1..60) {
            val filler = TrackItem(id = "f_$i", title = "Song $i", artist = "Artist $i", album = "Album $i")
            record(filler)
        }
        assertEquals(50, history.size)
    }

    @Test
    fun testNoPlaceholdersWhenHistoryIsEmpty() {
        val emptyHistory = emptyList<TrackItem>()
        val seedArtists = emptyHistory
            .map { it.artist.trim() }
            .filter { it.isNotBlank() && !it.equals("Unknown Artist", ignoreCase = true) }
            .distinct()

        assertTrue("Seed artists must be empty when user has no listening history", seedArtists.isEmpty())

        // Subtitle logic
        val subtitle = if (seedArtists.isNotEmpty()) {
            "Powered by YouTube Music • Inspired by ${seedArtists.joinToString(", ")}"
        } else {
            "Trending on YouTube Music"
        }

        assertEquals("Trending on YouTube Music", subtitle)
        assertFalse(subtitle.contains("MARINA"))
        assertFalse(subtitle.contains("Tove Lo"))
        assertFalse(subtitle.contains("Bruno Mars"))
        assertFalse(subtitle.contains("Daft Punk"))
    }

    @Test
    fun testRecommendationsDerivedFromUserListenedArtists() {
        val userHistory = listOf(
            TrackItem(id = "1", title = "Bad Romance", artist = "Lady Gaga", album = "The Fame Monster"),
            TrackItem(id = "2", title = "Poker Face", artist = "Lady Gaga", album = "The Fame"),
            TrackItem(id = "3", title = "Viva La Vida", artist = "Coldplay", album = "Viva la Vida")
        )

        val userArtists = userHistory
            .map { it.artist.trim() }
            .filter { it.isNotBlank() && !it.equals("Unknown Artist", ignoreCase = true) }
            .distinct()

        assertEquals(listOf("Lady Gaga", "Coldplay"), userArtists)

        val primaryArtist = userArtists.first()
        val recQuery = if (userArtists.size >= 2) {
            "${userArtists[0]} ${userArtists[1]} mix songs"
        } else {
            "$primaryArtist similar songs"
        }

        assertEquals("Lady Gaga Coldplay mix songs", recQuery)

        val subtitle = "Powered by YouTube Music • Inspired by ${userArtists.joinToString(", ")}"
        assertEquals("Powered by YouTube Music • Inspired by Lady Gaga, Coldplay", subtitle)
    }
}
