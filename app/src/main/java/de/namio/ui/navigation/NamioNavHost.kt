package de.namio.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import de.namio.feature.klassen.KlassenDetailScreen
import de.namio.feature.klassen.KlassenListeScreen
import de.namio.feature.quiz.QuizAuswahlScreen
import de.namio.feature.quiz.QuizRundeScreen
import de.namio.feature.schueler.FotoAufnahmeScreen
import de.namio.feature.schueler.SchuelerDetailScreen
import de.namio.feature.sitzplan.SitzplanScreen
import de.namio.feature.statistik.StatistikScreen
import de.namio.feature.csvimport.CsvImportScreen
import de.namio.feature.einstellungen.EinstellungenScreen
import kotlinx.serialization.Serializable

/** Type-Safe Routen. */
sealed interface Route {
    @Serializable data object KlassenListe : Route
    @Serializable data object Einstellungen : Route
    @Serializable data class KlassenDetail(val klasseId: Long) : Route
    @Serializable data class SchuelerDetail(val schuelerId: Long) : Route
    @Serializable data class FotoAufnahme(val schuelerId: Long) : Route
    @Serializable data class QuizAuswahl(val klasseId: Long) : Route
    @Serializable data class Sitzplan(val klasseId: Long) : Route
    @Serializable data class Statistik(val klasseId: Long) : Route
    @Serializable data class CsvImport(val klasseId: Long) : Route
    /** [modus] ist der Name des [de.namio.core.model.QuizModus]. */
    @Serializable data class QuizRunde(val klasseId: Long, val modus: String) : Route
}

@Composable
fun NamioNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Route.KlassenListe) {
        composable<Route.KlassenListe> {
            KlassenListeScreen(
                onKlasseOeffnen = { navController.navigate(Route.KlassenDetail(it)) },
                onEinstellungen = { navController.navigate(Route.Einstellungen) },
            )
        }
        composable<Route.Einstellungen> {
            EinstellungenScreen(onZurueck = { navController.popBackStack() })
        }
        composable<Route.KlassenDetail> {
            KlassenDetailScreen(
                onZurueck = { navController.popBackStack() },
                onSchuelerOeffnen = { navController.navigate(Route.SchuelerDetail(it)) },
                onQuiz = { navController.navigate(Route.QuizAuswahl(it)) },
                onSitzplan = { navController.navigate(Route.Sitzplan(it)) },
                onStatistik = { navController.navigate(Route.Statistik(it)) },
                onCsvImport = { navController.navigate(Route.CsvImport(it)) },
            )
        }
        composable<Route.SchuelerDetail> {
            SchuelerDetailScreen(
                onZurueck = { navController.popBackStack() },
                onFotoAufnehmen = { navController.navigate(Route.FotoAufnahme(it)) },
            )
        }
        composable<Route.FotoAufnahme> {
            FotoAufnahmeScreen(onZurueck = { navController.popBackStack() })
        }
        composable<Route.CsvImport> {
            CsvImportScreen(onZurueck = { navController.popBackStack() })
        }
        composable<Route.Statistik> {
            StatistikScreen(onZurueck = { navController.popBackStack() })
        }
        composable<Route.Sitzplan> {
            SitzplanScreen(onZurueck = { navController.popBackStack() })
        }
        composable<Route.QuizAuswahl> {
            QuizAuswahlScreen(
                onZurueck = { navController.popBackStack() },
                onStarten = { klasseId, modus -> navController.navigate(Route.QuizRunde(klasseId, modus.name)) },
            )
        }
        composable<Route.QuizRunde> {
            QuizRundeScreen(onBeenden = { navController.popBackStack() })
        }
    }
}
