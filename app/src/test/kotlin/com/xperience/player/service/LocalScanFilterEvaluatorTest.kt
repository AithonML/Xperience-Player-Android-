package com.xperience.player.service

import com.xperience.player.domain.model.FolderDepthMode
import com.xperience.player.domain.model.FolderListType
import com.xperience.player.domain.model.FolderScanRule
import com.xperience.player.domain.model.LocalScanFilterConfig
import com.xperience.player.domain.model.ScanFilterMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalScanFilterEvaluatorTest {

    @Test
    fun safTreeUriNormalizesToStandardStoragePath() {
        val safUri = "content://com.android.externalstorage.documents/tree/primary%3AMusic%2FRingtones"
        val normalized = LocalScanFilterEvaluator.normalizeFolderPath(safUri)
        assertEquals("/storage/emulated/0/music/ringtones", normalized)
    }

    @Test
    fun blacklistModeExcludesMatchedFoldersRespectingShallowAndDeep() {
        val deepRule = FolderScanRule(
            uriOrPath = "/storage/emulated/0/WhatsApp/Media",
            displayName = "Media",
            listType = FolderListType.BLACKLIST,
            depthMode = FolderDepthMode.INHERIT_GLOBAL
        )
        val shallowRule = FolderScanRule(
            uriOrPath = "/storage/emulated/0/Music",
            displayName = "Music",
            listType = FolderListType.BLACKLIST,
            depthMode = FolderDepthMode.SHALLOW
        )
        val config = LocalScanFilterConfig(
            filterMode = ScanFilterMode.BLACKLIST,
            globalDeepScan = true,
            rules = listOf(deepRule, shallowRule)
        )

        // Deep blacklist excludes folder and subfolders
        assertFalse(LocalScanFilterEvaluator.shouldIncludeFile("/storage/emulated/0/WhatsApp/Media", config))
        assertFalse(LocalScanFilterEvaluator.shouldIncludeFile("/storage/emulated/0/WhatsApp/Media/VoiceNotes", config))

        // Shallow blacklist excludes ONLY exact folder, NOT subfolders
        assertFalse(LocalScanFilterEvaluator.shouldIncludeFile("/storage/emulated/0/Music", config))
        assertTrue(LocalScanFilterEvaluator.shouldIncludeFile("/storage/emulated/0/Music/Albums/Rock", config))

        // Unlisted folder is included
        assertTrue(LocalScanFilterEvaluator.shouldIncludeFile("/storage/emulated/0/Downloads", config))
    }

    @Test
    fun whitelistModeIncludesOnlySelectedFoldersWithPerFolderDepthOverride() {
        val shallowGlobalWhitelist = FolderScanRule(
            uriOrPath = "/storage/emulated/0/Music/Singles",
            displayName = "Singles",
            listType = FolderListType.WHITELIST,
            depthMode = FolderDepthMode.INHERIT_GLOBAL
        )
        val deepOverrideWhitelist = FolderScanRule(
            uriOrPath = "/storage/emulated/0/Music/Albums",
            displayName = "Albums",
            listType = FolderListType.WHITELIST,
            depthMode = FolderDepthMode.DEEP
        )
        val config = LocalScanFilterConfig(
            filterMode = ScanFilterMode.WHITELIST,
            globalDeepScan = false, // shallow globally
            rules = listOf(shallowGlobalWhitelist, deepOverrideWhitelist)
        )

        // Shallow global rule matches root only
        assertTrue(LocalScanFilterEvaluator.shouldIncludeFile("/storage/emulated/0/Music/Singles", config))
        assertFalse(LocalScanFilterEvaluator.shouldIncludeFile("/storage/emulated/0/Music/Singles/Sub", config))

        // Deep per-folder override matches root AND subfolders
        assertTrue(LocalScanFilterEvaluator.shouldIncludeFile("/storage/emulated/0/Music/Albums", config))
        assertTrue(LocalScanFilterEvaluator.shouldIncludeFile("/storage/emulated/0/Music/Albums/DaftPunk/RAM", config))

        // Non-whitelisted folder excluded
        assertFalse(LocalScanFilterEvaluator.shouldIncludeFile("/storage/emulated/0/Downloads", config))
    }

    @Test
    fun allModeAlwaysIncludesEveryFolder() {
        val config = LocalScanFilterConfig(
            filterMode = ScanFilterMode.ALL,
            globalDeepScan = false,
            rules = listOf(
                FolderScanRule("/storage/emulated/0/Music", "Music", FolderListType.BLACKLIST, FolderDepthMode.DEEP)
            )
        )
        assertTrue(LocalScanFilterEvaluator.shouldIncludeFile("/storage/emulated/0/Music", config))
    }
}
