package de.namio.feature.klassen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.namio.core.model.KlasseUebersicht
import de.namio.core.repository.DemoDaten
import de.namio.core.repository.EinstellungenRepository
import de.namio.core.repository.KlassenRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class KlassenListeUiState(
    val klassen: List<KlasseUebersicht> = emptyList(),
    val laedt: Boolean = true,
    val hinweisZeigen: Boolean = false,
)

@HiltViewModel
class KlassenListeViewModel @Inject constructor(
    private val repository: KlassenRepository,
    private val einstellungen: EinstellungenRepository,
    demoDaten: DemoDaten,
) : ViewModel() {

    fun hinweisBestaetigen() { viewModelScope.launch { einstellungen.hinweisBestaetigen() } }

    init {
        viewModelScope.launch { runCatching { demoDaten.anlegenFallsErsterStart() } }
    }

    val uiState: StateFlow<KlassenListeUiState> = combine(repository.observeUebersicht(), einstellungen.hinweisBestaetigt) { klassen, bestaetigt ->
        KlassenListeUiState(klassen = klassen, laedt = false, hinweisZeigen = !bestaetigt)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KlassenListeUiState())

    fun anlegen(name: String, schule: String, jahrgang: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.anlegen(name, schule, jahrgang) }
    }

    fun loeschen(klasseId: Long) {
        viewModelScope.launch { repository.loeschen(klasseId) }
    }
}
