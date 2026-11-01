package com.example.localbackup.backup

import com.example.localbackup.model.BackupBatch
import com.example.localbackup.model.BackupBatchStatus
import com.example.localbackup.model.BackupFile
import com.example.localbackup.model.BackupStatus
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/**
 * Workflow for one backup batch: plan metadata, persist the initial batch, copy files
 * sequentially and record each outcome before moving on.
 *
 * An ordinary failure of one file is recorded as FAILED and the next file is still attempted.
 * [CancellationException] is never turned into FAILED: it stops the batch, a short
 * non-cancellable section records the outcome (CANCELLED for [UserCancellationException],
 * INTERRUPTED otherwise) and the exception is rethrown.
 * Operation-level problems (destination cannot be prepared, store fails) are thrown to the caller.
 */
class CreateBackupUseCase(
    private val sources: BackupSources,
    private val destinations: BackupDestinations,
    private val copier: BackupCopier,
    private val store: BatchStore,
    private val probe: DestinationProbe,
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

        try {
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

                // Once the copy finished (or failed), record it even if cancel arrives right now.
                withContext(NonCancellable) {
                    updateFile(batchId, file.id) {
                        it.copy(
                            status = if (errorMessage == null) BackupStatus.SUCCESS else BackupStatus.FAILED,
                            errorMessage = errorMessage
                        )
                    }
                }
            }

            // Every file was attempted: COMPLETED even if some entries FAILED.
            updateBatch(batchId) { it.copy(status = BackupBatchStatus.COMPLETED) }
        } catch (e: CancellationException) {
            withContext(NonCancellable) { recordStopped(batchId, userCancelled = e.isUserCancellation()) }
            throw e
        }
    }

    /**
     * Bounded cleanup after cancellation: keeps SUCCESS/FAILED, removes the active `.partial`,
     * marks unfinished files CANCELLED (user) or INTERRUPTED (anything else). If the active file
     * was already finalized with the expected size, it is kept as SUCCESS. If nothing was left
     * unfinished, the batch legitimately stays/becomes COMPLETED.
     */
    private suspend fun recordStopped(batchId: String, userCancelled: Boolean) {
        val current = store.latestBatch.first()?.takeIf { it.id == batchId } ?: return
        if (current.status != BackupBatchStatus.IN_PROGRESS) return

        val active = current.files.firstOrNull { it.status == BackupStatus.COPYING }
        val activeFinalized = active != null && cleanUpAndCheckFinalized(active)

        val stoppedStatus = if (userCancelled) BackupStatus.CANCELLED else BackupStatus.INTERRUPTED
        val stoppedMessage = if (userCancelled) null else "Interrupted before completion"

        updateBatch(batchId) { batch ->
            if (batch.status != BackupBatchStatus.IN_PROGRESS) return@updateBatch batch
            val updated = batch.files.map { file ->
                when {
                    file.status != BackupStatus.PENDING && file.status != BackupStatus.COPYING -> file
                    file.id == active?.id && activeFinalized ->
                        file.copy(status = BackupStatus.SUCCESS, errorMessage = null)
                    else -> file.copy(status = stoppedStatus, errorMessage = stoppedMessage)
                }
            }
            val anyStopped = updated.any { it.status == stoppedStatus }
            batch.copy(
                files = updated,
                status = when {
                    !anyStopped -> BackupBatchStatus.COMPLETED
                    userCancelled -> BackupBatchStatus.CANCELLED
                    else -> BackupBatchStatus.INTERRUPTED
                }
            )
        }
    }

    /** Best effort: delete the partial, then report whether a full-size final file exists. */
    private suspend fun cleanUpAndCheckFinalized(file: BackupFile): Boolean =
        try {
            probe.deletePartial(file.destinationUri)
            val size = probe.finalizedSize(file.destinationUri)
            size != null && file.expectedSize != null && size == file.expectedSize
        } catch (e: Exception) {
            false
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
