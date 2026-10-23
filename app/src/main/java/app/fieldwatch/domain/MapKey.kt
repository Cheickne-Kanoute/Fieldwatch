package app.fieldwatch.domain

import java.net.URI
import java.net.URLEncoder

/**
 * A map key is data the user pasted. It is never run, never logged, and never
 * written into a path, a report, or Export settings. Only a short token is kept.
 */
enum class MapKeyService {
    ESRI,
    MAPTILER,
    STADIA,
    THUNDERFOREST,
}

enum class MapKeyProblem {
    NONE,
    MISSING,
    DENIED,
    REJECTED,
    FAILED,
}

data class MapKeyNotice(
    val basemap: MapBasemap,
    val problem: MapKeyProblem,
)

object MapKey {
    const val MIN = 8
    const val MAX = 1024

    enum class Verdict { OK, EMPTY, SHORT, DENIED }

    /** Control characters, spaces, and anything outside the token set are refused. */
    fun verdict(raw: String): Verdict {
        if (raw.any { it == '\n' || it == '\r' || it.code < 0x20 || it.code == 0x7F }) return Verdict.DENIED
        val text = raw.trim()
        if (text.isEmpty()) return Verdict.EMPTY
        if (text.any { !allowed(it) }) return Verdict.DENIED
        if (text.length > MAX) return Verdict.DENIED
        if (text.length < MIN) return Verdict.SHORT
        return Verdict.OK
    }

    fun accept(raw: String?): String? {
        if (raw == null) return null
        val text = raw.trim()
        if (verdict(text) != Verdict.OK) return null
        return text
    }

    private fun allowed(c: Char): Boolean =
        c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '_' || c == '.'
}

/**
 * Builds a tile address from the fixed service URL plus a checked key.
 * The key is only a query value. A bad key returns null and is not placed in the address.
 */
object MapTileUrl {
    fun build(basemap: MapBasemap, z: Int, x: Int, y: Int, key: String?): String? {
        if (z !in 0..22 || x < 0 || y < 0) return null
        val n = 1 shl z
        if (x >= n || y >= n) return null
        val fixed = basemap.fixedTileUrl(z, x, y)
        if (!fixed.startsWith("https://${basemap.host}/")) return null
        if (fixed.any { it.code < 0x20 || it == '?' || it == '#' || it == ' ' }) return null
        if (!basemap.needsKey) return fixed
        val token = MapKey.accept(key) ?: return null
        val query = basemap.keyQuery ?: return null
        val encoded = URLEncoder.encode(token, Charsets.UTF_8.name())
        if (encoded != token) return null
        val url = "$fixed?$query=$encoded"
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host != basemap.host || uri.userInfo != null) return null
        if (url.count { it == '?' } != 1) return null
        val value = url.substringAfter('=')
        if (value != token) return null
        return url
    }

    /** A redirect may stay on this service. It may not carry the key to another host. */
    fun allowsRedirect(basemap: MapBasemap, location: String, key: String): Boolean {
        if (key.isNotEmpty() && location.contains(key)) return false
        val uri = runCatching { URI(location) }.getOrNull() ?: return false
        if (uri.scheme != "https" || uri.userInfo != null) return false
        val host = uri.host?.lowercase() ?: return false
        return basemap.redirectRoots.any { root -> host == root || host.endsWith(".$root") }
    }
}
