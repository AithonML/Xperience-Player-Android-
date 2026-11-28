package com.xperience.player.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZeroVolumePauseStateMachineTest {

    @Test
    fun disabledByDefaultNeverPausesOrResumes() {
        val sm = ZeroVolumePauseStateMachine()
        assertEquals(
            ZeroVolumePauseStateMachine.Action.NONE,
            sm.onVolumeChanged(volumeLevel = 0, isPlaying = true, featureEnabled = false)
        )
        assertFalse(sm.autoPausedByZeroVolume)
    }

    @Test
    fun pausesAtZeroVolumeAndResumesWhenVolumeRises() {
        val sm = ZeroVolumePauseStateMachine()

        assertEquals(
            ZeroVolumePauseStateMachine.Action.PAUSE,
            sm.onVolumeChanged(volumeLevel = 0, isPlaying = true, featureEnabled = true)
        )
        assertTrue(sm.autoPausedByZeroVolume)

        assertEquals(
            ZeroVolumePauseStateMachine.Action.RESUME,
            sm.onVolumeChanged(volumeLevel = 3, isPlaying = false, featureEnabled = true)
        )
        assertFalse(sm.autoPausedByZeroVolume)
    }

    @Test
    fun manualPlaybackActionClearsAutoPauseFlag() {
        val sm = ZeroVolumePauseStateMachine()
        assertEquals(
            ZeroVolumePauseStateMachine.Action.PAUSE,
            sm.onVolumeChanged(volumeLevel = 0, isPlaying = true, featureEnabled = true)
        )
        assertTrue(sm.autoPausedByZeroVolume)

        sm.onManualPlaybackAction()
        assertFalse(sm.autoPausedByZeroVolume)

        assertEquals(
            ZeroVolumePauseStateMachine.Action.NONE,
            sm.onVolumeChanged(volumeLevel = 5, isPlaying = false, featureEnabled = true)
        )
    }
}
