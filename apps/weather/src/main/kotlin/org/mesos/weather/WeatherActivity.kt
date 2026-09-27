package org.mesos.weather

import android.Manifest
import android.content.Context
import android.os.Bundle
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mesos.core.ui.MesOSGlyphs
import org.mesos.core.ui.MesOSSearchField
import org.mesos.core.ui.OnResume
import org.mesos.core.ui.glass
import org.mesos.core.ui.hasPermission
import org.mesos.core.ui.theme.MesOSUserTheme
import org.mesos.core.ui.theme.Sora
import java.time.format.TextStyle as DayTextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** MesOS Weather. */
class WeatherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MesOSUserTheme(forceDark = true) { WeatherApp() }
        }
    }
}

private val Dim = Color.White.copy(alpha = 0.7f)

/** Sky colours for a condition, used as the page background. */
internal fun skyFor(code: Int, isDay: Boolean): List<Color> {
    val condition = Condition.fromCode(code)
    return when {
        condition == Condition.THUNDERSTORM -> listOf(Color(0xFF1E1B4B), Color(0xFF312E81), Color(0xFF3F3F70))
        condition.isRainy -> if (isDay) listOf(Color(0xFF334155), Color(0xFF475569), Color(0xFF64748B)) else listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF334155))
        condition.isSnowy -> if (isDay) listOf(Color(0xFF6B8BB3), Color(0xFF93AFD1), Color(0xFFB9CBE2)) else listOf(Color(0xFF1E2A44), Color(0xFF2E3E5E), Color(0xFF45587A))
        condition == Condition.FOG || condition == Condition.OVERCAST ->
            if (isDay) listOf(Color(0xFF5B6B82), Color(0xFF7C8BA1), Color(0xFF9AA6B8)) else listOf(Color(0xFF111827), Color(0xFF1F2937), Color(0xFF374151))
        else -> if (isDay) listOf(Color(0xFF1D4ED8), Color(0xFF3B82F6), Color(0xFF7DB4FB)) else listOf(Color(0xFF050914), Color(0xFF0A1834), Color(0xFF1B2A4E))
    }
}

@Composable
private fun WeatherApp() {
    val context = LocalContext.current
    val repository = remember { WeatherRepository.get(context) }
    val state by repository.state.collectAsState()
    var showPlaces by rememberSaveable { mutableStateOf(false) }
    val pager = rememberPagerState { state.pages.size }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { repository.refresh(force = false) }
    OnResume { repository.refreshInBackground(force = false) }
    BackHandler(enabled = showPlaces && state.pages.isNotEmpty()) { showPlaces = false }

    val page = state.pages.getOrNull(pager.currentPage)
    val sky = skyFor(page?.forecast?.current?.code ?: 0, page?.forecast?.current?.isDay ?: true)
    val top by animateColorAsState(sky[0], label = "skyTop")
    val middle by animateColorAsState(sky[1], label = "skyMiddle")
    val bottom by animateColorAsState(sky[2], label = "skyBottom")

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(top, middle, bottom))),
    ) {
        if (showPlaces || state.pages.isEmpty()) {
            PlacesScreen(
                state = state,
                repository = repository,
                onClose = if (state.pages.isEmpty()) null else ({ showPlaces = false }),
                onOpen = { index ->
                    showPlaces = false
                    scope.launch { pager.scrollToPage(index) }
                },
            )
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { showPlaces = true }) {
                        Icon(MesOSGlyphs.Location, contentDescription = stringResource(R.string.weather_places), tint = Color.White)
                    }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                        repeat(state.pages.size) { index ->
                            Box(
                                Modifier
                                    .padding(horizontal = 3.dp)
                                    .size(if (index == pager.currentPage) 8.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = if (index == pager.currentPage) 1f else 0.45f)),
                            )
                        }
                    }
                    if (state.loading) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.padding(12.dp).size(22.dp))
                    } else {
                        IconButton(onClick = { repository.refreshInBackground(force = true) }) {
                            Icon(MesOSGlyphs.Refresh, contentDescription = stringResource(R.string.weather_refresh), tint = Color.White)
                        }
                    }
                }
                HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
                    state.pages.getOrNull(index)?.let { ForecastPage(it, onRetry = { repository.refreshInBackground(force = true) }) }
                }
            }
        }
    }
}

@Composable
private fun ForecastPage(page: PlaceWeather, onRetry: () -> Unit) {
    val forecast = page.forecast
    if (forecast == null) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(page.place.name, style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Spacer(Modifier.height(12.dp))
            if (page.failed) {
                Text(stringResource(R.string.weather_error), style = MaterialTheme.typography.bodyLarge, color = Dim)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.weather_retry), color = Color.White) }
            } else {
                CircularProgressIndicator(color = Color.White)
            }
        }
        return
    }
    val today = forecast.days.first()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "now") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (page.isCurrentLocation) Icon(MesOSGlyphs.Location, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text(page.place.name, style = MaterialTheme.typography.headlineSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WeatherIcon(forecast.current.code, forecast.current.isDay, Modifier.size(72.dp))
                    Text(
                        temperature(forecast.current.temperature),
                        style = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Light, fontSize = 96.sp, color = Color.White),
                    )
                }
                Text(conditionLabel(forecast.current.code), style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(
                    stringResource(R.string.weather_high_low, temperature(today.max), temperature(today.min)),
                    style = MaterialTheme.typography.titleMedium,
                    color = Dim,
                )
            }
        }
        item(key = "hours") {
            Card {
                Text(stringResource(R.string.weather_hourly), style = MaterialTheme.typography.labelLarge, color = Dim)
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(forecast.hours, key = { it.time.toString() }) { hour ->
                        HourCell(hour, isFirst = hour == forecast.hours.first())
                    }
                }
            }
        }
        item(key = "days") {
            val lowest = forecast.days.minOf { it.min }
            val highest = forecast.days.maxOf { it.max }
            Card {
                Text(stringResource(R.string.weather_daily), style = MaterialTheme.typography.labelLarge, color = Dim)
                Spacer(Modifier.height(4.dp))
                forecast.days.forEachIndexed { index, day ->
                    DayRow(day, index == 0, lowest, highest, current = if (index == 0) forecast.current.temperature else null)
                }
            }
        }
        item(key = "details") {
            val rows = listOf(
                Triple(MesOSGlyphs.Thermometer, stringResource(R.string.weather_feels_like), temperature(forecast.current.apparent)),
                Triple(MesOSGlyphs.Drop, stringResource(R.string.weather_humidity), stringResource(R.string.weather_percent, forecast.current.humidity)),
                Triple(MesOSGlyphs.Wind, stringResource(R.string.weather_wind), windText(forecast.current.windSpeed)),
                Triple(MesOSGlyphs.Sun, stringResource(R.string.weather_uv), today.uvIndex?.let { "%.0f".format(it) } ?: "—"),
                Triple(MesOSGlyphs.Sunrise, stringResource(R.string.weather_sunrise), today.sunrise?.let { clock(it.hour, it.minute) } ?: "—"),
                Triple(MesOSGlyphs.Sunset, stringResource(R.string.weather_sunset), today.sunset?.let { clock(it.hour, it.minute) } ?: "—"),
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                rows.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { (icon, label, value) ->
                            Column(
                                Modifier
                                    .weight(1f)
                                    .glass(RoundedCornerShape(22.dp), fill = 0.14f)
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(icon, contentDescription = null, tint = Dim, modifier = Modifier.size(16.dp))
                                    Text(label, style = MaterialTheme.typography.labelMedium, color = Dim)
                                }
                                Text(value, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
        item(key = "source") {
            Text(
                stringResource(
                    R.string.weather_source,
                    DateUtils.getRelativeTimeSpanString(page.fetchedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = Dim,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(24.dp), fill = 0.14f)
            .padding(16.dp),
    ) { content() }
}

@Composable
private fun HourCell(hour: HourWeather, isFirst: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            if (isFirst) stringResource(R.string.weather_now) else clock(hour.time.hour, null),
            style = MaterialTheme.typography.labelMedium,
            color = Dim,
        )
        WeatherIcon(hour.code, hour.isDay, Modifier.size(30.dp))
        Text(temperature(hour.temperature), style = MaterialTheme.typography.titleSmall, color = Color.White)
        val rain = hour.precipitation
        Text(
            if (rain != null && rain >= 20) "$rain%" else " ",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF7DD3FC),
        )
    }
}

@Composable
private fun DayRow(day: DayWeather, isToday: Boolean, lowest: Double, highest: Double, current: Double?) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (isToday) stringResource(R.string.weather_today) else day.date.dayOfWeek.getDisplayName(DayTextStyle.SHORT, Locale.getDefault()),
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            modifier = Modifier.width(64.dp),
        )
        Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
            WeatherIcon(day.code, true, Modifier.size(26.dp))
        }
        Text(
            day.precipitation?.takeIf { it >= 20 }?.let { "$it%" } ?: "",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF7DD3FC),
            modifier = Modifier.width(40.dp),
        )
        Text(temperature(day.min), style = MaterialTheme.typography.titleSmall, color = Dim, modifier = Modifier.width(40.dp))
        RangeBar(day.min, day.max, lowest, highest, current, Modifier.weight(1f))
        Text(temperature(day.max), style = MaterialTheme.typography.titleSmall, color = Color.White, modifier = Modifier.padding(start = 10.dp).width(40.dp))
    }
}

@Composable
private fun RangeBar(min: Double, max: Double, lowest: Double, highest: Double, current: Double?, modifier: Modifier) {
    val span = (highest - lowest).takeIf { it > 0 } ?: 1.0
    BoxWithConstraints(
        modifier
            .height(6.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f)),
    ) {
        val start = ((min - lowest) / span).toFloat()
        val end = ((max - lowest) / span).toFloat()
        Box(
            Modifier
                .offset(x = maxWidth * start)
                .width(maxWidth * (end - start).coerceAtLeast(0.04f))
                .height(6.dp)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(Color(0xFF7DD3FC), Color(0xFFFDE68A), Color(0xFFFB923C)))),
        )
        if (current != null) {
            val at = ((current - lowest) / span).toFloat().coerceIn(0f, 1f)
            Box(
                Modifier
                    .offset(x = maxWidth * at - 3.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun PlacesScreen(state: WeatherState, repository: WeatherRepository, onClose: (() -> Unit)?, onOpen: (Int) -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        repository.setUseLocation(context.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    LaunchedEffect(query) {
        if (query.trim().length < 2) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(350)
        searching = true
        results = repository.searchPlaces(query)
        searching = false
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.weather_places),
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                if (onClose != null) {
                    IconButton(onClick = onClose) { Icon(MesOSGlyphs.Close, contentDescription = stringResource(R.string.weather_close), tint = Color.White) }
                }
            }
        }
        item(key = "search") {
            MesOSSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.weather_search_hint),
                containerColor = Color.White.copy(alpha = 0.16f),
                contentColor = Color.White,
                placeholderColor = Dim,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotBlank()) {
            if (searching) {
                item(key = "searching") { CircularProgressIndicator(color = Color.White, modifier = Modifier.padding(8.dp)) }
            } else if (results.isEmpty() && query.trim().length >= 2) {
                item(key = "none") { Text(stringResource(R.string.weather_no_results), color = Dim, style = MaterialTheme.typography.bodyMedium) }
            }
            items(results, key = { "r" + it.id }) { place ->
                PlaceRow(place.name, place.region, onClick = {
                    repository.addPlace(place)
                    query = ""
                })
            }
        } else {
            item(key = "location") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .glass(RoundedCornerShape(22.dp), fill = 0.14f)
                        .clickable {
                            if (state.useLocation) {
                                repository.setUseLocation(false)
                            } else if (context.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                                repository.setUseLocation(true)
                            } else {
                                permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(MesOSGlyphs.Location, contentDescription = null, tint = Color.White)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.weather_use_location), style = MaterialTheme.typography.titleSmall, color = Color.White)
                        Text(stringResource(R.string.weather_use_location_summary), style = MaterialTheme.typography.bodySmall, color = Dim)
                    }
                    Switch(checked = state.useLocation, onCheckedChange = null)
                }
            }
            item(key = "unit") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .glass(RoundedCornerShape(22.dp), fill = 0.14f)
                        .clickable { repository.setFahrenheit(!state.fahrenheit) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.weather_fahrenheit), style = MaterialTheme.typography.titleSmall, color = Color.White, modifier = Modifier.weight(1f))
                    Switch(checked = state.fahrenheit, onCheckedChange = null)
                }
            }
            items(state.pages.size, key = { index -> "p" + state.pages[index].place.id + state.pages[index].isCurrentLocation }) { index ->
                val page = state.pages[index]
                PlaceRow(
                    title = page.place.name,
                    subtitle = page.forecast?.let { "${temperature(it.current.temperature)} · ${conditionLabel(it.current.code)}" } ?: page.place.region,
                    onClick = { onOpen(index) },
                    onRemove = if (page.isCurrentLocation) null else ({ repository.removePlace(page.place) }),
                    isLocation = page.isCurrentLocation,
                )
            }
            if (state.pages.isEmpty()) {
                item(key = "empty") {
                    Text(stringResource(R.string.weather_empty), style = MaterialTheme.typography.bodyMedium, color = Dim, modifier = Modifier.padding(8.dp))
                }
            }
        }
    }
}

@Composable
private fun PlaceRow(title: String, subtitle: String, onClick: () -> Unit, onRemove: (() -> Unit)? = null, isLocation: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(22.dp), fill = 0.14f)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isLocation) Icon(MesOSGlyphs.Location, contentDescription = null, tint = Color.White, modifier = Modifier.padding(end = 10.dp).size(18.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) { Icon(MesOSGlyphs.Trash, contentDescription = stringResource(R.string.weather_remove), tint = Dim) }
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

internal fun temperature(value: Double): String = "${value.roundToInt()}°"

@Composable
private fun windText(speed: Double): String {
    val repository = WeatherRepository.get(LocalContext.current)
    val state by repository.state.collectAsState()
    return if (state.fahrenheit) "${speed.roundToInt()} mph" else "${speed.roundToInt()} km/h"
}

@Composable
private fun clock(hour: Int, minute: Int?): String {
    val context = LocalContext.current
    return if (DateFormat.is24HourFormat(context)) {
        if (minute == null) "%02d".format(hour) else "%02d:%02d".format(hour, minute)
    } else {
        val h = if (hour % 12 == 0) 12 else hour % 12
        val suffix = if (hour < 12) "AM" else "PM"
        if (minute == null) "$h $suffix" else "$h:%02d $suffix".format(minute)
    }
}

@Composable
internal fun conditionLabel(code: Int): String = LocalContext.current.conditionLabel(code)

/** Localised name of a weather condition. */
fun Context.conditionLabel(code: Int): String = getString(
    when (Condition.fromCode(code)) {
        Condition.CLEAR -> R.string.weather_clear
        Condition.MAINLY_CLEAR -> R.string.weather_mainly_clear
        Condition.PARTLY_CLOUDY -> R.string.weather_partly_cloudy
        Condition.OVERCAST -> R.string.weather_overcast
        Condition.FOG -> R.string.weather_fog
        Condition.DRIZZLE -> R.string.weather_drizzle
        Condition.RAIN -> R.string.weather_rain
        Condition.FREEZING_RAIN -> R.string.weather_freezing_rain
        Condition.SNOW -> R.string.weather_snow
        Condition.SHOWERS -> R.string.weather_showers
        Condition.SNOW_SHOWERS -> R.string.weather_snow_showers
        Condition.THUNDERSTORM -> R.string.weather_thunderstorm
    },
)
