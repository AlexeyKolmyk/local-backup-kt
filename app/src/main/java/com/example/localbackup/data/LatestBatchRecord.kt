package com.example.localbackup.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.example.localbackup.model.BackupBatch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/** Stored DataStore value. `latestBatch == null` is the explicit "no backup yet" state. */
@Serializable
data class LatestBatchRecord(val latestBatch: BackupBatch? = null)

/**
 * JSON serializer for [LatestBatchRecord].
 *
 * A missing file yields [defaultValue] (no batch). Malformed content is reported as
 * [CorruptionException] instead of being silently turned into "no batch".
 */
object LatestBatchSerializer : Serializer<LatestBatchRecord> {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val defaultValue: LatestBatchRecord = LatestBatchRecord()

    override suspend fun readFrom(input: InputStream): LatestBatchRecord =
        try {
            json.decodeFromString(LatestBatchRecord.serializer(), input.readBytes().decodeToString())
        } catch (e: IllegalArgumentException) { // includes SerializationException
            throw CorruptionException("Saved backup metadata is unreadable", e)
        }

    override suspend fun writeTo(t: LatestBatchRecord, output: OutputStream) {
        output.write(json.encodeToString(LatestBatchRecord.serializer(), t).encodeToByteArray())
    }
}
