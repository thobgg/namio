package de.namio.feature.klassen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.namio.core.model.KlasseUebersicht
import de.namio.core.repository.KlassenRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class KlassenListeUiState(
    val klassen: List<KlasseUebersicht> = emptyList(),
    val laedt: Boolean = true,
)

@HiltViewModel
class KlassenListeViewModel @Inject constructor(
    private val repository: KlassenRepository,
) : ViewModel() {

    val uiState: StateFlow<KlassenListeUiState> = repository.observeUebersicht()
        .map { KlassenListeUiState(klassen = it, laedt = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KlassenListeUiState())

    fun anlegen(name: String, schule: String, jahrgang: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.anlegen(name, schule, jahrgang) }
    }

    fun loeschen(klasseId: Long) {
        viewModelScope.launch { repository.loeschen(klasseId) }
    }
}
