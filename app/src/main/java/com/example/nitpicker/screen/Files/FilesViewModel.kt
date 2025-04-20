package com.example.nitpicker.screen.files // Corrected package name

import android.app.Application
import android.os.Environment
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update // Add this import
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException // Add import
import javax.inject.Inject

class FolderAlreadyExistsException(message: String) : IOException(message)
class InvalidFolderNameException(message: String) : IllegalArgumentException(message)

// Keep FolderItem data class
data class FolderItem(
    val name: String,
    val path: String
)

// Define UI State similar to HomeUiState
data class FilesUiState(
    val folders: List<FolderItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class FilesViewModel @Inject constructor(
    private val application: Application
) : ViewModel() {

    // Expose UI State
    private val _uiState = MutableStateFlow(FilesUiState())
    val uiState: StateFlow<FilesUiState> = _uiState.asStateFlow()

    init {
        loadFolders()
    }

    // Function to load folders, updating the UI state
    fun loadFolders() {
        viewModelScope.launch {
            // Update state to loading
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val downloadDir = application.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                if (downloadDir != null && downloadDir.exists() && downloadDir.isDirectory) {
                    val directories = downloadDir.listFiles { file ->
                        file.isDirectory
                    } ?: emptyArray()

                    val folderItems = directories.map { FolderItem(it.name, it.absolutePath) }
                                             .sortedBy { it.name }
                    Log.d("FilesViewModel", "Found ${folderItems.size} folders in ${downloadDir.absolutePath}")
                    // Update state with loaded folders
                    _uiState.update { it.copy(folders = folderItems, isLoading = false) }
                } else {
                    Log.w("FilesViewModel", "Download directory not found or not accessible: ${downloadDir?.absolutePath}")
                    // Update state with empty list and potentially an error
                    _uiState.update { it.copy(folders = emptyList(), isLoading = false/*, error = "Download directory not found."*/) }
                }
            } catch (e: SecurityException) {
                Log.e("FilesViewModel", "Permission denied accessing download directory", e)
                // Update state with error
                _uiState.update { it.copy(error = "Permission denied accessing storage.", isLoading = false, folders = emptyList()) }
            } catch (e: Exception) {
                Log.e("FilesViewModel", "Error loading folders", e)
                // Update state with error
                _uiState.update { it.copy(error = "Error loading folders: ${e.localizedMessage}", isLoading = false, folders = emptyList()) }
            }
            // No finally needed as isLoading is set within update calls
        }
    }

    // Function to create a new folder
    suspend fun createFolder(folderName: String) { // Make it suspend if not already
        val trimmedName = folderName.trim()
        // Basic validation - throw specific exception
        if (trimmedName.isBlank() || trimmedName.contains("/") || trimmedName.contains("\\")) {
            throw InvalidFolderNameException("Invalid characters in name or name is empty.")
        }

        try {
            val downloadDir = application.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (downloadDir == null || !downloadDir.exists() || !downloadDir.isDirectory) {
                throw IOException("Download directory not accessible.") // Keep throwing for general IO errors
            }

            val newFolder = File(downloadDir, trimmedName)

            // Check if file/folder already exists - throw specific exception
            if (newFolder.exists()) {
                throw FolderAlreadyExistsException("Folder '$trimmedName' already exists.")
            }

            // Attempt to create the directory
            val success = withContext(Dispatchers.IO) {
                newFolder.mkdir()
            }

            if (success) {
                Log.d("FilesViewModel", "Created folder: '${newFolder.absolutePath}'")
                // Reload folders to show the new one - Keep this on success
                // Launching loadFolders in a new coroutine to avoid blocking the createFolder result
                viewModelScope.launch { loadFolders() }
            } else {
                // Throw general IO error if mkdir fails for unknown reasons
                throw IOException("Failed to create folder. Check storage permissions.")
            }

        } catch (e: SecurityException) {
            Log.e("FilesViewModel", "Permission denied during folder creation", e)
            // Update global error state for permission issues
            _uiState.update { it.copy(error = "Permission denied during folder creation.", isLoading = false) }
            throw e // Re-throw if needed, or handle differently
        } catch (e: IOException) {
            // Catch specific exceptions first if they are IOExceptions
            if (e is FolderAlreadyExistsException || e is InvalidFolderNameException) {
                throw e // Re-throw our specific exceptions
            }
            // Handle other IO errors globally
            Log.e("FilesViewModel", "IO error during folder creation", e)
            _uiState.update { it.copy(error = "Create folder failed: ${e.message}", isLoading = false) }
            throw e // Re-throw if needed
        } catch (e: Exception) {
             // Catch our specific exceptions if they are not IOExceptions
            if (e is InvalidFolderNameException) {
                 throw e // Re-throw our specific exceptions
            }
            // Handle other unexpected errors globally
            Log.e("FilesViewModel", "Unexpected error during folder creation", e)
            _uiState.update { it.copy(error = "An unexpected error occurred.", isLoading = false) }
            throw e // Re-throw if needed
        }
        // No finally block needed to reset isLoading as it's handled by loadFolders or error updates
    }

    // Function to rename a folder
    fun renameFolder(oldPath: String, newName: String) {
        // Basic validation for the new name (prevent empty, slashes, etc.)
        if (newName.isBlank() || newName.contains("/") || newName.contains("\\")) {
            _uiState.update { it.copy(error = "Invalid folder name.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) } // Show loading during rename
            try {
                val oldFile = File(oldPath)
                // Ensure the parent directory exists (should generally be the case here)
                val parentDir = oldFile.parentFile
                if (parentDir == null || !oldFile.exists() || !oldFile.isDirectory) {
                    throw IOException("Original folder not found or invalid.")
                }

                val newFile = File(parentDir, newName)

                // Check if a file/folder with the new name already exists
                if (newFile.exists()) {
                    throw IOException("A folder or file with the name '$newName' already exists.")
                }

                val success = withContext(Dispatchers.IO) {
                    oldFile.renameTo(newFile)
                }

                if (success) {
                    Log.d("FilesViewModel", "Renamed '$oldPath' to '$newName'")
                    // Reload folders to reflect the change
                    loadFolders() // This will set isLoading back to false on completion/error
                } else {
                    throw IOException("Failed to rename folder. Check storage permissions or file system.")
                }

            } catch (e: SecurityException) {
                Log.e("FilesViewModel", "Permission denied during rename", e)
                _uiState.update { it.copy(error = "Permission denied during rename.", isLoading = false) }
            } catch (e: IOException) {
                Log.e("FilesViewModel", "IO error during rename", e)
                _uiState.update { it.copy(error = "Rename failed: ${e.message}", isLoading = false) }
            } catch (e: Exception) {
                Log.e("FilesViewModel", "Unexpected error during rename", e)
                _uiState.update { it.copy(error = "An unexpected error occurred during rename.", isLoading = false) }
            }
        }
    }

    // Function to delete a folder
    fun deleteFolder(path: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) } // Indicate loading during delete
            try {
                val folder = File(path)
                if (!folder.exists() || !folder.isDirectory) {
                    throw IOException("Folder not found or invalid.")
                }

                // Recursively delete the folder and its contents
                val success = withContext(Dispatchers.IO) {
                    folder.deleteRecursively()
                }

                if (success) {
                    Log.d("FilesViewModel", "Deleted folder: '$path'")
                    // Reload folders to reflect the change
                    loadFolders() // Refreshes the list and sets isLoading to false
                } else {
                    // deleteRecursively might return false if some files couldn't be deleted
                    throw IOException("Failed to delete folder or some of its contents. Check permissions.")
                }

            } catch (e: SecurityException) {
                Log.e("FilesViewModel", "Permission denied during delete", e)
                _uiState.update { it.copy(error = "Permission denied during delete.", isLoading = false) }
            } catch (e: IOException) {
                Log.e("FilesViewModel", "IO error during delete", e)
                _uiState.update { it.copy(error = "Delete failed: ${e.message}", isLoading = false) }
            } catch (e: Exception) {
                Log.e("FilesViewModel", "Unexpected error during delete", e)
                _uiState.update { it.copy(error = "An unexpected error occurred during delete.", isLoading = false) }
            }
        }
    }

    // Optional: Add a retry function if needed, similar to HomeViewModel
    fun retry() {
        loadFolders()
    }
}