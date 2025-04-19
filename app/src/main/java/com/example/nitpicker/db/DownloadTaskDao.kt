// filepath: e:\Android\nitpicker\app\src\main\java\com\example\nitpicker\db\DownloadTaskDao.kt
package com.example.nitpicker.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.example.nitpicker.model.DownloadStatus

@Dao
interface DownloadTaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateTask(task: DownloadTaskEntity)

    @Query("SELECT * FROM download_tasks WHERE id = :id")
    suspend fun getTaskById(id: String): DownloadTaskEntity?

    // Use Flow to observe changes automatically
    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    fun getAllTasksFlow(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE status NOT IN ('Completed', 'Cancelled', 'Error')") // Example: Get active tasks
    suspend fun getActiveTasks(): List<DownloadTaskEntity>

    @Query("UPDATE download_tasks SET status = :status, downloadedBytes = :downloadedBytes, totalBytes = :totalBytes, error = :error WHERE id = :id")
    suspend fun updateTaskProgress(id: String, status: DownloadStatus, downloadedBytes: Long, totalBytes: Long, error: String?)

    @Query("UPDATE download_tasks SET status = :status, filePath = :filePath, downloadedBytes = totalBytes WHERE id = :id") // Mark as completed
    suspend fun updateTaskCompletion(id: String, status: DownloadStatus, filePath: String?)

     @Query("UPDATE download_tasks SET status = :status, error = :error WHERE id = :id")
    suspend fun updateTaskStatus(id: String, status: DownloadStatus, error: String? = null)

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteTaskById(id: String)

    // Add other queries as needed (e.g., delete completed)
}