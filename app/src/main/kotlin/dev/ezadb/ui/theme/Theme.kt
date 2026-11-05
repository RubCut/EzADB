package dev.ezadb.ui.theme

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
import dev.ezadb.data.Accent
import dev.ezadb.data.AppPreferences
import dev.ezadb.data.ThemeMode

private val GreenLight = lightColorScheme(
    primary = Color(0xFF006E1C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF97F994),
    onPrimaryContainer = Color(0xFF002204),
    secondary = Color(0xFF52634F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5E8CF),
    onSecondaryContainer = Color(0xFF101F0F),
    tertiary = Color(0xFF38656A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBCEBF0),
    onTertiaryContainer = Color(0xFF002023),
    background = Color(0xFFF6FAF0),
    onBackground = Color(0xFF181D16),
    surface = Color(0xFFF6FAF0),
    onSurface = Color(0xFF181D16),
    surfaceVariant = Color(0xFFDEE5D8),
    onSurfaceVariant = Color(0xFF424940),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F5EA),
    surfaceContainer = Color(0xFFEAEFE3),
    surfaceContainerHigh = Color(0xFFE4E9DC),
    surfaceContainerHighest = Color(0xFFDEE4D6),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val GreenDark = darkColorScheme(
    primary = Color(0xFF7BDC7B),
    onPrimary = Color(0xFF00390A),
    primaryContainer = Color(0xFF005313),
    onPrimaryContainer = Color(0xFF97F994),
    secondary = Color(0xFFB9CCB4),
    onSecondary = Color(0xFF253423),
    secondaryContainer = Color(0xFF3B4B38),
    onSecondaryContainer = Color(0xFFD5E8CF),
    tertiary = Color(0xFFA0CFD4),
    onTertiary = Color(0xFF00363B),
    tertiaryContainer = Color(0xFF1E4D52),
    onTertiaryContainer = Color(0xFFBCEBF0),
    background = Color(0xFF0F140E),
    onBackground = Color(0xFFE0E4D9),
    surface = Color(0xFF0F140E),
    onSurface = Color(0xFFE0E4D9),
    surfaceVariant = Color(0xFF424940),
    onSurfaceVariant = Color(0xFFC2C9BD),
    surfaceContainerLowest = Color(0xFF0A0F09),
    surfaceContainerLow = Color(0xFF171C15),
    surfaceContainer = Color(0xFF1B2119),
    surfaceContainerHigh = Color(0xFF252B22),
    surfaceContainerHighest = Color(0xFF30362C),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun EzTheme(prefs: AppPreferences, content: @Composable () -> Unit) {
    val dark = when (prefs.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val scheme = when {
        prefs.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        prefs.accent == Accent.GREEN && dark -> GreenDark
        prefs.accent == Accent.GREEN -> GreenLight
        dark -> darkColorScheme(primary = Color(prefs.accent.seed))
        else -> lightColorScheme(primary = Color(prefs.accent.seed))
    }
    // Stable Material 3 theme with expressive shapes, emphasized type and spring motion.
    MaterialTheme(
        colorScheme = scheme,
        typography = EzTypography,
        shapes = EzShapes,
        content = content,
    )
}
