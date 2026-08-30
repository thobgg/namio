package de.namio.core.repository

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FortschrittTest {

    @Test
    fun `keine schueler ergibt null prozent`() {
        assertEquals(0, Fortschritt.prozent(boxSumme = 0, schuelerAnzahl = 0))
        assertEquals(0, Fortschritt.prozent(boxSumme = 7, schuelerAnzahl = 0))
    }

    @Test
    fun `ohne karten null prozent`() {
        assertEquals(0, Fortschritt.prozent(boxSumme = 0, schuelerAnzahl = 25))
    }

    @Test
    fun `alle in box 5 sind hundert prozent`() {
        assertEquals(100, Fortschritt.prozent(boxSumme = 4 * 5, schuelerAnzahl = 4))
    }

    @Test
    fun `mischung wird anteilig gerechnet`() {
        // 2 Schüler: einer Box 5, einer Box 0 → 50 %
        assertEquals(50, Fortschritt.prozent(boxSumme = 5, schuelerAnzahl = 2))
        // 3 Schüler mit Boxen 1,2,3 → 6 von 15 = 40 %
        assertEquals(40, Fortschritt.prozent(boxSumme = 6, schuelerAnzahl = 3))
    }

    @Test
    fun `summe ueber maximum wird gedeckelt`() {
        assertEquals(100, Fortschritt.prozent(boxSumme = 99, schuelerAnzahl = 2))
    }
}
