package dev.personal.galleryorganizer.ml

import android.content.Context
import android.graphics.Bitmap
import dev.personal.galleryorganizer.scan.BATCH_SIZE
import java.io.Closeable
import org.json.JSONObject

/** Contents of text_embeddings.json written by tools/export_models.py. */
class ClipConfig(
    val encoderVersion: String,
    val labelsVersion: String,
    val imageSize: Int,
    val mean: FloatArray,
    val std: FloatArray,
    val logitScale: Float,
    val categories: List<Category>,
) {
    val clothingKeys: List<String> get() = categories.filter { it.clothing }.map { it.key }

    fun labelOf(key: String): String = categories.firstOrNull { it.key == key }?.label ?: key

    companion object {
        const val DIR = "models/clothing"
        const val JSON = "$DIR/text_embeddings.json"
        const val ONNX = "$DIR/image_encoder.onnx"

        fun available(context: Context) =
            OrtModels.assetExists(context, JSON) && OrtModels.assetExists(context, ONNX)

        fun load(context: Context): ClipConfig {
            val json = JSONObject(context.assets.open(JSON).bufferedReader().use { it.readText() })
            fun floats(name: String) = json.getJSONArray(name).let { a -> FloatArray(a.length()) { a.getDouble(it).toFloat() } }
            val cats = json.getJSONArray("categories")
            return ClipConfig(
                encoderVersion = json.getString("encoder_version"),
                labelsVersion = json.getString("labels_version"),
                imageSize = json.getJSONArray("image_size").getInt(0),
                mean = floats("mean"),
                std = floats("std"),
                logitScale = json.optDouble("logit_scale", 100.0).toFloat(),
                categories = List(cats.length()) { i ->
                    val c = cats.getJSONObject(i)
                    val e = c.getJSONArray("embedding")
                    Category(
                        key = c.getString("key"),
                        label = c.getString("label"),
                        clothing = c.optBoolean("clothing", true),
                        embedding = VectorMath.l2Normalize(FloatArray(e.length()) { e.getDouble(it).toFloat() }),
                    )
                },
            )
        }
    }
}

/**
 * Supplies the image regions to classify. v1 = the whole image. A later
 * "person crop" stage (detect people, classify each crop, keep the best)
 * plugs in here without touching the stage or the database.
 */
fun interface RegionProvider {
    fun regions(image: Bitmap): List<Bitmap>
}

val WholeImage = RegionProvider { listOf(it) }

/** MobileCLIP image encoder on ONNX Runtime + zero-shot head. */
class ClothingClassifier(
    context: Context,
    accelerator: String,
    val config: ClipConfig = ClipConfig.load(context),
) : Closeable {
    private val size = config.imageSize
    private val model = OrtModels.load(
        context,
        OrtModels.assetFile(context, ClipConfig.ONNX),
        cacheKey = config.encoderVersion,
        sampleShape = longArrayOf(1, 3, size.toLong(), size.toLong()),
        accelerator = accelerator,
    )
    val delegate: String get() = model.delegate
    val zeroShot = ZeroShotClassifier(config.categories, config.logitScale)

    private val plane = 3 * size * size
    private val buffer = OrtModels.directFloats(BATCH_SIZE * plane)
    private val pixels = IntArray(size * size)

    /** L2-normalized embeddings for up to BATCH_SIZE images (batched inference). */
    fun embed(images: List<Bitmap>): Array<FloatArray> {
        require(images.size in 1..BATCH_SIZE)
        buffer.clear()
        for (img in images) {
            val square = ThumbnailLoader.centerSquare(img, size)
            square.getPixels(pixels, 0, size, 0, 0, size, size)
            if (square !== img) square.recycle()
            Preprocess.fillChw(pixels, size, size, config.mean, config.std, buffer)
        }
        buffer.flip()
        val out = model.run(buffer, longArrayOf(images.size.toLong(), 3, size.toLong(), size.toLong()))
        return Array(out.size) { VectorMath.l2Normalize(out[it]) }
    }

    override fun close() = model.close()
}
