package de.namio.core.lernen

import de.namio.core.model.Schueler
import java.text.Normalizer

/**
 * Vergleicht getippte Namen tolerant: Groß-/Kleinschreibung und diakritische Zeichen werden
 * ignoriert, Tippfehler bis zu einer Levenshtein-Distanz von 1 (kurze Namen) bzw. 2 (ab fünf
 * Zeichen) gelten als richtig.
 */
object NamensVergleich {

    /** Normalisiert für den Vergleich: klein, ohne Diakritika, Mehrfach-Leerzeichen zusammengefasst. */
    fun normalisiere(text: String): String =
        Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .replace("ß", "ss")
            .replace(Regex("\\s+"), " ")

    /** Klassische Levenshtein-Distanz. */
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var vorher = IntArray(b.length + 1) { it }
        var aktuell = IntArray(b.length + 1)
        for (i in 1..a.length) {
            aktuell[0] = i
            for (j in 1..b.length) {
                val kosten = if (a[i - 1] == b[j - 1]) 0 else 1
                aktuell[j] = minOf(vorher[j] + 1, aktuell[j - 1] + 1, vorher[j - 1] + kosten)
            }
            val t = vorher; vorher = aktuell; aktuell = t
        }
        return vorher[b.length]
    }

    /** Erlaubte Distanz: 2 ab fünf Zeichen, sonst 1. */
    fun toleranz(erwartet: String): Int = if (normalisiere(erwartet).length >= 5) 2 else 1

    /** Passt [eingabe] zu [erwartet] innerhalb der Toleranz? Leere Eingabe passt nie. */
    fun passt(eingabe: String, erwartet: String): Boolean {
        val e = normalisiere(eingabe)
        if (e.isEmpty()) return false
        val z = normalisiere(erwartet)
        return levenshtein(e, z) <= toleranz(z)
    }

    /**
     * Welche Antworten für [ziel] gelten: der Vorname – oder Vor- und Nachname, wenn ein anderer
     * Schüler der Klasse denselben Vornamen trägt. Ein gesetzter Spitzname gilt zusätzlich.
     */
    fun erwarteteAntworten(ziel: Schueler, klasse: List<Schueler>): List<String> {
        val doppelt = klasse.any { it.id != ziel.id && normalisiere(it.vorname) == normalisiere(ziel.vorname) }
        val haupt = if (doppelt) ziel.vollerName else ziel.vorname
        return listOfNotNull(haupt, ziel.spitzname.takeIf { it.isNotBlank() }, ziel.vollerName.takeIf { !doppelt && it != ziel.vorname })
    }

    /** Ist die Eingabe für das Ziel richtig? */
    fun istRichtig(eingabe: String, ziel: Schueler, klasse: List<Schueler>): Boolean =
        erwarteteAntworten(ziel, klasse).any { passt(eingabe, it) }

    /** Mit welchem Mitschüler wurde verwechselt? `null`, wenn die Eingabe zu niemandem passt. */
    fun verwechseltMit(eingabe: String, ziel: Schueler, klasse: List<Schueler>): Schueler? =
        klasse.filter { it.id != ziel.id }.firstOrNull { anderer -> erwarteteAntworten(anderer, klasse).any { passt(eingabe, it) } }
}
