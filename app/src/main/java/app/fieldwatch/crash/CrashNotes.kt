package app.fieldwatch.crash

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import app.fieldwatch.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A crash note is written when a Java crash kills the process, and read back
 * before the rest of startup. A native crash is read from Android on the next
 * open, on Android 11 and newer. A normal exit leaves no note.
 */
object CrashNotes {
    data class Pending(val text: String, val exitAt: Long)

    private const val NOTE = "crash-note.txt"
    private const val SEEN = "crash-exit-seen.txt"
    private val writing = AtomicBoolean(false)

    fun install(app: Application) {
        val current = Thread.getDefaultUncaughtExceptionHandler()
        if (current is Handler) return
        Thread.setDefaultUncaughtExceptionHandler(Handler(app, current))
    }

    fun pending(context: Context): Pending? {
        val written = readNote(context)
        val exit = readExit(context)
        if (written == null && exit == null) return null
        return Pending(written ?: exit!!.text, exit?.at ?: 0L)
    }

    fun dismiss(context: Context, exitAt: Long) {
        noteFile(context).delete()
        if (exitAt > 0L) {
            runCatching { seenFile(context).writeText(exitAt.toString()) }
        }
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val stack = StringWriter()
        error.printStackTrace(PrintWriter(stack))
        val text = CrashReport.text(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            androidRelease = Build.VERSION.RELEASE.orEmpty(),
            sdk = Build.VERSION.SDK_INT,
            atLabel = CrashReport.timeLabel(System.currentTimeMillis()),
            reason = "Java crash",
            detail = "Thread: ${thread.name}\n${stack.toString().take(12_000)}",
        )
        val file = noteFile(context)
        file.outputStream().use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
    }

    private fun readNote(context: Context): String? {
        val file = noteFile(context)
        if (!file.isFile) return null
        val text = runCatching { file.readText(Charsets.UTF_8).take(16_000).trim() }.getOrNull()
        return text?.takeIf { it.isNotEmpty() }
    }

    private fun readExit(context: Context): Exit? {
        if (Build.VERSION.SDK_INT < 30) return null
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return null
        val record = am.getHistoricalProcessExitReasons(context.packageName, 0, 1)
            .firstOrNull() ?: return null
        val reason = CrashReport.kind(record.reason, record.status) ?: return null
        val seen = readSeen(context)
        if (!CrashReport.showExit(record.reason, record.status, record.timestamp, seen)) return null
        val described = record.description?.trim().orEmpty().takeIf { it.length in 1..180 }.orEmpty()
        val trace = traceOf(record)
        val detail = listOf(described, trace).filter { it.isNotBlank() }.joinToString("\n")
        val text = CrashReport.text(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            androidRelease = Build.VERSION.RELEASE.orEmpty(),
            sdk = Build.VERSION.SDK_INT,
            atLabel = CrashReport.timeLabel(record.timestamp),
            reason = reason,
            detail = detail,
        )
        return Exit(record.timestamp, text)
    }

    private fun traceOf(record: android.app.ApplicationExitInfo): String {
        val bytes = runCatching {
            record.traceInputStream?.use { input ->
                val buf = ByteArray(8_192)
                val n = input.read(buf)
                if (n <= 0) null else buf.copyOf(n)
            }
        }.getOrNull() ?: return ""
        val text = CrashReport.usableText(bytes) ?: return ""
        return CrashReport.traceLines(text)
    }

    private fun readSeen(context: Context): Long =
        runCatching { seenFile(context).readText().trim().toLong() }.getOrDefault(0L)

    private fun noteFile(context: Context) = File(context.filesDir, NOTE)
    private fun seenFile(context: Context) = File(context.filesDir, SEEN)

    private data class Exit(val at: Long, val text: String)

    private class Handler(
        private val app: Application,
        private val previous: Thread.UncaughtExceptionHandler?,
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, error: Throwable) {
            if (writing.compareAndSet(false, true)) {
                runCatching { write(app, thread, error) }
            }
            if (previous != null && previous !== this) {
                previous.uncaughtException(thread, error)
            }
        }
    }
}
