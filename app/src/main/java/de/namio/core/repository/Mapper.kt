package de.namio.core.repository

import de.namio.core.data.entity.KlasseEntity
import de.namio.core.data.entity.LernkarteEntity
import de.namio.core.data.entity.SchuelerEntity
import de.namio.core.data.entity.SitzplanEntity
import de.namio.core.data.entity.SitzplatzEntity
import de.namio.core.model.Sitzplan
import de.namio.core.model.Sitzplatz
import de.namio.core.data.entity.TischEntity
import de.namio.core.model.Tisch
import de.namio.core.model.Klasse
import de.namio.core.model.Lernkarte
import de.namio.core.model.Schueler
import java.time.Instant

internal fun KlasseEntity.zuModell() = Klasse(
    id = id,
    name = name,
    schule = schule,
    jahrgang = jahrgang,
    erstelltAm = Instant.ofEpochMilli(erstelltAm),
    archiviert = archiviert,
)

internal fun SchuelerEntity.zuModell() = Schueler(
    id = id,
    klasseId = klasseId,
    vorname = vorname,
    nachname = nachname,
    spitzname = spitzname,
    fotoDatei = fotoDatei,
    notiz = notiz,
    sortIndex = sortIndex,
    geschlecht = geschlecht,
)

internal fun Schueler.zuEntity() = SchuelerEntity(
    id = id,
    klasseId = klasseId,
    vorname = vorname,
    nachname = nachname,
    spitzname = spitzname,
    fotoDatei = fotoDatei,
    notiz = notiz,
    sortIndex = sortIndex,
    geschlecht = geschlecht,
)

internal fun LernkarteEntity.zuModell() = Lernkarte(
    id = id,
    schuelerId = schuelerId,
    modus = modus,
    box = box,
    faelligAm = Instant.ofEpochMilli(faelligAm),
    serieRichtig = serieRichtig,
    letzteAntwortAm = letzteAntwortAm?.let(Instant::ofEpochMilli),
)

internal fun Lernkarte.zuEntity() = LernkarteEntity(
    id = id,
    schuelerId = schuelerId,
    modus = modus,
    box = box,
    faelligAm = faelligAm.toEpochMilli(),
    serieRichtig = serieRichtig,
    letzteAntwortAm = letzteAntwortAm?.toEpochMilli(),
)

internal fun SitzplanEntity.zuModell() = Sitzplan(id, klasseId, name, spalten, reihen, istStandard, einrasten)
internal fun SitzplatzEntity.zuModell() = Sitzplatz(id, sitzplanId, tischId, slot, schuelerId)
internal fun TischEntity.zuModell() = Tisch(id, sitzplanId, x, y, drehung, plaetze, beschriftung, breite)
internal fun Tisch.zuEntity() = TischEntity(id.coerceAtLeast(0), sitzplanId, x, y, drehung, plaetze, beschriftung, breite)
internal fun Sitzplatz.zuEntity(tischId: Long = this.tischId) = SitzplatzEntity(id, sitzplanId, tischId, slot, schuelerId)
