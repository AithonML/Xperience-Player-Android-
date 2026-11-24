package com.xperience.player.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xperience.player.domain.model.PlaybackState
import com.xperience.player.ui.component.ExplicitBadge
import com.xperience.player.ui.component.GlassCircleButton
import com.xperience.player.ui.component.LocalGlassEffectConfig
import com.xperience.player.ui.component.liquidGlass
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.PlusJakartaSans
import com.xperience.player.ui.utils.pressWobble
import kotlinx.coroutines.delay

private val MiniPlayerShape = ContinuousRoundedRectangle(percent = 50)
private val ThumbnailShape = ContinuousRoundedRectangle(percent = 32)
private val SearchPillShape = ContinuousRoundedRectangle(percent = 50)

@Composable
fun MiniPlayerBar(
    playbackState: PlaybackState,
    isSearchExpanded: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onMiniPlayerClick: () -> Unit,
    isLiked: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    playbackPositionMsFlow: kotlinx.coroutines.flow.StateFlow<Long>? = null
) {
    val track = playbackState.currentTrack
    val glassConfig = LocalGlassEffectConfig.current
    val accentColor = LocalAccentColor.current
    val appColors = LocalAppColors.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // Fluid spring animation with a satisfying subtle bounce at the end
    val searchExpandProgress by animateFloatAsState(
        targetValue = if (isSearchExpanded) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "searchExpandSpringBounce"
    )

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) {
            delay(180)
            try {
                focusRequester.requestFocus()
                keyboardController?.show()
            } catch (_: Exception) {}
        } else {
            keyboardController?.hide()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
    ) {
        val availableWidth = maxWidth

        if (track != null) {
            // BOTH MINI PLAYER AND SEARCH BUTTON SHARE THE ROW
            val spacing = 8.dp
            val usableWidth = availableWidth - spacing

            // Interpolate widths with spring bounce overshoot
            // When normal: MiniPlayer is full width (usableWidth - 52.dp), Search is 52.dp
            // When expanded: MiniPlayer contracts to 56.dp compact widget, Search expands to (usableWidth - 56.dp)
            val miniPlayerWidth = ((usableWidth - 52.dp) * (1f - searchExpandProgress) + 56.dp * searchExpandProgress)
                .coerceAtLeast(50.dp)
            val searchBarWidth = (52.dp * (1f - searchExpandProgress) + (usableWidth - 56.dp) * searchExpandProgress)
                .coerceAtLeast(50.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // =========================================================================
                // 1. MINI PLAYER CAPSULE (Contracts / Expands with fluid bounce)
                // =========================================================================
                Box(
                    modifier = Modifier
                        .width(miniPlayerWidth)
                        .height(56.dp)
                        .clip(MiniPlayerShape)
                        .liquidGlass(
                            config = glassConfig,
                            shape = MiniPlayerShape,
                            highlightAlpha = 0.35f
                        )
                        .clickable {
                            if (isSearchExpanded) {
                                onTogglePlayPause()
                            } else {
                                onMiniPlayerClick()
                            }
                        }
                ) {
                    if (searchExpandProgress < 0.45f) {
                        // FULL EXPANDED MINI PLAYER
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Artwork Thumbnail
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(ThumbnailShape)
                                    .background(if (appColors.isDark) Color(0xFF22242B) else Color(0xFFE2E8F0)),
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
                                        tint = accentColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            // Song Title & Artist/Album Subtitle
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        text = track.title,
                                        color = appColors.glassTextPrimary,
                                        fontFamily = GoogleSans,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (track.isExplicit) {
                                        ExplicitBadge(
                                            tint = appColors.glassTextPrimary,
                                            compact = true
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${track.artist} • ${track.album}",
                                    color = appColors.glassTextSecondary,
                                    fontFamily = PlusJakartaSans,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Playback Controls
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                if (onToggleFavorite != null) {
                                    val heartSource = remember { MutableInteractionSource() }
                                    IconButton(
                                        onClick = onToggleFavorite,
                                        modifier = Modifier
                                            .pressWobble(heartSource)
                                            .size(28.dp),
                                        interactionSource = heartSource
                                    ) {
                                        Icon(
                                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = if (isLiked) "Unlike" else "Like",
                                            tint = if (isLiked) Color(0xFFFF2D55) else appColors.glassTextPrimary.copy(alpha = 0.7f),
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }

                                val prevSource = remember { MutableInteractionSource() }
                                IconButton(
                                    onClick = onSkipPrevious,
                                    modifier = Modifier
                                        .pressWobble(prevSource)
                                        .size(30.dp),
                                    interactionSource = prevSource
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipPrevious,
                                        contentDescription = "Previous",
                                        tint = appColors.glassTextPrimary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }

                                val playSource = remember { MutableInteractionSource() }
                                IconButton(
                                    onClick = onTogglePlayPause,
                                    modifier = Modifier
                                        .pressWobble(playSource)
                                        .size(32.dp),
                                    interactionSource = playSource
                                ) {
                                    Icon(
                                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                        tint = appColors.glassTextPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                val nextSource = remember { MutableInteractionSource() }
                                IconButton(
                                    onClick = onSkipNext,
                                    modifier = Modifier
                                        .pressWobble(nextSource)
                                        .size(30.dp),
                                    interactionSource = nextSource
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = "Next",
                                        tint = appColors.glassTextPrimary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        }

                        // Bottom dynamic progress bar (leaf collecting playbackPositionMsFlow without recomposing MiniPlayerBar)
                        MiniPlayerBottomProgressBar(
                            playbackPositionMsFlow = playbackPositionMsFlow,
                            fallbackFraction = playbackState.progressFraction,
                            durationMs = playbackState.durationMs,
                            accentColor = accentColor,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 58.dp, end = 16.dp, bottom = 4.dp)
                                .fillMaxWidth()
                                .height(2.dp)
                        )
                    } else {
                        // CONTRACTED COMPACT MINI PLAYER (Thumbnail + Play/Pause Pulse)
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(if (appColors.isDark) Color(0xFF22242B) else Color(0xFFE2E8F0)),
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
                                        tint = accentColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Subtle dark overlay with play/pause icon
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0x55000000)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = appColors.glassTextPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // =========================================================================
                // 2. SEARCH BUTTON / SEARCH BAR CAPSULE (Expands with fluid spring bounce)
                // =========================================================================
                if (searchExpandProgress < 0.35f) {
                    // Circular Search Button
                    GlassCircleButton(
                        onClick = onToggleSearch,
                        size = 52.dp
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = appColors.glassTextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    // Expanded Search Bar
                    Box(
                        modifier = Modifier
                            .width(searchBarWidth)
                            .height(56.dp)
                            .clip(SearchPillShape)
                            .liquidGlass(
                                config = glassConfig,
                                shape = SearchPillShape,
                                highlightAlpha = 0.38f
                            )
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search songs, artists...",
                                        color = appColors.glassTextSecondary,
                                        fontFamily = PlusJakartaSans,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = onSearchQueryChange,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(focusRequester),
                                    textStyle = TextStyle(
                                        color = appColors.glassTextPrimary,
                                        fontFamily = GoogleSans,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.5.sp
                                    ),
                                    singleLine = true,
                                    cursorBrush = SolidColor(accentColor),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = {
                                        onSearchSubmit(searchQuery)
                                        keyboardController?.hide()
                                    })
                                )
                            }

                            // Clear or Close button
                            IconButton(
                                onClick = {
                                    if (searchQuery.isNotEmpty()) {
                                        onSearchQueryChange("")
                                    } else {
                                        onCloseSearch()
                                    }
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = appColors.glassTextPrimary.copy(alpha = 0.85f),
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // NO MUSIC PLAYING: SEARCH BAR TAKES CENTER OR FULL EXPANDED WIDTH
            if (!isSearchExpanded) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    GlassCircleButton(
                        onClick = onToggleSearch,
                        size = 52.dp
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = appColors.glassTextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(SearchPillShape)
                        .liquidGlass(
                            config = glassConfig,
                            shape = SearchPillShape,
                            highlightAlpha = 0.38f
                        )
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search songs, artists, albums...",
                                    color = appColors.glassTextSecondary,
                                    fontFamily = PlusJakartaSans,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            BasicTextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChange,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                textStyle = TextStyle(
                                    color = appColors.glassTextPrimary,
                                    fontFamily = GoogleSans,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.5.sp
                                ),
                                singleLine = true,
                                cursorBrush = SolidColor(accentColor),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    onSearchSubmit(searchQuery)
                                    keyboardController?.hide()
                                })
                            )
                        }

                        IconButton(
                            onClick = {
                                if (searchQuery.isNotEmpty()) {
                                    onSearchQueryChange("")
                                } else {
                                    onCloseSearch()
                                }
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = appColors.glassTextPrimary.copy(alpha = 0.85f),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniPlayerBottomProgressBar(
    playbackPositionMsFlow: kotlinx.coroutines.flow.StateFlow<Long>?,
    fallbackFraction: Float,
    durationMs: Long,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val positionState = playbackPositionMsFlow?.collectAsState()
    val safeDurationMs = durationMs.coerceAtLeast(0L)
    val brush = remember(accentColor) {
        Brush.horizontalGradient(
            listOf(
                accentColor,
                accentColor.copy(alpha = 0.85f),
                accentColor.copy(alpha = 0.45f)
            )
        )
    }
    androidx.compose.foundation.Canvas(modifier = modifier.clip(CircleShape)) {
        val progress = if (positionState != null && safeDurationMs > 0L) {
            (positionState.value.coerceAtLeast(0L).toFloat() / safeDurationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            fallbackFraction.coerceIn(0f, 1f)
        }
        val activeW = size.width * progress
        if (activeW > 0.5f) {
            drawRect(
                brush = brush,
                size = androidx.compose.ui.geometry.Size(activeW, size.height)
            )
        }
    }
}
