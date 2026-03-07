package com.d3intran.nitpicker.model

/**
 * 远程媒体文件的元信息。
 *
 * 描述一个待下载的远程媒体文件，包含展示所需的基本信息（名称、类型、大小、缩略图）
 * 以及获取真实下载链接所需的中间页地址。该模型在首页列表和专辑详情页中使用。
 *
 * @property fileName 文件的显示名称（含扩展名）
 * @property fileType 文件扩展名（如 "mp4", "jpg"），从 [fileName] 中提取
 * @property fileSize 文件大小的人类可读描述（如 "158 MB"）
 * @property thumbnailUrl 缩略图的远程 URL，供 Coil 加载预览
 * @property pageUrl 文件详情页的完整 URL，用于解析真实下载地址
 * @property isSelected 当前是否被用户选中（用于批量操作的 UI 状态追踪）
 */
data class FileInfo(
    val fileName: String,
    val fileType: String,
    val fileSize: String,
    val thumbnailUrl: String,
    val pageUrl: String,
    var isSelected: Boolean = false
)