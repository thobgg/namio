package de.namio.feature.csvimport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.namio.R
import de.namio.core.csv.Zuordnung
import de.namio.core.model.Geschlecht
import de.namio.ui.components.GeschlechtAuswahl
import de.namio.ui.components.INHALT_MAX_BREITE
import de.namio.ui.components.geschlechtName

@Composable
fun CsvImportScreen(
    onZurueck: () -> Unit,
    viewModel: CsvImportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) viewModel.dateiLaden(uri) }
    CsvImportInhalt(
        state = state,
        onZurueck = onZurueck,
        onDateiWaehlen = { picker.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/csv", "*/*")) },
        onZuordnung = viewModel::zuordnungAendern,
        onStandardGeschlecht = viewModel::standardGeschlecht,
        onImportieren = viewModel::importieren,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CsvImportInhalt(
    state: CsvImportUiState,
    onZurueck: () -> Unit,
    onDateiWaehlen: () -> Unit,
    onZuordnung: ((Zuordnung) -> Zuordnung) -> Unit,
    onStandardGeschlecht: (Geschlecht) -> Unit,
    onImportieren: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.csv_titel)) },
                navigationIcon = { IconButton(onClick = onZurueck) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.zurueck)) } },
            )
        },
    ) { innen ->
        Box(Modifier.fillMaxSize().padding(innen), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = INHALT_MAX_BREITE).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val t = state.tabelle
                if (state.importiert != null) {
                    Text(pluralStringResource(R.plurals.csv_importiert, state.importiert, state.importiert), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Button(onClick = onZurueck, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ergebnis_fertig)) }
                    return@Column
                }
                Text(stringResource(R.string.csv_hinweis), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onDateiWaehlen, enabled = !state.laedt, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.UploadFile, contentDescription = null); Spacer(Modifier.padding(4.dp)); Text(stringResource(R.string.csv_datei_waehlen))
                }
                if (state.laedt) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                if (state.fehler) Text(stringResource(R.string.csv_fehler), color = MaterialTheme.colorScheme.error)
                if (t != null) {
                    Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.csv_erkannt, t.zeilen.size, t.zeichensatz, if (t.trennzeichen == '\t') "Tab" else t.trennzeichen.toString()), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.csv_zuordnung), style = MaterialTheme.typography.titleMedium)
                        SpaltenWahl(stringResource(R.string.schueler_vorname), t.spalten, state.zuordnung.vorname) { i -> onZuordnung { it.copy(vorname = i, kombiniert = if (i != null) null else it.kombiniert) } }
                        SpaltenWahl(stringResource(R.string.schueler_nachname), t.spalten, state.zuordnung.nachname) { i -> onZuordnung { it.copy(nachname = i, kombiniert = if (i != null) null else it.kombiniert) } }
                        SpaltenWahl(stringResource(R.string.csv_kombiniert), t.spalten, state.zuordnung.kombiniert) { i -> onZuordnung { it.copy(kombiniert = i, vorname = if (i != null) null else it.vorname, nachname = if (i != null) null else it.nachname) } }
                        SpaltenWahl(stringResource(R.string.csv_geschlecht_spalte), t.spalten, state.zuordnung.geschlecht) { i -> onZuordnung { it.copy(geschlecht = i) } }
                        Text(stringResource(R.string.csv_geschlecht_standard), style = MaterialTheme.typography.bodyMedium)
                        GeschlechtAuswahl(state.standardGeschlecht, onStandardGeschlecht, Modifier.fillMaxWidth())
                    } }
                    Card { Column(Modifier.padding(16.dp)) {
                        Text(pluralStringResource(R.plurals.csv_vorschau, state.vorschau.size, state.vorschau.size), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        state.vorschau.take(8).forEach { s ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text("${s.vorname} ${s.nachname}".trim(), modifier = Modifier.weight(1f))
                                Text(geschlechtName(s.geschlecht ?: state.standardGeschlecht), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HorizontalDivider()
                        }
                        if (state.vorschau.size > 8) Text(stringResource(R.string.csv_und_weitere, state.vorschau.size - 8), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    } }
                    Button(onClick = onImportieren, enabled = state.vorschau.isNotEmpty() && !state.laedt, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text(pluralStringResource(R.plurals.csv_importieren, state.vorschau.size, state.vorschau.size))
                    }
                }
            }
        }
    }
}

@Composable
private fun SpaltenWahl(label: String, spalten: List<String>, gewaehlt: Int?, onWahl: (Int?) -> Unit) {
    var offen by remember { mutableStateOf(false) }
    val keine = stringResource(R.string.csv_keine_spalte)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Box {
            OutlinedButton(onClick = { offen = true }) { Text(gewaehlt?.let { spalten.getOrNull(it) } ?: keine) }
            DropdownMenu(expanded = offen, onDismissRequest = { offen = false }) {
                DropdownMenuItem(text = { Text(keine) }, onClick = { offen = false; onWahl(null) })
                spalten.forEachIndexed { i, s -> DropdownMenuItem(text = { Text(s) }, onClick = { offen = false; onWahl(i) }) }
            }
        }
    }
}
