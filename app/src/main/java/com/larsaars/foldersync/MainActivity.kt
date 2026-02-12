package com.larsaars.foldersync

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.larsaars.foldersync.ui.theme.TrashyFolderSyncTheme
class MainActivity : ComponentActivity() {

    private val viewModel: FolderSyncViewModel by viewModels()
    private var currentSelection: Pair<Int, Boolean>? = null

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )

            val folderName = DocumentFile.fromTreeUri(this, it)?.name ?: "Unknown"

            currentSelection?.let { (pairId, isSource) ->
                if (isSource) {
                    viewModel.updateSourceFolder(pairId, it, folderName)
                } else {
                    viewModel.updateDestFolder(pairId, it, folderName)
                }
            }
            currentSelection = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrashyFolderSyncTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    FolderSyncScreen(
                        syncPairs = viewModel.syncPairs,
                        syncStatus = viewModel.syncStatus,
                        isSyncing = viewModel.isSyncing,
                        onAddPair = { viewModel.addNewPair() },
                        onSelectSource = { pairId ->
                            currentSelection = Pair(pairId, true)
                            folderPickerLauncher.launch(null)
                        },
                        onSelectDest = { pairId ->
                            currentSelection = Pair(pairId, false)
                            folderPickerLauncher.launch(null)
                        },
                        onRemovePair = { pairId -> viewModel.removePair(pairId) },
                        onSyncPair = { pairId -> viewModel.syncPair(pairId) },
                        onSyncAll = { viewModel.syncAll() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}