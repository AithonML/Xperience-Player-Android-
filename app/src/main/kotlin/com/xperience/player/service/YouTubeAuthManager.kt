package com.xperience.player.service

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import com.xperience.player.domain.model.YtmAccountState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface SecretCipher {
    fun encrypt(plaintext: String): String
    fun decrypt(ciphertext: String): String?
}

interface AuthPrefsStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
    fun getLong(key: String, defaultValue: Long): Long
    fun putLong(key: String, value: Long)
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun remove(key: String)
}

class SharedPreferencesAuthStore(
    private val prefs: SharedPreferences
) : AuthPrefsStore {
    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String?) {
        prefs.edit().apply {
            if (value == null) remove(key) else putString(key, value)
        }.apply()
    }

    override fun getLong(key: String, defaultValue: Long): Long = prefs.getLong(key, defaultValue)

    override fun putLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = prefs.getBoolean(key, defaultValue)

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}

class InMemoryAuthStore : AuthPrefsStore {
    private val values = mutableMapOf<String, Any>()

    @Synchronized
    override fun getString(key: String): String? = values[key] as? String

    @Synchronized
    override fun putString(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }

    @Synchronized
    override fun getLong(key: String, defaultValue: Long): Long = (values[key] as? Long) ?: defaultValue

    @Synchronized
    override fun putLong(key: String, value: Long) {
        values[key] = value
    }

    @Synchronized
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = (values[key] as? Boolean) ?: defaultValue

    @Synchronized
    override fun putBoolean(key: String, value: Boolean) {
        values[key] = value
    }

    @Synchronized
    override fun remove(key: String) {
        values.remove(key)
    }
}

class AndroidKeystoreSecretCipher(
    private val keyAlias: String = KEY_ALIAS
) : SecretCipher {

    override fun encrypt(plaintext: String): String {
        return try {
            encryptWithKey(plaintext, getOrCreateKey())
        } catch (_: Exception) {
            // Regenerate corrupted/invalidated keystore entry and retry once.
            runCatching { deleteKey() }
            encryptWithKey(plaintext, getOrCreateKey())
        }
    }

    private fun encryptWithKey(plaintext: String, secretKey: SecretKey): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val ivB64 = Base64.getEncoder().encodeToString(iv)
        val dataB64 = Base64.getEncoder().encodeToString(encrypted)
        return "$FORMAT_PREFIX$ivB64:$dataB64"
    }

    override fun decrypt(ciphertext: String): String? {
        if (!ciphertext.startsWith(FORMAT_PREFIX)) return null
        return try {
            val payload = ciphertext.removePrefix(FORMAT_PREFIX)
            val splitIndex = payload.indexOf(':')
            if (splitIndex <= 0) return null
            val iv = Base64.getDecoder().decode(payload.substring(0, splitIndex))
            val encrypted = Base64.getDecoder().decode(payload.substring(splitIndex + 1))
            val secretKey = getExistingKey() ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun getExistingKey(): SecretKey? {
        return runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val entry = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
            entry?.secretKey
        }.getOrNull()
    }

    private fun deleteKey() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(keyAlias)) {
            keyStore.deleteEntry(keyAlias)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        getExistingKey()?.let { return it }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "xperience_ytm_auth_aes_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val FORMAT_PREFIX = "v1:"
        private const val GCM_TAG_BITS = 128
    }
}

data class YtmAuthSession(
    val cookie: String,
    val visitorData: String,
    val dataSyncId: String? = null,
    val accountName: String? = null,
    val accountHandle: String? = null,
    val avatarUrl: String? = null,
    val lastSyncedAtMs: Long = 0L
)

class YouTubeAuthManager(
    private val store: AuthPrefsStore,
    private val cipher: SecretCipher,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {

    constructor(
        context: Context,
        cipher: SecretCipher = AndroidKeystoreSecretCipher(),
        clockMs: () -> Long = { System.currentTimeMillis() }
    ) : this(
        store = SharedPreferencesAuthStore(
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        ),
        cipher = cipher,
        clockMs = clockMs
    )

    private val lock = Any()
    @Volatile
    private var currentSession: YtmAuthSession? = null

    private val _accountState = MutableStateFlow(YtmAccountState())
    val accountState: StateFlow<YtmAccountState> = _accountState.asStateFlow()

    private val _shouldShowLoginPrompt = MutableStateFlow(false)
    val shouldShowLoginPrompt: StateFlow<Boolean> = _shouldShowLoginPrompt.asStateFlow()

    init {
        loadSessionFromStore()
        refreshPromptState()
    }

    private fun safeLog(message: String) {
        runCatching { Log.d(TAG, message) }
    }

    private fun loadSessionFromStore() {
        val encrypted = store.getString(KEY_ENCRYPTED_SESSION)
        if (encrypted.isNullOrBlank()) {
            currentSession = null
            _accountState.value = YtmAccountState()
            return
        }
        val decrypted = cipher.decrypt(encrypted)
        if (decrypted.isNullOrBlank()) {
            currentSession = null
            store.remove(KEY_ENCRYPTED_SESSION)
            _accountState.value = YtmAccountState()
            return
        }
        val parsed = deserializeSession(decrypted)
        if (parsed == null || parsed.cookie.isBlank() || !hasYouTubeAccountSession(parsed.cookie)) {
            currentSession = null
            store.remove(KEY_ENCRYPTED_SESSION)
            _accountState.value = YtmAccountState()
            return
        }
        currentSession = parsed
        _accountState.value = parsed.toAccountState()
    }

    private fun persistSession(session: YtmAuthSession) {
        val json = serializeSession(session)
        val encrypted = cipher.encrypt(json)
        store.putString(KEY_ENCRYPTED_SESSION, encrypted)
    }

    fun saveSession(
        cookie: String,
        visitorData: String,
        dataSyncId: String? = null,
        accountName: String? = null,
        accountHandle: String? = null,
        avatarUrl: String? = null,
        syncedAtMs: Long = clockMs()
    ) {
        val cleanCookie = mergeCookieStrings(cookie)
        val cleanVisitorData = decodePercentEncoded(visitorData).orEmpty()
        val cleanDataSyncId = decodePercentEncoded(dataSyncId)
        require(cleanCookie.isNotBlank() && hasYouTubeAccountSession(cleanCookie)) {
            "Invalid YouTube Music session cookie"
        }

        synchronized(lock) {
            val existing = currentSession
            val isSameAccount = existing != null && isSameAccountSession(existing.cookie, cleanCookie)
            val updated = YtmAuthSession(
                cookie = cleanCookie,
                visitorData = cleanVisitorData,
                dataSyncId = cleanDataSyncId ?: if (isSameAccount) existing.dataSyncId else null,
                accountName = accountName?.trim()?.takeIf { it.isNotBlank() }
                    ?: if (isSameAccount) existing.accountName else null,
                accountHandle = accountHandle?.trim()?.takeIf { it.isNotBlank() }
                    ?: if (isSameAccount) existing.accountHandle else null,
                avatarUrl = avatarUrl?.trim()?.takeIf { it.isNotBlank() }
                    ?: if (isSameAccount) existing.avatarUrl else null,
                lastSyncedAtMs = syncedAtMs
            )
            persistSession(updated)
            currentSession = updated
            _accountState.value = updated.toAccountState()
            _shouldShowLoginPrompt.value = false
            safeLog("Saved encrypted YTM session (hasVisitorData=${cleanVisitorData.isNotBlank()}, hasDataSyncId=${updated.dataSyncId != null})")
        }
    }

    fun updateAccountInfo(
        accountName: String?,
        accountHandle: String?,
        avatarUrl: String?,
        syncedAtMs: Long = clockMs(),
        dataSyncId: String? = null
    ) {
        synchronized(lock) {
            val existing = currentSession ?: return
            val cleanDataSyncId = decodePercentEncoded(dataSyncId)
            val updated = existing.copy(
                dataSyncId = cleanDataSyncId ?: existing.dataSyncId,
                accountName = accountName?.trim()?.takeIf { it.isNotBlank() } ?: existing.accountName,
                accountHandle = accountHandle?.trim()?.takeIf { it.isNotBlank() } ?: existing.accountHandle,
                avatarUrl = avatarUrl?.trim()?.takeIf { it.isNotBlank() } ?: existing.avatarUrl,
                lastSyncedAtMs = syncedAtMs
            )
            persistSession(updated)
            currentSession = updated
            _accountState.value = updated.toAccountState()
        }
    }

    fun clearSession() {
        synchronized(lock) {
            currentSession = null
            store.remove(KEY_ENCRYPTED_SESSION)
            _accountState.value = YtmAccountState(isSignedIn = false)
            refreshPromptState()
            safeLog("Cleared encrypted YTM session")
        }
    }

    fun dismissPromptTemporarily(durationMs: Long = DEFAULT_REMIND_LATER_MS) {
        synchronized(lock) {
            val safeDuration = durationMs.coerceAtLeast(0L)
            store.putLong(KEY_REMIND_LATER_UNTIL_MS, clockMs() + safeDuration)
            refreshPromptState()
        }
    }

    fun dismissPromptPermanently() {
        synchronized(lock) {
            store.putBoolean(KEY_PERMANENTLY_DISMISSED, true)
            refreshPromptState()
        }
    }

    fun shouldShowLoginPrompt(): Boolean {
        if (isSignedIn()) return false
        if (store.getBoolean(KEY_PERMANENTLY_DISMISSED, false)) return false
        val remindLaterUntil = store.getLong(KEY_REMIND_LATER_UNTIL_MS, 0L)
        return clockMs() >= remindLaterUntil
    }

    fun refreshPromptState() {
        _shouldShowLoginPrompt.value = shouldShowLoginPrompt()
    }

    fun isSignedIn(): Boolean {
        val session = currentSession ?: return false
        return session.cookie.isNotBlank() && hasYouTubeAccountSession(session.cookie)
    }

    fun getCookie(): String? = currentSession?.cookie?.takeIf { it.isNotBlank() }

    fun getVisitorData(): String? = currentSession?.visitorData?.takeIf { it.isNotBlank() }

    fun getRawDataSyncId(): String? = currentSession?.dataSyncId?.takeIf { it.isNotBlank() && it != "null" }

    fun getOnBehalfOfUser(): String? = getRawDataSyncId()
        ?.substringBefore("||")
        ?.trim()
        ?.takeIf { it.isNotBlank() && it != "null" }

    fun getPageId(): String? = getOnBehalfOfUser()

    fun getUserSessionId(): String? {
        val raw = getRawDataSyncId() ?: return null
        if (!raw.contains("||")) return null
        return raw.substringAfter("||").trim().takeIf { it.isNotBlank() && it != "null" }
    }

    /**
     * Only send X-Goog-PageId when DATASYNC_ID identifies a delegated Brand Account
     * (pageId differs from the underlying Gaia user session id). Personal accounts
     * carry `<gaiaId>||<gaiaId>` or a single id; sending X-Goog-PageId with a personal
     * user id causes Google API endpoints to reject the request with 400/403.
     */
    fun getDelegatedPageId(): String? {
        val pageId = getPageId() ?: return null
        val userSessionId = getUserSessionId() ?: return null
        if (userSessionId == "0" || pageId == userSessionId) return null
        return pageId
    }

    fun buildAuthHeaders(
        origin: String = YOUTUBE_MUSIC_ORIGIN,
        timestampSeconds: Long = clockMs() / 1000L,
        includePageId: Boolean = true
    ): Map<String, String> {
        val session = currentSession ?: return emptyMap()
        if (!hasYouTubeAccountSession(session.cookie)) return emptyMap()

        val headers = linkedMapOf<String, String>()
        headers["Cookie"] = session.cookie
        headers["X-Goog-AuthUser"] = "0"
        headers["X-Origin"] = origin
        headers["Referer"] = "$origin/"

        session.visitorData.takeIf { it.isNotBlank() }?.let {
            headers["X-Goog-Visitor-Id"] = it
        }
        if (includePageId) {
            getDelegatedPageId()?.let { pageId ->
                headers["X-Goog-PageId"] = pageId
            }
        }
        computeAuthorizationHeader(
            cookie = session.cookie,
            timestampSeconds = timestampSeconds,
            origin = origin,
            dataSyncId = session.dataSyncId
        )?.let { auth ->
            headers["Authorization"] = auth
        }
        return headers
    }

    /**
     * Builds authentication headers tailored for YouTube / YouTube Music `/player` requests,
     * ensuring a consent `SOCS` cookie and fallback `visitorData` are included when absent
     * without altering [buildAuthHeaders] output for browse/account endpoints.
     */
    fun buildPlayerAuthHeaders(
        origin: String = YOUTUBE_WWW_ORIGIN,
        timestampSeconds: Long = clockMs() / 1000L,
        includePageId: Boolean = false,
        fallbackVisitorData: String? = null,
        ensureConsentCookie: Boolean = true
    ): Map<String, String> {
        val base = buildAuthHeaders(
            origin = origin,
            timestampSeconds = timestampSeconds,
            includePageId = includePageId
        )
        if (base.isEmpty()) return emptyMap()
        val headers = LinkedHashMap(base)
        if (ensureConsentCookie) {
            val rawCookie = headers["Cookie"].orEmpty()
            val parsed = parseCookieString(rawCookie)
            if (parsed["SOCS"].isNullOrBlank()) {
                headers["Cookie"] = if (rawCookie.isBlank()) DEFAULT_SOCS_COOKIE else "$rawCookie; $DEFAULT_SOCS_COOKIE"
            }
        }
        if (headers["X-Goog-Visitor-Id"].isNullOrBlank() && !fallbackVisitorData.isNullOrBlank()) {
            headers["X-Goog-Visitor-Id"] = fallbackVisitorData
        }
        return headers
    }

    fun getCookieWithConsent(): String? {
        val raw = getCookie() ?: return null
        val parsed = parseCookieString(raw)
        return if (parsed["SOCS"].isNullOrBlank()) "$raw; $DEFAULT_SOCS_COOKIE" else raw
    }

    private fun YtmAuthSession.toAccountState(): YtmAccountState = YtmAccountState(
        isSignedIn = hasYouTubeAccountSession(cookie),
        accountName = accountName,
        accountHandle = accountHandle,
        avatarUrl = avatarUrl,
        lastSyncedAtMs = lastSyncedAtMs
    )

    private fun serializeSession(session: YtmAuthSession): String {
        return JSONObject().apply {
            put("cookie", session.cookie)
            put("visitorData", session.visitorData)
            if (!session.dataSyncId.isNullOrBlank()) put("dataSyncId", session.dataSyncId)
            if (!session.accountName.isNullOrBlank()) put("accountName", session.accountName)
            if (!session.accountHandle.isNullOrBlank()) put("accountHandle", session.accountHandle)
            if (!session.avatarUrl.isNullOrBlank()) put("avatarUrl", session.avatarUrl)
            put("lastSyncedAtMs", session.lastSyncedAtMs)
        }.toString()
    }

    private fun deserializeSession(raw: String): YtmAuthSession? {
        return try {
            val obj = JSONObject(raw)
            val cookie = obj.optString("cookie", "")
            if (cookie.isBlank()) return null
            YtmAuthSession(
                cookie = cookie,
                visitorData = obj.optString("visitorData", ""),
                dataSyncId = obj.optString("dataSyncId", "").ifBlank { null },
                accountName = obj.optString("accountName", "").ifBlank { null },
                accountHandle = obj.optString("accountHandle", "").ifBlank { null },
                avatarUrl = obj.optString("avatarUrl", "").ifBlank { null },
                lastSyncedAtMs = obj.optLong("lastSyncedAtMs", 0L)
            )
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "YouTubeAuthManager"
        const val PREFS_NAME = "xperience_ytm_auth_prefs"
        const val YOUTUBE_MUSIC_ORIGIN = "https://music.youtube.com"
        const val YOUTUBE_WWW_ORIGIN = "https://www.youtube.com"
        const val DEFAULT_SOCS_COOKIE = "SOCS=CAISEwgDEgk4MTM4MzYzNTIaAmVuIAEaBgiApPzGBg"
        const val DEFAULT_REMIND_LATER_MS = 3L * 24L * 60L * 60L * 1000L

        private const val KEY_ENCRYPTED_SESSION = "ytm_encrypted_session_v1"
        private const val KEY_REMIND_LATER_UNTIL_MS = "ytm_prompt_remind_later_until_ms"
        private const val KEY_PERMANENTLY_DISMISSED = "ytm_prompt_permanently_dismissed"

        private val AUTHENTICATED_COOKIE_NAMES = setOf(
            "SID",
            "HSID",
            "SSID",
            "APISID",
            "SAPISID",
            "__Secure-1PSID",
            "__Secure-3PSID",
            "__Secure-1PAPISID",
            "__Secure-3PAPISID"
        )

        @Volatile
        private var instance: YouTubeAuthManager? = null

        fun getInstance(context: Context): YouTubeAuthManager {
            instance?.let { return it }
            synchronized(this) {
                instance?.let { return it }
                return YouTubeAuthManager(context.applicationContext).also { instance = it }
            }
        }

        fun parseCookieString(cookie: String): Map<String, String> {
            if (cookie.isBlank()) return emptyMap()
            val result = linkedMapOf<String, String>()
            cookie.split(';').forEach { rawPart ->
                val part = rawPart.trim()
                if (part.isEmpty()) return@forEach
                val splitIndex = part.indexOf('=')
                if (splitIndex <= 0) return@forEach
                val name = part.substring(0, splitIndex).trim()
                val value = part.substring(splitIndex + 1).trim()
                if (name.isEmpty()) return@forEach
                val existing = result[name]
                if (existing.isNullOrBlank() || value.isNotBlank()) {
                    result[name] = value
                }
            }
            return result
        }

        fun mergeCookieStrings(vararg cookieStrings: String?): String {
            val merged = linkedMapOf<String, String>()
            cookieStrings.forEach { raw ->
                if (raw.isNullOrBlank()) return@forEach
                parseCookieString(raw).forEach { (name, value) ->
                    if (value.isNotBlank() && merged[name].isNullOrBlank()) {
                        merged[name] = value
                    }
                }
            }
            return merged.entries.joinToString("; ") { (name, value) -> "$name=$value" }
        }

        fun hasYouTubeAccountSession(cookie: String): Boolean {
            val map = parseCookieString(cookie)
            val hasAuthCookie = map.keys.any { it in AUTHENTICATED_COOKIE_NAMES && !map[it].isNullOrBlank() }
            return hasAuthCookie && extractSapisid(map) != null
        }

        private fun isSameAccountSession(existingCookie: String, newCookie: String): Boolean {
            val existingSapisid = extractSapisid(existingCookie)
            val newSapisid = extractSapisid(newCookie)
            return !existingSapisid.isNullOrBlank() && existingSapisid == newSapisid
        }

        fun decodePercentEncoded(raw: String?): String? {
            val trimmed = raw?.trim()?.takeIf { it.isNotBlank() && it != "null" } ?: return null
            if (!trimmed.contains('%')) return trimmed
            val bytes = ArrayList<Byte>(trimmed.length)
            var i = 0
            while (i < trimmed.length) {
                val c = trimmed[i]
                if (c == '%' && i + 2 < trimmed.length) {
                    val hi = trimmed[i + 1].digitToIntOrNull(16)
                    val lo = trimmed[i + 2].digitToIntOrNull(16)
                    if (hi != null && lo != null) {
                        bytes.add(((hi shl 4) or lo).toByte())
                        i += 3
                        continue
                    }
                }
                val charBytes = c.toString().toByteArray(Charsets.UTF_8)
                for (b in charBytes) bytes.add(b)
                i++
            }
            return String(bytes.toByteArray(), Charsets.UTF_8)
                .trim()
                .takeIf { it.isNotBlank() && it != "null" }
        }

        fun extractSapisid(cookieMap: Map<String, String>): String? {
            return cookieMap["SAPISID"]?.takeIf { it.isNotBlank() }
                ?: cookieMap["__Secure-3PAPISID"]?.takeIf { it.isNotBlank() }
                ?: cookieMap["__Secure-1PAPISID"]?.takeIf { it.isNotBlank() }
        }

        fun extractSapisid(cookie: String): String? = extractSapisid(parseCookieString(cookie))

        fun sha1Hex(input: String): String {
            val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
            return digest.joinToString(separator = "") { b -> "%02x".format(b) }
        }

        fun computeAuthorizationHeader(
            cookie: String,
            timestampSeconds: Long = System.currentTimeMillis() / 1000L,
            origin: String = YOUTUBE_MUSIC_ORIGIN,
            dataSyncId: String? = null
        ): String? {
            val cookieMap = parseCookieString(cookie)
            val sapisid = extractSapisid(cookieMap) ?: return null
            val tokens = mutableListOf<String>()

            val baseHash = sha1Hex("$timestampSeconds $sapisid $origin")
            tokens.add("SAPISIDHASH ${timestampSeconds}_${baseHash}")

            val userSessionId = dataSyncId
                ?.takeIf { it.contains("||") }
                ?.substringAfter("||")
                ?.trim()
                ?.takeIf { it.isNotBlank() && it != "null" }

            if (userSessionId != null) {
                val userHash = sha1Hex("$userSessionId $timestampSeconds $sapisid $origin")
                tokens.add("SAPISIDHASH ${timestampSeconds}_${userHash}_u")
            }

            val sapisid1p = cookieMap["__Secure-1PAPISID"]?.takeIf { it.isNotBlank() }
            if (sapisid1p != null) {
                val hash1p = sha1Hex("$timestampSeconds $sapisid1p $origin")
                tokens.add("SAPISID1PHASH ${timestampSeconds}_${hash1p}")
                if (userSessionId != null) {
                    val userHash1p = sha1Hex("$userSessionId $timestampSeconds $sapisid1p $origin")
                    tokens.add("SAPISID1PHASH ${timestampSeconds}_${userHash1p}_u")
                }
            }

            val sapisid3p = cookieMap["__Secure-3PAPISID"]?.takeIf { it.isNotBlank() }
            if (sapisid3p != null) {
                val hash3p = sha1Hex("$timestampSeconds $sapisid3p $origin")
                tokens.add("SAPISID3PHASH ${timestampSeconds}_${hash3p}")
                if (userSessionId != null) {
                    val userHash3p = sha1Hex("$userSessionId $timestampSeconds $sapisid3p $origin")
                    tokens.add("SAPISID3PHASH ${timestampSeconds}_${userHash3p}_u")
                }
            }

            return tokens.joinToString(" ")
        }
    }
}
