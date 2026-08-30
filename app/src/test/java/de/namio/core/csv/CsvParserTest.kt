package de.namio.core.csv

import de.namio.core.model.Geschlecht
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CsvParserTest {

    @Test
    fun `utf8 bom wird erkannt und entfernt`() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Vorname;Nachname\nJörg;Müller".toByteArray(Charsets.UTF_8)
        val (text, cs) = CsvParser.dekodiere(bytes)
        assertEquals("UTF-8 (BOM)", cs)
        assertTrue(text.startsWith("Vorname"))
    }

    @Test
    fun `windows 1252 faellt zurueck`() {
        val bytes = "Vorname;Nachname\nJörg;Müller".toByteArray(charset("windows-1252"))
        val (text, cs) = CsvParser.dekodiere(bytes)
        assertEquals("Windows-1252", cs)
        assertTrue(text.contains("Jörg;Müller"))
    }

    @Test
    fun `trennzeichen semikolon komma tab`() {
        assertEquals(';', CsvParser.erkenneTrennzeichen("a;b;c\nd;e;f"))
        assertEquals(',', CsvParser.erkenneTrennzeichen("a,b,c\nd,e,f"))
        assertEquals('\t', CsvParser.erkenneTrennzeichen("a\tb\nc\td"))
        assertEquals(';', CsvParser.erkenneTrennzeichen("\"Meier, Lena\";7b\n\"Roth, Emil\";7b"))
    }

    @Test
    fun `anfuehrungszeichen und escapes`() {
        assertEquals(listOf("Meier, Lena", "7b", "sagt \"hi\""), CsvParser.zerlege("\"Meier, Lena\";7b;\"sagt \"\"hi\"\"\"", ';'))
    }

    @Test
    fun `kopfzeile erkannt und zuordnung per namen`() {
        val t = CsvParser.parse("Nachname;Vorname;Geschlecht\nMeier;Lena;w\nRoth;Emil;m\n")
        assertTrue(t.hatKopfzeile)
        assertEquals(2, t.zeilen.size)
        val z = CsvParser.schlageZuordnungVor(t)
        assertEquals(Zuordnung(vorname = 1, nachname = 0, kombiniert = null, geschlecht = 2), z)
        val s = CsvParser.schueler(t, z)
        assertEquals(ImportSchueler("Lena", "Meier", Geschlecht.MAEDCHEN), s[0])
        assertEquals(ImportSchueler("Emil", "Roth", Geschlecht.JUNGE), s[1])
    }

    @Test
    fun `ohne kopfzeile erste zwei spalten`() {
        val t = CsvParser.parse("Lena;Meier\nEmil;Roth")
        assertTrue(!t.hatKopfzeile)
        assertEquals(listOf("Spalte 1", "Spalte 2"), t.spalten)
        val z = CsvParser.schlageZuordnungVor(t)
        assertEquals(0, z.vorname); assertEquals(1, z.nachname)
    }

    @Test
    fun `nachname komma vorname in einer spalte`() {
        val t = CsvParser.parse("Name;Klasse\n\"Meier, Lena\";7b\n\"Roth, Emil\";7b\n")
        val z = CsvParser.schlageZuordnungVor(t)
        assertEquals(0, z.kombiniert)
        val s = CsvParser.schueler(t, z)
        assertEquals("Lena", s[0].vorname); assertEquals("Meier", s[0].nachname)
        assertNull(s[0].geschlecht)
    }

    @Test
    fun `name mit leerzeichen wird am letzten leerzeichen getrennt`() {
        val t = CsvParser.parse("Name\nAnna Lena Berg\nBen Roth")
        val s = CsvParser.schueler(t, Zuordnung(null, null, 0, null))
        assertEquals(ImportSchueler("Anna Lena", "Berg", null), s[0])
    }

    @Test
    fun `leere zeilen und leere namen fallen weg`() {
        val t = CsvParser.parse("Vorname;Nachname\n\n;\nLena;Meier\n")
        assertEquals(1, CsvParser.schueler(t, CsvParser.schlageZuordnungVor(t)).size)
    }

    @Test
    fun `geschlecht varianten`() {
        assertEquals(Geschlecht.MAEDCHEN, CsvParser.parseGeschlecht("weiblich"))
        assertEquals(Geschlecht.JUNGE, CsvParser.parseGeschlecht(" M "))
        assertNull(CsvParser.parseGeschlecht("divers"))
    }
}
