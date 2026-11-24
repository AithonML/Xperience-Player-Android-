package com.xperience.player.service.ringtone

import com.xperience.player.domain.model.TrackItem
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.random.Random

/**
 * Destination target for a trimmed audio clip.
 */
enum class RingtoneTarget(
    val displayLabel: String,
    val shortLabel: String,
    val relativeDirectory: String,
    val requiresWriteSettings: Boolean
) {
    RINGTONE(
        displayLabel = "Tono de llamada",
        shortLabel = "Llamada",
        relativeDirectory = "Ringtones",
        requiresWriteSettings = true
    ),
    NOTIFICATION(
        displayLabel = "Notificación",
        shortLabel = "Notificación",
        relativeDirectory = "Notifications",
        requiresWriteSettings = true
    ),
    ALARM(
        displayLabel = "Alarma",
        shortLabel = "Alarma",
        relativeDirectory = "Alarms",
        requiresWriteSettings = true
    ),
    SAVE_CLIP_ONLY(
        displayLabel = "Solo guardar",
        shortLabel = "Guardado",
        relativeDirectory = "Ringtones",
        requiresWriteSettings = false
    )
}

/**
 * Quick trim duration/position presets shown in the ringtone editor.
 */
enum class TrimPreset(val label: String) {
    NOTIFICATION_5S("5 s"),
    CLIP_15S("15 s"),
    CLIP_30S("30 s"),
    CHORUS("Estribillo"),
    INTRO_15S("Intro")
}

/**
 * Active tab inside the Ringtone Editor UI.
 */
enum class RingtoneEditorTab(val label: String) {
    EDITOR("Recortar"),
    SAVED_CLIPS("Mis recortes")
}

/**
 * Represents a validated audio trim interval [startMs, endMs] in milliseconds.
 */
data class TrimRange(
    val startMs: Long,
    val endMs: Long
) {
    val durationMs: Long
        get() = (endMs - startMs).coerceAtLeast(0L)

    fun isValidFor(
        trackDurationMs: Long,
        minDurationMs: Long = MIN_DURATION_MS,
        maxDurationMs: Long = MAX_DURATION_MS
    ): Boolean {
        if (trackDurationMs <= 0L) return false
        val effectiveMin = effectiveMinDuration(trackDurationMs, minDurationMs)
        val effectiveMax = effectiveMaxDuration(trackDurationMs, effectiveMin, maxDurationMs)
        return startMs >= 0L &&
            endMs <= trackDurationMs &&
            endMs > startMs &&
            durationMs in effectiveMin..effectiveMax
    }

    fun startFraction(trackDurationMs: Long): Float {
        if (trackDurationMs <= 0L) return 0f
        return (startMs.toFloat() / trackDurationMs.toFloat()).coerceIn(0f, 1f)
    }

    fun endFraction(trackDurationMs: Long): Float {
        if (trackDurationMs <= 0L) return 0f
        return (endMs.toFloat() / trackDurationMs.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * Moves the start handle while keeping [endMs] anchored whenever possible,
     * clamping within [minDurationMs, maxDurationMs] and [0, trackDurationMs].
     */
    fun withStart(
        newStartMs: Long,
        trackDurationMs: Long,
        minDurationMs: Long = MIN_DURATION_MS,
        maxDurationMs: Long = MAX_DURATION_MS
    ): TrimRange {
        if (trackDurationMs <= 0L) return ZERO
        val effectiveMin = effectiveMinDuration(trackDurationMs, minDurationMs)
        val effectiveMax = effectiveMaxDuration(trackDurationMs, effectiveMin, maxDurationMs)
        val clampedEnd = endMs.coerceIn(effectiveMin, trackDurationMs)
        val minAllowedStart = (clampedEnd - effectiveMax).coerceAtLeast(0L)
        val maxAllowedStart = (clampedEnd - effectiveMin).coerceAtLeast(0L)
        val clampedStart = newStartMs.coerceIn(minAllowedStart, maxAllowedStart)
        return TrimRange(clampedStart, clampedEnd)
    }

    /**
     * Moves the end handle while keeping [startMs] anchored whenever possible,
     * clamping within [minDurationMs, maxDurationMs] and [0, trackDurationMs].
     */
    fun withEnd(
        newEndMs: Long,
        trackDurationMs: Long,
        minDurationMs: Long = MIN_DURATION_MS,
        maxDurationMs: Long = MAX_DURATION_MS
    ): TrimRange {
        if (trackDurationMs <= 0L) return ZERO
        val effectiveMin = effectiveMinDuration(trackDurationMs, minDurationMs)
        val effectiveMax = effectiveMaxDuration(trackDurationMs, effectiveMin, maxDurationMs)
        val clampedStart = startMs.coerceIn(0L, (trackDurationMs - effectiveMin).coerceAtLeast(0L))
        val minAllowedEnd = (clampedStart + effectiveMin).coerceAtMost(trackDurationMs)
        val maxAllowedEnd = (clampedStart + effectiveMax).coerceAtMost(trackDurationMs)
        val clampedEnd = newEndMs.coerceIn(minAllowedEnd, maxAllowedEnd)
        return TrimRange(clampedStart, clampedEnd)
    }

    fun nudgeStart(
        deltaMs: Long,
        trackDurationMs: Long,
        minDurationMs: Long = MIN_DURATION_MS,
        maxDurationMs: Long = MAX_DURATION_MS
    ): TrimRange = withStart(startMs + deltaMs, trackDurationMs, minDurationMs, maxDurationMs)

    fun nudgeEnd(
        deltaMs: Long,
        trackDurationMs: Long,
        minDurationMs: Long = MIN_DURATION_MS,
        maxDurationMs: Long = MAX_DURATION_MS
    ): TrimRange = withEnd(endMs + deltaMs, trackDurationMs, minDurationMs, maxDurationMs)

    /**
     * Slides the entire selected window by [deltaMs] while preserving [durationMs].
     */
    fun shiftBy(
        deltaMs: Long,
        trackDurationMs: Long,
        minDurationMs: Long = MIN_DURATION_MS,
        maxDurationMs: Long = MAX_DURATION_MS
    ): TrimRange {
        if (trackDurationMs <= 0L) return ZERO
        val normalized = createClamped(startMs, endMs, trackDurationMs, minDurationMs, maxDurationMs)
        val windowLen = normalized.durationMs
        val maxStart = (trackDurationMs - windowLen).coerceAtLeast(0L)
        val nextStart = (normalized.startMs + deltaMs).coerceIn(0L, maxStart)
        return TrimRange(nextStart, (nextStart + windowLen).coerceAtMost(trackDurationMs))
    }

    companion object {
        const val MIN_DURATION_MS: Long = 1_000L
        const val MAX_DURATION_MS: Long = 60_000L
        const val DEFAULT_CLIP_DURATION_MS: Long = 30_000L
        const val NUDGE_STEP_MS: Long = 500L
        val ZERO = TrimRange(0L, 0L)

        internal fun effectiveMinDuration(trackDurationMs: Long, minDurationMs: Long): Long {
            if (trackDurationMs <= 0L) return 0L
            return minOf(minDurationMs.coerceAtLeast(1L), trackDurationMs)
        }

        internal fun effectiveMaxDuration(
            trackDurationMs: Long,
            effectiveMin: Long,
            maxDurationMs: Long
        ): Long {
            if (trackDurationMs <= 0L) return 0L
            return minOf(maxDurationMs.coerceAtLeast(effectiveMin), trackDurationMs)
        }

        fun defaultForTrack(
            trackDurationMs: Long,
            preferredDurationMs: Long = DEFAULT_CLIP_DURATION_MS,
            minDurationMs: Long = MIN_DURATION_MS,
            maxDurationMs: Long = MAX_DURATION_MS
        ): TrimRange {
            if (trackDurationMs <= 0L) return ZERO
            val effectiveMin = effectiveMinDuration(trackDurationMs, minDurationMs)
            val effectiveMax = effectiveMaxDuration(trackDurationMs, effectiveMin, maxDurationMs)
            val end = preferredDurationMs.coerceIn(effectiveMin, effectiveMax)
            return TrimRange(0L, end)
        }

        fun createClamped(
            startMs: Long,
            endMs: Long,
            trackDurationMs: Long,
            minDurationMs: Long = MIN_DURATION_MS,
            maxDurationMs: Long = MAX_DURATION_MS
        ): TrimRange {
            if (trackDurationMs <= 0L) return ZERO
            val effectiveMin = effectiveMinDuration(trackDurationMs, minDurationMs)
            val effectiveMax = effectiveMaxDuration(trackDurationMs, effectiveMin, maxDurationMs)

            val orderedStart = minOf(startMs, endMs).coerceIn(0L, trackDurationMs)
            val orderedEnd = maxOf(startMs, endMs).coerceIn(0L, trackDurationMs)

            val maxAllowedStart = (trackDurationMs - effectiveMin).coerceAtLeast(0L)
            var clampedStart = orderedStart.coerceIn(0L, maxAllowedStart)
            var clampedEnd = orderedEnd.coerceIn(
                (clampedStart + effectiveMin).coerceAtMost(trackDurationMs),
                (clampedStart + effectiveMax).coerceAtMost(trackDurationMs)
            )

            // If orderedEnd was near track end and duration fell below effectiveMin, pull start back
            if (clampedEnd - clampedStart < effectiveMin) {
                clampedStart = (clampedEnd - effectiveMin).coerceAtLeast(0L)
            }
            if (clampedEnd - clampedStart > effectiveMax) {
                clampedEnd = (clampedStart + effectiveMax).coerceAtMost(trackDurationMs)
            }
            return TrimRange(clampedStart, clampedEnd)
        }

        fun applyPreset(
            preset: TrimPreset,
            trackDurationMs: Long,
            anchorStartMs: Long = 0L,
            minDurationMs: Long = MIN_DURATION_MS,
            maxDurationMs: Long = MAX_DURATION_MS
        ): TrimRange {
            if (trackDurationMs <= 0L) return ZERO
            return when (preset) {
                TrimPreset.NOTIFICATION_5S -> {
                    val targetDur = minOf(5_000L, trackDurationMs)
                    val start = anchorStartMs.coerceIn(0L, (trackDurationMs - targetDur).coerceAtLeast(0L))
                    createClamped(start, start + targetDur, trackDurationMs, minDurationMs, maxDurationMs)
                }
                TrimPreset.CLIP_15S -> {
                    val targetDur = minOf(15_000L, trackDurationMs)
                    val start = anchorStartMs.coerceIn(0L, (trackDurationMs - targetDur).coerceAtLeast(0L))
                    createClamped(start, start + targetDur, trackDurationMs, minDurationMs, maxDurationMs)
                }
                TrimPreset.CLIP_30S -> {
                    val targetDur = minOf(30_000L, trackDurationMs)
                    val start = anchorStartMs.coerceIn(0L, (trackDurationMs - targetDur).coerceAtLeast(0L))
                    createClamped(start, start + targetDur, trackDurationMs, minDurationMs, maxDurationMs)
                }
                TrimPreset.CHORUS -> {
                    val targetDur = minOf(30_000L, trackDurationMs)
                    val estimatedStart = if (trackDurationMs > targetDur) {
                        (trackDurationMs * 0.30).roundToLong()
                            .coerceIn(0L, trackDurationMs - targetDur)
                    } else {
                        0L
                    }
                    createClamped(estimatedStart, estimatedStart + targetDur, trackDurationMs, minDurationMs, maxDurationMs)
                }
                TrimPreset.INTRO_15S -> {
                    val targetDur = minOf(15_000L, trackDurationMs)
                    createClamped(0L, targetDur, trackDurationMs, minDurationMs, maxDurationMs)
                }
            }
        }
    }
}

/**
 * Normalized waveform amplitude bars (0f..1f) rendered by the interactive ringtone trimmer.
 */
data class WaveformData(
    val peaks: List<Float>,
    val durationMs: Long,
    val isRealWaveform: Boolean
) {
    val barCount: Int get() = peaks.size

    companion object {
        const val DEFAULT_BAR_COUNT: Int = 96

        fun empty(barCount: Int = DEFAULT_BAR_COUNT): WaveformData = WaveformData(
            peaks = List(barCount.coerceAtLeast(1)) { 0.14f },
            durationMs = 0L,
            isRealWaveform = false
        )
    }
}

/**
 * Pure Kotlin waveform normalization and deterministic fallback generator.
 */
object WaveformNormalizer {
    const val DEFAULT_MIN_FLOOR: Float = 0.10f
    const val DEFAULT_GAMMA: Float = 0.72f

    fun normalizePeaks(
        rawPeaks: FloatArray,
        minFloor: Float = DEFAULT_MIN_FLOOR,
        gamma: Float = DEFAULT_GAMMA
    ): List<Float> {
        if (rawPeaks.isEmpty()) return emptyList()
        val floor = minFloor.coerceIn(0.01f, 0.5f)
        val safeGamma = gamma.coerceIn(0.2f, 2.0f)

        var maxPeak = 0f
        val sanitized = FloatArray(rawPeaks.size) { i ->
            val v = rawPeaks[i]
            val clean = if (v.isFinite()) abs(v) else 0f
            if (clean > maxPeak) maxPeak = clean
            clean
        }

        if (maxPeak <= 1e-6f) {
            return List(rawPeaks.size) { floor }
        }

        return List(sanitized.size) { i ->
            val linear = (sanitized[i] / maxPeak).coerceIn(0f, 1f)
            val shaped = linear.toDouble().pow(safeGamma.toDouble()).toFloat()
            (floor + shaped * (1f - floor)).coerceIn(floor, 1f)
        }
    }

    /**
     * Generates a smooth, deterministic musical waveform when an exotic codec cannot be decoded.
     */
    fun generateFallbackWaveform(
        seed: Int,
        barCount: Int = WaveformData.DEFAULT_BAR_COUNT
    ): List<Float> {
        val count = barCount.coerceAtLeast(1)
        val random = Random(seed)
        val raw = FloatArray(count)
        var cursor = 0.48f
        for (i in 0 until count) {
            val pos = i.toFloat() / count.toFloat()
            // Musical macro envelope (intro -> chorus lift -> bridge -> outro)
            val macroEnvelope = 0.45f + 0.35f * sin(pos * Math.PI).toFloat() +
                0.15f * sin(pos * Math.PI * 5.0).toFloat()
            cursor = (cursor * 0.55f + macroEnvelope * 0.30f + random.nextFloat() * 0.35f)
                .coerceIn(0.12f, 1.0f)
            raw[i] = cursor
        }
        return normalizePeaks(raw, minFloor = 0.14f, gamma = 0.85f)
    }
}

/**
 * Pure Kotlin metadata & file naming sanitizer and timestamp formatter.
 */
object RingtoneMetadataSanitizer {
    private const val MAX_TITLE_LENGTH = 80
    private const val MAX_FILENAME_BASE_LENGTH = 64
    private val ILLEGAL_FILENAME_CHARS = Regex("""[\\/:*?"<>|\u0000-\u001F\u007F]""")
    private val CONTROL_CHARS = Regex("""[\u0000-\u001F\u007F]""")
    private val MULTI_WHITESPACE = Regex("""\s+""")
    private val MULTI_UNDERSCORE = Regex("""_+""")

    fun sanitizeDisplayTitle(
        rawTitle: String?,
        fallback: String = "Recorte Xperience"
    ): String {
        val cleaned = rawTitle
            .orEmpty()
            .replace(CONTROL_CHARS, " ")
            .replace(MULTI_WHITESPACE, " ")
            .trim()
        if (cleaned.isEmpty()) return fallback
        return cleaned.take(MAX_TITLE_LENGTH).trim().ifEmpty { fallback }
    }

    fun sanitizeArtist(rawArtist: String?): String {
        val cleaned = sanitizeDisplayTitle(rawArtist, fallback = "Xperience Player")
        return if (cleaned.equals("<unknown>", ignoreCase = true)) "Xperience Player" else cleaned
    }

    fun sanitizeFileNameBase(
        rawName: String?,
        fallback: String = "xperience_ringtone"
    ): String {
        val cleaned = rawName
            .orEmpty()
            .replace(ILLEGAL_FILENAME_CHARS, "_")
            .replace(MULTI_WHITESPACE, " ")
            .trim()
            .trim('.', ' ', '_')
            .replace(MULTI_UNDERSCORE, "_")
            .take(MAX_FILENAME_BASE_LENGTH)
            .trim('.', ' ', '_')

        return if (cleaned.isEmpty() || cleaned == "." || cleaned == "..") {
            fallback
        } else {
            cleaned
        }
    }

    fun buildM4aFileName(rawName: String?, uniqueSuffix: String = ""): String {
        val base = sanitizeFileNameBase(rawName)
        val suffix = uniqueSuffix.trim()
        return if (suffix.isEmpty()) "$base.m4a" else "${base}_$suffix.m4a"
    }

    fun defaultClipName(trackTitle: String): String {
        val base = sanitizeDisplayTitle(trackTitle, fallback = "Tono")
        val suffix = " (Recorte)"
        val maxBase = (MAX_TITLE_LENGTH - suffix.length).coerceAtLeast(12)
        return "${base.take(maxBase).trimEnd()}$suffix"
    }

    /**
     * Formats milliseconds as `m:ss.d` (e.g. `0:30.5`, `1:05.0`).
     */
    fun formatTrimTimeMs(ms: Long): String {
        val safeMs = ms.coerceAtLeast(0L)
        val totalTenths = (safeMs + 50L) / 100L
        val tenths = totalTenths % 10L
        val totalSeconds = totalTenths / 10L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%d:%02d.%d", minutes, seconds, tenths)
    }

    /**
     * Formats clip duration compactly (e.g. `15.0 s` or `1:00.0`).
     */
    fun formatDurationLabel(ms: Long): String {
        val safeMs = ms.coerceAtLeast(0L)
        return if (safeMs < 60_000L) {
            val totalTenths = (safeMs + 50L) / 100L
            String.format(Locale.US, "%d.%d s", totalTenths / 10L, totalTenths % 10L)
        } else {
            formatTrimTimeMs(safeMs)
        }
    }
}

/**
 * Previously trimmed and saved ringtone clip stored in Android MediaStore.
 */
data class SavedRingtoneClip(
    val id: Long,
    val contentUri: String,
    val title: String,
    val artist: String,
    val displayName: String,
    val durationMs: Long,
    val dateAddedMs: Long,
    val target: RingtoneTarget,
    val sizeBytes: Long = 0L,
    val relativePath: String = ""
)

/**
 * Source kind badge shown in the Ringtone Editor UI.
 */
enum class ResolvedSourceKind(val label: String) {
    LOCAL_FILE("Archivo local"),
    CACHED_ONLINE("Audio en caché"),
    DOWNLOADED_TEMP("Descargado para recorte"),
    UNKNOWN("Audio")
}

/**
 * Result of resolving any [TrackItem] (local MediaStore, SAF, file, or YouTube/remote stream)
 * into a local readable URI for waveform extraction, preview playback, and trimming.
 */
data class ResolvedLocalAudio(
    val uriString: String,
    val durationMs: Long,
    val sourceKind: ResolvedSourceKind,
    val isTemporaryFile: Boolean = false
)

/**
 * Pipeline stage during ringtone trim / encode / save / apply.
 */
enum class RingtoneExportStage {
    IDLE,
    RESOLVING_AUDIO,
    TRIMMING,
    ENCODING,
    SAVING,
    APPLYING,
    COMPLETED,
    ERROR
}

data class RingtoneExportProgress(
    val stage: RingtoneExportStage = RingtoneExportStage.IDLE,
    val fraction: Float = 0f,
    val message: String = ""
) {
    val isActive: Boolean
        get() = stage == RingtoneExportStage.RESOLVING_AUDIO ||
            stage == RingtoneExportStage.TRIMMING ||
            stage == RingtoneExportStage.ENCODING ||
            stage == RingtoneExportStage.SAVING ||
            stage == RingtoneExportStage.APPLYING
}

/**
 * Parameters passed to [AudioTrimmerEngine] for trimming & encoding a clip.
 */
data class RingtoneTrimRequest(
    val sourceUri: String,
    val range: TrimRange,
    val clipName: String,
    val fadeInEnabled: Boolean,
    val fadeOutEnabled: Boolean,
    val fadeDurationMs: Long = DEFAULT_FADE_DURATION_MS
) {
    companion object {
        const val DEFAULT_FADE_DURATION_MS: Long = 1_500L
    }
}

/**
 * Immutable UI state for the Ringtone Editor & Saved Clips Manager.
 */
data class RingtoneUiState(
    val track: TrackItem? = null,
    val resolvedAudio: ResolvedLocalAudio? = null,
    val effectiveDurationMs: Long = 0L,
    val trimRange: TrimRange = TrimRange.ZERO,
    val waveform: WaveformData = WaveformData.empty(),
    val isLoadingSource: Boolean = false,
    val sourceStatusMessage: String = "",
    val isExtractingWaveform: Boolean = false,
    val isPreviewPlaying: Boolean = false,
    val previewPositionMs: Long = 0L,
    val previewingClipId: Long? = null,
    val fadeInEnabled: Boolean = true,
    val fadeOutEnabled: Boolean = true,
    val fadeDurationMs: Long = RingtoneTrimRequest.DEFAULT_FADE_DURATION_MS,
    val clipName: String = "",
    val selectedTarget: RingtoneTarget = RingtoneTarget.RINGTONE,
    val canWriteSettings: Boolean = false,
    val showPermissionPrompt: Boolean = false,
    val pendingApplyAfterPermission: Boolean = false,
    val pendingClipReapplyAfterPermission: Pair<SavedRingtoneClip, RingtoneTarget>? = null,
    val exportProgress: RingtoneExportProgress = RingtoneExportProgress(),
    val savedClips: List<SavedRingtoneClip> = emptyList(),
    val isLoadingSavedClips: Boolean = false,
    val activeTab: RingtoneEditorTab = RingtoneEditorTab.EDITOR,
    val feedbackMessage: String? = null,
    val isFeedbackError: Boolean = false,
    val sourceErrorMessage: String? = null
) {
    val isExporting: Boolean
        get() = exportProgress.isActive

    val canSaveOrApply: Boolean
        get() = resolvedAudio != null &&
            !isLoadingSource &&
            !isExporting &&
            trimRange.isValidFor(effectiveDurationMs)

    val needsWritePermissionForSelectedTarget: Boolean
        get() = selectedTarget.requiresWriteSettings && !canWriteSettings
}
