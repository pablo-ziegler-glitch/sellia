package com.example.selliaapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ValkirjaLightColors = lightColorScheme(
    primary = Color(0xFF5B7A7C),
    onPrimary = Color(0xFFF7EEE2),
    primaryContainer = Color(0xFFE5D2B8),
    onPrimaryContainer = Color(0xFF3E4046),
    secondary = Color(0xFF9C6A4C),
    onSecondary = Color(0xFFF7EEE2),
    secondaryContainer = Color(0xFFD9A35B),
    onSecondaryContainer = Color(0xFF3E4046),
    tertiary = Color(0xFFD9A35B),
    onTertiary = Color(0xFF2E2A27),
    background = Color(0xFFF7EEE2),
    onBackground = Color(0xFF3E4046),
    surface = Color(0xFFFDF7EF),
    onSurface = Color(0xFF3E4046),
    surfaceVariant = Color(0xFFE5D2B8),
    onSurfaceVariant = Color(0xFF7A5A47),
    outline = Color(0xFFBCA58D)
)

private val ValkirjaDarkColors = darkColorScheme(
    primary = Color(0xFFD9A35B),
    onPrimary = Color(0xFF2E2A27),
    primaryContainer = Color(0xFF9C6A4C),
    onPrimaryContainer = Color(0xFFF7EEE2),
    secondary = Color(0xFF5B7A7C),
    onSecondary = Color(0xFFF7EEE2),
    secondaryContainer = Color(0xFF3A332E),
    onSecondaryContainer = Color(0xFFE5D2B8),
    tertiary = Color(0xFFE5D2B8),
    onTertiary = Color(0xFF2E2A27),
    background = Color(0xFF2B2622),
    onBackground = Color(0xFFF7EEE2),
    surface = Color(0xFF3A332E),
    onSurface = Color(0xFFF7EEE2),
    surfaceVariant = Color(0xFF4C433C),
    onSurfaceVariant = Color(0xFFE5D2B8),
    outline = Color(0xFF6E5E4F)
)

data class ThemePalette(
    val primaryHex: String = "",
    val secondaryHex: String = "",
    val tertiaryHex: String = ""
)

@Composable
fun ValkirjaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicPalette: ThemePalette = ThemePalette(),
    content: @Composable () -> Unit
) {
    val base = if (darkTheme) ValkirjaDarkColors else ValkirjaLightColors
    val colors = base.withPalette(dynamicPalette)

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}

private fun ColorScheme.withPalette(palette: ThemePalette): ColorScheme {
    val primaryOverride = palette.primaryHex.toColorOrNull()
    val secondaryOverride = palette.secondaryHex.toColorOrNull()
    val tertiaryOverride = palette.tertiaryHex.toColorOrNull()

    return copy(
        primary = primaryOverride ?: primary,
        secondary = secondaryOverride ?: secondary,
        tertiary = tertiaryOverride ?: tertiary
    )
}

private fun String.toColorOrNull(): Color? {
    val normalized = trim().uppercase().let { if (it.startsWith("#")) it else "#$it" }
    if (!normalized.matches(Regex("^#[0-9A-F]{6}$"))) return null
    return runCatching { Color(android.graphics.Color.parseColor(normalized)) }.getOrNull()
}
