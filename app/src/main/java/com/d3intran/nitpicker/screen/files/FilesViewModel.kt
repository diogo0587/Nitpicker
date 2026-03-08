package com.d3intran.nitpicker.screen.files

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.d3intran.nitpicker.repository.SafRepository
import com.d3intran.nitpicker.worker.MediaTaggingWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.d3intran.nitpicker.db.MediaMetadataDao

data class FolderItem(
    val name: String,
    val path: String // We now store the URI string here
)

data class FilesUiState(
    val folders: List<FolderItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class FilesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val safRepository: SafRepository,
    private val mediaMetadataDao: MediaMetadataDao
) : ViewModel() {

    private val workManager = WorkManager.getInstance(context)

    private val _uiState = MutableStateFlow(FilesUiState(isLoading = true))
    val uiState: StateFlow<FilesUiState> = _uiState.asStateFlow()

    init {
        observeSafDirectories()
    }

    private fun observeSafDirectories() {
        viewModelScope.launch {
            safRepository.savedDirectoriesFlow
                .catch { e ->
                    Log.e("FilesViewModel", "Error loading SAF directories", e)
                    _uiState.update { it.copy(isLoading = false, error = "Failed to load libraries: ${e.message}") }
                }
                .collect { uris ->
                    _uiState.update { it.copy(isLoading = true, error = null) }
                    
                    val folderItems = uris.mapNotNull { uri ->
                        val documentFile = DocumentFile.fromTreeUri(context, uri)
                        if (documentFile != null && documentFile.exists() && documentFile.canRead()) {
                            // 确保每个已授权的目录都在后台进行索引扫描（使用 KEEP 避免重复任务）
                            enqueueIndexingWorker(uri, ExistingWorkPolicy.KEEP)
                            
                            FolderItem(
                                name = documentFile.name ?: "Unknown Folder",
                                path = uri.toString()
                            )
                        } else {
                            Log.w("FilesViewModel", "Document folder missing or access revoked: $uri. Cleaning up database.")
                            // The folder was deleted externally or permissions revoked
                            viewModelScope.launch {
                                // Remove from remembered URIs
                                safRepository.removeDirectory(uri)
                                // Clean up all AI database tags associated with files in this folder
                                mediaMetadataDao.deleteMetadataByPrefix(uri.toString())
                            }
                            null
                        }
                    }.sortedBy { it.name }
                    
                    _uiState.update { it.copy(folders = folderItems, isLoading = false) }
                }
        }
    }

    fun addSafDirectory(uri: Uri) {
        viewModelScope.launch {
            try {
                safRepository.addDirectory(uri)
                // 启动后台索引任务（新添加目录使用 REPLACE 确保立即开始）
                enqueueIndexingWorker(uri, ExistingWorkPolicy.REPLACE)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to add directory: ${e.message}") }
            }
        }
    }

    private fun enqueueIndexingWorker(uri: Uri, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
        val workRequest = OneTimeWorkRequestBuilder<MediaTaggingWorker>()
            .setInputData(workDataOf("folderUri" to uri.toString()))
            .build()
        
        workManager.enqueueUniqueWork(
            "indexing_${uri.toString().hashCode()}",
            policy,
            workRequest
        )
        Log.d("FilesViewModel", "Enqueued indexing worker for: $uri with policy: $policy")
    }

    fun removeFolder(uriString: String) {
        viewModelScope.launch {
            try {
                // Delete tags for all files within this directory from the local DB
                mediaMetadataDao.deleteMetadataByPrefix(uriString)
                
                // Relinquish exact URI permissions and stop tracking
                safRepository.removeDirectory(Uri.parse(uriString))
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to remove directory: ${e.message}") }
            }
        }
    }

    fun retry() {
        // StateFlow collection handles updates natively, but if it faults we could restart it
        observeSafDirectories()
    }
}