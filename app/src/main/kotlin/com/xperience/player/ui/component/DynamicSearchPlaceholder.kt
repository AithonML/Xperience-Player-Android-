/**
 * Convx / Xperience Music Player
 * Licensed under GPL-3.0
 */
package com.xperience.player.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay

@Composable
fun DynamicSearchPlaceholder(
    searchSource: SearchSource,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle(),
    color: Color = Color.Unspecified
) {
    if (searchSource == SearchSource.LOCAL) {
        Text(
            text = "Search local library...",
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
        )
        return
    }

    val prompts = remember {
        listOf(
            "Search songs, albums, artists...",
            "What's on your mind?",
            "Today's pick",
            "Trending tracks",
            "Search YouTube Music",
            "Find your vibe..."
        )
    }

    var currentIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(3500)
            currentIndex = (currentIndex + 1) % prompts.size
        }
    }

    AnimatedContent(
        targetState = currentIndex,
        transitionSpec = {
            fadeIn(animationSpec = tween(600)) togetherWith fadeOut(animationSpec = tween(400))
        },
        label = "dynamicSearchPlaceholder",
        modifier = modifier
    ) { index ->
        Text(
            text = prompts[index],
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
