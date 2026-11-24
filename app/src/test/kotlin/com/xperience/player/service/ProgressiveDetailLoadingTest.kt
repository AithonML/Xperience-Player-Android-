package com.xperience.player.service

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.xperience.player.domain.model.AlbumDetailProfile
import com.xperience.player.domain.model.AlbumItem
import com.xperience.player.domain.model.ArtistDetailProfile
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

class ProgressiveDetailLoadingTest {

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
        tempCacheDir = Files.createTempDirectory("progressive_detail_test").toFile()
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

    private fun buildJwt(): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"ES256","typ":"JWT","kid":"WebPlayKid"}""".toByteArray())
        val payload = encoder.encodeToString("""{"iss":"AMPWebPlay","iat":1700000000,"exp":4102444800}""".toByteArray())
        val sig = encoder.encodeToString("sig".toByteArray())
        return "$header.$payload.$sig"
    }

    private fun respond(exchange: HttpExchange, code: Int, body: String, type: String = "application/json") {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", type)
        exchange.sendResponseHeaders(code, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    @Test
    fun progressiveArtistDetailEmitsFastAppleAssetsLongBeforeSlowYtmCompletes() = runBlocking {
        val encodedEnv = URLEncoder.encode("""{"MEDIA_API":{"token":"${buildJwt()}"}}""", StandardCharsets.UTF_8.name())
        val appleHits = AtomicInteger(0)
        val ytmBrowseHits = AtomicInteger(0)

        server.createContext("/browse") { ex ->
            respond(ex, 200, """<meta name="desktop-music-app/config/environment" content="$encodedEnv">""", "text/html")
        }
        server.createContext("/v1/catalog/us/search") { ex ->
            appleHits.incrementAndGet()
            respond(ex, 200, """{"results":{"artists":{"data":[{"id":"42","attributes":{"name":"Daft Punk"}}]}}}""")
        }
        server.createContext("/v1/catalog/us/artists/42") { ex ->
            appleHits.incrementAndGet()
            Thread.sleep(20)
            respond(
                ex,
                200,
                """
                {
                  "data": [{
                    "id": "42",
                    "attributes": {
                      "name": "Daft Punk",
                      "editorialArtwork": {
                        "musicContentWhiteLogoTrimmed": {
                          "url": "https://is1-ssl.mzstatic.com/daft_logo.png/{w}x{h}bb.{f}",
                          "bgColor": "112233"
                        }
                      },
                      "editorialVideo": {
                        "motionArtistWide16x9": { "video": "https://mvod.itunes.apple.com/daft_wide.m3u8" }
                      }
                    }
                  }]
                }
                """.trimIndent()
            )
        }
        server.createContext("/ytm/search") { ex ->
            Thread.sleep(150)
            respond(
                ex,
                200,
                """
                {
                  "contents": {
                    "musicCardShelfRenderer": {
                      "title": {
                        "runs": [{
                          "text": "Daft Punk",
                          "navigationEndpoint": { "browseEndpoint": { "browseId": "UC_DAFT_PUNK" } }
                        }]
                      }
                    }
                  }
                }
                """.trimIndent()
            )
        }
        server.createContext("/ytm/browse") { ex ->
            ytmBrowseHits.incrementAndGet()
            Thread.sleep(200)
            respond(
                ex,
                200,
                """
                {
                  "header": {
                    "musicImmersiveHeaderRenderer": {
                      "monthlyListenerCount": { "runs": [{ "text": "21M monthly audience" }] },
                      "description": { "runs": [{ "text": "French electronic music duo." }] }
                    }
                  }
                }
                """.trimIndent()
            )
        }

        val appleService = AppleMusicCanvasService(
            httpClient = testHttpClient,
            ampBaseUrl = "$baseUrl/",
            browseUrl = "$baseUrl/browse",
            echoFeedUrl = "$baseUrl/echo.json",
            viviFeedUrl = "$baseUrl/vivi.json",
            cacheRootDir = tempCacheDir
        )
        val ytmService = YouTubeMusicService(
            httpClient = testHttpClient,
            searchUrl = "$baseUrl/ytm/search",
            browseUrl = "$baseUrl/ytm/browse"
        )

        val emissions = mutableListOf<Pair<Long, ArtistDetailProfile>>()
        val startNs = System.nanoTime()

        buildProgressiveArtistDetailFlow(
            artistName = "Daft Punk",
            fallbackAvatarUri = "https://example.com/avatar.jpg",
            ytmService = ytmService,
            appleMusicCanvasService = appleService
        ).collect { snap ->
            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
            emissions.add(elapsedMs to snap)
        }

        assertEquals(3, emissions.size)

        // 1. Immediate seed: zero blocking placeholder text, loading flags true
        val seed = emissions[0].second
        assertEquals("", seed.biography)
        assertTrue(seed.isHeaderMediaLoading)
        assertTrue(seed.isCatalogLoading)

        // 2. Fast Apple emission arrives long before slow YTM (~350ms total) completes
        val (appleTimeMs, appleSnap) = emissions[1]
        assertFalse(appleSnap.isHeaderMediaLoading)
        assertTrue(appleSnap.isCatalogLoading)
        assertEquals("https://is1-ssl.mzstatic.com/daft_logo.png/800x266bb.png", appleSnap.customTitleLogoUri)
        assertEquals("https://mvod.itunes.apple.com/daft_wide.m3u8", appleSnap.headerVideoUri)
        assertEquals("112233", appleSnap.keyColorRgb)
        assertTrue("Expected Apple emission < 220ms, was ${appleTimeMs}ms", appleTimeMs < 220L)

        // 3. Final emission merges slow YTM bio & clears loading flag
        val finalSnap = emissions[2].second
        assertFalse(finalSnap.isHeaderMediaLoading)
        assertFalse(finalSnap.isCatalogLoading)
        assertEquals("French electronic music duo.", finalSnap.biography)
        assertEquals("21M monthly audience", finalSnap.monthlyListenersText)

        // 4. Subsequent re-open on warm cache emits ONE complete snapshot in < 20ms with ZERO extra HTTP hits
        val appleBeforeWarm = appleHits.get()
        val ytmBeforeWarm = ytmBrowseHits.get()
        val warmStartNs = System.nanoTime()
        val warmList = buildProgressiveArtistDetailFlow(
            artistName = "Daft Punk",
            fallbackAvatarUri = "https://example.com/avatar.jpg",
            ytmService = ytmService,
            appleMusicCanvasService = appleService
        ).toList()
        val warmElapsedMs = (System.nanoTime() - warmStartNs) / 1_000_000L

        assertEquals(1, warmList.size)
        assertFalse(warmList[0].isHeaderMediaLoading)
        assertFalse(warmList[0].isCatalogLoading)
        assertEquals("https://mvod.itunes.apple.com/daft_wide.m3u8", warmList[0].headerVideoUri)
        assertEquals(appleBeforeWarm, appleHits.get())
        assertEquals(ytmBeforeWarm, ytmBrowseHits.get())
        assertTrue("Expected warm reopen < 20ms, took ${warmElapsedMs}ms", warmElapsedMs < 20L)
    }

    @Test
    fun progressiveAlbumDetailEmitsFastCanvasBeforeSlowYtmTracks() = runBlocking {
        val encodedEnv = URLEncoder.encode("""{"MEDIA_API":{"token":"${buildJwt()}"}}""", StandardCharsets.UTF_8.name())

        server.createContext("/browse") { ex ->
            respond(ex, 200, """<meta name="desktop-music-app/config/environment" content="$encodedEnv">""", "text/html")
        }
        server.createContext("/v1/catalog/us/search") { ex ->
            Thread.sleep(20)
            respond(
                ex,
                200,
                """
                {
                  "results": {
                    "songs": {
                      "data": [{
                        "id": "s1",
                        "attributes": {
                          "name": "Random Access Memories",
                          "artistName": "Daft Punk",
                          "albumName": "Random Access Memories",
                          "editorialVideo": {
                            "motionDetailSquare": { "video": "https://cvs.apple.com/ram_canvas.m3u8" }
                          }
                        }
                      }]
                    }
                  }
                }
                """.trimIndent()
            )
        }
        server.createContext("/ytm/browse") { ex ->
            Thread.sleep(220)
            respond(
                ex,
                200,
                """
                {
                  "header": {
                    "musicResponsiveHeaderRenderer": {
                      "title": { "runs": [{ "text": "Random Access Memories" }] },
                      "straplineTextOne": { "runs": [{ "text": "Daft Punk" }] },
                      "subtitle": { "runs": [{ "text": "Album • 2013" }] }
                    }
                  }
                }
                """.trimIndent()
            )
        }

        val appleService = AppleMusicCanvasService(
            httpClient = testHttpClient,
            ampBaseUrl = "$baseUrl/",
            browseUrl = "$baseUrl/browse",
            echoFeedUrl = "$baseUrl/echo.json",
            viviFeedUrl = "$baseUrl/vivi.json",
            cacheRootDir = tempCacheDir
        )
        val ytmService = YouTubeMusicService(
            httpClient = testHttpClient,
            searchUrl = "$baseUrl/ytm/search",
            browseUrl = "$baseUrl/ytm/browse"
        )

        val album = AlbumItem(
            id = "ytm_alb_MPREb_RAM123",
            title = "Random Access Memories",
            artist = "Daft Punk",
            year = 2013
        )
        val snapshots = buildProgressiveAlbumDetailFlow(
            album = album,
            artistName = "Daft Punk",
            ytmService = ytmService,
            appleMusicCanvasService = appleService
        ).toList()

        assertEquals(3, snapshots.size)
        assertTrue(snapshots[0].isCanvasLoading && snapshots[0].isTracksLoading)
        assertFalse(snapshots[1].isCanvasLoading)
        assertTrue(snapshots[1].isTracksLoading)
        assertEquals("https://cvs.apple.com/ram_canvas.m3u8", snapshots[1].canvasVideoUri)
        assertFalse(snapshots[2].isTracksLoading)
        assertEquals(2013, snapshots[2].releaseYear)
    }

    @Test
    fun youtubeMusicServiceCoalescesConcurrentArtistAndAlbumDetailCalls() = runBlocking {
        val searchHits = AtomicInteger(0)
        val browseHits = AtomicInteger(0)

        server.createContext("/ytm/search") { ex ->
            searchHits.incrementAndGet()
            respond(
                ex,
                200,
                """
                {
                  "contents": {
                    "musicCardShelfRenderer": {
                      "title": {
                        "runs": [{
                          "text": "Tame Impala",
                          "navigationEndpoint": { "browseEndpoint": { "browseId": "UC_TAME_IMPALA" } }
                        }]
                      }
                    }
                  }
                }
                """.trimIndent()
            )
        }
        server.createContext("/ytm/browse") { ex ->
            browseHits.incrementAndGet()
            Thread.sleep(80)
            respond(
                ex,
                200,
                """
                {
                  "header": {
                    "musicImmersiveHeaderRenderer": {
                      "monthlyListenerCount": { "runs": [{ "text": "28M" }] }
                    },
                    "musicResponsiveHeaderRenderer": {
                      "title": { "runs": [{ "text": "Currents" }] },
                      "straplineTextOne": { "runs": [{ "text": "Tame Impala" }] },
                      "subtitle": { "runs": [{ "text": "2015" }] }
                    }
                  }
                }
                """.trimIndent()
            )
        }

        val ytmService = YouTubeMusicService(
            httpClient = testHttpClient,
            searchUrl = "$baseUrl/ytm/search",
            browseUrl = "$baseUrl/ytm/browse"
        )

        val profiles = coroutineScope {
            (1..5).map { async { ytmService.getArtistProfile("Tame Impala") } }.awaitAll()
        }
        assertTrue(profiles.all { it?.monthlyListenersText == "28M" })
        assertEquals(1, searchHits.get())
        assertEquals(1, browseHits.get())

        val albums = coroutineScope {
            (1..5).map {
                async { ytmService.getAlbumDetail("ytm_alb_MPREb_CURRENTS", "Currents", "Tame Impala") }
            }.awaitAll()
        }
        assertTrue(albums.all { it?.releaseYear == 2015 })
        assertEquals(2, browseHits.get())
    }
}
