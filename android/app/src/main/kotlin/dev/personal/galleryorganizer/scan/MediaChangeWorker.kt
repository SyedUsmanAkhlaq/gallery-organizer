package dev.personal.galleryorganizer.scan

import android.content.Context
import android.provider.MediaStore
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration

/**
 * Fires when MediaStore images/videos change (WorkManager content-URI
 * trigger, works with the app closed) or periodically. Starts an
 * incremental scan — cheap, since unchanged photos are skipped — and
 * re-arms itself (content triggers are one-shot).
 */
class MediaChangeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        ScanScheduler.startIfAllowed(applicationContext)
        if (triggeredContentUris.isNotEmpty() || triggeredContentAuthorities.isNotEmpty()) enqueue(applicationContext)
        return Result.success()
    }

    companion object {
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .addContentUriTrigger(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true)
                .addContentUriTrigger(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
                .setTriggerContentUpdateDelay(Duration.ofSeconds(30))
                .setTriggerContentMaxDelay(Duration.ofMinutes(5))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ScanScheduler.WATCH_WORK,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<MediaChangeWorker>().setConstraints(constraints).build(),
            )
        }
    }
}
