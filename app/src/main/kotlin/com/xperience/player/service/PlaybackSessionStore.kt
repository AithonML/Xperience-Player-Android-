package com.xperience.player.service

import android.content.Context
import android.net.Uri
import android.util.Log
import com.xperience.player.domain.model.PlaybackRepeatMode
import com.xperience.player.domain.model.TrackItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets

data class PersistedPlaybackSession(
    val version: Int = SESSION_VERSION,
    val savedAtMs: Long = 0L,
    val queue: List<TrackItem> = emptyList(),
    val canonicalQueue: List<TrackItem> = emptyList(),
    val currentQueueIndex: Int = -1,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: PlaybackRepeatMode = PlaybackRepeatMode.Off
) {
    companion object {
        const val SESSION_VERSION = 1
    }
}

/**
 * Atomic JSON store for persisting the active queue, canonical pre-shuffle order,
 * active track index, and exact playback progress across app closes and process kills.
 */
class PlaybackSessionStore(
    private val storageFile: File,
    private val localFileExists: (String) -> Boolean = { path -> File(path).exists() }
) {
    constructor(context: Context) : this(
        storageFile = File(context.applicationContext.filesDir, SESSION_FILE_NAME)
    )

    private val ioLock = Any()

    fun save(session: PersistedPlaybackSession) {
        synchronized(ioLock) {
            runCatching {
                val jsonString = serializeSessionToJson(session)
                val parent = storageFile.parentFile
                if (parent != null && !parent.exists()) {
                    parent.mkdirs()
                }
                val tmp = File(parent, "${storageFile.name}.tmp")
                tmp.writeText(jsonString, StandardCharsets.UTF_8)
                if (!tmp.renameTo(storageFile)) {
                    storageFile.writeText(jsonString, StandardCharsets.UTF_8)
                    tmp.delete()
                }
            }.onFailure { e ->
                Log.w(TAG, "Failed to save playback session: ${e.message}")
            }
        }
    }

    fun clear() {
        synchronized(ioLock) {
            runCatching {
                if (storageFile.exists()) storageFile.delete()
            }
        }
    }

    fun load(): PersistedPlaybackSession? {
        synchronized(ioLock) {
            return runCatching {
                if (!storageFile.exists() || !storageFile.isFile) return null
                val raw = storageFile.readText(StandardCharsets.UTF_8)
                if (raw.isBlank()) return null
                parseSessionFromJson(raw, localFileExists)
            }.onFailure { e ->
                Log.w(TAG, "Failed to load playback session: ${e.message}")
            }.getOrNull()
        }
    }

    companion object {
        private const val TAG = "PlaybackSessionStore"
        private const val SESSION_FILE_NAME = "playback_session_v1.json"
        internal const val MAX_PERSISTED_QUEUE_ITEMS = 200
        internal const val MAX_PERSISTED_HISTORY_BEFORE_ACTIVE = 40
        internal const val MAX_PERSISTED_CANONICAL_ITEMS = 250

        internal fun buildPersistedSession(
            queue: List<TrackItem>,
            canonicalQueue: List<TrackItem>,
            currentIndex: Int,
            positionMs: Long,
            durationMs: Long,
            isShuffleEnabled: Boolean,
            repeatMode: PlaybackRepeatMode,
            savedAtMs: Long = System.currentTimeMillis()
        ): PersistedPlaybackSession {
            if (queue.isEmpty()) {
                return PersistedPlaybackSession(savedAtMs = savedAtMs)
            }
            val safeIndex = currentIndex.coerceIn(0, queue.lastIndex)
            val (windowedQueue, rebasedIndex) = windowQueueForPersistence(queue, safeIndex)
            val windowedCanonical = if (canonicalQueue.size <= MAX_PERSISTED_CANONICAL_ITEMS) {
                canonicalQueue
            } else {
                canonicalQueue.take(MAX_PERSISTED_CANONICAL_ITEMS)
            }
            return PersistedPlaybackSession(
                version = PersistedPlaybackSession.SESSION_VERSION,
                savedAtMs = savedAtMs,
                queue = windowedQueue,
                canonicalQueue = windowedCanonical,
                currentQueueIndex = rebasedIndex,
                positionMs = positionMs.coerceAtLeast(0L),
                durationMs = durationMs.coerceAtLeast(0L),
                isShuffleEnabled = isShuffleEnabled,
                repeatMode = repeatMode
            )
        }

        internal fun windowQueueForPersistence(
            queue: List<TrackItem>,
            currentIndex: Int
        ): Pair<List<TrackItem>, Int> {
            if (queue.isEmpty()) return emptyList<TrackItem>() to -1
            val safeIdx = currentIndex.coerceIn(0, queue.lastIndex)
            if (queue.size <= MAX_PERSISTED_QUEUE_ITEMS) {
                return queue to safeIdx
            }
            val start = (safeIdx - MAX_PERSISTED_HISTORY_BEFORE_ACTIVE).coerceAtLeast(0)
            val endExclusive = (start + MAX_PERSISTED_QUEUE_ITEMS).coerceAtMost(queue.size)
            val adjustedStart = (endExclusive - MAX_PERSISTED_QUEUE_ITEMS).coerceAtLeast(0)
            return queue.subList(adjustedStart, endExclusive) to (safeIdx - adjustedStart)
        }

        internal fun serializeSessionToJson(session: PersistedPlaybackSession): String {
            val root = JSONObject().apply {
                put("v", session.version)
                put("savedAt", session.savedAtMs)
                put("idx", session.currentQueueIndex)
                put("pos", session.positionMs.coerceAtLeast(0L))
                put("dur", session.durationMs.coerceAtLeast(0L))
                put("shuf", session.isShuffleEnabled)
                put("rep", session.repeatMode.name)
                put("q", JSONArray().apply {
                    session.queue.forEach { put(trackToJson(it)) }
                })
                if (session.canonicalQueue.isNotEmpty()) {
                    put("cq", JSONArray().apply {
                        session.canonicalQueue.forEach { put(trackToJson(it)) }
                    })
                }
            }
            return root.toString()
        }

        internal fun parseSessionFromJson(
            rawJson: String,
            localFileExists: (String) -> Boolean = { File(it).exists() }
        ): PersistedPlaybackSession? {
            if (rawJson.isBlank()) return null
            val root = JSONObject(rawJson)
            val qArray = root.optJSONArray("q") ?: return null
            val rawIndex = root.optInt("idx", -1)

            val sanitizedQueue = ArrayList<TrackItem>(qArray.length())
            var adjustedIndex = rawIndex
            for (i in 0 until qArray.length()) {
                val obj = qArray.optJSONObject(i)
                val track = obj?.let { jsonToTrack(it) }
                val sanitized = track?.let { sanitizeRestoredTrack(it, localFileExists) }
                if (sanitized != null) {
                    sanitizedQueue.add(sanitized)
                } else {
                    if (i < rawIndex) {
                        adjustedIndex--
                    } else if (i == rawIndex) {
                        adjustedIndex = adjustedIndex.coerceAtMost(sanitizedQueue.size)
                    }
                }
            }
            if (sanitizedQueue.isEmpty()) return null

            val finalIndex = adjustedIndex.coerceIn(0, sanitizedQueue.lastIndex)
            val cqArray = root.optJSONArray("cq")
            val sanitizedCanonical = if (cqArray != null) {
                ArrayList<TrackItem>(cqArray.length()).apply {
                    for (i in 0 until cqArray.length()) {
                        val obj = cqArray.optJSONObject(i) ?: continue
                        val t = jsonToTrack(obj)?.let { sanitizeRestoredTrack(it, localFileExists) }
                        if (t != null) add(t)
                    }
                }
            } else {
                sanitizedQueue.toList()
            }

            val repeatName = root.optString("rep", PlaybackRepeatMode.Off.name)
            val repeatMode = runCatching { PlaybackRepeatMode.valueOf(repeatName) }
                .getOrDefault(PlaybackRepeatMode.Off)

            val activeTrack = sanitizedQueue[finalIndex]
            val savedDuration = root.optLong("dur", 0L).coerceAtLeast(0L)
                .takeIf { it > 0L } ?: activeTrack.durationMs.coerceAtLeast(0L)
            val savedPos = root.optLong("pos", 0L).coerceAtLeast(0L).let { pos ->
                if (savedDuration > 0L) pos.coerceAtMost(savedDuration) else pos
            }

            return PersistedPlaybackSession(
                version = root.optInt("v", PersistedPlaybackSession.SESSION_VERSION),
                savedAtMs = root.optLong("savedAt", 0L),
                queue = sanitizedQueue,
                canonicalQueue = if (sanitizedCanonical.isNotEmpty()) sanitizedCanonical else sanitizedQueue.toList(),
                currentQueueIndex = finalIndex,
                positionMs = savedPos,
                durationMs = savedDuration,
                isShuffleEnabled = root.optBoolean("shuf", false),
                repeatMode = repeatMode
            )
        }

        internal fun sanitizeRestoredTrack(
            track: TrackItem,
            localFileExists: (String) -> Boolean = { File(it).exists() }
        ): TrackItem? {
            if (track.id.isBlank() || track.title.isBlank()) return null
            val url = track.audioUrl?.trim().orEmpty()
            val isRemoteYtm = track.id.startsWith("ytm_")

            if (isRemoteYtm) {
                if (url.startsWith("file://", ignoreCase = true) || url.startsWith("/")) {
                    val filePath = if (url.startsWith("file://", ignoreCase = true)) {
                        runCatching { Uri.parse(url).path }.getOrNull().orEmpty()
                    } else {
                        url
                    }
                    if (filePath.isBlank() || !localFileExists(filePath)) {
                        val vid = track.id.removePrefix("ytm_video_").removePrefix("ytm_").trim()
                        return track.copy(
                            audioUrl = if (vid.isNotBlank()) "https://www.youtube.com/watch?v=$vid" else null
                        )
                    }
                }
                return track
            }

            if (url.startsWith("file://", ignoreCase = true) || url.startsWith("/")) {
                val filePath = if (url.startsWith("file://", ignoreCase = true)) {
                    runCatching { Uri.parse(url).path }.getOrNull().orEmpty()
                } else {
                    url
                }
                if (filePath.isBlank() || !localFileExists(filePath)) {
                    return null
                }
            }
            return track
        }

        private fun trackToJson(track: TrackItem): JSONObject = JSONObject().apply {
            put("id", track.id)
            put("t", track.title)
            put("a", track.artist)
            put("al", track.album)
            put("d", track.durationMs)
            track.artworkResId?.let { put("res", it) }
            track.artworkUri?.takeIf { it.isNotBlank() }?.let { put("art", it) }
            track.audioUrl?.takeIf { it.isNotBlank() }?.let { put("url", it) }
            if (track.isLiked) put("lk", true)
            if (track.isExplicit) put("ex", true)
            if (track.playCount > 0L) put("pc", track.playCount)
            if (track.dateAddedMs > 0L) put("da", track.dateAddedMs)
        }

        private fun jsonToTrack(obj: JSONObject): TrackItem? {
            val id = obj.optString("id", "").trim()
            val title = obj.optString("t", "").trim()
            if (id.isBlank() || title.isBlank()) return null
            return TrackItem(
                id = id,
                title = title,
                artist = obj.optString("a", "Unknown Artist").ifBlank { "Unknown Artist" },
                album = obj.optString("al", ""),
                durationMs = obj.optLong("d", 0L).coerceAtLeast(0L),
                artworkResId = if (obj.has("res")) obj.optInt("res") else null,
                artworkUri = obj.optString("art", "").takeIf { it.isNotBlank() },
                audioUrl = obj.optString("url", "").takeIf { it.isNotBlank() },
                isLiked = obj.optBoolean("lk", false),
                isExplicit = obj.optBoolean("ex", false),
                playCount = obj.optLong("pc", 0L),
                dateAddedMs = obj.optLong("da", 0L)
            )
        }
    }
}
