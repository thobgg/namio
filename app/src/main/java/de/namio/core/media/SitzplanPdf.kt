package de.namio.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import de.namio.core.model.Bestuhlung
import de.namio.core.model.Blickrichtung
import de.namio.core.model.Schueler
import de.namio.core.model.Sitzplan
import de.namio.core.sitzplan.SitzplanLogik
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** Rendert einen Sitzplan als einseitiges PDF (A4 quer) – fürs Pult oder die Vertretung. */
@Singleton
class SitzplanPdf @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fotoStore: FotoStore,
) {
    /** Schreibt das PDF nach [ziel]. [titel] steht oben, z. B. „7b · Klassenraum“. */
    suspend fun schreibe(
        ziel: Uri,
        titel: String,
        plan: Sitzplan,
        bestuhlung: Bestuhlung,
        schueler: Map<Long, Schueler>,
        blickrichtung: Blickrichtung,
        mitFotos: Boolean,
        tafelText: String,
    ) = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        val seite = doc.startPage(PdfDocument.PageInfo.Builder(A4_BREITE, A4_HOEHE, 1).create())
        zeichne(seite.canvas, titel, plan, bestuhlung, schueler, blickrichtung, mitFotos, tafelText)
        doc.finishPage(seite)
        context.contentResolver.openOutputStream(ziel, "wt")?.use { doc.writeTo(it) } ?: throw IOException("Ziel nicht beschreibbar")
        doc.close()
    }

    private fun zeichne(c: Canvas, titel: String, plan: Sitzplan, b: Bestuhlung, schueler: Map<Long, Schueler>, blick: Blickrichtung, mitFotos: Boolean, tafelText: String) {
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 16f }
        val klein = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 7.5f; textAlign = Paint.Align.CENTER }
        val rahmen = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; style = Paint.Style.STROKE; strokeWidth = 0.8f }
        val fuell = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        val moebel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8E8F0.toInt(); style = Paint.Style.FILL }
        val kante = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; strokeWidth = 2f }
        val tafel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2F4F3F.toInt(); style = Paint.Style.FILL }
        val weiss = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 10f; textAlign = Paint.Align.CENTER }

        val datum = LocalDate.now().format(DateTimeFormatter.ofPattern("d. MMMM yyyy"))
        c.drawText(titel, RAND, RAND + 14f, text)
        val rechts = Paint(text).apply { textAlign = Paint.Align.RIGHT; textSize = 10f; color = Color.DKGRAY }
        c.drawText(datum, A4_BREITE - RAND, RAND + 14f, rechts)

        // Raumfläche: Tafel unten (von vorn) oder oben (von hinten), 18 pt hoch
        val oben = RAND + 28f
        val unten = A4_HOEHE - RAND
        val tafelHoehe = 18f
        val tafelOben = blick == Blickrichtung.VON_HINTEN
        val raumOben = if (tafelOben) oben + tafelHoehe + 6f else oben
        val raumUnten = if (tafelOben) unten else unten - tafelHoehe - 6f
        val einheit = minOf((A4_BREITE - 2 * RAND) / plan.spalten, (raumUnten - raumOben) / plan.reihen)
        val raumBreite = einheit * plan.spalten
        val raumHoehe = einheit * plan.reihen
        val links = (A4_BREITE - raumBreite) / 2f
        val raumTop = raumOben + ((raumUnten - raumOben) - raumHoehe) / 2f
        c.drawRect(links, raumTop, links + raumBreite, raumTop + raumHoehe, Paint(rahmen).apply { color = Color.LTGRAY })

        val tafelY = if (tafelOben) oben else raumTop + raumHoehe + 6f
        val tafelLinks = links + raumBreite * 0.15f
        c.drawRoundRect(RectF(tafelLinks, tafelY, tafelLinks + raumBreite * 0.7f, tafelY + tafelHoehe), 3f, 3f, tafel)
        c.drawText(tafelText, links + raumBreite / 2f, tafelY + 13f, weiss)

        val tiefe = einheit * 1.05f
        for (t in b.tische) {
            val a = SitzplanLogik.anzeige(t, blick)
            val cx = links + a.x * raumBreite
            val cy = raumTop + a.y * raumHoehe
            val breite = einheit * t.breite * 0.96f
            c.save()
            c.rotate(a.drehung, cx, cy)
            val r = RectF(cx - breite / 2, cy - tiefe / 2, cx + breite / 2, cy + tiefe / 2)
            c.drawRoundRect(r, 3f, 3f, if (t.istMoebel) moebel else fuell)
            c.drawRoundRect(r, 3f, 3f, rahmen)
            val kopfueber = ((a.drehung % 360) + 360) % 360 in 90f..270f
            if (t.istMoebel) {
                if (kopfueber) c.rotate(180f, cx, cy)
                c.drawText(t.beschriftung.orEmpty(), cx, cy + 3f, klein)
            } else {
                c.drawLine(r.left + breite * 0.05f, r.top + 2f, r.right - breite * 0.05f, r.top + 2f, kante)
                if (kopfueber) c.rotate(180f, cx, cy)
                val slots = b.plaetzeVon(t.id)
                val slotBreite = breite / t.plaetze.coerceAtLeast(1)
                slots.forEach { slot ->
                    val index = if (kopfueber) t.plaetze - 1 - slot.slot else slot.slot
                    val sx = r.left + slotBreite * (index + 0.5f)
                    val s = slot.schuelerId?.let(schueler::get)
                    if (s != null) {
                        val fotoGroesse = einheit * 0.5f
                        if (mitFotos) {
                            val bmp = s.fotoDatei?.let { lade(it, (fotoGroesse * 4).toInt()) }
                            val fr = RectF(sx - fotoGroesse / 2, cy - tiefe / 2 + 5f, sx + fotoGroesse / 2, cy - tiefe / 2 + 5f + fotoGroesse)
                            if (bmp != null) c.drawBitmap(bmp, null, fr, null) else c.drawRoundRect(fr, 2f, 2f, Paint(rahmen).apply { color = Color.LTGRAY })
                        }
                        val nameY = if (mitFotos) cy + tiefe / 2 - 4f else cy + 3f
                        c.drawText(kuerze(s.anzeigeName, slotBreite, klein), sx, nameY, klein)
                    } else {
                        c.drawRoundRect(RectF(sx - 4f, cy - 4f, sx + 4f, cy + 4f), 1f, 1f, Paint(rahmen).apply { color = Color.LTGRAY; style = Paint.Style.FILL })
                    }
                }
            }
            c.restore()
        }
    }

    private fun kuerze(name: String, maxBreite: Float, paint: Paint): String {
        if (paint.measureText(name) <= maxBreite - 4f) return name
        var s = name
        while (s.length > 2 && paint.measureText("$s…") > maxBreite - 4f) s = s.dropLast(1)
        return "$s…"
    }

    private fun lade(name: String, kante: Int): Bitmap? {
        val f = fotoStore.datei(name)
        if (!f.exists()) return null
        val grenzen = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, grenzen)
        val opt = BitmapFactory.Options().apply { inSampleSize = Bildzuschnitt.sampleSize(grenzen.outWidth, grenzen.outHeight, kante) }
        return BitmapFactory.decodeFile(f.path, opt)
    }

    private companion object {
        const val A4_BREITE = 842
        const val A4_HOEHE = 595
        const val RAND = 24f
    }
}
