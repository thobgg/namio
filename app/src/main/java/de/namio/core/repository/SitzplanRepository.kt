package de.namio.core.repository

import androidx.room.withTransaction
import de.namio.core.data.NamioDatabase
import de.namio.core.data.dao.SitzplanDao
import de.namio.core.data.dao.SitzplatzDao
import de.namio.core.data.dao.TischDao
import de.namio.core.data.entity.SitzplanEntity
import de.namio.core.model.Bestuhlung
import de.namio.core.model.Sitzplan
import de.namio.core.model.SitzplanVorlage
import de.namio.core.sitzplan.SitzplanLogik
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Datenzugriff für Sitzpläne. Alle Änderungen an der Bestuhlung laufen über [SitzplanLogik]. */
@Singleton
class SitzplanRepository @Inject constructor(
    private val db: NamioDatabase,
    private val sitzplanDao: SitzplanDao,
    private val tischDao: TischDao,
    private val sitzplatzDao: SitzplatzDao,
) {
    fun observePlaene(klasseId: Long): Flow<List<Sitzplan>> =
        sitzplanDao.observeFuerKlasse(klasseId).map { liste -> liste.map { it.zuModell() } }

    fun observeBestuhlung(sitzplanId: Long): Flow<Bestuhlung> = combine(
        tischDao.observeFuerPlan(sitzplanId),
        sitzplatzDao.observeFuerPlan(sitzplanId),
    ) { tische, plaetze -> Bestuhlung(tische.map { it.zuModell() }, plaetze.map { it.zuModell() }) }

    /** Standardplan der Klasse mit Bestuhlung, `null` wenn es keinen Plan gibt. */
    suspend fun standardplan(klasseId: Long): Pair<Sitzplan, Bestuhlung>? {
        val plan = sitzplanDao.standardFuerKlasse(klasseId) ?: return null
        return plan.zuModell() to lies(plan.id)
    }

    /** Legt einen Plan an, optional mit Vorlage und Vorbelegung. Der erste Plan einer Klasse wird Standard. */
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
        schreibeBestuhlung(id, SitzplanLogik.vorlage(vorlage, id, s, r, schuelerIds))
        id
    }

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

    suspend fun loeschen(planId: Long) {
        val plan = sitzplanDao.get(planId) ?: return
        db.withTransaction {
            sitzplanDao.delete(planId)
            if (plan.istStandard) {
                sitzplanDao.standardFuerKlasse(plan.klasseId)?.let { sitzplanDao.setzeStandard(plan.klasseId, it.id) }
            }
        }
    }

    suspend fun ablegen(planId: Long, schuelerId: Long, x: Float, y: Float) = schreibe(planId) { p, b -> SitzplanLogik.ablegen(b, planId, schuelerId, x, y, p.spalten, p.reihen, p.einrasten) }
    suspend fun tischHinzufuegen(planId: Long, x: Float, y: Float, plaetze: Int, beschriftung: String?) = schreibe(planId) { p, b -> SitzplanLogik.tischHinzufuegen(b, planId, x, y, 0f, plaetze, beschriftung, p.spalten, p.reihen, p.einrasten) }
    suspend fun verschieben(planId: Long, tischId: Long, x: Float, y: Float) = schreibe(planId) { p, b -> SitzplanLogik.verschieben(b, tischId, x, y, p.spalten, p.reihen, p.einrasten) }
    suspend fun drehen(planId: Long, tischId: Long, grad: Float) = schreibe(planId) { _, b -> SitzplanLogik.drehen(b, tischId, grad) }
    suspend fun plaetzeAendern(planId: Long, tischId: Long, plaetze: Int) = schreibe(planId) { _, b -> SitzplanLogik.plaetzeAendern(b, tischId, plaetze) }
    suspend fun beschriften(planId: Long, tischId: Long, text: String) = schreibe(planId) { _, b -> SitzplanLogik.beschriften(b, tischId, text) }
    suspend fun entfernen(planId: Long, schuelerId: Long) = schreibe(planId) { _, b -> SitzplanLogik.entfernen(b, schuelerId) }
    suspend fun tischLoeschen(planId: Long, tischId: Long) = schreibe(planId) { _, b -> SitzplanLogik.tischLoeschen(b, tischId) }
    suspend fun mischen(planId: Long) = schreibe(planId) { _, b -> SitzplanLogik.mischen(b) }

    /** Setzt die Bestuhlung komplett – für Rückgängig. */
    suspend fun setzeBestuhlung(planId: Long, bestuhlung: Bestuhlung) {
        db.withTransaction { schreibeBestuhlung(planId, bestuhlung) }
    }

    private suspend fun lies(planId: Long) = Bestuhlung(
        tischDao.fuerPlan(planId).map { it.zuModell() },
        sitzplatzDao.fuerPlan(planId).map { it.zuModell() },
    )

    /** Liest Plan und Bestuhlung, wendet [transform] an und schreibt das Ergebnis komplett zurück. */
    private suspend fun schreibe(planId: Long, transform: (Sitzplan, Bestuhlung) -> Bestuhlung) {
        db.withTransaction {
            val plan = sitzplanDao.get(planId)?.zuModell() ?: return@withTransaction
            val alt = lies(planId)
            val neu = transform(plan, alt)
            if (neu == alt) return@withTransaction
            schreibeBestuhlung(planId, neu)
        }
    }

    /** Erst alles löschen, dann Tische (neue bekommen echte IDs) und Plätze einfügen. */
    private suspend fun schreibeBestuhlung(planId: Long, b: Bestuhlung) {
        tischDao.loescheAlleFuerPlan(planId)
        sitzplatzDao.loescheAlleFuerPlan(planId)
        val idMap = HashMap<Long, Long>()
        for (t in b.tische) idMap[t.id] = tischDao.insert(t.zuEntity())
        for (p in b.plaetze) {
            val tischId = idMap[p.tischId] ?: continue
            sitzplatzDao.insert(p.zuEntity(tischId = tischId).copy(id = 0))
        }
    }

    companion object {
        const val MIN_EINHEITEN = 4
        const val MAX_EINHEITEN = 20
        const val STANDARD_SPALTEN = 12
        const val STANDARD_REIHEN = 9
    }
}
