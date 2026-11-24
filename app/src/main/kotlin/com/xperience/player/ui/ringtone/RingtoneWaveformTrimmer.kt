package com.xperience.player.ui.ringtone

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xperience.player.service.ringtone.RingtoneMetadataSanitizer
import com.xperience.player.service.ringtone.TrimRange
import com.xperience.player.service.ringtone.WaveformData
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.theme.AppThemeColors
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.PlusJakartaSans
import kotlin.math.abs
import kotlin.math.roundToLong

private enum class ActiveDragTarget {
    NONE,
    START_HANDLE,
    END_HANDLE,
    WINDOW_BODY
}

private val TrimmerContainerShape = ContinuousRoundedRectangle(size = 20.dp)

/**
 * Interactive audio waveform timeline with draggable Start & End trim handles,
 * draggable active selection window, fade-in/fade-out visual envelope overlays,
 * tap-to-seek playhead, and time ruler.
 */
@Composable
fun RingtoneWaveformTrimmer(
    waveform: WaveformData,
    durationMs: Long,
    trimRange: TrimRange,
    previewPositionMs: Long,
    isPreviewPlaying: Boolean,
    fadeInEnabled: Boolean,
    fadeOutEnabled: Boolean,
    fadeDurationMs: Long,
    isExtractingWaveform: Boolean,
    onStartChange: (Long) -> Unit,
    onEndChange: (Long) -> Unit,
    onShiftWindow: (Long) -> Unit,
    onSeekPreview: (Long) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = LocalAccentColor.current,
    appColors: AppThemeColors = LocalAppColors.current
) {
    val latestDurationMs by rememberUpdatedState(durationMs)
    val latestRange by rememberUpdatedState(trimRange)
    val latestOnStart by rememberUpdatedState(onStartChange)
    val latestOnEnd by rememberUpdatedState(onEndChange)
    val latestOnShift by rememberUpdatedState(onShiftWindow)
    val latestOnSeek by rememberUpdatedState(onSeekPreview)

    val density = LocalDensity.current
    val handleVisualWidthPx = with(density) { 14.dp.toPx() }
    val handleHitSlopPx = with(density) { 30.dp.toPx() }

    var activeDrag by remember { mutableStateOf(ActiveDragTarget.NONE) }

    val surfaceFill = if (appColors.isDark) Color(0xFF171A23) else Color(0xFFF0F3F8)
    val borderColor = if (appColors.isDark) Color(0x1EFFFFFF) else Color(0x16000000)
    val inactiveBarColor = if (appColors.isDark) Color(0xFF3A3F50) else Color(0xFFCBD4E1)
    val playheadColor = if (appColors.isDark) Color.White else Color(0xFF0F172A)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(TrimmerContainerShape)
            .background(surfaceFill)
            .border(1.dp, borderColor, TrimmerContainerShape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        // Subtle status caption above waveform
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when {
                    isExtractingWaveform -> "Analizando forma de onda…"
                    waveform.isRealWaveform -> "Forma de onda real · Arrastra los bordes"
                    else -> "Línea de tiempo · Arrastra los bordes"
                },
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                color = appColors.textSecondary
            )
            if (activeDrag != ActiveDragTarget.NONE) {
                val badgeText = when (activeDrag) {
                    ActiveDragTarget.START_HANDLE -> "Inicio ${RingtoneMetadataSanitizer.formatTrimTimeMs(trimRange.startMs)}"
                    ActiveDragTarget.END_HANDLE -> "Fin ${RingtoneMetadataSanitizer.formatTrimTimeMs(trimRange.endMs)}"
                    ActiveDragTarget.WINDOW_BODY -> "${RingtoneMetadataSanitizer.formatTrimTimeMs(trimRange.startMs)} – ${RingtoneMetadataSanitizer.formatTrimTimeMs(trimRange.endMs)}"
                    ActiveDragTarget.NONE -> ""
                }
                Text(
                    text = badgeText,
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    color = accentColor
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(modifier = Modifier.fillMaxWidth()) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val dur = latestDurationMs
                            if (dur <= 0L || size.width <= 0) return@detectTapGestures
                            val inset = handleVisualWidthPx
                            val usableW = (size.width - inset * 2f).coerceAtLeast(1f)
                            val frac = ((offset.x - inset) / usableW).coerceIn(0f, 1f)
                            val tappedMs = (frac * dur).roundToLong()
                            val range = latestRange
                            if (tappedMs in range.startMs..range.endMs) {
                                latestOnSeek(tappedMs)
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        var windowDragResidualPx = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                windowDragResidualPx = 0f
                                val dur = latestDurationMs
                                if (dur <= 0L || size.width <= 0) {
                                    activeDrag = ActiveDragTarget.NONE
                                    return@detectHorizontalDragGestures
                                }
                                val inset = handleVisualWidthPx
                                val usableW = (size.width - inset * 2f).coerceAtLeast(1f)
                                val range = latestRange
                                val startX = inset + range.startFraction(dur) * usableW
                                val endX = inset + range.endFraction(dur) * usableW

                                val distStart = abs(offset.x - startX)
                                val distEnd = abs(offset.x - endX)

                                activeDrag = when {
                                    distStart <= handleHitSlopPx && distStart <= distEnd ->
                                        ActiveDragTarget.START_HANDLE
                                    distEnd <= handleHitSlopPx ->
                                        ActiveDragTarget.END_HANDLE
                                    offset.x > startX && offset.x < endX ->
                                        ActiveDragTarget.WINDOW_BODY
                                    distStart < distEnd ->
                                        ActiveDragTarget.START_HANDLE
                                    else ->
                                        ActiveDragTarget.END_HANDLE
                                }
                            },
                            onDragEnd = {
                                activeDrag = ActiveDragTarget.NONE
                                windowDragResidualPx = 0f
                            },
                            onDragCancel = {
                                activeDrag = ActiveDragTarget.NONE
                                windowDragResidualPx = 0f
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                val dur = latestDurationMs
                                if (dur <= 0L || size.width <= 0 || activeDrag == ActiveDragTarget.NONE) {
                                    return@detectHorizontalDragGestures
                                }
                                change.consume()
                                val inset = handleVisualWidthPx
                                val usableW = (size.width - inset * 2f).coerceAtLeast(1f)

                                when (activeDrag) {
                                    ActiveDragTarget.START_HANDLE -> {
                                        val frac = ((change.position.x - inset) / usableW).coerceIn(0f, 1f)
                                        latestOnStart((frac * dur).roundToLong())
                                    }
                                    ActiveDragTarget.END_HANDLE -> {
                                        val frac = ((change.position.x - inset) / usableW).coerceIn(0f, 1f)
                                        latestOnEnd((frac * dur).roundToLong())
                                    }
                                    ActiveDragTarget.WINDOW_BODY -> {
                                        windowDragResidualPx += dragAmount
                                        val deltaMs = ((windowDragResidualPx / usableW) * dur).roundToLong()
                                        if (deltaMs != 0L) {
                                            val consumedPx = (deltaMs.toFloat() / dur.toFloat()) * usableW
                                            windowDragResidualPx -= consumedPx
                                            latestOnShift(deltaMs)
                                        }
                                    }
                                    ActiveDragTarget.NONE -> Unit
                                }
                            }
                        )
                    }
            ) {
                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) return@Canvas

                val inset = handleVisualWidthPx
                val usableW = (w - inset * 2f).coerceAtLeast(1f)
                val midY = h / 2f
                val maxBarH = h * 0.78f
                val minBarH = h * 0.10f

                val startFrac = trimRange.startFraction(durationMs)
                val endFrac = trimRange.endFraction(durationMs)
                val startX = inset + startFrac * usableW
                val endX = inset + endFrac * usableW
                val selectedWidth = (endX - startX).coerceAtLeast(2f)

                // 1. Highlighted active selection background glow
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = if (appColors.isDark) 0.22f else 0.16f),
                            accentColor.copy(alpha = if (appColors.isDark) 0.08f else 0.06f),
                            accentColor.copy(alpha = if (appColors.isDark) 0.20f else 0.14f)
                        )
                    ),
                    topLeft = Offset(startX, 4f),
                    size = Size(selectedWidth, h - 8f),
                    cornerRadius = CornerRadius(8f, 8f)
                )

                // 2. Amplitude waveform bars
                val peaks = waveform.peaks
                if (peaks.isNotEmpty()) {
                    val slotW = usableW / peaks.size.toFloat()
                    val barW = (slotW * 0.62f).coerceIn(2f, 8f)
                    val activeBrush = Brush.verticalGradient(
                        colors = listOf(
                            accentColor,
                            accentColor.copy(alpha = 0.72f),
                            accentColor
                        )
                    )
                    for (i in peaks.indices) {
                        val barCenterX = inset + (i + 0.5f) * slotW
                        val isSelected = barCenterX >= startX && barCenterX <= endX
                        val amplitude = peaks[i].coerceIn(0.08f, 1f)
                        val barH = (amplitude * maxBarH).coerceAtLeast(minBarH)
                        val topLeft = Offset(barCenterX - barW / 2f, midY - barH / 2f)
                        val barSize = Size(barW, barH)
                        val radius = CornerRadius(barW / 2f, barW / 2f)

                        if (isSelected) {
                            drawRoundRect(
                                brush = activeBrush,
                                topLeft = topLeft,
                                size = barSize,
                                cornerRadius = radius
                            )
                        } else {
                            drawRoundRect(
                                color = inactiveBarColor,
                                topLeft = topLeft,
                                size = barSize,
                                cornerRadius = radius
                            )
                        }
                    }
                }

                // 3. Dimmed curtains outside [startX, endX]
                val curtainColor = if (appColors.isDark) Color(0x5A0A0C12) else Color(0x40FFFFFF)
                if (startX > inset) {
                    drawRect(
                        color = curtainColor,
                        topLeft = Offset(inset, 4f),
                        size = Size((startX - inset).coerceAtLeast(0f), h - 8f)
                    )
                }
                if (endX < inset + usableW) {
                    drawRect(
                        color = curtainColor,
                        topLeft = Offset(endX, 4f),
                        size = Size((inset + usableW - endX).coerceAtLeast(0f), h - 8f)
                    )
                }

                // 4. Visual fade-in / fade-out envelope curves inside active region
                if (durationMs > 0L && trimRange.durationMs > 0L) {
                    val maxFadeMs = if (fadeInEnabled && fadeOutEnabled) {
                        trimRange.durationMs / 2L
                    } else {
                        trimRange.durationMs
                    }
                    if (fadeInEnabled && maxFadeMs > 0L) {
                        val effFade = minOf(fadeDurationMs, maxFadeMs)
                        val fadeW = (effFade.toFloat() / trimRange.durationMs.toFloat()) * selectedWidth
                        if (fadeW > 4f) {
                            val fadePath = Path().apply {
                                moveTo(startX, h - 8f)
                                cubicTo(
                                    startX + fadeW * 0.45f, h - 8f,
                                    startX + fadeW * 0.55f, 10f,
                                    startX + fadeW, 10f
                                )
                            }
                            drawPath(
                                path = fadePath,
                                color = Color.White.copy(alpha = if (appColors.isDark) 0.55f else 0.70f),
                                style = Stroke(width = 2.2f, cap = StrokeCap.Round)
                            )
                        }
                    }
                    if (fadeOutEnabled && maxFadeMs > 0L) {
                        val effFade = minOf(fadeDurationMs, maxFadeMs)
                        val fadeW = (effFade.toFloat() / trimRange.durationMs.toFloat()) * selectedWidth
                        if (fadeW > 4f) {
                            val fadePath = Path().apply {
                                moveTo(endX - fadeW, 10f)
                                cubicTo(
                                    endX - fadeW * 0.55f, 10f,
                                    endX - fadeW * 0.45f, h - 8f,
                                    endX, h - 8f
                                )
                            }
                            drawPath(
                                path = fadePath,
                                color = Color.White.copy(alpha = if (appColors.isDark) 0.55f else 0.70f),
                                style = Stroke(width = 2.2f, cap = StrokeCap.Round)
                            )
                        }
                    }
                }

                // 5. Top & bottom glowing frame rails connecting handles
                drawLine(
                    color = accentColor,
                    start = Offset(startX, 5f),
                    end = Offset(endX, 5f),
                    strokeWidth = 4f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = accentColor,
                    start = Offset(startX, h - 5f),
                    end = Offset(endX, h - 5f),
                    strokeWidth = 4f,
                    cap = StrokeCap.Round
                )

                // 6. Live animated preview playhead inside active window
                if (durationMs > 0L && (isPreviewPlaying || previewPositionMs in trimRange.startMs..trimRange.endMs)) {
                    val clampedPos = previewPositionMs.coerceIn(trimRange.startMs, trimRange.endMs)
                    val playheadX = inset + (clampedPos.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) * usableW
                    if (isPreviewPlaying) {
                        drawLine(
                            color = accentColor.copy(alpha = 0.40f),
                            start = Offset(playheadX, 6f),
                            end = Offset(playheadX, h - 6f),
                            strokeWidth = 10f,
                            cap = StrokeCap.Round
                        )
                    }
                    drawLine(
                        color = playheadColor,
                        start = Offset(playheadX, 6f),
                        end = Offset(playheadX, h - 6f),
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = playheadColor,
                        radius = 5.5f,
                        center = Offset(playheadX, 7f)
                    )
                }

                // 7. Start & End draggable trim handles (sleek rounded pills with grip lines)
                val drawHandle: (Float, Boolean, Boolean) -> Unit = { anchorX, isStart, isActive ->
                    val handleW = handleVisualWidthPx
                    val left = if (isStart) anchorX - handleW else anchorX
                    val radius = CornerRadius(handleW * 0.45f, handleW * 0.45f)
                    val fillColor = if (isActive) Color.White else accentColor
                    val gripColor = if (isActive) Color(0xFF0F1012) else Color(0xFF0A0C12)

                    drawRoundRect(
                        color = fillColor,
                        topLeft = Offset(left, 2f),
                        size = Size(handleW, h - 4f),
                        cornerRadius = radius
                    )
                    val cx = left + handleW / 2f
                    drawLine(
                        color = gripColor.copy(alpha = 0.85f),
                        start = Offset(cx, midY - 12f),
                        end = Offset(cx, midY + 12f),
                        strokeWidth = 2.6f,
                        cap = StrokeCap.Round
                    )
                }

                drawHandle(startX, true, activeDrag == ActiveDragTarget.START_HANDLE)
                drawHandle(endX, false, activeDrag == ActiveDragTarget.END_HANDLE)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Time ruler below waveform
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "0:00.0",
                fontFamily = PlusJakartaSans,
                fontSize = 10.5.sp,
                color = appColors.textTertiary
            )
            Text(
                text = RingtoneMetadataSanitizer.formatTrimTimeMs(durationMs / 2L),
                fontFamily = PlusJakartaSans,
                fontSize = 10.5.sp,
                color = appColors.textTertiary
            )
            Text(
                text = RingtoneMetadataSanitizer.formatTrimTimeMs(durationMs),
                fontFamily = PlusJakartaSans,
                fontSize = 10.5.sp,
                color = appColors.textTertiary
            )
        }
    }
}
