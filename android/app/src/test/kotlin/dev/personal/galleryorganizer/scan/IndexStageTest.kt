package dev.personal.galleryorganizer.scan

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.personal.galleryorganizer.db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Runs the index stage against a real (in-memory) Room database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class IndexStageTest {
    private lateinit var db: AppDatabase
    private var rows = listOf<MediaRow>()
    private val source = MediaSource { rows }

    private fun row(id: Long, mod: Long, video: Boolean = false) =
        MediaRow(id, mod, if (video) "video/mp4" else "image/jpeg", video, 100, 50, 7L, "Camera")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `incremental scan inserts, skips, updates and deletes`() = runBlocking {
        val dao = db.mediaAssets()
        val reports = mutableListOf<ScanProgress>()

        rows = (1L..45L).map { row(it, 1000 + it, video = it % 10 == 0L) }
        val first = IndexStage(source, dao) { 1L }.execute { reports += it }
        assertEquals(45, first.processed)
        assertEquals(41, dao.count(false))
        assertEquals(4, dao.count(true))
        // 45 items in batches of 20 → 3 batch reports after the 2 setup ones.
        assertEquals(5, reports.size)
        assertEquals(45, reports.last().scanned)

        // Second run: 1 modified, 1 deleted, 1 new, rest unchanged.
        rows = rows.filter { it.id != 2L }.map { if (it.id == 3L) it.copy(dateModified = 9999) else it } +
            row(100, 5000)
        val second = IndexStage(source, dao) { 2L }.execute { }
        assertEquals(2, second.processed)
        assertEquals(1, second.deleted)
        assertEquals(45, second.total)
        assertNull(dao.byId(2))
        assertEquals(9999L, dao.byId(3)!!.dateModified)
        assertEquals(2L, dao.byId(3)!!.indexedAtMillis)
        assertEquals(1L, dao.byId(4)!!.indexedAtMillis) // untouched
    }
}
