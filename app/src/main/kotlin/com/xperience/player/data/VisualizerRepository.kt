package com.xperience.player.data

import android.content.Context
import android.util.Log
import com.xperience.player.domain.model.visualizer.VisualizerDefinition
import com.xperience.player.domain.model.visualizer.VisualizerJsonParser
import com.xperience.player.domain.model.visualizer.VisualizerRenderMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Manages built-in and user-imported GPU music visualizer presets.
 * Supports persistent storage of custom .xviz.json and .json files.
 */
class VisualizerRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("xperience_visualizers_prefs", Context.MODE_PRIVATE)
    private val visualizersDir = File(context.filesDir, "visualizers").apply {
        if (!exists()) mkdirs()
    }

    private val _availableVisualizers = MutableStateFlow<List<VisualizerDefinition>>(emptyList())
    val availableVisualizers: StateFlow<List<VisualizerDefinition>> = _availableVisualizers.asStateFlow()

    private val _selectedVisualizer = MutableStateFlow<VisualizerDefinition>(BuiltInPresets.first())
    val selectedVisualizer: StateFlow<VisualizerDefinition> = _selectedVisualizer.asStateFlow()

    init {
        loadVisualizers()
    }

    fun loadVisualizers() {
        val list = mutableListOf<VisualizerDefinition>()

        // 1. Add 5 official built-in presets
        list.addAll(BuiltInPresets)

        // 2. Load bundled asset sample visualizers if any
        try {
            val assetFiles = context.assets.list("visualizers")
            if (assetFiles != null) {
                for (assetFile in assetFiles) {
                    if (assetFile.endsWith(".json", ignoreCase = true)) {
                        try {
                            val content = context.assets.open("visualizers/$assetFile").bufferedReader().use { it.readText() }
                            val def = VisualizerJsonParser.parse(content, isCustomImported = true)
                            if (list.none { it.id.equals(def.id, ignoreCase = true) }) {
                                list.add(def)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to load asset visualizer: $assetFile", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not list assets/visualizers", e)
        }

        // 3. Load user-imported custom files from app internal directory
        try {
            val userFiles = visualizersDir.listFiles { _, name ->
                name.endsWith(".json", ignoreCase = true)
            }
            if (userFiles != null) {
                for (file in userFiles) {
                    try {
                        val content = file.readText()
                        val def = VisualizerJsonParser.parse(content, isCustomImported = true)
                        val existingIndex = list.indexOfFirst { it.id.equals(def.id, ignoreCase = true) }
                        if (existingIndex >= 0) {
                            list[existingIndex] = def
                        } else {
                            list.add(def)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to load custom visualizer file: ${file.name}", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enumerating custom visualizers directory", e)
        }

        _availableVisualizers.value = list

        // Restore previously selected visualizer ID
        val savedId = prefs.getString(PREF_SELECTED_VIZ_ID, BuiltInPresets.first().id)
        val selected = list.firstOrNull { it.id.equals(savedId, ignoreCase = true) }
            ?: list.firstOrNull()
            ?: BuiltInPresets.first()

        _selectedVisualizer.value = selected
    }

    fun selectVisualizer(visualizer: VisualizerDefinition) {
        _selectedVisualizer.value = visualizer
        prefs.edit().putString(PREF_SELECTED_VIZ_ID, visualizer.id).apply()
    }

    fun selectVisualizerById(id: String) {
        val found = _availableVisualizers.value.firstOrNull { it.id.equals(id, ignoreCase = true) }
        if (found != null) {
            selectVisualizer(found)
        }
    }

    /**
     * Imports a visualizer from a JSON string, persists it to storage, and activates it.
     */
    fun importVisualizer(jsonContent: String, originalFileName: String? = null): Result<VisualizerDefinition> {
        return try {
            val parsed = VisualizerJsonParser.parse(jsonContent, isCustomImported = true)
            if (parsed.name.isBlank()) {
                return Result.failure(IllegalArgumentException("Visualizer Name cannot be blank"))
            }

            val safeId = if (parsed.id.isNotBlank()) {
                parsed.id
            } else {
                val prefix = originalFileName?.substringBeforeLast(".") ?: "custom"
                "${prefix.replace(Regex("[^a-zA-Z0-9_-]"), "_")}_${System.currentTimeMillis()}"
            }

            val normalized = parsed.copy(
                id = safeId,
                isCustomImported = true
            )

            // Persist to internal storage
            val safeFileName = "${safeId.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.xviz.json"
            val targetFile = File(visualizersDir, safeFileName)
            targetFile.writeText(VisualizerJsonParser.toJson(normalized))

            // Update in-memory list
            val currentList = _availableVisualizers.value.toMutableList()
            val existingIndex = currentList.indexOfFirst { it.id.equals(normalized.id, ignoreCase = true) }
            if (existingIndex >= 0) {
                currentList[existingIndex] = normalized
            } else {
                currentList.add(normalized)
            }

            _availableVisualizers.value = currentList
            selectVisualizer(normalized)

            Result.success(normalized)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import visualizer", e)
            Result.failure(e)
        }
    }

    /**
     * Deletes a user-imported visualizer preset. Built-ins cannot be deleted.
     */
    fun deleteCustomVisualizer(id: String): Boolean {
        val target = _availableVisualizers.value.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: return false
        if (!target.isCustomImported) return false

        try {
            val safeFileName = "${target.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.xviz.json"
            val file = File(visualizersDir, safeFileName)
            if (file.exists()) file.delete()

            // Also check for .json extension
            val altFile = File(visualizersDir, "${target.id}.json")
            if (altFile.exists()) altFile.delete()

            val updatedList = _availableVisualizers.value.filterNot { it.id.equals(id, ignoreCase = true) }
            _availableVisualizers.value = updatedList

            if (_selectedVisualizer.value.id.equals(id, ignoreCase = true)) {
                selectVisualizer(BuiltInPresets.first())
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete visualizer: $id", e)
            return false
        }
    }

    companion object {
        private const val TAG = "VisualizerRepository"
        private const val PREF_SELECTED_VIZ_ID = "selected_visualizer_id"

        val BuiltInPresets: List<VisualizerDefinition> = listOf(
            VisualizerDefinition(
                id = "NeonTerrainPeaks",
                name = "Neon Terrain Peaks 3D",
                author = "Xperience Studio",
                description = "3D perspective wave terrain with vertical spectrum needle peaks and glowing particles.",
                renderMode = VisualizerRenderMode.NeonTerrainPeaks,
                useDynamicCoverAccent = false,
                colorPaletteHex = listOf("#FFFFBE0B", "#FF38B000", "#FF9D4EDD", "#FFFF007F", "#FF00F5D4", "#FF00BBF9"),
                sensitivity = 1.35,
                speed = 1.0,
                gridResolution = 58,
                waveLayers = 7,
                particleCount = 68,
                bassDeformation = 1.45,
                midDeformation = 1.15,
                trebleDeformation = 1.30,
                bloomRadius = 14.0,
                backgroundDimOpacity = 0.0
            ),
            VisualizerDefinition(
                id = "HarmonicWireframeSphere",
                name = "See Your Music (3D Orb)",
                author = "Xperience Studio",
                description = "3D rotating spherical harmonic wireframe mesh deformed by bass, vocal, and treble energy.",
                renderMode = VisualizerRenderMode.HarmonicWireframeSphere,
                useDynamicCoverAccent = false,
                colorPaletteHex = listOf("#FFFF007F", "#FF9D4EDD", "#FF00BBF9", "#FF00F5D4", "#FF38B000", "#FFFFBE0B"),
                sensitivity = 1.45,
                speed = 1.15,
                gridResolution = 56,
                waveLayers = 8,
                particleCount = 72,
                bassDeformation = 1.55,
                midDeformation = 1.25,
                trebleDeformation = 1.35,
                bloomRadius = 15.0,
                backgroundDimOpacity = 0.0
            ),
            VisualizerDefinition(
                id = "AuroraPrismaticWaves",
                name = "Aurora Prismatic Ribbon",
                author = "Xperience Studio",
                description = "Flowing multi-harmonic Aurora ribbon waves synced to the active album cover accent.",
                renderMode = VisualizerRenderMode.AuroraPrismaticWaves,
                useDynamicCoverAccent = true,
                colorPaletteHex = listOf("#FF2DD4BF", "#FF38BDF8", "#FF6366F1", "#FFA855F7", "#FFF43F5E"),
                sensitivity = 1.30,
                speed = 0.95,
                gridResolution = 52,
                waveLayers = 6,
                particleCount = 54,
                bassDeformation = 1.35,
                midDeformation = 1.20,
                trebleDeformation = 1.15,
                bloomRadius = 16.0,
                backgroundDimOpacity = 0.0
            ),
            VisualizerDefinition(
                id = "RadialCyberPulsar",
                name = "Radial Cyber Pulsar",
                author = "Xperience Studio",
                description = "360-degree radial cybernetic FFT equalizer with bass shockwaves and orbital particles.",
                renderMode = VisualizerRenderMode.RadialCyberPulsar,
                useDynamicCoverAccent = true,
                colorPaletteHex = listOf("#FF00F5D4", "#FF00BBF9", "#FF9D4EDD", "#FFFF007F", "#FFFFBE0B"),
                sensitivity = 1.50,
                speed = 1.20,
                gridResolution = 64,
                waveLayers = 6,
                particleCount = 64,
                bassDeformation = 1.50,
                midDeformation = 1.20,
                trebleDeformation = 1.35,
                bloomRadius = 14.0,
                backgroundDimOpacity = 0.0
            ),
            VisualizerDefinition(
                id = "BackdropOnly",
                name = "Cover Backdrop Only (Off)",
                author = "Xperience Studio",
                description = "Displays only the atmospheric album cover backdrop without 3D spectrum geometry.",
                renderMode = VisualizerRenderMode.None,
                useDynamicCoverAccent = true,
                sensitivity = 1.0,
                speed = 1.0,
                gridResolution = 32,
                waveLayers = 4,
                particleCount = 0,
                backgroundDimOpacity = 0.0
            )
        )
    }
}
