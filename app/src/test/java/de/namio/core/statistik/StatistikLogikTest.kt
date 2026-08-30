package de.namio.core.statistik

import de.namio.core.model.Geschlecht
import de.namio.core.model.Lernkarte
import de.namio.core.model.QuizModus
import de.namio.core.model.Schueler
import de.namio.core.model.SessionKurz
import de.namio.core.model.VerwechslungRoh
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class StatistikLogikTest {
    private val t = Instant.parse("2026-08-31T07:00:00Z")
    private fun s(id: Long, v: String) = Schueler(id, 1, v, "X", "", null, "", 0, Geschlecht.JUNGE)
    private fun k(schueler: Long, box: Int, modus: QuizModus = QuizModus.FOTO_ZU_NAME_MC) = Lernkarte(schueler, schueler, modus, box, t, 0, null)

    @Test
    fun `boxverteilung zaehlt karten und kartenlose`() {
        val karten = listOf(k(1, 1), k(2, 3), k(3, 3), k(4, 5), k(5, 2, QuizModus.SITZPLAN))
        val v = StatistikLogik.boxverteilung(karten, listOf(1, 2, 3, 4, 5, 6), QuizModus.FOTO_ZU_NAME_MC)
        assertArrayEquals(intArrayOf(2, 1, 0, 2, 0, 1), v)
    }

    @Test
    fun `verwechslungen werden symmetrisch summiert und sortiert`() {
        val roh = listOf(VerwechslungRoh(1, 2, 3), VerwechslungRoh(2, 1, 2), VerwechslungRoh(1, 3, 4), VerwechslungRoh(3, 3, 9), VerwechslungRoh(1, 99, 5))
        val paare = StatistikLogik.verwechslungsPaare(roh, mapOf(1L to s(1, "Anna"), 2L to s(2, "Ben"), 3L to s(3, "Cem")))
        assertEquals(listOf(4, 5), paare.map { it.anzahl }.sorted())
        assertEquals(5, paare[0].anzahl)
        assertEquals(setOf(1L, 2L), setOf(paare[0].a.id, paare[0].b.id))
    }

    @Test
    fun `verlauf filtert modus und leere runden und begrenzt`() {
        val sessions = (1..25).map { SessionKurz(it.toLong(), if (it % 2 == 0) QuizModus.SPEEDRUN else QuizModus.FOTO_ZU_NAME_MC, t.plusSeconds(it * 60L), it, 1) } +
            SessionKurz(99, QuizModus.FOTO_ZU_NAME_MC, t, 0, 0)
        val mc = StatistikLogik.verlauf(sessions, QuizModus.FOTO_ZU_NAME_MC, limit = 5)
        assertEquals(5, mc.size)
        assertEquals(listOf(17L, 19L, 21L, 23L, 25L), mc.map { it.id })
        assertEquals(20, StatistikLogik.verlauf(sessions, null).size)
        assertEquals(96, SessionKurz(1, QuizModus.FOTO_ZU_NAME_MC, t, 24, 1).prozent)
    }
}
