package com.example.nitpicker.screen.album

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.nitpicker.R
import com.example.nitpicker.model.FileInfo
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.adaptive.currentWindowSize
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.collectLatest

// Helper function to find Activity from Context safely
private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AlbumScreen(
    navController: NavController,
    albumViewModel: AlbumViewModel = hiltViewModel()
) {
    val uiState by albumViewModel.uiState.collectAsState()
    val gridState = rememberLazyGridState()
    val snackbarHostState = remember { SnackbarHostState() }

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    LaunchedEffect(Unit) {
        albumViewModel.snackbarMessages.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val columnCount = if (activity != null) {
        val windowWidth = windowSizeClass.minWidthDp
        val windowHeight = windowSizeClass.minHeightDp
        val isLandscape = windowWidth > windowHeight
        when {
            windowWidth >= 840 -> if (isLandscape) 6 else 4
            windowWidth >= 600 -> if (isLandscape) 4 else 3
            else -> if (isLandscape) 4 else 2
        }
    } else {
        2
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.albumTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val selectedCount = uiState.selectedFileCount
                    val isAnythingSelected = selectedCount > 0

                    // Define colors based on selection state
                    val iconButtonContainerColor = if (isAnythingSelected) {
                        Color(0xFF6D28D9) // Purple background when selected
                    } else {
                        Color.Transparent // No background when not selected
                    }
                    val iconButtonContentColor = Color.White // Icon color remains white

                    // IconButton without BadgedBox
                    IconButton(
                        onClick = {
                            if (isAnythingSelected) {
                                albumViewModel.queueSelectedFilesForDownload()
                            } else {
                                // Navigate to download screen when nothing is selected
                                navController.navigate("download_screen")
                            }
                        },
                        // Apply conditional colors
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = iconButtonContainerColor,
                            contentColor = iconButtonContentColor
                        ),
                        modifier = Modifier.clip(CircleShape) // Optional: Ensure background respects shape if needed
                    ) {
                        Icon(
                            Icons.Filled.Download,
                            contentDescription = if (isAnythingSelected) "Download selected files" else "Open Download Manager"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E1E),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White // Default action icon color
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = Color(0xFF121212)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 8.dp)
        ) {
            FilterControls(
                currentFilter = uiState.currentFilter,
                onFilterSelected = { albumViewModel.setFilter(it) }
            )

            Box(modifier = Modifier.weight(1f)) {
                when {
                    uiState.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFF6D28D9))
                        }
                    }
                    uiState.error != null -> {
                        ErrorState(
                            errorMessage = uiState.error ?: "An unknown error occurred.",
                            onRetry = { albumViewModel.retry() }
                        )
                    }
                    else -> {
                        val filteredFiles = filterFiles(uiState.allFiles, uiState.currentFilter)

                        if (filteredFiles.isEmpty() && uiState.allFiles.isNotEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No files match the current filter.",
                                    color = Color(0xFFAAAAAA),
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else if (filteredFiles.isEmpty() && uiState.allFiles.isEmpty() && !uiState.isLoading) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No files found in this album.",
                                    color = Color(0xFFAAAAAA),
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            FilesGrid(
                                files = filteredFiles,
                                gridState = gridState,
                                columnCount = columnCount,
                                queuedFiles = uiState.queuedFiles,
                                onFileClick = { file -> albumViewModel.toggleFileSelection(file) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterControls(
    currentFilter: FilterType,
    onFilterSelected: (FilterType) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        // 第一个 FilterChip (ALL)
        val isAllSelected = currentFilter == FilterType.ALL
        FilterChip(
            selected = isAllSelected,
            onClick = { onFilterSelected(FilterType.ALL) },
            label = { Text("All") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(0xFF3A2F4C),
                selectedLabelColor = Color.White,
                selectedLeadingIconColor = Color.White,
                containerColor = Color(0xFF2C2C2C),
                labelColor = Color(0xFFAAAAAA)
            ),
            border = FilterChipDefaults.filterChipBorder( // 修改这里
                enabled = true, // 传递 enabled 状态
                selected = isAllSelected, // 传递 selected 状态
                borderColor = Color.Transparent,
                selectedBorderColor = Color.Transparent
                // 其他参数使用默认值或根据需要设置
            )
        )

        // 第二个 FilterChip (IMAGES)
        val isImagesSelected = currentFilter == FilterType.IMAGES
        FilterChip(
            selected = isImagesSelected,
            onClick = { onFilterSelected(FilterType.IMAGES) },
            label = { Text("Images") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(0xFF3A2F4C),
                selectedLabelColor = Color.White,
                selectedLeadingIconColor = Color.White,
                containerColor = Color(0xFF2C2C2C),
                labelColor = Color(0xFFAAAAAA)
            ),
            border = FilterChipDefaults.filterChipBorder( // 修改这里
                enabled = true, // 传递 enabled 状态
                selected = isImagesSelected, // 传递 selected 状态
                borderColor = Color.Transparent,
                selectedBorderColor = Color.Transparent
            )
        )

        // 第三个 FilterChip (OTHER)
        val isOtherSelected = currentFilter == FilterType.OTHER
        FilterChip(
            selected = isOtherSelected,
            onClick = { onFilterSelected(FilterType.OTHER) },
            label = { Text("Other") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(0xFF3A2F4C),
                selectedLabelColor = Color.White,
                selectedLeadingIconColor = Color.White,
                containerColor = Color(0xFF2C2C2C),
                labelColor = Color(0xFFAAAAAA)
            ),
            border = FilterChipDefaults.filterChipBorder( // 修改这里
                enabled = true, // 传递 enabled 状态
                selected = isOtherSelected, // 传递 selected 状态
                borderColor = Color.Transparent,
                selectedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
fun FilesGrid(
    files: List<FileInfo>,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    columnCount: Int,
    queuedFiles: Set<String>,
    onFileClick: (FileInfo) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columnCount),
        state = gridState,
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(files, key = { it.pageUrl }) { file ->
            val isQueued = queuedFiles.contains(file.pageUrl)
            FileItem(
                fileInfo = file,
                isQueued = isQueued,
                onClick = { if (!isQueued) onFileClick(file) }
            )
        }
    }
}

@Composable
fun FileItem(
    fileInfo: FileInfo,
    isQueued: Boolean,
    onClick: () -> Unit
) {
    val cardShape = RoundedCornerShape(8.dp)
    val placeholderColor = Color(0xFF333333)
    val errorColor = Color(0xFF552222)
    val isSelected = fileInfo.isSelected

    val borderColor = when {
        isQueued -> Color.Gray
        isSelected -> Color(0xFF6D28D9)
        else -> Color.Transparent
    }
    val borderWidth = if (isQueued || isSelected) 2.dp else 0.dp

    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(cardShape)
            .clickable(enabled = !isQueued, onClick = onClick)
            .border(
                width = borderWidth,
                color = borderColor,
                shape = cardShape
            ),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = rememberAsyncImagePainter(
                    ImageRequest.Builder(LocalContext.current)
                        .data(fileInfo.thumbnailUrl)
                        .crossfade(true)
                        .placeholder(ColorDrawable(placeholderColor.hashCode()))
                        .error(ColorDrawable(errorColor.hashCode()))
                        .build()
                ),
                contentDescription = fileInfo.fileName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Column {
                    Text(
                        text = fileInfo.fileName,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = fileInfo.fileType.uppercase(),
                            color = Color(0xFFBBBBBB),
                            fontSize = 10.sp,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                        Text(
                            text = fileInfo.fileSize,
                            color = Color(0xFFAAAAAA),
                            fontSize = 10.sp
                        )
                    }
                }
            }

            when {
                isQueued -> {
                    Icon(
                        imageVector = Icons.Filled.DownloadDone,
                        contentDescription = "Queued for download",
                        tint = Color.Gray,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(20.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    )
                }
                isSelected -> {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Selected",
                        tint = Color(0xFF6D28D9),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(20.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    )
                }
            }

            if (isQueued) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                )
            }
        }
    }
}

@Composable
fun ErrorState(errorMessage: String, onRetry: () -> Unit) {
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
            text = "Error Loading Files",
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
