package dev.personal.galleryorganizer.scan

import dev.personal.galleryorganizer.db.MediaAssetEntity

/** Items processed per batch (one transaction / one inference batch). */
const val BATCH_SIZE = 20

enum class ScanStateK { IDLE, RUNNING, PAUSED, COMPLETED, FAILED }

/** Pipeline stages, each independently resumable and versioned. */
enum class StageK { INDEXING, CLOTHING, FACES }

data class ScanProgress(
    val state: ScanStateK,
    val stage: StageK,
    val scanned: Int,
    val total: Int,
    val currentAssetId: Long? = null,
    val avgMsPerImage: Double = 0.0,
    val message: String? = null,
)

/** One MediaStore row (images and videos). No paths. */
data class MediaRow(
    val id: Long,
    val dateModified: Long,
    val mimeType: String?,
    val isVideo: Boolean,
    val width: Int,
    val height: Int,
    val bucketId: Long?,
    val bucketName: String?,
) {
    fun toEntity(nowMillis: Long) = MediaAssetEntity(
        id = id,
        dateModified = dateModified,
        mimeType = mimeType,
        isVideo = isVideo,
        width = width,
        height = height,
        bucketId = bucketId,
        bucketName = bucketName,
        indexedAtMillis = nowMillis,
    )
}

/** Source of media rows (MediaStore in the app, a fake in tests). */
fun interface MediaSource {
    fun queryAll(): List<MediaRow>
}
