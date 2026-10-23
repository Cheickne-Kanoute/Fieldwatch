package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoPrivacyTest {
    @Test
    fun screenCoordMasksWhenDemo() {
        assertNull(Geo.screenCoord(null, -122.41941, false))
        assertNull(Geo.screenCoord(37.77492, null, true))
        assertEquals("masked", Geo.screenCoord(37.77492, -122.41941, true))
        assertEquals("37.77492, -122.41941", Geo.screenCoord(37.77492, -122.41941, false))
    }

    @Test
    fun redactCoordsInReplacesSitReportPins() {
        val raw = "Stay  37.77492, -122.41941  (operator phone)\nTransit 37.7750,-122.4200"
        val masked = Geo.redactCoordsIn(raw, true)
        assertTrue(masked.contains("masked"))
        assertFalse(masked.contains("37.77492"))
        assertFalse(masked.contains("-122.41941"))
        assertEquals(raw, Geo.redactCoordsIn(raw, false))
    }

    @Test
    fun redactCoordsInLeavesPathMetersAlone() {
        val path = "GPS path so far 123 m. Keep moving (~50 m). Span 45 m."
        assertEquals(path, Geo.redactCoordsIn(path, true))
    }

    @Test
    fun screenGridAndRangeFollowPrivacy() {
        assertEquals("masked", Geo.screenGrid(36.2361322, -115.0820944, true))
        assertEquals("11S PA 72349 11844", Geo.screenGrid(36.2361322, -115.0820944, false))
        assertNull(Geo.screenGrid(88.0, 0.0, false))
        assertNull(Geo.liveRange(36.2, -115.08, 36.2361322, -115.0820944, true))
        val near = Geo.liveRange(36.2361322, -115.0820944, 36.23616, -115.0820944, false)
        assertTrue(near!!.matches(Regex("""\d+ m""")))
        val north = Geo.reportRange(36.0, -115.0, 36.01, -115.0, 1_700_000_000_000L)
        assertTrue(north!!.startsWith("1.1 km, bearing 000°, from this phone at "))
        val held = Geo.reportRange(36.2361322, -115.0820944, 36.23616, -115.0820944, 1_700_000_000_000L)
        assertTrue(held!!.contains(", from this phone at "))
        assertFalse(held.contains("bearing"))
        assertNull(Geo.reportRange(36.2, -115.08, 36.2361322, -115.0820944, null))
        assertNull(Geo.reportRange(36.2, -115.08, 36.2361322, -115.0820944, 0L))
    }

    @Test
    fun mapFooterTextFollowsTheChoice() {
        assertNull(Geo.mapFooterText(MapFooter.OFF, 36.2, -115.0, false))
        assertNull(Geo.mapFooterText(MapFooter.GPS, null, -115.0, false))
        assertNull(Geo.mapFooterText(MapFooter.GPS, 0.0, 0.0, false))
        assertEquals("36.20000, -115.00000", Geo.mapFooterText(MapFooter.GPS, 36.2, -115.0, false))
        assertEquals("masked", Geo.mapFooterText(MapFooter.GPS, 36.2, -115.0, true))
        assertEquals("masked", Geo.mapFooterText(MapFooter.GRID, 36.2361322, -115.0820944, true))
        assertEquals(
            "11S PA 72349 11844",
            Geo.mapFooterText(MapFooter.GRID, 36.2361322, -115.0820944, false),
        )
        assertNull(Geo.mapFooterText(MapFooter.GRID, 88.0, 0.0, false))
    }

    @Test
    fun mapFooterHoldsStillWhileThePhoneWanders() {
        val first = Geo.settleMapFooter(null, MapFooter.GRID, 36.2361322, -115.0820944, false)
        assertEquals("11S PA 72349 11844", first!!.text)
        val jitter = Geo.settleMapFooter(first, MapFooter.GRID, 36.23614, -115.0820944, false)
        assertSame(first, jitter)
        val stepped = Geo.settleMapFooter(first, MapFooter.GRID, 36.23640, -115.0820944, false)
        assertEquals(false, stepped === first)
        assertTrue(stepped!!.text.isNotEmpty())
        assertEquals(
            "36.23613, -115.08209",
            Geo.settleMapFooter(first, MapFooter.GPS, 36.2361322, -115.0820944, false)!!.text,
        )
        assertEquals(
            "masked",
            Geo.settleMapFooter(first, MapFooter.GRID, 36.2361322, -115.0820944, true)!!.text,
        )
        assertNull(Geo.settleMapFooter(first, MapFooter.GRID, null, null, false))
        assertNull(Geo.settleMapFooter(first, MapFooter.OFF, 36.2361322, -115.0820944, false))
    }

    @Test
    fun redactCoordsInMasksGridAndBearingAndKeepsCourse() {
        val raw = "Grid 11S PA 72349 11844. 840 m, bearing 045°, from this phone at 14:32. " +
            "4 m, from this phone at 14:32. course 100°. GPS path so far 123 m."
        val masked = Geo.redactCoordsIn(raw, true)
        assertFalse(masked.contains("11S"))
        assertFalse(masked.contains("bearing"))
        assertFalse(masked.contains("14:32"))
        assertTrue(masked.contains("course 100°"))
        assertTrue(masked.contains("GPS path so far 123 m"))
        assertEquals(raw, Geo.redactCoordsIn(raw, false))
    }

    @Test
    fun movingWithYouIgnoresWifiAccessPoints() {
        val now = 1_000_000L
        val path = listOf(
            GpsSample(now - 60_000, 37.7700, -122.4200),
            GpsSample(now - 30_000, 37.7710, -122.4200),
            GpsSample(now, 37.7720, -122.4200),
        )
        val ctx = CoTravel.Ctx.of(path)
        assertTrue(ctx.ready)
        val trail = listOf(
            GpsSample(now - 50_000, 37.7702, -122.4200, rssi = -60),
            GpsSample(now - 20_000, 37.7712, -122.4200, rssi = -58),
            GpsSample(now - 1_000, 37.7719, -122.4200, rssi = -55),
        )
        val wifi = radio(RadioKind.WIFI, trail, now)
        val ble = radio(RadioKind.BLE, trail, now)
        assertFalse(CoTravel.withYou(wifi, ctx, now))
        assertTrue(CoTravel.withYou(ble, ctx, now))
    }

    private fun radio(kind: RadioKind, trail: List<GpsSample>, now: Long): Sighting {
        val last = trail.last()
        return Sighting(
            key = "${kind.name}:AA:BB:CC:DD:EE:01",
            kind = kind,
            mac = "AA:BB:CC:DD:EE:01",
            name = if (kind == RadioKind.WIFI) "House" else "Tag",
            rssi = last.rssi,
            rssiMin = -70,
            rssiMax = -50,
            channel = 0,
            frequencyMhz = 0,
            vendor = null,
            randomized = false,
            hiddenSsid = false,
            serviceUuids = emptyList(),
            manufacturerId = null,
            manufacturerDataHex = "",
            rawHex = "",
            extras = "",
            firstSeen = trail.first().at,
            lastSeen = last.at,
            hitCount = trail.size,
            fleetIds = emptySet(),
            rssiHistory = emptyList(),
            presence = emptyList(),
            gpsTrail = trail,
        ).also { require(now - it.lastSeen < CoTravel.HEARD_MS) }
    }
}
