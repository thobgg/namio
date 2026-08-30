package de.namio.feature.einstellungen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.namio.core.model.Blickrichtung
import de.namio.core.repository.EinstellungenRepository
import de.namio.core.repository.KlassenRepository
import de.namio.core.repository.TransferRepository
import de.namio.core.transfer.Tresor
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

/** Ergebnis eines Exports/Imports für die Anzeige. */
sealed interface TransferStatus {
    data object Laeuft : TransferStatus
    data class Exportiert(val schueler: Int) : TransferStatus
    data class Importiert(val schueler: Int) : TransferStatus
    data object FalschesPasswort : TransferStatus
    data object Fehler : TransferStatus
}

@HiltViewModel
class EinstellungenViewModel @Inject constructor(
    private val einstellungen: EinstellungenRepository,
    private val klassenRepository: KlassenRepository,
    private val transfer: TransferRepository,
) : ViewModel() {
    private val _transfer = MutableStateFlow<TransferStatus?>(null)
    val transferStatus = _transfer.asStateFlow()

    fun exportieren(ziel: Uri, passwort: String) = viewModelScope.launch {
        _transfer.value = TransferStatus.Laeuft
        _transfer.value = runCatching { TransferStatus.Exportiert(transfer.exportieren(ziel, passwort.toCharArray())) }.getOrElse { TransferStatus.Fehler }
    }

    fun importieren(quelle: Uri, passwort: String) = viewModelScope.launch {
        _transfer.value = TransferStatus.Laeuft
        _transfer.value = runCatching { TransferStatus.Importiert(transfer.importieren(quelle, passwort.toCharArray())) }
            .getOrElse { if (it is Tresor.FalschesPasswortOderDatei) TransferStatus.FalschesPasswort else TransferStatus.Fehler }
    }

    fun transferQuittieren() { _transfer.value = null }
    val uiState: StateFlow<EinstellungenUiState> = combine(einstellungen.appSperre, einstellungen.blickrichtung, einstellungen.neueKartenProRunde) { s, b, n ->
        EinstellungenUiState(s, b, n, laedt = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EinstellungenUiState())

    fun appSperre(an: Boolean) = viewModelScope.launch { einstellungen.setzeAppSperre(an) }
    fun blickrichtung(b: Blickrichtung) = viewModelScope.launch { einstellungen.setzeBlickrichtung(b) }
    fun neueKarten(n: Int) = viewModelScope.launch { einstellungen.setzeNeueKartenProRunde(n) }
    fun allesLoeschen() = viewModelScope.launch { klassenRepository.allesLoeschen() }
}
