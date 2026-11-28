package com.xperience.player.service

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale

/**
 * Pure JVM-testable security policy helpers for Google/YouTube authentication URLs,
 * InnerTube API host allowlisting, YouTube media stream host validation, and
 * OkHttp network-level auth header / cookie scoping across redirects.
 */
object YouTubeSecurityPolicy {

    private val ALLOWED_GOOGLE_AUTH_DOMAINS = setOf(
        "google.com",
        "youtube.com",
        "gstatic.com",
        "googleusercontent.com",
        "googleapis.com"
    )

    private val ALLOWED_YOUTUBE_API_HOSTS = setOf(
        "music.youtube.com",
        "www.youtube.com",
        "m.youtube.com",
        "youtube.com",
        "youtubei.googleapis.com"
    )

    private val SENSITIVE_API_HEADERS = listOf(
        "Authorization",
        "X-Goog-PageId",
        "X-Goog-AuthUser",
        "X-Youtube-Bootstrap-Logged-In"
    )

    private fun normalizeHost(host: String): String {
        return host.trim().lowercase(Locale.ROOT).trimEnd('.')
    }

    private fun matchesExactOrSubdomain(host: String, domain: String): Boolean {
        if (host.isEmpty()) return false
        return host == domain || (host.endsWith(".$domain") && host.length > domain.length + 1)
    }

    /**
     * Allows ONLY `https://` URLs on Google/YouTube authentication domains
     * (exact domain or strict subdomain match, never substring match).
     */
    fun isAllowedGoogleAuthUrl(url: String): Boolean {
        val parsed = url.trim().toHttpUrlOrNull() ?: return false
        if (!parsed.isHttps) return false
        if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) return false
        val host = normalizeHost(parsed.host)
        if (host.isEmpty()) return false
        return ALLOWED_GOOGLE_AUTH_DOMAINS.any { domain ->
            matchesExactOrSubdomain(host, domain)
        }
    }

    /**
     * Returns true only for trusted YouTube / InnerTube API hosts that may receive
     * Google account authorization headers (`Authorization`, `X-Goog-PageId`, etc.).
     */
    fun isAllowedYouTubeApiHost(host: String): Boolean {
        val normalized = normalizeHost(host)
        return normalized in ALLOWED_YOUTUBE_API_HOSTS
    }

    /**
     * Returns true only for `*.googlevideo.com`, `*.youtube.com`, or `youtube.com` hosts
     * permitted to serve YouTube media streams and receive scoped stream cookies.
     */
    fun isAllowedYouTubeStreamHost(host: String): Boolean {
        val normalized = normalizeHost(host)
        if (normalized.isEmpty()) return false
        return matchesExactOrSubdomain(normalized, "youtube.com") ||
            (normalized.endsWith(".googlevideo.com") && normalized.length > ".googlevideo.com".length)
    }

    /**
     * Verifies that [url] is a well-formed `https://` URL targeting an allowlisted
     * YouTube media stream host.
     */
    fun isSafeHttpsYouTubeStreamUrl(url: String): Boolean {
        val parsed = url.trim().toHttpUrlOrNull() ?: return false
        if (!parsed.isHttps) return false
        if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) return false
        return isAllowedYouTubeStreamHost(parsed.host)
    }

    /**
     * Strips sensitive Google/YouTube auth headers from any outgoing request whose
     * destination is not HTTPS or not on an allowlisted host (including redirect hops).
     */
    fun stripDisallowedAuthHeaders(request: Request): Request {
        val url = request.url
        val isHttps = url.isHttps
        val host = url.host
        val allowApiHeaders = isHttps && isAllowedYouTubeApiHost(host)
        val allowCookie = isHttps && (isAllowedYouTubeApiHost(host) || isAllowedYouTubeStreamHost(host))

        if (allowApiHeaders && allowCookie) {
            return request
        }

        val builder = request.newBuilder()
        if (!allowApiHeaders) {
            for (header in SENSITIVE_API_HEADERS) {
                builder.removeHeader(header)
            }
        }
        if (!allowCookie) {
            builder.removeHeader("Cookie")
        }
        return builder.build()
    }

    /**
     * Network interceptor that enforces [stripDisallowedAuthHeaders] on every wire request,
     * including HTTP redirect hops.
     */
    fun authScopingNetworkInterceptor(): Interceptor = Interceptor { chain ->
        chain.proceed(stripDisallowedAuthHeaders(chain.request()))
    }

    /**
     * Derives a dedicated YouTube [OkHttpClient] from [baseClient] with SSL downgrade redirects
     * disabled and [authScopingNetworkInterceptor] attached.
     */
    fun createScopedYouTubeHttpClient(
        baseClient: OkHttpClient = MediaNetworkClients.sharedOkHttpClient
    ): OkHttpClient {
        return baseClient.newBuilder()
            .followSslRedirects(false)
            .addNetworkInterceptor(authScopingNetworkInterceptor())
            .build()
    }
}
