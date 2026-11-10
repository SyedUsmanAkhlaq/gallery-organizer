package dev.personal.galleryorganizer.face

import dev.personal.galleryorganizer.ml.VectorMath

/**
 * Face grouping (pure Kotlin, unit-tested).
 *
 * 1. Incremental: each new face joins the most similar existing person
 *    (cosine to the person's mean embedding) if similarity ≥ assign
 *    threshold, otherwise it starts a new person.
 * 2. After each full scan: an agglomerative merge pass with average
 *    linkage joins persons that were split (angles, lighting, age).
 *
 * Stability: clusters are only ever merged, never split or renumbered by
 * the algorithm, so person IDs (and user names) survive re-scans. The
 * merge survivor keeps its ID: a named person wins, then the larger one,
 * then the older (smaller ID).
 *
 * User corrections are constraints: two named persons are never merged
 * automatically, and "not this person" adds a cannot-link.
 */
object FaceClustering {
    /** Cosine threshold to join a person at sensitivity 0 (strictest). */
    const val ASSIGN_STRICT = 0.55f

    /** ... and at sensitivity 1 (loosest). Default sensitivity 0.5 → 0.45. */
    const val ASSIGN_LOOSE = 0.35f

    /** Merge threshold (average linkage) = assign threshold − this. */
    const val MERGE_OFFSET = 0.05f

    /** Centroid pre-filter: skip pairs whose means are this far below the merge threshold. */
    const val PREFILTER_MARGIN = 0.15f

    /** Faces per cluster used for average linkage (keeps the pass fast). */
    const val MAX_SAMPLES = 16

    const val DEFAULT_SENSITIVITY = 0.5f

    data class Thresholds(val assign: Float, val merge: Float)

    fun thresholds(sensitivity: Float): Thresholds {
        val s = sensitivity.coerceIn(0f, 1f)
        val assign = ASSIGN_STRICT + (ASSIGN_LOOSE - ASSIGN_STRICT) * s
        return Thresholds(assign, assign - MERGE_OFFSET)
    }

    /** In-memory view of a person for incremental assignment. */
    class Centroid(val id: Long, var sum: FloatArray, var count: Int) {
        fun mean(): FloatArray = VectorMath.l2Normalize(sum)

        fun add(embedding: FloatArray) {
            for (i in sum.indices) sum[i] += embedding[i]
            count++
        }
    }

    /** Best person for [embedding] at or above [threshold], skipping [excluded]. */
    fun bestMatch(embedding: FloatArray, centroids: Collection<Centroid>, threshold: Float, excluded: Set<Long> = emptySet()): Long? {
        var bestId: Long? = null
        var best = threshold
        for (c in centroids) {
            if (c.id in excluded) continue
            val sim = VectorMath.cosine(embedding, c.sum)
            if (sim >= best) {
                best = sim
                bestId = c.id
            }
        }
        return bestId
    }

    data class ClusterSample(
        val id: Long,
        val named: Boolean,
        val size: Int,
        /** L2-normalized member embeddings (a sample is enough). */
        val samples: List<FloatArray>,
    )

    data class Merge(val from: Long, val into: Long)

    fun averageLinkage(a: List<FloatArray>, b: List<FloatArray>): Float {
        var sum = 0.0
        for (x in a) for (y in b) sum += VectorMath.dot(x, y)
        return (sum / (a.size * b.size)).toFloat()
    }

    private fun key(a: Long, b: Long) = if (a < b) a to b else b to a

    private fun meanOf(samples: List<FloatArray>): FloatArray {
        val m = FloatArray(samples[0].size)
        for (s in samples) for (i in m.indices) m[i] += s[i]
        return VectorMath.l2Normalize(m)
    }

    private fun capSamples(list: List<FloatArray>): List<FloatArray> {
        if (list.size <= MAX_SAMPLES) return list
        val step = list.size.toDouble() / MAX_SAMPLES
        return List(MAX_SAMPLES) { list[(it * step).toInt()] }
    }

    /**
     * Agglomerative merge pass. Returns merges in the order they must be
     * applied (a later merge may use an earlier survivor).
     */
    fun mergePass(clusters: List<ClusterSample>, threshold: Float, cannotLink: Set<Pair<Long, Long>>, maxRounds: Int = 6): List<Merge> {
        class Node(val id: Long, var named: Boolean, var size: Int, var samples: List<FloatArray>, var mean: FloatArray)

        val nodes = clusters.filter { it.samples.isNotEmpty() }
            .associateTo(LinkedHashMap()) { it.id to Node(it.id, it.named, it.size, capSamples(it.samples), meanOf(it.samples)) }
        val forbidden = cannotLink.map { key(it.first, it.second) }.toMutableSet()
        val merges = mutableListOf<Merge>()

        repeat(maxRounds) {
            val list = nodes.values.toList()
            val candidates = mutableListOf<Triple<Float, Node, Node>>()
            for (i in list.indices) {
                val a = list[i]
                for (j in i + 1 until list.size) {
                    val b = list[j]
                    if (a.named && b.named) continue
                    if (key(a.id, b.id) in forbidden) continue
                    if (VectorMath.dot(a.mean, b.mean) < threshold - PREFILTER_MARGIN) continue
                    val link = averageLinkage(a.samples, b.samples)
                    if (link >= threshold) candidates += Triple(link, a, b)
                }
            }
            if (candidates.isEmpty()) return merges
            candidates.sortByDescending { it.first }
            val used = HashSet<Long>()
            for ((_, a, b) in candidates) {
                if (a.id in used || b.id in used) continue
                val (keep, drop) = survivor(a.id, a.named, a.size, b.id, b.named, b.size).let { if (it == a.id) a to b else b to a }
                val total = keep.size + drop.size
                keep.samples = capSamples(keep.samples + drop.samples)
                keep.mean = meanOf(keep.samples)
                keep.size = total
                keep.named = keep.named || drop.named
                nodes.remove(drop.id)
                // Constraints of the absorbed cluster now apply to the survivor.
                for (p in forbidden.toList()) {
                    if (p.first == drop.id || p.second == drop.id) {
                        val other = if (p.first == drop.id) p.second else p.first
                        forbidden += key(keep.id, other)
                    }
                }
                merges += Merge(from = drop.id, into = keep.id)
                used += a.id
                used += b.id
            }
        }
        return merges
    }

    /** Which ID survives a merge: named, then larger, then older (smaller id). */
    fun survivor(aId: Long, aNamed: Boolean, aSize: Int, bId: Long, bNamed: Boolean, bSize: Int): Long = when {
        aNamed != bNamed -> if (aNamed) aId else bId
        aSize != bSize -> if (aSize > bSize) aId else bId
        else -> minOf(aId, bId)
    }
}
