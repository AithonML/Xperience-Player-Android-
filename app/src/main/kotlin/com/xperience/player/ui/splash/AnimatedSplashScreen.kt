package com.xperience.player.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.XperiencePlayerTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Full-screen animated Splash Screen featuring:
 * - Seamless coordinate/theme handoff from Android 12+ `core-splashscreen`
 * - Dynamic electric-blue and violet Aurora illumination along viewport borders
 * - Staggered progressive contour tracing + fill reveal + spring bounce of the 3 logo paths
 *   (stylized "X", musical note, and upper acoustic wave)
 * - Non-blocking synchronization with parallel Home catalog loading and smooth fade-out exit
 */
@Composable
fun AnimatedSplashScreen(
    isHomeReady: Boolean,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    systemSplashIconSizePx: Int? = null,
    splashHandoffReady: Boolean = true,
    onFirstFrameDrawn: () -> Unit = {},
    onSplashFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPreview = LocalInspectionMode.current
    val density = LocalDensity.current

    val latestIsHomeReady by rememberUpdatedState(isHomeReady)
    val latestOnFirstFrameDrawn by rememberUpdatedState(onFirstFrameDrawn)
    val latestOnSplashFinished by rememberUpdatedState(onSplashFinished)

    var firstFrameNotified by remember { mutableStateOf(false) }
    SideEffect {
        if (!firstFrameNotified) {
            firstFrameNotified = true
            latestOnFirstFrameDrawn()
        }
    }

    val backgroundColor = if (isDarkTheme) {
        SplashPalette.DarkBackground
    } else {
        SplashPalette.LightBackground
    }
    val logoFillColor = if (isDarkTheme) {
        SplashPalette.DarkLogoTint
    } else {
        SplashPalette.LightLogoTint
    }

    // Match Android core-splashscreen's iconView box (default 288.dp) so initial handoff has 0px jump
    val logoContainerSize = remember(systemSplashIconSizePx, density) {
        val px = systemSplashIconSizePx?.takeIf { it > 0 }
        if (px != null) with(density) { px.toDp() } else 288.dp
    }

    // Core animation states (start settled in Compose Preview so design tools show full artwork)
    val initialProgress = if (isPreview) 1f else 0f
    val auroraIntensity = remember { Animatable(initialProgress) }
    val xStrokeProgress = remember { Animatable(initialProgress) }
    val noteStrokeProgress = remember { Animatable(initialProgress) }
    val waveStrokeProgress = remember { Animatable(initialProgress) }
    val xFillAlpha = remember { Animatable(initialProgress) }
    val noteFillAlpha = remember { Animatable(initialProgress) }
    val waveFillAlpha = remember { Animatable(initialProgress) }
    val strokeGlowAlpha = remember { Animatable(if (isPreview) 0.16f else 1f) }
    val logoScale = remember { Animatable(1f) }
    val pulseRingProgress = remember { Animatable(0f) }
    val wordmarkAlpha = remember { Animatable(initialProgress) }

    val exitAlpha = remember { Animatable(1f) }
    val exitScale = remember { Animatable(1f) }
    var isExiting by remember { mutableStateOf(false) }

    LaunchedEffect(splashHandoffReady) {
        if (isPreview || !splashHandoffReady) return@LaunchedEffect

        // Phase 1 & 2: Aurora edge bloom + staggered progressive path drawing + bounce lock-in
        coroutineScope {
            launch {
                auroraIntensity.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 680, easing = FastOutSlowInEasing)
                )
            }

            // Trace 1: Stylized "X"
            launch {
                xStrokeProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing)
                )
            }
            launch {
                delay(340L)
                xFillAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                )
            }

            // Trace 2: Musical Eighth Note
            launch {
                delay(145L)
                noteStrokeProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
                )
            }
            launch {
                delay(470L)
                noteFillAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                )
            }

            // Trace 3: Upper Acoustic Wave Crest
            launch {
                delay(270L)
                waveStrokeProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
                )
            }
            launch {
                delay(560L)
                waveFillAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                )
            }

            // Soften neon trace once solid logo reveals + tactile spring bounce + expanding aura pulse
            launch {
                delay(610L)
                launch {
                    logoScale.animateTo(
                        targetValue = 0.94f,
                        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing)
                    )
                    logoScale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        initialVelocity = 2.6f
                    )
                }
                launch {
                    pulseRingProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 720, easing = FastOutSlowInEasing)
                    )
                }
                launch {
                    strokeGlowAlpha.animateTo(
                        targetValue = 0.16f,
                        animationSpec = tween(durationMillis = 440, easing = FastOutSlowInEasing)
                    )
                }
                launch {
                    delay(80L)
                    wordmarkAlpha.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing)
                    )
                }
            }
        }

        // Phase 3: Wait for Home initial data (already loading in background) with a safe upper cap
        if (!latestIsHomeReady) {
            withTimeoutOrNull(2_400L) {
                snapshotFlow { latestIsHomeReady }.first { it }
            }
        }

        // Brief settle beat after lock-in, then smooth fade-out revealing Home underneath
        delay(120L)
        isExiting = true
        coroutineScope {
            launch {
                exitAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
                )
            }
            launch {
                exitScale.animateTo(
                    targetValue = 1.045f,
                    animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
                )
            }
        }
        latestOnSplashFinished()
    }

    val xStrokeBrush = remember(isDarkTheme) {
        Brush.linearGradient(
            colors = if (isDarkTheme) {
                listOf(SplashPalette.ElectricCyan, SplashPalette.CobaltBlue, SplashPalette.ElectricViolet)
            } else {
                listOf(SplashPalette.ElectricBlue, SplashPalette.LightIndigo, SplashPalette.ElectricViolet)
            }
        )
    }
    val noteStrokeBrush = remember(isDarkTheme) {
        Brush.linearGradient(
            colors = if (isDarkTheme) {
                listOf(SplashPalette.CobaltBlue, SplashPalette.ElectricViolet, SplashPalette.NeonPurple)
            } else {
                listOf(SplashPalette.LightIndigo, SplashPalette.LightViolet, SplashPalette.ElectricBlue)
            }
        )
    }
    val waveStrokeBrush = remember(isDarkTheme) {
        Brush.linearGradient(
            colors = if (isDarkTheme) {
                listOf(SplashPalette.ElectricCyan, SplashPalette.NeonPurple)
            } else {
                listOf(SplashPalette.LightSkyElectric, SplashPalette.LightViolet)
            }
        )
    }

    // Consume pointer input while splash is active so touches don't trigger Home cards underneath
    val inputBlockerModifier = if (!isExiting) {
        Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    event.changes.forEach { it.consume() }
                }
            }
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(inputBlockerModifier)
            .graphicsLayer {
                alpha = exitAlpha.value
                scaleX = exitScale.value
                scaleY = exitScale.value
            }
            .background(backgroundColor)
    ) {
        // Dynamic electric-blue / violet Aurora edge glow
        AuroraBorderEffect(
            isDark = isDarkTheme,
            intensityState = auroraIntensity.asState(),
            modifier = Modifier.fillMaxSize()
        )

        // Centered brand logo matching core-splashscreen coordinates + progressive draw animation
        BoxWithConstraints(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .size(logoContainerSize)
                .graphicsLayer {
                    val s = logoScale.value
                    scaleX = s
                    scaleY = s
                }
        ) {
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }

            val scaledLogo = remember(widthPx, heightPx) {
                buildScaledXperienceLogo(widthPx = widthPx, heightPx = heightPx)
            }
            val xSegmentPath = remember(widthPx, heightPx) { Path() }
            val noteSegmentPath = remember(widthPx, heightPx) { Path() }
            val waveSegmentPath = remember(widthPx, heightPx) { Path() }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val glowAlpha = strokeGlowAlpha.value
                val blendMode = if (isDarkTheme) BlendMode.Screen else BlendMode.SrcOver
                val sparkColor = if (isDarkTheme) SplashPalette.ElectricCyan else SplashPalette.ElectricBlue

                // Subtle expanding Aurora pulse ring behind the logo on spring lock-in
                val ringT = pulseRingProgress.value
                if (ringT in 0.001f..0.999f) {
                    val maxR = size.minDimension * 0.44f
                    val ringRadius = maxR * (0.36f + 0.64f * ringT)
                    val ringAlpha = (1f - ringT) * (if (isDarkTheme) 0.48f else 0.28f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            0.0f to Color.Transparent,
                            0.70f to SplashPalette.ElectricViolet.copy(alpha = ringAlpha * 0.4f),
                            0.88f to SplashPalette.ElectricCyan.copy(alpha = ringAlpha),
                            1.0f to Color.Transparent,
                            center = center,
                            radius = ringRadius
                        ),
                        radius = ringRadius,
                        center = center,
                        blendMode = blendMode
                    )
                }

                // Soft behind-logo ambient aura once fill reveals
                val combinedFill = (xFillAlpha.value + noteFillAlpha.value + waveFillAlpha.value) / 3f
                if (combinedFill > 0.01f) {
                    val haloRadius = size.minDimension * 0.36f
                    val haloAlpha = combinedFill * (if (isDarkTheme) 0.18f else 0.08f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            0.0f to SplashPalette.ElectricBlue.copy(alpha = haloAlpha),
                            0.55f to SplashPalette.ElectricViolet.copy(alpha = haloAlpha * 0.45f),
                            1.0f to Color.Transparent,
                            center = center,
                            radius = haloRadius
                        ),
                        radius = haloRadius,
                        center = center,
                        blendMode = blendMode
                    )
                }

                // 1. Stylized "X"
                drawAnimatedLogoComponent(
                    fullPath = scaledLogo.xPath,
                    pathMeasure = scaledLogo.xMeasure,
                    totalLength = scaledLogo.xLength,
                    segmentPath = xSegmentPath,
                    strokeProgress = xStrokeProgress.value,
                    fillAlpha = xFillAlpha.value,
                    strokeGlowAlpha = glowAlpha,
                    fillColor = logoFillColor,
                    strokeBrush = xStrokeBrush,
                    sparkColor = sparkColor,
                    isDarkTheme = isDarkTheme
                )

                // 2. Musical Eighth Note
                drawAnimatedLogoComponent(
                    fullPath = scaledLogo.notePath,
                    pathMeasure = scaledLogo.noteMeasure,
                    totalLength = scaledLogo.noteLength,
                    segmentPath = noteSegmentPath,
                    strokeProgress = noteStrokeProgress.value,
                    fillAlpha = noteFillAlpha.value,
                    strokeGlowAlpha = glowAlpha,
                    fillColor = logoFillColor,
                    strokeBrush = noteStrokeBrush,
                    sparkColor = SplashPalette.ElectricViolet,
                    isDarkTheme = isDarkTheme
                )

                // 3. Upper Acoustic Wave
                drawAnimatedLogoComponent(
                    fullPath = scaledLogo.wavePath,
                    pathMeasure = scaledLogo.waveMeasure,
                    totalLength = scaledLogo.waveLength,
                    segmentPath = waveSegmentPath,
                    strokeProgress = waveStrokeProgress.value,
                    fillAlpha = waveFillAlpha.value,
                    strokeGlowAlpha = glowAlpha,
                    fillColor = logoFillColor,
                    strokeBrush = waveStrokeBrush,
                    sparkColor = sparkColor,
                    isDarkTheme = isDarkTheme
                )
            }
        }

        // Subtle brand wordmark anchored beneath the centered icon
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
                .graphicsLayer {
                    alpha = wordmarkAlpha.value
                    translationY = (1f - wordmarkAlpha.value) * 10.dp.toPx()
                }
        ) {
            Text(
                text = "XPERIENCE",
                color = logoFillColor.copy(alpha = 0.88f),
                fontFamily = GoogleSans,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "MUSIC PLAYER",
                color = (if (isDarkTheme) SplashPalette.ElectricCyan else SplashPalette.ElectricBlue).copy(alpha = 0.72f),
                fontFamily = GoogleSans,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 3.2.sp
            )
        }
    }
}

private fun DrawScope.drawAnimatedLogoComponent(
    fullPath: Path,
    pathMeasure: PathMeasure,
    totalLength: Float,
    segmentPath: Path,
    strokeProgress: Float,
    fillAlpha: Float,
    strokeGlowAlpha: Float,
    fillColor: Color,
    strokeBrush: Brush,
    sparkColor: Color,
    isDarkTheme: Boolean
) {
    val clampedFill = fillAlpha.coerceIn(0f, 1f)
    if (clampedFill > 0.005f) {
        drawPath(
            path = fullPath,
            color = fillColor.copy(alpha = clampedFill)
        )
    }

    val clampedStroke = strokeProgress.coerceIn(0f, 1f)
    if (clampedStroke > 0.005f && strokeGlowAlpha > 0.005f) {
        val drawDist = totalLength * clampedStroke
        segmentPath.reset()
        pathMeasure.getSegment(
            startDistance = 0f,
            stopDistance = drawDist,
            destination = segmentPath,
            startWithMoveTo = true
        )

        val outerGlowAlpha = strokeGlowAlpha * (if (isDarkTheme) 0.36f else 0.22f)
        drawPath(
            path = segmentPath,
            brush = strokeBrush,
            alpha = outerGlowAlpha,
            style = Stroke(
                width = 6.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        val coreStrokeAlpha = strokeGlowAlpha * (if (isDarkTheme) 0.95f else 0.78f)
        drawPath(
            path = segmentPath,
            brush = strokeBrush,
            alpha = coreStrokeAlpha,
            style = Stroke(
                width = 2.1.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Luminous laser tip spark while drawing
        if (clampedStroke in 0.02f..0.985f) {
            val headPos = pathMeasure.getPosition(drawDist)
            if (headPos != Offset.Unspecified) {
                drawCircle(
                    brush = Brush.radialGradient(
                        0.0f to Color.White.copy(alpha = 0.95f),
                        0.35f to sparkColor.copy(alpha = 0.85f),
                        1.0f to Color.Transparent,
                        center = headPos,
                        radius = 7.5.dp.toPx()
                    ),
                    radius = 7.5.dp.toPx(),
                    center = headPos
                )
            }
        }
    }
}

@Preview(name = "Splash Dark", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun AnimatedSplashDarkPreview() {
    XperiencePlayerTheme(darkTheme = true) {
        AnimatedSplashScreen(
            isHomeReady = false,
            isDarkTheme = true,
            onSplashFinished = {}
        )
    }
}

@Preview(name = "Splash Light", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun AnimatedSplashLightPreview() {
    XperiencePlayerTheme(darkTheme = false) {
        AnimatedSplashScreen(
            isHomeReady = false,
            isDarkTheme = false,
            onSplashFinished = {}
        )
    }
}
