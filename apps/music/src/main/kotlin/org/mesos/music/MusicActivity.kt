package org.mesos.music

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import org.mesos.core.ui.EmptyState
import org.mesos.core.ui.MesOSGlyphs
import org.mesos.core.ui.MesOSSearchField
import org.mesos.core.ui.OnResume
import org.mesos.core.ui.PermissionGate
import org.mesos.core.ui.rememberContentThumbnail
import org.mesos.core.ui.theme.MesOSTheme
import org.mesos.core.ui.theme.MesOSUserTheme
import org.mesos.core.text.SearchText

/** MesOS Music. */
class MusicActivity : ComponentActivity() {

    private lateinit var player: PlayerConnection

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = PlayerConnection(this)
        setContent { MesOSUserTheme { MusicApp(player) } }
    }

    override fun onStart() {
        super.onStart()
        player.connect()
    }

    override fun onStop() {
        player.release()
        super.onStop()
    }
}

private val audioPermission =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

private enum class MusicTab { SONGS, ALBUMS, ARTISTS }

@Composable
private fun MusicApp(player: PlayerConnection) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.safeDrawingPadding()) {
            PermissionGate(
                permissions = listOf(audioPermission),
                rationale = stringResource(R.string.music_permission),
                icon = MesOSGlyphs.Music,
            ) {
                Library(player)
            }
        }
    }
}

@Composable
private fun Library(player: PlayerConnection) {
    val context = LocalContext.current
    val unknown = stringResource(R.string.music_unknown_artist)
    var library by remember { mutableStateOf<Library?>(null) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(reload) { library = MusicLibrary.load(context, unknown) }
    OnResume { reload++ }
    val now by player.state.collectAsState()

    var tab by rememberSaveable { mutableStateOf(MusicTab.SONGS) }
    var query by rememberSaveable { mutableStateOf("") }
    var openAlbum by rememberSaveable { mutableStateOf<Long?>(null) }
    var openArtist by rememberSaveable { mutableStateOf<String?>(null) }
    var showPlayer by rememberSaveable { mutableStateOf(false) }

    val lib = library
    if (lib == null) return
    if (lib.songs.isEmpty()) {
        EmptyState(MesOSGlyphs.Music, stringResource(R.string.music_empty), message = stringResource(R.string.music_empty_hint))
        return
    }

    BackHandler(enabled = showPlayer || openAlbum != null || openArtist != null) {
        when {
            showPlayer -> showPlayer = false
            else -> {
                openAlbum = null
                openArtist = null
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            val album = openAlbum?.let { id -> lib.albums.firstOrNull { it.id == id } }
            val artist = openArtist?.let { name -> lib.artists.firstOrNull { it.name == name } }
            when {
                album != null -> SongList(
                    title = album.title,
                    subtitle = album.artist,
                    songs = album.songs,
                    current = now.songId,
                    onBack = { openAlbum = null },
                    onPlay = { index, shuffle -> player.play(album.songs, index, shuffle) },
                )
                artist != null -> SongList(
                    title = artist.name,
                    subtitle = stringResource(R.string.music_song_count, artist.songs.size),
                    songs = artist.songs,
                    current = now.songId,
                    onBack = { openArtist = null },
                    onPlay = { index, shuffle -> player.play(artist.songs, index, shuffle) },
                )
                else -> {
                    Text(stringResource(R.string.music_app_name), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(start = 20.dp, top = 16.dp))
                    MesOSSearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = stringResource(R.string.music_search),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MusicTab.entries.forEach { value ->
                            val selected = tab == value
                            Text(
                                stringResource(
                                    when (value) {
                                        MusicTab.SONGS -> R.string.music_tab_songs
                                        MusicTab.ALBUMS -> R.string.music_tab_albums
                                        MusicTab.ARTISTS -> R.string.music_tab_artists
                                    },
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (selected) MaterialTheme.colorScheme.primary else MesOSTheme.colors.card)
                                    .clickable { tab = value }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                    val filtered = remember(lib, query) {
                        if (query.isBlank()) lib.songs else lib.songs.filter { song ->
                            SearchText.matches(query, song.title) || SearchText.matches(query, song.artist) || SearchText.matches(query, song.album)
                        }
                    }
                    when {
                        query.isNotBlank() || tab == MusicTab.SONGS -> SongList(
                            title = null,
                            subtitle = null,
                            songs = filtered,
                            current = now.songId,
                            onBack = null,
                            onPlay = { index, shuffle -> player.play(filtered, index, shuffle) },
                        )
                        tab == MusicTab.ALBUMS -> LazyVerticalGrid(
                            columns = GridCells.Adaptive(160.dp),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            items(lib.albums, key = { it.id }) { a ->
                                Column(Modifier.clickable { openAlbum = a.id }) {
                                    Cover(a.songs.first(), Modifier.fillMaxWidth().aspectRatio(1f), 20.dp)
                                    Spacer(Modifier.height(6.dp))
                                    Text(a.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(a.artist, style = MaterialTheme.typography.bodySmall, color = MesOSTheme.colors.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        else -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp)) {
                            items(lib.artists, key = { it.name }) { a ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { openArtist = a.name }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    Cover(a.songs.first(), Modifier.size(52.dp), 26.dp)
                                    Column(Modifier.weight(1f)) {
                                        Text(a.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(stringResource(R.string.music_song_count, a.songs.size), style = MaterialTheme.typography.bodySmall, color = MesOSTheme.colors.dim)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (now.songId != null && !showPlayer) {
            MiniPlayer(
                now = now,
                onOpen = { showPlayer = true },
                onToggle = player::toggle,
                onNext = { player.next() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
            )
        }
        AnimatedVisibility(
            visible = showPlayer && now.songId != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            NowPlayingScreen(now, player, onClose = { showPlayer = false })
        }
    }
}

@Composable
private fun SongList(
    title: String?,
    subtitle: String?,
    songs: List<Song>,
    current: Long?,
    onBack: (() -> Unit)?,
    onPlay: (index: Int, shuffle: Boolean) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp)) {
        if (title != null) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(MesOSGlyphs.Back, contentDescription = stringResource(R.string.music_back)) }
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MesOSTheme.colors.dim)
                    }
                }
            }
        }
        item {
            Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(stringResource(R.string.music_play_all), MesOSGlyphs.Play, primary = true) { onPlay(0, false) }
                PillButton(stringResource(R.string.music_shuffle), MesOSGlyphs.Shuffle, primary = false) { onPlay((songs.indices).random(), true) }
            }
        }
        itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
            val playing = song.id == current
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onPlay(index, false) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Cover(song, Modifier.size(50.dp), 12.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        song.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${song.artist} · ${formatTime(song.durationMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MesOSTheme.colors.dim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (playing) Icon(MesOSGlyphs.Volume, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun PillButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, primary: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (primary) MaterialTheme.colorScheme.primary else MesOSTheme.colors.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val color = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
private fun Cover(song: Song, modifier: Modifier, corner: Dp) {
    CoverFor(song.uri, modifier, corner)
}

@Composable
private fun CoverFor(uri: android.net.Uri?, modifier: Modifier, corner: Dp) {
    val art = rememberContentThumbnail(uri, 256.dp)
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(Brush.linearGradient(listOf(MesOSTheme.colors.accentBright, Color(0xFFDB2777)))),
        contentAlignment = Alignment.Center,
    ) {
        if (art != null) {
            Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(MesOSGlyphs.Music, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.fillMaxSize(0.42f))
        }
    }
}

@Composable
private fun MiniPlayer(now: NowPlaying, onOpen: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit, modifier: Modifier) {
    Row(
        modifier
            .padding(12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MesOSTheme.colors.card)
            .clickable(onClick = onOpen)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CoverFor(now.songUri, Modifier.size(48.dp), 12.dp)
        Column(Modifier.weight(1f)) {
            Text(now.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(now.artist, style = MaterialTheme.typography.bodySmall, color = MesOSTheme.colors.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onToggle) {
            Icon(if (now.isPlaying) MesOSGlyphs.Pause else MesOSGlyphs.Play, contentDescription = stringResource(if (now.isPlaying) R.string.music_pause else R.string.music_play))
        }
        IconButton(onClick = onNext) { Icon(MesOSGlyphs.Next, contentDescription = stringResource(R.string.music_next)) }
    }
}

@Composable
private fun NowPlayingScreen(now: NowPlaying, player: PlayerConnection, onClose: () -> Unit) {
    var position by remember { mutableLongStateOf(player.position()) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(now.songId, now.isPlaying) {
        while (true) {
            if (!dragging) position = player.position()
            delay(500)
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth()) {
                IconButton(onClick = onClose) { Icon(MesOSGlyphs.ChevronDown, contentDescription = stringResource(R.string.music_close_player)) }
            }
            Spacer(Modifier.height(12.dp))
            CoverFor(now.songUri, Modifier.fillMaxWidth().aspectRatio(1f), 32.dp)
            Spacer(Modifier.height(28.dp))
            Text(now.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(now.artist, style = MaterialTheme.typography.titleMedium, color = MesOSTheme.colors.dim, maxLines = 1)
            Spacer(Modifier.height(20.dp))
            val duration = now.durationMs.coerceAtLeast(1L)
            Slider(
                value = if (dragging) dragValue else (position.toFloat() / duration).coerceIn(0f, 1f),
                onValueChange = {
                    dragging = true
                    dragValue = it
                },
                onValueChangeFinished = {
                    player.seekTo((dragValue * duration).toLong())
                    position = (dragValue * duration).toLong()
                    dragging = false
                },
            )
            Row(Modifier.fillMaxWidth()) {
                Text(formatTime(if (dragging) (dragValue * duration).toLong() else position), style = MaterialTheme.typography.labelMedium, color = MesOSTheme.colors.dim, modifier = Modifier.weight(1f))
                Text(formatTime(now.durationMs), style = MaterialTheme.typography.labelMedium, color = MesOSTheme.colors.dim)
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = player::toggleShuffle) {
                    Icon(MesOSGlyphs.Shuffle, contentDescription = stringResource(R.string.music_shuffle), tint = if (now.shuffle) MaterialTheme.colorScheme.primary else MesOSTheme.colors.dim)
                }
                IconButton(onClick = { player.previous() }) { Icon(MesOSGlyphs.Previous, contentDescription = stringResource(R.string.music_previous), modifier = Modifier.size(30.dp)) }
                Box(
                    Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = player::toggle),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (now.isPlaying) MesOSGlyphs.Pause else MesOSGlyphs.Play,
                        contentDescription = stringResource(if (now.isPlaying) R.string.music_pause else R.string.music_play),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp),
                    )
                }
                IconButton(onClick = { player.next() }) { Icon(MesOSGlyphs.Next, contentDescription = stringResource(R.string.music_next), modifier = Modifier.size(30.dp)) }
                IconButton(onClick = player::cycleRepeat) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(MesOSGlyphs.Repeat, contentDescription = stringResource(R.string.music_repeat), tint = if (now.repeat != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MesOSTheme.colors.dim)
                        if (now.repeat == Player.REPEAT_MODE_ONE) Text("1", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return if (total >= 3600) "%d:%02d:%02d".format(total / 3600, total / 60 % 60, total % 60) else "%d:%02d".format(total / 60, total % 60)
}
