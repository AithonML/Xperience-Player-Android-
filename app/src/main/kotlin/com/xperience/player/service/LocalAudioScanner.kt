package com.xperience.player.service

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.xperience.player.BuildConfig
import android.os.Build
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.xperience.player.domain.model.AlbumItem
import com.xperience.player.domain.model.ArtistItem
import com.xperience.player.domain.model.FolderDepthMode
import com.xperience.player.domain.model.FolderListType
import com.xperience.player.domain.model.FolderScanRule
import com.xperience.player.domain.model.LocalScanFilterConfig
import com.xperience.player.domain.model.ScanFilterMode
import com.xperience.player.domain.model.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class LocalAudioScanner(private val context: Context) {

    private val prefs = context.getSharedPreferences("xperience_catalog_prefs", Context.MODE_PRIVATE)

    fun getSavedLibraryFolders(): Set<String> {
        return prefs.getStringSet("saved_library_folders", emptySet()) ?: emptySet()
    }

    fun saveLibraryFolder(uriString: String) {
        val current = getSavedLibraryFolders().toMutableSet()
        current.add(uriString)
        prefs.edit().putStringSet("saved_library_folders", current).apply()
    }

    fun removeLibraryFolder(uriString: String) {
        val current = getSavedLibraryFolders().toMutableSet()
        current.remove(uriString)
        prefs.edit().putStringSet("saved_library_folders", current).apply()
    }

    fun getScanFilterConfig(): LocalScanFilterConfig {
        val mode = ScanFilterMode.fromId(prefs.getString(KEY_SCAN_FILTER_MODE, ScanFilterMode.BLACKLIST.id))
        val globalDeep = prefs.getBoolean(KEY_GLOBAL_DEEP_SCAN, true)
        val rawRules = prefs.getString(KEY_FOLDER_SCAN_RULES, null)
        val rules = mutableListOf<FolderScanRule>()
        if (!rawRules.isNullOrBlank()) {
            runCatching {
                val arr = JSONArray(rawRules)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val uri = obj.optString("uri", "").trim()
                    if (uri.isBlank()) continue
                    rules.add(
                        FolderScanRule(
                            uriOrPath = uri,
                            displayName = obj.optString("name", LocalScanFilterEvaluator.formatFolderDisplayName(uri)),
                            listType = FolderListType.fromId(obj.optString("type", FolderListType.BLACKLIST.id)),
                            depthMode = FolderDepthMode.fromId(obj.optString("depth", FolderDepthMode.INHERIT_GLOBAL.id))
                        )
                    )
                }
            }
        }
        return LocalScanFilterConfig(
            filterMode = mode,
            globalDeepScan = globalDeep,
            rules = rules
        )
    }

    fun saveScanFilterConfig(config: LocalScanFilterConfig) {
        val arr = JSONArray()
        for (rule in config.rules) {
            arr.put(
                JSONObject().apply {
                    put("uri", rule.uriOrPath)
                    put("name", rule.displayName)
                    put("type", rule.listType.id)
                    put("depth", rule.depthMode.id)
                }
            )
        }
        prefs.edit()
            .putString(KEY_SCAN_FILTER_MODE, config.filterMode.id)
            .putBoolean(KEY_GLOBAL_DEEP_SCAN, config.globalDeepScan)
            .putString(KEY_FOLDER_SCAN_RULES, arr.toString())
            .apply()
    }

    companion object {
        private const val KEY_SCAN_FILTER_MODE = "local_scan_filter_mode"
        private const val KEY_GLOBAL_DEEP_SCAN = "local_scan_global_deep"
        private const val KEY_FOLDER_SCAN_RULES = "local_scan_folder_rules_json"
    }

    /**
     * Scans all audio files indexed in MediaStore (Music folder, Downloads, SD card, etc.),
     * applying the configured Blacklist/Whitelist and shallow/deep rules.
     */
    suspend fun scanMediaStoreTracks(
        filterConfig: LocalScanFilterConfig = getScanFilterConfig()
    ): List<TrackItem> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<TrackItem>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val nameCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                val dateAddedCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                val dateModifiedCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)

                val dirEntriesCache = HashMap<String, Set<String>>()
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val rawTitle = cursor.getString(titleCol) ?: "Unknown Song"
                    var rawArtist = cursor.getString(artistCol) ?: "Unknown Artist"
                    val album = cursor.getString(albumCol) ?: "Unknown Album"
                    val duration = cursor.getLong(durCol)
                    val albumId = cursor.getLong(albumIdCol)
                    val filePath = if (dataCol >= 0) cursor.getString(dataCol) else null
                    val parentPath = filePath?.let { runCatching { File(it).parent }.getOrNull() }
                    if (!LocalScanFilterEvaluator.shouldIncludeFile(parentPath, filterConfig)) {
                        continue
                    }
                    val displayName = if (nameCol >= 0) cursor.getString(nameCol) else null
                    val rawDateAddedSec = if (dateAddedCol >= 0) cursor.getLong(dateAddedCol) else 0L
                    val rawDateModifiedSec = if (dateModifiedCol >= 0) cursor.getLong(dateModifiedCol) else 0L

                    // 1. Separate "Title - Artist" if artist is unknown or generic
                    var effectiveTitle = rawTitle
                    var effectiveArtist = rawArtist

                    val isArtistUnknown = rawArtist.isBlank() ||
                            rawArtist.contains("<unknown>", ignoreCase = true) ||
                            rawArtist.equals("Unknown Artist", ignoreCase = true)

                    val candidateName = when {
                        rawTitle.contains(" - ") -> rawTitle
                        displayName != null && displayName.contains(" - ") -> displayName.substringBeforeLast(".")
                        else -> null
                    }

                    if (candidateName != null && (isArtistUnknown || rawTitle.contains(" - "))) {
                        val parts = candidateName.split(" - ", limit = 2)
                        effectiveTitle = parts[0].trim()
                        if (isArtistUnknown || effectiveArtist.isBlank()) {
                            effectiveArtist = parts[1].trim()
                        }
                    }

                    if (effectiveArtist.contains("<unknown>", ignoreCase = true)) {
                        effectiveArtist = "Unknown Artist"
                    }

                    // 2. Discover companion album cover (.jpg sidecar next to audio file) using cached dir listing
                    var resolvedArtworkUri: String? = null
                    var fileLastModifiedMs = 0L
                    if (!filePath.isNullOrBlank()) {
                        try {
                            val audioFile = File(filePath)
                            if (rawDateAddedSec <= 0L && rawDateModifiedSec <= 0L) {
                                fileLastModifiedMs = audioFile.lastModified()
                            }
                            val parentDir = audioFile.parentFile
                            if (parentDir != null) {
                                val dirKey = parentDir.absolutePath
                                val dirEntries = dirEntriesCache.getOrPut(dirKey) {
                                    parentDir.list()?.toHashSet() ?: emptySet()
                                }
                                if (dirEntries.isNotEmpty()) {
                                    val baseName = audioFile.nameWithoutExtension
                                    val candidateNames = listOf(
                                        "$baseName.jpg",
                                        "$baseName.png",
                                        "$effectiveTitle - $effectiveArtist.jpg",
                                        "$effectiveTitle.jpg",
                                        "cover.jpg",
                                        "cover.png",
                                        "folder.jpg"
                                    )
                                    val foundImg = candidateNames
                                        .firstOrNull { it in dirEntries }
                                        ?.let { File(parentDir, it) }
                                        ?.takeIf { it.length() > 1024 }
                                    if (foundImg != null) {
                                        resolvedArtworkUri = foundImg.toURI().toString()
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    if (resolvedArtworkUri == null && albumId > 0) {
                        resolvedArtworkUri = "content://media/external/audio/albumart/$albumId"
                    }

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    val dateAddedMs = resolveMediaStoreDateAddedMs(
                        dateAddedSeconds = rawDateAddedSec,
                        dateModifiedSeconds = rawDateModifiedSec,
                        fileLastModifiedMs = fileLastModifiedMs
                    )

                    tracks.add(
                        TrackItem(
                            id = "local_media_$id",
                            title = effectiveTitle,
                            artist = effectiveArtist,
                            album = album,
                            durationMs = duration,
                            audioUrl = contentUri.toString(),
                            artworkUri = resolvedArtworkUri,
                            dateAddedMs = dateAddedMs
                        )
                    )
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) runCatching { Log.w("LocalAudioScanner", "Local audio scan failed", e) }
        }

        tracks
    }

    /**
     * Scans a specific tree directory selected by the user via Storage Access Framework,
     * respecting shallow (root folder only) vs deep (recursive subfolders) scanning.
     */
    suspend fun scanDocumentTree(
        treeUri: Uri,
        deepScan: Boolean = LocalScanFilterEvaluator.shouldTraverseSubfoldersForTree(treeUri.toString(), getScanFilterConfig())
    ): List<TrackItem> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<TrackItem>()
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext tracks

        fun traverse(dir: DocumentFile, depth: Int) {
            val files = dir.listFiles()
            for (file in files) {
                if (file.isDirectory) {
                    if (deepScan) {
                        traverse(file, depth + 1)
                    }
                } else {
                    val name = file.name ?: ""
                    val isAudio = name.endsWith(".mp3", ignoreCase = true) ||
                            name.endsWith(".m4a", ignoreCase = true) ||
                            name.endsWith(".flac", ignoreCase = true) ||
                            name.endsWith(".wav", ignoreCase = true) ||
                            name.endsWith(".ogg", ignoreCase = true) ||
                            name.endsWith(".aac", ignoreCase = true)

                    if (isAudio) {
                        val docLastModifiedMs = runCatching { file.lastModified() }.getOrDefault(0L).coerceAtLeast(0L)
                        extractTrackFromUri(
                            fileUri = file.uri,
                            fallbackName = name,
                            parentDoc = dir,
                            dateAddedMs = docLastModifiedMs
                        )?.let { tracks.add(it) }
                    }
                }
            }
        }

        traverse(rootDoc, 0)
        tracks
    }

    private fun extractTrackFromUri(
        fileUri: Uri,
        fallbackName: String,
        parentDoc: DocumentFile? = null,
        dateAddedMs: Long = 0L
    ): TrackItem? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, fileUri)
            val rawTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() } ?: fallbackName.substringBeforeLast(".")
            var rawArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
            val rawAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() } ?: "Unknown Album"
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L

            var effectiveTitle = rawTitle
            var effectiveArtist = rawArtist

            val isArtistUnknown = rawArtist.isBlank() ||
                    rawArtist.contains("<unknown>", ignoreCase = true) ||
                    rawArtist.equals("Unknown Artist", ignoreCase = true)

            val cleanFallback = fallbackName.substringBeforeLast(".")
            val candidateName = when {
                rawTitle.contains(" - ") -> rawTitle
                cleanFallback.contains(" - ") -> cleanFallback
                else -> null
            }

            if (candidateName != null && (isArtistUnknown || rawTitle.contains(" - "))) {
                val parts = candidateName.split(" - ", limit = 2)
                effectiveTitle = parts[0].trim()
                if (isArtistUnknown || effectiveArtist.isBlank()) {
                    effectiveArtist = parts[1].trim()
                }
            }

            if (effectiveArtist.contains("<unknown>", ignoreCase = true)) {
                effectiveArtist = "Unknown Artist"
            }

            // Extract embedded artwork if available
            val embeddedArt = retriever.embeddedPicture
            var coverUri: String? = null
            if (embeddedArt != null && embeddedArt.isNotEmpty()) {
                val cacheFile = File(context.cacheDir, "cover_${fileUri.hashCode()}.jpg")
                if (!cacheFile.exists()) {
                    FileOutputStream(cacheFile).use { it.write(embeddedArt) }
                }
                coverUri = cacheFile.toURI().toString()
            }

            // If no embedded cover, check for companion cover file in parent folder
            if (coverUri == null && parentDoc != null) {
                val baseName = fallbackName.substringBeforeLast(".")
                val imgNames = listOf(
                    "$baseName.jpg",
                    "$baseName.png",
                    "$effectiveTitle - $effectiveArtist.jpg",
                    "cover.jpg",
                    "folder.jpg"
                )
                for (imgName in imgNames) {
                    val imgDoc = parentDoc.findFile(imgName)
                    if (imgDoc != null && imgDoc.isFile && imgDoc.length() > 1024) {
                        coverUri = imgDoc.uri.toString()
                        break
                    }
                }
            }

            TrackItem(
                id = "saf_${fileUri.hashCode()}",
                title = effectiveTitle,
                artist = effectiveArtist,
                album = rawAlbum,
                durationMs = durationMs,
                audioUrl = fileUri.toString(),
                artworkUri = coverUri,
                dateAddedMs = dateAddedMs
            )
        } catch (e: Exception) {
            val cleanFallback = fallbackName.substringBeforeLast(".")
            var title = cleanFallback
            var artist = "Unknown Artist"
            if (cleanFallback.contains(" - ")) {
                val parts = cleanFallback.split(" - ", limit = 2)
                title = parts[0].trim()
                artist = parts[1].trim()
            }
            TrackItem(
                id = "saf_${fileUri.hashCode()}",
                title = title,
                artist = artist,
                album = "Local",
                durationMs = 0L,
                audioUrl = fileUri.toString(),
                dateAddedMs = dateAddedMs
            )
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }
}

internal fun resolveMediaStoreDateAddedMs(
    dateAddedSeconds: Long,
    dateModifiedSeconds: Long,
    fileLastModifiedMs: Long
): Long {
    fun normalizeTimestamp(raw: Long): Long {
        if (raw <= 0L) return 0L
        return if (raw < 100_000_000_000L) raw * 1000L else raw
    }
    val addedMs = normalizeTimestamp(dateAddedSeconds)
    if (addedMs > 0L) return addedMs
    val modifiedMs = normalizeTimestamp(dateModifiedSeconds)
    if (modifiedMs > 0L) return modifiedMs
    return fileLastModifiedMs.coerceAtLeast(0L)
}
