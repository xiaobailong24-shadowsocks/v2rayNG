package com.v2ray.compose.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Brand = Color(0xFF2E7D32)
private val BrandDark = Color(0xFF1B5E20)
private val Accent = Color(0xFF00C853)

private val LightColors = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9F6CA),
    onPrimaryContainer = Color(0xFF002106),
    secondary = Color(0xFF4CAF50),
    tertiary = Color(0xFF00897B),
)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF003910),
    primaryContainer = BrandDark,
    onPrimaryContainer = Color(0xFFB9F6CA),
    secondary = Color(0xFF81C784),
    tertiary = Color(0xFF4DB6AC),
)

@Composable
fun V2rayComposeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
