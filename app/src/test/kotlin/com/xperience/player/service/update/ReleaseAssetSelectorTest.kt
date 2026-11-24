package com.xperience.player.service.update

import com.xperience.player.domain.model.update.GitHubReleaseAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseAssetSelectorTest {

    private val validHashA = "a".repeat(64)
    private val validHashB = "0123456789abcdef".repeat(4)

    @Test
    fun selectsSingleApkIgnoringNonApkAssets() {
        val assets = listOf(
            asset("checksums.txt"),
            asset("XperiencePlayer-v1.0.1.apk"),
            asset("source.zip")
        )
        val selected = ReleaseAssetSelector.selectApkAsset(assets, listOf("arm64-v8a"))
        assertEquals("XperiencePlayer-v1.0.1.apk", selected?.name)
    }

    @Test
    fun prefersAndroidNamedApkWhenMultipleGenericApksExist() {
        val assets = listOf(
            asset("xperience-wear-v1.0.1.apk"),
            asset("xperience-android-v1.0.1.apk")
        )
        val selected = ReleaseAssetSelector.selectApkAsset(assets, listOf("arm64-v8a"))
        assertEquals("xperience-android-v1.0.1.apk", selected?.name)
    }

    @Test
    fun prefersMatchingAbiOverUniversalAndOtherAbis() {
        val assets = listOf(
            asset("xperience-android-armeabi-v7a.apk"),
            asset("xperience-android-universal.apk"),
            asset("xperience-android-arm64-v8a.apk"),
            asset("xperience-android-x86_64.apk")
        )
        val selectedArm64 = ReleaseAssetSelector.selectApkAsset(assets, listOf("arm64-v8a", "armeabi-v7a"))
        assertEquals("xperience-android-arm64-v8a.apk", selectedArm64?.name)

        val selectedX86 = ReleaseAssetSelector.selectApkAsset(assets, listOf("x86"))
        assertEquals("xperience-android-universal.apk", selectedX86?.name)
    }

    @Test
    fun prefersReleaseOverDebugApk() {
        val assets = listOf(
            asset("xperience-android-debug.apk"),
            asset("xperience-android-release.apk")
        )
        val selected = ReleaseAssetSelector.selectApkAsset(assets, listOf("arm64-v8a"))
        assertEquals("xperience-android-release.apk", selected?.name)
    }

    @Test
    fun returnsNullWhenNoApkAssetExists() {
        val assets = listOf(asset("checksums.txt"), asset("release-notes.md"))
        assertNull(ReleaseAssetSelector.selectApkAsset(assets, listOf("arm64-v8a")))
    }

    @Test
    fun normalizeSha256HandlesDigestPrefixAndRawHex() {
        assertEquals(validHashB, ReleaseAssetSelector.normalizeSha256("sha256:$validHashB"))
        assertEquals(validHashB, ReleaseAssetSelector.normalizeSha256("SHA256:${validHashB.uppercase()}"))
        assertEquals(validHashA, ReleaseAssetSelector.normalizeSha256("  ${validHashA.uppercase()}  "))
        assertNull(ReleaseAssetSelector.normalizeSha256(null))
        assertNull(ReleaseAssetSelector.normalizeSha256(""))
        assertNull(ReleaseAssetSelector.normalizeSha256("sha256:1234"))
        assertNull(ReleaseAssetSelector.normalizeSha256("g".repeat(64)))
    }

    @Test
    fun findsChecksumAssetWithCorrectPriority() {
        val apkName = "xperience-android-v1.0.1.apk"
        val withDedicated = listOf(
            asset(apkName),
            asset("checksums.txt"),
            asset("$apkName.sha256")
        )
        assertEquals("$apkName.sha256", ReleaseAssetSelector.findChecksumAsset(withDedicated, apkName)?.name)

        val withChecksumsTxt = listOf(
            asset(apkName),
            asset("other.sha256"),
            asset("SHA256SUMS")
        )
        assertEquals("SHA256SUMS", ReleaseAssetSelector.findChecksumAsset(withChecksumsTxt, apkName)?.name)
    }

    @Test
    fun parsesGnuBsdKeyValueAndDedicatedChecksumFormats() {
        val gnu = """
            # Release SHA256SUMS
            $validHashA  other-file.zip
            ${validHashB.uppercase()} *./dist/xperience-android.apk
        """.trimIndent()
        assertEquals(validHashB, ReleaseAssetSelector.parseChecksumFile(gnu, "xperience-android.apk"))

        val bsd = "SHA256 (xperience-android.apk) = $validHashB"
        assertEquals(validHashB, ReleaseAssetSelector.parseChecksumFile(bsd, "xperience-android.apk"))

        val kv = "xperience-android.apk: sha256:$validHashB"
        assertEquals(validHashB, ReleaseAssetSelector.parseChecksumFile(kv, "xperience-android.apk"))

        // Bare hash only accepted for dedicated <apk>.sha256 file, never for multi-file checksums.txt
        assertEquals(
            validHashB,
            ReleaseAssetSelector.parseChecksumFile("sha256:$validHashB", "xperience-android.apk", isDedicatedAssetFile = true)
        )
        assertNull(
            ReleaseAssetSelector.parseChecksumFile(validHashB, "xperience-android.apk", isDedicatedAssetFile = false)
        )

        // Missing filename fails closed
        assertNull(ReleaseAssetSelector.parseChecksumFile(gnu, "nonexistent.apk"))
    }

    @Test
    fun parsesUtf8BomPrefixedChecksumsAndSingleHashFiles() {
        val bomGnu = "\uFEFF$validHashB  xperience-android.apk\n"
        assertEquals(validHashB, ReleaseAssetSelector.parseChecksumFile(bomGnu, "xperience-android.apk"))

        val bomDedicated = "\uFEFFsha256:$validHashB\r\n"
        assertEquals(
            validHashB,
            ReleaseAssetSelector.parseChecksumFile(
                content = bomDedicated,
                targetFileName = "xperience-android.apk",
                isDedicatedAssetFile = true
            )
        )

        assertEquals(validHashB, ReleaseAssetSelector.normalizeSha256("\uFEFF$validHashB"))
        assertEquals(validHashB, ReleaseAssetSelector.normalizeSha256("\uFEFFsha256:\uFEFF$validHashB"))
    }

    @Test
    fun findChecksumAssetsFiltersHttpsAndReturnsOrderedCandidates() {
        val apkName = "xperience-android-v1.0.1.apk"
        val insecureDedicated = GitHubReleaseAsset(
            name = "$apkName.sha256",
            browserDownloadUrl = "http://insecure.example.com/$apkName.sha256",
            size = 64
        )
        val genericOther = asset("other-build.sha256")
        val manifest = asset("checksums.txt")
        val httpsDedicated = asset("$apkName.sha256sum")

        val candidates = ReleaseAssetSelector.findChecksumAssets(
            assets = listOf(asset(apkName), insecureDedicated, genericOther, manifest, httpsDedicated),
            targetApkName = apkName
        )

        assertEquals(
            listOf("$apkName.sha256sum", "checksums.txt", "other-build.sha256"),
            candidates.map { it.name }
        )
    }

    @Test
    fun validatesHttpsUrlsStrictly() {
        assertTrue(ReleaseAssetSelector.isValidHttpsUrl("https://github.com/AithonML/Xperience-Player-Android-/releases/download/v1.0.1/app.apk"))
        assertFalse(ReleaseAssetSelector.isValidHttpsUrl("http://github.com/AithonML/app.apk"))
        assertFalse(ReleaseAssetSelector.isValidHttpsUrl("file:///sdcard/app.apk"))
        assertFalse(ReleaseAssetSelector.isValidHttpsUrl(""))
        assertFalse(ReleaseAssetSelector.isValidHttpsUrl("not-a-url"))
    }

    @Test
    fun sanitizesApkFilenamesAgainstPathTraversal() {
        assertEquals("evil.apk", ReleaseAssetSelector.sanitizeApkFileName("../../evil.apk", "v1.0.1"))
        assertEquals("evil.apk", ReleaseAssetSelector.sanitizeApkFileName("..\\..\\evil.apk", "v1.0.1"))
        assertEquals("xperience-update-v1.0.1.apk", ReleaseAssetSelector.sanitizeApkFileName("..", "v1.0.1"))
        assertEquals("app_1.0.1.apk", ReleaseAssetSelector.sanitizeApkFileName("app 1.0.1.apk", "v1.0.1"))
    }

    private fun asset(name: String, digest: String? = null) = GitHubReleaseAsset(
        name = name,
        browserDownloadUrl = "https://github.com/AithonML/Xperience-Player-Android-/releases/download/v1.0.1/$name",
        size = 1024L,
        digest = digest
    )
}
