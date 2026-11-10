package dev.personal.galleryorganizer.scan

import android.content.Context
import androidx.core.content.edit

/** Small persisted scan facts (SharedPreferences, excluded from backup). */
class ScanStatsStore(context: Context) {
    private val prefs = context.getSharedPreferences("scan_stats", Context.MODE_PRIVATE)

    var paused: Boolean
        get() = prefs.getBoolean("paused", false)
        set(value) = prefs.edit { putBoolean("paused", value) }

    var lastCompletedAtMillis: Long
        get() = prefs.getLong("lastCompletedAt", 0L)
        set(value) = prefs.edit { putLong("lastCompletedAt", value) }

    data class StageStats(
        val processed: Int,
        val avgMs: Double,
        val lastRunAtMillis: Long,
        val delegate: String?,
    )

    fun stage(stage: StageK): StageStats? {
        val at = prefs.getLong("${stage.name}.lastRunAt", 0L)
        if (at == 0L) return null
        return StageStats(
            processed = prefs.getInt("${stage.name}.processed", 0),
            avgMs = prefs.getFloat("${stage.name}.avgMs", 0f).toDouble(),
            lastRunAtMillis = at,
            delegate = prefs.getString("${stage.name}.delegate", null),
        )
    }

    fun saveStage(stage: StageK, stats: StageStats) = prefs.edit {
        putInt("${stage.name}.processed", stats.processed)
        putFloat("${stage.name}.avgMs", stats.avgMs.toFloat())
        putLong("${stage.name}.lastRunAt", stats.lastRunAtMillis)
        putString("${stage.name}.delegate", stats.delegate)
    }
}
