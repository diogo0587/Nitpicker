package com.d3intran.nitpicker.screen.album

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.d3intran.nitpicker.model.FileInfo
import com.d3intran.nitpicker.repository.AlbumRepository
import com.d3intran.nitpicker.service.DownloadManagerService // Import DownloadManagerService
import dagger.hilt.android.lifecycle.HiltViewModel // Import HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject // Import Inject

// 定义UI状态数据类
data class AlbumUiState(
    val albumUrl: String = "",
    val albumTitle: String = "Album", // 默认标题
    val allFiles: List<FileInfo> = emptyList(),
    val selectedFiles: Map<String, FileInfo> = emptyMap(), // 使用 Map<pageUrl, FileInfo> 存储选中项
    val queuedFiles: Set<String> = emptySet(), // 使用 Set<pageUrl> 存储已加入下载队列的文件
    val isLoading: Boolean = true,
    val error: String? = null,
    val currentFilter: FilterType = FilterType.ALL
) {
    val selectedFileCount: Int
        get() = selectedFiles.size
}

// 定义过滤器类型
enum class FilterType { ALL, IMAGES, OTHER }

@HiltViewModel // Add HiltViewModel annotation
class AlbumViewModel @Inject constructor( // Use @Inject constructor
    private val savedStateHandle: SavedStateHandle,
    private val downloadManagerService: DownloadManagerService, // Inject DownloadManagerService
    private val repository: AlbumRepository // Inject Repository if provided via Hilt, otherwise keep internal instance
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumUiState())
    val uiState: StateFlow<AlbumUiState> = _uiState.asStateFlow()

    // Channel 用于发送一次性事件，如 Snack bar 消息
    private val _snackbarMessages = Channel<String>()
    val snackbarMessages = _snackbarMessages.receiveAsFlow()

    init {
        // 从 SavedStateHandle 获取导航参数
        val encodedUrl = savedStateHandle.get<String>("albumUrl") ?: ""
        val encodedTitle = savedStateHandle.get<String>("albumTitle") ?: "Album"
        // 解码 URL 和标题
        val albumUrl = try { URLDecoder.decode(encodedUrl, "UTF-8") } catch (e: Exception) { encodedUrl }
        val albumTitle = try { URLDecoder.decode(encodedTitle, "UTF-8") } catch (e: Exception) { encodedTitle }

        _uiState.update { it.copy(albumUrl = albumUrl, albumTitle = albumTitle) }
        fetchFileInfo()
    }

    // 获取文件信息
    private fun fetchFileInfo() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = repository.getFileInfo(_uiState.value.albumUrl)
            result.onSuccess { files ->
                _uiState.update { currentState -> // Use currentState for clarity
                    currentState.copy(
                        // Ensure newly fetched files respect existing queued and selected state
                        allFiles = files.map { file ->
                            file.copy(isSelected = currentState.selectedFiles.containsKey(file.pageUrl))
                        },
                        isLoading = false
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        error = throwable.message ?: "Failed to load album details.",
                        isLoading = false
                    )
                }
            }
        }
    }

    // 重试加载
    fun retry() {
        fetchFileInfo()
    }

    // 切换文件选中状态
    fun toggleFileSelection(file: FileInfo) {
        _uiState.update { currentState ->
            // 如果文件已加入队列，则不允许选中/取消选中
            if (currentState.queuedFiles.contains(file.pageUrl)) {
                return@update currentState
            }

            val currentSelected = currentState.selectedFiles.toMutableMap()
            val isCurrentlySelected = currentSelected.containsKey(file.pageUrl)

            // 更新 selectedFiles Map
            if (isCurrentlySelected) {
                currentSelected.remove(file.pageUrl)
            } else {
                currentSelected[file.pageUrl] = file // Store the actual FileInfo object
            }

            // 更新 allFiles 列表中的 isSelected 状态
            val updatedAllFiles = currentState.allFiles.map { existingFile ->
                if (existingFile.pageUrl == file.pageUrl) {
                    // 创建一个新的 FileInfo 实例，切换 isSelected 状态
                    existingFile.copy(isSelected = !isCurrentlySelected)
                } else {
                    existingFile // 其他文件保持不变
                }
            }

            // 更新 UI State
            currentState.copy(
                selectedFiles = currentSelected,
                allFiles = updatedAllFiles // 使用更新后的 allFiles 列表
            )
        }
    }

    /**
     * Toggles selection for all currently visible (filtered) files.
     * @param selectAll True to select all visible, false to deselect all.
     */
    fun toggleSelectAllVisible(selectAll: Boolean) {
        viewModelScope.launch {
            val currentState = _uiState.value
            // Apply the current filter to get the list of visible files
            val visibleFiles = filterFiles(currentState.allFiles, currentState.currentFilter)

            val currentSelected = currentState.selectedFiles.toMutableMap()

            if (selectAll) {
                // Add all visible files to selection (excluding already queued ones)
                visibleFiles.forEach { file ->
                    if (!currentState.queuedFiles.contains(file.pageUrl)) {
                        currentSelected[file.pageUrl] = file // Add FileInfo object
                    }
                }
            } else {
                // Deselect all files (or just the visible ones - deselecting all is simpler)
                currentSelected.clear()
                // Alternative: Deselect only visible ones:
                // val visibleFileUrls = visibleFiles.map { it.pageUrl }.toSet()
                // visibleFileUrls.forEach { currentSelected.remove(it) }
            }

            _uiState.update {
                it.copy(
                    selectedFiles = currentSelected
                    // No need to update allFiles manually
                )
            }
        }
    }

    // 设置过滤器
    fun setFilter(filterType: FilterType) {
        _uiState.update { it.copy(currentFilter = filterType) }
    }

    // 将选中的文件加入下载队列
    fun queueSelectedFilesForDownload() {
        viewModelScope.launch {
            val currentState = _uiState.value // Get current state once
            val selectedCount = currentState.selectedFileCount
            if (selectedCount > 0) {
                // Get the actual FileInfo objects to queue
                val filesToQueueInfo = currentState.selectedFiles.values.toList()
                val filesToQueueUrls = filesToQueueInfo.map { it.pageUrl }.toSet() // Get URLs for state update

                // 更新 UI state: add to queue, clear selection, reset isSelected in allFiles
                _uiState.update { state -> // Renamed to 'state' for clarity
                    val updatedAllFiles = state.allFiles.map { file ->
                        // 如果文件在刚加入队列的URL集合中，将其 isSelected 设为 false
                        if (filesToQueueUrls.contains(file.pageUrl)) {
                            file.copy(isSelected = false)
                        } else {
                            file
                        }
                    }
                    state.copy(
                        queuedFiles = state.queuedFiles + filesToQueueUrls, // Add URLs to queued set
                        selectedFiles = emptyMap(), // Clear selection map
                        allFiles = updatedAllFiles // Update allFiles to reset selection state for queued items
                    )
                }

                // Trigger the download service
                downloadManagerService.enqueueDownloads(filesToQueueInfo, currentState.albumTitle)

                // Send Snackbar message
                try {
                    _snackbarMessages.send("Added $selectedCount files to download queue.")
                } catch (e: Exception) {
                    // Handle potential channel send exception (e.g., if scope is cancelled)
                    println("Error sending snackbar message: ${e.message}")
                }
                println("Queued files for download: $filesToQueueUrls") // Log URLs
            }
        }
    }
}

// Keep the filterFiles function outside the ViewModel or in a separate utility file
fun filterFiles(files: List<FileInfo>, filter: FilterType): List<FileInfo> {
    return when (filter) {
        FilterType.ALL -> files
        FilterType.IMAGES -> files.filter {
            val ext = it.fileType.lowercase()
            ext in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "avif")
        }
        FilterType.OTHER -> files.filter {
            val ext = it.fileType.lowercase()
            ext !in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "avif")
        }
    }
}
