package com.example.localbackup.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.localbackup.R
import com.example.localbackup.model.BackupBatch
import com.example.localbackup.model.BackupBatchStatus
import com.example.localbackup.model.BackupFile
import com.example.localbackup.model.BackupStatus
import com.example.localbackup.ui.theme.LocalBackupTheme

@Composable
fun BackupScreen(
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel = viewModel(factory = BackupViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pickFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> viewModel.onFilesSelected(uris) }

    BackupContent(
        state = state,
        onSelectFiles = { pickFiles.launch(arrayOf("*/*")) },
        modifier = modifier
    )
}

/** Stateless rendering only; no file I/O. */
@Composable
fun BackupContent(
    state: BackupUiState,
    onSelectFiles: () -> Unit,
    modifier: Modifier = Modifier
) {
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
        Button(onClick = onSelectFiles, enabled = state.canStartBackup) {
            Text(stringResource(R.string.select_files))
        }
        if (state.isRunning) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        when (val stored = state.stored) {
            StoredBatchState.Loading -> Text("Loading saved backup status…")
            StoredBatchState.Empty -> Text("No backups yet. Select files to create one.")
            is StoredBatchState.Error -> Text(stored.message, color = MaterialTheme.colorScheme.error)
            is StoredBatchState.Loaded -> {
                Text("Latest batch: ${stored.batch.status}", style = MaterialTheme.typography.titleMedium)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(stored.batch.files, key = { it.id }) { file -> FileRow(file) }
                }
            }
        }
    }
}

@Composable
private fun FileRow(file: BackupFile) {
    Column {
        Text(file.name, style = MaterialTheme.typography.bodyLarge)
        Text(file.status.name, style = MaterialTheme.typography.labelMedium)
        file.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BackupContentPreview() {
    fun file(id: String, status: BackupStatus, error: String? = null) =
        BackupFile(id, "$id.pdf", "content://x/$id", "file:///x/$id", 1024, status, error)
    LocalBackupTheme {
        BackupContent(
            state = BackupUiState(
                stored = StoredBatchState.Loaded(BackupBatch(
                    id = "b", createdAt = 0, status = BackupBatchStatus.IN_PROGRESS,
                    files = listOf(
                        file("a", BackupStatus.SUCCESS),
                        file("b", BackupStatus.FAILED, "File not found or not readable"),
                        file("c", BackupStatus.COPYING)
                    )
                )),
                isRunning = true
            ),
            onSelectFiles = {}
        )
    }
}
