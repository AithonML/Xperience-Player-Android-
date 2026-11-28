package com.xperience.player.service

import com.xperience.player.domain.model.FolderDepthMode
import com.xperience.player.domain.model.FolderListType
import com.xperience.player.domain.model.FolderScanRule
import com.xperience.player.domain.model.LocalScanFilterConfig
import com.xperience.player.domain.model.ScanFilterMode
import java.util.Locale

/**
 * Pure evaluator for Local Scan Blacklist/Whitelist and global/per-folder Shallow vs Deep rules.
 */
object LocalScanFilterEvaluator {

    /**
     * Normalizes a filesystem path or decoded SAF tree URI into a canonical lowercase path
     * without trailing slashes for reliable folder prefix/equality comparison.
     */
    fun normalizeFolderPath(rawPathOrUri: String): String {
        var s = rawPathOrUri.trim()
        if (s.isBlank()) return ""
        if (s.startsWith("content://", ignoreCase = true)) {
            val decoded = runCatching { java.net.URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
            val afterTree = decoded.substringAfter("/tree/", decoded)
            s = when {
                afterTree.contains("primary:", ignoreCase = true) -> {
                    val rel = afterTree.substringAfter("primary:").trim('/')
                    if (rel.isBlank()) "/storage/emulated/0" else "/storage/emulated/0/$rel"
                }
                afterTree.contains(':') -> {
                    val vol = afterTree.substringBefore(':').substringAfterLast('/')
                    val rel = afterTree.substringAfter(':').trim('/')
                    if (rel.isBlank()) "/storage/$vol" else "/storage/$vol/$rel"
                }
                else -> afterTree
            }
        }
        return s.replace('\\', '/')
            .replace(Regex("/+"), "/")
            .trimEnd('/')
            .lowercase(Locale.ROOT)
    }

    /**
     * Returns true if [fileParentDir] matches [rule] respecting shallow (exact folder only)
     * vs deep (folder + any nested subfolder) scanning.
     */
    fun matchesRule(
        fileParentDir: String,
        rule: FolderScanRule,
        globalDeepScan: Boolean
    ): Boolean {
        val normParent = normalizeFolderPath(fileParentDir)
        val normRule = normalizeFolderPath(rule.uriOrPath)
        if (normParent.isBlank() || normRule.isBlank()) return false

        val isDeep = rule.effectiveDeepScan(globalDeepScan)
        return if (isDeep) {
            normParent == normRule || normParent.startsWith("$normRule/")
        } else {
            normParent == normRule
        }
    }

    /**
     * Evaluates whether a MediaStore audio file located in [fileParentDir] should be included
     * in the local music library according to [config].
     */
    fun shouldIncludeFile(
        fileParentDir: String?,
        config: LocalScanFilterConfig
    ): Boolean {
        return when (config.filterMode) {
            ScanFilterMode.ALL -> true
            ScanFilterMode.BLACKLIST -> {
                val blacklist = config.blacklistRules
                if (blacklist.isEmpty() || fileParentDir.isNullOrBlank()) return true
                blacklist.none { rule -> matchesRule(fileParentDir, rule, config.globalDeepScan) }
            }
            ScanFilterMode.WHITELIST -> {
                val whitelist = config.whitelistRules
                if (whitelist.isEmpty()) return false
                if (fileParentDir.isNullOrBlank()) return false
                whitelist.any { rule -> matchesRule(fileParentDir, rule, config.globalDeepScan) }
            }
        }
    }

    /**
     * Computes effective deep-scan flag for a SAF DocumentTree root URI.
     */
    fun shouldTraverseSubfoldersForTree(
        treeUriString: String,
        config: LocalScanFilterConfig
    ): Boolean {
        val normTree = normalizeFolderPath(treeUriString)
        val matchingRule = config.rules.firstOrNull {
            normalizeFolderPath(it.uriOrPath) == normTree
        }
        return matchingRule?.effectiveDeepScan(config.globalDeepScan) ?: config.globalDeepScan
    }

    fun formatFolderDisplayName(uriOrPath: String): String {
        if (uriOrPath.isBlank()) return "Folder"
        val decoded = runCatching { java.net.URLDecoder.decode(uriOrPath, "UTF-8") }.getOrDefault(uriOrPath)
        val afterColon = decoded.substringAfterLast(':').ifBlank { decoded }
        return afterColon.trimEnd('/').substringAfterLast('/').ifBlank { afterColon }
    }
}
