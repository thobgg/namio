package de.namio.feature.csvimport

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.namio.core.csv.CsvParser
import de.namio.core.csv.CsvTabelle
import de.namio.core.csv.ImportSchueler
import de.namio.core.csv.Zuordnung
import de.namio.core.model.Geschlecht
import de.namio.core.repository.SchuelerRepository
import de.namio.ui.navigation.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class CsvImportUiState(
    val tabelle: CsvTabelle? = null,
    val zuordnung: Zuordnung = Zuordnung(null, null, null, null),
    /** Geschlecht für Zeilen ohne erkennbare Angabe. */
    val standardGeschlecht: Geschlecht = Geschlecht.MAEDCHEN,
    val vorschau: List<ImportSchueler> = emptyList(),
    val laedt: Boolean = false,
    val fehler: Boolean = false,
    val importiert: Int? = null,
)

@HiltViewModel
class CsvImportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val schuelerRepository: SchuelerRepository,
) : ViewModel() {

    private val klasseId = savedStateHandle.toRoute<Route.CsvImport>().klasseId
    private val _uiState = MutableStateFlow(CsvImportUiState())
    val uiState: StateFlow<CsvImportUiState> = _uiState.asStateFlow()

    fun dateiLaden(uri: Uri) {
        _uiState.update { it.copy(laedt = true, fehler = false, importiert = null) }
        viewModelScope.launch {
            val tabelle = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("leer")
                    val (text, zeichensatz) = CsvParser.dekodiere(bytes)
                    CsvParser.parse(text, zeichensatz = zeichensatz)
                }.getOrNull()
            }
            if (tabelle == null || tabelle.zeilen.isEmpty()) {
                _uiState.update { it.copy(laedt = false, fehler = true) }
                return@launch
            }
            val zuordnung = CsvParser.schlageZuordnungVor(tabelle)
            _uiState.update { it.copy(tabelle = tabelle, zuordnung = zuordnung, laedt = false, vorschau = CsvParser.schueler(tabelle, zuordnung)) }
        }
    }

    fun zuordnungAendern(transform: (Zuordnung) -> Zuordnung) {
        _uiState.update { s ->
            val z = transform(s.zuordnung)
            s.copy(zuordnung = z, vorschau = s.tabelle?.let { CsvParser.schueler(it, z) }.orEmpty())
        }
    }

    fun standardGeschlecht(g: Geschlecht) = _uiState.update { it.copy(standardGeschlecht = g) }

    fun importieren() {
        val s = _uiState.value
        if (s.vorschau.isEmpty()) return
        _uiState.update { it.copy(laedt = true) }
        viewModelScope.launch {
            var n = 0
            for (i in s.vorschau) {
                schuelerRepository.anlegen(klasseId, i.vorname, i.nachname, i.geschlecht ?: s.standardGeschlecht)
                n++
            }
            _uiState.update { it.copy(laedt = false, importiert = n) }
        }
    }
}
