package dev.personal.galleryorganizer.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import dev.personal.galleryorganizer.ml.OrtModels
import dev.personal.galleryorganizer.ml.Preprocess
import dev.personal.galleryorganizer.ml.VectorMath
import java.io.Closeable

data class AnalyzedFace(val box: Box, val quality: Float, val embedding: FloatArray)

/**
 * Detect (ML Kit, bundled model, offline) → quality filter → align to the
 * ArcFace 112×112 template → embed (MobileFaceNet w600k_mbf, ONNX Runtime,
 * 512-d) → L2-normalize. ML Kit provides boxes/landmarks only, never
 * identities; recognition is the separate ONNX model.
 */
class FaceAnalyzer(context: Context, accelerator: String) : Closeable {
    companion object {
        const val ONNX = "models/face/w600k_mbf.onnx"

        /** Analysis image size: 224 px is too small for faces. */
        const val ANALYSIS_PX = 512

        /** Max faces embedded per photo (largest first). */
        const val MAX_FACES_PER_PHOTO = 12

        /** Bump when detector settings, alignment or the model change (targeted re-scan). */
        const val MODEL_VERSION = "mlkit16-arcface5pt-w600k_mbf-r1"

        private val MEAN = floatArrayOf(0.5f, 0.5f, 0.5f) // (x - 127.5) / 127.5
        private val STD = floatArrayOf(0.5f, 0.5f, 0.5f)

        fun available(context: Context) = OrtModels.assetExists(context, ONNX)
    }

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setMinFaceSize(FaceQuality.MIN_FACE_WIDTH_RATIO)
            .build(),
    )

    private val size = FaceGeometry.ALIGNED_SIZE
    private val model = OrtModels.load(
        context,
        OrtModels.assetFile(context, ONNX),
        cacheKey = MODEL_VERSION,
        sampleShape = longArrayOf(1, 3, size.toLong(), size.toLong()),
        accelerator = accelerator,
    )
    val delegate: String get() = model.delegate

    private val plane = 3 * size * size
    private val buffer = OrtModels.directFloats(MAX_FACES_PER_PHOTO * plane)
    private val pixels = IntArray(size * size)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    /** Must be called off the main thread (blocks on ML Kit). */
    fun analyze(image: Bitmap): List<AnalyzedFace> {
        val detected: List<Face> = Tasks.await(detector.process(InputImage.fromBitmap(image, 0)))
        val candidates = detected
            .mapNotNull { face -> prepare(face, image)?.let { face to it } }
            .sortedByDescending { it.first.boundingBox.width() }
            .take(MAX_FACES_PER_PHOTO)
        if (candidates.isEmpty()) return emptyList()

        buffer.clear()
        val aligned = candidates.map { (face, sim) -> Triple(face, sim, align(image, sim)) }
        for ((_, _, bmp) in aligned) {
            bmp.getPixels(pixels, 0, size, 0, 0, size, size)
            Preprocess.fillChw(pixels, size, size, MEAN, STD, buffer)
            bmp.recycle()
        }
        buffer.flip()
        val out = model.run(buffer, longArrayOf(aligned.size.toLong(), 3, size.toLong(), size.toLong()))

        return aligned.mapIndexed { i, (face, _, _) ->
            val r = face.boundingBox
            AnalyzedFace(
                box = FaceGeometry.squareCropBox(
                    r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat(),
                    image.width, image.height,
                ),
                quality = quality(face, image)!!,
                embedding = VectorMath.l2Normalize(out[i]),
            )
        }
    }

    private fun point(face: Face, type: Int): Pt? = face.getLandmark(type)?.position?.let { Pt(it.x, it.y) }

    private fun quality(face: Face, image: Bitmap): Float? = FaceQuality.score(
        faceWidthPx = face.boundingBox.width().toFloat(),
        imageWidthPx = image.width,
        yaw = face.headEulerAngleY,
        pitch = face.headEulerAngleX,
        roll = face.headEulerAngleZ,
        hasEyes = face.getLandmark(FaceLandmark.LEFT_EYE) != null && face.getLandmark(FaceLandmark.RIGHT_EYE) != null,
    )

    private fun prepare(face: Face, image: Bitmap): Similarity? {
        quality(face, image) ?: return null
        val e1 = point(face, FaceLandmark.LEFT_EYE) ?: return null
        val e2 = point(face, FaceLandmark.RIGHT_EYE) ?: return null
        val (src, dst) = FaceGeometry.alignmentPairs(
            e1, e2,
            point(face, FaceLandmark.NOSE_BASE),
            point(face, FaceLandmark.MOUTH_LEFT),
            point(face, FaceLandmark.MOUTH_RIGHT),
        )
        return runCatching { FaceGeometry.similarity(src, dst) }.getOrNull()
    }

    private fun align(image: Bitmap, sim: Similarity): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val m = Matrix().apply { setValues(sim.toMatrixValues()) }
        Canvas(out).drawBitmap(image, m, paint)
        return out
    }

    override fun close() {
        detector.close()
        model.close()
    }
}
