package org.mesos.launcher.ui

import android.content.Intent
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mesos.core.ui.Avatar
import org.mesos.core.ui.IconBadge
import org.mesos.core.ui.MesOSGlyphs
import org.mesos.core.ui.MesOSSearchField
import org.mesos.core.ui.theme.MesOSTheme
import org.mesos.core.ui.theme.Sora
import org.mesos.launcher.AppEntry
import org.mesos.launcher.R
import org.mesos.launcher.search.SearchEngine
import org.mesos.launcher.search.SearchResults
import org.mesos.core.text.SearchText
import org.mesos.notes.NotesFeed

private val onGlass = Color.White
private val onGlassDim = Color.White.copy(alpha = 0.66f)

/** The app drawer and universal search, drawn over the blurred home screen. */
@Composable
internal fun AppDrawer(
    apps: List<AppEntry>,
    recent: List<AppEntry>,
    badges: Map<String, Int>,
    query: String,
    onQueryChange: (String) -> Unit,
    focusSearch: Boolean,
    results: SearchResults,
    onLaunch: (AppEntry, Rect?) -> Unit,
    onLongPress: (AppEntry, Rect) -> Unit,
    onOpen: (Intent) -> Unit,
    onCopy: (String) -> Unit,
    onClose: () -> Unit,
    pullToClose: NestedScrollConnection,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(focusSearch) {
        if (focusSearch) {
            try {
                focusRequester.requestFocus()
                keyboard?.show()
            } catch (e: IllegalStateException) {
                // Not attached yet; the user can tap the field.
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
                .padding(vertical = 10.dp),
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
        MesOSSearchField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = stringResource(R.string.launcher_search_hint),
            focusRequester = focusRequester,
            containerColor = Color.White.copy(alpha = 0.14f),
            contentColor = onGlass,
            placeholderColor = onGlassDim,
            onSearch = {
                val first = results.apps.firstOrNull()
                when {
                    first != null -> onLaunch(first, null)
                    query.isNotBlank() -> onOpen(SearchEngine.webSearchIntent(context, query))
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        if (query.isBlank()) {
            AllApps(apps, recent, badges, onLaunch, onLongPress, pullToClose)
        } else {
            SearchResultsList(results, badges, onLaunch, onLongPress, onOpen, onCopy, pullToClose)
        }
    }
}

@Composable
private fun AllApps(
    apps: List<AppEntry>,
    recent: List<AppEntry>,
    badges: Map<String, Int>,
    onLaunch: (AppEntry, Rect?) -> Unit,
    onLongPress: (AppEntry, Rect) -> Unit,
    pullToClose: NestedScrollConnection,
) {
    val sections = remember(apps) {
        apps.groupBy { entry ->
            SearchText.normalize(entry.label).firstOrNull()?.takeIf(Char::isLetter)?.uppercaseChar() ?: '#'
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(pullToClose)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (recent.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "suggested-title") {
                SectionTitle(stringResource(R.string.launcher_suggested))
            }
            items(recent.take(4), key = { "recent-" + it.key }) { entry ->
                DrawerTile(entry, badges[entry.packageName] ?: 0, onLaunch, onLongPress)
            }
        }
        sections.forEach { (letter, entries) ->
            item(span = { GridItemSpan(maxLineSpan) }, key = "letter-$letter") {
                SectionTitle(letter.toString())
            }
            items(entries, key = { it.key }) { entry ->
                DrawerTile(entry, badges[entry.packageName] ?: 0, onLaunch, onLongPress)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = onGlassDim,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun DrawerTile(
    entry: AppEntry,
    badge: Int,
    onLaunch: (AppEntry, Rect?) -> Unit,
    onLongPress: (AppEntry, Rect) -> Unit,
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    AppTile(
        entry = entry,
        onClick = { onLaunch(entry, bounds) },
        onLongClick = { onLongPress(entry, bounds) },
        iconSize = 56.dp,
        badge = badge,
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { bounds = it.boundsInRoot() },
    )
}

@Composable
private fun SearchResultsList(
    results: SearchResults,
    badges: Map<String, Int>,
    onLaunch: (AppEntry, Rect?) -> Unit,
    onLongPress: (AppEntry, Rect) -> Unit,
    onOpen: (Intent) -> Unit,
    onCopy: (String) -> Unit,
    pullToClose: NestedScrollConnection,
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(pullToClose)
            .navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val calculation = results.calculation
        if (calculation != null) {
            item(key = "calc") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(MesOSTheme.colors.accentBright.copy(alpha = 0.28f))
                        .clickable { onCopy(calculation) }
                        .padding(18.dp),
                ) {
                    Text(results.expression.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = onGlassDim)
                    Text(
                        "= $calculation",
                        style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Sora),
                        color = onGlass,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(stringResource(R.string.search_tap_to_copy), style = MaterialTheme.typography.labelMedium, color = onGlassDim)
                }
            }
        }
        if (results.apps.isNotEmpty()) {
            item(key = "apps") {
                ResultGroup(stringResource(R.string.search_section_apps)) {
                    results.apps.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { entry ->
                                Box(Modifier.weight(1f)) { DrawerTile(entry, badges[entry.packageName] ?: 0, onLaunch, onLongPress) }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        if (results.settings.isNotEmpty()) {
            item(key = "settings") {
                ResultGroup(stringResource(R.string.search_section_settings)) {
                    results.settings.forEach { setting ->
                        ResultRow(
                            title = setting.title,
                            leading = { IconBadge(setting.icon, setting.color) },
                            onClick = { onOpen(setting.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                        )
                    }
                }
            }
        }
        if (results.contacts.isNotEmpty()) {
            item(key = "contacts") {
                ResultGroup(stringResource(R.string.search_section_contacts)) {
                    results.contacts.forEach { contact ->
                        ResultRow(
                            title = contact.name,
                            leading = { Avatar(contact.name, photo = contact.photo, size = 36.dp) },
                            onClick = { onOpen(SearchEngine.contactIntent(context, contact)) },
                        )
                    }
                }
            }
        }
        if (results.notes.isNotEmpty()) {
            item(key = "notes") {
                ResultGroup(stringResource(R.string.search_section_notes)) {
                    results.notes.forEach { note ->
                        ResultRow(
                            title = note.title.ifBlank { note.body.lineSequence().firstOrNull().orEmpty() },
                            subtitle = note.body.take(80).replace('\n', ' '),
                            leading = { IconBadge(MesOSGlyphs.Edit, Color(0xFFF59E0B), size = 36.dp) },
                            onClick = { onOpen(NotesFeed.openIntent(context, note.id)) },
                        )
                    }
                }
            }
        }
        if (results.files.isNotEmpty()) {
            item(key = "files") {
                ResultGroup(stringResource(R.string.search_section_files)) {
                    results.files.forEach { file ->
                        ResultRow(
                            title = file.name,
                            subtitle = file.mimeType,
                            leading = { IconBadge(MesOSGlyphs.Folder, Color(0xFF3B82F6), size = 36.dp) },
                            onClick = { onOpen(SearchEngine.fileIntent(context, file)) },
                        )
                    }
                }
            }
        }
        item(key = "web") {
            ResultGroup(null) {
                ResultRow(
                    title = stringResource(R.string.search_web, results.query),
                    leading = { IconBadge(MesOSGlyphs.Globe, Color(0xFF0E7490), size = 36.dp) },
                    onClick = { onOpen(SearchEngine.webSearchIntent(context, results.query)) },
                )
                ResultRow(
                    title = stringResource(R.string.search_play, results.query),
                    leading = { IconBadge(MesOSGlyphs.Download, Color(0xFF16A34A), size = 36.dp) },
                    onClick = { onOpen(SearchEngine.playStoreIntent(results.query)) },
                )
            }
        }
    }
}

@Composable
private fun ResultGroup(title: String?, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(vertical = 6.dp),
    ) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = onGlassDim,
                modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 4.dp),
            )
        }
        content()
    }
}

@Composable
private fun ResultRow(
    title: String,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = onGlass, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = onGlassDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(MesOSGlyphs.ChevronRight, contentDescription = null, tint = onGlassDim, modifier = Modifier.size(14.dp))
    }
}
