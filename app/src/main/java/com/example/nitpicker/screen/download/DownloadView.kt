package com.example.nitpicker.screen.download

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel // Keep
import androidx.compose.material.icons.filled.Delete // Add
import androidx.compose.material.icons.filled.Error // Keep
import androidx.compose.material.icons.filled.HourglassTop // Keep
import androidx.compose.material.icons.filled.Refresh // Add for Retry
import androidx.compose.material.icons.filled.SyncProblem // Keep
import androidx.compose.material.icons.filled.Download // Keep
import androidx.compose.material.icons.filled.DownloadDone // Keep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf // Add import
import androidx.compose.runtime.remember // Add import
import androidx.compose.runtime.rememberCoroutineScope // Add import
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.compose.runtime.setValue // Add this import

import kotlinx.coroutines.launch // Add import
import com.example.nitpicker.model.DownloadProgress
import com.example.nitpicker.model.DownloadStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    navController: NavController,
    viewModel: DownloadViewModel = hiltViewModel()
) {
    val downloadState by viewModel.downloadState.collectAsState()
    val downloads = downloadState.values.toList()
        .sortedWith(
            compareByDescending<DownloadProgress> { it.status == DownloadStatus.Downloading }
                .thenBy { it.status.ordinal }
                .thenBy { it.fileName }
        )

    // Check if there are any completed or cancelled tasks to enable the delete button
    val canDeleteCleanup = downloads.any { it.status == DownloadStatus.Completed || it.status == DownloadStatus.Cancelled }

    // State to track if navigating back is in progress
    var isNavigatingBack by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope() // Scope for launching navigation

    // Add lifecycle logging for DownloadScreen
    DisposableEffect(Unit) {
        Log.d("CompositionLifecycle", "DownloadScreen Composed")
        onDispose {
            Log.d("CompositionLifecycle", "DownloadScreen Disposed")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            // Prevent multiple clicks
                            if (!isNavigatingBack) {
                                isNavigatingBack = true // Set flag immediately
                                Log.d("NavigationFlow", "[${System.currentTimeMillis()}] DownloadScreen: Back button clicked. Calling popBackStack().")
                                // Launch in a coroutine to allow UI update (disabling button)
                                scope.launch {
                                    try {
                                        // Use popBackStack for standard back behavior
                                        val popped = navController.popBackStack()
                                        Log.d("NavigationFlow", "[${System.currentTimeMillis()}] DownloadScreen: navController.popBackStack() called. Result: $popped")
                                        // If popBackStack fails (e.g., already at start), reset the flag
                                        if (!popped) {
                                            isNavigatingBack = false
                                        }
                                        // Otherwise, the screen will dispose, no need to reset the flag manually here.
                                    } catch (e: Exception) {
                                        Log.e("NavigationFlow", "[${System.currentTimeMillis()}] DownloadScreen: Error calling popBackStack()", e)
                                        isNavigatingBack = false // Reset flag on error
                                    }
                                }
                            } else {
                                Log.d("NavigationFlow", "[${System.currentTimeMillis()}] DownloadScreen: Back button clicked but navigation already in progress.")
                            }
                        },
                        // Disable button while navigating back
                        enabled = !isNavigatingBack
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = { // Add actions section
                    // Show delete button only if there are tasks to clean up
                    if (canDeleteCleanup) {
                        IconButton(onClick = { viewModel.deleteCompletedAndCancelledTasks() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Completed/Cancelled", tint = Color.White)
                        }
                    }
                },
                 colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E1E),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF121212) // Match theme
    ) { paddingValues ->
        if (downloads.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("No active or recent downloads.", color = Color(0xFFAAAAAA))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(downloads, key = { it.id }) { download ->
                    DownloadItem(
                        progress = download,
                        onCancel = { viewModel.cancelDownload(download.id) },
                        onRetry = { viewModel.retryDownload(download.id) } // Pass retry lambda
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadItem(
    progress: DownloadProgress,
    onCancel: () -> Unit,
    onRetry: () -> Unit // Add onRetry parameter
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status Icon
            StatusIcon(progress.status)

            Spacer(Modifier.width(12.dp))

            // File Info and Progress
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = progress.fileName,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = progress.albumTitle,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color(0xFFAAAAAA)
                )
                Spacer(Modifier.height(6.dp))

                // Status specific UI
                when (progress.status) {
                    DownloadStatus.Downloading -> {
                        // ... (existing Downloading UI) ...
                        LinearProgressIndicator(
                            progress = { progress.progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF6D28D9),
                            trackColor = Color(0xFF444444)
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                             Text(
                                text = formatBytes(progress.downloadedBytes) + "/" + formatBytes(progress.totalBytes),
                                fontSize = 11.sp,
                                color = Color(0xFFAAAAAA)
                            )
                            Text(
                                text = "${progress.progressPercent}%",
                                fontSize = 11.sp,
                                color = Color(0xFFAAAAAA)
                            )
                        }
                    }
                    DownloadStatus.Completed -> {
                        // ... (existing Completed UI) ...
                        Text("Completed", fontSize = 12.sp, color = Color.Green)
                        progress.filePath?.let {
                             Text("Saved to: ${it.substringAfterLast('/')}", fontSize = 10.sp, color = Color(0xFFAAAAAA), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    DownloadStatus.Error -> {
                        // ... (existing Error UI) ...
                        Text("Error: ${progress.error ?: "Unknown"}", fontSize = 12.sp, color = Color.Red, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    DownloadStatus.Cancelled -> {
                         // ... (existing Cancelled UI) ...
                         Text("Cancelled", fontSize = 12.sp, color = Color.Yellow)
                    }
                    DownloadStatus.Pending -> {
                         // ... (existing Pending UI) ...
                         Text("Pending...", fontSize = 12.sp, color = Color(0xFFAAAAAA))
                    }
                     DownloadStatus.FetchingUrl -> {
                         // ... (existing FetchingUrl UI) ...
                         Text("Getting download link...", fontSize = 12.sp, color = Color(0xFFAAAAAA))
                    }
                    DownloadStatus.Paused -> {
                         // ... (existing Paused UI) ...
                         Text("Paused", fontSize = 12.sp, color = Color(0xFFAAAAAA))
                    }
                    // else -> { ... } // Optional: handle any other potential statuses
                }
            }

            Spacer(Modifier.width(8.dp))

            // Action Buttons based on status
            Row { // Use a Row to place buttons side-by-side if needed
                when (progress.status) {
                    DownloadStatus.Downloading,
                    DownloadStatus.Pending,
                    DownloadStatus.FetchingUrl -> {
                        // Show Cancel button for active/pending tasks
                        IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Cancel, contentDescription = "Cancel", tint = Color(0xFFCCCCCC))
                        }
                    }
                    DownloadStatus.Error -> {
                        // Show Retry button
                        IconButton(onClick = onRetry, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = Color(0xFF6D28D9)) // Use Refresh icon
                        }
                        // Show Cancel button (to remove the error entry)
                        IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Cancel, contentDescription = "Cancel", tint = Color(0xFFCCCCCC))
                        }
                    }
                    // No buttons for Completed or Cancelled (handled by top bar delete)
                    DownloadStatus.Completed,
                    DownloadStatus.Paused,
                    DownloadStatus.Cancelled -> {
                        // Optionally add a single delete icon per item if preferred over the global one
                        // IconButton(onClick = { /* Call viewModel.deleteTask(progress.id) */ }, modifier = Modifier.size(36.dp)) {
                        //     Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFCCCCCC))
                        // }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusIcon(status: DownloadStatus) {
    // ... (StatusIcon implementation remains the same) ...
    val icon = when(status) {
        DownloadStatus.Downloading -> Icons.Default.Download
        DownloadStatus.Completed -> Icons.Default.DownloadDone
        DownloadStatus.Error -> Icons.Default.Error
        DownloadStatus.Cancelled -> Icons.Default.Cancel
        DownloadStatus.Pending -> Icons.Default.HourglassTop
        DownloadStatus.FetchingUrl -> Icons.Default.SyncProblem
        DownloadStatus.Paused -> Icons.Default.PlayArrow
        // else -> Icons.Default.HourglassTop // Removed else, covered by specific cases
    }
    val tint = when(status) {
         DownloadStatus.Downloading -> Color(0xFF6D28D9)
        DownloadStatus.Completed -> Color.Green
        DownloadStatus.Error -> Color.Red
        DownloadStatus.Cancelled -> Color.Yellow
        DownloadStatus.Paused -> Color.Gray
        DownloadStatus.Pending, DownloadStatus.FetchingUrl -> Color(0xFFAAAAAA) // Grouped pending/fetching
    }
    Icon(icon, contentDescription = status.name, tint = tint)
}

// Helper to format bytes
fun formatBytes(bytes: Long): String {
    // ... (formatBytes implementation remains the same) ...
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.1f GB".format(gb)
}