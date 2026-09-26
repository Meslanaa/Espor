package org.mesos.files

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.FileProvider
import org.mesos.core.MesOSApps
import org.mesos.core.ui.hasPermission
import org.mesos.core.ui.startActivitySafely
import org.mesos.core.ui.theme.MesOSUserTheme
import java.io.File

/** MesOS Files. [DownloadsActivity] reuses it with the Download folder as its top level. */
open class FilesActivity : ComponentActivity() {

    /** Folder the app opens in and never leaves; null shows the MesOS Files home. */
    protected open val startDirectory: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val start = startDirectory
        setContent {
            MesOSUserTheme {
                FilesApp(topDirectory = start, onExit = ::finish)
            }
        }
    }
}

/** MesOS Downloads: MesOS Files opened on the Download folder. */
class DownloadsActivity : FilesActivity() {
    @Suppress("DEPRECATION")
    override val startDirectory: File
        get() = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
}

internal object Storage {

    @Suppress("DEPRECATION")
    val root: File
        get() = Environment.getExternalStorageDirectory()

    @Suppress("DEPRECATION")
    fun publicDirectory(type: String): File = Environment.getExternalStoragePublicDirectory(type)

    fun hasAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            context.hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE) &&
                context.hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

    /** Android's "All files access" screen for MesOS (API 30+). */
    fun openAllFilesAccessSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val forApp = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
        if (!context.startActivitySafely(forApp)) {
            context.startActivitySafely(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }

    fun mimeType(file: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
            ?: "application/octet-stream"

    fun contentUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    /** Intent that opens [file]: photos and videos in MesOS Photos, everything else by type. */
    fun viewIntent(context: Context, file: File): Intent {
        val uri = contentUri(context, file)
        val mime = mimeType(file)
        return if (mime.startsWith("image/") || mime.startsWith("video/")) {
            MesOSApps.viewInPhotos(context, uri, mime)
        } else {
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun shareIntent(context: Context, file: File, title: String): Intent {
        val send = Intent(Intent.ACTION_SEND)
            .setType(mimeType(file))
            .putExtra(Intent.EXTRA_STREAM, contentUri(context, file))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, title)
    }

    /** Tells MediaStore about added, moved or deleted files so galleries stay correct. */
    fun rescan(context: Context, files: List<File>) {
        if (files.isEmpty()) return
        MediaScannerConnection.scanFile(context.applicationContext, files.map { it.path }.toTypedArray(), null, null)
    }
}
