package com.xperience.player.service

import com.xperience.player.domain.model.AlbumItem
import com.xperience.player.domain.model.ArtistItem
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeMusicAlbumFilterTest {

    private val service = YouTubeMusicService()

    @Test
    fun testVideosShelfIgnoredAndNeverAddedToAlbums() {
        val studioAlbums = mutableListOf<AlbumItem>()
        val singlesAndEps = mutableListOf<AlbumItem>()
        val artistPlaylists = mutableListOf<AlbumItem>()
        val similarArtists = mutableListOf<ArtistItem>()

        val videoCarousel = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("musicTwoRowItemRenderer", JSONObject().apply {
                        put("aspectRatio", "MUSIC_TWO_ROW_ITEM_THUMBNAIL_ASPECT_RATIO_RECTANGLE_16_9")
                        put("title", JSONObject().apply {
                            put("runs", JSONArray().apply {
                                put(JSONObject().apply { put("text", "HOT TO GO!") })
                            })
                        })
                        put("subtitle", JSONObject().apply {
                            put("runs", JSONArray().apply {
                                put(JSONObject().apply { put("text", "Chappell Roan • 81M views") })
                            })
                        })
                        put("navigationEndpoint", JSONObject().apply {
                            put("watchEndpoint", JSONObject().apply {
                                put("videoId", "xaPNR-_Cfn0")
                            })
                        })
                    })
                })
            })
        }

        service.parseArtistCarouselShelf(
            carousel = videoCarousel,
            shelfTitle = "Videos",
            artistName = "Chappell Roan",
            studioAlbums = studioAlbums,
            singlesAndEps = singlesAndEps,
            artistPlaylists = artistPlaylists,
            similarArtists = similarArtists
        )

        assertTrue("Videos shelf must NOT add anything to studioAlbums", studioAlbums.isEmpty())
        assertTrue("Videos shelf must NOT add anything to singlesAndEps", singlesAndEps.isEmpty())
    }

    @Test
    fun testLivePerformancesShelfIgnored() {
        val studioAlbums = mutableListOf<AlbumItem>()
        val singlesAndEps = mutableListOf<AlbumItem>()
        val artistPlaylists = mutableListOf<AlbumItem>()
        val similarArtists = mutableListOf<ArtistItem>()

        val liveCarousel = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("musicTwoRowItemRenderer", JSONObject().apply {
                        put("title", JSONObject().apply {
                            put("runs", JSONArray().apply {
                                put(JSONObject().apply { put("text", "Pink Pony Club (Live)") })
                            })
                        })
                        put("subtitle", JSONObject().apply {
                            put("runs", JSONArray().apply {
                                put(JSONObject().apply { put("text", "Chappell Roan • 6.6M views") })
                            })
                        })
                    })
                })
            })
        }

        service.parseArtistCarouselShelf(
            carousel = liveCarousel,
            shelfTitle = "Live performances",
            artistName = "Chappell Roan",
            studioAlbums = studioAlbums,
            singlesAndEps = singlesAndEps,
            artistPlaylists = artistPlaylists,
            similarArtists = similarArtists
        )

        assertTrue("Live performances must NOT be added to albums", studioAlbums.isEmpty())
    }

    @Test
    fun testAlbumsShelfStrictlyParsesStudioAlbums() {
        val studioAlbums = mutableListOf<AlbumItem>()
        val singlesAndEps = mutableListOf<AlbumItem>()
        val artistPlaylists = mutableListOf<AlbumItem>()
        val similarArtists = mutableListOf<ArtistItem>()

        val albumsCarousel = JSONObject().apply {
            put("contents", JSONArray().apply {
                // 1. Valid album
                put(JSONObject().apply {
                    put("musicTwoRowItemRenderer", JSONObject().apply {
                        put("aspectRatio", "MUSIC_TWO_ROW_ITEM_THUMBNAIL_ASPECT_RATIO_SQUARE")
                        put("title", JSONObject().apply {
                            put("runs", JSONArray().apply {
                                put(JSONObject().apply { put("text", "The Rise and Fall of a Midwest Princess") })
                            })
                        })
                        put("subtitle", JSONObject().apply {
                            put("runs", JSONArray().apply {
                                put(JSONObject().apply { put("text", "2023") })
                            })
                        })
                        put("navigationEndpoint", JSONObject().apply {
                            put("browseEndpoint", JSONObject().apply {
                                put("browseId", "MPREb_PIreit0oHuz")
                            })
                        })
                    })
                })
                // 2. Video item inside (should be skipped)
                put(JSONObject().apply {
                    put("musicTwoRowItemRenderer", JSONObject().apply {
                        put("aspectRatio", "MUSIC_TWO_ROW_ITEM_THUMBNAIL_ASPECT_RATIO_RECTANGLE_16_9")
                        put("title", JSONObject().apply {
                            put("runs", JSONArray().apply {
                                put(JSONObject().apply { put("text", "HOT TO GO! (Official Video)") })
                            })
                        })
                        put("subtitle", JSONObject().apply {
                            put("runs", JSONArray().apply {
                                put(JSONObject().apply { put("text", "81M views") })
                            })
                        })
                        put("navigationEndpoint", JSONObject().apply {
                            put("watchEndpoint", JSONObject().apply {
                                put("videoId", "xaPNR-_Cfn0")
                            })
                        })
                    })
                })
            })
        }

        service.parseArtistCarouselShelf(
            carousel = albumsCarousel,
            shelfTitle = "Albums",
            artistName = "Chappell Roan",
            studioAlbums = studioAlbums,
            singlesAndEps = singlesAndEps,
            artistPlaylists = artistPlaylists,
            similarArtists = similarArtists
        )

        assertEquals(1, studioAlbums.size)
        val album = studioAlbums[0]
        assertEquals("The Rise and Fall of a Midwest Princess", album.title)
        assertEquals("Chappell Roan", album.artist)
        assertEquals(2023, album.year)
        assertEquals("ytm_alb_MPREb_PIreit0oHuz", album.id)
    }

    @Test
    fun testSinglesAndEpsShelfStrictlyParsesSingles() {
        val studioAlbums = mutableListOf<AlbumItem>()
        val singlesAndEps = mutableListOf<AlbumItem>()
        val artistPlaylists = mutableListOf<AlbumItem>()
        val similarArtists = mutableListOf<ArtistItem>()

        val singlesCarousel = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("musicTwoRowItemRenderer", JSONObject().apply {
                        put("aspectRatio", "MUSIC_TWO_ROW_ITEM_THUMBNAIL_ASPECT_RATIO_SQUARE")
                        put("title", JSONObject().apply {
                            put("runs", JSONArray().apply {
                                put(JSONObject().apply { put("text", "HOT TO GO!") })
                            })
                        })
                        put("subtitle", JSONObject().apply {
                            put("runs", JSONArray().apply {
                                put(JSONObject().apply { put("text", "Single • 2023") })
                            })
                        })
                        put("navigationEndpoint", JSONObject().apply {
                            put("browseEndpoint", JSONObject().apply {
                                put("browseId", "MPREb_R086YlzvLVW")
                            })
                        })
                    })
                })
            })
        }

        service.parseArtistCarouselShelf(
            carousel = singlesCarousel,
            shelfTitle = "Singles & EPs",
            artistName = "Chappell Roan",
            studioAlbums = studioAlbums,
            singlesAndEps = singlesAndEps,
            artistPlaylists = artistPlaylists,
            similarArtists = similarArtists
        )

        assertTrue("Singles must not go into studioAlbums", studioAlbums.isEmpty())
        assertEquals(1, singlesAndEps.size)
        assertEquals("HOT TO GO!", singlesAndEps[0].title)
        assertEquals(2023, singlesAndEps[0].year)
    }

    @Test
    fun testParseResponsiveAlbumItemStrictlyAlbumsOnly() {
        val albumRenderer = JSONObject().apply {
            put("flexColumns", JSONArray().apply {
                put(JSONObject().apply {
                    put("musicResponsiveListItemFlexColumnRenderer", JSONObject().apply {
                        put("text", JSONObject().apply {
                            put("runs", JSONArray().apply {
                                put(JSONObject().apply { put("text", "The Rise and Fall of a Midwest Princess") })
                            })
                        })
                    })
                })
                put(JSONObject().apply {
                    put("musicResponsiveListItemFlexColumnRenderer", JSONObject().apply {
                        put("text", JSONObject().apply {
                            put("runs", JSONArray().apply {
                                put(JSONObject().apply { put("text", "Album") })
                                put(JSONObject().apply { put("text", " • ") })
                                put(JSONObject().apply { put("text", "Chappell Roan") })
                                put(JSONObject().apply { put("text", " • ") })
                                put(JSONObject().apply { put("text", "2023") })
                            })
                        })
                    })
                })
            })
            put("navigationEndpoint", JSONObject().apply {
                put("browseEndpoint", JSONObject().apply {
                    put("browseId", "MPREb_PIreit0oHuz")
                })
            })
        }

        val parsedAlbum = service.parseResponsiveAlbumItem(albumRenderer, strictlyAlbumsOnly = true)
        assertNotNull(parsedAlbum)
        assertEquals("The Rise and Fall of a Midwest Princess", parsedAlbum?.title)
        assertEquals("Chappell Roan", parsedAlbum?.artist)
        assertEquals(2023, parsedAlbum?.year)

        val singleRenderer = JSONObject().apply {
            put("flexColumns", JSONArray().apply {
                put(JSONObject().apply {
                    put("musicResponsiveListItemFlexColumnRenderer", JSONObject().apply {
                        put("text", JSONObject().apply {
                            put("runs", JSONArray().apply {
                                put(JSONObject().apply { put("text", "HOT TO GO!") })
                            })
                        })
                    })
                })
                put(JSONObject().apply {
                    put("musicResponsiveListItemFlexColumnRenderer", JSONObject().apply {
                        put("text", JSONObject().apply {
                            put("runs", JSONArray().apply {
                                put(JSONObject().apply { put("text", "Single") })
                                put(JSONObject().apply { put("text", " • ") })
                                put(JSONObject().apply { put("text", "Chappell Roan") })
                                put(JSONObject().apply { put("text", " • ") })
                                put(JSONObject().apply { put("text", "2023") })
                            })
                        })
                    })
                })
            })
            put("navigationEndpoint", JSONObject().apply {
                put("browseEndpoint", JSONObject().apply {
                    put("browseId", "MPREb_R086YlzvLVW")
                })
            })
        }

        val parsedSingle = service.parseResponsiveAlbumItem(singleRenderer, strictlyAlbumsOnly = true)
        assertNull("When strictlyAlbumsOnly is true, Single item should be null", parsedSingle)
    }
}
