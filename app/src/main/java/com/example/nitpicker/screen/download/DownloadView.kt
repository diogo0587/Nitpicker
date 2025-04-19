package com.example.nitpicker.screen.download

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.nitpicker.model.DownloadProgress
import com.example.nitpicker.model.DownloadStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    navController: NavController,
    viewModel: DownloadViewModel = hiltViewModel()
) {
    val downloadState by viewModel.downloadState.collectAsState()
    // Sort downloads perhaps by status or time added
    val downloads = downloadState.values.toList()
        .sortedWith(compareBy({ it.status }, { it.fileName })) // Example sorting

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                        onCancel = { viewModel.cancelDownload(download.id) }
                        // Add onRetry if implemented
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
    // onRetry: () -> Unit
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

                when (progress.status) {
                    DownloadStatus.Downloading -> {
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
                        Text("Completed", fontSize = 12.sp, color = Color.Green)
                        progress.filePath?.let {
                             Text("Saved to: ${it.substringAfterLast('/')}", fontSize = 10.sp, color = Color(0xFFAAAAAA), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    DownloadStatus.Error -> {
                        Text("Error: ${progress.error ?: "Unknown"}", fontSize = 12.sp, color = Color.Red, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        // Add Retry Button here if needed
                    }
                    DownloadStatus.Cancelled -> {
                         Text("Cancelled", fontSize = 12.sp, color = Color.Yellow)
                    }
                    DownloadStatus.Pending -> {
                         Text("Pending...", fontSize = 12.sp, color = Color(0xFFAAAAAA))
                    }
                     DownloadStatus.FetchingUrl -> {
                         Text("Getting download link...", fontSize = 12.sp, color = Color(0xFFAAAAAA))
                    }
                    else -> {
                         Text(progress.status.name, fontSize = 12.sp, color = Color(0xFFAAAAAA))
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            // Action Button (Cancel/Retry)
            if (progress.status == DownloadStatus.Downloading || progress.status == DownloadStatus.Pending || progress.status == DownloadStatus.FetchingUrl) {
                IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Cancel, contentDescription = "Cancel", tint = Color(0xFFCCCCCC))
                }
            }
            // Add Retry button for Error status if needed
            // if (progress.status == DownloadStatus.Error) { ... }
        }
    }
}

@Composable
fun StatusIcon(status: DownloadStatus) {
    val icon = when(status) {
        DownloadStatus.Downloading -> Icons.Default.Download
        DownloadStatus.Completed -> Icons.Default.DownloadDone
        DownloadStatus.Error -> Icons.Default.Error
        DownloadStatus.Cancelled -> Icons.Default.Cancel
        DownloadStatus.Pending -> Icons.Default.HourglassTop
        DownloadStatus.FetchingUrl -> Icons.Default.SyncProblem
        else -> Icons.Default.HourglassTop
    }
    val tint = when(status) {
         DownloadStatus.Downloading -> Color(0xFF6D28D9)
        DownloadStatus.Completed -> Color.Green
        DownloadStatus.Error -> Color.Red
        DownloadStatus.Cancelled -> Color.Yellow
        else -> Color(0xFFAAAAAA)
    }
    Icon(icon, contentDescription = status.name, tint = tint)
}

// Helper to format bytes
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.1f GB".format(gb)
}