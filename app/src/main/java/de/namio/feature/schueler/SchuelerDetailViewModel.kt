package de.namio.feature.schueler

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import de.namio.core.model.Geschlecht
import de.namio.core.model.Lernkarte
import de.namio.core.model.Schueler
import de.namio.core.repository.SchuelerRepository
import de.namio.ui.navigation.Route
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SchuelerFormular(
    val vorname: String = "",
    val nachname: String = "",
    val spitzname: String = "",
    val notiz: String = "",
    val geschlecht: Geschlecht = Geschlecht.MAEDCHEN,
)

data class SchuelerDetailUiState(
    val schueler: Schueler? = null,
    val formular: SchuelerFormular = SchuelerFormular(),
    val lernstand: List<Lernkarte> = emptyList(),
    val geaendert: Boolean = false,
    val laedt: Boolean = true,
    /** true, sobald der Schüler gelöscht wurde – der Screen soll sich schließen. */
    val geloescht: Boolean = false,
)

@HiltViewModel
class SchuelerDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SchuelerRepository,
) : ViewModel() {

    val schuelerId = savedStateHandle.toRoute<Route.SchuelerDetail>().schuelerId

    private val formular = MutableStateFlow<SchuelerFormular?>(null)
    private val geloescht = MutableStateFlow(false)

    val uiState: StateFlow<SchuelerDetailUiState> = combine(
        repository.observe(schuelerId),
        repository.observeLernstand(schuelerId),
        formular,
        geloescht,
    ) { schueler, lernstand, eingabe, weg ->
        val gespeichert = schueler?.let {
            SchuelerFormular(it.vorname, it.nachname, it.spitzname, it.notiz, it.geschlecht)
        } ?: SchuelerFormular()
        val aktuell = eingabe ?: gespeichert
        SchuelerDetailUiState(
            schueler = schueler,
            formular = aktuell,
            lernstand = lernstand,
            geaendert = aktuell != gespeichert,
            laedt = false,
            geloescht = weg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SchuelerDetailUiState())

    fun formularAendern(transform: (SchuelerFormular) -> SchuelerFormular) {
        formular.update { transform(it ?: uiState.value.formular) }
    }

    fun speichern() {
        val schueler = uiState.value.schueler ?: return
        val f = uiState.value.formular
        viewModelScope.launch {
            repository.aktualisieren(
                schueler.copy(vorname = f.vorname, nachname = f.nachname, spitzname = f.spitzname, notiz = f.notiz, geschlecht = f.geschlecht),
            )
            formular.value = null
        }
    }

    fun fotoAusUri(uri: android.net.Uri) {
        viewModelScope.launch { runCatching { repository.fotoSetzen(schuelerId, uri) } }
    }

    fun avatarWaehlen(name: String) {
        viewModelScope.launch { runCatching { repository.avatarSetzen(schuelerId, name) } }
    }

    fun fotoEntfernen() {
        viewModelScope.launch { repository.fotoEntfernen(schuelerId) }
    }

    fun loeschen() {
        viewModelScope.launch {
            repository.loeschen(schuelerId)
            geloescht.value = true
        }
    }
}
