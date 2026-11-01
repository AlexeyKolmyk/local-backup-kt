package com.example.localbackup.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.localbackup.R
import com.example.localbackup.copy.BackupStorage
import com.example.localbackup.copy.FileCopier
import com.example.localbackup.copy.querySourceDocument
import com.example.localbackup.ui.theme.LocalBackupTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
fun BackupScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var isCopying by remember { mutableStateOf(false) }
    val results = remember { mutableStateListOf<String>() }

    val pickFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty() || isCopying) return@rememberLauncherForActivityResult
        isCopying = true
        results.clear()
        scope.launch {
            try {
                temporaryCopyAll(context, uris) { line -> results += line }
            } finally {
                isCopying = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium
        )
        Button(
            onClick = { pickFiles.launch(arrayOf("*/*")) },
            enabled = !isCopying
        ) {
            Text(stringResource(R.string.select_files))
        }
        if (isCopying) {
            LinearProgressIndicator()
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results) { line -> Text(line) }
        }
    }
}

/**
 * TEMPORARY Step 02 wiring: sequential loop over picked files so the copier can be tried on a
 * device. Step 03 moves this sequence into CreateBackupUseCase and out of the UI.
 */
private suspend fun temporaryCopyAll(
    context: Context,
    uris: List<Uri>,
    report: (String) -> Unit
) {
    val storage = BackupStorage(context.filesDir)
    val copier = FileCopier(context.contentResolver)
    val batchDir = try {
        withContext(Dispatchers.IO) { storage.prepareBatchDir(UUID.randomUUID().toString()) }
    } catch (e: Exception) {
        report("Cannot start backup: ${e.message}")
        return
    }
    for (uri in uris) {
        val document = withContext(Dispatchers.IO) { context.contentResolver.querySourceDocument(uri) }
        val destination = storage.destinationFile(batchDir, UUID.randomUUID().toString(), document.displayName)
        try {
            val copied = copier.copy(uri, destination)
            val expected = document.size?.let { " of $it" } ?: ""
            report("OK  ${document.displayName}: $copied$expected bytes")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            report("FAILED  ${document.displayName}: ${e.message ?: e::class.java.simpleName}")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BackupScreenPreview() {
    LocalBackupTheme {
        BackupScreen()
    }
}
