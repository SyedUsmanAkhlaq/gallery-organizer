package dev.personal.galleryorganizer.face

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.personal.galleryorganizer.db.AppDatabase
import dev.personal.galleryorganizer.db.FaceEntity
import dev.personal.galleryorganizer.db.MediaAssetEntity
import dev.personal.galleryorganizer.db.PersonEntity
import dev.personal.galleryorganizer.ml.VectorMath
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Corrections and the merge pass against a real (in-memory) Room DB. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PeopleRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: PeopleRepository
    private val a = VectorMath.l2Normalize(FloatArray(8) { if (it == 0) 1f else 0.05f })
    private val b = VectorMath.l2Normalize(FloatArray(8) { if (it == 1) 1f else 0.05f })

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = PeopleRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun asset(id: Long) = db.mediaAssets().upsert(
        listOf(MediaAssetEntity(id, id, "image/jpeg", false, 100, 100, 1, "Camera", 0)),
    )

    private suspend fun person(name: String? = null) =
        db.persons().insert(PersonEntity(name = name, centroidSum = VectorMath.toBlob(FloatArray(8)), faceCount = 0, createdAtMillis = 0))

    private suspend fun face(assetId: Long, personId: Long, emb: FloatArray) = db.faces().insertFace(
        FaceEntity(
            assetId = assetId, faceIndex = 0, boxLeft = 0f, boxTop = 0f, boxRight = 1f, boxBottom = 1f,
            quality = 0.5f, embedding = VectorMath.toBlob(emb), personId = personId,
        ),
    )

    @Test
    fun `merge pass joins split person and keeps the larger id`() = runBlocking {
        (1L..5L).forEach { asset(it) }
        val p1 = person()
        val p2 = person()
        val p3 = person()
        face(1, p1, a); face(2, p2, a); face(3, p2, a); face(4, p3, b); face(5, p3, b)
        val merges = repo.mergePass(0.5f)
        assertEquals(1, merges)
        assertNull(db.persons().byId(p1))
        assertEquals(3, db.faces().embeddingsOf(p2).size)
        assertEquals(2, db.faces().embeddingsOf(p3).size)
    }

    @Test
    fun `not this person creates an exclusion the merge pass respects`() = runBlocking {
        (1L..3L).forEach { asset(it) }
        val p = person("Ali")
        face(1, p, a); face(2, p, a); face(3, p, a)
        val moved = repo.removeFaceFromPerson(p, 3, null)!!
        assertNotEquals(p, moved)
        assertEquals(2, db.faces().embeddingsOf(p).size)
        repo.mergePass(1f) // loosest: would merge without the exclusion
        assertEquals(1, db.faces().embeddingsOf(moved).size)
        assertEquals("Ali", db.persons().byId(p)!!.name)
    }

    @Test
    fun `user merge moves faces, keeps a name, and deletes the source`() = runBlocking {
        (1L..2L).forEach { asset(it) }
        val named = person("Sara")
        val other = person()
        face(1, named, a); face(2, other, b)
        repo.merge(named, other)
        assertNull(db.persons().byId(named))
        assertEquals("Sara", db.persons().byId(other)!!.name)
        assertEquals(2, db.faces().embeddingsOf(other).size)
        assertEquals(2, db.persons().byId(other)!!.faceCount)
    }
}
