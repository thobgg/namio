package de.namio.feature.statistik

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import de.namio.core.model.Klasse
import de.namio.core.model.QuizModus
import de.namio.core.model.SessionKurz
import de.namio.core.model.Verwechslung
import de.namio.core.repository.KlassenRepository
import de.namio.core.repository.StatistikRepository
import de.namio.core.statistik.StatistikLogik
import de.namio.ui.navigation.Route
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class StatistikUiState(
    val klasse: Klasse? = null,
    val modus: QuizModus = QuizModus.FOTO_ZU_NAME_MC,
    /** Index 0 = ohne Karte, 1–5 = Box. */
    val boxen: IntArray = IntArray(6),
    val schuelerAnzahl: Int = 0,
    val verlauf: List<SessionKurz> = emptyList(),
    val verwechslungen: List<Verwechslung> = emptyList(),
    val laedt: Boolean = true,
)

@HiltViewModel
class StatistikViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    klassenRepository: KlassenRepository,
    private val repository: StatistikRepository,
) : ViewModel() {

    private val klasseId = savedStateHandle.toRoute<Route.Statistik>().klasseId
    private val modus = MutableStateFlow(QuizModus.FOTO_ZU_NAME_MC)

    val uiState: StateFlow<StatistikUiState> = combine(
        klassenRepository.observe(klasseId),
        repository.observeSchueler(klasseId),
        repository.observeKarten(klasseId),
        combine(repository.observeSessions(klasseId), repository.observeVerwechslungen(klasseId), modus) { s, v, m -> Triple(s, v, m) },
    ) { klasse, schueler, karten, (sessions, roh, m) ->
        StatistikUiState(
            klasse = klasse,
            modus = m,
            boxen = StatistikLogik.boxverteilung(karten, schueler.map { it.id }, m),
            schuelerAnzahl = schueler.size,
            verlauf = StatistikLogik.verlauf(sessions, m),
            verwechslungen = StatistikLogik.verwechslungsPaare(roh, schueler.associateBy { it.id }),
            laedt = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatistikUiState())

    fun modusWaehlen(m: QuizModus) { modus.value = m }
}
