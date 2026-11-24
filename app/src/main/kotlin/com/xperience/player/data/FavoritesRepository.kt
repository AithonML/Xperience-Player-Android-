package com.xperience.player.data

import android.content.Context
import android.util.Log
import com.xperience.player.domain.model.TrackItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Manages persistent storage of liked / favorite tracks on disk (JSON format).
 * Ensures favorite tracks (both online and local) survive application restarts,
 * and maintains reactive StateFlows for instant UI responsiveness across all screens.
 */
class FavoritesRepository(context: Context) {

    private val appContext: Context = context.applicationContext
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "FavoritesRepository"
        private const val FILE_NAME = "xperience_favorites.json"

        @Volatile
        private var instance: FavoritesRepository? = null

        fun getInstance(context: Context): FavoritesRepository {
            return instance ?: synchronized(this) {
                instance ?: FavoritesRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    private val favoritesFile: File
        get() = File(appContext.filesDir, FILE_NAME)

    private val _likedTracks = MutableStateFlow<List<TrackItem>>(emptyList())
    val likedTracks: StateFlow<List<TrackItem>> = _likedTracks.asStateFlow()

    private val _likedTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val likedTrackIds: StateFlow<Set<String>> = _likedTrackIds.asStateFlow()

    init {
        ioScope.launch {
            loadFavoritesFromDisk()
        }
    }

    /**
     * Loads the saved favorites list from disk into memory.
     */
    fun loadFavoritesFromDisk() {
        try {
            val file = favoritesFile
            if (!file.exists()) {
                _likedTracks.value = emptyList()
                _likedTrackIds.value = emptySet()
                return
            }

            val jsonContent = file.readText(Charsets.UTF_8)
            if (jsonContent.isBlank()) {
                _likedTracks.value = emptyList()
                _likedTrackIds.value = emptySet()
                return
            }

            val jsonArray = JSONArray(jsonContent)
            val rawList = mutableListOf<TrackItem>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                if (id.isBlank()) continue

                rawList.add(
                    TrackItem(
                        id = id,
                        title = obj.optString("title", "Unknown Title"),
                        artist = obj.optString("artist", "Unknown Artist"),
                        album = obj.optString("album", "Unknown Album"),
                        durationMs = obj.optLong("durationMs", 0L),
                        artworkUri = obj.optString("artworkUri").ifBlank { null },
                        audioUrl = obj.optString("audioUrl").ifBlank { null },
                        isLiked = true,
                        isExplicit = obj.optBoolean("isExplicit", false),
                        playCount = obj.optLong("playCount", 0L)
                    )
                )
            }

            val counterparts = com.xperience.player.service.YouTubeMusicService.loadAtvCounterpartMapSafe(
                com.xperience.player.service.YouTubeMusicService.getAtvCounterpartCacheFile(appContext)
            )
            val (list, modified) = com.xperience.player.service.YouTubeMusicService.sanitizePersistedTracks(
                rawList,
                counterparts
            )

            _likedTracks.value = list
            _likedTrackIds.value = list.mapTo(mutableSetOf()) { it.id }
            if (modified) {
                saveFavoritesToDisk(list)
            }
            Log.d(TAG, "Loaded ${list.size} favorite tracks from disk (migrated=$modified).")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading favorites from disk", e)
        }
    }

    /**
     * Toggles a track favorite status. If adding, uses the provided track item.
     * Returns true if the track is now liked, false if unliked.
     */
    suspend fun toggleFavorite(track: TrackItem): Boolean = withContext(Dispatchers.IO) {
        val currentIds = _likedTrackIds.value
        val isCurrentlyLiked = currentIds.contains(track.id)

        if (isCurrentlyLiked) {
            removeFavoriteInternal(track.id)
            false
        } else {
            addFavoriteInternal(track.copy(isLiked = true))
            true
        }
    }

    /**
     * Toggles favorite by trackId with an optional fallback track.
     */
    suspend fun toggleFavoriteById(trackId: String, fallback: TrackItem? = null): Boolean = withContext(Dispatchers.IO) {
        val currentIds = _likedTrackIds.value
        val isCurrentlyLiked = currentIds.contains(trackId)

        if (isCurrentlyLiked) {
            removeFavoriteInternal(trackId)
            false
        } else {
            val trackToAdd = fallback?.copy(isLiked = true) ?: TrackItem(
                id = trackId,
                title = "Favorite Song",
                artist = "Unknown Artist",
                album = "Favorites",
                isLiked = true
            )
            addFavoriteInternal(trackToAdd)
            true
        }
    }

    private fun addFavoriteInternal(track: TrackItem) {
        val currentList = _likedTracks.value.toMutableList()
        currentList.removeAll { it.id == track.id }
        currentList.add(0, track) // Most recently liked at the top

        _likedTracks.value = currentList
        _likedTrackIds.value = currentList.map { it.id }.toSet()
        saveFavoritesToDisk(currentList)
    }

    private fun removeFavoriteInternal(trackId: String) {
        val currentList = _likedTracks.value.toMutableList()
        currentList.removeAll { it.id == trackId }

        _likedTracks.value = currentList
        _likedTrackIds.value = currentList.map { it.id }.toSet()
        saveFavoritesToDisk(currentList)
    }

    private fun saveFavoritesToDisk(tracks: List<TrackItem>) {
        try {
            val jsonArray = JSONArray()
            for (t in tracks) {
                val obj = JSONObject()
                obj.put("id", t.id)
                obj.put("title", t.title)
                obj.put("artist", t.artist)
                obj.put("album", t.album)
                obj.put("durationMs", t.durationMs)
                obj.put("artworkUri", t.artworkUri ?: "")
                obj.put("audioUrl", t.audioUrl ?: "")
                obj.put("isExplicit", t.isExplicit)
                obj.put("playCount", t.playCount)
                jsonArray.put(obj)
            }

            favoritesFile.writeText(jsonArray.toString(2), Charsets.UTF_8)
            Log.d(TAG, "Saved ${tracks.size} favorites to disk.")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving favorites to disk", e)
        }
    }
}
