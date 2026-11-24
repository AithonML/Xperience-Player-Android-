package com.xperience.player.domain.model.visualizer

/**
 * Defines the GPU rendering geometry topology used by the Xperience Music Visualizer engine.
 * Fully compatible with the Windows port.
 */
enum class VisualizerRenderMode {
    /**
     * No active spectrum mesh; displays only the atmospheric album cover backdrop.
     */
    None,

    /**
     * 3D perspective multi-layer wave terrain with vertical spectrum needle peaks and glowing particles.
     */
    NeonTerrainPeaks,

    /**
     * 3D rotating spherical harmonic wireframe mesh ("See Your Music") deformed by bass/mid/treble energy.
     */
    HarmonicWireframeSphere,

    /**
     * Flowing multi-harmonic Aurora ribbon waves with Gaussian bloom.
     */
    AuroraPrismaticWaves,

    /**
     * 360-degree radial cybernetic FFT equalizer with bass shockwaves and orbital particles.
     */
    RadialCyberPulsar;

    companion object {
        fun fromString(value: String?): VisualizerRenderMode {
            if (value.isNullOrBlank()) return NeonTerrainPeaks
            val trimmed = value.trim()
            return entries.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
                ?: when (trimmed) {
                    "0" -> None
                    "1" -> NeonTerrainPeaks
                    "2" -> HarmonicWireframeSphere
                    "3" -> AuroraPrismaticWaves
                    "4" -> RadialCyberPulsar
                    else -> NeonTerrainPeaks
                }
        }
    }
}
