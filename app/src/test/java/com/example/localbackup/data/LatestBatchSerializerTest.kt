package com.example.localbackup.data

import androidx.datastore.core.CorruptionException
import com.example.localbackup.model.BackupBatch
import com.example.localbackup.model.BackupBatchStatus
import com.example.localbackup.model.BackupFile
import com.example.localbackup.model.BackupStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class LatestBatchSerializerTest {

    private suspend fun roundTrip(record: LatestBatchRecord): LatestBatchRecord {
        val output = ByteArrayOutputStream()
        LatestBatchSerializer.writeTo(record, output)
        return LatestBatchSerializer.readFrom(ByteArrayInputStream(output.toByteArray()))
    }

    @Test
    fun roundTripKeepsSuccessAndFailedOutcomes() = runBlocking {
        val record = LatestBatchRecord(
            BackupBatch(
                id = "batch-1",
                createdAt = 1_700_000_000_000,
                status = BackupBatchStatus.COMPLETED,
                files = listOf(
                    BackupFile("f1", "a.pdf", "content://p/a", "file:///b/f1_a.pdf", 1234L, BackupStatus.SUCCESS),
                    BackupFile("f2", "b.jpg", "content://p/b", "file:///b/f2_b.jpg", null, BackupStatus.FAILED, "File not found or not readable")
                )
            )
        )

        assertEquals(record, roundTrip(record))
    }

    @Test
    fun roundTripKeepsEmptyState() = runBlocking {
        assertEquals(LatestBatchRecord(), roundTrip(LatestBatchRecord()))
    }

    @Test
    fun malformedDataIsReportedAsCorruptionNotEmpty() = runBlocking {
        try {
            LatestBatchSerializer.readFrom(ByteArrayInputStream("{not json".encodeToByteArray()))
            fail("CorruptionException expected")
        } catch (expected: CorruptionException) {
            // ok
        }
    }
}
