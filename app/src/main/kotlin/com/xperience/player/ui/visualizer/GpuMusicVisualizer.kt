package com.xperience.player.ui.visualizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.domain.model.visualizer.VisualizerDefinition
import com.xperience.player.domain.model.visualizer.VisualizerRenderMode
import com.xperience.player.service.AudioVisualizerSampler
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-performance GPU-accelerated music visualizer control for Jetpack Compose.
 * Implements:
 * 1. NeonTerrainPeaks (3D perspective terrain, spectrum needles, glowing peak particles)
 * 2. HarmonicWireframeSphere (3D rotating spherical harmonic wireframe "See Your Music")
 * 3. AuroraPrismaticWaves (Flowing multi-harmonic ribbon waves & sparkle dust)
 * 4. RadialCyberPulsar (360-degree radial FFT equalizer & bass shockwaves)
 * 5. Cover Backdrop Only
 */
@Composable
fun GpuMusicVisualizer(
    definition: VisualizerDefinition,
    currentPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    currentTrack: TrackItem?,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    if (definition.renderMode == VisualizerRenderMode.None) {
        return
    }

    val sampler = remember { AudioVisualizerSampler() }
    LaunchedEffect(currentTrack?.id) {
        sampler.setTrack(currentTrack)
    }

    var phaseTime by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying, definition.speed) {
        val speed = definition.speed.toFloat().coerceIn(0.1f, 4.0f)
        var lastNano = 0L
        while (true) {
            withFrameNanos { now ->
                if (lastNano != 0L) {
                    val dt = ((now - lastNano) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                    val rate = if (isPlaying) 1.0f else 0.35f
                    phaseTime += dt * 1.5f * speed * rate
                }
                lastNano = now
            }
        }
    }

    val palette = remember(definition, accentColor) {
        resolvePalette(definition, accentColor)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        if (width <= 8f || height <= 8f) return@Canvas

        val frame = sampler.sample(currentPositionMs, durationMs, isPlaying, definition)

        // Render Selected Geometry Engine (preserves background tone and opacity identical to visualizers off)
        when (definition.renderMode) {
            VisualizerRenderMode.NeonTerrainPeaks -> {
                drawNeonTerrainPeaks(
                    width = width,
                    height = height,
                    definition = definition,
                    palette = palette,
                    frame = frame,
                    phaseTime = phaseTime
                )
            }
            VisualizerRenderMode.HarmonicWireframeSphere -> {
                drawHarmonicWireframeSphere(
                    width = width,
                    height = height,
                    definition = definition,
                    palette = palette,
                    frame = frame,
                    phaseTime = phaseTime
                )
            }
            VisualizerRenderMode.AuroraPrismaticWaves -> {
                drawAuroraPrismaticWaves(
                    width = width,
                    height = height,
                    definition = definition,
                    palette = palette,
                    frame = frame,
                    phaseTime = phaseTime
                )
            }
            VisualizerRenderMode.RadialCyberPulsar -> {
                drawRadialCyberPulsar(
                    width = width,
                    height = height,
                    definition = definition,
                    palette = palette,
                    frame = frame,
                    phaseTime = phaseTime
                )
            }
            VisualizerRenderMode.None -> Unit
        }
    }
}

// =========================================================================
// 1. NEON TERRAIN PEAKS 3D
// =========================================================================
private fun DrawScope.drawNeonTerrainPeaks(
    width: Float,
    height: Float,
    definition: VisualizerDefinition,
    palette: List<Color>,
    frame: AudioVisualizerSampler.SpectrumFrame,
    phaseTime: Float
) {
    val columns = definition.gridResolution.coerceIn(24, 64)
    val baseHorizonY = height * 0.62f
    val bands = frame.bands
    val peakHold = frame.peakHold

    // Pass A: Vertical Neon Spectrum Stems & Glowing Particle Tips
    for (c in 0 until columns) {
        val u = c.toFloat() / (columns - 1).coerceAtLeast(1)
        val bandIdx = (u * (bands.size - 1)).toInt().coerceIn(0, bands.size - 1)
        val bandVal = bands[bandIdx]
        val peakVal = peakHold[bandIdx]

        val x = (u * width * 0.92f) + (width * 0.04f)
        val ridgeWave = sin((u * 8.5f) + (phaseTime * 0.9f)) * 22.0f * (0.4f + (frame.midEnergy * 0.6f))
        val baseY = baseHorizonY + ridgeWave - (bandVal * height * 0.08f)
        val stemHeight = (24.0f + (bandVal * height * 0.38f)) * (0.75f + (0.25f * sin((c * 0.7f) + phaseTime)))
        val topY = maxOf(height * 0.08f, baseY - stemHeight)

        val col = samplePalette(palette, u)

        // Vertical glowing stem gradient
        drawLine(
            brush = Brush.verticalGradient(
                colors = listOf(
                    col.copy(alpha = 0.92f),
                    col.copy(alpha = 0.48f),
                    col.copy(alpha = 0.04f)
                ),
                startY = topY,
                endY = baseY + 35f
            ),
            start = Offset(x, topY),
            end = Offset(x, baseY + 35f),
            strokeWidth = 2.2f,
            cap = StrokeCap.Round
        )

        // Glowing tip orb (outer soft aura + bright center)
        drawCircle(
            color = col.copy(alpha = 0.55f),
            radius = 6.2f,
            center = Offset(x, topY)
        )
        drawCircle(
            color = Color.White,
            radius = 3.0f,
            center = Offset(x, topY)
        )

        // Floating peak particle
        val peakY = maxOf(height * 0.05f, baseY - (peakVal * height * 0.43f) - 10f)
        drawCircle(
            color = col.copy(alpha = 0.86f),
            radius = 2.5f,
            center = Offset(x, peakY)
        )

        // Sub-dots along stem
        val subDots = 3
        for (d in 1..subDots) {
            val dotRatio = d.toFloat() / (subDots + 1)
            val dy = topY + ((baseY - topY) * dotRatio)
            val dotAlpha = (0.82f * (1.0f - (dotRatio * 0.65f))).coerceIn(0.15f, 0.85f)
            drawCircle(
                color = col.copy(alpha = dotAlpha),
                radius = 1.9f,
                center = Offset(x, dy)
            )
        }
    }

    // Pass B: 3D Perspective Layered Wireframe Ribbon Terrain (Front to Back)
    val layers = definition.waveLayers.coerceIn(3, 12)
    val subLinesPerLayer = 7

    for (layer in 0 until layers) {
        val layerNorm = layer.toFloat() / (layers - 1).coerceAtLeast(1) // 0 = back, 1 = front
        val layerBaseY = (height * 0.56f) + (layerNorm * height * 0.34f)
        val perspectiveSpread = 1.0f + (layerNorm * 0.28f)
        val ampScale = (28.0f + (frame.bassEnergy * 52.0f)) * (1.15f - (layerNorm * 0.35f))

        for (sub in 0 until subLinesPerLayer) {
            val subNorm = sub.toFloat() / subLinesPerLayer
            val yOffset = sub * 3.8f * perspectiveSpread

            val path = Path()
            val segs = 64
            for (s in 0..segs) {
                val u = s.toFloat() / segs
                val bIdx = (u * (bands.size - 1)).toInt().coerceIn(0, bands.size - 1)
                val fft = bands[bIdx]

                val px = ((u - 0.5f) * width * perspectiveSpread) + (width * 0.5f)
                val wave1 = sin((u * 9.0f) + (phaseTime * (1.1f + (layer * 0.14f))) + (layer * 0.9f))
                val wave2 = cos((u * 15.0f) - (phaseTime * 0.8f) + (sub * 0.25f)) * 0.45f
                val py = layerBaseY + yOffset - (((wave1 + wave2) * 0.5f * ampScale) + (fft * 42.0f * (1.0f - (subNorm * 0.4f))))

                if (s == 0) {
                    path.moveTo(px, py)
                } else {
                    path.lineTo(px, py)
                }
            }

            val alpha = ((1.0f - (subNorm * 0.72f)) * 0.85f).coerceIn(0.15f, 0.92f)
            val strokeW = if (sub == 0) 2.4f else 1.25f

            // Create gradient brush across the layer width
            val stops = palette.indices.map { p ->
                val pos = p.toFloat() / (palette.size - 1).coerceAtLeast(1)
                val c = palette[(p + layer) % palette.size]
                pos to c.copy(alpha = alpha)
            }.toTypedArray()

            drawPath(
                path = path,
                brush = Brush.horizontalGradient(
                    colorStops = stops,
                    startX = 0f,
                    endX = width
                ),
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )
        }
    }
}

// =========================================================================
// 2. HARMONIC WIREFRAME SPHERE 3D ("See Your Music")
// =========================================================================
private fun DrawScope.drawHarmonicWireframeSphere(
    width: Float,
    height: Float,
    definition: VisualizerDefinition,
    palette: List<Color>,
    frame: AudioVisualizerSampler.SpectrumFrame,
    phaseTime: Float
) {
    val bands = frame.bands

    // Pass A: Background Flowing Translucent Wave Ribbons
    val bgWaves = 5
    for (w in 0 until bgWaves) {
        val wy = height * (0.20f + (w * 0.16f))
        val wc = samplePalette(palette, w.toFloat() / (bgWaves - 1).coerceAtLeast(1))

        val bgPath = Path()
        for (xStep in 0..48) {
            val u = xStep / 48f
            val px = u * width
            val py = wy + (sin((u * 6.2f) + (phaseTime * 0.7f) + (w * 1.1f)) * (26f + (frame.bassEnergy * 34f)))
            if (xStep == 0) bgPath.moveTo(px, py) else bgPath.lineTo(px, py)
        }
        drawPath(
            path = bgPath,
            color = wc.copy(alpha = 0.26f),
            style = Stroke(width = 2.4f, cap = StrokeCap.Round)
        )
    }

    // Pass B: 3D Spherical Harmonic Wireframe Mesh
    val latRings = (definition.gridResolution / 2).coerceIn(16, 32)
    val lonSegments = definition.gridResolution.coerceIn(24, 48)

    val centerX = width * 0.5f
    val centerY = height * 0.48f
    val baseRadius = minOf(width, height) * 0.27f

    val rotY = phaseTime * 0.48f
    val rotX = -0.38f + (sin(phaseTime * 0.27f) * 0.22f)
    val rotZ = cos(phaseTime * 0.19f) * 0.18f

    // 3D Matrix Rotation constants
    val cosY = cos(rotY); val sinY = sin(rotY)
    val cosX = cos(rotX); val sinX = sin(rotX)
    val cosZ = cos(rotZ); val sinZ = sin(rotZ)

    val projX = Array(latRings + 1) { FloatArray(lonSegments + 1) }
    val projY = Array(latRings + 1) { FloatArray(lonSegments + 1) }
    val energyGrid = Array(latRings + 1) { FloatArray(lonSegments + 1) }
    val depthGrid = Array(latRings + 1) { FloatArray(lonSegments + 1) }

    for (i in 0..latRings) {
        val v = i.toFloat() / latRings
        val theta = v * PI.toFloat()
        val sinTheta = sin(theta)
        val cosTheta = cos(theta)

        val bandIdx = (v * (bands.size - 1)).toInt().coerceIn(0, bands.size - 1)
        val bandVal = bands[bandIdx]

        for (j in 0..lonSegments) {
            val u = j.toFloat() / lonSegments
            val phi = u * PI.toFloat() * 2.0f

            val harmonicRidge = sin((theta * 10.0f) - (phaseTime * 1.6f)) * 0.12f * (0.4f + frame.bassEnergy)
            val lonWave = cos((phi * 4.0f) + (phaseTime * 0.9f)) * sinTheta * 0.14f * (0.3f + frame.midEnergy)
            val fftDisplacement = bandVal * 0.26f * sin((phi * 2.0f) + theta)

            val totalDeform = 1.0f + harmonicRidge + lonWave + fftDisplacement
            val r = baseRadius * totalDeform

            val x0 = r * sinTheta * cos(phi)
            val y0 = r * cosTheta
            val z0 = r * sinTheta * sin(phi)

            // Apply 3D Rotation (Yaw-Pitch-Roll)
            // 1. Rotate Y
            val x1 = (x0 * cosY) + (z0 * sinY)
            val y1 = y0
            val z1 = (-x0 * sinY) + (z0 * cosY)

            // 2. Rotate X
            val x2 = x1
            val y2 = (y1 * cosX) - (z1 * sinX)
            val z2 = (y1 * sinX) + (z1 * cosX)

            // 3. Rotate Z
            val xRot = (x2 * cosZ) - (y2 * sinZ)
            val yRot = (x2 * sinZ) + (y2 * cosZ)
            val zRot = z2

            val cameraDist = baseRadius * 3.4f
            val perspective = cameraDist / maxOf(60.0f, cameraDist - zRot)

            projX[i][j] = centerX + (xRot * perspective)
            projY[i][j] = centerY + (yRot * perspective)
            energyGrid[i][j] = ((totalDeform - 0.82f) * 2.4f).coerceIn(0f, 1f)
            depthGrid[i][j] = zRot / baseRadius
        }
    }

    // Draw Wireframe Lines connecting quads
    for (i in 0 until latRings) {
        for (j in 0 until lonSegments) {
            val p00 = Offset(projX[i][j], projY[i][j])
            val p01 = Offset(projX[i][j + 1], projY[i][j + 1])
            val p10 = Offset(projX[i + 1][j], projY[i + 1][j])

            val e = (energyGrid[i][j] + energyGrid[i][j + 1]) * 0.5f
            val depth = depthGrid[i][j]
            val alpha = (0.45f + (depth * 0.28f) + (e * 0.25f)).coerceIn(0.18f, 0.95f)

            val wireColor = samplePalette(palette, e)
            val strokeCol = wireColor.copy(alpha = alpha)

            // Latitude ring line
            drawLine(
                color = strokeCol,
                start = p00,
                end = p01,
                strokeWidth = 1.35f,
                cap = StrokeCap.Round
            )
            // Longitude meridian line
            drawLine(
                color = strokeCol,
                start = p00,
                end = p10,
                strokeWidth = 1.25f,
                cap = StrokeCap.Round
            )
        }
    }
}

// =========================================================================
// 3. AURORA PRISMATIC WAVES
// =========================================================================
private fun DrawScope.drawAuroraPrismaticWaves(
    width: Float,
    height: Float,
    definition: VisualizerDefinition,
    palette: List<Color>,
    frame: AudioVisualizerSampler.SpectrumFrame,
    phaseTime: Float
) {
    val ribbons = definition.waveLayers.coerceIn(4, 10)
    val centerY = height * 0.52f
    val bands = frame.bands

    for (r in 0 until ribbons) {
        val rNorm = r.toFloat() / (ribbons - 1).coerceAtLeast(1)
        val c = samplePalette(palette, rNorm)

        val path = Path()
        val steps = 64
        for (s in 0..steps) {
            val u = s.toFloat() / steps
            val bIdx = (u * (bands.size - 1)).toInt().coerceIn(0, bands.size - 1)
            val fft = bands[bIdx]

            val x = u * width
            val y = centerY +
                    (sin((u * 5.5f) + (phaseTime * (0.8f + (r * 0.15f))) + r) * (55f + (frame.bassEnergy * 90f))) +
                    (cos((u * 11.0f) - (phaseTime * 1.1f) + (r * 0.7f)) * (28f + (fft * 85f)))

            if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        // Soft outer glow pass
        drawPath(
            path = path,
            color = c.copy(alpha = 0.28f),
            style = Stroke(width = 8.0f, cap = StrokeCap.Round)
        )
        // Sharp core ribbon pass
        drawPath(
            path = path,
            color = c.copy(alpha = 0.88f),
            style = Stroke(width = 3.2f, cap = StrokeCap.Round)
        )
    }

    // Floating Sparkle Particles
    val particles = definition.particleCount.coerceIn(16, 120)
    for (p in 0 until particles) {
        val u = (p * 0.6180339f) % 1.0f
        val px = ((u + (phaseTime * 0.03f * ((p % 3) + 1))) % 1.0f) * width
        val py = centerY + (sin((p * 1.7f) + (phaseTime * 0.9f)) * height * 0.34f)
        val radius = 1.6f + ((p % 4) * 0.9f * (0.5f + frame.trebleEnergy))
        val pc = samplePalette(palette, u)

        drawCircle(
            color = pc.copy(alpha = 0.75f),
            radius = radius,
            center = Offset(px, py)
        )
    }
}

// =========================================================================
// 4. RADIAL CYBER PULSAR 360°
// =========================================================================
private fun DrawScope.drawRadialCyberPulsar(
    width: Float,
    height: Float,
    definition: VisualizerDefinition,
    palette: List<Color>,
    frame: AudioVisualizerSampler.SpectrumFrame,
    phaseTime: Float
) {
    val center = Offset(width * 0.5f, height * 0.48f)
    val minDim = minOf(width, height)
    val baseR = (minDim * 0.18f) + (frame.bassEnergy * 32.0f)
    val bands = frame.bands

    // Bass shockwave expanding rings
    for (ring in 0 until 3) {
        val ringPhase = ((phaseTime * 0.45f) + (ring * 0.33f)) % 1.0f
        val shockR = baseR + (ringPhase * minDim * 0.28f)
        val shockAlpha = ((1.0f - ringPhase) * 0.55f * (0.4f + frame.bassEnergy)).coerceIn(0f, 0.75f)
        val rc = samplePalette(palette, ringPhase)

        drawCircle(
            color = rc.copy(alpha = shockAlpha),
            radius = shockR,
            center = center,
            style = Stroke(width = 2.2f)
        )
    }

    // 360-degree radial equalizer bars
    val bars = (definition.gridResolution * 2).coerceIn(48, 128)
    for (i in 0 until bars) {
        val u = i.toFloat() / bars
        val angle = (u * PI.toFloat() * 2.0f) + (phaseTime * 0.2f)
        val bIdx = (abs((u * 2.0f) - 1.0f) * (bands.size - 1)).toInt().coerceIn(0, bands.size - 1)
        val valBand = bands[bIdx]

        val barLen = 14.0f + (valBand * minDim * 0.22f)
        val cosA = cos(angle)
        val sinA = sin(angle)

        val pStart = Offset(center.x + (cosA * baseR), center.y + (sinA * baseR))
        val pEnd = Offset(center.x + (cosA * (baseR + barLen)), center.y + (sinA * (baseR + barLen)))

        val barCol = samplePalette(palette, u)

        drawLine(
            color = barCol.copy(alpha = 0.90f),
            start = pStart,
            end = pEnd,
            strokeWidth = 2.8f,
            cap = StrokeCap.Round
        )

        drawCircle(
            color = Color.White,
            radius = 2.4f,
            center = pEnd
        )
    }
}

// =========================================================================
// PALETTE RESOLUTION & SAMPLING
// =========================================================================
private fun resolvePalette(definition: VisualizerDefinition, accentColor: Color): List<Color> {
    if (definition.useDynamicCoverAccent) {
        // Derive harmonious accent suite from dynamic cover color
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(
            android.graphics.Color.argb(
                (accentColor.alpha * 255).toInt(),
                (accentColor.red * 255).toInt(),
                (accentColor.green * 255).toInt(),
                (accentColor.blue * 255).toInt()
            ),
            hsv
        )

        val primary = accentColor
        val secondaryHsv = floatArrayOf((hsv[0] + 16f) % 360f, (hsv[1] * 0.88f).coerceIn(0.42f, 0.82f), 0.95f)
        val secondary = Color(android.graphics.Color.HSVToColor(secondaryHsv))

        return listOf(
            primary,
            secondary,
            Color(0xFFFFBE0B),
            Color(0xFFF43F5E),
            primary
        )
    }

    if (definition.colorPaletteHex.isNotEmpty()) {
        val list = mutableListOf<Color>()
        for (hex in definition.colorPaletteHex) {
            val parsed = parseHexColor(hex)
            if (parsed != null) list.add(parsed)
        }
        if (list.isNotEmpty()) return list
    }

    return listOf(
        Color(0xFFFFBE0B),
        Color(0xFF38B000),
        Color(0xFF9D4EDD),
        Color(0xFFFF007F),
        Color(0xFF00F5D4),
        Color(0xFF00BBF9)
    )
}

private fun parseHexColor(hex: String): Color? {
    return try {
        val clean = hex.trim().removePrefix("#")
        when (clean.length) {
            6 -> {
                val colorInt = clean.toLong(16).toInt() or -0x1000000
                Color(colorInt)
            }
            8 -> {
                val colorInt = clean.toLong(16).toInt()
                Color(colorInt)
            }
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}

private fun samplePalette(palette: List<Color>, t: Float): Color {
    if (palette.isEmpty()) return Color.Cyan
    if (palette.size == 1) return palette[0]

    val clamped = t.coerceIn(0f, 0.9999f)
    val scaled = clamped * (palette.size - 1)
    val idx0 = scaled.toInt()
    val idx1 = minOf(idx0 + 1, palette.size - 1)
    val frac = scaled - idx0

    val c0 = palette[idx0]
    val c1 = palette[idx1]

    return Color(
        red = c0.red + ((c1.red - c0.red) * frac),
        green = c0.green + ((c1.green - c0.green) * frac),
        blue = c0.blue + ((c1.blue - c0.blue) * frac),
        alpha = c0.alpha + ((c1.alpha - c0.alpha) * frac)
    )
}
