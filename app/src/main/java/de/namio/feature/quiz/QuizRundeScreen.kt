package de.namio.feature.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.namio.R
import de.namio.core.media.FotoStore
import de.namio.core.model.QuizFehler
import de.namio.core.model.Schueler
import de.namio.core.model.Blickrichtung
import de.namio.core.model.QuizModus
import de.namio.feature.sitzplan.PlatzMarkierung
import de.namio.feature.sitzplan.SitzplanFlaeche
import de.namio.feature.sitzplan.BASIS_BREITE
import de.namio.ui.components.EinstellungenEntryPoint
import dagger.hilt.android.EntryPointAccessors
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import de.namio.feature.klassen.rememberFotoStore
import de.namio.ui.components.INHALT_MAX_BREITE
import de.namio.ui.components.SchuelerFoto
import de.namio.ui.components.modusName

@Composable
fun QuizRundeScreen(
    onBeenden: () -> Unit,
    viewModel: QuizRundeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val fotoStore = rememberFotoStore()
    QuizRundeInhalt(
        state = state,
        fotoStore = fotoStore,
        onBeenden = onBeenden,
        onAntwort = viewModel::antworten,
        onAntwortText = viewModel::antwortenText,
        onFehlerWiederholen = viewModel::fehlerWiederholen,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuizRundeInhalt(
    state: QuizRundeUiState,
    fotoStore: FotoStore,
    onBeenden: () -> Unit,
    onAntwort: (Schueler) -> Unit,
    onAntwortText: (String) -> Unit,
    onFehlerWiederholen: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(modusName(state.modus)) },
                navigationIcon = {
                    IconButton(onClick = onBeenden) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.quiz_beenden))
                    }
                },
            )
        },
    ) { innen ->
        Box(Modifier.fillMaxSize().padding(innen), contentAlignment = Alignment.TopCenter) {
            when (val phase = state.phase) {
                QuizRundePhase.Laedt -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                QuizRundePhase.KeineKandidaten -> Text(
                    stringResource(R.string.quiz_keine_fotos),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
                QuizRundePhase.KeinSitzplan -> Text(
                    stringResource(R.string.quiz_sitzplan_kein_plan),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
                is QuizRundePhase.Frage -> when (state.modus) {
                    QuizModus.SITZPLAN -> SitzplanFrageInhalt(phase, fotoStore, onAntwort)
                    QuizModus.FOTO_ZU_NAME_TIPPEN -> TippenFrageInhalt(phase, fotoStore, onAntwortText)
                    QuizModus.NAME_ZU_FOTO -> NameZuFotoInhalt(phase, fotoStore, onAntwort)
                    else -> FrageInhalt(phase, fotoStore, onAntwort)
                }
                is QuizRundePhase.Ergebnis -> ErgebnisInhalt(phase, fotoStore, onFehlerWiederholen, onBeenden)
            }
        }
    }
}

@Composable
private fun FrageInhalt(
    phase: QuizRundePhase.Frage,
    fotoStore: FotoStore,
    onAntwort: (Schueler) -> Unit,
) {
    val ziel = phase.frage.ziel
    BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
        val querformat = maxWidth > maxHeight
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = if (querformat) Dp.Unspecified else INHALT_MAX_BREITE).fillMaxWidth()) {
                FortschrittsZeile(phase)
            }
            if (querformat) {
                Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        ZielFoto(ziel, fotoStore, Modifier.fillMaxHeight())
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                        modifier = Modifier.weight(1f).widthIn(max = INHALT_MAX_BREITE).padding(start = 24.dp),
                    ) {
                        Antworten(phase, ziel, onAntwort)
                    }
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ZielFoto(ziel, fotoStore, Modifier.fillMaxHeight().widthIn(max = INHALT_MAX_BREITE))
                }
                Spacer(Modifier.height(16.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.widthIn(max = INHALT_MAX_BREITE).fillMaxWidth(),
                ) {
                    Antworten(phase, ziel, onAntwort)
                }
            }
        }
    }
}

/** Fortschrittsbalken mit „x von y“ – im Speedrun stattdessen Restzeit und Trefferzahl. */
@Composable
private fun FortschrittsZeile(phase: QuizRundePhase.Frage) {
    val rest = phase.restSekunden
    if (rest != null) {
        LinearProgressIndicator(progress = { rest / 60f }, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp)) {
            Text(stringResource(R.string.speedrun_rest, rest), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.speedrun_treffer, phase.erledigt), style = MaterialTheme.typography.titleMedium)
        }
    } else {
        LinearProgressIndicator(progress = { phase.fortschritt }, modifier = Modifier.fillMaxWidth())
        Text(
            stringResource(R.string.quiz_fortschritt, phase.erledigt, phase.gesamt),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
    }
}

/** Tippen-Modus: Foto, Textfeld, Prüfen. */
@Composable
private fun TippenFrageInhalt(
    phase: QuizRundePhase.Frage,
    fotoStore: FotoStore,
    onAntwortText: (String) -> Unit,
) {
    val ziel = phase.frage.ziel
    var eingabe by remember(ziel.id) { mutableStateOf("") }
    val fokus = remember { FocusRequester() }
    LaunchedEffect(ziel.id) { fokus.requestFocus() }
    Column(
        modifier = Modifier.widthIn(max = INHALT_MAX_BREITE).fillMaxSize().imePadding().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FortschrittsZeile(phase)
        Box(Modifier.weight(1f, fill = false).fillMaxWidth().heightIn(max = 360.dp), contentAlignment = Alignment.Center) {
            ZielFoto(ziel, fotoStore, Modifier.fillMaxHeight().widthIn(max = 320.dp))
        }
        Spacer(Modifier.height(12.dp))
        val fb = phase.feedback
        if (fb == null) {
            OutlinedTextField(
                value = eingabe,
                onValueChange = { eingabe = it },
                label = { Text(stringResource(R.string.tippen_hinweis)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (eingabe.isNotBlank()) onAntwortText(eingabe) }),
                modifier = Modifier.fillMaxWidth().focusRequester(fokus),
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onAntwortText(eingabe) }, enabled = eingabe.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.tippen_pruefen))
            }
        } else {
            val farbe = if (fb.korrekt) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
            Text(
                if (fb.korrekt) stringResource(R.string.tippen_richtig, ziel.vollerName) else stringResource(R.string.tippen_falsch, fb.eingabe.orEmpty(), ziel.vollerName),
                color = farbe,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            )
        }
    }
}

/** Name → Foto: Name oben, Raster aus bis zu neun Gesichtern. */
@Composable
private fun NameZuFotoInhalt(
    phase: QuizRundePhase.Frage,
    fotoStore: FotoStore,
    onAntwort: (Schueler) -> Unit,
) {
    val ziel = phase.frage.ziel
    Column(
        modifier = Modifier.widthIn(max = INHALT_MAX_BREITE).fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FortschrittsZeile(phase)
        Text(ziel.vollerName, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            items(phase.frage.optionen, key = { it.id }) { option ->
                val zustand = antwortZustand(option, ziel, phase.feedback)
                val rahmen = when (zustand) {
                    AntwortZustand.RICHTIG -> Color(0xFF2E7D32)
                    AntwortZustand.FALSCH -> MaterialTheme.colorScheme.error
                    else -> Color.Transparent
                }
                SchuelerFoto(
                    datei = option.fotoDatei?.let(fotoStore::datei),
                    beschreibung = option.vollerName,
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .border(4.dp, rahmen, RoundedCornerShape(14.dp))
                        .alpha(if (zustand == AntwortZustand.GEDIMMT) 0.4f else 1f)
                        .then(if (phase.feedback == null) Modifier.clickable { onAntwort(option) } else Modifier),
                )
            }
        }
    }
}

/** Sitzplan-Modus: Name oben, der Plan darunter; Antwort durch Tippen auf den Platz. */
@Composable
private fun SitzplanFrageInhalt(
    phase: QuizRundePhase.Frage,
    fotoStore: FotoStore,
    onAntwort: (Schueler) -> Unit,
) {
    val plan = phase.sitzplan ?: return
    val ziel = phase.frage.ziel
    val schuelerProId = phase.frage.optionen.associateBy { it.id }
    val blickrichtung by rememberBlickrichtung()
    var zoomFaktor by rememberSaveable { mutableStateOf(1f) }
    val zoomGeste = rememberTransformableState { faktor, _, _ -> zoomFaktor = (zoomFaktor * faktor).coerceIn(0.5f, 3f) }
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        LinearProgressIndicator(progress = { phase.fortschritt }, modifier = Modifier.fillMaxWidth())
        Text(
            stringResource(R.string.quiz_fortschritt, phase.erledigt, phase.gesamt),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            stringResource(R.string.quiz_sitzplan_frage, ziel.vollerName),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
          val viewport = maxWidth
          val basis = viewport.coerceAtMost(BASIS_BREITE)
          val zoom = ((56.dp * plan.spalten) / basis).coerceAtLeast(1f) * zoomFaktor
          Box(Modifier.fillMaxSize().transformable(zoomGeste).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
           Box(Modifier.widthIn(min = viewport), contentAlignment = Alignment.TopCenter) {
            SitzplanFlaeche(
                plan = plan,
                bestuhlung = phase.bestuhlung,
                schuelerProId = schuelerProId,
                blickrichtung = blickrichtung,
                fotoStore = fotoStore,
                zoom = zoom,
                basisBreite = basis,
                namenZeigen = false,
                slotMarkierung = { platz ->
                    val fb = phase.feedback
                    when {
                        fb == null -> PlatzMarkierung.KEINE
                        platz.schuelerId == ziel.id -> PlatzMarkierung.RICHTIG
                        platz.schuelerId == fb.gewaehltId -> PlatzMarkierung.FALSCH
                        else -> PlatzMarkierung.KEINE
                    }
                },
                slotModifier = { _, platz ->
                    val s = platz.schuelerId?.let(schuelerProId::get)
                    if (s != null && phase.feedback == null) Modifier.clickable { onAntwort(s) } else Modifier
                },
            )
        }
           }
          }
    }
}

@Composable
private fun rememberBlickrichtung(): State<Blickrichtung> {
    val context = LocalContext.current.applicationContext
    val repo = remember(context) {
        EntryPointAccessors.fromApplication(context, EinstellungenEntryPoint::class.java).einstellungen()
    }
    return repo.blickrichtung.collectAsStateWithLifecycle(initialValue = Blickrichtung.VON_VORN)
}

@Composable
private fun ZielFoto(ziel: Schueler, fotoStore: FotoStore, modifier: Modifier) {
    SchuelerFoto(
        datei = ziel.fotoDatei?.let(fotoStore::datei),
        beschreibung = stringResource(R.string.quiz_foto_beschreibung),
        modifier = modifier
            .aspectRatio(1f, matchHeightConstraintsFirst = true)
            .clip(RoundedCornerShape(24.dp)),
    )
}

@Composable
private fun Antworten(phase: QuizRundePhase.Frage, ziel: Schueler, onAntwort: (Schueler) -> Unit) {
    phase.frage.optionen.forEach { option ->
        AntwortButton(
            text = option.vollerName,
            zustand = antwortZustand(option, ziel, phase.feedback),
            onClick = { onAntwort(option) },
        )
    }
}

private enum class AntwortZustand { NEUTRAL, RICHTIG, FALSCH, GEDIMMT }

private fun antwortZustand(option: Schueler, ziel: Schueler, feedback: Feedback?): AntwortZustand {
    if (feedback == null) return AntwortZustand.NEUTRAL
    return when {
        option.id == ziel.id -> AntwortZustand.RICHTIG
        feedback.gewaehltId != null && option.id == feedback.gewaehltId -> AntwortZustand.FALSCH
        else -> AntwortZustand.GEDIMMT
    }
}

@Composable
private fun AntwortButton(text: String, zustand: AntwortZustand, onClick: () -> Unit) {
    val farben = when (zustand) {
        AntwortZustand.NEUTRAL -> ButtonDefaults.filledTonalButtonColors()
        AntwortZustand.RICHTIG -> ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32), contentColor = Color.White)
        AntwortZustand.FALSCH -> ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
        AntwortZustand.GEDIMMT -> ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    }
    Button(
        onClick = onClick,
        colors = farben,
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ErgebnisInhalt(
    phase: QuizRundePhase.Ergebnis,
    fotoStore: FotoStore,
    onFehlerWiederholen: () -> Unit,
    onBeenden: () -> Unit,
) {
    val gesamt = phase.richtig + phase.falsch
    val quote = if (gesamt == 0) 0 else phase.richtig * 100 / gesamt
    Column(
        modifier = Modifier
            .widthIn(max = INHALT_MAX_BREITE)
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(if (phase.speedrun) R.string.ergebnis_speedrun_titel else R.string.ergebnis_titel), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.ergebnis_quote, quote),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(R.string.ergebnis_zaehler, phase.richtig, phase.falsch),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(16.dp))
        if (phase.fehler.isEmpty()) {
            Text(stringResource(R.string.ergebnis_keine_fehler), textAlign = TextAlign.Center)
        } else {
            Text(
                stringResource(R.string.ergebnis_fehler_titel),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(phase.fehler, key = { it.schueler.id }) { FehlerZeile(it, fotoStore) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (phase.fehler.isNotEmpty() && !phase.speedrun) {
                Button(onClick = onFehlerWiederholen) { Text(stringResource(R.string.ergebnis_fehler_wiederholen)) }
            }
            OutlinedButton(onClick = onBeenden) { Text(stringResource(R.string.ergebnis_fertig)) }
        }
    }
}

@Composable
private fun FehlerZeile(fehler: QuizFehler, fotoStore: FotoStore) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SchuelerFoto(
                datei = fehler.schueler.fotoDatei?.let(fotoStore::datei),
                beschreibung = fehler.schueler.vollerName,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
            )
            Column(Modifier.padding(start = 12.dp)) {
                Text(fehler.schueler.vollerName, style = MaterialTheme.typography.titleMedium)
                fehler.verwechseltMit?.let {
                    Text(
                        stringResource(R.string.ergebnis_verwechselt_mit, it.vollerName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        HorizontalDivider()
    }
}
