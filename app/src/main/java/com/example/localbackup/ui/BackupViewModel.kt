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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

data class BackupUiState(
    val batch: BackupBatch? = null,
    val isRunning: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Owns the single active backup job and blocks concurrent starts.
 * The per-file loop lives in [CreateBackupUseCase], not here.
 */
class BackupViewModel(
    private val createBackup: CreateBackupUseCase,
    batchStore: BatchStore
) : ViewModel() {

    private var activeJob: Job? = null
    private val isRunning = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<BackupUiState> =
        combine(batchStore.latestBatch, isRunning, errorMessage) { batch, running, error ->
            BackupUiState(batch = batch, isRunning = running, errorMessage = error)
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
