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

/**
 * Ein Tisch im Raum: [plaetze] Sitzplätze nebeneinander (0 = Möbel wie Pult oder PC).
 * [x]/[y] ist der Mittelpunkt, normiert 0–1; [drehung] in Grad im Uhrzeigersinn.
 * Neue, noch nicht gespeicherte Tische haben eine negative [id].
 */
data class Tisch(
    val id: Long,
    val sitzplanId: Long,
    val x: Float,
    val y: Float,
    val drehung: Float = 0f,
    val plaetze: Int = 1,
    val beschriftung: String? = null,
) {
    val istMoebel: Boolean get() = plaetze == 0
}

/** Ein Sitzplatz-Slot auf einem Tisch. [schuelerId] = null ist ein leerer Stuhl. */
data class Sitzplatz(
    val id: Long,
    val sitzplanId: Long,
    val tischId: Long,
    val slot: Int,
    val schuelerId: Long?,
)

/** Tische und Plätze eines Plans zusammen – die Einheit, auf der die Logik arbeitet. */
data class Bestuhlung(
    val tische: List<Tisch> = emptyList(),
    val plaetze: List<Sitzplatz> = emptyList(),
) {
    fun plaetzeVon(tischId: Long): List<Sitzplatz> = plaetze.filter { it.tischId == tischId }.sortedBy { it.slot }
    fun tisch(id: Long): Tisch? = tische.firstOrNull { it.id == id }
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
