package com.d3intran.nitpicker.api

import com.d3intran.nitpicker.model.Album
import com.d3intran.nitpicker.model.FileInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 专辑API服务
 */
@Singleton
class AlbumApiService @Inject constructor(
    private val client: OkHttpClient
) {
    // 保存最后一次搜索的艺术家
    private var lastSearchArtist = ""

    /**
     * 首次搜索专辑，返回专辑列表和总页数
     * throws IOException if network fails or response is not successful
     */
    suspend fun firstSearch(artist: String): Pair<List<Album>, Int> = withContext(Dispatchers.IO) {
        lastSearchArtist = artist
        val url = "https://bunkr-albums.io/?search=$artist"
        val request = createRequest(url)

        val response = client.newCall(request).execute() // Let this throw IOException
        if (!response.isSuccessful) {
            // Throw exception with status code for better error info in ViewModel
            throw IOException("Request failed with status: ${response.code} ${response.message}")
        }
        val body = response.body?.string() ?: throw IOException("Empty response body")
        val document = Jsoup.parse(body)
        val albums = extractAlbums(document)
        val maxPage = extractMaxPage(document)
        Pair(albums, maxPage)
    }

    /**
     * 获取指定页的专辑
     * throws IOException if network fails or response is not successful
     */
    suspend fun getAlbumsByPage(artist: String, page: Int): List<Album> = withContext(Dispatchers.IO) {
        val url = "https://bunkr-albums.io/?search=$artist&page=$page"
        val request = createRequest(url)

        val response = client.newCall(request).execute() // Let this throw IOException
        if (!response.isSuccessful) {
            // Throw exception with status code
            throw IOException("Request failed for page $page with status: ${response.code} ${response.message}")
        }
        val body = response.body?.string() ?: throw IOException("Empty response body for page $page")
        val document = Jsoup.parse(body)
        extractAlbums(document)
    }

    /**
     * 获取指定专辑URL的文件信息列表
     * (Keeping try-catch here for now, but ideally should also propagate errors)
     */
    suspend fun getFileInfo(albumUrl: String): List<FileInfo> = withContext(Dispatchers.IO) {
        val fullUrl = if (albumUrl.startsWith("http")) albumUrl else "https://bunkr-albums.io$albumUrl"
        println("Fetching file info from: $fullUrl")
        val request = createRequest(fullUrl)
        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                // Consider throwing here too for consistency
                println("Request failed for file info with status: ${response.code}")
                return@withContext emptyList()
            }
            val body = response.body?.string() ?: throw IOException("Empty response body for file info")
            val document = Jsoup.parse(body)
            extractFileInfo(document)
        } catch (e: IOException) {
            println("Error during getFileInfo: ${e.message}")
            emptyList()
        } catch (e: Exception) {
            println("Parsing error during getFileInfo: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    fun getLastSearchArtist(): String = lastSearchArtist

    private fun createRequest(url: String): Request {
        return Request.Builder()
            .url(url)
            .build()
    }

    private fun extractAlbums(document: Document): List<Album> {
        val albums = mutableListOf<Album>()
        val items = document.select("div.rounded-xl.bg-mute")
            .ifEmpty { document.select("div[data-repeat='0']") }

        println("Found ${items.size} potential album items using primary/fallback selectors.")

        for (element in items) {
            try {
                val titleElement = element.select("p.text-subs span.truncate").first()
                val title = titleElement?.text()?.trim() ?: ""

                val filesElement = element.select("p.text-xs span.font-semibold").first()
                val filesText = filesElement?.text()?.trim() ?: "0"
                val files = filesText.split(" ").firstOrNull()?.toIntOrNull() ?: 0

                val urlElement = element.select("a[href]").first()
                val url = urlElement?.attr("href") ?: ""

                if (title.isNotEmpty() && url.isNotEmpty()) {
                    albums.add(Album(title = title, file = files, url = url))
                } else {
                    println("Skipping album item due to missing title or URL. Title: '$title', URL: '$url'")
                    println("HTML snippet for skipped item: ${element.outerHtml().take(200)}...")
                }
            } catch (e: Exception) {
                println("Error parsing album item: ${e.message}")
                println("HTML snippet for error item: ${element.outerHtml().take(200)}...")
            }
        }
        println("Extracted ${albums.size} albums.")
        return albums
    }

    private fun extractMaxPage(document: Document): Int {
        val pagination = document.select("div.flex.gap-2[class*=\"lg:order-last\"]")
            .ifEmpty { document.select("nav div.flex.gap-2") }
            .ifEmpty { document.select("div.flex.gap-2") }
            .firstOrNull()

        var maxPage = 1
        pagination?.let { nav ->
            val pageLinks = nav.select("a.btn")
            for (link in pageLinks) {
                link.text()?.trim()?.toIntOrNull()?.let { pageNum ->
                    if (pageNum > maxPage) maxPage = pageNum
                }
                val href = link.attr("href")
                if (href.contains("page=")) {
                    href.split("page=").getOrNull(1)?.split("&")?.firstOrNull()?.toIntOrNull()?.let { pageNum ->
                        if (pageNum > maxPage) maxPage = pageNum
                    }
                }
            }
        }
        println("Extracted max page: $maxPage")
        return if (maxPage <= 0) 1 else maxPage
    }

    private fun extractFileInfo(document: Document): List<FileInfo> {
        val fileInfos = mutableListOf<FileInfo>()
        val itemSelector = "div[class*='relative group/item']"

        val items = document.select(itemSelector)
        println("Found ${items.size} potential file items using selector: '$itemSelector'")

        if (items.isEmpty()) {
            println("Warning: No file items found. Check selector and HTML structure.")
            println("HTML Body Snippet: ${document.body()?.html()?.take(500)}...")
        }

        for (element in items) {
            try {
                val fileNameElement = element.select("p[style*=\"display:none\"]").first()
                val fileName = fileNameElement?.text()?.trim() ?: "Unknown File"

                val thumbElement = element.select("img.grid-images_box-img").first()
                val thumbnailUrl = thumbElement?.attr("src") ?: ""

                val sizeElement = element.select("p.text-xs.theSize").first()
                val fileSize = sizeElement?.text()?.trim() ?: "0 B"

                val linkElement = element.select("a[class*=\"after:absolute\"]").first()
                val pagePath = linkElement?.attr("href") ?: ""

                val fileType = fileName.substringAfterLast('.', "")

                val pageUrl = if (pagePath.startsWith("http")) pagePath else "https://bunkr.cr$pagePath"

                if (fileName != "Unknown File" && thumbnailUrl.isNotEmpty() && pageUrl.isNotEmpty()) {
                    fileInfos.add(
                        FileInfo(
                            fileName = fileName,
                            fileType = fileType,
                            fileSize = fileSize,
                            thumbnailUrl = thumbnailUrl,
                            pageUrl = pageUrl
                        )
                    )
                } else {
                    println("Skipping file item due to missing data. Name: '$fileName', Thumb: '$thumbnailUrl', PageURL: '$pageUrl'")
                    println("HTML snippet for skipped file: ${element.outerHtml().take(200)}...")
                }
            } catch (e: Exception) {
                println("Error parsing file item: ${e.message}")
                println("HTML snippet for error file: ${element.outerHtml().take(200)}...")
            }
        }
        println("Extracted ${fileInfos.size} file infos.")
        return fileInfos
    }
}