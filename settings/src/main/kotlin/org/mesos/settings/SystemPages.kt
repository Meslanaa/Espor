package org.mesos.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.DateUtils
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mesos.core.MesOSApps
import org.mesos.core.MesOSRelease
import org.mesos.core.prefs.MesOSPreferences
import org.mesos.core.ui.GroupDivider
import org.mesos.core.ui.ListGroup
import org.mesos.core.ui.ListRow
import org.mesos.core.ui.MesOSCard
import org.mesos.core.ui.MesOSGlyphs
import org.mesos.core.ui.MesOSMark
import org.mesos.core.ui.MesOSPalette
import org.mesos.core.ui.MesOSWordmark
import org.mesos.core.ui.SwitchRow
import org.mesos.core.ui.theme.MesOSTheme
import org.mesos.updater.FailureReason
import org.mesos.updater.UpdateCheckScheduler
import org.mesos.updater.UpdateController
import org.mesos.updater.UpdateState
import java.io.IOException

@Composable
internal fun SystemPage(nav: SettingsNav, canLeave: Boolean) {
    val context = LocalContext.current
    val state by UpdateController.state.collectAsState()
    val summary = updateSummary()
    val androidTag = stringResource(R.string.settings_android_tag)

    SettingsPage(Page.SYSTEM, nav, canLeave) {
        item(key = "update") {
            ListGroup {
                ListRow(
                    title = stringResource(R.string.settings_update),
                    subtitle = summary,
                    icon = MesOSGlyphs.Download,
                    iconColor = MesOSPalette.Indigo,
                    badge = if (state is UpdateState.Available) 1 else 0,
                    onClick = { nav.open(Page.UPDATE) },
                )
            }
        }
        item(key = "android") {
            ListGroup {
                ListRow(
                    title = stringResource(R.string.system_date_time),
                    icon = MesOSGlyphs.Clock,
                    iconColor = MesOSPalette.Blue,
                    value = androidTag,
                    onClick = { nav.external(Intent(Settings.ACTION_DATE_SETTINGS)) },
                )
                GroupDivider()
                ListRow(
                    title = stringResource(R.string.system_accessibility),
                    icon = MesOSGlyphs.Person,
                    iconColor = MesOSPalette.Teal,
                    value = androidTag,
                    onClick = { nav.external(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                )
                GroupDivider()
                ListRow(
                    title = stringResource(R.string.settings_android_system),
                    icon = MesOSGlyphs.Settings,
                    iconColor = MesOSPalette.Slate,
                    value = androidTag,
                    onClick = { nav.external(Intent(Settings.ACTION_SETTINGS)) },
                )
            }
        }
        item(key = "setup") {
            ListGroup {
                ListRow(
                    title = stringResource(R.string.system_run_setup),
                    subtitle = stringResource(R.string.system_run_setup_summary),
                    icon = MesOSGlyphs.Sparkle,
                    iconColor = MesOSPalette.Violet,
                    onClick = { nav.external(MesOSApps.launchIntent(context, MesOSApps.SETUP)) },
                )
            }
        }
    }
}

@Composable
internal fun UpdatePage(nav: SettingsNav, canLeave: Boolean) {
    val context = LocalContext.current
    val preferences = remember(context) { MesOSPreferences.get(context) }
    val state by UpdateController.state.collectAsState()
    val autoCheck by preferences.autoUpdateCheck.collectAsState()
    val release = MesOSRelease.current
    val summary = updateSummary()

    SettingsPage(Page.UPDATE, nav, canLeave) {
        item(key = "hero") {
            MesOSCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MesOSMark(size = 64.dp)
                    Text(release.displayName, style = MaterialTheme.typography.headlineSmall)
                    Text(summary, style = MaterialTheme.typography.bodyMedium, color = MesOSTheme.colors.dim, textAlign = TextAlign.Center)
                }
            }
        }
        UpdateController.updatedFrom?.let { from ->
            item(key = "updated") {
                MesOSCard(color = MaterialTheme.colorScheme.secondaryContainer) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.update_just_updated, from, release.versionName), style = MaterialTheme.typography.titleSmall)
                        UpdateController.installedReleaseNotes?.takeIf { it.isNotBlank() }?.let { notes ->
                            Text(stringResource(R.string.update_whats_new, release.versionName), style = MaterialTheme.typography.labelLarge)
                            Text(notes, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        item(key = "state") { UpdateStateCard(state, context) }
        item(key = "check") {
            Button(
                onClick = { UpdateController.check() },
                enabled = !state.isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Text(stringResource(if (state is UpdateState.Failed) R.string.update_try_again else R.string.update_check))
            }
        }
        item(key = "auto") {
            ListGroup {
                SwitchRow(
                    title = stringResource(R.string.update_auto_check),
                    subtitle = stringResource(R.string.update_auto_check_summary),
                    checked = autoCheck,
                    onCheckedChange = { enabled ->
                        preferences.setAutoUpdateCheck(enabled)
                        UpdateCheckScheduler.sync(context)
                    },
                    icon = MesOSGlyphs.Refresh,
                    iconColor = MesOSPalette.Indigo,
                )
            }
        }
        item(key = "notes") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Note(stringResource(R.string.update_scope_note))
                Note(stringResource(R.string.update_source, UpdateController.manifestUrl))
            }
        }
    }
}

@Composable
private fun UpdateStateCard(state: UpdateState, context: Context) {
    when (state) {
        UpdateState.Idle -> Unit
        UpdateState.Checking -> MesOSCard { BusyRow(stringResource(R.string.update_checking)) }
        is UpdateState.UpToDate -> MesOSCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    MesOSGlyphs.Check,
                    contentDescription = null,
                    tint = MesOSTheme.colors.success,
                    modifier = Modifier.size(24.dp),
                )
                Text(stringResource(R.string.update_up_to_date), style = MaterialTheme.typography.bodyLarge)
            }
        }
        is UpdateState.Available -> MesOSCard(color = MaterialTheme.colorScheme.primaryContainer) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.update_available_title, state.manifest.versionName), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.update_available_size, Formatter.formatShortFileSize(context, state.manifest.packageSize)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (state.manifest.releaseNotes.isNotBlank()) {
                    Text(stringResource(R.string.update_release_notes), style = MaterialTheme.typography.labelLarge)
                    Text(state.manifest.releaseNotes, style = MaterialTheme.typography.bodyMedium)
                }
                Button(onClick = { UpdateController.downloadAndInstall() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.update_download_install))
                }
            }
        }
        is UpdateState.Downloading -> MesOSCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.update_downloading, (state.progress * 100).toInt()), style = MaterialTheme.typography.bodyLarge)
                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
            }
        }
        is UpdateState.Verifying -> MesOSCard { BusyRow(stringResource(R.string.update_verifying)) }
        is UpdateState.NeedsInstallPermission -> MesOSCard(color = MaterialTheme.colorScheme.secondaryContainer) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.update_permission_needed), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = {
                    try {
                        context.startActivity(UpdateController.installPermissionIntent(context))
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(context, R.string.settings_not_available, Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text(stringResource(R.string.update_permission_action))
                }
                OutlinedButton(onClick = { UpdateController.downloadAndInstall() }) {
                    Text(stringResource(R.string.update_download_install))
                }
            }
        }
        is UpdateState.AwaitingConfirmation -> MesOSCard {
            Text(stringResource(R.string.update_awaiting_confirmation), style = MaterialTheme.typography.bodyLarge)
        }
        is UpdateState.Installed -> MesOSCard {
            Text(stringResource(R.string.update_installed), style = MaterialTheme.typography.bodyLarge)
        }
        is UpdateState.Failed -> MesOSCard(color = MaterialTheme.colorScheme.errorContainer) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.update_failed), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(failureMessage(state.reason)), style = MaterialTheme.typography.bodyMedium)
                state.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
internal fun AboutPage(nav: SettingsNav, canLeave: Boolean) {
    val release = MesOSRelease.current
    val unknown = stringResource(R.string.about_unknown)
    val summary = updateSummary()

    SettingsPage(Page.ABOUT, nav, canLeave) {
        item(key = "hero") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MesOSMark(size = 80.dp)
                MesOSWordmark()
                Text(release.displayVersion, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        item(key = "mesos") {
            ListGroup {
                InfoLine(stringResource(R.string.about_mesos_version), release.displayVersion)
                GroupDivider(inset = 16.dp)
                InfoLine(
                    stringResource(R.string.about_mesos_build),
                    stringResource(R.string.about_mesos_build_value, release.buildNumber, release.channel.displayName),
                )
                GroupDivider(inset = 16.dp)
                InfoLine(stringResource(R.string.about_update_status), summary)
            }
        }
        item(key = "android") {
            ListGroup {
                InfoLine(
                    stringResource(R.string.about_android_base),
                    stringResource(R.string.about_android_base_value, Build.VERSION.RELEASE, Build.VERSION.SDK_INT),
                )
                GroupDivider(inset = 16.dp)
                InfoLine(stringResource(R.string.about_android_build), Build.DISPLAY.ifBlank { unknown })
                GroupDivider(inset = 16.dp)
                InfoLine(stringResource(R.string.about_security_patch), Build.VERSION.SECURITY_PATCH.ifBlank { unknown })
                GroupDivider(inset = 16.dp)
                InfoLine(stringResource(R.string.about_device), deviceName())
            }
        }
        item(key = "licenses") {
            ListGroup {
                ListRow(
                    title = stringResource(R.string.settings_licenses),
                    subtitle = stringResource(R.string.licenses_summary),
                    icon = MesOSGlyphs.Info,
                    iconColor = MesOSPalette.Blue,
                    onClick = { nav.open(Page.LICENSES) },
                )
            }
        }
        item(key = "note") { Note(stringResource(R.string.about_scope_note)) }
    }
}

/** Third-party software and data in MesOS, with the licence text or where to read it. */
private class License(val name: String, val license: String, val asset: String? = null, val url: String? = null)

private val licenses = listOf(
    License("Sora", "SIL Open Font License 1.1", asset = "licenses/OFL-Sora.txt"),
    License("Manrope", "SIL Open Font License 1.1", asset = "licenses/OFL-Manrope.txt"),
    License("AndroidX · Jetpack Compose · CameraX · Media3", "Apache License 2.0", url = "https://www.apache.org/licenses/LICENSE-2.0"),
    License("Kotlin · kotlinx.coroutines", "Apache License 2.0", url = "https://www.apache.org/licenses/LICENSE-2.0"),
    License("Coil", "Apache License 2.0", url = "https://www.apache.org/licenses/LICENSE-2.0"),
    License("ZXing", "Apache License 2.0", url = "https://www.apache.org/licenses/LICENSE-2.0"),
    License("Open-Meteo", "CC BY 4.0", url = "https://open-meteo.com/"),
)

@Composable
internal fun LicensesPage(nav: SettingsNav, canLeave: Boolean) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }

    SettingsPage(Page.LICENSES, nav, canLeave) {
        item(key = "list") {
            ListGroup {
                licenses.forEachIndexed { index, license ->
                    if (index > 0) GroupDivider(inset = 16.dp)
                    ListRow(title = license.name, subtitle = license.license, onClick = { open = license.name })
                }
            }
        }
        item(key = "note") { Note(stringResource(R.string.licenses_note)) }
    }

    licenses.firstOrNull { it.name == open }?.let { license ->
        LicenseDialog(license, onDismiss = { open = null })
    }
}

@Composable
private fun LicenseDialog(license: License, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val text by produceState<String?>(null, license) {
        value = license.asset?.let { path ->
            withContext(Dispatchers.IO) {
                try {
                    context.assets.open(path).bufferedReader().use { it.readText() }
                } catch (e: IOException) {
                    null
                }
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } },
        title = { Text(license.name) },
        text = {
            Box(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = listOfNotNull(license.license, license.url, text).joinToString("\n\n"),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
    )
}

/** One-line update status for Settings rows and cards. */
@Composable
internal fun updateSummary(): String {
    val context = LocalContext.current
    val state by UpdateController.state.collectAsState()
    val lastCheckedAt by UpdateController.lastCheckedAt.collectAsState()
    val current = state
    val checked = lastCheckedAt
    val updatedFrom = UpdateController.updatedFrom
    return when {
        current is UpdateState.Available -> stringResource(R.string.update_status_available, current.manifest.versionName)
        current is UpdateState.UpToDate -> stringResource(R.string.update_status_up_to_date, formatTime(context, current.checkedAt))
        updatedFrom != null -> stringResource(R.string.update_status_updated_from, updatedFrom)
        checked != null -> stringResource(R.string.update_status_last_checked, formatTime(context, checked))
        else -> stringResource(R.string.update_status_never)
    }
}

@Composable
private fun BusyRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
        Text(text, style = MaterialTheme.typography.bodyLarge)
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
