package de.namio.core.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import de.namio.core.data.NamioDatabase
import de.namio.core.data.entity.KlasseEntity
import de.namio.core.data.entity.LernkarteEntity
import de.namio.core.data.entity.QuizAntwortEntity
import de.namio.core.data.entity.QuizSessionEntity
import de.namio.core.data.entity.SchuelerEntity
import de.namio.core.data.entity.SitzplanEntity
import de.namio.core.data.entity.SitzplatzEntity
import de.namio.core.data.entity.TischEntity
import de.namio.core.media.FotoStore
import de.namio.core.model.Geschlecht
import de.namio.core.model.QuizModus
import de.namio.core.transfer.AntwortX
import de.namio.core.transfer.ExportDaten
import de.namio.core.transfer.ExportFormat
import de.namio.core.transfer.KlasseX
import de.namio.core.transfer.LernkarteX
import de.namio.core.transfer.PlatzX
import de.namio.core.transfer.SchuelerX
import de.namio.core.transfer.SessionX
import de.namio.core.transfer.SitzplanX
import de.namio.core.transfer.TischX
import de.namio.core.transfer.Tresor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Clock
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Export und Import der kompletten Daten als passwortverschlüsselte `.namio`-Datei. */
@Singleton
class TransferRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: NamioDatabase,
    private val fotoStore: FotoStore,
    private val klassenRepository: KlassenRepository,
    private val clock: Clock,
) {
    /** Schreibt alle Daten verschlüsselt nach [ziel]. Liefert die Anzahl exportierter Schüler. */
    suspend fun exportieren(ziel: Uri, passwort: CharArray): Int = withContext(Dispatchers.IO) {
        val daten = sammle()
        val zip = ByteArrayOutputStream()
        ZipOutputStream(zip).use { z ->
            z.putNextEntry(ZipEntry(ExportFormat.DATEN_EINTRAG))
            z.write(ExportFormat.kodiere(daten).toByteArray(Charsets.UTF_8))
            z.closeEntry()
            for (f in fotoStore.alleDateien()) {
                z.putNextEntry(ZipEntry(ExportFormat.FOTO_ORDNER + f.name))
                f.inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
        }
        val geheim = Tresor.verschluessele(zip.toByteArray(), passwort)
        context.contentResolver.openOutputStream(ziel, "wt")?.use { it.write(geheim) } ?: throw IOException("Ziel nicht beschreibbar")
        daten.schueler.size
    }

    /** Liest eine `.namio`-Datei und ERSETZT alle vorhandenen Daten. Liefert die Anzahl importierter Schüler. */
    suspend fun importieren(quelle: Uri, passwort: CharArray): Int = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(quelle)?.use { it.readBytes() } ?: throw IOException("Datei nicht lesbar")
        val zipBytes = Tresor.entschluessele(bytes, passwort)
        var daten: ExportDaten? = null
        val fotos = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { z ->
            var e = z.nextEntry
            while (e != null) {
                when {
                    e.name == ExportFormat.DATEN_EINTRAG -> daten = ExportFormat.dekodiere(z.readBytes().toString(Charsets.UTF_8))
                    e.name.startsWith(ExportFormat.FOTO_ORDNER) -> fotos[e.name.removePrefix(ExportFormat.FOTO_ORDNER)] = z.readBytes()
                }
                e = z.nextEntry
            }
        }
        val d = daten ?: throw IOException("Keine Daten in der Datei")
        klassenRepository.allesLoeschen()
        db.withTransaction {
            d.klassen.forEach { db.klasseDao().insert(KlasseEntity(it.id, it.name, it.schule, it.jahrgang, it.erstelltAm, it.archiviert)) }
            d.schueler.forEach {
                db.schuelerDao().insert(SchuelerEntity(it.id, it.klasseId, it.vorname, it.nachname, it.spitzname, it.fotoDatei, it.notiz, it.sortIndex, runCatching { Geschlecht.valueOf(it.geschlecht) }.getOrDefault(Geschlecht.MAEDCHEN)))
            }
            d.lernkarten.forEach { db.lernkarteDao().upsert(LernkarteEntity(it.id, it.schuelerId, QuizModus.valueOf(it.modus), it.box, it.faelligAm, it.serieRichtig, it.letzteAntwortAm)) }
            d.sessions.forEach { db.quizSessionDao().insert(QuizSessionEntity(it.id, it.klasseId, QuizModus.valueOf(it.modus), it.startedAt, it.endedAt, it.anzahlRichtig, it.anzahlFalsch)) }
            d.antworten.forEach { db.quizAntwortDao().insert(QuizAntwortEntity(it.id, it.sessionId, it.schuelerId, it.verwechseltMit, it.korrekt, it.dauerMs, it.zeitpunkt)) }
            d.sitzplaene.forEach { db.sitzplanDao().insert(SitzplanEntity(it.id, it.klasseId, it.name, it.spalten, it.reihen, it.istStandard, it.einrasten)) }
            d.tische.forEach { db.tischDao().insert(TischEntity(it.id, it.sitzplanId, it.x, it.y, it.drehung, it.plaetze, it.beschriftung, it.breite)) }
            d.plaetze.forEach { db.sitzplatzDao().insert(SitzplatzEntity(it.id, it.sitzplanId, it.tischId, it.slot, it.schuelerId)) }
        }
        fotos.forEach { (name, b) -> fotoStore.schreibeRoh(name, b) }
        d.schueler.size
    }

    private suspend fun sammle(): ExportDaten = ExportDaten(
        exportiertAm = clock.millis(),
        klassen = db.klasseDao().alle().map { KlasseX(it.id, it.name, it.schule, it.jahrgang, it.erstelltAm, it.archiviert) },
        schueler = db.schuelerDao().alle().map { SchuelerX(it.id, it.klasseId, it.vorname, it.nachname, it.spitzname, it.fotoDatei, it.notiz, it.sortIndex, it.geschlecht.name) },
        lernkarten = db.lernkarteDao().alle().map { LernkarteX(it.id, it.schuelerId, it.modus.name, it.box, it.faelligAm, it.serieRichtig, it.letzteAntwortAm) },
        sessions = db.quizSessionDao().alle().map { SessionX(it.id, it.klasseId, it.modus.name, it.startedAt, it.endedAt, it.anzahlRichtig, it.anzahlFalsch) },
        antworten = db.quizAntwortDao().alle().map { AntwortX(it.id, it.sessionId, it.schuelerId, it.verwechseltMit, it.korrekt, it.dauerMs, it.zeitpunkt) },
        sitzplaene = db.sitzplanDao().alle().map { SitzplanX(it.id, it.klasseId, it.name, it.spalten, it.reihen, it.istStandard, it.einrasten) },
        tische = db.tischDao().alle().map { TischX(it.id, it.sitzplanId, it.x, it.y, it.drehung, it.plaetze, it.beschriftung, it.breite) },
        plaetze = db.sitzplatzDao().alle().map { PlatzX(it.id, it.sitzplanId, it.tischId, it.slot, it.schuelerId) },
    )
}
