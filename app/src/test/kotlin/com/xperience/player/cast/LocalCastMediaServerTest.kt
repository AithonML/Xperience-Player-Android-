package com.xperience.player.cast

import com.xperience.player.service.cast.LocalCastMediaServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class LocalCastMediaServerTest {

    private lateinit var server: LocalCastMediaServer
    private lateinit var tempFile: File
    private lateinit var payloadBytes: ByteArray

    @Before
    fun setUp() {
        payloadBytes = ByteArray(1024) { idx -> ((idx * 31 + 7) and 0xFF).toByte() }
        tempFile = File.createTempFile("xperience_cast_test_", ".m4a").apply {
            writeBytes(payloadBytes)
            deleteOnExit()
        }
        server = LocalCastMediaServer(
            bindHost = "127.0.0.1",
            requestedPort = 0
        )
    }

    @After
    fun tearDown() {
        server.stop()
        if (::tempFile.isInitialized && tempFile.exists()) {
            tempFile.delete()
        }
    }

    @Test
    fun parseByteRange_handlesNullExplicitOpenEndedSuffixAndInvalidRanges() {
        assertEquals(
            LocalCastMediaServer.ByteRangeResult.Full,
            LocalCastMediaServer.parseByteRange(null, 1000L)
        )
        assertEquals(
            LocalCastMediaServer.ByteRangeResult.Full,
            LocalCastMediaServer.parseByteRange("   ", 1000L)
        )
        assertEquals(
            LocalCastMediaServer.ByteRangeResult.Partial(0L, 99L),
            LocalCastMediaServer.parseByteRange("bytes=0-99", 1000L)
        )
        assertEquals(
            LocalCastMediaServer.ByteRangeResult.Partial(500L, 999L),
            LocalCastMediaServer.parseByteRange("bytes=500-", 1000L)
        )
        assertEquals(
            LocalCastMediaServer.ByteRangeResult.Partial(900L, 999L),
            LocalCastMediaServer.parseByteRange("bytes=-100", 1000L)
        )
        // End beyond EOF is clamped per RFC 7233
        assertEquals(
            LocalCastMediaServer.ByteRangeResult.Partial(950L, 999L),
            LocalCastMediaServer.parseByteRange("bytes=950-5000", 1000L)
        )
        // Out-of-bounds start or inverted range -> 416 Unsatisfiable
        assertEquals(
            LocalCastMediaServer.ByteRangeResult.Unsatisfiable,
            LocalCastMediaServer.parseByteRange("bytes=1000-1010", 1000L)
        )
        assertEquals(
            LocalCastMediaServer.ByteRangeResult.Unsatisfiable,
            LocalCastMediaServer.parseByteRange("bytes=200-100", 1000L)
        )
        assertEquals(
            LocalCastMediaServer.ByteRangeResult.Unsatisfiable,
            LocalCastMediaServer.parseByteRange("bytes=-0", 1000L)
        )
    }

    @Test
    fun selectPreferredLanIpv4_prioritizesWifiSiteLocalOverVpnAndCellular() {
        val candidates = listOf(
            LocalCastMediaServer.NetworkAddressCandidate("rmnet_data0", "10.42.0.5", isSiteLocal = true),
            LocalCastMediaServer.NetworkAddressCandidate("tun0", "10.8.0.2", isSiteLocal = true),
            LocalCastMediaServer.NetworkAddressCandidate("eth0", "192.168.1.50", isSiteLocal = true),
            LocalCastMediaServer.NetworkAddressCandidate("wlan0", "192.168.1.105", isSiteLocal = true)
        )
        assertEquals("192.168.1.105", LocalCastMediaServer.selectPreferredLanIpv4(candidates))
    }

    @Test
    fun extractTokenFromPath_validatesMediaPrefixAndRejectsTraversal() {
        assertEquals("abc123", LocalCastMediaServer.extractTokenFromPath("/media/abc123/song.m4a"))
        assertEquals("token_42", LocalCastMediaServer.extractTokenFromPath("/media/token_42"))
        assertNull(LocalCastMediaServer.extractTokenFromPath("/media/../etc/passwd"))
        assertNull(LocalCastMediaServer.extractTokenFromPath("/other/abc123/song.m4a"))
        assertNull(LocalCastMediaServer.extractTokenFromPath("/media/"))
    }

    @Test
    fun httpGet_servesFullFileWith200OkCorsAndAcceptRanges() {
        val mediaUrl = server.registerFile(tempFile, mimeType = "audio/mp4", dedupeKey = "track_1")
        assertNotNull(mediaUrl)
        assertTrue(server.isRunning)

        val conn = (URL(mediaUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 3_000
            readTimeout = 3_000
        }
        try {
            assertEquals(200, conn.responseCode)
            assertEquals("audio/mp4", conn.contentType)
            assertEquals("bytes", conn.getHeaderField("Accept-Ranges"))
            assertEquals("*", conn.getHeaderField("Access-Control-Allow-Origin"))
            assertEquals(payloadBytes.size.toString(), conn.getHeaderField("Content-Length"))

            val received = conn.inputStream.readBytes()
            assertArrayEquals(payloadBytes, received)
        } finally {
            conn.disconnect()
        }
    }

    @Test
    fun httpGet_withExplicitRange_serves206PartialContentWithExactBytes() {
        val mediaUrl = server.registerFile(tempFile, mimeType = "audio/mpeg", dedupeKey = "track_range")
        assertNotNull(mediaUrl)

        val conn = (URL(mediaUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Range", "bytes=128-383")
            connectTimeout = 3_000
            readTimeout = 3_000
        }
        try {
            assertEquals(206, conn.responseCode)
            assertEquals("audio/mpeg", conn.contentType)
            assertEquals("bytes 128-383/1024", conn.getHeaderField("Content-Range"))
            assertEquals("256", conn.getHeaderField("Content-Length"))
            assertEquals("*", conn.getHeaderField("Access-Control-Allow-Origin"))

            val received = conn.inputStream.readBytes()
            val expected = payloadBytes.copyOfRange(128, 384)
            assertArrayEquals(expected, received)
        } finally {
            conn.disconnect()
        }
    }

    @Test
    fun httpGet_withOpenEndedAndSuffixRanges_serves206PartialContent() {
        val mediaUrl = server.registerBytes(payloadBytes, mimeType = "audio/flac", suggestedFileName = "lossless.flac")
        assertNotNull(mediaUrl)

        // Open-ended range: bytes=900-
        val openConn = (URL(mediaUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Range", "bytes=900-")
            connectTimeout = 3_000
            readTimeout = 3_000
        }
        try {
            assertEquals(206, openConn.responseCode)
            assertEquals("bytes 900-1023/1024", openConn.getHeaderField("Content-Range"))
            assertArrayEquals(payloadBytes.copyOfRange(900, 1024), openConn.inputStream.readBytes())
        } finally {
            openConn.disconnect()
        }

        // Suffix range: bytes=-64
        val suffixConn = (URL(mediaUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Range", "bytes=-64")
            connectTimeout = 3_000
            readTimeout = 3_000
        }
        try {
            assertEquals(206, suffixConn.responseCode)
            assertEquals("bytes 960-1023/1024", suffixConn.getHeaderField("Content-Range"))
            assertArrayEquals(payloadBytes.copyOfRange(960, 1024), suffixConn.inputStream.readBytes())
        } finally {
            suffixConn.disconnect()
        }
    }

    @Test
    fun httpGet_withUnsatisfiableRange_returns416() {
        val mediaUrl = server.registerFile(tempFile, mimeType = "audio/mp4")
        assertNotNull(mediaUrl)

        val conn = (URL(mediaUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Range", "bytes=4096-8192")
            connectTimeout = 3_000
            readTimeout = 3_000
        }
        try {
            assertEquals(416, conn.responseCode)
            assertEquals("bytes */1024", conn.getHeaderField("Content-Range"))
        } finally {
            conn.disconnect()
        }
    }

    @Test
    fun httpHeadOptionsAnd404_returnExpectedStatusAndHeaders() {
        val mediaUrl = server.registerFile(tempFile, mimeType = "audio/mp4", dedupeKey = "dedupe_key")
        val dedupedUrl = server.registerFile(tempFile, mimeType = "audio/mp4", dedupeKey = "dedupe_key")
        assertEquals(mediaUrl, dedupedUrl)

        // HEAD request returns headers with zero body
        val headConn = (URL(mediaUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            connectTimeout = 3_000
            readTimeout = 3_000
        }
        try {
            assertEquals(200, headConn.responseCode)
            assertEquals("1024", headConn.getHeaderField("Content-Length"))
            assertEquals(0, headConn.inputStream.readBytes().size)
        } finally {
            headConn.disconnect()
        }

        // OPTIONS CORS preflight returns 204
        val optConn = (URL(mediaUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "OPTIONS"
            connectTimeout = 3_000
            readTimeout = 3_000
        }
        try {
            assertEquals(204, optConn.responseCode)
            assertEquals("*", optConn.getHeaderField("Access-Control-Allow-Origin"))
        } finally {
            optConn.disconnect()
        }

        // Unknown token returns 404
        val missingUrl = "http://127.0.0.1:${server.listeningPort}/media/nonexistenttoken/track.m4a"
        val missingConn = (URL(missingUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 3_000
            readTimeout = 3_000
        }
        try {
            assertEquals(404, missingConn.responseCode)
        } finally {
            missingConn.disconnect()
        }

        // Stop closes socket cleanly
        server.stop()
        assertFalse(server.isRunning)
    }

    @Test
    fun fifoEviction_evictsOnlyOldestTokenAndKeepsActiveTracksReachable() {
        val cappedServer = LocalCastMediaServer(
            bindHost = "127.0.0.1",
            requestedPort = 0,
            maxRegisteredSources = 2
        )
        try {
            val url1 = cappedServer.registerBytes("first".toByteArray(), "audio/mp4", dedupeKey = "k1")!!
            val url2 = cappedServer.registerBytes("second".toByteArray(), "audio/mp4", dedupeKey = "k2")!!
            // Adding a 3rd entry should evict k1 (oldest) while keeping k2 & k3 active
            val url3 = cappedServer.registerBytes("third".toByteArray(), "audio/mp4", dedupeKey = "k3")!!

            val c1 = (URL(url1).openConnection() as HttpURLConnection).apply { requestMethod = "GET" }
            val c2 = (URL(url2).openConnection() as HttpURLConnection).apply { requestMethod = "GET" }
            val c3 = (URL(url3).openConnection() as HttpURLConnection).apply { requestMethod = "GET" }
            try {
                assertEquals(404, c1.responseCode)
                assertEquals(200, c2.responseCode)
                assertEquals("second", c2.inputStream.readBytes().decodeToString())
                assertEquals(200, c3.responseCode)
                assertEquals("third", c3.inputStream.readBytes().decodeToString())
            } finally {
                c1.disconnect()
                c2.disconnect()
                c3.disconnect()
            }
        } finally {
            cappedServer.stop()
        }
    }
}
