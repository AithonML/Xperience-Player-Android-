package com.xperience.player.ui.library

import com.xperience.player.domain.model.TrackItem
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryGroupingTest {

    private val utc = ZoneOffset.UTC

    private fun ms(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 12,
        minute: Int = 0,
        second: Int = 0
    ): Long = ZonedDateTime.of(year, month, day, hour, minute, second, 0, utc)
        .toInstant()
        .toEpochMilli()

    private fun track(
        id: String,
        title: String = "Track $id",
        dateAddedMs: Long = 0L
    ): TrackItem = TrackItem(
        id = id,
        title = title,
        artist = "Artist $id",
        album = "Album $id",
        durationMs = 180_000L,
        dateAddedMs = dateAddedMs
    )

    @Test
    fun `groupTracksByAddedPeriod returns empty list for empty input`() {
        val result = groupTracksByAddedPeriod(emptyList(), nowMs = ms(2026, 11, 14), zoneId = utc)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `groupTracksByAddedPeriod classifies all chronological buckets and descending years in order`() {
        val nowMs = ms(2026, 11, 14, hour = 12)

        val tracks = listOf(
            track("y2022", dateAddedMs = ms(2022, 6, 15)),
            track("undated", dateAddedMs = 0L),
            track("sixMonths", dateAddedMs = ms(2026, 7, 1)),
            track("weekOlder", dateAddedMs = ms(2026, 11, 10)),
            track("thisYear", dateAddedMs = ms(2026, 2, 10)),
            track("y2025", dateAddedMs = ms(2025, 11, 1)),
            track("threeMonths", dateAddedMs = ms(2026, 9, 20)),
            track("weekNewest", dateAddedMs = ms(2026, 11, 14, hour = 10)),
            track("lastMonth", dateAddedMs = ms(2026, 10, 25)),
            track("y2024", dateAddedMs = ms(2024, 3, 5)),
            track("futureSkew", dateAddedMs = ms(2026, 11, 14, hour = 13))
        )

        val groups = groupTracksByAddedPeriod(tracks, nowMs = nowMs, zoneId = utc)

        assertEquals(
            listOf(
                "Added Last Week",
                "Added Last Month",
                "Added Last 3 Months",
                "Added Last 6 Months",
                "Added This Year",
                "2025",
                "2024",
                "2022",
                "Undated"
            ),
            groups.map { it.label }
        )

        assertEquals(
            listOf("futureSkew", "weekNewest", "weekOlder"),
            groups[0].tracks.map { it.id }
        )
        assertEquals(listOf("lastMonth"), groups[1].tracks.map { it.id })
        assertEquals(listOf("threeMonths"), groups[2].tracks.map { it.id })
        assertEquals(listOf("sixMonths"), groups[3].tracks.map { it.id })
        assertEquals(listOf("thisYear"), groups[4].tracks.map { it.id })
        assertEquals(listOf("y2025"), groups[5].tracks.map { it.id })
        assertEquals(listOf("y2024"), groups[6].tracks.map { it.id })
        assertEquals(listOf("y2022"), groups[7].tracks.map { it.id })
        assertEquals(listOf("undated"), groups[8].tracks.map { it.id })
    }

    @Test
    fun `groupTracksByAddedPeriod handles exact boundary instants and year rollover`() {
        val nowMs = ms(2026, 1, 15, hour = 12)

        val tracks = listOf(
            // Exactly 7 days before now -> still included in last week (>= 1 week ago)
            track("exactWeek", dateAddedMs = ms(2026, 1, 8, hour = 12)),
            // 1 second older than 7 days -> falls into last month
            track("justPastWeek", dateAddedMs = ms(2026, 1, 8, hour = 11, second = 59)),
            // Crosses calendar year boundary (Dec 2025 is within 1 month of Jan 15, 2026)
            track("decRecent", dateAddedMs = ms(2025, 12, 25)),
            // Older than 6 months in previous calendar year -> grouped into "2025"
            track("may2025", dateAddedMs = ms(2025, 5, 1))
        )

        val groups = groupTracksByAddedPeriod(tracks, nowMs = nowMs, zoneId = utc)

        assertEquals(
            listOf(
                "Added Last Week",
                "Added Last Month",
                "2025"
            ),
            groups.map { it.label }
        )
        assertEquals(listOf("exactWeek"), groups[0].tracks.map { it.id })
        assertEquals(listOf("justPastWeek", "decRecent"), groups[1].tracks.map { it.id })
        assertEquals(listOf("may2025"), groups[2].tracks.map { it.id })
    }

    @Test
    fun `alphabetBucketKey normalizes case diacritics whitespace and non-alpha to hash`() {
        assertEquals("B", alphabetBucketKey("bad bunny"))
        assertEquals("D", alphabetBucketKey("  Daft Punk"))
        assertEquals("A", alphabetBucketKey("Álvaro Soler"))
        assertEquals("E", alphabetBucketKey("Édith Piaf"))
        assertEquals("I", alphabetBucketKey("Íñigo Quintero"))
        assertEquals("N", alphabetBucketKey("Ñengo Flow"))
        assertEquals("O", alphabetBucketKey("Óscar Chávez"))
        assertEquals("U", alphabetBucketKey("Última Llamada"))
        assertEquals("#", alphabetBucketKey("21 Savage"))
        assertEquals("#", alphabetBucketKey("50 Cent"))
        assertEquals("#", alphabetBucketKey("\$uicideboy\$"))
        assertEquals("#", alphabetBucketKey("   "))
        assertEquals("#", alphabetBucketKey(""))
    }

    @Test
    fun `groupItemsAlphabetically orders A to Z then hash and sorts within each bucket`() {
        val titles = listOf(
            "Zoe",
            "505",
            "Álvaro",
            "alpha",
            "Beta",
            "  amanece",
            "\$money",
            "Ñengo"
        )

        val sections = groupItemsAlphabetically(titles) { it }

        assertEquals(listOf("A", "B", "N", "Z", "#"), sections.map { it.letter })
        assertEquals(listOf("alpha", "Álvaro", "  amanece"), sections[0].items)
        assertEquals(listOf("Beta"), sections[1].items)
        assertEquals(listOf("Ñengo"), sections[2].items)
        assertEquals(listOf("Zoe"), sections[3].items)
        assertEquals(listOf("\$money", "505"), sections[4].items)
    }

    @Test
    fun `groupItemsAlphabetically returns empty list when input is empty`() {
        val sections = groupItemsAlphabetically(emptyList<String>()) { it }
        assertTrue(sections.isEmpty())
    }
}
