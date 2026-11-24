package com.xperience.player.service

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.xperience.player.domain.model.AlbumItem
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.domain.model.canvas.CanvasArtworkResult
import com.xperience.player.ui.home.TrackCanvasReuseCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class AlbumCanvasReuseTest {

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
        tempCacheDir = Files.createTempDirectory("album_canvas_reuse_test").toFile()
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

    private fun newCanvasService(): AppleMusicCanvasService {
        return AppleMusicCanvasService(
            httpClient = testHttpClient,
            ampBaseUrl = "$baseUrl/",
            browseUrl = "$baseUrl/browse",
            echoFeedUrl = "$baseUrl/echo.json",
            viviFeedUrl = "$baseUrl/vivi.json",
            cacheRootDir = tempCacheDir
        )
    }

    private fun setupBrowseEndpoint() {
        val encodedEnv = URLEncoder.encode("""{"MEDIA_API":{"token":"${buildJwt()}"}}""", StandardCharsets.UTF_8.name())
        server.createContext("/browse") { ex ->
            respond(ex, 200, """<meta name="desktop-music-app/config/environment" content="$encodedEnv">""", "text/html")
        }
    }

    @Test
    fun sameAlbumTracksReuseAlbumCanvasWithZeroDuplicateHttpCalls() = runBlocking {
        setupBrowseEndpoint()
        val searchHits = AtomicInteger(0)

        server.createContext("/v1/catalog/us/search") { ex ->
            searchHits.incrementAndGet()
            respond(
                ex,
                200,
                """
                {
                  "results": {
                    "albums": {
                      "data": [{
                        "id": "alb_after_hours",
                        "attributes": {
                          "name": "After Hours",
                          "artistName": "The Weeknd",
                          "editorialVideo": {
                            "motionDetailSquare": {
                              "video": "$baseUrl/canvas/after_hours.m3u8",
                              "previewFrame": { "url": "https://cdn.example.com/after_hours_{w}x{h}.jpg" }
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
        server.createContext("/v1/catalog/gb/search") { ex ->
            searchHits.incrementAndGet()
            respond(ex, 200, """{"results":{}}""")
        }

        val service = newCanvasService()

        val firstTrack = service.getCanvasArtwork("Blinding Lights", "The Weeknd", "After Hours")
        assertNotNull(firstTrack)
        assertEquals("$baseUrl/canvas/after_hours.m3u8", firstTrack!!.videoUri)
        assertEquals("alb_after_hours", firstTrack.albumId)
        assertEquals(1, searchHits.get())

        // Subsequent track on the same album with featured artist hits synchronously via peek
        val peekedSecond = service.peekCanvasArtwork(
            title = "Save Your Tears",
            artist = "The Weeknd feat. Ariana Grande",
            album = "After Hours"
        )
        assertNotNull(peekedSecond)
        assertEquals(firstTrack.videoUri, peekedSecond!!.videoUri)
        assertEquals(1, searchHits.get())

        val thirdTrack = service.getCanvasArtwork("In Your Eyes", "The Weeknd", "After Hours")
        assertNotNull(thirdTrack)
        assertEquals(firstTrack.videoUri, thirdTrack!!.videoUri)
        assertNotNull(service.peekAlbumCanvasArtwork("After Hours", "The Weeknd"))
        assertEquals(1, searchHits.get())
    }

    @Test
    fun albumScreenAndNowPlayingShareAlbumCanvasBidirectionallyWithZeroDuplicateHttp() = runBlocking {
        setupBrowseEndpoint()
        val ampSearchHits = AtomicInteger(0)
        val ampAlbumHits = AtomicInteger(0)

        server.createContext("/v1/catalog/us/search") { ex ->
            ampSearchHits.incrementAndGet()
            val query = URLDecoder.decode(ex.requestURI.query ?: "", StandardCharsets.UTF_8.name())
            when {
                query.contains("Random Access Memories", ignoreCase = true) -> {
                    respond(
                        ex,
                        200,
                        """
                        {
                          "results": {
                            "albums": {
                              "data": [{
                                "id": "alb_ram",
                                "attributes": {
                                  "name": "Random Access Memories",
                                  "artistName": "Daft Punk",
                                  "editorialVideo": {
                                    "motionDetailSquare": {
                                      "video": "$baseUrl/canvas/ram.m3u8"
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
                query.contains("Dawn FM", ignoreCase = true) -> {
                    respond(
                        ex,
                        200,
                        """
                        {
                          "results": {
                            "songs": {
                              "data": [{
                                "id": "song_sac",
                                "attributes": {
                                  "name": "Sacrifice",
                                  "artistName": "The Weeknd",
                                  "albumName": "Dawn FM"
                                },
                                "relationships": {
                                  "albums": { "data": [{ "id": "alb_dawn_fm" }] }
                                }
                              }]
                            }
                          }
                        }
                        """.trimIndent()
                    )
                }
                else -> respond(ex, 200, """{"results":{}}""")
            }
        }
        server.createContext("/v1/catalog/us/albums/") { ex ->
            ampAlbumHits.incrementAndGet()
            if (ex.requestURI.path.endsWith("/alb_dawn_fm")) {
                respond(
                    ex,
                    200,
                    """
                    {
                      "data": [{
                        "id": "alb_dawn_fm",
                        "attributes": {
                          "name": "Dawn FM",
                          "artistName": "The Weeknd",
                          "editorialVideo": {
                            "motionDetailSquare": {
                              "video": "$baseUrl/canvas/dawn_fm.m3u8"
                            }
                          }
                        }
                      }]
                    }
                    """.trimIndent()
                )
            } else {
                respond(ex, 404, "{}")
            }
        }
        server.createContext("/ytm/browse") { ex ->
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

        val appleService = newCanvasService()
        val ytmService = YouTubeMusicService(
            httpClient = testHttpClient,
            searchUrl = "$baseUrl/ytm/search",
            browseUrl = "$baseUrl/ytm/browse"
        )

        // Direction 1: AlbumScreen loads Random Access Memories -> NowPlaying plays tracks from it
        val ramAlbum = AlbumItem(
            id = "ytm_ram",
            title = "Random Access Memories",
            artist = "Daft Punk",
            year = 2013
        )
        val snapshots = buildProgressiveAlbumDetailFlow(
            album = ramAlbum,
            artistName = "Daft Punk",
            ytmService = ytmService,
            appleMusicCanvasService = appleService
        ).toList()

        assertTrue(snapshots.last().canvasVideoUri == "$baseUrl/canvas/ram.m3u8")
        assertEquals(1, ampSearchHits.get())

        val peekedTrack = appleService.peekCanvasArtwork(
            title = "Instant Crush",
            artist = "Daft Punk feat. Julian Casablancas",
            album = "Random Access Memories"
        )
        val fetchedTrack = appleService.getCanvasArtwork(
            title = "Get Lucky",
            artist = "Daft Punk",
            album = "Random Access Memories"
        )
        assertEquals("$baseUrl/canvas/ram.m3u8", peekedTrack?.videoUri)
        assertEquals("$baseUrl/canvas/ram.m3u8", fetchedTrack?.videoUri)
        assertEquals(1, ampSearchHits.get())

        // Direction 2: Null/invalid album never falls back to song search; valid album populates cache for AlbumScreen
        assertNull(appleService.getCanvasArtwork("Sacrifice", "The Weeknd", null))
        assertEquals(1, ampSearchHits.get())

        val sacrifice = appleService.getCanvasArtwork("Sacrifice", "The Weeknd", "Dawn FM")
        assertEquals("$baseUrl/canvas/dawn_fm.m3u8", sacrifice?.videoUri)
        assertEquals(2, ampSearchHits.get())
        assertEquals(1, ampAlbumHits.get())

        val albumScreenPeek = appleService.peekAlbumCanvasArtwork("Dawn FM", "The Weeknd")
        val albumScreenFetch = appleService.getAlbumCanvasArtwork("Dawn FM", "The Weeknd")
        val secondDawnTrack = appleService.getCanvasArtwork("Out of Time", "The Weeknd", "Dawn FM")
        assertEquals("$baseUrl/canvas/dawn_fm.m3u8", albumScreenPeek?.videoUri)
        assertEquals("$baseUrl/canvas/dawn_fm.m3u8", albumScreenFetch?.videoUri)
        assertEquals("$baseUrl/canvas/dawn_fm.m3u8", secondDawnTrack?.videoUri)
        assertEquals(2, ampSearchHits.get())
        assertEquals(1, ampAlbumHits.get())
    }

    @Test
    fun inFlightAlbumCanvasCoalescesConcurrentCallersAndPersistsToDisk() = runBlocking {
        setupBrowseEndpoint()
        val searchHits = AtomicInteger(0)

        server.createContext("/v1/catalog/us/search") { ex ->
            searchHits.incrementAndGet()
            Thread.sleep(60)
            respond(
                ex,
                200,
                """
                {
                  "results": {
                    "albums": {
                      "data": [{
                        "id": "alb_midnights",
                        "attributes": {
                          "name": "Midnights",
                          "artistName": "Taylor Swift",
                          "editorialVideo": {
                            "motionDetailSquare": { "video": "$baseUrl/canvas/midnights.m3u8" }
                          }
                        }
                      }]
                    }
                  }
                }
                """.trimIndent()
            )
        }

        val service = newCanvasService()
        val titles = listOf("Lavender Haze", "Maroon", "Anti-Hero", "Midnight Rain", "Karma")
        val concurrentResults = coroutineScope {
            titles.map { trackTitle ->
                async {
                    service.getCanvasArtwork(trackTitle, "Taylor Swift", "Midnights")
                }
            }.awaitAll()
        }

        assertEquals(1, searchHits.get())
        assertTrue(concurrentResults.all { it?.videoUri == "$baseUrl/canvas/midnights.m3u8" })

        // Allow debounced disk persist to flush album_canvas_entries.json
        delay(500)

        val restartedService = newCanvasService()
        val peekFromDisk = restartedService.peekAlbumCanvasArtwork("Midnights", "Taylor Swift")
        val trackFromDisk = restartedService.getCanvasArtwork("Mastermind", "Taylor Swift", "Midnights")
        assertNotNull(peekFromDisk)
        assertEquals("$baseUrl/canvas/midnights.m3u8", peekFromDisk!!.videoUri)
        assertEquals("$baseUrl/canvas/midnights.m3u8", trackFromDisk?.videoUri)
        assertEquals(1, searchHits.get())
    }

    @Test
    fun invalidAlbumNamesReturnNullWithoutSongTitleOrFallbackLookup() = runBlocking {
        setupBrowseEndpoint()
        val searchQueries = CopyOnWriteArrayList<String>()
        val fallbackHits = AtomicInteger(0)

        server.createContext("/v1/catalog/us/search") { ex ->
            val q = URLDecoder.decode(ex.requestURI.query ?: "", StandardCharsets.UTF_8.name())
            searchQueries.add(q)
            respond(
                ex,
                200,
                """
                {
                  "results": {
                    "songs": {
                      "data": [{
                        "id": "song_echoes",
                        "attributes": {
                          "name": "Echoes",
                          "artistName": "Indie Band",
                          "albumName": "Unknown Album",
                          "editorialVideo": {
                            "motionDetailSquare": { "video": "$baseUrl/canvas/echoes_track.m3u8" }
                          }
                        }
                      }]
                    }
                  }
                }
                """.trimIndent()
            )
        }
        server.createContext("/v1/catalog/gb/search") { ex -> respond(ex, 200, """{"results":{}}""") }
        server.createContext("/echo.json") { ex ->
            fallbackHits.incrementAndGet()
            respond(
                ex,
                200,
                """[{"song":"Echoes","artist":"Indie Band","videoUrl":"https://cdn.example.com/echoes.mp4"}]"""
            )
        }
        server.createContext("/vivi.json") { ex ->
            fallbackHits.incrementAndGet()
            respond(ex, 200, "[]")
        }

        val service = newCanvasService()

        val invalidAlbums = listOf(
            "", "   ", "Unknown Album", "YouTube Music", "Single", "<unknown>",
            "Favorites", "Downloaded", "Local", "71K views", "138K plays", "3:26"
        )
        invalidAlbums.forEach { badAlbum ->
            assertFalse("Expected invalid album: '$badAlbum'", service.isValidAlbumName(badAlbum))
            assertNull(service.buildAlbumCacheKey(badAlbum, "Indie Band"))
            assertNull(service.peekCanvasArtwork("Echoes", "Indie Band", badAlbum))
            assertNull(service.getAlbumCanvasArtwork(badAlbum, "Indie Band"))
            assertNull(service.getCanvasArtwork("Echoes", "Indie Band", badAlbum))
        }
        assertEquals(0, searchQueries.size)
        assertEquals(0, fallbackHits.get())
        assertNull(service.peekAlbumCanvasArtwork("Unknown Album", "Indie Band"))
    }

    @Test
    fun homeViewModelReuseCoordinatorKeepsSameAlbumCanvasWithoutNullFlicker() = runBlocking {
        setupBrowseEndpoint()
        val searchHits = AtomicInteger(0)

        server.createContext("/v1/catalog/us/search") { ex ->
            searchHits.incrementAndGet()
            val q = URLDecoder.decode(ex.requestURI.query ?: "", StandardCharsets.UTF_8.name())
            when {
                q.contains("After Hours", ignoreCase = true) -> respond(
                    ex,
                    200,
                    """
                    {
                      "results": {
                        "albums": {
                          "data": [{
                            "id": "alb_after_hours",
                            "attributes": {
                              "name": "After Hours",
                              "artistName": "The Weeknd",
                              "editorialVideo": { "motionDetailSquare": { "video": "$baseUrl/canvas/ah.m3u8" } }
                            }
                          }]
                        }
                      }
                    }
                    """.trimIndent()
                )
                q.contains("Starboy", ignoreCase = true) -> respond(
                    ex,
                    200,
                    """
                    {
                      "results": {
                        "albums": {
                          "data": [{
                            "id": "alb_starboy",
                            "attributes": {
                              "name": "Starboy",
                              "artistName": "The Weeknd",
                              "editorialVideo": { "motionDetailSquare": { "video": "$baseUrl/canvas/sb.m3u8" } }
                            }
                          }]
                        }
                      }
                    }
                    """.trimIndent()
                )
                else -> respond(ex, 200, """{"results":{}}""")
            }
        }
        server.createContext("/v1/catalog/gb/search") { ex -> respond(ex, 200, """{"results":{}}""") }
        server.createContext("/echo.json") { ex ->
            respond(
                ex,
                200,
                """[{"song":"Track Only Video","artist":"Local Artist","videoUrl":"https://cdn.example.com/track_only.mp4"}]"""
            )
        }
        server.createContext("/vivi.json") { ex -> respond(ex, 200, "[]") }

        val service = newCanvasService()
        val targetFlow = MutableStateFlow<CanvasArtworkResult?>(null)
        val emitted = CopyOnWriteArrayList<CanvasArtworkResult?>()
        val collectorJob = launch(Dispatchers.Unconfined) {
            targetFlow.collect { emitted.add(it) }
        }
        val coordinatorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val coordinator = TrackCanvasReuseCoordinator(
                scope = coordinatorScope,
                canvasService = service,
                targetFlow = targetFlow
            )

            // 1. Track 1 on After Hours resolves Album Canvas
            coordinator.fetchForTrack(
                TrackItem(id = "t1", title = "Blinding Lights", artist = "The Weeknd", album = "After Hours")
            )
            withTimeout(3000) {
                while (targetFlow.value == null) delay(15)
            }
            val firstCanvas = targetFlow.value!!
            assertEquals("$baseUrl/canvas/ah.m3u8", firstCanvas.videoUri)
            val emissionsAfterTrack1 = emitted.size

            // 2. Track 2 on same album (with feat. artist credit) reuses with ZERO null emit & ZERO HTTP calls
            coordinator.fetchForTrack(
                TrackItem(
                    id = "t2",
                    title = "Save Your Tears",
                    artist = "The Weeknd feat. Ariana Grande",
                    album = "After Hours"
                )
            )
            delay(50)
            assertEquals(firstCanvas, targetFlow.value)
            assertEquals(emissionsAfterTrack1, emitted.size)
            assertEquals(1, searchHits.get())

            // 3. Track 3 on a DIFFERENT album resets cleanly and resolves Starboy
            coordinator.fetchForTrack(
                TrackItem(id = "t3", title = "I Feel It Coming", artist = "The Weeknd feat. Daft Punk", album = "Starboy")
            )
            withTimeout(3000) {
                while (targetFlow.value?.videoUri != "$baseUrl/canvas/sb.m3u8") delay(15)
            }
            // Should have cleared previous album before emitting Starboy
            assertEquals(null, emitted[emissionsAfterTrack1])
            assertEquals("$baseUrl/canvas/sb.m3u8", targetFlow.value?.videoUri)

            // 4. Track 4 on valid album with NO album-level canvas must stay null even if a song-only
            //    EchoMusic video exists, and must never carry stale canvas to invalid-album tracks
            coordinator.fetchForTrack(
                TrackItem(id = "t4", title = "Track Only Video", artist = "Local Artist", album = "Indie LP")
            )
            withTimeout(3000) {
                while (targetFlow.value != null) delay(15)
            }
            delay(100)
            assertNull(targetFlow.value)

            coordinator.fetchForTrack(
                TrackItem(id = "t5", title = "Different Indie Track", artist = "Local Artist", album = "Indie LP")
            )
            delay(80)
            assertNull(targetFlow.value)
        } finally {
            collectorJob.cancel()
            coordinatorScope.cancel()
        }
    }

    @Test
    fun spiteChristineDoesNotMatchGirliePopEncore() = runBlocking {
        setupBrowseEndpoint()
        val queries = CopyOnWriteArrayList<String>()

        server.createContext("/v1/catalog/us/search") { ex ->
            val q = URLDecoder.decode(ex.requestURI.query ?: "", StandardCharsets.UTF_8.name())
            queries.add(q)
            when {
                q.contains("Girlie Pop: Encore", ignoreCase = true) -> respond(
                    ex,
                    200,
                    """
                    {
                      "results": {
                        "albums": {
                          "data": [{
                            "id": "1761555512",
                            "attributes": {
                              "name": "Girlie Pop: Encore",
                              "artistName": "Chrissy Chlapecka",
                              "editorialVideo": {
                                "motionDetailSquare": { "video": "$baseUrl/canvas/girlie_pop_encore.m3u8" }
                              }
                            }
                          }]
                        }
                      }
                    }
                    """.trimIndent()
                )
                else -> respond(
                    ex,
                    200,
                    """
                    {
                      "results": {
                        "albums": {
                          "data": [
                            {
                              "id": "1720000001",
                              "attributes": {
                                "name": "Christine - EP",
                                "artistName": "Chrissy Chlapecka"
                              }
                            },
                            {
                              "id": "1761555512",
                              "attributes": {
                                "name": "Girlie Pop: Encore",
                                "artistName": "Chrissy Chlapecka",
                                "editorialVideo": {
                                  "motionDetailSquare": { "video": "$baseUrl/canvas/girlie_pop_encore.m3u8" }
                                }
                              }
                            }
                          ]
                        },
                        "songs": {
                          "data": [
                            {
                              "id": "s_spite_christine",
                              "attributes": {
                                "name": "Spite",
                                "artistName": "Chrissy Chlapecka",
                                "albumName": "Christine - EP"
                              },
                              "relationships": {
                                "albums": { "data": [{ "id": "1720000001" }] }
                              }
                            },
                            {
                              "id": "s_spite_encore",
                              "attributes": {
                                "name": "Spite",
                                "artistName": "Chrissy Chlapecka",
                                "albumName": "Girlie Pop: Encore"
                              },
                              "relationships": {
                                "albums": { "data": [{ "id": "1761555512" }] }
                              }
                            }
                          ]
                        }
                      }
                    }
                    """.trimIndent()
                )
            }
        }
        server.createContext("/v1/catalog/gb/search") { ex -> respond(ex, 200, """{"results":{}}""") }
        server.createContext("/v1/catalog/us/albums/") { ex ->
            if (ex.requestURI.path.endsWith("/1761555512")) {
                respond(
                    ex,
                    200,
                    """
                    {
                      "data": [{
                        "id": "1761555512",
                        "attributes": {
                          "name": "Girlie Pop: Encore",
                          "artistName": "Chrissy Chlapecka",
                          "editorialVideo": {
                            "motionDetailSquare": { "video": "$baseUrl/canvas/girlie_pop_encore.m3u8" }
                          }
                        }
                      }]
                    }
                    """.trimIndent()
                )
            } else {
                respond(
                    ex,
                    200,
                    """{"data":[{"id":"1720000001","attributes":{"name":"Christine - EP","artistName":"Chrissy Chlapecka"}}]}"""
                )
            }
        }
        server.createContext("/v1/catalog/gb/albums/") { ex -> respond(ex, 404, "{}") }

        val service = newCanvasService()

        assertNull(service.getAlbumCanvasArtwork("Christine", "Chrissy Chlapecka"))
        assertNull(service.getCanvasArtwork("Spite", "Chrissy Chlapecka", "Christine"))
        assertNull(service.getCanvasArtwork("Spite", "Chrissy Chlapecka", "Christine (High Voltage)"))
        assertTrue(
            "Queries must search by album title, never song title Spite: $queries",
            queries.none { it.contains("Spite", ignoreCase = true) }
        )

        val encore = service.getCanvasArtwork("Girlie Pop", "Chrissy Chlapecka", "Girlie Pop: Encore")
        assertEquals("$baseUrl/canvas/girlie_pop_encore.m3u8", encore?.videoUri)
    }

    @Test
    fun strictAlbumTitleMatchingRejectsSubstringsAndVersionMismatches() {
        val service = newCanvasService()

        assertFalse(service.albumNamesMatch("Slut Pop Miami", "Slut Pop"))
        assertFalse(service.albumNamesMatch("Slut Pop", "Slut Pop Miami"))
        assertFalse(service.albumNamesMatch("Girlie Pop: Encore", "Girlie Pop"))
        assertFalse(service.albumNamesMatch("Girlie Pop: Encore", "Christine"))
        assertFalse(service.albumNamesMatch("After Hours - Single", "After Hours"))
        assertFalse(service.albumNamesMatch("After Hours (Remix)", "After Hours"))
        assertFalse(service.albumNamesMatch("After Hours (Live)", "After Hours"))
        assertFalse(service.albumNamesMatch("After Hours (Remixes)", "After Hours (Deluxe)"))
        assertFalse(service.albumNamesMatch("Midnights (3am Edition)", "Midnights"))
        assertFalse(service.albumNamesMatch("Chrissy Chlapecka Essentials", "Christine"))

        assertTrue(service.albumNamesMatch("STARFUCKER (Deluxe)", "STARFUCKER"))
        assertTrue(service.albumNamesMatch("Christine - EP", "Christine"))
        assertTrue(service.albumNamesMatch("Christine (EP)", "Christine"))
        assertTrue(service.albumNamesMatch("After Hours (Deluxe) [Explicit]", "After Hours"))
        assertTrue(service.albumNamesMatch("Hold the Girl (Bonus Edition)", "Hold the Girl"))
    }

    @Test
    fun albumProbeRejectsMismatchedAlbumMetadataAndBlankSongAlbum() = runBlocking {
        setupBrowseEndpoint()

        server.createContext("/v1/catalog/us/search") { ex ->
            respond(
                ex,
                200,
                """
                {
                  "results": {
                    "albums": {
                      "data": [{
                        "id": "999",
                        "attributes": {
                          "name": "Christine",
                          "artistName": "Chrissy Chlapecka"
                        }
                      }]
                    },
                    "songs": {
                      "data": [{
                        "id": "s_blank_album",
                        "attributes": {
                          "name": "Christine",
                          "artistName": "Chrissy Chlapecka",
                          "albumName": "",
                          "editorialVideo": {
                            "motionDetailSquare": { "video": "$baseUrl/canvas/should_ignore.m3u8" }
                          }
                        }
                      }]
                    }
                  }
                }
                """.trimIndent()
            )
        }
        server.createContext("/v1/catalog/gb/search") { ex -> respond(ex, 200, """{"results":{}}""") }
        // Simulate Apple album ID 999 resolving to a different album (Girlie Pop: Encore)
        server.createContext("/v1/catalog/us/albums/999") { ex ->
            respond(
                ex,
                200,
                """
                {
                  "data": [{
                    "id": "999",
                    "attributes": {
                      "name": "Girlie Pop: Encore",
                      "artistName": "Chrissy Chlapecka",
                      "editorialVideo": {
                        "motionDetailSquare": { "video": "$baseUrl/canvas/wrong_probe.m3u8" }
                      }
                    }
                  }]
                }
                """.trimIndent()
            )
        }

        val service = newCanvasService()
        assertNull(service.getAlbumCanvasArtwork("Christine", "Chrissy Chlapecka"))
    }
}
