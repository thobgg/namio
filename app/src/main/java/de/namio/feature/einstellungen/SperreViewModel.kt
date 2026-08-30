package de.namio.feature.einstellungen

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.namio.core.repository.EinstellungenRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Zustand der App-Sperre. Gesperrt beim Kaltstart und wenn die App länger als [KARENZ_MS] im
 * Hintergrund war – kurze Ausflüge in Systemdialoge (Dateiauswahl, Kamera, Biometrie) sperren nicht.
 */
@HiltViewModel
class SperreViewModel @Inject constructor(private val einstellungen: EinstellungenRepository) : ViewModel() {
    private val _gesperrt = MutableStateFlow(true)
    val gesperrt: StateFlow<Boolean> = _gesperrt.asStateFlow()
    private var hintergrundSeit: Long? = null

    init {
        viewModelScope.launch { if (!einstellungen.appSperre.first()) _gesperrt.value = false }
    }

    fun entsperrt() { _gesperrt.value = false }

    fun beimStop() { hintergrundSeit = SystemClock.elapsedRealtime() }

    fun beimStart() {
        val seit = hintergrundSeit ?: return
        hintergrundSeit = null
        if (SystemClock.elapsedRealtime() - seit < KARENZ_MS) return
        viewModelScope.launch { if (einstellungen.appSperre.first()) _gesperrt.value = true }
    }

    private companion object {
        const val KARENZ_MS = 30_000L
    }
}
