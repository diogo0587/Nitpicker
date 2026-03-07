package com.d3intran.nitpicker.db

import androidx.room.TypeConverter
import com.d3intran.nitpicker.model.DownloadStatus

/**
 * Room [TypeConverter]，用于将 [DownloadStatus] 枚举与数据库中的字符串互转。
 *
 * Room 不能直接存储 Kotlin 枚举，因此需要 TypeConverter。
 * 存储时调用 [fromStatus] 将枚举转为 String（枚举名），
 * 读取时调用 [toStatus] 将 String 还原为枚举。
 */
class DownloadStatusConverter {

    /** 枚举 → 字符串（写入数据库） */
    @TypeConverter
    fun fromStatus(status: DownloadStatus): String = status.name

    /** 字符串 → 枚举（从数据库读取） */
    @TypeConverter
    fun toStatus(statusString: String): DownloadStatus = DownloadStatus.valueOf(statusString)
}
