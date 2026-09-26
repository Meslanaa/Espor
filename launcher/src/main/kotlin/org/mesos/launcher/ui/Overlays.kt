package org.mesos.launcher.ui

import android.appwidget.AppWidgetProviderInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mesos.core.ui.MesOSGlyphs
import org.mesos.launcher.AppEntry
import org.mesos.launcher.R
import org.mesos.launcher.layout.WidgetKinds
import org.mesos.launcher.widgets.AndroidWidgets
import org.mesos.launcher.widgets.MesOSWidget

private val PanelColor = Color(0xF0141C33)

/** An open folder: editable name and its apps. */
@Composable
internal fun FolderOverlay(
    name: String,
    apps: List<AppEntry>,
    badges: Map<String, Int>,
    onRename: (String) -> Unit,
    onLaunch: (AppEntry, Rect?) -> Unit,
    onLongPress: (AppEntry, Rect) -> Unit,
    onDismiss: () -> Unit,
) {
    var editedName by remember(name) { mutableStateOf(name) }
    val dismiss = {
        if (editedName.trim() != name) onRename(editedName)
        onDismiss()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = dismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(328.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(PanelColor)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (editedName.isEmpty()) {
                    Text(
                        stringResource(R.string.launcher_folder_unnamed),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White.copy(alpha = 0.5f),
                    )
                }
                BasicTextField(
                    value = editedName,
                    onValueChange = { editedName = it.take(40) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(color = Color.White, textAlign = TextAlign.Center),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onRename(editedName) }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            apps.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { entry ->
                        var bounds by remember { mutableStateOf(Rect.Zero) }
                        AppTile(
                            entry = entry,
                            onClick = { onLaunch(entry, bounds) },
                            onLongClick = { onLongPress(entry, bounds) },
                            badge = badges[entry.packageName] ?: 0,
                            modifier = Modifier
                                .weight(1f)
                                .onGloballyPositioned { bounds = it.boundsInRoot() },
                        )
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Something that can be added to Home from the widget picker. */
internal sealed interface WidgetChoice {
    data class MesOS(val kind: String, val w: Int, val h: Int) : WidgetChoice
    data class Android(val info: AppWidgetProviderInfo) : WidgetChoice
}

/** Bottom sheet listing MesOS widgets (live previews) and Android app widgets. */
@Composable
internal fun WidgetPicker(
    nowMillis: Long,
    widgets: AndroidWidgets,
    cellWidthDp: Float,
    cellHeightDp: Float,
    onPick: (WidgetChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val providers by produceState<List<AppWidgetProviderInfo>>(emptyList()) {
        value = withContext(Dispatchers.IO) { widgets.providers() }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(PanelColor)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .navigationBarsPadding(),
        ) {
            Text(
                stringResource(R.string.launcher_widgets_title),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                modifier = Modifier.padding(start = 24.dp, top = 22.dp, bottom = 8.dp),
            )
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "mesos-title") { PickerHeader(stringResource(R.string.launcher_widgets_mesos)) }
                items(WidgetKinds.mesos.entries.toList(), key = { it.key }) { (kind, size) ->
                    val (w, h) = size
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .clickable { onPick(WidgetChoice.MesOS(kind, w, h)) }
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .width((cellWidthDp * w).dp.coerceAtMost(340.dp))
                                .height((cellHeightDp * h).dp.coerceAtMost(200.dp))
                                .clip(RoundedCornerShape(20.dp))
                                .background(previewBackground)
                                .padding(6.dp),
                        ) {
                            MesOSWidget(kind, nowMillis, Modifier.fillMaxSize())
                        }
                        Text(
                            "${widgetName(kind)} · ${w}×$h",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                        )
                    }
                }
                if (providers.isNotEmpty()) {
                    item(key = "android-title") { PickerHeader(stringResource(R.string.launcher_widgets_apps)) }
                }
                items(providers, key = { it.provider.flattenToString() + it.profile.hashCode() }) { info ->
                    AndroidWidgetRow(info, onClick = { onPick(WidgetChoice.Android(info)) }, appLabel = remember(info) {
                        try {
                            context.packageManager.getApplicationLabel(
                                context.packageManager.getApplicationInfo(info.provider.packageName, 0),
                            ).toString()
                        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
                            info.provider.packageName
                        }
                    })
                }
            }
        }
    }
}

private val previewBackground = Brush.linearGradient(listOf(Color(0xFF0A1834), Color(0xFF1E2F5A)))

@Composable
private fun PickerHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = Color.White.copy(alpha = 0.66f),
        modifier = Modifier.padding(start = 8.dp, top = 8.dp),
    )
}

@Composable
private fun AndroidWidgetRow(info: AppWidgetProviderInfo, appLabel: String, onClick: () -> Unit) {
    val context = LocalContext.current
    val preview by produceState<ImageBitmap?>(null, info) {
        value = withContext(Dispatchers.IO) {
            try {
                val density = context.resources.displayMetrics.densityDpi
                val drawable = info.loadPreviewImage(context, density) ?: info.loadIcon(context, density)
                drawable?.let {
                    val w = it.intrinsicWidth.takeIf { v -> v > 0 }?.coerceAtMost(600) ?: 200
                    val h = it.intrinsicHeight.takeIf { v -> v > 0 }?.coerceAtMost(400) ?: 200
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    it.setBounds(0, 0, w, h)
                    it.draw(Canvas(bitmap))
                    bitmap.asImageBitmap()
                }
            } catch (e: RuntimeException) {
                null
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            preview?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.heightIn(max = 88.dp)) }
                ?: Icon(MesOSGlyphs.Widgets, contentDescription = null, tint = Color.White)
        }
        Column(Modifier.weight(1f)) {
            Text(
                info.loadLabel(context.packageManager),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(appLabel, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.66f), maxLines = 1)
        }
    }
}

@Composable
private fun widgetName(kind: String): String = stringResource(
    when (kind) {
        WidgetKinds.CLOCK -> R.string.widget_clock
        WidgetKinds.WEATHER -> R.string.widget_weather
        WidgetKinds.AGENDA -> R.string.widget_agenda
        WidgetKinds.NOTES -> R.string.widget_notes
        WidgetKinds.MUSIC -> R.string.widget_music
        else -> R.string.widget_battery
    },
)
