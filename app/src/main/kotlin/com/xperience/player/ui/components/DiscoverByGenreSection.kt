package com.xperience.player.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.ui.adaptive.LocalAppWindowSizeClass
import com.xperience.player.ui.adaptive.isExpandedWidth
import com.xperience.player.ui.adaptive.isMediumOrExpandedWidth
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.PlusJakartaSans
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscoverByGenreSection(
    genres: List<String>,
    selectedGenre: String,
    genreTracks: List<TrackItem>,
    onGenreSelect: (String) -> Unit,
    onTrackClick: (TrackItem) -> Unit,
    modifier: Modifier = Modifier,
    onPlayTrackFromList: ((List<TrackItem>, Int) -> Unit)? = null,
    onTrackMoreClick: ((TrackItem) -> Unit)? = null
) {
    val accentColor = LocalAccentColor.current
    val appColors = LocalAppColors.current
    val windowSizeClass = LocalAppWindowSizeClass.current
    val badgePopScale = remember { Animatable(1f) }
    var lastGenre by remember { mutableStateOf(selectedGenre) }

    LaunchedEffect(selectedGenre) {
        if (selectedGenre != lastGenre) {
            lastGenre = selectedGenre
            badgePopScale.snapTo(1.16f)
            badgePopScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.42f, stiffness = 420f)
            )
        }
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Section Header with active genre badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = "Discover by Genre",
                color = appColors.textPrimary,
                fontFamily = GoogleSans,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.size(10.dp))

            Box(
                modifier = Modifier
                    .graphicsLayer {
                        val s = badgePopScale.value
                        scaleX = s
                        scaleY = s
                    }
                    .animateContentSize(
                        animationSpec = spring(dampingRatio = 0.46f, stiffness = 400f)
                    )
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        width = 1.dp,
                        color = accentColor.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .background(accentColor.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = selectedGenre,
                    color = accentColor,
                    fontFamily = GoogleSans,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Adaptive Genre Filter Pills (FlowRow on Medium/Expanded, horizontal scroll on Compact)
        if (windowSizeClass.isMediumOrExpandedWidth) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                genres.forEach { genre ->
                    GenreFilterPill(
                        genre = genre,
                        isSelected = genre.equals(selectedGenre, ignoreCase = true),
                        onClick = { onGenreSelect(genre) }
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                genres.forEach { genre ->
                    GenreFilterPill(
                        genre = genre,
                        isSelected = genre.equals(selectedGenre, ignoreCase = true),
                        onClick = { onGenreSelect(genre) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Genre tracks list / grid
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            if (genreTracks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = accentColor,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else {
                AdaptiveTrackRowsGrid(
                    tracks = genreTracks,
                    limit = 6,
                    onTrackClick = onTrackClick,
                    onPlayTrackFromList = onPlayTrackFromList,
                    onTrackMoreClick = onTrackMoreClick
                )
            }
        }
    }
}

@Composable
private fun GenreFilterPill(
    genre: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val jelly = rememberAuroraJellyPressState(
        interactionSource = interactionSource,
        pressedScaleX = 0.93f,
        pressedScaleY = 0.90f,
        releaseVelocityX = 1.95f,
        releaseVelocityY = 0.95f,
        dampingRatio = 0.42f,
        stiffness = 420f,
        releaseWobbleDeg = 2.4f
    )
    val selectionBounceX = remember(genre) { Animatable(1f) }
    val selectionBounceY = remember(genre) { Animatable(1f) }
    var wasSelected by remember(genre) { mutableStateOf(isSelected) }

    LaunchedEffect(isSelected) {
        if (isSelected && !wasSelected) {
            launch {
                selectionBounceX.snapTo(1.10f)
                selectionBounceX.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.42f, stiffness = 380f)
                )
            }
            launch {
                selectionBounceY.snapTo(0.90f)
                selectionBounceY.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.44f, stiffness = 400f)
                )
            }
        }
        wasSelected = isSelected
    }

    val accentColor = LocalAccentColor.current
    val appColors = LocalAppColors.current
    val shape = CircleShape

    val borderStartColor by animateColorAsState(
        targetValue = if (isSelected) accentColor else if (appColors.isDark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.10f),
        animationSpec = tween(durationMillis = 220),
        label = "pillBorderStart"
    )
    val borderEndColor by animateColorAsState(
        targetValue = if (isSelected) accentColor.copy(alpha = 0.6f) else if (appColors.isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.04f),
        animationSpec = tween(durationMillis = 220),
        label = "pillBorderEnd"
    )
    val bgStartColor by animateColorAsState(
        targetValue = if (isSelected) accentColor.copy(alpha = 0.25f) else if (appColors.isDark) Color(0xFF16181F).copy(alpha = 0.8f) else Color(0xFFFFFFFF),
        animationSpec = tween(durationMillis = 220),
        label = "pillBgStart"
    )
    val bgEndColor by animateColorAsState(
        targetValue = if (isSelected) accentColor.copy(alpha = 0.10f) else if (appColors.isDark) Color(0xFF16181F).copy(alpha = 0.5f) else Color(0xFFFFFFFF),
        animationSpec = tween(durationMillis = 220),
        label = "pillBgEnd"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) accentColor else appColors.textSecondary,
        animationSpec = tween(durationMillis = 200),
        label = "pillText"
    )

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = jelly.scaleX.value * selectionBounceX.value
                scaleY = jelly.scaleY.value * selectionBounceY.value
                rotationZ = jelly.rotationZ.value
            }
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(colors = listOf(borderStartColor, borderEndColor)),
                shape = shape
            )
            .background(
                Brush.linearGradient(colors = listOf(bgStartColor, bgEndColor))
            )
            .padding(horizontal = 16.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = genre,
            color = textColor,
            fontFamily = if (isSelected) GoogleSans else PlusJakartaSans,
            fontSize = 12.5.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}
