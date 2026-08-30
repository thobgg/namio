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
    val spalten: Int,
    val reihen: Int,
    val istStandard: Boolean = false,
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
            entity = SchuelerEntity::class,
            parentColumns = ["id"],
            childColumns = ["schuelerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["sitzplanId", "spalte", "reihe"], unique = true),
        Index(value = ["sitzplanId", "schuelerId"], unique = true),
        Index("schuelerId"),
    ],
)
data class SitzplatzEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sitzplanId: Long,
    /** null = leerer Stuhl. */
    val schuelerId: Long? = null,
    val spalte: Int,
    val reihe: Int,
)

/** Klasse mit Kennzahlen für die Liste (Abfrageergebnis, keine Tabelle). */
data class KlasseMitZahlen(
    @androidx.room.Embedded val klasse: KlasseEntity,
    @ColumnInfo(name = "schuelerAnzahl") val schuelerAnzahl: Int,
    @ColumnInfo(name = "boxSumme") val boxSumme: Int,
)
