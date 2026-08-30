package de.namio.feature.sitzplan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.namio.R
import de.namio.core.media.FotoStore
import de.namio.core.model.Blickrichtung
import de.namio.core.model.Position
import de.namio.core.model.Schueler
import de.namio.core.model.Sitzplan
import de.namio.core.model.Sitzplatz
import de.namio.core.sitzplan.SitzplanLogik
import de.namio.ui.components.SchuelerFoto

val PLATZ_GROESSE: Dp = 84.dp
val PLATZ_ABSTAND: Dp = 6.dp
val GANG_BREITE: Dp = 22.dp

/**
 * Liegt in der Anzeige nach Spalte [anzeigeSpalte] ein Gang? Bei Doppeltischen trennt ein Gang
 * die Modellspalten (1|2), (3|4), … – bei gedrehter Ansicht entsprechend gespiegelt.
 */
internal fun gangNach(anzeigeSpalte: Int, spalten: Int, doppeltische: Boolean, blickrichtung: Blickrichtung): Boolean {
    if (!doppeltische || anzeigeSpalte >= spalten - 1) return false
    val modell = if (blickrichtung == Blickrichtung.VON_HINTEN) anzeigeSpalte else spalten - 1 - anzeigeSpalte
    // Gang nach Modellspalte m, wenn m ungerade (rechts vom Doppeltisch); gedreht: Gang vor m, wenn m gerade
    return if (blickrichtung == Blickrichtung.VON_HINTEN) modell % 2 == 1 else modell % 2 == 0
}

/** Farbliche Hervorhebung eines Platzes (Quiz-Feedback, Auswahl). */
enum class PlatzMarkierung { KEINE, AUSGEWAEHLT, RICHTIG, FALSCH }

/**
 * Rendert einen Sitzplan als Raster. [inhalt] entscheidet pro Modellposition, was in der Zelle
 * steht. Ab mehr als 8 Spalten wird horizontal gescrollt statt die Kacheln zu verkleinern.
 * Die Tafel wird je nach [blickrichtung] oben (von hinten) oder unten (von vorn) gezeichnet.
 */
@Composable
fun SitzplanRaster(
    plan: Sitzplan,
    plaetze: List<Sitzplatz>,
    schuelerProId: Map<Long, Schueler>,
    blickrichtung: Blickrichtung,
    fotoStore: FotoStore,
    modifier: Modifier = Modifier,
    markierung: (Sitzplatz) -> PlatzMarkierung = { PlatzMarkierung.KEINE },
    namenZeigen: Boolean = true,
    onZellePositioniert: ((Position, Rect) -> Unit)? = null,
    zellenModifier: (Position, Sitzplatz?) -> Modifier = { _, _ -> Modifier },
) {
    val platzProPosition = plaetze.associateBy { Position(it.spalte, it.reihe) }
    val tafelOben = blickrichtung == Blickrichtung.VON_HINTEN
    Column(modifier.horizontalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        val gaenge = (0 until plan.spalten - 1).count { gangNach(it, plan.spalten, plan.doppeltische, blickrichtung) }
        if (tafelOben) Tafel(plan.spalten, gaenge)
        for (anzeigeReihe in 0 until plan.reihen) {
            Row(horizontalArrangement = Arrangement.spacedBy(PLATZ_ABSTAND), modifier = Modifier.padding(vertical = PLATZ_ABSTAND / 2)) {
                for (anzeigeSpalte in 0 until plan.spalten) {
                    if (anzeigeSpalte > 0 && gangNach(anzeigeSpalte - 1, plan.spalten, plan.doppeltische, blickrichtung)) {
                        Spacer(Modifier.width(GANG_BREITE))
                    }
                    val modell = SitzplanLogik.modellPosition(Position(anzeigeSpalte, anzeigeReihe), plan.spalten, plan.reihen, blickrichtung)
                    val platz = platzProPosition[modell]
                    val schueler = platz?.schuelerId?.let(schuelerProId::get)
                    Zelle(
                        schueler = schueler,
                        leererStuhl = platz != null && platz.schuelerId == null,
                        markierung = platz?.let(markierung) ?: PlatzMarkierung.KEINE,
                        namenZeigen = namenZeigen,
                        fotoStore = fotoStore,
                        modifier = Modifier
                            .then(
                                if (onZellePositioniert != null) {
                                    Modifier.onGloballyPositioned { onZellePositioniert(modell, it.boundsInRoot()) }
                                } else {
                                    Modifier
                                },
                            )
                            .then(zellenModifier(modell, platz)),
                    )
                }
            }
        }
        if (!tafelOben) Tafel(plan.spalten, gaenge)
    }
}

@Composable
private fun Tafel(spalten: Int, gaenge: Int) {
    Box(
        Modifier
            .padding(vertical = 8.dp)
            .width((PLATZ_GROESSE + PLATZ_ABSTAND) * spalten - PLATZ_ABSTAND + GANG_BREITE * gaenge)
            .background(Color(0xFF2F4F3F), RoundedCornerShape(6.dp))
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.sitzplan_tafel), color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun Zelle(
    schueler: Schueler?,
    leererStuhl: Boolean,
    markierung: PlatzMarkierung,
    namenZeigen: Boolean,
    fotoStore: FotoStore,
    modifier: Modifier,
) {
    val rahmen = when (markierung) {
        PlatzMarkierung.KEINE -> null
        PlatzMarkierung.AUSGEWAEHLT -> MaterialTheme.colorScheme.primary
        PlatzMarkierung.RICHTIG -> Color(0xFF2E7D32)
        PlatzMarkierung.FALSCH -> MaterialTheme.colorScheme.error
    }
    val form = RoundedCornerShape(10.dp)
    Box(
        modifier
            .size(PLATZ_GROESSE)
            .clip(form)
            .background(
                when {
                    schueler != null -> MaterialTheme.colorScheme.surface
                    leererStuhl -> MaterialTheme.colorScheme.surfaceVariant
                    else -> Color.Transparent
                },
            )
            .then(if (rahmen != null) Modifier.border(3.dp, rahmen, form) else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, form)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            schueler != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SchuelerFoto(
                    datei = schueler.fotoDatei?.let(fotoStore::datei),
                    beschreibung = schueler.vollerName,
                    modifier = Modifier.size(if (namenZeigen) 54.dp else 72.dp).clip(RoundedCornerShape(8.dp)),
                )
                if (namenZeigen) {
                    Text(
                        schueler.anzeigeName,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp).fillMaxWidth(),
                    )
                }
            }
            leererStuhl -> Text(stringResource(R.string.sitzplan_leer), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
