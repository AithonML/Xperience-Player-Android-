package com.xperience.player.cast

import com.xperience.player.domain.model.PlaybackRepeatMode
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.service.cast.CastConnectionState
import com.xperience.player.service.cast.CastSessionController
import com.xperience.player.service.cast.LocalCastMediaServer
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CastSessionControllerFallbackTest {

    @Test
    fun gmsUnavailable_marksStateUnavailableWithoutCrashing() {
        val server = LocalCastMediaServer(bindHost = "127.0.0.1", requestedPort = 0)
        val controller = CastSessionController(
            appContext = null,
            localServer = server,
            mainDispatcher = Dispatchers.Unconfined,
            gmsAvailabilityCheck = { false },
            skipAndroidFrameworkInit = true
        )

        try {
            assertFalse(controller.isCastAvailable.value)
            assertEquals(CastConnectionState.UNAVAILABLE, controller.castState.value.connectionState)
            assertFalse(controller.castState.value.isAvailable)
            assertTrue(controller.routes.value.isEmpty())
            assertFalse(controller.isScanning.value)
        } finally {
            controller.release()
        }
    }

    @Test
    fun gmsCheckThrowsException_fallsBackGracefullyAndAllControlsAreSafeNoOps() {
        val server = LocalCastMediaServer(bindHost = "127.0.0.1", requestedPort = 0)
        val controller = CastSessionController(
            appContext = null,
            localServer = server,
            mainDispatcher = Dispatchers.Unconfined,
            gmsAvailabilityCheck = { throw IllegalStateException("GMS missing on emulator") },
            skipAndroidFrameworkInit = true
        )

        var endedHandoffCount = 0
        controller.onCastSessionEnded = { endedHandoffCount++ }

        val sampleTrack = TrackItem(
            id = "t1",
            title = "Safe Fallback Track",
            artist = "Xperience",
            album = "Tests",
            durationMs = 180_000L
        )

        try {
            assertEquals(CastConnectionState.UNAVAILABLE, controller.castState.value.connectionState)

            // Every control call must degrade gracefully without throwing
            controller.startActiveDiscovery()
            controller.stopActiveDiscovery()
            controller.selectRoute("non_existent_route")
            controller.setRouteVolume("non_existent_route", 50)
            controller.playTrack(sampleTrack)
            controller.togglePlayPause()
            controller.play()
            controller.pause()
            controller.seekTo(30_000L)
            controller.seekToFraction(0.5f)
            controller.skipNext()
            controller.skipPrevious()
            controller.jumpToQueueIndex(0)
            controller.setVolume(0.6f)
            controller.toggleMute()
            controller.setRepeatMode(PlaybackRepeatMode.RepeatOne)
            controller.cycleRepeatMode()
            controller.toggleShuffle()
            controller.clearError()
            controller.disconnect()

            // No bogus local resume handoff fired since no track was ever active on Cast
            assertEquals(0, endedHandoffCount)
            assertEquals(CastConnectionState.UNAVAILABLE, controller.castState.value.connectionState)
            assertFalse(server.isRunning)
        } finally {
            // Double release must also be idempotent
            controller.release()
            controller.release()
        }
    }

    @Test
    fun repeatAndShuffleControls_updateStateDeterministically() {
        val controller = CastSessionController(
            appContext = null,
            localServer = LocalCastMediaServer(bindHost = "127.0.0.1", requestedPort = 0),
            mainDispatcher = Dispatchers.Unconfined,
            gmsAvailabilityCheck = { false },
            skipAndroidFrameworkInit = true
        )

        try {
            assertEquals(PlaybackRepeatMode.Off, controller.castState.value.repeatMode)
            assertEquals(PlaybackRepeatMode.RepeatAll, controller.cycleRepeatMode())
            assertEquals(PlaybackRepeatMode.RepeatOne, controller.cycleRepeatMode())
            assertEquals(PlaybackRepeatMode.Off, controller.cycleRepeatMode())

            assertFalse(controller.castState.value.isShuffleEnabled)
            controller.toggleShuffle()
            assertTrue(controller.castState.value.isShuffleEnabled)
            controller.toggleShuffle()
            assertFalse(controller.castState.value.isShuffleEnabled)
        } finally {
            controller.release()
        }
    }
}
