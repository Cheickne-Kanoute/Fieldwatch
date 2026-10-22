package app.fieldwatch.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.fieldwatch.domain.GpsSample
import app.fieldwatch.domain.SitPathPlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Optional OSM raster under Reports → Path and the letter path figure.
 * Same switch as Online place names and maps. Privacy mode does not hide tiles.
 * Never throws; offline or a failed fetch returns an empty list (plain trace).
 */
object PathTiles {
    data class Tile(
        val bitmap: Bitmap,
        val north: Double,
        val south: Double,
        val west: Double,
        val east: Double,
    )

    suspend fun load(
        context: Context,
        samples: List<GpsSample>,
        onlineLookup: Boolean,
        forPrint: Boolean = false,
    ): List<Tile> = withContext(Dispatchers.IO) {
        runCatching {
            if (!onlineLookup) return@withContext emptyList()
            if (samples.isEmpty()) return@withContext emptyList()
            if (!PlaceLookup.online(context)) return@withContext emptyList()
            val expanded = expandToPlot(
                samples.minOf { it.lat },
                samples.maxOf { it.lat },
                samples.minOf { it.lon },
                samples.maxOf { it.lon },
            )
            loadBox(
                context,
                expanded[0],
                expanded[1],
                expanded[2],
                expanded[3],
                forPrint,
            )
        }.getOrDefault(emptyList())
    }

    /** Tiles for the area on screen. [south]/[north]/[west]/[east] are the padded view. */
    suspend fun loadBounds(
        context: Context,
        south: Double,
        north: Double,
        west: Double,
        east: Double,
        onlineLookup: Boolean,
    ): List<Tile> = withContext(Dispatchers.IO) {
        runCatching {
            if (!onlineLookup) return@withContext emptyList()
            if (!PlaceLookup.online(context)) return@withContext emptyList()
            loadBox(context, south, north, west, east)
        }.getOrDefault(emptyList())
    }

    /** Same tile set means the same key. A move that leaves those tiles changes it. */
    internal fun coverageKey(south: Double, north: Double, west: Double, east: Double): String =
        tilePlan(south, north, west, east)?.key ?: "off"

    private fun loadBox(
        context: Context,
        south: Double,
        north: Double,
        west: Double,
        east: Double,
        forPrint: Boolean = false,
    ): List<Tile> {
        val plan = tilePlan(south, north, west, east, forPrint) ?: return emptyList()
        val cache = File(context.cacheDir, "osm").apply { mkdirs() }
        val out = ArrayList<Tile>(plan.tiles.size)
        for ((x, y) in plan.tiles) {
            val bmp = tileBitmap(cache, plan.z, x, y) ?: continue
            out += Tile(
                bitmap = bmp,
                north = tileLat(y, plan.z),
                south = tileLat(y + 1, plan.z),
                west = tileLon(x, plan.z),
                east = tileLon(x + 1, plan.z),
            )
        }
        return out
    }

    private data class TilePlan(val z: Int, val tiles: List<Pair<Int, Int>>, val key: String)

    /** Zoom the PDF actually uses after the print cap. Screen callers leave [forPrint] false. */
    internal fun plannedZoom(
        south: Double,
        north: Double,
        west: Double,
        east: Double,
        forPrint: Boolean = false,
    ): Int = tilePlan(south, north, west, east, forPrint)?.z ?: -1

    private fun tilePlan(
        south: Double,
        north: Double,
        west: Double,
        east: Double,
        forPrint: Boolean = false,
    ): TilePlan? {
        if (!south.isFinite() || !north.isFinite() || !west.isFinite() || !east.isFinite()) return null
        if (north <= south || east <= west) return null
        val midLat = (south + north) / 2.0
        val spanM = app.fieldwatch.domain.Geo.meters(south, west, north, east).coerceAtLeast(40.0)
        val cap = if (forPrint) PRINT_TILE_CAP else SCREEN_TILE_CAP
        val maxZ = if (forPrint) PRINT_MAX_Z else SCREEN_MAX_Z
        var z = (zoomFor(spanM, midLat) + if (forPrint) 2 else 0).coerceIn(8, maxZ)
        var tiles = tileBox(south, north, west, east, z)
        while (tiles.size > cap && z > 8) {
            z--
            tiles = tileBox(south, north, west, east, z)
        }
        if (tiles.isEmpty()) return null
        val x0 = tiles.minOf { it.first }
        val x1 = tiles.maxOf { it.first }
        val y0 = tiles.minOf { it.second }
        val y1 = tiles.maxOf { it.second }
        return TilePlan(z, tiles, "$z:$x0:$x1:$y0:$y1")
    }

    /** Grow the path bbox to the Path plot aspect so tiles fill the allotted box, not a skinny strip. */
    internal fun expandToPlot(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
        targetAspect: Double = 1.7,
    ): DoubleArray {
        val midLat = (minLat + maxLat) / 2.0
        val midLon = (minLon + maxLon) / 2.0
        val cos = cos(Math.toRadians(midLat)).absoluteValue.coerceAtLeast(0.2)
        val minM = (SitPathPlot.MIN_HALF_SPAN_M * 2f * 1.22f).toDouble()
        var yM = ((maxLat - minLat) * 110_540.0).coerceAtLeast(minM)
        var xM = ((maxLon - minLon) * 111_320.0 * cos).coerceAtLeast(minM)
        if (xM / yM < targetAspect) {
            xM = yM * targetAspect
        } else {
            yM = xM / targetAspect
        }
        xM *= 1.35
        yM *= 1.35
        val dLat = yM / 2.0 / 110_540.0
        val dLon = xM / 2.0 / (111_320.0 * cos)
        return doubleArrayOf(midLat - dLat, midLat + dLat, midLon - dLon, midLon + dLon)
    }

    private const val SCREEN_TILE_CAP = 20
    private const val PRINT_TILE_CAP = 96
    private const val SCREEN_MAX_Z = 17
    private const val PRINT_MAX_Z = 19

    internal fun zoomFor(spanM: Double, lat: Double): Int {
        val earth = 40_075_016.686 * cos(Math.toRadians(lat)).absoluteValue.coerceAtLeast(0.2)
        for (z in 17 downTo 8) {
            val tileM = earth / (1 shl z)
            if (tileM * 2.2 >= spanM) return z
        }
        return 8
    }

    private fun tileBox(
        minLat: Double, maxLat: Double, minLon: Double, maxLon: Double, z: Int,
    ): List<Pair<Int, Int>> {
        val x0 = lon2tile(minLon, z)
        val x1 = lon2tile(maxLon, z)
        val y0 = lat2tile(maxLat, z)
        val y1 = lat2tile(minLat, z)
        val n = 1 shl z
        val xs = (minOf(x0, x1).coerceIn(0, n - 1)..maxOf(x0, x1).coerceIn(0, n - 1))
        val ys = (minOf(y0, y1).coerceIn(0, n - 1)..maxOf(y0, y1).coerceIn(0, n - 1))
        val out = ArrayList<Pair<Int, Int>>()
        for (x in xs) for (y in ys) out += x to y
        return out
    }

    private fun lon2tile(lon: Double, z: Int): Int {
        val n = 1 shl z
        return floor((lon + 180.0) / 360.0 * n).toInt()
    }

    private fun lat2tile(lat: Double, z: Int): Int {
        val n = 1 shl z
        val latRad = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
        return floor((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n).toInt()
    }

    private fun tileLon(x: Int, z: Int): Double {
        val n = 1 shl z
        return x.toDouble() / n * 360.0 - 180.0
    }

    private fun tileLat(y: Int, z: Int): Double {
        val n = 1 shl z
        val t = PI - 2.0 * PI * y / n
        return Math.toDegrees(atan(sinh(t)))
    }

    private fun tileBitmap(cache: File, z: Int, x: Int, y: Int): Bitmap? {
        val file = File(cache, "${z}_${x}_$y.png")
        if (file.isFile && file.length() > 64) {
            return BitmapFactory.decodeFile(file.absolutePath)
        }
        val url = URL("https://tile.openstreetmap.org/$z/$x/$y.png")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 4000
            readTimeout = 4000
            instanceFollowRedirects = true
            setRequestProperty(
                "User-Agent",
                "Fieldwatch/${app.fieldwatch.BuildConfig.VERSION_NAME} (https://github.com/OffGridPete/Fieldwatch)",
            )
        }
        return try {
            if (conn.responseCode != 200) return null
            val bytes = conn.inputStream.use { it.readBytes() }
            if (bytes.size < 64) return null
            runCatching { file.writeBytes(bytes) }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }
}
