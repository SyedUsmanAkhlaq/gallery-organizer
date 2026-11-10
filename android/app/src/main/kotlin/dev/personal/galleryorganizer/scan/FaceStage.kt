package dev.personal.galleryorganizer.scan

import android.content.Context
import androidx.room.withTransaction
import dev.personal.galleryorganizer.db.AppDatabase
import dev.personal.galleryorganizer.db.FaceEntity
import dev.personal.galleryorganizer.db.FaceScanEntity
import dev.personal.galleryorganizer.db.PersonEntity
import dev.personal.galleryorganizer.face.AnalyzedFace
import dev.personal.galleryorganizer.face.FaceAnalyzer
import dev.personal.galleryorganizer.face.FaceClustering
import dev.personal.galleryorganizer.ml.ThumbnailLoader
import dev.personal.galleryorganizer.ml.VectorMath
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Stage 3: detect + embed faces, assign each to a person incrementally. */
class FaceStage(
    private val context: Context,
    private val db: AppDatabase,
    private val accelerator: String,
    private val sensitivity: Float,
) {
    suspend fun execute(report: suspend (ScanProgress) -> Unit): StageRun {
        report(ScanProgress(ScanStateK.RUNNING, StageK.FACES, 0, 0, message = "Loading face models"))
        val faces = db.faces()
        val persons = db.persons()
        val threshold = FaceClustering.thresholds(sensitivity).assign
        FaceAnalyzer(context, accelerator).use { analyzer ->
            val pending = faces.pending(FaceAnalyzer.MODEL_VERSION)
            val total = pending.size
            val centroids = persons.all()
                .associateTo(HashMap()) { it.id to FaceClustering.Centroid(it.id, VectorMath.fromBlob(it.centroidSum), it.faceCount) }
            val loader = ThumbnailLoader(context.contentResolver)
            val errors = StageErrors("faces")
            val avg = RunningAverage()
            var done = 0
            report(ScanProgress(ScanStateK.RUNNING, StageK.FACES, 0, total))

            for (batch in pending.chunked(BATCH_SIZE)) {
                currentCoroutineContext().ensureActive()
                val start = System.nanoTime()
                val results = ArrayList<Pair<Long, List<AnalyzedFace>>>(batch.size)
                for (a in batch) {
                    val found = try {
                        val bmp = loader.load(a.id, FaceAnalyzer.ANALYSIS_PX)
                        try { analyzer.analyze(bmp) } finally { bmp.recycle() }
                    } catch (e: Exception) {
                        errors.record(e, a.id)
                        emptyList()
                    }
                    results += a.id to found
                }
                val dirty = HashSet<Long>()
                val now = System.currentTimeMillis()
                val modified = batch.associate { it.id to it.dateModified }
                db.withTransaction {
                    for ((assetId, found) in results) {
                        // Re-processed (edited) photo: old faces go, centroids are rebuilt by the cluster pass.
                        faces.deleteFacesForAsset(assetId)
                        found.forEachIndexed { index, f ->
                            var personId = FaceClustering.bestMatch(f.embedding, centroids.values, threshold)
                            if (personId == null) {
                                personId = persons.insert(
                                    PersonEntity(centroidSum = VectorMath.toBlob(FloatArray(f.embedding.size)), faceCount = 0, createdAtMillis = now),
                                )
                                centroids[personId] = FaceClustering.Centroid(personId, FloatArray(f.embedding.size), 0)
                            }
                            centroids.getValue(personId).add(f.embedding)
                            dirty += personId
                            faces.insertFace(
                                FaceEntity(
                                    assetId = assetId, faceIndex = index,
                                    boxLeft = f.box.left, boxTop = f.box.top, boxRight = f.box.right, boxBottom = f.box.bottom,
                                    quality = f.quality, embedding = VectorMath.toBlob(f.embedding), personId = personId,
                                ),
                            )
                        }
                        faces.upsertScan(FaceScanEntity(assetId, modified.getValue(assetId), FaceAnalyzer.MODEL_VERSION, found.size, now))
                    }
                    for (id in dirty) {
                        val c = centroids.getValue(id)
                        persons.updateCentroid(id, VectorMath.toBlob(c.sum), c.count)
                    }
                }
                done += batch.size
                avg.add((System.nanoTime() - start) / 1e6 / batch.size, batch.size)
                report(
                    ScanProgress(
                        ScanStateK.RUNNING, StageK.FACES, done, total,
                        currentAssetId = batch.last().id, avgMsPerImage = avg.mean,
                    ),
                )
            }
            return StageRun(done, total, avg.mean, analyzer.delegate)
        }
    }
}
