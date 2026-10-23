package app.fieldwatch.ui.screen

import app.fieldwatch.domain.LanguageText

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import app.fieldwatch.ui.component.FieldwatchActionButton
import androidx.compose.material3.Scaffold
import app.fieldwatch.ui.component.FieldwatchSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import app.fieldwatch.domain.DeviceExplain
import app.fieldwatch.domain.Geo
import app.fieldwatch.domain.GpsSample
import app.fieldwatch.domain.MacUtil
import app.fieldwatch.domain.Hunt
import app.fieldwatch.domain.MapFrame
import app.fieldwatch.domain.PayloadLocation
import app.fieldwatch.domain.Palette
import app.fieldwatch.domain.Rssi
import app.fieldwatch.domain.SitPathPlot
import app.fieldwatch.ui.FieldwatchViewModel
import app.fieldwatch.ui.component.HuntHeatMap
import app.fieldwatch.ui.component.Sparkline
import app.fieldwatch.ui.component.rssiColor
import app.fieldwatch.ui.theme.LocalNightMode
import app.fieldwatch.ui.theme.nightIf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HuntScreen(
    vm: FieldwatchViewModel,
    onBack: () -> Unit,
    demoMode: Boolean = false,
    huntBeep: Boolean = false,
    huntVibrate: Boolean = false,
) {
    val hunt by vm.hunt.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        onDispose { vm.stopHunt() }
    }
    val ui by vm.ui.collectAsStateWithLifecycle()
    var viewport by remember { mutableStateOf<SitPathPlot.Viewport?>(null) }
    val tiles by vm.huntTiles.collectAsStateWithLifecycle()
    LaunchedEffect(hunt.active, huntBeep, huntVibrate) {
        if (!hunt.active || (!huntBeep && !huntVibrate)) return@LaunchedEffect
        var lastTick = 0L
        while (true) {
            val live = vm.hunt.value
            val interval = Hunt.tickIntervalMs(live.device?.rssi, live.cue)
            if (interval == null) {
                delay(200)
                continue
            }
            val wait = interval - (System.currentTimeMillis() - lastTick)
            if (wait > 0L) {
                delay(wait.coerceAtMost(80L))
                continue
            }
            vm.huntTick(huntBeep, huntVibrate)
            lastTick = System.currentTimeMillis()
        }
    }
    val cover = remember(hunt.trail, hunt.hereLat, hunt.hereLon) {
        if (PayloadLocation.validCoord(hunt.hereLat, hunt.hereLon)) {
            hunt.trail + GpsSample(0L, hunt.hereLat!!, hunt.hereLon!!)
        } else {
            hunt.trail
        }
    }
    val online = ui.settings.onlineLookup
    val basemap = ui.settings.mapBasemap
    val mapKeyRev by vm.mapKeyRevision.collectAsStateWithLifecycle()
    LaunchedEffect(cover, online, basemap, viewport, mapKeyRev) {
        val held = viewport
        if (held == null) {
            vm.refreshHuntTiles(cover, online, basemap)
        } else {
            delay(350)
            vm.refreshHuntViewport(held, online, basemap)
        }
    }
    val device = hunt.device
    val rssi = device?.rssi
    val night = LocalNightMode.current
    val accent = (device?.fleetIds?.firstOrNull()
        ?.let { Color(Palette.color(vm.fleetColor(it))) }
        ?: rssi?.let { rssiColor(it) }
        ?: MaterialTheme.colorScheme.primary)
        .nightIf(night)
    val hasHere = PayloadLocation.validCoord(hunt.hereLat, hunt.hereLon)
    val showMap = hunt.trail.isNotEmpty() || hasHere
    val footer = Geo.mapFooterText(
        ui.settings.mapFooter,
        if (hasHere) hunt.hereLat else ui.phoneLat,
        if (hasHere) hunt.hereLon else ui.phoneLon,
        demoMode,
    )
    val hint = when {
        showMap && hunt.trail.isEmpty() -> LanguageText.text("hunt.heat.empty")
        !showMap && !ui.settings.tagLocation -> LanguageText.text("ui.turn.tag.detections.gps.2")
        !showMap -> LanguageText.text("ui.waiting.gps.fix")
        else -> null
    }
    val loudest = if (hunt.peakRssi > -127 && Rssi.measured(hunt.peakRssi)) {
        LanguageText.text("ui.loudest.hunt") + " ${hunt.peakRssi} dBm"
    } else {
        LanguageText.text("ui.loudest.hunt")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val shown = hunt.device?.let { MacUtil.redactMacIn(hunt.title, it.mac, demoMode) } ?: hunt.title
                    Text(shown.ifBlank { LanguageText.text("ui.hunt.label") }, maxLines = 1)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, LanguageText.text("chrome.back"))
                    }
                },
            )
        },
        bottomBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FieldwatchActionButton(
                    onClick = vm::resetHunt,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = hunt.active,
                ) {
                    Text(LanguageText.text("ui.reset.hunt"))
                }
                FieldwatchActionButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(LanguageText.text("ui.back.detail"))
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(LanguageText.text("ui.beep.label"), Modifier.weight(1f))
                        FieldwatchSwitch(
                            huntBeep,
                            { on ->
                                vm.updateSettings { it.copy(huntBeep = on) }
                                if (on) vm.huntTick(true, huntVibrate)
                            },
                        )
                    }
                    Row(
                        Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(LanguageText.text("ui.vibrate.label"), Modifier.weight(1f))
                        FieldwatchSwitch(
                            huntVibrate,
                            { on ->
                                vm.updateSettings { it.copy(huntVibrate = on) }
                                if (on) vm.huntTick(huntBeep, true)
                            },
                        )
                    }
                }
            }
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(horizontal = 12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().height(44.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    rssi?.toString() ?: "—",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp,
                    color = accent,
                )
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(
                        rssi?.let { DeviceExplain.rssiExplain(it) } ?: LanguageText.text("ui.no.live.rssi"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        loudest,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (showMap) {
                    HuntHeatMap(
                        trail = hunt.trail,
                        hereLat = hunt.hereLat,
                        hereLon = hunt.hereLon,
                        tiles = tiles,
                        position = footer,
                        hint = hint,
                        viewport = viewport,
                        onViewport = { viewport = it },
                        courseDeg = ui.travelDeg,
                        mapFrame = MapFrame.resolved(ui.settings.mapFrame, ui.settings.mapRotate),
                        onRotate = { vm.cycleMapFrame() },
                        onToggleClose = { vm.toggleMapClose() },
                    )
                } else {
                    Text(
                        hint.orEmpty(),
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Sparkline(
                hunt.samples,
                accent,
                Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}
