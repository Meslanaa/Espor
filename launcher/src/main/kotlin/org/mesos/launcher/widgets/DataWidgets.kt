package org.mesos.launcher.widgets

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import org.mesos.calendar.CalendarFeed
import org.mesos.calendar.Occurrence
import org.mesos.core.MesOSApps
import org.mesos.core.ui.startActivitySafely
import org.mesos.core.ui.theme.MesOSTheme
import org.mesos.core.ui.theme.Sora
import org.mesos.launcher.R
import org.mesos.weather.WeatherFeed
import org.mesos.weather.WeatherIcon
import org.mesos.weather.conditionLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun WeatherWidget(modifier: Modifier) {
    val context = LocalContext.current
    val weather by remember { WeatherFeed.primary(context) }.collectAsState()
    LaunchedEffect(Unit) { WeatherFeed.refreshIfStale(context) }
    val open = { context.startActivitySafely(MesOSApps.launchIntent(context, MesOSApps.WEATHER)) }
    val page = weather
    val forecast = page?.forecast
    if (page == null || forecast == null) {
        WidgetCard(title = stringResource(R.string.widget_weather), modifier = modifier, onClick = { open() }) {
            // Partly cloudy day, as a hint of what the widget shows once it has a forecast.
            WeatherIcon(2, isDay = true, modifier = Modifier.size(44.dp))
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.widget_weather_setup),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
        return
    }
    val today = forecast.days.first()
    WidgetCard(
        title = page.place.name,
        modifier = modifier,
        titleColor = Color.White.copy(alpha = 0.85f),
        onClick = { open() },
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                WeatherFeed.temperatureText(forecast.current.temperature),
                style = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Light, fontSize = 40.sp, color = Color.White),
                modifier = Modifier.weight(1f),
            )
            WeatherIcon(forecast.current.code, forecast.current.isDay, Modifier.size(40.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(
            context.conditionLabel(forecast.current.code),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            stringResource(
                org.mesos.weather.R.string.weather_high_low,
                WeatherFeed.temperatureText(today.max),
                WeatherFeed.temperatureText(today.min),
            ),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.75f),
            maxLines = 1,
        )
    }
}

@Composable
internal fun AgendaWidget(nowMillis: Long, modifier: Modifier) {
    val context = LocalContext.current
    val changes by remember { CalendarFeed.changes(context) }.collectAsState()
    var upcoming by remember { mutableStateOf<List<Occurrence>>(emptyList()) }
    LaunchedEffect(changes, nowMillis / 60_000L) { upcoming = CalendarFeed.upcoming(context, nowMillis, 3) }
    val accent = MesOSTheme.colors.accentBright
    val is24 = DateFormat.is24HourFormat(context)
    val locale = Locale.getDefault()
    val weekday = remember(locale) { SimpleDateFormat("EEEE", locale) }
    WidgetCard(
        title = weekday.format(Date(nowMillis)).uppercase(locale),
        modifier = modifier,
        titleColor = CalendarRed,
        onClick = { context.startActivitySafely(CalendarFeed.openIntent(context)) },
    ) {
        Text(
            SimpleDateFormat("d", locale).format(Date(nowMillis)),
            style = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Light, fontSize = 34.sp, color = Color.White),
            maxLines = 1,
        )
        if (upcoming.isEmpty()) {
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.widget_agenda_empty), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
        }
        val time = remember(is24, locale) { SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, if (is24) "Hm" else "hm"), locale) }
        val dayTime = remember(is24, locale) { SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, if (is24) "EEEHm" else "EEEhm"), locale) }
        upcoming.take(2).forEach { occurrence ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { context.startActivitySafely(CalendarFeed.openIntent(context, occurrence.start)) },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .width(3.dp)
                        .height(34.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
                Column {
                    Text(
                        occurrence.event.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        when {
                            occurrence.event.allDay -> stringResource(R.string.widget_agenda_all_day)
                            CalendarFeed.isToday(occurrence) -> time.format(Date(occurrence.start))
                            else -> dayTime.format(Date(occurrence.start))
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** The red of the MesOS Calendar icon, for the weekday on the agenda widget. */
private val CalendarRed = Color(0xFFFF6B81)
