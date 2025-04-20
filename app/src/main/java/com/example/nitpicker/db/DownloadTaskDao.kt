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
    
        // 新增：更新获取到的URL和状态
    @Query("UPDATE download_tasks SET downloadPageUrl = :downloadPageUrl, fileUrl = :fileUrl, status = :status WHERE id = :id")
    suspend fun updateTaskUrlsAndStatus(id: String, downloadPageUrl: String, fileUrl: String, status: DownloadStatus)

    // 新增：仅更新获取到的URL (如果状态由其他逻辑管理)
    @Query("UPDATE download_tasks SET downloadPageUrl = :downloadPageUrl, fileUrl = :fileUrl WHERE id = :id")
    suspend fun updateTaskUrls(id: String, downloadPageUrl: String, fileUrl: String)
    
    @Query("UPDATE download_tasks SET status = :status, downloadedBytes = :downloadedBytes, totalBytes = :totalBytes, error = :error WHERE id = :id")
    suspend fun updateTaskProgress(id: String, status: DownloadStatus, downloadedBytes: Long, totalBytes: Long, error: String?)

    @Query("UPDATE download_tasks SET status = :status, filePath = :filePath, downloadedBytes = totalBytes WHERE id = :id") // Mark as completed
    suspend fun updateTaskCompletion(id: String, status: DownloadStatus, filePath: String?)

     @Query("UPDATE download_tasks SET status = :status, error = :error WHERE id = :id")
    suspend fun updateTaskStatus(id: String, status: DownloadStatus, error: String? = null)

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteTaskById(id: String)

    // 新增：删除所有已完成或已取消的任务
    @Query("DELETE FROM download_tasks WHERE status IN ('Completed', 'Cancelled')")
    suspend fun deleteCompletedAndCancelled()

    // Add other queries as needed (e.g., delete completed)
}