package com.d3intran.nitpicker.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class AnalysisResult(
    val labels: List<String>,
    val faceCount: Int
)

object ImageAIProcessor {

    private const val CONFIDENCE_THRESHOLD = 0.6f
    private const val TAG = "ImageAIProcessor"

    /**
     * Analyzes an image URI using the BUNDLED default ML Kit Image Labeler.
     * This uses Google's standard 400+ class model which is excellent for consumer photos
     * (e.g., "Sky", "Person", "Pet", "Food").
     * Since we use `com.google.mlkit:image-labeling`, the model is packaged directly in the APK,
     * so it works 100% offline without needing a first-time Play Services download.
     */
    suspend fun analyzeImage(context: Context, imageUri: Uri): AnalysisResult {
        return try {
            val options = ImageLabelerOptions.Builder()
                .setConfidenceThreshold(CONFIDENCE_THRESHOLD)
                .build()

            val labeler = ImageLabeling.getClient(options)

            val bitmap = loadScaledBitmap(context, imageUri) ?: return AnalysisResult(emptyList(), 0)
            val inputImage = InputImage.fromBitmap(bitmap, 0)

            val labels = suspendCancellableCoroutine { continuation ->
                labeler.process(inputImage)
                    .addOnSuccessListener { results ->
                        val labelNames = results.map { it.text }
                        Log.d(TAG, "Labels for $imageUri: $labelNames")
                        continuation.resume(labelNames)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Labeling failed for $imageUri", e)
                        continuation.resumeWithException(e)
                    }
            }

            labeler.close()
            bitmap.recycle()

            AnalysisResult(labels = labels, faceCount = 0)
        } catch (e: Exception) {
            Log.e(TAG, "analyzeImage error", e)
            AnalysisResult(emptyList(), 0)
        }
    }

    private fun loadScaledBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val opts = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(stream, null, opts)

                val maxDim = 640
                opts.inSampleSize = calculateInSampleSize(opts, maxDim, maxDim)
                opts.inJustDecodeBounds = false

                context.contentResolver.openInputStream(uri)?.use { s2 ->
                    BitmapFactory.decodeStream(s2, null, opts)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bitmap from $uri", e)
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height, width) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
