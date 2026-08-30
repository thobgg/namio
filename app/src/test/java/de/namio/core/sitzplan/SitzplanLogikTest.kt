package de.namio.core.sitzplan

import de.namio.core.model.Blickrichtung
import de.namio.core.model.Sitzplatz
import de.namio.core.model.SitzplanVorlage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class SitzplanLogikTest {
    private val S = 12
    private val R = 9
    private fun p(id: Long, schueler: Long?, x: Float, y: Float, d: Float = 0f) = Sitzplatz(id, 1, schueler, x, y, d)
    private fun List<Sitzplatz>.von(schueler: Long) = first { it.schuelerId == schueler }

    @Test
    fun `ablegen im freien raum erzeugt platz und rastet ein`() {
        val neu = SitzplanLogik.ablegen(emptyList(), 1, 7, 0.26f, 0.31f, S, R, einrasten = true)
        assertEquals(1, neu.size)
        assertEquals(0.25f, neu[0].x, 1e-4f)
        assertEquals(0.3333f, neu[0].y, 1e-3f)
        assertEquals(7L, neu[0].schuelerId)
    }

    @Test
    fun `ablegen ohne einrasten behaelt position`() {
        val neu = SitzplanLogik.ablegen(emptyList(), 1, 7, 0.26f, 0.31f, S, R, einrasten = false)
        assertEquals(0.26f, neu[0].x, 1e-6f)
    }

    @Test
    fun `ablegen auf belegten platz tauscht`() {
        val start = listOf(p(1, 7, 0.2f, 0.2f), p(2, 8, 0.6f, 0.2f))
        val neu = SitzplanLogik.ablegen(start, 1, 7, 0.61f, 0.21f, S, R, true)
        assertEquals(8L, neu.first { it.id == 1L }.schuelerId)
        assertEquals(7L, neu.first { it.id == 2L }.schuelerId)
    }

    @Test
    fun `ablegen auf leeren stuhl belegt ihn und laesst alten stuhl leer`() {
        val start = listOf(p(1, 7, 0.2f, 0.2f), p(2, null, 0.6f, 0.2f))
        val neu = SitzplanLogik.ablegen(start, 1, 7, 0.6f, 0.2f, S, R, true)
        assertNull(neu.first { it.id == 1L }.schuelerId)
        assertEquals(7L, neu.first { it.id == 2L }.schuelerId)
    }

    @Test
    fun `sitzender schueler wandert samt platz in freien raum`() {
        val start = listOf(p(1, 7, 0.2f, 0.2f))
        val neu = SitzplanLogik.ablegen(start, 1, 7, 0.8f, 0.8f, S, R, false)
        assertEquals(1, neu.size)
        assertEquals(0.8f, neu[0].x, 1e-6f)
    }

    @Test
    fun `platzBei findet nur im trefferradius`() {
        val plaetze = listOf(p(1, 7, 0.5f, 0.5f))
        assertEquals(1L, SitzplanLogik.platzBei(plaetze, 0.5f + 0.4f / S, 0.5f, S, R)?.id)
        assertNull(SitzplanLogik.platzBei(plaetze, 0.5f + 1.2f / S, 0.5f, S, R))
    }

    @Test
    fun `drehen bleibt im bereich 0 bis 360`() {
        val neu = SitzplanLogik.drehen(listOf(p(1, null, 0.5f, 0.5f, 350f)), 1, 15f)
        assertEquals(5f, neu[0].drehung, 1e-4f)
        val zurueck = SitzplanLogik.drehen(neu, 1, -15f)
        assertEquals(350f, zurueck[0].drehung, 1e-4f)
    }

    @Test
    fun `partnerplatz liegt rechts in blickrichtung des tisches`() {
        val neu = SitzplanLogik.partnerplatz(listOf(p(1, 7, 0.5f, 0.5f, 0f)), 1, S, R)
        assertEquals(2, neu.size)
        assertEquals(0.5f + 1f / S, neu[1].x, 1e-4f)
        assertEquals(0.5f, neu[1].y, 1e-4f)
        val gedreht = SitzplanLogik.partnerplatz(listOf(p(1, 7, 0.5f, 0.5f, 90f)), 1, S, R)
        assertEquals(0.5f, gedreht[1].x, 1e-4f)
        assertEquals(0.5f + 1f / R, gedreht[1].y, 1e-4f)
    }

    @Test
    fun `partnerplatz nicht doppelt`() {
        val start = listOf(p(1, 7, 0.5f, 0.5f), p(2, null, 0.5f + 1f / S, 0.5f))
        assertEquals(start, SitzplanLogik.partnerplatz(start, 1, S, R))
    }

    @Test
    fun `einrasten haelt den platz im raum`() {
        val (x, y) = SitzplanLogik.einrasten(1.2f, -0.3f, S, R)
        assertEquals(1f - 0.5f / S, x, 1e-4f)
        assertEquals(0.5f / R, y, 1e-4f)
    }

    @Test
    fun `mischen permutiert nur belegte`() {
        val start = listOf(p(1, 7, 0.1f, 0.1f), p(2, 8, 0.3f, 0.1f), p(3, null, 0.5f, 0.1f), p(4, 9, 0.7f, 0.1f))
        val neu = SitzplanLogik.mischen(start, Random(5))
        assertEquals(setOf(7L, 8L, 9L), neu.mapNotNull { it.schuelerId }.toSet())
        assertNull(neu.first { it.id == 3L }.schuelerId)
    }

    @Test
    fun `entfernen und loeschen`() {
        val start = listOf(p(1, 7, 0.1f, 0.1f))
        assertNull(SitzplanLogik.entfernen(start, 7)[0].schuelerId)
        assertTrue(SitzplanLogik.platzLoeschen(start, 1).isEmpty())
    }

    @Test
    fun `anzeige von vorn dreht um 180 grad und ist selbstinvers`() {
        val a = SitzplanLogik.anzeige(p(1, null, 0.2f, 0.3f, 30f), Blickrichtung.VON_VORN)
        assertEquals(0.8f, a.x, 1e-6f)
        assertEquals(0.7f, a.y, 1e-6f)
        assertEquals(210f, a.drehung, 1e-6f)
        val (mx, my) = SitzplanLogik.modellKoordinate(a.x, a.y, Blickrichtung.VON_VORN)
        assertEquals(0.2f, mx, 1e-6f)
        assertEquals(0.3f, my, 1e-6f)
        assertEquals(p(1, null, 0.2f, 0.3f), SitzplanLogik.anzeige(p(1, null, 0.2f, 0.3f), Blickrichtung.VON_HINTEN))
    }

    @Test
    fun `vorlage doppeltischreihen fuer 12x9 hat 4 tische in 4 reihen`() {
        val plaetze = SitzplanLogik.vorlage(SitzplanVorlage.DOPPELTISCH_REIHEN, 1, S, R, (1L..24L).toList())
        assertEquals(32, plaetze.size)
        assertEquals(24, plaetze.count { it.schuelerId != null })
        assertEquals(1L, plaetze[0].schuelerId)
        assertTrue(plaetze.all { it.x in 0f..1f && it.y in 0f..1f })
        // Partner eines Doppeltischs sind genau eine Einheit auseinander
        assertEquals(1f / S, plaetze[1].x - plaetze[0].x, 1e-4f)
    }

    @Test
    fun `vorlagen u-form und gruppen liegen im raum`() {
        for (v in listOf(SitzplanVorlage.U_FORM, SitzplanVorlage.GRUPPENTISCHE)) {
            val plaetze = SitzplanLogik.vorlage(v, 1, S, R, emptyList())
            assertTrue(plaetze.isNotEmpty())
            assertTrue(plaetze.all { it.x in 0f..1f && it.y in 0f..1f && it.schuelerId == null })
        }
        assertTrue(SitzplanLogik.vorlage(SitzplanVorlage.LEER, 1, S, R, listOf(1)).isEmpty())
    }
}

class MoebelTest {
    private fun p(id: Long, schueler: Long?, x: Float, y: Float, b: String? = null) = Sitzplatz(id, 1, schueler, x, y, 0f, b)

    @Test
    fun `beschriften macht moebel und leerer text wieder stuhl`() {
        val m = SitzplanLogik.beschriften(listOf(p(1, null, 0.5f, 0.5f)), 1, " Pult ")
        assertEquals("Pult", m[0].beschriftung)
        assertTrue(m[0].istMoebel)
        assertNull(SitzplanLogik.beschriften(m, 1, "  ")[0].beschriftung)
    }

    @Test
    fun `belegter platz laesst sich nicht beschriften`() {
        val m = SitzplanLogik.beschriften(listOf(p(1, 7, 0.5f, 0.5f)), 1, "Pult")
        assertNull(m[0].beschriftung)
    }

    @Test
    fun `moebel ist kein ablageziel`() {
        val start = listOf(p(1, null, 0.5f, 0.5f, "PC"))
        assertNull(SitzplanLogik.platzBei(start, 0.5f, 0.5f, 12, 9))
        val neu = SitzplanLogik.ablegen(start, 1, 7, 0.5f, 0.5f, 12, 9, einrasten = false)
        assertEquals(2, neu.size)
        assertEquals("PC", neu[0].beschriftung)
    }
}
