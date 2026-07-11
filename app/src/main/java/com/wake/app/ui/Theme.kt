package com.wake.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val WakeColors = darkColorScheme(
    primary = Color(0xFF6C63FF),
    background = Color(0xFF0F1017),
    surface = Color(0xFF1A1B2E),
    surfaceVariant = Color(0xFF232438),
    onBackground = Color(0xFFECECF4),
    secondary = Color(0xFF4ECDC4)
)

@Composable
fun WakeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = WakeColors, content = content)
}
