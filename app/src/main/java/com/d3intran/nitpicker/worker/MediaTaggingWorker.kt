package com.d3intran.nitpicker.worker

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.d3intran.nitpicker.db.MediaMetadataDao
import com.d3intran.nitpicker.db.MediaMetadataEntity
import com.d3intran.nitpicker.util.ImageAIProcessor
import com.d3intran.nitpicker.util.SafDirectoryViewer
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * 媒体后台标记 Worker。
 * 负责遍历指定目录，对新图片进行本地 ML Kit 分析（人脸/物体检测），
 * 并将结果存入 Room 数据库，为后续 Gemini 处理和全局搜索做准备。
 */
@HiltWorker
class MediaTaggingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val imageAIProcessor: ImageAIProcessor,
    private val mediaMetadataDao: MediaMetadataDao
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val folderUriString = inputData.getString("folderUri") ?: return Result.failure()
        val folderUri = Uri.parse(folderUriString)

        Log.d("MediaTaggingWorker", "Starting background indexing for: $folderUriString")

        return try {
            // 获取目录下所有文件
            val files = SafDirectoryViewer.listFilesFromUri(applicationContext, folderUri)
            val totalFiles = files.size
            var processedCount = 0

            files.forEach { fileItem ->
                // 目前仅对图片进行本地 AI 分析
                if (fileItem.type == com.d3intran.nitpicker.model.FileType.IMAGE) {
                    val existing = mediaMetadataDao.getMetadataForUri(fileItem.path)
                    
                    // 如果尚未处理，则运行分析
                    if (existing == null) {
                        val result = imageAIProcessor.analyzeImage(Uri.parse(fileItem.path))
                        
                        val entity = MediaMetadataEntity(
                            uri = fileItem.path,
                            tags = result.localLabels,
                            description = "Local ML Kit Scan",
                            faceCount = result.faceCount,
                            objectCount = result.objectCount,
                            isProcessed = false, // 设为 false，表示尚未经过 Gemini 深度处理
                            lastUpdated = System.currentTimeMillis()
                        )
                        mediaMetadataDao.insertMetadata(entity)
                        Log.d("MediaTaggingWorker", "Indexed: ${fileItem.name} (Faces: ${result.faceCount})")
                    }
                }
                
                processedCount++
                // 更新进度，UI 可以监听
                setProgress(workDataOf("progress" to (processedCount * 100 / totalFiles)))
            }

            Log.d("MediaTaggingWorker", "Indexing completed for: $folderUriString")
            Result.success()
        } catch (e: Exception) {
            Log.e("MediaTaggingWorker", "Error during background indexing", e)
            Result.retry()
        }
    }
}
