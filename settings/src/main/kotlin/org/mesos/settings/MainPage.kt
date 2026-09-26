package org.mesos.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mesos.core.MesOSApps
import org.mesos.core.MesOSRelease
import org.mesos.core.system.HomeRole
import org.mesos.core.text.SearchText
import org.mesos.core.ui.CountBadge
import org.mesos.core.ui.EmptyState
import org.mesos.core.ui.GroupDivider
import org.mesos.core.ui.ListGroup
import org.mesos.core.ui.ListRow
import org.mesos.core.ui.MesOSCard
import org.mesos.core.ui.MesOSGlyphs
import org.mesos.core.ui.MesOSMark
import org.mesos.core.ui.MesOSPalette
import org.mesos.core.ui.MesOSSearchField
import org.mesos.core.ui.OnResume
import org.mesos.core.ui.theme.MesOSTheme
import org.mesos.updater.UpdateController
import org.mesos.updater.UpdateState

/** One row of the main page (or a search-only shortcut to a setting deeper inside). */
private class Entry(
    val key: String,
    val title: String,
    val subtitle: String?,
    val icon: ImageVector,
    val color: Color,
    val keywords: String = "",
    /** Opens an Android settings screen rather than a MesOS page. */
    val android: Boolean = false,
    val badge: Int = 0,
    val onClick: () -> Unit,
) {
    fun score(query: String): Int =
        maxOf(SearchText.score(query, title), SearchText.score(query, keywords) - 50, SearchText.score(query, subtitle.orEmpty()) - 80)
}

@Composable
internal fun MainPage(nav: SettingsNav) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val state by UpdateController.state.collectAsState()
    val updateBadge = if (state is UpdateState.Available) 1 else 0
    val summary = updateSummary()
    val openCare = {
        nav.external(MesOSApps.launchIntent(context, MesOSApps.CARE), Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
    }

    val groups = listOf(
        listOf(
            Entry(
                "network", stringResource(R.string.settings_network), stringResource(R.string.settings_network_summary),
                MesOSGlyphs.Wifi, MesOSPalette.Blue, stringResource(R.string.settings_network_keywords), android = true,
            ) { nav.external(Intent(Settings.ACTION_WIRELESS_SETTINGS)) },
            Entry(
                "devices", stringResource(R.string.settings_devices), stringResource(R.string.settings_devices_summary),
                MesOSGlyphs.Bluetooth, MesOSPalette.Sky, stringResource(R.string.settings_devices_keywords), android = true,
            ) { nav.external(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
        ),
        listOf(
            Entry(
                "appearance", stringResource(R.string.settings_appearance), stringResource(R.string.settings_appearance_summary),
                MesOSGlyphs.Palette, MesOSPalette.Violet, stringResource(R.string.settings_appearance_keywords),
            ) { nav.open(Page.APPEARANCE) },
            Entry(
                "home", stringResource(R.string.settings_home), stringResource(R.string.settings_home_summary),
                MesOSGlyphs.Home, MesOSPalette.Indigo, stringResource(R.string.settings_home_keywords),
            ) { nav.open(Page.HOME) },
            Entry(
                "notifications", stringResource(R.string.settings_notifications), stringResource(R.string.settings_notifications_summary),
                MesOSGlyphs.Bell, MesOSPalette.Rose, stringResource(R.string.settings_notifications_keywords),
            ) { nav.open(Page.NOTIFICATIONS) },
            Entry(
                "sound", stringResource(R.string.settings_sound), stringResource(R.string.settings_sound_summary),
                MesOSGlyphs.Volume, MesOSPalette.Pink, stringResource(R.string.settings_sound_keywords), android = true,
            ) { nav.external(Intent(Settings.ACTION_SOUND_SETTINGS)) },
        ),
        listOf(
            Entry(
                "apps", stringResource(R.string.settings_apps), stringResource(R.string.settings_apps_summary),
                MesOSGlyphs.Apps, MesOSPalette.Teal, stringResource(R.string.settings_apps_keywords),
            ) { nav.open(Page.APPS) },
            Entry(
                "care", stringResource(R.string.settings_care), stringResource(R.string.settings_care_summary),
                MesOSGlyphs.Battery, MesOSPalette.Green, stringResource(R.string.settings_care_keywords),
                onClick = openCare,
            ),
            Entry(
                "storage", stringResource(R.string.settings_storage), stringResource(R.string.settings_storage_summary),
                MesOSGlyphs.Storage, MesOSPalette.Amber, stringResource(R.string.settings_storage_keywords), android = true,
            ) { nav.external(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) },
        ),
        listOf(
            Entry(
                "privacy", stringResource(R.string.settings_privacy), stringResource(R.string.settings_privacy_summary),
                MesOSGlyphs.Shield, MesOSPalette.Slate, stringResource(R.string.settings_privacy_keywords),
            ) { nav.open(Page.PRIVACY) },
            Entry(
                "language", stringResource(R.string.settings_language), stringResource(R.string.settings_language_summary),
                MesOSGlyphs.Globe, MesOSPalette.Orange, stringResource(R.string.settings_language_keywords),
            ) { nav.open(Page.LANGUAGE) },
        ),
        listOf(
            Entry(
                "update", stringResource(R.string.settings_update), summary,
                MesOSGlyphs.Download, MesOSPalette.Indigo, stringResource(R.string.settings_update_keywords), badge = updateBadge,
            ) { nav.open(Page.UPDATE) },
            Entry(
                "system", stringResource(R.string.settings_system), stringResource(R.string.settings_system_summary),
                MesOSGlyphs.Settings, MesOSPalette.Slate, stringResource(R.string.settings_system_keywords),
            ) { nav.open(Page.SYSTEM) },
            Entry(
                "about", stringResource(R.string.settings_about), MesOSRelease.current.displayName + " · Android " + Build.VERSION.RELEASE,
                MesOSGlyphs.Info, MesOSPalette.Blue, stringResource(R.string.settings_about_keywords),
            ) { nav.open(Page.ABOUT) },
        ),
    )

    // Settings that live inside pages, so search finds them by name.
    val shortcuts = listOf(
        Entry("wallpaper", stringResource(R.string.appearance_wallpaper), stringResource(R.string.settings_appearance), MesOSGlyphs.Image, MesOSPalette.Violet) { nav.open(Page.APPEARANCE) },
        Entry("accent", stringResource(R.string.appearance_accent), stringResource(R.string.settings_appearance), MesOSGlyphs.Palette, MesOSPalette.Violet) { nav.open(Page.APPEARANCE) },
        Entry("theme", stringResource(R.string.appearance_theme), stringResource(R.string.settings_appearance), MesOSGlyphs.Contrast, MesOSPalette.Violet, stringResource(R.string.appearance_theme_keywords)) { nav.open(Page.APPEARANCE) },
        Entry("icons", stringResource(R.string.appearance_icon_shape), stringResource(R.string.settings_appearance), MesOSGlyphs.Grid, MesOSPalette.Violet) { nav.open(Page.APPEARANCE) },
        Entry("badges", stringResource(R.string.home_badges), stringResource(R.string.settings_home), MesOSGlyphs.Bell, MesOSPalette.Indigo) { nav.open(Page.HOME) },
        Entry("default-home", stringResource(R.string.home_default), stringResource(R.string.settings_home), MesOSGlyphs.Home, MesOSPalette.Indigo) { nav.open(Page.HOME) },
        Entry("dnd", stringResource(R.string.notifications_dnd), stringResource(R.string.settings_notifications), MesOSGlyphs.Moon, MesOSPalette.Rose) { nav.open(Page.NOTIFICATIONS) },
        Entry("auto-update", stringResource(R.string.update_auto_check), stringResource(R.string.settings_update), MesOSGlyphs.Refresh, MesOSPalette.Indigo) { nav.open(Page.UPDATE) },
        Entry("permissions", stringResource(R.string.privacy_app_permissions), stringResource(R.string.settings_privacy), MesOSGlyphs.Lock, MesOSPalette.Slate) { nav.open(Page.PRIVACY) },
        Entry("licenses", stringResource(R.string.settings_licenses), stringResource(R.string.settings_about), MesOSGlyphs.Info, MesOSPalette.Blue) { nav.open(Page.LICENSES) },
        Entry("setup", stringResource(R.string.system_run_setup), stringResource(R.string.settings_system), MesOSGlyphs.Sparkle, MesOSPalette.Indigo) {
            nav.external(MesOSApps.launchIntent(context, MesOSApps.SETUP))
        },
        Entry("display", stringResource(R.string.appearance_android_display), stringResource(R.string.settings_android_tag), MesOSGlyphs.Sun, MesOSPalette.Amber, stringResource(R.string.appearance_android_display_keywords), android = true) {
            nav.external(Intent(Settings.ACTION_DISPLAY_SETTINGS))
        },
        Entry("wifi", "Wi‑Fi", stringResource(R.string.settings_android_tag), MesOSGlyphs.Wifi, MesOSPalette.Blue, android = true) {
            nav.external(Intent(Settings.ACTION_WIFI_SETTINGS))
        },
        Entry("location", stringResource(R.string.privacy_location_settings), stringResource(R.string.settings_android_tag), MesOSGlyphs.Location, MesOSPalette.Slate, android = true) {
            nav.external(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        },
        Entry("date", stringResource(R.string.system_date_time), stringResource(R.string.settings_android_tag), MesOSGlyphs.Clock, MesOSPalette.Slate, android = true) {
            nav.external(Intent(Settings.ACTION_DATE_SETTINGS))
        },
        Entry("accessibility", stringResource(R.string.system_accessibility), stringResource(R.string.settings_android_tag), MesOSGlyphs.Person, MesOSPalette.Slate, android = true) {
            nav.external(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        },
        Entry("keyboard", stringResource(R.string.language_keyboard), stringResource(R.string.settings_android_tag), MesOSGlyphs.Keypad, MesOSPalette.Orange, android = true) {
            nav.external(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        },
        Entry("default-apps", stringResource(R.string.apps_default_apps), stringResource(R.string.settings_android_tag), MesOSGlyphs.Apps, MesOSPalette.Teal, android = true) {
            nav.external(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        },
    )

    val results = if (query.isBlank()) {
        emptyList()
    } else {
        (groups.flatten() + shortcuts)
            .map { it to it.score(query) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }
    val androidTag = stringResource(R.string.settings_android_tag)

    SettingsPage(Page.MAIN, nav, canLeave = false) {
        item(key = "search") {
            MesOSSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.settings_search_hint),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotBlank()) {
            item(key = "results") {
                if (results.isEmpty()) {
                    EmptyState(
                        icon = MesOSGlyphs.Search,
                        title = stringResource(R.string.settings_search_empty_title),
                        message = stringResource(R.string.settings_search_empty, query),
                    )
                } else {
                    ListGroup {
                        results.forEachIndexed { index, entry ->
                            if (index > 0) GroupDivider()
                            EntryRow(entry, androidTag)
                        }
                    }
                }
            }
            return@SettingsPage
        }
        item(key = "mesos") { MesOSHeroCard(summary = summary, badge = updateBadge, onClick = { nav.open(Page.UPDATE) }) }
        item(key = "set-home") { SetHomeBanner() }
        groups.forEachIndexed { index, group ->
            item(key = "group-$index") {
                ListGroup {
                    group.forEachIndexed { rowIndex, entry ->
                        if (rowIndex > 0) GroupDivider()
                        EntryRow(entry, androidTag)
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: Entry, androidTag: String) {
    ListRow(
        title = entry.title,
        subtitle = entry.subtitle,
        icon = entry.icon,
        iconColor = entry.color,
        value = if (entry.android) androidTag else null,
        badge = entry.badge,
        onClick = entry.onClick,
    )
}

@Composable
private fun MesOSHeroCard(summary: String, badge: Int, onClick: () -> Unit) {
    MesOSCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MesOSMark(size = 52.dp)
            Column(Modifier.weight(1f)) {
                Text(MesOSRelease.current.displayName, style = MaterialTheme.typography.titleLarge)
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = MesOSTheme.colors.dim)
            }
            if (badge > 0) CountBadge(badge)
        }
    }
}

/** Shown while another app is the default home app. */
@Composable
private fun SetHomeBanner() {
    val context = LocalContext.current
    var isHome by remember { mutableStateOf(HomeRole.isMesOSDefaultHome(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isHome = HomeRole.isMesOSDefaultHome(context)
    }
    OnResume { isHome = HomeRole.isMesOSDefaultHome(context) }
    if (isHome) return

    MesOSCard(color = MaterialTheme.colorScheme.primaryContainer) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.settings_set_home_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.settings_set_home_summary), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = {
                try {
                    launcher.launch(HomeRole.requestIntent(context))
                } catch (e: ActivityNotFoundException) {
                    isHome = HomeRole.isMesOSDefaultHome(context)
                }
            }) {
                Text(stringResource(R.string.settings_set_home_action))
            }
        }
    }
}
