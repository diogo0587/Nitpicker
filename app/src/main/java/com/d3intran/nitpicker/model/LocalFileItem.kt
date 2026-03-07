package com.d3intran.nitpicker.model

import android.net.Uri

/**
 * 本地媒体文件的展示模型。
 *
 * 代表已下载到本地或从用户相册导入的一个媒体文件。
 * 在"素材库"和"本地相册"页面中使用，供 Compose UI 渲染列表。
 *
 * @property path 文件在本地文件系统中的绝对路径
 * @property name 文件的显示名称
 * @property type 文件类型（图片或视频），参见 [FileType]
 * @property thumbnailUri 缩略图 URI，供 Coil 加载
 *   - 图片：直接使用文件路径作为 URI
 *   - 视频：使用缓存的缩略图 URI
 * @property size 文件大小（字节）
 * @property lastModified 文件最后修改时间戳（毫秒）
 * @property durationMillis 视频时长（毫秒），仅视频文件有值
 */
data class LocalFileItem(
    val path: String,
    val name: String,
    val type: FileType,
    val thumbnailUri: Uri?,
    val size: Long,
    val lastModified: Long,
    val durationMillis: Long? = null,
    val tags: List<String> = emptyList(),
    val faceCount: Int = 0,
    val objectCount: Int = 0
)

/**
 * 媒体文件类型枚举。
 *
 * 用于区分图片和视频，决定加载策略和展示方式。
 */
enum class FileType {
    /** 图片文件（jpg, png, webp 等） */
    IMAGE,

    /** 视频文件（mp4, mkv, webm 等） */
    VIDEO,
}