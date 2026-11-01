package com.example.localbackup.copy

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Copies one source document to one prepared destination file.
 *
 * Bytes go to an adjacent `<destination>.partial` first; only after both streams are closed is
 * it renamed to [destination]. On any failure or cancellation the partial file is removed
 * (best effort) and the exception is rethrown. Knows nothing about batches or persistence.
 */
class FileCopier(private val contentResolver: ContentResolver) {

    suspend fun copy(
        sourceUri: Uri,
        destination: File,
        onProgress: ((copiedBytes: Long) -> Unit)? = null
    ): Long = withContext(Dispatchers.IO) {
        val partial = File(destination.parentFile, destination.name + PARTIAL_SUFFIX)
        try {
            val input = contentResolver.openInputStream(sourceUri)
                ?: throw IOException("Cannot open the selected file")
            val copied = input.use { source ->
                FileOutputStream(partial).use { target ->
                    val bytes = copyStream(source, target, onProgress)
                    target.fd.sync()
                    bytes
                }
            }
            if (!partial.renameTo(destination)) {
                throw IOException("Cannot finalize the backup file")
            }
            copied
        } catch (t: Throwable) {
            // Covers ordinary errors and CancellationException; the exception is rethrown as is.
            partial.delete()
            throw t
        }
    }

    companion object {
        const val PARTIAL_SUFFIX = ".partial"
    }
}
