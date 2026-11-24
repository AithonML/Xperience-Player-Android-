package com.xperience.player.ui.home

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xperience.player.domain.model.NewReleaseItem
import com.xperience.player.domain.model.RadioStationItem
import com.xperience.player.ui.adaptive.AdaptivePreviewFixtures
import com.xperience.player.ui.adaptive.AdaptivePreviewSurface
import com.xperience.player.ui.adaptive.LocalAppWindowSizeClass
import com.xperience.player.ui.adaptive.LocalBottomChromePadding
import com.xperience.player.ui.adaptive.LocalTopChromePadding
import com.xperience.player.ui.adaptive.adaptiveGridMinSize
import com.xperience.player.ui.adaptive.isMediumOrExpandedWidth
import com.xperience.player.ui.component.GlassComponent
import com.xperience.player.ui.component.LocalAppBackdrop
import com.xperience.player.ui.component.LocalGlassEffectConfig
import com.xperience.player.ui.component.backdrop.backdrops.layerBackdrop
import com.xperience.player.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.xperience.player.ui.component.isGlassAllowed
import com.xperience.player.ui.component.liquidGlass
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.components.NewReleaseCard
import com.xperience.player.ui.components.RadioStationCard
import com.xperience.player.ui.components.jellyTapScale
import com.xperience.player.ui.components.onAccentColor
import com.xperience.player.ui.components.readableAccentColor
import com.xperience.player.ui.components.rememberJellyPressState
import com.xperience.player.ui.theme.AppThemeColors
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.PlusJakartaSans
import com.xperience.player.ui.utils.pressWobble
import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException

enum class HomeGridCategory {
    RADIO_STATIONS,
    NOVEDADES
}

private enum class NovedadesFilter(val label: String) {
    ALL("All"),
    ALBUMS("Albums"),
    SINGLES("Singles")
}

private val PillShape = ContinuousRoundedRectangle(percent = 50)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RadioAndNovedadesGridScreen(
    category: HomeGridCategory,
    radioStations: List<RadioStationItem>,
    newReleases: List<NewReleaseItem>,
    onBack: () -> Unit,
    onRadioClick: (RadioStationItem) -> Unit,
    onReleaseClick: (NewReleaseItem) -> Unit,
    modifier: Modifier = Modifier,
    onCategoryChange: ((HomeGridCategory) -> Unit)? = null,
    appColors: AppThemeColors = LocalAppColors.current,
    accentColor: Color = LocalAccentColor.current,
    enableInternalBackHandler: Boolean = false
) {
    var selectedFilter by remember(category) { mutableStateOf(NovedadesFilter.ALL) }
    var internalBackProgress by remember { mutableFloatStateOf(0f) }

    PredictiveBackHandler(enabled = enableInternalBackHandler) { progressFlow ->
        try {
            progressFlow.collect { event ->
                internalBackProgress = event.progress
            }
            internalBackProgress = 0f
            onBack()
        } catch (_: CancellationException) {
            internalBackProgress = 0f
        }
    }

    val filteredReleases = remember(newReleases, selectedFilter) {
        when (selectedFilter) {
            NovedadesFilter.ALL -> newReleases
            NovedadesFilter.ALBUMS -> newReleases.filter { it.isAlbum }
            NovedadesFilter.SINGLES -> newReleases.filter { !it.isAlbum }
        }
    }

    val activeCount = when (category) {
        HomeGridCategory.RADIO_STATIONS -> radioStations.size
        HomeGridCategory.NOVEDADES -> filteredReleases.size
    }

    val gridState = rememberLazyGridState()
    val windowSizeClass = LocalAppWindowSizeClass.current
    val topChromePadding = LocalTopChromePadding.current
    val bottomChromePadding = LocalBottomChromePadding.current
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topContentPadding = statusBarTop + topChromePadding + if (category == HomeGridCategory.NOVEDADES) 134.dp else 86.dp

    val gridBackdrop = rememberLayerBackdrop(
        onDraw = {
            drawRect(appColors.background)
            drawContent()
        }
    )

    val animatedBackProgress by animateFloatAsState(
        targetValue = internalBackProgress,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "gridBackProgress"
    )
    val effectiveBack = if (enableInternalBackHandler) animatedBackProgress else 0f
    val backCornerRadius = (effectiveBack * 36f).dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                if (effectiveBack > 0.001f) {
                    val s = 1f - effectiveBack * 0.12f
                    scaleX = s
                    scaleY = s
                    translationY = effectiveBack * 90.dp.toPx()
                    clip = true
                    shape = ContinuousRoundedRectangle(size = backCornerRadius)
                }
            }
            .background(appColors.background)
    ) {
        // Scrollable Aurora + Grid Layer captured into gridBackdrop
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(gridBackdrop)
                .drawBehind {
                    val primaryGlow = accentColor.copy(alpha = if (appColors.isDark) 0.24f else 0.16f)
                    val secondaryHue = if (category == HomeGridCategory.RADIO_STATIONS) {
                        Color(0xFF7C3AED)
                    } else {
                        Color(0xFF00C2FF)
                    }
                    val secondaryGlow = secondaryHue.copy(alpha = if (appColors.isDark) 0.18f else 0.12f)

                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(primaryGlow, Color.Transparent),
                            center = Offset(size.width * 0.18f, size.height * 0.08f),
                            radius = size.width * 0.92f
                        )
                    )
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(secondaryGlow, Color.Transparent),
                            center = Offset(size.width * 0.86f, size.height * 0.14f),
                            radius = size.width * 0.85f
                        )
                    )
                }
        ) {
            val isEmpty = when (category) {
                HomeGridCategory.RADIO_STATIONS -> radioStations.isEmpty()
                HomeGridCategory.NOVEDADES -> filteredReleases.isEmpty()
            }

            if (isEmpty) {
                GridEmptyState(
                    category = category,
                    hasActiveFilter = category == HomeGridCategory.NOVEDADES && selectedFilter != NovedadesFilter.ALL,
                    onResetFilter = { selectedFilter = NovedadesFilter.ALL },
                    appColors = appColors,
                    accentColor = accentColor,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topContentPadding, bottom = bottomChromePadding, start = 24.dp, end = 24.dp)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(
                        minSize = windowSizeClass.adaptiveGridMinSize(158.dp, 180.dp, 198.dp)
                    ),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 18.dp,
                        end = 18.dp,
                        top = topContentPadding,
                        bottom = bottomChromePadding
                    ),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (category) {
                        HomeGridCategory.RADIO_STATIONS -> {
                            itemsIndexed(
                                items = radioStations,
                                key = { index, item -> "grid_radio_${item.id}_$index" }
                            ) { index, station ->
                                StaggeredGridItemFrame(
                                    index = index,
                                    staggerKey = category,
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = tween(220),
                                        placementSpec = spring(
                                            dampingRatio = 0.68f,
                                            stiffness = Spring.StiffnessMediumLow
                                        ),
                                        fadeOutSpec = tween(180)
                                    )
                                ) {
                                    RadioStationCard(
                                        station = station,
                                        accentColor = accentColor,
                                        onClick = { onRadioClick(station) },
                                        showPlayBadge = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(0.84f)
                                    )
                                }
                            }
                        }

                        HomeGridCategory.NOVEDADES -> {
                            itemsIndexed(
                                items = filteredReleases,
                                key = { index, item -> "grid_release_${item.id}_$index" }
                            ) { index, release ->
                                StaggeredGridItemFrame(
                                    index = index,
                                    staggerKey = selectedFilter,
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = tween(220),
                                        placementSpec = spring(
                                            dampingRatio = 0.68f,
                                            stiffness = Spring.StiffnessMediumLow
                                        ),
                                        fadeOutSpec = tween(180)
                                    )
                                ) {
                                    NewReleaseCard(
                                        release = release,
                                        accentColor = accentColor,
                                        onClick = { onReleaseClick(release) },
                                        showPlayBadge = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(0.84f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Top soft gradient fade behind floating LiquidGlass top bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(topContentPadding + 14.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            appColors.background.copy(alpha = 0.88f),
                            appColors.background.copy(alpha = 0.56f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Floating LiquidGlass Top Bar & Optional Filter Chips
        CompositionLocalProvider(LocalAppBackdrop provides gridBackdrop) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = topChromePadding)
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LiquidGlassBackPill(
                        onClick = onBack,
                        appColors = appColors
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        AnimatedContent(
                            targetState = category,
                            transitionSpec = {
                                (fadeIn(tween(240)) togetherWith fadeOut(tween(180)))
                            },
                            label = "gridCategoryTitle"
                        ) { activeCategory ->
                            val titleText = when (activeCategory) {
                                HomeGridCategory.RADIO_STATIONS -> "Radio Stations"
                                HomeGridCategory.NOVEDADES -> "New Releases"
                            }
                            Text(
                                text = titleText,
                                color = appColors.textPrimary,
                                fontFamily = GoogleSans,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        val subtitleText = when (category) {
                            HomeGridCategory.RADIO_STATIONS -> "$activeCount stations & mixes"
                            HomeGridCategory.NOVEDADES -> "$activeCount recent releases"
                        }
                        Text(
                            text = subtitleText,
                            color = appColors.textSecondary,
                            fontFamily = PlusJakartaSans,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }

                    if (onCategoryChange != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        CategoryTogglePill(
                            category = category,
                            accentColor = accentColor,
                            appColors = appColors,
                            onClick = {
                                val next = if (category == HomeGridCategory.RADIO_STATIONS) {
                                    HomeGridCategory.NOVEDADES
                                } else {
                                    HomeGridCategory.RADIO_STATIONS
                                }
                                onCategoryChange(next)
                            }
                        )
                    }
                }

                AnimatedVisibility(
                    visible = category == HomeGridCategory.NOVEDADES,
                    enter = fadeIn(tween(200)) + expandVertically(),
                    exit = fadeOut(tween(150)) + shrinkVertically()
                ) {
                    val renderChips: @Composable () -> Unit = {
                        NovedadesFilter.entries.forEach { filter ->
                            val countForChip = when (filter) {
                                NovedadesFilter.ALL -> newReleases.size
                                NovedadesFilter.ALBUMS -> newReleases.count { it.isAlbum }
                                NovedadesFilter.SINGLES -> newReleases.count { !it.isAlbum }
                            }
                            NovedadesFilterChip(
                                label = "${filter.label} ($countForChip)",
                                selected = selectedFilter == filter,
                                accentColor = accentColor,
                                appColors = appColors,
                                onClick = { selectedFilter = filter }
                            )
                        }
                    }
                    if (windowSizeClass.isMediumOrExpandedWidth) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            renderChips()
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            renderChips()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StaggeredGridItemFrame(
    index: Int,
    staggerKey: Any?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val entrance = remember(staggerKey) { Animatable(0f) }
    LaunchedEffect(staggerKey) {
        entrance.snapTo(0f)
        val delayMs = (index % 8) * 32L
        if (delayMs > 0L) {
            delay(delayMs)
        }
        entrance.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.54f,
                stiffness = 340f
            )
        )
    }

    Box(
        modifier = modifier.graphicsLayer {
            val p = entrance.value
            alpha = p.coerceIn(0f, 1f)
            val s = 0.85f + 0.15f * p
            scaleX = s
            scaleY = s
            translationY = (1f - p) * 36.dp.toPx()
        }
    ) {
        content()
    }
}

@Composable
private fun LiquidGlassBackPill(
    onClick: () -> Unit,
    appColors: AppThemeColors
) {
    val glassConfig = LocalGlassEffectConfig.current
    val useGlass = glassConfig.isEnabledFor(GlassComponent.NAV_BAR) && isGlassAllowed()
    val interactionSource = remember { MutableInteractionSource() }

    val surfaceModifier = if (useGlass) {
        Modifier.liquidGlass(
            config = glassConfig,
            shape = PillShape,
            highlightAlpha = 0.36f
        )
    } else {
        Modifier
            .clip(PillShape)
            .background(
                if (appColors.isDark) Color(0xCC1B1F2B) else Color(0xEBFFFFFF)
            )
            .border(
                width = 0.9.dp,
                color = if (appColors.isDark) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.10f),
                shape = PillShape
            )
    }

    val contentTint = if (useGlass) glassConfig.textColor else appColors.textPrimary

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .pressWobble(interactionSource)
            .height(42.dp)
            .clip(PillShape)
            .then(surfaceModifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 13.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "Back",
            tint = contentTint,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = "Home",
            color = contentTint,
            fontFamily = GoogleSans,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun CategoryTogglePill(
    category: HomeGridCategory,
    accentColor: Color,
    appColors: AppThemeColors,
    onClick: () -> Unit
) {
    val jellyState = rememberJellyPressState(pressedScaleX = 0.94f, pressedScaleY = 0.90f)
    val displayAccent = readableAccentColor(accentColor, appColors.isDark)
    val icon = if (category == HomeGridCategory.RADIO_STATIONS) {
        Icons.Rounded.AutoAwesome
    } else {
        Icons.Rounded.Radio
    }
    val nextLabel = if (category == HomeGridCategory.RADIO_STATIONS) "Releases" else "Radio"

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .jellyTapScale(jellyState)
            .clip(CircleShape)
            .background(displayAccent.copy(alpha = if (appColors.isDark) 0.20f else 0.14f))
            .border(1.dp, displayAccent.copy(alpha = 0.50f), CircleShape)
            .clickable(
                interactionSource = jellyState.interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 11.dp, vertical = 7.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = displayAccent,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = nextLabel,
            color = displayAccent,
            fontFamily = GoogleSans,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun NovedadesFilterChip(
    label: String,
    selected: Boolean,
    accentColor: Color,
    appColors: AppThemeColors,
    onClick: () -> Unit
) {
    val jellyState = rememberJellyPressState(pressedScaleX = 0.94f, pressedScaleY = 0.90f)
    val displayAccent = readableAccentColor(accentColor, appColors.isDark)

    Box(
        modifier = Modifier
            .jellyTapScale(jellyState)
            .clip(CircleShape)
            .background(
                if (selected) {
                    Brush.linearGradient(
                        colors = listOf(
                            displayAccent.copy(alpha = 0.28f),
                            displayAccent.copy(alpha = 0.14f)
                        )
                    )
                } else {
                    Brush.linearGradient(
                        colors = if (appColors.isDark) {
                            listOf(Color(0x99191D28), Color(0x80151821))
                        } else {
                            listOf(Color(0xE8FFFFFF), Color(0xD8F8FAFC))
                        }
                    )
                }
            )
            .border(
                width = 1.dp,
                color = if (selected) {
                    displayAccent.copy(alpha = 0.72f)
                } else if (appColors.isDark) {
                    Color.White.copy(alpha = 0.16f)
                } else {
                    Color.Black.copy(alpha = 0.09f)
                },
                shape = CircleShape
            )
            .clickable(
                interactionSource = jellyState.interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 13.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) displayAccent else appColors.textSecondary,
            fontFamily = if (selected) GoogleSans else PlusJakartaSans,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun GridEmptyState(
    category: HomeGridCategory,
    hasActiveFilter: Boolean,
    onResetFilter: () -> Unit,
    appColors: AppThemeColors,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val jellyState = rememberJellyPressState()
    val cardShape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .clip(cardShape)
                .background(if (appColors.isDark) Color(0xAA171A24) else Color(0xE8FFFFFF))
                .border(
                    width = 1.dp,
                    color = if (appColors.isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.08f),
                    shape = cardShape
                )
                .padding(24.dp)
        ) {
            Icon(
                imageVector = if (category == HomeGridCategory.RADIO_STATIONS) {
                    Icons.Rounded.Radio
                } else {
                    Icons.Rounded.AutoAwesome
                },
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(38.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (hasActiveFilter) {
                    "No items in this category"
                } else if (category == HomeGridCategory.RADIO_STATIONS) {
                    "No radio stations available"
                } else {
                    "No new releases available"
                },
                color = appColors.textPrimary,
                fontFamily = GoogleSans,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (hasActiveFilter) {
                    "Try selecting \"All\" to view all releases."
                } else {
                    "Listen to music or explore artists to refresh your recommendations."
                },
                color = appColors.textSecondary,
                fontFamily = PlusJakartaSans,
                fontSize = 12.5.sp,
                textAlign = TextAlign.Center
            )

            if (hasActiveFilter) {
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .jellyTapScale(jellyState)
                        .clip(CircleShape)
                        .background(accentColor)
                        .clickable(
                            interactionSource = jellyState.interactionSource,
                            indication = null,
                            onClick = onResetFilter
                        )
                        .padding(horizontal = 18.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = "View All",
                        color = onAccentColor(accentColor),
                        fontFamily = GoogleSans,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun RadioAndNovedadesPreviewContent(widthDp: Int, heightDp: Int) {
    AdaptivePreviewSurface(widthDp = widthDp, heightDp = heightDp) {
        RadioAndNovedadesGridScreen(
            category = HomeGridCategory.NOVEDADES,
            radioStations = AdaptivePreviewFixtures.radioStations,
            newReleases = AdaptivePreviewFixtures.newReleases,
            onBack = {},
            onRadioClick = {},
            onReleaseClick = {}
        )
    }
}

@Preview(name = "Phone (360x800)", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
private fun RadioAndNovedadesPhonePreview() = RadioAndNovedadesPreviewContent(360, 800)

@Preview(name = "Tablet Portrait (800x1280)", widthDp = 800, heightDp = 1280, showBackground = true)
@Composable
private fun RadioAndNovedadesTabletPortraitPreview() = RadioAndNovedadesPreviewContent(800, 1280)

@Preview(name = "Tablet Landscape (1280x800)", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
private fun RadioAndNovedadesTabletLandscapePreview() = RadioAndNovedadesPreviewContent(1280, 800)
