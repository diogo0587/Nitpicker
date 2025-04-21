package com.d3intran.nitpicker.screen.image

import android.app.Activity
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.d3intran.nitpicker.R
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable // Import the zoomable modifier

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ImageViewerScreen(
    navController: NavController,
    viewModel: ImageViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current

    var controlsVisible by remember { mutableStateOf(true) } // Initially show controls

    val window = (view.context as? Activity)?.window
    val windowInsetsController = remember(view, window) {
        window?.let { WindowCompat.getInsetsController(it, view) }
    }

    // Hide/Show System Bars based on controlsVisible state
    LaunchedEffect(controlsVisible, windowInsetsController) {
        windowInsetsController?.apply {
            if (controlsVisible) {
                show(WindowInsetsCompat.Type.systemBars())
            } else {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    // Remember PagerState
    val pagerState = rememberPagerState(
        initialPage = uiState.currentImageIndex,
        pageCount = { uiState.imageUris.size }
    )

    // Update ViewModel when pager state changes (user swipes)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                viewModel.onPageChanged(page)
            }
    }

    // Scroll pager if ViewModel index changes externally (e.g., initial load)
    LaunchedEffect(uiState.currentImageIndex) {
        if (pagerState.currentPage != uiState.currentImageIndex && uiState.currentImageIndex in 0 until pagerState.pageCount) {
            Log.d("ImageViewer", "Scrolling pager to index: ${uiState.currentImageIndex}")
            pagerState.scrollToPage(uiState.currentImageIndex)
        }
    }

    // Handle back press to navigate back
    BackHandler {
        navController.popBackStack()
    }

    Scaffold(
        topBar = {
            if (controlsVisible) {
                TopAppBar(
                    title = {
                        Text(
                            uiState.currentImageName ?: stringResource(R.string.image_viewer_loading),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
                .padding(paddingValues), // Apply padding from Scaffold
            contentAlignment = Alignment.Center
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(color = Color.White)
                }
                uiState.error != null -> {
                    Text(
                        text = uiState.error ?: stringResource(R.string.error_unknown),
                        color = Color.Red,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                uiState.imageUris.isNotEmpty() -> {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { pageIndex ->
                        val imageUri = uiState.imageUris.getOrNull(pageIndex)
                        if (imageUri != null) {
                            // Use Zoomable composable
                            val zoomState = rememberZoomState(maxScale = 5f) // Adjust max zoom level
                            val painter = rememberAsyncImagePainter(
                                model = ImageRequest.Builder(context)
                                    .data(imageUri)
                                    .crossfade(true)
                                    .build()
                            )

                            Image(
                                painter = painter,
                                contentDescription = stringResource(R.string.image_viewer_cd, uiState.imageNames.getOrNull(pageIndex) ?: ""),
                                contentScale = ContentScale.Fit, // Fit within bounds initially
                                modifier = Modifier
                                    .fillMaxSize()
                                    .zoomable(
                                        zoomState = zoomState,
                                        onTap = { controlsVisible = !controlsVisible } // Toggle controls on tap
                                    )
                            )

                            // Show loading indicator while image is loading within the pager item
                            if (painter.state is AsyncImagePainter.State.Loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.align(Alignment.Center),
                                    color = Color.White
                                )
                            }
                            // Optionally show error if image fails to load
                            if (painter.state is AsyncImagePainter.State.Error) {
                                Text(
                                    text = stringResource(R.string.image_viewer_load_error),
                                    color = Color.Gray,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        } else {
                            // Placeholder if URI is somehow null for this index
                            Text(
                                text = stringResource(R.string.image_viewer_invalid_image),
                                color = Color.Gray,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
                else -> {
                    // Case where not loading, no error, but list is empty
                    Text(
                        text = stringResource(R.string.image_viewer_no_images),
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}