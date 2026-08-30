package de.namio.core.lernen

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SchuljahresendeTest {
    @Test
    fun `zeitfenster mitte juni bis ende august`() {
        assertFalse(Schuljahresende.istZeitfenster(LocalDate.of(2026, 6, 14)))
        assertTrue(Schuljahresende.istZeitfenster(LocalDate.of(2026, 6, 15)))
        assertTrue(Schuljahresende.istZeitfenster(LocalDate.of(2026, 7, 20)))
        assertTrue(Schuljahresende.istZeitfenster(LocalDate.of(2026, 8, 31)))
        assertFalse(Schuljahresende.istZeitfenster(LocalDate.of(2026, 9, 1)))
    }

    @Test
    fun `einmal pro jahr und nur mit klassen`() {
        val d = LocalDate.of(2026, 8, 30)
        assertTrue(Schuljahresende.erinnern(d, quittiertJahr = 0, klassenAnzahl = 2))
        assertTrue(Schuljahresende.erinnern(d, quittiertJahr = 2025, klassenAnzahl = 2))
        assertFalse(Schuljahresende.erinnern(d, quittiertJahr = 2026, klassenAnzahl = 2))
        assertFalse(Schuljahresende.erinnern(d, quittiertJahr = 0, klassenAnzahl = 0))
        assertFalse(Schuljahresende.erinnern(LocalDate.of(2026, 3, 1), 0, 2))
    }
}
