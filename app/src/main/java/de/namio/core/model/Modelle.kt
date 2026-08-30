package de.namio.core.model

import java.time.Instant

/** Eine Schulklasse. */
data class Klasse(
    val id: Long,
    val name: String,
    val schule: String,
    val jahrgang: String,
    val erstelltAm: Instant,
    val archiviert: Boolean,
)

/** Klasse mit den Kennzahlen für die Klassenliste. */
data class KlasseUebersicht(
    val klasse: Klasse,
    val schuelerAnzahl: Int,
    /** Lernfortschritt in Prozent, 0–100. */
    val fortschrittProzent: Int,
)

/** Ein Schüler. [fotoDatei] ist nur der Dateiname im FotoStore, nie ein Pfad. */
data class Schueler(
    val id: Long,
    val klasseId: Long,
    val vorname: String,
    val nachname: String,
    val spitzname: String,
    val fotoDatei: String?,
    val notiz: String,
    val sortIndex: Int,
    val geschlecht: Geschlecht = Geschlecht.MAEDCHEN,
) {
    val anzeigeName: String
        get() = spitzname.ifBlank { vorname }
    val vollerName: String
        get() = listOf(vorname, nachname).filter { it.isNotBlank() }.joinToString(" ")
}

/** Lernstand eines Schülers in einem Modus (Leitner-Box). */
data class Lernkarte(
    val id: Long,
    val schuelerId: Long,
    val modus: QuizModus,
    val box: Int,
    val faelligAm: Instant,
    val serieRichtig: Int,
    val letzteAntwortAm: Instant?,
)
