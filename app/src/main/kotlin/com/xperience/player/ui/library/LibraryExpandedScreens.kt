package com.xperience.player.ui.library

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed as lazyListItemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as lazyGridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.xperience.player.domain.model.AlbumItem
import com.xperience.player.domain.model.ArtistItem
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.service.MediaNetworkClients
import com.xperience.player.ui.adaptive.LocalAppWindowSizeClass
import com.xperience.player.ui.adaptive.LocalBottomChromePadding
import com.xperience.player.ui.adaptive.LocalTopChromePadding
import com.xperience.player.ui.adaptive.adaptiveGridMinSize
import com.xperience.player.ui.adaptive.isMediumOrExpandedWidth
import com.xperience.player.ui.component.ExplicitBadge
import com.xperience.player.ui.component.GlassComponent
import com.xperience.player.ui.component.LocalAppBackdrop
import com.xperience.player.ui.component.LocalGlassEffectConfig
import com.xperience.player.ui.component.backdrop.backdrops.layerBackdrop
import com.xperience.player.ui.component.backdrop.backdrops.rememberBackdropFreeze
import com.xperience.player.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.xperience.player.ui.component.isGlassAllowed
import com.xperience.player.ui.component.liquidGlass
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.components.FrostedCardBadge
import com.xperience.player.ui.components.GlossyCardShell
import com.xperience.player.ui.components.HomeAuroraBackground
import com.xperience.player.ui.components.ProgressiveArtworkBlurScrim
import com.xperience.player.ui.components.expressiveGradientColors
import com.xperience.player.ui.components.jellyTapScale
import com.xperience.player.ui.components.onAccentColor
import com.xperience.player.ui.components.readableAccentColor
import com.xperience.player.ui.components.rememberJellyPressState
import com.xperience.player.ui.theme.AppThemeColors
import com.xperience.player.ui.theme.AuroraColors
import com.xperience.player.ui.theme.DEFAULT_AURORA_COLORS
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.PlusJakartaSans
import com.xperience.player.ui.utils.pressWobble
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Fullscreen or directory sub-views reachable from the Library tab.
 */
enum class LibrarySubView {
    RECENTLY_ADDED,
    ARTISTS,
    ALBUMS,
    SONGS
}

/**
 * Chronological bucket used by "Añadidos recientemente" expanded grid screen.
 */
data class AddedPeriodGroup(
    val label: String,
    val tracks: List<TrackItem>
)

/**
 * Alphabetical section (`A`..`Z` or `#`) used by the full Artists, Albums, and Songs directories.
 */
data class AlphabetSection<T>(
    val letter: String,
    val items: List<T>
)

private const val PERIOD_LAST_WEEK = "Added Last Week"
private const val PERIOD_LAST_MONTH = "Added Last Month"
private const val PERIOD_LAST_3_MONTHS = "Added Last 3 Months"
private const val PERIOD_LAST_6_MONTHS = "Added Last 6 Months"
private const val PERIOD_THIS_YEAR = "Added This Year"
private const val PERIOD_UNDATED = "Undated"

private val CardCornerShape = ContinuousRoundedRectangle(size = 20.dp)
private val QueueRowShape = ContinuousRoundedRectangle(size = 16.dp)
private val QueueArtworkShape = ContinuousRoundedRectangle(percent = 24)
private val GlassPillShape = ContinuousRoundedRectangle(percent = 50)
private val CombiningMarksRegex = Regex("\\p{Mn}+")

/**
 * Groups [tracks] chronologically from newest to oldest into Spanish recency periods:
 * - "Añadidos la última semana"
 * - "Añadido el último mes"
 * - "Añadido los últimos 3 meses"
 * - "Añadido los últimos 6 meses"
 * - "Añadido este año"
 * - Descending calendar years ("2025", "2024", "2023", ...)
 * - "Sin fecha" for tracks without a positive timestamp (`dateAddedMs <= 0L`)
 */
fun groupTracksByAddedPeriod(
    tracks: List<TrackItem>,
    nowMs: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault()
): List<AddedPeriodGroup> {
    if (tracks.isEmpty()) return emptyList()

    val nowZdt = Instant.ofEpochMilli(nowMs).atZone(zoneId)
    val oneWeekAgoMs = nowZdt.minusWeeks(1).toInstant().toEpochMilli()
    val oneMonthAgoMs = nowZdt.minusMonths(1).toInstant().toEpochMilli()
    val threeMonthsAgoMs = nowZdt.minusMonths(3).toInstant().toEpochMilli()
    val sixMonthsAgoMs = nowZdt.minusMonths(6).toInstant().toEpochMilli()
    val currentYear = nowZdt.year

    val sortedTracks = tracks.sortedByDescending { it.dateAddedMs }

    val lastWeek = mutableListOf<TrackItem>()
    val lastMonth = mutableListOf<TrackItem>()
    val last3Months = mutableListOf<TrackItem>()
    val last6Months = mutableListOf<TrackItem>()
    val thisYear = mutableListOf<TrackItem>()
    val olderByYear = sortedMapOf<Int, MutableList<TrackItem>>(compareByDescending { it })
    val undated = mutableListOf<TrackItem>()

    for (track in sortedTracks) {
        val addedMs = track.dateAddedMs
        if (addedMs <= 0L) {
            undated.add(track)
            continue
        }
        when {
            addedMs >= oneWeekAgoMs -> lastWeek.add(track)
            addedMs >= oneMonthAgoMs -> lastMonth.add(track)
            addedMs >= threeMonthsAgoMs -> last3Months.add(track)
            addedMs >= sixMonthsAgoMs -> last6Months.add(track)
            else -> {
                val addedYear = Instant.ofEpochMilli(addedMs).atZone(zoneId).year
                if (addedYear == currentYear) {
                    thisYear.add(track)
                } else {
                    olderByYear.getOrPut(addedYear) { mutableListOf() }.add(track)
                }
            }
        }
    }

    return buildList {
        if (lastWeek.isNotEmpty()) add(AddedPeriodGroup(PERIOD_LAST_WEEK, lastWeek))
        if (lastMonth.isNotEmpty()) add(AddedPeriodGroup(PERIOD_LAST_MONTH, lastMonth))
        if (last3Months.isNotEmpty()) add(AddedPeriodGroup(PERIOD_LAST_3_MONTHS, last3Months))
        if (last6Months.isNotEmpty()) add(AddedPeriodGroup(PERIOD_LAST_6_MONTHS, last6Months))
        if (thisYear.isNotEmpty()) add(AddedPeriodGroup(PERIOD_THIS_YEAR, thisYear))
        olderByYear.forEach { (year, yearTracks) ->
            add(AddedPeriodGroup(year.toString(), yearTracks))
        }
        if (undated.isNotEmpty()) add(AddedPeriodGroup(PERIOD_UNDATED, undated))
    }
}

/**
 * Strips diacritics/accents and extracts the alphabetical section key (`A`..`Z` or `#`).
 */
fun alphabetBucketKey(rawText: String): String {
    val normalized = normalizeSortKey(rawText)
    val firstChar = normalized.firstOrNull() ?: return "#"
    return if (firstChar in 'A'..'Z') firstChar.toString() else "#"
}

/**
 * Groups [items] alphabetically into `A`..`Z` sections followed by `#`, sorting items
 * case-insensitively within every section.
 */
fun <T> groupItemsAlphabetically(
    items: List<T>,
    keySelector: (T) -> String
): List<AlphabetSection<T>> {
    if (items.isEmpty()) return emptyList()

    val keyed = items.map { item ->
        val raw = keySelector(item)
        val sortKey = normalizeSortKey(raw)
        val firstChar = sortKey.firstOrNull()
        val bucket = if (firstChar != null && firstChar in 'A'..'Z') {
            firstChar.toString()
        } else {
            "#"
        }
        Triple(bucket, sortKey, item)
    }

    val grouped = keyed.groupBy { it.first }
    val result = mutableListOf<AlphabetSection<T>>()

    for (letter in DEFAULT_ALPHABET_INDEX) {
        val entries = grouped[letter] ?: continue
        val sortedItems = entries
            .sortedWith(compareBy<Triple<String, String, T>> { it.second }.thenBy { keySelector(it.third).lowercase(Locale.ROOT) })
            .map { it.third }
        if (sortedItems.isNotEmpty()) {
            result.add(AlphabetSection(letter = letter, items = sortedItems))
        }
    }

    return result
}

private fun normalizeSortKey(rawText: String): String {
    val trimmed = rawText.trim()
    if (trimmed.isEmpty()) return ""
    val decomposed = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
    return CombiningMarksRegex.replace(decomposed, "").uppercase(Locale.ROOT)
}

/**
 * Fullscreen chronological "Añadidos recientemente" view styled like Home's Radio/Novedades grid:
 * Aurora background, floating LiquidGlass back header, staggered spring entrance, and progressive
 * artwork blur scrims grouped chronologically by recency period.
 */
@Composable
fun RecentlyAddedExpandedScreen(
    tracks: List<TrackItem>,
    onTrackClick: (TrackItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    currentPlayingTrackId: String? = null,
    isPlaybackPlaying: Boolean = false,
    auroraColors: AuroraColors = DEFAULT_AURORA_COLORS,
    onTrackMoreClick: ((TrackItem) -> Unit)? = null,
    likedTrackIds: Set<String> = emptySet(),
    onToggleFavorite: ((TrackItem) -> Unit)? = null,
    applyStatusBarsPadding: Boolean = true,
    onPlayTrackFromList: ((List<TrackItem>, Int) -> Unit)? = null
) {
    val appColors = LocalAppColors.current
    val accentColor = LocalAccentColor.current
    val displayAccent = readableAccentColor(accentColor, appColors.isDark)
    val gridState = rememberLazyGridState()
    val groups = remember(tracks) { groupTracksByAddedPeriod(tracks) }
    val orderedTracks = remember(groups) { groups.flatMap { it.tracks } }

    var backSwipeProgress by remember { mutableFloatStateOf(0f) }
    var isBackGestureActive by remember { mutableStateOf(false) }

    PredictiveBackHandler { progressFlow ->
        isBackGestureActive = true
        try {
            progressFlow.collect { backEvent ->
                backSwipeProgress = backEvent.progress.coerceIn(0f, 1f)
            }
            onBack()
        } catch (e: CancellationException) {
            backSwipeProgress = 0f
            isBackGestureActive = false
            throw e
        } finally {
            isBackGestureActive = false
            backSwipeProgress = 0f
        }
    }

    val animatedSwipeScale by animateFloatAsState(
        targetValue = if (isBackGestureActive) (1f - 0.08f * backSwipeProgress) else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 460f),
        label = "recAddedBackScale"
    )
    val animatedCornerDp by animateFloatAsState(
        targetValue = if (isBackGestureActive) (28f * backSwipeProgress) else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 460f),
        label = "recAddedBackRadius"
    )

    val screenBackdrop = rememberLayerBackdrop {
        drawRect(appColors.background)
        drawContent()
    }

    val windowSizeClass = LocalAppWindowSizeClass.current
    val topChromePadding = LocalTopChromePadding.current
    val bottomChromePadding = LocalBottomChromePadding.current
    val statusBarTop = if (applyStatusBarsPadding) {
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    } else {
        0.dp
    }
    val topContentPadding = statusBarTop + topChromePadding + 72.dp

    val scrollOffsetPx by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex * 220 + gridState.firstVisibleItemScrollOffset
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = animatedSwipeScale
                scaleY = animatedSwipeScale
                clip = animatedCornerDp > 0.5f
                shape = RoundedCornerShape(animatedCornerDp.dp)
            }
            .background(appColors.background)
    ) {
        // Layer 1: Aurora + ambient glow + chronological staggered grid (recorded by screenBackdrop)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(screenBackdrop)
        ) {
            HomeAuroraBackground(
                auroraColors = auroraColors,
                isDark = appColors.isDark,
                scrollOffsetPxProvider = { scrollOffsetPx },
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = if (appColors.isDark) 0.16f else 0.10f),
                                    Color.Transparent
                                ),
                                center = Offset(size.width * 0.80f, size.height * 0.14f),
                                radius = size.width * 0.72f
                            )
                        )
                    }
            )

            if (groups.isEmpty()) {
                LibraryEmptyDirectoryCard(
                    icon = Icons.Rounded.History,
                    title = "No recent additions",
                    subtitle = "Add local music or stream songs online to build your chronological history.",
                    appColors = appColors,
                    accentColor = accentColor,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topContentPadding, bottom = bottomChromePadding, start = 24.dp, end = 24.dp)
                )
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(
                        minSize = windowSizeClass.adaptiveGridMinSize(158.dp, 180.dp, 196.dp)
                    ),
                    contentPadding = PaddingValues(
                        start = 18.dp,
                        end = 18.dp,
                        top = topContentPadding,
                        bottom = bottomChromePadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    var runningIndex = 0
                    groups.forEach { group ->
                        item(
                            key = "period_header_${group.label}",
                            span = { GridItemSpan(maxLineSpan) },
                            contentType = "PeriodHeader"
                        ) {
                            LibrarySectionDividerHeader(
                                title = group.label,
                                countText = "${group.tracks.size}",
                                leadingIcon = Icons.Rounded.History,
                                appColors = appColors,
                                accentColor = accentColor,
                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                            )
                        }

                        val startIdx = runningIndex
                        runningIndex += group.tracks.size

                        lazyGridItemsIndexed(
                            items = group.tracks,
                            key = { idx, track -> "rec_${group.label}_${track.id}_$idx" },
                            contentType = { _, _ -> "RecentlyAddedGridCard" }
                        ) { idx, track ->
                            LibraryStaggeredItemFrame(
                                index = startIdx + idx,
                                staggerKey = tracks.size,
                                modifier = Modifier.animateItem(
                                    fadeInSpec = tween(220),
                                    placementSpec = spring(
                                        dampingRatio = 0.68f,
                                        stiffness = Spring.StiffnessMediumLow
                                    ),
                                    fadeOutSpec = tween(180)
                                )
                            ) {
                                LibraryRecentlyAddedCard(
                                    track = track,
                                    isCurrent = track.id == currentPlayingTrackId,
                                    isPlaying = isPlaybackPlaying && track.id == currentPlayingTrackId,
                                    appColors = appColors,
                                    accentColor = accentColor,
                                    onClick = {
                                        if (onPlayTrackFromList != null) {
                                            onPlayTrackFromList(orderedTracks, startIdx + idx)
                                        } else {
                                            onTrackClick(track)
                                        }
                                    },
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

        // Top soft fade scrim behind floating LiquidGlass header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(topContentPadding + 16.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            appColors.background.copy(alpha = 0.86f),
                            appColors.background.copy(alpha = 0.50f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Floating LiquidGlass Header Bar
        CompositionLocalProvider(LocalAppBackdrop provides screenBackdrop) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .then(if (applyStatusBarsPadding) Modifier.statusBarsPadding() else Modifier)
                    .padding(top = topChromePadding)
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                LibraryLiquidGlassBackPill(
                    label = "Library",
                    onClick = onBack,
                    appColors = appColors
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Recently Added",
                        color = appColors.textPrimary,
                        fontFamily = GoogleSans,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${tracks.size} songs sorted chronologically",
                        color = appColors.textSecondary,
                        fontFamily = PlusJakartaSans,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(displayAccent.copy(alpha = if (appColors.isDark) 0.22f else 0.14f))
                        .border(0.9.dp, displayAccent.copy(alpha = 0.45f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "${tracks.size}",
                        color = displayAccent,
                        fontFamily = GoogleSans,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Full Alphabetical Directory for `Artists` (3-col grid), `Albums` (2-col grid), or
 * `Songs` (Playing-Queue-style card list), complete with alphabetical section headers (`A`..`Z`, `#`)
 * and the right-side physical [WaveAlphabetScrubber].
 */
@Composable
internal fun LibraryAlphabeticalDirectory(
    subView: LibrarySubView,
    artists: List<ArtistItem>,
    albums: List<AlbumItem>,
    songs: List<TrackItem>,
    currentPlayingTrackId: String?,
    isPlaybackPlaying: Boolean,
    likedTrackIds: Set<String>,
    onToggleFavorite: ((TrackItem) -> Unit)?,
    onArtistClick: (ArtistItem) -> Unit,
    onAlbumClick: (AlbumItem) -> Unit,
    onTrackClick: (TrackItem) -> Unit,
    onSongMoreClick: (TrackItem) -> Unit,
    topContentPadding: Dp,
    appColors: AppThemeColors,
    accentColor: Color,
    onScrollOffsetChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    onPlayTrackFromList: ((List<TrackItem>, Int) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    var activeScrubJob by remember { mutableStateOf<Job?>(null) }
    val windowSizeClass = LocalAppWindowSizeClass.current
    val bottomChromePadding = LocalBottomChromePadding.current

    when (subView) {
        LibrarySubView.ARTISTS -> {
            val sections = remember(artists) { groupItemsAlphabetically(artists) { it.name } }
            if (sections.isEmpty()) {
                LibraryEmptyDirectoryCard(
                    icon = Icons.Rounded.Person,
                    title = "No artists yet",
                    subtitle = "Change the source or scan your storage to browse the alphabetical directory.",
                    appColors = appColors,
                    accentColor = accentColor,
                    modifier = modifier
                        .widthIn(max = 520.dp)
                        .padding(top = topContentPadding, bottom = bottomChromePadding, start = 20.dp, end = 20.dp)
                )
                return
            }

            val gridState = rememberLazyGridState()
            val (letterToFlatIndex, headerPositions) = remember(sections) {
                buildSectionIndexMaps(sections)
            }
            val availableLetters = remember(sections) { sections.map { it.letter }.toSet() }
            val activeLetter by remember(headerPositions) {
                derivedStateOf {
                    val firstIdx = gridState.firstVisibleItemIndex
                    headerPositions.lastOrNull { it.first <= firstIdx }?.second ?: headerPositions.firstOrNull()?.second
                }
            }

            val scrollPx by remember {
                derivedStateOf { gridState.firstVisibleItemIndex * 180 + gridState.firstVisibleItemScrollOffset }
            }
            LaunchedEffect(scrollPx) { onScrollOffsetChanged(scrollPx) }

            AlphabetScrubberDirectoryScaffold(
                availableLetters = availableLetters,
                activeLetter = activeLetter,
                topContentPadding = topContentPadding,
                onLetterSelected = { letter ->
                    val targetIdx = letterToFlatIndex[letter] ?: return@AlphabetScrubberDirectoryScaffold
                    activeScrubJob?.cancel()
                    activeScrubJob = scope.launch {
                        gridState.scrollToItem(targetIdx)
                    }
                },
                modifier = modifier
            ) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(
                        minSize = windowSizeClass.adaptiveGridMinSize(108.dp, 128.dp, 144.dp)
                    ),
                    contentPadding = PaddingValues(
                        start = 18.dp,
                        end = 42.dp,
                        top = topContentPadding,
                        bottom = bottomChromePadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    var runningIndex = 0
                    sections.forEach { section ->
                        item(
                            key = "artists_header_${section.letter}",
                            span = { GridItemSpan(maxLineSpan) },
                            contentType = "AlphabetHeader"
                        ) {
                            LibraryAlphabetSectionHeader(
                                letter = section.letter,
                                count = section.items.size,
                                appColors = appColors,
                                accentColor = accentColor,
                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                            )
                        }

                        val startIdx = runningIndex
                        runningIndex += section.items.size

                        lazyGridItemsIndexed(
                            items = section.items,
                            key = { idx, artist -> "dir_artist_${section.letter}_${artist.id}_$idx" },
                            contentType = { _, _ -> "ArtistCircleCard" }
                        ) { idx, artist ->
                            LibraryStaggeredItemFrame(
                                index = startIdx + idx,
                                staggerKey = artists.size
                            ) {
                                LibraryArtistCircleCard(
                                    artist = artist,
                                    appColors = appColors,
                                    accentColor = accentColor,
                                    onClick = { onArtistClick(artist) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }

        LibrarySubView.ALBUMS -> {
            val sections = remember(albums) { groupItemsAlphabetically(albums) { it.title } }
            if (sections.isEmpty()) {
                LibraryEmptyDirectoryCard(
                    icon = Icons.Rounded.Album,
                    title = "No albums yet",
                    subtitle = "Change the source or scan your library to view albums grouped A to Z.",
                    appColors = appColors,
                    accentColor = accentColor,
                    modifier = modifier.padding(top = topContentPadding, bottom = 152.dp, start = 20.dp, end = 20.dp)
                )
                return
            }

            val gridState = rememberLazyGridState()
            val (letterToFlatIndex, headerPositions) = remember(sections) {
                buildSectionIndexMaps(sections)
            }
            val availableLetters = remember(sections) { sections.map { it.letter }.toSet() }
            val activeLetter by remember(headerPositions) {
                derivedStateOf {
                    val firstIdx = gridState.firstVisibleItemIndex
                    headerPositions.lastOrNull { it.first <= firstIdx }?.second ?: headerPositions.firstOrNull()?.second
                }
            }

            val scrollPx by remember {
                derivedStateOf { gridState.firstVisibleItemIndex * 220 + gridState.firstVisibleItemScrollOffset }
            }
            LaunchedEffect(scrollPx) { onScrollOffsetChanged(scrollPx) }

            AlphabetScrubberDirectoryScaffold(
                availableLetters = availableLetters,
                activeLetter = activeLetter,
                topContentPadding = topContentPadding,
                onLetterSelected = { letter ->
                    val targetIdx = letterToFlatIndex[letter] ?: return@AlphabetScrubberDirectoryScaffold
                    activeScrubJob?.cancel()
                    activeScrubJob = scope.launch {
                        gridState.scrollToItem(targetIdx)
                    }
                },
                modifier = modifier
            ) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(
                        minSize = windowSizeClass.adaptiveGridMinSize(158.dp, 180.dp, 196.dp)
                    ),
                    contentPadding = PaddingValues(
                        start = 18.dp,
                        end = 42.dp,
                        top = topContentPadding,
                        bottom = bottomChromePadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    var runningIndex = 0
                    sections.forEach { section ->
                        item(
                            key = "albums_header_${section.letter}",
                            span = { GridItemSpan(maxLineSpan) },
                            contentType = "AlphabetHeader"
                        ) {
                            LibraryAlphabetSectionHeader(
                                letter = section.letter,
                                count = section.items.size,
                                appColors = appColors,
                                accentColor = accentColor,
                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                            )
                        }

                        val startIdx = runningIndex
                        runningIndex += section.items.size

                        lazyGridItemsIndexed(
                            items = section.items,
                            key = { idx, album -> "dir_album_${section.letter}_${album.id}_$idx" },
                            contentType = { _, _ -> "AlbumCard" }
                        ) { idx, album ->
                            LibraryStaggeredItemFrame(
                                index = startIdx + idx,
                                staggerKey = albums.size
                            ) {
                                LibraryAlbumCard(
                                    album = album,
                                    appColors = appColors,
                                    accentColor = accentColor,
                                    onClick = { onAlbumClick(album) },
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

        LibrarySubView.SONGS -> {
            val sections = remember(songs) { groupItemsAlphabetically(songs) { it.title } }
            val orderedSongs = remember(sections) { sections.flatMap { it.items } }
            if (sections.isEmpty()) {
                LibraryEmptyDirectoryCard(
                    icon = Icons.Rounded.MusicNote,
                    title = "No songs yet",
                    subtitle = "Change the source filter or scan local folders to list your music.",
                    appColors = appColors,
                    accentColor = accentColor,
                    modifier = modifier
                        .widthIn(max = 520.dp)
                        .padding(top = topContentPadding, bottom = bottomChromePadding, start = 20.dp, end = 20.dp)
                )
                return
            }

            val songColumns = if (windowSizeClass.isMediumOrExpandedWidth) 2 else 1
            val listState = rememberLazyListState()
            val (letterToFlatIndex, headerPositions) = remember(sections, songColumns) {
                if (songColumns == 1) {
                    buildSectionIndexMaps(sections)
                } else {
                    val map = LinkedHashMap<String, Int>(sections.size)
                    val positions = ArrayList<Pair<Int, String>>(sections.size)
                    var flatIndex = 0
                    for (section in sections) {
                        map[section.letter] = flatIndex
                        positions.add(flatIndex to section.letter)
                        flatIndex += 1 + section.items.chunked(songColumns).size
                    }
                    map to positions
                }
            }
            val availableLetters = remember(sections) { sections.map { it.letter }.toSet() }
            val activeLetter by remember(headerPositions) {
                derivedStateOf {
                    val firstIdx = listState.firstVisibleItemIndex
                    headerPositions.lastOrNull { it.first <= firstIdx }?.second ?: headerPositions.firstOrNull()?.second
                }
            }

            val scrollPx by remember {
                derivedStateOf { listState.firstVisibleItemIndex * 84 + listState.firstVisibleItemScrollOffset }
            }
            LaunchedEffect(scrollPx) { onScrollOffsetChanged(scrollPx) }

            AlphabetScrubberDirectoryScaffold(
                availableLetters = availableLetters,
                activeLetter = activeLetter,
                topContentPadding = topContentPadding,
                onLetterSelected = { letter ->
                    val targetIdx = letterToFlatIndex[letter] ?: return@AlphabetScrubberDirectoryScaffold
                    activeScrubJob?.cancel()
                    activeScrubJob = scope.launch {
                        listState.scrollToItem(targetIdx)
                    }
                },
                modifier = modifier
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 42.dp,
                        top = topContentPadding,
                        bottom = bottomChromePadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    var runningIndex = 0
                    sections.forEach { section ->
                        val startIdx = runningIndex
                        runningIndex += section.items.size
                        item(
                            key = "songs_header_${section.letter}",
                            contentType = "AlphabetHeader"
                        ) {
                            LibraryAlphabetSectionHeader(
                                letter = section.letter,
                                count = section.items.size,
                                appColors = appColors,
                                accentColor = accentColor,
                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                            )
                        }

                        if (songColumns == 1) {
                            lazyListItemsIndexed(
                                items = section.items,
                                key = { idx, track -> "dir_song_${section.letter}_${track.id}_$idx" },
                                contentType = { _, _ -> "QueueSongRow" }
                            ) { idx, track ->
                                val isCurrent = track.id == currentPlayingTrackId
                                LibraryQueueSongRow(
                                    track = track,
                                    isCurrent = isCurrent,
                                    isPlaying = isPlaybackPlaying && isCurrent,
                                    isFavorite = track.id in likedTrackIds || track.isLiked,
                                    onToggleFavorite = onToggleFavorite?.let { callback -> { callback(track) } },
                                    onClick = {
                                        if (onPlayTrackFromList != null) {
                                            onPlayTrackFromList(orderedSongs, startIdx + idx)
                                        } else {
                                            onTrackClick(track)
                                        }
                                    },
                                    onMoreClick = { onSongMoreClick(track) },
                                    appColors = appColors,
                                    accentColor = accentColor
                                )
                            }
                        } else {
                            val rowChunks = section.items.chunked(songColumns)
                            lazyListItemsIndexed(
                                items = rowChunks,
                                key = { rowIdx, _ -> "dir_song_row_${section.letter}_$rowIdx" },
                                contentType = { _, _ -> "QueueSongGridRow" }
                            ) { rowIdx, rowTracks ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    rowTracks.forEachIndexed { colIdx, track ->
                                        val actualIdx = startIdx + rowIdx * songColumns + colIdx
                                        val isCurrent = track.id == currentPlayingTrackId
                                        LibraryQueueSongRow(
                                            track = track,
                                            isCurrent = isCurrent,
                                            isPlaying = isPlaybackPlaying && isCurrent,
                                            isFavorite = track.id in likedTrackIds || track.isLiked,
                                            onToggleFavorite = onToggleFavorite?.let { callback -> { callback(track) } },
                                            onClick = {
                                                if (onPlayTrackFromList != null) {
                                                    onPlayTrackFromList(orderedSongs, actualIdx)
                                                } else {
                                                    onTrackClick(track)
                                                }
                                            },
                                            onMoreClick = { onSongMoreClick(track) },
                                            appColors = appColors,
                                            accentColor = accentColor,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    repeat(songColumns - rowTracks.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        LibrarySubView.RECENTLY_ADDED -> Unit
    }
}

@Composable
private fun AlphabetScrubberDirectoryScaffold(
    availableLetters: Set<String>,
    activeLetter: String?,
    topContentPadding: Dp,
    onLetterSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val directoryBackdrop = rememberLayerBackdrop()
    val backdropFreeze = rememberBackdropFreeze()
    Box(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(backdropFreeze.connection)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(directoryBackdrop, frozen = backdropFreeze.frozen)
        ) {
            content()
        }
        CompositionLocalProvider(LocalAppBackdrop provides directoryBackdrop) {
            WaveAlphabetScrubber(
                availableLetters = availableLetters,
                activeLetter = activeLetter,
                onLetterSelected = onLetterSelected,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(top = topContentPadding + 4.dp, bottom = LocalBottomChromePadding.current, end = 4.dp)
            )
        }
    }
}

private fun <T> buildSectionIndexMaps(
    sections: List<AlphabetSection<T>>
): Pair<Map<String, Int>, List<Pair<Int, String>>> {
    val map = LinkedHashMap<String, Int>(sections.size)
    val positions = ArrayList<Pair<Int, String>>(sections.size)
    var flatIndex = 0
    for (section in sections) {
        map[section.letter] = flatIndex
        positions.add(flatIndex to section.letter)
        flatIndex += 1 + section.items.size
    }
    return map to positions
}

// =============================================================================
// SHARED LIBRARY CARDS & QUEUE-STYLE SONG ROW
// =============================================================================

@Composable
internal fun LibraryRecentlyAddedCard(
    track: TrackItem,
    isCurrent: Boolean,
    isPlaying: Boolean,
    appColors: AppThemeColors,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val jellyState = rememberJellyPressState(pressedScaleX = 0.955f, pressedScaleY = 0.92f)
    val context = LocalContext.current
    val sharedImageLoader = remember(context) {
        MediaNetworkClients.getImageLoader(context.applicationContext)
    }
    val gradientColors = remember(track.id) { expressiveGradientColors(track.id) }
    val displayAccent = readableAccentColor(accentColor, appColors.isDark)
    var uriLoadFailed by remember(track.artworkUri) { mutableStateOf(false) }

    val artworkModel: Any? = remember(track.artworkResId, track.artworkUri) {
        track.artworkResId ?: track.artworkUri?.takeIf { it.isNotBlank() }
    }

    GlossyCardShell(
        shape = CardCornerShape,
        jellyState = jellyState,
        onClick = onClick,
        elevation = if (isCurrent) 12.dp else 7.dp,
        containerColor = if (appColors.isDark) Color(0xFF151924) else Color(0xFFE7ECF5),
        modifier = modifier
    ) {
        // Artwork + gradient fallback
        if (track.artworkResId != null) {
            Image(
                painter = painterResource(id = track.artworkResId),
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (!track.artworkUri.isNullOrBlank() && !uriLoadFailed) {
            AsyncImage(
                model = ImageRequest.Builder(context.applicationContext)
                    .data(track.artworkUri)
                    .crossfade(200)
                    .build(),
                imageLoader = sharedImageLoader,
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                onError = { uriLoadFailed = true },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(gradientColors)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.42f),
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        // Real ProgressiveArtworkBlurScrim behind bottom text metadata
        ProgressiveArtworkBlurScrim(
            artworkModel = artworkModel,
            blurRadius = 22.dp,
            tintColor = Color.Black,
            blurStartFraction = 0.46f,
            scrimTopAlpha = 0.04f,
            scrimBottomAlpha = 0.80f,
            modifier = Modifier.fillMaxSize()
        )

        // Subtle top-left playing badge or duration pill
        if (isCurrent) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(CircleShape)
                    .background(displayAccent.copy(alpha = 0.88f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                LibraryEqualizerBars(
                    color = onAccentColor(displayAccent),
                    isPlaying = isPlaying,
                    barHeight = 11.dp
                )
            }
        } else if (track.durationMs > 0L) {
            FrostedCardBadge(
                text = track.formattedDuration,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(9.dp)
            )
        }

        // Bottom track title + artist metadata over blurred scrim
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = track.title,
                    color = Color.White,
                    fontFamily = GoogleSans,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (track.isExplicit) {
                    ExplicitBadge(
                        tint = Color.White,
                        compact = true
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.artist,
                color = Color.White.copy(alpha = 0.78f),
                fontFamily = PlusJakartaSans,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isCurrent) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.4.dp, displayAccent.copy(alpha = 0.85f), CardCornerShape)
            )
        }
    }
}

@Composable
internal fun LibraryAlbumCard(
    album: AlbumItem,
    appColors: AppThemeColors,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val jellyState = rememberJellyPressState(pressedScaleX = 0.955f, pressedScaleY = 0.92f)
    val context = LocalContext.current
    val sharedImageLoader = remember(context) {
        MediaNetworkClients.getImageLoader(context.applicationContext)
    }
    val gradientColors = remember(album.id) { expressiveGradientColors(album.id) }
    var uriLoadFailed by remember(album.coverUri) { mutableStateOf(false) }

    val artworkModel: Any? = remember(album.coverResId, album.coverUri) {
        album.coverResId ?: album.coverUri?.takeIf { it.isNotBlank() }
    }

    GlossyCardShell(
        shape = CardCornerShape,
        jellyState = jellyState,
        onClick = onClick,
        elevation = 8.dp,
        containerColor = if (appColors.isDark) Color(0xFF151924) else Color(0xFFE7ECF5),
        modifier = modifier
    ) {
        if (album.coverResId != null) {
            Image(
                painter = painterResource(id = album.coverResId),
                contentDescription = album.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (!album.coverUri.isNullOrBlank() && !uriLoadFailed) {
            AsyncImage(
                model = ImageRequest.Builder(context.applicationContext)
                    .data(album.coverUri)
                    .crossfade(200)
                    .build(),
                imageLoader = sharedImageLoader,
                contentDescription = album.title,
                contentScale = ContentScale.Crop,
                onError = { uriLoadFailed = true },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(gradientColors)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Album,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.44f),
                    modifier = Modifier.size(46.dp)
                )
            }
        }

        // Progressive artwork blur behind bottom title/artist overlay
        ProgressiveArtworkBlurScrim(
            artworkModel = artworkModel,
            blurRadius = 22.dp,
            tintColor = Color.Black,
            blurStartFraction = 0.44f,
            scrimTopAlpha = 0.02f,
            scrimBottomAlpha = 0.80f,
            modifier = Modifier.fillMaxSize()
        )

        // Subtle track count pill at top-right
        if (album.tracksCount > 0) {
            FrostedCardBadge(
                text = "${album.tracksCount} tracks",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(9.dp)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp)
        ) {
            Text(
                text = album.title,
                color = Color.White,
                fontFamily = GoogleSans,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (album.year > 0) "${album.artist} • ${album.year}" else album.artist,
                color = Color.White.copy(alpha = 0.78f),
                fontFamily = PlusJakartaSans,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun LibraryArtistCircleCard(
    artist: ArtistItem,
    appColors: AppThemeColors,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 88.dp
) {
    val jellyState = rememberJellyPressState(pressedScaleX = 0.94f, pressedScaleY = 0.90f)
    val context = LocalContext.current
    val sharedImageLoader = remember(context) {
        MediaNetworkClients.getImageLoader(context.applicationContext)
    }
    val gradientColors = remember(artist.id) { expressiveGradientColors(artist.id) }
    val displayAccent = readableAccentColor(accentColor, appColors.isDark)
    var uriLoadFailed by remember(artist.avatarUri) { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .jellyTapScale(jellyState)
            .clickable(
                interactionSource = jellyState.interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .size(avatarSize)
                .shadow(
                    elevation = 10.dp,
                    shape = CircleShape,
                    ambientColor = displayAccent.copy(alpha = 0.25f),
                    spotColor = Color.Black.copy(alpha = 0.45f)
                )
                .clip(CircleShape)
                .background(Brush.linearGradient(gradientColors))
                .border(
                    width = 1.6.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (appColors.isDark) 0.48f else 0.85f),
                            displayAccent.copy(alpha = 0.65f),
                            Color.White.copy(alpha = if (appColors.isDark) 0.14f else 0.35f)
                        )
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (artist.avatarResId != null) {
                Image(
                    painter = painterResource(id = artist.avatarResId),
                    contentDescription = artist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (!artist.avatarUri.isNullOrBlank() && !uriLoadFailed) {
                AsyncImage(
                    model = ImageRequest.Builder(context.applicationContext)
                        .data(artist.avatarUri)
                        .crossfade(200)
                        .build(),
                    imageLoader = sharedImageLoader,
                    contentDescription = artist.name,
                    contentScale = ContentScale.Crop,
                    onError = { uriLoadFailed = true },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = artist.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    color = Color.White,
                    fontFamily = GoogleSans,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(7.dp))

        Text(
            text = artist.name,
            color = appColors.textPrimary,
            fontFamily = GoogleSans,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        if (artist.genre.isNotBlank()) {
            Text(
                text = artist.genre,
                color = appColors.textSecondary,
                fontFamily = PlusJakartaSans,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
internal fun SeeMoreDirectoryCard(
    title: String,
    subtitle: String?,
    appColors: AppThemeColors,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val jellyState = rememberJellyPressState(pressedScaleX = 0.94f, pressedScaleY = 0.90f)
    val displayAccent = readableAccentColor(accentColor, appColors.isDark)

    GlossyCardShell(
        shape = CardCornerShape,
        jellyState = jellyState,
        onClick = onClick,
        elevation = 6.dp,
        containerColor = if (appColors.isDark) Color(0x521B2130) else Color(0xDDF2F6FF),
        modifier = modifier.border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    displayAccent.copy(alpha = 0.55f),
                    Color.White.copy(alpha = if (appColors.isDark) 0.18f else 0.72f)
                )
            ),
            shape = CardCornerShape
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            displayAccent.copy(alpha = if (appColors.isDark) 0.22f else 0.14f),
                            Color.Transparent
                        )
                    )
                )
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(displayAccent.copy(alpha = if (appColors.isDark) 0.24f else 0.16f))
                        .border(1.dp, displayAccent.copy(alpha = 0.55f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = title,
                        tint = displayAccent,
                        modifier = Modifier.size(21.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = title,
                    color = appColors.textPrimary,
                    fontFamily = GoogleSans,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = subtitle,
                        color = displayAccent,
                        fontFamily = PlusJakartaSans,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Song row card styled identically to Playing Queue's [QueueTrackRow]:
 * frosted translucent rounded card container (`16.dp` ContinuousRoundedRectangle), side `48.dp` artwork,
 * glowing pulsing accent border + [LibraryEqualizerBars] when active, duration label, favorite heart
 * & more-options icon with jelly spring press feedback.
 */
@Composable
internal fun LibraryQueueSongRow(
    track: TrackItem,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: (() -> Unit)?,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    appColors: AppThemeColors,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sharedImageLoader = remember(context) {
        MediaNetworkClients.getImageLoader(context.applicationContext)
    }
    val displayAccent = readableAccentColor(accentColor, appColors.isDark)
    val isDark = appColors.isDark
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.972f else 1f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium),
        label = "libSongPressScale"
    )

    val activePopScale = remember(track.id) { Animatable(1f) }
    LaunchedEffect(isCurrent) {
        if (isCurrent) {
            activePopScale.snapTo(0.96f)
            activePopScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    val glowPulse by if (isCurrent && isPlaying) {
        val transition = rememberInfiniteTransition(label = "libSongActiveGlow")
        transition.animateFloat(
            initialValue = 0.55f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1050, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "libSongGlowPulse"
        )
    } else {
        remember(isCurrent) { mutableFloatStateOf(if (isCurrent) 0.65f else 0f) }
    }

    val rowElevation by animateDpAsState(
        targetValue = if (isCurrent) 8.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "libSongElevation"
    )

    val rowBackground by animateColorAsState(
        targetValue = when {
            isCurrent -> displayAccent.copy(alpha = if (isDark) 0.22f else 0.16f)
            isDark -> Color(0x20FFFFFF)
            else -> Color(0x78FFFFFF)
        },
        animationSpec = tween(durationMillis = 220),
        label = "libSongBg"
    )

    val borderBrush = if (isCurrent) {
        Brush.linearGradient(
            listOf(
                displayAccent.copy(alpha = 0.72f * glowPulse),
                Color.White.copy(alpha = (if (isDark) 0.35f else 0.70f) * glowPulse),
                displayAccent.copy(alpha = 0.55f * glowPulse)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = if (isDark) 0.16f else 0.58f),
                Color.White.copy(alpha = if (isDark) 0.06f else 0.26f)
            )
        )
    }

    val coverScale by animateFloatAsState(
        targetValue = if (isCurrent && isPlaying) 1.04f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "libSongCoverScale"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                val combined = pressScale * activePopScale.value
                scaleX = combined
                scaleY = combined
            }
            .shadow(
                elevation = rowElevation,
                shape = QueueRowShape,
                ambientColor = if (isCurrent) displayAccent.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.22f),
                spotColor = if (isCurrent) displayAccent.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.30f)
            )
            .clip(QueueRowShape)
            .background(rowBackground)
            .border(
                width = if (isCurrent) 1.25.dp else 0.8.dp,
                brush = borderBrush,
                shape = QueueRowShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LibraryTrackCover(
            track = track,
            isCurrent = isCurrent,
            accentColor = displayAccent,
            isDark = isDark,
            sharedImageLoader = sharedImageLoader,
            modifier = Modifier.graphicsLayer {
                scaleX = coverScale
                scaleY = coverScale
            }
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = track.title,
                    fontFamily = GoogleSans,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = if (isCurrent) displayAccent else appColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (track.isExplicit) {
                    ExplicitBadge()
                }
            }
            Text(
                text = if (track.album.isNotBlank() && track.album != "Unknown Album") {
                    "${track.artist} • ${track.album}"
                } else {
                    track.artist
                },
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = if (isCurrent) appColors.textPrimary.copy(alpha = 0.80f) else appColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        if (isCurrent) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(displayAccent.copy(alpha = (0.16f + 0.12f * glowPulse)))
                    .border(0.75.dp, displayAccent.copy(alpha = 0.45f * glowPulse), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                LibraryEqualizerBars(
                    color = displayAccent,
                    isPlaying = isPlaying
                )
            }
        } else if (track.durationMs > 0L) {
            Text(
                text = track.formattedDuration,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Medium,
                fontSize = 11.5.sp,
                color = appColors.textSecondary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        if (onToggleFavorite != null) {
            val favInteraction = remember { MutableInteractionSource() }
            val favPop = remember(track.id) { Animatable(1f) }
            LaunchedEffect(isFavorite) {
                if (isFavorite) {
                    favPop.snapTo(0.74f)
                    favPop.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(dampingRatio = 0.36f, stiffness = 420f)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .pressWobble(favInteraction, pressedScale = 0.82f)
                    .graphicsLayer {
                        scaleX = favPop.value
                        scaleY = favPop.value
                    }
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = favInteraction,
                        indication = null,
                        onClick = onToggleFavorite
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (isFavorite) displayAccent else appColors.textSecondary.copy(alpha = 0.82f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        val moreInteraction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .size(34.dp)
                .pressWobble(moreInteraction, pressedScale = 0.82f)
                .clip(CircleShape)
                .clickable(
                    interactionSource = moreInteraction,
                    indication = null,
                    onClick = onMoreClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "More options",
                tint = appColors.textSecondary.copy(alpha = 0.86f),
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
private fun LibraryTrackCover(
    track: TrackItem,
    isCurrent: Boolean,
    accentColor: Color,
    isDark: Boolean,
    sharedImageLoader: ImageLoader,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var uriLoadFailed by remember(track.artworkUri) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(48.dp)
            .clip(QueueArtworkShape)
            .background(if (isDark) Color(0xFF1E222B) else Color(0xFFE2E8F0))
            .then(
                if (isCurrent) {
                    Modifier.border(1.2.dp, accentColor.copy(alpha = 0.75f), QueueArtworkShape)
                } else {
                    Modifier.border(0.6.dp, Color.White.copy(alpha = if (isDark) 0.14f else 0.35f), QueueArtworkShape)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (track.artworkResId != null) {
            Image(
                painter = painterResource(id = track.artworkResId),
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (!track.artworkUri.isNullOrBlank() && !uriLoadFailed) {
            AsyncImage(
                model = ImageRequest.Builder(context.applicationContext)
                    .data(track.artworkUri)
                    .crossfade(200)
                    .build(),
                imageLoader = sharedImageLoader,
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                onError = { uriLoadFailed = true },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = if (isCurrent) accentColor else if (isDark) Color.White.copy(alpha = 0.38f) else Color.Black.copy(alpha = 0.38f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
internal fun LibraryEqualizerBars(
    color: Color,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barHeight: Dp = 14.dp
) {
    val barHeights: List<Float> = if (isPlaying) {
        val transition = rememberInfiniteTransition(label = "libraryEq")
        listOf(
            transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 0.95f,
                animationSpec = infiniteRepeatable(
                    animation = tween(360, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "libEq0"
            ).value,
            transition.animateFloat(
                initialValue = 0.90f,
                targetValue = 0.28f,
                animationSpec = infiniteRepeatable(
                    animation = tween(430, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "libEq1"
            ).value,
            transition.animateFloat(
                initialValue = 0.38f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(310, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "libEq2"
            ).value,
            transition.animateFloat(
                initialValue = 0.80f,
                targetValue = 0.32f,
                animationSpec = infiniteRepeatable(
                    animation = tween(490, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "libEq3"
            ).value
        )
    } else {
        listOf(0.32f, 0.56f, 0.42f, 0.35f)
    }

    Row(
        modifier = modifier.height(barHeight),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        barHeights.forEach { frac ->
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .fillMaxHeight(frac.coerceIn(0.22f, 1f))
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(color)
            )
        }
    }
}

// =============================================================================
// SECTION HEADERS, STAGGERED FRAME, GLASS BACK PILL & EMPTY CARD
// =============================================================================

@Composable
internal fun LibrarySectionDividerHeader(
    title: String,
    countText: String,
    leadingIcon: ImageVector,
    appColors: AppThemeColors,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val displayAccent = readableAccentColor(accentColor, appColors.isDark)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(displayAccent.copy(alpha = if (appColors.isDark) 0.22f else 0.14f))
                .border(0.8.dp, displayAccent.copy(alpha = 0.46f), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = displayAccent,
                modifier = Modifier.size(15.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            color = appColors.textPrimary,
            fontFamily = GoogleSans,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(displayAccent.copy(alpha = if (appColors.isDark) 0.18f else 0.12f))
                .border(0.8.dp, displayAccent.copy(alpha = 0.42f), CircleShape)
                .padding(horizontal = 9.dp, vertical = 3.dp)
        ) {
            Text(
                text = countText,
                color = displayAccent,
                fontFamily = GoogleSans,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun LibraryAlphabetSectionHeader(
    letter: String,
    count: Int,
    appColors: AppThemeColors,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val displayAccent = readableAccentColor(accentColor, appColors.isDark)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(displayAccent.copy(alpha = if (appColors.isDark) 0.24f else 0.16f))
                .border(0.9.dp, displayAccent.copy(alpha = 0.50f), RoundedCornerShape(10.dp))
                .padding(horizontal = 11.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = letter,
                color = displayAccent,
                fontFamily = GoogleSans,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .height(0.8.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            displayAccent.copy(alpha = 0.42f),
                            if (appColors.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f),
                            Color.Transparent
                        )
                    )
                )
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "$count",
            color = appColors.textSecondary,
            fontFamily = PlusJakartaSans,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
internal fun LibraryStaggeredItemFrame(
    index: Int,
    staggerKey: Any?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val entrance = remember(staggerKey) { Animatable(0f) }
    LaunchedEffect(staggerKey) {
        entrance.snapTo(0f)
        val delayMs = (index % 8) * 30L
        if (delayMs > 0L) delay(delayMs)
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
            val s = 0.86f + 0.14f * p
            scaleX = s
            scaleY = s
            translationY = (1f - p) * 32.dp.toPx()
        }
    ) {
        content()
    }
}

@Composable
internal fun LibraryLiquidGlassBackPill(
    label: String,
    onClick: () -> Unit,
    appColors: AppThemeColors,
    modifier: Modifier = Modifier
) {
    val glassConfig = LocalGlassEffectConfig.current
    val useGlass = glassConfig.isEnabledFor(GlassComponent.NAV_BAR) && isGlassAllowed()
    val interactionSource = remember { MutableInteractionSource() }

    val surfaceModifier = if (useGlass) {
        Modifier.liquidGlass(
            config = glassConfig,
            shape = GlassPillShape,
            highlightAlpha = 0.36f
        )
    } else {
        Modifier
            .clip(GlassPillShape)
            .background(if (appColors.isDark) Color(0xCC1B1F2B) else Color(0xEBFFFFFF))
            .border(
                width = 0.9.dp,
                color = if (appColors.isDark) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.10f),
                shape = GlassPillShape
            )
    }

    val contentTint = if (useGlass) glassConfig.textColor else appColors.textPrimary

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier
            .pressWobble(interactionSource)
            .height(40.dp)
            .clip(GlassPillShape)
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
            contentDescription = label,
            tint = contentTint,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            color = contentTint,
            fontFamily = GoogleSans,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
internal fun LibraryEmptyDirectoryCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    appColors: AppThemeColors,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val displayAccent = readableAccentColor(accentColor, appColors.isDark)
    val shape = ContinuousRoundedRectangle(size = 24.dp)

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(
                    if (appColors.isDark) Color(0x99171B26) else Color(0xE8FFFFFF)
                )
                .border(
                    width = 0.9.dp,
                    color = if (appColors.isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.08f),
                    shape = shape
                )
                .padding(horizontal = 24.dp, vertical = 28.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(displayAccent.copy(alpha = if (appColors.isDark) 0.22f else 0.14f))
                    .border(1.dp, displayAccent.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = displayAccent,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                color = appColors.textPrimary,
                fontFamily = GoogleSans,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                color = appColors.textSecondary,
                fontFamily = PlusJakartaSans,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}
