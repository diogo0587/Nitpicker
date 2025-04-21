package com.d3intran.nitpicker.screen.download

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import com.d3intran.nitpicker.R
import com.d3intran.nitpicker.model.DownloadProgress
import com.d3intran.nitpicker.model.DownloadStatus

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

    val canDeleteCleanup = downloads.any { it.status == DownloadStatus.Completed || it.status == DownloadStatus.Cancelled }

    var isNavigatingBack by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val emptyText = stringResource(R.string.download_empty)

    DisposableEffect(Unit) {
        Log.d("CompositionLifecycle", "DownloadScreen Composed")
        onDispose {
            Log.d("CompositionLifecycle", "DownloadScreen Disposed")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.download_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (!isNavigatingBack) {
                                isNavigatingBack = true
                                Log.d("NavigationFlow", "[${System.currentTimeMillis()}] DownloadScreen: Back button clicked. Calling popBackStack().")
                                scope.launch {
                                    try {
                                        val popped = navController.popBackStack()
                                        Log.d("NavigationFlow", "[${System.currentTimeMillis()}] DownloadScreen: navController.popBackStack() called. Result: $popped")
                                        if (!popped) {
                                            isNavigatingBack = false
                                        }
                                    } catch (e: Exception) {
                                        Log.e("NavigationFlow", "[${System.currentTimeMillis()}] DownloadScreen: Error calling popBackStack()", e)
                                        isNavigatingBack = false
                                    }
                                }
                            } else {
                                Log.d("NavigationFlow", "[${System.currentTimeMillis()}] DownloadScreen: Back button clicked but navigation already in progress.")
                            }
                        },
                        enabled = !isNavigatingBack
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (canDeleteCleanup) {
                        IconButton(onClick = { viewModel.deleteCompletedAndCancelledTasks() }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.download_delete_completed_cancelled), tint = Color.White)
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
        containerColor = Color(0xFF121212)
    ) { paddingValues ->
        if (downloads.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(emptyText, color = Color(0xFFAAAAAA))
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
                        onRetry = { viewModel.retryDownload(download.id) }
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
    onRetry: () -> Unit
) {
    val unknownError = stringResource(R.string.error_unknown)
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
            StatusIcon(progress.status)

            Spacer(Modifier.width(12.dp))

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
                        Text(stringResource(R.string.download_status_completed), fontSize = 12.sp, color = Color.Green)
                        progress.filePath?.let {
                            Text(stringResource(R.string.download_status_saved_to, it.substringAfterLast('/')), fontSize = 10.sp, color = Color(0xFFAAAAAA), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    DownloadStatus.Error -> {
                        Text(stringResource(R.string.download_status_error, progress.error ?: unknownError), fontSize = 12.sp, color = Color.Red, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    DownloadStatus.Cancelled -> {
                        Text(stringResource(R.string.download_status_cancelled), fontSize = 12.sp, color = Color.Yellow)
                    }
                    DownloadStatus.Pending -> {
                        Text(stringResource(R.string.download_status_pending), fontSize = 12.sp, color = Color(0xFFAAAAAA))
                    }
                    DownloadStatus.FetchingUrl -> {
                        Text(stringResource(R.string.download_status_fetching), fontSize = 12.sp, color = Color(0xFFAAAAAA))
                    }
                    DownloadStatus.Paused -> {
                        Text(stringResource(R.string.download_status_paused), fontSize = 12.sp, color = Color(0xFFAAAAAA))
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            Row {
                when (progress.status) {
                    DownloadStatus.Downloading,
                    DownloadStatus.Pending, DownloadStatus.Paused,DownloadStatus.Completed, DownloadStatus.Cancelled,
                    DownloadStatus.FetchingUrl -> {
                        IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Cancel, contentDescription = stringResource(R.string.download_action_cancel_cd), tint = Color(0xFFCCCCCC))
                        }
                    }
                    DownloadStatus.Error -> {
                        IconButton(onClick = onRetry, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.download_action_retry_cd), tint = Color(0xFF6D28D9))
                        }
                        IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Cancel, contentDescription = stringResource(R.string.download_action_cancel_cd), tint = Color(0xFFCCCCCC))
                        }
                    }

                }
            }
        }
    }
}

@Composable
fun StatusIcon(status: DownloadStatus) {
    val icon = when (status) {
        DownloadStatus.Downloading -> Icons.Default.Download
        DownloadStatus.Completed -> Icons.Default.DownloadDone
        DownloadStatus.Error -> Icons.Default.Error
        DownloadStatus.Cancelled -> Icons.Default.Cancel
        DownloadStatus.Pending -> Icons.Default.HourglassTop
        DownloadStatus.FetchingUrl -> Icons.Default.SyncProblem
        DownloadStatus.Paused -> Icons.Default.PlayArrow
    }
    val tint = when (status) {
        DownloadStatus.Downloading -> Color(0xFF6D28D9)
        DownloadStatus.Completed -> Color.Green
        DownloadStatus.Error -> Color.Red
        DownloadStatus.Cancelled -> Color.Yellow
        DownloadStatus.Paused -> Color.Gray
        DownloadStatus.Pending, DownloadStatus.FetchingUrl -> Color(0xFFAAAAAA)
    }
    Icon(icon, contentDescription = status.name, tint = tint)
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.1f GB".format(gb)
}