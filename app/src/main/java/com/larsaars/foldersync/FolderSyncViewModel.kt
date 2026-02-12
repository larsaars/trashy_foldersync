package com.larsaars.foldersync

// FolderSyncViewModel.kt
import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.core.net.toUri


class FolderSyncViewModel(application: Application) : AndroidViewModel(application) {


    private val repository = SyncPairRepository(application)
    private val syncEngine = FolderSyncEngine(application)

    private val _syncPairs = mutableStateListOf<SyncPair>()
    val syncPairs: List<SyncPair> = _syncPairs

    private val _syncStatus = mutableStateOf<String>("")
    val syncStatus: String by _syncStatus

    private val _isSyncing = mutableStateOf(false)
    val isSyncing: Boolean by _isSyncing

    init {
        // Load saved pairs on startup
        viewModelScope.launch {
            val savedPairs = repository.syncPairs.first()
            _syncPairs.clear()
            _syncPairs.addAll(savedPairs.map { it.toSyncPair() })
        }
    }

    fun addNewPair() {
        val newId = (_syncPairs.maxOfOrNull { it.id } ?: 0) + 1
        _syncPairs.add(SyncPair(id = newId))
        savePairs()
    }

    fun updateSourceFolder(pairId: Int, uri: Uri, name: String) {
        val index = _syncPairs.indexOfFirst { it.id == pairId }
        if (index != -1) {
            _syncPairs[index] = _syncPairs[index].copy(
                sourceFolderUri = uri,
                sourceFolderName = name
            )
            savePairs()
        }
    }

    fun updateDestFolder(pairId: Int, uri: Uri, name: String) {
        val index = _syncPairs.indexOfFirst { it.id == pairId }
        if (index != -1) {
            _syncPairs[index] = _syncPairs[index].copy(
                destFolderUri = uri,
                destFolderName = name
            )
            savePairs()
        }
    }

    fun removePair(pairId: Int) {
        _syncPairs.removeAll { it.id == pairId }
        savePairs()
    }

    private fun savePairs() {
        viewModelScope.launch {
            val dataList = _syncPairs.map { it.toSyncPairData() }
            repository.saveSyncPairs(dataList)
        }
    }
    fun syncPair(pairId: Int) {
        val pair = _syncPairs.find { it.id == pairId } ?: return

        if (pair.sourceFolderUri == null || pair.destFolderUri == null) {
            _syncStatus.value = "Both folders must be selected"
            return
        }

        _isSyncing.value = true
        _syncStatus.value = "Syncing..."

        viewModelScope.launch {
            try {
                val result = syncEngine.syncFolders(
                    sourceUri = pair.sourceFolderUri,
                    destUri = pair.destFolderUri,
                    twoWaySync = true
                )

                if (result.success) {
                    _syncStatus.value = "✓ Synced: ${result.filesCopied} copied, " +
                            "${result.filesUpdated} updated (${result.filesScanned} scanned)"
                } else {
                    _syncStatus.value = "✗ Sync failed: ${result.errors.firstOrNull()}"
                }

            } catch (e: Exception) {
                _syncStatus.value = "✗ Error: ${e.message}"
                Log.e("FolderSyncViewModel", "Sync error", e)
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun syncAll() {
        viewModelScope.launch {
            _isSyncing.value = true
            var totalCopied = 0
            var totalUpdated = 0
            var totalScanned = 0

            for (pair in _syncPairs) {
                if (pair.sourceFolderUri != null && pair.destFolderUri != null) {
                    val result = syncEngine.syncFolders(
                        sourceUri = pair.sourceFolderUri,
                        destUri = pair.destFolderUri,
                        twoWaySync = true
                    )
                    totalCopied += result.filesCopied
                    totalUpdated += result.filesUpdated
                    totalScanned += result.filesScanned
                }
            }

            _syncStatus.value = "✓ All synced: $totalCopied copied, $totalUpdated updated ($totalScanned scanned)"
            _isSyncing.value = false
        }
    }
}

// Extension functions to convert between UI and Data models
private fun SyncPairData.toSyncPair() = SyncPair(
    id = id,
    sourceFolderUri = sourceFolderUri?.toUri(),
    sourceFolderName = sourceFolderName,
    destFolderUri = destFolderUri?.toUri(),
    destFolderName = destFolderName
)

private fun SyncPair.toSyncPairData() = SyncPairData(
    id = id,
    sourceFolderUri = sourceFolderUri?.toString(),
    sourceFolderName = sourceFolderName,
    destFolderUri = destFolderUri?.toString(),
    destFolderName = destFolderName
)