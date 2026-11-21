package com.perez.jornadas

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Navy = Color(0xFF14243B)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F3A5F), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6F2), onPrimaryContainer = Color(0xFF0F2238),
    secondary = Color(0xFF2F8F83), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5ECE8), onSecondaryContainer = Color(0xFF0B3A34),
    tertiary = Color(0xFFB8893B), onTertiary = Color.White,
    background = Color(0xFFF5F7FA), onBackground = Color(0xFF1A2330),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF1A2330),
    surfaceVariant = Color(0xFFE6EBF1), onSurfaceVariant = Color(0xFF4B5668),
    surfaceTint = Color(0xFF1F3A5F),
    outline = Color(0xFF8A94A3), outlineVariant = Color(0xFFD3D9E0),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF5F7FA),
    surfaceContainer = Color(0xFFEFF2F6), surfaceContainerHigh = Color(0xFFE9EDF2),
    surfaceContainerHighest = Color(0xFFE4E9EF)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DB9DC), onPrimary = Color(0xFF0F2238),
    primaryContainer = Color(0xFF2A4A73), onPrimaryContainer = Color(0xFFDCE6F2),
    secondary = Color(0xFF7CCBC0), onSecondary = Color(0xFF06302B),
    secondaryContainer = Color(0xFF1F5C54), onSecondaryContainer = Color(0xFFD5ECE8),
    tertiary = Color(0xFFE0B665), onTertiary = Color(0xFF3A2A06),
    background = Color(0xFF10151C), onBackground = Color(0xFFE4E8EE),
    surface = Color(0xFF161C25), onSurface = Color(0xFFE4E8EE),
    surfaceVariant = Color(0xFF232B37), onSurfaceVariant = Color(0xFFAAB3C0),
    surfaceTint = Color(0xFF9DB9DC),
    outline = Color(0xFF6F7A8A), outlineVariant = Color(0xFF323C4A),
    surfaceContainerLowest = Color(0xFF0D1218), surfaceContainerLow = Color(0xFF141A22),
    surfaceContainer = Color(0xFF1A212B), surfaceContainerHigh = Color(0xFF212936),
    surfaceContainerHighest = Color(0xFF283140)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = AppShapes,
        content = content
    )
}
