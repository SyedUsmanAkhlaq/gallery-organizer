package dev.personal.galleryorganizer.ml

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MlMathTest {
    @Test
    fun `softmax is stable for large logits and sums to one`() {
        val p = VectorMath.softmax(floatArrayOf(0.30f, 0.25f, 0.10f), scale = 100f)
        assertEquals(1.0, p.sum().toDouble(), 1e-5)
        assertTrue(p[0] > p[1] && p[1] > p[2])
        // exp(1000) would overflow without subtracting the max.
        val q = VectorMath.softmax(floatArrayOf(10f, 9f), scale = 100f)
        assertTrue(q.all { it.isFinite() })
        assertEquals(1f, q[0], 1e-6f)
    }

    @Test
    fun `l2 normalize, cosine, blob round trip`() {
        val v = VectorMath.l2Normalize(floatArrayOf(3f, 4f))
        assertArrayEquals(floatArrayOf(0.6f, 0.8f), v, 1e-6f)
        assertEquals(1f, VectorMath.cosine(floatArrayOf(1f, 1f), floatArrayOf(2f, 2f)), 1e-6f)
        assertArrayEquals(v, VectorMath.fromBlob(VectorMath.toBlob(v)), 0f)
        assertEquals(8, VectorMath.toBlob(v).size)
    }

    @Test
    fun `zero-shot picks the closest category with temperature 100`() {
        val cats = listOf(
            Category("saree", "Saree", true, VectorMath.l2Normalize(floatArrayOf(1f, 0f, 0f))),
            Category("kurta", "Kurta", true, VectorMath.l2Normalize(floatArrayOf(0f, 1f, 0f))),
            Category("none", "Not clothing", false, VectorMath.l2Normalize(floatArrayOf(0f, 0f, 1f))),
        )
        val clf = ZeroShotClassifier(cats, 100f)
        val top = clf.classify(floatArrayOf(0.30f, 0.20f, 0.05f))
        assertEquals(listOf("saree", "kurta", "none"), top.map { it.key })
        assertTrue(top[0].score > DEFAULT_CLOTHING_THRESHOLD)
        assertEquals(1.0, top.sumOf { it.score.toDouble() }, 1e-4)
    }

    @Test
    fun `fillChw writes planar RGB with mean and std`() {
        val pixels = intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt()) // red, green (1×2)
        val buf = ByteBuffer.allocateDirect(6 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        Preprocess.fillChw(pixels, 2, 1, floatArrayOf(0.5f, 0.5f, 0.5f), floatArrayOf(0.5f, 0.5f, 0.5f), buf)
        assertEquals(6, buf.position())
        val out = FloatArray(6).also { buf.flip(); buf.get(it) }
        // R plane, G plane, B plane; 255 → 1, 0 → -1
        assertArrayEquals(floatArrayOf(1f, -1f, -1f, 1f, -1f, -1f), out, 1e-5f)
    }

    @Test
    fun `center square crop`() {
        assertEquals(Triple(20, 0, 60), Preprocess.centerSquare(100, 60))
        assertEquals(Triple(0, 5, 50), Preprocess.centerSquare(50, 60))
    }
}
