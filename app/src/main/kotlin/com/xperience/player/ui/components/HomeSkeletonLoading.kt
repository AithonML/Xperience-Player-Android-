package com.xperience.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xperience.player.ui.adaptive.LocalBottomChromePadding
import com.xperience.player.ui.component.ShimmerBox
import com.xperience.player.ui.component.ShimmerLine
import com.xperience.player.ui.component.ShimmerScope
import com.xperience.player.ui.theme.LocalAppColors

private val TopPickSkeletonShape = RoundedCornerShape(22.dp)
private val BentoSkeletonShape = RoundedCornerShape(26.dp)
private val ShelfCardSkeletonShape = RoundedCornerShape(22.dp)
private val MoreLikeCardSkeletonShape = RoundedCornerShape(20.dp)

/**
 * Full Home feed skeleton matching the exact visual hierarchy of [HomeScreen]:
 * TopPicks (`196.dp x 264.dp`), Recently Played Bento grid (`185.dp` columns),
 * More Like horizontal shelf (`150.dp x 178.dp`), Related Artists circles (`135.dp`),
 * Playlists (`168.dp x 202.dp`), and Radio / Novedades shelves (`164.dp x 194.dp`),
 * styled with synchronized [ShimmerScope] sweeps and LiquidGlass frosted borders.
 */
@Composable
fun HomeFeedSkeletonScreen(
    modifier: Modifier = Modifier
) {
    ShimmerScope {
        Column(
            modifier = modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // 1. Top Picks For You skeleton
            HomeSkeletonSectionHeader(
                titleWidth = 196.dp,
                hasLeadingBadge = true
            )
            Spacer(modifier = Modifier.height(14.dp))
            TopPicksSkeletonRow()

            Spacer(modifier = Modifier.height(26.dp))

            // 2. Recently Played Bento skeleton grid
            HomeSkeletonSectionHeader(
                titleWidth = 152.dp,
                hasLeadingBadge = false
            )
            Spacer(modifier = Modifier.height(14.dp))
            BentoSkeletonRow()

            Spacer(modifier = Modifier.height(26.dp))

            // 3. More like album shelf skeleton
            HomeSkeletonSectionHeader(
                titleWidth = 176.dp,
                hasLeadingBadge = true,
                hasTrailingBadge = true
            )
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalCardShelfSkeletonRow(
                cardWidth = 150.dp,
                cardHeight = 178.dp,
                shape = MoreLikeCardSkeletonShape,
                spacing = 13.dp,
                cardCount = 4,
                showTopBadges = false
            )

            Spacer(modifier = Modifier.height(26.dp))

            // 4. Related Artists circular avatar skeleton row
            HomeSkeletonSectionHeader(
                titleWidth = 188.dp,
                hasLeadingBadge = false
            )
            Spacer(modifier = Modifier.height(14.dp))
            RelatedArtistsSkeletonRow()

            Spacer(modifier = Modifier.height(26.dp))

            // 5. Playlists Made For You shelf skeleton
            HomeSkeletonSectionHeader(
                titleWidth = 172.dp,
                hasLeadingBadge = true
            )
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalCardShelfSkeletonRow(
                cardWidth = 168.dp,
                cardHeight = 202.dp,
                shape = ShelfCardSkeletonShape,
                spacing = 14.dp,
                cardCount = 4,
                showTopBadges = true
            )

            Spacer(modifier = Modifier.height(26.dp))

            // 6. Radio / Novedades shelf skeleton
            HomeSkeletonSectionHeader(
                titleWidth = 164.dp,
                hasLeadingBadge = true,
                hasTrailingBadge = true
            )
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalCardShelfSkeletonRow(
                cardWidth = 164.dp,
                cardHeight = 194.dp,
                shape = ShelfCardSkeletonShape,
                spacing = 14.dp,
                cardCount = 4,
                showTopBadges = true
            )

            Spacer(modifier = Modifier.height(LocalBottomChromePadding.current))
        }
    }
}

@Composable
private fun HomeSkeletonSectionHeader(
    titleWidth: Dp,
    hasLeadingBadge: Boolean,
    hasTrailingBadge: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        if (hasLeadingBadge) {
            FrostedSkeletonSurface(
                shape = RoundedCornerShape(9.dp),
                modifier = Modifier.size(28.dp),
                elevation = 0.dp
            ) {
                ShimmerBox(
                    modifier = Modifier.matchParentSize(),
                    shape = RoundedCornerShape(9.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
        }

        ShimmerBox(
            modifier = Modifier
                .width(titleWidth)
                .height(20.dp),
            shape = RoundedCornerShape(7.dp)
        )

        if (hasTrailingBadge) {
            Spacer(modifier = Modifier.weight(1f))
            FrostedSkeletonSurface(
                shape = CircleShape,
                modifier = Modifier
                    .width(54.dp)
                    .height(20.dp),
                elevation = 0.dp
            ) {
                ShimmerBox(
                    modifier = Modifier.matchParentSize(),
                    shape = CircleShape
                )
            }
        }
    }
}

@Composable
private fun TopPicksSkeletonRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState(), enabled = false)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        repeat(3) { index ->
            FrostedSkeletonSurface(
                shape = TopPickSkeletonShape,
                modifier = Modifier
                    .width(196.dp)
                    .height(264.dp)
            ) {
                ShimmerBox(
                    modifier = Modifier.matchParentSize(),
                    shape = TopPickSkeletonShape
                )

                // Top-left eyebrow badge placeholder
                ShimmerBox(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .width(88.dp)
                        .height(20.dp),
                    shape = CircleShape
                )

                // Bottom frosted title & subtitle lines
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    ShimmerLine(
                        fraction = if (index % 2 == 0) 0.78f else 0.66f,
                        height = 15.dp
                    )
                    ShimmerLine(
                        fraction = if (index % 2 == 0) 0.54f else 0.46f,
                        height = 11.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun BentoSkeletonRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState(), enabled = false)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Column 1: Two stacked bento cards (220.dp + 180.dp)
        Column(
            modifier = Modifier
                .width(185.dp)
                .height(412.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BentoCardSkeleton(
                height = 220.dp,
                titleFraction = 0.72f,
                subtitleFraction = 0.46f
            )
            BentoCardSkeleton(
                height = 180.dp,
                titleFraction = 0.62f,
                subtitleFraction = 0.40f
            )
        }

        // Column 2: Three stacked bento cards (125.dp + 125.dp + 138.dp)
        Column(
            modifier = Modifier
                .width(185.dp)
                .height(412.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BentoCardSkeleton(
                height = 125.dp,
                titleFraction = 0.68f,
                subtitleFraction = 0.44f
            )
            BentoCardSkeleton(
                height = 125.dp,
                titleFraction = 0.56f,
                subtitleFraction = 0.36f
            )
            BentoCardSkeleton(
                height = 138.dp,
                titleFraction = 0.74f,
                subtitleFraction = 0.48f
            )
        }

        // Column 3: Peek column matching TwoCards reversed (180.dp + 220.dp)
        Column(
            modifier = Modifier
                .width(185.dp)
                .height(412.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BentoCardSkeleton(
                height = 180.dp,
                titleFraction = 0.65f,
                subtitleFraction = 0.42f
            )
            BentoCardSkeleton(
                height = 220.dp,
                titleFraction = 0.76f,
                subtitleFraction = 0.50f
            )
        }
    }
}

@Composable
private fun BentoCardSkeleton(
    height: Dp,
    titleFraction: Float,
    subtitleFraction: Float
) {
    FrostedSkeletonSurface(
        shape = BentoSkeletonShape,
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        elevation = 6.dp
    ) {
        ShimmerBox(
            modifier = Modifier.matchParentSize(),
            shape = BentoSkeletonShape
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 13.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            ShimmerLine(fraction = titleFraction, height = 13.dp)
            ShimmerLine(fraction = subtitleFraction, height = 10.dp)
        }
    }
}

@Composable
private fun RelatedArtistsSkeletonRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState(), enabled = false)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(4) { index ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FrostedSkeletonSurface(
                    shape = CircleShape,
                    modifier = Modifier.size(135.dp),
                    elevation = 6.dp
                ) {
                    ShimmerBox(
                        modifier = Modifier.matchParentSize(),
                        shape = CircleShape
                    )
                }

                ShimmerBox(
                    modifier = Modifier
                        .width(if (index % 2 == 0) 92.dp else 78.dp)
                        .height(13.dp),
                    shape = RoundedCornerShape(5.dp)
                )
            }
        }
    }
}

@Composable
private fun HorizontalCardShelfSkeletonRow(
    cardWidth: Dp,
    cardHeight: Dp,
    shape: Shape,
    spacing: Dp,
    cardCount: Int,
    showTopBadges: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState(), enabled = false)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(spacing)
    ) {
        repeat(cardCount) { index ->
            FrostedSkeletonSurface(
                shape = shape,
                modifier = Modifier
                    .width(cardWidth)
                    .height(cardHeight),
                elevation = 8.dp
            ) {
                ShimmerBox(
                    modifier = Modifier.matchParentSize(),
                    shape = shape
                )

                if (showTopBadges) {
                    ShimmerBox(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(11.dp)
                            .width(72.dp)
                            .height(18.dp),
                        shape = CircleShape
                    )
                    ShimmerBox(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(11.dp)
                            .size(28.dp),
                        shape = CircleShape
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 13.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ShimmerLine(
                        fraction = if (index % 2 == 0) 0.80f else 0.66f,
                        height = 13.dp
                    )
                    ShimmerLine(
                        fraction = if (index % 2 == 0) 0.50f else 0.42f,
                        height = 10.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun FrostedSkeletonSurface(
    shape: Shape,
    modifier: Modifier = Modifier,
    elevation: Dp = 8.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = LocalAppColors.current.isDark
    val containerColor = if (isDark) {
        Color(0xFF151822).copy(alpha = 0.72f)
    } else {
        Color.White.copy(alpha = 0.66f)
    }
    val rimBrush = remember(isDark) {
        if (isDark) {
            Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.30f),
                    Color.White.copy(alpha = 0.07f),
                    Color.White.copy(alpha = 0.16f)
                )
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.85f),
                    Color.Black.copy(alpha = 0.06f),
                    Color.White.copy(alpha = 0.45f)
                )
            )
        }
    }

    val shadowModifier = if (elevation > 0.dp) {
        Modifier.shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = if (isDark) 0.34f else 0.12f),
            spotColor = Color.Black.copy(alpha = if (isDark) 0.44f else 0.18f)
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(shadowModifier)
            .clip(shape)
            .background(containerColor)
            .border(width = 0.9.dp, brush = rimBrush, shape = shape),
        content = content
    )
}
