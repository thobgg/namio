package de.namio.core.sitzplan

import de.namio.core.model.Bestuhlung
import de.namio.core.model.Blickrichtung
import de.namio.core.model.Sitzplatz
import de.namio.core.model.SitzplanVorlage
import de.namio.core.model.Tisch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class SitzplanLogikTest {
    private val S = 12
    private val R = 9
    private fun doppeltisch(id: Long, x: Float, y: Float, d: Float = 0f, a: Long? = null, b: Long? = null) = Bestuhlung(
        listOf(Tisch(id, 1, x, y, d, 2)),
        listOf(Sitzplatz(id * 10, 1, id, 0, a), Sitzplatz(id * 10 + 1, 1, id, 1, b)),
    )
    private operator fun Bestuhlung.plus(o: Bestuhlung) = Bestuhlung(tische + o.tische, plaetze + o.plaetze)
    private fun Bestuhlung.von(schueler: Long) = plaetze.first { it.schuelerId == schueler }

    @Test
    fun `slotpositionen eines doppeltischs liegen eine einheit auseinander`() {
        val t = Tisch(1, 1, 0.5f, 0.5f, 0f, 2)
        val (x0, _) = SitzplanLogik.slotPosition(t, 0, S, R)
        val (x1, y1) = SitzplanLogik.slotPosition(t, 1, S, R)
        assertEquals(1f / S, x1 - x0, 1e-5f)
        assertEquals(0.5f, y1, 1e-5f)
        val gedreht = Tisch(1, 1, 0.5f, 0.5f, 90f, 2)
        val (gx, gy) = SitzplanLogik.slotPosition(gedreht, 1, S, R)
        assertEquals(0.5f, gx, 1e-5f)
        assertEquals(0.5f + 0.5f / R, gy, 1e-5f)
    }

    @Test
    fun `ablegen im freien raum aendert nichts`() {
        val b = SitzplanLogik.ablegen(Bestuhlung(), 1, 7, 0.26f, 0.31f, S, R, einrasten = true)
        assertTrue(b.tische.isEmpty())
        assertTrue(b.plaetze.isEmpty())
    }

    @Test
    fun `ablegen auf belegten slot tauscht`() {
        val start = doppeltisch(1, 0.5f, 0.5f, a = 7, b = 8)
        val (x1, y1) = SitzplanLogik.slotPosition(start.tische[0], 1, S, R)
        val neu = SitzplanLogik.ablegen(start, 1, 7, x1, y1, S, R, true)
        assertEquals(8L, neu.plaetze.first { it.slot == 0 }.schuelerId)
        assertEquals(7L, neu.plaetze.first { it.slot == 1 }.schuelerId)
        assertEquals(1, neu.tische.size)
    }

    @Test
    fun `sitzender schueler bleibt beim ablegen ins freie sitzen`() {
        val start = doppeltisch(1, 0.3f, 0.3f, a = 7)
        val neu = SitzplanLogik.ablegen(start, 1, 7, 0.8f, 0.8f, S, R, false)
        assertEquals(start, neu)
    }

    @Test
    fun `slotBei nur im trefferradius`() {
        val b = doppeltisch(1, 0.5f, 0.5f)
        assertEquals(0, SitzplanLogik.slotBei(b, 0.5f - 0.5f / S, 0.5f, S, R)?.slot)
        assertNull(SitzplanLogik.slotBei(b, 0.5f + 2f / S, 0.5f, S, R))
    }

    @Test
    fun `plaetze aendern fuegt slots hinzu und entfernt sie`() {
        val b = doppeltisch(1, 0.5f, 0.5f, a = 7, b = 8)
        val drei = SitzplanLogik.plaetzeAendern(b, 1, 3)
        assertEquals(3, drei.plaetzeVon(1).size)
        assertEquals(3, drei.tische[0].plaetze)
        val eins = SitzplanLogik.plaetzeAendern(drei, 1, 1)
        assertEquals(listOf(7L), eins.plaetzeVon(1).map { it.schuelerId })
        assertEquals(4, SitzplanLogik.plaetzeAendern(b, 1, 9).tische[0].plaetze)
    }

    @Test
    fun `beschriften macht moebel ohne slots und zurueck`() {
        val b = doppeltisch(1, 0.5f, 0.5f)
        val m = SitzplanLogik.beschriften(b, 1, " Pult ")
        assertTrue(m.tische[0].istMoebel)
        assertEquals("Pult", m.tische[0].beschriftung)
        assertTrue(m.plaetzeVon(1).isEmpty())
        val zurueck = SitzplanLogik.beschriften(m, 1, "")
        assertEquals(1, zurueck.tische[0].plaetze)
        assertEquals(1, zurueck.plaetzeVon(1).size)
        // belegt: nicht beschriftbar
        assertEquals(doppeltisch(1, 0.5f, 0.5f, a = 7), SitzplanLogik.beschriften(doppeltisch(1, 0.5f, 0.5f, a = 7), 1, "PC"))
    }

    @Test
    fun `drehen bleibt im bereich`() {
        val b = SitzplanLogik.drehen(doppeltisch(1, 0.5f, 0.5f, 350f), 1, 15f)
        assertEquals(5f, b.tische[0].drehung, 1e-4f)
    }

    @Test
    fun `mischen permutiert nur belegte slots`() {
        val start = doppeltisch(1, 0.2f, 0.2f, a = 7, b = 8) + doppeltisch(2, 0.6f, 0.2f, a = 9)
        val neu = SitzplanLogik.mischen(start, Random(3))
        assertEquals(setOf(7L, 8L, 9L), neu.plaetze.mapNotNull { it.schuelerId }.toSet())
        assertNull(neu.plaetze.first { it.tischId == 2L && it.slot == 1 }.schuelerId)
    }

    @Test
    fun `entfernen und tisch loeschen`() {
        val b = doppeltisch(1, 0.2f, 0.2f, a = 7)
        assertTrue(SitzplanLogik.entfernen(b, 7).plaetze.all { it.schuelerId == null })
        val weg = SitzplanLogik.tischLoeschen(b, 1)
        assertTrue(weg.tische.isEmpty() && weg.plaetze.isEmpty())
    }

    @Test
    fun `anzeige von vorn dreht um 180 grad`() {
        val a = SitzplanLogik.anzeige(Tisch(1, 1, 0.2f, 0.3f, 30f, 2), Blickrichtung.VON_VORN)
        assertEquals(0.8f, a.x, 1e-6f)
        assertEquals(0.7f, a.y, 1e-6f)
        assertEquals(210f, a.drehung, 1e-6f)
        val (mx, my) = SitzplanLogik.modellKoordinate(0.8f, 0.7f, Blickrichtung.VON_VORN)
        assertEquals(0.2f, mx, 1e-6f)
        assertEquals(0.3f, my, 1e-6f)
    }

    @Test
    fun `einrasten haelt punkt im raum`() {
        val (x, y) = SitzplanLogik.einrasten(1.2f, -0.3f, S, R)
        assertEquals(1f - 0.5f / S, x, 1e-4f)
        assertEquals(0.5f / R, y, 1e-4f)
    }

    @Test
    fun `vorlage doppeltischreihen 12x9 liefert 16 doppeltische mit 24 belegten slots`() {
        val b = SitzplanLogik.vorlage(SitzplanVorlage.DOPPELTISCH_REIHEN, 1, S, R, (1L..24L).toList())
        assertEquals(16, b.tische.size)
        assertTrue(b.tische.all { it.plaetze == 2 })
        assertEquals(32, b.plaetze.size)
        assertEquals(24, b.plaetze.count { it.schuelerId != null })
        assertEquals(1L, b.plaetze[0].schuelerId)
        assertEquals(b.tische.map { it.id }.toSet().size, b.tische.size)
    }

    @Test
    fun `alle vorlagen liegen im raum`() {
        for (v in listOf(SitzplanVorlage.U_FORM, SitzplanVorlage.GRUPPENTISCHE)) {
            val b = SitzplanLogik.vorlage(v, 1, S, R, emptyList())
            assertTrue(b.tische.isNotEmpty())
            assertTrue(b.tische.all { it.x in 0f..1f && it.y in 0f..1f })
            assertTrue(b.plaetze.all { p -> b.tische.any { it.id == p.tischId } })
        }
        assertTrue(SitzplanLogik.vorlage(SitzplanVorlage.LEER, 1, S, R, listOf(1)).tische.isEmpty())
    }
}

class TischTrefferTest {
    private val S = 12
    private val R = 9
    private fun tisch(id: Long, x: Float, y: Float, d: Float = 0f, plaetze: Int = 1, breite: Float = 3f) = Tisch(id, 1, x, y, d, plaetze, null, breite)

    @Test
    fun `drop irgendwo auf breitem tisch landet im freien slot`() {
        val t = tisch(1, 0.5f, 0.5f)
        val b = Bestuhlung(listOf(t), listOf(Sitzplatz(10, 1, 1, 0, null)))
        // 1,2 Einheiten rechts der Mitte: außerhalb des Slot-Radius, aber noch auf dem 3 breiten Tisch
        val neu = SitzplanLogik.ablegen(b, 1, 7, 0.5f + 1.2f / S, 0.5f, S, R, einrasten = false)
        assertEquals(1, neu.tische.size)
        assertEquals(7L, neu.plaetze.single().schuelerId)
    }

    @Test
    fun `drop neben dem tisch aendert nichts`() {
        val b = Bestuhlung(listOf(tisch(1, 0.5f, 0.5f)), listOf(Sitzplatz(10, 1, 1, 0, null)))
        val neu = SitzplanLogik.ablegen(b, 1, 7, 0.5f + 2.5f / S, 0.5f, S, R, einrasten = false)
        assertEquals(b, neu)
    }

    @Test
    fun `gedrehter tisch wird korrekt getroffen`() {
        val t = tisch(1, 0.5f, 0.5f, d = 90f)
        val b = Bestuhlung(listOf(t), listOf(Sitzplatz(10, 1, 1, 0, null)))
        assertEquals(t, SitzplanLogik.tischBei(b, 0.5f, 0.5f + 1.2f / R, S, R))
        assertNull(SitzplanLogik.tischBei(b, 0.5f + 1.2f / S, 0.5f, S, R))
    }

    @Test
    fun `duplizieren kopiert form und setzt daneben`() {
        val t = tisch(1, 0.3f, 0.5f, d = 0f, plaetze = 1, breite = 3f)
        val b = Bestuhlung(listOf(t), listOf(Sitzplatz(10, 1, 1, 0, 7)))
        val neu = SitzplanLogik.duplizieren(b, 1, S, R)
        assertEquals(2, neu.tische.size)
        val k = neu.tische.last()
        assertEquals(3f, k.breite)
        assertEquals(1, k.plaetze)
        assertEquals(0.3f + 3f / S, k.x, 1e-4f)
        assertNull(neu.plaetzeVon(k.id).single().schuelerId)
    }
}

class RasterEinzelTest {
    @Test
    fun `je kind ein tisch alle belegt im raum`() {
        val ids = (1L..24L).toList()
        val b = SitzplanLogik.vorlage(SitzplanVorlage.RASTER_EINZEL, 1, 14, 20, ids)
        assertEquals(24, b.tische.size)
        assertTrue(b.tische.all { it.plaetze == 1 && it.x in 0f..1f && it.y in 0f..1f })
        assertEquals(ids, b.plaetze.map { it.schuelerId })
    }
}


class MehrfachTest {
    private val S = 12; private val R = 9
    private fun t(id: Long, x: Float, y: Float, d: Float = 0f) = Tisch(id, 1, x, y, d, 1, null, 1f)
    private fun b(vararg ts: Tisch) = Bestuhlung(ts.toList(), ts.map { Sitzplatz(it.id * 10, 1, it.id, 0, it.id) })

    @Test
    fun `verschieben und drehen mehrerer`() {
        val start = b(t(1, 0.2f, 0.2f), t(2, 0.4f, 0.2f), t(3, 0.8f, 0.8f))
        val v = SitzplanLogik.verschiebeMehrere(start, setOf(1, 2), 0.1f, 0.05f, S, R, einrasten = false)
        assertEquals(0.3f, v.tisch(1)!!.x, 1e-5f); assertEquals(0.5f, v.tisch(2)!!.x, 1e-5f); assertEquals(0.8f, v.tisch(3)!!.x, 1e-5f)
        val d = SitzplanLogik.drehenMehrere(start, setOf(1, 3), 45f)
        assertEquals(45f, d.tisch(1)!!.drehung); assertEquals(0f, d.tisch(2)!!.drehung); assertEquals(45f, d.tisch(3)!!.drehung)
    }

    @Test
    fun `ausrichten an ankertisch`() {
        val start = b(t(1, 0.2f, 0.3f, 30f), t(2, 0.5f, 0.35f, 0f), t(3, 0.9f, 0.25f, 90f))
        val zeile = SitzplanLogik.ausrichten(start, listOf(1, 2, 3), SitzplanLogik.Ausrichtung.GLEICHE_ZEILE)
        assertTrue(zeile.tische.all { it.y == 0.3f })
        val dreh = SitzplanLogik.ausrichten(start, listOf(2, 1, 3), SitzplanLogik.Ausrichtung.GLEICHE_DREHUNG)
        assertTrue(dreh.tische.all { it.drehung == 0f })
        val abstand = SitzplanLogik.ausrichten(start, listOf(1, 2, 3), SitzplanLogik.Ausrichtung.GLEICHER_ABSTAND)
        assertEquals(0.55f, abstand.tisch(2)!!.x, 1e-5f)
    }

    @Test
    fun `kopie mit und ohne schueler`() {
        val start = b(t(1, 0.2f, 0.3f, 30f), t(2, 0.5f, 0.35f))
        val mit = SitzplanLogik.kopiereFuer(start, 99, mitSchuelern = true)
        assertEquals(2, mit.tische.size); assertTrue(mit.tische.all { it.sitzplanId == 99L && it.id < 0 })
        assertEquals(listOf(1L, 2L), mit.plaetze.map { it.schuelerId })
        val ohne = SitzplanLogik.kopiereFuer(start, 99, mitSchuelern = false)
        assertTrue(ohne.plaetze.all { it.schuelerId == null })
    }
}
