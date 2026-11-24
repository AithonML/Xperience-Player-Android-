package com.xperience.player.service.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest

class StreamingSha256VerifierTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun streamCopyAndHashMatchesStandardSha256VectorAndReportsProgress() {
        // SHA-256("hello world")
        val payload = "hello world".toByteArray(Charsets.UTF_8)
        val expectedHash = "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9"

        val partFile = File(tempFolder.root, "update.apk.part")
        val progressSamples = mutableListOf<Pair<Long, Long>>()

        val computed = StreamingSha256Verifier.streamCopyAndHash(
            input = ByteArrayInputStream(payload),
            destinationPartFile = partFile,
            expectedTotalBytes = payload.size.toLong(),
            bufferSize = 4,
            onProgress = { read, total -> progressSamples.add(read to total) }
        )

        assertEquals(expectedHash, computed)
        assertTrue(partFile.exists())
        assertEquals(payload.size.toLong(), partFile.length())
        assertTrue("Expected multi-chunk progress updates", progressSamples.size >= 3)
        assertEquals(payload.size.toLong() to payload.size.toLong(), progressSamples.last())
    }

    @Test
    fun verifyAndPromotePromotesPartFileWhenHashMatches() {
        val payload = ByteArray(48 * 1024) { idx -> (idx % 251).toByte() }
        val expected = sha256Hex(payload)

        val partFile = File(tempFolder.root, "app.apk.part")
        val finalFile = File(tempFolder.root, "app.apk")

        val computed = StreamingSha256Verifier.streamCopyAndHash(
            input = ByteArrayInputStream(payload),
            destinationPartFile = partFile,
            expectedTotalBytes = payload.size.toLong()
        )

        val ok = StreamingSha256Verifier.verifyAndPromote(
            partFile = partFile,
            finalFile = finalFile,
            computedSha256 = computed,
            expectedSha256 = "sha256:${expected.uppercase()}"
        )

        assertTrue(ok)
        assertFalse("Temp .part file should be moved/removed after promotion", partFile.exists())
        assertTrue("Verified .apk file must exist", finalFile.exists())
        assertEquals(expected, StreamingSha256Verifier.computeSha256(finalFile))
    }

    @Test
    fun verifyAndPromoteImmediatelyDeletesTempFileOnHashMismatch() {
        val payload = "corrupted-apk-bytes".toByteArray(Charsets.UTF_8)
        val wrongExpectedHash = "0".repeat(64)

        val partFile = File(tempFolder.root, "tampered.apk.part")
        val finalFile = File(tempFolder.root, "tampered.apk")

        val computed = StreamingSha256Verifier.streamCopyAndHash(
            input = ByteArrayInputStream(payload),
            destinationPartFile = partFile
        )
        assertTrue(partFile.exists())

        val ok = StreamingSha256Verifier.verifyAndPromote(
            partFile = partFile,
            finalFile = finalFile,
            computedSha256 = computed,
            expectedSha256 = wrongExpectedHash
        )

        assertFalse("Verification MUST fail on SHA-256 mismatch", ok)
        assertFalse("Temp .part file MUST be deleted immediately on mismatch", partFile.exists())
        assertFalse("Final .apk MUST NOT exist on mismatch", finalFile.exists())
    }

    @Test
    fun verifyAndPromoteFailsClosedAndDeletesTempFileWhenExpectedHashMissing() {
        val partFile = File(tempFolder.root, "missing-hash.apk.part")
        val finalFile = File(tempFolder.root, "missing-hash.apk")
        val computed = StreamingSha256Verifier.streamCopyAndHash(
            input = ByteArrayInputStream("apk".toByteArray()),
            destinationPartFile = partFile
        )

        assertFalse(StreamingSha256Verifier.verifyAndPromote(partFile, finalFile, computed, null))
        assertFalse(partFile.exists())
        assertFalse(finalFile.exists())
    }

    @Test
    fun verifyFileOrDeleteDeletesTamperedFileAndPreservesValidFile() {
        val validBytes = "authentic-installer".toByteArray()
        val expectedHash = sha256Hex(validBytes)

        val validFile = File(tempFolder.root, "valid.apk").apply { writeBytes(validBytes) }
        assertTrue(StreamingSha256Verifier.verifyFileOrDelete(validFile, expectedHash))
        assertTrue(validFile.exists())

        val tamperedFile = File(tempFolder.root, "bad.apk").apply { writeBytes("modified".toByteArray()) }
        assertFalse(StreamingSha256Verifier.verifyFileOrDelete(tamperedFile, expectedHash))
        assertFalse("Tampered file must be deleted immediately", tamperedFile.exists())
    }

    @Test
    fun cleanupStaleFilesRemovesOldApksAndPartsOnly() {
        val dir = tempFolder.newFolder("updates")
        val oldApk = File(dir, "old.apk").apply { writeText("old") }
        val oldPart = File(dir, "old.apk.part").apply { writeText("partial") }
        val keepApk = File(dir, "current.apk").apply { writeText("keep") }
        val unrelated = File(dir, "notes.txt").apply { writeText("keep") }

        StreamingSha256Verifier.cleanupStaleFiles(dir, keepFile = keepApk)

        assertFalse(oldApk.exists())
        assertFalse(oldPart.exists())
        assertTrue(keepApk.exists())
        assertTrue(unrelated.exists())
    }

    private fun sha256Hex(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }
}
