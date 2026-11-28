package com.xperience.player.service

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Resolves YouTube player script metadata (`signatureTimestamp`) and transforms stream URLs
 * (deciphering `signatureCipher` + solving the `n` throttling challenge).
 */
interface PlayerScriptResolver {
    suspend fun getSignatureTimestamp(): Int?

    /**
     * Deciphers [signature] (placed at query param [sp]) if non-blank and transforms the `n`
     * query parameter for [url]. Returns `null` when [signature] was required and could not be deciphered.
     */
    suspend fun transformStreamUrl(url: String, sp: String = "", signature: String = ""): String?
}

object NoOpPlayerScriptResolver : PlayerScriptResolver {
    override suspend fun getSignatureTimestamp(): Int? = null

    override suspend fun transformStreamUrl(url: String, sp: String, signature: String): String? {
        return if (signature.isBlank()) url else null
    }
}

object YouTubePlayerScriptParser {
    private val PLAYER_VERSION_REGEX = Regex("""player\\?/([A-Za-z0-9_-]{6,32})\\?/""")
    private val STS_REGEX = Regex("""signatureTimestamp\s*:\s*(\d+)""")
    private val PRIMARY_SIG_FN_REGEX = Regex(
        """\b([A-Za-z0-9_$]+)=function\([A-Za-z0-9_$,]+\)\{[^}]{0,120}=new\s+[A-Za-z0-9_$.]+\(a,!0\);[A-Za-z0-9_$]+\.set\(["']alr["'],["']yes["']\);[^}]{0,240}\}"""
    )
    private val FALLBACK_SIG_FN_REGEX = Regex(
        """\b([A-Za-z0-9_$]+)=function\(a,b,c\)\{b=b===void\s+0\?["']{2}:b;[^}]{0,260}["']alr["'],["']yes["'][^}]{0,260}\}"""
    )
    private val FUNCTION_NAME_REGEX = Regex("""^[A-Za-z0-9_$]+$""")

    fun extractPlayerVersionFromIframeApi(iframeBody: String): String? {
        return PLAYER_VERSION_REGEX.find(iframeBody)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
    }

    fun extractSignatureTimestamp(baseJs: String): Int? {
        return STS_REGEX.find(baseJs)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    fun extractSigDecipherFunctionName(baseJs: String): String? {
        val name = PRIMARY_SIG_FN_REGEX.find(baseJs)?.groupValues?.getOrNull(1)
            ?: FALLBACK_SIG_FN_REGEX.find(baseJs)?.groupValues?.getOrNull(1)
            ?: return null
        return name.takeIf { FUNCTION_NAME_REGEX.matches(it) }
    }

    fun buildHookedPlayerScript(baseJs: String, sigFnName: String): String? {
        if (!FUNCTION_NAME_REGEX.matches(sigFnName)) return null
        val semiIdx = baseJs.lastIndexOf("})(_yt_player);")
        val endIdx = if (semiIdx >= 0) semiIdx else baseJs.lastIndexOf("})(_yt_player)")
        if (endIdx < 0) return null

        val hook = buildString {
            append(";g.__xt_transform_url=function(u,sp,s){")
            append("var o=").append(sigFnName).append("(u,sp||\"\",s||\"\");")
            append("for(var k in o){")
            append("if(typeof o[k]==='function'){")
            append("try{var v=o[k]();if(typeof v==='string'&&v.startsWith('http'))return v;}catch(e){}")
            append("}")
            append("}")
            append("return String(o);")
            append("};")
        }
        return baseJs.substring(0, endIdx) + hook + baseJs.substring(endIdx)
    }
}

/**
 * Android Chromium WebView-backed [PlayerScriptResolver] that executes YouTube's `base.js`
 * once per player version to compute `signatureTimestamp`, `sig` deciphering, and `n` parameters.
 */
class AndroidWebViewPlayerScriptSolver(
    context: Context,
    private val httpClient: OkHttpClient,
    private val cacheDir: File? = File((context.applicationContext ?: context).cacheDir, "player_js_cache"),
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) : PlayerScriptResolver {

    private val appContext: Context = context.applicationContext ?: context
    private val loadMutex = Mutex()
    private val evalMutex = Mutex()
    private val idleScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    @Volatile private var loadedVersion: String? = null
    @Volatile private var cachedSts: Int? = null
    @Volatile private var lastVersionCheckMs: Long = 0L
    @Volatile private var hookedJsBytes: ByteArray? = null
    @Volatile private var webView: WebView? = null
    @Volatile private var isJsReady: Boolean = false
    @Volatile private var idleEvictionJob: Job? = null

    companion object {
        private const val TAG = "YtmScriptSolver"
        private const val IFRAME_API_URL = "https://www.youtube.com/iframe_api"
        private const val ORIGIN_URL = "https://music.youtube.com/"
        private const val VIRTUAL_SCRIPT_URL = "https://music.youtube.com/__xt_base.js"
        private const val PLAYER_VERSION_TTL_MS = 6L * 60L * 60L * 1000L
        private const val LOAD_TIMEOUT_MS = 10_000L
        private const val EVAL_TIMEOUT_MS = 3_000L
        private const val IDLE_EVICTION_MS = 3L * 60L * 1000L
    }

    override suspend fun getSignatureTimestamp(): Int? {
        ensurePlayerMetadataLoaded(requireHookedBytes = false)
        return cachedSts
    }

    override suspend fun transformStreamUrl(url: String, sp: String, signature: String): String? {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return null

        val ready = ensureWebViewReady()
        if (!ready) {
            return if (signature.isBlank()) cleanUrl else null
        }

        scheduleIdleEviction()
        return evalMutex.withLock {
            withTimeoutOrNull(EVAL_TIMEOUT_MS) {
                withContext(mainDispatcher) {
                    val wv = webView ?: return@withContext null
                    val expr = "typeof _yt_player==='object'&&typeof _yt_player.__xt_transform_url==='function'" +
                        "?_yt_player.__xt_transform_url(${JSONObject.quote(cleanUrl)},${JSONObject.quote(sp)},${JSONObject.quote(signature)})" +
                        ":null"
                    val resultDeferred = CompletableDeferred<String?>()
                    wv.evaluateJavascript(expr) { raw ->
                        val parsed = decodeJsStringResult(raw)
                        resultDeferred.complete(parsed)
                    }
                    resultDeferred.await()
                }
            }?.takeIf { YouTubeSecurityPolicy.isSafeHttpsYouTubeStreamUrl(it) }
                ?: if (signature.isBlank() && YouTubeSecurityPolicy.isSafeHttpsYouTubeStreamUrl(cleanUrl)) cleanUrl else null
        }
    }

    private fun scheduleIdleEviction() {
        idleEvictionJob?.cancel()
        idleEvictionJob = idleScope.launch {
            delay(IDLE_EVICTION_MS)
            evictWebView()
        }
    }

    private suspend fun evictWebView() {
        loadMutex.withLock {
            evalMutex.withLock {
                destroyWebViewOnMain()
                isJsReady = false
                hookedJsBytes = null
                Log.d(TAG, "Evicted idle headless player script WebView")
            }
        }
    }

    private suspend fun destroyWebViewOnMain() {
        withContext(mainDispatcher) {
            runCatching {
                webView?.stopLoading()
                webView?.loadUrl("about:blank")
                webView?.destroy()
            }
            webView = null
        }
    }

    private suspend fun ensurePlayerMetadataLoaded(requireHookedBytes: Boolean): Boolean = withContext(ioDispatcher) {
        val now = clockMs()
        val hasMetadata = cachedSts != null && now - lastVersionCheckMs < PLAYER_VERSION_TTL_MS
        if (hasMetadata && (!requireHookedBytes || hookedJsBytes != null || (isJsReady && webView != null))) {
            return@withContext true
        }
        loadMutex.withLock {
            val currentNow = clockMs()
            val lockedHasMetadata = cachedSts != null && currentNow - lastVersionCheckMs < PLAYER_VERSION_TTL_MS
            if (lockedHasMetadata && (!requireHookedBytes || hookedJsBytes != null || (isJsReady && webView != null))) {
                return@withContext true
            }
            val playerVer = if (lockedHasMetadata && loadedVersion != null) {
                loadedVersion!!
            } else {
                fetchPlayerVersion() ?: loadedVersion ?: return@withContext hookedJsBytes != null
            }
            if (playerVer == loadedVersion && cachedSts != null && (!requireHookedBytes || hookedJsBytes != null || (isJsReady && webView != null))) {
                lastVersionCheckMs = currentNow
                return@withContext true
            }

            val baseJs = loadOrDownloadBaseJs(playerVer) ?: return@withContext false
            val sts = YouTubePlayerScriptParser.extractSignatureTimestamp(baseJs)
            val sigFnName = YouTubePlayerScriptParser.extractSigDecipherFunctionName(baseJs)
            if (sigFnName == null) {
                Log.w(TAG, "Unable to locate sig/n decipher function in base.js ($playerVer)")
                return@withContext false
            }
            val hooked = YouTubePlayerScriptParser.buildHookedPlayerScript(baseJs, sigFnName)
            if (hooked == null) {
                Log.w(TAG, "Unable to inject wrapper into base.js ($playerVer)")
                return@withContext false
            }

            val versionChanged = playerVer != loadedVersion
            hookedJsBytes = hooked.toByteArray(Charsets.UTF_8)
            cachedSts = sts
            loadedVersion = playerVer
            lastVersionCheckMs = currentNow
            if (versionChanged) {
                isJsReady = false
            }
            Log.d(TAG, "Loaded YTM player script $playerVer (sts=$sts, fn=$sigFnName)")
            true
        }
    }

    private suspend fun ensureWebViewReady(): Boolean {
        if (isJsReady && webView != null) return true
        if (!ensurePlayerMetadataLoaded(requireHookedBytes = true)) return false
        if (isJsReady && webView != null) return true

        return loadMutex.withLock {
            if (isJsReady && webView != null) return@withLock true
            val jsBytes = hookedJsBytes ?: return@withLock false
            val loaded = withTimeoutOrNull(LOAD_TIMEOUT_MS) {
                loadScriptInWebView(jsBytes)
            }
            if (loaded == true) {
                isJsReady = true
                hookedJsBytes = null
                true
            } else {
                isJsReady = false
                destroyWebViewOnMain()
                false
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun loadScriptInWebView(jsBytes: ByteArray): Boolean {
        val loadDeferred = CompletableDeferred<Boolean>()
        withContext(mainDispatcher) {
            runCatching {
                webView?.destroy()
                val wv = WebView(appContext)
                wv.settings.javaScriptEnabled = true
                wv.settings.domStorageEnabled = false
                wv.settings.databaseEnabled = false
                wv.settings.allowFileAccess = false
                wv.settings.allowContentAccess = false
                wv.settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                wv.settings.loadsImagesAutomatically = false
                wv.settings.blockNetworkImage = true
                runCatching { CookieManager.getInstance().setAcceptThirdPartyCookies(wv, false) }
                wv.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean = true

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse {
                        val reqUrl = request?.url?.toString().orEmpty()
                        return if (reqUrl == VIRTUAL_SCRIPT_URL) {
                            WebResourceResponse(
                                "application/javascript",
                                "utf-8",
                                ByteArrayInputStream(jsBytes)
                            )
                        } else {
                            WebResourceResponse(
                                "text/html",
                                "utf-8",
                                ByteArrayInputStream(ByteArray(0))
                            )
                        }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (loadDeferred.isCompleted) return
                        view?.evaluateJavascript(
                            "typeof _yt_player==='object'&&typeof _yt_player.__xt_transform_url==='function'"
                        ) { value ->
                            val ok = value?.trim()?.equals("true", ignoreCase = true) == true
                            if (!loadDeferred.isCompleted) {
                                loadDeferred.complete(ok)
                            }
                        }
                    }
                }
                webView = wv
                val html = "<!doctype html><html><head><script src=\"$VIRTUAL_SCRIPT_URL\"></script></head><body></body></html>"
                wv.loadDataWithBaseURL(ORIGIN_URL, html, "text/html", "utf-8", null)
            }.onFailure { err ->
                Log.w(TAG, "Failed initializing WebView script runtime", err)
                if (!loadDeferred.isCompleted) loadDeferred.complete(false)
            }
        }
        return loadDeferred.await()
    }

    private fun fetchPlayerVersion(): String? {
        return runCatching {
            val req = Request.Builder()
                .url(IFRAME_API_URL)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string().orEmpty()
                YouTubePlayerScriptParser.extractPlayerVersionFromIframeApi(body)
            }
        }.getOrNull()
    }

    private fun loadOrDownloadBaseJs(playerVer: String): String? {
        val dir = cacheDir?.apply { mkdirs() }
        val cacheFile = dir?.let { File(it, "player_${playerVer}.js") }
        if (cacheFile != null && cacheFile.exists() && cacheFile.length() > 100_000L) {
            val cached = runCatching { cacheFile.readText(Charsets.UTF_8) }.getOrNull()
            if (!cached.isNullOrBlank()) return cached
        }

        val url = "https://www.youtube.com/s/player/$playerVer/player_ias.vflset/en_US/base.js"
        val body = runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.string()
            }
        }.getOrNull() ?: return null

        if (cacheFile != null && body.length > 100_000) {
            runCatching {
                dir.listFiles()?.forEach { existing ->
                    if (existing.name.startsWith("player_") && existing.name.endsWith(".js") && existing.name != cacheFile.name) {
                        existing.delete()
                    }
                }
                cacheFile.writeText(body, Charsets.UTF_8)
            }
        }
        return body
    }

    private fun decodeJsStringResult(raw: String?): String? {
        if (raw.isNullOrBlank() || raw == "null" || raw == "undefined") return null
        return runCatching {
            JSONObject("{\"v\":$raw}").optString("v").takeIf { it.isNotBlank() && it != "null" }
        }.getOrNull()
    }
}
