package com.example.localbackup.copy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class BackupInventoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val uuidA = "11111111-2222-3333-4444-555555555555"
    private val uuidB = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"

    private lateinit var root: File

    private fun file(relative: String, bytes: Int): File =
        File(root, relative).apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(bytes) { 7 })
        }

    private fun setUpTree(): BackupInventory {
        root = temp.newFolder("backups")
        file("batch1/${uuidA}_report.pdf", 1000)
        file("batch1/${uuidB}_photo.jpg.partial", 500) // unfinished copy
        file("batch2/${uuidB}_photo.jpg", 2048)
        file("batch2/nested/${uuidA}_deep.txt", 10) // not directly in a batch dir
        file("stray.txt", 5) // not inside a batch dir
        return BackupInventory(root)
    }

    private fun expectRejected(inventory: BackupInventory, id: String) {
        try {
            inventory.delete(id)
            fail("Deletion of '$id' must be rejected")
        } catch (expected: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun listsFinalizedCopiesFromAllBatchesWithActualSizes() {
        val inventory = setUpTree()

        val listed = inventory.list().sortedBy { it.id }

        assertEquals(listOf("batch1/${uuidA}_report.pdf", "batch2/${uuidB}_photo.jpg"), listed.map { it.id })
        assertEquals(listOf("report.pdf", "photo.jpg"), listed.map { it.displayName })
        assertEquals(listOf(1000L, 2048L), listed.map { it.sizeBytes })
        assertEquals(listOf("batch1", "batch2"), listed.map { it.batchId })
    }

    @Test
    fun missingRootMeansNoBackups() {
        assertTrue(BackupInventory(File(temp.root, "does-not-exist")).list().isEmpty())
    }

    @Test
    fun deletingOneCopyKeepsOthersAndRemovesEmptyBatchDir() {
        val inventory = setUpTree()

        inventory.delete("batch2/${uuidB}_photo.jpg")

        assertFalse(File(root, "batch2/${uuidB}_photo.jpg").exists())
        assertTrue(File(root, "batch1/${uuidA}_report.pdf").exists())
        assertTrue(File(root, "batch1/${uuidB}_photo.jpg.partial").exists())
        assertTrue(File(root, "batch2").exists()) // still holds the nested folder
        assertEquals(listOf("batch1/${uuidA}_report.pdf"), inventory.list().map { it.id })
    }

    @Test
    fun emptyBatchDirIsRemovedAfterLastCopy() {
        root = temp.newFolder("backups")
        file("batch1/${uuidA}_only.bin", 3)
        val inventory = BackupInventory(root)

        inventory.delete("batch1/${uuidA}_only.bin")

        assertFalse(File(root, "batch1").exists())
        assertTrue(root.exists())
    }

    @Test
    fun targetsOutsideTheBackupsRootAreRejected() {
        val inventory = setUpTree()
        val outside = File(temp.root, "outside.txt").apply { writeText("keep me") }

        expectRejected(inventory, "../outside.txt")
        expectRejected(inventory, "batch1/../../outside.txt")
        expectRejected(inventory, "stray.txt")
        expectRejected(inventory, "batch1")
        expectRejected(inventory, "batch2/nested/${uuidA}_deep.txt")
        expectRejected(inventory, "batch1/${uuidB}_photo.jpg.partial")
        expectRejected(inventory, "batch1/missing.bin")

        assertTrue(outside.exists())
        assertTrue(File(root, "stray.txt").exists())
        assertTrue(File(root, "batch1/${uuidB}_photo.jpg.partial").exists())
    }

    @Test
    fun symlinkEscapingTheRootIsNeitherListedNorDeleted() {
        val inventory = setUpTree()
        val outside = File(temp.root, "secret.txt").apply { writeText("keep me") }
        val link = File(root, "batch1/${uuidA}_link.txt")
        Files.createSymbolicLink(link.toPath(), outside.toPath())

        assertFalse(inventory.list().any { it.id.endsWith("_link.txt") })
        expectRejected(inventory, "batch1/${uuidA}_link.txt")

        assertTrue(outside.exists())
    }

    @Test
    fun displayNameKeepsNamesWithoutGeneratedPrefix() {
        assertEquals("plain.txt", BackupInventory.displayNameOf("plain.txt"))
        assertEquals("a_b.txt", BackupInventory.displayNameOf("${uuidA}_a_b.txt"))
    }
}
