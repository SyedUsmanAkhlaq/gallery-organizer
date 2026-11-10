package dev.personal.galleryorganizer.diag

import android.content.Context
import java.io.File

/**
 * Last [MAX_ENTRIES] errors, kept in memory and in `files/logs/errors.log`
 * (app-private, excluded from backup). Everything is sanitized on the way in.
 * Errors from Flutter arrive through the Pigeon bridge, so there is one log.
 */
object ErrorLog {
    const val MAX_ENTRIES = 100
    private const val MAX_MESSAGE = 2_000
    private const val MAX_STACK = 8_000

    private var file: File? = null
    private val entries = ArrayDeque<ErrorEntry>()

    fun init(context: Context) {
        synchronized(this) {
            val f = File(context.filesDir, "logs/errors.log")
            f.parentFile?.mkdirs()
            file = f
            entries.clear()
            if (f.exists()) {
                runCatching { entries.addAll(ErrorLogFormat.parse(f.readText()).takeLast(MAX_ENTRIES)) }
            }
        }
    }

    fun logsDir(context: Context): File = File(context.filesDir, "logs").apply { mkdirs() }

    fun record(source: String, error: Throwable) {
        record(source, error.toString(), error.stackTraceToString())
    }

    fun record(source: String, message: String, stack: String?) {
        synchronized(this) { append(source, message, stack) }
    }

    private fun append(source: String, message: String, stack: String?) {
        entries.addLast(
            ErrorEntry(
                timestampMillis = System.currentTimeMillis(),
                source = source.take(40),
                message = ErrorLogFormat.sanitize(message).take(MAX_MESSAGE),
                stack = stack?.let { ErrorLogFormat.sanitize(it).take(MAX_STACK) },
            ),
        )
        while (entries.size > MAX_ENTRIES) entries.removeFirst()
        persist()
    }

    /** Newest first. */
    fun recent(): List<ErrorEntry> = synchronized(this) { entries.toList().asReversed() }

    fun count(): Int = synchronized(this) { entries.size }

    fun clear() {
        synchronized(this) {
            entries.clear()
            persist()
        }
    }

    /** Full log text, oldest first. */
    fun asText(): String = synchronized(this) { entries.joinToString("") { ErrorLogFormat.format(it) } }

    private fun persist() {
        val f = file ?: return
        runCatching { f.writeText(entries.joinToString("") { ErrorLogFormat.format(it) }) }
    }
}
