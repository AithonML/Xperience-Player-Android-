package com.xperience.player.domain.model

/**
 * Global filtering mode for local audio library scanning.
 */
enum class ScanFilterMode(val id: String, val displayName: String) {
    ALL("all", "All Folders"),
    BLACKLIST("blacklist", "Blacklist"),
    WHITELIST("whitelist", "Whitelist");

    companion object {
        fun fromId(id: String?): ScanFilterMode {
            return entries.firstOrNull {
                it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true)
            } ?: BLACKLIST
        }
    }
}

/**
 * Whether a folder rule belongs to the Blacklist (exclude) or Whitelist (include only).
 */
enum class FolderListType(val id: String, val displayName: String) {
    BLACKLIST("blacklist", "Blacklisted"),
    WHITELIST("whitelist", "Whitelisted");

    companion object {
        fun fromId(id: String?): FolderListType {
            return entries.firstOrNull {
                it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true)
            } ?: BLACKLIST
        }
    }
}

/**
 * Controls subfolder scanning depth globally or per individual folder.
 */
enum class FolderDepthMode(val id: String, val displayName: String) {
    INHERIT_GLOBAL("inherit", "Global"),
    SHALLOW("shallow", "Shallow"),
    DEEP("deep", "Deep");

    companion object {
        fun fromId(id: String?): FolderDepthMode {
            return entries.firstOrNull {
                it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true)
            } ?: INHERIT_GLOBAL
        }
    }
}

/**
 * Individual folder rule with its Blacklist/Whitelist assignment and shallow/deep override.
 */
data class FolderScanRule(
    val uriOrPath: String,
    val displayName: String,
    val listType: FolderListType,
    val depthMode: FolderDepthMode = FolderDepthMode.INHERIT_GLOBAL
) {
    fun effectiveDeepScan(globalDeepScan: Boolean): Boolean {
        return when (depthMode) {
            FolderDepthMode.INHERIT_GLOBAL -> globalDeepScan
            FolderDepthMode.SHALLOW -> false
            FolderDepthMode.DEEP -> true
        }
    }
}

/**
 * Complete persisted configuration for Local Scan Blacklist/Whitelist and shallow/deep recursion.
 */
data class LocalScanFilterConfig(
    val filterMode: ScanFilterMode = ScanFilterMode.BLACKLIST,
    val globalDeepScan: Boolean = true,
    val rules: List<FolderScanRule> = emptyList()
) {
    val blacklistRules: List<FolderScanRule>
        get() = rules.filter { it.listType == FolderListType.BLACKLIST }

    val whitelistRules: List<FolderScanRule>
        get() = rules.filter { it.listType == FolderListType.WHITELIST }
}
