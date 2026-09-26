package org.mesos.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.mesos.core.MesOSRelease
import org.mesos.core.prefs.MesOSPreferences
import org.mesos.core.prefs.ThemeMode
import org.mesos.core.system.HomeRole
import org.mesos.core.ui.MesOSWordmark
import org.mesos.updater.FailureReason
import org.mesos.updater.UpdateController
import org.mesos.updater.UpdateState

private enum class Page(val parent: Page?) {
    MAIN(null),
    DISPLAY(MAIN),
    APPS(MAIN),
    SYSTEM(MAIN),
    ABOUT(MAIN),
    UPDATE(SYSTEM),
}

@Composable
internal fun SettingsApp(openExternal: (Intent) -> Unit) {
    var page by rememberSaveable { mutableStateOf(Page.MAIN) }
    val navigateUp: () -> Unit = { page.parent?.let { page = it } }
    BackHandler(enabled = page.parent != null, onBack = navigateUp)

    val title = when (page) {
        Page.MAIN -> R.string.settings_title
        Page.DISPLAY -> R.string.settings_display
        Page.APPS -> R.string.settings_apps
        Page.SYSTEM -> R.string.settings_system
        Page.ABOUT -> R.string.settings_about
        Page.UPDATE -> R.string.settings_update
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            Header(title = stringResource(title), onBack = if (page.parent != null) navigateUp else null)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (page) {
                    Page.MAIN -> MainPage(open = { page = it }, openExternal = openExternal)
                    Page.DISPLAY -> DisplayPage(openExternal)
                    Page.APPS -> AppsPage(openExternal)
                    Page.SYSTEM -> SystemPage(openUpdate = { page = Page.UPDATE }, openExternal = openExternal)
                    Page.ABOUT -> AboutPage()
                    Page.UPDATE -> UpdatePage()
                }
            }
        }
    }
}

@Composable
private fun Header(title: String, onBack: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack) { Text("← " + stringResource(R.string.settings_back)) }
        } else {
            Spacer(Modifier.width(16.dp))
        }
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun MainPage(open: (Page) -> Unit, openExternal: (Intent) -> Unit) {
    SetHomeBanner()
    val android = stringResource(R.string.settings_opens_android)
    SettingsRow(stringResource(R.string.settings_network), android) {
        openExternal(Intent(Settings.ACTION_WIRELESS_SETTINGS))
    }
    SettingsRow(stringResource(R.string.settings_display), stringResource(R.string.settings_display_summary)) {
        open(Page.DISPLAY)
    }
    SettingsRow(stringResource(R.string.settings_sound), android) {
        openExternal(Intent(Settings.ACTION_SOUND_SETTINGS))
    }
    SettingsRow(stringResource(R.string.settings_apps), stringResource(R.string.settings_apps_summary)) {
        open(Page.APPS)
    }
    SettingsRow(stringResource(R.string.settings_storage), android) {
        openExternal(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
    }
    SettingsRow(stringResource(R.string.settings_system), stringResource(R.string.settings_system_summary)) {
        open(Page.SYSTEM)
    }
    SettingsRow(stringResource(R.string.settings_about), MesOSRelease.current.displayName) {
        open(Page.ABOUT)
    }
}

@Composable
private fun SetHomeBanner() {
    val context = LocalContext.current
    var isHome by remember { mutableStateOf(HomeRole.isMesOSDefaultHome(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isHome = HomeRole.isMesOSDefaultHome(context)
    }
    if (isHome) return

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

@Composable
private fun DisplayPage(openExternal: (Intent) -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) { MesOSPreferences.get(context) }
    val mode by preferences.themeMode.collectAsState()

    SectionTitle(stringResource(R.string.settings_theme_header))
    ThemeMode.entries.forEach { option ->
        val label = when (option) {
            ThemeMode.SYSTEM -> R.string.settings_theme_system
            ThemeMode.LIGHT -> R.string.settings_theme_light
            ThemeMode.DARK -> R.string.settings_theme_dark
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = mode == option,
                    onClick = { preferences.setThemeMode(option) },
                    role = Role.RadioButton,
                )
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = mode == option, onClick = null)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
        }
    }
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    SettingsRow(stringResource(R.string.settings_android_display), stringResource(R.string.settings_opens_android)) {
        openExternal(Intent(Settings.ACTION_DISPLAY_SETTINGS))
    }
}

@Composable
private fun AppsPage(openExternal: (Intent) -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) { MesOSPreferences.get(context) }
    val showAndroidApps by preferences.showAndroidApps.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = showAndroidApps,
                onValueChange = preferences::setShowAndroidApps,
                role = Role.Switch,
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_show_android_apps), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_show_android_apps_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = showAndroidApps, onCheckedChange = null)
    }
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    SettingsRow(stringResource(R.string.settings_manage_android_apps), stringResource(R.string.settings_opens_android)) {
        openExternal(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS))
    }
}

@Composable
private fun SystemPage(openUpdate: () -> Unit, openExternal: (Intent) -> Unit) {
    SettingsRow(stringResource(R.string.settings_update), updateSummary(), onClick = openUpdate)
    SettingsRow(stringResource(R.string.settings_android_system), stringResource(R.string.settings_opens_android)) {
        openExternal(Intent(Settings.ACTION_SETTINGS))
    }
}

@Composable
private fun AboutPage() {
    val release = MesOSRelease.current
    val unknown = stringResource(R.string.about_unknown)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MesOSWordmark()
        Text(
            text = release.displayVersion,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    InfoRow(stringResource(R.string.about_mesos_version), release.displayVersion)
    InfoRow(
        stringResource(R.string.about_mesos_build),
        stringResource(R.string.about_mesos_build_value, release.buildNumber, release.channel.displayName),
    )
    InfoRow(
        stringResource(R.string.about_android_base),
        stringResource(R.string.about_android_base_value, Build.VERSION.RELEASE, Build.VERSION.SDK_INT),
    )
    InfoRow(stringResource(R.string.about_android_build), Build.DISPLAY.ifBlank { unknown })
    InfoRow(stringResource(R.string.about_security_patch), Build.VERSION.SECURITY_PATCH.ifBlank { unknown })
    InfoRow(stringResource(R.string.about_device), deviceName())
    InfoRow(stringResource(R.string.about_update_status), updateSummary())
    Text(
        text = stringResource(R.string.about_scope_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 12.dp),
    )
}

@Composable
private fun UpdatePage() {
    val context = LocalContext.current
    val state by UpdateController.state.collectAsState()
    val release = MesOSRelease.current

    InfoRow(stringResource(R.string.update_current_version), release.displayVersion)

    UpdateController.updatedFrom?.let { from ->
        NoticeCard {
            Text(
                stringResource(R.string.update_just_updated, from, release.versionName),
                style = MaterialTheme.typography.titleSmall,
            )
            UpdateController.installedReleaseNotes?.takeIf { it.isNotBlank() }?.let { notes ->
                Text(stringResource(R.string.update_whats_new, release.versionName), style = MaterialTheme.typography.labelLarge)
                Text(notes, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    when (val current = state) {
        UpdateState.Idle -> Text(updateSummary(), style = MaterialTheme.typography.bodyMedium)
        UpdateState.Checking -> BusyRow(stringResource(R.string.update_checking))
        is UpdateState.UpToDate -> Text(stringResource(R.string.update_up_to_date), style = MaterialTheme.typography.bodyLarge)
        is UpdateState.Available -> NoticeCard {
            Text(
                stringResource(R.string.update_available_title, current.manifest.versionName),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(
                    R.string.update_available_size,
                    Formatter.formatShortFileSize(context, current.manifest.packageSize),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (current.manifest.releaseNotes.isNotBlank()) {
                Text(stringResource(R.string.update_release_notes), style = MaterialTheme.typography.labelLarge)
                Text(current.manifest.releaseNotes, style = MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = { UpdateController.downloadAndInstall() }) {
                Text(stringResource(R.string.update_download_install))
            }
        }
        is UpdateState.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.update_downloading, (current.progress * 100).toInt()))
            LinearProgressIndicator(progress = { current.progress }, modifier = Modifier.fillMaxWidth())
        }
        is UpdateState.Verifying -> BusyRow(stringResource(R.string.update_verifying))
        is UpdateState.NeedsInstallPermission -> NoticeCard {
            Text(stringResource(R.string.update_permission_needed), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { startSafely(context, UpdateController.installPermissionIntent(context)) }) {
                Text(stringResource(R.string.update_permission_action))
            }
            OutlinedButton(onClick = { UpdateController.downloadAndInstall() }) {
                Text(stringResource(R.string.update_download_install))
            }
        }
        is UpdateState.AwaitingConfirmation -> Text(
            stringResource(R.string.update_awaiting_confirmation),
            style = MaterialTheme.typography.bodyLarge,
        )
        is UpdateState.Installed -> Text(stringResource(R.string.update_installed), style = MaterialTheme.typography.bodyLarge)
        is UpdateState.Failed -> NoticeCard(error = true) {
            Text(stringResource(R.string.update_failed), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(failureMessage(current.reason)), style = MaterialTheme.typography.bodyMedium)
            current.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }

    Button(
        onClick = { UpdateController.check() },
        enabled = !state.isBusy,
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Text(
            stringResource(
                if (state is UpdateState.Failed) R.string.update_try_again else R.string.update_check,
            ),
        )
    }

    Spacer(Modifier.height(16.dp))
    Text(
        stringResource(R.string.update_scope_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        stringResource(R.string.update_source, UpdateController.manifestUrl),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** One-line update status used by System and About MesOS. */
@Composable
private fun updateSummary(): String {
    val context = LocalContext.current
    val state by UpdateController.state.collectAsState()
    val lastCheckedAt by UpdateController.lastCheckedAt.collectAsState()
    val current = state
    return when {
        current is UpdateState.Available -> stringResource(R.string.update_status_available, current.manifest.versionName)
        current is UpdateState.UpToDate -> stringResource(R.string.update_status_up_to_date, formatTime(context, current.checkedAt))
        UpdateController.updatedFrom != null -> stringResource(R.string.update_status_updated_from, UpdateController.updatedFrom!!)
        lastCheckedAt != null -> stringResource(R.string.update_status_last_checked, formatTime(context, lastCheckedAt!!))
        else -> stringResource(R.string.update_status_never)
    }
}

private fun failureMessage(reason: FailureReason): Int = when (reason) {
    FailureReason.NETWORK -> R.string.update_error_network
    FailureReason.NO_RELEASE -> R.string.update_error_no_release
    FailureReason.INVALID_MANIFEST -> R.string.update_error_invalid_manifest
    FailureReason.UNSUPPORTED_MANIFEST -> R.string.update_error_unsupported_manifest
    FailureReason.WRONG_PACKAGE -> R.string.update_error_wrong_package
    FailureReason.WRONG_CHANNEL -> R.string.update_error_wrong_channel
    FailureReason.INSECURE_URL -> R.string.update_error_insecure_url
    FailureReason.NOT_SUPPORTED_FROM_INSTALLED -> R.string.update_error_not_supported_from_installed
    FailureReason.TOO_LARGE -> R.string.update_error_too_large
    FailureReason.CHECKSUM_MISMATCH -> R.string.update_error_checksum
    FailureReason.PACKAGE_MISMATCH -> R.string.update_error_package_mismatch
    FailureReason.SIGNATURE_MISMATCH -> R.string.update_error_signature
    FailureReason.INSTALL_FAILED -> R.string.update_error_install_failed
    FailureReason.INSTALL_CANCELLED -> R.string.update_error_install_cancelled
    FailureReason.STORAGE -> R.string.update_error_storage
}

private fun formatTime(context: Context, millis: Long): String =
    DateUtils.formatDateTime(
        context,
        millis,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
    )

private fun deviceName(): String {
    val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercaseChar() }
    val model = if (Build.MODEL.startsWith(Build.MANUFACTURER, ignoreCase = true)) Build.MODEL else "$manufacturer ${Build.MODEL}"
    return "$model (${Build.DEVICE})"
}

private fun startSafely(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        android.widget.Toast.makeText(context, R.string.settings_not_available, android.widget.Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun SettingsRow(title: String, summary: String, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun BusyRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun NoticeCard(error: Boolean = false, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}
