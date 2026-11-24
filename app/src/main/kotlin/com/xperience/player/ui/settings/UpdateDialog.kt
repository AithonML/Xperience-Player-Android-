package com.xperience.player.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xperience.player.R
import com.xperience.player.domain.model.update.UpdateErrorKind
import com.xperience.player.domain.model.update.UpdateInstallStage
import com.xperience.player.domain.model.update.UpdateUiState
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.PlusJakartaSans
import java.util.Locale

private val UpdateDialogShape = ContinuousRoundedRectangle(percent = 18)
private val UpdateButtonPillShape = ContinuousRoundedRectangle(percent = 50)

@Composable
fun UpdateDialog(
    uiState: UpdateUiState,
    onDismiss: () -> Unit,
    onStartUpdate: () -> Unit,
    onCancelDownload: () -> Unit,
    onOpenInstallPermissionSettings: () -> Unit,
    onRetryLaunchInstaller: () -> Unit
) {
    val candidate = uiState.availableUpdate
    if (!uiState.isDialogOpen || candidate == null) return

    val appColors = LocalAppColors.current
    val accentColor = LocalAccentColor.current
    val primaryButtonTextColor = if (accentColor.luminance() > 0.5f) Color.Black else Color.White
    val isBusy = uiState.isBusyDownloadingOrVerifying
    val stage = uiState.installStage

    Dialog(
        onDismissRequest = {
            if (!isBusy) onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !isBusy,
            dismissOnClickOutside = !isBusy
        )
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 500.dp)
                .fillMaxWidth()
                .shadow(
                    elevation = 24.dp,
                    shape = UpdateDialogShape,
                    ambientColor = Color.Black.copy(alpha = if (appColors.isDark) 0.5f else 0.15f),
                    spotColor = Color.Black.copy(alpha = if (appColors.isDark) 0.7f else 0.25f)
                )
                .clip(UpdateDialogShape)
                .background(if (appColors.isDark) Color(0xFF16181F) else Color(0xFFFFFFFF))
                .border(
                    width = 1.2.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (appColors.isDark) 0.30f else 0.80f),
                            Color.White.copy(alpha = if (appColors.isDark) 0.08f else 0.25f)
                        )
                    ),
                    shape = UpdateDialogShape
                )
                .padding(22.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header Row: Icon + Title + Version Transition + Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(21.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.update_dialog_title),
                                fontFamily = GoogleSans,
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp,
                                color = appColors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(3.dp))

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(accentColor.copy(alpha = 0.20f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.update_dialog_version_transition,
                                        uiState.currentVersionDisplay,
                                        candidate.tagName
                                    ),
                                    fontFamily = GoogleSans,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    color = accentColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (!isBusy) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (appColors.isDark) Color(0x20FFFFFF) else Color(0x10000000))
                                .clickable { onDismiss() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.update_dialog_close),
                                tint = appColors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Release Notes Header
                Text(
                    text = stringResource(R.string.update_dialog_release_notes),
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    color = appColors.textSecondary
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Scrollable Changelog Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 76.dp, max = 210.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(appColors.surfaceVariant)
                        .border(1.dp, appColors.cardBorder, RoundedCornerShape(14.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp)
                ) {
                    Text(
                        text = candidate.releaseNotes.ifBlank {
                            stringResource(R.string.update_dialog_no_notes)
                        },
                        fontFamily = PlusJakartaSans,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = appColors.textPrimary
                    )
                }

                // Dynamic Progress / Verification / Permission / Error Status Card
                AnimatedVisibility(
                    visible = stage !is UpdateInstallStage.Ready,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        UpdateStageBanner(
                            stage = stage,
                            accentColor = accentColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Bottom Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val secondaryLabel = when (stage) {
                        is UpdateInstallStage.Downloading -> stringResource(R.string.update_dialog_cancel)
                        is UpdateInstallStage.Installing -> stringResource(R.string.update_dialog_close)
                        else -> stringResource(R.string.update_dialog_later)
                    }
                    val isSecondaryEnabled = stage !is UpdateInstallStage.Verifying

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(UpdateButtonPillShape)
                            .background(if (appColors.isDark) Color(0x20FFFFFF) else Color(0x10000000))
                            .clickable(enabled = isSecondaryEnabled) {
                                if (stage is UpdateInstallStage.Downloading) {
                                    onCancelDownload()
                                } else {
                                    onDismiss()
                                }
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = secondaryLabel,
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp,
                            color = if (isSecondaryEnabled) {
                                appColors.textSecondary
                            } else {
                                appColors.textSecondary.copy(alpha = 0.45f)
                            }
                        )
                    }

                    val primaryLabel = when (stage) {
                        is UpdateInstallStage.Ready -> stringResource(R.string.update_dialog_update_now)
                        is UpdateInstallStage.Downloading -> {
                            stage.percent?.let { "$it%" }
                                ?: stringResource(R.string.update_status_downloading_indeterminate)
                        }
                        is UpdateInstallStage.Verifying -> stringResource(R.string.update_status_verifying)
                        is UpdateInstallStage.AwaitingUnknownSourcesPermission ->
                            stringResource(R.string.update_dialog_open_settings)
                        is UpdateInstallStage.Installing -> stringResource(R.string.update_dialog_install_now)
                        is UpdateInstallStage.Error -> stringResource(R.string.update_dialog_retry)
                    }
                    val isPrimaryEnabled = !isBusy

                    Box(
                        modifier = Modifier
                            .weight(1.45f)
                            .clip(UpdateButtonPillShape)
                            .background(
                                if (isPrimaryEnabled) accentColor else accentColor.copy(alpha = 0.40f)
                            )
                            .clickable(enabled = isPrimaryEnabled) {
                                when (stage) {
                                    is UpdateInstallStage.Ready,
                                    is UpdateInstallStage.Error -> onStartUpdate()
                                    is UpdateInstallStage.AwaitingUnknownSourcesPermission ->
                                        onOpenInstallPermissionSettings()
                                    is UpdateInstallStage.Installing -> onRetryLaunchInstaller()
                                    is UpdateInstallStage.Downloading,
                                    is UpdateInstallStage.Verifying -> Unit
                                }
                            }
                            .padding(vertical = 12.dp, horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(
                                    color = primaryButtonTextColor,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = primaryLabel,
                                fontFamily = GoogleSans,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = primaryButtonTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateStageBanner(
    stage: UpdateInstallStage,
    accentColor: Color
) {
    val appColors = LocalAppColors.current

    when (stage) {
        is UpdateInstallStage.Ready -> Unit

        is UpdateInstallStage.Downloading -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(accentColor.copy(alpha = 0.12f))
                    .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (stage.percent != null) {
                            stringResource(R.string.update_status_downloading, stage.percent)
                        } else {
                            stringResource(R.string.update_status_downloading_indeterminate)
                        },
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.5.sp,
                        color = appColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    val sizeLabel = formatBytesProgress(stage.bytesDownloaded, stage.totalBytes)
                    if (sizeLabel.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = sizeLabel,
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp,
                            color = accentColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val trackColor = if (appColors.isDark) Color(0x33FFFFFF) else Color(0x22000000)
                if (stage.percent != null) {
                    LinearProgressIndicator(
                        progress = { (stage.percent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = accentColor,
                        trackColor = trackColor
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = accentColor,
                        trackColor = trackColor
                    )
                }
            }
        }

        is UpdateInstallStage.Verifying -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(accentColor.copy(alpha = 0.12f))
                    .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.update_status_verifying),
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.5.sp,
                        color = appColors.textPrimary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = accentColor,
                    trackColor = if (appColors.isDark) Color(0x33FFFFFF) else Color(0x22000000)
                )
            }
        }

        is UpdateInstallStage.AwaitingUnknownSourcesPermission -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(accentColor.copy(alpha = 0.12f))
                    .border(1.dp, accentColor.copy(alpha = 0.40f), RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.update_status_permission_needed),
                    fontFamily = PlusJakartaSans,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    color = appColors.textPrimary
                )
            }
        }

        is UpdateInstallStage.Installing -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x184CAF50))
                    .border(1.dp, Color(0xFF4CAF50).copy(alpha = 0.50f), RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.update_status_verified),
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    color = Color(0xFF4CAF50)
                )
            }
        }

        is UpdateInstallStage.Error -> {
            val errorTint = if (appColors.isDark) Color(0xFFFF8A80) else Color(0xFFC62828)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (appColors.isDark) Color(0x1CFF5252) else Color(0x14D32F2F))
                    .border(
                        1.dp,
                        if (appColors.isDark) Color(0x55FF5252) else Color(0x40D32F2F),
                        RoundedCornerShape(14.dp)
                    )
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = errorTint,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = localizedUpdateErrorMessage(stage.errorKind, stage.detail),
                    fontFamily = PlusJakartaSans,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    color = errorTint
                )
            }
        }
    }
}

@Composable
internal fun localizedUpdateErrorMessage(errorKind: UpdateErrorKind, detail: String?): String {
    return when (errorKind) {
        UpdateErrorKind.NETWORK -> stringResource(R.string.update_error_network)
        UpdateErrorKind.NO_APK_ASSET -> stringResource(R.string.update_error_no_apk)
        UpdateErrorKind.INVALID_URL -> stringResource(R.string.update_error_invalid_url)
        UpdateErrorKind.MISSING_CHECKSUM -> stringResource(R.string.update_error_missing_checksum)
        UpdateErrorKind.INTEGRITY_MISMATCH -> stringResource(R.string.update_error_integrity)
        UpdateErrorKind.INSTALLER_LAUNCH_FAILED -> stringResource(R.string.update_error_installer_launch)
        UpdateErrorKind.UNKNOWN -> stringResource(
            R.string.update_error_generic,
            detail?.takeIf { it.isNotBlank() } ?: "Unknown error"
        )
    }
}

private fun formatBytesProgress(downloaded: Long, total: Long): String {
    if (downloaded <= 0L && total <= 0L) return ""
    val mb = 1024.0 * 1024.0
    return if (total > 0L) {
        String.format(Locale.US, "%.1f / %.1f MB", downloaded / mb, total / mb)
    } else {
        String.format(Locale.US, "%.1f MB", downloaded / mb)
    }
}
