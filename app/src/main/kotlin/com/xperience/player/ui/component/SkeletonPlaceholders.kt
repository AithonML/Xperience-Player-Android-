package com.xperience.player.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.theme.LocalAppColors

private const val SHIMMER_PERIOD_MS = 1350

private val DefaultBlockShape = RoundedCornerShape(8.dp)
private val DefaultLineShape = RoundedCornerShape(4.dp)
private val SongRowShape = RoundedCornerShape(14.dp)
private val AlbumCardShape = RoundedCornerShape(16.dp)
private val PillShape = ContinuousRoundedRectangle(percent = 50)

private val TitleWidths = listOf(0.64f, 0.48f, 0.72f, 0.54f, 0.68f, 0.44f, 0.60f)
private val SubtitleWidths = listOf(0.38f, 0.46f, 0.30f, 0.42f, 0.34f, 0.40f, 0.28f)

private val LocalShimmerProgress = compositionLocalOf<State<Float>?> { null }

/**
 * Provides one shared infinite shimmer sweep across all descendant skeleton placeholders
 * so every block animates in synchronized unison without creating multiple transitions.
 */
@Composable
fun ShimmerScope(content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "skeletonShimmer")
    val progress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SHIMMER_PERIOD_MS, easing = LinearEasing)
        ),
        label = "shimmerSweep"
    )
    CompositionLocalProvider(LocalShimmerProgress provides progress) {
        content()
    }
}

@Composable
private fun resolveShimmerProgress(): State<Float> {
    LocalShimmerProgress.current?.let { return it }
    val transition = rememberInfiniteTransition(label = "skeletonShimmerSingle")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SHIMMER_PERIOD_MS, easing = LinearEasing)
        ),
        label = "shimmerSweepSingle"
    )
}

/**
 * Draw-phase-only shimmer block. Reading [progress] inside [drawBehind] prevents per-frame
 * recomposition and layout work across large skeleton lists.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = DefaultBlockShape,
    baseColor: Color? = null,
    highlightColor: Color? = null,
    progress: State<Float> = resolveShimmerProgress()
) {
    val isDark = LocalAppColors.current.isDark
    val resolvedBase = baseColor ?: if (isDark) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color.Black.copy(alpha = 0.07f)
    }
    val resolvedHighlight = highlightColor ?: if (isDark) {
        Color.White.copy(alpha = 0.20f)
    } else {
        Color.Black.copy(alpha = 0.16f)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .drawBehind {
                val width = size.width
                if (width <= 0f) return@drawBehind
                val band = (width * 0.55f).coerceAtLeast(1f)
                val startX = -band + progress.value * (width + band * 2f)
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(resolvedBase, resolvedHighlight, resolvedBase),
                        startX = startX,
                        endX = startX + band
                    )
                )
            }
    )
}

/** Rounded shimmer placeholder line sized as a fraction of its parent width. */
@Composable
fun ShimmerLine(
    fraction: Float,
    height: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = DefaultLineShape
) {
    ShimmerBox(
        modifier = modifier
            .fillMaxWidth(fraction.coerceIn(0.05f, 1f))
            .height(height),
        shape = shape
    )
}

/** Centered multi-line biography / description skeleton block. */
@Composable
fun CenteredTextBlockSkeleton(
    lines: Int = 2,
    modifier: Modifier = Modifier
) {
    val lineFractions = listOf(0.84f, 0.58f, 0.72f)
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        repeat(lines.coerceAtLeast(1)) { index ->
            ShimmerLine(
                fraction = lineFractions[index % lineFractions.size],
                height = 12.dp
            )
        }
    }
}

/** Wordmark/Title placeholder for hero header while custom logo or name settles. */
@Composable
fun ArtistWordmarkSkeleton(
    modifier: Modifier = Modifier
) {
    ShimmerBox(
        modifier = modifier
            .width(196.dp)
            .height(42.dp),
        shape = RoundedCornerShape(12.dp)
    )
}

/** Audience subtitle placeholder (monthly listeners / album metadata). */
@Composable
fun HeroSubtitleSkeleton(
    modifier: Modifier = Modifier,
    width: Dp = 148.dp
) {
    ShimmerBox(
        modifier = modifier
            .width(width)
            .height(14.dp),
        shape = PillShape
    )
}

/** Subscribers pill skeleton matching [LiquidSubscribersGlassPill] proportions. */
@Composable
fun SubscribersPillSkeleton(
    modifier: Modifier = Modifier
) {
    ShimmerBox(
        modifier = modifier
            .width(134.dp)
            .height(32.dp),
        shape = PillShape
    )
}

/** Skeleton for Top Songs section matching [ArtistTopSongsSection] and [ArtistSongRow]. */
@Composable
fun ArtistTopSongsSkeleton(
    ambientBgColor: Color,
    rowCount: Int = 5,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val cardBg = if (appColors.isDark) {
        ambientBgColor.copy(alpha = 0.45f)
    } else {
        Color.White.copy(alpha = 0.75f)
    }
    val borderColor = if (appColors.isDark) {
        Color.White.copy(alpha = 0.10f)
    } else {
        Color.Black.copy(alpha = 0.08f)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ShimmerBox(
            modifier = Modifier
                .width(124.dp)
                .height(22.dp),
            shape = RoundedCornerShape(6.dp)
        )

        repeat(rowCount) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SongRowShape)
                    .background(cardBg)
                    .border(1.dp, borderColor, SongRowShape)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ShimmerBox(
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(8.dp)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ShimmerLine(
                        fraction = TitleWidths[index % TitleWidths.size],
                        height = 13.dp
                    )
                    ShimmerLine(
                        fraction = SubtitleWidths[index % SubtitleWidths.size],
                        height = 10.dp
                    )
                }

                ShimmerBox(
                    modifier = Modifier.size(22.dp),
                    shape = CircleShape
                )
            }
        }
    }
}

/** Skeleton for horizontal album/single/playlist shelves matching [ArtistMediaShelf]. */
@Composable
fun MediaShelfSkeleton(
    titleWidth: Dp = 108.dp,
    cardCount: Int = 4,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ShimmerBox(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .width(titleWidth)
                .height(22.dp),
            shape = RoundedCornerShape(6.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState(), enabled = false)
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            repeat(cardCount) { index ->
                Column(
                    modifier = Modifier.width(136.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ShimmerBox(
                        modifier = Modifier.size(136.dp),
                        shape = AlbumCardShape
                    )
                    ShimmerLine(
                        fraction = if (index % 2 == 0) 0.82f else 0.68f,
                        height = 12.dp
                    )
                    ShimmerLine(
                        fraction = if (index % 2 == 0) 0.42f else 0.52f,
                        height = 10.dp
                    )
                }
            }
        }
    }
}

/** Skeleton for Fans Might Also Like circular artist cards matching [ArtistFansSection]. */
@Composable
fun ArtistFansSkeleton(
    cardCount: Int = 4,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ShimmerBox(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .width(184.dp)
                .height(22.dp),
            shape = RoundedCornerShape(6.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState(), enabled = false)
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            repeat(cardCount) { index ->
                Column(
                    modifier = Modifier.width(118.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ShimmerBox(
                        modifier = Modifier.size(118.dp),
                        shape = CircleShape
                    )
                    ShimmerLine(
                        fraction = if (index % 2 == 0) 0.78f else 0.64f,
                        height = 12.dp
                    )
                    ShimmerLine(
                        fraction = 0.44f,
                        height = 10.dp
                    )
                }
            }
        }
    }
}

/** Skeleton for album tracklist rows matching [AlbumTrackRow]. */
@Composable
fun AlbumTracksSkeleton(
    rowCount: Int = 7,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ShimmerBox(
            modifier = Modifier
                .padding(bottom = 6.dp)
                .width(84.dp)
                .height(22.dp),
            shape = RoundedCornerShape(6.dp)
        )

        repeat(rowCount) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ShimmerBox(
                    modifier = Modifier.size(22.dp),
                    shape = RoundedCornerShape(6.dp)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    ShimmerLine(
                        fraction = TitleWidths[index % TitleWidths.size],
                        height = 13.dp
                    )
                    ShimmerLine(
                        fraction = SubtitleWidths[index % SubtitleWidths.size],
                        height = 10.dp
                    )
                }

                ShimmerBox(
                    modifier = Modifier
                        .width(34.dp)
                        .height(11.dp),
                    shape = DefaultLineShape
                )
            }
        }
    }
}
