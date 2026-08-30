package de.namio.core.data.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.namio.core.data.NamioDatabase
import de.namio.core.security.KeystoreManager
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatenModule {

    @Provides
    @Singleton
    fun datenbank(@ApplicationContext context: Context, keystore: KeystoreManager): NamioDatabase {
        // sqlcipher-android (Nachfolger von android-database-sqlcipher): das alte Artefakt ist
        // deprecated und seine libsqlcipher.so nicht 16-KB-page-aligned (Android 15+).
        // 4.18.0+ verlangt compileSdk 37, deshalb 4.17.0 bis AGP 8.13 abgelöst wird.
        System.loadLibrary("sqlcipher")
        return Room.databaseBuilder(context, NamioDatabase::class.java, "namio.db")
            .openHelperFactory(SupportOpenHelperFactory(keystore.datenbankPassphrase()))
            .addMigrations(*NamioDatabase.ALLE_MIGRATIONEN)
            .build()
    }

    @Provides fun klasseDao(db: NamioDatabase) = db.klasseDao()
    @Provides fun schuelerDao(db: NamioDatabase) = db.schuelerDao()
    @Provides fun lernkarteDao(db: NamioDatabase) = db.lernkarteDao()
    @Provides fun quizSessionDao(db: NamioDatabase) = db.quizSessionDao()
    @Provides fun quizAntwortDao(db: NamioDatabase) = db.quizAntwortDao()
    @Provides fun sitzplanDao(db: NamioDatabase) = db.sitzplanDao()
    @Provides fun sitzplatzDao(db: NamioDatabase) = db.sitzplatzDao()

    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemDefaultZone()
}
