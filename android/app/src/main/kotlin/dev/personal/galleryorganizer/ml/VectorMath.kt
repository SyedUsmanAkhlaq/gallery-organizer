package dev.personal.galleryorganizer.ml

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.sqrt

/** Pure vector helpers (unit-tested). Embeddings are FloatArrays. */
object VectorMath {
    fun dot(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "dim mismatch ${a.size} vs ${b.size}" }
        var s = 0f
        for (i in a.indices) s += a[i] * b[i]
        return s
    }

    fun norm(a: FloatArray): Float = sqrt(dot(a, a))

    /** Returns a new L2-normalized copy (zero vector stays zero). */
    fun l2Normalize(a: FloatArray): FloatArray {
        val n = norm(a)
        if (n == 0f) return a.copyOf()
        return FloatArray(a.size) { a[it] / n }
    }

    fun cosine(a: FloatArray, b: FloatArray): Float {
        val d = norm(a) * norm(b)
        return if (d == 0f) 0f else dot(a, b) / d
    }

    /** Numerically stable softmax of `scale * logits` (max is subtracted before exp). */
    fun softmax(logits: FloatArray, scale: Float = 1f): FloatArray {
        if (logits.isEmpty()) return FloatArray(0)
        var max = Float.NEGATIVE_INFINITY
        for (v in logits) if (v * scale > max) max = v * scale
        val out = FloatArray(logits.size)
        var sum = 0.0
        for (i in logits.indices) {
            val e = exp((logits[i] * scale - max).toDouble())
            out[i] = e.toFloat()
            sum += e
        }
        for (i in out.indices) out[i] = (out[i] / sum).toFloat()
        return out
    }

    /** Indices of the k largest values, descending. */
    fun topK(values: FloatArray, k: Int): IntArray =
        values.indices.sortedByDescending { values[it] }.take(k).toIntArray()

    /** Compact little-endian float32 blob for Room. */
    fun toBlob(v: FloatArray): ByteArray {
        val buf = ByteBuffer.allocate(v.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        buf.asFloatBuffer().put(v)
        return buf.array()
    }

    fun fromBlob(b: ByteArray): FloatArray {
        val fb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(fb.remaining()).also { fb.get(it) }
    }
}
