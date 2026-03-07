package com.d3intran.nitpicker.screen.files

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import java.net.URLEncoder
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.foundation.border
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.d3intran.nitpicker.R
import androidx.compose.foundation.layout.WindowInsets // Import WindowInsets
import androidx.compose.foundation.layout.statusBars // Import statusBars
import androidx.compose.foundation.layout.windowInsetsPadding // Import windowInsetsPadding

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FilesScreen(
    navController: NavController,
    viewModel: FilesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Launcher for ACTION_OPEN_DOCUMENT_TREE
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.addSafDirectory(uri)
        }
    }

    // State to track if navigating back is in progress
    var isNavigatingBack by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope() // Scope for launching navigation

    // State for action menu (Remove)
    var showActionMenu by remember { mutableStateOf(false) }
    var selectedFolderForMenu by remember { mutableStateOf<FolderItem?>(null) }

    // State for remove confirmation dialog
    var showRemoveConfirmDialog by remember { mutableStateOf(false) }
    var folderToRemove by remember { mutableStateOf<FolderItem?>(null) }

    val unknownError = stringResource(R.string.error_unknown)
    val errorLoadingFolders = stringResource(R.string.files_error_title)
    val noFoldersFound = stringResource(R.string.files_empty) // "No libraries added yet. Tap + to authorize an album."

    Log.d("FilesScreenState", "Recomposing FilesScreen. isLoading: ${uiState.isLoading}, error: ${uiState.error}, folders: ${uiState.folders.size}")

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                title = { Text(stringResource(R.string.files_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (!isNavigatingBack) {
                                isNavigatingBack = true
                                scope.launch {
                                    try {
                                        val popped = navController.popBackStack()
                                        if (!popped) {
                                            isNavigatingBack = false
                                        }
                                    } catch (e: Exception) {
                                        isNavigatingBack = false
                                    }
                                }
                            }
                        },
                        enabled = !isNavigatingBack
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        // Launch the SAF DocumentTree Picker
                        folderPickerLauncher.launch(null)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Album",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E1E), // Matches status bar color
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White // Ensure action icons are white
                )
            )
        },
        containerColor = Color(0xFF121212) // Main background color
    ) { paddingValues -> // Use paddingValues provided by Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues) // Apply Scaffold padding
                .padding(horizontal = 16.dp)
        ) {
            Box(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                when {
                    uiState.isLoading -> {
                        // Loading indicator
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFF6D28D9)) // Use theme color
                        }
                    }
                    uiState.error != null -> {
                        // Error state with retry button
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = errorLoadingFolders,
                                color = Color.White
                            )
                            Text(
                                text = uiState.error ?: unknownError,
                                color = Color(0xFFAAAAAA),
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.retry() }, // Call retry on ViewModel
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF6D28D9)
                                )
                            ) {
                                Text(stringResource(R.string.action_retry))
                            }
                        }
                    }
                    uiState.folders.isEmpty() -> {
                        // No folders found state
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = noFoldersFound,
                                color = Color(0xFFAAAAAA)
                            )
                        }
                    }
                    else -> {
                        // Display folder list
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp) // Spacing like HomeView AlbumItem
                        ) {
                            items(
                                items = uiState.folders,
                                key = { folder -> folder.path } // Use path as unique key
                            ) { folder ->
                                FolderItemRow(
                                    folder = folder,
                                    isSelected = selectedFolderForMenu == folder, // Highlight based on the menu being open for this item
                                    onFolderClick = { clickedFolder ->
                                        // Close menu if open before navigating
                                        if (showActionMenu) {
                                            showActionMenu = false
                                            selectedFolderForMenu = null
                                        }
                                        Log.d("FilesScreen", "Clicked on folder: ${clickedFolder.name}, Path: ${clickedFolder.path}")
                                        try {
                                            // URI.encode the path before navigating for proper SAF URI preservation
                                            val encodedPath = Uri.encode(clickedFolder.path)
                                            navController.navigate("local_album_screen/$encodedPath")
                                        } catch (e: Exception) {
                                            Log.e("FilesScreen", "Error encoding path or navigating", e)
                                            // Optionally show a snackbar or toast about the error
                                        }
                                    },
                                    onFolderLongClick = { longClickedFolder ->
                                        Log.d("FilesScreen", "Long clicked on folder: ${longClickedFolder.name}")
                                        selectedFolderForMenu = longClickedFolder
                                        showActionMenu = true // Show the action menu
                                    },
                                    showMenu = showActionMenu && selectedFolderForMenu == folder, // Pass menu state and actions
                                    onDismissMenu = {
                                        showActionMenu = false
                                        selectedFolderForMenu = null
                                    },
                                    onRemoveClick = {
                                        folderToRemove = folder
                                        showRemoveConfirmDialog = true
                                        showActionMenu = false
                                        selectedFolderForMenu = null
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Remove Confirmation Dialog
    if (showRemoveConfirmDialog && folderToRemove != null) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirmDialog = false; folderToRemove = null },
            title = { Text("Remove Library") },
            text = { Text("Are you sure you want to remove '${folderToRemove!!.name}' from your material base? The local files will NOT be deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeFolder(folderToRemove!!.path)
                        showRemoveConfirmDialog = false
                        folderToRemove = null
                    }
                ) {
                    Text("Remove", color = Color(0xFFF44336))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirmDialog = false; folderToRemove = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            containerColor = Color(0xFF2A2A2A),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFAAAAAA)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderItemRow(
    folder: FolderItem,
    isSelected: Boolean,
    onFolderClick: (FolderItem) -> Unit,
    onFolderLongClick: (FolderItem) -> Unit,
    showMenu: Boolean,
    onDismissMenu: () -> Unit,
    onRemoveClick: () -> Unit
) {
    val borderModifier = if (isSelected) {
        Modifier.border(BorderStroke(1.dp, Color(0xFF6D28D9)), RoundedCornerShape(12.dp))
    } else {
        Modifier
    }

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .then(borderModifier)
                .combinedClickable(
                    onClick = { onFolderClick(folder) },
                    onLongClick = { onFolderLongClick(folder) }
                ),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1E1E)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = stringResource(R.string.folder),
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text(
                        text = folder.name,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Dropdown Menu anchored to the Box
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = onDismissMenu,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .background(Color(0xFF2C2C2C)),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp
        ) {
            DropdownMenuItem(
                text = { Text("Remove Library", color = Color(0xFFF44336)) },
                onClick = onRemoveClick
            )
        }
    }
}