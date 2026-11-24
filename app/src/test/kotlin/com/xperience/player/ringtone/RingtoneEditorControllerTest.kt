package com.xperience.player.ringtone

import com.xperience.player.domain.model.TrackItem
import com.xperience.player.service.ringtone.ResolvedLocalAudio
import com.xperience.player.service.ringtone.ResolvedSourceKind
import com.xperience.player.service.ringtone.RingtoneEditorTab
import com.xperience.player.service.ringtone.RingtoneExportProgress
import com.xperience.player.service.ringtone.RingtoneExportStage
import com.xperience.player.service.ringtone.RingtoneTarget
import com.xperience.player.service.ringtone.RingtoneTrimRequest
import com.xperience.player.service.ringtone.SavedRingtoneClip
import com.xperience.player.service.ringtone.TrimPreset
import com.xperience.player.service.ringtone.TrimRange
import com.xperience.player.service.ringtone.WaveformData
import com.xperience.player.ui.ringtone.RingtoneEditorController
import com.xperience.player.ui.ringtone.RingtonePreviewGateway
import com.xperience.player.ui.ringtone.RingtoneSourceGateway
import com.xperience.player.ui.ringtone.RingtoneStorageGateway
import com.xperience.player.ui.ringtone.RingtoneTrimGateway
import com.xperience.player.ui.ringtone.RingtoneWaveformGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class RingtoneEditorControllerTest {

    private val sampleTrack = TrackItem(
        id = "ytm_abc12345678",
        title = "Instant Crush",
        artist = "Daft Punk",
        album = "Random Access Memories",
        durationMs = 240_000L
    )

    private class FakeSourceGateway(
        var nextResult: Result<ResolvedLocalAudio> = Result.success(
            ResolvedLocalAudio(
                uriString = "file:///cache/instant_crush.m4a",
                durationMs = 240_000L,
                sourceKind = ResolvedSourceKind.CACHED_ONLINE
            )
        )
    ) : RingtoneSourceGateway {
        var calls = 0
        override suspend fun resolveTrackAudio(
            track: TrackItem,
            onStatus: (String) -> Unit
        ): Result<ResolvedLocalAudio> {
            calls++
            onStatus("Cargando pista…")
            return nextResult
        }
    }

    private class FakeWaveformGateway : RingtoneWaveformGateway {
        override suspend fun extractWaveform(
            uriString: String,
            barCount: Int,
            fallbackSeed: Int,
            knownDurationMs: Long
        ): WaveformData = WaveformData(
            peaks = List(barCount) { 0.5f },
            durationMs = knownDurationMs,
            isRealWaveform = true
        )
    }

    private class FakeTrimGateway : RingtoneTrimGateway {
        var lastRequest: RingtoneTrimRequest? = null
        var lastTempFile: File? = null
        var shouldFail = false

        override suspend fun trimToM4a(
            request: RingtoneTrimRequest,
            onProgress: (RingtoneExportProgress) -> Unit
        ): Result<File> {
            lastRequest = request
            if (shouldFail) {
                return Result.failure(IllegalStateException("Codec error"))
            }
            onProgress(RingtoneExportProgress(RingtoneExportStage.TRIMMING, 0.4f, "Recortando"))
            onProgress(RingtoneExportProgress(RingtoneExportStage.ENCODING, 0.85f, "Codificando"))
            val temp = File.createTempFile("ringtone_test_", ".m4a").apply {
                writeBytes(ByteArray(2048) { 1 })
            }
            lastTempFile = temp
            return Result.success(temp)
        }
    }

    private class FakeStorageGateway(
        var writeSettingsGranted: Boolean = false
    ) : RingtoneStorageGateway {
        val clips = mutableListOf<SavedRingtoneClip>()
        val appliedCalls = mutableListOf<Pair<String, RingtoneTarget>>()
        private var nextId = 100L

        override fun canWriteSettings(): Boolean = writeSettingsGranted

        override suspend fun saveClipToMediaStore(
            trimmedM4aFile: File,
            clipTitle: String,
            artist: String,
            durationMs: Long,
            target: RingtoneTarget
        ): Result<SavedRingtoneClip> {
            val id = nextId++
            val saved = SavedRingtoneClip(
                id = id,
                contentUri = "content://media/external/audio/media/$id",
                title = clipTitle,
                artist = artist,
                displayName = "$clipTitle.m4a",
                durationMs = durationMs,
                dateAddedMs = 1_760_000_000_000L,
                target = target,
                sizeBytes = trimmedM4aFile.length()
            )
            clips.add(0, saved)
            return Result.success(saved)
        }

        override suspend fun applyDefaultTone(
            contentUriString: String,
            target: RingtoneTarget
        ): Result<Unit> {
            appliedCalls.add(contentUriString to target)
            return Result.success(Unit)
        }

        override suspend fun listSavedClips(): List<SavedRingtoneClip> = clips.toList()

        override suspend fun deleteClip(clip: SavedRingtoneClip): Result<Unit> {
            clips.removeAll { it.id == clip.id }
            return Result.success(Unit)
        }
    }

    private class FakePreviewGateway : RingtonePreviewGateway {
        override val isPlaying = MutableStateFlow(false)
        override val positionMs = MutableStateFlow(0L)
        override val previewingClipId = MutableStateFlow<Long?>(null)

        var lastLiveRange: TrimRange? = null
        var lastFadeIn: Boolean = false
        var lastFadeOut: Boolean = false
        var stopCount = 0
        var released = false

        override fun toggleSegmentPreview(
            uriString: String,
            range: TrimRange,
            fadeInEnabled: Boolean,
            fadeOutEnabled: Boolean,
            fadeDurationMs: Long
        ) {
            if (isPlaying.value && previewingClipId.value == null) {
                stop()
            } else {
                isPlaying.value = true
                previewingClipId.value = null
                positionMs.value = range.startMs
            }
        }

        override fun startSegmentPreview(
            uriString: String,
            range: TrimRange,
            fadeInEnabled: Boolean,
            fadeOutEnabled: Boolean,
            fadeDurationMs: Long,
            seekPosMs: Long
        ) {
            isPlaying.value = true
            previewingClipId.value = null
            positionMs.value = seekPosMs
        }

        override fun updateLiveSegmentParameters(
            range: TrimRange,
            fadeInEnabled: Boolean,
            fadeOutEnabled: Boolean,
            fadeDurationMs: Long
        ) {
            lastLiveRange = range
            lastFadeIn = fadeInEnabled
            lastFadeOut = fadeOutEnabled
        }

        override fun seekSegmentTo(positionMs: Long) {
            this.positionMs.value = positionMs
        }

        override fun toggleSavedClipPreview(clip: SavedRingtoneClip) {
            if (isPlaying.value && previewingClipId.value == clip.id) {
                stop()
            } else {
                isPlaying.value = true
                previewingClipId.value = clip.id
            }
        }

        override fun stop() {
            stopCount++
            isPlaying.value = false
            previewingClipId.value = null
        }

        override fun release() {
            stop()
            released = true
        }
    }

    private fun TestScope.createController(
        source: RingtoneSourceGateway = FakeSourceGateway(),
        waveform: RingtoneWaveformGateway = FakeWaveformGateway(),
        trim: RingtoneTrimGateway = FakeTrimGateway(),
        storage: RingtoneStorageGateway = FakeStorageGateway(writeSettingsGranted = true),
        preview: RingtonePreviewGateway = FakePreviewGateway()
    ): RingtoneEditorController {
        val controllerScope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))
        return RingtoneEditorController(
            sourceGateway = source,
            waveformGateway = waveform,
            trimGateway = trim,
            storageGateway = storage,
            previewGateway = preview,
            scope = controllerScope,
            ownsScope = true
        )
    }

    @Test
    fun `loadTrack resolves audio extracts real waveform and initializes range and clip name`() = runTest {
        val source = FakeSourceGateway()
        val storage = FakeStorageGateway(writeSettingsGranted = true)
        val preview = FakePreviewGateway()
        val controller = createController(source = source, storage = storage, preview = preview)
        try {
            controller.loadTrack(sampleTrack)
            advanceUntilIdle()

            val state = controller.uiState.value
            assertEquals(sampleTrack, state.track)
            assertNotNull(state.resolvedAudio)
            assertEquals(240_000L, state.effectiveDurationMs)
            assertEquals(TrimRange(0L, 30_000L), state.trimRange)
            assertEquals("Instant Crush (Recorte)", state.clipName)
            assertTrue(state.waveform.isRealWaveform)
            assertFalse(state.isLoadingSource)
            assertFalse(state.isExtractingWaveform)
            assertTrue(state.canSaveOrApply)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `nudge presets and fade toggles update trim range and propagate live parameters to preview`() = runTest {
        val preview = FakePreviewGateway()
        val controller = createController(preview = preview)
        try {
            controller.loadTrack(sampleTrack)
            advanceUntilIdle()

            controller.applyPreset(TrimPreset.CHORUS)
            assertEquals(TrimRange(72_000L, 102_000L), controller.uiState.value.trimRange)
            assertEquals(TrimRange(72_000L, 102_000L), preview.lastLiveRange)

            controller.nudgeStart(500L)
            controller.nudgeEnd(-500L)
            assertEquals(TrimRange(72_500L, 101_500L), controller.uiState.value.trimRange)

            controller.toggleFadeIn()
            assertFalse(controller.uiState.value.fadeInEnabled)
            assertFalse(preview.lastFadeIn)
            assertTrue(preview.lastFadeOut)
        } finally {
            controller.release()
        }
    }

    @Test
    fun `applying ringtone without WRITE_SETTINGS prompts permission and auto-applies when granted`() = runTest {
        val trim = FakeTrimGateway()
        val storage = FakeStorageGateway(writeSettingsGranted = false)
        val preview = FakePreviewGateway()
        val controller = createController(trim = trim, storage = storage, preview = preview)
        try {
            controller.loadTrack(sampleTrack)
            advanceUntilIdle()

            // First attempt without permission -> prompts user, does not trim yet
            controller.selectTarget(RingtoneTarget.RINGTONE)
            controller.saveAndApplyCurrentClip()
            advanceUntilIdle()

            assertTrue(controller.uiState.value.showPermissionPrompt)
            assertTrue(controller.uiState.value.pendingApplyAfterPermission)
            assertNull(trim.lastRequest)
            assertTrue(storage.appliedCalls.isEmpty())

            // User grants permission in System Settings and returns -> auto-runs trim, save, apply
            storage.writeSettingsGranted = true
            controller.onWriteSettingsPermissionReturned()
            advanceUntilIdle()

            val state = controller.uiState.value
            assertTrue(state.canWriteSettings)
            assertFalse(state.showPermissionPrompt)
            assertEquals(RingtoneExportStage.COMPLETED, state.exportProgress.stage)
            assertEquals(1, storage.clips.size)
            assertEquals(1, storage.appliedCalls.size)
            assertEquals(RingtoneTarget.RINGTONE, storage.appliedCalls.first().second)
            // Verify temporary m4a file was cleaned up after MediaStore persistence
            assertNotNull(trim.lastTempFile)
            assertFalse("Expected temp M4A file to be deleted after save", trim.lastTempFile!!.exists())
        } finally {
            controller.release()
        }
    }

    @Test
    fun `SAVE_CLIP_ONLY works without WRITE_SETTINGS permission`() = runTest {
        val trim = FakeTrimGateway()
        val storage = FakeStorageGateway(writeSettingsGranted = false)
        val controller = createController(trim = trim, storage = storage)
        try {
            controller.loadTrack(sampleTrack)
            advanceUntilIdle()

            controller.selectTarget(RingtoneTarget.SAVE_CLIP_ONLY)
            controller.updateClipName("Solo Clip")
            controller.saveAndApplyCurrentClip()
            advanceUntilIdle()

            val state = controller.uiState.value
            assertEquals(RingtoneExportStage.COMPLETED, state.exportProgress.stage)
            assertEquals(1, state.savedClips.size)
            assertEquals("Solo Clip", state.savedClips.first().title)
            assertTrue(storage.appliedCalls.isEmpty())
        } finally {
            controller.release()
        }
    }

    @Test
    fun `saved clips tab supports previewing reapplying and deleting clips`() = runTest {
        val storage = FakeStorageGateway(writeSettingsGranted = true)
        val clip = SavedRingtoneClip(
            id = 55L,
            contentUri = "content://media/external/audio/media/55",
            title = "Chorus Clip",
            artist = "Daft Punk",
            displayName = "Chorus_Clip.m4a",
            durationMs = 20_000L,
            dateAddedMs = 1_760_000_000_000L,
            target = RingtoneTarget.RINGTONE
        )
        storage.clips.add(clip)

        val preview = FakePreviewGateway()
        val controller = createController(storage = storage, preview = preview)
        try {
            controller.loadTrack(sampleTrack)
            advanceUntilIdle()

            controller.selectTab(RingtoneEditorTab.SAVED_CLIPS)
            advanceUntilIdle()
            assertEquals(1, controller.uiState.value.savedClips.size)

            // Play saved clip preview
            controller.toggleSavedClipPreview(clip)
            advanceUntilIdle()
            assertTrue(controller.uiState.value.isPreviewPlaying)
            assertEquals(55L, controller.uiState.value.previewingClipId)

            // Reapply as alarm
            controller.reapplySavedClip(clip, RingtoneTarget.ALARM)
            advanceUntilIdle()
            assertEquals(RingtoneTarget.ALARM, controller.uiState.value.savedClips.first().target)
            assertEquals(RingtoneTarget.ALARM, storage.appliedCalls.last().second)

            // Deleting currently previewed clip stops preview and removes clip
            controller.deleteSavedClip(clip)
            advanceUntilIdle()
            assertFalse(controller.uiState.value.isPreviewPlaying)
            assertNull(controller.uiState.value.previewingClipId)
            assertTrue(controller.uiState.value.savedClips.isEmpty())
        } finally {
            controller.release()
        }
    }

    @Test
    fun `source resolution error can be recovered via retryLoadSource`() = runTest {
        val source = FakeSourceGateway(
            nextResult = Result.failure(IllegalStateException("Sin conexión"))
        )
        val controller = createController(source = source)
        try {
            controller.loadTrack(sampleTrack)
            advanceUntilIdle()
            assertEquals("Sin conexión", controller.uiState.value.sourceErrorMessage)
            assertFalse(controller.uiState.value.canSaveOrApply)

            // Recover on retry
            source.nextResult = Result.success(
                ResolvedLocalAudio(
                    uriString = "content://media/external/audio/media/9",
                    durationMs = 180_000L,
                    sourceKind = ResolvedSourceKind.LOCAL_FILE
                )
            )
            controller.retryLoadSource()
            advanceUntilIdle()
            assertNull(controller.uiState.value.sourceErrorMessage)
            assertTrue(controller.uiState.value.canSaveOrApply)
            assertEquals(2, source.calls)
        } finally {
            controller.release()
        }
    }
}
