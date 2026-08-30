package de.namio.core.model

/** Eine Frage: [ziel] wird abgefragt, [optionen] enthält das Ziel plus Ablenker in Anzeigereihenfolge. */
data class QuizFrage(
    val ziel: Schueler,
    val optionen: List<Schueler>,
)

/** Ein Fehler in der Runde, fürs Ergebnis. */
data class QuizFehler(
    val schueler: Schueler,
    val verwechseltMit: Schueler?,
)
