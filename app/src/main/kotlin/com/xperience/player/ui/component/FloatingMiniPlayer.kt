/**
 * Convx / Xperience Music Player
 * Licensed under GPL-3.0
 */
package com.xperience.player.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xperience.player.domain.model.PlaybackState
import com.xperience.player.ui.component.backdrop.catalog.utils.InteractiveHighlight
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.PlusJakartaSans
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Compact now playing controls docked inside the floating tab bar, mirroring the
 * Convx / iOS 26 Apple Music accessory: a wide pill above the tabs when the bar is
 * expanded, and a slim strip between the tab pill and the search tab when inline.
 *
 * Swiping the artwork/title area horizontally changes the song.
 * Tapping opens the full player.
 */
@Composable
fun FloatingMiniPlayer(
    playbackState: PlaybackState,
    isInline: Boolean,
    contentColor: Color,
    onClick: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    surfaceModifier: Modifier = Modifier,
    isDesktopBar: Boolean = false,
    onLyricsClick: (() -> Unit)? = null,
    onQueueClick: (() -> Unit)? = null,
    playbackPositionMsFlow: kotlinx.coroutines.flow.StateFlow<Long>? = null,
) {
    val track = playbackState.currentTrack ?: return
    val isPlaying = playbackState.isPlaying

    val layoutDirection = LocalLayoutDirection.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val densityScale = density.density

    val offsetXAnimatable = remember { Animatable(0f) }
    var dragStartTime by remember { mutableLongStateOf(0L) }
    var totalDragDistance by remember { mutableFloatStateOf(0f) }
    val animationSpec = remember {
        spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
    }

    var measuredWidthPx by remember { mutableIntStateOf(0) }
    val autoSwipeThreshold = remember(measuredWidthPx, densityScale) {
        if (measuredWidthPx > 0) {
            (measuredWidthPx * 0.28f).roundToInt().coerceAtLeast((56f * densityScale).roundToInt())
        } else {
            (90f * densityScale).roundToInt()
        }
    }

    // iOS 26 style press response: the whole glass pill grows slightly while touched.
    val pressInteractionSource = remember { MutableInteractionSource() }
    val isPressed by pressInteractionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 1.04f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "accessoryPressScale",
    )

    // Finger-tracking glow
    val interactiveHighlight = remember(coroutineScope) { InteractiveHighlight(animationScope = coroutineScope) }
    val perimeterState = rememberPillPerimeterState(
        playbackState = playbackState,
        onSeek = onSeek,
        playbackPositionMsFlow = playbackPositionMsFlow,
    )

    var measuredHeightPx by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier.pillPerimeterHost(
            state = perimeterState,
            pressScale = pressScale,
        ),
    ) {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = surfaceModifier
                .onSizeChanged {
                    measuredHeightPx = it.height
                    measuredWidthPx = it.width
                }
                .then(interactiveHighlight.modifier)
                .clipToBounds()
                .then(interactiveHighlight.gestureModifier)
                .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragStartTime = System.currentTimeMillis()
                        totalDragDistance = 0f
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            offsetXAnimatable.animateTo(0f, animationSpec)
                        }
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        val adjustedDragAmount =
                            if (layoutDirection == LayoutDirection.Rtl) -dragAmount else dragAmount
                        totalDragDistance += abs(adjustedDragAmount)
                        coroutineScope.launch {
                            offsetXAnimatable.snapTo(offsetXAnimatable.value + adjustedDragAmount)
                        }
                    },
                    onDragEnd = {
                        val dragDuration = System.currentTimeMillis() - dragStartTime
                        val velocity = if (dragDuration > 0) totalDragDistance / dragDuration else 0f
                        val currentOffset = offsetXAnimatable.value
                        val dragged = abs(currentOffset)

                        val shouldChangeSong = dragged > autoSwipeThreshold ||
                                (velocity > 0.55f && dragged > autoSwipeThreshold * 0.25f)

                        if (shouldChangeSong) {
                            if (currentOffset > 0) {
                                onSkipPrevious()
                            } else {
                                onSkipNext()
                            }
                        }
                        coroutineScope.launch {
                            offsetXAnimatable.animateTo(0f, animationSpec)
                        }
                    },
                )
            }
    ) {
        val measuredHeight = with(density) { measuredHeightPx.toDp() }
        val artSize = when {
            isDesktopBar -> 34.dp
            isInline -> 36.dp
            else -> (measuredHeight * 0.62f).coerceIn(36.dp, 56.dp)
        }
        val artCornerRadius = when {
            isDesktopBar || isInline -> 9.dp
            else -> (artSize * 0.24f).coerceAtLeast(8.dp)
        }
        val controlSize = when {
            isDesktopBar -> 30.dp
            isInline -> 36.dp
            else -> (measuredHeight * 0.55f).coerceIn(36.dp, 48.dp)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetXAnimatable.value.roundToInt(), 0) }
                .clickable(
                    interactionSource = pressInteractionSource,
                    indication = null,
                    onClick = onClick,
                )
                .padding(
                    horizontal = when {
                        isDesktopBar -> 9.dp
                        isInline -> 10.dp
                        else -> 12.dp
                    },
                    vertical = when {
                        isDesktopBar -> 5.dp
                        isInline -> 6.dp
                        else -> 8.dp
                    },
                ),
        ) {
            // Artwork Thumbnail
            Box(
                modifier = Modifier
                    .size(artSize)
                    .clip(ContinuousRoundedRectangle(size = artCornerRadius))
                    .background(contentColor.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                if (track.artworkResId != null) {
                    Image(
                        painter = painterResource(id = track.artworkResId),
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                } else if (!track.artworkUri.isNullOrBlank()) {
                    AsyncImage(
                        model = track.artworkUri,
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = contentColor.copy(alpha = 0.6f),
                        modifier = Modifier.size(artSize * 0.5f)
                    )
                }
            }

            Spacer(Modifier.width(if (isInline) 8.dp else 10.dp))

            // Track details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = track.title,
                        fontFamily = GoogleSans,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = if (isInline) 13.sp else 14.sp,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (track.isExplicit) {
                        ExplicitBadge(
                            tint = contentColor,
                            compact = true
                        )
                    }
                }
                Text(
                    text = track.artist,
                    fontFamily = PlusJakartaSans,
                    fontSize = if (isInline) 10.5.sp else 11.5.sp,
                    color = contentColor.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (!isInline && !isDesktopBar) {
                // Song elapsed / total time (replaces removed waveform bars)
                Spacer(Modifier.width(8.dp))
                MiniPlayerTimeText(
                    state = perimeterState,
                    contentColor = contentColor,
                )
            }

            Spacer(Modifier.width(if (isInline || isDesktopBar) 6.dp else 10.dp))

            if (isDesktopBar) {
                IconButton(
                    onClick = onSkipPrevious,
                    modifier = Modifier.size(controlSize),
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = contentColor,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }

            // Play / Pause Button
            IconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier.size(controlSize),
            ) {
                AnimatedPlayPauseIcon(
                    isPlaying = isPlaying,
                    tint = contentColor,
                    size = when {
                        isDesktopBar -> 21.dp
                        isInline -> 22.dp
                        else -> 26.dp
                    },
                )
            }

            if (!isInline) {
                // Skip Next Button
                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier.size(controlSize),
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = contentColor,
                        modifier = Modifier.size(if (isDesktopBar) 19.dp else 24.dp),
                    )
                }
            }
        }
        }

        PillPerimeterOverlay(
            state = perimeterState,
            contentColor = contentColor,
            modifier = Modifier.matchParentSize(),
        )
    }
}
