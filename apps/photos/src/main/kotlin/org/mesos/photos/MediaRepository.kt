package org.mesos.photos

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.BaseColumns
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A photo or video, from MediaStore or opened from another app. */
data class MediaItem(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val isVideo: Boolean,
    val dateTaken: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val album: String,
) {
    /** Only MediaStore items can be deleted through MediaStore's delete request. */
    val isMediaStoreItem: Boolean
        get() = uri.authority == MediaStore.AUTHORITY
}

data class Album(val name: String, val items: List<MediaItem>)

internal object MediaRepository {

    /** All photos and videos the app may read, newest first. */
    suspend fun load(context: Context): List<MediaItem> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        (query(resolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, isVideo = false) +
            query(resolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, isVideo = true))
            .sortedByDescending { it.dateTaken }
    }

    fun albums(items: List<MediaItem>): List<Album> =
        items.groupBy { it.album }
            .map { (name, albumItems) -> Album(name, albumItems) }
            .sortedByDescending { it.items.first().dateTaken }

    /** Describes a single URI handed to MesOS Photos by another app. */
    suspend fun describe(context: Context, uri: Uri, mimeType: String?): MediaItem = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var name = uri.lastPathSegment.orEmpty()
        var size = 0L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { name = it }
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
        }
        val type = mimeType ?: runCatching { resolver.getType(uri) }.getOrNull() ?: "image/*"
        MediaItem(
            uri = uri,
            name = name,
            mimeType = type,
            isVideo = type.startsWith("video/"),
            dateTaken = 0,
            size = size,
            width = 0,
            height = 0,
            durationMs = 0,
            album = "",
        )
    }

    private fun query(resolver: ContentResolver, collection: Uri, isVideo: Boolean): List<MediaItem> {
        val projection = buildList {
            add(BaseColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.Images.ImageColumns.DATE_TAKEN)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.WIDTH)
            add(MediaStore.MediaColumns.HEIGHT)
            add(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)
            if (isVideo) add(MediaStore.Video.VideoColumns.DURATION)
        }.toTypedArray()

        val cursor = runCatching {
            resolver.query(collection, projection, null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")
        }.getOrNull() ?: return emptyList()

        return cursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val taken = if (c.isNull(3)) 0L else c.getLong(3)
                    val added = if (c.isNull(4)) 0L else c.getLong(4) * 1000
                    add(
                        MediaItem(
                            uri = ContentUris.withAppendedId(collection, id),
                            name = c.getString(1).orEmpty(),
                            mimeType = c.getString(2) ?: if (isVideo) "video/*" else "image/*",
                            isVideo = isVideo,
                            dateTaken = if (taken > 0) taken else added,
                            size = if (c.isNull(5)) 0 else c.getLong(5),
                            width = if (c.isNull(6)) 0 else c.getInt(6),
                            height = if (c.isNull(7)) 0 else c.getInt(7),
                            durationMs = if (isVideo && !c.isNull(9)) c.getLong(9) else 0,
                            album = c.getString(8).orEmpty(),
                        ),
                    )
                }
            }
        }
    }
}
