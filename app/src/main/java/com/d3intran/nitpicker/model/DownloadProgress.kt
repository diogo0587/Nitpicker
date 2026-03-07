package com.d3intran.nitpicker.model

/**
 * 下载任务的实时进度信息。
 *
 * 用于 UI 层展示下载状态和进度。通过 [DownloadManagerService] 从
 * [DownloadTaskEntity] 映射而来，经由 Flow 实时推送给 ViewModel。
 *
 * @property id 下载任务的唯一标识符，与 [DownloadFileInfo.id] 对应
 * @property fileName 文件名（含扩展名），用于 UI 显示
 * @property albumTitle 所属专辑标题
 * @property totalBytes 文件总大小（字节），0 表示尚未获取
 * @property downloadedBytes 已下载的字节数，用于断点续传和进度计算
 * @property progressPercent 下载进度百分比 (0-100)
 * @property status 当前下载状态，参见 [DownloadStatus]
 * @property error 错误信息（仅当 [status] 为 [DownloadStatus.Error] 时有值）
 * @property filePath 下载完成后的本地文件路径（仅当 [status] 为 [DownloadStatus.Completed] 时有值）
 */
data class DownloadProgress(
    val id: String,
    val fileName: String,
    val albumTitle: String,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val progressPercent: Int = 0,
    val status: DownloadStatus = DownloadStatus.Pending,
    val error: String? = null,
    val filePath: String? = null
)
