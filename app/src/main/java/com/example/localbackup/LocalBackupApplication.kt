package com.example.localbackup

import android.app.Application
import android.content.Context
import com.example.localbackup.backup.BatchStore
import com.example.localbackup.backup.CreateBackupUseCase
import com.example.localbackup.backup.InMemoryBatchStore
import com.example.localbackup.copy.AppPrivateBackupFiles
import com.example.localbackup.copy.FileCopier

class LocalBackupApplication : Application() {
    /** Manual dependency wiring; one instance per process. */
    val container: AppContainer by lazy { AppContainer(this) }
}

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val backupFiles = AppPrivateBackupFiles(appContext)

    /** TEMPORARY in-memory store; replaced by the DataStore implementation in Step 04. */
    val batchStore: BatchStore = InMemoryBatchStore()

    val createBackup = CreateBackupUseCase(
        sources = backupFiles,
        destinations = backupFiles,
        copier = FileCopier(appContext.contentResolver),
        store = batchStore
    )
}
