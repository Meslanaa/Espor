package org.mesos.launcher

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
internal fun HomeScreen(
    model: LauncherModel,
    nowMillis: Long,
    drawerOpen: Boolean,
    onOpenDrawer: () -> Unit,
    onCloseDrawer: () -> Unit,
    onLaunch: (AppEntry) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val background = Brush.verticalGradient(
        listOf(colors.primaryContainer.copy(alpha = 0.6f), colors.background, colors.background),
    )
    val openDrawer by rememberUpdatedState(onOpenDrawer)

    // Home never finishes on Back; Back only closes the drawer.
    BackHandler(enabled = true) {
        if (drawerOpen) onCloseDrawer()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .pointerInput(Unit) {
                val threshold = 64.dp.toPx()
                var dragged = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = { if (dragged < -threshold) openDrawer() },
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        dragged += amount
                    },
                )
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            ClockHeader(nowMillis)
            Spacer(Modifier.weight(1f))
            PinnedGrid(
                apps = model.pinned,
                onLaunch = onLaunch,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(16.dp))
            AllAppsHandle(onClick = onOpenDrawer)
            Dock(apps = model.dock, onLaunch = onLaunch)
        }

        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            AppDrawer(apps = model.allApps, onLaunch = onLaunch, onClose = onCloseDrawer)
        }
    }
}

@Composable
private fun ClockHeader(nowMillis: Long) {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val timeZoneId = TimeZone.getDefault().id
    val is24Hour = DateFormat.is24HourFormat(context)
    val timeFormat = remember(locale, timeZoneId, is24Hour) {
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, if (is24Hour) "Hm" else "hm"), locale)
    }
    val dateFormat = remember(locale, timeZoneId) {
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEEMMMMd"), locale)
    }
    val date = Date(nowMillis)

    Text(
        text = timeFormat.format(date),
        style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Light),
        color = MaterialTheme.colorScheme.onBackground,
    )
    Text(
        text = dateFormat.format(date),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PinnedGrid(apps: List<AppEntry>, onLaunch: (AppEntry) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = (maxWidth / 88.dp).toInt().coerceIn(3, 6)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            apps.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { entry ->
                        AppTile(entry = entry, onClick = { onLaunch(entry) }, modifier = Modifier.weight(1f))
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun AllAppsHandle(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.launcher_all_apps),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Dock(apps: List<AppEntry>, onLaunch: (AppEntry) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            apps.forEach { entry -> AppTile(entry = entry, onClick = { onLaunch(entry) }, showLabel = false) }
        }
    }
}

@Composable
private fun AppDrawer(apps: List<AppEntry>, onLaunch: (AppEntry) -> Unit, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        val q = query.trim()
        if (q.isEmpty()) apps else apps.filter { it.label.contains(q, ignoreCase = true) }
    }
    val pullToClose = rememberPullToClose(onClose)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            DrawerHandle(onClose = onClose)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.launcher_search_apps)) },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            if (filtered.isEmpty()) {
                Text(
                    text = stringResource(R.string.launcher_no_apps_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    textAlign = TextAlign.Center,
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 88.dp),
                    contentPadding = PaddingValues(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(pullToClose),
                ) {
                    items(filtered, key = { it.key }) { entry ->
                        AppTile(entry = entry, onClick = { onLaunch(entry) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerHandle(onClose: () -> Unit) {
    val close by rememberUpdatedState(onClose)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClose)
            .pointerInput(Unit) {
                val threshold = 48.dp.toPx()
                var dragged = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = { if (dragged > threshold) close() },
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        dragged += amount
                    },
                )
            }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant),
        )
    }
}

/** Closes the drawer when the user keeps pulling down while the app list is at its top. */
@Composable
private fun rememberPullToClose(onClose: () -> Unit): NestedScrollConnection {
    val threshold = with(LocalDensity.current) { 96.dp.toPx() }
    val close by rememberUpdatedState(onClose)
    return remember(threshold) {
        object : NestedScrollConnection {
            private var pulled = 0f

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    pulled += available.y
                    if (pulled > threshold) {
                        pulled = 0f
                        close()
                    }
                } else if (consumed.y != 0f) {
                    pulled = 0f
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                pulled = 0f
                return Velocity.Zero
            }
        }
    }
}

@Composable
private fun AppTile(
    entry: AppEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            bitmap = entry.icon,
            contentDescription = if (showLabel) null else entry.label,
            modifier = Modifier.size(56.dp),
        )
        if (showLabel) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = entry.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
