package com.xperience.player.service

import com.xperience.player.ui.settings.isAllowedGoogleAuthUrl
import com.xperience.player.widget.WidgetActions
import com.xperience.player.widget.WidgetDeepLink
import com.xperience.player.widget.WidgetPlaylistSourceType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

class SecurityHardeningTest {

    // -------------------------------------------------------------------------
    // 1. Google/YouTube Auth WebView URL Allowlist
    // -------------------------------------------------------------------------

    @Test
    fun isAllowedGoogleAuthUrl_allowsLegitimateHttpsGoogleAndYouTubeHosts() {
        val allowed = listOf(
            "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com",
            "https://accounts.youtube.com/accounts/SetSID",
            "https://music.youtube.com/",
            "https://www.youtube.com/signin",
            "https://m.youtube.com/",
            "https://myaccount.google.com/",
            "https://consent.google.com/m",
            "https://consent.youtube.com/m",
            "https://gds.google.com/web/chip",
            "https://gstatic.com/foo.js",
            "https://ssl.gstatic.com/accounts/ui/avatar.png",
            "https://lh3.googleusercontent.com/a/avatar",
            "https://youtubei.googleapis.com/youtubei/v1/browse"
        )
        for (url in allowed) {
            assertTrue("Expected allowed URL: $url", isAllowedGoogleAuthUrl(url))
            assertTrue("Expected allowed URL via policy: $url", YouTubeSecurityPolicy.isAllowedGoogleAuthUrl(url))
        }
    }

    @Test
    fun isAllowedGoogleAuthUrl_blocksHttpAndLookalikeDomains() {
        val blocked = listOf(
            "http://accounts.google.com/ServiceLogin",
            "http://music.youtube.com/",
            "https://evil-google.com/ServiceLogin",
            "https://accounts.google.com.evil.com/ServiceLogin",
            "https://youtube.com.attacker.net/",
            "https://notyoutube.com/login",
            "https://evil.example.com/?continue=https://accounts.google.com",
            "https://user:pass@accounts.google.com/ServiceLogin",
            "file:///data/data/com.xperience.player/shared_prefs/xperience_ytm_auth_prefs.xml",
            "javascript:alert(1)",
            "intent://music.youtube.com/#Intent;scheme=https;end",
            ""
        )
        for (url in blocked) {
            assertFalse("Expected blocked URL: $url", isAllowedGoogleAuthUrl(url))
        }
    }

    // -------------------------------------------------------------------------
    // 2. YouTube API / Stream Host Allowlists & Redirect Header Stripping
    // -------------------------------------------------------------------------

    @Test
    fun hostAllowlists_validateApiAndStreamHostsStrictly() {
        assertTrue(YouTubeSecurityPolicy.isAllowedYouTubeApiHost("music.youtube.com"))
        assertTrue(YouTubeSecurityPolicy.isAllowedYouTubeApiHost("www.youtube.com"))
        assertTrue(YouTubeSecurityPolicy.isAllowedYouTubeApiHost("m.youtube.com"))
        assertTrue(YouTubeSecurityPolicy.isAllowedYouTubeApiHost("youtube.com"))
        assertTrue(YouTubeSecurityPolicy.isAllowedYouTubeApiHost("youtubei.googleapis.com"))

        assertFalse(YouTubeSecurityPolicy.isAllowedYouTubeApiHost("rr1.googlevideo.com"))
        assertFalse(YouTubeSecurityPolicy.isAllowedYouTubeApiHost("evil-youtube.com"))
        assertFalse(YouTubeSecurityPolicy.isAllowedYouTubeApiHost("music.youtube.com.evil.com"))

        assertTrue(YouTubeSecurityPolicy.isAllowedYouTubeStreamHost("rr1---sn-abc.googlevideo.com"))
        assertTrue(YouTubeSecurityPolicy.isAllowedYouTubeStreamHost("manifest.googlevideo.com"))
        assertTrue(YouTubeSecurityPolicy.isAllowedYouTubeStreamHost("music.youtube.com"))
        assertTrue(YouTubeSecurityPolicy.isAllowedYouTubeStreamHost("youtube.com"))

        assertFalse(YouTubeSecurityPolicy.isAllowedYouTubeStreamHost("googlevideo.com"))
        assertFalse(YouTubeSecurityPolicy.isAllowedYouTubeStreamHost("evil-googlevideo.com"))
        assertFalse(YouTubeSecurityPolicy.isAllowedYouTubeStreamHost("rr1.googlevideo.com.evil.com"))
        assertFalse(YouTubeSecurityPolicy.isAllowedYouTubeStreamHost("evil.com"))

        assertTrue(YouTubeSecurityPolicy.isSafeHttpsYouTubeStreamUrl("https://rr1.googlevideo.com/videoplayback?id=1"))
        assertFalse(YouTubeSecurityPolicy.isSafeHttpsYouTubeStreamUrl("http://rr1.googlevideo.com/videoplayback?id=1"))
        assertFalse(YouTubeSecurityPolicy.isSafeHttpsYouTubeStreamUrl("https://evil.com/videoplayback?id=1"))
    }

    @Test
    fun authScopingInterceptor_preservesHeadersOnYouTubeApiAndStripsOnCrossDomainOrHttp() {
        fun requestWithAuth(url: String): Request = Request.Builder()
            .url(url)
            .header("Cookie", "SAPISID=secret; SID=abc")
            .header("Authorization", "SAPISIDHASH 123_deadbeef")
            .header("X-Goog-PageId", "123456")
            .header("X-Goog-AuthUser", "0")
            .header("X-Youtube-Bootstrap-Logged-In", "true")
            .header("User-Agent", "TestAgent")
            .build()

        // 1. Trusted HTTPS YouTube API host retains all headers
        val apiReq = YouTubeSecurityPolicy.stripDisallowedAuthHeaders(
            requestWithAuth("https://music.youtube.com/youtubei/v1/browse")
        )
        assertEquals("SAPISID=secret; SID=abc", apiReq.header("Cookie"))
        assertEquals("SAPISIDHASH 123_deadbeef", apiReq.header("Authorization"))
        assertEquals("123456", apiReq.header("X-Goog-PageId"))
        assertEquals("0", apiReq.header("X-Goog-AuthUser"))
        assertEquals("true", apiReq.header("X-Youtube-Bootstrap-Logged-In"))

        // 2. Trusted HTTPS stream host retains Cookie, strips API-only auth headers
        val streamReq = YouTubeSecurityPolicy.stripDisallowedAuthHeaders(
            requestWithAuth("https://rr1.googlevideo.com/videoplayback?id=123")
        )
        assertEquals("SAPISID=secret; SID=abc", streamReq.header("Cookie"))
        assertNull(streamReq.header("Authorization"))
        assertNull(streamReq.header("X-Goog-PageId"))
        assertNull(streamReq.header("X-Goog-AuthUser"))
        assertNull(streamReq.header("X-Youtube-Bootstrap-Logged-In"))
        assertEquals("TestAgent", streamReq.header("User-Agent"))

        // 3. Cross-domain redirect target strips Cookie and all API auth headers
        val crossDomainReq = YouTubeSecurityPolicy.stripDisallowedAuthHeaders(
            requestWithAuth("https://evil.example.com/capture")
        )
        assertNull(crossDomainReq.header("Cookie"))
        assertNull(crossDomainReq.header("Authorization"))
        assertNull(crossDomainReq.header("X-Goog-PageId"))
        assertNull(crossDomainReq.header("X-Goog-AuthUser"))
        assertNull(crossDomainReq.header("X-Youtube-Bootstrap-Logged-In"))
        assertEquals("TestAgent", crossDomainReq.header("User-Agent"))

        // 4. Plaintext HTTP downgrade strips Cookie and all API auth headers even on youtube host
        val httpDowngradeReq = YouTubeSecurityPolicy.stripDisallowedAuthHeaders(
            requestWithAuth("http://music.youtube.com/youtubei/v1/browse")
        )
        assertNull(httpDowngradeReq.header("Cookie"))
        assertNull(httpDowngradeReq.header("Authorization"))
        assertNull(httpDowngradeReq.header("X-Goog-PageId"))

        // 5. Scoped client disables SSL downgrade redirects and registers network interceptor
        val scopedClient = YouTubeSecurityPolicy.createScopedYouTubeHttpClient(OkHttpClient())
        assertFalse(scopedClient.followSslRedirects)
        assertTrue(scopedClient.networkInterceptors.isNotEmpty())

        // 6. Interceptor itself executes cleanly and strips headers on cross-domain request
        var interceptedRequest: Request? = null
        val testClient = OkHttpClient.Builder()
            .addInterceptor(YouTubeSecurityPolicy.authScopingNetworkInterceptor())
            .addInterceptor { chain ->
                interceptedRequest = chain.request()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("ok".toResponseBody())
                    .build()
            }
            .build()
        testClient.newCall(requestWithAuth("https://attacker.invalid/leak")).execute().use { resp ->
            assertEquals(200, resp.code)
        }
        val req = interceptedRequest
        assertNotNull(req)
        assertNull(req?.header("Cookie"))
        assertNull(req?.header("Authorization"))
    }

    // -------------------------------------------------------------------------
    // 3. YouTubeAuthManager Keystore Key Erasure on Sign-Out
    // -------------------------------------------------------------------------

    @Test
    fun authManagerClearSession_deletesCipherKeyAndClearsState() {
        val deleteCalls = AtomicInteger(0)
        val fakeCipher = object : SecretCipher {
            override fun encrypt(plaintext: String): String = "enc:$plaintext"
            override fun decrypt(ciphertext: String): String? =
                ciphertext.takeIf { it.startsWith("enc:") }?.removePrefix("enc:")
            override fun deleteKey() {
                deleteCalls.incrementAndGet()
            }
        }
        val store = InMemoryAuthStore()
        val manager = YouTubeAuthManager(store = store, cipher = fakeCipher, hydrateAsync = false)

        manager.saveSession(
            cookie = "SID=sid_val; SAPISID=sapi_val; __Secure-3PAPISID=p3_val",
            visitorData = "CgtVisitor123"
        )
        assertTrue(manager.isSignedIn())
        assertEquals(0, deleteCalls.get())

        manager.clearSession()

        assertFalse(manager.isSignedIn())
        assertNull(manager.getCookie())
        assertNull(manager.getVisitorData())
        assertEquals(1, deleteCalls.get())
    }

    // -------------------------------------------------------------------------
    // 4. MornyeMusicDownloadService Path Traversal & URL Validation Hardening
    // -------------------------------------------------------------------------

    @Test
    fun sanitizeFileName_blocksTraversalNullBytesLeadingDotsAndCapsLength() {
        val traversal = MornyeMusicDownloadService.sanitizeFileName("../../../etc/passwd")
        assertFalse(traversal.contains(".."))
        assertFalse(traversal.contains("/"))
        assertFalse(traversal.contains("\\"))
        assertFalse(traversal.startsWith("."))

        val withNullAndCtrl = MornyeMusicDownloadService.sanitizeFileName("Track\u0000Name\r\n\t01")
        assertFalse(withNullAndCtrl.contains('\u0000'))
        assertFalse(withNullAndCtrl.contains('\r'))
        assertFalse(withNullAndCtrl.contains('\n'))
        assertEquals("TrackName01", withNullAndCtrl)

        assertEquals("hidden", MornyeMusicDownloadService.sanitizeFileName("...hidden"))
        assertEquals("track", MornyeMusicDownloadService.sanitizeFileName("..."))
        assertEquals("track", MornyeMusicDownloadService.sanitizeFileName("   "))

        val longName = MornyeMusicDownloadService.sanitizeFileName("a".repeat(250))
        assertTrue(longName.length <= 120)
    }

    @Test
    fun downloadService_canonicalContainmentAndUrlValidation() {
        val tempDir = Files.createTempDirectory("mornye_sec_test").toFile()
        try {
            val safe = MornyeMusicDownloadService.resolveSafeLocalFile(tempDir, "Song - Artist.flac")
            assertNotNull(safe)
            assertEquals(File(tempDir, "Song - Artist.flac").canonicalPath, safe!!.canonicalPath)

            val escaped = MornyeMusicDownloadService.resolveSafeLocalFile(tempDir, "../outside.flac")
            assertNull(escaped)

            assertTrue(MornyeMusicDownloadService.isHttpsUrl("https://cdn.example.com/track.flac"))
            assertFalse(MornyeMusicDownloadService.isHttpsUrl("http://cdn.example.com/track.flac"))
            assertFalse(MornyeMusicDownloadService.isHttpsUrl("file:///etc/passwd"))

            assertTrue(MornyeMusicDownloadService.isValidTidalId("123456789"))
            assertFalse(MornyeMusicDownloadService.isValidTidalId("1234/../admin"))
            assertFalse(MornyeMusicDownloadService.isValidTidalId("1234?foo=bar"))

            assertTrue(MornyeMusicDownloadService.isValidTidalCoverPath("abcd1234-5678-ef90"))
            assertFalse(MornyeMusicDownloadService.isValidTidalCoverPath("../../evil"))
            assertFalse(MornyeMusicDownloadService.isValidTidalCoverPath("abc/def"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // -------------------------------------------------------------------------
    // 5. WidgetDeepLink ID Validation
    // -------------------------------------------------------------------------

    @Test
    fun widgetDeepLink_rejectsMalformedIdsAndPreservesValidIds() {
        // Malformed IDs are stripped/rejected
        val malformed = WidgetDeepLink.parseRaw(
            action = WidgetActions.ACTION_PLAY_PLAYLIST,
            playlistId = "local_1",
            playlistTitle = "Test",
            sourceTypeRaw = WidgetPlaylistSourceType.RADIO_STATION.wireValue,
            ytmPlaylistId = "PL123\";\"injected\":\"1",
            browseId = "../etc/passwd",
            seedVideoId = "a" // too short (<2 chars)
        ) as WidgetDeepLink.OpenPlaylist

        assertNull(malformed.ytmPlaylistId)
        assertNull(malformed.browseId)
        assertNull(malformed.seedVideoId)

        // When playlistId is blank and ytmPlaylistId is malformed, degrades safely to OpenHome
        val degraded = WidgetDeepLink.parseRaw(
            action = WidgetActions.ACTION_PLAY_PLAYLIST,
            playlistId = "",
            ytmPlaylistId = "invalid id with spaces!"
        )
        assertEquals(WidgetDeepLink.OpenHome, degraded)

        // Valid IDs pass through intact
        val valid = WidgetDeepLink.parseRaw(
            action = WidgetActions.ACTION_PLAY_PLAYLIST,
            playlistId = "RDAMVM_valid123",
            ytmPlaylistId = "RDAMVM_valid123",
            browseId = "VLRDAMVM-valid_123",
            seedVideoId = "dQw4w9WgXcQ"
        ) as WidgetDeepLink.OpenPlaylist

        assertEquals("RDAMVM_valid123", valid.ytmPlaylistId)
        assertEquals("VLRDAMVM-valid_123", valid.browseId)
        assertEquals("dQw4w9WgXcQ", valid.seedVideoId)
    }
}
