@file:OptIn(ExperimentalSharedTransitionApi::class)

/**
 * Convx / Xperience Music Player
 * Licensed under GPL-3.0
 */

package com.xperience.player.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xperience.player.domain.model.PlaybackState
import com.xperience.player.ui.component.backdrop.catalog.utils.InteractiveHighlight
import com.xperience.player.ui.component.floatingtabbar.FloatingTabBar
import com.xperience.player.ui.component.floatingtabbar.FloatingTabBarDefaults
import com.xperience.player.ui.component.floatingtabbar.LocalTabBarBackdropFrozen
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.components.NavigationTab
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.utils.bounceClick
import kotlinx.coroutines.delay

private val NavBarShape = ContinuousRoundedRectangle(percent = 50)
private val NavBarSearchBarHeight = 48.dp
private val NavBarStandaloneReserve = 80.dp
private val NavBarMinTabWidth = 56.dp
private const val KeyboardOpenDelayMs = 260L

/**
 * The Convx iOS 26 style floating navigation bar with fluid animations,
 * scroll retraction/expansion, search expansion, and mini player docking.
 */
@Composable
fun AppFloatingNavBar(
    navigationItems: List<NavigationTab>,
    selectedTab: NavigationTab,
    onTabSelected: (NavigationTab) -> Unit,
    scrollConnection: FloatingTabBarScrollConnection,
    playbackState: PlaybackState,
    onMiniPlayerClick: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
) {
    val navSearch = LocalNavSearchState.current

    AnimatedContent(
        targetState = navSearch.keyboardActive,
        modifier = modifier,
        transitionSpec = {
            if (targetState) {
                (slideInVertically(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                ) { it / 2 } + fadeIn()) togetherWith
                        (slideOutVertically(
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                        ) { it } + fadeOut())
            } else {
                (slideInVertically(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                ) { it } + fadeIn()) togetherWith
                        (slideOutVertically(
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                        ) { it / 2 } + fadeOut())
            }
        },
        label = "navBarKeyboardActive",
    ) { keyboardActive ->
        if (keyboardActive) {
            NavBarSearchInputBar(
                state = navSearch,
                pureBlack = pureBlack,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            AppFloatingNavBarChrome(
                navigationItems = navigationItems,
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
                scrollConnection = scrollConnection,
                pureBlack = pureBlack,
                playbackState = playbackState,
                onMiniPlayerClick = onMiniPlayerClick,
                onTogglePlayPause = onTogglePlayPause,
                onSkipNext = onSkipNext,
                onSkipPrevious = onSkipPrevious,
                onSeek = onSeek,
                searchModeActive = navSearch.visualActive,
                navSearch = navSearch,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AppFloatingNavBarChrome(
    navigationItems: List<NavigationTab>,
    selectedTab: NavigationTab,
    onTabSelected: (NavigationTab) -> Unit,
    scrollConnection: FloatingTabBarScrollConnection,
    pureBlack: Boolean,
    playbackState: PlaybackState,
    onMiniPlayerClick: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    searchModeActive: Boolean,
    navSearch: NavSearchState,
    modifier: Modifier,
) {
    BackHandler(enabled = searchModeActive, onBack = navSearch.onExit)

    var expandedContentWidthPx by remember { mutableStateOf<Int?>(null) }

    val glassConfig = LocalGlassEffectConfig.current
    val useGlass = glassConfig.isEnabledFor(GlassComponent.NAV_BAR) && isGlassAllowed()
    val appColors = LocalAppColors.current

    val backgroundColor = when {
        useGlass -> Color.Transparent
        pureBlack -> Color.Black
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val selectedContentColor = if (useGlass) glassConfig.textColor else appColors.textPrimary
    val unselectedContentColor = selectedContentColor.copy(alpha = 0.6f)

    val tabBarContentModifier = if (useGlass) {
        Modifier.liquidGlass(
            config = glassConfig,
            shape = NavBarShape,
            highlightAlpha = 0.3f,
            frozen = LocalTabBarBackdropFrozen.current,
        )
    } else {
        Modifier
    }

    val latestPlaybackState by rememberUpdatedState(playbackState)
    val showPlayerAccessory = playbackState.currentTrack != null
    val accessoryContentColor = when {
        useGlass -> glassConfig.textColor
        pureBlack -> Color.White
        else -> appColors.textPrimary
    }

    val inlineAccessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)? =
        if (showPlayerAccessory) {
            { accessoryModifier, _ ->
                FloatingMiniPlayer(
                    playbackState = latestPlaybackState,
                    isInline = true,
                    contentColor = accessoryContentColor,
                    onClick = onMiniPlayerClick,
                    onTogglePlayPause = onTogglePlayPause,
                    onSkipNext = onSkipNext,
                    onSkipPrevious = onSkipPrevious,
                    onSeek = onSeek,
                    modifier = Modifier,
                    surfaceModifier = accessoryModifier.then(tabBarContentModifier),
                )
            }
        } else {
            null
        }

    val expandedAccessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)? =
        if (showPlayerAccessory) {
            { accessoryModifier, _ ->
                FloatingMiniPlayer(
                    playbackState = latestPlaybackState,
                    isInline = false,
                    contentColor = accessoryContentColor,
                    onClick = onMiniPlayerClick,
                    onTogglePlayPause = onTogglePlayPause,
                    onSkipNext = onSkipNext,
                    onSkipPrevious = onSkipPrevious,
                    onSeek = onSeek,
                    modifier = Modifier.fillMaxWidth(),
                    surfaceModifier = accessoryModifier.fillMaxWidth().then(tabBarContentModifier),
                )
            }
        } else {
            null
        }

    BoxWithConstraints(modifier) {
        val tabWidth = if (navigationItems.isEmpty()) {
            FloatingTabBarDefaults.TabWidth
        } else {
            ((maxWidth - NavBarStandaloneReserve) / navigationItems.size)
                .coerceIn(NavBarMinTabWidth, FloatingTabBarDefaults.TabWidth)
        }

        FloatingTabBar(
            selectedTabKey = selectedTab.name,
            scrollConnection = scrollConnection,
            modifier = Modifier.fillMaxWidth(),
            tabBarContentModifier = tabBarContentModifier,
            inlineAccessory = inlineAccessory,
            expandedAccessory = expandedAccessory,
            colors = FloatingTabBarDefaults.colors(
                backgroundColor = backgroundColor,
                accessoryBackgroundColor = backgroundColor,
            ),
            sizes = FloatingTabBarDefaults.sizes(
                tabBarContentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp),
                tabExpandedContentPadding = PaddingValues(vertical = 4.dp, horizontal = 6.dp),
                tabInlineContentPadding = PaddingValues(8.dp),
                tabWidth = tabWidth,
            ),
            backdrop = if (useGlass) LocalAppBackdrop.current else null,
            accentColor = LocalAccentColor.current,
            searchMode = searchModeActive,
            searchBarContent = if (searchModeActive) {
                { contentModifier ->
                    SearchBarPlaceholder(
                        state = navSearch,
                        contentColor = accessoryContentColor,
                        modifier = contentModifier,
                    )
                }
            } else {
                null
            },
            expandedContentWidthPx = expandedContentWidthPx,
            onExpandedWidthChanged = { expandedContentWidthPx = it },
        ) {
            navigationItems.forEach { tabItem ->
                val isSelected = tabItem == selectedTab
                tab(
                    key = tabItem.name,
                    title = {
                        Text(
                            text = tabItem.label,
                            color = if (isSelected) selectedContentColor else unselectedContentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 10.sp,
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = tabItem.icon,
                            contentDescription = tabItem.label,
                            tint = if (isSelected) selectedContentColor else unselectedContentColor,
                            modifier = Modifier.size(26.dp),
                        )
                    },
                    onClick = {
                        if (searchModeActive) {
                            navSearch.onExit()
                        } else {
                            onTabSelected(tabItem)
                        }
                    },
                )
            }

            // Standalone Circular Search Tab
            val isSearchSelected = searchModeActive
            standaloneTab(
                key = "Search",
                title = {
                    Text(
                        text = "Search",
                        color = if (isSearchSelected) selectedContentColor else unselectedContentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 10.sp,
                    )
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = if (isSearchSelected) selectedContentColor else unselectedContentColor,
                        modifier = Modifier.size(26.dp),
                    )
                },
                onClick = {
                    if (searchModeActive) {
                        navSearch.onTapBar()
                    } else {
                        navSearch.onTapSearchIcon()
                    }
                },
            )
        }
    }
}

/**
 * Pre-keyboard search bar content — the wide slot [FloatingTabBar] expands the
 * search circle into once [searchMode] is on.
 */
@Composable
private fun SearchBarPlaceholder(
    state: NavSearchState,
    contentColor: Color,
    modifier: Modifier,
) {
    val glowScope = rememberCoroutineScope()
    val glow = remember(glowScope) { InteractiveHighlight(animationScope = glowScope) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .then(glow.gestureModifier)
            .then(glow.modifier)
            .bounceClick(onClick = state.onTapBar)
            .padding(start = 4.dp, end = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .bounceClick { state.onExit() }
                .padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = contentColor,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(Modifier.weight(1f)) {
            DynamicSearchPlaceholder(
                searchSource = state.searchSource,
                style = TextStyle(color = contentColor.copy(alpha = 0.6f), fontSize = 15.sp),
            )
        }
        if (state.canToggleSource) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .bounceClick { state.onToggleSource() }
                    .padding(9.dp)
            ) {
                Icon(
                    imageVector = when (state.searchSource) {
                        SearchSource.LOCAL -> Icons.Default.Folder
                        SearchSource.ONLINE -> Icons.Default.Public
                    },
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * Keyboard-active search state — replaces the whole nav bar while focused,
 * docked above the keyboard via [Modifier.imePadding].
 */
@Composable
fun NavBarSearchInputBar(
    state: NavSearchState,
    pureBlack: Boolean,
    modifier: Modifier,
) {
    val glassConfig = LocalGlassEffectConfig.current
    val useGlass = glassConfig.isEnabledFor(GlassComponent.NAV_BAR) && isGlassAllowed()
    val appColors = LocalAppColors.current

    val onTint = when {
        useGlass -> glassConfig.textColor
        pureBlack -> Color.White
        else -> appColors.textPrimary
    }
    val pillShape = ContinuousRoundedRectangle(percent = 50)
    val coroutineScope = rememberCoroutineScope()
    val pillGlow = remember(coroutineScope) { InteractiveHighlight(animationScope = coroutineScope) }

    BackHandler(onBack = state.onCloseKeyboard)

    LaunchedEffect(Unit) {
        delay(KeyboardOpenDelayMs)
        try {
            state.focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 6.dp, bottom = 2.dp)
            .height(NavBarSearchBarHeight)
            .clip(pillShape)
            .then(
                if (useGlass) {
                    Modifier.liquidGlass(
                        config = glassConfig.copy(surfaceOpacity = 0.15f),
                        shape = pillShape,
                        highlightAlpha = 0.3f,
                    )
                } else {
                    Modifier.background(onTint.copy(alpha = 0.15f))
                }
            )
            .then(pillGlow.gestureModifier)
            .then(pillGlow.modifier)
            .padding(horizontal = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .bounceClick { state.onExit() }
                .padding(10.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = onTint,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            if (state.query.text.isEmpty()) {
                DynamicSearchPlaceholder(
                    searchSource = state.searchSource,
                    style = TextStyle(color = onTint.copy(alpha = 0.6f), fontSize = 16.sp),
                )
            }
            BasicTextField(
                value = state.query,
                onValueChange = state.onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = onTint, fontSize = 16.sp),
                cursorBrush = SolidColor(onTint),
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
                        } else {
                            false
                        }
                    }
            )
        }
        if (state.query.text.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .bounceClick { state.onQueryChange(TextFieldValue("")) }
                    .padding(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear",
                    tint = onTint,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (state.canToggleSource) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .bounceClick { state.onToggleSource() }
                    .padding(10.dp)
            ) {
                Icon(
                    imageVector = when (state.searchSource) {
                        SearchSource.LOCAL -> Icons.Default.Folder
                        SearchSource.ONLINE -> Icons.Default.Public
                    },
                    contentDescription = null,
                    tint = onTint,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
