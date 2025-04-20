package com.example.nitpicker.screen.player

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView // 导入 Media3 PlayerView
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

    // --- ExoPlayer 状态管理 ---
    // 使用 remember 来创建和记住 ExoPlayer 实例
    val exoPlayer = remember { ExoPlayer.Builder(context).build() }

    // --- 播放器生命周期处理 ---
    DisposableEffect(exoPlayer, lifecycleOwner, uiState.videoUri) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    Log.d("PlayerLifecycle", "ON_START: Initializing player.")
                    // 当 videoUri 准备好时，在 ON_START 中开始播放
                    if (exoPlayer.playbackState == ExoPlayer.STATE_READY || exoPlayer.playbackState == ExoPlayer.STATE_BUFFERING) {
                        exoPlayer.playWhenReady = true
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    Log.d("PlayerLifecycle", "ON_STOP: Pausing player.")
                    exoPlayer.pause() // 当屏幕不可见时暂停
                }
                // ON_DESTROY 由 onDispose 处理
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        // 当 Uri 可用时设置媒体项
        uiState.videoUri?.let { uri ->
            Log.d("PlayerSetup", "Setting media item: $uri")
            val mediaItem = MediaItem.fromUri(uri)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare() // 准备播放器
            // 如果生命周期已经是 START 或 RESUME，则立即开始播放
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                 exoPlayer.playWhenReady = true
            }
        }

        onDispose {
            Log.d("PlayerLifecycle", "ON_DISPOSE: Releasing player.")
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release() // 释放播放器资源
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Video Player") }, // 或显示视频名称
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black, // 匹配播放器背景
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black // 将背景设置为黑色
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black), // 确保背景是黑色
            contentAlignment = Alignment.Center
        ) {
            if (uiState.videoUri != null) {
                // 使用 AndroidView 嵌入 Media3 PlayerView
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true // 显示默认播放控件
                            // 可选：自定义控制器行为
                            // controllerShowTimeoutMs = 3000
                            // controllerHideOnTouch = true
                        }
                    },
                    modifier = Modifier.fillMaxSize() // 填充可用空间
                )
            } else {
                // Check for error using let
                uiState.error?.let { errorMessage -> // errorMessage is guaranteed non-null here
                    // 显示错误信息
                    Text(
                        text = errorMessage,
                        color = Color.Red,
                        modifier = Modifier.padding(16.dp)
                    )
                } ?: run { // If error is null, show the loading indicator
                    // 加载状态或初始状态 (when videoUri is also null)
                    if (uiState.videoUri == null) { // Add check to only show loading if URI isn't ready
                         CircularProgressIndicator(color = Color.White)
                    }
                    // If videoUri is not null but error is null, the AndroidView will be shown.
                    // This else block might need adjustment based on exact loading logic.
                }
            }
        }
    }
}