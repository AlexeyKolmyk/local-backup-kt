package com.example.localbackup.backup

import com.example.localbackup.model.BackupBatch
import kotlinx.coroutines.flow.Flow

/** Holds only the latest backup batch. Single source of truth for batch status. */
interface BatchStore {
    /** Emits the latest batch, or null when none was ever recorded. Read errors are thrown. */
    val latestBatch: Flow<BackupBatch?>

    /** Atomically replaces the stored batch with [transform] applied to the current value. */
    suspend fun update(transform: (BackupBatch?) -> BackupBatch?)
}
