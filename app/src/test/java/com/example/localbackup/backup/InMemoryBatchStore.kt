package com.example.localbackup.backup

import com.example.localbackup.model.BackupBatch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Test fake for [BatchStore]; production uses DataStoreBatchStore. */
class InMemoryBatchStore : BatchStore {
    private val state = MutableStateFlow<BackupBatch?>(null)

    override val latestBatch: Flow<BackupBatch?> = state.asStateFlow()

    override suspend fun update(transform: (BackupBatch?) -> BackupBatch?) {
        state.update(transform)
    }
}
