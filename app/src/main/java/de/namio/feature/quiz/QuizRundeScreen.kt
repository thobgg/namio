package de.namio.feature.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
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
import de.namio.ui.components.EinstellungenEntryPoint
import dagger.hilt.android.EntryPointAccessors
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
                is QuizRundePhase.Frage ->
                    if (state.modus == QuizModus.SITZPLAN) SitzplanFrageInhalt(phase, fotoStore, onAntwort)
                    else FrageInhalt(phase, fotoStore, onAntwort)
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
                LinearProgressIndicator(progress = { phase.fortschritt }, modifier = Modifier.fillMaxWidth())
                Text(
                    stringResource(R.string.quiz_fortschritt, phase.erledigt, phase.gesamt),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
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
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            SitzplanFlaeche(
                plan = plan,
                plaetze = phase.plaetze,
                schuelerProId = schuelerProId,
                blickrichtung = blickrichtung,
                fotoStore = fotoStore,
                namenZeigen = false,
                modifier = Modifier.widthIn(max = 900.dp),
                markierung = { platz ->
                    val fb = phase.feedback
                    when {
                        fb == null -> PlatzMarkierung.KEINE
                        platz.schuelerId == ziel.id -> PlatzMarkierung.RICHTIG
                        platz.schuelerId == fb.gewaehltId -> PlatzMarkierung.FALSCH
                        else -> PlatzMarkierung.KEINE
                    }
                },
                platzModifier = { platz ->
                    val s = platz.schuelerId?.let(schuelerProId::get)
                    if (s != null && phase.feedback == null) Modifier.clickable { onAntwort(s) } else Modifier
                },
            )
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
        option.id == feedback.gewaehltId -> AntwortZustand.FALSCH
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
        Text(stringResource(R.string.ergebnis_titel), style = MaterialTheme.typography.headlineSmall)
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
            if (phase.fehler.isNotEmpty()) {
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
