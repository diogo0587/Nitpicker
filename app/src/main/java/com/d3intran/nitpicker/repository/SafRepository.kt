package com.d3intran.nitpicker.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Initialize DataStore instance
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "saf_preferences")

/**
 * 存储访问框架 (SAF) 目录仓库。
 * 负责管理用户授权的本地相册目录 Uri。
 */
@Singleton
class SafRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val SAVED_DIRECTORIES_KEY = stringSetPreferencesKey("saved_saf_directories")

    /**
     * 观察所有已保存的授权目录 Uri。
     */
    val savedDirectoriesFlow: Flow<List<Uri>> = context.dataStore.data
        .map { preferences ->
            val uriStrings = preferences[SAVED_DIRECTORIES_KEY] ?: emptySet()
            uriStrings.mapNotNull {
                try {
                    Uri.parse(it)
                } catch (e: Exception) {
                    null
                }
            }
        }

    /**
     * 添加一个新的授权目录，并在系统中保留持久的访问权限（跨重启有效）。
     */
    suspend fun addDirectory(uri: Uri) {
        // Take persistable URI permission so we have access across reboots
        val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (e: SecurityException) {
            // Permission might have been lost or not granted properly
            e.printStackTrace()
        }

        context.dataStore.edit { preferences ->
            val currentUris = preferences[SAVED_DIRECTORIES_KEY]?.toMutableSet() ?: mutableSetOf()
            currentUris.add(uri.toString())
            preferences[SAVED_DIRECTORIES_KEY] = currentUris
        }
    }

    /**
     * 移除一个不再授权的目录。
     */
    suspend fun removeDirectory(uri: Uri) {
        // Relinquish permission
        try {
            val flags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.releasePersistableUriPermission(uri, flags)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }

        context.dataStore.edit { preferences ->
            val currentUris = preferences[SAVED_DIRECTORIES_KEY]?.toMutableSet() ?: mutableSetOf()
            currentUris.remove(uri.toString())
            preferences[SAVED_DIRECTORIES_KEY] = currentUris
        }
    }
}
