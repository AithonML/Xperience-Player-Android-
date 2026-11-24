package com.xperience.player.service

import com.xperience.player.domain.model.AlbumItem
import com.xperience.player.domain.model.ArtistItem
import com.xperience.player.domain.model.HomeDiscoveryFeed
import com.xperience.player.domain.model.NewReleaseItem
import com.xperience.player.domain.model.RadioStationItem
import com.xperience.player.domain.model.RecommendedPlaylistItem
import com.xperience.player.domain.model.TopPickKind
import com.xperience.player.domain.model.TrackItem
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.Interceptor
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class YouTubeAuthAndFeedTest {

    /**
     * Pure JVM AES-GCM [SecretCipher] test implementation (same algorithm as AndroidKeystoreSecretCipher)
     * so encryption/decryption roundtrip tests run deterministically on JVM without Android Keystore.
     */
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

    private fun expectedSha1Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    @Test
    fun cookieParsingAndSapisidExtractionHandleMultipleVariants() {
        val cookie = "PREF=tz=America.Chicago; SAPISID=abc=123/xyz; __Secure-3PAPISID=sec3_val; __Secure-1PAPISID=sec1_val; SID=sid_token"
        val map = YouTubeAuthManager.parseCookieString(cookie)

        assertEquals("abc=123/xyz", map["SAPISID"])
        assertEquals("sec3_val", map["__Secure-3PAPISID"])
        assertEquals("sec1_val", map["__Secure-1PAPISID"])
        assertEquals("abc=123/xyz", YouTubeAuthManager.extractSapisid(map))
        assertTrue(YouTubeAuthManager.hasYouTubeAccountSession(cookie))

        // Fallback to __Secure-3PAPISID when SAPISID missing
        val cookie3p = "SID=sid_token; __Secure-3PAPISID=only_3p"
        assertEquals("only_3p", YouTubeAuthManager.extractSapisid(cookie3p))
        assertTrue(YouTubeAuthManager.hasYouTubeAccountSession(cookie3p))

        // Unauthenticated visitor cookie should return false
        assertFalse(YouTubeAuthManager.hasYouTubeAccountSession("PREF=hl=en; YSC=random"))
        assertNull(YouTubeAuthManager.extractSapisid("PREF=hl=en"))
    }

    @Test
    fun sapisidHashAndAuthHeadersMatchYouTubeSpecification() {
        val ts = 1700000000L
        val cookie = "SAPISID=sapi_secret; __Secure-1PAPISID=p1_secret; __Secure-3PAPISID=p3_secret"

        // Without dataSyncId -> standard SAPISIDHASH + SAPISID1PHASH + SAPISID3PHASH
        val authStandard = YouTubeAuthManager.computeAuthorizationHeader(
            cookie = cookie,
            timestampSeconds = ts,
            origin = "https://music.youtube.com",
            dataSyncId = null
        )!!
        val expectedBase = expectedSha1Hex("$ts sapi_secret https://music.youtube.com")
        val expected1p = expectedSha1Hex("$ts p1_secret https://music.youtube.com")
        val expected3p = expectedSha1Hex("$ts p3_secret https://music.youtube.com")
        assertTrue(authStandard.contains("SAPISIDHASH ${ts}_$expectedBase"))
        assertTrue(authStandard.contains("SAPISID1PHASH ${ts}_$expected1p"))
        assertTrue(authStandard.contains("SAPISID3PHASH ${ts}_$expected3p"))

        // With dataSyncId containing "||" -> includes _u variant hashing userSessionId (token after "||")
        val dataSyncId = "11223344||10999888"
        val authWithUser = YouTubeAuthManager.computeAuthorizationHeader(
            cookie = cookie,
            timestampSeconds = ts,
            origin = "https://music.youtube.com",
            dataSyncId = dataSyncId
        )!!
        val expectedU = expectedSha1Hex("10999888 $ts sapi_secret https://music.youtube.com")
        assertTrue(authWithUser.contains("SAPISIDHASH ${ts}_${expectedU}_u"))
    }

    @Test
    fun encryptedSessionRoundtripRestoresAcrossInstancesAndNeverStoresPlaintextCookie() {
        var nowMs = 1_700_000_000_000L
        val store = InMemoryAuthStore()
        val cipher = JvmAesGcmCipher()

        val manager1 = YouTubeAuthManager(store = store, cipher = cipher, clockMs = { nowMs })
        val secretCookie = "SAPISID=top_secret_sapi; SID=top_secret_sid"
        manager1.saveSession(
            cookie = secretCookie,
            visitorData = "CgtWaXNpdG9yMTIz",
            dataSyncId = "10555999||10999888",
            accountName = "Alex Rivera",
            accountHandle = "@alexmusic",
            avatarUrl = "https://yt3.ggpht.com/avatar.jpg"
        )

        // Ensure raw stored value is encrypted and doesn't leak secrets
        val storedCiphertext = store.getString("ytm_encrypted_session_v1")
        assertNotNull(storedCiphertext)
        assertFalse(storedCiphertext!!.contains("top_secret_sapi"))
        assertFalse(storedCiphertext.contains("top_secret_sid"))
        assertNotEquals(secretCookie, storedCiphertext)

        // New manager reading same encrypted store
        val manager2 = YouTubeAuthManager(store = store, cipher = cipher, clockMs = { nowMs })
        assertTrue(manager2.isSignedIn())
        assertEquals(secretCookie, manager2.getCookie())
        assertEquals("CgtWaXNpdG9yMTIz", manager2.getVisitorData())
        assertEquals("10555999", manager2.getOnBehalfOfUser())
        assertEquals("Alex Rivera", manager2.accountState.value.accountName)
        assertEquals("@alexmusic", manager2.accountState.value.accountHandle)

        val headers = manager2.buildAuthHeaders(timestampSeconds = 1_700_000_000L)
        assertEquals(secretCookie, headers["Cookie"])
        assertEquals("0", headers["X-Goog-AuthUser"])
        assertEquals("https://music.youtube.com", headers["X-Origin"])
        assertEquals("CgtWaXNpdG9yMTIz", headers["X-Goog-Visitor-Id"])
        assertEquals("10555999", headers["X-Goog-PageId"])
        assertTrue(headers["Authorization"]!!.contains("SAPISIDHASH 1700000000_"))

        manager2.clearSession()
        assertFalse(manager2.isSignedIn())
        assertNull(manager2.getCookie())
        assertFalse(manager2.accountState.value.isSignedIn)
    }

    @Test
    fun loginPromptDismissalRulesWorkForTemporaryPermanentAndSignedInStates() {
        var nowMs = 100_000_000L
        val store = InMemoryAuthStore()
        val cipher = JvmAesGcmCipher()
        val manager = YouTubeAuthManager(store = store, cipher = cipher, clockMs = { nowMs })

        // Signed out, never dismissed -> prompt shows
        assertTrue(manager.shouldShowLoginPrompt())

        // Temporarily dismiss for 2 hours -> hidden until elapsed
        manager.dismissPromptTemporarily(durationMs = 7_200_000L)
        assertFalse(manager.shouldShowLoginPrompt())
        nowMs += 3_600_000L
        assertFalse(manager.shouldShowLoginPrompt())
        nowMs += 3_600_001L
        assertTrue(manager.shouldShowLoginPrompt())

        // Signed in -> prompt never shows
        manager.saveSession(cookie = "SAPISID=abc", visitorData = "vd")
        assertFalse(manager.shouldShowLoginPrompt())

        // Signed out again -> shows unless permanently dismissed
        manager.clearSession()
        assertTrue(manager.shouldShowLoginPrompt())

        // Permanently dismiss -> stays hidden forever
        manager.dismissPromptPermanently()
        assertFalse(manager.shouldShowLoginPrompt())
        nowMs += 90L * 24L * 3600L * 1000L
        assertFalse(manager.shouldShowLoginPrompt())
    }

    @Test
    fun parseAccountMenuAndWebRemixContextWorkCorrectly() {
        val store = InMemoryAuthStore()
        val manager = YouTubeAuthManager(store = store, cipher = JvmAesGcmCipher())
        manager.saveSession(
            cookie = "SAPISID=abc123",
            visitorData = "VD_XYZ",
            dataSyncId = "12345||0"
        )
        val service = YouTubeMusicService(authManager = manager)

        val ctxSignedIn = service.buildWebRemixContextJson()
        assertEquals("VD_XYZ", ctxSignedIn.getJSONObject("client").getString("visitorData"))
        assertEquals("12345", ctxSignedIn.getJSONObject("user").getString("onBehalfOfUser"))

        val accountJson = JSONObject().apply {
            put("actions", JSONArray().apply {
                put(JSONObject().apply {
                    put("openPopupAction", JSONObject().apply {
                        put("popup", JSONObject().apply {
                            put("multiPageMenuRenderer", JSONObject().apply {
                                put("header", JSONObject().apply {
                                    put("activeAccountHeaderRenderer", JSONObject().apply {
                                        put("accountName", runs("Maria Lopez"))
                                        put("channelHandle", runs("@marialopez"))
                                        put("accountPhoto", JSONObject().apply {
                                            put("thumbnails", JSONArray().apply {
                                                put(JSONObject().put("url", "https://yt3.ggpht.com/maria=s120"))
                                            })
                                        })
                                    })
                                })
                            })
                        })
                    })
                })
            })
        }

        val parsed = service.parseAccountMenuResponse(accountJson)
        assertNotNull(parsed)
        assertTrue(parsed!!.isSignedIn)
        assertEquals("Maria Lopez", parsed.accountName)
        assertEquals("@marialopez", parsed.accountHandle)
        assertTrue(parsed.avatarUrl!!.contains("https://yt3.ggpht.com/maria"))

        manager.clearSession()
        val ctxSignedOut = service.buildWebRemixContextJson()
        assertFalse(ctxSignedOut.has("user"))
    }

    @Test
    fun parseHomeFeedNewReleasesPlaylistAndRadioResponses() {
        val service = YouTubeMusicService()

        // 1. FEmusic_home response
        val homeJson = JSONObject().apply {
            put("contents", JSONObject().apply {
                put("singleColumnBrowseResultsRenderer", JSONObject().apply {
                    put("tabs", JSONArray().apply {
                        put(JSONObject().apply {
                            put("tabRenderer", JSONObject().apply {
                                put("content", JSONObject().apply {
                                    put("sectionListRenderer", JSONObject().apply {
                                        put("contents", JSONArray().apply {
                                            // Shelf 1: Quick picks
                                            put(carouselShelf("Quick picks", JSONArray().apply {
                                                put(responsiveTrackItem("v111", "Starboy", "The Weeknd", "Starboy", "3:50"))
                                            }))
                                            // Shelf 2: Mixed for you
                                            put(carouselShelf("Mixed for you", JSONArray().apply {
                                                put(twoRowBrowseItem(
                                                    title = "My Supermix",
                                                    subtitle = "YouTube Music • 50 songs",
                                                    browseId = "VLRDTMAK5uy_supermix",
                                                    pageType = "MUSIC_PAGE_TYPE_PLAYLIST"
                                                ))
                                            }))
                                            // Shelf 3: Recommended radios
                                            put(carouselShelf("Recommended radios", JSONArray().apply {
                                                put(twoRowWatchItem(
                                                    title = "Daft Punk Radio",
                                                    subtitle = "Daft Punk, Justice, Kavinsky",
                                                    videoId = "seedVid9",
                                                    playlistId = "RDEM_daft9"
                                                ))
                                            }))
                                            // Shelf 4: Albums for you
                                            put(carouselShelf("Albums for you", JSONArray().apply {
                                                put(twoRowBrowseItem(
                                                    title = "Discovery",
                                                    subtitle = "Album • Daft Punk • 2001",
                                                    browseId = "MPREb_discovery",
                                                    pageType = "MUSIC_PAGE_TYPE_ALBUM"
                                                ))
                                            }))
                                        })
                                    })
                                })
                            })
                        })
                    })
                })
            })
        }

        val parsedHome = service.parseHomeFeedResponse(homeJson, isPersonalized = true)
        assertTrue(parsedHome.isPersonalized)
        assertEquals(1, parsedHome.personalizedTracks.size)
        assertEquals("Starboy", parsedHome.personalizedTracks[0].title)
        assertEquals("The Weeknd", parsedHome.personalizedTracks[0].artist)
        assertEquals(1, parsedHome.playlistsMadeForYou.size)
        assertEquals("My Supermix", parsedHome.playlistsMadeForYou[0].title)
        assertEquals("RDTMAK5uy_supermix", parsedHome.playlistsMadeForYou[0].playlistId)
        assertEquals(1, parsedHome.radioStations.size)
        assertEquals("Daft Punk Radio", parsedHome.radioStations[0].title)
        assertEquals("RDEM_daft9", parsedHome.radioStations[0].playlistId)
        assertEquals(1, parsedHome.personalizedAlbums.size)
        assertEquals("Discovery", parsedHome.personalizedAlbums[0].title)
        assertEquals(2001, parsedHome.personalizedAlbums[0].year)

        // 2. FEmusic_new_releases_albums response (gridRenderer)
        val newReleasesJson = JSONObject().apply {
            put("contents", JSONObject().apply {
                put("singleColumnBrowseResultsRenderer", JSONObject().apply {
                    put("tabs", JSONArray().apply {
                        put(JSONObject().apply {
                            put("tabRenderer", JSONObject().apply {
                                put("content", JSONObject().apply {
                                    put("sectionListRenderer", JSONObject().apply {
                                        put("contents", JSONArray().apply {
                                            put(JSONObject().apply {
                                                put("gridRenderer", JSONObject().apply {
                                                    put("header", JSONObject().apply {
                                                        put("gridHeaderRenderer", JSONObject().apply {
                                                            put("title", runs("New albums & singles"))
                                                        })
                                                    })
                                                    put("items", JSONArray().apply {
                                                        put(twoRowBrowseItem(
                                                            title = "Hurry Up Tomorrow",
                                                            subtitle = "Album • The Weeknd • 2025",
                                                            browseId = "MPREb_hut",
                                                            pageType = "MUSIC_PAGE_TYPE_ALBUM"
                                                        ))
                                                    })
                                                })
                                            })
                                        })
                                    })
                                })
                            })
                        })
                    })
                })
            })
        }

        val releases = service.parseNewReleasesResponse(newReleasesJson)
        assertEquals(1, releases.size)
        assertEquals("Hurry Up Tomorrow", releases[0].title)
        assertEquals("The Weeknd", releases[0].artist)
        assertTrue(releases[0].isAlbum)
        assertEquals("2025", releases[0].year)

        // 3. /next radio panel response
        val nextJson = JSONObject().apply {
            put("contents", JSONObject().apply {
                put("singleColumnMusicWatchNextResultsRenderer", JSONObject().apply {
                    put("tabbedRenderer", JSONObject().apply {
                        put("watchNextTabbedResultsRenderer", JSONObject().apply {
                            put("tabs", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("tabRenderer", JSONObject().apply {
                                        put("content", JSONObject().apply {
                                            put("musicQueueRenderer", JSONObject().apply {
                                                put("content", JSONObject().apply {
                                                    put("playlistPanelRenderer", JSONObject().apply {
                                                        put("contents", JSONArray().apply {
                                                            put(JSONObject().apply {
                                                                put("playlistPanelVideoRenderer", JSONObject().apply {
                                                                    put("videoId", "vidNext1")
                                                                    put("title", runs("One More Time"))
                                                                    put("longBylineText", runs("Daft Punk • Discovery • 2001"))
                                                                    put("lengthText", runs("5:20"))
                                                                    put("thumbnail", JSONObject().apply {
                                                                        put("thumbnails", JSONArray().apply {
                                                                            put(JSONObject().put("url", "https://lh3.googleusercontent.com/omt=w120-h120"))
                                                                        })
                                                                    })
                                                                })
                                                            })
                                                        })
                                                    })
                                                })
                                            })
                                        })
                                    })
                                })
                            })
                        })
                    })
                })
            })
        }

        val radioTracks = service.parseNextPanelTracks(nextJson)
        assertEquals(1, radioTracks.size)
        assertEquals("ytm_vidNext1", radioTracks[0].id)
        assertEquals("One More Time", radioTracks[0].title)
        assertEquals("Daft Punk", radioTracks[0].artist)
        assertEquals("Discovery", radioTracks[0].album)
        assertEquals(320_000L, radioTracks[0].durationMs)

        // 4. /browse VL playlist twoColumnBrowseResultsRenderer response
        val playlistJson = JSONObject().apply {
            put("contents", JSONObject().apply {
                put("twoColumnBrowseResultsRenderer", JSONObject().apply {
                    put("secondaryContents", JSONObject().apply {
                        put("sectionListRenderer", JSONObject().apply {
                            put("contents", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("musicPlaylistShelfRenderer", JSONObject().apply {
                                        put("contents", JSONArray().apply {
                                            put(responsivePlaylistTrackItem("plVid1", "Blinding Lights", "The Weeknd", "After Hours", "3:20"))
                                        })
                                    })
                                })
                            })
                        })
                    })
                })
            })
        }

        val playlistTracks = service.parsePlaylistBrowseTracks(playlistJson)
        assertEquals(1, playlistTracks.size)
        assertEquals("Blinding Lights", playlistTracks[0].title)
        assertEquals("The Weeknd", playlistTracks[0].artist)
        assertEquals("After Hours", playlistTracks[0].album)
        assertEquals(200_000L, playlistTracks[0].durationMs)
    }

    @Test
    fun resolveRadioAndPlaylistSendAuthenticatedInnerTubeRequests() = runTest {
        var lastRequestUrl: String? = null
        var lastAuthHeader: String? = null
        var lastBodyJson: JSONObject? = null

        val mockNextResponse = JSONObject().apply {
            put("playlistPanelRenderer", JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("playlistPanelVideoRenderer", JSONObject().apply {
                            put("videoId", "mockRadio1")
                            put("title", runs("Midnight City"))
                            put("longBylineText", runs("M83 • Hurry Up, We're Dreaming"))
                            put("lengthText", runs("4:03"))
                        })
                    })
                })
            })
        }.toString()

        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                lastRequestUrl = req.url.toString()
                lastAuthHeader = req.header("Authorization")
                val buffer = okio.Buffer()
                req.body?.writeTo(buffer)
                lastBodyJson = JSONObject(buffer.readUtf8())
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(mockNextResponse.toResponseBody())
                    .build()
            })
            .build()

        val authManager = YouTubeAuthManager(store = InMemoryAuthStore(), cipher = JvmAesGcmCipher(), clockMs = { 1_700_000_000_000L })
        authManager.saveSession(cookie = "SAPISID=test_sapi", visitorData = "VD_TEST", dataSyncId = "999||0")

        val service = YouTubeMusicService(
            httpClient = client,
            nextUrl = "https://music.youtube.com/youtubei/v1/next?prettyPrint=false",
            authManager = authManager,
            clockMs = { 1_700_000_000_000L }
        )

        val tracks = service.resolveRadioStationTracks(
            RadioStationItem(
                id = "radio_1",
                title = "Synthwave Radio",
                subtitle = "M83",
                artworkUri = null,
                playlistId = "RDEM_m83",
                seedVideoId = "seed123"
            )
        )

        assertEquals(1, tracks.size)
        assertEquals("Midnight City", tracks[0].title)
        assertEquals("https://music.youtube.com/youtubei/v1/next?prettyPrint=false", lastRequestUrl)
        assertNotNull(lastAuthHeader)
        assertTrue(lastAuthHeader!!.contains("SAPISIDHASH 1700000000_"))
        assertEquals("RDEM_m83", lastBodyJson?.getString("playlistId"))
        assertEquals("seed123", lastBodyJson?.getString("videoId"))
    }

    @Test
    fun moreLikeSectionsBuilderCreatesThreeDistinctSectionsUpToFifteenAlbumsEach() {
        val liked = listOf(
            TrackItem(id = "t1", title = "Song A", artist = "Artist A", album = "Album Alpha"),
            TrackItem(id = "t2", title = "Song A2", artist = "Artist A", album = "Album Alpha"), // duplicate album
            TrackItem(id = "t3", title = "Song B", artist = "Artist B", album = "Album Beta")
        )
        val recent = listOf(
            TrackItem(id = "t4", title = "Song C", artist = "Artist C", album = "Album Gamma")
        )

        val seeds = selectSeedAlbumsForMoreLike(
            likedTracks = liked,
            recentlyPlayed = recent,
            localAlbums = emptyList(),
            ytmAlbums = emptyList(),
            targetCount = 3
        )

        assertEquals(3, seeds.size)
        assertEquals(listOf("Album Alpha", "Album Beta", "Album Gamma"), seeds.map { it.title })

        // Provide 20 candidate albums per seed (including a duplicate of the seed itself & duplicate candidate)
        val candidatesMap = seeds.associate { seed ->
            val list = mutableListOf<AlbumItem>()
            list.add(seed) // should be filtered out
            for (idx in 1..20) {
                list.add(
                    AlbumItem(
                        id = "${seed.id}_sim_$idx",
                        title = "Similar $idx to ${seed.title}",
                        artist = "Sim Artist $idx"
                    )
                )
            }
            // Add duplicate of first similar album
            list.add(list[1])
            seed.id to list
        }

        val sections = buildMoreLikeAlbumSections(
            seeds = seeds,
            candidatesBySeedId = candidatesMap,
            maxSections = 3,
            maxAlbumsPerSection = 15
        )

        assertEquals(3, sections.size)
        sections.forEachIndexed { idx, section ->
            assertEquals("More like: ${seeds[idx].title}", section.sectionTitle)
            assertEquals(15, section.albums.size)
            assertFalse(section.albums.any { it.title == seeds[idx].title })
        }
    }

    @Test
    fun topPicksBuilderAndHomeDiscoveryFeedEightItemCapsWorkCorrectly() {
        val allRadios = (1..20).map { i ->
            RadioStationItem(
                id = "r$i",
                title = "Radio $i",
                subtitle = "Mix $i",
                artworkUri = null,
                playlistId = "RD$i"
            )
        }
        val allReleases = (1..20).map { i ->
            NewReleaseItem(
                id = "nr$i",
                title = "Release $i",
                artist = "Artist $i",
                subtitle = "Album • 2026",
                artworkUri = null,
                isAlbum = true
            )
        }
        val playlists = (1..4).map { i ->
            RecommendedPlaylistItem(
                id = "pl$i",
                title = "Playlist $i",
                subtitle = "For You",
                artworkUri = null,
                playlistId = "VLPL$i"
            )
        }
        val artists = listOf(ArtistItem(id = "art1", name = "Daft Punk", genre = "Electronic"))

        val topPicks = buildHomeTopPicks(
            homeCandidates = emptyList(),
            radioStations = allRadios,
            newReleases = allReleases,
            playlists = playlists,
            relatedArtists = artists,
            candidateTracks = emptyList(),
            isPersonalized = true,
            maxItems = 8
        )

        assertEquals(8, topPicks.size)
        assertTrue(topPicks.any { it.kind == TopPickKind.PERSONAL_STATION })
        assertTrue(topPicks.any { it.kind == TopPickKind.NEW_RELEASE })
        assertTrue(topPicks.any { it.kind == TopPickKind.FEATURED_PLAYLIST })
        assertTrue(topPicks.any { it.kind == TopPickKind.ARTIST_SPOTLIGHT })

        // Verify Home preview caps (8) vs full catalog (20) & backward compatibility of existing defaults
        val legacyFeed = HomeDiscoveryFeed(
            personalizedSubtitle = "Trending on YouTube Music",
            recentlyPlayedTracks = emptyList(),
            relatedArtists = artists
        )
        assertTrue(legacyFeed.topPicks.isEmpty())
        assertFalse(legacyFeed.isPersonalizedFromAccount)

        val feed = HomeDiscoveryFeed(
            personalizedSubtitle = "Personalized",
            recentlyPlayedTracks = emptyList(),
            relatedArtists = artists,
            topPicks = topPicks,
            radioStations = allRadios.take(8),
            allRadioStations = allRadios,
            newReleases = allReleases.take(8),
            allNewReleases = allReleases,
            isPersonalizedFromAccount = true
        )
        assertEquals(8, feed.radioStations.size)
        assertEquals(20, feed.allRadioStations.size)
        assertEquals(8, feed.newReleases.size)
        assertEquals(20, feed.allNewReleases.size)
    }

    @Test
    fun cookieMergingPartialSessionRejectionAndPercentDecodingPreventBrokenCaptures() {
        // SID-only cookie lacks SAPISID / __Secure-3PAPISID / __Secure-1PAPISID needed for Authorization header
        assertFalse(YouTubeAuthManager.hasYouTubeAccountSession("SID=sid_only; HSID=hsid_only"))

        // Merging across music.youtube.com + www.youtube.com + youtube.com recovers SAPISID without duplicating keys
        val merged = YouTubeAuthManager.mergeCookieStrings(
            "PREF=tz=UTC; SID=sid_1; SAPISID=",
            "SID=sid_stale; SAPISID=sapi_from_www; __Secure-3PAPISID=p3_from_www",
            "__Secure-1PAPISID=p1_from_root"
        )
        val mergedMap = YouTubeAuthManager.parseCookieString(merged)
        assertEquals("sid_1", mergedMap["SID"])
        assertEquals("sapi_from_www", mergedMap["SAPISID"])
        assertEquals("p3_from_www", mergedMap["__Secure-3PAPISID"])
        assertEquals("p1_from_root", mergedMap["__Secure-1PAPISID"])
        assertTrue(YouTubeAuthManager.hasYouTubeAccountSession(merged))

        // Percent decoding must decode %3D / %7C without turning '+' into space
        assertEquals("Cgt+abc/123==", YouTubeAuthManager.decodePercentEncoded("Cgt+abc/123%3D%3D"))
        assertEquals("10555999||10999888", YouTubeAuthManager.decodePercentEncoded("10555999%7C%7C10999888"))
        assertNull(YouTubeAuthManager.decodePercentEncoded("null"))
        assertNull(YouTubeAuthManager.decodePercentEncoded("   "))

        // Saving a different account session must not inherit previous account's stale metadata
        val manager = YouTubeAuthManager(store = InMemoryAuthStore(), cipher = JvmAesGcmCipher())
        manager.saveSession(
            cookie = "SAPISID=first_sapi; SID=first_sid",
            visitorData = "VD1%3D%3D",
            dataSyncId = "111%7C%7C222",
            accountName = "First User",
            accountHandle = "@first"
        )
        assertEquals("VD1==", manager.getVisitorData())
        assertEquals("111||222", manager.getRawDataSyncId())

        manager.saveSession(
            cookie = "SAPISID=second_sapi; SID=second_sid",
            visitorData = "VD2%3D%3D",
            dataSyncId = null
        )
        assertNull(manager.getRawDataSyncId())
        assertNull(manager.accountState.value.accountName)
        assertNull(manager.accountState.value.accountHandle)
    }

    @Test
    fun personalAccountDataSyncIdOmitsPageIdHeaderWhileBrandAccountPreservesIt() {
        val manager = YouTubeAuthManager(
            store = InMemoryAuthStore(),
            cipher = JvmAesGcmCipher(),
            clockMs = { 1_700_000_000_000L }
        )

        // Personal account: DATASYNC_ID repeats user's own Gaia ID on both sides of "||"
        manager.saveSession(
            cookie = "SAPISID=personal_sapi; SID=personal_sid",
            visitorData = "VD_PERSONAL",
            dataSyncId = "10999888777||10999888777"
        )
        assertNull(manager.getDelegatedPageId())
        val personalHeaders = manager.buildAuthHeaders(timestampSeconds = 1_700_000_000L)
        assertFalse(personalHeaders.containsKey("X-Goog-PageId"))
        assertEquals("10999888777", manager.getOnBehalfOfUser())

        // Brand account: distinct delegated page ID before "||"
        manager.saveSession(
            cookie = "SAPISID=brand_sapi; SID=brand_sid",
            visitorData = "VD_BRAND",
            dataSyncId = "10555999||10999888777"
        )
        assertEquals("10555999", manager.getDelegatedPageId())
        val brandHeaders = manager.buildAuthHeaders(timestampSeconds = 1_700_000_000L)
        assertEquals("10555999", brandHeaders["X-Goog-PageId"])

        val noPageIdHeaders = manager.buildAuthHeaders(timestampSeconds = 1_700_000_000L, includePageId = false)
        assertFalse(noPageIdHeaders.containsKey("X-Goog-PageId"))
    }

    @Test
    fun parseAccountMenuHandlesGoogleAccountHeaderSimpleTextAccountItemAndRejectsLoggedOut() {
        val service = YouTubeMusicService()

        // 1. googleAccountHeaderRenderer variant (accounts without a YouTube channel)
        val googleHeaderJson = JSONObject().apply {
            put("actions", JSONArray().apply {
                put(JSONObject().apply {
                    put("openPopupAction", JSONObject().apply {
                        put("popup", JSONObject().apply {
                            put("multiPageMenuRenderer", JSONObject().apply {
                                put("header", JSONObject().apply {
                                    put("googleAccountHeaderRenderer", JSONObject().apply {
                                        put("name", runs("Jordan Lee"))
                                        put("email", JSONObject().put("simpleText", "jordan@example.com"))
                                        put("accountPhoto", JSONObject().apply {
                                            put("thumbnails", JSONArray().apply {
                                                put(JSONObject().put("url", "//yt3.ggpht.com/jordan=s88"))
                                            })
                                        })
                                    })
                                })
                            })
                        })
                    })
                })
            })
        }
        val parsedGoogle = service.parseAccountMenuResponse(googleHeaderJson)
        assertNotNull(parsedGoogle)
        assertTrue(parsedGoogle!!.isSignedIn)
        assertEquals("Jordan Lee", parsedGoogle.accountName)
        assertEquals("jordan@example.com", parsedGoogle.accountHandle)
        assertTrue(parsedGoogle.avatarUrl!!.startsWith("https://yt3.ggpht.com/jordan"))

        // 2. getAccountSwitcherEndpoint variant (`accountItem` + `simpleText` + selected item)
        val switcherJson = JSONObject().apply {
            put("code", "SUCCESS")
            put("data", JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("accountSectionListRenderer", JSONObject().apply {
                            put("contents", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("accountItemSectionRenderer", JSONObject().apply {
                                        put("contents", JSONArray().apply {
                                            put(JSONObject().apply {
                                                put("accountItem", JSONObject().apply {
                                                    put("accountName", JSONObject().put("simpleText", "Other Channel"))
                                                    put("isSelected", false)
                                                })
                                            })
                                            put(JSONObject().apply {
                                                put("accountItem", JSONObject().apply {
                                                    put("accountName", JSONObject().put("simpleText", "Main Studio"))
                                                    put("channelHandle", JSONObject().put("simpleText", "@mainstudio"))
                                                    put("isSelected", true)
                                                })
                                            })
                                        })
                                    })
                                })
                            })
                        })
                    })
                })
            })
        }
        val parsedSwitcher = service.parseAccountMenuResponse(switcherJson)
        assertNotNull(parsedSwitcher)
        assertEquals("Main Studio", parsedSwitcher!!.accountName)
        assertEquals("@mainstudio", parsedSwitcher.accountHandle)

        // 3. Logged-out menu with signInEndpoint must be rejected
        val loggedOutJson = JSONObject().apply {
            put("actions", JSONArray().apply {
                put(JSONObject().apply {
                    put("openPopupAction", JSONObject().apply {
                        put("popup", JSONObject().apply {
                            put("multiPageMenuRenderer", JSONObject().apply {
                                put("sections", JSONArray().apply {
                                    put(JSONObject().apply {
                                        put("multiPageMenuSectionRenderer", JSONObject().apply {
                                            put("items", JSONArray().apply {
                                                put(JSONObject().apply {
                                                    put("compactLinkRenderer", JSONObject().apply {
                                                        put("navigationEndpoint", JSONObject().apply {
                                                            put("signInEndpoint", JSONObject().put("hack", true))
                                                        })
                                                    })
                                                })
                                            })
                                        })
                                    })
                                })
                            })
                        })
                    })
                })
            })
        }
        assertNull(service.parseAccountMenuResponse(loggedOutJson))
    }

    @Test
    fun fetchAccountInfoRecoversViaRetryAndSwitcherFallbackAndRejectsExpiredCookies() = runTest {
        // Scenario A: Brand account DATASYNC_ID rejected with 400 on first account_menu call,
        // succeeds on clean retry without X-Goog-PageId / onBehalfOfUser, with required InnerTube headers.
        var menuCallCount = 0
        var firstSawPageId = false
        var secondSawPageId = false
        var sawClientHeaders = false

        val retryClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                val hasPageId = req.header("X-Goog-PageId") != null
                sawClientHeaders = req.header("X-Goog-Api-Format-Version") == "1" &&
                    req.header("X-YouTube-Client-Name") == "67" &&
                    !req.header("X-YouTube-Client-Version").isNullOrBlank() &&
                    req.header("X-Youtube-Bootstrap-Logged-In") == "true" &&
                    !req.header("Authorization").isNullOrBlank()
                menuCallCount++
                if (menuCallCount == 1) {
                    firstSawPageId = hasPageId
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(400)
                        .message("Bad Request")
                        .body("{\"error\":{\"code\":400}}".toResponseBody())
                        .build()
                } else {
                    secondSawPageId = hasPageId
                    val okBody = JSONObject().apply {
                        put("actions", JSONArray().apply {
                            put(JSONObject().apply {
                                put("openPopupAction", JSONObject().apply {
                                    put("popup", JSONObject().apply {
                                        put("multiPageMenuRenderer", JSONObject().apply {
                                            put("header", JSONObject().apply {
                                                put("activeAccountHeaderRenderer", JSONObject().apply {
                                                    put("accountName", runs("Elena Vance"))
                                                    put("channelHandle", runs("@elenavance"))
                                                })
                                            })
                                        })
                                    })
                                })
                            })
                        })
                    }.toString()
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(okBody.toResponseBody())
                        .build()
                }
            })
            .build()

        val retryAuth = YouTubeAuthManager(store = InMemoryAuthStore(), cipher = JvmAesGcmCipher())
        retryAuth.saveSession(
            cookie = "SID=valid_sid; __Secure-3PAPISID=valid_3p",
            visitorData = "VD_RETRY",
            dataSyncId = "10555999||10999888"
        )
        val retryService = YouTubeMusicService(
            httpClient = retryClient,
            accountMenuUrl = "https://music.youtube.com/youtubei/v1/account/account_menu?prettyPrint=false",
            accountSwitcherUrl = "https://music.youtube.com/getAccountSwitcherEndpoint",
            authManager = retryAuth
        )
        val retryResult = retryService.fetchAccountInfo()
        assertTrue(retryResult.isSignedIn)
        assertEquals("Elena Vance", retryResult.accountName)
        assertEquals("@elenavance", retryResult.accountHandle)
        assertTrue(sawClientHeaders)
        assertTrue(firstSawPageId)
        assertFalse(secondSawPageId)

        // Scenario B: account_menu lacks header, falls back to getAccountSwitcherEndpoint (`)]}'` + `accountItem` + `simpleText`)
        val switcherBody = ")]}'\n" + JSONObject().apply {
            put("code", "SUCCESS")
            put("data", JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("accountSectionListRenderer", JSONObject().apply {
                            put("contents", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("accountItemSectionRenderer", JSONObject().apply {
                                        put("contents", JSONArray().apply {
                                            put(JSONObject().apply {
                                                put("accountItem", JSONObject().apply {
                                                    put("accountName", JSONObject().put("simpleText", "Carlos Mendez"))
                                                    put("accountByline", JSONObject().put("simpleText", "carlos@example.com"))
                                                    put("isSelected", true)
                                                    put("serviceEndpoint", JSONObject().apply {
                                                        put("selectActiveIdentityEndpoint", JSONObject().apply {
                                                            put("supportedTokens", JSONArray().apply {
                                                                put(JSONObject().apply {
                                                                    put("datasyncIdToken", JSONObject().put("datasyncIdToken", "777||888"))
                                                                })
                                                            })
                                                        })
                                                    })
                                                })
                                            })
                                        })
                                    })
                                })
                            })
                        })
                    })
                })
            })
        }.toString()

        val switcherClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                val body = if (req.url.encodedPath.contains("account_menu")) {
                    JSONObject().apply {
                        put("actions", JSONArray().apply {
                            put(JSONObject().apply {
                                put("openPopupAction", JSONObject().apply {
                                    put("popup", JSONObject().apply {
                                        put("multiPageMenuRenderer", JSONObject().put("sections", JSONArray()))
                                    })
                                })
                            })
                        })
                    }.toString()
                } else {
                    assertNull(req.header("X-Goog-PageId"))
                    switcherBody
                }
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody())
                    .build()
            })
            .build()

        val switcherAuth = YouTubeAuthManager(store = InMemoryAuthStore(), cipher = JvmAesGcmCipher())
        switcherAuth.saveSession(cookie = "SAPISID=sapi_ok; SID=sid_ok", visitorData = "VD_SW")
        val switcherService = YouTubeMusicService(
            httpClient = switcherClient,
            accountMenuUrl = "https://music.youtube.com/youtubei/v1/account/account_menu?prettyPrint=false",
            accountSwitcherUrl = "https://music.youtube.com/getAccountSwitcherEndpoint",
            authManager = switcherAuth
        )
        val switcherResult = switcherService.fetchAccountInfo()
        assertTrue(switcherResult.isSignedIn)
        assertEquals("Carlos Mendez", switcherResult.accountName)
        assertEquals("carlos@example.com", switcherResult.accountHandle)
        assertEquals("777||888", switcherAuth.getRawDataSyncId())

        // Scenario C: Expired cookies (401 on account_menu & non-JSON redirect/401 on switcher) must be rejected
        val expiredClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(401)
                    .message("Unauthorized")
                    .body("{\"error\":{\"code\":401}}".toResponseBody())
                    .build()
            })
            .build()

        val expiredAuth = YouTubeAuthManager(store = InMemoryAuthStore(), cipher = JvmAesGcmCipher())
        expiredAuth.saveSession(cookie = "SAPISID=expired_sapi; SID=expired_sid", visitorData = "VD_EXP")
        val expiredService = YouTubeMusicService(
            httpClient = expiredClient,
            accountMenuUrl = "https://music.youtube.com/youtubei/v1/account/account_menu?prettyPrint=false",
            accountSwitcherUrl = "https://music.youtube.com/getAccountSwitcherEndpoint",
            authManager = expiredAuth
        )
        val expiredResult = expiredService.fetchAccountInfo()
        assertFalse(expiredResult.isSignedIn)
        assertNull(expiredResult.accountName)
    }

    @Test
    fun parseArtistCarouselShelfDeduplicatesItemsAndUsesIndexedBlankSimilarIds() {
        val service = YouTubeMusicService(authManager = YouTubeAuthManager(store = InMemoryAuthStore(), cipher = JvmAesGcmCipher()))
        val studioAlbums = mutableListOf<com.xperience.player.domain.model.AlbumItem>()
        val singles = mutableListOf<com.xperience.player.domain.model.AlbumItem>()
        val playlists = mutableListOf<com.xperience.player.domain.model.AlbumItem>()
        val similarArtists = mutableListOf<com.xperience.player.domain.model.ArtistItem>()

        val albumCarousel = carouselShelf(
            title = "Albums",
            items = JSONArray().apply {
                put(twoRowBrowseItem("Album One", "2024", "MPREb_DUP", "MUSIC_PAGE_TYPE_ALBUM"))
                put(twoRowBrowseItem("Album One", "2024", "MPREb_DUP", "MUSIC_PAGE_TYPE_ALBUM"))
                put(twoRowBrowseItem("Album Two", "2025", "MPREb_TWO", "MUSIC_PAGE_TYPE_ALBUM"))
            }
        ).getJSONObject("musicCarouselShelfRenderer")
        service.parseArtistCarouselShelf(albumCarousel, "Albums", "Artist", studioAlbums, singles, playlists, similarArtists)
        assertEquals(listOf("ytm_alb_MPREb_DUP", "ytm_alb_MPREb_TWO"), studioAlbums.map { it.id })

        val similarCarousel = carouselShelf(
            title = "Fans might also like",
            items = JSONArray().apply {
                put(twoRowBrowseItem("Alpha", "1M monthly listeners", "", "MUSIC_PAGE_TYPE_ARTIST"))
                put(twoRowBrowseItem("Beta", "2M monthly listeners", "UC_BETA", "MUSIC_PAGE_TYPE_ARTIST"))
                put(twoRowBrowseItem("Beta Duplicate", "2M monthly listeners", "UC_BETA", "MUSIC_PAGE_TYPE_ARTIST"))
                put(twoRowBrowseItem("Gamma", "500K listeners", "", "MUSIC_PAGE_TYPE_ARTIST"))
            }
        ).getJSONObject("musicCarouselShelfRenderer")
        service.parseArtistCarouselShelf(similarCarousel, "Fans might also like", "Artist", studioAlbums, singles, playlists, similarArtists)
        assertEquals(3, similarArtists.size)
        assertTrue(similarArtists[0].id.startsWith("ytm_sim_0_"))
        assertEquals("ytm_sim_UC_BETA", similarArtists[1].id)
        assertTrue(similarArtists[2].id.startsWith("ytm_sim_3_"))
        assertEquals(3, similarArtists.map { it.id }.distinct().size)
    }

    private fun runs(text: String) = JSONObject().apply {
        put("runs", JSONArray().apply {
            put(JSONObject().put("text", text))
        })
    }

    private fun carouselShelf(title: String, items: JSONArray) = JSONObject().apply {
        put("musicCarouselShelfRenderer", JSONObject().apply {
            put("header", JSONObject().apply {
                put("musicCarouselShelfBasicHeaderRenderer", JSONObject().apply {
                    put("title", runs(title))
                })
            })
            put("contents", items)
        })
    }

    private fun responsiveTrackItem(
        videoId: String,
        title: String,
        artist: String,
        album: String,
        duration: String
    ) = JSONObject().apply {
        put("musicResponsiveListItemRenderer", JSONObject().apply {
            put("playlistItemData", JSONObject().put("videoId", videoId))
            put("flexColumns", JSONArray().apply {
                put(JSONObject().put("musicResponsiveListItemFlexColumnRenderer", JSONObject().put("text", runs(title))))
                put(JSONObject().put("musicResponsiveListItemFlexColumnRenderer", JSONObject().apply {
                    put("text", JSONObject().apply {
                        put("runs", JSONArray().apply {
                            put(JSONObject().put("text", artist))
                            put(JSONObject().put("text", " • "))
                            put(JSONObject().put("text", album))
                            put(JSONObject().put("text", " • "))
                            put(JSONObject().put("text", duration))
                        })
                    })
                }))
            })
        })
    }

    private fun responsivePlaylistTrackItem(
        videoId: String,
        title: String,
        artist: String,
        album: String,
        duration: String
    ) = JSONObject().apply {
        put("musicResponsiveListItemRenderer", JSONObject().apply {
            put("playlistItemData", JSONObject().put("videoId", videoId))
            put("flexColumns", JSONArray().apply {
                put(JSONObject().put("musicResponsiveListItemFlexColumnRenderer", JSONObject().put("text", runs(title))))
                put(JSONObject().put("musicResponsiveListItemFlexColumnRenderer", JSONObject().put("text", runs(artist))))
                put(JSONObject().put("musicResponsiveListItemFlexColumnRenderer", JSONObject().put("text", runs(album))))
            })
            put("fixedColumns", JSONArray().apply {
                put(JSONObject().put("musicResponsiveListItemFixedColumnRenderer", JSONObject().put("text", runs(duration))))
            })
        })
    }

    private fun twoRowBrowseItem(
        title: String,
        subtitle: String,
        browseId: String,
        pageType: String
    ) = JSONObject().apply {
        put("musicTwoRowItemRenderer", JSONObject().apply {
            put("title", runs(title))
            put("subtitle", runs(subtitle))
            put("navigationEndpoint", JSONObject().apply {
                put("browseEndpoint", JSONObject().apply {
                    put("browseId", browseId)
                    put("browseEndpointContextSupportedConfigs", JSONObject().apply {
                        put("browseEndpointContextMusicConfig", JSONObject().apply {
                            put("pageType", pageType)
                        })
                    })
                })
            })
        })
    }

    private fun twoRowWatchItem(
        title: String,
        subtitle: String,
        videoId: String,
        playlistId: String
    ) = JSONObject().apply {
        put("musicTwoRowItemRenderer", JSONObject().apply {
            put("title", runs(title))
            put("subtitle", runs(subtitle))
            put("navigationEndpoint", JSONObject().apply {
                put("watchEndpoint", JSONObject().apply {
                    put("videoId", videoId)
                    put("playlistId", playlistId)
                })
            })
        })
    }
}
