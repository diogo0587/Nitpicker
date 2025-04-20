package com.example.nitpicker.screen.local_album

import android.app.Application
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nitpicker.model.FileType
import com.example.nitpicker.model.LocalFileItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import android.media.MediaMetadataRetriever

data class LocalAlbumUiState(
    val folderName: String = "",
    val folderPath: String = "",
    val files: List<LocalFileItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class LocalAlbumViewModel @Inject constructor(
    private val application: Application,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocalAlbumUiState())
    val uiState: StateFlow<LocalAlbumUiState> = _uiState.asStateFlow()

    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
    private val videoExtensions = setOf("mp4", "mkv", "webm", "avi", "3gp")

    init {
        // Decode the path passed via navigation
        val encodedPath = savedStateHandle.get<String>("folderPath") ?: ""
        val folderPath = try {
            java.net.URLDecoder.decode(encodedPath, "UTF-8")
        } catch (e: Exception) {
            Log.e("LocalAlbumViewModel", "Error decoding folder path", e)
            ""
        }

        if (folderPath.isNotEmpty()) {
            val folder = File(folderPath)
            _uiState.update { it.copy(folderPath = folderPath, folderName = folder.name) }
            loadFiles(folderPath)
        } else {
            _uiState.update { it.copy(error = "Invalid folder path received.") }
        }
    }

    fun loadFiles(folderPath: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val folder = File(folderPath)
                if (!folder.exists() || !folder.isDirectory) {
                    throw IOException("Folder not found or is not a directory: $folderPath")
                }

                val fileList = withContext(Dispatchers.IO) {
                    folder.listFiles()?.mapNotNull { file ->
                        processFile(file)
                    }?.sortedBy { it.name } ?: emptyList()
                }

                Log.d("LocalAlbumViewModel", "Loaded ${fileList.size} items from $folderPath")
                _uiState.update { it.copy(files = fileList, isLoading = false) }

            } catch (e: SecurityException) {
                Log.e("LocalAlbumViewModel", "Permission denied accessing $folderPath", e)
                _uiState.update { it.copy(error = "Permission denied.", isLoading = false, files = emptyList()) }
            } catch (e: Exception) {
                Log.e("LocalAlbumViewModel", "Error loading files from $folderPath", e)
                _uiState.update { it.copy(error = "Error loading files: ${e.localizedMessage}", isLoading = false, files = emptyList()) }
            }
        }
    }

    // Processes a single file to create a LocalFileItem (runs on IO dispatcher)
    private suspend fun processFile(file: File): LocalFileItem? {
        if (!file.isFile) return null
        val name = file.name
        val path = file.absolutePath
        val extension = file.extension.lowercase()
        val size = file.length()
        val lastModified = file.lastModified()

        val type = when {
            imageExtensions.contains(extension) -> FileType.IMAGE
            videoExtensions.contains(extension) -> FileType.VIDEO
            else -> return null // Skip unsupported files for now
        }

        // --- Thumbnail Logic ---
        val thumbnailUri: Uri? = try {
            when (type) {
                FileType.IMAGE -> file.toUri() // Coil can handle image file URIs directly
                FileType.VIDEO -> getVideoThumbnailUri(file) // Generate/cache video thumbnail
                else -> null
            }
        } catch (e: Exception) {
            Log.e("LocalAlbumViewModel", "Error getting thumbnail for $name", e)
            null
        }

        return LocalFileItem(path, name, type, thumbnailUri, size, lastModified)
    }

    // Generates and caches a video thumbnail, returning its URI (runs on IO dispatcher)
    private suspend fun getVideoThumbnailUri(videoFile: File): Uri? = withContext(Dispatchers.IO) {
        val cacheDir = File(application.cacheDir, "thumbnails")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        // Create a unique cache file name based on path and modification time
        val cacheKey = "${videoFile.absolutePath.hashCode()}_${videoFile.lastModified()}.jpg"
        val thumbFile = File(cacheDir, cacheKey)

        if (thumbFile.exists() && thumbFile.length() > 0) {
            return@withContext thumbFile.toUri()
        }

        // --- Thumbnail Generation ---
        var bitmap: Bitmap? = null
        var fos: FileOutputStream? = null
        val targetSize = 512 // Increase target size for better quality (e.g., 512x512)
        val quality = 90 // Increase compression quality (e.g., 90)

        try {
            // --- Option 1: Using ThumbnailUtils (Improved Size) ---
            bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Request a larger size
                ThumbnailUtils.createVideoThumbnail(videoFile, Size(targetSize, targetSize), null)
            } else {
                // MINI_KIND is low quality, MICRO_KIND might be slightly better but still small.
                // Consider MediaMetadataRetriever for older APIs if quality is critical.
                @Suppress("DEPRECATION")
                ThumbnailUtils.createVideoThumbnail(videoFile.absolutePath, MediaStore.Images.Thumbnails.MICRO_KIND) // Try MICRO_KIND
                // Fallback or alternative: Use MediaMetadataRetriever (see Option 2 below)
            }

            // --- Option 2: Using MediaMetadataRetriever (Potentially higher quality, might be slower) ---
            /* // Uncomment to use MediaMetadataRetriever instead of ThumbnailUtils
            var retriever: MediaMetadataRetriever? = null
            try {
                retriever = MediaMetadataRetriever()
                retriever.setDataSource(videoFile.absolutePath)
                // Get frame at a specific time (e.g., 1 second)
                bitmap = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

                // Optional: Scale the retrieved frame if needed (can be large)
                if (bitmap != null) {
                    val originalWidth = bitmap.width
                    val originalHeight = bitmap.height
                    val scale = if (originalWidth > originalHeight) {
                        targetSize.toFloat() / originalWidth
                    } else {
                        targetSize.toFloat() / originalHeight
                    }
                    if (scale < 1.0) { // Only scale down
                         val scaledWidth = (originalWidth * scale).toInt()
                         val scaledHeight = (originalHeight * scale).toInt()
                         val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
                         bitmap.recycle() // Recycle original large bitmap
                         bitmap = scaledBitmap
                    }
                }

            } catch (e: Exception) {
                 Log.e("LocalAlbumViewModel", "MediaMetadataRetriever failed for ${videoFile.name}", e)
            } finally {
                 retriever?.release()
            }
            */ // End of MediaMetadataRetriever block


            if (bitmap != null) {
                fos = FileOutputStream(thumbFile)
                // Use higher quality setting
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos)
                Log.d("LocalAlbumViewModel", "Generated thumbnail for ${videoFile.name} at ${thumbFile.absolutePath}")
                return@withContext thumbFile.toUri()
            } else {
                Log.w("LocalAlbumViewModel", "Failed to generate thumbnail for ${videoFile.name}")
                null
            }
        } catch (e: Exception) {
            Log.e("LocalAlbumViewModel", "Error generating or saving thumbnail for ${videoFile.name}", e)
            thumbFile.delete()
            null
        } finally {
            bitmap?.recycle()
            try {
                fos?.close()
            } catch (ioe: IOException) { /* Ignore */ }
        }
    }

    fun retry() {
        loadFiles(uiState.value.folderPath)
    }
}