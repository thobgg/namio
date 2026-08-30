package de.namio.feature.quiz

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import de.namio.core.model.Klasse
import de.namio.core.model.QuizModus
import de.namio.core.repository.KlassenRepository
import de.namio.core.repository.QuizRepository
import de.namio.ui.navigation.Route
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class QuizAuswahlUiState(
    val klasse: Klasse? = null,
    val faellig: Map<QuizModus, Int> = emptyMap(),
    /** Modi, die in dieser Version spielbar sind. */
    val verfuegbar: Set<QuizModus> = QuizModus.entries.toSet(),
    val laedt: Boolean = true,
)

@HiltViewModel
class QuizAuswahlViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    klassenRepository: KlassenRepository,
    quizRepository: QuizRepository,
) : ViewModel() {

    val klasseId = savedStateHandle.toRoute<Route.QuizAuswahl>().klasseId

    val uiState: StateFlow<QuizAuswahlUiState> = combine(
        klassenRepository.observe(klasseId),
        quizRepository.observeFaelligProModus(klasseId),
    ) { klasse, faellig ->
        QuizAuswahlUiState(klasse = klasse, faellig = faellig, laedt = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuizAuswahlUiState())
}
