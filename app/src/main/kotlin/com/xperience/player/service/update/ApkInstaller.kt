package com.xperience.player.service.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File

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
     * Launches Android's package installer for a verified local [apkFile].
     * Returns `true` if the installer activity was started cleanly, `false` otherwise.
     */
    fun launchInstall(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists() || !apkFile.isFile) return false
        return runCatching {
            val intent = createInstallApkIntent(context, apkFile)
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
