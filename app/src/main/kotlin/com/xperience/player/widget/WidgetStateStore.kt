package com.xperience.player.widget

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure JSON codec for [WidgetPlaybackSnapshot] and [WidgetPlaylistItem] lists.
 * Designed to be 100% defensive against malformed, partial, or legacy payloads.
 */
object WidgetStateSerializer {

    private const val MAX_STORED_RECOMMENDATIONS = 20

    fun encodePlayback(snapshot: WidgetPlaybackSnapshot): String {
        val obj = JSONObject().apply {
            put("schemaVersion", WidgetStateSnapshot.CURRENT_SCHEMA_VERSION)
            put("trackId", snapshot.trackId ?: JSONObject.NULL)
            put("title", snapshot.title)
            put("artist", snapshot.artist)
            put("album", snapshot.album)
            put("artworkUri", snapshot.artworkUri ?: JSONObject.NULL)
            put("artworkResId", snapshot.artworkResId ?: JSONObject.NULL)
            put("audioUrl", snapshot.audioUrl ?: JSONObject.NULL)
            put("isPlaying", snapshot.isPlaying)
            put("isLoading", snapshot.isLoading)
            put("isLiked", snapshot.isLiked)
            put("positionMs", snapshot.safePositionMs)
            put("durationMs", snapshot.safeDurationMs)
            put("queueSize", snapshot.queueSize.coerceAtLeast(0))
            put("accentColorArgb", snapshot.accentColorArgb ?: JSONObject.NULL)
            put("updatedAtMs", snapshot.updatedAtMs.coerceAtLeast(0L))
        }
        return obj.toString()
    }

    fun decodePlayback(rawJson: String?): WidgetPlaybackSnapshot {
        if (rawJson.isNullOrBlank()) return WidgetPlaybackSnapshot()
        return runCatching {
            val obj = JSONObject(rawJson)
            val trackId = obj.optNullableString("trackId")
            val artworkResId = if (obj.has("artworkResId") && !obj.isNull("artworkResId")) {
                obj.optInt("artworkResId", 0).takeIf { it != 0 }
            } else {
                null
            }
            val accentColorArgb = if (obj.has("accentColorArgb") && !obj.isNull("accentColorArgb")) {
                obj.optInt("accentColorArgb")
            } else {
                null
            }
            WidgetPlaybackSnapshot(
                trackId = trackId,
                title = obj.optSafeString("title"),
                artist = obj.optSafeString("artist"),
                album = obj.optSafeString("album"),
                artworkUri = obj.optNullableString("artworkUri"),
                artworkResId = artworkResId,
                audioUrl = obj.optNullableString("audioUrl"),
                isPlaying = obj.optBoolean("isPlaying", false),
                isLoading = obj.optBoolean("isLoading", false),
                isLiked = obj.optBoolean("isLiked", false),
                positionMs = obj.optLong("positionMs", 0L).coerceAtLeast(0L),
                durationMs = obj.optLong("durationMs", 0L).coerceAtLeast(0L),
                queueSize = obj.optInt("queueSize", 0).coerceAtLeast(0),
                accentColorArgb = accentColorArgb,
                updatedAtMs = obj.optLong("updatedAtMs", 0L).coerceAtLeast(0L)
            )
        }.getOrDefault(WidgetPlaybackSnapshot())
    }

    fun encodeRecommendations(
        items: List<WidgetPlaylistItem>,
        updatedAtMs: Long
    ): String {
        val array = JSONArray()
        items.take(MAX_STORED_RECOMMENDATIONS).forEach { item ->
            if (item.id.isBlank() || item.title.isBlank()) return@forEach
            val entry = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("subtitle", item.subtitle)
                put("trackCount", item.trackCount.coerceAtLeast(0))
                put("artworkUri", item.artworkUri ?: JSONObject.NULL)
                put("sourceType", item.sourceType.wireValue)
                put("playlistId", item.playlistId ?: JSONObject.NULL)
                put("browseId", item.browseId ?: JSONObject.NULL)
                put("seedVideoId", item.seedVideoId ?: JSONObject.NULL)
                put("params", item.params ?: JSONObject.NULL)
            }
            array.put(entry)
        }
        val root = JSONObject().apply {
            put("schemaVersion", WidgetStateSnapshot.CURRENT_SCHEMA_VERSION)
            put("updatedAtMs", updatedAtMs.coerceAtLeast(0L))
            put("items", array)
        }
        return root.toString()
    }

    fun decodeRecommendations(rawJson: String?): Pair<List<WidgetPlaylistItem>, Long> {
        if (rawJson.isNullOrBlank()) return emptyList<WidgetPlaylistItem>() to 0L
        return runCatching {
            val root = JSONObject(rawJson)
            val updatedAtMs = root.optLong("updatedAtMs", 0L).coerceAtLeast(0L)
            val array = root.optJSONArray("items") ?: JSONArray()
            val results = mutableListOf<WidgetPlaylistItem>()
            for (i in 0 until array.length()) {
                val entry = array.optJSONObject(i) ?: continue
                val id = entry.optSafeString("id")
                val title = entry.optSafeString("title")
                if (id.isBlank() || title.isBlank()) continue
                results.add(
                    WidgetPlaylistItem(
                        id = id,
                        title = title,
                        subtitle = entry.optSafeString("subtitle"),
                        trackCount = entry.optInt("trackCount", 0).coerceAtLeast(0),
                        artworkUri = entry.optNullableString("artworkUri"),
                        sourceType = WidgetPlaylistSourceType.fromWireValue(entry.optNullableString("sourceType")),
                        playlistId = entry.optNullableString("playlistId"),
                        browseId = entry.optNullableString("browseId"),
                        seedVideoId = entry.optNullableString("seedVideoId"),
                        params = entry.optNullableString("params")
                    )
                )
            }
            results.toList() to updatedAtMs
        }.getOrDefault(emptyList<WidgetPlaylistItem>() to 0L)
    }

    private fun JSONObject.optSafeString(key: String): String {
        if (!has(key) || isNull(key)) return ""
        return optString(key, "").trim()
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key, "").trim().ifBlank { null }
    }
}

/**
 * Minimal key-value backing store abstraction so [WidgetStateStore] can be tested on JVM
 * without requiring Robolectric or an Android runtime.
 */
internal interface WidgetKeyValueBackend {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun getInt(key: String, defaultValue: Int): Int
    fun putInt(key: String, value: Int)
    fun clear()
}

private class SharedPrefsBackend(
    private val prefs: SharedPreferences
) : WidgetKeyValueBackend {
    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun getInt(key: String, defaultValue: Int): Int = prefs.getInt(key, defaultValue)

    override fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }
}

/**
 * Persistent storage for Xperience home-screen widget snapshots.
 */
class WidgetStateStore internal constructor(
    private val backend: WidgetKeyValueBackend
) {
    constructor(context: Context) : this(
        SharedPrefsBackend(
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        )
    )

    fun readState(): WidgetStateSnapshot {
        val playback = readPlayback()
        val (recommendations, updatedAtMs) = WidgetStateSerializer.decodeRecommendations(
            backend.getString(KEY_RECOMMENDATIONS_JSON)
        )
        val rotationOffset = backend.getInt(KEY_ROTATION_OFFSET, 0).coerceAtLeast(0)
        return WidgetStateSnapshot(
            playback = playback,
            recommendations = recommendations,
            recommendationsUpdatedAtMs = updatedAtMs,
            rotationOffset = rotationOffset
        )
    }

    fun readPlayback(): WidgetPlaybackSnapshot {
        return WidgetStateSerializer.decodePlayback(backend.getString(KEY_PLAYBACK_JSON))
    }

    fun savePlayback(snapshot: WidgetPlaybackSnapshot) {
        backend.putString(KEY_PLAYBACK_JSON, WidgetStateSerializer.encodePlayback(snapshot))
    }

    fun readRecommendations(): List<WidgetPlaylistItem> {
        return WidgetStateSerializer.decodeRecommendations(
            backend.getString(KEY_RECOMMENDATIONS_JSON)
        ).first
    }

    fun saveRecommendations(
        items: List<WidgetPlaylistItem>,
        updatedAtMs: Long = System.currentTimeMillis()
    ) {
        backend.putString(
            KEY_RECOMMENDATIONS_JSON,
            WidgetStateSerializer.encodeRecommendations(items, updatedAtMs)
        )
        if (items.isEmpty()) {
            backend.putInt(KEY_ROTATION_OFFSET, 0)
        } else {
            val currentOffset = backend.getInt(KEY_ROTATION_OFFSET, 0)
            if (currentOffset >= items.size) {
                backend.putInt(KEY_ROTATION_OFFSET, 0)
            }
        }
    }

    fun getRecommendationOffset(): Int = backend.getInt(KEY_ROTATION_OFFSET, 0).coerceAtLeast(0)

    /**
     * Cycles the recommendation display window forward by [step] items when the user taps Refresh.
     */
    fun advanceRecommendationOffset(totalItems: Int, step: Int = 2): Int {
        if (totalItems <= 1) {
            backend.putInt(KEY_ROTATION_OFFSET, 0)
            return 0
        }
        val safeStep = step.coerceAtLeast(1)
        val next = (getRecommendationOffset() + safeStep) % totalItems
        backend.putInt(KEY_ROTATION_OFFSET, next)
        return next
    }

    /**
     * Optimistically updates the favorite state of the persisted track snapshot.
     */
    fun updateLikedStatus(trackId: String, isLiked: Boolean): WidgetPlaybackSnapshot {
        val current = readPlayback()
        if (current.trackId != trackId) return current
        val updated = current.copy(isLiked = isLiked)
        savePlayback(updated)
        return updated
    }

    fun clear() {
        backend.clear()
    }

    companion object {
        private const val PREFS_NAME = "xperience_widget_state_prefs"
        private const val KEY_PLAYBACK_JSON = "playback_snapshot_json"
        private const val KEY_RECOMMENDATIONS_JSON = "recommendations_snapshot_json"
        private const val KEY_ROTATION_OFFSET = "recommendations_rotation_offset"
    }
}
