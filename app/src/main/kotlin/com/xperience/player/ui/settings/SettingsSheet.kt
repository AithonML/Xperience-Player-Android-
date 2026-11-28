package com.xperience.player.ui.settings

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import com.xperience.player.R
import com.xperience.player.domain.model.update.ManualCheckFeedback
import com.xperience.player.domain.model.update.UpdateUiState
import com.xperience.player.ui.adaptive.AdaptiveModalContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xperience.player.domain.model.DataUsageSummary
import com.xperience.player.domain.model.FolderDepthMode
import com.xperience.player.domain.model.FolderListType
import com.xperience.player.domain.model.FolderScanRule
import com.xperience.player.domain.model.LocalScanFilterConfig
import com.xperience.player.domain.model.ScanFilterMode
import com.xperience.player.domain.model.StreamingAudioQuality
import com.xperience.player.domain.model.YtmAccountState
import com.xperience.player.domain.model.download.DownloadAudioFormat
import com.xperience.player.domain.model.download.DownloadMetadataOptions
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
import kotlin.math.roundToInt

private val OptionCardShape = ContinuousRoundedRectangle(percent = 28)

private fun formatDownloadFolderDisplay(pathOrUri: String): String {
    if (pathOrUri.isBlank()) return "Default Music Folder"
    if (pathOrUri.startsWith("content://")) {
        try {
            val decoded = Uri.decode(pathOrUri)
            val docPart = decoded.substringAfter("/tree/").substringAfter(":")
            if (docPart.isNotBlank()) {
                val storage = if (decoded.contains("primary")) "Internal Storage" else "External SD"
                return "/$docPart ($storage)"
            }
        } catch (_: Exception) {}
        return "Selected Folder"
    }
    return pathOrUri
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
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
    // Local Scan Blacklist & Whitelist State
    localScanFilterConfig: LocalScanFilterConfig = LocalScanFilterConfig(),
    onUpdateLocalScanFilterConfig: (LocalScanFilterConfig) -> Unit = {}
) {
    if (!isOpen) return

    val context = LocalContext.current
    val accentColor = LocalAccentColor.current
    val appColors = LocalAppColors.current

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            onAddFolder(it)
        }
    }

    val downloadFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            val path = it.toString()
            onUpdateDownloadOptions(downloadOptions.copy(downloadDirectory = path))
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

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    AdaptiveModalContainer(
        isOpen = isOpen,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = appColors.surface,
        dialogMaxWidth = 640.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Settings",
                    color = appColors.textPrimary,
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = appColors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // =========================================================================
            // SECTION 0: YouTube Music Account
            // =========================================================================
            YouTubeMusicAccountSection(
                accountState = ytmAccountState,
                isSyncing = isSyncingYtmAccount,
                accentColor = accentColor,
                appColors = appColors,
                onSignInClick = onSignInYtmClick,
                onSignOutClick = onSignOutYtmClick,
                onForceSyncClick = onForceSyncYtmClick,
                onImportPlaylistsClick = onImportYtmPlaylistsClick
            )

            Spacer(modifier = Modifier.height(24.dp))

            // =========================================================================
            // SECTION 1: Theme Mode (System Default / Dark / Light)
            // =========================================================================
            Text(
                text = "Theme",
                color = appColors.textPrimary,
                fontFamily = GoogleSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "Choose your preferred appearance or synchronize automatically with the system.",
                color = appColors.textSecondary,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Normal,
                fontSize = 12.5.sp,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 3 Segmented Option Cards: System / Dark / Light
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ThemeCard(
                    label = "System",
                    icon = Icons.Default.BrightnessAuto,
                    isSelected = selectedThemeMode == ThemeMode.SYSTEM,
                    accentColor = accentColor,
                    appColors = appColors,
                    onClick = { onSelectThemeMode(ThemeMode.SYSTEM) },
                    modifier = Modifier.weight(1f)
                )

                ThemeCard(
                    label = "Dark",
                    icon = Icons.Default.DarkMode,
                    isSelected = selectedThemeMode == ThemeMode.DARK,
                    accentColor = accentColor,
                    appColors = appColors,
                    onClick = { onSelectThemeMode(ThemeMode.DARK) },
                    modifier = Modifier.weight(1f)
                )

                ThemeCard(
                    label = "Light",
                    icon = Icons.Default.LightMode,
                    isSelected = selectedThemeMode == ThemeMode.LIGHT,
                    accentColor = accentColor,
                    appColors = appColors,
                    onClick = { onSelectThemeMode(ThemeMode.LIGHT) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // =========================================================================
            // SECTION 2: Accent Color (Dynamic + Presets)
            // =========================================================================
            Text(
                text = "Accent Color",
                color = appColors.textPrimary,
                fontFamily = GoogleSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "Customize interface controls or enable dynamic mode to adapt automatically to album artwork.",
                color = appColors.textSecondary,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Normal,
                fontSize = 12.5.sp,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Dynamic Accent Card (DEFAULT OPTION)
            val isDynamic = selectedAccentOption == AccentColorOption.DYNAMIC
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(OptionCardShape)
                    .background(
                        if (isDynamic) accentColor.copy(alpha = 0.16f) else appColors.surfaceVariant
                    )
                    .border(
                        width = if (isDynamic) 1.5.dp else 1.dp,
                        color = if (isDynamic) accentColor else appColors.cardBorder,
                        shape = OptionCardShape
                    )
                    .clickable { onSelectAccentOption(AccentColorOption.DYNAMIC) }
                    .padding(horizontal = 16.dp, vertical = 13.dp)
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
                        // Dynamic Rainbow / Star Icon
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            Color(0xFFFF007A),
                                            Color(0xFF7928CA),
                                            Color(0xFF0070F3),
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
                                text = "Smoothly adapts and transitions accent colors from music artwork",
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
                                tint = Color.Black,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Static Predefined Colors Row
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
                            .size(42.dp)
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
                                tint = if (color == Color(0xFF00E5FF) || color == Color(0xFF10B981)) Color.Black else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // =========================================================================
            // SECTION 3: LiquidGlass Configurable Sliders
            // =========================================================================
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
                        fontSize = 18.sp
                    )
                }

                // Reset button to defaults
                Text(
                    text = "Reset",
                    color = accentColor,
                    fontFamily = PlusJakartaSans,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onResetLiquidGlassSettings() }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Fine-tune optical glass and refraction parameters in real time.",
                color = appColors.textSecondary,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Normal,
                fontSize = 12.5.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Slider 1: Blur Radius
            GlassSliderItem(
                title = "Blur Radius",
                valueDisplay = "${String.format(Locale.US, "%.1f", glassSettings.blurRadiusDp)} dp",
                subtitle = "Backdrop blur radius behind glass surfaces",
                value = glassSettings.blurRadiusDp,
                valueRange = 0f..12f,
                accentColor = accentColor,
                appColors = appColors,
                onValueChange = onUpdateBlurRadius
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Slider 2: Refraction Strength
            GlassSliderItem(
                title = "Refraction Strength (Lens)",
                valueDisplay = "${(glassSettings.refractionStrength * 100).roundToInt()}%",
                subtitle = "Optical lens deformation and AGSL shader curvature",
                value = glassSettings.refractionStrength,
                valueRange = 0f..1f,
                accentColor = accentColor,
                appColors = appColors,
                onValueChange = onUpdateRefractionStrength
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Slider 3: Glass Tint Opacity
            GlassSliderItem(
                title = "Glass Tint Opacity (Surface)",
                valueDisplay = "${(glassSettings.surfaceTintOpacity * 100).roundToInt()}%",
                subtitle = "Density and tint darkening of the glass body",
                value = glassSettings.surfaceTintOpacity,
                valueRange = 0f..1f,
                accentColor = accentColor,
                appColors = appColors,
                onValueChange = onUpdateSurfaceTintOpacity
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Slider 4: Specular & Meniscus
            GlassSliderItem(
                title = "Specular & Meniscus (Bezel)",
                valueDisplay = "${(glassSettings.specularMeniscus * 100).roundToInt()}%",
                subtitle = "Fresnel rim highlight and edge specular shine",
                value = glassSettings.specularMeniscus,
                valueRange = 0f..1f,
                accentColor = accentColor,
                appColors = appColors,
                onValueChange = onUpdateSpecularMeniscus
            )

            Spacer(modifier = Modifier.height(24.dp))

            // =========================================================================
            // SECTION 3B: Streaming Quality & Playback
            // =========================================================================
            Text(
                text = stringResource(R.string.settings_audio_quality_title),
                color = appColors.textPrimary,
                fontFamily = GoogleSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_audio_quality_subtitle),
                color = appColors.textSecondary,
                fontFamily = PlusJakartaSans,
                fontSize = 12.5.sp,
                lineHeight = 17.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            // WiFi Quality Selector
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Wifi, contentDescription = null, tint = Color(0xFF0A84FF), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.settings_wifi_quality),
                    color = appColors.textPrimary,
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.5.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            QualityTierSelectorRow(
                selected = playbackPreferences.wifiQuality,
                accentColor = accentColor,
                appColors = appColors,
                onSelect = onSelectWifiQuality
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Mobile Data Quality Selector
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CellTower, contentDescription = null, tint = Color(0xFFFF9F0A), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.settings_mobile_quality),
                    color = appColors.textPrimary,
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.5.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            QualityTierSelectorRow(
                selected = playbackPreferences.mobileDataQuality,
                accentColor = Color(0xFFFF9F0A),
                appColors = appColors,
                onSelect = onSelectMobileQuality
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Next-Song Preloading (Gapless) Toggle
            SettingsToggleCard(
                title = stringResource(R.string.settings_gapless_preload_title),
                subtitle = stringResource(R.string.settings_gapless_preload_subtitle),
                checked = playbackPreferences.gaplessPreloadEnabled,
                onCheckedChange = onToggleGaplessPreload,
                accentColor = accentColor,
                appColors = appColors
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Pause at 0 Volume Toggle
            SettingsToggleCard(
                title = stringResource(R.string.settings_pause_zero_volume_title),
                subtitle = stringResource(R.string.settings_pause_zero_volume_subtitle),
                checked = playbackPreferences.pauseAtZeroVolumeEnabled,
                onCheckedChange = onTogglePauseAtZeroVolume,
                accentColor = accentColor,
                appColors = appColors
            )

            Spacer(modifier = Modifier.height(24.dp))

            // =========================================================================
            // SECTION 3C: Data Usage & Restrictions
            // =========================================================================
            Text(
                text = stringResource(R.string.data_usage_title),
                color = appColors.textPrimary,
                fontFamily = GoogleSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.data_usage_subtitle),
                color = appColors.textSecondary,
                fontFamily = PlusJakartaSans,
                fontSize = 12.5.sp,
                lineHeight = 17.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(appColors.surfaceVariant)
                    .border(1.dp, appColors.cardBorder, RoundedCornerShape(16.dp))
                    .clickable(onClick = onOpenDataUsageClick)
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = Color(0xFF0A84FF), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${stringResource(R.string.data_usage_wifi)}: ${DataUsageTracker.formatBytes(dataUsageSummary.periodWifiBytes)}",
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = appColors.textPrimary
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CellTower, contentDescription = null, tint = Color(0xFFFF9F0A), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${stringResource(R.string.data_usage_mobile)}: ${DataUsageTracker.formatBytes(dataUsageSummary.periodMobileBytes)}",
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = appColors.textPrimary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.16f))
                        .padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.DataUsage, contentDescription = null, tint = accentColor, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.data_usage_open_button),
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        color = accentColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // =========================================================================
            // SECTION 4: Local Music Folders
            // =========================================================================
            Text(
                text = "Local Music Folders",
                color = appColors.textPrimary,
                fontFamily = GoogleSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Manage device folders where Xperience Player indexes local audio files without relying on placeholders.",
                color = appColors.textSecondary,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Action Button 1: Add music folder
            LiquidGlassPill(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                tintColor = appColors.glassPillTint,
                elevation = 6.dp,
                onClick = { folderPickerLauncher.launch(null) }
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

            // Action Button 2: Scan device storage
            LiquidGlassPill(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                tintColor = appColors.glassPillTint,
                elevation = 6.dp,
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

            Spacer(modifier = Modifier.height(16.dp))

            // List of added folders
            if (folders.isNotEmpty()) {
                Text(
                    text = "Indexed Folders (${folders.size})",
                    color = appColors.textPrimary,
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                folders.forEach { folderUri ->
                    val decoded = Uri.decode(folderUri)
                    val displayName = decoded.substringAfterLast(":").ifBlank { decoded }.substringAfterLast("/")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(appColors.surfaceVariant)
                            .border(1.dp, appColors.cardBorder, RoundedCornerShape(12.dp))
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
                                tint = appColors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = displayName,
                                color = appColors.textPrimary,
                                fontFamily = PlusJakartaSans,
                                fontSize = 13.sp,
                                maxLines = 1
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
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Local Scan Blacklist & Whitelist + Shallow/Deep Controls
            Spacer(modifier = Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(appColors.surfaceVariant)
                    .border(1.dp, appColors.cardBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.scan_filter_title),
                    color = appColors.textPrimary,
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.5.sp
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = stringResource(R.string.scan_filter_subtitle),
                    color = appColors.textSecondary,
                    fontFamily = PlusJakartaSans,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Filter Mode Pills: All / Blacklist / Whitelist
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) pillTint.copy(alpha = 0.2f) else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (isSelected) pillTint else appColors.cardBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    onUpdateLocalScanFilterConfig(localScanFilterConfig.copy(filterMode = mode))
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode.displayName,
                                fontFamily = GoogleSans,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.5.sp,
                                color = if (isSelected) pillTint else appColors.textPrimary,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Global Shallow vs Deep Scan Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.scan_filter_global_deep_title),
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = appColors.textPrimary
                        )
                        Text(
                            text = stringResource(R.string.scan_filter_global_deep_subtitle),
                            fontFamily = PlusJakartaSans,
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp,
                            color = appColors.textSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = localScanFilterConfig.globalDeepScan,
                        onCheckedChange = { deep ->
                            onUpdateLocalScanFilterConfig(localScanFilterConfig.copy(globalDeepScan = deep))
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Add to Blacklist / Add to Whitelist Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x1CFF453A))
                            .border(1.dp, Color(0x45FF453A), RoundedCornerShape(10.dp))
                            .clickable { blacklistFolderPickerLauncher.launch(null) }
                            .padding(vertical = 9.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, tint = Color(0xFFFF453A), modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = stringResource(R.string.scan_filter_add_blacklist),
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp,
                            color = Color(0xFFFF453A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x1C34C759))
                            .border(1.dp, Color(0x4534C759), RoundedCornerShape(10.dp))
                            .clickable { whitelistFolderPickerLauncher.launch(null) }
                            .padding(vertical = 9.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF34C759), modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = stringResource(R.string.scan_filter_add_whitelist),
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp,
                            color = Color(0xFF34C759),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Configured Folder Rules with Clear Visual Blacklist / Whitelist Badges + Per-Folder Depth Cycle
                if (localScanFilterConfig.rules.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    localScanFilterConfig.rules.forEach { rule ->
                        val isBlacklist = rule.listType == FolderListType.BLACKLIST
                        val badgeColor = if (isBlacklist) Color(0xFFFF453A) else Color(0xFF34C759)
                        val badgeIcon = if (isBlacklist) Icons.Default.Block else Icons.Default.CheckCircle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(badgeColor.copy(alpha = 0.08f))
                                .border(1.dp, badgeColor.copy(alpha = 0.35f), RoundedCornerShape(11.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(badgeIcon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(17.dp))
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
                            // Per-folder depth mode pill: Global -> Shallow -> Deep
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(7.dp))
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

            Spacer(modifier = Modifier.height(28.dp))

            // =========================================================================
            // SECTION 5: Downloads, Offline Priority & Metadata Tagging
            // =========================================================================
            Text(
                text = "Downloads, Offline Priority & Metadata Tagging",
                color = appColors.textPrimary,
                fontFamily = GoogleSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Downloaded songs automatically take priority over online streaming so playback is instant and zero-bandwidth.",
                color = appColors.textSecondary,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Subtitle: Default Download Quality
            Text(
                text = "Default Download Quality",
                color = appColors.textPrimary,
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 3 Quality Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DownloadAudioFormat.values().forEach { fmt ->
                    val isSelected = downloadOptions.defaultFormat == fmt
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(ContinuousRoundedRectangle(percent = 32))
                            .background(
                                if (isSelected) accentColor.copy(alpha = 0.22f)
                                else if (appColors.isDark) Color(0x18FFFFFF) else Color(0x0C000000)
                            )
                            .border(
                                1.2.dp,
                                if (isSelected) accentColor else Color.Transparent,
                                ContinuousRoundedRectangle(percent = 32)
                            )
                            .clickable {
                                onUpdateDownloadOptions(downloadOptions.copy(defaultFormat = fmt))
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = fmt.displayName,
                            fontFamily = GoogleSans,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.5.sp,
                            color = if (isSelected) accentColor else appColors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Download Folder Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(appColors.surfaceVariant)
                    .border(1.dp, appColors.cardBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
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
                    Text(
                        text = formatDownloadFolderDisplay(downloadOptions.downloadDirectory),
                        fontFamily = PlusJakartaSans,
                        fontSize = 11.5.sp,
                        color = appColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.15f))
                        .clickable { downloadFolderPickerLauncher.launch(null) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
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

            Spacer(modifier = Modifier.height(14.dp))

            // Metadata & Offline Checkboxes
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    DownloadOptionCheckItem(
                        title = "Title & Artist",
                        isChecked = downloadOptions.includeTitleAndArtist,
                        onCheckedChange = { onUpdateDownloadOptions(downloadOptions.copy(includeTitleAndArtist = it)) },
                        accentColor = accentColor,
                        appColors = appColors
                    )
                    DownloadOptionCheckItem(
                        title = "1280px High-Res Cover Art",
                        isChecked = downloadOptions.includeHighResCoverArt,
                        onCheckedChange = { onUpdateDownloadOptions(downloadOptions.copy(includeHighResCoverArt = it)) },
                        accentColor = accentColor,
                        appColors = appColors
                    )
                    DownloadOptionCheckItem(
                        title = "Synced Lyrics (.LRC)",
                        isChecked = downloadOptions.includeSynchronizedLyrics,
                        onCheckedChange = { onUpdateDownloadOptions(downloadOptions.copy(includeSynchronizedLyrics = it)) },
                        accentColor = accentColor,
                        appColors = appColors
                    )
                    DownloadOptionCheckItem(
                        title = "Offline Apple Music Canvas (.canvas.mp4)",
                        isChecked = downloadOptions.includeAppleCanvasOffline,
                        onCheckedChange = { onUpdateDownloadOptions(downloadOptions.copy(includeAppleCanvasOffline = it)) },
                        accentColor = accentColor,
                        appColors = appColors
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    DownloadOptionCheckItem(
                        title = "Album & Track #",
                        isChecked = downloadOptions.includeAlbumAndTrackNumber,
                        onCheckedChange = { onUpdateDownloadOptions(downloadOptions.copy(includeAlbumAndTrackNumber = it)) },
                        accentColor = accentColor,
                        appColors = appColors
                    )
                    DownloadOptionCheckItem(
                        title = "Genre & Release Year",
                        isChecked = downloadOptions.includeGenreAndReleaseYear,
                        onCheckedChange = { onUpdateDownloadOptions(downloadOptions.copy(includeGenreAndReleaseYear = it)) },
                        accentColor = accentColor,
                        appColors = appColors
                    )
                    DownloadOptionCheckItem(
                        title = "ReplayGain & ISRC",
                        isChecked = downloadOptions.includeIsrcAndCopyright,
                        onCheckedChange = { onUpdateDownloadOptions(downloadOptions.copy(includeIsrcAndCopyright = it, includeReplayGain = it)) },
                        accentColor = accentColor,
                        appColors = appColors
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // =========================================================================
            // SECTION 6: App Updates (GitHub Releases)
            // =========================================================================
            UpdatesSection(
                autoCheckUpdates = autoCheckUpdates,
                onToggleAutoCheckUpdates = onToggleAutoCheckUpdates,
                updateUiState = updateUiState,
                onCheckForUpdatesClick = onCheckForUpdatesClick,
                accentColor = accentColor,
                appColors = appColors
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun QualityTierSelectorRow(
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
                    .clip(ContinuousRoundedRectangle(percent = 28))
                    .background(
                        if (isSelected) accentColor.copy(alpha = 0.2f)
                        else appColors.surfaceVariant
                    )
                    .border(
                        width = if (isSelected) 1.3.dp else 1.dp,
                        color = if (isSelected) accentColor else appColors.cardBorder,
                        shape = ContinuousRoundedRectangle(percent = 28)
                    )
                    .clickable { onSelect(tier) }
                    .padding(vertical = 9.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontFamily = GoogleSans,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = if (isSelected) accentColor else appColors.textPrimary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SettingsToggleCard(
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
            .clip(RoundedCornerShape(14.dp))
            .background(appColors.surfaceVariant)
            .border(1.dp, appColors.cardBorder, RoundedCornerShape(14.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
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
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
        )
    }
}

@Composable
private fun DownloadOptionCheckItem(
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
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!isChecked) }
            .padding(vertical = 6.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(if (isChecked) accentColor else if (appColors.isDark) Color(0x28FFFFFF) else Color(0x18000000))
                .border(
                    1.dp,
                    if (isChecked) accentColor else if (appColors.isDark) Color(0x40FFFFFF) else Color(0x30000000),
                    RoundedCornerShape(5.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isChecked) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.Black,
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
            lineHeight = 15.sp
        )
    }
}

@Composable
private fun ThemeCard(
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
            .clip(OptionCardShape)
            .background(
                if (isSelected) accentColor.copy(alpha = 0.16f) else appColors.surfaceVariant
            )
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) accentColor else appColors.cardBorder,
                shape = OptionCardShape
            )
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
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
private fun GlassSliderItem(
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
            .clip(RoundedCornerShape(14.dp))
            .background(appColors.surfaceVariant)
            .border(1.dp, appColors.cardBorder, RoundedCornerShape(14.dp))
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
private fun YouTubeMusicAccountSection(
    accountState: YtmAccountState,
    isSyncing: Boolean,
    accentColor: Color,
    appColors: AppThemeColors,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onForceSyncClick: () -> Unit,
    onImportPlaylistsClick: () -> Unit = {}
) {
    Text(
        text = "YouTube Music Account",
        color = appColors.textPrimary,
        fontFamily = GoogleSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp
    )
    Spacer(modifier = Modifier.height(3.dp))
    Text(
        text = "Connect your Google account to sync personalized mixes, stations, new releases, and tastes.",
        color = appColors.textSecondary,
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 17.sp
    )

    Spacer(modifier = Modifier.height(12.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(OptionCardShape)
            .background(
                if (accountState.isSignedIn) accentColor.copy(alpha = 0.10f) else appColors.surfaceVariant
            )
            .border(
                width = if (accountState.isSignedIn) 1.3.dp else 1.dp,
                color = if (accountState.isSignedIn) accentColor.copy(alpha = 0.65f) else appColors.cardBorder,
                shape = OptionCardShape
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        if (accountState.isSignedIn) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
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
                                .size(44.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = accountState.accountName?.takeIf { it.isNotBlank() } ?: "YouTube Music Account",
                        color = appColors.textPrimary,
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
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

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.16f))
                        .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .clickable(enabled = !isSyncing, onClick = onForceSyncClick)
                        .padding(vertical = 10.dp, horizontal = 12.dp),
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
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isSyncing) "Syncing…" else "Force Sync",
                        color = accentColor,
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (appColors.isDark) Color(0x18FF5252) else Color(0x14D32F2F))
                        .border(
                            1.dp,
                            if (appColors.isDark) Color(0x45FF5252) else Color(0x35D32F2F),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable(enabled = !isSyncing, onClick = onSignOutClick)
                        .padding(vertical = 10.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Sign out",
                        tint = if (appColors.isDark) Color(0xFFFF8A80) else Color(0xFFC62828),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Sign Out",
                        color = if (appColors.isDark) Color(0xFFFF8A80) else Color(0xFFC62828),
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (appColors.isDark) Color(0x1FFF2D55) else Color(0x14D90429))
                    .border(
                        1.dp,
                        if (appColors.isDark) Color(0x55FF2D55) else Color(0x40D90429),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable(enabled = !isSyncing, onClick = onImportPlaylistsClick)
                    .padding(vertical = 10.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    contentDescription = "Import YouTube Music Playlists",
                    tint = if (appColors.isDark) Color(0xFFFF4D6D) else Color(0xFFD90429),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Import YouTube Music Playlists",
                    color = if (appColors.isDark) Color(0xFFFF4D6D) else Color(0xFFD90429),
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
        } else {
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
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(24.dp)
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
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = "Sign In",
                        color = Color.White,
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdatesSection(
    autoCheckUpdates: Boolean,
    onToggleAutoCheckUpdates: (Boolean) -> Unit,
    updateUiState: UpdateUiState,
    onCheckForUpdatesClick: () -> Unit,
    accentColor: Color,
    appColors: AppThemeColors
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.update_section_title),
                color = appColors.textPrimary,
                fontFamily = GoogleSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
        }

        if (updateUiState.currentVersionDisplay.isNotBlank()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(accentColor.copy(alpha = 0.20f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = updateUiState.currentVersionDisplay,
                    color = accentColor,
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(4.dp))

    Text(
        text = stringResource(R.string.update_section_description),
        color = appColors.textSecondary,
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 17.sp
    )

    Spacer(modifier = Modifier.height(14.dp))

    // Version & Auto-Check Card
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(OptionCardShape)
            .background(appColors.surfaceVariant)
            .border(1.dp, appColors.cardBorder, OptionCardShape)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Installed version row
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
                fontSize = 12.5.sp
            )
            Text(
                text = updateUiState.currentVersionDisplay.ifBlank { "v0.0.0" },
                color = appColors.textPrimary,
                fontFamily = GoogleSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = appColors.dividerColor, thickness = 1.dp)
        Spacer(modifier = Modifier.height(10.dp))

        // Auto-check Switch Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { onToggleAutoCheckUpdates(!autoCheckUpdates) }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.update_auto_check_title),
                    color = appColors.textPrimary,
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.5.sp,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.update_auto_check_subtitle),
                    color = appColors.textSecondary,
                    fontFamily = PlusJakartaSans,
                    fontSize = 11.5.sp,
                    lineHeight = 15.5.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Switch(
                checked = autoCheckUpdates,
                onCheckedChange = onToggleAutoCheckUpdates,
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

    Spacer(modifier = Modifier.height(12.dp))

    // Manual Check Action Button
    LiquidGlassPill(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(16.dp),
        tintColor = appColors.glassPillTint,
        elevation = 6.dp,
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

    // Inline Feedback Banner after manual check
    val feedback = updateUiState.manualCheckFeedback
    AnimatedVisibility(visible = feedback != null) {
        if (feedback != null) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(10.dp))
                when (feedback) {
                    is ManualCheckFeedback.UpToDate -> {
                        val green = Color(0xFF4CAF50)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x184CAF50))
                                .border(1.dp, green.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = green,
                                modifier = Modifier.size(18.dp)
                            )
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
                                .clip(RoundedCornerShape(12.dp))
                                .background(appColors.surfaceVariant)
                                .border(1.dp, appColors.cardBorder, RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(18.dp)
                            )
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
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (appColors.isDark) Color(0x1CFF5252) else Color(0x14D32F2F))
                                .border(
                                    1.dp,
                                    if (appColors.isDark) Color(0x45FF5252) else Color(0x35D32F2F),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = errorTint,
                                modifier = Modifier.size(18.dp)
                            )
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
