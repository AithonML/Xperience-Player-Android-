package com.xperience.player.domain.model.visualizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualizerJsonParserTest {

    @Test
    fun parse_outrunSunsetPeaks_matchesSpec() {
        val json = """
        {
          "Id": "OutrunSunsetPeaks",
          "Name": "Outrun Sunset 1984",
          "Author": "Custom Creator",
          "Description": "Terreno 3D de alta energía con paleta Synthwave cálida y cian.",
          "RenderMode": "NeonTerrainPeaks",
          "UseDynamicCoverAccent": false,
          "ColorPaletteHex": [
            "#FFFFBE0B",
            "#FFFF007F",
            "#FF9D4EDD",
            "#FF00F5D4",
            "#FF00BBF9"
          ],
          "Sensitivity": 1.65,
          "Speed": 1.35,
          "GridResolution": 68,
          "WaveLayers": 9,
          "ParticleCount": 88,
          "BassDeformation": 1.65,
          "MidDeformation": 1.25,
          "TrebleDeformation": 1.40,
          "BloomRadius": 18.0,
          "BackgroundDimOpacity": 0.76
        }
        """.trimIndent()

        val def = VisualizerJsonParser.parse(json)
        assertEquals("OutrunSunsetPeaks", def.id)
        assertEquals("Outrun Sunset 1984", def.name)
        assertEquals("Custom Creator", def.author)
        assertEquals(VisualizerRenderMode.NeonTerrainPeaks, def.renderMode)
        assertFalse(def.useDynamicCoverAccent)
        assertEquals(5, def.colorPaletteHex.size)
        assertEquals(1.65, def.sensitivity, 0.001)
        assertEquals(1.35, def.speed, 0.001)
        assertEquals(68, def.gridResolution)
        assertEquals(9, def.waveLayers)
        assertEquals(88, def.particleCount)
        assertEquals(1.65, def.bassDeformation, 0.001)
        assertEquals(1.25, def.midDeformation, 0.001)
        assertEquals(1.40, def.trebleDeformation, 0.001)
        assertEquals(18.0, def.bloomRadius, 0.001)
        assertEquals(0.76, def.backgroundDimOpacity, 0.001)
    }

    @Test
    fun parse_quantumHarmonicOrb_matchesSpec() {
        val json = """
        {
          "Id": "QuantumHarmonicOrb",
          "Name": "Quantum Cover-Synced Orb",
          "Author": "Custom Creator",
          "Description": "Esfera armónica 3D sincronizada con el color dominante de la portada del álbum.",
          "RenderMode": "HarmonicWireframeSphere",
          "UseDynamicCoverAccent": true,
          "ColorPaletteHex": [
            "#FF2DD4BF",
            "#FF38BDF8",
            "#FFA855F7",
            "#FFF43F5E"
          ],
          "Sensitivity": 1.70,
          "Speed": 1.40,
          "GridResolution": 64,
          "WaveLayers": 10,
          "ParticleCount": 96,
          "BassDeformation": 1.70,
          "MidDeformation": 1.35,
          "TrebleDeformation": 1.45,
          "BloomRadius": 16.0,
          "BackgroundDimOpacity": 0.80
        }
        """.trimIndent()

        val def = VisualizerJsonParser.parse(json)
        assertEquals("QuantumHarmonicOrb", def.id)
        assertEquals(VisualizerRenderMode.HarmonicWireframeSphere, def.renderMode)
        assertTrue(def.useDynamicCoverAccent)
        assertEquals(4, def.colorPaletteHex.size)
        assertEquals(1.70, def.sensitivity, 0.001)
    }

    @Test
    fun parse_clampsOutOfRangeValues() {
        val json = """
        {
          "Id": "ExtremeTest",
          "Name": "Extreme Clamping Test",
          "Sensitivity": 999.0,
          "Speed": -50.0,
          "GridResolution": 200,
          "WaveLayers": 1,
          "ParticleCount": 500,
          "BassDeformation": 10.0,
          "MidDeformation": 0.05,
          "TrebleDeformation": 100.0,
          "BloomRadius": 99.0,
          "BackgroundDimOpacity": 1.5
        }
        """.trimIndent()

        val def = VisualizerJsonParser.parse(json)
        assertEquals(3.0, def.sensitivity, 0.001) // Max 3.0
        assertEquals(0.1, def.speed, 0.001)       // Min 0.1
        assertEquals(96, def.gridResolution)      // Max 96
        assertEquals(2, def.waveLayers)           // Min 2
        assertEquals(160, def.particleCount)      // Max 160
        assertEquals(3.0, def.bassDeformation, 0.001) // Max 3.0
        assertEquals(0.2, def.midDeformation, 0.001)  // Min 0.2
        assertEquals(3.0, def.trebleDeformation, 0.001) // Max 3.0
        assertEquals(28.0, def.bloomRadius, 0.001)     // Max 28.0
        assertEquals(0.95, def.backgroundDimOpacity, 0.001) // Max 0.95
    }

    @Test
    fun roundTrip_serialization_isPreserved() {
        val original = VisualizerDefinition(
            id = "RoundTripTest",
            name = "Round Trip Test Visualizer",
            author = "Unit Tester",
            description = "Tests JSON round-trip stability",
            renderMode = VisualizerRenderMode.AuroraPrismaticWaves,
            useDynamicCoverAccent = true,
            colorPaletteHex = listOf("#FF00F5D4", "#FF00BBF9", "#FF9D4EDD"),
            sensitivity = 1.42,
            speed = 1.15,
            gridResolution = 60,
            waveLayers = 8,
            particleCount = 70,
            bassDeformation = 1.45,
            midDeformation = 1.15,
            trebleDeformation = 1.30,
            bloomRadius = 14.0,
            backgroundDimOpacity = 0.75
        )

        val serialized = VisualizerJsonParser.toJson(original)
        val deserialized = VisualizerJsonParser.parse(serialized)

        assertEquals(original.id, deserialized.id)
        assertEquals(original.name, deserialized.name)
        assertEquals(original.author, deserialized.author)
        assertEquals(original.description, deserialized.description)
        assertEquals(original.renderMode, deserialized.renderMode)
        assertEquals(original.useDynamicCoverAccent, deserialized.useDynamicCoverAccent)
        assertEquals(original.colorPaletteHex, deserialized.colorPaletteHex)
        assertEquals(original.sensitivity, deserialized.sensitivity, 0.001)
        assertEquals(original.speed, deserialized.speed, 0.001)
        assertEquals(original.gridResolution, deserialized.gridResolution)
        assertEquals(original.waveLayers, deserialized.waveLayers)
        assertEquals(original.particleCount, deserialized.particleCount)
        assertEquals(original.bassDeformation, deserialized.bassDeformation, 0.001)
        assertEquals(original.midDeformation, deserialized.midDeformation, 0.001)
        assertEquals(original.trebleDeformation, deserialized.trebleDeformation, 0.001)
        assertEquals(original.bloomRadius, deserialized.bloomRadius, 0.001)
        assertEquals(original.backgroundDimOpacity, deserialized.backgroundDimOpacity, 0.001)
    }
}
