package com.example.localbackup.copy

import java.io.File
import java.io.IOException

/** Layout of app-private backups: `filesDir/backups/<batch-id>/<file-id>_<safe-name>`. */
class BackupStorage(private val filesDir: File) {

    /** Creates (if needed) and returns the directory for [batchId]. */
    fun batchDir(batchId: String): File = File(File(filesDir, BACKUPS_DIR), batchId)

    fun prepareBatchDir(batchId: String): File {
        val dir = batchDir(batchId)
        if (!dir.isDirectory && !dir.mkdirs()) {
            throw IOException("Cannot create backup folder")
        }
        return dir
    }

    /** Unique destination: the [fileId] prefix prevents collisions between equal display names. */
    fun destinationFile(batchDir: File, fileId: String, displayName: String): File =
        File(batchDir, "${fileId}_${safeFileName(displayName)}")

    companion object {
        const val BACKUPS_DIR = "backups"
        private const val MAX_NAME_LENGTH = 80

        fun safeFileName(displayName: String): String =
            displayName
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .trimStart('.')
                .take(MAX_NAME_LENGTH)
                .ifEmpty { "file" }
    }
}
