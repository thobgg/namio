package de.namio.core.repository

import androidx.room.withTransaction
import de.namio.core.data.NamioDatabase
import de.namio.core.data.dao.SitzplanDao
import de.namio.core.data.dao.SitzplatzDao
import de.namio.core.data.entity.SitzplanEntity
import de.namio.core.model.Sitzplan
import de.namio.core.model.SitzplanVorlage
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

    /**
     * Legt einen Plan an, optional mit Vorlage und Vorbelegung durch [schuelerIds].
     * Der erste Plan einer Klasse wird automatisch Standard.
     */
    suspend fun anlegen(
        klasseId: Long,
        name: String,
        spalten: Int,
        reihen: Int,
        vorlage: SitzplanVorlage = SitzplanVorlage.LEER,
        schuelerIds: List<Long> = emptyList(),
    ): Long = db.withTransaction {
        val erster = sitzplanDao.anzahlFuerKlasse(klasseId) == 0
        val s = spalten.coerceIn(MIN_EINHEITEN, MAX_EINHEITEN)
        val r = reihen.coerceIn(MIN_EINHEITEN, MAX_EINHEITEN)
        val id = sitzplanDao.insert(
            SitzplanEntity(klasseId = klasseId, name = name.trim().ifBlank { "Sitzplan" }, spalten = s, reihen = r, istStandard = erster),
        )
        val plaetze = SitzplanLogik.vorlage(vorlage, id, s, r, schuelerIds)
        if (plaetze.isNotEmpty()) sitzplatzDao.upsertAlle(plaetze.map { it.zuEntity() })
        id
    }

    /** Name, Raumgröße und Einrasten ändern. Plätze bleiben (normierte Koordinaten). */
    suspend fun aendern(planId: Long, name: String, spalten: Int, reihen: Int, einrasten: Boolean) {
        val plan = sitzplanDao.get(planId) ?: return
        sitzplanDao.update(
            plan.copy(
                name = name.trim().ifBlank { plan.name },
                spalten = spalten.coerceIn(MIN_EINHEITEN, MAX_EINHEITEN),
                reihen = reihen.coerceIn(MIN_EINHEITEN, MAX_EINHEITEN),
                einrasten = einrasten,
            ),
        )
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

    suspend fun ablegen(planId: Long, schuelerId: Long, x: Float, y: Float) = schreibe(planId) { plan, plaetze ->
        SitzplanLogik.ablegen(plaetze, planId, schuelerId, x, y, plan.spalten, plan.reihen, plan.einrasten)
    }

    suspend fun verschieben(planId: Long, platzId: Long, x: Float, y: Float) = schreibe(planId) { plan, plaetze ->
        SitzplanLogik.verschieben(plaetze, platzId, x, y, plan.spalten, plan.reihen, plan.einrasten)
    }

    suspend fun drehen(planId: Long, platzId: Long, grad: Float) = schreibe(planId) { _, plaetze -> SitzplanLogik.drehen(plaetze, platzId, grad) }

    suspend fun entfernen(planId: Long, schuelerId: Long) = schreibe(planId) { _, plaetze -> SitzplanLogik.entfernen(plaetze, schuelerId) }

    suspend fun platzLoeschen(planId: Long, platzId: Long) = schreibe(planId) { _, plaetze -> SitzplanLogik.platzLoeschen(plaetze, platzId) }

    suspend fun leererStuhl(planId: Long, x: Float, y: Float) = schreibe(planId) { plan, plaetze ->
        SitzplanLogik.leererStuhl(plaetze, planId, x, y, plan.spalten, plan.reihen, plan.einrasten)
    }

    suspend fun partnerplatz(planId: Long, platzId: Long) = schreibe(planId) { plan, plaetze ->
        SitzplanLogik.partnerplatz(plaetze, platzId, plan.spalten, plan.reihen)
    }

    suspend fun beschriften(planId: Long, platzId: Long, text: String) = schreibe(planId) { _, plaetze -> SitzplanLogik.beschriften(plaetze, platzId, text) }

    suspend fun mischen(planId: Long) = schreibe(planId) { _, plaetze -> SitzplanLogik.mischen(plaetze) }

    /**
     * Liest Plan und Plätze, wendet [transform] an und schreibt das Ergebnis komplett zurück.
     * Erst löschen, dann einfügen – so kollidiert der Unique-Index beim Tausch nicht.
     */
    private suspend fun schreibe(planId: Long, transform: (Sitzplan, List<Sitzplatz>) -> List<Sitzplatz>) {
        db.withTransaction {
            val plan = sitzplanDao.get(planId)?.zuModell() ?: return@withTransaction
            val alt = sitzplatzDao.fuerPlan(planId).map { it.zuModell() }
            val neu = transform(plan, alt)
            if (neu == alt) return@withTransaction
            sitzplatzDao.loescheAlleFuerPlan(planId)
            sitzplatzDao.upsertAlle(neu.map { it.zuEntity() })
        }
    }

    companion object {
        const val MIN_EINHEITEN = 4
        const val MAX_EINHEITEN = 20
        const val STANDARD_SPALTEN = 12
        const val STANDARD_REIHEN = 9
    }
}
