package de.namio.core.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
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

    /** App-Sperre (Biometrie/Geräte-PIN beim Start), standardmäßig an. */
    val appSperre: Flow<Boolean> = dataStore.data.map { it[APP_SPERRE] ?: true }
    suspend fun setzeAppSperre(an: Boolean) { dataStore.edit { it[APP_SPERRE] = an } }

    /** Neue Karten pro Quizrunde, Standard 5. */
    val neueKartenProRunde: Flow<Int> = dataStore.data.map { (it[NEUE_KARTEN] ?: 5).coerceIn(1, 15) }
    suspend fun setzeNeueKartenProRunde(n: Int) { dataStore.edit { it[NEUE_KARTEN] = n.coerceIn(1, 15) } }

    private companion object {
        val BLICKRICHTUNG = stringPreferencesKey("blickrichtung")
        val APP_SPERRE = booleanPreferencesKey("app_sperre")
        val NEUE_KARTEN = intPreferencesKey("neue_karten_pro_runde")
    }
}
