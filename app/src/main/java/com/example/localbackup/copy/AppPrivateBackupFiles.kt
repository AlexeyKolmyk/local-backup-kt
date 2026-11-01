package com.example.localbackup.copy

import android.content.Context
import android.net.Uri
import com.example.localbackup.backup.BackupDestinations
import com.example.localbackup.backup.BackupSources
import com.example.localbackup.backup.DestinationProbe
import com.example.localbackup.backup.SourceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android adapter: SAF source metadata, app-private destinations under `filesDir/backups`,
 * and destination checks for startup reconciliation.
 */
class AppPrivateBackupFiles(private val context: Context) :
    BackupSources, BackupDestinations, DestinationProbe {

    private val storage = BackupStorage(context.filesDir)

    override suspend fun describe(sourceUri: String): SourceInfo = withContext(Dispatchers.IO) {
        context.contentResolver.querySourceInfo(Uri.parse(sourceUri))
    }

    override suspend fun prepareBatch(batchId: String) {
        withContext(Dispatchers.IO) { storage.prepareBatchDir(batchId) }
    }

    override fun destinationUri(batchId: String, fileId: String, displayName: String): String =
        Uri.fromFile(storage.destinationFile(storage.batchDir(batchId), fileId, displayName)).toString()

    override suspend fun finalizedSize(destinationUri: String): Long? = withContext(Dispatchers.IO) {
        val file = fileOf(destinationUri)
        if (file.isFile) file.length() else null
    }

    override suspend fun deletePartial(destinationUri: String): Boolean = withContext(Dispatchers.IO) {
        val destination = fileOf(destinationUri)
        val partial = File(destination.parentFile, destination.name + FileCopier.PARTIAL_SUFFIX)
        !partial.exists() || partial.delete()
    }

    private fun fileOf(destinationUri: String): File =
        File(requireNotNull(Uri.parse(destinationUri).path) { "Invalid destination" })
}
