package app.fieldwatch.data

import app.fieldwatch.domain.GnssRawFormat
import app.fieldwatch.domain.GnssRawSession
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GnssRawRecording(
    val name: String,
    val bytes: Long,
    val startedMs: Long,
) {
    fun label(): String {
        val whenText = SimpleDateFormat("MMM d, HH:mm:ss", Locale.US).format(Date(startedMs))
        return "$whenText · ${sizeLabel(bytes)}"
    }

    companion object {
        fun sizeLabel(bytes: Long): String = when {
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
            else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
}

/** App-private GnssLogger files. Newest first. Oldest are deleted past 20 MB. */
class GnssRawFiles(private val dir: File) {
    private val lock = Any()
    private var writer: BufferedWriter? = null
    private var open: File? = null
    private val _recordings = MutableStateFlow<List<GnssRawRecording>>(emptyList())
    val recordings: StateFlow<List<GnssRawRecording>> = _recordings.asStateFlow()

    init {
        refresh()
    }

    fun file(name: String): File? = runCatching {
        if (!GnssRawFormat.acceptableName(name)) return null
        val child = File(dir, name)
        if (!child.isFile) return null
        val parent = child.canonicalFile.parentFile ?: return null
        if (parent != dir.canonicalFile) return null
        child
    }.getOrNull()

    fun open(startedMs: Long, header: String): Boolean = synchronized(lock) {
        closeWriter()
        if (!dir.exists() && !dir.mkdirs()) return false
        var name = GnssRawFormat.fileName(startedMs)
        var attempt = startedMs
        while (File(dir, name).exists()) {
            attempt += 1_000L
            name = GnssRawFormat.fileName(attempt)
        }
        val file = File(dir, name)
        val opened = runCatching {
            val out = file.bufferedWriter()
            out.write(header)
            out.flush()
            writer = out
            open = file
        }
        if (opened.isFailure) {
            file.delete()
            writer = null
            open = null
            return false
        }
        refreshLocked()
        true
    }

    fun write(text: String): Boolean = synchronized(lock) {
        val out = writer ?: return false
        if (text.isEmpty()) return true
        runCatching {
            out.write(text)
            out.flush()
        }.isSuccess
    }

    fun close() = synchronized(lock) {
        closeWriter()
        trimLocked()
        refreshLocked()
    }

    /** Privacy, or a write that failed. The open file is removed. */
    fun abort() = synchronized(lock) {
        val file = open
        closeWriter()
        file?.delete()
        refreshLocked()
    }

    fun refresh() = synchronized(lock) { refreshLocked() }

    private fun closeWriter() {
        runCatching { writer?.flush() }
        runCatching { writer?.close() }
        writer = null
        open = null
    }

    private fun trimLocked() {
        val files = completedOldest()
        val drop = GnssRawFormat.victims(
            files.map { it.name to it.length() },
            GnssRawSession.CAP_BYTES,
        )
        for (name in drop) File(dir, name).delete()
    }

    private fun refreshLocked() {
        val rows = completedOldest().mapNotNull { file ->
            val started = GnssRawFormat.startedMs(file.name) ?: file.lastModified()
            GnssRawRecording(file.name, file.length(), started)
        }.asReversed()
        _recordings.value = rows
    }

    private fun completedOldest(): List<File> {
        val current = open
        return dir.listFiles()
            ?.filter { it.isFile && GnssRawFormat.acceptableName(it.name) && it != current }
            ?.sortedBy { GnssRawFormat.startedMs(it.name) ?: it.lastModified() }
            .orEmpty()
    }
}
