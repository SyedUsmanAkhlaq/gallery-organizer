package dev.personal.galleryorganizer.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.edit
import dev.personal.galleryorganizer.diag.ErrorLog
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** A loaded ONNX model plus the execution provider ("delegate") it runs on. */
class OrtModel(
    private val env: OrtEnvironment,
    private val session: OrtSession,
    val delegate: String,
) : Closeable {
    private val inputName: String = session.inputNames.first()

    /** Runs [input] (remaining elements = product of [shape]); returns rows of the first output. */
    fun run(input: FloatBuffer, shape: LongArray): Array<FloatArray> {
        OnnxTensor.createTensor(env, input, shape).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { result ->
                @Suppress("UNCHECKED_CAST")
                return result[0].value as Array<FloatArray>
            }
        }
    }

    override fun close() = session.close()
}

object OrtModels {
    const val AUTO = "auto"
    private const val PREFS = "ml_runtime"

    fun directFloats(count: Int): FloatBuffer =
        ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    /** Copies a bundled asset to no-backup storage once per app install/update (ORT mmaps files). */
    fun assetFile(context: Context, assetPath: String): File {
        val dest = File(context.noBackupFilesDir, assetPath)
        val stamp = File(dest.path + ".stamp")
        val version = appUpdateTime(context).toString()
        if (!dest.exists() || !stamp.exists() || stamp.readText() != version) {
            dest.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input -> dest.outputStream().use { input.copyTo(it) } }
            stamp.writeText(version)
        }
        return dest
    }

    fun assetExists(context: Context, assetPath: String): Boolean =
        runCatching { context.assets.open(assetPath).close() }.isSuccess

    private fun appUpdateTime(context: Context): Long = runCatching {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0)
        }
        info.lastUpdateTime
    }.getOrDefault(0L)

    /**
     * Loads [file]. With accelerator "auto", tries NNAPI, XNNPACK and plain
     * CPU once per model version, times a warm-up on [sampleShape], and
     * remembers the fastest. If NNAPI crashes the process during the probe,
     * the next start notices the unfinished probe and disables NNAPI.
     */
    fun load(context: Context, file: File, cacheKey: String, sampleShape: LongArray, accelerator: String): OrtModel {
        val env = OrtEnvironment.getEnvironment()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean("probe_in_progress", false)) {
            prefs.edit(commit = true) {
                putBoolean("nnapi_broken", true)
                putBoolean("probe_in_progress", false)
            }
            ErrorLog.record("ml", "NNAPI probe did not finish last time; NNAPI disabled", null)
        }
        val nnapiAllowed = accelerator == AUTO && !prefs.getBoolean("nnapi_broken", false)
        val choiceKey = "choice_$cacheKey"
        val cached = prefs.getString(choiceKey, null)

        fun make(kind: String): OrtModel? = try {
            val opts = OrtSession.SessionOptions()
            when (kind) {
                "NNAPI" -> opts.addNnapi()
                "XNNPACK" -> {
                    opts.addXnnpack(mapOf("intra_op_num_threads" to "4"))
                    opts.setIntraOpNumThreads(1)
                }
                else -> opts.setIntraOpNumThreads(4)
            }
            opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            OrtModel(env, env.createSession(file.absolutePath, opts), kind)
        } catch (e: Exception) {
            ErrorLog.record("ml:$kind", e)
            null
        }

        if (accelerator != AUTO) return make("CPU") ?: error("CPU session failed")
        if (cached != null && (cached != "NNAPI" || nnapiAllowed)) {
            make(cached)?.let { return it }
        }

        val kinds = buildList {
            if (nnapiAllowed) add("NNAPI")
            add("XNNPACK")
            add("CPU")
        }
        var best: OrtModel? = null
        var bestMs = Double.MAX_VALUE
        prefs.edit(commit = true) { putBoolean("probe_in_progress", true) }
        try {
            for (kind in kinds) {
                val model = make(kind) ?: continue
                val ms = runCatching { timeWarmup(model, sampleShape) }.getOrElse {
                    ErrorLog.record("ml:$kind", it)
                    Double.MAX_VALUE
                }
                prefs.edit { putFloat("ms_${cacheKey}_$kind", ms.toFloat()) }
                if (ms < bestMs) {
                    best?.close()
                    best = model
                    bestMs = ms
                } else {
                    model.close()
                }
            }
        } finally {
            prefs.edit(commit = true) { putBoolean("probe_in_progress", false) }
        }
        val chosen = best ?: make("CPU") ?: error("no ONNX session could be created")
        prefs.edit { putString(choiceKey, chosen.delegate) }
        return chosen
    }

    private fun timeWarmup(model: OrtModel, shape: LongArray): Double {
        val n = shape.fold(1L) { a, b -> a * b }.toInt()
        val buf = directFloats(n)
        var best = Double.MAX_VALUE
        repeat(3) { i ->
            buf.clear()
            buf.limit(n)
            val t = System.nanoTime()
            model.run(buf, shape)
            val ms = (System.nanoTime() - t) / 1e6
            if (i > 0 && ms < best) best = ms // first run includes lazy init
        }
        return best
    }
}
