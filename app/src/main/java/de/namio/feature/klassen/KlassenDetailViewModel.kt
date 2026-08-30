package de.namio.feature.klassen

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import de.namio.core.model.Geschlecht
import de.namio.core.model.Klasse
import de.namio.core.model.Schueler
import de.namio.core.repository.KlassenRepository
import de.namio.core.repository.SchuelerRepository
import de.namio.ui.navigation.Route
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class KlassenDetailUiState(
    val klasse: Klasse? = null,
    val schueler: List<Schueler> = emptyList(),
    val laedt: Boolean = true,
)

@HiltViewModel
class KlassenDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    klassenRepository: KlassenRepository,
    private val schuelerRepository: SchuelerRepository,
) : ViewModel() {

    private val klasseId = savedStateHandle.toRoute<Route.KlassenDetail>().klasseId

    val uiState: StateFlow<KlassenDetailUiState> = combine(
        klassenRepository.observe(klasseId),
        schuelerRepository.observeFuerKlasse(klasseId),
    ) { klasse, schueler ->
        KlassenDetailUiState(klasse = klasse, schueler = schueler, laedt = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KlassenDetailUiState())

    /** Legt einen Schüler an; [onAngelegt] bekommt die neue ID. */
    fun schuelerAnlegen(vorname: String, nachname: String, geschlecht: Geschlecht, onAngelegt: (Long) -> Unit) {
        if (vorname.isBlank() && nachname.isBlank()) return
        viewModelScope.launch {
            onAngelegt(schuelerRepository.anlegen(klasseId, vorname, nachname, geschlecht))
        }
    }
}
