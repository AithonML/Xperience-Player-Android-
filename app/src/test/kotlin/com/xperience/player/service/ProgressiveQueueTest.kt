package com.xperience.player.service

import com.xperience.player.domain.model.PlaybackRepeatMode
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.ui.home.OverlayLayer
import com.xperience.player.ui.home.popTopOverlayLayer
import com.xperience.player.ui.home.pushOverlayLayer
import com.xperience.player.ui.home.removeOverlayLayer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressiveQueueTest {

    private fun track(
        id: String,
        title: String = "Title $id",
        artist: String = "Artist $id"
    ) = TrackItem(
        id = id,
        title = title,
        artist = artist,
        album = "Album $id",
        durationMs = 180_000L
    )

    @Test
    fun `search single track replaces prior queue and prefetches similar tracks`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = PlaybackQueueController()
        controller.setQueue(listOf(track("old1"), track("old2"), track("old3")), startIndex = 1)

        var fetchCount = 0
        var capturedSeeds: List<TrackItem> = emptyList()
        var queueUpdates = 0
        var autoAdvancedTo: TrackItem? = null

        val manager = ProgressiveQueueManager(
            scope = this,
            ioDispatcher = dispatcher,
            queueController = controller,
            fetchRemoteSimilar = { seeds, excludeIds ->
                fetchCount++
                capturedSeeds = seeds
                assertTrue("search_1" in excludeIds)
                listOf(track("sim_1"), track("sim_2"))
            },
            onQueueUpdated = { queueUpdates++ },
            onAutoAdvance = { nextTrack, _ -> autoAdvancedTo = nextTrack }
        )

        val searchTrack = track("search_1", title = "Daylight", artist = "David Kushner")
        manager.onQueueReplaced()
        val snap = controller.playSingleTrack(searchTrack)
        assertEquals(listOf(searchTrack), snap.queue)
        assertEquals(0, snap.currentIndex)

        manager.maybePrefetch(PlaybackRepeatMode.Off)
        advanceUntilIdle()

        assertEquals(1, fetchCount)
        assertEquals(listOf(searchTrack), capturedSeeds)
        assertEquals(1, queueUpdates)
        assertEquals(listOf("search_1", "sim_1", "sim_2"), controller.snapshot().queue.map { it.id })

        // Finishing search_1 seamlessly advances to sim_1
        manager.resolveAndAdvanceFromEnd(PlaybackRepeatMode.Off)
        advanceUntilIdle()

        assertNotNull(autoAdvancedTo)
        assertEquals("sim_1", autoAdvancedTo?.id)
        assertEquals(1, controller.currentQueueIndex())
    }

    @Test
    fun `ordered list plays sequentially and prefetches only near queue end`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = PlaybackQueueController()
        val list = listOf(track("t0"), track("t1"), track("t2"), track("t3"))
        controller.setQueue(list, startIndex = 1) // at t1 (not near end of 4 items)

        var remoteCalls = 0
        val manager = ProgressiveQueueManager(
            scope = this,
            ioDispatcher = dispatcher,
            queueController = controller,
            fetchRemoteSimilar = { _, _ ->
                remoteCalls++
                listOf(track("t4"), track("t5"))
            }
        )

        manager.maybePrefetch(PlaybackRepeatMode.Off)
        advanceUntilIdle()
        assertEquals(0, remoteCalls)
        assertEquals(4, controller.size)

        // Advance to t2 (penultimate track, index = 2 == lastIndex - 1) -> triggers prefetch
        val next2 = controller.skipNext(PlaybackRepeatMode.Off)
        assertEquals("t2", next2?.id)
        manager.maybePrefetch(PlaybackRepeatMode.Off)
        advanceUntilIdle()

        assertEquals(1, remoteCalls)
        assertEquals(listOf("t0", "t1", "t2", "t3", "t4", "t5"), controller.snapshot().queue.map { it.id })

        val next3 = controller.skipNext(PlaybackRepeatMode.Off)
        val next4 = controller.skipNext(PlaybackRepeatMode.Off)
        assertEquals("t3", next3?.id)
        assertEquals("t4", next4?.id)
    }

    @Test
    fun `end of queue awaits existing in-flight prefetch without duplicate network call`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = PlaybackQueueController()
        controller.playSingleTrack(track("s0"))

        val gate = CompletableDeferred<List<TrackItem>>()
        var fetchCalls = 0
        var advancedTrack: TrackItem? = null

        val manager = ProgressiveQueueManager(
            scope = this,
            ioDispatcher = dispatcher,
            queueController = controller,
            fetchRemoteSimilar = { _, _ ->
                fetchCalls++
                gate.await()
            },
            onAutoAdvance = { next, _ -> advancedTrack = next }
        )

        manager.maybePrefetch(PlaybackRepeatMode.Off)
        advanceUntilIdle() // stops inside gate.await()
        assertEquals(1, fetchCalls)

        // Two rapid end triggers (e.g., STATE_ENDED + manual skipNext) while prefetch still pending
        manager.resolveAndAdvanceFromEnd(PlaybackRepeatMode.Off)
        manager.resolveAndAdvanceFromEnd(PlaybackRepeatMode.Off)
        advanceUntilIdle()

        assertEquals(1, fetchCalls)
        assertNull(advancedTrack)

        gate.complete(listOf(track("rec_1"), track("rec_2")))
        advanceUntilIdle()

        assertEquals("rec_1", advancedTrack?.id)
        assertEquals(1, controller.currentQueueIndex())
        assertEquals(3, controller.size)
    }

    @Test
    fun `replacing queue mid-fetch invalidates stale epoch results`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = PlaybackQueueController()
        controller.playSingleTrack(track("old_seed"))

        val oldGate = CompletableDeferred<List<TrackItem>>()
        val manager = ProgressiveQueueManager(
            scope = this,
            ioDispatcher = dispatcher,
            queueController = controller,
            fetchRemoteSimilar = { seeds, _ ->
                if (seeds.firstOrNull()?.id == "old_seed") {
                    oldGate.await()
                } else {
                    listOf(track("fresh_rec"))
                }
            }
        )

        manager.maybePrefetch(PlaybackRepeatMode.Off)
        advanceUntilIdle()

        // User switches to a new search song before old_seed recommendations return
        manager.onQueueReplaced()
        controller.playSingleTrack(track("new_seed"))
        manager.maybePrefetch(PlaybackRepeatMode.Off)

        // Complete old request with stale item (in case cancellation races)
        oldGate.complete(listOf(track("stale_rec")))
        advanceUntilIdle()

        val finalIds = controller.snapshot().queue.map { it.id }
        assertEquals(listOf("new_seed", "fresh_rec"), finalIds)
    }

    @Test
    fun `deduplication removes ytm prefix duplicates and normalized title-artist matches`() {
        val controller = PlaybackQueueController()
        val initial = track(id = "ytm_abc123", title = "Nightcall", artist = "Kavinsky")
        val epoch = run {
            controller.playSingleTrack(initial)
            controller.queueEpoch
        }

        val candidates = listOf(
            track(id = "abc123", title = "Nightcall", artist = "Kavinsky"),
            track(id = "local_42", title = "  nightcall ", artist = "KAVINSKY "),
            track(id = "ytm_unique_1", title = "A Real Hero", artist = "College"),
            track(id = "unique_1", title = "A Real Hero", artist = "College"),
            track(id = "ytm_unique_2", title = "Under Your Spell", artist = "Desire")
        )

        val snapshot = controller.appendSimilarTracks(epoch, candidates)
        assertNotNull(snapshot)
        assertEquals(
            listOf("ytm_abc123", "ytm_unique_1", "ytm_unique_2"),
            snapshot?.queue?.map { it.id }
        )
    }

    @Test
    fun `remote failure falls back to catalog provider and exhausted queue pauses cleanly`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = PlaybackQueueController()
        controller.playSingleTrack(track("seed"))

        var autoAdvanced: TrackItem? = null
        var exhaustedCount = 0

        val managerWithFallback = ProgressiveQueueManager(
            scope = this,
            ioDispatcher = dispatcher,
            queueController = controller,
            fetchRemoteSimilar = { _, _ -> throw RuntimeException("offline") },
            fetchFallbackSimilar = { _, excludeIds ->
                assertTrue("seed" in excludeIds)
                listOf(track("offline_fb_1"))
            },
            onAutoAdvance = { t, _ -> autoAdvanced = t },
            onQueueEndedExhausted = { exhaustedCount++ }
        )

        managerWithFallback.resolveAndAdvanceFromEnd(PlaybackRepeatMode.Off)
        advanceUntilIdle()

        assertEquals("offline_fb_1", autoAdvanced?.id)
        assertEquals(0, exhaustedCount)

        // When both remote & fallback return nothing new, queue exhaustion callback fires cleanly
        val deadController = PlaybackQueueController()
        deadController.playSingleTrack(track("lonely"))
        var deadExhausted = 0
        val emptyManager = ProgressiveQueueManager(
            scope = this,
            ioDispatcher = dispatcher,
            queueController = deadController,
            fetchRemoteSimilar = { _, _ -> emptyList() },
            fetchFallbackSimilar = { _, _ -> emptyList() },
            onQueueEndedExhausted = { deadExhausted++ }
        )

        emptyManager.resolveAndAdvanceFromEnd(PlaybackRepeatMode.Off)
        advanceUntilIdle()

        assertEquals(1, deadExhausted)
    }

    @Test
    fun `repeatAll and repeatOne disable progressive fetch and wrap on skip`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = PlaybackQueueController()
        controller.setQueue(listOf(track("a"), track("b")), startIndex = 1)

        var fetchCount = 0
        val manager = ProgressiveQueueManager(
            scope = this,
            ioDispatcher = dispatcher,
            queueController = controller,
            fetchRemoteSimilar = { _, _ ->
                fetchCount++
                listOf(track("c"))
            }
        )

        assertFalse(controller.shouldPrefetchNext(PlaybackRepeatMode.RepeatAll))
        assertFalse(controller.shouldPrefetchNext(PlaybackRepeatMode.RepeatOne))
        manager.maybePrefetch(PlaybackRepeatMode.RepeatAll)
        manager.resolveAndAdvanceFromEnd(PlaybackRepeatMode.RepeatAll)
        advanceUntilIdle()

        assertEquals(0, fetchCount)
        assertEquals("a", controller.skipNext(PlaybackRepeatMode.RepeatAll)?.id)

        // Switching back to Off at end of queue starts prefetch
        controller.skipNext(PlaybackRepeatMode.Off) // now at b (index 1)
        assertTrue(controller.shouldPrefetchNext(PlaybackRepeatMode.Off))
        manager.maybePrefetch(PlaybackRepeatMode.Off)
        advanceUntilIdle()
        assertEquals(1, fetchCount)
    }

    @Test
    fun `overlay stack preserves underlying detail screens when Search opens and backs out`() {
        var stack = emptyList<OverlayLayer>()

        // Open Album -> tap Search -> Back from Search -> back in Album
        stack = pushOverlayLayer(stack, OverlayLayer.ALBUM_DETAIL)
        stack = pushOverlayLayer(stack, OverlayLayer.SEARCH)
        assertEquals(listOf(OverlayLayer.ALBUM_DETAIL, OverlayLayer.SEARCH), stack)

        val (afterSearchBack, popped) = popTopOverlayLayer(stack)
        assertEquals(OverlayLayer.SEARCH, popped)
        assertEquals(listOf(OverlayLayer.ALBUM_DETAIL), afterSearchBack)

        // Open HomeGrid -> tap Search -> open Artist from Search -> Back -> Back -> HomeGrid
        stack = emptyList()
        stack = pushOverlayLayer(stack, OverlayLayer.HOME_GRID)
        stack = pushOverlayLayer(stack, OverlayLayer.SEARCH)
        stack = pushOverlayLayer(stack, OverlayLayer.ARTIST_DETAIL)
        assertEquals(
            listOf(OverlayLayer.HOME_GRID, OverlayLayer.SEARCH, OverlayLayer.ARTIST_DETAIL),
            stack
        )

        stack = removeOverlayLayer(stack, OverlayLayer.ARTIST_DETAIL)
        assertEquals(listOf(OverlayLayer.HOME_GRID, OverlayLayer.SEARCH), stack)

        stack = removeOverlayLayer(stack, OverlayLayer.SEARCH)
        assertEquals(listOf(OverlayLayer.HOME_GRID), stack)
    }
}
