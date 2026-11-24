package com.xperience.player.ui.ringtone

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.service.ringtone.AudioTrimmerEngine
import com.xperience.player.service.ringtone.AudioWaveformExtractor
import com.xperience.player.service.ringtone.ResolvedLocalAudio
import com.xperience.player.service.ringtone.RingtoneAudioStreamResolver
import com.xperience.player.service.ringtone.RingtoneEditorTab
import com.xperience.player.service.ringtone.RingtoneExportProgress
import com.xperience.player.service.ringtone.RingtoneExportStage
import com.xperience.player.service.ringtone.RingtoneMetadataSanitizer
import com.xperience.player.service.ringtone.RingtonePreviewPlayer
import com.xperience.player.service.ringtone.RingtoneResolvedStream
import com.xperience.player.service.ringtone.RingtoneStorageManager
import com.xperience.player.service.ringtone.RingtoneTarget
import com.xperience.player.service.ringtone.RingtoneTrimRequest
import com.xperience.player.service.ringtone.RingtoneUiState
import com.xperience.player.service.ringtone.SavedRingtoneClip
import com.xperience.player.service.ringtone.TrimPreset
import com.xperience.player.service.ringtone.TrimRange
import com.xperience.player.service.ringtone.WaveformData
import com.xperience.player.service.ringtone.WriteSettingsPermissionRequiredException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * Pure abstractions behind [RingtoneEditorController] so all state transitions can be
 * thoroughly unit-tested on the JVM without Android framework mocks.
 */
interface RingtoneSourceGateway {
    suspend fun resolveTrackAudio(
        track: TrackItem,
        onStatus: (String) -> Unit
    ): Result<ResolvedLocalAudio>
}

interface RingtoneWaveformGateway {
    suspend fun extractWaveform(
        uriString: String,
        barCount: Int,
        fallbackSeed: Int,
        knownDurationMs: Long
    ): WaveformData
}

interface RingtoneTrimGateway {
    suspend fun trimToM4a(
        request: RingtoneTrimRequest,
        onProgress: (RingtoneExportProgress) -> Unit
    ): Result<File>
}

interface RingtoneStorageGateway {
    fun canWriteSettings(): Boolean
    suspend fun saveClipToMediaStore(
        trimmedM4aFile: File,
        clipTitle: String,
        artist: String,
        durationMs: Long,
        target: RingtoneTarget
    ): Result<SavedRingtoneClip>

    suspend fun applyDefaultTone(
        contentUriString: String,
        target: RingtoneTarget
    ): Result<Unit>

    suspend fun listSavedClips(): List<SavedRingtoneClip>
    suspend fun deleteClip(clip: SavedRingtoneClip): Result<Unit>
}

interface RingtonePreviewGateway {
    val isPlaying: StateFlow<Boolean>
    val positionMs: StateFlow<Long>
    val previewingClipId: StateFlow<Long?>

    fun toggleSegmentPreview(
        uriString: String,
        range: TrimRange,
        fadeInEnabled: Boolean,
        fadeOutEnabled: Boolean,
        fadeDurationMs: Long
    )

    fun startSegmentPreview(
        uriString: String,
        range: TrimRange,
        fadeInEnabled: Boolean,
        fadeOutEnabled: Boolean,
        fadeDurationMs: Long,
        seekPosMs: Long
    )

    fun updateLiveSegmentParameters(
        range: TrimRange,
        fadeInEnabled: Boolean,
        fadeOutEnabled: Boolean,
        fadeDurationMs: Long
    )

    fun seekSegmentTo(positionMs: Long)
    fun toggleSavedClipPreview(clip: SavedRingtoneClip)
    fun stop()
    fun release()
}

/**
 * State holder & orchestrator for the Ringtone Editor and Saved Clips Manager.
 */
class RingtoneEditorController(
    private val sourceGateway: RingtoneSourceGateway,
    private val waveformGateway: RingtoneWaveformGateway,
    private val trimGateway: RingtoneTrimGateway,
    private val storageGateway: RingtoneStorageGateway,
    private val previewGateway: RingtonePreviewGateway,
    private val scope: CoroutineScope,
    private val ownsScope: Boolean = false
) {
    private val _uiState = MutableStateFlow(
        RingtoneUiState(canWriteSettings = storageGateway.canWriteSettings())
    )
    val uiState: StateFlow<RingtoneUiState> = _uiState.asStateFlow()

    private var sourceLoadJob: Job? = null
    private var waveformJob: Job? = null
    private var exportJob: Job? = null
    private var savedClipsJob: Job? = null

    init {
        scope.launch {
            previewGateway.isPlaying.collect { playing ->
                _uiState.update { it.copy(isPreviewPlaying = playing) }
            }
        }
        scope.launch {
            previewGateway.positionMs.collect { pos ->
                _uiState.update { it.copy(previewPositionMs = pos) }
            }
        }
        scope.launch {
            previewGateway.previewingClipId.collect { clipId ->
                _uiState.update { it.copy(previewingClipId = clipId) }
            }
        }
    }

    fun loadTrack(track: TrackItem) {
        sourceLoadJob?.cancel()
        waveformJob?.cancel()
        previewGateway.stop()

        val hasPermission = storageGateway.canWriteSettings()
        val initialDuration = track.durationMs.coerceAtLeast(0L)
        val initialRange = TrimRange.defaultForTrack(initialDuration)

        _uiState.update {
            it.copy(
                track = track,
                resolvedAudio = null,
                effectiveDurationMs = initialDuration,
                trimRange = initialRange,
                waveform = WaveformData.empty(),
                isLoadingSource = true,
                sourceStatusMessage = "Preparando audio…",
                isExtractingWaveform = false,
                previewPositionMs = initialRange.startMs,
                clipName = RingtoneMetadataSanitizer.defaultClipName(track.title),
                canWriteSettings = hasPermission,
                showPermissionPrompt = false,
                pendingApplyAfterPermission = false,
                pendingClipReapplyAfterPermission = null,
                exportProgress = RingtoneExportProgress(),
                sourceErrorMessage = null,
                feedbackMessage = null,
                isFeedbackError = false
            )
        }

        refreshSavedClips()

        sourceLoadJob = scope.launch {
            val result = sourceGateway.resolveTrackAudio(track) { status ->
                _uiState.update { state -> state.copy(sourceStatusMessage = status) }
            }
            result.fold(
                onSuccess = { resolved ->
                    val effectiveDuration = resolved.durationMs
                        .takeIf { it > 0L }
                        ?: track.durationMs.coerceAtLeast(0L)
                    val range = TrimRange.defaultForTrack(effectiveDuration)
                    _uiState.update { state ->
                        state.copy(
                            resolvedAudio = resolved,
                            effectiveDurationMs = effectiveDuration,
                            trimRange = range,
                            previewPositionMs = range.startMs,
                            isLoadingSource = false,
                            sourceStatusMessage = "",
                            isExtractingWaveform = true,
                            sourceErrorMessage = null
                        )
                    }
                    extractWaveformForResolved(track, resolved, effectiveDuration)
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _uiState.update { state ->
                        state.copy(
                            isLoadingSource = false,
                            sourceStatusMessage = "",
                            isExtractingWaveform = false,
                            sourceErrorMessage = error.message
                                ?: "No se pudo cargar el audio de esta canción."
                        )
                    }
                }
            )
        }
    }

    private fun extractWaveformForResolved(
        track: TrackItem,
        resolved: ResolvedLocalAudio,
        knownDurationMs: Long
    ) {
        waveformJob?.cancel()
        waveformJob = scope.launch {
            val waveform = try {
                waveformGateway.extractWaveform(
                    uriString = resolved.uriString,
                    barCount = WaveformData.DEFAULT_BAR_COUNT,
                    fallbackSeed = track.id.hashCode(),
                    knownDurationMs = knownDurationMs
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                WaveformData(
                    peaks = com.xperience.player.service.ringtone.WaveformNormalizer.generateFallbackWaveform(
                        seed = track.id.hashCode(),
                        barCount = WaveformData.DEFAULT_BAR_COUNT
                    ),
                    durationMs = knownDurationMs,
                    isRealWaveform = false
                )
            }

            _uiState.update { state ->
                val refinedDuration = when {
                    state.effectiveDurationMs > 0L -> state.effectiveDurationMs
                    waveform.durationMs > 0L -> waveform.durationMs
                    else -> state.effectiveDurationMs
                }
                val refinedRange = if (state.trimRange.durationMs <= 0L && refinedDuration > 0L) {
                    TrimRange.defaultForTrack(refinedDuration)
                } else if (refinedDuration > 0L) {
                    TrimRange.createClamped(state.trimRange.startMs, state.trimRange.endMs, refinedDuration)
                } else {
                    state.trimRange
                }
                state.copy(
                    waveform = waveform,
                    effectiveDurationMs = refinedDuration,
                    trimRange = refinedRange,
                    previewPositionMs = state.previewPositionMs.coerceIn(
                        refinedRange.startMs,
                        refinedRange.endMs.coerceAtLeast(refinedRange.startMs)
                    ),
                    isExtractingWaveform = false
                )
            }
        }
    }

    fun retryLoadSource() {
        val track = _uiState.value.track ?: return
        loadTrack(track)
    }

    fun updateStartMs(newStartMs: Long) {
        val state = _uiState.value
        val dur = state.effectiveDurationMs
        if (dur <= 0L) return
        val next = state.trimRange.withStart(newStartMs, dur)
        applyUpdatedRange(next)
    }

    fun updateEndMs(newEndMs: Long) {
        val state = _uiState.value
        val dur = state.effectiveDurationMs
        if (dur <= 0L) return
        val next = state.trimRange.withEnd(newEndMs, dur)
        applyUpdatedRange(next)
    }

    fun shiftRangeBy(deltaMs: Long) {
        val state = _uiState.value
        val dur = state.effectiveDurationMs
        if (dur <= 0L) return
        val next = state.trimRange.shiftBy(deltaMs, dur)
        applyUpdatedRange(next)
    }

    fun nudgeStart(deltaMs: Long) {
        val state = _uiState.value
        val dur = state.effectiveDurationMs
        if (dur <= 0L) return
        val next = state.trimRange.nudgeStart(deltaMs, dur)
        applyUpdatedRange(next)
    }

    fun nudgeEnd(deltaMs: Long) {
        val state = _uiState.value
        val dur = state.effectiveDurationMs
        if (dur <= 0L) return
        val next = state.trimRange.nudgeEnd(deltaMs, dur)
        applyUpdatedRange(next)
    }

    fun applyPreset(preset: TrimPreset) {
        val state = _uiState.value
        val dur = state.effectiveDurationMs
        if (dur <= 0L) return
        val next = TrimRange.applyPreset(
            preset = preset,
            trackDurationMs = dur,
            anchorStartMs = state.trimRange.startMs
        )
        applyUpdatedRange(next)
    }

    private fun applyUpdatedRange(nextRange: TrimRange) {
        _uiState.update { state ->
            val clampedPlayhead = state.previewPositionMs.coerceIn(
                nextRange.startMs,
                nextRange.endMs.coerceAtLeast(nextRange.startMs)
            )
            state.copy(
                trimRange = nextRange,
                previewPositionMs = clampedPlayhead
            )
        }
        val latest = _uiState.value
        previewGateway.updateLiveSegmentParameters(
            range = nextRange,
            fadeInEnabled = latest.fadeInEnabled,
            fadeOutEnabled = latest.fadeOutEnabled,
            fadeDurationMs = latest.fadeDurationMs
        )
    }

    fun toggleFadeIn() {
        _uiState.update { it.copy(fadeInEnabled = !it.fadeInEnabled) }
        syncPreviewFadeParams()
    }

    fun toggleFadeOut() {
        _uiState.update { it.copy(fadeOutEnabled = !it.fadeOutEnabled) }
        syncPreviewFadeParams()
    }

    private fun syncPreviewFadeParams() {
        val s = _uiState.value
        previewGateway.updateLiveSegmentParameters(
            range = s.trimRange,
            fadeInEnabled = s.fadeInEnabled,
            fadeOutEnabled = s.fadeOutEnabled,
            fadeDurationMs = s.fadeDurationMs
        )
    }

    fun updateClipName(name: String) {
        _uiState.update { it.copy(clipName = name.take(80)) }
    }

    fun selectTarget(target: RingtoneTarget) {
        val hasPerm = storageGateway.canWriteSettings()
        _uiState.update { state ->
            state.copy(
                selectedTarget = target,
                canWriteSettings = hasPerm,
                showPermissionPrompt = if (!target.requiresWriteSettings) false else state.showPermissionPrompt
            )
        }
    }

    fun selectTab(tab: RingtoneEditorTab) {
        if (_uiState.value.activeTab == tab) return
        previewGateway.stop()
        _uiState.update { it.copy(activeTab = tab) }
        if (tab == RingtoneEditorTab.SAVED_CLIPS) {
            refreshSavedClips()
        }
    }

    fun toggleSegmentPreview() {
        val s = _uiState.value
        val source = s.resolvedAudio ?: return
        if (!s.trimRange.isValidFor(s.effectiveDurationMs)) return
        previewGateway.toggleSegmentPreview(
            uriString = source.uriString,
            range = s.trimRange,
            fadeInEnabled = s.fadeInEnabled,
            fadeOutEnabled = s.fadeOutEnabled,
            fadeDurationMs = s.fadeDurationMs
        )
    }

    fun restartSegmentPreview() {
        val s = _uiState.value
        val source = s.resolvedAudio ?: return
        if (!s.trimRange.isValidFor(s.effectiveDurationMs)) return
        previewGateway.startSegmentPreview(
            uriString = source.uriString,
            range = s.trimRange,
            fadeInEnabled = s.fadeInEnabled,
            fadeOutEnabled = s.fadeOutEnabled,
            fadeDurationMs = s.fadeDurationMs,
            seekPosMs = s.trimRange.startMs
        )
    }

    fun seekPreviewTo(positionMs: Long) {
        val s = _uiState.value
        val clamped = positionMs.coerceIn(s.trimRange.startMs, s.trimRange.endMs.coerceAtLeast(s.trimRange.startMs))
        _uiState.update { it.copy(previewPositionMs = clamped) }
        if (s.isPreviewPlaying && s.previewingClipId == null) {
            previewGateway.seekSegmentTo(clamped)
        } else {
            val source = s.resolvedAudio ?: return
            if (s.trimRange.isValidFor(s.effectiveDurationMs)) {
                previewGateway.startSegmentPreview(
                    uriString = source.uriString,
                    range = s.trimRange,
                    fadeInEnabled = s.fadeInEnabled,
                    fadeOutEnabled = s.fadeOutEnabled,
                    fadeDurationMs = s.fadeDurationMs,
                    seekPosMs = clamped
                )
            }
        }
    }

    fun stopPreview() {
        previewGateway.stop()
    }

    fun toggleSavedClipPreview(clip: SavedRingtoneClip) {
        previewGateway.toggleSavedClipPreview(clip)
    }

    fun refreshSavedClips() {
        savedClipsJob?.cancel()
        savedClipsJob = scope.launch {
            _uiState.update { it.copy(isLoadingSavedClips = true) }
            val clips = runCatching { storageGateway.listSavedClips() }.getOrDefault(emptyList())
            _uiState.update { it.copy(savedClips = clips, isLoadingSavedClips = false) }
        }
    }

    /**
     * Re-checks `Settings.System.canWrite` (e.g. when returning from System Settings) and
     * automatically resumes any pending apply action once permission is granted.
     */
    fun onWriteSettingsPermissionReturned() {
        val granted = storageGateway.canWriteSettings()
        val snapshot = _uiState.value
        _uiState.update {
            it.copy(
                canWriteSettings = granted,
                showPermissionPrompt = if (granted) false else it.showPermissionPrompt
            )
        }

        if (granted) {
            val pendingReapply = snapshot.pendingClipReapplyAfterPermission
            if (pendingReapply != null) {
                _uiState.update { it.copy(pendingClipReapplyAfterPermission = null) }
                reapplySavedClip(pendingReapply.first, pendingReapply.second)
            } else if (snapshot.pendingApplyAfterPermission) {
                _uiState.update { it.copy(pendingApplyAfterPermission = false) }
                saveAndApplyCurrentClip()
            }
        }
    }

    fun requestWriteSettingsPermissionPrompt() {
        _uiState.update { it.copy(showPermissionPrompt = true) }
    }

    fun dismissPermissionPrompt() {
        _uiState.update {
            it.copy(
                showPermissionPrompt = false,
                pendingApplyAfterPermission = false,
                pendingClipReapplyAfterPermission = null
            )
        }
    }

    fun saveAndApplyCurrentClip() {
        val state = _uiState.value
        val track = state.track ?: return
        val resolved = state.resolvedAudio ?: return
        if (!state.trimRange.isValidFor(state.effectiveDurationMs) || state.isExporting) return

        previewGateway.stop()

        val hasPermission = storageGateway.canWriteSettings()
        _uiState.update { it.copy(canWriteSettings = hasPermission) }

        if (state.selectedTarget.requiresWriteSettings && !hasPermission) {
            _uiState.update {
                it.copy(
                    showPermissionPrompt = true,
                    pendingApplyAfterPermission = true,
                    feedbackMessage = "Otorga el permiso de ajustes del sistema para establecer ${state.selectedTarget.displayLabel.lowercase()}.",
                    isFeedbackError = false
                )
            }
            return
        }

        val target = state.selectedTarget
        val sanitizedTitle = RingtoneMetadataSanitizer.sanitizeDisplayTitle(
            rawTitle = state.clipName.ifBlank { RingtoneMetadataSanitizer.defaultClipName(track.title) },
            fallback = RingtoneMetadataSanitizer.defaultClipName(track.title)
        )
        val trimRequest = RingtoneTrimRequest(
            sourceUri = resolved.uriString,
            range = state.trimRange,
            clipName = sanitizedTitle,
            fadeInEnabled = state.fadeInEnabled,
            fadeOutEnabled = state.fadeOutEnabled,
            fadeDurationMs = state.fadeDurationMs
        )

        exportJob?.cancel()
        exportJob = scope.launch {
            var tempM4aFile: File? = null
            try {
                _uiState.update {
                    it.copy(
                        feedbackMessage = null,
                        isFeedbackError = false,
                        exportProgress = RingtoneExportProgress(
                            stage = RingtoneExportStage.TRIMMING,
                            fraction = 0.05f,
                            message = "Preparando recorte…"
                        )
                    )
                }

                val trimResult = trimGateway.trimToM4a(trimRequest) { progress ->
                    _uiState.update { it.copy(exportProgress = progress) }
                }
                tempM4aFile = trimResult.getOrThrow()

                _uiState.update {
                    it.copy(
                        exportProgress = RingtoneExportProgress(
                            stage = RingtoneExportStage.SAVING,
                            fraction = 0.92f,
                            message = "Guardando en ${target.relativeDirectory}…"
                        )
                    )
                }

                val savedClip = storageGateway.saveClipToMediaStore(
                    trimmedM4aFile = tempM4aFile,
                    clipTitle = sanitizedTitle,
                    artist = track.artist,
                    durationMs = state.trimRange.durationMs,
                    target = target
                ).getOrThrow()

                if (target != RingtoneTarget.SAVE_CLIP_ONLY) {
                    _uiState.update {
                        it.copy(
                            exportProgress = RingtoneExportProgress(
                                stage = RingtoneExportStage.APPLYING,
                                fraction = 0.97f,
                                message = "Estableciendo como ${target.displayLabel.lowercase()}…"
                            )
                        )
                    }
                    storageGateway.applyDefaultTone(savedClip.contentUri, target).getOrThrow()
                }

                val updatedClips = runCatching { storageGateway.listSavedClips() }
                    .getOrElse { listOf(savedClip) + _uiState.value.savedClips }

                val successMsg = when (target) {
                    RingtoneTarget.RINGTONE -> "\"$sanitizedTitle\" establecido como tono de llamada."
                    RingtoneTarget.NOTIFICATION -> "\"$sanitizedTitle\" establecido como sonido de notificación."
                    RingtoneTarget.ALARM -> "\"$sanitizedTitle\" establecido como tono de alarma."
                    RingtoneTarget.SAVE_CLIP_ONLY -> "Recorte \"$sanitizedTitle\" guardado en Mis recortes."
                }

                _uiState.update {
                    it.copy(
                        savedClips = updatedClips,
                        exportProgress = RingtoneExportProgress(
                            stage = RingtoneExportStage.COMPLETED,
                            fraction = 1f,
                            message = successMsg
                        ),
                        feedbackMessage = successMsg,
                        isFeedbackError = false
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (permEx: WriteSettingsPermissionRequiredException) {
                _uiState.update {
                    it.copy(
                        canWriteSettings = false,
                        showPermissionPrompt = true,
                        pendingApplyAfterPermission = true,
                        exportProgress = RingtoneExportProgress(stage = RingtoneExportStage.IDLE),
                        feedbackMessage = permEx.message,
                        isFeedbackError = true
                    )
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        exportProgress = RingtoneExportProgress(
                            stage = RingtoneExportStage.ERROR,
                            fraction = 0f,
                            message = t.message ?: "Error al crear el tono."
                        ),
                        feedbackMessage = t.message ?: "No se pudo crear el tono. Intenta nuevamente.",
                        isFeedbackError = true
                    )
                }
            } finally {
                tempM4aFile?.let { file -> runCatching { file.delete() } }
            }
        }
    }

    fun reapplySavedClip(clip: SavedRingtoneClip, target: RingtoneTarget) {
        if (target == RingtoneTarget.SAVE_CLIP_ONLY) return
        val hasPerm = storageGateway.canWriteSettings()
        _uiState.update { it.copy(canWriteSettings = hasPerm) }

        if (!hasPerm) {
            _uiState.update {
                it.copy(
                    showPermissionPrompt = true,
                    pendingClipReapplyAfterPermission = clip to target,
                    feedbackMessage = "Otorga el permiso de ajustes del sistema para aplicar \"${clip.title}\".",
                    isFeedbackError = false
                )
            }
            return
        }

        scope.launch {
            val result = storageGateway.applyDefaultTone(clip.contentUri, target)
            result.fold(
                onSuccess = {
                    val msg = "\"${clip.title}\" aplicado como ${target.displayLabel.lowercase()}."
                    _uiState.update { state ->
                        state.copy(
                            savedClips = state.savedClips.map {
                                if (it.id == clip.id) it.copy(target = target) else it
                            },
                            feedbackMessage = msg,
                            isFeedbackError = false
                        )
                    }
                },
                onFailure = { err ->
                    if (err is WriteSettingsPermissionRequiredException) {
                        _uiState.update {
                            it.copy(
                                canWriteSettings = false,
                                showPermissionPrompt = true,
                                pendingClipReapplyAfterPermission = clip to target,
                                feedbackMessage = err.message,
                                isFeedbackError = true
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                feedbackMessage = err.message ?: "No se pudo aplicar el tono.",
                                isFeedbackError = true
                            )
                        }
                    }
                }
            )
        }
    }

    fun deleteSavedClip(clip: SavedRingtoneClip) {
        if (_uiState.value.previewingClipId == clip.id) {
            previewGateway.stop()
        }
        scope.launch {
            val result = storageGateway.deleteClip(clip)
            result.fold(
                onSuccess = {
                    _uiState.update { state ->
                        state.copy(
                            savedClips = state.savedClips.filterNot { it.id == clip.id },
                            feedbackMessage = "Recorte \"${clip.title}\" eliminado.",
                            isFeedbackError = false
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            feedbackMessage = err.message ?: "No se pudo eliminar el recorte.",
                            isFeedbackError = true
                        )
                    }
                }
            )
        }
    }

    fun dismissFeedback() {
        _uiState.update { it.copy(feedbackMessage = null, isFeedbackError = false) }
    }

    fun release() {
        sourceLoadJob?.cancel()
        waveformJob?.cancel()
        exportJob?.cancel()
        savedClipsJob?.cancel()
        previewGateway.release()
        if (ownsScope) {
            scope.cancel()
        }
    }
}

/**
 * Android runtime handle pairing [RingtoneEditorController] with the system settings intent helper.
 */
internal class AndroidRingtoneEditorHandle(
    val controller: RingtoneEditorController,
    private val storageManager: RingtoneStorageManager
) {
    fun createWriteSettingsIntent(callerContext: Context): Intent {
        return storageManager.createWriteSettingsIntent(callerContext)
    }

    fun release() {
        controller.release()
    }
}

internal fun createAndroidRingtoneEditorHandle(
    context: Context,
    onPauseMainPlayback: () -> Unit,
    resolveAudioSource: (suspend (TrackItem) -> Uri?)? = null
): AndroidRingtoneEditorHandle {
    val appContext = context.applicationContext
    val trimmerEngine = AudioTrimmerEngine(appContext)
    val waveformExtractor = AudioWaveformExtractor(appContext)
    val storageManager = RingtoneStorageManager(appContext)
    val previewPlayer = RingtonePreviewPlayer(appContext, onPauseMainPlayback)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val streamResolver: RingtoneAudioStreamResolver? = resolveAudioSource?.let { callback ->
        RingtoneAudioStreamResolver { track ->
            val uri = callback(track) ?: return@RingtoneAudioStreamResolver null
            val raw = uri.toString().trim()
            when {
                raw.startsWith("http://", ignoreCase = true) ||
                    raw.startsWith("https://", ignoreCase = true) ->
                    RingtoneResolvedStream.RemoteHttpUrl(raw)
                raw.startsWith("file://", ignoreCase = true) -> {
                    val path = uri.path
                    if (!path.isNullOrBlank()) RingtoneResolvedStream.LocalFile(File(path))
                    else RingtoneResolvedStream.LocalUri(raw)
                }
                else -> RingtoneResolvedStream.LocalUri(raw)
            }
        }
    }

    val sourceGateway = object : RingtoneSourceGateway {
        override suspend fun resolveTrackAudio(
            track: TrackItem,
            onStatus: (String) -> Unit
        ): Result<ResolvedLocalAudio> {
            trimmerEngine.cleanupStaleTempFiles()
            return trimmerEngine.resolveTrackAudio(
                track = track,
                customResolver = streamResolver,
                onStatus = onStatus
            )
        }
    }

    val waveformGateway = object : RingtoneWaveformGateway {
        override suspend fun extractWaveform(
            uriString: String,
            barCount: Int,
            fallbackSeed: Int,
            knownDurationMs: Long
        ): WaveformData = waveformExtractor.extractWaveform(
            uriString = uriString,
            barCount = barCount,
            fallbackSeed = fallbackSeed,
            knownDurationMs = knownDurationMs
        )
    }

    val trimGateway = object : RingtoneTrimGateway {
        override suspend fun trimToM4a(
            request: RingtoneTrimRequest,
            onProgress: (RingtoneExportProgress) -> Unit
        ): Result<File> = trimmerEngine.trimToM4a(request, onProgress)
    }

    val storageGateway = object : RingtoneStorageGateway {
        override fun canWriteSettings(): Boolean = storageManager.canWriteSettings()

        override suspend fun saveClipToMediaStore(
            trimmedM4aFile: File,
            clipTitle: String,
            artist: String,
            durationMs: Long,
            target: RingtoneTarget
        ): Result<SavedRingtoneClip> = storageManager.saveClipToMediaStore(
            trimmedM4aFile = trimmedM4aFile,
            clipTitle = clipTitle,
            artist = artist,
            durationMs = durationMs,
            target = target
        )

        override suspend fun applyDefaultTone(
            contentUriString: String,
            target: RingtoneTarget
        ): Result<Unit> = storageManager.applyDefaultTone(contentUriString, target)

        override suspend fun listSavedClips(): List<SavedRingtoneClip> = storageManager.listSavedClips()

        override suspend fun deleteClip(clip: SavedRingtoneClip): Result<Unit> = storageManager.deleteClip(clip)
    }

    val previewGateway = object : RingtonePreviewGateway {
        override val isPlaying: StateFlow<Boolean> = previewPlayer.isPlaying
        override val positionMs: StateFlow<Long> = previewPlayer.positionMs
        override val previewingClipId: StateFlow<Long?> = previewPlayer.previewingClipId

        override fun toggleSegmentPreview(
            uriString: String,
            range: TrimRange,
            fadeInEnabled: Boolean,
            fadeOutEnabled: Boolean,
            fadeDurationMs: Long
        ) = previewPlayer.toggleSegmentPreview(uriString, range, fadeInEnabled, fadeOutEnabled, fadeDurationMs)

        override fun startSegmentPreview(
            uriString: String,
            range: TrimRange,
            fadeInEnabled: Boolean,
            fadeOutEnabled: Boolean,
            fadeDurationMs: Long,
            seekPosMs: Long
        ) = previewPlayer.startSegmentPreview(uriString, range, fadeInEnabled, fadeOutEnabled, fadeDurationMs, seekPosMs)

        override fun updateLiveSegmentParameters(
            range: TrimRange,
            fadeInEnabled: Boolean,
            fadeOutEnabled: Boolean,
            fadeDurationMs: Long
        ) = previewPlayer.updateLiveSegmentParameters(range, fadeInEnabled, fadeOutEnabled, fadeDurationMs)

        override fun seekSegmentTo(positionMs: Long) = previewPlayer.seekSegmentTo(positionMs)

        override fun toggleSavedClipPreview(clip: SavedRingtoneClip) = previewPlayer.toggleSavedClipPreview(clip)

        override fun stop() = previewPlayer.stop()

        override fun release() = previewPlayer.release()
    }

    val controller = RingtoneEditorController(
        sourceGateway = sourceGateway,
        waveformGateway = waveformGateway,
        trimGateway = trimGateway,
        storageGateway = storageGateway,
        previewGateway = previewGateway,
        scope = scope,
        ownsScope = true
    )

    return AndroidRingtoneEditorHandle(controller, storageManager)
}
