package com.xperience.player.service

import com.xperience.player.domain.model.PlaybackSourceType
import com.xperience.player.domain.model.StreamingAudioQuality
import com.xperience.player.domain.model.TrackPlaybackInfo
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingQualityAndFlacTest {

    private fun buildSampleStreamingData(): JSONObject {
        val formats = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("url", "https://example.com/audio_48k")
                    put("mimeType", "audio/mp4; codecs=\"mp4a.40.5\"")
                    put("averageBitrate", 48_000)
                    put("bitrate", 52_000)
                    put("audioSampleRate", "44100")
                    put("audioChannels", 2)
                }
            )
            put(
                JSONObject().apply {
                    put("url", "https://example.com/audio_64k")
                    put("mimeType", "audio/webm; codecs=\"opus\"")
                    put("averageBitrate", 64_000)
                    put("bitrate", 66_000)
                    put("audioSampleRate", "48000")
                    put("audioChannels", 2)
                }
            )
            put(
                JSONObject().apply {
                    put("url", "https://example.com/audio_128k")
                    put("mimeType", "audio/mp4; codecs=\"mp4a.40.2\"")
                    put("averageBitrate", 128_000)
                    put("bitrate", 130_000)
                    put("audioSampleRate", "44100")
                    put("audioChannels", 2)
                }
            )
            put(
                JSONObject().apply {
                    put("url", "https://example.com/audio_160k_opus")
                    put("mimeType", "audio/webm; codecs=\"opus\"")
                    put("averageBitrate", 160_000)
                    put("bitrate", 165_000)
                    put("audioSampleRate", "48000")
                    put("audioChannels", 2)
                }
            )
        }
        return JSONObject().put("adaptiveFormats", formats)
    }

    @Test
    fun pickBestAudioCandidate_selectsMatchingQualityTier() {
        val service = YouTubeMusicService.getInstance()
        val data = buildSampleStreamingData()

        val low = service.pickBestAudioCandidate(data, StreamingAudioQuality.LOW_64)
        assertNotNull(low)
        assertEquals("https://example.com/audio_64k", low!!.url)
        assertEquals(64_000, low.bitrateBps)
        assertEquals("Opus", low.codec)

        val regular = service.pickBestAudioCandidate(data, StreamingAudioQuality.REGULAR_128)
        assertNotNull(regular)
        assertEquals("https://example.com/audio_128k", regular!!.url)
        assertEquals(128_000, regular.bitrateBps)
        assertEquals("AAC", regular.codec)

        val high = service.pickBestAudioCandidate(data, StreamingAudioQuality.HIGH_160)
        assertNotNull(high)
        assertEquals("https://example.com/audio_160k_opus", high!!.url)
        assertEquals(160_000, high.bitrateBps)
        assertEquals("Opus", high.codec)
    }

    @Test
    fun flacMagicHeaderAndStreamInfoParser_extractsExactSpecs() {
        // Construct a valid 42-byte FLAC header + STREAMINFO block:
        // sampleRate = 96000 Hz, channels = 2 (encoded as 1), bitDepth = 24 (encoded as 23), totalSamples = 9_600_000 (100 sec)
        val header = ByteArray(42)
        header[0] = 'f'.code.toByte()
        header[1] = 'L'.code.toByte()
        header[2] = 'a'.code.toByte()
        header[3] = 'C'.code.toByte()
        header[4] = 0x80.toByte() // last metadata block flag + type 0 (STREAMINFO)
        header[5] = 0x00
        header[6] = 0x00
        header[7] = 34 // STREAMINFO length = 34 bytes

        val sampleRate = 96000
        val channelsMinus1 = 1 // stereo
        val bpsMinus1 = 23 // 24-bit
        val totalSamples = 9_600_000L

        header[18] = ((sampleRate ushr 12) and 0xFF).toByte()
        header[19] = ((sampleRate ushr 4) and 0xFF).toByte()
        header[20] = (((sampleRate and 0x0F) shl 4) or ((channelsMinus1 and 0x07) shl 1) or ((bpsMinus1 ushr 4) and 0x01)).toByte()
        header[21] = (((bpsMinus1 and 0x0F) shl 4) or ((totalSamples ushr 32).toInt() and 0x0F)).toByte()
        header[22] = ((totalSamples ushr 24) and 0xFF).toByte()
        header[23] = ((totalSamples ushr 16) and 0xFF).toByte()
        header[24] = ((totalSamples ushr 8) and 0xFF).toByte()
        header[25] = (totalSamples and 0xFF).toByte()

        assertTrue(FlacStreamResolver.hasFlacMagicHeader(header))
        assertFalse(FlacStreamResolver.hasFlacMagicHeader(byteArrayOf(0x00, 0x00, 0x00, 0x20)))

        val info = FlacStreamResolver.parseFlacStreamInfo(header)
        assertNotNull(info)
        assertEquals(96000, info!!.sampleRateHz)
        assertEquals(2, info.channelCount)
        assertEquals(24, info.bitDepth)
        assertEquals(9_600_000L, info.totalSamples)
        assertEquals(100_000L, info.durationMs)

        // 30 MB over 100 seconds = 2400 kbps
        val kbps = FlacStreamResolver.computeBitrateKbps(30_000_000L, 100_000L)
        assertEquals(2400, kbps)
    }

    @Test
    fun truthfulProviderAndQualityLabeling_neverClaimsPremiumOrHiResForRegularStreams() {
        val unauthRegular = TrackPlaybackInfo.buildAccurateYouTubeProviderLabel(
            isVideoFallback = false,
            isAuthenticated = false,
            bitrateKbps = 128
        )
        assertEquals("YouTube Music", unauthRegular)

        val auth128 = TrackPlaybackInfo.buildAccurateYouTubeProviderLabel(
            isVideoFallback = false,
            isAuthenticated = true,
            bitrateKbps = 128
        )
        assertEquals("YouTube Music (Signed-In)", auth128)

        val genuinePremium = TrackPlaybackInfo.buildAccurateYouTubeProviderLabel(
            isVideoFallback = false,
            isAuthenticated = true,
            bitrateKbps = 256
        )
        assertEquals("YouTube Music Premium", genuinePremium)

        val regularQualityLabel = TrackPlaybackInfo.buildAccurateQualityLabel(
            codec = "AAC",
            bitrateKbps = 128,
            sampleRateHz = 44100,
            bitDepth = 0,
            isLossless = false
        )
        assertEquals("AAC • 44.1 kHz • 128 kbps", regularQualityLabel)
        assertFalse(regularQualityLabel.contains("Hi-Res", ignoreCase = true))
        assertFalse(regularQualityLabel.contains("24-bit", ignoreCase = true))

        val info = TrackPlaybackInfo(
            trackId = "ytm_test",
            title = "Test",
            artist = "Artist",
            album = "Album",
            sourceType = PlaybackSourceType.YOUTUBE_MUSIC,
            codec = "AAC",
            bitrateKbps = 128,
            sampleRateHz = 44100,
            bitDepth = 0
        )
        assertFalse(info.isLossless)
        assertFalse(info.formattedTechnicalSummary.contains("24-bit"))
    }
}
