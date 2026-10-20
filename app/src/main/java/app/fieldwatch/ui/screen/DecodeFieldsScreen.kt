package app.fieldwatch.ui.screen

import app.fieldwatch.domain.LanguageText

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import app.fieldwatch.ui.component.FieldwatchFilterChip
import app.fieldwatch.ui.component.FieldwatchSwitch
import app.fieldwatch.ui.component.FieldwatchActionButton
import app.fieldwatch.ui.component.FieldwatchDropdownField
import app.fieldwatch.ui.component.FieldwatchOutlinedField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.fieldwatch.domain.DecodedFieldValue
import app.fieldwatch.domain.DecodeEndian
import app.fieldwatch.domain.DecodeField
import app.fieldwatch.domain.DecodeSource
import app.fieldwatch.domain.DecodeType
import app.fieldwatch.domain.DecodeWhen
import app.fieldwatch.domain.DecodeWhenOp
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.FleetDecode
import app.fieldwatch.domain.Sighting
import app.fieldwatch.domain.SignatureFieldDecoder
import app.fieldwatch.domain.defaultLength
import app.fieldwatch.domain.hexSpaced
import app.fieldwatch.domain.normalized
import app.fieldwatch.domain.resolvedLength
import app.fieldwatch.ui.NestedTabInsets
import app.fieldwatch.ui.NestedTopBar
import app.fieldwatch.ui.component.SectionCard
import app.fieldwatch.ui.component.spectreTileFill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecodeFieldsScreen(
    fleet: Fleet,
    previewDevice: Sighting?,
    onSave: (FleetDecode?) -> Unit,
    onBack: () -> Unit,
) {
    val initial = fleet.decode
    var source by remember { mutableStateOf(initial?.source ?: DecodeSource.MANUFACTURER_DATA) }
    var companyText by remember {
        mutableStateOf(initial?.companyId?.takeIf { it != 0 }?.let { "0x%04X".format(it) }.orEmpty())
    }
    var serviceUuid by remember { mutableStateOf(initial?.serviceUuid.orEmpty()) }
    var fields by remember { mutableStateOf(initial?.fields ?: emptyList()) }
    var confirmRemove by remember { mutableStateOf(false) }
    val includeCompanyId = initial?.includeCompanyId == true

    fun currentDecode(): FleetDecode? {
        val cleaned = fields.filter { it.label.isNotBlank() && it.id.isNotBlank() }.map { it.normalized() }
        if (cleaned.isEmpty()) return null
        return FleetDecode(
            source = source,
            serviceUuid = serviceUuid.trim().ifBlank { null },
            companyId = parseCompanyId(companyText),
            includeCompanyId = includeCompanyId,
            fields = cleaned,
        )
    }

    val previewDecode = currentDecode()
    val previewRows = remember(previewDecode, previewDevice) {
        if (previewDecode == null || previewDevice == null) emptyList()
        else SignatureFieldDecoder.decodeFleet(fleet.copy(decode = previewDecode), previewDecode, previewDevice)
    }
    val previewHex = remember(previewDecode, previewDevice) {
        if (previewDecode == null || previewDevice == null) null
        else SignatureFieldDecoder.payloadHex(previewDecode, previewDevice)
    }

    Scaffold(
        contentWindowInsets = NestedTabInsets,
        topBar = {
            NestedTopBar(
                title = LanguageText.text("ui.decode.fields"),
                navigationIcon = { TextButton(onClick = onBack) { Text(LanguageText.text("chrome.back")) } },
                actions = { TextButton(onClick = { onSave(currentDecode()) }) { Text(LanguageText.text("ui.save.label")) } },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(LanguageText.text("ui.source.label")) {
            Text(
                LanguageText.text("ui.map.cleartext.ble.bytes"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldwatchFilterChip(
                    selected = source == DecodeSource.MANUFACTURER_DATA,
                    onClick = { source = DecodeSource.MANUFACTURER_DATA },
                    label = { Text(LanguageText.text("ui.manufacturer.label")) },
                )
                FieldwatchFilterChip(
                    selected = source == DecodeSource.SERVICE_DATA,
                    onClick = { source = DecodeSource.SERVICE_DATA },
                    label = { Text(LanguageText.text("ui.service.data")) },
                )
            }
            if (source == DecodeSource.MANUFACTURER_DATA) {
                CompactField(
                    companyText,
                    { companyText = it },
                    LanguageText.text("ui.company.id"),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    LanguageText.text("ui.optional.byte.0.first"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                CompactField(
                    serviceUuid,
                    { serviceUuid = it },
                    LanguageText.text("ui.service.uuid"),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    LanguageText.text("ui.required.16.bit.feaa"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            }

            SectionCard(LanguageText.text("ui.fields.label")) {
            fields.forEachIndexed { index, field ->
                FieldCard(
                    field = field,
                    onChange = { next ->
                        fields = fields.toMutableList().also { it[index] = next }
                    },
                    onDelete = {
                        fields = fields.filterIndexed { i, _ -> i != index }
                    },
                )
            }
            FieldwatchActionButton(
                onClick = {
                    val nextOffset = fields.lastOrNull()?.let { it.offset + it.resolvedLength() } ?: 0
                    val n = fields.size + 1
                    fields = fields + DecodeField(
                        id = "field_$n",
                        label = "Field $n",
                        offset = nextOffset,
                        type = DecodeType.U8,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(LanguageText.text("ui.add.field")) }
            if (initial != null || fields.isNotEmpty()) {
                FieldwatchActionButton(
                    onClick = { confirmRemove = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(LanguageText.text("ui.remove.decode.map")) }
                Text(
                    LanguageText.text("ui.clears.every.field.live"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            }

            SectionCard(LanguageText.text("ui.preview.label")) {
            PreviewBlock(
                previewDevice = previewDevice,
                previewHex = previewHex,
                previewRows = previewRows,
            )
            }
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(LanguageText.text("ui.remove.decode.map.2")) },
            text = {
                Text(LanguageText.text("ui.clears.all.fields.signature"))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemove = false
                        onSave(null)
                    },
                ) { Text(LanguageText.text("language.section.remove")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text(LanguageText.text("language.cancel")) }
            },
        )
    }
}

@Composable
private fun PreviewBlock(
    previewDevice: Sighting?,
    previewHex: String?,
    previewRows: List<DecodedFieldValue>,
) {
    when {
        previewDevice == null -> {
            Text(
                LanguageText.text("ui.no.matching.radio.air"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        previewHex.isNullOrBlank() -> {
            Text(
                LanguageText.text("ui.matching.radio.air.but"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> {
            Text(
                previewHex.hexSpaced(),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (previewRows.isEmpty()) {
                Text(
                    LanguageText.text("ui.nothing.parsed.check.offset"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                previewRows.forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            row.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                        Text(row.display, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldCard(
    field: DecodeField,
    onChange: (DecodeField) -> Unit,
    onDelete: () -> Unit,
) {
    val needsEndian = field.type !in setOf(
        DecodeType.U8, DecodeType.I8, DecodeType.UTF8, DecodeType.HEX, DecodeType.BOOL,
    )
    val numeric = field.type in setOf(
        DecodeType.U8, DecodeType.I8, DecodeType.U16, DecodeType.I16,
        DecodeType.U24, DecodeType.U32, DecodeType.I32, DecodeType.F32, DecodeType.BITS,
    )
    val idIsCustom = !looksGeneratedId(field.id, field.label)
    var more by remember(field.id) {
        mutableStateOf(idIsCustom || field.modulo != null)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = spectreTileFill(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompactField(
                    field.label,
                    { next ->
                        val id = if (looksGeneratedId(field.id, field.label)) slugId(next) else field.id
                        onChange(field.copy(label = next, id = id))
                    },
                    LanguageText.text("ui.label.label"),
                    modifier = Modifier.weight(1f),
                )
                TypeMenu(field.type, Modifier.width(112.dp)) { onChange(field.copy(type = it)) }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, LanguageText.text("ui.delete.field")) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactField(
                    field.offset.toString(),
                    { onChange(field.copy(offset = it.toIntOrNull() ?: 0)) },
                    LanguageText.text("ui.offset.label"),
                    keyboard = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
                CompactField(
                    (field.length ?: field.type.defaultLength()).toString(),
                    { onChange(field.copy(length = it.toIntOrNull()?.coerceAtLeast(1))) },
                    LanguageText.text("ui.length.label"),
                    keyboard = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
                CompactField(
                    field.unit.orEmpty(),
                    { onChange(field.copy(unit = it.ifBlank { null })) },
                    LanguageText.text("ui.unit.label"),
                    modifier = Modifier.width(72.dp),
                )
                if (needsEndian) {
                    EndianMenu(field.endian, Modifier.weight(1f)) { onChange(field.copy(endian = it)) }
                }
            }
            if (field.type == DecodeType.BITS) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactField(
                        (field.bitOffset ?: 0).toString(),
                        { onChange(field.copy(bitOffset = it.toIntOrNull() ?: 0)) },
                        LanguageText.text("ui.bit.offset"),
                        keyboard = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                    CompactField(
                        (field.bitWidth ?: 1).toString(),
                        { onChange(field.copy(bitWidth = it.toIntOrNull()?.coerceAtLeast(1) ?: 1)) },
                        LanguageText.text("ui.bit.width"),
                        keyboard = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (numeric) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactField(
                        field.scale?.toString().orEmpty(),
                        { onChange(field.copy(scale = it.toDoubleOrNull())) },
                        LanguageText.text("ui.scale.label"),
                        keyboard = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                    CompactField(
                        field.offsetAdd?.toString().orEmpty(),
                        { onChange(field.copy(offsetAdd = it.toDoubleOrNull())) },
                        LanguageText.text("language.section.add"),
                        keyboard = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            OnlyIfBlock(field.gate, onChange = { onChange(field.copy(gate = it)) })
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(LanguageText.text("ui.live.row"), style = MaterialTheme.typography.titleSmall)
                    Text(
                        LanguageText.text("ui.show.value.next.signature"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FieldwatchSwitch(
                    checked = field.live,
                    onCheckedChange = { onChange(field.copy(live = it)) },
                )
            }
            NamedValuesBlock(field, onChange)
            if (!more) {
                TextButton(onClick = { more = true }) { Text(LanguageText.text("ui.more.label")) }
            } else {
                CompactField(
                    field.id,
                    { onChange(field.copy(id = it.lowercase().filter { ch -> ch.isLetterOrDigit() || ch == '_' })) },
                    "ID",
                    modifier = Modifier.fillMaxWidth(),
                )
                if (numeric) {
                    CompactField(
                        field.modulo?.toString().orEmpty(),
                        { onChange(field.copy(modulo = it.toDoubleOrNull())) },
                        LanguageText.text("ui.modulo.label"),
                        keyboard = KeyboardType.Decimal,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(onClick = { more = false }) { Text(LanguageText.text("ui.hide.extra")) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnlyIfBlock(gate: DecodeWhen?, onChange: (DecodeWhen?) -> Unit) {
    if (gate == null) {
        TextButton(onClick = {
            onChange(DecodeWhen(offset = 0, length = 1, op = DecodeWhenOp.EQ, valueHex = ""))
        }) { Text(LanguageText.text("ui.only.if")) }
        return
    }
    Text(LanguageText.text("ui.only.if.2"), style = MaterialTheme.typography.titleSmall)
    if (gate.op == DecodeWhenOp.LEN) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WhenOpMenu(gate.op, Modifier.weight(1f)) { onChange(gate.copy(op = it)) }
            CompactField(
                gate.length.toString(),
                { onChange(gate.copy(length = it.toIntOrNull()?.coerceAtLeast(1) ?: 1)) },
                LanguageText.text("ui.bytes.label"),
                keyboard = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactField(
                gate.offset.toString(),
                { onChange(gate.copy(offset = it.toIntOrNull() ?: 0)) },
                LanguageText.text("ui.offset.label"),
                keyboard = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
            CompactField(
                gate.length.toString(),
                { onChange(gate.copy(length = it.toIntOrNull()?.coerceAtLeast(1) ?: 1)) },
                LanguageText.text("ui.length.label"),
                keyboard = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WhenOpMenu(gate.op, Modifier.weight(1f)) { onChange(gate.copy(op = it)) }
            CompactField(
                gate.valueHex,
                { onChange(gate.copy(valueHex = it)) },
                LanguageText.text("ui.hex.label"),
                modifier = Modifier.width(96.dp),
            )
        }
    }
    TextButton(onClick = { onChange(null) }) { Text(LanguageText.text("language.section.remove")) }
}

@Composable
private fun NamedValuesBlock(
    field: DecodeField,
    onChange: (DecodeField) -> Unit,
) {
    val fieldId = field.id
    val labels = field.enumLabels
    var rows by remember(fieldId) {
        mutableStateOf(labels?.toList() ?: emptyList())
    }
    var open by remember(fieldId) { mutableStateOf(rows.isNotEmpty()) }
    fun publish(
        nextRows: List<Pair<String, String>>,
        emphasis: List<String> = field.liveEmphasis,
        notes: Map<String, String>? = field.enumNotes,
    ) {
        onChange(
            field.copy(
                enumLabels = nextRows.toEnumMap(),
                liveEmphasis = emphasis,
                enumNotes = notes?.filterValues { it.isNotBlank() }?.ifEmpty { null },
            ),
        )
    }
    if (!open) {
        TextButton(onClick = {
            open = true
            rows = listOf("" to "")
        }) { Text(LanguageText.text("ui.named.values")) }
        return
    }
    Text(LanguageText.text("ui.named.values.2"), style = MaterialTheme.typography.titleSmall)
    rows.forEachIndexed { index, (raw, shown) ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompactField(
                    raw,
                    { next ->
                        val nextRows = rows.toMutableList().also { it[index] = next to shown }
                        rows = nextRows
                        val emphasis = field.liveEmphasis.map { if (it == raw) next else it }
                        val notes = field.enumNotes?.mapKeys { (key, _) -> if (key == raw) next else key }
                        publish(nextRows, emphasis, notes)
                    },
                    LanguageText.text("ui.raw.label"),
                    modifier = Modifier.width(88.dp),
                )
                CompactField(
                    shown,
                    { next ->
                        val nextRows = rows.toMutableList().also { it[index] = raw to next }
                        rows = nextRows
                        publish(nextRows)
                    },
                    LanguageText.text("ui.show.label"),
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        val nextRows = rows.filterIndexed { i, _ -> i != index }
                        rows = nextRows
                        val emphasis = field.liveEmphasis.filter { it != raw }
                        val notes = field.enumNotes?.filterKeys { it != raw }
                        if (nextRows.isEmpty()) {
                            open = false
                            onChange(field.copy(enumLabels = null, liveEmphasis = emphasis, enumNotes = notes?.ifEmpty { null }))
                        } else {
                            publish(nextRows, emphasis, notes)
                        }
                    },
                ) { Icon(Icons.Outlined.Delete, LanguageText.text("ui.delete.value")) }
            }
            if (field.live && raw.isNotBlank()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val strong = field.liveEmphasis.any { it == raw }
                    FieldwatchFilterChip(
                        selected = strong,
                        onClick = {
                            val emphasis = if (strong) {
                                field.liveEmphasis.filter { it != raw }
                            } else {
                                field.liveEmphasis + raw
                            }
                            publish(rows, emphasis)
                        },
                        label = { Text(LanguageText.text("ui.strong.label")) },
                    )
                    CompactField(
                        field.enumNotes?.get(raw).orEmpty(),
                        { note ->
                            val notes = (field.enumNotes ?: emptyMap()).toMutableMap()
                            if (note.isBlank()) notes.remove(raw) else notes[raw] = note
                            publish(rows, notes = notes)
                        },
                        LanguageText.text("ui.note.label"),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
    Row {
        TextButton(onClick = { rows = rows + ("" to "") }) { Text(LanguageText.text("ui.add.value")) }
        TextButton(
            onClick = {
                open = false
                rows = emptyList()
                onChange(field.copy(enumLabels = null, liveEmphasis = emptyList(), enumNotes = null))
            },
        ) { Text(LanguageText.text("language.section.remove")) }
    }
}

@Composable
private fun CompactField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    FieldwatchOutlinedField(
        value,
        onChange,
        label,
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhenOpMenu(op: DecodeWhenOp, modifier: Modifier, onChange: (DecodeWhenOp) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = when (op) {
        DecodeWhenOp.EQ -> "equals"
        DecodeWhenOp.NEQ -> LanguageText.text("ui.not.equals")
        DecodeWhenOp.MASK -> "mask"
        DecodeWhenOp.NMASK -> LanguageText.text("ui.none.bits")
        DecodeWhenOp.LEN -> "length"
    }
    ExposedDropdownMenuBox(open, { open = it }, modifier) {
        FieldwatchDropdownField(LanguageText.text("ui.when.label"), label, open)
        ExposedDropdownMenu(open, { open = false }) {
            DropdownMenuItem(text = { Text("equals") }, onClick = { onChange(DecodeWhenOp.EQ); open = false })
            DropdownMenuItem(text = { Text(LanguageText.text("ui.not.equals")) }, onClick = { onChange(DecodeWhenOp.NEQ); open = false })
            DropdownMenuItem(text = { Text("mask") }, onClick = { onChange(DecodeWhenOp.MASK); open = false })
            DropdownMenuItem(text = { Text(LanguageText.text("ui.none.bits")) }, onClick = { onChange(DecodeWhenOp.NMASK); open = false })
            DropdownMenuItem(text = { Text(LanguageText.text("ui.payload.length")) }, onClick = { onChange(DecodeWhenOp.LEN); open = false })
        }
    }
}

private fun List<Pair<String, String>>.toEnumMap(): Map<String, String>? {
    val out = linkedMapOf<String, String>()
    for ((k, v) in this) {
        val key = k.trim()
        val label = v.trim()
        if (key.isEmpty() || label.isEmpty()) continue
        out[key] = label
    }
    return out.ifEmpty { null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeMenu(type: DecodeType, modifier: Modifier, onChange: (DecodeType) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(open, { open = it }, modifier) {
        FieldwatchDropdownField(LanguageText.text("ui.type.label"), type.name.lowercase(), open)
        ExposedDropdownMenu(open, { open = false }) {
            DecodeType.entries.forEach { t ->
                DropdownMenuItem(
                    text = { Text(t.name.lowercase()) },
                    onClick = { onChange(t); open = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EndianMenu(endian: DecodeEndian, modifier: Modifier, onChange: (DecodeEndian) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(open, { open = it }, modifier) {
        FieldwatchDropdownField(LanguageText.text("ui.endian.label"), if (endian == DecodeEndian.BE) "BE" else "LE", open)
        ExposedDropdownMenu(open, { open = false }) {
            DropdownMenuItem(text = { Text("little") }, onClick = { onChange(DecodeEndian.LE); open = false })
            DropdownMenuItem(text = { Text("big") }, onClick = { onChange(DecodeEndian.BE); open = false })
        }
    }
}

private fun parseCompanyId(text: String): Int? {
    val t = text.trim()
    if (t.isEmpty()) return null
    val hex = t.removePrefix("0x").removePrefix("0X")
    return hex.toIntOrNull(16) ?: t.toIntOrNull()
}

private fun slugId(label: String): String {
    val slug = label.lowercase().map { ch ->
        if (ch.isLetterOrDigit()) ch else '_'
    }.joinToString("").trim('_')
    val clipped = slug.take(32).ifBlank { "field" }
    return if (clipped.first().isLetter()) clipped else "f_$clipped"
}

private fun looksGeneratedId(id: String, label: String): Boolean =
    id == slugId(label) || id.matches(Regex("field_\\d+"))
