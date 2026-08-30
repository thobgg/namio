package de.namio.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Speichert Schülerfotos ausschließlich im internen Speicher unter `filesDir/photos/<uuid>.jpg`.
 * Bilder werden mittig quadratisch zugeschnitten, auf [MAX_KANTE] Pixel verkleinert und als JPEG abgelegt.
 */
@Singleton
class FotoStore @Inject constructor(@ApplicationContext private val context: Context) {

    private val ordner: File
        get() = File(context.filesDir, ORDNER).also { it.mkdirs() }

    /** Datei zu einem gespeicherten Dateinamen. Existiert nicht zwingend. */
    fun datei(name: String): File = File(ordner, name)

    /** Speichert JPEG-Rohdaten (z. B. aus CameraX) und liefert den neuen Dateinamen. */
    suspend fun speichere(jpegBytes: ByteArray, rotationGrad: Int, spiegeln: Boolean = false): String =
        withContext(Dispatchers.IO) {
            val bitmap = dekodiere(jpegBytes) ?: throw IOException("Bild nicht dekodierbar")
            schreibe(bereite(bitmap, rotationGrad, spiegeln))
        }

    /** Liest ein Bild aus einer Content-Uri (Photo Picker) und speichert es. */
    suspend fun speichereAusUri(uri: Uri): String = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Bild nicht lesbar")
        val rotation = exifRotation(bytes)
        val bitmap = dekodiere(bytes) ?: throw IOException("Bild nicht dekodierbar")
        schreibe(bereite(bitmap, rotation, spiegeln = false))
    }

    /** Namen der mitgelieferten Standard-Avatare (Assets unter `avatare/`), sortiert. */
    fun avatare(): List<String> =
        context.assets.list(AVATAR_ORDNER)?.filter { it.endsWith(".jpg") }?.sorted().orEmpty()

    /** Uri eines Avatars zum Anzeigen (Coil kann `file:///android_asset/…` laden). */
    fun avatarUri(name: String): Uri = Uri.parse("file:///android_asset/$AVATAR_ORDNER/$name")

    /** Kopiert einen Standard-Avatar als reguläres Foto in den Store. */
    suspend fun speichereAvatar(name: String): String = withContext(Dispatchers.IO) {
        val bytes = context.assets.open("$AVATAR_ORDNER/$name").use { it.readBytes() }
        val bitmap = dekodiere(bytes) ?: throw IOException("Avatar nicht dekodierbar")
        schreibe(bereite(bitmap, 0, spiegeln = false))
    }

    /** Löscht eine Fotodatei. Ein unbekannter oder leerer Name ist kein Fehler. */
    suspend fun loesche(name: String?) {
        if (name.isNullOrBlank()) return
        withContext(Dispatchers.IO) { datei(name).delete() }
    }

    /** Löscht mehrere Fotodateien. */
    suspend fun loescheAlle(namen: Collection<String>) = namen.forEach { loesche(it) }

    private fun dekodiere(bytes: ByteArray): Bitmap? {
        val grenzen = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, grenzen)
        if (grenzen.outWidth <= 0 || grenzen.outHeight <= 0) return null
        val optionen = BitmapFactory.Options().apply {
            inSampleSize = Bildzuschnitt.sampleSize(grenzen.outWidth, grenzen.outHeight, MAX_KANTE)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, optionen)
    }

    private fun bereite(quelle: Bitmap, rotationGrad: Int, spiegeln: Boolean): Bitmap {
        val gedreht = if (rotationGrad != 0 || spiegeln) {
            val matrix = Matrix().apply {
                postRotate(rotationGrad.toFloat())
                if (spiegeln) postScale(-1f, 1f)
            }
            Bitmap.createBitmap(quelle, 0, 0, quelle.width, quelle.height, matrix, true)
        } else {
            quelle
        }
        val a = Bildzuschnitt.mittigesQuadrat(gedreht.width, gedreht.height)
        val quadrat = Bitmap.createBitmap(gedreht, a.x, a.y, a.groesse, a.groesse)
        val kante = Bildzuschnitt.zielKante(a.groesse, MAX_KANTE)
        return if (kante != a.groesse) Bitmap.createScaledBitmap(quadrat, kante, kante, true) else quadrat
    }

    private fun schreibe(bitmap: Bitmap): String {
        val name = "${UUID.randomUUID()}.jpg"
        val ziel = datei(name)
        val temp = File(ordner, "$name.tmp")
        temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITAET, it) }
        if (!temp.renameTo(ziel)) throw IOException("Foto konnte nicht abgelegt werden")
        return name
    }

    private fun exifRotation(bytes: ByteArray): Int = runCatching {
        when (ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }.getOrDefault(0)

    private companion object {
        const val ORDNER = "photos"
        const val AVATAR_ORDNER = "avatare"
        const val MAX_KANTE = 800
        const val JPEG_QUALITAET = 85
    }
}
