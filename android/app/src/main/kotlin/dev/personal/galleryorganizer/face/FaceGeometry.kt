package dev.personal.galleryorganizer.face

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class Pt(val x: Float, val y: Float)

/** x' = a*x - b*y + tx ; y' = b*x + a*y + ty  (rotation + uniform scale + shift). */
data class Similarity(val a: Float, val b: Float, val tx: Float, val ty: Float) {
    fun apply(p: Pt) = Pt(a * p.x - b * p.y + tx, b * p.x + a * p.y + ty)

    /** Row-major 3×3 for android.graphics.Matrix.setValues. */
    fun toMatrixValues() = floatArrayOf(a, -b, tx, b, a, ty, 0f, 0f, 1f)
}

/** Normalized crop box (0..1 of image width/height). */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float)

object FaceGeometry {
    /** Face crop size expected by the recognition model. */
    const val ALIGNED_SIZE = 112

    /**
     * ArcFace/InsightFace 112×112 template: eye (image-left), eye
     * (image-right), nose tip, mouth corner (image-left), mouth corner
     * (image-right).
     */
    val ARCFACE_TEMPLATE = listOf(
        Pt(38.2946f, 51.6963f),
        Pt(73.5318f, 51.5014f),
        Pt(56.0252f, 71.7366f),
        Pt(41.5493f, 92.3655f),
        Pt(70.7299f, 92.2041f),
    )

    /** Least-squares similarity transform mapping [src] onto [dst] (Umeyama, no reflection). */
    fun similarity(src: List<Pt>, dst: List<Pt>): Similarity {
        require(src.size == dst.size && src.size >= 2)
        val n = src.size
        val msx = src.sumOf { it.x.toDouble() } / n
        val msy = src.sumOf { it.y.toDouble() } / n
        val mdx = dst.sumOf { it.x.toDouble() } / n
        val mdy = dst.sumOf { it.y.toDouble() } / n
        var num1 = 0.0
        var num2 = 0.0
        var den = 0.0
        for (i in 0 until n) {
            val xs = src[i].x - msx
            val ys = src[i].y - msy
            val xd = dst[i].x - mdx
            val yd = dst[i].y - mdy
            num1 += xs * xd + ys * yd
            num2 += xs * yd - ys * xd
            den += xs * xs + ys * ys
        }
        require(den > 1e-9) { "degenerate landmarks" }
        val a = num1 / den
        val b = num2 / den
        val tx = mdx - (a * msx - b * msy)
        val ty = mdy - (b * msx + a * msy)
        return Similarity(a.toFloat(), b.toFloat(), tx.toFloat(), ty.toFloat())
    }

    /**
     * Orders detector landmarks to match [ARCFACE_TEMPLATE]: the two eyes
     * and the two mouth corners are sorted by image x, so we do not depend on
     * the detector's "subject's left" naming. Mouth/nose are optional; with
     * eyes only, the template's eye points are used.
     */
    fun alignmentPairs(
        eyeA: Pt,
        eyeB: Pt,
        nose: Pt?,
        mouthA: Pt?,
        mouthB: Pt?,
    ): Pair<List<Pt>, List<Pt>> {
        val (le, re) = if (eyeA.x <= eyeB.x) eyeA to eyeB else eyeB to eyeA
        val src = mutableListOf(le, re)
        val dst = mutableListOf(ARCFACE_TEMPLATE[0], ARCFACE_TEMPLATE[1])
        if (nose != null && mouthA != null && mouthB != null) {
            val (lm, rm) = if (mouthA.x <= mouthB.x) mouthA to mouthB else mouthB to mouthA
            src += listOf(nose, lm, rm)
            dst += listOf(ARCFACE_TEMPLATE[2], ARCFACE_TEMPLATE[3], ARCFACE_TEMPLATE[4])
        }
        return src to dst
    }

    /** Square box around a face rect, enlarged by [scale], normalized and clamped. */
    fun squareCropBox(left: Float, top: Float, right: Float, bottom: Float, imageW: Int, imageH: Int, scale: Float = 1.5f): Box {
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        var side = max(right - left, bottom - top) * scale
        side = min(side, min(imageW, imageH).toFloat())
        var l = cx - side / 2f
        var t = cy - side / 2f
        // Shift inside the image instead of shrinking, so the box stays square.
        l = l.coerceIn(0f, imageW - side)
        t = t.coerceIn(0f, imageH - side)
        return Box(l / imageW, t / imageH, (l + side) / imageW, (t + side) / imageH)
    }
}

/** Face quality filter. Faces failing it are not embedded or grouped. */
object FaceQuality {
    /** Face box width must be ≥ 10% of the image width (spec). */
    const val MIN_FACE_WIDTH_RATIO = 0.10f

    /** And at least this many pixels in the 512 px analysis image. */
    const val MIN_FACE_PX = 40

    /** Max head turn left/right (degrees). Profiles embed poorly. */
    const val MAX_YAW = 35f

    /** Max head tilt up/down (degrees). */
    const val MAX_PITCH = 30f

    /** In-plane roll is corrected by alignment, so this is generous. */
    const val MAX_ROLL = 45f

    /**
     * ML Kit gives no detection confidence, so quality is derived from
     * size and pose. Returns null when the face is rejected, otherwise a
     * score in (0, 1] (higher = better cover face candidate).
     */
    fun score(faceWidthPx: Float, imageWidthPx: Int, yaw: Float, pitch: Float, roll: Float, hasEyes: Boolean): Float? {
        if (!hasEyes) return null
        if (faceWidthPx < MIN_FACE_PX) return null
        val ratio = faceWidthPx / imageWidthPx
        if (ratio < MIN_FACE_WIDTH_RATIO) return null
        if (abs(yaw) > MAX_YAW || abs(pitch) > MAX_PITCH || abs(roll) > MAX_ROLL) return null
        val sizeScore = min(1f, ratio / 0.3f)
        val poseScore = (1f - abs(yaw) / 90f) * (1f - abs(pitch) / 90f)
        return (sizeScore * poseScore).coerceIn(0.01f, 1f)
    }
}
