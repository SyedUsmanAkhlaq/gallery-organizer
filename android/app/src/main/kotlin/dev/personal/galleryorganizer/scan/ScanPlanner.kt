package dev.personal.galleryorganizer.scan

data class ScanPlan(
    /** New or changed items, newest first. */
    val toProcess: List<MediaRow>,
    /** Stored IDs no longer present in MediaStore. */
    val toDelete: List<Long>,
    /** Items already processed with the same dateModified (skipped). */
    val upToDate: Int,
)

/**
 * Cache-key diff: an item is skipped iff a row with the same
 * (assetId, dateModified) is stored. Pure, unit-tested.
 */
object ScanPlanner {
    fun plan(current: List<MediaRow>, stored: Map<Long, Long>): ScanPlan {
        val currentIds = HashSet<Long>(current.size * 2)
        val toProcess = ArrayList<MediaRow>()
        var upToDate = 0
        for (row in current) {
            if (!currentIds.add(row.id)) continue // duplicate row from provider
            if (stored[row.id] == row.dateModified) upToDate++ else toProcess += row
        }
        toProcess.sortWith(compareByDescending<MediaRow> { it.dateModified }.thenByDescending { it.id })
        val toDelete = stored.keys.filter { it !in currentIds }.sorted()
        return ScanPlan(toProcess, toDelete, upToDate)
    }
}

/** Weighted running mean of per-item milliseconds. */
class RunningAverage {
    private var sum = 0.0
    private var count = 0L

    fun add(msPerItem: Double, items: Int = 1) {
        if (items <= 0) return
        sum += msPerItem * items
        count += items
    }

    val mean: Double get() = if (count == 0L) 0.0 else sum / count
    val samples: Long get() = count
}

/** Lets through at most one event per [intervalMs] unless forced. */
class Throttle(private val intervalMs: Long, private val clock: () -> Long) {
    private var last: Long? = null

    fun ready(force: Boolean = false): Boolean {
        val now = clock()
        val prev = last
        if (force || prev == null || now - prev >= intervalMs) {
            last = now
            return true
        }
        return false
    }
}
