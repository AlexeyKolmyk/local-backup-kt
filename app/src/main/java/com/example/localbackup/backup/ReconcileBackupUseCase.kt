package com.example.localbackup.backup

import com.example.localbackup.model.BackupBatch
import com.example.localbackup.model.BackupBatchStatus
import com.example.localbackup.model.BackupFile
import com.example.localbackup.model.BackupStatus
import kotlinx.coroutines.flow.first
import kotlin.coroutines.cancellation.CancellationException

/**
 * Repairs the latest batch after the process died mid-backup. Never resumes copying.
 *
 * Only an `IN_PROGRESS` batch is touched. Recorded SUCCESS/FAILED results are preserved.
 * Each PENDING/COPYING item gets its `.partial` removed (best effort) and becomes INTERRUPTED,
 * unless a finalized destination exists whose size equals the known expected size, in which case
 * it is recovered as SUCCESS (a sanity check, not an integrity proof).
 *
 * Must run once per process, before any new backup can start, so it never races a live job.
 */
class ReconcileBackupUseCase(
    private val store: BatchStore,
    private val probe: DestinationProbe
) {

    suspend operator fun invoke() {
        val saved = store.latestBatch.first() ?: return
        if (saved.status != BackupBatchStatus.IN_PROGRESS) return

        val decisions: Map<String, BackupFile> = saved.files
            .filter { it.status.isUnfinished() }
            .associate { it.id to reconcile(it) }

        store.update { current ->
            if (current == null || current.id != saved.id || current.status != BackupBatchStatus.IN_PROGRESS) {
                current
            } else {
                val files = current.files.map { file ->
                    if (file.status.isUnfinished()) decisions[file.id] ?: interrupted(file) else file
                }
                current.copy(files = files, status = overallStatus(files))
            }
        }
    }

    private suspend fun reconcile(file: BackupFile): BackupFile {
        // A .partial is never a finished backup: always try to remove it.
        probeSafely { probe.deletePartial(file.destinationUri) }

        val finalSize = probeSafely { probe.finalizedSize(file.destinationUri) }
        return if (finalSize != null && file.expectedSize != null && finalSize == file.expectedSize) {
            file.copy(status = BackupStatus.SUCCESS, errorMessage = null)
        } else {
            interrupted(file)
        }
    }

    private fun interrupted(file: BackupFile) =
        file.copy(status = BackupStatus.INTERRUPTED, errorMessage = "Interrupted before completion")

    /** COMPLETED only if every file genuinely reached SUCCESS/FAILED; otherwise INTERRUPTED. */
    private fun overallStatus(files: List<BackupFile>): BackupBatchStatus =
        if (files.all { it.status == BackupStatus.SUCCESS || it.status == BackupStatus.FAILED }) {
            BackupBatchStatus.COMPLETED
        } else {
            BackupBatchStatus.INTERRUPTED
        }

    private fun BackupStatus.isUnfinished() = this == BackupStatus.PENDING || this == BackupStatus.COPYING

    /** Filesystem problems must not crash startup; they just make the item INTERRUPTED. */
    private suspend fun <T> probeSafely(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
}
