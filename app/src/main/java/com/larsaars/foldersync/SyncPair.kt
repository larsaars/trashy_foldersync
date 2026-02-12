package com.larsaars.foldersync

import android.net.Uri

// Data class for a sync pair
data class SyncPair(
    val id: Int,
    val sourceFolderUri: Uri? = null,
    val sourceFolderName: String? = null,
    val destFolderUri: Uri? = null,
    val destFolderName: String? = null
)
