package de.namio.feature.quiz

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import de.namio.core.lernen.AblenkerWaehler
import de.namio.core.lernen.KartenAuswahl
import de.namio.core.lernen.QuizRunde
import de.namio.core.model.QuizFehler
import de.namio.core.model.QuizFrage
import de.namio.core.model.QuizModus
import de.namio.core.model.Schueler
import de.namio.core.model.Bestuhlung
import de.namio.core.model.Sitzplan
import de.namio.core.repository.SitzplanRepository
import de.namio.core.repository.QuizRepository
import de.namio.ui.navigation.Route
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject
import kotlin.random.Random

/** Rückmeldung nach einer Antwort. */
data class Feedback(val gewaehltId: Long, val korrekt: Boolean)

sealed interface QuizRundePhase {
    data object Laedt : QuizRundePhase

    /** Keine Schüler mit Foto – ohne Gesicht kein Quiz. */
    data object KeineKandidaten : QuizRundePhase

    /** Sitzplan-Modus ohne Sitzplan. */
    data object KeinSitzplan : QuizRundePhase

    data class Frage(
        val frage: QuizFrage,
        val fortschritt: Float,
        val erledigt: Int,
        val gesamt: Int,
        val feedback: Feedback? = null,
        /** Nur im Sitzplan-Modus: der gerenderte Plan. */
        val sitzplan: Sitzplan? = null,
        val bestuhlung: Bestuhlung = Bestuhlung(),
    ) : QuizRundePhase

    data class Ergebnis(
        val richtig: Int,
        val falsch: Int,
        val fehler: List<QuizFehler>,
    ) : QuizRundePhase
}

data class QuizRundeUiState(
    val modus: QuizModus = QuizModus.FOTO_ZU_NAME_MC,
    val phase: QuizRundePhase = QuizRundePhase.Laedt,
)

@HiltViewModel
class QuizRundeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: QuizRepository,
    private val sitzplanRepository: SitzplanRepository,
    private val clock: Clock,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Route.QuizRunde>()
    private val klasseId = route.klasseId
    private val modus = QuizModus.valueOf(route.modus)
    private val ablenkerWaehler = AblenkerWaehler(Random.Default)

    private val _uiState = MutableStateFlow(QuizRundeUiState(modus = modus))
    val uiState: StateFlow<QuizRundeUiState> = _uiState.asStateFlow()

    private var alleSchueler: List<Schueler> = emptyList()
    private var sitzplan: Sitzplan? = null
    private var bestuhlung: Bestuhlung = Bestuhlung()
    private var runde = QuizRunde(emptyList())
    private var sessionId = 0L
    private var sessionStart = 0L
    private var frageGezeigtAb = 0L
    private var richtig = 0
    private var falsch = 0
    private val fehler = LinkedHashMap<Long, QuizFehler>()
    private var antwortLaeuft = false

    init {
        viewModelScope.launch { starten(nurSchueler = null) }
    }

    /** Neue Runde nur mit den Fehlern der letzten. */
    fun fehlerWiederholen() {
        val ids = (uiState.value.phase as? QuizRundePhase.Ergebnis)?.fehler?.map { it.schueler.id } ?: return
        viewModelScope.launch { starten(nurSchueler = ids) }
    }

    private suspend fun starten(nurSchueler: List<Long>?) {
        _uiState.update { it.copy(phase = QuizRundePhase.Laedt) }
        alleSchueler = repository.schuelerDerKlasse(klasseId)
        var mitFoto = alleSchueler.filter { it.fotoDatei != null }.map { it.id }
        if (modus == QuizModus.SITZPLAN) {
            val plan = sitzplanRepository.standardplan(klasseId)
            val sitzend = plan?.second?.plaetze?.mapNotNull { it.schuelerId }?.toSet().orEmpty()
            mitFoto = mitFoto.filter { it in sitzend }
            if (plan == null || mitFoto.isEmpty()) {
                _uiState.update { it.copy(phase = QuizRundePhase.KeinSitzplan) }
                return
            }
            sitzplan = plan.first
            bestuhlung = plan.second
        }
        val reihenfolge = when {
            nurSchueler != null -> nurSchueler.filter { it in mitFoto }
            else -> {
                val karten = repository.lernkarten(klasseId, modus)
                val faellig = KartenAuswahl.reihenfolge(mitFoto, karten, clock.instant())
                // Nichts fällig: trotzdem üben lassen, mit allen Schülern in zufälliger Reihenfolge.
                faellig.ifEmpty { mitFoto.shuffled() }
            }
        }
        if (reihenfolge.isEmpty()) {
            _uiState.update { it.copy(phase = QuizRundePhase.KeineKandidaten) }
            return
        }
        runde = QuizRunde(reihenfolge)
        richtig = 0
        falsch = 0
        fehler.clear()
        sessionStart = clock.millis()
        sessionId = repository.sessionStarten(klasseId, modus)
        naechsteFrage()
    }

    private suspend fun naechsteFrage() {
        val zielId = runde.aktuell
        if (zielId == null) {
            repository.sessionBeenden(sessionId, klasseId, modus, sessionStart, richtig, falsch)
            _uiState.update {
                it.copy(phase = QuizRundePhase.Ergebnis(richtig, falsch, fehler.values.toList()))
            }
            return
        }
        val ziel = alleSchueler.first { it.id == zielId }
        val ablenker = ablenkerWaehler.waehle(
            ziel = ziel,
            kandidaten = alleSchueler,
            verwechslungen = repository.verwechslungen(zielId),
            anzahl = ANZAHL_ABLENKER,
        )
        // Im Sitzplan-Modus ist der ganze Plan die Auswahl – alle Schüler gehören ins Raster.
        val optionen = if (modus == QuizModus.SITZPLAN) alleSchueler else (ablenker + ziel).shuffled()
        frageGezeigtAb = clock.millis()
        _uiState.update {
            it.copy(
                phase = QuizRundePhase.Frage(
                    frage = QuizFrage(ziel, optionen),
                    fortschritt = runde.fortschritt,
                    erledigt = runde.fertig,
                    gesamt = runde.anzahl,
                    sitzplan = sitzplan,
                    bestuhlung = bestuhlung,
                ),
            )
        }
    }

    fun antworten(gewaehlt: Schueler) {
        val phase = uiState.value.phase as? QuizRundePhase.Frage ?: return
        if (phase.feedback != null || antwortLaeuft) return
        antwortLaeuft = true
        val ziel = phase.frage.ziel
        val korrekt = gewaehlt.id == ziel.id
        val dauer = clock.millis() - frageGezeigtAb
        if (korrekt) richtig++ else falsch++
        if (!korrekt) fehler.putIfAbsent(ziel.id, QuizFehler(ziel, gewaehlt))
        _uiState.update { it.copy(phase = phase.copy(feedback = Feedback(gewaehlt.id, korrekt))) }
        viewModelScope.launch {
            repository.antwortVerbuchen(
                sessionId = sessionId,
                modus = modus,
                schuelerId = ziel.id,
                verwechseltMit = if (korrekt) null else gewaehlt.id,
                korrekt = korrekt,
                dauerMs = dauer,
                lernstandAktualisieren = modus != QuizModus.SPEEDRUN,
            )
            runde.antworte(korrekt)
            delay(if (korrekt) FEEDBACK_RICHTIG_MS else FEEDBACK_FALSCH_MS)
            antwortLaeuft = false
            naechsteFrage()
        }
    }

    private companion object {
        const val ANZAHL_ABLENKER = 3
        const val FEEDBACK_RICHTIG_MS = 700L
        const val FEEDBACK_FALSCH_MS = 1600L
    }
}
