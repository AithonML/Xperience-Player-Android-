package com.xperience.player.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xperience.player.ui.component.GlassComponent
import com.xperience.player.ui.component.LocalGlassEffectConfig
import com.xperience.player.ui.component.isGlassAllowed
import com.xperience.player.ui.component.liquidGlass
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.theme.AppThemeColors
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.PlusJakartaSans
import com.xperience.player.ui.utils.pressWobble

private val NightcorePillShape = ContinuousRoundedRectangle(percent = 50)
private val NightcoreCardShape = ContinuousRoundedRectangle(percent = 32)

private val NeonNightcoreMagenta = Color(0xFFFF2E93)
private val NeonNightcoreCyan = Color(0xFF00F0FF)
private val NeonNightcoreViolet = Color(0xFFA855F7)

/**
 * 1-Click "Nightcore" Real-Time DSP Toggle Pill Button.
 *
 * Features tactile micro-interactions (spring scale bounce, haptic pulse),
 * an animated neon gradient border and pulsing outer glow when active,
 * and a clear `"Nightcore ON"` visual state.
 */
@Composable
fun NightcorePillButton(
    isNightcoreEnabled: Boolean,
    onToggleNightcore: () -> Unit,
    modifier: Modifier = Modifier,
    nightcoreSpeed: Float = 1.22f,
    accentColor: Color = LocalAccentColor.current,
    appColors: AppThemeColors = LocalAppColors.current,
    compact: Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val glassConfig = LocalGlassEffectConfig.current
    val useGlass = glassConfig.isEnabledFor(GlassComponent.PLAYER) && isGlassAllowed()

    val infiniteTransition = rememberInfiniteTransition(label = "nightcorePulse")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nightcoreGlowPulse"
    )

    val activePopScale by animateFloatAsState(
        targetValue = if (isNightcoreEnabled) 1.03f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "nightcoreActiveScale"
    )

    val iconTint by animateColorAsState(
        targetValue = if (isNightcoreEnabled) NeonNightcoreCyan else appColors.glassTextPrimary.copy(alpha = 0.82f),
        animationSpec = tween(220),
        label = "nightcoreIconTint"
    )

    val neonBorderBrush = remember(accentColor, isNightcoreEnabled) {
        if (isNightcoreEnabled) {
            Brush.linearGradient(
                colors = listOf(
                    NeonNightcoreCyan,
                    NeonNightcoreMagenta,
                    NeonNightcoreViolet,
                    accentColor
                )
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.28f),
                    Color.White.copy(alpha = 0.12f)
                )
            )
        }
    }

    val backgroundModifier = when {
        isNightcoreEnabled -> {
            Modifier.background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        NeonNightcoreMagenta.copy(alpha = if (appColors.isDark) 0.34f else 0.22f),
                        NeonNightcoreViolet.copy(alpha = if (appColors.isDark) 0.38f else 0.24f),
                        NeonNightcoreCyan.copy(alpha = if (appColors.isDark) 0.28f else 0.18f)
                    )
                )
            )
        }
        useGlass -> {
            Modifier.liquidGlass(
                config = glassConfig,
                shape = NightcorePillShape,
                highlightAlpha = 0.35f
            )
        }
        else -> {
            Modifier.background(
                if (appColors.isDark) Color(0x66181B24) else Color(0xCCFFFFFF)
            )
        }
    }

    val pillHeight = if (compact) 32.dp else 36.dp
    val horizontalPad = if (compact) 11.dp else 14.dp

    Box(
        modifier = modifier
            .pressWobble(interactionSource, pressedScale = 0.92f)
            .graphicsLayer {
                val pulseScale = if (isNightcoreEnabled) 1f + (pulseProgress * 0.022f) else 1f
                scaleX = activePopScale * pulseScale
                scaleY = activePopScale * pulseScale
            }
            .height(pillHeight)
            .shadow(
                elevation = if (isNightcoreEnabled) (10 + pulseProgress * 6).dp else 4.dp,
                shape = NightcorePillShape,
                ambientColor = if (isNightcoreEnabled) NeonNightcoreMagenta.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.2f),
                spotColor = if (isNightcoreEnabled) NeonNightcoreCyan.copy(alpha = 0.70f) else Color.Black.copy(alpha = 0.25f)
            )
            .drawBehind {
                if (isNightcoreEnabled) {
                    val glowAlpha = 0.24f + (pulseProgress * 0.26f)
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            listOf(
                                NeonNightcoreCyan.copy(alpha = glowAlpha),
                                NeonNightcoreMagenta.copy(alpha = glowAlpha)
                            )
                        ),
                        cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                        style = Stroke(width = (2.5.dp + (pulseProgress * 2f).dp).toPx())
                    )
                }
            }
            .clip(NightcorePillShape)
            .then(backgroundModifier)
            .border(
                width = if (isNightcoreEnabled) 1.5.dp else 1.dp,
                brush = neonBorderBrush,
                shape = NightcorePillShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Switch,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleNightcore()
                }
            )
            .padding(horizontal = horizontalPad),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = if (isNightcoreEnabled) "Nightcore ON" else "Enable Nightcore",
                tint = iconTint,
                modifier = Modifier
                    .size(if (compact) 15.dp else 17.dp)
                    .graphicsLayer {
                        if (isNightcoreEnabled) {
                            val iconPulse = 1f + pulseProgress * 0.14f
                            scaleX = iconPulse
                            scaleY = iconPulse
                        }
                    }
            )

            AnimatedContent(
                targetState = isNightcoreEnabled,
                transitionSpec = {
                    fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                },
                label = "nightcorePillLabel"
            ) { active ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = if (active) "Nightcore ON" else "Nightcore",
                        fontFamily = GoogleSans,
                        fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                        fontSize = if (compact) 11.5.sp else 12.5.sp,
                        color = if (active) {
                            if (appColors.isDark) Color.White else Color(0xFF1A0826)
                        } else {
                            appColors.glassTextPrimary.copy(alpha = 0.90f)
                        },
                        maxLines = 1
                    )

                    if (active && !compact) {
                        Box(
                            modifier = Modifier
                                .clip(NightcorePillShape)
                                .background(NeonNightcoreCyan.copy(alpha = if (appColors.isDark) 0.22f else 0.18f))
                                .padding(horizontal = 6.dp, vertical = 1.5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "%.2fx".format(nightcoreSpeed),
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = if (appColors.isDark) NeonNightcoreCyan else Color(0xFF006A75),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Full-width 1-Click Nightcore Mode card used inside the Now Playing / Tablet Fullscreen
 * options menu and the Audio Effects sheet.
 */
@Composable
fun NightcoreMenuCard(
    isNightcoreEnabled: Boolean,
    nightcoreSpeed: Float,
    onToggleNightcore: () -> Unit,
    appColors: AppThemeColors,
    modifier: Modifier = Modifier,
    accentColor: Color = LocalAccentColor.current
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    val infiniteTransition = rememberInfiniteTransition(label = "nightcoreCardPulse")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nightcoreCardGlow"
    )

    val cardBackground = if (isNightcoreEnabled) {
        Brush.horizontalGradient(
            colors = listOf(
                NeonNightcoreMagenta.copy(alpha = if (appColors.isDark) 0.25f else 0.14f),
                NeonNightcoreViolet.copy(alpha = if (appColors.isDark) 0.22f else 0.12f),
                NeonNightcoreCyan.copy(alpha = if (appColors.isDark) 0.20f else 0.12f)
            )
        )
    } else {
        Brush.horizontalGradient(
            colors = if (appColors.isDark) {
                listOf(Color(0xFF1E222D), Color(0xFF1E222D))
            } else {
                listOf(Color(0xFFEFF3F8), Color(0xFFEFF3F8))
            }
        )
    }

    val borderBrush = if (isNightcoreEnabled) {
        Brush.horizontalGradient(
            colors = listOf(
                NeonNightcoreCyan.copy(alpha = glowPulse),
                NeonNightcoreMagenta.copy(alpha = glowPulse),
                accentColor.copy(alpha = glowPulse)
            )
        )
    } else {
        Brush.horizontalGradient(
            colors = if (appColors.isDark) {
                listOf(Color(0xFF2C3240), Color(0xFF2C3240))
            } else {
                listOf(Color(0xFFDCE2EC), Color(0xFFDCE2EC))
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pressWobble(interactionSource, pressedScale = 0.97f)
            .clip(NightcoreCardShape)
            .background(brush = cardBackground)
            .border(
                width = if (isNightcoreEnabled) 1.5.dp else 1.dp,
                brush = borderBrush,
                shape = NightcoreCardShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleNightcore()
                }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
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
                        .size(38.dp)
                        .clip(NightcorePillShape)
                        .background(
                            if (isNightcoreEnabled) {
                                Brush.linearGradient(listOf(NeonNightcoreMagenta, NeonNightcoreCyan))
                            } else {
                                Brush.linearGradient(
                                    listOf(
                                        accentColor.copy(alpha = 0.18f),
                                        accentColor.copy(alpha = 0.10f)
                                    )
                                )
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = if (isNightcoreEnabled) Color.White else accentColor,
                        modifier = Modifier.size(21.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isNightcoreEnabled) "Nightcore ON" else "Nightcore Mode (1-Click)",
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (isNightcoreEnabled) {
                            if (appColors.isDark) NeonNightcoreCyan else Color(0xFF9D174D)
                        } else {
                            appColors.textPrimary
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isNightcoreEnabled) {
                            "%.2fx Speed & Pitch (+3.4 st) • Sub-Bass +6dB • Presence +2dB".format(nightcoreSpeed)
                        } else {
                            "Coupled 1.22x resampling + 90Hz bass + 3.5kHz presence"
                        },
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.5.sp,
                        color = appColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .clip(NightcorePillShape)
                    .background(
                        if (isNightcoreEnabled) {
                            NeonNightcoreMagenta
                        } else if (appColors.isDark) {
                            Color(0xFF2B303D)
                        } else {
                            Color(0xFFDCE2EC)
                        }
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isNightcoreEnabled) "ON" else "OFF",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    color = if (isNightcoreEnabled) Color.White else appColors.textSecondary
                )
            }
        }
    }
}
