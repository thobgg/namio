package de.namio.core.lernen

import de.namio.core.model.Lernkarte
import de.namio.core.model.QuizModus
import java.time.Duration
import java.time.Instant

/**
 * Leitner-System mit fünf Boxen und bewusst kurzen Intervallen: die Namen werden am Montag
 * gebraucht, nicht in drei Monaten. Richtig = eine Box hoch, falsch = zurück auf Box 1.
 */
object Leitner {
    const val ERSTE_BOX = 1
    const val LETZTE_BOX = 5

    /** Wartezeit bis zur nächsten Fälligkeit für eine Box. */
    fun intervall(box: Int): Duration = when (box.coerceIn(ERSTE_BOX, LETZTE_BOX)) {
        1 -> Duration.ZERO
        2 -> Duration.ofMinutes(10)
        3 -> Duration.ofDays(1)
        4 -> Duration.ofDays(3)
        else -> Duration.ofDays(7)
    }

    /** Neue Box nach einer Antwort. */
    fun neueBox(box: Int, korrekt: Boolean): Int =
        if (korrekt) (box + 1).coerceIn(ERSTE_BOX, LETZTE_BOX) else ERSTE_BOX

    /**
     * Wendet eine Antwort auf eine Karte an. Fehlt die Karte (Schüler wurde in diesem Modus
     * noch nie abgefragt), entsteht sie hier mit Box 1 als Ausgangslage.
     */
    fun anwenden(
        karte: Lernkarte?,
        schuelerId: Long,
        modus: QuizModus,
        korrekt: Boolean,
        jetzt: Instant,
    ): Lernkarte {
        val alteBox = karte?.box ?: ERSTE_BOX
        val box = neueBox(alteBox, korrekt)
        return Lernkarte(
            id = karte?.id ?: 0,
            schuelerId = schuelerId,
            modus = modus,
            box = box,
            faelligAm = jetzt.plus(intervall(box)),
            serieRichtig = if (korrekt) (karte?.serieRichtig ?: 0) + 1 else 0,
            letzteAntwortAm = jetzt,
        )
    }
}
