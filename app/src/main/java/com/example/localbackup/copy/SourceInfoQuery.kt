package com.example.localbackup.copy

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.example.localbackup.backup.SourceInfo

/**
 * Reads display name and size via [OpenableColumns]. Never throws for missing metadata:
 * falls back to the last URI segment (or a generic name) and a null size.
 * Call off the main thread.
 */
fun ContentResolver.querySourceInfo(uri: Uri): SourceInfo {
    var name: String? = null
    var size: Long? = null
    runCatching {
        query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
    }
    return SourceInfo(
        displayName = name?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment ?: "file",
        size = size
    )
}
