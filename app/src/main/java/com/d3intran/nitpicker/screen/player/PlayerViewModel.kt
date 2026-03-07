package com.d3intran.nitpicker.screen.player

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.d3intran.nitpicker.util.SafDirectoryViewer
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val currentWindowIndex: Int = 0,
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

    init {
        loadPlaylist()
    }

    private fun loadPlaylist() {
        viewModelScope.launch {
            if (_uiState.value.mediaItems.isEmpty()) {
                _uiState.update { it.copy(isLoading = true, error = null) }
            } else {
                _uiState.update { it.copy(isLoading = false, error = null) }
            }

            val encodedPath = savedStateHandle.get<String>("folderPath") ?: ""
            val navInitialIndex = savedStateHandle.get<Int>("initialIndex") ?: -1
            val restoredIndex = savedStateHandle.get<Int>("currentWindowIndex") ?: -1
            val restoredPosition = savedStateHandle.get<Long>("playbackPosition") ?: 0L
            val restoredPlayWhenReady = savedStateHandle.get<Boolean>("playWhenReady") ?: true

            Log.d("PlayerViewModel", "loadPlaylist - Received encoded URI: $encodedPath, navInitialIndex: $navInitialIndex, restoredIndex: $restoredIndex")

            if (encodedPath.isNotEmpty()) {
                try {
                    val folderUriString = encodedPath
                    val folderUri = Uri.parse(folderUriString)
                    
                    // Use SafDirectoryViewer to load videos from the URI
                    val videoFiles = SafDirectoryViewer.listFilesFromUri(application, folderUri)
                        .filter { it.type == com.d3intran.nitpicker.model.FileType.VIDEO }

                    if (videoFiles.isNotEmpty()) {
                        val mediaItems = videoFiles.map { localFile ->
                            MediaItem.Builder()
                                .setUri(Uri.parse(localFile.path))
                                .setMediaId(localFile.path)
                                .setMediaMetadata(
                                    androidx.media3.common.MediaMetadata.Builder()
                                        .setTitle(localFile.name)
                                        .build()
                                )
                                .build()
                        }

                        val finalStartIndex = if (navInitialIndex in mediaItems.indices) {
                            Log.d("PlayerViewModel", "Using navigation initial index: $navInitialIndex")
                            navInitialIndex
                        } else if (restoredIndex in mediaItems.indices) {
                            Log.d("PlayerViewModel", "Navigation index invalid, using restored index: $restoredIndex")
                            restoredIndex
                        } else if (mediaItems.isNotEmpty()) {
                            Log.d("PlayerViewModel", "Both indices invalid, defaulting to 0")
                            0
                        } else {
                            Log.w("PlayerViewModel", "Indices invalid and list empty.")
                            -1
                        }

                        if (finalStartIndex != -1) {
                            val isRestoring = finalStartIndex == restoredIndex && restoredIndex != -1
                            val startPosition = if (isRestoring && savedStateHandle.contains("playbackPosition")) {
                                Log.d("PlayerViewModel", "Keeping restored position: $restoredPosition")
                                restoredPosition
                            } else {
                                Log.d("PlayerViewModel", "Resetting position to 0.")
                                0L
                            }
                            val startPlayWhenReady = if (isRestoring && savedStateHandle.contains("playWhenReady")) {
                                restoredPlayWhenReady
                            } else {
                                true
                            }

                            Log.d("PlayerViewModel", "Playlist loaded. FinalStartIndex: $finalStartIndex, StartPos: $startPosition, PlayReady: $startPlayWhenReady")

                            _uiState.update {
                                it.copy(
                                    mediaItems = mediaItems,
                                    currentWindowIndex = finalStartIndex,
                                    videoTitle = mediaItems.getOrNull(finalStartIndex)?.mediaMetadata?.title?.toString(),
                                    isLoading = false,
                                    playbackPosition = startPosition,
                                    playWhenReady = startPlayWhenReady
                                )
                            }
                            savedStateHandle["currentWindowIndex"] = finalStartIndex
                            savedStateHandle["playbackPosition"] = startPosition
                            savedStateHandle["playWhenReady"] = startPlayWhenReady

                        } else {
                            _uiState.update { it.copy(error = "No videos found.", isLoading = false, mediaItems = emptyList()) }
                        }

                    } else {
                        _uiState.update { it.copy(error = "No videos found in this folder.", isLoading = false, mediaItems = emptyList()) }
                    }
                } catch (e: Exception) {
                    _uiState.update { it.copy(error = "Could not load video playlist.", isLoading = false, mediaItems = emptyList()) }
                }
            } else {
                _uiState.update { it.copy(error = "Folder path not provided.", isLoading = false, mediaItems = emptyList()) }
            }
        }
    }

    fun onMediaItemTransition(currentMediaItem: MediaItem?, newIndex: Int, reason: Int) {
        val newTitle = currentMediaItem?.mediaMetadata?.title?.toString()
        Log.d("PlayerViewModel", "MediaItem Transition. New Title: $newTitle, New Index: $newIndex, Reason: $reason")

        _uiState.update {
            it.copy(
                videoTitle = newTitle,
                currentWindowIndex = newIndex,
                playbackPosition = if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) 0L else it.playbackPosition
            )
        }
        savedStateHandle["currentWindowIndex"] = newIndex
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
            savedStateHandle["playbackPosition"] = 0L
        }
    }

    fun updateNavigationButtonStates(hasNext: Boolean, hasPrevious: Boolean) {
        _uiState.update { it.copy(isNextEnabled = hasNext, isPreviousEnabled = hasPrevious) }
    }

    fun saveCurrentPlaybackState(position: Long, playWhenReady: Boolean, currentWindowIndex: Int) {
        Log.d("PlayerViewModel", "Saving state: Pos=$position, PlayReady=$playWhenReady, Index=$currentWindowIndex")
        if (position >= 0) {
            savedStateHandle["playbackPosition"] = position
        }
        savedStateHandle["playWhenReady"] = playWhenReady
        savedStateHandle["currentWindowIndex"] = currentWindowIndex

        if (_uiState.value.playbackPosition != position ||
            _uiState.value.playWhenReady != playWhenReady ||
            _uiState.value.currentWindowIndex != currentWindowIndex) {
            _uiState.update {
                it.copy(
                    playbackPosition = position,
                    playWhenReady = playWhenReady,
                    currentWindowIndex = currentWindowIndex
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("PlayerViewModel", "onCleared - Final state saved: Pos=${savedStateHandle.get<Long>("playbackPosition")}, PlayReady=${savedStateHandle.get<Boolean>("playWhenReady")}, Index=${savedStateHandle.get<Int>("currentWindowIndex")}")
    }
}