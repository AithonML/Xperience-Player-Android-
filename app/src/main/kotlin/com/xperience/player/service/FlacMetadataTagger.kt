package com.xperience.player.service

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * High-performance, zero-dependency FLAC metadata tagger.
 * Injects or updates standard Vorbis Comments (TITLE, ARTIST, ALBUM, GENRE, DATE, ISRC)
 * and PICTURE (Front Cover) metadata blocks into FLAC lossless audio files according to
 * the official Xiph / FLAC container specification.
 */
object FlacMetadataTagger {

    private const val TAG = "FlacMetadataTagger"
    private const val VENDOR_STRING = "Xperience Music Player FLAC Engine"

    data class FlacTags(
        val title: String,
        val artist: String,
        val album: String = "",
        val genre: String = "",
        val year: String = "",
        val isrc: String = "",
        val coverArtBytes: ByteArray? = null
    )

    fun tagFlacFile(inputFile: File, tags: FlacTags): Boolean {
        if (!inputFile.exists() || inputFile.length() < 42) return false
        val tempOutput = File(inputFile.parentFile, "${inputFile.name}.tagged.tmp")

        try {
            FileInputStream(inputFile).use { fis ->
                // 1. Verify FLAC magic 'fLaC' (0x66, 0x4C, 0x61, 0x43)
                val magic = ByteArray(4)
                if (fis.read(magic) != 4) return false
                if (magic[0] != 'f'.code.toByte() ||
                    magic[1] != 'L'.code.toByte() ||
                    magic[2] != 'a'.code.toByte() ||
                    magic[3] != 'C'.code.toByte()
                ) {
                    return false
                }

                FileOutputStream(tempOutput).use { fos ->
                    fos.write(magic)

                    // 2. Read STREAMINFO block (Block type 0)
                    val header = ByteArray(4)
                    if (fis.read(header) != 4) return false
                    val blockType = header[0].toInt() and 0x7F
                    if (blockType != 0) {
                        // First block must be STREAMINFO
                        return false
                    }
                    val streamInfoLen = ((header[1].toInt() and 0xFF) shl 16) or
                            ((header[2].toInt() and 0xFF) shl 8) or
                            (header[3].toInt() and 0xFF)

                    val streamInfoData = ByteArray(streamInfoLen)
                    if (fis.read(streamInfoData) != streamInfoLen) return false

                    // STREAMINFO is not the last block since we are adding tags
                    fos.write(byteArrayOf(0x00, header[1], header[2], header[3]))
                    fos.write(streamInfoData)

                    // 3. Skip any existing VORBIS_COMMENT (4), PICTURE (6), or PADDING (1)
                    var isLast = (header[0].toInt() and 0x80) != 0
                    val retainedBlocks = mutableListOf<Pair<Byte, ByteArray>>()

                    while (!isLast) {
                        if (fis.read(header) != 4) break
                        isLast = (header[0].toInt() and 0x80) != 0
                        val type = header[0].toInt() and 0x7F
                        val len = ((header[1].toInt() and 0xFF) shl 16) or
                                ((header[2].toInt() and 0xFF) shl 8) or
                                (header[3].toInt() and 0xFF)

                        if (type == 4 || type == 6 || type == 1) {
                            // Skip old vorbis comment, old picture, old padding
                            var skipped = 0L
                            while (skipped < len) {
                                val s = fis.skip(len - skipped)
                                if (s <= 0) {
                                    val buf = ByteArray(minOf(len - skipped.toInt(), 8192))
                                    val r = fis.read(buf)
                                    if (r <= 0) break
                                    skipped += r
                                } else {
                                    skipped += s
                                }
                            }
                        } else {
                            // Retain other metadata blocks (e.g. CUESHEET, SEEKTABLE)
                            val data = ByteArray(len)
                            if (fis.read(data) == len) {
                                retainedBlocks.add(Pair(type.toByte(), data))
                            }
                        }
                    }

                    // 4. Build Vorbis Comment Block (type 4)
                    val vorbisData = buildVorbisCommentBlock(tags)
                    val pictureData = tags.coverArtBytes?.takeIf { it.isNotEmpty() }?.let { buildPictureBlock(it) }

                    // Write any retained blocks (marked not last)
                    for (block in retainedBlocks) {
                        val blkLen = block.second.size
                        fos.write(byteArrayOf(
                            (block.first.toInt() and 0x7F).toByte(),
                            ((blkLen shr 16) and 0xFF).toByte(),
                            ((blkLen shr 8) and 0xFF).toByte(),
                            (blkLen and 0xFF).toByte()
                        ))
                        fos.write(block.second)
                    }

                    // Write Vorbis Comment Block
                    val isVorbisLast = pictureData == null
                    fos.write(byteArrayOf(
                        (if (isVorbisLast) 0x84 else 0x04).toByte(),
                        ((vorbisData.size shr 16) and 0xFF).toByte(),
                        ((vorbisData.size shr 8) and 0xFF).toByte(),
                        (vorbisData.size and 0xFF).toByte()
                    ))
                    fos.write(vorbisData)

                    // Write Picture Block if present (marked as last metadata block = 0x86)
                    if (pictureData != null) {
                        fos.write(byteArrayOf(
                            0x86.toByte(), // 0x80 (last) | 0x06 (PICTURE)
                            ((pictureData.size shr 16) and 0xFF).toByte(),
                            ((pictureData.size shr 8) and 0xFF).toByte(),
                            (pictureData.size and 0xFF).toByte()
                        ))
                        fos.write(pictureData)
                    }

                    // 5. Stream all remaining audio frames verbatim
                    val buffer = ByteArray(65536)
                    var read: Int
                    while (fis.read(buffer).also { read = it } != -1) {
                        fos.write(buffer, 0, read)
                    }
                }
            }

            if (tempOutput.exists() && tempOutput.length() > 42) {
                inputFile.delete()
                return tempOutput.renameTo(inputFile)
            }
            tempOutput.delete()
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error tagging FLAC file: ${e.message}", e)
            tempOutput.delete()
            return false
        }
    }

    private fun buildVorbisCommentBlock(tags: FlacTags): ByteArray {
        val baos = ByteArrayOutputStream()

        fun writeInt32LE(value: Int) {
            baos.write(value and 0xFF)
            baos.write((value shr 8) and 0xFF)
            baos.write((value shr 16) and 0xFF)
            baos.write((value shr 24) and 0xFF)
        }

        // 1. Vendor string
        val vendorBytes = VENDOR_STRING.toByteArray(Charsets.UTF_8)
        writeInt32LE(vendorBytes.size)
        baos.write(vendorBytes)

        // 2. User comments
        val comments = mutableListOf<String>()
        if (tags.title.isNotBlank()) comments.add("TITLE=${tags.title}")
        if (tags.artist.isNotBlank()) {
            comments.add("ARTIST=${tags.artist}")
            comments.add("ALBUMARTIST=${tags.artist}")
        }
        if (tags.album.isNotBlank()) comments.add("ALBUM=${tags.album}")
        if (tags.genre.isNotBlank()) comments.add("GENRE=${tags.genre}")
        if (tags.year.isNotBlank()) comments.add("DATE=${tags.year}")
        if (tags.isrc.isNotBlank()) comments.add("ISRC=${tags.isrc}")

        writeInt32LE(comments.size)
        for (c in comments) {
            val cBytes = c.toByteArray(Charsets.UTF_8)
            writeInt32LE(cBytes.size)
            baos.write(cBytes)
        }

        return baos.toByteArray()
    }

    private fun buildPictureBlock(pictureBytes: ByteArray): ByteArray {
        val mimeType = "image/jpeg"
        val mimeBytes = mimeType.toByteArray(Charsets.US_ASCII)
        val description = "Front Cover".toByteArray(Charsets.UTF_8)

        val totalSize = 4 + 4 + mimeBytes.size + 4 + description.size + 4 + 4 + 4 + 4 + 4 + pictureBytes.size
        val bb = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)

        bb.putInt(3) // Picture type: 3 = Cover (front)
        bb.putInt(mimeBytes.size)
        bb.put(mimeBytes)
        bb.putInt(description.size)
        bb.put(description)
        bb.putInt(0) // Width (0 if unknown)
        bb.putInt(0) // Height (0 if unknown)
        bb.putInt(24) // Color depth: 24 bits
        bb.putInt(0) // Number of colors: 0 (not indexed)
        bb.putInt(pictureBytes.size)
        bb.put(pictureBytes)

        return bb.array()
    }
}
