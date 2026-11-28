package com.xperience.player.service

import com.xperience.player.domain.model.TrackItem
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MusicVideoSyncAndFilterTest {

    @Test
    fun segmentMap_mapsSongToVideoAndBackAcrossIntroOffset() {
        val meta = OfficialMusicVideoMetadata(
            atvVideoId = "atv12345678",
            omvVideoId = "omv87654321",
            title = "Test Song",
            artist = "Test Artist",
            segments = listOf(
                MusicVideoTimeSegment(
                    primaryStartTimeMs = 0L,
                    counterpartStartTimeMs = 11_000L,
                    durationMs = 180_000L
                )
            )
        )

        // Song at 0:12 (12,000 ms) -> Video at 0:23 (23,000 ms)
        assertEquals(23_000L, meta.mapSongToVideoPosition(12_000L))
        // Video at 0:23 (23,000 ms) -> Song at 0:12 (12,000 ms)
        assertEquals(12_000L, meta.mapVideoToSongPosition(23_000L))
        // During 11-second intro skit in video (0:05) -> pins lyrics cleanly to Song 0:00
        assertEquals(0L, meta.mapVideoToSongPosition(5_000L))
    }

    @Test
    fun parseCounterpartWrapperMetadata_extractsOmvIdAndSegmentMap() {
        val nextJson = JSONObject().apply {
            put(
                "playlistPanelVideoWrapperRenderer",
                JSONObject().apply {
                    put(
                        "primaryRenderer",
                        JSONObject().put(
                            "playlistPanelVideoRenderer",
                            JSONObject().put("videoId", "aaaaaaaaaaa")
                        )
                    )
                    put(
                        "counterpart",
                        JSONArray().put(
                            JSONObject().put(
                                "counterpartRenderer",
                                JSONObject().apply {
                                    put(
                                        "playlistPanelVideoRenderer",
                                        JSONObject().apply {
                                            put("videoId", "bbbbbbbbbbb")
                                            put(
                                                "navigationEndpoint",
                                                JSONObject().put(
                                                    "watchEndpoint",
                                                    JSONObject().put(
                                                        "watchEndpointMusicSupportedConfigs",
                                                        JSONObject().put(
                                                            "watchEndpointMusicConfig",
                                                            JSONObject().put("musicVideoType", "MUSIC_VIDEO_TYPE_OMV")
                                                        )
                                                    )
                                                )
                                            )
                                        }
                                    )
                                    put(
                                        "segmentMap",
                                        JSONObject().put(
                                            "segment",
                                            JSONArray().put(
                                                JSONObject().apply {
                                                    put("primaryVideoStartTimeMilliseconds", "0")
                                                    put("counterpartVideoStartTimeMilliseconds", "11000")
                                                    put("durationMilliseconds", "195000")
                                                }
                                            )
                                        )
                                    )
                                }
                            )
                        )
                    )
                }
            )
        }

        val service = YouTubeMusicService.getInstance()
        val meta = service.parseCounterpartWrapperMetadata(
            node = nextJson,
            sourceVideoId = "aaaaaaaaaaa",
            fallbackTitle = "Test Song",
            fallbackArtist = "Test Artist"
        )
        assertNotNull(meta)
        assertEquals("bbbbbbbbbbb", meta!!.omvVideoId)
        assertEquals("aaaaaaaaaaa", meta.atvVideoId)
        assertEquals(1, meta.segments.size)
        assertEquals(23_000L, meta.mapSongToVideoPosition(12_000L))
    }

    @Test
    fun parseCounterpartWrapperMetadata_rejectsUgcNonOfficialCounterparts() {
        val ugcOnlyJson = JSONObject().apply {
            put(
                "playlistPanelVideoWrapperRenderer",
                JSONObject().apply {
                    put(
                        "primaryRenderer",
                        JSONObject().put(
                            "playlistPanelVideoRenderer",
                            JSONObject().put("videoId", "aaaaaaaaaaa")
                        )
                    )
                    put(
                        "counterpart",
                        JSONArray().put(
                            JSONObject().put(
                                "counterpartRenderer",
                                JSONObject().put(
                                    "playlistPanelVideoRenderer",
                                    JSONObject().apply {
                                        put("videoId", "ccccccccccc")
                                        put(
                                            "navigationEndpoint",
                                            JSONObject().put(
                                                "watchEndpoint",
                                                JSONObject().put(
                                                    "watchEndpointMusicSupportedConfigs",
                                                    JSONObject().put(
                                                        "watchEndpointMusicConfig",
                                                        JSONObject().put("musicVideoType", "MUSIC_VIDEO_TYPE_UGC")
                                                    )
                                                )
                                            )
                                        )
                                    }
                                )
                            )
                        )
                    )
                }
            )
        }

        val service = YouTubeMusicService.getInstance()
        val meta = service.parseCounterpartWrapperMetadata(
            node = ugcOnlyJson,
            sourceVideoId = "aaaaaaaaaaa"
        )
        assertNull(meta)
    }

    @Test
    fun parseCounterpartWrapperMetadata_neverStealsNextQueueTrackWrapper() {
        // Index 0: Primadonna (sourceVideoId = "primadonna1") has NO wrapper (plain playlistPanelVideoRenderer)
        // Index 1: Busy Girl (primaryId = "busygirl001", counterpart = "busygirlOMV") HAS a wrapper
        val nextPanelJson = JSONObject().apply {
            put(
                "playlistPanelRenderer",
                JSONObject().put(
                    "contents",
                    JSONArray().apply {
                        put(
                            JSONObject().put(
                                "playlistPanelVideoRenderer",
                                JSONObject().apply {
                                    put("videoId", "primadonna1")
                                    put("title", JSONObject().put("runs", JSONArray().put(JSONObject().put("text", "Primadonna"))))
                                }
                            )
                        )
                        put(
                            JSONObject().put(
                                "playlistPanelVideoWrapperRenderer",
                                JSONObject().apply {
                                    put(
                                        "primaryRenderer",
                                        JSONObject().put(
                                            "playlistPanelVideoRenderer",
                                            JSONObject().apply {
                                                put("videoId", "busygirl001")
                                                put("title", JSONObject().put("runs", JSONArray().put(JSONObject().put("text", "Busy Girl"))))
                                            }
                                        )
                                    )
                                    put(
                                        "counterpart",
                                        JSONArray().put(
                                            JSONObject().put(
                                                "counterpartRenderer",
                                                JSONObject().put(
                                                    "playlistPanelVideoRenderer",
                                                    JSONObject().apply {
                                                        put("videoId", "busygirlOMV")
                                                        put(
                                                            "navigationEndpoint",
                                                            JSONObject().put(
                                                                "watchEndpoint",
                                                                JSONObject().put(
                                                                    "watchEndpointMusicSupportedConfigs",
                                                                    JSONObject().put(
                                                                        "watchEndpointMusicConfig",
                                                                        JSONObject().put("musicVideoType", "MUSIC_VIDEO_TYPE_OMV")
                                                                    )
                                                                )
                                                            )
                                                        )
                                                    }
                                                )
                                            )
                                        )
                                    )
                                }
                            )
                        )
                    }
                )
            )
        }

        val service = YouTubeMusicService.getInstance()
        val meta = service.parseCounterpartWrapperMetadata(
            node = nextPanelJson,
            sourceVideoId = "primadonna1",
            fallbackTitle = "Primadonna",
            fallbackArtist = "MARINA"
        )
        assertNull("Must never steal the next track's OMV counterpart", meta)
    }

    @Test
    fun parseCounterpartWrapperMetadata_matchesOnlySourceVideoWhenMultipleWrappersPresent() {
        val nextPanelJson = JSONObject().apply {
            put(
                "contents",
                JSONArray().apply {
                    // Index 0: unrelated track wrapper
                    put(
                        JSONObject().put(
                            "playlistPanelVideoWrapperRenderer",
                            JSONObject().apply {
                                put(
                                    "primaryRenderer",
                                    JSONObject().put(
                                        "playlistPanelVideoRenderer",
                                        JSONObject().put("videoId", "otherTrack1")
                                    )
                                )
                                put(
                                    "counterpart",
                                    JSONArray().put(
                                        JSONObject().put(
                                            "counterpartRenderer",
                                            JSONObject().put(
                                                "playlistPanelVideoRenderer",
                                                JSONObject().apply {
                                                    put("videoId", "otherOmv001")
                                                    put(
                                                        "navigationEndpoint",
                                                        JSONObject().put(
                                                            "watchEndpoint",
                                                            JSONObject().put(
                                                                "watchEndpointMusicSupportedConfigs",
                                                                JSONObject().put(
                                                                    "watchEndpointMusicConfig",
                                                                    JSONObject().put("musicVideoType", "MUSIC_VIDEO_TYPE_OMV")
                                                                )
                                                            )
                                                        )
                                                    )
                                                }
                                            )
                                        )
                                    )
                                )
                            }
                        )
                    )
                    // Index 1: target track wrapper
                    put(
                        JSONObject().put(
                            "playlistPanelVideoWrapperRenderer",
                            JSONObject().apply {
                                put(
                                    "primaryRenderer",
                                    JSONObject().put(
                                        "playlistPanelVideoRenderer",
                                        JSONObject().put("videoId", "primadonna1")
                                    )
                                )
                                put(
                                    "counterpart",
                                    JSONArray().put(
                                        JSONObject().put(
                                            "counterpartRenderer",
                                            JSONObject().put(
                                                "playlistPanelVideoRenderer",
                                                JSONObject().apply {
                                                    put("videoId", "primadonnaV")
                                                    put(
                                                        "navigationEndpoint",
                                                        JSONObject().put(
                                                            "watchEndpoint",
                                                            JSONObject().put(
                                                                "watchEndpointMusicSupportedConfigs",
                                                                JSONObject().put(
                                                                    "watchEndpointMusicConfig",
                                                                    JSONObject().put("musicVideoType", "MUSIC_VIDEO_TYPE_OMV")
                                                                )
                                                            )
                                                        )
                                                    )
                                                }
                                            )
                                        )
                                    )
                                )
                            }
                        )
                    )
                }
            )
        }

        val service = YouTubeMusicService.getInstance()
        val meta = service.parseCounterpartWrapperMetadata(
            node = nextPanelJson,
            sourceVideoId = "primadonna1",
            fallbackTitle = "Primadonna",
            fallbackArtist = "MARINA"
        )
        assertNotNull(meta)
        assertEquals("primadonna1", meta!!.atvVideoId)
        assertEquals("primadonnaV", meta.omvVideoId)
    }

    @Test
    fun parseTrackAlbumFromNextJson_extractsAlbumOnlyForSourceVideoId() {
        val nextPanelJson = JSONObject().apply {
            put(
                "contents",
                JSONArray().apply {
                    put(
                        JSONObject().put(
                            "playlistPanelVideoRenderer",
                            JSONObject().apply {
                                put("videoId", "otherTrack1")
                                put(
                                    "longBylineText",
                                    JSONObject().put(
                                        "runs",
                                        JSONArray().put(
                                            JSONObject().apply {
                                                put("text", "Wrong Album")
                                                put(
                                                    "navigationEndpoint",
                                                    JSONObject().put(
                                                        "browseEndpoint",
                                                        JSONObject().put("browseId", "MPREb_wrong001")
                                                    )
                                                )
                                            }
                                        )
                                    )
                                )
                            }
                        )
                    )
                    put(
                        JSONObject().put(
                            "playlistPanelVideoRenderer",
                            JSONObject().apply {
                                put("videoId", "primadonna1")
                                put(
                                    "longBylineText",
                                    JSONObject().put(
                                        "runs",
                                        JSONArray().put(
                                            JSONObject().apply {
                                                put("text", "Electra Heart")
                                                put(
                                                    "navigationEndpoint",
                                                    JSONObject().put(
                                                        "browseEndpoint",
                                                        JSONObject().put("browseId", "MPREb_electra1")
                                                    )
                                                )
                                            }
                                        )
                                    )
                                )
                            }
                        )
                    )
                }
            )
        }

        val service = YouTubeMusicService.getInstance()
        val album = service.parseTrackAlbumFromNextJson(
            node = nextPanelJson,
            sourceVideoId = "primadonna1",
            fallbackArtist = "MARINA"
        )
        assertNotNull(album)
        assertEquals("Electra Heart", album!!.title)
        assertEquals("ytm_alb_MPREb_electra1", album.id)
    }
}
