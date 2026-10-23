package app.fieldwatch.data

import app.fieldwatch.domain.MapKey
import app.fieldwatch.domain.MapKeyService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Keys for map services, one per service. Not part of Export settings.
 * A value that is not a token is dropped on read and never written back.
 */
class MapKeyStore(private val file: File) {
    private val lock = Any()
    private val keys = LinkedHashMap<MapKeyService, String>()
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    init {
        synchronized(lock) { keys.putAll(readFile()) }
    }

    fun read(service: MapKeyService): String? = synchronized(lock) { keys[service] }

    fun save(service: MapKeyService, raw: String): Boolean {
        val token = MapKey.accept(raw) ?: return false
        synchronized(lock) {
            if (keys[service] == token) return true
            keys[service] = token
            writeFile()
            _revision.value = _revision.value + 1
        }
        return true
    }

    fun clear(service: MapKeyService) {
        synchronized(lock) {
            if (keys.remove(service) == null) return
            writeFile()
            _revision.value = _revision.value + 1
        }
    }

    private fun readFile(): Map<MapKeyService, String> {
        val text = runCatching { if (file.isFile) file.readText() else "" }.getOrDefault("")
        return parse(text)
    }

    private fun writeFile() {
        val parent = file.parentFile
        if (parent != null && !parent.isDirectory) parent.mkdirs()
        val tmp = File(parent, "${file.name}.tmp")
        tmp.writeText(encode(keys))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    companion object {
        /** Only known service names and accepted tokens. Anything else is ignored. */
        fun parse(text: String): Map<MapKeyService, String> {
            val out = LinkedHashMap<MapKeyService, String>()
            var i = 0
            fun skip() {
                while (i < text.length && text[i].isWhitespace()) i++
            }
            skip()
            if (i >= text.length || text[i] != '{') return emptyMap()
            i++
            skip()
            if (i < text.length && text[i] == '}') return emptyMap()
            while (i < text.length) {
                skip()
                if (i >= text.length || text[i] != '"') return emptyMap()
                i++
                val nameStart = i
                while (i < text.length && text[i] != '"') {
                    if (text[i] == '\\' || text[i].code < 0x20) return emptyMap()
                    i++
                }
                if (i >= text.length) return emptyMap()
                val name = text.substring(nameStart, i)
                i++
                skip()
                if (i >= text.length || text[i] != ':') return emptyMap()
                i++
                skip()
                if (i >= text.length || text[i] != '"') return emptyMap()
                i++
                val valueStart = i
                while (i < text.length && text[i] != '"') {
                    if (text[i] == '\\' || text[i].code < 0x20) return emptyMap()
                    i++
                }
                if (i >= text.length) return emptyMap()
                val value = text.substring(valueStart, i)
                i++
                val service = MapKeyService.entries.firstOrNull { it.name == name }
                val token = MapKey.accept(value)
                if (service != null && token != null) out[service] = token
                skip()
                if (i < text.length && text[i] == ',') {
                    i++
                    continue
                }
                if (i < text.length && text[i] == '}') return out
                return emptyMap()
            }
            return emptyMap()
        }

        fun encode(keys: Map<MapKeyService, String>): String {
            val parts = ArrayList<String>()
            for (service in MapKeyService.entries) {
                val token = MapKey.accept(keys[service]) ?: continue
                parts += "\"${service.name}\":\"$token\""
            }
            return "{" + parts.joinToString(",") + "}"
        }
    }
}
