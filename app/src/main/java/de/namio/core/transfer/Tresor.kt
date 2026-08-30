package de.namio.core.transfer

import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Passwortbasierte Verschlüsselung für Export-Dateien: PBKDF2-HMAC-SHA256 (200 000 Runden)
 * leitet einen AES-256-Schlüssel ab, AES-GCM sichert Vertraulichkeit und Integrität.
 * Dateiformat: `NAMIO1` · Salt (16) · IV (12) · Chiffrat mit GCM-Tag.
 */
object Tresor {
    private const val MAGIC = "NAMIO1"
    private const val RUNDEN = 200_000
    private const val SALT_LAENGE = 16
    private const val IV_LAENGE = 12

    /** Wird geworfen, wenn Passwort oder Datei nicht passen. */
    class FalschesPasswortOderDatei : Exception()

    fun verschluessele(klartext: ByteArray, passwort: CharArray): ByteArray {
        val zufall = SecureRandom()
        val salt = ByteArray(SALT_LAENGE).also(zufall::nextBytes)
        val iv = ByteArray(IV_LAENGE).also(zufall::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, schluessel(passwort, salt), GCMParameterSpec(128, iv))
        val out = ByteArrayOutputStream(klartext.size + 64)
        out.write(MAGIC.toByteArray(Charsets.US_ASCII))
        out.write(salt)
        out.write(iv)
        out.write(cipher.doFinal(klartext))
        return out.toByteArray()
    }

    fun entschluessele(daten: ByteArray, passwort: CharArray): ByteArray {
        val kopf = MAGIC.length + SALT_LAENGE + IV_LAENGE
        if (daten.size < kopf + 16 || String(daten, 0, MAGIC.length, Charsets.US_ASCII) != MAGIC) throw FalschesPasswortOderDatei()
        val salt = daten.copyOfRange(MAGIC.length, MAGIC.length + SALT_LAENGE)
        val iv = daten.copyOfRange(MAGIC.length + SALT_LAENGE, kopf)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, schluessel(passwort, salt), GCMParameterSpec(128, iv))
        return try {
            cipher.doFinal(daten, kopf, daten.size - kopf)
        } catch (e: javax.crypto.AEADBadTagException) {
            throw FalschesPasswortOderDatei()
        }
    }

    private fun schluessel(passwort: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passwort, salt, RUNDEN, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }
}
