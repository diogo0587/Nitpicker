package com.d3intran.nitpicker.di

import android.content.Context
import androidx.room.Room
import com.d3intran.nitpicker.db.AppDatabase
import com.d3intran.nitpicker.db.DownloadTaskDao
import com.d3intran.nitpicker.db.MediaMetadataDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt 依赖注入模块：数据库层。
 *
 * 安装在 [SingletonComponent] 中，保证整个应用生命周期内
 * [AppDatabase] 和 [DownloadTaskDao] 都是单例。
 *
 * Room 数据库实例通过 [Room.databaseBuilder] 惰性创建，
 * 首次访问时才会初始化（不会拖慢 App 启动速度）。
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * 提供 [AppDatabase] 单例。
     *
     * 数据库文件名为 `nitpicker_database`，存储在应用私有目录。
     * 后续如需数据库迁移，在此处添加 `.addMigrations(...)` 调用。
     */
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext appContext: Context): AppDatabase {
        return Room.databaseBuilder(
            appContext,
            AppDatabase::class.java,
            "nitpicker_database"
        ).fallbackToDestructiveMigration().build()
    }

    /**
     * 提供 [DownloadTaskDao] 实例。
     *
     * 从 [AppDatabase] 中获取，Hilt 会自动解析依赖关系。
     */
    @Provides
    fun provideDownloadTaskDao(appDatabase: AppDatabase): DownloadTaskDao {
        return appDatabase.downloadTaskDao()
    }

    /**
     * 提供 [MediaMetadataDao] 实例。
     */
    @Provides
    fun provideMediaMetadataDao(appDatabase: AppDatabase): MediaMetadataDao {
        return appDatabase.mediaMetadataDao()
    }
}