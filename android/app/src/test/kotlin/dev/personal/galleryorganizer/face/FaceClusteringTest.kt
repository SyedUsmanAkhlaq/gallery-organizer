package dev.personal.galleryorganizer.face

import dev.personal.galleryorganizer.ml.VectorMath
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceClusteringTest {
    private val rnd = Random(42)

    /** A random unit "identity" plus noisy samples around it. */
    private fun identity(dim: Int = 64) = VectorMath.l2Normalize(FloatArray(dim) { rnd.nextFloat() * 2 - 1 })

    private fun sample(center: FloatArray, noise: Float) =
        VectorMath.l2Normalize(FloatArray(center.size) { center[it] + (rnd.nextFloat() * 2 - 1) * noise })

    @Test
    fun `thresholds move with sensitivity`() {
        val strict = FaceClustering.thresholds(0f)
        val def = FaceClustering.thresholds(FaceClustering.DEFAULT_SENSITIVITY)
        val loose = FaceClustering.thresholds(1f)
        assertTrue(strict.assign > def.assign && def.assign > loose.assign)
        assertEquals(0.45f, def.assign, 1e-6f)
        assertEquals(def.assign - FaceClustering.MERGE_OFFSET, def.merge, 1e-6f)
    }

    @Test
    fun `incremental assignment joins the right person and respects exclusions`() {
        val a = identity()
        val b = identity()
        val ca = FaceClustering.Centroid(1, a.copyOf(), 1)
        val cb = FaceClustering.Centroid(2, b.copyOf(), 1)
        val face = sample(a, 0.1f)
        assertEquals(1L, FaceClustering.bestMatch(face, listOf(ca, cb), 0.45f))
        assertNull(FaceClustering.bestMatch(face, listOf(ca, cb), 0.45f, excluded = setOf(1L)))
        assertNull(FaceClustering.bestMatch(identity(), listOf(ca, cb), 0.45f)) // stranger
    }

    @Test
    fun `merge pass joins split clusters of one person, keeps others apart`() {
        val a = identity()
        val b = identity()
        val clusters = listOf(
            FaceClustering.ClusterSample(10, false, 3, List(3) { sample(a, 0.15f) }),
            FaceClustering.ClusterSample(11, false, 5, List(5) { sample(a, 0.15f) }),
            FaceClustering.ClusterSample(12, false, 4, List(4) { sample(b, 0.15f) }),
        )
        val merges = FaceClustering.mergePass(clusters, 0.40f, emptySet())
        assertEquals(listOf(FaceClustering.Merge(from = 10, into = 11)), merges) // larger survives
    }

    @Test
    fun `merge pass respects cannot-link and never merges two named people`() {
        val a = identity()
        val split = listOf(
            FaceClustering.ClusterSample(1, false, 2, List(2) { sample(a, 0.1f) }),
            FaceClustering.ClusterSample(2, false, 2, List(2) { sample(a, 0.1f) }),
        )
        assertTrue(FaceClustering.mergePass(split, 0.4f, setOf(2L to 1L)).isEmpty())
        val named = split.map { it.copy(named = true) }
        assertTrue(FaceClustering.mergePass(named, 0.4f, emptySet()).isEmpty())
    }

    @Test
    fun `named survivor keeps its id even when smaller`() {
        assertEquals(5L, FaceClustering.survivor(5, true, 1, 3, false, 10))
        assertEquals(3L, FaceClustering.survivor(5, false, 1, 3, false, 10))
        assertEquals(3L, FaceClustering.survivor(5, false, 4, 3, false, 4))
    }

    @Test
    fun `chained merges are ordered and transitive`() {
        val a = identity()
        val clusters = (1L..4L).map { FaceClustering.ClusterSample(it, false, 1, listOf(sample(a, 0.1f))) }
        val merges = FaceClustering.mergePass(clusters, 0.4f, emptySet())
        // Apply in order with a union map: everything must end in one cluster.
        val owner = (1L..4L).associateWith { it }.toMutableMap()
        for (m in merges) owner.replaceAll { _, v -> if (v == m.from) m.into else v }
        assertEquals(1, owner.values.toSet().size)
    }
}
