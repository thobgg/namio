package de.namio.feature.sitzplan

import de.namio.core.model.Blickrichtung
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GangTest {
    private fun gaenge(spalten: Int, richtung: Blickrichtung, doppel: Boolean = true) =
        (0 until spalten - 1).filter { gangNach(it, spalten, doppel, richtung) }

    @Test
    fun `sechs spalten ergeben zwei gaenge in beiden richtungen`() {
        assertEquals(listOf(1, 3), gaenge(6, Blickrichtung.VON_HINTEN))
        assertEquals(listOf(1, 3), gaenge(6, Blickrichtung.VON_VORN))
    }

    @Test
    fun `ungerade spaltenzahl spiegelt die gaenge`() {
        assertEquals(listOf(1, 3), gaenge(5, Blickrichtung.VON_HINTEN)) // 2-2-1
        assertEquals(listOf(0, 2), gaenge(5, Blickrichtung.VON_VORN)) // 1-2-2
    }

    @Test
    fun `ohne doppeltische keine gaenge`() {
        assertEquals(emptyList<Int>(), gaenge(6, Blickrichtung.VON_VORN, doppel = false))
    }
}
