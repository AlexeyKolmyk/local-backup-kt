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
import com.example.localbackup.model.BackupBatch
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
    val stored: StoredBatchState = StoredBatchState.Loading,
    val isRunning: Boolean = false,
    val errorMessage: String? = null
) {
    /** New backups only once the stored state is known and readable, and nothing is running. */
    val canStartBackup: Boolean
        get() = !isRunning && (stored is StoredBatchState.Empty || stored is StoredBatchState.Loaded)
}

/**
 * Owns the single active backup job and blocks concurrent starts.
 * The per-file loop lives in [CreateBackupUseCase]; status comes from the persisted [BatchStore].
 */
class BackupViewModel(
    private val createBackup: CreateBackupUseCase,
    batchStore: BatchStore
) : ViewModel() {

    private var activeJob: Job? = null
    private val isRunning = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)

    private val stored = batchStore.latestBatch
        .map { batch -> if (batch == null) StoredBatchState.Empty else StoredBatchState.Loaded(batch) }
        .onStart<StoredBatchState> { emit(StoredBatchState.Loading) }
        .catch { e -> emit(StoredBatchState.Error("Saved backup status cannot be read (${e::class.java.simpleName})")) }

    val uiState: StateFlow<BackupUiState> =
        combine(stored, isRunning, errorMessage) { storedState, running, error ->
            BackupUiState(stored = storedState, isRunning = running, errorMessage = error)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUiState())

    fun onFilesSelected(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (activeJob?.isActive == true) return // duplicate-start guard

        errorMessage.value = null
        isRunning.value = true
        activeJob = viewModelScope.launch {
            try {
                createBackup(uris.map(Uri::toString))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage.value = "Backup could not be completed (${e::class.java.simpleName})"
            } finally {
                isRunning.value = false
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as LocalBackupApplication).container
                BackupViewModel(container.createBackup, container.batchStore)
            }
        }
    }
}
