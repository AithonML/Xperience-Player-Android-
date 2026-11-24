package com.xperience.player.service

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.xperience.player.domain.model.AlbumItem
import com.xperience.player.ui.theme.ArtworkColorExtractor
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
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

class DetailCrashGuardTest {

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
        tempCacheDir = Files.createTempDirectory("detail_crash_guard").toFile()
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

    private fun buildSyntheticWebPlayJwt(): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"ES256","typ":"JWT","kid":"WebPlayKid"}""".toByteArray())
        val payload = encoder.encodeToString("""{"iss":"AMPWebPlay","iat":1700000000,"exp":4102444800}""".toByteArray())
        val sig = encoder.encodeToString("signature".toByteArray())
        return "$header.$payload.$sig"
    }

    private fun respond(exchange: HttpExchange, status: Int, body: String, contentType: String = "application/json") {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", contentType)
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    @Test
    fun peekCanvasMethodsReturnNullWhenUnpopulatedWithoutThrowing() {
        val jwt = buildSyntheticWebPlayJwt()
        val encodedEnv = URLEncoder.encode("""{"MEDIA_API":{"token":"$jwt"}}""", StandardCharsets.UTF_8.name())
        server.createContext("/browse") { ex ->
            respond(ex, 200, """<html><meta name="desktop-music-app/config/environment" content="$encodedEnv"></html>""", "text/html")
        }
        server.createContext("/echo.json") { ex -> respond(ex, 200, "[]") }
        server.createContext("/vivi.json") { ex -> respond(ex, 200, "[]") }

        val service = AppleMusicCanvasService(
            httpClient = testHttpClient,
            ampBaseUrl = "$baseUrl/",
            browseUrl = "$baseUrl/browse",
            echoFeedUrl = "$baseUrl/echo.json",
            viviFeedUrl = "$baseUrl/vivi.json",
            cacheRootDir = tempCacheDir
        )

        val startNs = System.nanoTime()
        assertNull(service.peekAlbumCanvasArtwork("Random Access Memories", "Daft Punk"))
        assertNull(service.peekCanvasArtwork("Instant Crush", "Daft Punk", "Random Access Memories"))
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
        assertTrue("Expected fast peek under 250ms, was ${elapsedMs}ms", elapsedMs < 250L)
    }

    @Test
    fun albumDetailFlowEmitsUniqueNonBlankIdsAcrossVersionsAndOtherAlbums() = runBlocking {
        val jwt = buildSyntheticWebPlayJwt()
        val encodedEnv = URLEncoder.encode("""{"MEDIA_API":{"token":"$jwt"}}""", StandardCharsets.UTF_8.name())
        server.createContext("/browse") { ex ->
            respond(ex, 200, """<html><meta name="desktop-music-app/config/environment" content="$encodedEnv"></html>""", "text/html")
        }
        server.createContext("/echo.json") { ex -> respond(ex, 200, "[]") }
        server.createContext("/vivi.json") { ex -> respond(ex, 200, "[]") }
        server.createContext("/v1/catalog/us/search") { ex -> respond(ex, 200, """{"results":{}}""") }
        server.createContext("/v1/catalog/gb/search") { ex -> respond(ex, 200, """{"results":{}}""") }

        server.createContext("/ytm/browse") { ex ->
            respond(
                ex,
                200,
                """
                {
                  "contents": {
                    "twoColumnBrowseResultsRenderer": {
                      "tabs": [{
                        "tabRenderer": {
                          "content": {
                            "sectionListRenderer": {
                              "contents": [{
                                "musicResponsiveHeaderRenderer": {
                                  "title": { "runs": [{ "text": "Current Album" }] },
                                  "straplineTextOne": { "runs": [{ "text": "Test Artist" }] }
                                }
                              }]
                            }
                          }
                        }
                      }],
                      "secondaryContents": {
                        "sectionListRenderer": {
                          "contents": [
                            {
                              "musicShelfRenderer": {
                                "contents": [
                                  {
                                    "musicResponsiveListItemRenderer": {
                                      "playlistItemData": { "videoId": "vid1" },
                                      "flexColumns": [{
                                        "musicResponsiveListItemFlexColumnRenderer": {
                                          "text": { "runs": [{ "text": "Song 1" }] }
                                        }
                                      }]
                                    }
                                  }
                                ]
                              }
                            },
                            {
                              "musicCarouselShelfRenderer": {
                                "header": { "musicCarouselShelfBasicHeaderRenderer": { "title": { "runs": [{ "text": "Other versions" }] } } },
                                "contents": [
                                  {
                                    "musicTwoRowItemRenderer": {
                                      "title": { "runs": [{ "text": "Deluxe Edition", "navigationEndpoint": { "browseEndpoint": { "browseId": "MPREb_dup" } } }] },
                                      "subtitle": { "runs": [{ "text": "2024" }] }
                                    }
                                  },
                                  {
                                    "musicTwoRowItemRenderer": {
                                      "title": { "runs": [{ "text": "Deluxe Copy", "navigationEndpoint": { "browseEndpoint": { "browseId": "MPREb_dup" } } }] },
                                      "subtitle": { "runs": [{ "text": "2024" }] }
                                    }
                                  },
                                  {
                                    "musicTwoRowItemRenderer": {
                                      "title": { "runs": [{ "text": "No Id Edition A" }] },
                                      "subtitle": { "runs": [{ "text": "2024" }] }
                                    }
                                  },
                                  {
                                    "musicTwoRowItemRenderer": {
                                      "title": { "runs": [{ "text": "No Id Edition B" }] },
                                      "subtitle": { "runs": [{ "text": "2024" }] }
                                    }
                                  }
                                ]
                              }
                            }
                          ]
                        }
                      }
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

        val snapshot = buildProgressiveAlbumDetailFlow(
            album = AlbumItem(id = "MPREb_root", title = "Current Album", artist = "Test Artist"),
            artistName = "Test Artist",
            localOtherAlbums = listOf(
                AlbumItem(id = "", title = "Local Alpha", artist = "Test Artist"),
                AlbumItem(id = "", title = "Local Beta", artist = "Test Artist")
            ),
            ytmService = ytmService,
            appleMusicCanvasService = appleService
        ).last()

        val otherIds = snapshot.otherAlbums.map { it.id }
        assertTrue(otherIds.all { it.isNotBlank() })
        assertEquals(otherIds.size, otherIds.distinct().size)

        val versionIds = snapshot.otherVersions.map { it.id }
        assertTrue(versionIds.all { it.isNotBlank() })
        assertEquals(versionIds.size, versionIds.distinct().size)
    }

    @Test
    fun artistAmbientHexHandlesInvalidAndBlankInputsWithoutThrowing() {
        assertNull(ArtworkColorExtractor.artistAmbientColorsFromHex(null, null))
        assertNull(ArtworkColorExtractor.artistAmbientColorsFromHex("", "   "))
        assertNull(ArtworkColorExtractor.artistAmbientColorsFromHex("#GGGGGG", "12345"))
    }
}
