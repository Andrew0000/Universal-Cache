package crocodile8.universal_cache_app

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val Purple40 = Color(0xFF6650a4)

val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    background = Color(0xFF121212),
    onBackground = Color(0xFFFFFFFF), // Text color for background in dark mode
    surface = Color(0xFF222222),
    onSurface = Color(0xFFECECEC)    // Text color for surfaces in dark mode
)

val LightColorScheme = lightColorScheme(
    primary = Purple40,
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1C1B1F), // Text color for background in light mode
    surface = Color(0xFFFFF7FF),
    onSurface = Color(0xFF1C1B1F)    // Text color for surfaces in light mode
)
