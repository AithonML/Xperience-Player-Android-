package com.xperience.player.domain.model.update

import java.io.File
import java.math.BigInteger

/**
 * Semantic Versioning 2.0.0 representation with pre-release precedence support.
 */
data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: List<String> = emptyList(),
    val buildMetadata: String? = null
) : Comparable<SemVer> {

    val isPreRelease: Boolean
        get() = preRelease.isNotEmpty()

    override fun compareTo(other: SemVer): Int {
        major.compareTo(other.major).takeIf { it != 0 }?.let { return it }
        minor.compareTo(other.minor).takeIf { it != 0 }?.let { return it }
        patch.compareTo(other.patch).takeIf { it != 0 }?.let { return it }

        // Per SemVer 2.0.0 §11.3: a normal version has higher precedence than a pre-release version.
        val thisHasPre = preRelease.isNotEmpty()
        val otherHasPre = other.preRelease.isNotEmpty()
        if (!thisHasPre && !otherHasPre) return 0
        if (!thisHasPre) return 1
        if (!otherHasPre) return -1

        val count = minOf(preRelease.size, other.preRelease.size)
        for (i in 0 until count) {
            val a = preRelease[i]
            val b = other.preRelease[i]
            if (a == b) continue

            val aNum = a.toNumericIdentifierOrNull()
            val bNum = b.toNumericIdentifierOrNull()
            val cmp = when {
                aNum != null && bNum != null -> aNum.compareTo(bNum)
                // Per SemVer 2.0.0 §11.4.3: numeric identifiers always have lower precedence than non-numeric.
                aNum != null -> -1
                bNum != null -> 1
                else -> a.compareTo(b)
            }
            if (cmp != 0) return cmp
        }

        // Per SemVer 2.0.0 §11.4.4: a larger set of pre-release fields has higher precedence.
        return preRelease.size.compareTo(other.preRelease.size)
    }

    fun isNewerThan(other: SemVer): Boolean = compareTo(other) > 0

    override fun toString(): String = buildString {
        append(major).append('.').append(minor).append('.').append(patch)
        if (preRelease.isNotEmpty()) {
            append('-').append(preRelease.joinToString("."))
        }
        if (!buildMetadata.isNullOrBlank()) {
            append('+').append(buildMetadata)
        }
    }

    private fun String.toNumericIdentifierOrNull(): BigInteger? {
        if (isEmpty() || any { it !in '0'..'9' }) return null
        return runCatching { BigInteger(this) }.getOrNull()
    }
}

data class GitHubReleaseAsset(
    val name: String,
    val browserDownloadUrl: String,
    val size: Long,
    val contentType: String = "",
    val digest: String? = null
)

data class GitHubRelease(
    val tagName: String,
    val name: String,
    val body: String,
    val htmlUrl: String,
    val draft: Boolean,
    val prerelease: Boolean,
    val assets: List<GitHubReleaseAsset>
)

data class UpdateCandidate(
    val tagName: String,
    val version: SemVer,
    val releaseTitle: String,
    val releaseNotes: String,
    val apkAsset: GitHubReleaseAsset,
    val expectedSha256: String?,
    val checksumAssets: List<GitHubReleaseAsset> = emptyList(),
    val htmlUrl: String
) {
    val checksumAsset: GitHubReleaseAsset?
        get() = checksumAssets.firstOrNull()
}

enum class UpdateErrorKind {
    NETWORK,
    INVALID_URL,
    NO_APK_ASSET,
    MISSING_CHECKSUM,
    INTEGRITY_MISMATCH,
    INSTALLER_LAUNCH_FAILED,
    UNKNOWN
}

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val candidate: UpdateCandidate) : UpdateCheckResult
    data class UpToDate(val currentVersion: String, val latestTag: String?) : UpdateCheckResult
    data class NoReleasesPublished(val currentVersion: String) : UpdateCheckResult
    data class Error(val errorKind: UpdateErrorKind, val detail: String? = null) : UpdateCheckResult
}

data class UpdateDownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val percent: Int?
)

sealed interface UpdateDownloadResult {
    data class Success(val apkFile: File, val sha256Hex: String) : UpdateDownloadResult
    data class Error(val errorKind: UpdateErrorKind, val detail: String? = null) : UpdateDownloadResult
}

sealed interface ManualCheckFeedback {
    data class UpToDate(val versionDisplay: String) : ManualCheckFeedback
    data object NoReleasesPublished : ManualCheckFeedback
    data class Error(val errorKind: UpdateErrorKind, val detail: String? = null) : ManualCheckFeedback
}

sealed interface UpdateInstallStage {
    data object Ready : UpdateInstallStage
    data class Downloading(
        val percent: Int?,
        val bytesDownloaded: Long = 0L,
        val totalBytes: Long = -1L
    ) : UpdateInstallStage
    data object Verifying : UpdateInstallStage
    data class AwaitingUnknownSourcesPermission(val apkFile: File) : UpdateInstallStage
    data class Installing(val apkFile: File) : UpdateInstallStage
    data class Error(val errorKind: UpdateErrorKind, val detail: String? = null) : UpdateInstallStage
}

data class UpdateUiState(
    val currentVersionDisplay: String = "",
    val isChecking: Boolean = false,
    val manualCheckFeedback: ManualCheckFeedback? = null,
    val availableUpdate: UpdateCandidate? = null,
    val isDialogOpen: Boolean = false,
    val installStage: UpdateInstallStage = UpdateInstallStage.Ready
) {
    val isBusyDownloadingOrVerifying: Boolean
        get() = installStage is UpdateInstallStage.Downloading || installStage is UpdateInstallStage.Verifying
}
