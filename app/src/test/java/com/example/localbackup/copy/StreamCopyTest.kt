package com.example.localbackup.copy

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import kotlin.random.Random

class StreamCopyTest {

    /** Records the largest length ever requested from read(b, off, len). */
    private class TrackingInputStream(source: InputStream) : FilterInputStream(source) {
        var maxRequested = 0
            private set
        var readCalls = 0
            private set

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            readCalls++
            maxRequested = maxOf(maxRequested, len)
            return super.read(b, off, len)
        }
    }

    // ~1 MiB plus a remainder so the last chunk is partial.
    private val payload = Random(42).nextBytes(1024 * 1024 + 123)

    @Test
    fun copiesExactBytesAcrossMultipleChunks() = runBlocking {
        val output = ByteArrayOutputStream()

        val copied = copyStream(ByteArrayInputStream(payload), output)

        assertEquals(payload.size.toLong(), copied)
        assertArrayEquals(payload, output.toByteArray())
    }

    @Test
    fun neverRequestsMoreThanBufferSizePerRead() = runBlocking {
        val tracking = TrackingInputStream(ByteArrayInputStream(payload))

        copyStream(tracking, ByteArrayOutputStream())

        assertTrue("max read ${tracking.maxRequested}", tracking.maxRequested <= COPY_BUFFER_SIZE)
        assertTrue("expected several reads, got ${tracking.readCalls}", tracking.readCalls > 1)
    }

    @Test
    fun emptySourceCopiesNothing() = runBlocking {
        val output = ByteArrayOutputStream()

        val copied = copyStream(ByteArrayInputStream(ByteArray(0)), output)

        assertEquals(0L, copied)
        assertEquals(0, output.size())
    }

    @Test
    fun progressEndsAtTotalSize() = runBlocking {
        var lastProgress = -1L

        copyStream(ByteArrayInputStream(payload), ByteArrayOutputStream()) { lastProgress = it }

        assertEquals(payload.size.toLong(), lastProgress)
    }

    @Test
    fun cancellationStopsCopyingAndIsPropagated() = runBlocking {
        var reads = 0
        var returnedNormally = false
        lateinit var job: Job
        job = launch(start = CoroutineStart.LAZY) {
            // Endless source that requests cancellation on the first read.
            val endless = object : InputStream() {
                override fun read(): Int = error("single-byte read not expected")
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    reads++
                    job.cancel()
                    return len
                }
            }
            copyStream(endless, ByteArrayOutputStream())
            returnedNormally = true
        }

        job.join()

        assertTrue(job.isCancelled)
        assertFalse(returnedNormally)
        assertEquals(1, reads)
    }
}
