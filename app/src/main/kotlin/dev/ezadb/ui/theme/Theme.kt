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
    background = Color(0xFFF7FBF1),
    onBackground = Color(0xFF191D17),
    surface = Color(0xFFF7FBF1),
    onSurface = Color(0xFF191D17),
    surfaceVariant = Color(0xFFDEE5D8),
    onSurfaceVariant = Color(0xFF424940),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
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
    background = Color(0xFF101410),
    onBackground = Color(0xFFE1E4DA),
    surface = Color(0xFF101410),
    onSurface = Color(0xFFE1E4DA),
    surfaceVariant = Color(0xFF424940),
    onSurfaceVariant = Color(0xFFC2C9BD),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
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
    MaterialTheme(
        colorScheme = scheme,
        typography = EzTypography,
        shapes = EzShapes,
        content = content,
    )
}
