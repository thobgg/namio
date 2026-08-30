package de.namio.core.repository

import de.namio.core.data.dao.KlasseDao
import de.namio.core.data.dao.SchuelerDao
import de.namio.core.data.entity.KlasseEntity
import de.namio.core.media.FotoStore
import de.namio.core.model.Klasse
import de.namio.core.model.KlasseUebersicht
import de.namio.core.model.QuizModus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Einzige Datenquelle für Klassen. Liefert Domain-Modelle, nie Entities. */
@Singleton
class KlassenRepository @Inject constructor(
    private val klasseDao: KlasseDao,
    private val schuelerDao: SchuelerDao,
    private val fotoStore: FotoStore,
    private val clock: Clock,
) {
    /** Alle nicht archivierten Klassen mit Schülerzahl und Fortschritt, alphabetisch. */
    fun observeUebersicht(): Flow<List<KlasseUebersicht>> =
        klasseDao.observeUebersicht(QuizModus.FOTO_ZU_NAME_MC).map { liste ->
            liste.map {
                KlasseUebersicht(
                    klasse = it.klasse.zuModell(),
                    schuelerAnzahl = it.schuelerAnzahl,
                    fortschrittProzent = Fortschritt.prozent(it.boxSumme, it.schuelerAnzahl),
                )
            }
        }

    /** Eine Klasse; `null`, wenn sie nicht (mehr) existiert. */
    fun observe(id: Long): Flow<Klasse?> = klasseDao.observe(id).map { it?.zuModell() }

    /** Legt eine Klasse an und liefert ihre ID. */
    suspend fun anlegen(name: String, schule: String, jahrgang: String): Long =
        klasseDao.insert(
            KlasseEntity(
                name = name.trim(),
                schule = schule.trim(),
                jahrgang = jahrgang.trim(),
                erstelltAm = clock.millis(),
            ),
        )

    /**
     * Löscht eine Klasse endgültig – inklusive aller Fotodateien der Schüler.
     * Das CASCADE der DB räumt nur die Zeilen, die Dateien liegen außerhalb.
     */
    suspend fun loeschen(id: Long) {
        val fotos = schuelerDao.fotoDateienFuerKlasse(id)
        klasseDao.delete(id)
        fotoStore.loescheAlle(fotos)
    }
}
