package de.namio.core.transfer

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TresorTest {
    @Test
    fun `roundtrip mit passwort`() {
        val klartext = "Lena;Meier;w".toByteArray()
        val geheim = Tresor.verschluessele(klartext, "sehr-geheim".toCharArray())
        assertArrayEquals(klartext, Tresor.entschluessele(geheim, "sehr-geheim".toCharArray()))
        assertEquals(true, String(geheim, 0, 6, Charsets.US_ASCII) == "NAMIO1")
    }

    @Test
    fun `falsches passwort und kaputte datei werden erkannt`() {
        val geheim = Tresor.verschluessele("x".toByteArray(), "a".toCharArray())
        assertThrows(Tresor.FalschesPasswortOderDatei::class.java) { Tresor.entschluessele(geheim, "b".toCharArray()) }
        assertThrows(Tresor.FalschesPasswortOderDatei::class.java) { Tresor.entschluessele("kein namio".toByteArray(), "a".toCharArray()) }
        val manipuliert = geheim.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        assertThrows(Tresor.FalschesPasswortOderDatei::class.java) { Tresor.entschluessele(manipuliert, "a".toCharArray()) }
    }

    @Test
    fun `jede verschluesselung ist anders`() {
        val a = Tresor.verschluessele("x".toByteArray(), "p".toCharArray())
        val b = Tresor.verschluessele("x".toByteArray(), "p".toCharArray())
        assertEquals(false, a.contentEquals(b))
    }
}

class ExportFormatTest {
    @Test
    fun `json roundtrip`() {
        val d = ExportDaten(
            exportiertAm = 1L,
            klassen = listOf(KlasseX(1, "7b", "", "", 0, false)),
            schueler = listOf(SchuelerX(1, 1, "Lena", "Meier", "", "a.jpg", "", 0, "MAEDCHEN")),
            lernkarten = emptyList(), sessions = emptyList(), antworten = emptyList(),
            sitzplaene = emptyList(), tische = emptyList(), plaetze = emptyList(),
        )
        val zurueck = ExportFormat.dekodiere(ExportFormat.kodiere(d))
        assertEquals(d, zurueck)
        // unbekannte Felder späterer Versionen stören nicht
        val text = ExportFormat.kodiere(d).replace("\"version\":1", "\"version\":1,\"neu\":true")
        assertEquals(d, ExportFormat.dekodiere(text))
    }
}
