package com.xperience.player.ui.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xperience.player.domain.model.lyrics.LyricsProviderConfig
import com.xperience.player.domain.model.lyrics.LyricsProviderId
import com.xperience.player.ui.theme.AppThemeColors
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.PlusJakartaSans

@Composable
fun LyricsProviderSettingsSheetContent(
    providers: List<LyricsProviderConfig>,
    accentColor: Color,
    appColors: AppThemeColors,
    onMovePriority: (LyricsProviderId, Int) -> Unit,
    onToggleEnabled: (LyricsProviderId, Boolean) -> Unit,
    onImportLrcClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Lyrics Providers Pipeline",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = appColors.textPrimary
                )
                Text(
                    text = "Reorder priority or import local companion .LRC",
                    fontFamily = PlusJakartaSans,
                    fontSize = 11.5.sp,
                    color = appColors.textSecondary
                )
            }

            ElevatedButton(
                onClick = onImportLrcClick,
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = accentColor,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(imageVector = Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Import .LRC", fontFamily = GoogleSans, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Providers list
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(390.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(providers, key = { _, p -> p.id.name }) { index, provider ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (provider.isEnabled) {
                                if (appColors.isDark) Color(0x18FFFFFF) else Color(0x0C000000)
                            } else {
                                if (appColors.isDark) Color(0x0AFFFFFF) else Color(0x06000000)
                            }
                        )
                        .border(
                            1.dp,
                            if (appColors.isDark) Color(0x20FFFFFF) else Color(0x10000000),
                            RoundedCornerShape(14.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Priority Badge
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (provider.isEnabled) accentColor.copy(alpha = 0.25f) else Color(0x18FFFFFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "#${provider.priorityOrder}",
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (provider.isEnabled) accentColor else appColors.textSecondary
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Title & Capability
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = provider.displayName,
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (provider.isEnabled) appColors.textPrimary else appColors.textSecondary
                        )
                        Text(
                            text = provider.capabilityBadge,
                            fontFamily = PlusJakartaSans,
                            fontSize = 11.sp,
                            color = appColors.textSecondary
                        )
                    }

                    // Up / Down reordering arrows
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onMovePriority(provider.id, -1) },
                            enabled = index > 0,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Move Up",
                                tint = if (index > 0) appColors.textPrimary else appColors.textSecondary.copy(alpha = 0.3f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = { onMovePriority(provider.id, 1) },
                            enabled = index < providers.size - 1,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Move Down",
                                tint = if (index < providers.size - 1) appColors.textPrimary else appColors.textSecondary.copy(alpha = 0.3f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Enable / Disable switch
                    Switch(
                        checked = provider.isEnabled,
                        onCheckedChange = { onToggleEnabled(provider.id, it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = accentColor,
                            uncheckedThumbColor = appColors.textSecondary,
                            uncheckedTrackColor = Color(0x22FFFFFF)
                        ),
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
    }
}
