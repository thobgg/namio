package de.namio.core.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class BildzuschnittTest {

    @Test
    fun `querformat wird mittig auf hoehe beschnitten`() {
        assertEquals(Ausschnitt(x = 200, y = 0, groesse = 600), Bildzuschnitt.mittigesQuadrat(1000, 600))
    }

    @Test
    fun `hochformat wird mittig auf breite beschnitten`() {
        assertEquals(Ausschnitt(x = 0, y = 500, groesse = 800), Bildzuschnitt.mittigesQuadrat(800, 1800))
    }

    @Test
    fun `quadrat bleibt unveraendert`() {
        assertEquals(Ausschnitt(0, 0, 640), Bildzuschnitt.mittigesQuadrat(640, 640))
    }

    @Test
    fun `ungerader rest wird abgerundet`() {
        assertEquals(Ausschnitt(x = 1, y = 0, groesse = 10), Bildzuschnitt.mittigesQuadrat(13, 10))
    }

    @Test
    fun `ungueltige masse werfen`() {
        assertThrows(IllegalArgumentException::class.java) { Bildzuschnitt.mittigesQuadrat(0, 10) }
        assertThrows(IllegalArgumentException::class.java) { Bildzuschnitt.mittigesQuadrat(10, -1) }
    }

    @Test
    fun `zielkante verkleinert aber vergroessert nie`() {
        assertEquals(800, Bildzuschnitt.zielKante(3000, 800))
        assertEquals(300, Bildzuschnitt.zielKante(300, 800))
        assertEquals(800, Bildzuschnitt.zielKante(800, 800))
    }

    @Test
    fun `samplesize haelt kurze kante ueber dem minimum`() {
        assertEquals(1, Bildzuschnitt.sampleSize(1000, 800, 800))
        assertEquals(2, Bildzuschnitt.sampleSize(4000, 1600, 800))
        assertEquals(4, Bildzuschnitt.sampleSize(4000, 3200, 800))
        assertEquals(1, Bildzuschnitt.sampleSize(400, 300, 800))
    }
}
