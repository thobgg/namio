package de.namio.feature.sitzplan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import de.namio.core.model.Bestuhlung
import de.namio.core.model.Blickrichtung
import de.namio.core.model.Klasse
import de.namio.core.model.Schueler
import de.namio.core.model.Sitzplan
import de.namio.core.model.SitzplanVorlage
import de.namio.core.repository.EinstellungenRepository
import de.namio.core.repository.KlassenRepository
import de.namio.core.repository.SchuelerRepository
import de.namio.core.repository.SitzplanRepository
import de.namio.ui.navigation.Route
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SitzplanUiState(
    val klasse: Klasse? = null,
    val plaene: List<Sitzplan> = emptyList(),
    val aktiv: Sitzplan? = null,
    val bestuhlung: Bestuhlung = Bestuhlung(),
    val schueler: List<Schueler> = emptyList(),
    val blickrichtung: Blickrichtung = Blickrichtung.VON_VORN,
    val laedt: Boolean = true,
) {
    val schuelerProId: Map<Long, Schueler> get() = schueler.associateBy { it.id }
    val unplatziert: List<Schueler>
        get() {
            val sitzend = bestuhlung.plaetze.mapNotNull { it.schuelerId }.toSet()
            return schueler.filter { it.id !in sitzend }
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SitzplanViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    klassenRepository: KlassenRepository,
    schuelerRepository: SchuelerRepository,
    private val sitzplanRepository: SitzplanRepository,
    private val einstellungen: EinstellungenRepository,
) : ViewModel() {

    private val klasseId = savedStateHandle.toRoute<Route.Sitzplan>().klasseId
    private val gewaehltePlanId = MutableStateFlow<Long?>(null)

    private val aktiverPlan = combine(sitzplanRepository.observePlaene(klasseId), gewaehltePlanId) { plaene, id ->
        plaene.firstOrNull { it.id == id } ?: plaene.firstOrNull()
    }
    private val bestuhlung = aktiverPlan.flatMapLatest { plan ->
        if (plan == null) flowOf(Bestuhlung()) else sitzplanRepository.observeBestuhlung(plan.id)
    }

    val uiState: StateFlow<SitzplanUiState> = combine(
        klassenRepository.observe(klasseId),
        sitzplanRepository.observePlaene(klasseId),
        aktiverPlan,
        bestuhlung,
        combine(schuelerRepository.observeFuerKlasse(klasseId), einstellungen.blickrichtung) { s, b -> s to b },
    ) { klasse, plaene, aktiv, best, (schueler, blick) ->
        SitzplanUiState(klasse, plaene, aktiv, best, schueler, blick, laedt = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SitzplanUiState())

    private inline fun mitPlan(block: (Long) -> Unit) { uiState.value.aktiv?.let { block(it.id) } }

    fun planWaehlen(id: Long) { gewaehltePlanId.value = id }

    fun planAnlegen(name: String, spalten: Int, reihen: Int, vorlage: SitzplanVorlage, vorbelegen: Boolean) {
        viewModelScope.launch {
            val ids = if (vorbelegen) uiState.value.schueler.map { it.id } else emptyList()
            gewaehltePlanId.value = sitzplanRepository.anlegen(klasseId, name, spalten, reihen, vorlage, ids)
        }
    }

    fun planAendern(name: String, spalten: Int, reihen: Int, einrasten: Boolean) = mitPlan { id -> viewModelScope.launch { sitzplanRepository.aendern(id, name, spalten, reihen, einrasten) } }
    fun planLoeschen() = mitPlan { id -> viewModelScope.launch { sitzplanRepository.loeschen(id); gewaehltePlanId.value = null } }
    fun alsStandard() = mitPlan { id -> viewModelScope.launch { sitzplanRepository.alsStandard(id) } }
    fun ablegen(schuelerId: Long, x: Float, y: Float) = mitPlan { id -> viewModelScope.launch { sitzplanRepository.ablegen(id, schuelerId, x, y) } }
    fun tischHinzufuegen(x: Float, y: Float, plaetze: Int) = mitPlan { id -> viewModelScope.launch { sitzplanRepository.tischHinzufuegen(id, x, y, plaetze, null) } }
    fun verschieben(tischId: Long, x: Float, y: Float) = mitPlan { id -> viewModelScope.launch { sitzplanRepository.verschieben(id, tischId, x, y) } }
    fun drehen(tischId: Long, grad: Float) = mitPlan { id -> viewModelScope.launch { sitzplanRepository.drehen(id, tischId, grad) } }
    fun plaetzeAendern(tischId: Long, plaetze: Int) = mitPlan { id -> viewModelScope.launch { sitzplanRepository.plaetzeAendern(id, tischId, plaetze) } }
    fun beschriften(tischId: Long, text: String) = mitPlan { id -> viewModelScope.launch { sitzplanRepository.beschriften(id, tischId, text) } }
    fun entfernen(schuelerId: Long) = mitPlan { id -> viewModelScope.launch { sitzplanRepository.entfernen(id, schuelerId) } }
    fun tischLoeschen(tischId: Long) = mitPlan { id -> viewModelScope.launch { sitzplanRepository.tischLoeschen(id, tischId) } }
    fun mischen() = mitPlan { id -> viewModelScope.launch { sitzplanRepository.mischen(id) } }

    fun blickrichtungUmschalten() {
        val neu = if (uiState.value.blickrichtung == Blickrichtung.VON_VORN) Blickrichtung.VON_HINTEN else Blickrichtung.VON_VORN
        viewModelScope.launch { einstellungen.setzeBlickrichtung(neu) }
    }
}
