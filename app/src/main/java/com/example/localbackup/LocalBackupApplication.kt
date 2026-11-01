package com.example.localbackup

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import com.example.localbackup.backup.BatchStore
import com.example.localbackup.backup.CreateBackupUseCase
import com.example.localbackup.copy.AppPrivateBackupFiles
import com.example.localbackup.copy.FileCopier
import com.example.localbackup.data.DataStoreBatchStore
import com.example.localbackup.data.LatestBatchSerializer

class LocalBackupApplication : Application() {
    /** Manual dependency wiring; one instance per process. */
    val container: AppContainer by lazy { AppContainer(this) }
}

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val backupFiles = AppPrivateBackupFiles(appContext)

    /** The only DataStore instance for this file in the process. */
    private val latestBatchDataStore = DataStoreFactory.create(
        serializer = LatestBatchSerializer,
        produceFile = { appContext.dataStoreFile(LATEST_BATCH_FILE) }
    )

    val batchStore: BatchStore = DataStoreBatchStore(latestBatchDataStore)

    val createBackup = CreateBackupUseCase(
        sources = backupFiles,
        destinations = backupFiles,
        copier = FileCopier(appContext.contentResolver),
        store = batchStore
    )

    private companion object {
        const val LATEST_BATCH_FILE = "latest_batch.json"
    }
}
