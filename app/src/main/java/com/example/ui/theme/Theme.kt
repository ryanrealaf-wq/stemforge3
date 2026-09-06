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
    primary = ElegantDarkPrimary,
    onPrimary = ElegantDarkOnPrimary,
    primaryContainer = ElegantDarkPrimaryContainer,
    onPrimaryContainer = ElegantDarkOnPrimaryContainer,
    secondary = ElegantDarkSecondary,
    onSecondary = ElegantDarkOnSecondary,
    secondaryContainer = ElegantDarkSecondaryContainer,
    onSecondaryContainer = ElegantDarkOnSecondaryContainer,
    tertiary = ElegantDarkTertiary,
    onTertiary = ElegantDarkOnTertiary,
    tertiaryContainer = ElegantDarkTertiaryContainer,
    onTertiaryContainer = ElegantDarkOnTertiaryContainer,
    background = ElegantDarkBg,
    onBackground = ElegantDarkTextPrimary,
    surface = ElegantDarkSurface,
    onSurface = ElegantDarkTextPrimary,
    surfaceVariant = ElegantDarkSurfaceContainer,
    onSurfaceVariant = ElegantDarkTextSecondary,
    outline = ElegantDarkBorder,
    outlineVariant = ElegantDarkBorderSubtle,
    error = ElegantDarkError,
    onError = ElegantDarkOnError,
    errorContainer = ElegantDarkErrorContainer
)

private val LightColorScheme = lightColorScheme(
    primary = ElegantDarkPrimaryContainer,
    onPrimary = Color.White,
    primaryContainer = ElegantDarkOnPrimaryContainer,
    onPrimaryContainer = ElegantDarkOnPrimary,
    secondary = ElegantDarkSecondaryContainer,
    onSecondary = Color.White,
    secondaryContainer = ElegantDarkOnSecondaryContainer,
    onSecondaryContainer = ElegantDarkOnSecondary,
    tertiary = ElegantDarkTertiaryContainer,
    onTertiary = Color.White,
    tertiaryContainer = ElegantDarkOnTertiaryContainer,
    onTertiaryContainer = ElegantDarkOnTertiary,
    background = StudioBgLight,
    onBackground = StudioTextPrimaryLight,
    surface = StudioSurfaceLight,
    onSurface = StudioTextPrimaryLight,
    surfaceVariant = StudioSurfaceCardLight,
    onSurfaceVariant = StudioTextSecondaryLight,
    outline = StudioBorderLight,
    error = ElegantDarkError
)

@Composable
fun AudioToMidiTheme(
    darkTheme: Boolean = true, // Default to sleek studio dark aesthetic
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
