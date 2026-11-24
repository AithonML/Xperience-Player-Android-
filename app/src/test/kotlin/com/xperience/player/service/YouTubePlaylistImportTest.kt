package com.xperience.player.service

import com.xperience.player.domain.model.YouTubeMusicLibraryPlaylist
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubePlaylistImportTest {

    private class PassThroughCipher : SecretCipher {
        override fun encrypt(plaintext: String): String = plaintext
        override fun decrypt(ciphertext: String): String = ciphertext
    }

    private fun createSignedInAuthManager(
        dataSyncId: String? = "112233445566778899001||1234567890"
    ): YouTubeAuthManager {
        val manager = YouTubeAuthManager(
            store = InMemoryAuthStore(),
            cipher = PassThroughCipher(),
            clockMs = { 1_760_000_000_000L }
        )
        manager.saveSession(
            cookie = "SID=test_sid; SAPISID=test_sapisid; __Secure-3PAPISID=test_3papisid",
            visitorData = "CgtTESTVISITOR",
            dataSyncId = dataSyncId
        )
        return manager
    }

    private fun createMockService(
        authManager: YouTubeAuthManager?,
        handler: (url: String, body: String, headers: okhttp3.Headers) -> Pair<Int, String>
    ): YouTubeMusicService {
        val interceptor = Interceptor { chain ->
            val request = chain.request()
            val buffer = Buffer()
            request.body?.writeTo(buffer)
            val bodyStr = buffer.readUtf8()
            val (code, responseJson) = handler(request.url.toString(), bodyStr, request.headers)
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code == 200) "OK" else "Error")
                .body(responseJson.toResponseBody())
                .build()
        }
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        return YouTubeMusicService(
            httpClient = client,
            searchUrl = "https://mock.ytm/search",
            browseUrl = "https://mock.ytm/browse",
            nextUrl = "https://mock.ytm/next",
            accountMenuUrl = "https://mock.ytm/account_menu",
            accountSwitcherUrl = "https://mock.ytm/switcher",
            authManager = authManager,
            clockMs = { 1_760_000_000_000L }
        )
    }

    @Test
    fun parseUserLibraryPlaylistsHandlesTwoRowResponsiveSkipsNewAndPodcasts() {
        val service = createMockService(authManager = null) { _, _, _ -> 200 to "{}" }
        val json = JSONObject(
            """
            {
              "contents": {
                "singleColumnBrowseResultsRenderer": {
                  "tabs": [
                    {
                      "tabRenderer": {
                        "content": {
                          "sectionListRenderer": {
                            "contents": [
                              {
                                "gridRenderer": {
                                  "items": [
                                    {
                                      "musicTwoRowItemRenderer": {
                                        "title": { "runs": [ { "text": "New playlist" } ] },
                                        "navigationEndpoint": {
                                          "createPlaylistServiceEndpoint": { "params": "CAES" }
                                        }
                                      }
                                    },
                                    {
                                      "musicTwoRowItemRenderer": {
                                        "title": { "runs": [ { "text": "Liked Music" } ] },
                                        "subtitle": { "runs": [ { "text": "Auto playlist" }, { "text": " • " }, { "text": "120 songs" } ] },
                                        "navigationEndpoint": {
                                          "browseEndpoint": {
                                            "browseId": "VLLM",
                                            "browseEndpointContextSupportedConfigs": {
                                              "browseEndpointContextMusicConfig": { "pageType": "MUSIC_PAGE_TYPE_PLAYLIST" }
                                            }
                                          }
                                        },
                                        "thumbnailRenderer": {
                                          "musicThumbnailRenderer": {
                                            "thumbnail": { "thumbnails": [ { "url": "https://lh3.googleusercontent.com/lm=w226-h226" } ] }
                                          }
                                        }
                                      }
                                    },
                                    {
                                      "musicTwoRowItemRenderer": {
                                        "title": { "runs": [ { "text": "Episodes for Later" } ] },
                                        "navigationEndpoint": {
                                          "browseEndpoint": {
                                            "browseId": "VLSE",
                                            "browseEndpointContextSupportedConfigs": {
                                              "browseEndpointContextMusicConfig": { "pageType": "MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE" }
                                            }
                                          }
                                        }
                                      }
                                    },
                                    {
                                      "musicTwoRowItemRenderer": {
                                        "title": { "runs": [ { "text": "Late Night Synth" } ] },
                                        "subtitle": { "runs": [ { "text": "Playlist" }, { "text": " • " }, { "text": "Alice" }, { "text": " • " }, { "text": "42 songs" } ] },
                                        "navigationEndpoint": {
                                          "browseEndpoint": {
                                            "browseId": "VLPLsynth999",
                                            "browseEndpointContextSupportedConfigs": {
                                              "browseEndpointContextMusicConfig": { "pageType": "MUSIC_PAGE_TYPE_PLAYLIST" }
                                            }
                                          }
                                        },
                                        "thumbnailRenderer": {
                                          "musicThumbnailRenderer": {
                                            "thumbnail": { "thumbnails": [ { "url": "https://lh3.googleusercontent.com/synth=w226-h226" } ] }
                                          }
                                        }
                                      }
                                    },
                                    {
                                      "musicResponsiveListItemRenderer": {
                                        "flexColumns": [
                                          { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "Late Night Synth Duplicate" } ] } } },
                                          { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "Alice" } ] } } }
                                        ],
                                        "navigationEndpoint": {
                                          "browseEndpoint": { "browseId": "VLPLsynth999" }
                                        }
                                      }
                                    }
                                  ],
                                  "continuations": [
                                    { "nextContinuationData": { "continuation": "CONT_PAGE_2" } }
                                  ]
                                }
                              }
                            ]
                          }
                        }
                      }
                    }
                  ]
                }
              }
            }
            """.trimIndent()
        )

        val (items, continuation) = service.parseUserLibraryPlaylistsResponse(json)
        assertEquals(2, items.size)
        assertEquals("LM", items[0].playlistId)
        assertEquals("VLLM", items[0].browseId)
        assertEquals("Liked Music", items[0].title)
        assertEquals("PLsynth999", items[1].playlistId)
        assertEquals("VLPLsynth999", items[1].browseId)
        assertEquals("Late Night Synth", items[1].title)
        assertEquals("CONT_PAGE_2", continuation)
    }

    @Test
    fun fetchUserLibraryPlaylistsReturnsEmptyWhenSignedOut() = runTest {
        var calls = 0
        val service = createMockService(authManager = null) { _, _, _ ->
            calls++
            200 to "{}"
        }
        val result = service.fetchUserLibraryPlaylists()
        assertTrue(result.isEmpty())
        assertEquals(0, calls)
    }

    @Test
    fun fetchUserLibraryPlaylistsRetriesWithoutPageIdOn403AndFollowsContinuation() = runTest {
        val auth = createSignedInAuthManager(dataSyncId = "112233445566778899001||9988776655")
        val requests = mutableListOf<Pair<String, String?>>()

        val page1 = """
            {
              "contents": {
                "singleColumnBrowseResultsRenderer": {
                  "tabs": [{
                    "tabRenderer": {
                      "content": {
                        "sectionListRenderer": {
                          "contents": [{
                            "gridRenderer": {
                              "items": [{
                                "musicTwoRowItemRenderer": {
                                  "title": { "runs": [{ "text": "Workout" }] },
                                  "navigationEndpoint": { "browseEndpoint": { "browseId": "VLPLwork1" } }
                                }
                              }],
                              "continuations": [{ "nextContinuationData": { "continuation": "NEXT_LIB_TOKEN" } }]
                            }
                          }]
                        }
                      }
                    }
                  }]
                }
              }
            }
        """.trimIndent()

        val page2 = """
            {
              "continuationContents": {
                "gridContinuation": {
                  "items": [{
                    "musicTwoRowItemRenderer": {
                      "title": { "runs": [{ "text": "Focus" }] },
                      "navigationEndpoint": { "browseEndpoint": { "browseId": "VLPLfocus2" } }
                    }
                  }]
                }
              }
            }
        """.trimIndent()

        var firstBrowseAttempt = 0
        val service = createMockService(authManager = auth) { _, body, headers ->
            val json = JSONObject(body)
            val pageId = headers["X-Goog-PageId"]
            requests.add(body to pageId)
            when {
                json.optString("browseId") == "FEmusic_liked_playlists" && firstBrowseAttempt == 0 -> {
                    firstBrowseAttempt++
                    403 to """{"error":{"code":403}}"""
                }
                json.optString("browseId") == "FEmusic_liked_playlists" -> 200 to page1
                json.optString("continuation") == "NEXT_LIB_TOKEN" -> 200 to page2
                else -> 404 to "{}"
            }
        }

        val playlists = service.fetchUserLibraryPlaylists()
        assertEquals(2, playlists.size)
        assertEquals(listOf("PLwork1", "PLfocus2"), playlists.map { it.playlistId })
        assertTrue(requests.size >= 3)
        // Retry call stripped X-Goog-PageId
        assertNull(requests[1].second)
    }

    @Test
    fun fetchUserPlaylistTracksFollowsContinuationsAndDoesNotFallbackToSearchWhenEmpty() = runTest {
        val auth = createSignedInAuthManager(dataSyncId = null)
        var searchCalled = false

        val trackPage1 = """
            {
              "contents": {
                "twoColumnBrowseResultsRenderer": {
                  "secondaryContents": {
                    "sectionListRenderer": {
                      "contents": [{
                        "musicPlaylistShelfRenderer": {
                          "contents": [
                            {
                              "musicResponsiveListItemRenderer": {
                                "playlistItemData": { "videoId": "vidAAA11111" },
                                "flexColumns": [
                                  { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Track One" }] } } },
                                  { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Artist One" }] } } }
                                ]
                              }
                            },
                            {
                              "continuationItemRenderer": {
                                "continuationEndpoint": {
                                  "continuationCommand": { "token": "PL_CONT_2" }
                                }
                              }
                            }
                          ]
                        }
                      }]
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val trackPage2 = """
            {
              "onResponseReceivedActions": [{
                "appendContinuationItemsAction": {
                  "continuationItems": [{
                    "musicResponsiveListItemRenderer": {
                      "playlistItemData": { "videoId": "vidBBB22222" },
                      "flexColumns": [
                        { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Track Two" }] } } },
                        { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [{ "text": "Artist Two" }] } } }
                      ]
                    }
                  }]
                }
              }]
            }
        """.trimIndent()

        val service = createMockService(authManager = auth) { url, body, _ ->
            if (url.contains("/search")) {
                searchCalled = true
                return@createMockService 200 to "{}"
            }
            val json = JSONObject(body)
            when {
                json.optString("browseId") == "VLPLmulti" -> 200 to trackPage1
                json.optString("continuation") == "PL_CONT_2" -> 200 to trackPage2
                json.optString("browseId") == "VLPLempty" -> 200 to """{"contents":{}}"""
                else -> 404 to "{}"
            }
        }

        val multiTracks = service.fetchUserPlaylistTracks(
            YouTubeMusicLibraryPlaylist(playlistId = "PLmulti", browseId = "VLPLmulti", title = "Multi")
        )
        assertEquals(2, multiTracks.size)
        assertEquals(listOf("ytm_vidAAA11111", "ytm_vidBBB22222"), multiTracks.map { it.id })

        val emptyTracks = service.fetchUserPlaylistTracks(
            YouTubeMusicLibraryPlaylist(playlistId = "PLempty", browseId = "VLPLempty", title = "Empty Playlist")
        )
        assertTrue(emptyTracks.isEmpty())
        assertTrue("Empty playlist must not fall back to catalog search", !searchCalled)
    }
}
