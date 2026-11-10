package dev.personal.galleryorganizer.bridge

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import dev.personal.galleryorganizer.db.AppDatabase
import dev.personal.galleryorganizer.diag.ErrorLog
import dev.personal.galleryorganizer.scan.MediaStoreScanner
import dev.personal.galleryorganizer.scan.ScanProgress
import dev.personal.galleryorganizer.scan.ScanProgressBus
import dev.personal.galleryorganizer.scan.ScanScheduler
import dev.personal.galleryorganizer.scan.ScanStateK
import dev.personal.galleryorganizer.scan.ScanStatsStore
import dev.personal.galleryorganizer.scan.StageK
import java.io.File
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pigeon host API. Suspend methods are launched by the generated code on
 * the main dispatcher; DB / file / MediaStore work hops to IO here.
 */
class GalleryHostApiImpl(private val activity: Activity) : GalleryHostApi {
    private val context get() = activity.applicationContext
    private val stats by lazy { ScanStatsStore(context) }

    override fun startScan() {
        requestNotificationPermissionIfNeeded()
        ScanScheduler.start(context)
    }

    override fun pauseScan() = ScanScheduler.pause(context)

    override suspend fun getScanStatus(): ScanProgressMsg = withContext(Dispatchers.IO) {
        val live = ScanProgressBus.state.value
        val work = ScanScheduler.workState(context)
        val status: ScanProgress = when {
            live?.state == ScanStateK.RUNNING -> live
            work == "RUNNING" || work == "ENQUEUED" || work == "BLOCKED" ->
                ScanProgress(ScanStateK.RUNNING, StageK.INDEXING, 0, 0, message = "Waiting to start")
            live != null -> live
            stats.paused -> indexedSnapshot(ScanStateK.PAUSED, "Paused")
            stats.lastCompletedAtMillis > 0 -> indexedSnapshot(
                ScanStateK.COMPLETED,
                "Last scan ${Instant.ofEpochMilli(stats.lastCompletedAtMillis)}",
            )
            else -> ScanProgress(ScanStateK.IDLE, StageK.INDEXING, 0, 0, message = "Not scanned yet")
        }
        status.toMsg()
    }

    private suspend fun indexedSnapshot(state: ScanStateK, message: String): ScanProgress {
        val dao = AppDatabase.get(context).mediaAssets()
        val n = dao.count(false) + dao.count(true)
        val avg = stats.stage(StageK.INDEXING)?.avgMs ?: 0.0
        return ScanProgress(state, StageK.INDEXING, n, n, avgMsPerImage = avg, message = message)
    }

    override suspend fun getDebugInfo(): DebugInfoMsg = withContext(Dispatchers.IO) {
        val dao = AppDatabase.get(context).mediaAssets()
        val stages = StageK.entries.map { stage ->
            val s = stats.stage(stage)
            StageStatsMsg(
                stage = stage.toMsg(),
                processed = (s?.processed ?: 0).toLong(),
                avgMsPerImage = s?.avgMs ?: 0.0,
                delegate = s?.delegate,
                lastRunAtMillis = s?.lastRunAtMillis,
            )
        }
        DebugInfoMsg(
            indexedImages = dao.count(false).toLong(),
            indexedVideos = dao.count(true).toLong(),
            dbSizeBytes = AppDatabase.sizeOnDisk(context),
            workState = ScanScheduler.workState(context),
            stages = stages,
            errorCount = ErrorLog.count().toLong(),
        )
    }

    override suspend fun verifyAssetIds(ids: List<String>): IdCheckMsg = withContext(Dispatchers.IO) {
        val parsed = ids.mapNotNull { it.toLongOrNull() }
        val found = MediaStoreScanner(context.contentResolver).existingIds(parsed)
        val missing = ids.filter { id -> id.toLongOrNull()?.let { it !in found } ?: true }
        IdCheckMsg(
            checked = ids.size.toLong(),
            matched = (ids.size - missing.size).toLong(),
            missing = missing.take(20),
        )
    }

    override suspend fun logError(source: String, message: String, stack: String?) =
        withContext(Dispatchers.IO) { ErrorLog.record(source, message, stack) }

    override suspend fun getRecentErrors(): List<ErrorEntryMsg> =
        withContext(Dispatchers.IO) { ErrorLog.recent().map { it.toMsg() } }

    override suspend fun clearErrors() = withContext(Dispatchers.IO) { ErrorLog.clear() }

    override suspend fun shareLog() {
        val file = withContext(Dispatchers.IO) { writeShareFile() }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.logs", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Gallery Organizer log")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        activity.startActivity(Intent.createChooser(send, "Share log"))
    }

    private suspend fun writeShareFile(): File {
        val info = getAppInfo()
        val debug = getDebugInfo()
        val header = buildString {
            appendLine("Gallery Organizer ${info.versionName} (${info.versionCode})")
            appendLine("Device: ${info.device}, Android SDK ${info.sdkInt}")
            appendLine("Generated: ${Instant.now()}")
            appendLine(
                "Indexed: ${debug.indexedImages} images, ${debug.indexedVideos} videos; " +
                    "work=${debug.workState}; db=${debug.dbSizeBytes} bytes",
            )
            for (s in debug.stages) {
                appendLine("Stage ${s.stage}: processed=${s.processed} avgMs=${"%.2f".format(s.avgMsPerImage)} delegate=${s.delegate ?: "-"}")
            }
            appendLine("Errors (${ErrorLog.count()}, oldest first):")
            appendLine()
        }
        return File(ErrorLog.logsDir(context), "gallery-organizer-log.txt").apply {
            writeText(header + ErrorLog.asText())
        }
    }

    override fun getAppInfo(): AppInfoMsg {
        val pm = context.packageManager
        val pkg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0)
        }
        return AppInfoMsg(
            versionName = pkg.versionName ?: "?",
            versionCode = PackageInfoCompat.getLongVersionCode(pkg),
            sdkInt = Build.VERSION.SDK_INT.toLong(),
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
        )
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4102)
        }
    }
}
