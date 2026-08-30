package de.namio.feature.schueler

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import de.namio.core.model.Schueler
import de.namio.core.repository.SchuelerRepository
import de.namio.ui.navigation.Route
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FotoAufnahmeUiState(
    val speichert: Boolean = false,
    val fertig: Boolean = false,
    val fehler: Boolean = false,
    val frontkamera: Boolean = false,
    /** Fotorunde: das Kind, das gerade dran ist; null im Einzelmodus. */
    val aktuell: Schueler? = null,
    val runde: Boolean = false,
    val position: Int = 0,
    val gesamt: Int = 0,
)

/**
 * Einzelmodus (schuelerId) oder Fotorunde (klasseId): In der Runde bleibt die Kamera offen,
 * oben steht das nächste Kind ohne Foto; nach jedem Foto geht es automatisch weiter.
 */
@HiltViewModel
class FotoAufnahmeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SchuelerRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Route.FotoAufnahme>()
    private val einzelId: Long = route.schuelerId
    private val uebersprungen = MutableStateFlow<Set<Long>>(emptySet())

    private val _uiState = MutableStateFlow(FotoAufnahmeUiState(runde = route.klasseId >= 0))
    val uiState: StateFlow<FotoAufnahmeUiState> = _uiState.asStateFlow()

    init {
        if (route.klasseId >= 0) {
            viewModelScope.launch {
                var gesamt = -1
                combine(repository.observeFuerKlasse(route.klasseId), uebersprungen) { liste, skip ->
                    val ohneFoto = liste.filter { it.fotoDatei == null }
                    if (gesamt < 0) gesamt = ohneFoto.size
                    Triple(ohneFoto.firstOrNull { it.id !in skip }, gesamt, gesamt - ohneFoto.size + skip.size + 1)
                }.collect { (naechstes, gesamt, position) ->
                    _uiState.update {
                        it.copy(aktuell = naechstes, gesamt = gesamt, position = position, fertig = naechstes == null)
                    }
                }
            }
        }
    }

    private val zielId: Long? get() = if (route.klasseId >= 0) _uiState.value.aktuell?.id else einzelId

    fun kameraWechseln() = _uiState.update { it.copy(frontkamera = !it.frontkamera) }

    fun fehlerGesehen() = _uiState.update { it.copy(fehler = false) }

    fun ueberspringen() {
        val id = _uiState.value.aktuell?.id ?: return
        uebersprungen.update { it + id }
    }

    /** Speichert JPEG-Bytes aus CameraX. Frontkamera-Bilder werden gespiegelt, damit sie wie im Sucher aussehen. */
    fun speichere(jpegBytes: ByteArray, rotationGrad: Int) {
        val id = zielId ?: return
        speichereMit { repository.fotoSetzen(id, jpegBytes, rotationGrad, spiegeln = _uiState.value.frontkamera) }
    }

    fun speichereAusUri(uri: android.net.Uri) {
        val id = zielId ?: return
        speichereMit { repository.fotoSetzen(id, uri) }
    }

    private fun speichereMit(aktion: suspend () -> Unit) {
        if (_uiState.value.speichert) return
        _uiState.update { it.copy(speichert = true) }
        viewModelScope.launch {
            runCatching { aktion() }
                .onSuccess { _uiState.update { it.copy(speichert = false, fertig = !it.runde) } }
                .onFailure { _uiState.update { it.copy(speichert = false, fehler = true) } }
        }
    }
}
