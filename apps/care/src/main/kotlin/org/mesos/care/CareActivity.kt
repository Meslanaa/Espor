package org.mesos.care

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mesos.core.MesOSApps
import org.mesos.core.log.MesOSLog
import org.mesos.core.ui.GroupDivider
import org.mesos.core.ui.GroupLabel
import org.mesos.core.ui.ListGroup
import org.mesos.core.ui.ListRow
import org.mesos.core.ui.MesOSCard
import org.mesos.core.ui.MesOSGlyphs
import org.mesos.core.ui.MesOSListScreen
import org.mesos.core.ui.MesOSPalette
import org.mesos.core.ui.startActivitySafely
import org.mesos.core.ui.theme.MesOSTheme
import org.mesos.core.ui.theme.MesOSUserTheme
import org.mesos.core.ui.theme.Sora

/** MesOS Device Care: battery, storage and memory at a glance, with honest advice. */
class CareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MesOSLog.i(MesOSLog.SYSTEM, "MesOS Device Care opened")
        setContent {
            MesOSUserTheme {
                CareApp(onBack = ::finish)
            }
        }
    }
}

@Composable
private fun CareApp(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<DeviceState?>(null) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                state = withContext(Dispatchers.IO) { DeviceStatus.read(context) }
                delay(2_000)
            }
        }
    }
    val androidTag = stringResource(R.string.care_android)
    val freedText = stringResource(R.string.care_cache_cleared)
    val open = { intent: Intent -> if (!context.startActivitySafely(intent)) Toast.makeText(context, R.string.care_not_available, Toast.LENGTH_SHORT).show() }

    MesOSListScreen(title = stringResource(R.string.care_app_name), onBack = onBack) {
        val current = state ?: return@MesOSListScreen
        val snapshot = current.snapshot
        val score = CareScore.score(snapshot)

        item(key = "score") { ScoreCard(score, CareScore.checks(snapshot)) }

        item(key = "battery") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GroupLabel(stringResource(R.string.care_battery))
                MesOSCard { BatteryCard(current) }
                ListGroup {
                    ListRow(
                        title = stringResource(R.string.care_battery_saver),
                        icon = MesOSGlyphs.Battery,
                        iconColor = MesOSPalette.Green,
                        value = androidTag,
                        onClick = { open(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)) },
                    )
                    GroupDivider()
                    ListRow(
                        title = stringResource(R.string.care_battery_usage),
                        icon = MesOSGlyphs.Bolt,
                        iconColor = MesOSPalette.Amber,
                        value = androidTag,
                        onClick = { open(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) },
                    )
                }
            }
        }

        item(key = "storage") {
            val free = snapshot.storageFreeBytes
            val total = snapshot.storageTotalBytes
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GroupLabel(stringResource(R.string.care_storage))
                MesOSCard {
                    UsageCard(
                        headline = stringResource(R.string.care_free_of, size(context, free), size(context, total)),
                        used = CareScore.fraction(total - free, total) ?: 0f,
                        color = MesOSPalette.Orange,
                    )
                }
                ListGroup {
                    ListRow(
                        title = stringResource(R.string.care_open_files),
                        subtitle = stringResource(R.string.care_open_files_summary),
                        icon = MesOSGlyphs.Folder,
                        iconColor = MesOSPalette.Blue,
                        onClick = { open(MesOSApps.launchIntent(context, MesOSApps.FILES)) },
                    )
                    GroupDivider()
                    ListRow(
                        title = stringResource(R.string.care_storage_settings),
                        icon = MesOSGlyphs.Storage,
                        iconColor = MesOSPalette.Amber,
                        value = androidTag,
                        onClick = { open(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) },
                    )
                    GroupDivider()
                    ListRow(
                        title = stringResource(R.string.care_cache),
                        subtitle = stringResource(R.string.care_cache_summary),
                        icon = MesOSGlyphs.Trash,
                        iconColor = MesOSPalette.Rose,
                        trailing = {
                            FilledTonalButton(
                                enabled = current.mesosCacheBytes > 0,
                                onClick = {
                                    scope.launch {
                                        val freed = withContext(Dispatchers.IO) { DeviceStatus.clearMesOSCache(context) }
                                        Toast.makeText(context, String.format(freedText, size(context, freed)), Toast.LENGTH_SHORT).show()
                                        state = withContext(Dispatchers.IO) { DeviceStatus.read(context) }
                                    }
                                },
                            ) {
                                Text(size(context, current.mesosCacheBytes))
                            }
                        },
                    )
                }
            }
        }

        item(key = "memory") {
            val available = snapshot.memoryAvailableBytes
            val total = snapshot.memoryTotalBytes
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GroupLabel(stringResource(R.string.care_memory))
                MesOSCard {
                    UsageCard(
                        headline = stringResource(R.string.care_available_of, size(context, available), size(context, total)),
                        used = CareScore.fraction(total - available, total) ?: 0f,
                        color = MesOSPalette.Violet,
                    )
                    Text(
                        stringResource(R.string.care_memory_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MesOSTheme.colors.dim,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }

        item(key = "device") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GroupLabel(stringResource(R.string.care_device))
                ListGroup {
                    Info(stringResource(R.string.care_model), deviceName())
                    GroupDivider(inset = 16.dp)
                    Info(stringResource(R.string.care_android_version), "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    GroupDivider(inset = 16.dp)
                    Info(stringResource(R.string.care_processor), stringResource(R.string.care_processor_value, current.cores, current.abi))
                    GroupDivider(inset = 16.dp)
                    Info(stringResource(R.string.care_uptime), duration(context, current.uptimeMs))
                }
            }
        }
    }
}

@Composable
private fun ScoreCard(score: Int, checks: List<CareCheck>) {
    val status = CareScore.status(score)
    val color = statusColor(status)
    val animated by animateFloatAsState(score / 100f, tween(900), label = "score")
    val track = MesOSTheme.colors.separator
    MesOSCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 11.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(track, 135f, 270f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    drawArc(
                        Brush.sweepGradient(listOf(color.copy(alpha = 0.6f), color, color.copy(alpha = 0.6f))),
                        135f,
                        270f * animated,
                        false,
                        Offset(inset, inset),
                        arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                Text(
                    "$score",
                    style = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 36.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(
                        when (status) {
                            CareStatus.GOOD -> R.string.care_status_good
                            CareStatus.FAIR -> R.string.care_status_fair
                            CareStatus.ATTENTION -> R.string.care_status_attention
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(stringResource(R.string.care_score_note), style = MaterialTheme.typography.bodySmall, color = MesOSTheme.colors.dim)
            }
        }
        val findings = checks.filter { it.status != CareStatus.GOOD }
        if (findings.isNotEmpty()) {
            Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                findings.forEach { check ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier
                                .padding(top = 6.dp)
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(statusColor(check.status)),
                        )
                        Text(stringResource(advice(check.topic)), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun statusColor(status: CareStatus): Color = when (status) {
    CareStatus.GOOD -> MesOSTheme.colors.success
    CareStatus.FAIR -> MesOSTheme.colors.warning
    CareStatus.ATTENTION -> MesOSTheme.colors.danger
}

private fun advice(topic: CareTopic): Int = when (topic) {
    CareTopic.BATTERY_HEALTH -> R.string.care_advice_battery_health
    CareTopic.TEMPERATURE -> R.string.care_advice_temperature
    CareTopic.STORAGE -> R.string.care_advice_storage
    CareTopic.MEMORY -> R.string.care_advice_memory
    CareTopic.CHARGE -> R.string.care_advice_charge
}

@Composable
private fun BatteryCard(state: DeviceState) {
    val context = LocalContext.current
    val snapshot = state.snapshot
    val level = snapshot.batteryLevel
    val color = when {
        snapshot.charging -> MesOSTheme.colors.success
        level in 0..19 -> MesOSTheme.colors.danger
        else -> MaterialTheme.colorScheme.primary
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (level >= 0) "$level%" else "—",
                style = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Light, fontSize = 44.sp),
            )
            Text(
                stringResource(
                    when {
                        !snapshot.charging -> R.string.care_on_battery
                        state.plugged == Plug.WIRELESS -> R.string.care_charging_wireless
                        state.plugged == Plug.USB -> R.string.care_charging_usb
                        else -> R.string.care_charging
                    },
                ),
                style = MaterialTheme.typography.titleSmall,
                color = color,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        Bar(fraction = (level.coerceAtLeast(0)) / 100f, color = color)
        val stats = buildList {
            snapshot.batteryTemperatureC?.let { add(stringResource(R.string.care_temperature) to "%.1f °C".format(it)) }
            add(stringResource(R.string.care_health) to stringResource(healthLabel(state.batteryHealth)))
            state.voltageMv?.let { add(stringResource(R.string.care_voltage) to "%.2f V".format(it / 1000f)) }
            state.currentMa?.let { add(stringResource(R.string.care_current) to (if (it > 0) "+" else "") + "$it mA") }
            state.technology?.let { add(stringResource(R.string.care_technology) to it) }
            state.chargeTimeRemainingMs?.let { add(stringResource(R.string.care_until_full) to duration(context, it)) }
        }
        stats.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (label, value) ->
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = MesOSTheme.colors.dim)
                        Text(value, style = MaterialTheme.typography.titleSmall)
                    }
                }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

private fun healthLabel(health: BatteryHealth): Int = when (health) {
    BatteryHealth.GOOD -> R.string.care_health_good
    BatteryHealth.OVERHEAT -> R.string.care_health_overheat
    BatteryHealth.DEAD -> R.string.care_health_dead
    BatteryHealth.OVER_VOLTAGE -> R.string.care_health_over_voltage
    BatteryHealth.COLD -> R.string.care_health_cold
    BatteryHealth.FAILURE -> R.string.care_health_failure
    BatteryHealth.UNKNOWN -> R.string.care_health_unknown
}

@Composable
private fun UsageCard(headline: String, used: Float, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(headline, style = MaterialTheme.typography.titleMedium)
        Bar(used, color)
        Text(
            stringResource(R.string.care_used_percent, (used * 100).toInt()),
            style = MaterialTheme.typography.labelMedium,
            color = MesOSTheme.colors.dim,
        )
    }
}

@Composable
private fun Bar(fraction: Float, color: Color) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700), label = "bar")
    Box(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(MesOSTheme.colors.separator),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .clip(RoundedCornerShape(5.dp))
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.75f), color))),
        )
    }
}

@Composable
private fun Info(label: String, value: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MesOSTheme.colors.dim)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun size(context: Context, bytes: Long): String = Formatter.formatShortFileSize(context, bytes.coerceAtLeast(0))

private fun duration(context: Context, millis: Long): String {
    val minutes = millis / 60_000
    val days = minutes / (24 * 60)
    val hours = (minutes / 60) % 24
    val mins = minutes % 60
    return when {
        days > 0 -> context.getString(R.string.care_days_hours, days, hours)
        hours > 0 -> context.getString(R.string.care_hours_minutes, hours, mins)
        else -> context.getString(R.string.care_minutes, mins)
    }
}

private fun deviceName(): String {
    val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercaseChar() }
    return if (Build.MODEL.startsWith(Build.MANUFACTURER, ignoreCase = true)) Build.MODEL else "$manufacturer ${Build.MODEL}"
}
