package com.example.localbackup.copy

import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * One finalized backup copy physically present on disk.
 *
 * [id] is the internal relative path `<batchDir>/<fileName>` taken from our own listing; it is the
 * only value used to locate the file. [displayName] is for humans only and never used as a path.
 */
data class StoredBackup(
    val id: String,
    val batchId: String,
    val displayName: String,
    val sizeBytes: Long,
    val lastModified: Long
)

/**
 * Inventory of app-private copies under `backups/<batchId>/`. Reads the file system, not
 * DataStore records. Blocking: call from `Dispatchers.IO`. Plain `java.io`, so JVM-testable.
 */
class BackupInventory(private val backupsRoot: File) {

    /** Finalized regular files from every batch directory; excludes `.partial`, folders and symlinks. */
    fun list(): List<StoredBackup> {
        if (!backupsRoot.isDirectory) return emptyList()
        return backupsRoot.listFiles().orEmpty()
            .filter { it.isDirectory && !it.isSymlink() }
            .flatMap { batchDir ->
                batchDir.listFiles().orEmpty()
                    .filter { it.isFinalizedCopy() }
                    .map { file ->
                        StoredBackup(
                            id = "${batchDir.name}/${file.name}",
                            batchId = batchDir.name,
                            displayName = displayNameOf(file.name),
                            sizeBytes = file.length(),
                            lastModified = file.lastModified()
                        )
                    }
            }
            .sortedWith(compareByDescending<StoredBackup> { it.lastModified }.thenBy { it.id })
    }

    /**
     * Deletes exactly the copy identified by [id] (from [list]). Rejects anything that is not an
     * existing finalized regular file directly inside a batch directory of the backups root:
     * path traversal, absolute paths, symlinks, `.partial` files and directories.
     * Removes the batch directory if it becomes empty.
     */
    fun delete(id: String) {
        val root = backupsRoot.canonicalFile
        val requested = File(backupsRoot, id)
        val target = requested.canonicalFile
        val batchDir = target.parentFile
        if (batchDir == null || batchDir.parentFile != root) {
            throw IllegalArgumentException("Not a backup file")
        }
        if (requested.isSymlink() || !target.isFinalizedCopy()) {
            throw IllegalArgumentException("Not a backup file")
        }
        if (!target.delete()) {
            throw IOException("Backup file could not be deleted")
        }
        if (batchDir.list()?.isEmpty() == true) {
            batchDir.delete()
        }
    }

    private fun File.isFinalizedCopy(): Boolean =
        isFile && !isSymlink() && !name.endsWith(FileCopier.PARTIAL_SUFFIX)

    private fun File.isSymlink(): Boolean = Files.isSymbolicLink(toPath())

    companion object {
        private val GENERATED_PREFIX =
            Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}_(.+)$")

        /** Strips our generated `<uuid>_` prefix; the rest is the sanitized original name. */
        fun displayNameOf(fileName: String): String =
            GENERATED_PREFIX.matchEntire(fileName)?.groupValues?.get(1) ?: fileName
    }
}
