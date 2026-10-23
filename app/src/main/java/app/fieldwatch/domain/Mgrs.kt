package app.fieldwatch.domain

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan
import kotlin.math.truncate

/**
 * WGS84 latitude and longitude to a 1 m MGRS string.
 * Polar regions, south of 80°S and north of 84°N, return null.
 * The letter math follows the same UTM grouping as the public mgrs forward conversion.
 */
object Mgrs {
    private const val A = 65
    private const val I = 73
    private const val O = 79
    private const val V = 86
    private const val Z = 90
    private const val ECC_SQUARED = 0.00669438
    private const val SCALE = 0.9996
    private const val AXIS = 6_378_137.0
    private const val EASTING_OFFSET = 500_000.0
    private const val NORTHING_OFFSET = 10_000_000.0
    private const val SETS = 6
    private const val COLUMN_ORIGIN = "AJSAJS"
    private const val ROW_ORIGIN = "AFAFAF"

    /** Spaced 1 m grid, such as `11S PA 72349 11844`. Null outside the UTM bands. */
    fun format(lat: Double, lon: Double): String? {
        val compact = forward(lat, lon) ?: return null
        val zoneDigits = if (compact[1].isDigit()) 2 else 1
        val head = zoneDigits + 1
        val square = compact.substring(head, head + 2)
        val digits = compact.substring(head + 2)
        val half = digits.length / 2
        return compact.substring(0, head) + " " + square + " " +
            digits.substring(0, half) + " " + digits.substring(half)
    }

    /** Compact 1 m grid. Null when [lat] is outside -80..84. */
    fun forward(lat: Double, lon: Double): String? {
        if (!lat.isFinite() || !lon.isFinite()) return null
        if (lon < -180.0 || lon > 180.0 || lat < -80.0 || lat > 84.0) return null
        val utm = toUtm(lat, lon)
        val east = truncate(utm.easting).toLong()
        val north = truncate(utm.northing).toLong()
        val eastText = east.toString().padStart(5, '0')
        val northText = north.toString().padStart(5, '0')
        return utm.zone.toString() + utm.band +
            squareId(east, north, utm.zone) +
            eastText.takeLast(5) +
            northText.takeLast(5)
    }

    private data class Utm(val easting: Double, val northing: Double, val zone: Int, val band: Char)

    private fun toUtm(lat: Double, lon: Double): Utm {
        var zone = floor((lon + 180.0) / 6.0).toInt() + 1
        if (lon == 180.0) zone = 60
        if (lat >= 56.0 && lat < 64.0 && lon >= 3.0 && lon < 12.0) zone = 32
        if (lat >= 72.0 && lat < 84.0) {
            zone = when {
                lon >= 0.0 && lon < 9.0 -> 31
                lon >= 9.0 && lon < 21.0 -> 33
                lon >= 21.0 && lon < 33.0 -> 35
                lon >= 33.0 && lon < 42.0 -> 37
                else -> zone
            }
        }
        val origin = (zone - 1) * 6.0 - 180.0 + 3.0
        val latRad = Math.toRadians(lat)
        val eccPrime = ECC_SQUARED / (1.0 - ECC_SQUARED)
        val n = AXIS / kotlin.math.sqrt(1.0 - ECC_SQUARED * sin(latRad) * sin(latRad))
        val t = tan(latRad) * tan(latRad)
        val c = eccPrime * cos(latRad) * cos(latRad)
        val a = cos(latRad) * Math.toRadians(lon - origin)
        val m = AXIS * (
            (1.0 - ECC_SQUARED / 4.0 - 3.0 * ECC_SQUARED * ECC_SQUARED / 64.0 - 5.0 * ECC_SQUARED * ECC_SQUARED * ECC_SQUARED / 256.0) * latRad -
                (3.0 * ECC_SQUARED / 8.0 + 3.0 * ECC_SQUARED * ECC_SQUARED / 32.0 + 45.0 * ECC_SQUARED * ECC_SQUARED * ECC_SQUARED / 1024.0) * sin(2.0 * latRad) +
                (15.0 * ECC_SQUARED * ECC_SQUARED / 256.0 + 45.0 * ECC_SQUARED * ECC_SQUARED * ECC_SQUARED / 1024.0) * sin(4.0 * latRad) -
                (35.0 * ECC_SQUARED * ECC_SQUARED * ECC_SQUARED / 3072.0) * sin(6.0 * latRad)
            )
        val easting = SCALE * n * (
            a + (1.0 - t + c) * a * a * a / 6.0 +
                (5.0 - 18.0 * t + t * t + 72.0 * c - 58.0 * eccPrime) * a * a * a * a * a / 120.0
            ) + EASTING_OFFSET
        var northing = SCALE * (
            m + n * tan(latRad) * (
                a * a / 2.0 +
                    (5.0 - t + 9.0 * c + 4.0 * c * c) * a * a * a * a / 24.0 +
                    (61.0 - 58.0 * t + t * t + 600.0 * c - 330.0 * eccPrime) * a * a * a * a * a * a / 720.0
                )
            )
        if (lat < 0.0) northing += NORTHING_OFFSET
        return Utm(easting, northing, zone, band(lat))
    }

    private fun band(lat: Double): Char {
        if (lat >= 72.0) return 'X'
        val letters = "CDEFGHJKLMNPQRSTUVWX"
        val index = floor((lat - -80.0) / 8.0).toInt()
        return letters[index]
    }

    private fun squareId(easting: Long, northing: Long, zone: Int): String {
        var set = zone % SETS
        if (set == 0) set = SETS
        val column = (easting / 100_000L).toInt()
        val row = ((northing / 100_000L) % 20L).toInt()
        return letters(column, row, set)
    }

    private fun letters(column: Int, row: Int, set: Int): String {
        val index = set - 1
        val colOrigin = COLUMN_ORIGIN[index].code
        val rowOrigin = ROW_ORIGIN[index].code
        var col = colOrigin + column - 1
        var line = rowOrigin + row
        var rollover = false
        if (col > Z) {
            col = col - Z + A - 1
            rollover = true
        }
        if (col == I || (colOrigin < I && col > I) || ((col > I || colOrigin < I) && rollover)) col++
        if (col == O || (colOrigin < O && col > O) || ((col > O || colOrigin < O) && rollover)) {
            col++
            if (col == I) col++
        }
        if (col > Z) col = col - Z + A - 1
        if (line > V) {
            line = line - V + A - 1
            rollover = true
        } else {
            rollover = false
        }
        if (line == I || (rowOrigin < I && line > I) || ((line > I || rowOrigin < I) && rollover)) line++
        if (line == O || (rowOrigin < O && line > O) || ((line > O || rowOrigin < O) && rollover)) {
            line++
            if (line == I) line++
        }
        if (line > V) line = line - V + A - 1
        return Char(col).toString() + Char(line)
    }
}
