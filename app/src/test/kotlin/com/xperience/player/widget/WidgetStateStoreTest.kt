package com.xperience.player.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetStateStoreTest {

    private class FakeKeyValueBackend : WidgetKeyValueBackend {
        private val strings = mutableMapOf<String, String>()
        private val ints = mutableMapOf<String, Int>()

        override fun getString(key: String): String? = strings[key]
        override fun putString(key: String, value: String) { strings[key] = value }
        override fun getInt(key: String, defaultValue: Int): Int = ints[key] ?: defaultValue
        override fun putInt(key: String, value: Int) { ints[key] = value }
        override fun clear() {
            strings.clear()
            ints.clear()
        }
    }

    @Test
    fun playbackSnapshotRoundTripPreservesAllFields() {
        val original = WidgetPlaybackSnapshot(
            trackId = "ytm_abc123",
            title = "Midnight City",
            artist = "M83",
            album = "Hurry Up, We're Dreaming",
            artworkUri = "https://lh3.googleusercontent.com/cover=w544-h544",
            artworkResId = 42,
            audioUrl = "content://media/external/audio/media/99",
            isPlaying = true,
            isLoading = false,
            isLiked = true,
            positionMs = 95_000L,
            durationMs = 243_000L,
            queueSize = 18,
            accentColorArgb = 0xFF00E5FF.toInt(),
            updatedAtMs = 1_700_000_000_000L
        )

        val encoded = WidgetStateSerializer.encodePlayback(original)
        val decoded = WidgetStateSerializer.decodePlayback(encoded)

        assertEquals(original, decoded)
        assertTrue(decoded.hasTrack)
        assertEquals(390, decoded.progressPermille)
        assertEquals("1:35", decoded.formattedPosition)
        assertEquals("4:03", decoded.formattedDuration)
    }

    @Test
    fun playbackDecodeHandlesNullCorruptAndNegativeValuesSafely() {
        assertFalse(WidgetStateSerializer.decodePlayback(null).hasTrack)
        assertFalse(WidgetStateSerializer.decodePlayback("").hasTrack)
        assertFalse(WidgetStateSerializer.decodePlayback("{not valid json").hasTrack)

        val malformedFieldsJson = """
            {
              "trackId": null,
              "title": null,
              "artist": "   ",
              "positionMs": -500,
              "durationMs": -100,
              "queueSize": -3
            }
        """.trimIndent()
        val decoded = WidgetStateSerializer.decodePlayback(malformedFieldsJson)
        assertNull(decoded.trackId)
        assertEquals("", decoded.title)
        assertEquals("", decoded.artist)
        assertEquals(0L, decoded.positionMs)
        assertEquals(0L, decoded.durationMs)
        assertEquals(0, decoded.queueSize)
        assertEquals(0, decoded.progressPermille)
        assertFalse(decoded.hasTrack)
    }

    @Test
    fun formatWidgetTimeHandlesHoursAndSubMinuteDurations() {
        assertEquals("0:00", formatWidgetTimeMs(-120L))
        assertEquals("0:07", formatWidgetTimeMs(7_000L))
        assertEquals("3:45", formatWidgetTimeMs(225_000L))
        assertEquals("1:05:09", formatWidgetTimeMs(3_909_000L))
    }

    @Test
    fun recommendationsRoundTripPreservesMetadataAndHandlesUnknownSourceType() {
        val items = listOf(
            WidgetPlaylistItem(
                id = "pl_1",
                title = "Night Drive",
                subtitle = "14 songs",
                trackCount = 14,
                artworkUri = "https://example.com/1.jpg",
                sourceType = WidgetPlaylistSourceType.USER_PLAYLIST
            ),
            WidgetPlaylistItem(
                id = "RDAMVMxyz",
                title = "Synthwave Radio",
                subtitle = "Quick Mix",
                trackCount = 0,
                artworkUri = "https://example.com/radio.jpg",
                sourceType = WidgetPlaylistSourceType.RADIO_STATION,
                playlistId = "RDAMVMxyz",
                seedVideoId = "xyz",
                params = "wAEB"
            ),
            WidgetPlaylistItem(
                id = "PLmade4u",
                title = "Discover Mix",
                subtitle = "YouTube Music",
                trackCount = 25,
                artworkUri = "https://example.com/mix.jpg",
                sourceType = WidgetPlaylistSourceType.MADE_FOR_YOU,
                playlistId = "PLmade4u",
                browseId = "VLPLmade4u"
            )
        )

        val encoded = WidgetStateSerializer.encodeRecommendations(items, updatedAtMs = 99_123L)
        val (decoded, updatedAt) = WidgetStateSerializer.decodeRecommendations(encoded)

        assertEquals(99_123L, updatedAt)
        assertEquals(items, decoded)

        val unknownSourceJson = """
            {
              "updatedAtMs": 500,
              "items": [
                {"id": "x1", "title": "Future Mix", "sourceType": "unknown_future_type"},
                {"id": "", "title": "Missing ID"},
                {"id": "x2", "title": ""}
              ]
            }
        """.trimIndent()
        val (safeDecoded, _) = WidgetStateSerializer.decodeRecommendations(unknownSourceJson)
        assertEquals(1, safeDecoded.size)
        assertEquals("x1", safeDecoded[0].id)
        assertEquals(WidgetPlaylistSourceType.USER_PLAYLIST, safeDecoded[0].sourceType)
    }

    @Test
    fun storePersistsStateUpdatesFavoriteOptimisticallyAndRotatesOffset() {
        val store = WidgetStateStore(FakeKeyValueBackend())

        val initialPlayback = WidgetPlaybackSnapshot(
            trackId = "t1",
            title = "Starboy",
            artist = "The Weeknd",
            isPlaying = true,
            isLiked = false,
            durationMs = 230_000L
        )
        store.savePlayback(initialPlayback)
        assertEquals("Starboy", store.readPlayback().title)
        assertFalse(store.readPlayback().isLiked)

        // Mismatched trackId should not mutate persisted track
        store.updateLikedStatus("other_track", true)
        assertFalse(store.readPlayback().isLiked)

        // Matching trackId updates immediately
        val updated = store.updateLikedStatus("t1", true)
        assertTrue(updated.isLiked)
        assertTrue(store.readPlayback().isLiked)

        val recs = List(6) { idx ->
            WidgetPlaylistItem(id = "p$idx", title = "Playlist $idx", subtitle = "$idx songs")
        }
        store.saveRecommendations(recs, updatedAtMs = 1234L)
        assertEquals(0, store.getRecommendationOffset())
        assertEquals(2, store.advanceRecommendationOffset(recs.size, step = 2))
        assertEquals(4, store.advanceRecommendationOffset(recs.size, step = 2))
        assertEquals(0, store.advanceRecommendationOffset(recs.size, step = 2))

        val fullState = store.readState()
        assertEquals("t1", fullState.playback.trackId)
        assertEquals(6, fullState.recommendations.size)
        assertEquals(1234L, fullState.recommendationsUpdatedAtMs)

        store.clear()
        assertFalse(store.readPlayback().hasTrack)
        assertTrue(store.readRecommendations().isEmpty())
    }
}
