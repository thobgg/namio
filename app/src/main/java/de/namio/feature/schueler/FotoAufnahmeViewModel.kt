package de.namio.feature.schueler

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import de.namio.core.repository.SchuelerRepository
import de.namio.ui.navigation.Route
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FotoAufnahmeUiState(
    val speichert: Boolean = false,
    val fertig: Boolean = false,
    val fehler: Boolean = false,
    val frontkamera: Boolean = false,
)

@HiltViewModel
class FotoAufnahmeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SchuelerRepository,
) : ViewModel() {

    private val schuelerId = savedStateHandle.toRoute<Route.FotoAufnahme>().schuelerId

    private val _uiState = MutableStateFlow(FotoAufnahmeUiState())
    val uiState: StateFlow<FotoAufnahmeUiState> = _uiState.asStateFlow()

    fun kameraWechseln() = _uiState.update { it.copy(frontkamera = !it.frontkamera) }

    fun fehlerGesehen() = _uiState.update { it.copy(fehler = false) }

    /** Speichert JPEG-Bytes aus CameraX. Frontkamera-Bilder werden gespiegelt, damit sie wie im Sucher aussehen. */
    fun speichere(jpegBytes: ByteArray, rotationGrad: Int) {
        if (_uiState.value.speichert) return
        _uiState.update { it.copy(speichert = true) }
        viewModelScope.launch {
            runCatching {
                repository.fotoSetzen(schuelerId, jpegBytes, rotationGrad, spiegeln = _uiState.value.frontkamera)
            }.onSuccess {
                _uiState.update { it.copy(speichert = false, fertig = true) }
            }.onFailure {
                _uiState.update { it.copy(speichert = false, fehler = true) }
            }
        }
    }

    fun speichereAusUri(uri: android.net.Uri) {
        if (_uiState.value.speichert) return
        _uiState.update { it.copy(speichert = true) }
        viewModelScope.launch {
            runCatching { repository.fotoSetzen(schuelerId, uri) }
                .onSuccess { _uiState.update { it.copy(speichert = false, fertig = true) } }
                .onFailure { _uiState.update { it.copy(speichert = false, fehler = true) } }
        }
    }
}
