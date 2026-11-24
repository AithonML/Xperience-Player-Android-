package com.xperience.player.service.update

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale

/**
 * Streaming SHA-256 download writer and file verifier.
 *
 * Uses a constant-size buffer (never loads APKs into memory) and fails closed by immediately
 * deleting temporary files on any hash mismatch, invalid expected digest, or I/O error.
 */
object StreamingSha256Verifier {

    private const val DEFAULT_BUFFER_SIZE = 8 * 1024

    /**
     * Streams [input] into [destinationPartFile] while computing SHA-256 in a single pass.
     * Invokes [checkCancellation] on each chunk so callers can cancel downloads cleanly.
     */
    fun streamCopyAndHash(
        input: InputStream,
        destinationPartFile: File,
        expectedTotalBytes: Long = -1L,
        bufferSize: Int = DEFAULT_BUFFER_SIZE,
        checkCancellation: () -> Unit = {},
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(bufferSize)
        var totalRead = 0L

        destinationPartFile.parentFile?.mkdirs()

        try {
            FileOutputStream(destinationPartFile).use { output ->
                while (true) {
                    checkCancellation()
                    val read = input.read(buffer)
                    if (read == -1) break
                    if (read > 0) {
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        totalRead += read
                        onProgress(totalRead, expectedTotalBytes)
                    }
                }
                output.flush()
                runCatching { output.fd.sync() }
            }
            return digest.digest().toLowerHex()
        } catch (t: Throwable) {
            safeDelete(destinationPartFile)
            throw t
        }
    }

    /**
     * Computes the SHA-256 hex digest of [file] in streaming fashion.
     */
    fun computeSha256(file: File, bufferSize: Int = DEFAULT_BUFFER_SIZE): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(bufferSize)
        FileInputStream(file).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                if (read > 0) {
                    digest.update(buffer, 0, read)
                }
            }
        }
        return digest.digest().toLowerHex()
    }

    /**
     * Constant-time SHA-256 comparison against a normalized 64-char hex digest.
     * Fails closed (returns `false`) if [expectedSha256] is null or invalid.
     */
    fun hashesMatch(computedSha256: String?, expectedSha256: String?): Boolean {
        val normalizedComputed = ReleaseAssetSelector.normalizeSha256(computedSha256) ?: return false
        val normalizedExpected = ReleaseAssetSelector.normalizeSha256(expectedSha256) ?: return false
        return MessageDigest.isEqual(
            normalizedComputed.toByteArray(Charsets.US_ASCII),
            normalizedExpected.toByteArray(Charsets.US_ASCII)
        )
    }

    /**
     * Verifies [computedSha256] against [expectedSha256].
     *
     * - On mismatch or invalid expected hash: IMMEDIATELY deletes [partFile] (and any stale [finalFile])
     *   and returns `false`.
     * - On match: atomically promotes [partFile] to [finalFile] and returns `true`.
     */
    fun verifyAndPromote(
        partFile: File,
        finalFile: File,
        computedSha256: String,
        expectedSha256: String?
    ): Boolean {
        if (!hashesMatch(computedSha256, expectedSha256)) {
            safeDelete(partFile)
            safeDelete(finalFile)
            return false
        }

        if (!partFile.exists() || !partFile.isFile) {
            safeDelete(finalFile)
            return false
        }

        if (finalFile.exists()) {
            safeDelete(finalFile)
        }

        if (partFile.renameTo(finalFile)) {
            return finalFile.exists()
        }

        // Fallback if filesystem rename fails (e.g. Windows/FUSE semantics)
        return try {
            partFile.copyTo(finalFile, overwrite = true)
            safeDelete(partFile)
            val reVerified = hashesMatch(computeSha256(finalFile), expectedSha256)
            if (!reVerified) {
                safeDelete(finalFile)
            }
            reVerified
        } catch (_: Exception) {
            safeDelete(partFile)
            safeDelete(finalFile)
            false
        }
    }

    /**
     * Streams [file] to verify its SHA-256 against [expectedSha256].
     * Immediately deletes [file] if verification fails or [expectedSha256] is invalid.
     */
    fun verifyFileOrDelete(file: File, expectedSha256: String?): Boolean {
        if (!file.exists() || !file.isFile) return false
        val expected = ReleaseAssetSelector.normalizeSha256(expectedSha256)
        if (expected == null) {
            safeDelete(file)
            return false
        }
        val computed = try {
            computeSha256(file)
        } catch (_: Exception) {
            safeDelete(file)
            return false
        }
        if (!hashesMatch(computed, expected)) {
            safeDelete(file)
            return false
        }
        return true
    }

    /**
     * Cleans up stale `.apk` and `.part` files inside [updatesDir].
     */
    fun cleanupStaleFiles(updatesDir: File, keepFile: File? = null) {
        if (!updatesDir.exists() || !updatesDir.isDirectory) return
        val keepCanonical = keepFile?.let { runCatching { it.canonicalPath }.getOrNull() }
        updatesDir.listFiles()?.forEach { child ->
            if (!child.isFile) return@forEach
            val lower = child.name.lowercase(Locale.ROOT)
            if (!lower.endsWith(".apk") && !lower.endsWith(".part")) return@forEach
            val childCanonical = runCatching { child.canonicalPath }.getOrNull()
            if (keepCanonical != null && childCanonical == keepCanonical) return@forEach
            safeDelete(child)
        }
    }

    fun safeDelete(file: File?) {
        if (file == null) return
        runCatching {
            if (file.exists()) {
                file.delete()
            }
        }
    }

    private fun ByteArray.toLowerHex(): String {
        val hexChars = "0123456789abcdef"
        val result = CharArray(size * 2)
        for (i in indices) {
            val v = this[i].toInt() and 0xFF
            result[i * 2] = hexChars[v ushr 4]
            result[i * 2 + 1] = hexChars[v and 0x0F]
        }
        return String(result)
    }
}
