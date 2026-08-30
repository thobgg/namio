package de.namio.core.repository

/** Berechnet den Lernfortschritt einer Klasse aus den Leitner-Boxen. */
object Fortschritt {
    const val MAX_BOX = 5

    /**
     * Prozentwert 0–100. Jede Karte trägt ihre Box (1–5) bei, Schüler ohne Karte zählen als 0.
     * Vollständig gelernt heißt: alle Schüler in Box 5.
     */
    fun prozent(boxSumme: Int, schuelerAnzahl: Int): Int {
        if (schuelerAnzahl <= 0) return 0
        val maximum = schuelerAnzahl * MAX_BOX
        return (boxSumme.coerceIn(0, maximum) * 100) / maximum
    }
}
