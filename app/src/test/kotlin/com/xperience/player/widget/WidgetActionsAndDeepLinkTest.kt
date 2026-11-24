package com.xperience.player.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetActionsAndDeepLinkTest {

    @Test
    fun parseRawResolvesStandardWidgetActions() {
        assertEquals(
            WidgetDeepLink.OpenPlayer,
            WidgetDeepLink.parseRaw(action = WidgetActions.ACTION_OPEN_PLAYER)
        )
        assertEquals(
            WidgetDeepLink.OpenAmbient,
            WidgetDeepLink.parseRaw(action = WidgetActions.ACTION_OPEN_AMBIENT)
        )
        assertEquals(
            WidgetDeepLink.OpenHome,
            WidgetDeepLink.parseRaw(action = WidgetActions.ACTION_OPEN_HOME)
        )
        assertEquals(
            WidgetDeepLink.RefreshRecommendations,
            WidgetDeepLink.parseRaw(action = WidgetActions.ACTION_REFRESH_RECOMMENDATIONS)
        )
    }

    @Test
    fun parseRawResolvesOpenPlaylistWithAllMetadata() {
        val parsed = WidgetDeepLink.parseRaw(
            action = WidgetActions.ACTION_PLAY_PLAYLIST,
            deepLinkType = WidgetDeepLink.TYPE_OPEN_PLAYLIST,
            playlistId = "RDAMVMabc",
            playlistTitle = "Indie Radio",
            sourceTypeRaw = WidgetPlaylistSourceType.RADIO_STATION.wireValue,
            ytmPlaylistId = "RDAMVMabc",
            browseId = "VLRDAMVMabc",
            seedVideoId = "abc",
            params = "wAEB",
            autoPlay = true
        )

        assertTrue(parsed is WidgetDeepLink.OpenPlaylist)
        val playlistLink = parsed as WidgetDeepLink.OpenPlaylist
        assertEquals("RDAMVMabc", playlistLink.id)
        assertEquals("Indie Radio", playlistLink.title)
        assertEquals(WidgetPlaylistSourceType.RADIO_STATION, playlistLink.sourceType)
        assertEquals("RDAMVMabc", playlistLink.ytmPlaylistId)
        assertEquals("VLRDAMVMabc", playlistLink.browseId)
        assertEquals("abc", playlistLink.seedVideoId)
        assertEquals("wAEB", playlistLink.params)
        assertTrue(playlistLink.autoPlay)
    }

    @Test
    fun parseRawFallsBackGracefullyOnMissingPlaylistIdOrNonWidgetAction() {
        // Blank playlistId falls back to ytmPlaylistId
        val fromYtmId = WidgetDeepLink.parseRaw(
            action = WidgetActions.ACTION_PLAY_PLAYLIST,
            playlistId = "  ",
            ytmPlaylistId = "PL_REMOTE",
            sourceTypeRaw = WidgetPlaylistSourceType.MADE_FOR_YOU.wireValue
        ) as WidgetDeepLink.OpenPlaylist
        assertEquals("PL_REMOTE", fromYtmId.id)

        // Missing both IDs degrades safely to OpenHome instead of crashing
        assertEquals(
            WidgetDeepLink.OpenHome,
            WidgetDeepLink.parseRaw(action = WidgetActions.ACTION_PLAY_PLAYLIST)
        )

        // Standard launcher intent should not trigger a widget deep link
        assertNull(WidgetDeepLink.parseRaw(action = "android.intent.action.MAIN"))
        assertNull(WidgetDeepLink.parseRaw(action = null))
    }

    @Test
    fun throttlePolicyPushesStructuralChangesImmediatelyAndThrottlesSteadyTicks() {
        val base = WidgetPlaybackSnapshot(
            trackId = "t1",
            title = "Song A",
            artist = "Artist A",
            isPlaying = true,
            isLiked = false,
            positionMs = 10_000L,
            durationMs = 200_000L
        )

        // Initial snapshot renders if widgets exist, persists only if none exist
        assertEquals(
            WidgetSyncThrottlePolicy.SyncDecision.PERSIST_AND_RENDER,
            WidgetSyncThrottlePolicy.evaluate(null, base, 0L, 1_000L, hasActiveWidgets = true)
        )
        assertEquals(
            WidgetSyncThrottlePolicy.SyncDecision.PERSIST_ONLY,
            WidgetSyncThrottlePolicy.evaluate(null, base, 0L, 1_000L, hasActiveWidgets = false)
        )

        // 33ms steady tick should be skipped
        val tick33ms = base.copy(positionMs = 10_033L)
        assertEquals(
            WidgetSyncThrottlePolicy.SyncDecision.SKIP,
            WidgetSyncThrottlePolicy.evaluate(base, tick33ms, 1_000L, 1_033L, hasActiveWidgets = true)
        )

        // After 5 seconds of steady playback, progress update renders
        val tick5s = base.copy(positionMs = 15_000L)
        assertEquals(
            WidgetSyncThrottlePolicy.SyncDecision.PERSIST_AND_RENDER,
            WidgetSyncThrottlePolicy.evaluate(base, tick5s, 1_000L, 6_000L, hasActiveWidgets = true)
        )

        // User seek jump >= 3 seconds renders immediately even within the 5s window
        val seeked = base.copy(positionMs = 80_000L)
        assertEquals(
            WidgetSyncThrottlePolicy.SyncDecision.PERSIST_AND_RENDER,
            WidgetSyncThrottlePolicy.evaluate(base, seeked, 1_000L, 1_500L, hasActiveWidgets = true)
        )

        // Favorite toggle or pause triggers immediate render
        assertEquals(
            WidgetSyncThrottlePolicy.SyncDecision.PERSIST_AND_RENDER,
            WidgetSyncThrottlePolicy.evaluate(base, base.copy(isLiked = true), 1_000L, 1_050L, hasActiveWidgets = true)
        )
        assertEquals(
            WidgetSyncThrottlePolicy.SyncDecision.PERSIST_AND_RENDER,
            WidgetSyncThrottlePolicy.evaluate(base, base.copy(isPlaying = false), 1_000L, 1_050L, hasActiveWidgets = true)
        )

        // When no widgets are placed, steady progress ticks are skipped completely
        assertEquals(
            WidgetSyncThrottlePolicy.SyncDecision.SKIP,
            WidgetSyncThrottlePolicy.evaluate(base, tick5s, 1_000L, 6_000L, hasActiveWidgets = false)
        )
    }
}
