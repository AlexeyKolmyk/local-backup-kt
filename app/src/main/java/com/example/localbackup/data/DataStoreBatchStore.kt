package com.example.localbackup.data

import androidx.datastore.core.DataStore
import com.example.localbackup.backup.BatchStore
import com.example.localbackup.model.BackupBatch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * [BatchStore] backed by one Typed DataStore instance. Every update transforms the value
 * DataStore passes in (the current one), never a stale snapshot, and suspends until written.
 */
class DataStoreBatchStore(private val dataStore: DataStore<LatestBatchRecord>) : BatchStore {

    override val latestBatch: Flow<BackupBatch?> = dataStore.data.map { it.latestBatch }

    override suspend fun update(transform: (BackupBatch?) -> BackupBatch?) {
        dataStore.updateData { current -> current.copy(latestBatch = transform(current.latestBatch)) }
    }
}
