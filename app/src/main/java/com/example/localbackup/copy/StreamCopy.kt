package com.example.localbackup.copy

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.InputStream
import java.io.OutputStream

/** Size of the single reusable buffer used for one active copy. Pragmatic, not benchmarked. */
const val COPY_BUFFER_SIZE = 256 * 1024

/**
 * Copies [input] to [output] with one reusable [COPY_BUFFER_SIZE] buffer, checking for
 * coroutine cancellation before every read. Never reads the whole source into memory.
 *
 * Framework-free so it can be unit tested with plain JVM streams. Does not close the streams.
 *
 * @param onProgress optional callback with the total number of bytes copied so far.
 * @return total number of bytes copied.
 */
suspend fun copyStream(
    input: InputStream,
    output: OutputStream,
    onProgress: ((copiedBytes: Long) -> Unit)? = null
): Long {
    val buffer = ByteArray(COPY_BUFFER_SIZE)
    var total = 0L
    while (true) {
        currentCoroutineContext().ensureActive()
        val read = input.read(buffer, 0, buffer.size)
        if (read < 0) break
        if (read == 0) continue
        output.write(buffer, 0, read)
        total += read
        onProgress?.invoke(total)
    }
    output.flush()
    return total
}
