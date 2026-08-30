package de.namio.core.lernen

import de.namio.core.model.Geschlecht
import de.namio.core.model.Schueler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NamensVergleichTest {
    private fun s(id: Long, vorname: String, nachname: String = "Test", spitz: String = "") =
        Schueler(id, 1, vorname, nachname, spitz, null, "", id.toInt(), Geschlecht.MAEDCHEN)

    @Test
    fun `normalisierung ignoriert gross klein und diakritika`() {
        assertEquals("zoe", NamensVergleich.normalisiere("  Zoë "))
        assertEquals("jose maria", NamensVergleich.normalisiere("José   María"))
        assertEquals("gross", NamensVergleich.normalisiere("Groß"))
    }

    @Test
    fun `levenshtein grundfaelle`() {
        assertEquals(0, NamensVergleich.levenshtein("lena", "lena"))
        assertEquals(1, NamensVergleich.levenshtein("lena", "lina"))
        assertEquals(2, NamensVergleich.levenshtein("marieke", "mareike"))
        assertEquals(4, NamensVergleich.levenshtein("", "lena"))
    }

    @Test
    fun `toleranz haengt von der laenge ab`() {
        assertTrue(NamensVergleich.passt("Lina", "Lena"))      // 4 Zeichen: 1 erlaubt
        assertFalse(NamensVergleich.passt("Lisa", "Lena"))     // Distanz 2 bei kurzem Namen
        assertTrue(NamensVergleich.passt("Mareike", "Marieke")) // 7 Zeichen: 2 erlaubt
        assertFalse(NamensVergleich.passt("Marike", "Marieke L"))
        assertFalse(NamensVergleich.passt("", "Lena"))
    }

    @Test
    fun `nur vorname zaehlt ausser bei doppeltem vornamen`() {
        val lena1 = s(1, "Lena", "Meier"); val lena2 = s(2, "Lena", "Schulz"); val ben = s(3, "Ben", "Roth")
        val klasse = listOf(lena1, lena2, ben)
        assertTrue(NamensVergleich.istRichtig("ben", ben, klasse))
        assertFalse(NamensVergleich.istRichtig("Lena", lena1, klasse))
        assertTrue(NamensVergleich.istRichtig("Lena Meier", lena1, klasse))
        assertTrue(NamensVergleich.istRichtig("lena meyer", lena1, klasse)) // Tippfehler im Nachnamen
    }

    @Test
    fun `spitzname und voller name gelten auch`() {
        val ben = s(3, "Benjamin", "Roth", spitz = "Benny")
        assertTrue(NamensVergleich.istRichtig("Benny", ben, listOf(ben)))
        assertTrue(NamensVergleich.istRichtig("Benjamin Roth", ben, listOf(ben)))
    }

    @Test
    fun `verwechslung wird dem passenden mitschueler zugeordnet`() {
        val lena = s(1, "Lena"); val lina = s(2, "Linda"); val ben = s(3, "Ben")
        val klasse = listOf(lena, lina, ben)
        assertEquals(ben, NamensVergleich.verwechseltMit("Ben", lena, klasse))
        assertNull(NamensVergleich.verwechseltMit("Xaver", lena, klasse))
    }
}
