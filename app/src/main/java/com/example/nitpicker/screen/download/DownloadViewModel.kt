package com.example.nitpicker.screen.download

import androidx.lifecycle.ViewModel
import com.example.nitpicker.model.DownloadProgress
import com.example.nitpicker.service.DownloadManagerService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val downloadManagerService: DownloadManagerService
) : ViewModel() {

    val downloadState: StateFlow<Map<String, DownloadProgress>> = downloadManagerService.downloadState

    fun cancelDownload(downloadId: String) {
        downloadManagerService.cancelDownload(downloadId)
    }

    // 新增：调用 Service 删除任务
    fun deleteCompletedAndCancelledTasks() {
        downloadManagerService.deleteCompletedAndCancelledTasks()
    }

    // 新增：调用 Service 重试任务
    fun retryDownload(downloadId: String) {
        downloadManagerService.retryDownload(downloadId)
    }
}