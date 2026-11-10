package dev.personal.galleryorganizer.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One gallery item as last seen in MediaStore.
 *
 * [id] is MediaStore `_ID` — the shared asset key (equals photo_manager's
 * `AssetEntity.id` on Android). No file paths are stored.
 */
@Entity(
    tableName = "media_assets",
    indices = [Index("bucketId"), Index("isVideo")],
)
data class MediaAssetEntity(
    @PrimaryKey val id: Long,
    /** MediaStore DATE_MODIFIED (seconds). Part of the cache key. */
    val dateModified: Long,
    val mimeType: String?,
    val isVideo: Boolean,
    val width: Int,
    val height: Int,
    val bucketId: Long?,
    val bucketName: String?,
    val indexedAtMillis: Long,
)

/** Cache key projection: (assetId, dateModified). */
data class AssetKey(val id: Long, val dateModified: Long)
