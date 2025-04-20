// filepath: e:\Android\com.d3intran.nitpicker\app\src\main\java\com\d3intran\com.d3intran.nitpicker\di\DatabaseModule.kt
package com.d3intran.nitpicker.di

import android.content.Context
import androidx.room.Room
import com.d3intran.nitpicker.db.AppDatabase
import com.d3intran.nitpicker.db.DownloadTaskDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext appContext: Context): AppDatabase {
        return Room.databaseBuilder(
            appContext,
            AppDatabase::class.java,
            "nitpicker_database"
        ).build()
    }

    @Provides
    fun provideDownloadTaskDao(appDatabase: AppDatabase): DownloadTaskDao {
        return appDatabase.downloadTaskDao()
    }
}