package org.mesos.browser

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.text.format.DateUtils
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mesos.core.MesOSApps
import org.mesos.core.ui.EmptyState
import org.mesos.core.ui.GroupDivider
import org.mesos.core.ui.GroupLabel
import org.mesos.core.ui.ListGroup
import org.mesos.core.ui.ListRow
import org.mesos.core.ui.MesOSGlyphs
import org.mesos.core.ui.MesOSListScreen
import org.mesos.core.ui.MesOSMark
import org.mesos.core.ui.RadioRow
import org.mesos.core.ui.avatarColor
import org.mesos.core.ui.startActivitySafely
import org.mesos.core.ui.theme.MesOSTheme

private enum class Overlay { NONE, TABS, BOOKMARKS, HISTORY, SETTINGS }

/** Sites on the start page until the user has bookmarks of their own. */
private val quickLinks = listOf(
    "Google" to "https://www.google.com",
    "YouTube" to "https://m.youtube.com",
    "Wikipedia" to "https://www.wikipedia.org",
    "GitHub" to "https://github.com",
)

@Composable
internal fun BrowserScreen(
    tabs: TabManager,
    store: BrowserStore,
    customView: View?,
    onExitCustomView: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val tab = tabs.current ?: return
    val engine by store.engine.collectAsState()
    val bookmarks by store.bookmarks.collectAsState()
    var editing by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var overlay by remember { mutableStateOf(Overlay.NONE) }
    val keyboard = LocalSoftwareKeyboardController.current

    val go: (String) -> Unit = { text ->
        val url = UrlPolicy.resolve(text, engine)
        if (url.isNotEmpty()) {
            if (UrlPolicy.classify(url) == LinkAction.EXTERNAL) {
                context.startActivitySafely(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
            } else {
                tabs.load(tab, url)
            }
        }
        editing = false
        keyboard?.hide()
    }
    val startEditing: () -> Unit = {
        val current = if (tab.isStartPage) "" else tab.url
        input = TextFieldValue(current, TextRange(0, current.length))
        editing = true
    }

    BackHandler(enabled = true) {
        when {
            customView != null -> onExitCustomView()
            editing -> editing = false
            overlay != Overlay.NONE -> overlay = Overlay.NONE
            tab.webView?.canGoBack() == true && !tab.isStartPage -> tab.webView?.goBack()
            else -> onClose()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(Modifier.fillMaxSize()) {
            AddressBar(
                tab = tab,
                editing = editing,
                input = input,
                onInput = { input = it },
                onStartEditing = startEditing,
                onSubmit = { go(input.text) },
                onCancel = {
                    editing = false
                    keyboard?.hide()
                },
                onReload = { tab.webView?.reload() },
                onStop = { tab.webView?.stopLoading() },
            )
            if (tab.progress in 1..99 && !tab.isStartPage) {
                LinearProgressIndicator(
                    progress = { tab.progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                )
            } else {
                Spacer(Modifier.height(2.dp))
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val error = tab.error
                when {
                    tab.isStartPage -> StartPage(bookmarks = bookmarks, onOpen = { tabs.load(tab, it) }, onSearch = startEditing)
                    error != null -> ErrorPage(error, onRetry = { tabs.load(tab, tab.url) }, onBack = { tabs.back(tab) })
                    else -> {
                        val view = tabs.webViewFor(tab)
                        key(view) {
                            AndroidView(
                                factory = {
                                    (view.parent as? ViewGroup)?.removeView(view)
                                    view
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
                if (editing) {
                    Suggestions(
                        query = input.text,
                        engine = engine,
                        store = store,
                        bookmarks = bookmarks,
                        onPick = { go(it) },
                    )
                }
            }
            BottomBar(
                tab = tab,
                tabCount = tabs.tabs.size,
                bookmarked = bookmarks.any { it.url == tab.url },
                onBack = { tab.webView?.goBack() },
                onForward = { tab.webView?.goForward() },
                onBookmark = { store.toggleBookmark(tab.url, tab.title) },
                onTabs = { overlay = Overlay.TABS },
                onNewTab = { tabs.open() },
                onOverlay = { overlay = it },
                onDesktop = { tabs.setDesktop(tab, !tab.desktop) },
                onShare = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, tab.url)
                    context.startActivitySafely(Intent.createChooser(send, tab.title.ifBlank { null }))
                },
                onDownloads = { context.startActivitySafely(MesOSApps.launchIntent(context, MesOSApps.DOWNLOADS)) },
            )
        }

        when (overlay) {
            Overlay.NONE -> Unit
            Overlay.TABS -> TabsOverlay(
                tabs = tabs,
                onPick = {
                    tabs.select(it)
                    overlay = Overlay.NONE
                },
                onNew = {
                    tabs.open()
                    overlay = Overlay.NONE
                },
                onClose = { overlay = Overlay.NONE },
            )
            Overlay.BOOKMARKS -> BookmarksOverlay(
                bookmarks = bookmarks,
                onOpen = {
                    tabs.load(tab, it)
                    overlay = Overlay.NONE
                },
                onRemove = store::removeBookmark,
                onClose = { overlay = Overlay.NONE },
            )
            Overlay.HISTORY -> HistoryOverlay(
                store = store,
                onOpen = {
                    tabs.load(tab, it)
                    overlay = Overlay.NONE
                },
                onClose = { overlay = Overlay.NONE },
            )
            Overlay.SETTINGS -> SettingsOverlay(store = store, tabs = tabs, onClose = { overlay = Overlay.NONE })
        }

        if (customView != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                AndroidView(
                    factory = {
                        (customView.parent as? ViewGroup)?.removeView(customView)
                        customView
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

}

@Composable
private fun AddressBar(
    tab: BrowserTab,
    editing: Boolean,
    input: TextFieldValue,
    onInput: (TextFieldValue) -> Unit,
    onStartEditing: () -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(46.dp)
                .clip(RoundedCornerShape(23.dp))
                .background(MesOSTheme.colors.card)
                .clickable(enabled = !editing, onClick = onStartEditing)
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val secure = UrlPolicy.isSecure(tab.url)
            Icon(
                when {
                    editing || tab.isStartPage -> MesOSGlyphs.Search
                    secure -> MesOSGlyphs.Lock
                    else -> MesOSGlyphs.Warning
                },
                contentDescription = stringResource(
                    when {
                        editing || tab.isStartPage -> R.string.browser_search
                        secure -> R.string.browser_secure
                        else -> R.string.browser_not_secure
                    },
                ),
                tint = if (!editing && !tab.isStartPage && !secure) MesOSTheme.colors.warning else MesOSTheme.colors.dim,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (editing) {
                    if (input.text.isEmpty()) {
                        Text(stringResource(R.string.browser_address_hint), color = MesOSTheme.colors.dim, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    }
                    BasicTextField(
                        value = input,
                        onValueChange = onInput,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus),
                    )
                    LaunchedEffect(Unit) { focus.requestFocus() }
                } else {
                    Text(
                        text = if (tab.isStartPage) stringResource(R.string.browser_address_hint) else UrlPolicy.displayHost(tab.url),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (tab.isStartPage) MesOSTheme.colors.dim else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!editing && !tab.isStartPage) {
                val loading = tab.progress in 1..99
                IconButton(onClick = if (loading) onStop else onReload) {
                    Icon(
                        if (loading) MesOSGlyphs.Close else MesOSGlyphs.Refresh,
                        contentDescription = stringResource(if (loading) R.string.browser_stop else R.string.browser_reload),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        if (editing) {
            TextButton(onClick = onCancel) { Text(stringResource(org.mesos.core.R.string.mesos_cancel)) }
        }
    }
}

@Composable
private fun Suggestions(
    query: String,
    engine: SearchEngine,
    store: BrowserStore,
    bookmarks: List<Bookmark>,
    onPick: (String) -> Unit,
) {
    var visits by remember { mutableStateOf<List<Visit>>(emptyList()) }
    LaunchedEffect(query) { visits = withContext(Dispatchers.IO) { store.suggestions(query) } }
    val marked = if (query.length < 2) {
        emptyList()
    } else {
        bookmarks.filter { it.url.contains(query, ignoreCase = true) || it.title.contains(query, ignoreCase = true) }.take(3)
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (query.isNotBlank()) {
            item(key = "search") {
                SuggestionRow(MesOSGlyphs.Search, stringResource(R.string.browser_search_with, query.trim(), engine.label), null) { onPick(query) }
            }
        }
        items(marked, key = { "b-" + it.url }) { bookmark ->
            SuggestionRow(MesOSGlyphs.Star, bookmark.title, UrlPolicy.displayHost(bookmark.url)) { onPick(bookmark.url) }
        }
        items(visits.filter { visit -> marked.none { it.url == visit.url } }, key = { "h-" + it.url }) { visit ->
            SuggestionRow(MesOSGlyphs.History, visit.title.ifBlank { visit.url }, UrlPolicy.displayHost(visit.url)) { onPick(visit.url) }
        }
    }
}

@Composable
private fun SuggestionRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MesOSTheme.colors.dim, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MesOSTheme.colors.dim, maxLines = 1)
        }
    }
}

@Composable
private fun StartPage(bookmarks: List<Bookmark>, onOpen: (String) -> Unit, onSearch: () -> Unit) {
    val sites = bookmarks.take(8).map { it.title to it.url }.ifEmpty { quickLinks }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        MesOSMark(size = 64.dp)
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.browser_app_name), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(27.dp))
                .background(MesOSTheme.colors.card)
                .clickable(onClick = onSearch)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(MesOSGlyphs.Search, contentDescription = null, tint = MesOSTheme.colors.dim)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.browser_address_hint), color = MesOSTheme.colors.dim, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(28.dp))
        sites.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { (title, url) -> SiteTile(title, url, Modifier.weight(1f)) { onOpen(url) } }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.browser_privacy_note),
            style = MaterialTheme.typography.bodySmall,
            color = MesOSTheme.colors.dim,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SiteTile(title: String, url: String, modifier: Modifier, onClick: () -> Unit) {
    val name = title.ifBlank { UrlPolicy.displayHost(url) }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(avatarColor(UrlPolicy.displayHost(url))),
            contentAlignment = Alignment.Center,
        ) {
            Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = Color.White)
        }
        Text(
            name,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp),
        )
    }
}

@Composable
private fun ErrorPage(error: PageError, onRetry: () -> Unit, onBack: () -> Unit) {
    val (title, message) = when (error) {
        PageError.Insecure -> stringResource(R.string.browser_error_ssl_title) to stringResource(R.string.browser_error_ssl_text)
        is PageError.Network -> stringResource(R.string.browser_error_network_title) to
            (stringResource(R.string.browser_error_network_text) + if (error.description.isNotBlank()) "\n(${error.description})" else "")
        PageError.Crashed -> stringResource(R.string.browser_error_crash_title) to stringResource(R.string.browser_error_crash_text)
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
    ) {
        EmptyState(
            icon = if (error == PageError.Insecure) MesOSGlyphs.Shield else MesOSGlyphs.Warning,
            title = title,
            message = message,
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onBack) { Text(stringResource(org.mesos.core.R.string.mesos_back)) }
                    if (error != PageError.Insecure) {
                        Button(onClick = onRetry) { Text(stringResource(org.mesos.core.R.string.mesos_retry)) }
                    }
                }
            },
        )
    }
}

@Composable
private fun BottomBar(
    tab: BrowserTab,
    tabCount: Int,
    bookmarked: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onBookmark: () -> Unit,
    onTabs: () -> Unit,
    onNewTab: () -> Unit,
    onOverlay: (Overlay) -> Unit,
    onDesktop: () -> Unit,
    onShare: () -> Unit,
    onDownloads: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding(),
    ) {
        HorizontalDivider(color = MesOSTheme.colors.separator)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, enabled = tab.canGoBack && !tab.isStartPage) {
                Icon(MesOSGlyphs.Back, contentDescription = stringResource(org.mesos.core.R.string.mesos_back))
            }
            IconButton(onClick = onForward, enabled = tab.canGoForward && !tab.isStartPage) {
                Icon(MesOSGlyphs.ChevronRight, contentDescription = stringResource(R.string.browser_forward))
            }
            IconButton(onClick = onBookmark, enabled = !tab.isStartPage) {
                Icon(
                    if (bookmarked) MesOSGlyphs.StarFilled else MesOSGlyphs.Star,
                    contentDescription = stringResource(R.string.browser_bookmark),
                    tint = if (bookmarked) MesOSTheme.colors.warning else MaterialTheme.colorScheme.onSurface.copy(alpha = if (tab.isStartPage) 0.38f else 1f),
                )
            }
            IconButton(onClick = onTabs) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (tabCount > 99) ":)" else tabCount.toString(), style = MaterialTheme.typography.labelMedium)
                }
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(MesOSGlyphs.More, contentDescription = stringResource(org.mesos.core.R.string.mesos_more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    MenuItem(MesOSGlyphs.Plus, stringResource(R.string.browser_new_tab)) {
                        menu = false
                        onNewTab()
                    }
                    MenuItem(MesOSGlyphs.Bookmark, stringResource(R.string.browser_bookmarks)) {
                        menu = false
                        onOverlay(Overlay.BOOKMARKS)
                    }
                    MenuItem(MesOSGlyphs.History, stringResource(R.string.browser_history)) {
                        menu = false
                        onOverlay(Overlay.HISTORY)
                    }
                    MenuItem(MesOSGlyphs.Download, stringResource(R.string.browser_downloads)) {
                        menu = false
                        onDownloads()
                    }
                    if (!tab.isStartPage) {
                        MenuItem(MesOSGlyphs.Share, stringResource(org.mesos.core.R.string.mesos_share)) {
                            menu = false
                            onShare()
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.browser_desktop_site)) },
                            leadingIcon = { Icon(MesOSGlyphs.Globe, contentDescription = null) },
                            trailingIcon = { Checkbox(checked = tab.desktop, onCheckedChange = null) },
                            onClick = {
                                menu = false
                                onDesktop()
                            },
                        )
                    }
                    MenuItem(MesOSGlyphs.Settings, stringResource(R.string.browser_settings)) {
                        menu = false
                        onOverlay(Overlay.SETTINGS)
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

@Composable
private fun TabsOverlay(tabs: TabManager, onPick: (BrowserTab) -> Unit, onNew: () -> Unit, onClose: () -> Unit) {
    MesOSListScreen(
        title = stringResource(R.string.browser_tabs, tabs.tabs.size),
        onBack = onClose,
        actions = {
            TextButton(onClick = onNew) { Text(stringResource(R.string.browser_new_tab)) }
        },
    ) {
        item(key = "grid") {
            // A plain grid of cards; the list above scrolls when there are many tabs.
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                tabs.tabs.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { tab ->
                            TabCard(
                                tab = tab,
                                current = tab.id == tabs.current?.id,
                                modifier = Modifier.weight(1f),
                                onPick = { onPick(tab) },
                                onClose = { tabs.close(tab) },
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun TabCard(tab: BrowserTab, current: Boolean, modifier: Modifier, onPick: () -> Unit, onClose: () -> Unit) {
    val title = when {
        tab.isStartPage -> stringResource(R.string.browser_start_page)
        tab.title.isNotBlank() -> tab.title
        else -> UrlPolicy.displayHost(tab.url)
    }
    Column(
        modifier = modifier
            .aspectRatio(0.82f)
            .clip(RoundedCornerShape(20.dp))
            .background(if (current) MaterialTheme.colorScheme.primaryContainer else MesOSTheme.colors.card)
            .clickable(onClick = onPick)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Favicon(tab.favicon, title, 22.dp)
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(MesOSGlyphs.Close, contentDescription = stringResource(R.string.browser_close_tab), modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            if (tab.isStartPage) "" else UrlPolicy.displayHost(tab.url),
            style = MaterialTheme.typography.labelMedium,
            color = MesOSTheme.colors.dim,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Favicon(icon: Bitmap?, name: String, size: Dp) {
    if (icon != null) {
        Image(icon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(size).clip(RoundedCornerShape(4.dp)))
    } else {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(avatarColor(name)),
            contentAlignment = Alignment.Center,
        ) {
            Text(name.take(1).uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.White)
        }
    }
}

@Composable
private fun BookmarksOverlay(bookmarks: List<Bookmark>, onOpen: (String) -> Unit, onRemove: (Bookmark) -> Unit, onClose: () -> Unit) {
    MesOSListScreen(title = stringResource(R.string.browser_bookmarks), onBack = onClose) {
        if (bookmarks.isEmpty()) {
            item(key = "empty") { EmptyState(MesOSGlyphs.Star, stringResource(R.string.browser_bookmarks_empty)) }
            return@MesOSListScreen
        }
        item(key = "list") {
            ListGroup {
                bookmarks.forEachIndexed { index, bookmark ->
                    if (index > 0) GroupDivider(inset = 16.dp)
                    ListRow(
                        title = bookmark.title,
                        subtitle = UrlPolicy.displayHost(bookmark.url),
                        onClick = { onOpen(bookmark.url) },
                        trailing = {
                            IconButton(onClick = { onRemove(bookmark) }) {
                                Icon(MesOSGlyphs.Trash, contentDescription = stringResource(org.mesos.core.R.string.mesos_delete), tint = MesOSTheme.colors.dim)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryOverlay(store: BrowserStore, onOpen: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var visits by remember { mutableStateOf<List<Visit>>(emptyList()) }
    var version by remember { mutableIntStateOf(0) }
    LaunchedEffect(version) { visits = withContext(Dispatchers.IO) { store.history() } }
    MesOSListScreen(
        title = stringResource(R.string.browser_history),
        onBack = onClose,
        actions = {
            if (visits.isNotEmpty()) {
                TextButton(onClick = {
                    store.clearHistory()
                    version++
                }) { Text(stringResource(R.string.browser_clear)) }
            }
        },
    ) {
        if (visits.isEmpty()) {
            item(key = "empty") { EmptyState(MesOSGlyphs.History, stringResource(R.string.browser_history_empty)) }
            return@MesOSListScreen
        }
        visits.groupBy { DateUtils.formatDateTime(context, it.time, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY) }
            .forEach { (day, entries) ->
                item(key = "day-$day") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        GroupLabel(day)
                        ListGroup {
                            entries.forEachIndexed { index, visit ->
                                if (index > 0) GroupDivider(inset = 16.dp)
                                ListRow(
                                    title = visit.title.ifBlank { visit.url },
                                    subtitle = UrlPolicy.displayHost(visit.url) + " · " +
                                        DateUtils.formatDateTime(context, visit.time, DateUtils.FORMAT_SHOW_TIME),
                                    onClick = { onOpen(visit.url) },
                                    trailing = {
                                        IconButton(onClick = {
                                            store.deleteVisit(visit)
                                            version++
                                        }) {
                                            Icon(MesOSGlyphs.Close, contentDescription = stringResource(org.mesos.core.R.string.mesos_delete), tint = MesOSTheme.colors.dim)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
    }
}

@Composable
private fun SettingsOverlay(store: BrowserStore, tabs: TabManager, onClose: () -> Unit) {
    val context = LocalContext.current
    val engine by store.engine.collectAsState()
    MesOSListScreen(title = stringResource(R.string.browser_settings), onBack = onClose) {
        item(key = "engine") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GroupLabel(stringResource(R.string.browser_search_engine))
                ListGroup {
                    SearchEngine.entries.forEachIndexed { index, option ->
                        if (index > 0) GroupDivider(inset = 16.dp)
                        RadioRow(title = option.label, selected = option == engine, onSelect = { store.setEngine(option) })
                    }
                }
            }
        }
        item(key = "privacy") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GroupLabel(stringResource(R.string.browser_privacy))
                ListGroup {
                    ListRow(
                        title = stringResource(R.string.browser_clear_history),
                        icon = MesOSGlyphs.History,
                        showChevron = false,
                        onClick = {
                            store.clearHistory()
                            toast(context, R.string.browser_cleared)
                        },
                    )
                    GroupDivider()
                    ListRow(
                        title = stringResource(R.string.browser_clear_site_data),
                        subtitle = stringResource(R.string.browser_clear_site_data_summary),
                        icon = MesOSGlyphs.Trash,
                        showChevron = false,
                        onClick = {
                            CookieManager.getInstance().removeAllCookies(null)
                            WebStorage.getInstance().deleteAllData()
                            tabs.tabs.forEach { it.webView?.clearCache(true) }
                            toast(context, R.string.browser_cleared)
                        },
                    )
                }
            }
        }
        item(key = "about") {
            Text(
                stringResource(R.string.browser_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MesOSTheme.colors.dim,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

private fun toast(context: Context, text: Int) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
