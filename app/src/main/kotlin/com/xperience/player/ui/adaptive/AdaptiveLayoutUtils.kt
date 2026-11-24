package com.xperience.player.ui.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.window.core.layout.WindowSizeClass
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.theme.LocalAppColors

/**
 * Current [WindowSizeClass] computed from [currentWindowAdaptiveInfo] at the app root.
 * Defaults to a compact phone size class for isolated preview safety.
 */
val LocalAppWindowSizeClass = staticCompositionLocalOf {
    WindowSizeClass(minWidthDp = 360, minHeightDp = 800)
}

/**
 * Top clearance below the floating desktop top bar in Expanded mode (0.dp in Compact/Medium).
 */
val LocalTopChromePadding = staticCompositionLocalOf { 0.dp }

/**
 * Bottom clearance above the floating bottom bar in Compact/Medium mode (or clean bottom margin in Expanded).
 */
val LocalBottomChromePadding = staticCompositionLocalOf { 180.dp }

val WindowSizeClass.isExpandedWidth: Boolean
    get() = isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

val WindowSizeClass.isMediumOrExpandedWidth: Boolean
    get() = isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

val WindowSizeClass.isCompactWidth: Boolean
    get() = !isMediumOrExpandedWidth

/**
 * Uses the PC-inspired top LiquidGlass bar only on Expanded widths (>= 840dp, e.g. landscape
 * tablets and desktop windows). Compact phones and Medium portrait tablets preserve the vertical
 * bottom floating bar so portrait aspect ratios stay ergonomic and cohesive.
 */
val WindowSizeClass.usesTopDesktopChrome: Boolean
    get() = isExpandedWidth

/**
 * Medium and Expanded screens convert full-width bottom sheets into centered dialogs or integrated
 * side panels so controls remain ergonomic and properly bounded.
 */
val WindowSizeClass.shouldUseModalDialogs: Boolean
    get() = isMediumOrExpandedWidth

/**
 * Multi-pane scaffolds (List-Detail and Supporting-Pane) expand side-by-side on Expanded widths.
 */
val WindowSizeClass.supportsTwoPaneLayout: Boolean
    get() = isExpandedWidth

val WindowSizeClass.adaptiveHorizontalPadding: Dp
    get() = when {
        isExpandedWidth -> 28.dp
        isMediumOrExpandedWidth -> 24.dp
        else -> 20.dp
    }

fun WindowSizeClass.adaptiveGridMinSize(
    compactMinDp: Dp = 158.dp,
    mediumMinDp: Dp = 176.dp,
    expandedMinDp: Dp = 192.dp
): Dp = when {
    isExpandedWidth -> expandedMinDp
    isMediumOrExpandedWidth -> mediumMinDp
    else -> compactMinDp
}

@Composable
fun ProvideAppWindowSizeClass(
    windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfo().windowSizeClass,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalAppWindowSizeClass provides windowSizeClass) {
        content()
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun rememberAdaptivePaneScaffoldDirective(): PaneScaffoldDirective {
    val windowAdaptiveInfo = currentWindowAdaptiveInfo()
    val windowSizeClass = LocalAppWindowSizeClass.current
    val effectiveAdaptiveInfo = WindowAdaptiveInfo(
        windowSizeClass = windowSizeClass,
        windowPosture = windowAdaptiveInfo.windowPosture
    )
    val base = calculatePaneScaffoldDirective(effectiveAdaptiveInfo)
    return if (windowSizeClass.supportsTwoPaneLayout) {
        base.copy(
            maxHorizontalPartitions = 2,
            horizontalPartitionSpacerSize = 20.dp
        )
    } else {
        base.copy(maxHorizontalPartitions = 1)
    }
}

private val AdaptiveDialogShape = ContinuousRoundedRectangle(size = 26.dp)

/**
 * Renders a standard [ModalBottomSheet] on Compact screens and automatically upgrades to a
 * bounded centered [Dialog] on Medium/Expanded screens (tablets & foldables).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveModalContainer(
    isOpen: Boolean,
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    containerColor: Color,
    sheetShape: Shape = ContinuousRoundedRectangle(percent = 14),
    dialogMaxWidth: Dp = 560.dp,
    dialogMaxHeight: Dp = 760.dp,
    showDragHandle: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!isOpen) return

    val windowSizeClass = LocalAppWindowSizeClass.current
    val appColors = LocalAppColors.current

    if (windowSizeClass.shouldUseModalDialogs) {
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            val rimBrush = Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = if (appColors.isDark) 0.24f else 0.72f),
                    Color.White.copy(alpha = if (appColors.isDark) 0.07f else 0.22f)
                )
            )
            Surface(
                shape = AdaptiveDialogShape,
                color = containerColor,
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 24.dp)
                    .widthIn(max = dialogMaxWidth)
                    .heightIn(max = dialogMaxHeight)
                    .fillMaxWidth()
                    .shadow(
                        elevation = 28.dp,
                        shape = AdaptiveDialogShape,
                        ambientColor = Color.Black.copy(alpha = if (appColors.isDark) 0.55f else 0.16f),
                        spotColor = Color.Black.copy(alpha = if (appColors.isDark) 0.70f else 0.24f)
                    )
                    .clip(AdaptiveDialogShape)
                    .border(width = 1.dp, brush = rimBrush, shape = AdaptiveDialogShape)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp, bottom = 14.dp)
                ) {
                    content()
                }
            }
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            containerColor = containerColor,
            shape = sheetShape,
            dragHandle = if (showDragHandle) {
                {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 10.dp)
                            .size(width = 40.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (appColors.isDark) Color(0x40FFFFFF) else Color(0x28000000))
                    )
                }
            } else {
                null
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                content()
            }
        }
    }
}
