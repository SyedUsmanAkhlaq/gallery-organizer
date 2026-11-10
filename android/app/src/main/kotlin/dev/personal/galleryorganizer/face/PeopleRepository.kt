package dev.personal.galleryorganizer.face

import androidx.room.withTransaction
import dev.personal.galleryorganizer.db.AppDatabase
import dev.personal.galleryorganizer.db.FaceExclusionEntity
import dev.personal.galleryorganizer.db.PersonEntity
import dev.personal.galleryorganizer.ml.VectorMath

/**
 * Re-clustering (merge pass) and user corrections. Corrections are stored
 * as data (names, hidden flag, locked faces, exclusions) that the merge
 * pass respects, so re-clustering never overwrites them.
 */
class PeopleRepository(private val db: AppDatabase) {
    private val faces = db.faces()
    private val persons = db.persons()

    /** Rebuilds every centroid from its faces, then runs the agglomerative merge pass. Returns merges applied. */
    suspend fun mergePass(sensitivity: Float): Int = db.withTransaction {
        faces.deleteOrphanExclusions()
        persons.deleteEmpty()
        val rows = faces.allAssignedEmbeddings()
        val byPerson = rows.groupBy { it.personId!! }
        val named = persons.all().filter { it.name != null }.map { it.id }.toSet()

        // Fresh centroids (photos may have been deleted or edited).
        for ((pid, list) in byPerson) {
            persons.updateCentroid(pid, VectorMath.toBlob(sum(list.map { VectorMath.fromBlob(it.embedding) })), list.size)
        }

        val personOfFace = rows.associate { it.id to it.personId!! }
        val cannotLink = faces.allExclusions().mapNotNull { ex ->
            personOfFace[ex.faceId]?.let { it to ex.personId }
        }.toSet()

        val clusters = byPerson.map { (pid, list) ->
            FaceClustering.ClusterSample(
                id = pid,
                named = pid in named,
                size = list.size,
                samples = list.map { VectorMath.fromBlob(it.embedding) },
            )
        }
        val merges = FaceClustering.mergePass(clusters, FaceClustering.thresholds(sensitivity).merge, cannotLink)
        for (m in merges) {
            faces.moveAll(m.from, m.into)
            faces.remapExclusions(m.from, m.into)
            faces.deleteExclusionsForPerson(m.from)
            persons.delete(m.from)
        }
        if (merges.isNotEmpty()) recompute(merges.map { it.into }.toSet())
        merges.size
    }

    suspend fun rename(personId: Long, name: String?) =
        persons.rename(personId, name?.trim()?.takeIf { it.isNotEmpty() })

    suspend fun setHidden(personId: Long, hidden: Boolean) = persons.setHidden(personId, hidden)

    /** User merge: everything of [source] moves to [target]; overrides "not this person" between them. */
    suspend fun merge(source: Long, target: Long) {
        if (source == target) return
        db.withTransaction {
            val src = persons.byId(source) ?: return@withTransaction
            val dst = persons.byId(target) ?: return@withTransaction
            faces.deleteExclusionsBetween(source, target)
            faces.moveAll(source, target)
            faces.remapExclusions(source, target)
            faces.deleteExclusionsForPerson(source)
            if (dst.name == null && src.name != null) persons.rename(target, src.name)
            if (src.hidden && dst.hidden) persons.setHidden(target, true)
            persons.delete(source)
            recompute(setOf(target))
        }
    }

    /**
     * "Not this person" for one photo: its faces in [personId] get an
     * exclusion, then move to [targetPersonId] or to a new person of their own.
     * Returns the person they moved to.
     */
    suspend fun removeFaceFromPerson(personId: Long, assetId: Long, targetPersonId: Long?): Long? = db.withTransaction {
        val faceIds = faces.facesOfAssetInPerson(assetId, personId)
        if (faceIds.isEmpty()) return@withTransaction null
        val rows = faces.byIds(faceIds)
        val target = targetPersonId ?: persons.insert(
            PersonEntity(
                centroidSum = VectorMath.toBlob(FloatArray(VectorMath.fromBlob(rows[0].embedding).size)),
                faceCount = 0,
                createdAtMillis = System.currentTimeMillis(),
            ),
        )
        for (f in rows) {
            faces.addExclusion(FaceExclusionEntity(f.id, personId))
            faces.lockToPerson(f.id, target)
        }
        recompute(setOf(personId, target))
        persons.deleteEmpty()
        target
    }

    private suspend fun recompute(ids: Set<Long>) {
        for (id in ids) {
            val list = faces.embeddingsOf(id)
            if (list.isEmpty()) continue
            persons.updateCentroid(id, VectorMath.toBlob(sum(list.map { VectorMath.fromBlob(it.embedding) })), list.size)
        }
    }

    private fun sum(vectors: List<FloatArray>): FloatArray {
        val s = FloatArray(vectors.first().size)
        for (v in vectors) for (i in s.indices) s[i] += v[i]
        return s
    }
}
