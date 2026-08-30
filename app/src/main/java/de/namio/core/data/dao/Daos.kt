package de.namio.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import de.namio.core.data.entity.KlasseEntity
import de.namio.core.data.entity.KlasseMitZahlen
import de.namio.core.data.entity.LernkarteEntity
import de.namio.core.data.entity.QuizAntwortEntity
import de.namio.core.data.entity.QuizSessionEntity
import de.namio.core.data.entity.SchuelerEntity
import de.namio.core.data.entity.SitzplanEntity
import de.namio.core.data.entity.SitzplatzEntity
import de.namio.core.data.entity.TischEntity
import de.namio.core.model.QuizModus
import kotlinx.coroutines.flow.Flow

@Dao
interface KlasseDao {
    @Query(
        """
        SELECT k.*,
            (SELECT COUNT(*) FROM schueler s WHERE s.klasseId = k.id) AS schuelerAnzahl,
            (SELECT COALESCE(SUM(l.box), 0) FROM lernkarte l
                JOIN schueler s ON s.id = l.schuelerId
                WHERE s.klasseId = k.id AND l.modus = :modus) AS boxSumme
        FROM klasse k
        WHERE k.archiviert = 0
        ORDER BY k.name COLLATE NOCASE
        """,
    )
    fun observeUebersicht(modus: QuizModus): Flow<List<KlasseMitZahlen>>

    @Query("SELECT * FROM klasse WHERE id = :id")
    fun observe(id: Long): Flow<KlasseEntity?>

    @Insert
    suspend fun insert(klasse: KlasseEntity): Long

    @Update
    suspend fun update(klasse: KlasseEntity)

    @Query("DELETE FROM klasse WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface SchuelerDao {
    @Query("SELECT * FROM schueler WHERE klasseId = :klasseId ORDER BY sortIndex, nachname COLLATE NOCASE, vorname COLLATE NOCASE")
    fun observeFuerKlasse(klasseId: Long): Flow<List<SchuelerEntity>>

    @Query("SELECT * FROM schueler WHERE klasseId = :klasseId")
    suspend fun fuerKlasse(klasseId: Long): List<SchuelerEntity>

    @Query("SELECT * FROM schueler WHERE id = :id")
    fun observe(id: Long): Flow<SchuelerEntity?>

    @Query("SELECT * FROM schueler WHERE id = :id")
    suspend fun get(id: Long): SchuelerEntity?

    @Query("SELECT fotoDatei FROM schueler WHERE klasseId = :klasseId AND fotoDatei IS NOT NULL")
    suspend fun fotoDateienFuerKlasse(klasseId: Long): List<String>

    @Query("SELECT COALESCE(MAX(sortIndex), -1) + 1 FROM schueler WHERE klasseId = :klasseId")
    suspend fun naechsterSortIndex(klasseId: Long): Int

    @Insert
    suspend fun insert(schueler: SchuelerEntity): Long

    @Update
    suspend fun update(schueler: SchuelerEntity)

    @Query("UPDATE schueler SET fotoDatei = :fotoDatei WHERE id = :id")
    suspend fun setzeFoto(id: Long, fotoDatei: String?)

    @Query("DELETE FROM schueler WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface LernkarteDao {
    @Query("SELECT * FROM lernkarte WHERE schuelerId = :schuelerId")
    fun observeFuerSchueler(schuelerId: Long): Flow<List<LernkarteEntity>>

    @Query(
        """
        SELECT l.* FROM lernkarte l JOIN schueler s ON s.id = l.schuelerId
        WHERE s.klasseId = :klasseId AND l.modus = :modus
        """,
    )
    suspend fun fuerKlasseUndModus(klasseId: Long, modus: QuizModus): List<LernkarteEntity>

    @Query(
        """
        SELECT l.* FROM lernkarte l JOIN schueler s ON s.id = l.schuelerId
        WHERE s.klasseId = :klasseId
        """,
    )
    fun observeFuerKlasse(klasseId: Long): Flow<List<LernkarteEntity>>

    @Query("SELECT * FROM lernkarte WHERE schuelerId = :schuelerId AND modus = :modus")
    suspend fun get(schuelerId: Long, modus: QuizModus): LernkarteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(karte: LernkarteEntity): Long

    @Update
    suspend fun update(karte: LernkarteEntity)
}

@Dao
interface QuizSessionDao {
    @Insert
    suspend fun insert(session: QuizSessionEntity): Long

    @Update
    suspend fun update(session: QuizSessionEntity)

    @Query("SELECT * FROM quiz_session WHERE klasseId = :klasseId ORDER BY startedAt DESC")
    fun observeFuerKlasse(klasseId: Long): Flow<List<QuizSessionEntity>>
}

@Dao
interface QuizAntwortDao {
    @Insert
    suspend fun insert(antwort: QuizAntwortEntity): Long

    /** Verwechslungen eines Schülers, häufigste zuerst – Basis der Ablenkerauswahl. */
    @Query(
        """
        SELECT verwechseltMit FROM quiz_antwort
        WHERE schuelerId = :schuelerId AND verwechseltMit IS NOT NULL
        GROUP BY verwechseltMit ORDER BY COUNT(*) DESC
        """,
    )
    suspend fun verwechslungenFuer(schuelerId: Long): List<Long>
}

@Dao
interface SitzplanDao {
    @Query("SELECT * FROM sitzplan WHERE klasseId = :klasseId ORDER BY istStandard DESC, name COLLATE NOCASE")
    fun observeFuerKlasse(klasseId: Long): Flow<List<SitzplanEntity>>

    @Query("SELECT * FROM sitzplan WHERE id = :id")
    suspend fun get(id: Long): SitzplanEntity?

    @Query("SELECT * FROM sitzplan WHERE klasseId = :klasseId ORDER BY istStandard DESC, id LIMIT 1")
    suspend fun standardFuerKlasse(klasseId: Long): SitzplanEntity?

    @Query("SELECT COUNT(*) FROM sitzplan WHERE klasseId = :klasseId")
    suspend fun anzahlFuerKlasse(klasseId: Long): Int

    @Query("UPDATE sitzplan SET istStandard = (id = :planId) WHERE klasseId = :klasseId")
    suspend fun setzeStandard(klasseId: Long, planId: Long)

    @Insert
    suspend fun insert(sitzplan: SitzplanEntity): Long

    @Update
    suspend fun update(sitzplan: SitzplanEntity)

    @Query("DELETE FROM sitzplan WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface TischDao {
    @Query("SELECT * FROM tisch WHERE sitzplanId = :sitzplanId")
    fun observeFuerPlan(sitzplanId: Long): Flow<List<TischEntity>>

    @Query("SELECT * FROM tisch WHERE sitzplanId = :sitzplanId")
    suspend fun fuerPlan(sitzplanId: Long): List<TischEntity>

    @Insert
    suspend fun insert(tisch: TischEntity): Long

    @Query("DELETE FROM tisch WHERE sitzplanId = :sitzplanId")
    suspend fun loescheAlleFuerPlan(sitzplanId: Long)
}

@Dao
interface SitzplatzDao {
    @Query("SELECT * FROM sitzplatz WHERE sitzplanId = :sitzplanId")
    fun observeFuerPlan(sitzplanId: Long): Flow<List<SitzplatzEntity>>

    @Query("SELECT * FROM sitzplatz WHERE sitzplanId = :sitzplanId")
    suspend fun fuerPlan(sitzplanId: Long): List<SitzplatzEntity>

    /** Belegte Plätze des Standardplans einer Klasse – Kandidaten für den Sitzplan-Quizmodus. */
    @Query(
        """
        SELECT p.schuelerId FROM sitzplatz p JOIN sitzplan s ON s.id = p.sitzplanId
        WHERE s.klasseId = :klasseId AND s.istStandard = 1 AND p.schuelerId IS NOT NULL
        """,
    )
    fun observeSchuelerImStandardplan(klasseId: Long): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(platz: SitzplatzEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlle(plaetze: List<SitzplatzEntity>)

    @Query("DELETE FROM sitzplatz WHERE sitzplanId = :sitzplanId")
    suspend fun loescheAlleFuerPlan(sitzplanId: Long)

    @Insert
    suspend fun insert(platz: SitzplatzEntity): Long
}
