package com.d3intran.nitpicker.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 媒体元数据数据访问对象 (DAO)。
 */
@Dao
interface MediaMetadataDao {
    @Query("SELECT * FROM media_metadata WHERE uri = :uri")
    suspend fun getMetadataForUri(uri: String): MediaMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: MediaMetadataEntity)

    /**
     * 根据标签搜索媒体。使用 SQL LIKE 进行模糊匹配。
     */
    @Query("SELECT * FROM media_metadata WHERE tags LIKE :query")
    fun searchByTag(query: String): Flow<List<MediaMetadataEntity>>

    @Query("DELETE FROM media_metadata WHERE uri = :uri")
    suspend fun deleteMetadata(uri: String)

    @Query("SELECT * FROM media_metadata")
    fun getAllMetadata(): Flow<List<MediaMetadataEntity>>

    /**
     * 获取所有包含人脸的媒体。
     */
    @Query("SELECT * FROM media_metadata WHERE faceCount > 0")
    fun getMetadataWithFaces(): Flow<List<MediaMetadataEntity>>

    /**
     * 获取所有包含物体的媒体。
     */
    @Query("SELECT * FROM media_metadata WHERE objectCount > 0")
    fun getMetadataWithObjects(): Flow<List<MediaMetadataEntity>>
}
