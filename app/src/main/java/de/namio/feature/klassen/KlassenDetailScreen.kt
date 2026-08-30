package de.namio.feature.klassen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.namio.R
import de.namio.core.media.FotoStore
import de.namio.core.model.Geschlecht
import de.namio.core.model.Schueler
import de.namio.ui.components.GeschlechtAuswahl
import de.namio.ui.components.SchuelerFoto
import dagger.hilt.android.EntryPointAccessors
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import de.namio.ui.components.FotoStoreEntryPoint

@Composable
fun KlassenDetailScreen(
    onZurueck: () -> Unit,
    onSchuelerOeffnen: (Long) -> Unit,
    onQuiz: (Long) -> Unit,
    onSitzplan: (Long) -> Unit,
    onStatistik: (Long) -> Unit,
    onCsvImport: (Long) -> Unit,
    viewModel: KlassenDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val fotoStore = rememberFotoStore()
    KlassenDetailInhalt(
        state = state,
        fotoStore = fotoStore,
        onZurueck = onZurueck,
        onSchuelerOeffnen = onSchuelerOeffnen,
        onSchuelerAnlegen = { vor, nach, g -> viewModel.schuelerAnlegen(vor, nach, g, onSchuelerOeffnen) },
        onQuiz = { state.klasse?.let { onQuiz(it.id) } },
        onSitzplan = { state.klasse?.let { onSitzplan(it.id) } },
        onStatistik = { state.klasse?.let { onStatistik(it.id) } },
        onCsvImport = { state.klasse?.let { onCsvImport(it.id) } },
    )
}

@Composable
fun rememberFotoStore(): FotoStore {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        EntryPointAccessors.fromApplication(context, FotoStoreEntryPoint::class.java).fotoStore()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KlassenDetailInhalt(
    state: KlassenDetailUiState,
    fotoStore: FotoStore,
    onZurueck: () -> Unit,
    onSchuelerOeffnen: (Long) -> Unit,
    onSchuelerAnlegen: (String, String, Geschlecht) -> Unit,
    onQuiz: () -> Unit,
    onSitzplan: () -> Unit,
    onStatistik: () -> Unit,
    onCsvImport: () -> Unit,
) {
    var dialogOffen by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.klasse?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onZurueck) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.zurueck))
                    }
                },
                actions = {
                    IconButton(onClick = onCsvImport) {
                        Icon(Icons.Default.UploadFile, contentDescription = stringResource(R.string.csv_titel))
                    }
                    if (state.schueler.isNotEmpty()) {
                        IconButton(onClick = onStatistik) {
                            Icon(Icons.Default.BarChart, contentDescription = stringResource(R.string.statistik_oeffnen))
                        }
                        IconButton(onClick = onSitzplan) {
                            Icon(Icons.Default.GridOn, contentDescription = stringResource(R.string.sitzplan_oeffnen))
                        }
                    }
                    if (state.schueler.any { it.fotoDatei != null }) {
                        TextButton(onClick = onQuiz) {
                            Icon(Icons.Default.School, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text(stringResource(R.string.quiz_starten))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { dialogOffen = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.schueler_hinzufuegen))
            }
        },
    ) { innen ->
        if (!state.laedt && state.schueler.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innen).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.schueler_leer),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = innen.calculateTopPadding() + 8.dp,
                    bottom = innen.calculateBottomPadding() + 88.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.schueler, key = { it.id }) { schueler ->
                    SchuelerKachel(
                        schueler = schueler,
                        fotoStore = fotoStore,
                        onKlick = { onSchuelerOeffnen(schueler.id) },
                    )
                }
            }
        }
    }

    if (dialogOffen) {
        SchuelerAnlegenDialog(
            onAbbrechen = { dialogOffen = false },
            onAnlegen = { vor, nach, g ->
                dialogOffen = false
                onSchuelerAnlegen(vor, nach, g)
            },
        )
    }
}

@Composable
private fun SchuelerKachel(schueler: Schueler, fotoStore: FotoStore, onKlick: () -> Unit) {
    Column(
        modifier = Modifier.clickable(onClick = onKlick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SchuelerFoto(
            datei = schueler.fotoDatei?.let(fotoStore::datei),
            beschreibung = stringResource(R.string.schueler_foto_von, schueler.vollerName),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp)),
        )
        Text(
            schueler.anzeigeName,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun SchuelerAnlegenDialog(
    onAbbrechen: () -> Unit,
    onAnlegen: (String, String, Geschlecht) -> Unit,
) {
    var vorname by rememberSaveable { mutableStateOf("") }
    var nachname by rememberSaveable { mutableStateOf("") }
    var geschlecht by rememberSaveable { mutableStateOf(Geschlecht.MAEDCHEN) }
    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text(stringResource(R.string.schueler_hinzufuegen)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = vorname,
                    onValueChange = { vorname = it },
                    label = { Text(stringResource(R.string.schueler_vorname)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = nachname,
                    onValueChange = { nachname = it },
                    label = { Text(stringResource(R.string.schueler_nachname)) },
                    singleLine = true,
                )
                GeschlechtAuswahl(geschlecht, onWahl = { geschlecht = it })
            }
        },
        confirmButton = {
            TextButton(
                enabled = vorname.isNotBlank() || nachname.isNotBlank(),
                onClick = { onAnlegen(vorname, nachname, geschlecht) },
            ) { Text(stringResource(R.string.anlegen)) }
        },
        dismissButton = {
            TextButton(onClick = onAbbrechen) { Text(stringResource(R.string.abbrechen)) }
        },
    )
}
