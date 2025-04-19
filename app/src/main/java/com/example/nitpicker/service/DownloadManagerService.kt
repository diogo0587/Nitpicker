package com.example.nitpicker.service

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.nitpicker.model.DownloadFileInfo
import com.example.nitpicker.model.DownloadProgress
import com.example.nitpicker.model.DownloadStatus
import com.example.nitpicker.model.FileInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class DownloadManagerService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient // Inject OkHttpClient (configure in Hilt module if needed)
) {
    private val _downloadState = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloadState: StateFlow<Map<String, DownloadProgress>> = _downloadState.asStateFlow()

    // Use a limited dispatcher for downloads to control concurrency easily
    private val imageDownloadDispatcher = Executors.newFixedThreadPool(5).asCoroutineDispatcher()
    private val otherDownloadDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher() // Serial for others

    private val jobMap = ConcurrentHashMap<String, Job>() // To manage cancellation

    // --- Public API ---

    fun enqueueDownloads(files: List<FileInfo>, albumTitle: String) {
        Log.d("DownloadService", "Enqueueing ${files.size} files for album: $albumTitle")
        // Don't block the caller, launch fetching and downloading
        CoroutineScope(Dispatchers.IO).launch {
            val downloadFileInfos = fetchDownloadUrls(files, albumTitle)

            val (images, others) = downloadFileInfos.partition { isImage(it.fileType) }

            // Add all to state as Pending first
            val initialProgressList = downloadFileInfos.map {
                DownloadProgress(id = it.id, fileName = it.fileName, albumTitle = it.albumTitle)
            }
            updateProgressBatch(initialProgressList) { it } // <-- 修改这里：提供一个返回自身的 lambda

            // Launch downloads concurrently for images, serially for others
            launchDownloads(images, albumTitle, imageDownloadDispatcher)
            launchDownloads(others, albumTitle, otherDownloadDispatcher)
        }
    }

    fun cancelDownload(downloadId: String) {
        jobMap[downloadId]?.cancel() // Cancel the coroutine
        updateProgress(downloadId) { it.copy(status = DownloadStatus.Cancelled) }
        jobMap.remove(downloadId)
        Log.d("DownloadService", "Cancelled download: $downloadId")
        // Consider deleting partially downloaded file here if needed
    }

    // --- Internal Logic ---

    private suspend fun fetchDownloadUrls(files: List<FileInfo>, albumTitle: String): List<DownloadFileInfo> {
        return coroutineScope {
            files.map { fileInfo ->
                async { // Fetch URLs concurrently
                    Log.d("DownloadService", "Fetching URL for: ${fileInfo.fileName}")
                    val tempId = "temp_${fileInfo.pageUrl}" // Temporary ID for FetchingUrl status
                    updateProgress(tempId) { // Show fetching status
                        DownloadProgress(
                            id = tempId,
                            fileName = fileInfo.fileName,
                            albumTitle = albumTitle,
                            status = DownloadStatus.FetchingUrl
                        )
                    }
                    try {
                        val downloadInfo = getSingleDownloadFileInfo(fileInfo, albumTitle)
                        removeProgress(tempId) // Remove temporary entry
                        Log.d("DownloadService", "Successfully fetched URL for: ${downloadInfo.fileName}")
                        downloadInfo // Return successful info
                    } catch (e: Exception) {
                        Log.e("DownloadService", "Failed to fetch URL for ${fileInfo.fileName}: ${e.message}", e)
                        removeProgress(tempId) // Remove temporary entry
                        // Create an error progress state for this file
                        val errorId = "error_${fileInfo.pageUrl}"
                        updateProgress(errorId) {
                            DownloadProgress(
                                id = errorId,
                                fileName = fileInfo.fileName,
                                albumTitle = albumTitle,
                                status = DownloadStatus.Error,
                                error = "Failed to get download URL: ${e.message}"
                            )
                        }
                        null // Indicate failure for this file
                    }
                }
            }.awaitAll().filterNotNull() // Wait for all fetches and filter out nulls (errors)
        }
    }

    // Mirrors the Rust logic for getting DownloadFile
    private suspend fun getSingleDownloadFileInfo(fileInfo: FileInfo, albumTitle: String): DownloadFileInfo {
        val pageUrl = fileInfo.pageUrl
        val request = Request.Builder().url(pageUrl).build() // Add headers if needed from create_header logic

        val response: Response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("Failed to fetch page $pageUrl: ${response.code}")

        val html = response.body?.string() ?: throw IOException("Empty body for $pageUrl")
        val document = Jsoup.parse(html, pageUrl) // Provide base URI for relative URLs

        // Regex to find the intermediate download page URL (Referer)
        val downloadPageUrlPattern = Pattern.compile("""href="(https://get\.bunkrr\.su/file/\d+)"""")
        val matcher = downloadPageUrlPattern.matcher(html)
        val downloadPageUrl = if (matcher.find()) {
            matcher.group(1) ?: throw IOException("Cannot find download page URL in $pageUrl")
        } else {
            // Fallback or alternative selectors if needed
            throw IOException("Cannot find download page URL pattern in $pageUrl")
        }

        // Logic to construct the final file URL (mirrors Rust)
        val fileType = fileInfo.fileType
        val thumbnailUrl = fileInfo.thumbnailUrl
        val fileName = fileInfo.fileName

        val id = thumbnailUrl.substringAfterLast('/').substringBefore('.')
        val domainMatch = Regex("^(https?://[^/]+)").find(thumbnailUrl)
        val baseDomain = domainMatch?.groups?.get(1)?.value ?: throw IOException("Cannot extract domain from thumbnail $thumbnailUrl")

        val fileUrl = when {
            isImage(fileType) -> "$baseDomain/$id.$fileType?n=${encodeUrl(fileName)}"
            baseDomain.contains("burger") -> "https://brg-bk.cdn.gigachad-cdn.ru/$id.$fileType?n=${encodeUrl(fileName)}"
            baseDomain.contains("milkshake") -> "https://mlk-bk.cdn.gigachad-cdn.ru/$id.$fileType?n=${encodeUrl(fileName)}"
            else -> "${baseDomain.replace("i-", "")}/$id.$fileType?n=${encodeUrl(fileName)}"
        }

        return DownloadFileInfo(
            fileName = fileName,
            fileType = fileType,
            downloadPageUrl = downloadPageUrl,
            fileUrl = fileUrl,
            albumTitle = albumTitle
        )
    }

    private fun launchDownloads(
        downloads: List<DownloadFileInfo>,
        albumTitle: String,
        dispatcher: CoroutineDispatcher
    ) {
        downloads.forEach { downloadInfo ->
            val job = CoroutineScope(dispatcher).launch {
                performDownload(downloadInfo)
            }
            jobMap[downloadInfo.id] = job // Store job for cancellation
            job.invokeOnCompletion {
                if (it is CancellationException) {
                    Log.d("DownloadService", "Job ${downloadInfo.id} cancelled.")
                    // Status already set by cancelDownload
                }
                jobMap.remove(downloadInfo.id) // Clean up completed/cancelled job
            }
        }
    }

    private suspend fun performDownload(downloadInfo: DownloadFileInfo) {
        val downloadId = downloadInfo.id
        Log.d("DownloadService", "Starting download for ID: $downloadId, File: ${downloadInfo.fileName}")
        updateProgress(downloadId) { it.copy(status = DownloadStatus.Downloading) }

        var file: File? = null
        var outputStream: FileOutputStream? = null
        var response: Response? = null

        try {
            // 1. Prepare File Path
            file = prepareFilePath(downloadInfo.albumTitle, downloadInfo.fileName)
            Log.d("DownloadService", "Saving to: ${file.absolutePath}")

            // 2. Create Request with Referer
            val request = Request.Builder()
                .url(downloadInfo.fileUrl)
                .header("Referer", downloadInfo.downloadPageUrl) // Crucial header
                // Add other headers if necessary
                .build()

            // 3. Execute Request
            response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                throw IOException("Download failed: HTTP ${response.code} for ${downloadInfo.fileUrl}")
            }

            val body = response.body ?: throw IOException("Response body is null")
            val totalBytes = body.contentLength()
            updateProgress(downloadId) { it.copy(totalBytes = totalBytes) }

            // 4. Stream to File and Report Progress
            outputStream = FileOutputStream(file)
            val buffer = ByteArray(8 * 1024) // 8KB buffer
            var bytesCopied = 0L
            var bytes = body.byteStream().read(buffer)
            var lastProgressEmitTime = System.currentTimeMillis()

            while (bytes >= 0) {
                currentCoroutineContext().ensureActive()
                outputStream.write(buffer, 0, bytes)
                bytesCopied += bytes

                val currentTime = System.currentTimeMillis()
                // Throttle progress updates (e.g., every 300ms or significant change)
                if (currentTime - lastProgressEmitTime > 300 || bytesCopied == totalBytes) {
                    val progressPercent = if (totalBytes > 0) ((bytesCopied * 100) / totalBytes).toInt() else 0
                    updateProgress(downloadId) {
                        it.copy(downloadedBytes = bytesCopied, progressPercent = progressPercent)
                    }
                    lastProgressEmitTime = currentTime
                }

                bytes = body.byteStream().read(buffer)
            }

            // 5. Finalize
            outputStream.flush()
            Log.d("DownloadService", "Download completed: ${downloadInfo.fileName}")
            updateProgress(downloadId) {
                it.copy(
                    status = DownloadStatus.Completed,
                    progressPercent = 100,
                    downloadedBytes = totalBytes, // Ensure final bytes match total
                    filePath = file.absolutePath
                )
            }

        } catch (e: CancellationException) {
            Log.d("DownloadService", "Download cancelled: ${downloadInfo.fileName}")
            updateProgress(downloadId) { it.copy(status = DownloadStatus.Cancelled) }
            // Delete partial file on cancellation
            file?.delete()
        } catch (e: Exception) {
            Log.e("DownloadService", "Download error for ${downloadInfo.fileName}: ${e.message}", e)
            updateProgress(downloadId) { it.copy(status = DownloadStatus.Error, error = e.message ?: "Unknown download error") }
            // Optionally delete partial file on error
            file?.delete()
        } finally {
            // 6. Close resources
            try {
                outputStream?.close()
            } catch (e: IOException) {
                Log.e("DownloadService", "Error closing output stream: ${e.message}")
            }
            try {
                response?.body?.close() // Close response body
            } catch (e: Exception) {
                 Log.e("DownloadService", "Error closing response body: ${e.message}")
            }
        }
    }

    // --- Helper Functions ---

    private suspend fun prepareFilePath(albumTitle: String, fileName: String): File {
        // Use app-specific external files directory (no special permissions needed usually)
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir // Fallback to internal storage if external is unavailable
        val sanitizedAlbumTitle = sanitizeFilename(albumTitle)
        val albumDir = File(baseDir, sanitizedAlbumTitle)

        // Create directories if they don't exist (IO operation)
        withContext(Dispatchers.IO) {
            if (!albumDir.exists()) {
                if (albumDir.mkdirs()) {
                    Log.d("DownloadService", "Created directory: ${albumDir.absolutePath}")
                } else {
                    Log.e("DownloadService", "Failed to create directory: ${albumDir.absolutePath}")
                    // Fallback to base directory if album dir creation fails
                    // return@withContext File(baseDir, sanitizeFilename(fileName)) // Option 1
                    throw IOException("Failed to create album directory: ${albumDir.absolutePath}") // Option 2: Fail download
                }
            }
        }

        // Handle filename conflicts (similar to Rust code)
        val sanitizedFileName = sanitizeFilename(fileName)
        var finalFile = File(albumDir, sanitizedFileName)
        var counter = 1
        val nameWithoutExt = finalFile.nameWithoutExtension
        val extension = finalFile.extension

        while (withContext(Dispatchers.IO) { finalFile.exists() }) {
            val newName = if (extension.isNotEmpty()) {
                "$nameWithoutExt($counter).$extension"
            } else {
                "$nameWithoutExt($counter)"
            }
            finalFile = File(albumDir, newName)
            counter++
        }
        return finalFile
    }

    // Basic filename sanitizer (replace invalid chars)
    private fun sanitizeFilename(name: String): String {
        // Replace characters invalid for typical filesystems
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }

    private fun isImage(fileType: String): Boolean {
        return fileType.lowercase() in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "avif", "tiff", "svg", "ico")
    }

    // Basic URL encoding helper
    private fun encodeUrl(value: String): String {
        return java.net.URLEncoder.encode(value, "UTF-8")
    }

    // --- State Update Helpers ---

    private fun updateProgress(id: String, update: (DownloadProgress) -> DownloadProgress) {
        _downloadState.update { currentMap ->
            val currentProgress = currentMap[id] ?: DownloadProgress(id, "Unknown", "Unknown") // Should ideally not happen if initialized properly
            currentMap + (id to update(currentProgress))
        }
    }

    private fun updateProgressBatch(progressList: List<DownloadProgress>, update: (DownloadProgress) -> DownloadProgress) {
        _downloadState.update { currentMap ->
            val newMap = currentMap.toMutableMap()
            progressList.forEach { progress ->
                 val currentProgress = currentMap[progress.id] ?: progress // Use provided progress if not existing
                 newMap[progress.id] = update(currentProgress)
            }
            newMap
        }
    }

     private fun removeProgress(id: String) {
        _downloadState.update { currentMap ->
            currentMap - id
        }
    }

    // Consider adding methods for pausing, resuming, retrying later
}