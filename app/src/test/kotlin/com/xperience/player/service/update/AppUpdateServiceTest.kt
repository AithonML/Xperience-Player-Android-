package com.xperience.player.service.update

import com.xperience.player.domain.model.update.UpdateCheckResult
import com.xperience.player.domain.model.update.UpdateDownloadProgress
import com.xperience.player.domain.model.update.UpdateDownloadResult
import com.xperience.player.domain.model.update.UpdateErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.buffer
import okio.source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AppUpdateServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun checkForUpdateQueriesGitHubEndpointWithRequiredHeadersAndParsesRelease() = runTest {
        var capturedRequest: Request? = null
        val apkBytes = "fake-apk-payload".toByteArray()
        val digest = "sha256:${sha256Hex(apkBytes)}"

        val releaseJson = """
            {
              "tag_name": "v1.0.1",
              "name": "Xperience 1.0.1",
              "body": "- Improved audio engine\n- Fixed crashes",
              "html_url": "https://github.com/AithonML/Xperience-Player-Android-/releases/tag/v1.0.1",
              "draft": false,
              "prerelease": false,
              "assets": [
                {
                  "name": "xperience-android-arm64-v8a.apk",
                  "browser_download_url": "https://github.com/AithonML/Xperience-Player-Android-/releases/download/v1.0.1/xperience-android-arm64-v8a.apk",
                  "size": ${apkBytes.size},
                  "content_type": "application/vnd.android.package-archive",
                  "digest": "$digest"
                }
              ]
            }
        """.trimIndent()

        val service = buildService(currentVersion = "0.1-Beta") { request ->
            capturedRequest = request
            jsonResponse(request, 200, releaseJson)
        }

        val result = service.checkForUpdate()
        assertTrue(result is UpdateCheckResult.UpdateAvailable)

        val available = result as UpdateCheckResult.UpdateAvailable
        assertEquals("v1.0.1", available.candidate.tagName)
        assertEquals("- Improved audio engine\n- Fixed crashes", available.candidate.releaseNotes)
        assertEquals("xperience-android-arm64-v8a.apk", available.candidate.apkAsset.name)
        assertEquals(sha256Hex(apkBytes), available.candidate.expectedSha256)

        val req = requireNotNull(capturedRequest)
        assertEquals(AppUpdateService.GITHUB_LATEST_RELEASE_URL, req.url.toString())
        assertEquals("XperienceMusicPlayer-Android/0.1-Beta", req.header("User-Agent"))
        assertEquals("application/vnd.github+json", req.header("Accept"))
    }

    @Test
    fun checkForUpdateReturnsUpToDateWhenVersionIsNotNewer() = runTest {
        val releaseJson = """
            {
              "tag_name": "v1.0.0",
              "name": "v1.0.0",
              "body": "Initial release",
              "html_url": "https://github.com/AithonML/Xperience-Player-Android-/releases/tag/v1.0.0",
              "draft": false,
              "prerelease": false,
              "assets": []
            }
        """.trimIndent()

        val service = buildService(currentVersion = "1.0.0") { request ->
            jsonResponse(request, 200, releaseJson)
        }

        val result = service.checkForUpdate()
        assertTrue(result is UpdateCheckResult.UpToDate)
    }

    @Test
    fun checkForUpdateHandles404AsNoReleasesPublished() = runTest {
        val service = buildService(currentVersion = "0.1-Beta") { request ->
            jsonResponse(request, 404, """{"message":"Not Found"}""")
        }

        val result = service.checkForUpdate()
        assertTrue(result is UpdateCheckResult.NoReleasesPublished)
    }

    @Test
    fun checkForUpdateReturnsNetworkErrorOnIoFailure() = runTest {
        val service = buildService(currentVersion = "0.1-Beta") {
            throw IOException("offline")
        }

        val result = service.checkForUpdate()
        assertTrue(result is UpdateCheckResult.Error)
        assertEquals(UpdateErrorKind.NETWORK, (result as UpdateCheckResult.Error).errorKind)
    }

    @Test
    fun downloadAndVerifySucceedsWithInlineDigestAndCleansStaleFiles() = runTest {
        val updatesDir = tempFolder.newFolder("updates")
        val staleApk = File(updatesDir, "old-version.apk").apply { writeText("stale") }

        val apkPayload = ByteArray(32 * 1024) { (it * 7 % 256).toByte() }
        val expectedHash = sha256Hex(apkPayload)

        val releaseJson = """
            {
              "tag_name": "v1.1.0",
              "name": "v1.1.0",
              "body": "Changelog",
              "html_url": "https://github.com/AithonML/Xperience-Player-Android-/releases/tag/v1.1.0",
              "draft": false,
              "prerelease": false,
              "assets": [
                {
                  "name": "xperience-android-v1.1.0.apk",
                  "browser_download_url": "https://github.com/AithonML/Xperience-Player-Android-/releases/download/v1.1.0/xperience-android-v1.1.0.apk",
                  "size": ${apkPayload.size},
                  "digest": "sha256:$expectedHash"
                }
              ]
            }
        """.trimIndent()

        val service = buildService(currentVersion = "0.1-Beta", updatesDir = updatesDir) { request ->
            when {
                request.url.toString().endsWith("/releases/latest") -> jsonResponse(request, 200, releaseJson)
                request.url.toString().endsWith(".apk") -> binaryResponse(request, 200, apkPayload)
                else -> jsonResponse(request, 404, "{}")
            }
        }

        val candidate = (service.checkForUpdate() as UpdateCheckResult.UpdateAvailable).candidate
        val progressEvents = mutableListOf<UpdateDownloadProgress>()
        var verifyingCalled = false

        val downloadResult = service.downloadAndVerifyUpdate(
            candidate = candidate,
            onProgress = { progressEvents.add(it) },
            onVerifying = { verifyingCalled = true }
        )

        assertTrue(downloadResult is UpdateDownloadResult.Success)
        val success = downloadResult as UpdateDownloadResult.Success
        assertEquals(expectedHash, success.sha256Hex)
        assertTrue(success.apkFile.exists())
        assertEquals("xperience-android-v1.1.0.apk", success.apkFile.name)
        assertFalse("Stale cached APK must be cleaned up", staleApk.exists())
        assertTrue("Verifying callback must be triggered", verifyingCalled)
        assertTrue("Progress must reach 100%", progressEvents.any { it.percent == 100 })
    }

    @Test
    fun downloadAndVerifyFallsBackToChecksumsTxtAsset() = runTest {
        val updatesDir = tempFolder.newFolder("updates-checksums")
        val apkPayload = "verified-via-checksums-txt".toByteArray()
        val expectedHash = sha256Hex(apkPayload)

        val releaseJson = """
            {
              "tag_name": "v1.2.0",
              "name": "v1.2.0",
              "body": "Checksum fallback test",
              "html_url": "https://github.com/AithonML/Xperience-Player-Android-/releases/tag/v1.2.0",
              "draft": false,
              "prerelease": false,
              "assets": [
                {
                  "name": "xperience-android.apk",
                  "browser_download_url": "https://github.com/AithonML/Xperience-Player-Android-/releases/download/v1.2.0/xperience-android.apk",
                  "size": ${apkPayload.size},
                  "digest": null
                },
                {
                  "name": "checksums.txt",
                  "browser_download_url": "https://github.com/AithonML/Xperience-Player-Android-/releases/download/v1.2.0/checksums.txt",
                  "size": 120
                }
              ]
            }
        """.trimIndent()

        val checksumsContent = "$expectedHash  xperience-android.apk\n"

        val service = buildService(currentVersion = "1.0.0", updatesDir = updatesDir) { request ->
            when {
                request.url.toString().endsWith("/releases/latest") -> jsonResponse(request, 200, releaseJson)
                request.url.toString().endsWith("checksums.txt") -> textResponse(request, 200, checksumsContent)
                request.url.toString().endsWith(".apk") -> binaryResponse(request, 200, apkPayload)
                else -> jsonResponse(request, 404, "{}")
            }
        }

        val candidate = (service.checkForUpdate() as UpdateCheckResult.UpdateAvailable).candidate
        val downloadResult = service.downloadAndVerifyUpdate(candidate)

        assertTrue(downloadResult is UpdateDownloadResult.Success)
        assertEquals(expectedHash, (downloadResult as UpdateDownloadResult.Success).sha256Hex)
        assertTrue(downloadResult.apkFile.exists())
    }

    @Test
    fun downloadAndVerifyDeletesTempFileAndFailsClosedOnSha256Mismatch() = runTest {
        val updatesDir = tempFolder.newFolder("updates-mismatch")
        val apkPayload = "tampered-apk-binary".toByteArray()
        val wrongHash = "f".repeat(64)

        val releaseJson = """
            {
              "tag_name": "v1.3.0",
              "name": "v1.3.0",
              "body": "Mismatch test",
              "html_url": "https://github.com/AithonML/Xperience-Player-Android-/releases/tag/v1.3.0",
              "draft": false,
              "prerelease": false,
              "assets": [
                {
                  "name": "xperience-android.apk",
                  "browser_download_url": "https://github.com/AithonML/Xperience-Player-Android-/releases/download/v1.3.0/xperience-android.apk",
                  "size": ${apkPayload.size},
                  "digest": "sha256:$wrongHash"
                }
              ]
            }
        """.trimIndent()

        val service = buildService(currentVersion = "1.0.0", updatesDir = updatesDir) { request ->
            when {
                request.url.toString().endsWith("/releases/latest") -> jsonResponse(request, 200, releaseJson)
                request.url.toString().endsWith(".apk") -> binaryResponse(request, 200, apkPayload)
                else -> jsonResponse(request, 404, "{}")
            }
        }

        val candidate = (service.checkForUpdate() as UpdateCheckResult.UpdateAvailable).candidate
        val downloadResult = service.downloadAndVerifyUpdate(candidate)

        assertTrue(downloadResult is UpdateDownloadResult.Error)
        assertEquals(
            UpdateErrorKind.INTEGRITY_MISMATCH,
            (downloadResult as UpdateDownloadResult.Error).errorKind
        )
        assertTrue(
            "Updates cache directory must have zero leftover .apk or .part files after mismatch",
            updatesDir.listFiles().orEmpty().isEmpty()
        )
    }

    @Test
    fun checkForUpdateFailsClosedImmediatelyWhenNoChecksumAvailable() = runTest {
        val updatesDir = tempFolder.newFolder("updates-nochecksum")

        val releaseJson = """
            {
              "tag_name": "v1.4.0",
              "name": "v1.4.0",
              "body": "Unsigned test",
              "html_url": "https://github.com/AithonML/Xperience-Player-Android-/releases/tag/v1.4.0",
              "draft": false,
              "prerelease": false,
              "assets": [
                {
                  "name": "xperience-android.apk",
                  "browser_download_url": "https://github.com/AithonML/Xperience-Player-Android-/releases/download/v1.4.0/xperience-android.apk",
                  "size": 100,
                  "digest": null
                }
              ]
            }
        """.trimIndent()

        val service = buildService(currentVersion = "1.0.0", updatesDir = updatesDir) { request ->
            jsonResponse(request, 200, releaseJson)
        }

        val checkResult = service.checkForUpdate()
        assertTrue(checkResult is UpdateCheckResult.Error)
        assertEquals(
            UpdateErrorKind.MISSING_CHECKSUM,
            (checkResult as UpdateCheckResult.Error).errorKind
        )
    }

    @Test
    fun downloadAndVerifyFailsClosedWhenChecksumFileDoesNotMatchApk() = runTest {
        val updatesDir = tempFolder.newFolder("updates-checksum-missing-entry")
        var apkDownloadAttempted = false

        val releaseJson = """
            {
              "tag_name": "v1.4.1",
              "name": "v1.4.1",
              "body": "Unmatched checksum entry test",
              "html_url": "https://github.com/AithonML/Xperience-Player-Android-/releases/tag/v1.4.1",
              "draft": false,
              "prerelease": false,
              "assets": [
                {
                  "name": "xperience-android.apk",
                  "browser_download_url": "https://github.com/AithonML/Xperience-Player-Android-/releases/download/v1.4.1/xperience-android.apk",
                  "size": 100,
                  "digest": null
                },
                {
                  "name": "checksums.txt",
                  "browser_download_url": "https://github.com/AithonML/Xperience-Player-Android-/releases/download/v1.4.1/checksums.txt",
                  "size": 100
                }
              ]
            }
        """.trimIndent()

        val service = buildService(currentVersion = "1.0.0", updatesDir = updatesDir) { request ->
            when {
                request.url.toString().endsWith("/releases/latest") -> jsonResponse(request, 200, releaseJson)
                request.url.toString().endsWith("checksums.txt") ->
                    textResponse(request, 200, "${"a".repeat(64)}  some-other-file.apk\n")
                request.url.toString().endsWith(".apk") -> {
                    apkDownloadAttempted = true
                    binaryResponse(request, 200, "apk".toByteArray())
                }
                else -> jsonResponse(request, 404, "{}")
            }
        }

        val candidate = (service.checkForUpdate() as UpdateCheckResult.UpdateAvailable).candidate
        val downloadResult = service.downloadAndVerifyUpdate(candidate)

        assertTrue(downloadResult is UpdateDownloadResult.Error)
        assertEquals(
            UpdateErrorKind.MISSING_CHECKSUM,
            (downloadResult as UpdateDownloadResult.Error).errorKind
        )
        assertFalse("Should fail closed before downloading unverified APK", apkDownloadAttempted)
        assertTrue(updatesDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun downloadAndVerifyFallsThroughChecksumCandidatesAndHandlesUtf8Bom() = runTest {
        val updatesDir = tempFolder.newFolder("updates-checksum-fallthrough")
        val apkPayload = "bom-and-fallback-apk".toByteArray()
        val expectedHash = sha256Hex(apkPayload)

        val releaseJson = """
            {
              "tag_name": "v1.4.2",
              "name": "v1.4.2",
              "body": "Ordered checksum candidates + BOM test",
              "html_url": "https://github.com/AithonML/Xperience-Player-Android-/releases/tag/v1.4.2",
              "draft": false,
              "prerelease": false,
              "assets": [
                {
                  "name": "xperience-android.apk",
                  "browser_download_url": "https://github.com/AithonML/Xperience-Player-Android-/releases/download/v1.4.2/xperience-android.apk",
                  "size": ${apkPayload.size},
                  "digest": null
                },
                {
                  "name": "xperience-android.apk.sha256",
                  "browser_download_url": "http://insecure.example.com/xperience-android.apk.sha256",
                  "size": 65
                },
                {
                  "name": "checksums.txt",
                  "browser_download_url": "https://github.com/AithonML/Xperience-Player-Android-/releases/download/v1.4.2/checksums.txt",
                  "size": 100
                },
                {
                  "name": "SHA256SUMS",
                  "browser_download_url": "https://github.com/AithonML/Xperience-Player-Android-/releases/download/v1.4.2/SHA256SUMS",
                  "size": 100
                }
              ]
            }
        """.trimIndent()

        val service = buildService(currentVersion = "1.0.0", updatesDir = updatesDir) { request ->
            when {
                request.url.toString().endsWith("/releases/latest") -> jsonResponse(request, 200, releaseJson)
                request.url.toString().endsWith("checksums.txt") ->
                    textResponse(request, 200, "${"b".repeat(64)}  unrelated.zip\n")
                request.url.toString().endsWith("SHA256SUMS") ->
                    textResponse(request, 200, "\uFEFF$expectedHash  xperience-android.apk\r\n")
                request.url.toString().endsWith(".apk") -> binaryResponse(request, 200, apkPayload)
                else -> jsonResponse(request, 404, "{}")
            }
        }

        val candidate = (service.checkForUpdate() as UpdateCheckResult.UpdateAvailable).candidate
        val downloadResult = service.downloadAndVerifyUpdate(candidate)

        assertTrue(downloadResult is UpdateDownloadResult.Success)
        assertEquals(expectedHash, (downloadResult as UpdateDownloadResult.Success).sha256Hex)
        assertTrue(downloadResult.apkFile.exists())
    }

    @Test
    fun checkForUpdateRejectsNonHttpsApkUrl() = runTest {
        val releaseJson = """
            {
              "tag_name": "v1.5.0",
              "name": "v1.5.0",
              "body": "Insecure url test",
              "html_url": "https://github.com/AithonML/Xperience-Player-Android-/releases/tag/v1.5.0",
              "draft": false,
              "prerelease": false,
              "assets": [
                {
                  "name": "xperience-android.apk",
                  "browser_download_url": "http://insecure.example.com/xperience-android.apk",
                  "size": 100,
                  "digest": "sha256:${"a".repeat(64)}"
                }
              ]
            }
        """.trimIndent()

        val service = buildService(currentVersion = "1.0.0") { request ->
            jsonResponse(request, 200, releaseJson)
        }

        val result = service.checkForUpdate()
        assertTrue(result is UpdateCheckResult.Error)
        assertEquals(UpdateErrorKind.INVALID_URL, (result as UpdateCheckResult.Error).errorKind)
    }

    @Test
    fun downloadAndVerifySanitizesPathTraversalFilenameInsideUpdatesDir() = runTest {
        val updatesDir = tempFolder.newFolder("updates-traversal")
        val apkPayload = "safe-apk-bytes".toByteArray()
        val expectedHash = sha256Hex(apkPayload)

        val releaseJson = """
            {
              "tag_name": "v1.6.0",
              "name": "v1.6.0",
              "body": "Traversal defense test",
              "html_url": "https://github.com/AithonML/Xperience-Player-Android-/releases/tag/v1.6.0",
              "draft": false,
              "prerelease": false,
              "assets": [
                {
                  "name": "../../escaped-android.apk",
                  "browser_download_url": "https://github.com/AithonML/Xperience-Player-Android-/releases/download/v1.6.0/escaped-android.apk",
                  "size": ${apkPayload.size},
                  "digest": "sha256:$expectedHash"
                }
              ]
            }
        """.trimIndent()

        val service = buildService(currentVersion = "1.0.0", updatesDir = updatesDir) { request ->
            when {
                request.url.toString().endsWith("/releases/latest") -> jsonResponse(request, 200, releaseJson)
                request.url.toString().endsWith(".apk") -> binaryResponse(request, 200, apkPayload)
                else -> jsonResponse(request, 404, "{}")
            }
        }

        val candidate = (service.checkForUpdate() as UpdateCheckResult.UpdateAvailable).candidate
        val downloadResult = service.downloadAndVerifyUpdate(candidate)

        assertTrue(downloadResult is UpdateDownloadResult.Success)
        val file = (downloadResult as UpdateDownloadResult.Success).apkFile
        assertEquals(updatesDir.canonicalFile, file.parentFile?.canonicalFile)
        assertEquals("escaped-android.apk", file.name)
    }

    @Test
    fun downloadAndVerifySupportsDedicatedSha256AssetFile() = runTest {
        val updatesDir = tempFolder.newFolder("updates-dedicated-sha256")
        val apkPayload = "dedicated-sha256-apk".toByteArray()
        val expectedHash = sha256Hex(apkPayload)

        val releaseJson = """
            {
              "tag_name": "v1.7.0",
              "name": "v1.7.0",
              "body": "Dedicated sha256 test",
              "html_url": "https://github.com/AithonML/Xperience-Player-Android-/releases/tag/v1.7.0",
              "draft": false,
              "prerelease": false,
              "assets": [
                {
                  "name": "xperience-android.apk",
                  "browser_download_url": "https://github.com/AithonML/Xperience-Player-Android-/releases/download/v1.7.0/xperience-android.apk",
                  "size": ${apkPayload.size}
                },
                {
                  "name": "xperience-android.apk.sha256",
                  "browser_download_url": "https://github.com/AithonML/Xperience-Player-Android-/releases/download/v1.7.0/xperience-android.apk.sha256",
                  "size": 65
                }
              ]
            }
        """.trimIndent()

        val service = buildService(currentVersion = "1.0.0", updatesDir = updatesDir) { request ->
            when {
                request.url.toString().endsWith("/releases/latest") -> jsonResponse(request, 200, releaseJson)
                request.url.toString().endsWith(".apk.sha256") -> textResponse(request, 200, "$expectedHash\n")
                request.url.toString().endsWith(".apk") -> binaryResponse(request, 200, apkPayload)
                else -> jsonResponse(request, 404, "{}")
            }
        }

        val candidate = (service.checkForUpdate() as UpdateCheckResult.UpdateAvailable).candidate
        val downloadResult = service.downloadAndVerifyUpdate(candidate)

        assertTrue(downloadResult is UpdateDownloadResult.Success)
        assertEquals(expectedHash, (downloadResult as UpdateDownloadResult.Success).sha256Hex)
    }

    @Test
    fun cancellingDownloadCancelsOkHttpCallAndDeletesOnlyOwnPartFile() = runTest {
        val updatesDir = tempFolder.newFolder("updates-cancel")
        val downloadStarted = CountDownLatch(1)
        val okHttpCallCancelled = AtomicBoolean(false)

        val releaseJson = """
            {
              "tag_name": "v1.8.0",
              "name": "v1.8.0",
              "body": "Cancel race test",
              "html_url": "https://github.com/AithonML/Xperience-Player-Android-/releases/tag/v1.8.0",
              "draft": false,
              "prerelease": false,
              "assets": [
                {
                  "name": "xperience-android.apk",
                  "browser_download_url": "https://github.com/AithonML/Xperience-Player-Android-/releases/download/v1.8.0/xperience-android.apk",
                  "size": 100000,
                  "digest": "sha256:${"c".repeat(64)}"
                }
              ]
            }
        """.trimIndent()

        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                when {
                    request.url.toString().endsWith("/releases/latest") -> jsonResponse(request, 200, releaseJson)
                    request.url.toString().endsWith(".apk") -> {
                        val slowStream = object : InputStream() {
                            private var emittedFirstChunk = false
                            override fun read(): Int = throw UnsupportedOperationException()
                            override fun read(b: ByteArray, off: Int, len: Int): Int {
                                if (!emittedFirstChunk) {
                                    emittedFirstChunk = true
                                    b[off] = 0x50
                                    return 1
                                }
                                downloadStarted.countDown()
                                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                                while (System.nanoTime() < deadline) {
                                    if (chain.call().isCanceled()) {
                                        okHttpCallCancelled.set(true)
                                        throw IOException("Canceled")
                                    }
                                    Thread.sleep(10)
                                }
                                return -1
                            }
                        }
                        Response.Builder()
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(slowStream.source().buffer().asResponseBody("application/octet-stream".toMediaType(), 100000L))
                            .build()
                    }
                    else -> jsonResponse(request, 404, "{}")
                }
            })
            .build()

        val service = AppUpdateService(
            okHttpClient = client,
            updatesDirProvider = { updatesDir },
            currentVersionProvider = { "1.0.0" },
            supportedAbisProvider = { listOf("arm64-v8a") }
        )

        val candidate = (service.checkForUpdate() as UpdateCheckResult.UpdateAvailable).candidate
        val downloadJob = launch(Dispatchers.Default) {
            service.downloadAndVerifyUpdate(candidate)
        }

        assertTrue("Download should start streaming", downloadStarted.await(5, TimeUnit.SECONDS))

        // Simulate a newer retry's final file and unique .part file existing alongside this attempt
        val siblingRetryPart = File(updatesDir, "xperience-android.apk.other-attempt.part").apply { writeText("retry") }
        val siblingFinalApk = File(updatesDir, "xperience-android.apk").apply { writeText("verified") }

        downloadJob.cancelAndJoin()

        assertTrue("Coroutine cancellation must invoke OkHttp call.cancel()", okHttpCallCancelled.get())
        assertTrue("Cancellation must not delete a sibling retry's .part file", siblingRetryPart.exists())
        assertTrue("Cancellation must not delete finalApkFile", siblingFinalApk.exists())
        val remainingParts = updatesDir.listFiles().orEmpty().filter { it.name.endsWith(".part") }
        assertEquals("Only sibling .part should remain; cancelled attempt's .part must be deleted", 1, remainingParts.size)
        assertEquals(siblingRetryPart.name, remainingParts.first().name)
    }

    private fun buildService(
        currentVersion: String,
        updatesDir: File = tempFolder.root,
        handler: (Request) -> Response
    ): AppUpdateService {
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain -> handler(chain.request()) })
            .build()
        return AppUpdateService(
            okHttpClient = client,
            updatesDirProvider = { updatesDir },
            currentVersionProvider = { currentVersion },
            supportedAbisProvider = { listOf("arm64-v8a", "armeabi-v7a") }
        )
    }

    private fun jsonResponse(request: Request, code: Int, json: String): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "ERR")
            .body(json.toResponseBody("application/json".toMediaType()))
            .build()
    }

    private fun textResponse(request: Request, code: Int, text: String): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("OK")
            .body(text.toResponseBody("text/plain".toMediaType()))
            .build()
    }

    private fun binaryResponse(request: Request, code: Int, bytes: ByteArray): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("OK")
            .header("Content-Length", bytes.size.toString())
            .body(bytes.toResponseBody("application/octet-stream".toMediaType()))
            .build()
    }

    private fun sha256Hex(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }
}
