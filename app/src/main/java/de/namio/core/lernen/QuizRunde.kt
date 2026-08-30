package de.namio.core.lernen

/**
 * Ablauf einer Runde: eine Warteschlange von Schüler-IDs. Falsch beantwortete kommen später in
 * derselben Runde noch einmal dran, möglichst nicht direkt hintereinander.
 */
class QuizRunde(start: List<Long>) {
    private val warteschlange = ArrayDeque(start.distinct())
    private val gesamt = warteschlange.size
    private var erledigt = 0
    private var zuletzt: Long? = null

    /** Anzahl unterschiedlicher Schüler in dieser Runde. */
    val anzahl: Int get() = gesamt

    /** Wie viele Schüler schon richtig beantwortet wurden. */
    val fertig: Int get() = erledigt

    /** Fortschritt 0f..1f. */
    val fortschritt: Float get() = if (gesamt == 0) 1f else erledigt.toFloat() / gesamt

    val istZuEnde: Boolean get() = warteschlange.isEmpty()

    /** Der aktuell abzufragende Schüler oder `null`, wenn die Runde vorbei ist. */
    val aktuell: Long? get() = warteschlange.firstOrNull()

    /**
     * Verbucht die Antwort auf [aktuell]. Bei falscher Antwort wird der Schüler weiter hinten
     * wieder eingereiht (mindestens [MINDEST_ABSTAND] andere dazwischen, sofern möglich).
     */
    fun antworte(korrekt: Boolean) {
        val id = warteschlange.removeFirstOrNull() ?: return
        zuletzt = id
        if (korrekt) {
            erledigt++
        } else {
            val position = minOf(MINDEST_ABSTAND, warteschlange.size)
            warteschlange.add(position, id)
        }
    }

    companion object {
        const val MINDEST_ABSTAND = 2
    }
}
