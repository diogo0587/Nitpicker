// filepath: e:\Android\nitpicker\app\src\main\java\com\example\nitpicker\db\AppDatabase.kt
package com.example.nitpicker.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [DownloadTaskEntity::class], version = 1, exportSchema = false)
@TypeConverters(DownloadStatusConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadTaskDao(): DownloadTaskDao
}