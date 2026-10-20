# Trusted networks

Requirements for a later implementation. This document does not authorize that work by itself. Do not start it until asked. When asked, implement this document and stop there. Do not ship, tag, sideload, or open a GitHub Release unless that request says so.

Fieldwatch watches Wi-Fi networks the user has marked trusted and alerts when a sighting looks like someone else using that name. Nothing else is flagged. The phone stays receive-only. This work uses scan results the app already collects. It does not connect, probe, or transmit on its own.

## Out of scope

- No change to GNSS detection, the GNSS dialog, the red line, or the TAK line on the Fieldwatch marker. A GNSS pin is a separate decision and is not part of this work.
- Floods stay a note on the Fieldwatch marker. They do not get a pin.
- No catalog change. Trusted networks are not signatures.
- No user-manual rewrite unless a later request asks for one.

## Outcome

On a Wi-Fi detail page the user can trust that network's name. Fieldwatch remembers the access points it was shown, the security those access points used, and the places the phone was. Later, a sighting of that name that is weaker, from an unknown maker, or far from those places can alert. When TAK/CoT is on, the alert also drops a pin where the phone was when that suspect was loudest.

Untrusted names never alert. A trusted name that still matches what was learned stays quiet.

## Marking trust

The control is **Trust this network**. It sits on Wi-Fi device detail, next to Mine. It is absent on Bluetooth. Mine still means one radio stays listed and does not beep. Trust means the network name is watched. The two switches do not change each other.

Trust is the SSID string Fieldwatch already stores (`ssidClean()` in `WifiRadio.kt`). Match is exact and case-sensitive. Fieldwatch does not have the raw 802.11 bytes. A blank name, a hidden network, and the placeholder `<unknown ssid>` cannot be trusted. The switch stays off and explains that a hidden name cannot be watched.

Turning trust on does not silently keep every access point in view. The phone lists the access points that share that exact name right now, with vendor and band, and the user unchecks any that are not theirs. Only the checked ones are learned. If several vendors are already on that name, the list says so. The user can still trust it.

A helper, **Also trust these?**, lists other names visible now that share a learned access point's OUI and the same security. Each is unchecked. Similar-looking names are never grouped in, and nothing in that list is trusted until the user ticks it.

A long-press on the Live list is not required.

## What is stored

One record per trusted SSID, in its own store. Not in the signature catalog and not in the live device list.

For each learned access point:

- BSSID
- OUI and vendor, when the address is a real factory OUI
- Band and channel
- Security profile
- First seen, last seen
- Loudest RSSI, and the phone's position at that loudest hear, when a good fix exists

Security is a set of profiles, not a single "strongest." WPA2 on one band and WPA3 on another are both normal. Below Android 11 the profile comes from the capabilities string. From Android 11 up, reuse `WifiIeParser` (RSN and WPA). From Android 13 up, `ScanResult.getSecurityTypes()` may refine that. Rank used only for downgrade checks: open, then WEP, then WPA, then WPA2, then WPA3. A mixed profile stores each level that was actually seen. OWE is not treated as open.

Places are a small set of clusters, at most eight. A new fix within 300 m of a cluster updates that cluster. The screen never shows coordinates. A cluster is "a usual place," and the user can remove one. Clusters are not written into the export file.

A locally administered address (the local bit) has no vendor. It is stored as the BSSID only.

## Learning

**Learn new access points** defaults on. The learning window defaults to 3 days from the moment that name was trusted, and it can be changed. While the window is open, and learning is on:

- A new BSSID with the same name, a security profile already in the set, and a good fix near a usual place is added silently.
- A sibling is the easy case: same OUI, same security, only the last bytes differ, near a usual place. A locally administered address is not a sibling.
- A new BSSID with the same OUI and the same security, but not a sibling, is information only until it has been heard on 2 fresh scans near a usual place. Then it is learned. It does not alert.

A known BSSID heard far from every usual place, with security already in the set, adds a place and does not alert. That is the router traveling with the user. After the window, a far sighting alerts instead, and the alert can offer **Remember this place**. Remembering adds the cluster and ends that away-from-home episode.

Pausing learning keeps the record and stops new access points and new places. Untrust deletes the record for that name.

A fresh scan is one `fresh` result from `WifiRadio`, not a second read of the cached list.

## Detection

The master switch defaults off. While it is off, nothing alerts and no pin is sent. Records stay. Each rule below has its own switch. All three default on. Confidence uses the existing Low / Medium / High labels.

A hit needs 2 fresh scans of that suspect BSSID before it alerts. One episode per suspect BSSID. Further scans of the same suspect update the episode. They do not open a new alert. A rise in confidence may notify again. The episode ends when that BSSID has been absent for 10 minutes, or the user dismisses it.

**Security downgrade** (Medium, or High when the sighting is open or WEP and the baseline has never been either). The name is seen with a profile weaker than every profile in the set. Open and WEP always qualify when the set has never included them. This fires for any BSSID on that name, learned or not. A WPA2 sighting is not a downgrade when WPA2 is already in the set, including when WPA3 was also seen.

**Unknown vendor** (Medium). The name is seen from a BSSID whose factory OUI is not among the learned access points. A locally administered address does not fire this rule. A sibling that is still inside the learning rule does not fire it either.

**Away from home** (Medium). The name is seen more than the distance from every usual place. The default distance is 1 km. The fix must be good: accuracy tighter than 100 m, and not a paused receiver. A coarse fix, a missing fix, or a paused receiver does not alert and does not add a place. During the learning window, a known BSSID with matching security takes the "add a place" path above instead of this alert.

**Stronger signal.** When this hear is at a usual place and at least 15 dB louder than the loudest RSSI stored for that place, confidence goes up one notch. This is not its own alert. No stored RSSI means no notch.

**OUI 00:13:37.** Information only. The IEEE registry lists that OUI as Orient Power Home Network. It does not raise confidence and it does not alert by itself. The screen does not name any tool.

Known limit, stated in the help on that screen: an access point that copies a learned BSSID and a learned security profile is not caught, except when it is far from every usual place after the learning window. Catching a sighting can take a few minutes because Wi-Fi scans are throttled (about four scans in two minutes unless the phone's faster scan is on, then about every 8 seconds).

## Alerts

Wording names what was seen. It does not say an attack was detected, and it does not say a jammer or a spoofer was found.

> Possible evil twin of "{ssid}": {reason}

Reasons stay short: "open security", "weaker security", "unknown device", "away from usual places". Two reasons can be joined with a comma. The OUI note, when present, is a second line and not part of the reason.

The alert follows the GNSS pattern: popup, optional beep, optional voice, optional system notification, and a minimum confidence. Defaults: beep on, voice on, notification off, minimum Medium. These switches belong to this feature. They do not change the watchlist or GNSS switches. Voice speaks the same sentence, and it repeats only when confidence rises.

On screen, MAC addresses follow the masking already used everywhere else. The alert does not show coordinates.

## TAK pin

When TAK/CoT is configured, the feed is running, and this feature's TAK switch is on, an episode sends one point for the suspect BSSID.

The pin is where the phone was, not a surveyed position of the access point. It uses the heard-here rule in `TakPublish.heardHereAction`: the pin moves only when a later hear of that same suspect is louder. A weaker hear refreshes the same latitude and longitude on the normal interval so ATAK does not stale-drop it. The callsign does not ride along on the Fieldwatch self marker.

One pin per suspect BSSID. A different suspect BSSID is a different pin. The uid is stable for that BSSID for as long as the name stays trusted.

Callsign, trimmed like the other callsigns:

> Possible evil twin: {ssid}

Remarks: the SSID, the suspect BSSID and vendor, the reason, the confidence, and the time. No claim that the pin is the access point's own coordinates.

Color and CoT type must not be the Fieldwatch self marker (Cyan, uid `FIELDWATCH-SELF`) and must not reuse a heard-radio or Remote ID type. Pick one pair and keep it.

Stale time is how long the pin remains after the last hear. Choices are 1 hour, 6 hours, 24 hours, and until deleted. The default is 1 hour. Until deleted means the pin stays until the user untrusts the name, removes that access point, or turns the TAK switch off. Those actions send the tombstone the feed already uses for a dropped marker.

Privacy mode already pauses the feed. This feature sends nothing while the feed is paused.

## Settings

Settings gets one row, **Trusted networks**, opening its own screen. That screen holds:

- Master on/off.
- The three rule switches: security downgrade, unknown vendor, away from home.
- Away distance, default 1 km.
- Learn new access points, and the learning-window length.
- Beep, voice, system notification, and minimum confidence.
- TAK pin on/off, and stale time.
- The trusted list. One row per SSID: learned access-point count, bands, last seen. Tap a row for the access points (BSSID, vendor, band, security), remove one access point, pause learning, or rename the row. Untrust by swipe or tap. Rename changes the label the user sees. The match key stays the SSID.
- Export and import of the trusted list as JSON. The file includes names, learned access points, and security. It does not include places or coordinates. The export screen says that places stay on this phone. Import of a broken file does not wipe the list already on the phone.

New `AppSettings` fields have defaults so an old settings file still loads. The learned list itself is not stuffed into `AppSettings`. It is its own file, with `ignoreUnknownKeys` and defaults, same as the other stores.

## What the phone must not do

No association, no `WifiManager` connect, no local-only hotspot, and no extra probe. The scan Fieldwatch already runs stays as it is. Do not describe that scan as passive.

Do not add a transmit path to "test" a network.

## Tests

Unit tests on synthetic scan snapshots, using `WifiIeParser.parseIes` where the frame matters:

- Open or WEP against a WPA2/WPA3 set alerts. WPA2 against a set that already contains WPA2 does not, even if WPA3 is also in the set.
- Unknown factory OUI alerts. A locally administered address does not. OUI `00:13:37` does not alert by itself.
- Far from every place, after the window, alerts. A fix coarser than 100 m does not. A known BSSID during the window adds a place and does not alert.
- A sibling near a usual place is learned and does not alert.
- One fresh scan does not alert. The second does. A repeat read of the same cached result does not count.
- One pin uid per suspect BSSID. A louder hear moves it. A weaker hear keeps the latitude and longitude. A second BSSID is a second uid.
- An untrusted name produces no alert and no pin.
- An old settings JSON without the new fields still decodes.

Field check, when a phone test is asked for: mark the home mesh, after unchecking any neighbor on the same name, and confirm it stays quiet. Mixed WPA2 and WPA3 at home must stay quiet. Then, from a second phone, start a hotspot with the same name and open security. The watch phone cannot see its own hotspot. That must raise a downgrade alert and a TAK pin at the louder spot. Repeat with a WPA2 hotspot whose OUI is not the home router's, and confirm the unknown-vendor alert. Confirm an untrusted name stays quiet.
