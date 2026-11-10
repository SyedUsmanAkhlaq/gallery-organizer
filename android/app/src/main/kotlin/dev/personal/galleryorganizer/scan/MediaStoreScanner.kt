package dev.personal.galleryorganizer.scan

import android.content.ContentResolver
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns

/**
 * Reads images + videos straight from MediaStore's Files collection.
 *
 * Shared-ID rule: `FileColumns._ID` is the same number photo_manager exposes
 * as `AssetEntity.id` (it also queries MediaStore and uses `_ID`). The debug
 * screen's "Verify asset IDs" checks this at runtime via [existingIds].
 *
 * With Android 14 partial access, MediaStore only returns user-selected
 * items, so the index naturally shrinks to what is visible.
 */
class MediaStoreScanner(private val resolver: ContentResolver) : MediaSource {
    private val uri = MediaStore.Files.getContentUri("external")
    private val mediaFilter =
        "${FileColumns.MEDIA_TYPE} IN (${FileColumns.MEDIA_TYPE_IMAGE}, ${FileColumns.MEDIA_TYPE_VIDEO})"

    override fun queryAll(): List<MediaRow> {
        val projection = arrayOf(
            FileColumns._ID,
            FileColumns.DATE_MODIFIED,
            FileColumns.MIME_TYPE,
            FileColumns.MEDIA_TYPE,
            FileColumns.WIDTH,
            FileColumns.HEIGHT,
            COL_BUCKET_ID,
            COL_BUCKET_NAME,
        )
        val out = ArrayList<MediaRow>()
        resolver.query(uri, projection, mediaFilter, null, "${FileColumns.DATE_MODIFIED} DESC")?.use { c ->
            val iId = c.getColumnIndexOrThrow(FileColumns._ID)
            val iMod = c.getColumnIndex(FileColumns.DATE_MODIFIED)
            val iMime = c.getColumnIndex(FileColumns.MIME_TYPE)
            val iType = c.getColumnIndex(FileColumns.MEDIA_TYPE)
            val iW = c.getColumnIndex(FileColumns.WIDTH)
            val iH = c.getColumnIndex(FileColumns.HEIGHT)
            val iBucket = c.getColumnIndex(COL_BUCKET_ID)
            val iBucketName = c.getColumnIndex(COL_BUCKET_NAME)
            while (c.moveToNext()) {
                out += MediaRow(
                    id = c.getLong(iId),
                    dateModified = if (iMod >= 0) c.getLong(iMod) else 0L,
                    mimeType = if (iMime >= 0) c.getString(iMime) else null,
                    isVideo = iType >= 0 && c.getInt(iType) == FileColumns.MEDIA_TYPE_VIDEO,
                    width = if (iW >= 0) c.getInt(iW) else 0,
                    height = if (iH >= 0) c.getInt(iH) else 0,
                    bucketId = if (iBucket >= 0 && !c.isNull(iBucket)) c.getLong(iBucket) else null,
                    bucketName = if (iBucketName >= 0) c.getString(iBucketName) else null,
                )
            }
        }
        return out
    }

    /** Which of [ids] exist as image/video `_ID`s. */
    fun existingIds(ids: List<Long>): Set<Long> {
        val found = HashSet<Long>()
        for (chunk in ids.distinct().chunked(500)) {
            val placeholders = chunk.joinToString(",") { "?" }
            val selection = "${FileColumns._ID} IN ($placeholders) AND $mediaFilter"
            val args = chunk.map { it.toString() }.toTypedArray()
            resolver.query(uri, arrayOf(FileColumns._ID), selection, args, null)?.use { c ->
                while (c.moveToNext()) found += c.getLong(0)
            }
        }
        return found
    }

    private companion object {
        // Raw names: the MediaColumns constants only exist from API 29.
        const val COL_BUCKET_ID = "bucket_id"
        const val COL_BUCKET_NAME = "bucket_display_name"
    }
}
