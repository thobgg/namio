package de.namio.ui.components

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.namio.core.repository.EinstellungenRepository

/** Zugriff auf Einstellungen aus Composables ohne eigenes ViewModel. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface EinstellungenEntryPoint {
    fun einstellungen(): EinstellungenRepository
}
