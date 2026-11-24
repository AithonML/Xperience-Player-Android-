package com.xperience.player.service.update

import com.xperience.player.domain.model.update.GitHubReleaseAsset
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

/**
 * Filters GitHub release assets for Android APK installers, validates HTTPS URLs,
 * sanitizes local filenames against path traversal, and resolves expected SHA-256 checksums
 * from GitHub asset `digest` metadata or companion checksum files.
 */
object ReleaseAssetSelector {

    private val SHA256_HEX_REGEX = Regex("^[0-9a-fA-F]{64}$")
    private val BSD_CHECKSUM_REGEX = Regex("""^SHA256\s*\((.+)\)\s*=\s*(?:sha256:)?([0-9a-fA-F]{64})$""", RegexOption.IGNORE_CASE)
    private val GNU_CHECKSUM_REGEX = Regex("""^(?:sha256:)?([0-9a-fA-F]{64})\s+\*?(.+)$""", RegexOption.IGNORE_CASE)
    private val KV_CHECKSUM_REGEX = Regex("""^(.+?)\s*[:=]\s*(?:sha256:)?([0-9a-fA-F]{64})$""", RegexOption.IGNORE_CASE)

    private val KNOWN_CHECKSUM_FILENAMES = setOf(
        "checksums.txt",
        "sha256sums",
        "sha256sums.txt",
        "checksums.sha256",
        "sha256.txt",
        "checksum.txt"
    )

    private val ABI_ARM64_TOKENS = listOf("arm64-v8a", "arm64_v8a", "arm64", "aarch64")
    private val ABI_ARMV7_TOKENS = listOf("armeabi-v7a", "armeabi_v7a", "armeabi", "armv7a", "armv7")
    private val ABI_X86_64_TOKENS = listOf("x86_64", "x86-64", "amd64", "x64")
    private val ABI_X86_TOKENS = listOf("x86", "i686", "i386")
    private val UNIVERSAL_TOKENS = listOf("universal", "fat")

    /**
     * Strict HTTPS validation for download and checksum URLs.
     */
    fun isValidHttpsUrl(url: String?): Boolean {
        val clean = url?.trim().orEmpty()
        if (clean.isEmpty()) return false
        val parsed = clean.toHttpUrlOrNull() ?: return false
        return parsed.isHttps && parsed.host.isNotBlank()
    }

    /**
     * Selects the best Android `.apk` asset from [assets], preferring ABI match against [supportedAbis]
     * and `*android*.apk` release builds when multiple APKs are present, falling back to generic/universal `.apk`.
     */
    fun selectApkAsset(
        assets: List<GitHubReleaseAsset>,
        supportedAbis: List<String> = emptyList()
    ): GitHubReleaseAsset? {
        val apkAssets = assets.filter { it.name.trim().lowercase(Locale.ROOT).endsWith(".apk") }
        if (apkAssets.isEmpty()) return null
        if (apkAssets.size == 1) return apkAssets.first()

        val normalizedSupportedAbis = supportedAbis
            .mapNotNull { canonicalAbi(it) }
            .distinct()

        data class ScoredApk(
            val asset: GitHubReleaseAsset,
            val tier: Int,
            val abiRank: Int,
            val hasAndroidToken: Boolean,
            val isNotDebug: Boolean,
            val originalIndex: Int
        )

        val scored = apkAssets.mapIndexedNotNull { idx, asset ->
            val lower = asset.name.trim().lowercase(Locale.ROOT)
            val detectedAbis = detectAbisInFilename(lower)
            val isUniversal = containsAnyToken(lower, UNIVERSAL_TOKENS)

            val (tier, abiRank) = when {
                normalizedSupportedAbis.isEmpty() -> {
                    // No device ABI preference provided: prefer universal/generic, otherwise keep all
                    if (isUniversal || detectedAbis.isEmpty()) 2 to 0 else 1 to 0
                }
                detectedAbis.isEmpty() || isUniversal -> {
                    // Universal or ABI-agnostic APK works on every device
                    2 to Int.MAX_VALUE
                }
                else -> {
                    val bestIndex = detectedAbis
                        .map { abi -> normalizedSupportedAbis.indexOf(abi) }
                        .filter { it >= 0 }
                        .minOrNull()
                    if (bestIndex != null) {
                        3 to bestIndex
                    } else {
                        // Explicitly targets an incompatible ABI
                        return@mapIndexedNotNull null
                    }
                }
            }

            ScoredApk(
                asset = asset,
                tier = tier,
                abiRank = abiRank,
                hasAndroidToken = lower.contains("android"),
                isNotDebug = !containsToken(lower, "debug"),
                originalIndex = idx
            )
        }

        if (scored.isEmpty()) {
            // Last-resort fallback only if all APKs failed ABI match (e.g. non-standard naming)
            return apkAssets.firstOrNull { it.name.lowercase(Locale.ROOT).contains("android") }
                ?: apkAssets.first()
        }

        return scored
            .sortedWith(
                compareByDescending<ScoredApk> { it.tier }
                    .thenBy { it.abiRank }
                    .thenByDescending { it.hasAndroidToken }
                    .thenByDescending { it.isNotDebug }
                    .thenBy { it.originalIndex }
            )
            .first()
            .asset
    }

    /**
     * Normalizes an expected SHA-256 string (supporting `"sha256:<hex>"` or raw 64-char hex,
     * and stripping any UTF-8 BOM `'\uFEFF'`).
     * Returns lowercase 64-char hex or `null` if invalid/blank.
     */
    fun normalizeSha256(raw: String?): String? {
        val trimmed = raw?.trimBomAndWhitespace().orEmpty()
        if (trimmed.isEmpty()) return null
        val hex = if (trimmed.startsWith("sha256:", ignoreCase = true)) {
            trimmed.substring("sha256:".length).trimBomAndWhitespace()
        } else {
            trimmed
        }
        return if (SHA256_HEX_REGEX.matches(hex)) hex.lowercase(Locale.ROOT) else null
    }

    /**
     * Returns all valid HTTPS companion checksum candidate assets in [assets] ordered by priority:
     * 1. Dedicated checksum file for [targetApkName] (`<apk>.sha256`, `<apk>.sha256sum`, `<apk>.sha256.txt`)
     * 2. Well-known release checksum manifests (`checksums.txt`, `SHA256SUMS`, etc.)
     * 3. Generic `*.sha256`, `*.sha256sum`, or `*checksum*`/`*sha256*` `.txt` assets
     */
    fun findChecksumAssets(
        assets: List<GitHubReleaseAsset>,
        targetApkName: String
    ): List<GitHubReleaseAsset> {
        val httpsAssets = assets.filter { isValidHttpsUrl(it.browserDownloadUrl) }
        if (httpsAssets.isEmpty()) return emptyList()

        val targetLower = extractFileName(targetApkName).lowercase(Locale.ROOT)
        val dedicatedCandidates = setOf(
            "$targetLower.sha256",
            "$targetLower.sha256sum",
            "$targetLower.sha256.txt"
        )

        val dedicated = mutableListOf<GitHubReleaseAsset>()
        val manifests = mutableListOf<GitHubReleaseAsset>()
        val generic = mutableListOf<GitHubReleaseAsset>()

        for (asset in httpsAssets) {
            val lower = asset.name.trim().lowercase(Locale.ROOT)
            when {
                lower in dedicatedCandidates -> dedicated.add(asset)
                lower in KNOWN_CHECKSUM_FILENAMES -> manifests.add(asset)
                lower.endsWith(".sha256") ||
                    lower.endsWith(".sha256sum") ||
                    (lower.endsWith(".txt") && (lower.contains("checksum") || lower.contains("sha256"))) ->
                    generic.add(asset)
            }
        }

        return (dedicated + manifests + generic).distinctBy { it.browserDownloadUrl }
    }

    /**
     * Locates the highest-priority valid HTTPS companion checksum asset in [assets] for [targetApkName].
     */
    fun findChecksumAsset(
        assets: List<GitHubReleaseAsset>,
        targetApkName: String
    ): GitHubReleaseAsset? = findChecksumAssets(assets, targetApkName).firstOrNull()

    /**
     * Returns true when [checksumAssetName] is specifically a dedicated checksum file for [targetApkName]
     * (e.g. `app-android.apk.sha256`).
     */
    fun isDedicatedChecksumFile(checksumAssetName: String, targetApkName: String): Boolean {
        val checksumLower = extractFileName(checksumAssetName).lowercase(Locale.ROOT)
        val targetLower = extractFileName(targetApkName).lowercase(Locale.ROOT)
        return checksumLower == "$targetLower.sha256" ||
            checksumLower == "$targetLower.sha256sum" ||
            checksumLower == "$targetLower.sha256.txt"
    }

    /**
     * Parses a checksum file (`checksums.txt`, `SHA256SUMS`, `*.sha256`) and returns the normalized
     * 64-char lowercase SHA-256 hex matching [targetFileName], or `null` if not found / invalid.
     * Strips UTF-8 BOM (`'\uFEFF'`) safely before parsing.
     */
    fun parseChecksumFile(
        content: String,
        targetFileName: String,
        isDedicatedAssetFile: Boolean = false
    ): String? {
        val targetBase = extractFileName(targetFileName)
        if (targetBase.isBlank()) return null

        val cleanedContent = content.removePrefix("\uFEFF")
        val lines = cleanedContent
            .lineSequence()
            .map { it.trimBomAndWhitespace() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()

        if (lines.isEmpty()) return null

        var caseInsensitiveMatch: String? = null

        for (line in lines) {
            val pair = parseChecksumLine(line) ?: continue
            val (hash, entryPath) = pair
            val entryBase = extractFileName(entryPath)
            if (entryBase == targetBase) {
                return hash
            }
            if (caseInsensitiveMatch == null && entryBase.equals(targetBase, ignoreCase = true)) {
                caseInsensitiveMatch = hash
            }
        }

        if (caseInsensitiveMatch != null) return caseInsensitiveMatch

        // Only accept a bare single-hash line when the file is specifically dedicated to this APK.
        if (isDedicatedAssetFile && lines.size == 1) {
            return normalizeSha256(lines.first())
        }

        return null
    }

    private fun String.trimBomAndWhitespace(): String =
        trim { it <= ' ' || it == '\uFEFF' }

    /**
     * Sanitizes a remote APK filename to prevent path traversal or invalid filesystem characters.
     */
    fun sanitizeApkFileName(rawName: String?, fallbackVersionTag: String = "latest"): String {
        val base = extractFileName(rawName.orEmpty())
            .replace("\u0000", "")
            .trim()

        val cleaned = base
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trimStart('.')

        val isSafeBase = cleaned.isNotEmpty() &&
            cleaned != "." &&
            cleaned != ".." &&
            !cleaned.contains("..")

        val candidate = if (isSafeBase) {
            if (cleaned.lowercase(Locale.ROOT).endsWith(".apk")) cleaned else "$cleaned.apk"
        } else {
            val safeTag = fallbackVersionTag
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .trimStart('.')
                .ifBlank { "latest" }
            "xperience-update-$safeTag.apk"
        }

        return candidate.take(128).let {
            if (it.lowercase(Locale.ROOT).endsWith(".apk")) it else "$it.apk"
        }
    }

    private fun parseChecksumLine(line: String): Pair<String, String>? {
        BSD_CHECKSUM_REGEX.matchEntire(line)?.let { match ->
            val file = match.groupValues[1].trim()
            val hash = normalizeSha256(match.groupValues[2]) ?: return null
            return hash to file
        }
        GNU_CHECKSUM_REGEX.matchEntire(line)?.let { match ->
            val hash = normalizeSha256(match.groupValues[1]) ?: return null
            val file = match.groupValues[2].trim().trim('"')
            return hash to file
        }
        KV_CHECKSUM_REGEX.matchEntire(line)?.let { match ->
            val file = match.groupValues[1].trim().trim('"')
            val hash = normalizeSha256(match.groupValues[2]) ?: return null
            return hash to file
        }
        return null
    }

    private fun extractFileName(path: String): String {
        val normalized = path.trim().replace('\\', '/')
        return normalized.substringAfterLast('/').trim()
    }

    private fun canonicalAbi(abi: String): String? {
        val lower = abi.trim().lowercase(Locale.ROOT)
        return when {
            lower in ABI_ARM64_TOKENS -> "arm64-v8a"
            lower in ABI_ARMV7_TOKENS -> "armeabi-v7a"
            lower in ABI_X86_64_TOKENS -> "x86_64"
            lower in ABI_X86_TOKENS -> "x86"
            else -> null
        }
    }

    private fun detectAbisInFilename(lowerName: String): Set<String> {
        val found = linkedSetOf<String>()
        if (containsAnyToken(lowerName, ABI_ARM64_TOKENS)) found.add("arm64-v8a")
        if (containsAnyToken(lowerName, ABI_ARMV7_TOKENS)) found.add("armeabi-v7a")
        val isX8664 = containsAnyToken(lowerName, ABI_X86_64_TOKENS)
        if (isX8664) found.add("x86_64")
        if (!isX8664 && containsAnyToken(lowerName, ABI_X86_TOKENS)) found.add("x86")
        return found
    }

    private fun containsAnyToken(lowerName: String, tokens: List<String>): Boolean =
        tokens.any { containsToken(lowerName, it) }

    private fun containsToken(lowerName: String, token: String): Boolean {
        val escaped = Regex.escape(token)
        return Regex("(^|[^a-z0-9])$escaped([^a-z0-9]|$)").containsMatchIn(lowerName)
    }
}
