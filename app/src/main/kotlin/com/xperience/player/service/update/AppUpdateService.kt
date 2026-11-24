package com.xperience.player.service.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.xperience.player.BuildConfig
import com.xperience.player.domain.model.update.GitHubRelease
import com.xperience.player.domain.model.update.GitHubReleaseAsset
import com.xperience.player.domain.model.update.UpdateCandidate
import com.xperience.player.domain.model.update.UpdateCheckResult
import com.xperience.player.domain.model.update.UpdateDownloadProgress
import com.xperience.player.domain.model.update.UpdateDownloadResult
import com.xperience.player.domain.model.update.UpdateErrorKind
import com.xperience.player.service.MediaNetworkClients
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Modular GitHub Releases auto-update service for Xperience Music Player Android.
 *
 * Responsibilities:
 * - Queries GitHub Releases `/releases/latest` with compliant `User-Agent` & `Accept` headers.
 * - Compares release tags against the installed app version via SemVer 2.0.0.
 * - Selects the best Android `.apk` asset and validates HTTPS download URLs.
 * - Resolves expected SHA-256 from asset `digest` or ordered HTTPS companion checksum files
 *   (`checksums.txt`, `SHA256SUMS`, `*.sha256`), failing closed when no valid hash is available.
 * - Downloads to a unique `cacheDir/updates/<sanitized>.<uuid>.part` file with cancellable OkHttp
 *   execution and streaming progress (0%–100%), verifies SHA-256 in streaming fashion, immediately
 *   deletes the temp file on any mismatch/error/cancellation, and promotes to `.apk` only on success.
 */
class AppUpdateService(
    private val okHttpClient: OkHttpClient,
    private val updatesDirProvider: () -> File,
    private val currentVersionProvider: () -> String,
    private val supportedAbisProvider: () -> List<String>,
    private val latestReleaseUrl: String = GITHUB_LATEST_RELEASE_URL
) {

    constructor(context: Context) : this(
        okHttpClient = createDefaultHttpClient(),
        updatesDirProvider = { File(context.applicationContext.cacheDir, UPDATES_SUBDIR) },
        currentVersionProvider = { resolveInstalledVersionName(context.applicationContext) },
        supportedAbisProvider = { Build.SUPPORTED_ABIS?.toList().orEmpty() }
    )

    fun getCurrentVersionName(): String = currentVersionProvider()

    fun getCurrentVersionDisplayTag(): String = SemVerParser.formatDisplayTag(getCurrentVersionName())

    /**
     * Checks GitHub Releases for a newer version than the currently installed app version.
     */
    suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val currentVersion = currentVersionProvider().trim().ifEmpty { BuildConfig.VERSION_NAME }
        val currentSemVer = SemVerParser.parse(currentVersion)
            ?: return@withContext UpdateCheckResult.Error(
                UpdateErrorKind.UNKNOWN,
                "Invalid installed version: $currentVersion"
            )

        if (!ReleaseAssetSelector.isValidHttpsUrl(latestReleaseUrl)) {
            return@withContext UpdateCheckResult.Error(UpdateErrorKind.INVALID_URL)
        }

        val request = Request.Builder()
            .url(latestReleaseUrl)
            .header("User-Agent", buildUserAgent(currentVersion))
            .header("Accept", GITHUB_ACCEPT_HEADER)
            .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
            .get()
            .build()

        val fetchOutcome = try {
            executeCancellable(okHttpClient.newCall(request)) { response ->
                when {
                    !response.request.url.isHttps ->
                        ReleaseFetchOutcome.Failure(UpdateCheckResult.Error(UpdateErrorKind.INVALID_URL))
                    response.code == 404 ->
                        ReleaseFetchOutcome.Failure(UpdateCheckResult.NoReleasesPublished(currentVersion))
                    !response.isSuccessful ->
                        ReleaseFetchOutcome.Failure(
                            UpdateCheckResult.Error(UpdateErrorKind.NETWORK, "HTTP ${response.code}")
                        )
                    else -> {
                        val parsed = parseReleaseJson(response.body?.string().orEmpty())
                        if (parsed != null) {
                            ReleaseFetchOutcome.Success(parsed)
                        } else {
                            ReleaseFetchOutcome.Failure(
                                UpdateCheckResult.Error(
                                    UpdateErrorKind.UNKNOWN,
                                    "Malformed GitHub release payload"
                                )
                            )
                        }
                    }
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (ioe: IOException) {
            return@withContext UpdateCheckResult.Error(UpdateErrorKind.NETWORK, ioe.message)
        } catch (e: Exception) {
            return@withContext UpdateCheckResult.Error(UpdateErrorKind.UNKNOWN, e.message)
        }

        val release = when (fetchOutcome) {
            is ReleaseFetchOutcome.Failure -> return@withContext fetchOutcome.result
            is ReleaseFetchOutcome.Success -> fetchOutcome.release
        }

        if (release.draft) {
            return@withContext UpdateCheckResult.NoReleasesPublished(currentVersion)
        }

        val remoteSemVer = SemVerParser.parse(release.tagName)
            ?: return@withContext UpdateCheckResult.Error(
                UpdateErrorKind.UNKNOWN,
                "Unsupported release tag: ${release.tagName}"
            )

        if (!remoteSemVer.isNewerThan(currentSemVer)) {
            return@withContext UpdateCheckResult.UpToDate(
                currentVersion = currentVersion,
                latestTag = release.tagName
            )
        }

        val apkAsset = ReleaseAssetSelector.selectApkAsset(
            assets = release.assets,
            supportedAbis = supportedAbisProvider()
        ) ?: return@withContext UpdateCheckResult.Error(UpdateErrorKind.NO_APK_ASSET)

        if (!ReleaseAssetSelector.isValidHttpsUrl(apkAsset.browserDownloadUrl)) {
            return@withContext UpdateCheckResult.Error(UpdateErrorKind.INVALID_URL)
        }

        val inlineSha256 = ReleaseAssetSelector.normalizeSha256(apkAsset.digest)
        val checksumAssets = if (inlineSha256 == null) {
            ReleaseAssetSelector.findChecksumAssets(release.assets, apkAsset.name)
        } else {
            emptyList()
        }

        // Fail closed upfront if no inline digest and no valid HTTPS checksum asset exists
        if (inlineSha256 == null && checksumAssets.isEmpty()) {
            return@withContext UpdateCheckResult.Error(UpdateErrorKind.MISSING_CHECKSUM)
        }

        val candidate = UpdateCandidate(
            tagName = SemVerParser.formatDisplayTag(release.tagName),
            version = remoteSemVer,
            releaseTitle = release.name.ifBlank { SemVerParser.formatDisplayTag(release.tagName) },
            releaseNotes = release.body.trim(),
            apkAsset = apkAsset,
            expectedSha256 = inlineSha256,
            checksumAssets = checksumAssets,
            htmlUrl = release.htmlUrl
        )
        UpdateCheckResult.UpdateAvailable(candidate)
    }

    /**
     * Resolves expected SHA-256 (fail-closed), cleans stale cached APKs, streams the APK download
     * to a per-attempt `.part` file with 0%–100% progress, verifies SHA-256 in streaming fashion,
     * deletes the temp file immediately on mismatch/error/cancellation, and promotes to `.apk` only
     * on verified success.
     */
    suspend fun downloadAndVerifyUpdate(
        candidate: UpdateCandidate,
        onProgress: (UpdateDownloadProgress) -> Unit = {},
        onVerifying: () -> Unit = {}
    ): UpdateDownloadResult = withContext(Dispatchers.IO) {
        val currentVersion = currentVersionProvider().trim().ifEmpty { BuildConfig.VERSION_NAME }
        val userAgent = buildUserAgent(currentVersion)

        // 1. Validate APK download URL uses HTTPS
        val downloadUrl = candidate.apkAsset.browserDownloadUrl
        if (!ReleaseAssetSelector.isValidHttpsUrl(downloadUrl)) {
            return@withContext UpdateDownloadResult.Error(UpdateErrorKind.INVALID_URL)
        }

        // 2. Resolve expected SHA-256 (asset digest first, ordered HTTPS checksum files fallback) — fail closed!
        val expectedSha256 = resolveExpectedSha256(candidate, userAgent)
            ?: return@withContext UpdateDownloadResult.Error(UpdateErrorKind.MISSING_CHECKSUM)

        // 3. Prepare cacheDir/updates/ and sanitize filename against path traversal
        val updatesDir = updatesDirProvider()
        if (!updatesDir.exists() && !updatesDir.mkdirs()) {
            return@withContext UpdateDownloadResult.Error(
                UpdateErrorKind.UNKNOWN,
                "Unable to create updates cache directory"
            )
        }

        val safeApkName = ReleaseAssetSelector.sanitizeApkFileName(
            rawName = candidate.apkAsset.name,
            fallbackVersionTag = candidate.tagName
        )
        val finalApkFile = File(updatesDir, safeApkName)
        val partFile = File(updatesDir, "$safeApkName.${UUID.randomUUID()}.part")

        if (!isChildOfDirectory(updatesDir, finalApkFile) || !isChildOfDirectory(updatesDir, partFile)) {
            return@withContext UpdateDownloadResult.Error(
                UpdateErrorKind.UNKNOWN,
                "Unsafe target update path"
            )
        }

        StreamingSha256Verifier.cleanupStaleFiles(updatesDir, keepFile = partFile)
        StreamingSha256Verifier.safeDelete(partFile)

        // 4. Stream APK download to unique `.part` while hashing SHA-256 on the fly
        val request = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", userAgent)
            .header("Accept", "application/octet-stream")
            .get()
            .build()

        val outerContext = currentCoroutineContext()
        try {
            val streamOutcome = executeCancellable(okHttpClient.newCall(request)) { response ->
                when {
                    !response.request.url.isHttps ->
                        StreamOutcome.Error(UpdateDownloadResult.Error(UpdateErrorKind.INVALID_URL))
                    !response.isSuccessful ->
                        StreamOutcome.Error(
                            UpdateDownloadResult.Error(UpdateErrorKind.NETWORK, "HTTP ${response.code}")
                        )
                    else -> {
                        val body = response.body
                        if (body == null) {
                            StreamOutcome.Error(
                                UpdateDownloadResult.Error(
                                    UpdateErrorKind.NETWORK,
                                    "Empty download response body"
                                )
                            )
                        } else {
                            val totalBytes = body.contentLength().takeIf { it > 0L }
                                ?: candidate.apkAsset.size.takeIf { it > 0L }
                                ?: -1L

                            onProgress(
                                UpdateDownloadProgress(
                                    bytesDownloaded = 0L,
                                    totalBytes = totalBytes,
                                    percent = if (totalBytes > 0L) 0 else null
                                )
                            )

                            var lastEmittedPercent = if (totalBytes > 0L) 0 else -1
                            var lastEmittedBytes = 0L

                            val hash = body.byteStream().use { inputStream ->
                                StreamingSha256Verifier.streamCopyAndHash(
                                    input = inputStream,
                                    destinationPartFile = partFile,
                                    expectedTotalBytes = totalBytes,
                                    checkCancellation = { outerContext.ensureActive() },
                                    onProgress = { bytesRead, total ->
                                        if (total > 0L) {
                                            val pct = ((bytesRead * 100L) / total).toInt().coerceIn(0, 100)
                                            if (pct != lastEmittedPercent || bytesRead >= total) {
                                                lastEmittedPercent = pct
                                                lastEmittedBytes = bytesRead
                                                onProgress(UpdateDownloadProgress(bytesRead, total, pct))
                                            }
                                        } else if (bytesRead - lastEmittedBytes >= INDETERMINATE_PROGRESS_STEP_BYTES) {
                                            lastEmittedBytes = bytesRead
                                            onProgress(UpdateDownloadProgress(bytesRead, -1L, null))
                                        }
                                    }
                                )
                            }
                            StreamOutcome.Hashed(hash)
                        }
                    }
                }
            }

            val computedSha256 = when (streamOutcome) {
                is StreamOutcome.Error -> {
                    StreamingSha256Verifier.safeDelete(partFile)
                    return@withContext streamOutcome.result
                }
                is StreamOutcome.Hashed -> streamOutcome.sha256Hex
            }

            // 5. Strict SHA-256 verification and atomic promotion `.part` -> `.apk`
            outerContext.ensureActive()
            onVerifying()
            val verified = StreamingSha256Verifier.verifyAndPromote(
                partFile = partFile,
                finalFile = finalApkFile,
                computedSha256 = computedSha256,
                expectedSha256 = expectedSha256
            )

            if (!verified) {
                // verifyAndPromote already deleted partFile & finalApkFile immediately
                return@withContext UpdateDownloadResult.Error(UpdateErrorKind.INTEGRITY_MISMATCH)
            }

            UpdateDownloadResult.Success(apkFile = finalApkFile, sha256Hex = computedSha256)
        } catch (ce: CancellationException) {
            // Only clean up this attempt's unique `.part` file so a subsequent retry is never clobbered
            StreamingSha256Verifier.safeDelete(partFile)
            throw ce
        } catch (ioe: IOException) {
            StreamingSha256Verifier.safeDelete(partFile)
            UpdateDownloadResult.Error(UpdateErrorKind.NETWORK, ioe.message)
        } catch (e: Exception) {
            StreamingSha256Verifier.safeDelete(partFile)
            UpdateDownloadResult.Error(UpdateErrorKind.UNKNOWN, e.message)
        }
    }

    private suspend fun resolveExpectedSha256(
        candidate: UpdateCandidate,
        userAgent: String
    ): String? {
        // Priority 1: Inline GitHub release asset `digest`
        ReleaseAssetSelector.normalizeSha256(candidate.expectedSha256)?.let { return it }
        ReleaseAssetSelector.normalizeSha256(candidate.apkAsset.digest)?.let { return it }

        // Priority 2: Ordered valid HTTPS companion checksum files (`<apk>.sha256`, `checksums.txt`, `SHA256SUMS`, `*.sha256`)
        for (checksumAsset in candidate.checksumAssets) {
            currentCoroutineContext().ensureActive()
            if (!ReleaseAssetSelector.isValidHttpsUrl(checksumAsset.browserDownloadUrl)) {
                continue
            }

            val request = Request.Builder()
                .url(checksumAsset.browserDownloadUrl)
                .header("User-Agent", userAgent)
                .header("Accept", "text/plain, application/octet-stream, */*")
                .get()
                .build()

            val checksumText = try {
                executeCancellable(okHttpClient.newCall(request)) { response ->
                    if (!response.request.url.isHttps || !response.isSuccessful) {
                        null
                    } else {
                        response.body?.let { readBoundedUtf8(it.byteStream(), MAX_CHECKSUM_FILE_BYTES) }
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                null
            } ?: continue

            val isDedicated = ReleaseAssetSelector.isDedicatedChecksumFile(
                checksumAssetName = checksumAsset.name,
                targetApkName = candidate.apkAsset.name
            )
            val parsed = ReleaseAssetSelector.parseChecksumFile(
                content = checksumText,
                targetFileName = candidate.apkAsset.name,
                isDedicatedAssetFile = isDedicated
            )
            if (parsed != null) {
                return parsed
            }
        }

        return null
    }

    /**
     * Executes an OkHttp [call] while wiring coroutine cancellation immediately to [Call.cancel]
     * on [Dispatchers.IO] so stalled network reads abort immediately instead of waiting for readTimeout.
     */
    private suspend fun <T> executeCancellable(
        call: Call,
        block: (Response) -> T
    ): T = coroutineScope {
        val completed = AtomicBoolean(false)
        val cancelWatcher = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                if (!completed.get()) {
                    call.cancel()
                }
            }
        }
        try {
            coroutineContext.ensureActive()
            val result = call.execute().use(block)
            completed.set(true)
            result
        } catch (ioe: IOException) {
            if (!coroutineContext.isActive) {
                throw CancellationException("Update HTTP request cancelled", ioe)
            }
            throw ioe
        } finally {
            completed.set(true)
            cancelWatcher.cancel()
        }
    }

    private fun readBoundedUtf8(input: java.io.InputStream, maxBytes: Int): String? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var total = 0
        input.use { stream ->
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                total += read
                if (total > maxBytes) return null
                output.write(buffer, 0, read)
            }
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun isChildOfDirectory(parentDir: File, candidateFile: File): Boolean {
        return runCatching {
            val parentPath = parentDir.canonicalFile.toPath().normalize()
            val candidatePath = candidateFile.canonicalFile.toPath().normalize()
            candidatePath.startsWith(parentPath) && candidatePath != parentPath
        }.getOrDefault(false)
    }

    private sealed interface ReleaseFetchOutcome {
        data class Success(val release: GitHubRelease) : ReleaseFetchOutcome
        data class Failure(val result: UpdateCheckResult) : ReleaseFetchOutcome
    }

    private sealed interface StreamOutcome {
        data class Hashed(val sha256Hex: String) : StreamOutcome
        data class Error(val result: UpdateDownloadResult.Error) : StreamOutcome
    }

    companion object {
        const val GITHUB_LATEST_RELEASE_URL =
            "https://api.github.com/repos/AithonML/Xperience-Player-Android-/releases/latest"
        const val GITHUB_ACCEPT_HEADER = "application/vnd.github+json"
        const val GITHUB_API_VERSION = "2022-11-28"
        const val UPDATES_SUBDIR = "updates"

        private const val MAX_CHECKSUM_FILE_BYTES = 256 * 1024 // 256 KiB safety cap
        private const val INDETERMINATE_PROGRESS_STEP_BYTES = 64L * 1024L

        fun buildUserAgent(appVersion: String): String {
            val cleanVersion = appVersion.trim().ifEmpty { "0.0.0" }
            return "XperienceMusicPlayer-Android/$cleanVersion"
        }

        fun createDefaultHttpClient(): OkHttpClient {
            return MediaNetworkClients.sharedOkHttpClient.newBuilder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .followRedirects(true)
                // Disallow cross-protocol redirects (prevents HTTPS -> HTTP downgrade)
                .followSslRedirects(false)
                .build()
        }

        fun resolveInstalledVersionName(context: Context): String {
            val pkgVersion = runCatching {
                val info = context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
                info.versionName?.trim()?.takeIf { it.isNotEmpty() }
            }.getOrNull()
            return pkgVersion ?: BuildConfig.VERSION_NAME
        }

        fun parseReleaseJson(rawJson: String): GitHubRelease? {
            if (rawJson.isBlank()) return null
            return try {
                val root = JSONObject(rawJson)
                val tagName = root.optNullableString("tag_name") ?: return null
                val name = root.optNullableString("name") ?: tagName
                val body = root.optNullableString("body").orEmpty()
                val htmlUrl = root.optNullableString("html_url").orEmpty()
                val draft = root.optBoolean("draft", false)
                val prerelease = root.optBoolean("prerelease", false)

                val assetsJson = root.optJSONArray("assets")
                val assets = buildList {
                    if (assetsJson != null) {
                        for (i in 0 until assetsJson.length()) {
                            val obj = assetsJson.optJSONObject(i) ?: continue
                            val assetName = obj.optNullableString("name") ?: continue
                            val downloadUrl = obj.optNullableString("browser_download_url") ?: continue
                            add(
                                GitHubReleaseAsset(
                                    name = assetName,
                                    browserDownloadUrl = downloadUrl,
                                    size = obj.optLong("size", -1L),
                                    contentType = obj.optNullableString("content_type").orEmpty(),
                                    digest = obj.optNullableString("digest")
                                )
                            )
                        }
                    }
                }

                GitHubRelease(
                    tagName = tagName,
                    name = name,
                    body = body,
                    htmlUrl = htmlUrl,
                    draft = draft,
                    prerelease = prerelease,
                    assets = assets
                )
            } catch (_: Exception) {
                null
            }
        }

        private fun JSONObject.optNullableString(key: String): String? {
            if (!has(key) || isNull(key)) return null
            return optString(key).takeIf { it.isNotBlank() }
        }
    }
}
