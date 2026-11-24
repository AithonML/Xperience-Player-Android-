package com.xperience.player.ui.ambient

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AmbientHudControllerTest {

    @Test
    fun `auto hide timer hides HUD precisely after configured delay`() = runTest {
        val controller = AmbientHudController(
            initialHudVisible = true,
            hudAutoHideDelayMs = 4_500L
        )

        val job = controller.scheduleAutoHide(this)
        assertNotNull(job)

        advanceTimeBy(4_499L)
        runCurrent()
        assertTrue("HUD should remain visible just before timeout", controller.state.value.isHudVisible)

        advanceTimeBy(1L)
        runCurrent()
        assertFalse("HUD should auto-hide once timeout elapses", controller.state.value.isHudVisible)
    }

    @Test
    fun `registerInteraction resets auto hide countdown without premature dismissal`() = runTest {
        val controller = AmbientHudController(
            initialHudVisible = true,
            hudAutoHideDelayMs = 4_000L
        )

        controller.scheduleAutoHide(this)
        advanceTimeBy(3_000L)
        runCurrent()
        assertTrue(controller.state.value.isHudVisible)

        // User taps a transport control at t = 3000ms -> resets deadline to t = 7000ms
        controller.registerInteraction()
        controller.scheduleAutoHide(this)

        advanceTimeBy(2_500L) // t = 5500ms (past original 4000ms deadline)
        runCurrent()
        assertTrue("HUD should still be visible after interaction reset", controller.state.value.isHudVisible)

        advanceTimeBy(1_500L) // t = 7000ms
        runCurrent()
        assertFalse("HUD should hide after full delay following last interaction", controller.state.value.isHudVisible)
    }

    @Test
    fun `scrubbing suspends auto hide and blocks non-forced hide until drag ends`() = runTest {
        val controller = AmbientHudController(
            initialHudVisible = true,
            hudAutoHideDelayMs = 3_000L
        )

        controller.scheduleAutoHide(this)
        advanceTimeBy(2_000L)
        runCurrent()

        controller.setScrubbing(true)
        assertNull("Auto-hide delay must be null while scrubbing", controller.state.value.resolveAutoHideDelayMs())
        assertNull("scheduleAutoHide must not arm timer while scrubbing", controller.scheduleAutoHide(this))

        // Non-forced hide is ignored while dragging the seek bar
        controller.hideHud(force = false)
        assertTrue(controller.state.value.isHudVisible)

        // Even after a long scrub, HUD remains pinned open
        advanceTimeBy(30_000L)
        runCurrent()
        assertTrue(controller.state.value.isHudVisible)

        // Releasing seek thumb resumes auto-hide countdown cleanly
        controller.setScrubbing(false)
        assertEquals(3_000L, controller.state.value.resolveAutoHideDelayMs())
        controller.scheduleAutoHide(this)

        advanceTimeBy(2_999L)
        runCurrent()
        assertTrue(controller.state.value.isHudVisible)

        advanceTimeBy(1L)
        runCurrent()
        assertFalse(controller.state.value.isHudVisible)
    }

    @Test
    fun `toggleHud flips visibility and cancels pending auto hide timer`() = runTest {
        val controller = AmbientHudController(
            initialHudVisible = true,
            hudAutoHideDelayMs = 4_000L
        )
        val firstJob = controller.scheduleAutoHide(this)
        assertNotNull(firstJob)

        controller.toggleHud()
        assertFalse(controller.state.value.isHudVisible)
        assertTrue("Hiding HUD manually must cancel active auto-hide timer", firstJob!!.isCancelled)

        controller.toggleHud()
        assertTrue(controller.state.value.isHudVisible)
        assertEquals(1L, controller.state.value.interactionGeneration)
    }

    @Test
    fun `refreshTimerIfVisible bumps generation only when HUD is already visible`() {
        val controller = AmbientHudController(initialHudVisible = false)
        val initialGen = controller.state.value.interactionGeneration

        controller.refreshTimerIfVisible()
        assertFalse(controller.state.value.isHudVisible)
        assertEquals(initialGen, controller.state.value.interactionGeneration)

        controller.revealHud()
        val revealedGen = controller.state.value.interactionGeneration
        controller.refreshTimerIfVisible()
        assertTrue(controller.state.value.isHudVisible)
        assertEquals(revealedGen + 1L, controller.state.value.interactionGeneration)
    }

    @Test
    fun `panel mode switching and cycling handles lyrics and clock modes cleanly`() {
        val controller = AmbientHudController(initialPanelMode = AmbientPanelMode.LYRICS)

        assertEquals(AmbientPanelMode.LYRICS, controller.state.value.panelMode)

        controller.cyclePanelMode(hasLyrics = true)
        assertEquals(AmbientPanelMode.CLOCK_METADATA, controller.state.value.panelMode)

        controller.cyclePanelMode(hasLyrics = true)
        assertEquals(AmbientPanelMode.CINEMA_ART, controller.state.value.panelMode)

        controller.cyclePanelMode(hasLyrics = true)
        assertEquals(AmbientPanelMode.LYRICS, controller.state.value.panelMode)

        // When no lyrics exist, cycling from CINEMA_ART skips straight to CLOCK_METADATA
        controller.setPanelMode(AmbientPanelMode.CINEMA_ART)
        controller.cyclePanelMode(hasLyrics = false)
        assertEquals(AmbientPanelMode.CLOCK_METADATA, controller.state.value.panelMode)
    }

    @Test
    fun `visual layer and timestamp toggles update state and bump generation while visible`() {
        val controller = AmbientHudController(
            initialHudVisible = true,
            initialVisualizerEnabled = false,
            initialCanvasVideoEnabled = true
        )

        val gen0 = controller.state.value.interactionGeneration

        controller.toggleVisualizer()
        assertTrue(controller.state.value.isVisualizerEnabled)
        assertEquals(gen0 + 1L, controller.state.value.interactionGeneration)

        controller.toggleCanvasVideo()
        assertFalse(controller.state.value.isCanvasVideoEnabled)
        assertEquals(gen0 + 2L, controller.state.value.interactionGeneration)

        controller.toggleRemainingTimeDisplay()
        assertTrue(controller.state.value.showRemainingTime)
        assertEquals(gen0 + 3L, controller.state.value.interactionGeneration)
    }

    @Test
    fun `cancelAutoHideTimer prevents delayed dismissal on teardown`() = runTest {
        val controller = AmbientHudController(
            initialHudVisible = true,
            hudAutoHideDelayMs = 2_000L
        )

        controller.scheduleAutoHide(this)
        advanceTimeBy(1_000L)
        runCurrent()

        controller.cancelAutoHideTimer()
        advanceTimeBy(5_000L)
        runCurrent()

        assertTrue("Cancelled timer must not mutate state after disposal", controller.state.value.isHudVisible)
    }

    @Test
    fun `zero auto hide delay disables automatic timeout`() = runTest {
        val controller = AmbientHudController(
            initialHudVisible = true,
            hudAutoHideDelayMs = 0L
        )
        assertNull(controller.state.value.resolveAutoHideDelayMs())
        assertNull(controller.scheduleAutoHide(this))
    }
}
