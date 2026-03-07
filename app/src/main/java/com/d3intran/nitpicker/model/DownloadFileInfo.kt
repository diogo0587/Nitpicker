package com.d3intran.nitpicker.model

import java.util.UUID

/**
 * 已解析的下载任务详细信息。
 *
 * 与 [FileInfo] 的区别：[FileInfo] 是远程列表中的元信息（尚未解析真实下载地址），
 * 而 [DownloadFileInfo] 是经过 URL 解析后的、可以直接发起 HTTP 下载的完整信息。
 *
 * 该模型在 [DownloadManagerService] 的下载队列中使用。
 *
 * @property id 下载任务的唯一标识符，用于跟踪状态和断点续传
 * @property fileName 文件名（含扩展名）
 * @property fileType 文件扩展名
 * @property downloadPageUrl 中间页 URL，HTTP 请求时用作 Referer 头
 * @property fileUrl 解析后的真实直链下载地址
 * @property albumTitle 所属专辑标题，决定本地存储的子目录名
 */
data class DownloadFileInfo(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val fileType: String,
    val downloadPageUrl: String,
    val fileUrl: String,
    val albumTitle: String
)