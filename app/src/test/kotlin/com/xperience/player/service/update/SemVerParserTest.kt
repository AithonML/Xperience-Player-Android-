package com.xperience.player.service.update

import com.xperience.player.domain.model.update.SemVer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemVerParserTest {

    @Test
    fun parseStripsLeadingVPrefixAndParsesCoreSegments() {
        assertEquals(SemVer(1, 0, 1), SemVerParser.parse("v1.0.1"))
        assertEquals(SemVer(2, 10, 3), SemVerParser.parse("V2.10.3"))
        assertEquals(SemVer(0, 0, 1), SemVerParser.parse("0.0.1"))
        assertEquals(SemVer(1, 2, 3), SemVerParser.parse("  v1.2.3  "))
    }

    @Test
    fun parseNormalizesTwoSegmentAppVersionWithPreRelease() {
        val parsed = SemVerParser.parse("0.1-Beta")
        assertNotNull(parsed)
        assertEquals(SemVer(0, 1, 0, listOf("Beta")), parsed)

        assertTrue(SemVerParser.isNewerVersion("v0.1.0", "0.1-Beta"))
        assertTrue(SemVerParser.isNewerVersion("v0.1.1", "0.1-Beta"))
        assertTrue(SemVerParser.isNewerVersion("v1.0.0", "0.1-Beta"))
        assertFalse(SemVerParser.isNewerVersion("v0.1.0-Beta", "0.1-Beta"))
        assertFalse(SemVerParser.isNewerVersion("v0.0.9", "0.1-Beta"))
    }

    @Test
    fun compareFollowsSemVer200PreReleasePrecedence() {
        val ordered = listOf(
            "1.0.0-alpha",
            "1.0.0-alpha.1",
            "1.0.0-alpha.beta",
            "1.0.0-beta",
            "1.0.0-beta.2",
            "1.0.0-beta.11",
            "1.0.0-rc.1",
            "1.0.0",
            "1.0.1",
            "1.1.0",
            "2.0.0-alpha",
            "2.0.0"
        ).map { requireNotNull(SemVerParser.parse(it)) }

        for (i in 0 until ordered.lastIndex) {
            assertTrue(
                "Expected ${ordered[i]} < ${ordered[i + 1]}",
                ordered[i] < ordered[i + 1]
            )
            assertTrue(
                "Expected ${ordered[i + 1]} > ${ordered[i]}",
                ordered[i + 1].isNewerThan(ordered[i])
            )
        }
    }

    @Test
    fun numericPreReleaseIdentifierHasLowerPrecedenceThanNonNumeric() {
        val numeric = requireNotNull(SemVerParser.parse("1.0.0-1"))
        val alpha = requireNotNull(SemVerParser.parse("1.0.0-alpha"))
        assertTrue(numeric < alpha)
    }

    @Test
    fun buildMetadataIsParsedAndIgnoredForPrecedence() {
        val a = requireNotNull(SemVerParser.parse("v1.0.0+20130313144700"))
        val b = requireNotNull(SemVerParser.parse("1.0.0+exp.sha.5114f85"))
        assertEquals("20130313144700", a.buildMetadata)
        assertEquals(0, a.compareTo(b))
        assertFalse(SemVerParser.isNewerVersion("v1.0.0+build.2", "1.0.0+build.1"))
    }

    @Test
    fun rejectsMalformedVersions() {
        val invalid = listOf(
            null,
            "",
            "   ",
            "v",
            "vv1.0.0",
            "1",
            "1.2.3.4",
            "01.0.0",
            "1.02.0",
            "1.0.0-",
            "1.0.0+",
            "1.0.0-alpha..1",
            "1.0.0-01",
            "1.0.a",
            "v-1.0.0"
        )
        for (raw in invalid) {
            assertNull("Expected null for '$raw'", SemVerParser.parse(raw))
        }
    }

    @Test
    fun formatDisplayTagAddsSingleLowercaseVPrefix() {
        assertEquals("v0.1-Beta", SemVerParser.formatDisplayTag("0.1-Beta"))
        assertEquals("v1.0.1", SemVerParser.formatDisplayTag("v1.0.1"))
        assertEquals("v2.0.0", SemVerParser.formatDisplayTag("V2.0.0"))
        assertEquals("v0.0.0", SemVerParser.formatDisplayTag(""))
    }
}
