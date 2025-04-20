package com.example.nitpicker.screen.player

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject

data class PlayerUiState(
    val videoUri: Uri? = null,
    val error: String? = null
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    application: Application, // 如果以后需要，保留 application
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    init {
        val encodedPath = savedStateHandle.get<String>("videoPath") ?: ""
        Log.d("PlayerViewModel", "Received encoded path: $encodedPath")
        if (encodedPath.isNotEmpty()) {
            try {
                val decodedPath = URLDecoder.decode(encodedPath, StandardCharsets.UTF_8.toString())
                Log.d("PlayerViewModel", "Decoded path: $decodedPath")
                val videoFile = File(decodedPath)
                if (videoFile.exists() && videoFile.isFile) {
                    _uiState.value = PlayerUiState(videoUri = videoFile.toUri())
                    Log.d("PlayerViewModel", "Video URI set: ${videoFile.toUri()}")
                } else {
                    Log.e("PlayerViewModel", "Decoded file path does not exist or is not a file: $decodedPath")
                    _uiState.value = PlayerUiState(error = "Video file not found.")
                }
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error decoding path or creating URI", e)
                _uiState.value = PlayerUiState(error = "Could not load video: Invalid path.")
            }
        } else {
            Log.e("PlayerViewModel", "Video path argument is missing.")
            _uiState.value = PlayerUiState(error = "Video path not provided.")
        }
    }
}