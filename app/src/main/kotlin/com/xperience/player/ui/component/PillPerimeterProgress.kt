package com.xperience.player.ui.component

import android.graphics.Path as AndroidPath
import android.graphics.PathMeasure
import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.xperience.player.domain.model.PlaybackState
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.player.formatTrackDuration
import com.xperience.player.ui.theme.ArtworkColorExtractor
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAccentColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor

private const val PERIMETER_SAMPLE_COUNT = 256
private const val TOP_CENTER_REFINE_STEPS = 16
private const val CLOSEST_REFINE_STEPS = 8

private const val LOADING_CYCLE_SECONDS = 1.05f
private const val LOADING_BASE_LAPS_PER_SEC = 0.52f
private const val LOADING_CYCLE_GAIN = 0.44f
private const val LOADING_MIN_SWEEP = 0.08f
private const val LOADING_MAX_SWEEP_DELTA = 0.44f

private const val BORDER_HOLD_TIMEOUT_MS = 190L
private val BORDER_GRAB_BAND = 22.dp
internal val MiniPlayerPillShape: Shape = ContinuousRoundedRectangle(percent = 50)

/**
 * Formats `"elapsed / total"` for the compact mini player display (e.g. `"3:07 / 4:14"`).
 */
internal fun formatMiniPlayerTime(positionMs: Long, durationMs: Long): String {
    val clampedPos = if (durationMs > 0L) {
        positionMs.coerceIn(0L, durationMs)
    } else {
        positionMs.coerceAtLeast(0L)
    }
    val totalLabel = if (durationMs > 0L) formatTrackDuration(durationMs) else "--:--"
    return "${formatTrackDuration(clampedPos)} / $totalLabel"
}

/**
 * Wraps any normalized perimeter fraction into the half-open range `[0f, 1f)`.
 */
internal fun wrapFraction(fraction: Float): Float {
    val wrapped = ((fraction % 1f) + 1f) % 1f
    return if (wrapped >= 1f) 0f else wrapped
}

/**
 * Returns the signed shortest circular delta in `[-0.5f, 0.5f]` from [fromFraction] to [toFraction].
 */
internal fun shortestCircularDelta(fromFraction: Float, toFraction: Float): Float {
    var delta = wrapFraction(toFraction) - wrapFraction(fromFraction)
    if (delta > 0.5f) delta -= 1f
    if (delta < -0.5f) delta += 1f
    return delta
}

internal fun easeInOutCubic(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return if (x < 0.5f) {
        4f * x * x * x
    } else {
        val inv = -2f * x + 2f
        1f - (inv * inv * inv) / 2f
    }
}

/**
 * Progressive clockwise perimeter loading arc represented by its normalized start and sweep `[0f, 1f)`.
 */
internal data class LoadingArcSegment(
    val startFraction: Float,
    val sweepFraction: Float,
)

/**
 * Computes a continuous, C1-smooth, progressive loading arc that sweeps around the pill border
 * slightly under once per cycle while rhythmically expanding and contracting like a real Material
 * indeterminate indicator.
 */
internal fun computeLoadingArc(elapsedSeconds: Float): LoadingArcSegment {
    val safeTime = elapsedSeconds.coerceAtLeast(0f)
    val totalCycles = safeTime / LOADING_CYCLE_SECONDS
    val cycleIndex = floor(totalCycles)
    val cyclePhase = totalCycles - cycleIndex

    val baseStart = safeTime * LOADING_BASE_LAPS_PER_SEC + cycleIndex * LOADING_CYCLE_GAIN
    val startUnwrapped: Float
    val sweep: Float

    if (cyclePhase < 0.5f) {
        val grow = easeInOutCubic(cyclePhase * 2f)
        startUnwrapped = baseStart
        sweep = LOADING_MIN_SWEEP + grow * LOADING_MAX_SWEEP_DELTA
    } else {
        val shrink = easeInOutCubic((cyclePhase - 0.5f) * 2f)
        startUnwrapped = baseStart + shrink * LOADING_CYCLE_GAIN
        sweep = LOADING_MIN_SWEEP + (1f - shrink) * LOADING_MAX_SWEEP_DELTA
    }

    return LoadingArcSegment(
        startFraction = wrapFraction(startUnwrapped),
        sweepFraction = sweep.coerceIn(0.02f, 0.96f),
    )
}

/**
 * Calibrates the extracted album AmbientColor so the glowing pill perimeter stands out vibrantly
 * over dark or light LiquidGlass surfaces without washing out into white or muddy dark tones.
 */
internal fun adjustAmbientColorForPerimeter(color: Color, isLightTheme: Boolean): Color {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(color.toArgb(), hsv)
    val isAchromatic = hsv[1] < 0.08f
    if (isLightTheme) {
        if (!isAchromatic) {
            hsv[1] = hsv[1].coerceIn(0.55f, 1f)
        }
        hsv[2] = hsv[2].coerceIn(0.42f, 0.74f)
    } else {
        if (!isAchromatic) {
            hsv[1] = hsv[1].coerceIn(0.45f, 1f)
        }
        hsv[2] = hsv[2].coerceIn(0.82f, 0.98f)
    }
    return Color(AndroidColor.HSVToColor((color.alpha * 255f).roundToInt().coerceIn(0, 255), hsv))
}

internal data class PerimeterHit(
    val fraction: Float,
    val distancePx: Float,
)

/**
 * Pre-sampled closed contour geometry for a pill [Shape], parameterized clockwise starting from
 * the top-center midpoint `(width / 2f, 0f)`.
 */
internal class PillPerimeterGeometry private constructor(
    private val nativePath: AndroidPath,
    private val pathMeasure: PathMeasure,
    val totalLengthPx: Float,
    private val topCenterDistancePx: Float,
    private val directionSign: Float,
    private val sampleX: FloatArray,
    private val sampleY: FloatArray,
) {
    private val posBuffer = FloatArray(2)
    private val segmentAndroidPath = AndroidPath()
    val segmentPath: Path = segmentAndroidPath.asComposePath()

    private fun wrapDistance(distancePx: Float): Float {
        if (totalLengthPx <= 0f) return 0f
        val wrapped = ((distancePx % totalLengthPx) + totalLengthPx) % totalLengthPx
        return if (wrapped >= totalLengthPx) 0f else wrapped
    }

    private fun fractionToContourDistance(fraction: Float): Float {
        return wrapDistance(topCenterDistancePx + directionSign * wrapFraction(fraction) * totalLengthPx)
    }

    /**
     * Returns the contour point at normalized clockwise [fraction] (`0f` = top-center).
     */
    fun positionAt(fraction: Float): Offset {
        val dist = fractionToContourDistance(fraction)
        pathMeasure.getPosTan(dist, posBuffer, null)
        return Offset(posBuffer[0], posBuffer[1])
    }

    /**
     * Populates [segmentPath] with the clockwise border slice starting at [startFraction] and
     * spanning [sweepFraction] (`0f..1f`), bridging across any underlying contour seam cleanly.
     */
    fun prepareSegmentPath(startFraction: Float, sweepFraction: Float): Path {
        segmentAndroidPath.rewind()
        val clampedSweep = sweepFraction.coerceIn(0f, 1f)
        if (clampedSweep <= 0.0002f) return segmentPath
        if (clampedSweep >= 0.9998f) {
            segmentAndroidPath.set(nativePath)
            return segmentPath
        }

        val rawStartDist = fractionToContourDistance(startFraction)
        val sweepLen = clampedSweep * totalLengthPx
        val forwardStartDist = if (directionSign >= 0f) {
            rawStartDist
        } else {
            wrapDistance(rawStartDist - sweepLen)
        }
        val forwardEndDist = forwardStartDist + sweepLen
        if (forwardEndDist <= totalLengthPx) {
            pathMeasure.getSegment(forwardStartDist, forwardEndDist, segmentAndroidPath, true)
        } else {
            pathMeasure.getSegment(forwardStartDist, totalLengthPx, segmentAndroidPath, true)
            pathMeasure.getSegment(
                0f,
                (forwardEndDist - totalLengthPx).coerceAtMost(totalLengthPx),
                segmentAndroidPath,
                false,
            )
        }
        return segmentPath
    }

    /**
     * Finds the closest point on the pill perimeter to [point]. When [hintFraction] is supplied,
     * restricts candidate search to `±maxHalfWindowFraction` around [hintFraction] so rapid drags
     * on rounded end-caps or thin pills never jump across opposite edges.
     */
    fun closestFraction(
        point: Offset,
        hintFraction: Float? = null,
        maxHalfWindowFraction: Float = 0.35f,
    ): PerimeterHit {
        var bestIdx = 0
        var bestDistSq = Float.MAX_VALUE

        if (hintFraction == null) {
            for (i in 0 until PERIMETER_SAMPLE_COUNT) {
                val dx = point.x - sampleX[i]
                val dy = point.y - sampleY[i]
                val distSq = dx * dx + dy * dy
                if (distSq < bestDistSq) {
                    bestDistSq = distSq
                    bestIdx = i
                }
            }
        } else {
            val centerIdx = ((wrapFraction(hintFraction) * PERIMETER_SAMPLE_COUNT).roundToInt()) % PERIMETER_SAMPLE_COUNT
            val halfWindow = (maxHalfWindowFraction.coerceIn(0.05f, 0.5f) * PERIMETER_SAMPLE_COUNT).roundToInt()
            for (offset in -halfWindow..halfWindow) {
                val idx = ((centerIdx + offset) % PERIMETER_SAMPLE_COUNT + PERIMETER_SAMPLE_COUNT) % PERIMETER_SAMPLE_COUNT
                val dx = point.x - sampleX[idx]
                val dy = point.y - sampleY[idx]
                val distSq = dx * dx + dy * dy
                if (distSq < bestDistSq) {
                    bestDistSq = distSq
                    bestIdx = idx
                }
            }
        }

        // Sub-sample ternary search between neighboring indices so slow scrubbing feels analog-smooth.
        val stepFrac = 1f / PERIMETER_SAMPLE_COUNT.toFloat()
        var lo = bestIdx * stepFrac - stepFrac
        var hi = bestIdx * stepFrac + stepFrac
        for (iter in 0 until CLOSEST_REFINE_STEPS) {
            val m1 = lo + (hi - lo) / 3f
            val m2 = hi - (hi - lo) / 3f
            val p1 = positionAt(m1)
            val p2 = positionAt(m2)
            val d1 = (point.x - p1.x) * (point.x - p1.x) + (point.y - p1.y) * (point.y - p1.y)
            val d2 = (point.x - p2.x) * (point.x - p2.x) + (point.y - p2.y) * (point.y - p2.y)
            if (d1 < d2) {
                hi = m2
            } else {
                lo = m1
            }
        }

        val refinedFraction = wrapFraction((lo + hi) * 0.5f)
        val refinedPoint = positionAt(refinedFraction)
        val distancePx = hypot(point.x - refinedPoint.x, point.y - refinedPoint.y)
        return PerimeterHit(fraction = refinedFraction, distancePx = distancePx)
    }

    companion object {
        fun create(
            shape: Shape,
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density,
        ): PillPerimeterGeometry? {
            if (size.width <= 1f || size.height <= 1f) return null
            val composePath = when (val outline = shape.createOutline(size, layoutDirection, density)) {
                is Outline.Generic -> outline.path
                is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
            }
            val nativePath = AndroidPath(composePath.asAndroidPath())
            val pathMeasure = PathMeasure(nativePath, true)
            val totalLength = pathMeasure.length
            if (totalLength <= 1f) return null

            val targetX = size.width * 0.5f
            val halfH = size.height * 0.5f
            val pos = FloatArray(2)

            fun sampleAtRawDist(rawDist: Float): Pair<Float, Float> {
                val d = ((rawDist % totalLength) + totalLength) % totalLength
                pathMeasure.getPosTan(d, pos, null)
                return pos[0] to pos[1]
            }

            // Coarse search restricted to upper half of contour to locate top-center x = width/2.
            var bestRawDist = 0f
            var bestCost = Float.MAX_VALUE
            val coarseStep = totalLength / PERIMETER_SAMPLE_COUNT.toFloat()
            for (i in 0 until PERIMETER_SAMPLE_COUNT) {
                val d = i * coarseStep
                val (sx, sy) = sampleAtRawDist(d)
                if (sy <= halfH) {
                    val cost = abs(sx - targetX) + sy * 0.05f
                    if (cost < bestCost) {
                        bestCost = cost
                        bestRawDist = d
                    }
                }
            }

            // Refine top-center distance to sub-pixel precision.
            var lo = bestRawDist - coarseStep
            var hi = bestRawDist + coarseStep
            for (iter in 0 until TOP_CENTER_REFINE_STEPS) {
                val m1 = lo + (hi - lo) / 3f
                val m2 = hi - (hi - lo) / 3f
                val (x1, y1) = sampleAtRawDist(m1)
                val (x2, y2) = sampleAtRawDist(m2)
                val c1 = if (y1 <= halfH) abs(x1 - targetX) else Float.MAX_VALUE
                val c2 = if (y2 <= halfH) abs(x2 - targetX) else Float.MAX_VALUE
                if (c1 < c2) {
                    hi = m2
                } else {
                    lo = m1
                }
            }
            val topCenterDist = (((lo + hi) * 0.5f % totalLength) + totalLength) % totalLength

            // Detect underlying contour winding so our parameterization always runs clockwise.
            val (tx0, _) = sampleAtRawDist(topCenterDist)
            val (txNext, _) = sampleAtRawDist(topCenterDist + (totalLength * 0.01f).coerceAtLeast(1f))
            val directionSign = if (txNext >= tx0) 1f else -1f

            val sampleX = FloatArray(PERIMETER_SAMPLE_COUNT)
            val sampleY = FloatArray(PERIMETER_SAMPLE_COUNT)
            for (i in 0 until PERIMETER_SAMPLE_COUNT) {
                val frac = i.toFloat() / PERIMETER_SAMPLE_COUNT.toFloat()
                val rawD = (((topCenterDist + directionSign * frac * totalLength) % totalLength) + totalLength) % totalLength
                pathMeasure.getPosTan(rawD, pos, null)
                sampleX[i] = pos[0]
                sampleY[i] = pos[1]
            }

            return PillPerimeterGeometry(
                nativePath = nativePath,
                pathMeasure = pathMeasure,
                totalLengthPx = totalLength,
                topCenterDistancePx = topCenterDist,
                directionSign = directionSign,
                sampleX = sampleX,
                sampleY = sampleY,
            )
        }
    }
}

/**
 * Coordinates the LiquidGlass mini player's perimeter progress, border hold-to-scrub gesture,
 * jelly entry/exit springs, progressive loading animation, and extracted album AmbientColor.
 */
@Stable
internal class PillPerimeterState(
    initialAmbientColor: Color,
    initialPlaybackState: PlaybackState,
    private val hapticFeedback: HapticFeedback,
    borderGrabBandPx: Float,
) {
    internal var geometry by mutableStateOf<PillPerimeterGeometry?>(null)
        private set

    var isScrubbing by mutableStateOf(false)
        private set

    internal var scrubFraction by mutableFloatStateOf(0f)
        private set

    internal var pendingSeekFraction by mutableStateOf<Float?>(null)

    internal val scrubMorphAnim = Animatable(0f)
    internal val jellyScaleXAnim = Animatable(1f)
    internal val jellyScaleYAnim = Animatable(1f)
    internal val loadingBlendAnim = Animatable(if (initialPlaybackState.isLoading) 1f else 0f)
    internal var loadingElapsedSeconds by mutableFloatStateOf(0f)

    internal var ambientColor by mutableStateOf(initialAmbientColor)
    internal var latestDurationMs by mutableLongStateOf(initialPlaybackState.durationMs.coerceAtLeast(0L))
    internal var latestPositionMs by mutableLongStateOf(initialPlaybackState.currentPositionMs.coerceAtLeast(0L))
    internal var latestPlaybackFraction by mutableFloatStateOf(initialPlaybackState.progressFraction)
    internal var latestIsLoading by mutableStateOf(initialPlaybackState.isLoading)
    internal var latestOnSeek: (Float) -> Unit = {}

    private var lastMeasuredSize: IntSize = IntSize.Zero
    private var lastDensityValue: Float = -1f
    private var lastLayoutDirection: LayoutDirection? = null
    private var lastPerimeterAnchorFraction: Float = 0f

    val canSeek: Boolean
        get() = latestDurationMs > 0L && !latestIsLoading

    val displayFraction: Float
        get() = when {
            isScrubbing -> scrubFraction
            pendingSeekFraction != null -> pendingSeekFraction!!
            else -> latestPlaybackFraction
        }.coerceIn(0f, 1f)

    val displayedPositionMs: Long
        get() = when {
            isScrubbing -> (scrubFraction * latestDurationMs).toLong()
            pendingSeekFraction != null -> (pendingSeekFraction!! * latestDurationMs).toLong()
            else -> latestPositionMs.coerceAtLeast(0L)
        }

    val jellyScaleX: Float
        get() = jellyScaleXAnim.value

    val jellyScaleY: Float
        get() = jellyScaleYAnim.value

    fun updateSize(
        size: IntSize,
        layoutDirection: LayoutDirection,
        density: Density,
    ) {
        if (size == lastMeasuredSize &&
            lastDensityValue == density.density &&
            lastLayoutDirection == layoutDirection
        ) {
            return
        }
        lastMeasuredSize = size
        lastDensityValue = density.density
        lastLayoutDirection = layoutDirection
        geometry = PillPerimeterGeometry.create(
            shape = MiniPlayerPillShape,
            size = size.toSize(),
            layoutDirection = layoutDirection,
            density = density,
        )
    }

    fun onTrackChanged() {
        pendingSeekFraction = null
        if (isScrubbing) {
            isScrubbing = false
        }
    }

    internal fun beginScrub(initialAnchorFraction: Float) {
        scrubFraction = displayFraction
        lastPerimeterAnchorFraction = initialAnchorFraction
        isScrubbing = true
    }

    internal fun updateScrubFromPointer(point: Offset) {
        val geom = geometry ?: return
        val hit = geom.closestFraction(point = point, hintFraction = lastPerimeterAnchorFraction)
        val delta = shortestCircularDelta(lastPerimeterAnchorFraction, hit.fraction)
        lastPerimeterAnchorFraction = hit.fraction
        scrubFraction = (scrubFraction + delta).coerceIn(0f, 1f)
    }

    internal fun endScrub(commitSeek: Boolean) {
        if (!isScrubbing) return
        val target = scrubFraction.coerceIn(0f, 1f)
        isScrubbing = false
        if (commitSeek && latestDurationMs > 0L) {
            pendingSeekFraction = target
            latestOnSeek(target)
        }
    }

    val borderScrubModifier: Modifier = Modifier.pointerInput(this, borderGrabBandPx) {
        awaitEachGesture {
            val initialEvent = awaitPointerEvent(PointerEventPass.Initial)
            val down = initialEvent.changes.firstOrNull { it.changedToDown() } ?: return@awaitEachGesture
            val geom = geometry ?: return@awaitEachGesture
            if (!canSeek || isScrubbing) return@awaitEachGesture

            val downHit = geom.closestFraction(down.position)
            val bandPx = minOf(borderGrabBandPx, size.height * 0.36f)
            if (downHit.distancePx > bandPx) return@awaitEachGesture

            val pointerId = down.id
            val downPosition = down.position
            var lastPosition = down.position
            val holdDeadlineMs = down.uptimeMillis + BORDER_HOLD_TIMEOUT_MS
            val holdSlopPx = viewConfiguration.touchSlop * 1.35f

            var cancelledBeforeHold = false
            var holdReached = false
            while (!cancelledBeforeHold && !holdReached) {
                val remainingMs = holdDeadlineMs - SystemClock.uptimeMillis()
                if (remainingMs <= 0L) {
                    holdReached = true
                    break
                }
                val nextEvent = withTimeoutOrNull(remainingMs.coerceAtLeast(1L)) {
                    awaitPointerEvent(PointerEventPass.Initial)
                }
                if (nextEvent == null) {
                    holdReached = true
                    break
                }
                val tracked = nextEvent.changes.firstOrNull { it.id == pointerId }
                if (tracked == null || !tracked.pressed || tracked.isConsumed) {
                    cancelledBeforeHold = true
                } else {
                    lastPosition = tracked.position
                    if ((tracked.position - downPosition).getDistance() > holdSlopPx) {
                        cancelledBeforeHold = true
                    }
                }
            }

            if (cancelledBeforeHold || !holdReached || !canSeek) return@awaitEachGesture

            try {
                val anchorHit = geom.closestFraction(lastPosition)
                beginScrub(initialAnchorFraction = anchorHit.fraction)
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)

                var lastTrackedPosition = lastPosition
                var lastHapticSegment = (scrubFraction * 24f).roundToInt()

                while (isScrubbing) {
                    val moveEvent = awaitPointerEvent(PointerEventPass.Initial)
                    val tracked = moveEvent.changes.firstOrNull { it.id == pointerId }
                    // Consume all changes in Initial pass once scrub captures so child click/swipe gestures cancel cleanly.
                    moveEvent.changes.forEach { it.consume() }

                    if (tracked == null || !tracked.pressed) {
                        endScrub(commitSeek = true)
                        break
                    }

                    if (tracked.position != lastTrackedPosition) {
                        lastTrackedPosition = tracked.position
                        updateScrubFromPointer(tracked.position)
                        val currentSegment = (scrubFraction * 24f).roundToInt()
                        if (currentSegment != lastHapticSegment) {
                            lastHapticSegment = currentSegment
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                }
            } finally {
                if (isScrubbing) {
                    endScrub(commitSeek = false)
                }
            }
        }
    }
}

@Composable
internal fun rememberPillPerimeterState(
    playbackState: PlaybackState,
    onSeek: (Float) -> Unit,
): PillPerimeterState {
    val context = LocalContext.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val hapticFeedback = LocalHapticFeedback.current
    val fallbackAccent = LocalAccentColor.current
    val borderGrabBandPx = with(density) { BORDER_GRAB_BAND.toPx() }

    val state = remember(hapticFeedback, borderGrabBandPx) {
        PillPerimeterState(
            initialAmbientColor = fallbackAccent,
            initialPlaybackState = playbackState,
            hapticFeedback = hapticFeedback,
            borderGrabBandPx = borderGrabBandPx,
        )
    }

    val track = playbackState.currentTrack
    val rawAmbientColor = rememberAlbumAmbientColor(
        track = track,
        fallbackColor = fallbackAccent,
        appContext = context,
    )

    SideEffect {
        state.ambientColor = rawAmbientColor
        state.latestDurationMs = playbackState.durationMs.coerceAtLeast(0L)
        state.latestPositionMs = playbackState.currentPositionMs.coerceAtLeast(0L)
        state.latestPlaybackFraction = playbackState.progressFraction
        state.latestIsLoading = playbackState.isLoading
        state.latestOnSeek = onSeek
        val pending = state.pendingSeekFraction
        if (pending != null && abs(playbackState.progressFraction - pending) < 0.025f) {
            state.pendingSeekFraction = null
        }
    }

    LaunchedEffect(track?.id) {
        state.onTrackChanged()
    }

    // Safety timeout in case ExoPlayer seek settles outside the early-clear tolerance window.
    LaunchedEffect(state.pendingSeekFraction) {
        if (state.pendingSeekFraction != null) {
            delay(450L)
            state.pendingSeekFraction = null
        }
    }

    // Smooth crossfade into and out of progressive loading sweep with zero per-frame recomposition.
    var isLoadingClockActive by remember { mutableStateOf(playbackState.isLoading) }
    val showLoading = playbackState.isLoading && !state.isScrubbing
    LaunchedEffect(showLoading) {
        if (showLoading) {
            isLoadingClockActive = true
        }
        state.loadingBlendAnim.animateTo(
            targetValue = if (showLoading) 1f else 0f,
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        )
        if (!showLoading) {
            isLoadingClockActive = false
            state.loadingElapsedSeconds = 0f
        }
    }

    // Frame-clock ticker active only while loading indicator is visible (zero idle overhead).
    LaunchedEffect(isLoadingClockActive) {
        if (isLoadingClockActive) {
            var lastNanos = withFrameNanos { it }
            while (isActive) {
                withFrameNanos { frameNanos ->
                    val dt = ((frameNanos - lastNanos).coerceIn(0L, 100_000_000L)) / 1_000_000_000f
                    lastNanos = frameNanos
                    state.loadingElapsedSeconds += dt
                }
            }
        }
    }

    // Tactile jelly spring for both entering and exiting border scrub mode.
    var hasSeenScrubTransition by remember { mutableStateOf(false) }
    LaunchedEffect(state.isScrubbing) {
        if (!hasSeenScrubTransition) {
            hasSeenScrubTransition = true
            return@LaunchedEffect
        }
        if (state.isScrubbing) {
            launch {
                state.scrubMorphAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.40f, stiffness = 420f),
                )
            }
            launch {
                state.jellyScaleXAnim.animateTo(1.028f, tween(65, easing = FastOutSlowInEasing))
                state.jellyScaleXAnim.animateTo(1.012f, spring(dampingRatio = 0.36f, stiffness = 380f))
            }
            launch {
                state.jellyScaleYAnim.animateTo(0.968f, tween(65, easing = FastOutSlowInEasing))
                state.jellyScaleYAnim.animateTo(1.012f, spring(dampingRatio = 0.36f, stiffness = 380f))
            }
        } else {
            launch {
                state.scrubMorphAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = 0.44f, stiffness = 390f),
                )
            }
            launch {
                state.jellyScaleXAnim.animateTo(0.978f, tween(55, easing = FastOutSlowInEasing))
                state.jellyScaleXAnim.animateTo(1f, spring(dampingRatio = 0.36f, stiffness = 380f))
            }
            launch {
                state.jellyScaleYAnim.animateTo(1.028f, tween(55, easing = FastOutSlowInEasing))
                state.jellyScaleYAnim.animateTo(1f, spring(dampingRatio = 0.36f, stiffness = 380f))
            }
        }
    }

    return state
}

/**
 * Unclipped outer container modifier that applies press + jelly scale, tracks geometry without
 * recreating it across frames, and intercepts long-press drags along the pill perimeter.
 */
@Composable
internal fun Modifier.pillPerimeterHost(
    state: PillPerimeterState,
    pressScale: Float,
): Modifier {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    return this
        .graphicsLayer {
            val effectivePress = if (state.isScrubbing) 1f else pressScale
            scaleX = effectivePress * state.jellyScaleX
            scaleY = effectivePress * state.jellyScaleY
        }
        .onSizeChanged { state.updateSize(it, layoutDirection, density) }
        .then(state.borderScrubModifier)
}

@Composable
private fun rememberAlbumAmbientColor(
    track: TrackItem?,
    fallbackColor: Color,
    appContext: android.content.Context,
): Color {
    var targetAmbientColor by remember(track?.id, track?.artworkUri) {
        mutableStateOf(ArtworkColorExtractor.getCachedAccentColor(track) ?: fallbackColor)
    }

    LaunchedEffect(track?.id, track?.artworkUri, fallbackColor) {
        if (track == null) {
            targetAmbientColor = fallbackColor
            return@LaunchedEffect
        }
        val cached = ArtworkColorExtractor.getCachedAccentColor(track)
        if (cached != null) {
            targetAmbientColor = cached
        }
        val extracted = runCatching {
            ArtworkColorExtractor.extractAccentColor(appContext, track)
        }.getOrNull()
        targetAmbientColor = extracted ?: fallbackColor
    }

    val animatedColor by animateColorAsState(
        targetValue = targetAmbientColor,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "miniPlayerAmbientColor",
    )
    return animatedColor
}

/**
 * Unclipped sibling overlay drawing the album-illuminated perimeter progress line, loading pulse,
 * and interactive jelly-bounce scrub thumb right on the LiquidGlass pill border.
 */
@Composable
internal fun PillPerimeterOverlay(
    state: PillPerimeterState,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    val isLightTheme = contentColor.luminance() < 0.5f
    Canvas(modifier = modifier) {
        val geom = state.geometry ?: return@Canvas
        drawPillPerimeterProgress(
            geometry = geom,
            ambientColor = state.ambientColor,
            progressFraction = state.displayFraction,
            loadingBlend = state.loadingBlendAnim.value,
            loadingElapsedSeconds = state.loadingElapsedSeconds,
            scrubMorph = state.scrubMorphAnim.value,
            isLightTheme = isLightTheme,
        )
    }
}

/**
 * Displays live `"elapsed / total"` song time (e.g. `"3:07 / 4:14"`) in the former waveform slot,
 * updating continuously during playback and border scrub via snapshot observation.
 */
@Composable
internal fun MiniPlayerTimeText(
    state: PillPerimeterState,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    val isLightTheme = contentColor.luminance() < 0.5f
    val textColor = if (state.isScrubbing) {
        adjustAmbientColorForPerimeter(state.ambientColor, isLightTheme = isLightTheme)
    } else {
        contentColor.copy(alpha = 0.78f)
    }

    val timeLabel by remember(state) {
        derivedStateOf {
            formatMiniPlayerTime(
                positionMs = state.displayedPositionMs,
                durationMs = state.latestDurationMs,
            )
        }
    }

    Text(
        text = timeLabel,
        fontFamily = GoogleSans,
        fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp,
        color = textColor,
        maxLines = 1,
        softWrap = false,
        style = TextStyle(fontFeatureSettings = "tnum"),
        modifier = modifier.wrapContentWidth(),
    )
}

/**
 * Draws the LiquidGlass mini player's perimeter progress indicator starting at top-center and
 * sweeping clockwise, including:
 * - Album [ambientColor] illumination + multi-pass ambient bloom
 * - Subtle guide track and thickened stroke + glowing circle thumb during interactive border scrub
 * - Progressive clockwise loading sweep during track preparation/buffering
 */
internal fun DrawScope.drawPillPerimeterProgress(
    geometry: PillPerimeterGeometry,
    ambientColor: Color,
    progressFraction: Float,
    loadingBlend: Float,
    loadingElapsedSeconds: Float,
    scrubMorph: Float,
    isLightTheme: Boolean,
) {
    val glowColor = adjustAmbientColorForPerimeter(ambientColor, isLightTheme = isLightTheme)
    val clampedLoading = loadingBlend.coerceIn(0f, 1f)
    val playbackVisibility = (1f - clampedLoading).coerceIn(0f, 1f)
    val positiveMorph = scrubMorph.coerceAtLeast(0f)
    val clampedMorph = scrubMorph.coerceIn(0f, 1f)

    // Allow subtle overshoot > 1f on stroke/thumb for the tactile jelly pop-in without negative sizes.
    val idleStrokePx = 2.4.dp.toPx()
    val scrubTargetStrokePx = 4.5.dp.toPx()
    val strokeWidthPx = (idleStrokePx + (scrubTargetStrokePx - idleStrokePx) * positiveMorph)
        .coerceAtLeast(idleStrokePx * 0.85f)

    // 1. Subtle perimeter scrub guide track when holding/dragging the border.
    if (clampedMorph > 0.01f && playbackVisibility > 0.01f) {
        val guidePath = geometry.prepareSegmentPath(startFraction = 0f, sweepFraction = 1f)
        drawPath(
            path = guidePath,
            color = glowColor.copy(alpha = (if (isLightTheme) 0.24f else 0.18f) * clampedMorph * playbackVisibility),
            style = Stroke(width = strokeWidthPx * 0.72f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }

    // 2. Clockwise playback progress line starting at top-center.
    val displayFraction = progressFraction.coerceIn(0f, 1f)
    if (playbackVisibility > 0.01f && displayFraction > 0.001f) {
        val progressPath = geometry.prepareSegmentPath(
            startFraction = 0f,
            sweepFraction = displayFraction,
        )
        drawGlowingPerimeterStroke(
            path = progressPath,
            color = glowColor,
            coreWidthPx = strokeWidthPx,
            alphaMultiplier = playbackVisibility,
            emphasis = clampedMorph,
            isLightTheme = isLightTheme,
        )
    }

    // 3. Fast progressive loading sweep while resolving/buffering a track.
    if (clampedLoading > 0.01f) {
        val arc = computeLoadingArc(loadingElapsedSeconds)
        val loadingStrokePx = 2.8.dp.toPx() + (scrubTargetStrokePx - 2.8.dp.toPx()) * clampedMorph * 0.4f
        val loadingPath = geometry.prepareSegmentPath(
            startFraction = arc.startFraction,
            sweepFraction = arc.sweepFraction,
        )
        drawGlowingPerimeterStroke(
            path = loadingPath,
            color = glowColor,
            coreWidthPx = loadingStrokePx,
            alphaMultiplier = clampedLoading,
            emphasis = 0.55f,
            isLightTheme = isLightTheme,
        )
    }

    // 4. Interactive circle thumb at the end of the generated progress line during scrub mode.
    val thumbScale = positiveMorph * playbackVisibility
    if (thumbScale > 0.02f) {
        val thumbCenter = geometry.positionAt(displayFraction)
        val haloRadiusPx = (15.dp.toPx() * thumbScale).coerceAtLeast(1f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    glowColor.copy(alpha = 0.58f * thumbScale.coerceAtMost(1f)),
                    glowColor.copy(alpha = 0.18f * thumbScale.coerceAtMost(1f)),
                    Color.Transparent,
                ),
                center = thumbCenter,
                radius = haloRadiusPx,
            ),
            radius = haloRadiusPx,
            center = thumbCenter,
        )

        drawCircle(
            color = Color.Black.copy(alpha = 0.24f * thumbScale.coerceAtMost(1f)),
            radius = (7.4.dp.toPx() * thumbScale).coerceAtLeast(0.5f),
            center = thumbCenter,
        )

        drawCircle(
            color = glowColor.copy(alpha = thumbScale.coerceAtMost(1f)),
            radius = (6.3.dp.toPx() * thumbScale).coerceAtLeast(0.5f),
            center = thumbCenter,
        )

        drawCircle(
            color = Color.White.copy(alpha = thumbScale.coerceAtMost(1f)),
            radius = (2.6.dp.toPx() * thumbScale).coerceAtLeast(0.3f),
            center = thumbCenter,
        )
    }
}

private fun DrawScope.drawGlowingPerimeterStroke(
    path: Path,
    color: Color,
    coreWidthPx: Float,
    alphaMultiplier: Float,
    emphasis: Float,
    isLightTheme: Boolean,
) {
    val outerGlowAlpha = (if (isLightTheme) 0.16f else 0.20f) + 0.12f * emphasis
    val innerGlowAlpha = (if (isLightTheme) 0.30f else 0.36f) + 0.14f * emphasis

    drawPath(
        path = path,
        color = color.copy(alpha = outerGlowAlpha * alphaMultiplier),
        style = Stroke(
            width = coreWidthPx + 7.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
    drawPath(
        path = path,
        color = color.copy(alpha = innerGlowAlpha * alphaMultiplier),
        style = Stroke(
            width = coreWidthPx + 2.8.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
    val luminousCore = if (isLightTheme) {
        color
    } else {
        lerp(color, Color.White, 0.16f)
    }
    drawPath(
        path = path,
        color = luminousCore.copy(alpha = 0.96f * alphaMultiplier),
        style = Stroke(
            width = coreWidthPx,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}
