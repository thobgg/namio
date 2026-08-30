package de.namio.core.sitzplan

import de.namio.core.model.Blickrichtung
import de.namio.core.model.Position
import de.namio.core.model.Sitzplatz
import kotlin.random.Random

/** Reine Logik für Sitzpläne – ohne Android, damit testbar. */
object SitzplanLogik {

    /**
     * Setzt [schuelerId] auf ([spalte], [reihe]) und liefert die neue Platzliste.
     * Sitzt der Schüler schon woanders, wandert er; ist das Ziel belegt, tauschen beide.
     * Ein leerer Stuhl am Ziel bleibt als Platz erhalten und wird belegt.
     */
    fun setzen(plaetze: List<Sitzplatz>, sitzplanId: Long, schuelerId: Long, spalte: Int, reihe: Int): List<Sitzplatz> {
        val alt = plaetze.firstOrNull { it.schuelerId == schuelerId }
        val ziel = plaetze.firstOrNull { it.spalte == spalte && it.reihe == reihe }
        if (alt != null && ziel != null && alt.id == ziel.id) return plaetze
        val rest = plaetze.filter { it.id != alt?.id && it.id != ziel?.id }
        val neu = mutableListOf<Sitzplatz>()
        neu += Sitzplatz(ziel?.id ?: 0, sitzplanId, schuelerId, spalte, reihe)
        if (alt != null) {
            // alter Platz: Tausch mit dem bisherigen Ziel-Schüler, sonst leerer Stuhl bleibt bestehen
            neu += alt.copy(schuelerId = ziel?.schuelerId)
        }
        return rest + neu
    }

    /** Nimmt [schuelerId] vom Plan, der Stuhl bleibt leer stehen. */
    fun entfernen(plaetze: List<Sitzplatz>, schuelerId: Long): List<Sitzplatz> =
        plaetze.map { if (it.schuelerId == schuelerId) it.copy(schuelerId = null) else it }

    /** Verteilt die sitzenden Schüler zufällig auf ihre bisherigen Plätze (leere Stühle bleiben leer). */
    fun mischen(plaetze: List<Sitzplatz>, random: Random = Random.Default): List<Sitzplatz> {
        val belegt = plaetze.filter { it.schuelerId != null }
        val ids = belegt.mapNotNull { it.schuelerId }.shuffled(random)
        val neuZuordnung = belegt.map { it.id }.zip(ids).toMap()
        return plaetze.map { p -> if (p.id in neuZuordnung) p.copy(schuelerId = neuZuordnung.getValue(p.id)) else p }
    }

    /** Plätze außerhalb eines verkleinerten Rasters. */
    fun ausserhalb(plaetze: List<Sitzplatz>, spalten: Int, reihen: Int): List<Sitzplatz> =
        plaetze.filter { it.spalte >= spalten || it.reihe >= reihen }

    /**
     * Rechnet eine gespeicherte Position (Papieransicht: Tafel oben, Spalte 0 links) in die
     * Anzeigeposition um. Von vorn ist der Plan um 180° gedreht: die Tafel liegt beim Betrachter.
     */
    fun anzeigePosition(position: Position, spalten: Int, reihen: Int, blickrichtung: Blickrichtung): Position =
        when (blickrichtung) {
            Blickrichtung.VON_HINTEN -> position
            Blickrichtung.VON_VORN -> Position(spalten - 1 - position.spalte, reihen - 1 - position.reihe)
        }

    /** Umkehrung von [anzeigePosition] – die Drehung ist selbstinvers. */
    fun modellPosition(anzeige: Position, spalten: Int, reihen: Int, blickrichtung: Blickrichtung): Position =
        anzeigePosition(anzeige, spalten, reihen, blickrichtung)
}
