package de.namio.core.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import de.namio.core.model.Blickrichtung
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** App-Einstellungen aus dem DataStore. */
@Singleton
class EinstellungenRepository @Inject constructor(private val dataStore: DataStore<Preferences>) {

    /** Blickrichtung für Sitzpläne, Standard: von vorn (wie der Lehrer die Klasse sieht). */
    val blickrichtung: Flow<Blickrichtung> = dataStore.data.map { prefs ->
        prefs[BLICKRICHTUNG]?.let { runCatching { Blickrichtung.valueOf(it) }.getOrNull() } ?: Blickrichtung.VON_VORN
    }

    suspend fun setzeBlickrichtung(richtung: Blickrichtung) {
        dataStore.edit { it[BLICKRICHTUNG] = richtung.name }
    }

    private companion object {
        val BLICKRICHTUNG = stringPreferencesKey("blickrichtung")
    }
}
