// filepath: e:\Android\com.d3intran.nitpicker\app\src\main\java\com\d3intran\com.d3intran.nitpicker\db\AppDatabase.kt
package com.d3intran.nitpicker.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [DownloadTaskEntity::class], version = 1, exportSchema = false)
@TypeConverters(DownloadStatusConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadTaskDao(): DownloadTaskDao
}