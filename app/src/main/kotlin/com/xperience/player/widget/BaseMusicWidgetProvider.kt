package com.xperience.player.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Shared coroutine-backed [AppWidgetProvider] base that safely wraps `goAsync()` with a bounded
 * timeout and guaranteed `pendingResult.finish()` teardown so launcher updates never leak or ANR.
 */
abstract class BaseMusicWidgetProvider : AppWidgetProvider() {

    protected abstract val providerTag: String

    protected abstract suspend fun performUpdate(context: Context, appWidgetIds: IntArray?)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        dispatchAsyncUpdate(context, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        dispatchAsyncUpdate(context, intArrayOf(appWidgetId))
    }

    override fun onEnabled(context: Context) {
        dispatchAsyncUpdate(context, null)
    }

    private fun dispatchAsyncUpdate(context: Context, appWidgetIds: IntArray?) {
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        providerScope.launch {
            try {
                withTimeoutOrNull(UPDATE_TIMEOUT_MS) {
                    performUpdate(appContext, appWidgetIds)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e(providerTag, "Failed updating widget: ${e.message}", e)
            } finally {
                runCatching { pendingResult.finish() }
            }
        }
    }

    companion object {
        private const val UPDATE_TIMEOUT_MS = 8_500L
        private val providerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
