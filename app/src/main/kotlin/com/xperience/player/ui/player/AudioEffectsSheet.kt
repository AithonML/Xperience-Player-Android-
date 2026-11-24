package com.xperience.player.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xperience.player.service.dsp.AudioDspController
import com.xperience.player.service.dsp.AudioDspState
import com.xperience.player.service.dsp.EqPreset
import com.xperience.player.service.dsp.SleepTimerManager
import com.xperience.player.service.dsp.SleepTimerPreset
import com.xperience.player.service.dsp.SleepTimerState
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.theme.AppThemeColors
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.PlusJakartaSans
import kotlin.math.abs

enum class AudioEffectsSectionTab(val title: String) {
    ALL("All"),
    EQUALIZER("Equalizer"),
    SPEED_PITCH("Speed & Pitch"),
    SLEEP_TIMER("Sleep Timer")
}

private val SectionCardShape = ContinuousRoundedRectangle(size = 20.dp)
private val ChipCapsuleShape = ContinuousRoundedRectangle(percent = 50)

private val EqBandLabels = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")
private val SpeedQuickPresets = listOf(0.75f, 1.0f, 1.15f, 1.22f, 1.50f, 2.0f)
private val PitchQuickPresets = listOf(-4f, -2f, 0f, 2f, 3.5f, 6f)

/**
 * Modular Audio DSP, Equalizer, Playback Speed, Pitch/Tone Control, 1-Click Nightcore Mode,
 * and Visual Music Sleep Timer sheet content for Android Now Playing and Tablet Fullscreen menus.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AudioEffectsSheetContent(
    dspState: AudioDspState,
    sleepTimerState: SleepTimerState,
    dspController: AudioDspController,
    sleepTimerManager: SleepTimerManager,
    modifier: Modifier = Modifier,
    initialTab: AudioEffectsSectionTab = AudioEffectsSectionTab.ALL,
    accentColor: Color = LocalAccentColor.current,
    appColors: AppThemeColors = LocalAppColors.current
) {
    var selectedTab by remember(initialTab) { mutableStateOf(initialTab) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Header Row: Title + Reset DSP button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Audio Effects",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    color = appColors.textPrimary
                )
                Text(
                    text = "Equalizer, 1-Click Nightcore, Speed, Pitch & Sleep Timer",
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = appColors.textSecondary
                )
            }

            if (dspState.isAnyEffectActive) {
                Box(
                    modifier = Modifier
                        .clip(ChipCapsuleShape)
                        .background(accentColor.copy(alpha = 0.16f))
                        .border(1.dp, accentColor.copy(alpha = 0.65f), ChipCapsuleShape)
                        .clickable { dspController.resetAllEffects() }
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset DSP",
                            tint = accentColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Reset",
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            color = accentColor
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Filter tab pills (Todo | Ecualizador | Velocidad y Tono | Temporizador)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AudioEffectsSectionTab.entries.forEach { tab ->
                val isSelected = selectedTab == tab
                Box(
                    modifier = Modifier
                        .clip(ChipCapsuleShape)
                        .background(
                            if (isSelected) {
                                accentColor
                            } else if (appColors.isDark) {
                                Color(0xFF1E222D)
                            } else {
                                Color(0xFFEFF3F8)
                            }
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) {
                                accentColor
                            } else if (appColors.isDark) {
                                Color(0xFF2D3343)
                            } else {
                                Color(0xFFDCE2EC)
                            },
                            shape = ChipCapsuleShape
                        )
                        .clickable { selectedTab = tab }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tab.title,
                        fontFamily = GoogleSans,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                        color = if (isSelected) Color.White else appColors.textPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // =========================================================================
        // 1. 1-CLICK NIGHTCORE REAL-TIME DSP CARD (Always visible at top or in Speed/EQ)
        // =========================================================================
        NightcoreMenuCard(
            isNightcoreEnabled = dspState.isNightcoreEnabled,
            nightcoreSpeed = dspState.nightcoreSpeed,
            onToggleNightcore = { dspController.toggleNightcore() },
            appColors = appColors,
            accentColor = accentColor
        )

        AnimatedVisibility(visible = dspState.isNightcoreEnabled) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(SectionCardShape)
                    .background(if (appColors.isDark) Color(0xFF181C26) else Color(0xFFF3F6FB))
                    .border(
                        width = 1.dp,
                        color = Color(0xFF00F0FF).copy(alpha = 0.45f),
                        shape = SectionCardShape
                    )
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Real-Time Nightcore DSP Chain (Series)",
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        color = if (appColors.isDark) Color(0xFF00F0FF) else Color(0xFF006A75)
                    )
                    Text(
                        text = "%.2fx (+%.1f st)".format(dspState.effectiveSpeed, dspState.effectiveSemitones),
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.5.sp,
                        color = Color(0xFFFF2E93)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Fine Nightcore resampling rate adjustment within the 1.20x - 1.25x spec window
                Slider(
                    value = dspState.nightcoreSpeed,
                    onValueChange = { dspController.setNightcoreSpeed(it) },
                    valueRange = 1.20f..1.25f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFF2E93),
                        activeTrackColor = Color(0xFF00F0FF)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "1. Low-Shelf 90Hz (+6.0 dB, Q=0.7)  •  2. Peaking 3.5kHz (+2.0 dB)  •  3. Limiter -6dB (4:1, 3ms/150ms)  •  150ms Ramp",
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = appColors.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // =========================================================================
        // 2. MUSIC SLEEP TIMER (Temporizador de música)
        // =========================================================================
        if (selectedTab == AudioEffectsSectionTab.ALL || selectedTab == AudioEffectsSectionTab.SLEEP_TIMER) {
            SleepTimerControlSection(
                sleepTimerState = sleepTimerState,
                sleepTimerManager = sleepTimerManager,
                accentColor = accentColor,
                appColors = appColors
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        // =========================================================================
        // 3. PLAYBACK SPEED & PITCH / TONE CONTROL (Velocidad y Tono)
        // =========================================================================
        if (selectedTab == AudioEffectsSectionTab.ALL || selectedTab == AudioEffectsSectionTab.SPEED_PITCH) {
            SpeedAndPitchControlSection(
                dspState = dspState,
                dspController = dspController,
                accentColor = accentColor,
                appColors = appColors
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        // =========================================================================
        // 4. MULTI-BAND EQUALIZER & BASS/TREBLE TONE STACK (Ecualizador)
        // =========================================================================
        if (selectedTab == AudioEffectsSectionTab.ALL || selectedTab == AudioEffectsSectionTab.EQUALIZER) {
            EqualizerControlSection(
                dspState = dspState,
                dspController = dspController,
                accentColor = accentColor,
                appColors = appColors
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SleepTimerControlSection(
    sleepTimerState: SleepTimerState,
    sleepTimerManager: SleepTimerManager,
    accentColor: Color,
    appColors: AppThemeColors
) {
    val cardBg = if (appColors.isDark) Color(0xFF1A1E29) else Color(0xFFF2F5FA)
    val cardBorder = if (sleepTimerState.isActive) {
        Color(0xFFFFB74D).copy(alpha = 0.75f)
    } else if (appColors.isDark) {
        Color(0xFF2B3142)
    } else {
        Color(0xFFDCE2EC)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SectionCardShape)
            .background(cardBg)
            .border(1.dp, cardBorder, SectionCardShape)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Bedtime,
                    contentDescription = null,
                    tint = if (sleepTimerState.isActive) Color(0xFFFFB74D) else accentColor,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = "Sleep Timer",
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = appColors.textPrimary
                    )
                    Text(
                        text = if (sleepTimerState.isActive) {
                            "Active on screen • Smooth fade-out at end"
                        } else {
                            "Automatically pauses playback"
                        },
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.5.sp,
                        color = appColors.textSecondary
                    )
                }
            }

            if (sleepTimerState.isActive) {
                Box(
                    modifier = Modifier
                        .clip(ChipCapsuleShape)
                        .background(Color(0xFFFFB74D).copy(alpha = 0.18f))
                        .border(1.dp, Color(0xFFFFB74D), ChipCapsuleShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = sleepTimerState.formattedRemaining,
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.5.sp,
                        color = if (appColors.isDark) Color(0xFFFFB74D) else Color(0xFFB45309)
                    )
                }
            }
        }

        AnimatedVisibility(visible = sleepTimerState.isActive) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                LinearProgressIndicator(
                    progress = { sleepTimerState.progressFraction },
                    color = Color(0xFFFFB74D),
                    trackColor = if (appColors.isDark) Color(0xFF2B3142) else Color(0xFFDCE2EC),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(ChipCapsuleShape)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(ChipCapsuleShape)
                            .background(accentColor.copy(alpha = 0.16f))
                            .border(1.dp, accentColor.copy(alpha = 0.65f), ChipCapsuleShape)
                            .clickable { sleepTimerManager.addMinutes(5) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+5 min",
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = accentColor
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(ChipCapsuleShape)
                            .background(Color(0xFFEF4444).copy(alpha = 0.16f))
                            .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.70f), ChipCapsuleShape)
                            .clickable { sleepTimerManager.cancelTimer() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Off",
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = Color(0xFFEF4444)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SleepTimerPreset.entries.forEach { preset ->
                val isSelected = sleepTimerState.isActive && sleepTimerState.activePreset == preset
                Box(
                    modifier = Modifier
                        .clip(ChipCapsuleShape)
                        .background(
                            if (isSelected) {
                                Color(0xFFFFB74D).copy(alpha = if (appColors.isDark) 0.25f else 0.20f)
                            } else if (appColors.isDark) {
                                Color(0xFF232836)
                            } else {
                                Color.White
                            }
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) {
                                Color(0xFFFFB74D)
                            } else if (appColors.isDark) {
                                Color(0xFF32394B)
                            } else {
                                Color(0xFFD5DCE6)
                            },
                            shape = ChipCapsuleShape
                        )
                        .clickable {
                            if (isSelected) {
                                sleepTimerManager.cancelTimer()
                            } else {
                                sleepTimerManager.startPreset(preset)
                            }
                        }
                        .padding(horizontal = 13.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = preset.label,
                        fontFamily = GoogleSans,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp,
                        color = if (isSelected) {
                            if (appColors.isDark) Color(0xFFFFB74D) else Color(0xFFB45309)
                        } else {
                            appColors.textPrimary
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeedAndPitchControlSection(
    dspState: AudioDspState,
    dspController: AudioDspController,
    accentColor: Color,
    appColors: AppThemeColors
) {
    val cardBg = if (appColors.isDark) Color(0xFF1A1E29) else Color(0xFFF2F5FA)
    val cardBorder = if (appColors.isDark) Color(0xFF2B3142) else Color(0xFFDCE2EC)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SectionCardShape)
            .background(cardBg)
            .border(1.dp, cardBorder, SectionCardShape)
            .padding(16.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Speed & Pitch",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = appColors.textPrimary
                )
            }

            if (abs(dspState.effectiveSpeed - 1.0f) > 0.01f || abs(dspState.effectivePitch - 1.0f) > 0.01f) {
                Text(
                    text = "Reset",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = accentColor,
                    modifier = Modifier
                        .clip(ChipCapsuleShape)
                        .clickable {
                            dspController.setNightcoreEnabled(false)
                            dspController.setPlaybackSpeed(1.0f)
                            dspController.setPitchSemitones(0.0f)
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 1. Playback Speed Slider (0.5x to 2.0x)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Speed",
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = appColors.textPrimary
            )
            Text(
                text = "%.2fx".format(dspState.effectiveSpeed),
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
                color = accentColor
            )
        }

        Slider(
            value = dspState.effectiveSpeed,
            onValueChange = { dspController.setPlaybackSpeed(it) },
            valueRange = 0.50f..2.00f,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Speed preset chips
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SpeedQuickPresets.forEach { presetSpeed ->
                val isSelected = abs(dspState.effectiveSpeed - presetSpeed) < 0.015f
                Box(
                    modifier = Modifier
                        .clip(ChipCapsuleShape)
                        .background(
                            if (isSelected) accentColor.copy(alpha = 0.20f)
                            else if (appColors.isDark) Color(0xFF232836)
                            else Color.White
                        )
                        .border(
                            1.dp,
                            if (isSelected) accentColor
                            else if (appColors.isDark) Color(0xFF32394B)
                            else Color(0xFFD5DCE6),
                            ChipCapsuleShape
                        )
                        .clickable { dspController.setPlaybackSpeed(presetSpeed) }
                        .padding(horizontal = 11.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "%.2fx".format(presetSpeed).removeSuffix("0x").let {
                            if (it.endsWith(".x")) "${presetSpeed.toInt()}.0x" else if (!it.endsWith("x")) "${it}x" else it
                        },
                        fontFamily = GoogleSans,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.5.sp,
                        color = if (isSelected) accentColor else appColors.textPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Pitch / Tone Control (-6 to +6 semitones)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Pitch (Low ↔ High)",
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = appColors.textPrimary
            )
            val semitones = dspState.effectiveSemitones
            val signPrefix = if (semitones > 0.05f) "+" else ""
            Text(
                text = "$signPrefix%.1f st".format(semitones),
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
                color = accentColor
            )
        }

        Slider(
            value = dspState.effectiveSemitones.coerceIn(-6f, 6f),
            onValueChange = { dspController.setPitchSemitones(it) },
            valueRange = -6.0f..6.0f,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor
            ),
            modifier = Modifier.fillMaxWidth()
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PitchQuickPresets.forEach { presetSt ->
                val isSelected = abs(dspState.effectiveSemitones - presetSt) < 0.2f
                val label = when {
                    presetSt > 0f -> "+${presetSt} st"
                    presetSt < 0f -> "${presetSt} st"
                    else -> "0 st (Original)"
                }
                Box(
                    modifier = Modifier
                        .clip(ChipCapsuleShape)
                        .background(
                            if (isSelected) accentColor.copy(alpha = 0.20f)
                            else if (appColors.isDark) Color(0xFF232836)
                            else Color.White
                        )
                        .border(
                            1.dp,
                            if (isSelected) accentColor
                            else if (appColors.isDark) Color(0xFF32394B)
                            else Color(0xFFD5DCE6),
                            ChipCapsuleShape
                        )
                        .clickable { dspController.setPitchSemitones(presetSt) }
                        .padding(horizontal = 11.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = label,
                        fontFamily = GoogleSans,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.5.sp,
                        color = if (isSelected) accentColor else appColors.textPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Coupled Speed & Pitch (Vinyl / Resampling mode) toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Couple Pitch to Speed (Vinyl Resampling)",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = appColors.textPrimary
                )
                Text(
                    text = "Disables time-stretch so pitch scales naturally with speed",
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = appColors.textSecondary
                )
            }
            Switch(
                checked = dspState.isNightcoreEnabled || dspState.couplePitchToSpeed,
                onCheckedChange = { coupled ->
                    if (dspState.isNightcoreEnabled && !coupled) {
                        dspController.setNightcoreEnabled(false)
                    }
                    dspController.setCouplePitchToSpeed(coupled)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = accentColor
                )
            )
        }
    }
}

@Composable
private fun EqualizerControlSection(
    dspState: AudioDspState,
    dspController: AudioDspController,
    accentColor: Color,
    appColors: AppThemeColors
) {
    val cardBg = if (appColors.isDark) Color(0xFF1A1E29) else Color(0xFFF2F5FA)
    val cardBorder = if (appColors.isDark) Color(0xFF2B3142) else Color(0xFFDCE2EC)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SectionCardShape)
            .background(cardBg)
            .border(1.dp, cardBorder, SectionCardShape)
            .padding(16.dp)
    ) {
        // Header + Master EQ Switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Equalizer,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = "Equalizer & Bass / Treble",
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = appColors.textPrimary
                    )
                    Text(
                        text = "Real-time Biquad filters + Soft Limiter -6 dB",
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.5.sp,
                        color = appColors.textSecondary
                    )
                }
            }

            Switch(
                checked = dspState.isEqualizerEnabled,
                onCheckedChange = { dspController.setEqualizerEnabled(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = accentColor
                )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // EQ Presets Horizontal Scroll
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EqPreset.entries.forEach { preset ->
                val isSelected = dspState.selectedPreset == preset
                Box(
                    modifier = Modifier
                        .clip(ChipCapsuleShape)
                        .background(
                            if (isSelected) accentColor.copy(alpha = 0.22f)
                            else if (appColors.isDark) Color(0xFF232836)
                            else Color.White
                        )
                        .border(
                            1.dp,
                            if (isSelected) accentColor
                            else if (appColors.isDark) Color(0xFF32394B)
                            else Color(0xFFD5DCE6),
                            ChipCapsuleShape
                        )
                        .clickable { dspController.selectPreset(preset) }
                        .padding(horizontal = 13.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = preset.displayName,
                        fontFamily = GoogleSans,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp,
                        color = if (isSelected) accentColor else appColors.textPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Dedicated Bass & Treble Tone Controls
        ToneShelfSliderRow(
            label = "Bass (Sub-Bass 95 Hz)",
            gainDb = dspState.bassBoostDb,
            onGainChange = { dspController.setBassBoostDb(it) },
            accentColor = accentColor,
            appColors = appColors
        )

        Spacer(modifier = Modifier.height(8.dp))

        ToneShelfSliderRow(
            label = "Treble (8.5 kHz)",
            gainDb = dspState.trebleBoostDb,
            onGainChange = { dspController.setTrebleBoostDb(it) },
            accentColor = accentColor,
            appColors = appColors
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Frequency Bands (5-Band Parametric EQ)",
            fontFamily = GoogleSans,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = appColors.textPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        EqBandLabels.forEachIndexed { index, freqLabel ->
            val gainDb = dspState.bandGainsDb.getOrElse(index) { 0f }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = freqLabel,
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = appColors.textSecondary,
                    modifier = Modifier.width(56.dp)
                )

                Slider(
                    value = gainDb,
                    onValueChange = { dspController.setBandGainDb(index, it) },
                    valueRange = -12.0f..12.0f,
                    enabled = dspState.isEqualizerEnabled,
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor
                    ),
                    modifier = Modifier.weight(1f)
                )

                val sign = if (gainDb > 0.05f) "+" else ""
                Text(
                    text = "$sign%.1f dB".format(gainDb),
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (abs(gainDb) > 0.1f) accentColor else appColors.textSecondary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(58.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Dynamics Compressor / Soft Limiter row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Anti-Clipping Dynamics Limiter (-6 dB • 4:1)",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = appColors.textPrimary
                )
                Text(
                    text = "Attack 3ms / Release 150ms • Prevents bass distortion",
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = appColors.textSecondary
                )
            }
            Switch(
                checked = dspState.isNightcoreEnabled || dspState.isCompressorEnabled,
                onCheckedChange = { dspController.setCompressorEnabled(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = accentColor
                )
            )
        }
    }
}

@Composable
private fun ToneShelfSliderRow(
    label: String,
    gainDb: Float,
    onGainChange: (Float) -> Unit,
    accentColor: Color,
    appColors: AppThemeColors
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp,
                color = appColors.textPrimary
            )
            val sign = if (gainDb > 0.05f) "+" else ""
            Text(
                text = "$sign%.1f dB".format(gainDb),
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                color = if (abs(gainDb) > 0.1f) accentColor else appColors.textSecondary
            )
        }
        Slider(
            value = gainDb,
            onValueChange = onGainChange,
            valueRange = -12.0f..12.0f,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Compact horizontal row for Now Playing (Phone + Tablet Fullscreen 2-Pane) and Tablet Sidebar:
 * - 1-Click [NightcorePillButton] ("Nightcore ON" with neon glow)
 * - Live Visual [SleepTimerBadge] (visible whenever the sleep timer is counting down)
 * - Quick [DspQuickAccessPill] (opens Equalizer / Speed / Pitch / Sleep Timer sheet)
 */
@Composable
fun NowPlayingDspQuickBar(
    dspState: AudioDspState,
    sleepTimerState: SleepTimerState,
    onToggleNightcore: () -> Unit,
    onOpenAudioEffects: (AudioEffectsSectionTab) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = LocalAccentColor.current,
    appColors: AppThemeColors = LocalAppColors.current,
    compact: Boolean = false
) {
    val summaryLabel = remember(
        dspState.isNightcoreEnabled,
        dspState.effectiveSpeed,
        dspState.effectiveSemitones,
        dspState.selectedPreset,
        dspState.isAnyEffectActive
    ) {
        when {
            !dspState.isAnyEffectActive -> "EQ & Speed"
            abs(dspState.effectiveSpeed - 1.0f) > 0.01f || abs(dspState.effectiveSemitones) > 0.05f -> {
                val st = dspState.effectiveSemitones
                val sign = if (st > 0.05f) "+" else ""
                "%.2fx • %s%.1f st".format(dspState.effectiveSpeed, sign, st)
            }
            else -> dspState.selectedPreset.displayName
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NightcorePillButton(
            isNightcoreEnabled = dspState.isNightcoreEnabled,
            nightcoreSpeed = dspState.nightcoreSpeed,
            onToggleNightcore = onToggleNightcore,
            accentColor = accentColor,
            appColors = appColors,
            compact = compact
        )

        SleepTimerBadge(
            timerState = sleepTimerState,
            onClick = { onOpenAudioEffects(AudioEffectsSectionTab.SLEEP_TIMER) },
            accentColor = accentColor,
            appColors = appColors,
            compact = compact
        )

        DspQuickAccessPill(
            isAnyEffectActive = dspState.isAnyEffectActive,
            summaryLabel = summaryLabel,
            onClick = { onOpenAudioEffects(AudioEffectsSectionTab.ALL) },
            accentColor = accentColor,
            appColors = appColors,
            compact = compact
        )
    }
}

/**
 * Modular section embedded inside the Now Playing & Tablet Fullscreen Song Options Menu
 * ([SongDetailsSheetContent]), exposing 1-Click Nightcore Mode, Equalizer, Playback Speed,
 * Pitch/Tone control, and Music Sleep Timer.
 */
@Composable
fun SongMenuAudioDspOptionsSection(
    dspState: AudioDspState,
    sleepTimerState: SleepTimerState,
    onToggleNightcore: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenSpeedAndPitch: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    accentColor: Color,
    appColors: AppThemeColors,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. 1-Click Nightcore Mode Card
        NightcoreMenuCard(
            isNightcoreEnabled = dspState.isNightcoreEnabled,
            nightcoreSpeed = dspState.nightcoreSpeed,
            onToggleNightcore = onToggleNightcore,
            appColors = appColors,
            accentColor = accentColor
        )

        // 2. Equalizer & Bass/Treble
        val isEqModified = dspState.isEqualizerEnabled && (
            abs(dspState.bassBoostDb) > 0.1f ||
                abs(dspState.trebleBoostDb) > 0.1f ||
                dspState.bandGainsDb.any { abs(it) > 0.1f }
            )
        SongMenuOptionRowButton(
            icon = Icons.Default.Equalizer,
            title = "Equalizer",
            valueText = if (dspState.isNightcoreEnabled) {
                "Nightcore (+6dB Bass • +2dB Treble)"
            } else if (isEqModified) {
                dspState.selectedPreset.displayName
            } else {
                "Flat"
            },
            isHighlighted = dspState.isNightcoreEnabled || isEqModified,
            accentColor = accentColor,
            appColors = appColors,
            onClick = onOpenEqualizer
        )

        // 3. Playback Speed & Pitch
        val isSpeedOrPitchModified = abs(dspState.effectiveSpeed - 1.0f) > 0.01f || abs(dspState.effectiveSemitones) > 0.05f
        val semitones = dspState.effectiveSemitones
        val sign = if (semitones > 0.05f) "+" else ""
        SongMenuOptionRowButton(
            icon = Icons.Default.Speed,
            title = "Speed & Pitch",
            valueText = "%.2fx • %s%.1f st".format(dspState.effectiveSpeed, sign, semitones),
            isHighlighted = isSpeedOrPitchModified,
            accentColor = accentColor,
            appColors = appColors,
            onClick = onOpenSpeedAndPitch
        )

        // 4. Music Sleep Timer
        SongMenuOptionRowButton(
            icon = Icons.Default.Bedtime,
            title = "Sleep Timer",
            valueText = if (sleepTimerState.isActive) {
                "Active (${sleepTimerState.formattedRemaining})"
            } else {
                "Off"
            },
            isHighlighted = sleepTimerState.isActive,
            highlightColor = Color(0xFFFFB74D),
            accentColor = accentColor,
            appColors = appColors,
            onClick = onOpenSleepTimer
        )
    }
}

@Composable
private fun SongMenuOptionRowButton(
    icon: ImageVector,
    title: String,
    valueText: String,
    isHighlighted: Boolean,
    accentColor: Color,
    appColors: AppThemeColors,
    onClick: () -> Unit,
    highlightColor: Color = accentColor
) {
    val rowShape = ContinuousRoundedRectangle(percent = 32)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(rowShape)
            .background(
                if (isHighlighted) {
                    highlightColor.copy(alpha = if (appColors.isDark) 0.18f else 0.12f)
                } else if (appColors.isDark) {
                    Color(0xFF1E222D)
                } else {
                    Color(0xFFEFF3F8)
                }
            )
            .border(
                1.dp,
                if (isHighlighted) {
                    highlightColor.copy(alpha = 0.75f)
                } else if (appColors.isDark) {
                    Color(0xFF2C3240)
                } else {
                    Color(0xFFDCE2EC)
                },
                rowShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
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
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isHighlighted) highlightColor else appColors.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = if (isHighlighted) highlightColor else appColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = valueText,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = if (isHighlighted) highlightColor else appColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

