package com.example.nitpicker.screen.player

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
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

data class PlayerUiState(
    val videoTitle: String? = null,
    val error: String? = null,
    val playbackPosition: Long = 0L,
    val playWhenReady: Boolean = true,
    val isLoading: Boolean = true,
    val mediaItems: List<MediaItem> = emptyList(),
    val initialWindowIndex: Int = 0,
    val isNextEnabled: Boolean = false,
    val isPreviousEnabled: Boolean = false
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val application: Application,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val videoExtensions = setOf("mp4", "mkv", "avi", "mov", "wmv", "3gp")

    private var restoredPosition: Long = 0L
    private var restoredPlayWhenReady: Boolean = true

    init {
        restoredPosition = savedStateHandle.get<Long>("playbackPosition") ?: 0L
        restoredPlayWhenReady = savedStateHandle.get<Boolean>("playWhenReady") ?: true
        Log.d("PlayerViewModel", "Init - Restored internal state: position=$restoredPosition, playWhenReady=$restoredPlayWhenReady")

        loadPlaylist()
    }

    private fun loadPlaylist() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val encodedPath = savedStateHandle.get<String>("folderPath") ?: ""
            val initialIndex = savedStateHandle.get<Int>("initialIndex") ?: 0
            Log.d("PlayerViewModel", "Received encoded folder path: $encodedPath, initial index: $initialIndex")

            if (encodedPath.isNotEmpty()) {
                try {
                    val folderPath = URLDecoder.decode(encodedPath, StandardCharsets.UTF_8.toString())
                    val videoFiles = loadVideosFromFolder(folderPath)

                    if (videoFiles.isNotEmpty()) {
                        val mediaItems = videoFiles.map { file ->
                            MediaItem.Builder()
                                .setUri(file.toUri())
                                .setMediaId(file.absolutePath)
                                .setMediaMetadata(
                                    androidx.media3.common.MediaMetadata.Builder()
                                        .setTitle(file.name)
                                        .build()
                                )
                                .build()
                        }

                        val validInitialIndex = if (initialIndex in mediaItems.indices) initialIndex else 0

                        val isRestoration = _uiState.value.mediaItems.isNotEmpty()
                        val startPosition = if (isRestoration) restoredPosition else 0L
                        val startPlayWhenReady = if (isRestoration) restoredPlayWhenReady else true

                        Log.d("PlayerViewModel", "Playlist loaded. isRestoration=$isRestoration, startPosition=$startPosition, startPlayWhenReady=$startPlayWhenReady")

                        _uiState.update {
                            it.copy(
                                mediaItems = mediaItems,
                                initialWindowIndex = validInitialIndex,
                                videoTitle = mediaItems.getOrNull(validInitialIndex)?.mediaMetadata?.title?.toString(),
                                isLoading = false,
                                playbackPosition = startPosition,
                                playWhenReady = startPlayWhenReady
                            )
                        }
                        Log.d("PlayerViewModel", "Playlist loaded state updated. Size: ${mediaItems.size}, Index: $validInitialIndex, Pos: $startPosition, PlayReady: $startPlayWhenReady")

                    } else {
                        Log.e("PlayerViewModel", "No video files found in folder: $folderPath")
                        _uiState.update { it.copy(error = "No videos found in this folder.", isLoading = false) }
                    }
                } catch (e: Exception) {
                    Log.e("PlayerViewModel", "Error processing folder path or loading videos", e)
                    _uiState.update { it.copy(error = "Could not load video playlist.", isLoading = false) }
                }
            } else {
                Log.e("PlayerViewModel", "Folder path argument is missing.")
                _uiState.update { it.copy(error = "Folder path not provided.", isLoading = false) }
            }
        }
    }

    private suspend fun loadVideosFromFolder(folderPath: String): List<File> = withContext(Dispatchers.IO) {
        try {
            val folder = File(folderPath)
            if (folder.exists() && folder.isDirectory) {
                folder.listFiles { file ->
                    file.isFile && videoExtensions.contains(file.extension.lowercase(Locale.ROOT))
                }?.sortedBy { it.name } ?: emptyList()
            } else {
                Log.w("PlayerViewModel", "Folder not found or not a directory: $folderPath")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("PlayerViewModel", "Error loading videos from folder: $folderPath", e)
            emptyList()
        }
    }

    fun onMediaItemTransition(currentMediaItem: MediaItem?) {
        val newTitle = currentMediaItem?.mediaMetadata?.title?.toString()
        Log.d("PlayerViewModel", "MediaItem Transition. New Title: $newTitle. Resetting position to 0.")
        restoredPosition = 0L
        savedStateHandle["playbackPosition"] = 0L
        _uiState.update {
            it.copy(
                videoTitle = newTitle,
                playbackPosition = 0L
            )
        }
    }

    fun updateNavigationButtonStates(hasNext: Boolean, hasPrevious: Boolean) {
        _uiState.update { it.copy(isNextEnabled = hasNext, isPreviousEnabled = hasPrevious) }
    }

    fun saveCurrentPlaybackState(position: Long, playWhenReady: Boolean) {
        if (position >= 0) {
            restoredPosition = position
            savedStateHandle["playbackPosition"] = position
        }
        restoredPlayWhenReady = playWhenReady
        savedStateHandle["playWhenReady"] = playWhenReady

        if (_uiState.value.playbackPosition != position || _uiState.value.playWhenReady != playWhenReady) {
            _uiState.update { it.copy(playbackPosition = position, playWhenReady = playWhenReady) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("PlayerViewModel", "onCleared - Final position saved was: $restoredPosition")
    }
}