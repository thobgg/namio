package de.namio.core.sitzplan

import de.namio.core.model.Blickrichtung
import de.namio.core.model.Position
import de.namio.core.model.Sitzplatz
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class SitzplanLogikTest {
    private fun p(id: Long, schueler: Long?, spalte: Int, reihe: Int) = Sitzplatz(id, 1, schueler, spalte, reihe)
    private fun List<Sitzplatz>.bei(spalte: Int, reihe: Int) = firstOrNull { it.spalte == spalte && it.reihe == reihe }

    @Test
    fun `neuer schueler auf freies feld erzeugt platz`() {
        val neu = SitzplanLogik.setzen(emptyList(), 1, schuelerId = 7, spalte = 2, reihe = 1)
        assertEquals(listOf(Sitzplatz(0, 1, 7, 2, 1)), neu)
    }

    @Test
    fun `schueler wandert und laesst leeren stuhl zurueck`() {
        val start = listOf(p(1, 7, 0, 0))
        val neu = SitzplanLogik.setzen(start, 1, 7, 3, 3)
        assertNull(neu.bei(0, 0)?.schuelerId)
        assertEquals(7L, neu.bei(3, 3)?.schuelerId)
        assertEquals(2, neu.size)
    }

    @Test
    fun `belegtes ziel tauscht die beiden`() {
        val start = listOf(p(1, 7, 0, 0), p(2, 8, 1, 0))
        val neu = SitzplanLogik.setzen(start, 1, 7, 1, 0)
        assertEquals(8L, neu.bei(0, 0)?.schuelerId)
        assertEquals(7L, neu.bei(1, 0)?.schuelerId)
        assertEquals(2, neu.size)
        assertEquals(setOf(1L, 2L), neu.map { it.id }.toSet())
    }

    @Test
    fun `leeren stuhl belegen behaelt platz id`() {
        val start = listOf(p(5, null, 2, 2))
        val neu = SitzplanLogik.setzen(start, 1, 9, 2, 2)
        assertEquals(listOf(Sitzplatz(5, 1, 9, 2, 2)), neu)
    }

    @Test
    fun `auf eigenen platz setzen aendert nichts`() {
        val start = listOf(p(1, 7, 0, 0))
        assertEquals(start, SitzplanLogik.setzen(start, 1, 7, 0, 0))
    }

    @Test
    fun `entfernen laesst stuhl stehen`() {
        val neu = SitzplanLogik.entfernen(listOf(p(1, 7, 0, 0), p(2, 8, 1, 0)), 7)
        assertNull(neu.bei(0, 0)?.schuelerId)
        assertEquals(8L, neu.bei(1, 0)?.schuelerId)
    }

    @Test
    fun `mischen permutiert nur belegte plaetze`() {
        val start = listOf(p(1, 7, 0, 0), p(2, 8, 1, 0), p(3, null, 2, 0), p(4, 9, 3, 0))
        val neu = SitzplanLogik.mischen(start, Random(3))
        assertEquals(setOf(7L, 8L, 9L), neu.mapNotNull { it.schuelerId }.toSet())
        assertNull(neu.bei(2, 0)?.schuelerId)
        assertEquals(start.map { it.id }, neu.map { it.id })
    }

    @Test
    fun `ausserhalb findet plaetze jenseits des rasters`() {
        val plaetze = listOf(p(1, 7, 0, 0), p(2, 8, 5, 0), p(3, 9, 0, 4))
        assertEquals(listOf(2L, 3L), SitzplanLogik.ausserhalb(plaetze, spalten = 5, reihen = 4).map { it.id })
    }

    @Test
    fun `von hinten ist identitaet von vorn ist drehung`() {
        val pos = Position(0, 0)
        assertEquals(pos, SitzplanLogik.anzeigePosition(pos, 6, 4, Blickrichtung.VON_HINTEN))
        assertEquals(Position(5, 3), SitzplanLogik.anzeigePosition(pos, 6, 4, Blickrichtung.VON_VORN))
        val zurueck = SitzplanLogik.modellPosition(Position(5, 3), 6, 4, Blickrichtung.VON_VORN)
        assertEquals(pos, zurueck)
    }

    @Test
    fun `drehung ist selbstinvers`() {
        for (s in 0 until 7) for (r in 0 until 5) {
            val a = SitzplanLogik.anzeigePosition(Position(s, r), 7, 5, Blickrichtung.VON_VORN)
            assertTrue(SitzplanLogik.anzeigePosition(a, 7, 5, Blickrichtung.VON_VORN) == Position(s, r))
        }
    }
}
