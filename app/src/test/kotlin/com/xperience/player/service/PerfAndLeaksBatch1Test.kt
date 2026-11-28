package com.xperience.player.service

import com.xperience.player.data.VisualizerRepository
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.domain.model.visualizer.VisualizerRenderMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PerfAndLeaksBatch1Test {

    private fun track(id: String) = TrackItem(
        id = id,
        title = "Title $id",
        artist = "Artist $id",
        album = "Album $id",
        durationMs = 180_000L
    )

    @Test
    fun `visualizer default preset is BackdropOnly with renderMode None`() {
        val defaultPreset = VisualizerRepository.DefaultPreset
        assertEquals("BackdropOnly", defaultPreset.id)
        assertEquals(VisualizerRenderMode.None, defaultPreset.renderMode)
        assertTrue(VisualizerRepository.BuiltInPresets.contains(defaultPreset))
    }

    @Test
    fun `playback queue controller trims played head when exceeding cap during progressive autoplay`() {
        val controller = PlaybackQueueController()
        val initial = (0 until 245).map { track("t_$it") }
        // Start near the end so currentIndex > PLAYED_HISTORY_KEEP_COUNT (50)
        controller.setQueue(initial, startIndex = 240)
        val activeTrackBefore = controller.snapshot().currentTrack
        assertEquals("t_240", activeTrackBefore?.id)

        // Append 20 more tracks -> total 265 > MAX_QUEUE_SIZE (250)
        val extra = (245 until 265).map { track("t_$it") }
        val updated = controller.appendSimilarTracks(controller.queueEpoch, extra)

        assertTrue(updated != null)
        // Trims 240 - 50 = 190 oldest played tracks, leaving 75 tracks with currentIndex = 50
        assertEquals(PlaybackQueueController.PLAYED_HISTORY_KEEP_COUNT, updated!!.currentIndex)
        assertEquals("t_240", updated.currentTrack?.id)
        assertEquals(75, updated.queue.size)
        assertEquals("t_190", updated.queue.first().id)
        assertEquals("t_264", updated.queue.last().id)
    }

    @Test
    fun `bounded lru map evicts least recently used entries when exceeding capacity`() {
        val cache = BoundedLruMap<String, Int>(maxEntries = 3)
        cache["a"] = 1
        cache["b"] = 2
        cache["c"] = 3

        // Access "a" so "b" becomes the least recently used entry
        assertEquals(1, cache["a"])

        cache["d"] = 4
        assertEquals(3, cache.size)
        assertNull(cache["b"])
        assertEquals(1, cache["a"])
        assertEquals(3, cache["c"])
        assertEquals(4, cache["d"])

        // putIfAbsent does not overwrite existing key
        assertEquals(1, cache.putIfAbsent("a", 99))
        assertEquals(1, cache["a"])
    }

    @Test
    fun `download failure never triggers silent playback and respects auto-skip failure cap`() {
        // Auto-skip while under cap and remaining tracks exist
        assertEquals(
            AudioPlayerService.DownloadFailureAction.AUTO_SKIP_NEXT,
            AudioPlayerService.resolveDownloadFailureAction(
                autoPlay = true,
                hasRemainingQueueTracks = true,
                consecutiveFailures = 0
            )
        )
        assertEquals(
            AudioPlayerService.DownloadFailureAction.AUTO_SKIP_NEXT,
            AudioPlayerService.resolveDownloadFailureAction(
                autoPlay = true,
                hasRemainingQueueTracks = true,
                consecutiveFailures = AudioPlayerService.MAX_CONSECUTIVE_AUTO_SKIP_FAILURES - 1
            )
        )

        // Stop cleanly once consecutive failures reach cap
        assertEquals(
            AudioPlayerService.DownloadFailureAction.STOP_CLEANLY,
            AudioPlayerService.resolveDownloadFailureAction(
                autoPlay = true,
                hasRemainingQueueTracks = true,
                consecutiveFailures = AudioPlayerService.MAX_CONSECUTIVE_AUTO_SKIP_FAILURES
            )
        )

        // Stop cleanly when no remaining tracks or autoPlay is false
        assertEquals(
            AudioPlayerService.DownloadFailureAction.STOP_CLEANLY,
            AudioPlayerService.resolveDownloadFailureAction(
                autoPlay = true,
                hasRemainingQueueTracks = false,
                consecutiveFailures = 0
            )
        )
        assertEquals(
            AudioPlayerService.DownloadFailureAction.STOP_CLEANLY,
            AudioPlayerService.resolveDownloadFailureAction(
                autoPlay = false,
                hasRemainingQueueTracks = true,
                consecutiveFailures = 0
            )
        )
    }

    @Test
    fun `audio cache disk trim evicts oldest files while preserving excluded active file`() {
        val tempDir = Files.createTempDirectory("ytm_audio_cache_test").toFile()
        try {
            val f1 = File(tempDir, "old1.m4a").apply {
                writeBytes(ByteArray(100))
                setLastModified(1_000L)
            }
            val f2 = File(tempDir, "old2.m4a").apply {
                writeBytes(ByteArray(100))
                setLastModified(2_000L)
            }
            val f3 = File(tempDir, "newest.m4a").apply {
                writeBytes(ByteArray(100))
                setLastModified(3_000L)
            }

            val ytm = YouTubeMusicService(audioCacheDirOverride = tempDir)
            ytm.trimAudioCacheIfNeeded(
                cacheDir = tempDir,
                maxBytes = 150L,
                excludeFileName = f3.name
            )

            assertFalse(f1.exists())
            assertFalse(f2.exists())
            assertTrue(f3.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
