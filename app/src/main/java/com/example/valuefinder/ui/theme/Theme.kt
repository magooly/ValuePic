package com.example.valuefinder.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.valuefinder.AppTier

// ── Personal tier palette (purple/violet — formerly "abc" / Main flavour) ──────
private val PersonalLightColors = lightColorScheme(
    primary = Color(0xFF6200EE),
    secondary = Color(0xFF03DAC6),
    tertiary = Color(0xFF018786),
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    surfaceVariant = Color(0xFFE7E0EC),
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    onSurfaceVariant = Color(0xFF2D2A32),
    outline = Color(0xFF5F5A66),
)

private val PersonalDarkColors = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    secondary = Color(0xFF4FD8D6),
    tertiary = Color(0xFF4FD8D6),
    background = Color(0xFF1C1B1F),
    surface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFF49454F),
    onBackground = Color(0xFFE6E1E5),
    onSurface = Color(0xFFE6E1E5),
    onSurfaceVariant = Color(0xFFF1EDF5),
    outline = Color(0xFFC8C3CE),
)

// ── Insurance tier palette (blue/navy — formerly "xyz" / Other flavour) ─────────
private val InsuranceLightColors = lightColorScheme(
    primary = Color(0xFF1565C0),
    secondary = Color(0xFF0288D1),
    tertiary = Color(0xFF0097A7),
    background = Color(0xFFBBDEFB),
    surface = Color(0xFF90CAF9),
    surfaceVariant = Color(0xFF64B5F6),
    onBackground = Color(0xFF0D1B2A),
    onSurface = Color(0xFF0D1B2A),
    onSurfaceVariant = Color(0xFF0D1B2A),
    outline = Color(0xFF1565C0),
)

private val InsuranceDarkColors = darkColorScheme(
    primary = Color(0xFF90CAF9),
    secondary = Color(0xFF4FC3F7),
    tertiary = Color(0xFF4DD0E1),
    background = Color(0xFF0A1929),
    surface = Color(0xFF0D1F30),
    surfaceVariant = Color(0xFF132F45),
    onBackground = Color(0xFFE3F2FD),
    onSurface = Color(0xFFE3F2FD),
    onSurfaceVariant = Color(0xFFBBDEFB),
    outline = Color(0xFF4A7FAB),
)

@Composable
fun ValuePicsTheme(
    darkTheme: Boolean = false,
    appTier: AppTier = AppTier.PERSONAL,
    content: @Composable () -> Unit
) {
    val colorScheme = when (appTier) {
        AppTier.PERSONAL -> if (darkTheme) PersonalDarkColors else PersonalLightColors
        AppTier.INSURANCE -> if (darkTheme) InsuranceDarkColors else InsuranceLightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}
