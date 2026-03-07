package com.d3intran.nitpicker.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 媒体 AI 元数据实体类。
 * 存储图片的标签、描述以及本地 AI (ML Kit) 的处理结果。
 */
@Entity(tableName = "media_metadata")
data class MediaMetadataEntity(
    @PrimaryKey val uri: String,
    val tags: List<String>,
    val description: String?,
    val faceCount: Int = 0,
    val objectCount: Int = 0,
    val isProcessed: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)
