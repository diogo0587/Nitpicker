// filepath: e:\Android\nitpicker\app\src\main\java\com\example\nitpicker\db\DownloadTaskEntity.kt
package com.example.nitpicker.db

import androidx.room.TypeConverter
import com.example.nitpicker.model.DownloadStatus
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "download_tasks")
@TypeConverters(DownloadStatusConverter::class) // Need a TypeConverter for Enum
data class DownloadTaskEntity(
    @PrimaryKey val id: String, // Use the same ID as DownloadFileInfo/DownloadProgress
    val fileName: String,
    val fileType: String,
    val downloadPageUrl: String, // Referer URL
    val fileUrl: String,         // Direct download URL
    val albumTitle: String,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    var status: DownloadStatus = DownloadStatus.Pending,
    var filePath: String? = null, // Final path on completion
    var error: String? = null,
    val createdAt: Long = System.currentTimeMillis() // For sorting or cleanup later
)

// filepath: e:\Android\nitpicker\app\src\main\java\com\example\nitpicker\db\DownloadStatusConverter.kt


class DownloadStatusConverter {
    @TypeConverter
    fun fromStatus(status: DownloadStatus): String = status.name

    @TypeConverter
    fun toStatus(statusString: String): DownloadStatus = DownloadStatus.valueOf(statusString)
}