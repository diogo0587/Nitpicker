package com.d3intran.nitpicker.screen.local_album

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.d3intran.nitpicker.R
import com.d3intran.nitpicker.model.FileType
import com.d3intran.nitpicker.model.LocalFileItem
import com.d3intran.nitpicker.screen.files.FolderItem
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun LocalAlbumScreen(
    navController: NavController,
    viewModel: LocalAlbumViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val gridState = rememberLazyGridState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showNormalActionMenu by remember { mutableStateOf(false) }
    var showSelectionActionMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var moveTargetFolders by remember { mutableStateOf<List<FolderItem>>(emptyList()) }

    BackHandler(enabled = uiState.isSelectionModeActive) {
        Log.d("BackHandler", "Back pressed in selection mode, exiting selection.")
        viewModel.exitSelectionMode()
        showSelectionActionMenu = false
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    DisposableEffect(Unit) {
        Log.d("CompositionLifecycle", "LocalAlbumScreen Composed for path: ${uiState.folderPath}")
        onDispose {
            Log.d("CompositionLifecycle", "LocalAlbumScreen Disposed for path: ${uiState.folderPath}")
        }
    }
    Log.d("LocalAlbumScreenState", "Recomposing. isLoading: ${uiState.isLoading}, error: ${uiState.error}, files: ${uiState.files.size}")

    // --- Set System Bar Color for LocalAlbumScreen ---
    val view = LocalView.current
    val localAlbumBackgroundColor = Color(0xFF121212) // Match Scaffold containerColor
    DisposableEffect(view, localAlbumBackgroundColor) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val originalStatusBarColor = window.statusBarColor
            val controller = WindowCompat.getInsetsController(window, view)
            val wasLightStatusBars = controller?.isAppearanceLightStatusBars ?: false

            window.statusBarColor = localAlbumBackgroundColor.toArgb()
            controller?.isAppearanceLightStatusBars = false // Dark background -> light icons

            onDispose {
                // Optional: Restore previous color/flags
                // window.statusBarColor = originalStatusBarColor
                // controller?.isAppearanceLightStatusBars = wasLightStatusBars
            }
        } else {
            onDispose { }
        }
    }
    // --- End System Bar Color Setting ---

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

    val defaultTitle = stringResource(R.string.local_album_default_title)
    val snackbarNoMoveTarget = stringResource(R.string.local_album_snackbar_no_move_target)
    val snackbarPlayerError = stringResource(R.string.local_album_snackbar_player_error)
    val snackbarIndexError = stringResource(R.string.local_album_snackbar_index_error)
    val snackbarImageViewerNYI = stringResource(R.string.local_album_snackbar_image_viewer_nyi)
    val emptyFolderText = stringResource(R.string.local_album_empty)

    Scaffold(
        modifier = Modifier.statusBarsPadding(),
        topBar = {
            if (uiState.isSelectionModeActive) {
                SelectionTopAppBar(
                    selectedCount = uiState.selectedFileCount,
                    onCancelClick = {
                        Log.d("SelectionTopAppBar", "Cancel clicked, exiting selection.")
                        viewModel.exitSelectionMode()
                        showSelectionActionMenu = false
                    },
                    onActionMenuClick = { showSelectionActionMenu = !showSelectionActionMenu },
                    showMenu = showSelectionActionMenu,
                    onDismissMenu = { showSelectionActionMenu = false },
                    onSelectAllClick = { viewModel.selectAll(); showSelectionActionMenu = false },
                    onMoveClick = {
                        showSelectionActionMenu = false
                        scope.launch {
                            moveTargetFolders = viewModel.getFoldersForMove()
                            if (moveTargetFolders.isNotEmpty()) {
                                showMoveDialog = true
                            } else {
                                snackbarHostState.showSnackbar(snackbarNoMoveTarget)
                            }
                        }
                    },
                    onDeleteClick = { showDeleteConfirmDialog = true; showSelectionActionMenu = false }
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = uiState.folderName.ifEmpty { defaultTitle },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { showNormalActionMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_actions))
                            }
                            DropdownMenu(
                                expanded = showNormalActionMenu,
                                onDismissRequest = { showNormalActionMenu = false },
                                modifier = Modifier.background(Color(0xFF2C2C2C))
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_select), color = Color.White) },
                                    onClick = {
                                        viewModel.enterSelectionMode()
                                        showNormalActionMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_select), tint = Color.White) }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF1E1E1E),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = localAlbumBackgroundColor
    ) { paddingValues -> // Use paddingValues provided by Scaffold for content below TopAppBar
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues) // Apply Scaffold padding
                .padding(horizontal = 8.dp)
        ) {
            Box(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                when {
                    uiState.isLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFF6D28D9))
                        }
                    }
                    uiState.error != null -> {
                        LocalErrorState(
                            errorMessage = uiState.error ?: stringResource(R.string.error_unknown),
                            onRetry = { viewModel.retry() }
                        )
                    }
                    uiState.files.isEmpty() && !uiState.isLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = emptyFolderText,
                                color = Color(0xFFAAAAAA),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    else -> {
                        // Filter and sort images and videos separately for correct indexing
                        val imageFiles = uiState.files
                            .filter { it.type == FileType.IMAGE }
                            .sortedBy { it.name } // Ensure same sorting as ViewModel
                        val videoFiles = uiState.files
                            .filter { it.type == FileType.VIDEO }
                            .sortedBy { it.name } // Ensure same sorting as ViewModel

                        LocalFilesGrid(
                            files = uiState.files, // Pass the original mixed list for display order
                            gridState = gridState,
                            columnCount = columnCount,
                            isSelectionModeActive = uiState.isSelectionModeActive,
                            selectedFilePaths = uiState.selectedFilePaths,
                            onFileClick = { file ->
                                if (uiState.isSelectionModeActive) {
                                    viewModel.toggleSelection(file.path)
                                } else {
                                    val encodedFolderPath = try {
                                        URLEncoder.encode(uiState.folderPath, StandardCharsets.UTF_8.toString())
                                    } catch (e: Exception) {
                                        Log.e("Navigation", "Failed to encode folder path: ${uiState.folderPath}", e)
                                        "" // Handle encoding error
                                    }

                                    if (encodedFolderPath.isEmpty()) {
                                         scope.launch { snackbarHostState.showSnackbar("Error preparing navigation.") }
                                         return@LocalFilesGrid // Stop if encoding failed
                                    }

                                    when (file.type) {
                                        FileType.VIDEO -> {
                                            // Find index in the *sorted video list*
                                            val initialIndex = videoFiles.indexOfFirst { it.path == file.path }
                                            if (initialIndex != -1) {
                                                Log.d("LocalAlbumScreen", "Navigating to player for folder: ${uiState.folderPath}, index: $initialIndex (sorted video list)")
                                                navController.navigate("player_screen/$encodedFolderPath/$initialIndex")
                                            } else {
                                                Log.e("LocalAlbumScreen", "Clicked video not found in filtered/sorted list: ${file.path}")
                                                scope.launch { snackbarHostState.showSnackbar(snackbarIndexError) }
                                            }
                                        }
                                        FileType.IMAGE -> {
                                            // Find index in the *sorted image list*
                                            val initialIndex = imageFiles.indexOfFirst { it.path == file.path }
                                            if (initialIndex != -1) {
                                                Log.d("LocalAlbumScreen", "Navigating to image viewer for folder: ${uiState.folderPath}, index: $initialIndex (sorted image list)")
                                                navController.navigate("image_viewer_screen/$encodedFolderPath/$initialIndex")
                                            } else {
                                                 Log.e("LocalAlbumScreen", "Clicked image not found in filtered/sorted list: ${file.path}")
                                                 scope.launch { snackbarHostState.showSnackbar(snackbarIndexError) }
                                            }
                                        }
                                    }
                                }
                            },
                            onFileLongClick = { file ->
                                if (!uiState.isSelectionModeActive) {
                                    viewModel.enterSelectionMode()
                                }
                                viewModel.toggleSelection(file.path)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        DeleteConfirmationDialog(
            count = uiState.selectedFileCount,
            onDismiss = { showDeleteConfirmDialog = false },
            onConfirm = {
                showDeleteConfirmDialog = false
                viewModel.deleteSelectedFiles()
            }
        )
    }

    if (showMoveDialog) {
        MoveTargetDialog(
            folders = moveTargetFolders,
            onDismiss = { showMoveDialog = false },
            onConfirm = { destinationFolder ->
                showMoveDialog = false
                scope.launch {
                    viewModel.moveSelectedFiles(destinationFolder.path)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopAppBar(
    selectedCount: Int,
    onCancelClick: () -> Unit,
    onActionMenuClick: () -> Unit,
    showMenu: Boolean,
    onDismissMenu: () -> Unit,
    onSelectAllClick: () -> Unit,
    onMoveClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    TopAppBar(
        title = { Text(stringResource(R.string.selected_count, selectedCount)) },
        navigationIcon = {
            IconButton(onClick = onCancelClick) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.local_album_selection_cancel_cd))
            }
        },
        actions = {
            Box {
                IconButton(onClick = onActionMenuClick) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.actions))
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = onDismissMenu,
                    modifier = Modifier.background(Color(0xFF2C2C2C))
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_select_all), color = Color.White) },
                        onClick = onSelectAllClick,
                        leadingIcon = { Icon(Icons.Default.SelectAll, contentDescription = null, tint = Color.White) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_move), color = if (selectedCount > 0) Color.White else Color.Gray) },
                        onClick = onMoveClick,
                        enabled = selectedCount > 0,
                        leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null, tint = if (selectedCount > 0) Color.White else Color.Gray) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete), color = if (selectedCount > 0) Color(0xFFF44336) else Color.Gray) },
                        onClick = onDeleteClick,
                        enabled = selectedCount > 0,
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = if (selectedCount > 0) Color(0xFFF44336) else Color.Gray) }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color(0xFF1E1E1E),
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White,
            actionIconContentColor = Color.White
        )
    )
}

@Composable
fun LocalFilesGrid(
    files: List<LocalFileItem>,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    columnCount: Int,
    isSelectionModeActive: Boolean,
    selectedFilePaths: Set<String>,
    onFileClick: (LocalFileItem) -> Unit,
    onFileLongClick: (LocalFileItem) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columnCount),
        state = gridState,
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(files, key = { it.path }) { file ->
            val isSelected = selectedFilePaths.contains(file.path)
            LocalFileItemRow(
                fileInfo = file,
                isSelected = isSelected,
                onClick = { onFileClick(file) },
                onLongClick = { onFileLongClick(file)
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocalFileItemRow(
    fileInfo: LocalFileItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val cardShape = RoundedCornerShape(8.dp)
    val context = LocalContext.current
    val placeholderPainter = remember { ColorPainter(Color(0xFF3A3A3A)) }
    val errorPainter = remember { ColorPainter(Color(0xFF555555)) }

    val borderColor = if (isSelected) Color(0xFF6D28D9) else Color.Transparent
    val borderWidth = if (isSelected) 2.dp else 0.dp

    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(cardShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
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
                    model = ImageRequest.Builder(context)
                        .data(fileInfo.thumbnailUri ?: fileInfo.path)
                        .crossfade(true)
                        .placeholder(ColorDrawable(android.graphics.Color.DKGRAY))
                        .error(ColorDrawable(android.graphics.Color.GRAY))
                        .build(),
                    placeholder = placeholderPainter,
                    error = errorPainter
                ),
                contentDescription = fileInfo.name,
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
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
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }

                    if (fileInfo.type == FileType.VIDEO && fileInfo.durationMillis != null && fileInfo.durationMillis > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatDuration(fileInfo.durationMillis),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.End
                        )
                    }
                }
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.selected_content_description),
                    tint = Color(0xFF6D28D9),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(20.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.2f))
                )
            }
        }
    }
}

@Composable
fun DeleteConfirmationDialog(
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.local_album_delete_dialog_title)) },
        text = { Text(stringResource(R.string.local_album_delete_dialog_message, count)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_delete), color = Color(0xFFF44336))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        containerColor = Color(0xFF2A2A2A),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFAAAAAA)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveTargetDialog(
    folders: List<FolderItem>,
    onDismiss: () -> Unit,
    onConfirm: (FolderItem) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.local_album_move_dialog_title)) },
        text = {
            if (folders.isEmpty()) {
                Text(stringResource(R.string.local_album_move_dialog_empty), color = Color(0xFFAAAAAA))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(items = folders, key = { folder -> folder.path }) { folder ->
                        ListItem(
                            headlineContent = { Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
                            modifier = Modifier.clickable { onConfirm(folder) },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                                headlineColor = Color.White,
                                leadingIconColor = Color.White
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        containerColor = Color(0xFF2A2A2A),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFAAAAAA)
    )
}

fun formatDuration(millis: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % TimeUnit.HOURS.toMinutes(1)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % TimeUnit.MINUTES.toSeconds(1)

    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

fun formatFileSize(sizeBytes: Long): String {
    if (sizeBytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(sizeBytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", sizeBytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

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
            text = stringResource(R.string.local_album_error_title),
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
            Text(stringResource(R.string.action_retry))
        }
    }
}