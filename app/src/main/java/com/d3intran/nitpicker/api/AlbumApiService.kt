package com.d3intran.nitpicker.api

import com.d3intran.nitpicker.model.Album
import com.d3intran.nitpicker.model.FileInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 专辑API服务 (已脱敏)
 * 使用安全的Mock数据，替换了原有的爬虫逻辑
 */
@Singleton
class AlbumApiService @Inject constructor() {
    // 保存最后一次搜索的艺术家
    private var lastSearchArtist = ""

    /**
     * 首次搜索专辑，返回专辑列表和总页数
     */
    suspend fun firstSearch(artist: String): Pair<List<Album>, Int> = withContext(Dispatchers.IO) {
        lastSearchArtist = artist
        delay(500) // Simulate network delay
        val maxPage = 2
        Pair(getMockAlbums(), maxPage)
    }

    /**
     * 获取指定页的专辑
     */
    suspend fun getAlbumsByPage(artist: String, page: Int): List<Album> = withContext(Dispatchers.IO) {
        delay(500)
        getMockAlbums()
    }

    /**
     * 获取指定专辑URL的文件信息列表
     */
    suspend fun getFileInfo(albumUrl: String): List<FileInfo> = withContext(Dispatchers.IO) {
        delay(500)
        val files = mutableListOf<FileInfo>()
        
        // 视频 1
        files.add(
            FileInfo(
                fileName = "Big Buck Bunny.mp4",
                fileType = "mp4",
                fileSize = "158 MB",
                thumbnailUrl = "https://peach.blender.org/wp-content/uploads/title_anouncement.jpg",
                pageUrl = "mock_page_bunny"
            )
        )
        // 视频 2
        files.add(
            FileInfo(
                fileName = "Elephants Dream.mp4",
                fileType = "mp4",
                fileSize = "425 MB",
                thumbnailUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e8/Elephants_Dream_s5_proog.jpg/800px-Elephants_Dream_s5_proog.jpg",
                pageUrl = "mock_page_elephant"
            )
        )
        // 图片 1
        files.add(
            FileInfo(
                fileName = "Sample Scenery 1.jpg",
                fileType = "jpg",
                fileSize = "1.2 MB",
                thumbnailUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b6/Image_created_with_a_mobile_phone.png/800px-Image_created_with_a_mobile_phone.png",
                pageUrl = "mock_page_image1"
            )
        )
        // 图片 2
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

    fun getLastSearchArtist(): String = lastSearchArtist

    private fun getMockAlbums(): List<Album> {
        val albums = mutableListOf<Album>()
        for (i in 1..20) {
            albums.add(
                Album(
                    title = "Open Source Test Media $i",
                    file = 4, // Matches our 4 mock files
                    url = "mock_album_url_$i"
                )
            )
        }
        return albums
    }
}