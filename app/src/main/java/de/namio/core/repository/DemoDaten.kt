package de.namio.core.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import de.namio.core.data.dao.SchuelerDao
import de.namio.core.data.entity.SchuelerEntity
import de.namio.core.media.FotoStore
import de.namio.core.model.Geschlecht
import de.namio.core.model.Geschlecht.JUNGE
import de.namio.core.model.Geschlecht.MAEDCHEN
import de.namio.core.model.SitzplanVorlage
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Legt beim allerersten Start eine Demoklasse mit 24 Avataren an, damit die App nicht leer
 * startet. Wird sie gelöscht, kommt sie nicht wieder – das merkt sich ein DataStore-Flag.
 */
@Singleton
class DemoDaten @Inject constructor(
    private val klassenRepository: KlassenRepository,
    private val schuelerDao: SchuelerDao,
    private val fotoStore: FotoStore,
    private val sitzplanRepository: SitzplanRepository,
    private val dataStore: DataStore<Preferences>,
) {
    private data class Demo(val vorname: String, val nachname: String, val geschlecht: Geschlecht, val avatar: Int)

    private val schueler = listOf(
        Demo("Lena", "Meier", MAEDCHEN, 15),
        Demo("Ben", "Schulz", JUNGE, 2),
        Demo("Sophie", "Kaiser", MAEDCHEN, 3),
        Demo("Anna", "Berg", MAEDCHEN, 8),
        Demo("Emil", "Roth", JUNGE, 4),
        Demo("Marita", "Burger", MAEDCHEN, 13),
        Demo("Paul", "Winter", JUNGE, 6),
        Demo("Jonas", "Fischer", JUNGE, 7),
        Demo("Laura", "Schmidt", MAEDCHEN, 12),
        Demo("Finn", "Weber", JUNGE, 18),
        Demo("Clara", "Hoffmann", MAEDCHEN, 19),
        Demo("Lukas", "Braun", JUNGE, 20),
        Demo("Mia", "Wagner", MAEDCHEN, 24),
        Demo("Tim", "Krüger", JUNGE, 30),
        Demo("Emma", "Neumann", MAEDCHEN, 32),
        Demo("Noah", "Lange", JUNGE, 33),
        Demo("Hannah", "Koch", MAEDCHEN, 1),
        Demo("Leon", "Schäfer", JUNGE, 9),
        Demo("Lea", "Vogel", MAEDCHEN, 10),
        Demo("Max", "Richter", JUNGE, 11),
        Demo("Julia", "Klein", MAEDCHEN, 17),
        Demo("Felix", "Wolf", JUNGE, 23),
        Demo("Nele", "Schröder", MAEDCHEN, 27),
        Demo("Moritz", "Hartmann", JUNGE, 25),
    )

    /** Legt die Demoklasse an, falls das noch nie passiert ist. Mehrfachaufruf ist harmlos. */
    suspend fun anlegenFallsErsterStart() {
        val prefs = dataStore.data.first()
        if (prefs[DEMO_ANGELEGT] == true) return
        val klasseId = klassenRepository.anlegen(name = "7b", schule = "Demoklasse", jahrgang = "")
        val ids = mutableListOf<Long>()
        schueler.forEachIndexed { index, demo ->
            val foto = runCatching { fotoStore.speichereAvatar("avatar_%02d.jpg".format(demo.avatar)) }.getOrNull()
            ids += schuelerDao.insert(
                SchuelerEntity(
                    klasseId = klasseId,
                    vorname = demo.vorname,
                    nachname = demo.nachname,
                    fotoDatei = foto,
                    sortIndex = index,
                    geschlecht = demo.geschlecht,
                ),
            )
        }
        sitzplanRepository.anlegen(
            klasseId = klasseId,
            name = "Klassenraum",
            spalten = SitzplanRepository.STANDARD_SPALTEN,
            reihen = SitzplanRepository.STANDARD_REIHEN,
            vorlage = SitzplanVorlage.DOPPELTISCH_REIHEN,
            schuelerIds = ids,
        )
        dataStore.edit { it[DEMO_ANGELEGT] = true }
    }

    private companion object {
        val DEMO_ANGELEGT = booleanPreferencesKey("demo_angelegt")
    }
}
