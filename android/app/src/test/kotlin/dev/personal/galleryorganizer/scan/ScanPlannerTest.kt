package dev.personal.galleryorganizer.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanPlannerTest {
    private fun row(id: Long, mod: Long) = MediaRow(id, mod, "image/jpeg", false, 10, 10, 1L, "Camera")

    @Test
    fun `first scan processes everything newest first`() {
        val plan = ScanPlanner.plan(listOf(row(1, 100), row(2, 300), row(3, 200)), emptyMap())
        assertEquals(listOf(2L, 3L, 1L), plan.toProcess.map { it.id })
        assertEquals(0, plan.upToDate)
        assertTrue(plan.toDelete.isEmpty())
    }

    @Test
    fun `cache key skips unchanged, reprocesses modified, deletes missing`() {
        val stored = mapOf(1L to 100L, 2L to 300L, 9L to 50L)
        val plan = ScanPlanner.plan(listOf(row(1, 100), row(2, 301), row(3, 10)), stored)
        assertEquals(1, plan.upToDate)
        assertEquals(setOf(2L, 3L), plan.toProcess.map { it.id }.toSet())
        assertEquals(listOf(9L), plan.toDelete)
    }

    @Test
    fun `duplicate provider rows are counted once`() {
        val plan = ScanPlanner.plan(listOf(row(1, 100), row(1, 100)), emptyMap())
        assertEquals(1, plan.toProcess.size)
    }

    @Test
    fun `running average is weighted by item count`() {
        val avg = RunningAverage()
        assertEquals(0.0, avg.mean, 0.0)
        avg.add(10.0, 20)
        avg.add(40.0, 10)
        assertEquals(20.0, avg.mean, 1e-9)
        avg.add(1000.0, 0) // ignored
        assertEquals(30L, avg.samples)
    }

    @Test
    fun `throttle lets first and forced events through`() {
        var now = 0L
        val t = Throttle(100) { now }
        assertTrue(t.ready())
        now = 50
        assertFalse(t.ready())
        assertTrue(t.ready(force = true))
        now = 149
        assertFalse(t.ready())
        now = 150
        assertTrue(t.ready())
    }
}
