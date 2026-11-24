package com.xperience.player.service

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FlacMetadataTaggerTest {

    @Test
    fun testTagLocalFiles() {
        val espressoFile = File("temp_espresso.flac")
        val espressoCover = File("temp_espresso.jpg")

        if (espressoFile.exists()) {
            val coverBytes = if (espressoCover.exists()) espressoCover.readBytes() else null
            val ok = FlacMetadataTagger.tagFlacFile(
                espressoFile,
                FlacMetadataTagger.FlacTags(
                    title = "Espresso",
                    artist = "Sabrina Carpenter",
                    album = "Short n' Sweet",
                    genre = "Pop",
                    year = "2024",
                    coverArtBytes = coverBytes
                )
            )
            assertTrue("Espresso tagging should succeed", ok)
        }

        val sodaPopFile = File("temp_sodapop.flac")
        val sodaPopCover = File("temp_sodapop.jpg")

        if (sodaPopFile.exists()) {
            val coverBytes = if (sodaPopCover.exists()) sodaPopCover.readBytes() else null
            val ok = FlacMetadataTagger.tagFlacFile(
                sodaPopFile,
                FlacMetadataTagger.FlacTags(
                    title = "Soda Pop",
                    artist = "Saja Boys",
                    album = "KPop Demon Hunters",
                    genre = "K-Pop",
                    year = "2024",
                    coverArtBytes = coverBytes
                )
            )
            assertTrue("Soda Pop tagging should succeed", ok)
        }
    }
}
