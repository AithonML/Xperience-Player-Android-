package com.xperience.player.service

import com.xperience.player.domain.model.ArtistItem
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.ui.components.resolveGoToAlbumLabel
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistAndWordmarkFixTest {

    private val appleMusicCanvasService = AppleMusicCanvasService()
    private val youTubeMusicService = YouTubeMusicService()

    @Test
    fun normalizeAppleWordmarkUrl_forcesTransparentPng800x266AndFixesWhiteBoxJpgs() {
        val marinaWhiteJpg =
            "https://is1-ssl.mzstatic.com/image/thumb/Features126/v4/11/22/33/wordmark/800x266bb.jpg"
        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/Features126/v4/11/22/33/wordmark/800x266bb.png",
            appleMusicCanvasService.normalizeAppleWordmarkUrl(marinaWhiteJpg)
        )

        val maeMullerLowerResWebp =
            "https://is1-ssl.mzstatic.com/image/thumb/Music221/v4/aa/bb/cc/logo/400x133bb.webp"
        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/Music221/v4/aa/bb/cc/logo/800x266bb.png",
            appleMusicCanvasService.normalizeAppleWordmarkUrl(maeMullerLowerResWebp)
        )

        val validPng =
            "https://is1-ssl.mzstatic.com/image/thumb/Music221/v4/aa/bb/cc/logo/800x266bb.png"
        assertEquals(validPng, appleMusicCanvasService.normalizeAppleWordmarkUrl(validPng))
    }

    @Test
    fun exactArtistSearchSelection_prefersSheenaOverSheenaRingo() {
        val candidates = listOf(
            YouTubeMusicService.ArtistSearchCandidate(
                name = "Sheena Ringo",
                browseId = "UC_sheena_ringo_12345678",
                avatarUrl = null
            ),
            YouTubeMusicService.ArtistSearchCandidate(
                name = "Sheena",
                browseId = "UC_sheena_exact_87654321",
                avatarUrl = null
            )
        )

        val picked = youTubeMusicService.selectExactArtistSearchCandidate(candidates, "Sheena")
        assertEquals("UC_sheena_exact_87654321", picked?.browseId)
        assertEquals("Sheena", picked?.name)
    }

    @Test
    fun collectArtistSearchCandidatesRecursive_handlesBrowseIdsWithoutRedirecting() {
        val searchJson = JSONObject().apply {
            put(
                "contents",
                JSONArray().apply {
                    put(
                        JSONObject().put(
                            "musicResponsiveListItemRenderer",
                            JSONObject().apply {
                                put(
                                    "navigationEndpoint",
                                    JSONObject().put(
                                        "browseEndpoint",
                                        JSONObject().put("browseId", "UC_sheena_ringo_001")
                                    )
                                )
                                put(
                                    "flexColumns",
                                    JSONArray().put(
                                        JSONObject().put(
                                            "musicResponsiveListItemFlexColumnRenderer",
                                            JSONObject().put(
                                                "text",
                                                JSONObject().put(
                                                    "runs",
                                                    JSONArray().put(JSONObject().put("text", "Sheena Ringo"))
                                                )
                                            )
                                        )
                                    )
                                )
                            }
                        )
                    )
                    put(
                        JSONObject().put(
                            "musicResponsiveListItemRenderer",
                            JSONObject().apply {
                                put(
                                    "navigationEndpoint",
                                    JSONObject().put(
                                        "browseEndpoint",
                                        JSONObject().put("browseId", "UC_sheena_exact_002")
                                    )
                                )
                                put(
                                    "flexColumns",
                                    JSONArray().put(
                                        JSONObject().put(
                                            "musicResponsiveListItemFlexColumnRenderer",
                                            JSONObject().put(
                                                "text",
                                                JSONObject().put(
                                                    "runs",
                                                    JSONArray().put(JSONObject().put("text", "Sheena"))
                                                )
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

        val collected = mutableListOf<YouTubeMusicService.ArtistSearchCandidate>()
        youTubeMusicService.collectArtistSearchCandidatesRecursive(searchJson, collected)
        val picked = youTubeMusicService.selectExactArtistSearchCandidate(collected, "Sheena")
        assertEquals("UC_sheena_exact_002", picked?.browseId)
    }

    @Test
    fun artistItem_extractChannelBrowseId_validatesStrictUcFormat() {
        val withExplicitBrowse = ArtistItem(
            id = "ytm_art_1",
            name = "Sheena",
            browseId = "UC_exact_channel_123456"
        )
        assertEquals("UC_exact_channel_123456", withExplicitBrowse.extractChannelBrowseId())

        val invalidArtist = ArtistItem(
            id = "local_art_1",
            name = "Local Artist",
            browseId = "MPREb_not_channel"
        )
        assertNull(invalidArtist.extractChannelBrowseId())
        assertTrue(ArtistItem.isValidChannelBrowseId("UCxxxxxxxxxxxxxxxxxxxxxx"))
        assertFalse(ArtistItem.isValidChannelBrowseId("FEmusic_library"))
    }

    @Test
    fun resolveGoToAlbumLabel_showsExplicitAlbumOrGoToAlbumForHomeAndNowPlayingTracks() {
        val searchTrackWithAlbum = TrackItem(
            id = "ytm_primadonna1",
            title = "Primadonna",
            artist = "MARINA",
            album = "Electra Heart"
        )
        assertEquals("Go to Electra Heart", resolveGoToAlbumLabel(searchTrackWithAlbum))

        val homeSingleTrack = TrackItem(
            id = "ytm_primadonna1",
            title = "Primadonna",
            artist = "MARINA",
            album = "Single"
        )
        assertEquals("Go to Album", resolveGoToAlbumLabel(homeSingleTrack))

        val nowPlayingBlankAlbumTrack = TrackItem(
            id = "ytm_primadonna1",
            title = "Primadonna",
            artist = "MARINA",
            album = ""
        )
        assertEquals("Go to Album", resolveGoToAlbumLabel(nowPlayingBlankAlbumTrack))
    }
}
