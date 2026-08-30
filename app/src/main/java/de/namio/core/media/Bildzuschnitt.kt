package de.namio.core.media

/** Quadratischer Ausschnitt in Pixelkoordinaten des Quellbilds. */
data class Ausschnitt(val x: Int, val y: Int, val groesse: Int)

/** Reine Geometrie für den Fotozuschnitt – ohne Android-Abhängigkeiten, damit testbar. */
object Bildzuschnitt {

    /** Größte mittige Quadratfläche eines Bilds mit [breite] × [hoehe]. */
    fun mittigesQuadrat(breite: Int, hoehe: Int): Ausschnitt {
        require(breite > 0 && hoehe > 0) { "Bildmaße müssen positiv sein" }
        val groesse = minOf(breite, hoehe)
        return Ausschnitt(x = (breite - groesse) / 2, y = (hoehe - groesse) / 2, groesse = groesse)
    }

    /** Kantenlänge nach dem Verkleinern: nie größer als [maximal], nie hochskaliert. */
    fun zielKante(groesse: Int, maximal: Int): Int = minOf(groesse, maximal)

    /**
     * Zweierpotenz-Faktor für `BitmapFactory.inSampleSize`, sodass die kürzere Kante nach dem
     * Dekodieren noch mindestens [mindestKante] Pixel hat.
     */
    fun sampleSize(breite: Int, hoehe: Int, mindestKante: Int): Int {
        var faktor = 1
        val kurz = minOf(breite, hoehe)
        while (kurz / (faktor * 2) >= mindestKante) faktor *= 2
        return faktor
    }
}
