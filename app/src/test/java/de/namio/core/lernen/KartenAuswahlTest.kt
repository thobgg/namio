package de.namio.core.lernen

import de.namio.core.model.Lernkarte
import de.namio.core.model.QuizModus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class KartenAuswahlTest {
    private val jetzt: Instant = Instant.parse("2026-08-31T07:00:00Z")

    private fun karte(schueler: Long, faelligIn: Duration, box: Int = 2) =
        Lernkarte(schueler, schueler, QuizModus.FOTO_ZU_NAME_MC, box, jetzt.plus(faelligIn), 0, null)

    @Test
    fun `faellige zuerst nach faelligkeit dann neue`() {
        val karten = listOf(
            karte(1, Duration.ofMinutes(-5)),
            karte(2, Duration.ofDays(-1)),
            karte(3, Duration.ofMinutes(5)), // noch nicht fällig
        )
        val reihenfolge = KartenAuswahl.reihenfolge(listOf(1, 2, 3, 4, 5), karten, jetzt)
        assertEquals(listOf(2L, 1L, 4L, 5L), reihenfolge)
    }

    @Test
    fun `genau jetzt faellig zaehlt als faellig`() {
        val reihenfolge = KartenAuswahl.reihenfolge(listOf(1), listOf(karte(1, Duration.ZERO)), jetzt)
        assertEquals(listOf(1L), reihenfolge)
    }

    @Test
    fun `hoechstens maxNeue neue karten`() {
        val reihenfolge = KartenAuswahl.reihenfolge((1L..10L).toList(), emptyList(), jetzt, maxNeue = 5)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), reihenfolge)
    }

    @Test
    fun `karten fremder schueler werden ignoriert`() {
        val reihenfolge = KartenAuswahl.reihenfolge(listOf(1), listOf(karte(99, Duration.ofDays(-1))), jetzt)
        assertEquals(listOf(1L), reihenfolge)
    }

    @Test
    fun `anzahl faellig zaehlt alle neuen mit`() {
        val karten = listOf(karte(1, Duration.ofDays(-1)), karte(2, Duration.ofDays(1)))
        assertEquals(9, KartenAuswahl.anzahlFaellig((1L..10L).toList(), karten, jetzt))
    }
}
