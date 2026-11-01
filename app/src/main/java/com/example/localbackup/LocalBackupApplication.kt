package com.example.localbackup

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import com.example.localbackup.backup.BatchStore
import com.example.localbackup.backup.CreateBackupUseCase
import com.example.localbackup.backup.ReconcileBackupUseCase
import com.example.localbackup.copy.AppPrivateBackupFiles
import com.example.localbackup.copy.FileCopier
import com.example.localbackup.data.DataStoreBatchStore
import com.example.localbackup.data.LatestBatchSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

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

    /** Process-lifetime scope for one-off app work (startup reconciliation). */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Startup reconciliation, started on first access and run exactly once per process.
     * The UI awaits it before enabling new backups, so it never runs over a live job.
     */
    val startupReconciliation: Deferred<Result<Unit>> by lazy {
        val reconcile = ReconcileBackupUseCase(batchStore, backupFiles)
        appScope.async { runCatching { reconcile() } }
    }

    private companion object {
        const val LATEST_BATCH_FILE = "latest_batch.json"
    }
}
