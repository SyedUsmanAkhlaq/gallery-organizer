package dev.personal.galleryorganizer.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ErrorLogFormatTest {
    @Test
    fun `paths are stripped`() {
        val s = ErrorLogFormat.sanitize(
            "open failed: /storage/emulated/0/DCIM/Camera/IMG_1.jpg: ENOENT and /data/user/0/dev.x/files/a.db",
        )
        assertFalse(s.contains("DCIM"))
        assertFalse(s.contains("dev.x"))
        assertEquals("open failed: <path>: ENOENT and <path>", s)
    }

    @Test
    fun `format and parse round-trip`() {
        val entries = listOf(
            ErrorEntry(1_700_000_000_000, "kotlin:scan", "boom\nsecond line", "at a.b(C.kt:1)\n=== fake header\nat d.e"),
            ErrorEntry(1_700_000_000_001, "flutter", "no stack", null),
        )
        val parsed = ErrorLogFormat.parse(entries.joinToString("") { ErrorLogFormat.format(it) })
        assertEquals(2, parsed.size)
        assertEquals("kotlin:scan", parsed[0].source)
        assertEquals("boom ⏎ second line", parsed[0].message)
        assertEquals("at a.b(C.kt:1)\n === fake header\nat d.e", parsed[0].stack)
        assertEquals(1_700_000_000_001, parsed[1].timestampMillis)
        assertNull(parsed[1].stack)
    }

    @Test
    fun `garbage before first header is ignored`() {
        assertEquals(0, ErrorLogFormat.parse("random\ntext\n").size)
    }
}
