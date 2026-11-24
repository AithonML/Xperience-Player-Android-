package com.xperience.player.ui.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xperience.player.ui.component.GlassComponent
import com.xperience.player.ui.component.LocalGlassEffectConfig
import com.xperience.player.ui.component.isGlassAllowed
import com.xperience.player.ui.component.liquidGlass
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.components.readableAccentColor
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

val DEFAULT_ALPHABET_INDEX: List<String> = ('A'..'Z').map { it.toString() } + "#"

private val ScrubberRailShape = ContinuousRoundedRectangle(percent = 50)
private val PreviewBubbleShape = ContinuousRoundedRectangle(size = 20.dp)

/**
 * Physical right-edge Wave Alphabet Scrubber for long alphabetical directories.
 *
 * Dragging or tapping along the index rail displaces adjacent letters leftward along a smooth
 * raised-cosine bell curve so the active letter peeks out clear of the thumb, displays a floating
 * LiquidGlass preview bubble with bouncy spring feedback, emits [HapticFeedbackType.TextHandleMove]
 * ticks as sections change, and reports the resolved section letter via [onLetterSelected].
 */
@Composable
fun WaveAlphabetScrubber(
    onLetterSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    letters: List<String> = DEFAULT_ALPHABET_INDEX,
    availableLetters: Set<String> = DEFAULT_ALPHABET_INDEX.toSet(),
    activeLetter: String? = null,
    onDragActiveChanged: (Boolean) -> Unit = {}
) {
    if (letters.isEmpty()) return

    val appColors = LocalAppColors.current
    val accentColor = LocalAccentColor.current
    val displayAccent = readableAccentColor(accentColor, appColors.isDark)
    val glassConfig = LocalGlassEffectConfig.current
    val useGlass = glassConfig.isEnabledFor(GlassComponent.NAV_BAR) && isGlassAllowed()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    var railHeightPx by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var targetWaveIndex by remember { mutableFloatStateOf(0f) }
    var currentPreviewLetter by remember(letters) {
        mutableStateOf(activeLetter ?: letters.first())
    }
    var lastEmittedLetter by remember { mutableStateOf<String?>(null) }

    // Smooth wave tracking & bouncy amplitude spring
    val animatedWaveIndex by animateFloatAsState(
        targetValue = targetWaveIndex,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = 750f
        ),
        label = "scrubberWaveIndex"
    )
    val waveAmplitude by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.42f,
            stiffness = 380f
        ),
        label = "scrubberWaveAmplitude"
    )
    val bubbleScale by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.36f,
            stiffness = 420f
        ),
        label = "scrubberBubbleScale"
    )
    val bubbleAlpha by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = if (isDragging) {
            spring(dampingRatio = 0.65f, stiffness = 460f)
        } else {
            tween(durationMillis = 160)
        },
        label = "scrubberBubbleAlpha"
    )

    // Extra "efecto rebote" pulse each time the previewed letter ticks while scrubbing
    val letterPopAnim = remember { Animatable(1f) }
    LaunchedEffect(currentPreviewLetter, isDragging) {
        if (isDragging) {
            letterPopAnim.snapTo(0.76f)
            letterPopAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.36f,
                    stiffness = 420f
                )
            )
        } else {
            letterPopAnim.snapTo(1f)
        }
    }

    LaunchedEffect(isDragging) {
        onDragActiveChanged(isDragging)
    }

    val maxWaveDisplacementPx = with(density) { 38.dp.toPx() }
    val bubbleSizeDp = 62.dp
    val bubbleSizePx = with(density) { bubbleSizeDp.toPx() }
    val bubbleLeftGapPx = with(density) { 82.dp.toPx() }

    fun resolveNearestAvailableLetter(rawIndex: Int): String {
        val rawLetter = letters[rawIndex.coerceIn(0, letters.lastIndex)]
        if (availableLetters.isEmpty() || rawLetter in availableLetters) {
            return rawLetter
        }
        val nearestAvailable = letters.withIndex()
            .filter { it.value in availableLetters }
            .minByOrNull { abs(it.index - rawIndex) }
        return nearestAvailable?.value ?: rawLetter
    }

    fun handleTouchY(yPx: Float) {
        if (railHeightPx <= 0f || letters.isEmpty()) return
        val clampedFraction = (yPx / railHeightPx).coerceIn(0f, 0.9999f)
        val continuousIndex = clampedFraction * (letters.size - 1).coerceAtLeast(1)
        targetWaveIndex = continuousIndex
        val snappedIdx = continuousIndex.roundToInt().coerceIn(0, letters.lastIndex)
        val resolved = resolveNearestAvailableLetter(snappedIdx)
        currentPreviewLetter = resolved
        if (resolved != lastEmittedLetter) {
            lastEmittedLetter = resolved
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onLetterSelected(resolved)
        }
    }

    Box(
        modifier = modifier.fillMaxHeight(),
        contentAlignment = Alignment.CenterEnd
    ) {
        // Floating LiquidGlass preview bubble aligned with the wave peak on drag
        if (bubbleAlpha > 0.01f && railHeightPx > 0f) {
            val fraction = if (letters.size > 1) {
                (animatedWaveIndex / (letters.size - 1).toFloat()).coerceIn(0f, 1f)
            } else {
                0.5f
            }
            val peakCenterY = fraction * railHeightPx
            val maxTopPx = (railHeightPx - bubbleSizePx).coerceAtLeast(0f)
            val topPx = (peakCenterY - bubbleSizePx / 2f).coerceIn(0f, maxTopPx)

            val bubbleGlassModifier = if (useGlass) {
                Modifier.liquidGlass(
                    config = glassConfig,
                    shape = PreviewBubbleShape,
                    highlightAlpha = 0.48f
                )
            } else {
                Modifier
                    .clip(PreviewBubbleShape)
                    .background(
                        Brush.linearGradient(
                            colors = if (appColors.isDark) {
                                listOf(Color(0xE81C2232), Color(0xD8141824))
                            } else {
                                listOf(Color(0xF5FFFFFF), Color(0xE8EEF2FF))
                            }
                        )
                    )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset {
                        IntOffset(
                            x = (-bubbleLeftGapPx * bubbleScale).roundToInt(),
                            y = topPx.roundToInt()
                        )
                    }
                    .size(bubbleSizeDp)
                    .graphicsLayer {
                        val combinedScale = bubbleScale * letterPopAnim.value
                        scaleX = combinedScale
                        scaleY = combinedScale
                        alpha = bubbleAlpha
                    }
                    .shadow(
                        elevation = 14.dp,
                        shape = PreviewBubbleShape,
                        ambientColor = displayAccent.copy(alpha = 0.45f),
                        spotColor = displayAccent.copy(alpha = 0.55f)
                    )
                    .clip(PreviewBubbleShape)
                    .then(bubbleGlassModifier)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                displayAccent.copy(alpha = if (appColors.isDark) 0.30f else 0.20f),
                                Color.Transparent
                            )
                        )
                    )
                    .border(
                        width = 1.2.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                displayAccent.copy(alpha = 0.80f),
                                Color.White.copy(alpha = if (appColors.isDark) 0.42f else 0.78f),
                                displayAccent.copy(alpha = 0.45f)
                            )
                        ),
                        shape = PreviewBubbleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = currentPreviewLetter,
                    color = if (useGlass) glassConfig.textColor else displayAccent,
                    fontFamily = GoogleSans,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Right-edge frosted touch rail
        val railBgColor = when {
            isDragging && appColors.isDark -> Color(0x66192030)
            isDragging -> Color(0xCCFFFFFF)
            appColors.isDark -> Color(0x2E141822)
            else -> Color(0x88FFFFFF)
        }
        val railBorderColor = when {
            isDragging -> displayAccent.copy(alpha = 0.52f)
            appColors.isDark -> Color.White.copy(alpha = 0.12f)
            else -> Color.Black.copy(alpha = 0.08f)
        }

        Box(
            modifier = Modifier
                .width(30.dp)
                .fillMaxHeight()
                .background(railBgColor, ScrubberRailShape)
                .border(0.75.dp, railBorderColor, ScrubberRailShape)
                .onSizeChanged { size ->
                    railHeightPx = size.height.toFloat()
                }
                .pointerInput(letters, availableLetters) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        lastEmittedLetter = null
                        isDragging = true
                        handleTouchY(down.position.y)

                        try {
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: event.changes.firstOrNull()
                                if (change != null) {
                                    if (change.pressed) {
                                        change.consume()
                                        handleTouchY(change.position.y)
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        } finally {
                            isDragging = false
                            lastEmittedLetter = null
                        }
                    }
                }
                .padding(vertical = 6.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val waveRadius = 3.8f
                letters.forEachIndexed { index, letter ->
                    val isAvailable = availableLetters.isEmpty() || letter in availableLetters
                    val isSelectedSection = if (isDragging) {
                        letter == currentPreviewLetter
                    } else {
                        letter == activeLetter
                    }

                    val dist = abs(index.toFloat() - animatedWaveIndex)
                    val influence = if (dist < waveRadius && waveAmplitude > 0.005f) {
                        val raisedCosine = 0.5f * (1f + cos(PI.toFloat() * (dist / waveRadius)))
                        raisedCosine * waveAmplitude
                    } else {
                        0f
                    }

                    val letterScale = 1f + 0.62f * influence
                    val letterTranslationX = -maxWaveDisplacementPx * influence

                    val textColor = when {
                        influence > 0.45f -> displayAccent
                        isSelectedSection && isAvailable -> displayAccent
                        isAvailable && appColors.isDark -> Color.White.copy(alpha = 0.88f)
                        isAvailable -> Color(0xFF1A1F2C)
                        appColors.isDark -> Color.White.copy(alpha = 0.26f)
                        else -> Color.Black.copy(alpha = 0.24f)
                    }

                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .weight(1f)
                            .graphicsLayer {
                                translationX = letterTranslationX
                                scaleX = letterScale
                                scaleY = letterScale
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelectedSection && !isDragging && isAvailable) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(displayAccent.copy(alpha = 0.20f))
                                    .border(0.7.dp, displayAccent.copy(alpha = 0.50f), CircleShape)
                            )
                        }
                        Text(
                            text = letter,
                            color = textColor,
                            fontFamily = GoogleSans,
                            fontSize = 9.5.sp,
                            fontWeight = if (isSelectedSection || influence > 0.35f) {
                                FontWeight.ExtraBold
                            } else {
                                FontWeight.SemiBold
                            },
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
