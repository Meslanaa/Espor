package org.mesos.recorder

import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.core.content.FileProvider
import org.mesos.core.log.MesOSLog
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A saved voice recording. */
data class Recording(val uri: Uri, val name: String, val durationMs: Long, val sizeBytes: Long, val dateMs: Long)

/** What is being recorded, which decides the MediaStore collection and folder. */
internal enum class MediaKind(val mime: String, val extension: String) {
    AUDIO("audio/mp4", "m4a"),
    VIDEO("video/mp4", "mp4"),
}

/** A file being written: a pending MediaStore entry (Android 10+) or an app file. */
internal class Output(val uri: Uri?, val file: File?, val descriptor: ParcelFileDescriptor?) {
    /** URI to open the finished file with (content:// on Android 10+). */
    fun viewUri(context: Context): Uri? =
        uri ?: file?.let { FileProvider.getUriForFile(context, "${context.packageName}.files", it) }
}

sealed interface DeleteResult {
    data object Deleted : DeleteResult
    /** Android asks the user first (the file belongs to an earlier MesOS install). */
    class NeedsConsent(val request: PendingIntent) : DeleteResult
    data object Failed : DeleteResult
}

/**
 * Where MesOS keeps recordings: Recordings/MesOS (voice) and Movies/MesOS
 * (screen) in shared storage, visible to Files and other apps. Before Android 10
 * they stay in MesOS's own folder, which needs no storage permission.
 */
internal object RecordingStore {

    fun newName(prefix: String, kind: MediaKind, now: Long = System.currentTimeMillis()): String =
        "$prefix ${SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.ROOT).format(Date(now))}.${kind.extension}"

    private fun relativePath(kind: MediaKind): String = when (kind) {
        MediaKind.AUDIO -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            "${Environment.DIRECTORY_RECORDINGS}/MesOS"
        } else {
            "${Environment.DIRECTORY_MUSIC}/MesOS Recordings"
        }
        MediaKind.VIDEO -> "${Environment.DIRECTORY_MOVIES}/MesOS"
    }

    private fun collection(kind: MediaKind): Uri = when (kind) {
        MediaKind.AUDIO -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        MediaKind.VIDEO -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
    }

    private fun legacyDir(context: Context, kind: MediaKind): File? {
        val base = context.getExternalFilesDir(if (kind == MediaKind.AUDIO) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES)
            ?: return null
        return File(base, if (kind == MediaKind.AUDIO) "Recordings" else "Screen recordings").apply { mkdirs() }
    }

    /** Creates the file to record into, or null when storage is not available. */
    fun create(context: Context, name: String, kind: MediaKind): Output? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, kind.mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath(kind))
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = try {
                resolver.insert(collection(kind), values)
            } catch (e: IllegalArgumentException) {
                MesOSLog.w(MesOSLog.SYSTEM, "Could not create $name", e)
                null
            } ?: return null
            val descriptor = try {
                resolver.openFileDescriptor(uri, "rw")
            } catch (e: IOException) {
                null
            }
            if (descriptor == null) {
                resolver.delete(uri, null, null)
                return null
            }
            return Output(uri, null, descriptor)
        }
        val dir = legacyDir(context, kind) ?: return null
        return Output(null, File(dir, name), null)
    }

    /** Makes a finished recording visible to everyone. */
    fun publish(context: Context, output: Output) {
        closeQuietly(output.descriptor)
        val uri = output.uri ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            context.contentResolver.update(uri, values, null, null)
        }
    }

    /** Removes an unfinished or failed recording. */
    fun discard(context: Context, output: Output) {
        closeQuietly(output.descriptor)
        output.uri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
        output.file?.delete()
    }

    /** Voice recordings, newest first. Call off the main thread. */
    fun list(context: Context): List<Recording> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val dir = legacyDir(context, MediaKind.AUDIO) ?: return emptyList()
            return dir.listFiles { f -> f.isFile && f.extension == MediaKind.AUDIO.extension }.orEmpty()
                .sortedByDescending { it.lastModified() }
                .map { file -> Recording(Uri.fromFile(file), file.nameWithoutExtension, durationOf(file), file.length(), file.lastModified()) }
        }
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
        )
        val collection = collection(MediaKind.AUDIO)
        val result = mutableListOf<Recording>()
        try {
            context.contentResolver.query(
                collection,
                projection,
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                arrayOf(relativePath(MediaKind.AUDIO) + "%"),
                "${MediaStore.MediaColumns.DATE_ADDED} DESC",
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    result += Recording(
                        uri = ContentUris.withAppendedId(collection, id),
                        name = cursor.getString(1).orEmpty().substringBeforeLast('.'),
                        durationMs = cursor.getLong(2),
                        sizeBytes = cursor.getLong(3),
                        dateMs = cursor.getLong(4) * 1000,
                    )
                }
            }
        } catch (e: SecurityException) {
            MesOSLog.w(MesOSLog.SYSTEM, "Recordings not readable", e)
        }
        return result
    }

    fun delete(context: Context, recording: Recording): DeleteResult {
        if (recording.uri.scheme == "file") {
            return if (recording.uri.path?.let { File(it).delete() } == true) DeleteResult.Deleted else DeleteResult.Failed
        }
        return try {
            if (context.contentResolver.delete(recording.uri, null, null) > 0) DeleteResult.Deleted else DeleteResult.Failed
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                DeleteResult.NeedsConsent(MediaStore.createDeleteRequest(context.contentResolver, listOf(recording.uri)))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                DeleteResult.NeedsConsent(e.userAction.actionIntent)
            } else {
                DeleteResult.Failed
            }
        }
    }

    fun rename(context: Context, recording: Recording, newName: String): Boolean {
        val clean = newName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
        if (clean.isEmpty()) return false
        if (recording.uri.scheme == "file") {
            val file = File(recording.uri.path ?: return false)
            return file.renameTo(File(file.parentFile, "$clean.${file.extension}"))
        }
        return try {
            val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, "$clean.${MediaKind.AUDIO.extension}") }
            context.contentResolver.update(recording.uri, values, null, null) > 0
        } catch (e: SecurityException) {
            false
        }
    }

    /** A URI other apps can read, for sharing. */
    fun shareUri(context: Context, recording: Recording): Uri =
        if (recording.uri.scheme == "file") {
            FileProvider.getUriForFile(context, "${context.packageName}.files", File(recording.uri.path!!))
        } else {
            recording.uri
        }

    private fun durationOf(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: RuntimeException) {
            0L
        } finally {
            retriever.release()
        }
    }

    private fun closeQuietly(descriptor: ParcelFileDescriptor?) {
        try {
            descriptor?.close()
        } catch (e: IOException) {
            // Already closed.
        }
    }
}
