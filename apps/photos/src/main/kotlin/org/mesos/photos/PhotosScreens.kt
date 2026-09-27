package org.mesos.photos

import android.Manifest
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.mesos.core.ui.EmptyState
import org.mesos.core.ui.MesOSGlyphs
import org.mesos.core.ui.MesOSTopBar
import org.mesos.core.ui.OnResume
import org.mesos.core.ui.PermissionGate
import org.mesos.core.ui.hasPermission

private enum class Tab { PHOTOS, ALBUMS }

@Composable
internal fun PhotosApp(viewUri: Uri?, viewType: String?, onExit: () -> Unit) {
    if (viewUri != null) {
        SingleItemViewer(uri = viewUri, mimeType = viewType, onExit = onExit)
        return
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.safeDrawingPadding()) {
            PermissionGate(
                permissions = MediaAccess.requested(),
                rationale = stringResource(R.string.photos_access_rationale),
                isGranted = MediaAccess::granted,
            ) {
                Gallery(onExit = onExit)
            }
        }
    }
}

internal object MediaAccess {

    fun requested(): List<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    fun granted(context: Context): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
            context.hasPermission(Manifest.permission.READ_MEDIA_IMAGES) ||
                context.hasPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            context.hasPermission(Manifest.permission.READ_MEDIA_IMAGES)
        else -> context.hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** Android 14+: the user shared only some photos. */
    fun limited(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            !context.hasPermission(Manifest.permission.READ_MEDIA_IMAGES) &&
            context.hasPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
}

@Composable
private fun Gallery(onExit: () -> Unit) {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf<List<MediaItem>?>(null) }
    var tab by rememberSaveable { mutableStateOf(Tab.PHOTOS) }
    var albumName by rememberSaveable { mutableStateOf<String?>(null) }
    var viewerIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var limited by remember { mutableStateOf(MediaAccess.limited(context)) }
    val requestMore = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        limited = MediaAccess.limited(context)
        version++
    }

    LaunchedEffect(version) { items = MediaRepository.load(context) }
    OnResume {
        limited = MediaAccess.limited(context)
        version++
    }
    MediaChanges { version++ }

    val all = items.orEmpty()
    val albums = remember(all) { MediaRepository.albums(all) }
    val shown = albumName?.let { name -> albums.firstOrNull { it.name == name }?.items } ?: all

    // Decide about the viewer only once the list has loaded (it may be restoring after rotation).
    val index = viewerIndex
    if (index != null && items != null) {
        if (shown.isNotEmpty()) {
            MediaViewer(
                items = shown,
                startIndex = index.coerceIn(0, shown.lastIndex),
                onClose = { viewerIndex = null },
            )
            return
        }
        LaunchedEffect(Unit) { viewerIndex = null }
    }

    BackHandler {
        when {
            albumName != null -> albumName = null
            else -> onExit()
        }
    }

    Column(Modifier.fillMaxSize()) {
        MesOSTopBar(
            title = albumName ?: stringResource(R.string.photos_app_name),
            onBack = albumName?.let { { albumName = null } },
        )
        if (albumName == null) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = tab == Tab.PHOTOS, onClick = { tab = Tab.PHOTOS }, label = { Text(stringResource(R.string.photos_tab_photos)) })
                FilterChip(selected = tab == Tab.ALBUMS, onClick = { tab = Tab.ALBUMS }, label = { Text(stringResource(R.string.photos_tab_albums)) })
            }
        }
        if (limited) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.photos_limited_access),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { requestMore.launch(MediaAccess.requested().toTypedArray()) }) {
                        Text(stringResource(R.string.photos_allow_all))
                    }
                }
            }
        }
        when {
            items == null -> Unit
            all.isEmpty() -> EmptyState(
                icon = MesOSGlyphs.Image,
                title = stringResource(R.string.photos_empty_title),
                message = stringResource(R.string.photos_empty),
            )
            albumName == null && tab == Tab.ALBUMS -> AlbumGrid(albums) { albumName = it.name }
            else -> MediaGrid(shown) { viewerIndex = it }
        }
    }
}

/** Calls [onChange] (debounced) when photos or videos are added, changed or removed. */
@Composable
private fun MediaChanges(onChange: () -> Unit) {
    val context = LocalContext.current
    DisposableEffect(context) {
        val handler = Handler(Looper.getMainLooper())
        val notify = Runnable { onChange() }
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                handler.removeCallbacks(notify)
                handler.postDelayed(notify, 400)
            }
        }
        val resolver = context.contentResolver
        resolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        resolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
        onDispose {
            resolver.unregisterContentObserver(observer)
            handler.removeCallbacks(notify)
        }
    }
}

@Composable
private fun MediaGrid(items: List<MediaItem>, onOpen: (Int) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        contentPadding = PaddingValues(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items.size, key = { items[it].uri.toString() }) { index ->
            val item = items[index]
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onOpen(index) },
            ) {
                AsyncImage(
                    model = item.uri,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (item.isVideo) {
                    Text(
                        text = "▶ " + formatDuration(item.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumGrid(albums: List<Album>, onOpen: (Album) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(albums, key = { it.name }) { album ->
            Column(Modifier.clickable { onOpen(album) }) {
                AsyncImage(
                    model = album.items.first().uri,
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium),
                )
                Text(
                    album.name.ifEmpty { stringResource(R.string.photos_other) },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    album.items.size.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

internal fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
