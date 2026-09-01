package de.namio.feature.sitzplan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import de.namio.R
import de.namio.core.media.FotoStore
import de.namio.core.model.Bestuhlung
import de.namio.core.model.Blickrichtung
import de.namio.core.model.Schueler
import de.namio.core.model.Sitzplan
import de.namio.core.model.Sitzplatz
import de.namio.core.model.Tisch
import de.namio.core.sitzplan.SitzplanLogik
import de.namio.ui.components.SchuelerFoto
import kotlin.math.roundToInt

/** Farbliche Hervorhebung eines Slots (Quiz-Feedback, Auswahl). */
enum class PlatzMarkierung { KEINE, AUSGEWAEHLT, RICHTIG, FALSCH }

/** Basisbreite des Raums bei Zoom 1 – etwa eine Tabletbreite; kleinere Bildschirme scrollen. */
val BASIS_BREITE: Dp = 800.dp

/** Verhältnis Tischtiefe zu Platzbreite: mit Namen tiefer, damit Foto und zwei Namenszeilen Platz haben. */
fun tischTiefe(namenZeigen: Boolean): Float = if (namenZeigen) 1.12f else 0.95f

/**
 * Zeichnet einen Sitzplan: Tische als Rechtecke (Breite = Plätze × Einheit), darauf die Slots.
 * Die Tafel liegt je nach [blickrichtung] oben (von hinten) oder unten (von vorn).
 */
@Composable
fun SitzplanFlaeche(
    plan: Sitzplan,
    bestuhlung: Bestuhlung,
    schuelerProId: Map<Long, Schueler>,
    blickrichtung: Blickrichtung,
    fotoStore: FotoStore,
    modifier: Modifier = Modifier,
    /** Breite des Raums bei Zoom 1; sinnvoll: sichtbare Breite des Containers. */
    basisBreite: Dp = BASIS_BREITE,
    zoom: Float = 1f,
    namenZeigen: Boolean = true,
    rasterZeigen: Boolean = false,
    tischMarkierung: (Tisch) -> Boolean = { false },
    slotMarkierung: (Sitzplatz) -> PlatzMarkierung = { PlatzMarkierung.KEINE },
    flaechenModifier: Modifier = Modifier,
    tischModifier: (Tisch) -> Modifier = { Modifier },
    slotModifier: (Tisch, Sitzplatz) -> Modifier = { _, _ -> Modifier },
) {
    val tafelOben = blickrichtung == Blickrichtung.VON_HINTEN
    BoxWithConstraints(modifier) {
        val basis: Dp = if (maxWidth.value.isFinite() && maxWidth < basisBreite) maxWidth else basisBreite
        val einheit: Dp = (basis / plan.spalten) * zoom
        val breite: Dp = einheit * plan.spalten
        val hoehe: Dp = einheit * plan.reihen
        val dichte = LocalDensity.current
        val raumBeschreibung = stringResource(R.string.sitzplan_raum_beschreibung, plan.spalten, plan.reihen)
        Column(Modifier.width(breite), horizontalAlignment = Alignment.CenterHorizontally) {
            if (tafelOben) Tafel(breite)
            Box(
                Modifier
                    .width(breite)
                    .height(hoehe)
                    .semantics { contentDescription = raumBeschreibung }
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
                bestuhlung.tische.forEach { tisch ->
                    val a = SitzplanLogik.anzeige(tisch, blickrichtung)
                    val tischBreite = einheit * tisch.breite * 0.96f
                    val tischHoehe = einheit * tischTiefe(namenZeigen && !tisch.istMoebel)
                    val links = with(dichte) { (breite * a.x - tischBreite / 2).toPx().roundToInt() }
                    val oben = with(dichte) { (hoehe * a.y - tischHoehe / 2).toPx().roundToInt() }
                    TischKachel(
                        tisch = tisch,
                        anzeigeDrehung = a.drehung,
                        slots = bestuhlung.plaetzeVon(tisch.id),
                        schuelerProId = schuelerProId,
                        breite = tischBreite,
                        hoehe = tischHoehe,
                        einheit = einheit,
                        markiert = tischMarkierung(tisch),
                        slotMarkierung = slotMarkierung,
                        namenZeigen = namenZeigen,
                        fotoStore = fotoStore,
                        slotModifier = slotModifier,
                        modifier = Modifier
                            .offset { IntOffset(links, oben) }
                            .then(tischModifier(tisch)),
                    )
                }
            }
            if (!tafelOben) Tafel(breite)
        }
    }
}

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
private fun TischKachel(
    tisch: Tisch,
    anzeigeDrehung: Float,
    slots: List<Sitzplatz>,
    schuelerProId: Map<Long, Schueler>,
    breite: Dp,
    hoehe: Dp,
    einheit: Dp,
    markiert: Boolean,
    slotMarkierung: (Sitzplatz) -> PlatzMarkierung,
    namenZeigen: Boolean,
    fotoStore: FotoStore,
    slotModifier: (Tisch, Sitzplatz) -> Modifier,
    modifier: Modifier,
) {
    val form = RoundedCornerShape(einheit / 8)
    // Inhalt dreht mit dem Tisch, steht aber nie auf dem Kopf.
    val kopfueber = ((anzeigeDrehung % 360) + 360) % 360 in 90f..270f
    val slotsAnzeige = if (kopfueber) slots.reversed() else slots
    val rahmen = if (markiert) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier
            .size(breite, hoehe)
            .graphicsLayer { rotationZ = anzeigeDrehung }
            .clip(form)
            .background(if (tisch.istMoebel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
            .border(if (markiert) 3.dp else 1.dp, rahmen, form),
    ) {
        if (tisch.istMoebel) {
            Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = if (kopfueber) 180f else 0f }, contentAlignment = Alignment.Center) {
                Text(
                    tisch.beschriftung.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(4.dp),
                )
            }
        } else {
            // Tischkante an der Seite, in die der Tisch „blickt“ (Richtung Tafel bei 0°)
            Box(Modifier.fillMaxWidth(0.9f).height(3.dp).align(Alignment.TopCenter).offset(y = hoehe * 0.05f).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)))
            Row(Modifier.fillMaxSize().graphicsLayer { rotationZ = if (kopfueber) 180f else 0f }, horizontalArrangement = Arrangement.SpaceEvenly) {
                slotsAnzeige.forEach { slot ->
                    Slot(
                        schueler = slot.schuelerId?.let(schuelerProId::get),
                        markierung = slotMarkierung(slot),
                        einheit = einheit,
                        namenZeigen = namenZeigen,
                        fotoStore = fotoStore,
                        modifier = Modifier.width(einheit).fillMaxHeight().then(slotModifier(tisch, slot)),
                    )
                }
            }
        }
    }
}

@Composable
private fun Slot(
    schueler: Schueler?,
    markierung: PlatzMarkierung,
    einheit: Dp,
    namenZeigen: Boolean,
    fotoStore: FotoStore,
    modifier: Modifier,
) {
    val rahmen = when (markierung) {
        PlatzMarkierung.KEINE -> null
        // Tertiär statt Primär: gewähltes KIND muss sich vom blau markierten TISCH unterscheiden
        PlatzMarkierung.AUSGEWAEHLT -> MaterialTheme.colorScheme.tertiary
        PlatzMarkierung.RICHTIG -> Color(0xFF2E7D32)
        PlatzMarkierung.FALSCH -> MaterialTheme.colorScheme.error
    }
    val zeigeName = namenZeigen && einheit >= 56.dp
    val zweizeilig = einheit >= 64.dp && (schueler?.anzeigeName?.contains(' ') == true)
    val namenStil = when {
        einheit >= 120.dp && !zweizeilig -> MaterialTheme.typography.labelLarge
        einheit >= 90.dp -> MaterialTheme.typography.labelMedium
        else -> MaterialTheme.typography.labelSmall
    }
    val form = RoundedCornerShape(einheit / 10)
    Box(
        modifier
            .padding(3.dp)
            .clip(form)
            .then(if (rahmen != null) Modifier.border(3.dp, rahmen, form) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (schueler != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SchuelerFoto(
                    datei = schueler.fotoDatei?.let(fotoStore::datei),
                    beschreibung = schueler.vollerName,
                    modifier = Modifier.size(if (zeigeName) einheit * 0.58f else einheit * 0.75f).clip(RoundedCornerShape(6.dp)),
                )
                if (zeigeName) {
                    Box(Modifier.width(einheit - 10.dp).clipToBounds(), contentAlignment = Alignment.Center) {
                        Text(
                            schueler.anzeigeName,
                            style = namenStil,
                            maxLines = if (zweizeilig) 2 else 1,
                            softWrap = zweizeilig,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            lineHeight = namenStil.fontSize * 1.1f,
                        )
                    }
                }
            }
        } else {
            Box(Modifier.size(einheit * 0.32f).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)))
        }
    }
}
