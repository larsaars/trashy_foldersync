package com.larsaars.foldersync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun FolderSyncScreen(
    syncPairs: List<SyncPair>,
    syncStatus: String,
    isSyncing: Boolean,
    onAddPair: () -> Unit,
    onSelectSource: (Int) -> Unit,
    onSelectDest: (Int) -> Unit,
    onRemovePair: (Int) -> Unit,
    onSyncPair: (Int) -> Unit,
    onSyncAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = "Folder Sync Pairs",
            style = MaterialTheme.typography.headlineMedium
        )

        // Status bar
        if (syncStatus.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = syncStatus,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Sync all button
        Button(
            onClick = onSyncAll,
            enabled = !isSyncing && syncPairs.any {
                it.sourceFolderUri != null && it.destFolderUri != null
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(if (isSyncing) "Syncing..." else "Sync All")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // List of sync pairs
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(syncPairs) { pair ->
                SyncPairItem(
                    pair = pair,
                    isSyncing = isSyncing,
                    onSelectSource = { onSelectSource(pair.id) },
                    onSelectDest = { onSelectDest(pair.id) },
                    onRemove = { onRemovePair(pair.id) },
                    onSync = { onSyncPair(pair.id) }
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
    isSyncing: Boolean,
    onSelectSource: () -> Unit,
    onSelectDest: () -> Unit,
    onRemove: () -> Unit,
    onSync: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Pair #${pair.id}", style = MaterialTheme.typography.titleMedium)
                Row {
                    // Sync button
                    IconButton(
                        onClick = onSync,
                        enabled = !isSyncing &&
                                pair.sourceFolderUri != null &&
                                pair.destFolderUri != null
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync")
                    }
                    // Delete button
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Source folder
            OutlinedButton(onClick = onSelectSource, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Source: ${pair.sourceFolderName ?: "Select folder"}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Icon(
                imageVector = Icons.Default.ArrowDownward,
                contentDescription = "Sync direction",
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Destination folder
            OutlinedButton(onClick = onSelectDest, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Dest: ${pair.destFolderName ?: "Select folder"}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}