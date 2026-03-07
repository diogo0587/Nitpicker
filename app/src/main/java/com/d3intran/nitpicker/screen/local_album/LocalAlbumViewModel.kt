package com.d3intran.nitpicker.screen.local_album

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.d3intran.nitpicker.db.MediaMetadataDao
import com.d3intran.nitpicker.model.FileType
import com.d3intran.nitpicker.model.LocalFileItem
import com.d3intran.nitpicker.screen.files.FolderItem
import com.d3intran.nitpicker.util.SafDirectoryViewer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject

data class LocalAlbumUiState(
    val folderName: String = "",
    val folderPath: String = "", // This will now store the URI string
    val files: List<LocalFileItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSelectionModeActive: Boolean = false,
    val selectedFilePaths: Set<String> = emptySet()
) {
    val selectedFileCount: Int get() = selectedFilePaths.size
}

@HiltViewModel
class LocalAlbumViewModel @Inject constructor(
    private val application: Application,
    private val mediaMetadataDao: MediaMetadataDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocalAlbumUiState())
    val uiState: StateFlow<LocalAlbumUiState> = _uiState.asStateFlow()

    private val _snackbarMessages = MutableSharedFlow<String>()
    val snackbarMessages = _snackbarMessages.asSharedFlow()

    private val folderUriString: String

    init {
        folderUriString = savedStateHandle.get<String>("folderPath") ?: ""

        if (folderUriString.isNotEmpty()) {
            try {
                val uri = Uri.parse(folderUriString)
                val documentFile = DocumentFile.fromTreeUri(application, uri)
                val folderName = documentFile?.name ?: "Unknown Album"
                
                _uiState.update { it.copy(folderName = folderName, folderPath = folderUriString) }
                loadFiles()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Invalid album URI provided.") }
            }
        } else {
            _uiState.update { it.copy(error = "Folder URI not provided.") }
        }
    }

    fun loadFiles() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val uri = Uri.parse(folderUriString)
                val rawFiles = SafDirectoryViewer.listFilesFromUri(application, uri)
                
                // 为每个文件补充 AI 元数据
                val filesWithMetadata = rawFiles.map { fileItem ->
                    val metadata = mediaMetadataDao.getMetadataForUri(fileItem.path)
                    if (metadata != null) {
                        fileItem.copy(
                            tags = metadata.tags,
                            faceCount = metadata.faceCount,
                            objectCount = metadata.objectCount
                        )
                    } else {
                        fileItem
                    }
                }
                
                _uiState.update {
                    it.copy(
                        files = filesWithMetadata,
                        isLoading = false,
                        error = null
                    )
                }
            } catch (e: Exception) {
                Log.e("LocalAlbumViewModel", "Error loading files for URI: $folderUriString", e)
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
        val selectedPaths = _uiState.value.selectedFilePaths
        if (selectedPaths.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            var deletedCount = 0
            selectedPaths.forEach { uriString ->
                try {
                    val uri = Uri.parse(uriString)
                    val documentFile = DocumentFile.fromSingleUri(application, uri)
                    if (documentFile?.delete() == true) {
                        deletedCount++
                    }
                } catch (e: Exception) {
                    Log.e("LocalAlbumViewModel", "Failed to delete file: $uriString", e)
                }
            }

            withContext(Dispatchers.Main) {
                if (deletedCount > 0) {
                    _snackbarMessages.emit("Permanently deleted $deletedCount file(s).")
                    loadFiles() // Refresh list
                } else {
                    _snackbarMessages.emit("Failed to delete selected files.")
                }
                exitSelectionMode()
            }
        }
    }

    suspend fun moveSelectedFiles(destinationFolderPath: String) {
        // TODO: Implement SAF-based move (requires copying streams + deleting source)
        _snackbarMessages.emit("Move is currently disabled in SAF mode.")
        exitSelectionMode()
    }

    suspend fun getFoldersForMove(): List<FolderItem> = withContext(Dispatchers.IO) {
        // This could be fetched from SafRepository if we inject it, but leaving empty for now
        emptyList()
    }

    fun retry() {
        loadFiles()
    }
}