package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = TealPrimary,
    secondary = TealSecondary,
    tertiary = AmberAccent,
    background = SpaceDarkBg,
    surface = SpaceCardBg,
    onPrimary = OnTeal,
    onSecondary = OnTeal,
    onTertiary = OnTeal,
    onBackground = TextPrimary,
    onSurface = TextLight,
    surfaceContainer = SpacePlayerBg
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force Dark theme like Musicolet's standard state
    dynamicColor: Boolean = false, // Disable dynamic colors to keep Musicolet identity
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
