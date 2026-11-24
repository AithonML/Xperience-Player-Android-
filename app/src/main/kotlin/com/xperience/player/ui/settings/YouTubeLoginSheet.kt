package com.xperience.player.ui.settings

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xperience.player.service.YouTubeAuthManager
import com.xperience.player.ui.adaptive.LocalAppWindowSizeClass
import com.xperience.player.ui.adaptive.shouldUseModalDialogs
import com.xperience.player.ui.component.shapes.ContinuousRoundedRectangle
import com.xperience.player.ui.theme.GoogleSans
import com.xperience.player.ui.theme.LocalAccentColor
import com.xperience.player.ui.theme.LocalAppColors
import com.xperience.player.ui.theme.PlusJakartaSans
import com.xperience.player.ui.utils.pressWobble
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private const val YTM_SERVICE_LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"
private const val YTM_ORIGIN_URL = "https://music.youtube.com"
private const val YT_WWW_URL = "https://www.youtube.com"
private const val YT_ROOT_URL = "https://youtube.com"
private const val MAX_CREDENTIAL_PROBE_ATTEMPTS = 15
private const val CREDENTIAL_PROBE_DELAY_MS = 300L

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeLoginSheet(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onCredentialsCaptured: (cookie: String, visitorData: String, dataSyncId: String?) -> Unit,
    isValidating: Boolean = false,
    validationError: String? = null
) {
    if (!isOpen) return

    val appColors = LocalAppColors.current
    val accentColor = LocalAccentColor.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var currentHostLabel by remember { mutableStateOf("accounts.google.com") }
    var pageProgress by remember { mutableFloatStateOf(0f) }
    var isPageLoading by remember { mutableStateOf(true) }
    val hasCapturedSession = remember { AtomicBoolean(false) }
    val isDisposed = remember { AtomicBoolean(false) }
    val probeGeneration = remember { AtomicInteger(0) }

    // Allow retrying if parent reported a validation error
    LaunchedEffect(validationError) {
        if (!validationError.isNullOrBlank()) {
            hasCapturedSession.set(false)
            probeGeneration.incrementAndGet()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            isDisposed.set(true)
            probeGeneration.incrementAndGet()
            mainHandler.removeCallbacksAndMessages(null)
            runCatching {
                webViewRef?.handler?.removeCallbacksAndMessages(null)
            }
            webViewRef = null
        }
    }

    BackHandler(enabled = !isValidating) {
        val wv = webViewRef
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            onDismiss()
        }
    }

    val useModalDialog = LocalAppWindowSizeClass.current.shouldUseModalDialogs
    val dialogShape = ContinuousRoundedRectangle(24.dp)
    val rimBrush = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (appColors.isDark) 0.24f else 0.72f),
            Color.White.copy(alpha = if (appColors.isDark) 0.07f else 0.22f)
        )
    )

    Dialog(
        onDismissRequest = { if (!isValidating) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = useModalDialog
        )
    ) {
        Surface(
            shape = if (useModalDialog) dialogShape else RectangleShape,
            modifier = if (useModalDialog) {
                Modifier
                    .padding(horizontal = 24.dp, vertical = 24.dp)
                    .widthIn(max = 600.dp)
                    .heightIn(max = 760.dp)
                    .fillMaxSize()
                    .shadow(
                        elevation = 28.dp,
                        shape = dialogShape,
                        ambientColor = Color.Black.copy(alpha = if (appColors.isDark) 0.55f else 0.16f),
                        spotColor = Color.Black.copy(alpha = if (appColors.isDark) 0.70f else 0.24f)
                    )
                    .clip(dialogShape)
                    .border(1.dp, rimBrush, dialogShape)
            } else {
                Modifier.fillMaxSize()
            },
            color = appColors.surface
        ) {
            Column(
                modifier = if (useModalDialog) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                }
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(appColors.surface)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (!isValidating) onDismiss() },
                        enabled = !isValidating
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close sign in",
                            tint = appColors.textPrimary
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp)
                    ) {
                        Text(
                            text = "Connect YouTube Music",
                            color = appColors.textPrimary,
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = currentHostLabel,
                                color = appColors.textSecondary,
                                fontFamily = PlusJakartaSans,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            hasCapturedSession.set(false)
                            probeGeneration.incrementAndGet()
                            webViewRef?.reload()
                        },
                        enabled = !isValidating
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reload page",
                            tint = appColors.textSecondary
                        )
                    }
                }

                // Progress indicator
                if (isPageLoading || isValidating) {
                    LinearProgressIndicator(
                        progress = {
                            if (isValidating) 0.9f else pageProgress.coerceIn(0.08f, 1f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp),
                        color = accentColor,
                        trackColor = accentColor.copy(alpha = 0.16f)
                    )
                }

                // Validation error banner
                AnimatedVisibility(visible = !validationError.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(ContinuousRoundedRectangle(14.dp))
                            .background(Color(0x28FF5252))
                            .border(1.dp, Color(0x66FF5252), ContinuousRoundedRectangle(14.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = validationError.orEmpty(),
                            color = appColors.textPrimary,
                            fontFamily = PlusJakartaSans,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        val retryInteraction = remember { MutableInteractionSource() }
                        val haptic = LocalHapticFeedback.current
                        Text(
                            text = "Retry",
                            color = accentColor,
                            fontFamily = GoogleSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .pressWobble(retryInteraction, pressedScale = 0.88f)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = retryInteraction,
                                    indication = null
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    hasCapturedSession.set(false)
                                    probeGeneration.incrementAndGet()
                                    val wv = webViewRef ?: return@clickable
                                    clearCookiesAndLoadLogin(
                                        webView = wv,
                                        cookieManager = CookieManager.getInstance(),
                                        mainHandler = mainHandler,
                                        isDisposed = isDisposed
                                    )
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // WebView container
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)

                            WebView(context).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    setSupportZoom(true)
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                    userAgentString = userAgentString.replace("; wv", "")
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        pageProgress = newProgress / 100f
                                        if (newProgress >= 100) {
                                            isPageLoading = false
                                        }
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val scheme = request?.url?.scheme?.lowercase() ?: return false
                                        return scheme != "https" && scheme != "http"
                                    }

                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        isPageLoading = true
                                        val host = runCatching { Uri.parse(url).host }.getOrNull()
                                        if (!host.isNullOrBlank()) {
                                            currentHostLabel = host
                                        }
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        isPageLoading = false
                                        if (view == null || url == null || isDisposed.get()) return
                                        val host = runCatching { Uri.parse(url).host }.getOrNull()
                                        if (!host.isNullOrBlank()) {
                                            currentHostLabel = host
                                        }

                                        if (isMusicYouTubeUrl(url) && !hasCapturedSession.get()) {
                                            cookieManager.flush()
                                            val generation = probeGeneration.incrementAndGet()
                                            probeCredentialsWithRetry(
                                                webView = view,
                                                cookieManager = cookieManager,
                                                hasCapturedSession = hasCapturedSession,
                                                isDisposed = isDisposed,
                                                probeGeneration = probeGeneration,
                                                expectedGeneration = generation,
                                                attempt = 0,
                                                onCapture = { cookie, visitorData, dataSyncId ->
                                                    onCredentialsCaptured(cookie, visitorData, dataSyncId)
                                                }
                                            )
                                        }
                                    }
                                }

                                webViewRef = this
                                clearCookiesAndLoadLogin(
                                    webView = this,
                                    cookieManager = cookieManager,
                                    mainHandler = mainHandler,
                                    isDisposed = isDisposed
                                )
                            }
                        },
                        onRelease = { wv ->
                            runCatching {
                                wv.handler?.removeCallbacksAndMessages(null)
                                wv.stopLoading()
                                wv.onPause()
                                wv.webChromeClient = null
                                wv.webViewClient = WebViewClient()
                                wv.loadUrl("about:blank")
                                wv.clearHistory()
                                wv.removeAllViews()
                                (wv.parent as? ViewGroup)?.removeView(wv)
                                wv.destroy()
                            }
                            if (webViewRef === wv) {
                                webViewRef = null
                            }
                        }
                    )

                    if (isValidating) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(appColors.surface.copy(alpha = 0.85f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier
                                    .clip(ContinuousRoundedRectangle(24.dp))
                                    .background(appColors.surfaceVariant)
                                    .border(1.dp, appColors.cardBorder, ContinuousRoundedRectangle(24.dp))
                                    .padding(horizontal = 28.dp, vertical = 24.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = accentColor,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "Syncing your YouTube Music profile…",
                                    color = appColors.textPrimary,
                                    fontFamily = GoogleSans,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun isMusicYouTubeUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
    return uri.scheme.equals("https", ignoreCase = true) &&
        uri.host.equals("music.youtube.com", ignoreCase = true)
}

private fun clearCookiesAndLoadLogin(
    webView: WebView,
    cookieManager: CookieManager,
    mainHandler: Handler,
    isDisposed: AtomicBoolean
) {
    // Wait for Chromium's async cookie wipe before starting ServiceLogin to avoid wiping newly issued auth cookies.
    cookieManager.removeAllCookies {
        cookieManager.flush()
        mainHandler.post {
            if (!isDisposed.get()) {
                webView.loadUrl(YTM_SERVICE_LOGIN_URL)
            }
        }
    }
}

private fun readCombinedYtmCookie(cookieManager: CookieManager): String {
    val ytmCookie = cookieManager.getCookie(YTM_ORIGIN_URL).orEmpty()
    val ytWwwCookie = cookieManager.getCookie(YT_WWW_URL).orEmpty()
    val ytRootCookie = cookieManager.getCookie(YT_ROOT_URL).orEmpty()
    return YouTubeAuthManager.mergeCookieStrings(ytmCookie, ytWwwCookie, ytRootCookie)
}

private fun probeCredentialsWithRetry(
    webView: WebView,
    cookieManager: CookieManager,
    hasCapturedSession: AtomicBoolean,
    isDisposed: AtomicBoolean,
    probeGeneration: AtomicInteger,
    expectedGeneration: Int,
    attempt: Int,
    onCapture: (cookie: String, visitorData: String, dataSyncId: String?) -> Unit
) {
    if (isDisposed.get() || hasCapturedSession.get() || probeGeneration.get() != expectedGeneration) return

    cookieManager.flush()
    val cookie = readCombinedYtmCookie(cookieManager)
    if (!YouTubeAuthManager.hasYouTubeAccountSession(cookie)) {
        if (attempt < MAX_CREDENTIAL_PROBE_ATTEMPTS) {
            scheduleNextCredentialProbe(
                webView = webView,
                cookieManager = cookieManager,
                hasCapturedSession = hasCapturedSession,
                isDisposed = isDisposed,
                probeGeneration = probeGeneration,
                expectedGeneration = expectedGeneration,
                nextAttempt = attempt + 1,
                onCapture = onCapture
            )
        }
        return
    }

    val js = """
        (function() {
            try {
                var cfg = window.ytcfg;
                var cfgData = (cfg && cfg.data_) ? cfg.data_ : {};
                var ytc = (window.yt && window.yt.config_) ? window.yt.config_ : {};
                var vd = ytc.VISITOR_DATA || (cfg && cfg.get ? cfg.get('VISITOR_DATA') : '') || cfgData.VISITOR_DATA || '';
                var ds = ytc.DATASYNC_ID || (cfg && cfg.get ? cfg.get('DATASYNC_ID') : '') || cfgData.DATASYNC_ID || '';
                var loggedIn = (ytc.LOGGED_IN !== undefined)
                    ? ytc.LOGGED_IN
                    : ((cfg && cfg.get && cfg.get('LOGGED_IN') !== undefined) ? cfg.get('LOGGED_IN') : cfgData.LOGGED_IN);
                return {
                    status: loggedIn === false ? 'logged_out' : 'ok',
                    vd: String(vd || ''),
                    ds: String(ds || ''),
                    loggedIn: loggedIn === true
                };
            } catch (e) {
                return { status: 'error', vd: '', ds: '', loggedIn: false };
            }
        })();
    """.trimIndent()

    webView.evaluateJavascript(js) { rawResult ->
        if (isDisposed.get() || hasCapturedSession.get() || probeGeneration.get() != expectedGeneration) return@evaluateJavascript

        val parsed = runCatching {
            val cleaned = rawResult?.takeIf { it.isNotBlank() && it != "null" } ?: return@runCatching null
            val jsonText = if (cleaned.startsWith("\"")) {
                JSONObject("{\"v\":$cleaned}").optString("v")
            } else {
                cleaned
            }
            JSONObject(jsonText)
        }.getOrNull()

        val status = parsed?.optString("status").orEmpty()
        val visitorData = YouTubeAuthManager.decodePercentEncoded(parsed?.optString("vd")).orEmpty()
        val dataSyncId = YouTubeAuthManager.decodePercentEncoded(parsed?.optString("ds"))
        val loggedIn = parsed?.optBoolean("loggedIn", false) == true

        val isReady = status != "logged_out" && (
            (visitorData.isNotBlank() && (!dataSyncId.isNullOrBlank() || loggedIn || attempt >= 4)) ||
                attempt >= MAX_CREDENTIAL_PROBE_ATTEMPTS
            )

        if (isReady) {
            cookieManager.flush()
            val latestCookie = readCombinedYtmCookie(cookieManager)
            if (YouTubeAuthManager.hasYouTubeAccountSession(latestCookie) &&
                hasCapturedSession.compareAndSet(false, true)
            ) {
                onCapture(latestCookie, visitorData, dataSyncId)
                return@evaluateJavascript
            }
        }

        if (attempt < MAX_CREDENTIAL_PROBE_ATTEMPTS) {
            scheduleNextCredentialProbe(
                webView = webView,
                cookieManager = cookieManager,
                hasCapturedSession = hasCapturedSession,
                isDisposed = isDisposed,
                probeGeneration = probeGeneration,
                expectedGeneration = expectedGeneration,
                nextAttempt = attempt + 1,
                onCapture = onCapture
            )
        }
    }
}

private fun scheduleNextCredentialProbe(
    webView: WebView,
    cookieManager: CookieManager,
    hasCapturedSession: AtomicBoolean,
    isDisposed: AtomicBoolean,
    probeGeneration: AtomicInteger,
    expectedGeneration: Int,
    nextAttempt: Int,
    onCapture: (cookie: String, visitorData: String, dataSyncId: String?) -> Unit
) {
    webView.postDelayed(
        {
            probeCredentialsWithRetry(
                webView = webView,
                cookieManager = cookieManager,
                hasCapturedSession = hasCapturedSession,
                isDisposed = isDisposed,
                probeGeneration = probeGeneration,
                expectedGeneration = expectedGeneration,
                attempt = nextAttempt,
                onCapture = onCapture
            )
        },
        CREDENTIAL_PROBE_DELAY_MS
    )
}
