package com.example.localbackup.backup

import com.example.localbackup.model.BackupBatch
import com.example.localbackup.model.BackupBatchStatus
import com.example.localbackup.model.BackupFile
import com.example.localbackup.model.BackupStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ReconcileBackupUseCaseTest {

    /** Fake filesystem: finalized destinations with their sizes; records partial deletions. */
    private class FakeProbe(
        private val finalized: Map<String, Long> = emptyMap(),
        private val failing: Set<String> = emptySet()
    ) : DestinationProbe {
        val partialDeletes = mutableListOf<String>()
        var calls = 0
            private set

        override suspend fun finalizedSize(destinationUri: String): Long? {
            calls++
            if (destinationUri in failing) throw IOException("disk error")
            return finalized[destinationUri]
        }

        override suspend fun deletePartial(destinationUri: String): Boolean {
            calls++
            partialDeletes += destinationUri
            return true
        }
    }

    private val store = InMemoryBatchStore()

    private fun file(id: String, status: BackupStatus, size: Long? = 100L) =
        BackupFile(id, "$id.bin", "content://src/$id", "file:///backups/b/$id", size, status)

    private fun batch(status: BackupBatchStatus, vararg files: BackupFile) =
        BackupBatch(id = "b", createdAt = 1L, status = status, files = files.toList())

    private suspend fun save(batch: BackupBatch?) = store.update { batch }
    private suspend fun latest() = requireNotNull(store.latestBatch.first())

    @Test
    fun unfinishedItemsBecomeInterruptedAndSuccessIsPreserved() = runBlocking {
        save(
            batch(
                BackupBatchStatus.IN_PROGRESS,
                file("A", BackupStatus.SUCCESS),
                file("B", BackupStatus.COPYING),
                file("C", BackupStatus.PENDING)
            )
        )
        val probe = FakeProbe() // no finalized destination for B or C

        ReconcileBackupUseCase(store, probe)()

        val result = latest()
        assertEquals(
            listOf(BackupStatus.SUCCESS, BackupStatus.INTERRUPTED, BackupStatus.INTERRUPTED),
            result.files.map { it.status }
        )
        assertEquals(BackupBatchStatus.INTERRUPTED, result.status)
        assertEquals(listOf("file:///backups/b/B", "file:///backups/b/C"), probe.partialDeletes)
    }

    @Test
    fun failedResultIsPreserved() = runBlocking {
        save(
            batch(
                BackupBatchStatus.IN_PROGRESS,
                file("A", BackupStatus.FAILED).copy(errorMessage = "No permission to read the file"),
                file("B", BackupStatus.PENDING)
            )
        )

        ReconcileBackupUseCase(store, FakeProbe())()

        val a = latest().files[0]
        assertEquals(BackupStatus.FAILED, a.status)
        assertEquals("No permission to read the file", a.errorMessage)
    }

    @Test
    fun finalizedDestinationWithExpectedSizeIsRecoveredAsSuccess() = runBlocking {
        save(
            batch(
                BackupBatchStatus.IN_PROGRESS,
                file("A", BackupStatus.SUCCESS),
                file("B", BackupStatus.COPYING, size = 100L)
            )
        )

        ReconcileBackupUseCase(store, FakeProbe(finalized = mapOf("file:///backups/b/B" to 100L)))()

        val result = latest()
        assertEquals(BackupStatus.SUCCESS, result.files[1].status)
        assertNull(result.files[1].errorMessage)
        assertEquals(BackupBatchStatus.COMPLETED, result.status)
    }

    @Test
    fun sizeMismatchOrUnknownSizeStaysInterrupted() = runBlocking {
        save(
            batch(
                BackupBatchStatus.IN_PROGRESS,
                file("B", BackupStatus.COPYING, size = 100L),
                file("C", BackupStatus.COPYING, size = null)
            )
        )
        val probe = FakeProbe(finalized = mapOf("file:///backups/b/B" to 50L, "file:///backups/b/C" to 10L))

        ReconcileBackupUseCase(store, probe)()

        assertTrue(latest().files.all { it.status == BackupStatus.INTERRUPTED })
        assertEquals(BackupBatchStatus.INTERRUPTED, latest().status)
    }

    @Test
    fun filesystemErrorMarksItemInterruptedWithoutCrashing() = runBlocking {
        save(batch(BackupBatchStatus.IN_PROGRESS, file("B", BackupStatus.COPYING)))

        ReconcileBackupUseCase(store, FakeProbe(failing = setOf("file:///backups/b/B")))()

        assertEquals(BackupStatus.INTERRUPTED, latest().files.single().status)
        assertEquals(BackupBatchStatus.INTERRUPTED, latest().status)
    }

    @Test
    fun terminalBatchIsLeftUntouched() = runBlocking {
        val completed = batch(
            BackupBatchStatus.COMPLETED,
            file("A", BackupStatus.SUCCESS),
            file("B", BackupStatus.FAILED)
        )
        save(completed)
        val probe = FakeProbe()

        ReconcileBackupUseCase(store, probe)()

        assertEquals(completed, latest())
        assertEquals(0, probe.calls)
    }

    @Test
    fun noSavedBatchDoesNothing() = runBlocking {
        ReconcileBackupUseCase(store, FakeProbe())()

        assertNull(store.latestBatch.first())
    }
}
