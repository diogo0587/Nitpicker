package com.example.nitpicker.screen.local_album

import android.graphics.drawable.ColorDrawable
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage // Placeholder for missing thumb
import androidx.compose.material.icons.filled.Videocam // Placeholder for video
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.nitpicker.model.FileType
import com.example.nitpicker.model.LocalFileItem
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun LocalAlbumScreen(
    navController: NavController,
    viewModel: LocalAlbumViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val gridState = rememberLazyGridState()

    // State for back navigation debounce
    var isNavigatingBack by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Log composition lifecycle
    DisposableEffect(Unit) {
        Log.d("CompositionLifecycle", "LocalAlbumScreen Composed for path: ${uiState.folderPath}")
        onDispose {
            Log.d("CompositionLifecycle", "LocalAlbumScreen Disposed for path: ${uiState.folderPath}")
        }
    }
    Log.d("LocalAlbumScreenState", "Recomposing. isLoading: ${uiState.isLoading}, error: ${uiState.error}, files: ${uiState.files.size}")


    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val columnCount = remember(windowSizeClass) {
        val windowWidth = windowSizeClass.minWidthDp
        val windowHeight = windowSizeClass.minHeightDp
        val isLandscape = windowWidth > windowHeight
        when {
            windowWidth >= 840 -> if (isLandscape) 6 else 4
            windowWidth >= 600 -> if (isLandscape) 4 else 3
            else -> if (isLandscape) 4 else 2
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.folderName.ifEmpty { "Local Files" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            // Prevent multiple clicks
                            if (!isNavigatingBack) {
                                isNavigatingBack = true // Set flag immediately
                                Log.d("NavigationFlow", "[${System.currentTimeMillis()}] LocalAlbumScreen: Back button clicked. Calling popBackStack().")
                                scope.launch {
                                    try {
                                        val popped = navController.popBackStack()
                                        Log.d("NavigationFlow", "[${System.currentTimeMillis()}] LocalAlbumScreen: navController.popBackStack() called. Result: $popped")
                                        if (!popped) isNavigatingBack = false
                                    } catch (e: Exception) {
                                        Log.e("NavigationFlow", "[${System.currentTimeMillis()}] LocalAlbumScreen: Error calling popBackStack()", e)
                                        isNavigatingBack = false
                                    }
                                }
                            } else {
                                Log.d("NavigationFlow", "[${System.currentTimeMillis()}] LocalAlbumScreen: Back button clicked but navigation already in progress.")
                            }
                        },
                        enabled = !isNavigatingBack
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                // No actions for now
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E1E),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF121212)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 8.dp) // Consistent padding
        ) {
            // No FilterControls needed for local view initially

            Box(modifier = Modifier.weight(1f).padding(top = 8.dp)) { // Add top padding
                when {
                    uiState.isLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFF6D28D9))
                        }
                    }
                    uiState.error != null -> {
                        LocalErrorState( // Use a specific error state if needed, or reuse AlbumView's
                            errorMessage = uiState.error ?: "An unknown error occurred.",
                            onRetry = { viewModel.retry() }
                        )
                    }
                    uiState.files.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "No images or videos found in this folder.",
                                color = Color(0xFFAAAAAA),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    else -> {
                        LocalFilesGrid(
                            files = uiState.files,
                            gridState = gridState,
                            columnCount = columnCount,
                            onFileClick = { file ->
                                Log.d("LocalAlbumScreen", "Clicked on local file: ${file.name}")
                                // No action for now
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LocalFilesGrid(
    files: List<LocalFileItem>,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    columnCount: Int,
    onFileClick: (LocalFileItem) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columnCount),
        state = gridState,
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp), // Consistent padding
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(files, key = { it.path }) { file ->
            LocalFileItemRow(
                fileInfo = file,
                onClick = { onFileClick(file) }
            )
        }
    }
}

@Composable
fun LocalFileItemRow(
    fileInfo: LocalFileItem,
    onClick: () -> Unit
) {
    val cardShape = RoundedCornerShape(8.dp)

    // Determine placeholder icon based on type
    val placeholderIcon: ImageVector = when(fileInfo.type) {
        FileType.IMAGE -> Icons.Filled.BrokenImage
        FileType.VIDEO -> Icons.Filled.Videocam
        else -> Icons.Filled.BrokenImage
    }
    // Create the Painter here
    val placeholderPainter = rememberVectorPainter(image = placeholderIcon)

    // Get context within the @Composable scope
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(cardShape)
            .clickable(onClick = onClick),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Create the ImageRequest using the captured context
            val imageRequest = remember(context, fileInfo.thumbnailUri, fileInfo.path) { // Optionally add context as a key
                ImageRequest.Builder(context) // Use the context variable here
                    .data(fileInfo.thumbnailUri ?: fileInfo.path)
                    .crossfade(true)
                    .build()
            }

            Image(
                painter = rememberAsyncImagePainter(
                    model = imageRequest,
                    placeholder = placeholderPainter,
                    error = placeholderPainter
                ),
                contentDescription = fileInfo.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit // Change from Crop to Fit
            )

            // Bottom overlay with file name and size
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Column {
                    Text(
                        text = fileInfo.name,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatFileSize(fileInfo.size),
                        color = Color(0xFFAAAAAA),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

// Helper to format file size
fun formatFileSize(sizeBytes: Long): String {
    if (sizeBytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(sizeBytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", sizeBytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

// Simple Error State Composable (can reuse/adapt from AlbumView if preferred)
@Composable
fun LocalErrorState(errorMessage: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = Color(0xFFAAAAAA),
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Error Loading Folder",
            color = Color.White,
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = errorMessage,
            color = Color(0xFFAAAAAA),
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6D28D9))
        ) {
            Text("Retry")
        }
    }
}