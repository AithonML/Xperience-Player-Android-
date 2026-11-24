package com.xperience.player.ui.lyrics

import com.xperience.player.domain.model.lyrics.LyricLine
import com.xperience.player.domain.model.lyrics.LyricSyllableSegment
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Character or grapheme timing span inside a [LyricWordToken], carrying both
 * absolute milliseconds and normalized horizontal fraction [0f..1f] within the word.
 */
data class LyricGlyphSpan(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val fractionStart: Float,
    val fractionEnd: Float
)

/**
 * Word or CJK character-group token rendered inside a synchronized lyric line.
 */
data class LyricWordToken(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val glyphs: List<LyricGlyphSpan>,
    val hasTrailingSpace: Boolean = true,
    val isCjkToken: Boolean = false
)

/**
 * Computed per-frame visual state for an individual [LyricWordToken].
 */
data class WordVisualState(
    val sweepProgress: Float,
    val activeEnvelope: Float,
    val scale: Float,
    val liftDp: Float,
    val glowIntensity: Float
)

/**
 * Smooth frame-clock position interpolator that bridges discrete playback state ticks
 * (e.g., ~30Hz polling) to silky 60/120fps animations while preventing backward jitter
 * and bounding runaway extrapolation on stalls.
 */
class PlaybackPositionInterpolator(
    private val maxExtrapolationMs: Long = LyricsTimingEngine.MAX_EXTRAPOLATION_MS,
    private val seekSnapThresholdMs: Long = LyricsTimingEngine.SEEK_SNAP_THRESHOLD_MS
) {
    private var lastReportedPositionMs: Long = Long.MIN_VALUE
    private var anchorPositionMs: Long = 0L
    private var anchorFrameMs: Long = 0L
    private var lastSmoothPositionMs: Long = 0L
    private var initialized: Boolean = false
    private var awaitingAnchorFrame: Boolean = false

    fun snap(positionMs: Long) {
        val clamped = positionMs.coerceAtLeast(0L)
        lastReportedPositionMs = clamped
        anchorPositionMs = clamped
        lastSmoothPositionMs = clamped
        initialized = true
        awaitingAnchorFrame = true
    }

    fun reset(positionMs: Long, frameTimeMs: Long) {
        val clamped = positionMs.coerceAtLeast(0L)
        lastReportedPositionMs = clamped
        anchorPositionMs = clamped
        anchorFrameMs = frameTimeMs
        lastSmoothPositionMs = clamped
        initialized = true
        awaitingAnchorFrame = false
    }

    fun update(reportedPositionMs: Long, isPlaying: Boolean, frameTimeMs: Long): Long {
        val reported = reportedPositionMs.coerceAtLeast(0L)
        if (!initialized) {
            reset(reported, frameTimeMs)
            return reported
        }
        if (awaitingAnchorFrame) {
            anchorFrameMs = frameTimeMs
            awaitingAnchorFrame = false
        }

        if (!isPlaying) {
            anchorPositionMs = reported
            anchorFrameMs = frameTimeMs
            lastReportedPositionMs = reported
            lastSmoothPositionMs = reported
            return reported
        }

        val jumpedBackward = reported < lastReportedPositionMs - 40L
        val jumpedFar = abs(reported - lastSmoothPositionMs) > seekSnapThresholdMs
        if (jumpedBackward || jumpedFar) {
            anchorPositionMs = reported
            anchorFrameMs = frameTimeMs
            lastReportedPositionMs = reported
            lastSmoothPositionMs = reported
            return reported
        }

        if (reported != lastReportedPositionMs) {
            anchorPositionMs = reported
            anchorFrameMs = frameTimeMs
            lastReportedPositionMs = reported
        }

        val elapsedSinceAnchor = (frameTimeMs - anchorFrameMs).coerceIn(0L, maxExtrapolationMs)
        val rawExtrapolated = anchorPositionMs + elapsedSinceAnchor

        // Keep monotonic progression across slight coroutine tick jitter while bounding ahead-drift.
        val monotonicFloor = minOf(lastSmoothPositionMs, reported + maxExtrapolationMs)
        val smooth = maxOf(rawExtrapolated, monotonicFloor).coerceAtLeast(0L)
        lastSmoothPositionMs = smooth
        return smooth
    }
}

/**
 * Pure timing, tokenization, sweep progression, and dynamic growth envelope math
 * for synchronized lyrics.
 */
object LyricsTimingEngine {

    const val DEFAULT_LINE_DURATION_MS = 3200L
    const val MIN_WORD_DURATION_MS = 60L
    const val SCALE_RELEASE_TAIL_MS = 200L
    const val GLOW_RELEASE_TAIL_MS = 380L
    const val MAX_EXTRAPOLATION_MS = 150L
    const val SEEK_SNAP_THRESHOLD_MS = 220L

    private data class TimedChar(
        val ch: Char,
        val startMs: Long,
        val endMs: Long
    )

    fun isCjkCharacter(ch: Char): Boolean {
        val code = ch.code
        return (code in 0x4E00..0x9FFF) ||   // CJK Unified Ideographs
            (code in 0x3400..0x4DBF) ||      // CJK Extension A
            (code in 0x3040..0x309F) ||      // Hiragana
            (code in 0x30A0..0x30FF) ||      // Katakana
            (code in 0xAC00..0xD7AF) ||      // Hangul Syllables
            (code in 0xF900..0xFAFF)         // CJK Compatibility Ideographs
    }

    private fun isCjkTrailingPunctuation(ch: Char): Boolean {
        return ch == '。' || ch == '、' || ch == '，' || ch == '！' || ch == '？' ||
            ch == '」' || ch == '』' || ch == '）' || ch == '》' || ch == '〜'
    }

    /**
     * Finds the active lyric line index for [positionMs].
     * Returns `-1` if playback is currently before the first line's timestamp.
     */
    fun findActiveLineIndex(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        if (positionMs < lines.first().timestampMs) return -1

        var low = 0
        var high = lines.lastIndex
        var result = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timestampMs <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    /**
     * Tokenizes an entire lyrics list into per-line [LyricWordToken] lists.
     */
    fun tokenizeLines(lines: List<LyricLine>): List<List<LyricWordToken>> {
        if (lines.isEmpty()) return emptyList()
        return lines.mapIndexed { index, line ->
            val nextStart = lines.getOrNull(index + 1)?.timestampMs
            tokenizeLine(line, nextStart)
        }
    }

    /**
     * Builds progressive word/CJK-glyph tokens for a single [LyricLine], preserving exact
     * syllable/character timestamps when present and clamping overlapping word tails so two
     * consecutive words do not sweep simultaneously.
     */
    fun tokenizeLine(line: LyricLine, nextLineStartMs: Long? = null): List<LyricWordToken> {
        val effectiveLineEnd = resolveEffectiveLineEndMs(line, nextLineStartMs)
        val syllables = line.syllables

        val rawTokens = if (!syllables.isNullOrEmpty()) {
            tokenizeFromSyllables(syllables, line.timestampMs, effectiveLineEnd)
        } else {
            emptyList()
        }

        val tokens = if (rawTokens.isNotEmpty()) {
            rawTokens
        } else {
            synthesizeWordTokens(line.text, line.timestampMs, effectiveLineEnd)
        }

        return clampOverlappingWordEnds(tokens)
    }

    private fun resolveEffectiveLineEndMs(line: LyricLine, nextLineStartMs: Long?): Long {
        val explicitEnd = line.endTimestampMs
        if (explicitEnd != null && explicitEnd > line.timestampMs) {
            return explicitEnd
        }
        if (nextLineStartMs != null && nextLineStartMs > line.timestampMs) {
            val rawSpan = ((nextLineStartMs - line.timestampMs) * 0.90).toLong()
            val maxByLength = maxOf(1600L, line.text.length * 110L)
            return line.timestampMs + rawSpan.coerceIn(350L, maxByLength)
        }
        return line.timestampMs + maxOf(DEFAULT_LINE_DURATION_MS, line.text.length * 110L)
    }

    private fun tokenizeFromSyllables(
        syllables: List<LyricSyllableSegment>,
        lineStartMs: Long,
        lineEndMs: Long
    ): List<LyricWordToken> {
        val timedChars = mutableListOf<TimedChar>()
        for (seg in syllables) {
            if (seg.text.isEmpty()) continue
            val segStart = seg.startMs.coerceAtLeast(0L)
            val segEnd = maxOf(segStart + 20L, seg.endMs)
            val len = seg.text.length
            val step = (segEnd - segStart).toDouble() / len
            for (i in 0 until len) {
                val cStart = segStart + (i * step).toLong()
                val cEnd = maxOf(cStart + 1L, segStart + ((i + 1) * step).toLong())
                timedChars.add(TimedChar(seg.text[i], cStart, cEnd))
            }
        }
        if (timedChars.isEmpty()) return emptyList()

        val tokens = mutableListOf<LyricWordToken>()
        val currentChars = mutableListOf<TimedChar>()

        fun flushWord(hasTrailingSpace: Boolean, isCjk: Boolean = false) {
            if (currentChars.isEmpty()) return
            val wordText = buildString(currentChars.size) {
                for (tc in currentChars) append(tc.ch)
            }
            val start = currentChars.first().startMs
            val end = maxOf(start + MIN_WORD_DURATION_MS, currentChars.last().endMs)
            val total = currentChars.size.toFloat()
            val glyphs = currentChars.mapIndexed { idx, tc ->
                LyricGlyphSpan(
                    text = tc.ch.toString(),
                    startMs = tc.startMs,
                    endMs = maxOf(tc.startMs + 1L, tc.endMs),
                    fractionStart = idx / total,
                    fractionEnd = (idx + 1) / total
                )
            }
            tokens.add(
                LyricWordToken(
                    text = wordText,
                    startMs = start,
                    endMs = end,
                    glyphs = glyphs,
                    hasTrailingSpace = hasTrailingSpace,
                    isCjkToken = isCjk
                )
            )
            currentChars.clear()
        }

        for (i in timedChars.indices) {
            val tc = timedChars[i]
            val ch = tc.ch
            if (ch.isWhitespace()) {
                flushWord(hasTrailingSpace = true)
                continue
            }
            if (isCjkCharacter(ch)) {
                flushWord(hasTrailingSpace = false)
                currentChars.add(tc)
                // Attach trailing CJK punctuation to this ideograph so punctuation doesn't orphan on wrap
                val next = timedChars.getOrNull(i + 1)
                if (next == null || !isCjkTrailingPunctuation(next.ch)) {
                    val hasNextSpace = next?.ch?.isWhitespace() == true
                    flushWord(hasTrailingSpace = hasNextSpace, isCjk = true)
                }
                continue
            }
            if (currentChars.isNotEmpty() && isCjkCharacter(currentChars.first().ch) && isCjkTrailingPunctuation(ch)) {
                currentChars.add(tc)
                val next = timedChars.getOrNull(i + 1)
                flushWord(hasTrailingSpace = next?.ch?.isWhitespace() == true, isCjk = true)
                continue
            }
            currentChars.add(tc)
        }
        flushWord(hasTrailingSpace = false)

        if (tokens.isNotEmpty()) {
            return splitOversizedTokens(tokens)
        }

        // Fallback if syllables were whitespace-only
        return synthesizeWordTokens(
            text = syllables.joinToString("") { it.text }.trim().ifEmpty { "♪" },
            lineStartMs = lineStartMs,
            lineEndMs = lineEndMs
        )
    }

    private fun synthesizeWordTokens(
        text: String,
        lineStartMs: Long,
        lineEndMs: Long
    ): List<LyricWordToken> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return listOf(
                LyricWordToken(
                    text = text,
                    startMs = lineStartMs,
                    endMs = maxOf(lineStartMs + MIN_WORD_DURATION_MS, lineEndMs),
                    glyphs = listOf(LyricGlyphSpan(text, lineStartMs, maxOf(lineStartMs + MIN_WORD_DURATION_MS, lineEndMs), 0f, 1f)),
                    hasTrailingSpace = false
                )
            )
        }

        val rawUnits = mutableListOf<Pair<String, Boolean>>() // (tokenText, hasTrailingSpace)
        val sb = StringBuilder()
        for (i in trimmed.indices) {
            val ch = trimmed[i]
            if (ch.isWhitespace()) {
                if (sb.isNotEmpty()) {
                    rawUnits.add(sb.toString() to true)
                    sb.clear()
                }
            } else if (isCjkCharacter(ch)) {
                if (sb.isNotEmpty()) {
                    rawUnits.add(sb.toString() to false)
                    sb.clear()
                }
                val next = trimmed.getOrNull(i + 1)
                if (next != null && isCjkTrailingPunctuation(next)) {
                    sb.append(ch)
                } else {
                    rawUnits.add(ch.toString() to (next?.isWhitespace() == true))
                }
            } else if (sb.isNotEmpty() && isCjkCharacter(sb[0]) && isCjkTrailingPunctuation(ch)) {
                sb.append(ch)
                val next = trimmed.getOrNull(i + 1)
                rawUnits.add(sb.toString() to (next?.isWhitespace() == true))
                sb.clear()
            } else {
                sb.append(ch)
            }
        }
        if (sb.isNotEmpty()) {
            rawUnits.add(sb.toString() to false)
        }

        if (rawUnits.isEmpty()) return emptyList()

        val activeDuration = maxOf(250L, lineEndMs - lineStartMs)
        val totalWeight = rawUnits.sumOf { (word, hasSpace) -> word.length + if (hasSpace) 1 else 0 }.coerceAtLeast(1)
        var accumulated = 0L

        val tokens = rawUnits.map { (word, hasSpace) ->
            val weight = word.length + if (hasSpace) 1 else 0
            val wordSlotDur = maxOf(MIN_WORD_DURATION_MS, (activeDuration * weight.toDouble() / totalWeight).toLong())
            val wStart = lineStartMs + accumulated
            val activeCharDur = if (hasSpace) {
                maxOf(MIN_WORD_DURATION_MS, (wordSlotDur * word.length.toDouble() / weight).toLong())
            } else {
                wordSlotDur
            }
            val wEnd = wStart + activeCharDur
            accumulated += wordSlotDur

            val totalChars = word.length.coerceAtLeast(1)
            val glyphs = word.mapIndexed { cIdx, ch ->
                val gStart = wStart + (activeCharDur * cIdx.toDouble() / totalChars).toLong()
                val gEnd = maxOf(gStart + 15L, wStart + (activeCharDur * (cIdx + 1).toDouble() / totalChars).toLong())
                LyricGlyphSpan(
                    text = ch.toString(),
                    startMs = gStart,
                    endMs = gEnd,
                    fractionStart = cIdx.toFloat() / totalChars,
                    fractionEnd = (cIdx + 1).toFloat() / totalChars
                )
            }

            LyricWordToken(
                text = word,
                startMs = wStart,
                endMs = wEnd,
                glyphs = glyphs,
                hasTrailingSpace = hasSpace,
                isCjkToken = word.isNotEmpty() && isCjkCharacter(word[0])
            )
        }

        return splitOversizedTokens(tokens)
    }

    /**
     * Splits unusually long non-CJK tokens (> 16 chars) on '-' or '/' so FlowRow wraps cleanly
     * without horizontal clipping on narrow screens.
     */
    private fun splitOversizedTokens(tokens: List<LyricWordToken>): List<LyricWordToken> {
        val result = ArrayList<LyricWordToken>(tokens.size)
        for (token in tokens) {
            if (token.isCjkToken || token.text.length <= 16 || (!token.text.contains('-') && !token.text.contains('/'))) {
                result.add(token)
                continue
            }
            val parts = mutableListOf<Pair<Int, Int>>()
            var partStart = 0
            for (i in token.text.indices) {
                val c = token.text[i]
                if ((c == '-' || c == '/') && i > partStart) {
                    parts.add(partStart to (i + 1))
                    partStart = i + 1
                }
            }
            if (partStart < token.text.length) {
                parts.add(partStart to token.text.length)
            }
            if (parts.size <= 1) {
                result.add(token)
                continue
            }

            parts.forEachIndexed { pIdx, (startIdx, endIdx) ->
                val subText = token.text.substring(startIdx, endIdx)
                val subGlyphsRaw = if (token.glyphs.size == token.text.length) {
                    token.glyphs.subList(startIdx, endIdx)
                } else {
                    emptyList()
                }
                val sMs = subGlyphsRaw.firstOrNull()?.startMs
                    ?: (token.startMs + ((token.endMs - token.startMs) * startIdx.toDouble() / token.text.length).toLong())
                val eMs = maxOf(
                    sMs + MIN_WORD_DURATION_MS,
                    subGlyphsRaw.lastOrNull()?.endMs
                        ?: (token.startMs + ((token.endMs - token.startMs) * endIdx.toDouble() / token.text.length).toLong())
                )
                val subLen = subText.length.coerceAtLeast(1).toFloat()
                val rebasedGlyphs = if (subGlyphsRaw.isNotEmpty()) {
                    subGlyphsRaw.mapIndexed { idx, g ->
                        g.copy(fractionStart = idx / subLen, fractionEnd = (idx + 1) / subLen)
                    }
                } else {
                    subText.mapIndexed { idx, ch ->
                        val gs = sMs + ((eMs - sMs) * idx / subLen).toLong()
                        val ge = maxOf(gs + 15L, sMs + ((eMs - sMs) * (idx + 1) / subLen).toLong())
                        LyricGlyphSpan(ch.toString(), gs, ge, idx / subLen, (idx + 1) / subLen)
                    }
                }
                result.add(
                    LyricWordToken(
                        text = subText,
                        startMs = sMs,
                        endMs = eMs,
                        glyphs = rebasedGlyphs,
                        hasTrailingSpace = if (pIdx == parts.lastIndex) token.hasTrailingSpace else false,
                        isCjkToken = false
                    )
                )
            }
        }
        return result
    }

    private fun clampOverlappingWordEnds(tokens: List<LyricWordToken>): List<LyricWordToken> {
        if (tokens.size <= 1) return tokens
        return tokens.mapIndexed { index, token ->
            val nextStart = tokens.getOrNull(index + 1)?.startMs
            val clampedEnd = if (nextStart != null && token.endMs > nextStart && nextStart > token.startMs + MIN_WORD_DURATION_MS) {
                nextStart
            } else {
                maxOf(token.startMs + MIN_WORD_DURATION_MS, token.endMs)
            }
            if (clampedEnd == token.endMs) {
                token
            } else {
                val clampedGlyphs = token.glyphs.map { g ->
                    val gs = g.startMs.coerceAtMost(clampedEnd - 15L).coerceAtLeast(token.startMs)
                    val ge = g.endMs.coerceAtMost(clampedEnd).coerceAtLeast(gs + 15L)
                    g.copy(startMs = gs, endMs = ge)
                }
                token.copy(endMs = clampedEnd, glyphs = clampedGlyphs)
            }
        }
    }

    /**
     * Computes progressive horizontal sweep fraction `[0f..1f]` across [word]'s glyphs.
     */
    fun computeWordSweepProgress(word: LyricWordToken, positionMs: Long): Float {
        if (positionMs <= word.startMs) return 0f
        if (positionMs >= word.endMs) return 1f

        val glyphs = word.glyphs
        if (glyphs.isEmpty()) {
            val dur = (word.endMs - word.startMs).coerceAtLeast(1L).toFloat()
            return ((positionMs - word.startMs).toFloat() / dur).coerceIn(0f, 1f)
        }

        for (i in glyphs.indices) {
            val glyph = glyphs[i]
            if (positionMs < glyph.startMs) {
                return if (i == 0) 0f else glyph.fractionStart.coerceIn(0f, 1f)
            }
            if (positionMs <= glyph.endMs) {
                val dur = (glyph.endMs - glyph.startMs).coerceAtLeast(1L).toFloat()
                val local = ((positionMs - glyph.startMs).toFloat() / dur).coerceIn(0f, 1f)
                return (glyph.fractionStart + (glyph.fractionEnd - glyph.fractionStart) * local).coerceIn(0f, 1f)
            }
        }
        return 1f
    }

    /**
     * Computes the individual word/glyph-group dynamic scale, lift, sweep, and multi-layered glow
     * intensity for a single [LyricWordToken] at [positionMs].
     *
     * Upcoming unreached words stay smaller (`0.94f`) and dimmed; active words swell smoothly
     * along a continuous attack-peak-release curve (`~1.12x-1.16x`), lift gently, and settle
     * smoothly back to `1.00f` after passing.
     */
    fun computeWordVisualState(
        word: LyricWordToken,
        positionMs: Long,
        isLineActive: Boolean,
        isLinePast: Boolean,
        isSynchronized: Boolean
    ): WordVisualState {
        if (!isSynchronized) {
            return WordVisualState(
                sweepProgress = 1f,
                activeEnvelope = 0f,
                scale = 1.0f,
                liftDp = 0f,
                glowIntensity = 0f
            )
        }

        val timeSinceWordEnd = positionMs - word.endMs
        val inReleaseTail = timeSinceWordEnd in 0L until GLOW_RELEASE_TAIL_MS

        if (!isLineActive && !isLinePast) {
            return WordVisualState(
                sweepProgress = 0f,
                activeEnvelope = 0f,
                scale = 0.94f,
                liftDp = 0f,
                glowIntensity = 0f
            )
        }

        if (isLinePast && !inReleaseTail) {
            return WordVisualState(
                sweepProgress = 1f,
                activeEnvelope = 0f,
                scale = 1.0f,
                liftDp = 0f,
                glowIntensity = 0f
            )
        }

        val sweep = if (isLinePast) 1f else computeWordSweepProgress(word, positionMs)

        val activeEnvelope = when {
            !isLinePast && positionMs <= word.startMs -> 0f
            !isLinePast && positionMs <= word.endMs -> {
                val attack = smoothStep((sweep / 0.28f).coerceIn(0f, 1f))
                val arch = 0.72f + 0.28f * sin(sweep * PI.toFloat())
                (attack * arch).coerceIn(0f, 1f)
            }
            timeSinceWordEnd in 0L until SCALE_RELEASE_TAIL_MS -> {
                val tailRemaining = 1f - (timeSinceWordEnd.toFloat() / SCALE_RELEASE_TAIL_MS).coerceIn(0f, 1f)
                0.72f * smoothStep(tailRemaining)
            }
            else -> 0f
        }

        // Taper peak scale slightly on very long words so adjacent words keep crisp breathing room
        val peakBoost = when {
            word.text.length <= 5 -> 0.16f
            word.text.length <= 9 -> 0.14f
            else -> 0.12f
        }

        val baselineScale = if (isLinePast) {
            1.00f
        } else {
            0.94f + 0.06f * smoothStep((sweep / 0.35f).coerceIn(0f, 1f))
        }
        val scale = baselineScale + (peakBoost * activeEnvelope)
        val liftDp = -4.5f * activeEnvelope

        val glowIntensity = when {
            !isLinePast && positionMs <= word.startMs -> 0f
            !isLinePast && positionMs <= word.endMs -> {
                smoothStep((sweep / 0.18f).coerceIn(0f, 1f))
            }
            timeSinceWordEnd in 0L until GLOW_RELEASE_TAIL_MS -> {
                val fadeRemaining = 1f - (timeSinceWordEnd.toFloat() / GLOW_RELEASE_TAIL_MS).coerceIn(0f, 1f)
                val activeLineResidual = if (isLineActive) 0.12f else 0f
                activeLineResidual + (1f - activeLineResidual) * smoothStep(fadeRemaining)
            }
            isLineActive && sweep >= 1f -> 0.12f
            else -> 0f
        }

        return WordVisualState(
            sweepProgress = sweep,
            activeEnvelope = activeEnvelope,
            scale = scale,
            liftDp = liftDp,
            glowIntensity = glowIntensity.coerceIn(0f, 1f)
        )
    }

    internal fun smoothStep(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }
}
