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
        SitzplatzEntity::class,
    ],
    version = 4,
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

        val ALLE_MIGRATIONEN = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
    }
}
