package com.d3intran.nitpicker.api

import com.d3intran.nitpicker.model.Album
import com.d3intran.nitpicker.model.FileInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [MediaApiService] 的 Mock 实现，用于开发和调试。
 *
 * 提供模拟的搜索结果和文件信息（使用公开的 Creative Commons 媒体资源），
 * 通过 [delay] 模拟网络延迟，使 UI 层的加载/错误处理逻辑可以正常验证。
 *
 * 在生产环境中，应通过 DI 替换为 [PexelsApiService] 等真实实现。
 */
@Singleton
class MockMediaApiService @Inject constructor() : MediaApiService {

    private var lastSearchQuery = ""

    override suspend fun search(query: String): Pair<List<Album>, Int> = withContext(Dispatchers.IO) {
        lastSearchQuery = query
        delay(500) // 模拟网络延迟
        val maxPage = 2
        Pair(getMockAlbums(query), maxPage)
    }

    override suspend fun getResultsByPage(query: String, page: Int): List<Album> = withContext(Dispatchers.IO) {
        delay(500)
        getMockAlbums(query)
    }

    override suspend fun getFileInfo(albumUrl: String): List<FileInfo> = withContext(Dispatchers.IO) {
        delay(500)
        val files = mutableListOf<FileInfo>()

        // 使用公开的 Creative Commons 视频和图片作为测试数据
        files.add(
            FileInfo(
                fileName = "Big Buck Bunny.mp4",
                fileType = "mp4",
                fileSize = "158 MB",
                thumbnailUrl = "https://peach.blender.org/wp-content/uploads/title_anouncement.jpg",
                pageUrl = "mock_page_bunny"
            )
        )
        files.add(
            FileInfo(
                fileName = "Elephants Dream.mp4",
                fileType = "mp4",
                fileSize = "425 MB",
                thumbnailUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e8/Elephants_Dream_s5_proog.jpg/800px-Elephants_Dream_s5_proog.jpg",
                pageUrl = "mock_page_elephant"
            )
        )
        files.add(
            FileInfo(
                fileName = "Sample Scenery 1.jpg",
                fileType = "jpg",
                fileSize = "1.2 MB",
                thumbnailUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b6/Image_created_with_a_mobile_phone.png/800px-Image_created_with_a_mobile_phone.png",
                pageUrl = "mock_page_image1"
            )
        )
        files.add(
            FileInfo(
                fileName = "Sample Scenery 2.jpg",
                fileType = "jpg",
                fileSize = "2.1 MB",
                thumbnailUrl = "https://upload.wikimedia.org/wikipedia/commons/3/3a/Cat03.jpg",
                pageUrl = "mock_page_image2"
            )
        )

        files
    }

    override fun getLastSearchQuery(): String = lastSearchQuery

    private fun getMockAlbums(query: String): List<Album> {
        return (1..20).map { i ->
            Album(
                title = "$query - Open Source Media $i",
                fileCount = 4,
                url = "mock_album_url_$i"
            )
        }
    }
}
