package com.wake.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val WakeColors = darkColorScheme(
    primary = Color(0xFF4FD6BF),
    onPrimary = Color(0xFF06201B),
    primaryContainer = Color(0xFF14453C),
    onPrimaryContainer = Color(0xFFD3F5EC),
    secondary = Color(0xFFE9C46A),
    onSecondary = Color(0xFF241B04),
    background = Color(0xFF0A0C10),
    onBackground = Color(0xFFEAECEF),
    surface = Color(0xFF11141B),
    onSurface = Color(0xFFEAECEF),
    surfaceVariant = Color(0xFF1A1F29),
    onSurfaceVariant = Color(0xFFC6CAD3),
    tertiary = Color(0xFF9AB8FF),
    onTertiary = Color(0xFF0D1B33),
    outline = Color(0xFF333A48),
    error = Color(0xFFFFB4AB)
)

private val WakeTypography = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, letterSpacing = 0.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = (-0.3).sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.3.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.3.sp)
)

@Composable
fun WakeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = WakeColors, typography = WakeTypography, content = content)
}
