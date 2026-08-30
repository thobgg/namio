package de.namio.core.repository

import android.net.Uri
import de.namio.core.data.dao.LernkarteDao
import de.namio.core.data.dao.SchuelerDao
import de.namio.core.data.entity.SchuelerEntity
import de.namio.core.media.FotoStore
import de.namio.core.model.Geschlecht
import de.namio.core.model.Lernkarte
import de.namio.core.model.Schueler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Einzige Datenquelle für Schüler und ihre Fotos. */
@Singleton
class SchuelerRepository @Inject constructor(
    private val schuelerDao: SchuelerDao,
    private val lernkarteDao: LernkarteDao,
    private val fotoStore: FotoStore,
) {
    /** Schüler einer Klasse in Anzeige-Reihenfolge. */
    fun observeFuerKlasse(klasseId: Long): Flow<List<Schueler>> =
        schuelerDao.observeFuerKlasse(klasseId).map { liste -> liste.map { it.zuModell() } }

    /** Ein Schüler; `null`, wenn er nicht (mehr) existiert. */
    fun observe(id: Long): Flow<Schueler?> = schuelerDao.observe(id).map { it?.zuModell() }

    /** Lernstand eines Schülers über alle Modi, in denen er schon geübt wurde. */
    fun observeLernstand(schuelerId: Long): Flow<List<Lernkarte>> =
        lernkarteDao.observeFuerSchueler(schuelerId).map { liste -> liste.map { it.zuModell() } }

    /** Legt einen Schüler ans Ende der Klasse und liefert seine ID. */
    suspend fun anlegen(klasseId: Long, vorname: String, nachname: String, geschlecht: Geschlecht): Long =
        schuelerDao.insert(
            SchuelerEntity(
                klasseId = klasseId,
                vorname = vorname.trim(),
                nachname = nachname.trim(),
                sortIndex = schuelerDao.naechsterSortIndex(klasseId),
                geschlecht = geschlecht,
            ),
        )

    /** Speichert Namensfelder und Notiz. Das Foto wird hier nicht angefasst. */
    suspend fun aktualisieren(schueler: Schueler) {
        val aktuell = schuelerDao.get(schueler.id) ?: return
        schuelerDao.update(
            aktuell.copy(
                vorname = schueler.vorname.trim(),
                nachname = schueler.nachname.trim(),
                spitzname = schueler.spitzname.trim(),
                notiz = schueler.notiz.trim(),
                geschlecht = schueler.geschlecht,
            ),
        )
    }

    /** Speichert ein Kamerabild als neues Foto und ersetzt das alte. */
    suspend fun fotoSetzen(schuelerId: Long, jpegBytes: ByteArray, rotationGrad: Int, spiegeln: Boolean) {
        val neu = fotoStore.speichere(jpegBytes, rotationGrad, spiegeln)
        fotoTauschen(schuelerId, neu)
    }

    /** Übernimmt ein Bild aus dem Photo Picker als Foto. */
    suspend fun fotoSetzen(schuelerId: Long, uri: Uri) {
        val neu = fotoStore.speichereAusUri(uri)
        fotoTauschen(schuelerId, neu)
    }

    /** Übernimmt einen mitgelieferten Standard-Avatar als Foto. */
    suspend fun avatarSetzen(schuelerId: Long, avatarName: String) {
        val neu = fotoStore.speichereAvatar(avatarName)
        fotoTauschen(schuelerId, neu)
    }

    /** Entfernt das Foto inklusive Datei. */
    suspend fun fotoEntfernen(schuelerId: Long) {
        val alt = schuelerDao.get(schuelerId)?.fotoDatei
        schuelerDao.setzeFoto(schuelerId, null)
        fotoStore.loesche(alt)
    }

    /** Löscht einen Schüler endgültig, samt Fotodatei. */
    suspend fun loeschen(schuelerId: Long) {
        val alt = schuelerDao.get(schuelerId)?.fotoDatei
        schuelerDao.delete(schuelerId)
        fotoStore.loesche(alt)
    }

    private suspend fun fotoTauschen(schuelerId: Long, neu: String) {
        val alt = schuelerDao.get(schuelerId)
        if (alt == null) {
            // Schüler wurde zwischenzeitlich gelöscht – Datei nicht verwaisen lassen.
            fotoStore.loesche(neu)
            return
        }
        schuelerDao.setzeFoto(schuelerId, neu)
        fotoStore.loesche(alt.fotoDatei)
    }
}
