package com.example.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = CyberPrimary,
    secondary = CyberSecondary,
    tertiary = CyberTertiary,
    background = MidnightDark,
    surface = SlateSurface,
    surfaceVariant = SlateSurfaceVariant,
    onBackground = CyberTextColor,
    onSurface = CyberTextColor,
    onPrimary = Color.White,
    onSecondary = MidnightDark,
    onTertiary = Color.White,
    primaryContainer = Color(0xFF1B2A4A),
    onPrimaryContainer = Color(0xFFD3E4FF),
    outline = Color(0xFF30363D)
)

private val LightColorScheme = lightColorScheme(
    primary = GeoPrimary,
    onPrimary = Color.White,
    primaryContainer = GeoPrimaryContainer,
    onPrimaryContainer = GeoOnPrimaryContainer,
    secondary = GeoDarkText,
    onSecondary = Color.White,
    secondaryContainer = GeoSecondary,
    onSecondaryContainer = GeoPrimaryContainer,
    tertiary = GeoHighlight,
    onTertiary = GeoPrimaryContainer,
    background = GeoBg,
    onBackground = GeoOnBg,
    surface = Color.White,
    onSurface = GeoOnBg,
    surfaceVariant = GeoNavBarBg,
    onSurfaceVariant = GeoDarkText,
    outline = GeoBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Default to false to showcase Geometric Balance theme
    dynamicColor: Boolean = false, // Use our master branded cyber-slate theme
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
