package com.xperience.player.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.xperience.player.domain.model.PlaybackState
import com.xperience.player.ui.component.backdrop.BackdropEffectScope
import com.xperience.player.ui.component.backdrop.backdrops.layerBackdrop
import com.xperience.player.ui.component.backdrop.backdrops.rememberCombinedBackdrop
import com.xperience.player.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.xperience.player.ui.component.backdrop.catalog.utils.DampedDragAnimation
import com.xperience.player.ui.component.backdrop.catalog.utils.InteractiveHighlight
import com.xperience.player.ui.component.backdrop.drawBackdrop
import com.xperience.player.ui.component.backdrop.effects.blur
import com.xperience.player.ui.component.backdrop.effects.lens
import com.xperience.player.ui.component.backdrop.effects.vibrancy
import com.xperience.player.ui.component.backdrop.highlight.Highlight
import com.xperience.player.ui.component.backdrop.shadow.InnerShadow
import com.xperience.player.ui.component.backdrop.shadow.Shadow
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.components.NavigationTab
import com.xperience.player.ui.components.readableAccentColor
import com.xperience.player.ui.components.rememberAuroraJellyPressState
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.utils.bounceClick
import com.xperience.player.ui.utils.pressWobble
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign

private val TopChromePillShape = ContinuousRoundedRectangle(percent = 50)
private const val DesktopPuckRestHighlightAlpha = 0.5f
private const val DesktopPuckRestShadowAlpha = 0.35f

/**
 * PC-inspired top LiquidGlass navigation chrome for Expanded screens (landscape tablets / desktop):
 * - Left: Xperience Player brand badge pill
 * - Center-left: Navigation pill with icon + label side-by-side, followed immediately on the right
 *   by the expandable Search button / search input capsule
 * - Right: FloatingMiniPlayer (desktop mode) when the Now Playing sidebar is closed, swapping
 *   smoothly to a circular sidebar-toggle dock button next to Settings when the sidebar is open.
 */
@Composable
fun DesktopTopNavBar(
    navigationItems: List<NavigationTab>,
    selectedTab: NavigationTab,
    onTabSelected: (NavigationTab) -> Unit,
    onBrandClick: () -> Unit,
    playbackState: PlaybackState,
    onMiniPlayerClick: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    isNowPlayingSidebarOpen: Boolean = false,
    onToggleNowPlayingSidebar: () -> Unit = onMiniPlayerClick,
    playbackPositionMsFlow: kotlinx.coroutines.flow.StateFlow<Long>? = null,
) {
    val navSearch = LocalNavSearchState.current
    val glassConfig = LocalGlassEffectConfig.current
    val useGlass = glassConfig.isEnabledFor(GlassComponent.NAV_BAR) && isGlassAllowed()
    val appColors = LocalAppColors.current

    BackHandler(enabled = navSearch.visualActive, onBack = navSearch.onExit)

    val pillSurfaceModifier = if (useGlass) {
        Modifier.liquidGlass(
            config = glassConfig,
            shape = TopChromePillShape,
            highlightAlpha = 0.30f
        )
    } else {
        Modifier
            .clip(TopChromePillShape)
            .background(
                if (appColors.isDark) Color(0xCC191D29) else Color(0xECFFFFFF)
            )
            .border(
                width = 0.9.dp,
                color = if (appColors.isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.09f),
                shape = TopChromePillShape
            )
    }

    val contentColor = if (useGlass) glassConfig.textColor else appColors.textPrimary

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
    ) {
        val barMaxWidth = maxWidth
        val hasTrack = playbackState.currentTrack != null
        val showMiniPlayerSlot = hasTrack && !isNowPlayingSidebarOpen
        val compactBrand = when {
            navSearch.visualActive -> barMaxWidth < if (showMiniPlayerSlot) 1140.dp else 940.dp
            showMiniPlayerSlot -> barMaxWidth < 920.dp
            else -> barMaxWidth < 760.dp
        }
        val compactTabs = when {
            navSearch.visualActive -> barMaxWidth < if (showMiniPlayerSlot) 1040.dp else 820.dp
            showMiniPlayerSlot -> barMaxWidth < 840.dp
            else -> barMaxWidth < 640.dp
        }
        val ultraCompactTabs = barMaxWidth < 560.dp
        val outerClusterGap = if (ultraCompactTabs) 8.dp else 12.dp
        val buttonGap = if (ultraCompactTabs) 8.dp else 10.dp

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // 1. Left Brand Pill
            DesktopBrandPill(
                showLabel = !compactBrand,
                useGlass = useGlass,
                glassConfig = glassConfig,
                onClick = onBrandClick
            )

            Spacer(modifier = Modifier.width(outerClusterGap))

            // 2. NavBar Pill (icon + label side-by-side) + Search button/bar immediately to its right
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(buttonGap),
                modifier = Modifier.weight(1f)
            ) {
                DesktopNavTabPill(
                    navigationItems = navigationItems,
                    selectedTab = selectedTab,
                    searchActive = navSearch.visualActive,
                    compactTabs = compactTabs,
                    ultraCompactTabs = ultraCompactTabs,
                    useGlass = useGlass,
                    glassConfig = glassConfig,
                    pillSurfaceModifier = pillSurfaceModifier,
                    contentColor = contentColor,
                    modifier = Modifier.weight(1f, fill = false),
                    onTabSelected = { tab ->
                        if (navSearch.visualActive) {
                            navSearch.onExit()
                        }
                        onTabSelected(tab)
                    }
                )

                // Search button / expanding inline search input to the right of the navbar
                AnimatedContent(
                    targetState = navSearch.visualActive,
                    transitionSpec = {
                        (fadeIn(tween(200)) + scaleIn(initialScale = 0.92f, animationSpec = tween(220))) togetherWith
                            (fadeOut(tween(150)) + scaleOut(targetScale = 0.92f))
                    },
                    label = "desktopSearchSlot"
                ) { searchOpen ->
                    if (!searchOpen) {
                        GlassCircleButton(
                            onClick = {
                                navSearch.onTapSearchIcon()
                                navSearch.onTapBar()
                            },
                            size = 46.dp
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = contentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        val searchMin = if (barMaxWidth < 760.dp) 150.dp else 200.dp
                        val searchMax = if (barMaxWidth < 760.dp) 240.dp else 310.dp
                        DesktopInlineSearchBar(
                            state = navSearch,
                            pillSurfaceModifier = pillSurfaceModifier,
                            contentColor = contentColor,
                            modifier = Modifier.widthIn(min = searchMin, max = searchMax)
                        )
                    }
                }
            }

            // 3. Right cluster: FloatingMiniPlayer (sidebar closed) <-> Circular Sidebar Dock Button (sidebar open) + Settings
            AnimatedVisibility(
                visible = hasTrack,
                enter = fadeIn(tween(200)) + expandHorizontally(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    expandFrom = Alignment.End
                ),
                exit = fadeOut(tween(150)) + shrinkHorizontally(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    shrinkTowards = Alignment.End
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(outerClusterGap))
                    AnimatedContent(
                        targetState = isNowPlayingSidebarOpen,
                        transitionSpec = {
                            val enter = fadeIn(tween(220, delayMillis = 45)) + scaleIn(
                                initialScale = 0.86f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                            val exit = fadeOut(tween(150)) + scaleOut(
                                targetScale = 0.86f,
                                animationSpec = tween(150)
                            )
                            (enter togetherWith exit) using SizeTransform(
                                clip = false,
                                sizeAnimationSpec = { _, _ ->
                                    spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                }
                            )
                        },
                        label = "desktopMiniPlayerSidebarToggle"
                    ) { sidebarOpen ->
                        if (!sidebarOpen) {
                            val miniPlayerMaxWidth = if (navSearch.visualActive && barMaxWidth < 1080.dp) {
                                236.dp
                            } else {
                                296.dp
                            }
                            FloatingMiniPlayer(
                                playbackState = playbackState,
                                isInline = false,
                                isDesktopBar = true,
                                contentColor = contentColor,
                                onClick = onMiniPlayerClick,
                                onTogglePlayPause = onTogglePlayPause,
                                onSkipNext = onSkipNext,
                                onSkipPrevious = onSkipPrevious,
                                onSeek = onSeek,
                                modifier = Modifier
                                    .widthIn(min = 196.dp, max = miniPlayerMaxWidth)
                                    .height(48.dp),
                                surfaceModifier = Modifier
                                    .fillMaxSize()
                                    .then(pillSurfaceModifier),
                                playbackPositionMsFlow = playbackPositionMsFlow,
                            )
                        } else {
                            GlassCircleButton(
                                onClick = onToggleNowPlayingSidebar,
                                size = 46.dp
                            ) {
                                SidebarDockIcon(
                                    tint = contentColor,
                                    sidebarOpen = true,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(buttonGap))

            GlassCircleButton(
                onClick = onOpenSettings,
                size = 46.dp
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun SidebarDockIcon(
    tint: Color,
    sidebarOpen: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.semantics {
            contentDescription = if (sidebarOpen) {
                "Hide Now Playing sidebar"
            } else {
                "Show Now Playing sidebar"
            }
        }
    ) {
        val strokeWidth = 1.6.dp.toPx()
        val inset = strokeWidth / 2f
        val left = size.width * 0.10f + inset
        val top = size.height * 0.17f + inset
        val width = size.width * 0.80f - strokeWidth
        val height = size.height * 0.66f - strokeWidth
        val corner = 3.2.dp.toPx()
        val splitX = left + width * 0.63f

        val framePath = Path().apply {
            addRoundRect(
                RoundRect(
                    left = left,
                    top = top,
                    right = left + width,
                    bottom = top + height,
                    cornerRadius = CornerRadius(corner, corner)
                )
            )
        }

        if (sidebarOpen) {
            clipPath(framePath) {
                drawRect(
                    color = tint.copy(alpha = 0.85f),
                    topLeft = Offset(splitX, top),
                    size = Size(left + width - splitX, height)
                )
            }
        }

        drawRoundRect(
            color = tint,
            topLeft = Offset(left, top),
            size = Size(width, height),
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = strokeWidth)
        )

        drawLine(
            color = tint,
            start = Offset(splitX, top),
            end = Offset(splitX, top + height),
            strokeWidth = strokeWidth
        )
    }
}

@Composable
private fun DesktopBrandPill(
    showLabel: Boolean,
    useGlass: Boolean,
    glassConfig: GlassEffectConfig,
    onClick: () -> Unit
) {
    val appColors = LocalAppColors.current
    val interaction = remember { MutableInteractionSource() }
    val jelly = rememberAuroraJellyPressState(
        interactionSource = interaction,
        pressedScaleX = 0.94f,
        pressedScaleY = 0.90f,
        releaseVelocityX = 1.8f,
        releaseVelocityY = 1.0f,
        releaseWobbleDeg = 1.8f
    )
    val notePop = remember { Animatable(1f) }
    var tapCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(tapCount) {
        if (tapCount > 0) {
            notePop.snapTo(1.22f)
            notePop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 420f))
        }
    }

    val surfaceModifier = if (useGlass) {
        Modifier.liquidGlass(
            config = glassConfig,
            shape = TopChromePillShape,
            highlightAlpha = 0.28f
        )
    } else {
        Modifier
            .clip(TopChromePillShape)
            .background(if (appColors.isDark) Color(0xFF171922) else Color.White)
            .border(
                width = 0.9.dp,
                color = if (appColors.isDark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.09f),
                shape = TopChromePillShape
            )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(46.dp)
            .graphicsLayer {
                scaleX = jelly.scaleX.value
                scaleY = jelly.scaleY.value
                rotationZ = jelly.rotationZ.value
            }
            .shadow(
                elevation = 8.dp,
                shape = TopChromePillShape,
                ambientColor = Color.Black.copy(alpha = 0.16f),
                spotColor = Color.Black.copy(alpha = 0.22f)
            )
            .clip(TopChromePillShape)
            .then(surfaceModifier)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = {
                    tapCount++
                    onClick()
                }
            )
            .padding(horizontal = if (showLabel) 14.dp else 11.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    scaleX = notePop.value
                    scaleY = notePop.value
                }
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFFF7272)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = "Xperience Player",
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }

        if (showLabel) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Xperience Player",
                color = if (useGlass) glassConfig.textColor else appColors.textPrimary,
                fontFamily = GoogleSans,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DesktopNavTabPill(
    navigationItems: List<NavigationTab>,
    selectedTab: NavigationTab,
    searchActive: Boolean,
    compactTabs: Boolean,
    ultraCompactTabs: Boolean,
    useGlass: Boolean,
    glassConfig: GlassEffectConfig,
    pillSurfaceModifier: Modifier,
    contentColor: Color,
    onTabSelected: (NavigationTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabsCount = navigationItems.size
    if (tabsCount == 0) return

    val appColors = LocalAppColors.current
    val accentColor = LocalAccentColor.current
    val displayAccent = readableAccentColor(accentColor, appColors.isDark)
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val isLtr = layoutDirection == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()

    val currentTabs = rememberUpdatedState(navigationItems)
    val currentOnTabSelected = rememberUpdatedState(onTabSelected)
    val currentIsLtr = rememberUpdatedState(isLtr)

    val backdrop = if (useGlass) LocalAppBackdrop.current else null
    val tabsBackdrop = rememberLayerBackdrop()

    val puckWash = if (glassConfig.puckColor.isSpecified) {
        glassConfig.puckColor
    } else if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        Color(0xFFF2F2F2)
    } else {
        Color(28, 27, 28)
    }
    val puckRestAlpha = glassConfig.puckOpacity.coerceIn(0f, 1f)

    val pillPadding = 4.dp
    val paddingPx = with(density) { pillPadding.toPx() }
    val tabHorizontalPadding = when {
        ultraCompactTabs -> 7.dp
        compactTabs -> 10.dp
        else -> 13.dp
    }

    val selectedIndex = navigationItems.indexOf(selectedTab).coerceIn(0, tabsCount - 1)
    val itemOffsets = remember(tabsCount) { mutableStateMapOf<Int, Int>() }
    val itemWidths = remember(tabsCount) { mutableStateMapOf<Int, Int>() }
    var barWidthPx by remember { mutableIntStateOf(0) }
    val rowInnerWidthPx = (barWidthPx - paddingPx * 2f).coerceAtLeast(1f)
    val allTabsMeasured = (0 until tabsCount).all { (itemWidths[it] ?: 0) > 0 }

    fun puckOffsetPxFor(v: Float): Float {
        if (tabsCount <= 1) return (itemOffsets[0] ?: 0).toFloat()
        val clamped = v.coerceIn(0f, (tabsCount - 1).toFloat())
        val i0 = floor(clamped).toInt().coerceIn(0, tabsCount - 1)
        val i1 = (i0 + 1).coerceIn(0, tabsCount - 1)
        val t = clamped - i0
        val o0 = (itemOffsets[i0] ?: 0).toFloat()
        val o1 = (itemOffsets[i1] ?: o0.roundToInt()).toFloat()
        return lerp(o0, o1, t)
    }

    fun puckWidthPxFor(v: Float): Float {
        if (tabsCount <= 1) return (itemWidths[0] ?: 0).toFloat()
        val clamped = v.coerceIn(0f, (tabsCount - 1).toFloat())
        val i0 = floor(clamped).toInt().coerceIn(0, tabsCount - 1)
        val i1 = (i0 + 1).coerceIn(0, tabsCount - 1)
        val t = clamped - i0
        val w0 = (itemWidths[i0] ?: 0).toFloat()
        val w1 = (itemWidths[i1] ?: w0.roundToInt()).toFloat()
        return lerp(w0, w1, t)
    }

    val offsetAnimation = remember(tabsCount) { Animatable(0f) }
    val panelOffset by remember(density) {
        derivedStateOf {
            val totalWidth = (barWidthPx.toFloat()).coerceAtLeast(1f)
            val fraction = (offsetAnimation.value / totalWidth).fastCoerceIn(-1f, 1f)
            with(density) {
                4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
            }
        }
    }

    var currentIndex by remember(tabsCount) {
        mutableIntStateOf(selectedIndex)
    }

    val applyDragDelta: DampedDragAnimation.(Float) -> Unit = { dragAmountX ->
        if (tabsCount > 1) {
            val maxIdx = tabsCount - 1
            val signedDragPx = dragAmountX * if (currentIsLtr.value) 1f else -1f
            val currentTarget = targetValue
            val segStart = if (signedDragPx >= 0f) {
                floor(currentTarget).toInt().coerceIn(0, maxIdx - 1)
            } else {
                (ceil(currentTarget).toInt() - 1).coerceIn(0, maxIdx - 1)
            }
            val segEnd = (segStart + 1).coerceIn(0, maxIdx)
            val c0 = (itemOffsets[segStart] ?: 0) + (itemWidths[segStart] ?: 0) / 2f
            val c1 = (itemOffsets[segEnd] ?: 0) + (itemWidths[segEnd] ?: 0) / 2f
            val stepPx = abs(c1 - c0).coerceAtLeast(1f)
            updateValue(
                (currentTarget + signedDragPx / stepPx)
                    .fastCoerceIn(0f, maxIdx.toFloat())
            )
            animationScope.launch {
                offsetAnimation.snapTo(offsetAnimation.value + dragAmountX)
            }
        }
    }

    val finishDrag: DampedDragAnimation.() -> Unit = {
        val targetIndex = targetValue.fastRoundToInt().coerceIn(0, tabsCount - 1)
        currentIndex = targetIndex
        updateValue(targetIndex.toFloat())
        animationScope.launch {
            offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
        }
        currentTabs.value.getOrNull(targetIndex)?.let { tab ->
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            currentOnTabSelected.value(tab)
        }
    }

    val dampedDragAnimation = remember(animationScope, tabsCount) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = currentIndex.toFloat(),
            valueRange = 0f..(tabsCount - 1).coerceAtLeast(1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 78f / 56f,
            onDragStarted = {},
            onDragStopped = { finishDrag() },
            onDrag = { _, dragAmount -> applyDragDelta(dragAmount.x) },
            velocityDampingRatio = 1f,
        )
    }

    var hasSyncedSelection by remember(tabsCount) { mutableStateOf(false) }

    LaunchedEffect(selectedIndex, tabsCount) {
        currentIndex = selectedIndex
        if (!hasSyncedSelection) {
            hasSyncedSelection = true
            dampedDragAnimation.updateValue(selectedIndex.toFloat())
            return@LaunchedEffect
        }
        dampedDragAnimation.animateToValue(selectedIndex.toFloat())
    }

    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(
            animationScope = animationScope,
            position = { size, _ ->
                val puckCenter = puckOffsetPxFor(dampedDragAnimation.value) +
                    puckWidthPxFor(dampedDragAnimation.value) / 2f
                Offset(
                    paddingPx + puckCenter + panelOffset,
                    size.height / 2f
                )
            }
        )
    }

    val puckTranslationX: () -> Float = {
        val puckOffsetPx = puckOffsetPxFor(dampedDragAnimation.value)
        val puckWidthPx = puckWidthPxFor(dampedDragAnimation.value)
        val baseX = if (currentIsLtr.value) {
            puckOffsetPx
        } else {
            puckOffsetPx - (rowInnerWidthPx - puckWidthPx)
        }
        baseX + panelOffset
    }

    val puckFallbackFill = Color.White
    val puckFallbackBorder = if (appColors.isDark) {
        Color.White.copy(alpha = 0.24f)
    } else {
        Color.Black.copy(alpha = 0.08f)
    }

    val tabsBackdropScale = glassResolutionScale(1f)
    val pillShapeBlock: () -> Shape = remember { { TopChromePillShape } }
    val tabsEffectsBlock: BackdropEffectScope.() -> Unit =
        remember(dampedDragAnimation, glassConfig.blurRadius, tabsBackdropScale) {
            {
                val progress = dampedDragAnimation.pressProgress
                vibrancy()
                blur(glassConfig.blurRadius.dp.toPx() * tabsBackdropScale)
                lens(
                    15f.dp.toPx() * progress * tabsBackdropScale,
                    18f.dp.toPx() * progress * tabsBackdropScale
                )
            }
        }
    val tabsHighlightBlock: () -> Highlight? = remember(dampedDragAnimation) {
        {
            val progress = dampedDragAnimation.pressProgress
            Highlight.Default.copy(alpha = progress)
        }
    }
    val tabsSurfaceBlock: DrawScope.() -> Unit = remember {
        { drawRect(Color.Transparent) }
    }

    val puckEffectsBlock: BackdropEffectScope.() -> Unit =
        remember(dampedDragAnimation) {
            {
                val progress = dampedDragAnimation.pressProgress
                blur(3f.dp.toPx() * (1f - progress))
                lens(
                    lerp(0f.dp.toPx(), 10f.dp.toPx(), progress),
                    lerp(0f.dp.toPx(), 12f.dp.toPx(), progress),
                    chromaticAberration = progress > 0.01f
                )
            }
        }
    val puckHighlightBlock: () -> Highlight? = remember(dampedDragAnimation) {
        {
            val progress = dampedDragAnimation.pressProgress
            Highlight.Default.copy(
                alpha = lerp(DesktopPuckRestHighlightAlpha, 1f, progress)
            )
        }
    }
    val puckShadowBlock: () -> Shadow? = remember(dampedDragAnimation) {
        {
            val progress = dampedDragAnimation.pressProgress
            Shadow(alpha = lerp(DesktopPuckRestShadowAlpha, 1f, progress))
        }
    }
    val puckInnerShadowBlock: () -> InnerShadow? = remember(dampedDragAnimation) {
        {
            val progress = dampedDragAnimation.pressProgress
            InnerShadow(radius = 8f.dp * progress, alpha = progress)
        }
    }
    val puckLayerBlock: GraphicsLayerScope.() -> Unit =
        remember(dampedDragAnimation) {
            {
                scaleX = dampedDragAnimation.scaleX
                scaleY = dampedDragAnimation.scaleY
                val velocity = dampedDragAnimation.velocity / 10f
                scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
            }
        }
    val puckSurfaceBlock: DrawScope.() -> Unit =
        remember(dampedDragAnimation, puckWash, puckRestAlpha) {
            {
                val progress = dampedDragAnimation.pressProgress
                drawRect(puckWash.copy(alpha = puckRestAlpha * (1f - 0.75f * progress)))
            }
        }

    Box(
        modifier = modifier.height(46.dp)
    ) {
        // 1. Base floating glass pill + interactive tabs
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .fillMaxHeight()
                .onSizeChanged { barWidthPx = it.width }
                .graphicsLayer { translationX = panelOffset }
                .shadow(
                    elevation = 8.dp,
                    shape = TopChromePillShape,
                    ambientColor = Color.Black.copy(alpha = 0.16f),
                    spotColor = Color.Black.copy(alpha = 0.22f)
                )
                .clip(TopChromePillShape)
                .then(pillSurfaceModifier)
                .then(interactiveHighlight.modifier)
                .pointerInput(tabsCount, searchActive) {
                    if (searchActive || tabsCount <= 1) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { startOffset ->
                            if (!allTabsMeasured) return@detectHorizontalDragGestures
                            val localX = startOffset.x - paddingPx
                            val startIdx = (0 until tabsCount).minByOrNull { idx ->
                                val center = (itemOffsets[idx] ?: 0) + (itemWidths[idx] ?: 0) / 2f
                                abs(localX - center)
                            } ?: currentIndex
                            currentIndex = startIdx
                            dampedDragAnimation.press()
                            dampedDragAnimation.updateValue(startIdx.toFloat())
                        },
                        onDragEnd = {
                            if (!allTabsMeasured) return@detectHorizontalDragGestures
                            dampedDragAnimation.finishDrag()
                            dampedDragAnimation.release()
                        },
                        onDragCancel = {
                            if (!allTabsMeasured) return@detectHorizontalDragGestures
                            dampedDragAnimation.finishDrag()
                            dampedDragAnimation.release()
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            if (!allTabsMeasured) return@detectHorizontalDragGestures
                            dampedDragAnimation.applyDragDelta(dragAmount)
                        }
                    )
                }
                .padding(pillPadding)
        ) {
            navigationItems.forEachIndexed { index, tab ->
                val isSelected = !searchActive && tab == selectedTab
                val itemColor by animateColorAsState(
                    targetValue = if (isSelected) contentColor else contentColor.copy(alpha = 0.66f),
                    animationSpec = tween(180),
                    label = "desktopTabTint"
                )
                val interaction = remember { MutableInteractionSource() }

                DesktopTabItemContent(
                    tab = tab,
                    tint = itemColor,
                    isSelected = isSelected,
                    compactTabs = compactTabs,
                    ultraCompactTabs = ultraCompactTabs,
                    contentScaleProvider = {
                        if (index == currentIndex && !searchActive) {
                            lerp(1f, 1.12f, dampedDragAnimation.pressProgress)
                        } else {
                            1f
                        }
                    },
                    modifier = Modifier
                        .fillMaxHeight()
                        .onGloballyPositioned { coords ->
                            itemOffsets[index] = coords.positionInParent().x.roundToInt()
                            itemWidths[index] = coords.size.width
                        }
                        .pressWobble(interaction, pressedScale = 0.94f)
                        .clip(TopChromePillShape)
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onTabSelected(tab)
                            }
                        )
                        .padding(horizontal = tabHorizontalPadding)
                )
            }
        }

        // 2. Offscreen refractive accent-tinted layer sampled by the lens puck
        if (backdrop != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .matchParentSize()
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .padding(pillPadding)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = pillShapeBlock,
                        effects = tabsEffectsBlock,
                        highlight = tabsHighlightBlock,
                        onDrawSurface = tabsSurfaceBlock,
                        backdropScale = tabsBackdropScale
                    )
                    .then(interactiveHighlight.modifier)
                    .graphicsLayer(colorFilter = ColorFilter.tint(displayAccent))
            ) {
                navigationItems.forEachIndexed { index, tab ->
                    val isSelected = !searchActive && tab == selectedTab
                    DesktopTabItemContent(
                        tab = tab,
                        tint = contentColor,
                        isSelected = isSelected,
                        compactTabs = compactTabs,
                        ultraCompactTabs = ultraCompactTabs,
                        contentScaleProvider = {
                            if (index == currentIndex && !searchActive) {
                                lerp(1f, 1.12f, dampedDragAnimation.pressProgress)
                            } else {
                                1f
                            }
                        },
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = tabHorizontalPadding)
                    )
                }
            }
        }

        // 3. Draggable LiquidGlass selection puck (hidden while search mode is focused)
        if (!searchActive && allTabsMeasured) {
            Box(
                Modifier
                    .padding(pillPadding)
                    .graphicsLayer {
                        translationX = puckTranslationX()
                    }
                    .then(interactiveHighlight.gestureModifier)
                    .then(dampedDragAnimation.modifier)
                    .layout { measurable, constraints ->
                        val wPx = puckWidthPxFor(dampedDragAnimation.value).roundToInt().coerceAtLeast(1)
                        val placeable = measurable.measure(constraints.copy(minWidth = wPx, maxWidth = wPx))
                        layout(placeable.width, placeable.height) {
                            placeable.placeRelative(0, 0)
                        }
                    }
                    .fillMaxHeight()
                    .then(
                        if (backdrop != null) {
                            Modifier.drawBackdrop(
                                backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                                shape = pillShapeBlock,
                                effects = puckEffectsBlock,
                                highlight = puckHighlightBlock,
                                shadow = puckShadowBlock,
                                innerShadow = puckInnerShadowBlock,
                                layerBlock = puckLayerBlock,
                                onDrawSurface = puckSurfaceBlock
                            )
                        } else {
                            Modifier
                                .graphicsLayer(puckLayerBlock)
                                .shadow(
                                    shape = TopChromePillShape,
                                    elevation = 8.dp * dampedDragAnimation.pressProgress
                                )
                                .clip(TopChromePillShape)
                                .background(
                                    puckFallbackFill.copy(
                                        alpha = (if (appColors.isDark) 0.16f else 0.78f) *
                                            (1f - 0.35f * dampedDragAnimation.pressProgress)
                                    )
                                )
                                .border(
                                    width = 0.8.dp,
                                    brush = Brush.verticalGradient(
                                        listOf(puckFallbackBorder, displayAccent.copy(alpha = 0.18f))
                                    ),
                                    shape = TopChromePillShape
                                )
                        }
                    )
            )

            // 4. Crisp selected tab icon + label rendered above the resting puck; fades during lens press/drag
            val puckIndex by remember(tabsCount) {
                derivedStateOf {
                    dampedDragAnimation.value
                        .fastRoundToInt()
                        .coerceIn(0, (tabsCount - 1).coerceAtLeast(0))
                }
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(pillPadding)
                    .graphicsLayer {
                        translationX = puckTranslationX()
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        alpha = (1f - dampedDragAnimation.pressProgress * 3f).fastCoerceIn(0f, 1f)
                    }
                    .layout { measurable, constraints ->
                        val wPx = puckWidthPxFor(dampedDragAnimation.value).roundToInt().coerceAtLeast(1)
                        val placeable = measurable.measure(constraints.copy(minWidth = wPx, maxWidth = wPx))
                        layout(placeable.width, placeable.height) {
                            placeable.placeRelative(0, 0)
                        }
                    }
                    .fillMaxHeight()
                    .clearAndSetSemantics {},
            ) {
                currentTabs.value.getOrNull(puckIndex)?.let { tab ->
                    DesktopTabItemContent(
                        tab = tab,
                        tint = contentColor,
                        isSelected = true,
                        compactTabs = compactTabs,
                        ultraCompactTabs = ultraCompactTabs,
                        modifier = Modifier.padding(horizontal = tabHorizontalPadding)
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopTabItemContent(
    tab: NavigationTab,
    tint: Color,
    isSelected: Boolean,
    compactTabs: Boolean,
    ultraCompactTabs: Boolean,
    modifier: Modifier = Modifier,
    contentScaleProvider: (() -> Float)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (ultraCompactTabs) 4.dp else 6.dp),
        modifier = modifier.then(
            if (contentScaleProvider != null) {
                Modifier.graphicsLayer {
                    val scale = contentScaleProvider()
                    scaleX = scale
                    scaleY = scale
                }
            } else {
                Modifier
            }
        )
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = tint,
            modifier = Modifier.size(if (ultraCompactTabs) 16.dp else 18.dp)
        )
        Text(
            text = tab.label,
            color = tint,
            fontFamily = GoogleSans,
            fontSize = when {
                ultraCompactTabs -> 11.5.sp
                compactTabs -> 12.sp
                else -> 13.sp
            },
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DesktopInlineSearchBar(
    state: NavSearchState,
    pillSurfaceModifier: Modifier,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val pillGlow = remember(coroutineScope) { InteractiveHighlight(animationScope = coroutineScope) }
    val accentColor = LocalAccentColor.current

    LaunchedEffect(Unit) {
        delay(120L)
        try {
            state.focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(46.dp)
            .shadow(
                elevation = 8.dp,
                shape = TopChromePillShape,
                ambientColor = Color.Black.copy(alpha = 0.16f),
                spotColor = Color.Black.copy(alpha = 0.22f)
            )
            .clip(TopChromePillShape)
            .then(pillSurfaceModifier)
            .then(pillGlow.gestureModifier)
            .then(pillGlow.modifier)
            .padding(horizontal = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .padding(7.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (state.query.text.isEmpty()) {
                DynamicSearchPlaceholder(
                    searchSource = state.searchSource,
                    style = TextStyle(color = contentColor.copy(alpha = 0.58f), fontSize = 13.5.sp),
                )
            }
            BasicTextField(
                value = state.query,
                onValueChange = state.onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = contentColor,
                    fontFamily = GoogleSans,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(accentColor),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { state.onSubmit(state.query.text) },
                    onDone = { state.onSubmit(state.query.text) },
                    onGo = { state.onSubmit(state.query.text) },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(state.focusRequester)
                    .onKeyEvent {
                        if (it.key == Key.Enter) {
                            state.onSubmit(state.query.text)
                            true
                        } else if (it.key == Key.Escape) {
                            state.onExit()
                            true
                        } else {
                            false
                        }
                    }
            )
        }

        if (state.canToggleSource) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .bounceClick { state.onToggleSource() }
                    .padding(7.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (state.searchSource) {
                        SearchSource.LOCAL -> Icons.Default.Folder
                        SearchSource.ONLINE -> Icons.Default.Public
                    },
                    contentDescription = "Toggle search source",
                    tint = contentColor.copy(alpha = 0.82f),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .bounceClick {
                    if (state.query.text.isNotEmpty()) {
                        state.onQueryChange(TextFieldValue(""))
                    } else {
                        state.onExit()
                    }
                }
                .padding(7.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = if (state.query.text.isNotEmpty()) "Clear query" else "Close search",
                tint = contentColor,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
