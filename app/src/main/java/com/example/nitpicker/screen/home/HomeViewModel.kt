package com.example.nitpicker.screen.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nitpicker.model.Album
import com.example.nitpicker.repository.AlbumRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 首页视图模型
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: AlbumRepository
) : ViewModel() {
    
    // UI状态
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    // 搜索关键词
    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()
    
    /**
     * 更新搜索文本
     */
    fun updateSearchText(text: String) {
        _searchText.value = text
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
        val artist = repository.getLastSearchArtist()
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
        if (uiState.value.currentPage > 0 && repository.getLastSearchArtist().isNotEmpty()) {
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
    val albums: List<Album> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentPage: Int = 0,
    val totalPages: Int = 0
)