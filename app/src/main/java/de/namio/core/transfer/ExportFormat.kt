package de.namio.core.transfer

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Alles, was eine Namio-Installation ausmacht – als portables JSON im Export-ZIP (`daten.json`). */
@Serializable
data class ExportDaten(
    val version: Int = 1,
    val exportiertAm: Long,
    val klassen: List<KlasseX>,
    val schueler: List<SchuelerX>,
    val lernkarten: List<LernkarteX>,
    val sessions: List<SessionX>,
    val antworten: List<AntwortX>,
    val sitzplaene: List<SitzplanX>,
    val tische: List<TischX>,
    val plaetze: List<PlatzX>,
)

@Serializable data class KlasseX(val id: Long, val name: String, val schule: String, val jahrgang: String, val erstelltAm: Long, val archiviert: Boolean)
@Serializable data class SchuelerX(val id: Long, val klasseId: Long, val vorname: String, val nachname: String, val spitzname: String, val fotoDatei: String?, val notiz: String, val sortIndex: Int, val geschlecht: String)
@Serializable data class LernkarteX(val id: Long, val schuelerId: Long, val modus: String, val box: Int, val faelligAm: Long, val serieRichtig: Int, val letzteAntwortAm: Long?)
@Serializable data class SessionX(val id: Long, val klasseId: Long, val modus: String, val startedAt: Long, val endedAt: Long?, val anzahlRichtig: Int, val anzahlFalsch: Int)
@Serializable data class AntwortX(val id: Long, val sessionId: Long, val schuelerId: Long, val verwechseltMit: Long?, val korrekt: Boolean, val dauerMs: Long, val zeitpunkt: Long)
@Serializable data class SitzplanX(val id: Long, val klasseId: Long, val name: String, val spalten: Int, val reihen: Int, val istStandard: Boolean, val einrasten: Boolean)
@Serializable data class TischX(val id: Long, val sitzplanId: Long, val x: Float, val y: Float, val drehung: Float, val plaetze: Int, val beschriftung: String?, val breite: Float)
@Serializable data class PlatzX(val id: Long, val sitzplanId: Long, val tischId: Long, val slot: Int, val schuelerId: Long?)

object ExportFormat {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    const val DATEN_EINTRAG = "daten.json"
    const val FOTO_ORDNER = "fotos/"
    const val DATEIENDUNG = ".namio"

    fun kodiere(daten: ExportDaten): String = json.encodeToString(ExportDaten.serializer(), daten)
    fun dekodiere(text: String): ExportDaten = json.decodeFromString(ExportDaten.serializer(), text)
}
