package org.mesos.music

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executor
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mesos.core.log.MesOSLog

/** What the player is doing, for the MesOS Music screens. */
data class NowPlaying(
    val songId: Long? = null,
    val title: String = "",
    val artist: String = "",
    /** The song's own URI; its embedded cover art is read from it. */
    val songUri: Uri? = null,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
)

/** A MediaController connected to [PlaybackService] while MesOS Music is open. */
class PlayerConnection(context: Context) {

    private val appContext = context.applicationContext
    private var controller: MediaController? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { mainHandler.post(it) }
    private val _state = MutableStateFlow(NowPlaying())
    val state: StateFlow<NowPlaying> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish()
    }

    fun connect() {
        if (controller != null) return
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener(
            {
                try {
                    controller = future.get().also { it.addListener(listener) }
                    publish()
                } catch (e: Exception) {
                    MesOSLog.w(TAG, "Could not connect to the player", e)
                }
            },
            mainExecutor,
        )
    }

    fun release() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    /** Current position in ms (the UI polls this for the seek bar). */
    fun position(): Long = controller?.currentPosition ?: 0L

    fun play(songs: List<Song>, startIndex: Int, shuffle: Boolean = false) {
        val c = controller ?: return
        c.setMediaItems(songs.map(::toItem), startIndex.coerceIn(0, (songs.size - 1).coerceAtLeast(0)), 0L)
        c.shuffleModeEnabled = shuffle
        c.prepare()
        c.play()
    }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() = controller?.seekToNext()

    fun previous() = controller?.seekToPrevious()

    fun seekTo(ms: Long) = controller?.seekTo(ms)

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    private fun publish() {
        val c = controller ?: return
        val metadata = c.mediaMetadata
        _state.value = NowPlaying(
            songId = c.currentMediaItem?.mediaId?.toLongOrNull(),
            title = metadata.title?.toString().orEmpty(),
            artist = metadata.artist?.toString().orEmpty(),
            songUri = c.currentMediaItem?.requestMetadata?.mediaUri ?: c.currentMediaItem?.localConfiguration?.uri,
            isPlaying = c.isPlaying,
            durationMs = c.duration.takeIf { it > 0 } ?: 0L,
            shuffle = c.shuffleModeEnabled,
            repeat = c.repeatMode,
        )
    }

    private fun toItem(song: Song): MediaItem =
        MediaItem.Builder()
            .setMediaId(song.id.toString())
            .setUri(song.uri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(song.uri).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .build(),
            )
            .build()

    private companion object {
        const val TAG = "MesOSMusic"
    }
}
