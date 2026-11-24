package com.xperience.player.service

import com.xperience.player.domain.model.lyrics.LyricLine
import com.xperience.player.domain.model.lyrics.LyricSyllableSegment
import java.util.regex.Pattern

/**
 * Robust LRC parser supporting Standard LRC, Enhanced LRC (word/syllable timestamps),
 * and Plain / Fixed lyrics fallback.
 * Accurately replicates the PC version's parsing and letter-by-letter expansion logic.
 */
object LrcParser {

    private val LRC_TIMESTAMP_REGEX = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]")
    private val INLINE_WORD_TIMESTAMP_REGEX = Pattern.compile("<(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?>")
    private val LRC_METADATA_REGEX = Pattern.compile("^\\[(ar|ti|al|au|by|length|re|ve|tool|id|offset):.*?\\]$", Pattern.CASE_INSENSITIVE)

    /**
     * Parses raw LRC text or plain text into a list of [LyricLine]s.
     */
    fun parse(rawText: String, totalDurationMs: Long = 0L): List<LyricLine> {
        if (rawText.isBlank()) return emptyList()

        val rawLines = rawText.split(Regex("[\r\n]+")).map { it.trim() }.filter { it.isNotEmpty() }
        val timedLines = mutableListOf<LyricLine>()
        var foundAnyTimestamp = false

        for (line in rawLines) {
            if (LRC_METADATA_REGEX.matcher(line).matches()) {
                continue
            }

            val timestampMatcher = LRC_TIMESTAMP_REGEX.matcher(line)
            val lineMatches = mutableListOf<Pair<Long, Pair<Int, Int>>>()

            while (timestampMatcher.find()) {
                val ms = parseTimestampToMs(
                    timestampMatcher.group(1),
                    timestampMatcher.group(2),
                    timestampMatcher.group(3)
                )
                lineMatches.add(ms to (timestampMatcher.start() to timestampMatcher.end()))
            }

            if (lineMatches.isEmpty()) {
                continue
            }

            foundAnyTimestamp = true
            val lastMatchEnd = lineMatches.last().second.second
            val contentAfterTags = if (lastMatchEnd < line.length) line.substring(lastMatchEnd).trim() else ""

            // Check for Enhanced LRC inline word timestamps: <00:12.34>Word
            val inlineMatcher = INLINE_WORD_TIMESTAMP_REGEX.matcher(contentAfterTags)
            val inlineMatches = mutableListOf<Pair<Long, Pair<Int, Int>>>()
            while (inlineMatcher.find()) {
                val ms = parseTimestampToMs(
                    inlineMatcher.group(1),
                    inlineMatcher.group(2),
                    inlineMatcher.group(3)
                )
                inlineMatches.add(ms to (inlineMatcher.start() to inlineMatcher.end()))
            }

            var cleanContent = contentAfterTags
            var inlineSegments: List<LyricSyllableSegment>? = null

            if (inlineMatches.isNotEmpty()) {
                val segments = mutableListOf<LyricSyllableSegment>()
                val cleanSb = StringBuilder()

                for (i in 0 until inlineMatches.size) {
                    val currentWord = inlineMatches[i]
                    val wordStartMs = currentWord.first
                    val textStart = currentWord.second.second
                    val textEnd = if (i + 1 < inlineMatches.size) inlineMatches[i + 1].second.first else contentAfterTags.length
                    val segmentText = if (textStart <= textEnd) contentAfterTags.substring(textStart, textEnd) else ""

                    if (segmentText.isNotEmpty()) {
                        val wordEndMs = if (i + 1 < inlineMatches.size) {
                            inlineMatches[i + 1].first
                        } else {
                            wordStartMs + 350L
                        }
                        segments.add(LyricSyllableSegment(wordStartMs, maxOf(wordStartMs + 40L, wordEndMs), segmentText))
                        cleanSb.append(segmentText)
                    }
                }
                cleanContent = cleanSb.toString().trim()
                if (segments.isNotEmpty()) {
                    inlineSegments = segments
                }
            }

            if (cleanContent.isBlank()) {
                cleanContent = "♪ • • •"
            }

            for (match in lineMatches) {
                timedLines.add(
                    LyricLine(
                        timestampMs = match.first,
                        text = cleanContent,
                        isSynchronized = true,
                        endTimestampMs = null,
                        syllables = if (!inlineSegments.isNullOrEmpty()) expandSyllablesToCharacters(inlineSegments) else null
                    )
                )
            }
        }

        // If no timestamp tags were found anywhere, treat as fixed plain lyrics
        if (!foundAnyTimestamp) {
            return parsePlainLyrics(rawLines)
        }

        if (timedLines.isNotEmpty()) {
            val sorted = timedLines.sortedBy { it.timestampMs }
            return ensureProgressiveSegmentsForAllLines(sorted, totalDurationMs)
        }

        return emptyList()
    }

    /**
     * Parses plain text without timestamps into non-synchronized lines.
     */
    fun parsePlainLyrics(lines: List<String>): List<LyricLine> {
        return lines.mapIndexed { index, text ->
            LyricLine(
                timestampMs = index * 3000L,
                text = text,
                isSynchronized = false,
                endTimestampMs = (index + 1) * 3000L,
                syllables = null
            )
        }
    }

    /**
     * Ensures every synchronized line has an end timestamp and character-level
     * timing segments for smooth progressive illumination.
     */
    fun ensureProgressiveSegmentsForAllLines(
        sourceLines: List<LyricLine>,
        trackDurationMs: Long
    ): List<LyricLine> {
        val result = ArrayList<LyricLine>(sourceLines.size)

        for (i in sourceLines.indices) {
            val current = sourceLines[i]
            if (!current.isSynchronized) {
                result.add(current)
                continue
            }

            val nextStart = if (i + 1 < sourceLines.size) {
                sourceLines[i + 1].timestampMs
            } else {
                if (trackDurationMs > current.timestampMs) trackDurationMs else current.timestampMs + 4000L
            }

            val rawSpanMs = ((nextStart - current.timestampMs) * 0.88).toLong().coerceAtLeast(450L)
            val clampedSpanMs = minOf(rawSpanMs, maxOf(1600L, (current.text.length * 110L)))

            val effectiveEnd = if (current.endTimestampMs != null && current.endTimestampMs > current.timestampMs) {
                current.endTimestampMs
            } else {
                current.timestampMs + clampedSpanMs
            }

            val segments = if (!current.syllables.isNullOrEmpty()) {
                current.syllables
            } else {
                synthesizeCharacterSegments(current.text, current.timestampMs, effectiveEnd)
            }

            result.add(
                current.copy(
                    endTimestampMs = effectiveEnd,
                    syllables = segments
                )
            )
        }

        return result
    }

    /**
     * Subdivides word/syllable segments into individual character timings.
     */
    fun expandSyllablesToCharacters(wordSegments: List<LyricSyllableSegment>): List<LyricSyllableSegment> {
        val charSegments = mutableListOf<LyricSyllableSegment>()
        for (seg in wordSegments) {
            if (seg.text.isEmpty()) continue
            val len = seg.text.length
            val totalMs = (seg.endMs - seg.startMs).toDouble().coerceAtLeast(30.0)
            val stepMs = totalMs / len

            for (c in 0 until len) {
                val cStart = seg.startMs + (c * stepMs).toLong()
                val cEnd = seg.startMs + ((c + 1) * stepMs).toLong()
                charSegments.add(LyricSyllableSegment(cStart, cEnd, seg.text[c].toString()))
            }
        }
        return charSegments
    }

    /**
     * Synthesizes character-level timings linearly across line duration.
     */
    fun synthesizeCharacterSegments(text: String, startMs: Long, endMs: Long): List<LyricSyllableSegment> {
        if (text.isEmpty()) return emptyList()
        val segments = ArrayList<LyricSyllableSegment>(text.length)
        val totalMs = (endMs - startMs).toDouble().coerceAtLeast(200.0)
        val stepMs = totalMs / text.length

        for (i in text.indices) {
            val cStart = startMs + (i * stepMs).toLong()
            val cEnd = startMs + ((i + 1) * stepMs).toLong()
            segments.add(LyricSyllableSegment(cStart, cEnd, text[i].toString()))
        }
        return segments
    }

    private fun parseTimestampToMs(minsStr: String?, secsStr: String?, fracStr: String?): Long {
        val mins = minsStr?.toLongOrNull() ?: 0L
        val secs = secsStr?.toLongOrNull() ?: 0L
        val fracMs = when {
            fracStr.isNullOrEmpty() -> 0L
            fracStr.length == 1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
            fracStr.length == 2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
            else -> fracStr.substring(0, 3).toLongOrNull() ?: 0L
        }
        return (mins * 60_000L) + (secs * 1000L) + fracMs
    }
}
