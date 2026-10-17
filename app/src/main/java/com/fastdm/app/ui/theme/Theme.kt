package com.fastdm.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val BrandBlue = Color(0xFF5B7CFF)
val BrandCyan = Color(0xFF22D3EE)
val Success = Color(0xFF34D399)
val Warning = Color(0xFFFBBF24)
val BrandGradient = Brush.linearGradient(listOf(Color(0xFF4F6BFF), Color(0xFF14B8D4)))

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7C9CFF),
    onPrimary = Color(0xFF0B1230),
    primaryContainer = Color(0xFF1E2B5C),
    onPrimaryContainer = Color(0xFFDCE4FF),
    secondary = BrandCyan,
    onSecondary = Color(0xFF00252B),
    tertiary = Success,
    background = Color(0xFF0B0F1A),
    onBackground = Color(0xFFE8ECF8),
    surface = Color(0xFF121826),
    onSurface = Color(0xFFE8ECF8),
    surfaceVariant = Color(0xFF1B2335),
    onSurfaceVariant = Color(0xFF9AA6C4),
    outline = Color(0xFF4A5675),
    outlineVariant = Color(0xFF263049),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3B0A0A),
    errorContainer = Color(0xFF4A1C1C),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF3B5BDB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE4FF),
    onPrimaryContainer = Color(0xFF0B1A5C),
    secondary = Color(0xFF0891B2),
    onSecondary = Color.White,
    tertiary = Color(0xFF059669),
    background = Color(0xFFF5F7FD),
    onBackground = Color(0xFF111827),
    surface = Color.White,
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE9EDF8),
    onSurfaceVariant = Color(0xFF55607D),
    outline = Color(0xFF9AA6C4),
    outlineVariant = Color(0xFFDCE2F0),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFFE1E1),
    onErrorContainer = Color(0xFF410002)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun FastDMTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = AppShapes,
        content = content
    )
}
