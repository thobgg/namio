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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import de.namio.core.transfer.ExportFormat
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
    val transfer by viewModel.transferStatus.collectAsStateWithLifecycle()
    var passwortFuer by remember { mutableStateOf<TransferArt?>(null) }
    var gewaehlteUri by remember { mutableStateOf<Uri?>(null) }
    val dateiname = remember { "namio-" + java.time.LocalDate.now() + ExportFormat.DATEIENDUNG }
    val exportZiel = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) { gewaehlteUri = uri; passwortFuer = TransferArt.EXPORT }
    }
    val importQuelle = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { gewaehlteUri = uri; passwortFuer = TransferArt.IMPORT }
    }
    EinstellungenInhalt(
        state, onZurueck, viewModel::appSperre, viewModel::blickrichtung, viewModel::neueKarten, viewModel::allesLoeschen,
        onExport = { exportZiel.launch(dateiname) },
        onImport = { importQuelle.launch(arrayOf("*/*")) },
        transfer = transfer,
        onTransferQuittieren = viewModel::transferQuittieren,
    )
    passwortFuer?.let { art ->
        PasswortDialog(
            art = art,
            onAbbrechen = { passwortFuer = null },
            onOk = { pw ->
                val uri = gewaehlteUri
                passwortFuer = null
                if (uri != null) { if (art == TransferArt.EXPORT) viewModel.exportieren(uri, pw) else viewModel.importieren(uri, pw) }
            },
        )
    }
}

enum class TransferArt { EXPORT, IMPORT }

@Composable
private fun PasswortDialog(art: TransferArt, onAbbrechen: () -> Unit, onOk: (String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    val export = art == TransferArt.EXPORT
    val gueltig = pw.length >= 6 && (!export || pw == pw2)
    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text(stringResource(if (export) R.string.export_titel else R.string.import_titel)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(if (export) R.string.export_hinweis else R.string.import_hinweis), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(value = pw, onValueChange = { pw = it }, label = { Text(stringResource(R.string.transfer_passwort)) }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                if (export) OutlinedTextField(value = pw2, onValueChange = { pw2 = it }, label = { Text(stringResource(R.string.transfer_passwort_wiederholen)) }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                Text(stringResource(R.string.transfer_passwort_regel), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(enabled = gueltig, onClick = { onOk(pw) }) { Text(stringResource(if (export) R.string.export_starten else R.string.import_starten)) } },
        dismissButton = { TextButton(onClick = onAbbrechen) { Text(stringResource(R.string.abbrechen)) } },
    )
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
    onExport: () -> Unit = {},
    onImport: () -> Unit = {},
    transfer: TransferStatus? = null,
    onTransferQuittieren: () -> Unit = {},
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
                    Text(stringResource(R.string.einstellungen_transfer_hinweis), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.export_titel)) }
                        OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.import_titel)) }
                    }
                    Button(onClick = { loeschenFrage = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.einstellungen_alles_loeschen))
                    }
                } }
            }
        }
    }
    when (transfer) {
        TransferStatus.Laeuft -> AlertDialog(onDismissRequest = {}, confirmButton = {}, text = { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(); Text(stringResource(R.string.transfer_laeuft), modifier = Modifier.padding(start = 16.dp)) } })
        is TransferStatus.Exportiert -> AlertDialog(onDismissRequest = onTransferQuittieren, confirmButton = { TextButton(onClick = onTransferQuittieren) { Text("OK") } }, text = { Text(pluralStringResource(R.plurals.export_fertig, transfer.schueler, transfer.schueler)) })
        is TransferStatus.Importiert -> AlertDialog(onDismissRequest = onTransferQuittieren, confirmButton = { TextButton(onClick = onTransferQuittieren) { Text("OK") } }, text = { Text(pluralStringResource(R.plurals.import_fertig, transfer.schueler, transfer.schueler)) })
        TransferStatus.FalschesPasswort -> AlertDialog(onDismissRequest = onTransferQuittieren, confirmButton = { TextButton(onClick = onTransferQuittieren) { Text("OK") } }, text = { Text(stringResource(R.string.import_falsches_passwort)) })
        TransferStatus.Fehler -> AlertDialog(onDismissRequest = onTransferQuittieren, confirmButton = { TextButton(onClick = onTransferQuittieren) { Text("OK") } }, text = { Text(stringResource(R.string.transfer_fehler)) })
        null -> Unit
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
