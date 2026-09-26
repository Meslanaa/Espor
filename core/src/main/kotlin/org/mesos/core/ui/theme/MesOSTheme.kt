package org.mesos.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/*
 * MesOS design tokens (0.1).
 *
 * MesOS intentionally does not use wallpaper-derived dynamic color: the system keeps
 * its own identity. The full design system (typography scale, motion, wallpapers)
 * is MesOS 0.2 work; 0.1 only fixes the palette, shapes and light/dark switching.
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFF3B5BDB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE3FF),
    onPrimaryContainer = Color(0xFF0B1F66),
    secondary = Color(0xFF5B6475),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0E4EE),
    onSecondaryContainer = Color(0xFF181C24),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF15181D),
    surface = Color(0xFFF7F8FA),
    onSurface = Color(0xFF15181D),
    surfaceVariant = Color(0xFFE4E7EC),
    onSurfaceVariant = Color(0xFF444A55),
    surfaceContainerLow = Color(0xFFF1F3F6),
    surfaceContainer = Color(0xFFEBEEF2),
    surfaceContainerHigh = Color(0xFFE5E8ED),
    outline = Color(0xFF767C88),
    outlineVariant = Color(0xFFC6CAD2),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB4C3FF),
    onPrimary = Color(0xFF0A2378),
    primaryContainer = Color(0xFF2A43B8),
    onPrimaryContainer = Color(0xFFDCE3FF),
    secondary = Color(0xFFC0C6D4),
    onSecondary = Color(0xFF2A303B),
    secondaryContainer = Color(0xFF414755),
    onSecondaryContainer = Color(0xFFDDE2F0),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE3E6EC),
    surface = Color(0xFF0E1116),
    onSurface = Color(0xFFE3E6EC),
    surfaceVariant = Color(0xFF2A2F38),
    onSurfaceVariant = Color(0xFFC2C7D1),
    surfaceContainerLow = Color(0xFF14181E),
    surfaceContainer = Color(0xFF181C23),
    surfaceContainerHigh = Color(0xFF20252D),
    outline = Color(0xFF8C919B),
    outlineVariant = Color(0xFF3A3F48),
)

private val MesOSShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun MesOSTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = MesOSShapes,
        content = content,
    )
}
