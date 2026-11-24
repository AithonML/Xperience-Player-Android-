package com.xperience.player.data

import android.content.Context
import android.util.Log
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.domain.model.UserPlaylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages persistent storage and reactive state for user playlists on disk (JSON format).
 * Matches the PC port architecture (UserPlaylists_v1) for seamless cross-platform
 * playlist compatibility between Android and Windows.
 */
class PlaylistRepository(private val context: Context) {

    companion object {
        private const val TAG = "PlaylistRepository"
        private const val FILE_NAME = "xperience_playlists.json"

        fun formatIsoTimestamp(date: Date = Date()): String =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(date)

        fun normalizeYouTubePlaylistId(rawId: String): String {
            val trimmed = rawId.trim()
            return if (trimmed.startsWith("VL") && trimmed.length > 2) {
                trimmed.removePrefix("VL")
            } else {
                trimmed
            }
        }

        internal fun isLocalCustomCoverPath(path: String): Boolean {
            val trimmed = path.trim()
            return trimmed.startsWith("content://", ignoreCase = true) ||
                trimmed.startsWith("file://", ignoreCase = true) ||
                trimmed.startsWith("/")
        }

        /**
         * Pure helper for upserting an imported YouTube Music playlist into an existing playlist list.
         * Normalizes [externalPlaylistId] (stripping leading "VL"), updates an existing matching entry
         * in-place while preserving any local custom gallery cover (`content://` or `file://`), or
         * prepends a new playlist when not yet present.
         */
        fun upsertImportedYouTubePlaylist(
            existing: List<UserPlaylist>,
            externalPlaylistId: String,
            externalBrowseId: String,
            title: String,
            description: String,
            coverUrl: String,
            tracks: List<TrackItem>,
            accountLabel: String,
            importedAt: String = formatIsoTimestamp()
        ): Pair<List<UserPlaylist>, UserPlaylist> {
            val normalizedId = normalizeYouTubePlaylistId(
                externalPlaylistId.ifBlank { externalBrowseId }
            )
            val normalizedBrowseId = externalBrowseId.trim().ifBlank {
                if (normalizedId.isNotBlank()) "VL$normalizedId" else ""
            }
            val cleanTitle = title.trim().ifBlank { "YouTube Music Playlist" }
            val cleanDescription = description.trim()
            val cleanCoverUrl = coverUrl.trim()
            val cleanAccountLabel = accountLabel.trim()

            val current = existing.toMutableList()
            val existingIdx = if (normalizedId.isNotBlank()) {
                current.indexOfFirst { playlist ->
                    playlist.externalPlaylistId.isNotBlank() &&
                        normalizeYouTubePlaylistId(playlist.externalPlaylistId)
                            .equals(normalizedId, ignoreCase = true)
                }
            } else {
                -1
            }

            val saved = if (existingIdx >= 0) {
                val prior = current[existingIdx]
                val resolvedCover = if (isLocalCustomCoverPath(prior.customCoverImagePath)) {
                    prior.customCoverImagePath
                } else {
                    cleanCoverUrl.ifBlank { prior.customCoverImagePath }
                }
                val updated = prior.copy(
                    title = cleanTitle,
                    description = cleanDescription,
                    customCoverImagePath = resolvedCover,
                    tracks = tracks,
                    source = UserPlaylist.SOURCE_YOUTUBE_MUSIC,
                    externalPlaylistId = normalizedId,
                    externalBrowseId = normalizedBrowseId,
                    sourceAccountLabel = cleanAccountLabel,
                    importedAt = importedAt
                )
                current[existingIdx] = updated
                updated
            } else {
                val created = UserPlaylist(
                    title = cleanTitle,
                    description = cleanDescription,
                    customCoverImagePath = cleanCoverUrl,
                    tracks = tracks,
                    source = UserPlaylist.SOURCE_YOUTUBE_MUSIC,
                    externalPlaylistId = normalizedId,
                    externalBrowseId = normalizedBrowseId,
                    sourceAccountLabel = cleanAccountLabel,
                    importedAt = importedAt
                )
                current.add(0, created)
                created
            }

            return current.toList() to saved
        }

        /**
         * Parses a JSON array string representing playlists (compatible with PC port format
         * and backward-compatible with legacy JSON lacking YouTube Music metadata fields).
         */
        fun parsePlaylistsJson(jsonContent: String): List<UserPlaylist> {
            val list = mutableListOf<UserPlaylist>()
            try {
                // Could be a raw array [...] or a dict containing "UserPlaylists_v1"
                val array = if (jsonContent.trimStart().startsWith("{")) {
                    val root = JSONObject(jsonContent)
                    root.optJSONArray("UserPlaylists_v1") ?: JSONArray()
                } else {
                    JSONArray(jsonContent)
                }

                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val id = obj.optString("id").ifBlank { "pl_$i" }
                    val title = obj.optString("title", "Untitled Playlist")
                    val description = obj.optString("description", "")
                    val customCoverImagePath = obj.optString("customCoverImagePath", "")
                    val createdAt = obj.optString("createdAt", "")
                    val source = obj.optString("source", "")
                    val externalPlaylistId = obj.optString("externalPlaylistId", "")
                    val externalBrowseId = obj.optString("externalBrowseId", "")
                    val sourceAccountLabel = obj.optString("sourceAccountLabel", "")
                    val importedAt = obj.optString("importedAt", "")

                    val tracksArray = obj.optJSONArray("tracks") ?: JSONArray()
                    val tracks = mutableListOf<TrackItem>()
                    for (j in 0 until tracksArray.length()) {
                        val trackObj = tracksArray.optJSONObject(j) ?: continue
                        val trackId = trackObj.optString("id")
                        if (trackId.isBlank()) continue

                        // Parse duration: support both ms Long and "00:03:28" string from PC port
                        var durationMs = trackObj.optLong("durationMs", 0L)
                        if (durationMs == 0L) {
                            val durationStr = trackObj.optString("duration", "")
                            if (durationStr.isNotBlank()) {
                                durationMs = parseTimeStringToMs(durationStr)
                            }
                        }

                        // Artwork: support both artworkUri and coverArtUri/safeCoverArtUri
                        val artwork = trackObj.optString("artworkUri")
                            .ifBlank { trackObj.optString("coverArtUri") }
                            .ifBlank { trackObj.optString("safeCoverArtUri") }
                            .ifBlank { null }

                        // Audio: support both audioUrl and audioSourceUri
                        val audioUrl = trackObj.optString("audioUrl")
                            .ifBlank { trackObj.optString("audioSourceUri") }
                            .ifBlank { null }

                        tracks.add(
                            TrackItem(
                                id = trackId,
                                title = trackObj.optString("title", "Unknown Title"),
                                artist = trackObj.optString("artist", "Unknown Artist"),
                                album = trackObj.optString("album", "Unknown Album"),
                                durationMs = durationMs,
                                artworkUri = artwork,
                                audioUrl = audioUrl,
                                isLiked = trackObj.optBoolean("isLiked", false),
                                isExplicit = trackObj.optBoolean("isExplicit", false),
                                playCount = trackObj.optLong("playCount", 0L)
                            )
                        )
                    }

                    list.add(
                        UserPlaylist(
                            id = id,
                            title = title,
                            description = description,
                            customCoverImagePath = customCoverImagePath,
                            createdAt = createdAt,
                            tracks = tracks,
                            source = source,
                            externalPlaylistId = externalPlaylistId,
                            externalBrowseId = externalBrowseId,
                            sourceAccountLabel = sourceAccountLabel,
                            importedAt = importedAt
                        )
                    )
                }
            } catch (e: Exception) {
                runCatching { Log.e(TAG, "Failed parsing playlists JSON", e) }
            }
            return list
        }

        /**
         * Serializes a list of playlists to formatted JSON for disk persistence.
         */
        fun serializePlaylistsToJson(playlistsList: List<UserPlaylist>): String {
            val jsonArray = JSONArray()
            for (playlist in playlistsList) {
                val obj = JSONObject()
                obj.put("id", playlist.id)
                obj.put("title", playlist.title)
                obj.put("description", playlist.description)
                obj.put("customCoverImagePath", playlist.customCoverImagePath)
                obj.put("createdAt", playlist.createdAt)
                if (playlist.source.isNotBlank()) {
                    obj.put("source", playlist.source)
                }
                if (playlist.externalPlaylistId.isNotBlank()) {
                    obj.put("externalPlaylistId", playlist.externalPlaylistId)
                }
                if (playlist.externalBrowseId.isNotBlank()) {
                    obj.put("externalBrowseId", playlist.externalBrowseId)
                }
                if (playlist.sourceAccountLabel.isNotBlank()) {
                    obj.put("sourceAccountLabel", playlist.sourceAccountLabel)
                }
                if (playlist.importedAt.isNotBlank()) {
                    obj.put("importedAt", playlist.importedAt)
                }

                val tracksArray = JSONArray()
                for (track in playlist.tracks) {
                    val trackObj = JSONObject()
                    trackObj.put("id", track.id)
                    trackObj.put("title", track.title)
                    trackObj.put("artist", track.artist)
                    trackObj.put("album", track.album)
                    trackObj.put("durationMs", track.durationMs)
                    trackObj.put("artworkUri", track.artworkUri ?: "")
                    trackObj.put("audioUrl", track.audioUrl ?: "")
                    trackObj.put("isLiked", track.isLiked)
                    trackObj.put("isExplicit", track.isExplicit)
                    trackObj.put("playCount", track.playCount)
                    tracksArray.put(trackObj)
                }
                obj.put("tracks", tracksArray)
                jsonArray.put(obj)
            }
            return jsonArray.toString(2)
        }

        private fun parseTimeStringToMs(timeStr: String): Long {
            return try {
                val parts = timeStr.split(":")
                when (parts.size) {
                    3 -> {
                        val hrs = parts[0].toLongOrNull() ?: 0L
                        val mins = parts[1].toLongOrNull() ?: 0L
                        val secs = parts[2].toDoubleOrNull() ?: 0.0
                        (hrs * 3600 + mins * 60 + secs.toLong()) * 1000L
                    }
                    2 -> {
                        val mins = parts[0].toLongOrNull() ?: 0L
                        val secs = parts[1].toDoubleOrNull() ?: 0.0
                        (mins * 60 + secs.toLong()) * 1000L
                    }
                    else -> 0L
                }
            } catch (_: Exception) {
                0L
            }
        }
    }

    private val appContext: Context = context.applicationContext
    private val ioScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    private val playlistsFile: File
        get() = File(appContext.filesDir, FILE_NAME)

    private val _playlists = MutableStateFlow<List<UserPlaylist>>(emptyList())
    val playlists: StateFlow<List<UserPlaylist>> = _playlists.asStateFlow()

    init {
        ioScope.launch {
            loadPlaylistsFromDisk()
        }
    }

    /**
     * Loads saved playlists from disk into memory.
     */
    fun loadPlaylistsFromDisk() {
        try {
            val file = playlistsFile
            if (!file.exists()) {
                _playlists.value = emptyList()
                return
            }

            val jsonContent = file.readText(Charsets.UTF_8)
            if (jsonContent.isBlank()) {
                _playlists.value = emptyList()
                return
            }

            val loadedList = parsePlaylistsJson(jsonContent)
            val counterparts = com.xperience.player.service.YouTubeMusicService.loadAtvCounterpartMapSafe(
                com.xperience.player.service.YouTubeMusicService.getAtvCounterpartCacheFile(context)
            )
            val (migratedList, modified) = migratePlaylistsTracks(loadedList, counterparts)
            _playlists.value = migratedList
            if (modified) {
                savePlaylistsToDisk(migratedList)
            }
            Log.d(TAG, "Loaded ${migratedList.size} playlists from disk (migrated=$modified).")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading playlists from disk", e)
        }
    }

    internal fun migratePlaylistsTracks(
        playlists: List<UserPlaylist>,
        counterparts: Map<String, String>
    ): Pair<List<UserPlaylist>, Boolean> {
        var anyModified = false
        val migrated = playlists.map { playlist ->
            val (sanitizedTracks, playlistModified) =
                com.xperience.player.service.YouTubeMusicService.sanitizePersistedTracks(
                    playlist.tracks,
                    counterparts
                )
            if (playlistModified) {
                anyModified = true
                playlist.copy(tracks = sanitizedTracks)
            } else {
                playlist
            }
        }
        return migrated to anyModified
    }

    /**
     * Parses a JSON array string representing playlists (compatible with PC port format).
     */
    fun parsePlaylistsJson(jsonContent: String): List<UserPlaylist> =
        Companion.parsePlaylistsJson(jsonContent)

    /**
     * Imports (or refreshes) a playlist from YouTube Music into local storage.
     * Upserts by normalized externalPlaylistId so re-importing updates tracks & metadata
     * without creating duplicates, while preserving any custom local gallery cover.
     */
    suspend fun importYouTubeMusicPlaylist(
        externalPlaylistId: String,
        externalBrowseId: String,
        title: String,
        description: String,
        coverUrl: String,
        tracks: List<TrackItem>,
        accountLabel: String
    ): UserPlaylist = withContext(Dispatchers.IO) {
        val (updatedList, saved) = upsertImportedYouTubePlaylist(
            existing = _playlists.value,
            externalPlaylistId = externalPlaylistId,
            externalBrowseId = externalBrowseId,
            title = title,
            description = description,
            coverUrl = coverUrl,
            tracks = tracks,
            accountLabel = accountLabel
        )
        _playlists.value = updatedList
        savePlaylistsToDisk(updatedList)
        saved
    }

    /**
     * Creates a new playlist and saves it to disk.
     */
    suspend fun createPlaylist(
        title: String,
        description: String = "",
        customCoverPath: String = "",
        initialTrack: TrackItem? = null
    ): UserPlaylist = withContext(Dispatchers.IO) {
        val cleanTitle = if (title.isBlank()) "My Playlist" else title.trim()
        val newPlaylist = UserPlaylist(
            title = cleanTitle,
            description = description.trim(),
            customCoverImagePath = customCoverPath.trim(),
            tracks = if (initialTrack != null) listOf(initialTrack) else emptyList()
        )

        val current = _playlists.value.toMutableList()
        current.add(0, newPlaylist) // Newest first
        _playlists.value = current
        savePlaylistsToDisk(current)
        newPlaylist
    }

    /**
     * Explicitly saves an auto-generated smart playlist suggestion into the user's persistent library.
     * Smart playlists are NEVER saved automatically unless the user invokes this action.
     */
    suspend fun saveSmartPlaylistToLibrary(smartPlaylist: UserPlaylist): UserPlaylist = withContext(Dispatchers.IO) {
        val current = _playlists.value.toMutableList()
        val existingIdx = current.indexOfFirst {
            it.id == smartPlaylist.id || it.title.equals(smartPlaylist.title, ignoreCase = true)
        }
        val savedCopy = UserPlaylist(
            id = if (existingIdx >= 0) current[existingIdx].id else "pl_saved_${smartPlaylist.id}",
            title = smartPlaylist.title.trim(),
            description = smartPlaylist.description.trim(),
            customCoverImagePath = smartPlaylist.customCoverImagePath.trim(),
            tracks = smartPlaylist.tracks,
            isSmartSuggestion = false
        )
        if (existingIdx >= 0) {
            current[existingIdx] = savedCopy
        } else {
            current.add(0, savedCopy)
        }
        _playlists.value = current
        savePlaylistsToDisk(current)
        savedCopy
    }

    /**
     * Updates an existing playlist's title, description, or custom cover image.
     */
    suspend fun updatePlaylist(
        playlistId: String,
        title: String,
        description: String = "",
        customCoverPath: String = ""
    ): UserPlaylist? = withContext(Dispatchers.IO) {
        val current = _playlists.value.toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index == -1) return@withContext null

        val existing = current[index]
        val updated = existing.copy(
            title = if (title.isBlank()) existing.title else title.trim(),
            description = description.trim(),
            customCoverImagePath = customCoverPath.trim()
        )
        current[index] = updated
        _playlists.value = current
        savePlaylistsToDisk(current)
        updated
    }

    /**
     * Deletes a playlist by its unique ID.
     */
    suspend fun deletePlaylist(playlistId: String): Boolean = withContext(Dispatchers.IO) {
        val current = _playlists.value.toMutableList()
        val removed = current.removeAll { it.id == playlistId }
        if (removed) {
            _playlists.value = current
            savePlaylistsToDisk(current)
        }
        removed
    }

    /**
     * Adds a track to a playlist.
     * Returns true if added, false if already present.
     */
    suspend fun addTrackToPlaylist(playlistId: String, track: TrackItem): Boolean = withContext(Dispatchers.IO) {
        val current = _playlists.value.toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index == -1) return@withContext false

        val playlist = current[index]
        val existingIndex = playlist.tracks.indexOfFirst { it.id == track.id }
        if (existingIndex >= 0) {
            // Already present in playlist
            return@withContext false
        }

        val updatedTracks = playlist.tracks.toMutableList()
        updatedTracks.add(track)
        current[index] = playlist.copy(tracks = updatedTracks)
        _playlists.value = current
        savePlaylistsToDisk(current)
        true
    }

    /**
     * Removes a track from a playlist.
     */
    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String): UserPlaylist? = withContext(Dispatchers.IO) {
        val current = _playlists.value.toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index == -1) return@withContext null

        val playlist = current[index]
        val updatedTracks = playlist.tracks.filterNot { it.id == trackId }
        val updatedPlaylist = playlist.copy(tracks = updatedTracks)
        current[index] = updatedPlaylist
        _playlists.value = current
        savePlaylistsToDisk(current)
        updatedPlaylist
    }

    /**
     * Reorders tracks inside a playlist.
     */
    suspend fun reorderTracks(playlistId: String, fromIndex: Int, toIndex: Int): UserPlaylist? = withContext(Dispatchers.IO) {
        val current = _playlists.value.toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index == -1) return@withContext null

        val playlist = current[index]
        val tracks = playlist.tracks.toMutableList()
        if (fromIndex !in tracks.indices || toIndex !in tracks.indices || fromIndex == toIndex) {
            return@withContext playlist
        }

        val moved = tracks.removeAt(fromIndex)
        tracks.add(toIndex, moved)
        val updatedPlaylist = playlist.copy(tracks = tracks)
        current[index] = updatedPlaylist
        _playlists.value = current
        savePlaylistsToDisk(current)
        updatedPlaylist
    }

    /**
     * Gets a playlist by ID.
     */
    fun getPlaylistById(playlistId: String): UserPlaylist? {
        return _playlists.value.firstOrNull { it.id == playlistId }
    }

    private fun savePlaylistsToDisk(playlistsList: List<UserPlaylist>) {
        try {
            val serialized = serializePlaylistsToJson(playlistsList)
            val file = playlistsFile
            val tempFile = File(context.filesDir, "$FILE_NAME.tmp")
            tempFile.writeText(serialized, Charsets.UTF_8)
            if (tempFile.renameTo(file)) {
                Log.d(TAG, "Successfully persisted ${playlistsList.size} playlists to disk.")
            } else {
                file.writeText(serialized, Charsets.UTF_8)
                tempFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving playlists to disk", e)
        }
    }
}
