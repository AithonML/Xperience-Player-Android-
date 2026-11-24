package com.xperience.player.cast

import com.google.android.gms.cast.MediaStatus
import com.xperience.player.domain.model.PlaybackRepeatMode
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.service.cast.CastConnectionState
import com.xperience.player.service.cast.CastHandoffSnapshot
import com.xperience.player.service.cast.CastMediaMapper
import com.xperience.player.service.cast.CastMimeResolver
import com.xperience.player.service.cast.CastPlaybackState
import com.xperience.player.service.cast.CastRouteDevice
import com.xperience.player.service.cast.CastStateReducer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CastMediaMapperAndHandoffTest {

    private val sampleQueue = listOf(
        TrackItem(
            id = "ytm_track_1",
            title = "Midnight City",
            artist = "M83",
            album = "Hurry Up, We're Dreaming",
            durationMs = 243_000L,
            artworkUri = "https://example.com/art1.jpg",
            isLiked = true
        ),
        TrackItem(
            id = "local_track_2",
            title = "Starboy",
            artist = "The Weeknd",
            album = "Starboy",
            durationMs = 230_000L,
            audioUrl = "content://media/external/audio/media/42",
            artworkUri = "https://example.com/art2.jpg",
            isExplicit = true
        ),
        TrackItem(
            id = "ytm_track_3",
            title = "Blinding Lights",
            artist = "The Weeknd",
            album = "After Hours",
            durationMs = 200_000L
        )
    )

    @Test
    fun resolveAudioMimeType_mapsCommonFormatsAndStripsQueryStrings() {
        assertEquals("audio/mpeg", CastMimeResolver.resolveAudioMimeType("https://cdn.example.com/song.mp3?token=abc"))
        assertEquals("audio/mp4", CastMimeResolver.resolveAudioMimeType("/storage/emulated/0/Music/track.m4a"))
        assertEquals("audio/flac", CastMimeResolver.resolveAudioMimeType("file:///music/hi_res.FLAC"))
        assertEquals("audio/wav", CastMimeResolver.resolveAudioMimeType("recording.wav"))
        assertEquals("audio/ogg", CastMimeResolver.resolveAudioMimeType("stream.opus"))
        assertEquals("audio/webm", CastMimeResolver.resolveAudioMimeType("audio.webm"))
        assertEquals("application/x-mpegURL", CastMimeResolver.resolveAudioMimeType("https://cdn.test/live.m3u8"))
        assertEquals("audio/mp4", CastMimeResolver.resolveAudioMimeType(null))

        // ContentResolver MIME takes precedence over extensionless MediaStore content:// URIs
        assertEquals(
            "audio/mpeg",
            CastMimeResolver.resolveAudioMimeType("content://media/external/audio/media/42", "audio/mpeg")
        )
        assertEquals(
            "audio/flac",
            CastMimeResolver.resolveAudioMimeType("content://media/external/audio/media/43", "audio/flac")
        )
        assertEquals(
            "audio/ogg",
            CastMimeResolver.resolveAudioMimeType("content://media/external/audio/media/44", "application/ogg")
        )
        assertEquals(
            "image/png",
            CastMimeResolver.resolveImageMimeType("content://media/external/audio/albumart/9", "image/png")
        )
        assertEquals("mp3", CastMimeResolver.extensionForAudioMimeType("audio/mpeg"))
        assertEquals("flac", CastMimeResolver.extensionForAudioMimeType("audio/flac"))
        assertEquals("png", CastMimeResolver.extensionForImageMimeType("image/png"))
    }

    @Test
    fun isDirectHttpStreamUrl_acceptsDirectStreamsAndRejectsWatchPagesOrLocalUris() {
        assertTrue(CastMimeResolver.isDirectHttpStreamUrl("https://cdn.example.com/audio/song.m4a"))
        assertTrue(CastMimeResolver.isDirectHttpStreamUrl("http://192.168.1.5:8080/media/abc/track.mp3"))
        assertFalse(CastMimeResolver.isDirectHttpStreamUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertFalse(CastMimeResolver.isDirectHttpStreamUrl("https://music.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertFalse(CastMimeResolver.isDirectHttpStreamUrl("https://youtu.be/dQw4w9WgXcQ"))
        assertFalse(CastMimeResolver.isDirectHttpStreamUrl("content://media/external/audio/media/99"))
        assertFalse(CastMimeResolver.isDirectHttpStreamUrl("file:///sdcard/Music/test.mp3"))
        assertFalse(CastMimeResolver.isDirectHttpStreamUrl(null))
    }

    @Test
    fun customDataRoundTrip_preservesKnownTrackMetadataAndSupportsFallbackReconstruction() {
        val original = sampleQueue[1]
        val json = CastMediaMapper.buildCustomData(original)

        // When track is in knownQueue, preserves full local TrackItem (including isExplicit & audioUrl)
        val matched = CastMediaMapper.resolveTrackFromCustomData(
            customData = json,
            knownQueue = sampleQueue
        )
        assertEquals(original, matched)

        // When track is NOT in knownQueue (e.g., reconnected session after process restart), reconstructs cleanly
        val reconstructed = CastMediaMapper.resolveTrackFromCustomData(
            customData = json,
            knownQueue = emptyList()
        )
        assertNotNull(reconstructed)
        assertEquals(original.id, reconstructed!!.id)
        assertEquals(original.title, reconstructed.title)
        assertEquals(original.artist, reconstructed.artist)
        assertEquals(original.album, reconstructed.album)
        assertEquals(original.durationMs, reconstructed.durationMs)
        assertEquals(original.artworkUri, reconstructed.artworkUri)

        // When customData is null, falls back to receiver MediaMetadata fields
        val fromFallback = CastMediaMapper.resolveTrackFromCustomData(
            customData = null,
            knownQueue = emptyList(),
            fallbackTitle = "Remote Song",
            fallbackArtist = "Remote Artist",
            fallbackAlbum = "Remote Album",
            fallbackArtworkUrl = "https://img.test/cover.jpg",
            fallbackDurationMs = 185_000L
        )
        assertNotNull(fromFallback)
        assertEquals("Remote Song", fromFallback!!.title)
        assertEquals("Remote Artist", fromFallback.artist)
        assertEquals(185_000L, fromFallback.durationMs)
    }

    @Test
    fun repeatAndShuffleMapping_roundTripsAllModes() {
        assertEquals(
            MediaStatus.REPEAT_MODE_REPEAT_OFF,
            CastMediaMapper.toCastRepeatMode(PlaybackRepeatMode.Off, isShuffleEnabled = false)
        )
        assertEquals(
            MediaStatus.REPEAT_MODE_REPEAT_SINGLE,
            CastMediaMapper.toCastRepeatMode(PlaybackRepeatMode.RepeatOne, isShuffleEnabled = false)
        )
        assertEquals(
            MediaStatus.REPEAT_MODE_REPEAT_ALL,
            CastMediaMapper.toCastRepeatMode(PlaybackRepeatMode.RepeatAll, isShuffleEnabled = false)
        )
        assertEquals(
            MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE,
            CastMediaMapper.toCastRepeatMode(PlaybackRepeatMode.RepeatAll, isShuffleEnabled = true)
        )

        assertEquals(
            PlaybackRepeatMode.Off to false,
            CastMediaMapper.fromCastRepeatMode(MediaStatus.REPEAT_MODE_REPEAT_OFF)
        )
        assertEquals(
            PlaybackRepeatMode.RepeatOne to false,
            CastMediaMapper.fromCastRepeatMode(MediaStatus.REPEAT_MODE_REPEAT_SINGLE)
        )
        assertEquals(
            PlaybackRepeatMode.RepeatAll to false,
            CastMediaMapper.fromCastRepeatMode(MediaStatus.REPEAT_MODE_REPEAT_ALL)
        )
        assertEquals(
            PlaybackRepeatMode.RepeatAll to true,
            CastMediaMapper.fromCastRepeatMode(MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE)
        )
    }

    @Test
    fun queueNavigation_respectsRepeatModeWrapAround() {
        assertEquals(1, CastMediaMapper.computeNextQueueIndex(0, 3, PlaybackRepeatMode.Off))
        assertNull(CastMediaMapper.computeNextQueueIndex(2, 3, PlaybackRepeatMode.Off))
        assertEquals(0, CastMediaMapper.computeNextQueueIndex(2, 3, PlaybackRepeatMode.RepeatAll))

        assertEquals(0, CastMediaMapper.computePreviousQueueIndex(1, 3, PlaybackRepeatMode.Off))
        assertEquals(0, CastMediaMapper.computePreviousQueueIndex(0, 3, PlaybackRepeatMode.Off))
        assertEquals(2, CastMediaMapper.computePreviousQueueIndex(0, 3, PlaybackRepeatMode.RepeatAll))
    }

    @Test
    fun castSessionHandoff_preservesTrackQueuePositionAndPlaybackStateAcrossConnectAndDisconnect() {
        // 1. Capture local ExoPlayer playback snapshot before Cast connect
        val localHandoff = CastHandoffSnapshot.fromLocalPlayback(
            currentTrack = sampleQueue[1],
            queue = sampleQueue,
            currentQueueIndex = 1,
            positionMs = 64_500L,
            isPlaying = true,
            repeatMode = PlaybackRepeatMode.RepeatAll,
            isShuffleEnabled = true
        )
        assertTrue(localHandoff.hasPlayableContent)
        assertEquals("local_track_2", localHandoff.currentTrack?.id)
        assertEquals(1, localHandoff.currentQueueIndex)

        // 2. Connect -> Connecting -> Connected
        val connecting = CastStateReducer.onConnecting(
            current = CastPlaybackState(),
            deviceName = "Living Room TV",
            deviceId = "route_1"
        )
        assertEquals(CastConnectionState.CONNECTING, connecting.connectionState)
        assertTrue(connecting.isActiveSession)

        val connected = CastStateReducer.onSessionConnected(
            current = connecting,
            deviceName = "Living Room TV",
            deviceId = "route_1",
            volume = 0.75f,
            isMuted = false,
            handoff = localHandoff
        )
        assertTrue(connected.isConnected)
        assertEquals("Living Room TV", connected.deviceName)
        assertEquals(sampleQueue[1], connected.currentTrack)
        assertEquals(64_500L, connected.positionMs)
        assertEquals(0.75f, connected.volume, 0.001f)
        assertEquals(PlaybackRepeatMode.RepeatAll, connected.repeatMode)
        assertTrue(connected.isShuffleEnabled)

        // 3. Remote playback advances to 115,000ms and volume changes
        val progressed = CastStateReducer.onRemoteProgress(connected, positionMs = 115_000L, durationMs = 230_000L)
        assertEquals(0.5f, progressed.progressFraction, 0.001f)
        val volumeAdjusted = CastStateReducer.onRemoteVolumeChanged(progressed, volume = 1.4f, isMuted = true)
        assertEquals(1.0f, volumeAdjusted.volume, 0.001f)
        assertTrue(volumeAdjusted.isMuted)

        // 4. Disconnect emits exact Cast -> Local resume handoff using latest remote position
        val (disconnected, resumeHandoff) = CastStateReducer.onSessionDisconnected(
            current = volumeAdjusted,
            latestPositionMs = 128_200L
        )
        assertEquals(CastConnectionState.DISCONNECTED, disconnected.connectionState)
        assertFalse(disconnected.isActiveSession)
        assertEquals(sampleQueue[1], resumeHandoff.currentTrack)
        assertEquals(sampleQueue, resumeHandoff.queue)
        assertEquals(1, resumeHandoff.currentQueueIndex)
        assertEquals(128_200L, resumeHandoff.positionMs)
        assertTrue(resumeHandoff.wasPlaying)
        assertEquals(PlaybackRepeatMode.RepeatAll, resumeHandoff.repeatMode)
    }

    @Test
    fun castRouteDevice_volumeFractionClampsAndHandlesZeroMax() {
        val normal = CastRouteDevice(id = "d1", name = "TV", volume = 15, volumeMax = 20)
        assertEquals(0.75f, normal.volumeFraction, 0.001f)

        val zeroMax = CastRouteDevice(id = "d2", name = "Fixed", volume = 5, volumeMax = 0)
        assertEquals(0f, zeroMax.volumeFraction, 0.001f)
    }
}
