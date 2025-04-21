package com.d3intran.nitpicker.screen.image

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.inject.Inject

data class ImageViewerUiState(
    val imageUris: List<Uri> = emptyList(),
    val imageNames: List<String> = emptyList(),
    val currentImageIndex: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null
) {
    val currentImageName: String? get() = imageNames.getOrNull(currentImageIndex)
}

@HiltViewModel
class ImageViewModel @Inject constructor(
    private val application: Application,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImageViewerUiState())
    val uiState: StateFlow<ImageViewerUiState> = _uiState.asStateFlow()

    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")

    init {
        loadImages()
    }

    private fun loadImages() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val encodedPath = savedStateHandle.get<String>("folderPath") ?: ""
            val navInitialIndex = savedStateHandle.get<Int>("initialIndex") ?: -1
            val restoredIndex = savedStateHandle.get<Int>("currentImageIndex") ?: -1

            Log.d("ImageViewModel", "loadImages - Received path: $encodedPath, navInitialIndex: $navInitialIndex, restoredIndex: $restoredIndex")

            if (encodedPath.isNotEmpty()) {
                try {
                    val folderPath = URLDecoder.decode(encodedPath, StandardCharsets.UTF_8.toString())
                    val imageFiles = loadImageFilesFromFolder(folderPath)

                    if (imageFiles.isNotEmpty()) {
                        val imageUris = imageFiles.map { it.toUri() }
                        val imageNames = imageFiles.map { it.name }

                        val finalStartIndex = if (navInitialIndex in imageUris.indices) {
                            Log.d("ImageViewModel", "Using navigation initial index: $navInitialIndex")
                            navInitialIndex
                        } else if (restoredIndex in imageUris.indices) {
                            Log.d("ImageViewModel", "Navigation index invalid, using restored index: $restoredIndex")
                            restoredIndex
                        } else if (imageUris.isNotEmpty()) {
                            Log.d("ImageViewModel", "Both indices invalid, defaulting to 0")
                            0
                        } else {
                            Log.w("ImageViewModel", "Indices invalid and list empty.")
                            -1
                        }

                        if (finalStartIndex != -1) {
                            _uiState.update {
                                it.copy(
                                    imageUris = imageUris,
                                    imageNames = imageNames,
                                    currentImageIndex = finalStartIndex,
                                    isLoading = false
                                )
                            }
                            savedStateHandle["currentImageIndex"] = finalStartIndex
                            Log.d("ImageViewModel", "Images loaded. Count: ${imageUris.size}, Final Start Index: $finalStartIndex")
                        } else {
                            _uiState.update { it.copy(error = "No images found.", isLoading = false) }
                        }

                    } else {
                        _uiState.update { it.copy(error = "No images found in this folder.", isLoading = false) }
                    }
                } catch (e: Exception) {
                    _uiState.update { it.copy(error = "Could not load images.", isLoading = false) }
                }
            } else {
                _uiState.update { it.copy(error = "Folder path not provided.", isLoading = false) }
            }
        }
    }

    private suspend fun loadImageFilesFromFolder(folderPath: String): List<File> = withContext(Dispatchers.IO) {
        try {
            val folder = File(folderPath)
            if (folder.exists() && folder.isDirectory) {
                folder.listFiles { file ->
                    file.isFile && imageExtensions.contains(file.extension.lowercase(Locale.ROOT))
                }?.sortedBy { it.name } ?: emptyList()
            } else {
                Log.w("ImageViewModel", "Folder not found or not a directory: $folderPath")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("ImageViewModel", "Error loading image files from folder: $folderPath", e)
            emptyList()
        }
    }

    fun onPageChanged(index: Int) {
        if (index != _uiState.value.currentImageIndex && index in _uiState.value.imageUris.indices) {
            Log.d("ImageViewModel", "Page changed to index: $index")
            _uiState.update { it.copy(currentImageIndex = index) }
            savedStateHandle["currentImageIndex"] = index
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("ImageViewModel", "onCleared - Final index saved: ${savedStateHandle.get<Int>("currentImageIndex")}")
    }
}