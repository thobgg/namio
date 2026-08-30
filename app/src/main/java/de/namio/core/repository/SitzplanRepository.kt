package de.namio.core.repository

import androidx.room.withTransaction
import de.namio.core.data.NamioDatabase
import de.namio.core.data.dao.SitzplanDao
import de.namio.core.data.dao.SitzplatzDao
import de.namio.core.data.entity.SitzplanEntity
import de.namio.core.model.Sitzplan
import de.namio.core.model.Sitzplatz
import de.namio.core.sitzplan.SitzplanLogik
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Datenzugriff für Sitzpläne und Plätze. Alle Änderungen an Plätzen laufen über [SitzplanLogik]. */
@Singleton
class SitzplanRepository @Inject constructor(
    private val db: NamioDatabase,
    private val sitzplanDao: SitzplanDao,
    private val sitzplatzDao: SitzplatzDao,
) {
    fun observePlaene(klasseId: Long): Flow<List<Sitzplan>> =
        sitzplanDao.observeFuerKlasse(klasseId).map { liste -> liste.map { it.zuModell() } }

    fun observePlaetze(sitzplanId: Long): Flow<List<Sitzplatz>> =
        sitzplatzDao.observeFuerPlan(sitzplanId).map { liste -> liste.map { it.zuModell() } }

    /** Standardplan der Klasse mit Plätzen, `null` wenn es keinen Plan gibt. */
    suspend fun standardplan(klasseId: Long): Pair<Sitzplan, List<Sitzplatz>>? {
        val plan = sitzplanDao.standardFuerKlasse(klasseId) ?: return null
        return plan.zuModell() to sitzplatzDao.fuerPlan(plan.id).map { it.zuModell() }
    }

    /** Legt einen Plan an; der erste Plan einer Klasse wird automatisch Standard. */
    suspend fun anlegen(klasseId: Long, name: String, spalten: Int, reihen: Int, doppeltische: Boolean): Long {
        val erster = sitzplanDao.anzahlFuerKlasse(klasseId) == 0
        return sitzplanDao.insert(
            SitzplanEntity(
                klasseId = klasseId,
                name = name.trim().ifBlank { "Sitzplan" },
                spalten = spalten.coerceIn(1, MAX_SPALTEN),
                reihen = reihen.coerceIn(1, MAX_REIHEN),
                istStandard = erster,
                doppeltische = doppeltische,
            ),
        )
    }

    /** Name und Rastergröße ändern; Plätze außerhalb des neuen Rasters werden entfernt. */
    suspend fun aendern(planId: Long, name: String, spalten: Int, reihen: Int, doppeltische: Boolean) {
        val plan = sitzplanDao.get(planId) ?: return
        val s = spalten.coerceIn(1, MAX_SPALTEN)
        val r = reihen.coerceIn(1, MAX_REIHEN)
        db.withTransaction {
            val plaetze = sitzplatzDao.fuerPlan(planId).map { it.zuModell() }
            val weg = SitzplanLogik.ausserhalb(plaetze, s, r).map { it.id }
            if (weg.isNotEmpty()) sitzplatzDao.loesche(weg)
            sitzplanDao.update(plan.copy(name = name.trim().ifBlank { plan.name }, spalten = s, reihen = r, doppeltische = doppeltische))
        }
    }

    suspend fun alsStandard(planId: Long) {
        val plan = sitzplanDao.get(planId) ?: return
        sitzplanDao.setzeStandard(plan.klasseId, planId)
    }

    /** Löscht den Plan; war er Standard, rückt der nächste nach. */
    suspend fun loeschen(planId: Long) {
        val plan = sitzplanDao.get(planId) ?: return
        db.withTransaction {
            sitzplanDao.delete(planId)
            if (plan.istStandard) {
                sitzplanDao.standardFuerKlasse(plan.klasseId)?.let { sitzplanDao.setzeStandard(plan.klasseId, it.id) }
            }
        }
    }

    suspend fun setzen(planId: Long, schuelerId: Long, spalte: Int, reihe: Int) = schreibe(planId) {
        SitzplanLogik.setzen(it, planId, schuelerId, spalte, reihe)
    }

    suspend fun entfernen(planId: Long, schuelerId: Long) = schreibe(planId) { SitzplanLogik.entfernen(it, schuelerId) }

    suspend fun mischen(planId: Long) = schreibe(planId) { SitzplanLogik.mischen(it) }

    /** Leeren Stuhl setzen oder wieder wegnehmen. */
    suspend fun leerenStuhlUmschalten(planId: Long, spalte: Int, reihe: Int) = schreibe(planId) { plaetze ->
        val vorhanden = plaetze.firstOrNull { it.spalte == spalte && it.reihe == reihe }
        when {
            vorhanden == null -> plaetze + Sitzplatz(0, planId, null, spalte, reihe)
            vorhanden.schuelerId == null -> plaetze.filter { it.id != vorhanden.id }
            else -> plaetze
        }
    }

    /**
     * Liest die Plätze, wendet [transform] an und schreibt das Ergebnis komplett zurück.
     * Erst löschen, dann einfügen – so kollidieren die Unique-Indizes beim Tausch nicht.
     */
    private suspend fun schreibe(planId: Long, transform: (List<Sitzplatz>) -> List<Sitzplatz>) {
        db.withTransaction {
            val alt = sitzplatzDao.fuerPlan(planId).map { it.zuModell() }
            val neu = transform(alt)
            if (neu == alt) return@withTransaction
            sitzplatzDao.loescheAlleFuerPlan(planId)
            sitzplatzDao.upsertAlle(neu.map { it.zuEntity() })
        }
    }

    companion object {
        const val MAX_SPALTEN = 12
        const val MAX_REIHEN = 10
    }
}
