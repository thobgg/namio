package de.namio.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.namio.R
import de.namio.core.model.Geschlecht

private val REIHENFOLGE = listOf(Geschlecht.MAEDCHEN, Geschlecht.JUNGE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeschlechtAuswahl(
    gewaehlt: Geschlecht,
    onWahl: (Geschlecht) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier) {
        REIHENFOLGE.forEachIndexed { index, g ->
            SegmentedButton(
                selected = g == gewaehlt,
                onClick = { onWahl(g) },
                shape = SegmentedButtonDefaults.itemShape(index, REIHENFOLGE.size),
            ) {
                Text(geschlechtName(g))
            }
        }
    }
}

@Composable
fun geschlechtName(g: Geschlecht): String = stringResource(
    when (g) {
        Geschlecht.MAEDCHEN -> R.string.geschlecht_maedchen
        Geschlecht.JUNGE -> R.string.geschlecht_junge
    },
)
