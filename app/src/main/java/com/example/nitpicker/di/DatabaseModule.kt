// filepath: e:\Android\nitpicker\app\src\main\java\com\example\nitpicker\di\DatabaseModule.kt
package com.example.nitpicker.di

import android.content.Context
import androidx.room.Room
import com.example.nitpicker.db.AppDatabase
import com.example.nitpicker.db.DownloadTaskDao
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