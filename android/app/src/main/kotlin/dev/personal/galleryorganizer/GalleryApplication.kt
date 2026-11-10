package dev.personal.galleryorganizer

import android.app.Application
import dev.personal.galleryorganizer.diag.ErrorLog
import dev.personal.galleryorganizer.scan.ScanNotifications

class GalleryApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ErrorLog.init(this)
        installCrashRecorder()
        ScanNotifications.ensureChannel(this)
    }

    /** Records uncaught exceptions (any thread) before the default handler runs. */
    private fun installCrashRecorder() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { ErrorLog.record("crash:${thread.name}", error) }
            previous?.uncaughtException(thread, error)
        }
    }
}
