package com.d3intran.nitpicker.api

import android.util.Log
import com.d3intran.nitpicker.model.Album
import com.d3intran.nitpicker.model.FileInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [MediaApiService] 的 Pexels 实现。
 *
 * 对接 [Pexels REST API](https://www.pexels.com/api/documentation/)，
 * 为用户提供高质量的免费图片和视频素材搜索与下载。
 *
 * Pexels API 免费配额：200 请求/小时，完全够用。
 * 使用前需要在 [Pexels 开发者页面](https://www.pexels.com/api/) 申请 API Key。
 *
 * @property okHttpClient 共享的 OkHttp 客户端（由 Hilt 注入）
 * @property apiKey Pexels API 密钥（后续应从 BuildConfig 或 DataStore 读取）
 */
@Singleton
class PexelsApiService @Inject constructor(
    private val okHttpClient: OkHttpClient
) : MediaApiService {

    companion object {
        private const val TAG = "PexelsApiService"
        private const val BASE_URL = "https://api.pexels.com/v1"
        private const val VIDEO_BASE_URL = "https://api.pexels.com/videos"
        private const val PER_PAGE = 20
    }

    // TODO: 从 BuildConfig 或加密存储中读取
    private var apiKey: String = ""
    private var lastSearchQuery = ""

    /**
     * 设置 Pexels API Key。
     * 应该在应用启动时从安全存储中读取并设置。
     */
    fun setApiKey(key: String) {
        apiKey = key
    }

    override suspend fun search(query: String): Pair<List<Album>, Int> = withContext(Dispatchers.IO) {
        lastSearchQuery = query

        // 同时搜索图片和视频，合并结果
        val photoAlbums = searchPhotos(query, page = 1)
        val videoAlbums = searchVideos(query, page = 1)

        val combined = photoAlbums.first + videoAlbums.first
        val maxPages = maxOf(photoAlbums.second, videoAlbums.second)

        Pair(combined, maxPages)
    }

    override suspend fun getResultsByPage(query: String, page: Int): List<Album> = withContext(Dispatchers.IO) {
        val photos = searchPhotos(query, page)
        val videos = searchVideos(query, page)
        photos.first + videos.first
    }

    override suspend fun getFileInfo(albumUrl: String): List<FileInfo> = withContext(Dispatchers.IO) {
        // 在 Pexels 中，每个"Album"实际上就是单个资源
        // albumUrl 格式: "pexels_photo_{id}" 或 "pexels_video_{id}"
        when {
            albumUrl.startsWith("pexels_photo_") -> {
                val id = albumUrl.removePrefix("pexels_photo_")
                getPhotoFileInfo(id)
            }
            albumUrl.startsWith("pexels_video_") -> {
                val id = albumUrl.removePrefix("pexels_video_")
                getVideoFileInfo(id)
            }
            else -> emptyList()
        }
    }

    override fun getLastSearchQuery(): String = lastSearchQuery

    // ==================== 私有方法 ====================

    /**
     * 搜索 Pexels 图片。
     * API 文档：https://www.pexels.com/api/documentation/#photos-search
     */
    private fun searchPhotos(query: String, page: Int): Pair<List<Album>, Int> {
        val url = "$BASE_URL/search?query=$query&page=$page&per_page=$PER_PAGE"
        val response = executeAuthenticatedRequest(url) ?: return Pair(emptyList(), 0)

        val json = JSONObject(response)
        val totalResults = json.optInt("total_results", 0)
        val totalPages = (totalResults + PER_PAGE - 1) / PER_PAGE

        val photos = json.getJSONArray("photos")
        val albums = (0 until photos.length()).map { i ->
            val photo = photos.getJSONObject(i)
            Album(
                title = photo.optString("alt", "Pexels Photo ${photo.getInt("id")}"),
                fileCount = 1,
                url = "pexels_photo_${photo.getInt("id")}"
            )
        }

        return Pair(albums, totalPages)
    }

    /**
     * 搜索 Pexels 视频。
     * API 文档：https://www.pexels.com/api/documentation/#videos-search
     */
    private fun searchVideos(query: String, page: Int): Pair<List<Album>, Int> {
        val url = "$VIDEO_BASE_URL/search?query=$query&page=$page&per_page=$PER_PAGE"
        val response = executeAuthenticatedRequest(url) ?: return Pair(emptyList(), 0)

        val json = JSONObject(response)
        val totalResults = json.optInt("total_results", 0)
        val totalPages = (totalResults + PER_PAGE - 1) / PER_PAGE

        val videos = json.getJSONArray("videos")
        val albums = (0 until videos.length()).map { i ->
            val video = videos.getJSONObject(i)
            val duration = video.optInt("duration", 0)
            Album(
                title = "Pexels Video ${video.getInt("id")} (${duration}s)",
                fileCount = 1,
                url = "pexels_video_${video.getInt("id")}"
            )
        }

        return Pair(albums, totalPages)
    }

    /** 获取单张 Pexels 图片的不同尺寸下载链接 */
    private fun getPhotoFileInfo(photoId: String): List<FileInfo> {
        val url = "$BASE_URL/photos/$photoId"
        val response = executeAuthenticatedRequest(url) ?: return emptyList()

        val json = JSONObject(response)
        val src = json.getJSONObject("src")
        val alt = json.optString("alt", "Photo $photoId")

        // 提供多个尺寸选择
        return listOf(
            FileInfo(
                fileName = "${alt}_original.jpg",
                fileType = "jpg",
                fileSize = "Original",
                thumbnailUrl = src.getString("medium"),
                pageUrl = src.getString("original")
            ),
            FileInfo(
                fileName = "${alt}_large.jpg",
                fileType = "jpg",
                fileSize = "Large (1880px)",
                thumbnailUrl = src.getString("medium"),
                pageUrl = src.getString("large")
            )
        )
    }

    /** 获取单个 Pexels 视频的不同质量下载链接 */
    private fun getVideoFileInfo(videoId: String): List<FileInfo> {
        val url = "$VIDEO_BASE_URL/videos/$videoId"
        val response = executeAuthenticatedRequest(url) ?: return emptyList()

        val json = JSONObject(response)
        val videoFiles = json.getJSONArray("video_files")
        val image = json.getString("image") // 视频封面图

        return (0 until videoFiles.length()).map { i ->
            val file = videoFiles.getJSONObject(i)
            val quality = file.optString("quality", "unknown")
            val width = file.optInt("width", 0)
            val height = file.optInt("height", 0)
            val fileType = file.optString("file_type", "video/mp4")
                .substringAfter("/")

            FileInfo(
                fileName = "pexels_video_${videoId}_${quality}_${width}x${height}.$fileType",
                fileType = fileType,
                fileSize = "${width}x${height} $quality",
                thumbnailUrl = image,
                pageUrl = file.getString("link")
            )
        }
    }

    /**
     * 执行带认证的 API 请求。
     *
     * Pexels API 使用 Authorization Header 认证。
     * @return 响应 body 字符串，失败时返回 null
     */
    private fun executeAuthenticatedRequest(url: String): String? {
        if (apiKey.isBlank()) {
            Log.e(TAG, "Pexels API Key 未设置！请调用 setApiKey() 配置。")
            return null
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", apiKey)
            .build()

        return try {
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()
            } else {
                Log.e(TAG, "Pexels API 请求失败: HTTP ${response.code} for $url")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pexels API 请求异常: ${e.message}", e)
            null
        }
    }
}
