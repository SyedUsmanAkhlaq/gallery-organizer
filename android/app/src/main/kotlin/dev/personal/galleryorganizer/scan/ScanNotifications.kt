package dev.personal.galleryorganizer.scan

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import dev.personal.galleryorganizer.MainActivity
import dev.personal.galleryorganizer.R
import java.util.UUID

object ScanNotifications {
    private const val CHANNEL_ID = "scan"
    private const val NOTIFICATION_ID = 4101

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Library scan", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Progress while photos are analyzed on this device"
                setShowBadge(false)
            },
        )
    }

    fun foregroundInfo(context: Context, workId: UUID, progress: ScanProgress): ForegroundInfo {
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val pause = WorkManager.getInstance(context).createCancelPendingIntent(workId)
        val stageLabel = when (progress.stage) {
            StageK.INDEXING -> "Indexing"
            StageK.CLOTHING -> "Clothing"
            StageK.FACES -> "Faces"
        }
        val text = if (progress.total > 0) {
            "$stageLabel ${progress.scanned} / ${progress.total}"
        } else {
            progress.message ?: "Starting…"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_scan)
            .setContentTitle("Organizing your gallery")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(progress.total, progress.scanned, progress.total == 0)
            .setContentIntent(open)
            .addAction(0, "Pause", pause)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }
}
