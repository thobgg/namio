package de.namio.core.lernen

import de.namio.core.model.Lernkarte
import de.namio.core.model.QuizModus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class LeitnerTest {
    private val jetzt: Instant = Instant.parse("2026-08-31T07:00:00Z")

    @Test
    fun `intervalle entsprechen der tabelle`() {
        assertEquals(Duration.ZERO, Leitner.intervall(1))
        assertEquals(Duration.ofMinutes(10), Leitner.intervall(2))
        assertEquals(Duration.ofDays(1), Leitner.intervall(3))
        assertEquals(Duration.ofDays(3), Leitner.intervall(4))
        assertEquals(Duration.ofDays(7), Leitner.intervall(5))
        assertEquals(Duration.ofDays(7), Leitner.intervall(9))
        assertEquals(Duration.ZERO, Leitner.intervall(0))
    }

    @Test
    fun `richtig geht eine box hoch bis maximal fuenf`() {
        assertEquals(2, Leitner.neueBox(1, true))
        assertEquals(5, Leitner.neueBox(4, true))
        assertEquals(5, Leitner.neueBox(5, true))
    }

    @Test
    fun `falsch faellt immer auf box eins`() {
        assertEquals(1, Leitner.neueBox(5, false))
        assertEquals(1, Leitner.neueBox(2, false))
        assertEquals(1, Leitner.neueBox(1, false))
    }

    @Test
    fun `erste antwort erzeugt karte`() {
        val karte = Leitner.anwenden(null, 7, QuizModus.FOTO_ZU_NAME_MC, korrekt = true, jetzt = jetzt)
        assertEquals(0, karte.id)
        assertEquals(7, karte.schuelerId)
        assertEquals(2, karte.box)
        assertEquals(jetzt.plus(Duration.ofMinutes(10)), karte.faelligAm)
        assertEquals(1, karte.serieRichtig)
        assertEquals(jetzt, karte.letzteAntwortAm)
    }

    @Test
    fun `erste antwort falsch bleibt sofort faellig`() {
        val karte = Leitner.anwenden(null, 7, QuizModus.FOTO_ZU_NAME_MC, korrekt = false, jetzt = jetzt)
        assertEquals(1, karte.box)
        assertEquals(jetzt, karte.faelligAm)
        assertEquals(0, karte.serieRichtig)
    }

    @Test
    fun `bestehende karte behaelt id und zaehlt serie`() {
        val alt = Lernkarte(42, 7, QuizModus.NAME_ZU_FOTO, box = 3, faelligAm = jetzt, serieRichtig = 2, letzteAntwortAm = null)
        val richtig = Leitner.anwenden(alt, 7, QuizModus.NAME_ZU_FOTO, korrekt = true, jetzt = jetzt)
        assertEquals(42, richtig.id)
        assertEquals(4, richtig.box)
        assertEquals(3, richtig.serieRichtig)
        assertEquals(jetzt.plus(Duration.ofDays(3)), richtig.faelligAm)

        val falsch = Leitner.anwenden(alt, 7, QuizModus.NAME_ZU_FOTO, korrekt = false, jetzt = jetzt)
        assertEquals(1, falsch.box)
        assertEquals(0, falsch.serieRichtig)
        assertEquals(jetzt, falsch.faelligAm)
    }

    @Test
    fun `karte ohne antwort hat kein datum`() {
        assertNull(Lernkarte(1, 1, QuizModus.SPEEDRUN, 1, jetzt, 0, null).letzteAntwortAm)
    }
}
