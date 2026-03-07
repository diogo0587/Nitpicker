package com.d3intran.nitpicker.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Nitpicker 应用的 Room 数据库定义。
 *
 * 当前版本 (v2) 包含：
 * - [DownloadTaskEntity]：下载任务持久化
 * - [MediaMetadataEntity]：AI 媒体元数据持久化
 *
 * 后续版本将新增：
 * - FaceEmbeddingEntity：人脸向量持久化
 *
 * @see DownloadTaskDao 下载任务的 DAO
 * @see MediaMetadataDao 媒体元数据的 DAO
 * @see DownloadStatusConverter 枚举类型转换器
 */
@Database(
    entities = [DownloadTaskEntity::class, MediaMetadataEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(DownloadStatusConverter::class, StringListConverter::class)
abstract class AppDatabase : RoomDatabase() {

    /** 获取下载任务 DAO 实例 */
    abstract fun downloadTaskDao(): DownloadTaskDao

    /** 获取媒体元数据 DAO 实例 */
    abstract fun mediaMetadataDao(): MediaMetadataDao
}