package dev.personal.galleryorganizer.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface MediaAssetDao {
    @Query("SELECT id, dateModified FROM media_assets")
    suspend fun allKeys(): List<AssetKey>

    @Upsert
    suspend fun upsert(rows: List<MediaAssetEntity>)

    /** Callers chunk [ids] (SQLite bind-variable limit). */
    @Query("DELETE FROM media_assets WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>): Int

    @Query("SELECT COUNT(*) FROM media_assets WHERE isVideo = :video")
    suspend fun count(video: Boolean): Int

    @Query("SELECT * FROM media_assets WHERE id = :id")
    suspend fun byId(id: Long): MediaAssetEntity?

    @Query("DELETE FROM media_assets")
    suspend fun clear()
}
