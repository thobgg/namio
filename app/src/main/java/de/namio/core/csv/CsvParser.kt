package de.namio.core.csv

import de.namio.core.model.Geschlecht
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Eine geparste CSV-Datei: Kopfzeile (oder generierte Spaltennamen) und Datenzeilen. */
data class CsvTabelle(
    val spalten: List<String>,
    val zeilen: List<List<String>>,
    val trennzeichen: Char,
    val zeichensatz: String,
    val hatKopfzeile: Boolean,
)

/** Spaltenzuordnung für den Import. `null` = nicht vorhanden. */
data class Zuordnung(
    val vorname: Int?,
    val nachname: Int?,
    /** Spalte mit „Nachname, Vorname“ in einem Feld. */
    val kombiniert: Int?,
    val geschlecht: Int?,
)

/** Ein importierbarer Schüler. */
data class ImportSchueler(val vorname: String, val nachname: String, val geschlecht: Geschlecht?)

/**
 * CSV-Parser für das, was aus Untis und Excel herausfällt: Trennzeichen automatisch,
 * UTF-8 mit/ohne BOM oder Windows-1252, Anführungszeichen, Kopfzeile optional.
 */
object CsvParser {

    /** Dekodiert Bytes: BOM → UTF-8; sonst UTF-8, wenn gültig, sonst Windows-1252. */
    fun dekodiere(bytes: ByteArray): Pair<String, String> {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8) to "UTF-8 (BOM)"
        }
        val strikt = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            strikt.decode(ByteBuffer.wrap(bytes)).toString() to "UTF-8"
        } catch (e: CharacterCodingException) {
            String(bytes, Charset.forName("windows-1252")) to "Windows-1252"
        }
    }

    /** Erkennt das Trennzeichen (Semikolon, Komma, Tab) am häufigsten konsistenten Vorkommen. */
    fun erkenneTrennzeichen(text: String): Char {
        val zeilen = text.lineSequence().filter { it.isNotBlank() }.take(20).toList()
        if (zeilen.isEmpty()) return ';'
        return listOf(';', ',', '\t').maxByOrNull { t ->
            val zaehlungen = zeilen.map { zaehleAusserhalbAnfuehrung(it, t) }
            val min = zaehlungen.minOrNull() ?: 0
            if (min == 0) 0 else min * 1000 - (zaehlungen.maxOrNull()!! - min)
        } ?: ';'
    }

    private fun zaehleAusserhalbAnfuehrung(zeile: String, t: Char): Int {
        var n = 0
        var inAnf = false
        for (c in zeile) {
            if (c == '"') inAnf = !inAnf else if (c == t && !inAnf) n++
        }
        return n
    }

    /** Zerlegt eine Zeile in Felder, mit `"`-Quoting und `""`-Escapes. */
    fun zerlege(zeile: String, t: Char): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inAnf = false
        var i = 0
        while (i < zeile.length) {
            val c = zeile[i]
            when {
                inAnf && c == '"' && i + 1 < zeile.length && zeile[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> inAnf = !inAnf
                c == t && !inAnf -> { out += sb.toString().trim(); sb.setLength(0) }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString().trim()
        return out
    }

    private val kopfWoerter = listOf("vorname", "nachname", "name", "familienname", "geschlecht", "sex", "first", "last", "klasse", "schüler", "schueler")

    /** Parst den Text in eine Tabelle; erkennt Kopfzeile an typischen Spaltennamen. */
    fun parse(text: String, trennzeichen: Char = erkenneTrennzeichen(text), zeichensatz: String = "UTF-8"): CsvTabelle {
        val roh = text.lineSequence().map { it.trimEnd('\r') }.filter { it.isNotBlank() }.map { zerlege(it, trennzeichen) }.toList()
        if (roh.isEmpty()) return CsvTabelle(emptyList(), emptyList(), trennzeichen, zeichensatz, false)
        val breite = roh.maxOf { it.size }
        val erste = roh.first().map { it.lowercase() }
        val hatKopf = erste.any { z -> kopfWoerter.any { z.contains(it) } }
        val spalten = if (hatKopf) roh.first().mapIndexed { i, s -> s.ifBlank { "Spalte ${i + 1}" } } else List(breite) { "Spalte ${it + 1}" }
        val daten = (if (hatKopf) roh.drop(1) else roh).map { z -> List(breite) { z.getOrElse(it) { "" } } }
        return CsvTabelle(spalten.let { if (it.size < breite) it + List(breite - it.size) { i -> "Spalte ${it.size + i + 1}" } else it }, daten, trennzeichen, zeichensatz, hatKopf)
    }

    /** Schlägt eine Spaltenzuordnung vor – per Kopfzeile, sonst per Inhalt. */
    fun schlageZuordnungVor(t: CsvTabelle): Zuordnung {
        val k = t.spalten.map { it.lowercase() }
        fun finde(vararg namen: String) = k.indexOfFirst { s -> namen.any { s == it || s.contains(it) } }.takeIf { it >= 0 }
        var vor = finde("vorname", "first", "rufname")
        var nach = finde("nachname", "familienname", "last", "surname")
        var komb: Int? = null
        val geschl = finde("geschlecht", "sex", "gender")
        // Kombinierte Spalte „Nachname, Vorname“: Mehrheit der Werte enthält ein Komma
        val kandidat = (0 until t.spalten.size).firstOrNull { i ->
            i != geschl && t.zeilen.isNotEmpty() && t.zeilen.count { it[i].contains(',') } * 2 > t.zeilen.size
        }
        if (vor == null && nach == null) {
            if (kandidat != null) {
                komb = kandidat
            } else {
                val name = finde("name")
                if (name != null) komb = name
                else if (t.spalten.size >= 2) { vor = 0; nach = 1 } else if (t.spalten.size == 1) komb = 0
            }
        } else if (vor == null && nach != null && kandidat == nach) {
            komb = nach; nach = null
        }
        return Zuordnung(vor, nach, komb, geschl)
    }

    /** Interpretiert Geschlechtsangaben (m/w, männlich/weiblich, male/female, Junge/Mädchen). */
    fun parseGeschlecht(text: String): Geschlecht? = when (text.trim().lowercase()) {
        "w", "f", "weiblich", "female", "mädchen", "maedchen", "girl", "2" -> Geschlecht.MAEDCHEN
        "m", "männlich", "maennlich", "male", "junge", "boy", "1" -> Geschlecht.JUNGE
        else -> null
    }

    /** Wandelt die Tabelle mit einer Zuordnung in Schüler um; leere Namen fallen weg. */
    fun schueler(t: CsvTabelle, z: Zuordnung): List<ImportSchueler> = t.zeilen.mapNotNull { zeile ->
        fun feld(i: Int?) = i?.let { zeile.getOrNull(it) }?.trim().orEmpty()
        var vor = feld(z.vorname)
        var nach = feld(z.nachname)
        if (z.kombiniert != null) {
            val k = feld(z.kombiniert)
            if (k.contains(',')) { nach = k.substringBefore(',').trim(); vor = k.substringAfter(',').trim() }
            else if (k.contains(' ')) { vor = k.substringBeforeLast(' ').trim(); nach = k.substringAfterLast(' ').trim() }
            else vor = k
        }
        if (vor.isBlank() && nach.isBlank()) null
        else ImportSchueler(vor, nach, z.geschlecht?.let { parseGeschlecht(feld(it)) })
    }
}
