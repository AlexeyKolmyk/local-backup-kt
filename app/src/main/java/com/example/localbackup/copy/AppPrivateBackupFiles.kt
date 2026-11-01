package com.example.localbackup.copy

import android.content.Context
import android.net.Uri
import com.example.localbackup.backup.BackupDestinations
import com.example.localbackup.backup.BackupSources
import com.example.localbackup.backup.SourceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Android adapter: SAF source metadata + app-private destinations under `filesDir/backups`. */
class AppPrivateBackupFiles(private val context: Context) : BackupSources, BackupDestinations {

    private val storage = BackupStorage(context.filesDir)

    override suspend fun describe(sourceUri: String): SourceInfo = withContext(Dispatchers.IO) {
        context.contentResolver.querySourceInfo(Uri.parse(sourceUri))
    }

    override suspend fun prepareBatch(batchId: String) {
        withContext(Dispatchers.IO) { storage.prepareBatchDir(batchId) }
    }

    override fun destinationUri(batchId: String, fileId: String, displayName: String): String =
        Uri.fromFile(storage.destinationFile(storage.batchDir(batchId), fileId, displayName)).toString()
}
