package de.namio.feature.sitzplan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.namio.R
import de.namio.core.media.FotoStore
import de.namio.core.model.Blickrichtung
import de.namio.core.model.Position
import de.namio.core.model.Schueler
import de.namio.core.model.Sitzplan
import de.namio.core.repository.SitzplanRepository
import de.namio.feature.klassen.rememberFotoStore
import de.namio.ui.components.BestaetigenDialog
import de.namio.ui.components.SchuelerFoto
import kotlin.math.roundToInt

@Composable
fun SitzplanScreen(
    onZurueck: () -> Unit,
    viewModel: SitzplanViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val fotoStore = rememberFotoStore()
    SitzplanInhalt(
        state = state,
        fotoStore = fotoStore,
        onZurueck = onZurueck,
        onPlanWaehlen = viewModel::planWaehlen,
        onPlanAnlegen = viewModel::planAnlegen,
        onPlanAendern = viewModel::planAendern,
        onPlanLoeschen = viewModel::planLoeschen,
        onAlsStandard = viewModel::alsStandard,
        onSetzen = viewModel::setzen,
        onEntfernen = viewModel::entfernen,
        onLeererStuhl = viewModel::leerenStuhlUmschalten,
        onMischen = viewModel::mischen,
        onBlickrichtung = viewModel::blickrichtungUmschalten,
    )
}

/** Laufender Drag: welcher Schüler, wo ist der Finger (Root-Koordinaten). */
private data class Drag(val schueler: Schueler, val position: Offset)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SitzplanInhalt(
    state: SitzplanUiState,
    fotoStore: FotoStore,
    onZurueck: () -> Unit,
    onPlanWaehlen: (Long) -> Unit,
    onPlanAnlegen: (String, Int, Int, Boolean) -> Unit,
    onPlanAendern: (String, Int, Int, Boolean) -> Unit,
    onPlanLoeschen: () -> Unit,
    onAlsStandard: () -> Unit,
    onSetzen: (Long, Int, Int) -> Unit,
    onEntfernen: (Long) -> Unit,
    onLeererStuhl: (Int, Int) -> Unit,
    onMischen: () -> Unit,
    onBlickrichtung: () -> Unit,
) {
    var neuDialog by rememberSaveable { mutableStateOf(false) }
    var bearbeitenDialog by rememberSaveable { mutableStateOf(false) }
    var loeschenFrage by rememberSaveable { mutableStateOf(false) }
    var menueOffen by remember { mutableStateOf(false) }
    var ausgewaehlt by remember { mutableStateOf<Long?>(null) }
    var drag by remember { mutableStateOf<Drag?>(null) }
    val zellen = remember { mutableStateMapOf<Position, Rect>() }
    var leiste by remember { mutableStateOf<Rect?>(null) }
    var wurzel by remember { mutableStateOf(Offset.Zero) }
    val plan = state.aktiv

    fun ablegen(schueler: Schueler, punkt: Offset) {
        val ziel = zellen.entries.firstOrNull { it.value.contains(punkt) }?.key
        when {
            ziel != null -> onSetzen(schueler.id, ziel.spalte, ziel.reihe)
            leiste?.contains(punkt) == true -> onEntfernen(schueler.id)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sitzplan_titel, state.klasse?.name ?: "")) },
                navigationIcon = {
                    IconButton(onClick = onZurueck) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.zurueck))
                    }
                },
                actions = {
                    if (plan != null) {
                        IconButton(onClick = onBlickrichtung) {
                            Icon(Icons.Default.Cameraswitch, contentDescription = stringResource(R.string.sitzplan_blickrichtung))
                        }
                        IconButton(onClick = onMischen) {
                            Icon(Icons.Default.Shuffle, contentDescription = stringResource(R.string.sitzplan_mischen))
                        }
                        Box {
                            IconButton(onClick = { menueOffen = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.mehr))
                            }
                            DropdownMenu(expanded = menueOffen, onDismissRequest = { menueOffen = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.sitzplan_neu)) }, onClick = { menueOffen = false; neuDialog = true })
                                DropdownMenuItem(text = { Text(stringResource(R.string.sitzplan_bearbeiten)) }, onClick = { menueOffen = false; bearbeitenDialog = true })
                                if (!plan.istStandard) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.sitzplan_als_standard)) }, onClick = { menueOffen = false; onAlsStandard() })
                                }
                                DropdownMenuItem(text = { Text(stringResource(R.string.sitzplan_loeschen)) }, onClick = { menueOffen = false; loeschenFrage = true })
                            }
                        }
                    }
                },
            )
        },
    ) { innen ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innen)
                .onGloballyPositioned { wurzel = it.boundsInRoot().topLeft },
        ) {
            if (!state.laedt && plan == null) {
                Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.sitzplan_keiner), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { neuDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.sitzplan_neu))
                    }
                }
            } else if (plan != null) {
                Column(Modifier.fillMaxSize()) {
                    if (state.plaene.size > 1) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(state.plaene, key = { it.id }) { p ->
                                FilterChip(selected = p.id == plan.id, onClick = { onPlanWaehlen(p.id) }, label = { Text(p.name) })
                            }
                        }
                    }
                    Text(
                        stringResource(if (ausgewaehlt != null) R.string.sitzplan_hinweis_platz else R.string.sitzplan_hinweis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
                        SitzplanRaster(
                            plan = plan,
                            plaetze = state.plaetze,
                            schuelerProId = state.schuelerProId,
                            blickrichtung = state.blickrichtung,
                            fotoStore = fotoStore,
                            modifier = Modifier.padding(8.dp),
                            markierung = { if (it.schuelerId != null && it.schuelerId == ausgewaehlt) PlatzMarkierung.AUSGEWAEHLT else PlatzMarkierung.KEINE },
                            onZellePositioniert = { pos, rect -> zellen[pos] = rect },
                            zellenModifier = { pos, platz ->
                                val sitzender = platz?.schuelerId?.let(state.schuelerProId::get)
                                Modifier
                                    .clickable {
                                        val gewaehlt = ausgewaehlt
                                        when {
                                            gewaehlt != null -> { onSetzen(gewaehlt, pos.spalte, pos.reihe); ausgewaehlt = null }
                                            sitzender != null -> ausgewaehlt = sitzender.id
                                            else -> onLeererStuhl(pos.spalte, pos.reihe)
                                        }
                                    }
                                    .then(
                                        if (sitzender != null) {
                                            Modifier.dragQuelle(sitzender, { zellen[pos]?.topLeft ?: Offset.Zero }, { drag = it }, { s, p -> drag = null; ablegen(s, p) })
                                        } else {
                                            Modifier
                                        },
                                    )
                            },
                        )
                    }
                    UnplatziertLeiste(
                        schueler = state.unplatziert,
                        ausgewaehlt = ausgewaehlt,
                        fotoStore = fotoStore,
                        onTipp = { ausgewaehlt = if (ausgewaehlt == it.id) null else it.id },
                        onDrag = { drag = it },
                        onDrop = { s, p -> drag = null; ablegen(s, p) },
                        modifier = Modifier.onGloballyPositioned { leiste = it.boundsInRoot() },
                    )
                }
            }
            drag?.let { d ->
                val dichte = LocalDensity.current
                val halb = with(dichte) { (PLATZ_GROESSE / 2).toPx() }
                Box(
                    Modifier
                        .zIndex(10f)
                        .offset { IntOffset((d.position.x - wurzel.x - halb).roundToInt(), (d.position.y - wurzel.y - halb).roundToInt()) }
                        .size(PLATZ_GROESSE)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    SchuelerFoto(
                        datei = d.schueler.fotoDatei?.let(fotoStore::datei),
                        beschreibung = d.schueler.vollerName,
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                    )
                }
            }
        }
    }

    if (neuDialog) {
        PlanDialog(
            titel = stringResource(R.string.sitzplan_neu),
            startName = stringResource(R.string.sitzplan_standardname),
            startSpalten = 6,
            startReihen = 4,
            startDoppeltische = true,
            onAbbrechen = { neuDialog = false },
            onOk = { n, s, r, d -> neuDialog = false; onPlanAnlegen(n, s, r, d) },
        )
    }
    if (bearbeitenDialog && plan != null) {
        PlanDialog(
            titel = stringResource(R.string.sitzplan_bearbeiten),
            startName = plan.name,
            startSpalten = plan.spalten,
            startReihen = plan.reihen,
            startDoppeltische = plan.doppeltische,
            onAbbrechen = { bearbeitenDialog = false },
            onOk = { n, s, r, d -> bearbeitenDialog = false; onPlanAendern(n, s, r, d) },
        )
    }
    if (loeschenFrage && plan != null) {
        BestaetigenDialog(
            titel = stringResource(R.string.sitzplan_loeschen),
            text = stringResource(R.string.sitzplan_loeschen_frage, plan.name),
            bestaetigenText = stringResource(R.string.loeschen),
            onBestaetigen = { loeschenFrage = false; onPlanLoeschen() },
            onAbbrechen = { loeschenFrage = false },
        )
    }
}

/** Long-Press startet den Drag. [anker] liefert die Root-Position des Elements, damit Fingerpositionen absolut sind. */
private fun Modifier.dragQuelle(
    schueler: Schueler,
    anker: () -> Offset,
    onDrag: (Drag) -> Unit,
    onDrop: (Schueler, Offset) -> Unit,
): Modifier = pointerInput(schueler.id) {
    var aktuell = Offset.Zero
    detectDragGesturesAfterLongPress(
        onDragStart = { lokal -> aktuell = anker() + lokal; onDrag(Drag(schueler, aktuell)) },
        onDrag = { change, delta -> change.consume(); aktuell += delta; onDrag(Drag(schueler, aktuell)) },
        onDragEnd = { onDrop(schueler, aktuell) },
        onDragCancel = { onDrop(schueler, Offset(-1f, -1f)) },
    )
}

@Composable
private fun UnplatziertLeiste(
    schueler: List<Schueler>,
    ausgewaehlt: Long?,
    fotoStore: FotoStore,
    onTipp: (Schueler) -> Unit,
    onDrag: (Drag) -> Unit,
    onDrop: (Schueler, Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(vertical = 8.dp)) {
        Text(
            if (schueler.isEmpty()) stringResource(R.string.sitzplan_alle_platziert) else stringResource(R.string.sitzplan_unplatziert, schueler.size),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(schueler, key = { it.id }) { s ->
                var anker by remember { mutableStateOf(Offset.Zero) }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (s.id == ausgewaehlt) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                        .clickable { onTipp(s) }
                        .onGloballyPositioned { anker = it.boundsInRoot().topLeft }
                        .dragQuelle(s, { anker }, onDrag, onDrop)
                        .padding(6.dp),
                ) {
                    SchuelerFoto(
                        datei = s.fotoDatei?.let(fotoStore::datei),
                        beschreibung = s.vollerName,
                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)),
                    )
                    Text(s.anzeigeName, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun PlanDialog(
    titel: String,
    startName: String,
    startSpalten: Int,
    startReihen: Int,
    startDoppeltische: Boolean,
    onAbbrechen: () -> Unit,
    onOk: (String, Int, Int, Boolean) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(startName) }
    var doppeltische by rememberSaveable { mutableStateOf(startDoppeltische) }
    var spalten by rememberSaveable { mutableStateOf(startSpalten.toString()) }
    var reihen by rememberSaveable { mutableStateOf(startReihen.toString()) }
    val s = spalten.toIntOrNull()
    val r = reihen.toIntOrNull()
    val gueltig = s != null && r != null && s in 1..SitzplanRepository.MAX_SPALTEN && r in 1..SitzplanRepository.MAX_REIHEN
    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text(titel) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.sitzplan_name)) }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = spalten, onValueChange = { spalten = it.filter(Char::isDigit).take(2) }, label = { Text(stringResource(R.string.sitzplan_spalten)) }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = reihen, onValueChange = { reihen = it.filter(Char::isDigit).take(2) }, label = { Text(stringResource(R.string.sitzplan_reihen)) }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.sitzplan_doppeltische), modifier = Modifier.weight(1f))
                    Switch(checked = doppeltische, onCheckedChange = { doppeltische = it })
                }
                Text(
                    stringResource(R.string.sitzplan_max, SitzplanRepository.MAX_SPALTEN, SitzplanRepository.MAX_REIHEN),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = gueltig, onClick = { onOk(name, s ?: 1, r ?: 1, doppeltische) }) { Text(stringResource(R.string.speichern)) }
        },
        dismissButton = { TextButton(onClick = onAbbrechen) { Text(stringResource(R.string.abbrechen)) } },
    )
}
