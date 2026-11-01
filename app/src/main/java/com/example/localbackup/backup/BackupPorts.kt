package com.example.localbackup.backup

/** Provider metadata of a picked document. [size] is null when unknown. */
data class SourceInfo(val displayName: String, val size: Long?)

/** Reads metadata of a selected source document (a serialized Android Uri string). */
interface BackupSources {
    suspend fun describe(sourceUri: String): SourceInfo
}

/** Plans and prepares destination locations for a batch. */
interface BackupDestinations {
    /** Creates the batch destination. Throws if storage is unavailable. */
    suspend fun prepareBatch(batchId: String)

    /** Unique destination URI string for one file; the file itself is created by the copier. */
    fun destinationUri(batchId: String, fileId: String, displayName: String): String
}

/** Copies one source to one destination. Throws on failure; cancellation propagates. */
interface BackupCopier {
    suspend fun copy(
        sourceUri: String,
        destinationUri: String,
        onProgress: ((copiedBytes: Long) -> Unit)? = null
    ): Long
}
