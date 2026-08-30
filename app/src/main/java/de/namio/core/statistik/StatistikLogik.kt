package de.namio.core.statistik

import de.namio.core.model.Lernkarte
import de.namio.core.model.QuizModus
import de.namio.core.model.Schueler
import de.namio.core.model.SessionKurz
import de.namio.core.model.Verwechslung
import de.namio.core.model.VerwechslungRoh

/** Reine Auswertungen für den Statistikscreen. */
object StatistikLogik {

    /**
     * Verteilung der Leitner-Boxen für einen Modus: Index 0 = Schüler ohne Karte, 1–5 = Box.
     * Schüler, die nicht in [schuelerIds] stehen, werden ignoriert.
     */
    fun boxverteilung(karten: List<Lernkarte>, schuelerIds: Collection<Long>, modus: QuizModus): IntArray {
        val out = IntArray(6)
        val ids = schuelerIds.toSet()
        val mitKarte = karten.filter { it.modus == modus && it.schuelerId in ids }
        mitKarte.forEach { out[it.box.coerceIn(1, 5)]++ }
        out[0] = (ids.size - mitKarte.map { it.schuelerId }.toSet().size).coerceAtLeast(0)
        return out
    }

    /** Verwechslungen richtungsunabhängig zusammengefasst, häufigste zuerst, höchstens [limit]. */
    fun verwechslungsPaare(roh: List<VerwechslungRoh>, schueler: Map<Long, Schueler>, limit: Int = 10): List<Verwechslung> {
        val summen = HashMap<Pair<Long, Long>, Int>()
        for (r in roh) {
            if (r.schuelerId == r.verwechseltMit) continue
            val key = if (r.schuelerId < r.verwechseltMit) r.schuelerId to r.verwechseltMit else r.verwechseltMit to r.schuelerId
            summen[key] = (summen[key] ?: 0) + r.anzahl
        }
        return summen.entries
            .mapNotNull { (k, n) ->
                val a = schueler[k.first] ?: return@mapNotNull null
                val b = schueler[k.second] ?: return@mapNotNull null
                Verwechslung(a, b, n)
            }
            .sortedWith(compareByDescending<Verwechslung> { it.anzahl }.thenBy { it.a.vollerName })
            .take(limit)
    }

    /** Die letzten [limit] Runden mit mindestens einer Antwort, chronologisch. */
    fun verlauf(sessions: List<SessionKurz>, modus: QuizModus?, limit: Int = 20): List<SessionKurz> =
        sessions
            .filter { it.gesamt > 0 && (modus == null || it.modus == modus) }
            .sortedBy { it.startedAt }
            .takeLast(limit)
}
