package com.xperience.player.ui.playlists

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xperience.player.domain.model.UserPlaylist
import com.xperience.player.ui.adaptive.AdaptivePreviewFixtures
import com.xperience.player.ui.adaptive.AdaptivePreviewSurface
import com.xperience.player.ui.adaptive.LocalAppWindowSizeClass
import com.xperience.player.ui.adaptive.LocalBottomChromePadding
import com.xperience.player.ui.adaptive.LocalTopChromePadding
import com.xperience.player.ui.adaptive.adaptiveGridMinSize
import com.xperience.player.ui.adaptive.usesTopDesktopChrome
import com.xperience.player.ui.component.GlassComponent
import com.xperience.player.ui.component.LocalAppBackdrop
import com.xperience.player.ui.component.LocalGlassEffectConfig
import com.xperience.player.ui.component.backdrop.backdrops.layerBackdrop
import com.xperience.player.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.xperience.player.ui.component.isGlassAllowed
import com.xperience.player.ui.component.liquidGlass
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.components.FrostedCardBadge
import com.xperience.player.ui.components.GlossyCardShell
import com.xperience.player.ui.components.HomeAuroraBackground
import com.xperience.player.ui.components.onAccentColor
import com.xperience.player.ui.components.readableAccentColor
import com.xperience.player.ui.components.rememberJellyPressState
import com.xperience.player.ui.theme.AuroraColors
import com.xperience.player.ui.theme.DEFAULT_AURORA_COLORS
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.PlusJakartaSans
import com.xperience.player.ui.utils.pressWobble
import kotlinx.coroutines.delay

private val PlaylistGridCardShape = ContinuousRoundedRectangle(size = 18.dp)
private val PlaylistPillShape = ContinuousRoundedRectangle(percent = 50)
private val EmptyEmblemShape = ContinuousRoundedRectangle(size = 26.dp)
private val EmptyCardShape = ContinuousRoundedRectangle(size = 24.dp)

/**
 * Playlists screen with dynamic Aurora mesh backdrop, floating LiquidGlass controls,
 * and a responsive 2-column glossy grid aligned with Home's album cards.
 */
@Composable
fun PlaylistsScreen(
    playlists: List<UserPlaylist>,
    onPlaylistClick: (UserPlaylist) -> Unit,
    onPlayPlaylistClick: (UserPlaylist) -> Unit,
    onCreatePlaylistClick: () -> Unit,
    modifier: Modifier = Modifier,
    auroraColors: AuroraColors = DEFAULT_AURORA_COLORS,
    applyStatusBarsPadding: Boolean = false,
    selectedPlaylistId: String? = null,
    suggestedPlaylists: List<UserPlaylist> = emptyList(),
    onSaveSuggestedPlaylist: (UserPlaylist) -> Unit = {},
    isYtmSignedIn: Boolean = false,
    onImportYtmPlaylistsClick: () -> Unit = {},
    onRequestDeletePlaylist: ((UserPlaylist) -> Unit)? = null,
    statusMessage: String? = null,
    onDismissStatusMessage: () -> Unit = {}
) {
    val appColors = LocalAppColors.current
    val isDark = appColors.isDark
    val accentColor = readableAccentColor(LocalAccentColor.current, isDark)
    val density = LocalDensity.current
    val topChromePadding = LocalTopChromePadding.current
    val bottomChromePadding = LocalBottomChromePadding.current

    val totalTracks = remember(playlists) { playlists.sumOf { it.tracks.size } }
    val totalDurationText = remember(playlists) {
        formatPlaylistsTotalDuration(playlists.sumOf { it.totalDurationMs })
    }

    LaunchedEffect(statusMessage) {
        if (!statusMessage.isNullOrBlank()) {
            delay(3600L)
            onDismissStatusMessage()
        }
    }

    val playlistsBackdrop = rememberLayerBackdrop()
    val gridState = rememberLazyGridState()
    val isScrolled by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 14
        }
    }

    var headerHeightPx by remember { mutableIntStateOf(0) }
    val fallbackHeaderHeight = (if (applyStatusBarsPadding) 168.dp else 130.dp) + topChromePadding
    val headerHeightDp = if (headerHeightPx > 0) {
        with(density) { headerHeightPx.toDp() }
    } else {
        fallbackHeaderHeight
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(appColors.background)
    ) {
        // Layer 1: Aurora ambient wash + grid content recorded by LiquidGlass header pills
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(playlistsBackdrop)
        ) {
            HomeAuroraBackground(
                auroraColors = auroraColors,
                isDark = isDark,
                scrollOffsetPxProvider = {
                    if (gridState.firstVisibleItemIndex == 0) {
                        gridState.firstVisibleItemScrollOffset
                    } else {
                        1200 + gridState.firstVisibleItemIndex * 220
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Subtle atmospheric accent bloom matching Library
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .drawBehind {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = if (isDark) 0.11f else 0.06f),
                                    Color.Transparent
                                ),
                                center = Offset(size.width * 0.84f, size.height * 0.08f),
                                radius = size.width * 0.92f
                            )
                        )
                    }
            )

            AnimatedContent(
                targetState = playlists.isEmpty() && suggestedPlaylists.isEmpty(),
                transitionSpec = {
                    (fadeIn(tween(240)) + scaleIn(initialScale = 0.97f, animationSpec = tween(240)))
                        .togetherWith(fadeOut(tween(160)))
                },
                label = "playlistsContentState",
                modifier = Modifier.fillMaxSize()
            ) { isCompletelyEmpty ->
                if (isCompletelyEmpty) {
                    PlaylistsEmptyState(
                        accentColor = accentColor,
                        topPadding = headerHeightDp + 16.dp,
                        onCreatePlaylistClick = onCreatePlaylistClick,
                        isYtmSignedIn = isYtmSignedIn,
                        onImportYtmPlaylistsClick = onImportYtmPlaylistsClick
                    )
                } else {
                    PlaylistsGridContent(
                        playlists = playlists,
                        suggestedPlaylists = suggestedPlaylists,
                        gridState = gridState,
                        topContentPadding = headerHeightDp + 12.dp,
                        accentColor = accentColor,
                        selectedPlaylistId = selectedPlaylistId,
                        isYtmSignedIn = isYtmSignedIn,
                        onPlaylistClick = onPlaylistClick,
                        onPlayPlaylistClick = onPlayPlaylistClick,
                        onSaveSuggestedPlaylist = onSaveSuggestedPlaylist,
                        onCreatePlaylistClick = onCreatePlaylistClick,
                        onImportYtmPlaylistsClick = onImportYtmPlaylistsClick,
                        onRequestDeletePlaylist = onRequestDeletePlaylist
                    )
                }
            }
        }

        // Layer 2: Soft top readability scrim + floating LiquidGlass header controls
        CompositionLocalProvider(LocalAppBackdrop provides playlistsBackdrop) {
            Box(modifier = Modifier.fillMaxSize()) {
                val scrimTopAlpha = if (isScrolled) {
                    if (isDark) 0.82f else 0.90f
                } else {
                    if (isDark) 0.44f else 0.62f
                }
                val scrimMidAlpha = if (isScrolled) {
                    if (isDark) 0.42f else 0.48f
                } else {
                    if (isDark) 0.16f else 0.24f
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(headerHeightDp + 32.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to appColors.background.copy(alpha = scrimTopAlpha),
                                    0.58f to appColors.background.copy(alpha = scrimMidAlpha),
                                    1.0f to Color.Transparent
                                )
                            )
                        )
                )

                PlaylistsFloatingHeader(
                    playlistCount = playlists.size,
                    totalTracks = totalTracks,
                    totalDurationText = totalDurationText,
                    accentColor = accentColor,
                    isScrolled = isScrolled,
                    applyStatusBarsPadding = applyStatusBarsPadding,
                    isYtmSignedIn = isYtmSignedIn,
                    onImportYtmPlaylistsClick = onImportYtmPlaylistsClick,
                    onCreatePlaylistClick = onCreatePlaylistClick,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .onSizeChanged { headerHeightPx = it.height }
                )

                if (!statusMessage.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 20.dp)
                            .padding(bottom = bottomChromePadding + 12.dp)
                            .shadow(12.dp, PlaylistPillShape)
                            .clip(PlaylistPillShape)
                            .background(
                                if (isDark) Color(0xEE181C26) else Color(0xF2FFFFFF)
                            )
                            .border(
                                0.9.dp,
                                accentColor.copy(alpha = 0.45f),
                                PlaylistPillShape
                            )
                            .clickable { onDismissStatusMessage() }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(17.dp)
                        )
                        Text(
                            text = statusMessage,
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp,
                            color = appColors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistsFloatingHeader(
    playlistCount: Int,
    totalTracks: Int,
    totalDurationText: String,
    accentColor: Color,
    isScrolled: Boolean,
    applyStatusBarsPadding: Boolean,
    isYtmSignedIn: Boolean,
    onImportYtmPlaylistsClick: () -> Unit,
    onCreatePlaylistClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val isDark = appColors.isDark
    val glassConfig = LocalGlassEffectConfig.current
    val useGlass = glassConfig.isEnabledFor(GlassComponent.NAV_BAR) && isGlassAllowed()
    val haptic = LocalHapticFeedback.current
    val ytmRed = if (isDark) YouTubeMusicBrandRedDark else YouTubeMusicBrandRedLight

    val subtitleText = when {
        playlistCount == 0 -> "Curate your personal collections"
        totalTracks == 0 -> if (playlistCount == 1) "1 empty playlist" else "$playlistCount playlists · 0 tracks"
        else -> {
            val playlistLabel = if (playlistCount == 1) "1 playlist" else "$playlistCount playlists"
            val trackLabel = if (totalTracks == 1) "1 track" else "$totalTracks tracks"
            "$playlistLabel · $trackLabel"
        }
    }

    val createInteraction = remember { MutableInteractionSource() }
    val importInteraction = remember { MutableInteractionSource() }
    val createElevation by animateDpAsState(
        targetValue = if (isScrolled) 10.dp else 4.dp,
        animationSpec = tween(220),
        label = "createPillElevation"
    )

    val windowSizeClass = LocalAppWindowSizeClass.current
    val topChromePadding = LocalTopChromePadding.current

    Column(
        modifier = modifier
            .then(if (applyStatusBarsPadding) Modifier.statusBarsPadding() else Modifier)
            .padding(top = topChromePadding)
            .padding(top = 10.dp, bottom = 6.dp)
    ) {
        // Right margin keeps clear of HomeScreen's top-right Settings GlassCircleButton on Compact/Medium
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 20.dp,
                    end = if (windowSizeClass.usesTopDesktopChrome) 20.dp else 68.dp
                )
        ) {
            Text(
                text = "Playlists",
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                letterSpacing = (-0.6).sp,
                color = appColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitleText,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = appColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Floating glass action & metadata strip below the Settings button height
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Frosted/LiquidGlass collection readout pill
            val infoBorderColor = if (isDark) {
                Color.White.copy(alpha = 0.14f)
            } else {
                appColors.textPrimary.copy(alpha = 0.10f)
            }
            val infoModifier = Modifier
                .weight(1f, fill = false)
                .height(38.dp)
                .then(
                    if (useGlass) {
                        Modifier.liquidGlass(
                            config = glassConfig,
                            shape = PlaylistPillShape,
                            highlightAlpha = 0.16f
                        )
                    } else {
                        Modifier
                            .clip(PlaylistPillShape)
                            .background(
                                if (isDark) Color(0xFF1A1E29).copy(alpha = 0.86f)
                                else Color.White.copy(alpha = 0.86f)
                            )
                    }
                )
                .border(0.75.dp, infoBorderColor, PlaylistPillShape)
                .padding(horizontal = 12.dp)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = infoModifier
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (totalTracks > 0) totalDurationText else "Ready to mix",
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = if (useGlass) appColors.glassTextPrimary else appColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (isYtmSignedIn) {
                val importModifier = Modifier
                    .pressWobble(importInteraction, pressedScale = 0.91f)
                    .height(38.dp)
                    .shadow(
                        elevation = createElevation,
                        shape = PlaylistPillShape,
                        ambientColor = ytmRed.copy(alpha = 0.25f),
                        spotColor = ytmRed.copy(alpha = 0.35f)
                    )
                    .then(
                        if (useGlass) {
                            Modifier
                                .liquidGlass(
                                    config = glassConfig,
                                    shape = PlaylistPillShape,
                                    highlightAlpha = 0.20f
                                )
                                .background(
                                    ytmRed.copy(alpha = if (isDark) 0.18f else 0.12f),
                                    PlaylistPillShape
                                )
                                .border(
                                    0.85.dp,
                                    ytmRed.copy(alpha = 0.48f),
                                    PlaylistPillShape
                                )
                        } else {
                            Modifier
                                .clip(PlaylistPillShape)
                                .background(
                                    if (isDark) Color(0xFF2B1720) else Color(0xFFFFEEF1)
                                )
                                .border(0.85.dp, ytmRed.copy(alpha = 0.45f), PlaylistPillShape)
                        }
                    )
                    .clickable(
                        interactionSource = importInteraction,
                        indication = ripple(bounded = true)
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onImportYtmPlaylistsClick()
                    }
                    .padding(horizontal = 12.dp)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = importModifier
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayCircle,
                        contentDescription = "Import YouTube Music playlists",
                        tint = ytmRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Import",
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                        color = if (useGlass) appColors.glassTextPrimary else appColors.textPrimary,
                        maxLines = 1
                    )
                }
            }

            // Polished LiquidGlass "New Playlist" CTA pill with press wobble
            val ctaInk = if (useGlass) appColors.glassTextPrimary else onAccentColor(accentColor)
            val ctaModifier = Modifier
                .pressWobble(createInteraction, pressedScale = 0.91f)
                .height(38.dp)
                .shadow(
                    elevation = createElevation,
                    shape = PlaylistPillShape,
                    ambientColor = accentColor.copy(alpha = 0.35f),
                    spotColor = accentColor.copy(alpha = 0.45f)
                )
                .then(
                    if (useGlass) {
                        Modifier
                            .liquidGlass(
                                config = glassConfig,
                                shape = PlaylistPillShape,
                                highlightAlpha = 0.24f
                            )
                            .background(
                                accentColor.copy(alpha = if (isDark) 0.22f else 0.16f),
                                PlaylistPillShape
                            )
                            .border(
                                0.85.dp,
                                Brush.linearGradient(
                                    listOf(
                                        Color.White.copy(alpha = if (isDark) 0.32f else 0.65f),
                                        accentColor.copy(alpha = 0.40f)
                                    )
                                ),
                                PlaylistPillShape
                            )
                    } else {
                        Modifier
                            .clip(PlaylistPillShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(accentColor, accentColor.copy(alpha = 0.84f))
                                )
                            )
                            .border(
                                0.8.dp,
                                Color.White.copy(alpha = if (isDark) 0.28f else 0.45f),
                                PlaylistPillShape
                            )
                    }
                )
                .clickable(
                    interactionSource = createInteraction,
                    indication = ripple(bounded = true)
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCreatePlaylistClick()
                }
                .padding(horizontal = 13.dp)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = ctaModifier
            ) {
                if (useGlass) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = onAccentColor(accentColor),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = ctaInk,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "New Playlist",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    color = ctaInk,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun PlaylistsGridContent(
    playlists: List<UserPlaylist>,
    suggestedPlaylists: List<UserPlaylist>,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    topContentPadding: Dp,
    accentColor: Color,
    selectedPlaylistId: String?,
    isYtmSignedIn: Boolean,
    onPlaylistClick: (UserPlaylist) -> Unit,
    onPlayPlaylistClick: (UserPlaylist) -> Unit,
    onSaveSuggestedPlaylist: (UserPlaylist) -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onImportYtmPlaylistsClick: () -> Unit,
    onRequestDeletePlaylist: ((UserPlaylist) -> Unit)?
) {
    val windowSizeClass = LocalAppWindowSizeClass.current
    val bottomChromePadding = LocalBottomChromePadding.current
    val appColors = LocalAppColors.current
    val savedTitles = remember(playlists) {
        playlists.map { it.title.trim().lowercase() }.toSet()
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(
            minSize = windowSizeClass.adaptiveGridMinSize(158.dp, 180.dp, 196.dp)
        ),
        state = gridState,
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = topContentPadding,
            bottom = bottomChromePadding
        ),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (suggestedPlaylists.isNotEmpty()) {
            item(
                key = "suggested_smart_playlists_section",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "suggested_banner"
            ) {
                SuggestedSmartPlaylistsBannerSection(
                    suggestedPlaylists = suggestedPlaylists,
                    savedTitles = savedTitles,
                    accentColor = accentColor,
                    selectedPlaylistId = selectedPlaylistId,
                    onPlaylistClick = onPlaylistClick,
                    onPlayPlaylistClick = onPlayPlaylistClick,
                    onSaveSuggestedPlaylist = onSaveSuggestedPlaylist
                )
            }

            item(
                key = "saved_playlists_section_header",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "section_header"
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Your Saved Playlists",
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = appColors.textPrimary
                    )
                    Text(
                        text = if (playlists.size == 1) "1 saved" else "${playlists.size} saved",
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = appColors.textSecondary
                    )
                }
            }
        }

        if (playlists.isEmpty()) {
            item(
                key = "saved_playlists_empty_inline",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "empty_state"
            ) {
                PlaylistsEmptyState(
                    accentColor = accentColor,
                    topPadding = 8.dp,
                    onCreatePlaylistClick = onCreatePlaylistClick,
                    isYtmSignedIn = isYtmSignedIn,
                    onImportYtmPlaylistsClick = onImportYtmPlaylistsClick
                )
            }
        } else {
            itemsIndexed(
                items = playlists,
                key = { _, playlist -> playlist.id },
                contentType = { _, _ -> "playlist_grid_card" }
            ) { index, playlist ->
                PlaylistGridCard(
                    playlist = playlist,
                    index = index,
                    accentColor = accentColor,
                    isSelected = selectedPlaylistId == playlist.id,
                    onClick = { onPlaylistClick(playlist) },
                    onPlayClick = { onPlayPlaylistClick(playlist) },
                    onDeleteClick = onRequestDeletePlaylist?.let { cb -> { cb(playlist) } },
                    modifier = Modifier.animateItem(
                        fadeInSpec = tween(260),
                        fadeOutSpec = tween(180),
                        placementSpec = spring(
                            dampingRatio = 0.82f,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                )
            }
        }
    }
}

@Composable
private fun SuggestedSmartPlaylistsBannerSection(
    suggestedPlaylists: List<UserPlaylist>,
    savedTitles: Set<String>,
    accentColor: Color,
    selectedPlaylistId: String?,
    onPlaylistClick: (UserPlaylist) -> Unit,
    onPlayPlaylistClick: (UserPlaylist) -> Unit,
    onSaveSuggestedPlaylist: (UserPlaylist) -> Unit
) {
    val appColors = LocalAppColors.current
    val isDark = appColors.isDark

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = if (isDark) 0.22f else 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Column {
                    Text(
                        text = "Suggested for You",
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.5.sp,
                        color = appColors.textPrimary
                    )
                    Text(
                        text = "Generated from your listening history • Save to keep them",
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.5.sp,
                        color = appColors.textSecondary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(PlaylistPillShape)
                    .background(accentColor.copy(alpha = if (isDark) 0.16f else 0.12f))
                    .border(0.75.dp, accentColor.copy(alpha = 0.40f), PlaylistPillShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Auto Mix",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.5.sp,
                    color = accentColor
                )
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(
                items = suggestedPlaylists,
                key = { it.id },
                contentType = { "suggested_playlist_card" }
            ) { suggestion ->
                val isSaved = savedTitles.contains(suggestion.title.trim().lowercase())
                val isSelected = selectedPlaylistId == suggestion.id
                SuggestedSmartPlaylistCard(
                    playlist = suggestion,
                    isSaved = isSaved,
                    isSelected = isSelected,
                    accentColor = accentColor,
                    onClick = { onPlaylistClick(suggestion) },
                    onPlayClick = { onPlayPlaylistClick(suggestion) },
                    onSaveClick = { onSaveSuggestedPlaylist(suggestion) }
                )
            }
        }
    }
}

@Composable
private fun SuggestedSmartPlaylistCard(
    playlist: UserPlaylist,
    isSaved: Boolean,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    val appColors = LocalAppColors.current
    val isDark = appColors.isDark
    val haptic = LocalHapticFeedback.current
    val cardJelly = rememberJellyPressState(pressedScaleX = 0.95f, pressedScaleY = 0.93f)
    val containerColor = if (isDark) Color(0xFF1A1D28) else Color(0xFFEBEEF5)
    val badgeText = playlist.suggestionBadge.ifBlank {
        if (playlist.tracks.size == 1) "1 song" else "${playlist.tracks.size} songs"
    }

    Column(
        modifier = Modifier.width(184.dp)
    ) {
        GlossyCardShell(
            shape = PlaylistGridCardShape,
            jellyState = cardJelly,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
            elevation = if (isDark) 10.dp else 7.dp,
            containerColor = containerColor,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .then(
                    if (isSelected) {
                        Modifier.border(2.dp, accentColor, PlaylistGridCardShape)
                    } else {
                        Modifier
                    }
                )
        ) {
            PlaylistCoverImage(
                playlist = playlist,
                shape = PlaylistGridCardShape,
                fallbackIconSize = 44.dp,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            brush = Brush.verticalGradient(
                                0.0f to Color.Black.copy(alpha = 0.25f),
                                0.35f to Color.Transparent,
                                1.0f to Color.Black.copy(alpha = 0.65f)
                            )
                        )
                    }
            )

            FrostedCardBadge(
                text = badgeText,
                icon = Icons.Rounded.AutoAwesome,
                dotColor = accentColor,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(9.dp)
            )

            // Explicit Save to Library action button (Bottom Start)
            val saveBg = if (isSaved) accentColor else Color.Black.copy(alpha = 0.65f)
            val saveFg = if (isSaved) onAccentColor(accentColor) else Color.White
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(9.dp)
                    .clip(PlaylistPillShape)
                    .background(saveBg)
                    .border(0.8.dp, if (isSaved) Color.White.copy(alpha = 0.5f) else accentColor.copy(alpha = 0.7f), PlaylistPillShape)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSaveClick()
                    }
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            ) {
                Icon(
                    imageVector = if (isSaved) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                    contentDescription = if (isSaved) "Saved" else "Save to Library",
                    tint = saveFg,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = if (isSaved) "Saved" else "Save",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.5.sp,
                    color = saveFg
                )
            }

            if (playlist.tracks.isNotEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(9.dp)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                        .border(0.9.dp, Color.White.copy(alpha = 0.44f), CircleShape)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onPlayClick()
                        }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play ${playlist.title}",
                        tint = onAccentColor(accentColor),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = playlist.title,
            fontFamily = GoogleSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.8.sp,
            color = appColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = playlist.description.ifBlank { playlist.summarySubtitle },
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.Medium,
            fontSize = 11.5.sp,
            color = appColors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Glossy playlist card matching Home's album card visual language:
 * specular rim shell, square cover art with subtle glass glaze, frosted track pill,
 * quick-play orb, and crisp below-cover typography hierarchy.
 */
@Composable
private fun PlaylistGridCard(
    playlist: UserPlaylist,
    index: Int,
    accentColor: Color,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    onDeleteClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val isDark = appColors.isDark
    val haptic = LocalHapticFeedback.current
    val cardJelly = rememberJellyPressState(pressedScaleX = 0.945f, pressedScaleY = 0.925f)
    var isMenuExpanded by remember { mutableStateOf(false) }

    // Subtle staggered entrance matching Home & Library grids
    val enterProgress = remember(playlist.id) { Animatable(0f) }
    LaunchedEffect(playlist.id) {
        if (enterProgress.value < 1f) {
            delay(((index % 6) * 32L).coerceAtMost(160L))
            enterProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
            )
        }
    }

    val containerColor = if (isDark) Color(0xFF1A1D28) else Color(0xFFEBEEF5)
    val trackBadgeLabel = if (playlist.tracks.size == 1) "1 track" else "${playlist.tracks.size} tracks"
    val destructiveTint = if (isDark) Color(0xFFFF6B7A) else Color(0xFFD92D20)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                val p = enterProgress.value
                alpha = 0.35f + 0.65f * p
                translationY = (1f - p) * 18f
                val s = 0.965f + 0.035f * p
                scaleX = s
                scaleY = s
            }
    ) {
        GlossyCardShell(
            shape = PlaylistGridCardShape,
            jellyState = cardJelly,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
            elevation = if (isDark) 10.dp else 7.dp,
            containerColor = containerColor,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .then(
                    if (isSelected) {
                        Modifier.border(2.dp, accentColor, PlaylistGridCardShape)
                    } else {
                        Modifier
                    }
                )
        ) {
            PlaylistCoverImage(
                playlist = playlist,
                shape = PlaylistGridCardShape,
                fallbackIconSize = 44.dp,
                modifier = Modifier.fillMaxSize()
            )

            // Subtle specular glaze on top edge + contrast vignette near bottom controls
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            brush = Brush.verticalGradient(
                                0.0f to Color.White.copy(alpha = if (isDark) 0.09f else 0.18f),
                                0.25f to Color.Transparent,
                                0.56f to Color.Transparent,
                                1.0f to Color.Black.copy(
                                    alpha = if (playlist.tracks.isNotEmpty()) 0.44f else 0.16f
                                )
                            )
                        )
                    }
            )

            // Frosted track count pill matching Home's FrostedCardBadge
            FrostedCardBadge(
                text = trackBadgeLabel,
                dotColor = if (playlist.tracks.isNotEmpty()) accentColor else null,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(9.dp)
            )

            // Clear YouTube Music source indicator pill for imported playlists
            if (playlist.isFromYouTubeMusic) {
                FrostedCardBadge(
                    text = "YT Music",
                    icon = Icons.Rounded.PlayCircle,
                    containerColor = Color(0xCC990F22),
                    contentColor = Color.White,
                    borderColor = Color(0x88FF5C77),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(9.dp)
                )
            }

            // Quick-play circular badge with tactile press wobble
            if (playlist.tracks.isNotEmpty()) {
                val playInteraction = remember { MutableInteractionSource() }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(9.dp)
                        .pressWobble(playInteraction, pressedScale = 0.84f)
                        .size(36.dp)
                        .shadow(
                            elevation = 8.dp,
                            shape = CircleShape,
                            ambientColor = accentColor.copy(alpha = 0.42f),
                            spotColor = accentColor.copy(alpha = 0.52f)
                        )
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(accentColor, accentColor.copy(alpha = 0.86f))
                            )
                        )
                        .border(0.9.dp, Color.White.copy(alpha = 0.44f), CircleShape)
                        .clickable(
                            interactionSource = playInteraction,
                            indication = ripple(bounded = true)
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onPlayClick()
                        }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play ${playlist.title}",
                        tint = onAccentColor(accentColor),
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Metadata hierarchy below cover matching Home's AlbumCard + subtle overflow menu
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 3.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.title,
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.5.sp,
                    color = appColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = playlist.description.ifBlank { playlist.summarySubtitle },
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = appColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (onDeleteClick != null) {
                Box {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isMenuExpanded = true
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Playlist options for ${playlist.title}",
                            tint = appColors.textSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = isMenuExpanded,
                        onDismissRequest = { isMenuExpanded = false },
                        containerColor = if (isDark) Color(0xFF1C202B) else Color.White
                    ) {
                        if (playlist.tracks.isNotEmpty()) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Play",
                                        fontFamily = GoogleSans,
                                        fontWeight = FontWeight.Medium,
                                        color = appColors.textPrimary
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    isMenuExpanded = false
                                    onPlayClick()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (playlist.isFromYouTubeMusic) "Remove from app" else "Delete playlist",
                                    fontFamily = GoogleSans,
                                    fontWeight = FontWeight.Medium,
                                    color = destructiveTint
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = null,
                                    tint = destructiveTint,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                isMenuExpanded = false
                                onDeleteClick()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistsEmptyState(
    accentColor: Color,
    topPadding: Dp,
    onCreatePlaylistClick: () -> Unit,
    isYtmSignedIn: Boolean = false,
    onImportYtmPlaylistsClick: () -> Unit = {}
) {
    val appColors = LocalAppColors.current
    val isDark = appColors.isDark
    val haptic = LocalHapticFeedback.current
    val buttonInteraction = remember { MutableInteractionSource() }
    val importInteraction = remember { MutableInteractionSource() }
    val ytmRed = if (isDark) YouTubeMusicBrandRedDark else YouTubeMusicBrandRedLight

    val cardFill = if (isDark) {
        Color.White.copy(alpha = 0.055f)
    } else {
        Color.White.copy(alpha = 0.72f)
    }
    val cardBorder = if (isDark) {
        Color.White.copy(alpha = 0.14f)
    } else {
        appColors.textPrimary.copy(alpha = 0.08f)
    }

    val bottomChromePadding = LocalBottomChromePadding.current

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topPadding, bottom = bottomChromePadding, start = 24.dp, end = 24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .clip(EmptyCardShape)
                .background(cardFill)
                .border(0.85.dp, cardBorder, EmptyCardShape)
                .padding(horizontal = 26.dp, vertical = 30.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(76.dp)
                    .shadow(
                        elevation = 14.dp,
                        shape = EmptyEmblemShape,
                        ambientColor = accentColor.copy(alpha = 0.30f),
                        spotColor = accentColor.copy(alpha = 0.35f)
                    )
                    .clip(EmptyEmblemShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                accentColor.copy(alpha = if (isDark) 0.24f else 0.16f),
                                accentColor.copy(alpha = if (isDark) 0.10f else 0.07f)
                            )
                        )
                    )
                    .border(
                        width = 0.9.dp,
                        color = accentColor.copy(alpha = 0.38f),
                        shape = EmptyEmblemShape
                    )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Your playlists live here",
                fontFamily = GoogleSans,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = appColors.textPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Collect favorite tracks, add custom cover art, and build mixes for every session.",
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Normal,
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
                color = appColors.textSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(22.dp))

            val buttonModifier = Modifier
                .pressWobble(buttonInteraction, pressedScale = 0.92f)
                .height(44.dp)
                .shadow(
                    elevation = 10.dp,
                    shape = PlaylistPillShape,
                    ambientColor = accentColor.copy(alpha = 0.36f),
                    spotColor = accentColor.copy(alpha = 0.44f)
                )
                .clip(PlaylistPillShape)
                .background(
                    Brush.linearGradient(
                        listOf(accentColor, accentColor.copy(alpha = 0.85f))
                    )
                )
                .border(0.85.dp, Color.White.copy(alpha = if (isDark) 0.28f else 0.42f), PlaylistPillShape)
                .clickable(
                    interactionSource = buttonInteraction,
                    indication = ripple(bounded = true)
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCreatePlaylistClick()
                }
                .padding(horizontal = 22.dp)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = buttonModifier
            ) {
                val ink = onAccentColor(accentColor)
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Create First Playlist",
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = ink
                )
            }

            if (isYtmSignedIn) {
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .pressWobble(importInteraction, pressedScale = 0.92f)
                        .height(42.dp)
                        .clip(PlaylistPillShape)
                        .background(ytmRed.copy(alpha = if (isDark) 0.18f else 0.10f))
                        .border(0.9.dp, ytmRed.copy(alpha = 0.45f), PlaylistPillShape)
                        .clickable(
                            interactionSource = importInteraction,
                            indication = ripple(bounded = true)
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onImportYtmPlaylistsClick()
                        }
                        .padding(horizontal = 20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayCircle,
                        contentDescription = null,
                        tint = ytmRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Import from YouTube Music",
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                        color = appColors.textPrimary
                    )
                }
            }
        }
    }
}

private fun formatPlaylistsTotalDuration(totalMs: Long): String {
    if (totalMs <= 0L) return "0 min"
    val totalMinutes = (totalMs / 60_000L).toInt().coerceAtLeast(1)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "$totalMinutes min"
    }
}

/**
 * Empty detail pane shown alongside the playlists list on Expanded widths when no playlist exists yet.
 */
@Composable
internal fun PlaylistsEmptyDetailPane(
    onCreatePlaylistClick: () -> Unit,
    modifier: Modifier = Modifier,
    isYtmSignedIn: Boolean = false,
    onImportYtmPlaylistsClick: () -> Unit = {}
) {
    val appColors = LocalAppColors.current
    val accentColor = readableAccentColor(LocalAccentColor.current, appColors.isDark)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(appColors.background),
        contentAlignment = Alignment.Center
    ) {
        PlaylistsEmptyState(
            accentColor = accentColor,
            topPadding = LocalTopChromePadding.current,
            onCreatePlaylistClick = onCreatePlaylistClick,
            isYtmSignedIn = isYtmSignedIn,
            onImportYtmPlaylistsClick = onImportYtmPlaylistsClick
        )
    }
}

@Preview(name = "Phone (360x800)", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
private fun PlaylistsPhonePreview() {
    AdaptivePreviewSurface(widthDp = 360, heightDp = 800) {
        PlaylistsScreen(
            playlists = AdaptivePreviewFixtures.playlists,
            onPlaylistClick = {},
            onPlayPlaylistClick = {},
            onCreatePlaylistClick = {},
            applyStatusBarsPadding = true
        )
    }
}

@Preview(name = "Tablet Portrait (800x1280)", widthDp = 800, heightDp = 1280, showBackground = true)
@Composable
private fun PlaylistsTabletPortraitPreview() {
    AdaptivePreviewSurface(widthDp = 800, heightDp = 1280) {
        PlaylistsScreen(
            playlists = AdaptivePreviewFixtures.playlists,
            onPlaylistClick = {},
            onPlayPlaylistClick = {},
            onCreatePlaylistClick = {},
            applyStatusBarsPadding = true
        )
    }
}

@Preview(name = "Tablet Landscape (1280x800)", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
private fun PlaylistsTabletLandscapePreview() {
    AdaptivePreviewSurface(widthDp = 1280, heightDp = 800) {
        PlaylistsScreen(
            playlists = AdaptivePreviewFixtures.playlists,
            selectedPlaylistId = AdaptivePreviewFixtures.playlists.first().id,
            onPlaylistClick = {},
            onPlayPlaylistClick = {},
            onCreatePlaylistClick = {},
            applyStatusBarsPadding = true
        )
    }
}
