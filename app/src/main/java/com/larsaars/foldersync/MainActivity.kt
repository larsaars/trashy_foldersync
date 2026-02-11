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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
    private var currentSelection: Pair<Int, Boolean>? = null // (pairId, isSource)

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            // Take persistent permission
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )

            // Get folder name
            val folderName = DocumentFile.fromTreeUri(this, it)?.name ?: "Unknown"

            // Update the appropriate field
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
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun FolderSyncScreen(
    syncPairs: List<SyncPair>,
    onAddPair: () -> Unit,
    onSelectSource: (Int) -> Unit,
    onSelectDest: (Int) -> Unit,
    onRemovePair: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = "Folder Sync Pairs",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        // List of sync pairs
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(syncPairs) { pair ->
                SyncPairItem(
                    pair = pair,
                    onSelectSource = { onSelectSource(pair.id) },
                    onSelectDest = { onSelectDest(pair.id) },
                    onRemove = { onRemovePair(pair.id) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Add new pair button
        Button(
            onClick = onAddPair,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add Sync Pair")
        }
    }
}

@Composable
fun SyncPairItem(
    pair: SyncPair,
    onSelectSource: () -> Unit,
    onSelectDest: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Pair #${pair.id}", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Source folder
            OutlinedButton(onClick = onSelectSource, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = pair.sourceFolderName ?: "Select Source Folder",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Destination folder
            OutlinedButton(onClick = onSelectDest, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = pair.destFolderName ?: "Select Destination Folder",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}