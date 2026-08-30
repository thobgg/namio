package de.namio.feature.sitzplan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
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
import de.namio.core.model.Schueler
import de.namio.core.model.SitzplanVorlage
import de.namio.core.model.Sitzplatz
import de.namio.core.repository.SitzplanRepository
import de.namio.core.sitzplan.SitzplanLogik
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
        aktionen = SitzplanAktionen(
            planWaehlen = viewModel::planWaehlen,
            planAnlegen = viewModel::planAnlegen,
            planAendern = viewModel::planAendern,
            planLoeschen = viewModel::planLoeschen,
            alsStandard = viewModel::alsStandard,
            ablegen = viewModel::ablegen,
            verschieben = viewModel::verschieben,
            drehen = viewModel::drehen,
            entfernen = viewModel::entfernen,
            platzLoeschen = viewModel::platzLoeschen,
            leererStuhl = viewModel::leererStuhl,
            partnerplatz = viewModel::partnerplatz,
            beschriften = viewModel::beschriften,
            mischen = viewModel::mischen,
            blickrichtung = viewModel::blickrichtungUmschalten,
        ),
    )
}

/** Alle Editor-Aktionen gebündelt, damit die Composables schlank bleiben. */
data class SitzplanAktionen(
    val planWaehlen: (Long) -> Unit,
    val planAnlegen: (String, Int, Int, SitzplanVorlage, Boolean) -> Unit,
    val planAendern: (String, Int, Int, Boolean) -> Unit,
    val planLoeschen: () -> Unit,
    val alsStandard: () -> Unit,
    val ablegen: (Long, Float, Float) -> Unit,
    val verschieben: (Long, Float, Float) -> Unit,
    val drehen: (Long, Float) -> Unit,
    val entfernen: (Long) -> Unit,
    val platzLoeschen: (Long) -> Unit,
    val leererStuhl: (Float, Float) -> Unit,
    val partnerplatz: (Long) -> Unit,
    val beschriften: (Long, String) -> Unit,
    val mischen: () -> Unit,
    val blickrichtung: () -> Unit,
)

/** Laufender Drag: was wird gezogen, wo ist der Finger (Root-Koordinaten). */
private sealed interface Drag {
    val position: Offset
    data class Schueler(val schueler: de.namio.core.model.Schueler, override val position: Offset) : Drag
    data class Platz(val platz: Sitzplatz, val schueler: de.namio.core.model.Schueler?, override val position: Offset) : Drag
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SitzplanInhalt(
    state: SitzplanUiState,
    fotoStore: FotoStore,
    onZurueck: () -> Unit,
    aktionen: SitzplanAktionen,
) {
    var neuDialog by rememberSaveable { mutableStateOf(false) }
    var bearbeitenDialog by rememberSaveable { mutableStateOf(false) }
    var loeschenFrage by rememberSaveable { mutableStateOf(false) }
    var beschriftenDialog by rememberSaveable { mutableStateOf(false) }
    var menueOffen by remember { mutableStateOf(false) }
    var ausgewaehlterPlatz by remember { mutableStateOf<Long?>(null) }
    var ausgewaehlterSchueler by remember { mutableStateOf<Long?>(null) }
    var drag by remember { mutableStateOf<Drag?>(null) }
    var flaeche by remember { mutableStateOf<Rect?>(null) }
    var leiste by remember { mutableStateOf<Rect?>(null) }
    var wurzel by remember { mutableStateOf(Offset.Zero) }
    val plan = state.aktiv

    /** Root-Punkt → normierte Raumkoordinate (Modell), oder null außerhalb der Fläche. */
    fun raumKoordinate(punkt: Offset): Pair<Float, Float>? {
        val f = flaeche ?: return null
        if (!f.contains(punkt)) return null
        val ax = (punkt.x - f.left) / f.width
        val ay = (punkt.y - f.top) / f.height
        return SitzplanLogik.modellKoordinate(ax, ay, state.blickrichtung)
    }

    fun ablegen(d: Drag) {
        val raum = raumKoordinate(d.position)
        when (d) {
            is Drag.Schueler -> when {
                raum != null -> aktionen.ablegen(d.schueler.id, raum.first, raum.second)
                leiste?.contains(d.position) == true -> aktionen.entfernen(d.schueler.id)
            }
            is Drag.Platz -> when {
                raum != null -> {
                    val ziel = plan?.let { SitzplanLogik.platzBei(state.plaetze.filter { p -> p.id != d.platz.id }, raum.first, raum.second, it.spalten, it.reihen) }
                    if (ziel != null && d.schueler != null) aktionen.ablegen(d.schueler.id, raum.first, raum.second)
                    else aktionen.verschieben(d.platz.id, raum.first, raum.second)
                }
                leiste?.contains(d.position) == true && d.schueler != null -> aktionen.entfernen(d.schueler.id)
            }
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
                        IconButton(onClick = aktionen.blickrichtung) {
                            Icon(Icons.Default.Cameraswitch, contentDescription = stringResource(R.string.sitzplan_blickrichtung))
                        }
                        IconButton(onClick = aktionen.mischen) {
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
                                    DropdownMenuItem(text = { Text(stringResource(R.string.sitzplan_als_standard)) }, onClick = { menueOffen = false; aktionen.alsStandard() })
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
                val gewaehlt = state.plaetze.firstOrNull { it.id == ausgewaehlterPlatz }
                Column(Modifier.fillMaxSize()) {
                    if (state.plaene.size > 1) {
                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.plaene, key = { it.id }) { p ->
                                FilterChip(selected = p.id == plan.id, onClick = { aktionen.planWaehlen(p.id) }, label = { Text(p.name) })
                            }
                        }
                    }
                    Text(
                        stringResource(
                            when {
                                ausgewaehlterSchueler != null -> R.string.sitzplan_hinweis_platz
                                gewaehlt != null -> R.string.sitzplan_hinweis_gewaehlt
                                else -> R.string.sitzplan_hinweis
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
                        SitzplanFlaeche(
                            plan = plan,
                            plaetze = state.plaetze,
                            schuelerProId = state.schuelerProId,
                            blickrichtung = state.blickrichtung,
                            fotoStore = fotoStore,
                            modifier = Modifier.widthIn(max = 900.dp).padding(8.dp),
                            rasterZeigen = plan.einrasten,
                            markierung = { if (it.id == ausgewaehlterPlatz) PlatzMarkierung.AUSGEWAEHLT else PlatzMarkierung.KEINE },
                            flaechenModifier = Modifier
                                .onGloballyPositioned { flaeche = it.boundsInRoot() }
                                .pointerInput(plan.id, ausgewaehlterSchueler, state.blickrichtung) {
                                    detectTapGestures { lokal ->
                                        val f = flaeche ?: return@detectTapGestures
                                        val raum = raumKoordinate(f.topLeft + lokal) ?: return@detectTapGestures
                                        val s = ausgewaehlterSchueler
                                        if (s != null) {
                                            aktionen.ablegen(s, raum.first, raum.second)
                                            ausgewaehlterSchueler = null
                                        } else if (ausgewaehlterPlatz != null) {
                                            ausgewaehlterPlatz = null
                                        } else {
                                            aktionen.leererStuhl(raum.first, raum.second)
                                        }
                                    }
                                },
                            platzModifier = { platz ->
                                val sitzender = platz.schuelerId?.let(state.schuelerProId::get)
                                Modifier
                                    .pointerInput(platz.id, ausgewaehlterSchueler) {
                                        detectTapGestures {
                                            val s = ausgewaehlterSchueler
                                            if (s != null && !platz.istMoebel) {
                                                aktionen.ablegen(s, platz.x, platz.y)
                                                ausgewaehlterSchueler = null
                                            } else {
                                                ausgewaehlterPlatz = if (ausgewaehlterPlatz == platz.id) null else platz.id
                                            }
                                        }
                                    }
                                    .dragQuelle(
                                        key = platz.id,
                                        anker = {
                                            val f = flaeche ?: Rect.Zero
                                            val a = SitzplanLogik.anzeige(platz, state.blickrichtung)
                                            val g = f.width / plan.spalten * 0.92f
                                            Offset(f.left + f.width * a.x - g / 2, f.top + f.height * a.y - g / 2)
                                        },
                                        start = { Drag.Platz(platz, sitzender, it) },
                                        onDrag = { drag = it },
                                        onDrop = { drag = null; ablegen(it) },
                                    )
                            },
                        )
                    }
                    if (gewaehlt != null) {
                        PlatzWerkzeuge(
                            platz = gewaehlt,
                            onDrehen = { aktionen.drehen(gewaehlt.id, it) },
                            onPartner = { aktionen.partnerplatz(gewaehlt.id) },
                            onBeschriften = { beschriftenDialog = true },
                            onSchuelerEntfernen = { gewaehlt.schuelerId?.let(aktionen.entfernen) },
                            onLoeschen = { aktionen.platzLoeschen(gewaehlt.id); ausgewaehlterPlatz = null },
                        )
                    }
                    UnplatziertLeiste(
                        schueler = state.unplatziert,
                        ausgewaehlt = ausgewaehlterSchueler,
                        fotoStore = fotoStore,
                        onTipp = { ausgewaehlterSchueler = if (ausgewaehlterSchueler == it.id) null else it.id; ausgewaehlterPlatz = null },
                        onDrag = { drag = it },
                        onDrop = { drag = null; ablegen(it) },
                        modifier = Modifier.onGloballyPositioned { leiste = it.boundsInRoot() },
                    )
                }
            }
            drag?.let { d ->
                val halb = with(LocalDensity.current) { 36.dp.toPx() }
                val schueler = when (d) { is Drag.Schueler -> d.schueler; is Drag.Platz -> d.schueler }
                Box(
                    Modifier
                        .zIndex(10f)
                        .offset { IntOffset((d.position.x - wurzel.x - halb).roundToInt(), (d.position.y - wurzel.y - halb).roundToInt()) }
                        .size(72.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    if (schueler != null) {
                        SchuelerFoto(datei = schueler.fotoDatei?.let(fotoStore::datei), beschreibung = schueler.vollerName, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)))
                    }
                }
            }
        }
    }

    if (neuDialog) {
        NeuerPlanDialog(
            onAbbrechen = { neuDialog = false },
            onOk = { n, s, r, v, b -> neuDialog = false; aktionen.planAnlegen(n, s, r, v, b) },
        )
    }
    if (bearbeitenDialog && plan != null) {
        PlanBearbeitenDialog(
            plan = plan.name, spalten = plan.spalten, reihen = plan.reihen, einrasten = plan.einrasten,
            onAbbrechen = { bearbeitenDialog = false },
            onOk = { n, s, r, e -> bearbeitenDialog = false; aktionen.planAendern(n, s, r, e) },
        )
    }
    val zuBeschriften = state.plaetze.firstOrNull { it.id == ausgewaehlterPlatz }
    if (beschriftenDialog && zuBeschriften != null) {
        BeschriftenDialog(
            start = zuBeschriften.beschriftung ?: "",
            onAbbrechen = { beschriftenDialog = false },
            onOk = { beschriftenDialog = false; aktionen.beschriften(zuBeschriften.id, it) },
        )
    }
    if (loeschenFrage && plan != null) {
        BestaetigenDialog(
            titel = stringResource(R.string.sitzplan_loeschen),
            text = stringResource(R.string.sitzplan_loeschen_frage, plan.name),
            bestaetigenText = stringResource(R.string.loeschen),
            onBestaetigen = { loeschenFrage = false; aktionen.planLoeschen() },
            onAbbrechen = { loeschenFrage = false },
        )
    }
}

/** Long-Press startet den Drag. [anker] liefert die Root-Position des Elements, damit Fingerpositionen absolut sind. */
private fun Modifier.dragQuelle(
    key: Any,
    anker: () -> Offset,
    start: (Offset) -> Drag,
    onDrag: (Drag) -> Unit,
    onDrop: (Drag) -> Unit,
): Modifier = pointerInput(key) {
    var aktuell = Offset.Zero
    var laufend: Drag? = null
    detectDragGesturesAfterLongPress(
        onDragStart = { lokal -> aktuell = anker() + lokal; laufend = start(aktuell); onDrag(laufend!!) },
        onDrag = { change, delta ->
            change.consume()
            aktuell += delta
            laufend = when (val l = laufend) {
                is Drag.Schueler -> l.copy(position = aktuell)
                is Drag.Platz -> l.copy(position = aktuell)
                null -> null
            }
            laufend?.let(onDrag)
        },
        onDragEnd = { laufend?.let(onDrop) },
        onDragCancel = { laufend?.let { onDrop(when (it) { is Drag.Schueler -> it.copy(position = Offset(-1f, -1f)); is Drag.Platz -> it.copy(position = Offset(-1f, -1f)) }) } },
    )
}

@Composable
private fun PlatzWerkzeuge(
    platz: Sitzplatz,
    onDrehen: (Float) -> Unit,
    onPartner: () -> Unit,
    onBeschriften: () -> Unit,
    onSchuelerEntfernen: () -> Unit,
    onLoeschen: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onDrehen(-15f) }) { Icon(Icons.Default.RotateLeft, contentDescription = stringResource(R.string.sitzplan_drehen_links)) }
        Text("${platz.drehung.roundToInt()}°", style = MaterialTheme.typography.labelMedium)
        IconButton(onClick = { onDrehen(15f) }) { Icon(Icons.Default.RotateRight, contentDescription = stringResource(R.string.sitzplan_drehen_rechts)) }
        if (!platz.istMoebel) {
            IconButton(onClick = onPartner) { Icon(Icons.Default.GroupAdd, contentDescription = stringResource(R.string.sitzplan_partnerplatz)) }
        }
        if (platz.schuelerId == null) {
            IconButton(onClick = onBeschriften) { Icon(Icons.Default.Label, contentDescription = stringResource(R.string.sitzplan_beschriften)) }
        }
        if (platz.schuelerId != null) {
            IconButton(onClick = onSchuelerEntfernen) { Icon(Icons.Default.PersonRemove, contentDescription = stringResource(R.string.sitzplan_schueler_entfernen)) }
        }
        IconButton(onClick = onLoeschen) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.sitzplan_platz_loeschen)) }
    }
}

@Composable
private fun BeschriftenDialog(start: String, onAbbrechen: () -> Unit, onOk: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(start) }
    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text(stringResource(R.string.sitzplan_beschriften)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = text, onValueChange = { text = it.take(20) }, label = { Text(stringResource(R.string.sitzplan_beschriftung)) }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(R.string.moebel_pult, R.string.moebel_pc, R.string.moebel_schrank, R.string.moebel_tuer).forEach { id ->
                        val n = stringResource(id)
                        FilterChip(selected = text == n, onClick = { text = n }, label = { Text(n) })
                    }
                }
                Text(stringResource(R.string.sitzplan_beschriften_hinweis), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onOk(text) }) { Text(stringResource(R.string.speichern)) } },
        dismissButton = { TextButton(onClick = onAbbrechen) { Text(stringResource(R.string.abbrechen)) } },
    )
}

@Composable
private fun UnplatziertLeiste(
    schueler: List<Schueler>,
    ausgewaehlt: Long?,
    fotoStore: FotoStore,
    onTipp: (Schueler) -> Unit,
    onDrag: (Drag) -> Unit,
    onDrop: (Drag) -> Unit,
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
                        .dragQuelle(key = s.id, anker = { anker }, start = { Drag.Schueler(s, it) }, onDrag = onDrag, onDrop = onDrop)
                        .padding(6.dp),
                ) {
                    SchuelerFoto(datei = s.fotoDatei?.let(fotoStore::datei), beschreibung = s.vollerName, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)))
                    Text(s.anzeigeName, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun vorlageName(v: SitzplanVorlage): String = stringResource(
    when (v) {
        SitzplanVorlage.LEER -> R.string.vorlage_leer
        SitzplanVorlage.DOPPELTISCH_REIHEN -> R.string.vorlage_doppeltische
        SitzplanVorlage.U_FORM -> R.string.vorlage_u_form
        SitzplanVorlage.GRUPPENTISCHE -> R.string.vorlage_gruppen
    },
)

@Composable
private fun NeuerPlanDialog(
    onAbbrechen: () -> Unit,
    onOk: (String, Int, Int, SitzplanVorlage, Boolean) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var spalten by rememberSaveable { mutableStateOf(SitzplanRepository.STANDARD_SPALTEN.toString()) }
    var reihen by rememberSaveable { mutableStateOf(SitzplanRepository.STANDARD_REIHEN.toString()) }
    var vorlage by rememberSaveable { mutableStateOf(SitzplanVorlage.DOPPELTISCH_REIHEN) }
    var vorbelegen by rememberSaveable { mutableStateOf(true) }
    val standardName = stringResource(R.string.sitzplan_standardname)
    val s = spalten.toIntOrNull()
    val r = reihen.toIntOrNull()
    val gueltig = s != null && r != null && s in SitzplanRepository.MIN_EINHEITEN..SitzplanRepository.MAX_EINHEITEN && r in SitzplanRepository.MIN_EINHEITEN..SitzplanRepository.MAX_EINHEITEN
    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text(stringResource(R.string.sitzplan_neu)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.sitzplan_name)) }, placeholder = { Text(standardName) }, singleLine = true)
                RaumFelder(spalten, reihen, { spalten = it }, { reihen = it })
                Text(stringResource(R.string.sitzplan_vorlage), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    SitzplanVorlage.entries.forEach { v ->
                        FilterChip(selected = v == vorlage, onClick = { vorlage = v }, label = { Text(vorlageName(v)) })
                    }
                }
                if (vorlage != SitzplanVorlage.LEER) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.sitzplan_vorbelegen), modifier = Modifier.weight(1f))
                        Switch(checked = vorbelegen, onCheckedChange = { vorbelegen = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = gueltig, onClick = { onOk(name.ifBlank { standardName }, s ?: 12, r ?: 9, vorlage, vorbelegen) }) { Text(stringResource(R.string.anlegen)) }
        },
        dismissButton = { TextButton(onClick = onAbbrechen) { Text(stringResource(R.string.abbrechen)) } },
    )
}

@Composable
private fun PlanBearbeitenDialog(
    plan: String,
    spalten: Int,
    reihen: Int,
    einrasten: Boolean,
    onAbbrechen: () -> Unit,
    onOk: (String, Int, Int, Boolean) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(plan) }
    var sp by rememberSaveable { mutableStateOf(spalten.toString()) }
    var re by rememberSaveable { mutableStateOf(reihen.toString()) }
    var rast by rememberSaveable { mutableStateOf(einrasten) }
    val s = sp.toIntOrNull()
    val r = re.toIntOrNull()
    val gueltig = s != null && r != null && s in SitzplanRepository.MIN_EINHEITEN..SitzplanRepository.MAX_EINHEITEN && r in SitzplanRepository.MIN_EINHEITEN..SitzplanRepository.MAX_EINHEITEN
    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text(stringResource(R.string.sitzplan_bearbeiten)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.sitzplan_name)) }, singleLine = true)
                RaumFelder(sp, re, { sp = it }, { re = it })
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.sitzplan_einrasten), modifier = Modifier.weight(1f))
                    Switch(checked = rast, onCheckedChange = { rast = it })
                }
            }
        },
        confirmButton = { TextButton(enabled = gueltig, onClick = { onOk(name, s ?: spalten, r ?: reihen, rast) }) { Text(stringResource(R.string.speichern)) } },
        dismissButton = { TextButton(onClick = onAbbrechen) { Text(stringResource(R.string.abbrechen)) } },
    )
}

@Composable
private fun RaumFelder(spalten: String, reihen: String, onSpalten: (String) -> Unit, onReihen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = spalten, onValueChange = { onSpalten(it.filter(Char::isDigit).take(2)) }, label = { Text(stringResource(R.string.sitzplan_breite)) }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(value = reihen, onValueChange = { onReihen(it.filter(Char::isDigit).take(2)) }, label = { Text(stringResource(R.string.sitzplan_tiefe)) }, singleLine = true, modifier = Modifier.weight(1f))
        }
        Text(
            stringResource(R.string.sitzplan_raum_hinweis, SitzplanRepository.MIN_EINHEITEN, SitzplanRepository.MAX_EINHEITEN),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
