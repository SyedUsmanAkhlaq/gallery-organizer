package dev.personal.galleryorganizer.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
interface ClothingDao {
    /** Images never classified, changed since, or classified by another encoder. Newest first. */
    @Query(
        """SELECT a.id, a.dateModified FROM media_assets a
           LEFT JOIN clothing_results c ON c.assetId = a.id
           WHERE a.isVideo = 0 AND (c.assetId IS NULL OR c.dateModified != a.dateModified
                 OR c.encoderVersion != :encoderVersion)
           ORDER BY a.dateModified DESC, a.id DESC""",
    )
    suspend fun pending(encoderVersion: String): List<PendingAsset>

    /** Same encoder, different category set: re-label from stored embedding. */
    @Query(
        """SELECT assetId, embedding FROM clothing_results
           WHERE encoderVersion = :encoderVersion AND labelsVersion != :labelsVersion
           LIMIT :limit""",
    )
    suspend fun needingRelabel(encoderVersion: String, labelsVersion: String, limit: Int): List<RelabelRow>

    @Query(
        """UPDATE clothing_results SET labelsVersion = :labelsVersion, label1 = :l1, score1 = :s1,
           label2 = :l2, score2 = :s2, label3 = :l3, score3 = :s3 WHERE assetId = :assetId""",
    )
    suspend fun relabel(assetId: Long, labelsVersion: String, l1: String, s1: Float, l2: String?, s2: Float?, l3: String?, s3: Float?)

    @Upsert
    suspend fun upsert(rows: List<ClothingResultEntity>)

    @Query("DELETE FROM clothing_results WHERE assetId NOT IN (SELECT id FROM media_assets)")
    suspend fun deleteOrphans(): Int

    /** Top label counts at or above [threshold]. */
    @Query("SELECT label1 AS label, COUNT(*) AS count FROM clothing_results WHERE score1 >= :threshold GROUP BY label1")
    suspend fun countsAbove(threshold: Float): List<CategoryCount>

    @Query("SELECT COUNT(*) FROM clothing_results WHERE label1 IN (:clothingKeys) AND score1 < :threshold")
    suspend fun countUncertain(clothingKeys: List<String>, threshold: Float): Int

    @Query("SELECT COUNT(*) FROM clothing_results WHERE label1 = :key")
    suspend fun countAll(key: String): Int

    @Query("SELECT COUNT(*) FROM clothing_results")
    suspend fun countProcessed(): Int

    @Query(
        """SELECT a.id, a.width, a.height, a.isVideo, a.bucketName, c.score1 AS score
           FROM clothing_results c JOIN media_assets a ON a.id = c.assetId
           WHERE c.label1 = :key AND c.score1 >= :minScore AND c.score1 < :maxScore
           ORDER BY a.dateModified DESC, a.id DESC LIMIT :limit OFFSET :offset""",
    )
    suspend fun assetsForLabel(key: String, minScore: Float, maxScore: Float, limit: Int, offset: Int): List<AssetRefRow>

    @Query(
        """SELECT a.id, a.width, a.height, a.isVideo, a.bucketName, c.score1 AS score
           FROM clothing_results c JOIN media_assets a ON a.id = c.assetId
           WHERE c.label1 IN (:keys) AND c.score1 < :threshold
           ORDER BY a.dateModified DESC, a.id DESC LIMIT :limit OFFSET :offset""",
    )
    suspend fun uncertain(keys: List<String>, threshold: Float, limit: Int, offset: Int): List<AssetRefRow>

    @Query("SELECT * FROM clothing_results WHERE assetId = :assetId")
    suspend fun byAsset(assetId: Long): ClothingResultEntity?
}

@Dao
interface FaceDao {
    @Query(
        """SELECT a.id, a.dateModified FROM media_assets a
           LEFT JOIN face_scans s ON s.assetId = a.id
           WHERE a.isVideo = 0 AND (s.assetId IS NULL OR s.dateModified != a.dateModified
                 OR s.modelVersion != :modelVersion)
           ORDER BY a.dateModified DESC, a.id DESC""",
    )
    suspend fun pending(modelVersion: String): List<PendingAsset>

    @Upsert
    suspend fun upsertScan(row: FaceScanEntity)

    @Query("SELECT id FROM faces WHERE assetId = :assetId")
    suspend fun faceIdsForAsset(assetId: Long): List<Long>

    @Query("DELETE FROM faces WHERE assetId = :assetId")
    suspend fun deleteFacesForAsset(assetId: Long)

    @Insert
    suspend fun insertFace(face: FaceEntity): Long

    @Query("SELECT id, personId, embedding FROM faces WHERE personId = :personId")
    suspend fun embeddingsOf(personId: Long): List<FaceEmbeddingRow>

    @Query("SELECT id, personId, embedding FROM faces WHERE personId IS NOT NULL")
    suspend fun allAssignedEmbeddings(): List<FaceEmbeddingRow>

    @Query("UPDATE faces SET personId = :personId WHERE id = :faceId")
    suspend fun setPerson(faceId: Long, personId: Long?)

    @Query("UPDATE faces SET personId = :personId, lockedByUser = 1 WHERE id = :faceId")
    suspend fun lockToPerson(faceId: Long, personId: Long)

    @Query("UPDATE faces SET personId = :into WHERE personId = :from")
    suspend fun moveAll(from: Long, into: Long)

    @Query("SELECT id FROM faces WHERE assetId = :assetId AND personId = :personId")
    suspend fun facesOfAssetInPerson(assetId: Long, personId: Long): List<Long>

    @Query("SELECT COUNT(*) FROM faces")
    suspend fun countFaces(): Int

    @Query("SELECT COUNT(*) FROM face_scans")
    suspend fun countScanned(): Int

    @Query("SELECT * FROM faces WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<FaceEntity>

    @Query(
        """SELECT f.id, f.assetId, f.boxLeft, f.boxTop, f.boxRight, f.boxBottom, a.width, a.height
           FROM faces f JOIN media_assets a ON a.id = f.assetId WHERE f.id IN (:ids)""",
    )
    suspend fun faceRefs(ids: List<Long>): List<FaceRefRow>

    @Query(
        """SELECT a.id, a.width, a.height, a.isVideo, a.bucketName, NULL AS score
           FROM media_assets a WHERE a.id IN (SELECT assetId FROM faces WHERE personId = :personId)
           ORDER BY a.dateModified DESC, a.id DESC LIMIT :limit OFFSET :offset""",
    )
    suspend fun assetsOfPerson(personId: Long, limit: Int, offset: Int): List<AssetRefRow>

    @Query("SELECT DISTINCT personId FROM faces WHERE assetId = :assetId AND personId IS NOT NULL")
    suspend fun personsInAsset(assetId: Long): List<Long>

    @Query("DELETE FROM faces WHERE assetId NOT IN (SELECT id FROM media_assets)")
    suspend fun deleteOrphanFaces(): Int

    @Query("DELETE FROM face_scans WHERE assetId NOT IN (SELECT id FROM media_assets)")
    suspend fun deleteOrphanScans(): Int

    // ---- exclusions ("not this person") ----

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun addExclusion(row: FaceExclusionEntity)

    @Query("SELECT personId FROM face_exclusions WHERE faceId = :faceId")
    suspend fun exclusionsOf(faceId: Long): List<Long>

    @Query("SELECT * FROM face_exclusions")
    suspend fun allExclusions(): List<FaceExclusionEntity>

    @Query("DELETE FROM face_exclusions WHERE faceId NOT IN (SELECT id FROM faces)")
    suspend fun deleteOrphanExclusions(): Int

    @Query("UPDATE OR IGNORE face_exclusions SET personId = :into WHERE personId = :from")
    suspend fun remapExclusions(from: Long, into: Long)

    @Query("DELETE FROM face_exclusions WHERE personId = :personId")
    suspend fun deleteExclusionsForPerson(personId: Long)

    @Query(
        """DELETE FROM face_exclusions WHERE personId = :target
           AND faceId IN (SELECT id FROM faces WHERE personId = :source)""",
    )
    suspend fun deleteExclusionsBetween(source: Long, target: Long)
}

@Dao
interface PersonDao {
    @Insert
    suspend fun insert(person: PersonEntity): Long

    @Query("SELECT * FROM persons WHERE id = :id")
    suspend fun byId(id: Long): PersonEntity?

    @Query("SELECT * FROM persons")
    suspend fun all(): List<PersonEntity>

    @Query("UPDATE persons SET centroidSum = :sum, faceCount = :count WHERE id = :id")
    suspend fun updateCentroid(id: Long, sum: ByteArray, count: Int)

    @Query("UPDATE persons SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String?)

    @Query("UPDATE persons SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean)

    @Query("DELETE FROM persons WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM persons WHERE id NOT IN (SELECT DISTINCT personId FROM faces WHERE personId IS NOT NULL)")
    suspend fun deleteEmpty(): Int

    @Query("SELECT COUNT(*) FROM persons")
    suspend fun count(): Int

    @Query(
        """SELECT p.id, p.name, p.hidden, COUNT(f.id) AS faceCount, COUNT(DISTINCT f.assetId) AS photoCount,
             (SELECT f2.id FROM faces f2 WHERE f2.personId = p.id ORDER BY f2.quality DESC, f2.id LIMIT 1) AS coverFaceId
           FROM persons p JOIN faces f ON f.personId = p.id
           GROUP BY p.id
           HAVING (:includeHidden = 1 OR p.hidden = 0) AND COUNT(DISTINCT f.assetId) >= :minPhotos
           ORDER BY (p.name IS NULL), photoCount DESC, p.id""",
    )
    suspend fun summaries(includeHidden: Boolean, minPhotos: Int): List<PersonSummaryRow>
}

/** Wipes every analysis table (Settings → Delete all analysis data). */
@Dao
abstract class MaintenanceDao {
    @Query("DELETE FROM clothing_results")
    protected abstract suspend fun clearClothing()

    @Query("DELETE FROM face_scans")
    protected abstract suspend fun clearFaceScans()

    @Query("DELETE FROM face_exclusions")
    protected abstract suspend fun clearExclusions()

    @Query("DELETE FROM faces")
    protected abstract suspend fun clearFaces()

    @Query("DELETE FROM persons")
    protected abstract suspend fun clearPersons()

    @Query("DELETE FROM media_assets")
    protected abstract suspend fun clearAssets()

    @Transaction
    open suspend fun clearAll() {
        clearExclusions()
        clearFaces()
        clearPersons()
        clearFaceScans()
        clearClothing()
        clearAssets()
    }
}
