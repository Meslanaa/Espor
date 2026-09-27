package org.mesos.music

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mesos.core.log.MesOSLog
import java.text.Collator
import java.util.Locale

/** A song in the device's music library. */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val track: Int,
    val uri: Uri,
)

data class Album(val id: Long, val title: String, val artist: String, val songs: List<Song>)

data class Artist(val name: String, val songs: List<Song>)

data class Library(val songs: List<Song>, val albums: List<Album>, val artists: List<Artist>)

/** Reads music from Android's MediaStore (the Music folder and every other audio file marked as music). */
object MusicLibrary {

    suspend fun load(context: Context, unknownArtist: String): Library = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
        )
        val songs = try {
            context.contentResolver.query(
                collection,
                projection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                null,
            )?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val id = c.getLong(0)
                        add(
                            Song(
                                id = id,
                                title = c.getString(1).orEmpty(),
                                artist = c.getString(2)?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING } ?: unknownArtist,
                                album = c.getString(3).orEmpty(),
                                albumId = c.getLong(4),
                                durationMs = c.getLong(5),
                                track = c.getInt(6) % 1000,
                                uri = ContentUris.withAppendedId(collection, id),
                            ),
                        )
                    }
                }
            }.orEmpty()
        } catch (e: SecurityException) {
            MesOSLog.w(TAG, "No access to music", e)
            emptyList()
        }
        val collator = Collator.getInstance(Locale.getDefault())
        val sorted = songs.sortedWith { a, b -> collator.compare(a.title, b.title) }
        val albums = songs.groupBy { it.albumId }
            .map { (id, list) -> Album(id, list.first().album, list.first().artist, list.sortedBy { it.track }) }
            .sortedWith { a, b -> collator.compare(a.title, b.title) }
        val artists = songs.groupBy { it.artist }
            .map { (name, list) -> Artist(name, list.sortedWith { a, b -> collator.compare(a.title, b.title) }) }
            .sortedWith { a, b -> collator.compare(a.name, b.name) }
        Library(sorted, albums, artists)
    }

    private const val TAG = "MesOSMusic"
}
