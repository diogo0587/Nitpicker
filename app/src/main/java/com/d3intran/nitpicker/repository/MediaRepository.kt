package com.d3intran.nitpicker.repository

import com.d3intran.nitpicker.api.MediaApiService
import com.d3intran.nitpicker.model.Album
import com.d3intran.nitpicker.model.FileInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 媒体资源仓库，管理在线素材的搜索、分页和缓存。
 *
 * 作为 ViewModel 与 [MediaApiService] 之间的中间层：
 * - 封装不同数据源（Mock / Pexels / Unsplash）的差异
 * - 提供内存级缓存，避免重复请求
 * - 统一错误处理，将网络异常包装为 [Result]
 *
 * @property apiService 具体的数据源实现（由 Hilt 注入）
 */
@Singleton
class MediaRepository @Inject constructor(
    private val apiService: MediaApiService
) {

    /** 缓存最近一次搜索结果 */
    private var cachedAlbums: List<Album>? = null
    private var lastSearchQuery: String = ""
    private var lastPage: Int = 1
    private var totalPages: Int = 1

    /**
     * 首次搜索素材。
     *
     * 清空旧的缓存并发起新搜索。搜索结果会被缓存。
     *
     * @param query 搜索关键词
     * @return Pair<搜索结果, 总页数>
     * @throws IOException 网络异常
     */
    suspend fun searchAlbums(query: String): Pair<List<Album>, Int> {
        val result = apiService.search(query)

        cachedAlbums = result.first
        lastSearchQuery = query
        lastPage = 1
        totalPages = result.second

        return result
    }

    /**
     * 加载指定页的搜索结果（分页）。
     *
     * 如果请求的是已缓存的同一页，直接返回缓存结果。
     *
     * @param query 搜索关键词
     * @param page 页码（从 1 开始）
     * @return 该页的搜索结果列表
     * @throws IOException 网络异常
     */
    suspend fun getAlbumsByPage(query: String, page: Int): List<Album> {
        // 命中缓存：相同关键词 + 相同页码
        if (query == lastSearchQuery && page == lastPage && cachedAlbums != null) {
            return cachedAlbums!!
        }

        val albums = apiService.getResultsByPage(query, page)

        cachedAlbums = albums
        lastSearchQuery = query
        lastPage = page

        return albums
    }

    /** 获取当前搜索的总页数 */
    fun getTotalPages() = totalPages

    /** 获取当前所在页码 */
    fun getCurrentPage() = lastPage

    /** 获取最近一次搜索的关键词（用于翻页时复用） */
    fun getLastSearchQuery() = lastSearchQuery

    /**
     * 获取指定合集的文件详情列表。
     *
     * 使用 [Result] 包装返回值，让上层决定如何处理成功/失败。
     *
     * @param albumUrl 合集的 URL 标识
     * @return 成功时返回文件列表，失败时返回异常
     */
    suspend fun getFileInfo(albumUrl: String): Result<List<FileInfo>> = withContext(Dispatchers.IO) {
        try {
            val fileInfoList = apiService.getFileInfo(albumUrl)
            Result.success(fileInfoList)
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
