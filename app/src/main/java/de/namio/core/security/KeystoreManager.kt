package de.namio.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verwaltet die SQLCipher-Passphrase. Die Passphrase selbst ist zufällig und liegt
 * AES-GCM-verschlüsselt in [filesDir]; der Schlüssel dazu verlässt den Android Keystore nie.
 */
@Singleton
class KeystoreManager @Inject constructor(@ApplicationContext private val context: Context) {

    private val passphraseDatei get() = File(context.filesDir, PASSPHRASE_DATEI)

    /**
     * Liefert die Datenbank-Passphrase als Bytes (64 Hex-Zeichen). Beim ersten Aufruf wird
     * sie erzeugt und verschlüsselt abgelegt. Der Aufrufer darf das Array anschließend nullen.
     */
    @Synchronized
    fun datenbankPassphrase(): ByteArray {
        val schluessel = ladeOderErzeugeSchluessel()
        val datei = passphraseDatei
        if (datei.exists()) {
            val inhalt = datei.readBytes()
            val iv = inhalt.copyOfRange(0, IV_LAENGE)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, schluessel, GCMParameterSpec(TAG_BITS, iv))
            return cipher.doFinal(inhalt, IV_LAENGE, inhalt.size - IV_LAENGE)
        }
        val zufall = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val passphrase = zufall.joinToString("") { "%02x".format(it) }.toByteArray(Charsets.US_ASCII)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, schluessel)
        val temp = File(datei.parentFile, "$PASSPHRASE_DATEI.tmp")
        temp.writeBytes(cipher.iv + cipher.doFinal(passphrase))
        if (!temp.renameTo(datei)) error("Passphrase-Datei konnte nicht geschrieben werden")
        return passphrase
    }

    private fun ladeOderErzeugeSchluessel(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(SCHLUESSEL_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                SCHLUESSEL_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val SCHLUESSEL_ALIAS = "namio_db_key"
        const val PASSPHRASE_DATEI = "db.key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LAENGE = 12
        const val TAG_BITS = 128
    }
}
