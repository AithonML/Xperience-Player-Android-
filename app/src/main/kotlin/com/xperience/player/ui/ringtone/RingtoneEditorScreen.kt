package com.xperience.player.ui.ringtone

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.RingVolume
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.service.ringtone.RingtoneEditorTab
import com.xperience.player.service.ringtone.RingtoneMetadataSanitizer
import com.xperience.player.service.ringtone.RingtoneTarget
import com.xperience.player.service.ringtone.RingtoneUiState
import com.xperience.player.service.ringtone.SavedRingtoneClip
import com.xperience.player.service.ringtone.TrimPreset
import com.xperience.player.service.ringtone.TrimRange
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.theme.AppThemeColors
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.PlusJakartaSans
import java.text.DateFormat
import java.util.Date

private val CardShape = ContinuousRoundedRectangle(size = 18.dp)
private val PillShape = ContinuousRoundedRectangle(percent = 50)
private val SmallButtonShape = ContinuousRoundedRectangle(size = 12.dp)

/**
 * Full-screen Set as Ringtone Editor & Saved Clips Manager screen.
 *
 * Public integration contract:
 * - [track]: Song selected for trimming.
 * - [onDismiss]: Closes the editor and returns to the host screen.
 * - [onPauseMainPlayback]: Invoked right before live preview playback starts so main playback pauses cleanly.
 * - [resolveAudioSource]: Optional host callback resolving a [TrackItem] into a local/stream [Uri].
 *   When null or returning null, [com.xperience.player.service.ringtone.AudioTrimmerEngine] automatically
 *   falls back to local URI / YouTube Music cache / OkHttp stream resolution.
 */
@Composable
fun RingtoneEditorScreen(
    track: TrackItem,
    onDismiss: () -> Unit,
    onPauseMainPlayback: () -> Unit,
    resolveAudioSource: (suspend (TrackItem) -> Uri?)? = null,
    modifier: Modifier = Modifier,
    initialTab: RingtoneEditorTab = RingtoneEditorTab.EDITOR,
    showFullScreenInsets: Boolean = true,
    accentColor: Color = LocalAccentColor.current,
    appColors: AppThemeColors = LocalAppColors.current
) {
    val context = LocalContext.current
    val latestOnPauseMain by rememberUpdatedState(onPauseMainPlayback)
    val latestResolveAudio by rememberUpdatedState(resolveAudioSource)

    val handle = remember(context) {
        createAndroidRingtoneEditorHandle(
            context = context,
            onPauseMainPlayback = { latestOnPauseMain() },
            resolveAudioSource = { t -> latestResolveAudio?.invoke(t) }
        )
    }

    val controller = handle.controller
    val uiState by controller.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(track.id, track.audioUrl) {
        controller.loadTrack(track)
        if (initialTab != RingtoneEditorTab.EDITOR) {
            controller.selectTab(initialTab)
        }
    }

    val writeSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        controller.onWriteSettingsPermissionReturned()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        controller.onWriteSettingsPermissionReturned()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        controller.stopPreview()
    }

    DisposableEffect(handle) {
        onDispose {
            handle.release()
        }
    }

    BackHandler(enabled = showFullScreenInsets) {
        controller.stopPreview()
        onDismiss()
    }

    val openWriteSettings: () -> Unit = {
        runCatching {
            val intent = handle.createWriteSettingsIntent(context)
            writeSettingsLauncher.launch(intent)
        }
    }

    val rootModifier = if (showFullScreenInsets) {
        modifier
            .fillMaxSize()
            .background(appColors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    } else {
        modifier.fillMaxWidth()
    }

    RingtoneEditorContent(
        state = uiState,
        onDismiss = {
            controller.stopPreview()
            onDismiss()
        },
        onSelectTab = controller::selectTab,
        onRetryLoadSource = controller::retryLoadSource,
        onStartChange = controller::updateStartMs,
        onEndChange = controller::updateEndMs,
        onShiftWindow = controller::shiftRangeBy,
        onNudgeStart = controller::nudgeStart,
        onNudgeEnd = controller::nudgeEnd,
        onApplyPreset = controller::applyPreset,
        onTogglePreview = controller::toggleSegmentPreview,
        onRestartPreview = controller::restartSegmentPreview,
        onSeekPreview = controller::seekPreviewTo,
        onToggleFadeIn = controller::toggleFadeIn,
        onToggleFadeOut = controller::toggleFadeOut,
        onClipNameChange = controller::updateClipName,
        onSelectTarget = controller::selectTarget,
        onOpenWriteSettings = openWriteSettings,
        onDismissPermissionPrompt = controller::dismissPermissionPrompt,
        onSaveAndApply = controller::saveAndApplyCurrentClip,
        onDismissFeedback = controller::dismissFeedback,
        onRefreshSavedClips = controller::refreshSavedClips,
        onToggleSavedClipPreview = controller::toggleSavedClipPreview,
        onReapplySavedClip = controller::reapplySavedClip,
        onDeleteSavedClip = controller::deleteSavedClip,
        modifier = rootModifier,
        accentColor = accentColor,
        appColors = appColors
    )
}

/**
 * Stateless presentation body of the Ringtone Editor & Saved Clips Manager.
 * Cleanly previewable without Android audio/MediaStore dependencies.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RingtoneEditorContent(
    state: RingtoneUiState,
    onDismiss: () -> Unit,
    onSelectTab: (RingtoneEditorTab) -> Unit,
    onRetryLoadSource: () -> Unit,
    onStartChange: (Long) -> Unit,
    onEndChange: (Long) -> Unit,
    onShiftWindow: (Long) -> Unit,
    onNudgeStart: (Long) -> Unit,
    onNudgeEnd: (Long) -> Unit,
    onApplyPreset: (TrimPreset) -> Unit,
    onTogglePreview: () -> Unit,
    onRestartPreview: () -> Unit,
    onSeekPreview: (Long) -> Unit,
    onToggleFadeIn: () -> Unit,
    onToggleFadeOut: () -> Unit,
    onClipNameChange: (String) -> Unit,
    onSelectTarget: (RingtoneTarget) -> Unit,
    onOpenWriteSettings: () -> Unit,
    onDismissPermissionPrompt: () -> Unit,
    onSaveAndApply: () -> Unit,
    onDismissFeedback: () -> Unit,
    onRefreshSavedClips: () -> Unit,
    onToggleSavedClipPreview: (SavedRingtoneClip) -> Unit,
    onReapplySavedClip: (SavedRingtoneClip, RingtoneTarget) -> Unit,
    onDeleteSavedClip: (SavedRingtoneClip) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = LocalAccentColor.current,
    appColors: AppThemeColors = LocalAppColors.current
) {
    var clipPendingDelete by remember { mutableStateOf<SavedRingtoneClip?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        // 1. Header bar
        RingtoneHeaderBar(
            onDismiss = onDismiss,
            accentColor = accentColor,
            appColors = appColors
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Segmented Tab Bar (Recortar / Mis recortes)
        RingtoneSegmentedTabs(
            activeTab = state.activeTab,
            savedClipsCount = state.savedClips.size,
            onSelectTab = onSelectTab,
            accentColor = accentColor,
            appColors = appColors
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Feedback banner (success / error)
        AnimatedVisibility(
            visible = !state.feedbackMessage.isNullOrBlank(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                FeedbackBanner(
                    message = state.feedbackMessage.orEmpty(),
                    isError = state.isFeedbackError,
                    showOpenClipsAction = !state.isFeedbackError && state.activeTab == RingtoneEditorTab.EDITOR,
                    onOpenClips = {
                        onDismissFeedback()
                        onSelectTab(RingtoneEditorTab.SAVED_CLIPS)
                    },
                    onDismiss = onDismissFeedback,
                    accentColor = accentColor,
                    appColors = appColors
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // 4. WRITE_SETTINGS Permission Prompt Card (visible whenever needed)
        val shouldShowPermissionCard = state.showPermissionPrompt ||
            (state.activeTab == RingtoneEditorTab.EDITOR && state.needsWritePermissionForSelectedTarget)
        AnimatedVisibility(
            visible = shouldShowPermissionCard,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                WriteSettingsPermissionCard(
                    onOpenSettings = onOpenWriteSettings,
                    onDismiss = if (state.showPermissionPrompt) onDismissPermissionPrompt else null,
                    appColors = appColors
                )
                Spacer(modifier = Modifier.height(14.dp))
            }
        }

        // 5. Tab Content
        when (state.activeTab) {
            RingtoneEditorTab.EDITOR -> {
                EditorTabBody(
                    state = state,
                    onRetryLoadSource = onRetryLoadSource,
                    onStartChange = onStartChange,
                    onEndChange = onEndChange,
                    onShiftWindow = onShiftWindow,
                    onNudgeStart = onNudgeStart,
                    onNudgeEnd = onNudgeEnd,
                    onApplyPreset = onApplyPreset,
                    onTogglePreview = onTogglePreview,
                    onRestartPreview = onRestartPreview,
                    onSeekPreview = onSeekPreview,
                    onToggleFadeIn = onToggleFadeIn,
                    onToggleFadeOut = onToggleFadeOut,
                    onClipNameChange = onClipNameChange,
                    onSelectTarget = onSelectTarget,
                    onSaveAndApply = onSaveAndApply,
                    accentColor = accentColor,
                    appColors = appColors
                )
            }
            RingtoneEditorTab.SAVED_CLIPS -> {
                SavedClipsTabBody(
                    clips = state.savedClips,
                    isLoading = state.isLoadingSavedClips,
                    previewingClipId = if (state.isPreviewPlaying) state.previewingClipId else null,
                    onRefresh = onRefreshSavedClips,
                    onSwitchToEditor = { onSelectTab(RingtoneEditorTab.EDITOR) },
                    onToggleClipPreview = onToggleSavedClipPreview,
                    onReapplyClip = onReapplySavedClip,
                    onRequestDelete = { clipPendingDelete = it },
                    accentColor = accentColor,
                    appColors = appColors
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }

    // Delete Confirmation Dialog
    clipPendingDelete?.let { clip ->
        AlertDialog(
            onDismissRequest = { clipPendingDelete = null },
            containerColor = if (appColors.isDark) Color(0xFF1A1D26) else Color.White,
            titleContentColor = appColors.textPrimary,
            textContentColor = appColors.textSecondary,
            title = {
                Text(
                    text = "Eliminar recorte",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "¿Quieres eliminar \"${clip.title}\" de tus tonos guardados?",
                    fontFamily = PlusJakartaSans
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteSavedClip(clip)
                        clipPendingDelete = null
                    }
                ) {
                    Text(
                        text = "Eliminar",
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEF4444)
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { clipPendingDelete = null }) {
                    Text(
                        text = "Cancelar",
                        fontFamily = GoogleSans,
                        color = appColors.textSecondary
                    )
                }
            }
        )
    }
}

@Composable
private fun RingtoneHeaderBar(
    onDismiss: () -> Unit,
    accentColor: Color,
    appColors: AppThemeColors
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ContentCut,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Establecer como tono",
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = 18.5.sp,
                color = appColors.textPrimary
            )
            Text(
                text = "Recorta el mejor fragmento para llamada, notificación o alarma",
                fontFamily = PlusJakartaSans,
                fontSize = 11.5.sp,
                color = appColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (appColors.isDark) Color(0x20FFFFFF) else Color(0x10000000))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Cerrar",
                tint = appColors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun RingtoneSegmentedTabs(
    activeTab: RingtoneEditorTab,
    savedClipsCount: Int,
    onSelectTab: (RingtoneEditorTab) -> Unit,
    accentColor: Color,
    appColors: AppThemeColors
) {
    val containerBg = if (appColors.isDark) Color(0xFF1A1D26) else Color(0xFFEAF0F6)
    val onAccentColor = if (accentColor.luminance() > 0.55f) Color(0xFF0A0C12) else Color.White

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PillShape)
            .background(containerBg)
            .border(0.8.dp, appColors.cardBorder, PillShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        RingtoneEditorTab.entries.forEach { tab ->
            val selected = tab == activeTab
            val label = if (tab == RingtoneEditorTab.SAVED_CLIPS && savedClipsCount > 0) {
                "${tab.label} ($savedClipsCount)"
            } else {
                tab.label
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(PillShape)
                    .background(if (selected) accentColor else Color.Transparent)
                    .clickable { onSelectTab(tab) }
                    .padding(vertical = 9.dp, horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontFamily = GoogleSans,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp,
                    color = if (selected) onAccentColor else appColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorTabBody(
    state: RingtoneUiState,
    onRetryLoadSource: () -> Unit,
    onStartChange: (Long) -> Unit,
    onEndChange: (Long) -> Unit,
    onShiftWindow: (Long) -> Unit,
    onNudgeStart: (Long) -> Unit,
    onNudgeEnd: (Long) -> Unit,
    onApplyPreset: (TrimPreset) -> Unit,
    onTogglePreview: () -> Unit,
    onRestartPreview: () -> Unit,
    onSeekPreview: (Long) -> Unit,
    onToggleFadeIn: () -> Unit,
    onToggleFadeOut: () -> Unit,
    onClipNameChange: (String) -> Unit,
    onSelectTarget: (RingtoneTarget) -> Unit,
    onSaveAndApply: () -> Unit,
    accentColor: Color,
    appColors: AppThemeColors
) {
    // Track header card
    state.track?.let { track ->
        TrackInfoCard(
            track = track,
            effectiveDurationMs = state.effectiveDurationMs,
            sourceBadge = state.resolvedAudio?.sourceKind?.label,
            accentColor = accentColor,
            appColors = appColors
        )
        Spacer(modifier = Modifier.height(14.dp))
    }

    // Loading or Error banner while resolving local/remote audio
    if (state.isLoadingSource) {
        SurfaceCard(appColors = appColors) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = accentColor,
                    strokeWidth = 2.4.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.sourceStatusMessage.ifBlank { "Preparando pista para recorte…" },
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                        color = appColors.textPrimary
                    )
                    Text(
                        text = "Descargando o leyendo el audio en caché local",
                        fontFamily = PlusJakartaSans,
                        fontSize = 11.5.sp,
                        color = appColors.textSecondary
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        return
    }

    if (!state.sourceErrorMessage.isNullOrBlank()) {
        SurfaceCard(appColors = appColors) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = state.sourceErrorMessage,
                    fontFamily = PlusJakartaSans,
                    fontSize = 13.sp,
                    color = appColors.textPrimary,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            val retryOnAccent = if (accentColor.luminance() > 0.55f) Color(0xFF0A0C12) else Color.White
            Button(
                onClick = onRetryLoadSource,
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = retryOnAccent
                ),
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Reintentar",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        return
    }

    // Interactive Waveform Trimmer
    RingtoneWaveformTrimmer(
        waveform = state.waveform,
        durationMs = state.effectiveDurationMs,
        trimRange = state.trimRange,
        previewPositionMs = state.previewPositionMs,
        isPreviewPlaying = state.isPreviewPlaying && state.previewingClipId == null,
        fadeInEnabled = state.fadeInEnabled,
        fadeOutEnabled = state.fadeOutEnabled,
        fadeDurationMs = state.fadeDurationMs,
        isExtractingWaveform = state.isExtractingWaveform,
        onStartChange = onStartChange,
        onEndChange = onEndChange,
        onShiftWindow = onShiftWindow,
        onSeekPreview = onSeekPreview,
        accentColor = accentColor,
        appColors = appColors
    )

    Spacer(modifier = Modifier.height(12.dp))

    // Precision Start / Duration / End Nudge Controls
    PrecisionTrimControlsRow(
        trimRange = state.trimRange,
        onNudgeStart = onNudgeStart,
        onNudgeEnd = onNudgeEnd,
        accentColor = accentColor,
        appColors = appColors
    )

    Spacer(modifier = Modifier.height(12.dp))

    // Live Clip Preview Strip
    LivePreviewStrip(
        isPlaying = state.isPreviewPlaying && state.previewingClipId == null,
        previewPositionMs = state.previewPositionMs,
        trimRange = state.trimRange,
        enabled = state.resolvedAudio != null && state.trimRange.isValidFor(state.effectiveDurationMs),
        onTogglePreview = onTogglePreview,
        onRestartPreview = onRestartPreview,
        accentColor = accentColor,
        appColors = appColors
    )

    Spacer(modifier = Modifier.height(14.dp))

    // Quick Presets row
    Text(
        text = "Ajustes rápidos",
        fontFamily = GoogleSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        color = appColors.textSecondary
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TrimPreset.entries.forEach { preset ->
            PresetChip(
                label = preset.label,
                onClick = { onApplyPreset(preset) },
                accentColor = accentColor,
                appColors = appColors
            )
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Fade-in / Fade-out & Clip Name Card
    SurfaceCard(appColors = appColors) {
        Text(
            text = "Nombre del recorte",
            fontFamily = GoogleSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = appColors.textPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = state.clipName,
            onValueChange = onClipNameChange,
            singleLine = true,
            placeholder = {
                Text(
                    text = "Ej. Mi canción favorita (Recorte)",
                    fontFamily = PlusJakartaSans,
                    fontSize = 13.sp,
                    color = appColors.textTertiary
                )
            },
            trailingIcon = {
                if (state.clipName.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Limpiar nombre",
                        tint = appColors.textSecondary,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onClipNameChange("") }
                    )
                }
            },
            shape = SmallButtonShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accentColor,
                unfocusedBorderColor = appColors.cardBorder,
                focusedTextColor = appColors.textPrimary,
                unfocusedTextColor = appColors.textPrimary,
                cursorColor = accentColor
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FadeToggleTile(
                title = "Fade-in",
                subtitle = "Entrada suave 1.5 s",
                checked = state.fadeInEnabled,
                onToggle = onToggleFadeIn,
                accentColor = accentColor,
                appColors = appColors,
                modifier = Modifier.weight(1f)
            )
            FadeToggleTile(
                title = "Fade-out",
                subtitle = "Salida suave 1.5 s",
                checked = state.fadeOutEnabled,
                onToggle = onToggleFadeOut,
                accentColor = accentColor,
                appColors = appColors,
                modifier = Modifier.weight(1f)
            )
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Destination Target Selector Chips
    Text(
        text = "Destino del tono",
        fontFamily = GoogleSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        color = appColors.textSecondary
    )
    Spacer(modifier = Modifier.height(8.dp))
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RingtoneTarget.entries.forEach { target ->
            TargetChip(
                target = target,
                selected = state.selectedTarget == target,
                onClick = { onSelectTarget(target) },
                accentColor = accentColor,
                appColors = appColors
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Export Progress Bar (when active)
    AnimatedVisibility(visible = state.isExporting) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = state.exportProgress.message.ifBlank { "Procesando audio…" },
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = appColors.textPrimary
                )
                Text(
                    text = "${(state.exportProgress.fraction * 100f).toInt()}%",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = accentColor
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { state.exportProgress.fraction.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(PillShape),
                color = accentColor,
                trackColor = if (appColors.isDark) Color(0x24FFFFFF) else Color(0x18000000)
            )
        }
    }

    // Primary Action Button
    val onAccentColor = if (accentColor.luminance() > 0.55f) Color(0xFF0A0C12) else Color.White
    val primaryCtaLabel = when (state.selectedTarget) {
        RingtoneTarget.RINGTONE -> "Guardar y usar como tono de llamada"
        RingtoneTarget.NOTIFICATION -> "Guardar y usar como notificación"
        RingtoneTarget.ALARM -> "Guardar y usar como alarma"
        RingtoneTarget.SAVE_CLIP_ONLY -> "Guardar recorte en Mis recortes"
    }

    Button(
        onClick = onSaveAndApply,
        enabled = state.canSaveOrApply,
        shape = PillShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = accentColor,
            contentColor = onAccentColor,
            disabledContainerColor = accentColor.copy(alpha = 0.30f),
            disabledContentColor = onAccentColor.copy(alpha = 0.55f)
        ),
        contentPadding = PaddingValues(vertical = 15.dp, horizontal = 20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (state.isExporting) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = onAccentColor,
                strokeWidth = 2.2.dp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Procesando…",
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = 14.5.sp
            )
        } else {
            Icon(
                imageVector = targetIcon(state.selectedTarget),
                contentDescription = null,
                modifier = Modifier.size(19.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = primaryCtaLabel,
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TrackInfoCard(
    track: TrackItem,
    effectiveDurationMs: Long,
    sourceBadge: String?,
    accentColor: Color,
    appColors: AppThemeColors
) {
    SurfaceCard(appColors = appColors) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(ContinuousRoundedRectangle(percent = 24))
                    .background(if (appColors.isDark) Color(0xFF222634) else Color(0xFFE2E8F0)),
                contentAlignment = Alignment.Center
            ) {
                if (!track.artworkUri.isNullOrBlank()) {
                    AsyncImage(
                        model = track.artworkUri,
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = appColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = track.artist,
                    fontFamily = PlusJakartaSans,
                    fontSize = 12.5.sp,
                    color = appColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!sourceBadge.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(PillShape)
                                .background(accentColor.copy(alpha = 0.14f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = sourceBadge,
                                fontFamily = GoogleSans,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.5.sp,
                                color = accentColor
                            )
                        }
                    }
                    if (effectiveDurationMs > 0L) {
                        Text(
                            text = "Duración total ${RingtoneMetadataSanitizer.formatTrimTimeMs(effectiveDurationMs)}",
                            fontFamily = PlusJakartaSans,
                            fontSize = 11.sp,
                            color = appColors.textTertiary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrecisionTrimControlsRow(
    trimRange: TrimRange,
    onNudgeStart: (Long) -> Unit,
    onNudgeEnd: (Long) -> Unit,
    accentColor: Color,
    appColors: AppThemeColors
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Start nudge box
        NudgeEndpointCard(
            label = "Inicio",
            timeText = RingtoneMetadataSanitizer.formatTrimTimeMs(trimRange.startMs),
            onMinus = { onNudgeStart(-TrimRange.NUDGE_STEP_MS) },
            onPlus = { onNudgeStart(TrimRange.NUDGE_STEP_MS) },
            accentColor = accentColor,
            appColors = appColors,
            modifier = Modifier.weight(1f)
        )

        // Duration badge in center
        Column(
            modifier = Modifier
                .clip(SmallButtonShape)
                .background(accentColor.copy(alpha = 0.15f))
                .border(1.dp, accentColor.copy(alpha = 0.45f), SmallButtonShape)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Duración",
                fontFamily = PlusJakartaSans,
                fontSize = 10.sp,
                color = appColors.textSecondary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = RingtoneMetadataSanitizer.formatDurationLabel(trimRange.durationMs),
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
                color = accentColor
            )
        }

        // End nudge box
        NudgeEndpointCard(
            label = "Fin",
            timeText = RingtoneMetadataSanitizer.formatTrimTimeMs(trimRange.endMs),
            onMinus = { onNudgeEnd(-TrimRange.NUDGE_STEP_MS) },
            onPlus = { onNudgeEnd(TrimRange.NUDGE_STEP_MS) },
            accentColor = accentColor,
            appColors = appColors,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun NudgeEndpointCard(
    label: String,
    timeText: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    accentColor: Color,
    appColors: AppThemeColors,
    modifier: Modifier = Modifier
) {
    val bg = if (appColors.isDark) Color(0xFF171A23) else Color(0xFFF3F6FA)
    val btnBg = if (appColors.isDark) Color(0x22FFFFFF) else Color(0x12000000)

    Column(
        modifier = modifier
            .clip(SmallButtonShape)
            .background(bg)
            .border(0.8.dp, appColors.cardBorder, SmallButtonShape)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontFamily = PlusJakartaSans,
            fontSize = 10.5.sp,
            color = appColors.textSecondary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = timeText,
            fontFamily = GoogleSans,
            fontWeight = FontWeight.Bold,
            fontSize = 13.5.sp,
            color = appColors.textPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(btnBg)
                    .clickable(onClick = onMinus)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "-0.5s",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.5.sp,
                    color = appColors.textPrimary
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.16f))
                    .clickable(onClick = onPlus)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+0.5s",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.5.sp,
                    color = accentColor
                )
            }
        }
    }
}

@Composable
private fun LivePreviewStrip(
    isPlaying: Boolean,
    previewPositionMs: Long,
    trimRange: TrimRange,
    enabled: Boolean,
    onTogglePreview: () -> Unit,
    onRestartPreview: () -> Unit,
    accentColor: Color,
    appColors: AppThemeColors
) {
    val onAccentColor = if (accentColor.luminance() > 0.55f) Color(0xFF0A0C12) else Color.White
    val relativePos = (previewPositionMs - trimRange.startMs).coerceIn(0L, trimRange.durationMs)

    SurfaceCard(appColors = appColors) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (enabled) accentColor else accentColor.copy(alpha = 0.3f))
                    .clickable(enabled = enabled, onClick = onTogglePreview),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar vista previa" else "Escuchar recorte",
                    tint = onAccentColor,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isPlaying) "Reproduciendo fragmento en bucle" else "Escuchar fragmento recortado",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    color = appColors.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${RingtoneMetadataSanitizer.formatTrimTimeMs(relativePos)} / ${RingtoneMetadataSanitizer.formatTrimTimeMs(trimRange.durationMs)}",
                    fontFamily = PlusJakartaSans,
                    fontSize = 12.sp,
                    color = if (isPlaying) accentColor else appColors.textSecondary
                )
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (appColors.isDark) Color(0x1EFFFFFF) else Color(0x0E000000))
                    .clickable(enabled = enabled, onClick = onRestartPreview),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Replay,
                    contentDescription = "Reiniciar vista previa",
                    tint = appColors.textPrimary,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

@Composable
private fun PresetChip(
    label: String,
    onClick: () -> Unit,
    accentColor: Color,
    appColors: AppThemeColors
) {
    val bg = if (appColors.isDark) Color(0xFF1A1E29) else Color(0xFFEEF2F7)
    Box(
        modifier = Modifier
            .clip(PillShape)
            .background(bg)
            .border(0.8.dp, accentColor.copy(alpha = 0.35f), PillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            fontFamily = GoogleSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = appColors.textPrimary
        )
    }
}

@Composable
private fun FadeToggleTile(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit,
    accentColor: Color,
    appColors: AppThemeColors,
    modifier: Modifier = Modifier
) {
    val bg = if (checked) {
        accentColor.copy(alpha = if (appColors.isDark) 0.14f else 0.10f)
    } else {
        if (appColors.isDark) Color(0x12FFFFFF) else Color(0x08000000)
    }
    val border = if (checked) accentColor.copy(alpha = 0.55f) else appColors.cardBorder

    Row(
        modifier = modifier
            .clip(SmallButtonShape)
            .background(bg)
            .border(0.9.dp, border, SmallButtonShape)
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                color = appColors.textPrimary
            )
            Text(
                text = subtitle,
                fontFamily = PlusJakartaSans,
                fontSize = 10.5.sp,
                color = appColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accentColor
            )
        )
    }
}

@Composable
private fun TargetChip(
    target: RingtoneTarget,
    selected: Boolean,
    onClick: () -> Unit,
    accentColor: Color,
    appColors: AppThemeColors
) {
    val bg = if (selected) {
        accentColor.copy(alpha = if (appColors.isDark) 0.22f else 0.16f)
    } else {
        if (appColors.isDark) Color(0xFF181B24) else Color(0xFFF1F5F9)
    }
    val border = if (selected) accentColor else appColors.cardBorder
    val contentTint = if (selected) accentColor else appColors.textSecondary

    Row(
        modifier = Modifier
            .clip(PillShape)
            .background(bg)
            .border(if (selected) 1.3.dp else 0.8.dp, border, PillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = targetIcon(target),
            contentDescription = null,
            tint = contentTint,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = target.displayLabel,
            fontFamily = GoogleSans,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 12.5.sp,
            color = if (selected) appColors.textPrimary else appColors.textSecondary
        )
    }
}

@Composable
private fun WriteSettingsPermissionCard(
    onOpenSettings: () -> Unit,
    onDismiss: (() -> Unit)?,
    appColors: AppThemeColors
) {
    val amber = Color(0xFFF59E0B)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(amber.copy(alpha = if (appColors.isDark) 0.14f else 0.10f))
            .border(1.dp, amber.copy(alpha = 0.45f), CardShape)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = amber,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Permiso requerido de Android",
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
                color = appColors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            if (onDismiss != null) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Ocultar",
                    tint = appColors.textSecondary,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable(onClick = onDismiss)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Para establecer un tono de llamada, notificación o alarma predeterminado, activa \"Modificar ajustes del sistema\" para Xperience Player. Si eliges \"Solo guardar\", no es necesario.",
            fontFamily = PlusJakartaSans,
            fontSize = 12.sp,
            color = appColors.textSecondary,
            lineHeight = 17.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = onOpenSettings,
            shape = PillShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = amber,
                contentColor = Color(0xFF14110A)
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Abrir ajustes de permiso",
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp
            )
        }
    }
}

@Composable
private fun FeedbackBanner(
    message: String,
    isError: Boolean,
    showOpenClipsAction: Boolean,
    onOpenClips: () -> Unit,
    onDismiss: () -> Unit,
    accentColor: Color,
    appColors: AppThemeColors
) {
    val tint = if (isError) Color(0xFFEF4444) else Color(0xFF10B981)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(tint.copy(alpha = if (appColors.isDark) 0.16f else 0.11f))
            .border(1.dp, tint.copy(alpha = 0.45f), CardShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = message,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.Medium,
            fontSize = 12.5.sp,
            color = appColors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        if (showOpenClipsAction) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Ver",
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                color = accentColor,
                modifier = Modifier
                    .clip(PillShape)
                    .clickable(onClick = onOpenClips)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Descartar",
            tint = appColors.textSecondary,
            modifier = Modifier
                .size(18.dp)
                .clickable(onClick = onDismiss)
        )
    }
}

@Composable
private fun SavedClipsTabBody(
    clips: List<SavedRingtoneClip>,
    isLoading: Boolean,
    previewingClipId: Long?,
    onRefresh: () -> Unit,
    onSwitchToEditor: () -> Unit,
    onToggleClipPreview: (SavedRingtoneClip) -> Unit,
    onReapplyClip: (SavedRingtoneClip, RingtoneTarget) -> Unit,
    onRequestDelete: (SavedRingtoneClip) -> Unit,
    accentColor: Color,
    appColors: AppThemeColors
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Mis recortes y tonos guardados",
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = appColors.textPrimary
            )
            Text(
                text = "Reproduce, vuelve a aplicar o elimina tus fragmentos",
                fontFamily = PlusJakartaSans,
                fontSize = 11.5.sp,
                color = appColors.textSecondary
            )
        }
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (appColors.isDark) Color(0x1CFFFFFF) else Color(0x0E000000))
                .clickable(onClick = onRefresh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Actualizar recortes",
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    if (isLoading && clips.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 36.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = accentColor, strokeWidth = 2.5.dp)
        }
        return
    }

    if (clips.isEmpty()) {
        SurfaceCard(appColors = appColors) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Aún no tienes recortes guardados",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = appColors.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Recorta un fragmento de cualquier canción para guardarlo aquí y reutilizarlo cuando quieras.",
                    fontFamily = PlusJakartaSans,
                    fontSize = 12.sp,
                    color = appColors.textSecondary
                )
                Spacer(modifier = Modifier.height(14.dp))
                val createOnAccent = if (accentColor.luminance() > 0.55f) Color(0xFF0A0C12) else Color.White
                Button(
                    onClick = onSwitchToEditor,
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = createOnAccent
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCut,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Crear un recorte",
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        clips.forEach { clip ->
            SavedClipCard(
                clip = clip,
                isPreviewing = previewingClipId == clip.id,
                onTogglePreview = { onToggleClipPreview(clip) },
                onApplyAs = { target -> onReapplyClip(clip, target) },
                onDelete = { onRequestDelete(clip) },
                accentColor = accentColor,
                appColors = appColors
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SavedClipCard(
    clip: SavedRingtoneClip,
    isPreviewing: Boolean,
    onTogglePreview: () -> Unit,
    onApplyAs: (RingtoneTarget) -> Unit,
    onDelete: () -> Unit,
    accentColor: Color,
    appColors: AppThemeColors
) {
    val onAccentColor = if (accentColor.luminance() > 0.55f) Color(0xFF0A0C12) else Color.White
    val formattedDate = remember(clip.dateAddedMs) {
        if (clip.dateAddedMs > 0L) {
            runCatching {
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(clip.dateAddedMs))
            }.getOrDefault("")
        } else {
            ""
        }
    }

    SurfaceCard(appColors = appColors) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (isPreviewing) accentColor else accentColor.copy(alpha = 0.16f))
                    .clickable(onClick = onTogglePreview),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPreviewing) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isPreviewing) "Detener recorte" else "Reproducir recorte",
                    tint = if (isPreviewing) onAccentColor else accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = clip.title,
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = appColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val metaParts = buildList {
                    if (clip.artist.isNotBlank()) add(clip.artist)
                    add(RingtoneMetadataSanitizer.formatDurationLabel(clip.durationMs))
                    if (formattedDate.isNotBlank()) add(formattedDate)
                }
                Text(
                    text = metaParts.joinToString(" · "),
                    fontFamily = PlusJakartaSans,
                    fontSize = 11.5.sp,
                    color = appColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0x1AEF4444))
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Eliminar recorte",
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                RingtoneTarget.RINGTONE,
                RingtoneTarget.NOTIFICATION,
                RingtoneTarget.ALARM
            ).forEach { target ->
                val isCurrentCategory = clip.target == target
                Row(
                    modifier = Modifier
                        .clip(PillShape)
                        .background(
                            if (isCurrentCategory) accentColor.copy(alpha = 0.16f)
                            else if (appColors.isDark) Color(0x16FFFFFF) else Color(0x0A000000)
                        )
                        .border(
                            width = 0.8.dp,
                            color = if (isCurrentCategory) accentColor.copy(alpha = 0.65f) else appColors.cardBorder,
                            shape = PillShape
                        )
                        .clickable { onApplyAs(target) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = targetIcon(target),
                        contentDescription = null,
                        tint = if (isCurrentCategory) accentColor else appColors.textSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Usar como ${target.shortLabel}",
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = if (isCurrentCategory) accentColor else appColors.textPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun SurfaceCard(
    appColors: AppThemeColors,
    content: @Composable ColumnScope.() -> Unit
) {
    val bg = if (appColors.isDark) Color(0xFF161922) else Color.White
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(bg)
            .border(0.8.dp, appColors.cardBorder, CardShape)
            .padding(14.dp),
        content = content
    )
}

private fun targetIcon(target: RingtoneTarget): ImageVector = when (target) {
    RingtoneTarget.RINGTONE -> Icons.Default.RingVolume
    RingtoneTarget.NOTIFICATION -> Icons.Default.NotificationsActive
    RingtoneTarget.ALARM -> Icons.Default.Alarm
    RingtoneTarget.SAVE_CLIP_ONLY -> Icons.Default.Save
}
