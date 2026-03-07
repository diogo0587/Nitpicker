package com.d3intran.nitpicker.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 图像 AI 处理类。
 * 使用 Google ML Kit 在本地进行快速的人脸检测和物体识别，
 * 作为发送给 Gemini API 之前的预处理步骤。
 */
@Singleton
class ImageAIProcessor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // 初始化人脸检测器 (性能模式：快速)
    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
    )

    // 初始化物体检测器 (单图模式，启用多分类)
    private val objectDetector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
    )

    /**
     * 异步分析图像，提取人脸数、物体数和本地标签。
     */
    suspend fun analyzeImage(uri: Uri): AnalysisResult = withContext(Dispatchers.IO) {
        val bitmap = loadBitmapFromUri(uri) ?: return@withContext AnalysisResult(0, 0, emptyList())
        val image = InputImage.fromBitmap(bitmap, 0)

        return@withContext try {
            val faces = faceDetector.process(image).await()
            val objects = objectDetector.process(image).await()

            val labels = objects.flatMap { obj ->
                obj.labels.map { it.text }
            }.distinct()

            AnalysisResult(
                faceCount = faces.size,
                objectCount = objects.size,
                localLabels = labels
            )
        } catch (e: Exception) {
            AnalysisResult(0, 0, emptyList())
        }
    }

    /**
     * 从 Uri 加载并压缩 Bitmap，防止内存溢出 (OOM)。
     */
    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { 
                BitmapFactory.decodeStream(it, null, options) 
            }

            // 设定分析时的最大边长为 1024 像素
            val targetSize = 1024
            var inSampleSize = 1
            if (options.outHeight > targetSize || options.outWidth > targetSize) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while (halfHeight / inSampleSize >= targetSize && halfWidth / inSampleSize >= targetSize) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = inSampleSize
            }
            context.contentResolver.openInputStream(uri)?.use { 
                BitmapFactory.decodeStream(it, null, decodeOptions) 
            }
        } catch (e: Exception) {
            null
        }
    }

    data class AnalysisResult(
        val faceCount: Int,
        val objectCount: Int,
        val localLabels: List<String>
    )
}
