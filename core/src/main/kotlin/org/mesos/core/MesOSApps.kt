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
    const val CAMERA = "org.mesos.camera.CameraActivity"
    const val PHOTOS = "org.mesos.photos.PhotosActivity"
    const val FILES = "org.mesos.files.FilesActivity"
    const val DOWNLOADS = "org.mesos.files.DownloadsActivity"
    const val CALCULATOR = "org.mesos.calculator.CalculatorActivity"
    const val NOTES = "org.mesos.notes.NotesActivity"

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
}
