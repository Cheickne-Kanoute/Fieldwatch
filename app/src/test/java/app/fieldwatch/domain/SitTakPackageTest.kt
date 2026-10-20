package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.zip.ZipInputStream

class SitTakPackageTest {
    private val now = 1_800_000_000_000L
    private val heardAt = 1_700_000_000_000L
    private val droneFleet = Fleet(
        id = "fleet-dji",
        name = "DJI",
        kind = SignatureClass.DRONE,
    )
    private val phone = GpsSample(heardAt, 28.1, -81.1, -40)
    private val path = listOf(phone, GpsSample(heardAt + 60_000L, 28.11, -81.11, -40))

    @Test
    fun packageHasHearAircraftOperatorPathAndGnssPin() {
        val drone = drone()
        val packed = SitTakPackage.pack(
            listOf(drone),
            LogExportRadios.BOTH,
            path,
            listOf(GnssMark(heardAt + 10_000L, heardAt + 60_000L, "interference", "Medium", "Gain dropped.")),
            listOf(droneFleet),
            emptyList(),
            now,
            tag = "AB23",
            callsign = "",
        )!!
        val files = unzip(SitTakPackage.zip(packed, "pkg-1", "Drive", now))
        val manifest = files.getValue("MANIFEST/manifest.xml")
        assertTrue(manifest.contains("version=\"2\""))
        assertTrue(manifest.contains("Drive"))
        files.keys.filter { it != "MANIFEST/manifest.xml" }.forEach { name ->
            assertTrue(manifest, manifest.contains(name))
        }
        val cot = files.filterKeys { it.endsWith(".cot") }.values.joinToString("\n")
        val kml = files.getValue("pkg-1/tracks.kml")
        assertTrue(cot.contains("FIELDWATCH-AB23-HERE-"))
        assertTrue(cot.contains("FIELDWATCH-SELF-AB23"))
        assertTrue(cot.contains("callsign=\"Fieldwatch-AB23\""))
        assertTrue(cot.contains("via Fieldwatch-AB23"))
        assertTrue(cot.contains("TESTUAS1 (here)"))
        assertTrue(cot.contains("heard here (operator GPS)"))
        assertTrue(cot.contains("lat=\"28.1\""))
        assertTrue(cot.contains("FIELDWATCH-RID-TESTUAS1"))
        assertTrue(cot.contains("type=\"a-u-A-M-H-Q\""))
        assertTrue(cot.contains("name=\"Yellow\""))
        assertTrue(cot.contains("lat=\"28.2\""))
        assertTrue(cot.contains("Takeoff"))
        assertTrue(cot.contains("name=\"Orange\""))
        assertTrue(cot.contains("lat=\"28.4\""))
        assertTrue(cot.contains("type=\"b-m-r\""))
        assertTrue(cot.contains("point=\"28.1,-81.1\""))
        assertTrue(cot.contains("point=\"28.11,-81.11\""))
        assertTrue(cot.contains("FIELDWATCH-GNSS-AB23-"))
        assertTrue(cot.contains("type=\"b-m-p-s-m\""))
        assertTrue(cot.contains("name=\"Magenta\""))
        assertTrue(cot.contains("Possible GNSS interference"))
        assertTrue(cot.contains("Where this phone was. Not the source."))
        assertTrue(cot.contains("This phone. End of the recorded path."))
        assertFalse(cot.contains("u-d-c-e"))
        assertFalse(cot.contains("<ellipse"))
        assertFalse(cot.contains("Flood"))
        assertTrue(kml.contains("Operator path"))
        assertTrue(kml.contains("-81.100000,28.100000"))
        assertTrue(kml.contains("TESTUAS1"))
        val here = cot.substring(cot.indexOf("FIELDWATCH-AB23-HERE-"))
        val rid = cot.substring(cot.indexOf("FIELDWATCH-RID-TESTUAS1"))
        assertFalse(rid.substringBefore("FIELDWATCH-AB23-HERE-").contains("via "))
        val time = Regex("""time="([^"]+)"""").find(cot)!!.groupValues[1]
        val stale = Regex("""stale="([^"]+)"""").find(here)!!.groupValues[1]
        assertTrue(Instant.parse(stale).isAfter(Instant.parse(time).plusSeconds(3600L * 24 * 3000)))
        assertTrue(cot.contains("time=\"2023-11-14T22:13:20.000Z\""))
    }

    @Test
    fun wifiOnlyKeepsThePathAndDropsTheAircraft() {
        val packed = SitTakPackage.pack(
            listOf(drone()),
            LogExportRadios.WIFI,
            path,
            emptyList(),
            listOf(droneFleet),
            emptyList(),
            now,
            tag = "AB23",
            callsign = "",
        )!!
        val cot = unzip(SitTakPackage.zip(packed, "pkg-2", "Drive", now))
            .filterKeys { it.endsWith(".cot") }
            .values
            .joinToString("\n")
        assertTrue(cot.contains("FIELDWATCH-PATH"))
        assertFalse(cot.contains("FIELDWATCH-RID-"))
        assertFalse(cot.contains("FIELDWATCH-HERE-"))
        assertFalse(cot.contains("Takeoff"))
    }

    @Test
    fun nothingToMapReturnsNull() {
        val ble = drone().copy(
            payloadLat = null,
            payloadLon = null,
            payloadOpLat = null,
            payloadOpLon = null,
            payloadTrail = emptyList(),
            gpsTrail = emptyList(),
        )
        assertNull(
            SitTakPackage.pack(
                listOf(ble),
                LogExportRadios.BOTH,
                emptyList(),
                listOf(GnssMark(heardAt, 0L, "interference", "Low", "Gain dropped.")),
                listOf(droneFleet),
                emptyList(),
                now,
                tag = "AB23",
                callsign = "",
            ),
        )
    }

    private fun drone(): Sighting = Sighting(
        key = "BLE:11:22:33:44:55:66",
        kind = RadioKind.BLE,
        mac = "11:22:33:44:55:66",
        name = "DJI-MINI",
        rssi = -60,
        rssiMin = -70,
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
        firstSeen = heardAt,
        lastSeen = heardAt + 60_000L,
        hitCount = 4,
        fleetIds = setOf(droneFleet.id),
        rssiHistory = emptyList(),
        presence = emptyList(),
        gpsTrail = listOf(phone),
        payloadLat = 28.2,
        payloadLon = -81.2,
        payloadUasId = "TESTUAS1",
        payloadOpLat = 28.4,
        payloadOpLon = -81.4,
        payloadOpKind = OperatorLoc.TAKEOFF,
        payloadTrail = listOf(
            PayloadFix(heardAt, 28.2, -81.2),
            PayloadFix(heardAt + 30_000L, 28.3, -81.3),
        ),
    )

    private fun unzip(bytes: ByteArray): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                out[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                entry = zip.nextEntry
            }
        }
        return out
    }
}
