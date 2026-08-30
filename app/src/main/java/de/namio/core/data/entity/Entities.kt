package de.namio.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import de.namio.core.model.Geschlecht
import de.namio.core.model.QuizModus

@Entity(tableName = "klasse")
data class KlasseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val schule: String,
    val jahrgang: String,
    val erstelltAm: Long,
    val archiviert: Boolean = false,
)

@Entity(
    tableName = "schueler",
    foreignKeys = [
        ForeignKey(
            entity = KlasseEntity::class,
            parentColumns = ["id"],
            childColumns = ["klasseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("klasseId")],
)
data class SchuelerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val klasseId: Long,
    val vorname: String,
    val nachname: String,
    val spitzname: String = "",
    /** Nur der Dateiname, nie ein absoluter Pfad. */
    val fotoDatei: String? = null,
    val notiz: String = "",
    val sortIndex: Int = 0,
    @ColumnInfo(defaultValue = "MAEDCHEN") val geschlecht: Geschlecht = Geschlecht.MAEDCHEN,
)

@Entity(
    tableName = "lernkarte",
    foreignKeys = [
        ForeignKey(
            entity = SchuelerEntity::class,
            parentColumns = ["id"],
            childColumns = ["schuelerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["schuelerId", "modus"], unique = true)],
)
data class LernkarteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val schuelerId: Long,
    val modus: QuizModus,
    val box: Int = 1,
    val faelligAm: Long,
    val serieRichtig: Int = 0,
    val letzteAntwortAm: Long? = null,
)

@Entity(
    tableName = "quiz_session",
    foreignKeys = [
        ForeignKey(
            entity = KlasseEntity::class,
            parentColumns = ["id"],
            childColumns = ["klasseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("klasseId")],
)
data class QuizSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val klasseId: Long,
    val modus: QuizModus,
    val startedAt: Long,
    val endedAt: Long? = null,
    val anzahlRichtig: Int = 0,
    val anzahlFalsch: Int = 0,
)

@Entity(
    tableName = "quiz_antwort",
    foreignKeys = [
        ForeignKey(
            entity = QuizSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SchuelerEntity::class,
            parentColumns = ["id"],
            childColumns = ["schuelerId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SchuelerEntity::class,
            parentColumns = ["id"],
            childColumns = ["verwechseltMit"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("schuelerId"), Index("verwechseltMit")],
)
data class QuizAntwortEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val schuelerId: Long,
    /** Wen der Nutzer stattdessen getippt hat – Grundlage der Ablenkerauswahl. */
    val verwechseltMit: Long? = null,
    val korrekt: Boolean,
    val dauerMs: Long,
    val zeitpunkt: Long,
)

@Entity(
    tableName = "sitzplan",
    foreignKeys = [
        ForeignKey(
            entity = KlasseEntity::class,
            parentColumns = ["id"],
            childColumns = ["klasseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("klasseId")],
)
data class SitzplanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val klasseId: Long,
    val name: String,
    /** Raumbreite in Rastereinheiten (eine Einheit = ein Sitzplatz). */
    val spalten: Int,
    /** Raumtiefe in Rastereinheiten. */
    val reihen: Int,
    val istStandard: Boolean = false,
    /** Plätze beim Verschieben am halben Raster einrasten. */
    @ColumnInfo(defaultValue = "1") val einrasten: Boolean = true,
)

@Entity(
    tableName = "tisch",
    foreignKeys = [
        ForeignKey(
            entity = SitzplanEntity::class,
            parentColumns = ["id"],
            childColumns = ["sitzplanId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sitzplanId")],
)
data class TischEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sitzplanId: Long,
    /** Mittelpunkt, normiert 0–1 auf Raumbreite/-tiefe. */
    val x: Float,
    val y: Float,
    /** Drehung in Grad, im Uhrzeigersinn. */
    val drehung: Float = 0f,
    /** Anzahl Sitzplätze nebeneinander; 0 = Möbel (Pult, PC …). */
    val plaetze: Int = 1,
    val beschriftung: String? = null,
    /** Breite in Platzbreiten (z. B. Zweiertisch mit einem Kind = 2). */
    @ColumnInfo(defaultValue = "1") val breite: Float = 1f,
)

@Entity(
    tableName = "sitzplatz",
    foreignKeys = [
        ForeignKey(
            entity = SitzplanEntity::class,
            parentColumns = ["id"],
            childColumns = ["sitzplanId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TischEntity::class,
            parentColumns = ["id"],
            childColumns = ["tischId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SchuelerEntity::class,
            parentColumns = ["id"],
            childColumns = ["schuelerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["sitzplanId", "schuelerId"], unique = true),
        Index(value = ["tischId", "slot"], unique = true),
        Index("sitzplanId"),
        Index("schuelerId"),
    ],
)
data class SitzplatzEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sitzplanId: Long,
    val tischId: Long,
    /** Position auf dem Tisch, 0 = links (in Tischrichtung). */
    val slot: Int,
    /** null = leerer Stuhl. */
    val schuelerId: Long? = null,
)

/** Klasse mit Kennzahlen für die Liste (Abfrageergebnis, keine Tabelle). */
data class KlasseMitZahlen(
    @androidx.room.Embedded val klasse: KlasseEntity,
    @ColumnInfo(name = "schuelerAnzahl") val schuelerAnzahl: Int,
    @ColumnInfo(name = "boxSumme") val boxSumme: Int,
)
