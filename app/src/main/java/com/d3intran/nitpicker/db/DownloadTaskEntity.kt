package com.d3intran.nitpicker.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.d3intran.nitpicker.model.DownloadStatus

/**
 * 下载任务的 Room 持久化实体。
 *
 * 对应数据库表 `download_tasks`，记录每个下载任务的完整生命周期信息。
 * 支持断点续传（通过 [downloadedBytes] / [totalBytes]）和状态恢复。
 *
 * 数据流向：
 * ```
 * FileInfo → DownloadManagerService 创建 → DownloadTaskEntity 存入 Room
 *                                            ↓
 *                                     DownloadTaskDao 查询
 *                                            ↓
 *                                    映射为 DownloadProgress → UI 展示
 * ```
 *
 * @property id 主键，与 [DownloadFileInfo.id] 一致，用于全局唯一追踪
 * @property fileName 文件名（含扩展名）
 * @property fileType 文件扩展名（如 "mp4", "jpg"）
 * @property sourcePageUrl 资源来源页面 URL
 * @property downloadPageUrl 中间页 URL，HTTP 请求时用作 Referer
 * @property fileUrl 解析后的真实直链下载地址
 * @property thumbnailUrl 缩略图 URL，用于 UI 预览
 * @property albumTitle 所属专辑/合集标题
 * @property totalBytes 文件总字节数（从 Content-Length 获取）
 * @property downloadedBytes 已下载的字节数（用于断点续传）
 * @property status 当前任务状态，通过 [DownloadStatusConverter] 存储为字符串
 * @property filePath 下载完成后的本地路径（仅完成状态有值）
 * @property error 错误信息（仅错误状态有值）
 * @property createdAt 任务创建时间戳，用于排序
 */
@Entity(tableName = "download_tasks")
@TypeConverters(DownloadStatusConverter::class)
data class DownloadTaskEntity(
    @PrimaryKey val id: String,
    val fileName: String,
    val fileType: String,
    val sourcePageUrl: String,
    val downloadPageUrl: String,
    val fileUrl: String,
    val thumbnailUrl: String,
    val albumTitle: String,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    var status: DownloadStatus = DownloadStatus.Pending,
    var filePath: String? = null,
    var error: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)