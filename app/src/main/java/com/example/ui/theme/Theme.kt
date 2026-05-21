package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFD100),
    secondary = Color(0xFFFFD100),
    tertiary = Color(0xFF8B8015),
    background = Color(0xFF1C1A14),
    surface = Color(0xFF26241E),
    onPrimary = Color(0xFF121100),
    onSecondary = Color(0xFF121100),
    onBackground = Color(0xFFF7F5EA),
    onSurface = Color(0xFFF7F5EA)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFFFD100),
    secondary = Color(0xFFFFD100),
    tertiary = Color(0xFF8B8015),
    background = Color(0xFFFFFCEF),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF222222),
    onSecondary = Color(0xFF222222),
    onBackground = Color(0xFF222222),
    onSurface = Color(0xFF222222)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
