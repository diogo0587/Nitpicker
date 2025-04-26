package com.d3intran.nitpicker.screen.player

import android.annotation.SuppressLint
import android.app.Activity
import android.util.Log
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import android.graphics.Color as AndroidColor // Import Android Graphics Color

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@androidx.annotation.OptIn(UnstableApi::class)
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

    val exoPlayer = remember {
        Log.d("PlayerLifecycle", "Creating ExoPlayer instance")
        ExoPlayer.Builder(context).build()
    }

    // --- Add DisposableEffect for System Bar Transparency ---
    DisposableEffect(window, windowInsetsController) {
        if (window == null || windowInsetsController == null) {
            return@DisposableEffect onDispose {}
        }

        // Store original colors/flags to restore later (optional but good practice)
        val originalStatusBarColor = window.statusBarColor
        val originalNavBarColor = window.navigationBarColor
        val wasLightStatusBars = windowInsetsController.isAppearanceLightStatusBars
        val wasLightNavBars = windowInsetsController.isAppearanceLightNavigationBars

        // Make system bars transparent
        window.statusBarColor = Color.Transparent.toArgb()
        window.navigationBarColor = Color.Transparent.toArgb() // Also make nav bar transparent

        // Set system bar icons to light (assuming video content is generally dark)
        windowInsetsController.isAppearanceLightStatusBars = false
        windowInsetsController.isAppearanceLightNavigationBars = false // Adjust if nav bar area is light

        Log.d("SystemUI", "Set system bars to transparent, icons to light.")

        onDispose {
            // Restore original settings when leaving the screen (optional)
            // window.statusBarColor = originalStatusBarColor
            // window.navigationBarColor = originalNavBarColor
            // windowInsetsController.isAppearanceLightStatusBars = wasLightStatusBars
            // windowInsetsController.isAppearanceLightNavigationBars = wasLightNavBars
            // Log.d("SystemUI", "Restored original system bar appearance.")
            // Note: If other screens also manage system bars, explicit restoration might
            // interfere. Often, just letting the next screen set its desired appearance is enough.
        }
    }
    // --- End DisposableEffect ---


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
                if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                    viewModel.saveCurrentPlaybackState(exoPlayer.currentPosition, exoPlayer.playWhenReady, exoPlayer.currentMediaItemIndex)
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                Log.d("ExoPlayerState", "PlayWhenReady changed: $playWhenReady, Reason: $reason")
                viewModel.saveCurrentPlaybackState(exoPlayer.currentPosition, playWhenReady, exoPlayer.currentMediaItemIndex)
                viewModel.updateNavigationButtonStates(exoPlayer.hasNextMediaItem(), exoPlayer.hasPreviousMediaItem())
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                Log.d("ExoPlayerState", "Position discontinuity: Reason $reason, NewPos ${newPosition.positionMs}")
                if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    viewModel.saveCurrentPlaybackState(newPosition.positionMs, exoPlayer.playWhenReady, newPosition.mediaItemIndex)
                }
                viewModel.updateNavigationButtonStates(exoPlayer.hasNextMediaItem(), exoPlayer.hasPreviousMediaItem())
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val newIndex = exoPlayer.currentMediaItemIndex
                Log.d("ExoPlayerState", "MediaItem transition, new item: ${mediaItem?.mediaMetadata?.title}, newIndex: $newIndex, reason: $reason")
                viewModel.onMediaItemTransition(mediaItem, newIndex, reason)
                viewModel.updateNavigationButtonStates(exoPlayer.hasNextMediaItem(), exoPlayer.hasPreviousMediaItem())
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                Log.d("ExoPlayerState", "Timeline changed, Reason: $reason")
                viewModel.updateNavigationButtonStates(exoPlayer.hasNextMediaItem(), exoPlayer.hasPreviousMediaItem())
            }
        }
        exoPlayer.addListener(listener)

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    Log.d("PlayerLifecycle", "ON_STOP: Saving state. Player Playing: ${exoPlayer.isPlaying}")
                    if (exoPlayer.playbackState != Player.STATE_IDLE) {
                        viewModel.saveCurrentPlaybackState(exoPlayer.currentPosition, exoPlayer.playWhenReady, exoPlayer.currentMediaItemIndex)
                    }
                }
                Lifecycle.Event.ON_START -> {
                    Log.d("PlayerLifecycle", "ON_START: Player will resume if playWhenReady is true.")
                }
                Lifecycle.Event.ON_PAUSE -> {
                    Log.d("PlayerLifecycle", "ON_PAUSE: Saving state.")
                    if (exoPlayer.playbackState != Player.STATE_IDLE) {
                        viewModel.saveCurrentPlaybackState(exoPlayer.currentPosition, exoPlayer.playWhenReady, exoPlayer.currentMediaItemIndex)
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            Log.d("PlayerLifecycle", "ON_DISPOSE: Releasing player.")
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
            Log.d("PlayerLifecycle", "Player released.")
        }
    }

    LaunchedEffect(uiState.mediaItems, uiState.currentWindowIndex, uiState.playbackPosition, uiState.playWhenReady) {
        if (uiState.mediaItems.isNotEmpty()) {
            val currentMediaId = exoPlayer.currentMediaItem?.mediaId
            val targetMediaId = uiState.mediaItems.getOrNull(uiState.currentWindowIndex)?.mediaId
            val needsMediaItemSet = exoPlayer.mediaItemCount != uiState.mediaItems.size || currentMediaId != targetMediaId || exoPlayer.currentMediaItemIndex != uiState.currentWindowIndex

            if (needsMediaItemSet) {
                Log.d("PlayerSetup", "Setting media items. Count: ${uiState.mediaItems.size}, Start Index: ${uiState.currentWindowIndex}, Start Pos: ${uiState.playbackPosition}")
                exoPlayer.setMediaItems(uiState.mediaItems, uiState.currentWindowIndex, uiState.playbackPosition)
                exoPlayer.prepare()
            } else {
                Log.d("PlayerSetup", "Media items already set. Seeking if necessary.")
                if (kotlin.math.abs(exoPlayer.currentPosition - uiState.playbackPosition) > 1000) {
                    exoPlayer.seekTo(uiState.currentWindowIndex, uiState.playbackPosition)
                }
            }

            if (exoPlayer.playWhenReady != uiState.playWhenReady) {
                Log.d("PlayerSetup", "Applying playWhenReady state: ${uiState.playWhenReady}")
                exoPlayer.playWhenReady = uiState.playWhenReady
            } else {
                Log.d("PlayerSetup", "playWhenReady state already matches: ${uiState.playWhenReady}")
            }

        } else if (!uiState.isLoading) {
            Log.d("PlayerSetup", "MediaItems list is empty or loading finished with no items, clearing player.")
            exoPlayer.clearMediaItems()
            exoPlayer.stop()
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
                        containerColor = Color.Transparent, // <-- 设置为完全透明
                        titleContentColor = Color.White,      // 保持标题和图标颜色为白色以便在视频上可见
                        navigationIconContentColor = Color.White
                    )
                )
            }
        },
        containerColor = Color.Black
    ) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            val currentUiState = uiState

            when {
                currentUiState.isLoading && currentUiState.mediaItems.isEmpty() -> {
                    CircularProgressIndicator(color = Color.White)
                }
                currentUiState.error != null -> {
                    Text(
                        text = currentUiState.error,
                        color = Color.Red,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                currentUiState.mediaItems.isNotEmpty() -> {
                    AndroidView(
                        factory = { ctx ->
                            Log.d("PlayerView", "AndroidView Factory creating PlayerView")
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = true
                                controllerShowTimeoutMs = 3000
                                setControllerVisibilityListener(
                                    PlayerView.ControllerVisibilityListener { visibility ->
                                        Log.d("PlayerView", "Controller visibility changed: $visibility")
                                        isControllerVisible = visibility == View.VISIBLE
                                    }
                                )
                                // --- Make Controller Background Transparent ---
                                val controlView = findViewById<View>(androidx.media3.ui.R.id.exo_controller) // Find the controller view by ID
                                controlView?.setBackgroundColor(AndroidColor.TRANSPARENT) // Set its background to transparent
                                Log.d("PlayerView", "Controller background set to transparent in factory.")
                                // --- End Transparency Modification ---
                            }
                        },
                        update = { playerView ->
                            Log.d("PlayerView", "AndroidView Update. Player set.")
                            playerView.player = exoPlayer
                            playerView.setControllerVisibilityListener(
                                PlayerView.ControllerVisibilityListener { visibility ->
                                    Log.d("PlayerView", "Controller visibility changed (update): $visibility")
                                    isControllerVisible = visibility == View.VISIBLE
                                }
                            )
                            // --- Make Controller Background Transparent (Update) ---
                            val controlView = playerView.findViewById<View>(androidx.media3.ui.R.id.exo_controller) // Find the controller view again
                            controlView?.setBackgroundColor(AndroidColor.TRANSPARENT) // Ensure transparency on update
                            Log.d("PlayerView", "Controller background set to transparent in update.")
                            // --- End Transparency Modification ---
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                !currentUiState.isLoading && currentUiState.mediaItems.isEmpty() && currentUiState.error == null -> {
                    Text("No video found or playlist empty.", color = Color.Gray)
                }
            }
        }
    }
}