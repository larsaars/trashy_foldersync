package com.larsaars.foldersync

// FolderSyncEngine.kt - REPLACE the existing file
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException


class FolderSyncEngine(private val context: Context) {

    // Track which files we've already synced in this session
    private data class FileIdentifier(val path: String, val size: Long)
    private val syncedFiles = mutableSetOf<FileIdentifier>()

    suspend fun syncFolders(
        sourceUri: Uri,
        destUri: Uri,
        twoWaySync: Boolean = true
    ): SyncResult = withContext(Dispatchers.IO) {

        syncedFiles.clear() // Reset for each sync operation
        val errors = mutableListOf<String>()
        var totalFilesScanned = 0
        var totalFilesCopied = 0
        var totalFilesUpdated = 0

        try {
            val sourceDir = DocumentFile.fromTreeUri(context, sourceUri)
            val destDir = DocumentFile.fromTreeUri(context, destUri)

            if (sourceDir == null || !sourceDir.exists()) {
                return@withContext SyncResult(
                    success = false,
                    errors = listOf("Source folder not accessible")
                )
            }

            if (destDir == null || !destDir.exists()) {
                return@withContext SyncResult(
                    success = false,
                    errors = listOf("Destination folder not accessible")
                )
            }

            Log.d("SyncEngine", "Starting sync: ${sourceDir.name} -> ${destDir.name}")

            if (twoWaySync) {
                // Build file maps for both directories
                val sourceFiles = buildFileMap(sourceDir, "")
                val destFiles = buildFileMap(destDir, "")

                Log.d("SyncEngine", "Source has ${sourceFiles.size} files, Dest has ${destFiles.size} files")

                totalFilesScanned = maxOf(sourceFiles.size, destFiles.size)

                // Process all unique file paths
                val allPaths = (sourceFiles.keys + destFiles.keys).toSet()

                for (path in allPaths) {
                    val sourceFile = sourceFiles[path]
                    val destFile = destFiles[path]

                    when {
                        sourceFile != null && destFile == null -> {
                            // File only exists in source, copy to dest
                            if (copyFileToPath(sourceFile.file, destDir, sourceFile.relativePath)) {
                                totalFilesCopied++
                                Log.d("SyncEngine", "Copied to dest: $path")
                            }
                        }
                        sourceFile == null && destFile != null -> {
                            // File only exists in dest, copy to source
                            if (copyFileToPath(destFile.file, sourceDir, destFile.relativePath)) {
                                totalFilesCopied++
                                Log.d("SyncEngine", "Copied to source: $path")
                            }
                        }
                        sourceFile != null && destFile != null -> {
                            // File exists in both, sync the newer one
                            val sourceModified = sourceFile.file.lastModified()
                            val destModified = destFile.file.lastModified()

                            // Add tolerance of 2 seconds to account for filesystem timestamp precision
                            val timeDiff = Math.abs(sourceModified - destModified)

                            if (timeDiff > 2000) { // More than 2 seconds difference
                                if (sourceModified > destModified) {
                                    // Source is newer, update dest
                                    if (updateFile(sourceFile.file, destFile.file)) {
                                        totalFilesUpdated++
                                        Log.d("SyncEngine", "Updated dest: $path (src: $sourceModified, dst: $destModified)")
                                    }
                                } else {
                                    // Dest is newer, update source
                                    if (updateFile(destFile.file, sourceFile.file)) {
                                        totalFilesUpdated++
                                        Log.d("SyncEngine", "Updated source: $path (src: $sourceModified, dst: $destModified)")
                                    }
                                }
                            } else {
                                Log.d("SyncEngine", "Files in sync: $path (difference: ${timeDiff}ms)")
                            }
                        }
                    }
                }

            } else {
                // One-way sync (source -> dest only)
                val result = syncDirectories(
                    source = sourceDir,
                    dest = destDir,
                    errors = errors
                )
                totalFilesScanned = result.first
                totalFilesCopied = result.second
                totalFilesUpdated = result.third
            }

            SyncResult(
                success = errors.isEmpty(),
                filesScanned = totalFilesScanned,
                filesCopied = totalFilesCopied,
                filesUpdated = totalFilesUpdated,
                errors = errors
            )

        } catch (e: Exception) {
            Log.e("SyncEngine", "Sync failed", e)
            SyncResult(
                success = false,
                errors = listOf("Sync failed: ${e.message}")
            )
        }
    }

    private data class FileInfo(
        val file: DocumentFile,
        val relativePath: String
    )

    // Build a map of all files with their relative paths
    private fun buildFileMap(
        dir: DocumentFile,
        currentPath: String
    ): Map<String, FileInfo> {
        val fileMap = mutableMapOf<String, FileInfo>()

        try {
            for (file in dir.listFiles()) {
                val fileName = file.name ?: continue
                val filePath = if (currentPath.isEmpty()) fileName else "$currentPath/$fileName"

                if (file.isDirectory) {
                    // Recurse into subdirectory
                    fileMap.putAll(buildFileMap(file, filePath))
                } else {
                    fileMap[filePath] = FileInfo(file, filePath)
                }
            }
        } catch (e: Exception) {
            Log.e("SyncEngine", "Error building file map for $currentPath", e)
        }

        return fileMap
    }

    // Copy a file to a specific path in the destination
    private fun copyFileToPath(
        sourceFile: DocumentFile,
        destRoot: DocumentFile,
        relativePath: String
    ): Boolean {
        try {
            // Split path into directory parts and filename
            val parts = relativePath.split("/")
            val fileName = parts.last()
            val dirParts = parts.dropLast(1)

            // Create directory structure if needed
            var currentDir = destRoot
            for (dirName in dirParts) {
                val subDir = currentDir.findFile(dirName)
                currentDir = if (subDir != null && subDir.isDirectory) {
                    subDir
                } else {
                    currentDir.createDirectory(dirName) ?: return false
                }
            }

            // Create and copy the file
            val newFile = currentDir.createFile(
                sourceFile.type ?: "application/octet-stream",
                fileName
            ) ?: return false

            context.contentResolver.openInputStream(sourceFile.uri)?.use { input ->
                context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                    input.copyTo(output, bufferSize = 8192)
                }
            }

            return true

        } catch (e: Exception) {
            Log.e("SyncEngine", "Failed to copy file: $relativePath", e)
            return false
        }
    }

    // Update an existing file with newer content
    private fun updateFile(
        sourceFile: DocumentFile,
        destFile: DocumentFile
    ): Boolean {
        try {
            // Delete and recreate to ensure clean write
            destFile.delete()

            val parent = findParentDirectory(destFile) ?: return false
            val fileName = sourceFile.name ?: return false

            val newFile = parent.createFile(
                sourceFile.type ?: "application/octet-stream",
                fileName
            ) ?: return false

            context.contentResolver.openInputStream(sourceFile.uri)?.use { input ->
                context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                    input.copyTo(output, bufferSize = 8192)
                }
            }

            return true

        } catch (e: Exception) {
            Log.e("SyncEngine", "Failed to update file", e)
            return false
        }
    }

    // Helper to find parent directory of a DocumentFile
    private fun findParentDirectory(file: DocumentFile): DocumentFile? {
        // This is tricky with SAF - we can't directly get parent
        // We need to parse the URI to reconstruct the parent
        val uri = file.uri
        val path = uri.path ?: return null

        // For tree URIs, we can try to reconstruct parent
        val parentPath = path.substringBeforeLast('/')
        if (parentPath.isEmpty() || parentPath == path) return null

        // This is a limitation - we might need to pass parent reference differently
        // For now, return null and handle error
        return null
    }

    // Fallback: one-way sync implementation (kept for backwards compatibility)
    private fun syncDirectories(
        source: DocumentFile,
        dest: DocumentFile,
        errors: MutableList<String>
    ): Triple<Int, Int, Int> {

        var scanned = 0
        var copied = 0
        var updated = 0

        try {
            val sourceFiles = source.listFiles()

            for (sourceFile in sourceFiles) {
                scanned++

                if (sourceFile.isDirectory) {
                    val destSubDir = dest.findFile(sourceFile.name!!)
                        ?: dest.createDirectory(sourceFile.name!!)

                    if (destSubDir != null) {
                        val subResult = syncDirectories(sourceFile, destSubDir, errors)
                        scanned += subResult.first
                        copied += subResult.second
                        updated += subResult.third
                    }
                } else {
                    val result = syncFile(sourceFile, dest, errors)
                    when (result) {
                        SyncFileResult.COPIED -> copied++
                        SyncFileResult.UPDATED -> updated++
                        SyncFileResult.SKIPPED -> { }
                        SyncFileResult.ERROR -> { }
                    }
                }
            }

        } catch (e: Exception) {
            errors.add("Error scanning ${source.name}: ${e.message}")
        }

        return Triple(scanned, copied, updated)
    }

    private enum class SyncFileResult {
        COPIED, UPDATED, SKIPPED, ERROR
    }

    private fun syncFile(
        sourceFile: DocumentFile,
        destFolder: DocumentFile,
        errors: MutableList<String>
    ): SyncFileResult {

        try {
            val fileName = sourceFile.name ?: return SyncFileResult.ERROR
            val destFile = destFolder.findFile(fileName)

            if (destFile == null) {
                copyFile(sourceFile, destFolder)
                return SyncFileResult.COPIED

            } else {
                val sourceModified = sourceFile.lastModified()
                val destModified = destFile.lastModified()

                // 2 second tolerance for timestamp differences
                if (sourceModified > destModified + 2000) {
                    destFile.delete()
                    copyFile(sourceFile, destFolder)
                    return SyncFileResult.UPDATED
                } else {
                    return SyncFileResult.SKIPPED
                }
            }

        } catch (e: Exception) {
            val fileName = sourceFile.name ?: "unknown"
            errors.add("Error syncing $fileName: ${e.message}")
            return SyncFileResult.ERROR
        }
    }

    private fun copyFile(sourceFile: DocumentFile, destFolder: DocumentFile) {
        val newFile = destFolder.createFile(
            sourceFile.type ?: "application/octet-stream",
            sourceFile.name!!
        ) ?: throw IOException("Failed to create destination file")

        context.contentResolver.openInputStream(sourceFile.uri)?.use { input ->
            context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                input.copyTo(output, bufferSize = 8192)
            }
        }
    }
}