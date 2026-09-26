package org.mesos.core.ui.theme

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.mesos.core.prefs.Accent
import org.mesos.core.prefs.MesOSPreferences
import org.mesos.core.prefs.ThemeMode

/*
 * MesOS "Aurora" design tokens (0.3).
 *
 * Deep navy neutrals, one accent colour the user picks, bundled Sora/Manrope type
 * and generous rounded shapes. Wallpaper-derived dynamic colour is intentionally
 * not used: MesOS keeps its own identity.
 */

/** Colours Material's scheme has no slot for. Read them through [MesOSTheme.colors]. */
@Immutable
data class MesOSColors(
    /** Grouped rows and cards on the page background. */
    val card: Color,
    /** Hairlines between rows inside a card. */
    val separator: Color,
    /** Secondary text on cards. */
    val dim: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    /** Accent in its bright form, for glass surfaces over wallpapers. */
    val accentBright: Color,
    val isDark: Boolean,
)

private val LocalMesOSColors = staticCompositionLocalOf {
    MesOSColors(
        card = Color.White,
        separator = Color(0xFFEEF1F6),
        dim = Color(0xFF64748B),
        success = Color(0xFF10B981),
        warning = Color(0xFFF59E0B),
        danger = Color(0xFFF43F5E),
        accentBright = Color(0xFF6D7CFF),
        isDark = false,
    )
}

object MesOSTheme {
    val colors: MesOSColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMesOSColors.current
}

private fun darkScheme(accent: Accent): ColorScheme {
    val base = Color(accent.base)
    val background = Color(0xFF070C1A)
    val onAccent = if (base.luminance() > 0.4f) Color(0xFF06121F) else Color.White
    return darkColorScheme(
        primary = base,
        onPrimary = onAccent,
        primaryContainer = lerp(background, base, 0.28f),
        onPrimaryContainer = lerp(base, Color.White, 0.65f),
        inversePrimary = Color(accent.deep),
        secondary = Color(0xFFA9B4CC),
        onSecondary = Color(0xFF0B1222),
        secondaryContainer = Color(0xFF1E2842),
        onSecondaryContainer = Color(0xFFDCE3F2),
        tertiary = if (accent == Accent.TEAL) Color(0xFF6D7CFF) else Color(0xFF2DD4BF),
        onTertiary = Color(0xFF06121F),
        tertiaryContainer = Color(0xFF123A3A),
        onTertiaryContainer = Color(0xFFB7F5EC),
        background = background,
        onBackground = Color(0xFFE8ECF6),
        surface = background,
        onSurface = Color(0xFFE8ECF6),
        surfaceVariant = Color(0xFF1D2742),
        onSurfaceVariant = Color(0xFFA3ADC2),
        surfaceTint = base,
        inverseSurface = Color(0xFFE8ECF6),
        inverseOnSurface = Color(0xFF111931),
        error = Color(0xFFFF6B81),
        onError = Color(0xFF2A0610),
        errorContainer = Color(0xFF4A1426),
        onErrorContainer = Color(0xFFFFD9DE),
        outline = Color(0xFF56607A),
        outlineVariant = Color(0xFF263049),
        scrim = Color.Black,
        surfaceBright = Color(0xFF232D4A),
        surfaceDim = background,
        surfaceContainerLowest = Color(0xFF050914),
        surfaceContainerLow = Color(0xFF0C1324),
        surfaceContainer = Color(0xFF111931),
        surfaceContainerHigh = Color(0xFF172039),
        surfaceContainerHighest = Color(0xFF1D2742),
    )
}

private fun lightScheme(accent: Accent): ColorScheme {
    val base = Color(accent.base)
    val deep = Color(accent.deep)
    val background = Color(0xFFF3F5FA)
    return lightColorScheme(
        primary = deep,
        onPrimary = Color.White,
        primaryContainer = lerp(base, Color.White, 0.8f),
        onPrimaryContainer = lerp(deep, Color.Black, 0.45f),
        inversePrimary = base,
        secondary = Color(0xFF5B6478),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE1E6F0),
        onSecondaryContainer = Color(0xFF1A2235),
        tertiary = if (accent == Accent.TEAL) Color(0xFF4F5BD5) else Color(0xFF0D9488),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFCCF3EE),
        onTertiaryContainer = Color(0xFF053B36),
        background = background,
        onBackground = Color(0xFF0F172A),
        surface = background,
        onSurface = Color(0xFF0F172A),
        surfaceVariant = Color(0xFFE3E7EF),
        onSurfaceVariant = Color(0xFF5B6478),
        surfaceTint = deep,
        inverseSurface = Color(0xFF1A2235),
        inverseOnSurface = Color(0xFFEFF2F8),
        error = Color(0xFFE11D48),
        onError = Color.White,
        errorContainer = Color(0xFFFFE0E6),
        onErrorContainer = Color(0xFF5B0A1F),
        outline = Color(0xFF8A93A6),
        outlineVariant = Color(0xFFD5DAE4),
        scrim = Color.Black,
        surfaceBright = Color.White,
        surfaceDim = Color(0xFFDDE2EB),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF8F9FC),
        surfaceContainer = Color(0xFFEDF0F6),
        surfaceContainerHigh = Color(0xFFE7EBF2),
        surfaceContainerHighest = Color(0xFFE1E6EE),
    )
}

private fun extendedColors(accent: Accent, dark: Boolean): MesOSColors =
    if (dark) {
        MesOSColors(
            card = Color(0xFF111931),
            separator = Color(0xFF1E2842),
            dim = Color(0xFF8C97AE),
            success = Color(0xFF34D399),
            warning = Color(0xFFFBBF24),
            danger = Color(0xFFFB7185),
            accentBright = Color(accent.base),
            isDark = true,
        )
    } else {
        MesOSColors(
            card = Color.White,
            separator = Color(0xFFEEF1F6),
            dim = Color(0xFF64748B),
            success = Color(0xFF059669),
            warning = Color(0xFFD97706),
            danger = Color(0xFFE11D48),
            accentBright = Color(accent.base),
            isDark = false,
        )
    }

private val MesOSShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun MesOSTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: Accent = Accent.INDIGO,
    content: @Composable () -> Unit,
) {
    val colorScheme = remember(accent, darkTheme) { if (darkTheme) darkScheme(accent) else lightScheme(accent) }
    val extended = remember(accent, darkTheme) { extendedColors(accent, darkTheme) }
    CompositionLocalProvider(LocalMesOSColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MesOSTypography,
            shapes = MesOSShapes,
            content = content,
        )
    }
}

/** Whether the MesOS appearance the user picked is dark right now. */
@Composable
fun rememberMesOSDarkTheme(): Boolean {
    val context = LocalContext.current
    val mode by remember(context) { MesOSPreferences.get(context) }.themeMode.collectAsState()
    return when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}

/**
 * [MesOSTheme] driven by the appearance and accent the user picked in MesOS
 * Settings. Also keeps the status/navigation bar icons readable.
 *
 * [forceDark] is for screens that are always dark (camera viewfinder, in-call).
 */
@Composable
fun MesOSUserTheme(forceDark: Boolean = false, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) { MesOSPreferences.get(context) }
    val accent by preferences.accent.collectAsState()
    val darkTheme = forceDark || rememberMesOSDarkTheme()

    val activity = LocalActivity.current as? ComponentActivity
    LaunchedEffect(activity, darkTheme) {
        activity?.enableEdgeToEdge(
            statusBarStyle = systemBarStyle(darkTheme),
            navigationBarStyle = systemBarStyle(darkTheme),
        )
    }

    MesOSTheme(darkTheme = darkTheme, accent = accent, content = content)
}

private fun systemBarStyle(darkTheme: Boolean): SystemBarStyle =
    if (darkTheme) {
        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
    } else {
        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
    }
