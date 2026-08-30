package de.namio.feature.klassen

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import de.namio.ui.components.INHALT_MAX_BREITE
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.namio.R
import de.namio.core.model.KlasseUebersicht
import de.namio.ui.components.BestaetigenDialog

@Composable
fun KlassenListeScreen(
    onKlasseOeffnen: (Long) -> Unit,
    onEinstellungen: () -> Unit,
    viewModel: KlassenListeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    KlassenListeInhalt(
        state = state,
        onKlasseOeffnen = onKlasseOeffnen,
        onEinstellungen = onEinstellungen,
        onErinnerungQuittieren = viewModel::erinnerungQuittieren,
        onAnlegen = viewModel::anlegen,
        onLoeschen = viewModel::loeschen,
    )
    if (state.hinweisZeigen && !state.laedt) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.hinweis_titel)) },
            text = { Text(stringResource(R.string.hinweis_text)) },
            confirmButton = { TextButton(onClick = viewModel::hinweisBestaetigen) { Text(stringResource(R.string.hinweis_verstanden)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KlassenListeInhalt(
    state: KlassenListeUiState,
    onKlasseOeffnen: (Long) -> Unit,
    onEinstellungen: () -> Unit,
    onAnlegen: (String, String, String) -> Unit,
    onLoeschen: (Long) -> Unit,
    onErinnerungQuittieren: () -> Unit = {},
) {
    var dialogOffen by rememberSaveable { mutableStateOf(false) }
    var zuLoeschen by remember { mutableStateOf<KlasseUebersicht?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.klassen_titel)) },
                actions = { IconButton(onClick = onEinstellungen) { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.einstellungen_titel)) } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { dialogOffen = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.klasse_anlegen))
            }
        },
    ) { innen ->
        if (!state.laedt && state.klassen.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innen).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.klassen_leer),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innen.calculateTopPadding() + 8.dp,
                    bottom = innen.calculateBottomPadding() + 88.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.schuljahresende) {
                    item(key = "erinnerung") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                            modifier = Modifier.widthIn(max = INHALT_MAX_BREITE).fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(stringResource(R.string.erinnerung_titel), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.erinnerung_text), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = onErinnerungQuittieren) { Text(stringResource(R.string.erinnerung_ok)) }
                                }
                            }
                        }
                    }
                }
                items(state.klassen, key = { it.klasse.id }) { eintrag ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        KlassenKarte(
                            eintrag = eintrag,
                            onKlick = { onKlasseOeffnen(eintrag.klasse.id) },
                            onLoeschen = { zuLoeschen = eintrag },
                        )
                    }
                }
            }
        }
    }

    if (dialogOffen) {
        KlasseAnlegenDialog(
            onAbbrechen = { dialogOffen = false },
            onAnlegen = { name, schule, jahrgang ->
                onAnlegen(name, schule, jahrgang)
                dialogOffen = false
            },
        )
    }
    zuLoeschen?.let { eintrag ->
        BestaetigenDialog(
            titel = stringResource(R.string.klasse_loeschen),
            text = stringResource(R.string.klasse_loeschen_frage, eintrag.klasse.name),
            bestaetigenText = stringResource(R.string.loeschen),
            onBestaetigen = {
                onLoeschen(eintrag.klasse.id)
                zuLoeschen = null
            },
            onAbbrechen = { zuLoeschen = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KlassenKarte(
    eintrag: KlasseUebersicht,
    onKlick: () -> Unit,
    onLoeschen: () -> Unit,
) {
    var menueOffen by remember { mutableStateOf(false) }
    val vorlesen = stringResource(R.string.klasse_vorlesen, eintrag.klasse.name, eintrag.schuelerAnzahl, eintrag.fortschrittProzent)
    Card(
        modifier = Modifier
            .widthIn(max = INHALT_MAX_BREITE)
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = vorlesen }
            .combinedClickable(role = Role.Button, onClick = onKlick, onLongClick = { menueOffen = true }),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(eintrag.klasse.name, style = MaterialTheme.typography.titleLarge)
                    val untertitel = listOf(eintrag.klasse.schule, eintrag.klasse.jahrgang)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                    if (untertitel.isNotBlank()) {
                        Text(untertitel, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Box {
                    IconButton(onClick = { menueOffen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.mehr))
                    }
                    DropdownMenu(expanded = menueOffen, onDismissRequest = { menueOffen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.klasse_loeschen)) },
                            onClick = {
                                menueOffen = false
                                onLoeschen()
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { eintrag.fortschrittProzent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Row {
                Text(
                    stringResource(R.string.klasse_schueler_anzahl, eintrag.schuelerAnzahl),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.klasse_fortschritt, eintrag.fortschrittProzent),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun KlasseAnlegenDialog(
    onAbbrechen: () -> Unit,
    onAnlegen: (String, String, String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var schule by rememberSaveable { mutableStateOf("") }
    var jahrgang by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text(stringResource(R.string.klasse_anlegen)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.klasse_name)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = schule,
                    onValueChange = { schule = it },
                    label = { Text(stringResource(R.string.klasse_schule)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = jahrgang,
                    onValueChange = { jahrgang = it },
                    label = { Text(stringResource(R.string.klasse_jahrgang)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onAnlegen(name, schule, jahrgang) },
            ) { Text(stringResource(R.string.anlegen)) }
        },
        dismissButton = {
            TextButton(onClick = onAbbrechen) { Text(stringResource(R.string.abbrechen)) }
        },
    )
}
