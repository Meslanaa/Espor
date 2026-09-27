package org.mesos.calendar

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.mesos.core.ui.MesOSGlyphs
import org.mesos.core.ui.hasPermission
import org.mesos.core.ui.theme.MesOSTheme
import org.mesos.core.ui.theme.MesOSUserTheme
import org.mesos.core.ui.theme.Sora
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** MesOS Calendar. */
class CalendarActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val requested = intent.getLongExtra(EXTRA_DAY, -1L)
        val day = if (requested > 0) Instant.ofEpochMilli(requested).atZone(ZoneId.systemDefault()).toLocalDate() else LocalDate.now()
        setContent { MesOSUserTheme { CalendarApp(day) } }
    }

    companion object {
        /** Epoch millis of a moment on the day to show. */
        const val EXTRA_DAY = "org.mesos.calendar.extra.DAY"
    }
}

/** Event colours; 0 is the MesOS accent. */
@Composable
internal fun eventColor(index: Int): Color = when (index) {
    1 -> Color(0xFFF43F5E)
    2 -> Color(0xFFF59E0B)
    3 -> Color(0xFF10B981)
    4 -> Color(0xFF0EA5E9)
    5 -> Color(0xFF8B5CF6)
    else -> MaterialTheme.colorScheme.primary
}

@Composable
private fun CalendarApp(initialDay: LocalDate) {
    val context = LocalContext.current
    val store = remember { EventStore.get(context) }
    val changes by store.changes.collectAsState()
    var events by remember { mutableStateOf<List<Event>>(emptyList()) }
    LaunchedEffect(changes) { events = store.all() }

    var selectedEpochDay by rememberSaveable { mutableStateOf(initialDay.toEpochDay()) }
    var monthEpochDay by rememberSaveable { mutableStateOf(initialDay.withDayOfMonth(1).toEpochDay()) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selected = LocalDate.ofEpochDay(selectedEpochDay)
    val month = LocalDate.ofEpochDay(monthEpochDay)

    val editing = editingId
    if (editing != null) {
        BackHandler { editingId = null }
        EventEditor(
            eventId = editing,
            day = selected,
            store = store,
            onClose = { editingId = null },
        )
        return
    }

    val zone = ZoneId.systemDefault()
    val locale = Locale.getDefault()
    val grid = remember(monthEpochDay, locale) { CalendarMath.monthGrid(month, locale) }
    val byDay = remember(events, grid) {
        CalendarMath.agenda(events, CalendarMath.dayStart(grid.first(), zone), CalendarMath.dayEnd(grid.last(), zone), zone)
            .groupBy { Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate() }
    }
    val dayAgenda = remember(events, selectedEpochDay) {
        CalendarMath.agenda(events, CalendarMath.dayStart(selected, zone), CalendarMath.dayEnd(selected, zone), zone)
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.safeDrawingPadding()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "header") {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) },
                                style = MaterialTheme.typography.headlineLarge,
                            )
                            Text(month.year.toString(), style = MaterialTheme.typography.titleMedium, color = MesOSTheme.colors.dim)
                        }
                        TextButton(onClick = {
                            val today = LocalDate.now()
                            selectedEpochDay = today.toEpochDay()
                            monthEpochDay = today.withDayOfMonth(1).toEpochDay()
                        }) { Text(stringResource(R.string.calendar_today)) }
                        IconButton(onClick = { monthEpochDay = month.minusMonths(1).toEpochDay() }) {
                            Icon(MesOSGlyphs.Back, contentDescription = stringResource(R.string.calendar_previous_month))
                        }
                        IconButton(onClick = { monthEpochDay = month.plusMonths(1).toEpochDay() }) {
                            Icon(MesOSGlyphs.ChevronRight, contentDescription = stringResource(R.string.calendar_next_month))
                        }
                    }
                }
                item(key = "grid") {
                    MonthGrid(
                        month = month,
                        grid = grid,
                        selected = selected,
                        byDay = byDay,
                        onSelect = { date ->
                            selectedEpochDay = date.toEpochDay()
                            if (date.month != month.month) monthEpochDay = date.withDayOfMonth(1).toEpochDay()
                        },
                        onSwipe = { forward -> monthEpochDay = (if (forward) month.plusMonths(1) else month.minusMonths(1)).toEpochDay() },
                    )
                }
                item(key = "day-title") {
                    Text(
                        selected.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                    )
                }
                if (dayAgenda.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            stringResource(R.string.calendar_no_events),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MesOSTheme.colors.dim,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
                items(dayAgenda, key = { it.event.id.toString() + "-" + it.start }) { occurrence ->
                    AgendaRow(occurrence, onClick = { editingId = occurrence.event.id })
                }
            }
            FloatingActionButton(
                onClick = { editingId = 0L },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
            ) {
                Icon(MesOSGlyphs.Plus, contentDescription = stringResource(R.string.calendar_new_event))
            }
        }
    }
}

@Composable
private fun MonthGrid(
    month: LocalDate,
    grid: List<LocalDate>,
    selected: LocalDate,
    byDay: Map<LocalDate, List<Occurrence>>,
    onSelect: (LocalDate) -> Unit,
    onSwipe: (forward: Boolean) -> Unit,
) {
    val locale = Locale.getDefault()
    val today = LocalDate.now()
    var dragged by remember { mutableStateOf(0f) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MesOSTheme.colors.card)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = { if (kotlin.math.abs(dragged) > 120f) onSwipe(dragged < 0) },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        dragged += amount
                    },
                )
            }
            .padding(10.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            grid.take(7).forEach { day ->
                Text(
                    day.dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, locale),
                    style = MaterialTheme.typography.labelMedium,
                    color = MesOSTheme.colors.dim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        grid.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    val inMonth = day.month == month.month
                    val isToday = day == today
                    val isSelected = day == selected
                    val occurrences = byDay[day].orEmpty()
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)) else Modifier)
                            .clickable { onSelect(day) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                day.dayOfMonth.toString(),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.SemiBold),
                                color = when {
                                    isToday -> MaterialTheme.colorScheme.onPrimary
                                    inMonth -> MaterialTheme.colorScheme.onSurface
                                    else -> MaterialTheme.colorScheme.outline
                                },
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(6.dp)) {
                            occurrences.take(3).forEach { occurrence ->
                                Box(
                                    Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(eventColor(occurrence.event.color)),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgendaRow(occurrence: Occurrence, onClick: () -> Unit) {
    val event = occurrence.event
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MesOSTheme.colors.card)
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .width(5.dp)
                .height(44.dp)
                .clip(CircleShape)
                .background(eventColor(event.color)),
        )
        Column(Modifier.weight(1f)) {
            Text(event.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(timeRange(occurrence), style = MaterialTheme.typography.bodyMedium, color = MesOSTheme.colors.dim)
            if (event.location.isNotBlank()) {
                Text(event.location, style = MaterialTheme.typography.bodySmall, color = MesOSTheme.colors.dim, maxLines = 1)
            }
        }
        if (event.repeat != Repeat.NONE) Icon(MesOSGlyphs.Repeat, contentDescription = null, tint = MesOSTheme.colors.dim, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun timeRange(occurrence: Occurrence): String {
    if (occurrence.event.allDay) return stringResource(R.string.calendar_all_day)
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    val format = DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
    val start = Instant.ofEpochMilli(occurrence.start).atZone(zone)
    val end = Instant.ofEpochMilli(occurrence.end).atZone(zone)
    return start.format(format) + " – " + end.format(format)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventEditor(eventId: Long, day: LocalDate, store: EventStore, onClose: () -> Unit) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(eventId == 0L) }
    var title by rememberSaveable { mutableStateOf("") }
    var allDay by rememberSaveable { mutableStateOf(false) }
    val defaultStart = remember(day) {
        val now = LocalDateTime.now()
        val hour = if (day == now.toLocalDate()) (now.hour + 1).coerceAtMost(23) else 9
        day.atTime(hour, 0)
    }
    var startMs by rememberSaveable { mutableStateOf(defaultStart.atZone(zone).toInstant().toEpochMilli()) }
    var endMs by rememberSaveable { mutableStateOf(defaultStart.plusHours(1).atZone(zone).toInstant().toEpochMilli()) }
    var location by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableStateOf(0) }
    var reminder by rememberSaveable { mutableStateOf(REMINDER_OPTIONS[2]) }
    var repeat by rememberSaveable { mutableStateOf(Repeat.NONE) }
    var picker by remember { mutableStateOf<Picker?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(eventId) {
        if (eventId > 0 && !loaded) {
            store.get(eventId)?.let { e ->
                title = e.title
                allDay = e.allDay
                startMs = e.start
                endMs = if (e.allDay) e.end - 1 else e.end
                location = e.location
                notes = e.notes
                color = e.color
                reminder = e.reminderMinutes
                repeat = e.repeat
            }
            loaded = true
        }
    }

    val start = Instant.ofEpochMilli(startMs).atZone(zone).toLocalDateTime()
    val end = Instant.ofEpochMilli(endMs).atZone(zone).toLocalDateTime()

    fun save() {
        val (s, e) = if (allDay) {
            val first = start.toLocalDate()
            val last = maxOf(end.toLocalDate(), first)
            CalendarMath.dayStart(first, zone) to CalendarMath.dayEnd(last, zone)
        } else {
            startMs to maxOf(endMs, startMs)
        }
        if (reminder >= 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        scope.launch {
            store.save(
                Event(
                    id = eventId,
                    title = title.trim().ifEmpty { context.getString(R.string.calendar_untitled) },
                    start = s,
                    end = e,
                    allDay = allDay,
                    location = location.trim(),
                    notes = notes.trim(),
                    color = color,
                    reminderMinutes = reminder,
                    repeat = repeat,
                ),
            )
            onClose()
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .safeDrawingPadding()
                .imePadding()
                .fillMaxHeight(),
        ) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(MesOSGlyphs.Close, contentDescription = stringResource(R.string.calendar_cancel)) }
                Text(
                    stringResource(if (eventId == 0L) R.string.calendar_new_event else R.string.calendar_edit_event),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = ::save, enabled = loaded) { Text(stringResource(R.string.calendar_save)) }
            }
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.calendar_title)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.calendar_all_day), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Switch(checked = allDay, onCheckedChange = { allDay = it })
                }
                DateTimeRow(stringResource(R.string.calendar_starts), start, allDay, onDate = { picker = Picker.START_DATE }, onTime = { picker = Picker.START_TIME })
                DateTimeRow(stringResource(R.string.calendar_ends), end, allDay, onDate = { picker = Picker.END_DATE }, onTime = { picker = Picker.END_TIME })

                Label(stringResource(R.string.calendar_repeat))
                ChipRow(Repeat.entries.map { it to repeatLabel(it) }, repeat) { repeat = it }

                Label(stringResource(R.string.calendar_reminder))
                ChipRow(REMINDER_OPTIONS.map { it to reminderLabel(it) }, reminder) { reminder = it }

                Label(stringResource(R.string.calendar_color))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    (0..5).forEach { index ->
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(eventColor(index))
                                .then(if (index == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .clickable { color = index },
                        )
                    }
                }
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text(stringResource(R.string.calendar_location)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.calendar_notes)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (eventId > 0) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Icon(MesOSGlyphs.Trash, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.calendar_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    fun shift(newStart: LocalDateTime) {
        val duration = endMs - startMs
        startMs = newStart.atZone(zone).toInstant().toEpochMilli()
        endMs = startMs + duration.coerceAtLeast(0)
    }

    when (picker) {
        Picker.START_DATE, Picker.END_DATE -> {
            val isStart = picker == Picker.START_DATE
            val current = if (isStart) start.toLocalDate() else end.toLocalDate()
            val state = rememberDatePickerState(initialSelectedDateMillis = current.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            DatePickerDialog(
                onDismissRequest = { picker = null },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { millis ->
                            val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            if (isStart) {
                                shift(date.atTime(start.toLocalTime()))
                            } else {
                                val candidate = date.atTime(end.toLocalTime())
                                endMs = maxOf(candidate.atZone(zone).toInstant().toEpochMilli(), startMs)
                            }
                        }
                        picker = null
                    }) { Text(stringResource(R.string.calendar_ok)) }
                },
                dismissButton = { TextButton(onClick = { picker = null }) { Text(stringResource(R.string.calendar_cancel)) } },
            ) { DatePicker(state = state) }
        }
        Picker.START_TIME, Picker.END_TIME -> {
            val isStart = picker == Picker.START_TIME
            val current = if (isStart) start.toLocalTime() else end.toLocalTime()
            val state = rememberTimePickerState(current.hour, current.minute, DateFormat.is24HourFormat(context))
            AlertDialog(
                onDismissRequest = { picker = null },
                confirmButton = {
                    TextButton(onClick = {
                        val time = LocalTime.of(state.hour, state.minute)
                        if (isStart) {
                            shift(start.toLocalDate().atTime(time))
                        } else {
                            val candidate = end.toLocalDate().atTime(time).atZone(zone).toInstant().toEpochMilli()
                            endMs = if (candidate < startMs) candidate + 24 * 3_600_000L else candidate
                        }
                        picker = null
                    }) { Text(stringResource(R.string.calendar_ok)) }
                },
                dismissButton = { TextButton(onClick = { picker = null }) { Text(stringResource(R.string.calendar_cancel)) } },
                text = { TimePicker(state = state) },
            )
        }
        null -> Unit
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.calendar_delete_title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        store.delete(eventId)
                        onClose()
                    }
                }) { Text(stringResource(R.string.calendar_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.calendar_cancel)) } },
        )
    }
}

private enum class Picker { START_DATE, START_TIME, END_DATE, END_TIME }

private val REMINDER_OPTIONS = listOf(Event.NO_REMINDER, 0, 10, 30, 60, 1440)

@Composable
private fun reminderLabel(minutes: Int): String = when (minutes) {
    Event.NO_REMINDER -> stringResource(R.string.calendar_reminder_none)
    0 -> stringResource(R.string.calendar_reminder_at_start)
    1440 -> stringResource(R.string.calendar_reminder_day)
    else -> stringResource(R.string.calendar_reminder_minutes, minutes)
}

@Composable
private fun repeatLabel(repeat: Repeat): String = stringResource(
    when (repeat) {
        Repeat.NONE -> R.string.calendar_repeat_none
        Repeat.DAILY -> R.string.calendar_repeat_daily
        Repeat.WEEKLY -> R.string.calendar_repeat_weekly
        Repeat.MONTHLY -> R.string.calendar_repeat_monthly
        Repeat.YEARLY -> R.string.calendar_repeat_yearly
    },
)

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MesOSTheme.colors.dim)
}

@Composable
private fun <T> ChipRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

@Composable
private fun DateTimeRow(label: String, value: LocalDateTime, allDay: Boolean, onDate: () -> Unit, onTime: () -> Unit) {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(
            value.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MesOSTheme.colors.card)
                .clickable(onClick = onDate)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
        if (!allDay) {
            Spacer(Modifier.width(8.dp))
            val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
            Text(
                value.toLocalTime().format(DateTimeFormatter.ofPattern(pattern, locale)),
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = Sora),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MesOSTheme.colors.card)
                    .clickable(onClick = onTime)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}
