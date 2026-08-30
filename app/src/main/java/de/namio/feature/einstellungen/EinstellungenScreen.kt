package de.namio.feature.einstellungen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.namio.R
import de.namio.core.model.Blickrichtung
import de.namio.ui.components.BestaetigenDialog
import de.namio.ui.components.INHALT_MAX_BREITE

@Composable
fun EinstellungenScreen(onZurueck: () -> Unit, viewModel: EinstellungenViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    EinstellungenInhalt(state, onZurueck, viewModel::appSperre, viewModel::blickrichtung, viewModel::neueKarten, viewModel::allesLoeschen)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EinstellungenInhalt(
    state: EinstellungenUiState,
    onZurueck: () -> Unit,
    onSperre: (Boolean) -> Unit,
    onBlickrichtung: (Blickrichtung) -> Unit,
    onNeueKarten: (Int) -> Unit,
    onAllesLoeschen: () -> Unit,
) {
    var loeschenFrage by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.einstellungen_titel)) },
                navigationIcon = { IconButton(onClick = onZurueck) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.zurueck)) } },
            )
        },
    ) { innen ->
        Box(Modifier.fillMaxSize().padding(innen), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = INHALT_MAX_BREITE).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.einstellungen_sperre), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.einstellungen_sperre_hinweis), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = state.appSperre, onCheckedChange = onSperre)
                    }
                } }
                Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.einstellungen_blickrichtung), style = MaterialTheme.typography.titleMedium)
                    val optionen = listOf(Blickrichtung.VON_VORN to R.string.blickrichtung_vorn, Blickrichtung.VON_HINTEN to R.string.blickrichtung_hinten)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        optionen.forEachIndexed { i, (b, label) ->
                            SegmentedButton(selected = state.blickrichtung == b, onClick = { onBlickrichtung(b) }, shape = SegmentedButtonDefaults.itemShape(i, optionen.size)) { Text(stringResource(label)) }
                        }
                    }
                } }
                Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.einstellungen_neue_karten, state.neueKarten), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.einstellungen_neue_karten_hinweis), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(value = state.neueKarten.toFloat(), onValueChange = { onNeueKarten(it.toInt()) }, valueRange = 1f..15f, steps = 13)
                } }
                Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.einstellungen_daten), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.einstellungen_export_bald), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { loeschenFrage = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.einstellungen_alles_loeschen))
                    }
                } }
            }
        }
    }
    if (loeschenFrage) {
        BestaetigenDialog(
            titel = stringResource(R.string.einstellungen_alles_loeschen),
            text = stringResource(R.string.einstellungen_alles_loeschen_frage),
            bestaetigenText = stringResource(R.string.loeschen),
            onBestaetigen = { loeschenFrage = false; onAllesLoeschen() },
            onAbbrechen = { loeschenFrage = false },
        )
    }
}
