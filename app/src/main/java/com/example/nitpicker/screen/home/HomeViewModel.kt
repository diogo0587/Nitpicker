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
        if (query.isEmpty()) return
        
        viewModelScope.launch {
            try {
                // 更新状态为加载中
                _uiState.update { it.copy(
                    isLoading = true,
                    error = null
                ) }
                
                // 执行搜索
                val (albums, totalPages) = repository.searchAlbums(query)
                
                // 记录搜索日志
                Log.d("HomeViewModel", "搜索结果: ${albums.size} 个专辑, 总页数: $totalPages")
                
                // 更新状态
                _uiState.update { it.copy(
                    albums = albums,
                    currentPage = 1,
                    totalPages = totalPages,
                    isLoading = false
                ) }
            } catch (e: Exception) {
                // 记录错误日志
                Log.e("HomeViewModel", "搜索失败", e)
                
                // 处理错误
                _uiState.update { it.copy(
                    error = e.localizedMessage ?: "搜索失败",
                    isLoading = false
                ) }
            }
        }
    }
    
    /**
     * 加载指定页的专辑
     */
    fun loadPage(page: Int) {
        val artist = repository.getLastSearchArtist()
        if (artist.isEmpty()) return
        
        viewModelScope.launch {
            try {
                // 更新状态为加载中
                _uiState.update { it.copy(
                    isLoading = true,
                    error = null
                ) }
                
                // 获取指定页的专辑
                val albums = repository.getAlbumsByPage(artist, page)
                
                // 更新状态
                _uiState.update { it.copy(
                    albums = albums,
                    currentPage = page,
                    isLoading = false
                ) }
            } catch (e: Exception) {
                // 处理错误
                _uiState.update { it.copy(
                    error = e.localizedMessage ?: "加载失败",
                    isLoading = false
                ) }
            }
        }
    }
    
    /**
     * 重试上次失败的操作
     */
    fun retry() {
        if (uiState.value.currentPage > 0) {
            loadPage(uiState.value.currentPage)
        } else {
            searchAlbums()
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