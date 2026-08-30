package de.namio.core.lernen

import de.namio.core.model.Lernkarte
import java.time.Instant

/** Bestimmt, welche Schüler in einer Runde drankommen und in welcher Reihenfolge. */
object KartenAuswahl {
    const val STANDARD_MAX_NEUE = 5

    /**
     * Reihenfolge der Schüler-IDs für eine Runde: erst alle fälligen Karten nach `faelligAm`
     * aufsteigend, dann Schüler ohne Lernstand (in der übergebenen Reihenfolge), höchstens
     * [maxNeue] davon. Schüler, die nicht in [kandidaten] stehen, werden ignoriert.
     */
    fun reihenfolge(
        kandidaten: List<Long>,
        karten: List<Lernkarte>,
        jetzt: Instant,
        maxNeue: Int = STANDARD_MAX_NEUE,
    ): List<Long> {
        val erlaubt = kandidaten.toSet()
        val kartenProSchueler = karten.filter { it.schuelerId in erlaubt }.associateBy { it.schuelerId }
        val faellig = kartenProSchueler.values
            .filter { !it.faelligAm.isAfter(jetzt) }
            .sortedBy { it.faelligAm }
            .map { it.schuelerId }
        val neue = kandidaten.filter { it !in kartenProSchueler }.take(maxNeue.coerceAtLeast(0))
        return faellig + neue
    }

    /** Anzahl fälliger plus neuer Karten – für die Kacheln der Quizauswahl. */
    fun anzahlFaellig(kandidaten: List<Long>, karten: List<Lernkarte>, jetzt: Instant): Int =
        reihenfolge(kandidaten, karten, jetzt, maxNeue = Int.MAX_VALUE).size
}
