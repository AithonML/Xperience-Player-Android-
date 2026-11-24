package com.xperience.player.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistDetailProfileTest {

    @Test
    fun testArtistDetailProfileProperties() {
        val emptyProfile = ArtistDetailProfile(name = "MARINA")
        assertFalse(emptyProfile.hasCustomWordmark)
        assertFalse(emptyProfile.hasBanner)
        assertFalse(emptyProfile.hasVideo)
        assertFalse(emptyProfile.isHeaderMediaLoading)
        assertFalse(emptyProfile.isCatalogLoading)
        assertEquals("", emptyProfile.keyColorRgb)
        assertEquals("Subscribers", emptyProfile.displaySubscribersPillText)

        val fullProfile = ArtistDetailProfile(
            name = "MARINA",
            avatarUri = "https://example.com/avatar.jpg",
            bannerImageUri = "https://example.com/banner.jpg",
            customTitleLogoUri = "https://example.com/wordmark.png",
            headerVideoUri = "https://example.com/header_video.m3u8",
            subscribersCountText = "2.84M",
            monthlyListenersText = "17.9M monthly audience",
            biography = "MARINA is a Welsh singer-songwriter..."
        )

        assertTrue(fullProfile.hasCustomWordmark)
        assertTrue(fullProfile.hasBanner)
        assertTrue(fullProfile.hasVideo)
        assertEquals("Subscribers 2.84M", fullProfile.displaySubscribersPillText)
        assertEquals("17.9M monthly audience", fullProfile.monthlyListenersText)
    }

    @Test
    fun testTrackPlayCountFormatting() {
        val trackMillions = TrackItem(
            id = "ytm_1",
            title = "Bubblegum Bitch",
            artist = "MARINA",
            album = "Electra Heart",
            durationMs = 155000L,
            playCount = 329000000L
        )
        assertEquals("329M plays", trackMillions.formattedPlayCount)
        assertEquals("2:35", trackMillions.formattedDuration)

        val trackBillions = TrackItem(
            id = "ytm_2",
            title = "Popular Song",
            artist = "Artist",
            album = "Hit Album",
            durationMs = 210000L,
            playCount = 1500000000L
        )
        assertEquals("2B plays", trackBillions.formattedPlayCount)
        assertEquals("3:30", trackBillions.formattedDuration)

        val trackThousands = TrackItem(
            id = "ytm_3",
            title = "Indie Song",
            artist = "Artist",
            album = "Single",
            durationMs = 65000L,
            playCount = 45000L
        )
        assertEquals("45K plays", trackThousands.formattedPlayCount)
        assertEquals("1:05", trackThousands.formattedDuration)

        val trackZero = TrackItem(
            id = "local_1",
            title = "Local Song",
            artist = "Artist",
            album = "Album",
            durationMs = 120000L,
            playCount = 0L
        )
        assertEquals("", trackZero.formattedPlayCount)
        assertEquals("2:00", trackZero.formattedDuration)
    }
}
