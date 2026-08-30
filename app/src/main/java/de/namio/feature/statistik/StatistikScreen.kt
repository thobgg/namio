package de.namio.feature.statistik

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.namio.R
import de.namio.core.media.FotoStore
import de.namio.core.model.QuizModus
import de.namio.core.model.SessionKurz
import de.namio.core.model.Verwechslung
import de.namio.feature.klassen.rememberFotoStore
import de.namio.ui.components.INHALT_MAX_BREITE
import de.namio.ui.components.SchuelerFoto
import de.namio.ui.components.modusName
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun StatistikScreen(
    onZurueck: () -> Unit,
    viewModel: StatistikViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StatistikInhalt(state = state, fotoStore = rememberFotoStore(), onZurueck = onZurueck, onModus = viewModel::modusWaehlen)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatistikInhalt(
    state: StatistikUiState,
    fotoStore: FotoStore,
    onZurueck: () -> Unit,
    onModus: (QuizModus) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.statistik_titel, state.klasse?.name ?: "")) },
                navigationIcon = { IconButton(onClick = onZurueck) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.zurueck)) } },
            )
        },
    ) { innen ->
        Box(Modifier.fillMaxSize().padding(innen), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = INHALT_MAX_BREITE).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    QuizModus.entries.filter { it != QuizModus.SPEEDRUN }.forEach { m ->
                        FilterChip(selected = m == state.modus, onClick = { onModus(m) }, label = { Text(modusName(m)) })
                    }
                }
                Card { Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.statistik_boxen), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Boxverteilung(state.boxen, state.schuelerAnzahl)
                } }
                Card { Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.statistik_verlauf), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    if (state.verlauf.isEmpty()) Text(stringResource(R.string.statistik_keine_runden), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else Verlauf(state.verlauf)
                } }
                Card { Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.statistik_verwechslungen), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    if (state.verwechslungen.isEmpty()) Text(stringResource(R.string.statistik_keine_verwechslungen), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else state.verwechslungen.forEach { VerwechslungZeile(it, fotoStore) }
                } }
            }
        }
    }
}

@Composable
private fun Boxverteilung(boxen: IntArray, gesamt: Int) {
    val labels = listOf(
        stringResource(R.string.statistik_ohne_karte),
        stringResource(R.string.statistik_box, 1), stringResource(R.string.statistik_box, 2),
        stringResource(R.string.statistik_box, 3), stringResource(R.string.statistik_box, 4), stringResource(R.string.statistik_box, 5),
    )
    val farben = listOf(
        MaterialTheme.colorScheme.outlineVariant, MaterialTheme.colorScheme.error, Color(0xFFE0A100),
        Color(0xFF8BC34A), Color(0xFF43A047), Color(0xFF1B5E20),
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        boxen.forEachIndexed { i, n ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(labels[i], modifier = Modifier.width(96.dp), style = MaterialTheme.typography.bodyMedium)
                Box(Modifier.weight(1f).height(18.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    val anteil = if (gesamt == 0) 0f else n.toFloat() / gesamt
                    Box(Modifier.fillMaxHeight().fillMaxWidth(anteil).background(farben[i]))
                }
                Text(n.toString(), modifier = Modifier.width(40.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Verlauf(sessions: List<SessionKurz>) {
    val gruen = Color(0xFF2E7D32)
    val hinter = MaterialTheme.colorScheme.surfaceVariant
    val format = DateTimeFormatter.ofPattern("d.M.")
    Column {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val n = sessions.size
            val luecke = 6.dp.toPx()
            val breite = (size.width - luecke * (n - 1)) / n
            sessions.forEachIndexed { i, s ->
                val x = i * (breite + luecke)
                drawRect(hinter, Offset(x, 0f), Size(breite, size.height))
                val h = size.height * s.prozent / 100f
                drawRect(gruen, Offset(x, size.height - h), Size(breite, h))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            val erste = sessions.first(); val letzte = sessions.last()
            Text(erste.startedAt.atZone(ZoneId.systemDefault()).format(format), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.statistik_letzte_quote, letzte.prozent), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
            Text(letzte.startedAt.atZone(ZoneId.systemDefault()).format(format), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun VerwechslungZeile(v: Verwechslung, fotoStore: FotoStore) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        SchuelerFoto(v.a.fotoDatei?.let(fotoStore::datei), v.a.vollerName, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
        Spacer(Modifier.width(8.dp))
        SchuelerFoto(v.b.fotoDatei?.let(fotoStore::datei), v.b.vollerName, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text("${v.a.vollerName} ↔ ${v.b.vollerName}", style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.statistik_mal, v.anzahl), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
