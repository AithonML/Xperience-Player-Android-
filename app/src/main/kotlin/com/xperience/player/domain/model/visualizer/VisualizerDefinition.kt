package com.xperience.player.domain.model.visualizer

/**
 * Declarative specification for a built-in or user-imported GPU Music Visualizer preset (.xviz.json or .json).
 * Matches the exact schema of the Windows version.
 */
data class VisualizerDefinition(
    val id: String = "",
    val name: String = "",
    val author: String = "Xperience Studio",
    val description: String = "",
    val renderMode: VisualizerRenderMode = VisualizerRenderMode.NeonTerrainPeaks,
    val isCustomImported: Boolean = false,
    val useDynamicCoverAccent: Boolean = false,
    val colorPaletteHex: List<String> = listOf(
        "#FFFFBE0B",
        "#FF38B000",
        "#FF9D4EDD",
        "#FFFF007F",
        "#FF00F5D4",
        "#FF00BBF9"
    ),
    val sensitivity: Double = 1.0,
    val speed: Double = 1.0,
    val gridResolution: Int = 52,
    val waveLayers: Int = 6,
    val particleCount: Int = 64,
    val bassDeformation: Double = 1.35,
    val midDeformation: Double = 1.10,
    val trebleDeformation: Double = 1.25,
    val bloomRadius: Double = 12.0,
    val backgroundDimOpacity: Double = 0.0
)
