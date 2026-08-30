package de.namio.core.model

import java.time.Instant

/** Eine abgeschlossene Quizrunde in Kurzform. */
data class SessionKurz(
    val id: Long,
    val modus: QuizModus,
    val startedAt: Instant,
    val richtig: Int,
    val falsch: Int,
) {
    val gesamt: Int get() = richtig + falsch
    val prozent: Int get() = if (gesamt == 0) 0 else richtig * 100 / gesamt
}

/** Wie oft [a] und [b] in beide Richtungen verwechselt wurden. */
data class Verwechslung(val a: Schueler, val b: Schueler, val anzahl: Int)

/** Rohzählung aus der Datenbank. */
data class VerwechslungRoh(val schuelerId: Long, val verwechseltMit: Long, val anzahl: Int)
