package org.mesos.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.mesos.core.ui.SquircleShape
import org.mesos.core.ui.glass
import org.mesos.launcher.AppEntry

/** Label style on the wallpaper: white with a soft shadow for any background. */
@Composable
internal fun wallpaperLabelStyle(): TextStyle = MaterialTheme.typography.labelMedium.copy(
    color = Color.White,
    shadow = Shadow(color = Color.Black.copy(alpha = 0.55f), offset = Offset(0f, 1.5f), blurRadius = 6f),
)

/** An app icon with an optional label and notification badge. Presses shrink it slightly. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AppTile(
    entry: AppEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    iconSize: Dp = 60.dp,
    showLabel: Boolean = true,
    badge: Int = 0,
    labelStyle: TextStyle = wallpaperLabelStyle(),
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "iconPress")
    Column(
        modifier = modifier
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.scale(scale)) {
            Image(
                bitmap = entry.icon,
                contentDescription = if (showLabel) null else entry.label,
                modifier = Modifier.size(iconSize),
            )
            if (badge > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .size(if (badge > 9) 22.dp else 18.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF43F5E))
                        .border(1.5.dp, Color.White.copy(alpha = 0.9f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (badge > 99) "99" else badge.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
            }
        }
        if (showLabel) {
            Text(
                text = entry.label,
                style = labelStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            )
        }
    }
}

/** A folder: up to four app icons on a glass squircle. */
@Composable
internal fun FolderTile(
    name: String,
    apps: List<AppEntry>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 60.dp,
    showLabel: Boolean = true,
    badge: Int = 0,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "folderPress")
    Column(
        modifier = modifier
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .scale(scale)
                .size(iconSize)
                .glass(SquircleShape, fill = 0.22f, edge = 0.28f),
            contentAlignment = Alignment.Center,
        ) {
            val mini = iconSize * 0.34f
            Column(verticalArrangement = Arrangement.spacedBy(iconSize * 0.06f)) {
                apps.take(4).chunked(2).forEach { row ->
                    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(iconSize * 0.06f)) {
                        row.forEach { Image(it.icon, contentDescription = null, modifier = Modifier.size(mini)) }
                        if (row.size == 1) Box(Modifier.size(mini))
                    }
                }
            }
            if (badge > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF43F5E)),
                )
            }
        }
        if (showLabel) {
            Text(
                text = name,
                style = wallpaperLabelStyle(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            )
        }
    }
}
