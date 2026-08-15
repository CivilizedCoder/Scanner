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
    primary = VibrantPrimaryDark,
    onPrimary = VibrantOnPrimaryDark,
    primaryContainer = VibrantPrimaryContainerDark,
    onPrimaryContainer = VibrantOnPrimaryContainerDark,
    secondary = VibrantSecondaryDark,
    onSecondary = VibrantOnSecondaryDark,
    secondaryContainer = VibrantSecondaryContainerDark,
    onSecondaryContainer = VibrantOnSecondaryContainerDark,
    tertiary = VibrantTertiaryDark,
    onTertiary = VibrantOnTertiaryDark,
    tertiaryContainer = VibrantTertiaryContainerDark,
    onTertiaryContainer = VibrantOnTertiaryContainerDark,
    background = VibrantBackgroundDark,
    onBackground = VibrantOnBackgroundDark,
    surface = VibrantSurfaceDark,
    onSurface = VibrantOnSurfaceDark,
    surfaceVariant = VibrantSurfaceVariantDark,
    onSurfaceVariant = VibrantOnSurfaceVariantDark,
    outline = VibrantOutlineDark,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColorScheme = lightColorScheme(
    primary = VibrantPrimaryLight,
    onPrimary = VibrantOnPrimaryLight,
    primaryContainer = VibrantPrimaryContainerLight,
    onPrimaryContainer = VibrantOnPrimaryContainerLight,
    secondary = VibrantSecondaryLight,
    onSecondary = VibrantOnSecondaryLight,
    secondaryContainer = VibrantSecondaryContainerLight,
    onSecondaryContainer = VibrantOnSecondaryContainerLight,
    tertiary = VibrantTertiaryLight,
    onTertiary = VibrantOnTertiaryLight,
    tertiaryContainer = VibrantTertiaryContainerLight,
    onTertiaryContainer = VibrantOnTertiaryContainerLight,
    background = VibrantBackgroundLight,
    onBackground = VibrantOnBackgroundLight,
    surface = VibrantSurfaceLight,
    onSurface = VibrantOnSurfaceLight,
    surfaceVariant = VibrantSurfaceVariantLight,
    onSurfaceVariant = VibrantOnSurfaceVariantLight,
    outline = VibrantOutlineLight,
    error = VibrantRed,
    onError = Color.White,
    errorContainer = VibrantRedContainer,
    onErrorContainer = VibrantOnRedContainer
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep Vibrant Palette active
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

