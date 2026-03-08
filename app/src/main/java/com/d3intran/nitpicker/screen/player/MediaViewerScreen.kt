package com.d3intran.nitpicker.screen.player

import android.app.Activity
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.d3intran.nitpicker.util.MediaSessionHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable
import javax.inject.Inject

@HiltViewModel
class MediaViewerViewModel @Inject constructor(
    private val mediaSessionHolder: MediaSessionHolder
) : ViewModel() {
    val mediaUris: List<String> get() = mediaSessionHolder.mediaUris
    val initialIndex: Int get() = mediaSessionHolder.currentIndex
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MediaViewerScreen(
    navController: NavController,
    mediaUriString: String = "", // fallback for direct navigation
    viewModel: MediaViewerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val view = LocalView.current
    var controlsVisible by remember { mutableStateOf(true) }

    // Use the session list if available, otherwise fallback to single URI
    val mediaList = remember {
        if (viewModel.mediaUris.isNotEmpty()) viewModel.mediaUris
        else if (mediaUriString.isNotEmpty()) listOf(mediaUriString)
        else emptyList()
    }
    val initialPage = remember { viewModel.initialIndex.coerceIn(0, (mediaList.size - 1).coerceAtLeast(0)) }

    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { mediaList.size })

    val window = (view.context as? Activity)?.window
    val windowInsetsController = remember(view, window) {
        window?.let { WindowCompat.getInsetsController(it, view) }
    }

    DisposableEffect(window, windowInsetsController) {
        if (window == null || windowInsetsController == null) return@DisposableEffect onDispose {}
        window.statusBarColor = Color.Transparent.toArgb()
        window.navigationBarColor = Color.Transparent.toArgb()
        windowInsetsController.isAppearanceLightStatusBars = false
        windowInsetsController.isAppearanceLightNavigationBars = false
        onDispose {}
    }

    LaunchedEffect(controlsVisible, windowInsetsController) {
        windowInsetsController?.apply {
            if (controlsVisible) show(WindowInsetsCompat.Type.systemBars())
            else {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    // Current file name derived from pager position
    val currentName = mediaList.getOrNull(pagerState.currentPage)
        ?.substringAfterLast('/') ?: "Media Viewer"

    Scaffold(
        topBar = {
            if (controlsVisible) {
                TopAppBar(
                    title = { Text(currentName, maxLines = 1, color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        },
        containerColor = Color.Black
    ) { _ ->
        if (mediaList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Text("No media to display", color = Color.Gray)
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().background(Color.Black)
            ) { page ->
                val uri = mediaList.getOrNull(page) ?: return@HorizontalPager
                val zoomState = rememberZoomState(maxScale = 5f)

                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(Uri.parse(uri))
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .zoomable(
                                zoomState = zoomState,
                                onTap = { controlsVisible = !controlsVisible }
                            )
                    )
                }
            }
        }
    }
}
