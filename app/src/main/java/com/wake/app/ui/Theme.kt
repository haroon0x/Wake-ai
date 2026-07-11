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
    background = Color(0xFF0B0D12),
    onBackground = Color(0xFFE9EAF0),
    surface = Color(0xFF10131A),
    onSurface = Color(0xFFE9EAF0),
    surfaceVariant = Color(0xFF191D27),
    onSurfaceVariant = Color(0xFFC4C7D1),
    outline = Color(0xFF353B49),
    error = Color(0xFFFFB4AB)
)

private val WakeTypography = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp)
)

@Composable
fun WakeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = WakeColors, typography = WakeTypography, content = content)
}
