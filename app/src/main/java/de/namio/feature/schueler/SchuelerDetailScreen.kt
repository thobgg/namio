package de.namio.feature.schueler

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import de.namio.ui.components.INHALT_MAX_BREITE
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.namio.R
import de.namio.core.media.FotoStore
import de.namio.core.model.QuizModus
import de.namio.feature.klassen.rememberFotoStore
import de.namio.ui.components.BestaetigenDialog
import de.namio.ui.components.GeschlechtAuswahl
import de.namio.ui.components.modusName
import de.namio.ui.components.SchuelerFoto

@Composable
fun SchuelerDetailScreen(
    onZurueck: () -> Unit,
    onFotoAufnehmen: (Long) -> Unit,
    viewModel: SchuelerDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val fotoStore = rememberFotoStore()
    val galerie = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.fotoAusUri(uri)
    }

    LaunchedEffect(state.geloescht) {
        if (state.geloescht) onZurueck()
    }

    SchuelerDetailInhalt(
        state = state,
        fotoStore = fotoStore,
        onZurueck = onZurueck,
        onFormular = viewModel::formularAendern,
        onSpeichern = viewModel::speichern,
        onFotoAufnehmen = { onFotoAufnehmen(viewModel.schuelerId) },
        onFotoAusGalerie = {
            galerie.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onFotoEntfernen = viewModel::fotoEntfernen,
        onAvatar = viewModel::avatarWaehlen,
        onLoeschen = viewModel::loeschen,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SchuelerDetailInhalt(
    state: SchuelerDetailUiState,
    fotoStore: FotoStore,
    onZurueck: () -> Unit,
    onFormular: ((SchuelerFormular) -> SchuelerFormular) -> Unit,
    onSpeichern: () -> Unit,
    onFotoAufnehmen: () -> Unit,
    onFotoAusGalerie: () -> Unit,
    onFotoEntfernen: () -> Unit,
    onAvatar: (String) -> Unit,
    onLoeschen: () -> Unit,
) {
    var loeschenFrage by rememberSaveable { mutableStateOf(false) }
    var avatarWahl by rememberSaveable { mutableStateOf(false) }
    val schueler = state.schueler

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(schueler?.vollerName ?: stringResource(R.string.schueler_titel)) },
                navigationIcon = {
                    IconButton(onClick = onZurueck) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.zurueck))
                    }
                },
                actions = {
                    if (state.geaendert) {
                        TextButton(onClick = onSpeichern) { Text(stringResource(R.string.speichern)) }
                    }
                    IconButton(onClick = { loeschenFrage = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.schueler_loeschen))
                    }
                },
            )
        },
    ) { innen ->
        if (schueler == null) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innen)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Column(
            Modifier.widthIn(max = INHALT_MAX_BREITE).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            SchuelerFoto(
                datei = schueler.fotoDatei?.let(fotoStore::datei),
                beschreibung = stringResource(R.string.schueler_foto_von, schueler.vollerName),
                modifier = Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(24.dp)),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onFotoAufnehmen) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.foto_aufnehmen))
                }
                OutlinedButton(onClick = onFotoAusGalerie) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.foto_aus_galerie))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { avatarWahl = true }) {
                    Icon(Icons.Default.Face, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.avatar_waehlen))
                }
                if (schueler.fotoDatei != null) {
                    TextButton(onClick = onFotoEntfernen) { Text(stringResource(R.string.foto_entfernen)) }
                }
            }
            Spacer(Modifier.height(16.dp))

            val f = state.formular
            OutlinedTextField(
                value = f.vorname,
                onValueChange = { v -> onFormular { it.copy(vorname = v) } },
                label = { Text(stringResource(R.string.schueler_vorname)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = f.nachname,
                onValueChange = { v -> onFormular { it.copy(nachname = v) } },
                label = { Text(stringResource(R.string.schueler_nachname)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            GeschlechtAuswahl(
                gewaehlt = f.geschlecht,
                onWahl = { g -> onFormular { it.copy(geschlecht = g) } },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            OutlinedTextField(
                value = f.spitzname,
                onValueChange = { v -> onFormular { it.copy(spitzname = v) } },
                label = { Text(stringResource(R.string.schueler_spitzname)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = f.notiz,
                onValueChange = { v -> onFormular { it.copy(notiz = v) } },
                label = { Text(stringResource(R.string.schueler_notiz)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.lernstand_titel),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            QuizModus.entries.forEach { modus ->
                val karte = state.lernstand.firstOrNull { it.modus == modus }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(modusName(modus), modifier = Modifier.weight(1f))
                    Text(
                        if (karte != null) stringResource(R.string.lernstand_box, karte.box)
                        else stringResource(R.string.lernstand_neu),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
          }
        }
    }

    if (avatarWahl) {
        AvatarDialog(
            fotoStore = fotoStore,
            onAbbrechen = { avatarWahl = false },
            onGewaehlt = {
                avatarWahl = false
                onAvatar(it)
            },
        )
    }
    if (loeschenFrage && schueler != null) {
        BestaetigenDialog(
            titel = stringResource(R.string.schueler_loeschen),
            text = stringResource(R.string.schueler_loeschen_frage, schueler.vollerName),
            bestaetigenText = stringResource(R.string.loeschen),
            onBestaetigen = {
                loeschenFrage = false
                onLoeschen()
            },
            onAbbrechen = { loeschenFrage = false },
        )
    }
}

@Composable
private fun AvatarDialog(
    fotoStore: FotoStore,
    onAbbrechen: () -> Unit,
    onGewaehlt: (String) -> Unit,
) {
    val avatare = remember(fotoStore) { fotoStore.avatare() }
    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text(stringResource(R.string.avatar_waehlen)) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(72.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 420.dp),
            ) {
                items(avatare, key = { it }) { name ->
                    AsyncImage(
                        model = fotoStore.avatarUri(name),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button) { onGewaehlt(name) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onAbbrechen) { Text(stringResource(R.string.abbrechen)) }
        },
    )
}
