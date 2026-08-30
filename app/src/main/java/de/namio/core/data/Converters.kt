package de.namio.core.data

import androidx.room.TypeConverter
import de.namio.core.model.Geschlecht
import de.namio.core.model.QuizModus

/** Room-Konverter. [QuizModus] wird als Name gespeichert, damit die DB lesbar bleibt. */
class Converters {
    @TypeConverter
    fun modusZuString(modus: QuizModus): String = modus.name

    @TypeConverter
    fun stringZuModus(name: String): QuizModus = QuizModus.valueOf(name)

    @TypeConverter
    fun geschlechtZuString(g: Geschlecht): String = g.name

    @TypeConverter
    fun stringZuGeschlecht(name: String): Geschlecht =
        runCatching { Geschlecht.valueOf(name) }.getOrDefault(Geschlecht.MAEDCHEN)
}
