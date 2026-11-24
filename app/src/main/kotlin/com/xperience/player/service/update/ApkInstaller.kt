package com.xperience.player.service.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File
import java.security.MessageDigest

/**
 * Native Android APK installer helper using [FileProvider] and API 26+ install-unknown-apps checks.
 */
object ApkInstaller {

    const val APK_MIME_TYPE = "application/vnd.android.package-archive"

    fun fileProviderAuthority(context: Context): String = "${context.packageName}.fileprovider"

    /**
     * Checks whether this app is currently permitted to request package installations
     * (Android 8.0 / API 26+; app minSdk is 33).
     */
    fun canRequestPackageInstalls(context: Context): Boolean {
        return context.packageManager.canRequestPackageInstalls()
    }

    /**
     * Builds the system Settings intent directing the user to grant "Install unknown apps"
     * for this package.
     */
    fun createUnknownAppSourcesSettingsIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            "package:${context.packageName}".toUri()
        )
    }

    /**
     * Exposes a verified APK file inside `cacheDir/updates/` as a content URI via [FileProvider].
     */
    fun getApkContentUri(context: Context, apkFile: File): Uri {
        return FileProvider.getUriForFile(
            context,
            fileProviderAuthority(context),
            apkFile
        )
    }

    /**
     * Creates the native `ACTION_VIEW` intent to launch Android's package installer for [apkFile].
     */
    fun createInstallApkIntent(context: Context, apkFile: File): Intent {
        val contentUri = getApkContentUri(context, apkFile)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Inspects [apkFile] and verifies that its package name and signing certificate SHA-256
     * digest(s) match the currently installed package. Fails closed on any mismatch or error.
     */
    fun verifyApkMatchesInstalledPackage(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists() || !apkFile.isFile) return false
        return runCatching {
            val pm = context.packageManager
            val flags = PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            val archiveInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, flags) ?: return false
            if (archiveInfo.packageName != context.packageName) return false

            val installedInfo = pm.getPackageInfo(context.packageName, flags)
            val archiveDigests = extractSigningSha256Digests(archiveInfo)
            val installedDigests = extractSigningSha256Digests(installedInfo)
            archiveDigests.isNotEmpty() && archiveDigests == installedDigests
        }.getOrDefault(false)
    }

    private fun extractSigningSha256Digests(packageInfo: PackageInfo): Set<String> {
        val signingInfo = packageInfo.signingInfo ?: return emptySet()
        val signatures = if (signingInfo.hasMultipleSigners()) {
            signingInfo.apkContentsSigners
        } else {
            signingInfo.signingCertificateHistory
        } ?: return emptySet()
        val digest = MessageDigest.getInstance("SHA-256")
        return signatures.mapNotNull { sig ->
            val bytes = sig?.toByteArray() ?: return@mapNotNull null
            digest.digest(bytes).joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    /**
     * Launches Android's package installer for a verified local [apkFile].
     * Returns `true` if the installer activity was started cleanly, `false` otherwise.
     */
    fun launchInstall(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists() || !apkFile.isFile) return false
        if (!verifyApkMatchesInstalledPackage(context, apkFile)) return false
        return runCatching {
            val intent = createInstallApkIntent(context, apkFile)
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
