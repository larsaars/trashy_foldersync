package com.larsaars.foldersync

data class SyncResult(
    val success: Boolean,
    val filesScanned: Int = 0,
    val filesCopied: Int = 0,
    val filesUpdated: Int = 0,
    val errors: List<String> = emptyList()
)
