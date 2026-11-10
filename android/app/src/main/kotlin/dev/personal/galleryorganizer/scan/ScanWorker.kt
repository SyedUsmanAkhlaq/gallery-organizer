package dev.personal.galleryorganizer.scan

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.personal.galleryorganizer.AppSettings
import dev.personal.galleryorganizer.db.AppDatabase
import dev.personal.galleryorganizer.face.FaceAnalyzer
import dev.personal.galleryorganizer.face.PeopleRepository
import dev.personal.galleryorganizer.ml.ClipConfig
import dev.personal.galleryorganizer.diag.ErrorLog
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit
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
            val ctx = applicationContext
            val db = AppDatabase.get(ctx)
            val settings = AppSettings(ctx)

            // Stage 1: MediaStore index (+ drop analysis rows of deleted photos).
            val index = IndexStage(MediaStoreScanner(ctx.contentResolver), db.mediaAssets()).execute { publish(it) }
            db.clothing().deleteOrphans()
            db.faces().deleteOrphanFaces()
            db.faces().deleteOrphanScans()
            saveStage(StageK.INDEXING, StageRun(index.processed, index.total, index.avgMs, null))

            // Stage 2: clothing.
            if (ClipConfig.available(ctx)) {
                val run = ClothingStage(ctx, db, settings.accelerator).execute { publish(it) }
                saveStage(StageK.CLOTHING, run)
            } else {
                ErrorLog.record("scan", "clothing model missing from APK; stage skipped", null)
            }

            // Stage 3: faces, then the merge pass over all people.
            if (FaceAnalyzer.available(ctx)) {
                val run = FaceStage(ctx, db, settings.accelerator, settings.clusteringSensitivity).execute { publish(it) }
                saveStage(StageK.FACES, run)
                publish(ScanProgress(ScanStateK.RUNNING, StageK.FACES, run.total, run.total, message = "Grouping people"), force = true)
                PeopleRepository(db).mergePass(settings.clusteringSensitivity)
            } else {
                ErrorLog.record("scan", "face model missing from APK; stage skipped", null)
            }

            stats.lastCompletedAtMillis = System.currentTimeMillis()
            publish(
                ScanProgress(
                    ScanStateK.COMPLETED, StageK.INDEXING, index.total, index.total,
                    message = "All photos analyzed",
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

    private fun saveStage(stage: StageK, run: StageRun) {
        // Keep the previous average when nothing was processed this time.
        val prev = stats.stage(stage)
        val avg = if (run.processed > 0) run.avgMs else prev?.avgMs ?: 0.0
        stats.saveStage(stage, ScanStatsStore.StageStats(run.processed, avg, System.currentTimeMillis(), run.delegate ?: prev?.delegate))
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
    const val WATCH_WORK = "media-watch"
    const val PERIODIC_WORK = "periodic-scan"

    /**
     * Called when the app starts (after media permission): first scan if
     * never done, plus automatic pick-up of new photos:
     *  - a content-URI-triggered worker (fires when MediaStore changes, even
     *    with the app closed), and
     *  - a 12-hourly periodic safety net.
     */
    fun onAppStarted(context: Context) {
        val stats = ScanStatsStore(context)
        if (stats.lastCompletedAtMillis == 0L && !stats.paused) start(context)
        MediaChangeWorker.enqueue(context)
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<MediaChangeWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build(),
        )
    }

    /** Automatic (not user-initiated) start: respects a user pause. */
    fun startIfAllowed(context: Context) {
        val stats = ScanStatsStore(context)
        if (stats.paused || stats.lastCompletedAtMillis == 0L) return
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ScanWorker>().build(),
        )
    }

    fun start(context: Context) {
        ScanStatsStore(context).paused = false
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ScanWorker>().build(),
        )
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
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
