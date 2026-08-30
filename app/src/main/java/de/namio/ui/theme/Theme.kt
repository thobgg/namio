package de.namio.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Hell = lightColorScheme(
    primary = Color(0xFF1E5F74),
    secondary = Color(0xFF4F6D7A),
    tertiary = Color(0xFFB8860B),
)

private val Dunkel = darkColorScheme(
    primary = Color(0xFF8FD3E8),
    secondary = Color(0xFFB4CAD4),
    tertiary = Color(0xFFFFD166),
)

@Composable
fun NamioTheme(content: @Composable () -> Unit) {
    val dunkel = isSystemInDarkTheme()
    val schema = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dunkel) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dunkel -> Dunkel
        else -> Hell
    }
    MaterialTheme(colorScheme = schema, content = content)
}
