package dev.personal.galleryorganizer.scan

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.personal.galleryorganizer.db.AppDatabase
import dev.personal.galleryorganizer.diag.ErrorLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Background scan. Runs as a foreground worker (type dataSync) with a
 * progress notification, so it survives the app being closed.
 */
class ScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val stats = ScanStatsStore(context)
    private val busThrottle = Throttle(150) { System.currentTimeMillis() }
    private val notificationThrottle = Throttle(1_000) { System.currentTimeMillis() }
    private var last = ScanProgress(ScanStateK.RUNNING, StageK.INDEXING, 0, 0, message = "Starting…")

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ScanNotifications.foregroundInfo(applicationContext, id, last)

    override suspend fun doWork(): Result {
        stats.paused = false
        publish(last, force = true)
        try {
            setForeground(getForegroundInfo())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // e.g. ForegroundServiceStartNotAllowedException when started from
            // the background on Android 12+. Keep scanning without it.
            ErrorLog.record("scan:foreground", e)
        }

        return try {
            val db = AppDatabase.get(applicationContext)
            val scanner = MediaStoreScanner(applicationContext.contentResolver)
            val index = IndexStage(scanner, db.mediaAssets()).execute { publish(it) }
            stats.saveStage(
                StageK.INDEXING,
                ScanStatsStore.StageStats(index.processed, index.avgMs, System.currentTimeMillis(), null),
            )
            // Milestone 3: ClothingStage, milestone 4: FaceStage run here.
            stats.lastCompletedAtMillis = System.currentTimeMillis()
            publish(
                ScanProgress(
                    ScanStateK.COMPLETED, StageK.INDEXING, index.total, index.total,
                    avgMsPerImage = index.avgMs,
                    message = "Indexed ${index.processed} new/changed, removed ${index.deleted}",
                ),
                force = true,
            )
            Result.success()
        } catch (e: CancellationException) {
            stats.paused = true
            ScanProgressBus.publish(last.copy(state = ScanStateK.PAUSED, message = "Paused"))
            throw e
        } catch (e: Exception) {
            ErrorLog.record("scan", e)
            publish(last.copy(state = ScanStateK.FAILED, message = e.javaClass.simpleName), force = true)
            Result.failure()
        }
    }

    private suspend fun publish(progress: ScanProgress, force: Boolean = false) {
        last = progress
        if (busThrottle.ready(force)) ScanProgressBus.publish(progress)
        if (progress.state == ScanStateK.RUNNING && notificationThrottle.ready(force)) {
            runCatching { setForeground(getForegroundInfo()) }
        }
    }
}

object ScanScheduler {
    const val UNIQUE_WORK = "library-scan"

    fun start(context: Context) {
        ScanStatsStore(context).paused = false
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ScanWorker>().build(),
        )
    }

    fun pause(context: Context) {
        ScanStatsStore(context).paused = true
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
    }

    /** e.g. RUNNING, ENQUEUED, SUCCEEDED, CANCELLED, or NONE. */
    suspend fun workState(context: Context): String =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(UNIQUE_WORK)
            .first()
            .lastOrNull()
            ?.state
            ?.name
            ?: "NONE"
}
