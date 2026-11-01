package com.example.localbackup.model

import kotlinx.serialization.Serializable

/** Latest backup batch metadata. Only the most recent batch is persisted. */
@Serializable
data class BackupBatch(
    val id: String,
    val createdAt: Long,
    val status: BackupBatchStatus,
    val files: List<BackupFile>
)

/**
 * One file inside a batch.
 *
 * [sourceUri] and [destinationUri] are serialized Android `Uri` strings. They are not
 * persisted permission grants.
 */
@Serializable
data class BackupFile(
    val id: String,
    val name: String,
    val sourceUri: String,
    val destinationUri: String,
    val expectedSize: Long?,
    val status: BackupStatus,
    val errorMessage: String? = null
)

@Serializable
enum class BackupStatus { PENDING, COPYING, SUCCESS, FAILED, INTERRUPTED, CANCELLED }

@Serializable
enum class BackupBatchStatus { IN_PROGRESS, COMPLETED, INTERRUPTED, CANCELLED }
