package com.xperience.player.service

import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView

/**
 * Centralizes cleanup of Android WebView session state (cookies, DOM/WebStorage,
 * HTTP cache, navigation history, and form data) so live Google/YouTube account
 * session cookies are never left persisted in plaintext under `app_webview/`.
 */
object WebViewSessionCleaner {

    fun clearAll(webView: WebView? = null) {
        val cleanupAction = {
            runCatching {
                val cookieManager = CookieManager.getInstance()
                cookieManager.removeAllCookies(null)
                cookieManager.flush()
            }
            runCatching {
                WebStorage.getInstance().deleteAllData()
            }
            if (webView != null) {
                runCatching { webView.clearCache(true) }
                runCatching { webView.clearHistory() }
                runCatching { webView.clearFormData() }
            }
        }

        val mainLooper = runCatching { Looper.getMainLooper() }.getOrNull()
        val isMainThread = mainLooper != null && runCatching { Looper.myLooper() == mainLooper }.getOrDefault(false)
        if (isMainThread || mainLooper == null) {
            cleanupAction()
        } else {
            Handler(mainLooper).post(cleanupAction)
        }
    }
}
