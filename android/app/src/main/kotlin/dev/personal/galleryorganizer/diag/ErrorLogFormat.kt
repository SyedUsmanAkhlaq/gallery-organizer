package dev.personal.galleryorganizer.diag

import java.time.Instant

data class ErrorEntry(
    val timestampMillis: Long,
    val source: String,
    val message: String,
    val stack: String?,
)

/**
 * Pure (Android-free) helpers for the on-device error log, unit-tested.
 *
 * File format, human readable so the shared file can be read directly:
 * ```
 * === <epochMillis> <ISO-8601 UTC> [<source>] <one-line message>
 * <stack trace lines...>
 * ```
 */
object ErrorLogFormat {
    private const val HEADER = "=== "
    private val HEADER_RE = Regex("""^=== (\d+) \S+ \[([^\]]*)] ?(.*)$""")

    /** Absolute file-system paths are replaced (privacy: never log paths). */
    private val PATH_RE =
        Regex("""(/storage|/sdcard|/mnt|/data/media|/data/user/\d+|/data/data|/data/user_de/\d+)/[^\s'":,)\]]*""")

    fun sanitize(text: String): String = PATH_RE.replace(text, "<path>")

    fun format(entry: ErrorEntry): String = buildString {
        append(HEADER)
        append(entry.timestampMillis).append(' ')
        append(Instant.ofEpochMilli(entry.timestampMillis).toString()).append(' ')
        append('[').append(entry.source.replace(']', ')').replace('\n', ' ')).append("] ")
        append(entry.message.replace('\r', ' ').replace("\n", " ⏎ "))
        append('\n')
        entry.stack?.trimEnd()?.takeIf { it.isNotEmpty() }?.let {
            // A stack line must never look like an entry header.
            append(it.lines().joinToString("\n") { line -> if (line.startsWith(HEADER)) " $line" else line })
            append('\n')
        }
    }

    fun parse(text: String): List<ErrorEntry> {
        val out = mutableListOf<ErrorEntry>()
        var header: MatchResult? = null
        val stack = StringBuilder()
        fun flush() {
            val h = header ?: return
            out += ErrorEntry(
                timestampMillis = h.groupValues[1].toLong(),
                source = h.groupValues[2],
                message = h.groupValues[3],
                stack = stack.toString().trimEnd().ifEmpty { null },
            )
            stack.clear()
        }
        for (line in text.lineSequence()) {
            val m = HEADER_RE.find(line)
            if (m != null) {
                flush()
                header = m
            } else if (header != null) {
                stack.append(line).append('\n')
            }
        }
        flush()
        return out
    }
}
