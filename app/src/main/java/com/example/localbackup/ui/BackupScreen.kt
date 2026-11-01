package com.example.localbackup.ui

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.localbackup.R
import com.example.localbackup.backup.FileProgress
import com.example.localbackup.copy.StoredBackup
import com.example.localbackup.model.BackupBatch
import com.example.localbackup.model.BackupBatchStatus
import com.example.localbackup.model.BackupFile
import com.example.localbackup.model.BackupStatus
import com.example.localbackup.ui.theme.LocalBackupTheme
import com.example.localbackup.ui.theme.SuccessDark
import com.example.localbackup.ui.theme.SuccessLight
import com.example.localbackup.ui.theme.WarningDark
import com.example.localbackup.ui.theme.WarningLight
import java.text.DateFormat
import java.util.Date

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
        onDeleteBackup = viewModel::onDeleteBackup,
        onCancelBackup = viewModel::onCancelBackup,
        modifier = modifier
    )
}

/** Stateless rendering only; no file I/O, no side effects on recomposition. */
@Composable
fun BackupContent(
    state: BackupUiState,
    onSelectFiles: () -> Unit,
    onDeleteBackup: (StoredBackup) -> Unit,
    onCancelBackup: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Backup awaiting confirmation; the dialog is the only way to trigger deletion.
    var pendingDelete by remember { mutableStateOf<StoredBackup?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Header() }
        item {
            Button(
                onClick = onSelectFiles,
                enabled = state.canStartBackup,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.select_files))
            }
        }
        state.errorMessage?.let { message -> item { ErrorCard(message) } }

        when (val stored = state.stored) {
            StoredBatchState.Loading -> item { LoadingRow() }
            StoredBatchState.Empty -> item { EmptyState() }
            is StoredBatchState.Error -> item { ErrorCard(stored.message) }
            is StoredBatchState.Loaded -> {
                item {
                    SummaryCard(
                        batch = stored.batch,
                        progress = state.progress,
                        showCancel = state.isRunning,
                        isCancelling = state.isCancelling,
                        onCancel = onCancelBackup
                    )
                }
                item { FileList(stored.batch.files) }
            }
        }

        item {
            StoredBackupsSection(
                state = state.storedBackups,
                deleteEnabled = state.canDeleteBackups,
                onDeleteRequest = { pendingDelete = it }
            )
        }
    }

    pendingDelete?.let { backup ->
        DeleteBackupDialog(
            backup = backup,
            onConfirm = {
                pendingDelete = null
                onDeleteBackup(backup)
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun StoredBackupsSection(
    state: StoredBackupsState,
    deleteEnabled: Boolean,
    onDeleteRequest: (StoredBackup) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.stored_title), style = MaterialTheme.typography.titleMedium)
        when (state) {
            StoredBackupsState.Loading -> Text(
                stringResource(R.string.stored_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            is StoredBackupsState.Error -> ErrorCard(state.message)
            is StoredBackupsState.Loaded -> if (state.backups.isEmpty()) {
                Text(
                    stringResource(R.string.stored_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column {
                    state.backups.forEachIndexed { index, backup ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        StoredBackupRow(backup, deleteEnabled, onDeleteRequest)
                    }
                }
            }
        }
    }
}

@Composable
private fun StoredBackupRow(
    backup: StoredBackup,
    deleteEnabled: Boolean,
    onDeleteRequest: (StoredBackup) -> Unit
) {
    val context = LocalContext.current
    val size = Formatter.formatShortFileSize(context, backup.sizeBytes)
    val date = remember(backup.lastModified) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(backup.lastModified))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                backup.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                stringResource(R.string.stored_item_details, size, date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = { onDeleteRequest(backup) }, enabled = deleteEnabled) {
            Icon(
                painter = painterResource(R.drawable.ic_delete),
                contentDescription = stringResource(R.string.delete_backup_description, backup.displayName)
            )
        }
    }
}

@Composable
private fun DeleteBackupDialog(
    backup: StoredBackup,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val size = Formatter.formatShortFileSize(LocalContext.current, backup.sizeBytes)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_dialog_title)) },
        text = { Text(stringResource(R.string.delete_dialog_text, backup.displayName, size)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.delete_dismiss)) }
        }
    )
}

@Composable
private fun Header() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_folder),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
        Text(stringResource(R.string.screen_title), style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun LoadingRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(stringResource(R.string.loading_saved), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_folder),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp)
        )
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ErrorCard(message: String) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_error),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SummaryCard(
    batch: BackupBatch,
    progress: FileProgress?,
    showCancel: Boolean = false,
    isCancelling: Boolean = false,
    onCancel: () -> Unit = {}
) {
    val total = batch.files.size
    val processed = batch.files.count { it.status.isTerminal() }
    val copying = batch.files.firstOrNull { it.status == BackupStatus.COPYING }
    val startedAt = remember(batch.createdAt) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(batch.createdAt))
    }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(batch.status.titleRes()), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.files_processed, processed, total),
                style = MaterialTheme.typography.bodyMedium
            )

            if (batch.status == BackupBatchStatus.IN_PROGRESS && copying != null) {
                Text(
                    stringResource(R.string.copying_file, copying.name),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val fraction = fileFraction(copying, progress)
                if (fraction != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.weight(1f))
                        Text(
                            stringResource(R.string.percent, (fraction * 100).toInt()),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                } else {
                    // Unknown size (or no progress reported yet): indeterminate.
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            // Cancel sits right under the progress of the running batch.
            if (showCancel && batch.status == BackupBatchStatus.IN_PROGRESS) {
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !isCancelling,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(
                            if (isCancelling) R.string.cancelling_backup else R.string.cancel_backup
                        )
                    )
                }
            }

            val counts = countsText(batch.files)
            if (counts.isNotEmpty()) {
                Text(counts, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                stringResource(R.string.started_at, startedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FileList(files: List<BackupFile>) {
    Column {
        files.forEachIndexed { index, file ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            FileRow(file)
        }
    }
}

@Composable
private fun FileRow(file: BackupFile) {
    val context = LocalContext.current
    val size = file.expectedSize?.let { Formatter.formatShortFileSize(context, it) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatusIcon(file.status)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                file.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(stringResource(file.status.labelRes()), size).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = statusColor(file.status)
            )
            file.errorMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusIcon(status: BackupStatus) {
    Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        if (status == BackupStatus.COPYING) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            // Decorative: the status text label next to it is what screen readers read.
            Icon(
                painter = painterResource(status.iconRes()),
                contentDescription = null,
                tint = statusColor(status)
            )
        }
    }
}

@Composable
private fun countsText(files: List<BackupFile>): String {
    val parts = listOf(
        BackupStatus.SUCCESS to R.string.count_succeeded,
        BackupStatus.FAILED to R.string.count_failed,
        BackupStatus.INTERRUPTED to R.string.count_interrupted,
        BackupStatus.CANCELLED to R.string.count_cancelled
    ).mapNotNull { (status, res) ->
        val count = files.count { it.status == status }
        if (count > 0) stringResource(res, count) else null
    }
    return parts.joinToString(" · ")
}

/** Fraction for the current file; capped below 1 so an unfinished file never shows 100%. */
private fun fileFraction(file: BackupFile, progress: FileProgress?): Float? {
    val expected = file.expectedSize ?: return null
    if (expected <= 0 || progress == null || progress.fileId != file.id) return null
    return (progress.copiedBytes.toFloat() / expected).coerceIn(0f, 0.99f)
}

private fun BackupStatus.isTerminal() = this != BackupStatus.PENDING && this != BackupStatus.COPYING

@StringRes
private fun BackupBatchStatus.titleRes(): Int = when (this) {
    BackupBatchStatus.IN_PROGRESS -> R.string.batch_in_progress
    BackupBatchStatus.COMPLETED -> R.string.batch_completed
    BackupBatchStatus.INTERRUPTED -> R.string.batch_interrupted
    BackupBatchStatus.CANCELLED -> R.string.batch_cancelled
}

@StringRes
private fun BackupStatus.labelRes(): Int = when (this) {
    BackupStatus.PENDING -> R.string.status_pending
    BackupStatus.COPYING -> R.string.status_copying
    BackupStatus.SUCCESS -> R.string.status_success
    BackupStatus.FAILED -> R.string.status_failed
    BackupStatus.INTERRUPTED -> R.string.status_interrupted
    BackupStatus.CANCELLED -> R.string.status_cancelled
}

@DrawableRes
private fun BackupStatus.iconRes(): Int = when (this) {
    BackupStatus.PENDING, BackupStatus.COPYING -> R.drawable.ic_schedule
    BackupStatus.SUCCESS -> R.drawable.ic_check_circle
    BackupStatus.FAILED -> R.drawable.ic_error
    BackupStatus.INTERRUPTED -> R.drawable.ic_warning
    BackupStatus.CANCELLED -> R.drawable.ic_cancel
}

@Composable
private fun statusColor(status: BackupStatus): Color {
    val dark = isSystemInDarkTheme()
    return when (status) {
        BackupStatus.SUCCESS -> if (dark) SuccessDark else SuccessLight
        BackupStatus.FAILED -> MaterialTheme.colorScheme.error
        BackupStatus.INTERRUPTED -> if (dark) WarningDark else WarningLight
        BackupStatus.COPYING -> MaterialTheme.colorScheme.primary
        BackupStatus.PENDING, BackupStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

// ---- Previews ---------------------------------------------------------------------------------

private fun previewFile(id: String, name: String, status: BackupStatus, size: Long?, error: String? = null) =
    BackupFile(id, name, "content://p/$id", "file:///b/$id", size, status, error)

@Preview(name = "Empty", showBackground = true)
@Composable
private fun EmptyPreview() {
    LocalBackupTheme { BackupContent(BackupUiState(stored = StoredBatchState.Empty), onSelectFiles = {}, onDeleteBackup = {}, onCancelBackup = {}) }
}

@Preview(name = "Copying", showBackground = true)
@Composable
private fun CopyingPreview() {
    LocalBackupTheme {
        BackupContent(
            BackupUiState(
                stored = StoredBatchState.Loaded(
                    BackupBatch(
                        "b", 1_700_000_000_000, BackupBatchStatus.IN_PROGRESS, listOf(
                            previewFile("a", "docs_ukr.pdf", BackupStatus.SUCCESS, 24_383_904),
                            previewFile("b", "large_test.bin", BackupStatus.COPYING, 471_859_200),
                            previewFile("c", "share.mp4", BackupStatus.PENDING, 226_650_109)
                        )
                    )
                ),
                isRunning = true,
                progress = FileProgress("b", 190_000_000)
            ),
            onSelectFiles = {},
            onDeleteBackup = {},
            onCancelBackup = {}
        )
    }
}

@Preview(name = "Results", showBackground = true)
@Composable
private fun ResultsPreview() {
    LocalBackupTheme {
        BackupContent(
            BackupUiState(
                stored = StoredBatchState.Loaded(
                    BackupBatch(
                        "b", 1_700_000_000_000, BackupBatchStatus.INTERRUPTED, listOf(
                            previewFile("a", "docs_ukr.pdf", BackupStatus.SUCCESS, 24_383_904),
                            previewFile("b", "photo_with_a_really_long_file_name_2022-02-03.jpg", BackupStatus.FAILED, null, "File not found or not readable"),
                            previewFile("c", "share.mp4", BackupStatus.INTERRUPTED, 226_650_109, "Interrupted before completion")
                        )
                    )
                ),
                storedBackups = StoredBackupsState.Loaded(
                    listOf(
                        StoredBackup("b1/x_docs_ukr.pdf", "b1", "docs_ukr.pdf", 24_383_904, 1_700_000_000_000),
                        StoredBackup("b0/y_share.mp4", "b0", "share.mp4", 226_650_109, 1_699_000_000_000)
                    )
                )
            ),
            onSelectFiles = {},
            onDeleteBackup = {},
            onCancelBackup = {}
        )
    }
}

@Preview(name = "Error", showBackground = true)
@Composable
private fun ErrorPreview() {
    LocalBackupTheme {
        BackupContent(
            BackupUiState(stored = StoredBatchState.Error("Saved backup status cannot be read (CorruptionException)")),
            onSelectFiles = {},
            onDeleteBackup = {},
            onCancelBackup = {}
        )
    }
}
