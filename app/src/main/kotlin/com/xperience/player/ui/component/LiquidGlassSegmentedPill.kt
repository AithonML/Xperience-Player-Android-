package com.xperience.player.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.components.onAccentColor
import com.xperience.player.ui.components.readableAccentColor
import com.xperience.player.ui.theme.AppThemeColors
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.PlusJakartaSans
import com.xperience.player.ui.utils.pressWobble
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val SegmentedPillShape = ContinuousRoundedRectangle(percent = 50)

/**
 * Shared refractive LiquidGlass segmented pill selector with an animated sliding active capsule.
 *
 * Supports both intrinsic-width filter slicers (`equalWidthSegments = false`, used in Library and
 * Liked screens) and full-width equal-segment tab bars (`equalWidthSegments = true`, used in Search).
 */
@Composable
internal fun <T> LiquidGlassSegmentedPill(
    options: List<T>,
    selectedOption: T,
    labelProvider: (T) -> String,
    onSelectOption: (T) -> Unit,
    modifier: Modifier = Modifier,
    appColors: AppThemeColors = LocalAppColors.current,
    accentColor: Color = LocalAccentColor.current,
    equalWidthSegments: Boolean = false,
    segmentHeight: Dp = 34.dp
) {
    val glassConfig = LocalGlassEffectConfig.current
    val useGlass = glassConfig.isEnabledFor(GlassComponent.NAV_BAR) && isGlassAllowed()
    val haptic = LocalHapticFeedback.current
    val displayAccent = readableAccentColor(accentColor, appColors.isDark)

    val outerSurfaceModifier = if (useGlass) {
        Modifier.liquidGlass(
            config = glassConfig,
            shape = SegmentedPillShape,
            highlightAlpha = 0.30f
        )
    } else {
        Modifier
            .clip(SegmentedPillShape)
            .background(
                if (appColors.isDark) Color(0x99181D29) else Color(0xECFFFFFF)
            )
            .border(
                width = 0.9.dp,
                color = if (appColors.isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.09f),
                shape = SegmentedPillShape
            )
    }

    val selectedIndex = options.indexOf(selectedOption).coerceAtLeast(0)
    val itemOffsets = remember(options.size) { mutableStateMapOf<Int, Int>() }
    val itemWidths = remember(options.size) { mutableStateMapOf<Int, Int>() }

    val targetOffset = itemOffsets[selectedIndex] ?: 0
    val targetWidth = itemWidths[selectedIndex] ?: 0

    val capsuleOffsetAnim = remember { Animatable(0f) }
    val capsuleWidthAnim = remember { Animatable(0f) }
    var initialized by remember { mutableStateOf(false) }

    val capsuleSpring = remember {
        spring<Float>(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    }

    LaunchedEffect(targetOffset, targetWidth) {
        if (targetWidth <= 0) return@LaunchedEffect
        if (!initialized) {
            capsuleOffsetAnim.snapTo(targetOffset.toFloat())
            capsuleWidthAnim.snapTo(targetWidth.toFloat())
            initialized = true
        } else {
            launch { capsuleOffsetAnim.animateTo(targetOffset.toFloat(), capsuleSpring) }
            launch { capsuleWidthAnim.animateTo(targetWidth.toFloat(), capsuleSpring) }
        }
    }

    val density = LocalDensity.current

    Box(
        modifier = modifier
            .clip(SegmentedPillShape)
            .then(outerSurfaceModifier)
            .padding(4.dp)
    ) {
        // Animated refractive sliding active-tab capsule
        if (initialized && capsuleWidthAnim.value > 0f) {
            val capsuleWidthDp = with(density) { capsuleWidthAnim.value.toDp() }
            Box(
                modifier = Modifier
                    .offset { IntOffset(x = capsuleOffsetAnim.value.roundToInt(), y = 0) }
                    .width(capsuleWidthDp)
                    .height(segmentHeight)
                    .shadow(
                        elevation = 6.dp,
                        shape = SegmentedPillShape,
                        ambientColor = displayAccent.copy(alpha = 0.35f),
                        spotColor = displayAccent.copy(alpha = 0.45f)
                    )
                    .clip(SegmentedPillShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                displayAccent.copy(alpha = if (appColors.isDark) 0.88f else 0.94f),
                                displayAccent.copy(alpha = if (appColors.isDark) 0.68f else 0.78f)
                            )
                        )
                    )
                    .border(
                        width = 0.9.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.62f),
                                Color.White.copy(alpha = 0.16f)
                            )
                        ),
                        shape = SegmentedPillShape
                    )
            )
        }

        Row(
            modifier = if (equalWidthSegments) Modifier.fillMaxWidth() else Modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selectedIndex
                val interactionSource = remember { MutableInteractionSource() }
                val unselectedTextColor = if (useGlass) {
                    glassConfig.textColor.copy(alpha = 0.80f)
                } else {
                    appColors.textSecondary
                }
                val activeTextColor = onAccentColor(displayAccent)

                Box(
                    modifier = Modifier
                        .then(if (equalWidthSegments) Modifier.weight(1f) else Modifier)
                        .height(segmentHeight)
                        .pressWobble(interactionSource, pressedScale = 0.88f)
                        .onGloballyPositioned { coords ->
                            itemOffsets[index] = coords.positionInParent().x.roundToInt()
                            itemWidths[index] = coords.size.width
                        }
                        .clip(SegmentedPillShape)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectOption(option)
                            }
                        )
                        .padding(horizontal = if (equalWidthSegments) 6.dp else 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = labelProvider(option),
                        color = if (isSelected) activeTextColor else unselectedTextColor,
                        fontFamily = if (isSelected) GoogleSans else PlusJakartaSans,
                        fontSize = if (equalWidthSegments) 13.sp else 12.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
