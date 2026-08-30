package de.namio.core.repository

import de.namio.core.data.dao.LernkarteDao
import de.namio.core.data.dao.QuizAntwortDao
import de.namio.core.data.dao.QuizSessionDao
import de.namio.core.data.dao.SchuelerDao
import de.namio.core.data.entity.QuizAntwortEntity
import de.namio.core.data.entity.QuizSessionEntity
import de.namio.core.lernen.KartenAuswahl
import de.namio.core.lernen.Leitner
import de.namio.core.model.Lernkarte
import de.namio.core.model.QuizModus
import de.namio.core.model.Schueler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Datenzugriff für Quizrunden: Kandidaten, Lernkarten, Sessions und Antworten. */
@Singleton
class QuizRepository @Inject constructor(
    private val schuelerDao: SchuelerDao,
    private val lernkarteDao: LernkarteDao,
    private val sessionDao: QuizSessionDao,
    private val antwortDao: QuizAntwortDao,
    private val clock: Clock,
) {
    /** Alle Schüler der Klasse (auch ohne Foto – als Ablenker brauchbar). */
    suspend fun schuelerDerKlasse(klasseId: Long): List<Schueler> =
        schuelerDao.fuerKlasse(klasseId).map { it.zuModell() }

    /** Lernkarten der Klasse in einem Modus. */
    suspend fun lernkarten(klasseId: Long, modus: QuizModus): List<Lernkarte> =
        lernkarteDao.fuerKlasseUndModus(klasseId, modus).map { it.zuModell() }

    /**
     * Anzahl fälliger Karten je Modus, live. Als Kandidaten zählen nur Schüler mit Foto,
     * weil jeder Modus ein Gesicht braucht.
     */
    fun observeFaelligProModus(klasseId: Long): Flow<Map<QuizModus, Int>> = combine(
        schuelerDao.observeFuerKlasse(klasseId),
        lernkarteDao.observeFuerKlasse(klasseId),
    ) { schueler, karten ->
        val kandidaten = schueler.filter { it.fotoDatei != null }.map { it.id }
        val jetzt = clock.instant()
        QuizModus.entries.associateWith { modus ->
            val modusKarten = karten.filter { it.modus == modus }.map { it.zuModell() }
            KartenAuswahl.anzahlFaellig(kandidaten, modusKarten, jetzt)
        }
    }

    /** Verwechslungen eines Schülers, häufigste zuerst. */
    suspend fun verwechslungen(schuelerId: Long): List<Long> = antwortDao.verwechslungenFuer(schuelerId)

    /** Startet eine Session und liefert ihre ID. */
    suspend fun sessionStarten(klasseId: Long, modus: QuizModus): Long =
        sessionDao.insert(QuizSessionEntity(klasseId = klasseId, modus = modus, startedAt = clock.millis()))

    /** Schließt eine Session mit Endstand ab. */
    suspend fun sessionBeenden(sessionId: Long, klasseId: Long, modus: QuizModus, startedAt: Long, richtig: Int, falsch: Int) {
        sessionDao.update(
            QuizSessionEntity(
                id = sessionId,
                klasseId = klasseId,
                modus = modus,
                startedAt = startedAt,
                endedAt = clock.millis(),
                anzahlRichtig = richtig,
                anzahlFalsch = falsch,
            ),
        )
    }

    /**
     * Speichert eine Antwort und aktualisiert den Lernstand nach Leitner.
     * Bei [lernstandAktualisieren] = false (Speedrun) bleibt die Karte unberührt.
     */
    suspend fun antwortVerbuchen(
        sessionId: Long,
        modus: QuizModus,
        schuelerId: Long,
        verwechseltMit: Long?,
        korrekt: Boolean,
        dauerMs: Long,
        lernstandAktualisieren: Boolean = true,
    ) {
        val jetzt = clock.instant()
        antwortDao.insert(
            QuizAntwortEntity(
                sessionId = sessionId,
                schuelerId = schuelerId,
                verwechseltMit = verwechseltMit,
                korrekt = korrekt,
                dauerMs = dauerMs,
                zeitpunkt = jetzt.toEpochMilli(),
            ),
        )
        if (!lernstandAktualisieren) return
        val alt = lernkarteDao.get(schuelerId, modus)?.zuModell()
        val neu = Leitner.anwenden(alt, schuelerId, modus, korrekt, jetzt)
        if (alt == null) lernkarteDao.upsert(neu.zuEntity()) else lernkarteDao.update(neu.zuEntity())
    }
}
