package de.namio.core.repository

import de.namio.core.data.dao.LernkarteDao
import de.namio.core.data.dao.QuizAntwortDao
import de.namio.core.data.dao.QuizSessionDao
import de.namio.core.data.dao.SchuelerDao
import de.namio.core.model.Lernkarte
import de.namio.core.model.Schueler
import de.namio.core.model.SessionKurz
import de.namio.core.model.VerwechslungRoh
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Rohdaten für die Statistik einer Klasse, live aus der Datenbank. */
@Singleton
class StatistikRepository @Inject constructor(
    private val schuelerDao: SchuelerDao,
    private val lernkarteDao: LernkarteDao,
    private val sessionDao: QuizSessionDao,
    private val antwortDao: QuizAntwortDao,
) {
    fun observeSchueler(klasseId: Long): Flow<List<Schueler>> = schuelerDao.observeFuerKlasse(klasseId).map { l -> l.map { it.zuModell() } }
    fun observeKarten(klasseId: Long): Flow<List<Lernkarte>> = lernkarteDao.observeFuerKlasse(klasseId).map { l -> l.map { it.zuModell() } }
    fun observeSessions(klasseId: Long): Flow<List<SessionKurz>> = sessionDao.observeFuerKlasse(klasseId).map { l ->
        l.filter { it.endedAt != null }.map { SessionKurz(it.id, it.modus, Instant.ofEpochMilli(it.startedAt), it.anzahlRichtig, it.anzahlFalsch) }
    }
    fun observeVerwechslungen(klasseId: Long): Flow<List<VerwechslungRoh>> = antwortDao.observeVerwechslungen(klasseId).map { l ->
        l.map { VerwechslungRoh(it.schuelerId, it.verwechseltMit, it.anzahl) }
    }
}
