package com.example.localbackup.backup

import com.example.localbackup.model.BackupBatch
import com.example.localbackup.model.BackupBatchStatus
import com.example.localbackup.model.BackupStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

class CreateBackupUseCaseTest {

    private val store = InMemoryBatchStore()

    private val sources = object : BackupSources {
        override suspend fun describe(sourceUri: String) = SourceInfo(sourceUri.substringAfterLast('/'), 10L)
    }

    private val destinations = object : BackupDestinations {
        override suspend fun prepareBatch(batchId: String) = Unit
        override fun destinationUri(batchId: String, fileId: String, displayName: String) =
            "file:///backups/$batchId/${fileId}_$displayName"
    }

    /** Fake file system: finalized destinations (matched by suffix) and partial deletions. */
    private class FakeProbe(private val finalizedSuffixes: Map<String, Long> = emptyMap()) : DestinationProbe {
        val partialDeletes = mutableListOf<String>()

        override suspend fun finalizedSize(destinationUri: String): Long? =
            finalizedSuffixes.entries.firstOrNull { destinationUri.endsWith(it.key) }?.value

        override suspend fun deletePartial(destinationUri: String): Boolean {
            partialDeletes += destinationUri
            return true
        }
    }

    private var probe = FakeProbe()

    /** Records attempts in order and the persisted status seen at the moment of each copy. */
    private class FakeCopier(
        private val store: BatchStore,
        private val failures: Map<String, Throwable>
    ) : BackupCopier {
        val attempted = mutableListOf<String>()
        val statusDuringCopy = mutableListOf<BackupStatus?>()

        override suspend fun copy(
            sourceUri: String,
            destinationUri: String,
            onProgress: ((copiedBytes: Long) -> Unit)?
        ): Long {
            attempted += sourceUri
            statusDuringCopy += store.latestBatch.first()?.files?.single { it.sourceUri == sourceUri }?.status
            failures[sourceUri]?.let { throw it }
            return 10L
        }
    }

    private var nextId = 0
    private fun useCase(copier: BackupCopier) = CreateBackupUseCase(
        sources = sources,
        destinations = destinations,
        copier = copier,
        store = store,
        probe = probe,
        newId = { "id${nextId++}" },
        now = { 1_000L }
    )

    private suspend fun latest(): BackupBatch = requireNotNull(store.latestBatch.first())

    @Test
    fun failureOfOneFileDoesNotStopTheOthers() = runBlocking {
        val copier = FakeCopier(store, mapOf("content://x/B" to IOException("simulated read error")))

        useCase(copier)(listOf("content://x/A", "content://x/B", "content://x/C"))

        assertEquals(listOf("content://x/A", "content://x/B", "content://x/C"), copier.attempted)
        assertEquals(List(3) { BackupStatus.COPYING }, copier.statusDuringCopy)

        val batch = latest()
        assertEquals(BackupBatchStatus.COMPLETED, batch.status)
        assertEquals(listOf("A", "B", "C"), batch.files.map { it.name })
        assertEquals(
            listOf(BackupStatus.SUCCESS, BackupStatus.FAILED, BackupStatus.SUCCESS),
            batch.files.map { it.status }
        )
        assertNull(batch.files[0].errorMessage)
        assertEquals("simulated read error", batch.files[1].errorMessage)
        assertNull(batch.files[2].errorMessage)
    }

    @Test
    fun destinationsAreUniqueForEqualDisplayNames() = runBlocking {
        val copier = FakeCopier(store, emptyMap())

        useCase(copier)(listOf("content://one/same.pdf", "content://two/same.pdf"))

        val destinations = latest().files.map { it.destinationUri }
        assertEquals(2, destinations.toSet().size)
    }

    private suspend fun runExpectingCancellation(copier: BackupCopier, uris: List<String>) {
        try {
            useCase(copier)(uris)
            fail("CancellationException expected")
        } catch (expected: CancellationException) {
            // ok: cancellation must propagate, not be swallowed
        }
    }

    private val abc = listOf("content://x/A", "content://x/B", "content://x/C")

    @Test
    fun userCancellationOnSecondFileRecordsCancelled() = runBlocking {
        val copier = FakeCopier(store, mapOf("content://x/B" to UserCancellationException()))

        runExpectingCancellation(copier, abc)

        assertEquals(listOf("content://x/A", "content://x/B"), copier.attempted) // C never attempted
        val batch = latest()
        assertEquals(BackupBatchStatus.CANCELLED, batch.status)
        assertEquals(
            listOf(BackupStatus.SUCCESS, BackupStatus.CANCELLED, BackupStatus.CANCELLED),
            batch.files.map { it.status }
        )
        assertEquals(listOf(batch.files[1].destinationUri), probe.partialDeletes) // active partial cleaned
    }

    @Test
    fun otherCancellationIsRecordedAsInterruptedNotFailed() = runBlocking {
        val copier = FakeCopier(store, mapOf("content://x/B" to CancellationException("screen closed")))

        runExpectingCancellation(copier, abc)

        assertEquals(listOf("content://x/A", "content://x/B"), copier.attempted)
        val batch = latest()
        assertEquals(BackupBatchStatus.INTERRUPTED, batch.status)
        assertEquals(
            listOf(BackupStatus.SUCCESS, BackupStatus.INTERRUPTED, BackupStatus.INTERRUPTED),
            batch.files.map { it.status }
        )
    }

    @Test
    fun cancelRightAfterFinalizingKeepsThatFileSuccessful() = runBlocking {
        // B was fully written and renamed, but cancellation arrived before SUCCESS was recorded.
        probe = FakeProbe(finalizedSuffixes = mapOf("_B" to 10L))
        val copier = FakeCopier(store, mapOf("content://x/B" to UserCancellationException()))

        runExpectingCancellation(copier, abc)

        val batch = latest()
        assertEquals(
            listOf(BackupStatus.SUCCESS, BackupStatus.SUCCESS, BackupStatus.CANCELLED),
            batch.files.map { it.status }
        )
        assertEquals(BackupBatchStatus.CANCELLED, batch.status)
    }

    @Test
    fun cancelWhenNothingIsLeftKeepsCompleted() = runBlocking {
        probe = FakeProbe(finalizedSuffixes = mapOf("_C" to 10L))
        val copier = FakeCopier(store, mapOf("content://x/C" to UserCancellationException()))

        runExpectingCancellation(copier, abc)

        val batch = latest()
        assertEquals(List(3) { BackupStatus.SUCCESS }, batch.files.map { it.status })
        assertEquals(BackupBatchStatus.COMPLETED, batch.status)
    }

    @Test
    fun emptySelectionDoesNothing() = runBlocking {
        useCase(FakeCopier(store, emptyMap()))(emptyList())

        assertNull(store.latestBatch.first())
    }
}
