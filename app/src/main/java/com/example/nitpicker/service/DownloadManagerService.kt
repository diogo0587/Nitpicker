package com.example.nitpicker.service

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.nitpicker.model.DownloadFileInfo
import com.example.nitpicker.model.DownloadProgress
import com.example.nitpicker.model.DownloadStatus
import com.example.nitpicker.model.FileInfo
import com.example.nitpicker.db.DownloadTaskEntity
import com.example.nitpicker.db.DownloadTaskDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
    private val okHttpClient: OkHttpClient,
    private val downloadTaskDao: DownloadTaskDao
) {
    val downloadState: StateFlow<Map<String, DownloadProgress>> =
        downloadTaskDao.getAllTasksFlow()
            .map { taskEntities ->
                taskEntities.associate { entity ->
                    entity.id to mapEntityToProgress(entity)
                }
            }
            .stateIn(
                scope = CoroutineScope(Dispatchers.IO),
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyMap()
            )

    private val jobMap = ConcurrentHashMap<String, Job>()
    private val imageDownloadDispatcher = Executors.newFixedThreadPool(5).asCoroutineDispatcher()
    private val otherDownloadDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val urlFetchSemaphore = Semaphore(5) // For image URL fetching
    private val otherDownloadSemaphore = Semaphore(1) // New: For controlling the entire "other" download process

    init {
        CoroutineScope(Dispatchers.IO).launch {
            resumeInterruptedDownloads()
        }
    }

    private suspend fun resumeInterruptedDownloads() {
        val activeTasks = downloadTaskDao.getActiveTasks()
        Log.d("DownloadService", "Found ${activeTasks.size} potentially interrupted tasks to resume.")
        activeTasks.forEach { task ->
            val dispatcher = if (isImage(task.fileType)) imageDownloadDispatcher else otherDownloadDispatcher
            Log.d("DownloadService", "Resuming task: ${task.fileName} with status ${task.status}")
            launchSingleDownload(task.id, dispatcher, null)
        }
    }

    fun enqueueDownloads(files: List<FileInfo>, albumTitle: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val initialTaskEntities = files.map { fileInfo ->
                val stableId = generateStableId(fileInfo)
                DownloadTaskEntity(
                    id = stableId,
                    fileName = fileInfo.fileName,
                    fileType = fileInfo.fileType,
                    sourcePageUrl = fileInfo.pageUrl,
                    downloadPageUrl = "",
                    fileUrl = "",
                    thumbnailUrl = fileInfo.thumbnailUrl,
                    albumTitle = albumTitle,
                    status = DownloadStatus.Pending
                )
            }

            initialTaskEntities.forEach { entity ->
                val existingTask = downloadTaskDao.getTaskById(entity.id)
                if (existingTask != null && (existingTask.status == DownloadStatus.Error || existingTask.status == DownloadStatus.Cancelled)) {
                    downloadTaskDao.insertOrUpdateTask(entity.copy(
                        createdAt = existingTask.createdAt,
                        downloadedBytes = 0, totalBytes = 0, filePath = null, error = null
                    ))
                    Log.d("DownloadService", "Updating existing error/cancelled task to Pending: ${entity.fileName}")
                } else if (existingTask == null) {
                    downloadTaskDao.insertOrUpdateTask(entity)
                    Log.d("DownloadService", "Inserting new task: ${entity.fileName}")
                } else {
                    Log.d("DownloadService", "Task ${entity.fileName} already exists and is not in error/cancelled state.")
                }
            }

            val (imageFiles, otherFiles) = files.partition { isImage(it.fileType) }

            if (imageFiles.isNotEmpty()) {
                launch {
                    fetchAndLaunchImageDownloads(imageFiles, albumTitle)
                }
            }

            if (otherFiles.isNotEmpty()) {
                val otherTaskIds = otherFiles.map { generateStableId(it) }
                launchQueuedDownloads(otherTaskIds, otherDownloadDispatcher)
            }
        }
    }

    private suspend fun fetchAndLaunchImageDownloads(imageFiles: List<FileInfo>, albumTitle: String) {
        Log.d("DownloadService", "Fetching URLs for ${imageFiles.size} image files...")

        val downloadFileInfos = imageFiles.map { fileInfo ->
            val taskId = generateStableId(fileInfo)
            coroutineScope {
                async(Dispatchers.IO) {
                    urlFetchSemaphore.withPermit {
                        Log.d("DownloadService", "Fetching URL for image: ${fileInfo.fileName} (Task ID: $taskId)")
                        downloadTaskDao.updateTaskStatus(taskId, DownloadStatus.FetchingUrl)
                        try {
                            val downloadInfo = getSingleDownloadFileInfo(fileInfo, albumTitle)
                            downloadTaskDao.updateTaskUrlsAndStatus(taskId, downloadInfo.downloadPageUrl, downloadInfo.fileUrl, DownloadStatus.Pending)
                            Log.d("DownloadService", "Successfully fetched URL for image: ${downloadInfo.fileName}")
                            downloadInfo
                        } catch (e: Exception) {
                            Log.e("DownloadService", "Failed to fetch URL for image ${fileInfo.fileName}: ${e.message}", e)
                            downloadTaskDao.updateTaskStatus(
                                id = taskId,
                                status = DownloadStatus.Error,
                                error = "Failed to get download URL: ${e.message}"
                            )
                            null
                        }
                    }
                }
            }
        }.awaitAll().filterNotNull()

        Log.d("DownloadService", "Finished fetching URLs for images. Launching ${downloadFileInfos.size} downloads.")
        launchDownloads(downloadFileInfos, imageDownloadDispatcher)
    }

    private fun launchDownloads(
        downloads: List<DownloadFileInfo>,
        dispatcher: CoroutineDispatcher
    ) {
        downloads.forEach { downloadInfo ->
            if (!jobMap.containsKey(downloadInfo.id)) {
                launchSingleDownload(downloadInfo.id, dispatcher, downloadInfo)
            } else {
                Log.d("DownloadService", "Job for image ${downloadInfo.id} already running or queued.")
            }
        }
    }

    private fun launchQueuedDownloads(
        taskIds: List<String>,
        dispatcher: CoroutineDispatcher
    ) {
        taskIds.forEach { taskId ->
            if (!jobMap.containsKey(taskId)) {
                launchSingleDownload(taskId, dispatcher, null)
            } else {
                Log.d("DownloadService", "Job for other file $taskId already running or queued.")
            }
        }
    }

    private fun launchSingleDownload(taskId: String, dispatcher: CoroutineDispatcher, preFetchedInfo: DownloadFileInfo?) {
        if (jobMap.containsKey(taskId)) {
            Log.d("DownloadService", "Skipping launch for $taskId, job already exists.")
            return
        }

        val job = CoroutineScope(dispatcher).launch { // Still use the appropriate dispatcher
            // Use 'val' for the initial check to allow smart casting initially
            val initialTask = downloadTaskDao.getTaskById(taskId)
            if (initialTask == null) {
                Log.e("DownloadService", "Task $taskId not found in DB for launching download.")
                return@launch // Return from the launch coroutine
            }
            if (initialTask.status == DownloadStatus.Completed || initialTask.status == DownloadStatus.Cancelled) {
                Log.w("DownloadService", "Skipping launch for task $taskId as its status is ${initialTask.status}")
                return@launch // Return from the launch coroutine
            }

            // Determine if it's an "other" file *before* potentially blocking operations
            val isOtherFile = !isImage(initialTask.fileType) // Smart cast works here

            if (isOtherFile) {
                Log.d("DownloadService", "Task $taskId is an 'other' file, acquiring single download permit...")
                otherDownloadSemaphore.withPermit { // Label for this lambda is @withPermit
                    Log.d("DownloadService", "Permit acquired for $taskId.")
                    // --- Start of the critical section for "other" files ---
                    // Re-fetch task inside the permit block as state might have changed while waiting
                    // Use a new 'var' specific to this block
                    var currentTask = downloadTaskDao.getTaskById(taskId) ?: run {
                        Log.e("DownloadService", "Task $taskId disappeared while waiting for permit.")
                        return@withPermit // Return from withPermit lambda
                    }

                    val downloadInfoToUse: DownloadFileInfo? = if (currentTask.fileUrl.isBlank() || currentTask.downloadPageUrl.isBlank()) { // Smart cast works on currentTask after null check
                        Log.d("DownloadService", "File URL missing for $taskId. Attempting fetch...")
                        downloadTaskDao.updateTaskStatus(taskId, DownloadStatus.FetchingUrl)
                        // Re-fetch and assign to currentTask after status update
                        currentTask = downloadTaskDao.getTaskById(taskId) ?: run {
                            Log.e("DownloadService", "Task $taskId disappeared after status update.")
                            return@withPermit // Return from withPermit lambda
                        }

                        try {
                            val fileInfo = FileInfo(
                                pageUrl = currentTask.sourcePageUrl, // Use non-null currentTask
                                fileName = currentTask.fileName,
                                fileType = currentTask.fileType,
                                thumbnailUrl = currentTask.thumbnailUrl,
                                fileSize = "" // Ensure FileInfo definition allows this or provide valid value
                            )
                            val fetchedInfo = getSingleDownloadFileInfo(fileInfo, currentTask.albumTitle)
                            downloadTaskDao.updateTaskUrls(taskId, fetchedInfo.downloadPageUrl, fetchedInfo.fileUrl)
                            Log.d("DownloadService", "Successfully fetched URL for $taskId on demand.")
                            fetchedInfo
                        } catch (e: Exception) {
                            Log.e("DownloadService", "Failed to fetch URL on demand for $taskId: ${e.message}", e)
                            downloadTaskDao.updateTaskStatus(taskId, DownloadStatus.Error, "Failed to get download URL: ${e.message}")
                            null
                        }
                    } else if (preFetchedInfo != null && preFetchedInfo.id == taskId) {
                        preFetchedInfo
                    } else {
                        mapEntityToDownloadInfo(currentTask) // Use non-null currentTask
                    }

                    if (downloadInfoToUse != null) {
                        // Re-check status before potentially changing it
                        val statusBeforeDownload = downloadTaskDao.getTaskById(taskId)?.status
                        if (statusBeforeDownload == null) {
                             Log.e("DownloadService", "Task $taskId disappeared before performDownload call.")
                             return@withPermit // Return from withPermit lambda
                        }
                        // Set to Downloading only if it's in a state that should proceed
                        if (statusBeforeDownload !in listOf(DownloadStatus.Downloading, DownloadStatus.Completed, DownloadStatus.Error, DownloadStatus.Cancelled)) {
                             downloadTaskDao.updateTaskStatus(taskId, DownloadStatus.Downloading)
                        }

                        // Final check before actual download, ensuring status is still Downloading
                        if (downloadTaskDao.getTaskById(taskId)?.status == DownloadStatus.Downloading) {
                            performDownload(downloadInfoToUse)
                        } else {
                             Log.w("DownloadService", "Skipping performDownload for $taskId as status changed before execution.")
                        }
                    } else {
                        Log.e("DownloadService", "Cannot proceed with download for $taskId, failed to get download info.")
                        // Status should already be Error from the fetch attempt
                    }
                    // --- End of the critical section for "other" files ---
                    Log.d("DownloadService", "Permit released for $taskId.")
                }
            } else { // Image file logic (no semaphore)
                // Use the initially fetched task, or re-fetch if necessary.
                // For simplicity, let's assume initialTask is sufficient unless URL is missing.
                var currentTask = initialTask // Start with the initial non-null task

                // --- Start of the image download logic ---
                 val downloadInfoToUse: DownloadFileInfo? = if (currentTask.fileUrl.isBlank() || currentTask.downloadPageUrl.isBlank()) {
                    // Handle case where image URL fetch failed previously or resuming an interrupted state
                    Log.d("DownloadService", "Image URL missing for $taskId. Attempting fetch...")
                    downloadTaskDao.updateTaskStatus(taskId, DownloadStatus.FetchingUrl)
                    currentTask = downloadTaskDao.getTaskById(taskId) ?: run {
                        Log.e("DownloadService", "Task $taskId disappeared after status update.")
                        return@launch // Return from the main launch block
                    }

                    try {
                        val fileInfo = FileInfo(
                            pageUrl = currentTask.sourcePageUrl,
                            fileName = currentTask.fileName,
                            fileType = currentTask.fileType,
                            thumbnailUrl = currentTask.thumbnailUrl,
                            fileSize = ""
                        )
                        val fetchedInfo = getSingleDownloadFileInfo(fileInfo, currentTask.albumTitle)
                        downloadTaskDao.updateTaskUrls(taskId, fetchedInfo.downloadPageUrl, fetchedInfo.fileUrl)
                        Log.d("DownloadService", "Successfully fetched URL for image $taskId on demand.")
                        fetchedInfo
                    } catch (e: Exception) {
                        Log.e("DownloadService", "Failed to fetch URL on demand for image $taskId: ${e.message}", e)
                        downloadTaskDao.updateTaskStatus(taskId, DownloadStatus.Error, "Failed to get download URL: ${e.message}")
                        null
                    }
                } else if (preFetchedInfo != null && preFetchedInfo.id == taskId) {
                    preFetchedInfo
                } else {
                    mapEntityToDownloadInfo(currentTask)
                }

                if (downloadInfoToUse != null) {
                    val statusBeforeDownload = downloadTaskDao.getTaskById(taskId)?.status
                    if (statusBeforeDownload == null) {
                         Log.e("DownloadService", "Task $taskId disappeared before performDownload call.")
                         return@launch // Return from the main launch block
                    }
                    if (statusBeforeDownload !in listOf(DownloadStatus.Downloading, DownloadStatus.Completed, DownloadStatus.Error, DownloadStatus.Cancelled)) {
                         downloadTaskDao.updateTaskStatus(taskId, DownloadStatus.Downloading)
                    }

                    if (downloadTaskDao.getTaskById(taskId)?.status == DownloadStatus.Downloading) {
                        performDownload(downloadInfoToUse)
                    } else {
                         Log.w("DownloadService", "Skipping performDownload for image $taskId as status changed.")
                    }
                } else {
                    Log.e("DownloadService", "Cannot proceed with download for image $taskId, failed to get download info.")
                }
                // --- End of the image download logic ---
            }
        }
        jobMap[taskId] = job
        // invokeOnCompletion remains the same
        job.invokeOnCompletion { throwable ->
            CoroutineScope(Dispatchers.IO).launch {
                val finalStatus = try {
                    downloadTaskDao.getTaskById(taskId)?.status
                } catch (e: Exception) {
                    Log.e("DownloadService", "Error getting final status for $taskId in invokeOnCompletion", e)
                    null
                }

                if (throwable is CancellationException) {
                    Log.d("DownloadService", "Job $taskId completed with cancellation.")
                    if (finalStatus != null && finalStatus !in listOf(DownloadStatus.Completed, DownloadStatus.Error, DownloadStatus.Cancelled)) {
                        downloadTaskDao.updateTaskStatus(taskId, DownloadStatus.Cancelled, "Job cancelled")
                    }
                } else if (throwable != null) {
                    Log.e("DownloadService", "Job $taskId completed with error: ${throwable.message}")
                } else {
                    if (finalStatus == DownloadStatus.Completed) {
                        Log.d("DownloadService", "Job $taskId completed successfully.")
                    } else {
                        Log.w("DownloadService", "Job $taskId finished without error, but final status is $finalStatus")
                    }
                }
                jobMap.remove(taskId)
            }
        }
    }

    fun cancelDownload(downloadId: String) {
        jobMap[downloadId]?.cancel(CancellationException("User cancelled download $downloadId"))
        jobMap.remove(downloadId)
        CoroutineScope(Dispatchers.IO).launch {
            val task = downloadTaskDao.getTaskById(downloadId)
            if (task != null && task.status != DownloadStatus.Completed) {
                downloadTaskDao.updateTaskStatus(downloadId, DownloadStatus.Cancelled, "User cancelled")
                task.filePath?.let { path ->
                    try {
                        File(path).delete()
                        Log.d("DownloadService", "Deleted partial file for cancelled download: $path")
                    } catch (e: Exception) {
                        Log.e("DownloadService", "Error deleting file for cancelled task $downloadId: $path", e)
                    }
                }
            }
        }
    }

    // 新增：删除所有已完成和已取消的任务
    fun deleteCompletedAndCancelledTasks() {
        CoroutineScope(Dispatchers.IO).launch {
            // Optional: Delete associated files first if needed (more complex)
            // For now, just delete from DB
            downloadTaskDao.deleteCompletedAndCancelled()
            Log.d("DownloadService", "Deleted completed and cancelled tasks from database.")
        }
    }

    // 新增：重试下载任务
    fun retryDownload(taskId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val task = downloadTaskDao.getTaskById(taskId)
            if (task != null && task.status == DownloadStatus.Error) {
                Log.d("DownloadService", "Retrying download for task: ${task.fileName}")
                // Reset status, error, and progress
                val resetTask = task.copy(
                    status = DownloadStatus.Pending, // Reset to Pending
                    error = null,
                    downloadedBytes = 0L
                    // Keep totalBytes if known, otherwise it will be re-fetched
                    // Keep filePath null or let prepareFilePath handle it
                )
                downloadTaskDao.insertOrUpdateTask(resetTask)

                // Delete potentially corrupted file before retry
                task.filePath?.let { path ->
                    try {
                        File(path).delete()
                        Log.d("DownloadService", "Deleted potentially corrupted file before retry: $path")
                    } catch (e: Exception) {
                        Log.e("DownloadService", "Error deleting file before retry for task $taskId: $path", e)
                    }
                }


                // Re-launch the download
                val dispatcher = if (isImage(task.fileType)) imageDownloadDispatcher else otherDownloadDispatcher
                // Remove existing job if somehow present (shouldn't be for Error state, but safe)
                jobMap.remove(taskId)?.cancel()
                // Launch again
                launchSingleDownload(taskId, dispatcher, null)
            } else {
                Log.w("DownloadService", "Cannot retry task $taskId. Task not found or not in Error state (current status: ${task?.status}).")
            }
        }
    }

    private suspend fun getSingleDownloadFileInfo(fileInfo: FileInfo, albumTitle: String): DownloadFileInfo {
        val pageUrl = fileInfo.pageUrl
        val request = Request.Builder().url(pageUrl).build()

        val response: Response = withContext(Dispatchers.IO) {
            okHttpClient.newCall(request).execute()
        }
        if (!response.isSuccessful) throw IOException("Failed to fetch page $pageUrl: ${response.code}")

        val html = response.body?.string() ?: throw IOException("Empty body for $pageUrl")
        val document = Jsoup.parse(html, pageUrl)

        val downloadPageUrlPattern = Pattern.compile("""href="(https://get\.bunkrr\.su/file/\d+)"""")
        val matcher = downloadPageUrlPattern.matcher(html)
        val downloadPageUrl = if (matcher.find()) {
            matcher.group(1) ?: throw IOException("Cannot find download page URL in $pageUrl")
        } else {
            throw IOException("Cannot find download page URL pattern in $pageUrl")
        }

        val fileType = fileInfo.fileType
        val thumbnailUrl = fileInfo.thumbnailUrl
        val fileName = fileInfo.fileName

        val stableId = generateStableId(fileInfo)

        val domainMatch = Regex("^(https?://[^/]+)").find(thumbnailUrl)
        val baseDomain = domainMatch?.groups?.get(1)?.value ?: throw IOException("Cannot extract domain from thumbnail $thumbnailUrl")

        val fileUrl = when {
            isImage(fileType) -> "$baseDomain/$stableId.$fileType?n=${encodeUrl(fileName)}"
            baseDomain.contains("burger") -> "https://brg-bk.cdn.gigachad-cdn.ru/$stableId.$fileType?n=${encodeUrl(fileName)}"
            baseDomain.contains("milkshake") -> "https://mlk-bk.cdn.gigachad-cdn.ru/$stableId.$fileType?n=${encodeUrl(fileName)}"
            else -> "${baseDomain.replace("i-", "")}/$stableId.$fileType?n=${encodeUrl(fileName)}"
        }

        return DownloadFileInfo(
            id = stableId,
            fileName = fileName,
            fileType = fileType,
            downloadPageUrl = downloadPageUrl,
            fileUrl = fileUrl,
            albumTitle = albumTitle
        )
    }

    private suspend fun performDownload(downloadInfo: DownloadFileInfo) {
        val downloadId = downloadInfo.id
        var currentTask = downloadTaskDao.getTaskById(downloadId) ?: run {
            Log.e("DownloadService", "Task $downloadId not found in DB for download start.")
            return
        }

        Log.d("DownloadService", "Starting/Resuming download for ID: $downloadId, File: ${downloadInfo.fileName}")
        if (currentTask.status != DownloadStatus.Downloading) {
            downloadTaskDao.updateTaskStatus(downloadId, DownloadStatus.Downloading)
        }

        var file: File? = null
        var outputStream: FileOutputStream? = null
        var response: Response? = null
        var downloadedBytes = currentTask.downloadedBytes
        val requestBuilder = Request.Builder().url(downloadInfo.fileUrl)
            .header("Referer", downloadInfo.downloadPageUrl)

        try {
            file = if (currentTask.filePath != null && File(currentTask.filePath!!).exists()) {
                File(currentTask.filePath!!)
            } else {
                prepareFilePath(downloadInfo.albumTitle, downloadInfo.id, downloadInfo.fileName).also {
                    currentTask = currentTask.copy(filePath = it.absolutePath)
                    downloadTaskDao.insertOrUpdateTask(currentTask)
                }
            }
            Log.d("DownloadService", "Saving to: ${file.absolutePath}")

            var resumeOffset = 0L
            if (downloadedBytes > 0 && file.exists() && file.length() == downloadedBytes) {
                Log.d("DownloadService", "Attempting resume for $downloadId from $downloadedBytes bytes.")
                requestBuilder.header("Range", "bytes=$downloadedBytes-")
                resumeOffset = downloadedBytes
            } else if (downloadedBytes > 0) {
                Log.w("DownloadService", "Partial file mismatch/missing for $downloadId (file size ${file.length()}, expected $downloadedBytes). Restarting download.")
                downloadedBytes = 0
                resumeOffset = 0
                withContext(Dispatchers.IO) { file.delete() }
                downloadTaskDao.updateTaskProgress(downloadId, DownloadStatus.Downloading, 0, currentTask.totalBytes, null)
            }

            response = withContext(Dispatchers.IO) {
                okHttpClient.newCall(requestBuilder.build()).execute()
            }

            if (response.code == 416 && resumeOffset > 0) {
                Log.w("DownloadService", "Server returned 416 for range request $downloadId. Checking if file is complete or restarting.")
                val serverTotalSize = fetchContentLength(downloadInfo)
                if (serverTotalSize != null && file.exists() && file.length() == serverTotalSize) {
                    Log.i("DownloadService", "File $downloadId already complete based on size check after 416.")
                    downloadTaskDao.updateTaskCompletion(downloadId, DownloadStatus.Completed, file.absolutePath)
                    return
                } else {
                    Log.w("DownloadService", "Restarting download for $downloadId after 416.")
                    downloadedBytes = 0
                    resumeOffset = 0
                    withContext(Dispatchers.IO) { file.delete() }
                    downloadTaskDao.updateTaskProgress(downloadId, DownloadStatus.Downloading, 0, currentTask.totalBytes, null)
                    response.close()
                    val freshRequestBuilder = Request.Builder().url(downloadInfo.fileUrl)
                        .header("Referer", downloadInfo.downloadPageUrl)
                    response = withContext(Dispatchers.IO) {
                        okHttpClient.newCall(freshRequestBuilder.build()).execute()
                    }
                }
            }

            if (!response.isSuccessful) {
                throw IOException("Download failed: HTTP ${response.code} for ${downloadInfo.fileUrl}")
            }

            val body = response.body ?: throw IOException("Response body is null")
            val responseContentLength = body.contentLength()
            val totalBytes = if (resumeOffset > 0 && responseContentLength > 0) {
                resumeOffset + responseContentLength
            } else if (responseContentLength > 0) {
                responseContentLength
            } else {
                currentTask.totalBytes.takeIf { it > 0 } ?: -1L
            }

            if (totalBytes > 0 && totalBytes != currentTask.totalBytes) {
                downloadTaskDao.updateTaskProgress(downloadId, DownloadStatus.Downloading, downloadedBytes, totalBytes, null)
            }

            outputStream = FileOutputStream(file, resumeOffset > 0)
            val buffer = ByteArray(8 * 1024)
            var bytesRead: Int
            var lastProgressEmitTime = System.currentTimeMillis()

            val inputStream = body.byteStream()
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                currentCoroutineContext().ensureActive()
                outputStream.write(buffer, 0, bytesRead)
                downloadedBytes += bytesRead

                val currentTime = System.currentTimeMillis()
                if (totalBytes > 0 && (currentTime - lastProgressEmitTime > 300 || downloadedBytes == totalBytes)) {
                    downloadTaskDao.updateTaskProgress(downloadId, DownloadStatus.Downloading, downloadedBytes, totalBytes, null)
                    lastProgressEmitTime = currentTime
                }
            }
            outputStream.flush()

            if (totalBytes > 0 && downloadedBytes != totalBytes) {
                throw IOException("Download incomplete: Expected $totalBytes bytes, got $downloadedBytes bytes.")
            }

            Log.d("DownloadService", "Download completed successfully: ${downloadInfo.fileName}")
            downloadTaskDao.updateTaskCompletion(downloadId, DownloadStatus.Completed, file.absolutePath)

        } catch (e: CancellationException) {
            Log.d("DownloadService", "Download cancelled: ${downloadInfo.fileName}")
        } catch (e: Exception) {
            Log.e("DownloadService", "Download error for ${downloadInfo.fileName}: ${e.message}", e)
            downloadTaskDao.updateTaskStatus(downloadId, DownloadStatus.Error, e.message ?: "Unknown download error")
            file?.let {
                withContext(Dispatchers.IO + NonCancellable) {
                    try { it.delete() } catch (delEx: Exception) { Log.e("DownloadService", "Error deleting file on error: ${it.path}", delEx) }
                }
            }
        } finally {
            try { outputStream?.close() } catch (e: IOException) { }
            try { response?.body?.close() } catch (e: Exception) { }
        }
    }

    private suspend fun fetchContentLength(downloadInfo: DownloadFileInfo): Long? {
        val request = Request.Builder().url(downloadInfo.fileUrl)
            .head()
            .header("Referer", downloadInfo.downloadPageUrl)
            .build()
        return try {
            val response = withContext(Dispatchers.IO) {
                okHttpClient.newCall(request).execute()
            }
            response.use {
                if (it.isSuccessful) {
                    it.header("Content-Length")?.toLongOrNull()
                } else {
                    null
                }
            }
        } catch (e: IOException) {
            Log.w("DownloadService", "Failed to fetch content length for ${downloadInfo.id}: ${e.message}")
            null
        }
    }

    private fun generateStableId(fileInfo: FileInfo): String {
        return try {
            fileInfo.thumbnailUrl.substringAfterLast('/').substringBefore('.')
        } catch (e: Exception) {
            Log.w("DownloadService", "Could not generate stable ID from thumbnail URL: ${fileInfo.thumbnailUrl}. Using page URL hash for ${fileInfo.fileName}.")
            "id_${fileInfo.pageUrl.hashCode()}"
        }
    }

    private fun mapEntityToDownloadInfo(entity: DownloadTaskEntity): DownloadFileInfo {
        return DownloadFileInfo(
            id = entity.id,
            fileName = entity.fileName,
            fileType = entity.fileType,
            downloadPageUrl = entity.downloadPageUrl,
            fileUrl = entity.fileUrl,
            albumTitle = entity.albumTitle
        )
    }

    private fun mapEntityToProgress(entity: DownloadTaskEntity): DownloadProgress {
        return DownloadProgress(
            id = entity.id,
            fileName = entity.fileName,
            albumTitle = entity.albumTitle,
            totalBytes = entity.totalBytes,
            downloadedBytes = entity.downloadedBytes,
            progressPercent = if (entity.totalBytes > 0 && entity.status != DownloadStatus.Error) ((entity.downloadedBytes * 100) / entity.totalBytes).toInt() else 0,
            status = entity.status,
            error = entity.error,
            filePath = entity.filePath
        )
    }

    private suspend fun prepareFilePath(albumTitle: String, id: String, originalFileName: String): File {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        val sanitizedAlbumTitle = sanitizeFilename(albumTitle.ifBlank { "Downloads" })
        val albumDir = File(baseDir, sanitizedAlbumTitle)

        withContext(Dispatchers.IO) {
            if (!albumDir.exists()) {
                if (albumDir.mkdirs()) {
                    Log.d("DownloadService", "Created directory: ${albumDir.absolutePath}")
                } else {
                    // Log error but proceed, attempting to save in baseDir
                    Log.e("DownloadService", "Failed to create directory: ${albumDir.absolutePath}")
                    Log.w("DownloadService", "Falling back to base download directory.")
                    // Ensure albumDir points to baseDir if creation failed and it doesn't exist
                    if (!albumDir.exists()) {
                         // This assignment might not be needed if finalFile logic handles it,
                         // but clarifies the fallback directory.
                         // albumDir = baseDir // Reassigning might be complex, handle in finalFile logic
                    }
                }
            }
        }

        // Extract extension from the original filename
        val extension = originalFileName.substringAfterLast('.', "")
        val filenameById = if (extension.isNotEmpty()) "$id.$extension" else id

        // Determine the target directory (albumDir if exists, else baseDir)
        val targetDir = if (withContext(Dispatchers.IO) { albumDir.exists() }) albumDir else baseDir
        Log.d("DownloadService", "Target directory for $id: ${targetDir.absolutePath}")

        var finalFile = File(targetDir, filenameById)
        var counter = 1
        val nameWithoutExt = id // Use ID as the base name for uniqueness check

        // Check for existing file and add counter if needed
        while (withContext(Dispatchers.IO) { finalFile.exists() }) {
            val newName = if (extension.isNotEmpty()) {
                "${nameWithoutExt}_($counter).$extension" // Add counter before extension
            } else {
                "${nameWithoutExt}_($counter)"
            }
            finalFile = File(targetDir, newName)
            counter++
            if (counter > 100) { // Safety break
                Log.e("DownloadService", "Could not find unique filename using ID after 100 attempts for: $originalFileName (ID: $id)")
                throw IOException("Could not determine unique filename for $originalFileName (ID: $id)")
            }
        }
        Log.d("DownloadService", "Final path for $id: ${finalFile.absolutePath}")
        return finalFile
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
    }

    private fun isImage(fileType: String): Boolean {
        return fileType.lowercase() in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "avif", "tiff", "svg", "ico")
    }

    private fun encodeUrl(value: String): String {
        return try {
            java.net.URLEncoder.encode(value, "UTF-8")
        } catch (e: java.io.UnsupportedEncodingException) {
            Log.e("DownloadService", "UTF-8 encoding not supported?", e)
            value
        }
    }
}