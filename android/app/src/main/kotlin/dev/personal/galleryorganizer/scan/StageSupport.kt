package dev.personal.galleryorganizer.scan

import dev.personal.galleryorganizer.diag.ErrorLog

/** Logs at most [limit] errors per stage run so one bad batch can't flood the log. */
class StageErrors(private val stage: String, private val limit: Int = 20) {
    var count = 0
        private set

    fun record(e: Throwable, assetId: Long? = null) {
        count++
        if (count <= limit) {
            ErrorLog.record("scan:$stage", "${e.javaClass.simpleName} on asset ${assetId ?: "-"}: ${e.message}", e.stackTraceToString())
        } else if (count == limit + 1) {
            ErrorLog.record("scan:$stage", "more errors suppressed for this run", null)
        }
    }
}

/** Per-stage result for stats/notification. */
data class StageRun(val processed: Int, val total: Int, val avgMs: Double, val delegate: String?)
