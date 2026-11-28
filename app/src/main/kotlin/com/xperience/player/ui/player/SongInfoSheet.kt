package com.xperience.player.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xperience.player.R
import com.xperience.player.domain.model.NetworkConnectionType
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.domain.model.TrackPlaybackInfo
import com.xperience.player.service.DataUsageTracker
import com.xperience.player.ui.adaptive.AdaptiveModalContainer
import com.xperience.player.ui.theme.AppThemeColors
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.PlusJakartaSans

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongInfoSheet(
    isOpen: Boolean,
    track: TrackItem?,
    playbackInfo: TrackPlaybackInfo?,
    onDismiss: () -> Unit,
    outputDeviceName: String = ""
) {
    if (!isOpen || track == null) return

    val appColors = LocalAppColors.current
    val accentColor = LocalAccentColor.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isCurrentTrackInfo = playbackInfo != null && playbackInfo.trackId == track.id
    val info = if (isCurrentTrackInfo) playbackInfo else null
    val effectiveOutputDevice = info?.outputDeviceLabel?.ifBlank { outputDeviceName }
        ?: outputDeviceName

    AdaptiveModalContainer(
        isOpen = true,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (appColors.isDark) Color(0xFF13151C) else Color(0xFFFFFFFF),
        dialogMaxWidth = 540.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.song_info_title),
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = appColors.textPrimary
                    )
                }

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (appColors.isDark) Color(0x20FFFFFF) else Color(0x10000000))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.update_dialog_close),
                        tint = appColors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Track Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (appColors.isDark) Color(0x18FFFFFF) else Color(0x0A000000))
                    .border(1.dp, appColors.cardBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF222632)),
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
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = info?.title?.ifBlank { track.title } ?: track.title,
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = appColors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = info?.artist?.ifBlank { track.artist } ?: track.artist,
                        fontFamily = PlusJakartaSans,
                        fontSize = 13.sp,
                        color = appColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (info != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val badgeColor = if (info.isLossless) Color(0xFFFFD60A) else accentColor
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(badgeColor.copy(alpha = 0.18f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = info.actualQualityLabel.ifBlank { info.sourceType.displayName },
                                    fontFamily = GoogleSans,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = badgeColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // FLAC Fallback Warning Banner
            if (info?.isFlacFallback == true) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x22FF9F0A))
                        .border(1.dp, Color(0x55FF9F0A), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = Color(0xFFFF9F0A),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.song_info_flac_fallback_notice),
                        fontFamily = PlusJakartaSans,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = appColors.textPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 1: Track Metadata
            SongInfoSectionHeader(
                icon = Icons.Default.MusicNote,
                title = stringResource(R.string.song_info_section_metadata),
                accentColor = accentColor,
                appColors = appColors
            )
            Spacer(modifier = Modifier.height(8.dp))
            SongInfoCard(appColors = appColors) {
                SongInfoRow(stringResource(R.string.song_info_label_title), info?.title?.ifBlank { track.title } ?: track.title, appColors)
                SongInfoDivider(appColors)
                SongInfoRow(stringResource(R.string.song_info_label_artist), info?.artist?.ifBlank { track.artist } ?: track.artist, appColors)
                SongInfoDivider(appColors)
                SongInfoRow(
                    stringResource(R.string.song_info_label_album),
                    (info?.album?.ifBlank { track.album } ?: track.album).ifBlank { "—" },
                    appColors
                )
                SongInfoDivider(appColors)
                SongInfoRow(
                    stringResource(R.string.song_info_label_duration),
                    formatDurationMs(info?.durationMs?.takeIf { it > 0L } ?: track.durationMs),
                    appColors
                )
                if (!info?.releaseYear.isNullOrBlank()) {
                    SongInfoDivider(appColors)
                    SongInfoRow(stringResource(R.string.song_info_label_year), info!!.releaseYear, appColors)
                }
                if (!info?.genre.isNullOrBlank()) {
                    SongInfoDivider(appColors)
                    SongInfoRow(stringResource(R.string.song_info_label_genre), info!!.genre, appColors)
                }
                if (!info?.isrc.isNullOrBlank()) {
                    SongInfoDivider(appColors)
                    SongInfoRow("ISRC", info!!.isrc, appColors)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 2: Audio Stream & Source Telemetry
            SongInfoSectionHeader(
                icon = Icons.Default.HighQuality,
                title = stringResource(R.string.song_info_section_audio_quality),
                accentColor = accentColor,
                appColors = appColors
            )
            Spacer(modifier = Modifier.height(8.dp))
            SongInfoCard(appColors = appColors) {
                if (effectiveOutputDevice.isNotBlank()) {
                    SongInfoRow(stringResource(R.string.song_info_label_output), effectiveOutputDevice, appColors)
                    SongInfoDivider(appColors)
                }
                if (info != null) {
                    SongInfoRow(
                        stringResource(R.string.song_info_label_source),
                        info.providerLabel.ifBlank { info.sourceType.displayName },
                        appColors
                    )
                    if (info.requestedQualityLabel.isNotBlank()) {
                        SongInfoDivider(appColors)
                        SongInfoRow(
                            stringResource(R.string.song_info_label_requested_quality),
                            info.requestedQualityLabel,
                            appColors
                        )
                    }
                    SongInfoDivider(appColors)
                    SongInfoRow(
                        stringResource(R.string.song_info_label_codec),
                        info.codec.ifBlank { "—" },
                        appColors
                    )
                    SongInfoDivider(appColors)
                    SongInfoRow(
                        stringResource(R.string.song_info_label_bitrate),
                        if (info.bitrateKbps > 0) "${info.bitrateKbps} kbps" else "—",
                        appColors
                    )
                    if (info.sampleRateHz > 0) {
                        SongInfoDivider(appColors)
                        val khz = if (info.sampleRateHz % 1000 == 0) {
                            "${info.sampleRateHz / 1000} kHz"
                        } else {
                            String.format(java.util.Locale.ROOT, "%.1f kHz", info.sampleRateHz / 1000.0)
                        }
                        SongInfoRow(stringResource(R.string.song_info_label_sample_rate), khz, appColors)
                    }
                    if (info.isLossless && info.bitDepth > 0) {
                        SongInfoDivider(appColors)
                        SongInfoRow(stringResource(R.string.song_info_label_bit_depth), "${info.bitDepth}-bit", appColors)
                    }
                    if (info.channelCount > 0) {
                        SongInfoDivider(appColors)
                        val chLabel = when (info.channelCount) {
                            1 -> "Mono (1ch)"
                            2 -> "Stereo (2ch)"
                            else -> "${info.channelCount} channels"
                        }
                        SongInfoRow(stringResource(R.string.song_info_label_channels), chLabel, appColors)
                    }
                    if (info.fileSizeBytes > 0L) {
                        SongInfoDivider(appColors)
                        SongInfoRow(
                            stringResource(R.string.song_info_label_size),
                            DataUsageTracker.formatBytes(info.fileSizeBytes),
                            appColors
                        )
                    }
                    if (info.networkType != null) {
                        SongInfoDivider(appColors)
                        val netLabel = when (info.networkType) {
                            NetworkConnectionType.WIFI -> stringResource(R.string.data_usage_wifi)
                            NetworkConnectionType.MOBILE -> stringResource(R.string.data_usage_mobile)
                            NetworkConnectionType.OTHER -> "Other"
                        }
                        SongInfoRow(
                            stringResource(R.string.song_info_label_network),
                            if (info.servedFromCache) "$netLabel (Cached)" else netLabel,
                            appColors
                        )
                    }
                    if (info.filePathOrUri.isNotBlank()) {
                        SongInfoDivider(appColors)
                        SongInfoRow(stringResource(R.string.song_info_label_location), info.filePathOrUri, appColors)
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = appColors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.song_info_not_currently_playing),
                            fontFamily = PlusJakartaSans,
                            fontSize = 12.5.sp,
                            color = appColors.textSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SongInfoSectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    accentColor: Color,
    appColors: AppThemeColors
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(17.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            fontFamily = GoogleSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = appColors.textPrimary
        )
    }
}

@Composable
private fun SongInfoCard(
    appColors: AppThemeColors,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(appColors.surfaceVariant)
            .border(1.dp, appColors.cardBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        content()
    }
}

@Composable
private fun SongInfoRow(label: String, value: String, appColors: AppThemeColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontFamily = PlusJakartaSans,
            fontSize = 12.5.sp,
            color = appColors.textSecondary,
            modifier = Modifier.width(116.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.Medium,
            fontSize = 12.5.sp,
            color = appColors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SongInfoDivider(appColors: AppThemeColors) {
    HorizontalDivider(color = appColors.cardBorder.copy(alpha = 0.5f), thickness = 0.6.dp)
}

private fun formatDurationMs(durationMs: Long): String {
    if (durationMs <= 0L) return "0:00"
    val totalSec = durationMs / 1000L
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return "%d:%02d".format(min, sec)
}
