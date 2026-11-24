package com.xperience.player.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class ThemeMode(val label: String) {
    SYSTEM("System Default"),
    DARK("Dark"),
    LIGHT("Light")
}

@Immutable
data class AppThemeColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val cardBackground: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val glassSurfaceTint: Color,
    val glassTextPrimary: Color,
    val glassTextSecondary: Color,
    val glassHighlightAlpha: Float,
    val glassPillTint: Color,
    val dividerColor: Color
)

val DarkThemeColors = AppThemeColors(
    isDark = true,
    background = Color(0xFF0F1012),
    surface = Color(0xFF14161C),
    surfaceVariant = Color(0xFF1E2129),
    cardBackground = Color(0x22FFFFFF),
    cardBorder = Color(0x18FFFFFF),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFA2A7B5),
    textTertiary = Color(0xFF717684),
    glassSurfaceTint = Color(0xFF16181F),
    glassTextPrimary = Color(0xFFFFFFFF),
    glassTextSecondary = Color(0xFFAEB2BD),
    glassHighlightAlpha = 0.35f,
    glassPillTint = Color(0x601A1D24),
    dividerColor = Color(0x20FFFFFF)
)

val LightThemeColors = AppThemeColors(
    isDark = false,
    background = Color(0xFFF6F8FB),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEEF2F6),
    cardBackground = Color(0xFFFFFFFF),
    cardBorder = Color(0x16000000),
    textPrimary = Color(0xFF10141D),
    textSecondary = Color(0xFF64748B),
    textTertiary = Color(0xFF94A3B8),
    glassSurfaceTint = Color(0xFFFFFFFF),
    glassTextPrimary = Color(0xFF10141D),
    glassTextSecondary = Color(0xFF64748B),
    glassHighlightAlpha = 0.45f,
    glassPillTint = Color(0xE8FFFFFF),
    dividerColor = Color(0x12000000)
)

val LocalAppColors = staticCompositionLocalOf { DarkThemeColors }
