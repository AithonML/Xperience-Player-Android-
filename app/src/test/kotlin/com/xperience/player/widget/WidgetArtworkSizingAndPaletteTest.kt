package com.xperience.player.widget

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetArtworkSizingAndPaletteTest {

    @Test
    fun computeSafeTargetPxAlwaysStaysUnderBinderPayloadBudget() {
        for (slots in listOf(1, 2, 3, 4, 6, 8, 10, 12, 16)) {
            val sidePx = WidgetArtworkSizing.computeSafeTargetPx(
                requestedPx = 256,
                bitmapSlotsInPayload = slots
            )
            assertTrue("Side must be >= min safe", sidePx >= WidgetArtworkSizing.MIN_SAFE_SIDE_PX)
            assertTrue("Side must be <= max safe", sidePx <= WidgetArtworkSizing.MAX_SAFE_SIDE_PX)

            val totalBytes = WidgetArtworkSizing.estimateBitmapBytes(sidePx) * slots
            assertTrue(
                "Total bytes ($totalBytes) for $slots slots must stay safely below 1 MB Binder cap",
                totalBytes <= WidgetArtworkSizing.MAX_TOTAL_PAYLOAD_BYTES.toLong()
            )
        }
    }

    @Test
    fun computeSafeTargetPxRespectsRequestedSizeWhenBelowBudget() {
        val sidePx = WidgetArtworkSizing.computeSafeTargetPx(
            requestedPx = 96,
            bitmapSlotsInPayload = 2
        )
        assertEquals(96, sidePx)

        // Non-positive slot counts and tiny requests clamp safely
        assertEquals(
            WidgetArtworkSizing.MIN_SAFE_SIDE_PX,
            WidgetArtworkSizing.computeSafeTargetPx(requestedPx = 10, bitmapSlotsInPayload = 0)
        )
    }

    @Test
    fun calculateInSampleSizeComputesPowerOfTwoDownscaling() {
        assertEquals(1, WidgetArtworkSizing.calculateInSampleSize(128, 128, 128))
        assertEquals(8, WidgetArtworkSizing.calculateInSampleSize(1200, 1200, 150))
        assertEquals(4, WidgetArtworkSizing.calculateInSampleSize(1000, 1000, 150))
        assertEquals(4, WidgetArtworkSizing.calculateInSampleSize(800, 600, 150))
        assertEquals(2, WidgetArtworkSizing.calculateInSampleSize(500, 400, 150))
        assertEquals(1, WidgetArtworkSizing.calculateInSampleSize(0, 0, 128))
        assertEquals(1, WidgetArtworkSizing.calculateInSampleSize(500, 500, 0))
    }

    @Test
    fun computeCenterSquareCropCentersLandscapePortraitAndSquare() {
        assertArrayEquals(intArrayOf(100, 0, 500, 400), WidgetArtworkSizing.computeCenterSquareCrop(600, 400))
        assertArrayEquals(intArrayOf(0, 100, 400, 500), WidgetArtworkSizing.computeCenterSquareCrop(400, 600))
        assertArrayEquals(intArrayOf(0, 0, 300, 300), WidgetArtworkSizing.computeCenterSquareCrop(300, 300))
        assertArrayEquals(intArrayOf(0, 0, 0, 0), WidgetArtworkSizing.computeCenterSquareCrop(0, 100))
    }

    @Test
    fun blendOpaqueAndHarmonizationPreserveOpacityAndRespondToAccent() {
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        val mid = WidgetColorMath.blendOpaque(black, white, 0.5f)
        assertEquals(0xFF, (mid ushr 24) and 0xFF)
        assertEquals(128, (mid ushr 16) and 0xFF)
        assertEquals(128, (mid ushr 8) and 0xFF)
        assertEquals(128, mid and 0xFF)

        val base = WidgetColorTokens(
            surfaceContainer = DayNightColor(0xFFF5F5F5.toInt(), 0xFF14161C.toInt()),
            surfaceCard = DayNightColor(0xFFEAEAEA.toInt(), 0xFF1E2129.toInt()),
            artworkPlaceholder = DayNightColor(0xFFD0E4F0.toInt(), 0xFF203040.toInt()),
            primary = DayNightColor(0xFF00506E.toInt(), 0xFF87CFF9.toInt()),
            onPrimary = DayNightColor(0xFFFFFFFF.toInt(), 0xFF00344A.toInt()),
            secondaryContainer = DayNightColor(0xFFCFEAFD.toInt(), 0xFF294351.toInt()),
            onSecondaryContainer = DayNightColor(0xFF344D5C.toInt(), 0xFFBDD8EA.toInt()),
            iconButtonSurface = DayNightColor(0xFFE6E8EC.toInt(), 0xFF272A2D.toInt()),
            onSurface = DayNightColor(0xFF10141D.toInt(), 0xFFFFFFFF.toInt()),
            onSurfaceVariant = DayNightColor(0xFF64748B.toInt(), 0xFFA2A7B5.toInt()),
            accentText = DayNightColor(0xFF00506E.toInt(), 0xFF87CFF9.toInt()),
            progressTrack = DayNightColor(0xFFCBD5E1.toInt(), 0xFF334155.toInt()),
            divider = DayNightColor(0xFFCBD5E1.toInt(), 0xFF334155.toInt()),
            favoriteActive = DayNightColor(0xFFE11D48.toInt(), 0xFFFF6B8A.toInt())
        )

        // Null accent returns base untouched
        assertEquals(base, WidgetColorMath.harmonizeWithArtworkAccent(base, null))

        // Vivid cyan accent shifts dark surface container & primary gently while staying opaque
        val harmonized = WidgetColorMath.harmonizeWithArtworkAccent(base, 0x0000E5FF)
        assertNotEquals(base.surfaceContainer.dark, harmonized.surfaceContainer.dark)
        assertEquals(0xFF, (harmonized.surfaceContainer.dark ushr 24) and 0xFF)
        assertEquals(0xFF, (harmonized.primary.light ushr 24) and 0xFF)
    }
}
