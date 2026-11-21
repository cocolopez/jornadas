package com.perez.jornadas

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Degradado de marca (verde petróleo a menta). */
val BrandBrush = Brush.linearGradient(listOf(Color(0xFF0B6B63), Color(0xFF22B59B)))

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F766E), onPrimary = Color.White,
    primaryContainer = Color(0xFFD3F1EA), onPrimaryContainer = Color(0xFF04302B),
    secondary = Color(0xFFE8654B), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDFD6), onSecondaryContainer = Color(0xFF4A1409),
    tertiary = Color(0xFFE2A12B), onTertiary = Color(0xFF3B2800),
    tertiaryContainer = Color(0xFFFFEDC2), onTertiaryContainer = Color(0xFF3B2800),
    background = Color(0xFFFAF7F2), onBackground = Color(0xFF1E211F),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF1E211F),
    surfaceVariant = Color(0xFFEFEAE2), onSurfaceVariant = Color(0xFF5A5348),
    surfaceTint = Color(0xFF0F766E),
    outline = Color(0xFF9A9184), outlineVariant = Color(0xFFE2DCD2),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFBF8F4),
    surfaceContainer = Color(0xFFF4F0E9), surfaceContainerHigh = Color(0xFFEFEAE1),
    surfaceContainerHighest = Color(0xFFE9E3D9)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5FD3BF), onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF0F5A50), onPrimaryContainer = Color(0xFFCFF5EC),
    secondary = Color(0xFFFF9B85), onSecondary = Color(0xFF4A1409),
    secondaryContainer = Color(0xFF6B2A1B), onSecondaryContainer = Color(0xFFFFDFD6),
    tertiary = Color(0xFFF2C466), onTertiary = Color(0xFF3B2800),
    tertiaryContainer = Color(0xFF5A4200), onTertiaryContainer = Color(0xFFFFEDC2),
    background = Color(0xFF0F1514), onBackground = Color(0xFFE3EAE7),
    surface = Color(0xFF151C1B), onSurface = Color(0xFFE3EAE7),
    surfaceVariant = Color(0xFF26302E), onSurfaceVariant = Color(0xFFB5C0BC),
    surfaceTint = Color(0xFF5FD3BF),
    outline = Color(0xFF7A8582), outlineVariant = Color(0xFF34403D),
    surfaceContainerLowest = Color(0xFF0B1110), surfaceContainerLow = Color(0xFF121918),
    surfaceContainer = Color(0xFF182120), surfaceContainerHigh = Color(0xFF1F2A28),
    surfaceContainerHighest = Color(0xFF273331)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = AppShapes,
        typography = AppTypography,
        content = content
    )
}
