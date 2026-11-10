package dev.personal.galleryorganizer.bridge

import android.content.Context
import dev.personal.galleryorganizer.AppSettings
import dev.personal.galleryorganizer.db.AppDatabase
import dev.personal.galleryorganizer.db.AssetRefRow
import dev.personal.galleryorganizer.face.FaceAnalyzer
import dev.personal.galleryorganizer.ml.ClipConfig

/** Builds the small result messages for the Categories and People tabs. */
class AnalysisQueries(private val context: Context) {
    companion object {
        const val UNCERTAIN = "_uncertain"
        private const val NO_LIMIT = 2f // scores are ≤ 1
    }

    private val db = AppDatabase.get(context)
    private val settings = AppSettings(context)
    private val config: ClipConfig? by lazy { if (ClipConfig.available(context)) ClipConfig.load(context) else null }

    suspend fun categorySummaries(): List<CategorySummaryMsg> {
        val cfg = config ?: return emptyList()
        val dao = db.clothing()
        val t = settings.clothingThreshold
        val above = dao.countsAbove(t).associate { it.label to it.count }
        val out = cfg.categories.map { c ->
            val min = if (c.clothing) t else 0f
            val count = if (c.clothing) above[c.key] ?: 0 else dao.countAll(c.key)
            val cover = if (count > 0) dao.assetsForLabel(c.key, min, NO_LIMIT, 1, 0).firstOrNull() else null
            summary(c.key, c.label, c.clothing, count, cover)
        }.toMutableList()
        val keys = cfg.clothingKeys
        val uncertain = dao.countUncertain(keys, t)
        val cover = if (uncertain > 0) dao.uncertain(keys, t, 1, 0).firstOrNull() else null
        // Clothing first (as ordered in categories.yaml), then low-confidence, then negatives.
        val (clothing, negatives) = out.partition { it.isClothing }
        return clothing + summary(UNCERTAIN, "Low confidence", true, uncertain, cover) + negatives
    }

    suspend fun assetsByCategory(key: String, page: Int, size: Int): List<AssetRefMsg> {
        val cfg = config ?: return emptyList()
        val dao = db.clothing()
        val t = settings.clothingThreshold
        val offset = page * size
        val rows = when {
            key == UNCERTAIN -> dao.uncertain(cfg.clothingKeys, t, size, offset)
            cfg.categories.firstOrNull { it.key == key }?.clothing == true -> dao.assetsForLabel(key, t, NO_LIMIT, size, offset)
            else -> dao.assetsForLabel(key, 0f, NO_LIMIT, size, offset)
        }
        return rows.map { it.toMsg() }
    }

    suspend fun people(includeHidden: Boolean, includeSingles: Boolean): List<PersonMsg> {
        val minPhotos = if (includeSingles || settings.showSinglePhotoPeople) 1 else 2
        val rows = db.persons().summaries(includeHidden, minPhotos)
        val covers = db.faces().faceRefs(rows.mapNotNull { it.coverFaceId }.ifEmpty { listOf(-1L) }).associateBy { it.id }
        return rows.map { p ->
            val c = p.coverFaceId?.let { covers[it] }
            PersonMsg(
                id = p.id,
                name = p.name,
                hidden = p.hidden,
                photoCount = p.photoCount.toLong(),
                faceCount = p.faceCount.toLong(),
                coverAssetId = c?.assetId?.toString(),
                coverWidth = (c?.width ?: 0).toLong(),
                coverHeight = (c?.height ?: 0).toLong(),
                coverBox = c?.let {
                    FaceBoxMsg(it.boxLeft.toDouble(), it.boxTop.toDouble(), it.boxRight.toDouble(), it.boxBottom.toDouble())
                },
            )
        }
    }

    suspend fun assetsByPerson(personId: Long, page: Int, size: Int): List<AssetRefMsg> =
        db.faces().assetsOfPerson(personId, size, page * size).map { it.toMsg() }

    suspend fun assetDetails(assetId: Long): AssetDetailsMsg {
        val cfg = config
        val c = db.clothing().byAsset(assetId)
        val labels = if (c == null || cfg == null) {
            emptyList()
        } else {
            listOfNotNull(
                c.label1 to c.score1,
                c.label2?.let { k -> c.score2?.let { k to it } },
                c.label3?.let { k -> c.score3?.let { k to it } },
            ).map { (k, s) -> LabelScoreMsg(k, cfg.labelOf(k), s.toDouble()) }
        }
        val people = db.faces().personsInAsset(assetId).mapNotNull { db.persons().byId(it) }.map {
            PersonMsg(
                id = it.id, name = it.name, hidden = it.hidden,
                photoCount = 0, faceCount = it.faceCount.toLong(), coverWidth = 0, coverHeight = 0,
            )
        }
        return AssetDetailsMsg(analyzed = c != null, labels = labels, people = people)
    }

    suspend fun fillDebug(base: DebugInfoMsg): DebugInfoMsg = base.copy(
        clothingProcessed = db.clothing().countProcessed().toLong(),
        faceScanned = db.faces().countScanned().toLong(),
        faceCount = db.faces().countFaces().toLong(),
        peopleCount = db.persons().count().toLong(),
        clothingEncoderVersion = config?.encoderVersion,
        clothingLabelsVersion = config?.labelsVersion,
        faceModelVersion = FaceAnalyzer.MODEL_VERSION + if (FaceAnalyzer.available(context)) "" else " (missing)",
        categories = categorySummaries(),
    )

    private fun summary(key: String, label: String, clothing: Boolean, count: Int, cover: AssetRefRow?) = CategorySummaryMsg(
        key = key,
        label = label,
        isClothing = clothing,
        count = count.toLong(),
        coverAssetId = cover?.id?.toString(),
        coverWidth = (cover?.width ?: 0).toLong(),
        coverHeight = (cover?.height ?: 0).toLong(),
    )
}

fun AssetRefRow.toMsg() = AssetRefMsg(
    assetId = id.toString(),
    width = width.toLong(),
    height = height.toLong(),
    isVideo = isVideo,
    score = score?.toDouble(),
    albumName = bucketName,
)
