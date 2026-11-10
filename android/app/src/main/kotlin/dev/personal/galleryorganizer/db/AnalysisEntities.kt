package dev.personal.galleryorganizer.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Clothing result per image. Top-3 labels with softmax scores plus the raw
 * image embedding (512 float32, little-endian), so categories can be
 * re-labelled or a linear probe trained later without re-running the model.
 *
 * Re-processing rules (see ClothingStage):
 *  - dateModified or encoderVersion differs → run the model again
 *  - only labelsVersion differs → re-label from [embedding] (fast)
 */
@Entity(
    tableName = "clothing_results",
    indices = [Index(value = ["label1", "score1"])],
)
data class ClothingResultEntity(
    @PrimaryKey val assetId: Long,
    val dateModified: Long,
    val encoderVersion: String,
    val labelsVersion: String,
    val label1: String,
    val score1: Float,
    val label2: String?,
    val score2: Float?,
    val label3: String?,
    val score3: Float?,
    val embedding: ByteArray?,
    val processedAtMillis: Long,
)

/** Face stage bookkeeping: one row per processed image (even with 0 faces). */
@Entity(tableName = "face_scans")
data class FaceScanEntity(
    @PrimaryKey val assetId: Long,
    val dateModified: Long,
    val modelVersion: String,
    val faceCount: Int,
    val processedAtMillis: Long,
)

/**
 * One detected, embedded face. Box = normalized square crop around the face
 * (used to show the face in the People tab). Embedding = 512 float32 LE,
 * L2-normalized.
 */
@Entity(
    tableName = "faces",
    indices = [Index("assetId"), Index("personId"), Index(value = ["assetId", "faceIndex"], unique = true)],
)
data class FaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val assetId: Long,
    val faceIndex: Int,
    val boxLeft: Float,
    val boxTop: Float,
    val boxRight: Float,
    val boxBottom: Float,
    val quality: Float,
    val embedding: ByteArray,
    val personId: Long?,
    /** Set by "Not this person → Move to…"; clustering never moves it. */
    val lockedByUser: Boolean = false,
)

/**
 * A person (face cluster). IDs are stable: clustering only merges, and the
 * survivor keeps its ID. [centroidSum] = sum of member embeddings.
 */
@Entity(tableName = "persons")
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String? = null,
    val hidden: Boolean = false,
    val centroidSum: ByteArray,
    val faceCount: Int,
    val createdAtMillis: Long,
)

/** "Not this person": [faceId] must never be grouped into [personId]. */
@Entity(tableName = "face_exclusions", primaryKeys = ["faceId", "personId"], indices = [Index("personId")])
data class FaceExclusionEntity(val faceId: Long, val personId: Long)

// ---- query projections ------------------------------------------------------

data class PendingAsset(val id: Long, val dateModified: Long)

data class RelabelRow(val assetId: Long, val embedding: ByteArray?)

data class CategoryCount(val label: String, val count: Int)

data class AssetRefRow(
    val id: Long,
    val width: Int,
    val height: Int,
    val isVideo: Boolean,
    val bucketName: String?,
    val score: Float?,
)

data class PersonSummaryRow(
    val id: Long,
    val name: String?,
    val hidden: Boolean,
    val faceCount: Int,
    val photoCount: Int,
    val coverFaceId: Long?,
)

data class FaceRefRow(
    val id: Long,
    val assetId: Long,
    val boxLeft: Float,
    val boxTop: Float,
    val boxRight: Float,
    val boxBottom: Float,
    val width: Int,
    val height: Int,
)

data class FaceEmbeddingRow(val id: Long, val personId: Long?, val embedding: ByteArray)
