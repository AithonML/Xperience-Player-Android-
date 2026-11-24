package com.xperience.player.service

import com.xperience.player.domain.model.PlaybackRepeatMode
import com.xperience.player.domain.model.PlaybackState
import com.xperience.player.domain.model.TrackItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueReorderTest {

    private fun track(id: String, title: String = "Track $id") = TrackItem(
        id = id,
        title = title,
        artist = "Artist $id",
        album = "Album $id",
        durationMs = 180_000L
    )

    @Test
    fun playbackStateDefaultsCurrentQueueIndexToMinusOne() {
        val state = PlaybackState()
        assertEquals(-1, state.currentQueueIndex)
        assertTrue(state.queue.isEmpty())
        assertNull(state.currentTrack)
    }

    @Test
    fun setQueueAndPlayTrackUpdateCurrentIndexAccurately() {
        val controller = PlaybackQueueController()
        val t0 = track("t0")
        val t1 = track("t1")
        val t2 = track("t2")

        val emptySnap = controller.setQueue(emptyList(), startIndex = 2)
        assertEquals(-1, emptySnap.currentIndex)
        assertTrue(emptySnap.queue.isEmpty())
        assertNull(emptySnap.currentTrack)

        val snap1 = controller.setQueue(listOf(t0, t1, t2), startIndex = 1)
        assertEquals(1, snap1.currentIndex)
        assertEquals(t1, snap1.currentTrack)
        assertEquals(listOf("t0", "t1", "t2"), snap1.queue.map { it.id })

        // Playing an out-of-queue track appends it and sets currentIndex to tail
        val t3 = track("t3")
        val snap2 = controller.playTrack(t3)
        assertEquals(3, snap2.currentIndex)
        assertEquals(t3, snap2.currentTrack)
        assertEquals(listOf("t0", "t1", "t2", "t3"), snap2.queue.map { it.id })

        // Playing an in-queue track switches currentIndex to its slot
        val snap3 = controller.playTrack(t0)
        assertEquals(0, snap3.currentIndex)
        assertEquals(t0, snap3.currentTrack)
    }

    @Test
    fun playQueueIndexAndSkipNavigateExactSlotEvenWithDuplicateTrackIds() {
        val controller = PlaybackQueueController()
        val aFirst = track("dup_a", title = "Song A (1)")
        val b = track("b", title = "Song B")
        val aSecond = track("dup_a", title = "Song A (2)")
        val c = track("c", title = "Song C")

        // Start directly on duplicate slot 2
        val initial = controller.setQueue(listOf(aFirst, b, aSecond, c), startIndex = 2)
        assertEquals(2, initial.currentIndex)
        assertEquals("Song A (2)", initial.currentTrack?.title)

        // playQueueIndex(0) selects first slot; playQueueIndex(2) selects third slot
        val firstPlayed = controller.playQueueIndex(0)
        assertEquals("Song A (1)", firstPlayed?.title)
        assertEquals(0, controller.currentQueueIndex())

        val secondPlayed = controller.playQueueIndex(2)
        assertEquals("Song A (2)", secondPlayed?.title)
        assertEquals(2, controller.currentQueueIndex())

        // Invalid index is a safe no-op
        assertNull(controller.playQueueIndex(-1))
        assertNull(controller.playQueueIndex(4))
        assertEquals(2, controller.currentQueueIndex())

        // skipNext from index 1 lands on index 2 (not jumping back to index 0)
        controller.playQueueIndex(1)
        val skippedTo2 = controller.skipNext(PlaybackRepeatMode.RepeatAll)
        assertEquals(2, controller.currentQueueIndex())
        assertEquals("Song A (2)", skippedTo2?.title)

        // skipNext wraps around with RepeatAll, stops at end with RepeatOff
        controller.playQueueIndex(3)
        assertNull(controller.skipNext(PlaybackRepeatMode.Off))
        assertEquals(3, controller.currentQueueIndex())

        val wrapped = controller.skipNext(PlaybackRepeatMode.RepeatAll)
        assertEquals(0, controller.currentQueueIndex())
        assertEquals("Song A (1)", wrapped?.title)

        // skipPrevious wraps from 0 to last index
        val prevWrapped = controller.skipPrevious()
        assertEquals(3, controller.currentQueueIndex())
        assertEquals("c", prevWrapped?.id)
    }

    @Test
    fun moveQueueItemBeforeAndAfterActiveIndexKeepsActiveIndexAccurate() {
        val controller = PlaybackQueueController()
        val tracks = (0..4).map { track("t$it") }
        controller.setQueue(tracks, startIndex = 2) // Active = t2 at index 2

        // Move entirely before currentIndex (0 -> 1): currentIndex stays 2
        assertTrue(controller.moveQueueItem(0, 1))
        var snap = controller.snapshot()
        assertEquals(listOf("t1", "t0", "t2", "t3", "t4"), snap.queue.map { it.id })
        assertEquals(2, snap.currentIndex)
        assertEquals("t2", snap.currentTrack?.id)

        // Move entirely after currentIndex (4 -> 3): currentIndex stays 2
        assertTrue(controller.moveQueueItem(4, 3))
        snap = controller.snapshot()
        assertEquals(listOf("t1", "t0", "t2", "t4", "t3"), snap.queue.map { it.id })
        assertEquals(2, snap.currentIndex)
        assertEquals("t2", snap.currentTrack?.id)

        // Move item from BEFORE active index ONTO active index (0 -> 2): currentIndex shifts 2 -> 1
        assertTrue(controller.moveQueueItem(0, 2))
        snap = controller.snapshot()
        assertEquals(listOf("t0", "t2", "t1", "t4", "t3"), snap.queue.map { it.id })
        assertEquals(1, snap.currentIndex)
        assertEquals("t2", snap.currentTrack?.id)

        // Move item from BEFORE active index PAST active index (0 -> 4): currentIndex shifts 1 -> 0
        assertTrue(controller.moveQueueItem(0, 4))
        snap = controller.snapshot()
        assertEquals(listOf("t2", "t1", "t4", "t3", "t0"), snap.queue.map { it.id })
        assertEquals(0, snap.currentIndex)
        assertEquals("t2", snap.currentTrack?.id)

        // Move item from AFTER active index ONTO/BEFORE active index (3 -> 0): currentIndex shifts 0 -> 1
        assertTrue(controller.moveQueueItem(3, 0))
        snap = controller.snapshot()
        assertEquals(listOf("t3", "t2", "t1", "t4", "t0"), snap.queue.map { it.id })
        assertEquals(1, snap.currentIndex)
        assertEquals("t2", snap.currentTrack?.id)
    }

    @Test
    fun moveActiveTrackItselfUpdatesCurrentIndexAndSubsequentSkips() {
        val controller = PlaybackQueueController()
        val tracks = (0..4).map { track("t$it") }
        controller.setQueue(tracks, startIndex = 1) // Active = t1 at index 1

        // Drag active item down from 1 to 3
        assertTrue(controller.moveQueueItem(1, 3))
        var snap = controller.snapshot()
        assertEquals(listOf("t0", "t2", "t3", "t1", "t4"), snap.queue.map { it.id })
        assertEquals(3, snap.currentIndex)
        assertEquals("t1", snap.currentTrack?.id)

        // skipNext from new position 3 -> plays t4 at index 4
        val next = controller.skipNext(PlaybackRepeatMode.Off)
        assertEquals(4, controller.currentQueueIndex())
        assertEquals("t4", next?.id)

        // Drag active item (t4 at index 4) up to index 0
        assertTrue(controller.moveQueueItem(4, 0))
        snap = controller.snapshot()
        assertEquals(listOf("t4", "t0", "t2", "t3", "t1"), snap.queue.map { it.id })
        assertEquals(0, snap.currentIndex)
        assertEquals("t4", snap.currentTrack?.id)

        // Invalid or no-op moves return false and leave queue untouched
        assertFalse(controller.moveQueueItem(2, 2))
        assertFalse(controller.moveQueueItem(-1, 2))
        assertFalse(controller.moveQueueItem(2, 5))
        assertEquals(listOf("t4", "t0", "t2", "t3", "t1"), controller.snapshot().queue.map { it.id })
        assertEquals(0, controller.currentQueueIndex())
    }

    @Test
    fun moveQueueItemWithDuplicateTrackIdsPreservesActiveSlotIdentity() {
        val controller = PlaybackQueueController()
        val a1 = track("dup", title = "First")
        val b = track("b", title = "B")
        val a2 = track("dup", title = "Second")
        val c = track("c", title = "C")

        controller.setQueue(listOf(a1, b, a2, c), startIndex = 2) // Active = Second ("dup") at index 2

        // Move First ("dup") from index 0 past active index 2 to index 3
        assertTrue(controller.moveQueueItem(0, 3))
        val snap = controller.snapshot()
        assertEquals(listOf("B", "Second", "C", "First"), snap.queue.map { it.title })
        assertEquals(1, snap.currentIndex)
        assertEquals("Second", snap.currentTrack?.title)
    }
}
