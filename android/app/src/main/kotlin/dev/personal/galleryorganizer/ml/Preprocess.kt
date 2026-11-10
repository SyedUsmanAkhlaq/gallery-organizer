package dev.personal.galleryorganizer.ml

import java.nio.FloatBuffer

/** Pure pixel → tensor helpers. No boxed numbers, no nested lists. */
object Preprocess {
    /** Centered square crop of a w×h image: (x, y, side). */
    fun centerSquare(width: Int, height: Int): Triple<Int, Int, Int> {
        val side = minOf(width, height)
        return Triple((width - side) / 2, (height - side) / 2, side)
    }

    /**
     * Writes ARGB [pixels] (row-major, `width*height`) into [out] as CHW RGB
     * floats: `(channel/255 - mean[c]) / std[c]`. [out] position advances by
     * 3*width*height, so consecutive calls fill a batch.
     */
    fun fillChw(pixels: IntArray, width: Int, height: Int, mean: FloatArray, std: FloatArray, out: FloatBuffer) {
        val plane = width * height
        require(pixels.size >= plane)
        val base = out.position()
        val r0 = base
        val g0 = base + plane
        val b0 = base + 2 * plane
        val sr = 1f / (255f * std[0])
        val sg = 1f / (255f * std[1])
        val sb = 1f / (255f * std[2])
        val mr = mean[0] / std[0]
        val mg = mean[1] / std[1]
        val mb = mean[2] / std[2]
        for (i in 0 until plane) {
            val p = pixels[i]
            out.put(r0 + i, ((p shr 16) and 0xFF) * sr - mr)
            out.put(g0 + i, ((p shr 8) and 0xFF) * sg - mg)
            out.put(b0 + i, (p and 0xFF) * sb - mb)
        }
        out.position(base + 3 * plane)
    }
}
