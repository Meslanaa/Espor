package org.mesos.settings

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.LocaleManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.LocaleList
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mesos.core.prefs.MesOSPreferences
import org.mesos.core.ui.GroupDivider
import org.mesos.core.ui.GroupLabel
import org.mesos.core.ui.ListGroup
import org.mesos.core.ui.ListRow
import org.mesos.core.ui.MesOSCard
import org.mesos.core.ui.MesOSGlyphs
import org.mesos.core.ui.MesOSPalette
import org.mesos.core.ui.OnResume
import org.mesos.core.ui.RadioRow
import org.mesos.core.ui.SwitchRow
import org.mesos.core.ui.appDetailsIntent
import org.mesos.core.ui.hasPermission
import org.mesos.core.ui.startActivitySafely
import org.mesos.updater.UpdateController

/** Android "special access" MesOS features rely on; each is granted by the user in Android. */
internal object SpecialAccess {
    private const val LISTENER = "org.mesos.launcher.control.MesOSNotificationService"

    private fun listener(context: Context) = ComponentName(context.packageName, LISTENER)

    private fun packageUri(context: Context): Uri = Uri.parse("package:${context.packageName}")

    fun notificationListener(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            return context.getSystemService(NotificationManager::class.java)
                ?.isNotificationListenerAccessGranted(listener(context)) == true
        }
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == listener(context) }
    }

    fun notificationListenerIntents(context: Context): Array<Intent> {
        val list = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return arrayOf(list)
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listener(context).flattenToString())
        return arrayOf(detail, list)
    }

    fun postNotifications(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() == true &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || context.hasPermission(Manifest.permission.POST_NOTIFICATIONS))

    fun appNotificationSettings(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun doNotDisturb(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true

    fun writeSettings(context: Context): Boolean = Settings.System.canWrite(context)

    fun writeSettingsIntent(context: Context) = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, packageUri(context))

    fun allFiles(context: Context): Boolean? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            Manifest.permission.MANAGE_EXTERNAL_STORAGE in declaredPermissions(context)
        ) {
            Environment.isExternalStorageManager()
        } else {
            null
        }

    fun allFilesIntents(context: Context): Array<Intent> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            arrayOf(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri(context)),
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
            )
        } else {
            arrayOf(appDetailsIntent(context))
        }

    fun installPackages(context: Context): Boolean = UpdateController.canInstallPackages(context)

    fun exactAlarms(context: Context): Boolean? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
        } else {
            null
        }

    fun exactAlarmsIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri(context))
        } else {
            appDetailsIntent(context)
        }

    fun fullScreen(context: Context): Boolean? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true
        } else {
            null
        }

    fun fullScreenIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri(context))
        } else {
            appDetailsIntent(context)
        }

    /** Permissions the MesOS package declares (only those can be requested). */
    fun declaredPermissions(context: Context): Set<String> =
        try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            }
            info.requestedPermissions?.toSet().orEmpty()
        } catch (e: PackageManager.NameNotFoundException) {
            emptySet()
        }
}

/** A group of Android runtime permissions shown as one row ("Camera", "Location"…). */
internal class PermissionSpec(
    val key: String,
    val title: Int,
    val summary: Int,
    val icon: ImageVector,
    val color: Color,
    val permissions: List<String>,
) {
    fun isGranted(context: Context): Boolean = permissions.any(context::hasPermission)
}

/** The permission groups MesOS uses, limited to permissions the package declares. */
internal fun permissionSpecs(context: Context): List<PermissionSpec> {
    val declared = SpecialAccess.declaredPermissions(context)
    val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val all = buildList {
        if (modern) {
            add(PermissionSpec("notifications", R.string.permission_notifications, R.string.permission_notifications_summary, MesOSGlyphs.Bell, MesOSPalette.Rose, listOf(Manifest.permission.POST_NOTIFICATIONS)))
        }
        add(PermissionSpec("location", R.string.permission_location, R.string.permission_location_summary, MesOSGlyphs.Location, MesOSPalette.Sky, listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)))
        if (modern) {
            add(PermissionSpec("photos", R.string.permission_photos, R.string.permission_photos_summary, MesOSGlyphs.Image, MesOSPalette.Pink, listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)))
            add(PermissionSpec("music", R.string.permission_music, R.string.permission_music_summary, MesOSGlyphs.Music, MesOSPalette.Orange, listOf(Manifest.permission.READ_MEDIA_AUDIO)))
        } else {
            add(PermissionSpec("storage", R.string.permission_storage, R.string.permission_storage_summary, MesOSGlyphs.Folder, MesOSPalette.Amber, listOf(Manifest.permission.READ_EXTERNAL_STORAGE)))
        }
        add(PermissionSpec("camera", R.string.permission_camera, R.string.permission_camera_summary, MesOSGlyphs.Camera, MesOSPalette.Slate, listOf(Manifest.permission.CAMERA)))
        add(PermissionSpec("microphone", R.string.permission_microphone, R.string.permission_microphone_summary, MesOSGlyphs.Mic, MesOSPalette.Red, listOf(Manifest.permission.RECORD_AUDIO)))
        add(PermissionSpec("contacts", R.string.permission_contacts, R.string.permission_contacts_summary, MesOSGlyphs.Person, MesOSPalette.Teal, listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)))
        add(PermissionSpec("phone", R.string.permission_phone, R.string.permission_phone_summary, MesOSGlyphs.Phone, MesOSPalette.Green, listOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_PHONE_STATE)))
        add(PermissionSpec("sms", R.string.permission_sms, R.string.permission_sms_summary, MesOSGlyphs.Message, MesOSPalette.Blue, listOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS)))
    }
    return all.mapNotNull { spec ->
        val usable = spec.permissions.filter { it in declared }
        if (usable.isEmpty()) null else PermissionSpec(spec.key, spec.title, spec.summary, spec.icon, spec.color, usable)
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Asks Android for permissions. When Android no longer shows its dialog (the user
 * refused twice), opens MesOS's app page in Android settings instead.
 */
@Composable
internal fun rememberPermissionRequest(onResult: () -> Unit): (List<String>) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<List<String>>(emptyList()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        onResult()
        val activity = context.findActivity()
        val blocked = result.values.none { it } && activity != null &&
            pending.none { activity.shouldShowRequestPermissionRationale(it) }
        if (blocked) context.startActivitySafely(appDetailsIntent(context))
    }
    return { permissions ->
        pending = permissions
        launcher.launch(permissions.toTypedArray())
    }
}

@Composable
internal fun NotificationsPage(nav: SettingsNav, canLeave: Boolean) {
    val context = LocalContext.current
    val preferences = remember(context) { MesOSPreferences.get(context) }
    val badges by preferences.notificationBadges.collectAsState()
    var tick by remember { mutableIntStateOf(0) }
    OnResume { tick++ }
    val request = rememberPermissionRequest { tick++ }
    val listener = remember(tick) { SpecialAccess.notificationListener(context) }
    val post = remember(tick) { SpecialAccess.postNotifications(context) }
    val dnd = remember(tick) { SpecialAccess.doNotDisturb(context) }
    val androidTag = stringResource(R.string.settings_android_tag)

    SettingsPage(Page.NOTIFICATIONS, nav, canLeave) {
        item(key = "access") {
            ListGroup {
                ListRow(
                    title = stringResource(R.string.notifications_access),
                    subtitle = stringResource(R.string.notifications_access_summary),
                    icon = MesOSGlyphs.Bell,
                    iconColor = MesOSPalette.Rose,
                    trailing = { StatusPill(listener) },
                    onClick = { nav.external(*SpecialAccess.notificationListenerIntents(context)) },
                )
                GroupDivider()
                ListRow(
                    title = stringResource(R.string.notifications_post),
                    subtitle = stringResource(R.string.notifications_post_summary),
                    icon = MesOSGlyphs.Message,
                    iconColor = MesOSPalette.Orange,
                    trailing = { StatusPill(post) },
                    onClick = {
                        if (!post && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)
                        ) {
                            request(listOf(Manifest.permission.POST_NOTIFICATIONS))
                        } else {
                            nav.external(SpecialAccess.appNotificationSettings(context), appDetailsIntent(context))
                        }
                    },
                )
                GroupDivider()
                ListRow(
                    title = stringResource(R.string.notifications_dnd),
                    subtitle = stringResource(R.string.notifications_dnd_summary),
                    icon = MesOSGlyphs.Moon,
                    iconColor = MesOSPalette.Violet,
                    trailing = { StatusPill(dnd) },
                    onClick = { nav.external(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) },
                )
            }
        }
        item(key = "badges") {
            ListGroup {
                SwitchRow(
                    title = stringResource(R.string.home_badges),
                    subtitle = stringResource(if (listener) R.string.home_badges_summary else R.string.home_badges_needs_access),
                    checked = badges,
                    onCheckedChange = preferences::setNotificationBadges,
                    icon = MesOSGlyphs.Apps,
                    iconColor = MesOSPalette.Indigo,
                )
            }
        }
        item(key = "android") {
            ListGroup {
                ListRow(
                    title = stringResource(R.string.notifications_android),
                    subtitle = stringResource(R.string.notifications_android_summary),
                    icon = MesOSGlyphs.Settings,
                    iconColor = MesOSPalette.Slate,
                    value = androidTag,
                    onClick = {
                        nav.external(Intent("android.settings.NOTIFICATION_SETTINGS"), Intent(Settings.ACTION_SETTINGS))
                    },
                )
            }
        }
        item(key = "note") { Note(stringResource(R.string.notifications_note)) }
    }
}

private class SpecialRow(
    val key: String,
    val title: Int,
    val summary: Int,
    val icon: ImageVector,
    val color: Color,
    val granted: Boolean,
    val intents: Array<Intent>,
)

@Composable
internal fun PrivacyPage(nav: SettingsNav, canLeave: Boolean) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    OnResume { tick++ }
    val request = rememberPermissionRequest { tick++ }
    val specs = remember { permissionSpecs(context) }
    val granted = remember(tick) { specs.associate { it.key to it.isGranted(context) } }
    val special = remember(tick) {
        buildList {
            add(SpecialRow("listener", R.string.notifications_access, R.string.notifications_access_summary, MesOSGlyphs.Bell, MesOSPalette.Rose, SpecialAccess.notificationListener(context), SpecialAccess.notificationListenerIntents(context)))
            add(SpecialRow("dnd", R.string.notifications_dnd, R.string.notifications_dnd_summary, MesOSGlyphs.Moon, MesOSPalette.Violet, SpecialAccess.doNotDisturb(context), arrayOf(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))))
            add(SpecialRow("write", R.string.privacy_write_settings, R.string.privacy_write_settings_summary, MesOSGlyphs.Sun, MesOSPalette.Amber, SpecialAccess.writeSettings(context), arrayOf(SpecialAccess.writeSettingsIntent(context))))
            SpecialAccess.allFiles(context)?.let { on ->
                add(SpecialRow("files", R.string.privacy_all_files, R.string.privacy_all_files_summary, MesOSGlyphs.Folder, MesOSPalette.Orange, on, SpecialAccess.allFilesIntents(context)))
            }
            add(SpecialRow("install", R.string.privacy_install, R.string.privacy_install_summary, MesOSGlyphs.Download, MesOSPalette.Indigo, SpecialAccess.installPackages(context), arrayOf(UpdateController.installPermissionIntent(context))))
            SpecialAccess.exactAlarms(context)?.let { on ->
                add(SpecialRow("alarms", R.string.privacy_alarms, R.string.privacy_alarms_summary, MesOSGlyphs.Alarm, MesOSPalette.Teal, on, arrayOf(SpecialAccess.exactAlarmsIntent(context))))
            }
            SpecialAccess.fullScreen(context)?.let { on ->
                add(SpecialRow("fullscreen", R.string.privacy_full_screen, R.string.privacy_full_screen_summary, MesOSGlyphs.Clock, MesOSPalette.Green, on, arrayOf(SpecialAccess.fullScreenIntent(context))))
            }
        }
    }
    val allowed = stringResource(R.string.privacy_allowed)
    val notAllowed = stringResource(R.string.privacy_not_allowed)
    val androidTag = stringResource(R.string.settings_android_tag)

    SettingsPage(Page.PRIVACY, nav, canLeave) {
        item(key = "permissions") {
            Column {
                GroupLabel(stringResource(R.string.privacy_app_permissions))
                ListGroup {
                    specs.forEachIndexed { index, spec ->
                        if (index > 0) GroupDivider()
                        val on = granted[spec.key] == true
                        ListRow(
                            title = stringResource(spec.title),
                            subtitle = stringResource(spec.summary),
                            icon = spec.icon,
                            iconColor = spec.color,
                            trailing = { StatusPill(on, onText = allowed, offText = notAllowed) },
                            onClick = {
                                if (on) nav.external(appDetailsIntent(context)) else request(spec.permissions)
                            },
                        )
                    }
                }
            }
        }
        item(key = "special") {
            Column {
                GroupLabel(stringResource(R.string.privacy_special_access))
                ListGroup {
                    special.forEachIndexed { index, row ->
                        if (index > 0) GroupDivider()
                        ListRow(
                            title = stringResource(row.title),
                            subtitle = stringResource(row.summary),
                            icon = row.icon,
                            iconColor = row.color,
                            trailing = { StatusPill(row.granted, onText = allowed, offText = notAllowed) },
                            onClick = { nav.external(*row.intents) },
                        )
                    }
                }
            }
        }
        item(key = "android") {
            ListGroup {
                ListRow(
                    title = stringResource(R.string.privacy_android),
                    icon = MesOSGlyphs.Shield,
                    iconColor = MesOSPalette.Slate,
                    value = androidTag,
                    onClick = { nav.external(Intent(Settings.ACTION_PRIVACY_SETTINGS), Intent(Settings.ACTION_SECURITY_SETTINGS)) },
                )
                GroupDivider()
                ListRow(
                    title = stringResource(R.string.privacy_location_settings),
                    icon = MesOSGlyphs.Location,
                    iconColor = MesOSPalette.Sky,
                    value = androidTag,
                    onClick = { nav.external(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                )
                GroupDivider()
                ListRow(
                    title = stringResource(R.string.privacy_security),
                    icon = MesOSGlyphs.Lock,
                    iconColor = MesOSPalette.Green,
                    value = androidTag,
                    onClick = { nav.external(Intent(Settings.ACTION_SECURITY_SETTINGS)) },
                )
            }
        }
        item(key = "note") { Note(stringResource(R.string.privacy_note)) }
    }
}

/** Languages MesOS is translated into, by their own names. */
private val languages = listOf("tr" to "Türkçe", "en" to "English")

@Composable
internal fun LanguagePage(nav: SettingsNav, canLeave: Boolean) {
    val androidTag = stringResource(R.string.settings_android_tag)

    SettingsPage(Page.LANGUAGE, nav, canLeave) {
        item(key = "mesos") {
            Column {
                GroupLabel(stringResource(R.string.language_mesos))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    LanguageChoices()
                } else {
                    MesOSCard {
                        Text(stringResource(R.string.language_follows_android), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        item(key = "android") {
            ListGroup {
                ListRow(
                    title = stringResource(R.string.language_android),
                    subtitle = stringResource(R.string.language_android_summary),
                    icon = MesOSGlyphs.Globe,
                    iconColor = MesOSPalette.Orange,
                    value = androidTag,
                    onClick = { nav.external(Intent(Settings.ACTION_LOCALE_SETTINGS)) },
                )
                GroupDivider()
                ListRow(
                    title = stringResource(R.string.language_keyboard),
                    subtitle = stringResource(R.string.language_keyboard_summary),
                    icon = MesOSGlyphs.Keypad,
                    iconColor = MesOSPalette.Slate,
                    value = androidTag,
                    onClick = { nav.external(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
                )
            }
        }
        item(key = "note") { Note(stringResource(R.string.language_note)) }
    }
}

/** MesOS's own language (Android 13+ per-app language), shared with the setup wizard. */
@Composable
internal fun LanguageChoices() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(LocaleManager::class.java) }
    var current by remember { mutableStateOf(manager?.applicationLocales?.get(0)?.language.orEmpty()) }
    val choose = { tag: String ->
        current = tag
        manager?.applicationLocales = if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
    }
    ListGroup {
        RadioRow(
            title = stringResource(R.string.language_system),
            subtitle = stringResource(R.string.language_system_summary),
            selected = current.isEmpty(),
            onSelect = { choose("") },
        )
        languages.forEach { (tag, name) ->
            GroupDivider(inset = 16.dp)
            RadioRow(title = name, selected = current == tag, onSelect = { choose(tag) })
        }
    }
}

@Composable
internal fun AppsPage(nav: SettingsNav, canLeave: Boolean) {
    val context = LocalContext.current
    val preferences = remember(context) { MesOSPreferences.get(context) }
    val showAndroidApps by preferences.showAndroidApps.collectAsState()
    val androidTag = stringResource(R.string.settings_android_tag)

    SettingsPage(Page.APPS, nav, canLeave) {
        item(key = "mode") {
            ListGroup {
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
        item(key = "android") {
            ListGroup {
                ListRow(
                    title = stringResource(R.string.apps_default_apps),
                    subtitle = stringResource(R.string.apps_default_apps_summary),
                    icon = MesOSGlyphs.Star,
                    iconColor = MesOSPalette.Amber,
                    value = androidTag,
                    onClick = { nav.external(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) },
                )
                GroupDivider()
                ListRow(
                    title = stringResource(R.string.settings_manage_android_apps),
                    subtitle = stringResource(R.string.apps_manage_summary),
                    icon = MesOSGlyphs.Grid,
                    iconColor = MesOSPalette.Blue,
                    value = androidTag,
                    onClick = { nav.external(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)) },
                )
                GroupDivider()
                ListRow(
                    title = stringResource(R.string.apps_mesos_info),
                    subtitle = stringResource(R.string.apps_mesos_info_summary),
                    icon = MesOSGlyphs.Info,
                    iconColor = MesOSPalette.Indigo,
                    value = androidTag,
                    onClick = { nav.external(appDetailsIntent(context)) },
                )
            }
        }
    }
}
