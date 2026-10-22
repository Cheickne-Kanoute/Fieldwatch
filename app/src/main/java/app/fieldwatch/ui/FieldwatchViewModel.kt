package app.fieldwatch.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.fieldwatch.BuildConfig
import app.fieldwatch.FieldwatchApp
import app.fieldwatch.domain.AircraftTrail
import app.fieldwatch.domain.AppSettings
import app.fieldwatch.domain.attentionNotes
import app.fieldwatch.domain.signatureNotes
import app.fieldwatch.domain.detectionPolicy
import app.fieldwatch.domain.gnssSettings
import app.fieldwatch.data.CatalogRemote
import app.fieldwatch.data.DebriefPdf
import app.fieldwatch.data.LanguageFiles
import app.fieldwatch.data.LanguageRead
import app.fieldwatch.data.PathTiles
import app.fieldwatch.data.PlaceLookup
import app.fieldwatch.domain.DeviceDetailPrompt
import app.fieldwatch.domain.DeviceDetailText
import app.fieldwatch.domain.DebriefDoc
import app.fieldwatch.domain.DebriefPlaces
import app.fieldwatch.domain.DebriefPrompt
import app.fieldwatch.domain.DebriefReport
import app.fieldwatch.domain.DefaultCatalog
import app.fieldwatch.domain.DISCLAIMER_REV
import app.fieldwatch.domain.disclaimerOk
import app.fieldwatch.domain.FilterEngine
import app.fieldwatch.domain.Geo
import app.fieldwatch.domain.GeoExport
import app.fieldwatch.domain.GpsSample
import app.fieldwatch.domain.livePathOnScreen
import app.fieldwatch.domain.LogExportKind
import app.fieldwatch.domain.LogExportRadios
import app.fieldwatch.domain.ScanRadioFacts
import app.fieldwatch.domain.ScanStatus
import app.fieldwatch.domain.GnssMark
import app.fieldwatch.domain.SitExport
import app.fieldwatch.domain.SitTakPackage
import app.fieldwatch.domain.ClassOutline
import app.fieldwatch.domain.CoTravel
import app.fieldwatch.domain.FilterPreset
import app.fieldwatch.domain.FilterState
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.Hunt
import app.fieldwatch.domain.HuntCue
import app.fieldwatch.domain.FamilyVerdict
import app.fieldwatch.domain.LogRadio
import app.fieldwatch.domain.RadioBookmarks
import app.fieldwatch.domain.RadioKind
import app.fieldwatch.domain.RssiSample
import app.fieldwatch.domain.Sighting
import app.fieldwatch.domain.SignatureCandidate
import app.fieldwatch.domain.SignatureCandidates
import app.fieldwatch.domain.SignatureFamilyHint
import app.fieldwatch.domain.CandidateReport

import app.fieldwatch.domain.SignatureClass
import app.fieldwatch.domain.SignatureEngine
import app.fieldwatch.domain.SignatureListSort
import app.fieldwatch.domain.SettingsExchange
import app.fieldwatch.domain.SettingsPack
import app.fieldwatch.domain.SignatureExchange
import app.fieldwatch.domain.TakFeedStatus
import app.fieldwatch.domain.TakInstall
import app.fieldwatch.domain.LanguageErrors
import app.fieldwatch.domain.LanguageExportCopy
import app.fieldwatch.domain.LanguagePack
import app.fieldwatch.domain.LanguageParse
import app.fieldwatch.domain.LanguageText
import app.fieldwatch.domain.ListLine
import app.fieldwatch.domain.MacUtil
import app.fieldwatch.domain.PairingFlood
import app.fieldwatch.domain.PlaceMemory
import app.fieldwatch.domain.ListSort
import app.fieldwatch.domain.StrengthSort
import app.fieldwatch.domain.ViewMode
import app.fieldwatch.domain.WatchTarget
import app.fieldwatch.domain.DebriefWindow
import app.fieldwatch.domain.Sit
import app.fieldwatch.domain.SitDiff
import app.fieldwatch.domain.SitDiffPrompt
import app.fieldwatch.domain.SitPathPlot
import app.fieldwatch.domain.SitUi
import app.fieldwatch.radio.RadioPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import java.io.File
import java.util.UUID

data class CandidatesUi(
    val loading: Boolean = false,
    val progress: Float = 0f,
    val message: String = "",
    val report: CandidateReport? = null,
    val error: String? = null,
)

private data class FamilyLogSnap(
    val radios: List<LogRadio> = emptyList(),
    val loaded: Boolean = false,
)

data class PendingSettingsImport(
    val pack: SettingsPack,
    val message: String,
)

data class LanguageUiState(
    val snap: app.fieldwatch.data.LanguageSnapshot = app.fieldwatch.data.LanguageSnapshot(),
    val pending: app.fieldwatch.domain.LanguagePreview? = null,
    val errorKey: String? = null,
)

data class ExportUi(
    val active: Boolean = false,
    val progress: Float = 0f,
    /** True: spinning wait (Debrief / AI Export). False: determinate bar (log). */
    val spinner: Boolean = false,
    val message: String = "",
    val share: Intent? = null,
    val shareTitle: String = LanguageText.text("ui.export.fieldwatch.logs"),
    val error: String? = null,
    val errorTitle: String? = null,
    val cleared: Boolean = false,
    val saved: Boolean = false,
    val noticeTitle: String? = null,
    val noticeMessage: String? = null,
    /** Shown after the user dismisses [noticeTitle]. Catalog import skip-decode only. */
    val followUpTitle: String? = null,
    val followUpMessage: String? = null,
)

data class FieldwatchUi(
    val devices: List<Sighting> = emptyList(),
    val filtered: List<Sighting> = emptyList(),
    val fleets: List<Fleet> = emptyList(),
    val filter: FilterState = FilterState(),
    val presets: List<FilterPreset> = emptyList(),
    val watchlist: List<WatchTarget> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val selected: Sighting? = null,
    val draftFleet: Fleet? = null,
    val permissionsOk: Boolean = false,
    val scanning: Boolean = false,
    val wifiNow: Int = 0,
    val bleNow: Int = 0,
    val namedNow: Int = 0,
    val logLines: Long = 0,
    val throttleHint: String = "",
    val scanRadio: ScanRadioFacts = ScanRadioFacts(),
    val scanBlocked: Boolean = false,
    /** System Location services. Live uses this for the banner, separate from a blocked scan. */
    val locationOn: Boolean = true,
    val hiddenKnown: Int = 0,
    val arrivalsLearning: Boolean = false,
    val displayPaused: Boolean = false,
    val operatorSpanM: Double = 0.0,
    val takStatus: TakFeedStatus = TakFeedStatus(),
    /** This install's TAK tag. Not part of the settings pack. */
    val takInstallTag: String = "",
    val sit: SitUi = SitUi(),
    val catalogVersion: Int = 0,
    /** Phone GPS while Show live path is on this view. Empty when the band is off. Windowed. */
    val operatorPath: List<GpsSample> = emptyList(),
    /** Start of the live-map window. Sit start, or the later of switch-on and 15 minutes ago. */
    val livePathSince: Long = Long.MIN_VALUE,
    /** Watchlist alerts drawn on the live map. Not the filtered list. */
    val livePathRadios: List<Sighting> = emptyList(),
)

class FieldwatchViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as FieldwatchApp
    private val filters = FilterEngine()
    private val signatures = SignatureEngine()
    private val selectedKey = MutableStateFlow<String?>(null)
    private val draft = MutableStateFlow<Fleet?>(null)
    private var draftFromCandidates = false
    private val _export = MutableStateFlow(ExportUi())
    private val _placeStreets = MutableStateFlow<Map<String, String>>(emptyMap())
    val placeStreetNames: StateFlow<Map<String, String>> = _placeStreets
    val export: StateFlow<ExportUi> = _export
    private val _pendingSettingsImport = MutableStateFlow<PendingSettingsImport?>(null)
    val pendingSettingsImport: StateFlow<PendingSettingsImport?> = _pendingSettingsImport
    private val _language = MutableStateFlow(LanguageUiState())
    val language: StateFlow<LanguageUiState> = _language.asStateFlow()
    private val _candidates = MutableStateFlow(CandidatesUi())
    val candidates: StateFlow<CandidatesUi> = _candidates
    private val _logExportKind = MutableStateFlow(LogExportKind.LOG_CSV)
    val logExportKind: StateFlow<LogExportKind> = _logExportKind
    private val _logExportRadios = MutableStateFlow(LogExportRadios.BOTH)
    val logExportRadios: StateFlow<LogExportRadios> = _logExportRadios
    private val _sitExportKind = MutableStateFlow(LogExportKind.LOG_CSV)
    val sitExportKind: StateFlow<LogExportKind> = _sitExportKind
    private val _sitExportRadios = MutableStateFlow(LogExportRadios.BOTH)
    val sitExportRadios: StateFlow<LogExportRadios> = _sitExportRadios
    private val _sitPath = MutableStateFlow<SitPathPlot.Model?>(null)
    val sitPath: StateFlow<SitPathPlot.Model?> = _sitPath
    private val _pathTiles = MutableStateFlow<List<PathTiles.Tile>>(emptyList())
    val pathTiles: StateFlow<List<PathTiles.Tile>> = _pathTiles
    private val _livePathTiles = MutableStateFlow<List<PathTiles.Tile>>(emptyList())
    val livePathTiles: StateFlow<List<PathTiles.Tile>> = _livePathTiles
    private var livePathTileJob: Job? = null
    private var livePathTileKey: String? = null
    private val _pathAircraftTiles = MutableStateFlow<List<List<PathTiles.Tile>>>(emptyList())
    val pathAircraftTiles: StateFlow<List<List<PathTiles.Tile>>> = _pathAircraftTiles
    @Volatile private var pathRadios: List<Sighting> = emptyList()
    private val _liveFocus = MutableStateFlow(0)
    val liveFocus: StateFlow<Int> = _liveFocus
    private val flashUntil = HashMap<String, Long>()
    private val _flashKeys = MutableStateFlow<Set<String>>(emptySet())
    val flashKeys: StateFlow<Set<String>> = _flashKeys
    private val _beepSnap = MutableStateFlow(BeepSnap())
    val beepSnap: StateFlow<BeepSnap> = _beepSnap
    private val lastAlertAt = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val _alertedKeys = MutableStateFlow<Set<String>>(emptySet())
    val alertedKeys: StateFlow<Set<String>> = _alertedKeys
    private val clock = MutableStateFlow(System.currentTimeMillis())

    /** When Show live path last turned on, or when the last sit ended while the switch stayed on. */
    @Volatile
    private var livePathArmedAt: Long = System.currentTimeMillis()

    /**
     * Where the blue dot was at [livePathArmedAt]. The stored line only moves its
     * tip until the next bend, so the window needs this point to draw a stroke.
     */
    @Volatile
    private var livePathMark: GpsSample? = null
    private var openSitId: String? = null
    private var sawSitUi = false
    private val locationEpoch = MutableStateFlow(0)
    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            locationEpoch.value = locationEpoch.value + 1
        }
    }
    private val displayPaused = MutableStateFlow(false)
    private val heldSelected = MutableStateFlow<Sighting?>(null)
    private val familyLog = MutableStateFlow(FamilyLogSnap())
    val familyHint: StateFlow<SignatureFamilyHint?> = combine(
        selectedKey,
        heldSelected,
        app.devices.devices,
        familyLog,
        app.config.config,
    ) { key, held, live, log, config ->
        if (key == null) return@combine null
        val device = live.firstOrNull { it.key == key } ?: held?.takeIf { it.key == key }
            ?: return@combine null
        val hint = SignatureCandidates.assessFamily(device, live, log.radios, config.fleets)
        if (!log.loaded && hint.verdict == FamilyVerdict.SINGLE) return@combine null
        hint
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val huntKey = MutableStateFlow<String?>(null)
    private val huntStartedAt = MutableStateFlow(0L)
    private val huntPeakRssi = MutableStateFlow(-127)
    private val huntSamples = MutableStateFlow<List<RssiSample>>(emptyList())
    private val _outlineOpenClasses = MutableStateFlow<Set<String>>(emptySet())
    val outlineOpenClasses: StateFlow<Set<String>> = _outlineOpenClasses
    private val _outlineOpenSigs = MutableStateFlow<Set<String>>(emptySet())
    val outlineOpenSigs: StateFlow<Set<String>> = _outlineOpenSigs
    private val _catalogOpenClasses = MutableStateFlow<Set<String>>(emptySet())
    val catalogOpenClasses: StateFlow<Set<String>> = _catalogOpenClasses
    @Volatile private var frozenUi: FieldwatchUi? = null

    val gnssNotice: StateFlow<app.fieldwatch.domain.GnssNotice?> = app.gnss.reading
        .map { it.notice }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val gnssStatus: StateFlow<String> = app.gnss.reading
        .map { it.statusLine }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun dismissGnss() {
        app.gnss.dismiss()
    }

    val gnssProfile = app.gnssProfile.profile
    val gnssCalibration = app.gnss.calibration

    fun startGnssCalibration() = app.gnss.startCalibration()

    fun stopGnssCalibration() = app.gnss.stopCalibration()

    val gnssRecordings = app.gnssRaw.recordings

    fun shareGnssRecording(name: String) {
        val file = app.gnssRaw.file(name) ?: return
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            clipData = ClipData.newRawUri("gnss", uri)
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, LanguageText.text("ui.fieldwatch.gnss.recording"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        _export.value = ExportUi(share = intent, shareTitle = LanguageText.text("ui.gnss.recording"))
    }

    val floodNotice: StateFlow<PairingFlood.Notice?> = combine(
        app.pairingFlood.notice,
        app.wifiFlood.notice,
    ) { ble, wifi -> ble ?: wifi }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val floodHide: StateFlow<PairingFlood.FloodHide> = combine(
        app.pairingFlood.hide,
        app.wifiFlood.hide,
    ) { ble, wifi ->
        PairingFlood.FloodHide(
            episodeOn = ble.episodeOn || wifi.episodeOn,
            keys = if (wifi.keys.isEmpty()) ble.keys else if (ble.keys.isEmpty()) wifi.keys else ble.keys + wifi.keys,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PairingFlood.FloodHide())

    private val liveUi: StateFlow<FieldwatchUi> = combine(
        combine(app.devices.devices, app.devices.stats, app.config.config) { devices, stats, config ->
            Triple(devices, stats, config)
        },
        combine(selectedKey, draft, app.arrivals, clock, locationEpoch) { sel, fleetDraft, arr, now, _ ->
            arrayOf(sel, fleetDraft, arr, now)
        },
        lastAlertAt,
        app.tak.status,
        floodHide,
    ) { tripleA, quad, alerts, takStatus, hide ->
        val (devices, stats, config) = tripleA
        val sel = quad[0] as String?
        val fleetDraft = quad[1] as Fleet?
        val arr = quad[2] as app.fieldwatch.ArrivalsState
        val now = quad[3] as Long
        val labeled = devices
        val fleetNames = config.fleets.associate { it.id to it.name }
        val watchNames = config.watchlist.mapNotNull { row ->
            val key = row.deviceKey ?: return@mapNotNull null
            val label = row.label.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            key to label
        }.toMap()
        val windowMs = config.settings.averageWindowSec.coerceIn(10, 180) * 1000L
        val persistMs = maxOf(
            config.settings.staleSec.coerceAtLeast(15) * 1000L,
            config.settings.decaySec.coerceAtLeast(0) * 1000L,
        )
        val arrivalsOn = config.filter.arrivalsOnly && arr.active
        val learning = arrivalsOn && now <= arr.learningUntil
        var hiddenKnown = 0
        val moveCtx = if (config.filter.movingWithYou) {
            CoTravel.Ctx.of(app.operatorPathCopy())
        } else {
            CoTravel.Ctx.None
        }
        val classById = config.fleets.associate { it.id to it.kind }
        val hiddenFlood = hide.keys
        val mineKeys = RadioBookmarks.mineKeys(config.watchlist)
        val placeKeys = if (config.filter.seenAtPlaces && config.settings.placeWatch) {
            app.places.multiPlaceKeys(now, mineKeys)
        } else {
            emptySet()
        }
        val filtered = labeled.filter { device ->
            if (hiddenFlood.isNotEmpty() && device.key in hiddenFlood) return@filter false
            if (!filters.pass(
                    device,
                    config.filter,
                    moveCtx,
                    now,
                    classById,
                    watchNames.keys,
                    RadioBookmarks.watchedFleetIds(config.watchlist),
                    RadioBookmarks.alertDeviceKeys(config.watchlist),
                    mineKeys,
                    placeKeys,
                )
            ) {
                return@filter false
            }
            val heardAgo = now - device.lastSeen
            val inWindow = when (config.settings.viewMode) {
                ViewMode.TIMELINE -> heardAgo <= 15 * 60_000L
                else -> heardAgo <= persistMs || !device.gone
            }
            if (!inWindow) return@filter false
            if (!arrivalsOn) return@filter true
            val absorbed = device.key in arr.knownKeys ||
                (learning && device.kind == RadioKind.WIFI)
            if (absorbed) {
                hiddenKnown++
                return@filter false
            }
            heardAgo <= persistMs || !device.gone
        }.sortedWith(
            when (config.settings.listSort) {
                ListSort.STRENGTH ->
                    compareByDescending { it.sortRssi(config.settings.strengthSort, windowMs, now) }
                ListSort.NEWEST ->
                    compareByDescending<Sighting> { it.lastSeen }
                        .thenByDescending { it.sortRssi(config.settings.strengthSort, windowMs, now) }
                ListSort.NEWEST_ALERT ->
                    compareByDescending<Sighting> { alerts[it.key] ?: 0L }
                        .thenByDescending { it.lastSeen }
                ListSort.FIRST_SEEN ->
                    compareByDescending<Sighting> { it.firstSeen }
                        .thenByDescending { it.lastSeen }
                ListSort.ARRIVAL ->
                    compareBy<Sighting> { it.firstSeen }.thenBy { it.mac }
                ListSort.NAME ->
                    compareBy<Sighting> { d ->
                        val names = d.fleetIds.map { fleetNames[it] ?: it }
                        d.listLineText(config.settings.listTitleLine, names, watchNames[d.key]).lowercase()
                    }.thenBy { it.mac }
                ListSort.SIGNATURES ->
                    compareByDescending<Sighting> { it.fleetIds.isNotEmpty() }
                        .thenByDescending { it.sortRssi(config.settings.strengthSort, windowMs, now) }
            },
        )
        val scanRadio = ScanRadioFacts(
            scanning = stats.scanning,
            wifiOn = stats.wifiRadioOn,
            wifiWaitingOnOs = stats.wifiWaitingOnOs,
            lastWifiScanAt = stats.lastWifiScanAt,
            bleOn = stats.bleRadioOn,
            bleRunning = stats.bleRunning,
            bleParked = stats.bleParked,
            bleRetrying = stats.bleRetrying,
            bleStarting = stats.bleStarting,
            bleHitsLastMin = stats.bleHitsLastMin,
        )
        val pathOn = config.settings.livePathOnScreen()
        val alertKeys = if (pathOn) RadioBookmarks.alertDeviceKeys(config.watchlist) else emptySet()
        val watchedFleets = if (pathOn) RadioBookmarks.watchedFleetIds(config.watchlist) else emptySet()
        val openPath = if (pathOn) {
            app.sits.openLivePath(alertKeys, watchedFleets, hiddenFlood)
        } else {
            null
        }
        val pathSince = if (pathOn) {
            SitPathPlot.livePathSince(now, livePathArmedAt, openPath?.startAt)
        } else {
            Long.MIN_VALUE
        }
        val pathSamples = when {
            !pathOn -> emptyList()
            openPath != null -> openPath.samples
            else -> SitPathPlot.livePathSamples(app.operatorPathCopy(), pathSince, livePathMark)
        }
        val pathRadios = when {
            !pathOn -> emptyList()
            openPath != null -> openPath.radios
            else -> SitPathPlot.livePathRadios(labeled, pathSince, alertKeys, watchedFleets, hiddenFlood)
        }
        FieldwatchUi(
            devices = labeled,
            filtered = filtered,
            fleets = config.fleets,
            filter = config.filter,
            presets = config.presets,
            watchlist = config.watchlist,
            settings = config.settings,
            selected = labeled.firstOrNull { it.key == sel },
            draftFleet = fleetDraft,
            permissionsOk = RadioPermissions.granted(app),
            scanning = stats.scanning,
            wifiNow = stats.wifiNow,
            bleNow = stats.bleNow,
            namedNow = stats.namedNow,
            logLines = stats.logLines,
            throttleHint = stats.throttleHint,
            scanRadio = scanRadio,
            scanBlocked = ScanStatus.isBlocked(scanRadio, app.systemLocationOn(), app.hasFineLocation()),
            locationOn = app.systemLocationOn(),
            hiddenKnown = hiddenKnown,
            arrivalsLearning = learning,
            operatorSpanM = if (moveCtx.ready || config.filter.movingWithYou) {
                moveCtx.pathLengthM
            } else {
                app.operatorPathLengthM()
            },
            takStatus = takStatus,
            takInstallTag = config.takInstallTag,
            catalogVersion = config.version,
            operatorPath = pathSamples,
            livePathSince = pathSince,
            livePathRadios = pathRadios,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            FieldwatchUi(
                settings = app.config.settings,
                takInstallTag = app.config.takInstallTag,
                catalogVersion = app.config.config.value.version,
            ),
        )

    val ui: StateFlow<FieldwatchUi> = combine(liveUi, displayPaused, selectedKey, heldSelected, app.sits.ui) { live, paused, selKey, held, sit ->
        if (!paused) {
            frozenUi = null
            val selected = live.selected ?: held?.takeIf { selKey != null && it.key == selKey }?.let { snap ->
                if (snap.gone) snap else snap.copy(gone = true)
            }
            live.copy(displayPaused = false, selected = selected, sit = sit)
        } else {
            val hold = frozenUi ?: live
            frozenUi = hold
            val selected = held?.takeIf { selKey == null || it.key == selKey }
                ?: selKey?.let { key ->
                    hold.filtered.firstOrNull { it.key == key }
                        ?: hold.devices.firstOrNull { it.key == key }
                }
            live.copy(
                displayPaused = true,
                devices = hold.devices,
                filtered = hold.filtered,
                selected = selected,
                sit = sit,
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        FieldwatchUi(settings = app.config.settings, takInstallTag = app.config.takInstallTag),
    )

    val hunt: StateFlow<HuntUi> = combine(
        combine(huntKey, huntStartedAt, huntPeakRssi, huntSamples) { key, started, peak, samples ->
            arrayOf(key, started, peak, samples)
        },
        app.devices.devices,
        clock,
    ) { bits, devices, now ->
        val key = bits[0] as String?
        val started = bits[1] as Long
        val peak = bits[2] as Int
        @Suppress("UNCHECKED_CAST")
        val samples = bits[3] as List<RssiSample>
        if (key == null) return@combine HuntUi()
        val device = devices.firstOrNull { it.key == key }
        HuntUi(
            active = true,
            device = device,
            title = device?.listTitle() ?: "Hunt",
            cue = Hunt.cue(samples, now, device?.lastSeen, device == null && started > 0L),
            peakRssi = peak,
            samples = samples,
            lastSeen = device?.lastSeen ?: 0L,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HuntUi())

    /** Re-read system Location. Live calls this when the screen resumes. */
    fun refreshLocation() {
        locationEpoch.value = locationEpoch.value + 1
    }

    override fun onCleared() {
        runCatching { app.unregisterReceiver(locationReceiver) }
        super.onCleared()
    }

    init {
        armLivePath()
        app.loadedLanguage()?.let { store ->
            _language.value = LanguageUiState(store.current())
        }
        ContextCompat.registerReceiver(
            app,
            locationReceiver,
            IntentFilter(LocationManager.MODE_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        viewModelScope.launch {
            while (true) {
                delay(1_000)
                val now = System.currentTimeMillis()
                clock.value = now
                val liveKeys = app.devices.devices.value.mapTo(HashSet()) { it.key }
                app.pairingFlood.tick(now, app.floodSpeed(now))
                app.pairingFlood.prune(liveKeys)
                app.wifiFlood.prune(liveKeys)
                app.sits.noteFloods(app.pairingFlood.bursts())
                app.sits.noteFloods(app.wifiFlood.bursts())
                app.sits.noteGnss(app.gnss.marks())
            }
        }
        viewModelScope.launch {
            combine(selectedKey, app.devices.devices) { key, devices ->
                key to key?.let { k -> devices.firstOrNull { it.key == k } }
            }.collect { (key, device) ->
                when {
                    key == null -> heldSelected.value = null
                    device != null -> heldSelected.value = device
                }
            }
        }
        viewModelScope.launch {
            app.sits.ui.collect { sit ->
                val id = sit.open?.id
                if (sawSitUi && openSitId != null && id == null) {
                    armLivePath()
                }
                sawSitUi = true
                openSitId = id
                val open = id != null
                app.pairingFlood.setSitOpen(open)
                app.wifiFlood.setSitOpen(open)
                refreshSitPath()
            }
        }
        viewModelScope.launch {
            selectedKey.collect { key ->
                if (key == null) return@collect
                runCatching { app.logs.readRadios() }
                    .onSuccess { radios -> familyLog.value = FamilyLogSnap(radios, loaded = true) }
                    .onFailure { familyLog.value = familyLog.value.copy(loaded = true) }
            }
        }
        viewModelScope.launch {
            combine(app.devices.devices, huntKey) { devices, key ->
                key to devices.firstOrNull { it.key == key }
            }.collect { (key, device) ->
                if (key == null || device == null) return@collect
                val last = huntSamples.value.lastOrNull()
                if (last != null && device.lastSeen <= last.at) return@collect
                val sample = RssiSample(device.lastSeen, device.rssi)
                huntSamples.update { (it + sample).takeLast(120) }
                if (device.rssi > huntPeakRssi.value) huntPeakRssi.value = device.rssi
            }
        }
        viewModelScope.launch {
            app.alerter.flashes.collect { key ->
                revealOutlineFor(key)
                lastAlertAt.update { it + (key to System.currentTimeMillis()) }
                _alertedKeys.update { it + key }
                val end = System.currentTimeMillis() + FLASH_MS
                flashUntil[key] = end
                _flashKeys.value = flashUntil.keys.toSet()
                if (app.config.settings.snapToBeep) {
                    _beepSnap.update { it.copy(seq = it.seq + 1, key = key) }
                }
                launch {
                    delay(FLASH_MS)
                    if (flashUntil[key] == end) {
                        flashUntil.remove(key)
                        _flashKeys.value = flashUntil.keys.toSet()
                    }
                }
            }
        }
    }

    fun refreshPermissions() {
        viewModelScope.launch { app.config.update { it } }
    }

    fun startScan() {
        if (!app.config.settings.disclaimerOk()) return
        if (RadioPermissions.granted(app)) app.startScanning()
    }

    fun acceptDisclaimer() {
        viewModelScope.launch {
            app.config.update {
                it.copy(
                    settings = it.settings.copy(
                        disclaimerAccepted = true,
                        disclaimerRev = DISCLAIMER_REV,
                    ),
                )
            }
            startScan()
        }
    }

    fun dismissPairingFlood() {
        if (app.pairingFlood.notice.value != null) app.pairingFlood.dismiss()
        else app.wifiFlood.dismiss()
    }

    fun setHideBurst(on: Boolean) {
        if (app.pairingFlood.notice.value != null) app.pairingFlood.setHideBurst(on)
        else app.wifiFlood.setHideBurst(on)
    }

    fun hidePairingFlood() {
        if (app.pairingFlood.notice.value != null) {
            app.pairingFlood.setHideBurst(true)
            app.pairingFlood.dismiss()
        } else {
            app.wifiFlood.setHideBurst(true)
            app.wifiFlood.dismiss()
        }
    }

    fun clearHiddenFlood() {
        app.pairingFlood.clearHidden()
        app.wifiFlood.clearHidden()
    }

    fun dismissLiveTour() {
        viewModelScope.launch {
            app.config.update {
                it.copy(settings = it.settings.copy(liveTourDone = true))
            }
        }
    }

    /** Ends the tour and opens the Live display panel in one save. */
    fun openTuneFromTour() {
        viewModelScope.launch {
            app.config.update {
                it.copy(
                    settings = it.settings.copy(
                        liveTourDone = true,
                        scanControlsExpanded = true,
                    ),
                )
            }
        }
    }

    fun showLiveTour(then: () -> Unit = {}) {
        viewModelScope.launch {
            app.config.update {
                it.copy(settings = it.settings.copy(liveTourDone = false))
            }
            then()
        }
    }

    fun stopScan() = app.stopScanning()

    fun select(key: String?) {
        selectedKey.value = key
        if (key == null) heldSelected.value = null
    }

    fun select(device: Sighting) {
        heldSelected.value = device
        selectedKey.value = device.key
    }

    fun startHunt(device: Sighting) {
        val now = System.currentTimeMillis()
        huntKey.value = device.key
        huntStartedAt.value = now
        huntPeakRssi.value = device.rssi
        huntSamples.value = listOf(RssiSample(now, device.rssi))
    }

    fun resetHunt() {
        val key = huntKey.value ?: return
        val live = app.devices.devices.value.firstOrNull { it.key == key } ?: return
        startHunt(live)
    }

    fun stopHunt() {
        huntKey.value = null
        huntSamples.value = emptyList()
        huntPeakRssi.value = -127
        huntStartedAt.value = 0L
    }

    fun huntTick(beepOn: Boolean, vibrateOn: Boolean) {
        if (!beepOn && !vibrateOn) return
        app.alerter.huntTick(beepOn, vibrateOn)
    }

    fun setViewMode(mode: ViewMode) {
        viewModelScope.launch {
            app.config.update { it.copy(settings = it.settings.copy(viewMode = mode)) }
        }
    }

    fun toggleOutlineClass(id: String) {
        val open = _outlineOpenClasses.value
        _outlineOpenClasses.value = if (id in open) {
            _outlineOpenSigs.value = _outlineOpenSigs.value.filterNot { it.startsWith("$id/") }.toSet()
            open - id
        } else {
            open + id
        }
    }

    fun toggleOutlineSignature(id: String) {
        val open = _outlineOpenSigs.value
        _outlineOpenSigs.value = if (id in open) open - id else open + id
    }

    fun toggleCatalogClass(id: String) {
        val open = _catalogOpenClasses.value
        _catalogOpenClasses.value = if (id in open) open - id else open + id
    }

    private fun revealOutlineFor(deviceKey: String) {
        val device = app.devices.devices.value.firstOrNull { it.key == deviceKey } ?: return
        val classBy = app.config.fleets.associate { it.id to it.kind }
        val reveal = ClassOutline.reveal(device, classBy)
        _outlineOpenClasses.update { it + reveal.classIds }
        _outlineOpenSigs.update { it + reveal.sigKeys }
    }

    fun setStrengthSort(sort: StrengthSort, windowSec: Int? = null) {
        viewModelScope.launch {
            app.config.update {
                it.copy(
                    settings = it.settings.copy(
                        listSort = ListSort.STRENGTH,
                        strengthSort = sort,
                        averageWindowSec = windowSec ?: it.settings.averageWindowSec,
                    ),
                )
            }
        }
    }

    fun setListSort(sort: ListSort) {
        viewModelScope.launch {
            app.config.update { it.copy(settings = it.settings.copy(listSort = sort)) }
        }
    }

    fun setSignatureListSort(sort: SignatureListSort) {
        viewModelScope.launch {
            app.config.update { it.copy(settings = it.settings.copy(signatureListSort = sort)) }
        }
    }

    fun setDecaySec(sec: Int) {
        viewModelScope.launch {
            app.config.update { it.copy(settings = it.settings.copy(decaySec = sec.coerceIn(0, 60))) }
        }
    }

    fun setShowRssiBar(show: Boolean) {
        viewModelScope.launch {
            app.config.update { it.copy(settings = it.settings.copy(showRssiBar = show)) }
        }
    }

    fun toggleRssiBar() {
        viewModelScope.launch {
            app.config.update {
                it.copy(settings = it.settings.copy(showRssiBar = !it.settings.showRssiBar))
            }
        }
    }

    private fun armLivePath(at: Long = System.currentTimeMillis()) {
        livePathArmedAt = at
        val tip = app.operatorPathCopy().lastOrNull()
        livePathMark = tip?.let { GpsSample(at, it.lat, it.lon) }
    }

    fun toggleLivePath() {
        viewModelScope.launch {
            app.config.update { config ->
                val on = !config.settings.showLivePath
                if (on) armLivePath()
                config.copy(settings = config.settings.copy(showLivePath = on))
            }
        }
    }

    /** Persist the divider after the finger lifts. Skip a write that did not really move. */
    fun setLivePathSplit(fraction: Float) {
        val next = fraction.coerceIn(0.22f, 0.78f)
        viewModelScope.launch {
            app.config.update {
                val current = it.settings.livePathSplit
                if (abs(current - next) < 0.01f) it
                else it.copy(settings = it.settings.copy(livePathSplit = next))
            }
        }
    }

    /**
     * Street tiles for the live path only. Reports keep [pathTiles].
     * One fix is enough to show the neighborhood. The line still needs a second point.
     * A coarse key skips a fetch while the walk has barely moved.
     */
    fun refreshLivePathTiles(samples: List<GpsSample>, online: Boolean) {
        if (!online || samples.isEmpty()) {
            livePathTileJob?.cancel()
            livePathTileJob = null
            livePathTileKey = null
            if (_livePathTiles.value.isNotEmpty()) _livePathTiles.value = emptyList()
            return
        }
        val end = samples.last()
        val key = "${samples.size / 4}:${(end.lat * 1000.0).roundToInt()}:${(end.lon * 1000.0).roundToInt()}"
        if (key == livePathTileKey) return
        livePathTileKey = key
        livePathTileJob?.cancel()
        livePathTileJob = viewModelScope.launch(Dispatchers.Default) {
            val tiles = runCatching {
                PathTiles.load(app, samples, onlineLookup = true)
            }.getOrDefault(emptyList())
            if (isActive && livePathTileKey == key) _livePathTiles.value = tiles
        }
    }

    /**
     * Tiles for the area the user is looking at. The pictures already on screen stay
     * until the new ones arrive. The same tile set is not fetched again.
     */
    fun refreshLivePathViewport(view: SitPathPlot.Viewport, online: Boolean) {
        if (!online) {
            refreshLivePathTiles(emptyList(), false)
            return
        }
        val bounds = view.coveredBounds()
        val key = "v:" + PathTiles.coverageKey(bounds[0], bounds[1], bounds[2], bounds[3])
        if (key == "v:off" || key == livePathTileKey) return
        livePathTileKey = key
        livePathTileJob?.cancel()
        livePathTileJob = viewModelScope.launch(Dispatchers.Default) {
            val tiles = runCatching {
                PathTiles.loadBounds(
                    app,
                    south = bounds[0],
                    north = bounds[1],
                    west = bounds[2],
                    east = bounds[3],
                    onlineLookup = true,
                )
            }.getOrDefault(emptyList())
            if (isActive && livePathTileKey == key && tiles.isNotEmpty()) _livePathTiles.value = tiles
        }
    }

    fun toggleFleetName() {
        viewModelScope.launch {
            app.config.update {
                it.copy(settings = it.settings.copy(showFleetName = !it.settings.showFleetName))
            }
        }
    }

    fun toggleFrequency() {
        viewModelScope.launch {
            app.config.update {
                it.copy(settings = it.settings.copy(showFrequency = !it.settings.showFrequency))
            }
        }
    }

    fun toggleSeenTimes() {
        viewModelScope.launch {
            app.config.update {
                it.copy(settings = it.settings.copy(showSeenTimes = !it.settings.showSeenTimes))
            }
        }
    }

    fun setListTitleLine(line: ListLine) {
        if (line == ListLine.NONE) return
        viewModelScope.launch {
            app.config.update { it.copy(settings = it.settings.copy(listTitleLine = line)) }
        }
    }

    fun setListSubtitleLine(line: ListLine) {
        viewModelScope.launch {
            app.config.update { it.copy(settings = it.settings.copy(listSubtitleLine = line)) }
        }
    }

    fun markArrivalsSeen() {
        val keys = if (displayPaused.value) frozenUi?.filtered?.map { it.key } else null
        app.markArrivalsSeen(keys)
    }

    fun resetArrivalsSeen() {
        app.resetSeenBuffer()
    }

    fun resetFollowSession() {
        app.resetFollowSession()
    }

    fun setScanControlsExpanded(expanded: Boolean) {
        viewModelScope.launch {
            app.config.update { it.copy(settings = it.settings.copy(scanControlsExpanded = expanded)) }
        }
    }

    fun focusLiveList() {
        _liveFocus.value = _liveFocus.value + 1
    }

    fun toggleLiveDisplay() {
        displayPaused.value = !displayPaused.value
    }

    fun defaultSitName(): String = Sit.defaultName(System.currentTimeMillis())

    fun startSit(name: String) {
        viewModelScope.launch {
            val heard = app.devices.devices.value.filter { !it.gone }
            app.sits.start(name, heard, app.config.fleets, app.config.watchlist)
            publishSitNotice()
        }
    }

    fun endSit() {
        viewModelScope.launch {
            app.sits.end(app.config.fleets)
            publishSitNotice()
        }
    }

    fun renameSit(id: String, name: String) {
        viewModelScope.launch { app.sits.rename(id, name) }
    }

    fun deleteSit(id: String) {
        viewModelScope.launch { app.sits.delete(id) }
    }

    fun deleteAllSits() {
        viewModelScope.launch { app.sits.deleteAllClosed() }
    }

    fun selectSit(id: String?) {
        app.sits.select(id)
    }

    fun selectCompareSit(id: String?) {
        app.sits.selectCompare(id)
    }

    fun startSitCompare() {
        if (_export.value.active) return
        viewModelScope.launch {
            publishExport(0.08f, LanguageText.text("ui.writing.sit.compare"))
            runCatching {
                val doc = sitCompareDoc()
                publishExport(0.85f, LanguageText.text("ui.writing.sit.compare"))
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, compareSubject(doc))
                    putExtra(Intent.EXTRA_TEXT, doc.toPlainText())
                }
            }.onSuccess { intent ->
                _export.value = ExportUi(
                    active = false,
                    progress = 1f,
                    share = intent,
                    shareTitle = LanguageText.text("ui.sit.compare.2"),
                )
            }.onFailure { err ->
                _export.value = ExportUi(error = err.message ?: LanguageText.text("ui.could.not.write.sit"))
            }
        }
    }

    fun startSitComparePdf() {
        if (_export.value.active) return
        viewModelScope.launch {
            publishExport(0.06f, LanguageText.text("ui.writing.sit.compare.pdf"))
            runCatching {
                val doc = sitCompareDoc()
                publishExport(0.35f, LanguageText.text("ui.laying.out.sit.compare"))
                val dir = File(app.cacheDir, "debrief").apply { mkdirs() }
                val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
                    .format(java.util.Date())
                val file = File(dir, "fieldwatch-sit-compare-$stamp.pdf")
                publishExport(0.32f, LanguageText.text("ui.loading.map.tiles"))
                val tiles = pathTilesForFigure(doc.pathFigure)
                val extraTiles = doc.extraFigures.map { pathTilesForFigure(it) }
                withContext(Dispatchers.Default) {
                    DebriefPdf.write(doc, file, tiles, extraTiles) { p ->
                        kotlinx.coroutines.runBlocking {
                            publishExport(0.38f + 0.55f * p, LanguageText.text("ui.writing.sit.compare.pdf"))
                        }
                    }
                }
                val uri: Uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    clipData = ClipData.newRawUri("sit-compare", uri)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, compareSubject(doc))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }.onSuccess { intent ->
                _export.value = ExportUi(
                    active = false,
                    progress = 1f,
                    share = intent,
                    shareTitle = LanguageText.text("ui.sit.compare.pdf"),
                )
            }.onFailure { err ->
                _export.value = ExportUi(error = err.message ?: LanguageText.text("ui.could.not.write.sit.2"))
            }
        }
    }

    private fun compareSubject(doc: DebriefDoc): String =
        if (doc.windowLine.isNotBlank()) "Fieldwatch sit compare — ${doc.windowLine}"
        else LanguageText.text("ui.fieldwatch.sit.compare")

    fun startSitCompareAiExport() {
        if (_export.value.active) return
        viewModelScope.launch {
            publishExport(0.08f, LanguageText.text("ui.building.compare.ai.export"))
            runCatching {
                val (thisSide, second) = compareSides()
                publishExport(0.45f, LanguageText.text("ui.building.compare.ai.export"))
                val text = withContext(Dispatchers.Default) {
                    SitDiffPrompt.build(thisSide, second, app.config.settings.demoMode)
                }
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                        Intent.EXTRA_SUBJECT,
                        "Fieldwatch sit compare AI export — ${thisSide.name} vs ${second.name}",
                    )
                    putExtra(Intent.EXTRA_TEXT, text)
                }
            }.onSuccess { intent ->
                _export.value = ExportUi(
                    active = false,
                    progress = 1f,
                    share = intent,
                    shareTitle = LanguageText.text("ui.sit.compare.ai.export"),
                )
            }.onFailure { err ->
                _export.value = ExportUi(error = err.message ?: LanguageText.text("ui.could.not.write.compare"))
            }
        }
    }

    private suspend fun sitCompareDoc(): DebriefDoc {
        val (thisSide, second) = compareSides()
        val radios = thisSide.radios + second.radios
        return SitDiff.document(
            thisSide,
            second,
            RadioBookmarks.watchedFleetIds(app.config.watchlist),
            showAllRadios = app.config.settings.debriefShowAllRadios,
        )
            .withDemoMacs(radios.map { it.mac }, app.config.settings.demoMode, radios.map { it.payloadUasId })
    }

    private suspend fun compareSides(): Pair<SitDiff.Side, SitDiff.Side> {
        val sit = app.sits.ui.value
        val otherId = sit.compareId ?: error(LanguageText.text("ui.pick.second.sit"))
        val otherFile = withContext(Dispatchers.IO) { app.sits.sitFile(otherId) }
            ?: error(LanguageText.text("ui.could.not.read.sit"))
        val fleets = app.config.fleets
        val customNames = RadioBookmarks.labels(app.config.watchlist)
        val observerNotes = RadioBookmarks.notes(app.config.watchlist)
        val bookmarkedKeys = RadioBookmarks.alertDeviceKeys(app.config.watchlist)
        val mineKeys = RadioBookmarks.mineKeys(app.config.watchlist)
        val thisSide = compareThisSide(sit, fleets, customNames, observerNotes, bookmarkedKeys, mineKeys)
        val second = SitDiff.Side(
            name = otherFile.summary.name,
            ram = false,
            radios = otherFile.radios.map {
                SitDiff.fromSitRadio(it, fleets, customNames, observerNotes, bookmarkedKeys, mineKeys)
            },
            path = otherFile.operatorPath,
            floods = otherFile.floods,
            gnss = otherFile.gnss,
        )
        return thisSide to second
    }

    private suspend fun compareThisSide(
        sit: SitUi,
        fleets: List<Fleet>,
        customNames: Map<String, String>,
        observerNotes: Map<String, String>,
        bookmarkedKeys: Set<String>,
        mineKeys: Set<String>,
    ): SitDiff.Side {
        val open = sit.open
        if (open != null) {
            val source = app.sits.debriefSource()
            return SitDiff.Side(
                name = open.name,
                ram = false,
                radios = source?.devices.orEmpty().map {
                    SitDiff.fromSighting(it, fleets, customNames, observerNotes, bookmarkedKeys, mineKeys)
                },
                path = source?.operatorPath.orEmpty(),
                floods = source?.floods.orEmpty(),
                gnss = source?.gnss.orEmpty(),
            )
        }
        val selected = sit.closed.firstOrNull { it.id == sit.selectedId }
        if (selected != null) {
            val file = withContext(Dispatchers.IO) { app.sits.sitFile(selected.id) }
                ?: error(LanguageText.text("ui.could.not.read.this.sit"))
            return SitDiff.Side(
                name = selected.name,
                ram = false,
                radios = file.radios.map {
                    SitDiff.fromSitRadio(it, fleets, customNames, observerNotes, bookmarkedKeys, mineKeys)
                },
                path = file.operatorPath,
                floods = file.floods,
                gnss = file.gnss,
            )
        }
        val now = System.currentTimeMillis()
        val since = now - DebriefPrompt.WINDOW_MS
        return SitDiff.Side(
            name = LanguageText.text("ui.last.15.minutes"),
            ram = true,
            radios = app.devices.devices.value.map {
                SitDiff.fromSighting(it, fleets, customNames, observerNotes, bookmarkedKeys, mineKeys)
            },
            path = app.operatorPathCopy().filter { it.at >= since },
            floods = app.floodBursts().filter { it.at >= since },
            gnss = app.gnss.marks().filter { it.at >= since || it.endedAt >= since },
        )
    }

    private fun publishSitNotice() {
        val notice = app.sits.ui.value.notice ?: return
        _export.value = ExportUi(noticeTitle = "Sits", noticeMessage = notice)
        app.sits.consumeNotice()
    }

    fun updateFilter(transform: (FilterState) -> FilterState) {
        viewModelScope.launch {
            var nextOn = app.config.filter.arrivalsOnly
            app.config.update {
                val next = transform(it.filter)
                nextOn = next.arrivalsOnly
                it.copy(filter = next)
            }
            app.syncArrivals(nextOn)
        }
    }

    fun applyPreset(preset: FilterPreset) {
        viewModelScope.launch {
            app.config.update { it.copy(filter = preset.filter) }
            app.syncArrivals(preset.filter.arrivalsOnly)
        }
    }

    fun savePreset(name: String) {
        viewModelScope.launch {
            app.config.update { cfg ->
                val preset = FilterPreset(UUID.randomUUID().toString(), name, cfg.filter)
                cfg.copy(presets = cfg.presets + preset)
            }
        }
    }

    fun deletePreset(id: String) {
        viewModelScope.launch {
            app.config.update { cfg ->
                val removing = cfg.presets.firstOrNull { it.id == id }
                val hidden = if (removing?.isBuiltIn() == true) cfg.hiddenPresetIds + id else cfg.hiddenPresetIds
                cfg.copy(
                    presets = cfg.presets.filterNot { it.id == id },
                    hiddenPresetIds = hidden,
                )
            }
        }
    }

    fun upsertFleet(fleet: Fleet, after: (() -> Unit)? = null) {
        viewModelScope.launch {
            app.config.update { cfg ->
                val existing = cfg.fleets.indexOfFirst { it.id == fleet.id }
                val next = cfg.fleets.toMutableList()
                if (existing >= 0) next[existing] = fleet else next += fleet
                cfg.copy(fleets = next)
            }
            draft.value = null
            app.devices.refresh(
                app.config.fleets,
                app.config.settings.staleSec,
                policy = app.config.settings.detectionPolicy(),
                decaySec = app.config.settings.decaySec,
            )
            after?.invoke()
        }
    }

    fun deleteFleet(id: String) {
        viewModelScope.launch {
            app.config.update { cfg ->
                cfg.copy(
                    fleets = cfg.fleets.filterNot { it.id == id },
                    watchlist = cfg.watchlist.filterNot { it.fleetId == id },
                    filter = cfg.filter.copy(
                        fleetIds = cfg.filter.fleetIds - id,
                        includeFleetIds = cfg.filter.includeFleetIds - id,
                    ),
                )
            }
            draft.value = null
            app.devices.refresh(
                app.config.fleets,
                app.config.settings.staleSec,
                policy = app.config.settings.detectionPolicy(),
                decaySec = app.config.settings.decaySec,
            )
        }
    }

    fun beginCreateFrom(device: Sighting) {
        draftFromCandidates = false
        draft.value = signatures.suggestFleet(device)
    }

    fun beginCreateFromCandidate(candidate: SignatureCandidate) {
        draftFromCandidates = true
        draft.value = SignatureCandidates.suggestFleet(candidate)
    }

    fun takeDraftFromCandidates(): Boolean {
        val hit = draftFromCandidates
        draftFromCandidates = false
        return hit
    }

    fun startSignatureCandidates() {
        if (_candidates.value.loading) return
        viewModelScope.launch {
            _candidates.value = CandidatesUi(
                loading = true,
                progress = 0f,
                message = LanguageText.text("ui.reading.log"),
            )
            yield()
            runCatching {
                val radios = app.logs.readRadios { copied, total ->
                    val fraction = (copied.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    publishCandidates(
                        fraction * 0.9f,
                        "Reading the log · ${copied / 1024} KB of ${total / 1024} KB",
                    )
                }
                publishCandidates(0.92f, LanguageText.text("ui.matching.catalog"))
                withContext(Dispatchers.Default) {
                    SignatureCandidates.analyze(radios, app.config.fleets)
                }
            }.onSuccess { report ->
                _candidates.value = CandidatesUi(report = report)
            }.onFailure { err ->
                _candidates.value = CandidatesUi(error = err.message ?: LanguageText.text("ui.could.not.read.log"))
            }
        }
    }

    private suspend fun publishCandidates(progress: Float, message: String) {
        withContext(Dispatchers.Main.immediate) {
            val cur = _candidates.value
            if (!cur.loading) return@withContext
            _candidates.value = cur.copy(progress = progress.coerceIn(0f, 1f), message = message)
        }
        yield()
    }

    fun beginNewFleet() {
        draftFromCandidates = false
        draft.value = DefaultCatalog.newBlankFleet()
    }

    fun editFleet(fleet: Fleet) {
        draftFromCandidates = false
        draft.value = fleet
    }

    fun saveFleetKeepDraft(fleet: Fleet) {
        viewModelScope.launch {
            app.config.update { cfg ->
                val existing = cfg.fleets.indexOfFirst { it.id == fleet.id }
                val next = cfg.fleets.toMutableList()
                if (existing >= 0) next[existing] = fleet else next += fleet
                cfg.copy(fleets = next)
            }
            draft.value = fleet
        }
    }

    fun cancelDraft() {
        draft.value = null
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            val prev = app.config.settings
            app.config.update { cfg ->
                var nextSettings = transform(cfg.settings)
                if (app.gnssProfile.profile.value == null) {
                    nextSettings = nextSettings.copy(gnssMonitor = false)
                }
                cfg.copy(settings = nextSettings)
            }
            val next = app.config.settings
            app.logs.configure(next.logFormat, next.logRotateKb, next.loggingEnabled)
            if (prev.tagLocation != next.tagLocation) app.syncLocationUpdates()
            if (prev.gnssSettings() != next.gnssSettings() || prev.tagLocation != next.tagLocation) {
                app.syncGnss()
            }
            if (prev.intensity != next.intensity && app.devices.stats.value.scanning) {
                app.startScanning()
            }
            if (prev.detectionPolicy() != next.detectionPolicy()) {
                app.devices.refresh(
                    app.config.fleets,
                    next.staleSec,
                    policy = next.detectionPolicy(),
                    decaySec = next.decaySec,
                )
            }
            if (next.alertVoice || next.gnssVoice) app.alerter.prepareVoice()
        }
    }

    /** New install tag. A typed callsign stays. Blank keeps meaning Fieldwatch- plus the tag. */
    fun regenerateTakTag() {
        viewModelScope.launch {
            val cur = app.config.config.value
            val oldTag = cur.takInstallTag
            val settings = cur.settings
            if (settings.takEnabled && !settings.demoMode) {
                app.tak.retireTag(
                    oldTag,
                    TakInstall.callsign(settings.takCallsign, oldTag),
                    settings,
                )
            }
            var next = TakInstall.newTag()
            if (next == TakInstall.clean(oldTag)) next = TakInstall.newTag()
            app.config.update { cfg -> cfg.copy(takInstallTag = next) }
        }
    }

    fun toggleWatchDevice(device: Sighting) {
        viewModelScope.launch {
            app.config.update { cfg ->
                val names = device.fleetIds.mapNotNull { id -> cfg.fleets.firstOrNull { it.id == id }?.name }
                cfg.copy(
                    watchlist = RadioBookmarks.toggleAlert(
                        cfg.watchlist,
                        device.key,
                        RadioBookmarks.suggestLabel(device, names),
                    ),
                )
            }
        }
    }

    fun saveRadioName(device: Sighting, name: String) {
        viewModelScope.launch {
            app.config.update { cfg ->
                cfg.copy(watchlist = RadioBookmarks.upsertName(cfg.watchlist, device.key, name))
            }
        }
    }

    fun setRadioAlert(id: String, on: Boolean) {
        viewModelScope.launch {
            app.config.update { cfg ->
                cfg.copy(watchlist = RadioBookmarks.setAlert(cfg.watchlist, id, on))
            }
        }
    }

    fun isMine(deviceKey: String): Boolean =
        app.config.watchlist.any { it.deviceKey == deviceKey && it.mine }

    /** Place lines for this radio. Empty when Places is off or the address cannot be kept. */
    fun placeLines(device: Sighting, streets: Map<String, String> = emptyMap()): List<String> {
        val settings = app.config.settings
        if (!settings.placeWatch) return emptyList()
        val named = if (settings.demoMode || !settings.onlineLookup) emptyMap() else streets
        return app.places.linesFor(
            device,
            app.config.fleets,
            RadioBookmarks.mineKeys(app.config.watchlist),
            System.currentTimeMillis(),
            demo = settings.demoMode,
            streets = named,
        )
    }

    /** Fill street names for this radio's places when Online place names is on. */
    fun refreshPlaceStreets(device: Sighting) {
        viewModelScope.launch { streetsForDevice(device) }
    }

    private suspend fun streetsForDevice(device: Sighting): Map<String, String> {
        val settings = app.config.settings
        if (!settings.placeWatch || !settings.onlineLookup || settings.demoMode) return emptyMap()
        val points = app.places.placePoints(
            device,
            app.config.fleets,
            RadioBookmarks.mineKeys(app.config.watchlist),
            System.currentTimeMillis(),
        )
        if (points.isEmpty()) return _placeStreets.value
        val missing = points.filter { (lat, lon) -> Geo.cellKey(lat, lon) !in _placeStreets.value }
        if (missing.isEmpty()) return _placeStreets.value
        val found = PlaceLookup.namesAt(app, missing)
        if (found.isNotEmpty()) _placeStreets.update { it + found }
        return _placeStreets.value
    }

    private fun placeLookupFixes(records: List<PlaceMemory.Record>, now: Long): List<PlaceLookup.Fix> {
        val out = ArrayList<PlaceLookup.Fix>()
        val ranked = records.sortedByDescending { PlaceMemory.placeCount(it, now) }
        for (record in ranked) {
            val places = PlaceMemory.placesInWindow(record, now)
            if (places.size < PlaceMemory.MIN_PLACES) continue
            for (place in places) {
                out += PlaceLookup.Fix("Place", place.lat, place.lon, namedOnly = true)
            }
        }
        return out
    }

    fun setRadioMine(device: Sighting, on: Boolean) {
        viewModelScope.launch {
            val suggest = RadioBookmarks.suggestLabel(device, device.fleetIds.map { fleetName(it) })
            app.config.update { cfg ->
                cfg.copy(watchlist = RadioBookmarks.setMine(cfg.watchlist, device.key, on, suggest))
            }
        }
    }

    fun setNamedRadioMine(id: String, on: Boolean) {
        viewModelScope.launch {
            app.config.update { cfg ->
                cfg.copy(watchlist = RadioBookmarks.setMineOnRow(cfg.watchlist, id, on))
            }
        }
    }

    fun renameRadioBookmark(id: String, name: String) {
        viewModelScope.launch {
            app.config.update { cfg ->
                cfg.copy(watchlist = RadioBookmarks.rename(cfg.watchlist, id, name))
            }
        }
    }

    fun updateNamedRadio(id: String, name: String, notes: String) {
        viewModelScope.launch {
            app.config.update { cfg ->
                cfg.copy(watchlist = RadioBookmarks.updateNamedRadio(cfg.watchlist, id, name, notes))
            }
        }
    }

    fun removeRadioBookmark(id: String) {
        viewModelScope.launch {
            app.config.update { cfg ->
                cfg.copy(watchlist = RadioBookmarks.remove(cfg.watchlist, id))
            }
        }
    }

    fun clearRadioBookmarks() {
        viewModelScope.launch {
            app.config.update { cfg ->
                cfg.copy(watchlist = RadioBookmarks.withoutRadios(cfg.watchlist))
            }
        }
    }

    fun watchLabelFor(deviceKey: String): String? =
        app.config.watchlist.firstOrNull { it.deviceKey == deviceKey }?.label?.trim()?.takeIf { it.isNotEmpty() }

    fun watchObserverNoteFor(deviceKey: String): String? =
        app.config.watchlist.firstOrNull { it.deviceKey == deviceKey }?.observerNotes?.trim()?.takeIf { it.isNotEmpty() }

    fun saveRadioNotes(device: Sighting, notes: String) {
        viewModelScope.launch {
            val suggest = RadioBookmarks.suggestLabel(device, device.fleetIds.map { fleetName(it) })
            app.config.update { cfg ->
                cfg.copy(
                    watchlist = RadioBookmarks.upsertNotes(cfg.watchlist, device.key, notes, suggest),
                )
            }
        }
    }

    fun toggleWatchFleet(fleet: Fleet) {
        viewModelScope.launch {
            app.config.update { cfg ->
                val exists = cfg.watchlist.any { it.fleetId == fleet.id }
                val next = if (exists) {
                    cfg.watchlist.filterNot { it.fleetId == fleet.id }
                } else {
                    cfg.watchlist + WatchTarget(
                        id = UUID.randomUUID().toString(),
                        fleetId = fleet.id,
                        label = fleet.name,
                    )
                }
                cfg.copy(watchlist = next)
            }
        }
    }

    fun restoreDefaults() {
        viewModelScope.launch {
            app.config.restoreDefaults()
            app.syncLocationUpdates()
            app.syncArrivals(false)
            app.devices.refresh(
                app.config.fleets,
                app.config.settings.staleSec,
                policy = app.config.settings.detectionPolicy(),
                decaySec = app.config.settings.decaySec,
            )
        }
    }

    fun suggestedSignaturesName(): String {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
            .format(java.util.Date())
        return "fieldwatch-signatures-$stamp.json"
    }

    private fun signaturePackJson(): String {
        val cfg = app.config.config.value
        return SignatureExchange.encode(
            SignatureExchange.pack(
                fleets = cfg.fleets,
                catalogVersion = cfg.version,
                appVersion = BuildConfig.VERSION_NAME,
                exportedAt = java.time.Instant.now().toString(),
            ),
        )
    }

    fun startSignatureShare() {
        viewModelScope.launch {
            runCatching {
                val json = withContext(Dispatchers.Default) { signaturePackJson() }
                val dir = File(app.cacheDir, "signatures").apply { mkdirs() }
                val file = File(dir, suggestedSignaturesName())
                withContext(Dispatchers.IO) { file.writeText(json) }
                val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    clipData = ClipData.newRawUri("signatures", uri)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, LanguageText.text("ui.fieldwatch.signatures"))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }.onSuccess { intent ->
                _export.value = ExportUi(share = intent, shareTitle = LanguageText.text("ui.fieldwatch.signatures"))
            }.onFailure { err ->
                _export.value = ExportUi(
                    error = err.message ?: LanguageText.text("ui.could.not.export.signatures"),
                    errorTitle = LanguageText.text("ui.could.not.export.signatures"),
                )
            }
        }
    }

    fun saveSignaturesToUri(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val json = withContext(Dispatchers.Default) { signaturePackJson() }
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error(LanguageText.text("ui.could.not.write.location"))
                }
            }.onSuccess {
                _export.value = ExportUi(
                    noticeTitle = LanguageText.text("ui.signatures.saved"),
                    noticeMessage = LanguageText.text("ui.pack.was.written.folder"),
                )
            }.onFailure { err ->
                _export.value = ExportUi(
                    error = err.message ?: LanguageText.text("ui.could.not.save.signatures"),
                    errorTitle = LanguageText.text("ui.could.not.save.signatures"),
                )
            }
        }
    }

    fun updateStockCatalogFromGitHub() {
        if (_export.value.active) return
        viewModelScope.launch {
            if (!PlaceLookup.online(app)) {
                _export.value = ExportUi(
                    errorTitle = LanguageText.text("ui.no.internet"),
                    error = LanguageText.text("ui.no.internet.use.import"),
                )
                return@launch
            }
            _export.value = ExportUi(
                active = true,
                spinner = true,
                message = LanguageText.text("ui.updating.stock.catalog"),
            )
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    CatalogRemote.fetch(
                        userAgent = "Fieldwatch/${BuildConfig.VERSION_NAME}",
                    )
                }
                val parsed = SignatureExchange.parsePack(text)
                val result = app.config.overlayStockCatalog(parsed.pack)
                    .copy(skippedDecode = parsed.skippedDecode)
                result.error?.let { throw IllegalStateException(it) }
                if (!result.alreadyLatest) {
                    app.devices.refresh(
                        app.config.fleets,
                        app.config.settings.staleSec,
                        policy = app.config.settings.detectionPolicy(),
                        decaySec = app.config.settings.decaySec,
                    )
                }
                result
            }.onSuccess { result ->
                _export.value = if (result.alreadyLatest) {
                    ExportUi(
                        noticeTitle = LanguageText.text("ui.already.latest.catalog"),
                        noticeMessage = "Already on catalog ${result.catalogVersion}. Nothing to update.",
                    )
                } else {
                    val bits = mutableListOf<String>()
                    if (result.updated > 0) bits += "updated ${result.updated}"
                    if (result.added > 0) bits += "added ${result.added}"
                    val change = if (bits.isEmpty()) LanguageText.text("ui.no.stock.rows.changed")
                    else bits.joinToString(" · ").replaceFirstChar { it.uppercase() } + "."
                    val skip = result.skippedDecode > 0
                    ExportUi(
                        noticeTitle = LanguageText.text("ui.catalog.updated"),
                        noticeMessage = "Stock catalog is now ${result.catalogVersion}. $change " +
                            LanguageText.text("ui.bookmarks.settings.were.not"),
                        followUpTitle = if (skip) LanguageText.text("ui.signature.decoding.skipped") else null,
                        followUpMessage = if (skip) {
                            (LanguageText.text("ui.some.signature.field.maps") + " ") +
                                LanguageText.text("ui.signatures.still.match.install")
                        } else {
                            null
                        },
                    )
                }
            }.onFailure { err ->
                val msg = err.message.orEmpty()
                val access = msg.contains("HTTP", ignoreCase = true) ||
                    msg.contains("Unable to resolve", ignoreCase = true) ||
                    msg.contains("failed to connect", ignoreCase = true) ||
                    msg.contains("timeout", ignoreCase = true) ||
                    msg.contains("GitHub", ignoreCase = true)
                _export.value = if (access) {
                    ExportUi(
                        errorTitle = LanguageText.text("ui.could.not.reach.github"),
                        error = LanguageText.text("ui.could.not.reach.catalog"),
                    )
                } else {
                    ExportUi(
                        errorTitle = LanguageText.text("ui.could.not.import.catalog"),
                        error = err.message ?: LanguageText.text("ui.could.not.import.catalog.2"),
                    )
                }
            }
        }
    }

    fun importSignaturesFromUri(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    } ?: error(LanguageText.text("ui.could.not.read.file"))
                }
                val pack = SignatureExchange.parse(text)
                val result = app.config.importFleets(pack.fleets)
                if (result.error != null) error(result.error)
                app.devices.refresh(
                    app.config.fleets,
                    app.config.settings.staleSec,
                    policy = app.config.settings.detectionPolicy(),
                    decaySec = app.config.settings.decaySec,
                )
                result.summary()
            }.onSuccess { summary ->
                _export.value = ExportUi(
                    noticeTitle = LanguageText.text("ui.signatures.imported"),
                    noticeMessage = summary,
                )
            }.onFailure { err ->
                _export.value = ExportUi(
                    error = err.message ?: LanguageText.text("ui.could.not.import.signatures"),
                    errorTitle = LanguageText.text("ui.could.not.import.signatures"),
                )
            }
        }
    }

    fun suggestedSettingsName(): String {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
            .format(java.util.Date())
        return "fieldwatch-settings-$stamp.json"
    }

    private fun settingsPackJson(): String {
        val cfg = app.config.config.value
        return SettingsExchange.encode(
            SettingsExchange.pack(
                settings = cfg.settings,
                filter = cfg.filter,
                presets = cfg.presets,
                watchlist = cfg.watchlist,
                hiddenPresetIds = cfg.hiddenPresetIds,
                appVersion = BuildConfig.VERSION_NAME,
                exportedAt = java.time.Instant.now().toString(),
            ),
        )
    }

    fun startSettingsShare() {
        viewModelScope.launch {
            runCatching {
                val json = withContext(Dispatchers.Default) { settingsPackJson() }
                val dir = File(app.cacheDir, "settings").apply { mkdirs() }
                val file = File(dir, suggestedSettingsName())
                withContext(Dispatchers.IO) { file.writeText(json) }
                val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    clipData = ClipData.newRawUri("settings", uri)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, LanguageText.text("ui.fieldwatch.settings"))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }.onSuccess { intent ->
                _export.value = ExportUi(share = intent, shareTitle = LanguageText.text("ui.fieldwatch.settings"))
            }.onFailure { err ->
                _export.value = ExportUi(
                    error = err.message ?: LanguageText.text("ui.could.not.export.settings"),
                    errorTitle = LanguageText.text("ui.could.not.export.settings"),
                )
            }
        }
    }

    fun saveSettingsToUri(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val json = withContext(Dispatchers.Default) { settingsPackJson() }
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error(LanguageText.text("ui.could.not.write.location"))
                }
            }.onSuccess {
                _export.value = ExportUi(
                    noticeTitle = LanguageText.text("ui.settings.saved"),
                    noticeMessage = LanguageText.text("ui.pack.was.written.folder.2"),
                )
            }.onFailure { err ->
                _export.value = ExportUi(
                    error = err.message ?: LanguageText.text("ui.could.not.save.settings"),
                    errorTitle = LanguageText.text("ui.could.not.save.settings"),
                )
            }
        }
    }

    fun importSettingsFromUri(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    } ?: error(LanguageText.text("ui.could.not.read.file"))
                }
                val pack = SettingsExchange.parse(text)
                val warning = SettingsExchange.takImportWarning(app.config.settings, pack.settings)
                if (warning != null) {
                    _pendingSettingsImport.value = PendingSettingsImport(pack, warning)
                } else {
                    finishSettingsImport(pack)
                }
            }.onFailure { err ->
                _export.value = ExportUi(
                    error = err.message ?: LanguageText.text("ui.could.not.import.settings"),
                    errorTitle = LanguageText.text("ui.could.not.import.settings"),
                )
            }
        }
    }

    fun confirmPendingSettingsImport() {
        val pending = _pendingSettingsImport.value ?: return
        _pendingSettingsImport.value = null
        viewModelScope.launch {
            runCatching { finishSettingsImport(pending.pack) }.onFailure { err ->
                _export.value = ExportUi(
                    error = err.message ?: LanguageText.text("ui.could.not.import.settings"),
                    errorTitle = LanguageText.text("ui.could.not.import.settings"),
                )
            }
        }
    }

    fun dismissPendingSettingsImport() {
        _pendingSettingsImport.value = null
    }

    fun chooseLanguage(choice: String) {
        viewModelScope.launch {
            val snap = withContext(Dispatchers.IO) {
                app.language.choose(choice, java.util.Locale.getDefault())
            }
            publishLanguage(snap)
        }
    }

    fun importLanguageFromUri(uri: Uri) {
        viewModelScope.launch {
            val parsed = withContext(Dispatchers.IO) {
                val read = app.contentResolver.openInputStream(uri)?.use {
                    LanguageFiles.readCapped(it)
                }
                when (read) {
                    null -> LanguageParse.Reject(LanguageErrors.NOT_PACK)
                    LanguageRead.Empty -> LanguageParse.Reject(LanguageErrors.EMPTY)
                    LanguageRead.TooBig -> LanguageParse.Reject(LanguageErrors.LARGE)
                    is LanguageRead.Bytes -> LanguagePack.parse(read.bytes)
                }
            }
            when (parsed) {
                is LanguageParse.Reject ->
                    _language.value = _language.value.copy(pending = null, errorKey = parsed.messageKey)
                is LanguageParse.Ok ->
                    _language.value = _language.value.copy(pending = parsed.preview, errorKey = null)
            }
        }
    }

    fun confirmLanguageImport() {
        val pending = _language.value.pending ?: return
        viewModelScope.launch {
            val snap = withContext(Dispatchers.IO) {
                app.language.confirm(pending, java.util.Locale.getDefault())
            }
            publishLanguage(snap)
        }
    }

    fun cancelLanguageImport() {
        _language.value = _language.value.copy(pending = null)
    }

    fun dismissLanguageError() {
        _language.value = _language.value.copy(errorKey = null)
    }

    fun removeLanguagePack() {
        viewModelScope.launch {
            val snap = withContext(Dispatchers.IO) {
                app.language.remove(java.util.Locale.getDefault())
            }
            publishLanguage(snap)
        }
    }

    fun dismissLanguageDoor() {
        viewModelScope.launch {
            val snap = withContext(Dispatchers.IO) {
                app.language.dismissDoor(java.util.Locale.getDefault())
            }
            publishLanguage(snap, _language.value.pending, _language.value.errorKey)
        }
    }

    fun dismissLanguageAi() {
        val tag = _language.value.snap.activeTag ?: return
        viewModelScope.launch {
            val snap = withContext(Dispatchers.IO) {
                app.language.markAiSeen(tag, java.util.Locale.getDefault())
            }
            publishLanguage(snap, _language.value.pending, _language.value.errorKey)
        }
    }

    fun startLanguageTemplateShare() {
        viewModelScope.launch {
            runCatching {
                val json = withContext(Dispatchers.Default) {
                    LanguagePack.template(BuildConfig.VERSION_NAME)
                }
                val dir = java.io.File(app.cacheDir, "language").apply { mkdirs() }
                val file = java.io.File(dir, "fieldwatch-language-en.json")
                withContext(Dispatchers.IO) { file.writeText(json) }
                val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    clipData = ClipData.newRawUri("language", uri)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, LanguageExportCopy.SHARE)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }.onSuccess { intent ->
                _export.value = ExportUi(share = intent, shareTitle = LanguageExportCopy.SHARE)
            }.onFailure { err ->
                _export.value = ExportUi(
                    error = err.message ?: LanguageExportCopy.FAILED,
                    errorTitle = LanguageExportCopy.FAILED,
                )
            }
        }
    }

    private fun publishLanguage(
        snap: app.fieldwatch.data.LanguageSnapshot,
        pending: app.fieldwatch.domain.LanguagePreview? = null,
        errorKey: String? = null,
    ) {
        LanguageText.apply(snap.overlay)
        _language.value = LanguageUiState(snap, pending, errorKey)
    }

    private suspend fun finishSettingsImport(pack: SettingsPack) {
        val prev = app.config.settings
        val result = app.config.importSettings(pack)
        if (result.error != null) error(result.error)
        if (app.gnssProfile.profile.value == null && app.config.settings.gnssMonitor) {
            app.config.update { it.copy(settings = it.settings.copy(gnssMonitor = false)) }
        }
        val next = app.config.settings
        app.logs.configure(next.logFormat, next.logRotateKb, next.loggingEnabled)
        if (prev.tagLocation != next.tagLocation) app.syncLocationUpdates()
        if (prev.gnssSettings() != next.gnssSettings() || prev.tagLocation != next.tagLocation) {
            app.syncGnss()
        }
        if (prev.intensity != next.intensity && app.devices.stats.value.scanning) {
            app.startScanning()
        }
        app.devices.refresh(
            app.config.fleets,
            next.staleSec,
            policy = next.detectionPolicy(),
            decaySec = next.decaySec,
        )
        if (next.alertVoice || next.gnssVoice) app.alerter.prepareVoice()
        _export.value = ExportUi(
            noticeTitle = LanguageText.text("ui.settings.imported"),
            noticeMessage = result.summary(),
        )
    }

    fun setLogExportKind(kind: LogExportKind) {
        if (kind.sitOnly) return
        _logExportKind.value = kind
    }

    fun setLogExportRadios(radios: LogExportRadios) {
        _logExportRadios.value = radios
    }

    fun setSitExportKind(kind: LogExportKind) {
        _sitExportKind.value = kind
    }

    fun setSitExportRadios(radios: LogExportRadios) {
        _sitExportRadios.value = radios
    }

    private suspend fun pathTilesForFigure(figure: SitPathPlot.Figure?): List<PathTiles.Tile> {
        if (figure == null || !figure.drawable) return emptyList()
        val phone = figure.tracks.filter { !it.aircraft }.flatMap { it.samples }
        val craft = figure.tracks.filter { it.aircraft }.flatMap { it.samples }
        val samples = Geo.despikePath(phone).let { if (it.size >= 2) it else phone } + craft
        if (samples.isEmpty()) return emptyList()
        val settings = app.config.settings
        return PathTiles.load(
            app, samples,
            onlineLookup = settings.onlineLookup,
            forPrint = true,
        )
    }

    fun refreshSitPath() {
        viewModelScope.launch(Dispatchers.Default) {
            val model = buildSitPath()
            _sitPath.value = model
            val settings = app.config.settings
            if (!settings.onlineLookup) {
                _pathTiles.value = emptyList()
                _pathAircraftTiles.value = emptyList()
                return@launch
            }
            val phone = Geo.despikePath(model.samples).let { if (it.size >= 2) it else model.samples }
            val walk = phone + model.craft.flatMap { it.samples }
            _pathTiles.value = if (walk.isNotEmpty()) {
                runCatching {
                    PathTiles.load(
                        app, walk,
                        onlineLookup = settings.onlineLookup,
                    )
                }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            _pathAircraftTiles.value = model.aircraftCards.map { card ->
                val samples = card.craft.flatMap { it.samples }
                if (samples.isEmpty()) {
                    emptyList()
                } else {
                    runCatching {
                        PathTiles.load(
                            app, samples,
                            onlineLookup = settings.onlineLookup,
                        )
                    }.getOrDefault(emptyList())
                }
            }
        }
    }

    private fun buildSitPath(): SitPathPlot.Model {
        val now = System.currentTimeMillis()
        val source = app.sits.debriefSource(now)
        val customNames = RadioBookmarks.labels(app.config.watchlist)
        val namedKeys = customNames.keys
        val bookmarkedKeys = RadioBookmarks.alertDeviceKeys(app.config.watchlist)
        val watchedFleets = RadioBookmarks.watchedFleetIds(app.config.watchlist)
        val fleets = app.config.fleets
        val tagging = app.config.settings.tagLocation
        if (source != null) {
            val raw = source.operatorPath
            val samples = Geo.despikePath(raw)
            val plot = SitPathPlot.dotsFrom(
                source.devices, fleets, namedKeys, customNames = customNames,
                observerNotes = RadioBookmarks.notes(app.config.watchlist),
                bookmarkedKeys = bookmarkedKeys,
                watchedFleetIds = watchedFleets,
                alertsOnly = true,
                mineKeys = RadioBookmarks.mineKeys(app.config.watchlist),
            )
            val empty = when {
                !tagging -> LanguageText.text("ui.turn.tag.detections.gps.2")
                samples.isEmpty() -> LanguageText.text("ui.walk.path.needs.gps")
                else -> null
            }
            pathRadios = source.devices
            val pictures = AircraftTrail.pictures(
                source.devices.mapNotNull { device ->
                    AircraftTrail.source(
                        device,
                        device.payloadUasId?.trim().orEmpty().ifBlank { device.reportName(customNames) },
                    )
                },
                if (empty == null) raw else emptyList(),
            )
            return AircraftTrail.overlay(
                SitPathPlot.Model(
                    samples = samples,
                    dots = if (samples.isNotEmpty()) plot.points else emptyList(),
                    lengthM = Geo.pathLengthM(samples),
                    spanM = Geo.spanM(samples),
                    title = source.name,
                    emptyHint = empty,
                    live = app.sits.ui.value.open != null,
                    gnss = if (samples.isNotEmpty()) SitPathPlot.gnssPins(source.gnss, samples) else emptyList(),
                ),
                pictures,
            )
        }
        val start = now - DebriefPrompt.WINDOW_MS
        val samples = Geo.despikePath(app.operatorPathCopy().filter { it.at >= start })
        val devices = app.devices.devices.value.filter { it.lastSeen >= start || it.firstSeen >= start }
        val empty = when {
            !tagging -> LanguageText.text("ui.turn.tag.detections.gps.2")
            samples.isEmpty() -> LanguageText.text("ui.last.15.minutes.walk")
            else -> null
        }
        pathRadios = devices
        val plot = if (samples.isNotEmpty()) {
            SitPathPlot.dotsFrom(
                devices, fleets, namedKeys, customNames = customNames,
                observerNotes = RadioBookmarks.notes(app.config.watchlist),
                bookmarkedKeys = bookmarkedKeys,
                watchedFleetIds = watchedFleets,
                alertsOnly = true,
                mineKeys = RadioBookmarks.mineKeys(app.config.watchlist),
            )
        } else {
            SitPathPlot.PlotRadios(emptyList())
        }
        val windowPath = app.operatorPathCopy().filter { it.at >= start }
        val gnssMarks = app.gnss.marks().filter { it.at >= start || (it.endedAt > 0L && it.endedAt >= start) }
        return AircraftTrail.overlay(
            SitPathPlot.Model(
                samples = samples,
                dots = plot.points,
                lengthM = Geo.pathLengthM(samples),
                spanM = Geo.spanM(samples),
                title = LanguageText.text("ui.last.15.minutes"),
                emptyHint = empty,
                live = true,
                gnss = if (samples.isNotEmpty()) SitPathPlot.gnssPins(gnssMarks, samples) else emptyList(),
            ),
            AircraftTrail.pictures(
                devices.mapNotNull { device ->
                    AircraftTrail.source(
                        device,
                        device.payloadUasId?.trim().orEmpty().ifBlank { device.reportName(customNames) },
                    )
                },
                if (empty == null) windowPath else emptyList(),
            ),
        )
    }

    fun openPathRadio(key: String): Boolean {
        val device = pathRadios.firstOrNull { it.key == key } ?: return false
        select(device)
        return true
    }

    fun suggestedExportName(): String {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        return when (val kind = _logExportKind.value) {
            LogExportKind.LOG_CSV -> app.logs.suggestedExportName(asJsonl = false)
            LogExportKind.LOG_JSONL -> app.logs.suggestedExportName(asJsonl = true)
            LogExportKind.GPX, LogExportKind.KML, LogExportKind.WIGLE ->
                GeoExport.suggestedName(GeoExport.formatOf(kind)!!, stamp)
            LogExportKind.GEOJSON, LogExportKind.ATAK ->
                error(LanguageText.text("ui.format.sit.export"))
        }
    }

    fun exportMime(): String = when (_logExportKind.value) {
        LogExportKind.LOG_CSV -> app.logs.exportMime(asJsonl = false)
        LogExportKind.LOG_JSONL -> app.logs.exportMime(asJsonl = true)
        LogExportKind.GPX, LogExportKind.KML, LogExportKind.WIGLE ->
            GeoExport.formatOf(_logExportKind.value)!!.mime
        LogExportKind.GEOJSON, LogExportKind.ATAK ->
            error(LanguageText.text("ui.format.sit.export"))
    }

    fun startFieldDebriefPdf() {
        if (_export.value.active) return
        viewModelScope.launch {
            publishExport(0.05f, LanguageText.text("ui.writing.debrief.pdf"))
            runCatching {
                val doc = fieldDebriefDoc()
                publishExport(0.4f, LanguageText.text("ui.laying.out.debrief.pdf"))
                val dir = File(app.cacheDir, "debrief").apply { mkdirs() }
                val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
                    .format(java.util.Date())
                val file = File(dir, "fieldwatch-debrief-$stamp.pdf")
                publishExport(0.32f, LanguageText.text("ui.loading.map.tiles"))
                val tiles = pathTilesForFigure(doc.pathFigure)
                val extraTiles = doc.extraFigures.map { pathTilesForFigure(it) }
                withContext(Dispatchers.Default) {
                    DebriefPdf.write(doc, file, tiles, extraTiles) { p ->
                        kotlinx.coroutines.runBlocking {
                            publishExport(0.4f + 0.55f * p, LanguageText.text("ui.writing.debrief.pdf"))
                        }
                    }
                }
                val uri: Uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    clipData = ClipData.newRawUri("debrief", uri)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, debriefSubject(doc))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }.onSuccess { intent ->
                _export.value = ExportUi(
                    active = false,
                    progress = 1f,
                    share = intent,
                    shareTitle = LanguageText.text("ui.debrief.pdf"),
                )
            }.onFailure { err ->
                _export.value = ExportUi(error = err.message ?: LanguageText.text("ui.could.not.write.debrief"))
            }
        }
    }

    fun startFieldDebrief() {
        if (_export.value.active) return
        viewModelScope.launch {
            publishExport(0.08f, LanguageText.text("ui.writing.debrief"))
            runCatching {
                val doc = fieldDebriefDoc()
                publishExport(0.9f, LanguageText.text("ui.writing.debrief"))
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, debriefSubject(doc))
                    putExtra(Intent.EXTRA_TEXT, doc.toPlainText())
                }
            }.onSuccess { intent ->
                _export.value = ExportUi(
                    active = false,
                    progress = 1f,
                    share = intent,
                    shareTitle = "Debrief",
                )
            }.onFailure { err ->
                _export.value = ExportUi(error = err.message ?: LanguageText.text("ui.could.not.write.debrief.2"))
            }
        }
    }

    private fun debriefSubject(doc: DebriefDoc): String =
        if (doc.heading.startsWith("FIELDWATCH SIT")) doc.heading else LanguageText.text("ui.fieldwatch.field.debrief.last")

    private suspend fun fieldDebriefDoc(): DebriefDoc {
        publishExport(0.08f, LanguageText.text("ui.gathering.sit"))
        val settings = app.config.settings
        val fleets = app.config.fleets
        val now = System.currentTimeMillis()
        val source = app.sits.debriefSource(now)
        val devices = source?.devices ?: app.devices.devices.value
        val path = source?.operatorPath ?: app.operatorPathCopy()
        val window = source?.let { DebriefWindow(it.startAt, it.endAt, it.name) }
        val places = if (settings.demoMode) {
            DebriefPlaces.Off
        } else if (settings.onlineLookup) {
            publishExport(0.14f, LanguageText.text("ui.looking.up.place.names"))
            val found = PlaceLookup.lookup(
                app, path, devices, now,
                extra = placeLookupFixes(
                    if (settings.placeWatch) app.places.snapshot() else emptyList(),
                    window?.endAt ?: now,
                ),
                onProgress = { msg ->
                    kotlinx.coroutines.runBlocking { publishExport(0.18f, msg) }
                },
            )
            found
        } else {
            DebriefPlaces.Off
        }
        publishExport(0.32f, LanguageText.text("ui.building.debrief"))
        return withContext(Dispatchers.Default) {
            DebriefReport.document(
                devices = devices,
                fleets = fleets,
                settings = settings,
                operatorPath = path,
                now = now,
                places = places,
                window = window,
                customNames = RadioBookmarks.labels(app.config.watchlist),
                observerNotes = RadioBookmarks.notes(app.config.watchlist),
                bookmarkedKeys = RadioBookmarks.alertDeviceKeys(app.config.watchlist),
                watchedFleetIds = RadioBookmarks.watchedFleetIds(app.config.watchlist),
                mineKeys = RadioBookmarks.mineKeys(app.config.watchlist),
                floods = source?.floods ?: app.floodBursts(),
                gnss = source?.gnss ?: app.gnss.marks(),
                placeRecords = if (settings.placeWatch) app.places.snapshot() else emptyList(),
            ).withDemoMacs(
                devices.map { it.mac },
                settings.demoMode,
                devices.mapNotNull { it.payloadUasId },
            )
        }
    }

    fun startAiExport() {
        if (_export.value.active) return
        viewModelScope.launch {
            publishExport(0.06f, LanguageText.text("ui.building.ai.export.prompt"))
            runCatching {
                val settings = app.config.settings
                val fleets = app.config.fleets
                val now = System.currentTimeMillis()
                val source = app.sits.debriefSource(now)
                val devices = source?.devices ?: app.devices.devices.value
                val path = source?.operatorPath ?: app.operatorPathCopy()
                val window = source?.let { DebriefWindow(it.startAt, it.endAt, it.name) }
                val places = if (settings.demoMode) {
                    DebriefPlaces.Off
                } else if (settings.onlineLookup) {
                    publishExport(0.12f, LanguageText.text("ui.looking.up.place.names"))
                    val found = PlaceLookup.lookup(
                        app, path, devices, now,
                        extra = placeLookupFixes(
                            if (settings.placeWatch) app.places.snapshot() else emptyList(),
                            window?.endAt ?: now,
                        ),
                    ) { msg ->
                        kotlinx.coroutines.runBlocking { publishExport(0.16f, msg) }
                    }
                    publishExport(0.35f, LanguageText.text("ui.building.ai.export.prompt"))
                    found
                } else {
                    DebriefPlaces.Off
                }
                publishExport(0.4f, LanguageText.text("ui.building.ai.export.prompt"))
                val text = withContext(Dispatchers.Default) {
                    val raw = DebriefPrompt.build(
                        devices = devices,
                        fleets = fleets,
                        settings = settings,
                        now = now,
                        operatorPath = path,
                        places = places,
                        window = window,
                        customNames = RadioBookmarks.labels(app.config.watchlist),
                        observerNotes = RadioBookmarks.notes(app.config.watchlist),
                        bookmarkedKeys = RadioBookmarks.alertDeviceKeys(app.config.watchlist),
                        mineKeys = RadioBookmarks.mineKeys(app.config.watchlist),
                        floods = source?.floods ?: app.floodBursts(),
                        gnss = source?.gnss ?: app.gnss.marks(),
                        placeRecords = if (settings.placeWatch) app.places.snapshot() else emptyList(),
                    )
                    val masked = MacUtil.redactPrivateText(
                        raw,
                        devices.map { it.mac },
                        devices.mapNotNull { it.payloadUasId },
                        settings.demoMode,
                    )
                    if (settings.demoMode) {
                        "Privacy mode: MAC tails are **:**:**. UAS ids and GPS coordinates are masked. Logs on the phone are unchanged.\n\n$masked"
                    } else {
                        masked
                    }
                }
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                        Intent.EXTRA_SUBJECT,
                        if (window != null) "Fieldwatch AI export — sit ${window.sitName}"
                        else LanguageText.text("ui.fieldwatch.ai.export.last"),
                    )
                    putExtra(Intent.EXTRA_TEXT, text)
                }
            }.onSuccess { intent ->
                _export.value = ExportUi(
                    active = false,
                    progress = 1f,
                    share = intent,
                    shareTitle = "AI Export",
                )
            }.onFailure { err ->
                _export.value = ExportUi(error = err.message ?: LanguageText.text("ui.could.not.build.ai"))
            }
        }
    }

    fun startDeviceDetailAiExport(device: Sighting) {
        if (_export.value.active) return
        viewModelScope.launch {
            publishExport(0.08f, LanguageText.text("ui.building.ai.export.prompt"))
            runCatching {
                val settings = app.config.settings
                val names = device.fleetIds.map { fleetName(it) }
                val attention = attentionNotesFor(device)
                val notes = signatureNotesFor(device)
                val places = if (settings.demoMode) {
                    DebriefPlaces.Off
                } else if (settings.onlineLookup && (settings.tagLocation || device.latitude != null || settings.placeWatch)) {
                    publishExport(0.15f, LanguageText.text("ui.looking.up.place.names"))
                    val found = PlaceLookup.lookup(
                        app,
                        app.operatorPathCopy(),
                        listOf(device),
                        System.currentTimeMillis(),
                        extra = placeLookupFixes(
                            if (settings.placeWatch) app.places.snapshot() else emptyList(),
                            System.currentTimeMillis(),
                        ),
                    ) { msg ->
                        kotlinx.coroutines.runBlocking { publishExport(0.2f, msg) }
                    }
                    if (found.namesByCell.isNotEmpty()) _placeStreets.update { it + found.namesByCell }
                    publishExport(0.4f, LanguageText.text("ui.building.ai.export.prompt"))
                    found
                } else {
                    DebriefPlaces.Off
                }
                publishExport(0.45f, LanguageText.text("ui.building.ai.export.prompt"))
                val text = withContext(Dispatchers.Default) {
                    val raw = DeviceDetailPrompt.build(
                        device, names, settings, places, attentionNotes = attention,
                        signatureNotes = notes,
                        fleets = app.config.fleets,
                        mine = isMine(device.key),
                        placeLines = placeLines(device, places.namesByCell),
                    )
                    val masked = MacUtil.redactPrivateText(
                        raw,
                        listOf(device.mac),
                        listOfNotNull(device.payloadUasId),
                        settings.demoMode,
                    )
                    if (settings.demoMode) {
                        "Privacy mode: MAC tails are **:**:**. UAS ids and GPS coordinates are masked. Logs on the phone are unchanged.\n\n$masked"
                    } else {
                        masked
                    }
                }
                val title = MacUtil.redactPrivateText(
                    device.listTitle(names),
                    listOf(device.mac),
                    listOfNotNull(device.payloadUasId),
                    settings.demoMode,
                )
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Fieldwatch AI export — $title")
                    putExtra(Intent.EXTRA_TEXT, text)
                }
            }.onSuccess { intent ->
                _export.value = ExportUi(
                    active = false,
                    progress = 1f,
                    share = intent,
                    shareTitle = "AI Export",
                )
            }.onFailure { err ->
                _export.value = ExportUi(error = err.message ?: LanguageText.text("ui.could.not.build.ai"))
            }
        }
    }

    fun startDeviceDetailShare(device: Sighting) {
        if (_export.value.active) return
        viewModelScope.launch {
            runCatching {
                val settings = app.config.settings
                val names = device.fleetIds.map { fleetName(it) }
                val attention = attentionNotesFor(device)
                val notes = signatureNotesFor(device)
                val streets = streetsForDevice(device)
                val text = withContext(Dispatchers.Default) {
                    val raw = DeviceDetailText.build(
                        device, names, attentionNotes = attention, signatureNotes = notes,
                        fleets = app.config.fleets,
                        mine = isMine(device.key),
                        placeLines = placeLines(device, streets),
                    )
                    val masked = MacUtil.redactPrivateText(
                        raw,
                        listOf(device.mac),
                        listOfNotNull(device.payloadUasId),
                        settings.demoMode,
                    )
                    if (settings.demoMode) {
                        "Privacy mode: MAC tails are **:**:**. UAS ids and GPS coordinates are masked. Logs on the phone are unchanged.\n\n$masked"
                    } else {
                        masked
                    }
                }
                val title = MacUtil.redactPrivateText(
                    device.listTitle(names),
                    listOf(device.mac),
                    listOfNotNull(device.payloadUasId),
                    settings.demoMode,
                )
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Fieldwatch device detail — $title")
                    putExtra(Intent.EXTRA_TEXT, text)
                }
            }.onSuccess { intent ->
                _export.value = ExportUi(share = intent, shareTitle = LanguageText.text("ui.device.detail"))
            }.onFailure { err ->
                _export.value = ExportUi(error = err.message ?: LanguageText.text("ui.could.not.share.device"))
            }
        }
    }

    fun suggestedSitExportName(): String {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        return SitExport.suggestedName(_sitExportKind.value, sitExportWindow().name, stamp)
    }

    fun sitExportMime(): String = SitExport.mime(_sitExportKind.value)

    fun startSitExport() {
        if (_export.value.active) return
        viewModelScope.launch {
            runExport(LanguageText.text("ui.preparing.sit.export")) {
                val kind = _sitExportKind.value
                val (file, sitName) = writeSitExport(kind)
                val uri: Uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
                Intent(Intent.ACTION_SEND).apply {
                    type = SitExport.mime(kind)
                    clipData = ClipData.newRawUri("sit-export", uri)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, SitExport.subject(kind, sitName))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }.onSuccess { intent ->
                _export.value = ExportUi(active = false, progress = 1f, share = intent, shareTitle = LanguageText.text("reports.sit.export"))
            }
        }
    }

    fun startSitSaveToUri(uri: Uri) {
        if (_export.value.active) return
        viewModelScope.launch {
            runExport(LanguageText.text("ui.saving.sit.export")) {
                val kind = _sitExportKind.value
                val bytes = sitExportBytes(kind)
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: error(LanguageText.text("ui.could.not.open.location"))
                }
            }.onSuccess {
                _export.value = ExportUi(active = false, progress = 1f, saved = true)
            }
        }
    }

    private data class SitExportWindow(
        val name: String,
        val devices: List<Sighting>,
        val path: List<GpsSample>,
        val gnss: List<GnssMark>,
    )

    private fun sitExportWindow(now: Long = System.currentTimeMillis()): SitExportWindow {
        val source = app.sits.debriefSource(now)
        if (source != null) {
            return SitExportWindow(source.name, source.devices, source.operatorPath, source.gnss)
        }
        val start = now - DebriefPrompt.WINDOW_MS
        val marks = app.gnss.marks().filter { it.at >= start || (it.endedAt > 0L && it.endedAt >= start) }
        return SitExportWindow(
            LanguageText.text("ui.last.15.minutes"),
            app.devices.devices.value.filter { it.lastSeen >= start || it.firstSeen >= start },
            app.operatorPathCopy().filter { it.at >= start },
            marks,
        )
    }

    private suspend fun writeSitExport(kind: LogExportKind): Pair<File, String> {
        val bytes = sitExportBytes(kind)
        val win = sitExportWindow()
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        val dir = File(app.cacheDir, "export").apply { mkdirs() }
        val file = File(dir, SitExport.suggestedName(kind, win.name, stamp))
        withContext(Dispatchers.IO) {
            file.writeBytes(bytes)
            app.getExternalFilesDir(null)?.let { ext ->
                runCatching { file.copyTo(File(ext, file.name), overwrite = true) }
            }
        }
        return file to win.name
    }

    private suspend fun sitExportBytes(kind: LogExportKind): ByteArray {
        if (kind != LogExportKind.ATAK) return sitExportText(kind).toByteArray(Charsets.UTF_8)
        publishExport(0.08f, LanguageText.text("ui.gathering.sit"))
        val win = sitExportWindow()
        val radios = _sitExportRadios.value
        val now = System.currentTimeMillis()
        publishExport(0.4f, LanguageText.text("ui.writing.what", mapOf("what" to kind.label())))
        val packed = withContext(Dispatchers.Default) {
            SitTakPackage.pack(
                win.devices,
                radios,
                win.path,
                win.gnss,
                app.config.fleets,
                app.config.watchlist,
                now,
                tag = app.config.takInstallTag,
                callsign = app.config.settings.takCallsign,
            )
        } ?: error(SitExport.emptyHint(kind, radios))
        return withContext(Dispatchers.Default) {
            SitTakPackage.zip(packed, java.util.UUID.randomUUID().toString(), win.name, now)
        }
    }

    private suspend fun sitExportText(kind: LogExportKind): String {
        publishExport(0.08f, LanguageText.text("ui.gathering.sit"))
        val win = sitExportWindow()
        val radios = _sitExportRadios.value
        val custom = RadioBookmarks.labels(app.config.watchlist)
        val notes = RadioBookmarks.notes(app.config.watchlist)
        val mine = RadioBookmarks.mineKeys(app.config.watchlist)
        val fleets = app.config.fleets
        val extra = win.devices.filter { it.attentionNotes(fleets).isNotEmpty() }.map { it.key }.toSet()
        val rows = SitExport.rows(win.devices, radios)
        if (kind == LogExportKind.LOG_CSV || kind == LogExportKind.LOG_JSONL) {
            if (rows.isEmpty()) error(SitExport.emptyHint(kind, radios))
            publishExport(
                0.4f,
                LanguageText.text(
                    "ui.writing.radios",
                    mapOf("what" to kind.label(), "n" to rows.size.toString()),
                ),
            )
            return withContext(Dispatchers.Default) {
                if (kind == LogExportKind.LOG_CSV) {
                    SitExport.csv(win.devices, radios, custom, notes, extra, fleets, mine)
                } else {
                    SitExport.jsonl(win.devices, radios, custom, notes, extra, fleets, mine)
                }
            }
        }
        val pins = SitExport.mapRadios(win.devices, radios)
        if (pins.isEmpty()) error(SitExport.emptyHint(kind, radios))
        val fmt = GeoExport.formatOf(kind) ?: error(LanguageText.text("ui.pick.map.format"))
        val info = listOf(
            "model=${android.os.Build.MODEL}",
            "release=${android.os.Build.VERSION.RELEASE}",
            "device=${android.os.Build.DEVICE}",
        ).joinToString(",")
        publishExport(
            0.45f,
            LanguageText.text(
                "ui.writing.pins",
                mapOf("what" to kind.label(), "n" to pins.size.toString()),
            ),
        )
        return withContext(Dispatchers.Default) {
            GeoExport.render(
                fmt, pins, emptyMap(), BuildConfig.VERSION_NAME, info,
                customNames = custom,
                observerNotes = notes,
                track = win.path,
                onProgress = { done, total ->
                    if (total <= 0) return@render
                    if (done == total || done % 250 == 0) {
                        val pct = 0.50f + 0.45f * done.toFloat() / total.toFloat()
                        kotlinx.coroutines.runBlocking {
                            publishExport(
                            pct,
                            LanguageText.text(
                                "ui.writing.progress",
                                mapOf(
                                    "what" to kind.label(),
                                    "done" to done.toString(),
                                    "total" to total.toString(),
                                ),
                            ),
                        )
                        }
                    }
                },
            )
        }
    }

    fun startExport() {
        if (_export.value.active) return
        viewModelScope.launch {
            runExport(LanguageText.text("ui.logging.paused.preparing.file")) {
                val kind = _logExportKind.value
                val radios = _logExportRadios.value
                val file = when (kind) {
                    LogExportKind.LOG_CSV ->
                        app.logs.exportBundle(asJsonl = false, radios = radios, onProgress = ::reportCopy)
                    LogExportKind.LOG_JSONL ->
                        app.logs.exportBundle(asJsonl = true, radios = radios, onProgress = ::reportCopy)
                    LogExportKind.GPX, LogExportKind.KML, LogExportKind.WIGLE -> writeMapExport(kind, radios)
                    LogExportKind.GEOJSON, LogExportKind.ATAK -> error(LanguageText.text("ui.format.sit.export"))
                }
                app.getExternalFilesDir(null)?.let { ext ->
                    runCatching { file.copyTo(File(ext, file.name), overwrite = true) }
                }
                val uri: Uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
                Intent(Intent.ACTION_SEND).apply {
                    type = exportMime()
                    clipData = ClipData.newRawUri("log-export", uri)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, exportSubject(kind))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }.onSuccess { intent ->
                _export.value = ExportUi(active = false, progress = 1f, share = intent)
            }
        }
    }

    fun startSaveToUri(uri: Uri) {
        if (_export.value.active) return
        viewModelScope.launch {
            runExport(LanguageText.text("ui.logging.paused.saving.location")) {
                val kind = _logExportKind.value
                val radios = _logExportRadios.value
                when (kind) {
                    LogExportKind.LOG_CSV ->
                        app.logs.exportToUri(
                            app.contentResolver, uri, asJsonl = false, radios = radios, onProgress = ::reportCopy,
                        )
                    LogExportKind.LOG_JSONL ->
                        app.logs.exportToUri(
                            app.contentResolver, uri, asJsonl = true, radios = radios, onProgress = ::reportCopy,
                        )
                    LogExportKind.GPX, LogExportKind.KML, LogExportKind.WIGLE -> {
                        val text = mapExportText(kind, radios)
                        withContext(Dispatchers.IO) {
                            app.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                                ?: error(LanguageText.text("ui.could.not.open.location"))
                        }
                    }
                    LogExportKind.GEOJSON, LogExportKind.ATAK -> error(LanguageText.text("ui.format.sit.export"))
                }
            }.onSuccess {
                _export.value = ExportUi(active = false, progress = 1f, saved = true)
            }
        }
    }

    private fun exportSubject(kind: LogExportKind): String = when (kind) {
        LogExportKind.LOG_CSV -> LanguageText.text("ui.fieldwatch.log.csv")
        LogExportKind.LOG_JSONL -> LanguageText.text("ui.fieldwatch.log.json.lines")
        LogExportKind.GPX -> LanguageText.text("ui.fieldwatch.gpx")
        LogExportKind.KML -> LanguageText.text("ui.fieldwatch.kml")
        LogExportKind.WIGLE -> LanguageText.text("ui.fieldwatch.wigle.csv")
        LogExportKind.GEOJSON, LogExportKind.ATAK -> error(LanguageText.text("ui.format.sit.export"))
    }

    private suspend fun writeMapExport(kind: LogExportKind, radios: LogExportRadios): File {
        val text = mapExportText(kind, radios)
        val fmt = GeoExport.formatOf(kind) ?: error(LanguageText.text("ui.pick.map.format"))
        val dir = File(app.cacheDir, "export").apply { mkdirs() }
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        val file = File(dir, GeoExport.suggestedName(fmt, stamp))
        withContext(Dispatchers.IO) {
            file.writeText(text)
            app.getExternalFilesDir(null)?.let { ext ->
                runCatching { file.copyTo(File(ext, file.name), overwrite = true) }
            }
        }
        return file
    }

    private suspend fun mapExportText(kind: LogExportKind, radios: LogExportRadios): String {
        val fmt = GeoExport.formatOf(kind) ?: error(LanguageText.text("ui.pick.map.format"))
        publishExport(0.02f, LanguageText.text("ui.reading.log.2"))
        val pins = app.logs.readRadios { copied, total ->
            val pct = 0.05f + 0.40f * copied.toFloat() / total.toFloat().coerceAtLeast(1f)
            publishExport(
                pct,
                LanguageText.text(
                    "ui.reading.kb",
                    mapOf(
                        "done" to (copied / 1024).toString(),
                        "total" to (total / 1024).toString(),
                    ),
                ),
            )
        }.filter { it.hasPosition && radios.matches(it.kind) }
        if (pins.isEmpty()) {
            val which = when (radios) {
                LogExportRadios.BOTH -> "ui.no.gps.tagged.radios"
                LogExportRadios.WIFI -> "ui.no.gps.tagged.wifi"
                LogExportRadios.BLE -> "ui.no.gps.tagged.ble"
            }
            error(LanguageText.text(which))
        }
        val info = listOf(
            "model=${android.os.Build.MODEL}",
            "release=${android.os.Build.VERSION.RELEASE}",
            "device=${android.os.Build.DEVICE}",
            "display=${android.os.Build.DISPLAY}",
            "board=${android.os.Build.BOARD}",
            "brand=${android.os.Build.BRAND}",
        ).joinToString(",")
        publishExport(
            0.48f,
            LanguageText.text(
                "ui.writing.pins",
                mapOf("what" to kind.label(), "n" to pins.size.toString()),
            ),
        )
        return withContext(Dispatchers.Default) {
            GeoExport.render(
                fmt, pins, emptyMap(), BuildConfig.VERSION_NAME, info,
                customNames = RadioBookmarks.labels(app.config.watchlist),
                onProgress = { done, total ->
                    if (total <= 0) return@render
                    if (done == total || done % 250 == 0) {
                        val pct = 0.50f + 0.45f * done.toFloat() / total.toFloat()
                        kotlinx.coroutines.runBlocking {
                            publishExport(
                            pct,
                            LanguageText.text(
                                "ui.writing.progress",
                                mapOf(
                                    "what" to kind.label(),
                                    "done" to done.toString(),
                                    "total" to total.toString(),
                                ),
                            ),
                        )
                        }
                    }
                },
            )
        }
    }

    fun clearLogs() {
        if (_export.value.active) return
        viewModelScope.launch {
            _export.value = ExportUi(active = true, progress = 0f, message = LanguageText.text("ui.clearing.log"))
            runCatching { app.logs.clear() }
                .onSuccess { count ->
                    app.devices.bumpLogs(count)
                    _export.value = ExportUi(cleared = true, message = LanguageText.text("ui.log.cleared"))
                }
                .onFailure { err ->
                    _export.value = ExportUi(error = err.message ?: LanguageText.text("ui.could.not.clear.log"))
                }
        }
    }

    fun consumeShare() {
        _export.value = _export.value.copy(share = null)
    }

    fun consumeExportNotice() {
        val cur = _export.value
        _export.value = if (cur.followUpTitle != null) {
            ExportUi(
                noticeTitle = cur.followUpTitle,
                noticeMessage = cur.followUpMessage,
            )
        } else {
            ExportUi()
        }
    }

    private suspend fun reportCopy(copied: Long, total: Long) {
        val pct = (copied.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        publishExport(
            pct,
            LanguageText.text(
                "ui.writing.kb",
                mapOf(
                    "done" to (copied / 1024).toString(),
                    "total" to (total / 1024).toString(),
                ),
            ),
        )
    }

    private suspend fun publishExport(progress: Float, message: String) {
        withContext(Dispatchers.Main.immediate) {
            _export.value = ExportUi(
                active = true,
                progress = progress.coerceIn(0f, 1f),
                message = message,
            )
        }
        yield()
    }

    private suspend fun <T> runExport(startMessage: String, block: suspend () -> T): Result<T> {
        _export.value = ExportUi(active = true, progress = 0f, message = startMessage)
        return runCatching { block() }.onFailure { err ->
            _export.value = ExportUi(active = false, error = err.message ?: LanguageText.text("ui.export.failed"))
        }
    }

    fun logBytes(): Long = app.logs.totalBytes()

    fun fleetName(id: String): String = app.config.fleets.firstOrNull { it.id == id }?.name ?: id

    fun fleetKind(id: String): SignatureClass? = app.config.fleets.firstOrNull { it.id == id }?.kind

    fun fleetColor(id: String): Int =
        app.config.fleets.firstOrNull { it.id == id }?.colorIndex ?: 0

    fun fleetAttentionNote(id: String): String =
        app.config.fleets.firstOrNull { it.id == id }?.attentionNote.orEmpty()

    fun hasAttention(device: Sighting): Boolean =
        device.fleetIds.any { fleetAttentionNote(it).isNotBlank() }

    fun hasObserverNote(device: Sighting): Boolean =
        watchObserverNoteFor(device.key) != null

    fun fleetHasDecode(id: String): Boolean =
        app.config.fleets.firstOrNull { it.id == id }?.decode != null

    fun attentionNotesFor(device: Sighting): List<Pair<String, String>> =
        device.attentionNotes(app.config.fleets)

    fun signatureNotesFor(device: Sighting): List<Pair<String, String>> =
        device.signatureNotes(app.config.fleets)

    fun isWatched(deviceKey: String): Boolean =
        app.config.watchlist.any { it.deviceKey == deviceKey && it.alert }
    fun isFleetWatched(id: String): Boolean = app.config.watchlist.any { it.fleetId == id }

    fun testWatchBeep() {
        val settings = app.config.settings
        app.alerter.playTestBeep(
            beepOn = settings.alertBeep,
            speakClass = settings.alertVoice,
            voiceWhat = settings.alertVoiceWhat,
        )
    }

    companion object {
        private const val FLASH_MS = 1_000L
    }
}

data class BeepSnap(
    val seq: Int = 0,
    val key: String = "",
)

data class HuntUi(
    val active: Boolean = false,
    val device: Sighting? = null,
    val title: String = "",
    val cue: HuntCue = HuntCue.WAITING,
    val peakRssi: Int = -127,
    val samples: List<RssiSample> = emptyList(),
    val lastSeen: Long = 0L,
)
