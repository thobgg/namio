package de.namio.feature.sitzplan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import de.namio.R
import de.namio.core.media.FotoStore
import de.namio.core.model.Blickrichtung
import de.namio.core.model.Schueler
import de.namio.core.model.Sitzplan
import de.namio.core.model.Sitzplatz
import de.namio.core.sitzplan.SitzplanLogik
import de.namio.ui.components.SchuelerFoto
import kotlin.math.roundToInt

/** Farbliche Hervorhebung eines Platzes (Quiz-Feedback, Auswahl). */
enum class PlatzMarkierung { KEINE, AUSGEWAEHLT, RICHTIG, FALSCH }

/** Geometrie der gezeichneten Fläche, um Fingerpositionen in Raumkoordinaten umzurechnen. */
data class FlaechenGeometrie(val breitePx: Float, val hoehePx: Float, val einheitPx: Float)

/**
 * Zeichnet einen Sitzplan als freie Fläche im Seitenverhältnis [Sitzplan.spalten]:[Sitzplan.reihen].
 * Plätze werden an ihren normierten Mittelpunkten platziert und gedreht; die Tafel liegt je nach
 * [blickrichtung] oben (von hinten) oder unten (von vorn).
 */
@Composable
fun SitzplanFlaeche(
    plan: Sitzplan,
    plaetze: List<Sitzplatz>,
    schuelerProId: Map<Long, Schueler>,
    blickrichtung: Blickrichtung,
    fotoStore: FotoStore,
    modifier: Modifier = Modifier,
    /** 1 = Raum füllt die Breite; größer = Kacheln größer, Fläche wird scrollbar. */
    zoom: Float = 1f,
    namenZeigen: Boolean = true,
    rasterZeigen: Boolean = false,
    markierung: (Sitzplatz) -> PlatzMarkierung = { PlatzMarkierung.KEINE },
    onGeometrie: ((FlaechenGeometrie) -> Unit)? = null,
    flaechenModifier: Modifier = Modifier,
    platzModifier: (Sitzplatz) -> Modifier = { Modifier },
) {
    val tafelOben = blickrichtung == Blickrichtung.VON_HINTEN
    BoxWithConstraints(modifier) {
        // Im Scrollcontainer ist die Breite unbegrenzt – dann auf die Basisbreite zurückfallen.
        val basis: Dp = if (maxWidth.value.isFinite() && maxWidth < BASIS_BREITE) maxWidth else BASIS_BREITE
        val einheit: Dp = (basis / plan.spalten) * zoom
        val breite: Dp = einheit * plan.spalten
        val hoehe: Dp = einheit * plan.reihen
        Column(Modifier.width(breite), horizontalAlignment = Alignment.CenterHorizontally) {
        if (tafelOben) Tafel(breite)
        Box(Modifier.width(breite)) {
            val dichte = LocalDensity.current
            with(dichte) { onGeometrie?.invoke(FlaechenGeometrie(breite.toPx(), hoehe.toPx(), einheit.toPx())) }
            Box(
                Modifier
                    .width(breite)
                    .height(hoehe)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(8.dp))
                    .then(flaechenModifier),
            ) {
                if (rasterZeigen) {
                    val linie = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    Canvas(Modifier.fillMaxSize()) {
                        val e = size.width / plan.spalten
                        for (i in 1 until plan.spalten) drawLine(linie, Offset(i * e, 0f), Offset(i * e, size.height), 1f)
                        for (j in 1 until plan.reihen) drawLine(linie, Offset(0f, j * e), Offset(size.width, j * e), 1f)
                    }
                }
                val platzGroesse = einheit * 0.92f
                plaetze.forEach { platz ->
                    val a = SitzplanLogik.anzeige(platz, blickrichtung)
                    val links = with(dichte) { (breite * a.x - platzGroesse / 2).toPx().roundToInt() }
                    val oben = with(dichte) { (hoehe * a.y - platzGroesse / 2).toPx().roundToInt() }
                    Platz(
                        schueler = platz.schuelerId?.let(schuelerProId::get),
                        beschriftung = platz.beschriftung,
                        drehung = a.drehung,
                        groesse = platzGroesse,
                        markierung = markierung(platz),
                        namenZeigen = namenZeigen,
                        fotoStore = fotoStore,
                        modifier = Modifier
                            .offset { IntOffset(links, oben) }
                            .then(platzModifier(platz)),
                    )
                }
            }
        }
        if (!tafelOben) Tafel(breite)
        }
    }
}

/** Basisbreite des Raums bei Zoom 1 – etwa eine Tabletbreite; Handys zoomen automatisch passend. */
val BASIS_BREITE: Dp = 800.dp

@Composable
private fun Tafel(breite: Dp) {
    Box(
        Modifier
            .padding(vertical = 6.dp)
            .width(breite * 0.7f)
            .background(Color(0xFF2F4F3F), RoundedCornerShape(6.dp))
            .padding(vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.sitzplan_tafel), color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun Platz(
    schueler: Schueler?,
    beschriftung: String?,
    drehung: Float,
    groesse: Dp,
    markierung: PlatzMarkierung,
    namenZeigen: Boolean,
    fotoStore: FotoStore,
    modifier: Modifier,
) {
    val rahmen = when (markierung) {
        PlatzMarkierung.KEINE -> MaterialTheme.colorScheme.outlineVariant
        PlatzMarkierung.AUSGEWAEHLT -> MaterialTheme.colorScheme.primary
        PlatzMarkierung.RICHTIG -> Color(0xFF2E7D32)
        PlatzMarkierung.FALSCH -> MaterialTheme.colorScheme.error
    }
    val dicke = if (markierung == PlatzMarkierung.KEINE) 1.dp else 3.dp
    val form = RoundedCornerShape(groesse / 8)
    val zeigeName = namenZeigen && groesse >= 56.dp
    // Inhalt dreht mit dem Tisch, steht aber nie auf dem Kopf: Text bleibt im Bereich −90°…+90°.
    val kopfueber = ((drehung % 360) + 360) % 360 in 90f..270f
    val inhaltDrehung = if (kopfueber) 180f else 0f
    val namenStil = when {
        groesse >= 96.dp -> MaterialTheme.typography.labelLarge
        groesse >= 72.dp -> MaterialTheme.typography.labelMedium
        else -> MaterialTheme.typography.labelSmall
    }
    Box(
        modifier
            .size(groesse)
            .graphicsLayer { rotationZ = drehung }
            .clip(form)
            .background(
                when {
                    schueler != null -> MaterialTheme.colorScheme.surface
                    beschriftung != null -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
            )
            .border(dicke, rahmen, form),
        contentAlignment = Alignment.Center,
    ) {
        // Tischkante: die Seite, in die der Platz „blickt“ (bei 0° oben, Richtung Tafel)
        if (beschriftung == null) Box(Modifier.fillMaxWidth(0.7f).height(3.dp).align(Alignment.TopCenter).offset(y = groesse * 0.06f).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)))
        Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = inhaltDrehung }, contentAlignment = Alignment.Center) {
            if (schueler != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SchuelerFoto(
                        datei = schueler.fotoDatei?.let(fotoStore::datei),
                        beschreibung = schueler.vollerName,
                        modifier = Modifier.size(if (zeigeName) groesse * 0.58f else groesse * 0.78f).clip(RoundedCornerShape(6.dp)),
                    )
                    if (zeigeName) {
                        Box(Modifier.width(groesse - 6.dp).clipToBounds(), contentAlignment = Alignment.Center) {
                            Text(
                                schueler.anzeigeName,
                                style = namenStil,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            } else if (beschriftung != null) {
                Text(
                    beschriftung,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(4.dp),
                )
            } else {
                Box(Modifier.size(groesse * 0.35f).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)))
            }
        }
    }
}
