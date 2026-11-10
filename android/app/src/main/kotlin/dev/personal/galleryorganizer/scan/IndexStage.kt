package dev.personal.galleryorganizer.scan

import dev.personal.galleryorganizer.db.MediaAssetDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class StageResult(val processed: Int, val deleted: Int, val total: Int, val avgMs: Double)

/**
 * Stage 1: mirror MediaStore into `media_assets`. Changed/new rows are
 * written in batches of [BATCH_SIZE]; a cancelled run resumes where it
 * stopped because finished rows match the cache key next time.
 *
 * Milestones 3/4 add stages with the same shape (plan → batches → report)
 * that work from `media_assets` instead of MediaStore.
 */
class IndexStage(
    private val source: MediaSource,
    private val dao: MediaAssetDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun execute(report: suspend (ScanProgress) -> Unit): StageResult {
        report(ScanProgress(ScanStateK.RUNNING, StageK.INDEXING, 0, 0, message = "Reading media library"))
        val rows = withContext(Dispatchers.IO) { source.queryAll() }
        val stored = dao.allKeys().associate { it.id to it.dateModified }
        val plan = ScanPlanner.plan(rows, stored)

        for (chunk in plan.toDelete.chunked(500)) dao.deleteByIds(chunk)

        val avg = RunningAverage()
        val total = plan.upToDate + plan.toProcess.size
        var done = plan.upToDate
        report(ScanProgress(ScanStateK.RUNNING, StageK.INDEXING, done, total))

        for (batch in plan.toProcess.chunked(BATCH_SIZE)) {
            currentCoroutineContext().ensureActive()
            val start = System.nanoTime()
            val now = clock()
            dao.upsert(batch.map { it.toEntity(now) })
            avg.add((System.nanoTime() - start) / 1_000_000.0 / batch.size, batch.size)
            done += batch.size
            report(
                ScanProgress(
                    ScanStateK.RUNNING, StageK.INDEXING, done, total,
                    currentAssetId = batch.last().id, avgMsPerImage = avg.mean,
                ),
            )
        }
        return StageResult(plan.toProcess.size, plan.toDelete.size, total, avg.mean)
    }
}
