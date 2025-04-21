package com.d3intran.nitpicker.screen.files // Corrected package name

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add // Import Add icon
import androidx.compose.material.icons.filled.Folder // Icon for folders/ Import for menu icon (optional)
import androidx.compose.material3.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource // Import stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp // Import for sp unit
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import androidx.compose.runtime.mutableStateOf // Add import
import androidx.compose.runtime.remember // Add import
import androidx.compose.runtime.rememberCoroutineScope // Add import
import androidx.compose.runtime.setValue // Add import
import kotlinx.coroutines.launch // Add import
import java.net.URLEncoder // Import for encoding
import androidx.compose.foundation.ExperimentalFoundationApi // Add for combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable // Add for combinedClickable
import androidx.compose.material3.AlertDialog // Add for Dialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon // Import Icon
import androidx.compose.material3.IconButton // Import IconButton
import androidx.compose.material3.OutlinedTextField // Add for TextField
import androidx.compose.material3.TextButton // Add for Dialog buttons
import androidx.compose.foundation.border // Add import for border
import androidx.compose.material3.OutlinedTextFieldDefaults // Add import for TextField colors
import com.d3intran.nitpicker.R // Import R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FilesScreen(
    navController: NavController,
    viewModel: FilesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // State to track if navigating back is in progress
    var isNavigatingBack by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope() // Scope for launching navigation

    // State for rename dialog
    var showRenameDialog by remember { mutableStateOf(false) }
    var folderToRename by remember { mutableStateOf<FolderItem?>(null) }

    // State for action menu (Rename/Delete)
    var showActionMenu by remember { mutableStateOf(false) }
    var selectedFolderForMenu by remember { mutableStateOf<FolderItem?>(null) }

    // State for delete confirmation dialog
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var folderToDelete by remember { mutableStateOf<FolderItem?>(null) }

    // State for create folder dialog
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var createFolderDialogError by remember { mutableStateOf<String?>(null) }

    val unknownError = stringResource(R.string.error_unknown)
    val errorLoadingFolders = stringResource(R.string.files_error_title)
    val noFoldersFound = stringResource(R.string.files_empty)

    // Log composition lifecycle
    DisposableEffect(Unit) {
        Log.d("CompositionLifecycle", "FilesScreen Composed")
        onDispose {
            Log.d("CompositionLifecycle", "FilesScreen Disposed")
        }
    }
    // Log state on recomposition
    Log.d("FilesScreenState", "Recomposing FilesScreen. isLoading: ${uiState.isLoading}, error: ${uiState.error}, folders: ${uiState.folders.size}")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.files_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            // Prevent multiple clicks
                            if (!isNavigatingBack) {
                                isNavigatingBack = true // Set flag immediately
                                Log.d("NavigationFlow", "[${System.currentTimeMillis()}] FilesScreen: Back button clicked. Calling popBackStack().")
                                // Launch in a coroutine to allow UI update (disabling button)
                                scope.launch {
                                    try {
                                        // Use popBackStack for standard back behavior
                                        val popped = navController.popBackStack()
                                        Log.d("NavigationFlow", "[${System.currentTimeMillis()}] FilesScreen: navController.popBackStack() called. Result: $popped")
                                        // If popBackStack fails, reset the flag
                                        if (!popped) {
                                            isNavigatingBack = false
                                        }
                                        // Otherwise, screen disposal handles it.
                                    } catch (e: Exception) {
                                        Log.e("NavigationFlow", "[${System.currentTimeMillis()}] FilesScreen: Error calling popBackStack()", e)
                                        isNavigatingBack = false // Reset flag on error
                                    }
                                }
                            } else {
                                Log.d("NavigationFlow", "[${System.currentTimeMillis()}] FilesScreen: Back button clicked but navigation already in progress.")
                            }
                        },
                        // Disable button while navigating back
                        enabled = !isNavigatingBack
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        createFolderDialogError = null // Clear previous error when opening dialog
                        showCreateFolderDialog = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.files_create_folder_cd),
                            tint = Color.White // Ensure icon is visible
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E1E),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White // Ensure action icons are white
                )
            )
        },
        containerColor = Color(0xFF121212) // Dark background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp) // Add horizontal padding like HomeView
        ) {
            // Main content area using Box and when, similar to HomeView
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
                                            // URL-encode the path before navigating
                                            val encodedPath = URLEncoder.encode(clickedFolder.path, "UTF-8")
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
                                    onRenameClick = {
                                        folderToRename = folder // Set folder for rename dialog
                                        showRenameDialog = true // Show rename dialog
                                        showActionMenu = false // Dismiss menu
                                        selectedFolderForMenu = null
                                    },
                                    onDeleteClick = {
                                        folderToDelete = folder // Set folder for delete dialog
                                        showDeleteConfirmDialog = true // Show delete confirmation
                                        showActionMenu = false // Dismiss menu
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

    // Rename Dialog (remains the same, triggered by onRenameClick)
    if (showRenameDialog && folderToRename != null) {
        RenameFolderDialog(
            currentName = folderToRename!!.name,
            onDismiss = { showRenameDialog = false; folderToRename = null },
            onConfirm = { newName ->
                viewModel.renameFolder(folderToRename!!.path, newName)
                showRenameDialog = false
                folderToRename = null
            }
        )
    }

    // Delete Confirmation Dialog (triggered by onDeleteClick)
    if (showDeleteConfirmDialog && folderToDelete != null) {
        DeleteConfirmationDialog(
            folderName = folderToDelete!!.name,
            onDismiss = { showDeleteConfirmDialog = false; folderToDelete = null },
            onConfirm = {
                viewModel.deleteFolder(folderToDelete!!.path)
                showDeleteConfirmDialog = false
                folderToDelete = null
            }
        )
    }

    // Create Folder Dialog (triggered by TopAppBar action)
    if (showCreateFolderDialog) {
        CreateFolderDialog(
            dialogError = createFolderDialogError,
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { newFolderName ->
                scope.launch {
                    try {
                        createFolderDialogError = null // Clear error before attempting
                        viewModel.createFolder(newFolderName)
                        showCreateFolderDialog = false // Close dialog on success
                    } catch (e: FolderAlreadyExistsException) {
                        createFolderDialogError = e.message
                    } catch (e: InvalidFolderNameException) {
                        createFolderDialogError = e.message
                    } catch (e: Exception) {
                        Log.e("FilesScreen", "Error creating folder: ${e.message}", e)
                        showCreateFolderDialog = false // Close dialog on other errors
                    }
                }
            }
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
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit
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
                .background(Color(0xFF2C2C2C)), // Keep background modifier separate
            shadowElevation = 0.dp,
            tonalElevation = 0.dp
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_rename), color = Color.White) },
                onClick = onRenameClick
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete), color = Color(0xFFF44336)) },
                onClick = onDeleteClick
            )
        }
    }
}

// Composable for the Rename Dialog
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenameFolderDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newName by remember(currentName) { mutableStateOf(currentName) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val invalidNameError = stringResource(R.string.files_rename_dialog_invalid)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.files_rename_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = newName,
                    onValueChange = {
                        newName = it
                        errorText = if (it.isBlank() || it.contains("/") || it.contains("\\")) {
                            invalidNameError
                        } else {
                            null
                        }
                    },
                    label = { Text(stringResource(R.string.files_rename_dialog_label)) },
                    singleLine = true,
                    isError = errorText != null,
                    // Customize colors for dark background
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, // Text color when focused
                        unfocusedTextColor = Color.White, // Text color when unfocused
                        cursorColor = Color(0xFF6D28D9), // Cursor color
                        focusedBorderColor = Color(0xFF6D28D9), // Border color when focused
                        unfocusedBorderColor = Color.Gray, // Border color when unfocused
                        focusedLabelColor = Color.White, // Label color when focused
                        unfocusedLabelColor = Color.Gray, // Label color when unfocused
                        errorBorderColor = MaterialTheme.colorScheme.error, // Error border color
                        errorLabelColor = MaterialTheme.colorScheme.error // Error label color
                    )
                )
                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (errorText == null && newName.isNotBlank()) { // Double check validity
                        onConfirm(newName.trim()) // Trim whitespace before confirming
                    }
                },
                enabled = errorText == null && newName.isNotBlank(),
                // Add padding to make button larger
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(stringResource(R.string.action_rename), fontSize = 16.sp) // Slightly larger font size
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                // Add padding to make button larger
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(stringResource(R.string.action_cancel), fontSize = 16.sp) // Slightly larger font size
            }
        },
        containerColor = Color(0xFF2A2A2A),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFAAAAAA) // Keep default text color for general dialog text
    )
}

// Add Delete Confirmation Dialog
@Composable
fun DeleteConfirmationDialog(
    folderName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.files_delete_dialog_title)) },
        text = { Text(stringResource(R.string.files_delete_dialog_message, folderName)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                // Use red color for confirmation button text
                Text(stringResource(R.string.action_delete), color = Color(0xFFF44336), fontSize = 16.sp)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(stringResource(R.string.action_cancel), fontSize = 16.sp)
            }
        },
        containerColor = Color(0xFF2A2A2A),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFAAAAAA)
    )
}

// Composable for the Create Folder Dialog
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateFolderDialog(
    dialogError: String?, // Receive the specific error message
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }
    var textFieldError by remember { mutableStateOf<String?>(null) }
    val nameEmptyError = stringResource(R.string.files_create_dialog_empty)
    val combinedError = dialogError ?: textFieldError // Prioritize external error

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.files_create_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = {
                        folderName = it
                        textFieldError = if (it.isBlank()) {
                            nameEmptyError
                        } else {
                            null
                        }
                    },
                    label = { Text(stringResource(R.string.files_create_dialog_label)) },
                    singleLine = true,
                    isError = combinedError != null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFF6D28D9),
                        focusedBorderColor = Color(0xFF6D28D9),
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.Gray,
                        errorBorderColor = MaterialTheme.colorScheme.error,
                        errorLabelColor = MaterialTheme.colorScheme.error
                    )
                )
                if (combinedError != null) {
                    Text(
                        text = combinedError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (textFieldError == null && folderName.isNotBlank()) {
                        onConfirm(folderName.trim())
                    } else if (folderName.isBlank()) {
                        textFieldError = nameEmptyError
                    }
                },
                enabled = folderName.isNotBlank(),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(stringResource(R.string.action_create), fontSize = 16.sp)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(stringResource(R.string.action_cancel), fontSize = 16.sp)
            }
        },
        containerColor = Color(0xFF2A2A2A),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFAAAAAA)
    )
}