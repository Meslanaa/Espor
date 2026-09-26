package org.mesos.launcher.search

import android.Manifest
import android.app.SearchManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.mesos.calculator.CalcResult
import org.mesos.calculator.CalculatorEngine
import org.mesos.core.MesOSApps
import org.mesos.core.MesOSIntents
import org.mesos.core.log.MesOSLog
import org.mesos.core.ui.MesOSGlyphs
import org.mesos.core.ui.MesOSPalette
import org.mesos.core.ui.hasPermission
import org.mesos.launcher.AppEntry
import org.mesos.launcher.R
import org.mesos.notes.Note
import org.mesos.notes.NotesFeed

data class SettingResult(val title: String, val icon: ImageVector, val color: Color, val intent: Intent)

data class ContactResult(val name: String, val photo: Uri?, val lookupUri: Uri)

data class FileResult(val name: String, val uri: Uri, val mimeType: String?)

/** Everything one search found, grouped the way the results screen shows it. */
data class SearchResults(
    val query: String = "",
    val apps: List<AppEntry> = emptyList(),
    val calculation: String? = null,
    val expression: String? = null,
    val settings: List<SettingResult> = emptyList(),
    val contacts: List<ContactResult> = emptyList(),
    val notes: List<Note> = emptyList(),
    val files: List<FileResult> = emptyList(),
    val contactsAllowed: Boolean = true,
)

/**
 * MesOS universal search: apps, calculations, settings, contacts, notes and files.
 * Providers the user has not granted access to are skipped silently.
 */
class SearchEngine(context: Context) {

    private val appContext = context.applicationContext

    private class SettingEntry(
        val title: Int,
        val keywords: Int,
        val icon: ImageVector,
        val color: Color,
        val intent: (Context) -> Intent,
    )

    private val settings = listOf(
        SettingEntry(R.string.search_setting_appearance, R.string.search_keywords_appearance, MesOSGlyphs.Palette, MesOSPalette.Indigo) {
            MesOSIntents.settings(it, MesOSIntents.PAGE_APPEARANCE)
        },
        SettingEntry(R.string.search_setting_home, R.string.search_keywords_home, MesOSGlyphs.Grid, MesOSPalette.Violet) {
            MesOSIntents.settings(it, MesOSIntents.PAGE_HOME)
        },
        SettingEntry(R.string.search_setting_notifications, R.string.search_keywords_notifications, MesOSGlyphs.Bell, MesOSPalette.Rose) {
            MesOSIntents.settings(it, MesOSIntents.PAGE_NOTIFICATIONS)
        },
        SettingEntry(R.string.search_setting_privacy, R.string.search_keywords_privacy, MesOSGlyphs.Shield, MesOSPalette.Slate) {
            MesOSIntents.settings(it, MesOSIntents.PAGE_PRIVACY)
        },
        SettingEntry(R.string.search_setting_update, R.string.search_keywords_update, MesOSGlyphs.Download, MesOSPalette.Sky) {
            MesOSIntents.settings(it, MesOSIntents.PAGE_UPDATE)
        },
        SettingEntry(R.string.search_setting_wifi, R.string.search_keywords_wifi, MesOSGlyphs.Wifi, MesOSPalette.Blue) {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        },
        SettingEntry(R.string.search_setting_bluetooth, R.string.search_keywords_bluetooth, MesOSGlyphs.Bluetooth, MesOSPalette.Indigo) {
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        },
        SettingEntry(R.string.search_setting_display, R.string.search_keywords_display, MesOSGlyphs.Sun, MesOSPalette.Amber) {
            Intent(Settings.ACTION_DISPLAY_SETTINGS)
        },
        SettingEntry(R.string.search_setting_sound, R.string.search_keywords_sound, MesOSGlyphs.Volume, MesOSPalette.Pink) {
            Intent(Settings.ACTION_SOUND_SETTINGS)
        },
        SettingEntry(R.string.search_setting_battery, R.string.search_keywords_battery, MesOSGlyphs.Battery, MesOSPalette.Green) {
            Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
        },
        SettingEntry(R.string.search_setting_storage, R.string.search_keywords_storage, MesOSGlyphs.Storage, MesOSPalette.Teal) {
            Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
        },
        SettingEntry(R.string.search_setting_apps, R.string.search_keywords_apps, MesOSGlyphs.Apps, MesOSPalette.Blue) {
            Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
        },
        SettingEntry(R.string.search_setting_default_apps, R.string.search_keywords_default_apps, MesOSGlyphs.Check, MesOSPalette.Green) {
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        },
        SettingEntry(R.string.search_setting_location, R.string.search_keywords_location, MesOSGlyphs.Location, MesOSPalette.Sky) {
            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        },
        SettingEntry(R.string.search_setting_language, R.string.search_keywords_language, MesOSGlyphs.Globe, MesOSPalette.Indigo) {
            Intent(Settings.ACTION_LOCALE_SETTINGS)
        },
        SettingEntry(R.string.search_setting_date, R.string.search_keywords_date, MesOSGlyphs.Clock, MesOSPalette.Slate) {
            Intent(Settings.ACTION_DATE_SETTINGS)
        },
        SettingEntry(R.string.search_setting_accessibility, R.string.search_keywords_accessibility, MesOSGlyphs.Person, MesOSPalette.Blue) {
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        },
        SettingEntry(R.string.search_setting_security, R.string.search_keywords_security, MesOSGlyphs.Lock, MesOSPalette.Slate) {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        },
        SettingEntry(R.string.search_setting_airplane, R.string.search_keywords_airplane, MesOSGlyphs.Send, MesOSPalette.Orange) {
            Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
        },
        SettingEntry(R.string.search_setting_accounts, R.string.search_keywords_accounts, MesOSGlyphs.Person, MesOSPalette.Red) {
            Intent(Settings.ACTION_SYNC_SETTINGS)
        },
        SettingEntry(R.string.search_setting_developer, R.string.search_keywords_developer, MesOSGlyphs.Memory, MesOSPalette.Slate) {
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        },
    )

    suspend fun search(query: String, apps: List<AppEntry>, usage: Map<String, Int>): SearchResults = coroutineScope {
        val q = query.trim()
        if (q.isEmpty()) return@coroutineScope SearchResults()
        val contacts = async(Dispatchers.IO) { searchContacts(q) }
        val notes = async(Dispatchers.IO) { searchNotes(q) }
        val files = async(Dispatchers.IO) { searchFiles(q) }

        val matchedApps = apps
            .map { it to SearchText.score(q, it.label) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<AppEntry, Int>> { it.second }.thenByDescending { usage[it.first.key] ?: 0 })
            .map { it.first }
            .take(MAX_APPS)

        val expression = SearchText.expression(q)
        val calculation = expression?.let { expr ->
            when (val result = CalculatorEngine.evaluate(expr)) {
                is CalcResult.Value -> CalculatorEngine.format(result.value)
                is CalcResult.Error -> null
            }
        }

        val matchedSettings = settings
            .map { entry ->
                val title = appContext.getString(entry.title)
                val keywords = appContext.getString(entry.keywords)
                entry to maxOf(SearchText.score(q, title), SearchText.score(q, keywords) / 2)
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(MAX_SETTINGS)
            .map { (entry, _) ->
                SettingResult(appContext.getString(entry.title), entry.icon, entry.color, entry.intent(appContext))
            }

        SearchResults(
            query = q,
            apps = matchedApps,
            calculation = calculation,
            expression = expression,
            settings = matchedSettings,
            contacts = contacts.await(),
            notes = notes.await(),
            files = files.await(),
            contactsAllowed = appContext.hasPermission(Manifest.permission.READ_CONTACTS),
        )
    }

    private fun searchContacts(query: String): List<ContactResult> {
        if (!appContext.hasPermission(Manifest.permission.READ_CONTACTS)) return emptyList()
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(query))
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
        )
        return try {
            appContext.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                buildList {
                    while (c.moveToNext() && size < MAX_CONTACTS) {
                        val id = c.getLong(0)
                        val lookup = c.getString(1) ?: continue
                        add(
                            ContactResult(
                                name = c.getString(2).orEmpty(),
                                photo = c.getString(3)?.let(Uri::parse),
                                lookupUri = ContactsContract.Contacts.getLookupUri(id, lookup),
                            ),
                        )
                    }
                }
            }.orEmpty()
        } catch (e: RuntimeException) {
            MesOSLog.w(MesOSLog.LAUNCHER, "Contact search failed", e)
            emptyList()
        }
    }

    private suspend fun searchNotes(query: String): List<Note> =
        try {
            NotesFeed.search(appContext, query, MAX_NOTES)
        } catch (e: RuntimeException) {
            MesOSLog.w(MesOSLog.LAUNCHER, "Note search failed", e)
            emptyList()
        }

    private fun canReadFiles(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            appContext.hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    private fun searchFiles(query: String): List<FileResult> {
        if (query.length < 2 || !canReadFiles()) return emptyList()
        val collection = MediaStore.Files.getContentUri("external")
        val pattern = "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
        )
        val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? ESCAPE '\\' AND " +
            "${MediaStore.Files.FileColumns.MIME_TYPE} IS NOT NULL"
        return try {
            appContext.contentResolver.query(
                collection,
                projection,
                selection,
                arrayOf(pattern),
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC",
            )?.use { c ->
                buildList {
                    while (c.moveToNext() && size < MAX_FILES) {
                        add(
                            FileResult(
                                name = c.getString(1).orEmpty(),
                                uri = ContentUris.withAppendedId(collection, c.getLong(0)),
                                mimeType = c.getString(2),
                            ),
                        )
                    }
                }
            }.orEmpty()
        } catch (e: RuntimeException) {
            MesOSLog.w(MesOSLog.LAUNCHER, "File search failed", e)
            emptyList()
        }
    }

    companion object {
        private const val MAX_APPS = 8
        private const val MAX_SETTINGS = 4
        private const val MAX_CONTACTS = 4
        private const val MAX_NOTES = 3
        private const val MAX_FILES = 5

        /** Web search in MesOS Browser (falls back to any browser). */
        fun webSearchIntent(context: Context, query: String): Intent =
            Intent(Intent.ACTION_WEB_SEARCH)
                .putExtra(SearchManager.QUERY, query)
                .setClassName(context.packageName, MesOSApps.BROWSER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        fun playStoreIntent(query: String): Intent =
            Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + Uri.encode(query)))
                .setPackage(MesOSApps.PLAY_STORE_PACKAGE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        fun contactIntent(context: Context, contact: ContactResult): Intent =
            Intent(Intent.ACTION_VIEW, contact.lookupUri)
                .setClassName(context.packageName, MesOSApps.CONTACTS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        fun fileIntent(context: Context, file: FileResult): Intent {
            val mime = file.mimeType ?: "*/*"
            return if (mime.startsWith("image/") || mime.startsWith("video/")) {
                MesOSApps.viewInPhotos(context, file.uri, mime).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } else {
                Intent.createChooser(
                    Intent(Intent.ACTION_VIEW).setDataAndType(file.uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    null,
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }
}
