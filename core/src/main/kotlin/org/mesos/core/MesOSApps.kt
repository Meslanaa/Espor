package org.mesos.core

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Activity class names of the apps bundled in MesOS. The launcher and the apps refer
 * to each other through these names instead of compile-time dependencies.
 */
object MesOSApps {
    const val SETTINGS = "org.mesos.settings.SettingsActivity"
    const val SETUP = "org.mesos.settings.SetupActivity"
    const val CAMERA = "org.mesos.camera.CameraActivity"
    const val PHOTOS = "org.mesos.photos.PhotosActivity"
    const val FILES = "org.mesos.files.FilesActivity"
    const val DOWNLOADS = "org.mesos.files.DownloadsActivity"
    const val CALCULATOR = "org.mesos.calculator.CalculatorActivity"
    const val NOTES = "org.mesos.notes.NotesActivity"
    const val CLOCK = "org.mesos.clock.ClockActivity"
    const val CALENDAR = "org.mesos.calendar.CalendarActivity"
    const val WEATHER = "org.mesos.weather.WeatherActivity"
    const val MUSIC = "org.mesos.music.MusicActivity"
    const val RECORDER = "org.mesos.recorder.RecorderActivity"
    const val SCREEN_RECORDER = "org.mesos.recorder.ScreenRecordActivity"
    const val SCANNER = "org.mesos.scanner.ScannerActivity"
    const val CONTACTS = "org.mesos.contacts.ContactsActivity"
    const val PHONE = "org.mesos.phone.PhoneActivity"
    const val MESSAGES = "org.mesos.messages.MessagesActivity"
    const val BROWSER = "org.mesos.browser.BrowserActivity"
    const val CARE = "org.mesos.care.CareActivity"
    const val TIPS = "org.mesos.tips.TipsActivity"

    /** Google Play Store: the one preinstalled app MesOS keeps visible. */
    const val PLAY_STORE_PACKAGE = "com.android.vending"

    fun launchIntent(context: Context, className: String): Intent =
        Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(context.packageName, className)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Opens a photo or video in MesOS Photos. */
    fun viewInPhotos(context: Context, uri: Uri, mimeType: String): Intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeType)
            .setClassName(context.packageName, PHOTOS)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    /** Opens a web page in MesOS Browser. */
    fun openInBrowser(context: Context, url: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .setClassName(context.packageName, BROWSER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Starts a call (or opens the dial pad) in MesOS Phone. */
    fun dial(context: Context, number: String): Intent =
        Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
            .setClassName(context.packageName, PHONE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Opens a conversation with [number] in MesOS Messages. */
    fun message(context: Context, number: String): Intent =
        Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null))
            .setClassName(context.packageName, MESSAGES)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
