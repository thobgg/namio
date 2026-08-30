package de.namio.core.sitzplan

import de.namio.core.model.Blickrichtung
import de.namio.core.model.Sitzplatz
import de.namio.core.model.SitzplanVorlage
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.random.Random

/** Reine Logik für frei positionierbare Sitzpläne – ohne Android, damit testbar. */
object SitzplanLogik {

    /** Ein Platz ist eine Rastereinheit breit; ab dieser Entfernung (in Einheiten) gilt ein Drop als „auf dem Platz“. */
    const val TREFFER_RADIUS = 0.55f

    /**
     * Legt [schuelerId] an Raumposition ([x], [y]) ab. Liegt dort ein Platz, wird er belegt bzw.
     * mit dem Sitzenden getauscht; sonst entsteht ein neuer Platz. Sitzt der Schüler schon, wandert er.
     */
    fun ablegen(plaetze: List<Sitzplatz>, sitzplanId: Long, schuelerId: Long, x: Float, y: Float, spalten: Int, reihen: Int, einrasten: Boolean): List<Sitzplatz> {
        val alt = plaetze.firstOrNull { it.schuelerId == schuelerId }
        val ziel = platzBei(plaetze, x, y, spalten, reihen)
        return when {
            ziel == null -> {
                val (nx, ny) = if (einrasten) einrasten(x, y, spalten, reihen) else x.coerceIn(0f, 1f) to y.coerceIn(0f, 1f)
                if (alt != null) {
                    // Schüler samt Platz verschieben
                    plaetze.map { if (it.id == alt.id) it.copy(x = nx, y = ny) else it }
                } else {
                    plaetze + Sitzplatz(0, sitzplanId, schuelerId, nx, ny)
                }
            }
            ziel.id == alt?.id -> plaetze
            else -> plaetze.map {
                when (it.id) {
                    ziel.id -> it.copy(schuelerId = schuelerId)
                    alt?.id -> it.copy(schuelerId = ziel.schuelerId)
                    else -> it
                }
            }
        }
    }

    /** Verschiebt einen Platz (samt Sitzendem) an eine neue Position. */
    fun verschieben(plaetze: List<Sitzplatz>, platzId: Long, x: Float, y: Float, spalten: Int, reihen: Int, einrasten: Boolean): List<Sitzplatz> {
        val (nx, ny) = if (einrasten) einrasten(x, y, spalten, reihen) else x.coerceIn(0f, 1f) to y.coerceIn(0f, 1f)
        return plaetze.map { if (it.id == platzId) it.copy(x = nx, y = ny) else it }
    }

    /** Dreht einen Platz um [grad] weiter (Ergebnis in 0 ≤ d < 360). */
    fun drehen(plaetze: List<Sitzplatz>, platzId: Long, grad: Float): List<Sitzplatz> =
        plaetze.map { if (it.id == platzId) it.copy(drehung = ((it.drehung + grad) % 360 + 360) % 360) else it }

    /** Nimmt den Schüler vom Plan; der Stuhl bleibt leer stehen. */
    fun entfernen(plaetze: List<Sitzplatz>, schuelerId: Long): List<Sitzplatz> =
        plaetze.map { if (it.schuelerId == schuelerId) it.copy(schuelerId = null) else it }

    /** Entfernt den Platz komplett. */
    fun platzLoeschen(plaetze: List<Sitzplatz>, platzId: Long): List<Sitzplatz> = plaetze.filter { it.id != platzId }

    /** Neuer leerer Stuhl an einer Position. */
    fun leererStuhl(plaetze: List<Sitzplatz>, sitzplanId: Long, x: Float, y: Float, spalten: Int, reihen: Int, einrasten: Boolean): List<Sitzplatz> {
        val (nx, ny) = if (einrasten) einrasten(x, y, spalten, reihen) else x.coerceIn(0f, 1f) to y.coerceIn(0f, 1f)
        return plaetze + Sitzplatz(0, sitzplanId, null, nx, ny)
    }

    /** Legt rechts neben [platzId] einen Partnerplatz mit gleicher Drehung an (Doppeltisch). */
    fun partnerplatz(plaetze: List<Sitzplatz>, platzId: Long, spalten: Int, reihen: Int): List<Sitzplatz> {
        val p = plaetze.firstOrNull { it.id == platzId } ?: return plaetze
        val rad = Math.toRadians(p.drehung.toDouble())
        val dx = (Math.cos(rad) / spalten).toFloat()
        val dy = (Math.sin(rad) / reihen).toFloat()
        val nx = (p.x + dx).coerceIn(0f, 1f)
        val ny = (p.y + dy).coerceIn(0f, 1f)
        if (platzBei(plaetze, nx, ny, spalten, reihen) != null) return plaetze
        return plaetze + Sitzplatz(0, p.sitzplanId, null, nx, ny, p.drehung)
    }

    /** Verteilt die sitzenden Schüler zufällig auf ihre bisherigen Plätze (leere Stühle bleiben leer). */
    fun mischen(plaetze: List<Sitzplatz>, random: Random = Random.Default): List<Sitzplatz> {
        val belegt = plaetze.filter { it.schuelerId != null }
        val ids = belegt.mapNotNull { it.schuelerId }.shuffled(random)
        val neu = belegt.map { it.id }.zip(ids).toMap()
        return plaetze.map { p -> if (p.id in neu) p.copy(schuelerId = neu.getValue(p.id)) else p }
    }

    /** Nächster Sitzplatz (kein Möbel) innerhalb [TREFFER_RADIUS] Einheiten um ([x], [y]) oder `null`. */
    fun platzBei(plaetze: List<Sitzplatz>, x: Float, y: Float, spalten: Int, reihen: Int): Sitzplatz? =
        plaetze.filter { !it.istMoebel }.minByOrNull { abstandInEinheiten(it, x, y, spalten, reihen) }
            ?.takeIf { abstandInEinheiten(it, x, y, spalten, reihen) <= TREFFER_RADIUS }

    /** Macht aus einem leeren Stuhl ein Möbel mit Text – oder ändert den Text; leerer Text macht wieder einen Stuhl daraus. */
    fun beschriften(plaetze: List<Sitzplatz>, platzId: Long, text: String): List<Sitzplatz> =
        plaetze.map {
            if (it.id == platzId && it.schuelerId == null) it.copy(beschriftung = text.trim().ifBlank { null }) else it
        }

    private fun abstandInEinheiten(p: Sitzplatz, x: Float, y: Float, spalten: Int, reihen: Int): Float =
        hypot((p.x - x) * spalten, (p.y - y) * reihen)

    /** Rastet auf das halbe Raster ein und hält den Platz im Raum. */
    fun einrasten(x: Float, y: Float, spalten: Int, reihen: Int): Pair<Float, Float> {
        val sx = (x * spalten * 2).roundToInt() / (spalten * 2f)
        val sy = (y * reihen * 2).roundToInt() / (reihen * 2f)
        val rand = 0.5f
        return sx.coerceIn(rand / spalten, 1f - rand / spalten) to sy.coerceIn(rand / reihen, 1f - rand / reihen)
    }

    /** Anzeigeposition: von vorn ist der Raum um 180° gedreht (Tafel beim Betrachter). */
    fun anzeige(p: Sitzplatz, blickrichtung: Blickrichtung): Sitzplatz = when (blickrichtung) {
        Blickrichtung.VON_HINTEN -> p
        Blickrichtung.VON_VORN -> p.copy(x = 1f - p.x, y = 1f - p.y, drehung = (p.drehung + 180f) % 360)
    }

    /** Umkehrung von [anzeige] für Fingerpositionen. */
    fun modellKoordinate(x: Float, y: Float, blickrichtung: Blickrichtung): Pair<Float, Float> = when (blickrichtung) {
        Blickrichtung.VON_HINTEN -> x to y
        Blickrichtung.VON_VORN -> (1f - x) to (1f - y)
    }

    /**
     * Erzeugt die Plätze einer Vorlage für einen Raum von [spalten] × [reihen] Einheiten,
     * belegt der Reihe nach mit [schuelerIds] (überzählige Plätze bleiben leer, überzählige
     * Schüler bleiben unplatziert). Reihe 0 liegt an der Tafel (y klein).
     */
    fun vorlage(art: SitzplanVorlage, sitzplanId: Long, spalten: Int, reihen: Int, schuelerIds: List<Long>): List<Sitzplatz> {
        val punkte: List<Triple<Float, Float, Float>> = when (art) {
            SitzplanVorlage.LEER -> emptyList()
            SitzplanVorlage.DOPPELTISCH_REIHEN -> doppeltischReihen(spalten, reihen)
            SitzplanVorlage.U_FORM -> uForm(spalten, reihen)
            SitzplanVorlage.GRUPPENTISCHE -> gruppentische(spalten, reihen)
        }
        return punkte.mapIndexed { i, (x, y, d) -> Sitzplatz(0, sitzplanId, schuelerIds.getOrNull(i), x, y, d) }
    }

    private fun doppeltischReihen(spalten: Int, reihen: Int): List<Triple<Float, Float, Float>> {
        // Doppeltische (2 Einheiten) mit 1 Einheit Gang dazwischen, Reihen mit 1 Einheit Abstand, 1,5 Einheiten Abstand zur Tafel
        val tische = ((spalten + 1) / 3).coerceAtLeast(1)
        val breite = tische * 3 - 1
        val links = (spalten - breite) / 2f
        val zeilen = ((reihen - 1) / 2).coerceAtLeast(1)
        val out = mutableListOf<Triple<Float, Float, Float>>()
        for (z in 0 until zeilen) {
            val y = (1.5f + z * 2f + 0.5f) / reihen
            for (t in 0 until tische) for (s in 0 until 2) {
                val x = (links + t * 3 + s + 0.5f) / spalten
                out += Triple(x, y, 0f)
            }
        }
        return out
    }

    private fun uForm(spalten: Int, reihen: Int): List<Triple<Float, Float, Float>> {
        val out = mutableListOf<Triple<Float, Float, Float>>()
        val obenY = (reihen - 0.5f) / reihen // hintere Reihe (weg von der Tafel)
        for (s in 0 until spalten) out += Triple((s + 0.5f) / spalten, obenY, 0f)
        for (r in reihen - 2 downTo 2) {
            out += Triple(0.5f / spalten, (r + 0.5f) / reihen, 90f)
            out += Triple((spalten - 0.5f) / spalten, (r + 0.5f) / reihen, 270f)
        }
        return out
    }

    private fun gruppentische(spalten: Int, reihen: Int): List<Triple<Float, Float, Float>> {
        // 4er-Gruppen (2×2) im Abstand von 3 Einheiten
        val out = mutableListOf<Triple<Float, Float, Float>>()
        val gx = ((spalten + 1) / 3).coerceAtLeast(1)
        val gy = ((reihen - 1) / 3).coerceAtLeast(1)
        val links = (spalten - (gx * 3 - 1)) / 2f
        for (j in 0 until gy) for (i in 0 until gx) {
            val x0 = links + i * 3
            val y0 = 1.5f + j * 3
            for (dy in 0 until 2) for (dx in 0 until 2) {
                out += Triple((x0 + dx + 0.5f) / spalten, (y0 + dy + 0.5f) / reihen, if (dy == 0) 180f else 0f)
            }
        }
        return out
    }
}
