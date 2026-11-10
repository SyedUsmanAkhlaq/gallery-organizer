package dev.personal.galleryorganizer

import android.content.Context
import androidx.core.content.edit
import dev.personal.galleryorganizer.face.FaceClustering
import dev.personal.galleryorganizer.ml.DEFAULT_CLOTHING_THRESHOLD

/** User-tunable settings (SharedPreferences, excluded from backup). */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** 0 = strict (fewer merges), 1 = loose. */
    var clusteringSensitivity: Float
        get() = prefs.getFloat("clusteringSensitivity", FaceClustering.DEFAULT_SENSITIVITY)
        set(v) = prefs.edit { putFloat("clusteringSensitivity", v.coerceIn(0f, 1f)) }

    /** Minimum top-1 score to put a photo into a clothing category. */
    var clothingThreshold: Float
        get() = prefs.getFloat("clothingThreshold", DEFAULT_CLOTHING_THRESHOLD)
        set(v) = prefs.edit { putFloat("clothingThreshold", v.coerceIn(0.05f, 0.99f)) }

    var showSinglePhotoPeople: Boolean
        get() = prefs.getBoolean("showSinglePhotoPeople", false)
        set(v) = prefs.edit { putBoolean("showSinglePhotoPeople", v) }

    /** "auto" (probe NNAPI / XNNPACK / CPU, keep the fastest) or "cpu". */
    var accelerator: String
        get() = prefs.getString("accelerator", "auto") ?: "auto"
        set(v) = prefs.edit { putString("accelerator", if (v == "cpu") "cpu" else "auto") }
}
