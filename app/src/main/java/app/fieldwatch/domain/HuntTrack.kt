package app.fieldwatch.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * Where this phone is during a hunt.
 * GPS anchors the walk. Steps fill the meters GPS is too coarse to see.
 * A moving GPS fix sets the step direction. The top of the phone is used
 * when that fix is stuck. A tight fix that has actually moved recenters it.
 */
class HuntTrack {
    private var anchorLat = 0.0
    private var anchorLon = 0.0
    private var anchored = false
    private var east = 0.0
    private var north = 0.0
    private var headingDeg = 0.0
    private var haveHeading = false
    private var rateHeading = 0.0
    private var rateAt = Long.MIN_VALUE
    private var turnRate = 0.0
    private var stepHeading = 0.0
    private var haveStepHeading = false
    private var courseAt = Long.MIN_VALUE
    private var onCourse = false
    private var lastGpsLat = 0.0
    private var lastGpsLon = 0.0
    private var haveLastGps = false

    fun clear() {
        anchored = false
        east = 0.0
        north = 0.0
        haveHeading = false
        headingDeg = 0.0
        rateAt = Long.MIN_VALUE
        turnRate = 0.0
        haveStepHeading = false
        stepHeading = 0.0
        courseAt = Long.MIN_VALUE
        onCourse = false
        haveLastGps = false
    }

    /** [accuracyM] is the GPS reading. A loose fix does not pull the steps back. */
    fun gps(lat: Double, lon: Double, accuracyM: Float, atMs: Long = 0L) {
        if (!PayloadLocation.validCoord(lat, lon)) return
        val acc = accuracyM.toDouble().coerceIn(1.0, 30.0)
        if (!anchored) {
            anchorLat = lat
            anchorLon = lon
            anchored = true
            east = 0.0
            north = 0.0
            lastGpsLat = lat
            lastGpsLon = lon
            haveLastGps = true
            return
        }
        if (haveLastGps && acc <= GPS_TRUST_M) {
            val (dEast, dNorth, moved) = offset(lastGpsLat, lastGpsLon, lat, lon)
            if (moved >= max(acc, COURSE_MIN_M)) {
                headingDeg = norm(Math.toDegrees(atan2(dEast, dNorth)))
                haveHeading = true
                courseAt = atMs
                onCourse = true
                turnRate = 0.0
            }
        }
        lastGpsLat = lat
        lastGpsLon = lon
        haveLastGps = true
        val (_, _, moved) = offset(anchorLat, anchorLon, lat, lon)
        if (acc <= GPS_TRUST_M && moved >= max(acc, GPS_RECENTER_M)) {
            anchorLat = lat
            anchorLon = lon
            east = 0.0
            north = 0.0
        }
    }

    /**
     * Degrees clockwise from north. Zero is the top of the phone pointing north.
     * Ignored while a GPS course from a real move is still fresh.
     */
    fun heading(deg: Double, atMs: Long = 0L) {
        val next = norm(deg)
        if (rateAt == Long.MIN_VALUE) {
            rateHeading = next
            rateAt = atMs
        } else if (atMs - rateAt >= RATE_WINDOW_MS) {
            val dt = (atMs - rateAt) / 1000.0
            if (dt > 0.0) turnRate = angleDiff(next, rateHeading) / dt
            rateHeading = next
            rateAt = atMs
        }
        if (!courseFresh(atMs)) {
            if (onCourse) {
                haveStepHeading = false
                turnRate = 0.0
                onCourse = false
            }
            headingDeg = next
            haveHeading = true
        }
    }

    /** One stride along the current heading. False until GPS and a heading exist. */
    fun step(atMs: Long = 0L): Boolean {
        if (!anchored || !haveHeading) return false
        if (hypot(east, north) >= STEP_CAP_M) return false
        if (!courseFresh(atMs)) {
            if (turnRate > TURN_RATE_DPS) return false
            if (haveStepHeading && angleDiff(headingDeg, stepHeading) > TURN_DEG) {
                stepHeading = headingDeg
                haveStepHeading = true
                return false
            }
        }
        stepHeading = headingDeg
        haveStepHeading = true
        val rad = Math.toRadians(headingDeg)
        north += STRIDE_M * cos(rad)
        east += STRIDE_M * sin(rad)
        return true
    }

    fun position(): Pair<Double, Double>? {
        if (!anchored) return null
        val cos0 = cos(Math.toRadians(anchorLat)).coerceAtLeast(0.2)
        val lat = anchorLat + north / 110_540.0
        val lon = anchorLon + east / (111_320.0 * cos0)
        return lat to lon
    }

    private fun courseFresh(atMs: Long): Boolean =
        courseAt != Long.MIN_VALUE && atMs - courseAt in 0..COURSE_HOLD_MS

    private fun offset(
        lat0: Double,
        lon0: Double,
        lat: Double,
        lon: Double,
    ): Triple<Double, Double, Double> {
        val cos0 = cos(Math.toRadians(lat0)).coerceAtLeast(0.2)
        val dNorth = (lat - lat0) * 110_540.0
        val dEast = (lon - lon0) * 111_320.0 * cos0
        return Triple(dEast, dNorth, hypot(dEast, dNorth))
    }

    private fun norm(deg: Double) = ((deg % 360.0) + 360.0) % 360.0

    private fun angleDiff(a: Double, b: Double): Double {
        val d = norm(a - b)
        return if (d > 180.0) 360.0 - d else d
    }

    companion object {
        const val STRIDE_M = 0.72
        /** Steps stop extending once this far from the last GPS anchor. */
        const val STEP_CAP_M = 60.0
        const val GPS_RECENTER_M = 8.0
        const val GPS_TRUST_M = 12.0
        /** A GPS move at least this far, and at least the accuracy, sets the step direction. */
        const val COURSE_MIN_M = 4.0
        /** Phone heading takes over when GPS has not moved for this long. */
        const val COURSE_HOLD_MS = 4_000L
        /** A step whose heading jumped this far is a turn, not a stride. */
        const val TURN_DEG = 45.0
        /** Faster than this, the phone is turning and the step is dropped. */
        const val TURN_RATE_DPS = 25.0
        private const val RATE_WINDOW_MS = 250L
    }
}

/**
 * A walking step from the accelerometer magnitude, gravity included.
 * Peaks re-arm only after the magnitude falls, and not faster than a stride.
 */
class HuntStepDetector {
    private var armed = true
    private var lastAt = 0L

    fun reset() {
        armed = true
        lastAt = 0L
    }

    fun sample(magnitude: Float, atMs: Long): Boolean {
        if (lastAt != 0L && atMs - lastAt < MIN_GAP_MS) {
            if (magnitude < LOW) armed = true
            return false
        }
        if (magnitude >= HIGH && armed) {
            armed = false
            lastAt = atMs
            return true
        }
        if (magnitude < LOW) armed = true
        return false
    }

    companion object {
        const val HIGH = 11.3f
        const val LOW = 9.9f
        const val MIN_GAP_MS = 320L
    }
}
