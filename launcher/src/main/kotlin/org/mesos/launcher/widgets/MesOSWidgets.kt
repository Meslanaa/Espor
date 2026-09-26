package org.mesos.launcher.widgets

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mesos.core.MesOSApps
import org.mesos.core.ui.MesOSGlyphs
import org.mesos.core.ui.glass
import org.mesos.core.ui.startActivitySafely
import org.mesos.core.ui.theme.MesOSTheme
import org.mesos.core.ui.theme.Sora
import org.mesos.launcher.R
import org.mesos.launcher.control.NotificationCenter
import org.mesos.launcher.layout.WidgetKinds
import org.mesos.notes.Note
import org.mesos.notes.NotesFeed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val WidgetShape = RoundedCornerShape(26.dp)

private val ShadowText = Shadow(color = Color.Black.copy(alpha = 0.45f), offset = Offset(0f, 2f), blurRadius = 10f)

/** Draws the MesOS widget [kind]; unknown kinds draw nothing. */
@Composable
internal fun MesOSWidget(kind: String, nowMillis: Long, modifier: Modifier = Modifier) {
    when (kind) {
        WidgetKinds.CLOCK -> ClockWidget(nowMillis, modifier)
        WidgetKinds.WEATHER -> WeatherWidget(modifier)
        WidgetKinds.AGENDA -> AgendaWidget(nowMillis, modifier)
        WidgetKinds.NOTES -> NotesWidget(modifier)
        WidgetKinds.MUSIC -> MusicWidget(modifier)
        WidgetKinds.BATTERY -> BatteryWidget(modifier)
    }
}

@Composable
private fun openApp(className: String): () -> Unit {
    val context = LocalContext.current
    return { context.startActivitySafely(MesOSApps.launchIntent(context, className)) }
}

@Composable
private fun ClockWidget(nowMillis: Long, modifier: Modifier) {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val is24Hour = DateFormat.is24HourFormat(context)
    val zone = TimeZone.getDefault().id
    // The AM/PM marker is shown small next to the digits, so it is taken out of the pattern.
    val timeFormat = remember(locale, is24Hour, zone) {
        val pattern = DateFormat.getBestDateTimePattern(locale, if (is24Hour) "Hm" else "hm")
        SimpleDateFormat(pattern.replace("a", "").replace("\u202F", " ").trim(), locale)
    }
    val markerFormat = remember(locale, is24Hour, zone) { if (is24Hour) null else SimpleDateFormat("a", locale) }
    val dateFormat = remember(locale, zone) {
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEEMMMMd"), locale)
    }
    val alarm = remember(nowMillis) {
        context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime
    }
    val alarmFormat = remember(locale, is24Hour, zone) {
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, if (is24Hour) "EEEHm" else "EEEhm"), locale)
    }
    BoxWithConstraints(
        modifier
            .clickable(onClick = openApp(MesOSApps.CLOCK))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val clockSize = (maxHeight.value * 0.52f).coerceIn(48f, 96f).sp
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = timeFormat.format(Date(nowMillis)),
                    style = TextStyle(
                        fontFamily = Sora,
                        fontWeight = FontWeight.Light,
                        fontSize = clockSize,
                        letterSpacing = (-2).sp,
                        color = Color.White,
                        shadow = ShadowText,
                    ),
                    maxLines = 1,
                )
                markerFormat?.let { marker ->
                    Text(
                        text = marker.format(Date(nowMillis)),
                        style = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Normal, fontSize = (clockSize.value * 0.3f).sp, color = Color.White, shadow = ShadowText),
                        modifier = Modifier.padding(bottom = (clockSize.value * 0.22f).dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = dateFormat.format(Date(nowMillis)),
                    style = MaterialTheme.typography.titleMedium.copy(color = Color.White, shadow = ShadowText),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (alarm != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(MesOSGlyphs.Alarm, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(15.dp))
                        Text(
                            text = alarmFormat.format(Date(alarm)),
                            style = MaterialTheme.typography.labelLarge.copy(color = Color.White.copy(alpha = 0.85f), shadow = ShadowText),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** A glass card with a small coloured heading, the common frame of MesOS widgets. */
@Composable
internal fun WidgetCard(
    title: String,
    modifier: Modifier = Modifier,
    titleColor: Color = Color.White.copy(alpha = 0.78f),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .glass(WidgetShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = titleColor, maxLines = 1)
        content()
    }
}

@Composable
private fun NotesWidget(modifier: Modifier) {
    val context = LocalContext.current
    val changes by remember { NotesFeed.changes(context) }.collectAsState()
    var notes by remember { mutableStateOf<List<Note>>(emptyList()) }
    LaunchedEffect(changes) { notes = NotesFeed.recent(context, 3) }
    WidgetCard(
        title = stringResource(R.string.widget_notes),
        modifier = modifier,
        titleColor = Color(0xFFFFD76A),
        onClick = openApp(MesOSApps.NOTES),
    ) {
        if (notes.isEmpty()) {
            Text(
                stringResource(R.string.widget_notes_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
            )
        }
        notes.forEach { note ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { context.startActivitySafely(NotesFeed.openIntent(context, note.id)) },
            ) {
                Text(
                    text = note.title.ifBlank { note.body.lineSequence().firstOrNull().orEmpty() },
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f))
                    .clickable { context.startActivitySafely(NotesFeed.newNoteIntent(context)) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(MesOSGlyphs.Plus, contentDescription = stringResource(R.string.widget_notes_new), tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun MusicWidget(modifier: Modifier) {
    val context = LocalContext.current
    val media by NotificationCenter.media.collectAsState()
    val current = media
    Row(
        modifier = modifier
            .fillMaxSize()
            .glass(WidgetShape)
            .clickable {
                if (!NotificationCenter.openMediaApp(context)) {
                    context.startActivitySafely(MesOSApps.launchIntent(context, MesOSApps.MUSIC))
                }
            }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(MesOSTheme.colors.accentBright.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            val art = current?.art
            if (art != null) {
                Image(art.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(MesOSGlyphs.Music, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = current?.title?.ifBlank { null } ?: stringResource(R.string.widget_music_idle),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = current?.artist?.ifBlank { null } ?: stringResource(R.string.widget_music_open),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (current != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { NotificationCenter.previous() }) {
                        Icon(MesOSGlyphs.Previous, contentDescription = stringResource(R.string.control_media_previous), tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { NotificationCenter.playPause() }) {
                        Icon(
                            if (current.isPlaying) MesOSGlyphs.Pause else MesOSGlyphs.Play,
                            contentDescription = stringResource(if (current.isPlaying) R.string.control_media_pause else R.string.control_media_play),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    IconButton(onClick = { NotificationCenter.next() }) {
                        Icon(MesOSGlyphs.Next, contentDescription = stringResource(R.string.control_media_next), tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BatteryWidget(modifier: Modifier) {
    val context = LocalContext.current
    var level by remember { mutableIntStateOf(-1) }
    var charging by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val raw = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                level = if (raw < 0) -1 else raw * 100 / scale
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val sticky = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        sticky?.let { receiver.onReceive(context, it) }
        onDispose { context.unregisterReceiver(receiver) }
    }
    val accent = if (level in 0..15 && !charging) Color(0xFFFB7185) else Color(0xFF34D399)
    WidgetCard(
        title = stringResource(R.string.widget_battery),
        modifier = modifier,
        titleColor = accent,
        onClick = openApp(MesOSApps.CARE),
    ) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxHeight().aspectRatio(1f)) {
                val stroke = size.minDimension * 0.1f
                val inset = stroke / 2
                val arcSize = Size(size.minDimension - stroke, size.minDimension - stroke)
                drawArc(Color.White.copy(alpha = 0.18f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                if (level >= 0) {
                    drawArc(accent, -90f, 360f * level / 100f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (charging) Icon(MesOSGlyphs.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text(
                    text = if (level >= 0) "$level%" else "—",
                    style = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = Color.White),
                )
            }
        }
    }
}
