package de.namio.core.lernen

import java.time.LocalDate
import java.time.Month

/**
 * Erinnerung zum Schuljahresende: einmal pro Jahr zwischen Mitte Juni und Ende August,
 * damit Daten der abgegebenen Klassen nicht ewig auf dem Gerät bleiben.
 */
object Schuljahresende {
    fun istZeitfenster(datum: LocalDate): Boolean =
        (datum.month == Month.JUNE && datum.dayOfMonth >= 15) || datum.month == Month.JULY || datum.month == Month.AUGUST

    /** Erinnern, wenn im Zeitfenster und in diesem Jahr noch nicht quittiert – und es überhaupt Klassen gibt. */
    fun erinnern(datum: LocalDate, quittiertJahr: Int, klassenAnzahl: Int): Boolean =
        klassenAnzahl > 0 && istZeitfenster(datum) && quittiertJahr < datum.year
}
