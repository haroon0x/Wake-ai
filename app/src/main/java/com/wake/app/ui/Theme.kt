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
    primary = Color(0xFF8D84FF),
    onPrimary = Color(0xFF11101C),
    primaryContainer = Color(0xFF312D63),
    onPrimaryContainer = Color(0xFFF0EEFF),
    secondary = Color(0xFF65D6C5),
    onSecondary = Color(0xFF07201C),
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
