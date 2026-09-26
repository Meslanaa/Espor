package org.mesos.launcher.control

import android.content.Intent
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mesos.core.MesOSApps
import org.mesos.core.MesOSIntents
import org.mesos.core.prefs.MesOSPreferences
import org.mesos.core.prefs.ThemeMode
import org.mesos.core.system.ScreenRecording
import org.mesos.core.ui.MesOSGlyphs
import org.mesos.core.ui.glass
import org.mesos.core.ui.rememberHaptics
import org.mesos.core.ui.theme.MesOSTheme
import org.mesos.core.ui.theme.rememberMesOSDarkTheme
import org.mesos.core.ui.theme.Sora
import org.mesos.launcher.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val Dim = Color.White.copy(alpha = 0.66f)
private val TileOff = Color.White.copy(alpha = 0.14f)

/**
 * The MesOS control and notification center (opened by swiping down on Home).
 * Toggles work through public Android APIs; notifications need MesOS notification
 * access, which the card at the bottom asks for.
 */
@Composable
internal fun ControlCenter(
    nowMillis: Long,
    appIcon: (String) -> ImageBitmap?,
    appLabel: (String) -> String,
    onOpen: (Intent) -> Unit,
    onClose: () -> Unit,
    closeConnection: NestedScrollConnection,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val toggles = remember { SystemToggles(context) }
    val status by toggles.status.collectAsState()
    val connected by NotificationCenter.connected.collectAsState()
    val notifications by NotificationCenter.notifications.collectAsState()
    val media by NotificationCenter.media.collectAsState()
    val recording by ScreenRecording.active.collectAsState()
    val preferences = remember { MesOSPreferences.get(context) }
    val darkTheme = rememberMesOSDarkTheme()
    val haptics = rememberHaptics()

    DisposableEffect(toggles) {
        toggles.start()
        onDispose { toggles.stop() }
    }
    LaunchedEffect(toggles) {
        // Wi-Fi, location and similar have no callbacks MesOS may use: poll while open.
        while (true) {
            delay(1000)
            toggles.refresh()
        }
    }

    fun toggle(which: Toggle) {
        haptics.tick()
        toggles.toggle(which)?.let { onOpen(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    val groups = remember(notifications) { notifications.groupBy { it.packageName }.values.toList() }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(closeConnection)
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            Header(nowMillis, status, onSettings = { onOpen(MesOSIntents.settings(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) })
        }
        item(key = "big") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigTile(
                    title = stringResource(R.string.control_wifi),
                    subtitle = stringResource(
                        when {
                            status.wifiConnected -> R.string.control_connected
                            status.wifiEnabled -> R.string.control_on
                            else -> R.string.control_off
                        },
                    ),
                    icon = MesOSGlyphs.Wifi,
                    active = status.wifiEnabled,
                    onClick = { toggle(Toggle.WIFI) },
                    modifier = Modifier.weight(1f),
                )
                BigTile(
                    title = stringResource(R.string.control_bluetooth),
                    subtitle = stringResource(
                        when {
                            !status.bluetoothAvailable -> R.string.control_unavailable
                            status.bluetoothEnabled -> R.string.control_on
                            else -> R.string.control_off
                        },
                    ),
                    icon = MesOSGlyphs.Bluetooth,
                    active = status.bluetoothEnabled,
                    onClick = { toggle(Toggle.BLUETOOTH) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item(key = "toggles") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(28.dp), fill = 0.1f, edge = 0.16f)
                    .padding(vertical = 14.dp, horizontal = 6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(Modifier.fillMaxWidth()) {
                    RoundToggle(stringResource(R.string.control_flashlight), MesOSGlyphs.Flashlight, status.torchOn, status.torchAvailable, Modifier.weight(1f)) { toggle(Toggle.TORCH) }
                    RoundToggle(stringResource(R.string.control_dnd), MesOSGlyphs.Moon, status.doNotDisturb, true, Modifier.weight(1f)) { toggle(Toggle.DO_NOT_DISTURB) }
                    RoundToggle(stringResource(R.string.control_rotate), MesOSGlyphs.Rotate, status.autoRotate, true, Modifier.weight(1f)) { toggle(Toggle.AUTO_ROTATE) }
                    RoundToggle(stringResource(R.string.control_dark), MesOSGlyphs.Contrast, darkTheme, true, Modifier.weight(1f)) {
                        haptics.tick()
                        preferences.setThemeMode(if (darkTheme) ThemeMode.LIGHT else ThemeMode.DARK)
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    RoundToggle(stringResource(R.string.control_screen_record), MesOSGlyphs.Record, recording, true, Modifier.weight(1f)) {
                        haptics.tick()
                        onOpen(
                            Intent(MesOSIntents.ACTION_TOGGLE_SCREEN_RECORDING)
                                .setClassName(context.packageName, MesOSApps.SCREEN_RECORDER)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                    RoundToggle(stringResource(R.string.control_qr), MesOSGlyphs.Qr, false, true, Modifier.weight(1f)) {
                        onOpen(MesOSApps.launchIntent(context, MesOSApps.SCANNER))
                    }
                    RoundToggle(stringResource(R.string.control_location), MesOSGlyphs.Location, status.locationOn, true, Modifier.weight(1f)) { toggle(Toggle.LOCATION) }
                    RoundToggle(stringResource(R.string.control_battery_saver), MesOSGlyphs.Battery, status.powerSave, true, Modifier.weight(1f)) { toggle(Toggle.POWER_SAVE) }
                }
            }
        }
        item(key = "sliders") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LevelSlider(
                    value = status.brightness,
                    icon = MesOSGlyphs.Sun,
                    description = stringResource(R.string.control_brightness),
                    onChange = { value -> toggles.setBrightness(value)?.let { onOpen(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                )
                LevelSlider(
                    value = status.volume,
                    icon = MesOSGlyphs.Volume,
                    description = stringResource(R.string.control_volume),
                    onChange = { toggles.setVolume(it) },
                )
            }
        }
        media?.let { current ->
            item(key = "media") { MediaCard(current, appIcon, onOpenApp = { NotificationCenter.openMediaApp(context) }) }
        }
        item(key = "notifications-header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.control_notifications),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                if (notifications.any { it.isClearable }) {
                    Text(
                        stringResource(R.string.control_clear_all),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(TileOff)
                            .clickable { NotificationCenter.dismissAll() }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
        }
        if (!connected) {
            item(key = "access") {
                AccessCard(onAllow = { onOpen(NotificationCenter.accessSettingsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) })
            }
        } else if (groups.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.control_no_notifications),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Dim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                )
            }
        }
        items(groups, key = { it.first().packageName + it.first().user.hashCode() }) { group ->
            val pkg = group.first().packageName
            val isOpen = expanded[pkg] == true || group.size == 1
            NotificationGroup(
                items = if (isOpen) group else group.take(1),
                hiddenCount = if (isOpen) 0 else group.size - 1,
                appName = appLabel(pkg),
                icon = appIcon(pkg),
                onExpand = { expanded[pkg] = true },
                onOpen = { item -> if (NotificationCenter.open(context, item)) onClose() },
            )
        }
        item(key = "close") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(40.dp)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.5f)),
                )
            }
        }
    }
}

@Composable
private fun Header(nowMillis: Long, status: SystemStatus, onSettings: () -> Unit) {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val is24 = DateFormat.is24HourFormat(context)
    val time = remember(nowMillis, locale, is24) {
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, if (is24) "Hm" else "hm"), locale).format(Date(nowMillis))
    }
    val date = remember(nowMillis, locale) {
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEEMMMMd"), locale).format(Date(nowMillis))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                time,
                style = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Light, fontSize = 44.sp, letterSpacing = (-1).sp),
                color = Color.White,
            )
            val battery = if (status.batteryPercent >= 0) {
                " · " + stringResource(R.string.control_battery_percent, status.batteryPercent)
            } else {
                ""
            }
            Text(
                date + battery,
                style = MaterialTheme.typography.labelLarge,
                color = Dim,
            )
        }
        Box(
            modifier = Modifier
                .size(42.dp)
                .glass(CircleShape)
                .clickable(onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) {
            Icon(MesOSGlyphs.Settings, contentDescription = stringResource(R.string.control_settings), tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun BigTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MesOSTheme.colors.accentBright
    val background by animateColorAsState(if (active) accent else TileOff, label = "bigTile")
    Row(
        modifier = modifier
            .height(70.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (active) Color.White else Color.White.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (active) accent else Color.White, modifier = Modifier.size(22.dp))
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
        }
    }
}

@Composable
private fun RoundToggle(
    label: String,
    icon: ImageVector,
    active: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accent = MesOSTheme.colors.accentBright
    val background by animateColorAsState(if (active) accent else TileOff, label = "roundToggle")
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(background),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/** A thick pill slider: the white fill is the level, drag or tap to change it. */
@Composable
private fun LevelSlider(value: Float, icon: ImageVector, description: String, onChange: (Float) -> Unit) {
    var local by remember { mutableStateOf<Float?>(null) }
    val shown = (local ?: value).coerceIn(0f, 1f)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .pointerInput(Unit) {
                detectTapGestures { offset -> onChange((offset.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset -> local = (offset.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = {
                        local?.let(onChange)
                        local = null
                    },
                    onDragCancel = { local = null },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val next = (change.position.x / size.width).coerceIn(0f, 1f)
                        local = next
                        onChange(next)
                    },
                )
            },
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(maxWidth * shown.coerceAtLeast(0.14f))
                .clip(RoundedCornerShape(26.dp))
                .background(Color.White.copy(alpha = 0.92f)),
        )
        Icon(
            icon,
            contentDescription = description,
            tint = Color(0xFF0B1222),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp)
                .size(22.dp),
        )
    }
}

@Composable
private fun MediaCard(media: MediaSnapshot, appIcon: (String) -> ImageBitmap?, onOpenApp: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(26.dp), fill = 0.1f, edge = 0.16f)
            .clickable(onClick = onOpenApp)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MesOSTheme.colors.accentBright.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            val art = media.art
            val icon = appIcon(media.packageName)
            when {
                art != null -> Image(art.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                icon != null -> Image(icon, contentDescription = null, modifier = Modifier.size(40.dp))
                else -> Icon(MesOSGlyphs.Music, contentDescription = null, tint = Color.White)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(media.title.ifBlank { "—" }, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(media.artist, style = MaterialTheme.typography.labelMedium, color = Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { NotificationCenter.previous() }) {
            Icon(MesOSGlyphs.Previous, contentDescription = stringResource(R.string.control_media_previous), tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable { NotificationCenter.playPause() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (media.isPlaying) MesOSGlyphs.Pause else MesOSGlyphs.Play,
                contentDescription = stringResource(if (media.isPlaying) R.string.control_media_pause else R.string.control_media_play),
                tint = Color(0xFF0B1222),
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = { NotificationCenter.next() }) {
            Icon(MesOSGlyphs.Next, contentDescription = stringResource(R.string.control_media_next), tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun AccessCard(onAllow: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(22.dp), fill = 0.1f, edge = 0.16f)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(MesOSGlyphs.Bell, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            Text(stringResource(R.string.control_access_title), style = MaterialTheme.typography.titleSmall, color = Color.White)
        }
        Text(stringResource(R.string.control_access_body), style = MaterialTheme.typography.bodyMedium, color = Dim)
        Text(
            stringResource(R.string.control_access_action),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            modifier = Modifier
                .clip(CircleShape)
                .background(MesOSTheme.colors.accentBright)
                .clickable(onClick = onAllow)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun NotificationGroup(
    items: List<NotificationItem>,
    hiddenCount: Int,
    appName: String,
    icon: ImageBitmap?,
    onExpand: () -> Unit,
    onOpen: (NotificationItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            key(item.key) {
                SwipeToDismiss(enabled = item.isClearable, onDismiss = { NotificationCenter.dismiss(item) }) {
                    NotificationCard(item, appName, icon, hiddenCount.takeIf { item == items.first() } ?: 0, onExpand, onOpen)
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    item: NotificationItem,
    appName: String,
    icon: ImageBitmap?,
    hiddenCount: Int,
    onExpand: () -> Unit,
    onOpen: (NotificationItem) -> Unit,
) {
    val context = LocalContext.current
    var replying by remember { mutableStateOf(false) }
    var reply by remember { mutableStateOf("") }
    val accent = MesOSTheme.colors.accentBright
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(22.dp), fill = 0.14f, edge = 0.18f)
            .clickable { if (hiddenCount > 0) onExpand() else onOpen(item) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(
                appName,
                style = MaterialTheme.typography.labelMedium,
                color = Dim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                DateUtils.getRelativeTimeSpanString(item.postTime, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                style = MaterialTheme.typography.labelSmall,
                color = Dim,
            )
            if (hiddenCount > 0) {
                Text(
                    "+$hiddenCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(TileOff)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        if (item.title.isNotBlank()) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (item.text.isNotBlank()) {
            Text(item.text, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f), maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        val replyAction = item.replyAction
        if (replying && replyAction != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.14f))
                    .padding(start = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (reply.isEmpty()) Text(stringResource(R.string.control_reply_hint), style = MaterialTheme.typography.bodyMedium, color = Dim)
                    BasicTextField(
                        value = reply,
                        onValueChange = { reply = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (reply.isNotBlank() && NotificationCenter.reply(context, replyAction, reply)) {
                                reply = ""
                                replying = false
                            }
                        }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                IconButton(onClick = {
                    if (reply.isNotBlank() && NotificationCenter.reply(context, replyAction, reply)) {
                        reply = ""
                        replying = false
                    }
                }) {
                    Icon(MesOSGlyphs.Send, contentDescription = stringResource(R.string.control_reply_send), tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        } else if (item.actions.isNotEmpty() && hiddenCount == 0) {
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item.actions.forEach { action ->
                    val isReply = action.remoteInput != null
                    Text(
                        action.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isReply) accent else TileOff)
                            .clickable {
                                if (isReply) replying = true else NotificationCenter.perform(context, action)
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }
        }
    }
}

/** Swipe a card sideways to dismiss it; it springs back when not swiped far enough. */
@Composable
private fun SwipeToDismiss(enabled: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = constraints.maxWidth.toFloat()
        Box(
            modifier = Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .graphicsLayer { alpha = 1f - (abs(offset.value) / width).coerceIn(0f, 0.8f) }
                .draggable(
                    enabled = enabled,
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta -> scope.launch { offset.snapTo(offset.value + delta) } },
                    onDragStopped = { velocity ->
                        if (abs(offset.value) > width * 0.35f || abs(velocity) > 2400f) {
                            val target = if (offset.value + velocity * 0.1f > 0) width else -width
                            offset.animateTo(target)
                            onDismiss()
                        } else {
                            offset.animateTo(0f)
                        }
                    },
                ),
        ) {
            content()
        }
    }
}
