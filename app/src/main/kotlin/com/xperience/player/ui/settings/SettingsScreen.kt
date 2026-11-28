package com.xperience.player.ui.settings

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.xperience.player.R
import com.xperience.player.domain.model.DataUsageLimitConfig
import com.xperience.player.domain.model.DataUsageSummary
import com.xperience.player.domain.model.FolderDepthMode
import com.xperience.player.domain.model.FolderListType
import com.xperience.player.domain.model.FolderScanRule
import com.xperience.player.domain.model.LocalScanFilterConfig
import com.xperience.player.domain.model.ScanFilterMode
import com.xperience.player.domain.model.StreamingAudioQuality
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.domain.model.YtmAccountState
import com.xperience.player.domain.model.download.DownloadAudioFormat
import com.xperience.player.domain.model.download.DownloadMetadataOptions
import com.xperience.player.domain.model.lyrics.LyricsProviderConfig
import com.xperience.player.domain.model.lyrics.LyricsProviderId
import com.xperience.player.domain.model.update.ManualCheckFeedback
import com.xperience.player.domain.model.update.UpdateUiState
import com.xperience.player.service.DataUsageTracker
import com.xperience.player.service.LocalScanFilterEvaluator
import com.xperience.player.service.PlaybackPreferences
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.home.LiquidGlassSettings
import com.xperience.player.ui.liquidglass.LiquidGlassPill
import com.xperience.player.ui.theme.AccentColorOption
import com.xperience.player.ui.theme.AppThemeColors
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.PlusJakartaSans
import com.xperience.player.ui.theme.ThemeMode
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

private enum class SettingsSection {
    APPEARANCE,
    LIBRARY,
    YTM_ACCOUNT,
    DOWNLOADS,
    LYRICS_CANVAS,
    PLAYBACK,
    DATA_USAGE,
    UPDATES
}

private val ExpressiveGroupShape = RoundedCornerShape(28.dp)
private val ExpressiveCardShape = RoundedCornerShape(24.dp)
private val ExpressiveInnerShape = ContinuousRoundedRectangle(percent = 28)

private fun formatDownloadFolderLabel(pathOrUri: String): String {
    if (pathOrUri.isBlank()) return "Default Music Folder"
    if (pathOrUri.startsWith("content://")) {
        try {
            val decoded = Uri.decode(pathOrUri)
            val docPart = decoded.substringAfter("/tree/").substringAfter(":")
            if (docPart.isNotBlank()) {
                val storage = if (decoded.contains("primary")) "Internal Storage" else "External SD"
                return "/$docPart ($storage)"
            }
        } catch (_: Exception) {
        }
        return "Selected Folder"
    }
    return pathOrUri
}

private fun shortQualityLabel(quality: StreamingAudioQuality): String = when (quality) {
    StreamingAudioQuality.LOW_64 -> "64k"
    StreamingAudioQuality.REGULAR_128 -> "128k"
    StreamingAudioQuality.HIGH_160 -> "160k+"
    StreamingAudioQuality.FLAC_LOSSLESS -> "FLAC"
}

@Composable
fun SettingsScreen(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    // Theme Mode State
    selectedThemeMode: ThemeMode,
    onSelectThemeMode: (ThemeMode) -> Unit,
    // Accent Color State
    selectedAccentOption: AccentColorOption,
    dynamicArtworkColor: Color,
    onSelectAccentOption: (AccentColorOption) -> Unit,
    // LiquidGlass Settings State
    glassSettings: LiquidGlassSettings,
    onUpdateBlurRadius: (Float) -> Unit,
    onUpdateRefractionStrength: (Float) -> Unit,
    onUpdateSurfaceTintOpacity: (Float) -> Unit,
    onUpdateSpecularMeniscus: (Float) -> Unit,
    onResetLiquidGlassSettings: () -> Unit,
    // Library Folders State
    folders: List<String>,
    isScanning: Boolean,
    onScanMediaStore: () -> Unit,
    onAddFolder: (Uri) -> Unit,
    onRemoveFolder: (String) -> Unit,
    // Download Options State
    downloadOptions: DownloadMetadataOptions = DownloadMetadataOptions(),
    onUpdateDownloadOptions: (DownloadMetadataOptions) -> Unit = {},
    // YouTube Music Account State
    ytmAccountState: YtmAccountState = YtmAccountState(),
    isSyncingYtmAccount: Boolean = false,
    onSignInYtmClick: () -> Unit = {},
    onSignOutYtmClick: () -> Unit = {},
    onForceSyncYtmClick: () -> Unit = {},
    onImportYtmPlaylistsClick: () -> Unit = {},
    // App Updates State
    autoCheckUpdates: Boolean = true,
    onToggleAutoCheckUpdates: (Boolean) -> Unit = {},
    updateUiState: UpdateUiState = UpdateUiState(),
    onCheckForUpdatesClick: () -> Unit = {},
    // Streaming Quality & Playback State
    playbackPreferences: PlaybackPreferences = PlaybackPreferences(),
    onSelectWifiQuality: (StreamingAudioQuality) -> Unit = {},
    onSelectMobileQuality: (StreamingAudioQuality) -> Unit = {},
    onToggleGaplessPreload: (Boolean) -> Unit = {},
    onTogglePauseAtZeroVolume: (Boolean) -> Unit = {},
    // Data Usage & Restrictions State
    dataUsageSummary: DataUsageSummary = DataUsageSummary(),
    onOpenDataUsageClick: () -> Unit = {},
    onUpdateDataUsageConfig: (DataUsageLimitConfig) -> Unit = {},
    onResetDataUsagePeriod: () -> Unit = {},
    onClearDataUsageHistory: () -> Unit = {},
    onDownloadSuggestedTrack: (TrackItem) -> Unit = {},
    // Local Scan Blacklist & Whitelist State
    localScanFilterConfig: LocalScanFilterConfig = LocalScanFilterConfig(),
    onUpdateLocalScanFilterConfig: (LocalScanFilterConfig) -> Unit = {},
    // Lyrics & Apple Canvas State
    lyricsProviders: List<LyricsProviderConfig> = emptyList(),
    onMoveLyricsProviderPriority: (LyricsProviderId, Int) -> Unit = { _, _ -> },
    onSetLyricsProviderEnabled: (LyricsProviderId, Boolean) -> Unit = { _, _ -> }
) {
    var selectedSection by remember { mutableStateOf<SettingsSection?>(null) }
    var hubBackProgress by remember { mutableFloatStateOf(0f) }
    var hubBackEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var subSectionBackProgress by remember { mutableFloatStateOf(0f) }
    var subSectionBackEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }

    LaunchedEffect(isOpen) {
        if (!isOpen) {
            selectedSection = null
            hubBackProgress = 0f
            subSectionBackProgress = 0f
        }
    }

    // Predictive Back from a sub-section -> previews returning to the 8-section hub
    PredictiveBackHandler(enabled = isOpen && selectedSection != null) { progressFlow ->
        try {
            progressFlow.collect { backEvent ->
                subSectionBackProgress = backEvent.progress
                subSectionBackEdge = backEvent.swipeEdge
            }
            subSectionBackProgress = 0f
            selectedSection = null
        } catch (_: CancellationException) {
            subSectionBackProgress = 0f
        }
    }

    // Predictive Back from the hub -> previews returning to Home before calling onDismiss()
    PredictiveBackHandler(enabled = isOpen && selectedSection == null) { progressFlow ->
        try {
            progressFlow.collect { backEvent ->
                hubBackProgress = backEvent.progress
                hubBackEdge = backEvent.swipeEdge
            }
            hubBackProgress = 0f
            onDismiss()
        } catch (_: CancellationException) {
            hubBackProgress = 0f
        }
    }

    val context = LocalContext.current

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            runCatching {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
            }
            onAddFolder(it)
        }
    }

    val downloadFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            runCatching {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
            }
            onUpdateDownloadOptions(downloadOptions.copy(downloadDirectory = it.toString()))
        }
    }

    val blacklistFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            runCatching {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
            }
            val uriStr = it.toString()
            val updatedRules = localScanFilterConfig.rules
                .filterNot { r -> r.uriOrPath == uriStr } +
                FolderScanRule(
                    uriOrPath = uriStr,
                    displayName = LocalScanFilterEvaluator.formatFolderDisplayName(uriStr),
                    listType = FolderListType.BLACKLIST,
                    depthMode = FolderDepthMode.INHERIT_GLOBAL
                )
            onUpdateLocalScanFilterConfig(
                localScanFilterConfig.copy(
                    filterMode = ScanFilterMode.BLACKLIST,
                    rules = updatedRules
                )
            )
        }
    }

    val whitelistFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            runCatching {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
            }
            val uriStr = it.toString()
            val updatedRules = localScanFilterConfig.rules
                .filterNot { r -> r.uriOrPath == uriStr } +
                FolderScanRule(
                    uriOrPath = uriStr,
                    displayName = LocalScanFilterEvaluator.formatFolderDisplayName(uriStr),
                    listType = FolderListType.WHITELIST,
                    depthMode = FolderDepthMode.INHERIT_GLOBAL
                )
            onUpdateLocalScanFilterConfig(
                localScanFilterConfig.copy(
                    filterMode = ScanFilterMode.WHITELIST,
                    rules = updatedRules
                )
            )
        }
    }

    AnimatedVisibility(
        visible = isOpen,
        modifier = Modifier
            .fillMaxSize()
            .zIndex(90f),
        enter = slideInHorizontally(
            initialOffsetX = { (it * 0.16f).roundToInt() },
            animationSpec = spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow)
        ) + fadeIn(animationSpec = tween(220)) + scaleIn(
            initialScale = 0.96f,
            animationSpec = tween(240)
        ),
        exit = slideOutHorizontally(
            targetOffsetX = { (it * 0.18f).roundToInt() },
            animationSpec = tween(210)
        ) + fadeOut(animationSpec = tween(180))
    ) {
        val appColors = LocalAppColors.current
        val accentColor = LocalAccentColor.current

        val hubSwipeDirection = if (hubBackEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
        val hubCornerDp = (hubBackProgress * 32f).dp

        // Full-screen container with Predictive Back transform when dismissing to Home
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = hubBackProgress.coerceIn(0f, 1f)
                    val scale = 1f - 0.08f * p
                    scaleX = scale
                    scaleY = scale
                    translationX = hubSwipeDirection * p * 44.dp.toPx()
                    alpha = 1f - 0.12f * p
                    shape = RoundedCornerShape(hubCornerDp)
                    clip = p > 0.001f
                }
                .background(appColors.background)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            // Subtle expressive top ambient tint gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                accentColor.copy(alpha = if (appColors.isDark) 0.13f else 0.09f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // When swiping back from a sub-section, preview the hub behind the shrinking sub-section
            if (selectedSection != null && subSectionBackProgress > 0.001f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = subSectionBackProgress.coerceIn(0f, 1f)
                            val scale = 0.95f + 0.05f * p
                            scaleX = scale
                            scaleY = scale
                            alpha = 0.45f + 0.55f * p
                            translationX = -(1f - p) * 28.dp.toPx()
                        }
                ) {
                    SettingsHubContent(
                        selectedThemeMode = selectedThemeMode,
                        selectedAccentOption = selectedAccentOption,
                        foldersCount = folders.size,
                        localScanFilterConfig = localScanFilterConfig,
                        ytmAccountState = ytmAccountState,
                        downloadOptions = downloadOptions,
                        lyricsProviders = lyricsProviders,
                        playbackPreferences = playbackPreferences,
                        dataUsageSummary = dataUsageSummary,
                        autoCheckUpdates = autoCheckUpdates,
                        updateUiState = updateUiState,
                        accentColor = accentColor,
                        appColors = appColors,
                        onBack = onDismiss,
                        onSelectSection = {}
                    )
                }
            }

            AnimatedContent(
                targetState = selectedSection,
                label = "SettingsNavigation",
                transitionSpec = {
                    val isForward = initialState == null && targetState != null
                    if (isForward) {
                        (slideInHorizontally(
                            animationSpec = spring(
                                dampingRatio = 0.9f,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) { (it * 0.28f).roundToInt() } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(240)) { -(it * 0.12f).roundToInt() } +
                                scaleOut(targetScale = 0.96f, animationSpec = tween(240)) +
                                fadeOut(tween(180)))
                    } else {
                        (slideInHorizontally(tween(240)) { -(it * 0.12f).roundToInt() } +
                            scaleIn(initialScale = 0.96f, animationSpec = tween(240)) +
                            fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally(
                                animationSpec = spring(
                                    dampingRatio = 0.9f,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) { (it * 0.28f).roundToInt() } + fadeOut(tween(180)))
                    }
                }
            ) { section ->
                if (section == null) {
                    SettingsHubContent(
                        selectedThemeMode = selectedThemeMode,
                        selectedAccentOption = selectedAccentOption,
                        foldersCount = folders.size,
                        localScanFilterConfig = localScanFilterConfig,
                        ytmAccountState = ytmAccountState,
                        downloadOptions = downloadOptions,
                        lyricsProviders = lyricsProviders,
                        playbackPreferences = playbackPreferences,
                        dataUsageSummary = dataUsageSummary,
                        autoCheckUpdates = autoCheckUpdates,
                        updateUiState = updateUiState,
                        accentColor = accentColor,
                        appColors = appColors,
                        onBack = onDismiss,
                        onSelectSection = { selectedSection = it }
                    )
                } else {
                    val subSwipeDir = if (subSectionBackEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
                    val subCornerDp = (subSectionBackProgress * 30f).dp
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val p = subSectionBackProgress.coerceIn(0f, 1f)
                                val scale = 1f - 0.08f * p
                                scaleX = scale
                                scaleY = scale
                                translationX = subSwipeDir * p * 54.dp.toPx()
                                shape = RoundedCornerShape(subCornerDp)
                                clip = p > 0.001f
                            }
                            .background(appColors.background)
                    ) {
                        // Top subtle accent gradient inside sub-section
                        val sectionColor = sectionAccentColor(section, accentColor)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            sectionColor.copy(alpha = if (appColors.isDark) 0.12f else 0.08f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )

                        SettingsSubSectionScaffold(
                            section = section,
                            accentColor = accentColor,
                            appColors = appColors,
                            onBack = { selectedSection = null }
                        ) {
                            when (section) {
                                SettingsSection.APPEARANCE -> {
                                    AppearanceSectionContent(
                                        selectedThemeMode = selectedThemeMode,
                                        onSelectThemeMode = onSelectThemeMode,
                                        selectedAccentOption = selectedAccentOption,
                                        dynamicArtworkColor = dynamicArtworkColor,
                                        onSelectAccentOption = onSelectAccentOption,
                                        glassSettings = glassSettings,
                                        onUpdateBlurRadius = onUpdateBlurRadius,
                                        onUpdateRefractionStrength = onUpdateRefractionStrength,
                                        onUpdateSurfaceTintOpacity = onUpdateSurfaceTintOpacity,
                                        onUpdateSpecularMeniscus = onUpdateSpecularMeniscus,
                                        onResetLiquidGlassSettings = onResetLiquidGlassSettings,
                                        accentColor = accentColor,
                                        appColors = appColors
                                    )
                                }

                                SettingsSection.LIBRARY -> {
                                    LibraryFoldersSectionContent(
                                        folders = folders,
                                        isScanning = isScanning,
                                        onScanMediaStore = onScanMediaStore,
                                        onPickMusicFolder = { folderPickerLauncher.launch(null) },
                                        onRemoveFolder = onRemoveFolder,
                                        localScanFilterConfig = localScanFilterConfig,
                                        onUpdateLocalScanFilterConfig = onUpdateLocalScanFilterConfig,
                                        onPickBlacklistFolder = { blacklistFolderPickerLauncher.launch(null) },
                                        onPickWhitelistFolder = { whitelistFolderPickerLauncher.launch(null) },
                                        accentColor = accentColor,
                                        appColors = appColors
                                    )
                                }

                                SettingsSection.YTM_ACCOUNT -> {
                                    YtmAccountSectionContent(
                                        accountState = ytmAccountState,
                                        isSyncing = isSyncingYtmAccount,
                                        accentColor = accentColor,
                                        appColors = appColors,
                                        onSignInClick = onSignInYtmClick,
                                        onSignOutClick = onSignOutYtmClick,
                                        onForceSyncClick = onForceSyncYtmClick,
                                        onImportPlaylistsClick = onImportYtmPlaylistsClick
                                    )
                                }

                                SettingsSection.DOWNLOADS -> {
                                    DownloadsSectionContent(
                                        downloadOptions = downloadOptions,
                                        onUpdateDownloadOptions = onUpdateDownloadOptions,
                                        onPickDownloadFolder = { downloadFolderPickerLauncher.launch(null) },
                                        accentColor = accentColor,
                                        appColors = appColors
                                    )
                                }

                                SettingsSection.LYRICS_CANVAS -> {
                                    LyricsAndCanvasSectionContent(
                                        lyricsProviders = lyricsProviders,
                                        onMoveLyricsProviderPriority = onMoveLyricsProviderPriority,
                                        onSetLyricsProviderEnabled = onSetLyricsProviderEnabled,
                                        downloadOptions = downloadOptions,
                                        onUpdateDownloadOptions = onUpdateDownloadOptions,
                                        dataUsageSummary = dataUsageSummary,
                                        accentColor = accentColor,
                                        appColors = appColors
                                    )
                                }

                                SettingsSection.PLAYBACK -> {
                                    PlaybackSectionContent(
                                        playbackPreferences = playbackPreferences,
                                        onSelectWifiQuality = onSelectWifiQuality,
                                        onSelectMobileQuality = onSelectMobileQuality,
                                        onToggleGaplessPreload = onToggleGaplessPreload,
                                        onTogglePauseAtZeroVolume = onTogglePauseAtZeroVolume,
                                        accentColor = accentColor,
                                        appColors = appColors
                                    )
                                }

                                SettingsSection.DATA_USAGE -> {
                                    DataUsageContent(
                                        summary = dataUsageSummary,
                                        onUpdateConfig = onUpdateDataUsageConfig,
                                        onResetPeriod = onResetDataUsagePeriod,
                                        onClearHistory = onClearDataUsageHistory,
                                        onDownloadSuggestedTrack = onDownloadSuggestedTrack,
                                        showHeader = false,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                SettingsSection.UPDATES -> {
                                    UpdatesSectionContent(
                                        autoCheckUpdates = autoCheckUpdates,
                                        onToggleAutoCheckUpdates = onToggleAutoCheckUpdates,
                                        updateUiState = updateUiState,
                                        onCheckForUpdatesClick = onCheckForUpdatesClick,
                                        accentColor = accentColor,
                                        appColors = appColors
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun sectionAccentColor(section: SettingsSection, accentColor: Color): Color = when (section) {
    SettingsSection.APPEARANCE -> Color(0xFF8B5CF6)
    SettingsSection.LIBRARY -> Color(0xFFF59E0B)
    SettingsSection.YTM_ACCOUNT -> Color(0xFFFF2D55)
    SettingsSection.DOWNLOADS -> Color(0xFF10B981)
    SettingsSection.LYRICS_CANVAS -> Color(0xFFEC4899)
    SettingsSection.PLAYBACK -> Color(0xFF0A84FF)
    SettingsSection.DATA_USAGE -> Color(0xFF06B6D4)
    SettingsSection.UPDATES -> accentColor
}

private fun sectionIcon(section: SettingsSection): ImageVector = when (section) {
    SettingsSection.APPEARANCE -> Icons.Rounded.Palette
    SettingsSection.LIBRARY -> Icons.Default.Folder
    SettingsSection.YTM_ACCOUNT -> Icons.Default.AccountCircle
    SettingsSection.DOWNLOADS -> Icons.Rounded.DownloadForOffline
    SettingsSection.LYRICS_CANVAS -> Icons.Rounded.Lyrics
    SettingsSection.PLAYBACK -> Icons.Rounded.GraphicEq
    SettingsSection.DATA_USAGE -> Icons.Default.DataUsage
    SettingsSection.UPDATES -> Icons.Default.SystemUpdate
}

@Composable
private fun sectionTitle(section: SettingsSection): String = when (section) {
    SettingsSection.APPEARANCE -> stringResource(R.string.settings_section_appearance)
    SettingsSection.LIBRARY -> stringResource(R.string.settings_section_library)
    SettingsSection.YTM_ACCOUNT -> stringResource(R.string.settings_section_ytm)
    SettingsSection.DOWNLOADS -> stringResource(R.string.settings_section_downloads)
    SettingsSection.LYRICS_CANVAS -> stringResource(R.string.settings_section_lyrics_canvas)
    SettingsSection.PLAYBACK -> stringResource(R.string.settings_section_playback)
    SettingsSection.DATA_USAGE -> stringResource(R.string.settings_section_data_usage)
    SettingsSection.UPDATES -> stringResource(R.string.settings_section_updates)
}

@Composable
private fun SettingsHubContent(
    selectedThemeMode: ThemeMode,
    selectedAccentOption: AccentColorOption,
    foldersCount: Int,
    localScanFilterConfig: LocalScanFilterConfig,
    ytmAccountState: YtmAccountState,
    downloadOptions: DownloadMetadataOptions,
    lyricsProviders: List<LyricsProviderConfig>,
    playbackPreferences: PlaybackPreferences,
    dataUsageSummary: DataUsageSummary,
    autoCheckUpdates: Boolean,
    updateUiState: UpdateUiState,
    accentColor: Color,
    appColors: AppThemeColors,
    onBack: () -> Unit,
    onSelectSection: (SettingsSection) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Expressive Top Navigation Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExpressiveBackButton(
                    appColors = appColors,
                    onClick = onBack
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Large Material 3 Expressive Display Header
            Text(
                text = stringResource(R.string.settings_title),
                color = appColors.textPrimary,
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
                letterSpacing = (-0.6).sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_hub_subtitle),
                color = appColors.textSecondary,
                fontFamily = PlusJakartaSans,
                fontSize = 14.sp,
                lineHeight = 19.sp
            )

            Spacer(modifier = Modifier.height(22.dp))

            // Group 1: Experience & Playback
            ExpressiveGroupLabel(
                text = stringResource(R.string.settings_group_experience),
                appColors = appColors
            )
            ExpressiveCardGroup(appColors = appColors) {
                SettingsHubRow(
                    title = stringResource(R.string.settings_section_appearance),
                    subtitle = "${selectedThemeMode.label} • ${selectedAccentOption.label}",
                    icon = sectionIcon(SettingsSection.APPEARANCE),
                    badgeTint = sectionAccentColor(SettingsSection.APPEARANCE, accentColor),
                    appColors = appColors,
                    onClick = { onSelectSection(SettingsSection.APPEARANCE) }
                )
                ExpressiveRowDivider(appColors)
                SettingsHubRow(
                    title = stringResource(R.string.settings_section_playback),
                    subtitle = "WiFi ${shortQualityLabel(playbackPreferences.wifiQuality)} • Mobile ${shortQualityLabel(playbackPreferences.mobileDataQuality)}",
                    icon = sectionIcon(SettingsSection.PLAYBACK),
                    badgeTint = sectionAccentColor(SettingsSection.PLAYBACK, accentColor),
                    appColors = appColors,
                    onClick = { onSelectSection(SettingsSection.PLAYBACK) }
                )
                ExpressiveRowDivider(appColors)
                val activeLyrics = lyricsProviders.count { it.isEnabled }
                val lyricsSummary = buildString {
                    if (lyricsProviders.isNotEmpty()) {
                        append("$activeLyrics/${lyricsProviders.size} providers")
                    } else {
                        append("Synchronized lyrics")
                    }
                    append(" • Canvas ")
                    append(if (downloadOptions.includeAppleCanvasOffline) "Offline" else "Stream")
                }
                SettingsHubRow(
                    title = stringResource(R.string.settings_section_lyrics_canvas),
                    subtitle = lyricsSummary,
                    icon = sectionIcon(SettingsSection.LYRICS_CANVAS),
                    badgeTint = sectionAccentColor(SettingsSection.LYRICS_CANVAS, accentColor),
                    appColors = appColors,
                    onClick = { onSelectSection(SettingsSection.LYRICS_CANVAS) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Group 2: Library & Accounts
            ExpressiveGroupLabel(
                text = stringResource(R.string.settings_group_library_account),
                appColors = appColors
            )
            ExpressiveCardGroup(appColors = appColors) {
                val folderWord = if (foldersCount == 1) "folder" else "folders"
                SettingsHubRow(
                    title = stringResource(R.string.settings_section_library),
                    subtitle = "$foldersCount $folderWord • ${localScanFilterConfig.filterMode.displayName}",
                    icon = sectionIcon(SettingsSection.LIBRARY),
                    badgeTint = sectionAccentColor(SettingsSection.LIBRARY, accentColor),
                    appColors = appColors,
                    onClick = { onSelectSection(SettingsSection.LIBRARY) }
                )
                ExpressiveRowDivider(appColors)
                val ytmSummary = if (ytmAccountState.isSignedIn) {
                    "${ytmAccountState.accountName?.takeIf { it.isNotBlank() } ?: "Connected"} • Synced"
                } else {
                    "Not signed in • Tap to connect"
                }
                SettingsHubRow(
                    title = stringResource(R.string.settings_section_ytm),
                    subtitle = ytmSummary,
                    icon = sectionIcon(SettingsSection.YTM_ACCOUNT),
                    badgeTint = sectionAccentColor(SettingsSection.YTM_ACCOUNT, accentColor),
                    appColors = appColors,
                    onClick = { onSelectSection(SettingsSection.YTM_ACCOUNT) }
                )
                ExpressiveRowDivider(appColors)
                SettingsHubRow(
                    title = stringResource(R.string.settings_section_downloads),
                    subtitle = "${downloadOptions.defaultFormat.displayName} • ${formatDownloadFolderLabel(downloadOptions.downloadDirectory)}",
                    icon = sectionIcon(SettingsSection.DOWNLOADS),
                    badgeTint = sectionAccentColor(SettingsSection.DOWNLOADS, accentColor),
                    appColors = appColors,
                    onClick = { onSelectSection(SettingsSection.DOWNLOADS) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Group 3: Data & System
            ExpressiveGroupLabel(
                text = stringResource(R.string.settings_group_system),
                appColors = appColors
            )
            ExpressiveCardGroup(appColors = appColors) {
                SettingsHubRow(
                    title = stringResource(R.string.settings_section_data_usage),
                    subtitle = "WiFi ${DataUsageTracker.formatBytes(dataUsageSummary.periodWifiBytes)} • Mobile ${DataUsageTracker.formatBytes(dataUsageSummary.periodMobileBytes)}",
                    icon = sectionIcon(SettingsSection.DATA_USAGE),
                    badgeTint = sectionAccentColor(SettingsSection.DATA_USAGE, accentColor),
                    appColors = appColors,
                    onClick = { onSelectSection(SettingsSection.DATA_USAGE) }
                )
                ExpressiveRowDivider(appColors)
                val versionPart = updateUiState.currentVersionDisplay.ifBlank { "Installed" }
                val autoPart = if (autoCheckUpdates) "Auto-check on" else "Manual"
                SettingsHubRow(
                    title = stringResource(R.string.settings_section_updates),
                    subtitle = "$versionPart • $autoPart",
                    icon = sectionIcon(SettingsSection.UPDATES),
                    badgeTint = sectionAccentColor(SettingsSection.UPDATES, accentColor),
                    appColors = appColors,
                    onClick = { onSelectSection(SettingsSection.UPDATES) }
                )
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}

@Composable
private fun SettingsSubSectionScaffold(
    section: SettingsSection,
    accentColor: Color,
    appColors: AppThemeColors,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val badgeTint = sectionAccentColor(section, accentColor)
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Expressive Sub-Section Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExpressiveBackButton(
                    appColors = appColors,
                    onClick = onBack
                )
                Spacer(modifier = Modifier.width(14.dp))
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(badgeTint.copy(alpha = if (appColors.isDark) 0.22f else 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = sectionIcon(section),
                        contentDescription = null,
                        tint = badgeTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = sectionTitle(section),
                    color = appColors.textPrimary,
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 21.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                content = {
                    Spacer(modifier = Modifier.height(6.dp))
                    content()
                    Spacer(modifier = Modifier.height(36.dp))
                }
            )
        }
    }
}

@Composable
private fun ExpressiveBackButton(
    appColors: AppThemeColors,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(appColors.surface)
            .border(1.dp, appColors.cardBorder, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(R.string.settings_back),
            tint = appColors.textPrimary,
            modifier = Modifier.size(21.dp)
        )
    }
}

@Composable
private fun ExpressiveGroupLabel(
    text: String,
    appColors: AppThemeColors
) {
    Text(
        text = text.uppercase(Locale.getDefault()),
        color = appColors.textSecondary,
        fontFamily = GoogleSans,
        fontWeight = FontWeight.Bold,
        fontSize = 11.5.sp,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 10.dp, bottom = 8.dp)
    )
}

@Composable
private fun ExpressiveCardGroup(
    appColors: AppThemeColors,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ExpressiveGroupShape)
            .background(appColors.surface)
            .border(1.dp, appColors.cardBorder, ExpressiveGroupShape),
        content = content
    )
}

@Composable
private fun ExpressiveSectionCard(
    appColors: AppThemeColors,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ExpressiveCardShape)
            .background(appColors.surface)
            .border(1.dp, appColors.cardBorder, ExpressiveCardShape)
            .padding(18.dp),
        content = content
    )
}

@Composable
private fun ExpressiveRowDivider(appColors: AppThemeColors) {
    HorizontalDivider(
        color = appColors.dividerColor,
        thickness = 1.dp,
        modifier = Modifier.padding(start = 76.dp, end = 18.dp)
    )
}

@Composable
private fun SettingsHubRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badgeTint: Color,
    appColors: AppThemeColors,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(badgeTint.copy(alpha = if (appColors.isDark) 0.20f else 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = badgeTint,
                modifier = Modifier.size(23.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = appColors.textPrimary,
                fontFamily = GoogleSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = appColors.textSecondary,
                fontFamily = PlusJakartaSans,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = appColors.textTertiary,
            modifier = Modifier.size(22.dp)
        )
    }
}

// =============================================================================
// SUB-SECTION 1: Appearance & Personalization
// =============================================================================
@Composable
private fun AppearanceSectionContent(
    selectedThemeMode: ThemeMode,
    onSelectThemeMode: (ThemeMode) -> Unit,
    selectedAccentOption: AccentColorOption,
    dynamicArtworkColor: Color,
    onSelectAccentOption: (AccentColorOption) -> Unit,
    glassSettings: LiquidGlassSettings,
    onUpdateBlurRadius: (Float) -> Unit,
    onUpdateRefractionStrength: (Float) -> Unit,
    onUpdateSurfaceTintOpacity: (Float) -> Unit,
    onUpdateSpecularMeniscus: (Float) -> Unit,
    onResetLiquidGlassSettings: () -> Unit,
    accentColor: Color,
    appColors: AppThemeColors
) {
    // Theme Mode Card
    ExpressiveSectionCard(appColors = appColors) {
        Text(
            text = "Theme",
            color = appColors.textPrimary,
            fontFamily = GoogleSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = "Choose your preferred appearance or synchronize automatically with the system.",
            color = appColors.textSecondary,
            fontFamily = PlusJakartaSans,
            fontSize = 12.5.sp,
            lineHeight = 17.sp
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ExpressiveThemeCard(
                label = "System",
                icon = Icons.Default.BrightnessAuto,
                isSelected = selectedThemeMode == ThemeMode.SYSTEM,
                accentColor = accentColor,
                appColors = appColors,
                onClick = { onSelectThemeMode(ThemeMode.SYSTEM) },
                modifier = Modifier.weight(1f)
            )
            ExpressiveThemeCard(
                label = "Dark",
                icon = Icons.Default.DarkMode,
                isSelected = selectedThemeMode == ThemeMode.DARK,
                accentColor = accentColor,
                appColors = appColors,
                onClick = { onSelectThemeMode(ThemeMode.DARK) },
                modifier = Modifier.weight(1f)
            )
            ExpressiveThemeCard(
                label = "Light",
                icon = Icons.Default.LightMode,
                isSelected = selectedThemeMode == ThemeMode.LIGHT,
                accentColor = accentColor,
                appColors = appColors,
                onClick = { onSelectThemeMode(ThemeMode.LIGHT) },
                modifier = Modifier.weight(1f)
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Accent Color Card
    ExpressiveSectionCard(appColors = appColors) {
        Text(
            text = "Accent Color",
            color = appColors.textPrimary,
            fontFamily = GoogleSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = "Customize interface controls or enable dynamic mode to adapt automatically to album artwork.",
            color = appColors.textSecondary,
            fontFamily = PlusJakartaSans,
            fontSize = 12.5.sp,
            lineHeight = 17.sp
        )
        Spacer(modifier = Modifier.height(14.dp))

        val isDynamic = selectedAccentOption == AccentColorOption.DYNAMIC
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ExpressiveInnerShape)
                .background(
                    if (isDynamic) accentColor.copy(alpha = 0.16f) else appColors.surfaceVariant
                )
                .border(
                    width = if (isDynamic) 1.5.dp else 1.dp,
                    color = if (isDynamic) accentColor else appColors.cardBorder,
                    shape = ExpressiveInnerShape
                )
                .clickable { onSelectAccentOption(AccentColorOption.DYNAMIC) }
                .padding(horizontal = 14.dp, vertical = 13.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFFFF007A),
                                        Color(0xFF7928CA),
                                        dynamicArtworkColor,
                                        Color(0xFF00DFD8)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Dynamic (Album Cover)",
                                color = appColors.textPrimary,
                                fontFamily = GoogleSans,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(accentColor.copy(alpha = 0.25f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Default",
                                    color = accentColor,
                                    fontFamily = PlusJakartaSans,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Smoothly adapts accent colors from music artwork",
                            color = appColors.textSecondary,
                            fontFamily = PlusJakartaSans,
                            fontSize = 11.5.sp
                        )
                    }
                }

                if (isDynamic) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(accentColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = if (accentColor.luminance() > 0.5f) Color.Black else Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val staticOptions = listOf(
                AccentColorOption.TURQUOISE,
                AccentColorOption.PURPLE,
                AccentColorOption.RED,
                AccentColorOption.ORANGE,
                AccentColorOption.BLUE,
                AccentColorOption.GREEN
            )
            staticOptions.forEach { opt ->
                val isSelected = selectedAccentOption == opt
                val color = opt.color ?: Color.White
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) (if (appColors.isDark) Color.White else Color(0xFF10141D)) else appColors.cardBorder,
                            shape = CircleShape
                        )
                        .clickable { onSelectAccentOption(opt) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = opt.label,
                            tint = if (color.luminance() > 0.55f) Color.Black else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // LiquidGlass Effect Card
    ExpressiveSectionCard(appColors = appColors) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "LiquidGlass Effect",
                    color = appColors.textPrimary,
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp
                )
            }
            Text(
                text = "Reset",
                color = accentColor,
                fontFamily = PlusJakartaSans,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.14f))
                    .clickable { onResetLiquidGlassSettings() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Fine-tune optical glass and refraction parameters in real time.",
            color = appColors.textSecondary,
            fontFamily = PlusJakartaSans,
            fontSize = 12.5.sp
        )
        Spacer(modifier = Modifier.height(14.dp))

        ExpressiveGlassSlider(
            title = "Blur Radius",
            valueDisplay = "${String.format(Locale.US, "%.1f", glassSettings.blurRadiusDp)} dp",
            subtitle = "Backdrop blur radius behind glass surfaces",
            value = glassSettings.blurRadiusDp,
            valueRange = 0f..12f,
            accentColor = accentColor,
            appColors = appColors,
            onValueChange = onUpdateBlurRadius
        )
        Spacer(modifier = Modifier.height(10.dp))
        ExpressiveGlassSlider(
            title = "Refraction Strength (Lens)",
            valueDisplay = "${(glassSettings.refractionStrength * 100).roundToInt()}%",
            subtitle = "Optical lens deformation and AGSL shader curvature",
            value = glassSettings.refractionStrength,
            valueRange = 0f..1f,
            accentColor = accentColor,
            appColors = appColors,
            onValueChange = onUpdateRefractionStrength
        )
        Spacer(modifier = Modifier.height(10.dp))
        ExpressiveGlassSlider(
            title = "Glass Tint Opacity (Surface)",
            valueDisplay = "${(glassSettings.surfaceTintOpacity * 100).roundToInt()}%",
            subtitle = "Density and tint darkening of the glass body",
            value = glassSettings.surfaceTintOpacity,
            valueRange = 0f..1f,
            accentColor = accentColor,
            appColors = appColors,
            onValueChange = onUpdateSurfaceTintOpacity
        )
        Spacer(modifier = Modifier.height(10.dp))
        ExpressiveGlassSlider(
            title = "Specular & Meniscus (Bezel)",
            valueDisplay = "${(glassSettings.specularMeniscus * 100).roundToInt()}%",
            subtitle = "Fresnel rim highlight and edge specular shine",
            value = glassSettings.specularMeniscus,
            valueRange = 0f..1f,
            accentColor = accentColor,
            appColors = appColors,
            onValueChange = onUpdateSpecularMeniscus
        )
    }
}

// =============================================================================
// SUB-SECTION 2: Library & Local Folders
// =============================================================================
@Composable
private fun LibraryFoldersSectionContent(
    folders: List<String>,
    isScanning: Boolean,
    onScanMediaStore: () -> Unit,
    onPickMusicFolder: () -> Unit,
    onRemoveFolder: (String) -> Unit,
    localScanFilterConfig: LocalScanFilterConfig,
    onUpdateLocalScanFilterConfig: (LocalScanFilterConfig) -> Unit,
    onPickBlacklistFolder: () -> Unit,
    onPickWhitelistFolder: () -> Unit,
    accentColor: Color,
    appColors: AppThemeColors
) {
    ExpressiveSectionCard(appColors = appColors) {
        Text(
            text = "Local Music Folders",
            color = appColors.textPrimary,
            fontFamily = GoogleSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Manage device folders where Xperience Player indexes local audio files.",
            color = appColors.textSecondary,
            fontFamily = PlusJakartaSans,
            fontSize = 12.5.sp,
            lineHeight = 17.sp
        )
        Spacer(modifier = Modifier.height(14.dp))

        LiquidGlassPill(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            tintColor = appColors.glassPillTint,
            elevation = 4.dp,
            onClick = onPickMusicFolder
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Add Music Folder",
                    color = appColors.textPrimary,
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LiquidGlassPill(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            tintColor = appColors.glassPillTint,
            elevation = 4.dp,
            onClick = onScanMediaStore
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = accentColor,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (isScanning) "Scanning device..." else "Scan Device Storage",
                    color = appColors.textPrimary,
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.5.sp
                )
            }
        }

        if (folders.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Indexed Folders (${folders.size})",
                color = appColors.textPrimary,
                fontFamily = GoogleSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.5.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            folders.forEach { folderUri ->
                val decoded = Uri.decode(folderUri)
                val displayName = decoded.substringAfterLast(":").ifBlank { decoded }.substringAfterLast("/")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(appColors.surfaceVariant)
                        .border(1.dp, appColors.cardBorder, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = displayName,
                            color = appColors.textPrimary,
                            fontFamily = PlusJakartaSans,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { onRemoveFolder(folderUri) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Remove folder",
                            tint = Color(0xFFFF6B6B),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Blacklist & Whitelist Card
    ExpressiveSectionCard(appColors = appColors) {
        Text(
            text = stringResource(R.string.scan_filter_title),
            color = appColors.textPrimary,
            fontFamily = GoogleSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = stringResource(R.string.scan_filter_subtitle),
            color = appColors.textSecondary,
            fontFamily = PlusJakartaSans,
            fontSize = 12.5.sp,
            lineHeight = 17.sp
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ScanFilterMode.entries.forEach { mode ->
                val isSelected = localScanFilterConfig.filterMode == mode
                val pillTint = when (mode) {
                    ScanFilterMode.BLACKLIST -> Color(0xFFFF453A)
                    ScanFilterMode.WHITELIST -> Color(0xFF34C759)
                    ScanFilterMode.ALL -> accentColor
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) pillTint.copy(alpha = 0.2f) else appColors.surfaceVariant)
                        .border(
                            1.dp,
                            if (isSelected) pillTint else appColors.cardBorder,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            onUpdateLocalScanFilterConfig(localScanFilterConfig.copy(filterMode = mode))
                        }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.displayName,
                        fontFamily = GoogleSans,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp,
                        color = if (isSelected) pillTint else appColors.textPrimary,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        ExpressiveToggleRow(
            title = stringResource(R.string.scan_filter_global_deep_title),
            subtitle = stringResource(R.string.scan_filter_global_deep_subtitle),
            checked = localScanFilterConfig.globalDeepScan,
            onCheckedChange = { deep ->
                onUpdateLocalScanFilterConfig(localScanFilterConfig.copy(globalDeepScan = deep))
            },
            accentColor = accentColor,
            appColors = appColors
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x1CFF453A))
                    .border(1.dp, Color(0x45FF453A), RoundedCornerShape(12.dp))
                    .clickable(onClick = onPickBlacklistFolder)
                    .padding(vertical = 10.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Block, contentDescription = null, tint = Color(0xFFFF453A), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.scan_filter_add_blacklist),
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = Color(0xFFFF453A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x1C34C759))
                    .border(1.dp, Color(0x4534C759), RoundedCornerShape(12.dp))
                    .clickable(onClick = onPickWhitelistFolder)
                    .padding(vertical = 10.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF34C759), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.scan_filter_add_whitelist),
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = Color(0xFF34C759),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (localScanFilterConfig.rules.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            localScanFilterConfig.rules.forEach { rule ->
                val isBlacklist = rule.listType == FolderListType.BLACKLIST
                val badgeColor = if (isBlacklist) Color(0xFFFF453A) else Color(0xFF34C759)
                val badgeIcon = if (isBlacklist) Icons.Default.Block else Icons.Default.CheckCircle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(badgeColor.copy(alpha = 0.08f))
                        .border(1.dp, badgeColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(badgeIcon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = rule.displayName,
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp,
                            color = appColors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isBlacklist) "Blacklisted" else "Whitelisted",
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.5.sp,
                            color = badgeColor
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (appColors.isDark) Color(0x22FFFFFF) else Color(0x12000000))
                            .clickable {
                                val nextDepth = when (rule.depthMode) {
                                    FolderDepthMode.INHERIT_GLOBAL -> FolderDepthMode.SHALLOW
                                    FolderDepthMode.SHALLOW -> FolderDepthMode.DEEP
                                    FolderDepthMode.DEEP -> FolderDepthMode.INHERIT_GLOBAL
                                }
                                val updated = localScanFilterConfig.rules.map {
                                    if (it.uriOrPath == rule.uriOrPath) it.copy(depthMode = nextDepth) else it
                                }
                                onUpdateLocalScanFilterConfig(localScanFilterConfig.copy(rules = updated))
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = rule.depthMode.displayName,
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.5.sp,
                            color = appColors.textPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = {
                            val updated = localScanFilterConfig.rules.filterNot { it.uriOrPath == rule.uriOrPath }
                            onUpdateLocalScanFilterConfig(localScanFilterConfig.copy(rules = updated))
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Remove rule",
                            tint = Color(0xFFFF6B6B),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// SUB-SECTION 3: YouTube Music Account
// =============================================================================
@Composable
private fun YtmAccountSectionContent(
    accountState: YtmAccountState,
    isSyncing: Boolean,
    accentColor: Color,
    appColors: AppThemeColors,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onForceSyncClick: () -> Unit,
    onImportPlaylistsClick: () -> Unit
) {
    ExpressiveSectionCard(appColors = appColors) {
        Text(
            text = "Connect your Google account to sync personalized mixes, stations, new releases, and tastes.",
            color = appColors.textSecondary,
            fontFamily = PlusJakartaSans,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (accountState.isSignedIn) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(accentColor.copy(alpha = 0.11f))
                    .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.20f))
                        .border(1.dp, accentColor.copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (!accountState.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = accountState.avatarUrl,
                            contentDescription = accountState.accountName ?: "Account avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = accountState.accountName?.takeIf { it.isNotBlank() } ?: "YouTube Music Account",
                        color = appColors.textPrimary,
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitleParts = buildList {
                        accountState.accountHandle?.takeIf { it.isNotBlank() }?.let { add(it) }
                        if (accountState.lastSyncedAtMs > 0L) {
                            val rel = DateUtils.getRelativeTimeSpanString(
                                accountState.lastSyncedAtMs,
                                System.currentTimeMillis(),
                                DateUtils.MINUTE_IN_MILLIS
                            )
                            add("Synced $rel")
                        }
                    }
                    Text(
                        text = if (subtitleParts.isNotEmpty()) subtitleParts.joinToString(" • ") else "Connected to YouTube Music",
                        color = appColors.textSecondary,
                        fontFamily = PlusJakartaSans,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.22f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "Synced",
                        color = accentColor,
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accentColor.copy(alpha = 0.16f))
                        .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                        .clickable(enabled = !isSyncing, onClick = onForceSyncClick)
                        .padding(vertical = 12.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            color = accentColor,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Force sync YouTube Music",
                            tint = accentColor,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isSyncing) "Syncing…" else "Force Sync",
                        color = accentColor,
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp
                    )
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (appColors.isDark) Color(0x18FF5252) else Color(0x14D32F2F))
                        .border(
                            1.dp,
                            if (appColors.isDark) Color(0x45FF5252) else Color(0x35D32F2F),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable(enabled = !isSyncing, onClick = onSignOutClick)
                        .padding(vertical = 12.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Sign out",
                        tint = if (appColors.isDark) Color(0xFFFF8A80) else Color(0xFFC62828),
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Sign Out",
                        color = if (appColors.isDark) Color(0xFFFF8A80) else Color(0xFFC62828),
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (appColors.isDark) Color(0x1FFF2D55) else Color(0x14D90429))
                    .border(
                        1.dp,
                        if (appColors.isDark) Color(0x55FF2D55) else Color(0x40D90429),
                        RoundedCornerShape(14.dp)
                    )
                    .clickable(enabled = !isSyncing, onClick = onImportPlaylistsClick)
                    .padding(vertical = 12.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    contentDescription = "Import YouTube Music Playlists",
                    tint = if (appColors.isDark) Color(0xFFFF4D6D) else Color(0xFFD90429),
                    modifier = Modifier.size(19.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Import YouTube Music Playlists",
                    color = if (appColors.isDark) Color(0xFFFF4D6D) else Color(0xFFD90429),
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(appColors.surfaceVariant)
                    .border(1.dp, appColors.cardBorder, RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF2D55).copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = Color(0xFFFF2D55),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Not Signed In",
                            color = appColors.textPrimary,
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Sign in to load your YouTube Music feed",
                            color = appColors.textSecondary,
                            fontFamily = PlusJakartaSans,
                            fontSize = 12.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(accentColor)
                        .clickable(onClick = onSignInClick)
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "Sign In",
                        color = if (accentColor.luminance() > 0.55f) Color.Black else Color.White,
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

// =============================================================================
// SUB-SECTION 4: Downloads & Offline
// =============================================================================
@Composable
private fun DownloadsSectionContent(
    downloadOptions: DownloadMetadataOptions,
    onUpdateDownloadOptions: (DownloadMetadataOptions) -> Unit,
    onPickDownloadFolder: () -> Unit,
    accentColor: Color,
    appColors: AppThemeColors
) {
    ExpressiveSectionCard(appColors = appColors) {
        Text(
            text = "Default Download Quality",
            color = appColors.textPrimary,
            fontFamily = GoogleSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Downloaded songs automatically take priority over online streaming so playback is instant and zero-bandwidth.",
            color = appColors.textSecondary,
            fontFamily = PlusJakartaSans,
            fontSize = 12.5.sp,
            lineHeight = 17.sp
        )
        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DownloadAudioFormat.entries.forEach { fmt ->
                val isSelected = downloadOptions.defaultFormat == fmt
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(ContinuousRoundedRectangle(percent = 32))
                        .background(
                            if (isSelected) accentColor.copy(alpha = 0.22f)
                            else appColors.surfaceVariant
                        )
                        .border(
                            1.3.dp,
                            if (isSelected) accentColor else appColors.cardBorder,
                            ContinuousRoundedRectangle(percent = 32)
                        )
                        .clickable {
                            onUpdateDownloadOptions(downloadOptions.copy(defaultFormat = fmt))
                        }
                        .padding(vertical = 11.dp, horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = fmt.displayName,
                        fontFamily = GoogleSans,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp,
                        color = if (isSelected) accentColor else appColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Download Folder Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(appColors.surfaceVariant)
                .border(1.dp, appColors.cardBorder, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Download Folder",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    color = appColors.textSecondary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatDownloadFolderLabel(downloadOptions.downloadDirectory),
                    fontFamily = PlusJakartaSans,
                    fontSize = 12.5.sp,
                    color = appColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.16f))
                    .clickable(onClick = onPickDownloadFolder)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Change...",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = accentColor
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Metadata Tagging Card
    ExpressiveSectionCard(appColors = appColors) {
        Text(
            text = "Metadata & Offline Assets",
            color = appColors.textPrimary,
            fontFamily = GoogleSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                ExpressiveCheckItem(
                    title = "Title & Artist",
                    isChecked = downloadOptions.includeTitleAndArtist,
                    onCheckedChange = { onUpdateDownloadOptions(downloadOptions.copy(includeTitleAndArtist = it)) },
                    accentColor = accentColor,
                    appColors = appColors
                )
                ExpressiveCheckItem(
                    title = "1280px High-Res Cover Art",
                    isChecked = downloadOptions.includeHighResCoverArt,
                    onCheckedChange = { onUpdateDownloadOptions(downloadOptions.copy(includeHighResCoverArt = it)) },
                    accentColor = accentColor,
                    appColors = appColors
                )
                ExpressiveCheckItem(
                    title = "Synced Lyrics (.LRC)",
                    isChecked = downloadOptions.includeSynchronizedLyrics,
                    onCheckedChange = { onUpdateDownloadOptions(downloadOptions.copy(includeSynchronizedLyrics = it)) },
                    accentColor = accentColor,
                    appColors = appColors
                )
                ExpressiveCheckItem(
                    title = "Offline Apple Music Canvas (.canvas.mp4)",
                    isChecked = downloadOptions.includeAppleCanvasOffline,
                    onCheckedChange = { onUpdateDownloadOptions(downloadOptions.copy(includeAppleCanvasOffline = it)) },
                    accentColor = accentColor,
                    appColors = appColors
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                ExpressiveCheckItem(
                    title = "Album & Track #",
                    isChecked = downloadOptions.includeAlbumAndTrackNumber,
                    onCheckedChange = { onUpdateDownloadOptions(downloadOptions.copy(includeAlbumAndTrackNumber = it)) },
                    accentColor = accentColor,
                    appColors = appColors
                )
                ExpressiveCheckItem(
                    title = "Genre & Release Year",
                    isChecked = downloadOptions.includeGenreAndReleaseYear,
                    onCheckedChange = { onUpdateDownloadOptions(downloadOptions.copy(includeGenreAndReleaseYear = it)) },
                    accentColor = accentColor,
                    appColors = appColors
                )
                ExpressiveCheckItem(
                    title = "ReplayGain & ISRC",
                    isChecked = downloadOptions.includeIsrcAndCopyright,
                    onCheckedChange = {
                        onUpdateDownloadOptions(
                            downloadOptions.copy(
                                includeIsrcAndCopyright = it,
                                includeReplayGain = it
                            )
                        )
                    },
                    accentColor = accentColor,
                    appColors = appColors
                )
            }
        }
    }
}

// =============================================================================
// SUB-SECTION 5: Lyrics & Apple Canvas
// =============================================================================
@Composable
private fun LyricsAndCanvasSectionContent(
    lyricsProviders: List<LyricsProviderConfig>,
    onMoveLyricsProviderPriority: (LyricsProviderId, Int) -> Unit,
    onSetLyricsProviderEnabled: (LyricsProviderId, Boolean) -> Unit,
    downloadOptions: DownloadMetadataOptions,
    onUpdateDownloadOptions: (DownloadMetadataOptions) -> Unit,
    dataUsageSummary: DataUsageSummary,
    accentColor: Color,
    appColors: AppThemeColors
) {
    // Lyrics Providers & Priority Card
    ExpressiveSectionCard(appColors = appColors) {
        Text(
            text = stringResource(R.string.settings_lyrics_providers_title),
            color = appColors.textPrimary,
            fontFamily = GoogleSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.settings_lyrics_providers_subtitle),
            color = appColors.textSecondary,
            fontFamily = PlusJakartaSans,
            fontSize = 12.5.sp,
            lineHeight = 17.sp
        )
        Spacer(modifier = Modifier.height(14.dp))

        if (lyricsProviders.isEmpty()) {
            Text(
                text = "Default synchronized lyrics providers are active.",
                color = appColors.textSecondary,
                fontFamily = PlusJakartaSans,
                fontSize = 13.sp
            )
        } else {
            lyricsProviders.forEachIndexed { idx, provider ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = if (idx < lyricsProviders.lastIndex) 10.dp else 0.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(appColors.surfaceVariant)
                        .border(
                            1.dp,
                            if (provider.isEnabled) accentColor.copy(alpha = 0.35f) else appColors.cardBorder,
                            RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (provider.isEnabled) accentColor.copy(alpha = 0.18f)
                                else appColors.dividerColor
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = provider.priorityLabel,
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (provider.isEnabled) accentColor else appColors.textSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = provider.displayName,
                            color = appColors.textPrimary,
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = provider.capabilityBadge,
                            color = appColors.textSecondary,
                            fontFamily = PlusJakartaSans,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { onMoveLyricsProviderPriority(provider.id, -1) },
                        enabled = idx > 0,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Move up",
                            tint = if (idx > 0) appColors.textPrimary else appColors.textTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = { onMoveLyricsProviderPriority(provider.id, 1) },
                        enabled = idx < lyricsProviders.lastIndex,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Move down",
                            tint = if (idx < lyricsProviders.lastIndex) appColors.textPrimary else appColors.textTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Switch(
                        checked = provider.isEnabled,
                        onCheckedChange = { enabled -> onSetLyricsProviderEnabled(provider.id, enabled) },
                        colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = appColors.dividerColor)
        Spacer(modifier = Modifier.height(12.dp))

        ExpressiveToggleRow(
            title = stringResource(R.string.settings_lyrics_offline_title),
            subtitle = stringResource(R.string.settings_lyrics_offline_subtitle),
            checked = downloadOptions.includeSynchronizedLyrics,
            onCheckedChange = { onUpdateDownloadOptions(downloadOptions.copy(includeSynchronizedLyrics = it)) },
            accentColor = accentColor,
            appColors = appColors
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Apple Music Motion Canvas Card
    ExpressiveSectionCard(appColors = appColors) {
        Text(
            text = stringResource(R.string.settings_canvas_title),
            color = appColors.textPrimary,
            fontFamily = GoogleSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.settings_canvas_subtitle),
            color = appColors.textSecondary,
            fontFamily = PlusJakartaSans,
            fontSize = 12.5.sp,
            lineHeight = 17.sp
        )
        Spacer(modifier = Modifier.height(14.dp))

        ExpressiveToggleRow(
            title = stringResource(R.string.settings_canvas_offline_title),
            subtitle = stringResource(R.string.settings_canvas_offline_subtitle),
            checked = downloadOptions.includeAppleCanvasOffline,
            onCheckedChange = { onUpdateDownloadOptions(downloadOptions.copy(includeAppleCanvasOffline = it)) },
            accentColor = accentColor,
            appColors = appColors
        )

        val totalCanvasBytes = dataUsageSummary.totalCanvasBytes
        val totalLyricsBytes = dataUsageSummary.totalLyricsBytes
        if (totalCanvasBytes > 0L || totalLyricsBytes > 0L) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(appColors.surfaceVariant)
                    .border(1.dp, appColors.cardBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Lyrics: ${DataUsageTracker.formatBytes(totalLyricsBytes)}",
                    color = appColors.textSecondary,
                    fontFamily = PlusJakartaSans,
                    fontSize = 12.sp
                )
                Text(
                    text = "Canvas: ${DataUsageTracker.formatBytes(totalCanvasBytes)}",
                    color = appColors.textSecondary,
                    fontFamily = PlusJakartaSans,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// =============================================================================
// SUB-SECTION 6: Playback & Quality
// =============================================================================
@Composable
private fun PlaybackSectionContent(
    playbackPreferences: PlaybackPreferences,
    onSelectWifiQuality: (StreamingAudioQuality) -> Unit,
    onSelectMobileQuality: (StreamingAudioQuality) -> Unit,
    onToggleGaplessPreload: (Boolean) -> Unit,
    onTogglePauseAtZeroVolume: (Boolean) -> Unit,
    accentColor: Color,
    appColors: AppThemeColors
) {
    ExpressiveSectionCard(appColors = appColors) {
        Text(
            text = stringResource(R.string.settings_audio_quality_title),
            color = appColors.textPrimary,
            fontFamily = GoogleSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.settings_audio_quality_subtitle),
            color = appColors.textSecondary,
            fontFamily = PlusJakartaSans,
            fontSize = 12.5.sp,
            lineHeight = 17.sp
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Wifi, contentDescription = null, tint = Color(0xFF0A84FF), modifier = Modifier.size(17.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.settings_wifi_quality),
                color = appColors.textPrimary,
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        ExpressiveQualityTierRow(
            selected = playbackPreferences.wifiQuality,
            accentColor = accentColor,
            appColors = appColors,
            onSelect = onSelectWifiQuality
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CellTower, contentDescription = null, tint = Color(0xFFFF9F0A), modifier = Modifier.size(17.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.settings_mobile_quality),
                color = appColors.textPrimary,
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        ExpressiveQualityTierRow(
            selected = playbackPreferences.mobileDataQuality,
            accentColor = Color(0xFFFF9F0A),
            appColors = appColors,
            onSelect = onSelectMobileQuality
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    ExpressiveSectionCard(appColors = appColors) {
        ExpressiveToggleRow(
            title = stringResource(R.string.settings_gapless_preload_title),
            subtitle = stringResource(R.string.settings_gapless_preload_subtitle),
            checked = playbackPreferences.gaplessPreloadEnabled,
            onCheckedChange = onToggleGaplessPreload,
            accentColor = accentColor,
            appColors = appColors
        )
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = appColors.dividerColor)
        Spacer(modifier = Modifier.height(12.dp))
        ExpressiveToggleRow(
            title = stringResource(R.string.settings_pause_zero_volume_title),
            subtitle = stringResource(R.string.settings_pause_zero_volume_subtitle),
            checked = playbackPreferences.pauseAtZeroVolumeEnabled,
            onCheckedChange = onTogglePauseAtZeroVolume,
            accentColor = accentColor,
            appColors = appColors
        )
    }
}

// =============================================================================
// SUB-SECTION 8: Updates
// =============================================================================
@Composable
private fun UpdatesSectionContent(
    autoCheckUpdates: Boolean,
    onToggleAutoCheckUpdates: (Boolean) -> Unit,
    updateUiState: UpdateUiState,
    onCheckForUpdatesClick: () -> Unit,
    accentColor: Color,
    appColors: AppThemeColors
) {
    ExpressiveSectionCard(appColors = appColors) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.update_current_version_label),
                color = appColors.textSecondary,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Medium,
                fontSize = 13.5.sp
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.20f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = updateUiState.currentVersionDisplay.ifBlank { "v0.0.0" },
                    color = accentColor,
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.update_section_description),
            color = appColors.textSecondary,
            fontFamily = PlusJakartaSans,
            fontSize = 12.5.sp,
            lineHeight = 17.sp
        )

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = appColors.dividerColor)
        Spacer(modifier = Modifier.height(12.dp))

        ExpressiveToggleRow(
            title = stringResource(R.string.update_auto_check_title),
            subtitle = stringResource(R.string.update_auto_check_subtitle),
            checked = autoCheckUpdates,
            onCheckedChange = onToggleAutoCheckUpdates,
            accentColor = accentColor,
            appColors = appColors
        )

        Spacer(modifier = Modifier.height(16.dp))

        LiquidGlassPill(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            tintColor = appColors.glassPillTint,
            elevation = 4.dp,
            onClick = {
                if (!updateUiState.isChecking) {
                    onCheckForUpdatesClick()
                }
            }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (updateUiState.isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = accentColor,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (updateUiState.isChecking) {
                        stringResource(R.string.update_checking)
                    } else {
                        stringResource(R.string.update_check_now_button)
                    },
                    color = appColors.textPrimary,
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.5.sp
                )
            }
        }

        val feedback = updateUiState.manualCheckFeedback
        AnimatedVisibility(visible = feedback != null) {
            if (feedback != null) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    when (feedback) {
                        is ManualCheckFeedback.UpToDate -> {
                            val green = Color(0xFF4CAF50)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0x184CAF50))
                                    .border(1.dp, green.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = green, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.update_up_to_date, feedback.versionDisplay),
                                    color = if (appColors.isDark) Color(0xFFA5D6A7) else Color(0xFF2E7D32),
                                    fontFamily = PlusJakartaSans,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.5.sp,
                                    lineHeight = 17.sp
                                )
                            }
                        }

                        is ManualCheckFeedback.NoReleasesPublished -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(appColors.surfaceVariant)
                                    .border(1.dp, appColors.cardBorder, RoundedCornerShape(14.dp))
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.update_no_releases),
                                    color = appColors.textSecondary,
                                    fontFamily = PlusJakartaSans,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.5.sp,
                                    lineHeight = 17.sp
                                )
                            }
                        }

                        is ManualCheckFeedback.Error -> {
                            val errorTint = if (appColors.isDark) Color(0xFFFF8A80) else Color(0xFFC62828)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (appColors.isDark) Color(0x1CFF5252) else Color(0x14D32F2F))
                                    .border(
                                        1.dp,
                                        if (appColors.isDark) Color(0x45FF5252) else Color(0x35D32F2F),
                                        RoundedCornerShape(14.dp)
                                    )
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = errorTint, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = localizedUpdateErrorMessage(feedback.errorKind, feedback.detail),
                                    color = errorTint,
                                    fontFamily = PlusJakartaSans,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.5.sp,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// Shared Expressive Controls
// =============================================================================
@Composable
private fun ExpressiveThemeCard(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    accentColor: Color,
    appColors: AppThemeColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(ExpressiveInnerShape)
            .background(if (isSelected) accentColor.copy(alpha = 0.16f) else appColors.surfaceVariant)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) accentColor else appColors.cardBorder,
                shape = ExpressiveInnerShape
            )
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) accentColor else appColors.textSecondary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                color = if (isSelected) accentColor else appColors.textPrimary,
                fontFamily = GoogleSans,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun ExpressiveGlassSlider(
    title: String,
    valueDisplay: String,
    subtitle: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    accentColor: Color,
    appColors: AppThemeColors,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(appColors.surfaceVariant)
            .border(1.dp, appColors.cardBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = appColors.textPrimary,
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Medium,
                fontSize = 13.5.sp
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(accentColor.copy(alpha = 0.2f))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = valueDisplay,
                    color = accentColor,
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            color = appColors.textSecondary,
            fontFamily = PlusJakartaSans,
            fontSize = 11.sp
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = if (appColors.isDark) Color(0x35FFFFFF) else Color(0x22000000)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
        )
    }
}

@Composable
private fun ExpressiveQualityTierRow(
    selected: StreamingAudioQuality,
    accentColor: Color,
    appColors: AppThemeColors,
    onSelect: (StreamingAudioQuality) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        StreamingAudioQuality.entries.forEach { tier ->
            val isSelected = selected == tier
            val label = when (tier) {
                StreamingAudioQuality.LOW_64 -> "Low\n64k"
                StreamingAudioQuality.REGULAR_128 -> "Regular\n128k"
                StreamingAudioQuality.HIGH_160 -> "High\n160k+"
                StreamingAudioQuality.FLAC_LOSSLESS -> "FLAC\nLossless"
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(ExpressiveInnerShape)
                    .background(
                        if (isSelected) accentColor.copy(alpha = 0.2f)
                        else appColors.surfaceVariant
                    )
                    .border(
                        width = if (isSelected) 1.3.dp else 1.dp,
                        color = if (isSelected) accentColor else appColors.cardBorder,
                        shape = ExpressiveInnerShape
                    )
                    .clickable { onSelect(tier) }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontFamily = GoogleSans,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.5.sp,
                    lineHeight = 14.5.sp,
                    color = if (isSelected) accentColor else appColors.textPrimary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ExpressiveToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accentColor: Color,
    appColors: AppThemeColors
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = GoogleSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = appColors.textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontFamily = PlusJakartaSans,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = appColors.textSecondary
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accentColor,
                uncheckedThumbColor = appColors.textSecondary,
                uncheckedTrackColor = if (appColors.isDark) Color(0x28FFFFFF) else Color(0x20000000),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun ExpressiveCheckItem(
    title: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accentColor: Color,
    appColors: AppThemeColors,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onCheckedChange(!isChecked) }
            .padding(vertical = 7.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(21.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (isChecked) accentColor else if (appColors.isDark) Color(0x28FFFFFF) else Color(0x18000000))
                .border(
                    1.dp,
                    if (isChecked) accentColor else if (appColors.isDark) Color(0x40FFFFFF) else Color(0x30000000),
                    RoundedCornerShape(6.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isChecked) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = if (accentColor.luminance() > 0.55f) Color.Black else Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontFamily = PlusJakartaSans,
            fontSize = 12.sp,
            color = appColors.textPrimary,
            lineHeight = 15.5.sp
        )
    }
}
