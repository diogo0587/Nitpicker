package com.d3intran.nitpicker.screen.local_album

import android.app.Application
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.d3intran.nitpicker.model.FileType
import com.d3intran.nitpicker.model.LocalFileItem
import com.d3intran.nitpicker.screen.files.FolderItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject

data class LocalAlbumUiState(
    val folderName: String = "",
    val folderPath: String = "",
    val files: List<LocalFileItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSelectionModeActive: Boolean = false,
    val selectedFilePaths: Set<String> = emptySet()
) {
    val selectedFileCount: Int get() = selectedFilePaths.size
}

class FileOperationException(message: String) : IOException(message)

@HiltViewModel
class LocalAlbumViewModel @Inject constructor(
    private val application: Application,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocalAlbumUiState())
    val uiState: StateFlow<LocalAlbumUiState> = _uiState.asStateFlow()

    private val _snackbarMessages = MutableSharedFlow<String>()
    val snackbarMessages = _snackbarMessages.asSharedFlow()

    private val folderPath: String

    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
    private val videoExtensions = setOf("mp4", "mkv", "avi", "mov", "wmv", "3gp")

    init {
        val rawPathArg = savedStateHandle.get<String>("folderPath") ?: ""
        Log.d("ViewModelInit", "Received raw folderPath argument from SavedStateHandle: $rawPathArg") // Should show the '+' version

        folderPath = try {
            URLDecoder.decode(rawPathArg, StandardCharsets.UTF_8.toString())
        } catch (e: Exception) {
            Log.e("ViewModelInit", "Failed to decode folderPath: $rawPathArg", e)
            rawPathArg
        }

        Log.d("ViewModelInit", "Decoded and using folderPath: $folderPath") // Should now show the path with spaces

        if (folderPath.isNotEmpty()) {
            val folder = File(folderPath)
            _uiState.update { it.copy(folderName = folder.name, folderPath = folderPath) }
            loadFiles()
        } else {
            _uiState.update { it.copy(error = "Folder path not provided.") }
        }
    }

    fun loadFiles() {
        loadFilesForPath(folderPath)
    }

    private fun loadFilesForPath(folderPath: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val folder = File(folderPath)
                if (!folder.exists() || !folder.isDirectory) {
                    throw IOException("Folder not found or is not a directory: $folderPath")
                }

                val files = folder.listFiles()
                if (files == null) {
                    // Handle case where listFiles returns null (e.g., permission issue)
                    _uiState.update { it.copy(files = emptyList(), isLoading = false, error = "Could not list files in folder.") }
                    Log.e("LocalAlbumViewModel", "listFiles() returned null for path: $folderPath")
                    return@launch
                }

                val fileItems = mutableListOf<LocalFileItem>()
                withContext(Dispatchers.IO) { // Keep file processing off the main thread
                    files.forEach { file ->
                        processFile(file)?.let { fileItems.add(it) }
                    }
                }

                // --- SORT THE LIST HERE (Videos first, then Images, then by name) ---
                val sortedFileItems = fileItems.sortedWith(
                    compareBy<LocalFileItem> {
                        // Assign lower number to VIDEO to make it appear first
                        when (it.type) {
                            FileType.VIDEO -> 0
                            FileType.IMAGE -> 1
                        }
                    }.thenBy { it.name } // Then sort by name within each type group
                )
                // --- END SORTING ---

                _uiState.update {
                    it.copy(
                        files = sortedFileItems, // <-- Use the new multi-level sorted list
                        isLoading = false,
                        folderPath = folderPath,
                        folderName = folder.name,
                        error = null // Clear previous error on success
                    )
                }
                Log.d("LocalAlbumViewModel", "Loaded and sorted ${sortedFileItems.size} files (videos first) for path: $folderPath")

            } catch (e: Exception) {
                Log.e("LocalAlbumViewModel", "Error loading files for path: $folderPath", e)
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load files.") }
            }
        }
    }

    fun enterSelectionMode() {
        if (!_uiState.value.isSelectionModeActive) {
            _uiState.update {
                it.copy(
                    isSelectionModeActive = true,
                    selectedFilePaths = emptySet()
                )
            }
        }
    }

    fun exitSelectionMode() {
        _uiState.update {
            it.copy(
                isSelectionModeActive = false,
                selectedFilePaths = emptySet()
            )
        }
    }

    fun toggleSelection(path: String) {
        if (!_uiState.value.isSelectionModeActive) return

        _uiState.update { currentState ->
            val currentSelection = currentState.selectedFilePaths
            val newSelection = if (currentSelection.contains(path)) {
                currentSelection - path
            } else {
                currentSelection + path
            }
            val newIsSelectionModeActive = newSelection.isNotEmpty()
            currentState.copy(
                selectedFilePaths = newSelection,
                isSelectionModeActive = newIsSelectionModeActive
            )
        }
    }

    fun selectAll() {
        if (!_uiState.value.isSelectionModeActive) return
        _uiState.update { currentState ->
            val allPaths = currentState.files.map { it.path }.toSet()
            currentState.copy(selectedFilePaths = allPaths)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedFilePaths = emptySet()) }
    }

    fun deleteSelectedFiles() {
        val pathsToDelete = _uiState.value.selectedFilePaths.toList()
        if (pathsToDelete.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            var successCount = 0
            var errorMsg: String? = null
            try {
                withContext(Dispatchers.IO) {
                    pathsToDelete.forEach { path ->
                        try {
                            val file = File(path)
                            if (file.exists() && file.delete()) {
                                successCount++
                            } else {
                                Log.w("LocalAlbumViewModel", "Failed to delete file: $path")
                            }
                        } catch (e: SecurityException) {
                            Log.e("LocalAlbumViewModel", "SecurityException deleting file: $path", e)
                            throw FileOperationException("Permission denied to delete one or more files.")
                        } catch (e: Exception) {
                            Log.e("LocalAlbumViewModel", "Exception deleting file: $path", e)
                        }
                    }
                }
                _snackbarMessages.emit("$successCount file(s) deleted.")
            } catch (e: FileOperationException) {
                errorMsg = e.message
            } catch (e: Exception) {
                Log.e("LocalAlbumViewModel", "Unexpected error during delete operation", e)
                errorMsg = "An unexpected error occurred during deletion."
            } finally {
                exitSelectionMode()
                loadFiles()
                if (errorMsg != null) {
                    _uiState.update { it.copy(error = errorMsg, isLoading = false) }
                    _snackbarMessages.emit("Deletion failed: $errorMsg")
                }
            }
        }
    }

    suspend fun moveSelectedFiles(destinationFolderPath: String) {
        val pathsToMove = _uiState.value.selectedFilePaths.toList()
        if (pathsToMove.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            var successCount = 0
            var errorMsg: String? = null
            val destinationDir = File(destinationFolderPath)

            if (!withContext(Dispatchers.IO) { destinationDir.exists() && destinationDir.isDirectory }) {
                errorMsg = "Destination folder does not exist."
            } else {
                try {
                    withContext(Dispatchers.IO) {
                        pathsToMove.forEach { sourcePath ->
                            val sourceFile = File(sourcePath)
                            val destinationFile = File(destinationDir, sourceFile.name)

                            if (destinationFile.exists()) {
                                Log.w("LocalAlbumViewModel", "Skipping move for ${sourceFile.name}: Destination file already exists.")
                                return@forEach
                            }

                            try {
                                Files.move(
                                    Paths.get(sourcePath),
                                    Paths.get(destinationFile.absolutePath),
                                    StandardCopyOption.ATOMIC_MOVE
                                )
                                successCount++
                            } catch (e: java.nio.file.FileAlreadyExistsException) {
                                Log.w("LocalAlbumViewModel", "File already exists (NIO): ${destinationFile.name}")
                            } catch (e: java.nio.file.AtomicMoveNotSupportedException) {
                                Log.w("LocalAlbumViewModel", "Atomic move not supported for $sourcePath, attempting non-atomic.")
                                try {
                                    Files.move(Paths.get(sourcePath), Paths.get(destinationFile.absolutePath))
                                    successCount++
                                } catch (moveEx: Exception) {
                                    Log.e("LocalAlbumViewModel", "Fallback move failed for ${sourceFile.name}", moveEx)
                                }
                            } catch (e: SecurityException) {
                                Log.e("LocalAlbumViewModel", "SecurityException moving file: ${sourceFile.name}", e)
                                throw FileOperationException("Permission denied to move one or more files.")
                            } catch (e: IOException) {
                                Log.e("LocalAlbumViewModel", "IOException moving file: ${sourceFile.name}", e)
                            }
                        }
                    }
                    _snackbarMessages.emit("$successCount file(s) moved to ${destinationDir.name}.")
                } catch (e: FileOperationException) {
                    errorMsg = e.message
                } catch (e: Exception) {
                    Log.e("LocalAlbumViewModel", "Unexpected error during move operation", e)
                    errorMsg = "An unexpected error occurred during move."
                }
            }

            exitSelectionMode()
            loadFiles()
            if (errorMsg != null) {
                _uiState.update { it.copy(error = errorMsg, isLoading = false) }
                _snackbarMessages.emit("Move failed: $errorMsg")
            }
        }
    }

    suspend fun getFoldersForMove(): List<FolderItem> = withContext(Dispatchers.IO) {
        try {
            val downloadDir = application.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (downloadDir != null && downloadDir.exists() && downloadDir.isDirectory) {
                downloadDir.listFiles { file ->
                    file.isDirectory && file.absolutePath != folderPath
                }?.map { FolderItem(it.name, it.absolutePath) }?.sortedBy { it.name } ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("LocalAlbumViewModel", "Error fetching folders for move dialog", e)
            emptyList()
        }
    }

    private suspend fun processFile(file: File): LocalFileItem? = withContext(Dispatchers.IO) {
        if (!file.isFile) return@withContext null
        val name = file.name
        val path = file.absolutePath
        val extension = file.extension.lowercase(Locale.ROOT)
        val size = file.length()
        val lastModified = file.lastModified()
        var durationMillis: Long? = null

        val type = when {
            imageExtensions.contains(extension) -> FileType.IMAGE
            videoExtensions.contains(extension) -> FileType.VIDEO
            else -> return@withContext null
        }

        if (type == FileType.VIDEO) {
            var retriever: MediaMetadataRetriever? = null
            try {
                retriever = MediaMetadataRetriever()
                retriever.setDataSource(file.absolutePath)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                durationMillis = durationStr?.toLongOrNull()
            } catch (e: Exception) {
                Log.w("LocalAlbumViewModel", "Failed to get duration for $name", e)
            } finally {
                try {
                    retriever?.release()
                } catch (e: IOException) {
                    Log.e("LocalAlbumViewModel", "Error releasing retriever", e)
                }
            }
        }

        val thumbnailUri: Uri? = try {
            when (type) {
                FileType.IMAGE -> file.toUri()
                FileType.VIDEO -> getVideoThumbnailUri(file)
            }
        } catch (e: Exception) {
            Log.e("LocalAlbumViewModel", "Error getting thumbnail for $name", e)
            null
        }

        return@withContext LocalFileItem(path, name, type, thumbnailUri, size, lastModified, durationMillis)
    }

    private suspend fun getVideoThumbnailUri(file: File): Uri? = withContext(Dispatchers.IO) {
        val cacheDir = application.cacheDir
        val cacheKey = "${file.absolutePath}_${file.lastModified()}"
        val cacheFileName = "thumb_${sha256(cacheKey)}.jpg"
        val cacheFile = File(cacheDir, cacheFileName)

        if (cacheFile.exists() && cacheFile.length() > 0) {
            Log.d("ThumbnailCache", "Using cached thumbnail for: ${file.name}")
            return@withContext cacheFile.toUri()
        }

        Log.d("ThumbnailCache", "Generating new thumbnail for: ${file.name}")
        var bitmap: Bitmap? = null
        try {
            bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ThumbnailUtils.createVideoThumbnail(file, Size(256, 256), null)
            } else {
                @Suppress("DEPRECATION")
                ThumbnailUtils.createVideoThumbnail(file.absolutePath, MediaStore.Images.Thumbnails.MINI_KIND)
            }

            if (bitmap == null) {
                Log.w("LocalAlbumViewModel", "ThumbnailUtils failed for: ${file.name}")
                return@withContext null
            }

            var fos: FileOutputStream? = null
            try {
                fos = FileOutputStream(cacheFile)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)
                fos.flush()
                Log.d("ThumbnailCache", "Saved new thumbnail to cache: ${cacheFile.absolutePath}")
                return@withContext cacheFile.toUri()
            } catch (e: IOException) {
                Log.e("LocalAlbumViewModel", "Failed to save thumbnail bitmap to cache for ${file.name}", e)
                cacheFile.delete()
                return@withContext null
            } finally {
                try {
                    fos?.close()
                } catch (e: IOException) {
                }
            }

        } catch (e: Exception) {
            Log.e("LocalAlbumViewModel", "Error generating video thumbnail for ${file.name}", e)
            return@withContext null
        } finally {
            bitmap?.recycle()
        }
    }

    private fun sha256(input: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(input.toByteArray(StandardCharsets.UTF_8))
            hashBytes.fold("") { str, it -> str + "%02x".format(it) }
        } catch (e: Exception) {
            Log.e("HashingError", "SHA-256 failed, using hashCode as fallback", e)
            input.hashCode().toString()
        }
    }

    fun retry() {
        loadFiles()
    }
}