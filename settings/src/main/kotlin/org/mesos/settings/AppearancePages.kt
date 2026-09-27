package org.mesos.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.mesos.core.prefs.Accent
import org.mesos.core.prefs.IconShape
import org.mesos.core.prefs.MesOSPreferences
import org.mesos.core.prefs.ThemeMode
import org.mesos.core.prefs.Wallpaper
import org.mesos.core.system.HomeRole
import org.mesos.core.ui.GroupDivider
import org.mesos.core.ui.GroupLabel
import org.mesos.core.ui.ListGroup
import org.mesos.core.ui.ListRow
import org.mesos.core.ui.MesOSCard
import org.mesos.core.ui.MesOSGlyphs
import org.mesos.core.ui.MesOSPalette
import org.mesos.core.ui.OnResume
import org.mesos.core.ui.SquircleShape
import org.mesos.core.ui.SwitchRow
import org.mesos.core.ui.theme.MesOSTheme
import org.mesos.core.ui.wallpaper.AuroraLiveWallpaper
import org.mesos.core.ui.wallpaper.WallpaperLayer

@Composable
internal fun AppearancePage(nav: SettingsNav, canLeave: Boolean) {
    val context = LocalContext.current
    val preferences = remember(context) { MesOSPreferences.get(context) }
    val theme by preferences.themeMode.collectAsState()
    val accent by preferences.accent.collectAsState()
    val wallpaper by preferences.wallpaper.collectAsState()
    val iconShape by preferences.iconShape.collectAsState()
    val themedIcons by preferences.themedIcons.collectAsState()
    var liveActive by remember { mutableStateOf(AuroraLiveWallpaper.isActive(context)) }
    OnResume { liveActive = AuroraLiveWallpaper.isActive(context) }
    val androidTag = stringResource(R.string.settings_android_tag)

    SettingsPage(Page.APPEARANCE, nav, canLeave) {
        item(key = "theme") {
            Column {
                GroupLabel(stringResource(R.string.appearance_theme))
                MesOSCard { ThemePicker(theme, preferences::setThemeMode) }
            }
        }
        item(key = "accent") {
            Column {
                GroupLabel(stringResource(R.string.appearance_accent))
                MesOSCard { AccentPicker(accent, preferences::setAccent) }
            }
        }
        item(key = "wallpaper") {
            Column {
                GroupLabel(stringResource(R.string.appearance_wallpaper))
                MesOSCard {
                    WallpaperPicker(wallpaper, accent, preferences::setWallpaper)
                }
            }
        }
        item(key = "live") {
            ListGroup {
                ListRow(
                    title = stringResource(R.string.appearance_live_wallpaper),
                    subtitle = stringResource(
                        if (liveActive) R.string.appearance_live_wallpaper_on else R.string.appearance_live_wallpaper_summary,
                    ),
                    icon = MesOSGlyphs.Lock,
                    iconColor = MesOSPalette.Indigo,
                    trailing = { StatusPill(liveActive) },
                    onClick = {
                        nav.external(
                            if (liveActive) Intent(Intent.ACTION_SET_WALLPAPER) else AuroraLiveWallpaper.chooserIntent(context),
                        )
                    },
                )
            }
        }
        item(key = "icons") {
            Column {
                GroupLabel(stringResource(R.string.appearance_icon_shape))
                MesOSCard { IconShapePicker(iconShape, accent, preferences::setIconShape) }
            }
        }
        item(key = "themed") {
            val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            ListGroup {
                SwitchRow(
                    title = stringResource(R.string.appearance_themed_icons),
                    subtitle = stringResource(
                        if (supported) R.string.appearance_themed_icons_summary else R.string.appearance_themed_icons_unsupported,
                    ),
                    checked = themedIcons && supported,
                    onCheckedChange = preferences::setThemedIcons,
                    icon = MesOSGlyphs.Sparkle,
                    iconColor = MesOSPalette.Violet,
                    enabled = supported,
                )
            }
        }
        item(key = "android") {
            ListGroup {
                ListRow(
                    title = stringResource(R.string.appearance_android_display),
                    subtitle = stringResource(R.string.appearance_android_display_summary),
                    icon = MesOSGlyphs.Sun,
                    iconColor = MesOSPalette.Amber,
                    value = androidTag,
                    onClick = { nav.external(Intent(Settings.ACTION_DISPLAY_SETTINGS)) },
                )
            }
        }
    }
}

@Composable
internal fun HomePage(nav: SettingsNav, canLeave: Boolean) {
    val context = LocalContext.current
    val preferences = remember(context) { MesOSPreferences.get(context) }
    val badges by preferences.notificationBadges.collectAsState()
    val showAndroidApps by preferences.showAndroidApps.collectAsState()
    var isHome by remember { mutableStateOf(HomeRole.isMesOSDefaultHome(context)) }
    var listenerOn by remember { mutableStateOf(SpecialAccess.notificationListener(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isHome = HomeRole.isMesOSDefaultHome(context)
    }
    OnResume {
        isHome = HomeRole.isMesOSDefaultHome(context)
        listenerOn = SpecialAccess.notificationListener(context)
    }

    SettingsPage(Page.HOME, nav, canLeave) {
        item(key = "default") {
            ListGroup {
                ListRow(
                    title = stringResource(R.string.home_default),
                    subtitle = stringResource(if (isHome) R.string.home_default_on else R.string.home_default_off),
                    icon = MesOSGlyphs.Home,
                    iconColor = MesOSPalette.Indigo,
                    trailing = { StatusPill(isHome, onText = stringResource(R.string.home_default_mesos)) },
                    onClick = {
                        if (isHome) {
                            nav.external(Intent(Settings.ACTION_HOME_SETTINGS))
                        } else {
                            try {
                                launcher.launch(HomeRole.requestIntent(context))
                            } catch (e: ActivityNotFoundException) {
                                nav.external(Intent(Settings.ACTION_HOME_SETTINGS))
                            }
                        }
                    },
                )
            }
        }
        item(key = "switches") {
            ListGroup {
                SwitchRow(
                    title = stringResource(R.string.home_badges),
                    subtitle = stringResource(if (listenerOn) R.string.home_badges_summary else R.string.home_badges_needs_access),
                    checked = badges,
                    onCheckedChange = preferences::setNotificationBadges,
                    icon = MesOSGlyphs.Bell,
                    iconColor = MesOSPalette.Rose,
                )
                GroupDivider()
                SwitchRow(
                    title = stringResource(R.string.settings_show_android_apps),
                    subtitle = stringResource(R.string.settings_show_android_apps_summary),
                    checked = showAndroidApps,
                    onCheckedChange = preferences::setShowAndroidApps,
                    icon = MesOSGlyphs.Apps,
                    iconColor = MesOSPalette.Teal,
                )
            }
        }
        item(key = "style") {
            ListGroup {
                ListRow(
                    title = stringResource(R.string.home_style),
                    subtitle = stringResource(R.string.settings_appearance_summary),
                    icon = MesOSGlyphs.Palette,
                    iconColor = MesOSPalette.Violet,
                    onClick = { nav.open(Page.APPEARANCE) },
                )
            }
        }
        item(key = "tips") {
            Column {
                GroupLabel(stringResource(R.string.home_gestures))
                MesOSCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        GestureTip(MesOSGlyphs.ChevronDown, stringResource(R.string.home_gesture_control))
                        GestureTip(MesOSGlyphs.ChevronUp, stringResource(R.string.home_gesture_drawer))
                        GestureTip(MesOSGlyphs.Edit, stringResource(R.string.home_gesture_edit))
                        GestureTip(MesOSGlyphs.Widgets, stringResource(R.string.home_gesture_widgets))
                    }
                }
            }
        }
    }
}

@Composable
private fun GestureTip(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

// ---- Pickers, shared with the setup wizard ----

/** System / light / dark, each with a small preview. */
@Composable
internal fun ThemePicker(selected: ThemeMode, onSelect: (ThemeMode) -> Unit, labelColor: Color = Color.Unspecified) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ThemeMode.entries.forEach { mode ->
            val isSelected = mode == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(mode) }
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemePreview(mode, isSelected)
                Text(
                    text = stringResource(
                        when (mode) {
                            ThemeMode.SYSTEM -> R.string.appearance_theme_system
                            ThemeMode.LIGHT -> R.string.appearance_theme_light
                            ThemeMode.DARK -> R.string.appearance_theme_dark
                        },
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else labelColor,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ThemePreview(mode: ThemeMode, selected: Boolean) {
    val light = Color(0xFFF3F5FA)
    val dark = Color(0xFF070C1A)
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.78f)
            .clip(shape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MesOSTheme.colors.separator,
                shape = shape,
            ),
    ) {
        when (mode) {
            ThemeMode.LIGHT -> PreviewHalf(light, Color.White, Color(0xFF0F172A), Modifier.fillMaxSize())
            ThemeMode.DARK -> PreviewHalf(dark, Color(0xFF141B2E), Color.White, Modifier.fillMaxSize())
            ThemeMode.SYSTEM -> Row(Modifier.fillMaxSize()) {
                PreviewHalf(light, Color.White, Color(0xFF0F172A), Modifier.weight(1f).fillMaxHeight())
                PreviewHalf(dark, Color(0xFF141B2E), Color.White, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun PreviewHalf(background: Color, card: Color, ink: Color, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(background)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.6f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(ink.copy(alpha = 0.8f)),
        )
        repeat(2) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(card),
            )
        }
        Box(
            Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/** The MesOS accent colours as round swatches. */
@Composable
internal fun AccentPicker(selected: Accent, onSelect: (Accent) -> Unit) {
    val dark = MesOSTheme.colors.isDark
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Accent.entries.forEach { accent ->
            val color = Color(if (dark) accent.base else accent.deep)
            val isSelected = accent == selected
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(accent) }
                    .then(if (isSelected) Modifier.border(3.dp, color.copy(alpha = 0.45f), CircleShape).padding(5.dp) else Modifier)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(MesOSGlyphs.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** Wallpaper thumbnails drawn by the real Aurora renderer (still frames). */
@Composable
internal fun WallpaperPicker(
    selected: Wallpaper,
    accent: Accent,
    onSelect: (Wallpaper) -> Unit,
    labelColor: Color = Color.Unspecified,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = contentPadding,
    ) {
        items(Wallpaper.entries, key = { it.id }) { wallpaper ->
            val isSelected = wallpaper == selected
            val shape = RoundedCornerShape(16.dp)
            Column(
                modifier = Modifier
                    .width(86.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(wallpaper) }
                    .padding(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.48f)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.16f),
                            shape = shape,
                        )
                        .padding(if (isSelected) 4.dp else 1.dp)
                        .clip(RoundedCornerShape(if (isSelected) 12.dp else 15.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (wallpaper == Wallpaper.SYSTEM) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Brush.verticalGradient(listOf(Color(0xFF334155), Color(0xFF0F172A)))),
                        )
                        Icon(MesOSGlyphs.Image, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(28.dp))
                    } else {
                        val still = if (wallpaper == Wallpaper.AURORA) Wallpaper.AURORA_STILL else wallpaper
                        WallpaperLayer(wallpaper = still, accent = accent, modifier = Modifier.fillMaxSize())
                        if (wallpaper == Wallpaper.AURORA) {
                            Text(
                                text = stringResource(R.string.appearance_wallpaper_live),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 8.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.Black.copy(alpha = 0.35f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(wallpaperName(wallpaper)),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else labelColor,
                    maxLines = 1,
                )
            }
        }
    }
}

internal fun wallpaperName(wallpaper: Wallpaper): Int = when (wallpaper) {
    Wallpaper.AURORA -> R.string.wallpaper_aurora
    Wallpaper.AURORA_STILL -> R.string.wallpaper_aurora_still
    Wallpaper.DAWN -> R.string.wallpaper_dawn
    Wallpaper.OCEAN -> R.string.wallpaper_ocean
    Wallpaper.GRAPHITE -> R.string.wallpaper_graphite
    Wallpaper.SYSTEM -> R.string.wallpaper_system
}

/** Squircle, circle and rounded square, previewed in the accent colour. */
@Composable
internal fun IconShapePicker(selected: IconShape, accent: Accent, onSelect: (IconShape) -> Unit, labelColor: Color = Color.Unspecified) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconShape.entries.forEach { shape ->
            val isSelected = shape == selected
            val outline: Shape = when (shape) {
                IconShape.SQUIRCLE -> SquircleShape
                IconShape.CIRCLE -> CircleShape
                IconShape.ROUNDED -> RoundedCornerShape(24)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(shape) }
                    .then(
                        if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)) else Modifier,
                    )
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(outline)
                        .background(Brush.linearGradient(listOf(Color(accent.base), Color(accent.deep)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(MesOSGlyphs.Camera, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
                Text(
                    text = stringResource(
                        when (shape) {
                            IconShape.SQUIRCLE -> R.string.appearance_shape_squircle
                            IconShape.CIRCLE -> R.string.appearance_shape_circle
                            IconShape.ROUNDED -> R.string.appearance_shape_rounded
                        },
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else labelColor,
                )
            }
        }
    }
}
