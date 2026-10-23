package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import app.fieldwatch.domain.Geo
import app.fieldwatch.domain.GpsSample
import kotlin.math.cos
import kotlin.math.hypot

class HuntTest {
    private val now = 100_000L

    @Test
    fun veryCloseBeatsCloserWhenScreamingLoud() {
        val samples = listOf(
            RssiSample(now - 6_000, -70),
            RssiSample(now - 5_000, -68),
            RssiSample(now - 1_200, -38),
            RssiSample(now - 400, -36),
        )
        assertEquals(HuntCue.VERY_CLOSE, Hunt.cue(samples, now, now - 400, missing = false))
    }

    @Test
    fun belowVeryCloseStillUsesRelativeCue() {
        val samples = listOf(
            RssiSample(now - 6_000, -72),
            RssiSample(now - 5_000, -70),
            RssiSample(now - 1_200, -60),
            RssiSample(now - 400, -58),
        )
        assertEquals(HuntCue.CLOSER, Hunt.cue(samples, now, now - 400, missing = false))
    }

    @Test
    fun quietWinsOverALoudLastPacket() {
        val samples = listOf(RssiSample(now - 9_000, -30))
        assertEquals(HuntCue.QUIET, Hunt.cue(samples, now, now - 9_000, missing = false))
    }

    @Test
    fun goneWins() {
        assertEquals(HuntCue.GONE, Hunt.cue(emptyList(), now, now, missing = true))
    }

    @Test
    fun tickSilentWhenQuietGoneOrNoRssi() {
        assertEquals(null, Hunt.tickIntervalMs(-40, HuntCue.QUIET))
        assertEquals(null, Hunt.tickIntervalMs(-40, HuntCue.GONE))
        assertEquals(null, Hunt.tickIntervalMs(null, HuntCue.CLOSER))
    }

    @Test
    fun tickFasterWhenLouder() {
        val slow = Hunt.tickIntervalMs(-90, HuntCue.SAME)!!
        val mid = Hunt.tickIntervalMs(-65, HuntCue.CLOSER)!!
        val fast = Hunt.tickIntervalMs(-40, HuntCue.VERY_CLOSE)!!
        assertEquals(Hunt.TICK_SLOW_MS, slow)
        assertEquals(Hunt.TICK_FAST_MS, fast)
        assertTrue(mid in (fast + 1) until slow)
        val veryClose = Hunt.tickIntervalMs(-45, HuntCue.VERY_CLOSE)!!
        assertTrue(veryClose < mid)
        assertTrue(veryClose > fast)
    }

    @Test
    fun heatRampRunsFromPoorToPowerful() {
        assertEquals(null, HuntHeat.level(127))
        assertEquals(0f, HuntHeat.level(-95)!!, 0.001f)
        assertEquals(0f, HuntHeat.level(-120)!!, 0.001f)
        assertEquals(1f, HuntHeat.level(-45)!!, 0.001f)
        assertEquals(1f, HuntHeat.level(-20)!!, 0.001f)
        val mid = HuntHeat.level(-70)!!
        assertTrue(mid > HuntHeat.level(-85)!!)
        assertTrue(mid < HuntHeat.level(-55)!!)
        assertEquals(0xFF1E4BFF.toInt(), HuntHeat.colorArgb(0f))
        assertEquals(0xFF00C8FF.toInt(), HuntHeat.colorArgb(0.2f))
        assertEquals(0xFF2DFF6A.toInt(), HuntHeat.colorArgb(0.4f))
        assertEquals(0xFFFFE033.toInt(), HuntHeat.colorArgb(0.6f))
        assertEquals(0xFFFF7A00.toInt(), HuntHeat.colorArgb(0.8f))
        assertEquals(0xFFFF1E1E.toInt(), HuntHeat.colorArgb(1f))
        val between = HuntHeat.colorArgb(0.1f)
        assertTrue(between != HuntHeat.colorArgb(0f))
        assertTrue(between != HuntHeat.colorArgb(0.2f))
    }

    @Test
    fun heatConnectsTheWalkAndChangesColorAlongIt() {
        assertTrue(HuntHeat.connects(HuntHeat.STEP_M))
        assertTrue(HuntHeat.connects(20.0))
        assertTrue(!HuntHeat.connects(HuntHeat.BRIDGE_M + 1.0))
        assertEquals(1f, HuntHeat.ribbonWeight(0.0), 0.001f)
        assertEquals(1f, HuntHeat.ribbonWeight(HuntHeat.COVER_M * 0.5), 0.001f)
        assertEquals(0f, HuntHeat.ribbonWeight(HuntHeat.RIBBON_M), 0.001f)
        assertTrue(HuntHeat.bridges(8.0))
        assertTrue(!HuntHeat.bridges(HuntHeat.CLOSE_M + 1.0))
        assertTrue(!HuntHeat.bridges(0.2))
        assertEquals(0.2f, HuntHeat.alongLevel(0.2f, 0.8f, 0.0), 0.001f)
        assertEquals(0.5f, HuntHeat.alongLevel(0.2f, 0.8f, 0.5), 0.001f)
        assertEquals(0.8f, HuntHeat.alongLevel(0.2f, 0.8f, 1.0), 0.001f)
    }

    @Test
    fun heatKeepsTheLouderHearWhileStanding() {
        val lat = 36.2361322
        val lon = -115.0820944
        val near = lat + 0.4 / 110_540.0
        assertTrue(Geo.meters(lat, lon, near, lon) < HuntHeat.STEP_M)
        var trail = HuntHeat.note(emptyList(), lat, lon, -70, 1_000L)
        trail = HuntHeat.note(trail, near, lon, -62, 2_000L)
        assertEquals(1, trail.size)
        assertEquals(-62, trail[0].rssi)
        trail = HuntHeat.note(trail, near, lon, -80, 3_000L)
        assertEquals(1, trail.size)
        assertEquals(-62, trail[0].rssi)
        assertTrue(HuntHeat.note(trail, lat, lon, 127, 4_000L) === trail)
        assertTrue(HuntHeat.note(trail, 0.0, 0.0, -40, 5_000L) === trail)
    }

    @Test
    fun heatMarkSitsInTheHottestPatch() {
        val cols = 4
        val sumW = FloatArray(8) { 1f }
        val sumL = floatArrayOf(0.4f, 0.4f, 0.4f, 0.4f, 1f, 1f, 1f, 1f)
        val fade = FloatArray(8) { 1f }
        val spot = HuntHeat.hotSpot(sumW, sumL, fade, cols)
        assertNotNull(spot)
        assertEquals(2f, spot!!.first, 0.01f)
        assertEquals(1.5f, spot.second, 0.01f)
        fade[0] = 0.1f
        sumL[0] = 1f
        val still = HuntHeat.hotSpot(sumW, sumL, fade, cols)
        assertNotNull(still)
        assertEquals(2f, still!!.first, 0.01f)
        assertNull(HuntHeat.hotSpot(sumW, sumL, FloatArray(8) { 0.1f }, cols))
    }

    @Test
    fun heatStepsWhenThePhoneMovesAndDropsTheOldest() {
        val lat = 36.2361322
        val lon = -115.0820944
        val far = lat + 3.0 / 110_540.0
        assertTrue(Geo.meters(lat, lon, far, lon) >= HuntHeat.STEP_M)
        val two = HuntHeat.note(
            HuntHeat.note(emptyList(), lat, lon, -80, 1_000L),
            far, lon, -50, 2_000L,
        )
        assertEquals(2, two.size)
        assertEquals(-80, two[0].rssi)
        assertEquals(-50, two[1].rssi)
        assertEquals(-50, HuntHeat.loudest(two)!!.rssi)
        var trail = emptyList<GpsSample>()
        repeat(HuntHeat.CAP + 3) { i ->
            trail = HuntHeat.note(trail, lat + i * 20.0 / 110_540.0, lon, -70, i.toLong())
        }
        assertEquals(HuntHeat.CAP, trail.size)
        assertEquals(3L, trail.first().at)
    }

    @Test
    fun huntOpensCloseAndPinchStopsAtAFewMeters() {
        val lat = 36.2361322
        val lon = -115.0820944
        val model = SitPathPlot.Model(
            samples = listOf(GpsSample(1L, lat, lon, -60)),
            dots = emptyList(),
            lengthM = 0.0,
            spanM = 0.0,
            title = "",
            frameSamples = listOf(GpsSample(0L, lat, lon)),
            minHalfSpanM = HuntHeat.OPEN_HALF_M,
        )
        val lay = SitPathPlot.layout(model, 1080f, 1800f)!!
        val half = lay.metersPerPx * (lay.plotRight - lay.plotLeft) / 2f
        assertTrue(half > 10f)
        assertTrue(half < 22f)
        val tight = SitPathPlot.Viewport(lat, lon, 12f, 12f)
            .zoomAbout(4f, lat, lon, minHalfM = HuntHeat.PINCH_HALF_M)
        assertEquals(HuntHeat.PINCH_HALF_M, tight.halfEastM, 0.05f)
        assertEquals(HuntHeat.PINCH_HALF_M, tight.halfNorthM, 0.05f)
    }

    @Test
    fun stepsMoveThePhoneAndALooseGpsFixDoesNotPullThemBack() {
        val track = HuntTrack()
        val lat = 36.2361322
        val lon = -115.0820944
        assertEquals(null, track.position())
        assertTrue(!track.step())
        track.gps(lat, lon, 5f)
        track.heading(0.0)
        assertTrue(track.step())
        val moved = track.position()!!
        val north = Geo.meters(lat, lon, moved.first, moved.second)
        assertEquals(HuntTrack.STRIDE_M, north, 0.05)
        track.gps(lat + 3.0 / 110_540.0, lon, 5f)
        val held = track.position()!!
        assertEquals(moved.first, held.first, 1e-7)
        track.gps(lat + 20.0 / 110_540.0, lon, 5f)
        val recentered = track.position()!!
        assertEquals(lat + 20.0 / 110_540.0, recentered.first, 1e-7)
        assertEquals(lon, recentered.second, 1e-7)
    }

    @Test
    fun huntStaysCloseOnTheDotWhenTheWalkIsLong() {
        val lat = 36.2361322
        val lon = -115.0820944
        val far = lat + 200.0 / 110_540.0
        val trail = listOf(
            GpsSample(1L, lat, lon, -80),
            GpsSample(2L, far, lon, -50),
        )
        val model = HuntHeat.followModel(lat, lon, trail)
        val lay = SitPathPlot.layout(model, 1080f, 1800f)!!
        val half = lay.metersPerPx * (lay.plotRight - lay.plotLeft) / 2f
        assertTrue(half > 10f)
        assertTrue(half < 22f)
        val dot = lay.project(lat, lon)
        val cx = (lay.plotLeft + lay.plotRight) / 2f
        val cy = (lay.plotTop + lay.plotBottom) / 2f
        assertTrue(hypot(dot.x - cx, dot.y - cy) < 2.0)
        val away = lay.project(far, lon)
        assertTrue(away.y < lay.plotTop || away.y > lay.plotBottom)
    }

    @Test
    fun leavingHuntKeepsTheSameRadio() {
        assertTrue(!HuntHeat.resumes(null, "a", fresh = false))
        assertTrue(HuntHeat.resumes("a", "a", fresh = false))
        assertTrue(!HuntHeat.resumes("a", "a", fresh = true))
        assertTrue(!HuntHeat.resumes("a", "b", fresh = false))
    }

    @Test
    fun aTurnInPlaceDoesNotMoveTheDot() {
        val track = HuntTrack()
        val lat = 36.2361322
        val lon = -115.0820944
        track.gps(lat, lon, 5f, 1_000L)
        track.heading(0.0, 1_000L)
        assertTrue(track.step(1_000L))
        val after = track.position()!!
        track.heading(70.0, 1_400L)
        assertTrue(!track.step(1_400L))
        val held = track.position()!!
        assertEquals(after.first, held.first, 1e-9)
        assertEquals(after.second, held.second, 1e-9)
        track.heading(70.0, 1_800L)
        assertTrue(!track.step(1_800L))
        track.heading(70.0, 2_200L)
        assertTrue(track.step(2_200L))
        val walked = track.position()!!
        assertTrue(walked.first > after.first)
    }

    @Test
    fun aMovingGpsFixSetsTheStepDirection() {
        val track = HuntTrack()
        val lat = 36.2361322
        val lon = -115.0820944
        val cos0 = cos(Math.toRadians(lat)).coerceAtLeast(0.2)
        val eastLon = lon + 6.0 / (111_320.0 * cos0)
        track.gps(lat, lon, 5f, 10_000L)
        track.heading(0.0, 10_000L)
        track.gps(lat, eastLon, 5f, 11_000L)
        track.heading(0.0, 11_200L)
        assertTrue(track.step(11_200L))
        val moved = track.position()!!
        val northM = (moved.first - lat) * 110_540.0
        val eastM = (moved.second - lon) * 111_320.0 * cos0
        assertEquals(0.0, northM, 0.05)
        assertEquals(HuntTrack.STRIDE_M, eastM, 0.05)
        track.heading(0.0, 15_001L)
        assertTrue(track.step(15_001L))
        val later = track.position()!!
        val northLater = (later.first - lat) * 110_540.0
        assertEquals(HuntTrack.STRIDE_M, northLater, 0.05)
    }

    @Test
    fun aStepNeedsARiseAndAFall() {
        val steps = HuntStepDetector()
        assertTrue(!steps.sample(9.8f, 0L))
        assertTrue(steps.sample(12.4f, 100L))
        assertTrue(!steps.sample(13.0f, 200L))
        assertTrue(!steps.sample(12.6f, 500L))
        assertTrue(!steps.sample(9.5f, 600L))
        assertTrue(steps.sample(12.2f, 1_000L))
    }
}
