package de.namio.ui.components

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.namio.core.media.FotoStore

/** Zugriff auf den FotoStore aus Composables, um Dateinamen in Dateien aufzulösen. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface FotoStoreEntryPoint {
    fun fotoStore(): FotoStore
}
