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
import de.namio.core.sitzplan.SitzplanLogik
import de.namio.core.media.SitzplanPdf
import android.net.Uri
import de.namio.ui.navigation.Route
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    /** Gesperrt: nur ansehen, zoomen, auslosen. */
    val gesperrt: Boolean = true,
    val kannRueckgaengig: Boolean = false,
    /** Ausgeloster Schüler, dessen Platz blinkt. */
    val ausgelost: Long? = null,
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
    private val pdf: SitzplanPdf,
) : ViewModel() {
    private val pdfStatus = MutableStateFlow<Boolean?>(null)
    /** true = PDF geschrieben, false = Fehler, null = nichts zu melden. */
    val pdfErgebnis = pdfStatus.asStateFlow()
    fun pdfQuittieren() { pdfStatus.value = null }

    fun pdfExport(ziel: Uri, mitFotos: Boolean, tafelText: String) {
        val s = uiState.value; val plan = s.aktiv ?: return
        viewModelScope.launch {
            pdfStatus.value = runCatching {
                pdf.schreibe(ziel, "${s.klasse?.name.orEmpty()} · ${plan.name}", plan, s.bestuhlung, s.schuelerProId, s.blickrichtung, mitFotos, tafelText)
            }.isSuccess
        }
    }

    private val klasseId = savedStateHandle.toRoute<Route.Sitzplan>().klasseId
    private val gewaehltePlanId = MutableStateFlow<Long?>(null)
    /** Explizit gesetzte Sperre je Plan; ohne Eintrag gilt: Pläne mit Tischen starten gesperrt. */
    private val gesperrt = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    private val ausgelost = MutableStateFlow<Long?>(null)
    /** Undo-Stapel je Plan: frühere Bestuhlungen, jüngste zuletzt. */
    private val verlauf = MutableStateFlow<Map<Long, List<Bestuhlung>>>(emptyMap())

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
        combine(
            schuelerRepository.observeFuerKlasse(klasseId),
            einstellungen.blickrichtung,
            gesperrt,
            ausgelost,
            verlauf,
        ) { s, b, g, z, v -> Extra(s, b, g, z, v) },
    ) { klasse, plaene, aktiv, best, extra ->
        // Standard: bestehende Pläne mit Tischen starten gesperrt, leere Pläne offen.
        val sperre = aktiv?.let { extra.gesperrt[it.id] } ?: best.tische.isNotEmpty()
        SitzplanUiState(
            klasse, plaene, aktiv, best, extra.schueler, extra.blick, laedt = false,
            gesperrt = sperre,
            kannRueckgaengig = aktiv != null && !extra.verlauf[aktiv.id].isNullOrEmpty(),
            ausgelost = extra.ausgelost,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SitzplanUiState())

    private data class Extra(
        val schueler: List<Schueler>,
        val blick: Blickrichtung,
        val gesperrt: Map<Long, Boolean>,
        val ausgelost: Long?,
        val verlauf: Map<Long, List<Bestuhlung>>,
    )

    fun sperreUmschalten() {
        val plan = uiState.value.aktiv ?: return
        gesperrt.value = gesperrt.value + (plan.id to !uiState.value.gesperrt)
    }

    /** Lost einen sitzenden Schüler aus (nie zweimal hintereinander denselben, wenn möglich). */
    fun auslosen() {
        val sitzend = uiState.value.bestuhlung.plaetze.mapNotNull { it.schuelerId }
        if (sitzend.isEmpty()) return
        val kandidaten = sitzend.filter { it != ausgelost.value }.ifEmpty { sitzend }
        ausgelost.value = kandidaten.random()
    }

    fun auslosungBeenden() { ausgelost.value = null }

    fun rueckgaengig() {
        val plan = uiState.value.aktiv ?: return
        val stapel = verlauf.value[plan.id].orEmpty()
        val letzte = stapel.lastOrNull() ?: return
        verlauf.value = verlauf.value + (plan.id to stapel.dropLast(1))
        viewModelScope.launch { sitzplanRepository.setzeBestuhlung(plan.id, letzte) }
    }

    /** Merkt sich den aktuellen Stand, bevor eine Änderung geschrieben wird. */
    private fun merken() {
        val plan = uiState.value.aktiv ?: return
        val stapel = verlauf.value[plan.id].orEmpty() + uiState.value.bestuhlung
        verlauf.value = verlauf.value + (plan.id to stapel.takeLast(MAX_VERLAUF))
    }

    private inline fun mitPlanImmer(block: (Long) -> Unit) { uiState.value.aktiv?.let { block(it.id) } }

    private inline fun mitPlan(block: (Long) -> Unit) {
        val plan = uiState.value.aktiv ?: return
        if (uiState.value.gesperrt) return
        // Wer bearbeitet, bleibt entsperrt – auch wenn gerade der erste Tisch entsteht.
        if (plan.id !in gesperrt.value) gesperrt.value = gesperrt.value + (plan.id to false)
        merken()
        block(plan.id)
    }

    fun planWaehlen(id: Long) { gewaehltePlanId.value = id }

    fun planAnlegen(name: String, spalten: Int, reihen: Int, vorlage: SitzplanVorlage, vorbelegen: Boolean) {
        viewModelScope.launch {
            val ids = if (vorbelegen) uiState.value.schueler.map { it.id } else emptyList()
            gewaehltePlanId.value = sitzplanRepository.anlegen(klasseId, name, spalten, reihen, vorlage, ids)
        }
    }

    fun planAendern(name: String, spalten: Int, reihen: Int, einrasten: Boolean) = mitPlanImmer { id -> viewModelScope.launch { sitzplanRepository.aendern(id, name, spalten, reihen, einrasten) } }
    fun planLoeschen() = mitPlanImmer { id -> viewModelScope.launch { sitzplanRepository.loeschen(id); gewaehltePlanId.value = null } }
    fun alsStandard() = mitPlanImmer { id -> viewModelScope.launch { sitzplanRepository.alsStandard(id) } }
    fun ablegen(schuelerId: Long, x: Float, y: Float) = mitPlan { id -> viewModelScope.launch { sitzplanRepository.ablegen(id, schuelerId, x, y) } }
    fun tischHinzufuegen(x: Float, y: Float, plaetze: Int, breite: Float? = null) = mitPlan { id -> viewModelScope.launch { sitzplanRepository.tischHinzufuegen(id, x, y, plaetze, null, breite) } }
    fun duplizieren(tischId: Long) = mitPlan { id -> viewModelScope.launch { sitzplanRepository.duplizieren(id, tischId) } }
    fun verschieben(tischId: Long, x: Float, y: Float) = mitPlan { id -> viewModelScope.launch { sitzplanRepository.verschieben(id, tischId, x, y) } }
    fun drehen(tischId: Long, grad: Float) = mitPlan { id -> viewModelScope.launch { sitzplanRepository.drehen(id, tischId, grad) } }
    fun breiteAendern(tischId: Long, breite: Float) = mitPlan { id -> viewModelScope.launch { sitzplanRepository.breiteAendern(id, tischId, breite) } }
    fun plaetzeAendern(tischId: Long, plaetze: Int) = mitPlan { id -> viewModelScope.launch { sitzplanRepository.plaetzeAendern(id, tischId, plaetze) } }
    fun beschriften(tischId: Long, text: String) = mitPlan { id -> viewModelScope.launch { sitzplanRepository.beschriften(id, tischId, text) } }
    fun entfernen(schuelerId: Long) = mitPlan { id -> viewModelScope.launch { sitzplanRepository.entfernen(id, schuelerId) } }
    fun tischLoeschen(tischId: Long) = mitPlan { id -> viewModelScope.launch { sitzplanRepository.tischLoeschen(id, tischId) } }
    fun mischen() = mitPlan { id -> viewModelScope.launch { sitzplanRepository.mischen(id) } }
    /** Mischen trotz Sperre (nach Rückfrage) – mit Undo-Eintrag. */
    fun mischenErzwingen() = mitPlanImmer { id -> merken(); viewModelScope.launch { sitzplanRepository.mischen(id) } }
    fun drehenMehrere(ids: Set<Long>, grad: Float) = mitPlan { id -> viewModelScope.launch { sitzplanRepository.drehenMehrere(id, ids, grad) } }
    fun verschiebeMehrere(ids: Set<Long>, dx: Float, dy: Float) = mitPlan { id -> viewModelScope.launch { sitzplanRepository.verschiebeMehrere(id, ids, dx, dy) } }
    fun ausrichten(ids: List<Long>, art: SitzplanLogik.Ausrichtung) = mitPlan { id -> viewModelScope.launch { sitzplanRepository.ausrichten(id, ids, art) } }
    fun kopieren(name: String, mitSchuelern: Boolean) = mitPlanImmer { id -> viewModelScope.launch { sitzplanRepository.kopieren(id, name, mitSchuelern)?.let { gewaehltePlanId.value = it } } }

    private companion object {
        const val MAX_VERLAUF = 30
    }

    fun blickrichtungUmschalten() {
        val neu = if (uiState.value.blickrichtung == Blickrichtung.VON_VORN) Blickrichtung.VON_HINTEN else Blickrichtung.VON_VORN
        viewModelScope.launch { einstellungen.setzeBlickrichtung(neu) }
    }
}
