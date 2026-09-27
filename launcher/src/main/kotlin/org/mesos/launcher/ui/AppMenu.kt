package org.mesos.launcher.ui

import android.content.pm.ShortcutInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mesos.core.ui.MesOSGlyphs
import org.mesos.launcher.AppEntry
import org.mesos.launcher.AppRepository
import kotlin.math.roundToInt

/** One button in the bottom row of a long-press menu. */
internal data class MenuAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/**
 * The long-press menu: an app's shortcuts (for apps) and actions, as a glass card
 * next to [anchor]. Tapping outside closes it.
 */
@Composable
internal fun LongPressMenu(
    anchor: Rect,
    entry: AppEntry?,
    actions: List<MenuAction>,
    onShortcut: (ShortcutInfo, Rect) -> Unit,
    onDismiss: () -> Unit,
    repository: AppRepository,
) {
    val density = LocalDensity.current
    val iconPx = with(density) { 28.dp.roundToPx() }
    val shortcuts by produceState<List<Pair<ShortcutInfo, ImageBitmap?>>>(emptyList(), entry) {
        value = if (entry == null) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) {
                repository.shortcuts(entry).map { it to repository.shortcutIcon(it, iconPx) }
            }
        }
    }
    val margin = with(density) { 12.dp.roundToPx() }
    val gap = with(density) { 10.dp.roundToPx() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.25f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        Column(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    val x = (anchor.center.x - placeable.width / 2f).roundToInt()
                        .coerceIn(margin, (constraints.maxWidth - placeable.width - margin).coerceAtLeast(margin))
                    val below = anchor.bottom.roundToInt() + gap
                    val above = anchor.top.roundToInt() - gap - placeable.height
                    val y = if (below + placeable.height <= constraints.maxHeight - margin) below else above.coerceAtLeast(margin)
                    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(x, y) }
                }
                .width(264.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xF0161E36))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
        ) {
            shortcuts.forEach { (shortcut, icon) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onShortcut(shortcut, anchor) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (icon != null) {
                        Image(icon, contentDescription = null, modifier = Modifier.size(28.dp))
                    } else {
                        Box(Modifier.size(28.dp))
                    }
                    Text(
                        text = (shortcut.shortLabel ?: shortcut.longLabel ?: "").toString(),
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (shortcuts.isNotEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.1f)),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                actions.forEach { action ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = action.onClick)
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(action.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        Text(
                            text = action.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}
