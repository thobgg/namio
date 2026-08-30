package de.namio.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.namio.R
import de.namio.core.model.QuizModus

@Composable
fun modusName(modus: QuizModus): String = stringResource(
    when (modus) {
        QuizModus.FOTO_ZU_NAME_MC -> R.string.modus_foto_zu_name_mc
        QuizModus.FOTO_ZU_NAME_TIPPEN -> R.string.modus_foto_zu_name_tippen
        QuizModus.NAME_ZU_FOTO -> R.string.modus_name_zu_foto
        QuizModus.SITZPLAN -> R.string.modus_sitzplan
        QuizModus.SPEEDRUN -> R.string.modus_speedrun
    },
)
