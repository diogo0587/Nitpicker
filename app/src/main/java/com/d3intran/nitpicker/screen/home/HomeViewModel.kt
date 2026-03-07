package com.d3intran.nitpicker.screen.home

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.d3intran.nitpicker.db.MediaMetadataDao
import com.d3intran.nitpicker.model.Album
import com.d3intran.nitpicker.model.FileType
import com.d3intran.nitpicker.model.LocalFileItem
import com.d3intran.nitpicker.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * 首页视图模型
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val mediaMetadataDao: MediaMetadataDao
) : ViewModel() {
    
    // UI状态
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    // 搜索关键词
    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()
    
    init {
        loadAIInsights()
    }

    private fun loadAIInsights() {
        // 使用 Flow 观察数据变化，实现 UI 实时更新
        mediaMetadataDao.getAllMetadata()
            .onEach { allMetadata ->
                val totalFaces = allMetadata.sumOf { it.faceCount }
                val totalObjects = allMetadata.sumOf { it.objectCount }
                
                // 获取热门标签
                val allTags = allMetadata.flatMap { it.tags }
                val topTags = allTags.groupingBy { it }
                    .eachCount()
                    .toList()
                    .sortedByDescending { it.second }
                    .take(10)
                    .map { it.first }

                _uiState.update { it.copy(
                    stats = AIStats(
                        totalIndexedItems = allMetadata.size,
                        totalFacesDetected = totalFaces,
                        totalObjectsDetected = totalObjects
                    ),
                    topTags = topTags
                ) }
            }
            .launchIn(viewModelScope)
    }

    /**
     * 更新搜索文本
     */
    fun updateSearchText(text: String) {
        _searchText.value = text
        // 当文本改变时同步搜索本地
        if (text.length >= 2) {
            searchLocalAI(text)
        } else if (text.isEmpty()) {
            _uiState.update { it.copy(localResults = emptyList()) }
        }
    }

    private fun searchLocalAI(query: String) {
        viewModelScope.launch {
            val results = mediaMetadataDao.searchByTag("%$query%").first()
            _uiState.update { it.copy(localResults = mapMetadataToLocalFiles(results)) }
        }
    }

    /**
     * 显示所有检测到人脸的媒体
     */
    fun showFaces() {
        _searchText.value = "Faces"
        viewModelScope.launch {
            val results = mediaMetadataDao.getMetadataWithFaces().first()
            _uiState.update { it.copy(localResults = mapMetadataToLocalFiles(results)) }
        }
    }

    /**
     * 显示所有检测到物体的媒体
     */
    fun showObjects() {
        _searchText.value = "Objects"
        viewModelScope.launch {
            val results = mediaMetadataDao.getMetadataWithObjects().first()
            _uiState.update { it.copy(localResults = mapMetadataToLocalFiles(results)) }
        }
    }

    private fun mapMetadataToLocalFiles(results: List<com.d3intran.nitpicker.db.MediaMetadataEntity>): List<LocalFileItem> {
        return results.map { meta ->
            LocalFileItem(
                path = meta.uri,
                name = meta.uri.substringAfterLast('/'),
                type = if (meta.uri.endsWith(".mp4", true)) FileType.VIDEO else FileType.IMAGE,
                thumbnailUri = Uri.parse(meta.uri),
                size = 0,
                lastModified = 0,
                tags = meta.tags,
                faceCount = meta.faceCount,
                objectCount = meta.objectCount
            )
        }
    }
    
    /**
     * 搜索专辑
     */
    fun searchAlbums() {
        val query = _searchText.value.trim()
        if (query.isEmpty()) {
             _uiState.update { it.copy(
                 error = "Please enter an artist name to search.",
                 albums = emptyList(),
                 currentPage = 0,
                 totalPages = 0,
                 isLoading = false
             ) }
            return
        }

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }
                val (albums, totalPages) = repository.searchAlbums(query) // This can now throw IOException
                Log.d("HomeViewModel", "搜索结果: ${albums.size} 个专辑, 总页数: $totalPages")
                _uiState.update { it.copy(
                    albums = albums,
                    currentPage = 1,
                    totalPages = totalPages,
                    isLoading = false,
                    error = null // Clear error on success
                ) }
            } catch (e: Exception) { // Catch Exception to handle IOException and others
                Log.e("HomeViewModel", "搜索失败 for query: $query", e)
                _uiState.update { it.copy(
                    // Use a more user-friendly message or the exception message
                    error = e.localizedMessage ?: "Search failed. Check connection or query.",
                    isLoading = false,
                    albums = emptyList(), // Clear albums on search error
                    currentPage = 0,      // Reset pagination on search error
                    totalPages = 0
                ) }
            }
        }
    }
    
    /**
     * 加载指定页的专辑
     */
    fun loadPage(page: Int) {
        // Ensure page is valid before attempting load
        if (page <= 0 || page > uiState.value.totalPages) {
            Log.w("HomeViewModel", "Attempted to load invalid page: $page")
            return
        }
        val artist = repository.getLastSearchQuery()
        if (artist.isEmpty()) {
             Log.w("HomeViewModel", "Cannot load page, last search artist is empty.")
             _uiState.update { it.copy(error = "Cannot load page, perform a search first.") }
            return
        }

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }
                val albums = repository.getAlbumsByPage(artist, page) // This can now throw IOException
                _uiState.update { it.copy(
                    albums = albums,
                    currentPage = page,
                    isLoading = false,
                    error = null // Clear error on success
                ) }
            } catch (e: Exception) { // Catch Exception
                Log.e("HomeViewModel", "加载页面 $page 失败 for artist: $artist", e)
                _uiState.update { it.copy(
                    // Provide specific error for page load failure
                    error = e.localizedMessage ?: "Failed to load page $page. Check connection.",
                    isLoading = false
                    // Keep previous albums displayed when a page load fails
                ) }
            }
        }
    }
    
    /**
     * 重试上次失败的操作
     */
    fun retry() {
        // Clear the error before retrying
        _uiState.update { it.copy(error = null) }
        // Decide whether to retry search or page load based on current state
        if (uiState.value.currentPage > 0 && repository.getLastSearchQuery().isNotEmpty()) {
            loadPage(uiState.value.currentPage)
        } else if (searchText.value.isNotEmpty()){
            searchAlbums()
        } else {
             _uiState.update { it.copy(error = "Nothing to retry. Please search first.") }
        }
    }
}

/**
 * 主页UI状态
 */
data class HomeUiState(
    val albums: List<Album> = emptyList(), // 线上搜索结果
    val localResults: List<LocalFileItem> = emptyList(), // 本地 AI 搜索结果
    val topTags: List<String> = emptyList(),
    val stats: AIStats = AIStats(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentPage: Int = 0,
    val totalPages: Int = 0
)

data class AIStats(
    val totalIndexedItems: Int = 0,
    val totalFacesDetected: Int = 0,
    val totalObjectsDetected: Int = 0
)