package de.namio.feature.einstellungen

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

/** Zustand der App-Sperre: gesperrt, bis der Nutzer sich ausgewiesen hat. */
@HiltViewModel
class SperreViewModel @Inject constructor(private val einstellungen: EinstellungenRepository) : ViewModel() {
    private val _gesperrt = MutableStateFlow(true)
    val gesperrt: StateFlow<Boolean> = _gesperrt.asStateFlow()

    init {
        viewModelScope.launch { if (!einstellungen.appSperre.first()) _gesperrt.value = false }
    }

    fun entsperrt() { _gesperrt.value = false }

    /** Beim Verlassen der App wieder sperren – falls die Sperre aktiv ist. */
    fun sperren() {
        viewModelScope.launch { if (einstellungen.appSperre.first()) _gesperrt.value = true }
    }
}
