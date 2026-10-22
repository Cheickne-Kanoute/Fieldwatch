package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SitPathPlotTest {
    @Test
    fun northIsUpAndEastIsRight() {
        val south = GpsSample(1L, 28.7800, -81.3700)
        val north = GpsSample(2L, 28.7810, -81.3700)
        val east = GpsSample(3L, 28.7810, -81.3690)
        val model = SitPathPlot.Model(
            samples = listOf(south, north, east),
            dots = emptyList(),
            lengthM = 200.0,
            spanM = 150.0,
            title = "test",
        )
        val layout = SitPathPlot.layout(model, 400f, 300f)!!
        assertEquals(3, layout.path.size)
        assertTrue(layout.path[1].y < layout.path[0].y)
        assertTrue(layout.path[2].x > layout.path[1].x)
    }

    @Test
    fun tallPathCentersHorizontally() {
        val south = GpsSample(1L, 28.7800, -81.3700)
        val north = GpsSample(2L, 28.7850, -81.3700)
        val model = SitPathPlot.Model(
            samples = listOf(south, north),
            dots = emptyList(),
            lengthM = 550.0,
            spanM = 550.0,
            title = "tall",
        )
        val layout = SitPathPlot.layout(model, 400f, 300f)!!
        val mid = 200f
        layout.path.forEach { pt ->
            assertTrue(kotlin.math.abs(pt.x - mid) < 20f)
        }
    }

    @Test
    fun oneAdvertisedFixStillFrames() {
        val model = SitPathPlot.Model(
            samples = emptyList(),
            dots = emptyList(),
            lengthM = 0.0,
            spanM = 0.0,
            title = "AIRCRAFT",
            frameSamples = listOf(GpsSample(1L, 28.78, -81.36)),
            minHalfSpanM = 140f,
        )
        val layout = SitPathPlot.layout(model, 400f, 300f, pad = 12f, scaleBarReserve = 0f)
        assertNotNull(layout)
        val pt = layout!!.project(28.78, -81.36)
        assertTrue(pt.x > layout.plotLeft && pt.x < layout.plotRight)
        assertTrue(pt.y > layout.plotTop && pt.y < layout.plotBottom)
        assertTrue(layout.path.isEmpty())
    }

    @Test
    fun advertisedFixDoesNotExtendThePhoneLine() {
        val model = SitPathPlot.Model(
            samples = listOf(
                GpsSample(1L, 28.78, -81.37),
                GpsSample(2L, 28.781, -81.37),
            ),
            dots = emptyList(),
            lengthM = 100.0,
            spanM = 100.0,
            title = "walk",
            frameSamples = listOf(GpsSample(3L, 28.80, -81.37)),
        )
        val layout = SitPathPlot.layout(model, 400f, 300f)!!
        assertEquals(2, layout.path.size)
        val craft = layout.project(28.80, -81.37)
        assertTrue(craft.y < layout.path[0].y)
        assertTrue(craft.y > layout.plotTop)
    }

    @Test
    fun shortPathOpensEnoughGroundToReadTheStreets() {
        val model = SitPathPlot.Model(
            samples = listOf(
                GpsSample(1L, 28.78000, -81.37000),
                GpsSample(2L, 28.78002, -81.37000),
            ),
            dots = emptyList(),
            lengthM = 2.0,
            spanM = 2.0,
            title = "still",
        )
        val layout = SitPathPlot.layout(model, 400f, 300f)!!
        val here = layout.project(28.78000, -81.37000)
        val north = layout.project(28.78200, -81.37000)
        val meters = 0.002 * 110_540.0
        val pxPerM = (here.y - north.y) / meters
        val viewM = (layout.plotBottom - layout.plotTop) / pxPerM
        assertTrue(viewM in 350.0..600.0)
        assertTrue(here.y > layout.plotTop && here.y < layout.plotBottom)
    }

    @Test
    fun oneGpsFixStillFrames() {
        val model = SitPathPlot.Model(
            samples = listOf(GpsSample(1L, 28.78000, -81.37000)),
            dots = emptyList(),
            lengthM = 0.0,
            spanM = 0.0,
            title = "one",
        )
        val layout = SitPathPlot.layout(model, 400f, 300f)
        assertNotNull(layout)
        val pt = layout!!.project(28.78000, -81.37000)
        assertTrue(pt.x > layout.plotLeft && pt.x < layout.plotRight)
        assertTrue(pt.y > layout.plotTop && pt.y < layout.plotBottom)
    }

    @Test
    fun aLongWalkStillFillsThePlot() {
        val model = SitPathPlot.Model(
            samples = listOf(
                GpsSample(1L, 28.7800, -81.3700),
                GpsSample(2L, 28.8000, -81.3700),
            ),
            dots = emptyList(),
            lengthM = 2200.0,
            spanM = 2200.0,
            title = "drive",
        )
        val layout = SitPathPlot.layout(model, 400f, 300f)!!
        val south = layout.project(28.7800, -81.3700)
        val north = layout.project(28.8000, -81.3700)
        val used = (south.y - north.y) / (layout.plotBottom - layout.plotTop)
        assertTrue(used > 0.7f)
    }

    @Test
    fun scaleBarIsANiceMeterValue() {
        assertEquals(50.0, SitPathPlot.niceMeters(47.0), 0.01)
        assertEquals(100.0, SitPathPlot.niceMeters(80.0), 0.01)
        assertEquals(20.0, SitPathPlot.niceMeters(18.0), 0.01)
    }

    @Test
    fun nearbyDotsBecomeOneStackedCluster() {
        val a = SitPathPlot.Dot("a", 0.0, 0.0, "A", extraAttention = true, named = false)
        val b = SitPathPlot.Dot("b", 0.0, 0.0, "B", extraAttention = true, named = false)
        val c = SitPathPlot.Dot("c", 0.0, 0.0, "C", extraAttention = false, named = true)
        val stacked = SitPathPlot.clusters(
            listOf(
                a to SitPathPlot.Pt(10f, 10f),
                b to SitPathPlot.Pt(18f, 12f),
                c to SitPathPlot.Pt(200f, 180f),
            ),
            threshPx = 28f,
        )
        assertEquals(2, stacked.size)
        val pile = stacked.first { it.stacked }
        assertEquals(2, pile.members.size)
        assertTrue(stacked.any { !it.stacked && it.members.single().dot.key == "c" })
    }

    @Test
    fun startLabelOpensTheDetectionItCovers() {
        val dot = SitPathPlot.Dot("a", 0.0, 0.0, "A", extraAttention = false, named = true)
        val cluster = SitPathPlot.clusters(listOf(dot to SitPathPlot.Pt(100f, 100f))).single()
        val start = SitPathPlot.HitMarker(
            x = 100f,
            y = 100f,
            radius = 12f,
            labelLeft = 108f,
            labelTop = 94f,
            labelRight = 180f,
            labelBottom = 112f,
        )
        val onTheWord = SitPathPlot.clusterAt(listOf(cluster), 160f, 100f, listOf(start))
        assertEquals(cluster.id, onTheWord?.id)
        val onTheDot = SitPathPlot.clusterAt(listOf(cluster), 100f, 100f, listOf(start))
        assertEquals(cluster.id, onTheDot?.id)
        val elsewhere = SitPathPlot.clusterAt(listOf(cluster), 160f, 40f, listOf(start))
        assertEquals(null, elsewhere)
    }

    @Test
    fun extraAttentionDotsComeFirstAndNeedAFix() {
        val axon = Fleet(
            id = "fleet-axon",
            name = "Axon",
            kind = SignatureClass.LAW_ENFORCEMENT,
            attentionNote = "Body-worn.",
        )
        val tagged = Sighting(
            key = "WIFI:AA:AA:AA:AA:AA:01",
            kind = RadioKind.WIFI,
            mac = "AA:AA:AA:AA:AA:01",
            name = "cam",
            rssi = -50,
            rssiMin = -50,
            rssiMax = -50,
            channel = 6,
            frequencyMhz = 2437,
            vendor = null,
            randomized = false,
            hiddenSsid = false,
            serviceUuids = emptyList(),
            manufacturerId = null,
            manufacturerDataHex = "",
            rawHex = "",
            extras = "",
            firstSeen = 1L,
            lastSeen = 1L,
            hitCount = 1,
            fleetIds = setOf("fleet-axon"),
            rssiHistory = emptyList(),
            presence = emptyList(),
            gpsTrail = listOf(GpsSample(1L, 28.78, -81.37)),
        )
        val dots = SitPathPlot.dotsFrom(listOf(tagged), listOf(axon), namedKeys = emptySet())
        assertEquals(1, dots.points.size)
        assertTrue(dots.points[0].extraAttention)
        assertNotNull(SitPathPlot.layout(
            SitPathPlot.Model(
                samples = listOf(
                    GpsSample(1L, 28.780, -81.370),
                    GpsSample(2L, 28.781, -81.371),
                ),
                dots = dots.points,
                lengthM = 120.0,
                spanM = 100.0,
                title = "plaza",
            ),
            300f, 200f,
        ))
    }

    @Test
    fun sameRadioPlotsOnceAtLoudestHear() {
        val axon = Fleet(
            id = "fleet-axon",
            name = "Axon",
            kind = SignatureClass.LAW_ENFORCEMENT,
            attentionNote = "Body-worn.",
        )
        val tagged = Sighting(
            key = "WIFI:AA:AA:AA:AA:AA:01",
            kind = RadioKind.WIFI,
            mac = "AA:AA:AA:AA:AA:01",
            name = "cam",
            rssi = -40,
            rssiMin = -80,
            rssiMax = -40,
            channel = 6,
            frequencyMhz = 2437,
            vendor = null,
            randomized = false,
            hiddenSsid = false,
            serviceUuids = emptyList(),
            manufacturerId = null,
            manufacturerDataHex = "",
            rawHex = "",
            extras = "",
            firstSeen = 1L,
            lastSeen = 3L,
            hitCount = 3,
            fleetIds = setOf("fleet-axon"),
            rssiHistory = emptyList(),
            presence = emptyList(),
            gpsTrail = listOf(
                GpsSample(1L, 28.7800, -81.3700, -80),
                GpsSample(2L, 28.7810, -81.3710, -42),
                GpsSample(3L, 28.7820, -81.3720, -70),
            ),
        )
        val dots = SitPathPlot.dotsFrom(listOf(tagged, tagged), listOf(axon), namedKeys = emptySet())
        assertEquals(1, dots.points.size)
        assertEquals(28.7810, dots.points[0].lat, 0.00001)
        assertEquals(-81.3710, dots.points[0].lon, 0.00001)
    }

    @Test
    fun extraAttentionAlongTheWalkIsStillANumberedStop() {
        val axon = Fleet(
            id = "fleet-axon",
            name = "Axon",
            kind = SignatureClass.LAW_ENFORCEMENT,
            attentionNote = "Body-worn.",
        )
        val path = listOf(
            GpsSample(0L, 28.7800, -81.3700),
            GpsSample(60_000L, 28.7850, -81.3700),
        )
        val tagged = Sighting(
            key = "WIFI:AA:AA:AA:AA:AA:01",
            kind = RadioKind.WIFI,
            mac = "AA:AA:AA:AA:AA:01",
            name = "van",
            rssi = -50,
            rssiMin = -60,
            rssiMax = -40,
            channel = 6,
            frequencyMhz = 2437,
            vendor = null,
            randomized = false,
            hiddenSsid = false,
            serviceUuids = emptyList(),
            manufacturerId = null,
            manufacturerDataHex = "",
            rawHex = "",
            extras = "",
            firstSeen = 0L,
            lastSeen = 60_000L,
            hitCount = 8,
            fleetIds = setOf("fleet-axon"),
            rssiHistory = emptyList(),
            presence = emptyList(),
            gpsTrail = listOf(
                GpsSample(0L, 28.7800, -81.3700, -55),
                GpsSample(15_000L, 28.7812, -81.3700, -48),
                GpsSample(30_000L, 28.7825, -81.3700, -40),
                GpsSample(45_000L, 28.7837, -81.3700, -46),
                GpsSample(60_000L, 28.7850, -81.3700, -52),
            ),
        )
        val dots = SitPathPlot.dotsFrom(listOf(tagged), listOf(axon), namedKeys = emptySet())
        assertEquals(1, dots.points.size)
        assertEquals("van", dots.points[0].label)
        assertTrue(dots.points[0].extraAttention)
    }

    @Test
    fun pathPlotsMacAlertsAndSignatureAlerts() {
        val axon = Fleet(
            id = "fleet-axon",
            name = "Axon",
            kind = SignatureClass.LAW_ENFORCEMENT,
            attentionNote = "Body-worn.",
            colorIndex = 7,
        )
        val tagged = Sighting(
            key = "WIFI:AA:AA:AA:AA:AA:01",
            kind = RadioKind.WIFI,
            mac = "AA:AA:AA:AA:AA:01",
            name = "cam",
            rssi = -50,
            rssiMin = -50,
            rssiMax = -50,
            channel = 6,
            frequencyMhz = 2437,
            vendor = null,
            randomized = false,
            hiddenSsid = false,
            serviceUuids = emptyList(),
            manufacturerId = null,
            manufacturerDataHex = "",
            rawHex = "",
            extras = "",
            firstSeen = 1L,
            lastSeen = 1L,
            hitCount = 1,
            fleetIds = setOf("fleet-axon"),
            rssiHistory = emptyList(),
            presence = emptyList(),
            gpsTrail = listOf(GpsSample(1L, 28.78, -81.37)),
        )
        val report = SitPathPlot.dotsFrom(listOf(tagged), listOf(axon), namedKeys = emptySet())
        assertEquals(1, report.points.size)
        val bare = SitPathPlot.dotsFrom(
            listOf(tagged), listOf(axon), namedKeys = emptySet(), alertsOnly = true,
        )
        assertTrue(bare.points.isEmpty())
        val signature = SitPathPlot.dotsFrom(
            listOf(tagged),
            listOf(axon),
            namedKeys = emptySet(),
            watchedFleetIds = setOf(axon.id),
            alertsOnly = true,
        )
        assertEquals(1, signature.points.size)
        assertEquals(SignatureClass.LAW_ENFORCEMENT, signature.points[0].classKind)
        assertEquals(Palette.color(7), signature.points[0].accentArgb)
        val mac = SitPathPlot.dotsFrom(
            listOf(tagged),
            listOf(axon),
            namedKeys = emptySet(),
            bookmarkedKeys = setOf(tagged.key),
            alertsOnly = true,
        )
        assertEquals(1, mac.points.size)
        assertTrue(mac.points[0].named)
        val decoded = tagged.copy(
            payloadLat = 29.5,
            payloadLon = -81.37,
            payloadTrail = listOf(
                PayloadFix(1L, 29.1, -81.37),
                PayloadFix(2L, 29.4, -81.37),
            ),
        )
        val pinned = SitPathPlot.dotsFrom(
            listOf(decoded),
            listOf(axon),
            namedKeys = emptySet(),
            watchedFleetIds = setOf(axon.id),
            alertsOnly = true,
        )
        assertEquals(29.4, pinned.points.single().lat, 1e-6)
        assertTrue(pinned.points.single().advertised)
        val letter = SitPathPlot.dotsFrom(listOf(decoded), listOf(axon), namedKeys = emptySet())
        assertEquals(28.78, letter.points.single().lat, 1e-6)
        assertFalse(letter.points.single().advertised)
    }

    @Test
    fun bookmarkedRadioIsANumberedStop() {
        val axon = Fleet(
            id = "fleet-axon",
            name = "Axon",
            kind = SignatureClass.LAW_ENFORCEMENT,
            attentionNote = "Body-worn.",
        )
        val path = (0..20).map { i ->
            GpsSample(i * 60_000L, 28.7800 + i * 0.01, -81.3700)
        }
        val tagged = Sighting(
            key = "BLE:AA:AA:AA:AA:AA:02",
            kind = RadioKind.BLE,
            mac = "AA:AA:AA:AA:AA:02",
            name = "Cybertruck",
            rssi = -55,
            rssiMin = -70,
            rssiMax = -40,
            channel = 0,
            frequencyMhz = 2402,
            vendor = "Tesla, Inc.",
            randomized = false,
            hiddenSsid = false,
            serviceUuids = emptyList(),
            manufacturerId = null,
            manufacturerDataHex = "",
            rawHex = "",
            extras = "",
            firstSeen = 0L,
            lastSeen = 20 * 60_000L,
            hitCount = 80,
            fleetIds = emptySet(),
            rssiHistory = emptyList(),
            presence = emptyList(),
            gpsTrail = listOf(0, 5, 10, 15, 20).map { i ->
                GpsSample(i * 60_000L, 28.7800 + i * 0.01, -81.3700, -50)
            },
        )
        val dots = SitPathPlot.dotsFrom(
            listOf(tagged), listOf(axon), namedKeys = setOf(tagged.key),
            bookmarkedKeys = setOf(tagged.key),
        )
        assertEquals(1, dots.points.size)
        assertEquals("Cybertruck", dots.points[0].label)
        assertTrue(dots.points[0].named)
    }

    @Test
    fun extraAttentionLeaveAndReturnIsAStop() {
        val axon = Fleet(
            id = "fleet-axon",
            name = "Axon",
            kind = SignatureClass.LAW_ENFORCEMENT,
            attentionNote = "Body-worn.",
        )
        val path = (0..20).map { i ->
            GpsSample(i * 60_000L, 28.7800 + i * 0.01, -81.3700)
        }
        val tagged = Sighting(
            key = "WIFI:AA:AA:AA:AA:AA:03",
            kind = RadioKind.WIFI,
            mac = "AA:AA:AA:AA:AA:03",
            name = "home AP",
            rssi = -40,
            rssiMin = -50,
            rssiMax = -35,
            channel = 6,
            frequencyMhz = 2437,
            vendor = null,
            randomized = false,
            hiddenSsid = false,
            serviceUuids = emptyList(),
            manufacturerId = null,
            manufacturerDataHex = "",
            rawHex = "",
            extras = "",
            firstSeen = 0L,
            lastSeen = 20 * 60_000L,
            hitCount = 12,
            fleetIds = setOf("fleet-axon"),
            rssiHistory = emptyList(),
            presence = emptyList(),
            gpsTrail = listOf(
                GpsSample(0L, 28.7800, -81.3700, -40),
                GpsSample(20 * 60_000L, 28.7800, -81.3700, -42),
            ),
        )
        val dots = SitPathPlot.dotsFrom(listOf(tagged), listOf(axon), namedKeys = emptySet())
        assertEquals(1, dots.points.size)
        assertEquals("home AP", dots.points[0].label)
        assertTrue(dots.points[0].extraAttention)
    }

    @Test
    fun namedWithoutBookmarkIsNotPlotted() {
        val path = listOf(
            GpsSample(0L, 28.7800, -81.3700),
            GpsSample(60_000L, 28.7850, -81.3700),
        )
        val tagged = Sighting(
            key = "BLE:AA:AA:AA:AA:AA:05",
            kind = RadioKind.BLE,
            mac = "AA:AA:AA:AA:AA:05",
            name = "tsTPMS",
            rssi = -55,
            rssiMin = -60,
            rssiMax = -50,
            channel = 0,
            frequencyMhz = 2402,
            vendor = null,
            randomized = false,
            hiddenSsid = false,
            serviceUuids = emptyList(),
            manufacturerId = null,
            manufacturerDataHex = "",
            rawHex = "",
            extras = "",
            firstSeen = 0L,
            lastSeen = 60_000L,
            hitCount = 8,
            fleetIds = emptySet(),
            rssiHistory = emptyList(),
            presence = emptyList(),
            gpsTrail = listOf(GpsSample(0L, 28.7800, -81.3700, -55)),
        )
        val dots = SitPathPlot.dotsFrom(
            listOf(tagged),
            fleets = emptyList(),
            namedKeys = setOf(tagged.key),
            observerNotes = mapOf(tagged.key to "Saw this at the gate."),
        )
        assertTrue(dots.points.isEmpty())
    }

    @Test
    fun observerNotesPlotAsNumberedBlueStop() {
        val path = listOf(
            GpsSample(0L, 28.7800, -81.3700),
            GpsSample(60_000L, 28.7850, -81.3700),
        )
        val tagged = Sighting(
            key = "BLE:AA:AA:AA:AA:AA:04",
            kind = RadioKind.BLE,
            mac = "AA:AA:AA:AA:AA:04",
            name = "iBeacon",
            rssi = -55,
            rssiMin = -60,
            rssiMax = -50,
            channel = 0,
            frequencyMhz = 2402,
            vendor = null,
            randomized = false,
            hiddenSsid = false,
            serviceUuids = emptyList(),
            manufacturerId = null,
            manufacturerDataHex = "",
            rawHex = "",
            extras = "",
            firstSeen = 0L,
            lastSeen = 60_000L,
            hitCount = 8,
            fleetIds = emptySet(),
            rssiHistory = emptyList(),
            presence = emptyList(),
            gpsTrail = listOf(
                GpsSample(0L, 28.7800, -81.3700, -55),
                GpsSample(30_000L, 28.7825, -81.3700, -50),
                GpsSample(60_000L, 28.7850, -81.3700, -58),
            ),
        )
        val dots = SitPathPlot.dotsFrom(
            listOf(tagged),
            fleets = emptyList(),
            namedKeys = emptySet(),
            observerNotes = mapOf(tagged.key to "Mall door, same as last week."),
            bookmarkedKeys = setOf(tagged.key),
        )
        assertEquals(1, dots.points.size)
        assertTrue(dots.points[0].named)
        assertFalse(dots.points[0].extraAttention)
        assertEquals("Mall door, same as last week.", dots.points[0].observerNotes)
    }

    @Test
    fun despikeDropsOutAndBackJump() {
        val a = GpsSample(0L, 28.7800, -81.3700)
        val spike = GpsSample(2_000L, 28.7950, -81.3700)
        val c = GpsSample(4_000L, 28.7802, -81.3700)
        val cleaned = Geo.despikePath(listOf(a, spike, c))
        assertEquals(2, cleaned.size)
        assertEquals(a.at, cleaned[0].at)
        assertEquals(c.at, cleaned[1].at)
    }

    @Test
    fun despikeKeepsHighwayHop() {
        val a = GpsSample(0L, 28.7800, -81.3700)
        val b = GpsSample(10_000L, 28.7827, -81.3700)
        val cleaned = Geo.despikePath(listOf(a, b))
        assertEquals(2, cleaned.size)
    }

    @Test
    fun capSpreadKeepsFirstAndLast() {
        val samples = (0 until 100).map { i ->
            GpsSample(i * 1_000L, 28.78 + i * 0.001, -81.37)
        }
        val kept = Geo.capSpread(samples, 40)
        assertEquals(40, kept.size)
        assertEquals(samples.first().at, kept.first().at)
        assertEquals(samples.last().at, kept.last().at)
        assertTrue(kept.any { it.at in 20_000L..30_000L })
    }

    @Test
    fun gnssPinSitsOnTheSampleNearestInTime() {
        val path = listOf(
            GpsSample(1_000L, 28.7800, -81.3700),
            GpsSample(10_000L, 28.7810, -81.3700),
            GpsSample(20_000L, 28.7820, -81.3700),
        )
        val mark = GnssMark(
            at = 11_000L,
            endedAt = 16_000L,
            kind = "interference",
            confidence = "Medium",
            detail = "GPS L1: AGC -9.0 dB, signal -7.0 dB, 8 satellites (baseline 12)",
        )
        val pin = SitPathPlot.gnssPins(listOf(mark), path).single()
        assertEquals(28.7810, pin.lat, 0.00001)
        assertEquals(-81.3700, pin.lon, 0.00001)
        val line = pin.keyLine()
        assertTrue(line.contains("Possible GNSS interference"))
        assertTrue(line.contains("Medium"))
        assertTrue(line.contains("AGC -9.0 dB"))
        assertFalse(line.contains("28.781"))
        assertFalse(line.contains("latitude", ignoreCase = true))
        assertFalse(line.contains("jammer", ignoreCase = true))
        assertFalse(line.contains("spoofer", ignoreCase = true))
    }

    @Test
    fun gnssPinNeedsAPathAndNamesSpoofingWithoutAPlace() {
        val mark = GnssMark(at = 5_000L, kind = "spoofing", confidence = "High", detail = "  ")
        assertTrue(SitPathPlot.gnssPins(listOf(mark), emptyList()).isEmpty())
        val pin = SitPathPlot.gnssPins(
            listOf(mark),
            listOf(GpsSample(1_000L, 28.7800, -81.3700)),
        ).single()
        assertEquals("Possible GNSS spoofing", pin.title())
        assertTrue(pin.keyLine().contains("Signal changed."))
        assertFalse(pin.keyLine().contains("28.7800"))
    }

    @Test
    fun liveDotsUsesTheLouderHear() {
        val dots = SitPathPlot.liveDots(
            listOf(
                radioAt(
                    "loud",
                    trail = listOf(
                        GpsSample(1L, 28.780, -81.370, rssi = -45),
                        GpsSample(2L, 28.790, -81.370, rssi = -80),
                    ),
                ),
            ),
            emptyList(),
        )
        assertEquals(1, dots.size)
        assertEquals(28.780, dots[0].lat, 1e-9)
        assertEquals(-81.370, dots[0].lon, 1e-9)
        assertFalse(dots[0].advertised)
    }

    @Test
    fun liveDotsPrefersTheAdvertisedFix() {
        val dots = SitPathPlot.liveDots(
            listOf(
                radioAt(
                    "rid",
                    trail = listOf(GpsSample(1L, 28.780, -81.370, rssi = -40)),
                    payloadLat = 28.900,
                    payloadLon = -81.500,
                ),
            ),
            emptyList(),
        )
        assertEquals(28.900, dots.single().lat, 1e-6)
        assertEquals(-81.500, dots.single().lon, 1e-6)
        assertTrue(dots.single().advertised)
    }

    @Test
    fun liveDotsOmitsARadioWithNoFix() {
        val dots = SitPathPlot.liveDots(
            listOf(
                radioAt("none"),
                radioAt("here", latitude = 28.78, longitude = -81.37),
            ),
            emptyList(),
        )
        assertEquals(listOf("here"), dots.map { it.key })
        assertFalse(dots.single().advertised)
    }

    @Test
    fun zoomAboutHalvesTheWindowAndKeepsTheFocus() {
        val view = SitPathPlot.Viewport(28.7800, -81.3700, 400f, 800f)
        val focusLat = 28.7810
        val focusLon = -81.3690
        val zoomed = view.zoomAbout(2f, focusLat, focusLon)
        assertEquals(200f, zoomed.halfEastM, 0.01f)
        assertEquals(400f, zoomed.halfNorthM, 0.01f)
        assertEquals(eastFraction(view, focusLon), eastFraction(zoomed, focusLon), 0.001)
        assertEquals(northFraction(view, focusLat), northFraction(zoomed, focusLat), 0.001)
    }

    @Test
    fun coveredBoundsHoldTheCenterAndReachPastTheScreen() {
        val view = SitPathPlot.Viewport(28.78, -81.37, 400f, 800f)
        val box = view.coveredBounds()
        val south = box[0]
        val north = box[1]
        val west = box[2]
        val east = box[3]
        assertTrue(south < view.centerLat && view.centerLat < north)
        assertTrue(west < view.centerLon && view.centerLon < east)
        val northEdge = view.centerLat + view.halfNorthM / 110_540.0
        assertTrue(north > northEdge)
    }

    @Test
    fun panNorthMovesTheCenterNorth() {
        val view = SitPathPlot.Viewport(28.78, -81.37, 400f, 800f)
        val moved = view.panMeters(0f, 100f)
        assertTrue(moved.centerLat > view.centerLat)
        assertEquals(view.centerLon, moved.centerLon, 1e-12)
        assertEquals(view.halfEastM, moved.halfEastM, 0.01f)
    }

    @Test
    fun heldViewportKeepsTheCenter() {
        val model = SitPathPlot.Model(
            samples = listOf(
                GpsSample(1L, 28.7800, -81.3700),
                GpsSample(2L, 28.7850, -81.3700),
            ),
            dots = emptyList(),
            lengthM = 550.0,
            spanM = 550.0,
            title = "tall",
        )
        val fitted = SitPathPlot.layout(model, 400f, 300f)!!
        val held = SitPathPlot.viewportOf(fitted)
        val again = SitPathPlot.layout(model, 400f, 300f, viewport = held)!!
        val midX = (again.plotLeft + again.plotRight) / 2f
        val midY = (again.plotTop + again.plotBottom) / 2f
        val center = again.project(held.centerLat, held.centerLon)
        assertTrue(kotlin.math.abs(center.x - midX) < 2f)
        assertTrue(kotlin.math.abs(center.y - midY) < 2f)
        val before = fitted.project(28.7850, -81.3700)
        val after = again.project(28.7850, -81.3700)
        assertTrue(kotlin.math.abs(before.x - after.x) < 2f)
        assertTrue(kotlin.math.abs(before.y - after.y) < 2f)
    }

    @Test
    fun liveDotsIgnoresALouderHearFromBeforeTheWindow() {
        val dots = SitPathPlot.liveDots(
            listOf(
                radioAt(
                    "loud",
                    trail = listOf(
                        GpsSample(1L, 28.780, -81.370, rssi = -45),
                        GpsSample(2L, 28.790, -81.370, rssi = -80),
                    ),
                ).copy(lastSeen = 2L),
            ),
            emptyList(),
            since = 2L,
        )
        assertEquals(28.790, dots.single().lat, 1e-9)
        assertEquals(-81.370, dots.single().lon, 1e-9)
    }

    @Test
    fun liveDotsUsesTheCurrentHearWhenTheTrailPointIsOlder() {
        val dots = SitPathPlot.liveDots(
            listOf(
                radioAt(
                    "same",
                    trail = listOf(GpsSample(1L, 28.780, -81.370, rssi = -40)),
                    latitude = 28.781,
                    longitude = -81.371,
                ).copy(lastSeen = 50L),
            ),
            emptyList(),
            since = 40L,
        )
        assertEquals(28.781, dots.single().lat, 1e-9)
        assertEquals(-81.371, dots.single().lon, 1e-9)
    }

    @Test
    fun livePathSinceUsesTheSitOrTheLaterOfArmAndFifteenMinutes() {
        val now = 1_000_000L
        val fifteen = DebriefPrompt.WINDOW_MS
        assertEquals(now - 1_000L, SitPathPlot.livePathSince(now, now - 1_000L, null))
        assertEquals(now - fifteen, SitPathPlot.livePathSince(now, now - fifteen - 5_000L, null))
        assertEquals(50L, SitPathPlot.livePathSince(now, now - 1_000L, 50L))
    }

    @Test
    fun livePathSamplesDropPointsBeforeTheWindow() {
        val path = listOf(
            GpsSample(1L, 28.78, -81.37),
            GpsSample(20L, 28.79, -81.37),
        )
        assertEquals(listOf(20L), SitPathPlot.livePathSamples(path, 10L).map { it.at })
    }

    @Test
    fun livePathSamplesDrawFromWhereTheSwitchTurnedOn() {
        val corner = GpsSample(1_000L, 28.7800, -81.3700)
        val tip = GpsSample(20_000L, 28.7810, -81.3700)
        val mark = GpsSample(15_000L, 28.7804, -81.3700)
        val line = SitPathPlot.livePathSamples(listOf(corner, tip), since = 15_000L, mark = mark)
        assertEquals(listOf(15_000L, 20_000L), line.map { it.at })
    }

    @Test
    fun livePathSamplesStayADotUntilThePhoneMoves() {
        val tip = GpsSample(20_000L, 28.7800, -81.3700)
        val mark = GpsSample(15_000L, 28.7800, -81.3700)
        val line = SitPathPlot.livePathSamples(listOf(tip), since = 15_000L, mark = mark)
        assertEquals(listOf(20_000L), line.map { it.at })
    }

    @Test
    fun livePathSamplesKeepTheLastStretchOfAStraightRoad() {
        val anchor = GpsSample(0L, 28.7800, -81.3700)
        val tip = GpsSample(30_000L, 28.7900, -81.3700)
        val mark = GpsSample(1_000L, 28.7801, -81.3700)
        val line = SitPathPlot.livePathSamples(listOf(anchor, tip), since = 15_000L, mark = mark)
        assertEquals(listOf(15_000L, 30_000L), line.map { it.at })
        assertEquals(28.7850, line.first().lat, 1e-9)
        assertEquals(28.7900, line.last().lat, 1e-9)
    }

    @Test
    fun livePathSamplesKeepATurnInsideTheWindow() {
        val anchor = GpsSample(0L, 28.7800, -81.3700)
        val corner = GpsSample(20_000L, 28.7860, -81.3700)
        val tip = GpsSample(30_000L, 28.7860, -81.3680)
        val mark = GpsSample(1_000L, 28.7802, -81.3700)
        val line = SitPathPlot.livePathSamples(listOf(anchor, corner, tip), since = 10_000L, mark = mark)
        assertEquals(listOf(10_000L, 20_000L, 30_000L), line.map { it.at })
    }

    @Test
    fun livePathRadiosKeepAWatchlistAlertHeardInTheWindow() {
        val heard = radioAt("BLE:AA").copy(lastSeen = 100L, fleetIds = setOf("fleet-a"))
        val old = radioAt("BLE:BB").copy(lastSeen = 10L, fleetIds = setOf("fleet-a"))
        val plain = radioAt("BLE:CC").copy(lastSeen = 100L)
        val hidden = radioAt("BLE:DD").copy(lastSeen = 100L, fleetIds = setOf("fleet-a"))
        val mac = radioAt("BLE:EE").copy(lastSeen = 100L)
        val kept = SitPathPlot.livePathRadios(
            listOf(heard, old, plain, hidden, mac),
            since = 50L,
            alertKeys = setOf("BLE:EE"),
            watchedFleetIds = setOf("fleet-a"),
            hiddenKeys = setOf("BLE:DD"),
        )
        assertEquals(listOf("BLE:AA", "BLE:EE"), kept.map { it.key })
    }

    @Test
    fun dotsAloneStillLayOut() {
        val dot = SitPathPlot.Dot(
            key = "BLE:AA",
            lat = 28.78,
            lon = -81.37,
            label = "AA",
            extraAttention = false,
            named = false,
        )
        val layout = SitPathPlot.layout(
            SitPathPlot.Model(emptyList(), listOf(dot), 0.0, 0.0, ""),
            400f,
            300f,
        )
        assertNotNull(layout)
        assertEquals(1, layout!!.dots.size)
    }

    @Test
    fun livePathStaysOffOnRadar() {
        val stock = AppSettings()
        assertFalse(stock.showLivePath)
        assertFalse(stock.copy(showLivePath = true, viewMode = ViewMode.RADAR).livePathOnScreen())
        assertTrue(stock.copy(showLivePath = true, viewMode = ViewMode.BY_CLASS).livePathOnScreen())
        assertFalse(stock.copy(showLivePath = false, viewMode = ViewMode.LIST).livePathOnScreen())
    }

    @Test
    fun displayControlsFollowThePicture() {
        assertFalse(ViewMode.RADAR.usesListFields())
        assertFalse(ViewMode.RADAR.usesRssiBars())
        assertTrue(ViewMode.TIMELINE.usesListFields())
        assertFalse(ViewMode.TIMELINE.usesRssiBars())
        listOf(ViewMode.LIST, ViewMode.HYBRID, ViewMode.BY_CLASS).forEach { mode ->
            assertTrue(mode.usesListFields())
            assertTrue(mode.usesRssiBars())
        }
    }

    private fun eastFraction(view: SitPathPlot.Viewport, lon: Double): Double {
        val cos0 = kotlin.math.cos(Math.toRadians(view.centerLat)).coerceAtLeast(0.2)
        val east = (lon - view.centerLon) * 111_320.0 * cos0
        return east / view.halfEastM
    }

    private fun northFraction(view: SitPathPlot.Viewport, lat: Double): Double {
        val north = (lat - view.centerLat) * 110_540.0
        return north / view.halfNorthM
    }

    private fun radioAt(
        key: String,
        trail: List<GpsSample> = emptyList(),
        latitude: Double? = null,
        longitude: Double? = null,
        payloadLat: Double? = null,
        payloadLon: Double? = null,
    ) = Sighting(
        key = key,
        kind = RadioKind.BLE,
        mac = "AA:BB:CC:DD:EE:10",
        name = key,
        rssi = -40,
        rssiMin = -40,
        rssiMax = -40,
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
        firstSeen = 1L,
        lastSeen = 1L,
        hitCount = 1,
        fleetIds = emptySet(),
        rssiHistory = emptyList(),
        presence = emptyList(),
        latitude = latitude,
        longitude = longitude,
        gpsTrail = trail,
        payloadLat = payloadLat,
        payloadLon = payloadLon,
    )
}
