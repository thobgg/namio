package de.namio.core.sitzplan

import de.namio.core.model.Bestuhlung
import de.namio.core.model.Blickrichtung
import de.namio.core.model.Sitzplatz
import de.namio.core.model.SitzplanVorlage
import de.namio.core.model.Tisch
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Reine Logik für Sitzpläne mit Tischen und Slots – ohne Android, damit testbar. */
object SitzplanLogik {

    /** Ab dieser Entfernung (in Einheiten) vom Slot-Mittelpunkt gilt ein Drop als „auf dem Platz“. */
    const val TREFFER_RADIUS = 0.55f
    /** Tischtiefe in Platzbreiten, für Treffertests. */
    const val TISCH_TIEFE = 1.12f
    const val MAX_PLAETZE = 4
    const val MAX_BREITE = 6f

    /** Raumkoordinate eines Slots (Mittelpunkt), Drehung berücksichtigt. */
    fun slotPosition(tisch: Tisch, slot: Int, spalten: Int, reihen: Int): Pair<Float, Float> {
        // Slots gleichmäßig über die Tischbreite verteilt
        val versatz = if (tisch.plaetze <= 0) 0f else ((slot + 0.5f) / tisch.plaetze - 0.5f) * tisch.breite
        val rad = Math.toRadians(tisch.drehung.toDouble())
        return (tisch.x + (versatz * cos(rad) / spalten).toFloat()) to (tisch.y + (versatz * sin(rad) / reihen).toFloat())
    }

    /** Nächster Sitzplatz-Slot innerhalb [TREFFER_RADIUS] Einheiten um ([x], [y]) oder `null`. */
    fun slotBei(b: Bestuhlung, x: Float, y: Float, spalten: Int, reihen: Int): Sitzplatz? {
        var best: Sitzplatz? = null
        var bestAbstand = Float.MAX_VALUE
        for (p in b.plaetze) {
            val t = b.tisch(p.tischId) ?: continue
            val (sx, sy) = slotPosition(t, p.slot, spalten, reihen)
            val abstand = hypot((sx - x) * spalten, (sy - y) * reihen)
            if (abstand < bestAbstand) { bestAbstand = abstand; best = p }
        }
        return best?.takeIf { bestAbstand <= TREFFER_RADIUS }
    }

    /** Tisch, dessen (gedrehte) Fläche den Punkt ([x], [y]) enthält – der oberste zuerst. */
    fun tischBei(b: Bestuhlung, x: Float, y: Float, spalten: Int, reihen: Int): Tisch? =
        b.tische.lastOrNull { t ->
            val rad = Math.toRadians(-t.drehung.toDouble())
            val dx = (x - t.x) * spalten
            val dy = (y - t.y) * reihen
            val lx = dx * cos(rad) - dy * sin(rad)
            val ly = dx * sin(rad) + dy * cos(rad)
            kotlin.math.abs(lx) <= t.breite / 2 && kotlin.math.abs(ly) <= TISCH_TIEFE / 2
        }

    /** Ziel-Slot für einen Drop: naher Slot, sonst freier (oder nächster) Slot des getroffenen Tisches. */
    fun zielSlot(b: Bestuhlung, x: Float, y: Float, spalten: Int, reihen: Int): Sitzplatz? {
        slotBei(b, x, y, spalten, reihen)?.let { return it }
        val t = tischBei(b, x, y, spalten, reihen) ?: return null
        val slots = b.plaetzeVon(t.id)
        if (slots.isEmpty()) return null
        fun abstand(p: Sitzplatz): Float { val (sx, sy) = slotPosition(t, p.slot, spalten, reihen); return hypot((sx - x) * spalten, (sy - y) * reihen) }
        return slots.filter { it.schuelerId == null }.minByOrNull(::abstand) ?: slots.minByOrNull(::abstand)
    }

    /** Kopie eines Tisches (Breite, Plätze, Drehung, Beschriftung) direkt daneben, leer. */
    fun duplizieren(b: Bestuhlung, tischId: Long, spalten: Int, reihen: Int): Bestuhlung {
        val t = b.tisch(tischId) ?: return b
        val rad = Math.toRadians(t.drehung.toDouble())
        var nx = t.x + (t.breite * cos(rad) / spalten).toFloat()
        var ny = t.y + (t.breite * sin(rad) / reihen).toFloat()
        if (nx !in 0f..1f || ny !in 0f..1f) {
            nx = t.x - (t.breite * cos(rad) / spalten).toFloat()
            ny = t.y - (t.breite * sin(rad) / reihen).toFloat()
        }
        val neu = tischHinzufuegen(b, t.sitzplanId, nx.coerceIn(0f, 1f), ny.coerceIn(0f, 1f), t.drehung, t.plaetze, t.beschriftung, spalten, reihen, einrasten = false)
        val id = neu.tische.last().id
        return neu.copy(tische = neu.tische.map { if (it.id == id) it.copy(breite = t.breite) else it })
    }

    /** Rastet auf das halbe Raster ein und hält den Punkt im Raum. */
    fun einrasten(x: Float, y: Float, spalten: Int, reihen: Int): Pair<Float, Float> {
        val sx = (x * spalten * 2).roundToInt() / (spalten * 2f)
        val sy = (y * reihen * 2).roundToInt() / (reihen * 2f)
        return sx.coerceIn(0.5f / spalten, 1f - 0.5f / spalten) to sy.coerceIn(0.5f / reihen, 1f - 0.5f / reihen)
    }

    private fun position(x: Float, y: Float, spalten: Int, reihen: Int, einrasten: Boolean) =
        if (einrasten) einrasten(x, y, spalten, reihen) else x.coerceIn(0f, 1f) to y.coerceIn(0f, 1f)

    private fun neueId(b: Bestuhlung): Long = (b.tische.minOfOrNull { it.id }?.coerceAtMost(0L) ?: 0L) - 1

    /** Neuer Tisch mit [plaetze] leeren Slots (0 = Möbel mit [beschriftung]). */
    fun tischHinzufuegen(b: Bestuhlung, sitzplanId: Long, x: Float, y: Float, drehung: Float, plaetze: Int, beschriftung: String?, spalten: Int, reihen: Int, einrasten: Boolean, breite: Float? = null): Bestuhlung {
        val (nx, ny) = position(x, y, spalten, reihen, einrasten)
        val id = neueId(b)
        val n = plaetze.coerceIn(0, MAX_PLAETZE)
        val tisch = Tisch(id, sitzplanId, nx, ny, drehung, n, beschriftung?.takeIf { plaetze == 0 }, breite = (breite ?: n.coerceAtLeast(1).toFloat()).coerceIn(0.5f, MAX_BREITE))
        val slots = (0 until tisch.plaetze).map { Sitzplatz(0, sitzplanId, id, it, null) }
        return Bestuhlung(b.tische + tisch, b.plaetze + slots)
    }

    /**
     * Legt [schuelerId] an ([x], [y]) ab: auf einem Slot → belegen bzw. mit dem Sitzenden tauschen;
     * im Freien → neuer Einzeltisch. Sitzt der Schüler schon, wird sein alter Slot frei.
     */
    fun ablegen(b: Bestuhlung, sitzplanId: Long, schuelerId: Long, x: Float, y: Float, spalten: Int, reihen: Int, einrasten: Boolean): Bestuhlung {
        val alt = b.plaetze.firstOrNull { it.schuelerId == schuelerId }
        val ziel = zielSlot(b, x, y, spalten, reihen)
        return when {
            ziel == null -> {
                val ohne = if (alt != null) b.copy(plaetze = b.plaetze.map { if (it.id == alt.id) it.copy(schuelerId = null) else it }) else b
                val mitTisch = tischHinzufuegen(ohne, sitzplanId, x, y, 0f, 1, null, spalten, reihen, einrasten)
                val neuerTisch = mitTisch.tische.last()
                mitTisch.copy(plaetze = mitTisch.plaetze.map { if (it.tischId == neuerTisch.id) it.copy(schuelerId = schuelerId) else it })
            }
            alt != null && alt.id == ziel.id -> b
            else -> b.copy(
                plaetze = b.plaetze.map {
                    when (it.id) {
                        ziel.id -> it.copy(schuelerId = schuelerId)
                        alt?.id -> it.copy(schuelerId = ziel.schuelerId)
                        else -> it
                    }
                },
            )
        }
    }

    fun verschieben(b: Bestuhlung, tischId: Long, x: Float, y: Float, spalten: Int, reihen: Int, einrasten: Boolean): Bestuhlung {
        val (nx, ny) = position(x, y, spalten, reihen, einrasten)
        return b.copy(tische = b.tische.map { if (it.id == tischId) it.copy(x = nx, y = ny) else it })
    }

    fun drehen(b: Bestuhlung, tischId: Long, grad: Float): Bestuhlung =
        b.copy(tische = b.tische.map { if (it.id == tischId) it.copy(drehung = ((it.drehung + grad) % 360 + 360) % 360) else it })

    /** Ändert die Tischbreite in Platzbreiten (0,5–[MAX_BREITE]). */
    fun breiteAendern(b: Bestuhlung, tischId: Long, breite: Float): Bestuhlung =
        b.copy(tische = b.tische.map { if (it.id == tischId) it.copy(breite = breite.coerceIn(0.5f, MAX_BREITE)) else it })

    /** Ändert die Platzzahl (1–[MAX_PLAETZE]). Wegfallende Slots lassen ihre Schüler unplatziert. */
    fun plaetzeAendern(b: Bestuhlung, tischId: Long, plaetze: Int): Bestuhlung {
        val t = b.tisch(tischId) ?: return b
        if (t.istMoebel) return b
        val n = plaetze.coerceIn(1, MAX_PLAETZE)
        val vorhanden = b.plaetzeVon(tischId)
        val behalten = b.plaetze.filter { it.tischId != tischId || it.slot < n }
        val neue = (vorhanden.size until n).map { Sitzplatz(0, t.sitzplanId, tischId, it, null) }
        return Bestuhlung(b.tische.map { if (it.id == tischId) it.copy(plaetze = n, breite = maxOf(it.breite, n.toFloat())) else it }, behalten + neue)
    }

    /** Macht aus einem Tisch ohne Sitzende ein Möbel mit Text; leerer Text macht einen Einzeltisch daraus. */
    fun beschriften(b: Bestuhlung, tischId: Long, text: String): Bestuhlung {
        val t = b.tisch(tischId) ?: return b
        if (b.plaetzeVon(tischId).any { it.schuelerId != null }) return b
        val neu = text.trim().ifBlank { null }
        return if (neu == null) {
            val ohne = b.copy(tische = b.tische.map { if (it.id == tischId) it.copy(plaetze = 1, beschriftung = null) else it }, plaetze = b.plaetze.filter { it.tischId != tischId })
            ohne.copy(plaetze = ohne.plaetze + Sitzplatz(0, t.sitzplanId, tischId, 0, null))
        } else {
            Bestuhlung(b.tische.map { if (it.id == tischId) it.copy(plaetze = 0, beschriftung = neu) else it }, b.plaetze.filter { it.tischId != tischId })
        }
    }

    fun entfernen(b: Bestuhlung, schuelerId: Long): Bestuhlung =
        b.copy(plaetze = b.plaetze.map { if (it.schuelerId == schuelerId) it.copy(schuelerId = null) else it })

    fun tischLoeschen(b: Bestuhlung, tischId: Long): Bestuhlung =
        Bestuhlung(b.tische.filter { it.id != tischId }, b.plaetze.filter { it.tischId != tischId })

    /** Verteilt die sitzenden Schüler zufällig auf die belegten Slots. */
    fun mischen(b: Bestuhlung, random: Random = Random.Default): Bestuhlung {
        val belegt = b.plaetze.filter { it.schuelerId != null }
        val ids = belegt.mapNotNull { it.schuelerId }.shuffled(random)
        val neu = belegt.map { it.id }.zip(ids).toMap()
        return b.copy(plaetze = b.plaetze.map { p -> if (p.id in neu) p.copy(schuelerId = neu.getValue(p.id)) else p })
    }

    /** Anzeige: von vorn ist der Raum um 180° gedreht (Tafel beim Betrachter). */
    fun anzeige(t: Tisch, blickrichtung: Blickrichtung): Tisch = when (blickrichtung) {
        Blickrichtung.VON_HINTEN -> t
        Blickrichtung.VON_VORN -> t.copy(x = 1f - t.x, y = 1f - t.y, drehung = (t.drehung + 180f) % 360)
    }

    fun modellKoordinate(x: Float, y: Float, blickrichtung: Blickrichtung): Pair<Float, Float> = when (blickrichtung) {
        Blickrichtung.VON_HINTEN -> x to y
        Blickrichtung.VON_VORN -> (1f - x) to (1f - y)
    }

    /**
     * Bestuhlung einer Vorlage für einen Raum von [spalten] × [reihen] Einheiten, Slots der Reihe
     * nach mit [schuelerIds] belegt. Reihe 0 liegt an der Tafel (y klein).
     */
    fun vorlage(art: SitzplanVorlage, sitzplanId: Long, spalten: Int, reihen: Int, schuelerIds: List<Long>): Bestuhlung {
        val tische: List<Triple<Pair<Float, Float>, Float, Int>> = when (art) {
            SitzplanVorlage.LEER -> emptyList()
            SitzplanVorlage.RASTER_EINZEL -> rasterEinzel(spalten, reihen, schuelerIds.size)
            SitzplanVorlage.DOPPELTISCH_REIHEN -> doppeltischReihen(spalten, reihen)
            SitzplanVorlage.U_FORM -> uForm(spalten, reihen)
            SitzplanVorlage.GRUPPENTISCHE -> gruppentische(spalten, reihen)
        }
        var b = Bestuhlung()
        tische.forEach { (pos, d, n) -> b = tischHinzufuegen(b, sitzplanId, pos.first, pos.second, d, n, null, spalten, reihen, einrasten = false) }
        var i = 0
        val plaetze = b.plaetze.map { p -> if (i < schuelerIds.size) p.copy(schuelerId = schuelerIds[i++]) else p }
        return b.copy(plaetze = plaetze)
    }

    /** Einzeltische im Raster: so viele wie Kinder (mindestens 1), 1,4 Einheiten Abstand, ab 1,5 Einheiten hinter der Tafel. */
    private fun rasterEinzel(spalten: Int, reihen: Int, anzahl: Int): List<Triple<Pair<Float, Float>, Float, Int>> {
        val proZeile = ((spalten - 1) / 1.4f).toInt().coerceAtLeast(1)
        val out = mutableListOf<Triple<Pair<Float, Float>, Float, Int>>()
        val n = anzahl.coerceAtLeast(1)
        val breiteGesamt = (minOf(n, proZeile) - 1) * 1.4f
        val links = (spalten - breiteGesamt) / 2f
        for (i in 0 until n) {
            val z = i / proZeile
            val s = i % proZeile
            val y = (1.5f + z * 1.4f + 0.5f) / reihen
            if (y > 1f) break
            out += Triple(((links + s * 1.4f) / spalten) to y, 0f, 1)
        }
        return out
    }

    private fun doppeltischReihen(spalten: Int, reihen: Int): List<Triple<Pair<Float, Float>, Float, Int>> {
        val tische = ((spalten + 1) / 3).coerceAtLeast(1)
        val breite = tische * 3 - 1
        val links = (spalten - breite) / 2f
        val zeilen = ((reihen - 1) / 2).coerceAtLeast(1)
        val out = mutableListOf<Triple<Pair<Float, Float>, Float, Int>>()
        for (z in 0 until zeilen) {
            val y = (1.5f + z * 2f + 0.5f) / reihen
            for (t in 0 until tische) out += Triple(((links + t * 3 + 1f) / spalten) to y, 0f, 2)
        }
        return out
    }

    private fun uForm(spalten: Int, reihen: Int): List<Triple<Pair<Float, Float>, Float, Int>> {
        val out = mutableListOf<Triple<Pair<Float, Float>, Float, Int>>()
        val hinten = (reihen - 0.5f) / reihen
        var x = 0
        while (x + 2 <= spalten) { out += Triple(((x + 1f) / spalten) to hinten, 0f, 2); x += 2 }
        var r = reihen - 2
        while (r - 1 >= 2) {
            out += Triple((0.5f / spalten) to ((r) / reihen.toFloat()), 90f, 2)
            out += Triple(((spalten - 0.5f) / spalten) to ((r) / reihen.toFloat()), 270f, 2)
            r -= 2
        }
        return out
    }

    private fun gruppentische(spalten: Int, reihen: Int): List<Triple<Pair<Float, Float>, Float, Int>> {
        val out = mutableListOf<Triple<Pair<Float, Float>, Float, Int>>()
        val gx = ((spalten + 1) / 3).coerceAtLeast(1)
        val gy = ((reihen - 1) / 3).coerceAtLeast(1)
        val links = (spalten - (gx * 3 - 1)) / 2f
        for (j in 0 until gy) for (i in 0 until gx) {
            val cx = (links + i * 3 + 1f) / spalten
            val y0 = 1.5f + j * 3
            out += Triple(cx to ((y0 + 0.5f) / reihen), 180f, 2)
            out += Triple(cx to ((y0 + 1.5f) / reihen), 0f, 2)
        }
        return out
    }
}
