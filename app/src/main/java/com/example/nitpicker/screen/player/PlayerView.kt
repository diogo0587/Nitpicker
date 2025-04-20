package com.example.nitpicker.screen.player

import android.app.Activity
import android.util.Log
import android.view.View
import com.example.nitpicker.screen.player.PlayerViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    navController: NavController,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current

    var isControllerVisible by remember { mutableStateOf(false) }

    val window = (view.context as? Activity)?.window
    val windowInsetsController = remember(view, window) {
        window?.let { WindowCompat.getInsetsController(it, view) }
    }

    val exoPlayer = remember { ExoPlayer.Builder(context).build() }

    LaunchedEffect(isControllerVisible, windowInsetsController) {
        if (windowInsetsController == null) return@LaunchedEffect
        if (isControllerVisible) {
            Log.d("SystemUI", "Controller visible, showing system bars.")
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        } else {
            Log.d("SystemUI", "Controller hidden, hiding system bars.")
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            windowInsetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    DisposableEffect(exoPlayer, lifecycleOwner, viewModel) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                Log.d("ExoPlayerState", "Playback state changed: $playbackState")
                viewModel.updateNavigationButtonStates(exoPlayer.hasNextMediaItem(), exoPlayer.hasPreviousMediaItem())
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (exoPlayer.playbackState != Player.STATE_IDLE && exoPlayer.playbackState != Player.STATE_ENDED) {
                    viewModel.saveCurrentPlaybackState(exoPlayer.currentPosition, playWhenReady)
                }
                viewModel.updateNavigationButtonStates(exoPlayer.hasNextMediaItem(), exoPlayer.hasPreviousMediaItem())
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    viewModel.saveCurrentPlaybackState(newPosition.positionMs, exoPlayer.playWhenReady)
                }
                viewModel.updateNavigationButtonStates(exoPlayer.hasNextMediaItem(), exoPlayer.hasPreviousMediaItem())
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                Log.d("ExoPlayerState", "MediaItem transition, new item: ${mediaItem?.mediaMetadata?.title}, reason: $reason")
                viewModel.onMediaItemTransition(mediaItem)
                viewModel.updateNavigationButtonStates(exoPlayer.hasNextMediaItem(), exoPlayer.hasPreviousMediaItem())
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                viewModel.updateNavigationButtonStates(exoPlayer.hasNextMediaItem(), exoPlayer.hasPreviousMediaItem())
            }
        }
        exoPlayer.addListener(listener)

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    Log.d("PlayerLifecycle", "ON_STOP: Saving state and pausing.")
                    if (exoPlayer.playbackState != Player.STATE_IDLE && exoPlayer.playbackState != Player.STATE_ENDED) {
                        viewModel.saveCurrentPlaybackState(exoPlayer.currentPosition, exoPlayer.playWhenReady)
                    }
                    if (exoPlayer.isPlaying) {
                        exoPlayer.pause()
                    }
                }
                Lifecycle.Event.ON_START -> {
                    Log.d("PlayerLifecycle", "ON_START: Player will resume if playWhenReady is true.")
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            Log.d("PlayerLifecycle", "ON_DISPOSE: Saving final state and releasing player.")
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.removeListener(listener)
            if (exoPlayer.playbackState != Player.STATE_IDLE) {
                try {
                    viewModel.saveCurrentPlaybackState(exoPlayer.currentPosition, exoPlayer.playWhenReady)
                } catch (e: Exception) {
                    Log.e("PlayerLifecycle", "Error saving state on dispose", e)
                }
            }
            exoPlayer.release()
            Log.d("PlayerLifecycle", "Player released.")
        }
    }

    LaunchedEffect(uiState.mediaItems, uiState.initialWindowIndex, exoPlayer) {
        if (uiState.mediaItems.isNotEmpty()) {
            Log.d("PlayerSetup", "Setting media items. Count: ${uiState.mediaItems.size}, Start Index: ${uiState.initialWindowIndex}, Start Pos: ${uiState.playbackPosition}")
            exoPlayer.setMediaItems(uiState.mediaItems, uiState.initialWindowIndex, uiState.playbackPosition)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = uiState.playWhenReady
            Log.d("PlayerSetup", "Player prepared. playWhenReady set to: ${uiState.playWhenReady}")
        } else if (!uiState.isLoading) {
            Log.d("PlayerSetup", "MediaItems list is empty, clearing player.")
            exoPlayer.clearMediaItems()
        }
    }

    Scaffold(
        topBar = {
            if (isControllerVisible) {
                TopAppBar(
                    title = {
                        Text(
                            uiState.videoTitle ?: "Loading...",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.5f),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            }
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            val currentUiState = uiState
            val currentError = currentUiState.error

            when {
                currentUiState.isLoading -> {
                    CircularProgressIndicator(color = Color.White)
                }
                currentError != null -> {
                    Text(
                        text = currentError,
                        color = Color.Red,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                currentUiState.mediaItems.isNotEmpty() -> {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = true
                                setControllerVisibilityListener(
                                    PlayerView.ControllerVisibilityListener { visibility ->
                                        Log.d("PlayerView", "Controller visibility changed: $visibility")
                                        isControllerVisible = visibility == View.VISIBLE
                                    }
                                )
                            }
                        },
                        update = { playerView ->
                            playerView.player = exoPlayer
                            playerView.setControllerVisibilityListener(
                                PlayerView.ControllerVisibilityListener { visibility ->
                                    Log.d("PlayerView", "Controller visibility changed (update): $visibility")
                                    isControllerVisible = visibility == View.VISIBLE
                                }
                            )
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    Text("No video found or playlist empty.", color = Color.Gray)
                }
            }
        }
    }
}