package de.namio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import de.namio.R
import androidx.compose.ui.unit.dp
import java.io.File

/** Maximale Inhaltsbreite für Listen und Formulare, damit Tablets keine Bandwurm-Zeilen zeigen. */
val INHALT_MAX_BREITE = 640.dp

/** Schülerfoto oder Platzhalter, quadratisch. */
@Composable
fun SchuelerFoto(
    datei: File?,
    beschreibung: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (datei != null) {
            AsyncImage(
                model = datei,
                contentDescription = beschreibung,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = stringResource(R.string.schueler_kein_foto),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxSize(0.5f),
            )
        }
    }
}

/** Ja/Nein-Dialog für destruktive Aktionen. */
@Composable
fun BestaetigenDialog(
    titel: String,
    text: String,
    bestaetigenText: String,
    onBestaetigen: () -> Unit,
    onAbbrechen: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text(titel) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onBestaetigen) {
                Text(bestaetigenText, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onAbbrechen) { Text(stringResource(R.string.abbrechen)) }
        },
    )
}
