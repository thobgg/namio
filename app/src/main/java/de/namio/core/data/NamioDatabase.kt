package de.namio.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import de.namio.core.data.dao.KlasseDao
import de.namio.core.data.dao.LernkarteDao
import de.namio.core.data.dao.QuizAntwortDao
import de.namio.core.data.dao.QuizSessionDao
import de.namio.core.data.dao.SchuelerDao
import de.namio.core.data.dao.SitzplanDao
import de.namio.core.data.dao.SitzplatzDao
import de.namio.core.data.entity.KlasseEntity
import de.namio.core.data.entity.LernkarteEntity
import de.namio.core.data.entity.QuizAntwortEntity
import de.namio.core.data.entity.QuizSessionEntity
import de.namio.core.data.entity.SchuelerEntity
import de.namio.core.data.entity.SitzplanEntity
import de.namio.core.data.entity.SitzplatzEntity
import de.namio.core.data.entity.TischEntity
import de.namio.core.data.dao.TischDao
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Die verschlüsselte Datenbank. Ab Version 1 ausschließlich explizite Migrationen –
 * `fallbackToDestructiveMigration` ist verboten.
 */
@Database(
    entities = [
        KlasseEntity::class,
        SchuelerEntity::class,
        LernkarteEntity::class,
        QuizSessionEntity::class,
        QuizAntwortEntity::class,
        SitzplanEntity::class,
        TischEntity::class,
        SitzplatzEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class NamioDatabase : RoomDatabase() {
    abstract fun klasseDao(): KlasseDao
    abstract fun schuelerDao(): SchuelerDao
    abstract fun lernkarteDao(): LernkarteDao
    abstract fun quizSessionDao(): QuizSessionDao
    abstract fun quizAntwortDao(): QuizAntwortDao
    abstract fun sitzplanDao(): SitzplanDao
    abstract fun sitzplatzDao(): SitzplatzDao
    abstract fun tischDao(): TischDao

    companion object {
        /** v2: Geschlecht am Schüler. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE schueler ADD COLUMN geschlecht TEXT NOT NULL DEFAULT 'KEINE_ANGABE'")
            }
        }

        /**
         * v3: Geschlecht nur noch Mädchen/Junge. SQLite kann Spalten-Defaults nicht ändern,
         * deshalb wird die Tabelle neu aufgebaut; bisherige „KEINE_ANGABE“ werden zu Mädchen.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE schueler_neu (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        klasseId INTEGER NOT NULL,
                        vorname TEXT NOT NULL,
                        nachname TEXT NOT NULL,
                        spitzname TEXT NOT NULL,
                        fotoDatei TEXT,
                        notiz TEXT NOT NULL,
                        sortIndex INTEGER NOT NULL,
                        geschlecht TEXT NOT NULL DEFAULT 'MAEDCHEN',
                        FOREIGN KEY(klasseId) REFERENCES klasse(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO schueler_neu (id, klasseId, vorname, nachname, spitzname, fotoDatei, notiz, sortIndex, geschlecht)
                    SELECT id, klasseId, vorname, nachname, spitzname, fotoDatei, notiz, sortIndex,
                        CASE geschlecht WHEN 'JUNGE' THEN 'JUNGE' ELSE 'MAEDCHEN' END
                    FROM schueler
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE schueler")
                db.execSQL("ALTER TABLE schueler_neu RENAME TO schueler")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_schueler_klasseId ON schueler(klasseId)")
            }
        }

        /** v4: Doppeltisch-Darstellung am Sitzplan. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sitzplan ADD COLUMN doppeltische INTEGER NOT NULL DEFAULT 1")
            }
        }

        /**
         * v5: frei positionierbare, drehbare Plätze. Rasterkoordinaten werden in normierte
         * Mittelpunkte umgerechnet; die Doppeltisch-Option weicht dem Einrasten.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE sitzplan_neu (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        klasseId INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        spalten INTEGER NOT NULL,
                        reihen INTEGER NOT NULL,
                        istStandard INTEGER NOT NULL,
                        einrasten INTEGER NOT NULL DEFAULT 1,
                        FOREIGN KEY(klasseId) REFERENCES klasse(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO sitzplan_neu (id, klasseId, name, spalten, reihen, istStandard, einrasten)
                    SELECT id, klasseId, name, MAX(spalten, 4), MAX(reihen, 3), istStandard, 1 FROM sitzplan
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE sitzplatz_neu (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sitzplanId INTEGER NOT NULL,
                        schuelerId INTEGER,
                        x REAL NOT NULL,
                        y REAL NOT NULL,
                        drehung REAL NOT NULL DEFAULT 0,
                        FOREIGN KEY(sitzplanId) REFERENCES sitzplan(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(schuelerId) REFERENCES schueler(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO sitzplatz_neu (id, sitzplanId, schuelerId, x, y, drehung)
                    SELECT p.id, p.sitzplanId, p.schuelerId,
                        (p.spalte + 0.5) / MAX(s.spalten, 4), (p.reihe + 0.5) / MAX(s.reihen, 3), 0
                    FROM sitzplatz p JOIN sitzplan s ON s.id = p.sitzplanId
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE sitzplatz")
                db.execSQL("DROP TABLE sitzplan")
                db.execSQL("ALTER TABLE sitzplan_neu RENAME TO sitzplan")
                db.execSQL("ALTER TABLE sitzplatz_neu RENAME TO sitzplatz")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sitzplan_klasseId ON sitzplan(klasseId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sitzplatz_sitzplanId_schuelerId ON sitzplatz(sitzplanId, schuelerId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sitzplatz_sitzplanId ON sitzplatz(sitzplanId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sitzplatz_schuelerId ON sitzplatz(schuelerId)")
            }
        }

        /** v6: Beschriftung für Möbel im Sitzplan (Pult, PC …). */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sitzplatz ADD COLUMN beschriftung TEXT")
            }
        }

        /**
         * v7: Tische als eigene Objekte, Sitzplätze als Slots darauf. Jeder alte Platz wird ein
         * Einzeltisch; Nachbarn mit gleicher Drehung im Abstand einer Einheit verschmelzen zu
         * Doppeltischen. Möbel (Beschriftung) werden Tische ohne Plätze.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE tisch (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sitzplanId INTEGER NOT NULL,
                        x REAL NOT NULL, y REAL NOT NULL,
                        drehung REAL NOT NULL DEFAULT 0,
                        plaetze INTEGER NOT NULL DEFAULT 1,
                        beschriftung TEXT,
                        FOREIGN KEY(sitzplanId) REFERENCES sitzplan(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tisch_sitzplanId ON tisch(sitzplanId)")
                db.execSQL(
                    """
                    CREATE TABLE sitzplatz_neu (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sitzplanId INTEGER NOT NULL,
                        tischId INTEGER NOT NULL,
                        slot INTEGER NOT NULL,
                        schuelerId INTEGER,
                        FOREIGN KEY(sitzplanId) REFERENCES sitzplan(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(tischId) REFERENCES tisch(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(schuelerId) REFERENCES schueler(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                data class Alt(val id: Long, val plan: Long, val schueler: Long?, val x: Float, val y: Float, val d: Float, val text: String?)
                val alte = mutableListOf<Alt>()
                db.query("SELECT id, sitzplanId, schuelerId, x, y, drehung, beschriftung FROM sitzplatz").use { c ->
                    while (c.moveToNext()) {
                        alte += Alt(c.getLong(0), c.getLong(1), if (c.isNull(2)) null else c.getLong(2), c.getFloat(3), c.getFloat(4), c.getFloat(5), if (c.isNull(6)) null else c.getString(6))
                    }
                }
                val raum = mutableMapOf<Long, Pair<Int, Int>>()
                db.query("SELECT id, spalten, reihen FROM sitzplan").use { c ->
                    while (c.moveToNext()) raum[c.getLong(0)] = c.getInt(1) to c.getInt(2)
                }
                val verbraucht = mutableSetOf<Long>()
                fun tischAnlegen(plan: Long, x: Float, y: Float, d: Float, plaetze: Int, text: String?): Long {
                    db.execSQL("INSERT INTO tisch (sitzplanId, x, y, drehung, plaetze, beschriftung) VALUES (?, ?, ?, ?, ?, ?)", arrayOf(plan, x, y, d, plaetze, text))
                    return db.query("SELECT last_insert_rowid()").use { it.moveToFirst(); it.getLong(0) }
                }
                fun platzAnlegen(plan: Long, tisch: Long, slot: Int, schueler: Long?) {
                    db.execSQL("INSERT INTO sitzplatz_neu (sitzplanId, tischId, slot, schuelerId) VALUES (?, ?, ?, ?)", arrayOf(plan, tisch, slot, schueler))
                }
                for (a in alte) {
                    if (a.id in verbraucht) continue
                    verbraucht += a.id
                    if (a.text != null) { tischAnlegen(a.plan, a.x, a.y, a.d, 0, a.text); continue }
                    val (sp, re) = raum[a.plan] ?: (12 to 9)
                    val rad = Math.toRadians(a.d.toDouble())
                    val ex = (cos(rad) / sp).toFloat()
                    val ey = (sin(rad) / re).toFloat()
                    // Partner rechts (in Tischrichtung) im Abstand einer Einheit?
                    val partner = alte.firstOrNull { b ->
                        b.id !in verbraucht && b.plan == a.plan && b.text == null &&
                            abs(((b.d - a.d) % 360 + 360) % 360).let { it < 1f || it > 359f } &&
                            hypot(((b.x - (a.x + ex)) * sp).toDouble(), ((b.y - (a.y + ey)) * re).toDouble()) < 0.3
                    }
                    if (partner != null) {
                        verbraucht += partner.id
                        val t = tischAnlegen(a.plan, (a.x + partner.x) / 2, (a.y + partner.y) / 2, a.d, 2, null)
                        platzAnlegen(a.plan, t, 0, a.schueler)
                        platzAnlegen(a.plan, t, 1, partner.schueler)
                    } else {
                        val t = tischAnlegen(a.plan, a.x, a.y, a.d, 1, null)
                        platzAnlegen(a.plan, t, 0, a.schueler)
                    }
                }
                db.execSQL("DROP TABLE sitzplatz")
                db.execSQL("ALTER TABLE sitzplatz_neu RENAME TO sitzplatz")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sitzplatz_sitzplanId_schuelerId ON sitzplatz(sitzplanId, schuelerId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sitzplatz_tischId_slot ON sitzplatz(tischId, slot)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sitzplatz_sitzplanId ON sitzplatz(sitzplanId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sitzplatz_schuelerId ON sitzplatz(schuelerId)")
            }
        }

        /** v8: Tischbreite unabhängig von der Platzzahl. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tisch ADD COLUMN breite REAL NOT NULL DEFAULT 1")
                db.execSQL("UPDATE tisch SET breite = MAX(plaetze, 1)")
            }
        }

        val ALLE_MIGRATIONEN = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
    }
}
