package de.namio.core.lernen

import de.namio.core.model.Geschlecht
import de.namio.core.model.Schueler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class AblenkerWaehlerTest {
    private fun s(id: Long, vorname: String, geschlecht: Geschlecht = Geschlecht.MAEDCHEN) =
        Schueler(id, 1, vorname, "Test", "", null, "", id.toInt(), geschlecht)

    private val anna = s(1, "Anna")
    private val klasse = listOf(
        anna, s(2, "Ben"), s(3, "Ayse"), s(4, "Émile"), s(5, "Clara"), s(6, "Ali"), s(7, "David"),
    )
    private val waehler = AblenkerWaehler(Random(1))

    @Test
    fun `verwechslungen zuerst in gegebener reihenfolge`() {
        val ablenker = waehler.waehle(anna, klasse, verwechslungen = listOf(5, 2), anzahl = 3)
        assertEquals(listOf(5L, 2L), ablenker.take(2).map { it.id })
        assertEquals(3, ablenker.size)
    }

    @Test
    fun `dann gleicher anfangsbuchstabe ohne diakritika`() {
        val ablenker = waehler.waehle(anna, klasse, verwechslungen = emptyList(), anzahl = 2)
        assertEquals(setOf(3L, 6L), ablenker.map { it.id }.toSet())
    }

    @Test
    fun `e mit akzent zaehlt wie e`() {
        val emil = s(8, "Emil")
        val ablenker = waehler.waehle(emil, klasse + emil, emptyList(), anzahl = 1)
        assertEquals(4L, ablenker.single().id)
    }

    @Test
    fun `zufaellig aufgefuellt ohne ziel und ohne duplikate`() {
        val ablenker = waehler.waehle(anna, klasse, emptyList(), anzahl = 6)
        assertEquals(6, ablenker.size)
        assertFalse(ablenker.any { it.id == anna.id })
        assertEquals(6, ablenker.map { it.id }.toSet().size)
    }

    @Test
    fun `zu wenig kandidaten liefert weniger ablenker`() {
        val ablenker = waehler.waehle(anna, listOf(anna, s(2, "Ben")), emptyList(), anzahl = 3)
        assertEquals(listOf(2L), ablenker.map { it.id })
    }

    @Test
    fun `verwechslungen aus anderer klasse werden ignoriert`() {
        val ablenker = waehler.waehle(anna, klasse, verwechslungen = listOf(999), anzahl = 1)
        assertTrue(ablenker.single().id != 999L)
    }

    @Test
    fun `maedchen bekommt nur maedchen als ablenker`() {
        val lena = s(10, "Lena", Geschlecht.MAEDCHEN)
        val klasse = listOf(
            lena,
            s(11, "Ben", Geschlecht.JUNGE), s(12, "Tom", Geschlecht.JUNGE), s(13, "Max", Geschlecht.JUNGE),
            s(14, "Sophie", Geschlecht.MAEDCHEN), s(15, "Marita", Geschlecht.MAEDCHEN), s(16, "Kim", Geschlecht.MAEDCHEN),
        )
        val ablenker = waehler.waehle(lena, klasse, emptyList(), anzahl = 3)
        assertEquals(setOf(14L, 15L, 16L), ablenker.map { it.id }.toSet())
    }

    @Test
    fun `zu wenig gleichgeschlechtliche werden mit jungen aufgefuellt`() {
        val lena = s(10, "Lena", Geschlecht.MAEDCHEN)
        val klasse = listOf(lena, s(11, "Ben", Geschlecht.JUNGE), s(12, "Tom", Geschlecht.JUNGE), s(14, "Sophie", Geschlecht.MAEDCHEN))
        val ablenker = waehler.waehle(lena, klasse, emptyList(), anzahl = 3)
        assertEquals(14L, ablenker.first().id)
        assertEquals(3, ablenker.size)
    }

    @Test
    fun `verwechslung mit junge zaehlt bei maedchen erst in der auffuellrunde`() {
        val lena = s(10, "Lena", Geschlecht.MAEDCHEN)
        val klasse = listOf(lena, s(11, "Ben", Geschlecht.JUNGE), s(14, "Sophie", Geschlecht.MAEDCHEN), s(15, "Marita", Geschlecht.MAEDCHEN))
        val ablenker = waehler.waehle(lena, klasse, verwechslungen = listOf(11), anzahl = 2)
        assertEquals(setOf(14L, 15L), ablenker.map { it.id }.toSet())
    }

    @Test
    fun `null oder leer`() {
        assertTrue(waehler.waehle(anna, klasse, emptyList(), anzahl = 0).isEmpty())
        assertTrue(waehler.waehle(anna, listOf(anna), emptyList(), anzahl = 3).isEmpty())
    }
}
