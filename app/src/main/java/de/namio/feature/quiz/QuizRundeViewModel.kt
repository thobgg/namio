package de.namio.feature.quiz

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import de.namio.core.lernen.AblenkerWaehler
import de.namio.core.lernen.KartenAuswahl
import de.namio.core.lernen.NamensVergleich
import de.namio.core.lernen.QuizRunde
import de.namio.core.model.QuizFehler
import de.namio.core.model.QuizFrage
import de.namio.core.model.QuizModus
import de.namio.core.model.Schueler
import de.namio.core.model.Bestuhlung
import de.namio.core.model.Sitzplan
import de.namio.core.repository.SitzplanRepository
import de.namio.core.repository.EinstellungenRepository
import kotlinx.coroutines.flow.first
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

/** Rückmeldung nach einer Antwort. [gewaehltId] = null bei Freitext ohne Treffer. */
data class Feedback(val gewaehltId: Long?, val korrekt: Boolean, val eingabe: String? = null)

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
        /** Nur im Speedrun: verbleibende Sekunden. */
        val restSekunden: Int? = null,
    ) : QuizRundePhase

    data class Ergebnis(
        val richtig: Int,
        val falsch: Int,
        val fehler: List<QuizFehler>,
        val speedrun: Boolean = false,
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
    private val einstellungen: EinstellungenRepository,
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
    private var speedrunEnde = 0L
    private var speedrunVorbei = false

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
            modus == QuizModus.SPEEDRUN -> {
                // Speedrun: alle Schüler mit Foto, mehrfach durchgemischt – das Zeitlimit beendet die Runde.
                val basis = mitFoto.shuffled()
                if (basis.isEmpty()) emptyList() else List(SPEEDRUN_DURCHGAENGE) { basis.shuffled() }.flatten()
            }
            else -> {
                val karten = repository.lernkarten(klasseId, modus)
                val faellig = KartenAuswahl.reihenfolge(mitFoto, karten, clock.instant(), maxNeue = einstellungen.neueKartenProRunde.first())
                // Nichts fällig: trotzdem üben lassen, mit allen Schülern in zufälliger Reihenfolge.
                faellig.ifEmpty { mitFoto.shuffled() }
            }
        }
        if (reihenfolge.isEmpty()) {
            _uiState.update { it.copy(phase = QuizRundePhase.KeineKandidaten) }
            return
        }
        runde = if (modus == QuizModus.SPEEDRUN) QuizRunde(reihenfolge, wiederholenBeiFehler = false, duplikateErlauben = true) else QuizRunde(reihenfolge)
        richtig = 0
        falsch = 0
        fehler.clear()
        speedrunVorbei = false
        sessionStart = clock.millis()
        sessionId = repository.sessionStarten(klasseId, modus)
        if (modus == QuizModus.SPEEDRUN) {
            speedrunEnde = clock.millis() + SPEEDRUN_SEKUNDEN * 1000L
            viewModelScope.launch {
                while (!speedrunVorbei) {
                    val rest = ((speedrunEnde - clock.millis()) / 1000L).toInt().coerceAtLeast(0)
                    _uiState.update { s ->
                        val p = s.phase
                        if (p is QuizRundePhase.Frage) s.copy(phase = p.copy(restSekunden = rest)) else s
                    }
                    if (rest <= 0) { speedrunVorbei = true; abschliessen(); break }
                    delay(250)
                }
            }
        }
        naechsteFrage()
    }

    private suspend fun abschliessen() {
        repository.sessionBeenden(sessionId, klasseId, modus, sessionStart, richtig, falsch)
        _uiState.update {
            it.copy(phase = QuizRundePhase.Ergebnis(richtig, falsch, fehler.values.toList(), speedrun = modus == QuizModus.SPEEDRUN))
        }
    }

    private suspend fun naechsteFrage() {
        if (speedrunVorbei) return
        val zielId = runde.aktuell
        if (zielId == null) {
            speedrunVorbei = true
            abschliessen()
            return
        }
        val ziel = alleSchueler.first { it.id == zielId }
        val optionen = when (modus) {
            // Im Sitzplan-Modus ist der ganze Plan die Auswahl – alle Schüler gehören ins Raster.
            QuizModus.SITZPLAN -> alleSchueler
            // Freitext: keine Optionen nötig.
            QuizModus.FOTO_ZU_NAME_TIPPEN -> listOf(ziel)
            else -> {
                val anzahl = if (modus == QuizModus.NAME_ZU_FOTO) ANZAHL_ABLENKER_RASTER else ANZAHL_ABLENKER
                val kandidaten = if (modus == QuizModus.NAME_ZU_FOTO) alleSchueler.filter { it.fotoDatei != null } else alleSchueler
                val ablenker = ablenkerWaehler.waehle(ziel, kandidaten, repository.verwechslungen(zielId), anzahl)
                (ablenker + ziel).shuffled()
            }
        }
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
                    restSekunden = if (modus == QuizModus.SPEEDRUN) ((speedrunEnde - clock.millis()) / 1000L).toInt().coerceAtLeast(0) else null,
                ),
            )
        }
    }

    fun antworten(gewaehlt: Schueler) = verbuchen(korrekt = gewaehlt.id == (uiState.value.phase as? QuizRundePhase.Frage)?.frage?.ziel?.id, verwechseltMit = gewaehlt, eingabe = null)

    /** Freitext-Antwort im Tippen-Modus. */
    fun antwortenText(text: String) {
        val phase = uiState.value.phase as? QuizRundePhase.Frage ?: return
        val ziel = phase.frage.ziel
        val korrekt = NamensVergleich.istRichtig(text, ziel, alleSchueler)
        val verwechselt = if (korrekt) null else NamensVergleich.verwechseltMit(text, ziel, alleSchueler)
        verbuchen(korrekt, verwechselt, text)
    }

    private fun verbuchen(korrekt: Boolean, verwechseltMit: Schueler?, eingabe: String?) {
        val phase = uiState.value.phase as? QuizRundePhase.Frage ?: return
        if (phase.feedback != null || antwortLaeuft || speedrunVorbei) return
        antwortLaeuft = true
        val ziel = phase.frage.ziel
        val dauer = clock.millis() - frageGezeigtAb
        if (korrekt) richtig++ else falsch++
        if (!korrekt) fehler.putIfAbsent(ziel.id, QuizFehler(ziel, verwechseltMit))
        _uiState.update { it.copy(phase = phase.copy(feedback = Feedback(verwechseltMit?.id ?: if (korrekt) ziel.id else null, korrekt, eingabe))) }
        viewModelScope.launch {
            repository.antwortVerbuchen(
                sessionId = sessionId,
                modus = modus,
                schuelerId = ziel.id,
                verwechseltMit = if (korrekt) null else verwechseltMit?.id,
                korrekt = korrekt,
                dauerMs = dauer,
                lernstandAktualisieren = modus != QuizModus.SPEEDRUN,
            )
            runde.antworte(korrekt)
            val pause = when {
                modus == QuizModus.SPEEDRUN -> if (korrekt) 250L else 900L
                modus == QuizModus.FOTO_ZU_NAME_TIPPEN -> if (korrekt) 1000L else 2500L
                else -> if (korrekt) FEEDBACK_RICHTIG_MS else FEEDBACK_FALSCH_MS
            }
            delay(pause)
            antwortLaeuft = false
            naechsteFrage()
        }
    }

    private companion object {
        const val ANZAHL_ABLENKER = 3
        const val ANZAHL_ABLENKER_RASTER = 8
        const val SPEEDRUN_SEKUNDEN = 60
        const val SPEEDRUN_DURCHGAENGE = 5
        const val FEEDBACK_RICHTIG_MS = 700L
        const val FEEDBACK_FALSCH_MS = 1600L
    }
}
