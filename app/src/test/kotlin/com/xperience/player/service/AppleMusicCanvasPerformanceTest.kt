package com.xperience.player.service

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class AppleMusicCanvasPerformanceTest {

    private lateinit var server: HttpServer
    private lateinit var baseUrl: String
    private lateinit var tempCacheDir: File
    private val serverExecutor = Executors.newCachedThreadPool()
    private val testHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    @Before
    fun setUp() {
        tempCacheDir = Files.createTempDirectory("canvas_perf_test").toFile()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = serverExecutor
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}"
    }

    @After
    fun tearDown() {
        server.stop(0)
        serverExecutor.shutdownNow()
        tempCacheDir.deleteRecursively()
    }

    private fun buildSyntheticWebPlayJwt(iss: String = "AMPWebPlay", expEpochSec: Long = 4_102_444_800L): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"ES256","typ":"JWT","kid":"WebPlayKid"}""".toByteArray())
        val payload = encoder.encodeToString("""{"iss":"$iss","iat":1700000000,"exp":$expEpochSec}""".toByteArray())
        val sig = encoder.encodeToString("signature".toByteArray())
        return "$header.$payload.$sig"
    }

    private fun sendText(exchange: HttpExchange, status: Int, body: String, contentType: String = "application/json") {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", contentType)
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun sendBytes(exchange: HttpExchange, status: Int, bytes: ByteArray, contentType: String = "video/mp4") {
        exchange.responseHeaders.set("Content-Type", contentType)
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    @Test
    fun metaEnvironmentTokenScrapedWithoutDownloadingLegacyJsAndUsedOnAmpApi() = runBlocking {
        val jwt = buildSyntheticWebPlayJwt()
        val envJson = """{"MEDIA_API":{"token":"$jwt"}}"""
        val encodedEnv = URLEncoder.encode(envJson, StandardCharsets.UTF_8.name())

        val browseHits = AtomicInteger(0)
        val legacyJsHits = AtomicInteger(0)

        server.createContext("/browse") { ex ->
            browseHits.incrementAndGet()
            val html = """
                <html><head>
                <meta name="desktop-music-app/config/environment" content="$encodedEnv">
                <script src="/assets/index~legacy123.js"></script>
                </head></html>
            """.trimIndent()
            sendText(ex, 200, html, "text/html")
        }
        server.createContext("/assets/index~legacy123.js") { ex ->
            legacyJsHits.incrementAndGet()
            sendText(ex, 200, "console.log('legacy');", "application/javascript")
        }
        server.createContext("/v1/catalog/us/search") { ex ->
            val auth = ex.requestHeaders.getFirst("Authorization")
            assertEquals("Bearer $jwt", auth)
            val body = """
                {
                  "results": {
                    "songs": {
                      "data": [{
                        "id": "song_1",
                        "attributes": {
                          "name": "Blinding Lights",
                          "artistName": "The Weeknd",
                          "albumName": "After Hours",
                          "editorialVideo": {
                            "motionDetailSquare": {
                              "video": "$baseUrl/video/blinding.m3u8",
                              "previewFrame": { "url": "https://is1-ssl.mzstatic.com/preview_{w}x{h}.jpg" }
                            }
                          }
                        }
                      }]
                    }
                  }
                }
            """.trimIndent()
            sendText(ex, 200, body)
        }

        val service = AppleMusicCanvasService(
            httpClient = testHttpClient,
            ampBaseUrl = "$baseUrl/",
            browseUrl = "$baseUrl/browse",
            echoFeedUrl = "$baseUrl/echo.json",
            viviFeedUrl = "$baseUrl/vivi.json",
            cacheRootDir = tempCacheDir
        )

        val result = service.getCanvasArtwork("Blinding Lights", "The Weeknd", "After Hours")
        assertNotNull(result)
        assertEquals("$baseUrl/video/blinding.m3u8", result!!.videoUri)
        assertTrue(result.isHls)
        assertEquals(1, browseHits.get())
        assertEquals(0, legacyJsHits.get())
    }

    @Test
    fun nonLegacyBundlePreferredOverLegacyAnd401RefreshesToken() = runBlocking {
        val initialJwt = buildSyntheticWebPlayJwt(expEpochSec = 4_102_444_800L)
        val refreshedJwt = buildSyntheticWebPlayJwt(iss = "AMPWebPlay", expEpochSec = 4_102_444_999L)
        val browseHits = AtomicInteger(0)
        val legacyHits = AtomicInteger(0)
        val modernHits = AtomicInteger(0)

        server.createContext("/browse") { ex ->
            browseHits.incrementAndGet()
            val html = """
                <html><head>
                <script src="/assets/index~legacy111.js"></script>
                <script src="/assets/index~modern222.js"></script>
                </head></html>
            """.trimIndent()
            sendText(ex, 200, html, "text/html")
        }
        server.createContext("/assets/index~legacy111.js") { ex ->
            legacyHits.incrementAndGet()
            sendText(ex, 200, "var t='$initialJwt';", "application/javascript")
        }
        server.createContext("/assets/index~modern222.js") { ex ->
            modernHits.incrementAndGet()
            val tokenToServe = if (browseHits.get() == 1) initialJwt else refreshedJwt
            sendText(ex, 200, "window.__token='$tokenToServe';", "application/javascript")
        }
        server.createContext("/v1/catalog/us/search") { ex ->
            val auth = ex.requestHeaders.getFirst("Authorization")
            if (auth == "Bearer $initialJwt") {
                sendText(ex, 401, """{"error":"expired"}""")
                return@createContext
            }
            assertEquals("Bearer $refreshedJwt", auth)
            sendText(
                ex,
                200,
                """
                {
                  "results": {
                    "songs": {
                      "data": [{
                        "id": "s2",
                        "attributes": {
                          "name": "Levitating",
                          "artistName": "Dua Lipa",
                          "albumName": "Future Nostalgia",
                          "editorialVideo": {
                            "motionSquareVideo1x1": { "video": "$baseUrl/v/levitating.m3u8" }
                          }
                        }
                      }]
                    }
                  }
                }
                """.trimIndent()
            )
        }

        val service = AppleMusicCanvasService(
            httpClient = testHttpClient,
            ampBaseUrl = "$baseUrl/",
            browseUrl = "$baseUrl/browse",
            echoFeedUrl = "$baseUrl/echo.json",
            viviFeedUrl = "$baseUrl/vivi.json",
            cacheRootDir = tempCacheDir
        )

        val result = service.getCanvasArtwork("Levitating", "Dua Lipa", "Future Nostalgia")
        assertNotNull(result)
        assertEquals("$baseUrl/v/levitating.m3u8", result!!.videoUri)
        assertEquals(0, legacyHits.get())
        assertEquals(2, modernHits.get())
    }

    @Test
    fun concurrentWordmarkAndHeaderVideoCoalesceIntoSinglePipelineAndWarmCacheIsInstant() = runBlocking {
        val jwt = buildSyntheticWebPlayJwt()
        val encodedEnv = URLEncoder.encode("""{"MEDIA_API":{"token":"$jwt"}}""", StandardCharsets.UTF_8.name())
        val artistSearchHits = AtomicInteger(0)
        val artistDetailHits = AtomicInteger(0)

        server.createContext("/browse") { ex ->
            sendText(ex, 200, """<meta name="desktop-music-app/config/environment" content="$encodedEnv">""", "text/html")
        }
        server.createContext("/v1/catalog/us/search") { ex ->
            artistSearchHits.incrementAndGet()
            Thread.sleep(60)
            sendText(
                ex,
                200,
                """
                {
                  "results": {
                    "artists": {
                      "data": [{
                        "id": "159260351",
                        "attributes": { "name": "Taylor Swift" }
                      }]
                    }
                  }
                }
                """.trimIndent()
            )
        }
        server.createContext("/v1/catalog/us/artists/159260351") { ex ->
            artistDetailHits.incrementAndGet()
            Thread.sleep(60)
            sendText(
                ex,
                200,
                """
                {
                  "data": [{
                    "id": "159260351",
                    "attributes": {
                      "name": "Taylor Swift",
                      "editorialArtwork": {
                        "musicContentWhiteLogoTrimmed": {
                          "url": "https://is1-ssl.mzstatic.com/image/thumb/Features/taylor.png/{w}x{h}bb.{f}",
                          "bgColor": "1a2b3c",
                          "textColor1": "ffffff"
                        },
                        "bannerUber": {
                          "url": "https://is1-ssl.mzstatic.com/image/thumb/Features/banner/{w}x{h}{c}.{f}",
                          "width": 3840,
                          "height": 2160,
                          "bgColor": "0e172a"
                        }
                      },
                      "editorialVideo": {
                        "motionArtistFullscreen16x9": {
                          "video": "https://mvod.itunes.apple.com/taylor_header.m3u8"
                        }
                      }
                    }
                  }]
                }
                """.trimIndent()
            )
        }

        val service = AppleMusicCanvasService(
            httpClient = testHttpClient,
            ampBaseUrl = "$baseUrl/",
            browseUrl = "$baseUrl/browse",
            echoFeedUrl = "$baseUrl/echo.json",
            viviFeedUrl = "$baseUrl/vivi.json",
            cacheRootDir = tempCacheDir
        )

        val (wordmark, video, fullAssets) = coroutineScope {
            val w = async { service.getArtistWordmarkLogoUri("Taylor Swift") }
            val v = async { service.getArtistHeaderVideoUri("Taylor Swift") }
            val a = async { service.getArtistAssets("Taylor Swift") }
            Triple(w.await(), v.await(), a.await())
        }

        assertEquals(1, artistSearchHits.get())
        assertEquals(1, artistDetailHits.get())
        assertEquals("https://is1-ssl.mzstatic.com/image/thumb/Features/taylor.png/800x266bb.png", wordmark)
        assertEquals("https://mvod.itunes.apple.com/taylor_header.m3u8", video)
        assertEquals("https://is1-ssl.mzstatic.com/image/thumb/Features/banner/1600x900cc.jpg", fullAssets?.bannerUri)
        assertEquals("0e172a", fullAssets?.bgColorHex)
        assertEquals("ffffff", fullAssets?.keyColorHex)

        // Warm cache peek & subsequent calls make zero network calls and complete quickly
        val startNs = System.nanoTime()
        val peeked = service.peekArtistAssets("Taylor Swift")
        val warmWordmark = service.getArtistWordmarkLogoUri("Taylor Swift")
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0
        assertNotNull(peeked)
        assertEquals(wordmark, warmWordmark)
        assertEquals(1, artistSearchHits.get())
        assertEquals(1, artistDetailHits.get())
        assertTrue("Expected warm cache < 15ms, took ${elapsedMs}ms", elapsedMs < 15.0)
    }

    @Test
    fun liveCanvasReturnsM3u8WithoutBlockingWhileOfflineDownloadResolvesMp4() = runBlocking {
        val jwt = buildSyntheticWebPlayJwt()
        val encodedEnv = URLEncoder.encode("""{"MEDIA_API":{"token":"$jwt"}}""", StandardCharsets.UTF_8.name())
        val masterM3u8Hits = AtomicInteger(0)
        val mp4Hits = AtomicInteger(0)

        server.createContext("/browse") { ex ->
            sendText(ex, 200, """<meta name="desktop-music-app/config/environment" content="$encodedEnv">""", "text/html")
        }
        server.createContext("/v1/catalog/us/search") { ex ->
            sendText(
                ex,
                200,
                """
                {
                  "results": {
                    "songs": {
                      "data": [{
                        "id": "s3",
                        "attributes": {
                          "name": "Midnight Rain",
                          "artistName": "Taylor Swift",
                          "albumName": "Midnights",
                          "editorialVideo": {
                            "motionDetailSquare": {
                              "video": "$baseUrl/hls/master.m3u8"
                            }
                          }
                        }
                      }]
                    }
                  }
                }
                """.trimIndent()
            )
        }
        server.createContext("/hls/master.m3u8") { ex ->
            masterM3u8Hits.incrementAndGet()
            sendText(
                ex,
                200,
                """
                #EXTM3U
                #EXT-X-STREAM-INF:BANDWIDTH=2100000,RESOLUTION=768x768,CODECS="avc1.64001f"
                sub_768.m3u8
                """.trimIndent(),
                "application/vnd.apple.mpegurl"
            )
        }
        server.createContext("/hls/sub_768.m3u8") { ex ->
            sendText(
                ex,
                200,
                """
                #EXTM3U
                #EXT-X-MAP:URI="canvas_avc.mp4"
                """.trimIndent(),
                "application/vnd.apple.mpegurl"
            )
        }
        server.createContext("/hls/canvas_avc.mp4") { ex ->
            mp4Hits.incrementAndGet()
            // > 16KB synthetic MP4 bytes
            sendBytes(ex, 200, ByteArray(24_000) { 0x20 })
        }

        val service = AppleMusicCanvasService(
            httpClient = testHttpClient,
            ampBaseUrl = "$baseUrl/",
            browseUrl = "$baseUrl/browse",
            echoFeedUrl = "$baseUrl/echo.json",
            viviFeedUrl = "$baseUrl/vivi.json",
            cacheRootDir = tempCacheDir
        )

        // Live path returns .m3u8 immediately without touching master.m3u8 or .mp4
        val live = service.getCanvasArtwork("Midnight Rain", "Taylor Swift", "Midnights")
        assertNotNull(live)
        assertEquals("$baseUrl/hls/master.m3u8", live!!.videoUri)
        assertTrue(live.isHls)
        assertEquals(0, masterM3u8Hits.get())
        assertEquals(0, mp4Hits.get())

        // Offline download path resolves HLS -> AVC MP4 and caches locally
        val offline = service.resolveDownloadableCanvasArtwork("Midnight Rain", "Taylor Swift", "Midnights")
        assertNotNull(offline)
        assertFalse(offline!!.isHls)
        assertTrue(File(offline.videoUri).exists())
        assertEquals(1, masterM3u8Hits.get())
        assertEquals(1, mp4Hits.get())
    }

    @Test
    fun parallelAlbumProbesAvoidSequentialWaterfall() = runBlocking {
        val jwt = buildSyntheticWebPlayJwt()
        val encodedEnv = URLEncoder.encode("""{"MEDIA_API":{"token":"$jwt"}}""", StandardCharsets.UTF_8.name())

        server.createContext("/browse") { ex ->
            sendText(ex, 200, """<meta name="desktop-music-app/config/environment" content="$encodedEnv">""", "text/html")
        }
        server.createContext("/v1/catalog/us/search") { ex ->
            sendText(
                ex,
                200,
                """
                {
                  "results": {
                    "songs": {
                      "data": [
                        {"id":"s1","attributes":{"name":"Track A","artistName":"Artist X","albumName":"Parallel Album","collectionId":"101"}},
                        {"id":"s2","attributes":{"name":"Track A","artistName":"Artist X","albumName":"Parallel Album","collectionId":"102"}},
                        {"id":"s3","attributes":{"name":"Track A","artistName":"Artist X","albumName":"Parallel Album","collectionId":"103"}}
                      ]
                    }
                  }
                }
                """.trimIndent()
            )
        }
        server.createContext("/v1/catalog/us/albums/") { ex ->
            Thread.sleep(160)
            if (ex.requestURI.path.endsWith("/103")) {
                sendText(
                    ex,
                    200,
                    """{"data":[{"id":"103","attributes":{"name":"Parallel Album","artistName":"Artist X","editorialVideo":{"motionDetailSquare":{"video":"https://cvs.apple.com/alb3.m3u8"}}}}]}"""
                )
            } else {
                sendText(ex, 200, """{"data":[{"id":"x","attributes":{"name":"Parallel Album","artistName":"Artist X"}}]}""")
            }
        }

        val service = AppleMusicCanvasService(
            httpClient = testHttpClient,
            ampBaseUrl = "$baseUrl/",
            browseUrl = "$baseUrl/browse",
            echoFeedUrl = "$baseUrl/echo.json",
            viviFeedUrl = "$baseUrl/vivi.json",
            cacheRootDir = tempCacheDir
        )

        val startMs = System.nanoTime() / 1_000_000L
        val res = service.getCanvasArtwork("Track A", "Artist X", "Parallel Album")
        val elapsedMs = (System.nanoTime() / 1_000_000L) - startMs

        assertNotNull(res)
        assertEquals("https://cvs.apple.com/alb3.m3u8", res!!.videoUri)
        // 3 sequential probes x 160ms would be >= 480ms; parallel should finish well under 430ms
        assertTrue("Expected parallel album probes < 430ms, took ${elapsedMs}ms", elapsedMs < 430L)
    }

    @Test
    fun transientErrorDoesNotPoisonCacheAndNegativeTtlExpiresCleanlyAndDiskPersists() = runBlocking {
        val jwt = buildSyntheticWebPlayJwt()
        val encodedEnv = URLEncoder.encode("""{"MEDIA_API":{"token":"$jwt"}}""", StandardCharsets.UTF_8.name())
        val searchHits = AtomicInteger(0)
        val transientHits = AtomicInteger(0)
        val emptyHits = AtomicInteger(0)
        val artistDetailHits = AtomicInteger(0)
        val fakeClock = AtomicLong(1_750_000_000_000L)

        server.createContext("/browse") { ex ->
            sendText(ex, 200, """<meta name="desktop-music-app/config/environment" content="$encodedEnv">""", "text/html")
        }
        server.createContext("/v1/catalog/us/search") { ex ->
            searchHits.incrementAndGet()
            val query = ex.requestURI.query ?: ""
            if (query.contains("TransientArtist")) {
                if (transientHits.incrementAndGet() == 1) {
                    sendText(ex, 503, """{"error":"busy"}""")
                } else {
                    sendText(ex, 200, """{"results":{"artists":{"data":[{"id":"777","attributes":{"name":"TransientArtist"}}]}}}""")
                }
                return@createContext
            }
            if (query.contains("EmptyArtist")) {
                if (emptyHits.incrementAndGet() == 1) {
                    sendText(ex, 200, """{"results":{"artists":{"data":[]}}}""")
                } else {
                    sendText(ex, 200, """{"results":{"artists":{"data":[{"id":"888","attributes":{"name":"EmptyArtist"}}]}}}""")
                }
                return@createContext
            }
            sendText(ex, 200, """{"results":{}}""")
        }
        server.createContext("/v1/catalog/gb/search") { ex ->
            sendText(ex, 200, """{"results":{"artists":{"data":[]}}}""")
        }
        server.createContext("/v1/catalog/us/artists/777") { ex ->
            artistDetailHits.incrementAndGet()
            sendText(
                ex,
                200,
                """{"data":[{"id":"777","attributes":{"name":"TransientArtist","editorialArtwork":{"artistLogo":{"url":"https://is1-ssl.mzstatic.com/t.png/{w}x{h}bb.{f}"}}}}]}"""
            )
        }
        server.createContext("/v1/catalog/us/artists/888") { ex ->
            sendText(
                ex,
                200,
                """{"data":[{"id":"888","attributes":{"name":"EmptyArtist","editorialArtwork":{"artistLogo":{"url":"https://is1-ssl.mzstatic.com/e.png/{w}x{h}bb.{f}"}}}}]}"""
            )
        }

        val service = AppleMusicCanvasService(
            httpClient = testHttpClient,
            ampBaseUrl = "$baseUrl/",
            browseUrl = "$baseUrl/browse",
            echoFeedUrl = "$baseUrl/echo.json",
            viviFeedUrl = "$baseUrl/vivi.json",
            cacheRootDir = tempCacheDir,
            clockMs = { fakeClock.get() }
        )

        // 1. Transient 503 failure must NOT cache a negative entry
        assertNull(service.getArtistAssets("TransientArtist"))
        val recovered = service.getArtistAssets("TransientArtist")
        assertNotNull(recovered)
        assertEquals("https://is1-ssl.mzstatic.com/t.png/800x266bb.png", recovered!!.wordmarkUri)

        // 2. Confirmed miss is cached for 24h without permanent "NONE" poisoning
        assertNull(service.getArtistAssets("EmptyArtist"))
        val hitsAfterMiss = searchHits.get()
        assertNull(service.getArtistAssets("EmptyArtist"))
        assertEquals(hitsAfterMiss, searchHits.get())

        // Advance clock past 24h miss TTL -> recovers cleanly when artist gains assets
        fakeClock.addAndGet(25L * 60L * 60L * 1000L)
        val afterExpiry = service.getArtistAssets("EmptyArtist")
        assertNotNull(afterExpiry)
        assertEquals("https://is1-ssl.mzstatic.com/e.png/800x266bb.png", afterExpiry!!.wordmarkUri)

        // Allow debounced disk persist to flush
        kotlinx.coroutines.delay(500)

        // 3. New service instance pointing at the same cache directory serves warm cache with 0 HTTP hits
        val hitsBeforeRestart = artistDetailHits.get()
        val restartedService = AppleMusicCanvasService(
            httpClient = testHttpClient,
            ampBaseUrl = "$baseUrl/",
            browseUrl = "$baseUrl/browse",
            echoFeedUrl = "$baseUrl/echo.json",
            viviFeedUrl = "$baseUrl/vivi.json",
            cacheRootDir = tempCacheDir,
            clockMs = { fakeClock.get() }
        )
        val reloaded = restartedService.getArtistAssets("TransientArtist")
        assertNotNull(reloaded)
        assertEquals("https://is1-ssl.mzstatic.com/t.png/800x266bb.png", reloaded!!.wordmarkUri)
        assertEquals(hitsBeforeRestart, artistDetailHits.get())
    }

    @Test
    fun artistMatchingAvoidsStopWordFalsePositivesAndNormalizesDiacritics() {
        val service = AppleMusicCanvasService(
            httpClient = testHttpClient,
            ampBaseUrl = "$baseUrl/",
            browseUrl = "$baseUrl/browse",
            echoFeedUrl = "$baseUrl/echo.json",
            viviFeedUrl = "$baseUrl/vivi.json",
            cacheRootDir = tempCacheDir
        )

        assertFalse(service.artistMatches("The Beatles", "The Weeknd"))
        assertTrue(service.artistMatches("Drake & 21 Savage", "Drake"))
        assertTrue(service.artistMatches("Beyoncé", "Beyonce"))
    }

    @Test
    fun strictAlbumOnlyLookupNeverQueriesSongFallbackFeeds() = runBlocking {
        val echoHits = AtomicInteger(0)
        val viviHits = AtomicInteger(0)

        server.createContext("/browse") { ex -> sendText(ex, 404, "no") }
        server.createContext("/v1/catalog/") { ex -> sendText(ex, 200, """{"results":{}}""") }
        server.createContext("/echo.json") { ex ->
            echoHits.incrementAndGet()
            sendText(ex, 200, """[{"song":"Track 1500","artist":"Artist 1500","videoUrl":"https://cdn.example.com/1500.mp4"}]""")
        }
        server.createContext("/vivi.json") { ex ->
            viviHits.incrementAndGet()
            sendText(ex, 200, """[{"song":"Track 1501","artist":"Artist 1501","videoUrl":"https://cdn.example.com/1501.mp4"}]""")
        }

        val service = AppleMusicCanvasService(
            httpClient = testHttpClient,
            ampBaseUrl = "$baseUrl/",
            browseUrl = "$baseUrl/browse",
            echoFeedUrl = "$baseUrl/echo.json",
            viviFeedUrl = "$baseUrl/vivi.json",
            cacheRootDir = tempCacheDir
        )

        val nullAlbumResults = coroutineScope {
            (0 until 4).map { idx ->
                async { service.getCanvasArtwork("Track ${1500 + idx}", "Artist ${1500 + idx}", null) }
            }.awaitAll()
        }
        val missingCanvasAlbumResult = service.getCanvasArtwork("Track 1500", "Artist 1500", "No Canvas Album")

        assertEquals(0, echoHits.get())
        assertEquals(0, viviHits.get())
        assertTrue(nullAlbumResults.all { it == null })
        assertNull(missingCanvasAlbumResult)
    }

    @Test
    fun heroBannerPreservesWideDimensionsAndFallsBackAppropriately() = runBlocking {
        val jwt = buildSyntheticWebPlayJwt()
        val encodedEnv = URLEncoder.encode("""{"MEDIA_API":{"token":"$jwt"}}""", StandardCharsets.UTF_8.name())

        server.createContext("/browse") { ex ->
            sendText(ex, 200, """<meta name="desktop-music-app/config/environment" content="$encodedEnv">""", "text/html")
        }
        server.createContext("/v1/catalog/us/search") { ex ->
            val q = ex.requestURI.query ?: ""
            val (id, name) = when {
                q.contains("WideArtist") -> "901" to "WideArtist"
                q.contains("FallbackWide") -> "902" to "FallbackWide"
                else -> "903" to "SquareAvatar"
            }
            sendText(ex, 200, """{"results":{"artists":{"data":[{"id":"$id","attributes":{"name":"$name"}}]}}}""")
        }
        server.createContext("/v1/catalog/us/artists/901") { ex ->
            sendText(
                ex,
                200,
                """{"data":[{"id":"901","attributes":{"name":"WideArtist","editorialArtwork":{"bannerUber":{"url":"https://is1-ssl.mzstatic.com/w/{w}x{h}{c}.{f}","width":4320,"height":1080}}}}]}"""
            )
        }
        server.createContext("/v1/catalog/us/artists/902") { ex ->
            sendText(
                ex,
                200,
                """{"data":[{"id":"902","attributes":{"name":"FallbackWide","editorialArtwork":{"artistBackground":{"url":"https://is1-ssl.mzstatic.com/fw/{w}x{h}{c}.{f}"}}}}]}"""
            )
        }
        server.createContext("/v1/catalog/us/artists/903") { ex ->
            sendText(
                ex,
                200,
                """{"data":[{"id":"903","attributes":{"name":"SquareAvatar","artwork":{"url":"https://is1-ssl.mzstatic.com/sq/{w}x{h}{c}.{f}"}}}]}"""
            )
        }

        val service = AppleMusicCanvasService(
            httpClient = testHttpClient,
            ampBaseUrl = "$baseUrl/",
            browseUrl = "$baseUrl/browse",
            echoFeedUrl = "$baseUrl/echo.json",
            viviFeedUrl = "$baseUrl/vivi.json",
            cacheRootDir = tempCacheDir
        )

        assertEquals(
            "https://is1-ssl.mzstatic.com/w/1600x400cc.jpg",
            service.getArtistAssets("WideArtist")?.bannerUri
        )
        assertEquals(
            "https://is1-ssl.mzstatic.com/fw/1600x900cc.jpg",
            service.getArtistAssets("FallbackWide")?.bannerUri
        )
        assertEquals(
            "https://is1-ssl.mzstatic.com/sq/1200x1200cc.jpg",
            service.getArtistAssets("SquareAvatar")?.bannerUri
        )
    }
}
