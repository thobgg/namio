package de.namio.core.lernen

import de.namio.core.model.Schueler
import java.text.Normalizer
import kotlin.random.Random

/**
 * Wählt falsche Antwortmöglichkeiten. Reihenfolge der Kandidaten:
 * 1. Schüler, mit denen das Ziel schon verwechselt wurde (häufigste zuerst),
 * 2. Schüler mit gleichem Anfangsbuchstaben des Vornamens,
 * 3. zufälliges Auffüllen.
 * Zuerst kommen nur Kandidaten mit gleichem Geschlecht in Frage – ein Jungenname unter einem
 * Mädchenfoto wäre trivial auszuschließen. Reichen die nicht, werden die übrigen nachgezogen.
 * Alle Kandidaten müssen aus derselben Klasse stammen – das stellt der Aufrufer sicher.
 */
class AblenkerWaehler(private val random: Random = Random.Default) {

    /**
     * @param ziel der abgefragte Schüler
     * @param kandidaten alle Schüler der Klasse (das Ziel darf enthalten sein, wird ignoriert)
     * @param verwechslungen IDs, mit denen [ziel] verwechselt wurde, häufigste zuerst
     * @param anzahl gewünschte Zahl an Ablenkern; bei zu wenig Kandidaten weniger
     */
    fun waehle(
        ziel: Schueler,
        kandidaten: List<Schueler>,
        verwechslungen: List<Long>,
        anzahl: Int,
    ): List<Schueler> {
        val alle = kandidaten.filter { it.id != ziel.id }.distinctBy { it.id }
        if (anzahl <= 0 || alle.isEmpty()) return emptyList()
        val passend = alle.filter { it.geschlecht == ziel.geschlecht }
        val gewaehlt = waehleAus(ziel, passend, verwechslungen, anzahl)
        if (gewaehlt.size >= anzahl || passend.size == alle.size) return gewaehlt
        val rest = alle.filter { r -> gewaehlt.none { it.id == r.id } }
        return gewaehlt + waehleAus(ziel, rest, verwechslungen, anzahl - gewaehlt.size)
    }

    private fun waehleAus(
        ziel: Schueler,
        pool: List<Schueler>,
        verwechslungen: List<Long>,
        anzahl: Int,
    ): List<Schueler> {
        if (anzahl <= 0 || pool.isEmpty()) return emptyList()
        val proId = pool.associateBy { it.id }
        val gewaehlt = LinkedHashMap<Long, Schueler>()

        for (id in verwechslungen) {
            if (gewaehlt.size >= anzahl) break
            proId[id]?.let { gewaehlt[id] = it }
        }
        if (gewaehlt.size < anzahl) {
            val zielBuchstabe = anfangsbuchstabe(ziel.vorname)
            val gleicher = pool
                .filter { it.id !in gewaehlt && zielBuchstabe != null && anfangsbuchstabe(it.vorname) == zielBuchstabe }
                .shuffled(random)
            for (s in gleicher) {
                if (gewaehlt.size >= anzahl) break
                gewaehlt[s.id] = s
            }
        }
        if (gewaehlt.size < anzahl) {
            val rest = pool.filter { it.id !in gewaehlt }.shuffled(random)
            for (s in rest) {
                if (gewaehlt.size >= anzahl) break
                gewaehlt[s.id] = s
            }
        }
        return gewaehlt.values.toList()
    }

    private fun anfangsbuchstabe(name: String): Char? {
        val normalisiert = Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
        return normalisiert.firstOrNull()?.lowercaseChar()
    }
}
