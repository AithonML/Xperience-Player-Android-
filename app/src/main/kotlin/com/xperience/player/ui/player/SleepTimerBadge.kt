package com.xperience.player.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xperience.player.service.dsp.SleepTimerState
import com.xperience.player.ui.component.GlassComponent
import com.xperience.player.ui.component.LocalGlassEffectConfig
import com.xperience.player.ui.component.isGlassAllowed
import com.xperience.player.ui.component.liquidGlass
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.theme.AppThemeColors
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.utils.pressWobble

private val TimerBadgeShape = ContinuousRoundedRectangle(percent = 50)
private val SleepTimerAmber = Color(0xFFFFB74D)
private val SleepTimerIndigo = Color(0xFF818CF8)

/**
 * Visual Music Sleep Timer countdown badge displayed directly on the Now Playing screen,
 * Tablet Fullscreen player, and Tablet Sidebar whenever the Sleep Timer is active.
 */
@Composable
fun SleepTimerBadge(
    timerState: SleepTimerState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = LocalAccentColor.current,
    appColors: AppThemeColors = LocalAppColors.current,
    compact: Boolean = false
) {
    AnimatedVisibility(
        visible = timerState.isActive,
        enter = fadeIn(tween(200)) + scaleIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            initialScale = 0.85f
        ),
        exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.85f),
        modifier = modifier
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        val glassConfig = LocalGlassEffectConfig.current
        val useGlass = glassConfig.isEnabledFor(GlassComponent.PLAYER) && isGlassAllowed()

        val infiniteTransition = rememberInfiniteTransition(label = "sleepTimerPulse")
        val iconPulse by infiniteTransition.animateFloat(
            initialValue = 0.88f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "sleepTimerIconPulse"
        )

        val backgroundModifier = if (useGlass) {
            Modifier.liquidGlass(
                config = glassConfig,
                shape = TimerBadgeShape,
                highlightAlpha = 0.38f
            )
        } else {
            Modifier.background(
                if (appColors.isDark) Color(0x881A1D28) else Color(0xDDFFFFFF)
            )
        }

        val badgeHeight = if (compact) 32.dp else 36.dp
        val horizontalPad = if (compact) 10.dp else 13.dp

        Box(
            modifier = Modifier
                .pressWobble(interactionSource, pressedScale = 0.93f)
                .height(badgeHeight)
                .shadow(
                    elevation = 6.dp,
                    shape = TimerBadgeShape,
                    ambientColor = SleepTimerIndigo.copy(alpha = 0.35f),
                    spotColor = Color.Black.copy(alpha = 0.3f)
                )
                .clip(TimerBadgeShape)
                .then(backgroundModifier)
                .background(
                    brush = Brush.horizontalGradient(
                        listOf(
                            SleepTimerIndigo.copy(alpha = if (appColors.isDark) 0.24f else 0.15f),
                            accentColor.copy(alpha = if (appColors.isDark) 0.20f else 0.12f)
                        )
                    )
                )
                .border(
                    width = 1.1.dp,
                    brush = Brush.horizontalGradient(
                        listOf(
                            SleepTimerAmber.copy(alpha = 0.80f),
                            SleepTimerIndigo.copy(alpha = 0.80f)
                        )
                    ),
                    shape = TimerBadgeShape
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick
                )
                .padding(horizontal = horizontalPad),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Bedtime,
                    contentDescription = "Active sleep timer",
                    tint = SleepTimerAmber,
                    modifier = Modifier
                        .size(if (compact) 14.dp else 15.5.dp)
                        .graphicsLayer {
                            scaleX = iconPulse
                            scaleY = iconPulse
                        }
                )

                Text(
                    text = timerState.formattedRemaining,
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 11.5.sp else 12.5.sp,
                    color = appColors.glassTextPrimary,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Compact Quick-Access Audio DSP / EQ / Speed / Pitch pill button for the Now Playing
 * and Tablet Fullscreen control bar.
 */
@Composable
fun DspQuickAccessPill(
    isAnyEffectActive: Boolean,
    summaryLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = LocalAccentColor.current,
    appColors: AppThemeColors = LocalAppColors.current,
    compact: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val glassConfig = LocalGlassEffectConfig.current
    val useGlass = glassConfig.isEnabledFor(GlassComponent.PLAYER) && isGlassAllowed()

    val backgroundModifier = if (useGlass) {
        Modifier.liquidGlass(
            config = glassConfig,
            shape = TimerBadgeShape,
            highlightAlpha = 0.35f
        )
    } else {
        Modifier.background(
            if (appColors.isDark) Color(0x66181B24) else Color(0xCCFFFFFF)
        )
    }

    val pillHeight = if (compact) 32.dp else 36.dp
    val horizontalPad = if (compact) 10.dp else 13.dp

    Box(
        modifier = modifier
            .pressWobble(interactionSource, pressedScale = 0.93f)
            .height(pillHeight)
            .clip(TimerBadgeShape)
            .then(backgroundModifier)
            .background(
                if (isAnyEffectActive) {
                    accentColor.copy(alpha = if (appColors.isDark) 0.22f else 0.14f)
                } else {
                    Color.Transparent
                }
            )
            .border(
                width = 1.dp,
                color = if (isAnyEffectActive) {
                    accentColor.copy(alpha = 0.78f)
                } else {
                    Color.White.copy(alpha = if (appColors.isDark) 0.22f else 0.55f)
                },
                shape = TimerBadgeShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = horizontalPad),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Equalizer,
                contentDescription = "Equalizer & DSP",
                tint = if (isAnyEffectActive) accentColor else appColors.glassTextPrimary.copy(alpha = 0.82f),
                modifier = Modifier.size(if (compact) 14.5.dp else 16.dp)
            )
            Text(
                text = summaryLabel,
                fontFamily = GoogleSans,
                fontWeight = if (isAnyEffectActive) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = if (compact) 11.5.sp else 12.sp,
                color = if (isAnyEffectActive) accentColor else appColors.glassTextPrimary.copy(alpha = 0.88f),
                maxLines = 1
            )
        }
    }
}
