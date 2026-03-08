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

    @Query("SELECT * FROM media_metadata WHERE tags LIKE :query")
    fun searchByTag(query: String): Flow<List<MediaMetadataEntity>>

    @Query("DELETE FROM media_metadata WHERE uri = :uri")
    suspend fun deleteMetadata(uri: String)

    @Query("SELECT * FROM media_metadata")
    fun getAllMetadata(): Flow<List<MediaMetadataEntity>>

    @Query("SELECT uri FROM media_metadata WHERE INSTR(uri, :prefix) = 1")
    suspend fun getUrisByPrefix(prefix: String): List<String>

    @Query("DELETE FROM media_metadata WHERE INSTR(uri, :prefix) = 1")
    suspend fun deleteMetadataByPrefix(prefix: String)

    // --- Category queries for Home grid ---

    @Query("SELECT * FROM media_metadata WHERE faceCount > 0")
    fun getMetadataWithFaces(): Flow<List<MediaMetadataEntity>>

    @Query("SELECT * FROM media_metadata WHERE tags LIKE '%Animal%' OR tags LIKE '%Pet%' OR tags LIKE '%Cat%' OR tags LIKE '%Dog%' OR tags LIKE '%Bird%'")
    fun getMetadataAnimals(): Flow<List<MediaMetadataEntity>>

    @Query("SELECT * FROM media_metadata WHERE tags LIKE '%Food%' OR tags LIKE '%Drink%' OR tags LIKE '%Meal%' OR tags LIKE '%Dish%' OR tags LIKE '%Cuisine%'")
    fun getMetadataFood(): Flow<List<MediaMetadataEntity>>

    @Query("SELECT * FROM media_metadata WHERE tags LIKE '%Nature%' OR tags LIKE '%Sky%' OR tags LIKE '%Plant%' OR tags LIKE '%Mountain%' OR tags LIKE '%Beach%' OR tags LIKE '%Water%' OR tags LIKE '%Tree%'")
    fun getMetadataNature(): Flow<List<MediaMetadataEntity>>

    @Query("SELECT * FROM media_metadata WHERE tags LIKE '%Architecture%' OR tags LIKE '%Building%' OR tags LIKE '%City%' OR tags LIKE '%Home%' OR tags LIKE '%Room%' OR tags LIKE '%Indoor%' OR tags LIKE '%House%'")
    fun getMetadataArchitecture(): Flow<List<MediaMetadataEntity>>

    @Query("SELECT * FROM media_metadata WHERE tags LIKE '%Vehicle%' OR tags LIKE '%Car%' OR tags LIKE '%Transportation%' OR tags LIKE '%Bicycle%' OR tags LIKE '%Motorcycle%'")
    fun getMetadataVehicles(): Flow<List<MediaMetadataEntity>>

    /** 未被成功打标签的照片（没有任何标签且没有人脸） */
    @Query("SELECT * FROM media_metadata WHERE faceCount = 0 AND (tags = '[]' OR tags = '')")
    fun getMetadataUnlabeled(): Flow<List<MediaMetadataEntity>>
}
