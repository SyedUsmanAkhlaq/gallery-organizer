package dev.personal.galleryorganizer.ml

/**
 * Zero-shot classification thresholds.
 *
 * A photo belongs to a clothing category only if its top softmax score is
 * at least [DEFAULT_CLOTHING_THRESHOLD]. The value is a starting point:
 * Settings → "Category confidence" overrides it at query time (no re-scan).
 */
const val DEFAULT_CLOTHING_THRESHOLD = 0.5f

/** Number of labels stored per photo (photos can contain several outfits). */
const val TOP_LABELS = 3

data class Category(val key: String, val label: String, val clothing: Boolean, val embedding: FloatArray)

data class LabelScore(val key: String, val score: Float)

/** Pure zero-shot classifier over precomputed (L2-normalized) text embeddings. */
class ZeroShotClassifier(val categories: List<Category>, private val logitScale: Float) {
    init {
        require(categories.isNotEmpty())
    }

    /**
     * cosine(image, category) for every category → × logitScale (CLIP
     * temperature, 100) → stable softmax → top [k].
     * [imageEmbedding] must already be L2-normalized (the exported encoder
     * does this; we normalize again defensively).
     */
    fun classify(imageEmbedding: FloatArray, k: Int = TOP_LABELS): List<LabelScore> {
        val img = VectorMath.l2Normalize(imageEmbedding)
        val sims = FloatArray(categories.size) { VectorMath.dot(img, categories[it].embedding) }
        val probs = VectorMath.softmax(sims, logitScale)
        return VectorMath.topK(probs, k).map { LabelScore(categories[it].key, probs[it]) }
    }
}
