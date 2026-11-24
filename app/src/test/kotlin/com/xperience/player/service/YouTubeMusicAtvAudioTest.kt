package com.xperience.player.service

import com.xperience.player.domain.model.PlaybackRepeatMode
import com.xperience.player.domain.model.PlaybackState
import com.xperience.player.domain.model.RadioStationItem
import com.xperience.player.domain.model.TrackItem
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class YouTubeMusicAtvAudioTest {

    private fun jsonResponse(chain: Interceptor.Chain, json: JSONObject): Response {
        return Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(json.toString().toResponseBody())
            .build()
    }

    private fun readRequestJson(chain: Interceptor.Chain): JSONObject {
        val buffer = Buffer()
        chain.request().body?.writeTo(buffer)
        val raw = buffer.readUtf8()
        return if (raw.isBlank()) JSONObject() else JSONObject(raw)
    }

    private fun runs(vararg pairs: Pair<String, JSONObject?>): JSONObject {
        val array = JSONArray()
        pairs.forEach { (text, navEp) ->
            val run = JSONObject().put("text", text)
            if (navEp != null) {
                run.put("navigationEndpoint", navEp)
            }
            array.put(run)
        }
        return JSONObject().put("runs", array)
    }

    private fun flexCol(textObj: JSONObject): JSONObject {
        return JSONObject().put(
            "musicResponsiveListItemFlexColumnRenderer",
            JSONObject().put("text", textObj)
        )
    }

    private fun fixedCol(duration: String): JSONObject {
        return JSONObject().put(
            "musicResponsiveListItemFixedColumnRenderer",
            JSONObject().put("text", runs(duration to null))
        )
    }

    private fun trackCreditsMenu(atvVideoId: String): JSONObject {
        return JSONObject().put(
            "menuRenderer",
            JSONObject().put(
                "items",
                JSONArray().put(
                    JSONObject().put(
                        "menuNavigationItemRenderer",
                        JSONObject().put(
                            "navigationEndpoint",
                            JSONObject().put(
                                "browseEndpoint",
                                JSONObject().put("browseId", "MPTC$atvVideoId")
                            )
                        )
                    )
                )
            )
        )
    }

    private fun watchEndpoint(videoId: String, musicVideoType: String? = null, playlistId: String? = null): JSONObject {
        val watch = JSONObject().put("videoId", videoId)
        if (!playlistId.isNullOrBlank()) {
            watch.put("playlistId", playlistId)
        }
        if (!musicVideoType.isNullOrBlank()) {
            watch.put(
                "watchEndpointMusicSupportedConfigs",
                JSONObject().put(
                    "watchEndpointMusicConfig",
                    JSONObject().put("musicVideoType", musicVideoType)
                )
            )
        }
        return JSONObject().put("watchEndpoint", watch)
    }

    private fun responsiveTrackRenderer(
        title: String,
        subtitleRuns: JSONObject,
        videoId: String,
        musicVideoType: String? = null,
        creditsAtvId: String? = null,
        thumbUrl: String? = null,
        fixedDuration: String? = null,
        col2Text: String? = null
    ): JSONObject {
        val flexColumns = JSONArray().apply {
            put(flexCol(runs(title to watchEndpoint(videoId, musicVideoType))))
            put(flexCol(subtitleRuns))
            if (!col2Text.isNullOrBlank()) {
                put(flexCol(runs(col2Text to null)))
            }
        }
        return JSONObject().apply {
            put("playlistItemData", JSONObject().put("videoId", videoId))
            put("flexColumns", flexColumns)
            if (!fixedDuration.isNullOrBlank()) {
                put("fixedColumns", JSONArray().put(fixedCol(fixedDuration)))
            }
            if (!creditsAtvId.isNullOrBlank()) {
                put("menu", trackCreditsMenu(creditsAtvId))
            }
            if (!thumbUrl.isNullOrBlank()) {
                put(
                    "thumbnail",
                    JSONObject().put(
                        "musicThumbnailRenderer",
                        JSONObject().put(
                            "thumbnail",
                            JSONObject().put(
                                "thumbnails",
                                JSONArray().put(JSONObject().put("url", thumbUrl))
                            )
                        )
                    )
                )
            }
        }
    }

    @Test
    fun albumDetailUsesTrackCreditsMptcOverOmvPlaylistItemData() = runTest {
        val browseJson = JSONObject().apply {
            put(
                "header",
                JSONObject().put(
                    "musicResponsiveHeaderRenderer",
                    JSONObject().apply {
                        put("title", runs("Noche" to null))
                        put("straplineTextOne", runs("Peces Raros" to null))
                        put("subtitle", runs("Album • 2023" to null))
                        put(
                            "thumbnail",
                            JSONObject().put(
                                "musicThumbnailRenderer",
                                JSONObject().put(
                                    "thumbnail",
                                    JSONObject().put(
                                        "thumbnails",
                                        JSONArray().put(
                                            JSONObject().put("url", "https://lh3.googleusercontent.com/square-art=w540-h540")
                                        )
                                    )
                                )
                            )
                        )
                    }
                )
            )
            put(
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
                                                    responsiveTrackRenderer(
                                                        title = "Óxido",
                                                        subtitleRuns = runs("Peces Raros" to null),
                                                        videoId = "cmFEMLWs-tI",
                                                        musicVideoType = "MUSIC_VIDEO_TYPE_OMV",
                                                        creditsAtvId = "Z0yIxtonLzA",
                                                        fixedDuration = "4:12"
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
        }

        val client = OkHttpClient.Builder()
            .addInterceptor { chain -> jsonResponse(chain, browseJson) }
            .build()

        val service = YouTubeMusicService(httpClient = client, authManager = null)
        val detail = service.getAlbumDetail(
            albumId = "MPREb_osHPI2eqKyL",
            albumTitle = "Noche",
            artistName = "Peces Raros"
        )

        assertNotNull(detail)
        assertEquals(1, detail!!.tracks.size)
        val track = detail.tracks.first()
        assertEquals("ytm_Z0yIxtonLzA", track.id)
        assertEquals("https://www.youtube.com/watch?v=Z0yIxtonLzA", track.audioUrl)
        assertEquals(252_000L, track.durationMs)
        assertTrue(track.artworkUri.orEmpty().startsWith("https://lh3.googleusercontent.com/square-art"))
    }

    @Test
    fun playlistBrowseStrips16x9VideoFrameThumbnailWhenOmvPromotedToAtv() {
        val headerCover = "https://lh3.googleusercontent.com/playlist-square=w540-h540-l90-rj"
        val playlistJson = JSONObject().apply {
            put(
                "header",
                JSONObject().put(
                    "musicResponsiveHeaderRenderer",
                    JSONObject().put(
                        "thumbnail",
                        JSONObject().put(
                            "musicThumbnailRenderer",
                            JSONObject().put(
                                "thumbnail",
                                JSONObject().put(
                                    "thumbnails",
                                    JSONArray().put(JSONObject().put("url", headerCover))
                                )
                            )
                        )
                    )
                )
            )
            put(
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
                                        "musicPlaylistShelfRenderer",
                                        JSONObject().put(
                                            "contents",
                                            JSONArray().put(
                                                JSONObject().put(
                                                    "musicResponsiveListItemRenderer",
                                                    responsiveTrackRenderer(
                                                        title = "No Time For Caution",
                                                        subtitleRuns = runs(
                                                            "Hans Zimmer" to null,
                                                            " • " to null,
                                                            "12M views" to null
                                                        ),
                                                        videoId = "CjNfYUQm7LI",
                                                        musicVideoType = "MUSIC_VIDEO_TYPE_OMV",
                                                        creditsAtvId = "F4WUNbUUwIw",
                                                        thumbUrl = "https://i.ytimg.com/vi/CjNfYUQm7LI/hq720.jpg",
                                                        col2Text = "3.4M visualizaciones",
                                                        fixedDuration = "4:06"
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
        }

        val service = YouTubeMusicService(authManager = null)
        val tracks = service.parsePlaylistBrowseTracks(playlistJson)

        assertEquals(1, tracks.size)
        assertEquals("ytm_F4WUNbUUwIw", tracks[0].id)
        assertEquals("Single", tracks[0].album)
        assertFalse(tracks[0].artworkUri.orEmpty().contains("i.ytimg.com/vi/"))
        assertTrue(tracks[0].artworkUri.orEmpty().startsWith("https://lh3.googleusercontent.com/playlist-square"))
    }

    @Test
    fun responsiveSearchDiscardsPureOmvUgcAndPrivateVideoItemsWithoutMptc() = runTest {
        val searchJson = JSONObject().put(
            "contents",
            JSONObject().put(
                "musicShelfRenderer",
                JSONObject().put(
                    "contents",
                    JSONArray().apply {
                        put(
                            JSONObject().put(
                                "musicResponsiveListItemRenderer",
                                responsiveTrackRenderer(
                                    title = "OMV Without Credits",
                                    subtitleRuns = runs("Artist A" to null, " • " to null, "10M views" to null),
                                    videoId = "omvNoCredit1",
                                    musicVideoType = "MUSIC_VIDEO_TYPE_OMV"
                                )
                            )
                        )
                        put(
                            JSONObject().put(
                                "musicResponsiveListItemRenderer",
                                responsiveTrackRenderer(
                                    title = "UGC Without Credits",
                                    subtitleRuns = runs("Uploader" to null),
                                    videoId = "yXmnmvDl-ao",
                                    musicVideoType = "MUSIC_VIDEO_TYPE_UGC"
                                )
                            )
                        )
                        put(
                            JSONObject().put(
                                "musicResponsiveListItemRenderer",
                                responsiveTrackRenderer(
                                    title = "Private Without Credits",
                                    subtitleRuns = runs("Private Artist" to null),
                                    videoId = "privTrack12",
                                    musicVideoType = "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"
                                )
                            )
                        )
                        put(
                            JSONObject().put(
                                "musicResponsiveListItemRenderer",
                                responsiveTrackRenderer(
                                    title = "OMV With MPTC Credits",
                                    subtitleRuns = runs(
                                        "Peces Raros" to null,
                                        " • " to null,
                                        "Noche" to JSONObject().put(
                                            "browseEndpoint",
                                            JSONObject().put("browseId", "MPREb_osHPI2eqKyL")
                                        ),
                                        " • " to null,
                                        "1.8M plays" to null
                                    ),
                                    videoId = "cmFEMLWs-tI",
                                    musicVideoType = "MUSIC_VIDEO_TYPE_OMV",
                                    creditsAtvId = "Z0yIxtonLzA",
                                    fixedDuration = "3:55"
                                )
                            )
                        )
                        put(
                            JSONObject().put(
                                "musicResponsiveListItemRenderer",
                                responsiveTrackRenderer(
                                    title = "Pure ATV Song",
                                    subtitleRuns = runs("Peces Raros" to null, " • " to null, "450K reproducciones" to null),
                                    videoId = "aNCnoWC-2Ic",
                                    musicVideoType = "MUSIC_VIDEO_TYPE_ATV",
                                    fixedDuration = "3:18"
                                )
                            )
                        )
                    }
                )
            )
        )

        val client = OkHttpClient.Builder()
            .addInterceptor { chain -> jsonResponse(chain, searchJson) }
            .build()

        val service = YouTubeMusicService(httpClient = client, authManager = null)
        val songs = service.searchSongs("peces raros")

        assertEquals(listOf("ytm_Z0yIxtonLzA", "ytm_aNCnoWC-2Ic"), songs.map { it.id })
        assertEquals("Noche", songs[0].album)
        assertEquals("Single", songs[1].album)
    }

    @Test
    fun homeFeedFiltersVideoOnlyTwoRowCardsAndPlayCountLabels() = runTest {
        fun twoRowCard(
            title: String,
            subtitle: String,
            videoId: String,
            musicVideoType: String? = null,
            creditsAtvId: String? = null
        ): JSONObject {
            return JSONObject().put(
                "musicTwoRowItemRenderer",
                JSONObject().apply {
                    put("title", runs(title to null))
                    put("subtitle", runs(subtitle to null))
                    put("navigationEndpoint", watchEndpoint(videoId, musicVideoType))
                    if (!creditsAtvId.isNullOrBlank()) {
                        put("menu", trackCreditsMenu(creditsAtvId))
                    }
                }
            )
        }

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
                                        JSONArray().apply {
                                            put(
                                                JSONObject().put(
                                                    "musicCarouselShelfRenderer",
                                                    JSONObject().apply {
                                                        put(
                                                            "header",
                                                            JSONObject().put(
                                                                "musicCarouselShelfBasicHeaderRenderer",
                                                                JSONObject().put("title", runs("Quick picks" to null))
                                                            )
                                                        )
                                                        put(
                                                            "contents",
                                                            JSONArray().apply {
                                                                put(
                                                                    twoRowCard(
                                                                        title = "Pure Video Only",
                                                                        subtitle = "Artist • 8M views",
                                                                        videoId = "omvHome0001",
                                                                        musicVideoType = "MUSIC_VIDEO_TYPE_OMV"
                                                                    )
                                                                )
                                                                put(
                                                                    twoRowCard(
                                                                        title = "Video Subtitle No MVT",
                                                                        subtitle = "Video • Artist",
                                                                        videoId = "vidSub00002"
                                                                    )
                                                                )
                                                                put(
                                                                    twoRowCard(
                                                                        title = "Promoted Via MPTC",
                                                                        subtitle = "12M visualizaciones • Vistas",
                                                                        videoId = "cmFEMLWs-tI",
                                                                        musicVideoType = "MUSIC_VIDEO_TYPE_OMV",
                                                                        creditsAtvId = "Z0yIxtonLzA"
                                                                    )
                                                                )
                                                            }
                                                        )
                                                    }
                                                )
                                            )
                                            put(
                                                JSONObject().put(
                                                    "musicCarouselShelfRenderer",
                                                    JSONObject().apply {
                                                        put(
                                                            "header",
                                                            JSONObject().put(
                                                                "musicCarouselShelfBasicHeaderRenderer",
                                                                JSONObject().put("title", runs("Music videos for you" to null))
                                                            )
                                                        )
                                                        put(
                                                            "contents",
                                                            JSONArray().put(
                                                                twoRowCard(
                                                                    title = "Shelf Video Card",
                                                                    subtitle = "Some Artist",
                                                                    videoId = "mvShelf0003"
                                                                )
                                                            )
                                                        )
                                                    }
                                                )
                                            )
                                        }
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        val client = OkHttpClient.Builder()
            .addInterceptor { chain -> jsonResponse(chain, homeJson) }
            .build()

        val service = YouTubeMusicService(httpClient = client, authManager = null)
        val feed = service.fetchPersonalizedHomeFeed()

        assertEquals(listOf("ytm_Z0yIxtonLzA"), feed.personalizedTracks.map { it.id })
        assertEquals("Vistas", feed.personalizedTracks[0].artist)
        assertTrue(feed.newReleases.none { it.videoId == "mvShelf0003" })
        assertFalse(YouTubeMusicService.isPlayCountLabel("Vistas"))
        assertTrue(YouTubeMusicService.isPlayCountLabel("12M vistas"))
        assertTrue(YouTubeMusicService.isPlayCountLabel("3.5M views"))
        assertTrue(YouTubeMusicService.isPlayCountLabel("420K plays"))
        assertTrue(YouTubeMusicService.isPlayCountLabel("89K reproducciones"))
        assertTrue(YouTubeMusicService.isPlayCountLabel("1.1M visualizaciones"))
    }

    @Test
    fun resolveCanonicalAtvVideoIdPromotesOmvUnauthenticatedPersistsCacheAndDeletesStaleM4a() = runTest {
        val tempRoot = Files.createTempDirectory("atv_resolve_test").toFile()
        val cacheFile = File(tempRoot, "OnlineCache/atv_counterparts_cache.json")
        val audioDir = File(tempRoot, "audio_cache").apply { mkdirs() }
        val staleOmvFile = File(audioDir, "cmFEMLWs-tI.m4a").apply { writeBytes(ByteArray(1024)) }

        var playerCalls = 0
        var searchCalls = 0

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val reqJson = readRequestJson(chain)
                when {
                    url.contains("/player") -> {
                        playerCalls++
                        assertEquals("cmFEMLWs-tI", reqJson.optString("videoId"))
                        assertTrue(reqJson.optBoolean("contentCheckOk"))
                        assertTrue(reqJson.optBoolean("racyCheckOk"))
                        assertNull(chain.request().header("Cookie"))
                        jsonResponse(
                            chain,
                            JSONObject().put(
                                "videoDetails",
                                JSONObject().apply {
                                    put("videoId", "cmFEMLWs-tI")
                                    put("musicVideoType", "MUSIC_VIDEO_TYPE_OMV")
                                    put("title", "La Noche (Official Music Video)")
                                    put("author", "Peces Raros - Topic")
                                }
                            )
                        )
                    }
                    url.contains("/search") -> {
                        searchCalls++
                        val query = reqJson.optString("query")
                        assertTrue(query.contains("Peces Raros"))
                        assertTrue(query.contains("La Noche"))
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
                                                responsiveTrackRenderer(
                                                    title = "La Noche",
                                                    subtitleRuns = runs("Peces Raros" to null),
                                                    videoId = "Z0yIxtonLzA",
                                                    musicVideoType = "MUSIC_VIDEO_TYPE_ATV"
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    }
                    else -> jsonResponse(chain, JSONObject())
                }
            }
            .build()

        val service = YouTubeMusicService(
            httpClient = client,
            authManager = null,
            atvCacheFileOverride = cacheFile,
            audioCacheDirOverride = audioDir
        )

        val resolvedFirst = service.resolveCanonicalAtvVideoId("cmFEMLWs-tI")
        assertEquals("Z0yIxtonLzA", resolvedFirst)
        assertEquals(1, playerCalls)
        assertEquals(1, searchCalls)
        assertFalse("Stale OMV audio file must be deleted on ATV promotion", staleOmvFile.exists())
        assertTrue(cacheFile.exists())
        assertEquals("Z0yIxtonLzA", YouTubeMusicService.loadAtvCounterpartMapSafe(cacheFile)["cmFEMLWs-tI"])

        // Second call uses in-memory cache (0 extra HTTP calls)
        assertEquals("Z0yIxtonLzA", service.resolveCanonicalAtvVideoId("cmFEMLWs-tI"))
        assertEquals(1, playerCalls)

        // Fresh service instance pointing to the same cache file resolves directly from disk cache
        val coldRestartService = YouTubeMusicService(
            httpClient = client,
            authManager = null,
            atvCacheFileOverride = cacheFile,
            audioCacheDirOverride = audioDir
        )
        assertEquals("Z0yIxtonLzA", coldRestartService.resolveCanonicalAtvVideoId("cmFEMLWs-tI"))
        assertEquals(1, playerCalls)

        tempRoot.deleteRecursively()
    }

    @Test
    fun resolveCanonicalAtvVideoIdHandlesUgcArtistTitleSplittingAndRejectsFalsePositives() = runTest {
        val tempRoot = Files.createTempDirectory("atv_ugc_test").toFile()
        val cacheFile = File(tempRoot, "atv_counterparts_cache.json")

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val reqJson = readRequestJson(chain)
                when {
                    url.contains("/player") -> {
                        when (reqJson.optString("videoId")) {
                            "yXmnmvDl-ao" -> jsonResponse(
                                chain,
                                JSONObject().put(
                                    "videoDetails",
                                    JSONObject().apply {
                                        put("musicVideoType", "MUSIC_VIDEO_TYPE_UGC")
                                        put("title", "Nightcall")
                                        put("author", "RandomFanChannel")
                                    }
                                )
                            )
                            "7KwZYD5lbWM" -> jsonResponse(
                                chain,
                                JSONObject().put(
                                    "videoDetails",
                                    JSONObject().apply {
                                        put("musicVideoType", "MUSIC_VIDEO_TYPE_UGC")
                                        put("title", "Indiscutible (Live Session)")
                                        put("author", "UnrelatedUploader")
                                    }
                                )
                            )
                            "ugcSplit001" -> jsonResponse(
                                chain,
                                JSONObject().put(
                                    "videoDetails",
                                    JSONObject().apply {
                                        put("musicVideoType", "MUSIC_VIDEO_TYPE_UGC")
                                        put("title", "Peces Raros - La Noche (Official Video)")
                                        put("author", "RandomFanUploader")
                                    }
                                )
                            )
                            else -> jsonResponse(chain, JSONObject())
                        }
                    }
                    url.contains("/search") -> {
                        val query = reqJson.optString("query")
                        val results = JSONArray().apply {
                            when {
                                query.contains("Nightcall") -> put(
                                    JSONObject().put(
                                        "musicResponsiveListItemRenderer",
                                        responsiveTrackRenderer(
                                            title = "Nightcall",
                                            subtitleRuns = runs("Kavinsky" to null),
                                            videoId = "MV_3D_q3K7M",
                                            musicVideoType = "MUSIC_VIDEO_TYPE_ATV"
                                        )
                                    )
                                )
                                query.contains("Indiscutible") -> put(
                                    JSONObject().put(
                                        "musicResponsiveListItemRenderer",
                                        responsiveTrackRenderer(
                                            title = "Indiscutible",
                                            subtitleRuns = runs("Different Artist" to null),
                                            videoId = "wrongAtv123",
                                            musicVideoType = "MUSIC_VIDEO_TYPE_ATV"
                                        )
                                    )
                                )
                                query.contains("Peces Raros") -> put(
                                    JSONObject().put(
                                        "musicResponsiveListItemRenderer",
                                        responsiveTrackRenderer(
                                            title = "La Noche",
                                            subtitleRuns = runs("Peces Raros" to null),
                                            videoId = "Z0yIxtonLzA",
                                            musicVideoType = "MUSIC_VIDEO_TYPE_ATV"
                                        )
                                    )
                                )
                            }
                        }
                        jsonResponse(
                            chain,
                            JSONObject().put(
                                "contents",
                                JSONObject().put(
                                    "musicShelfRenderer",
                                    JSONObject().put("contents", results)
                                )
                            )
                        )
                    }
                    else -> jsonResponse(chain, JSONObject())
                }
            }
            .build()

        val service = YouTubeMusicService(
            httpClient = client,
            authManager = null,
            atvCacheFileOverride = cacheFile
        )

        // False-positive UGC items without artist match must NOT be hijacked
        assertEquals("yXmnmvDl-ao", service.resolveCanonicalAtvVideoId("yXmnmvDl-ao"))
        assertEquals("7KwZYD5lbWM", service.resolveCanonicalAtvVideoId("7KwZYD5lbWM"))

        // UGC titled "Artist - Song (Official Video)" splits artist/title and resolves canonical ATV
        assertEquals("Z0yIxtonLzA", service.resolveCanonicalAtvVideoId("ugcSplit001"))

        tempRoot.deleteRecursively()
    }

    @Test
    fun resolveAudioStreamUrlPromotesOmvToAtvBeforeRequestingStream() = runTest {
        val streamPlayerVideoIds = mutableListOf<String>()
        val tempRoot = Files.createTempDirectory("atv_stream_test").toFile()

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val reqJson = readRequestJson(chain)
                when {
                    url.startsWith("https://music.youtube.com/youtubei/v1/player") -> jsonResponse(
                        chain,
                        JSONObject().put(
                            "videoDetails",
                            JSONObject().apply {
                                put("musicVideoType", "MUSIC_VIDEO_TYPE_OMV")
                                put("title", "La Noche")
                                put("author", "Peces Raros")
                            }
                        )
                    )
                    url.contains("/search") -> jsonResponse(
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
                                            responsiveTrackRenderer(
                                                title = "La Noche",
                                                subtitleRuns = runs("Peces Raros" to null),
                                                videoId = "Z0yIxtonLzA",
                                                musicVideoType = "MUSIC_VIDEO_TYPE_ATV"
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                    url.contains("sw.js_data") -> Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""[null, "CgtMOCK_VISITOR_DATA_STRING_EXCEEDING_FIFTY_CHARACTERS_0001"]""".toResponseBody())
                        .build()
                    url.startsWith("https://www.youtube.com/youtubei/v1/player") -> {
                        streamPlayerVideoIds.add(reqJson.optString("videoId"))
                        jsonResponse(
                            chain,
                            JSONObject().put(
                                "streamingData",
                                JSONObject().put(
                                    "adaptiveFormats",
                                    JSONArray().put(
                                        JSONObject().apply {
                                            put("mimeType", """audio/mp4; codecs="mp4a.40.2"""")
                                            put("bitrate", 131072)
                                            put("url", "https://rr1---sn.googlevideo.com/videoplayback?id=Z0yIxtonLzA")
                                        }
                                    )
                                )
                            )
                        )
                    }
                    else -> jsonResponse(chain, JSONObject())
                }
            }
            .build()

        val service = YouTubeMusicService(
            httpClient = client,
            authManager = null,
            atvCacheFileOverride = File(tempRoot, "atv_cache.json")
        )
        val resolvedUrl = service.resolveAudioStreamUrl("ytm_cmFEMLWs-tI")

        assertEquals("https://rr1---sn.googlevideo.com/videoplayback?id=Z0yIxtonLzA", resolvedUrl)
        assertEquals(listOf("Z0yIxtonLzA"), streamPlayerVideoIds)

        tempRoot.deleteRecursively()
    }

    @Test
    fun resolveRadioStationPromotesSeedToAtvSendsAudioOnlyAndExtractsPanelMptc() = runTest {
        var capturedNextBody: JSONObject? = null
        val tempRoot = Files.createTempDirectory("atv_radio_test").toFile()

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val reqJson = readRequestJson(chain)
                when {
                    url.contains("/player") -> jsonResponse(
                        chain,
                        JSONObject().put(
                            "videoDetails",
                            JSONObject().apply {
                                put("musicVideoType", "MUSIC_VIDEO_TYPE_OMV")
                                put("title", "La Noche")
                                put("author", "Peces Raros")
                            }
                        )
                    )
                    url.contains("/search") -> jsonResponse(
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
                                            responsiveTrackRenderer(
                                                title = "La Noche",
                                                subtitleRuns = runs("Peces Raros" to null),
                                                videoId = "Z0yIxtonLzA",
                                                musicVideoType = "MUSIC_VIDEO_TYPE_ATV"
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                    url.contains("/next") -> {
                        capturedNextBody = reqJson
                        val nextPanelJson = JSONObject().put(
                            "playlistPanelRenderer",
                            JSONObject().put(
                                "contents",
                                JSONArray().put(
                                    JSONObject().put(
                                        "playlistPanelVideoRenderer",
                                        JSONObject().apply {
                                            put("videoId", "CjNfYUQm7LI")
                                            put("title", runs("Indiscutible" to null))
                                            put(
                                                "longBylineText",
                                                runs(
                                                    "Peces Raros" to null,
                                                    " • " to null,
                                                    "2.1M views" to null,
                                                    " • " to null,
                                                    "2023" to null
                                                )
                                            )
                                            put("lengthText", runs("3:40" to null))
                                            put("menu", trackCreditsMenu("F4WUNbUUwIw"))
                                        }
                                    )
                                )
                            )
                        )
                        jsonResponse(chain, nextPanelJson)
                    }
                    else -> jsonResponse(chain, JSONObject())
                }
            }
            .build()

        val service = YouTubeMusicService(
            httpClient = client,
            authManager = null,
            atvCacheFileOverride = File(tempRoot, "atv_cache.json")
        )
        val tracks = service.resolveRadioStationTracks(
            RadioStationItem(
                id = "radio_RDAMVMcmFEMLWs-tI",
                title = "La Noche Radio",
                subtitle = "Peces Raros",
                artworkUri = null,
                playlistId = "RDAMVMcmFEMLWs-tI",
                seedVideoId = "cmFEMLWs-tI"
            )
        )

        assertNotNull(capturedNextBody)
        assertTrue(capturedNextBody!!.optBoolean("isAudioOnly"))
        assertEquals("Z0yIxtonLzA", capturedNextBody.optString("videoId"))
        assertEquals("RDAMVMZ0yIxtonLzA", capturedNextBody.optString("playlistId"))

        assertEquals(1, tracks.size)
        assertEquals("ytm_F4WUNbUUwIw", tracks[0].id)
        assertEquals("Single", tracks[0].album)
        assertEquals("Peces Raros", tracks[0].artist)

        tempRoot.deleteRecursively()
    }

    @Test
    fun artistTopTrackUsesMptcIgnoresPlayCountAsAlbumAndReadsAlbumRun() {
        val topTrackRenderer = responsiveTrackRenderer(
            title = "Cicuta",
            subtitleRuns = runs(
                "Peces Raros" to JSONObject().put(
                    "browseEndpoint",
                    JSONObject().put("browseId", "UC_peces_raros")
                ),
                " • " to null,
                "4.2M plays" to null
            ),
            videoId = "cmFEMLWs-tI",
            musicVideoType = "MUSIC_VIDEO_TYPE_OMV",
            creditsAtvId = "aNCnoWC-2Ic",
            col2Text = "Anestesia",
            fixedDuration = "3:44"
        )

        val topTrackPlayCountOnly = responsiveTrackRenderer(
            title = "Deseo",
            subtitleRuns = runs("850K reproducciones" to null),
            videoId = "aNCnoWC-2Ic",
            musicVideoType = "MUSIC_VIDEO_TYPE_ATV",
            fixedDuration = "3:12"
        )

        val service = YouTubeMusicService(authManager = null)
        val parsedWithAlbum = service.parseArtistTopTrack(topTrackRenderer, "Peces Raros")
        val parsedPlayCountOnly = service.parseArtistTopTrack(topTrackPlayCountOnly, "Peces Raros")

        assertNotNull(parsedWithAlbum)
        assertEquals("ytm_aNCnoWC-2Ic", parsedWithAlbum!!.id)
        assertEquals("Anestesia", parsedWithAlbum.album)
        assertEquals(4_200_000L, parsedWithAlbum.playCount)
        assertEquals(224_000L, parsedWithAlbum.durationMs)

        assertNotNull(parsedPlayCountOnly)
        assertEquals("Single", parsedPlayCountOnly!!.album)
        assertEquals("Peces Raros", parsedPlayCountOnly.artist)
        assertEquals(850_000L, parsedPlayCountOnly.playCount)
    }

    @Test
    fun persistedOnlineTrackMigrationRewritesIdsDeduplicatesAndCleansPlayCountAlbums() {
        val counterparts = mapOf(
            "cmFEMLWs-tI" to "Z0yIxtonLzA",
            "CjNfYUQm7LI" to "F4WUNbUUwIw"
        )
        val savedTracks = listOf(
            TrackItem(
                id = "ytm_cmFEMLWs-tI",
                title = "La Noche",
                artist = "Peces Raros",
                album = "3.5M views",
                audioUrl = "https://www.youtube.com/watch?v=cmFEMLWs-tI"
            ),
            TrackItem(
                id = "ytm_Z0yIxtonLzA",
                title = "La Noche",
                artist = "Peces Raros",
                album = "Noche",
                audioUrl = "https://www.youtube.com/watch?v=Z0yIxtonLzA"
            ),
            TrackItem(
                id = "ytm_CjNfYUQm7LI",
                title = "Indiscutible",
                artist = "Peces Raros",
                album = "480K visualizaciones",
                audioUrl = null
            ),
            TrackItem(
                id = "ytm_aNCnoWC-2Ic",
                title = "Cicuta",
                artist = "Peces Raros",
                album = "Noche",
                audioUrl = "https://www.youtube.com/watch?v=aNCnoWC-2Ic"
            )
        )

        val (sanitized, modified) = YouTubeMusicService.sanitizePersistedTracks(savedTracks, counterparts)

        assertTrue(modified)
        assertEquals(3, sanitized.size)
        assertEquals("ytm_Z0yIxtonLzA", sanitized[0].id)
        assertEquals("Single", sanitized[0].album)
        assertEquals("https://www.youtube.com/watch?v=Z0yIxtonLzA", sanitized[0].audioUrl)

        assertEquals("ytm_F4WUNbUUwIw", sanitized[1].id)
        assertEquals("Single", sanitized[1].album)
        assertEquals("https://www.youtube.com/watch?v=F4WUNbUUwIw", sanitized[1].audioUrl)

        assertEquals("ytm_aNCnoWC-2Ic", sanitized[2].id)
        assertEquals("Noche", sanitized[2].album)
    }

    @Test
    fun naturalDurationSyncRespectsLoadingGuardAndSyncsCurrentTrackAndQueue() {
        val track = TrackItem(
            id = "ytm_Z0yIxtonLzA",
            title = "La Noche",
            artist = "Peces Raros",
            album = "Noche",
            durationMs = 210_000L
        )
        val controller = PlaybackQueueController()
        val snapshot = controller.setQueue(listOf(track), startIndex = 0)
        val initialState = PlaybackState(
            currentTrack = snapshot.currentTrack,
            isPlaying = true,
            currentPositionMs = 0L,
            durationMs = track.durationMs,
            repeatMode = PlaybackRepeatMode.Off,
            queue = snapshot.queue,
            currentQueueIndex = snapshot.currentIndex
        )

        // While still loading new media item, stale previous duration must be ignored
        val ignoredWhileLoading = AudioPlayerService.computeUpdatedPlaybackState(
            currentState = initialState,
            naturalDurationMs = 95_000L,
            isLoadingMedia = true
        )
        assertEquals(210_000L, ignoredWhileLoading.durationMs)
        assertEquals(210_000L, ignoredWhileLoading.currentTrack?.durationMs)

        // Differences <= 1000ms update playbackState.durationMs without rewriting queue/track
        assertNull(controller.updateCurrentTrackDuration(210_800L))
        val smallDiffState = AudioPlayerService.computeUpdatedPlaybackState(
            currentState = initialState,
            naturalDurationMs = 210_800L,
            isLoadingMedia = false
        )
        assertEquals(210_800L, smallDiffState.durationMs)
        assertEquals(210_000L, smallDiffState.currentTrack?.durationMs)

        // Differences > 1000ms update playbackState.durationMs, currentTrack.durationMs, and active queue item
        val updatedQueueSnapshot = controller.updateCurrentTrackDuration(252_000L)
        assertNotNull(updatedQueueSnapshot)
        val updatedState = AudioPlayerService.computeUpdatedPlaybackState(
            currentState = initialState,
            naturalDurationMs = 252_000L,
            isLoadingMedia = false,
            updatedQueue = updatedQueueSnapshot!!.queue,
            updatedCurrentIndex = updatedQueueSnapshot.currentIndex
        )
        assertEquals(252_000L, updatedState.durationMs)
        assertEquals(252_000L, updatedState.currentTrack?.durationMs)
        assertEquals(252_000L, updatedState.queue[0].durationMs)
    }
}
