package de.namio.core.model

/** Ein Sitzplan einer Klasse (Klassenraum, Fachraum, Prüfung …). Der Raum ist [spalten] × [reihen] Einheiten groß. */
data class Sitzplan(
    val id: Long,
    val klasseId: Long,
    val name: String,
    val spalten: Int,
    val reihen: Int,
    val istStandard: Boolean,
    val einrasten: Boolean = true,
)

/** Ein frei platzierter Platz. [schuelerId] = null ist ein leerer Stuhl. [x]/[y] sind der Mittelpunkt, normiert 0–1. */
data class Sitzplatz(
    val id: Long,
    val sitzplanId: Long,
    val schuelerId: Long?,
    val x: Float,
    val y: Float,
    val drehung: Float = 0f,
    /** Gesetzt bei Möbeln ohne Schüler (Pult, PC, Schrank …). */
    val beschriftung: String? = null,
) {
    val istMoebel: Boolean get() = beschriftung != null
}

/** Darstellung des Plans: von vorn (wie der Lehrer ihn an der Tafel sieht) oder von hinten (wie er auf dem Papier steht). */
enum class Blickrichtung {
    VON_VORN,
    VON_HINTEN,
}

/** Startvorlagen für neue Pläne. */
enum class SitzplanVorlage {
    LEER,
    DOPPELTISCH_REIHEN,
    U_FORM,
    GRUPPENTISCHE,
}
