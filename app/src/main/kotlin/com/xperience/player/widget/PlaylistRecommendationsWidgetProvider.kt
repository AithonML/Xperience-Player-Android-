package com.xperience.player.widget

import android.content.Context

/**
 * Responsive Material You Playlist Recommendations widget displaying curated mixes, smart
 * suggestions, user playlists, radio stations, and Liked Songs with one-tap launch and refresh.
 */
class PlaylistRecommendationsWidgetProvider : BaseMusicWidgetProvider() {

    override val providerTag: String = "PlaylistRecommendationsWidgetProvider"

    override suspend fun performUpdate(context: Context, appWidgetIds: IntArray?) {
        WidgetSyncBridge.hydrateRecommendationsFromLocalDiskIfEmpty(context)
        WidgetUpdater.updatePlaylistWidgetsNow(context, appWidgetIds)
    }
}
