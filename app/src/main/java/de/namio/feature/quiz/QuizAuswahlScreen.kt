package de.namio.feature.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.namio.R
import de.namio.core.model.QuizModus
import de.namio.ui.components.modusName

@Composable
fun QuizAuswahlScreen(
    onZurueck: () -> Unit,
    onStarten: (klasseId: Long, modus: QuizModus) -> Unit,
    viewModel: QuizAuswahlViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    QuizAuswahlInhalt(
        state = state,
        onZurueck = onZurueck,
        onStarten = { onStarten(viewModel.klasseId, it) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuizAuswahlInhalt(
    state: QuizAuswahlUiState,
    onZurueck: () -> Unit,
    onStarten: (QuizModus) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.quiz_titel, state.klasse?.name ?: "")) },
                navigationIcon = {
                    IconButton(onClick = onZurueck) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.zurueck))
                    }
                },
            )
        },
    ) { innen ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 220.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innen.calculateTopPadding() + 8.dp,
                bottom = innen.calculateBottomPadding() + 16.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(QuizModus.entries, key = { it.name }) { modus ->
                val verfuegbar = modus in state.verfuegbar
                val faellig = state.faellig[modus] ?: 0
                Card(
                    onClick = { onStarten(modus) },
                    enabled = verfuegbar,
                    colors = if (verfuegbar) CardDefaults.cardColors() else CardDefaults.outlinedCardColors(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                modusName(modus),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            if (verfuegbar && faellig > 0) {
                                Badge { Text(faellig.toString()) }
                            }
                        }
                        Text(
                            when {
                                !verfuegbar -> stringResource(R.string.quiz_modus_bald)
                                faellig == 0 -> stringResource(R.string.quiz_nichts_faellig)
                                else -> pluralStringResource(R.plurals.quiz_faellig, faellig, faellig)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
