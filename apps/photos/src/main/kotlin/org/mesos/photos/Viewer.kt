package org.mesos.photos

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.format.DateUtils
import android.text.format.Formatter
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import org.mesos.core.R as CoreR
import org.mesos.core.ui.MesOSGlyphs
import org.mesos.core.ui.MesOSTopBar
import org.mesos.core.ui.startActivitySafely
import org.mesos.core.ui.theme.MesOSTheme

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

/** Viewer for one item handed over by another app (Files, Camera, …). */
@Composable
internal fun SingleItemViewer(uri: Uri, mimeType: String?, onExit: () -> Unit) {
    val context = LocalContext.current
    var item by remember { mutableStateOf<MediaItem?>(null) }
    LaunchedEffect(uri) { item = MediaRepository.describe(context, uri, mimeType) }
    item?.let { MediaViewer(items = listOf(it), startIndex = 0, onClose = onExit, onDeleted = onExit) }
}

/** Full-screen, swipeable viewer. Always dark, like a darkroom. */
@Composable
internal fun MediaViewer(
    items: List<MediaItem>,
    startIndex: Int,
    onClose: () -> Unit,
    onDeleted: () -> Unit = {},
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(initialPage = startIndex) { items.size }
    var chromeVisible by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var infoItem by remember { mutableStateOf<MediaItem?>(null) }
    var editing by remember { mutableStateOf<MediaItem?>(null) }
    // API 29: after the user allows it, the delete has to be retried.
    var retryDelete by remember { mutableStateOf<MediaItem?>(null) }

    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val retry = retryDelete
        retryDelete = null
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        if (retry != null) runCatching { context.contentResolver.delete(retry.uri, null, null) }
        onDeleted()
    }

    fun delete(item: MediaItem) {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val request = MediaStore.createDeleteRequest(resolver, listOf(item.uri))
            deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            return
        }
        try {
            resolver.delete(item.uri, null, null)
            onDeleted()
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                retryDelete = item
                deleteLauncher.launch(IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build())
            } else {
                Toast.makeText(context, R.string.photos_delete_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    BackHandler(onBack = onClose)
    LaunchedEffect(pagerState.currentPage) { zoomed = false }

    editing?.let { item ->
        MesOSTheme(darkTheme = true) {
            PhotoEditor(item = item, onClose = { editing = null }, onSaved = { editing = null })
        }
        return
    }

    MesOSTheme(darkTheme = true) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !zoomed,
                key = { items[it].uri.toString() },
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val item = items[page]
                val active = page == pagerState.currentPage
                if (item.isVideo) {
                    VideoPage(uri = item.uri, active = active, onTap = { chromeVisible = !chromeVisible })
                } else {
                    ZoomableImage(
                        item = item,
                        onTap = { chromeVisible = !chromeVisible },
                        onZoomChanged = { if (active) zoomed = it },
                    )
                }
            }

            val current = items.getOrNull(pagerState.currentPage)
            AnimatedVisibility(visible = chromeVisible, enter = fadeIn(), exit = fadeOut()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    MesOSTopBar(
                        title = current?.name.orEmpty(),
                        onBack = onClose,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.4f)),
                    )
                    if (current != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.4f)),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            if (!current.isVideo) {
                                IconButton(onClick = { editing = current }) {
                                    Icon(MesOSGlyphs.Edit, contentDescription = stringResource(R.string.editor_title), tint = Color.White)
                                }
                            }
                            IconButton(onClick = { share(context, current) }) {
                                Icon(Icons.Filled.Share, contentDescription = stringResource(CoreR.string.mesos_share), tint = Color.White)
                            }
                            if (current.isMediaStoreItem) {
                                IconButton(onClick = { delete(current) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = stringResource(CoreR.string.mesos_delete), tint = Color.White)
                                }
                            }
                            IconButton(onClick = { infoItem = current }) {
                                Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.photos_details), tint = Color.White)
                            }
                        }
                    }
                }
            }
        }

        infoItem?.let { item ->
            AlertDialog(
                onDismissRequest = { infoItem = null },
                title = { Text(item.name) },
                text = { Text(details(context, item)) },
                confirmButton = {
                    TextButton(onClick = { infoItem = null }) { Text(stringResource(CoreR.string.mesos_ok)) }
                },
            )
        }
    }
}

@Composable
private fun ZoomableImage(item: MediaItem, onTap: () -> Unit, onZoomChanged: (Boolean) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val tap by rememberUpdatedState(onTap)
    val zoomChanged by rememberUpdatedState(onZoomChanged)

    fun clamp(value: Offset, zoom: Float): Offset {
        val maxX = size.width * (zoom - 1) / 2
        val maxY = size.height * (zoom - 1) / 2
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { tap() },
                    onDoubleTap = { point ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            scale = DOUBLE_TAP_ZOOM
                            offset = clamp((center - point) * (DOUBLE_TAP_ZOOM - 1), DOUBLE_TAP_ZOOM)
                        }
                        zoomChanged(scale > 1f)
                    },
                )
            }
            .pointerInput(Unit) {
                // Pinch always zooms; a single finger pans only while zoomed, otherwise the
                // gesture is left to the pager so the user can swipe to the next photo.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pinching = event.changes.count { it.pressed } > 1
                        if (pinching || scale > 1f) {
                            val newScale = (scale * event.calculateZoom()).coerceIn(1f, MAX_ZOOM)
                            scale = newScale
                            offset = if (newScale > 1f) clamp(offset + event.calculatePan(), newScale) else Offset.Zero
                            zoomChanged(newScale > 1f)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}

@Composable
private fun VideoPage(uri: Uri, active: Boolean, onTap: () -> Unit) {
    val context = LocalContext.current
    val tap by rememberUpdatedState(onTap)
    val controller = remember { MediaController(context) }
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoURI(uri)
                    setMediaController(controller)
                }
            },
            update = { view ->
                if (active) {
                    if (!view.isPlaying) view.start()
                } else if (view.isPlaying) {
                    view.pause()
                }
            },
            onRelease = { view -> view.stopPlayback() },
            modifier = Modifier.align(Alignment.Center),
        )
        // Taps toggle the controls; swipes stay with the pager instead of the VideoView.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        runCatching { if (controller.isShowing) controller.hide() else controller.show() }
                        tap()
                    })
                },
        )
    }
}

private fun share(context: Context, item: MediaItem) {
    val send = Intent(Intent.ACTION_SEND)
        .setType(item.mimeType)
        .putExtra(Intent.EXTRA_STREAM, item.uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivitySafely(Intent.createChooser(send, context.getString(CoreR.string.mesos_share)))
}

private fun details(context: Context, item: MediaItem): String = buildString {
    if (item.dateTaken > 0) {
        appendLine(
            DateUtils.formatDateTime(
                context,
                item.dateTaken,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR,
            ),
        )
    }
    if (item.width > 0 && item.height > 0) appendLine("${item.width} × ${item.height}")
    if (item.size > 0) appendLine(Formatter.formatFileSize(context, item.size))
    if (item.isVideo && item.durationMs > 0) appendLine(formatDuration(item.durationMs))
    if (item.album.isNotEmpty()) appendLine(item.album)
    append(item.mimeType)
}
