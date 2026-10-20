package app.fieldwatch.domain

import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * ATAK mission package for one sit. Hear-points, advertised aircraft and operator
 * pins, the phone path, and a GNSS pin on the nearest path sample. No ellipse and
 * no flood marker. Stale is measured from export time so a later import still shows.
 */
object SitTakPackage {
    /** Ten years after the package is written. */
    const val STALE_MS = 10L * 365 * 24 * 60 * 60 * 1000

    private const val PATH_UID = "FIELDWATCH-PATH"
    private const val SELF_REMARKS = "This phone. End of the recorded path."
    private const val CYAN_STROKE = -16711681
    private const val YELLOW_STROKE = -256

    data class Item(
        val uid: String,
        val name: String,
        val xml: String,
    )

    data class Packed(
        val items: List<Item>,
        val tracksKml: String?,
    )

    fun pack(
        devices: List<Sighting>,
        radios: LogExportRadios,
        path: List<GpsSample>,
        gnss: List<GnssMark>,
        fleets: List<Fleet>,
        watchlist: List<WatchTarget>,
        now: Long,
        tag: String,
        callsign: String,
    ): Packed? {
        val phoneCallsign = TakInstall.callsign(callsign, tag)
        val rows = SitExport.rows(devices, radios)
        val staleAt = now + STALE_MS
        val items = ArrayList<Item>()
        val seen = HashSet<String>()
        fun add(uid: String, name: String, xml: String) {
            if (!seen.add(uid)) return
            items += Item(uid, name.take(80).ifBlank { uid }, xml)
        }

        val pathPoints = path.mapNotNull { sample ->
            if (!PayloadLocation.validCoord(sample.lat, sample.lon)) null
            else sample.lat to sample.lon
        }
        if (path.isNotEmpty()) {
            val end = path.last()
            if (PayloadLocation.validCoord(end.lat, end.lon)) {
                add(
                    CotEvent.selfUid(tag),
                    phoneCallsign,
                    CotEvent.render(
                        uid = CotEvent.selfUid(tag),
                        face = TakFace(
                            callsign = phoneCallsign,
                            remarks = SELF_REMARKS,
                            cotType = "a-f-G-U-C",
                            group = "Cyan",
                        ),
                        lat = end.lat,
                        lon = end.lon,
                        now = now,
                        staleMs = 0L,
                        eventTimeMs = end.at,
                        staleAtMs = staleAt,
                    ),
                )
            }
        }
        if (pathPoints.size >= 2) {
            add(
                PATH_UID,
                "Operator path",
                CotEvent.routeXml(
                    uid = PATH_UID,
                    callsign = "Operator path",
                    points = pathPoints,
                    group = "Cyan",
                    stroke = CYAN_STROKE,
                    now = now,
                    eventTimeMs = path.last().at,
                    staleAtMs = staleAt,
                ),
            )
        }

        val tracks = ArrayList<Pair<String, List<Pair<Double, Double>>>>()
        if (pathPoints.size >= 2) tracks += "Operator path" to pathPoints

        rows.forEach { device ->
            val heard = SitExport.hearPoint(device)
            if (heard != null && PayloadLocation.validCoord(heard.lat, heard.lon)) {
                val hereUid = CotEvent.hereUid(device, tag)
                val raw = CotEvent.face(
                    device,
                    fleets,
                    watchlist,
                    advertised = false,
                    tag = tag,
                    viaCallsign = TakInstall.viaLine(hereUid, tag, phoneCallsign),
                )
                val callsign = if (raw.callsign.endsWith(" (here)")) {
                    raw.callsign
                } else {
                    (raw.callsign.take((32 - " (here)".length).coerceAtLeast(1)) + " (here)").take(32)
                }
                val remarks = if (raw.remarks.startsWith(raw.callsign)) {
                    callsign + raw.remarks.removePrefix(raw.callsign)
                } else {
                    raw.remarks
                }
                val face = raw.copy(callsign = callsign, remarks = remarks, hae = 9999999.0)
                add(
                    hereUid,
                    face.callsign,
                    CotEvent.render(
                        uid = hereUid,
                        face = face,
                        lat = heard.lat,
                        lon = heard.lon,
                        now = now,
                        staleMs = 0L,
                        eventTimeMs = heard.at,
                        staleAtMs = staleAt,
                    ),
                )
            }
            if (TakPublish.advertisedPin(device)) {
                val airUid = CotEvent.uid(device, tag)
                val face = CotEvent.face(
                    device,
                    fleets,
                    watchlist,
                    advertised = true,
                    tag = tag,
                    viaCallsign = TakInstall.viaLine(airUid, tag, phoneCallsign),
                )
                val lat = device.payloadLat!!
                val lon = device.payloadLon!!
                val whenAt = device.payloadTrail.lastOrNull()?.at ?: device.lastSeen
                add(
                    airUid,
                    face.callsign,
                    CotEvent.render(
                        uid = airUid,
                        face = face,
                        lat = lat,
                        lon = lon,
                        now = now,
                        staleMs = 0L,
                        eventTimeMs = whenAt,
                        staleAtMs = staleAt,
                    ),
                )
                val trail = trailPoints(device)
                if (trail.size >= 2) {
                    tracks += face.callsign to trail
                    val trackUid = "FIELDWATCH-TRACK-${airUid.removePrefix("FIELDWATCH-")}"
                    add(
                        trackUid,
                        "${face.callsign} track".take(32),
                        CotEvent.routeXml(
                            uid = trackUid,
                            callsign = "${face.callsign} track".take(32),
                            points = trail,
                            group = face.group,
                            stroke = YELLOW_STROKE,
                            now = now,
                            eventTimeMs = whenAt,
                            staleAtMs = staleAt,
                        ),
                    )
                }
                val pilot = TakPublish.pilotPin(device)
                if (pilot != null) {
                    val pilotUid = CotEvent.pilotUid(device, tag)
                    val pilotFace = CotEvent.face(
                        device,
                        fleets,
                        watchlist,
                        advertised = true,
                        tag = tag,
                        pilot = true,
                        viaCallsign = TakInstall.viaLine(pilotUid, tag, phoneCallsign),
                    )
                    add(
                        pilotUid,
                        pilotFace.callsign,
                        CotEvent.render(
                            uid = pilotUid,
                            face = pilotFace,
                            lat = pilot.first,
                            lon = pilot.second,
                            now = now,
                            staleMs = 0L,
                            eventTimeMs = device.lastSeen,
                            staleAtMs = staleAt,
                        ),
                    )
                }
            }
        }

        if (path.isNotEmpty()) {
            SitPathPlot.gnssPins(gnss, path).forEach { pin ->
                val mark = gnss.firstOrNull { it.at == pin.at && it.kind == pin.kind }
                val remarks = buildString {
                    if (mark != null) append(mark.reportLine()) else append(pin.title())
                    append('\n')
                    append("Where this phone was. Not the source.")
                }.take(TakDefaults.REMARKS_MAX)
                val uid = TakInstall.gnssUid(tag, "${pin.at}-${pin.kind}")
                add(
                    uid,
                    TakGnss.CALLOUT,
                    CotEvent.gnssPinXml(
                        uid = uid,
                        lat = pin.lat,
                        lon = pin.lon,
                        now = now,
                        staleMs = 0L,
                        remarks = remarks,
                        eventTimeMs = pin.at,
                        staleAtMs = staleAt,
                    ),
                )
            }
        }

        val kml = tracksKml(tracks)
        if (items.isEmpty() && kml == null) return null
        return Packed(items, kml)
    }

    fun zip(packed: Packed, packageUid: String, packageName: String, now: Long): ByteArray {
        val folder = packageUid.trim().ifBlank { "fieldwatch" }
        val entries = ArrayList<Pair<String, ByteArray>>()
        packed.tracksKml?.let { kml ->
            entries += "$folder/tracks.kml" to kml.toByteArray(Charsets.UTF_8)
        }
        packed.items.forEach { item ->
            val file = item.uid.filter { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }
                .ifBlank { "marker" }
            entries += "$folder/$file.cot" to item.xml.toByteArray(Charsets.UTF_8)
        }
        val manifest = manifest(packageUid, packageName, entries.map { it.first to it.second })
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            put(zip, "MANIFEST/manifest.xml", manifest.toByteArray(Charsets.UTF_8), now)
            entries.forEach { (name, bytes) -> put(zip, name, bytes, now) }
        }
        return out.toByteArray()
    }

    private fun trailPoints(device: Sighting): List<Pair<Double, Double>> =
        device.payloadTrail.mapNotNull { fix ->
            if (!PayloadLocation.validCoord(fix.lat, fix.lon)) null else fix.lat to fix.lon
        }

    private fun tracksKml(tracks: List<Pair<String, List<Pair<Double, Double>>>>): String? {
        if (tracks.isEmpty()) return null
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
            append("""<kml xmlns="http://www.opengis.net/kml/2.2"><Document>""").append('\n')
            append("  <name>Fieldwatch sit tracks</name>\n")
            tracks.forEach { (name, points) ->
                append("  <Placemark>\n")
                append("    <name>${CotEvent.xmlEscape(name)}</name>\n")
                append("    <LineString><coordinates>")
                append(points.joinToString(" ") { (lat, lon) -> "${coord(lon)},${coord(lat)}" })
                append("</coordinates></LineString>\n")
                append("  </Placemark>\n")
            }
            append("</Document></kml>\n")
        }
    }

    private fun manifest(packageUid: String, packageName: String, entries: List<Pair<String, ByteArray>>): String =
        buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
            append("""<MissionPackageManifest version="2">""").append('\n')
            append("  <Configuration>\n")
            append("    <Parameter name=\"uid\" value=\"${CotEvent.xmlEscape(packageUid)}\"/>\n")
            append("    <Parameter name=\"name\" value=\"${CotEvent.xmlEscape(packageName)}\"/>\n")
            append("  </Configuration>\n")
            append("  <Contents>\n")
            entries.forEach { (path, _) ->
                val name = path.substringAfterLast('/')
                append("    <Content ignore=\"false\" zipEntry=\"${CotEvent.xmlEscape(path)}\">\n")
                append("      <Parameter name=\"uid\" value=\"${CotEvent.xmlEscape(name)}\"/>\n")
                append("      <Parameter name=\"name\" value=\"${CotEvent.xmlEscape(name)}\"/>\n")
                append("    </Content>\n")
            }
            append("  </Contents>\n")
            append("</MissionPackageManifest>\n")
        }

    private fun put(zip: ZipOutputStream, name: String, bytes: ByteArray, now: Long) {
        val entry = ZipEntry(name)
        entry.time = now
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun coord(v: Double): String = String.format(Locale.US, "%.6f", v)
}
