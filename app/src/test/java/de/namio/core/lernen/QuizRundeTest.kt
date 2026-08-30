package de.namio.core.lernen

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QuizRundeTest {

    @Test
    fun `richtige antworten arbeiten die liste ab`() {
        val runde = QuizRunde(listOf(1, 2, 3))
        assertEquals(1L, runde.aktuell)
        runde.antworte(true)
        assertEquals(2L, runde.aktuell)
        assertEquals(1f / 3f, runde.fortschritt, 0.001f)
        runde.antworte(true)
        runde.antworte(true)
        assertTrue(runde.istZuEnde)
        assertNull(runde.aktuell)
        assertEquals(1f, runde.fortschritt)
    }

    @Test
    fun `falsche antwort reiht mit abstand wieder ein`() {
        val runde = QuizRunde(listOf(1, 2, 3, 4))
        runde.antworte(false)
        assertEquals(2L, runde.aktuell)
        runde.antworte(true)
        assertEquals(3L, runde.aktuell)
        runde.antworte(true)
        assertEquals(1L, runde.aktuell) // nach zwei anderen wieder dran
        assertEquals(2, runde.fertig)
    }

    @Test
    fun `letzter schueler wiederholt sich notfalls direkt`() {
        val runde = QuizRunde(listOf(1))
        runde.antworte(false)
        assertEquals(1L, runde.aktuell)
        assertEquals(0f, runde.fortschritt)
        runde.antworte(true)
        assertTrue(runde.istZuEnde)
    }

    @Test
    fun `duplikate in der startliste werden entfernt`() {
        val runde = QuizRunde(listOf(1, 1, 2))
        assertEquals(2, runde.anzahl)
    }

    @Test
    fun `leere runde ist sofort zu ende`() {
        val runde = QuizRunde(emptyList())
        assertTrue(runde.istZuEnde)
        assertEquals(1f, runde.fortschritt)
        runde.antworte(true) // darf nicht werfen
    }
}
