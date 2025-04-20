// filepath: e:\Android\com.d3intran.nitpicker\app\src\main\java\com\d3intran\com.d3intran.nitpicker\db\DownloadTaskEntity.kt
package com.d3intran.nitpicker.db

import androidx.room.TypeConverter
import com.d3intran.nitpicker.model.DownloadStatus
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "download_tasks")
@TypeConverters(DownloadStatusConverter::class) // Need a TypeConverter for Enum
data class DownloadTaskEntity(
    @PrimaryKey val id: String, // Use the same ID as DownloadFileInfo/DownloadProgress
    val fileName: String,
    val fileType: String,
    val sourcePageUrl: String,
    val downloadPageUrl: String, // Referer URL
    val fileUrl: String,
    val thumbnailUrl: String,          // Direct download URL
    val albumTitle: String,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    var status: DownloadStatus = DownloadStatus.Pending,
    var filePath: String? = null, // Final path on completion
    var error: String? = null,
    val createdAt: Long = System.currentTimeMillis() // For sorting or cleanup later
)

// filepath: e:\Android\com.d3intran.nitpicker\app\src\main\java\com\d3intran\com.d3intran.nitpicker\db\DownloadStatusConverter.kt


class DownloadStatusConverter {
    @TypeConverter
    fun fromStatus(status: DownloadStatus): String = status.name

    @TypeConverter
    fun toStatus(statusString: String): DownloadStatus = DownloadStatus.valueOf(statusString)
}