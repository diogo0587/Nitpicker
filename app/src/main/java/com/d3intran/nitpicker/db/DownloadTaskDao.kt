package com.d3intran.nitpicker.db

import androidx.room.*
import com.d3intran.nitpicker.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

/**
 * 下载任务的 Room DAO（Data Access Object）。
 *
 * 提供对 [DownloadTaskEntity] 表的全部 CRUD 操作。
 * 所有写操作均为 suspend 函数（协程安全），
 * 读操作通过 [Flow] 实现响应式观察（数据变更时自动推送）。
 *
 * 主要使用场景：
 * - [DownloadManagerService]：创建/更新/删除下载任务
 * - [DownloadViewModel]：通过 [getAllTasksFlow] 实时展示下载进度
 */
@Dao
interface DownloadTaskDao {

    /**
     * 插入或更新下载任务。
     * 使用 [OnConflictStrategy.REPLACE] 策略：如果主键冲突则覆盖整行。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateTask(task: DownloadTaskEntity)

    /** 根据 ID 查询单个任务（用于断点续传时恢复状态） */
    @Query("SELECT * FROM download_tasks WHERE id = :id")
    suspend fun getTaskById(id: String): DownloadTaskEntity?

    /**
     * 以 [Flow] 形式观察所有下载任务（按创建时间倒序）。
     *
     * Room 会在底层数据变更时自动重新发射，ViewModel 通过
     * `collectAsState()` 即可实现 UI 自动刷新。
     */
    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    fun getAllTasksFlow(): Flow<List<DownloadTaskEntity>>

    /** 获取所有活跃（未完成/未取消/未出错）的任务，用于应用重启后恢复下载 */
    @Query("SELECT * FROM download_tasks WHERE status NOT IN ('Completed', 'Cancelled', 'Error')")
    suspend fun getActiveTasks(): List<DownloadTaskEntity>

    /** 更新任务的 URL 信息和状态（URL 解析完成后调用） */
    @Query("UPDATE download_tasks SET downloadPageUrl = :downloadPageUrl, fileUrl = :fileUrl, status = :status WHERE id = :id")
    suspend fun updateTaskUrlsAndStatus(id: String, downloadPageUrl: String, fileUrl: String, status: DownloadStatus)

    /** 仅更新任务的 URL 信息（不改变状态） */
    @Query("UPDATE download_tasks SET downloadPageUrl = :downloadPageUrl, fileUrl = :fileUrl WHERE id = :id")
    suspend fun updateTaskUrls(id: String, downloadPageUrl: String, fileUrl: String)

    /**
     * 更新下载进度。
     * 在 [DownloadManagerService.performDownload] 的下载循环中高频调用。
     */
    @Query("UPDATE download_tasks SET status = :status, downloadedBytes = :downloadedBytes, totalBytes = :totalBytes, error = :error WHERE id = :id")
    suspend fun updateTaskProgress(id: String, status: DownloadStatus, downloadedBytes: Long, totalBytes: Long, error: String?)

    /** 标记任务完成，同时将 downloadedBytes 设为 totalBytes 并记录文件路径 */
    @Query("UPDATE download_tasks SET status = :status, filePath = :filePath, downloadedBytes = totalBytes WHERE id = :id")
    suspend fun updateTaskCompletion(id: String, status: DownloadStatus, filePath: String?)

    /** 更新任务状态（通用方法，可选附带错误信息） */
    @Query("UPDATE download_tasks SET status = :status, error = :error WHERE id = :id")
    suspend fun updateTaskStatus(id: String, status: DownloadStatus, error: String? = null)

    /** 删除指定任务记录 */
    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteTaskById(id: String)

    /** 清理所有已完成和已取消的任务（释放数据库空间） */
    @Query("DELETE FROM download_tasks WHERE status IN ('Completed', 'Cancelled')")
    suspend fun deleteCompletedAndCancelled()
}