package com.d3intran.nitpicker.api

import com.d3intran.nitpicker.model.Album
import com.d3intran.nitpicker.model.FileInfo

/**
 * 媒体资源 API 的抽象接口。
 *
 * 定义了从远程数据源（搜索引擎、素材平台等）获取媒体资源的统一契约。
 * 通过接口抽象，可以灵活切换不同的数据源实现：
 *
 * - [MockMediaApiService]：Mock 数据，用于开发调试
 * - [PexelsApiService]：对接 Pexels 公开素材库 REST API
 *
 * 所有方法均为 suspend 函数，保证在 IO 协程中安全调用。
 */
interface MediaApiService {

    /**
     * 首次搜索，返回首页结果和总页数。
     *
     * @param query 搜索关键词
     * @return Pair<搜索结果列表, 总页数>
     */
    suspend fun search(query: String): Pair<List<Album>, Int>

    /**
     * 获取指定页的搜索结果（翻页）。
     *
     * @param query 搜索关键词（需与首次搜索一致）
     * @param page 目标页码（从 1 开始）
     * @return 该页的结果列表
     */
    suspend fun getResultsByPage(query: String, page: Int): List<Album>

    /**
     * 获取指定合集/专辑的文件详情列表。
     *
     * @param albumUrl 合集的访问地址
     * @return 该合集包含的所有媒体文件信息
     */
    suspend fun getFileInfo(albumUrl: String): List<FileInfo>

    /** 获取最近一次搜索的关键词，用于状态恢复 */
    fun getLastSearchQuery(): String
}
