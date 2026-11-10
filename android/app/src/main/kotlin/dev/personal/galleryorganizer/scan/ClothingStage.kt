package dev.personal.galleryorganizer.scan

import android.content.Context
import android.graphics.Bitmap
import dev.personal.galleryorganizer.db.AppDatabase
import dev.personal.galleryorganizer.db.ClothingResultEntity
import dev.personal.galleryorganizer.ml.ClothingClassifier
import dev.personal.galleryorganizer.ml.LabelScore
import dev.personal.galleryorganizer.ml.ThumbnailLoader
import dev.personal.galleryorganizer.ml.VectorMath
import dev.personal.galleryorganizer.ml.WholeImage
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Stage 2: zero-shot clothing classification of every image. */
class ClothingStage(
    private val context: Context,
    private val db: AppDatabase,
    private val accelerator: String,
) {
    companion object {
        /** Square input is cut from a thumbnail of this size (≥ model size). */
        const val LOAD_PX = 320

        /** Label stored for images that could not be decoded (not retried until modified). */
        const val UNREADABLE = "_unreadable"
    }

    suspend fun execute(report: suspend (ScanProgress) -> Unit): StageRun {
        val dao = db.clothing()
        report(ScanProgress(ScanStateK.RUNNING, StageK.CLOTHING, 0, 0, message = "Loading clothing model"))
        ClothingClassifier(context, accelerator).use { clf ->
            val cfg = clf.config
            relabel(clf, cfg.encoderVersion, cfg.labelsVersion)

            val pending = dao.pending(cfg.encoderVersion)
            val total = pending.size
            val loader = ThumbnailLoader(context.contentResolver)
            val errors = StageErrors("clothing")
            val avg = RunningAverage()
            var done = 0
            report(ScanProgress(ScanStateK.RUNNING, StageK.CLOTHING, 0, total))

            for (batch in pending.chunked(BATCH_SIZE)) {
                currentCoroutineContext().ensureActive()
                val start = System.nanoTime()
                val now = System.currentTimeMillis()
                val loaded = ArrayList<Pair<Long, Bitmap>>(batch.size)
                val rows = ArrayList<ClothingResultEntity>(batch.size)
                for (a in batch) {
                    try {
                        loaded += a.id to loader.load(a.id, LOAD_PX)
                    } catch (e: Exception) {
                        errors.record(e, a.id)
                        rows += ClothingResultEntity(
                            a.id, a.dateModified, cfg.encoderVersion, cfg.labelsVersion,
                            UNREADABLE, 0f, null, null, null, null, null, now,
                        )
                    }
                }
                if (loaded.isNotEmpty()) {
                    try {
                        // v1: one region per photo (whole image). See RegionProvider.
                        val regions = loaded.map { WholeImage.regions(it.second).first() }
                        val embeddings = clf.embed(regions)
                        val modified = batch.associate { it.id to it.dateModified }
                        loaded.forEachIndexed { i, (id, _) ->
                            val top = clf.zeroShot.classify(embeddings[i])
                            rows += row(id, modified.getValue(id), cfg.encoderVersion, cfg.labelsVersion, top, embeddings[i], now)
                        }
                    } catch (e: Exception) {
                        errors.record(e)
                    } finally {
                        loaded.forEach { it.second.recycle() }
                    }
                }
                dao.upsert(rows)
                done += batch.size
                avg.add((System.nanoTime() - start) / 1e6 / batch.size, batch.size)
                report(
                    ScanProgress(
                        ScanStateK.RUNNING, StageK.CLOTHING, done, total,
                        currentAssetId = batch.last().id, avgMsPerImage = avg.mean,
                    ),
                )
            }
            return StageRun(done, total, avg.mean, clf.delegate)
        }
    }

    /** Category set changed but the encoder did not: re-label from stored embeddings. */
    private suspend fun relabel(clf: ClothingClassifier, encoderVersion: String, labelsVersion: String) {
        val dao = db.clothing()
        while (true) {
            currentCoroutineContext().ensureActive()
            val rows = dao.needingRelabel(encoderVersion, labelsVersion, 500)
            if (rows.isEmpty()) return
            for (r in rows) {
                val emb = r.embedding
                if (emb == null) {
                    dao.relabel(r.assetId, labelsVersion, UNREADABLE, 0f, null, null, null, null)
                    continue
                }
                val top = clf.zeroShot.classify(VectorMath.fromBlob(emb))
                dao.relabel(
                    r.assetId, labelsVersion,
                    top[0].key, top[0].score,
                    top.getOrNull(1)?.key, top.getOrNull(1)?.score,
                    top.getOrNull(2)?.key, top.getOrNull(2)?.score,
                )
            }
        }
    }

    private fun row(
        id: Long,
        modified: Long,
        ev: String,
        lv: String,
        top: List<LabelScore>,
        embedding: FloatArray,
        now: Long,
    ) = ClothingResultEntity(
        assetId = id,
        dateModified = modified,
        encoderVersion = ev,
        labelsVersion = lv,
        label1 = top[0].key,
        score1 = top[0].score,
        label2 = top.getOrNull(1)?.key,
        score2 = top.getOrNull(1)?.score,
        label3 = top.getOrNull(2)?.key,
        score3 = top.getOrNull(2)?.score,
        embedding = VectorMath.toBlob(embedding),
        processedAtMillis = now,
    )
}
