package app.fieldwatch.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportTest {
    @Test
    fun ordinaryExitsAreNotACrash() {
        listOf(0, 1, 2, 3, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16).forEach { reason ->
            assertNull(CrashReport.kind(reason, status = 11))
            assertFalse(CrashReport.showExit(reason, 11, atMs = 50, seenAtMs = 0))
        }
    }

    @Test
    fun aJavaOrNativeCrashIsShownOnce() {
        assertEquals("Java crash", CrashReport.kind(CrashReport.REASON_CRASH, 0))
        assertEquals("Native crash, signal 11", CrashReport.kind(CrashReport.REASON_CRASH_NATIVE, 11))
        assertEquals("Native crash", CrashReport.kind(CrashReport.REASON_CRASH_NATIVE, 0))
        assertTrue(CrashReport.showExit(CrashReport.REASON_CRASH_NATIVE, 11, atMs = 50, seenAtMs = 0))
        assertFalse(CrashReport.showExit(CrashReport.REASON_CRASH_NATIVE, 11, atMs = 50, seenAtMs = 50))
        assertTrue(CrashReport.showExit(CrashReport.REASON_CRASH, 0, atMs = 51, seenAtMs = 50))
    }

    @Test
    fun theNoteNamesThePhoneAndDropsTheMemoryDump() {
        val text = CrashReport.text(
            versionName = "1.1.28",
            versionCode = 38,
            manufacturer = "samsung",
            model = "SM-G975U",
            androidRelease = "12",
            sdk = 31,
            atLabel = "2026-10-22 14:40:00 UTC",
            reason = "Native crash, signal 11",
            detail = "signal 11 (SIGSEGV)\n#00 pc 0000abcd /system/lib64/libbluetooth_jni.so",
        )
        assertTrue(text.contains("Fieldwatch 1.1.28 (38)"))
        assertTrue(text.contains("samsung SM-G975U"))
        assertTrue(text.contains("Android 12 (SDK 31)"))
        assertTrue(text.contains("Native crash, signal 11"))
        assertFalse(text.contains("MAC"))
        val raw = """
            pid: 123
            signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0
            memory near x0:
            00000000aabbccdd 00112233445566778899aabbccddeeff
            backtrace:
                  #00 pc 0000abcd /system/lib64/libbluetooth_jni.so
            some advertised network should not be kept if it is not a frame
        """.trimIndent()
        val kept = CrashReport.traceLines(raw)
        assertTrue(kept.contains("signal 11"))
        assertTrue(kept.contains("#00 pc"))
        assertTrue(kept.contains("backtrace"))
        assertFalse(kept.contains("aabbccdd"))
        assertFalse(kept.contains("advertised"))
    }

    @Test
    fun aBinaryTraceIsDropped() {
        val bytes = ByteArray(40) { 0 }
        assertNull(CrashReport.usableText(bytes))
        assertEquals("at app.Field", CrashReport.usableText("at app.Field".toByteArray()))
    }
}
