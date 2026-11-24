package com.xperience.player.widget

import android.content.Context

/**
 * Responsive Material You Music Hub widget (4x3 / 4x4) combining live Mini Player controls on top
 * with recommended playlist & quick-mix shortcuts underneath.
 */
class MusicHubWidgetProvider : BaseMusicWidgetProvider() {

    override val providerTag: String = "MusicHubWidgetProvider"

    override suspend fun performUpdate(context: Context, appWidgetIds: IntArray?) {
        WidgetSyncBridge.hydrateRecommendationsFromLocalDiskIfEmpty(context)
        WidgetUpdater.updateMusicHubWidgetsNow(context, appWidgetIds)
    }
}
