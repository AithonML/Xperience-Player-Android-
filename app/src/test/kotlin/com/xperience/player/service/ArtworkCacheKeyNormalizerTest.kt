package com.xperience.player.service

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkCacheKeyNormalizerTest {

    @Test
    fun googleCdnArtworkUrls_coalesceAcrossCoverSizeSuffixVariants() {
        val base = "https://lh3.googleusercontent.com/abcDEF123456"
        val homeVariant = "$base=w226-h226-l90-rj"
        val playerVariant = "$base=w540-h540-p-l90-rj"
        val standardVariant = "$base=w544-h544-l90-rj"
        val squareSVariant = "$base=s512-c-k-c0x00ffffff-no-rj-mo"

        val expected = "$base=w544-h544-l90-rj"
        assertEquals(expected, MediaNetworkClients.ArtworkCacheKeyNormalizer.normalizeUrl(homeVariant))
        assertEquals(expected, MediaNetworkClients.ArtworkCacheKeyNormalizer.normalizeUrl(playerVariant))
        assertEquals(expected, MediaNetworkClients.ArtworkCacheKeyNormalizer.normalizeUrl(standardVariant))
        assertEquals(expected, MediaNetworkClients.ArtworkCacheKeyNormalizer.normalizeUrl(squareSVariant))
        assertEquals(
            MediaNetworkClients.ArtworkCacheKeyNormalizer.cacheKey(homeVariant),
            MediaNetworkClients.ArtworkCacheKeyNormalizer.cacheKey(playerVariant)
        )
    }

    @Test
    fun nonSquareBannersAndNonGoogleUrls_arePreserved() {
        val banner = "https://lh3.googleusercontent.com/artistBanner=w1200-h500-l90-rj"
        assertEquals(banner, MediaNetworkClients.ArtworkCacheKeyNormalizer.normalizeUrl(banner))

        val appleUrl = "https://is1-ssl.mzstatic.com/image/thumb/Music126/v4/11/22/33/600x600bb.jpg"
        assertEquals(appleUrl, MediaNetworkClients.ArtworkCacheKeyNormalizer.normalizeUrl(appleUrl))
    }
}
