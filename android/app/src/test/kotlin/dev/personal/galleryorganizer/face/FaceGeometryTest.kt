package dev.personal.galleryorganizer.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceGeometryTest {
    @Test
    fun `similarity recovers rotation, scale and shift`() {
        val truth = Similarity(a = 0.8f, b = 0.6f, tx = 5f, ty = -3f) // scale 1, ~37°
        val src = listOf(Pt(10f, 10f), Pt(50f, 12f), Pt(30f, 40f), Pt(15f, 60f), Pt(45f, 58f))
        val dst = src.map { truth.apply(it) }
        val fit = FaceGeometry.similarity(src, dst)
        assertEquals(0.8f, fit.a, 1e-4f)
        assertEquals(0.6f, fit.b, 1e-4f)
        assertEquals(5f, fit.tx, 1e-3f)
        assertEquals(-3f, fit.ty, 1e-3f)
    }

    @Test
    fun `alignment maps detected eyes onto the template regardless of order`() {
        // Eyes given in "subject" order (right side first).
        val (src, dst) = FaceGeometry.alignmentPairs(Pt(200f, 100f), Pt(100f, 100f), null, null, null)
        val sim = FaceGeometry.similarity(src, dst)
        val left = sim.apply(Pt(100f, 100f))
        assertEquals(FaceGeometry.ARCFACE_TEMPLATE[0].x, left.x, 0.5f)
        assertEquals(FaceGeometry.ARCFACE_TEMPLATE[0].y, left.y, 0.5f)
    }

    @Test
    fun `square crop box stays square in pixels and inside the image`() {
        val b = FaceGeometry.squareCropBox(10f, 10f, 60f, 80f, 400, 200)
        val wPx = (b.right - b.left) * 400
        val hPx = (b.bottom - b.top) * 200
        assertEquals(wPx, hPx, 0.01f)
        assertTrue(b.left >= 0f && b.top >= 0f && b.right <= 1.0001f && b.bottom <= 1.0001f)
    }

    @Test
    fun `quality filter rejects small, turned or eyeless faces`() {
        assertNotNull(FaceQuality.score(120f, 512, 5f, 0f, 0f, true))
        assertNull(FaceQuality.score(30f, 512, 0f, 0f, 0f, true)) // < 10% and < 40 px
        assertNull(FaceQuality.score(120f, 512, 50f, 0f, 0f, true)) // profile
        assertNull(FaceQuality.score(120f, 512, 0f, 0f, 0f, false))
        val frontal = FaceQuality.score(150f, 512, 0f, 0f, 0f, true)!!
        val turned = FaceQuality.score(150f, 512, 30f, 0f, 0f, true)!!
        assertTrue(frontal > turned)
    }
}
