package org.mesos.recorder

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.format.DateUtils
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import org.mesos.core.log.MesOSLog
import org.mesos.core.system.ScreenRecording
import org.mesos.core.ui.EmptyState
import org.mesos.core.ui.GroupLabel
import org.mesos.core.ui.ListGroup
import org.mesos.core.ui.ListRow
import org.mesos.core.ui.MesOSCard
import org.mesos.core.ui.MesOSGlyphs
import org.mesos.core.ui.MesOSListScreen
import org.mesos.core.ui.MesOSPalette
import org.mesos.core.ui.OnResume
import org.mesos.core.ui.hasPermission
import org.mesos.core.ui.rememberHaptics
import org.mesos.core.ui.startActivitySafely
import org.mesos.core.ui.theme.MesOSTheme
import org.mesos.core.ui.theme.MesOSUserTheme
import org.mesos.core.ui.theme.Sora

/** MesOS Recorder: voice recordings, plus the entry point for screen recording. */
class RecorderActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MesOSLog.i(MesOSLog.SYSTEM, "MesOS Recorder opened")
        setContent {
            MesOSUserTheme {
                RecorderApp(onBack = ::finish)
            }
        }
    }
}

private val RecordRed = Color(0xFFF43F5E)

@Composable
private fun RecorderApp(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val state by VoiceRecorder.state.collectAsState()
    val levels by VoiceRecorder.levels.collectAsState()
    val saved by VoiceRecorder.saved.collectAsState()
    val error by VoiceRecorder.error.collectAsState()
    val screenActive by ScreenRecording.active.collectAsState()
    var recordings by remember { mutableStateOf<List<Recording>>(emptyList()) }
    var reload by remember { mutableIntStateOf(0) }
    val player = remember { ClipPlayer() }
    DisposableEffect(Unit) { onDispose { player.release() } }

    LaunchedEffect(saved, reload) { recordings = withContext(Dispatchers.IO) { RecordingStore.list(context) } }
    OnResume { reload++ }

    val errorText = stringResource(R.string.recorder_error)
    LaunchedEffect(error) {
        if (error) {
            Toast.makeText(context, errorText, Toast.LENGTH_LONG).show()
            VoiceRecorder.clearError()
        }
    }

    val start: () -> Unit = {
        player.stop()
        haptics.confirm()
        context.startForegroundService(VoiceRecordService.intent(context, VoiceRecordService.ACTION_START))
    }
    val permissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }
    val micDenied = stringResource(R.string.recorder_mic_rationale)
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) start() else Toast.makeText(context, micDenied, Toast.LENGTH_LONG).show()
    }
    val record = {
        if (context.hasPermission(Manifest.permission.RECORD_AUDIO)) start() else askMic.launch(permissions.toTypedArray())
    }
    val send: (String) -> Unit = { action ->
        haptics.tick()
        context.startService(VoiceRecordService.intent(context, action))
    }

    MesOSListScreen(title = stringResource(R.string.recorder_app_name), onBack = onBack) {
        item(key = "recorder") {
            MesOSCard {
                RecorderPanel(state = state, levels = levels, onRecord = record, onSend = send)
            }
        }
        item(key = "screen") {
            ListGroup {
                ListRow(
                    title = stringResource(if (screenActive) R.string.recorder_screen_stop else R.string.recorder_screen_start),
                    subtitle = stringResource(R.string.recorder_screen_summary),
                    icon = MesOSGlyphs.Record,
                    iconColor = MesOSPalette.Rose,
                    onClick = { context.startActivitySafely(ScreenRecordService.toggleIntent(context)) },
                )
            }
        }
        item(key = "list-title") { GroupLabel(stringResource(R.string.recorder_list_title)) }
        if (recordings.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = MesOSGlyphs.Mic,
                    title = stringResource(R.string.recorder_empty),
                    message = stringResource(R.string.recorder_empty_text),
                )
            }
        }
        items(recordings, key = { it.uri.toString() }) { recording ->
            RecordingRow(
                recording = recording,
                player = player,
                onChanged = { reload++ },
                scope = scope,
            )
        }
    }
}

@Composable
private fun RecorderPanel(
    state: VoiceRecorder.State,
    levels: List<Float>,
    onRecord: () -> Unit,
    onSend: (String) -> Unit,
) {
    val recording = state as? VoiceRecorder.State.Recording
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(recording) {
        while (recording != null && !recording.paused) {
            now = SystemClock.elapsedRealtime()
            delay(100)
        }
    }
    val elapsed = recording?.elapsed(now) ?: 0L
    val accent = if (recording?.paused == true) MesOSTheme.colors.dim else RecordRed

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Waveform(levels = if (recording == null) emptyList() else levels, color = accent)
        Text(
            formatClock(elapsed),
            style = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Light, fontSize = 48.sp),
            color = if (recording == null) MesOSTheme.colors.dim else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            stringResource(
                when {
                    recording == null -> R.string.recorder_ready
                    recording.paused -> R.string.recorder_paused
                    else -> R.string.recorder_recording
                },
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MesOSTheme.colors.dim,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SideButton(
                icon = MesOSGlyphs.Trash,
                label = stringResource(R.string.recorder_discard),
                visible = recording != null,
                onClick = { onSend(VoiceRecordService.ACTION_DISCARD) },
            )
            RecordButton(
                recording = recording != null,
                onClick = { if (recording == null) onRecord() else onSend(VoiceRecordService.ACTION_STOP) },
            )
            SideButton(
                icon = if (recording?.paused == true) MesOSGlyphs.Play else MesOSGlyphs.Pause,
                label = stringResource(if (recording?.paused == true) R.string.recorder_resume else R.string.recorder_pause),
                visible = recording != null,
                onClick = {
                    onSend(if (recording?.paused == true) VoiceRecordService.ACTION_RESUME else VoiceRecordService.ACTION_PAUSE)
                },
            )
        }
    }
}

@Composable
private fun Waveform(levels: List<Float>, color: Color) {
    val idle = MesOSTheme.colors.separator
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(96.dp),
    ) {
        val count = VoiceRecorder.LEVEL_HISTORY
        val gap = 3.dp.toPx()
        val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(1f)
        val middle = size.height / 2
        val minHeight = 4.dp.toPx()
        // Newest level on the right, scrolling left.
        val offset = count - levels.size
        for (i in 0 until count) {
            val level = levels.getOrNull(i - offset)
            val height = if (level == null) minHeight else (minHeight + level * (size.height - minHeight))
            drawRoundRect(
                color = if (level == null) idle else color.copy(alpha = 0.45f + 0.55f * level),
                topLeft = Offset(i * (barWidth + gap), middle - height / 2),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2),
            )
        }
    }
}

@Composable
private fun RecordButton(recording: Boolean, onClick: () -> Unit) {
    val inner by animateDpAsState(if (recording) 28.dp else 58.dp, label = "recordInner")
    val corner by animateDpAsState(if (recording) 8.dp else 29.dp, label = "recordCorner")
    val label = stringResource(if (recording) R.string.recorder_stop else R.string.recorder_start)
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(RecordRed.copy(alpha = 0.16f))
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(inner)
                .clip(RoundedCornerShape(corner))
                .background(RecordRed),
        )
    }
}

@Composable
private fun SideButton(icon: ImageVector, label: String, visible: Boolean, onClick: () -> Unit) {
    val tint by animateColorAsState(if (visible) MaterialTheme.colorScheme.onSurface else Color.Transparent, label = "sideTint")
    IconButton(
        onClick = onClick,
        enabled = visible,
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(if (visible) MesOSTheme.colors.separator else Color.Transparent),
    ) {
        Icon(icon, contentDescription = label, tint = tint)
    }
}

@Composable
private fun RecordingRow(
    recording: Recording,
    player: ClipPlayer,
    onChanged: () -> Unit,
    scope: CoroutineScope,
) {
    val context = LocalContext.current
    val playing = player.uri == recording.uri && player.playing
    val current = player.uri == recording.uri
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val deleteConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) onChanged()
    }
    val failed = stringResource(R.string.recorder_action_failed)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MesOSTheme.colors.card)
            .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { if (playing) player.pause() else player.play(context, recording.uri) },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            ) {
                Icon(
                    if (playing) MesOSGlyphs.Pause else MesOSGlyphs.Play,
                    contentDescription = stringResource(if (playing) R.string.recorder_pause else R.string.recorder_play),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(recording.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(
                        DateUtils.formatDateTime(context, recording.dateMs, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH),
                        formatClock(recording.durationMs),
                        Formatter.formatShortFileSize(context, recording.sizeBytes),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MesOSTheme.colors.dim,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(MesOSGlyphs.More, contentDescription = stringResource(org.mesos.core.R.string.mesos_more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(org.mesos.core.R.string.mesos_share)) },
                        leadingIcon = { Icon(MesOSGlyphs.Share, contentDescription = null) },
                        onClick = {
                            menu = false
                            share(context, recording)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(org.mesos.core.R.string.mesos_rename)) },
                        leadingIcon = { Icon(MesOSGlyphs.Edit, contentDescription = null) },
                        onClick = {
                            menu = false
                            renaming = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(org.mesos.core.R.string.mesos_delete), color = MesOSTheme.colors.danger) },
                        leadingIcon = { Icon(MesOSGlyphs.Trash, contentDescription = null, tint = MesOSTheme.colors.danger) },
                        onClick = {
                            menu = false
                            confirmDelete = true
                        },
                    )
                }
            }
        }
        if (current) {
            val duration = player.duration.coerceAtLeast(1)
            Column(Modifier.padding(start = 8.dp, end = 12.dp, top = 6.dp)) {
                LinearProgressIndicator(
                    progress = { (player.position.toFloat() / duration).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatClock(player.position.toLong()), style = MaterialTheme.typography.labelSmall, color = MesOSTheme.colors.dim)
                    Text(formatClock(player.duration.toLong()), style = MaterialTheme.typography.labelSmall, color = MesOSTheme.colors.dim)
                }
            }
        }
    }

    if (renaming) {
        var name by remember(recording) { mutableStateOf(recording.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(org.mesos.core.R.string.mesos_rename)) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    renaming = false
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { RecordingStore.rename(context, recording, name) }
                        if (ok) onChanged() else Toast.makeText(context, failed, Toast.LENGTH_SHORT).show()
                    }
                }) { Text(stringResource(org.mesos.core.R.string.mesos_save)) }
            },
            dismissButton = {
                TextButton(onClick = { renaming = false }) { Text(stringResource(org.mesos.core.R.string.mesos_cancel)) }
            },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.recorder_delete_title, recording.name)) },
            text = { Text(stringResource(R.string.recorder_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    if (player.uri == recording.uri) player.stop()
                    scope.launch {
                        when (val result = withContext(Dispatchers.IO) { RecordingStore.delete(context, recording) }) {
                            DeleteResult.Deleted -> onChanged()
                            is DeleteResult.NeedsConsent -> deleteConsent.launch(IntentSenderRequest.Builder(result.request.intentSender).build())
                            DeleteResult.Failed -> Toast.makeText(context, failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text(stringResource(org.mesos.core.R.string.mesos_delete), color = MesOSTheme.colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(org.mesos.core.R.string.mesos_cancel)) }
            },
        )
    }
}

private fun share(context: Context, recording: Recording) {
    val uri = RecordingStore.shareUri(context, recording)
    val send = Intent(Intent.ACTION_SEND)
        .setType(MediaKind.AUDIO.mime)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivitySafely(Intent.createChooser(send, recording.name))
}

/** m:ss (or h:mm:ss). */
internal fun formatClock(millis: Long): String {
    val total = millis / 1000
    val hours = total / 3600
    val minutes = (total / 60) % 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

/** Plays one recording at a time; progress is observable state. */
@Stable
private class ClipPlayer {
    private var media: MediaPlayer? = null
    var uri by mutableStateOf<Uri?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    var position by mutableIntStateOf(0)
        private set
    var duration by mutableIntStateOf(0)
        private set
    private var ticker: Job? = null
    private val scope = MainScope()

    fun play(context: Context, target: Uri) {
        if (uri == target && media != null) {
            media?.start()
            playing = true
            tick()
            return
        }
        stop()
        val next = MediaPlayer()
        try {
            next.setDataSource(context, target)
            next.setOnCompletionListener {
                playing = false
                position = duration
                ticker?.cancel()
            }
            next.prepare()
            next.start()
        } catch (e: IOException) {
            next.release()
            return
        } catch (e: RuntimeException) {
            next.release()
            return
        }
        media = next
        uri = target
        duration = next.duration
        playing = true
        tick()
    }

    fun pause() {
        media?.pause()
        playing = false
        ticker?.cancel()
    }

    fun stop() {
        ticker?.cancel()
        media?.release()
        media = null
        uri = null
        playing = false
        position = 0
        duration = 0
    }

    fun release() {
        stop()
        scope.cancel()
    }

    private fun tick() {
        ticker?.cancel()
        ticker = scope.launch {
            while (playing) {
                position = media?.currentPosition ?: 0
                delay(200)
            }
        }
    }
}
