package com.example.localbackup.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.localbackup.LocalBackupApplication
import com.example.localbackup.backup.BatchStore
import com.example.localbackup.backup.CreateBackupUseCase
import com.example.localbackup.backup.FileProgress
import com.example.localbackup.model.BackupBatch
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** What is known about the persisted latest batch. */
sealed interface StoredBatchState {
    data object Loading : StoredBatchState
    data object Empty : StoredBatchState
    data class Loaded(val batch: BackupBatch) : StoredBatchState
    data class Error(val message: String) : StoredBatchState
}

data class BackupUiState(
    /** Loading until startup reconciliation has finished, so a stale IN_PROGRESS is never shown. */
    val stored: StoredBatchState = StoredBatchState.Loading,
    val isRunning: Boolean = false,
    val errorMessage: String? = null,
    /** Transient, throttled progress of the file being copied; null when idle. */
    val progress: FileProgress? = null
) {
    /** New backups only once the stored state is known and readable, and nothing is running. */
    val canStartBackup: Boolean
        get() = !isRunning && (stored is StoredBatchState.Empty || stored is StoredBatchState.Loaded)
}

/**
 * Owns the single active backup job and blocks concurrent starts.
 * The per-file loop lives in [CreateBackupUseCase]; status comes from the persisted [BatchStore].
 * New backups are enabled only after [startupReconciliation] has finished.
 */
class BackupViewModel(
    private val createBackup: CreateBackupUseCase,
    batchStore: BatchStore,
    startupReconciliation: Deferred<Result<Unit>>
) : ViewModel() {

    private var activeJob: Job? = null
    private val isReady = MutableStateFlow(false)
    private val isRunning = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val progress = MutableStateFlow<FileProgress?>(null)

    @Volatile
    private var lastProgressNanos = 0L

    private val stored = batchStore.latestBatch
        .map { batch -> if (batch == null) StoredBatchState.Empty else StoredBatchState.Loaded(batch) }
        .onStart<StoredBatchState> { emit(StoredBatchState.Loading) }
        .catch { e -> emit(StoredBatchState.Error("Saved backup status cannot be read (${e::class.java.simpleName})")) }

    val uiState: StateFlow<BackupUiState> =
        combine(stored, isReady, isRunning, errorMessage, progress) { storedState, ready, running, error, fileProgress ->
            BackupUiState(
                stored = if (ready) storedState else StoredBatchState.Loading,
                isRunning = running,
                errorMessage = error,
                progress = fileProgress
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUiState())

    init {
        viewModelScope.launch {
            startupReconciliation.await().onFailure { e ->
                errorMessage.value = "Previous backup could not be checked (${e::class.java.simpleName})"
            }
            isReady.value = true
        }
    }

    fun onFilesSelected(uris: List<Uri>) {
        if (uris.isEmpty() || !isReady.value) return
        if (activeJob?.isActive == true) return // duplicate-start guard

        errorMessage.value = null
        isRunning.value = true
        activeJob = viewModelScope.launch {
            try {
                createBackup(uris.map(Uri::toString), ::reportProgress)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage.value = "Backup could not be completed (${e::class.java.simpleName})"
            } finally {
                progress.value = null
                isRunning.value = false
            }
        }
    }

    /** The copier reports every 256 KiB chunk; publish at most every [PROGRESS_INTERVAL_NANOS] per file. */
    private fun reportProgress(update: FileProgress) {
        val now = System.nanoTime()
        val current = progress.value
        if (current == null || current.fileId != update.fileId || now - lastProgressNanos >= PROGRESS_INTERVAL_NANOS) {
            lastProgressNanos = now
            progress.value = update
        }
    }

    companion object {
        private const val PROGRESS_INTERVAL_NANOS = 150_000_000L // 150 ms

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as LocalBackupApplication).container
                BackupViewModel(container.createBackup, container.batchStore, container.startupReconciliation)
            }
        }
    }
}
