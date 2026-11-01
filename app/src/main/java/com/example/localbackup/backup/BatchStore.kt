package com.example.localbackup.backup

import com.example.localbackup.model.BackupBatch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Holds only the latest backup batch. Single source of truth for batch status. */
interface BatchStore {
    val latestBatch: Flow<BackupBatch?>

    /** Atomically replaces the stored batch with [transform] applied to the current value. */
    suspend fun update(transform: (BackupBatch?) -> BackupBatch?)
}

/**
 * TEMPORARY (Step 03): process-memory store. Step 04 replaces it with a DataStore-backed
 * implementation; it must not be kept as a second persistence mechanism.
 */
class InMemoryBatchStore : BatchStore {
    private val state = MutableStateFlow<BackupBatch?>(null)

    override val latestBatch: Flow<BackupBatch?> = state.asStateFlow()

    override suspend fun update(transform: (BackupBatch?) -> BackupBatch?) {
        state.update(transform)
    }
}
