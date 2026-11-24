package com.xperience.player.service.ringtone

import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream

/**
 * Typed exception raised when [Settings.System.canWrite] is false and the user needs to
 * grant `WRITE_SETTINGS` in Android System Settings before applying a default tone.
 */
class WriteSettingsPermissionRequiredException :
    SecurityException("Se requiere el permiso 'Modificar ajustes del sistema' para establecer tonos predeterminados.")

/**
 * Manages persisting trimmed `.m4a` clips to Android 13+ (API 33+) [MediaStore.Audio.Media]
 * using atomic `IS_PENDING` writes, applying default system tones via [RingtoneManager],
 * checking/requesting `WRITE_SETTINGS`, and listing/re-applying/deleting app-created clips.
 */
class RingtoneStorageManager(
    private val context: Context
) {
    private val appContext = context.applicationContext

    fun canWriteSettings(): Boolean {
        return runCatching { Settings.System.canWrite(appContext) }.getOrDefault(false)
    }

    /**
     * Builds the system settings intent to grant [android.Manifest.permission.WRITE_SETTINGS].
     */
    fun createWriteSettingsIntent(callerContext: Context = appContext): Intent {
        return Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:${appContext.packageName}")
        ).apply {
            if (callerContext !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    /**
     * Atomically writes [trimmedM4aFile] into [MediaStore.Audio.Media] under the appropriate
     * system directory (`Ringtones/Xperience`, `Notifications/Xperience`, or `Alarms/Xperience`).
     */
    suspend fun saveClipToMediaStore(
        trimmedM4aFile: File,
        clipTitle: String,
        artist: String,
        durationMs: Long,
        target: RingtoneTarget
    ): Result<SavedRingtoneClip> = withContext(Dispatchers.IO) {
        if (!trimmedM4aFile.exists() || trimmedM4aFile.length() <= 0L) {
            return@withContext Result.failure(
                IllegalArgumentException("El archivo recortado no existe o está vacío.")
            )
        }

        val resolver = appContext.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val safeTitle = RingtoneMetadataSanitizer.sanitizeDisplayTitle(clipTitle)
        val safeArtist = RingtoneMetadataSanitizer.sanitizeArtist(artist)
        val displayName = RingtoneMetadataSanitizer.buildM4aFileName(safeTitle)
        val relativePath = resolveRelativePath(target)
        val safeDuration = durationMs.coerceAtLeast(1L)

        val insertValues = ContentValues().apply {
            put(MediaStore.Audio.Media.TITLE, safeTitle)
            put(MediaStore.Audio.Media.ARTIST, safeArtist)
            put(MediaStore.Audio.Media.ALBUM, CLIP_ALBUM_MARKER)
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.MIME_TYPE, MIME_AUDIO_MP4)
            put(MediaStore.Audio.Media.DURATION, safeDuration)
            put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Audio.Media.IS_MUSIC, 0)
            put(
                MediaStore.Audio.Media.IS_RINGTONE,
                if (target == RingtoneTarget.RINGTONE || target == RingtoneTarget.SAVE_CLIP_ONLY) 1 else 0
            )
            put(MediaStore.Audio.Media.IS_NOTIFICATION, if (target == RingtoneTarget.NOTIFICATION) 1 else 0)
            put(MediaStore.Audio.Media.IS_ALARM, if (target == RingtoneTarget.ALARM) 1 else 0)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }

        var insertedUri: Uri? = null
        try {
            insertedUri = resolver.insert(collection, insertValues)
                ?: return@withContext Result.failure(
                    IllegalStateException("MediaStore no pudo crear la entrada para el tono.")
                )

            val outputStream = resolver.openOutputStream(insertedUri, "w")
                ?: throw IllegalStateException("No se pudo abrir el flujo de escritura en MediaStore.")

            var bytesWritten = 0L
            outputStream.use { out ->
                BufferedInputStream(FileInputStream(trimmedM4aFile), IO_BUFFER_SIZE).use { input ->
                    val buffer = ByteArray(IO_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        bytesWritten += read
                    }
                    out.flush()
                }
            }

            if (bytesWritten <= 0L) {
                throw IllegalStateException("No se escribieron bytes al guardar el tono.")
            }

            val finalizeValues = ContentValues().apply {
                put(MediaStore.Audio.Media.IS_PENDING, 0)
            }
            resolver.update(insertedUri, finalizeValues, null, null)

            val mediaId = runCatching { ContentUris.parseId(insertedUri) }.getOrDefault(-1L)
            val savedClip = SavedRingtoneClip(
                id = mediaId,
                contentUri = insertedUri.toString(),
                title = safeTitle,
                artist = safeArtist,
                displayName = displayName,
                durationMs = safeDuration,
                dateAddedMs = System.currentTimeMillis(),
                target = target,
                sizeBytes = bytesWritten,
                relativePath = relativePath
            )
            Result.success(savedClip)
        } catch (t: Throwable) {
            // Clean up orphaned IS_PENDING entry on failure
            insertedUri?.let { uri ->
                runCatching { resolver.delete(uri, null, null) }
            }
            Log.e(TAG, "Failed saving ringtone clip to MediaStore: ${t.message}", t)
            Result.failure(t)
        }
    }

    /**
     * Sets [contentUriString] as the system default tone for [target].
     * Returns [WriteSettingsPermissionRequiredException] if `WRITE_SETTINGS` is not granted.
     */
    suspend fun applyDefaultTone(
        contentUriString: String,
        target: RingtoneTarget
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (target == RingtoneTarget.SAVE_CLIP_ONLY) {
            return@withContext Result.success(Unit)
        }
        if (!canWriteSettings()) {
            return@withContext Result.failure(WriteSettingsPermissionRequiredException())
        }

        val ringtoneType = mapTargetToRingtoneManagerType(target)
            ?: return@withContext Result.success(Unit)

        try {
            val uri = Uri.parse(contentUriString)

            // Ensure the corresponding IS_* MediaStore flag is enabled so system pickers also recognize it
            val flagUpdate = ContentValues().apply {
                when (target) {
                    RingtoneTarget.RINGTONE -> put(MediaStore.Audio.Media.IS_RINGTONE, 1)
                    RingtoneTarget.NOTIFICATION -> put(MediaStore.Audio.Media.IS_NOTIFICATION, 1)
                    RingtoneTarget.ALARM -> put(MediaStore.Audio.Media.IS_ALARM, 1)
                    RingtoneTarget.SAVE_CLIP_ONLY -> Unit
                }
            }
            if (flagUpdate.size() > 0) {
                runCatching { appContext.contentResolver.update(uri, flagUpdate, null, null) }
            }

            RingtoneManager.setActualDefaultRingtoneUri(appContext, ringtoneType, uri)
            Result.success(Unit)
        } catch (se: SecurityException) {
            Log.w(TAG, "SecurityException setting default tone: ${se.message}", se)
            Result.failure(WriteSettingsPermissionRequiredException())
        } catch (t: Throwable) {
            Log.e(TAG, "Failed applying default tone ($target): ${t.message}", t)
            Result.failure(t)
        }
    }

    /**
     * Queries MediaStore for all previously saved Xperience ringtone/notification/alarm clips.
     */
    suspend fun listSavedClips(): List<SavedRingtoneClip> = withContext(Dispatchers.IO) {
        val clips = mutableListOf<SavedRingtoneClip>()
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.IS_RINGTONE,
            MediaStore.Audio.Media.IS_NOTIFICATION,
            MediaStore.Audio.Media.IS_ALARM
        )

        val selection = buildString {
            append("${MediaStore.Audio.Media.IS_PENDING} = 0 AND ")
            append("(${MediaStore.Audio.Media.IS_RINGTONE} = 1 OR ")
            append("${MediaStore.Audio.Media.IS_NOTIFICATION} = 1 OR ")
            append("${MediaStore.Audio.Media.IS_ALARM} = 1) AND ")
            append("(${MediaStore.Audio.Media.ALBUM} = ? OR ")
            append("${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?)")
        }
        val selectionArgs = arrayOf(CLIP_ALBUM_MARKER, "%/$SUBFOLDER_NAME%")

        try {
            appContext.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val displayNameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val relPathCol = cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                val isRingtoneCol = cursor.getColumnIndex(MediaStore.Audio.Media.IS_RINGTONE)
                val isNotifCol = cursor.getColumnIndex(MediaStore.Audio.Media.IS_NOTIFICATION)
                val isAlarmCol = cursor.getColumnIndex(MediaStore.Audio.Media.IS_ALARM)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(collection, id)
                    val title = cursor.getString(titleCol).orEmpty()
                        .ifBlank { cursor.getString(displayNameCol).orEmpty().substringBeforeLast(".") }
                    val artist = RingtoneMetadataSanitizer.sanitizeArtist(cursor.getString(artistCol))
                    val displayName = cursor.getString(displayNameCol).orEmpty()
                    val durationMs = cursor.getLong(durationCol).coerceAtLeast(0L)
                    val rawDateSec = cursor.getLong(dateAddedCol).coerceAtLeast(0L)
                    val dateAddedMs = if (rawDateSec in 1L..99_999_999_999L) rawDateSec * 1000L else rawDateSec
                    val sizeBytes = cursor.getLong(sizeCol).coerceAtLeast(0L)
                    if (sizeBytes <= 0L) continue
                    val relPath = if (relPathCol >= 0) cursor.getString(relPathCol).orEmpty() else ""
                    val isRingtone = isRingtoneCol >= 0 && cursor.getInt(isRingtoneCol) != 0
                    val isNotification = isNotifCol >= 0 && cursor.getInt(isNotifCol) != 0
                    val isAlarm = isAlarmCol >= 0 && cursor.getInt(isAlarmCol) != 0

                    val inferredTarget = inferTargetFromMediaStoreRow(
                        relativePath = relPath,
                        isRingtone = isRingtone,
                        isNotification = isNotification,
                        isAlarm = isAlarm
                    )

                    clips.add(
                        SavedRingtoneClip(
                            id = id,
                            contentUri = contentUri.toString(),
                            title = title,
                            artist = artist,
                            displayName = displayName,
                            durationMs = durationMs,
                            dateAddedMs = dateAddedMs,
                            target = inferredTarget,
                            sizeBytes = sizeBytes,
                            relativePath = relPath
                        )
                    )
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed listing saved ringtone clips: ${t.message}", t)
        }

        clips
    }

    /**
     * Deletes an app-created ringtone clip from MediaStore.
     */
    suspend fun deleteClip(clip: SavedRingtoneClip): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(clip.contentUri)
            val deleted = appContext.contentResolver.delete(uri, null, null)
            if (deleted > 0) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("No se pudo eliminar el recorte seleccionado."))
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed deleting clip ${clip.id}: ${t.message}", t)
            Result.failure(t)
        }
    }

    private fun resolveRelativePath(target: RingtoneTarget): String {
        val baseDir = when (target) {
            RingtoneTarget.RINGTONE, RingtoneTarget.SAVE_CLIP_ONLY ->
                Environment.DIRECTORY_RINGTONES ?: "Ringtones"
            RingtoneTarget.NOTIFICATION ->
                Environment.DIRECTORY_NOTIFICATIONS ?: "Notifications"
            RingtoneTarget.ALARM ->
                Environment.DIRECTORY_ALARMS ?: "Alarms"
        }
        return "$baseDir/$SUBFOLDER_NAME"
    }

    private fun mapTargetToRingtoneManagerType(target: RingtoneTarget): Int? = when (target) {
        RingtoneTarget.RINGTONE -> RingtoneManager.TYPE_RINGTONE
        RingtoneTarget.NOTIFICATION -> RingtoneManager.TYPE_NOTIFICATION
        RingtoneTarget.ALARM -> RingtoneManager.TYPE_ALARM
        RingtoneTarget.SAVE_CLIP_ONLY -> null
    }

    companion object {
        private const val TAG = "RingtoneStorageManager"
        private const val SUBFOLDER_NAME = "Xperience"
        private const val CLIP_ALBUM_MARKER = "Xperience Ringtones"
        private const val MIME_AUDIO_MP4 = "audio/mp4"
        private const val IO_BUFFER_SIZE = 32 * 1024

        internal fun inferTargetFromMediaStoreRow(
            relativePath: String,
            isRingtone: Boolean,
            isNotification: Boolean,
            isAlarm: Boolean
        ): RingtoneTarget {
            val lower = relativePath.lowercase()
            return when {
                lower.contains("notifications") || (isNotification && !isRingtone && !isAlarm) ->
                    RingtoneTarget.NOTIFICATION
                lower.contains("alarms") || (isAlarm && !isRingtone && !isNotification) ->
                    RingtoneTarget.ALARM
                else -> RingtoneTarget.RINGTONE
            }
        }
    }
}
