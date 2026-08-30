package de.namio.feature.einstellungen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.namio.core.model.Blickrichtung
import de.namio.core.repository.EinstellungenRepository
import de.namio.core.repository.KlassenRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EinstellungenUiState(
    val appSperre: Boolean = true,
    val blickrichtung: Blickrichtung = Blickrichtung.VON_VORN,
    val neueKarten: Int = 5,
    val laedt: Boolean = true,
)

@HiltViewModel
class EinstellungenViewModel @Inject constructor(
    private val einstellungen: EinstellungenRepository,
    private val klassenRepository: KlassenRepository,
) : ViewModel() {
    val uiState: StateFlow<EinstellungenUiState> = combine(einstellungen.appSperre, einstellungen.blickrichtung, einstellungen.neueKartenProRunde) { s, b, n ->
        EinstellungenUiState(s, b, n, laedt = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EinstellungenUiState())

    fun appSperre(an: Boolean) = viewModelScope.launch { einstellungen.setzeAppSperre(an) }
    fun blickrichtung(b: Blickrichtung) = viewModelScope.launch { einstellungen.setzeBlickrichtung(b) }
    fun neueKarten(n: Int) = viewModelScope.launch { einstellungen.setzeNeueKartenProRunde(n) }
    fun allesLoeschen() = viewModelScope.launch { klassenRepository.allesLoeschen() }
}
