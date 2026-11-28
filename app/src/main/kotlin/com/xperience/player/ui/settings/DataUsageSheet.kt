package com.xperience.player.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xperience.player.R
import com.xperience.player.domain.model.DataLimitPeriod
import com.xperience.player.domain.model.DataUsageChartGranularity
import com.xperience.player.domain.model.DataUsageChartPoint
import com.xperience.player.domain.model.DataUsageLimitConfig
import com.xperience.player.domain.model.DataUsageSortOrder
import com.xperience.player.domain.model.DataUsageSummary
import com.xperience.player.domain.model.DataUsageTimeFilter
import com.xperience.player.domain.model.TrackDataUsageEntry
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.service.DataUsageTracker
import com.xperience.player.ui.adaptive.AdaptiveModalContainer
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.theme.AppThemeColors
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.PlusJakartaSans
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class UsageFilterTab { ALL, WIFI, MOBILE }

private val LIMIT_PRESETS_BYTES = listOf(
    100L * 1024L * 1024L to "100 MB",
    250L * 1024L * 1024L to "250 MB",
    500L * 1024L * 1024L to "500 MB",
    1024L * 1024L * 1024L to "1 GB",
    2L * 1024L * 1024L * 1024L to "2 GB",
    5L * 1024L * 1024L * 1024L to "5 GB",
    10L * 1024L * 1024L * 1024L to "10 GB"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataUsageSheet(
    isOpen: Boolean,
    summary: DataUsageSummary,
    onDismiss: () -> Unit,
    onUpdateConfig: (DataUsageLimitConfig) -> Unit,
    onResetPeriod: () -> Unit,
    onClearHistory: () -> Unit,
    onDownloadSuggestedTrack: (TrackItem) -> Unit
) {
    if (!isOpen) return

    val appColors = LocalAppColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    AdaptiveModalContainer(
        isOpen = true,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = appColors.surface,
        dialogMaxWidth = 640.dp
    ) {
        DataUsageContent(
            summary = summary,
            onUpdateConfig = onUpdateConfig,
            onResetPeriod = onResetPeriod,
            onClearHistory = onClearHistory,
            onDownloadSuggestedTrack = onDownloadSuggestedTrack,
            showHeader = true,
            onDismiss = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState())
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataUsageContent(
    summary: DataUsageSummary,
    onUpdateConfig: (DataUsageLimitConfig) -> Unit,
    onResetPeriod: () -> Unit,
    onClearHistory: () -> Unit,
    onDownloadSuggestedTrack: (TrackItem) -> Unit,
    modifier: Modifier = Modifier,
    showHeader: Boolean = false,
    onDismiss: (() -> Unit)? = null
) {
    val appColors = LocalAppColors.current
    val accentColor = LocalAccentColor.current

    var selectedTab by remember { mutableStateOf(UsageFilterTab.ALL) }
    var chartGranularity by remember { mutableStateOf(DataUsageChartGranularity.DAY) }
    var selectedTimeFilter by remember { mutableStateOf(DataUsageTimeFilter.ALL_TIME) }
    var selectedSortOrder by remember { mutableStateOf(DataUsageSortOrder.HIGHEST_USAGE) }
    var customRangeStartMs by remember { mutableStateOf<Long?>(null) }
    var customRangeEndMs by remember { mutableStateOf<Long?>(null) }
    var showDateRangePicker by remember { mutableStateOf(false) }
    var expandedTrackKeys by remember { mutableStateOf(setOf<String>()) }

    val chartPoints = remember(summary, chartGranularity) {
        DataUsageTracker.buildChartPoints(summary, chartGranularity)
    }

    val filteredTracks = remember(
        summary,
        selectedTab,
        selectedTimeFilter,
        customRangeStartMs,
        customRangeEndMs,
        selectedSortOrder
    ) {
        val networkFilter = when (selectedTab) {
            UsageFilterTab.ALL -> "ALL"
            UsageFilterTab.WIFI -> "WIFI"
            UsageFilterTab.MOBILE -> "MOBILE"
        }
        DataUsageTracker.filterAndSortTracks(
            summary = summary,
            timeFilter = selectedTimeFilter,
            customStartUtcMs = customRangeStartMs,
            customEndUtcMs = customRangeEndMs,
            sortOrder = selectedSortOrder,
            networkFilter = networkFilter
        )
    }

    Column(modifier = modifier) {
        if (showHeader) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DataUsage,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.data_usage_title),
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = appColors.textPrimary
                    )
                }

                if (onDismiss != null) {
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
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

            // WiFi & Mobile Summary Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NetworkStatCard(
                    title = stringResource(R.string.data_usage_wifi),
                    icon = Icons.Default.Wifi,
                    periodBytes = summary.periodWifiBytes,
                    totalBytes = summary.totalWifiBytes,
                    limitEnabled = summary.config.wifiLimitEnabled,
                    limitBytes = summary.config.wifiLimitBytes,
                    fraction = summary.wifiUsageFraction,
                    isExceeded = summary.isWifiLimitExceeded,
                    tint = Color(0xFF0A84FF),
                    appColors = appColors,
                    modifier = Modifier.weight(1f)
                )
                NetworkStatCard(
                    title = stringResource(R.string.data_usage_mobile),
                    icon = Icons.Default.CellTower,
                    periodBytes = summary.periodMobileBytes,
                    totalBytes = summary.totalMobileBytes,
                    limitEnabled = summary.config.mobileLimitEnabled,
                    limitBytes = summary.config.mobileLimitBytes,
                    fraction = summary.mobileUsageFraction,
                    isExceeded = summary.isMobileLimitExceeded,
                    tint = Color(0xFFFF9F0A),
                    appColors = appColors,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Consumption Bar Chart Card (Day / Week / Month)
            DataUsageChartCard(
                points = chartPoints,
                granularity = chartGranularity,
                onSelectGranularity = { chartGranularity = it },
                accentColor = accentColor,
                appColors = appColors
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Limits Configuration Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(appColors.surfaceVariant)
                    .border(1.dp, appColors.cardBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.data_usage_limits_section),
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = appColors.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.data_usage_limits_subtitle),
                    fontFamily = PlusJakartaSans,
                    fontSize = 12.sp,
                    color = appColors.textSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Period Pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DataLimitPeriod.entries.forEach { period ->
                        val selected = summary.config.period == period
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) accentColor.copy(alpha = 0.2f) else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (selected) accentColor else appColors.cardBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onUpdateConfig(summary.config.copy(period = period)) }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = period.displayName,
                                fontFamily = GoogleSans,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp,
                                color = if (selected) accentColor else appColors.textPrimary,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Mobile Data Limit Toggle + Presets
                LimitControlBlock(
                    label = stringResource(R.string.data_usage_mobile_limit),
                    enabled = summary.config.mobileLimitEnabled,
                    limitBytes = summary.config.mobileLimitBytes,
                    accentColor = Color(0xFFFF9F0A),
                    appColors = appColors,
                    onToggle = { onUpdateConfig(summary.config.copy(mobileLimitEnabled = it)) },
                    onSelectBytes = { onUpdateConfig(summary.config.copy(mobileLimitBytes = it)) }
                )

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = appColors.cardBorder.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(10.dp))

                // WiFi Limit Toggle + Presets
                LimitControlBlock(
                    label = stringResource(R.string.data_usage_wifi_limit),
                    enabled = summary.config.wifiLimitEnabled,
                    limitBytes = summary.config.wifiLimitBytes,
                    accentColor = Color(0xFF0A84FF),
                    appColors = appColors,
                    onToggle = { onUpdateConfig(summary.config.copy(wifiLimitEnabled = it)) },
                    onSelectBytes = { onUpdateConfig(summary.config.copy(wifiLimitBytes = it)) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Reset Period / Clear History Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (appColors.isDark) Color(0x18FFFFFF) else Color(0x0D000000))
                            .clickable(onClick = onResetPeriod)
                            .padding(vertical = 9.dp, horizontal = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, tint = appColors.textPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.data_usage_reset_period),
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = appColors.textPrimary
                        )
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x18FF453A))
                            .clickable(onClick = onClearHistory)
                            .padding(vertical = 9.dp, horizontal = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFFFF453A), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.data_usage_clear_history),
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = Color(0xFFFF453A)
                        )
                    }
                }
            }

            // Smart Download Suggestions
            if (summary.downloadSuggestions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = stringResource(R.string.data_usage_download_suggestions_title),
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = appColors.textPrimary
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = stringResource(R.string.data_usage_download_suggestions_subtitle),
                    fontFamily = PlusJakartaSans,
                    fontSize = 12.sp,
                    color = appColors.textSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))

                summary.downloadSuggestions.take(6).forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x1634C759))
                            .border(1.dp, Color(0x3834C759), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.title,
                                fontFamily = GoogleSans,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = appColors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${entry.artist} • ${entry.streamPlayCount}x streamed • ${DataUsageTracker.formatBytes(entry.totalNetworkBytes)}",
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
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF34C759))
                                .clickable {
                                    onDownloadSuggestedTrack(
                                        TrackItem(
                                            id = entry.trackId,
                                            title = entry.title,
                                            artist = entry.artist,
                                            album = ""
                                        )
                                    )
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.data_usage_download_cta),
                                    fontFamily = GoogleSans,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    color = Color.Black
                                )
                            }
                        }
                    }
                }
            }

            // Detailed Per-Song Analysis
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.data_usage_analysis_title),
                fontFamily = GoogleSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = appColors.textPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Filter Tabs: All / WiFi / Mobile
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    UsageFilterTab.ALL to stringResource(R.string.data_usage_tab_all),
                    UsageFilterTab.WIFI to stringResource(R.string.data_usage_wifi),
                    UsageFilterTab.MOBILE to stringResource(R.string.data_usage_mobile)
                ).forEach { (tab, label) ->
                    val selected = selectedTab == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(ContinuousRoundedRectangle(percent = 30))
                            .background(if (selected) accentColor.copy(alpha = 0.2f) else appColors.surfaceVariant)
                            .border(1.dp, if (selected) accentColor else appColors.cardBorder, ContinuousRoundedRectangle(percent = 30))
                            .clickable { selectedTab = tab }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontFamily = GoogleSans,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp,
                            color = if (selected) accentColor else appColors.textPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Date Range / Time Period Filter Chips
            val customLabel = remember(customRangeStartMs, customRangeEndMs) {
                if (customRangeStartMs != null && customRangeEndMs != null) {
                    val fmt = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
                    val s = Instant.ofEpochMilli(customRangeStartMs!!).atZone(ZoneOffset.UTC).toLocalDate().format(fmt)
                    val e = Instant.ofEpochMilli(customRangeEndMs!!).atZone(ZoneOffset.UTC).toLocalDate().format(fmt)
                    "$s – $e"
                } else {
                    null
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    DataUsageTimeFilter.ALL_TIME to stringResource(R.string.data_usage_filter_all_time),
                    DataUsageTimeFilter.TODAY to stringResource(R.string.data_usage_filter_today),
                    DataUsageTimeFilter.THIS_WEEK to stringResource(R.string.data_usage_filter_this_week),
                    DataUsageTimeFilter.THIS_MONTH to stringResource(R.string.data_usage_filter_this_month),
                    DataUsageTimeFilter.CUSTOM_RANGE to (customLabel ?: stringResource(R.string.data_usage_filter_custom))
                ).forEach { (filter, label) ->
                    val selected = selectedTimeFilter == filter
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) accentColor.copy(alpha = 0.2f) else appColors.surfaceVariant)
                            .border(
                                1.dp,
                                if (selected) accentColor else appColors.cardBorder,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                if (filter == DataUsageTimeFilter.CUSTOM_RANGE) {
                                    showDateRangePicker = true
                                } else {
                                    selectedTimeFilter = filter
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (filter == DataUsageTimeFilter.CUSTOM_RANGE) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = if (selected) accentColor else appColors.textSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Text(
                            text = label,
                            fontFamily = GoogleSans,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.5.sp,
                            color = if (selected) accentColor else appColors.textPrimary,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sort Order Row: Highest Usage / Recent Date / Alphabetical
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.SwapVert,
                    contentDescription = null,
                    tint = appColors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
                listOf(
                    DataUsageSortOrder.HIGHEST_USAGE to stringResource(R.string.data_usage_sort_highest),
                    DataUsageSortOrder.RECENT_DATE to stringResource(R.string.data_usage_sort_date),
                    DataUsageSortOrder.ALPHABETICAL to stringResource(R.string.data_usage_sort_alpha)
                ).forEach { (order, label) ->
                    val selected = selectedSortOrder == order
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) accentColor.copy(alpha = 0.2f) else Color.Transparent)
                            .border(
                                1.dp,
                                if (selected) accentColor else appColors.cardBorder,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { selectedSortOrder = order }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = label,
                            fontFamily = GoogleSans,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.sp,
                            color = if (selected) accentColor else appColors.textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (filteredTracks.isEmpty()) {
                Text(
                    text = stringResource(R.string.data_usage_empty),
                    fontFamily = PlusJakartaSans,
                    fontSize = 13.sp,
                    color = appColors.textSecondary,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                filteredTracks.take(60).forEach { entry ->
                    val isExpanded = entry.trackKey in expandedTrackKeys
                    TrackUsageAnalysisCard(
                        entry = entry,
                        selectedTab = selectedTab,
                        isExpanded = isExpanded,
                        accentColor = accentColor,
                        appColors = appColors,
                        onToggleExpand = {
                            expandedTrackKeys = if (isExpanded) {
                                expandedTrackKeys - entry.trackKey
                            } else {
                                expandedTrackKeys + entry.trackKey
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

    if (showDateRangePicker) {
        val dateRangeState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = customRangeStartMs,
            initialSelectedEndDateMillis = customRangeEndMs
        )
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val s = dateRangeState.selectedStartDateMillis
                        val e = dateRangeState.selectedEndDateMillis ?: s
                        if (s != null) {
                            customRangeStartMs = s
                            customRangeEndMs = e
                            selectedTimeFilter = DataUsageTimeFilter.CUSTOM_RANGE
                        }
                        showDateRangePicker = false
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangePicker = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            }
        ) {
            DateRangePicker(
                state = dateRangeState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(460.dp)
            )
        }
    }
}

@Composable
private fun NetworkStatCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    periodBytes: Long,
    totalBytes: Long,
    limitEnabled: Boolean,
    limitBytes: Long,
    fraction: Float,
    isExceeded: Boolean,
    tint: Color,
    appColors: AppThemeColors,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(appColors.surfaceVariant)
            .border(
                1.dp,
                if (isExceeded) Color(0xFFFF453A) else appColors.cardBorder,
                RoundedCornerShape(14.dp)
            )
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                fontFamily = GoogleSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = appColors.textPrimary
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = DataUsageTracker.formatBytes(periodBytes),
            fontFamily = GoogleSans,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = if (isExceeded) Color(0xFFFF453A) else appColors.textPrimary
        )
        Text(
            text = if (limitEnabled) {
                "of ${DataUsageTracker.formatBytes(limitBytes)}"
            } else {
                "All-time: ${DataUsageTracker.formatBytes(totalBytes)}"
            },
            fontFamily = PlusJakartaSans,
            fontSize = 11.sp,
            color = appColors.textSecondary
        )
        if (limitEnabled) {
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (isExceeded) Color(0xFFFF453A) else tint,
                trackColor = tint.copy(alpha = 0.18f)
            )
        }
    }
}

@Composable
private fun LimitControlBlock(
    label: String,
    enabled: Boolean,
    limitBytes: Long,
    accentColor: Color,
    appColors: AppThemeColors,
    onToggle: (Boolean) -> Unit,
    onSelectBytes: (Long) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.5.sp,
                    color = appColors.textPrimary
                )
                if (enabled) {
                    Text(
                        text = DataUsageTracker.formatBytes(limitBytes),
                        fontFamily = PlusJakartaSans,
                        fontSize = 11.5.sp,
                        color = accentColor
                    )
                }
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
            )
        }
        AnimatedVisibility(visible = enabled) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LIMIT_PRESETS_BYTES.forEach { (bytes, presetLabel) ->
                    val isSelected = limitBytes == bytes
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) accentColor.copy(alpha = 0.22f) else Color.Transparent)
                            .border(
                                1.dp,
                                if (isSelected) accentColor else appColors.cardBorder,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelectBytes(bytes) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = presetLabel,
                            fontFamily = GoogleSans,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.5.sp,
                            color = if (isSelected) accentColor else appColors.textPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackUsageAnalysisCard(
    entry: TrackDataUsageEntry,
    selectedTab: UsageFilterTab,
    isExpanded: Boolean,
    accentColor: Color,
    appColors: AppThemeColors,
    onToggleExpand: () -> Unit
) {
    val primaryBytes = when (selectedTab) {
        UsageFilterTab.ALL -> entry.totalNetworkBytes
        UsageFilterTab.WIFI -> entry.totalWifiBytes
        UsageFilterTab.MOBILE -> entry.totalMobileBytes
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(appColors.surfaceVariant)
            .border(1.dp, appColors.cardBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onToggleExpand)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    color = appColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val metaParts = buildList {
                    add(entry.artist)
                    if (entry.isLocal) add("Local (External Data)")
                    if (entry.lastQualityLabel.isNotBlank()) add(entry.lastQualityLabel)
                }
                Text(
                    text = metaParts.joinToString(" • "),
                    fontFamily = PlusJakartaSans,
                    fontSize = 11.5.sp,
                    color = appColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = DataUsageTracker.formatBytes(primaryBytes),
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = accentColor
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.data_usage_advanced),
                        fontFamily = PlusJakartaSans,
                        fontSize = 10.5.sp,
                        color = appColors.textSecondary
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = appColors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            ) {
                HorizontalDivider(color = appColors.cardBorder.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))
                val audioBytes = when (selectedTab) {
                    UsageFilterTab.ALL -> entry.totalAudioBytes
                    UsageFilterTab.WIFI -> entry.wifiAudioBytes
                    UsageFilterTab.MOBILE -> entry.mobileAudioBytes
                }
                val artBytes = when (selectedTab) {
                    UsageFilterTab.ALL -> entry.totalArtworkBytes
                    UsageFilterTab.WIFI -> entry.wifiArtworkBytes
                    UsageFilterTab.MOBILE -> entry.mobileArtworkBytes
                }
                val lyricsBytes = when (selectedTab) {
                    UsageFilterTab.ALL -> entry.totalLyricsBytes
                    UsageFilterTab.WIFI -> entry.wifiLyricsBytes
                    UsageFilterTab.MOBILE -> entry.mobileLyricsBytes
                }
                val canvasBytes = when (selectedTab) {
                    UsageFilterTab.ALL -> entry.totalCanvasBytes
                    UsageFilterTab.WIFI -> entry.wifiCanvasBytes
                    UsageFilterTab.MOBILE -> entry.mobileCanvasBytes
                }

                BreakdownRow(stringResource(R.string.data_usage_cat_audio), audioBytes, appColors)
                BreakdownRow(stringResource(R.string.data_usage_cat_artwork), artBytes, appColors)
                BreakdownRow(stringResource(R.string.data_usage_cat_lyrics), lyricsBytes, appColors)
                BreakdownRow(stringResource(R.string.data_usage_cat_canvas), canvasBytes, appColors)
                if (selectedTab == UsageFilterTab.ALL) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "WiFi: ${DataUsageTracker.formatBytes(entry.totalWifiBytes)} • Mobile: ${DataUsageTracker.formatBytes(entry.totalMobileBytes)}",
                        fontFamily = PlusJakartaSans,
                        fontSize = 11.sp,
                        color = appColors.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun BreakdownRow(label: String, bytes: Long, appColors: AppThemeColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontFamily = PlusJakartaSans,
            fontSize = 11.5.sp,
            color = appColors.textSecondary
        )
        Text(
            text = DataUsageTracker.formatBytes(bytes),
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.Medium,
            fontSize = 11.5.sp,
            color = appColors.textPrimary
        )
    }
}

@Composable
private fun DataUsageChartCard(
    points: List<DataUsageChartPoint>,
    granularity: DataUsageChartGranularity,
    onSelectGranularity: (DataUsageChartGranularity) -> Unit,
    accentColor: Color,
    appColors: AppThemeColors
) {
    var selectedIdx by remember(points) {
        mutableStateOf((points.lastIndex).coerceAtLeast(0))
    }
    val selectedPoint = points.getOrNull(selectedIdx)
    val maxBytes = remember(points) {
        points.maxOfOrNull { it.totalBytes }?.coerceAtLeast(1024L) ?: 1024L
    }
    val wifiColor = Color(0xFF0A84FF)
    val mobileColor = Color(0xFFFF9F0A)
    val gridColor = appColors.cardBorder.copy(alpha = 0.55f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(appColors.surfaceVariant)
            .border(1.dp, appColors.cardBorder, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.data_usage_chart_title),
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.5.sp,
                    color = appColors.textPrimary
                )
            }
            if (selectedPoint != null) {
                Text(
                    text = DataUsageTracker.formatBytes(selectedPoint.totalBytes),
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = accentColor
                )
            }
        }

        if (selectedPoint != null) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = buildString {
                    append(selectedPoint.subLabel.ifBlank { selectedPoint.label })
                    append(" • ")
                    append(stringResource(R.string.data_usage_wifi))
                    append(": ")
                    append(DataUsageTracker.formatBytes(selectedPoint.wifiBytes))
                    append(" • ")
                    append(stringResource(R.string.data_usage_mobile))
                    append(": ")
                    append(DataUsageTracker.formatBytes(selectedPoint.mobileBytes))
                },
                fontFamily = PlusJakartaSans,
                fontSize = 11.sp,
                color = appColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Granularity Segmented Selector: Day / Week / Month
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                DataUsageChartGranularity.DAY to stringResource(R.string.data_usage_granularity_day),
                DataUsageChartGranularity.WEEK to stringResource(R.string.data_usage_granularity_week),
                DataUsageChartGranularity.MONTH to stringResource(R.string.data_usage_granularity_month)
            ).forEach { (g, label) ->
                val selected = granularity == g
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) accentColor.copy(alpha = 0.2f) else Color.Transparent)
                        .border(
                            1.dp,
                            if (selected) accentColor else appColors.cardBorder,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelectGranularity(g) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontFamily = GoogleSans,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.5.sp,
                        color = if (selected) accentColor else appColors.textPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (points.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .pointerInput(points) {
                        detectTapGestures { offset ->
                            val step = size.width.toFloat() / points.size.coerceAtLeast(1)
                            val idx = (offset.x / step).toInt().coerceIn(0, points.lastIndex)
                            selectedIdx = idx
                        }
                    }
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp)
                ) {
                    val dashed = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    listOf(0.25f, 0.5f, 0.75f, 1f).forEach { frac ->
                        val y = size.height * (1f - frac)
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1f,
                            pathEffect = dashed
                        )
                    }

                    val slotWidth = size.width / points.size.coerceAtLeast(1)
                    val barWidth = (slotWidth * 0.46f).coerceIn(14f, 44f)
                    val radius = CornerRadius(6f, 6f)

                    points.forEachIndexed { idx, pt ->
                        val centerX = slotWidth * idx + slotWidth / 2f
                        val barLeft = centerX - barWidth / 2f
                        val totalFrac = (pt.totalBytes.toFloat() / maxBytes.toFloat()).coerceIn(0f, 1f)
                        val totalHeight = (totalFrac * (size.height - 8f)).coerceAtLeast(if (pt.totalBytes > 0L) 6f else 2f)
                        val barTop = size.height - totalHeight

                        val alpha = if (idx == selectedIdx) 1.0f else 0.72f
                        if (pt.totalBytes <= 0L) {
                            drawRoundRect(
                                color = gridColor,
                                topLeft = Offset(barLeft, size.height - 3f),
                                size = Size(barWidth, 3f),
                                cornerRadius = radius
                            )
                        } else {
                            val mobileFrac = pt.mobileBytes.toFloat() / pt.totalBytes.toFloat()
                            val mobileHeight = (totalHeight * mobileFrac).coerceAtLeast(if (pt.mobileBytes > 0L) 3f else 0f)
                            val wifiHeight = (totalHeight - mobileHeight).coerceAtLeast(0f)

                            if (wifiHeight > 0f) {
                                drawRoundRect(
                                    color = wifiColor.copy(alpha = alpha),
                                    topLeft = Offset(barLeft, barTop),
                                    size = Size(barWidth, wifiHeight),
                                    cornerRadius = radius
                                )
                            }
                            if (mobileHeight > 0f) {
                                drawRoundRect(
                                    color = mobileColor.copy(alpha = alpha),
                                    topLeft = Offset(barLeft, barTop + wifiHeight),
                                    size = Size(barWidth, mobileHeight),
                                    cornerRadius = radius
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                points.forEachIndexed { idx, pt ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedIdx = idx },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = pt.label,
                            fontFamily = PlusJakartaSans,
                            fontWeight = if (idx == selectedIdx) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 10.sp,
                            color = if (idx == selectedIdx) accentColor else appColors.textSecondary,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(wifiColor))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = stringResource(R.string.data_usage_wifi),
                        fontFamily = PlusJakartaSans,
                        fontSize = 11.sp,
                        color = appColors.textSecondary
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(mobileColor))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = stringResource(R.string.data_usage_mobile),
                        fontFamily = PlusJakartaSans,
                        fontSize = 11.sp,
                        color = appColors.textSecondary
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Max: ${DataUsageTracker.formatBytes(maxBytes)}",
                    fontFamily = PlusJakartaSans,
                    fontSize = 10.5.sp,
                    color = appColors.textSecondary
                )
            }
        }
    }
}
