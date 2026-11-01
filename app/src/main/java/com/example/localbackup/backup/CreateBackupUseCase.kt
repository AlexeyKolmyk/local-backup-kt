package com.example.localbackup.backup

import com.example.localbackup.model.BackupBatch
import com.example.localbackup.model.BackupBatchStatus
import com.example.localbackup.model.BackupFile
import com.example.localbackup.model.BackupStatus
import java.io.FileNotFoundException
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/**
 * Workflow for one backup batch: plan metadata, persist the initial batch, copy files
 * sequentially and record each outcome before moving on.
 *
 * An ordinary failure of one file is recorded as FAILED and the next file is still attempted.
 * [CancellationException] is never turned into FAILED; it propagates and stops the batch.
 * Operation-level problems (destination cannot be prepared, store fails) are thrown to the caller.
 */
class CreateBackupUseCase(
    private val sources: BackupSources,
    private val destinations: BackupDestinations,
    private val copier: BackupCopier,
    private val store: BatchStore,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val now: () -> Long = System::currentTimeMillis
) {

    /** @param onProgress transient per-chunk progress; may be called from a background thread. */
    suspend operator fun invoke(
        sourceUris: List<String>,
        onProgress: (FileProgress) -> Unit = {}
    ) {
        if (sourceUris.isEmpty()) return

        val batchId = newId()
        destinations.prepareBatch(batchId)

        val files = sourceUris.map { uri ->
            val info = sources.describe(uri)
            val fileId = newId()
            BackupFile(
                id = fileId,
                name = info.displayName,
                sourceUri = uri,
                destinationUri = destinations.destinationUri(batchId, fileId, info.displayName),
                expectedSize = info.size,
                status = BackupStatus.PENDING
            )
        }

        // Await the initial record before any byte is copied.
        store.update {
            BackupBatch(
                id = batchId,
                createdAt = now(),
                status = BackupBatchStatus.IN_PROGRESS,
                files = files
            )
        }

        for (file in files) {
            updateFile(batchId, file.id) { it.copy(status = BackupStatus.COPYING) }

            val errorMessage: String? = try {
                copier.copy(file.sourceUri, file.destinationUri) { copied ->
                    onProgress(FileProgress(file.id, copied))
                }
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                userMessage(e)
            }

            updateFile(batchId, file.id) {
                it.copy(
                    status = if (errorMessage == null) BackupStatus.SUCCESS else BackupStatus.FAILED,
                    errorMessage = errorMessage
                )
            }
        }

        // Every file was attempted: COMPLETED even if some entries FAILED.
        updateBatch(batchId) { it.copy(status = BackupBatchStatus.COMPLETED) }
    }

    private suspend fun updateBatch(batchId: String, transform: (BackupBatch) -> BackupBatch) {
        store.update { current -> if (current?.id == batchId) transform(current) else current }
    }

    private suspend fun updateFile(batchId: String, fileId: String, transform: (BackupFile) -> BackupFile) {
        updateBatch(batchId) { batch ->
            batch.copy(files = batch.files.map { if (it.id == fileId) transform(it) else it })
        }
    }

    /** Short, user-facing reason; avoids leaking absolute paths from I/O exception messages. */
    private fun userMessage(e: Exception): String = when (e) {
        is SecurityException -> "No permission to read the file"
        is FileNotFoundException -> "File not found or not readable"
        else -> e.message?.takeIf { it.isNotBlank() && '/' !in it }
            ?: "Copy failed (${e::class.java.simpleName})"
    }
}
