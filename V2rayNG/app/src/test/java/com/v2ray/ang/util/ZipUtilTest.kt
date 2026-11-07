package com.v2ray.ang.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipOutputStream

class ZipUtilTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun unzipToFolderExtractsNestedFile() {
        val archive = createArchive("nested/config" to "value".toByteArray())
        val destination = File(tempDir, "destination")

        assertTrue(ZipUtil.unzipToFolder(archive, destination.absolutePath))
        assertEquals("value", File(destination, "nested/config").readText())
    }

    @Test
    fun extractArchiveRejectsPathTraversal() {
        val archive = createArchive("../outside" to "overwrite".toByteArray())
        val outside = File(tempDir, "outside")

        assertArchiveRejected(archive)

        assertFalse(outside.exists())
    }

    @Test
    fun extractArchiveRejectsOversizedArchive() {
        val archive = createArchive("config" to byteArrayOf(1))

        assertArchiveRejected(
            archive,
            extractionLimits(maxArchiveBytes = archive.length() - 1),
        )
    }

    @Test
    fun extractArchiveRejectsTooManyEntries() {
        val archive = createArchive(
            "first" to byteArrayOf(1),
            "second" to byteArrayOf(2),
        )
        assertArchiveRejected(archive, extractionLimits(maxEntries = 1))
    }

    @Test
    fun extractArchiveRejectsOversizedEntry() {
        val archive = createArchive("config" to ByteArray(5))

        assertArchiveRejected(archive, extractionLimits(maxEntryBytes = 4))
    }

    @Test
    fun extractArchiveRejectsExcessiveTotalSize() {
        val archive = createArchive(
            "first" to ByteArray(3),
            "second" to ByteArray(3),
        )
        assertArchiveRejected(archive, extractionLimits(maxTotalBytes = 5))
    }

    @Test
    fun extractArchiveRejectsExcessiveCompressionRatio() {
        val archive = createArchive("config" to ByteArray(4096))

        assertArchiveRejected(archive, extractionLimits(maxCompressionRatio = 2.0))
    }

    @Test
    fun extractArchiveRejectsDuplicateCanonicalDestination() {
        val archive = createArchive(
            "config" to byteArrayOf(1),
            "nested/../config" to byteArrayOf(2),
        )
        assertArchiveRejected(archive)
    }

    @Test
    fun extractArchiveRejectsEntryBelowFile() {
        val archive = createArchive(
            "parent" to byteArrayOf(1),
            "parent/child" to byteArrayOf(2),
        )
        assertArchiveRejected(archive)
    }

    @Test
    fun zipFromFolderPacksTopLevelFilesAndRoundTrips() {
        val source = File(tempDir, "source").apply { mkdirs() }
        File(source, "one.txt").writeText("one")
        File(source, "two.bin").writeBytes(byteArrayOf(1, 2, 3))
        val archive = File(tempDir, "out.zip")

        assertTrue(ZipUtil.zipFromFolder(source.absolutePath, archive.absolutePath))

        // Entries are the top-level files only (the backup source is a flat MMKV dump).
        java.util.zip.ZipFile(archive).use { zip ->
            assertEquals(
                setOf("one.txt", "two.bin"),
                java.util.Collections.list(zip.entries()).map { it.name }.toSet(),
            )
        }

        // The archive must pass the hardened extractor unchanged (round-trip).
        val destination = File(tempDir, "destination")
        assertTrue(ZipUtil.unzipToFolder(archive, destination.absolutePath))
        assertEquals("one", File(destination, "one.txt").readText())
        assertTrue(File(destination, "two.bin").readBytes().contentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun zipFromFolderReturnsFalseForEmptyOrMissingFolders() {
        val empty = File(tempDir, "empty").apply { mkdirs() }
        assertFalse(ZipUtil.zipFromFolder(empty.absolutePath, File(tempDir, "a.zip").absolutePath))
        assertFalse(
            ZipUtil.zipFromFolder(
                File(tempDir, "missing").absolutePath,
                File(tempDir, "b.zip").absolutePath,
            )
        )
        assertFalse(File(tempDir, "a.zip").exists())
        assertFalse(File(tempDir, "b.zip").exists())
    }

    private fun assertArchiveRejected(
        archive: File,
        limits: ZipUtil.ExtractionLimits = extractionLimits(),
    ) {
        val destination = File(tempDir, "destination")
        assertThrows(ZipException::class.java) {
            ZipUtil.extractArchive(archive, destination, limits)
        }
        assertFalse(destination.exists())
    }

    private fun createArchive(vararg entries: Pair<String, ByteArray>): File {
        val archive = File.createTempFile("archive", ".zip", tempDir)
        ZipOutputStream(archive.outputStream()).use { output ->
            entries.forEach { (name, contents) ->
                output.putNextEntry(ZipEntry(name))
                output.write(contents)
                output.closeEntry()
            }
        }
        return archive
    }

    private fun extractionLimits(
        maxArchiveBytes: Long = 1024L * 1024L,
        maxEntries: Int = 10,
        maxEntryBytes: Long = 16L * 1024L,
        maxTotalBytes: Long = 32L * 1024L,
        maxCompressionRatio: Double = 1000.0,
    ) = ZipUtil.ExtractionLimits(
        maxArchiveBytes = maxArchiveBytes,
        maxEntries = maxEntries,
        maxEntryBytes = maxEntryBytes,
        maxTotalBytes = maxTotalBytes,
        maxCompressionRatio = maxCompressionRatio,
    )
}
