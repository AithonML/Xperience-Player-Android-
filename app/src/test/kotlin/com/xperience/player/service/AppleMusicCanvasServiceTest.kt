package com.xperience.player.service

import com.xperience.player.domain.model.canvas.CanvasArtworkResult
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppleMusicCanvasServiceTest {

    @Test
    fun testCanvasArtworkResultModel() {
        val hlsResult = CanvasArtworkResult(
            videoUri = "https://example.com/stream/master.m3u8",
            providerName = "Apple Music Canvas",
            previewFrameUri = "https://example.com/preview.jpg"
        )
        assertTrue(hlsResult.isHls)
        assertEquals("Apple Music Canvas", hlsResult.providerName)

        val mp4Result = CanvasArtworkResult(
            videoUri = "/data/user/0/com.xperience.player/cache/canvas_cache/canvas_123.mp4",
            providerName = "Apple Music Canvas"
        )
        assertFalse(mp4Result.isHls)
    }

    @Test
    fun testEditorialVideoJsonStructureParsing() {
        val jsonString = """
        {
            "editorialVideo": {
                "motionDetailSquare": {
                    "video": "https://cvs.apple.com/us/video1_square.m3u8",
                    "previewFrame": {
                        "url": "https://cvs.apple.com/us/preview_{w}x{h}.jpg"
                    }
                }
            }
        }
        """.trimIndent()

        val root = JSONObject(jsonString)
        val editorialVideo = root.optJSONObject("editorialVideo")
        assertNotNull(editorialVideo)

        val square = editorialVideo!!.optJSONObject("motionDetailSquare")
        assertNotNull(square)
        assertEquals("https://cvs.apple.com/us/video1_square.m3u8", square!!.optString("video"))

        val preview = square.optJSONObject("previewFrame")
        assertNotNull(preview)
        val resolvedPreview = preview!!.optString("url").replace("{w}", "600").replace("{h}", "600")
        assertEquals("https://cvs.apple.com/us/preview_600x600.jpg", resolvedPreview)
    }

    @Test
    fun testFallbackFeedJsonParsing() {
        val feedJson = """
        [
            {
                "song": "Blinding Lights",
                "artist": "The Weeknd",
                "videoUrl": "https://canvas.echomusic.fun/blinding_lights.mp4"
            },
            {
                "song": "Levitating",
                "artist": "Dua Lipa",
                "videoUrl": "https://canvas.echomusic.fun/levitating.mp4"
            }
        ]
        """.trimIndent()

        val array = JSONArray(feedJson)
        assertEquals(2, array.length())

        val first = array.getJSONObject(0)
        assertEquals("Blinding Lights", first.getString("song"))
        assertEquals("The Weeknd", first.getString("artist"))
        assertEquals("https://canvas.echomusic.fun/blinding_lights.mp4", first.getString("videoUrl"))
    }
}
