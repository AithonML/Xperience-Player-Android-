package com.xperience.player.widget

import android.content.Context

/**
 * Responsive Material You Mini Player home-screen widget (4x1 compact bar, 4x2 card with
 * progress & favorite toggle, 4x3 expanded card with Ambient Mode quick-launch).
 */
class MiniPlayerWidgetProvider : BaseMusicWidgetProvider() {

    override val providerTag: String = "MiniPlayerWidgetProvider"

    override suspend fun performUpdate(context: Context, appWidgetIds: IntArray?) {
        WidgetUpdater.updateMiniPlayerWidgetsNow(context, appWidgetIds)
    }
}
