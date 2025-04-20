package com.d3intran.nitpicker.repository

import com.d3intran.nitpicker.api.AlbumApiService
import com.d3intran.nitpicker.model.Album
import com.d3intran.nitpicker.model.FileInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 专辑数据仓库，负责管理专辑数据的获取和缓存
 */
@Singleton
class AlbumRepository @Inject constructor(
    private val apiService: AlbumApiService
) {
    
    // 缓存最后一次搜索结果
    private var cachedAlbums: List<Album>? = null
    private var lastSearchArtist: String = ""
    private var lastPage: Int = 1
    private var totalPages: Int = 1
    
    /**
     * 首次搜索专辑
     */
    suspend fun searchAlbums(artist: String): Pair<List<Album>, Int> {
        val result = apiService.firstSearch(artist)
        
        // 缓存结果
        cachedAlbums = result.first
        lastSearchArtist = artist
        lastPage = 1
        totalPages = result.second
        
        return result
    }
    
    /**
     * 获取指定页的专辑
     */
    suspend fun getAlbumsByPage(artist: String, page: Int): List<Album> {
        // 如果请求相同页，直接返回缓存
        if (artist == lastSearchArtist && page == lastPage && cachedAlbums != null) {
            return cachedAlbums!!
        }
        
        val albums = apiService.getAlbumsByPage(artist, page)
        
        // 更新缓存
        cachedAlbums = albums
        lastSearchArtist = artist
        lastPage = page
        
        return albums
    }
    
    /**
     * 获取总页数
     */
    fun getTotalPages() = totalPages
    
    /**
     * 获取当前页
     */
    fun getCurrentPage() = lastPage
    
    /**
     * 获取最后搜索的艺术家
     */
    fun getLastSearchArtist() = lastSearchArtist

    /**
     * 获取指定专辑URL的文件信息列表。
     * 使用 Result 包装器来处理成功或失败的情况。
     */
    suspend fun getFileInfo(albumUrl: String): Result<List<FileInfo>> = withContext(Dispatchers.IO) {
        try {
            // 调用 ApiService 中的方法
            val fileInfoList = apiService.getFileInfo(albumUrl)
            // 如果 API 调用本身没有抛出异常，则认为是成功的
            Result.success(fileInfoList)
        } catch (e: IOException) {
            // 网络或其他 IO 错误
            println("Repository Error fetching file info: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            // 其他潜在错误 (例如解析错误，虽然 ApiService 内部可能已处理)
            println("Repository Unexpected error fetching file info: ${e.message}")
            Result.failure(e)
        }
    }
}