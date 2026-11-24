package com.xperience.player.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun parseStandardLrcCorrectly() {
        val lrc = """
            [ti:Test Song]
            [ar:Artist Name]
            [al:Album Title]
            [00:05.50]First line of the song
            [00:10.00]Second line with delay
            [01:15.25]Third line after one minute
        """.trimIndent()

        val result = LrcParser.parse(lrc, totalDurationMs = 120_000L)

        assertEquals(3, result.size)

        // Line 1
        assertEquals(5500L, result[0].timestampMs)
        assertEquals("First line of the song", result[0].text)
        assertTrue(result[0].isSynchronized)
        assertNotNull(result[0].syllables)
        assertEquals(result[0].text.length, result[0].syllables?.size)

        // Line 2
        assertEquals(10000L, result[1].timestampMs)
        assertEquals("Second line with delay", result[1].text)

        // Line 3
        assertEquals(75250L, result[2].timestampMs)
        assertEquals("Third line after one minute", result[2].text)
    }

    @Test
    fun parseMultiTimestampLine() {
        val lrc = """
            [00:04.00][00:20.00]Repeated chorus phrase
        """.trimIndent()

        val result = LrcParser.parse(lrc)

        assertEquals(2, result.size)
        assertEquals(4000L, result[0].timestampMs)
        assertEquals(20000L, result[1].timestampMs)
        assertEquals("Repeated chorus phrase", result[0].text)
        assertEquals("Repeated chorus phrase", result[1].text)
    }

    @Test
    fun parseEnhancedLrcWithInlineTimestamps() {
        val enhancedLrc = """
            [00:02.00]<00:02.00>Never <00:02.50>gonna <00:03.00>give <00:03.50>you <00:04.00>up
        """.trimIndent()

        val result = LrcParser.parse(enhancedLrc)

        assertEquals(1, result.size)
        val line = result[0]
        assertEquals(2000L, line.timestampMs)
        assertEquals("Never gonna give you up", line.text)
        assertTrue(line.isSynchronized)
        assertNotNull(line.syllables)
        // Check that syllable characters were generated
        assertTrue(line.syllables!!.isNotEmpty())
    }

    @Test
    fun parsePlainLyricsFallback() {
        val plainLyrics = """
            Just a line of plain text
            Another line without timestamps
            Final closing verse
        """.trimIndent()

        val result = LrcParser.parse(plainLyrics)

        assertEquals(3, result.size)
        assertFalse(result[0].isSynchronized)
        assertEquals("Just a line of plain text", result[0].text)
        assertFalse(result[1].isSynchronized)
        assertEquals("Another line without timestamps", result[1].text)
        assertFalse(result[2].isSynchronized)
        assertEquals("Final closing verse", result[2].text)
    }

    @Test
    fun expandSyllablesToCharactersTest() {
        val segments = listOf(
            com.xperience.player.domain.model.lyrics.LyricSyllableSegment(1000L, 1500L, "Hello")
        )

        val expanded = LrcParser.expandSyllablesToCharacters(segments)

        assertEquals(5, expanded.size)
        assertEquals("H", expanded[0].text)
        assertEquals("e", expanded[1].text)
        assertEquals("l", expanded[2].text)
        assertEquals("l", expanded[3].text)
        assertEquals("o", expanded[4].text)
        assertEquals(1000L, expanded[0].startMs)
        assertEquals(1500L, expanded[4].endMs)
    }
}
