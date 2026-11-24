package com.xperience.player.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.xperience.player.domain.model.UserPlaylist
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors

private val DefaultPlaylistCoverShape = ContinuousRoundedRectangle(percent = 22)

/**
 * Renders the cover artwork for a UserPlaylist.
 * Matches the PC port behaviour:
 * 1. If customCoverImagePath is set, displays the custom image.
 * 2. If no custom cover and at least 2 distinct track covers exist, renders a 2x2 collage.
 * 3. If only 1 distinct cover exists, fills the square with that cover.
 * 4. If no track covers exist, displays a stylized fallback playlist icon with vibrant ambient gradient.
 */
@Composable
fun PlaylistCoverImage(
    playlist: UserPlaylist,
    modifier: Modifier = Modifier,
    shape: Shape = DefaultPlaylistCoverShape,
    fallbackIconSize: Dp = 32.dp
) {
    val appColors = LocalAppColors.current
    val accentColor = LocalAccentColor.current
    val distinctCovers = playlist.getDistinctCollageCovers()

    Box(
        modifier = modifier
            .clip(shape)
            .background(if (appColors.isDark) Color(0xFF1E222B) else Color(0xFFE8ECF2))
            .border(
                width = 0.8.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (appColors.isDark) 0.25f else 0.6f),
                        Color.White.copy(alpha = if (appColors.isDark) 0.05f else 0.15f)
                    )
                ),
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            // Case 1: Custom User Cover Image
            playlist.hasCustomCover -> {
                AsyncImage(
                    model = playlist.customCoverImagePath,
                    contentDescription = playlist.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Case 2: 2x2 Collage Grid (2 or more distinct covers)
            distinctCovers.size >= 2 -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(Color(0xFF222632))
                        ) {
                            playlist.collageCover1?.let { uri ->
                                AsyncImage(
                                    model = uri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(1.5.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(Color(0xFF222632))
                        ) {
                            playlist.collageCover2?.let { uri ->
                                AsyncImage(
                                    model = uri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(1.5.dp))

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(Color(0xFF222632))
                        ) {
                            playlist.collageCover3?.let { uri ->
                                AsyncImage(
                                    model = uri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(1.5.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(Color(0xFF222632))
                        ) {
                            playlist.collageCover4?.let { uri ->
                                AsyncImage(
                                    model = uri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }

            // Case 3: Exactly 1 distinct cover (full bleed)
            distinctCovers.size == 1 -> {
                AsyncImage(
                    model = distinctCovers[0],
                    contentDescription = playlist.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Case 4: No tracks or no track artwork available
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    accentColor.copy(alpha = if (appColors.isDark) 0.22f else 0.15f),
                                    if (appColors.isDark) Color(0xFF1B1E26) else Color(0xFFF0F3F8)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(fallbackIconSize)
                    )
                }
            }
        }
    }
}
