package com.larsaars.foldersync

import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel

// ViewModel to manage state
class FolderSyncViewModel : ViewModel() {
    private val _syncPairs = mutableStateListOf<SyncPair>()
    val syncPairs: List<SyncPair> = _syncPairs

    fun addNewPair() {
        val newId = (_syncPairs.maxOfOrNull { it.id } ?: 0) + 1
        _syncPairs.add(SyncPair(id = newId))
    }

    fun updateSourceFolder(pairId: Int, uri: Uri, name: String) {
        val index = _syncPairs.indexOfFirst { it.id == pairId }
        if (index != -1) {
            _syncPairs[index] = _syncPairs[index].copy(
                sourceFolderUri = uri,
                sourceFolderName = name
            )
        }
    }

    fun updateDestFolder(pairId: Int, uri: Uri, name: String) {
        val index = _syncPairs.indexOfFirst { it.id == pairId }
        if (index != -1) {
            _syncPairs[index] = _syncPairs[index].copy(
                destFolderUri = uri,
                destFolderName = name
            )
        }
    }

    fun removePair(pairId: Int) {
        _syncPairs.removeAll { it.id == pairId }
    }
}