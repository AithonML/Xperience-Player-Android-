package com.xperience.player.domain.model.visualizer

import org.json.JSONArray
import org.json.JSONObject

/**
 * Handles JSON parsing and serialization for .xviz.json and .json visualizer definitions.
 * Ensures 100% cross-platform compatibility between Windows and Android ports.
 */
object VisualizerJsonParser {

    /**
     * Parses a VisualizerDefinition from a JSON string.
     * Accepts both PascalCase (Windows contract) and camelCase property names.
     */
    fun parse(jsonString: String, isCustomImported: Boolean = true): VisualizerDefinition {
        val json = JSONObject(jsonString)

        val id = json.optStringIgnoreCase("Id", "")
        val name = json.optStringIgnoreCase("Name", "Custom Visualizer")
        val author = json.optStringIgnoreCase("Author", "Xperience Studio")
        val description = json.optStringIgnoreCase("Description", "")
        val renderModeStr = json.optStringIgnoreCase("RenderMode", "NeonTerrainPeaks")
        val renderMode = VisualizerRenderMode.fromString(renderModeStr)

        val useDynamicCoverAccent = json.optBooleanIgnoreCase("UseDynamicCoverAccent", false)

        val paletteList = mutableListOf<String>()
        val paletteArray = json.optJSONArrayIgnoreCase("ColorPaletteHex")
        if (paletteArray != null) {
            for (i in 0 until paletteArray.length()) {
                val hex = paletteArray.optString(i)
                if (!hex.isNullOrBlank()) {
                    paletteList.add(hex.trim())
                }
            }
        }
        val finalPalette = if (paletteList.isNotEmpty()) {
            paletteList
        } else {
            listOf(
                "#FFFFBE0B",
                "#FF38B000",
                "#FF9D4EDD",
                "#FFFF007F",
                "#FF00F5D4",
                "#FF00BBF9"
            )
        }

        val sensitivity = json.optDoubleIgnoreCase("Sensitivity", 1.0).coerceIn(0.2, 3.0)
        val speed = json.optDoubleIgnoreCase("Speed", 1.0).coerceIn(0.1, 4.0)
        val gridResolution = json.optIntIgnoreCase("GridResolution", 52).coerceIn(16, 96)
        val waveLayers = json.optIntIgnoreCase("WaveLayers", 6).coerceIn(2, 14)
        val particleCount = json.optIntIgnoreCase("ParticleCount", 64).coerceIn(0, 160)
        val bassDeformation = json.optDoubleIgnoreCase("BassDeformation", 1.35).coerceIn(0.2, 3.0)
        val midDeformation = json.optDoubleIgnoreCase("MidDeformation", 1.10).coerceIn(0.2, 3.0)
        val trebleDeformation = json.optDoubleIgnoreCase("TrebleDeformation", 1.25).coerceIn(0.2, 3.0)
        val bloomRadius = json.optDoubleIgnoreCase("BloomRadius", 12.0).coerceIn(0.0, 28.0)
        val backgroundDimOpacity = json.optDoubleIgnoreCase("BackgroundDimOpacity", 0.72).coerceIn(0.0, 0.95)

        val finalId = if (id.isNotBlank()) id else "custom_${System.currentTimeMillis()}"

        return VisualizerDefinition(
            id = finalId,
            name = name,
            author = author,
            description = description,
            renderMode = renderMode,
            isCustomImported = isCustomImported,
            useDynamicCoverAccent = useDynamicCoverAccent,
            colorPaletteHex = finalPalette,
            sensitivity = sensitivity,
            speed = speed,
            gridResolution = gridResolution,
            waveLayers = waveLayers,
            particleCount = particleCount,
            bassDeformation = bassDeformation,
            midDeformation = midDeformation,
            trebleDeformation = trebleDeformation,
            bloomRadius = bloomRadius,
            backgroundDimOpacity = backgroundDimOpacity
        )
    }

    /**
     * Serializes a VisualizerDefinition into formatted JSON matching the Windows contract.
     */
    fun toJson(definition: VisualizerDefinition): String {
        val json = JSONObject()
        json.put("Id", definition.id)
        json.put("Name", definition.name)
        json.put("Author", definition.author)
        json.put("Description", definition.description)
        json.put("RenderMode", definition.renderMode.name)
        json.put("UseDynamicCoverAccent", definition.useDynamicCoverAccent)

        val paletteArray = JSONArray()
        for (hex in definition.colorPaletteHex) {
            paletteArray.put(hex)
        }
        json.put("ColorPaletteHex", paletteArray)

        json.put("Sensitivity", definition.sensitivity)
        json.put("Speed", definition.speed)
        json.put("GridResolution", definition.gridResolution)
        json.put("WaveLayers", definition.waveLayers)
        json.put("ParticleCount", definition.particleCount)
        json.put("BassDeformation", definition.bassDeformation)
        json.put("MidDeformation", definition.midDeformation)
        json.put("TrebleDeformation", definition.trebleDeformation)
        json.put("BloomRadius", definition.bloomRadius)
        json.put("BackgroundDimOpacity", definition.backgroundDimOpacity)

        return json.toString(2)
    }

    private fun JSONObject.optStringIgnoreCase(key: String, fallback: String): String {
        if (has(key)) return optString(key, fallback)
        val lowerKey = key.lowercase()
        val keys = keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (k.equals(key, ignoreCase = true)) {
                return optString(k, fallback)
            }
        }
        return fallback
    }

    private fun JSONObject.optBooleanIgnoreCase(key: String, fallback: Boolean): Boolean {
        if (has(key)) return optBoolean(key, fallback)
        val keys = keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (k.equals(key, ignoreCase = true)) {
                return optBoolean(k, fallback)
            }
        }
        return fallback
    }

    private fun JSONObject.optDoubleIgnoreCase(key: String, fallback: Double): Double {
        if (has(key)) return optDouble(key, fallback)
        val keys = keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (k.equals(key, ignoreCase = true)) {
                return optDouble(k, fallback)
            }
        }
        return fallback
    }

    private fun JSONObject.optIntIgnoreCase(key: String, fallback: Int): Int {
        if (has(key)) return optInt(key, fallback)
        val keys = keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (k.equals(key, ignoreCase = true)) {
                return optInt(k, fallback)
            }
        }
        return fallback
    }

    private fun JSONObject.optJSONArrayIgnoreCase(key: String): JSONArray? {
        if (has(key)) return optJSONArray(key)
        val keys = keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (k.equals(key, ignoreCase = true)) {
                return optJSONArray(k)
            }
        }
        return null
    }
}
