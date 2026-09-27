package org.mesos.clock

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.AlarmClock
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONException
import org.mesos.core.ui.EmptyState
import org.mesos.core.ui.MesOSGlyphs
import org.mesos.core.ui.MesOSSearchField
import org.mesos.core.ui.hasPermission
import org.mesos.core.ui.rememberHaptics
import org.mesos.core.ui.startActivitySafely
import org.mesos.core.ui.theme.MesOSTheme
import org.mesos.core.ui.theme.MesOSUserTheme
import org.mesos.core.ui.theme.Sora
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DayTextStyle
import java.util.Locale
import java.util.TimeZone

internal enum class ClockTab { ALARMS, WORLD, STOPWATCH, TIMER }

/** MesOS Clock: alarms, world clock, stopwatch and timer. */
class ClockActivity : ComponentActivity() {

    private var tab by mutableStateOf(ClockTab.ALARMS)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tab = tabFor(intent) ?: savedInstanceState?.getString(EXTRA_TAB)?.let { runCatching { ClockTab.valueOf(it) }.getOrNull() } ?: ClockTab.ALARMS
        setContent { MesOSUserTheme { ClockApp(tab, onTab = { tab = it }) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        tabFor(intent)?.let { tab = it }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(EXTRA_TAB, tab.name)
    }

    private fun tabFor(intent: Intent?): ClockTab? = when (intent?.action) {
        AlarmClock.ACTION_SHOW_ALARMS -> ClockTab.ALARMS
        AlarmClock.ACTION_SHOW_TIMERS -> ClockTab.TIMER
        else -> intent?.getStringExtra(EXTRA_TAB)?.let { runCatching { ClockTab.valueOf(it) }.getOrNull() }
    }

    companion object {
        const val EXTRA_TAB = "org.mesos.clock.extra.TAB"
    }
}

/**
 * Other apps and assistants setting alarms and timers (AlarmClock intents). Callers
 * need Android's SET_ALARM permission; MesOS then opens Clock on the result.
 */
class ClockApiActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val engine = AlarmEngine.get(this)
        when (intent.action) {
            AlarmClock.ACTION_SET_ALARM -> {
                val hour = intent.getIntExtra(AlarmClock.EXTRA_HOUR, -1)
                val minute = intent.getIntExtra(AlarmClock.EXTRA_MINUTES, 0)
                if (hour in 0..23 && minute in 0..59) {
                    val days = intent.getIntegerArrayListExtra(AlarmClock.EXTRA_DAYS).orEmpty().fold(0) { mask, calendarDay ->
                        // java.util.Calendar: SUNDAY = 1 … SATURDAY = 7.
                        val dayOfWeek = if (calendarDay == 1) 7 else calendarDay - 1
                        if (dayOfWeek in 1..7) mask or (1 shl (dayOfWeek - 1)) else mask
                    }
                    engine.save(
                        Alarm(
                            id = 0,
                            hour = hour,
                            minute = minute,
                            days = days,
                            label = intent.getStringExtra(AlarmClock.EXTRA_MESSAGE).orEmpty(),
                            vibrate = intent.getBooleanExtra(AlarmClock.EXTRA_VIBRATE, true),
                        ),
                    )
                    Toast.makeText(this, getString(R.string.clock_alarm_set_at, "%02d:%02d".format(hour, minute)), Toast.LENGTH_SHORT).show()
                }
                openClock(ClockTab.ALARMS, intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false))
            }
            AlarmClock.ACTION_SET_TIMER -> {
                val seconds = intent.getIntExtra(AlarmClock.EXTRA_LENGTH, 0)
                if (seconds in 1..86_400) engine.startTimer(seconds * 1000L)
                openClock(ClockTab.TIMER, intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false))
            }
        }
        finish()
    }

    private fun openClock(tab: ClockTab, skipUi: Boolean) {
        if (skipUi) return
        startActivitySafely(Intent(this, ClockActivity::class.java).putExtra(ClockActivity.EXTRA_TAB, tab.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun ClockApp(tab: ClockTab, onTab: (ClockTab) -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MesOSTheme.colors.card) {
                listOf(
                    Triple(ClockTab.ALARMS, MesOSGlyphs.Alarm, R.string.clock_tab_alarms),
                    Triple(ClockTab.WORLD, MesOSGlyphs.Globe, R.string.clock_tab_world),
                    Triple(ClockTab.STOPWATCH, MesOSGlyphs.Stopwatch, R.string.clock_tab_stopwatch),
                    Triple(ClockTab.TIMER, MesOSGlyphs.Hourglass, R.string.clock_tab_timer),
                ).forEach { (value, icon, label) ->
                    NavigationBarItem(
                        selected = tab == value,
                        onClick = { onTab(value) },
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (tab) {
                ClockTab.ALARMS -> AlarmsTab()
                ClockTab.WORLD -> WorldTab()
                ClockTab.STOPWATCH -> StopwatchTab()
                ClockTab.TIMER -> TimerTab()
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp))
}

// ---------------------------------------------------------------- Alarms

@Composable
private fun AlarmsTab() {
    val context = LocalContext.current
    val engine = remember { AlarmEngine.get(context) }
    val alarms by engine.alarms.collectAsState()
    var editing by rememberSaveable { mutableStateOf<Int?>(null) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun announce(alarm: Alarm) {
        if (!alarm.enabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (!engine.canScheduleExact()) context.startActivitySafely(engine.exactAlarmSettingsIntent())
        val (h, m) = ClockMath.untilTrigger(engine.nextTrigger(alarm), System.currentTimeMillis())
        Toast.makeText(context, context.getString(R.string.clock_rings_in, h, m), Toast.LENGTH_SHORT).show()
    }

    val current = editing
    if (current != null) {
        BackHandler { editing = null }
        AlarmEditor(
            alarm = alarms.firstOrNull { it.id == current } ?: Alarm(0, 7, 0),
            onSave = { alarm ->
                announce(engine.save(alarm))
                editing = null
            },
            onDelete = { alarm ->
                engine.delete(alarm)
                editing = null
            },
            onCancel = { editing = null },
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Title(stringResource(R.string.clock_tab_alarms)) }
            if (alarms.isEmpty()) {
                item { EmptyState(MesOSGlyphs.Alarm, stringResource(R.string.clock_no_alarms), message = stringResource(R.string.clock_no_alarms_hint)) }
            }
            items(alarms, key = { it.id }) { alarm ->
                AlarmCard(
                    alarm = alarm,
                    onToggle = { enabled -> announce(engine.setEnabled(alarm, enabled)) },
                    onClick = { editing = alarm.id },
                )
            }
        }
        FloatingActionButton(
            onClick = { editing = 0 },
            containerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        ) { Icon(MesOSGlyphs.Plus, contentDescription = stringResource(R.string.clock_add_alarm)) }
    }
}

@Composable
private fun AlarmCard(alarm: Alarm, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MesOSTheme.colors.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .alpha(if (alarm.enabled) 1f else 0.5f),
        ) {
            Text(timeText(alarm.hour, alarm.minute), style = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Light, fontSize = 44.sp))
            Text(
                listOf(alarm.label, daysText(alarm)).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MesOSTheme.colors.dim,
            )
        }
        Switch(checked = alarm.enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun timeText(hour: Int, minute: Int): String {
    val context = LocalContext.current
    return if (DateFormat.is24HourFormat(context)) {
        "%02d:%02d".format(hour, minute)
    } else {
        val h = if (hour % 12 == 0) 12 else hour % 12
        "$h:%02d %s".format(minute, if (hour < 12) "AM" else "PM")
    }
}

@Composable
private fun daysText(alarm: Alarm): String = when (alarm.days) {
    0 -> stringResource(R.string.clock_once)
    Alarm.EVERY_DAY -> stringResource(R.string.clock_every_day)
    Alarm.WEEKDAYS -> stringResource(R.string.clock_weekdays)
    Alarm.WEEKEND -> stringResource(R.string.clock_weekend)
    else -> DayOfWeek.entries.filter { alarm.ringsOn(it) }.joinToString(", ") { it.getDisplayName(DayTextStyle.SHORT, Locale.getDefault()) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmEditor(alarm: Alarm, onSave: (Alarm) -> Unit, onDelete: (Alarm) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val time = rememberTimePickerState(alarm.hour, alarm.minute, DateFormat.is24HourFormat(context))
    var days by rememberSaveable { mutableStateOf(alarm.days) }
    var label by rememberSaveable { mutableStateOf(alarm.label) }
    var vibrate by rememberSaveable { mutableStateOf(alarm.vibrate) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) { Icon(MesOSGlyphs.Close, contentDescription = stringResource(R.string.clock_cancel)) }
            Text(
                stringResource(if (alarm.id == 0) R.string.clock_add_alarm else R.string.clock_edit_alarm),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                onSave(alarm.copy(hour = time.hour, minute = time.minute, days = days, label = label.trim(), vibrate = vibrate, enabled = true))
            }) { Text(stringResource(R.string.clock_save)) }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = time) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            DayOfWeek.entries.sortedBy { (it.value - java.time.temporal.WeekFields.of(Locale.getDefault()).firstDayOfWeek.value + 7) % 7 }.forEach { day ->
                val bit = 1 shl (day.value - 1)
                val on = days and bit != 0
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (on) MaterialTheme.colorScheme.primary else MesOSTheme.colors.card)
                        .clickable { days = days xor bit },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        day.getDisplayName(DayTextStyle.NARROW, Locale.getDefault()),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        OutlinedTextField(
            value = label,
            onValueChange = { label = it.take(60) },
            label = { Text(stringResource(R.string.clock_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.clock_vibrate), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Switch(checked = vibrate, onCheckedChange = { vibrate = it })
        }
        if (alarm.id != 0) {
            TextButton(onClick = { onDelete(alarm) }) {
                Icon(MesOSGlyphs.Trash, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.clock_delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ---------------------------------------------------------------- World clock

private const val WORLD_PREFS = "mesos_clock"
private const val KEY_ZONES = "world_zones"

private fun readZones(context: Context): List<String> =
    try {
        val array = JSONArray(context.getSharedPreferences(WORLD_PREFS, Context.MODE_PRIVATE).getString(KEY_ZONES, "[]"))
        (0 until array.length()).map { array.getString(it) }.filter { runCatching { ZoneId.of(it) }.isSuccess }
    } catch (e: JSONException) {
        emptyList()
    }

private fun writeZones(context: Context, zones: List<String>) {
    context.getSharedPreferences(WORLD_PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ZONES, JSONArray(zones).toString()).apply()
}

private fun cityOf(zone: String): String = zone.substringAfterLast('/').replace('_', ' ')

@Composable
private fun WorldTab() {
    val context = LocalContext.current
    var zones by remember { mutableStateOf(readZones(context)) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            now = System.currentTimeMillis()
        }
    }
    if (adding) {
        BackHandler { adding = false }
        ZonePicker(
            onPick = { zone ->
                zones = (zones + zone).distinct()
                writeZones(context, zones)
                adding = false
            },
            onCancel = { adding = false },
        )
        return
    }
    val here = ZoneId.systemDefault()
    val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    val formatter = remember(pattern) { DateTimeFormatter.ofPattern(pattern, Locale.getDefault()) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Title(stringResource(R.string.clock_tab_world)) }
            item {
                val local = ZonedDateTime.now(here)
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(local.format(formatter), style = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Light, fontSize = 64.sp))
                    Text(
                        TimeZone.getDefault().getDisplayName(false, TimeZone.LONG, Locale.getDefault()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MesOSTheme.colors.dim,
                    )
                }
            }
            if (zones.isEmpty()) {
                item { EmptyState(MesOSGlyphs.Globe, stringResource(R.string.clock_no_cities), message = stringResource(R.string.clock_no_cities_hint)) }
            }
            items(zones, key = { it }) { zone ->
                val time = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), ZoneId.of(zone))
                val diffHours = (time.offset.totalSeconds - ZonedDateTime.now(here).offset.totalSeconds) / 3600.0
                Row(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(MesOSTheme.colors.card)
                        .padding(start = 20.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(cityOf(zone), style = MaterialTheme.typography.titleMedium)
                        Text(offsetText(diffHours), style = MaterialTheme.typography.bodySmall, color = MesOSTheme.colors.dim)
                    }
                    Text(time.format(formatter), style = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Light, fontSize = 34.sp))
                    IconButton(onClick = {
                        zones = zones - zone
                        writeZones(context, zones)
                    }) { Icon(MesOSGlyphs.Close, contentDescription = stringResource(R.string.clock_remove), tint = MesOSTheme.colors.dim) }
                }
            }
        }
        FloatingActionButton(
            onClick = { adding = true },
            containerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        ) { Icon(MesOSGlyphs.Plus, contentDescription = stringResource(R.string.clock_add_city)) }
    }
}

@Composable
private fun offsetText(hours: Double): String = when {
    hours == 0.0 -> stringResource(R.string.clock_same_time)
    hours > 0 -> stringResource(R.string.clock_hours_ahead, formatHours(hours))
    else -> stringResource(R.string.clock_hours_behind, formatHours(-hours))
}

private fun formatHours(hours: Double): String = if (hours % 1.0 == 0.0) hours.toInt().toString() else "%.1f".format(hours)

@Composable
private fun ZonePicker(onPick: (String) -> Unit, onCancel: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val all = remember {
        ZoneId.getAvailableZoneIds()
            .filter { it.contains('/') && !it.startsWith("Etc/") && !it.startsWith("SystemV/") && it.first().isUpperCase() }
            .sortedBy { cityOf(it) }
    }
    val shown = remember(query) {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) all else all.filter { it.lowercase(Locale.ROOT).replace('_', ' ').contains(q) }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) { Icon(MesOSGlyphs.Back, contentDescription = stringResource(R.string.clock_cancel)) }
            Text(stringResource(R.string.clock_add_city), style = MaterialTheme.typography.titleLarge)
        }
        MesOSSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.clock_search_city),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            items(shown, key = { it }) { zone ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(zone) }
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                ) {
                    Text(cityOf(zone), style = MaterialTheme.typography.titleMedium)
                    Text(zone.substringBeforeLast('/').replace('_', ' '), style = MaterialTheme.typography.bodySmall, color = MesOSTheme.colors.dim)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Stopwatch

private class StopwatchState(context: Context) {
    private val prefs = context.getSharedPreferences(WORLD_PREFS, Context.MODE_PRIVATE)
    var running by mutableStateOf(prefs.getBoolean("sw_running", false))
    var startedAt by mutableLongStateOf(prefs.getLong("sw_started", 0L))
    var accumulated by mutableLongStateOf(prefs.getLong("sw_accumulated", 0L))
    var laps by mutableStateOf(readLaps())

    init {
        // After a reboot the elapsed-time base restarts; keep what was measured.
        if (running && startedAt > SystemClock.elapsedRealtime()) {
            running = false
            save()
        }
    }

    fun elapsed(now: Long = SystemClock.elapsedRealtime()): Long = accumulated + if (running) now - startedAt else 0L

    fun toggle() {
        val now = SystemClock.elapsedRealtime()
        if (running) {
            accumulated += now - startedAt
            running = false
        } else {
            startedAt = now
            running = true
        }
        save()
    }

    fun lapOrReset() {
        if (running) {
            laps = listOf(elapsed()) + laps
        } else {
            accumulated = 0L
            laps = emptyList()
        }
        save()
    }

    private fun readLaps(): List<Long> =
        try {
            val array = JSONArray(prefs.getString("sw_laps", "[]"))
            (0 until array.length()).map { array.getLong(it) }
        } catch (e: JSONException) {
            emptyList()
        }

    private fun save() {
        prefs.edit()
            .putBoolean("sw_running", running)
            .putLong("sw_started", startedAt)
            .putLong("sw_accumulated", accumulated)
            .putString("sw_laps", JSONArray(laps).toString())
            .apply()
    }
}

@Composable
private fun StopwatchTab() {
    val context = LocalContext.current
    val state = remember { StopwatchState(context) }
    val haptics = rememberHaptics()
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.running) {
        while (state.running) {
            withFrameMillis { now = SystemClock.elapsedRealtime() }
        }
        now = SystemClock.elapsedRealtime()
    }
    val elapsed = state.elapsed(now)
    Column(Modifier.fillMaxSize()) {
        Title(stringResource(R.string.clock_tab_stopwatch))
        Text(
            ClockMath.formatDuration(elapsed, withHundredths = true),
            style = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Light, fontSize = 72.sp, textAlign = TextAlign.Center),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundButton(
                label = stringResource(if (state.running) R.string.clock_lap else R.string.clock_reset),
                icon = if (state.running) MesOSGlyphs.Flag else MesOSGlyphs.Refresh,
                primary = false,
                enabled = state.running || elapsed > 0,
            ) {
                haptics.tick()
                state.lapOrReset()
            }
            RoundButton(
                label = stringResource(if (state.running) R.string.clock_pause else R.string.clock_start),
                icon = if (state.running) MesOSGlyphs.Pause else MesOSGlyphs.Play,
                primary = true,
            ) {
                haptics.confirm()
                state.toggle()
            }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            itemsIndexed(state.laps) { index, total ->
                val previous = state.laps.getOrNull(index + 1) ?: 0L
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 8.dp)) {
                    Text(stringResource(R.string.clock_lap_number, state.laps.size - index), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(ClockMath.formatDuration(total - previous, withHundredths = true), style = MaterialTheme.typography.titleMedium, color = MesOSTheme.colors.dim)
                    Spacer(Modifier.width(20.dp))
                    Text(ClockMath.formatDuration(total, withHundredths = true), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun RoundButton(label: String, icon: ImageVector, primary: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .size(if (primary) 84.dp else 68.dp)
                .clip(CircleShape)
                .background(if (primary) MaterialTheme.colorScheme.primary else MesOSTheme.colors.card)
                .clickable(enabled = enabled, onClick = onClick)
                .alpha(if (enabled) 1f else 0.4f),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(30.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

// ---------------------------------------------------------------- Timer

@Composable
private fun TimerTab() {
    val context = LocalContext.current
    val engine = remember { AlarmEngine.get(context) }
    val timer by engine.timer.collectAsState()
    val haptics = rememberHaptics()
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var digits by rememberSaveable { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timer.running) {
        while (timer.running) {
            withFrameMillis { now = System.currentTimeMillis() }
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Title(stringResource(R.string.clock_tab_timer))
        if (timer.active) {
            val remaining = if (timer.running) (timer.endAt - now).coerceAtLeast(0) else timer.remaining
            val fraction = if (timer.duration > 0) remaining.toFloat() / timer.duration else 0f
            val accent = MaterialTheme.colorScheme.primary
            val track = MesOSTheme.colors.card
            Box(
                Modifier
                    .padding(32.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = size.minDimension * 0.05f
                    val arc = Size(size.minDimension - stroke, size.minDimension - stroke)
                    drawArc(track, 0f, 360f, false, Offset(stroke / 2, stroke / 2), arc, style = Stroke(stroke))
                    drawArc(accent, -90f, 360f * fraction, false, Offset(stroke / 2, stroke / 2), arc, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Text(ClockMath.formatDuration(remaining + 999, withHundredths = false), style = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Light, fontSize = 64.sp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RoundButton(stringResource(R.string.clock_reset), MesOSGlyphs.Refresh, primary = false) { engine.resetTimer() }
                RoundButton(
                    stringResource(if (timer.running) R.string.clock_pause else R.string.clock_resume),
                    if (timer.running) MesOSGlyphs.Pause else MesOSGlyphs.Play,
                    primary = true,
                ) {
                    haptics.confirm()
                    if (timer.running) engine.pauseTimer() else engine.resumeTimer()
                }
            }
        } else {
            val padded = digits.padStart(6, '0')
            val h = padded.substring(0, 2).toInt()
            val m = padded.substring(2, 4).toInt()
            val s = padded.substring(4, 6).toInt()
            val total = (h * 3600L + m * 60L + s) * 1000L
            Text(
                "%02dh %02dm %02ds".format(h, m, s),
                style = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Light, fontSize = 48.sp, color = if (digits.isEmpty()) MesOSTheme.colors.dim else MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.padding(vertical = 16.dp),
            )
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "00", "0", "⌫")
            Column(Modifier.padding(horizontal = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                keys.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { key ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(60.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(MesOSTheme.colors.card)
                                    .clickable {
                                        haptics.key()
                                        digits = when (key) {
                                            "⌫" -> digits.dropLast(1)
                                            else -> (digits + key).trimStart('0').take(6)
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (key == "⌫") {
                                    Icon(MesOSGlyphs.Backspace, contentDescription = stringResource(R.string.clock_delete_digit))
                                } else {
                                    Text(key, style = MaterialTheme.typography.headlineSmall)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            RoundButton(stringResource(R.string.clock_start), MesOSGlyphs.Play, primary = true, enabled = total > 0) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
                    notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                engine.startTimer(total)
                digits = ""
            }
        }
    }
}
