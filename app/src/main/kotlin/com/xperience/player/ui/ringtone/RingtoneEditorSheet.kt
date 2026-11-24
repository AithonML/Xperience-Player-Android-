package com.xperience.player.ui.ringtone

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.service.ringtone.ResolvedLocalAudio
import com.xperience.player.service.ringtone.ResolvedSourceKind
import com.xperience.player.service.ringtone.RingtoneEditorTab
import com.xperience.player.service.ringtone.RingtoneTarget
import com.xperience.player.service.ringtone.RingtoneUiState
import com.xperience.player.service.ringtone.SavedRingtoneClip
import com.xperience.player.service.ringtone.TrimRange
import com.xperience.player.service.ringtone.WaveformData
import com.xperience.player.service.ringtone.WaveformNormalizer
import com.xperience.player.ui.adaptive.AdaptiveModalContainer
import com.xperience.player.ui.theme.DarkThemeColors
import com.xperience.player.ui.theme.LightThemeColors
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.XperiencePlayerTheme

/**
 * Adaptive modal sheet / tablet dialog wrapper around [RingtoneEditorScreen].
 *
 * Mirrors Xperience Music Player's [AdaptiveModalContainer] presentation used by
 * TrackOptionsMenuSheet and DownloadConfirmationModal so the Ringtone Editor can be
 * launched directly from TrackOptionsMenuSheet, NowPlayingScreen, or NowPlayingSidebar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RingtoneEditorSheet(
    track: TrackItem?,
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onPauseMainPlayback: () -> Unit,
    resolveAudioSource: (suspend (TrackItem) -> Uri?)? = null,
    initialTab: RingtoneEditorTab = RingtoneEditorTab.EDITOR
) {
    if (!isOpen || track == null) return

    val appColors = LocalAppColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    AdaptiveModalContainer(
        isOpen = true,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (appColors.isDark) Color(0xFF11131B) else Color(0xFFF6F8FB),
        dialogMaxWidth = 620.dp,
        dialogMaxHeight = 820.dp,
        showDragHandle = true
    ) {
        RingtoneEditorScreen(
            track = track,
            onDismiss = onDismiss,
            onPauseMainPlayback = onPauseMainPlayback,
            resolveAudioSource = resolveAudioSource,
            initialTab = initialTab,
            showFullScreenInsets = false,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 760.dp)
        )
    }
}

// ============================================================================
// Jetpack Compose Previews (Zero Android MediaStore / ExoPlayer runtime calls)
// ============================================================================

private fun samplePreviewTrack() = TrackItem(
    id = "preview_track_1",
    title = "Midnight City (Remastered)",
    artist = "M83",
    album = "Hurry Up, We're Dreaming",
    durationMs = 243_000L
)

private fun sampleEditorPreviewState(
    canWriteSettings: Boolean = true,
    activeTab: RingtoneEditorTab = RingtoneEditorTab.EDITOR,
    isPlaying: Boolean = true
): RingtoneUiState {
    val track = samplePreviewTrack()
    val peaks = WaveformNormalizer.generateFallbackWaveform(seed = 42, barCount = 96)
    return RingtoneUiState(
        track = track,
        resolvedAudio = ResolvedLocalAudio(
            uriString = "file:///storage/emulated/0/Music/midnight_city.m4a",
            durationMs = track.durationMs,
            sourceKind = ResolvedSourceKind.LOCAL_FILE
        ),
        effectiveDurationMs = track.durationMs,
        trimRange = TrimRange(startMs = 68_500L, endMs = 98_500L),
        waveform = WaveformData(peaks = peaks, durationMs = track.durationMs, isRealWaveform = true),
        isPreviewPlaying = isPlaying,
        previewPositionMs = 79_200L,
        fadeInEnabled = true,
        fadeOutEnabled = true,
        clipName = "Midnight City (Estribillo)",
        selectedTarget = RingtoneTarget.RINGTONE,
        canWriteSettings = canWriteSettings,
        showPermissionPrompt = !canWriteSettings,
        activeTab = activeTab,
        savedClips = listOf(
            SavedRingtoneClip(
                id = 101L,
                contentUri = "content://media/external/audio/media/101",
                title = "Midnight City (Estribillo)",
                artist = "M83",
                displayName = "Midnight_City_Estribillo.m4a",
                durationMs = 30_000L,
                dateAddedMs = 1_760_000_000_000L,
                target = RingtoneTarget.RINGTONE
            ),
            SavedRingtoneClip(
                id = 102L,
                contentUri = "content://media/external/audio/media/102",
                title = "Blinding Lights (Intro)",
                artist = "The Weeknd",
                displayName = "Blinding_Lights_Intro.m4a",
                durationMs = 15_000L,
                dateAddedMs = 1_759_500_000_000L,
                target = RingtoneTarget.NOTIFICATION
            ),
            SavedRingtoneClip(
                id = 103L,
                contentUri = "content://media/external/audio/media/103",
                title = "Starboy (Drop)",
                artist = "The Weeknd",
                displayName = "Starboy_Drop.m4a",
                durationMs = 25_000L,
                dateAddedMs = 1_759_000_000_000L,
                target = RingtoneTarget.ALARM
            )
        )
    )
}

@Preview(name = "Ringtone Editor - Dark", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun RingtoneEditorDarkPreview() {
    XperiencePlayerTheme(darkTheme = true) {
        CompositionLocalProvider(
            LocalAppColors provides DarkThemeColors,
            LocalAccentColor provides Color(0xFF00E5FF)
        ) {
            Box(modifier = Modifier.background(DarkThemeColors.background)) {
                PreviewRingtoneEditorBody(state = sampleEditorPreviewState(canWriteSettings = true))
            }
        }
    }
}

@Preview(name = "Ringtone Editor - Permission Needed", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun RingtoneEditorPermissionPreview() {
    XperiencePlayerTheme(darkTheme = true) {
        CompositionLocalProvider(
            LocalAppColors provides DarkThemeColors,
            LocalAccentColor provides Color(0xFFA855F7)
        ) {
            Box(modifier = Modifier.background(DarkThemeColors.background)) {
                PreviewRingtoneEditorBody(state = sampleEditorPreviewState(canWriteSettings = false, isPlaying = false))
            }
        }
    }
}

@Preview(name = "Ringtone Saved Clips - Dark", showBackground = true, widthDp = 412, heightDp = 820)
@Composable
private fun RingtoneSavedClipsDarkPreview() {
    XperiencePlayerTheme(darkTheme = true) {
        CompositionLocalProvider(
            LocalAppColors provides DarkThemeColors,
            LocalAccentColor provides Color(0xFF00E5FF)
        ) {
            Box(modifier = Modifier.background(DarkThemeColors.background)) {
                PreviewRingtoneEditorBody(
                    state = sampleEditorPreviewState(
                        canWriteSettings = true,
                        activeTab = RingtoneEditorTab.SAVED_CLIPS
                    ).copy(previewingClipId = 101L)
                )
            }
        }
    }
}

@Preview(name = "Ringtone Editor - Light", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun RingtoneEditorLightPreview() {
    XperiencePlayerTheme(darkTheme = false) {
        CompositionLocalProvider(
            LocalAppColors provides LightThemeColors,
            LocalAccentColor provides Color(0xFF3B82F6)
        ) {
            Box(modifier = Modifier.background(LightThemeColors.background)) {
                PreviewRingtoneEditorBody(state = sampleEditorPreviewState(canWriteSettings = true))
            }
        }
    }
}

@Composable
private fun PreviewRingtoneEditorBody(state: RingtoneUiState) {
    RingtoneEditorContent(
        state = state,
        onDismiss = {},
        onSelectTab = {},
        onRetryLoadSource = {},
        onStartChange = {},
        onEndChange = {},
        onShiftWindow = {},
        onNudgeStart = {},
        onNudgeEnd = {},
        onApplyPreset = {},
        onTogglePreview = {},
        onRestartPreview = {},
        onSeekPreview = {},
        onToggleFadeIn = {},
        onToggleFadeOut = {},
        onClipNameChange = {},
        onSelectTarget = {},
        onOpenWriteSettings = {},
        onDismissPermissionPrompt = {},
        onSaveAndApply = {},
        onDismissFeedback = {},
        onRefreshSavedClips = {},
        onToggleSavedClipPreview = {},
        onReapplySavedClip = { _, _ -> },
        onDeleteSavedClip = {}
    )
}
