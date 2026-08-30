package de.namio.core.model

/** Ein Sitzplan einer Klasse (Klassenraum, Fachraum, Prüfung …). */
data class Sitzplan(
    val id: Long,
    val klasseId: Long,
    val name: String,
    val spalten: Int,
    val reihen: Int,
    val istStandard: Boolean,
    /** Doppeltische: nach jeder zweiten Spalte ein Gang. */
    val doppeltische: Boolean = true,
)

/** Ein Platz im Raster. [schuelerId] = null ist ein leerer Stuhl. */
data class Sitzplatz(
    val id: Long,
    val sitzplanId: Long,
    val schuelerId: Long?,
    val spalte: Int,
    val reihe: Int,
)

/** Darstellung des Plans: von vorn (wie der Lehrer ihn an der Tafel sieht) oder von hinten (wie er auf dem Papier steht). */
enum class Blickrichtung {
    VON_VORN,
    VON_HINTEN,
}

/** Rasterkoordinate. */
data class Position(val spalte: Int, val reihe: Int)
