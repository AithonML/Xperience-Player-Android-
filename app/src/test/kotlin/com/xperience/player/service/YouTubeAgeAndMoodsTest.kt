package com.xperience.player.service

import com.xperience.player.domain.model.TrackItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class YouTubeAgeAndMoodsTest {

    private class JvmAesGcmCipher : SecretCipher {
        private val keySpec = SecretKeySpec(
            "0123456789ABCDEF0123456789ABCDEF".toByteArray(Charsets.UTF_8),
            "AES"
        )
        private val iv = "123456789012".toByteArray(Charsets.UTF_8)

        override fun encrypt(plaintext: String): String {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(128, iv))
            val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            return java.util.Base64.getEncoder().encodeToString(iv + encrypted)
        }

        override fun decrypt(ciphertext: String): String? {
            return runCatching {
                val decoded = java.util.Base64.getDecoder().decode(ciphertext)
                val ivPart = decoded.copyOfRange(0, 12)
                val dataPart = decoded.copyOfRange(12, decoded.size)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(128, ivPart))
                String(cipher.doFinal(dataPart), Charsets.UTF_8)
            }.getOrNull()
        }
    }

    private data class RecordedHttpCall(
        val url: String,
        val method: String,
        val headers: Map<String, String>,
        val bodyJson: JSONObject
    )

    private fun readRequestJson(chain: Interceptor.Chain): JSONObject {
        val buffer = Buffer()
        chain.request().body?.writeTo(buffer)
        val raw = buffer.readUtf8()
        return if (raw.isBlank()) JSONObject() else JSONObject(raw)
    }

    private fun headersMap(chain: Interceptor.Chain): Map<String, String> {
        val req = chain.request()
        val map = linkedMapOf<String, String>()
        for (name in req.headers.names()) {
            map[name] = req.header(name).orEmpty()
        }
        return map
    }

    private fun jsonResponse(chain: Interceptor.Chain, json: JSONObject, code: Int = 200): Response {
        return Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "Error")
            .body(json.toString().toResponseBody())
            .build()
    }

    private fun textResponse(
        chain: Interceptor.Chain,
        text: String,
        code: Int = 200,
        setCookies: List<String> = emptyList()
    ): Response {
        val builder = Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "Error")
            .body(text.toResponseBody())
        setCookies.forEach { builder.addHeader("Set-Cookie", it) }
        return builder.build()
    }

    private fun binaryResponse(chain: Interceptor.Chain, bytes: ByteArray, code: Int = 200): Response {
        return Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "Error")
            .body(bytes.toResponseBody())
            .build()
    }

    private fun createSignedInAuthManager(
        dataSyncId: String? = "10987654321||998877",
        visitorData: String = "CgtUserSessionVisitorData123456789012345678901234567890"
    ): YouTubeAuthManager {
        val store = InMemoryAuthStore()
        val manager = YouTubeAuthManager(store, JvmAesGcmCipher())
        manager.saveSession(
            cookie = "SAPISID=sapi_secret; __Secure-1PAPISID=p1_secret; __Secure-3PAPISID=p3_secret; SID=sid_tok",
            visitorData = visitorData,
            dataSyncId = dataSyncId,
            accountName = "Test Listener",
            accountHandle = "@testlistener"
        )
        assertTrue(manager.isSignedIn())
        return manager
    }

    private fun runs(vararg pairs: Pair<String, JSONObject?>): JSONObject {
        val array = JSONArray()
        pairs.forEach { (text, navEp) ->
            val obj = JSONObject().put("text", text)
            if (navEp != null) obj.put("navigationEndpoint", navEp)
            array.put(obj)
        }
        return JSONObject().put("runs", array)
    }

    private fun flexCol(textObj: JSONObject): JSONObject {
        return JSONObject().put(
            "musicResponsiveListItemFlexColumnRenderer",
            JSONObject().put("text", textObj)
        )
    }

    private fun explicitBadgeArray(useMetadataBadge: Boolean = false): JSONArray {
        val badgeObj = if (useMetadataBadge) {
            JSONObject().put(
                "metadataBadgeRenderer",
                JSONObject()
                    .put("icon", JSONObject().put("iconType", "MUSIC_EXPLICIT_BADGE"))
                    .put("style", "BADGE_STYLE_TYPE_EXPLICIT")
            )
        } else {
            JSONObject().put(
                "musicInlineBadgeRenderer",
                JSONObject()
                    .put("icon", JSONObject().put("iconType", "MUSIC_EXPLICIT_BADGE"))
            )
        }
        return JSONArray().put(badgeObj)
    }

    private fun watchNav(videoId: String, musicVideoType: String = "MUSIC_VIDEO_TYPE_ATV", playlistId: String? = null): JSONObject {
        val watch = JSONObject().put("videoId", videoId)
        if (!playlistId.isNullOrBlank()) watch.put("playlistId", playlistId)
        watch.put(
            "watchEndpointMusicSupportedConfigs",
            JSONObject().put(
                "watchEndpointMusicConfig",
                JSONObject().put("musicVideoType", musicVideoType)
            )
        )
        return JSONObject().put("watchEndpoint", watch)
    }

    private fun browseNav(browseId: String, pageType: String, params: String? = null): JSONObject {
        val ep = JSONObject()
            .put("browseId", browseId)
            .put(
                "browseEndpointContextSupportedConfigs",
                JSONObject().put(
                    "browseEndpointContextMusicConfig",
                    JSONObject().put("pageType", pageType)
                )
            )
        if (!params.isNullOrBlank()) ep.put("params", params)
        return JSONObject().put("browseEndpoint", ep)
    }

    private fun thumbArray(url: String, width: Int = 120, height: Int = 120): JSONArray {
        return JSONArray().put(
            JSONObject()
                .put("url", url)
                .put("width", width)
                .put("height", height)
        )
    }

    private fun playerSuccessJson(streamUrl: String, videoId: String, musicVideoType: String = "MUSIC_VIDEO_TYPE_ATV"): JSONObject {
        return JSONObject()
            .put("playabilityStatus", JSONObject().put("status", "OK"))
            .put(
                "videoDetails",
                JSONObject()
                    .put("videoId", videoId)
                    .put("title", "Starfucker")
                    .put("author", "Slayyyter")
                    .put("musicVideoType", musicVideoType)
            )
            .put(
                "streamingData",
                JSONObject().put(
                    "adaptiveFormats",
                    JSONArray().put(
                        JSONObject()
                            .put("itag", 140)
                            .put("mimeType", "audio/mp4; codecs=\"mp4a.40.2\"")
                            .put("bitrate", 131072)
                            .put("url", streamUrl)
                    )
                )
            )
    }

    private fun playerBlockedJson(status: String, reason: String): JSONObject {
        return JSONObject().put(
            "playabilityStatus",
            JSONObject()
                .put("status", status)
                .put("reason", reason)
        )
    }

    // -------------------------------------------------------------------------
    // Objective 1: Age-restricted / explicit playback (signed in & anon fallback)
    // -------------------------------------------------------------------------

    @Test
    fun signedInAgeRestrictedSkipsWebAuthOnVisionOsAndDeciphersWebRemixCipher() = runTest {
        val calls = mutableListOf<RecordedHttpCall>()
        val tempAudioDir = Files.createTempDirectory("ytm_signed_age_audio").toFile()
        val authManager = createSignedInAuthManager()
        val fakeAudioBytes = ByteArray(60_000) { (it % 127).toByte() }
        val rawCipherStreamUrl = "https://rr1.googlevideo.com/videoplayback?id=starfck01&itag=140&n=rawNval"
        val decipheredStreamUrl = "https://rr1.googlevideo.com/videoplayback?id=starfck01&itag=140&n=solvedN&alr=yes&sig=DECODED_SIG"
        val fakeResolver = object : PlayerScriptResolver {
            override suspend fun getSignatureTimestamp(): Int = 20767
            override suspend fun transformStreamUrl(url: String, sp: String, signature: String): String {
                return if (signature == "ENC_SIG_999" && sp == "sig") decipheredStreamUrl else url
            }
        }

        val cipherFormatJson = JSONObject()
            .put("playabilityStatus", JSONObject().put("status", "OK"))
            .put(
                "videoDetails",
                JSONObject()
                    .put("videoId", "SlayStar001")
                    .put("title", "Starfucker")
                    .put("author", "Slayyyter")
                    .put("musicVideoType", "MUSIC_VIDEO_TYPE_ATV")
            )
            .put(
                "streamingData",
                JSONObject().put(
                    "adaptiveFormats",
                    JSONArray().put(
                        JSONObject()
                            .put("itag", 140)
                            .put("mimeType", "audio/mp4; codecs=\"mp4a.40.2\"")
                            .put("bitrate", 131072)
                            .put("contentLength", "60000")
                            .put(
                                "signatureCipher",
                                "s=ENC_SIG_999&sp=sig&url=" + java.net.URLEncoder.encode(rawCipherStreamUrl, "UTF-8")
                            )
                    )
                )
            )

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val reqJson = readRequestJson(chain)
                val headers = headersMap(chain)
                calls.add(RecordedHttpCall(url, chain.request().method, headers, reqJson))

                when {
                    url.contains("sw.js_data") -> textResponse(
                        chain,
                        ")]}'\n[[\"CgtAnonVisitor012345678901234567890\"]]",
                        200,
                        setCookies = listOf("SOCS=CAI_anon; Path=/; Domain=.youtube.com")
                    )
                    url.contains("music.youtube.com/youtubei/v1/player") -> {
                        val clientName = reqJson.optJSONObject("context")?.optJSONObject("client")?.optString("clientName")
                        if (clientName == "WEB_REMIX" && reqJson.has("playbackContext")) {
                            jsonResponse(chain, cipherFormatJson)
                        } else {
                            // ATV metadata lookup initially returns LOGIN_REQUIRED until playback resolver tries WEB_REMIX+sts
                            jsonResponse(
                                chain,
                                JSONObject()
                                    .put(
                                        "videoDetails",
                                        JSONObject()
                                            .put("videoId", "SlayStar001")
                                            .put("title", "Starfucker")
                                            .put("author", "Slayyyter")
                                            .put("musicVideoType", "MUSIC_VIDEO_TYPE_ATV")
                                    )
                                    .put("playabilityStatus", JSONObject().put("status", "LOGIN_REQUIRED"))
                            )
                        }
                    }
                    url.contains("/youtubei/v1/player") -> {
                        val clientName = reqJson.optJSONObject("context")?.optJSONObject("client")?.optString("clientName")
                        when (clientName) {
                            "VISIONOS" -> jsonResponse(
                                chain,
                                playerBlockedJson("AGE_VERIFICATION_REQUIRED", "Confirm your age")
                            )
                            else -> jsonResponse(chain, playerBlockedJson("ERROR", "Unexpected client"))
                        }
                    }
                    url.contains("googlevideo.com/videoplayback") -> binaryResponse(chain, fakeAudioBytes, 200)
                    else -> jsonResponse(chain, JSONObject(), 404)
                }
            }
            .build()

        try {
            val service = YouTubeMusicService(
                httpClient = client,
                clockMs = { 1_700_000_000_000L },
                authManager = authManager,
                audioCacheDirOverride = tempAudioDir,
                playerScriptResolverOverride = fakeResolver
            )

            val file = service.getOrDownloadAudioFile("ytm_SlayStar001", context = null)
            assertNotNull(file)
            assertTrue(file!!.exists())
            assertEquals(60_000L, file.length())

            val visionCalls = calls.filter {
                it.bodyJson.optJSONObject("context")?.optJSONObject("client")?.optString("clientName") == "VISIONOS"
            }
            assertEquals(1, visionCalls.size)
            assertFalse(visionCalls[0].headers.containsKey("Authorization"))
            assertFalse(visionCalls[0].headers["Cookie"].orEmpty().contains("SAPISID="))

            val webRemixStreamCall = calls.first {
                it.bodyJson.optJSONObject("context")?.optJSONObject("client")?.optString("clientName") == "WEB_REMIX" &&
                    it.bodyJson.has("playbackContext")
            }
            assertEquals(
                20767,
                webRemixStreamCall.bodyJson.optJSONObject("playbackContext")
                    ?.optJSONObject("contentPlaybackContext")
                    ?.optInt("signatureTimestamp")
            )
            assertTrue(webRemixStreamCall.headers["Authorization"].orEmpty().contains("SAPISIDHASH"))
            assertTrue(webRemixStreamCall.headers["Cookie"].orEmpty().contains("SAPISID=sapi_secret"))
            assertEquals("https://music.youtube.com", webRemixStreamCall.headers["Origin"])
            assertTrue(webRemixStreamCall.bodyJson.optBoolean("contentCheckOk"))
            assertTrue(webRemixStreamCall.bodyJson.optBoolean("racyCheckOk"))

            val downloadCall = calls.first { it.url.contains("googlevideo.com/videoplayback") }
            assertTrue(downloadCall.url.contains("sig=DECODED_SIG"))
            assertTrue(downloadCall.url.contains("range=0-59999"))
            assertEquals("https://music.youtube.com", downloadCall.headers["Origin"])
            assertTrue(downloadCall.headers["Cookie"].orEmpty().contains("SAPISID=sapi_secret"))
        } finally {
            tempAudioDir.deleteRecursively()
        }
    }

    @Test
    fun signedInDelegatedBrandAccount400RetriesWithoutPageIdAndSucceeds() = runTest {
        val playerRequests = mutableListOf<RecordedHttpCall>()
        val authManager = createSignedInAuthManager(dataSyncId = "BRAND_PAGE_42||99")

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val reqJson = readRequestJson(chain)
                val headers = headersMap(chain)
                val call = RecordedHttpCall(url, chain.request().method, headers, reqJson)

                when {
                    url.contains("music.youtube.com/youtubei/v1/player") -> {
                        if (reqJson.has("playbackContext")) {
                            playerRequests.add(call)
                        }
                        if (headers.containsKey("X-Goog-PageId")) {
                            jsonResponse(chain, JSONObject(), code = 400)
                        } else {
                            jsonResponse(chain, playerSuccessJson("https://rr1.googlevideo.com/videoplayback?id=brand_ok&itag=140", "BrandVid001"))
                        }
                    }
                    url.contains("/youtubei/v1/player") -> {
                        jsonResponse(chain, playerBlockedJson("LOGIN_REQUIRED", "Age check"))
                    }
                    else -> jsonResponse(chain, JSONObject(), 404)
                }
            }
            .build()

        val fakeResolver = object : PlayerScriptResolver {
            override suspend fun getSignatureTimestamp(): Int = 20767
            override suspend fun transformStreamUrl(url: String, sp: String, signature: String): String = url
        }
        val service = YouTubeMusicService(
            httpClient = client,
            clockMs = { 1_700_000_000_000L },
            authManager = authManager,
            playerScriptResolverOverride = fakeResolver
        )

        val resolvedUrl = service.resolveAudioStreamUrl("ytm_BrandVid001")
        assertEquals("https://rr1.googlevideo.com/videoplayback?id=brand_ok&itag=140", resolvedUrl)
        assertEquals(2, playerRequests.size)
        assertEquals("BRAND_PAGE_42", playerRequests[0].headers["X-Goog-PageId"])
        assertTrue(playerRequests[0].bodyJson.optJSONObject("context")?.optJSONObject("user")?.has("onBehalfOfUser") == true)
        assertFalse(playerRequests[1].headers.containsKey("X-Goog-PageId"))
        assertFalse(playerRequests[1].bodyJson.optJSONObject("context")?.optJSONObject("user")?.has("onBehalfOfUser") == true)
    }

    @Test
    fun unauthenticatedAgeRestrictedAndDownload403FallsBackCleanly() = runTest {
        val calls = mutableListOf<RecordedHttpCall>()
        val tempAudioDir = Files.createTempDirectory("ytm_unauth_age_audio").toFile()
        val downloadAttempts = AtomicInteger(0)
        val fakeAudioBytes = ByteArray(55_000) { 0x42 }

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val reqJson = readRequestJson(chain)
                val headers = headersMap(chain)
                calls.add(RecordedHttpCall(url, chain.request().method, headers, reqJson))

                when {
                    url.contains("sw.js_data") -> textResponse(
                        chain,
                        ")]}'\n[[\"CgtAnonymousVisitorDataString123456789012345678901234567890\"]]",
                        200,
                        setCookies = listOf("SOCS=CAI_test; Path=/; Domain=.youtube.com", "YSC=ysc_tok; Path=/")
                    )
                    url.contains("music.youtube.com/youtubei/v1/player") -> jsonResponse(
                        chain,
                        JSONObject().put(
                            "videoDetails",
                            JSONObject()
                                .put("videoId", "AnonAge0001")
                                .put("title", "Explicit Track")
                                .put("author", "Slayyyter")
                                .put("musicVideoType", "MUSIC_VIDEO_TYPE_ATV")
                        )
                    )
                    url.contains("/youtubei/v1/player") -> {
                        val clientName = reqJson.optJSONObject("context")?.optJSONObject("client")?.optString("clientName")
                        when (clientName) {
                            "VISIONOS" -> jsonResponse(chain, playerBlockedJson("AGE_VERIFICATION_REQUIRED", "Sign in"))
                            "MWEB" -> jsonResponse(
                                chain,
                                playerSuccessJson("https://rr2.googlevideo.com/videoplayback?id=mweb403&itag=140", "AnonAge0001")
                            )
                            "ANDROID" -> jsonResponse(
                                chain,
                                playerSuccessJson("https://rr3.googlevideo.com/videoplayback?id=android_ok&itag=140", "AnonAge0001")
                            )
                            else -> jsonResponse(chain, playerBlockedJson("ERROR", "Unexpected"))
                        }
                    }
                    url.contains("googlevideo.com/videoplayback") -> {
                        downloadAttempts.incrementAndGet()
                        if (url.contains("id=mweb403")) {
                            binaryResponse(chain, ByteArray(0), code = 403)
                        } else {
                            binaryResponse(chain, fakeAudioBytes, code = 200)
                        }
                    }
                    else -> jsonResponse(chain, JSONObject(), 404)
                }
            }
            .build()

        try {
            val service = YouTubeMusicService(
                httpClient = client,
                clockMs = { 1_700_000_000_000L },
                authManager = null,
                audioCacheDirOverride = tempAudioDir
            )

            val file = service.getOrDownloadAudioFile("ytm_AnonAge0001", context = null)
            assertNotNull(file)
            assertEquals(55_000L, file!!.length())
            assertEquals(2, downloadAttempts.get())

            val androidDownloadCall = calls.last { it.url.contains("googlevideo.com/videoplayback") }
            assertFalse(androidDownloadCall.headers.containsKey("Origin"))
            assertFalse(androidDownloadCall.headers.containsKey("Referer"))
            assertTrue(androidDownloadCall.headers["User-Agent"].orEmpty().contains("com.google.android.youtube/20.10.38"))
        } finally {
            tempAudioDir.deleteRecursively()
        }
    }

    @Test
    fun canonicalAtvResolutionDoesNotPoisonCacheWhenAtvBlocked() = runTest {
        val atvFile = Files.createTempFile("atv_poison_guard", ".json").toFile()
        val authManager = createSignedInAuthManager()
        val atvPlayerCalls = AtomicInteger(0)

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                when {
                    url.contains("music.youtube.com/youtubei/v1/player") -> {
                        val attempt = atvPlayerCalls.incrementAndGet()
                        if (attempt == 1) {
                            jsonResponse(
                                chain,
                                JSONObject()
                                    .put("playabilityStatus", JSONObject().put("status", "LOGIN_REQUIRED"))
                                    .put(
                                        "videoDetails",
                                        JSONObject()
                                            .put("videoId", "AtvLock0001")
                                            .put("musicVideoType", "MUSIC_VIDEO_TYPE_ATV")
                                    )
                            )
                        } else {
                            jsonResponse(
                                chain,
                                JSONObject()
                                    .put("playabilityStatus", JSONObject().put("status", "OK"))
                                    .put(
                                        "videoDetails",
                                        JSONObject()
                                            .put("videoId", "AtvLock0001")
                                            .put("musicVideoType", "MUSIC_VIDEO_TYPE_ATV")
                                    )
                            )
                        }
                    }
                    else -> jsonResponse(chain, JSONObject(), 404)
                }
            }
            .build()

        try {
            val service = YouTubeMusicService(
                httpClient = client,
                authManager = authManager,
                atvCacheFileOverride = atvFile
            )

            assertEquals("AtvLock0001", service.resolveCanonicalAtvVideoId("AtvLock0001"))
            assertTrue(service.getAtvCounterpartSnapshot().isEmpty())

            assertEquals("AtvLock0001", service.resolveCanonicalAtvVideoId("AtvLock0001"))
            assertEquals("AtvLock0001", service.getAtvCounterpartSnapshot()["AtvLock0001"])
            assertEquals(2, atvPlayerCalls.get())
        } finally {
            atvFile.delete()
        }
    }

    // -------------------------------------------------------------------------
    // Objective 2: Explicit badge ("E") parsing across ALL YTM parsers
    // -------------------------------------------------------------------------

    @Test
    fun explicitBadgeParsedAcrossAllFiveYtmTrackParsers() = runTest {
        val searchPayload = JSONObject().put(
            "contents",
            JSONObject().put(
                "tabbedSearchResultsRenderer",
                JSONObject().put(
                    "tabs",
                    JSONArray().put(
                        JSONObject().put(
                            "tabRenderer",
                            JSONObject().put(
                                "content",
                                JSONObject().put(
                                    "sectionListRenderer",
                                    JSONObject().put(
                                        "contents",
                                        JSONArray().put(
                                            JSONObject().put(
                                                "musicShelfRenderer",
                                                JSONObject().put(
                                                    "contents",
                                                    JSONArray()
                                                        .put(
                                                            JSONObject().put(
                                                                "musicResponsiveListItemRenderer",
                                                                JSONObject()
                                                                    .put("badges", explicitBadgeArray())
                                                                    .put(
                                                                        "flexColumns",
                                                                        JSONArray()
                                                                            .put(flexCol(runs("Starfucker" to watchNav("ExpSearch01"))))
                                                                            .put(flexCol(runs("Slayyyter" to null, " • " to null, "STARFUCKER" to null, " • " to null, "3:12" to null)))
                                                                    )
                                                                    .put("thumbnail", JSONObject().put("musicThumbnailRenderer", JSONObject().put("thumbnail", JSONObject().put("thumbnails", thumbArray("https://lh3.googleusercontent.com/s1=w120-h120-l90-rj")))))
                                                            )
                                                        )
                                                        .put(
                                                            JSONObject().put(
                                                                "musicResponsiveListItemRenderer",
                                                                JSONObject()
                                                                    .put(
                                                                        "flexColumns",
                                                                        JSONArray()
                                                                            .put(flexCol(runs("Clean Song" to watchNav("CleanSrch01"))))
                                                                            .put(flexCol(runs("Slayyyter" to null, " • " to null, "STARFUCKER" to null, " • " to null, "2:55" to null)))
                                                                    )
                                                            )
                                                        )
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        val albumBrowsePayload = JSONObject()
            .put(
                "header",
                JSONObject().put(
                    "musicResponsiveHeaderRenderer",
                    JSONObject()
                        .put("title", runs("STARFUCKER" to null))
                        .put("subtitle", runs("Album" to null, " • " to null, "2023" to null))
                )
            )
            .put(
                "contents",
                JSONObject().put(
                    "twoColumnBrowseResultsRenderer",
                    JSONObject().put(
                        "secondaryContents",
                        JSONObject().put(
                            "sectionListRenderer",
                            JSONObject().put(
                                "contents",
                                JSONArray().put(
                                    JSONObject().put(
                                        "musicShelfRenderer",
                                        JSONObject().put(
                                            "contents",
                                            JSONArray().put(
                                                JSONObject().put(
                                                    "musicResponsiveListItemRenderer",
                                                    JSONObject()
                                                        .put("badges", explicitBadgeArray())
                                                        .put(
                                                            "flexColumns",
                                                            JSONArray()
                                                                .put(flexCol(runs("Out Of Time" to watchNav("ExpAlbum001"))))
                                                                .put(flexCol(runs("Slayyyter" to null)))
                                                        )
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val reqJson = readRequestJson(chain)
                when {
                    url.contains("/youtubei/v1/search") && reqJson.optString("params") == "EgWKAQIIAWoMEA4QChADEAQQCRAF" ->
                        jsonResponse(chain, searchPayload)
                    url.contains("/youtubei/v1/browse") ->
                        jsonResponse(chain, albumBrowsePayload)
                    else -> jsonResponse(chain, JSONObject())
                }
            }
            .build()

        val service = YouTubeMusicService(httpClient = client)

        // 1. parseResponsiveItem via SearchFilter.SONGS
        val searchedSongs = service.searchYouTubeMusic("Slayyyter", SearchFilter.SONGS).songs
        assertEquals(2, searchedSongs.size)
        assertTrue(searchedSongs[0].isExplicit)
        assertFalse(searchedSongs[1].isExplicit)

        // 2. extractAlbumTracks via getAlbumDetail
        val albumProfile = service.getAlbumDetail("ytm_alb_MPRE123", "STARFUCKER", "Slayyyter", null)
        assertNotNull(albumProfile)
        assertEquals(1, albumProfile!!.tracks.size)
        assertTrue(albumProfile.tracks[0].isExplicit)

        // 3. parseArtistTopTrack (direct JSON renderer test including metadataBadgeRenderer)
        val explicitTopRenderer = JSONObject()
            .put("badges", explicitBadgeArray(useMetadataBadge = true))
            .put(
                "flexColumns",
                JSONArray()
                    .put(flexCol(runs("Purrr" to watchNav("ExpTop00001"))))
                    .put(flexCol(runs("Slayyyter" to null)))
                    .put(flexCol(runs("STARFUCKER" to null)))
            )
        val cleanTopRenderer = JSONObject().put(
            "flexColumns",
            JSONArray()
                .put(flexCol(runs("Clouds" to watchNav("CleanTop001"))))
                .put(flexCol(runs("Slayyyter" to null)))
        )
        assertTrue(service.parseArtistTopTrack(explicitTopRenderer, "Slayyyter")!!.isExplicit)
        assertFalse(service.parseArtistTopTrack(cleanTopRenderer, "Slayyyter")!!.isExplicit)

        // 4. classifyTwoRowHomeItem via parseHomeFeedResponse (subtitleBadges)
        val homeJson = JSONObject().put(
            "contents",
            JSONObject().put(
                "singleColumnBrowseResultsRenderer",
                JSONObject().put(
                    "tabs",
                    JSONArray().put(
                        JSONObject().put(
                            "tabRenderer",
                            JSONObject().put(
                                "content",
                                JSONObject().put(
                                    "sectionListRenderer",
                                    JSONObject().put(
                                        "contents",
                                        JSONArray().put(
                                            JSONObject().put(
                                                "musicCarouselShelfRenderer",
                                                JSONObject()
                                                    .put("header", JSONObject().put("musicCarouselShelfBasicHeaderRenderer", JSONObject().put("title", runs("Quick picks" to null))))
                                                    .put(
                                                        "contents",
                                                        JSONArray().put(
                                                            JSONObject().put(
                                                                "musicTwoRowItemRenderer",
                                                                JSONObject()
                                                                    .put("title", runs("Erotic Electronic" to null))
                                                                    .put("subtitle", runs("Slayyyter" to null))
                                                                    .put("subtitleBadges", explicitBadgeArray())
                                                                    .put("navigationEndpoint", watchNav("ExpTwoRow01"))
                                                            )
                                                        )
                                                    )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )
        val homeFeed = service.parseHomeFeedResponse(homeJson, isPersonalized = false)
        assertEquals(1, homeFeed.personalizedTracks.size)
        assertTrue(homeFeed.personalizedTracks[0].isExplicit)

        // 5. parsePlaylistPanelVideoRenderer
        val explicitPanelRenderer = JSONObject()
            .put("videoId", "ExpPanel001")
            .put("title", runs("Daddy AF" to null))
            .put("longBylineText", runs("Slayyyter" to null, " • " to null, "Slayyyter" to null))
            .put("lengthText", runs("2:31" to null))
            .put("badges", explicitBadgeArray())
        assertTrue(service.parsePlaylistPanelVideoRenderer(explicitPanelRenderer)!!.isExplicit)
    }

    // -------------------------------------------------------------------------
    // Objective 3A: Real YTM "Ánimo y géneros" + Category Drill-Down
    // -------------------------------------------------------------------------

    @Test
    fun fetchMoodsAndGenresAndCategoryDetailParseSectionsColorsAndContent() = runTest {
        val moodsBrowseJson = JSONObject().put(
            "contents",
            JSONObject().put(
                "singleColumnBrowseResultsRenderer",
                JSONObject().put(
                    "tabs",
                    JSONArray().put(
                        JSONObject().put(
                            "tabRenderer",
                            JSONObject().put(
                                "content",
                                JSONObject().put(
                                    "sectionListRenderer",
                                    JSONObject().put(
                                        "contents",
                                        JSONArray()
                                            .put(
                                                JSONObject().put(
                                                    "gridRenderer",
                                                    JSONObject()
                                                        .put("header", JSONObject().put("gridHeaderRenderer", JSONObject().put("title", runs("Moods & moments" to null))))
                                                        .put(
                                                            "items",
                                                            JSONArray().put(
                                                                JSONObject().put(
                                                                    "musicNavigationButtonRenderer",
                                                                    JSONObject()
                                                                        .put("buttonText", runs("Workout" to null))
                                                                        .put("solid", JSONObject().put("leftStripeColor", -11776769L)) // 0xFF4C4EFF
                                                                        .put("clickCommand", browseNav("FEmusic_moods_and_genres_category", "MUSIC_PAGE_TYPE_NON_MUSIC_AUDIO_TRACK_PAGE", "ggMPOg1uX1JOQWZFeDByc2Jm"))
                                                                )
                                                            )
                                                        )
                                                )
                                            )
                                            .put(
                                                JSONObject().put(
                                                    "gridRenderer",
                                                    JSONObject()
                                                        .put("header", JSONObject().put("gridHeaderRenderer", JSONObject().put("title", runs("Genres" to null))))
                                                        .put(
                                                            "items",
                                                            JSONArray().put(
                                                                JSONObject().put(
                                                                    "musicNavigationButtonRenderer",
                                                                    JSONObject()
                                                                        .put("buttonText", runs("Hyperpop" to null))
                                                                        .put("solid", JSONObject().put("leftStripeColor", 0xFFD946EFL))
                                                                        .put("clickCommand", browseNav("FEmusic_moods_and_genres_category", "MUSIC_PAGE_TYPE_UNKNOWN", "ggMPOg1uX2h5cGVycG9w"))
                                                                )
                                                            )
                                                        )
                                                )
                                            )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        val categoryDetailJson = JSONObject()
            .put("header", JSONObject().put("musicHeaderRenderer", JSONObject().put("title", runs("Workout" to null))))
            .put(
                "contents",
                JSONObject().put(
                    "singleColumnBrowseResultsRenderer",
                    JSONObject().put(
                        "tabs",
                        JSONArray().put(
                            JSONObject().put(
                                "tabRenderer",
                                JSONObject().put(
                                    "content",
                                    JSONObject().put(
                                        "sectionListRenderer",
                                        JSONObject().put(
                                            "contents",
                                            JSONArray()
                                                .put(
                                                    JSONObject().put(
                                                        "musicCarouselShelfRenderer",
                                                        JSONObject()
                                                            .put("header", JSONObject().put("musicCarouselShelfBasicHeaderRenderer", JSONObject().put("title", runs("Featured playlists" to null))))
                                                            .put(
                                                                "contents",
                                                                JSONArray().put(
                                                                    JSONObject().put(
                                                                        "musicTwoRowItemRenderer",
                                                                        JSONObject()
                                                                            .put("title", runs("Cardio Bangers" to null))
                                                                            .put("subtitle", runs("YouTube Music • 45 songs" to null))
                                                                            .put("navigationEndpoint", browseNav("VLRDCLAK5uy_workout1", "MUSIC_PAGE_TYPE_PLAYLIST"))
                                                                            .put("thumbnailRenderer", JSONObject().put("musicThumbnailRenderer", JSONObject().put("thumbnail", JSONObject().put("thumbnails", thumbArray("//lh3.googleusercontent.com/pl_cardio=w120-h120-l90-rj")))))
                                                                    )
                                                                )
                                                            )
                                                    )
                                                )
                                                .put(
                                                    JSONObject().put(
                                                        "musicShelfRenderer",
                                                        JSONObject()
                                                            .put("title", runs("Trending songs" to null))
                                                            .put(
                                                                "contents",
                                                                JSONArray().put(
                                                                    JSONObject().put(
                                                                        "musicResponsiveListItemRenderer",
                                                                        JSONObject()
                                                                            .put("badges", explicitBadgeArray())
                                                                            .put(
                                                                                "flexColumns",
                                                                                JSONArray()
                                                                                    .put(flexCol(runs("Physical" to watchNav("MoodSong001"))))
                                                                                    .put(flexCol(runs("Dua Lipa" to null, " • " to null, "Future Nostalgia" to null, " • " to null, "3:13" to null)))
                                                                            )
                                                                    )
                                                                )
                                                            )
                                                    )
                                                )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )

        val browseCalls = AtomicInteger(0)
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val reqJson = readRequestJson(chain)
                if (url.contains("/youtubei/v1/browse")) {
                    browseCalls.incrementAndGet()
                    when (reqJson.optString("browseId")) {
                        "FEmusic_moods_and_genres" -> jsonResponse(chain, moodsBrowseJson)
                        "FEmusic_moods_and_genres_category" -> {
                            assertEquals("ggMPOg1uX1JOQWZFeDByc2Jm", reqJson.optString("params"))
                            jsonResponse(chain, categoryDetailJson)
                        }
                        else -> jsonResponse(chain, JSONObject(), 404)
                    }
                } else {
                    jsonResponse(chain, JSONObject(), 404)
                }
            }
            .build()

        val service = YouTubeMusicService(httpClient = client, clockMs = { 10_000L })

        val moods = service.fetchMoodsAndGenres()
        assertTrue(moods.isFromYouTubeMusic)
        assertEquals(2, moods.sections.size)
        assertEquals("Moods & moments", moods.sections[0].title)
        assertEquals("Workout", moods.sections[0].items[0].title)
        assertEquals(0xFF4C4CFFL, moods.sections[0].items[0].stripeColorArgb)
        assertEquals("ggMPOg1uX1JOQWZFeDByc2Jm", moods.sections[0].items[0].params)
        assertEquals("Genres", moods.sections[1].title)
        assertEquals(0xFFD946EFL, moods.sections[1].items[0].stripeColorArgb)

        // Second call hits in-memory cache
        val moodsCached = service.fetchMoodsAndGenres()
        assertEquals(moods, moodsCached)
        assertEquals(1, browseCalls.get())

        val detail = service.fetchMoodCategoryDetail(
            browseId = "FEmusic_moods_and_genres_category",
            params = "ggMPOg1uX1JOQWZFeDByc2Jm",
            categoryTitle = ""
        )
        assertEquals("Workout", detail.categoryTitle)
        assertEquals(1, detail.playlists.size)
        assertEquals("Cardio Bangers", detail.playlists[0].title)
        assertEquals(
            "https://lh3.googleusercontent.com/pl_cardio=w540-h540-l90-rj",
            detail.playlists[0].artworkUri
        )
        assertEquals(1, detail.songs.size)
        assertTrue(detail.songs[0].isExplicit)
        assertEquals(2, browseCalls.get())
    }

    // -------------------------------------------------------------------------
    // Objective 3B: Artist Photos in Search + URL Normalization + Concurrency
    // -------------------------------------------------------------------------

    @Test
    fun searchYouTubeMusicResolvesRealArtistPhotosAndPreservesCdnParams() = runTest {
        val artistSearchJson = JSONObject().put(
            "contents",
            JSONObject().put(
                "tabbedSearchResultsRenderer",
                JSONObject().put(
                    "tabs",
                    JSONArray().put(
                        JSONObject().put(
                            "tabRenderer",
                            JSONObject().put(
                                "content",
                                JSONObject().put(
                                    "sectionListRenderer",
                                    JSONObject().put(
                                        "contents",
                                        JSONArray()
                                            .put(
                                                JSONObject().put(
                                                    "musicCardShelfRenderer",
                                                    JSONObject()
                                                        .put("title", runs("Slayyyter" to browseNav("UC_Slayyyter_01", "MUSIC_PAGE_TYPE_ARTIST")))
                                                        .put("subtitle", runs("Artist • 2.4M monthly listeners" to null))
                                                        .put(
                                                            "thumbnail",
                                                            JSONObject().put(
                                                                "croppedSquareThumbnailRenderer",
                                                                JSONObject().put(
                                                                    "thumbnail",
                                                                    JSONObject().put(
                                                                        "thumbnails",
                                                                        thumbArray("//yt3.googleusercontent.com/slayyyter_card=w120-h120-p-l90-rj?token=abc")
                                                                    )
                                                                )
                                                            )
                                                        )
                                                )
                                            )
                                            .put(
                                                JSONObject().put(
                                                    "musicShelfRenderer",
                                                    JSONObject().put(
                                                        "contents",
                                                        JSONArray().put(
                                                            JSONObject().put(
                                                                "musicResponsiveListItemRenderer",
                                                                JSONObject()
                                                                    .put("navigationEndpoint", browseNav("UC_KimPetras_02", "MUSIC_PAGE_TYPE_ARTIST"))
                                                                    .put(
                                                                        "flexColumns",
                                                                        JSONArray()
                                                                            .put(flexCol(runs("Kim Petras" to null)))
                                                                            .put(flexCol(runs("Artist • 11M monthly listeners" to null)))
                                                                    )
                                                                    .put(
                                                                        "thumbnail",
                                                                        JSONObject().put(
                                                                            "musicThumbnailRenderer",
                                                                            JSONObject().put(
                                                                                "thumbnail",
                                                                                JSONObject().put(
                                                                                    "thumbnails",
                                                                                    thumbArray("https://yt3.googleusercontent.com/kim_petras=s120-c-k-c0x00ffffff-no-rj-mo?sig=xyz")
                                                                                )
                                                                            )
                                                                        )
                                                                    )
                                                            )
                                                        )
                                                    )
                                                )
                                            )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        val songsSearchJson = JSONObject().put(
            "contents",
            JSONObject().put(
                "tabbedSearchResultsRenderer",
                JSONObject().put(
                    "tabs",
                    JSONArray().put(
                        JSONObject().put(
                            "tabRenderer",
                            JSONObject().put(
                                "content",
                                JSONObject().put(
                                    "sectionListRenderer",
                                    JSONObject().put(
                                        "contents",
                                        JSONArray().put(
                                            JSONObject().put(
                                                "musicShelfRenderer",
                                                JSONObject().put(
                                                    "contents",
                                                    JSONArray().put(
                                                        JSONObject().put(
                                                            "musicResponsiveListItemRenderer",
                                                            JSONObject()
                                                                .put(
                                                                    "flexColumns",
                                                                    JSONArray()
                                                                        .put(flexCol(runs("Daddy AF" to watchNav("DaddyAf0001"))))
                                                                        .put(flexCol(runs("Slayyyter" to null, " • " to null, "2:31" to null)))
                                                                )
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        val artistLookupCount = AtomicInteger(0)
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val reqJson = readRequestJson(chain)
                if (url.contains("/youtubei/v1/search")) {
                    when (reqJson.optString("params")) {
                        "EgWKAQIgAWoMEA4QChADEAQQCRAF" -> {
                            artistLookupCount.incrementAndGet()
                            jsonResponse(chain, artistSearchJson)
                        }
                        "EgWKAQIIAWoMEA4QChADEAQQCRAF" -> jsonResponse(chain, songsSearchJson)
                        else -> jsonResponse(chain, JSONObject())
                    }
                } else {
                    jsonResponse(chain, JSONObject(), 404)
                }
            }
            .build()

        val service = YouTubeMusicService(httpClient = client)
        val allResult = service.searchYouTubeMusic("Slayyyter", SearchFilter.ALL)

        assertEquals(2, allResult.artists.size)
        assertEquals("Slayyyter", allResult.artists[0].name)
        assertEquals("ytm_sim_UC_Slayyyter_01", allResult.artists[0].id)
        assertEquals(
            "https://yt3.googleusercontent.com/slayyyter_card=w540-h540-p-l90-rj?token=abc",
            allResult.artists[0].avatarUri
        )
        assertEquals("2.4M monthly listeners", allResult.artists[0].monthlyListeners)

        assertEquals("Kim Petras", allResult.artists[1].name)
        assertEquals(
            "https://yt3.googleusercontent.com/kim_petras=s540-c-k-c0x00ffffff-no-rj-mo?sig=xyz",
            allResult.artists[1].avatarUri
        )

        // Warm lowercase & case-insensitive avatar cache populated directly from search
        assertEquals(
            "https://yt3.googleusercontent.com/slayyyter_card=w540-h540-p-l90-rj?token=abc",
            service.getArtistAvatarFromCache("slayyyter")
        )

        // ARTISTS filter also works and returns resolved avatars
        val artistsOnly = service.searchYouTubeMusic("Slayyyter", SearchFilter.ARTISTS)
        assertEquals(2, artistsOnly.artists.size)
        assertEquals(2, artistLookupCount.get())
        // Cached on repeat call
        service.searchYouTubeMusic("Slayyyter", SearchFilter.ARTISTS)
        assertEquals(2, artistLookupCount.get())
    }

    @Test
    fun concurrentGetArtistAvatarUriDeduplicatesInFlightNetworkRequests() = runTest {
        val searchRequests = AtomicInteger(0)
        val artistCardOnly = JSONObject().put(
            "contents",
            JSONObject().put(
                "musicCardShelfRenderer",
                JSONObject()
                    .put("title", runs("Charli xcx" to browseNav("UC_Charli_01", "MUSIC_PAGE_TYPE_ARTIST")))
                    .put("subtitle", runs("Artist • 30M monthly listeners" to null))
                    .put(
                        "thumbnail",
                        JSONObject().put(
                            "musicThumbnailRenderer",
                            JSONObject().put(
                                "thumbnail",
                                JSONObject().put(
                                    "thumbnails",
                                    thumbArray("https://yt3.googleusercontent.com/charli_avatar=w120-h120-l90-rj")
                                )
                            )
                        )
                    )
            )
        )

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                searchRequests.incrementAndGet()
                Thread.sleep(80)
                jsonResponse(chain, artistCardOnly)
            }
            .build()

        val service = YouTubeMusicService(httpClient = client)
        val results = (1..5).map {
            async { service.getArtistAvatarUri("Charli xcx") }
        }.awaitAll()

        assertTrue(results.all { it == "https://yt3.googleusercontent.com/charli_avatar=w540-h540-l90-rj" })
        assertEquals(1, searchRequests.get())
    }

    // -------------------------------------------------------------------------
    // Objective 4: Similar tracks for progressive queue (/next + fallback)
    // -------------------------------------------------------------------------

    @Test
    fun fetchSimilarTracksForQueuePromotesOmvSeedCallsNextAudioOnlyAndExcludesQueued() = runTest {
        val nextPayloads = mutableListOf<JSONObject>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val reqJson = readRequestJson(chain)
                when {
                    url.contains("music.youtube.com/youtubei/v1/player") -> {
                        val vid = reqJson.optString("videoId")
                        if (vid == "OmvSeed0001") {
                            jsonResponse(
                                chain,
                                JSONObject()
                                    .put("playabilityStatus", JSONObject().put("status", "OK"))
                                    .put(
                                        "videoDetails",
                                        JSONObject()
                                            .put("videoId", "OmvSeed0001")
                                            .put("title", "Starfucker (Official Music Video)")
                                            .put("author", "Slayyyter - Topic")
                                            .put("musicVideoType", "MUSIC_VIDEO_TYPE_OMV")
                                    )
                            )
                        } else {
                            jsonResponse(chain, JSONObject(), 404)
                        }
                    }
                    url.contains("/youtubei/v1/search") -> {
                        jsonResponse(
                            chain,
                            JSONObject().put(
                                "contents",
                                JSONObject().put(
                                    "musicShelfRenderer",
                                    JSONObject().put(
                                        "contents",
                                        JSONArray().put(
                                            JSONObject().put(
                                                "musicResponsiveListItemRenderer",
                                                JSONObject()
                                                    .put(
                                                        "flexColumns",
                                                        JSONArray()
                                                            .put(flexCol(runs("Starfucker" to watchNav("AtvSeed0001"))))
                                                            .put(flexCol(runs("Slayyyter" to null, " • " to null, "3:12" to null)))
                                                    )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    }
                    url.contains("/youtubei/v1/next") -> {
                        nextPayloads.add(reqJson)
                        jsonResponse(
                            chain,
                            JSONObject().put(
                                "contents",
                                JSONObject().put(
                                    "playlistPanelRenderer",
                                    JSONObject().put(
                                        "contents",
                                        JSONArray()
                                            // Seed song again -> must be excluded
                                            .put(
                                                JSONObject().put(
                                                    "playlistPanelVideoRenderer",
                                                    JSONObject()
                                                        .put("videoId", "AtvSeed0001")
                                                        .put("title", runs("Starfucker" to null))
                                                        .put("longBylineText", runs("Slayyyter" to null))
                                                )
                                            )
                                            // Explicitly excluded queued track -> must be skipped
                                            .put(
                                                JSONObject().put(
                                                    "playlistPanelVideoRenderer",
                                                    JSONObject()
                                                        .put("videoId", "QueuedExcl1")
                                                        .put("title", runs("Miss Belladonna" to null))
                                                        .put("longBylineText", runs("Slayyyter" to null))
                                                )
                                            )
                                            // Valid new explicit similar track -> kept with isExplicit=true
                                            .put(
                                                JSONObject().put(
                                                    "playlistPanelVideoRenderer",
                                                    JSONObject()
                                                        .put("videoId", "SimTrack001")
                                                        .put("title", runs("Vroom Vroom" to null))
                                                        .put("longBylineText", runs("Charli xcx" to null))
                                                        .put("lengthText", runs("3:13" to null))
                                                        .put("badges", explicitBadgeArray())
                                                )
                                            )
                                            // Valid clean track -> kept
                                            .put(
                                                JSONObject().put(
                                                    "playlistPanelVideoRenderer",
                                                    JSONObject()
                                                        .put("videoId", "SimTrack002")
                                                        .put("title", runs("360" to null))
                                                        .put("longBylineText", runs("Charli xcx" to null))
                                                        .put("lengthText", runs("2:13" to null))
                                                )
                                            )
                                    )
                                )
                            )
                        )
                    }
                    else -> jsonResponse(chain, JSONObject(), 404)
                }
            }
            .build()

        val service = YouTubeMusicService(httpClient = client)
        val seedTrack = TrackItem(
            id = "ytm_OmvSeed0001",
            title = "Starfucker",
            artist = "Slayyyter",
            album = "STARFUCKER",
            durationMs = 192000L,
            audioUrl = "https://www.youtube.com/watch?v=OmvSeed0001"
        )

        val similar = service.fetchSimilarTracksForQueue(
            seedTracks = listOf(seedTrack),
            excludeTrackIds = setOf("ytm_QueuedExcl1"),
            limit = 10
        )

        assertEquals(1, nextPayloads.size)
        assertTrue(nextPayloads[0].optBoolean("isAudioOnly"))
        assertEquals("AtvSeed0001", nextPayloads[0].optString("videoId"))
        assertEquals("RDAMVMAtvSeed0001", nextPayloads[0].optString("playlistId"))

        assertEquals(2, similar.size)
        assertEquals("ytm_SimTrack001", similar[0].id)
        assertTrue(similar[0].isExplicit)
        assertEquals("ytm_SimTrack002", similar[1].id)
        assertFalse(similar[1].isExplicit)
    }

    @Test
    fun fetchSimilarTracksForQueueFallsBackToSongsSearchWhenNextEmpty() = runTest {
        val searchQueries = mutableListOf<String>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val reqJson = readRequestJson(chain)
                when {
                    url.contains("music.youtube.com/youtubei/v1/player") -> jsonResponse(
                        chain,
                        JSONObject()
                            .put("playabilityStatus", JSONObject().put("status", "OK"))
                            .put(
                                "videoDetails",
                                JSONObject()
                                    .put("videoId", "EmptyNext01")
                                    .put("musicVideoType", "MUSIC_VIDEO_TYPE_ATV")
                            )
                    )
                    url.contains("/youtubei/v1/next") -> jsonResponse(chain, JSONObject())
                    url.contains("/youtubei/v1/search") -> {
                        searchQueries.add(reqJson.optString("query"))
                        jsonResponse(
                            chain,
                            JSONObject().put(
                                "contents",
                                JSONObject().put(
                                    "musicShelfRenderer",
                                    JSONObject().put(
                                        "contents",
                                        JSONArray()
                                            .put(
                                                JSONObject().put(
                                                    "musicResponsiveListItemRenderer",
                                                    JSONObject()
                                                        .put(
                                                            "flexColumns",
                                                            JSONArray()
                                                                .put(flexCol(runs("Same Seed" to watchNav("EmptyNext01"))))
                                                                .put(flexCol(runs("Kim Petras" to null)))
                                                        )
                                                )
                                            )
                                            .put(
                                                JSONObject().put(
                                                    "musicResponsiveListItemRenderer",
                                                    JSONObject()
                                                        .put("badges", explicitBadgeArray())
                                                        .put(
                                                            "flexColumns",
                                                            JSONArray()
                                                                .put(flexCol(runs("Coconuts" to watchNav("SearchSim01"))))
                                                                .put(flexCol(runs("Kim Petras" to null, " • " to null, "Feed The Beast" to null)))
                                                        )
                                                )
                                            )
                                    )
                                )
                            )
                        )
                    }
                    else -> jsonResponse(chain, JSONObject(), 404)
                }
            }
            .build()

        val service = YouTubeMusicService(httpClient = client)
        val seed = TrackItem(
            id = "ytm_EmptyNext01",
            title = "Unholy",
            artist = "Kim Petras",
            album = "Feed The Beast",
            durationMs = 156000L,
            audioUrl = "https://www.youtube.com/watch?v=EmptyNext01"
        )

        val fallbackTracks = service.fetchSimilarTracksForQueue(
            seedTracks = listOf(seed),
            excludeTrackIds = setOf("ytm_EmptyNext01"),
            limit = 5
        )

        assertTrue(searchQueries.isNotEmpty())
        assertEquals(1, fallbackTracks.size)
        assertEquals("ytm_SearchSim01", fallbackTracks[0].id)
        assertTrue(fallbackTracks[0].isExplicit)
    }

    @Test
    fun searchArtistsFallbackEnrichesAllMoreThanSixMissingAvatars() = runTest {
        val artistNames = (1..8).map { "Synth Artist $it" }
        val songsJson = JSONObject().put(
            "contents",
            JSONObject().put(
                "musicShelfRenderer",
                JSONObject().put(
                    "contents",
                    JSONArray().apply {
                        artistNames.forEachIndexed { idx, name ->
                            put(
                                JSONObject().put(
                                    "musicResponsiveListItemRenderer",
                                    JSONObject().put(
                                        "flexColumns",
                                        JSONArray()
                                            .put(flexCol(runs("Track $idx" to watchNav("SongId0000$idx"))))
                                            .put(flexCol(runs(name to null)))
                                    )
                                )
                            )
                        }
                    }
                )
            )
        )

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val reqJson = readRequestJson(chain)
                if (url.contains("/youtubei/v1/search")) {
                    when (reqJson.optString("params")) {
                        "EgWKAQIIAWoMEA4QChADEAQQCRAF" -> jsonResponse(chain, songsJson)
                        "EgWKAQIgAWoMEA4QChADEAQQCRAF" -> {
                            val q = reqJson.optString("query")
                            if (q == "batchquery") {
                                jsonResponse(chain, JSONObject()) // force fallback to song-derived artists
                            } else {
                                val slug = q.lowercase().replace(" ", "_")
                                jsonResponse(
                                    chain,
                                    JSONObject().put(
                                        "contents",
                                        JSONObject().put(
                                            "musicCardShelfRenderer",
                                            JSONObject()
                                                .put("title", runs(q to browseNav("UC_$slug", "MUSIC_PAGE_TYPE_ARTIST")))
                                                .put("subtitle", runs("Artist • 1M monthly listeners" to null))
                                                .put(
                                                    "thumbnail",
                                                    JSONObject().put(
                                                        "musicThumbnailRenderer",
                                                        JSONObject().put(
                                                            "thumbnail",
                                                            JSONObject().put(
                                                                "thumbnails",
                                                                thumbArray("https://yt3.googleusercontent.com/$slug=w120-h120-l90-rj")
                                                            )
                                                        )
                                                    )
                                                )
                                        )
                                    )
                                )
                            }
                        }
                        else -> jsonResponse(chain, JSONObject())
                    }
                } else {
                    jsonResponse(chain, JSONObject(), 404)
                }
            }
            .build()

        val service = YouTubeMusicService(httpClient = client)
        val result = service.searchYouTubeMusic("batchquery", SearchFilter.ARTISTS)

        assertEquals(8, result.artists.size)
        result.artists.forEachIndexed { idx, artist ->
            val slug = "synth_artist_${idx + 1}"
            assertEquals("https://yt3.googleusercontent.com/$slug=w540-h540-l90-rj", artist.avatarUri)
        }
    }

    @Test
    fun playerScriptParserExtractsVersionStsSigFunctionAndInjectsHook() {
        val iframeBody = """
            if (!window['YT']) {var YT = {loading: 0,loaded: 0};}
            var scriptUrl = 'https:\/\/www.youtube.com\/s\/player\/fad7e4e9\/www-widgetapi.vflset\/www-widgetapi.js';
        """.trimIndent()
        assertEquals("fad7e4e9", YouTubePlayerScriptParser.extractPlayerVersionFromIframeApi(iframeBody))
        assertEquals(null, YouTubePlayerScriptParser.extractPlayerVersionFromIframeApi("console.log('no player');"))

        val sampleBaseJs = buildString {
            append("(function(g){var a=1;")
            append("var cfg={signatureTimestamp:20767};")
            append("ko=function(a,b,c){var d=new z9(a,!0);d.set(\"alr\",\"yes\");b&&d.set(\"sp\",b);return d};")
            append("})(_yt_player);")
        }
        assertEquals(20767, YouTubePlayerScriptParser.extractSignatureTimestamp(sampleBaseJs))
        assertEquals("ko", YouTubePlayerScriptParser.extractSigDecipherFunctionName(sampleBaseJs))

        val fallbackSigJs =
            """(function(g){var fn_1=function(a,b,c){b=b===void 0?"":b;c&&a.set("n",c);a.set("alr","yes");return a};})(_yt_player)"""
        assertEquals("fn_1", YouTubePlayerScriptParser.extractSigDecipherFunctionName(fallbackSigJs))
        assertEquals(null, YouTubePlayerScriptParser.extractSigDecipherFunctionName("var x = function(){return 1;};"))

        val hooked = YouTubePlayerScriptParser.buildHookedPlayerScript(sampleBaseJs, "ko")
        assertNotNull(hooked)
        assertTrue(hooked!!.contains(";g.__xt_transform_url=function(u,sp,s){var o=ko(u,sp||\"\",s||\"\");"))
        assertTrue(hooked.endsWith("})(_yt_player);"))
        assertTrue(hooked.indexOf("g.__xt_transform_url") < hooked.lastIndexOf("})(_yt_player);"))

        val hookedNoSemi = YouTubePlayerScriptParser.buildHookedPlayerScript(fallbackSigJs, "fn_1")
        assertNotNull(hookedNoSemi)
        assertTrue(hookedNoSemi!!.contains("var o=fn_1("))
        assertTrue(hookedNoSemi.endsWith("})(_yt_player)"))

        assertEquals(null, YouTubePlayerScriptParser.buildHookedPlayerScript(sampleBaseJs, "bad;fn()"))
        assertEquals(null, YouTubePlayerScriptParser.buildHookedPlayerScript("var noMarker=1;", "ko"))
    }

    @Test
    fun chunked403RetryDownloadsAll512KbChunksInOrder() = runTest {
        val tempAudioDir = Files.createTempDirectory("ytm_chunked_403_audio").toFile()
        val authManager = createSignedInAuthManager()
        val totalSize = 1_200_000
        val expectedBytes = ByteArray(totalSize) { ((it * 31 + 7) and 0xFF).toByte() }
        val requestedRanges = mutableListOf<String>()

        val rawCipherStreamUrl = "https://rr2.googlevideo.com/videoplayback?id=chunk403&itag=140&n=rawN"
        val decipheredStreamUrl = "https://rr2.googlevideo.com/videoplayback?id=chunk403&itag=140&n=solvedN&alr=yes&sig=OK_SIG"
        val fakeResolver = object : PlayerScriptResolver {
            override suspend fun getSignatureTimestamp(): Int = 20767
            override suspend fun transformStreamUrl(url: String, sp: String, signature: String): String =
                if (signature == "ENC_CHUNK" && sp == "sig") decipheredStreamUrl else url
        }

        val cipherFormatJson = JSONObject()
            .put("playabilityStatus", JSONObject().put("status", "OK"))
            .put(
                "videoDetails",
                JSONObject()
                    .put("videoId", "Chunk403Vid")
                    .put("title", "Chunked Track")
                    .put("author", "Test Artist")
                    .put("musicVideoType", "MUSIC_VIDEO_TYPE_ATV")
            )
            .put(
                "streamingData",
                JSONObject().put(
                    "adaptiveFormats",
                    JSONArray().put(
                        JSONObject()
                            .put("itag", 140)
                            .put("mimeType", "audio/mp4; codecs=\"mp4a.40.2\"")
                            .put("bitrate", 131072)
                            .put("contentLength", totalSize.toString())
                            .put(
                                "signatureCipher",
                                "s=ENC_CHUNK&sp=sig&url=" + java.net.URLEncoder.encode(rawCipherStreamUrl, "UTF-8")
                            )
                    )
                )
            )

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val reqJson = readRequestJson(chain)
                when {
                    url.contains("sw.js_data") -> textResponse(
                        chain,
                        ")]}'\n[[\"CgtAnonVisitorChunk0123456789012345\"]]",
                        200,
                        setCookies = listOf("SOCS=CAI_anon; Path=/; Domain=.youtube.com")
                    )
                    url.contains("music.youtube.com/youtubei/v1/player") -> {
                        val clientName = reqJson.optJSONObject("context")?.optJSONObject("client")?.optString("clientName")
                        if (clientName == "WEB_REMIX" && reqJson.has("playbackContext")) {
                            jsonResponse(chain, cipherFormatJson)
                        } else {
                            jsonResponse(chain, playerBlockedJson("LOGIN_REQUIRED", "Sign in required"))
                        }
                    }
                    url.contains("/youtubei/v1/player") -> {
                        jsonResponse(chain, playerBlockedJson("AGE_VERIFICATION_REQUIRED", "Age restricted"))
                    }
                    url.contains("googlevideo.com/videoplayback") -> {
                        val rangeParam = chain.request().url.queryParameter("range").orEmpty()
                        requestedRanges.add(rangeParam)
                        if (rangeParam == "0-${totalSize - 1}") {
                            textResponse(chain, "Forbidden", 403)
                        } else {
                            val parts = rangeParam.split("-")
                            val start = parts.getOrNull(0)?.toIntOrNull()
                            val end = parts.getOrNull(1)?.toIntOrNull()
                            if (start != null && end != null && start in 0..end && end < totalSize) {
                                binaryResponse(chain, expectedBytes.copyOfRange(start, end + 1), 206)
                            } else {
                                textResponse(chain, "Bad range: $rangeParam", 400)
                            }
                        }
                    }
                    else -> jsonResponse(chain, JSONObject(), 404)
                }
            }
            .build()

        try {
            val service = YouTubeMusicService(
                httpClient = client,
                clockMs = { 1_700_000_000_000L },
                authManager = authManager,
                audioCacheDirOverride = tempAudioDir,
                playerScriptResolverOverride = fakeResolver
            )

            val file = service.getOrDownloadAudioFile("ytm_Chunk403Vid", context = null)
            assertNotNull(file)
            assertTrue(file!!.exists())
            assertEquals(totalSize.toLong(), file.length())
            assertTrue(expectedBytes.contentEquals(file.readBytes()))
            assertEquals(
                listOf("0-1199999", "0-511999", "512000-1023999", "1024000-1199999"),
                requestedRanges
            )
        } finally {
            tempAudioDir.deleteRecursively()
        }
    }
}
